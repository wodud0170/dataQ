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

    @Autowired
    private QualLockService lockService;

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

        // 83번 §6-4 글로벌 동시 진단 N건 제한 (default 5).
        // 슬롯 못 얻으면 SKIP 상태로 종료 — 사용자에게 큐 적재 안내.
        if (!lockService.tryAcquireGlobalSlot()) {
            log.warn(">> BusinessRuleService SKIPPED — 글로벌 동시 진단 큐 가득 (max={}, used={})",
                    lockService.globalMax(), lockService.globalMax() - lockService.globalAvailable());
            updateStatus("SKIPPED", "글로벌 동시 진단 큐 가득. 잠시 후 재시도");
            return;
        }

        DBHandler dbHandler = null;
        long totalViolations = 0;
        int  totalRules = 0;
        int  skippedColumns = 0;

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

            // 83번 Step5 — 진행률 totalCols (필터·EXCLUDE·NONE 제외)
            int progressTotal = 0;
            for (QualColRuleVo e : effList) {
                String src = e.getEffectiveSource();
                if ("EXCLUDED".equals(src) || "NONE".equals(src) || e.getEffectiveRuleType() == null) continue;
                if (scopeKeys != null && !scopeKeys.isEmpty()
                        && !scopeKeys.contains(e.getObjNm() + "." + e.getAttrNm())) continue;
                progressTotal++;
            }
            updateProgress(0, progressTotal);
            int progressDone = 0;

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

                // 83번 §6-2 컬럼 단위 application-level lock.
                // 동일 컬럼이 다른 진단에 점유 중이면 SKIP — 운영 DB 락 X, 우리 메타DB 만 사용.
                if (!lockService.acquire(eff.getDmId(), eff.getObjNm(), eff.getAttrNm(), diagId, userId)) {
                    skippedColumns++;
                    log.info(">> 컬럼 SKIP — {}.{} 다른 진단 점유 중", eff.getObjNm(), eff.getAttrNm());
                    continue;
                }

                try {
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
                } finally {
                    // lock 은 정상/예외 무관 무조건 해제
                    lockService.release(eff.getDmId(), eff.getObjNm(), eff.getAttrNm());
                    progressDone++;
                    updateProgress(progressDone, progressTotal);
                }
            }

            String doneMsg = skippedColumns > 0
                    ? String.format("동시 진단 SKIP 컬럼 %d개", skippedColumns) : null;
            updateFinalStats("DONE", doneMsg, totalRules, totalViolations);
            log.info(">> BusinessRuleService DONE: rules={} totalViol={} skipped={}",
                    totalRules, totalViolations, skippedColumns);
        } catch (Exception e) {
            log.error(">> BusinessRuleService failed", e);
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            updateStatus("ERROR", msg);
        } finally {
            try { if (dbHandler != null) dbHandler.close(); } catch (Exception ignore) {}
            // 글로벌 슬롯 해제 — 다른 진단이 진입 가능
            lockService.releaseGlobalSlot();
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

    private void updateProgress(int done, int total) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("progressDone",  done);
        p.put("progressTotal", total);
        sql.update("qualDiag.updateProgress", p);
    }
}
