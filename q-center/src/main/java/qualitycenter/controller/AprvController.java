package qualitycenter.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.model.std.StdApproveStatVo;
import com.ndata.quality.model.std.StdDataModelChangeHistoryVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.service.AprvService;

import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP1 — 결재함(마이페이지 > 결재함)과 결재선 설정(관리 > 결재선).
 *
 * <p>결재 처리는 대상별 기존 승인 API 로 보낸다 — 표준사전 항목은 {@code putStdAprvStat}, 데이터 모델 신청은
 * {@code dmApproval/approve·reject}. 그 API 들이 결재 문서의 단계를 진행하고, 마지막 단계에서만 기존 처리를 한다.</p>
 */
@RestController
@RequestMapping("/api/aprv")
public class AprvController {

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final List<String> BOXES = java.util.Arrays.asList("TURN", "MINE", "DONE", "ALL");

	@Autowired private AprvService aprvService;
	@Autowired private SessionService sessionService;
	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SqlSessionFactory sqlSessionFactory;
	@Autowired private DataStandardController dataStandardController;
	@Autowired private DataModelApprovalController dataModelApprovalController;

	private Response ok(Object data) throws JsonProcessingException {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		r.setContents(new ObjectMapper().writeValueAsString(data));
		return r;
	}

	private static Response fail(int code, String msg) {
		Response r = new Response();
		r.setResultInfo(code, msg);
		return r;
	}

	// ===== 결재함 =====

