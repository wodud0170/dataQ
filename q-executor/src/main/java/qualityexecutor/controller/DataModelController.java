package qualityexecutor.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.messaging.common.CustomHeaders;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.DataModelService;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/dm")
public class DataModelController extends DataControllerBase {

    @PostMapping(value = "/collectDataModel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Response> collectDataModel(@RequestHeader(value=CustomHeaders.HEADER_USERNAME, required=false) String userId,
                     @RequestHeader(value=CustomHeaders.HEADER_SCOKET_SESSION_ID, required=false) String socketSessionId,
                     @RequestBody StdDataModelVo dataVo) {
    	Response result = new Response();
    	
    	log.info(">> collectDataModel started : {}", dataVo);
    	
    	try {
			startService(new DataModelService("collectDataModel", userId, socketSessionId, dataVo));
	    	// set result message
	    	result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			//stompSessionService.sendMessage(socketSessionId, WsNoticeLevel.INFO, ">> uploadDomains failed : " + e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
    	
    	return Mono.just(result);
    }
}
