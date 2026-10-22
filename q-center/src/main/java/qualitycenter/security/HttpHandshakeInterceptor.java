package qualitycenter.security;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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

		// 로그인하지 않은 연결도 받는다 — q-executor 가 이 연결로 진행·완료 메시지를 보낸다(104번 §3.3 허용 경로).
		// 109번 #6 은 사용자를 정하는 곳(CONNECT — 로그인 세션만)과 구독 목적지 제한(SUBSCRIBE)으로 막는다(WebSocketConfiguration).
		// 로그인하지 않은 연결은 사용자가 없어 /user/ 구독이 자기 세션 목적지로만 풀린다
		if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails) {
			UserDetails principal = (UserDetails) auth.getPrincipal();
			log.info("[principal] Authenticated User={}, IP Address={}", principal.getUsername(), request.getRemoteAddress());
		} else {
			log.info("Websocket handshake without login from {}", request.getRemoteAddress());
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