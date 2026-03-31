package qualityexecutor.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.StructDiagService;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/structDiag")
public class StructDiagController extends DataControllerBase {

    /** 구조 진단 실행 (백그라운드) */
    @PostMapping("/run")
    public Mono<Response> runStructDiag(@RequestBody Map<String, String> params) {
        Response result = new Response();
        String diagId       = params.get("diagId");
        String dataModelId  = params.get("dataModelId");
        String userId       = params.get("userId");
        String clctId       = params.get("clctId"); // 선택적

        log.info(">> runStructDiag: diagId={}, dataModelId={}, clctId={}", diagId, dataModelId, clctId);
        try {
            startService(new StructDiagService(diagId, dataModelId, userId, clctId));
            result.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> runStructDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(result);
    }

    /** 스키마 비교 (동기 실행, 결과 직접 반환) */
    @PostMapping("/compareSchema")
    public Mono<Response> compareSchema(@RequestBody Map<String, String> params) {
        Response result = new Response();
        String dataModelId = params.get("dataModelId");
        String clctId      = params.get("clctId");

        log.info(">> compareSchema: dataModelId={}, clctId={}", dataModelId, clctId);
        try {
            StructDiagService svc = new StructDiagService(null, dataModelId, null, clctId);
            runService(svc);
            Map<String, Object> compareResult = svc.compareSchema(dataModelId, clctId);
            if (compareResult.containsKey("error")) {
                result.setResultInfo(RestResult.CODE_500.getCode(), (String) compareResult.get("error"));
            } else {
                result.setResultInfo(RestResult.CODE_200);
                result.setContents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(compareResult));
            }
        } catch (Exception e) {
            log.error(">> compareSchema failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(result);
    }
}
