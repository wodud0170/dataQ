package qualityexecutor.service.std;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.model.DataSourceVo;
import com.ndata.quality.model.std.QualProfileResultVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 값 프로파일링 서비스 (VALUE) — 67번 §3
 *
 * 컬럼별 통계: TOTAL/NULL/DISTINCT/MIN/MAX/MIN_LEN/MAX_LEN/AVG/STDDEV + Top-5
 */
@Slf4j
@NoArgsConstructor
public class ValueProfileService implements Runnable {

    private String  diagId;
    private String  dataModelId;
    private String  userId;
    private Integer sampleRate;
    private String  targetObj;     // 테이블 단위 또는 컬럼 단위 시 OBJ_NM
    private String  targetAttr;    // 컬럼 단위 시 ATTR_NM
    private java.util.Set<String> targetKeys;  // "OBJ_NM.ATTR_NM" Set — 다중 컬럼

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private DataSourceUtils dataSourceUtils;

    private static final int SQL_TIMEOUT_SEC   = 30;
    private static final int TOTAL_TIMEOUT_SEC = 1800;
    private static final Gson gson = new Gson();

    public ValueProfileService(String diagId, String dataModelId, String userId,
                               Integer sampleRate, String targetObj) {
        this(diagId, dataModelId, userId, sampleRate, targetObj, null);
    }

    public ValueProfileService(String diagId, String dataModelId, String userId,
                               Integer sampleRate, String targetObj, String targetAttr) {
        this(diagId, dataModelId, userId, sampleRate, targetObj, targetAttr, null);
    }

    public ValueProfileService(String diagId, String dataModelId, String userId,
                               Integer sampleRate, String targetObj, String targetAttr,
                               java.util.Set<String> targetKeys) {
        this.diagId      = diagId;
        this.dataModelId = dataModelId;
        this.userId      = userId;
        this.sampleRate  = sampleRate;
        this.targetObj   = targetObj;
        this.targetAttr  = targetAttr;
        this.targetKeys  = targetKeys;
    }

    @Override
    public void run() {
        long startedAt = System.currentTimeMillis();
        log.info(">> ValueProfileService start: diagId={} dmId={} obj={} sampleRate={}",
                diagId, dataModelId, targetObj, sampleRate);
        DBHandler dbHandler = null;
        int totalCols = 0;

        try {
            updateStatus("RUNNING", null);

            Map<String, Object> dmInfo = sql.selectOne("datamodel.selectDataModelById", dataModelId);
            if (dmInfo == null) throw new IllegalStateException("[DATA_NOT_FOUND] 모델 없음");
            String dsId = (String) dmInfo.get("dataModelDsId");
            if (dsId == null || dsId.trim().isEmpty()) {
                throw new IllegalStateException("[CONFIG] 논리 모델은 VALUE 진단 대상이 아닙니다");
            }
            DataSourceVo dataSource = sql.selectOne("sysinfo.selectDataSourceById", dsId);
            String dbmsType = dataSource.getDbmsTp();
            dbHandler = dataSourceUtils.getDBHandler(dataSource);

            // 모델의 OBJ/ATTR 목록 (DM_ID + USE_YN='Y')
            List<Map<String, Object>> attrs = sql.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", dataModelId);
            log.info(">> 컬럼 {} 개 로드", attrs.size());

            for (Map<String, Object> a : attrs) {
                if (System.currentTimeMillis() - startedAt > TOTAL_TIMEOUT_SEC * 1000L) {
                    throw new IllegalStateException("[TIMEOUT] 30분 누적 초과");
                }
                // selectDataModelAttrListByClctIdRaw 의 alias = tableNm/columnNm
                String objNm  = (String) (a.get("tableNm") != null ? a.get("tableNm") : a.get("objNm"));
                String attrNm = (String) (a.get("columnNm") != null ? a.get("columnNm") : a.get("attrNm"));
                String dataType = (String) a.get("dataType");
                if (targetObj  != null && !targetObj.equals(objNm))   continue;
                if (targetAttr != null && !targetAttr.equals(attrNm)) continue;
                if (objNm == null || attrNm == null) continue;
                if (targetKeys != null && !targetKeys.isEmpty()
                        && !targetKeys.contains(objNm + "." + attrNm)) continue;

                try {
                    QualProfileResultVo r = profileColumn(dbHandler, dbmsType, objNm, attrNm, dataType);
                    if (r != null) {
                        r.setDmId(dataModelId);
                        r.setObjNm(objNm);
                        r.setAttrNm(attrNm);
                        r.setDiagId(diagId);
                        // 직전값 (UPSERT)
                        sql.insert("qualDiag.upsertProfileResult", r);
                        // 시계열 누적 (통계 메뉴용)
                        sql.insert("qualDiag.insertProfileHistory", r);
                        totalCols++;
                    }
                } catch (Exception e) {
                    log.warn(">> 컬럼 프로파일 실패 obj={} attr={}: {}", objNm, attrNm, e.getMessage());
                }
            }

            updateFinal("DONE", null, totalCols);
            log.info(">> ValueProfileService DONE: cols={}", totalCols);
        } catch (Exception e) {
            log.error(">> ValueProfileService failed", e);
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            updateStatus("ERROR", msg);
        } finally {
            try { if (dbHandler != null) dbHandler.close(); } catch (Exception ignore) {}
        }
    }

