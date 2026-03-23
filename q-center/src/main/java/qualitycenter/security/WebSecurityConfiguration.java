package qualitycenter.security;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.PortMapperImpl;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * @author WarmBlueDot
 */
@Configuration
@EnableWebSecurity
@EnableGlobalAuthentication
@EnableConfigurationProperties
@ComponentScan(basePackages = {"qualitycenter.**"})
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

	@Value("${server.port}")
    String serverPort;

    @Value("${server.http.port:0}")
    String httpPort;

    @Autowired
    CustomAuthenticationProvider customAuthenticationProvider;

    @Autowired
    CustomAuthFailureHandler customAuthFailureHandler;

    @Autowired
    CustomAuthSuccessHandler customAuthSuccessHandler;

    @Autowired
    CustomLogoutHandler customLogoutHandler;

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/resources/**", "/css/**", "/image/**", "/js/**", "/assets/**", "/static/**", "/webjars/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // 로그인 설정
        http.cors().and().authorizeRequests()
            .antMatchers("/").permitAll() // 인덱스, 메뉴 페이지
            .antMatchers("/*.html").permitAll() // 인덱스, 메뉴 페이지
            .antMatchers("/sockjs/**").permitAll()
            .antMatchers("/login").permitAll()
            .antMatchers("/logout").permitAll()
            .antMatchers("/swagger-ui/**").permitAll()
            .antMatchers("/swagger-resources/**").permitAll()
            .antMatchers("/v3/api-docs/**").permitAll()
            .antMatchers("/api/login/**").permitAll()
            .antMatchers("/api/search/**").permitAll()
            .antMatchers("/api/std/**").permitAll()
            .antMatchers("/api/dm/**").permitAll()
            .antMatchers("/api/sysinfo/**").permitAll()
            .antMatchers("/api/diag/**").permitAll()
            //.antMatchers("/dashboard").hasAnyAuthority(NDConstant.ROLE_ADMIN) // 어드민 대시보드
            //.antMatchers("/userDashboard").hasAnyAuthority(NDConstant.ROLE_MEMBER) // 사용자 대시보드
            //.antMatchers("/api/admin/**").hasAnyAuthority(NDConstant.ROLE_ADMIN) // 관리
            .antMatchers("/api/admin/**").permitAll() // 관리
            //.antMatchers("/**").permitAll()//; // 개발 편의를 위해 모든페이지를 열어놓음
            .anyRequest().authenticated();
        
        // port mapping for preventing https redirect to serverPort
        PortMapperImpl portMapper = new PortMapperImpl();
        java.util.Map<String, String> portMappings = new java.util.HashMap<>();
        portMappings.put(serverPort, serverPort);
        if (!"0".equals(httpPort)) {
            portMappings.put(httpPort, httpPort);
        }
    	portMapper.setPortMappings(portMappings);
    	PortResolverImpl portResolver = new PortResolverImpl();
    	portResolver.setPortMapper(portMapper);
    	
    	LoginUrlAuthenticationEntryPoint entryPoint = new LoginUrlAuthenticationEntryPoint(
    			"/login");
    	entryPoint.setPortMapper(portMapper);
    	entryPoint.setPortResolver(portResolver);
    	
    	//HttpSessionRequestCache cache = new HttpSessionRequestCache();
    	//cache.setPortResolver(portResolver);
    	//http.setSharedObject(RequestCache.class, cache);

        // 로그인 페이지 및 로그인 성공 url, handler, 로그인시 사용되는 id, password 정의
        http.exceptionHandling()
		    .authenticationEntryPoint(entryPoint).and().cors().and().formLogin()
            .loginPage("/login")
            .failureHandler(customAuthFailureHandler)
            .successHandler(customAuthSuccessHandler)
            .usernameParameter("id")
            .passwordParameter("password");

        // 로그아웃 관련 설정
        http.cors().and().logout().logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
            .logoutSuccessHandler(customLogoutHandler)
            //.logoutSuccessUrl("/login")
            .invalidateHttpSession(true);

        // 중복 로그인 관련 설정
//        http.sessionManagement()
//            .maximumSessions(1)
//            .maxSessionsPreventsLogin(false)
//            .expiredUrl("/login")
//            .sessionRegistry(sessionRegistry());

        // csrf 사용유무 설정
        // csrf 설정을 사용하면 모든 request에 csrf 값을 함께 전달해야한다.
//            .csrf()
        http.csrf().disable(); // 개발 시 에만
        
        // 로그인 프로세스가 진행될 provider
        http.authenticationProvider(customAuthenticationProvider);

//                // ROLE_USER, ROLE_ADMIN으로 권한 분리 유알엘 정의
//                .antMatchers("/", "/user/login", "/error**").permitAll()
//                .antMatchers("/**").access("ROLE_MEMBER")
//                .antMatchers("/**").access("ROLE_ADMIN")
//                .antMatchers("/admin/**").access("ROLE_ADMIN")
//                .antMatchers("/**").authenticated()
    }
    
    //@Bean
    CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(Arrays.asList("*"/*"http://127.0.0.1:8080", "http://localhost:8080" */));
		configuration.setAllowedHeaders(Arrays.asList("*"/*"Origin,Accept,X-Requested-With,Content-Type,Access-Control-Request-Method,Access-Control-Request-Headers,Authorization" */));
        configuration.setAllowedMethods(Arrays.asList("*"/*"POST", "PUT", "GET", "OPTIONS", "DELETE" */));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(86400L);

        // you can configure many allowed CORS headers
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }
    
    //login page redirection 시에 JSESSIONID가 생성되지 않는 문제 해결을 위해서 추가
    @Bean
	public CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("JSESSIONID"); 
		serializer.setCookiePath("/"); 
		serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$"); 
		return serializer;
	}
}

