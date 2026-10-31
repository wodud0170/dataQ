package qualitycenter.security;

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

import qualitycenter.model.auth.UserVo;
import qualitycenter.service.auth.SessionService;


@Component("authProvider")
public class CustomAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    SecurityManager securityManager;

    @Autowired
    private SessionService sessionService;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String id = authentication.getName();
        String password = securityManager.decodeBase64((String) authentication.getCredentials());

        // 내장 관리자가 없을 때(최초 설치) — 아무도 로그인할 수 없다. 내장 관리자는 /api/login/createAdmin 으로 만든다.
        // 실패 로그는 여기서 한 건 남긴다(메시지에 "||id" 가 없어 failure handler 는 남기지 않는다).
        if(!sessionService.existAdmin()) {
            String message = id.equals(NDConstant.USER_ADMIN)
                    ? "내장 관리자 계정이 없습니다. 관리자 계정을 먼저 만드세요."
                    : "관리자 초기설정에 실패했습니다.\n관리자에게 문의하십시오.";
            sessionService.putAuditLog(NDSeverity.ERROR, id, message);
            throw new AuthenticationServiceException(message);
        }

        UserVo user = new UserVo();
        user.setId(id);
        user.setPassword(password);
        // Authority(권한) 설정 부분 검토 필요
        user = sessionService.login(user);
        // 인증이 끝나면 평문 비밀번호를 세션 사용자(Principal)·토큰에 남기지 않는다
        user.setPassword(null);

        // 로그
        sessionService.putAuditLog(NDSeverity.INFO, id, "로그인에 성공했습니다.");
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
