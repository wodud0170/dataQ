package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 79번 진단 대상 제외 관리 컨트롤러.
 *
 * 표준화/구조 변경/품질 진단 대상에서 OBJ/ATTR 단위로 제외하는 토글 API.
 * 매퍼 적용:
 *  - 표준/구조 진단: 즉시 적용 (DiagController + structdiag 매퍼에 TARGET_YN='Y' 필터)
 *  - 품질 진단: QUAL_DIAG_TARGET_YN 컬럼만 저장. 매퍼 통합은 67/70번 정식 구현 후
 */
@Slf4j
@RestController
@RequestMapping("/api/dm")
public class DiagTargetController {

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SessionService sessionService;

	/** 관리자 검증 (운영 안전성) */
	private void requireAdmin() {
		if (!sessionService.isAdmin()) {
			throw new RuntimeException("관리자만 진단 대상을 변경할 수 있습니다.");
		}
	}

	/** OBJ 단건 토글 — 86번 #11 OBJ_OWNER 명시 (같은 OBJ_NM 다른 OWNER 분리) */
	@PostMapping("/setObjDiagTarget")
	public Map<String, Object> setObjDiagTarget(@RequestBody Map<String, Object> body) {
		requireAdmin();
		String diagType = strOrThrow(body.get("diagType"), "diagType");
		String targetYn = strOrThrow(body.get("targetYn"), "targetYn");
		validate(diagType, targetYn);

		Map<String, Object> p = new HashMap<>();
		p.put("dmId",     body.get("dmId"));
		p.put("objOwner", nullable((String) body.get("objOwner")));
		p.put("objNm",    body.get("objNm"));
		p.put("diagType", diagType);
		p.put("targetYn", targetYn);
		p.put("reason",   "Y".equals(targetYn) ? null : nullable((String) body.get("reason")));
		p.put("userId",   sessionService.getUserId());

		int cnt = sqlSessionTemplate.update("diagTarget.updateObjTarget", p);
		Map<String, Object> res = new HashMap<>();
		res.put("success", cnt > 0);
		res.put("count", cnt);
		return res;
	}

	/** OBJ 일괄 토글 — 86번 #11 (objOwner, objNm) tuple 배열 받음 */
	@PostMapping("/setObjDiagTargetBatch")
	@SuppressWarnings("unchecked")
	public Map<String, Object> setObjDiagTargetBatch(@RequestBody Map<String, Object> body) {
		requireAdmin();
		String diagType = strOrThrow(body.get("diagType"), "diagType");
		String targetYn = strOrThrow(body.get("targetYn"), "targetYn");
		validate(diagType, targetYn);
		// 신규 형식: targets = [{objOwner, objNm}, ...]. 옛 형식 (objNms array) 호환도 지원.
		List<Map<String, Object>> targets = (List<Map<String, Object>>) body.get("targets");
		if (targets == null || targets.isEmpty()) {
			List<String> objNms = (List<String>) body.get("objNms");
			if (objNms == null || objNms.isEmpty()) throw new RuntimeException("targets/objNms 누락");
			String legacyOwner = nullable((String) body.get("objOwner"));
			targets = new ArrayList<>();
			for (String n : objNms) {
				Map<String, Object> t = new HashMap<>();
				t.put("objOwner", legacyOwner);
				t.put("objNm", n);
				targets.add(t);
			}
		}

		Map<String, Object> p = new HashMap<>();
		p.put("dmId",     body.get("dmId"));
		p.put("targets",  targets);
		p.put("diagType", diagType);
		p.put("targetYn", targetYn);
		p.put("reason",   "Y".equals(targetYn) ? null : nullable((String) body.get("reason")));
		p.put("userId",   sessionService.getUserId());

		int cnt = sqlSessionTemplate.update("diagTarget.updateObjTargetBatch", p);
		Map<String, Object> res = new HashMap<>();
		res.put("success", cnt > 0);
		res.put("count", cnt);
		return res;
	}

