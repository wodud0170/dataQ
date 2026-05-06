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
            // 논리 모델(dsId 없음) 방어: 호출 경로상 q-center 컨트롤러가 사전 차단하지만,
            // 스케줄러 등 다른 진입점 대비
            if (dsId == null || dsId.trim().isEmpty()) {
                log.warn(">> StructDiag: 논리 모델 — 데이터소스 없음. diagId={}", diagId);
                updateStatus("ERROR");
                return;
            }

            // 2. 현재 모델의 OBJ/ATTR 로드 (DM_ID 기반, CLCT 폐기 후)
            //    79번 진단 제외: STRUCT_DIAG_TARGET_YN='Y' 인 OBJ/ATTR 만 prev 로 가져옴 (cascade 포함)
            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectAttrListForStructDiagRaw", dataModelId);
            log.info(">> StructDiag: 모델 컬럼 {} 로드 (dataModelId={})", prevAttrs.size(), dataModelId);

            // 79번 진단 제외: curr 에도 OFF 된 OBJ/ATTR 를 빼야 결과에서 완전히 사라짐.
            // (prev 만 빼면 curr 에는 그대로 남아 ADDED 로 잘못 잡힘)
            List<Map<String, Object>> offRows = sqlSessionTemplate.selectList(
                    "datamodel.selectStructDiagOffSet", dataModelId);
            Set<String> offObjs = new HashSet<>();    // tableNm
            Set<String> offAttrs = new HashSet<>();   // tableNm|columnNm
            for (Map<String, Object> r : offRows) {
                String kind = String.valueOf(r.get("kind"));
                String tbl  = String.valueOf(r.get("tableNm"));
                if ("OBJ".equals(kind)) {
                    offObjs.add(tbl);
                } else {
                    offAttrs.add(tbl + "|" + r.get("columnNm"));
                }
            }
            log.info(">> StructDiag: OFF OBJ {}, OFF ATTR {}", offObjs.size(), offAttrs.size());

            // 최신 수집일시 조회 (이력 기록용)
            String targetClctDt = null;
            List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
                    "structdiag.selectRecentClctIds", dataModelId);
            if (recentClcts != null && !recentClcts.isEmpty()) {
                targetClctDt = (String) recentClcts.get(0).get("clctEndDt");
            }

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
                    String tbl = rs.getString("objNm");
                    String col = rs.getString("attrNm");
                    // 79번 진단 제외: OFF 된 OBJ/ATTR 는 curr 에도 포함 안 함 → 결과에서 완전 제외
                    if (offObjs.contains(tbl) || offAttrs.contains(tbl + "|" + col)) continue;
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("owner", schema);
                    attr.put("tableNm", tbl);
                    attr.put("columnNm", col);
                    attr.put("dataType", rs.getString("dataType"));
                    attr.put("dataLen", rs.getLong("dataLen"));
                    attr.put("nullableYn", rs.getString("nullableYn"));
                    currAttrs.add(attr);
                }
                pstmt.close();
                rs.close();
            }
            log.info(">> StructDiag: 실제 DB {} 컬럼 읽기 완료 (스키마: {}, OFF 제외 후)", currAttrs.size(), schemaNm);

            // 4. Diff: 수집 스냅샷(prev) vs 실제 DB(curr)
            Map<String, Map<String, Object>> prevMap = toAttrMap(prevAttrs);
            Map<String, Map<String, Object>> currMap = toAttrMap(currAttrs);

            List<Map<String, Object>> changes = new ArrayList<>();
            int addedTables = 0, addedColumns = 0, modifiedColumns = 0;
            int deletedTables = 0, deletedColumns = 0;

            Set<String> prevTableSet = new HashSet<>();
            Set<String> currTableSet = new HashSet<>();
            for (String key : prevMap.keySet()) {
                String[] parts = key.split("\\|");
                prevTableSet.add(parts[0] + "|" + parts[1]); // owner|tableNm
            }
            for (String key : currMap.keySet()) {
                String[] parts = key.split("\\|");
                currTableSet.add(parts[0] + "|" + parts[1]); // owner|tableNm
            }

            // ADDED: 실제 DB에 있지만 수집 스냅샷에 없는 것
            for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
                if (!prevMap.containsKey(entry.getKey())) {
                    Map<String, Object> curr = entry.getValue();
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("owner", curr.get("owner"));
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
                    detail.put("owner", prev.get("owner"));
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
                        detail.put("owner", curr.get("owner"));
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

            // 4-1. 인덱스 Diff
            List<Map<String, Object>> indexChanges = new ArrayList<>();
            int addedIndexes = 0, modifiedIndexes = 0, deletedIndexes = 0, totalIndexes = 0;
            List<Map<String, Object>> prevIndexes = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelIndexListByDmId", dataModelId);
            if (prevIndexes != null && !prevIndexes.isEmpty()) {
                // 실제 DB 인덱스 읽기
                String indexQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetIndexes");
                List<Map<String, Object>> currIndexRows = new ArrayList<>();
                if (indexQuery != null) {
                    for (String schema : schemas) {
                        NamedParamStatement ipstmt = dbHandler.namedParamStatement(indexQuery);
                        ipstmt.setString("owner", com.ndata.module.StringUtils.upperCase(schema));
                        ResultSet irs = dbHandler.executeSql(ipstmt);
                        while (irs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            row.put("owner", schema);
                            row.put("tableNm", irs.getString("tableNm"));
                            row.put("indexNm", irs.getString("indexNm"));
                            row.put("indexType", irs.getString("indexType"));
                            row.put("uniqueness", irs.getString("uniqueness"));
                            row.put("columnNm", irs.getString("columnNm"));
                            row.put("columnPos", irs.getInt("columnPos"));
                            row.put("sortOrder", irs.getString("sortOrder"));
                            currIndexRows.add(row);
                        }
                        ipstmt.close(); irs.close();
                    }
                }
                // 인덱스를 owner|tableNm|indexNm 키로 그룹핑 → columnsStr 생성
                Map<String, Map<String, Object>> prevIdxMap = toIndexMap(prevIndexes);
                Map<String, Map<String, Object>> currIdxMap = toIndexMap(currIndexRows);
                totalIndexes = currIdxMap.size();

                for (Map.Entry<String, Map<String, Object>> e : currIdxMap.entrySet()) {
                    if (!prevIdxMap.containsKey(e.getKey())) {
                        Map<String, Object> d = new HashMap<>();
                        Map<String, Object> c = e.getValue();
                        d.put("owner", c.get("owner")); d.put("tableNm", c.get("tableNm"));
                        d.put("indexNm", c.get("indexNm")); d.put("changeType", "ADDED");
                        d.put("currIndexType", c.get("indexType")); d.put("currUniqueness", c.get("uniqueness"));
                        d.put("currColumns", c.get("columnsStr"));
                        indexChanges.add(d); addedIndexes++;
                    }
                }
                for (Map.Entry<String, Map<String, Object>> e : prevIdxMap.entrySet()) {
                    if (!currIdxMap.containsKey(e.getKey())) {
                        Map<String, Object> d = new HashMap<>();
                        Map<String, Object> p = e.getValue();
                        d.put("owner", p.get("owner")); d.put("tableNm", p.get("tableNm"));
                        d.put("indexNm", p.get("indexNm")); d.put("changeType", "DELETED");
                        d.put("prevIndexType", p.get("indexType")); d.put("prevUniqueness", p.get("uniqueness"));
                        d.put("prevColumns", p.get("columnsStr"));
                        indexChanges.add(d); deletedIndexes++;
                    }
                }
                for (Map.Entry<String, Map<String, Object>> e : currIdxMap.entrySet()) {
                    if (prevIdxMap.containsKey(e.getKey())) {
                        Map<String, Object> p = prevIdxMap.get(e.getKey());
                        Map<String, Object> c = e.getValue();
                        boolean changed = !nullSafeEquals(p.get("indexType"), c.get("indexType"))
                                || !nullSafeEquals(p.get("uniqueness"), c.get("uniqueness"))
                                || !nullSafeEquals(p.get("columnsStr"), c.get("columnsStr"));
                        if (changed) {
                            Map<String, Object> d = new HashMap<>();
                            d.put("owner", c.get("owner")); d.put("tableNm", c.get("tableNm"));
                            d.put("indexNm", c.get("indexNm")); d.put("changeType", "MODIFIED");
                            d.put("prevIndexType", p.get("indexType")); d.put("currIndexType", c.get("indexType"));
                            d.put("prevUniqueness", p.get("uniqueness")); d.put("currUniqueness", c.get("uniqueness"));
                            d.put("prevColumns", p.get("columnsStr")); d.put("currColumns", c.get("columnsStr"));
                            indexChanges.add(d); modifiedIndexes++;
                        }
                    }
                }
                log.info(">> StructDiag: 인덱스 diff 완료 - added={}, modified={}, deleted={}", addedIndexes, modifiedIndexes, deletedIndexes);
            }

            // 4-2. 제약조건 Diff
            List<Map<String, Object>> constraintChanges = new ArrayList<>();
            int addedConstraints = 0, modifiedConstraints = 0, deletedConstraints = 0, totalConstraints = 0;
            List<Map<String, Object>> prevConstraints = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelConstraintListByDmId", dataModelId);
            if (prevConstraints != null && !prevConstraints.isEmpty()) {
                String constraintQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetConstraints");
                List<Map<String, Object>> currConstraintRows = new ArrayList<>();
                if (constraintQuery != null) {
                    for (String schema : schemas) {
                        NamedParamStatement cpstmt = dbHandler.namedParamStatement(constraintQuery);
                        cpstmt.setString("owner", com.ndata.module.StringUtils.upperCase(schema));
                        ResultSet crs = dbHandler.executeSql(cpstmt);
                        while (crs.next()) {
                            Map<String, Object> row = new HashMap<>();
                            row.put("owner", schema);
                            row.put("tableNm", crs.getString("tableNm"));
                            row.put("constraintNm", crs.getString("constraintNm"));
                            row.put("constraintType", crs.getString("constraintType"));
                            row.put("columnNm", crs.getString("columnNm"));
                            row.put("columnPos", crs.getInt("columnPos"));
                            row.put("refTableNm", crs.getString("refTableNm"));
                            row.put("refColumnNm", crs.getString("refColumnNm"));
                            row.put("deleteRule", crs.getString("deleteRule"));
                            row.put("status", crs.getString("status"));
                            currConstraintRows.add(row);
                        }
                        cpstmt.close(); crs.close();
                    }
                }
                Map<String, Map<String, Object>> prevCstMap = toConstraintMap(prevConstraints);
                Map<String, Map<String, Object>> currCstMap = toConstraintMap(currConstraintRows);
                totalConstraints = currCstMap.size();

                for (Map.Entry<String, Map<String, Object>> e : currCstMap.entrySet()) {
                    if (!prevCstMap.containsKey(e.getKey())) {
                        Map<String, Object> d = new HashMap<>();
                        Map<String, Object> c = e.getValue();
                        d.put("owner", c.get("owner")); d.put("tableNm", c.get("tableNm"));
                        d.put("constraintNm", c.get("constraintNm")); d.put("changeType", "ADDED");
                        d.put("currConstraintType", c.get("constraintType"));
                        d.put("currColumns", c.get("columnsStr"));
                        d.put("currRefTable", c.get("refTableNm")); d.put("currRefColumns", c.get("refColumnsStr"));
                        d.put("currDeleteRule", c.get("deleteRule")); d.put("currStatus", c.get("status"));
                        constraintChanges.add(d); addedConstraints++;
                    }
                }
                for (Map.Entry<String, Map<String, Object>> e : prevCstMap.entrySet()) {
                    if (!currCstMap.containsKey(e.getKey())) {
                        Map<String, Object> d = new HashMap<>();
                        Map<String, Object> p = e.getValue();
                        d.put("owner", p.get("owner")); d.put("tableNm", p.get("tableNm"));
                        d.put("constraintNm", p.get("constraintNm")); d.put("changeType", "DELETED");
                        d.put("prevConstraintType", p.get("constraintType"));
                        d.put("prevColumns", p.get("columnsStr"));
                        d.put("prevRefTable", p.get("refTableNm")); d.put("prevRefColumns", p.get("refColumnsStr"));
                        d.put("prevDeleteRule", p.get("deleteRule")); d.put("prevStatus", p.get("status"));
                        constraintChanges.add(d); deletedConstraints++;
                    }
                }
                for (Map.Entry<String, Map<String, Object>> e : currCstMap.entrySet()) {
                    if (prevCstMap.containsKey(e.getKey())) {
                        Map<String, Object> p = prevCstMap.get(e.getKey());
                        Map<String, Object> c = e.getValue();
                        boolean changed = !nullSafeEquals(p.get("constraintType"), c.get("constraintType"))
                                || !nullSafeEquals(p.get("columnsStr"), c.get("columnsStr"))
                                || !nullSafeEquals(p.get("refTableNm"), c.get("refTableNm"))
                                || !nullSafeEquals(p.get("refColumnsStr"), c.get("refColumnsStr"))
                                || !nullSafeEquals(p.get("deleteRule"), c.get("deleteRule"))
                                || !nullSafeEquals(p.get("status"), c.get("status"));
                        if (changed) {
                            Map<String, Object> d = new HashMap<>();
                            d.put("owner", c.get("owner")); d.put("tableNm", c.get("tableNm"));
                            d.put("constraintNm", c.get("constraintNm")); d.put("changeType", "MODIFIED");
                            d.put("prevConstraintType", p.get("constraintType")); d.put("currConstraintType", c.get("constraintType"));
                            d.put("prevColumns", p.get("columnsStr")); d.put("currColumns", c.get("columnsStr"));
                            d.put("prevRefTable", p.get("refTableNm")); d.put("currRefTable", c.get("refTableNm"));
                            d.put("prevRefColumns", p.get("refColumnsStr")); d.put("currRefColumns", c.get("refColumnsStr"));
                            d.put("prevDeleteRule", p.get("deleteRule")); d.put("currDeleteRule", c.get("deleteRule"));
                            d.put("prevStatus", p.get("status")); d.put("currStatus", c.get("status"));
                            constraintChanges.add(d); modifiedConstraints++;
                        }
                    }
                }
                log.info(">> StructDiag: 제약조건 diff 완료 - added={}, modified={}, deleted={}", addedConstraints, modifiedConstraints, deletedConstraints);
            }

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
            historyParam.put("totalIndexes", totalIndexes);
            historyParam.put("addedIndexes", addedIndexes);
            historyParam.put("modifiedIndexes", modifiedIndexes);
            historyParam.put("deletedIndexes", deletedIndexes);
            historyParam.put("totalConstraints", totalConstraints);
            historyParam.put("addedConstraints", addedConstraints);
            historyParam.put("modifiedConstraints", modifiedConstraints);
            historyParam.put("deletedConstraints", deletedConstraints);
            sqlSessionTemplate.update("structdiag.updateStructDiagResult", historyParam);

            int seq = 1;
            for (Map<String, Object> change : changes) {
                change.put("diagId", diagId);
                change.put("seq", seq++);
                sqlSessionTemplate.insert("structdiag.insertStructDiagDetail", change);
            }
            int idxSeq = 1;
            for (Map<String, Object> ic : indexChanges) {
                ic.put("diagId", diagId);
                ic.put("seq", idxSeq++);
                sqlSessionTemplate.insert("structdiag.insertStructDiagIndexDetail", ic);
            }
            int cstSeq = 1;
            for (Map<String, Object> cc : constraintChanges) {
                cc.put("diagId", diagId);
                cc.put("seq", cstSeq++);
                sqlSessionTemplate.insert("structdiag.insertStructDiagConstraintDetail", cc);
            }

            updateStatus("DONE");

            // TB_DATA_MODEL에 구조진단 결과 반영
            int totalChanges = changes.size() + indexChanges.size() + constraintChanges.size();
            boolean isMatch = (totalChanges == 0);
            Map<String, Object> dmUpdateParam = new HashMap<>();
            dmUpdateParam.put("dataModelId", dataModelId);
            dmUpdateParam.put("structDiagYn", isMatch ? "Y" : "N");
            sqlSessionTemplate.update("structdiag.updateDataModelStructDiag", dmUpdateParam);

            log.info("[StructDiag] 완료 - diagId={}, 컬럼변경={}건, 인덱스변경={}건, 제약조건변경={}건, 일치={}",
                    diagId, changes.size(), indexChanges.size(), constraintChanges.size(), isMatch);

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
            // owner 는 prev (메타 OBJ_OWNER) 와 curr (실제 DB schema 변수) 케이스가 다를 수 있어 통일
            String owner = attr.get("owner") != null ? attr.get("owner").toString().toUpperCase() : "";
            String key = owner + "|" + attr.get("tableNm") + "|" + attr.get("columnNm");
            map.put(key, attr);
        }
        return map;
    }

    /**
     * 인덱스 행들을 owner|tableNm|indexNm 키로 그룹핑하여 columnsStr 생성
     */
    private Map<String, Map<String, Object>> toIndexMap(List<Map<String, Object>> rows) {
        // 1단계: owner|tableNm|indexNm 기준으로 행 그룹핑
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String owner = row.get("owner") != null ? row.get("owner").toString()
                    : (row.get("objOwner") != null ? row.get("objOwner").toString() : "");
            String key = owner + "|" + row.get("tableNm") + "|" + row.get("indexNm");
            if (!grouped.containsKey(key)) grouped.put(key, new ArrayList<>());
            grouped.get(key).add(row);
        }
        // 2단계: 그룹별로 columnsStr 조합
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<Map<String, Object>> cols = e.getValue();
            cols.sort((a, b) -> {
                int pa = a.get("columnPos") != null ? Integer.parseInt(a.get("columnPos").toString()) : 0;
                int pb = b.get("columnPos") != null ? Integer.parseInt(b.get("columnPos").toString()) : 0;
                return pa - pb;
            });
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> col : cols) {
                if (sb.length() > 0) sb.append(",");
                sb.append(col.get("columnNm")).append("(").append(col.get("columnPos"));
                String sort = col.get("sortOrder") != null ? col.get("sortOrder").toString() : "ASC";
                sb.append(",").append(sort).append(")");
            }
            Map<String, Object> first = cols.get(0);
            Map<String, Object> info = new HashMap<>();
            String owner = first.get("owner") != null ? first.get("owner").toString()
                    : (first.get("objOwner") != null ? first.get("objOwner").toString() : "");
            info.put("owner", owner);
            info.put("tableNm", first.get("tableNm"));
            info.put("indexNm", first.get("indexNm"));
            info.put("indexType", first.get("indexType"));
            info.put("uniqueness", first.get("uniqueness"));
            info.put("columnsStr", sb.toString());
            result.put(e.getKey(), info);
        }
        return result;
    }

    /**
     * 제약조건 행들을 owner|tableNm|constraintNm 키로 그룹핑하여 columnsStr/refColumnsStr 생성
     */
    private Map<String, Map<String, Object>> toConstraintMap(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String owner = row.get("owner") != null ? row.get("owner").toString()
                    : (row.get("objOwner") != null ? row.get("objOwner").toString() : "");
            String key = owner + "|" + row.get("tableNm") + "|" + row.get("constraintNm");
            if (!grouped.containsKey(key)) grouped.put(key, new ArrayList<>());
            grouped.get(key).add(row);
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<Map<String, Object>> cols = e.getValue();
            cols.sort((a, b) -> {
                int pa = a.get("columnPos") != null ? Integer.parseInt(a.get("columnPos").toString()) : 0;
                int pb = b.get("columnPos") != null ? Integer.parseInt(b.get("columnPos").toString()) : 0;
                return pa - pb;
            });
            StringBuilder colSb = new StringBuilder();
            StringBuilder refSb = new StringBuilder();
            for (Map<String, Object> col : cols) {
                if (colSb.length() > 0) colSb.append(",");
                colSb.append(col.get("columnNm")).append("(").append(col.get("columnPos")).append(")");
                if (col.get("refColumnNm") != null) {
                    if (refSb.length() > 0) refSb.append(",");
                    refSb.append(col.get("refColumnNm"));
                }
            }
            Map<String, Object> first = cols.get(0);
            Map<String, Object> info = new HashMap<>();
            String owner = first.get("owner") != null ? first.get("owner").toString()
                    : (first.get("objOwner") != null ? first.get("objOwner").toString() : "");
            info.put("owner", owner);
            info.put("tableNm", first.get("tableNm"));
            info.put("constraintNm", first.get("constraintNm"));
            info.put("constraintType", first.get("constraintType"));
            info.put("columnsStr", colSb.toString());
            info.put("refTableNm", first.get("refTableNm"));
            info.put("refColumnsStr", refSb.length() > 0 ? refSb.toString() : null);
            info.put("deleteRule", first.get("deleteRule"));
            info.put("status", first.get("status"));
            result.put(e.getKey(), info);
        }
        return result;
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

            // 2. 현재 모델 OBJ/ATTR 로드 (DM_ID 기반)
            //    79번 진단 제외: STRUCT_DIAG_TARGET_YN='Y' 만 prev 로 (cascade 포함)
            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectAttrListForStructDiagRaw", dataModelId);

            // 79번 진단 제외: curr 도 OFF 된 OBJ/ATTR 빼야 결과에서 완전 사라짐
            List<Map<String, Object>> offRows = sqlSessionTemplate.selectList(
                    "datamodel.selectStructDiagOffSet", dataModelId);
            Set<String> offObjs = new HashSet<>();
            Set<String> offAttrs = new HashSet<>();
            for (Map<String, Object> r : offRows) {
                String kind = String.valueOf(r.get("kind"));
                String tbl  = String.valueOf(r.get("tableNm"));
                if ("OBJ".equals(kind)) offObjs.add(tbl);
                else offAttrs.add(tbl + "|" + r.get("columnNm"));
            }

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
                    String tbl = rs.getString("objNm");
                    String col = rs.getString("attrNm");
                    if (offObjs.contains(tbl) || offAttrs.contains(tbl + "|" + col)) continue;
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("owner", schema);
                    attr.put("tableNm", tbl);
                    attr.put("columnNm", col);
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
