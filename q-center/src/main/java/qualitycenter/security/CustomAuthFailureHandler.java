package qualitycenter.security;

import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import com.ndata.common.NDSeverity;

import qualitycenter.service.auth.SessionService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    @Autowired
    SessionService sessionService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException e) throws IOException, ServletException {

        String message = null;
        String[] loginData = null;

        if (e instanceof UsernameNotFoundException) {
            message = "로그인에 실패했습니다.";
        } else if (e instanceof BadCredentialsException) {
            message = "로그인에 실패했습니다.";
        } else if (e instanceof SessionAuthenticationException) {
            message = "동일한 아이디로 접속되어 있습니다.";
        } else if (e instanceof InternalAuthenticationServiceException) {
            message = "error.BadCredentials";
        } else if (e instanceof DisabledException) {
            message = "error.Disaled";
        } else if (e instanceof CredentialsExpiredException) {
            message = "error.CredentialsExpired";
        } else if (e instanceof AuthenticationServiceException) {
            loginData = e.getMessage().split("\\|\\|");
            message = loginData[0];

            if(loginData.length > 1) {
                // 로그인 실패 횟수 증가
                sessionService.increaseLoginFailCount(loginData[1]);
                // 로그
                sessionService.putAuditLog(NDSeverity.ERROR, loginData[1], message);
            }
        } else {
            message = e.getMessage();
        }

        System.out.println("error class:" + e.getClass());
        System.out.println("error message:" + e.getMessage());

        ObjectMapper om = new ObjectMapper();

        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("message", message);

        String jsonString = om.writeValueAsString(map);

        OutputStream out = httpServletResponse.getOutputStream();
        out.write(jsonString.getBytes());
        out.flush();
        out.close();
    }
}
