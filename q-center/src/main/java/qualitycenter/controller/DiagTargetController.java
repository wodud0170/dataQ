package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
		if (!sessionService.hasPerm(com.ndata.quality.common.PermCode.DIAG_TARGET_ADMIN)) {
			throw new Denied("진단 대상을 변경할 권한이 없습니다 (진단 제외 관리 권한 필요).");
		}
	}

	/** 권한 없음 — 403 */
	private static class Denied extends RuntimeException {
		Denied(String msg) { super(msg); }
	}

	/**
	 * 110 5.8-32 — 이 컨트롤러가 던진 오류의 사유를 화면이 받도록 본문 {success:false, message} 로 돌려준다
	 * (권한 없음 403, 그 밖 400). 예전에는 Spring 기본 오류 응답이라 메시지가 빠지고 화면에 "Request failed…" 만 보였다.
	 */
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> onError(RuntimeException e) {
		log.warn(">> diagTarget 거부: {}", e.getMessage());
		Map<String, Object> body = new HashMap<>();
		body.put("success", false);
		body.put("message", e.getMessage());
		return ResponseEntity.status(e instanceof Denied ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST).body(body);
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

	/** 통계 — 110 5.8-30: 목록과 같이 승인됐거나 내가 요청한 테이블·컬럼만 센다 */
	@GetMapping("/diagTargetStats")
	public Map<String, Object> diagTargetStats(@RequestParam("dmId") String dmId) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		p.put("userId", sessionService.getUserId());
		Map<String, Object> result = sqlSessionTemplate.selectOne("diagTarget.selectDiagTargetStats", p);
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
