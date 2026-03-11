package qualitycenter.service.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebSocketHandlerImpl implements WebSocketHandler {
	private final Map<String,HttpSession> httpSessionMap = new ConcurrentHashMap<String,HttpSession>();
	//private final Map<String,WebSocketSession> webSocketSessionMap = new ConcurrentHashMap<String,WebSocketSession>();

	/*
    @Scheduled(initialDelay = 10*1000, fixedRate = 10*1000)
	public void checkWebsocketSession() {
		log.info(">> websocket session check");
		webSocketSessionMap.values().forEach(session -> {
			if (session.isOpen()) {
				log.info(">> websocket session check : {}", session.getId());
				try {
					session.sendMessage(new TextMessage("ping"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					log.info(">> websocket session check error", e.getMessage());
				}
			}
		});
	}*/

	@Override
	public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
		// TODO Auto-generated method stub
		// Websocket connection 후에 HttpSession 에 Websocket session id(SSID)를 저장한다.
		//webSocketSessionMap.put(session.getId(), session);
		String httpSessionId = (String)session.getAttributes().get("sessionId");
		HttpSession httpSession = httpSessionMap.get(httpSessionId);
		if (httpSession != null) {
			httpSession.setAttribute("SSID", session.getId());
			log.info(">> HttpSession : [{}] saved", httpSessionId);
		}
	}

	@Override
	public void handleMessage(org.springframework.web.socket.WebSocketSession session, WebSocketMessage<?> message)
			throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public void handleTransportError(org.springframework.web.socket.WebSocketSession session, Throwable exception)
			throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session, CloseStatus closeStatus)
			throws Exception {
		// TODO Auto-generated method stub
		// Websocket connection 이 close되면 HttpSession map에서 session정보를 삭제한다.
		//webSocketSessionMap.remove(session.getId());
		String httpSessionId = (String)session.getAttributes().get("sessionId");
		httpSessionMap.remove(httpSessionId);
		log.info(">> HttpSession : [{}] removed", httpSessionId);
	}

	@Override
	public boolean supportsPartialMessages() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void setHttpSession(String sessionId, HttpSession httpSession) {
		httpSessionMap.put(sessionId, httpSession);
	}
}
