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

import com.ndata.common.handler.WebClientHandler;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.model.std.QualDiagHistoryVo;
import com.ndata.quality.model.std.QualProfileResultVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 데이터 품질 진단 — 값 프로파일링 API (67번 §3)
 */
@Tag(name = "QualValue", description = "데이터 품질 진단 — 값 프로파일링")
@Slf4j
@RestController
@RequestMapping("/api/qual/value")
public class QualValueController {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/run")
    public Response run(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return runScoped(request, body, (String) body.get("objNm"), null);
    }

    @PostMapping("/runTable")
    public Response runTable(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return runScoped(request, body, (String) body.get("objNm"), null);
    }

    @PostMapping("/runColumn")
    public Response runColumn(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return runScoped(request, body, (String) body.get("objNm"), (String) body.get("attrNm"));
    }

    /** 다중 컬럼 단일 진단 — body.targets = [{objNm, attrNm}, ...] */
    @PostMapping("/runColumns")
    public Response runColumns(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return runWithTargets(request, body);
    }

    private Response runWithTargets(HttpServletRequest request, Map<String, Object> body) {
        Response res = new Response();
        try {
            String dmId = (String) body.get("dataModelId");
            if (dmId == null) throw new IllegalArgumentException("dataModelId 필수");
            Object targets = body.get("targets");
            if (!(targets instanceof java.util.List) || ((java.util.List<?>) targets).isEmpty())
                throw new IllegalArgumentException("targets (컬럼 목록) 필수");

            QualDiagHistoryVo h = new QualDiagHistoryVo();
            h.setDiagId(StringUtils.getUUID().replace("-", ""));
            h.setDmId(dmId);
            h.setDiagType("VALUE");
            h.setStatus("READY");
            h.setSampleRate(body.get("sampleRate") != null
                    ? Integer.valueOf(body.get("sampleRate").toString()) : 100);
            h.setExecUserId(sessionService.getUserId());
            String targetsJson = new com.google.gson.Gson().toJson(targets);
            h.setTargetObjList(targetsJson);
            sqlSessionTemplate.insert("qualDiag.insertHistory", h);

            Map<String, String> params = new HashMap<>();
            params.put("diagId",      h.getDiagId());
            params.put("dataModelId", dmId);
            params.put("sampleRate",  String.valueOf(h.getSampleRate()));
            params.put("targets",     targetsJson);
            try {
                WebClientHandler webClient = new WebClientHandler(
                        NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/qual/value/run");
                webClient.postData(
                        sessionService.getUserId(),
                        Objects.toString(request.getSession().getAttribute("SSID"), null),
                        params
                ).block();
            } catch (Exception we) {
                Map<String, Object> p = new HashMap<>();
                p.put("diagId",   h.getDiagId());
                p.put("status",   "ERROR");
                p.put("errorMsg", "[CONFIG] q-executor 호출 실패: " + we.getMessage());
                sqlSessionTemplate.update("qualDiag.updateHistoryStatus", p);
                throw we;
            }

            res.setContents(h.getDiagId());
            int n = ((java.util.List<?>) targets).size();
            res.setResultInfo(RestResult.CODE_200.getCode(), n + "개 컬럼 값 프로파일링 실행 요청");
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> value runColumns failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    private Response runScoped(HttpServletRequest request, Map<String, Object> body,
                               String objNm, String attrNm) {
        Response res = new Response();
        try {
            String dmId = (String) body.get("dataModelId");
            if (dmId == null) throw new IllegalArgumentException("dataModelId 필수");

            QualDiagHistoryVo h = new QualDiagHistoryVo();
            h.setDiagId(StringUtils.getUUID().replace("-", ""));
            h.setDmId(dmId);
            h.setDiagType("VALUE");
            h.setStatus("READY");
            h.setSampleRate(body.get("sampleRate") != null
                    ? Integer.valueOf(body.get("sampleRate").toString()) : 100);
            h.setExecUserId(sessionService.getUserId());
            if (objNm != null) {
                String scope = attrNm != null ? "[\"" + objNm + "." + attrNm + "\"]" : "[\"" + objNm + "\"]";
                h.setTargetObjList(scope);
            }
            sqlSessionTemplate.insert("qualDiag.insertHistory", h);

            Map<String, String> params = new HashMap<>();
            params.put("diagId",      h.getDiagId());
            params.put("dataModelId", dmId);
            params.put("sampleRate",  String.valueOf(h.getSampleRate()));
            if (objNm  != null) params.put("objNm",  objNm);
            if (attrNm != null) params.put("attrNm", attrNm);
            try {
                WebClientHandler webClient = new WebClientHandler(
                        NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/qual/value/run");
                webClient.postData(
                        sessionService.getUserId(),
                        Objects.toString(request.getSession().getAttribute("SSID"), null),
                        params
                ).block();
            } catch (Exception we) {
                Map<String, Object> p = new HashMap<>();
                p.put("diagId",   h.getDiagId());
                p.put("status",   "ERROR");
                p.put("errorMsg", "[CONFIG] q-executor 호출 실패: " + we.getMessage());
                sqlSessionTemplate.update("qualDiag.updateHistoryStatus", p);
                throw we;
            }

            res.setContents(h.getDiagId());
            String unit = attrNm != null ? "컬럼" : (objNm != null ? "테이블" : "모델");
            res.setResultInfo(RestResult.CODE_200.getCode(), unit + " 단위 값 프로파일링 실행 요청");
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> value run failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @GetMapping("/result")
    public List<QualProfileResultVo> result(
            @RequestParam String dataModelId,
            @RequestParam(required = false) String objNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("dmId",  dataModelId);
        p.put("objNm", objNm);
        return sqlSessionTemplate.selectList("qualDiag.selectProfileResults", p);
    }

    @GetMapping("/history/{diagId}")
    public QualDiagHistoryVo historyDetail(
            @org.springframework.web.bind.annotation.PathVariable String diagId) {
        return sqlSessionTemplate.selectOne("qualDiag.selectHistoryById", diagId);
    }
}
