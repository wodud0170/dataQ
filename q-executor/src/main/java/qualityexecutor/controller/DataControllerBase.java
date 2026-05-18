package qualityexecutor.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public abstract class DataControllerBase {
    @Autowired
	private ApplicationContext appContext;

    public void startService(Runnable svc) {
    	appContext.getAutowireCapableBeanFactory().autowireBean(svc);
		Thread jobThreadEx = new Thread(svc);
		jobThreadEx.start();
    }

    public void runService(Runnable svc) {
        appContext.getAutowireCapableBeanFactory().autowireBean(svc);
        svc.run();
    }

    /**
     * 88번 fix — 업로드 multipart 파일을 요청 스레드에서 즉시 메모리(byte[])로 복사한다.
     * 백그라운드 스레드가 임시파일 대신 이 복사본을 사용 → 임시파일 삭제로 인한
     * FileNotFoundException 회피. 반드시 컨트롤러(요청 스레드) 안에서 호출할 것.
     */
    protected MultipartFile inMemory(MultipartFile src) throws IOException {
        return new InMemoryMultipartFile(src);
    }
}
