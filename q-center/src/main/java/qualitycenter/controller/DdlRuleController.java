package qualitycenter.controller;

import java.util.LinkedHashMap;
import java.util.Map;

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

import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ddl.DdlRuleService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP3 — DDL 규칙 (관리 > DDL 규칙): 데이터 타입 매핑과 PK 이름 규칙.
 *
 * <p>바꾸는 API 와 매핑 목록은 데이터 소스 관리 권한(관리자 또는 DS_ADMIN)이 필요하다.
 * PK 이름 규칙 조회는 컬럼 화면의 PK 이름 안내에 쓰므로 로그인 사용자 누구나 본다.</p>
 */
@RestController
@RequestMapping("/api/ddlRule")
public class DdlRuleController {

	@Autowired private DdlRuleService ddlRuleService;
	@Autowired private SessionService sessionService;

	private Response denied() {
		if (sessionService.hasPerm(PermCode.DS_ADMIN)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (데이터 소스 관리 권한 필요)");
		return r;
	}

	private Response ok(Object data) throws JsonProcessingException {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		r.setContents(new ObjectMapper().writeValueAsString(data));
		return r;
	}

	/** 규칙이 있는 DBMS 와 매핑 목록 — dbmsTp 가 비면 전부 */
	@GetMapping("/typeMap/list")
	public Mono<Response> typeMapList(@RequestParam(value = "dbmsTp", required = false) String dbmsTp) throws JsonProcessingException {
		Response res = denied();
		if (res != null) return Mono.just(res);
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("dbmsList", DdlRuleService.DBMS_LIST);
		m.put("items", ddlRuleService.listTypeMap(dbmsTp));
		return Mono.just(ok(m));
	}

	/** 매핑 저장 — {isNew, dataType, dbmsTp, physDataType, dfltDataLen} */
	@PostMapping("/typeMap/save")
	public Mono<Response> typeMapSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		try {
			Object len = body.get("dfltDataLen");
			Integer dflt = (len == null || String.valueOf(len).trim().isEmpty()) ? null : Integer.valueOf(String.valueOf(len).trim());
			ddlRuleService.saveTypeMap(Boolean.TRUE.equals(body.get("isNew")), (String) body.get("dataType"), (String) body.get("dbmsTp"),
					(String) body.get("physDataType"), dflt, sessionService.getUserId());
			res.setResultInfo(RestResult.CODE_200);
		} catch (NumberFormatException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), "기본 길이는 숫자여야 합니다.");
		} catch (IllegalArgumentException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 매핑 삭제 — {dataType, dbmsTp}. 지우면 그 타입은 이름(길이,소수점) 그대로 나간다 */
	@PostMapping("/typeMap/delete")
	public Mono<Response> typeMapDelete(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		ddlRuleService.deleteTypeMap((String) body.get("dataType"), (String) body.get("dbmsTp"));
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}

	/** PK 이름 규칙 (예: PK_{TABLE}) */
	@GetMapping("/pkTemplate")
	public Mono<Response> pkTemplate() {
		Response res = new Response();
		res.setResultInfo(RestResult.CODE_200);
		res.setContents(ddlRuleService.pkTemplate());
		return Mono.just(res);
	}

	/** PK 이름 규칙 저장 — {pkTemplate} */
	@PostMapping("/pkTemplate/save")
	public Mono<Response> pkTemplateSave(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		try {
			ddlRuleService.savePkTemplate((String) body.get("pkTemplate"), sessionService.getUserId());
			res.setResultInfo(RestResult.CODE_200);
		} catch (IllegalArgumentException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
