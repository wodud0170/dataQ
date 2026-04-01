package qualityexecutor.service.std;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.ndata.model.DataSourceVo;
import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.datasource.dbms.ext.NamedParamStatement;
import com.ndata.quality.model.std.StdDataModelSchemaVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 구조 진단 서비스 (수집 스냅샷 vs 실제 DBMS 스키마 비교)
 *
 * <p>이전 수집 스냅샷(TB_DATA_MODEL_ATTR)과 현재 실제 DBMS의 컬럼 정보를 비교하여
 * 추가/삭제/변경된 테이블 및 컬럼을 감지한다.</p>
 * <ul>
 *   <li>run(): 비동기 진단 실행 → 결과를 TB_STRUCT_DIAG_HISTORY/DETAIL에 저장</li>
 *   <li>compareSchema(): 동기 비교 → 결과를 직접 반환 (DB 미저장)</li>
 * </ul>
 */
@Slf4j
@NoArgsConstructor
public class StructDiagService implements Runnable {

    private String diagId;
    private String dataModelId;
    private String userId;
    private String clctId; // 선택적: null이면 최신 수집건 사용

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private DataSourceUtils dataSourceUtils;

    public StructDiagService(String diagId, String dataModelId, String userId, String clctId) {
        this.diagId = diagId;
        this.dataModelId = dataModelId;
        this.userId = userId;
        this.clctId = clctId;
    }

    /**
     * 구조 진단 비동기 실행 (Runnable.run)
     *
     * <p>처리 흐름:</p>
     * <ol>
     *   <li>데이터모델 정보 및 수집 스냅샷 결정</li>
     *   <li>스냅샷의 컬럼 목록(prev) 로드</li>
     *   <li>실제 DBMS에 접속하여 현재 컬럼 목록(curr) 수집</li>
     *   <li>prev vs curr 비교: ADDED / DELETED / MODIFIED 분류</li>
     *   <li>결과를 TB_STRUCT_DIAG_HISTORY/DETAIL에 저장</li>
     *   <li>TB_DATA_MODEL.structDiagYn 갱신 (변경 0건이면 Y, 아니면 N)</li>
     * </ol>
     */
    @Override
    public void run() {
        log.info(">> StructDiagService started: diagId={}, dataModelId={}", diagId, dataModelId);
        DBHandler dbHandler = null;

        try {
            updateStatus("RUNNING");

            // 1. 데이터모델 정보 조회
            Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
            if (dmInfo == null) {
                log.error(">> StructDiag: 데이터모델 없음 dataModelId={}", dataModelId);
                updateStatus("ERROR");
                return;
            }
            String dsId = (String) dmInfo.get("dataModelDsId");

            // 2. 수집 스냅샷 결정 (clctId 지정 시 해당 건, 없으면 최신)
            String targetClctId;
            String targetClctDt;
            if (this.clctId != null && !this.clctId.trim().isEmpty()) {
                targetClctId = this.clctId;
                Map<String, Object> clctInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelClctById", this.clctId);
                targetClctDt = clctInfo != null ? (String) clctInfo.get("clctEndDt") : null;
            } else {
                List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
                        "structdiag.selectRecentClctIds", dataModelId);
                if (recentClcts == null || recentClcts.isEmpty()) {
                    log.warn(">> StructDiag: 수집 이력 없음 diagId={}", diagId);
                    updateStatus("ERROR");
                    return;
                }
                targetClctId = (String) recentClcts.get(0).get("clctId");
                targetClctDt = (String) recentClcts.get(0).get("clctEndDt");
            }

            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", targetClctId);
            log.info(">> StructDiag: 수집 스냅샷 {} 컬럼 로드 (clctId={})", prevAttrs.size(), targetClctId);

            // 3. 실제 DBMS에 접속하여 현재 스키마 읽기 (CURR = 실제 DB 상태)
            DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
            dbHandler = dataSourceUtils.getDBHandler(dataSource);

            // 수집 대상 스키마 목록
            List<StdDataModelSchemaVo> schemaFilter = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelSchemaList", dataModelId);
            List<String> schemas = new ArrayList<>();
            for (StdDataModelSchemaVo sf : schemaFilter) {
                if ("Y".equals(sf.getUseYn())) schemas.add(sf.getSchemaNm());
            }
            if (schemas.isEmpty()) schemas.add(dbHandler.getSchema());

            String schemaNm = String.join(",", schemas);

            // DBMS별 컬럼 정보 쿼리
            String attrQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetAttrs");
            List<Map<String, Object>> currAttrs = new ArrayList<>();

            for (String schema : schemas) {
                NamedParamStatement pstmt = dbHandler.namedParamStatement(attrQuery);
                pstmt.setString("owner", com.ndata.module.StringUtils.upperCase(schema));
                ResultSet rs = dbHandler.executeSql(pstmt);
                while (rs.next()) {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("tableNm", rs.getString("objNm"));
                    attr.put("columnNm", rs.getString("attrNm"));
                    attr.put("dataType", rs.getString("dataType"));
                    attr.put("dataLen", rs.getLong("dataLen"));
                    attr.put("nullableYn", rs.getString("nullableYn"));
                    currAttrs.add(attr);
                }
                pstmt.close();
                rs.close();
            }
            log.info(">> StructDiag: 실제 DB {} 컬럼 읽기 완료 (스키마: {})", currAttrs.size(), schemaNm);

            // 4. Diff: 수집 스냅샷(prev) vs 실제 DB(curr)
            Map<String, Map<String, Object>> prevMap = toAttrMap(prevAttrs);
            Map<String, Map<String, Object>> currMap = toAttrMap(currAttrs);

            List<Map<String, Object>> changes = new ArrayList<>();
            int addedTables = 0, addedColumns = 0, modifiedColumns = 0;
            int deletedTables = 0, deletedColumns = 0;

            Set<String> prevTableSet = new HashSet<>();
            Set<String> currTableSet = new HashSet<>();
            for (String key : prevMap.keySet()) prevTableSet.add(key.split("\\|")[0]);
            for (String key : currMap.keySet()) currTableSet.add(key.split("\\|")[0]);

            // ADDED: 실제 DB에 있지만 수집 스냅샷에 없는 것
            for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
                if (!prevMap.containsKey(entry.getKey())) {
                    Map<String, Object> curr = entry.getValue();
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("tableNm", curr.get("tableNm"));
                    detail.put("columnNm", curr.get("columnNm"));
                    detail.put("changeType", "ADDED");
                    detail.put("prevDataType", null);
                    detail.put("currDataType", curr.get("dataType"));
                    detail.put("prevDataLen", null);
                    detail.put("currDataLen", curr.get("dataLen"));
                    detail.put("prevNullable", null);
                    detail.put("currNullable", curr.get("nullableYn"));
                    changes.add(detail);
                    addedColumns++;
                }
            }

