package qualitycenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

/**
 * 로그인 세션 목록 (110번 11.7-14).
 *
 * <p>WebSecurityConfiguration 의 세션 관리(만료된 세션 거부)와 SessionService.expireSessions(사용자 삭제 때 그 사용자 세션 만료)가
 * 같은 목록을 쓴다. WebSecurityConfiguration → CustomAuthenticationProvider → SessionService 가 이 빈을 주입받으므로
 * 순환을 피하려고 보안 설정 클래스 밖에 둔다.</p>
 */
@Configuration
public class SessionRegistryConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}
