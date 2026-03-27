package qualityexecutor.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.messaging.common.CustomHeaders;
import com.ndata.quality.model.std.TermAnalysisResult;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.DataStandardService;
import qualityexecutor.service.std.TermRecommendService;
import qualityexecutor.service.stomp.StompSessionService;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/std")
public class DataStandardController extends DataControllerBase {
	
	@Autowired
	private StompSessionService stompSessionService;

	@Autowired
	private TermRecommendService termRecommendService;
	
	//@ResponseBody
    @PostMapping(value = "/uploadWords", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Response> uploadWords(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
            @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestParam("file") MultipartFile multiPart) {
    	Response result = new Response();
    	
    	log.info(">> uploadWords started : {}", multiPart.getOriginalFilename());
    	
    	try {
			startService(new DataStandardService("uploadWords", userId, socketSessionId, multiPart));
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			//stompSessionService.sendMessage(socketSessionId, WsNoticeLevel.INFO, ">> uploadWords failed : " + e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }
    
	//@ResponseBody
    @PostMapping(value = "/uploadTermsList", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Response> uploadTermsList(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
            @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestParam("file") MultipartFile multiPart) {
    	Response result = new Response();
    	
    	log.info(">> uploadTermsList started : {}", multiPart.getOriginalFilename());
    	
    	try {
			startService(new DataStandardService("uploadTermsList", userId, socketSessionId, multiPart));
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }
 
    @PostMapping(value = "/uploadCodeInfoList", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Response> uploadCodeInfoList(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
            @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestParam("file") MultipartFile multiPart) {
    	Response result = new Response();
    	
    	log.info(">> uploadCodeInfoList started : {}", multiPart.getOriginalFilename());
    	
    	try {
			startService(new DataStandardService("uploadCodeInfoList", userId, socketSessionId, multiPart));
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			//stompSessionService.sendMessage(socketSessionId, WsNoticeLevel.INFO, ">> uploadCodeInfoList failed : " + e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }

    @PostMapping(value = "/uploadCodeDataList", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Response> uploadCodeDataList(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
            @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestParam("file") MultipartFile multiPart) {
    	Response result = new Response();
    	
    	log.info(">> uploadCodeDataList started : {}", multiPart.getOriginalFilename());
    	
    	try {
			startService(new DataStandardService("uploadCodeDataList", userId, socketSessionId, multiPart));
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			//stompSessionService.sendMessage(socketSessionId, WsNoticeLevel.INFO, ">> uploadCodeDataList failed : " + e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }

	//@ResponseBody
    @PostMapping(value = "/uploadDomains", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Response> uploadDomains(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
                     @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestParam("file") MultipartFile multiPart) {
    	Response result = new Response();
    	
    	log.info(">> uploadDomains started : {}", multiPart.getOriginalFilename());
    	
    	try {
			startService(new DataStandardService("uploadDomains", userId, socketSessionId, multiPart));
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			//stompSessionService.sendMessage(socketSessionId, WsNoticeLevel.INFO, ">> uploadDomains failed : " + e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }

    // 표준화 추천 - 용어 분석
    @PostMapping(value = "/analyzeTermsBatch")
    public List<TermAnalysisResult> analyzeTermsBatch(@RequestBody Map<String, List<String>> request) {
        List<String> termNames = request.get("termNames");
        if (termNames == null) termNames = new ArrayList<>();
        log.info(">> analyzeTermsBatch: {} items", termNames.size());
        return termRecommendService.analyze(termNames);
    }
}
