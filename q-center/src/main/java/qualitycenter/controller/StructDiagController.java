package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import com.ndata.common.handler.WebClientHandler;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
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
	public Mono<Response> execute(HttpServletRequest request, @RequestBody Map<String, Object> body) {
		String dataModelId = (String) body.get("dataModelId");
		String clctId = (String) body.get("clctId"); // 선택적: 특정 수집건 지정. 없으면 최신
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

			// q-executor에 실행 요청
			Map<String, String> params = new HashMap<>();
			params.put("diagId", diagId);
			params.put("dataModelId", dataModelId);
			params.put("userId", userId);
			if (clctId != null && !clctId.trim().isEmpty()) {
				params.put("clctId", clctId);
			}

			WebClientHandler webClientHandler = new WebClientHandler(
					NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/structDiag/run");
			return webClientHandler.postData(
					sessionService.getUserId(),
					Objects.toString(request.getSession().getAttribute("SSID"), null),
					params)
				.map(res -> {
					res.setContents(diagId);
					return res;
				});
		} catch (Exception e) {
			log.error(">> structDiag execute failed", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return Mono.just(result);
		}
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
		List<Map<String, Object>> indexDetails = sqlSessionTemplate.selectList("structdiag.selectStructDiagIndexDetailList", diagId);
		List<Map<String, Object>> constraintDetails = sqlSessionTemplate.selectList("structdiag.selectStructDiagConstraintDetailList", diagId);
		result.put("history", history);
		result.put("details", details);
		result.put("indexDetails", indexDetails);
		result.put("constraintDetails", constraintDetails);
		return result;
	}

	/**
	 * 스키마 비교 실행 (수집 스냅샷 vs 현재 DBMS)
	 * q-executor에 동기 요청하여 결과를 프론트에 반환
	 */
	@PostMapping("/compareSchema")
	public Mono<Response> compareSchema(HttpServletRequest request, @RequestBody Map<String, Object> body) {
		String dataModelId = (String) body.get("dataModelId");
		String clctId = (String) body.get("clctId");
		Response result = new Response();

		if (dataModelId == null || dataModelId.trim().isEmpty()) {
			result.setResultInfo(RestResult.CODE_500.getCode(), "dataModelId는 필수입니다.");
			return Mono.just(result);
		}

		try {
			Map<String, String> params = new HashMap<>();
			params.put("dataModelId", dataModelId);
			if (clctId != null && !clctId.trim().isEmpty()) {
				params.put("clctId", clctId);
			}

			WebClientHandler webClientHandler = new WebClientHandler(
					NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/structDiag/compareSchema");
			return webClientHandler.postData(
					sessionService.getUserId(),
					Objects.toString(request.getSession().getAttribute("SSID"), null),
					params);
		} catch (Exception e) {
			log.error(">> compareSchema failed", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return Mono.just(result);
		}
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

	/** 스키마별 최신 구조 진단 상태 (뱃지용) */
	@GetMapping("/latestStatus")
	public Map<String, Object> latestStatus(@RequestParam String dsId, @RequestParam(required = false) String schemaNm) {
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
}
