package qualityexecutor.config;

import java.util.Collections;
import java.util.List;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.stomp.ReactorNettyTcpStompSessionManager;
import org.springframework.integration.stomp.StompSessionManager;
import org.springframework.integration.stomp.WebSocketStompSessionManager;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.ReactorNettyTcpStompClient;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ndata.messaging.common.StompBrokerProperties;

/**
 * @author WarmBlueDot
 */
@Slf4j
@Configuration
@EnableIntegration
@AllArgsConstructor
public class AmqpConfiguration {

	private final StompBrokerProperties stompBrokerProperties;

	@Bean
	public StompSessionManager stompSessionManager() {
		if (stompBrokerProperties.isSimple()) {//using stomp simple broker
			log.info("=======================================");
        	log.info("# Websocket Connecting to Simple Broker");
        	log.info("=======================================");
			//set stomp client
			final WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
			StandardWebSocketClient wsClient = new StandardWebSocketClient(wsContainer);
			//ClientManager container = ClientManager.createClient();
	        //container.getProperties().put(ClientProperties.SHARED_CONTAINER, true);
			
			//->SSL연결일 경우에 인증정보 설정
			if (stompBrokerProperties.getUrl().startsWith("wss")) {
				//System.getProperties().put("javax.net.debug", "all"); // debug your certificate checking
				System.getProperties().put(SslContextConfigurator.KEY_STORE_FILE, stompBrokerProperties.getSslKeyStore());
				System.getProperties().put(SslContextConfigurator.TRUST_STORE_FILE, stompBrokerProperties.getSslKeyStore());
				System.getProperties().put(SslContextConfigurator.KEY_STORE_PASSWORD, stompBrokerProperties.getSslKeyStorePassword());
				System.getProperties().put(SslContextConfigurator.TRUST_STORE_PASSWORD, stompBrokerProperties.getSslKeyStorePassword());
				System.getProperties().put(SslContextConfigurator.KEY_STORE_TYPE, stompBrokerProperties.getSslKeyStoreType());
				System.getProperties().put(SslContextConfigurator.TRUST_STORE_TYPE, stompBrokerProperties.getSslKeyStoreType());
				final SslContextConfigurator defaultConfig = new SslContextConfigurator();
				defaultConfig.retrieve(System.getProperties());

				SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(defaultConfig);
				sslEngineConfigurator.setHostVerificationEnabled(false);//적용 안되며, 원인체크 필요함
				//sslEngineConfigurator.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
			    //    public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
			    //    	log.info("hostname--->{}", hostname);
			    //        return true;
			    //    }
			    //});
				wsClient.getUserProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
				
				//try {
				//	container.connectToServer(new Endpoint() {
				//	    @Override
				//	    public void onOpen(Session session, EndpointConfig endpointConfig) {
				//	    }
				//	  }, ClientEndpointConfig.Builder.create().build(), new URI(stompBrokerProperties.getUrl() + "/sockjs"));
				//} catch (DeploymentException | IOException | URISyntaxException e) {
					// TODO Auto-generated catch block
				//	e.printStackTrace();
				//}
			}
			
			Transport webSocketTransport = new WebSocketTransport(wsClient);
			List<Transport> transports = Collections.singletonList(webSocketTransport);

			SockJsClient client = new SockJsClient(transports);
			WebSocketStompClient stompClient = new WebSocketStompClient(client);
			
			//set message handler
			stompClient.setMessageConverter(new MappingJackson2MessageConverter());
			ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
			taskScheduler.afterPropertiesSet();
			stompClient.setTaskScheduler(taskScheduler);
			stompClient.setReceiptTimeLimit(5000);
			
			//set stomp session manager
			WebSocketStompSessionManager stompSessionManager = new WebSocketStompSessionManager(stompClient,
					stompBrokerProperties.getUrl() + "/sockjs");
			stompSessionManager.setAutoReceipt(true);
			
			return stompSessionManager;
		} else {//using stomp relay broker (RabbitMQ)
			log.info("=======================================");
        	log.info("# Websocket Connecting to Relay Broker");
        	log.info("=======================================");
			//set stomp client
			ReactorNettyTcpStompClient stompClient = new ReactorNettyTcpStompClient(stompBrokerProperties.getHost(),
					stompBrokerProperties.getPort());
			
			//set message handler
			stompClient.setMessageConverter(new MappingJackson2MessageConverter());
			ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
			taskScheduler.afterPropertiesSet();
			stompClient.setTaskScheduler(taskScheduler);
			stompClient.setReceiptTimeLimit(5000);

			//set stomp session manager
			ReactorNettyTcpStompSessionManager stompSessionManager = new ReactorNettyTcpStompSessionManager(
					stompClient);
			stompSessionManager.setAutoReceipt(true);
			StompHeaders headers = new StompHeaders();
			headers.add(StompHeaders.LOGIN, stompBrokerProperties.getUser());
			headers.add(StompHeaders.PASSCODE, stompBrokerProperties.getPassword());
			stompSessionManager.setConnectHeaders(headers);
			
			return stompSessionManager;
		}
	}
}
