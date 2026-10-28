package qualityexecutor.service.stomp;

import com.google.gson.Gson;
import com.ndata.model.ws.WsDestType;
import com.ndata.model.ws.WsNoticeLevel;
import com.ndata.model.ws.WsType;
import com.ndata.model.ws.WsVo;
import com.ndata.module.StringUtils;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.integration.stomp.StompSessionManager;
import org.springframework.stereotype.Component;

@Component
public class StompSessionService {

	private StompSession stompSession;
    private final StompSessionManager stompSessionManager;
    private final String DEST_WINDOW = "/exchange/amq.direct/message.window-user";
    private final String DEST_MESSAGE = "/topic/onMessage";
    private final String DEST_USER = "/exchange/amq.direct/message.user-user";

    public StompSessionService(StompSessionManager stompSessionManager) {
        this.stompSessionManager = stompSessionManager;
    }

	@EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
    	stompSessionManager.connect(new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            	StompSessionService.this.stompSession = session;
            }
        });
    }

    /**
     * 웹소켓 클라이언트에게 메시지를 전송
     * destination으로 subscription 하는 대상 전체에게 메시지(message)를 전송한다.
     * @param msg	sendMessage
     */
    public void sendMessage(String ssId, WsNoticeLevel noticeType, String msg) {
    	WsVo vo = new WsVo();
        vo.setType(WsType.NOTICE);
        vo.setNoticeType(noticeType);
        vo.setData(msg);
        vo.setReceiver("ALL");
        if (StringUtils.isEmpty(ssId)) {
        	sendMessage(vo);
        } else {
        	this.stompSession.send(this.DEST_WINDOW + ssId, new Gson().toJson(vo, WsVo.class));
        }
    }
    
    private void sendMessage(WsVo msg) {
        this.stompSession.send(this.DEST_MESSAGE, new Gson().toJson(msg, WsVo.class));
    }

    /**
     * 변경된 정보를 다시 읽도록 reload 메시지를 전송
     * @param data	reload 시 필요한 정보를 전송(없을시 null)
     */
    public void sendReload(WsDestType dest, String data){
        WsVo vo = new WsVo();
        vo.setType(WsType.RELOAD);
        vo.setDest(dest);
        vo.setData(data);
        vo.setReceiver("ALL");

        sendMessage(vo);
    }

    /**
     * 110 4.12-37-b — reload 메시지를 요청한 창(소켓 세션)에만 전송. ssId 가 없으면 전체 (sendMessage 와 같은 규칙)
     */
    public void sendReload(String ssId, WsDestType dest, String data) {
        WsVo vo = new WsVo();
        vo.setType(WsType.RELOAD);
        vo.setDest(dest);
        vo.setData(data);
        vo.setReceiver("ALL");
        if (StringUtils.isEmpty(ssId)) {
            sendMessage(vo);
        } else {
            this.stompSession.send(this.DEST_WINDOW + ssId, new Gson().toJson(vo, WsVo.class));
        }
    }

    /**
     * 110 4.12-41-b — 로그인 사용자별 알림(헤더 종). q-center WebSocketService.sendUserNotis 와 같은 목적지·본문이다
     * (화면은 /user/exchange/amq.direct/message.user 를 구독하고 q-center 가 사용자 목적지로 바꾼다). 알림 행은 호출한 쪽이 커밋한 뒤에 보낸다.
     */
    public void sendUserNotis(java.util.List<com.ndata.quality.service.AprvService.Noti> notis) {
        for (com.ndata.quality.service.AprvService.Noti n : notis) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("type", "NOTI");
            m.put("notiTp", n.notiTp);
            m.put("title", n.title);
            m.put("msg", n.msg);
            m.put("docId", n.docId);
            this.stompSession.send(this.DEST_USER + n.userId, new Gson().toJson(m));
        }
    }

    /**
     * alram 메시지를 전송
     * @param noticeType	INFO, WARNING, ERROR 등
     * @param data	전송할 알림 메시지
     */
    public void sendNotice(WsNoticeLevel noticeType, String data){
        WsVo vo = new WsVo();
        vo.setType(WsType.NOTICE);
        vo.setNoticeType(noticeType);
        vo.setData(data);
        vo.setReceiver("ALL");

        sendMessage(vo);
    }


    /**
     * 변경된 정보를 다시 읽도록 reload 메시지를 전송
     * @param dest	reload 할 대상(PROJECT, VM, NETWORK 등)
     * @param data	reload 시 필요한 정보를 전송(없을시 null)
     * @param reciever	메시지 수신자
     */
    public void sendReload(WsDestType dest, String data, String receiver){
        WsVo vo = new WsVo();
        vo.setType(WsType.RELOAD);
        vo.setDest(dest);
        vo.setData(data);
        vo.setReceiver(receiver);

        sendMessage(vo);
    }

    /**
     * alram 메시지를 전송
     * @param noticeLevel	INFO, WARNING, ERROR 등
     * @param data	전송할 알림 메시지
     * @param receiver	메시지 수신자
     */
    public void sendNotice(WsNoticeLevel noticeLevel, String data, String receiver){
        WsVo vo = new WsVo();
        vo.setType(WsType.NOTICE);
        vo.setNoticeType(noticeLevel);
        vo.setData(data);
        vo.setReceiver(receiver);

        sendMessage(vo);
    }
}