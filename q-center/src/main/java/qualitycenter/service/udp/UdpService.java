package qualitycenter.service.udp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 104번 WP8-31·75 — 사용자 정의 속성(테이블·컬럼 관리 항목) 값 검사·저장.
 *
 * <p>테이블 추가·수정·엑셀 업로드와 컬럼 배치 저장(saveAttrs)·컬럼 엑셀 업로드가 부른다. 그 대상(OBJ·ATTR)의 사용 중인 정의만 보고,
 * 필수 속성이 비면 저장을 막는다. 값은 유형대로 검사한다 — 숫자, 날짜(YYYY-MM-DD), 선택(선택값 중 하나), 글(4,000자까지).</p>
 */
@Service
public class UdpService {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;

	/** 그 대상(OBJ 테이블·ATTR 컬럼)의 사용 중인 정의 (순서대로) */
	public List<Map<String, Object>> activeDefs(String targetTp) {
		Map<String, Object> p = new HashMap<>();
		p.put("useOnly", "Y");
		p.put("targetTp", targetTp);
		return sqlSessionTemplate.selectList("udp.selectDefs", p);
	}

	/** 테이블 하나의 값 {udpId: val} */
	public Map<String, String> objVals(String dmId, String objOwner, String objNm) {
		Map<String, Object> p = key(dmId, objOwner, objNm);
		Map<String, String> out = new LinkedHashMap<>();
		for (Map<String, Object> r : sqlSessionTemplate.<Map<String, Object>>selectList("udp.selectObjVals", p)) out.put((String) r.get("udpId"), (String) r.get("val"));
		return out;
	}

	/** 테이블 하나의 컬럼 값 {attrNm: {udpId: val}} */
	public Map<String, Map<String, String>> attrValsByObj(String dmId, String objOwner, String objNm) {
		Map<String, Object> p = key(dmId, objOwner, objNm);
		Map<String, Map<String, String>> out = new HashMap<>();
		for (Map<String, Object> r : sqlSessionTemplate.<Map<String, Object>>selectList("udp.selectAttrValsByObj", p)) {
			out.computeIfAbsent((String) r.get("attrNm"), k -> new LinkedHashMap<>()).put((String) r.get("udpId"), (String) r.get("val"));
		}
		return out;
	}

	/** 테이블 정의로 검사 — 테이블 추가·수정·엑셀 업로드 */
	public Map<String, String> check(Map<String, String> existing, Map<String, Object> sent) {
		return check(activeDefs("OBJ"), existing, sent);
	}

	/**
	 * 값 검사. defs 는 그 대상의 사용 중인 정의, existing 은 지금 저장된 값(추가면 빈 맵), sent 는 요청 값(null 이면 보내지 않음 — 기존 값 그대로).
	 *
	 * @return 저장할 값 (사용 중 정의만, 다듬은 값)
	 * @throws IllegalArgumentException 필수가 비었거나 유형이 맞지 않을 때 (메시지는 화면에 그대로 보인다)
	 */
	public Map<String, String> check(List<Map<String, Object>> defs, Map<String, String> existing, Map<String, Object> sent) {
		Map<String, String> merged = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (Map<String, Object> d : defs) {
			String id = (String) d.get("udpId");
			String nm = (String) d.get("udpNm");
			String v = sent != null && sent.containsKey(id) ? (sent.get(id) == null ? "" : sent.get(id).toString().trim())
					: (existing.get(id) == null ? "" : existing.get(id));
			if (v.isEmpty()) {
				if ("Y".equals(d.get("requiredYn"))) missing.add(nm);
				merged.put(id, "");
				continue;
			}
			String tp = (String) d.get("dataTp");
			if ("NUMBER".equals(tp)) {
				try {
					new java.math.BigDecimal(v.replace(",", ""));
					v = v.replace(",", "");
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("'" + nm + "' 은 숫자여야 합니다 (" + v + ").");
				}
			} else if ("DATE".equals(tp)) {
				if (!v.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("'" + nm + "' 은 날짜(YYYY-MM-DD)여야 합니다 (" + v + ").");
			} else if ("SELECT".equals(tp)) {
				String sv = (String) d.get("selectVals");
				List<String> opts = sv == null ? new ArrayList<>() : Arrays.asList(sv.split("\n"));
				if (!opts.contains(v)) throw new IllegalArgumentException("'" + nm + "' 은 " + String.join("·", opts) + " 중 하나여야 합니다 (" + v + ").");
			} else if (v.length() > 4000) {
				throw new IllegalArgumentException("'" + nm + "' 은 4,000자까지입니다.");
			}
			merged.put(id, v);
		}
		if (!missing.isEmpty()) throw new IllegalArgumentException("필수 관리 항목을 넣어 주세요: " + String.join(", ", missing));
		return merged;
	}

	/** 테이블 값 저장 — check 결과. 빈 값은 지운다. session 이 null 이면 바로 반영 */
	public void save(SqlSession session, String dmId, String objOwner, String objNm, Map<String, String> vals, String userId) {
		saveAttr(session, dmId, objOwner, objNm, "", vals, userId);
	}

	/** 컬럼 값 저장 — check 결과. 테이블 save 와 같고 키에 컬럼 영문명(테이블은 '') */
	public void saveAttr(SqlSession session, String dmId, String objOwner, String objNm, String attrNm, Map<String, String> vals, String userId) {
		for (Map.Entry<String, String> e : vals.entrySet()) {
			Map<String, Object> p = key(dmId, objOwner, objNm);
			p.put("attrNm", attrNm);
			p.put("udpId", e.getKey());
			p.put("val", e.getValue());
			p.put("userId", userId);
			String sql = e.getValue() == null || e.getValue().isEmpty() ? "udp.deleteVal" : "udp.upsertVal";
			if (session != null) {
				if (sql.equals("udp.deleteVal")) session.delete(sql, p); else session.insert(sql, p);
			} else {
				if (sql.equals("udp.deleteVal")) sqlSessionTemplate.delete(sql, p); else sqlSessionTemplate.insert(sql, p);
			}
		}
	}

	private static Map<String, Object> key(String dmId, String objOwner, String objNm) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		p.put("objOwner", objOwner == null ? "" : objOwner);
		p.put("objNm", objNm);
		return p;
	}
}
