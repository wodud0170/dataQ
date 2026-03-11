package qualitycenter.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.ndata.common.NDConstant;
import com.ndata.common.message.Request;
import com.ndata.common.message.Response;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.messaging.common.CustomHeaders;
import com.ndata.messaging.common.StompBrokerProperties;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.concurrent.Executor;

/**
 * @author WarmBlueDot
 */
@Controller
@AllArgsConstructor
@Slf4j
public class WebSocketController {

	private StompBrokerProperties stompBrokerProperties;
	private final Executor executor;

    public WebSocketController() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(threadCount * 2);//modified from executor.setQueueCapacity(0)
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();

        this.executor = executor;
    }
	
    @MessageMapping("/message")
    public void messageHandler(Message<Request> message,
                               @Header(CustomHeaders.HEADER_SCOKET_SESSION_ID) String socketSessionId,
                               //@Header(CustomHeaders.HEADER_USERNAME) String socketUsername,
                               Principal principal) {
        //Assert.notNull(principal);
    	Request request = message.getPayload();
    	
    	String socketUsername = null;
    	if (principal != null) {
    		socketUsername = principal.getName();
    	} else if (NDConstant.SVC_MODE_DEV.equals(stompBrokerProperties.getMode())) { // 서비스 모드가 "dev" 일 때는 front html 로부터의 연결 허용
    		socketUsername = NDConstant.USER_ADMIN;
    	}
    	
    	//log.info("[socketUsername] {}, [socketSessionId] {}", socketUsername, socketSessionId);
        if (socketUsername == null || request.getServiceName() == null) {
        	return;
        }
    }

    @AllArgsConstructor
    private static class SendTask implements Runnable {
        final String url;
        final String username;
        final String socketSessionId;
        final Request request;

        @Override
        public void run() {
            try {
            	// 메시지를 서비스에 전달한다.
            	WebClientHandler webClientHandler = new WebClientHandler(this.url);
                Mono<Response> mResponse = webClientHandler.postData(username, socketSessionId, request);
                // non-blocking Mono 응답을 subscribe 한다.
                mResponse.subscribe();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
