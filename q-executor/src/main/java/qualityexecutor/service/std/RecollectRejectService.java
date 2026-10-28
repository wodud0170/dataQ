package qualityexecutor.service.std;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.service.AprvService;

import lombok.extern.slf4j.Slf4j;

/**
 * 110 4.12-41-b·5.8-18 (사용자 결정 10-28) — 데이터 모델을 다시 수집하면 그 모델의 승인되지 않은 변경(DRAFT·SUBMITTED, 유형 전부)은
 * 자동 반려한다. 수집 결과(실제 DB 구조)가 그대로 모델이 되므로 화면 [반려] 의 원복(변경 전 값·대기 삭제 복구·이름 되돌림)은 하지 않는다.
 *
 * <p>수집이 끝난 뒤(테이블·컬럼·인덱스·제약조건 MERGE 다음) 수집 완료 기록과 같은 트랜잭션에서 돈다. 수집이 실패하면 부르지 않으므로
 * 아무것도 반려되지 않고, 여기서 실패하면 수집도 실패로 닫힌다.</p>
 * <ol>
 *   <li>변경 이력 — REJECTED, 반려자 {@link AprvService#SYSTEM_USER_ID}·반려 일시·사유 {@link #REASON} (화면 반려와 같은 APRV_USER_ID·APRV_DT·APRV_COMMENT)</li>
 *   <li>결재 문서 — 반려한 신청에 진행 중 문서가 있으면 시스템 반려로 닫는다({@link AprvService#rejectBySystem}, 요청자 알림 포함)</li>
 *   <li>알림 — 문서로 알리지 않은 건(신청 전 DRAFT, 결재선 없는 신청)은 요청자마다 한 건(사용자 알림 테이블 → 헤더 종)</li>
 *   <li>모델 행 — 수집이 읽은 것은 수집 값 그대로. 수집이 덮지 않아 남은 대기 흔적만 정리한다:
 *     아직 대기 상태인 행(추가 대기 — 읽었으면 APPROVED, 안 읽었으면 컬럼·테이블 REJECTED / 제약조건·인덱스 삭제),
 *     이름·소유자 변경 대기로 생긴 실제 DB 에 없는 이름의 행(처음 이름이 이번에 수집됐으면 그 행으로 대신하고 지운다),
 *     반려된 FK 추가가 남긴 컬럼 FK 표시(수집은 FK 부모를 읽지 않아 덮지 않는다)</li>
 * </ol>
 */
@Slf4j
@Service
public class RecollectRejectService {

	public static final String REASON = "재수집으로 실제 DB 구조로 바뀜";

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private AprvService aprvService;

	/** 이번 수집이 읽은 키 (수집 MERGE 에 넣은 소유자·이름 그대로) */
	public static class Collected {
		private final Set<String> objs = new HashSet<>();
		private final Set<String> attrs = new HashSet<>();
		private final Set<String> constraints = new HashSet<>();
		private final Set<String> indexes = new HashSet<>();

		public void obj(String owner, String objNm) { objs.add(key(owner, objNm)); }
		public void attr(String owner, String objNm, String attrNm) { attrs.add(key(owner, objNm, attrNm)); }
		public void constraint(String owner, String tableNm, String constraintNm) { constraints.add(key(owner, tableNm, constraintNm)); }
		public void index(String owner, String tableNm, String indexNm) { indexes.add(key(owner, tableNm, indexNm)); }
	}

	/** 자동 반려한 이력 수와 커밋 뒤 실시간으로 보낼 알림 */
	public static class Outcome {
		public final int rejected;
		public final List<AprvService.Noti> notis;

		Outcome(int rejected, List<AprvService.Noti> notis) {
			this.rejected = rejected;
			this.notis = notis;
		}
	}

