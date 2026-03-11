package qualitycenter.service.ws;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.ndata.model.ws.WsDestType;
import com.ndata.model.ws.WsNoticeLevel;
import com.ndata.model.ws.WsType;
import com.ndata.model.ws.WsVo;

@Component
public class WebSocketService {
    public final String DEST_WINDOW = "/exchange/amq.direct/message.window-user";
    public final String DEST_MESSAGE = "/topic/onMessage";

    private final SimpMessagingTemplate template;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.template = messagingTemplate;
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
        	this.template.convertAndSend(this.DEST_WINDOW + ssId, new Gson().toJson(vo, WsVo.class));
        }
    }
    
    private void sendMessage(WsVo msg) {
        this.template.convertAndSend(this.DEST_MESSAGE, new Gson().toJson(msg, WsVo.class));
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