package qualityexecutor.service.std;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.model.DataSourceVo;
import com.ndata.quality.model.std.QualColRuleVo;
import com.ndata.quality.model.std.QualRuleResultVo;
import com.ndata.quality.model.std.QualRuleVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 70번 §3 업무 규칙 진단 — 컬럼별 effective rule 기반.
 *
 * 입력 단위:
 *   ATTR_NM 지정 → 그 컬럼 1개
 *   OBJ_NM 만 지정 → 그 테이블의 컬럼들
 *   둘 다 NULL → 모델 전체 컬럼
 *
 * effective rule 결정:
 *   매퍼 qualColRule.selectEffectiveRulesByModel 가 컬럼당 1행 반환
 *   effectiveSource = CUSTOM/DOMAIN/DEFAULT/EXCLUDED/NONE
 *
 * 결과 적재:
 *   TB_QUAL_RULE_RESULT INSERT (DIAG_ID 별)
 *   다른 컬럼/테이블 결과는 손대지 않음 (해당 컬럼만 갱신)
 */
@Slf4j
@NoArgsConstructor
public class BusinessRuleService implements Runnable {

    private String  diagId;
    private String  dataModelId;
    private String  userId;
    private Integer sampleRate;
    private String  incrementalYn;
    private String  scopeObjNm;     // null → 전체 테이블
    private String  scopeAttrNm;    // null → 전체 컬럼
    private java.util.Set<String> scopeKeys;  // "OBJ_NM.ATTR_NM" Set — 다중 컬럼

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private DataSourceUtils dataSourceUtils;

    private static final int TOTAL_TIMEOUT_SEC      = 1800;
    private static final int SQL_TIMEOUT_SEC        = 30;
    private static final double VIOLATION_RATE_BREAK = 0.90;

    public BusinessRuleService(String diagId, String dataModelId, String userId,
                               Integer sampleRate, String incrementalYn,
                               String scopeObjNm, String scopeAttrNm) {
        this(diagId, dataModelId, userId, sampleRate, incrementalYn, scopeObjNm, scopeAttrNm, null);
    }

    public BusinessRuleService(String diagId, String dataModelId, String userId,
                               Integer sampleRate, String incrementalYn,
                               String scopeObjNm, String scopeAttrNm,
                               java.util.Set<String> scopeKeys) {
        this.diagId       = diagId;
        this.dataModelId  = dataModelId;
        this.userId       = userId;
        this.sampleRate   = sampleRate;
        this.incrementalYn= incrementalYn;
        this.scopeObjNm   = scopeObjNm;
        this.scopeAttrNm  = scopeAttrNm;
        this.scopeKeys    = scopeKeys;
    }

    @Override
    public void run() {
        long startedAt = System.currentTimeMillis();
        log.info(">> BusinessRuleService start: diagId={} dmId={} obj={} attr={}",
                diagId, dataModelId, scopeObjNm, scopeAttrNm);
        DBHandler dbHandler = null;
        long totalViolations = 0;
        int  totalRules = 0;

        try {
            updateStatus("RUNNING", null);

            Map<String, Object> dmInfo = sql.selectOne("datamodel.selectDataModelById", dataModelId);
            if (dmInfo == null) throw new IllegalStateException("[DATA_NOT_FOUND] 모델 없음");
            String dsId = (String) dmInfo.get("dataModelDsId");
            if (dsId == null || dsId.trim().isEmpty()) {
                throw new IllegalStateException("[CONFIG] 논리 모델은 RULE 진단 대상이 아닙니다");
            }
            DataSourceVo dataSource = sql.selectOne("sysinfo.selectDataSourceById", dsId);
            String dbmsType = dataSource.getDbmsTp();
            dbHandler = dataSourceUtils.getDBHandler(dataSource);
            RuleSqlBuilder builder = new RuleSqlBuilder(dbmsType);

            // 컬럼별 effective rule 조회 (단일 SQL, JOIN 으로 결정)
            Map<String, Object> p = new HashMap<>();
            p.put("dmId",   dataModelId);
            p.put("objNm",  scopeObjNm);
            p.put("attrNm", scopeAttrNm);
            List<QualColRuleVo> effList = sql.selectList("qualColRule.selectEffectiveRulesByModel", p);
            log.info(">> effective rules: {} columns", effList.size());

            for (QualColRuleVo eff : effList) {
                if (System.currentTimeMillis() - startedAt > TOTAL_TIMEOUT_SEC * 1000L) {
                    throw new IllegalStateException("[TIMEOUT] 진단 30분 누적 초과");
                }

                String src = eff.getEffectiveSource();
                if ("EXCLUDED".equals(src) || "NONE".equals(src) || eff.getEffectiveRuleType() == null) {
                    continue;  // 진단 제외
                }
                if (scopeKeys != null && !scopeKeys.isEmpty()
                        && !scopeKeys.contains(eff.getObjNm() + "." + eff.getAttrNm())) {
                    continue;
                }

                // effective rule 을 QualRuleVo 로 wrap (RuleSqlBuilder 가 받는 형태)
                QualRuleVo rule = new QualRuleVo();
                rule.setRuleId(eff.getDomainRuleId() != null ? eff.getDomainRuleId() : eff.getCustomRuleId());
                if (rule.getRuleId() == null) rule.setRuleId("DEFAULT_" + eff.getObjNm() + "_" + eff.getAttrNm());
                rule.setRuleNm(eff.getEffectiveRuleNm());
                rule.setRuleType(eff.getEffectiveRuleType());
                rule.setRuleParams(eff.getEffectiveRuleParams());
                rule.setSeverity("WARN");

                totalViolations += executeRuleOnColumn(rule, eff.getObjNm(), eff.getAttrNm(),
                        builder, dbHandler);
                totalRules++;
            }

            updateFinalStats("DONE", null, totalRules, totalViolations);
            log.info(">> BusinessRuleService DONE: rules={} totalViol={}", totalRules, totalViolations);
        } catch (Exception e) {
            log.error(">> BusinessRuleService failed", e);
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            updateStatus("ERROR", msg);
        } finally {
            try { if (dbHandler != null) dbHandler.close(); } catch (Exception ignore) {}
        }
    }

