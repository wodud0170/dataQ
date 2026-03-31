package qualityexecutor.service.std;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.ndata.bean.DataSourceVo;
import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.datasource.dbms.handler.NamedParamStatement;
import com.ndata.quality.model.std.StdDataModelSchemaVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class StructDiagService implements Runnable {

    private String diagId;
    private String dataModelId;
    private String userId;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private DataSourceUtils dataSourceUtils;

    public StructDiagService(String diagId, String dataModelId, String userId) {
        this.diagId = diagId;
        this.dataModelId = dataModelId;
        this.userId = userId;
    }

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

            // 2. 최신 수집 스냅샷 조회 (PREV = 우리가 알고 있는 스키마)
            List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
                    "structdiag.selectRecentClctIds", dataModelId);
            if (recentClcts == null || recentClcts.isEmpty()) {
                log.warn(">> StructDiag: 수집 이력 없음 diagId={}", diagId);
                updateStatus("ERROR");
                return;
            }
            String latestClctId = (String) recentClcts.get(0).get("clctId");
            String latestClctDt = (String) recentClcts.get(0).get("clctEndDt");

            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", latestClctId);
            log.info(">> StructDiag: 수집 스냅샷 {} 컬럼 로드 (clctId={})", prevAttrs.size(), latestClctId);

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
            historyParam.put("prevCollectDt", latestClctDt);
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
            log.info("[StructDiag] 완료 - diagId={}, 수집스냅샷({}) vs 실제DB, 변경={}건 (추가T:{}/C:{}, 수정C:{}, 삭제T:{}/C:{})",
                    diagId, latestClctId, changes.size(), addedTables, addedColumns, modifiedColumns, deletedTables, deletedColumns);

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

    private boolean nullSafeEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }
}
