package qualitycenter.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpWebServerConfig {
	
    //HTTPS를 사용할 경우에 별도 포트로 HTTP 포트를 오픈한다.
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> cookieProcessorCustomizer() {
    	if (httpPort == 0) {
    		return null;
    	}
        return (TomcatServletWebServerFactory factory) -> {
            // also listen on http
            final Connector connector = new Connector();
            connector.setPort(httpPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
    
    @Value("${server.http.port:0}")
    private int httpPort;

}
