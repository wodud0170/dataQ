package qualityexecutor.service.std;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.stomp.StompSessionService;

import com.ndata.model.ws.WsNoticeLevel;
import com.ndata.quality.model.std.UploadResult;
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
	private UploadResult lastResult;

	public UploadResult getLastResult() {
		return lastResult;
	}

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
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "[단어] 일괄등록 시작");
			UploadResult result = excelUploadService.uploadWords(userId, multiPart);
			sendUploadResult(ssId, "[단어]", result);
			if (result.getSuccessCount() > 0) {
				stompSessionService.sendNotice(WsNoticeLevel.INFO, "단어가 일괄저장되었습니다.");
			}
		} catch (Exception e) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, "[단어] 일괄등록 시작"); } catch (Exception ignored) {}
			try { stompSessionService.sendNotice(WsNoticeLevel.ERROR, "[단어] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			log.error(">> uploadWords failed.", e);
		}
	}

	//용어 일괄 저장
	public void uploadTermsList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "[용어] 일괄등록 시작");
			UploadResult result = excelUploadService.uploadTermsList(userId, multiPart);
			this.lastResult = result;
			sendUploadResult(ssId, "[용어]", result);
			if (result.getSuccessCount() > 0) {
				stompSessionService.sendNotice(WsNoticeLevel.INFO, "용어가 일괄저장되었습니다.");
			}
		} catch (Exception e) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, "[용어] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			try { stompSessionService.sendNotice(WsNoticeLevel.ERROR, "[용어] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			log.error(">> uploadTermsList failed.", e);
		}
	}

	//코드정보 일괄 저장
	public void uploadCodeInfoList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "[코드] 일괄등록 시작");
			UploadResult result = excelUploadService.uploadCodeInfoList(userId, multiPart);
			sendUploadResult(ssId, "[코드]", result);
			if (result.getSuccessCount() > 0) {
				stompSessionService.sendNotice(WsNoticeLevel.INFO, "코드정보가 일괄저장되었습니다.");
			}
		} catch (Exception e) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, "[코드] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			try { stompSessionService.sendNotice(WsNoticeLevel.ERROR, "[코드] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			log.error(">> uploadCodeInfoList failed.", e);
		}
	}

	//코드데이터 일괄 저장
	public void uploadCodeDataList(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "[코드데이터] 일괄등록 시작");
			UploadResult result = excelUploadService.uploadCodeDataList(userId, multiPart);
			sendUploadResult(ssId, "[코드데이터]", result);
			if (result.getSuccessCount() > 0) {
				stompSessionService.sendNotice(WsNoticeLevel.INFO, "코드데이터(항목값)가 일괄저장되었습니다.");
			}
		} catch (Exception e) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, "[코드데이터] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			try { stompSessionService.sendNotice(WsNoticeLevel.ERROR, "[코드데이터] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			log.error(">> uploadCodeDataList failed.", e);
		}
	}

	//도메인 일괄 저장
	public void uploadDomains(String userId, String ssId, MultipartFile multiPart) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		try {
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "[도메인] 일괄등록 시작");
			UploadResult result = excelUploadService.uploadDomains(userId, multiPart);
			sendUploadResult(ssId, "[도메인]", result);
			if (result.getSuccessCount() > 0) {
				stompSessionService.sendNotice(WsNoticeLevel.INFO, "도메인이 일괄저장되었습니다.");
			}
		} catch (Exception e) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, "[도메인] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			try { stompSessionService.sendNotice(WsNoticeLevel.ERROR, "[도메인] 일괄등록 실패: " + e.getMessage()); } catch (Exception ignored) {}
			log.error(">> uploadDomains failed.", e);
		}
	}

	private void sendUploadResult(String ssId, String tag, UploadResult result) {
		for (String detail : result.getFailDetails()) {
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, tag + " 실패: " + detail); } catch (Exception ignored) {}
		}
		if (result.getFailCount() > result.getFailDetails().size()) {
			int remaining = result.getFailCount() - result.getFailDetails().size();
			try { stompSessionService.sendMessage(ssId, WsNoticeLevel.ERROR, tag + " 실패 상세 " + remaining + "건 생략 (최대 100건만 표시)"); } catch (Exception ignored) {}
		}
		WsNoticeLevel summaryLevel = result.getFailCount() > 0 ? WsNoticeLevel.ERROR : WsNoticeLevel.INFO;
		String summary = String.format("%s 완료 - 전체: %d건, 등록: %d건, 중복: %d건, 실패: %d건",
				tag, result.getTotalCount(), result.getSuccessCount(), result.getSkipCount(), result.getFailCount());
		// 개인 큐 전송 실패해도 브로드캐스트는 반드시 전송
		try { stompSessionService.sendMessage(ssId, summaryLevel, summary); } catch (Exception ignored) {}
		try { stompSessionService.sendNotice(summaryLevel, summary); } catch (Exception ignored) {}
	}
}
