package com.ndata.quality.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 104번 WP1 — 결재 엔진.
 *
 * <p>두 승인 흐름(표준사전 APRV_YN·TB_APRV_STATS, 데이터 모델 DRAFT→SUBMITTED→APPROVED/REJECTED)의 상태는 그대로 두고
 * 그 위에 결재 문서(TB_APRV_DOC)를 얹는다. 요청 유형에 결재선(TB_APRV_LINE, USE_YN='Y')이 있으면 요청 때 {@link #open} 이
 * 문서와 단계별 결재자를 만들고, {@link #act} 가 단계를 진행한다. 마지막 단계 승인·어느 단계든 반려가 나면 호출한 쪽이
 * 기존 승인·반려 처리를 그대로 부른다. 결재선이 없는 유형은 문서를 만들지 않으므로 지금처럼 동작한다.</p>
 *
 * <p>결재자는 문서를 만들 때 모든 단계에 대해 정해 둔다(USER 지정 사용자, ROLE 역할의 사용자, ADMIN 관리자 전원, ORG_HEAD 요청자 부서장 —
 * 요청자가 부서장이면 위로 올라가며 요청자가 아닌 첫 부서장). 요청자 자신은 빼고, 남는 사람이 없으면 관리자 전원.
 * 관리자는 현재 단계를 대신 처리할 수 있다(그 단계의 대기 결재자 전원 몫으로 기록, 실제 처리자는 ACT_USER_ID).</p>
 *
 * <p>q-center(화면 요청)와 q-executor(엑셀 일괄등록)가 함께 쓰므로 q-common 에 있다. 알림은 TB_NOTIFICATION 에 남기고,
 * 화면 실시간 전달은 돌려받은 {@link Noti} 목록으로 호출한 쪽이 한다.</p>
 */
@Service
public class AprvService {

	public static final List<String> REQ_TYPES = Arrays.asList("WORD", "TERMS", "CODE", "DOMAIN", "DM_CHANGE");
	public static final List<String> APRVR_TYPES = Arrays.asList("USER", "ROLE", "ORG_HEAD", "ADMIN");

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	@Autowired
	private PermService permService;

	/** 만든 알림 — 호출한 쪽이 WebSocket 으로 그 사용자에게 보낸다 */
	public static class Noti {
		public final String userId;
		public final String notiTp;
		public final String title;
		public final String msg;
		public final String docId;

		Noti(String userId, String notiTp, String title, String msg, String docId) {
			this.userId = userId;
			this.notiTp = notiTp;
			this.title = title;
			this.msg = msg;
			this.docId = docId;
		}
	}

	/** open·act 결과 */
	public static class Result {
		public final String docId;
		/** 문서가 끝났는지 (최종 승인 또는 반려) */
		public final boolean done;
		/** 승인 쪽인지 (done 이면 최종 승인, 아니면 단계 승인) — 반려면 false */
		public final boolean approved;
		public final String message;
		public final List<Noti> notis;

		Result(String docId, boolean done, boolean approved, String message, List<Noti> notis) {
			this.docId = docId;
			this.done = done;
			this.approved = approved;
			this.message = message;
			this.notis = notis;
		}
	}

	public static String reqTpNm(String reqTp) {
		switch (reqTp) {
			case "WORD": return "단어";
			case "TERMS": return "용어";
			case "CODE": return "코드";
			case "DOMAIN": return "도메인";
			case "DM_CHANGE": return "데이터 모델 변경";
			default: return reqTp;
		}
	}

	private static String now() {
		return LocalDateTime.now().format(DT_FMT);
	}

	/**
	 * 요청 유형에 켜진 결재선이 있으면 결재 문서를 만들고 1단계 결재자에게 알린다. 결재선이 없거나, 그 대상에 이미 진행 중
	 * 문서가 있으면 null.
	 */
	public Result open(String reqTp, String targetId, String targetNm, String dmId, String dictId, String reqUserId) {
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> line = s.selectOne("aprv.selectLine", reqTp);
			if (line == null || !"Y".equals(line.get("useYn"))) return null;
			List<Map<String, Object>> steps = s.selectList("aprv.selectLineSteps", reqTp);
			if (steps.isEmpty()) return null;
			Map<String, Object> key = new HashMap<>();
			key.put("reqTp", reqTp);
			key.put("targetId", targetId);
			if (s.selectOne("aprv.selectOpenDoc", key) != null) return null;

			String docId = UUID.randomUUID().toString();
			String now = now();
			Map<String, Object> doc = new HashMap<>();
			doc.put("docId", docId);
			doc.put("reqTp", reqTp);
			doc.put("targetId", targetId);
			doc.put("targetNm", targetNm);
			doc.put("dmId", dmId);
			doc.put("dictId", dictId);
			doc.put("reqUserId", reqUserId);
			doc.put("now", now);
			doc.put("stepCnt", steps.size());
			s.insert("aprv.insertDoc", doc);

			List<String> firstAprvrs = null;
			String firstStepNm = null;
			for (Map<String, Object> st : steps) {
				int stepNo = ((Number) st.get("stepNo")).intValue();
				List<String> aprvrs = resolveAprvrs(s, (String) st.get("aprvrTp"), (String) st.get("aprvrId"), reqUserId);
				for (String u : aprvrs) {
					Map<String, Object> p = new HashMap<>();
					p.put("docId", docId);
					p.put("stepNo", stepNo);
					p.put("aprvrUserId", u);
					p.put("stepNm", st.get("stepNm"));
					p.put("agreeYn", st.get("agreeYn"));
					s.insert("aprv.insertDocStep", p);
				}
				if (stepNo == 1) {
					firstAprvrs = aprvrs;
					firstStepNm = (String) st.get("stepNm");
				}
			}
			List<Noti> notis = new ArrayList<>();
			String reqUserNm = s.selectOne("aprv.selectUserNm", reqUserId);
			for (String u : firstAprvrs) {
				notis.add(noti(s, u, "APRV_REQ", "결재 요청: " + reqTpNm(reqTp) + " " + targetNm,
						reqUserNm + " 님의 요청 — 1/" + steps.size() + " 단계" + (firstStepNm == null ? "" : " (" + firstStepNm + ")"), docId, now));
			}
			s.commit();
			return new Result(docId, false, false, "결재 요청 — 1/" + steps.size() + " 단계", notis);
		}
	}

	/** 단계의 결재자. 요청자는 빼고, 아무도 없으면 관리자 전원 */
	private List<String> resolveAprvrs(SqlSession s, String aprvrTp, String aprvrId, String reqUserId) {
		Set<String> out = new LinkedHashSet<>();
		if ("USER".equals(aprvrTp)) {
			int active = s.selectOne("aprv.countActiveUser", aprvrId);
			if (active > 0) out.add(aprvrId);
		} else if ("ROLE".equals(aprvrTp)) {
			out.addAll(s.<String>selectList("aprv.selectRoleUserIds", aprvrId));
		} else if ("ADMIN".equals(aprvrTp)) {
			out.addAll(s.<String>selectList("aprv.selectAdminUserIds"));
		} else if ("ORG_HEAD".equals(aprvrTp)) {
			String orgId = s.selectOne("aprv.selectUserOrgId", reqUserId);
			for (int depth = 0; orgId != null && depth < 20; depth++) {
				Map<String, Object> org = s.selectOne("aprv.selectOrgHead", orgId);
				if (org == null) break;
				String head = (String) org.get("headUserId");
				if (head != null && !head.equals(reqUserId) && (int) s.selectOne("aprv.countActiveUser", head) > 0) {
					out.add(head);
					break;
				}
				orgId = (String) org.get("pOrgId");
			}
		}
		out.remove(reqUserId);
		if (out.isEmpty()) {
			out.addAll(s.<String>selectList("aprv.selectAdminUserIds"));
			if (out.size() > 1) out.remove(reqUserId);
		}
		return new ArrayList<>(out);
	}

	private Noti noti(SqlSession s, String userId, String notiTp, String title, String msg, String docId, String now) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("notiTp", notiTp);
		p.put("title", title.length() > 200 ? title.substring(0, 200) : title);
		p.put("msg", msg);
		p.put("linkTp", "APRV_DOC");
		p.put("linkId", docId);
		p.put("now", now);
		s.insert("aprv.insertNoti", p);
		return new Noti(userId, notiTp, (String) p.get("title"), msg, docId);
	}

	/** 대상의 진행 중 문서 (없으면 null) */
	public Map<String, Object> openDoc(String reqTp, String targetId) {
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> key = new HashMap<>();
			key.put("reqTp", reqTp);
			key.put("targetId", targetId);
			return s.selectOne("aprv.selectOpenDoc", key);
		}
	}

	/** 문서 + 단계별 결재자 */
	public Map<String, Object> doc(String docId) {
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> doc = s.selectOne("aprv.selectDoc", docId);
			if (doc == null) return null;
			Map<String, Object> out = new LinkedHashMap<>(doc);
			out.put("steps", s.selectList("aprv.selectDocSteps", docId));
			return out;
		}
	}

	/** 그 사용자가 문서의 현재 단계를 처리할 수 있는지 — 현재 단계의 대기 결재자이거나 관리자 */
	public boolean canAct(String docId, String userId) {
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> doc = s.selectOne("aprv.selectDoc", docId);
			if (doc == null || !"PROGRESS".equals(doc.get("docStat"))) return false;
			if (permService.isAdmin(userId)) return true;
			return myWaitRow(s, docId, ((Number) doc.get("curStepNo")).intValue(), userId) != null;
		}
	}

	private Map<String, Object> myWaitRow(SqlSession s, String docId, int stepNo, String userId) {
		for (Map<String, Object> r : s.<Map<String, Object>>selectList("aprv.selectDocSteps", docId)) {
			if (((Number) r.get("stepNo")).intValue() == stepNo && userId.equals(r.get("aprvrUserId")) && "WAIT".equals(r.get("actStat"))) return r;
		}
		return null;
	}

	/**
	 * 현재 단계 결재. 반려는 어느 단계든 문서를 끝내고(done=true, approved=false), 승인은 단계가 다 차면(합의 Y 는 전원,
	 * N 은 한 명) 다음 단계로, 마지막 단계면 문서를 끝낸다(done=true, approved=true).
	 *
	 * <p>109번 #4 — 호출한 쪽의 세션 s 에서 돌고 커밋하지 않는다. 호출한 쪽이 단계 진행과 그 뒤 항목 처리(최종 승인·반려)를
	 * 한 번에 커밋하거나 되돌린다. 알림(TB_NOTIFICATION)도 같은 트랜잭션이라, 화면 전달은 커밋한 뒤에 한다.
	 * 109번 #10 — 문서 행을 잠그고(FOR UPDATE) 시작해 같은 문서의 결재가 차례로 돌고, 단계의 승인 수는 갱신한 뒤 DB 에서 다시 센다
	 * (합의 결재자 둘이 동시에 승인하면 서로 상대를 대기로 보고 단계가 멈췄다).</p>
	 */
	public Result act(SqlSession s, String docId, String userId, boolean approve, String comment) {
		if (!approve && (comment == null || comment.trim().isEmpty())) throw new IllegalArgumentException("반려 사유는 필수입니다.");
		s.selectOne("aprv.lockDoc", docId);
		Map<String, Object> doc = s.selectOne("aprv.selectDoc", docId);
		if (doc == null || !"PROGRESS".equals(doc.get("docStat"))) throw new IllegalArgumentException("진행 중인 결재 문서가 아닙니다.");
		int cur = ((Number) doc.get("curStepNo")).intValue();
		int stepCnt = ((Number) doc.get("stepCnt")).intValue();
		boolean admin = permService.isAdmin(userId);
		List<Map<String, Object>> curRows = stepRows(s, docId, cur);
		Map<String, Object> mine = null;
		for (Map<String, Object> r : curRows) {
			if (userId.equals(r.get("aprvrUserId")) && "WAIT".equals(r.get("actStat"))) mine = r;
		}
		if (mine == null && !admin) throw new IllegalArgumentException("이 결재의 현재 단계 결재자가 아닙니다.");

		String now = now();
		String actStat = approve ? "APPROVED" : "REJECTED";
		// 결재자 본인이면 자기 몫만, 관리자 대결이면 그 단계 대기 결재자 전원 몫
		for (Map<String, Object> r : curRows) {
			if (!"WAIT".equals(r.get("actStat"))) continue;
			if (mine != null && r != mine) continue;
			Map<String, Object> u = new HashMap<>();
			u.put("docId", docId);
			u.put("stepNo", cur);
			u.put("aprvrUserId", r.get("aprvrUserId"));
			u.put("actStat", actStat);
			u.put("actCmnt", comment);
			u.put("actUserId", userId);
			u.put("now", now);
			s.update("aprv.updateStepAct", u);
		}

		String reqTp = (String) doc.get("reqTp");
		String targetNm = (String) doc.get("targetNm");
		String reqUserId = (String) doc.get("reqUserId");
		String actorNm = s.selectOne("aprv.selectUserNm", userId);
		String stepNm = curRows.isEmpty() ? null : (String) curRows.get(0).get("stepNm");
		String stepLabel = cur + "/" + stepCnt + " 단계" + (stepNm == null ? "" : " (" + stepNm + ")");
		List<Noti> notis = new ArrayList<>();

		if (!approve) {
			updateDoc(s, docId, "REJECTED", cur, now);
			notis.add(noti(s, reqUserId, "APRV_REJECT", "반려: " + reqTpNm(reqTp) + " " + targetNm,
					stepLabel + " " + actorNm + " — " + comment, docId, now));
			return new Result(docId, true, false, stepLabel + " 반려", notis);
		}

		boolean agree = !curRows.isEmpty() && "Y".equals(curRows.get(0).get("agreeYn"));
		List<Map<String, Object>> after = stepRows(s, docId, cur);
		long approvedCnt = after.stream().filter(r -> "APPROVED".equals(r.get("actStat"))).count();
		boolean stepDone = agree ? approvedCnt == after.size() : approvedCnt > 0;
		if (!stepDone) {
			return new Result(docId, false, true, stepLabel + " 합의 진행 (" + approvedCnt + "/" + after.size() + ")", notis);
		}
		if (cur >= stepCnt) {
			updateDoc(s, docId, "APPROVED", cur, now);
			notis.add(noti(s, reqUserId, "APRV_DONE", "최종 승인: " + reqTpNm(reqTp) + " " + targetNm,
					stepLabel + " " + actorNm + " 승인으로 결재가 끝났습니다", docId, now));
			return new Result(docId, true, true, stepLabel + " 승인 — 최종 승인", notis);
		}
		updateDoc(s, docId, "PROGRESS", cur + 1, null);
		String reqUserNm = s.selectOne("aprv.selectUserNm", reqUserId);
		String nextNm = null;
		for (Map<String, Object> r : stepRows(s, docId, cur + 1)) {
			nextNm = (String) r.get("stepNm");
			notis.add(noti(s, (String) r.get("aprvrUserId"), "APRV_REQ", "결재 요청: " + reqTpNm(reqTp) + " " + targetNm,
					reqUserNm + " 님의 요청 — " + (cur + 1) + "/" + stepCnt + " 단계" + (nextNm == null ? "" : " (" + nextNm + ")"), docId, now));
		}
		notis.add(noti(s, reqUserId, "APRV_STEP", "단계 승인: " + reqTpNm(reqTp) + " " + targetNm,
				stepLabel + " " + actorNm + " 승인 — 다음 " + (cur + 1) + "/" + stepCnt + " 단계", docId, now));
		return new Result(docId, false, true, stepLabel + " 승인 — 다음 " + (cur + 1) + "/" + stepCnt + " 단계로", notis);
	}

	/** 문서의 한 단계 결재자 행 */
	private static List<Map<String, Object>> stepRows(SqlSession s, String docId, int stepNo) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : s.<Map<String, Object>>selectList("aprv.selectDocSteps", docId)) {
			if (((Number) r.get("stepNo")).intValue() == stepNo) out.add(r);
		}
		return out;
	}

	private void updateDoc(SqlSession s, String docId, String docStat, int curStepNo, String endDt) {
		Map<String, Object> u = new HashMap<>();
		u.put("docId", docId);
		u.put("docStat", docStat);
		u.put("curStepNo", curStepNo);
		u.put("endDt", endDt);
		if (s.update("aprv.updateDocStat", u) == 0) throw new IllegalStateException("다른 사용자가 먼저 처리한 결재입니다.");
	}

	/** 대상이 다른 경로로 없어진 진행 중 문서를 닫는다 (CANCELED). 닫았으면 true */
	public boolean cancelIfGone(Map<String, Object> doc) {
		if (!"PROGRESS".equals(doc.get("docStat"))) return false;
		try (SqlSession s = sqlSessionFactory.openSession()) {
			Map<String, Object> key = new HashMap<>();
			key.put("reqTp", doc.get("reqTp"));
			key.put("targetId", doc.get("targetId"));
			int pending = s.selectOne("aprv.countTargetPending", key);
			if (pending > 0) return false;
			Map<String, Object> u = new HashMap<>();
			u.put("docId", doc.get("docId"));
			u.put("docStat", "CANCELED");
			u.put("curStepNo", doc.get("curStepNo"));
			u.put("endDt", now());
			s.update("aprv.updateDocStat", u);
			s.commit();
			doc.put("docStat", "CANCELED");
			return true;
		}
	}
}
