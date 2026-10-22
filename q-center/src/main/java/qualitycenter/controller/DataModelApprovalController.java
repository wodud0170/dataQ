package qualitycenter.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.quality.common.PermCode;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ddl.DdlDialects;
import qualitycenter.service.ddl.DdlGenerator;
import qualitycenter.service.ddl.DdlSafety;
import qualitycenter.service.ddl.SqlDialect;
import qualitycenter.service.governance.ChangeHistoryService;
import reactor.core.publisher.Mono;

/**
 * 88번 거버넌스 — 데이터 모델 변경 신청 / 승인 / 반려 / 롤백 endpoint.
 *
 * 핵심 흐름 (DRAFT → SUBMITTED → APPROVED/REJECTED):
 *  - 사용자가 모델을 변경하면 ChangeHistoryService 가 DRAFT 로 이력 INSERT (DataModelController 분기)
 *  - 사용자가 [신청] 누르면 submitDrafts 가 DRAFT → SUBMITTED + SUBMISSION_ID 부여
 *  - 관리자가 [승인] 누르면 approve / [반려] 누르면 reject
 *  - 사용자는 본인 DRAFT 롤백 가능
 */
@Slf4j
@RestController
@RequestMapping("/api/dmApproval")
public class DataModelApprovalController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private ChangeHistoryService changeHistory;
	@Autowired private com.ndata.quality.tool.DataSourceUtils dataSourceUtils;
	@Autowired private DdlDialects ddlDialects;
	@Autowired private DdlGenerator ddlGenerator;
	@Autowired private com.ndata.quality.service.AprvService aprvService;
	@Autowired private qualitycenter.service.ws.WebSocketService websocketService;
	@Autowired private PlatformTransactionManager txManager;

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/** 본인 DRAFT 목록 (신청 모달용) */
	@RequestMapping(value = "/myDrafts", method = RequestMethod.POST)
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> myDrafts(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("currentUserId", changeHistory.safeUserId());
		p.put("dmId", body.get("dmId"));
		return sqlSessionTemplate.selectList("dmChangeHistory.selectMyDrafts", p);
	}

	/** 사용자 — 선택한 DRAFT 들을 SUBMITTED 로 일괄 신청 */
	@PostMapping("/submit")
	@SuppressWarnings("unchecked")
	public Mono<Response> submit(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty())
				throw new IllegalArgumentException("changeSeqList 필수");
			String submissionId = UUID.randomUUID().toString();
			Map<String, Object> p = new HashMap<>();
			p.put("currentUserId", changeHistory.safeUserId());
			p.put("changeSeqList", seqList);
			p.put("submissionId",  submissionId);
			int n = sqlSessionTemplate.update("dmChangeHistory.submitDrafts", p);
			// 104번 WP1 — 데이터 모델 변경에 결재선이 있으면 이 신청으로 결재 문서를 만든다
			String aprvMsg = null;
			if (n > 0) {
				Map<String, Object> first = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seqList.get(0));
				String dmId = first == null ? null : (String) first.get("dm_id");
				Map<String, Object> model = dmId == null ? null : sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dmId);
				String dmNm = model == null ? dmId : String.valueOf(model.get("dataModelNm"));
				com.ndata.quality.service.AprvService.Result r = aprvService.open("DM_CHANGE", submissionId, dmNm + " 변경 " + n + "건", dmId, null, changeHistory.safeUserId());
				if (r != null) {
					websocketService.sendUserNotis(r.notis);
					aprvMsg = r.message;
				}
			}
			Map<String, Object> out = new java.util.LinkedHashMap<>();
			out.put("submitted", n);
			out.put("submissionId", submissionId);
			out.put("aprvMsg", aprvMsg);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out));
		} catch (Exception e) {
			log.error(">> submit failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 사용자 — DRAFT 단건 롤백 (DB row 삭제 + 본 변경에 따른 실제 모델 row 도 정리 필요 — V2 검토) */
	@PostMapping("/rollbackDraft")
	public Mono<Response> rollbackDraft(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			Object seq = body.get("changeSeq");
			if (seq == null) throw new IllegalArgumentException("changeSeq 필수");
			Map<String, Object> p = new HashMap<>();
			p.put("changeSeq",     seq);
			p.put("currentUserId", changeHistory.safeUserId());
			int n = sqlSessionTemplate.delete("dmChangeHistory.deleteDraft", p);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"deleted\":" + n + "}");
		} catch (Exception e) {
			log.error(">> rollbackDraft failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * SUBMITTED 묶음 목록 (승인 화면용). 모델 승인 권한(관리자 또는 DM_APPROVE)은 전체,
	 * 104번 WP1 — 그 밖에는 내가 현재 결재자인 결재 문서의 신청만. 결재 문서가 있으면 aprvDoc 에 진행 단계를 싣는다.
	 */
	@PostMapping("/submissions")
	public List<Map<String, Object>> submissions(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("changeUserId", body.get("changeUserId"));
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("dmChangeHistory.selectSubmissions", p);
		if (rows.isEmpty()) return rows;
		List<String> ids = new ArrayList<>();
		for (Map<String, Object> r : rows) ids.add((String) r.get("submissionId"));
		Map<String, Object> q = new HashMap<>();
		q.put("targetIds", ids);
		Map<String, Map<String, Object>> docs = new HashMap<>();
		for (Map<String, Object> d : sqlSessionTemplate.<Map<String, Object>>selectList("aprv.selectOpenDocsByTargets", q)) {
			if ("DM_CHANGE".equals(d.get("reqTp"))) docs.put((String) d.get("targetId"), d);
		}
		boolean dmApprover = sessionService.hasPerm(PermCode.DM_APPROVE);
		String userId = changeHistory.safeUserId();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			Map<String, Object> d = docs.get(r.get("submissionId"));
			r.put("aprvDoc", d);
			if (dmApprover || (d != null && aprvService.canAct((String) d.get("docId"), userId))) out.add(r);
		}
		return out;
	}

	/** 특정 묶음의 항목들 — 모델 승인 권한, 또는 그 신청 결재 문서의 현재 결재자 */
	@PostMapping("/submissionItems")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> submissionItems(@RequestBody Map<String, Object> body) {
		String submissionId = (String) body.get("submissionId");
		if (!sessionService.hasPerm(PermCode.DM_APPROVE)) {
			Map<String, Object> d = aprvService.openDoc("DM_CHANGE", submissionId);
			if (d == null || !aprvService.canAct((String) d.get("docId"), changeHistory.safeUserId())) return new ArrayList<>();
		}
		return sqlSessionTemplate.selectList("dmChangeHistory.selectBySubmissionId", submissionId);
	}

	/** 선택 항목 승인 */
	@PostMapping("/approve")
	public Mono<Response> approve(@RequestBody Map<String, Object> body) {
		return decide(body, "APPROVED");
	}

	/** 선택 항목 반려 + 사유 */
	@PostMapping("/reject")
	public Mono<Response> reject(@RequestBody Map<String, Object> body) {
		return decide(body, "REJECTED");
	}

	/**
	 * 승인·반려. 결재 문서가 없는 건은 모델 승인 권한(관리자 또는 DM_APPROVE)으로 지금처럼 선택 건만 처리한다.
	 * 104번 WP1 — 건이 속한 신청에 진행 중 결재 문서가 있으면 그 문서의 현재 결재자(관리자 대결 포함)가 단계를 진행하고,
	 * 최종 승인·반려일 때만 <b>그 신청의 SUBMITTED 건 전체</b>에 기존 처리를 한다 (결재 문서가 있으면 일부만 골라 처리하지 않는다).
	 * 109번 #4 — 요청 전체(결재 단계 진행·건 처리)를 한 트랜잭션으로 한다. 한 건이라도 실패하면 모두 되돌리고, 알림은 커밋한 뒤에 보낸다.
	 * 앞 건의 반려가 뒤 건을 함께 반려(cascade)했으면 뒤 건은 건너뛴다 — 건마다 지금 상태를 다시 읽는다.
	 * 응답 contents = {approved|rejected: 처리 건수, steps: [단계만 진행한 신청의 안내]}
	 */
	@SuppressWarnings("unchecked")
	private Mono<Response> decide(Map<String, Object> body, String status) {
		Response res = new Response();
		boolean approve = "APPROVED".equals(status);
		try {
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			String now = LocalDateTime.now().format(DT_FMT);
			String userId = changeHistory.safeUserId();
			boolean dmApprover = sessionService.hasPerm(PermCode.DM_APPROVE);
			Object comment = body.get("aprvComment");
			List<com.ndata.quality.service.AprvService.Noti> notis = new ArrayList<>();
			List<String> steps = new ArrayList<>();

			int n = new TransactionTemplate(txManager).execute(tx -> {
				// 신청별로 나눈다 — 결재 문서가 있는 신청은 문서 단위로 한 번만 처리
				List<Object> plain = new ArrayList<>();
				java.util.Set<String> pendingAtStart = new java.util.HashSet<>();
				Map<String, Map<String, Object>> docBySub = new java.util.LinkedHashMap<>();
				for (Object seq : seqList) {
					Map<String, Object> h = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seq);
					if (h != null && isPending(h)) pendingAtStart.add(String.valueOf(seq));
					String sub = h == null ? null : (String) h.get("submission_id");
					Map<String, Object> d = sub == null ? null : aprvService.openDoc("DM_CHANGE", sub);
					if (d != null) docBySub.put(sub, d);
					else plain.add(seq);
				}
				if (!plain.isEmpty() && !dmApprover) throw new IllegalStateException("권한이 없습니다 (모델 승인 권한 필요)");

				int cnt = 0;
				for (Object seq : plain) {
					// 요청 때 대기였는데 지금 아니면 이 요청의 앞 건 반려에 함께 반려된 것. 요청 때부터 처리된 건은 아래에서 거부(DEF-09)
					if (pendingAtStart.contains(String.valueOf(seq))
							&& !isPending(sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seq))) continue;
					cnt += applyApprovalStatus(seq, status, userId, now, comment);
				}
				for (Map.Entry<String, Map<String, Object>> e : docBySub.entrySet()) {
					com.ndata.quality.service.AprvService.Result r = aprvService.act(sqlSessionTemplate, (String) e.getValue().get("docId"),
							userId, approve, comment == null ? null : String.valueOf(comment));
					notis.addAll(r.notis);
					if (!r.done) {
						steps.add(e.getValue().get("targetNm") + ": " + r.message);
						continue;
					}
					for (com.ndata.quality.model.std.StdDataModelChangeHistoryVo i : sqlSessionTemplate.<com.ndata.quality.model.std.StdDataModelChangeHistoryVo>selectList(
							"dmChangeHistory.selectBySubmissionId", e.getKey())) {
						Map<String, Object> h = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", i.getChangeSeq());
						if ("SUBMITTED".equals(h.get("aprv_status"))) cnt += applyApprovalStatus(i.getChangeSeq(), status, userId, now, comment);
					}
				}
				return cnt;
			});
			websocketService.sendUserNotis(notis);
			Map<String, Object> out = new java.util.LinkedHashMap<>();
			out.put(approve ? "approved" : "rejected", n);
			out.put("steps", steps);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out));
		} catch (Exception e) {
			log.error(">> {} failed: {}", approve ? "approve" : "reject", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	private static boolean isPending(Map<String, Object> hist) {
		return hist != null && ("DRAFT".equals(hist.get("aprv_status")) || "SUBMITTED".equals(hist.get("aprv_status")));
	}

	/**
	 * change_history 의 status 갱신 + 실제 모델 row 의 aprv_status 동기화.
	 * change_type 별 분기 — ADD_ATTR/MODIFY_ATTR/DEL_ATTR → tb_data_model_attr,
	 * ADD_OBJ/MODIFY_OBJ/DEL_OBJ → tb_data_model_obj.
	 *
	 * 88번 §13 — 반려 cascade: REJECTED 시 종속 PENDING 자식 자동 반려.
	 * (예: 부모 컬럼 반려 → 그 컬럼을 FK_PARENT 로 참조하는 PENDING ATTR 도 반려)
	 */
	private int applyApprovalStatus(Object changeSeq, String status, String adminId, String now, Object comment) {
		Map<String, Object> p = new HashMap<>();
		p.put("changeSeq",   changeSeq);
		p.put("aprvStatus",  status);
		p.put("aprvUserId",  adminId);
		p.put("aprvDt",      now);
		p.put("aprvComment", comment);
		// DEF-09/DEF-14 — APPROVED/REJECTED 는 종료 상태. 대기 중인 것만 전이시킨다.
		p.put("fromStatuses", java.util.Arrays.asList("DRAFT", "SUBMITTED"));
		int n = sqlSessionTemplate.update("dmChangeHistory.updateAprvStatus", p);
		if (n == 0) {
			throw new IllegalStateException(
				"이미 처리된 항목입니다 (change_seq=" + changeSeq + "). 승인·반려는 대기 중인 항목에만 가능합니다.");
		}
		// 실제 모델 row 동기화
		Map<String, Object> hist = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", changeSeq);
		if (hist != null) {
			String changeType = (String) hist.get("change_type");
			String dmId       = (String) hist.get("dm_id");
			String objOwner   = (String) hist.get("obj_owner");
			String objNm      = (String) hist.get("obj_nm");
			String attrNm     = (String) hist.get("attr_nm");
			Map<String, Object> sp = new HashMap<>();
			sp.put("dmId",        dmId);
			sp.put("objOwner",    objOwner);
			sp.put("objNm",       objNm);
			sp.put("attrNm",      attrNm);
			sp.put("aprvStatus",  status);
			sp.put("aprvUserId",  adminId);
			sp.put("aprvDt",      now);
			// DEF-04 — 수정 반려 시 변경 전 값으로 되돌린다.
			// 그동안 모델 row 는 이미 바뀐 채였고 반려는 상태만 REJECTED 로 만들어,
			// 반려된 값이 그대로 살아 있었다.
			// 테이블 수정(MODIFY_OBJ)은 아래 테이블 반려 전파가 지금(바뀐) 이름으로 돈 뒤에 되돌린다 (109번 #14)
			if ("REJECTED".equals(status) && "MODIFY_ATTR".equals(changeType)) {
				restoreFromBeforeJson(hist, changeType, dmId, objOwner, objNm, attrNm);
			}

			// WP0 — PK/FK 변경 (createPk·deletePk·createFk·deleteFk). 승인은 확정, 반려는 신청 전 상태로.
			if (CONSTRAINT_CHANGE_TYPES.contains(changeType)) {
				sp.put("constraintNm", hist.get("constraint_nm"));
				applyConstraintDecision(changeType, status, sp, (String) hist.get("before_json"));
				return n;
			}

			// DEF-05 — 대기 중 삭제(USE_YN='N') 의 확정/복구.
			// 승인이면 이제 진짜로 지우고, 반려면 되살린다.
			if ("DEL_ATTR".equals(changeType)) {
				if ("APPROVED".equals(status)) {
					Map<String, Object> dp = new HashMap<>();
					dp.put("dataModelId", dmId);
					dp.put("objOwner", objOwner);
					dp.put("objNm", objNm);
					dp.put("attrNm", attrNm);
					sqlSessionTemplate.delete("datamodel.deleteConstraintByAttr",  dp);
					sqlSessionTemplate.delete("datamodel.deleteIndexByAttr",       dp);
					sqlSessionTemplate.delete("datamodel.deleteInboundFkByAttr",   dp);
					sqlSessionTemplate.update("datamodel.clearFkParentRefByAttr",  dp);
					sqlSessionTemplate.delete("datamodel.deleteDataModelAttr",     dp);
					log.info(">> DEL_ATTR 승인 — 물리 삭제 {}.{}.{}", objOwner, objNm, attrNm);
					return n;
				} else if ("REJECTED".equals(status)) {
					int restored = sqlSessionTemplate.update("datamodel.restoreAttrPendingDelete", sp);
					log.info(">> DEL_ATTR 반려 — {}건 복구 {}.{}.{}", restored, objOwner, objNm, attrNm);
					return n;
				}
			} else if ("DEL_OBJ".equals(changeType)) {
				if ("APPROVED".equals(status)) {
					Map<String, Object> dp = new HashMap<>();
					dp.put("dataModelId", dmId);
					dp.put("objOwner", objOwner);
					dp.put("objNm", objNm);
					sqlSessionTemplate.delete("datamodel.deleteIndexByObj",              dp);
					sqlSessionTemplate.delete("datamodel.deleteConstraintByObj",         dp);
					sqlSessionTemplate.delete("datamodel.deleteInboundFkByObj",          dp);
					sqlSessionTemplate.update("datamodel.clearFkParentRefByObj",         dp);
					sqlSessionTemplate.delete("udp.deleteObjVals",                       dp);   // 104번 WP8-31
					sqlSessionTemplate.delete("datamodel.deleteDataModelAttrsByObj",     dp);
					sqlSessionTemplate.delete("datamodel.deleteDataModelObj",            dp);
					log.info(">> DEL_OBJ 승인 — 물리 삭제 {}.{}", objOwner, objNm);
					return n;
				} else if ("REJECTED".equals(status)) {
					int a = sqlSessionTemplate.update("datamodel.restoreAttrsByObjPendingDelete", sp);
					int o = sqlSessionTemplate.update("datamodel.restoreObjPendingDelete", sp);
					log.info(">> DEL_OBJ 반려 — 테이블 {}건 / 컬럼 {}건 복구 {}.{}", o, a, objOwner, objNm);
					return n;
				}
			}

			if (changeType != null && changeType.contains("_ATTR") && attrNm != null) {
				sqlSessionTemplate.update("dmChangeHistory.syncAttrAprvStatus", sp);
				if ("REJECTED".equals(status)) {
					cascadeRejectFrom(dmId, objOwner, objNm, attrNm, adminId, now,
					                  "부모 컬럼 반려에 의한 cascade");
				}
			} else if (changeType != null && changeType.contains("_OBJ")) {
				sqlSessionTemplate.update("dmChangeHistory.syncObjAprvStatus", sp);
				// 테이블 반려 → 그 테이블의 PENDING 컬럼들, 그리고 그 컬럼을 참조하는 것들까지
				if ("REJECTED".equals(status)) {
					Map<String, Object> casc = new HashMap<>();
					casc.put("dmId", dmId);
					casc.put("objOwner", objOwner);
					casc.put("objNm", objNm);
					casc.put("aprvStatus", "REJECTED");
					casc.put("aprvUserId", adminId);
					casc.put("aprvDt",     now);
					casc.put("aprvComment", "부모 테이블 반려에 의한 cascade");
					int cnt  = sqlSessionTemplate.update("dmChangeHistory.cascadeRejectAttrsInObj",   casc);
					int hcnt = sqlSessionTemplate.update("dmChangeHistory.cascadeRejectHistoryInObj", casc);
					if (cnt > 0) log.info(">> reject cascade (obj→attrs): {} attrs, {} history", cnt, hcnt);
					// 방금 반려된 컬럼들을 기점으로 손자 이하까지 이어서 전파
					for (Map<String, Object> k : rejectedAttrKeys(dmId, now)) {
						cascadeRejectFrom(dmId, (String) k.get("objOwner"), (String) k.get("objNm"),
						                  (String) k.get("attrNm"), adminId, now,
						                  "상위 반려에 의한 cascade");
					}
					if ("MODIFY_OBJ".equals(changeType)) {
						restoreFromBeforeJson(hist, changeType, dmId, objOwner, objNm, attrNm);
					}
				}
			}
		}
		return n;
	}

	/** WP0 — PK/FK 변경 유형. 이름이 _ATTR/_OBJ 로 끝나지 않아 아래 컬럼·테이블 동기화를 타지 않는다. */
	private static final java.util.Set<String> CONSTRAINT_CHANGE_TYPES =
			new java.util.HashSet<>(java.util.Arrays.asList("ADD_PK", "DEL_PK", "ADD_FK", "DEL_FK"));

	/**
	 * WP0 — PK/FK 변경의 승인·반려.
	 *
	 * 신청 때 이미 모델에 대기 상태로 반영돼 있다 (DataModelController createPk·deletePk·createFk·deleteFk):
	 * 추가는 APRV_STATUS='DRAFT' 행, 삭제는 USE_YN='N' 으로 감춘 행, 컬럼 PK·FK 표시는 바뀐 값.
	 * <ul>
	 *   <li>ADD_PK·ADD_FK 승인 — 제약조건(과 PK 인덱스) 행을 APPROVED 로</li>
	 *   <li>ADD_PK·ADD_FK 반려 — 그 행을 지우고 컬럼 표시를 BEFORE_JSON 값으로</li>
	 *   <li>DEL_PK·DEL_FK 승인 — 감춰 둔 행(PK 삭제에 딸린 FK 포함)을 물리 삭제</li>
	 *   <li>DEL_PK·DEL_FK 반려 — 감춰 둔 행을 되살리고 컬럼 표시를 BEFORE_JSON 값으로</li>
	 * </ul>
	 * PK 인덱스 이름은 PK 이름과 같다 (createPk).
	 */
	@SuppressWarnings("unchecked")
	private void applyConstraintDecision(String changeType, String status, Map<String, Object> sp, String beforeJson) {
		boolean pk = changeType.endsWith("_PK");
		Map<String, Object> key = new HashMap<>();
		key.put("dataModelId",  sp.get("dmId"));
		key.put("objOwner",     sp.get("objOwner"));
		key.put("objNm",        sp.get("objNm"));
		key.put("constraintNm", sp.get("constraintNm"));
		key.put("indexNm",      sp.get("constraintNm"));
		Map<String, Object> before;
		try {
			before = new com.fasterxml.jackson.databind.ObjectMapper().readValue(beforeJson, Map.class);
		} catch (Exception e) {
			throw new IllegalStateException(changeType + " 처리에 필요한 변경 전 값(BEFORE_JSON)을 읽지 못했습니다: " + e.getMessage(), e);
		}
		List<Map<String, Object>> cascadedFks = before.containsKey("cascadedFks")
				? (List<Map<String, Object>>) before.get("cascadedFks") : new ArrayList<>();
		boolean approved = "APPROVED".equals(status);

		if (changeType.startsWith("ADD_")) {
			if (approved) {
				Map<String, Object> s = new HashMap<>(sp);
				s.put("indexNm", sp.get("constraintNm"));
				sqlSessionTemplate.update("dmChangeHistory.syncConstraintAprvStatus", s);
				if (pk) sqlSessionTemplate.update("dmChangeHistory.syncIndexAprvStatus", s);
			} else {
				sqlSessionTemplate.delete("datamodel.deleteConstraintByName", key);
				if (pk) sqlSessionTemplate.delete("datamodel.deleteIndexByName", key);
				restoreAttrKeys((List<Map<String, Object>>) before.get("columns"), sp.get("dmId"), pk);
			}
		} else {
			if (approved) {
				sqlSessionTemplate.delete("datamodel.deleteConstraintByName", key);
				if (pk) sqlSessionTemplate.delete("datamodel.deleteIndexByName", key);
				for (Map<String, Object> fk : cascadedFks) {
					sqlSessionTemplate.delete("datamodel.deleteConstraintByName", fkKey(sp.get("dmId"), fk));
				}
			} else {
				sqlSessionTemplate.update("datamodel.restoreConstraintPendingDelete", key);
				if (pk) sqlSessionTemplate.update("datamodel.restoreIndexPendingDelete", key);
				restoreAttrKeys((List<Map<String, Object>>) before.get("columns"), sp.get("dmId"), pk);
				for (Map<String, Object> fk : cascadedFks) {
					sqlSessionTemplate.update("datamodel.restoreConstraintPendingDelete", fkKey(sp.get("dmId"), fk));
					restoreAttrKeys((List<Map<String, Object>>) fk.get("columns"), sp.get("dmId"), false);
				}
			}
		}
		log.info(">> {} {} — {}.{} {}", changeType, status, sp.get("objOwner"), sp.get("objNm"), sp.get("constraintNm"));
	}

	/** PK 삭제에 딸려 지운 FK 의 제약조건 키 */
	private Map<String, Object> fkKey(Object dmId, Map<String, Object> fk) {
		Map<String, Object> k = new HashMap<>();
		k.put("dataModelId",  dmId);
		k.put("objOwner",     fk.get("objOwner"));
		k.put("objNm",        fk.get("objNm"));
		k.put("constraintNm", fk.get("constraintNm"));
		return k;
	}

	/** 컬럼의 PK 표시(pk=true) 또는 FK 표시(pk=false)를 신청 전 값으로 되돌린다. */
	private void restoreAttrKeys(List<Map<String, Object>> columns, Object dmId, boolean pk) {
		for (Map<String, Object> c : columns) {
			Map<String, Object> p = new HashMap<>(c);
			p.put("dataModelId", dmId);
			sqlSessionTemplate.update(pk ? "datamodel.updateAttrPkYn" : "datamodel.updateAttrFk", p);
		}
	}

	/**
	 * DEF-04 — 수정 반려 시 변경 전 값으로 되돌린다.
	 *
	 * 모델 row 는 신청 시점에 이미 바뀌어 있다 (그래야 신청자가 자기 변경을 화면에서 본다).
	 * 반려는 그 변경을 무효로 만드는 것이므로 이력의 BEFORE_JSON 으로 복원해야 한다.
	 *
	 * BEFORE_JSON 이 없으면 (구버전 이력) 복원할 수 없다. 로그만 남기고 상태만 REJECTED 로 둔다.
	 */
	private void restoreFromBeforeJson(Map<String, Object> hist, String changeType,
	                                   String dmId, String objOwner, String objNm, String attrNm) {
		String beforeJson = (String) hist.get("before_json");
		if (beforeJson == null || beforeJson.trim().isEmpty()) {
			log.warn(">> {} 반려 — BEFORE_JSON 이 없어 값 복원 불가 ({}.{}.{})",
			         changeType, objOwner, objNm, attrNm);
			return;
		}
		try {
			Map<String, Object> before = new com.fasterxml.jackson.databind.ObjectMapper()
					.readValue(beforeJson, Map.class);
			before.put("dataModelId", dmId);
			if ("MODIFY_ATTR".equals(changeType)) {
				// 신청 시 영문명이 바뀌었을 수 있으므로, 현재 이름으로 찾아 예전 값으로 되돌린다.
				before.put("currObjOwner", objOwner);
				before.put("currObjNm",    objNm);
				before.put("currAttrNm",   attrNm);
				int r = sqlSessionTemplate.update("datamodel.restoreAttrFromBefore", before);
				log.info(">> MODIFY_ATTR 반려 — {}건 원복 {}.{}.{}", r, objOwner, objNm, attrNm);
			} else if ("MODIFY_OBJ".equals(changeType)) {
				// 109번 #14 — 이름·소유자 변경 때(updateObj) 함께 바꾼 컬럼·인덱스·제약조건·참조 제약조건·FK 부모·사용자 정의 속성 값도
				// 변경 전 이름으로 되돌린다. 예전에는 테이블 행만 되돌려 나머지가 새 이름에 고아로 남았다.
				// updateObj 는 이름을 (변경 전 소유자로) 먼저 바꾸고 소유자를 바꾸므로(FK 부모는 소유자를 안 바꾼다), 거꾸로 소유자 → 이름 순서
				String curOwner = objOwner == null ? "" : objOwner;
				String oldOwner = before.get("objOwner") == null ? curOwner : (String) before.get("objOwner");
				String oldNm    = before.get("objNm") == null ? objNm : (String) before.get("objNm");
				if (!oldOwner.equals(curOwner)) {
					Map<String, Object> own = new HashMap<>();
					own.put("dataModelId", dmId);
					own.put("objNm",       objNm);
					own.put("objOwner",    curOwner);   // 지금 소유자 — WHERE
					own.put("newOwner",    oldOwner);   // 변경 전 소유자 — SET
					sqlSessionTemplate.update("datamodel.cascadeAttrOwner",          own);
					sqlSessionTemplate.update("datamodel.cascadeIndexOwner",         own);
					sqlSessionTemplate.update("datamodel.cascadeConstraintOwner",    own);
					sqlSessionTemplate.update("datamodel.cascadeConstraintRefOwner", own);
				}
				if (!oldNm.equals(objNm)) {
					Map<String, Object> rn = new HashMap<>();
					rn.put("dataModelId", dmId);
					rn.put("origObjNm",   objNm);
					rn.put("newObjNm",    oldNm);
					rn.put("objOwner",    oldOwner);
					sqlSessionTemplate.update("datamodel.renameObjAttrCascade",          rn);
					sqlSessionTemplate.update("datamodel.renameObjIndexCascade",         rn);
					sqlSessionTemplate.update("datamodel.renameObjConstraintCascade",    rn);
					sqlSessionTemplate.update("datamodel.renameObjConstraintRefCascade", rn);
					sqlSessionTemplate.update("datamodel.renameObjFkParentCascade",      rn);
				}
				if (!oldNm.equals(objNm) || !oldOwner.equals(curOwner)) {
					Map<String, Object> rv = new HashMap<>();
					rv.put("dmId",      dmId);
					rv.put("origOwner", curOwner);
					rv.put("origObjNm", objNm);
					rv.put("newOwner",  oldOwner);
					rv.put("newObjNm",  oldNm);
					sqlSessionTemplate.update("udp.renameObjVals", rv);
				}
				before.put("currObjOwner", objOwner);
				before.put("currObjNm",    objNm);
				int r = sqlSessionTemplate.update("datamodel.restoreObjFromBefore", before);
				log.info(">> MODIFY_OBJ 반려 — {}건 원복 {}.{} → {}.{}", r, objOwner, objNm, oldOwner, oldNm);
			}
		} catch (Exception e) {
			log.error(">> {} 반려 원복 실패 ({}.{}.{}): {}",
			          changeType, objOwner, objNm, attrNm, e.getMessage());
			throw new IllegalStateException("반려는 했으나 변경 전 값 복원에 실패했습니다: " + e.getMessage(), e);
		}
	}

	/**
	 * DEF-06 — 반려 전파. 한 단계만 돌던 것을 더 이상 바뀌는 행이 없을 때까지 반복한다.
	 *
	 * A ← B ← C 처럼 FK 로 이어진 신청이 있을 때, A 를 반려하면 B 만 반려되고 C 는
	 * 대기 상태로 남아 있었다. C 는 이미 사라질 B 를 부모로 삼고 있으므로 승인될 수 없다.
	 *
	 * DEF-07 — 모델 row 와 변경 이력을 함께 반려한다. 이력을 안 바꾸면 승인 화면에
	 * 이미 반려된 항목이 계속 대기 중으로 보인다.
	 *
	 * 종료 조건은 "이번 회차에 바뀐 행이 0" 이다. FK 순환이 있어도 두 번째 회차에서
	 * 이미 REJECTED 라 매칭되지 않으므로 멈춘다.
	 */
	private void cascadeRejectFrom(String dmId, String objOwner, String objNm, String attrNm,
	                               String adminId, String now, String reason) {
		java.util.Deque<String[]> queue = new java.util.ArrayDeque<>();
		queue.add(new String[]{ objOwner, objNm, attrNm });
		java.util.Set<String> visited = new java.util.HashSet<>();
		int depth = 0;
		while (!queue.isEmpty() && depth++ < 50) {
			String[] cur = queue.poll();
			String key = cur[0] + "|" + cur[1] + "|" + cur[2];
			if (!visited.add(key)) continue;

			Map<String, Object> casc = new HashMap<>();
			casc.put("dmId",           dmId);
			casc.put("parentObjOwner", cur[0]);
			casc.put("parentObjNm",    cur[1]);
			casc.put("parentAttrNm",   cur[2]);
			casc.put("aprvStatus",     "REJECTED");
			casc.put("aprvUserId",     adminId);
			casc.put("aprvDt",         now);
			casc.put("aprvComment",    reason);

			// 자식을 찾아둔 뒤 반려해야 한다. 반려 후에는 상태가 바뀌어 목록에서 빠진다.
			List<Map<String, Object>> children =
				sqlSessionTemplate.selectList("dmChangeHistory.selectPendingChildrenOfAttr", casc);
			int cnt  = sqlSessionTemplate.update("dmChangeHistory.cascadeRejectDependents",    casc);
			int hcnt = sqlSessionTemplate.update("dmChangeHistory.cascadeRejectHistoryByAttr", casc);
			if (cnt > 0 || hcnt > 0)
				log.info(">> reject cascade {}.{}.{} → {} attrs, {} history",
				         cur[0], cur[1], cur[2], cnt, hcnt);

			for (Map<String, Object> c : children) {
				queue.add(new String[]{ (String) c.get("objOwner"),
				                        (String) c.get("objNm"),
				                        (String) c.get("attrNm") });
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> rejectedAttrKeys(String dmId, String now) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		p.put("aprvDt", now);
		return sqlSessionTemplate.selectList("dmChangeHistory.selectRejectedAttrKeys", p);
	}

	/**
	 * 109번 — [DB 반영] 한 건의 실행 판정. 확인창(ddlPreview)과 실행(execDdl)이 같은 판정을 쓴다.
	 * deny 면 실행하지 않고 실패로 센다(이력에 남기지 않는다), skip 이면 건너뜀(SKIPPED)으로 이력에 남긴다, 그 밖은 stmts 를 실행한다.
	 */
	private static class DdlPlan {
		Map<String, Object> mds;   // 모델 데이터소스 {dsId, dsn, dbmsTp}
		String deny;
		String skip;
		List<String> stmts;
		int done;                  // 109번 #17 — 앞 실행(중간 실패)에서 이미 반영된 앞 문장 수. 이번에는 stmts[done..] 만 실행
		boolean create;            // 테이블 추가 — 같은 테이블의 뒤 변경을 '포함' 으로 기록
	}

	private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

	/** 109번 #17 — 같은 변경을 두 요청이 동시에 실행하지 못하게 (실행 중인 change_seq) */
	private final java.util.Set<String> ddlRunning = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private DdlPlan plan(Object seq) {
		DdlPlan p = new DdlPlan();
		Map<String, Object> hist = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seq);
		if (hist == null) { p.deny = "이력이 없습니다"; return p; }
		String sysCd = sqlSessionTemplate.selectOne("role.selectSysCdByChangeSeq", seq);
		if (!sessionService.hasPerm(PermCode.DDL_EXEC, sysCd)) {
			p.deny = "이 모델의 시스템(" + (sysCd == null ? "지정 안 됨" : sysCd) + ")에 대한 DB 반영 권한이 없습니다";
			return p;
		}
		if (!"APPROVED".equals(hist.get("aprv_status"))) { p.deny = "승인되지 않은 변경입니다"; return p; }
		if ("SUCCESS".equals(hist.get("ddl_exec_result"))) { p.deny = "이미 반영된 변경입니다"; return p; }

		String dmId = (String) hist.get("dm_id");
		String changeType = (String) hist.get("change_type");
		String owner = hist.get("obj_owner") == null ? "" : (String) hist.get("obj_owner");
		String objNm = (String) hist.get("obj_nm");
		p.mds = ddlDialects.modelDs(dmId);
		if (p.mds == null) { p.skip = "모델에 데이터소스가 없어 DB 에 반영할 수 없습니다"; return p; }
		// 109번 #2 — 시스템 범위로 받은 DB 반영 권한은 모델의 데이터소스가 그 시스템(하위 시스템 포함)의 데이터소스일 때만 통한다.
		// 모델의 시스템만 바꿔 다른 시스템의 DB 에 실행하는 길을 막는다. 관리자·모든 시스템 권한은 원래 어느 DB 에나 실행할 수 있다.
		if (sysCd != null && !sessionService.hasPerm(PermCode.DDL_EXEC, null)) {
			Map<String, Object> q = new HashMap<>();
			q.put("sysCd", sysCd);
			q.put("dsId", p.mds.get("dsId"));
			int n = sqlSessionTemplate.selectOne("ddlRule.countDsInSysTree", q);
			if (n == 0) {
				p.deny = "모델의 데이터소스(" + p.mds.get("dsn") + ")가 모델의 시스템(" + sysCd + ")에 등록된 데이터소스가 아닙니다"
						+ " — 관리 > 시스템에서 데이터소스를 지정하거나 모델의 데이터소스를 확인하세요";
				return p;
			}
		}
		String dbms = (String) p.mds.get("dbmsTp");
		if (hist.get("ddl_dbms_tp") != null && !String.valueOf(hist.get("ddl_dbms_tp")).equalsIgnoreCase(dbms)) {
			p.skip = "DDL 은 " + hist.get("ddl_dbms_tp") + " 용인데 모델 데이터소스는 " + dbms + " 입니다";
			return p;
		}
		SqlDialect d = ddlDialects.forDbms(dbms);
		try {
			if ("ADD_OBJ".equals(changeType)) {
				if (d == null) { p.skip = dbms + " 용 DDL 규칙이 없습니다"; return p; }
				// 109번 #5 — 컬럼·테이블 수정, PK·FK 변경은 신청 때 모델 행을 바로 바꾼다. 그 승인 대기 건이 있으면
				// CREATE TABLE 에 승인 안 된 값이 들어가므로 반영하지 않는다
				Map<String, Object> k = new HashMap<>();
				k.put("dmId", dmId);
				k.put("objOwner", owner);
				k.put("objNm", objNm);
				int pend = sqlSessionTemplate.selectOne("ddlRule.countPendingEditsOfObj", k);
				if (pend > 0) {
					p.deny = "이 테이블에 승인 대기 중인 수정이 " + pend + "건 있습니다 — 승인·반려한 뒤 반영하세요";
					return p;
				}
				p.stmts = ddlGenerator.createTableDdl(dmId, owner, objNm, d);
				if (p.stmts == null) { p.skip = "모델에 테이블이 없습니다: " + objNm; return p; }
				p.create = true;
			} else {
				String ddl = (String) hist.get("ddl_snippet");
				if (ddl == null || ddl.trim().isEmpty()) { p.skip = "실행할 DDL 이 없습니다"; return p; }
				if (hasCommentLine(ddl)) { p.skip = "DDL 을 만들 수 없는 변경입니다 — " + ddl.trim(); return p; }
				if (d == null) { p.skip = dbms + " 용 DDL 규칙이 없습니다"; return p; }
				// 109번 #3 — 저장된 조각을 그대로 실행하지 않는다. 이력의 값으로 지금의 규칙·검사를 거쳐 다시 만든다
				p.stmts = changeHistory.regenerate(hist, d);
				if (p.stmts.isEmpty()) { p.skip = "실행할 DDL 이 없습니다"; return p; }
			}
			for (String s : p.stmts) DdlSafety.checkStatement(s, d.backslashEscapes());
		} catch (DdlSafety.Invalid | IllegalStateException e) {
			p.stmts = null;
			p.create = false;
			p.deny = "DDL 검증 실패 — " + e.getMessage();
			return p;
		}
		// 109번 #17 — 앞 실행이 중간에 실패했으면 그때 실행된 문장이 지금 만든 문장의 앞부분과 같을 때만 그 뒤부터 이어서 실행한다
		Object doneJson = hist.get("ddl_exec_done");
		if (doneJson != null && !doneJson.toString().isEmpty()) {
			List<String> ran;
			try {
				ran = JSON.readValue(doneJson.toString(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
			} catch (java.io.IOException e) {
				ran = null;
			}
			if (ran == null || ran.size() >= p.stmts.size() || !p.stmts.subList(0, ran.size()).equals(ran)) {
				p.deny = "앞 [DB 반영] 에서 이미 반영된 문장이 지금 만든 문장의 앞부분과 다릅니다 — 이어서 실행할 수 없습니다. "
						+ "DB 를 직접 확인하세요 (반영된 문장: " + doneJson + ")";
				p.stmts = null;
				p.create = false;
				return p;
			}
			p.done = ran.size();
		}
		return p;
	}

	/**
	 * 109번 #3 — [DB 반영] 확인창. 실제로 실행할 문장(테이블 추가는 승인된 컬럼·제약조건으로 만든 CREATE TABLE)과
	 * 대상 데이터소스, 실행하지 않는 이유를 돌려준다. execDdl 과 같은 판정이다.
	 * 응답 contents = {changeSeq, dsNm, dbmsTp, status: RUN|SKIP|DENY, message, statements}
	 */
	@PostMapping("/ddlPreview")
	public Mono<Response> ddlPreview(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.DDL_EXEC)) throw new IllegalStateException("권한이 없습니다 (DB 반영 권한 필요)");
			Object seq = body.get("changeSeq");
			if (seq == null) throw new IllegalArgumentException("changeSeq 필수");
			DdlPlan p = plan(seq);
			Map<String, Object> out = new java.util.LinkedHashMap<>();
			out.put("changeSeq", seq);
			out.put("dsNm", p.mds == null ? null : p.mds.get("dsn"));
			out.put("dbmsTp", p.mds == null ? null : p.mds.get("dbmsTp"));
			out.put("status", p.deny != null ? "DENY" : p.skip != null ? "SKIP" : "RUN");
			out.put("message", p.deny != null ? p.deny : p.skip != null ? p.skip
					: p.done > 0 ? "앞 실행에서 " + p.done + "문장은 이미 반영됐습니다 — 나머지 " + (p.stmts.size() - p.done) + "문장만 실행합니다" : null);
			out.put("statements", p.stmts == null ? new ArrayList<String>() : p.stmts.subList(p.done, p.stmts.size()));
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out));
		} catch (Exception e) {
			log.error(">> ddlPreview failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * 88번 §8 — 변경 이력의 DDL 을 실제 DB 에 실행.
	 * 104번 WP2 — DB 반영 권한(DDL_EXEC)은 시스템 단위다. 건마다 그 변경의 모델이 속한 시스템
	 * (TB_DATA_MODEL.DM_SYS_CD)에 대한 권한을 본다. 시스템이 없는 모델은 모든 시스템으로 받은 권한만 통한다.
	 * 109번 #2 — 시스템 범위 권한이면 모델의 데이터소스가 그 시스템(하위 포함)의 데이터소스여야 한다.
	 * 104번 WP3 — 실행 대상은 모델의 데이터소스로 고정한다. 조각의 DBMS 가 데이터소스 DBMS 와 다르면 실행하지 않는다.
	 * 테이블 추가는 그 테이블의 승인된 컬럼·제약조건으로 CREATE TABLE 을 만들어 실행하고,
	 * 같은 테이블의 그 뒤 승인된 미반영 변경은 CREATE TABLE 에 포함된 것으로 기록한다.
	 * 109번 #3 — 다른 변경도 이력의 값으로 문장을 다시 만들어(검사 포함) 실행한다. 문장은 한 번에 하나씩 실행하고,
	 * 실행한 문장으로 이력의 조각을 바꾼다. 검사에 걸리면 실행하지 않는다.
	 * 109번 #17 — 여러 문장 중 중간에 실패하면 실행된 문장을 DDL_EXEC_DONE 에 남기고, 다시 실행하면 그 문장들이 지금 만든 문장의
	 * 앞부분과 같을 때 건너뛰고 나머지만 실행한다(다르면 거부). 같은 건을 다른 요청이 실행하는 중이면 그 건은 실패로 센다.
	 * 응답 contents = {success, failed, noPriv, skipped, messages}
	 */
	@PostMapping("/execDdl")
	@SuppressWarnings("unchecked")
	public Mono<Response> execDdl(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.DDL_EXEC)) throw new IllegalStateException("권한이 없습니다 (DB 반영 권한 필요)");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");

			int success = 0, failed = 0, noPriv = 0, skipped = 0;
			List<String> messages = new ArrayList<>();
			String adminId = changeHistory.safeUserId();
			String now = LocalDateTime.now().format(DT_FMT);
			for (Object seq : seqList) {
				// 109번 #17 — 같은 건을 다른 요청이 실행하는 중이면 기다리지 않고 실패로 센다. 그 실행이 끝난 뒤에는 판정이 "이미 반영된 변경" 으로 막는다
				String runKey = String.valueOf(seq);
				if (!ddlRunning.add(runKey)) { failed++; messages.add(seq + ": 다른 요청이 이 변경을 DB 에 반영하고 있습니다"); continue; }
				try {
					DdlPlan p = plan(seq);
					if (p.deny != null) { failed++; messages.add(seq + ": " + p.deny); continue; }
					if (p.skip != null) {
						skipped++;
						messages.add(seq + ": SKIPPED — " + p.skip);
						Map<String, Object> u = new HashMap<>();
						u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
						u.put("ddlExecResult", "SKIPPED"); u.put("ddlExecMessage", p.skip);
						sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
						continue;
					}
					Map<String, Object> hist = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seq);
					String dmId = (String) hist.get("dm_id");
					String owner = hist.get("obj_owner") == null ? "" : (String) hist.get("obj_owner");
					String objNm = (String) hist.get("obj_nm");
					List<String> stmts = p.stmts;
					String ranSnippet = String.join(";\n", stmts) + ";";

					com.ndata.model.DataSourceVo ds = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", p.mds.get("dsId"));
					com.ndata.datasource.dbms.handler.DBHandler h = null;
					// 109번 #17 — 앞 실행에서 반영된 문장은 건너뛰고, 이번에 실행한 문장까지 기록해 둔다(중간에 실패하면 다음 실행이 이어서 한다)
					List<String> ran = new ArrayList<>(stmts.subList(0, p.done));
					try {
						h = dataSourceUtils.getDBHandler(ds);
						for (String s : stmts.subList(p.done, stmts.size())) {
							h.execute(s);
							ran.add(s);
						}
						success++;
						Map<String, Object> u = new HashMap<>();
						u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
						u.put("ddlExecResult", "SUCCESS");
						u.put("ddlExecMessage", "OK (" + ds.getDsn() + ", " + stmts.size() + "문장"
								+ (p.done > 0 ? " — 앞 실행에서 반영된 " + p.done + "문장은 건너뜀" : "") + ")");
						u.put("ddlSnippet", ranSnippet);
						u.put("ddlExecDone", "");
						sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
						if (p.create) {
							Map<String, Object> inc = new HashMap<>();
							inc.put("dmId", dmId); inc.put("objOwner", owner); inc.put("objNm", objNm); inc.put("changeSeq", seq);
							inc.put("ddlExecDt", now); inc.put("ddlExecUserId", adminId);
							inc.put("ddlExecMessage", "테이블 생성(#" + seq + ")에 포함");
							int n = sqlSessionTemplate.update("dmChangeHistory.markIncludedInCreate", inc);
							if (n > 0) messages.add(seq + ": 같은 테이블의 뒤 변경 " + n + "건은 테이블 생성에 포함");
						}
					} catch (Exception ex) {
						String msg = ex.getMessage() == null ? "" : ex.getMessage();
						if (stmts.size() > 1) msg = "(" + (ran.size() + 1) + "/" + stmts.size() + "번째 문장, 앞 " + ran.size() + "문장은 반영됨) " + msg;
						boolean priv = msg.contains("ORA-01031") || msg.contains("insufficient")
								|| msg.contains("denied") || msg.contains("permission");
						String result = priv ? "NO_PRIVILEGE" : "FAILED";
						if (priv) noPriv++; else failed++;
						messages.add(seq + ": " + result + " — " + msg.substring(0, Math.min(200, msg.length())));
						Map<String, Object> u = new HashMap<>();
						u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
						u.put("ddlExecResult", result); u.put("ddlExecMessage", msg.substring(0, Math.min(2000, msg.length())));
						u.put("ddlExecDone", ran.isEmpty() ? "" : JSON.writeValueAsString(ran));
						sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
					} finally {
						if (h != null) try { h.close(); } catch (Exception e) {}
					}
				} finally {
					ddlRunning.remove(runKey);
				}
			}
			Map<String, Object> out = new java.util.LinkedHashMap<>();
			out.put("success", success); out.put("failed", failed); out.put("noPriv", noPriv); out.put("skipped", skipped);
			out.put("messages", messages);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out));
		} catch (Exception e) {
			log.error(">> execDdl failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** '--' 로 시작하는 줄이 있으면 DDL 을 만들 수 없는 변경(규칙 없는 DBMS, 소유자 이동 불가 등) */
	private static boolean hasCommentLine(String ddl) {
		for (String line : ddl.split("\n")) {
			if (line.trim().startsWith("--")) return true;
		}
		return false;
	}

	/** 변경 이력 조회 (관리자·DM_APPROVE: 전체, 그 밖: 본인 + APPROVED) */
	@PostMapping("/history")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> history(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>(body);
		p.put("currentUserId", changeHistory.safeUserId());
		p.put("viewAll",       sessionService.hasPerm(PermCode.DM_APPROVE));
		return sqlSessionTemplate.selectList("dmChangeHistory.selectByDmId", p);
	}
}
