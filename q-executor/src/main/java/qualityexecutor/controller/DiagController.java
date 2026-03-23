package qualityexecutor.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.DiagService;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/diag")
public class DiagController extends DataControllerBase {

    /** 진단 실행 (백그라운드) */
    @PostMapping("/runDiag")
    public Mono<Response> runDiag(@RequestBody Map<String, String> params) {
        Response result = new Response();
        String diagJobId    = params.get("diagJobId");
        String clctId       = params.get("clctId");
        String dataModelId  = params.get("dataModelId");
        String userId       = params.get("userId");

        log.info(">> runDiag: jobId={}, clctId={}", diagJobId, clctId);
        try {
            startService(new DiagService(diagJobId, clctId, dataModelId, userId));
            result.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> runDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(result);
    }

    /** 진단 중지 */
    @PostMapping("/stopDiag")
    public Mono<Response> stopDiag(@RequestBody Map<String, String> params) {
        Response result = new Response();
        String diagJobId = params.get("diagJobId");
        log.info(">> stopDiag: jobId={}", diagJobId);
        try {
            DiagService.requestStop(diagJobId);
            result.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> stopDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(result);
    }
}