            // DELETED: 수집 스냅샷에 있지만 실제 DB에 없는 것
            for (Map.Entry<String, Map<String, Object>> entry : prevMap.entrySet()) {
                if (!currMap.containsKey(entry.getKey())) {
                    Map<String, Object> prev = entry.getValue();
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("tableNm", prev.get("tableNm"));
                    detail.put("columnNm", prev.get("columnNm"));
                    detail.put("changeType", "DELETED");
                    detail.put("prevDataType", prev.get("dataType"));
                    detail.put("currDataType", null);
                    detail.put("prevDataLen", prev.get("dataLen"));
                    detail.put("currDataLen", null);
                    detail.put("prevNullable", prev.get("nullableYn"));
                    detail.put("currNullable", null);
                    changes.add(detail);
                    deletedColumns++;
                }
            }

            // MODIFIED: 둘 다 있지만 속성이 다른 것
            for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
                if (prevMap.containsKey(entry.getKey())) {
                    Map<String, Object> prev = prevMap.get(entry.getKey());
                    Map<String, Object> curr = entry.getValue();
                    boolean typeChanged = !nullSafeEquals(prev.get("dataType"), curr.get("dataType"));
                    boolean lenChanged = !nullSafeEquals(prev.get("dataLen"), curr.get("dataLen"));
                    boolean nullableChanged = !nullSafeEquals(prev.get("nullableYn"), curr.get("nullableYn"));

                    if (typeChanged || lenChanged || nullableChanged) {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("tableNm", curr.get("tableNm"));
                        detail.put("columnNm", curr.get("columnNm"));
                        detail.put("changeType", "MODIFIED");
                        detail.put("prevDataType", prev.get("dataType"));
                        detail.put("currDataType", curr.get("dataType"));
                        detail.put("prevDataLen", prev.get("dataLen"));
                        detail.put("currDataLen", curr.get("dataLen"));
                        detail.put("prevNullable", prev.get("nullableYn"));
                        detail.put("currNullable", curr.get("nullableYn"));
                        changes.add(detail);
                        modifiedColumns++;
                    }
                }
            }

            for (String t : currTableSet) { if (!prevTableSet.contains(t)) addedTables++; }
            for (String t : prevTableSet) { if (!currTableSet.contains(t)) deletedTables++; }

            // 5. 결과 저장
            Map<String, Object> historyParam = new HashMap<>();
            historyParam.put("diagId", diagId);
            historyParam.put("dsId", dsId);
            historyParam.put("schemaNm", schemaNm);
            historyParam.put("prevCollectDt", targetClctDt);
            historyParam.put("totalTables", currTableSet.size());
            historyParam.put("totalColumns", currAttrs.size());
            historyParam.put("addedTables", addedTables);
            historyParam.put("addedColumns", addedColumns);
            historyParam.put("modifiedColumns", modifiedColumns);
            historyParam.put("deletedTables", deletedTables);
            historyParam.put("deletedColumns", deletedColumns);
            sqlSessionTemplate.update("structdiag.updateStructDiagResult", historyParam);

            int seq = 1;
            for (Map<String, Object> change : changes) {
                change.put("diagId", diagId);
                change.put("seq", seq++);
                sqlSessionTemplate.insert("structdiag.insertStructDiagDetail", change);
            }

            updateStatus("DONE");

            // TB_DATA_MODEL에 구조진단 결과 반영
            boolean isMatch = (changes.size() == 0);
            Map<String, Object> dmUpdateParam = new HashMap<>();
            dmUpdateParam.put("dataModelId", dataModelId);
            dmUpdateParam.put("structDiagYn", isMatch ? "Y" : "N");
            sqlSessionTemplate.update("structdiag.updateDataModelStructDiag", dmUpdateParam);

            log.info("[StructDiag] 완료 - diagId={}, 수집스냅샷({}) vs 실제DB, 변경={}건, 일치={}",
                    diagId, targetClctId, changes.size(), isMatch);

        } catch (Exception e) {
            log.error(">> StructDiagService error: diagId={}", diagId, e);
            updateStatus("ERROR");
        } finally {
            if (dbHandler != null) {
                try { dbHandler.close(); } catch (Exception ignore) {}
            }
        }
    }

    private void updateStatus(String status) {
        Map<String, Object> param = new HashMap<>();
        param.put("diagId", diagId);
        param.put("status", status);
        sqlSessionTemplate.update("structdiag.updateStructDiagStatus", param);
    }

    private Map<String, Map<String, Object>> toAttrMap(List<Map<String, Object>> attrs) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> attr : attrs) {
            String key = attr.get("tableNm") + "|" + attr.get("columnNm");
            map.put(key, attr);
        }
        return map;
    }

    /**
     * 스키마 비교 (동기 실행, DB 미저장)
     *
     * <p>수집 스냅샷과 현재 DBMS 스키마를 테이블/컬럼 단위로 비교하여
     * 결과를 직접 반환한다. 프론트에서 실시간 비교 화면에 사용.</p>
     *
     * @param dataModelId 데이터모델 ID
     * @param clctId      수집 ID (null이면 최신 수집건 사용)
     * @return { tables: 테이블별 비교 결과, summary: 요약 통계 }
     */
    public Map<String, Object> compareSchema(String dataModelId, String clctId) {
        Map<String, Object> resultMap = new HashMap<>();
        DBHandler dbHandler = null;

        try {
            // 1. 데이터모델 정보 조회
            Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
            if (dmInfo == null) {
                log.error(">> compareSchema: 데이터모델 없음 dataModelId={}", dataModelId);
                resultMap.put("error", "데이터모델을 찾을 수 없습니다.");
                return resultMap;
            }
            String dsId = (String) dmInfo.get("dataModelDsId");

            // 2. 수집 스냅샷 결정
            String targetClctId;
            if (clctId != null && !clctId.trim().isEmpty()) {
                targetClctId = clctId;
            } else {
                List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
                        "structdiag.selectRecentClctIds", dataModelId);
                if (recentClcts == null || recentClcts.isEmpty()) {
                    resultMap.put("error", "수집 이력이 없습니다.");
                    return resultMap;
                }
                targetClctId = (String) recentClcts.get(0).get("clctId");
            }

            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", targetClctId);

            // 3. 실제 DBMS 접속하여 현재 스키마 읽기
            DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
            dbHandler = dataSourceUtils.getDBHandler(dataSource);

            List<StdDataModelSchemaVo> schemaFilter = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelSchemaList", dataModelId);
            List<String> schemas = new ArrayList<>();
            for (StdDataModelSchemaVo sf : schemaFilter) {
                if ("Y".equals(sf.getUseYn())) schemas.add(sf.getSchemaNm());
            }
            if (schemas.isEmpty()) schemas.add(dbHandler.getSchema());

            String attrQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetAttrs");
            List<Map<String, Object>> currAttrs = new ArrayList<>();

            for (String schema : schemas) {
                NamedParamStatement pstmt = dbHandler.namedParamStatement(attrQuery);
                pstmt.setString("owner", com.ndata.module.StringUtils.upperCase(schema));
                ResultSet rs = dbHandler.executeSql(pstmt);
                while (rs.next()) {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("tableNm", rs.getString("objNm"));
                    attr.put("columnNm", rs.getString("attrNm"));
                    attr.put("dataType", rs.getString("dataType"));
                    attr.put("dataLen", rs.getLong("dataLen"));
                    attr.put("nullableYn", rs.getString("nullableYn"));
                    currAttrs.add(attr);
                }
                pstmt.close();
                rs.close();
            }

            // 4. 테이블 단위로 그룹핑하여 비교
            Map<String, List<Map<String, Object>>> prevByTable = groupByTable(prevAttrs);
            Map<String, List<Map<String, Object>>> currByTable = groupByTable(currAttrs);

            Set<String> allTables = new HashSet<>();
            allTables.addAll(prevByTable.keySet());
            allTables.addAll(currByTable.keySet());

            List<Map<String, Object>> tables = new ArrayList<>();
            int sameTables = 0, modifiedTables = 0, addedTables = 0, deletedTables = 0;

            for (String tableNm : allTables) {
                Map<String, Object> tableResult = new HashMap<>();
                tableResult.put("tableNm", tableNm);

                List<Map<String, Object>> prevCols = prevByTable.get(tableNm);
                List<Map<String, Object>> currCols = currByTable.get(tableNm);

                if (prevCols == null) {
                    // 테이블 추가
                    tableResult.put("status", "ADDED");
                    tableResult.put("columns", buildColumnsForAdded(currCols));
                    addedTables++;
                } else if (currCols == null) {
                    // 테이블 삭제
                    tableResult.put("status", "DELETED");
                    tableResult.put("columns", buildColumnsForDeleted(prevCols));
                    deletedTables++;
                } else {
                    // 컬럼별 매칭
                    List<Map<String, Object>> columnResults = compareColumns(prevCols, currCols);
                    boolean hasChange = false;
                    for (Map<String, Object> col : columnResults) {
                        if (!"SAME".equals(col.get("status"))) {
                            hasChange = true;
                            break;
                        }
                    }
                    tableResult.put("status", hasChange ? "MODIFIED" : "SAME");
                    tableResult.put("columns", columnResults);
                    if (hasChange) modifiedTables++;
                    else sameTables++;
                }
                tables.add(tableResult);
            }

            // 정렬: 변경된 것 먼저
            tables.sort(new java.util.Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> a, Map<String, Object> b) {
                    int oa = statusOrder((String) a.get("status"));
                    int ob = statusOrder((String) b.get("status"));
                    if (oa != ob) return oa - ob;
                    return ((String) a.get("tableNm")).compareTo((String) b.get("tableNm"));
                }
                private int statusOrder(String s) {
                    if ("DELETED".equals(s)) return 0;
                    if ("MODIFIED".equals(s)) return 1;
                    if ("ADDED".equals(s)) return 2;
                    return 3;
                }
            });

            resultMap.put("tables", tables);
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalTables", allTables.size());
            summary.put("sameTables", sameTables);
            summary.put("modifiedTables", modifiedTables);
            summary.put("addedTables", addedTables);
            summary.put("deletedTables", deletedTables);
            resultMap.put("summary", summary);

        } catch (Exception e) {
            log.error(">> compareSchema error", e);
            resultMap.put("error", e.getMessage());
        } finally {
            if (dbHandler != null) {
                try { dbHandler.close(); } catch (Exception ignore) {}
            }
        }
        return resultMap;
    }

    private Map<String, List<Map<String, Object>>> groupByTable(List<Map<String, Object>> attrs) {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        for (Map<String, Object> attr : attrs) {
            String tableNm = (String) attr.get("tableNm");
            if (!map.containsKey(tableNm)) {
                map.put(tableNm, new ArrayList<>());
            }
            map.get(tableNm).add(attr);
        }
        return map;
    }

    private List<Map<String, Object>> buildColumnsForAdded(List<Map<String, Object>> currCols) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> col : currCols) {
            Map<String, Object> c = new HashMap<>();
            c.put("columnNm", col.get("columnNm"));
            c.put("status", "ADDED");
            c.put("snapshotType", null);
            c.put("snapshotLen", null);
            c.put("snapshotNullable", null);
            c.put("currentType", col.get("dataType"));
            c.put("currentLen", col.get("dataLen"));
            c.put("currentNullable", col.get("nullableYn"));
            result.add(c);
        }
        return result;
    }

    private List<Map<String, Object>> buildColumnsForDeleted(List<Map<String, Object>> prevCols) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> col : prevCols) {
            Map<String, Object> c = new HashMap<>();
            c.put("columnNm", col.get("columnNm"));
            c.put("status", "DELETED");
            c.put("snapshotType", col.get("dataType"));
            c.put("snapshotLen", col.get("dataLen"));
            c.put("snapshotNullable", col.get("nullableYn"));
            c.put("currentType", null);
            c.put("currentLen", null);
            c.put("currentNullable", null);
            result.add(c);
        }
        return result;
    }

    private List<Map<String, Object>> compareColumns(List<Map<String, Object>> prevCols, List<Map<String, Object>> currCols) {
        Map<String, Map<String, Object>> prevMap = new LinkedHashMap<>();
        for (Map<String, Object> col : prevCols) {
            prevMap.put((String) col.get("columnNm"), col);
        }
        Map<String, Map<String, Object>> currMap = new LinkedHashMap<>();
        for (Map<String, Object> col : currCols) {
            currMap.put((String) col.get("columnNm"), col);
        }

        Set<String> allCols = new LinkedHashSet<>();
        allCols.addAll(prevMap.keySet());
        allCols.addAll(currMap.keySet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (String colNm : allCols) {
            Map<String, Object> prev = prevMap.get(colNm);
            Map<String, Object> curr = currMap.get(colNm);
            Map<String, Object> c = new HashMap<>();
            c.put("columnNm", colNm);

            if (prev == null) {
                c.put("status", "ADDED");
                c.put("snapshotType", null);
                c.put("snapshotLen", null);
                c.put("snapshotNullable", null);
                c.put("currentType", curr.get("dataType"));
                c.put("currentLen", curr.get("dataLen"));
                c.put("currentNullable", curr.get("nullableYn"));
            } else if (curr == null) {
                c.put("status", "DELETED");
                c.put("snapshotType", prev.get("dataType"));
                c.put("snapshotLen", prev.get("dataLen"));
                c.put("snapshotNullable", prev.get("nullableYn"));
                c.put("currentType", null);
                c.put("currentLen", null);
                c.put("currentNullable", null);
            } else {
                boolean typeChanged = !nullSafeEquals(prev.get("dataType"), curr.get("dataType"));
                boolean lenChanged = !nullSafeEquals(prev.get("dataLen"), curr.get("dataLen"));
                boolean nullableChanged = !nullSafeEquals(prev.get("nullableYn"), curr.get("nullableYn"));

                c.put("status", (typeChanged || lenChanged || nullableChanged) ? "MODIFIED" : "SAME");
                c.put("snapshotType", prev.get("dataType"));
                c.put("snapshotLen", prev.get("dataLen"));
                c.put("snapshotNullable", prev.get("nullableYn"));
                c.put("currentType", curr.get("dataType"));
                c.put("currentLen", curr.get("dataLen"));
                c.put("currentNullable", curr.get("nullableYn"));
            }
            result.add(c);
        }
        return result;
    }

    private boolean nullSafeEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
}
