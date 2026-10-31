package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private com.ndata.quality.service.StdDictService dictService;

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

    /** 진단 결과 요약 조회 (테이블 집계) */
    @GetMapping("/getDiagResultSummary")
    public List<Map<String, Object>> getDiagResultSummary(@RequestParam String diagJobId) {
        return sqlSessionTemplate.selectList("diag.selectDiagResultSummary", diagJobId);
    }

    /**
     * 진단 결과 상세 조회 (컬럼 1행 기준, 용어/도메인 JOIN)
     *
     * <p>110 6.10-15 — 표준 용어·도메인은 이 진단을 잰 사전(Job 사전, 없으면 모델 사전)과 그 상위 사전에서만 찾는다.
     * 가까운 사전이 이긴다 (진단 executor 의 terms.selectAllTermsForDiag 와 같은 기준).</p>
     */
    @GetMapping("/getDiagResultDetail")
    public List<Map<String, Object>> getDiagResultDetail(@RequestParam String diagJobId) {
        StdDiagJobVo job = sqlSessionTemplate.selectOne("diag.selectDiagJobById", diagJobId);
        if (job == null) return new ArrayList<>();
        Map<String, Object> param = dictService.lineageParam(dictService.resolve(job.getDictId(), job.getDataModelId()));
        param.put("diagJobId", diagJobId);
        return sqlSessionTemplate.selectList("diag.selectDiagResultDetail", param);
    }

    /** 진단 시작: Job 생성 후 q-executor에 실행 요청 */
    @PostMapping("/startDiag")
    public Mono<Response> startDiag(@RequestBody StdDiagJobVo jobVo) {
        Response result = new Response();
        try {
            // 중복 Job 체크: 동일 데이터모델에 READY/RUNNING 상태 Job이 있으면 거부
            // 86번 #11 — CLCT 폐기로 dataModelId 기준으로 변경 (frontend 가 clctId 안 보냄)
            int activeCount = sqlSessionTemplate.selectOne("diag.selectActiveDiagJobCount", jobVo.getDataModelId());
            if (activeCount > 0) {
                result.setResultInfo(409, "이미 진행 중인 진단이 있습니다.");
                return Mono.just(result);
            }

            // 98번 — 이 진단을 어느 사전으로 잴지 확정한다.
            // 사용자가 화면에서 고른 값이 있으면 그것을, 없으면 모델에 저장된 사전을 쓴다.
            String diagDictId = dictService.resolve(jobVo.getDictId(), jobVo.getDataModelId());
            if (diagDictId == null) {
                result.setResultInfo(400,
                    "이 데이터 모델에 표준사전이 지정되어 있지 않습니다."
                    + " 진단에 사용할 표준사전을 선택하거나, 모델 수정에서 사전을 지정해 주세요.");
                return Mono.just(result);
            }
            jobVo.setDictId(diagDictId);

            // 110 6.10-20a — 진단 때 모델의 최근 완료 수집 (수집일시 열). 수집 이력 없는 논리 모델은 비어 '-'
            jobVo.setClctId(sqlSessionTemplate.selectOne("datamodel.selectLatestDoneClctIdByDmId", jobVo.getDataModelId()));

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
            params.put("dictId",      diagDictId);

            WebClientHandler webClientHandler = new WebClientHandler(
                    NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/runDiag");
            return webClientHandler.postData(sessionService.getUserId(), null, params)
                .map(res -> {
                    // 110 6.10-5b — executor 가 기동을 거부하면 Job 을 ERROR 로 (READY 로 남으면 다음 진단이 늘 409)
                    if (res.getResultCode() != RestResult.CODE_200.getCode()) {
                        markDiagJobError(jobVo.getDiagJobId());
                        return res;
                    }
                    res.setContents(jobVo.getDiagJobId());
                    return res;
                })
                .onErrorResume(e -> {
                    // executor 호출 실패 시 READY 좀비 방지: Job 상태를 ERROR로 변경
                    log.error(">> executor 호출 실패, Job을 ERROR로 변경: {}", jobVo.getDiagJobId(), e);
                    markDiagJobError(jobVo.getDiagJobId());
                    result.setResultInfo(RestResult.CODE_500.getCode(), "진단 서버 연결 실패: " + e.getMessage());
                    return Mono.just(result);
                });
        } catch (Exception e) {
            log.error(">> startDiag failed", e);
            result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
            return Mono.just(result);
        }
    }

    private void markDiagJobError(String diagJobId) {
        StdDiagJobVo errorJob = new StdDiagJobVo();
        errorJob.setDiagJobId(diagJobId);
        errorJob.setStatus("ERROR");
        sqlSessionTemplate.update("diag.updateDiagJobStatus", errorJob);
    }

    /** 진단 중지: q-executor에 중지 요청 */
    @PostMapping("/stopDiag")
    public Mono<Response> stopDiag(@RequestBody Map<String, String> params) {
        WebClientHandler webClientHandler = new WebClientHandler(
                NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/diag/stopDiag");
        return webClientHandler.postData(sessionService.getUserId(), null, params);
    }
}
