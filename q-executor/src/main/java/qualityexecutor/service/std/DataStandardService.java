package qualityexecutor.service.std;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.stomp.StompSessionService;

import com.ndata.model.ws.WsNoticeLevel;
import com.ndata.quality.service.ExcelUploadService;

@Slf4j
@NoArgsConstructor
@Service
public class DataStandardService implements Runnable {
	public static final String JOB_TYPE_WORDS = "uploadWords";
	public static final String JOB_TYPE_TERMS = "uploadTermsList";
	public static final String JOB_TYPE_CODE_INFO = "uploadCodeInfoList";
	public static final String JOB_TYPE_CODE_DATA = "uploadCodeDataList";
	public static final String JOB_TYPE_DOMAINS = "uploadDomains";

	private String jobType;
	private String userId;
	private String ssId;
	private MultipartFile multiPart;

	@Autowired
	private StompSessionService stompSessionService;
	
	@Autowired
	private ExcelUploadService excelUploadService;

	public DataStandardService(String jobType, String userId, String ssId, MultipartFile multiPart) {
		this.jobType = jobType;
		this.userId = userId;
		this.ssId = ssId;
		this.multiPart = multiPart;
	}

	@Override
	public void run() {
		switch(jobType) {
			case JOB_TYPE_WORDS:
				uploadWords(userId, ssId, multiPart);
				break;
			case JOB_TYPE_TERMS:
				uploadTermsList(userId, ssId, multiPart);
				break;
			case JOB_TYPE_CODE_INFO:
				uploadCodeInfoList(userId, ssId, multiPart);
				break;
			case JOB_TYPE_CODE_DATA:
				uploadCodeDataList(userId, ssId, multiPart);
				break;
			case JOB_TYPE_DOMAINS:
				uploadDomains(userId, ssId, multiPart);
				break;
		}
	}	

	//단어 일괄 저장
	public void uploadWords(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadWords start");
			excelUploadService.uploadWords(userId, multiPart);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadWords finished");
			stompSessionService.sendNotice(WsNoticeLevel.INFO, "단어가 일괄저장되었습니다.");
		} catch (Exception e) {
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "단어 일괄저장에 실패하였습니다. : " + e.getMessage());
			log.error(">> uploadWords failed.", e);
		}
	}

	//용어 일괄 저장
	public void uploadTermsList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadTermsList start");
			excelUploadService.uploadTermsList(userId, multiPart);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadTermsList finished");
			stompSessionService.sendNotice(WsNoticeLevel.INFO, "용어가 일괄저장되었습니다.");
		} catch (Exception e) {
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "용어 일괄저장에 실패하였습니다. : " + e.getMessage());
			log.error(">> uploadTermsList failed.", e);
		}
	}

	//코드정보 일괄 저장
	public void uploadCodeInfoList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadCodeInfoList start");
			excelUploadService.uploadCodeInfoList(userId, multiPart);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadCodeInfoList finished");
			stompSessionService.sendNotice(WsNoticeLevel.INFO, "코드정보가 일괄저장되었습니다.");
		} catch (Exception e) {
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "코드정보 일괄저장에 실패하였습니다. : " + e.getMessage());
			log.error(">> uploadCodeInfoList failed.", e);
		}
	}

	//코드데이터 일괄 저장
	public void uploadCodeDataList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadCodeDataList start");
			excelUploadService.uploadCodeDataList(userId, multiPart);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadCodeDataList finished");
			stompSessionService.sendNotice(WsNoticeLevel.INFO, "코드데이터(항목값)가 일괄저장되었습니다.");
		} catch (Exception e) {
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "코드데이터 일괄저장에 실패하였습니다. : " + e.getMessage());
			log.error(">> uploadCodeDataList failed.", e);
		}
	}

	//도메인 일괄 저장
	public void uploadDomains(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadDomains start");
			excelUploadService.uploadDomains(userId, multiPart);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> uploadDomains finished");
			stompSessionService.sendNotice(WsNoticeLevel.INFO, "도메인이 일괄저장되었습니다.");
		} catch (Exception e) {
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "도메인 일괄저장에 실패하였습니다. : " + e.getMessage());
			log.error(">> uploadDomains failed.", e);
		}
	}
}
