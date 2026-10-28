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
 * 자동 반려한다. 수집 결과(실제 DB 구조)가 그대로 모델이 되고, 반려된 변경은 모델에 남기지 않는다(권고 채택 10-28).
 *
 * <p>수집이 끝난 뒤(테이블·컬럼·인덱스·제약조건 MERGE 다음) 수집 완료 기록과 같은 트랜잭션에서 돈다. 수집이 실패하면 부르지 않으므로
 * 아무것도 반려되지 않고, 여기서 실패하면 수집도 실패로 닫힌다.</p>
 * <ol>
 *   <li>변경 이력 — REJECTED, 반려자 {@link AprvService#SYSTEM_USER_ID}·반려 일시·사유 {@link #REASON} (화면 반려와 같은 APRV_USER_ID·APRV_DT·APRV_COMMENT)</li>
 *   <li>결재 문서 — 반려한 신청에 진행 중 문서가 있으면 시스템 반려로 닫는다({@link AprvService#rejectBySystem}, 요청자 알림 포함)</li>
 *   <li>알림 — 문서로 알리지 않은 건(신청 전 DRAFT, 결재선 없는 신청)은 요청자마다 한 건(사용자 알림 테이블 → 헤더 종)</li>
 *   <li>모델 행 — 수집이 이번에 읽은 칸은 수집 값 그대로(화면 반려처럼 변경 전 값으로 되돌리지 않는다). 그 밖은 신청 전으로:
 *     수정 대기 — 수집이 덮지 않는 칸(컬럼 한글명, 테이블 한글명·설명·테이블스페이스·업무영역·주제영역)은 신청 전 값,
 *       수집에 없는 컬럼·테이블은 모든 칸(이름 포함)을 신청 전 값으로.
 *       이름·소유자 변경으로 생긴 실제 DB 에 없는 이름의 행은, 처음 이름이 이번에 수집됐으면 그 행으로 대신하고 지운다(수집이 덮지 않는 칸은 옮긴다);
 *     추가 대기 — 읽었으면 APPROVED, 안 읽었으면 컬럼·테이블 REJECTED / 제약조건·인덱스 삭제;
 *     삭제 대기 — 읽었으면 수집이 되살렸고, 이번에 읽은 스키마인데 없으면 물리 삭제(딸린 인덱스·제약조건·참조·사용자 정의 속성 값까지);
 *     반려된 FK 추가가 남긴 컬럼 FK 표시는 신청 전 값(수집은 FK 부모를 읽지 않아 덮지 않는다)</li>
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
		private final Set<String> owners = new HashSet<>();
		private final Set<String> objs = new HashSet<>();
		private final Set<String> attrs = new HashSet<>();
		private final Set<String> constraints = new HashSet<>();
		private final Set<String> indexes = new HashSet<>();
		private boolean indexesRead;
		private boolean constraintsRead;

		/** 수집한 스키마(소유자). 그 안에서 없는 행만 "DB 에 없다" 로 본다 */
		public void schema(String owner) { owners.add(owner == null ? "" : owner); }
		public void obj(String owner, String objNm) { owners.add(owner == null ? "" : owner); objs.add(key(owner, objNm)); }
		public void attr(String owner, String objNm, String attrNm) { attrs.add(key(owner, objNm, attrNm)); }
		public void constraint(String owner, String tableNm, String constraintNm) { constraints.add(key(owner, tableNm, constraintNm)); }
		public void index(String owner, String tableNm, String indexNm) { indexes.add(key(owner, tableNm, indexNm)); }
		/** 인덱스·제약조건 단계는 실패해도 수집이 이어진다 — 끝까지 읽었을 때만 "없다" 로 본다 */
		public void indexesRead() { indexesRead = true; }
		public void constraintsRead() { constraintsRead = true; }
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

	private static Map<String, Object> mkey(String dmId, String owner, String objNm, String attrNm) {
		Map<String, Object> k = new HashMap<>();
		k.put("dataModelId", dmId);
		k.put("objOwner", owner);
		k.put("objNm", objNm);
		if (attrNm != null) k.put("attrNm", attrNm);
		return k;
	}

	/** 수집 세션 s 에서 돌고 커밋하지 않는다 */
	public Outcome reject(SqlSession s, String dmId, Collected c) throws Exception {
		List<Map<String, Object>> pend = s.selectList("dmChangeHistory.selectPendingByDmForUpdate", dmId);
		if (pend.isEmpty()) return new Outcome(0, new ArrayList<>());
		String now = LocalDateTime.now().format(DT_FMT);

		revertAttrModifies(s, dmId, pend, c);
		revertObjModifies(s, dmId, pend, c);
		clearRejectedFkMarks(s, dmId, pend, c);
		purgeHiddenDeletes(s, dmId, pend, c);
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

	/** 같은 컬럼(테이블)에 이어진 대기 수정 — 처음 이름과 첫 대기 수정의 변경 전 값(BEFORE_JSON) */
	private static class Chain {
		String owner, obj, cur;          // 컬럼: 소유자·테이블·지금 이름 / 테이블: 지금 소유자·이름 (cur 없음)
		String origOwner, orig;          // 처음 (소유자,) 이름
		Map<String, Object> before;      // 첫 대기 수정의 변경 전 값
	}

	/**
	 * 컬럼 수정 대기(MODIFY_ATTR). 영문명을 여러 번 바꿨으면 처음 이름까지 따라간다. 수정이 바꾸는 칸 중 수집이 덮지 않는 것은 한글명이다.
	 * <ul>
	 *   <li>지금 이름이 수집됨 — 수집 값 그대로, 한글명만 신청 전 값</li>
	 *   <li>지금 이름은 없고 처음 이름이 수집됨(실제 DB 가 아직 처음 이름) — 바뀐 이름 행은 사본이다. 수집이 덮지 않는 칸을 처음 이름 행으로 옮기고
	 *       한글명은 신청 전 값, 그 이름을 가리키던 인덱스·제약조건·참조 제약조건·FK 부모를 처음 이름으로 돌리고 사본을 지운다</li>
	 *   <li>둘 다 수집에 없음 — 수집이 덮은 칸이 없으므로 화면 반려와 같이 모든 칸(이름 포함)을 신청 전 값으로. 처음 이름의 다른 행이 있으면 이름은 둔다</li>
	 * </ul>
	 */
	private void revertAttrModifies(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		Map<String, Chain> chains = new LinkedHashMap<>();   // 지금 키 → 사슬
		for (Map<String, Object> h : pend) {
			if (!"MODIFY_ATTR".equals(h.get("change_type")) || h.get("before_json") == null) continue;
			String owner = nz(h.get("obj_owner")), obj = (String) h.get("obj_nm"), cur = (String) h.get("attr_nm");
			Map<String, Object> before = readMap(h.get("before_json"));
			String bNm = before.get("attrNm") == null ? cur : String.valueOf(before.get("attrNm"));
			Chain ch = chains.remove(key(owner, obj, bNm));
			if (ch == null) {
				ch = new Chain();
				ch.owner = owner;
				ch.obj = obj;
				ch.orig = bNm;
				ch.before = before;
			}
			ch.cur = cur;
			chains.put(key(owner, obj, cur), ch);
		}
		for (Chain ch : chains.values()) {
			Map<String, Object> kr = mkey(dmId, ch.owner, ch.obj, ch.orig);
			kr.put("attrNmKr", ch.before.get("attrNmKr"));
			if (c.attrs.contains(key(ch.owner, ch.obj, ch.cur))) {
				kr.put("attrNm", ch.cur);
				s.update("datamodel.restoreAttrNmKr", kr);
				continue;
			}
			boolean renamed = !ch.orig.equals(ch.cur);
			if (renamed && c.attrs.contains(key(ch.owner, ch.obj, ch.orig))) {
				Map<String, Object> cp = new HashMap<>();
				cp.put("dataModelId", dmId);
				cp.put("fromOwner", ch.owner);
				cp.put("fromObjNm", ch.obj);
				cp.put("fromAttrNm", ch.cur);
				cp.put("toOwner", ch.owner);
				cp.put("toObjNm", ch.obj);
				cp.put("toAttrNm", ch.orig);
				s.update("datamodel.copyUncollectedAttrCols", cp);
				s.update("datamodel.restoreAttrNmKr", kr);
				renameAttrRefs(s, dmId, ch.owner, ch.obj, ch.cur, ch.orig);
				s.delete("datamodel.deleteDataModelAttr", mkey(dmId, ch.owner, ch.obj, ch.cur));
				log.info(">> 재수집 자동 반려 — 이름 변경 대기 컬럼 {}.{}.{} 은 DB 에 없어 처음 이름 {} 행으로 대신", ch.owner, ch.obj, ch.cur, ch.orig);
				continue;
			}
			String target = ch.cur;
			if (renamed && (int) s.selectOne("datamodel.countAttrByKey", mkey(dmId, ch.owner, ch.obj, ch.orig)) == 0) {
				renameAttrRefs(s, dmId, ch.owner, ch.obj, ch.cur, ch.orig);
				target = ch.orig;
			}
			Map<String, Object> b = new HashMap<>(ch.before);
			b.put("dataModelId", dmId);
			b.put("currObjOwner", ch.owner);
			b.put("currObjNm", ch.obj);
			b.put("currAttrNm", ch.cur);
			if (target.equals(ch.cur)) b.put("attrNm", ch.cur);
			s.update("datamodel.restoreAttrFromBefore", b);
			kr.put("attrNm", target);
			s.update("datamodel.restoreAttrNmKr", kr);
		}
	}

	/** 컬럼 영문명 변경 때(saveAttrs) 함께 바꾼 인덱스·제약조건·참조 제약조건·FK 부모의 이름을 되돌린다 */
	private void renameAttrRefs(SqlSession s, String dmId, String owner, String obj, String from, String to) {
		Map<String, Object> ren = new HashMap<>();
		ren.put("dataModelId", dmId);
		ren.put("objOwner", owner);
		ren.put("objNm", obj);
		ren.put("origAttrNm", from);
		ren.put("newAttrNm", to);
		s.update("datamodel.renameAttrInIndex", ren);
		s.update("datamodel.renameAttrInConstraint", ren);
		s.update("datamodel.renameAttrInConstraintRef", ren);
		s.update("datamodel.renameAttrInFkParent", ren);
	}

	/**
	 * 테이블 수정 대기(MODIFY_OBJ — 이름·소유자 변경, 함께 바꾼 한글명·설명·테이블스페이스·업무영역·주제영역). 컬럼과 같은 규칙:
	 * 지금 (소유자, 이름)이 수집됨 — 함께 바꾼 칸(수집이 덮지 않음)만 신청 전 값.
	 * 처음 (소유자, 이름)만 수집됨 — 바뀐 테이블은 사본이다. 수집이 덮지 않는 테이블·컬럼 칸과 사용자 정의 속성 값을 처음 이름 테이블로 옮기고
	 * 함께 바꾼 칸은 신청 전 값, 다른 테이블의 참조(참조 제약조건·FK 부모)를 처음 이름으로 돌리고 사본(컬럼·인덱스·제약조건)을 지운다.
	 * 둘 다 없음 — 화면 반려와 같이 소유자·이름을 되돌리고(컬럼·인덱스·제약조건·참조·FK 부모·속성 값까지) 함께 바꾼 칸도 신청 전 값.
	 * 이름 변경 때 FK 부모는 소유자를 바꾸지 않으므로 FK 부모는 이름만 돌린다. 함께 바꾼 칸의 신청 전 값은 그 칸이 이력에 남은 신청(10-28 뒤)만 있다.
	 */
	private void revertObjModifies(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		Map<String, Chain> chains = new LinkedHashMap<>();
		for (Map<String, Object> h : pend) {
			if (!"MODIFY_OBJ".equals(h.get("change_type")) || h.get("before_json") == null) continue;
			String curOwner = nz(h.get("obj_owner")), cur = (String) h.get("obj_nm");
			Map<String, Object> before = readMap(h.get("before_json"));
			String bOwner = before.get("objOwner") == null ? curOwner : String.valueOf(before.get("objOwner"));
			String bNm = before.get("objNm") == null ? cur : String.valueOf(before.get("objNm"));
			Chain ch = chains.remove(key(bOwner, bNm));
			if (ch == null) {
				ch = new Chain();
				ch.origOwner = bOwner;
				ch.orig = bNm;
				ch.before = before;
			}
			ch.owner = curOwner;
			ch.obj = cur;
			chains.put(key(curOwner, cur), ch);
		}
		for (Chain ch : chains.values()) {
			boolean moved = !ch.origOwner.equals(ch.owner) || !ch.orig.equals(ch.obj);
			if (c.objs.contains(key(ch.owner, ch.obj))) {
				restoreObjUncollected(s, dmId, ch.owner, ch.obj, ch.before);
				continue;
			}
			if (moved && c.objs.contains(key(ch.origOwner, ch.orig))) {
				Map<String, Object> cp = new HashMap<>();
				cp.put("dataModelId", dmId);
				cp.put("fromOwner", ch.owner);
				cp.put("fromObjNm", ch.obj);
				cp.put("toOwner", ch.origOwner);
				cp.put("toObjNm", ch.orig);
				s.update("datamodel.copyUncollectedObjCols", cp);
				s.update("datamodel.copyUncollectedAttrCols", cp);
				restoreObjUncollected(s, dmId, ch.origOwner, ch.orig, ch.before);
				renameObjVals(s, dmId, ch.owner, ch.obj, ch.origOwner, ch.orig);
				renameObjRefs(s, dmId, ch.owner, ch.obj, ch.origOwner, ch.orig);
				Map<String, Object> k = mkey(dmId, ch.owner, ch.obj, null);
				s.delete("datamodel.deleteIndexByObj", k);
				s.delete("datamodel.deleteConstraintByObj", k);
				s.delete("datamodel.deleteDataModelAttrsByObj", k);
				s.delete("datamodel.deleteDataModelObj", k);
				log.info(">> 재수집 자동 반려 — 이름·소유자 변경 대기 테이블 {}.{} 은 DB 에 없어 처음 {}.{} 테이블로 대신", ch.owner, ch.obj, ch.origOwner, ch.orig);
				continue;
			}
			String tOwner = ch.owner, tNm = ch.obj;
			if (moved && (int) s.selectOne("datamodel.countObjByKey", mkey(dmId, ch.origOwner, ch.orig, null)) == 0) {
				// 화면 반려(restoreFromBeforeJson)와 같은 순서 — 소유자 → 이름
				if (!ch.origOwner.equals(ch.owner)) {
					Map<String, Object> own = new HashMap<>();
					own.put("dataModelId", dmId);
					own.put("objNm", ch.obj);
					own.put("objOwner", ch.owner);
					own.put("newOwner", ch.origOwner);
					s.update("datamodel.cascadeAttrOwner", own);
					s.update("datamodel.cascadeIndexOwner", own);
					s.update("datamodel.cascadeConstraintOwner", own);
				}
				if (!ch.orig.equals(ch.obj)) {
					Map<String, Object> rn = new HashMap<>();
					rn.put("dataModelId", dmId);
					rn.put("origObjNm", ch.obj);
					rn.put("newObjNm", ch.orig);
					rn.put("objOwner", ch.origOwner);
					s.update("datamodel.renameObjAttrCascade", rn);
					s.update("datamodel.renameObjIndexCascade", rn);
					s.update("datamodel.renameObjConstraintCascade", rn);
				}
				renameObjRefs(s, dmId, ch.owner, ch.obj, ch.origOwner, ch.orig);
				renameObjVals(s, dmId, ch.owner, ch.obj, ch.origOwner, ch.orig);
				Map<String, Object> rv = new HashMap<>();
				rv.put("dataModelId", dmId);
				rv.put("objOwner", ch.origOwner);
				rv.put("objNm", ch.orig);
				rv.put("currObjOwner", ch.owner);
				rv.put("currObjNm", ch.obj);
				s.update("datamodel.restoreObjFromBefore", rv);
				tOwner = ch.origOwner;
				tNm = ch.orig;
			}
			restoreObjUncollected(s, dmId, tOwner, tNm, ch.before);
		}
	}

	/** 함께 바꾼 칸의 신청 전 값 — 그 칸이 BEFORE_JSON 에 있는 이력(10-28 뒤 신청)만 */
	private void restoreObjUncollected(SqlSession s, String dmId, String owner, String objNm, Map<String, Object> before) {
		if (!before.containsKey("objNmKr")) return;
		Map<String, Object> b = new HashMap<>(before);
		b.put("dataModelId", dmId);
		b.put("curObjOwner", owner);
		b.put("curObjNm", objNm);
		s.update("datamodel.restoreObjUncollected", b);
	}

	private void renameObjVals(SqlSession s, String dmId, String fromOwner, String fromNm, String toOwner, String toNm) {
		Map<String, Object> rv = new HashMap<>();
		rv.put("dmId", dmId);
		rv.put("origOwner", fromOwner);
		rv.put("origObjNm", fromNm);
		rv.put("newOwner", toOwner);
		rv.put("newObjNm", toNm);
		s.update("udp.renameObjVals", rv);
	}

	/** 다른 테이블의 참조 제약조건(REF_OWNER·REF_TABLE_NM)과 FK 부모(이름만)를 처음 (소유자, 이름)으로 */
	private void renameObjRefs(SqlSession s, String dmId, String fromOwner, String fromNm, String toOwner, String toNm) {
		if (!toOwner.equals(fromOwner)) {
			Map<String, Object> own = new HashMap<>();
			own.put("dataModelId", dmId);
			own.put("objOwner", fromOwner);
			own.put("objNm", fromNm);
			own.put("newOwner", toOwner);
			s.update("datamodel.cascadeConstraintRefOwner", own);
		}
		if (!toNm.equals(fromNm)) {
			Map<String, Object> rn = new HashMap<>();
			rn.put("dataModelId", dmId);
			rn.put("objOwner", toOwner);
			rn.put("origObjNm", fromNm);
			rn.put("newObjNm", toNm);
			s.update("datamodel.renameObjConstraintRefCascade", rn);
			s.update("datamodel.renameObjFkParentCascade", rn);
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
			Map<String, Object> before = readMap(h.get("before_json"));
			Map<String, Object> after = readMap(h.get("after_json"));
			Map<String, String> refOf = new HashMap<>();
			for (Map<String, Object> m : (List<Map<String, Object>>) after.get("mappings")) {
				refOf.put((String) m.get("ownAttrNm"), (String) m.get("refAttrNm"));
			}
			for (Map<String, Object> col : (List<Map<String, Object>>) before.get("columns")) {
				Map<String, Object> k = mkey(dmId, owner, obj, (String) col.get("attrNm"));
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

	/**
	 * 삭제 대기로 감춘(USE_YN='N') 행이 이번 수집에 없으면(수집이 읽은 스키마인데 없음 — 실제 DB 에서도 없어짐) 물리 삭제한다.
	 * 같은 이름을 다시 추가할 때 막히지 않게. 딸린 것은 승인 때(GOV-B07)와 같이 정리한다:
	 * 컬럼 — 그 컬럼이 든 인덱스·제약조건, 그 컬럼을 참조하는 FK 제약조건, 다른 컬럼의 FK 부모 / 테이블 — 그 테이블의 인덱스·제약조건,
	 * 그 테이블을 참조하는 FK 제약조건, 다른 컬럼의 FK 부모, 사용자 정의 속성 값, 컬럼 / PK·FK — 그 제약조건(PK 면 같은 이름 인덱스, PK 삭제에 딸려 감춘 FK).
	 * 인덱스·제약조건은 그 수집 단계를 끝까지 읽었을 때만 지운다. 이번에 읽지 않은 스키마의 행은 감춘 채 둔다.
	 */
	@SuppressWarnings("unchecked")
	private void purgeHiddenDeletes(SqlSession s, String dmId, List<Map<String, Object>> pend, Collected c) throws Exception {
		for (Map<String, Object> h : pend) {
			String type = (String) h.get("change_type");
			String owner = nz(h.get("obj_owner")), obj = (String) h.get("obj_nm"), attr = (String) h.get("attr_nm");
			if (!c.owners.contains(owner)) continue;
			if ("DEL_ATTR".equals(type)) {
				Map<String, Object> k = mkey(dmId, owner, obj, attr);
				if (c.attrs.contains(key(owner, obj, attr)) || (int) s.selectOne("datamodel.countHiddenAttr", k) == 0) continue;
				s.delete("datamodel.deleteConstraintByAttr", k);
				s.delete("datamodel.deleteIndexByAttr", k);
				s.delete("datamodel.deleteInboundFkByAttr", k);
				s.update("datamodel.clearFkParentRefByAttr", k);
				s.delete("datamodel.deleteDataModelAttr", k);
				log.info(">> 재수집 자동 반려 — 삭제 대기 컬럼 {}.{}.{} 은 DB 에도 없어 물리 삭제", owner, obj, attr);
			} else if ("DEL_OBJ".equals(type)) {
				Map<String, Object> k = mkey(dmId, owner, obj, null);
				if (c.objs.contains(key(owner, obj)) || (int) s.selectOne("datamodel.countHiddenObj", k) == 0) continue;
				s.delete("datamodel.deleteIndexByObj", k);
				s.delete("datamodel.deleteConstraintByObj", k);
				s.delete("datamodel.deleteInboundFkByObj", k);
				s.update("datamodel.clearFkParentRefByObj", k);
				s.delete("udp.deleteObjVals", k);
				s.delete("datamodel.deleteDataModelAttrsByObj", k);
				s.delete("datamodel.deleteDataModelObj", k);
				log.info(">> 재수집 자동 반려 — 삭제 대기 테이블 {}.{} 은 DB 에도 없어 물리 삭제", owner, obj);
			} else if (("DEL_PK".equals(type) || "DEL_FK".equals(type)) && c.constraintsRead) {
				String cons = (String) h.get("constraint_nm");
				List<Map<String, Object>> keys = new ArrayList<>();
				Map<String, Object> own = mkey(dmId, owner, obj, null);
				own.put("constraintNm", cons);
				keys.add(own);
				if (h.get("before_json") != null) {
					Object fks = readMap(h.get("before_json")).get("cascadedFks");
					if (fks != null) {
						for (Map<String, Object> fk : (List<Map<String, Object>>) fks) {
							Map<String, Object> fkKey = mkey(dmId, nz(fk.get("objOwner")), (String) fk.get("objNm"), null);
							fkKey.put("constraintNm", fk.get("constraintNm"));
							keys.add(fkKey);
						}
					}
				}
				for (Map<String, Object> k : keys) {
					if (c.constraints.contains(key((String) k.get("objOwner"), (String) k.get("objNm"), (String) k.get("constraintNm")))) continue;
					s.delete("datamodel.deleteHiddenConstraint", k);
				}
				if ("DEL_PK".equals(type) && c.indexesRead && !c.indexes.contains(key(owner, obj, cons))) {
					own.put("indexNm", cons);
					s.delete("datamodel.deleteHiddenIndex", own);
				}
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

	@SuppressWarnings("unchecked")
	private static Map<String, Object> readMap(Object json) throws Exception {
		return JSON.readValue((String) json, Map.class);
	}
}
