package qualitycenter.security;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.ndata.bean.SecurityManager;
import com.ndata.common.NDConstant;
import com.ndata.common.NDSeverity;
import com.ndata.module.DateUtils;

import qualitycenter.model.auth.AdminVo;
import qualitycenter.model.auth.UserVo;
import qualitycenter.service.auth.SessionService;


@Component("authProvider")
public class CustomAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    SecurityManager securityManager;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private SessionService sessionService;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String id = authentication.getName();
        String password = securityManager.decodeBase64((String) authentication.getCredentials());

        UserVo user = new UserVo();

        // Admin 없을 때 - 최초 설치 시
        if(!sessionService.existAdmin()) {
            if(id.equals(NDConstant.USER_ADMIN)) {

                String blockTime = sqlSessionTemplate.selectOne("login.getBlockTime", id);

                if(blockTime != null) {
                    if(!DateUtils.compareTime(blockTime)) {
                        throw new AuthenticationServiceException("로그인 차단 시간[" + DateUtils.changeTimeFormat(blockTime) + "]이 지나지 않았습니다.");
                    }
                }

                // Admin 로그인
                AdminVo admin = sqlSessionTemplate.selectOne("login.getUser", id);

                if(securityManager.validatePassword(securityManager.encryptSHA256(password), admin.getPassword())) {
                    // 로그인 실패 횟수 초기화 및 차단 시간 수정
                    sessionService.initializeLogin(id);

                    user.setId(id);
                    user.setPassword(password);
                    user.setName(admin.getName());
                    user.setAdmin(true);

                    // 로그
                    sessionService.putAuditLog(NDSeverity.INFO, id, "로그인에 성공했습니다." + "||" + id);
                    return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
                } else {
                	sessionService.putAuditLog(NDSeverity.ERROR, id, "비밀번호가 올바르지 않습니다." + "||" + id);
                    throw new AuthenticationServiceException("비밀번호가 올바르지 않습니다." + "||" + id);
                }
            } else {
            	sessionService.putAuditLog(NDSeverity.ERROR, id, "관리자 초기설정에 실패했습니다.\n관리자에게 문의하십시오." + "||" + id);
                throw new AuthenticationServiceException("관리자 초기설정에 실패했습니다.\n관리자에게 문의하십시오.");
            }
        } else {
            user.setId(id);
            user.setPassword(password);
            // Authority(권한) 설정 부분 검토 필요
            user = sessionService.login( user);
        }

        // 로그
        sessionService.putAuditLog(NDSeverity.INFO, id, "로그인에 성공했습니다." + "||" + id);
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
    
}
