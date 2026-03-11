/*
 * Developed by kbcho on 2019-08-02.
 * Last modified 2019-08-02 18:23:31.
 * Copyright (c) 2019 OKESTRO. All rights reserved.
 */

package qualitycenter.security;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.model.auth.UserVo;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@Slf4j
public class CustomLogoutHandler implements LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        //UserVo user = (UserVo) authentication.getPrincipal();

        if (authentication != null && authentication.getDetails() != null) {
            try {
                request.getSession().invalidate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);
        //response.sendRedirect("/login.html");
    }
}