    private QualProfileResultVo profileColumn(DBHandler db, String dbmsType,
                                               String objNm, String attrNm, String dataType) throws Exception {
        if (!isSafeIdent(objNm) || !isSafeIdent(attrNm)) {
            log.warn(">> 비정상 식별자 스킵: obj={} attr={}", objNm, attrNm);
            return null;
        }
        boolean numeric = isNumeric(dataType);
        String sampleClause = sampleClause(dbmsType, sampleRate);

        // 1) 기본 집계
        StringBuilder sqlBuf = new StringBuilder();
        sqlBuf.append("SELECT COUNT(*) AS total_cnt, ")
              .append("COUNT(*) - COUNT(").append(attrNm).append(") AS null_cnt, ")
              .append("COUNT(DISTINCT ").append(attrNm).append(") AS distinct_cnt, ")
              .append("MIN(").append(toText(attrNm, dbmsType)).append(") AS min_val, ")
              .append("MAX(").append(toText(attrNm, dbmsType)).append(") AS max_val, ")
              .append("MIN(").append(lenFn(dbmsType)).append("(").append(attrNm).append(")) AS min_len, ")
              .append("MAX(").append(lenFn(dbmsType)).append("(").append(attrNm).append(")) AS max_len");
        if (numeric) {
            sqlBuf.append(", AVG(").append(attrNm).append(") AS avg_val, ")
                  .append("STDDEV(").append(attrNm).append(") AS std_val");
        }
        sqlBuf.append(" FROM ").append(objNm).append(sampleClause);

        QualProfileResultVo r = new QualProfileResultVo();
        java.sql.Statement st = db.createStatement();
        try {
            st.setQueryTimeout(SQL_TIMEOUT_SEC);
            ResultSet rs = st.executeQuery(sqlBuf.toString());
            try {
                if (rs.next()) {
                    r.setTotalCnt(rs.getLong("total_cnt"));
                    r.setNullCnt(rs.getLong("null_cnt"));
                    r.setDistinctCnt(rs.getLong("distinct_cnt"));
                    r.setMinVal(truncate(rs.getString("min_val"), 200));
                    r.setMaxVal(truncate(rs.getString("max_val"), 200));
                    r.setMinLen(rs.getInt("min_len"));
                    r.setMaxLen(rs.getInt("max_len"));
                    if (numeric) {
                        BigDecimal avg = rs.getBigDecimal("avg_val");
                        BigDecimal std = rs.getBigDecimal("std_val");
                        if (avg != null) r.setAvgVal(avg.setScale(4, java.math.RoundingMode.HALF_UP));
                        if (std != null) r.setStdVal(std.setScale(4, java.math.RoundingMode.HALF_UP));
                    }
                }
            } finally { rs.close(); }
        } finally { st.close(); }

        // 2) Top-5 (cardinality 가 너무 크면 시간 소요 — 작은 모델만 의미. 1차 그대로 시도)
        try {
            String topSql = "SELECT " + attrNm + " AS v, COUNT(*) AS c FROM " + objNm + sampleClause
                    + " WHERE " + attrNm + " IS NOT NULL GROUP BY " + attrNm
                    + " ORDER BY COUNT(*) DESC " + limitClause(dbmsType, 5);
            java.sql.Statement st2 = db.createStatement();
            try {
                st2.setQueryTimeout(SQL_TIMEOUT_SEC);
                ResultSet rs2 = st2.executeQuery(topSql);
                List<Map<String, Object>> top = new ArrayList<>();
                try {
                    while (rs2.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("v", rs2.getString("v"));
                        m.put("cnt", rs2.getLong("c"));
                        top.add(m);
                    }
                } finally { rs2.close(); }
                r.setTopValues(gson.toJson(top));
            } finally { st2.close(); }
        } catch (Exception ignore) {
            // top-5 실패는 치명적이지 않음
        }
        return r;
    }

