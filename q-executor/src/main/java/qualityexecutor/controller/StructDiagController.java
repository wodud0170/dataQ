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

        log.info(">> runStructDiag: diagId={}, dataModelId={}", diagId, dataModelId);
        try {
            startService(new StructDiagService(diagId, dataModelId, userId));
            result.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> runStructDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(result);
    }
}
