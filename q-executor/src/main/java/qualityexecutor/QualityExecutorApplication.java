package qualityexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"qualityexecutor.**","com.ndata.quality.**","com.ndata.bean.**","com.ndata.messaging.common.**"})
public class QualityExecutorApplication {
	public static void main(String[] args) {
		SpringApplication.run(QualityExecutorApplication.class, args);
	}
}
