package qualitycenter.config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ndata.common.NDConstant;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.messaging.common.StompBrokerProperties;

import qualitycenter.security.HttpHandshakeInterceptor;
import qualitycenter.service.ws.WebSocketHandlerImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.security.Principal;
import java.util.List;

/**
 * @author WarmBlueDot
 */
@Configuration
@Component
//@EnableWebSocket
@EnableWebSocketMessageBroker
@AllArgsConstructor
@Slf4j
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer/*, WebSocketConfigurer*/ {

    private final String STOMP_HEADER_USE_SOCKET_SESSION = "useSocketSession";
    private final String STOMP_HEADER_SOCKET_USER_NAME = "socketUsername";

    private final StompBrokerProperties stompBrokerProperties;
    private final WebSocketHandlerImpl websocketHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //registry.addEndpoint("/sockjs").setAllowedOriginPatterns("*").withSockJS();
        	//.setAllowedOrigins("http://narae.hillstonefw.com:8080", "http://127.0.0.1:8080", "http://localhost:8080", "http://127.0.0.1:8088", "http://localhost:8088").withSockJS();
        //registry.addEndpoint("/manager").setAllowedOrigins("*").addInterceptors(new HttpHandshakeInterceptor());
        registry.addEndpoint("/sockjs")
            .setAllowedOriginPatterns("*")
            .addInterceptors(new HttpHandshakeInterceptor(websocketHandler))
            .withSockJS()
            .setHeartbeatTime(NDQualityConstant.WEBSOCKET_HEARTBEAT_TIME);
    }
    
    //@Override
    //public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    //    registry.addHandler(webSocketController, "/sockjs").setAllowedOriginPatterns("*").addInterceptors(new HttpHandshakeInterceptor(webSocketController)).withSockJS();
    //}

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
    	// eg. To invoke @MessageMapping("/message"), send message's destination is /app/message
        registry.setApplicationDestinationPrefixes("/app", "/user");
        
        if (stompBrokerProperties.isSimple()) {
        	log.info("=================================");
        	log.info("# Websocket Simple Broker Enabled");
        	log.info("=================================");
        	registry.enableSimpleBroker("/topic", "/exchange");
        } else {
        	log.info("=================================");
        	log.info("# Websocket Relay Broker Enabled");
        	log.info("=================================");
            registry.enableStompBrokerRelay("/topic", "/exchange")
                // remove noise from heartbeats for debug purposes
                .setSystemHeartbeatSendInterval(0)
                .setSystemHeartbeatReceiveInterval(0)
                .setRelayHost(stompBrokerProperties.getHost())
                .setRelayPort(stompBrokerProperties.getPort())
                .setSystemLogin(stompBrokerProperties.getUser())
                .setSystemPasscode(stompBrokerProperties.getPassword())
                .setClientLogin(stompBrokerProperties.getUser())
                .setClientPasscode(stompBrokerProperties.getPassword())
                .setVirtualHost(stompBrokerProperties.getVirtualHost());
                //.setUserRegistryBroadcast("/topic/simp-user-registry")*/;
        }
        
        registry.setUserDestinationPrefix("/user");
        
        // eg. Subscribe message's destination is /topic/{value} and use @SendTo annotation in controller
        // eg. Subscribe message's destination is /user/queue/{value} and use @SendToUser annotation in controller
        //registry.enableSimpleBroker("/topic", "/queue");
    }

    //@Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        // Extract sockjs sessionId, and use it as a Principal name, in order to target a particular web socket session
        // rather than all web socket sessions for a username when sending to "user" destinations with
        // simpMessagingTemplate
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {

                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> useWindowSession = accessor.getNativeHeader(STOMP_HEADER_USE_SOCKET_SESSION);
                    if (useWindowSession != null && useWindowSession.size() > 0) {
                        // Extract sockjs sessionId, and use it as a Principal name, in order to target a particular web
                        // socket session rather than all web socket sessions for a username when sending to "user"
                        // destinations with simpMessagingTemplate

                        // socket accessor에 userName과 sessionId를 세팅한다.
                    	Principal user = accessor.getUser();
                    	String socketSessionId = accessor.getSessionId();
                    	String socketUsername = null;
                    	
                        if (user != null) {
                        	socketUsername =  user.getName();
                            log.info("[principal] User={}, SessionId={}", socketUsername, socketSessionId);
                            SocketDestinationPrincipal customUser = new SocketDestinationPrincipal(socketUsername, socketSessionId);
                            accessor.setUser(customUser);
                        } else if (NDConstant.SVC_MODE_DEV.equals(stompBrokerProperties.getMode())) { // 서비스 모드가 "dev" 일 때는 front html 로부터의 연결 허용
                        	List<String> socketHeader = accessor.getNativeHeader(STOMP_HEADER_SOCKET_USER_NAME);
                        	if (socketHeader != null && socketHeader.size() > 0) {
                        		socketUsername = socketHeader.get(0);
                        	} else {
                        		socketUsername = NDConstant.USER_ADMIN;
                        	}
                        	log.info("[header] User={}, SessionId={}", socketUsername, socketSessionId);
	                        SocketDestinationPrincipal customUser = new SocketDestinationPrincipal(socketUsername, socketSessionId);
	                        accessor.setUser(customUser);
                        }
                    }
                } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    // Configure some RabbitMQ Stomp specific stuff
                    // delete when the last downstream consumers for a queue goes away
                    accessor.setNativeHeader("auto-delete", "true");
                    // delete when declaring connection closes
                    accessor.setNativeHeader("exclusive", "true");
                    // How long a message published to a queue can live before it is discarded (milliseconds).
                    accessor.setNativeHeader("x-message-ttl", "3000");
                    // How long a queue can be unused for before it is automatically deleted (milliseconds).
                    accessor.setNativeHeader("x-expires", String.valueOf(20 * 60 * 1000));
                }

                return message;
            }
        });
    }
    
    /**
     * Configure web socket transport.
     *
     * @param registration the registration
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    	registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(final WebSocketHandler handler) {
                return new WebSocketHandlerDecorator(handler) {
                    @Override
                    public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
                    	websocketHandler.afterConnectionEstablished(session);
                        super.afterConnectionEstablished(session);
                    }
                    
                    @Override
                    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                		// TODO Auto-generated method stub
                    	websocketHandler.afterConnectionClosed(session, closeStatus);
                    	super.afterConnectionClosed(session, closeStatus);
                	}
                };
            }
        });
        registration.setMessageSizeLimit(256 * 64 * 1024);    // Max incoming message size, default : 64 * 1024
        registration.setSendTimeLimit(20 * 10000);            // default : 10 * 10000
        registration.setSendBufferSizeLimit(10 * 512 * 1024); // Max outgoing buffer size, default : 512 * 1024
    }
    
    @Bean
    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean factoryBean = new ServletServerContainerFactoryBean();
        factoryBean.setMaxTextMessageBufferSize(10 * 512 * 1024);
        factoryBean.setMaxBinaryMessageBufferSize(10 * 512 * 1024);
        factoryBean.setMaxSessionIdleTimeout(20L * 10000);
        factoryBean.setAsyncSendTimeout(20L * 10000);
        return factoryBean;
    }

    // @Bean
    // public MessageConverter amqpMessageConverter() {
    //     return new Jackson2JsonMessageConverter();
    // }

    // uncomment for message.user routes to work with RabbitTemplate and StompSession senders
    // though simpMessagingTemplate won't send to "username" destinations for /exchange/amq.direct/message.user destination
    @Bean
    public UsernameUserDestinationResolver userDestinationResolver(SimpUserRegistry simpUserRegistry) {
        return new UsernameUserDestinationResolver(simpUserRegistry);
    }
}
