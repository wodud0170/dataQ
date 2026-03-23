package qualitycenter.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import qualitycenter.model.auth.AdminVo;
import qualitycenter.model.auth.UserVo;

import com.ndata.bean.AuthorityManager;
import com.ndata.bean.SecurityManager;
import com.ndata.common.NDConstant;
import com.ndata.common.NDSeverity;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.model.admin.RoleAuthorityVo;
import com.ndata.model.admin.RoleVo;
import com.ndata.model.admin.UserAsgnRoleVo;
import com.ndata.module.DateUtils;
import com.ndata.module.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class SessionService {
	
	@Resource(name = "jasyptStringEncryptor")
	private StringEncryptor stringEncryptor;

	@Autowired
	SecurityManager securityManager;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;


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
     * 어드민 생성
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

        int result = sqlSessionTemplate.insert("login.createAdmin", admin);

        return result == 1;
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

    public boolean isAdmin() {
		UserVo userVo = getUser();
		if (userVo != null) {
			return userVo.isAdmin();
		} else {
			return false;
		}
    }
	
    /**
     * 유저 생성
     */
    public boolean createUser(AdminVo admin) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);

        admin.setPassword(securityManager.createHash(securityManager.encryptSHA256(securityManager.decodeBase64(admin.getPassword()))));
        admin.setCreateDate(time);
        admin.setUpdateDate(time);

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
     * 역할 목록 조회
     */
    public List<RoleVo> getRoleList() {

    	List<RoleVo> roleVos = sqlSessionTemplate.selectList("authority.getRoleList");
    	
    	if (roleVos != null) {
    		for(RoleVo role : roleVos) {
    			role.setAuthorities(sqlSessionTemplate.selectList("authority.getRoleAuthority", role.getRoleNm()));
    		}
    	}
    	
    	return roleVos;
    }
    
    /**
     * 역할(Role) 생성
     */
    public boolean createRole(RoleVo role) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);

        role.setCreateDate(time);
        role.setUpdateDate(time);
        if (getUserId() != null) {
        	role.setCreateUserId(getUserId());
        }

        int result = sqlSessionTemplate.insert("authority.insertRole", role);

        // role 추가가 성공하고 role 접근권한이 NULL이 아닌 경우
        if (result == 1 && role.getAuthorities() != null) {
        	updateRoleAuthority(role, time);
        }
        
        return result == 1;
    }
    
    /**
     * 역할(Role)명 수정
     */
    public boolean updateRoleName(RoleVo role) {
        int result = sqlSessionTemplate.update("authority.updateRoleName", role);
        return result == 1;
    }

    /**
     * 역할(Role)삭제
     */
    public boolean deleteRole(RoleVo role) {
        int result = sqlSessionTemplate.delete("authority.deleteRole", role);
        return result == 1;
    }
    
    /**
     * 역할(Role) 수정
     */
    public void updateRoleAuthority(RoleVo role, String updateTime) {

    	// 기존 권한 삭제
        sqlSessionTemplate.delete("authority.deleteRoleAuthority", role.getRoleNm());
        
        // 신규 권한 추가
        for (RoleAuthorityVo authority : role.getAuthorities()) {
        	authority.setRoleNm(role.getRoleNm());
    		authority.setCreateDate(updateTime);
    		if (getUserId() != null) {
    			authority.setCreateUserId(getUserId());
            }
    		sqlSessionTemplate.insert("authority.insertRoleAuthority", authority);
    	}
    }
    
    /**
     * 유저 권한 추가
     */
    public boolean addUserRole(List<UserAsgnRoleVo> uas) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = LocalDateTime.now().format(formatter);
        
        int result = 0;
        for (UserAsgnRoleVo ua : uas) {
	        ua.setCreateDate(time);
	        result += sqlSessionTemplate.insert("authority.insertUserAsgnRole", ua);
        }

        return result >= 1;
    }
    
    /**
     * 유저 권한 삭제
     */
    public boolean removeUserRole(List<UserAsgnRoleVo> uas) {

    	int result = 0;
        for (UserAsgnRoleVo ua : uas) {
	        result += sqlSessionTemplate.delete("authority.deleteUserAsgnRole", ua);
        }

        return result >= 1;
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
	 * 비밀번호 변경
	 */
	public Response changePassword(String user, String oldPassword, String newPassword) {
		Response response = new Response();

		AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", user);

		if (securityManager.validatePassword(securityManager.encryptSHA256(securityManager.decodeBase64(newPassword)),
				admin.getPassword())) {
			response.setResultInfo(RestResult.CODE_406.getCode(), "기존 비밀번호와 다른 비밀번호를 입력해야 합니다.");
		} else {
			if (securityManager.validatePassword(securityManager.encryptSHA256(securityManager.decodeBase64(oldPassword)),
					admin.getPassword())) {
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
			} else {
				response.setResultInfo(RestResult.CODE_406.getCode(), "기존 비밀번호가 틀렸습니다.");
			}
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

		AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", user.getId());
		if (admin == null) {
			throw new AuthenticationServiceException("존재하지 않는 아이디입니다." + "||" + user.getId());
		}

		if (securityManager.validatePassword(securityManager.encryptSHA256(user.getPassword()), admin.getPassword())) {
			// user role
			user.setAdmin(admin.isAdmin());
			user.setName(admin.getName());
			// 로그인 실패 횟수 초기화
			initializeLogin(user.getId());
		} else {
			throw new AuthenticationServiceException("비밀번호가 올바르지 않습니다." + "||" + user.getId());
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