	/** ATTR 단건 토글 — 86번 #11 OBJ_OWNER */
	@PostMapping("/setAttrDiagTarget")
	public Map<String, Object> setAttrDiagTarget(@RequestBody Map<String, Object> body) {
		requireAdmin();
		String diagType = strOrThrow(body.get("diagType"), "diagType");
		String targetYn = strOrThrow(body.get("targetYn"), "targetYn");
		validate(diagType, targetYn);

		Map<String, Object> p = new HashMap<>();
		p.put("dmId",     body.get("dmId"));
		p.put("objOwner", nullable((String) body.get("objOwner")));
		p.put("objNm",    body.get("objNm"));
		p.put("attrNm",   body.get("attrNm"));
		p.put("diagType", diagType);
		p.put("targetYn", targetYn);
		p.put("reason",   "Y".equals(targetYn) ? null : nullable((String) body.get("reason")));
		p.put("userId",   sessionService.getUserId());

		int cnt = sqlSessionTemplate.update("diagTarget.updateAttrTarget", p);
		Map<String, Object> res = new HashMap<>();
		res.put("success", cnt > 0);
		res.put("count", cnt);
		return res;
	}

	/** ATTR 일괄 토글 */
	@PostMapping("/setAttrDiagTargetBatch")
	@SuppressWarnings("unchecked")
	public Map<String, Object> setAttrDiagTargetBatch(@RequestBody Map<String, Object> body) {
		requireAdmin();
		String diagType = strOrThrow(body.get("diagType"), "diagType");
		String targetYn = strOrThrow(body.get("targetYn"), "targetYn");
		validate(diagType, targetYn);
		List<String> attrNms = (List<String>) body.get("attrNms");
		if (attrNms == null || attrNms.isEmpty()) throw new RuntimeException("attrNms 누락");

		Map<String, Object> p = new HashMap<>();
		p.put("dmId",     body.get("dmId"));
		p.put("objOwner", nullable((String) body.get("objOwner")));
		p.put("objNm",    body.get("objNm"));
		p.put("attrNms",  attrNms);
		p.put("diagType", diagType);
		p.put("targetYn", targetYn);
		p.put("reason",   "Y".equals(targetYn) ? null : nullable((String) body.get("reason")));
		p.put("userId",   sessionService.getUserId());

		int cnt = sqlSessionTemplate.update("diagTarget.updateAttrTargetBatch", p);
		Map<String, Object> res = new HashMap<>();
		res.put("success", cnt > 0);
		res.put("count", cnt);
		return res;
	}

	/** OBJ 단건 상세 조회 (모달용) */
	@GetMapping("/objDiagTargetDetail")
	public Map<String, Object> objDiagTargetDetail(@RequestParam("dmId") String dmId,
	                                                @RequestParam(value = "objOwner", required = false) String objOwner,
	                                                @RequestParam("objNm") String objNm) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		p.put("objOwner", nullable(objOwner));
		p.put("objNm", objNm);
		Map<String, Object> result = sqlSessionTemplate.selectOne("diagTarget.selectObjDiagTargetDetail", p);
		return result == null ? new HashMap<>() : result;
	}

	/** 통계 */
	@GetMapping("/diagTargetStats")
	public Map<String, Object> diagTargetStats(@RequestParam("dmId") String dmId) {
		Map<String, Object> result = sqlSessionTemplate.selectOne("diagTarget.selectDiagTargetStats", dmId);
		return result == null ? new HashMap<>() : result;
	}

	// ---------------- helpers ----------------

	private static String strOrThrow(Object v, String name) {
		if (v == null) throw new RuntimeException(name + " 누락");
		String s = String.valueOf(v).trim();
		if (s.isEmpty()) throw new RuntimeException(name + " 빈값");
		return s;
	}

	private static String nullable(String v) {
		if (v == null) return null;
		String t = v.trim();
		return t.isEmpty() ? null : t;
	}

	private static void validate(String diagType, String targetYn) {
		if (!"STND".equals(diagType) && !"STRUCT".equals(diagType) && !"QUAL".equals(diagType)) {
			throw new RuntimeException("diagType 잘못됨: " + diagType);
		}
		if (!"Y".equals(targetYn) && !"N".equals(targetYn)) {
			throw new RuntimeException("targetYn 잘못됨: " + targetYn);
		}
	}
}
