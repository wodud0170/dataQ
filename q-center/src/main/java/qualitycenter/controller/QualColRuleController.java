package qualitycenter.controller;

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

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.model.std.QualColRuleVo;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 70번 §2.2 컬럼 → 적용 규칙 매핑
 *
 * - GET /list?dmId=&objNm=&attrNm= : effective rule (DOMAIN/CUSTOM/DEFAULT/EXCLUDED) 표기
 * - POST /save : 컬럼에 도메인 룰 또는 커스텀 룰 매핑 (사용자 셀프-서비스)
 * - POST /exclude : 진단 제외 토글
 */
@Slf4j
@RestController
@RequestMapping("/api/qual/colrule")
public class QualColRuleController {

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private SessionService session;

    @GetMapping("/list")
    public List<QualColRuleVo> list(
            @RequestParam String dmId,
            @RequestParam(required = false) String objNm,
            @RequestParam(required = false) String attrNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("dmId", dmId);
        p.put("objNm", objNm);
        p.put("attrNm", attrNm);
        return sql.selectList("qualColRule.selectEffectiveRulesByModel", p);
    }

    /**
     * DSQualValueProfile / DSQualColRule 화면용 — effective + 직전 값/룰 결과 join.
     * 83번 §5-3 + §5-4 — 도메인 분류 + 컬럼 검색 + 적합률 범위 필터.
     */
    @GetMapping("/listWithLatest")
    public List<Map<String, Object>> listWithLatest(
            @RequestParam String dmId,
            @RequestParam(required = false) String objNm,
            @RequestParam(required = false) String attrNm,
            @RequestParam(required = false) String domainClsfNm,
            @RequestParam(required = false) Double rateMin,
            @RequestParam(required = false) Double rateMax) {
        Map<String, Object> p = new HashMap<>();
        p.put("dmId", dmId);
        p.put("objNm", objNm);
        p.put("attrNm", attrNm);
        p.put("domainClsfNm", domainClsfNm);
        p.put("rateMin", rateMin);
        p.put("rateMax", rateMax);
        return sql.selectList("qualColRule.selectEffectiveWithLatest", p);
    }

    /**
     * 83번 §5-3 — drawer 미리보기용 위반 샘플 5건.
     * GET /detail?dmId=&objNm=&attrNm=
     */
    @GetMapping("/detail")
    public Map<String, Object> detail(
            @RequestParam String dmId,
            @RequestParam String objNm,
            @RequestParam String attrNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("dmId", dmId);
        p.put("objNm", objNm);
        p.put("attrNm", attrNm);
        Map<String, Object> result = new HashMap<>();
        result.put("violationSamples",
                sql.selectList("qualColRule.selectViolationSamplePreview", p));
        result.put("ruleResults",
                sql.selectList("qualColRule.selectRuleResultsByColumn", p));
        return result;
    }

    @PostMapping("/save")
    public Response save(@RequestBody QualColRuleVo vo) {
        Response res = new Response();
        try {
            if (vo.getDmId() == null || vo.getObjNm() == null || vo.getAttrNm() == null)
                throw new IllegalArgumentException("dmId/objNm/attrNm 필수");
            // 도메인 룰 OR 커스텀 룰 — 둘 다 채우면 도메인 우선
            if (vo.getDomainRuleId() != null && vo.getCustomRuleId() != null) {
                vo.setCustomRuleId(null);
            }
            vo.setObjOwner(resolveOwner(vo.getDmId(), vo.getObjNm(), vo.getAttrNm(), vo.getObjOwner()));
            vo.setUpdtUserId(session.getUserId());
            sql.insert("qualColRule.upsertColRule", vo);
            res.setResultInfo(RestResult.CODE_200.getCode(), "저장 완료");
        } catch (IllegalArgumentException e) { res.setResultInfo(400, e.getMessage()); }
        catch (Exception e) {
            log.error(">> colrule save failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/exclude")
    public Response exclude(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            QualColRuleVo vo = new QualColRuleVo();
            vo.setDmId(body.get("dmId"));
            vo.setObjNm(body.get("objNm"));
            vo.setAttrNm(body.get("attrNm"));
            vo.setObjOwner(resolveOwner(vo.getDmId(), vo.getObjNm(), vo.getAttrNm(),
                                        body.get("objOwner")));
            vo.setExcludeYn("Y".equals(body.get("excludeYn")) ? "Y" : "N");
            vo.setUpdtUserId(session.getUserId());
            sql.insert("qualColRule.upsertColRule", vo);
            res.setResultInfo(RestResult.CODE_200.getCode(), "OK");
        } catch (IllegalArgumentException e) { res.setResultInfo(400, e.getMessage()); }
        catch (Exception e) {
            log.error(">> colrule exclude failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    /**
     * 컬럼 룰의 식별 키는 (모델, 소유자, 테이블, 컬럼) 이다.
     * 호출부가 소유자를 안 보내면 모델에서 찾아 채운다.
     *
     * 소유자를 그냥 '' 로 두면 조회 조인(col_meta.OBJ_OWNER)과 어긋나 저장은 됐는데
     * 화면에 반영이 안 되는 상태가 된다. 여러 스키마에 같은 이름이 있으면 어느 쪽인지
     * 알 수 없으므로 임의로 고르지 않고 거부한다.
     */
    private String resolveOwner(String dmId, String objNm, String attrNm, String given) {
        if (given != null && !given.trim().isEmpty()) return given.trim();
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("dmId", dmId);
        p.put("objNm", objNm);
        p.put("attrNm", attrNm);
        java.util.List<String> owners = sql.selectList("qualColRule.selectOwnersOfAttr", p);
        if (owners == null || owners.isEmpty()) return "";
        if (owners.size() > 1) {
            throw new IllegalArgumentException(
                "같은 이름의 컬럼이 여러 스키마에 있습니다 (" + String.join(", ", owners)
                + "). objOwner 를 함께 보내주세요.");
        }
        return owners.get(0);
    }
}