    // ---------- DBMS dialect ----------

    private boolean isSafeIdent(String id) {
        return id != null && id.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private boolean isNumeric(String dataType) {
        if (dataType == null) return false;
        String t = dataType.toUpperCase();
        return t.startsWith("NUMBER") || t.startsWith("INT") || t.startsWith("BIGINT")
                || t.startsWith("SMALLINT") || t.startsWith("DECIMAL") || t.startsWith("NUMERIC")
                || t.startsWith("DOUBLE") || t.startsWith("FLOAT") || t.startsWith("REAL");
    }

    private String lenFn(String dbms) {
        switch (dbms.toUpperCase()) {
            case "MSSQL": return "LEN";
            default: return "LENGTH";
        }
    }

    private String toText(String col, String dbms) {
        switch (dbms.toUpperCase()) {
            case "ORACLE": case "TIBERO": return "TO_CHAR(" + col + ")";
            case "MSSQL": return "CAST(" + col + " AS NVARCHAR(200))";
            case "CUBRID":
            case "MYSQL":
            case "MARIADB":
                return "CAST(" + col + " AS VARCHAR(200))";
            default: return "CAST(" + col + " AS TEXT)";
        }
    }

    private String sampleClause(String dbms, Integer rate) {
        if (rate == null || rate >= 100 || rate <= 0) return "";
        switch (dbms.toUpperCase()) {
            case "POSTGRESQL": return " TABLESAMPLE BERNOULLI(" + rate + ")";
            case "ORACLE": case "TIBERO": return " SAMPLE(" + rate + ")";
            case "MSSQL": return " TABLESAMPLE (" + rate + " PERCENT)";
            default: return "";
        }
    }

    private String limitClause(String dbms, int n) {
        switch (dbms.toUpperCase()) {
            case "ORACLE": case "TIBERO": return "FETCH FIRST " + n + " ROWS ONLY";
            case "MSSQL": return "OFFSET 0 ROWS FETCH NEXT " + n + " ROWS ONLY";
            default: return "LIMIT " + n;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void updateStatus(String status, String errorMsg) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("status", status);
        p.put("errorMsg", errorMsg);
        sql.update("qualDiag.updateHistoryStatus", p);
    }

    private void updateFinal(String status, String errorMsg, int totalCols) {
        Map<String, Object> p = new HashMap<>();
        p.put("diagId", diagId);
        p.put("status", status);
        p.put("errorMsg", errorMsg);
        p.put("totalCols", totalCols);
        sql.update("qualDiag.updateHistoryStatus", p);
    }
}
