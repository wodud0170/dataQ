package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.bean.SecurityManager;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.model.std.StdDiagJobVo;
import com.ndata.quality.model.std.StdDiagResultVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 표준화 진단 컨트롤러 (용어/도메인 적합성 진단)
 *
 * <p>수집된 데이터모델의 컬럼을 표준 용어/도메인과 비교하여
 * 용어 미존재, 한글명 불일치, 데이터 타입/길이 불일치를 진단한다.
 * 진단 실행은 q-executor의 DiagService에서 비동기로 처리.</p>
 */
@Tag(name = "데이터표준화진단", description = "데이터 표준화 진단 API")
@Slf4j
@RestController
@RequestMapping("/api/diag")
public class DiagController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    SecurityManager securityUtils;

    /** 진단 Job 목록 조회 */
    @PostMapping("/getDiagJobList")
    public List<StdDiagJobVo> getDiagJobList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
        return sqlSessionTemplate.selectList("diag.selectDiagJobList", retCond);
    }

    /** 진단 Job 단건 조회 (폴링용) */
    @GetMapping("/getDiagJobById")
    public StdDiagJobVo getDiagJobById(@RequestParam String diagJobId) {
        return sqlSessionTemplate.selectOne("diag.selectDiagJobById", diagJobId);
    }

    /** 진단 결과 목록 조회 (컬럼 상세) */
    @GetMapping("/getDiagResultList")
    public List<StdDiagResultVo> getDiagResultList(@RequestParam String diagJobId) {
        return sqlSessionTemplate.selectList("diag.selectDiagResultList", diagJobId);
    }

    /** 진단 결과 요약 조회 (테이블 집계) */
    @GetMapping("/getDiagResultSummary")
    public List<Map<String, Object>> getDiagResultSummary(@RequestParam String diagJobId) {
        return sqlSessionTemplate.selectList("diag.selectDiagResultSummary", diagJobId);
    }

    /** 진단 결과 상세 조회 (컬럼 1행 기준, 용어/도메인 JOIN) */
    @GetMapping("/getDiagResultDetail")
    public List<Map<String, Object>> getDiagResultDetail(@RequestParam String diagJobId) {
        return sqlSessionTemplate.selectList("diag.selectDiagResultDetail", diagJobId);
    }

    /** 진단 시작: Job 생성 후 q-executor에 실행 요청 */
    @PostMapping("/startDiag")
    public Mono<Response> startDiag(HttpServletRequest request, @RequestBody StdDiagJobVo jobVo) {
        Response result = new Response();
        try {
            // Job 레코드 생성 (READY 상태)
            jobVo.setDiagJobId(StringUtils.getUUID());
            jobVo.setCretUserId(sessionService.getUserId());
            sqlSessionTemplate.insert("diag.insertDiagJob", jobVo);

            // q-executor에 실행 요청
            Map<String, String> params = new HashMap<>();
            params.put("diagJobId",   jobVo.getDiagJobId());
            params.put("clctId",      jobVo.getClctId());
            params.put("dataModelId", jobVo.getDataModelId());
            params.put("userId",      sessionService.getUserId());

            WebClientHandler webClientHandler = new WebClientHandler(
                    NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/runDiag");
            return webClientHandler.postData(
                    sessionService.getUserId(),
                    Objects.toString(request.getSession().getAttribute("SSID"), null),
                    params)
                .map(res -> {
                    res.setContents(jobVo.getDiagJobId());
                    return res;
                });
        } catch (Exception e) {
            log.error(">> startDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
            return Mono.just(result);
        }
    }

    /** 진단 중지: q-executor에 중지 요청 */
    @PostMapping("/stopDiag")
    public Mono<Response> stopDiag(HttpServletRequest request, @RequestBody Map<String, String> params) {
        WebClientHandler webClientHandler = new WebClientHandler(
                NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/stopDiag");
        return webClientHandler.postData(
                sessionService.getUserId(),
                Objects.toString(request.getSession().getAttribute("SSID"), null),
                params);
    }
}
