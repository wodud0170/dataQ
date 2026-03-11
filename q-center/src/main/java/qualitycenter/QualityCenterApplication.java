package qualitycenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"qualitycenter.**","com.ndata.quality.**","com.ndata.bean.**","com.ndata.messaging.common.**"})
public class QualityCenterApplication {
	public static void main(String[] args) {
		SpringApplication.run(QualityCenterApplication.class, args);
	}
}
