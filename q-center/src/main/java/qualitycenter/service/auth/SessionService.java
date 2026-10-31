package qualitycenter.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import qualitycenter.model.auth.AdminVo;
import qualitycenter.model.auth.UserVo;

import com.ndata.bean.SecurityManager;
import com.ndata.common.NDConstant;
import com.ndata.common.NDSeverity;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.DateUtils;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.PermService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class SessionService {

	@Autowired
	SecurityManager securityManager;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	@Autowired
	private PermService permService;

	@Autowired
	private SessionRegistry sessionRegistry;


	/**
	 * 세션 확인
	 *
	 * @return
	 */
	public boolean checkSession() {
		boolean result = false;

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) {
			result = true;
		}

		return result;
	}
	
    /**
     * 어드민 존재 여부 조회
     */
    public boolean existAdmin() {
        return sqlSessionTemplate.selectOne("login.getUser", NDConstant.USER_ADMIN) != null;
    }

    /**
     * 사용자 존재 여부 조회
     */
    public boolean existUser(String id) {
        return sqlSessionTemplate.selectOne("login.getUser", id) != null;
    }

    /**
     * 어드민 생성.
     * 내장 관리자 행이 삭제 표시(DEL_YN)로 남아 있으면 INSERT 가 기본키 중복이라 그 행을 되살린다 (110번 1.10-B1 안내 문구대로 만들 수 있게).
     */
    public boolean createAdmin(String password) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);

        AdminVo admin = new AdminVo();
        admin.setId(NDConstant.USER_ADMIN);
        admin.setPassword(securityManager.createHash(securityManager.encryptSHA256(securityManager.decodeBase64(password))));
        admin.setName(NDConstant.USER_ADMIN);
        //admin.setEmail("admin@naraedata.com");
        admin.setAdmin(true);
        admin.setCreateDate(time);
        admin.setUpdateDate(time);

        if ((int) sqlSessionTemplate.selectOne("login.selectDeletedUserCount", NDConstant.USER_ADMIN) > 0) {
            int result = sqlSessionTemplate.update("login.restoreUser", admin);
            permService.invalidate();
            return result == 1;
        }

        int result = sqlSessionTemplate.insert("login.createAdmin", admin);

        return result == 1;
    }

    /**
     * 유저 상세 조회
     */
    public AdminVo getUserDetail(String userId) {
        return sqlSessionTemplate.selectOne("login.getUser", userId);
    }

    /**
     * 유저 목록 조회
     */
    public List<AdminVo> getUserList() {

        List<AdminVo> admins = sqlSessionTemplate.selectList("login.getUserList");
        
        return admins;
    }
    
    /**
     * 유저 Role이 Admin인지 여부
     */
    public boolean isAdmin(String user) {

        AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", user);
        if (admin != null && admin.isAdmin()) {
        	return true;
        } else {
        	return false;
        }
    }

    /**
     * 로그인 사용자가 관리자인지. 권한 판정(hasPerm)과 같은 기준 — TB_USER.ADM_YN(PermService, 10초 캐시)으로 본다.
     * 로그인 때 세션에 담은 값을 쓰면 관리자 지정·해제가 다시 로그인할 때까지 반영되지 않는다 (110번 11.7-14).
     */
    public boolean isAdmin() {
		return permService.isAdmin(getUserId());
    }

    /**
     * 그 사용자의 로그인 세션을 모두 만료시킨다 (사용자 삭제 — 110번 11.7-14).
     * 만료된 세션의 다음 요청은 로그인이 없는 요청과 같이 처리된다 (WebSecurityConfiguration 세션 관리).
     */
    public void expireSessions(String userId) {
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (userId.equals(((UserVo) principal).getId())) {
				for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
					session.expireNow();
				}
			}
		}
    }

    /**
     * 104번 WP2 — 로그인 사용자가 그 권한을 갖는지. 관리자는 모든 권한을 갖는다.
     * 시스템 단위 권한은 어느 시스템이든 하나라도 받았으면 true — 특정 시스템은 {@link #hasPerm(PermCode, String)}.
     */
    public boolean hasPerm(PermCode code) {
		return permService.has(getUserId(), code);
    }

    /** 104번 WP2 — 그 시스템에 대한 권한. sysCd 가 비면 모든 시스템으로 받은 경우만 통한다. */
    public boolean hasPerm(PermCode code, String sysCd) {
		return permService.has(getUserId(), code, sysCd);
    }
	
    /**
     * 유저 생성.
     * 삭제(DEL_YN) 된 같은 ID 가 있으면 그 행을 새 사용자로 되살린다 — 예전 역할·부서·부서장 지정은 비운다 (110번 11.7-11a).
     */
    public boolean createUser(AdminVo admin) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);

        admin.setPassword(securityManager.createHash(securityManager.encryptSHA256(securityManager.decodeBase64(admin.getPassword()))));
        admin.setCreateDate(time);
        admin.setUpdateDate(time);

        if ((int) sqlSessionTemplate.selectOne("login.selectDeletedUserCount", admin.getId()) > 0) {
            admin.setUpdateUserId(getUserId());
            int result;
            try (SqlSession session = sqlSessionFactory.openSession(false)) {
                result = session.update("login.restoreUser", admin);
                session.delete("role.deleteUserRolesByUser", admin.getId());
                session.update("login.clearOrgHead", admin);
                session.commit();
            }
            permService.invalidate();
            return result == 1;
        }

        int result = sqlSessionTemplate.insert("login.createAdmin", admin);

        return result == 1;
    }

    /**
     * 유저 삭제
     */
    public boolean removeUser(AdminVo admin) {

        int result = sqlSessionTemplate.update("login.deleteAdmin", admin);

        return result == 1;
    }
    
    /**
     * 유저 변경
     */
    public boolean updateUser(AdminVo admin) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);

        if (StringUtils.isNotEmpty(admin.getPassword())) {
        	admin.setPassword(securityManager.createHash(securityManager.encryptSHA256(securityManager.decodeBase64(admin.getPassword()))));
        }
        admin.setUpdateDate(time);

        int result = sqlSessionTemplate.update("login.updateAdmin", admin);

        return result == 1;
    }

	/**
	 * 로그인 실패 카운트 증가
	 */
	public void increaseLoginFailCount(String user) {
		int result = sqlSessionTemplate.update("login.increaseLoginFailCount", user);
		if (result == 0) 
			return;
		
		if ((int) sqlSessionTemplate.selectOne("login.getLoginFailCount", user) >= 5)
			setBlockTime(user);
	}

	/**
	 * 로그인 실패 카운트,제한시간 초기화
	 */
	public void initializeLogin(String user) {
		sqlSessionTemplate.update("login.initializeLogin", user);
	}

	/**
	 * 로그인 금지 시작 시간
	 */
	public void setBlockTime(String user) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		String blockTime = LocalDateTime.now().plusMinutes(10).format(formatter);

		AdminVo admin = new AdminVo();
		admin.setId(user);
		admin.setBlockTime(blockTime);

		sqlSessionTemplate.update("login.setBlockTime", admin);
	}

	/**
	 * 비밀번호 변경 (110번 11.7-3b).
	 * ① 로그인 차단 시간이 지나지 않았으면 거부(로그인과 같은 문구) ② 현재 비밀번호 확인 — 틀리면 로그인 실패와 같이 실패 횟수를 올린다
	 * (5회면 10분 차단), 맞으면 로그인 성공과 같이 실패 횟수를 0 으로 ③ 새 비밀번호가 현재와 같으면 거부 ④ 저장.
	 * 새 비밀번호가 현재와 같은지는 현재 비밀번호를 맞힌 뒤에만 알려준다.
	 */
	public Response changePassword(String user, String oldPassword, String newPassword) {
		Response response = new Response();

		String blockTime = sqlSessionTemplate.selectOne("login.getBlockTime", user);
		if (blockTime != null && !DateUtils.compareTime(blockTime)) {
			response.setResultInfo(RestResult.CODE_406.getCode(),
					"로그인 차단 시간[" + DateUtils.changeTimeFormat(blockTime) + "]이 지나지 않았습니다.");
			return response;
		}

		// 세션 중에 삭제된 사용자
		AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", user);
		if (admin == null) {
			response.setResultInfo(RestResult.CODE_404.getCode(), "존재하지 않는 사용자입니다.");
			return response;
		}

		if (!securityManager.validatePassword(securityManager.encryptSHA256(securityManager.decodeBase64(oldPassword)),
				admin.getPassword())) {
			increaseLoginFailCount(user);
			response.setResultInfo(RestResult.CODE_406.getCode(), "기존 비밀번호가 틀렸습니다.");
			return response;
		}
		initializeLogin(user);

		if (securityManager.validatePassword(securityManager.encryptSHA256(securityManager.decodeBase64(newPassword)),
				admin.getPassword())) {
			response.setResultInfo(RestResult.CODE_406.getCode(), "기존 비밀번호와 다른 비밀번호를 입력해야 합니다.");
			return response;
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		String time = LocalDateTime.now().format(formatter);

		admin.setId(user);
		admin.setPassword(
				securityManager.createHash(securityManager.encryptSHA256(securityManager.decodeBase64(newPassword))));
		admin.setUpdateDate(time);

		if (sqlSessionTemplate.update("login.changePassword", admin) > 0) {
			response.setResultInfo(RestResult.CODE_200);
		} else {
			response.setResultInfo(RestResult.CODE_520.getCode(), "비밀번호 변경에 실패했습니다.");
		}

		return response;
	}

	/**
	 * 로그인한 사용자 정보 조회
	 */
	public UserVo getUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof AnonymousAuthenticationToken)) {
			return (UserVo) auth.getPrincipal();
		} else {
			return null;
		}
	}
	
	public String getUserId() {
		UserVo userVo = getUser();
		if (userVo != null) {
			return userVo.getId();
		} else {
			return null;
		}
	}

	/**
	 * 로그인한 사용자의 권한 조회
	 */
	public List<GrantedAuthority> getAuthorities() {
		UserVo user = (UserVo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return new ArrayList<GrantedAuthority>(user.getAuthorities());
	}

	/**
	 * 로그인 수행
	 *
	 * @param providerId
	 * @param user
	 * @return
	 */
	public UserVo login(UserVo user) {

		String blockTime = sqlSessionTemplate.selectOne("login.getBlockTime", user.getId());

		if (blockTime != null) {
			if (!DateUtils.compareTime(blockTime)) {
				throw new AuthenticationServiceException(
						"로그인 차단 시간[" + DateUtils.changeTimeFormat(blockTime) + "]이 지나지 않았습니다.");
			}
		}

		// 없는 아이디와 틀린 비밀번호는 같은 문구로 알린다 (어느 아이디가 있는지 드러내지 않는다)
		AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", user.getId());
		if (admin == null) {
			throw new AuthenticationServiceException("아이디 또는 비밀번호가 올바르지 않습니다." + "||" + user.getId());
		}

		if (securityManager.validatePassword(securityManager.encryptSHA256(user.getPassword()), admin.getPassword())) {
			// user role
			user.setAdmin(admin.isAdmin());
			user.setName(admin.getName());
			user.setEmail(admin.getEmail());
			user.setPhone(admin.getPhone());
			// 로그인 실패 횟수 초기화
			initializeLogin(user.getId());
		} else {
			throw new AuthenticationServiceException("아이디 또는 비밀번호가 올바르지 않습니다." + "||" + user.getId());
		}

		return user;
	}
	
	/**
	 * 로그인 감사로그 저장
	 *
	 * @param severity
	 * @param user
	 * @param msg
	 * @return
	 */
	public void putAuditLog(NDSeverity severity, String user, String msg) {
    	HashMap<String,Object> eventLog = new HashMap<>();
    	eventLog.put("hostName", StringUtils.getHostName());
    	eventLog.put("jobId", "authentication");
    	eventLog.put("jobExSvc", NDConstant.SVC_MANAGER);
    	eventLog.put("severity", severity);
    	eventLog.put("msg", msg);
    	eventLog.put("jobExUserId", user);
    	
    	log.info("EVENT_LOG : {}", eventLog);
    			
    	sqlSessionTemplate.insert("login.putEventLog", eventLog);
	}
}