    private long executeRuleOnColumn(QualRuleVo rule, String objNm, String attrNm,
                                      RuleSqlBuilder builder, DBHandler db) {
        try {
            RuleSqlBuilder.BuiltSql built = builder.build(rule, objNm, attrNm,
                    sampleRate, null, null);   // 증분 단순화 — null 전달

            long total = countSql(db, built.totalSql);
            long viol  = countSql(db, built.violationSql);
            double rate = total == 0 ? 0.0 : (double) viol / total;

            QualRuleResultVo r = new QualRuleResultVo();
            r.setDiagId(diagId);
            r.setRuleId(rule.getRuleId());
            r.setObjNm(objNm);
            r.setAttrNm(attrNm);
            r.setTotalCnt(total);
            r.setViolationCnt(viol);
            r.setViolationRate(java.math.BigDecimal.valueOf(rate * 100)
                    .setScale(4, java.math.RoundingMode.HALF_UP));
            r.setSampleCnt(0);
            if (rate > VIOLATION_RATE_BREAK) {
                r.setErrorMsg("[INFO] 위반률 90% 초과 — 룰 정의 오류 의심");
            }
            sql.insert("qualDiag.insertRuleResult", r);
            return viol;
        } catch (Exception e) {
            log.warn(">> 룰 실패 ruleId={} objNm={} attrNm={}: {}",
                    rule.getRuleId(), objNm, attrNm, e.getMessage());
            QualRuleResultVo r = new QualRuleResultVo();
            r.setDiagId(diagId);
            r.setRuleId(rule.getRuleId());
            r.setObjNm(objNm);
            r.setAttrNm(attrNm);
            r.setErrorMsg(e.getMessage());
            sql.insert("qualDiag.insertRuleResult", r);
            return 0;
        }
    }

    private long countSql(DBHandler db, String sql) throws Exception {
        java.sql.Statement st = db.createStatement();
        try {
            st.setQueryTimeout(SQL_TIMEOUT_SEC);
            ResultSet rs = st.executeQuery(sql);
            try { return rs.next() ? rs.getLong(1) : 0L; } finally { rs.close(); }
        } finally { st.close(); }
    }

    private void updateStatus(String status, String errorMsg) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("status", status);
        p.put("errorMsg", errorMsg);
        sql.update("qualDiag.updateHistoryStatus", p);
    }

    private void updateFinalStats(String status, String errorMsg, int totalRules, long totalViolations) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("status", status);
        p.put("errorMsg", errorMsg);
        p.put("totalRules", totalRules);
        p.put("totalViolations", (int) Math.min(Integer.MAX_VALUE, totalViolations));
        sql.update("qualDiag.updateHistoryStatus", p);
    }
}
