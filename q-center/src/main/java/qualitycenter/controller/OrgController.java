package qualitycenter.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;

import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP1 — 조직(관리 > 조직). 부서 트리, 부서장, 부서원. 결재선의 "요청자 부서장" 이 이 정보를 쓴다.
 * 바꾸는 API 와 부서원 목록은 사용자·권한 관리 권한(USER_ADMIN). 부서 목록은 로그인 사용자 누구나(사용자 화면·결재선 화면의 선택 목록).
 */
@RestController
@RequestMapping("/api/org")
public class OrgController {

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Autowired private SessionService sessionService;
	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SqlSessionFactory sqlSessionFactory;

	private Response denied() {
		if (sessionService.hasPerm(PermCode.USER_ADMIN)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (사용자·권한 관리 권한 필요)");
		return r;
	}

	private static Response fail(String msg) {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_500.getCode(), msg);
		return r;
	}

	private static Response ok() {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		return r;
	}

	/** 부서 목록 (부서장 이름·부서원 수 포함, 정렬 순서·이름 순). 트리는 화면이 P_ORG_ID 로 만든다 */
	@GetMapping("/list")
	public List<Map<String, Object>> list() {
		return sqlSessionTemplate.selectList("aprv.selectOrgList");
	}

	/** 부서원 — 109번 #16 사용자 목록(getUserList)과 같이 사용자·권한 관리 권한(USER_ADMIN)만 (부서원 ID·이름·이메일) */
	@GetMapping("/users")
	public List<Map<String, Object>> users(@RequestParam("orgId") String orgId) {
		if (!sessionService.hasPerm(PermCode.USER_ADMIN)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "부서원 목록을 볼 권한이 없습니다 (사용자·권한 관리 권한 필요).");
		}
		return sqlSessionTemplate.selectList("aprv.selectOrgUsers", orgId);
	}

	/** 부서 저장 — {orgId?, orgNm, pOrgId, headUserId, sortOrd}. 응답 contents = orgId */
	@PostMapping("/save")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		Response d = denied();
		if (d != null) return Mono.just(d);
		String orgId = (String) body.get("orgId");
		String orgNm = body.get("orgNm") == null ? "" : ((String) body.get("orgNm")).trim();
		String pOrgId = body.get("pOrgId") == null || ((String) body.get("pOrgId")).isEmpty() ? null : (String) body.get("pOrgId");
		String head = body.get("headUserId") == null || ((String) body.get("headUserId")).isEmpty() ? null : (String) body.get("headUserId");
		int sortOrd = body.get("sortOrd") == null || String.valueOf(body.get("sortOrd")).trim().isEmpty() ? 0
				: Integer.parseInt(String.valueOf(body.get("sortOrd")).trim());
		if (orgNm.isEmpty()) return Mono.just(fail("부서 이름은 필수입니다."));
		if (orgNm.length() > 100) return Mono.just(fail("부서 이름이 너무 깁니다 (최대 100자)."));
		if (pOrgId != null && (int) sqlSessionTemplate.selectOne("aprv.countOrg", pOrgId) == 0) return Mono.just(fail("없는 상위 부서입니다."));
		if (orgId != null && pOrgId != null) {
			List<String> self = sqlSessionTemplate.selectList("aprv.selectOrgDescendants", orgId);
			if (self.contains(pOrgId)) return Mono.just(fail("자기 자신이나 하위 부서를 상위 부서로 정할 수 없습니다."));
		}
		if (head != null && (int) sqlSessionTemplate.selectOne("aprv.countActiveUser", head) == 0) return Mono.just(fail("없는 사용자입니다: " + head));
		Map<String, Object> p = new HashMap<>();
		p.put("orgId", orgId);
		p.put("orgNm", orgNm);
		p.put("pOrgId", pOrgId);
		p.put("headUserId", head);
		p.put("sortOrd", sortOrd);
		p.put("now", LocalDateTime.now().format(DT_FMT));
		p.put("userId", sessionService.getUserId());
		if ((int) sqlSessionTemplate.selectOne("aprv.countOrgByNm", p) > 0) return Mono.just(fail("같은 상위 부서 아래에 같은 이름의 부서가 있습니다: " + orgNm));
		try (SqlSession s = sqlSessionFactory.openSession()) {
			if (orgId == null) {
				orgId = StringUtils.getUUID();
				p.put("orgId", orgId);
				s.insert("aprv.insertOrg", p);
			} else {
				if (s.update("aprv.updateOrg", p) == 0) return Mono.just(fail("없는 부서입니다."));
			}
			// 부서장은 그 부서 소속으로 둔다
			if (head != null) {
				Map<String, Object> u = new HashMap<>();
				u.put("userId", head);
				u.put("orgId", orgId);
				s.update("aprv.updateUserOrg", u);
			}
			s.commit();
		}
		Response r = ok();
		r.setContents(orgId);
		return Mono.just(r);
	}

	/** 부서 삭제 — {orgId}. 하위 부서가 있으면 거부, 부서원은 부서 없음으로 */
	@PostMapping("/delete")
	public Mono<Response> delete(@RequestBody Map<String, Object> body) {
		Response d = denied();
		if (d != null) return Mono.just(d);
		String orgId = (String) body.get("orgId");
		if ((int) sqlSessionTemplate.selectOne("aprv.countOrgChildren", orgId) > 0) return Mono.just(fail("하위 부서가 있어 삭제할 수 없습니다. 하위 부서를 먼저 옮기거나 삭제하세요."));
		try (SqlSession s = sqlSessionFactory.openSession()) {
			s.update("aprv.clearUsersOrg", orgId);
			s.delete("aprv.deleteOrg", orgId);
			s.commit();
		}
		return Mono.just(ok());
	}

	/** 사용자 부서 지정·해제 — {userId, orgId(비우면 해제)} */
	@PostMapping("/assignUser")
	public Mono<Response> assignUser(@RequestBody Map<String, Object> body) {
		Response d = denied();
		if (d != null) return Mono.just(d);
		String userId = (String) body.get("userId");
		String orgId = body.get("orgId") == null || ((String) body.get("orgId")).isEmpty() ? null : (String) body.get("orgId");
		if (userId == null || (int) sqlSessionTemplate.selectOne("aprv.countActiveUser", userId) == 0) return Mono.just(fail("없는 사용자입니다: " + userId));
		if (orgId != null && (int) sqlSessionTemplate.selectOne("aprv.countOrg", orgId) == 0) return Mono.just(fail("없는 부서입니다."));
		Map<String, Object> u = new HashMap<>();
		u.put("userId", userId);
		u.put("orgId", orgId);
		sqlSessionTemplate.update("aprv.updateUserOrg", u);
		return Mono.just(ok());
	}

}
