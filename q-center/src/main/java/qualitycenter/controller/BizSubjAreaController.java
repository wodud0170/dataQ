package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.quality.common.PermCode;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.governance.ChangeHistoryService;
import reactor.core.publisher.Mono;

/**
 * 88번 §15 — 업무영역 (BIZ_AREA, 논리) / 주제영역 (SUBJ_AREA, 물리) CRUD.
 * 영역 자체 생성/수정/삭제는 영역 관리 권한(관리자 또는 AREA_ADMIN)만.
 */
@Slf4j
@RestController
@RequestMapping("/api/area")
public class BizSubjAreaController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private ChangeHistoryService changeHistory;

	@PostMapping("/biz/list")
	public List<Map<String, Object>> bizList(@RequestBody(required = false) Map<String, Object> body) {
		return sqlSessionTemplate.selectList("area.selectBizAreas");
	}

	@PostMapping("/subj/list")
	public List<Map<String, Object>> subjList(@RequestBody(required = false) Map<String, Object> body) {
		return sqlSessionTemplate.selectList("area.selectSubjAreas");
	}

	@PostMapping("/biz/save")
	public Mono<Response> bizSave(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.AREA_ADMIN)) throw new IllegalStateException("권한이 없습니다 (영역 관리 권한 필요)");
			Map<String, Object> p = new HashMap<>();
			String id = (String) body.get("bizAreaId");
			if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
			p.put("bizAreaId", id);
			p.put("bizAreaNm", body.get("bizAreaNm"));
			p.put("bizAreaDesc", body.get("bizAreaDesc"));
			p.put("parentId", body.get("parentId"));
			p.put("sortOrder", body.get("sortOrder") == null ? 0 : body.get("sortOrder"));
			p.put("userId", changeHistory.safeUserId());
			sqlSessionTemplate.insert("area.insertBizArea", p);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"bizAreaId\":\"" + id + "\"}");
		} catch (Exception e) {
			log.error(">> bizSave failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	@PostMapping("/subj/save")
	public Mono<Response> subjSave(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.AREA_ADMIN)) throw new IllegalStateException("권한이 없습니다 (영역 관리 권한 필요)");
			Map<String, Object> p = new HashMap<>();
			String id = (String) body.get("subjAreaId");
			if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
			p.put("subjAreaId", id);
			p.put("subjAreaNm", body.get("subjAreaNm"));
			p.put("subjAreaDesc", body.get("subjAreaDesc"));
			String nm = body.get("subjAreaNm") == null ? "" : body.get("subjAreaNm").toString().trim();
			if (nm.isEmpty()) throw new IllegalArgumentException("주제영역명을 넣어 주세요.");
			String parent = body.get("parentId") == null ? "" : body.get("parentId").toString().trim();
			checkSubjTree(id, parent);   // 104번 WP8-38
			p.put("parentId", parent.isEmpty() ? null : parent);
			p.put("sortOrder", body.get("sortOrder") == null ? 0 : body.get("sortOrder"));
			p.put("userId", changeHistory.safeUserId());
			sqlSessionTemplate.insert("area.insertSubjArea", p);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"subjAreaId\":\"" + id + "\"}");
		} catch (Exception e) {
			log.error(">> subjSave failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	@PostMapping("/biz/delete")
	public Mono<Response> bizDelete(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.AREA_ADMIN)) throw new IllegalStateException("권한이 없습니다 (영역 관리 권한 필요)");
			String id = (String) body.get("bizAreaId");
			sqlSessionTemplate.update("area.deleteBizArea", id);
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	@PostMapping("/subj/delete")
	public Mono<Response> subjDelete(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.AREA_ADMIN)) throw new IllegalStateException("권한이 없습니다 (영역 관리 권한 필요)");
			String id = (String) body.get("subjAreaId");
			// 104번 WP8-38 — 하위 영역이나 연결된 테이블이 있으면 지우지 않는다
			Map<String, Object> a = subjMap().get(id);
			if (a != null && (((Number) a.get("childCnt")).intValue() > 0 || ((Number) a.get("tableCnt")).intValue() > 0)) {
				throw new IllegalStateException("주제영역 '" + a.get("subjAreaNm") + "' 에 하위 영역 " + a.get("childCnt") + "개·연결 테이블 " + a.get("tableCnt") + "개가 있어 지울 수 없습니다.");
			}
			sqlSessionTemplate.update("area.deleteSubjArea", id);
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	// ===== 104번 WP8-38 — 주제영역 트리·테이블 연결 =====

	private Map<String, Map<String, Object>> subjMap() {
		Map<String, Map<String, Object>> m = new java.util.LinkedHashMap<>();
		for (Map<String, Object> a : sqlSessionTemplate.<Map<String, Object>>selectList("area.selectSubjAreas")) m.put((String) a.get("subjAreaId"), a);
		return m;
	}

	/** 상위 영역은 있는 영역이어야 하고, 자기 자신·자기 하위 아래로 둘 수 없으며, 옮긴 뒤 3단계를 넘으면 안 된다 */
	private void checkSubjTree(String id, String parent) {
		if (parent == null || parent.isEmpty()) return;
		Map<String, Map<String, Object>> all = subjMap();
		Map<String, Object> p = all.get(parent);
		if (p == null) throw new IllegalArgumentException("상위 주제영역을 찾을 수 없습니다.");
		for (Map<String, Object> cur = p; cur != null; cur = cur.get("parentId") == null ? null : all.get(cur.get("parentId"))) {
			if (id.equals(cur.get("subjAreaId"))) throw new IllegalArgumentException("자기 자신이나 하위 영역을 상위 영역으로 둘 수 없습니다.");
		}
		int height = subjHeight(id, all);
		int level = ((Number) p.get("level")).intValue();
		if (level + height > 3) {
			throw new IllegalArgumentException("주제영역은 3단계까지입니다 (상위 '" + p.get("path") + "' 아래로 두면 " + (level + height) + "단계가 됩니다).");
		}
	}

	private static int subjHeight(String id, Map<String, Map<String, Object>> all) {
		int h = 1;
		for (Map<String, Object> a : all.values()) {
			if (id.equals(a.get("parentId"))) h = Math.max(h, 1 + subjHeight((String) a.get("subjAreaId"), all));
		}
		return h;
	}

	/** 주제영역에 연결된 테이블 — {subjAreaId} */
	@PostMapping("/subj/tables")
	public List<Map<String, Object>> subjTables(@RequestBody Map<String, Object> body) {
		return sqlSessionTemplate.selectList("area.selectSubjTables", body.get("subjAreaId"));
	}

	/**
	 * 테이블을 주제영역에 연결하거나 해제한다 — {subjAreaId(비우면 해제), tables: [{dmId, objOwner, objNm}]}.
	 * 영역 관리 권한(AREA_ADMIN). 테이블마다 변경 이력 MODIFY_OBJ 2단계(메타데이터라 승인 없이 반영).
	 */
	@PostMapping("/subj/link")
	public Mono<Response> subjLink(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.AREA_ADMIN)) throw new IllegalStateException("권한이 없습니다 (영역 관리 권한 필요)");
			String subj = body.get("subjAreaId") == null ? null : body.get("subjAreaId").toString().trim();
			if (subj != null && subj.isEmpty()) subj = null;
			if (subj != null && !subjMap().containsKey(subj)) throw new IllegalArgumentException("주제영역을 찾을 수 없습니다.");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");
			if (tables == null || tables.isEmpty()) throw new IllegalArgumentException("테이블을 골라 주세요.");
			int n = 0;
			for (Map<String, Object> t : tables) {
				Map<String, Object> p = new HashMap<>();
				p.put("dmId", t.get("dmId"));
				p.put("objOwner", t.get("objOwner") == null ? "" : t.get("objOwner"));
				p.put("objNm", t.get("objNm"));
				String before = sqlSessionTemplate.selectOne("area.selectObjSubj", p);
				p.put("subjAreaId", subj);
				if (sqlSessionTemplate.update("area.updateObjSubj", p) == 0) continue;
				n++;
				Map<String, Object> b = new HashMap<>();
				b.put("subjAreaId", before);
				Map<String, Object> a = new HashMap<>();
				a.put("subjAreaId", subj);
				changeHistory.record(null, (String) t.get("dmId"), "MODIFY_OBJ", "TIER2", (String) p.get("objOwner"), (String) t.get("objNm"), null, null, null,
						new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(b), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(a), null, "APPROVED");
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(String.valueOf(n));
		} catch (Exception e) {
			log.error(">> subjLink failed: {}", e.getMessage());
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
