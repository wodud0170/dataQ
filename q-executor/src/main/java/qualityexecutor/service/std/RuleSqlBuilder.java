package qualityexecutor.service.std;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.ndata.quality.model.std.QualRuleVo;

/**
 * 룰 → DBMS 별 SQL 빌더 (67번 §4.3, 68번 §2)
 *
 * <p>각 룰 타입별로 위반 건수 / 총 건수를 계산하는 SQL 을 생성한다.
 * 1차 구현: NOT_NULL, RANGE, LENGTH, REGEX, ENUM, UNIQUE, REFERENCE, COMPARE.
 * EXPRESSION 은 Phase 4 보안 검토 후 도입.</p>
 */
public class RuleSqlBuilder {

    private final String dbmsType;        // ORACLE / POSTGRESQL / MYSQL / MARIADB / MSSQL / TIBERO
    private final Gson gson = new Gson();

    public RuleSqlBuilder(String dbmsType) {
        this.dbmsType = dbmsType == null ? "POSTGRESQL" : dbmsType.toUpperCase();
    }

    public static class BuiltSql {
        public String violationSql;       // SELECT COUNT(*) FROM ... WHERE 위반조건
        public String totalSql;           // SELECT COUNT(*) FROM ...
        public String whereClause;        // 위반 조건 (샘플 추출용)
        public String fromTable;          // 대상 테이블
    }

    /**
     * 룰을 받아 위반/전체 카운트 SQL 을 만든다. 컬럼 단위 (objNm/attrNm 지정).
     */
    public BuiltSql build(QualRuleVo rule, String objNm, String attrNm,
                          Integer sampleRate, String incrementalCol, String lastDiagDt) {
        BuiltSql out = new BuiltSql();
        if (objNm == null || objNm.isEmpty()) {
            throw new IllegalArgumentException("[CONFIG] objNm 필수 (도메인 룰은 사전에 컬럼별로 전개되어야 함)");
        }
        out.fromTable = objNm;

        JsonObject params = parseParams(rule.getRuleParams());
        String col = quoteId(attrNm);

        // 1. 위반 WHERE 절 생성
        String where;
        switch (rule.getRuleType()) {
            case "NOT_NULL":
                where = col + " IS NULL";
                break;
            case "RANGE": {
                String min = optStr(params, "min");
                String max = optStr(params, "max");
                StringBuilder w = new StringBuilder();
                if (min != null) w.append(col).append(" < ").append(min);
                if (min != null && max != null) w.append(" OR ");
                if (max != null) w.append(col).append(" > ").append(max);
                if (w.length() == 0) throw new IllegalArgumentException("RANGE: min/max 둘 중 하나 이상 필요");
                where = "(" + w + ")";
                break;
            }
            case "LENGTH": {
                Integer minLen = optInt(params, "minLen");
                Integer maxLen = optInt(params, "maxLen");
                StringBuilder w = new StringBuilder();
                if (minLen != null) w.append(lengthFn()).append("(").append(col).append(") < ").append(minLen);
                if (minLen != null && maxLen != null) w.append(" OR ");
                if (maxLen != null) w.append(lengthFn()).append("(").append(col).append(") > ").append(maxLen);
                if (w.length() == 0) throw new IllegalArgumentException("LENGTH: minLen/maxLen 필요");
                where = "(" + w + ")";
                break;
            }
            case "REGEX": {
                String pattern = optStr(params, "pattern");
                if (pattern == null) throw new IllegalArgumentException("REGEX: pattern 필요");
                String esc = pattern.replace("'", "''");
                where = "NOT (" + regexMatch(col, esc) + ") AND " + col + " IS NOT NULL";
                break;
            }
            case "ENUM": {
                JsonArray values = params.has("values") ? params.getAsJsonArray("values") : null;
                if (values == null || values.size() == 0) throw new IllegalArgumentException("ENUM: values 필요");
                StringBuilder vlist = new StringBuilder();
                for (JsonElement e : values) {
                    if (vlist.length() > 0) vlist.append(",");
                    vlist.append("'").append(e.getAsString().replace("'", "''")).append("'");
                }
                where = col + " NOT IN (" + vlist + ") AND " + col + " IS NOT NULL";
                break;
            }
            case "UNIQUE": {
                // GROUP BY 후 HAVING COUNT > 1 — sub-query 형태
                String dup = "SELECT " + col + " FROM " + quoteId(objNm)
                           + " GROUP BY " + col + " HAVING COUNT(*) > 1";
                where = col + " IN (" + dup + ")";
                break;
            }
            case "REFERENCE": {
                String refTable = optStr(params, "refTable");
                String refCol   = optStr(params, "refCol");
                if (refTable == null || refCol == null)
                    throw new IllegalArgumentException("REFERENCE: refTable/refCol 필요");
                where = "NOT EXISTS (SELECT 1 FROM " + quoteId(refTable)
                      + " r WHERE r." + quoteId(refCol) + " = " + quoteId(objNm) + "." + col + ")"
                      + " AND " + col + " IS NOT NULL";
                break;
            }
            case "COMPARE": {
                String left  = optStr(params, "leftCol");
                String op    = optStr(params, "op");
                String right = optStr(params, "rightCol");
                if (left == null || op == null || right == null)
                    throw new IllegalArgumentException("COMPARE: leftCol/op/rightCol 필요");
                where = "NOT (" + quoteId(left) + " " + op + " " + quoteId(right) + ")";
                break;
            }
            default:
                throw new IllegalArgumentException("[CONFIG] 미지원 RULE_TYPE: " + rule.getRuleType()
                        + " (Phase 1 미구현)");
        }
        out.whereClause = where;

        // 2. 증분 절 부착
        StringBuilder incr = new StringBuilder();
        if (incrementalCol != null && !incrementalCol.isEmpty() && lastDiagDt != null) {
            incr.append(" AND ").append(quoteId(incrementalCol)).append(" > ").append(literalTimestamp(lastDiagDt));
        }

        // 3. 샘플링 절 (FROM 뒤에)
        String sampleClause = sampleClause(sampleRate);

        // 4. 최종 SQL
        StringBuilder vSql = new StringBuilder("SELECT COUNT(*) FROM ")
                .append(quoteId(objNm)).append(sampleClause)
                .append(" WHERE ").append(where).append(incr);
        StringBuilder tSql = new StringBuilder("SELECT COUNT(*) FROM ")
                .append(quoteId(objNm)).append(sampleClause);
        if (incr.length() > 0) tSql.append(" WHERE 1=1").append(incr);

        out.violationSql = vSql.toString();
        out.totalSql     = tSql.toString();
        return out;
    }

