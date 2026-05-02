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
import com.ndata.quality.model.std.QualRuleCatalogVo;
import com.ndata.quality.model.std.QualRuleResultVo;
import com.ndata.quality.model.std.QualRuleVo;
import com.ndata.quality.model.std.QualViolationSampleVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 데이터 품질 진단 — 업무 규칙 룰 관리 + 진단 실행 API (67번 §6)
 */
@Tag(name = "QualRule", description = "데이터 품질 진단 — 업무 규칙")
@Slf4j
@RestController
@RequestMapping("/api/qual/rule")
public class QualRuleController {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private SessionService sessionService;

    // ==================== 룰 CRUD ====================

    @PostMapping("/list")
    public List<QualRuleVo> list(@RequestBody Map<String, Object> body) {
        return sqlSessionTemplate.selectList("qualRule.selectRules", body);
    }

    @GetMapping("/{ruleId}")
    public QualRuleVo detail(@RequestParam String ruleId) {
        return sqlSessionTemplate.selectOne("qualRule.selectRuleById", ruleId);
    }

    @PostMapping("/save")
    public Response save(@RequestBody QualRuleVo vo) {
        Response res = new Response();
        try {
            assertAdmin();
            validate(vo);
            if (vo.getRuleId() == null || vo.getRuleId().isEmpty()) {
                vo.setRuleId(StringUtils.getUUID());
                vo.setCretUserId(sessionService.getUserId());
                if (vo.getEstCost() == null) vo.setEstCost(estimateCost(vo.getRuleType()));
                sqlSessionTemplate.insert("qualRule.insertRule", vo);
            } else {
                vo.setUpdtUserId(sessionService.getUserId());
                if (vo.getEstCost() == null) vo.setEstCost(estimateCost(vo.getRuleType()));
                sqlSessionTemplate.update("qualRule.updateRule", vo);
            }
            res.setContents(vo.getRuleId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "저장 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> rule save failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/delete")
    public Response delete(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String ruleId = body.get("ruleId");
            if (ruleId == null) throw new IllegalArgumentException("ruleId 필수");
            Map<String, Object> p = new HashMap<>();
            p.put("ruleId", ruleId);
            p.put("userId", sessionService.getUserId());
            sqlSessionTemplate.update("qualRule.softDeleteRule", p);
            res.setResultInfo(RestResult.CODE_200.getCode(), "삭제 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> rule delete failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    // ==================== 룰 진단 실행 ====================

    @PostMapping("/run")
    public Response run(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return runScoped(request, body, null, null);
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
            h.setDiagType("RULE");
            h.setStatus("READY");
            h.setSampleRate(body.get("sampleRate") != null
                    ? Integer.valueOf(body.get("sampleRate").toString()) : 100);
            h.setIncrementalYn(Objects.equals(body.get("incrementalYn"), "Y") ? "Y" : "N");
            h.setExecUserId(sessionService.getUserId());
            String targetsJson = new com.google.gson.Gson().toJson(targets);
            h.setTargetObjList(targetsJson);
            sqlSessionTemplate.insert("qualDiag.insertHistory", h);

            Map<String, String> params = new HashMap<>();
            params.put("diagId",        h.getDiagId());
            params.put("dataModelId",   dmId);
            params.put("sampleRate",    String.valueOf(h.getSampleRate()));
            params.put("incrementalYn", h.getIncrementalYn());
            params.put("targets",       targetsJson);
            try {
                WebClientHandler webClient = new WebClientHandler(
                        NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/qual/rule/run");
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
            res.setResultInfo(RestResult.CODE_200.getCode(), n + "개 컬럼 룰 진단 실행 요청");
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> rule runColumns failed", e);
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
            h.setDiagType("RULE");
            h.setStatus("READY");
            h.setSampleRate(body.get("sampleRate") != null
                    ? Integer.valueOf(body.get("sampleRate").toString()) : 100);
            h.setIncrementalYn(Objects.equals(body.get("incrementalYn"), "Y") ? "Y" : "N");
            h.setExecUserId(sessionService.getUserId());
            // 진단 단위 표시
            StringBuilder scope = new StringBuilder("[\"");
            if (objNm  != null) scope.append(objNm);
            if (attrNm != null) scope.append(".").append(attrNm);
            scope.append("\"]");
            h.setTargetObjList(objNm == null ? null : scope.toString());
            sqlSessionTemplate.insert("qualDiag.insertHistory", h);

            Map<String, String> params = new HashMap<>();
            params.put("diagId",        h.getDiagId());
            params.put("dataModelId",   dmId);
            params.put("sampleRate",    String.valueOf(h.getSampleRate()));
            params.put("incrementalYn", h.getIncrementalYn());
            if (objNm  != null) params.put("objNm",  objNm);
            if (attrNm != null) params.put("attrNm", attrNm);
            try {
                WebClientHandler webClient = new WebClientHandler(
                        NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/qual/rule/run");
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
            res.setResultInfo(RestResult.CODE_200.getCode(), unit + " 단위 룰 진단 실행 요청");
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> rule run failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @GetMapping("/result")
    public Response result(@RequestParam String diagId) {
        Response res = new Response();
        try {
            QualDiagHistoryVo h = sqlSessionTemplate.selectOne("qualDiag.selectHistoryById", diagId);
            Map<String, Object> p = new HashMap<>();
            p.put("diagId", diagId);
            List<QualRuleResultVo> results = sqlSessionTemplate.selectList(
                    "qualDiag.selectRuleResults", p);
            Map<String, Object> contents = new HashMap<>();
            contents.put("history", h);
            contents.put("results", results);
            res.setContents(new com.google.gson.Gson().toJson(contents));
            res.setResultInfo(RestResult.CODE_200.getCode(), "OK");
        } catch (Exception e) {
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @GetMapping("/violationSample")
    public List<QualViolationSampleVo> violationSample(
            @RequestParam String diagId, @RequestParam String ruleId) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("ruleId", ruleId);
        return sqlSessionTemplate.selectList("qualDiag.selectViolationSamples", p);
    }

    // ==================== 카탈로그 ====================

    @GetMapping("/catalog")
    public List<QualRuleCatalogVo> catalog() {
        return sqlSessionTemplate.selectList("qualRule.selectCatalog");
    }

    @PostMapping("/importFromCatalog")
    public Response importFromCatalog(@RequestBody Map<String, Object> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String dmId      = (String) body.get("dataModelId");
            String catalogId = (String) body.get("catalogId");
            String objNm     = (String) body.get("objNm");
            String attrNm    = (String) body.get("attrNm");
            if (dmId == null || catalogId == null) throw new IllegalArgumentException("dataModelId, catalogId 필수");

            // selectCatalogById 매퍼 없음 — list 에서 필터
            List<QualRuleCatalogVo> all = sqlSessionTemplate.selectList("qualRule.selectCatalog");
            QualRuleCatalogVo cat = all.stream()
                    .filter(c -> catalogId.equals(c.getCatalogId()))
                    .findFirst().orElse(null);
            if (cat == null) throw new IllegalArgumentException("카탈로그 없음");

            QualRuleVo vo = new QualRuleVo();
            vo.setRuleId(StringUtils.getUUID());
            vo.setDmId(dmId);
            vo.setObjNm(objNm);
            vo.setAttrNm(attrNm);
            vo.setRuleNm(cat.getCatalogNm());
            vo.setRuleType(cat.getRuleType());
            vo.setRuleParams(cat.getRuleParams());
            vo.setSeverity("WARN");
            vo.setUseYn("Y");
            vo.setEstCost(estimateCost(cat.getRuleType()));
            vo.setCretUserId(sessionService.getUserId());
            sqlSessionTemplate.insert("qualRule.insertRule", vo);
            res.setContents(vo.getRuleId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "카탈로그에서 룰 등록 완료");
        } catch (IllegalAccessException e) {
            res.setResultInfo(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            res.setResultInfo(400, e.getMessage());
        } catch (Exception e) {
            log.error(">> importFromCatalog failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    // ==================== 유틸 ====================

    private void assertAdmin() throws IllegalAccessException {
        if (!sessionService.isAdmin()) {
            throw new IllegalAccessException("관리자 권한 필요");
        }
    }

    private void validate(QualRuleVo vo) {
        if (vo.getDmId() == null)        throw new IllegalArgumentException("dmId 필수");
        if (vo.getRuleNm() == null)      throw new IllegalArgumentException("ruleNm 필수");
        if (vo.getRuleType() == null)    throw new IllegalArgumentException("ruleType 필수");
        if (vo.getObjNm() == null && vo.getDomainId() == null)
            throw new IllegalArgumentException("objNm 또는 domainId 중 하나 필수");
    }

    private String estimateCost(String ruleType) {
        if (ruleType == null) return "MID";
        switch (ruleType) {
            case "NOT_NULL":
            case "ENUM":
                return "LOW";
            case "REGEX":
            case "EXPRESSION":
                return "HIGH";
            default:
                return "MID";
        }
    }
}
