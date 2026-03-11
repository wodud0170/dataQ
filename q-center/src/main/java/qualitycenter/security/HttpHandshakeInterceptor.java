package qualitycenter.security;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import qualitycenter.service.ws.WebSocketHandlerImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpHandshakeInterceptor implements HandshakeInterceptor {
	
	private final WebSocketHandlerImpl websocketHandler;
	
	public HttpHandshakeInterceptor(WebSocketHandlerImpl websocketHandler) {
		this.websocketHandler = websocketHandler;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();

        if (auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails) {
	        UserDetails principal = (UserDetails) auth.getPrincipal();
	        log.info("[principal] Authenticated User={}, IP Address={}", principal.getUsername(), details.getRemoteAddress());
        } else {
        	log.error("Websocket handshake refused because of not authenticated request from {}", details.getRemoteAddress());
        }
        
		if (request instanceof ServletServerHttpRequest) {
			ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
			HttpSession session = servletRequest.getServletRequest().getSession();
			attributes.put("sessionId", session.getId());
			//WebSocketHandler에 HttpSession정보를 저장한다.
			((WebSocketHandlerImpl)this.websocketHandler).setHttpSession(session.getId(), session);
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// TODO Auto-generated method stub
		//ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
		//HttpSession session = servletRequest.getServletRequest().getSession();
		//log.info(">>{}", session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
	}
}