    /**
     * 위반 행의 PK + 위반 컬럼 값을 N건 조회 (샘플)
     */
    public String buildSampleSql(QualRuleVo rule, String objNm, String attrNm,
                                 String pkCols, BuiltSql built, int sampleN) {
        String selectCols = (pkCols != null && !pkCols.isEmpty())
                ? pkCols : "*";
        String limit = limitClause(sampleN);
        return "SELECT " + selectCols + ", " + quoteId(attrNm) + " AS _violating_val "
                + "FROM " + quoteId(objNm) + " WHERE " + built.whereClause + " " + limit;
    }

    // ============================== DBMS dialect ==============================

    private String quoteId(String id) {
        if (id == null) return null;
        // 식별자 안전성 체크 — 특수문자 포함 시 throw
        if (!id.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("[CONFIG] 비정상 식별자: " + id);
        }
        return id; // 표준 따옴표 없이도 대부분 동작. 필요시 DBMS 별 quote 적용.
    }

    private JsonObject parseParams(String json) {
        if (json == null || json.isEmpty()) return new JsonObject();
        return gson.fromJson(json, JsonObject.class);
    }

    private String optStr(JsonObject p, String k) {
        return p.has(k) && !p.get(k).isJsonNull() ? p.get(k).getAsString() : null;
    }

    private Integer optInt(JsonObject p, String k) {
        return p.has(k) && !p.get(k).isJsonNull() ? p.get(k).getAsInt() : null;
    }

    private String lengthFn() {
        // DBMS 별 문자 길이 함수
        switch (dbmsType) {
            case "ORACLE":
            case "TIBERO":
                return "LENGTH";
            case "MSSQL":
                return "LEN";
            default:
                return "LENGTH";
        }
    }

    private String regexMatch(String col, String pattern) {
        switch (dbmsType) {
            case "ORACLE":
            case "TIBERO":
                return "REGEXP_LIKE(" + col + ", '" + pattern + "')";
            case "POSTGRESQL":
                return col + " ~ '" + pattern + "'";
            case "MYSQL":
            case "MARIADB":
            case "CUBRID":
                return col + " REGEXP '" + pattern + "'";
            case "MSSQL":
                // MSSQL 은 표준 정규식 미지원 — fallback (LIKE 로 일부 패턴만 가능)
                return col + " LIKE '" + pattern.replace("^","").replace("$","") + "'";
            default:
                return col + " ~ '" + pattern + "'";
        }
    }

    private String literalTimestamp(String iso) {
        // iso = "2026-04-28 22:00:00" 형식 가정
        switch (dbmsType) {
            case "ORACLE":
            case "TIBERO":
                return "TO_TIMESTAMP('" + iso + "', 'YYYY-MM-DD HH24:MI:SS')";
            case "CUBRID":
                return "DATETIME '" + iso + "'";
            default:
                return "TIMESTAMP '" + iso + "'";
        }
    }

    private String sampleClause(Integer sampleRate) {
        if (sampleRate == null || sampleRate >= 100 || sampleRate <= 0) return "";
        switch (dbmsType) {
            case "POSTGRESQL":
                return " TABLESAMPLE BERNOULLI(" + sampleRate + ")";
            case "ORACLE":
            case "TIBERO":
                return " SAMPLE(" + sampleRate + ")";
            case "MSSQL":
                return " TABLESAMPLE (" + sampleRate + " PERCENT)";
            default:
                // MySQL/MariaDB/CUBRID 미지원 — 풀스캔
                return "";
        }
    }

    private String limitClause(int n) {
        switch (dbmsType) {
            case "ORACLE":
            case "TIBERO":
                return "FETCH FIRST " + n + " ROWS ONLY";
            case "MSSQL":
                return "OFFSET 0 ROWS FETCH NEXT " + n + " ROWS ONLY";
            default:
                return "LIMIT " + n;
        }
    }
}
