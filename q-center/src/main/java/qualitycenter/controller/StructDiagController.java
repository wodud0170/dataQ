package qualitycenter.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.service.ExcelDownloadService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 구조 진단 컨트롤러 (수집 스냅샷 vs 현재 DBMS 스키마 비교)
 *
 * <p>수집된 데이터모델 스냅샷과 실제 DBMS의 현재 스키마를 비교하여
 * 테이블/컬럼의 추가/삭제/변경을 감지한다. 진단 실행은 q-executor에서 처리.</p>
 */
@Tag(name = "구조진단", description = "구조 진단 (DBMS 재수집 → 스냅샷 diff) API")
@Slf4j
@RestController
@RequestMapping("/api/std/structDiag")
public class StructDiagController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private ExcelDownloadService excelDownloadService;

	/** 104번 WP8-63 — 결과를 모델에 반영할 때 테이블 추가·컬럼 저장·테이블 삭제를 화면과 같은 경로로 */
	@Autowired
	private DataModelController dataModelController;

	/**
	 * 104번 WP8-63 — 구조 진단 결과(컬럼)를 모델에 반영 {diagId, seqs:[..]}.
	 *
	 * <p>테이블별로 묶어 컬럼 저장(saveAttrs)과 같은 경로로 반영한다 — 변경 이력·결재(관리자 바로, 사용자 승인 대기)를 그대로 거친다.
	 * 추가: 모델에 테이블이 없으면 테이블부터 추가하고, 실 DB 값(코멘트 → 한글명·타입·길이·소수·NULL·PK·기본값)으로 컬럼 추가.
	 * 변경: 모델 컬럼의 타입·길이·소수·NULL 만 실 DB 값으로. 삭제: 모델 컬럼 삭제 — 테이블의 컬럼을 모두 지우게 되면 테이블째 삭제.
	 * 표준 준수 필수 모델의 비표준 컬럼은 사유 "구조 진단 현행화"로 표준 예외 신청이 된다.
	 * 반영한 행은 APPLIED_DT 로 표시한다. 테이블 하나가 실패해도 나머지 테이블은 반영한다.
	 * 109번 #23 — 반영할 수 있는지는 APPLIED_DT 가 아니라 지금 모델로 판정한다({@link #notApplicable}) — 모델의 가장 최근 완료 진단이고,
	 * 그 컬럼의 모델 값이 진단 때(이전 값)와 같아야 한다. 승인 대기로 반영한 것은 모델 행이 이미 바뀌어 다시 반영되지 않고,
	 * 그 변경이 반려되면 모델이 진단 때로 돌아가 다시 반영할 수 있다. 진단 뒤 모델을 고쳤으면 반영하지 않는다(진단을 다시 한다).</p>
	 */
	@PostMapping("/apply")
	public Mono<Response> apply(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			String diagId = (String) body.get("diagId");
			Set<Integer> seqs = new HashSet<>();
			if (body.get("seqs") instanceof List) {
				for (Object o : (List<?>) body.get("seqs")) seqs.add(Integer.valueOf(o.toString()));
			}
			if (diagId == null || seqs.isEmpty()) throw new IllegalArgumentException("반영할 행을 고르세요.");
			Map<String, Object> history = sqlSessionTemplate.selectOne("structdiag.selectStructDiagHistoryById", diagId);
			if (history == null) throw new IllegalArgumentException("구조 진단 결과를 찾을 수 없습니다.");
			String dmId = (String) history.get("dataModelId");
			if (!diagId.equals(sqlSessionTemplate.selectOne("structdiag.selectLatestDoneDiagId", dmId)))
				throw new IllegalArgumentException(NOT_LATEST);

			Map<String, Map<String, Object>> model = modelAttrs(dmId);
			Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();   // owner|table → 행
			for (Map<String, Object> d : sqlSessionTemplate.<Map<String, Object>>selectList("structdiag.selectStructDiagDetailList", diagId)) {
				if (!seqs.contains(((Number) d.get("seq")).intValue())) continue;
				groups.computeIfAbsent(d.get("owner") + "|" + d.get("tableNm"), k -> new ArrayList<>()).add(d);
			}
			if (groups.isEmpty()) throw new IllegalArgumentException("반영할 행이 없습니다.");

			List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByDmId", dmId);
			int applied = 0;
			List<Map<String, Object>> errors = new ArrayList<>();
			for (List<Map<String, Object>> rows : groups.values()) {
				String owner = (String) rows.get(0).get("owner");
				String tbl = (String) rows.get(0).get("tableNm");
				try {
					// 109번 #23 — 지금 모델에 반영할 수 없는 행이 있으면 그 테이블은 반영하지 않는다
					List<String> stale = new ArrayList<>();
					for (Map<String, Object> r : rows) {
						String why = notApplicable(r, model);
						if (why != null) stale.add(r.get("columnNm") + " — " + why);
					}
					if (!stale.isEmpty()) throw new IllegalStateException(String.join(" / ", stale));
					applyTable(dmId, owner == null ? "" : owner, tbl, rows, objs);
					List<Object> done = new ArrayList<>();
					for (Map<String, Object> r : rows) done.add(r.get("seq"));
					Map<String, Object> mp = new HashMap<>();
					mp.put("diagId", diagId);
					mp.put("seqs", done);
					mp.put("userId", sessionService.getUserId());
					sqlSessionTemplate.update("structdiag.markStructDiagApplied", mp);
					applied += rows.size();
				} catch (Exception e) {
					Map<String, Object> err = new HashMap<>();
					err.put("tableNm", tbl);
					err.put("message", e.getMessage());
					errors.add(err);
				}
			}
			Map<String, Object> data = new HashMap<>();
			data.put("applied", applied);
			data.put("errors", errors);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new ObjectMapper().writeValueAsString(data));
		} catch (Exception e) {
			log.error(">> structDiag apply failed: {}", e.getMessage());
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 테이블 하나 반영 — 실패하면 던진다 (메시지는 화면에 그대로) */
	private void applyTable(String dmId, String owner, String tbl, List<Map<String, Object>> rows, List<StdDataModelObjVo> objs) {
		// 수집·진단 쪽 소유자는 대소문자가 다를 수 있다 (진단 비교도 대문자로 맞춘다)
		StdDataModelObjVo obj = null;
		for (StdDataModelObjVo o : objs) {
			if (tbl.equals(o.getObjNm()) && (o.getObjOwner() == null ? "" : o.getObjOwner()).equalsIgnoreCase(owner)) { obj = o; break; }
		}
		List<Map<String, Object>> sorted = new ArrayList<>(rows);
		sorted.sort(Comparator.comparingInt(r -> r.get("currAttrOrd") == null ? Integer.MAX_VALUE : ((Number) r.get("currAttrOrd")).intValue()));
		int delCnt = 0;
		for (Map<String, Object> r : sorted) if ("DELETED".equals(r.get("changeType"))) delCnt++;

		String objOwner;
		if (obj == null) {
			if (delCnt > 0 || sorted.stream().anyMatch(r -> "MODIFIED".equals(r.get("changeType"))))
				throw new IllegalStateException("모델에 테이블이 없습니다: " + tbl);
			StdDataModelObjVo vo = new StdDataModelObjVo();
			vo.setDataModelId(dmId);
			vo.setObjOwner(owner);
			vo.setObjNm(tbl);
			check(dataModelController.addObj(vo).block());
			objOwner = owner;
		} else {
			objOwner = obj.getObjOwner() == null ? "" : obj.getObjOwner();
			if (delCnt == sorted.size()) {
				Map<String, Object> cp = new HashMap<>();
				cp.put("dataModelId", dmId);
				cp.put("objOwner", objOwner);
				cp.put("objNm", tbl);
				Integer modelCnt = sqlSessionTemplate.selectOne("structdiag.countModelAttrsOfObj", cp);
				if (modelCnt != null && delCnt >= modelCnt) {
					// 실 DB 에서 테이블이 없어졌다 — 테이블째 삭제 (컬럼·인덱스·제약조건 함께)
					Map<String, Object> item = new HashMap<>();
					item.put("objOwner", objOwner);
					item.put("objNm", tbl);
					Map<String, Object> db = new HashMap<>();
					db.put("dataModelId", dmId);
					db.put("items", Collections.singletonList(item));
					check(dataModelController.deleteObjs(db).block());
					return;
				}
			}
		}

		List<Map<String, Object>> attrs = new ArrayList<>();
		for (Map<String, Object> r : sorted) {
			String ct = (String) r.get("changeType");
			Map<String, Object> a = new HashMap<>();
			a.put("stndExceptReason", "구조 진단 현행화");
			if ("ADDED".equals(ct)) {
				a.put("mode", "ADD");
				a.put("attrNm", r.get("columnNm"));
				a.put("attrNmKr", r.get("currAttrNmKr"));
				a.put("dataType", r.get("currDataType"));
				a.put("dataLen", r.get("currDataLen"));
				a.put("dataDecimalLen", r.get("currDataDecimalLen"));
				a.put("nullableYn", r.get("currNullable"));
				a.put("pkYn", r.get("currPkYn"));
				a.put("defaultVal", r.get("currDefaultVal"));
			} else if ("MODIFIED".equals(ct)) {
				Map<String, Object> sel = new HashMap<>();
				sel.put("dataModelId", dmId);
				sel.put("objOwner", objOwner);
				sel.put("objNm", tbl);
				sel.put("attrNm", r.get("columnNm"));
				StdDataModelAttrVo ex = sqlSessionTemplate.selectOne("datamodel.selectDataModelAttrOne", sel);
				if (ex == null) throw new IllegalStateException("모델에 컬럼이 없습니다: " + tbl + "." + r.get("columnNm"));
				// 나머지(한글명·PK·FK·기본값)는 모델 값 그대로 — saveAttrs UPDATE 는 보낸 값으로 덮어쓴다
				a.put("mode", "UPDATE");
				a.put("origAttrNm", ex.getAttrNm());
				a.put("attrNm", ex.getAttrNm());
				a.put("attrNmKr", ex.getAttrNmKr());
				a.put("pkYn", ex.getPkYn());
				a.put("fkYn", ex.getFkYn());
				a.put("defaultVal", ex.getDefaultVal());
				a.put("nullableYn", r.get("currNullable"));
				a.put("dataType", r.get("currDataType"));
				a.put("dataLen", r.get("currDataLen"));
				a.put("dataDecimalLen", r.get("currDataDecimalLen") != null ? r.get("currDataDecimalLen") : ex.getDataDecimalLen());
			} else {
				a.put("mode", "DELETE");
				a.put("attrNm", r.get("columnNm"));
			}
			attrs.add(a);
		}
		Map<String, Object> sb = new HashMap<>();
		sb.put("dataModelId", dmId);
		sb.put("objOwner", objOwner);
		sb.put("objNm", tbl);
		sb.put("attrs", attrs);
		sb.put("keepNmCase", Boolean.TRUE);
		check(dataModelController.saveAttrs(sb).block());
	}

	private static void check(Response r) {
		if (r.getResultCode() != RestResult.CODE_200.getCode()) throw new IllegalStateException(r.getResultMessage());
	}

	private static final String NOT_LATEST = "더 최근 구조 진단 결과가 있습니다 — 최근 결과에서 반영하세요.";

	/** 109번 #23 — 지금 모델의 사용 중 컬럼 (대문자 소유자|테이블|컬럼 → 타입·길이·NULL) */
	private Map<String, Map<String, Object>> modelAttrs(String dmId) {
		Map<String, Map<String, Object>> m = new HashMap<>();
		for (Map<String, Object> a : sqlSessionTemplate.<Map<String, Object>>selectList("structdiag.selectModelAttrsForApply", dmId)) {
			m.put(a.get("owner") + "|" + a.get("tableNm") + "|" + a.get("columnNm"), a);
		}
		return m;
	}

	/**
	 * 109번 #23 — 진단 결과 한 행을 지금 모델에 반영할 수 없는 이유 (반영할 수 있으면 null).
	 * 추가는 모델에 그 컬럼이 없어야, 변경·삭제는 모델 컬럼의 타입·길이·NULL 이 진단 때 값(이전 값)과 같아야 한다.
	 */
	private static String notApplicable(Map<String, Object> d, Map<String, Map<String, Object>> model) {
		String owner = d.get("owner") == null ? "" : d.get("owner").toString().toUpperCase();
		Map<String, Object> m = model.get(owner + "|" + d.get("tableNm") + "|" + d.get("columnNm"));
		if ("ADDED".equals(d.get("changeType"))) {
			return m == null ? null : "모델에 이미 있는 컬럼입니다 (반영했거나 진단 뒤 모델이 바뀜)";
		}
		if (m == null) return "모델에 없는 컬럼입니다 (반영했거나 진단 뒤 모델이 바뀜)";
		if (!Objects.equals(str(m.get("dataType")), str(d.get("prevDataType"))) || !Objects.equals(str(m.get("dataLen")), str(d.get("prevDataLen")))
				|| !Objects.equals(str(m.get("nullableYn")), str(d.get("prevNullable")))) {
			return "진단 뒤 모델 컬럼이 바뀌었습니다 (반영했거나 직접 수정함) — 구조 진단을 다시 하세요";
		}
		return null;
	}

	private static String str(Object o) {
		return o == null ? null : o.toString().trim();
	}

	/**
	 * 구조 진단 실행
	 *
	 * 프론트 흐름:
	 *   1. 데이터모델 선택 → "구조 진단 실행" 클릭
	 *   2. 프론트가 /api/dm/collectDataModel 호출 → 수집 완료 대기
	 *   3. 수집 완료 후 이 API를 호출 (dataModelId만 전달)
	 *   4. q-executor에 백그라운드 실행 요청
	 */
	@PostMapping("/execute")
	public Mono<Response> execute(@RequestBody Map<String, Object> body) {
		String dataModelId = (String) body.get("dataModelId");
		Response result = new Response();

		if (dataModelId == null || dataModelId.trim().isEmpty()) {
			result.setResultInfo(RestResult.CODE_500.getCode(), "dataModelId는 필수입니다.");
			return Mono.just(result);
		}

		try {
			// 논리 모델(dsId 없음) 거부: 구조 변경 진단은 실 DBMS 스키마 비교라 데이터소스 필수
			Map<String, Object> dmInfo = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
			if (dmInfo == null) {
				result.setResultInfo(404, "데이터모델 없음: " + dataModelId);
				return Mono.just(result);
			}
			String preDsId = (String) dmInfo.get("dataModelDsId");
			if (preDsId == null || preDsId.trim().isEmpty()) {
				result.setResultInfo(400, "[CONFIG] 연결된 데이터소스가 없는 논리 모델은 구조 변경 진단 대상이 아닙니다.");
				return Mono.just(result);
			}

			// 중복 진단 체크: 동일 데이터모델에 READY/RUNNING 상태 진단이 있으면 거부
			int activeCount = sqlSessionTemplate.selectOne("structdiag.selectActiveStructDiagCount", dataModelId);
			if (activeCount > 0) {
				result.setResultInfo(409, "이미 진행 중인 구조 진단이 있습니다.");
				return Mono.just(result);
			}

			String diagId = StringUtils.getUUID();
			String userId = sessionService.getUserId();

			// DB에 이력 레코드 생성 (READY 상태)
			Map<String, Object> historyParam = new HashMap<>();
			historyParam.put("diagId", diagId);
			historyParam.put("dataModelId", dataModelId);
			historyParam.put("status", "READY");
			historyParam.put("cretUserId", userId);
			sqlSessionTemplate.insert("structdiag.insertStructDiagHistory", historyParam);

			// q-executor에 실행 요청 (110 6.10-27c — 진단은 늘 모델의 지금 컬럼과 실 DB 를 비교한다, 수집건 지정 없음)
			Map<String, String> params = new HashMap<>();
			params.put("diagId", diagId);
			params.put("dataModelId", dataModelId);
			params.put("userId", userId);

			WebClientHandler webClientHandler = new WebClientHandler(
					NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/structDiag/run");
			return webClientHandler.postData(sessionService.getUserId(), null, params)
				.map(res -> {
					// 110 6.10-5b — executor 가 기동을 거부하면 이력을 ERROR 로 (READY 로 남으면 다음 실행이 늘 409)
					if (res.getResultCode() != RestResult.CODE_200.getCode()) {
						markStructDiagError(diagId);
						return res;
					}
					res.setContents(diagId);
					return res;
				})
				.onErrorResume(e -> {
					// 110 6.10-5a — executor 호출 자체가 실패해도 같게 (표준 진단 startDiag 와 같은 방식)
					log.error(">> executor 호출 실패, 구조 진단 이력을 ERROR로 변경: {}", diagId, e);
					markStructDiagError(diagId);
					result.setResultInfo(RestResult.CODE_500.getCode(), "진단 서버 연결 실패: " + e.getMessage());
					return Mono.just(result);
				});
		} catch (Exception e) {
			log.error(">> structDiag execute failed", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return Mono.just(result);
		}
	}

	private void markStructDiagError(String diagId) {
		Map<String, Object> p = new HashMap<>();
		p.put("diagId", diagId);
		p.put("status", "ERROR");
		sqlSessionTemplate.update("structdiag.updateStructDiagStatus", p);
	}

	/** 진단 상태 조회 (폴링용) */
	@GetMapping("/status/{diagId}")
	public Map<String, Object> getDiagStatus(@PathVariable String diagId) {
		Map<String, Object> history = sqlSessionTemplate.selectOne("structdiag.selectStructDiagHistoryById", diagId);
		if (history == null) {
			Map<String, Object> empty = new HashMap<>();
			empty.put("status", "NONE");
			return empty;
		}
		return history;
	}

	/** 진단 이력 목록 */
	@GetMapping("/history")
	public List<Map<String, Object>> history() {
		return sqlSessionTemplate.selectList("structdiag.selectStructDiagHistoryList");
	}

	/** 88번 — 대시보드용: 특정 모델의 최신 구조 진단 결과 (요약 + 변경 상세) */
	@GetMapping("/latestByModel")
	public Map<String, Object> latestByModel(@RequestParam String dataModelId) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> history = sqlSessionTemplate.selectOne("structdiag.selectLatestStructDiagByModel", dataModelId);
		if (history == null) {
			result.put("history", null);
			result.put("details", new java.util.ArrayList<>());
			result.put("indexDetails", new java.util.ArrayList<>());
			result.put("constraintDetails", new java.util.ArrayList<>());
			return result;
		}
		String diagId = (String) history.get("diagId");
		result.put("history",            history);
		result.put("details",            sqlSessionTemplate.selectList("structdiag.selectStructDiagDetailList", diagId));
		result.put("indexDetails",       sqlSessionTemplate.selectList("structdiag.selectStructDiagIndexDetailList", diagId));
		result.put("constraintDetails",  sqlSessionTemplate.selectList("structdiag.selectStructDiagConstraintDetailList", diagId));
		return result;
	}

	/** 특정 진단 결과 (요약 + 상세: 컬럼/인덱스/제약조건) */
	@GetMapping("/result/{diagId}")
	public Map<String, Object> getResult(@PathVariable String diagId) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> history = sqlSessionTemplate.selectOne("structdiag.selectStructDiagHistoryById", diagId);
		List<Map<String, Object>> details = sqlSessionTemplate.selectList("structdiag.selectStructDiagDetailList", diagId);
		// 109번 #23 — 행마다 지금 모델에 반영할 수 있는지 (화면은 반영할 수 있는 행만 고르게 하고, 못 하는 이유를 보인다)
		if (history != null && !details.isEmpty()) {
			String dmId = (String) history.get("dataModelId");
			boolean latest = diagId.equals(sqlSessionTemplate.selectOne("structdiag.selectLatestDoneDiagId", dmId));
			Map<String, Map<String, Object>> model = latest ? modelAttrs(dmId) : null;
			for (Map<String, Object> d : details) {
				String why = latest ? notApplicable(d, model) : NOT_LATEST;
				d.put("applicable", why == null);
				d.put("notApplicableReason", why);
			}
		}
		List<Map<String, Object>> indexDetails = sqlSessionTemplate.selectList("structdiag.selectStructDiagIndexDetailList", diagId);
		List<Map<String, Object>> constraintDetails = sqlSessionTemplate.selectList("structdiag.selectStructDiagConstraintDetailList", diagId);
		result.put("history", history);
		result.put("details", details);
		result.put("indexDetails", indexDetails);
		result.put("constraintDetails", constraintDetails);
		return result;
	}

	/** 구조 진단 결과 엑셀 다운로드 */
	@GetMapping("/downloadResult")
	public void downloadResult(HttpServletRequest request, HttpServletResponse response, String diagId) {
		log.info(">> download struct diag result excel: diagId={}", diagId);
		try {
			excelDownloadService.getStructDiagResultExcel(diagId, request, response);
		} catch (Exception e) {
			log.error(">> download struct diag result excel failed: {}", e.getMessage());
		}
	}

	/** 구조 진단 인덱스 변경 결과 엑셀 다운로드 */
	@GetMapping("/downloadIndexResult")
	public void downloadIndexResult(HttpServletRequest request, HttpServletResponse response, String diagId) {
		try {
			excelDownloadService.getStructDiagIndexResultExcel(diagId, request, response);
		} catch (Exception e) {
			log.error(">> download struct diag index result excel failed: {}", e.getMessage());
		}
	}

	/** 구조 진단 제약조건 변경 결과 엑셀 다운로드 */
	@GetMapping("/downloadConstraintResult")
	public void downloadConstraintResult(HttpServletRequest request, HttpServletResponse response, String diagId) {
		try {
			excelDownloadService.getStructDiagConstraintResultExcel(diagId, request, response);
		} catch (Exception e) {
			log.error(">> download struct diag constraint result excel failed: {}", e.getMessage());
		}
	}
}
