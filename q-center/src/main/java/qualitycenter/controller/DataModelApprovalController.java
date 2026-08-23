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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
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
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"submitted\":" + n + ",\"submissionId\":\"" + submissionId + "\"}");
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

	/** 관리자 — SUBMITTED 묶음 목록 (승인 화면용) */
	@PostMapping("/submissions")
	public List<Map<String, Object>> submissions(@RequestBody Map<String, Object> body) {
		if (!sessionService.isAdmin()) return new ArrayList<>();
		Map<String, Object> p = new HashMap<>();
		p.put("changeUserId", body.get("changeUserId"));
		return sqlSessionTemplate.selectList("dmChangeHistory.selectSubmissions", p);
	}

	/** 관리자 — 특정 묶음의 항목들 */
	@PostMapping("/submissionItems")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> submissionItems(@RequestBody Map<String, Object> body) {
		if (!sessionService.isAdmin()) return new ArrayList<>();
		String submissionId = (String) body.get("submissionId");
		return sqlSessionTemplate.selectList("dmChangeHistory.selectBySubmissionId", submissionId);
	}

	/** 관리자 — 선택 항목 승인 */
	@PostMapping("/approve")
	@SuppressWarnings("unchecked")
	public Mono<Response> approve(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			String now = LocalDateTime.now().format(DT_FMT);
			String adminId = changeHistory.safeUserId();
			int n = 0;
			for (Object seq : seqList) {
				n += applyApprovalStatus(seq, "APPROVED", adminId, now, body.get("aprvComment"));
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"approved\":" + n + "}");
		} catch (Exception e) {
			log.error(">> approve failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 관리자 — 선택 항목 반려 + 사유 */
	@PostMapping("/reject")
	@SuppressWarnings("unchecked")
	public Mono<Response> reject(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			String now = LocalDateTime.now().format(DT_FMT);
			String adminId = changeHistory.safeUserId();
			int n = 0;
			for (Object seq : seqList) {
				n += applyApprovalStatus(seq, "REJECTED", adminId, now, body.get("aprvComment"));
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"rejected\":" + n + "}");
		} catch (Exception e) {
			log.error(">> reject failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
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
			if ("REJECTED".equals(status) && changeType != null && changeType.startsWith("MODIFY_")) {
				restoreFromBeforeJson(hist, changeType, dmId, objOwner, objNm, attrNm);
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
				}
			}
		}
		return n;
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
				before.put("currObjOwner", objOwner);
				before.put("currObjNm",    objNm);
				int r = sqlSessionTemplate.update("datamodel.restoreObjFromBefore", before);
				log.info(">> MODIFY_OBJ 반려 — {}건 원복 {}.{}", r, objOwner, objNm);
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

	/** 88번 §8 — 관리자: 변경 이력의 DDL_SNIPPET 을 실제 DB 에 실행 */
	@PostMapping("/execDdl")
	@SuppressWarnings("unchecked")
	public Mono<Response> execDdl(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			String dsId = (String) body.get("dsId");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			if (dsId == null || dsId.isEmpty()) throw new IllegalArgumentException("dsId 필수");

			com.ndata.model.DataSourceVo ds = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
			if (ds == null) throw new IllegalStateException("데이터소스 없음: " + dsId);

			int success = 0, failed = 0, noPriv = 0;
			java.util.List<String> messages = new java.util.ArrayList<>();
			String adminId = changeHistory.safeUserId();
			String now = LocalDateTime.now().format(DT_FMT);
			for (Object seq : seqList) {
				Map<String, Object> hist = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", seq);
				if (hist == null) { failed++; messages.add(seq + ": history 없음"); continue; }
				String ddl = (String) hist.get("ddl_snippet");
				if (ddl == null || ddl.trim().isEmpty() || ddl.startsWith("--") || ddl.contains("(...)")) {
					failed++; messages.add(seq + ": 실행 가능한 DDL 없음 (snippet=" + ddl + ")");
					Map<String, Object> u = new HashMap<>();
					u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
					u.put("ddlExecResult", "SKIPPED"); u.put("ddlExecMessage", "DDL 미완성 또는 미지원");
					sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
					continue;
				}
				com.ndata.datasource.dbms.handler.DBHandler h = null;
				try {
					h = dataSourceUtils.getDBHandler(ds);
					h.execute(ddl);
					success++;
					Map<String, Object> u = new HashMap<>();
					u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
					u.put("ddlExecResult", "SUCCESS"); u.put("ddlExecMessage", "OK");
					sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
				} catch (Exception ex) {
					String msg = ex.getMessage() == null ? "" : ex.getMessage();
					boolean priv = msg.contains("ORA-01031") || msg.contains("insufficient")
							|| msg.contains("denied") || msg.contains("permission");
					String result = priv ? "NO_PRIVILEGE" : "FAILED";
					if (priv) noPriv++; else failed++;
					messages.add(seq + ": " + result + " — " + msg.substring(0, Math.min(200, msg.length())));
					Map<String, Object> u = new HashMap<>();
					u.put("changeSeq", seq); u.put("ddlExecDt", now); u.put("ddlExecUserId", adminId);
					u.put("ddlExecResult", result); u.put("ddlExecMessage", msg.substring(0, Math.min(2000, msg.length())));
					sqlSessionTemplate.update("dmChangeHistory.updateDdlExecResult", u);
				} finally {
					if (h != null) try { h.close(); } catch (Exception e) {}
				}
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"success\":" + success + ",\"failed\":" + failed + ",\"noPriv\":" + noPriv + "}");
		} catch (Exception e) {
			log.error(">> execDdl failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 변경 이력 조회 (관리자: 전체, 사용자: 본인 + APPROVED) */
	@PostMapping("/history")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> history(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>(body);
		p.put("currentUserId", changeHistory.safeUserId());
		p.put("isAdmin",       sessionService.isAdmin());
		return sqlSessionTemplate.selectList("dmChangeHistory.selectByDmId", p);
	}
}
