package qualityexecutor.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public abstract class DataControllerBase {
    @Autowired
	private ApplicationContext appContext;
    
    public void startService(Runnable svc) {
    	appContext.getAutowireCapableBeanFactory().autowireBean(svc);
		Thread jobThreadEx = new Thread(svc);
		jobThreadEx.start();
    }
}
