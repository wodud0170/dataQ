package qualityexecutor.service.std;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
@Service
public class StructDiagService implements Runnable {

    private String diagId;
    private String dataModelId;
    private String userId;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    public StructDiagService(String diagId, String dataModelId, String userId) {
        this.diagId = diagId;
        this.dataModelId = dataModelId;
        this.userId = userId;
    }

    @Override
    public void run() {
        log.info(">> StructDiagService started: diagId={}, dataModelId={}", diagId, dataModelId);

        try {
            // 1. 상태 RUNNING으로 변경
            updateStatus("RUNNING");

            // 2. 최신 수집 이력 2건 조회
            List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
                    "structdiag.selectRecentClctIds", dataModelId);

            if (recentClcts == null || recentClcts.isEmpty()) {
                log.warn(">> StructDiag: 수집 이력 없음 diagId={}", diagId);
                updateStatus("ERROR");
                return;
            }

            if (recentClcts.size() < 2) {
                log.warn(">> StructDiag: 비교할 이전 수집 이력 없음 diagId={}", diagId);
                updateStatus("ERROR");
                return;
            }

            String currClctId = (String) recentClcts.get(0).get("clctId");
            String prevClctId = (String) recentClcts.get(1).get("clctId");
            String prevCollectDt = (String) recentClcts.get(1).get("clctEndDt");

            // 3. 이전/현재 스냅샷 조회
            List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", prevClctId);
            List<Map<String, Object>> currAttrs = sqlSessionTemplate.selectList(
                    "datamodel.selectDataModelAttrListByClctIdRaw", currClctId);

            // 4. Diff 계산
            Map<String, Map<String, Object>> prevMap = toAttrMap(prevAttrs);
            Map<String, Map<String, Object>> currMap = toAttrMap(currAttrs);

            List<Map<String, Object>> changes = new ArrayList<>();
            int addedTables = 0, addedColumns = 0, modifiedColumns = 0;
            int deletedTables = 0, deletedColumns = 0;

            Set<String> prevTableSet = new HashSet<>();
            Set<String> currTableSet = new HashSet<>();
            for (String key : prevMap.keySet()) prevTableSet.add(key.split("\\|")[0]);
            for (String key : currMap.keySet()) currTableSet.add(key.split("\\|")[0]);

            // ADDED
            for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
                if (!prevMap.containsKey(entry.getKey())) {
                    Map<String, Object> detail = new HashMap<>();
                    Map<String, Object> curr = entry.getValue();
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

            // DELETED
            for (Map.Entry<String, Map<String, Object>> entry : prevMap.entrySet()) {
                if (!currMap.containsKey(entry.getKey())) {
                    Map<String, Object> detail = new HashMap<>();
                    Map<String, Object> prev = entry.getValue();
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

            // MODIFIED
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

            // 5. 데이터모델 정보 조회
            Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
            String dsId = dmInfo != null ? (String) dmInfo.get("dataModelDsId") : null;

            // 6. 결과 요약 업데이트
            Map<String, Object> historyParam = new HashMap<>();
            historyParam.put("diagId", diagId);
            historyParam.put("dsId", dsId);
            historyParam.put("prevCollectDt", prevCollectDt);
            historyParam.put("totalTables", currTableSet.size());
            historyParam.put("totalColumns", currAttrs.size());
            historyParam.put("addedTables", addedTables);
            historyParam.put("addedColumns", addedColumns);
            historyParam.put("modifiedColumns", modifiedColumns);
            historyParam.put("deletedTables", deletedTables);
            historyParam.put("deletedColumns", deletedColumns);
            sqlSessionTemplate.update("structdiag.updateStructDiagResult", historyParam);

            // 7. 상세 결과 저장
            int seq = 1;
            for (Map<String, Object> change : changes) {
                change.put("diagId", diagId);
                change.put("seq", seq++);
                sqlSessionTemplate.insert("structdiag.insertStructDiagDetail", change);
            }

            // 8. 완료 상태
            updateStatus("DONE");

            log.info("[StructDiag] 완료 - diagId={}, prev={}, curr={}, 변경={}건",
                    diagId, prevClctId, currClctId, changes.size());

        } catch (Exception e) {
            log.error(">> StructDiagService error: diagId={}", diagId, e);
            updateStatus("ERROR");
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
