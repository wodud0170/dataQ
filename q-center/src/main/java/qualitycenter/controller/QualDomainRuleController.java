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
import com.ndata.module.StringUtils;
import com.ndata.quality.model.std.DomainRuleVo;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 70번 §2.1 도메인별 검증 규칙 1:N
 */
@Slf4j
@RestController
@RequestMapping("/api/qual/domain")
public class QualDomainRuleController {

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private SessionService session;

    @GetMapping("/rules")
    public List<DomainRuleVo> rulesByDomain(@RequestParam String domainId) {
        return sql.selectList("qualDomainRule.selectByDomain", domainId);
    }

    /**
     * 83번 §5-1 — 도메인 트리 (분류 → 도메인) + 룰 카운트.
     * 좌측 트리 패널 데이터.
     * GET /api/qual/domain/tree?schNm=...
     */
    @GetMapping("/tree")
    public List<Map<String, Object>> tree(
            @RequestParam(value="schNm", required=false) String schNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("schNm", schNm);
        return sql.selectList("qualDomainRule.selectDomainTreeWithRuleCount", p);
    }

    /**
     * 카탈로그에서 도메인 룰로 매핑 (시스템/사용자 카탈로그 → TB_DOMAIN_RULE 신규 row).
     * 시스템 기본은 fork 없이 직접 매핑 가능 (TB_DOMAIN_RULE 자체는 사용자 정의 영역).
     */
    @PostMapping("/rule/importFromCatalog")
    public Response importFromCatalog(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String domainId  = body.get("domainId");
            String catalogId = body.get("catalogId");
            if (domainId == null || catalogId == null) {
                throw new IllegalArgumentException("domainId, catalogId 필수");
            }
            // 카탈로그 row 조회
            com.ndata.quality.model.std.QualRuleCatalogVo cat =
                    sql.selectOne("qualRule.selectCatalogById", catalogId);
            if (cat == null) throw new IllegalArgumentException("카탈로그 없음: " + catalogId);

            DomainRuleVo vo = new DomainRuleVo();
            vo.setDomainRuleId(StringUtils.getUUID());
            vo.setDomainId(domainId);
            vo.setRuleNm(cat.getCatalogNm());
            vo.setRuleType(cat.getRuleType());
            vo.setRuleParams(cat.getRuleParams());
            vo.setSortOrd(1);
            vo.setUseYn("Y");
            vo.setDescr(cat.getDescr());
            vo.setCretUserId(session.getUserId());
            sql.insert("qualDomainRule.insertRule", vo);
            res.setContents(vo.getDomainRuleId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "매핑 완료");
        } catch (IllegalAccessException e)  { res.setResultInfo(403, e.getMessage()); }
        catch (IllegalArgumentException e)  { res.setResultInfo(400, e.getMessage()); }
        catch (Exception e) {
            log.error(">> import from catalog failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/rule/save")
    public Response save(@RequestBody DomainRuleVo vo) {
        Response res = new Response();
        try {
            assertAdmin();
            if (vo.getDomainId() == null) throw new IllegalArgumentException("domainId 필수");
            if (vo.getRuleNm() == null)   throw new IllegalArgumentException("ruleNm 필수");
            if (vo.getRuleType() == null) throw new IllegalArgumentException("ruleType 필수");
            if (vo.getDomainRuleId() == null || vo.getDomainRuleId().isEmpty()) {
                vo.setDomainRuleId(StringUtils.getUUID());
                vo.setCretUserId(session.getUserId());
                if (vo.getSortOrd() == null) vo.setSortOrd(1);
                if (vo.getUseYn() == null)   vo.setUseYn("Y");
                sql.insert("qualDomainRule.insertRule", vo);
            } else {
                vo.setUpdtUserId(session.getUserId());
                sql.update("qualDomainRule.updateRule", vo);
            }
            res.setContents(vo.getDomainRuleId());
            res.setResultInfo(RestResult.CODE_200.getCode(), "저장 완료");
        } catch (IllegalAccessException e)  { res.setResultInfo(403, e.getMessage()); }
        catch (IllegalArgumentException e)  { res.setResultInfo(400, e.getMessage()); }
        catch (Exception e) {
            log.error(">> domain rule save failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    @PostMapping("/rule/delete")
    public Response delete(@RequestBody Map<String, String> body) {
        Response res = new Response();
        try {
            assertAdmin();
            String id = body.get("domainRuleId");
            if (id == null) throw new IllegalArgumentException("domainRuleId 필수");
            sql.delete("qualDomainRule.deleteRule", id);
            res.setResultInfo(RestResult.CODE_200.getCode(), "삭제 완료");
        } catch (IllegalAccessException e)  { res.setResultInfo(403, e.getMessage()); }
        catch (IllegalArgumentException e)  { res.setResultInfo(400, e.getMessage()); }
        catch (Exception e) {
            log.error(">> domain rule delete failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return res;
    }

    private void assertAdmin() throws IllegalAccessException {
        if (!session.isAdmin()) throw new IllegalAccessException("관리자 권한 필요");
    }
}
