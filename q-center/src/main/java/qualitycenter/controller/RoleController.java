package qualitycenter.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.RoleService;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP2 — 역할·권한 관리 (관리 > 역할 및 권한).
 *
 * <p>모든 API 가 사용자·권한 관리 권한(관리자 또는 USER_ADMIN)을 요구한다. 로그인 사용자 자기 권한은
 * {@code GET /api/login/myPerms} 로 본다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/role")
public class RoleController {

	@Autowired private RoleService roleService;
	@Autowired private SessionService sessionService;

	private Response denied() {
		if (sessionService.hasPerm(PermCode.USER_ADMIN)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (사용자·권한 관리 권한 필요)");
		return r;
	}

	/** 200 + contents(JSON 문자열) */
	private Response ok(Object data) throws JsonProcessingException {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		r.setContents(new ObjectMapper().writeValueAsString(data));
		return r;
	}

	/** 권한 코드 목록 (코드·이름·설명·시스템 단위 여부) */
	@GetMapping("/perms")
	public Mono<Response> perms() throws JsonProcessingException {
		Response res = denied();
		if (res != null) return Mono.just(res);
		return Mono.just(ok(PermCode.catalog()));
	}

	/** 역할 목록 (권한 코드·지정된 사용자 수 포함) */
	@PostMapping("/list")
	public Mono<Response> list() throws JsonProcessingException {
		Response res = denied();
		if (res != null) return Mono.just(res);
		return Mono.just(ok(roleService.list()));
	}

	/** 역할 저장 — {roleId?, roleNm, roleDesc, perms: [코드]}. 응답 contents = roleId */
	@PostMapping("/save")
	@SuppressWarnings("unchecked")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		try {
			String roleId = roleService.save((String) body.get("roleId"), (String) body.get("roleNm"),
					(String) body.get("roleDesc"), (List<String>) body.get("perms"));
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(roleId);
		} catch (SecurityException e) {   // 109번 #12
			res.setResultInfo(403, e.getMessage());
		} catch (IllegalArgumentException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} catch (Exception e) {
			log.error(">> role save failed : {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), "역할 저장 중 오류가 발생했습니다.");
		}
		return Mono.just(res);
	}

	/** 역할 삭제 — {roleId}. 내장 역할은 거부 */
	@PostMapping("/delete")
	public Mono<Response> delete(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		try {
			roleService.delete((String) body.get("roleId"));
			res.setResultInfo(RestResult.CODE_200);
		} catch (IllegalArgumentException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 역할을 받은 사용자 — {roleId} */
	@PostMapping("/users")
	public Mono<Response> users(@RequestBody Map<String, Object> body) throws JsonProcessingException {
		Response res = denied();
		if (res != null) return Mono.just(res);
		return Mono.just(ok(roleService.roleUsers((String) body.get("roleId"))));
	}

	/** 사용자에게 역할 지정 — {userId, roleId, sysCd?}. sysCd 가 비면 모든 시스템 */
	@PostMapping("/assign")
	public Mono<Response> assign(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		try {
			roleService.assign((String) body.get("userId"), (String) body.get("roleId"), (String) body.get("sysCd"));
			res.setResultInfo(RestResult.CODE_200);
		} catch (SecurityException e) {   // 109번 #12
			res.setResultInfo(403, e.getMessage());
		} catch (IllegalArgumentException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 역할 지정 해제 — {userId, roleId, sysCd} */
	@PostMapping("/unassign")
	public Mono<Response> unassign(@RequestBody Map<String, Object> body) {
		Response res = denied();
		if (res != null) return Mono.just(res);
		res = new Response();
		roleService.unassign((String) body.get("userId"), (String) body.get("roleId"), (String) body.get("sysCd"));
		res.setResultInfo(RestResult.CODE_200);
		return Mono.just(res);
	}
}