	/** 결재함 목록 — box: TURN(내 차례) / MINE(내가 올린) / DONE(내가 처리한) / ALL(진행 중 전체, 관리자) */
	@GetMapping("/box")
	public Mono<Response> box(@RequestParam("box") String box, @RequestParam(value = "reqTp", required = false) String reqTp)
			throws JsonProcessingException {
		String userId = sessionService.getUserId();
		// 109번 #7 — 그 밖의 값은 매퍼에서 "진행 중 전체" 로 떨어져 관리자 검사를 건너뛰었다
		if (!BOXES.contains(box)) return Mono.just(fail(400, "결재함은 TURN·MINE·DONE·ALL 중 하나입니다: " + box));
		if ("ALL".equals(box) && !sessionService.isAdmin()) return Mono.just(fail(403, "권한이 없습니다 (관리자만)"));
		Map<String, Object> p = new HashMap<>();
		p.put("box", box);
		p.put("userId", userId);
		p.put("reqTp", reqTp);
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("aprv.selectBox", p);
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			// 대상이 다른 경로로 없어진 진행 중 문서는 닫는다 — 내 차례 목록에서는 뺀다
			if (aprvService.cancelIfGone(r) && "TURN".equals(box)) continue;
			r.put("reqTpNm", AprvService.reqTpNm((String) r.get("reqTp")));
			out.add(r);
		}
		return Mono.just(ok(out));
	}

	/** 내 차례 건수 — 헤더·메뉴 배지 */
	@GetMapping("/turnCount")
	public int turnCount() {
		return sqlSessionTemplate.selectOne("aprv.countTurn", sessionService.getUserId());
	}

	/** 대상들의 진행 중 결재 문서 — 기존 승인 화면 목록에 "결재 n/m 단계 · 현재 결재자" 를 보인다. {targetIds: [..]} → {targetId: 문서} */
	@PostMapping("/openDocs")
	@SuppressWarnings("unchecked")
	public Map<String, Object> openDocs(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new HashMap<>();
		List<String> ids = (List<String>) body.get("targetIds");
		if (ids == null || ids.isEmpty()) return out;
		Map<String, Object> p = new HashMap<>();
		p.put("targetIds", ids);
		for (Map<String, Object> d : sqlSessionTemplate.<Map<String, Object>>selectList("aprv.selectOpenDocsByTargets", p)) {
			out.put((String) d.get("targetId"), d);
		}
		return out;
	}

	/** 문서 상세 — 단계별 결재자, 대상 내용, 내가 처리할 수 있는지. 요청자·결재자·관리자·그 유형의 승인 권한자만 */
	@GetMapping("/doc")
	public Mono<Response> doc(@RequestParam("docId") String docId) throws JsonProcessingException {
		String userId = sessionService.getUserId();
		Map<String, Object> doc = aprvService.doc(docId);
		if (doc == null) return Mono.just(fail(404, "결재 문서가 없습니다."));
		String reqTp = (String) doc.get("reqTp");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> steps = (List<Map<String, Object>>) doc.get("steps");
		boolean related = userId.equals(doc.get("reqUserId")) || sessionService.isAdmin()
				|| steps.stream().anyMatch(s -> userId.equals(s.get("aprvrUserId")))
				|| ("DM_CHANGE".equals(reqTp) ? sessionService.hasPerm(PermCode.DM_APPROVE) : sessionService.hasPerm(PermCode.STD_APPROVE));
		if (!related) return Mono.just(fail(403, "이 결재를 볼 권한이 없습니다."));
		aprvService.cancelIfGone(doc);
		doc.put("reqTpNm", AprvService.reqTpNm(reqTp));
		doc.put("canAct", aprvService.canAct(docId, userId));
		Map<String, Object> key = new HashMap<>();
		key.put("reqTp", reqTp);
		key.put("targetId", doc.get("targetId"));
		if ("DM_CHANGE".equals(reqTp)) {
			doc.put("items", sqlSessionTemplate.selectList("dmChangeHistory.selectBySubmissionId", doc.get("targetId")));
		} else {
			doc.put("target", sqlSessionTemplate.selectOne("aprv.selectStdTarget", key));
		}
		return Mono.just(ok(doc));
	}

	/** 결재 — {docId, approve, comment}. 대상별 기존 승인 API 로 보낸다 */
	@PostMapping("/act")
	public Mono<Response> act(@RequestBody Map<String, Object> body) {
		String docId = (String) body.get("docId");
		boolean approve = Boolean.TRUE.equals(body.get("approve"));
		String comment = (String) body.get("comment");
		Map<String, Object> doc = aprvService.doc(docId);
		if (doc == null) return Mono.just(fail(404, "결재 문서가 없습니다."));
		if (!approve && (comment == null || comment.trim().isEmpty())) return Mono.just(fail(500, "반려 사유는 필수입니다."));
		String reqTp = (String) doc.get("reqTp");
		if ("DM_CHANGE".equals(reqTp)) {
			List<StdDataModelChangeHistoryVo> items = sqlSessionTemplate.selectList("dmChangeHistory.selectBySubmissionId", doc.get("targetId"));
			List<Object> seqs = new ArrayList<>();
			for (StdDataModelChangeHistoryVo i : items) {
				if ("SUBMITTED".equals(i.getAprvStatus())) seqs.add(i.getChangeSeq());
			}
			Map<String, Object> b = new HashMap<>();
			b.put("changeSeqList", seqs);
			b.put("aprvComment", comment);
			return approve ? dataModelApprovalController.approve(b) : dataModelApprovalController.reject(b);
		}
		return dataStandardController.putStdAprvStat(stdAprvItem(doc, approve, comment));
	}

	/** 표준사전 결재 문서의 대상 항목을 표준 승인(putStdAprvStat)에 넘길 한 건으로 */
	private List<StdApproveStatVo> stdAprvItem(Map<String, Object> doc, boolean approve, String comment) {
		Map<String, Object> key = new HashMap<>();
		key.put("reqTp", doc.get("reqTp"));
		key.put("targetId", doc.get("targetId"));
		Map<String, Object> target = sqlSessionTemplate.selectOne("aprv.selectStdTarget", key);
		StdApproveStatVo vo = new StdApproveStatVo();
		vo.setReqTp((String) doc.get("reqTp"));
		vo.setReqItemId((String) doc.get("targetId"));
		vo.setReqItemNm(target == null ? (String) doc.get("targetNm") : (String) target.get("nm"));
		vo.setAprvStat((short) (approve ? 2 : 3));
		vo.setAprvStatUpdtRsn(comment);
		List<StdApproveStatVo> list = new ArrayList<>();
		list.add(vo);
		return list;
	}

	/**
	 * 결재자 수정 후 승인 — 표준사전 항목. {docId, engNm, engAbrvNm, desc, comment}.
	 * 현재 단계 결재자(관리자 대결 포함)가 승인 대기 항목의 설명(단어는 영문명·영문약어도)을 고치고 바로 승인한다.
	 * 변경 이력에 "결재자 수정"(APPROVER_EDIT)으로 남긴다.
	 * 109번 #4 — 수정·변경 이력과 결재 단계 진행·승인을 한 트랜잭션으로 한다 (승인이 실패하면 수정도 되돌린다).
	 * 109번 #11 — 단어는 등록과 같은 검사(형식·길이·사전 운영 옵션)를 하고, 그 단어를 쓰는 승인 대기 용어가 있으면 영문약어를 못 바꾼다.
	 */
	@PostMapping("/editAndApprove")
	public Mono<Response> editAndApprove(@RequestBody Map<String, Object> body) {
		String docId = (String) body.get("docId");
		String userId = sessionService.getUserId();
		Map<String, Object> doc = aprvService.doc(docId);
		if (doc == null) return Mono.just(fail(404, "결재 문서가 없습니다."));
		String reqTp = (String) doc.get("reqTp");
		if ("DM_CHANGE".equals(reqTp)) return Mono.just(fail(500, "데이터 모델 변경은 수정 후 승인을 할 수 없습니다."));
		if (!aprvService.canAct(docId, userId)) return Mono.just(fail(403, "이 결재의 현재 단계 결재자가 아닙니다."));
		String desc = body.get("desc") == null ? "" : ((String) body.get("desc")).trim();
		if (desc.isEmpty()) return Mono.just(fail(500, "설명은 필수입니다."));
		if (desc.length() > 1000) return Mono.just(fail(500, "설명이 너무 깁니다 (최대 1000자)."));

		Map<String, Object> key = new HashMap<>();
		key.put("reqTp", reqTp);
		key.put("targetId", doc.get("targetId"));
		Map<String, Object> before = sqlSessionTemplate.selectOne("aprv.selectStdTarget", key);
		if (before == null || !"N".equals(before.get("aprvYn"))) return Mono.just(fail(500, "승인 대기 항목이 아닙니다."));

		Map<String, Object> p = new HashMap<>(key);
		p.put("desc", desc);
		p.put("now", LocalDateTime.now().format(DT_FMT));
		p.put("userId", userId);
		String stmt;
		if ("WORD".equals(reqTp)) {
			String engNm = body.get("engNm") == null ? "" : ((String) body.get("engNm")).trim();
			String abrv = body.get("engAbrvNm") == null ? "" : ((String) body.get("engAbrvNm")).trim();
			if (engNm.isEmpty() || abrv.isEmpty()) return Mono.just(fail(500, "영문명과 영문약어는 필수입니다."));
			// 109번 #11 — 등록·수정과 같은 검사 (영문명·영문약어 형식·길이, 사전 운영 옵션: 표준 대문자·영문명 중복)
			StdWordVo w = new StdWordVo();
			w.setId((String) doc.get("targetId"));
			w.setDictId((String) before.get("dictId"));
			w.setWordNm((String) before.get("nm"));
			w.setWordEngNm(engNm);
			w.setWordEngAbrvNm(abrv);
			try {
				dataStandardController.validateWordInput(w);
			} catch (Exception e) {
				return Mono.just(fail(500, e.getMessage()));
			}
			abrv = w.getWordEngAbrvNm();
			// 109번 #11 — 용어의 영문약어는 구성 단어의 약어로 만든다. 그 단어를 쓰는 승인 대기 용어가 있으면 약어를 바꾸면 어긋난다
			if (!abrv.equals(before.get("engAbrvNm"))) {
				List<Map<String, Object>> terms = sqlSessionTemplate.selectList("approve.selectUnapprovedTermsByWordId", doc.get("targetId"));
				if (!terms.isEmpty()) {
					List<String> nms = new ArrayList<>();
					for (Map<String, Object> t : terms) nms.add((String) t.get("termsNm"));
					return Mono.just(fail(500, "이 단어를 쓰는 승인 대기 용어가 있어 영문약어를 바꿀 수 없습니다: " + String.join(", ", nms)
							+ " (영문약어는 그대로 두고 승인하거나, 용어를 먼저 처리하세요)"));
				}
			}
			p.put("engNm", engNm);
			p.put("engAbrvNm", abrv);
			p.put("dictId", before.get("dictId"));
			int dup = sqlSessionTemplate.selectOne("aprv.countWordEngAbrvDup", p);
			if (dup > 0) return Mono.just(fail(500, "같은 사전에 이미 있는 영문약어입니다: " + abrv));
			stmt = "aprv.updateWordByApprover";
		} else if ("DOMAIN".equals(reqTp)) {
			stmt = "aprv.updateDomainDescByApprover";
		} else {
			stmt = "aprv.updateTermsDescByApprover";
		}
		Response r = dataStandardController.aprvItems(stdAprvItem(doc, true, (String) body.get("comment")), s -> {
			if (s.update(stmt, p) == 0) throw new IllegalStateException("승인 대기 항목이 아닙니다.");
			Map<String, Object> h = new HashMap<>();
			h.put("changeId", com.ndata.module.StringUtils.getUUID());
			h.put("changeType", "UPDATE");
			h.put("targetType", "TERMS".equals(reqTp) ? "TERM" : reqTp);
			h.put("targetId", doc.get("targetId"));
			h.put("targetNm", before.get("nm"));
			h.put("changeCnt", 1);
			h.put("summary", "결재자 수정: " + before.get("nm"));
			h.put("prevValue", before.toString());
			h.put("currValue", s.selectOne("aprv.selectStdTarget", key).toString());
			h.put("changeUserId", userId);
			h.put("changeSource", "APPROVER_EDIT");
			s.insert("changehistory.insertChangeHistory", h);
		});
		return Mono.just(r);
	}

	// ===== 결재선 설정 (관리 > 결재선, USER_ADMIN) =====

	private Response deniedLine() {
		return sessionService.hasPerm(PermCode.USER_ADMIN) ? null : fail(403, "권한이 없습니다 (사용자·권한 관리 권한 필요)");
	}

	/** 요청 유형 5개 각각의 결재선과 단계 */
	@GetMapping("/lines")
	public Mono<Response> lines() throws JsonProcessingException {
		Response d = deniedLine();
		if (d != null) return Mono.just(d);
		List<Map<String, Object>> out = new ArrayList<>();
		for (String reqTp : AprvService.REQ_TYPES) {
			Map<String, Object> line = sqlSessionTemplate.selectOne("aprv.selectLine", reqTp);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("reqTp", reqTp);
			m.put("reqTpNm", AprvService.reqTpNm(reqTp));
			m.put("lineNm", line == null ? null : line.get("lineNm"));
			m.put("useYn", line == null ? "N" : line.get("useYn"));
			m.put("updtDt", line == null ? null : line.get("updtDt"));
			m.put("steps", sqlSessionTemplate.selectList("aprv.selectLineSteps", reqTp));
			out.add(m);
		}
		return Mono.just(ok(out));
	}

	/** 결재선 저장 — {reqTp, lineNm, useYn, steps: [{stepNm, aprvrTp, aprvrId, agreeYn}]}. 단계는 순서대로 다시 만든다 */
	@PostMapping("/line/save")
	@SuppressWarnings("unchecked")
	public Mono<Response> saveLine(@RequestBody Map<String, Object> body) {
		Response d = deniedLine();
		if (d != null) return Mono.just(d);
		String reqTp = (String) body.get("reqTp");
		String useYn = "Y".equals(body.get("useYn")) ? "Y" : "N";
		List<Map<String, Object>> steps = body.get("steps") == null ? new ArrayList<>() : (List<Map<String, Object>>) body.get("steps");
		if (!AprvService.REQ_TYPES.contains(reqTp)) return Mono.just(fail(500, "없는 요청 유형입니다: " + reqTp));
		if ("Y".equals(useYn) && steps.isEmpty()) return Mono.just(fail(500, "결재선을 쓰려면 단계가 하나 이상 있어야 합니다."));
		if (steps.size() > 10) return Mono.just(fail(500, "단계는 10개까지입니다."));
		String lineNm = body.get("lineNm") == null ? null : ((String) body.get("lineNm")).trim();
		if (lineNm != null && lineNm.length() > 100) return Mono.just(fail(500, "결재선 이름이 너무 깁니다 (최대 100자)."));
		int no = 0;
		for (Map<String, Object> st : steps) {
			no++;
			String tp = (String) st.get("aprvrTp");
			String id = (String) st.get("aprvrId");
			if (!AprvService.APRVR_TYPES.contains(tp)) return Mono.just(fail(500, no + "단계: 결재자 방식을 고르세요."));
			if ("USER".equals(tp) && (id == null || (int) sqlSessionTemplate.selectOne("aprv.countActiveUser", id) == 0))
				return Mono.just(fail(500, no + "단계: 없는 사용자입니다: " + id));
			if ("ROLE".equals(tp) && (id == null || sqlSessionTemplate.selectOne("role.selectRole", id) == null))
				return Mono.just(fail(500, no + "단계: 없는 역할입니다: " + id));
			String stepNm = st.get("stepNm") == null ? null : ((String) st.get("stepNm")).trim();
			if (stepNm != null && stepNm.length() > 100) return Mono.just(fail(500, no + "단계: 단계 이름이 너무 깁니다 (최대 100자)."));
		}
		String now = LocalDateTime.now().format(DT_FMT);
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> p = new HashMap<>();
			p.put("reqTp", reqTp);
			p.put("lineNm", lineNm);
			p.put("useYn", useYn);
			p.put("now", now);
			p.put("userId", sessionService.getUserId());
			s.insert("aprv.upsertLine", p);
			s.delete("aprv.deleteLineSteps", reqTp);
			no = 0;
			for (Map<String, Object> st : steps) {
				no++;
				Map<String, Object> sp = new HashMap<>();
				sp.put("reqTp", reqTp);
				sp.put("stepNo", no);
				sp.put("stepNm", st.get("stepNm") == null ? null : ((String) st.get("stepNm")).trim());
				sp.put("aprvrTp", st.get("aprvrTp"));
				sp.put("aprvrId", "USER".equals(st.get("aprvrTp")) || "ROLE".equals(st.get("aprvrTp")) ? st.get("aprvrId") : null);
				sp.put("agreeYn", "Y".equals(st.get("agreeYn")) ? "Y" : "N");
				s.insert("aprv.insertLineStep", sp);
			}
			s.commit();
		}
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		return Mono.just(r);
	}
}