	private static String key(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String p : parts) sb.append(p == null ? "" : p).append('|');
		return sb.toString();
	}

	private static String nz(Object v) {
		return v == null ? "" : String.valueOf(v);
	}

	/** 수집 세션 s 에서 돌고 커밋하지 않는다 */
	public Outcome reject(SqlSession s, String dmId, Collected c) throws Exception {
		List<Map<String, Object>> pend = s.selectList("dmChangeHistory.selectPendingByDmForUpdate", dmId);
		if (pend.isEmpty()) return new Outcome(0, new ArrayList<>());
		String now = LocalDateTime.now().format(DT_FMT);

		dropRenamedAttrs(s, dmId, pend, c);
		dropRenamedObjs(s, dmId, pend, c);
		clearRejectedFkMarks(s, dmId, pend, c);
		settlePendingRows(s, dmId, c, now);

		List<Object> seqs = new ArrayList<>();
		for (Map<String, Object> h : pend) seqs.add(h.get("change_seq"));
		Map<String, Object> p = new HashMap<>();
		p.put("changeSeqList", seqs);
		p.put("aprvUserId", AprvService.SYSTEM_USER_ID);
		p.put("aprvDt", now);
		p.put("aprvComment", REASON);
		int n = s.update("dmChangeHistory.rejectPendingBySeqs", p);

		List<AprvService.Noti> notis = new ArrayList<>();
		// 신청의 결재 문서 — 그 신청에 대기 건이 남지 않았으면(다른 모델 건이 섞인 예전 신청이 아니면) 시스템 반려로 닫는다
		Set<String> subs = new LinkedHashSet<>();
		for (Map<String, Object> h : pend) {
			if ("SUBMITTED".equals(h.get("aprv_status")) && h.get("submission_id") != null) subs.add((String) h.get("submission_id"));
		}
		Set<String> closed = new HashSet<>();
		for (String sub : subs) {
			Map<String, Object> k = new HashMap<>();
			k.put("reqTp", "DM_CHANGE");
			k.put("targetId", sub);
			int left = s.selectOne("aprv.countTargetPending", k);
			if (left > 0) continue;
			Map<String, Object> doc = s.selectOne("aprv.selectOpenDoc", k);
			if (doc == null) continue;
			AprvService.Result r = aprvService.rejectBySystem(s, (String) doc.get("docId"), REASON);
			if (r != null) {
				notis.addAll(r.notis);
				closed.add(sub);
			}
		}
		// 문서로 알리지 않은 건 — 요청자마다 한 건
		Map<String, Object> model = s.selectOne("datamodel.selectDataModelById", dmId);
		String dmNm = String.valueOf(model.get("dataModelNm"));
		Map<String, Integer> byUser = new LinkedHashMap<>();
		for (Map<String, Object> h : pend) {
			if (!closed.contains(h.get("submission_id"))) byUser.merge((String) h.get("change_user_id"), 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> e : byUser.entrySet()) {
			notis.add(aprvService.rejectNoti(s, e.getKey(), "DM_CHANGE", dmNm,
					AprvService.SYSTEM_USER_NM + " — " + REASON + " (" + e.getValue() + "건)"));
		}
		s.update("datamodel.syncObjAttrCntByDm", dmId);
		log.info(">> 재수집 자동 반려 dm={} 이력 {}건, 결재 문서 {}건, 알림 {}건", dmId, n, closed.size(), notis.size());
		return new Outcome(n, notis);
	}

	/**
	 * 컬럼 영문명 변경 대기(MODIFY_ATTR, 변경 전 이름 ≠ 지금 이름)로 생긴 행. 실제 DB 가 아직 처음 이름이면 수집이 처음 이름 행을 새로 넣었으므로
	 * 바뀐 이름 행은 실제 DB 에 없는 사본이다 — 그 이름을 가리키던 인덱스·제약조건·참조 제약조건·FK 부모를 처음 이름으로 돌리고 지운다.
	 * 같은 컬럼을 여러 번 바꿨으면 처음 이름까지 따라간다. 바뀐 이름이 수집됐거나(DB 에도 바뀜) 처음 이름이 수집되지 않았으면 그대로 둔다.
	 */
	private void dropRenamedAttrs(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		Map<String, String[]> origOf = new LinkedHashMap<>();   // 지금 키 → {소유자, 테이블, 지금 이름, 처음 이름}
		for (Map<String, Object> h : pend) {
			if (!"MODIFY_ATTR".equals(h.get("change_type")) || h.get("before_json") == null) continue;
			String owner = nz(h.get("obj_owner")), obj = (String) h.get("obj_nm"), cur = (String) h.get("attr_nm");
			Object before = JSON.readValue((String) h.get("before_json"), Map.class).get("attrNm");
			if (before == null || before.equals(cur)) continue;
			String[] prev = origOf.remove(key(owner, obj, (String) before));
			origOf.put(key(owner, obj, cur), new String[] { owner, obj, cur, prev == null ? (String) before : prev[3] });
		}
		for (String[] r : origOf.values()) {
			String owner = r[0], obj = r[1], cur = r[2], orig = r[3];
			if (c.attrs.contains(key(owner, obj, cur)) || !c.attrs.contains(key(owner, obj, orig))) continue;
			Map<String, Object> ren = new HashMap<>();
			ren.put("dataModelId", dmId);
			ren.put("objOwner", owner);
			ren.put("objNm", obj);
			ren.put("origAttrNm", cur);
			ren.put("newAttrNm", orig);
			s.update("datamodel.renameAttrInIndex", ren);
			s.update("datamodel.renameAttrInConstraint", ren);
			s.update("datamodel.renameAttrInConstraintRef", ren);
			s.update("datamodel.renameAttrInFkParent", ren);
			Map<String, Object> k = new HashMap<>();
			k.put("dataModelId", dmId);
			k.put("objOwner", owner);
			k.put("objNm", obj);
			k.put("attrNm", cur);
			s.delete("datamodel.deleteDataModelAttr", k);
			log.info(">> 재수집 자동 반려 — 이름 변경 대기 컬럼 {}.{}.{} 은 DB 에 없어 지움 (실제 이름 {})", owner, obj, cur, orig);
		}
	}

	/**
	 * 테이블 이름·소유자 변경 대기(MODIFY_OBJ)로 생긴 테이블. 컬럼과 같은 규칙 — 실제 DB 가 아직 처음 (소유자, 이름)이면 바뀐 테이블은
	 * 사본이다. 사용자 정의 속성 값과 다른 테이블의 참조(참조 제약조건·FK 부모)를 처음 (소유자, 이름)으로 돌리고 그 테이블(컬럼·인덱스·제약조건)을 지운다.
	 * 이름 변경 때(updateObj) FK 부모는 소유자를 바꾸지 않으므로 FK 부모는 이름만 돌린다.
	 */
	private void dropRenamedObjs(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		Map<String, String[]> origOf = new LinkedHashMap<>();   // 지금 키 → {지금 소유자, 지금 이름, 처음 소유자, 처음 이름}
		for (Map<String, Object> h : pend) {
			if (!"MODIFY_OBJ".equals(h.get("change_type")) || h.get("before_json") == null) continue;
			String curOwner = nz(h.get("obj_owner")), cur = (String) h.get("obj_nm");
			Map<?, ?> before = JSON.readValue((String) h.get("before_json"), Map.class);
			String bOwner = before.get("objOwner") == null ? curOwner : String.valueOf(before.get("objOwner"));
			String bNm = before.get("objNm") == null ? cur : String.valueOf(before.get("objNm"));
			if (bOwner.equals(curOwner) && bNm.equals(cur)) continue;
			String[] prev = origOf.remove(key(bOwner, bNm));
			origOf.put(key(curOwner, cur), new String[] { curOwner, cur, prev == null ? bOwner : prev[2], prev == null ? bNm : prev[3] });
		}
		for (String[] r : origOf.values()) {
			String curOwner = r[0], cur = r[1], owner = r[2], orig = r[3];
			if (c.objs.contains(key(curOwner, cur)) || !c.objs.contains(key(owner, orig))) continue;
			Map<String, Object> rv = new HashMap<>();
			rv.put("dmId", dmId);
			rv.put("origOwner", curOwner);
			rv.put("origObjNm", cur);
			rv.put("newOwner", owner);
			rv.put("newObjNm", orig);
			s.update("udp.renameObjVals", rv);
			if (!owner.equals(curOwner)) {
				Map<String, Object> own = new HashMap<>();
				own.put("dataModelId", dmId);
				own.put("objOwner", curOwner);
				own.put("objNm", cur);
				own.put("newOwner", owner);
				s.update("datamodel.cascadeConstraintRefOwner", own);
			}
			if (!orig.equals(cur)) {
				Map<String, Object> rn = new HashMap<>();
				rn.put("dataModelId", dmId);
				rn.put("objOwner", owner);
				rn.put("origObjNm", cur);
				rn.put("newObjNm", orig);
				s.update("datamodel.renameObjConstraintRefCascade", rn);
				s.update("datamodel.renameObjFkParentCascade", rn);
			}
			Map<String, Object> k = new HashMap<>();
			k.put("dataModelId", dmId);
			k.put("objOwner", curOwner);
			k.put("objNm", cur);
			s.delete("datamodel.deleteIndexByObj", k);
			s.delete("datamodel.deleteConstraintByObj", k);
			s.delete("datamodel.deleteDataModelAttrsByObj", k);
			s.delete("datamodel.deleteDataModelObj", k);
			log.info(">> 재수집 자동 반려 — 이름·소유자 변경 대기 테이블 {}.{} 은 DB 에 없어 지움 (실제 {}.{})", curOwner, cur, owner, orig);
		}
	}

	/**
	 * FK 추가 대기(ADD_FK)의 제약조건이 이번 수집에 없으면(실제 DB 에 없음) 그 FK 가 컬럼에 남긴 FK 표시·부모를 신청 전 값으로.
	 * 수집은 FK 부모를 읽지 않아(4.12-41-c) 이 표시를 덮지 않는다. 지금 부모가 그 FK 의 참조 컬럼일 때만 바꾼다.
	 */
	@SuppressWarnings("unchecked")
	private void clearRejectedFkMarks(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		for (Map<String, Object> h : pend) {
			if (!"ADD_FK".equals(h.get("change_type")) || h.get("before_json") == null || h.get("after_json") == null) continue;
			String owner = nz(h.get("obj_owner")), obj = (String) h.get("obj_nm");
			if (c.constraints.contains(key(owner, obj, (String) h.get("constraint_nm")))) continue;
			Map<String, Object> before = JSON.readValue((String) h.get("before_json"), Map.class);
			Map<String, Object> after = JSON.readValue((String) h.get("after_json"), Map.class);
			Map<String, String> refOf = new HashMap<>();
			for (Map<String, Object> m : (List<Map<String, Object>>) after.get("mappings")) {
				refOf.put((String) m.get("ownAttrNm"), (String) m.get("refAttrNm"));
			}
			for (Map<String, Object> col : (List<Map<String, Object>>) before.get("columns")) {
				Map<String, Object> k = new HashMap<>();
				k.put("dataModelId", dmId);
				k.put("objOwner", owner);
				k.put("objNm", obj);
				k.put("attrNm", col.get("attrNm"));
				StdDataModelAttrVo a = s.selectOne("datamodel.selectDataModelAttrOne", k);
				if (a == null || !Objects.equals(a.getFkParentObjNm(), after.get("refTableNm"))
						|| !nz(a.getFkParentObjOwner()).equals(nz(after.get("refOwner")))
						|| !Objects.equals(a.getFkParentAttrNm(), refOf.get(col.get("attrNm")))) continue;
				k.put("fkYn", col.get("fkYn"));
				k.put("fkParentObjNm", col.get("fkParentObjNm"));
				k.put("fkParentObjOwner", col.get("fkParentObjOwner"));
				k.put("fkParentAttrNm", col.get("fkParentAttrNm"));
				s.update("datamodel.updateAttrFk", k);
			}
		}
	}

	/** 수집이 덮지 않아 아직 대기(DRAFT·SUBMITTED) 상태인 모델 행 — 이번 수집이 읽었으면 APPROVED, 아니면 반려 */
	private void settlePendingRows(SqlSession s, String dmId, Collected c, String now) {
		for (Map<String, Object> r : s.<Map<String, Object>>selectList("datamodel.selectPendingConstraintsByDm", dmId)) {
			Map<String, Object> k = new HashMap<>(r);
			k.put("dataModelId", dmId);
			boolean read = c.constraints.contains(key((String) r.get("objOwner"), (String) r.get("objNm"), (String) r.get("constraintNm")));
			if (read) s.update("datamodel.approveCollectedPendingConstraint", k);
			else s.delete("datamodel.deletePendingConstraint", k);
		}
		for (Map<String, Object> r : s.<Map<String, Object>>selectList("datamodel.selectPendingIndexesByDm", dmId)) {
			Map<String, Object> k = new HashMap<>(r);
			k.put("dataModelId", dmId);
			boolean read = c.indexes.contains(key((String) r.get("objOwner"), (String) r.get("objNm"), (String) r.get("indexNm")));
			if (read) s.update("datamodel.approveCollectedPendingIndex", k);
			else s.delete("datamodel.deletePendingIndex", k);
		}
		for (Map<String, Object> r : s.<Map<String, Object>>selectList("datamodel.selectPendingObjsByDm", dmId)) {
			Map<String, Object> k = new HashMap<>(r);
			k.put("dataModelId", dmId);
			if (c.objs.contains(key((String) r.get("objOwner"), (String) r.get("objNm")))) {
				s.update("datamodel.approveCollectedPendingObj", k);
			} else {
				k.put("aprvUserId", AprvService.SYSTEM_USER_ID);
				k.put("aprvDt", now);
				k.put("aprvComment", REASON);
				s.update("datamodel.rejectPendingObj", k);
			}
		}
		Map<String, Object> rej = new HashMap<>();
		rej.put("dmId", dmId);
		rej.put("aprvUserId", AprvService.SYSTEM_USER_ID);
		rej.put("aprvDt", now);
		rej.put("aprvComment", REASON);
		s.update("datamodel.rejectPendingAttrsByDm", rej);
	}
}
