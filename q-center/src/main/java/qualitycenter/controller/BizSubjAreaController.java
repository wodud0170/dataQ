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
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.governance.ChangeHistoryService;
import reactor.core.publisher.Mono;

/**
 * 88번 §15 — 업무영역 (BIZ_AREA, 논리) / 주제영역 (SUBJ_AREA, 물리) CRUD.
 * 영역 자체 생성/수정/삭제는 관리자 only.
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			Map<String, Object> p = new HashMap<>();
			String id = (String) body.get("subjAreaId");
			if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
			p.put("subjAreaId", id);
			p.put("subjAreaNm", body.get("subjAreaNm"));
			p.put("subjAreaDesc", body.get("subjAreaDesc"));
			p.put("parentId", body.get("parentId"));
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			String id = (String) body.get("subjAreaId");
			sqlSessionTemplate.update("area.deleteSubjArea", id);
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
