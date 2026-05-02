package qualityexecutor.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.BusinessRuleService;
import qualityexecutor.service.std.ValueProfileService;
import reactor.core.publisher.Mono;

/**
 * 데이터 품질 진단 — q-executor 트리거 엔드포인트 (q-center 의 /run 이 호출)
 *
 * <p>패턴: 즉시 200 반환, 실제 진단은 별도 Thread 에서 수행</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/qual")
public class QualDiagExecutorController {

    @Autowired
    private ApplicationContext appContext;

    private static final Gson GSON = new Gson();

    /** targets JSON ([{"objNm":..., "attrNm":...}, ...]) → "OBJ.ATTR" Set */
    private java.util.Set<String> parseTargets(String json) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (json == null || json.isEmpty()) return set;
        try {
            java.lang.reflect.Type type =
                new TypeToken<java.util.List<java.util.Map<String, String>>>(){}.getType();
            java.util.List<java.util.Map<String, String>> list = GSON.fromJson(json, type);
            if (list != null) {
                for (java.util.Map<String, String> m : list) {
                    String o = m.get("objNm");
                    String a = m.get("attrNm");
                    if (o != null && a != null) set.add(o + "." + a);
                }
            }
        } catch (Exception e) {
            log.warn(">> parseTargets parse fail: {}", e.getMessage());
        }
        return set;
    }

    /** 값 프로파일링 시작 */
    @PostMapping("/value/run")
    public Mono<Response> valueRun(@RequestBody Map<String, String> p) {
        Response res = new Response();
        try {
            String diagId = p.get("diagId");
            String dmId   = p.get("dataModelId");
            if (diagId == null || dmId == null) throw new IllegalArgumentException("diagId/dataModelId 필수");
            Integer sampleRate = p.get("sampleRate") != null ? Integer.valueOf(p.get("sampleRate")) : 100;
            String userId = p.getOrDefault("userId", "MANUAL");
            String objNm  = p.get("objNm");

            String attrNm = p.get("attrNm");
            java.util.Set<String> targetKeys = parseTargets(p.get("targets"));
            ValueProfileService svc = new ValueProfileService(diagId, dmId, userId, sampleRate, objNm, attrNm, targetKeys);
            appContext.getAutowireCapableBeanFactory().autowireBean(svc);
            Thread t = new Thread(svc, "ValueProfile-" + diagId.substring(0, Math.min(8, diagId.length())));
            t.setDaemon(true);
            t.start();

            res.setContents(diagId);
            res.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> value run failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(res);
    }

    /** 업무 규칙 진단 시작 */
    @PostMapping("/rule/run")
    public Mono<Response> ruleRun(@RequestBody Map<String, String> p) {
        Response res = new Response();
        try {
            String diagId = p.get("diagId");
            String dmId   = p.get("dataModelId");
            if (diagId == null || dmId == null) throw new IllegalArgumentException("diagId/dataModelId 필수");
            Integer sampleRate = p.get("sampleRate") != null ? Integer.valueOf(p.get("sampleRate")) : 100;
            String incrementalYn = p.getOrDefault("incrementalYn", "N");
            String userId = p.getOrDefault("userId", "MANUAL");

            String objNm  = p.get("objNm");
            String attrNm = p.get("attrNm");
            java.util.Set<String> scopeKeys = parseTargets(p.get("targets"));
            BusinessRuleService svc = new BusinessRuleService(diagId, dmId, userId, sampleRate, incrementalYn, objNm, attrNm, scopeKeys);
            appContext.getAutowireCapableBeanFactory().autowireBean(svc);
            Thread t = new Thread(svc, "BizRule-" + diagId.substring(0, Math.min(8, diagId.length())));
            t.setDaemon(true);
            t.start();

            res.setContents(diagId);
            res.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> rule run failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(res);
    }
}
