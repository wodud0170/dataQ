package qualitycenter.security;

import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import com.ndata.common.NDConstant;

import qualitycenter.model.auth.UserVo;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CustomAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) throws IOException, ServletException {

        UserVo user = (UserVo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        ObjectMapper om = new ObjectMapper();

        Map<String, Object> map = new HashMap<>();
        map.put("success", true);

        if (user.isAdmin()) {
        	map.put("role", NDConstant.ROLE_ADMIN);
        } else {
            map.put("role", NDConstant.ROLE_MEMBER);
        }
        
        String jsonString = om.writeValueAsString(map);

        OutputStream out = httpServletResponse.getOutputStream();
        httpServletResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
        httpServletResponse.setHeader("Access-Control-Allow-Credentials","true");
        
        out.write(jsonString.getBytes());
        out.flush();
        out.close();
    }
}
