package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

@Tag(name = "구조진단", description = "구조 진단 (DBMS 재수집 → 스냅샷 diff) API")
@Slf4j
@RestController
@RequestMapping("/api/std/structDiag")
public class StructDiagController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/**
	 * 구조 진단 실행
	 *
	 * 프론트 흐름:
	 *   1. 데이터모델 선택 → "구조 진단 실행" 클릭
	 *   2. 프론트가 /api/dm/collectDataModel 호출 → 수집 완료 대기
	 *   3. 수집 완료 후 이 API를 호출 (dataModelId만 전달)
	 *   4. 백엔드: 최신 수집 2건을 자동으로 찾아서 diff
	 */
	@PostMapping("/execute")
	public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
		String dataModelId = (String) body.get("dataModelId");
		Map<String, Object> result = new HashMap<>();

		if (dataModelId == null || dataModelId.trim().isEmpty()) {
			result.put("success", false);
			result.put("message", "dataModelId는 필수입니다.");
			return result;
		}

		// 최신 수집 이력 2건 조회 (1번=방금 수집한 것, 2번=이전 수집)
		List<Map<String, Object>> recentClcts = sqlSessionTemplate.selectList(
				"structdiag.selectRecentClctIds", dataModelId);

		if (recentClcts == null || recentClcts.isEmpty()) {
			result.put("success", false);
			result.put("message", "수집 이력이 없습니다. 먼저 데이터 모델을 수집해주세요.");
			return result;
		}

		if (recentClcts.size() < 2) {
			result.put("success", false);
			result.put("message", "비교할 이전 수집 이력이 없습니다. 수집이 2회 이상 필요합니다.");
			return result;
		}

		String currClctId = (String) recentClcts.get(0).get("clctId");
		String prevClctId = (String) recentClcts.get(1).get("clctId");
		String prevCollectDt = (String) recentClcts.get(1).get("clctEndDt");

		String userId = sessionService.getUserId();
		String diagId = StringUtils.getUUID();

		// 이전 스냅샷 조회
		List<Map<String, Object>> prevAttrs = sqlSessionTemplate.selectList(
				"datamodel.selectDataModelAttrListByClctIdRaw", prevClctId);

		// 현재(방금 수집) 스냅샷 조회
		List<Map<String, Object>> currAttrs = sqlSessionTemplate.selectList(
				"datamodel.selectDataModelAttrListByClctIdRaw", currClctId);

		// Diff 계산
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

		// DB 저장
		Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
		String dsId = dmInfo != null ? (String) dmInfo.get("dataModelDsId") : null;

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

		int seq = 1;
		for (Map<String, Object> change : changes) {
			change.put("diagId", diagId);
			change.put("seq", seq++);
			sqlSessionTemplate.insert("structdiag.insertStructDiagDetail", change);
		}

		log.info("[StructDiag] 완료 - diagId={}, prev={}, curr={}, 변경={}건",
				diagId, prevClctId, currClctId, changes.size());

		result.put("success", true);
		result.put("diagId", diagId);
		result.put("totalChanges", changes.size());
		result.put("totalTables", currTableSet.size());
		result.put("totalColumns", currAttrs.size());
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
	public Map<String, Object> getResult(@PathVariable String diagId) {
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
