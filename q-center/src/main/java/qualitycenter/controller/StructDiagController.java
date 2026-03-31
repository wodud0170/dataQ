package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.module.StringUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

@Tag(name = "구조진단", description = "구조 진단 (DBMS 스냅샷 diff) API")
@Slf4j
@RestController
@RequestMapping("/api/std/structDiag")
public class StructDiagController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/**
	 * 구조 진단 실행: 이전 스냅샷 vs 현재 수집 결과 diff
	 *
	 * 프론트에서 수집 완료 후 호출하거나, 여기서 직접 스냅샷 비교만 수행.
	 * (실제 재수집은 기존 /api/dm/collectDataModel을 먼저 호출한 뒤, 이 API를 호출)
	 */
	@PostMapping("/execute")
	public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
		String dataModelId = (String) body.get("dataModelId");
		String prevClctId = (String) body.get("prevClctId");
		String currClctId = (String) body.get("currClctId");

		Map<String, Object> result = new HashMap<>();

		if (dataModelId == null || prevClctId == null || currClctId == null) {
			result.put("success", false);
			result.put("message", "dataModelId, prevClctId, currClctId는 필수입니다.");
			return result;
		}

		String userId = sessionService.getUserId();
		String diagId = StringUtils.getUUID();

		// 1. 이전 스냅샷 조회
		List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
				"datamodel.selectDataModelAttrListByClctIdRaw", prevClctId);

		// 2. 현재 스냅샷 조회
		List<Map<String, Object>> currAttrs = sqlSessionTemplate.selectList(
				"datamodel.selectDataModelAttrListByClctIdRaw", currClctId);

		// 3. Map으로 변환 (key: tableNm + "|" + columnNm)
		Map<String, Map<String, Object>> prevMap = toAttrMap(prevAttrs);
		Map<String, Map<String, Object>> currMap = toAttrMap(currAttrs);

		// 4. Diff 계산
		List<Map<String, Object>> changes = new ArrayList<>();
		int addedTables = 0, addedColumns = 0, modifiedColumns = 0;
		int deletedTables = 0, deletedColumns = 0;

		// 이전 테이블 목록 / 현재 테이블 목록
		java.util.Set<String> prevTableSet = new java.util.HashSet<>();
		java.util.Set<String> currTableSet = new java.util.HashSet<>();
		for (String key : prevMap.keySet()) prevTableSet.add(key.split("\\|")[0]);
		for (String key : currMap.keySet()) currTableSet.add(key.split("\\|")[0]);

		// ADDED: currMap에만 있는 것
		for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
			if (!prevMap.containsKey(entry.getKey())) {
				Map<String, Object> detail = new HashMap<>();
				Map<String, Object> curr = entry.getValue();
				String tableNm = (String) curr.get("tableNm");
				detail.put("tableNm", tableNm);
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

		// DELETED: prevMap에만 있는 것
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

		// 테이블 단위 추가/삭제 카운트
		for (String t : currTableSet) { if (!prevTableSet.contains(t)) addedTables++; }
		for (String t : prevTableSet) { if (!currTableSet.contains(t)) deletedTables++; }

		// 5. DB 저장
		// 데이터소스 ID 조회
		Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
		String dsId = dmInfo != null ? (String) dmInfo.get("dataModelDsId") : null;

		// 이전 수집 일시 조회
		Map<String, Object> prevClctInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelClctById", prevClctId);
		String prevCollectDt = prevClctInfo != null ? (String) prevClctInfo.get("clctEndDt") : null;

		Map<String, Object> historyParam = new HashMap<>();
		historyParam.put("diagId", diagId);
		historyParam.put("dsId", dsId);
		historyParam.put("schemaNm", body.get("schemaNm"));
		historyParam.put("prevCollectDt", prevCollectDt);
		historyParam.put("totalTables", currTableSet.size());
		historyParam.put("totalColumns", currAttrs.size());
		historyParam.put("addedTables", addedTables);
		historyParam.put("addedColumns", addedColumns);
		historyParam.put("modifiedColumns", modifiedColumns);
		historyParam.put("deletedTables", deletedTables);
		historyParam.put("deletedColumns", deletedColumns);
		historyParam.put("cretUserId", userId);

		sqlSessionTemplate.insert("structdiag.insertStructDiagHistory", historyParam);

		// 상세 저장
		int seq = 1;
		for (Map<String, Object> change : changes) {
			change.put("diagId", diagId);
			change.put("seq", seq++);
			sqlSessionTemplate.insert("structdiag.insertStructDiagDetail", change);
		}

		log.info("[StructDiag] 완료 - diagId={}, 변경사항={}건 (추가T:{}/C:{}, 수정C:{}, 삭제T:{}/C:{})",
				diagId, changes.size(), addedTables, addedColumns, modifiedColumns, deletedTables, deletedColumns);

		result.put("success", true);
		result.put("diagId", diagId);
		result.put("totalChanges", changes.size());
		result.put("addedTables", addedTables);
		result.put("addedColumns", addedColumns);
		result.put("modifiedColumns", modifiedColumns);
		result.put("deletedTables", deletedTables);
		result.put("deletedColumns", deletedColumns);
		return result;
	}

	/** 진단 이력 목록 */
	@GetMapping("/history")
	public List<Map<String, Object>> history() {
		return sqlSessionTemplate.selectList("structdiag.selectStructDiagHistoryList");
	}

	/** 특정 진단 결과 (요약 + 상세) */
	@GetMapping("/result/{diagId}")
	public Map<String, Object> result(@PathVariable String diagId) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> history = sqlSessionTemplate.selectOne("structdiag.selectStructDiagHistoryById", diagId);
		List<Map<String, Object>> details = sqlSessionTemplate.selectList("structdiag.selectStructDiagDetailList", diagId);
		result.put("history", history);
		result.put("details", details);
		return result;
	}

	/** 스키마별 최신 구조 진단 상태 (뱃지용) */
	@GetMapping("/status")
	public Map<String, Object> status(@RequestParam String dsId, @RequestParam(required = false) String schemaNm) {
		Map<String, Object> param = new HashMap<>();
		param.put("dsId", dsId);
		param.put("schemaNm", schemaNm);
		Map<String, Object> latest = sqlSessionTemplate.selectOne("structdiag.selectLatestStructDiagStatus", param);
		if (latest == null) {
			Map<String, Object> empty = new HashMap<>();
			empty.put("status", "NONE");
			return empty;
		}
		return latest;
	}

	// ========== 헬퍼 ==========

	private Map<String, Map<String, Object>> toAttrMap(List<Map<String, Object>> attrs) {
		Map<String, Map<String, Object>> map = new java.util.LinkedHashMap<>();
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
