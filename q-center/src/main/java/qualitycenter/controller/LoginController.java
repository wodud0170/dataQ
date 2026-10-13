package qualitycenter.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.PermService;

import io.swagger.v3.oas.annotations.tags.Tag;
import qualitycenter.model.auth.AdminVo;
import qualitycenter.model.auth.UserVo;
import qualitycenter.service.auth.RoleService;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "인증/로그인", description = "인증/로그인 API")
@Slf4j
@RestController
@RequestMapping("/api/login")
public class LoginController {

  @Autowired
  private SessionService sessionService;

  @Autowired
  private PermService permService;

  @Autowired
  private RoleService roleService;

  @RequestMapping(value = "/getUser", method = RequestMethod.GET)
  public UserVo getUser() {
    return sessionService.getUser();
  }

  /**
   * 사용자 상세. 사용자·권한 관리 권한(USER_ADMIN)이 없으면 자기 정보만 본다.
   * 비밀번호 해시는 응답에 싣지 않는다 (AdminVo.password 는 요청에서만 받는다).
   */
  @RequestMapping(value = "/getUserDetail", method = RequestMethod.GET)
  public AdminVo getUserDetail(@RequestParam String userId) {
    if (!sessionService.hasPerm(PermCode.USER_ADMIN) && !userId.equals(sessionService.getUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 정보를 볼 권한이 없습니다 (사용자·권한 관리 권한 필요).");
    }
    return sessionService.getUserDetail(userId);
  }

  /**
   * 로그인한 사용자가 관리자인지 (사용자 속성 — 헤더·내 정보 표시용). 화면 기능 분기는 {@link #myPerms()} 로 한다.
   * 화면은 예전처럼 ?user= 를 붙여 부르지만 그 값은 보지 않는다.
   */
  @RequestMapping(value = "/isAdmin", method = RequestMethod.GET)
  public boolean isAdmin() {
    return sessionService.isAdmin();
  }

  /**
   * 104번 WP2 — 로그인 사용자의 권한. {admin, perms: {권한코드: [시스템코드|'*']}, roles: [{roleNm, sysCd, sysNm}]}.
   * 관리자는 모든 권한 코드에 '*'.
   */
  @RequestMapping(value = "/myPerms", method = RequestMethod.GET)
  public java.util.Map<String, Object> myPerms() {
    java.util.Map<String, Object> m = new java.util.HashMap<>();
    String userId = sessionService.getUserId();
    m.put("admin", sessionService.isAdmin());
    m.put("perms", permService.perms(userId));
    m.put("roles", roleService.userRoles(userId));
    return m;
  }

  /** 실제 로그인 세션이 있는지. stomp.broker.mode 와 무관하다 (dev 모드는 STOMP 연결에만 쓴다). */
  @RequestMapping(value = "/checkSession", method = RequestMethod.GET)
  public boolean checkSession() {
    return sessionService.checkSession();
  }

  @RequestMapping(value = "/existAdmin", method = RequestMethod.GET)
  public boolean existAdmin() {
    return sessionService.existAdmin();
  }

  /** 최초 설치용 — 내장 관리자가 아직 없을 때만 만든다. 로그인 없이 부를 수 있는 API 라 서버에서 막는다. */
  @RequestMapping(value = "/createAdmin", method = RequestMethod.GET)
  public Mono<Response> createAdmin(String password) {
    Response result = new Response();
		try {
			if (sessionService.existAdmin()) {
				result.setResultInfo(403, "관리자 계정이 이미 있습니다.");
				return Mono.just(result);
			}
			sessionService.createAdmin(password);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> create admin failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
  }

  /** 비밀번호 변경 — 로그인한 본인 것만 바꾼다(현재 비밀번호 확인). user 파라미터는 보지 않는다. */
  @RequestMapping(value = "/changePassword", method = RequestMethod.GET)
  public Response changePassword(String oldPassword, String newPassword) {
    return sessionService.changePassword(sessionService.getUserId(), oldPassword, newPassword);
  }

  @RequestMapping(value = "/createUser", method = RequestMethod.POST)
  public Mono<Response> createUser(@RequestBody AdminVo user) throws Exception {
    log.info(">> create user={}, name={}, admin={}", user.getId(), user.getName(), user.isAdmin());
    Response result = new Response();
		try {
      if (!sessionService.hasPerm(PermCode.USER_ADMIN)) {
        result.setResultInfo(403, "사용자를 추가할 권한이 없습니다 (사용자·권한 관리 권한 필요).");
        return Mono.just(result);
      }
      // 관리자 지정은 관리자만 — 사용자·권한 관리 권한만으로 관리자를 만들면 그 권한이 모든 권한이 된다
      if (user.isAdmin() && !sessionService.isAdmin()) {
        result.setResultInfo(403, "관리자 계정은 관리자만 만들 수 있습니다.");
        return Mono.just(result);
      }
      if (sessionService.existUser(user.getId())) {
        throw new Exception("이미 존재하는 사용자입니다(" + user.getId() + ")");
      }
			sessionService.createUser(user);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> create user failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
  }

  @RequestMapping(value = "/removeUser", method = RequestMethod.POST)
  public boolean removeUser(AdminVo user) throws Exception {
    log.info(">> remove user={}", user);
    if (!sessionService.hasPerm(PermCode.USER_ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용자를 삭제할 권한이 없습니다 (사용자·권한 관리 권한 필요).");
    }
    AdminVo target = user.getId() == null ? null : sessionService.getUserDetail(user.getId());
    if (target != null && target.isAdmin() && !sessionService.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 계정은 관리자만 삭제할 수 있습니다.");
    }
    return sessionService.removeUser(user);
  }

  /**
   * 사용자 수정.
   * <ul>
   *   <li>관리자 — 모든 사용자의 이름·이메일·전화·관리자 여부·비밀번호(재설정)</li>
   *   <li>사용자·권한 관리 권한(USER_ADMIN, 관리자 아님) — 관리자가 아닌 사용자의 이름·이메일·전화·비밀번호(재설정).
   *       관리자 여부는 바꾸지 못하고 관리자 계정은 고치지 못한다</li>
   *   <li>그 밖 — 자기 이름·이메일·전화만. 관리자 여부는 지금 값 그대로여야 하고,
   *       비밀번호는 현재 비밀번호를 확인하는 비밀번호 변경(changePassword)으로만 바꾼다.</li>
   * </ul>
   */
  @RequestMapping(value = "/updateUser", method = RequestMethod.POST)
  public Mono<Response> updateUser(@RequestBody AdminVo user) throws Exception {
    log.info(">> update user={}, name={}, admin={}", user.getId(), user.getName(), user.isAdmin());
    Response result = new Response();
		try {
			AdminVo current = user.getId() == null ? null : sessionService.getUserDetail(user.getId());
			if (current == null) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "존재하지 않는 사용자입니다(" + user.getId() + ")");
				return Mono.just(result);
			}
			if (!sessionService.isAdmin()) {
				String reason = null;
				if (user.isAdmin() != current.isAdmin()) {
					reason = "관리자 여부는 관리자만 변경할 수 있습니다.";
				} else if (sessionService.hasPerm(PermCode.USER_ADMIN)) {
					if (current.isAdmin() && !user.getId().equals(sessionService.getUserId())) {
						reason = "관리자 계정은 관리자만 변경할 수 있습니다.";
					}
				} else if (!user.getId().equals(sessionService.getUserId())) {
					reason = "다른 사용자의 정보를 변경할 권한이 없습니다 (사용자·권한 관리 권한 필요).";
				} else if (StringUtils.isNotEmpty(user.getPassword())) {
					reason = "비밀번호는 [비밀번호 변경]에서 현재 비밀번호를 확인한 뒤 바꿀 수 있습니다.";
				}
				if (reason != null) {
					log.warn(">> update user rejected : requester={}, target={}, reason={}", sessionService.getUserId(), user.getId(), reason);
					result.setResultInfo(403, reason);
					return Mono.just(result);
				}
			}
			sessionService.updateUser(user);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> update user failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
  }

  /** 사용자 목록 — 관리 > 사용자·역할 화면 용 (사용자·권한 관리 권한). */
  @RequestMapping(value = "/getUserList", method = RequestMethod.GET)
  public List<AdminVo> getUserList() throws Exception {
    if (!sessionService.hasPerm(PermCode.USER_ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용자 목록을 볼 권한이 없습니다 (사용자·권한 관리 권한 필요).");
    }
    return sessionService.getUserList();
  }
}
