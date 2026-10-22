package qualitycenter.controller;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.bean.SecurityManager;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.datasource.dbms.ext.NamedParamStatement;
import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.model.DataSourceVo;
import com.ndata.quality.tool.DataSourceUtils;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelCollectVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.model.std.StdDataModelSchemaVo;
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.service.ExcelDownloadService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ddl.DdlDialects;
import qualitycenter.service.ddl.DdlGenerator;
import qualitycenter.service.ddl.DdlRuleService;
import qualitycenter.service.ddl.DdlSafety;
import qualitycenter.service.ws.WebSocketService;
import qualitycenter.util.ErwinXmlParser;
import qualitycenter.util.ErwinXmlParser.ErwinParseResult;
import qualitycenter.util.XmiParser;
import qualitycenter.util.XmiParser.XmiParseResult;
import qualitycenter.util.XmiExporter;
import reactor.core.publisher.Mono;

/**
 * 데이터 모델 컨트롤러 (모델 CRUD, 스키마 수집, ERwin 임포트)
 *
 * <p>데이터 모델 등록/수정/삭제, 대상 DBMS 스키마 수집 요청,
 * 수집 이력 관리, ERwin XML 파일 임포트 기능을 제공한다.</p>
 */
@Tag(name = "데이터모델", description = "데이터모델 API")
@Slf4j
@RestController
@RequestMapping("/api/dm")
public class DataModelController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private com.ndata.quality.service.StdDictService dictService;

	@Autowired
	private ExcelDownloadService excelDownloadService;

	@Autowired
	private qualitycenter.service.governance.ChangeHistoryService changeHistory;

	/**
	 * 88번 §7.3 — 컬럼 순서 swap (Tier 1.5, 관리자만).
	 * direction = UP : 인접 위 행과 attr_ord 스왑.  DOWN : 아래 행과 스왑.
	 */
	@PostMapping("/swapAttrOrd")
	public Mono<Response> swapAttrOrd(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(com.ndata.quality.common.PermCode.DM_MANAGE)) throw new IllegalStateException("권한이 없습니다 (모델 직접 반영 권한 필요)");
			String dataModelId = (String) body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : (String) body.get("objOwner");
			String objNm       = (String) body.get("objNm");
			String attrNm      = (String) body.get("attrNm");
			String direction   = (String) body.get("direction");
			if (dataModelId == null || objNm == null || attrNm == null || direction == null)
				throw new IllegalArgumentException("dataModelId/objNm/attrNm/direction 필수");

			Map<String, Object> p = new HashMap<>();
			p.put("dataModelId", dataModelId);
			p.put("objOwner",    objOwner);
			p.put("objNm",       objNm);
			List<Map<String, Object>> rows = sqlSessionTemplate.selectList("datamodel.selectAttrOrdForSwap", p);
			int idx = -1;
			for (int i = 0; i < rows.size(); i++) {
				if (attrNm.equals(rows.get(i).get("attrNm"))) { idx = i; break; }
			}
			if (idx < 0) throw new IllegalStateException("대상 컬럼 없음");
			int swapWith = "UP".equalsIgnoreCase(direction) ? idx - 1 : idx + 1;
			if (swapWith < 0 || swapWith >= rows.size())
				throw new IllegalStateException("인접 컬럼 없음");

			Map<String, Object> a = rows.get(idx);
			Map<String, Object> b = rows.get(swapWith);
			Number ordA = (Number) a.get("attrOrder");
			Number ordB = (Number) b.get("attrOrder");

			Map<String, Object> u1 = new HashMap<>();
			u1.put("dataModelId", dataModelId); u1.put("objOwner", objOwner); u1.put("objNm", objNm);
			u1.put("attrNm", a.get("attrNm")); u1.put("attrOrder", ordB.shortValue());
			sqlSessionTemplate.update("datamodel.updateAttrOrdByName", u1);

			Map<String, Object> u2 = new HashMap<>();
			u2.put("dataModelId", dataModelId); u2.put("objOwner", objOwner); u2.put("objNm", objNm);
			u2.put("attrNm", b.get("attrNm")); u2.put("attrOrder", ordA.shortValue());
			sqlSessionTemplate.update("datamodel.updateAttrOrdByName", u2);

			// 이력 기록 (Tier 1.5, 즉시 APPROVED)
			changeHistory.record(null, dataModelId, "SWAP_ATTR_ORD", "TIER1_5",
					objOwner, objNm, attrNm, null, null,
					"{\"a\":\"" + a.get("attrNm") + "\":" + ordA + ",\"b\":\"" + b.get("attrNm") + "\":" + ordB + "}",
					"{\"a\":\"" + a.get("attrNm") + "\":" + ordB + ",\"b\":\"" + b.get("attrNm") + "\":" + ordA + "}",
					null, "APPROVED");
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> swapAttrOrd failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	@Autowired
	SecurityManager securityUtils;

	@Autowired
	private DataSourceUtils dataSourceUtils;

	@Autowired
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private WebSocketService websocketService;

	@Autowired
	private DdlGenerator ddlGenerator;

	@Autowired
	private DdlRuleService ddlRules;

	@Autowired
	private DdlDialects ddlDialects;

	/** 104번 WP7-23 — 용어 등록 자동 분석 엔진(analyzeTermsBatch)을 표준 후보 찾기에 쓴다 */
	@Autowired
	private DataStandardController stdController;

	/** 104번 WP8-31 — 테이블 사용자 정의 속성 값 검사·저장 */
	@Autowired
	private qualitycenter.service.udp.UdpService udpService;

	/**
	 * 데이터모델 등록 API
	 *
	 * @param dataVo 데이터모델 정보 (모델명, 데이터소스ID 등)
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createDataModel", method = RequestMethod.POST)
	public Mono<Response> createDataModel(@RequestBody StdDataModelVo dataVo) {
		dataVo.setDataModelId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		log.info(">> createDataModel : {}", dataVo);

		Response result = new Response();
		// 109번 #2 — 시스템·데이터소스 지정은 모델 직접 반영 권한(DM_MANAGE). DB 반영 권한(DDL_EXEC)이 시스템 단위라
		// 모델의 시스템·데이터소스를 아무나 바꾸면 그 권한 범위를 넘어 다른 시스템 DB 에 실행할 수 있었다
		if ((!isBlankStr(dataVo.getDataModelSysCd()) || !isBlankStr(dataVo.getDataModelDsId()))
				&& !sessionService.hasPerm(com.ndata.quality.common.PermCode.DM_MANAGE)) {
			result.setResultInfo(403, "모델의 시스템·데이터 소스를 지정하려면 모델 직접 반영 권한이 필요합니다.");
			return Mono.just(result);
		}

		try {
			sqlSessionTemplate.insert("datamodel.insertDataModel", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> createDataModel failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	private static boolean isBlankStr(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String blankToNull(Object o) {
		return o == null || o.toString().trim().isEmpty() ? null : o.toString().trim();
	}

	/**
	 * 데이터모델 수정 API
	 *
	 * @param dataVo 수정할 데이터모델 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateDataModel", method = RequestMethod.POST)
	public Mono<Response> updateDataModel(@RequestBody StdDataModelVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateDataModel : {}", dataVo);

		Response result = new Response();

		try {
			// 109번 #2 — 시스템·데이터소스를 바꾸는 것은 모델 직접 반영 권한(DM_MANAGE)
			Map<String, Object> cur = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataVo.getDataModelId());
			if (cur == null) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "수정할 데이터 모델이 없습니다: " + dataVo.getDataModelId());
				return Mono.just(result);
			}
			boolean sysDsChanged = !Objects.equals(blankToNull(cur.get("dataModelSysCd")), blankToNull(dataVo.getDataModelSysCd()))
					|| !Objects.equals(blankToNull(cur.get("dataModelDsId")), blankToNull(dataVo.getDataModelDsId()));
			if (sysDsChanged && !sessionService.hasPerm(com.ndata.quality.common.PermCode.DM_MANAGE)) {
				result.setResultInfo(403, "모델의 시스템·데이터 소스를 바꾸려면 모델 직접 반영 권한이 필요합니다.");
				return Mono.just(result);
			}
			sqlSessionTemplate.update("datamodel.updateDataModel", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateDataModel failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 104번 WP7-25 — 시스템(하위 시스템 포함)에 속한 사용 중 모델을 모두 표준 준수 필수로 켜거나 끈다.
	 * 모델 직접 반영 권한(DM_MANAGE).
	 *
	 * @param body {sysCd, stndReqYn: Y|N}
	 * @return contents = 바뀐 모델 수
	 */
	@RequestMapping(value = "/setStndReqBySys", method = RequestMethod.POST)
	public Mono<Response> setStndReqBySys(@RequestBody Map<String, Object> body) {
		Response result = new Response();
		if (!sessionService.hasPerm(com.ndata.quality.common.PermCode.DM_MANAGE)) {
			result.setResultInfo(403, "권한이 없습니다 (모델 직접 반영 권한 필요)");
			return Mono.just(result);
		}
		String sysCd = str(body.get("sysCd"));
		String yn = str(body.get("stndReqYn"));
		if (sysCd == null || sysCd.trim().isEmpty() || !("Y".equals(yn) || "N".equals(yn))) {
			result.setResultInfo(RestResult.CODE_500.getCode(), "시스템과 표준 준수 필수 여부(Y/N)를 보내 주세요.");
			return Mono.just(result);
		}
		Map<String, Object> p = new HashMap<>();
		p.put("sysCd", sysCd.trim());
		p.put("stndReqYn", yn);
		p.put("userId", sessionService.getUserId());
		int n = sqlSessionTemplate.update("datamodel.updateStndReqYnBySys", p);
		result.setResultInfo(RestResult.CODE_200);
		result.setContents(String.valueOf(n));
		return Mono.just(result);
	}

	/**
	 * 데이터모델 다건 삭제 API
	 *
	 * @param dataVos 삭제 대상 데이터모델 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteDataModels", method = RequestMethod.POST)
	public Mono<Response> deleteDataModels(@RequestBody List<StdDataModelVo> dataVos) {
		Response result = new Response();

		try {
			sqlSessionTemplate.delete("datamodel.deleteDataModels", dataVos);
			// cascade: 같은 모델의 진단 스케줄도 비활성화 (실패해도 모델 삭제는 유지)
			int deactivatedSchedules = 0;
			try {
				deactivatedSchedules = sqlSessionTemplate.update(
					"datamodel.deactivateSchedulesByDmIds", dataVos);
			} catch (Exception cascadeErr) {
				log.warn(">> deleteDataModels cascade(schedule) skipped: {}", cascadeErr.getMessage());
			}
			result.setContents(String.valueOf(deactivatedSchedules));
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteDataModels failed : {}", e.getMessage(), e);
			result.setResultInfo(RestResult.CODE_500.getCode(),
				e.getMessage() != null ? e.getMessage() : "삭제 실패");
		}
		return Mono.just(result);
	}

	/**
	 * 데이터모델 현황 목록 조회 (모델별 최신 수집/진단 상태 포함)
	 *
	 * @param retCond 검색 조건 (모델명, 시스템명 등)
	 * @return 데이터모델 현황 목록
	 */
	@RequestMapping(value = "/getDataModelStatsList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelVo> getDataModelStatsList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelStatsList", retCond);
	}

	/**
	 * 데이터모델 DDL 다운로드
	 *
	 * @param dataModelId 데이터모델 ID
	 * @param dbType      DBMS (Oracle·Tibero·PostgreSQL·MariaDB·SQLServer). 미지정 시 모델 데이터소스의 DBMS.
	 *                    데이터소스가 없거나 DDL 규칙이 없는 DBMS 면 Oracle 문법 (DdlGenerator).
	 * @param response    HTTP 응답 (파일 스트림)
	 */
	@RequestMapping(value = "/downloadDdl", method = RequestMethod.GET)
	public void downloadDdl(@RequestParam("dataModelId") String dataModelId,
			@RequestParam(value = "dbType", required = false) String dbType,
			HttpServletResponse response) {
		log.info(">> downloadDdl : dmId={}, dbType={}", dataModelId, dbType);
		try {
			Map<String, Object> model = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
			String dmNm = model == null ? dataModelId : String.valueOf(model.get("dataModelNm"));

			String resolvedDbType = dbType;
			if (resolvedDbType == null || resolvedDbType.trim().isEmpty()) {
				Map<String, Object> ds = ddlDialects.modelDs(dataModelId);
				resolvedDbType = ds == null ? null : (String) ds.get("dbmsTp");
			}

			String ddl = ddlGenerator.generate(dataModelId, resolvedDbType);

			String safeName = (dmNm == null ? dataModelId : dmNm).replaceAll("[\\\\/:*?\"<>|]", "_");
			String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
			String filename = safeName + "_" + ts + ".sql";

			response.setContentType("application/sql; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Content-Disposition",
					"attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20") + "\"");
			response.getWriter().write(ddl);
			response.getWriter().flush();
		} catch (Exception e) {
			log.error(">> downloadDdl failed : {}", e.getMessage(), e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 수집 ID 기준 데이터모델 현황 정보 조회
	 *
	 * @param clctId 수집 ID
	 * @return 해당 수집건의 데이터모델 현황 정보
	 */
	@RequestMapping(value = "/getDataModelStatsByClctId", method = RequestMethod.GET)
	public StdDataModelVo getDataModelStatsByClctId(String clctId) {
		return sqlSessionTemplate.selectOne("datamodel.selectDataModelStatsByClctId", clctId);
	}

	/**
	 * 데이터모델 목록 조회 (수집 정보 포함)
	 *
	 * @param retCond 검색 조건 (모델명, 시스템명 등)
	 * @return 데이터모델 + 수집 정보 목록
	 */
	@RequestMapping(value = "/getDataModelList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelList", retCond);
	}

	/**
	 * 데이터모델 수집 목록 조회 (모델ID, 모델명, 시스템명 기준)
	 *
	 * @param retCond 검색 조건
	 * @return 수집 이력 목록
	 */
	@RequestMapping(value = "/getDataModelClctList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelClctList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelClctList", retCond);
	}

	/**
	 * 데이터모델 객체(테이블) 수집 목록 조회 - 수집 ID 기준
	 *
	 * @param clctId 수집 ID
	 * @return 수집된 테이블 목록
	 */
	@RequestMapping(value = "/getDataModelObjListByClctId", method = RequestMethod.GET)
	public List<StdDataModelObjVo> getDataModelObjListByClctId(String clctId) {
		List<StdDataModelObjVo> all = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", clctId);
		// 88번 거버넌스 — 전원 'APPROVED + 본인 미승인'만. 관리자도 남의 미승인(DRAFT/SUBMITTED)은
		// 테이블 메뉴에 노출 안 됨 — 승인 화면에서만 본다.
		String currentUser = sessionService.getUserId();
		return all.stream()
			.filter(o -> o.getAprvStatus() == null
					|| "APPROVED".equals(o.getAprvStatus())
					|| (currentUser != null && currentUser.equals(o.getRequesterUserId())))
			.collect(java.util.stream.Collectors.toList());
	}

	/**
	 * 데이터모델 객체(테이블) 이름으로 검색
	 *
	 * @param retCond 검색 조건 (테이블명 등)
	 * @return 매칭되는 테이블 속성 목록
	 */
	@RequestMapping(value = "/getDataModelObjListByObjNm", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelAttrVo> getDataModelObjListByObjNm(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByObjNm", retCond);
	}

	/**
	 * 데이터모델 객체(테이블) 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답
	 * @param clctId   수집 ID
	 */
	@RequestMapping(value = "/downloadDataModelObjs", method = RequestMethod.GET)
	public void downloadDataModelObjs(HttpServletRequest request, HttpServletResponse response, String clctId) {
		log.info(">> download  data model objects excel started : {}", clctId);

		try {
			excelDownloadService.getDMObjsExcel(clctId, request, response);
		} catch (Exception e) {
			log.error(">> download data model objects excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 데이터모델 속성(컬럼) 수집 목록 조회 - 수집 ID 기준
	 *
	 * @param clctId 수집 ID
	 * @return 수집된 컬럼 목록
	 */
	@RequestMapping(value = "/getDataModelAttrListByClctId", method = RequestMethod.GET)
	public List<StdDataModelAttrVo> getDataModelAttrListByClctId(String clctId) {
		List<StdDataModelAttrVo> all = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", clctId);
		// 88번 거버넌스 — 전원 'APPROVED + 본인 미승인'만. 관리자도 남의 미승인(DRAFT/SUBMITTED)은
		// 컬럼 메뉴에 노출 안 됨 — 승인 화면에서만 본다.
		String currentUser = sessionService.getUserId();
		return all.stream()
			.filter(a -> a.getAprvStatus() == null
					|| "APPROVED".equals(a.getAprvStatus())
					|| (currentUser != null && currentUser.equals(a.getRequesterUserId())))
			.collect(java.util.stream.Collectors.toList());
	}

	/** 데이터모델 인덱스 목록 조회 (DM_ID 기준, CLCT 폐기 이후) */
	@RequestMapping(value = "/getDataModelIndexListByDmId", method = RequestMethod.GET)
	public List<java.util.Map<String, Object>> getDataModelIndexListByDmId(String dataModelId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelIndexListByDmId", dataModelId);
	}

	/** 데이터모델 제약조건 목록 조회 (DM_ID 기준, CLCT 폐기 이후) */
	@RequestMapping(value = "/getDataModelConstraintListByDmId", method = RequestMethod.GET)
	public List<java.util.Map<String, Object>> getDataModelConstraintListByDmId(String dataModelId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelConstraintListByDmId", dataModelId);
	}

	/**
	 * 데이터모델 속성(컬럼) 검색 - 조건별 조회
	 *
	 * @param retCond 검색 조건 (컬럼명, 테이블명 등)
	 * @return 매칭되는 컬럼 목록
	 */
	@RequestMapping(value = "/getDataModelAttrListByRetreiveCond", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelAttrVo> getDataModelAttrListByRetreiveCond(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByRetreiveCond", retCond);
	}

	/**
	 * 데이터모델 속성(컬럼) 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답
	 * @param clctId   수집 ID
	 */
	@RequestMapping(value = "/downloadDataModelAttrs", method = RequestMethod.GET)
	public void downloadDataModelAttrs(HttpServletRequest request, HttpServletResponse response, String clctId) {
		log.info(">> download  data model attributes excel started : {}", clctId);

		try {
			excelDownloadService.getDMAttrsExcel(clctId, request, response);
		} catch (Exception e) {
			log.error(">> download data model attributes excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 대상 DB 스키마 목록 조회 (DB 직접 접속)
	 *
	 * <p>데이터소스에 직접 접속하여 접근 가능한 스키마 목록을 반환한다.
	 * DBMS 유형(Oracle, MariaDB, PostgreSQL 등)에 따라 다른 SQL을 사용한다.</p>
	 *
	 * @param dataVo 데이터모델 정보 (dataModelDsId 필수)
	 * @return 스키마명 목록
	 */
	/**
	 * 대상 DB 스키마 목록 조회
	 * @return { schemas: [스키마명 목록], currentUser: 접속유저명 }
	 */
	@RequestMapping(value = "/getSchemaList", method = RequestMethod.POST)
	public Map<String, Object> getSchemaList(@RequestBody StdDataModelVo dataVo) {
		log.info(">> getSchemaList : dsId={}", dataVo.getDataModelDsId());
		Map<String, Object> result = new HashMap<>();
		List<String> schemaList = new ArrayList<>();
		String currentUser = "";
		String dbmsType = "";
		DBHandler dbHandler = null;
		try {
			DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dataVo.getDataModelDsId());
			dbHandler = dataSourceUtils.getDBHandler(dataSource);
			currentUser = dataSource.getUserId();
			dbmsType = dataSource.getDbmsTp();

			String sql = sqlSessionTemplate.selectOne("datamodel.selectSchemaListSql", dataSource.getDbmsTp());
			if (sql == null) sql = "SELECT schema_name AS schemaNm FROM information_schema.schemata ORDER BY schema_name";

			NamedParamStatement pstmt = dbHandler.namedParamStatement(sql);
			java.sql.ResultSet rs = dbHandler.executeSql(pstmt);
			while (rs.next()) {
				schemaList.add(rs.getString("schemaNm"));
			}
			pstmt.close();
			rs.close();
		} catch (Exception e) {
			log.error(">> getSchemaList failed : {}", e.getMessage());
		} finally {
			if (dbHandler != null) {
				try { dbHandler.close(); } catch (Exception e) {}
			}
		}
		// 86번 #8 — DBMS 시스템 스키마 자동 필터 (수집 대상 아님 — 사용자 노출 X)
		schemaList = filterSystemSchemas(schemaList, dbmsType);
		result.put("schemas", schemaList);
		result.put("currentUser", currentUser);
		return result;
	}

	/** 86번 #8 — 시스템 스키마 일괄 필터. DBMS 별 명시 set + prefix 룰. */
	private static final java.util.Set<String> ORACLE_SYS = new java.util.HashSet<>(java.util.Arrays.asList(
		"SYS","SYSTEM","OUTLN","MGMT_VIEW","OLAPSYS","MDSYS","ORDDATA","ORDPLUGINS",
		"EXFSYS","DBSNMP","WMSYS","XDB","CTXSYS","DVSYS","GSMADMIN_INTERNAL",
		"AUDSYS","GSMCATUSER","GSMUSER","REMOTE_SCHEDULER_AGENT","ANONYMOUS",
		"SI_INFORMTN_SCHEMA","ORDS_METADATA","ORDS_PUBLIC_USER","APPQOSSYS","DIP",
		"ORACLE_OCM","XS$NULL","GGSYS","LBACSYS","MDDATA","DBSFWUSER",
		"PUBLIC","ROLE_DBA","ROLE_RESOURCE","ROLE_CONNECT"
	));
	private static final java.util.Set<String> PG_SYS = new java.util.HashSet<>(java.util.Arrays.asList(
		"information_schema","pg_catalog","pg_toast"
	));
	private static final java.util.Set<String> MYSQL_SYS = new java.util.HashSet<>(java.util.Arrays.asList(
		"mysql","information_schema","performance_schema","sys"
	));
	private static final java.util.Set<String> MSSQL_SYS = new java.util.HashSet<>(java.util.Arrays.asList(
		"master","tempdb","model","msdb","INFORMATION_SCHEMA","sys","guest"
	));
	private static final java.util.Set<String> CUBRID_SYS = new java.util.HashSet<>(java.util.Arrays.asList(
		"DBA","PUBLIC"
	));

	private List<String> filterSystemSchemas(List<String> all, String dbmsType) {
		if (all == null) return new ArrayList<>();
		String t = dbmsType == null ? "" : dbmsType.toUpperCase();
		java.util.Set<String> sys;
		if (t.contains("ORACLE") || t.contains("TIBERO")) sys = ORACLE_SYS;
		else if (t.contains("POSTGRE")) sys = PG_SYS;
		else if (t.contains("MYSQL") || t.contains("MARIADB")) sys = MYSQL_SYS;
		else if (t.contains("MSSQL") || t.contains("SQLSERVER")) sys = MSSQL_SYS;
		else if (t.contains("CUBRID")) sys = CUBRID_SYS;
		else sys = java.util.Collections.emptySet();

		List<String> out = new ArrayList<>();
		for (String s : all) {
			if (s == null || s.isEmpty()) continue;
			String upper = s.toUpperCase();
			// 명시 set 매칭
			if (sys.contains(s) || sys.contains(upper)) continue;
			// prefix 룰
			if ((t.contains("ORACLE") || t.contains("TIBERO"))
					&& (upper.startsWith("APEX_") || upper.startsWith("FLOWS_"))) continue;
			if (t.contains("POSTGRE") && s.startsWith("pg_")) continue;
			if ((t.contains("MSSQL") || t.contains("SQLSERVER")) && upper.startsWith("DB_")) continue;
			out.add(s);
		}
		return out;
	}

	/**
	 * 데이터모델 스키마 수집 필터 조회
	 *
	 * @param dataModelId 데이터모델 ID
	 * @return 수집 대상 스키마 필터 목록
	 */
	@RequestMapping(value = "/getDataModelSchemas", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelSchemaVo> getDataModelSchemas(String dataModelId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelSchemaList", dataModelId);
	}

	/**
	 * 데이터모델 스키마 수집 필터 저장 (기존 전체 삭제 후 재저장, 트랜잭션 사용)
	 *
	 * @param schemas 저장할 스키마 필터 목록
	 * @return 저장 결과
	 */
	@RequestMapping(value = "/saveDataModelSchemas", method = RequestMethod.POST)
	public Mono<Response> saveDataModelSchemas(@RequestBody List<StdDataModelSchemaVo> schemas) {
		Response result = new Response();
		if (schemas == null || schemas.isEmpty()) {
			result.setResultInfo(RestResult.CODE_200);
			return Mono.just(result);
		}
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = schemas.get(0).getDataModelId();
			session.delete("datamodel.deleteDataModelSchemasByDmId", dataModelId);
			for (StdDataModelSchemaVo schema : schemas) {
				schema.setCretUserId(sessionService.getUserId());
				session.insert("datamodel.mergeDataModelSchema", schema);
			}
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			session.rollback();
			log.error(">> saveDataModelSchemas failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 데이터모델 수집 이력 목록 조회
	 *
	 * @param retCond 검색 조건
	 * @return 수집 이력 목록
	 */
	@RequestMapping(value = "/getDataModelHistoryList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelHistoryList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelHistoryList", retCond);
	}

	/**
	 * 데이터모델 수집 실행 API
	 *
	 * <p>q-executor에 수집 요청을 전달한다. executor가 대상 DBMS에 접속하여
	 * 테이블/컬럼 정보를 수집하고 TB_DATA_MODEL_OBJ/ATTR/STATS에 저장한다.</p>
	 *
	 * @param request HTTP 요청
	 * @param dataVo  데이터모델 정보 (dataModelId, dataModelDsId 필수)
	 * @return 수집 요청 결과
	 */
	@RequestMapping(value = "/collectDataModel", method = RequestMethod.POST)
	public Mono<Response> collectDataModel(HttpServletRequest request, @RequestBody StdDataModelVo dataVo) {
		log.info(">> started collect data model : {}", dataVo);

		// 같은 모델을 동시에 수집하면 MERGE 가 서로의 결과를 덮어쓴다. 진단과 같은 가드.
		int running = sqlSessionTemplate.selectOne(
				"datamodel.selectActiveCollectCount", dataVo.getDataModelId());
		if (running > 0) {
			Response busy = new Response();
			busy.setResultInfo(409, "이미 진행 중인 수집이 있습니다.");
			return Mono.just(busy);
		}

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/dm/collectDataModel");
		Mono<Response> mResponse = webClientHandler.postData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), dataVo);

		log.info(">> finished collect data model : {}", dataVo);
		return mResponse;
	}

	// ======== ERwin XML 임포트 ========

	/**
	 * ERwin XML 파일을 파싱하여 미리보기 결과를 반환한다.
	 */
	@PostMapping(value = "/parseErwinXml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Object> parseErwinXml(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			ErwinParseResult parsed = ErwinXmlParser.parse(file.getInputStream());
			result.put("success", true);
			result.put("tables", parsed.getTables());
			result.put("columns", parsed.getColumns());
			result.put("tableCount", parsed.getTableCount());
			result.put("columnCount", parsed.getColumnCount());
		} catch (Exception e) {
			log.error(">> parseErwinXml failed : {}", e.getMessage());
			result.put("success", false);
			result.put("message", "ERwin XML 파싱 실패: " + e.getMessage());
		}
		return result;
	}

	/**
	 * ERwin 모델을 데이터모델에 임포트한다.
	 * 기존 수집 구조(TB_DATA_MODEL_CLCT, TB_DATA_MODEL_OBJ, TB_DATA_MODEL_ATTR)를 재활용한다.
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("/importErwinModel")
	public Map<String, Object> importErwinModel(@RequestBody Map<String, Object> body) {
		Map<String, Object> result = new HashMap<>();
		String dataModelId = (String) body.get("dataModelId");
		List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");
		List<Map<String, Object>> columns = (List<Map<String, Object>>) body.get("columns");

		if (dataModelId == null || tables == null || columns == null) {
			result.put("success", false);
			result.put("message", "필수 파라미터(dataModelId, tables, columns)가 누락되었습니다.");
			return result;
		}

		// 109번 #3 — 테이블·컬럼 이름과 데이터 타입은 [DB 반영] DDL 에 들어간다
		String bad = importCheck(tables, columns);
		if (bad != null) {
			result.put("success", false);
			result.put("message", bad);
			return result;
		}

		String clctId = StringUtils.getUUID();
		String userId = sessionService.getUserId();
		String now = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

		SqlSession session = sqlSessionFactory.openSession();
		try {
			// 1) 수집 이력 생성 (시작)
			StdDataModelCollectVo clctVo = new StdDataModelCollectVo();
			clctVo.setClctId(clctId);
			clctVo.setDataModelId(dataModelId);
			clctVo.setClctType("ERWIN");
			clctVo.setCretUserId(userId);
			session.insert("datamodel.updateDataModelCollect", clctVo);

			// 2) 테이블(OBJ) 등록
			for (Map<String, Object> tbl : tables) {
				StdDataModelObjVo objVo = new StdDataModelObjVo();
				objVo.setClctId(clctId);
				objVo.setDataModelId(dataModelId);
				objVo.setObjNm((String) tbl.get("objNm"));
				objVo.setObjNmKr((String) tbl.get("objNmKr"));
				objVo.setObjOwner("ERWIN");
				Object attrCntObj = tbl.get("objAttrCnt");
				objVo.setObjAttrCnt(attrCntObj instanceof Number ? ((Number) attrCntObj).shortValue() : 0);
				session.insert("datamodel.insertDataModelObj", objVo);
			}

			// 3) 컬럼(ATTR) 등록
			for (Map<String, Object> col : columns) {
				StdDataModelAttrVo attrVo = new StdDataModelAttrVo();
				attrVo.setClctId(clctId);
				attrVo.setDataModelId(dataModelId);
				attrVo.setObjNm((String) col.get("objNm"));
				attrVo.setAttrNm((String) col.get("attrNm"));
				attrVo.setAttrNmKr((String) col.get("attrNmKr"));
				attrVo.setDataType((String) col.get("dataType"));
				Object dataLenObj = col.get("dataLen");
				attrVo.setDataLen(dataLenObj instanceof Number ? ((Number) dataLenObj).longValue() : 0L);
				attrVo.setNullableYn((String) col.get("nullableYn"));
				attrVo.setPkYn((String) col.get("pkYn"));
				attrVo.setFkYn("N");
				attrVo.setTermsStndYn("N");
				attrVo.setDomainStndYn("N");
				Object ordObj = col.get("attrOrder");
				attrVo.setAttrOrder(ordObj instanceof Number ? ((Number) ordObj).shortValue() : 0);
				session.insert("datamodel.insertDataModelAttr", attrVo);
			}

			// 4) 수집 이력 완료 업데이트
			clctVo.setClctEndDt(now);
			clctVo.setClctCmptnYn("Y");
			session.insert("datamodel.updateDataModelCollect", clctVo);

			session.commit();

			result.put("success", true);
			result.put("clctId", clctId);
			result.put("tableCount", tables.size());
			result.put("columnCount", columns.size());
			result.put("message", "ERwin 모델 임포트 완료: 테이블 " + tables.size() + "건, 컬럼 " + columns.size() + "건");
		} catch (Exception e) {
			session.rollback();
			log.error(">> importErwinModel failed : {}", e.getMessage(), e);
			result.put("success", false);
			result.put("message", "임포트 실패: " + e.getMessage());
		} finally {
			session.close();
		}
		return result;
	}

	// ======== 85번 — XMI 2.1 임포트 (OMG 표준, ERwin 9.x+ / EA / VP 등 호환) ========

	/**
	 * XMI 2.1 (UML 2.x) 파일을 파싱하여 미리보기 결과를 반환한다.
	 * RFP SFR-22 항목 C "표준 포맷" 의 표준 포맷 = XMI.
	 */
	@PostMapping(value = "/parseXmi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Object> parseXmi(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			XmiParseResult parsed = XmiParser.parse(file.getInputStream());
			result.put("success", true);
			result.put("tables", parsed.getTables());
			result.put("columns", parsed.getColumns());
			result.put("tableCount", parsed.getTableCount());
			result.put("columnCount", parsed.getColumnCount());
			result.put("format", "XMI 2.1");
		} catch (Exception e) {
			log.error(">> parseXmi failed : {}", e.getMessage());
			result.put("success", false);
			result.put("message", "XMI 파싱 실패: " + e.getMessage());
		}
		return result;
	}

	/**
	 * XMI 모델을 데이터모델에 임포트한다.
	 * importErwinModel 과 동일한 적재 로직 사용. CLCT_TYPE 만 'XMI' 로 표기.
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("/importXmiModel")
	public Map<String, Object> importXmiModel(@RequestBody Map<String, Object> body) {
		Map<String, Object> result = new HashMap<>();
		String dataModelId = (String) body.get("dataModelId");
		List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");
		List<Map<String, Object>> columns = (List<Map<String, Object>>) body.get("columns");

		if (dataModelId == null || tables == null || columns == null) {
			result.put("success", false);
			result.put("message", "필수 파라미터(dataModelId, tables, columns)가 누락되었습니다.");
			return result;
		}

		// 109번 #3 — 테이블·컬럼 이름과 데이터 타입은 [DB 반영] DDL 에 들어간다
		String bad = importCheck(tables, columns);
		if (bad != null) {
			result.put("success", false);
			result.put("message", bad);
			return result;
		}

		String clctId = StringUtils.getUUID();
		String userId = sessionService.getUserId();
		String now = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

		SqlSession session = sqlSessionFactory.openSession();
		try {
			StdDataModelCollectVo clctVo = new StdDataModelCollectVo();
			clctVo.setClctId(clctId);
			clctVo.setDataModelId(dataModelId);
			clctVo.setClctType("XMI");
			clctVo.setCretUserId(userId);
			session.insert("datamodel.updateDataModelCollect", clctVo);

			for (Map<String, Object> tbl : tables) {
				StdDataModelObjVo objVo = new StdDataModelObjVo();
				objVo.setClctId(clctId);
				objVo.setDataModelId(dataModelId);
				objVo.setObjNm((String) tbl.get("objNm"));
				objVo.setObjNmKr((String) tbl.get("objNmKr"));
				objVo.setObjOwner("XMI");
				Object attrCntObj = tbl.get("objAttrCnt");
				objVo.setObjAttrCnt(attrCntObj instanceof Number ? ((Number) attrCntObj).shortValue() : 0);
				session.insert("datamodel.insertDataModelObj", objVo);
			}

			for (Map<String, Object> col : columns) {
				StdDataModelAttrVo attrVo = new StdDataModelAttrVo();
				attrVo.setClctId(clctId);
				attrVo.setDataModelId(dataModelId);
				attrVo.setObjNm((String) col.get("objNm"));
				attrVo.setAttrNm((String) col.get("attrNm"));
				attrVo.setAttrNmKr((String) col.get("attrNmKr"));
				attrVo.setDataType((String) col.get("dataType"));
				Object dataLenObj = col.get("dataLen");
				attrVo.setDataLen(dataLenObj instanceof Number ? ((Number) dataLenObj).longValue() : 0L);
				attrVo.setNullableYn((String) col.get("nullableYn"));
				attrVo.setPkYn((String) col.get("pkYn"));
				// 85번 — XMI 관계 매핑: FK + 부모
				String fk = (String) col.get("fkYn");
				attrVo.setFkYn(fk != null ? fk : "N");
				attrVo.setFkParentObjNm((String) col.get("fkParentObjNm"));
				// XMI/ERwin 에는 스키마 개념이 없다. 임포트 모델은 단일 소유자이므로
				// 부모 소유자는 자기 자신과 같다고 본다.
				attrVo.setFkParentObjOwner(attrVo.getObjOwner());
				attrVo.setFkParentAttrNm((String) col.get("fkParentAttrNm"));
				attrVo.setTermsStndYn("N");
				attrVo.setDomainStndYn("N");
				Object ordObj = col.get("attrOrder");
				attrVo.setAttrOrder(ordObj instanceof Number ? ((Number) ordObj).shortValue() : 0);
				session.insert("datamodel.insertDataModelAttr", attrVo);
			}

			clctVo.setClctEndDt(now);
			clctVo.setClctCmptnYn("Y");
			session.insert("datamodel.updateDataModelCollect", clctVo);

			session.commit();

			result.put("success", true);
			result.put("clctId", clctId);
			result.put("tableCount", tables.size());
			result.put("columnCount", columns.size());
			result.put("format", "XMI 2.1");
			result.put("message", "XMI 모델 임포트 완료: 테이블 " + tables.size() + "건, 컬럼 " + columns.size() + "건");
		} catch (Exception e) {
			session.rollback();
			log.error(">> importXmiModel failed : {}", e.getMessage(), e);
			result.put("success", false);
			result.put("message", "임포트 실패: " + e.getMessage());
		} finally {
			session.close();
		}
		return result;
	}

	/**
	 * 85번 — DataQ 모델 → XMI 2.1 export.
	 * RFP SFR-22 항목 C "표준 포맷 추출".
	 */
	@org.springframework.web.bind.annotation.GetMapping(value = "/exportXmi",
			produces = "application/xml; charset=UTF-8")
	public org.springframework.http.ResponseEntity<String> exportXmi(
			@RequestParam("dataModelId") String dataModelId) {
		try {
			// 모델명 조회
			Map<String, Object> dm = sqlSessionTemplate.selectOne(
					"datamodel.selectDataModelById", dataModelId);
			String modelName = dm != null && dm.get("dataModelNm") != null
					? dm.get("dataModelNm").toString() : ("model-" + dataModelId);

			// ATTR raw list 조회 → OBJ list 는 distinct 로 도출
			List<Map<String, Object>> columns = sqlSessionTemplate.selectList(
					"datamodel.selectDataModelAttrListByClctIdRaw", dataModelId);
			java.util.Map<String, Map<String, Object>> objMap = new java.util.LinkedHashMap<>();
			for (Map<String, Object> c : columns) {
				Object oN = c.get("tableNm") != null ? c.get("tableNm") : c.get("objNm");
				if (oN == null) continue;
				String objNm = oN.toString();
				if (!objMap.containsKey(objNm)) {
					Map<String, Object> t = new HashMap<>();
					t.put("objNm", objNm);
					t.put("objNmKr", objNm);
					objMap.put(objNm, t);
				}
			}
			List<Map<String, Object>> tables = new java.util.ArrayList<>(objMap.values());
			// columns 의 키를 objNm/attrNm 기준으로 정규화 (alias 가 tableNm/columnNm 인 경우 보정)
			for (Map<String, Object> c : columns) {
				if (c.get("objNm") == null && c.get("tableNm") != null)
					c.put("objNm", c.get("tableNm"));
				if (c.get("attrNm") == null && c.get("columnNm") != null)
					c.put("attrNm", c.get("columnNm"));
			}

			String xml = XmiExporter.export(modelName, tables, columns);
			String safeName = modelName.replaceAll("[^A-Za-z0-9_-]", "_");
			return org.springframework.http.ResponseEntity.ok()
					.header("Content-Disposition",
							"attachment; filename=\"" + safeName + ".xmi\"")
					.header("Content-Type", "application/xml; charset=UTF-8")
					.body(xml);
		} catch (Exception e) {
			log.error(">> exportXmi failed", e);
			return org.springframework.http.ResponseEntity.status(500)
					.body("<error>" + e.getMessage() + "</error>");
		}
	}

	// ======== 데이터모델 재설계 1단계: 최신 스냅샷 편집 API (설계문서 40/41) ========

	/**
	 * 테이블(OBJ) 추가 — 최신 CLCT 스냅샷에 INSERT
	 */
	@RequestMapping(value = "/addObj", method = RequestMethod.POST)
	public Mono<Response> addObj(@RequestBody StdDataModelObjVo objVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			if ((objVo.getObjNm() == null || objVo.getObjNm().trim().isEmpty())
				&& (objVo.getObjNmKr() == null || objVo.getObjNmKr().trim().isEmpty()))
				throw new IllegalArgumentException("테이블명(물리명) 또는 한글명(논리명) 중 하나는 필수입니다.");

			// 86번 #11 — 영문명(물리) 정규식 검증. 영문/숫자/언더바만 허용 + 영문/언더바로 시작.
			if (objVo.getObjNm() != null && !objVo.getObjNm().trim().isEmpty()) {
				String en = objVo.getObjNm().trim();
				if (!en.matches("^[A-Za-z_][A-Za-z0-9_]*$"))
					throw new IllegalArgumentException("테이블 영문명은 영문(A-Z,a-z)/숫자(0-9)/언더바(_)만 허용되며 영문 또는 언더바로 시작해야 합니다. (입력값: " + en + ")");
				if (en.length() > 128)
					throw new IllegalArgumentException("테이블 영문명이 너무 깁니다 (최대 128자, 입력 길이: " + en.length() + ")");
				objVo.setObjNm(en);
			}

			// 물리명이 비어있으면 TMP_TBL_N 자동 생성 (한글명만 입력된 논리 모델 케이스)
			if (objVo.getObjNm() == null || objVo.getObjNm().trim().isEmpty()) {
				Integer cnt = sqlSessionTemplate.selectOne("datamodel.countDataModelObjByDm", objVo.getDataModelId());
				int seq = (cnt == null ? 0 : cnt) + 1;
				objVo.setObjNm("TMP_TBL_" + seq);
			}

			// 86번 #11 — OBJ_OWNER 정규화 (PK 일부, NULL 비허용)
			if (objVo.getObjOwner() == null) objVo.setObjOwner("");
			// 109번 #3 — 소유자·테이블스페이스는 [DB 반영] DDL 에 들어간다
			DdlSafety.identOpt("소유자", objVo.getObjOwner());
			if (objVo.getTablespaceNm() != null) {
				objVo.setTablespaceNm(objVo.getTablespaceNm().trim());
				DdlSafety.identOpt("테이블스페이스", objVo.getTablespaceNm());
			}

			// 물리명 중복 체크 — (DM_ID, OBJ_OWNER, OBJ_NM) 조합 기준
			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", objVo.getDataModelId());
			dupParam.put("objOwner",    objVo.getObjOwner());
			dupParam.put("objNm",       objVo.getObjNm());
			Integer dup = sqlSessionTemplate.selectOne("datamodel.countDataModelObjByDmId", dupParam);
			if (dup != null && dup > 0) throw new IllegalStateException("이미 존재하는 테이블입니다: " + objVo.getObjNm());

			// 104번 WP8-31 — 사용자 정의 속성: 필수·유형 검사 후 테이블과 같은 트랜잭션으로 저장
			Map<String, String> udpVals = udpService.check(new HashMap<>(), objVo.getUdpVals());

			if (objVo.getObjAttrCnt() == 0) objVo.setObjAttrCnt((short) 0);
			// 88번 거버넌스 — 관리자=APPROVED 즉시, 사용자=DRAFT
			String addObjAprvStatus = changeHistory.resolveAprvStatusForUserChange();
			objVo.setAprvStatus(addObjAprvStatus);
			objVo.setRequesterUserId(changeHistory.safeUserId());
			objVo.setReqDt(changeHistory.currentDt());
			if ("APPROVED".equals(addObjAprvStatus)) {
				objVo.setAprvUserId(objVo.getRequesterUserId());
				objVo.setAprvDt(objVo.getReqDt());
			}
			session.insert("datamodel.insertDataModelObj", objVo);
			udpService.save(session, objVo.getDataModelId(), objVo.getObjOwner(), objVo.getObjNm(), udpVals, changeHistory.safeUserId());
			changeHistory.record(session, objVo.getDataModelId(), "ADD_OBJ", "TIER1",
					objVo.getObjOwner(), objVo.getObjNm(), null, null, null,
					null, toJsonSafe(objVo),
					changeHistory.generateDdlSnippet(objVo.getDataModelId(), "ADD_OBJ", objVo.getObjOwner(), objVo.getObjNm(), null, null, objVo),
					addObjAprvStatus);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			session.rollback();
			log.error(">> addObj failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	// ======== 87-3: PK / FK 자동 생성·삭제 endpoint ========
	// WP0 — 네 API 모두 변경 이력·승인을 거친다 (deleteAttr·saveAttrs 와 같은 방식).
	//   관리자: 지금처럼 바로 반영 + APPROVED 이력.
	//   일반 사용자: 모델에 대기 상태로 반영 + DRAFT 이력 — 추가는 대기 상태 행(APRV_STATUS='DRAFT'),
	//   삭제는 USE_YN='N' 으로 감춤. 승인하면 확정, 반려하면 이력의 BEFORE_JSON 으로 되돌린다
	//   (DataModelApprovalController.applyConstraintDecision).

	/** WP0 — PK/FK 변경 전 컬럼의 PK·FK 표시값. 반려 때 이 값으로 되돌린다. */
	private Map<String, Object> attrKeySnapshot(SqlSession session, String dataModelId, String objOwner,
	                                            String objNm, String attrNm) {
		Map<String, Object> sel = new HashMap<>();
		sel.put("dataModelId", dataModelId);
		sel.put("objOwner",    objOwner);
		sel.put("objNm",       objNm);
		sel.put("attrNm",      attrNm);
		StdDataModelAttrVo a = session.selectOne("datamodel.selectDataModelAttrOne", sel);
		Map<String, Object> snap = new HashMap<>();
		snap.put("objOwner", objOwner);
		snap.put("objNm",    objNm);
		snap.put("attrNm",   attrNm);
		if (a != null) {
			snap.put("pkYn",             a.getPkYn());
			snap.put("fkYn",             a.getFkYn());
			snap.put("fkParentObjNm",    a.getFkParentObjNm());
			snap.put("fkParentObjOwner", a.getFkParentObjOwner());
			snap.put("fkParentAttrNm",   a.getFkParentAttrNm());
		}
		return snap;
	}

	/** WP0 — 제약조건·인덱스 행에 승인 상태를 싣는다 (insertConstraintRow / insertIndexRow). */
	private void putAprvColumns(Map<String, Object> row, String aprvStatus, String requester, String reqDt) {
		row.put("aprvStatus",      aprvStatus);
		row.put("requesterUserId", requester);
		row.put("reqDt",           reqDt);
		if ("APPROVED".equals(aprvStatus)) {
			row.put("aprvUserId", requester);
			row.put("aprvDt",     reqDt);
		}
	}

	/** WP0 — 승인 대기로 남긴 변경이면 응답 문구로 알린다 (관리자는 바로 반영이라 OK). */
	private void setChangeResult(Response res, String aprvStatus) {
		if ("APPROVED".equals(aprvStatus)) {
			res.setResultInfo(RestResult.CODE_200);
		} else {
			res.setResultInfo(RestResult.CODE_200.getCode(),
					"승인 대기(DRAFT)로 저장했습니다. [내 변경 신청]으로 관리자에게 신청하세요.");
		}
	}

	/**
	 * PK 생성. body: { dataModelId, objOwner, tableNm, pkName(옵션), columns: [attrNm, ...] }
	 * - pkName 미입력 시 PK_<TABLE> 자동 (중복이면 _2 _3 접미)
	 * - 같은 테이블에 PK 이미 있으면 거부 (PK 1개 제한). PK 삭제가 승인 대기 중이어도 거부
	 * - TB_DATA_MODEL_CONSTRAINT (TYPE='P') + TB_DATA_MODEL_INDEX (UNIQUENESS='UNIQUE') row 들 INSERT
	 * - 해당 ATTR 들의 PK_YN='Y' 갱신
	 * - 변경 이력 ADD_PK (관리자 APPROVED / 일반 사용자 DRAFT)
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("/createPk")
	public Mono<Response> createPk(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = (String) body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : (String) body.get("objOwner");
			String tableNm     = (String) body.get("tableNm");
			List<String> columns = (List<String>) body.get("columns");
			if (dataModelId == null || tableNm == null || columns == null || columns.isEmpty())
				throw new IllegalArgumentException("dataModelId/tableNm/columns 필수");

			Map<String, Object> p = new HashMap<>();
			p.put("dataModelId", dataModelId);
			p.put("objOwner",    objOwner);
			p.put("objNm",       tableNm);
			int existing = session.selectOne("datamodel.countPkOfTable", p);
			if (existing > 0) throw new IllegalStateException("이미 PK 가 있는 테이블입니다. 기존 PK 를 먼저 삭제하세요.");
			// WP0 — PK 삭제가 승인 대기 중이면 받지 않는다. 그 삭제가 반려되면 기존 PK 가 되살아나 PK 가 둘이 된다.
			Map<String, Object> pend = new HashMap<>();
			pend.put("dmId",       dataModelId);
			pend.put("objOwner",   objOwner);
			pend.put("objNm",      tableNm);
			pend.put("changeType", "DEL_PK");
			int pendingDel = session.selectOne("dmChangeHistory.countPendingByObj", pend);
			if (pendingDel > 0) throw new IllegalStateException("이 테이블의 PK 삭제가 승인 대기 중입니다. 승인·반려된 뒤에 PK 를 만드세요.");

			String pkName = (String) body.get("pkName");
			if (pkName == null || pkName.trim().isEmpty()) pkName = ddlRules.pkName(tableNm.toUpperCase());   // 104번 WP3 — DDL 규칙의 PK 이름 규칙
			pkName = pkName.trim().toUpperCase();
			// 모델 내 이름 중복이면 _2, _3 접미
			Map<String, Object> chk = new HashMap<>();
			chk.put("dataModelId", dataModelId);
			String candidate = pkName;
			int suffix = 2;
			while (true) {
				chk.put("constraintNm", candidate);
				int dup = session.selectOne("datamodel.countConstraintByName", chk);
				if (dup == 0) break;
				candidate = pkName + "_" + (suffix++);
			}
			pkName = candidate;
			// 109번 #3 — 이름은 [DB 반영] DDL 에 들어간다
			DdlSafety.identOpt("소유자", objOwner);
			DdlSafety.ident("테이블", tableNm);
			DdlSafety.ident("PK", pkName);
			for (String col : columns) DdlSafety.ident("PK 컬럼", col);

			String idxName = pkName; // 인덱스 이름은 PK 이름과 동일 (관례)
			// WP0 — 관리자=APPROVED 즉시, 일반 사용자=DRAFT
			String aprvStatus = changeHistory.resolveAprvStatusForUserChange();
			String requester  = changeHistory.safeUserId();
			String reqDt      = changeHistory.currentDt();
			List<Map<String, Object>> beforeCols = new ArrayList<>();
			// CONSTRAINT row 들 + INDEX row 들 INSERT
			for (int i = 0; i < columns.size(); i++) {
				String col = columns.get(i);
				Map<String, Object> cr = new HashMap<>();
				cr.put("dataModelId", dataModelId);
				cr.put("objOwner",    objOwner);
				cr.put("objNm",       tableNm);
				cr.put("constraintNm", pkName);
				cr.put("constraintType", "P");
				cr.put("columnNm",    col);
				cr.put("columnPos",   i + 1);
				cr.put("status",      "ENABLED");
				putAprvColumns(cr, aprvStatus, requester, reqDt);
				session.insert("datamodel.insertConstraintRow", cr);

				Map<String, Object> ir = new HashMap<>();
				ir.put("dataModelId", dataModelId);
				ir.put("objOwner",    objOwner);
				ir.put("objNm",       tableNm);
				ir.put("indexNm",     idxName);
				ir.put("indexType",   "NORMAL");
				ir.put("uniqueness",  "UNIQUE");
				ir.put("columnNm",    col);
				ir.put("columnPos",   i + 1);
				ir.put("sortOrder",   "ASC");
				putAprvColumns(ir, aprvStatus, requester, reqDt);
				session.insert("datamodel.insertIndexRow", ir);

				beforeCols.add(attrKeySnapshot(session, dataModelId, objOwner, tableNm, col));
				Map<String, Object> ap = new HashMap<>();
				ap.put("dataModelId", dataModelId);
				ap.put("objOwner",    objOwner);
				ap.put("objNm",       tableNm);
				ap.put("attrNm",      col);
				ap.put("pkYn",        "Y");
				session.update("datamodel.updateAttrPkYn", ap);
			}
			Map<String, Object> before = new HashMap<>();
			before.put("columns", beforeCols);
			Map<String, Object> after = new HashMap<>();
			after.put("pkName",  pkName);
			after.put("columns", columns);
			changeHistory.record(session, dataModelId, "ADD_PK", "TIER1",
					objOwner, tableNm, null, pkName, idxName,
					toJsonSafe(before), toJsonSafe(after),
					changeHistory.generateConstraintDdlSnippet(dataModelId, "ADD_PK", objOwner, tableNm, pkName, columns,
							null, null, null, false),
					aprvStatus);
			session.commit();
			res.setContents(pkName);
			setChangeResult(res, aprvStatus);
		} catch (IllegalArgumentException | IllegalStateException e) {
			session.rollback();
			res.setResultInfo(400, e.getMessage());
		} catch (Exception e) {
			session.rollback();
			log.error(">> createPk failed: {}", e.getMessage(), e);
			res.setResultInfo(500, e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(res);
	}

	/**
	 * FK 생성. body: { dataModelId, objOwner, tableNm, fkName(옵션), refOwner, refTableNm,
	 *                  mappings: [{ ownAttrNm, refAttrNm }, ...] }
	 * 변경 이력 ADD_FK (관리자 APPROVED / 일반 사용자 DRAFT).
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("/createFk")
	public Mono<Response> createFk(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = (String) body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : (String) body.get("objOwner");
			String tableNm     = (String) body.get("tableNm");
			String refOwner    = body.get("refOwner") == null ? "" : (String) body.get("refOwner");
			String refTableNm  = (String) body.get("refTableNm");
			List<Map<String, String>> mappings = (List<Map<String, String>>) body.get("mappings");
			if (dataModelId == null || tableNm == null || refTableNm == null
					|| mappings == null || mappings.isEmpty())
				throw new IllegalArgumentException("dataModelId/tableNm/refTableNm/mappings 필수");

			String fkName = (String) body.get("fkName");
			if (fkName == null || fkName.trim().isEmpty())
				fkName = "FK_" + tableNm.toUpperCase() + "_" + refTableNm.toUpperCase();
			fkName = fkName.trim().toUpperCase();
			Map<String, Object> chk = new HashMap<>();
			chk.put("dataModelId", dataModelId);
			String candidate = fkName;
			int suffix = 2;
			while (true) {
				chk.put("constraintNm", candidate);
				int dup = session.selectOne("datamodel.countConstraintByName", chk);
				if (dup == 0) break;
				candidate = fkName + "_" + (suffix++);
			}
			fkName = candidate;
			// 109번 #3 — 이름은 [DB 반영] DDL 에 들어간다
			DdlSafety.identOpt("소유자", objOwner);
			DdlSafety.ident("테이블", tableNm);
			DdlSafety.identOpt("참조 소유자", refOwner);
			DdlSafety.ident("참조 테이블", refTableNm);
			DdlSafety.ident("FK", fkName);
			for (Map<String, String> m : mappings) {
				DdlSafety.ident("FK 컬럼", m.get("ownAttrNm"));
				DdlSafety.ident("참조 컬럼", m.get("refAttrNm"));
			}

			// WP0 — 관리자=APPROVED 즉시, 일반 사용자=DRAFT
			String aprvStatus = changeHistory.resolveAprvStatusForUserChange();
			String requester  = changeHistory.safeUserId();
			String reqDt      = changeHistory.currentDt();
			List<Map<String, Object>> beforeCols = new ArrayList<>();
			List<String> ownCols = new ArrayList<>();
			List<String> refCols = new ArrayList<>();
			for (int i = 0; i < mappings.size(); i++) {
				Map<String, String> m = mappings.get(i);
				String ownCol = m.get("ownAttrNm");
				String refCol = m.get("refAttrNm");
				if (ownCol == null || refCol == null)
					throw new IllegalArgumentException("mappings[].ownAttrNm/refAttrNm 누락");
				ownCols.add(ownCol);
				refCols.add(refCol);

				Map<String, Object> cr = new HashMap<>();
				cr.put("dataModelId",  dataModelId);
				cr.put("objOwner",     objOwner);
				cr.put("objNm",        tableNm);
				cr.put("constraintNm", fkName);
				cr.put("constraintType", "R");
				cr.put("columnNm",     ownCol);
				cr.put("columnPos",    i + 1);
				cr.put("refOwner",     refOwner);
				cr.put("refTableNm",   refTableNm);
				cr.put("refColumnNm",  refCol);
				cr.put("status",       "ENABLED");
				putAprvColumns(cr, aprvStatus, requester, reqDt);
				session.insert("datamodel.insertConstraintRow", cr);

				beforeCols.add(attrKeySnapshot(session, dataModelId, objOwner, tableNm, ownCol));
				Map<String, Object> ap = new HashMap<>();
				ap.put("dataModelId",     dataModelId);
				ap.put("objOwner",        objOwner);
				ap.put("objNm",           tableNm);
				ap.put("attrNm",          ownCol);
				ap.put("fkYn",            "Y");
				ap.put("fkParentObjNm",   refTableNm);
				ap.put("fkParentObjOwner", refOwner);
				ap.put("fkParentAttrNm",  refCol);
				session.update("datamodel.updateAttrFk", ap);
			}
			Map<String, Object> before = new HashMap<>();
			before.put("columns", beforeCols);
			Map<String, Object> after = new HashMap<>();
			after.put("fkName",     fkName);
			after.put("refOwner",   refOwner);
			after.put("refTableNm", refTableNm);
			after.put("mappings",   mappings);
			changeHistory.record(session, dataModelId, "ADD_FK", "TIER1",
					objOwner, tableNm, null, fkName, null,
					toJsonSafe(before), toJsonSafe(after),
					changeHistory.generateConstraintDdlSnippet(dataModelId, "ADD_FK", objOwner, tableNm, fkName, ownCols,
							refOwner, refTableNm, refCols, false),
					aprvStatus);
			session.commit();
			res.setContents(fkName);
			setChangeResult(res, aprvStatus);
		} catch (IllegalArgumentException e) {
			session.rollback();
			res.setResultInfo(400, e.getMessage());
		} catch (Exception e) {
			session.rollback();
			log.error(">> createFk failed: {}", e.getMessage(), e);
			res.setResultInfo(500, e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(res);
	}

	/**
	 * PK 삭제 (이름 기준). 관련 ATTR 들의 PK_YN='N', CONSTRAINT/INDEX 물리 DELETE.
	 */
	/**
	 * 87-x — 특정 PK 가 해제될 때 cascade 될 외부 FK 목록 미리보기 (FE 확인 모달용)
	 */
	@PostMapping("/getPkDependentFks")
	public Map<String, Object> getPkDependentFks(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new HashMap<>();
		try {
			String dataModelId = (String) body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : (String) body.get("objOwner");
			String tableNm     = (String) body.get("tableNm");
			String pkName      = (String) body.get("pkName");
			Map<String, Object> sp = new HashMap<>();
			sp.put("dataModelId", dataModelId);
			sp.put("objOwner",    objOwner);
			sp.put("objNm",       tableNm);
			List<Map<String, Object>> rows = sqlSessionTemplate.selectList("datamodel.selectConstraintsByTable", sp);
			List<String> pkCols = new ArrayList<>();
			for (Map<String, Object> r : rows) {
				if (pkName != null && pkName.equalsIgnoreCase((String) r.get("constraintNm")))
					pkCols.add((String) r.get("columnNm"));
			}
			out.put("pkColumns", pkCols);
			if (pkCols.isEmpty()) { out.put("dependentFks", new ArrayList<>()); return out; }
			Map<String, Object> fp = new HashMap<>();
			fp.put("dataModelId", dataModelId);
			fp.put("refOwner",    objOwner);
			fp.put("refTableNm",  tableNm);
			fp.put("pkColumns",   pkCols);
			out.put("dependentFks", sqlSessionTemplate.selectList("datamodel.selectInboundFksByPkOfTable", fp));
		} catch (Exception e) {
			log.error(">> getPkDependentFks failed: {}", e.getMessage(), e);
			out.put("error", e.getMessage());
		}
		return out;
	}

	@PostMapping("/deletePk")
	public Mono<Response> deletePk(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = (String) body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : (String) body.get("objOwner");
			String tableNm     = (String) body.get("tableNm");
			String pkName      = (String) body.get("pkName");
			boolean cascadeFk  = Boolean.TRUE.equals(body.get("cascadeFk"));
			if (dataModelId == null || tableNm == null || pkName == null)
				throw new IllegalArgumentException("dataModelId/tableNm/pkName 필수");

			// WP0 — 관리자는 지금처럼 바로 지우고(APPROVED), 일반 사용자는 USE_YN='N' 으로 감춰 두고
			// 승인 때 지운다(DRAFT). 컬럼 PK·FK 표시는 둘 다 바로 내리고, 반려 때 BEFORE_JSON 으로 되돌린다.
			String aprvStatus = changeHistory.resolveAprvStatusForUserChange();
			boolean immediate = "APPROVED".equals(aprvStatus);
			String deletedDt  = changeHistory.currentDt();

			// 1) 삭제 전 — 어떤 컬럼들이 PK 구성원인지 조회
			Map<String, Object> sp = new HashMap<>();
			sp.put("dataModelId", dataModelId);
			sp.put("objOwner",    objOwner);
			sp.put("objNm",       tableNm);
			List<Map<String, Object>> rows = session.selectList("datamodel.selectConstraintsByTable", sp);
			List<String> pkCols = new ArrayList<>();
			List<Map<String, Object>> beforeCols = new ArrayList<>();
			for (Map<String, Object> r : rows) {
				if (!pkName.equalsIgnoreCase((String) r.get("constraintNm"))) continue;
				pkName = (String) r.get("constraintNm");   // DB 에 저장된 이름 그대로 (이력·승인 처리가 이 이름으로 찾는다)
				pkCols.add((String) r.get("columnNm"));
				beforeCols.add(attrKeySnapshot(session, dataModelId, objOwner, tableNm, (String) r.get("columnNm")));
				Map<String, Object> ap = new HashMap<>();
				ap.put("dataModelId", dataModelId);
				ap.put("objOwner",    objOwner);
				ap.put("objNm",       tableNm);
				ap.put("attrNm",      r.get("columnNm"));
				ap.put("pkYn",        "N");
				session.update("datamodel.updateAttrPkYn", ap);
			}

			// 87-x — PK 컬럼을 참조하는 외부 FK constraint 도 cascade 삭제 (cascadeFk=true 일 때만)
			List<Map<String, Object>> cascadedFks = new ArrayList<>();
			if (cascadeFk && !pkCols.isEmpty()) {
				Map<String, Object> fp = new HashMap<>();
				fp.put("dataModelId", dataModelId);
				fp.put("refOwner",    objOwner);
				fp.put("refTableNm",  tableNm);
				fp.put("pkColumns",   pkCols);
				List<Map<String, Object>> inFks = session.selectList("datamodel.selectInboundFksByPkOfTable", fp);
				for (Map<String, Object> fk : inFks) {
					String fkOwner    = fk.get("objOwner") == null ? "" : (String) fk.get("objOwner");
					String fkTableNm  = (String) fk.get("tableNm");
					String fkName     = (String) fk.get("constraintNm");
					Map<String, Object> rows2 = new HashMap<>();
					rows2.put("dataModelId", dataModelId);
					rows2.put("objOwner",    fkOwner);
					rows2.put("objNm",       fkTableNm);
					List<Map<String, Object>> fkRows = session.selectList("datamodel.selectConstraintsByTable", rows2);
					List<Map<String, Object>> fkBeforeCols = new ArrayList<>();
					for (Map<String, Object> rr : fkRows) {
						if (!fkName.equalsIgnoreCase((String) rr.get("constraintNm"))) continue;
						fkBeforeCols.add(attrKeySnapshot(session, dataModelId, fkOwner, fkTableNm, (String) rr.get("columnNm")));
						Map<String, Object> ap2 = new HashMap<>();
						ap2.put("dataModelId", dataModelId);
						ap2.put("objOwner",    fkOwner);
						ap2.put("objNm",       fkTableNm);
						ap2.put("attrNm",      rr.get("columnNm"));
						ap2.put("fkYn",        "N");
						ap2.put("fkParentObjNm",  null);
						ap2.put("fkParentObjOwner", null);
						ap2.put("fkParentAttrNm", null);
						session.update("datamodel.updateAttrFk", ap2);
					}
					Map<String, Object> dp2 = new HashMap<>();
					dp2.put("dataModelId",  dataModelId);
					dp2.put("objOwner",     fkOwner);
					dp2.put("objNm",        fkTableNm);
					dp2.put("constraintNm", fkName);
					if (immediate) {
						session.delete("datamodel.deleteConstraintByName", dp2);
					} else {
						dp2.put("deletedDt", deletedDt);
						session.update("datamodel.softDeleteConstraintPending", dp2);
					}
					Map<String, Object> cascaded = new HashMap<>();
					cascaded.put("objOwner",     fkOwner);
					cascaded.put("objNm",        fkTableNm);
					cascaded.put("constraintNm", fkName);
					cascaded.put("columns",      fkBeforeCols);
					cascadedFks.add(cascaded);
				}
			}

			// 2) CONSTRAINT / INDEX — 관리자는 물리 DELETE, 일반 사용자는 감춤
			Map<String, Object> dp = new HashMap<>();
			dp.put("dataModelId",  dataModelId);
			dp.put("objOwner",     objOwner);
			dp.put("objNm",        tableNm);
			dp.put("constraintNm", pkName);

			Map<String, Object> dx = new HashMap<>();
			dx.put("dataModelId", dataModelId);
			dx.put("objOwner",    objOwner);
			dx.put("objNm",       tableNm);
			dx.put("indexNm",     pkName);
			if (immediate) {
				session.delete("datamodel.deleteConstraintByName", dp);
				session.delete("datamodel.deleteIndexByName", dx);
			} else {
				dp.put("deletedDt", deletedDt);
				dx.put("deletedDt", deletedDt);
				session.update("datamodel.softDeleteConstraintPending", dp);
				session.update("datamodel.softDeleteIndexPending", dx);
			}

			Map<String, Object> before = new HashMap<>();
			before.put("pkName",      pkName);
			before.put("columns",     beforeCols);
			before.put("cascadedFks", cascadedFks);
			changeHistory.record(session, dataModelId, "DEL_PK", "TIER1",
					objOwner, tableNm, null, pkName, pkName,
					toJsonSafe(before), null,
					changeHistory.generateConstraintDdlSnippet(dataModelId, "DEL_PK", objOwner, tableNm, pkName, pkCols,
							null, null, null, !cascadedFks.isEmpty()),
					aprvStatus);
			session.commit();
			setChangeResult(res, aprvStatus);
		} catch (IllegalArgumentException e) {
			session.rollback();
			res.setResultInfo(400, e.getMessage());
		} catch (Exception e) {
			session.rollback();
			log.error(">> deletePk failed: {}", e.getMessage(), e);
			res.setResultInfo(500, e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(res);
	}

	/**
	 * FK 삭제 (이름 기준). 관련 ATTR 들의 FK_YN='N', FK_PARENT_* clear, CONSTRAINT DELETE.
	 * 변경 이력 DEL_FK — 관리자는 물리 삭제(APPROVED), 일반 사용자는 USE_YN='N' 으로 감추고 승인 때 지운다(DRAFT).
	 */
	@PostMapping("/deleteFk")
	public Mono<Response> deleteFk(@RequestBody Map<String, String> body) {
		Response res = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = body.get("dataModelId");
			String objOwner    = body.get("objOwner") == null ? "" : body.get("objOwner");
			String tableNm     = body.get("tableNm");
			String fkName      = body.get("fkName");
			if (dataModelId == null || tableNm == null || fkName == null)
				throw new IllegalArgumentException("dataModelId/tableNm/fkName 필수");

			String aprvStatus = changeHistory.resolveAprvStatusForUserChange();

			Map<String, Object> sp = new HashMap<>();
			sp.put("dataModelId", dataModelId);
			sp.put("objOwner",    objOwner);
			sp.put("objNm",       tableNm);
			List<Map<String, Object>> rows = session.selectList("datamodel.selectConstraintsByTable", sp);
			List<Map<String, Object>> beforeCols = new ArrayList<>();
			List<String> ownCols = new ArrayList<>();
			for (Map<String, Object> r : rows) {
				if (!fkName.equalsIgnoreCase((String) r.get("constraintNm"))) continue;
				fkName = (String) r.get("constraintNm");   // DB 에 저장된 이름 그대로 (이력·승인 처리가 이 이름으로 찾는다)
				ownCols.add((String) r.get("columnNm"));
				beforeCols.add(attrKeySnapshot(session, dataModelId, objOwner, tableNm, (String) r.get("columnNm")));
				Map<String, Object> ap = new HashMap<>();
				ap.put("dataModelId",     dataModelId);
				ap.put("objOwner",        objOwner);
				ap.put("objNm",           tableNm);
				ap.put("attrNm",          r.get("columnNm"));
				ap.put("fkYn",            "N");
				ap.put("fkParentObjNm",   null);
				ap.put("fkParentObjOwner", null);
				ap.put("fkParentAttrNm",  null);
				session.update("datamodel.updateAttrFk", ap);
			}

			Map<String, Object> dp = new HashMap<>();
			dp.put("dataModelId",  dataModelId);
			dp.put("objOwner",     objOwner);
			dp.put("objNm",        tableNm);
			dp.put("constraintNm", fkName);
			if ("APPROVED".equals(aprvStatus)) {
				session.delete("datamodel.deleteConstraintByName", dp);
			} else {
				dp.put("deletedDt", changeHistory.currentDt());
				session.update("datamodel.softDeleteConstraintPending", dp);
			}
			Map<String, Object> before = new HashMap<>();
			before.put("fkName",  fkName);
			before.put("columns", beforeCols);
			changeHistory.record(session, dataModelId, "DEL_FK", "TIER1",
					objOwner, tableNm, null, fkName, null,
					toJsonSafe(before), null,
					changeHistory.generateConstraintDdlSnippet(dataModelId, "DEL_FK", objOwner, tableNm, fkName, ownCols,
							null, null, null, false),
					aprvStatus);
			session.commit();
			setChangeResult(res, aprvStatus);
		} catch (IllegalArgumentException e) {
			session.rollback();
			res.setResultInfo(400, e.getMessage());
		} catch (Exception e) {
			session.rollback();
			log.error(">> deleteFk failed: {}", e.getMessage(), e);
			res.setResultInfo(500, e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(res);
	}

	/**
	 * 테이블 단위 제약조건 목록 (PK/FK) — 컬럼 그리드 칩 표시용
	 */
	@GetMapping("/getConstraintsByTable")
	public List<Map<String, Object>> getConstraintsByTable(@RequestParam String dataModelId,
	                                                        @RequestParam(required = false) String objOwner,
	                                                        @RequestParam String objNm) {
		Map<String, Object> p = new HashMap<>();
		p.put("dataModelId", dataModelId);
		p.put("objOwner",    objOwner == null ? "" : objOwner);
		p.put("objNm",       objNm);
		return sqlSessionTemplate.selectList("datamodel.selectConstraintsByTable", p);
	}

	/**
	 * 모델 단위 제약조건 일괄 — 컬럼 그리드 칩 매핑 (N+1 회피)
	 */
	@GetMapping("/getConstraintsByDmId")
	public List<Map<String, Object>> getConstraintsByDmId(@RequestParam String dataModelId) {
		return sqlSessionTemplate.selectList("datamodel.selectConstraintsByDmId", dataModelId);
	}

	// ======== / 87-3 ========

	/**
	 * 테이블(OBJ) 물리명 rename 영향 범위 미리보기.
	 *
	 * 변경 전 사용자에게 "이 변경으로 N건이 함께 갱신됩니다" 알리기 위한 카운트 조회.
	 *
	 * @return attrCnt, indexCnt, constraintCnt, refConstraintCnt, conflict (newObjNm 이미 존재 여부)
	 */
	@GetMapping(value = "/previewObjRename")
	public Map<String, Object> previewObjRename(@RequestParam("dataModelId") String dataModelId,
	                                            @RequestParam("origObjNm") String origObjNm,
	                                            @RequestParam("newObjNm") String newObjNm) {
		Map<String, Object> q = new HashMap<>();
		q.put("dataModelId", dataModelId);

		Map<String, Object> result = new HashMap<>();
		q.put("objNm", origObjNm);
		result.put("attrCnt",           sqlSessionTemplate.selectOne("datamodel.countObjAttrForRename", q));
		result.put("indexCnt",          sqlSessionTemplate.selectOne("datamodel.countObjIndexForRename", q));
		result.put("constraintCnt",     sqlSessionTemplate.selectOne("datamodel.countObjConstraintForRename", q));
		result.put("refConstraintCnt",  sqlSessionTemplate.selectOne("datamodel.countObjRefConstraintForRename", q));

		// 새 이름 충돌 검사 (자기 자신은 제외)
		boolean conflict = false;
		if (newObjNm != null && !newObjNm.equals(origObjNm)) {
			q.put("objNm", newObjNm);
			Integer exists = sqlSessionTemplate.selectOne("datamodel.existsObjNmInModel", q);
			conflict = exists != null && exists > 0;
		}
		result.put("conflict", conflict);
		return result;
	}

	/**
	 * 테이블(OBJ) 수정 — 논리명/오너/설명. origObjNm 가 다르면 물리명 rename 도 cascade 처리.
	 */
	@RequestMapping(value = "/updateObj", method = RequestMethod.POST)
	public Mono<Response> updateObj(@RequestBody Map<String, Object> body) {
		Response result = new Response();
		// rename/owner cascade 는 최대 5+4+2 개 테이블을 연달아 바꾼다.
		// sqlSessionTemplate 은 문장 단위 자동 커밋이라 중간에 실패하면 앞부분만 반영되고
		// 되돌릴 수 없다 (컬럼은 새 이름, 인덱스는 옛 이름으로 갈리는 영구 불일치).
		// → deleteObjs 와 동일하게 단일 세션 트랜잭션으로 묶는다.
		SqlSession session = null;
		try {
			String dataModelId = (String) body.get("dataModelId");
			String origObjNm   = (String) body.get("origObjNm");
			String newObjNm    = (String) body.get("objNm");
			String objNmKr     = (String) body.get("objNmKr");
			String objOwner    = (String) body.get("objOwner");
			// 86번 #11 — origOwner 미전달 케이스 호환. 변경 전 OWNER 가 없으면 새 OWNER 와 동일하다고 간주.
			String origOwner   = body.get("origObjOwner") != null ? (String) body.get("origObjOwner") : objOwner;
			String objDesc     = (String) body.get("objDesc");
			// PK 의 OWNER 정규화 (NULL → '')
			objOwner  = objOwner  == null ? "" : objOwner;
			origOwner = origOwner == null ? "" : origOwner;

			boolean rename     = origObjNm != null && newObjNm != null && !origObjNm.equals(newObjNm);
			boolean ownerChange = !origOwner.equals(objOwner);

			// 86번 #11 — 영문명(물리) 정규식 검증. 영문/숫자/언더바만 + 영문/언더바로 시작.
			if (newObjNm != null && !newObjNm.trim().isEmpty()) {
				String en = newObjNm.trim();
				if (!en.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
					result.setResultInfo(RestResult.CODE_500.getCode(),
						"테이블 영문명은 영문(A-Z,a-z)/숫자(0-9)/언더바(_)만 허용되며 영문 또는 언더바로 시작해야 합니다. (입력값: " + en + ")");
					return Mono.just(result);
				}
				if (en.length() > 128) {
					result.setResultInfo(RestResult.CODE_500.getCode(),
						"테이블 영문명이 너무 깁니다 (최대 128자, 입력 길이: " + en.length() + ")");
					return Mono.just(result);
				}
				newObjNm = en;
			}
			// 109번 #3 — 바꾼 소유자·테이블스페이스는 [DB 반영] DDL 에 들어간다
			if (ownerChange) DdlSafety.identOpt("소유자", objOwner);
			Map<String, Object> curKey = new HashMap<>();
			curKey.put("dmId", dataModelId);
			curKey.put("objOwner", origOwner);
			curKey.put("objNm", origObjNm != null ? origObjNm : newObjNm);
			StdDataModelObjVo curObj = sqlSessionTemplate.selectOne("ddlRule.selectObj", curKey);
			String newTs = blankToNull(body.get("tablespaceNm"));
			if (newTs != null && (curObj == null || !newTs.equals(blankToNull(curObj.getTablespaceNm()))))
				DdlSafety.ident("테이블스페이스", newTs);

			// 104번 WP8-31 — 사용자 정의 속성: 보냈을 때만 검사·저장 (안 보내면 그대로)
			String finalObjNm = newObjNm != null ? newObjNm : origObjNm;
			String origNmForUdp = origObjNm != null ? origObjNm : finalObjNm;
			Map<String, String> udpVals = null;
			if (body.containsKey("udpVals")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> sentUdp = (Map<String, Object>) body.get("udpVals");
				udpVals = udpService.check(udpService.objVals(dataModelId, origOwner, origNmForUdp), sentUdp);
			}

			if (rename) {
				// 충돌 재확인 (프론트 preview 후 다른 세션이 같은 이름 만들 수 있음)
				Map<String, Object> chk = new HashMap<>();
				chk.put("dataModelId", dataModelId);
				chk.put("objNm",       newObjNm);
				chk.put("objOwner",    objOwner);
				Integer exists = sqlSessionTemplate.selectOne("datamodel.existsObjNmInModel", chk);
				if (exists != null && exists > 0) {
					result.setResultInfo(RestResult.CODE_500.getCode(), "이미 같은 이름의 테이블이 존재합니다: " + newObjNm);
					return Mono.just(result);
				}

				// 86번 #11 — rename cascade. WHERE 매칭은 origOwner 기준 (rename 이 owner 변경을 동반하지 않으므로 origOwner = objOwner)
				Map<String, Object> rn = new HashMap<>();
				rn.put("dataModelId", dataModelId);
				rn.put("origObjNm",   origObjNm);
				rn.put("newObjNm",    newObjNm);
				rn.put("objOwner",    origOwner);

				if (session == null) session = sqlSessionFactory.openSession();
				session.update("datamodel.renameObjAttrCascade",          rn);
				session.update("datamodel.renameObjIndexCascade",         rn);
				session.update("datamodel.renameObjConstraintCascade",    rn);
				session.update("datamodel.renameObjConstraintRefCascade", rn);
				session.update("datamodel.renameObjFkParentCascade",      rn);   // DEF-03
				session.update("datamodel.renameObjPhysical",             rn);
			}

			// 86번 #11 — OBJ_OWNER 변경 cascade (OBJ 본체 update 보다 먼저 — sub rows 의 OLD owner 매칭이 살아있을 때 갱신).
			if (ownerChange) {
				Map<String, Object> ownParam = new HashMap<>();
				ownParam.put("dataModelId", dataModelId);
				ownParam.put("objNm",       newObjNm);
				ownParam.put("objOwner",    origOwner);  // OLD — WHERE 매칭
				ownParam.put("newOwner",    objOwner);   // NEW — SET 적용
				if (session == null) session = sqlSessionFactory.openSession();
				session.update("datamodel.cascadeAttrOwner",          ownParam);
				session.update("datamodel.cascadeIndexOwner",         ownParam);
				session.update("datamodel.cascadeConstraintOwner",    ownParam);
				session.update("datamodel.cascadeConstraintRefOwner", ownParam);
			}

			// 한글명/오너/설명 update — PK 가 OWNER 포함이라 OWNER 변경 시 row 자체 ID 가 바뀜
			// → ownerChange 이면 OLD PK row 를 직접 update 못 함. 이 경우 OBJ row 도 OWNER 만 update 하는 별도 매퍼 필요.
			// 임시: ownerChange 이면 일단 cascade 호출 후 OBJ.OWNER 도 갱신 — updateDataModelObj 매퍼는 PK 로 매칭하므로 OLD 키로 호출.
			StdDataModelObjVo objVo = new StdDataModelObjVo();
			objVo.setDataModelId(dataModelId);
			objVo.setObjNm(newObjNm);
			objVo.setObjNmKr(objNmKr);
			objVo.setObjOwner(ownerChange ? origOwner : objOwner);  // 일단 OLD owner 로 매칭
			objVo.setObjDesc(objDesc);
			// 88번 §15 — 테이블스페이스(물리)/업무영역(논리)/주제영역 수정 반영
			objVo.setTablespaceNm((String) body.get("tablespaceNm"));
			objVo.setBizAreaId((String) body.get("bizAreaId"));
			objVo.setSubjAreaId((String) body.get("subjAreaId"));
			objVo.setSubjAreaKeep(!body.containsKey("subjAreaId"));   // 104번 WP8-38 — 안 보내면 그대로 (예전엔 늘 지웠다)
			// PK 는 (DM_ID, OBJ_OWNER, OBJ_NM). 0건이면 대상이 아예 없는 것 → 성공으로 오인 방지.
			// 트랜잭션 안이므로 return 이 아니라 throw — catch 에서 rollback 후 동일 메시지로 500.
			if (session == null) session = sqlSessionFactory.openSession();
			int objUpdated = session.update("datamodel.updateDataModelObj", objVo);
			if (objUpdated == 0) {
				throw new IllegalStateException(
					"수정 대상 테이블을 찾지 못했습니다. (dmId=" + dataModelId
					+ ", owner=" + objVo.getObjOwner() + ", obj=" + newObjNm + ")");
			}
			if (ownerChange) {
				// OBJ 의 OWNER 자체를 OLD → NEW 로 변경 (PK 일부)
				Map<String, Object> ownerUpd = new HashMap<>();
				ownerUpd.put("dataModelId", dataModelId);
				ownerUpd.put("objNm",       newObjNm);
				ownerUpd.put("objOwner",    origOwner);  // WHERE 매칭
				ownerUpd.put("newOwner",    objOwner);
				session.update("datamodel.updateObjOwnerKey", ownerUpd);
			}
			// 104번 WP8-31 — 이름·소유자가 바뀌면 속성 값도 따라간다
			if (rename || ownerChange) {
				Map<String, Object> rv = new HashMap<>();
				rv.put("dmId",      dataModelId);
				rv.put("origOwner", origOwner);
				rv.put("origObjNm", origNmForUdp);
				rv.put("newOwner",  objOwner);
				rv.put("newObjNm",  finalObjNm);
				session.update("udp.renameObjVals", rv);
			}
			if (udpVals != null) udpService.save(session, dataModelId, objOwner, finalObjNm, udpVals, changeHistory.safeUserId());
			// 여기까지 전부 성공해야 커밋. 이력 기록은 커밋 이후 별도 트랜잭션.
			session.commit();

			// 88번 거버넌스 — updateObj 이력 (Tier 1 if owner/rename, else Tier 2)
			String updObjTier = (rename || ownerChange) ? "TIER1" : "TIER2";
			String updObjAprvStatus = "TIER1".equals(updObjTier)
					? changeHistory.resolveAprvStatusForUserChange()
					: "APPROVED";
			// DEF-04 — 변경 전/후 값을 남겨야 반려 시 되돌릴 수 있다. 그동안 둘 다 null 이었다.
			Map<String, Object> beforeObj = new HashMap<>();
			beforeObj.put("objOwner", origOwner);
			beforeObj.put("objNm",    origObjNm);
			Map<String, Object> afterObj = new HashMap<>();
			afterObj.put("objOwner", objOwner);
			afterObj.put("objNm",    newObjNm != null ? newObjNm : origObjNm);
			// 104번 WP3 — 이름·소유자가 바뀐 경우(TIER1)만 조각
			changeHistory.record(null, dataModelId, "MODIFY_OBJ", updObjTier,
					objOwner, newObjNm != null ? newObjNm : origObjNm, null, null, null,
					toJsonSafe(beforeObj), toJsonSafe(afterObj),
					"TIER1".equals(updObjTier)
							? changeHistory.generateModifyObjSnippet(dataModelId, origOwner, origObjNm, objOwner, newObjNm != null ? newObjNm : origObjNm)
							: null,
					updObjAprvStatus);

			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			if (session != null) {
				try { session.rollback(); } catch (Exception ignore) {}
			}
			log.error(">> updateObj failed : {}", e.getMessage(), e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			if (session != null) {
				try { session.close(); } catch (Exception ignore) {}
			}
		}
		return Mono.just(result);
	}

	/**
	 * 테이블(OBJ) 삭제 — 하위 컬럼 함께 삭제
	 */
	@RequestMapping(value = "/deleteObj", method = RequestMethod.POST)
	public Mono<Response> deleteObj(@RequestBody StdDataModelObjVo objVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			Map<String, Object> param = new HashMap<>();
			param.put("dataModelId", objVo.getDataModelId());
			param.put("objOwner",    objVo.getObjOwner() == null ? "" : objVo.getObjOwner());  // 86번 #11
			param.put("objNm",       objVo.getObjNm());
			String delObjBefore = toJsonSafe(session.selectOne("datamodel.selectDataModelObjOne", param));   // 104번 WP9-55
			// DEF-05 — 승인 대기 삭제는 감춰만 둔다. DEF-02 — 승인된 삭제는 참조까지 정리.
			String delObjAprvStatus = changeHistory.resolveAprvStatusForUserChange();
			if ("APPROVED".equals(delObjAprvStatus)) {
				cascadeDeleteObjRefs(session, param);
				session.delete("datamodel.deleteDataModelAttrsByObj", param);
				session.delete("datamodel.deleteDataModelObj", param);
			} else {
				param.put("deletedDt", changeHistory.currentDt());
				session.update("datamodel.softDeleteAttrsByObjPending", param);
				session.update("datamodel.softDeleteObjPending", param);
			}
			changeHistory.record(session, objVo.getDataModelId(), "DEL_OBJ", "TIER1",
					(String)param.get("objOwner"), objVo.getObjNm(), null, null, null,
					delObjBefore, null,
					changeHistory.generateDdlSnippet(objVo.getDataModelId(), "DEL_OBJ", (String)param.get("objOwner"), objVo.getObjNm(), null, null, null),
					delObjAprvStatus);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			session.rollback();
			log.error(">> deleteObj failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 테이블 다건 삭제 — 선택삭제/전체삭제 (모두 같은 흐름).
	 * 각 테이블의 하위 컬럼·인덱스·제약조건 cascade 삭제 + 변경 이력 기록 (Tier 1).
	 * 단일 트랜잭션 — 하나라도 실패하면 전체 롤백.
	 *
	 * 요청: { dataModelId, items: [ {objOwner, objNm}, ... ] }
	 * 응답: { deleted: N, errors: [{objNm, message}] }
	 */
	@RequestMapping(value = "/deleteObjs", method = RequestMethod.POST)
	@SuppressWarnings("unchecked")
	public Mono<Response> deleteObjs(@RequestBody Map<String, Object> body) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = (String) body.get("dataModelId");
			Object rawItems = body.get("items");
			if (dataModelId == null || dataModelId.trim().isEmpty())
				throw new IllegalArgumentException("dataModelId 누락");
			if (!(rawItems instanceof List))
				throw new IllegalArgumentException("items 배열이 아닙니다.");
			List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
			if (items.isEmpty())
				throw new IllegalArgumentException("삭제 대상이 비어있습니다.");

			int deleted = 0;
			List<Map<String, Object>> errors = new ArrayList<>();
			for (Map<String, Object> it : items) {
				String objOwner = it.get("objOwner") == null ? "" : (String) it.get("objOwner");
				String objNm    = (String) it.get("objNm");
				if (objNm == null || objNm.trim().isEmpty()) {
					Map<String, Object> e = new HashMap<>();
					e.put("objNm", "(빈값)"); e.put("message", "objNm 누락"); errors.add(e);
					continue;
				}
				try {
					Map<String, Object> param = new HashMap<>();
					param.put("dataModelId", dataModelId);
					param.put("objOwner",    objOwner);
					param.put("objNm",       objNm);
					String delBefore = toJsonSafe(session.selectOne("datamodel.selectDataModelObjOne", param));   // 104번 WP9-55
					String aprvStatus = changeHistory.resolveAprvStatusForUserChange();
					if ("APPROVED".equals(aprvStatus)) {
						cascadeDeleteObjRefs(session, param);   // DEF-02
						session.delete("datamodel.deleteDataModelAttrsByObj", param);
						session.delete("datamodel.deleteDataModelObj", param);
					} else {
						param.put("deletedDt", changeHistory.currentDt());   // DEF-05
						session.update("datamodel.softDeleteAttrsByObjPending", param);
						session.update("datamodel.softDeleteObjPending", param);
					}
					changeHistory.record(session, dataModelId, "DEL_OBJ", "TIER1",
							objOwner, objNm, null, null, null,
							delBefore, null,
							changeHistory.generateDdlSnippet(dataModelId, "DEL_OBJ", objOwner, objNm, null, null, null),
							aprvStatus);
					deleted++;
				} catch (Exception inner) {
					Map<String, Object> e = new HashMap<>();
					e.put("objNm", objNm); e.put("message", inner.getMessage());
					errors.add(e);
					throw inner; // 트랜잭션 전체 롤백
				}
			}
			session.commit();
			Map<String, Object> data = new HashMap<>();
			data.put("deleted", deleted);
			data.put("errors", errors);
			result.setResultInfo(RestResult.CODE_200);
			result.setContents(new ObjectMapper().writeValueAsString(data));
		} catch (Exception e) {
			session.rollback();
			log.error(">> deleteObjs failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 한글명으로 표준 용어 조회 → 영문약어명 + 도메인(타입/길이) 반환
	 * 컬럼 추가 시 [표준 적용] 버튼에서 호출
	 */
	// ===== 104번 WP5-11 — 사전과 상위 사전에서 승인 용어 찾기 (가까운 사전 우선, 찾은 사전을 dictId 에) =====

	private com.ndata.quality.model.std.StdTermsVo approvedTermByEng(String dictId, String eng) {
		for (String d : dictService.lineage(dictId)) {
			com.ndata.quality.model.std.StdTermsVo t = sqlSessionTemplate.selectOne("terms.selectApprovedTermsByEngNm",
					dictService.nameParam(d, "termsEngAbrvNm", eng));
			if (t != null) {
				t.setDictId(d);
				return t;
			}
		}
		return null;
	}

	private com.ndata.quality.model.std.StdTermsVo approvedTermByNm(String dictId, String nm) {
		for (String d : dictService.lineage(dictId)) {
			com.ndata.quality.model.std.StdTermsVo t = sqlSessionTemplate.selectOne("terms.selectApprovedTermsByNm",
					dictService.nameParam(d, "termsNm", nm));
			if (t != null) {
				t.setDictId(d);
				return t;
			}
		}
		return null;
	}

	// ===== 104번 WP7-26 — 컬럼 → 용어·단어·도메인·코드 (역방향 영향도) =====

	/**
	 * 컬럼이 기대는 표준. 모델 사전에서 영문명 = 용어 영문약어명인 승인 용어(없으면 한글명 = 용어명·이음동의어),
	 * 그 용어의 구성 단어(용어가 없으면 영문명 토큰과 같은 영문약어 단어), 도메인, 코드(용어에 코드 그룹이 있으면).
	 */
	@RequestMapping(value = "/impact/column", method = RequestMethod.GET)
	public Map<String, Object> columnImpact(@RequestParam String dmId, @RequestParam String attrNm,
			@RequestParam(required = false) String attrNmKr) {
		String dictId = dictService.resolve(null, dmId);
		Map<String, Object> out = new HashMap<>();
		com.ndata.quality.model.std.StdTermsVo term = approvedTermByEng(dictId, attrNm.trim());
		String matchType = "ENG";
		if (term == null && attrNmKr != null && !attrNmKr.trim().isEmpty()) {
			term = approvedTermByNm(dictId, attrNmKr.trim());
			matchType = "KR";
		}
		out.put("dictId", dictId);
		if (term != null) {
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("termsId", term.getId());
			t.put("termsNm", term.getTermsNm());
			t.put("termsEngAbrvNm", term.getTermsEngAbrvNm());
			t.put("matchType", matchType);
			t.put("codeGrp", term.getCodeGrp());
			out.put("term", t);
			out.put("words", sqlSessionTemplate.selectList("impact.selectTermWords", term.getId()));
			if (term.getDomainNm() != null) {
				com.ndata.quality.model.std.StdDomainVo d = sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm",
						dictService.nameParam(term.getDictId(), "domainNm", term.getDomainNm()));
				if (d != null) {
					Map<String, Object> dm = new LinkedHashMap<>();
					dm.put("domainNm", d.getDomainNm());
					dm.put("dataType", d.getDataType());
					dm.put("dataLen", d.getDataLen());
					dm.put("dataDecimalLen", d.getDataDecimalLen());
					out.put("domain", dm);
				}
			}
		} else {
			List<String> tokens = splitTokens(attrNm);
			Map<String, Object> wp = dictService.dictParam(dictId);
			wp.put("engAbrvNms", tokens);
			List<StdWordVo> words = tokens.isEmpty() ? new ArrayList<>() : sqlSessionTemplate.selectList("word.selectWordsByEngAbrvNms", wp);
			words.sort((x, y) -> tokens.indexOf(x.getWordEngAbrvNm()) - tokens.indexOf(y.getWordEngAbrvNm()));   // 컬럼명 토큰 순서
			out.put("words", words);
		}
		return out;
	}

	// ===== 104번 WP7-24 — 컬럼 한글명 용어 자동완성 =====

	/** 모델 사전의 승인 용어를 용어명·이음동의어로 찾아 20건 (도메인 타입·길이 포함) */
	@RequestMapping(value = "/termSuggest", method = RequestMethod.GET)
	public List<Map<String, Object>> termSuggest(@RequestParam String dmId, @RequestParam String q) {
		String kw = q == null ? "" : q.trim();
		if (kw.isEmpty()) return new ArrayList<>();
		Map<String, Object> p = dictService.lineageParam(dictService.resolve(null, dmId));   // 104번 WP5-11 — 상위 사전 포함
		p.put("q", kw);
		List<Map<String, Object>> raw = sqlSessionTemplate.selectList("terms.selectTermSuggest", p);
		List<Map<String, Object>> list = nearestByEng(raw, p.get("dictIds"));
		for (Map<String, Object> t : list) {
			String nm = (String) t.get("termsNm");
			if (nm != null && !nm.toLowerCase().contains(kw.toLowerCase())) {
				Map<String, Object> sp = new HashMap<>();
				sp.put("termsId", t.get("termsId"));
				sp.put("q", kw);
				t.put("synonym", sqlSessionTemplate.selectOne("terms.selectTermSynonymHit", sp));
			}
		}
		return list;
	}

	/** 104번 WP5-11 — 같은 영문약어 용어가 사전과 상위 사전에 다 있으면 가까운 사전 것만 (순서는 그대로). 상위 사전 것에는 inheritedDictNm */
	private List<Map<String, Object>> nearestByEng(List<Map<String, Object>> rows, Object dictIds) {
		@SuppressWarnings("unchecked")
		List<String> lin = (List<String>) dictIds;
		Map<String, Map<String, Object>> best = new LinkedHashMap<>();
		for (Map<String, Object> t : rows) {
			String key = (String) t.get("termsEngAbrvNm");
			Map<String, Object> prev = best.get(key);
			if (prev == null || lin.indexOf(t.get("dictId")) < lin.indexOf(prev.get("dictId"))) best.put(key, t);
		}
		List<Map<String, Object>> out = new ArrayList<>(best.values());
		for (Map<String, Object> t : out) {
			if (!lin.get(0).equals(t.get("dictId"))) {
				Object id = t.get("dictId");
				t.put("inheritedDictNm", dictService.list().stream().filter(x -> id.equals(x.get("dictId"))).map(x -> x.get("dictNm")).findFirst().orElse(id));
			}
		}
		return out;
	}

	// ===== 104번 WP7-23 — 컬럼별 표준 용어 후보 =====

	/**
	 * 컬럼 한글명으로 모델 사전의 승인 용어 후보를 찾는다 (컬럼마다 5건, 점수 순).
	 * ① 한글명 = 용어명(100)·이음동의어(95) ② 한글명을 단어 분석(용어 등록 자동 분석과 같은 엔진)해 만든 영문약어 = 용어 영문약어(90)
	 * ③ 나눈 단어를 모두 쓰는 용어(70) ④ 이름 일부 일치(50).
	 *
	 * @param body {dmId, attrs:[{objOwner, objNm, attrNm, attrNmKr}]}
	 * @return {items:[{objOwner, objNm, attrNm, attrNmKr, candidates:[{termsId, termsNm, termsEngAbrvNm, domainNm, dataType, dataLen, dataDecimalLen, reason, score}]}]}
	 */
	@RequestMapping(value = "/suggestTerms", method = RequestMethod.POST)
	public Map<String, Object> suggestTerms(@RequestBody Map<String, Object> body) {
		String dmId = (String) body.get("dmId");
		String dictId = dictService.resolve(null, dmId);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> attrs = (List<Map<String, Object>>) body.get("attrs");
		if (attrs == null) attrs = new ArrayList<>();
		if (attrs.size() > 2000) throw new IllegalArgumentException("한 번에 2,000개 컬럼까지 찾을 수 있습니다.");

		// 한글명마다 단어 분석 한 번 (같은 한글명은 묶는다)
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		for (Map<String, Object> a : attrs) {
			String kr = a.get("attrNmKr") == null ? "" : a.get("attrNmKr").toString().replaceAll("\\s", "");
			if (!kr.isEmpty()) names.add(kr);
		}
		Map<String, com.ndata.quality.model.std.TermAnalysisResult> analysis = new HashMap<>();
		if (!names.isEmpty()) {
			Map<String, Object> req = new HashMap<>();
			req.put("termNames", new ArrayList<>(names));
			req.put("dictId", dictId);
			for (com.ndata.quality.model.std.TermAnalysisResult r : stdController.analyzeTermsBatch(req)) {
				analysis.put(r.getInputNm(), r);
			}
		}

		Map<String, List<Map<String, Object>>> byName = new HashMap<>();
		List<Map<String, Object>> items = new ArrayList<>();
		for (Map<String, Object> a : attrs) {
			String kr = a.get("attrNmKr") == null ? "" : a.get("attrNmKr").toString().replaceAll("\\s", "");
			Map<String, Object> it = new LinkedHashMap<>();
			it.put("objOwner", a.get("objOwner"));
			it.put("objNm", a.get("objNm"));
			it.put("attrNm", a.get("attrNm"));
			it.put("attrNmKr", a.get("attrNmKr"));
			it.put("candidates", kr.isEmpty() ? new ArrayList<>() : byName.computeIfAbsent(kr, k -> termCandidates(dictId, k, analysis.get(k))));
			items.add(it);
		}
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		return out;
	}

	private List<Map<String, Object>> termCandidates(String dictId, String kr, com.ndata.quality.model.std.TermAnalysisResult r) {
		Map<String, Map<String, Object>> found = new LinkedHashMap<>();
		// ① 용어명·이음동의어
		com.ndata.quality.model.std.StdTermsVo t = approvedTermByNm(dictId, kr);
		if (t != null) {
			Map<String, Object> p = dictService.nameParam(t.getDictId(), "termsEngAbrvNm", t.getTermsEngAbrvNm());
			for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("terms.selectTermCandByEng", p)) {
				if (!t.getId().equals(c.get("termsId"))) continue;
				boolean exact = kr.equals(t.getTermsNm());
				addCand(found, c, exact ? "한글명 일치" : "유사어 일치 ('" + kr + "')", exact ? 100 : 95);
			}
		}
		if (r != null && r.getWords() != null && !r.getWords().isEmpty()) {
			boolean allMatched = r.getWords().stream().allMatch(w -> "MATCHED".equals(w.getStatus()) && w.getSelected() != null);
			// ② 단어 분석 영문약어
			if (allMatched && r.getRecommendedEngAbrvNm() != null && !r.getRecommendedEngAbrvNm().isEmpty()) {
				Map<String, Object> ep = dictService.lineageParam(dictId);
				ep.put("termsEngAbrvNm", r.getRecommendedEngAbrvNm());
				for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("terms.selectTermCandByEng", ep)) {
					addCand(found, c, "단어 분석 영문약어 일치 (" + r.getRecommendedEngAbrvNm() + ")", 90);
				}
			}
			// ③ 나눈 단어를 모두 쓰는 용어
			List<String> wordIds = new ArrayList<>();
			List<String> wordNms = new ArrayList<>();
			for (com.ndata.quality.model.std.TermAnalysisResult.WordAnalysis w : r.getWords()) {
				if ("MATCHED".equals(w.getStatus()) && w.getSelected() != null && w.getSelected().getWordId() != null) {
					if (!wordIds.contains(w.getSelected().getWordId())) wordIds.add(w.getSelected().getWordId());
					wordNms.add(w.getSelected().getWordNm());
				}
			}
			if (!wordIds.isEmpty()) {
				Map<String, Object> p = dictService.lineageParam(dictId);
				p.put("wordIds", wordIds);
				p.put("wordCnt", wordIds.size());
				for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("terms.selectTermCandByWords", p)) {
					addCand(found, c, "단어 포함 (" + String.join("+", wordNms) + ")", 70);
				}
			}
		}
		// ④ 이름 일부 일치
		Map<String, Object> p = dictService.lineageParam(dictId);
		p.put("kr", kr);
		for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("terms.selectTermCandByContains", p)) {
			addCand(found, c, "이름 일부 일치", 50);
		}
		List<Map<String, Object>> list = new ArrayList<>(found.values());
		list.sort((x, y) -> ((Integer) y.get("score")) - ((Integer) x.get("score")));
		return list.size() > 5 ? new ArrayList<>(list.subList(0, 5)) : list;
	}

	private static void addCand(Map<String, Map<String, Object>> found, Map<String, Object> c, String reason, int score) {
		String id = (String) c.get("termsEngAbrvNm");
		if (found.containsKey(id)) return;   // 먼저 찾은(점수가 높은) 이유를 남긴다 (같은 영문약어는 상위 사전 용어여도 한 번만)
		Map<String, Object> m = new LinkedHashMap<>(c);
		m.put("reason", reason);
		m.put("score", score);
		found.put(id, m);
	}

	@RequestMapping(value = "/resolveStandard", method = RequestMethod.GET)
	public Map<String, Object> resolveStandard(@RequestParam String termsNm,
			@RequestParam(required = false) String dmId,
			@RequestParam(required = false) String dictId) {
		// 98번 — 대상 모델이 오면 그 모델의 사전으로, 아니면 요청의 사전 / 기본 사전.
		return resolveTermsInternal(termsNm, dictService.resolve(dictId, dmId));
	}

	/**
	 * 비표준 컬럼(TERMS_STND_YN='N') 일괄/선택 표준 변환.
	 * attrs 미지정 → dataModelId의 모든 비표준 컬럼.
	 * attrs 지정 → {objNm, attrNm} 키로 지정된 컬럼만.
	 * 건별 독립 처리 — 한 건 실패가 전체를 롤백하지 않음.
	 */
	@SuppressWarnings("unchecked")
	@PostMapping(value = "/resolveAttrs")
	public Map<String, Object> resolveAttrs(@RequestBody Map<String, Object> req) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> failedList = new ArrayList<>();
		int tried = 0, succeeded = 0, failed = 0;

		String dataModelId = (String) req.get("dataModelId");
		if (dataModelId == null || dataModelId.trim().isEmpty()) {
			result.put("tried", 0);
			result.put("succeeded", 0);
			result.put("failed", 0);
			result.put("failedList", failedList);
			result.put("message", "dataModelId는 필수입니다.");
			return result;
		}

		List<Map<String, String>> attrs = (List<Map<String, String>>) req.get("attrs");
		boolean dryRun = Boolean.TRUE.equals(req.get("dryRun"));
		List<StdDataModelAttrVo> targets;
		if (dryRun && attrs != null && !attrs.isEmpty()) {
			// dryRun: 사용자가 그리드에서 dirty 입력 중인 값 그대로 사용 (DB lookup 우회)
			targets = new ArrayList<>();
			for (Map<String, String> a : attrs) {
				StdDataModelAttrVo vo = new StdDataModelAttrVo();
				vo.setDataModelId(dataModelId);
				vo.setObjOwner(a.get("objOwner"));
				vo.setObjNm(a.get("objNm"));
				vo.setAttrNm(a.get("attrNm"));
				vo.setAttrNmKr(a.get("attrNmKr"));
				targets.add(vo);
			}
		} else if (attrs == null || attrs.isEmpty()) {
			targets = sqlSessionTemplate.selectList("datamodel.selectNonStandardAttrs", dataModelId);
		} else {
			Map<String, Object> p = new HashMap<>();
			p.put("dataModelId", dataModelId);
			p.put("attrs", attrs);
			targets = sqlSessionTemplate.selectList("datamodel.selectAttrListByKeys", p);
		}

		List<Map<String, Object>> items = new ArrayList<>();
		for (StdDataModelAttrVo attr : targets) {
			tried++;
			try {
				if (dryRun) {
					Map<String, Object> resolved = lookupForKr(attr);
					items.add(resolved);
				} else {
					applyResolvedToAttr(attr);
				}
				succeeded++;
			} catch (Exception e) {
				failed++;
				Map<String, Object> fi = new HashMap<>();
				fi.put("objOwner", attr.getObjOwner());
				fi.put("objNm", attr.getObjNm());
				fi.put("attrNm", attr.getAttrNm());
				fi.put("attrNmKr", attr.getAttrNmKr());
				fi.put("reason", e.getMessage());
				failedList.add(fi);
			}
		}

		result.put("tried", tried);
		result.put("succeeded", succeeded);
		result.put("failed", failed);
		result.put("failedList", failedList);
		result.put("items", items);
		return result;
	}

	/**
	 * 영문명(ATTR_NM) 기준 컬럼 표준화.
	 * TB_TERMS.TERMS_ENG_ABRV_NM 으로 용어 조회 → 한글명/데이터타입/길이/소수점 채움.
	 * 영문명은 그대로 유지.
	 */
	@SuppressWarnings("unchecked")
	@PostMapping(value = "/resolveAttrsByEng")
	public Map<String, Object> resolveAttrsByEng(@RequestBody Map<String, Object> req) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> failedList = new ArrayList<>();
		int tried = 0, succeeded = 0, failed = 0;

		String dataModelId = (String) req.get("dataModelId");
		if (dataModelId == null || dataModelId.trim().isEmpty()) {
			result.put("tried", 0);
			result.put("succeeded", 0);
			result.put("failed", 0);
			result.put("failedList", failedList);
			result.put("message", "dataModelId는 필수입니다.");
			return result;
		}

		List<Map<String, String>> attrs = (List<Map<String, String>>) req.get("attrs");
		boolean dryRunByEng = Boolean.TRUE.equals(req.get("dryRun"));
		List<StdDataModelAttrVo> targets;
		if (attrs == null || attrs.isEmpty()) {
			result.put("tried", 0);
			result.put("succeeded", 0);
			result.put("failed", 0);
			result.put("failedList", failedList);
			result.put("message", "선택된 컬럼이 없습니다.");
			return result;
		}
		if (dryRunByEng) {
			// dryRun: 사용자가 그리드에서 dirty 입력 중인 영문명 그대로 사용
			targets = new ArrayList<>();
			for (Map<String, String> a : attrs) {
				StdDataModelAttrVo vo = new StdDataModelAttrVo();
				vo.setDataModelId(dataModelId);
				vo.setObjOwner(a.get("objOwner"));
				vo.setObjNm(a.get("objNm"));
				vo.setAttrNm(a.get("attrNm"));
				vo.setAttrNmKr(a.get("attrNmKr"));
				targets.add(vo);
			}
		} else {
			Map<String, Object> p = new HashMap<>();
			p.put("dataModelId", dataModelId);
			p.put("attrs", attrs);
			targets = sqlSessionTemplate.selectList("datamodel.selectAttrListByKeys", p);
		}
		List<Map<String, Object>> itemsByEng = new ArrayList<>();
		for (StdDataModelAttrVo attr : targets) {
			tried++;
			try {
				if (dryRunByEng) {
					Map<String, Object> resolved = lookupForEng(attr);
					itemsByEng.add(resolved);
				} else {
					applyResolvedToAttrByEng(attr);
				}
				succeeded++;
			} catch (Exception e) {
				failed++;
				Map<String, Object> fi = new HashMap<>();
				fi.put("objOwner", attr.getObjOwner());
				fi.put("objNm", attr.getObjNm());
				fi.put("attrNm", attr.getAttrNm());
				fi.put("attrNmKr", attr.getAttrNmKr());
				fi.put("reason", e.getMessage());
				failedList.add(fi);
			}
		}

		result.put("tried", tried);
		result.put("succeeded", succeeded);
		result.put("failed", failed);
		result.put("failedList", failedList);
		result.put("items", itemsByEng);
		return result;
	}

	/** dryRun 용 — 한글명 기준 lookup 후 새 값 반환 (DB UPDATE 안 함) */
	private Map<String, Object> lookupForKr(StdDataModelAttrVo attr) {
		String attrNmKr = attr.getAttrNmKr();
		if (attrNmKr == null || attrNmKr.trim().isEmpty())
			throw new IllegalStateException("한글명 없음");
		// 98번 — 이 컬럼이 속한 모델의 사전으로 용어를 찾는다.
		Map<String, Object> resolved = resolveTermsInternal(attrNmKr,
				dictService.resolve(null, attr.getDataModelId()));
		Boolean found = (Boolean) resolved.get("found");
		if (found == null || !found)
			throw new IllegalStateException((String) resolved.getOrDefault("message", "용어 미등록"));
		if (Boolean.TRUE.equals(resolved.get("domainMissing")))
			throw new IllegalStateException("도메인 미등록");
		String newAttrNm = (String) resolved.get("termsEngAbrvNm");
		if (newAttrNm == null || newAttrNm.trim().isEmpty())
			throw new IllegalStateException("용어 영문약어명 누락");
		long dataLen = 0L;
		Object dl = resolved.get("dataLen");
		if (dl instanceof Number) dataLen = ((Number) dl).longValue();
		short dataDecimalLen = 0;
		Object ddl = resolved.get("dataDecimalLen");
		if (ddl instanceof Number) dataDecimalLen = ((Number) ddl).shortValue();

		Map<String, Object> item = new HashMap<>();
		item.put("objOwner", attr.getObjOwner());                    // 스키마까지 있어야 행이 식별된다
		item.put("objNm", attr.getObjNm());
		item.put("attrNm", attr.getAttrNm());                        // 기존 (식별용)
		item.put("newAttrNm", newAttrNm);                            // 새 영문명
		// 104번 WP7-8 — 한글명이 용어 이음동의어(유사어)로 맞았으면 표준 용어명으로 바꾼다 (표준 검사는 한글명 = 용어명)
		String stdKr = (String) resolved.get("termsNm");
		item.put("newAttrNmKr", stdKr != null ? stdKr : attrNmKr);
		if (stdKr != null && !stdKr.equals(attrNmKr.trim())) item.put("synonymOf", stdKr);
		item.put("newDataType", resolved.get("dataType"));
		item.put("newDataLen", dataLen);
		item.put("newDataDecimalLen", dataDecimalLen);
		return item;
	}

	/** dryRun 용 — 영문명 기준 lookup 후 새 값 반환 (DB UPDATE 안 함) */
	private Map<String, Object> lookupForEng(StdDataModelAttrVo attr) {
		String engNm = attr.getAttrNm();
		if (engNm == null || engNm.trim().isEmpty())
			throw new IllegalStateException("영문명 없음");
		// 98번 — 이 컬럼이 속한 모델의 사전에서 찾는다.
		com.ndata.quality.model.std.StdTermsVo terms = approvedTermByEng(dictService.resolve(null, attr.getDataModelId()), engNm.trim());
		if (terms == null)
			throw new IllegalStateException("'" + engNm + "' 에 해당하는 표준 용어가 없습니다.");
		Map<String, Object> item = new HashMap<>();
		item.put("objOwner", attr.getObjOwner());                    // 스키마까지 있어야 행이 식별된다
		item.put("objNm", attr.getObjNm());
		item.put("attrNm", attr.getAttrNm());
		item.put("newAttrNm", engNm);                                // 영문명 그대로
		item.put("newAttrNmKr", terms.getTermsNm());                 // 새 한글명
		item.put("newDataType", terms.getDataType());
		item.put("newDataLen", (long) terms.getDataLen());
		item.put("newDataDecimalLen", (short) terms.getDataDecimalLen());
		return item;
	}

	/**
	 * 컬럼(ATTR) 추가 — 표준 검증 후 INSERT.
	 * WP0 — saveAttrs 의 ADD 와 같이 변경 이력(ADD_ATTR)·승인을 거친다:
	 * 관리자=APPROVED 즉시, 일반 사용자=DRAFT (승인 전에는 본인에게만 보이고 집계·진단에서 빠진다).
	 */
	@RequestMapping(value = "/addAttr", method = RequestMethod.POST)
	public Mono<Response> addAttr(@RequestBody StdDataModelAttrVo attrVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			if (attrVo.getObjNm() == null || attrVo.getObjNm().trim().isEmpty())
				throw new IllegalArgumentException("소속 테이블은 필수입니다.");
			if (attrVo.getAttrNmKr() == null || attrVo.getAttrNmKr().trim().isEmpty())
				throw new IllegalArgumentException("컬럼 한글명은 필수입니다.");
			// 영문명 UPPER 강제 (Oracle 기준 — 다른 DBMS 차후 분기)
			if (attrVo.getAttrNm() != null && !attrVo.getAttrNm().trim().isEmpty()) {
				attrVo.setAttrNm(attrVo.getAttrNm().trim().toUpperCase());
			}

			// 86번 #11 — OBJ_OWNER 미지정 시 부모 OBJ 에서 lookup
			if (attrVo.getObjOwner() == null || attrVo.getObjOwner().isEmpty()) {
				List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByDmId", attrVo.getDataModelId());
				for (StdDataModelObjVo o : objs) {
					if (attrVo.getObjNm().equals(o.getObjNm())) {
						attrVo.setObjOwner(o.getObjOwner() == null ? "" : o.getObjOwner());
						break;
					}
				}
				if (attrVo.getObjOwner() == null) attrVo.setObjOwner("");
			}

			boolean isStandard = !"N".equals(attrVo.getTermsStndYn());
			// 표준 컬럼: 물리명/타입 필수 + 표준 검증
			// 비표준 컬럼: 물리명 자동 생성(TMP_COL_{순번}) + 타입 기본값(VARCHAR(255))
			Map<String, Object> ordParam = new HashMap<>();
			ordParam.put("dataModelId", attrVo.getDataModelId());
			ordParam.put("objOwner",    attrVo.getObjOwner());
			ordParam.put("objNm",       attrVo.getObjNm());
			Short maxOrd = sqlSessionTemplate.selectOne("datamodel.selectMaxAttrOrd", ordParam);
			short nextOrd = (short) ((maxOrd == null ? 0 : maxOrd) + 1);
			attrVo.setAttrOrder(nextOrd);

			if (isStandard) {
				if (attrVo.getAttrNm() == null || attrVo.getAttrNm().trim().isEmpty())
					throw new IllegalArgumentException("표준 변환된 물리명이 필요합니다.");
				validateAttrStandards(attrVo);
				applyStandardFlags(attrVo);
			} else {
				// 비표준 저장: 물리명 자동 생성
				if (attrVo.getAttrNm() == null || attrVo.getAttrNm().trim().isEmpty()) {
					attrVo.setAttrNm("TMP_COL_" + nextOrd);
				}
				if (attrVo.getDataType() == null || attrVo.getDataType().trim().isEmpty()) {
					attrVo.setDataType("VARCHAR");
					if (attrVo.getDataLen() == 0) attrVo.setDataLen(255);
				}
				attrVo.setTermsStndYn("N");
				attrVo.setDomainStndYn("N");
			}
			checkAttrInput(attrVo, null);   // 109번 #3

			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", attrVo.getDataModelId());
			dupParam.put("objOwner",    attrVo.getObjOwner());  // 86번 #11
			dupParam.put("objNm",       attrVo.getObjNm());
			dupParam.put("attrNm",      attrVo.getAttrNm());
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelAttr", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 컬럼입니다: " + attrVo.getAttrNm());

			// WP0 — 88번 거버넌스. 관리자=APPROVED 즉시, 사용자=DRAFT (saveAttrs ADD 와 같은 처리)
			String addAprvStatus = changeHistory.resolveAprvStatusForUserChange();
			attrVo.setAprvStatus(addAprvStatus);
			attrVo.setRequesterUserId(changeHistory.safeUserId());
			attrVo.setReqDt(changeHistory.currentDt());
			if ("APPROVED".equals(addAprvStatus)) {
				attrVo.setAprvUserId(attrVo.getRequesterUserId());
				attrVo.setAprvDt(attrVo.getReqDt());
			}
			session.insert("datamodel.insertDataModelAttr", attrVo);
			session.update("datamodel.syncDataModelObjAttrCnt", dupParam);
			changeHistory.record(session, attrVo.getDataModelId(), "ADD_ATTR", "TIER1",
					attrVo.getObjOwner(), attrVo.getObjNm(), attrVo.getAttrNm(), null, null,
					null, toJsonSafe(attrVo),
					changeHistory.generateDdlSnippet(attrVo.getDataModelId(), "ADD_ATTR", attrVo.getObjOwner(), attrVo.getObjNm(),
							attrVo.getAttrNm(), attrVo, null),
					addAprvStatus);
			session.commit();
			setChangeResult(result, addAprvStatus);
		} catch (Exception e) {
			session.rollback();
			log.error(">> addAttr failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 컬럼(ATTR) 수정
	 */
	@RequestMapping(value = "/updateAttr", method = RequestMethod.POST)
	public Mono<Response> updateAttr(@RequestBody StdDataModelAttrVo attrVo) {
		Response result = new Response();
		try {
			// 109번 #3 — 바뀐 타입·길이·기본값 검사
			Map<String, Object> sel = new HashMap<>();
			sel.put("dataModelId", attrVo.getDataModelId());
			sel.put("objOwner",    attrVo.getObjOwner());
			sel.put("objNm",       attrVo.getObjNm());
			sel.put("attrNm",      attrVo.getAttrNm());
			StdDataModelAttrVo existing = sqlSessionTemplate.selectOne("datamodel.selectDataModelAttrOne", sel);
			if (existing != null) checkAttrInput(attrVo, existing);
			// 저장 시 표준 재평가 — 단어/용어/도메인 매칭이 끊긴 경우 자동 강등 (Y → N).
			// 사용자 시나리오: 이전에 표준이었던 컬럼의 데이터 타입/길이를 변경해 도메인 불일치가 발생한 경우.
			try {
				validateAttrStandards(attrVo);
				applyStandardFlags(attrVo);
			} catch (Exception stdErr) {
				attrVo.setTermsStndYn("N");
				attrVo.setDomainStndYn("N");
				log.info(">> updateAttr standard re-check downgraded: {}", stdErr.getMessage());
			}
			// PK 는 (DM_ID, OBJ_OWNER, OBJ_NM, ATTR_NM). objOwner 가 안 맞으면 0건 갱신인데
			// 예전엔 그대로 200 을 돌려줘 "저장됐다"고 오인됐다 → 갱신 건수 검사.
			int attrUpdated = sqlSessionTemplate.update("datamodel.updateDataModelAttr", attrVo);
			if (attrUpdated == 0) {
				result.setResultInfo(RestResult.CODE_500.getCode(),
					"수정 대상 컬럼을 찾지 못했습니다. (dmId=" + attrVo.getDataModelId()
					+ ", owner=" + attrVo.getObjOwner() + ", obj=" + attrVo.getObjNm()
					+ ", attr=" + attrVo.getAttrNm() + ")");
				return Mono.just(result);
			}
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateAttr failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 컬럼(ATTR) 삭제 — 87-3: PK/FK CONSTRAINT, INDEX, 외부 FK 참조까지 cascade
	 */
	@RequestMapping(value = "/deleteAttr", method = RequestMethod.POST)
	public Mono<Response> deleteAttr(@RequestBody StdDataModelAttrVo attrVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			Map<String, Object> param = new HashMap<>();
			param.put("dataModelId", attrVo.getDataModelId());
			param.put("objOwner",    attrVo.getObjOwner() == null ? "" : attrVo.getObjOwner());
			param.put("objNm",       attrVo.getObjNm());
			param.put("attrNm",      attrVo.getAttrNm());
			// 104번 WP9-55 — 결재 화면에서 무엇이 지워지는지 값으로 보이게 지우기 전 행을 남긴다
			String delAttrBefore = toJsonSafe(session.selectOne("datamodel.selectDataModelAttrOne", param));
			// DEF-05 — 승인이 필요한 사용자면 물리 삭제하지 않는다. 반려 시 되돌려야 한다.
			String delAttrStatus = changeHistory.resolveAprvStatusForUserChange();
			int attrDeleted;
			if ("APPROVED".equals(delAttrStatus)) {
				cascadeDeleteAttrRefs(session, param);
				// PK 는 (DM_ID, OBJ_OWNER, OBJ_NM, ATTR_NM). 0건이면 삭제된 게 없는데 200 이 나가던 문제.
				attrDeleted = session.delete("datamodel.deleteDataModelAttr", param);
			} else {
				param.put("deletedDt", changeHistory.currentDt());
				attrDeleted = session.update("datamodel.softDeleteAttrPending", param);
			}
			if (attrDeleted == 0) {
				session.rollback();
				result.setResultInfo(RestResult.CODE_500.getCode(),
					"삭제 대상 컬럼을 찾지 못했습니다. (dmId=" + attrVo.getDataModelId()
					+ ", owner=" + param.get("objOwner") + ", obj=" + attrVo.getObjNm()
					+ ", attr=" + attrVo.getAttrNm() + ")");
				return Mono.just(result);
			}
			session.update("datamodel.syncDataModelObjAttrCnt", param);
			// 단건 삭제도 이력을 남긴다. 그동안 그리드 배치 삭제만 기록돼,
			// 이 경로로 지운 컬럼은 승인 화면에도 변경 이력에도 나타나지 않았다.
			changeHistory.record(session, attrVo.getDataModelId(), "DEL_ATTR", "TIER1",
					(String) param.get("objOwner"), attrVo.getObjNm(), attrVo.getAttrNm(), null, null,
					delAttrBefore, null,
					changeHistory.generateDdlSnippet(attrVo.getDataModelId(), "DEL_ATTR", (String) param.get("objOwner"),
							attrVo.getObjNm(), attrVo.getAttrNm(), null, null),
					delAttrStatus);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			session.rollback();
			log.error(">> deleteAttr failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 87-3 — 컬럼 삭제 전 참조 조회 (FE 가 삭제 확인 시 무엇이 함께 정리될지 미리 보여주기 위함)
	 */
	@RequestMapping(value = "/getAttrReferences", method = RequestMethod.POST)
	public Map<String, Object> getAttrReferences(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("dataModelId", body.get("dataModelId"));
		p.put("objOwner",    body.get("objOwner") == null ? "" : body.get("objOwner"));
		p.put("objNm",       body.get("objNm"));
		p.put("attrNm",      body.get("attrNm"));
		Map<String, Object> refs = new HashMap<>();
		refs.put("ownConstraints", sqlSessionTemplate.selectList("datamodel.selectOwnConstraintRefsByAttr", p));
		refs.put("ownIndexes",     sqlSessionTemplate.selectList("datamodel.selectOwnIndexRefsByAttr",     p));
		refs.put("inboundFks",     sqlSessionTemplate.selectList("datamodel.selectInboundFkByAttr",        p));
		refs.put("fkParentAttrs",  sqlSessionTemplate.selectList("datamodel.selectFkParentAttrRefsByAttr", p));
		return refs;
	}

	/**
	 * 87-3 — 컬럼 삭제 시 cascade 정리 (CONSTRAINT, INDEX, 외부 FK, FK_PARENT_*)
	 */
	/**
	 * DEF-02 — 테이블 삭제 시 cascade 정리.
	 *
	 * 그동안 컬럼만 지우고 인덱스·제약조건·다른 테이블이 건 FK·FK_PARENT_* 참조를 남겼다.
	 * javadoc 은 "하위 컬럼 함께 삭제" 라고만 적혀 있었지만, 남은 참조는 ERD 에 끊어진
	 * 관계선으로 나타나고 재수집 시 유령 제약조건으로 되살아난다.
	 *
	 * 순서 주의 — 참조를 먼저 끊고 본체를 지운다.
	 */
	private void cascadeDeleteObjRefs(SqlSession session, Map<String, Object> param) {
		session.delete("datamodel.deleteIndexByObj",       param);
		session.delete("datamodel.deleteConstraintByObj",  param);
		session.delete("datamodel.deleteInboundFkByObj",   param);
		session.update("datamodel.clearFkParentRefByObj",  param);
		session.delete("udp.deleteObjVals",                param);   // 104번 WP8-31
	}

	private void cascadeDeleteAttrRefs(SqlSession session, Map<String, Object> param) {
		session.delete("datamodel.deleteConstraintByAttr",  param);
		session.delete("datamodel.deleteIndexByAttr",       param);
		session.delete("datamodel.deleteInboundFkByAttr",   param);
		session.update("datamodel.clearFkParentRefByAttr",  param);
	}

	/**
	 * 컬럼(ATTR) 배치 저장 — 그리드 인라인 편집 저장 (53번 §7-2).
	 * 논리 영역만 받는다: 물리명·타입·길이 등은 서버에서 TMP_COL_N / VARCHAR(255) 기본값.
	 * 단일 트랜잭션으로 ADD / UPDATE / DELETE 섞어서 처리.
	 *
	 * 요청: { dataModelId, objNm, attrs: [{ mode, attrNm?, attrNmKr?, pkYn?, fkYn?, nullableYn?, defaultVal?, attrDesc? }] }
	 * 응답: { saved, added, updated, deleted, errors:[{index, mode, message}] }
	 */
	@RequestMapping(value = "/saveAttrs", method = RequestMethod.POST)
	public Mono<Response> saveAttrs(@RequestBody Map<String, Object> body) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String dataModelId = (String) body.get("dataModelId");
			String objNm = (String) body.get("objNm");
			// 86번 #11 — saveAttrs 는 frontend 가 objOwner 명시 안 하면 null. 부모 OBJ 에서 lookup 필요.
			String objOwner = (String) body.get("objOwner");
			Object rawAttrs = body.get("attrs");

			if (dataModelId == null || dataModelId.trim().isEmpty())
				throw new IllegalArgumentException("dataModelId 누락");
			if (objNm == null || objNm.trim().isEmpty())
				throw new IllegalArgumentException("objNm 누락");
			if (!(rawAttrs instanceof List))
				throw new IllegalArgumentException("attrs 배열이 아닙니다.");
			// 104번 WP7-25 — 표준 준수 필수 모델은 비표준 컬럼 추가·수정을 거부한다 (행에 예외 사유가 있으면 결재로)
			boolean stndReq = "Y".equals(sqlSessionTemplate.selectOne("datamodel.selectStndReqYn", dataModelId));
			// 104번 WP8-63 — 구조 진단 반영은 실 DB 컬럼명을 그대로 쓴다 (PostgreSQL 소문자 등). 화면 입력은 지금처럼 대문자로
			boolean keepNmCase = Boolean.TRUE.equals(body.get("keepNmCase"));

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rawList = (List<Map<String, Object>>) rawAttrs;
			// 87-x — DELETE → UPDATE → ADD 순으로 정렬해 처리. 같은 배치 안의 ADD 가 DELETE 대상 attr_ord 와 충돌하는 케이스 방지.
			List<Map<String, Object>> attrs = new ArrayList<>(rawList);
			attrs.sort((a, b) -> modeOrder(str(a.get("mode"))) - modeOrder(str(b.get("mode"))));

			// 86번 #11 — objOwner 미전달 시 부모 OBJ 에서 lookup. PK (DM_ID, OBJ_OWNER, OBJ_NM) 일관 매칭 위해 명시화.
			if (objOwner == null || objOwner.isEmpty()) {
				List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByDmId", dataModelId);
				for (StdDataModelObjVo o : objs) {
					if (objNm.equals(o.getObjNm())) {
						objOwner = o.getObjOwner() == null ? "" : o.getObjOwner();
						break;
					}
				}
				if (objOwner == null) objOwner = "";
			}

			// 현재 최대 ATTR_ORD 조회 (ADD 시 nextOrd 증분 기준)
			Map<String, Object> ordParam = new HashMap<>();
			ordParam.put("dataModelId", dataModelId);
			ordParam.put("objOwner",    objOwner);
			ordParam.put("objNm",       objNm);
			Short maxOrdObj = sqlSessionTemplate.selectOne("datamodel.selectMaxAttrOrd", ordParam);
			short nextOrd = (short) (maxOrdObj == null ? 0 : maxOrdObj);
			// 87-3 — TMP_COL_N 자동 명명 시 ATTR_NM 충돌 회피. attr_ord 와 별개로 기존 TMP_COL_N 의 최대 N 으로 시작.
			Integer maxTmpSuffixObj = sqlSessionTemplate.selectOne("datamodel.selectMaxTmpColSuffix", ordParam);
			int tmpColSuffix = (maxTmpSuffixObj == null ? 0 : maxTmpSuffixObj);

			// 86번 #11 — 스왑 대응: 이 batch 안에서 ATTR_ORD 가 바뀌는 attr 들의 (origAttrNm → newOrd) 맵
			// dup 체크 시 batch 안에서 다른 order 로 이동하는 attr 는 충돌에서 제외
			Map<String, Short> batchNewOrders = new HashMap<>();
			for (Object o : attrs) {
				if (!(o instanceof Map)) continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> r = (Map<String, Object>) o;
				String m = str(r.get("mode"));
				if (!"UPDATE".equalsIgnoreCase(m)) continue;
				String orig = str(r.get("origAttrNm"));
				if (orig == null || orig.trim().isEmpty()) orig = str(r.get("attrNm"));
				if (orig == null || orig.trim().isEmpty()) continue;
				Integer ord = parseIntSafe(r.get("attrOrder"));
				if (ord != null && ord > 0) batchNewOrders.put(orig.trim(), ord.shortValue());
			}

			int added = 0, updated = 0, deleted = 0;
			List<Map<String, Object>> errors = new ArrayList<>();

			for (int i = 0; i < attrs.size(); i++) {
				Map<String, Object> row = attrs.get(i);
				String mode = str(row.get("mode"));
				try {
					if ("ADD".equalsIgnoreCase(mode)) {
						String attrNmKr = str(row.get("attrNmKr"));
						String attrNmInput = str(row.get("attrNm"));
						if ((attrNmKr == null || attrNmKr.trim().isEmpty())
								&& (attrNmInput == null || attrNmInput.trim().isEmpty()))
							throw new IllegalArgumentException("컬럼 영문명 또는 한글명 중 하나는 필수");
						// 영문명은 항상 UPPER (Oracle 기준 — 다른 DBMS 차후 분기)
						// 87-3 — TMP_COL_N 자동 생성 시 같은 테이블 내 기존 TMP_COL_N 과 충돌 회피
						String addAttrNm;
						if (attrNmInput != null && !attrNmInput.trim().isEmpty()) {
							addAttrNm = keepNmCase ? attrNmInput.trim() : attrNmInput.trim().toUpperCase();
						} else {
							tmpColSuffix++;
							addAttrNm = "TMP_COL_" + tmpColSuffix;
						}
						// 사용자 입력 attrOrder 우선, 없으면 max+1
						Integer addOrdIn = parseIntSafe(row.get("attrOrder"));
						short useOrd;
						if (addOrdIn != null && addOrdIn > 0) {
							useOrd = addOrdIn.shortValue();
							Map<String, Object> ordDup = new HashMap<>();
							ordDup.put("dataModelId", dataModelId);
							ordDup.put("objOwner",    objOwner);
							ordDup.put("objNm",       objNm);
							ordDup.put("attrOrder",   (int) useOrd);
							ordDup.put("selfAttrNm",  addAttrNm);
							@SuppressWarnings("unchecked")
							List<String> conflicts = session.selectList("datamodel.selectAttrNmByOrder", ordDup);
							if (conflicts != null && !conflicts.isEmpty()) {
								List<String> realConflicts = new ArrayList<>();
								for (String c : conflicts) {
									Short movedTo = batchNewOrders.get(c);
									if (movedTo == null || movedTo.shortValue() == useOrd) realConflicts.add(c);
								}
								if (!realConflicts.isEmpty())
									throw new IllegalArgumentException("순서 " + useOrd + " 는 이미 '"
											+ String.join("', '", realConflicts) + "' 가 사용 중입니다 (" + objNm + ")");
							}
						} else {
							nextOrd++;
							useOrd = nextOrd;
						}
						StdDataModelAttrVo vo = new StdDataModelAttrVo();
						vo.setDataModelId(dataModelId);
						vo.setObjOwner(objOwner);  // 86번 #11 — 부모 OBJ 에서 상속한 OWNER 명시
						vo.setObjNm(objNm);
						vo.setAttrNm(addAttrNm);
						vo.setAttrNmKr(attrNmKr);
						vo.setAttrOrder(useOrd);
						String dataTypeIn = str(row.get("dataType"));
						vo.setDataType((dataTypeIn != null && !dataTypeIn.trim().isEmpty()) ? dataTypeIn.trim() : "VARCHAR");
						Integer dataLenIn = parseIntSafe(row.get("dataLen"));
						vo.setDataLen(dataLenIn != null ? dataLenIn.longValue() : 255L);
						Integer decLenIn = parseIntSafe(row.get("dataDecimalLen"));
						if (decLenIn != null) vo.setDataDecimalLen(decLenIn.shortValue());
						vo.setPkYn("Y".equalsIgnoreCase(str(row.get("pkYn"))) ? "Y" : "N");
						vo.setFkYn("Y".equalsIgnoreCase(str(row.get("fkYn"))) ? "Y" : "N");
						String nullableYn = str(row.get("nullableYn"));
						vo.setNullableYn("Y".equalsIgnoreCase(vo.getPkYn()) ? "N"
								: (nullableYn == null || nullableYn.isEmpty() ? "Y" : nullableYn));
						vo.setDefaultVal(str(row.get("defaultVal")));
						checkAttrInput(vo, null);   // 109번 #3
						// ADD 시 표준 재평가 — 영문명이 표준 용어와 매칭되고 도메인이 일치하면 Y/Y, 아니면 N/N
						String addExcept = null;
						try {
							validateAttrStandards(vo);
							applyStandardFlags(vo);
						} catch (Exception stdErr) {
							vo.setTermsStndYn("N");
							vo.setDomainStndYn("N");
							addExcept = stndReqCheck(stndReq, row, objNm, addAttrNm, stdErr);
						}
						// 88번 거버넌스 — 관리자=APPROVED 즉시, 사용자=DRAFT. 표준 예외 신청은 관리자도 결재(DRAFT)
						String addAprvStatus = addExcept != null ? "DRAFT" : changeHistory.resolveAprvStatusForUserChange();
						vo.setAprvStatus(addAprvStatus);
						vo.setRequesterUserId(changeHistory.safeUserId());
						vo.setReqDt(changeHistory.currentDt());
						if ("APPROVED".equals(addAprvStatus)) {
							vo.setAprvUserId(vo.getRequesterUserId());
							vo.setAprvDt(vo.getReqDt());
						}
						session.insert("datamodel.insertDataModelAttr", vo);
						added++;
						changeHistory.record(session, dataModelId, "ADD_ATTR", "TIER1",
								objOwner, objNm, addAttrNm, null, null,
								null, toJsonSafe(vo),
								changeHistory.generateDdlSnippet(dataModelId, "ADD_ATTR", objOwner, objNm, addAttrNm, vo, null),
								addAprvStatus, addExcept);
					} else if ("UPDATE".equalsIgnoreCase(mode)) {
						// origAttrNm = DB 의 PK 매칭용 — DB 에 저장된 case 그대로 유지 (UPPER 처리 X)
						String origAttrNm = str(row.get("origAttrNm"));
						if (origAttrNm == null || origAttrNm.trim().isEmpty())
							origAttrNm = str(row.get("attrNm"));
						if (origAttrNm == null || origAttrNm.trim().isEmpty())
							throw new IllegalArgumentException("origAttrNm 누락");
						origAttrNm = origAttrNm.trim();
						// newAttrNm 은 UPPER 강제 — 새로 저장될 값 (Oracle 기준, 다른 DBMS 차후 분기)
						String newAttrNm = str(row.get("attrNm"));
						if (newAttrNm == null || newAttrNm.trim().isEmpty())
							newAttrNm = origAttrNm;
						newAttrNm = keepNmCase ? newAttrNm.trim() : newAttrNm.trim().toUpperCase();

						Map<String, Object> sel = new HashMap<>();
						sel.put("dataModelId", dataModelId);
						sel.put("objOwner",    objOwner);
						sel.put("objNm",       objNm);
						sel.put("attrNm",      origAttrNm);
						StdDataModelAttrVo existing = session.selectOne("datamodel.selectDataModelAttrOne", sel);
						if (existing == null) throw new IllegalArgumentException("수정 대상 ATTR 없음: " + objNm + "." + origAttrNm);

						// 영문명 변경 시 cascade rename (INDEX, CONSTRAINT, FK 참조)
						boolean nmChanged = !Objects.equals(origAttrNm, newAttrNm);
						if (nmChanged) {
							// 새 ATTR_NM 이 같은 테이블 내 다른 행과 중복인지
							Map<String, Object> dupParam = new HashMap<>();
							dupParam.put("dataModelId", dataModelId);
							dupParam.put("objOwner",    objOwner);
							dupParam.put("objNm",       objNm);
							dupParam.put("attrNm",      newAttrNm);
							int dup = session.selectOne("datamodel.countDataModelAttr", dupParam);
							if (dup > 0) throw new IllegalArgumentException("이미 존재하는 영문명: " + newAttrNm);

							Map<String, Object> ren = new HashMap<>();
							ren.put("dataModelId", dataModelId);
							ren.put("objOwner",    objOwner);
							ren.put("objNm",       objNm);
							ren.put("origAttrNm",  origAttrNm);
							ren.put("newAttrNm",   newAttrNm);
							session.update("datamodel.renameAttrInIndex",         ren);
							session.update("datamodel.renameAttrInConstraint",    ren);
							session.update("datamodel.renameAttrInConstraintRef", ren);
							session.update("datamodel.renameAttrInFkParent",      ren);
						}

						// 물리 컬럼 (영문명/타입/길이/소수점/순서) UPDATE
						String dataTypeIn = str(row.get("dataType"));
						Integer dataLenIn = parseIntSafe(row.get("dataLen"));
						Integer decLenIn  = parseIntSafe(row.get("dataDecimalLen"));
						Integer attrOrdIn = parseIntSafe(row.get("attrOrder"));

						String  newType  = (dataTypeIn != null && !dataTypeIn.trim().isEmpty()) ? dataTypeIn.trim() : existing.getDataType();
						long    newLen   = dataLenIn != null ? dataLenIn.longValue() : existing.getDataLen();
						short   newDec   = decLenIn  != null ? decLenIn.shortValue() : existing.getDataDecimalLen();
						// 109번 #3 — 바뀐 이름·타입·길이·기본값 검사
						StdDataModelAttrVo chk = new StdDataModelAttrVo();
						chk.setAttrNm(newAttrNm);
						chk.setDataType(newType);
						chk.setDataLen(newLen);
						chk.setDataDecimalLen(newDec);
						chk.setDefaultVal(str(row.get("defaultVal")));
						checkAttrInput(chk, existing);

						short newOrd;
						if (attrOrdIn != null && attrOrdIn > 0) {
							newOrd = attrOrdIn.shortValue();
							// self 식별 — cascade rename 이미 호출됐어도, INDEX/CONSTRAINT 만 rename 하고
							// TB_DATA_MODEL_ATTR 본체는 updateAttrPhysical 에서 비로소 rename 됨.
							// 즉 이 시점에 자기 자신은 DB 에 origAttrNm 으로 존재 → origAttrNm 으로 self 제외.
							Map<String, Object> ordDup = new HashMap<>();
							ordDup.put("dataModelId", dataModelId);
							ordDup.put("objOwner",    objOwner);
							ordDup.put("objNm",       objNm);
							ordDup.put("attrOrder",   (int) newOrd);
							ordDup.put("selfAttrNm",  origAttrNm);
							@SuppressWarnings("unchecked")
							List<String> conflicts = session.selectList("datamodel.selectAttrNmByOrder", ordDup);
							if (conflicts != null && !conflicts.isEmpty()) {
								List<String> realConflicts = new ArrayList<>();
								for (String c : conflicts) {
									Short movedTo = batchNewOrders.get(c);
									if (movedTo == null || movedTo.shortValue() == newOrd) realConflicts.add(c);
								}
								if (!realConflicts.isEmpty())
									throw new IllegalArgumentException("순서 " + newOrd + " 는 이미 '"
											+ String.join("', '", realConflicts) + "' 가 사용 중입니다 (" + objNm + ")");
							}
						} else {
							newOrd = existing.getAttrOrder();
						}

						Map<String, Object> phys = new HashMap<>();
						phys.put("dataModelId", dataModelId);
						phys.put("objOwner",    objOwner);
						phys.put("objNm",       objNm);
						phys.put("origAttrNm",  origAttrNm);
						phys.put("newAttrNm",   newAttrNm);
						phys.put("dataType",    newType);
						phys.put("dataLen",     newLen);
						phys.put("dataDecimalLen", newDec);
						phys.put("attrOrder",   newOrd);
						session.update("datamodel.updateAttrPhysical", phys);

						// 논리/플래그 별도 (PK 는 이미 newAttrNm)
						StdDataModelAttrVo vo = new StdDataModelAttrVo();
						vo.setDataModelId(dataModelId);
						vo.setObjOwner(objOwner);
						vo.setObjNm(objNm);
						vo.setAttrNm(newAttrNm);
						String newKr = str(row.get("attrNmKr"));
						vo.setAttrNmKr(newKr);
						vo.setPkYn("Y".equalsIgnoreCase(str(row.get("pkYn"))) ? "Y" : "N");
						vo.setFkYn("Y".equalsIgnoreCase(str(row.get("fkYn"))) ? "Y" : "N");
						String nullableYn = str(row.get("nullableYn"));
						vo.setNullableYn("Y".equalsIgnoreCase(vo.getPkYn()) ? "N"
								: (nullableYn == null || nullableYn.isEmpty() ? "Y" : nullableYn));
						vo.setDefaultVal(str(row.get("defaultVal")));
						vo.setDataType(newType);
						vo.setDataLen(newLen);
						vo.setDataDecimalLen(newDec);
						// 변경된 값이 표준에 맞는지 다시 평가 (영문명 토큰 + 도메인 매칭)
						String updExcept = null;   // 104번 WP7-25 — 표준 예외 신청 사유
						boolean krChanged   = !Objects.equals(existing.getAttrNmKr(), newKr);
						boolean typeChanged = !Objects.equals(existing.getDataType(), newType)
						                   || existing.getDataLen() != newLen
						                   || existing.getDataDecimalLen() != newDec;
						if (!krChanged && !nmChanged && !typeChanged) {
							// 변경 없음 — 기존 플래그 유지
							vo.setWordLst(existing.getWordLst());
							vo.setWordStndLst(existing.getWordStndLst());
							vo.setTermsStndYn(existing.getTermsStndYn());
							vo.setDomainStndYn(existing.getDomainStndYn());
						} else {
							// 새 값으로 표준 검증 재실행
							StdDataModelAttrVo recheck = new StdDataModelAttrVo();
							recheck.setDataModelId(dataModelId);   // 98번 — 모델의 사전으로 재검증
							recheck.setAttrNm(newAttrNm);
							recheck.setAttrNmKr(newKr);
							recheck.setDataType(newType);
							recheck.setDataLen(newLen);
							recheck.setDataDecimalLen((short) newDec);
							try {
								validateAttrStandards(recheck);
								applyStandardFlags(recheck);
								vo.setWordLst(recheck.getWordLst());
								vo.setWordStndLst(recheck.getWordStndLst());
								vo.setTermsStndYn(recheck.getTermsStndYn() != null ? recheck.getTermsStndYn() : "Y");
								vo.setDomainStndYn(recheck.getDomainStndYn() != null ? recheck.getDomainStndYn() : "Y");
							} catch (Exception stdErr) {
								// 표준 미적합 — 강등
								vo.setWordLst(existing.getWordLst());
								vo.setWordStndLst(existing.getWordStndLst());
								vo.setTermsStndYn("N");
								vo.setDomainStndYn("N");
								updExcept = stndReqCheck(stndReq, row, objNm, newAttrNm, stdErr);
							}
						}
						session.update("datamodel.updateDataModelAttr", vo);
						updated++;
						// 104번 WP9-55 — 이력의 '변경 후' 가 바꾸지 않은 항목까지 빈 값으로 남아
						// 결재 화면 전후 비교에 "컬럼 순서 1 → 0" 같은 없는 변경이 보였다
						vo.setAttrOrder(newOrd);
						vo.setObjNmKr(existing.getObjNmKr());
						vo.setAttrComment(existing.getAttrComment());
						vo.setFkParentObjOwner(existing.getFkParentObjOwner());
						vo.setFkParentObjNm(existing.getFkParentObjNm());
						vo.setFkParentAttrNm(existing.getFkParentAttrNm());
						vo.setDbmsDataType(existing.getDbmsDataType());
						// 88번 거버넌스 — UPDATE 이력 (Tier 1: 영문명/타입/길이 변경 / Tier 2: 한글명만 변경)
						// 104번 WP7-25 — 표준 예외 신청은 1단계·결재(DRAFT)
						String updTier = (nmChanged || typeChanged || updExcept != null) ? "TIER1" : "TIER2";
						String updAprvStatus = updExcept != null ? "DRAFT"
								: "TIER1".equals(updTier)
								? changeHistory.resolveAprvStatusForUserChange()
								: "APPROVED";
						changeHistory.record(session, dataModelId, "MODIFY_ATTR", updTier,
								objOwner, objNm, newAttrNm, null, null,
								toJsonSafe(existing), toJsonSafe(vo),
								"TIER1".equals(updTier) ? changeHistory.generateModifyAttrSnippet(dataModelId, objOwner, objNm, existing, vo) : null,
								updAprvStatus, updExcept);
					} else if ("DELETE".equalsIgnoreCase(mode)) {
						String attrNm = str(row.get("attrNm"));
						if (attrNm == null || attrNm.trim().isEmpty())
							throw new IllegalArgumentException("attrNm 누락");
						attrNm = keepNmCase ? attrNm.trim() : attrNm.trim().toUpperCase();
						Map<String, Object> p = new HashMap<>();
						p.put("dataModelId", dataModelId);
						p.put("objOwner",    objOwner);  // 86번 #11
						p.put("objNm",       objNm);
						p.put("attrNm",      attrNm);
						String delBefore = toJsonSafe(session.selectOne("datamodel.selectDataModelAttrOne", p));   // 104번 WP9-55
						// DEF-05 — 승인 대기 삭제는 물리 삭제하지 않는다 (반려 시 복구 대상)
						String delAprvStatus = changeHistory.resolveAprvStatusForUserChange();
						if ("APPROVED".equals(delAprvStatus)) {
							cascadeDeleteAttrRefs(session, p);  // 87-3 — CONSTRAINT/INDEX/외부FK/FK_PARENT cascade
							session.delete("datamodel.deleteDataModelAttr", p);
						} else {
							p.put("deletedDt", changeHistory.currentDt());
							session.update("datamodel.softDeleteAttrPending", p);
						}
						deleted++;
						changeHistory.record(session, dataModelId, "DEL_ATTR", "TIER1",
								objOwner, objNm, attrNm, null, null,
								delBefore, null,
								changeHistory.generateDdlSnippet(dataModelId, "DEL_ATTR", objOwner, objNm, attrNm, null, null),
								delAprvStatus);
					} else {
						throw new IllegalArgumentException("알 수 없는 mode: " + mode);
					}
				} catch (Exception inner) {
					Map<String, Object> err = new HashMap<>();
					err.put("index", i);
					err.put("mode", mode);
					err.put("message", inner.getMessage());
					errors.add(err);
					throw inner; // 배치 전체 롤백 — 53번 §7-2 트랜잭션 1개 원칙
				}
			}

			// 컬럼 개수 동기화 — 86번 #11 OBJ_OWNER 매칭 추가
			Map<String, Object> syncParam = new HashMap<>();
			syncParam.put("dataModelId", dataModelId);
			syncParam.put("objOwner",    objOwner);
			syncParam.put("objNm",       objNm);
			session.update("datamodel.syncDataModelObjAttrCnt", syncParam);
			// 컬럼 순서 재배열 — 같은 (owner, objNm) 안의 빠진 자리 채워서 1..N 으로 재할당
			session.update("datamodel.compactAttrOrders", syncParam);
			session.commit();

			Map<String, Object> data = new HashMap<>();
			data.put("saved", added + updated + deleted);
			data.put("added", added);
			data.put("updated", updated);
			data.put("deleted", deleted);
			data.put("errors", errors);
			result.setResultInfo(RestResult.CODE_200);
			result.setContents(new ObjectMapper().writeValueAsString(data));
		} catch (Exception e) {
			session.rollback();
			log.error(">> saveAttrs failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}
		return Mono.just(result);
	}

	/**
	 * 104번 WP7-25 — 표준 준수 필수 모델에서 표준 검사가 실패한 컬럼. 행에 예외 사유(stndExceptReason)가 있으면 그 사유를 돌려주고
	 * (호출자가 결재 대상으로 저장), 없으면 거부한다. 필수 모델이 아니면 null (지금처럼 표준 여부만 N).
	 */
	private static String stndReqCheck(boolean stndReq, Map<String, Object> row, String objNm, String attrNm, Exception stdErr) {
		if (!stndReq) return null;
		String reason = str(row.get("stndExceptReason"));
		if (reason != null && !reason.trim().isEmpty()) return reason.trim();
		throw new IllegalStateException(STND_REQ_MSG + objNm + "." + attrNm + " — " + stdErr.getMessage());
	}

	/** 화면이 이 문구로 시작하는 오류를 받으면 표준 예외 신청을 띄운다 */
	private static final String STND_REQ_MSG = "표준 준수 필수 모델입니다. 비표준 컬럼: ";

	/**
	 * 109번 #3 — 컬럼 입력 검사 (이름·데이터 타입·길이·소수점·기본값은 [DB 반영] DDL 에 들어간다). 규칙은 DdlSafety.
	 * before 가 있으면(수정) 바뀐 항목만 본다 — 수집한 모델에 이미 있는 값 때문에 다른 항목 수정까지 막히지 않게.
	 * 그런 값은 DDL 을 만들 때 다시 검사해 거부한다.
	 */
	private static void checkAttrInput(StdDataModelAttrVo a, StdDataModelAttrVo before) {
		if (before == null || !Objects.equals(before.getAttrNm(), a.getAttrNm())) DdlSafety.ident("컬럼", a.getAttrNm());
		if (before == null || !Objects.equals(before.getDataType(), a.getDataType())) DdlSafety.dataType(a.getDataType());
		if (before == null || before.getDataLen() != a.getDataLen() || before.getDataDecimalLen() != a.getDataDecimalLen())
			DdlSafety.length(a.getDataLen(), a.getDataDecimalLen());
		if (before == null || !Objects.equals(blankToNull(before.getDefaultVal()), blankToNull(a.getDefaultVal())))
			DdlSafety.defaultValue(a.getDefaultVal(), null);
	}

	/**
	 * 109번 #3 — ERwin·XMI 가져오기의 테이블·컬럼 이름과 데이터 타입 검사. 걸린 항목이 있으면 앞 5건을 담은 메시지, 없으면 null.
	 */
	private static String importCheck(List<Map<String, Object>> tables, List<Map<String, Object>> columns) {
		List<String> bad = new ArrayList<>();
		for (Map<String, Object> t : tables) {
			try { DdlSafety.ident("테이블", str(t.get("objNm"))); }
			catch (DdlSafety.Invalid e) { bad.add(e.getMessage()); }
		}
		for (Map<String, Object> c : columns) {
			try {
				DdlSafety.ident("테이블", str(c.get("objNm")));
				DdlSafety.ident("컬럼", str(c.get("attrNm")));
				DdlSafety.dataType(str(c.get("dataType")));
			} catch (DdlSafety.Invalid e) {
				bad.add(str(c.get("objNm")) + "." + str(c.get("attrNm")) + ": " + e.getMessage());
			}
		}
		if (bad.isEmpty()) return null;
		return "가져올 수 없는 이름·타입이 " + bad.size() + "건 있습니다 — " + String.join(" / ", bad.subList(0, Math.min(5, bad.size())))
				+ (bad.size() > 5 ? " 외 " + (bad.size() - 5) + "건" : "");
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}

	/** 86번 #11 — 컬럼 직접 입력 시 dataLen 등 숫자 필드 안전 변환. 빈 문자열/잘못된 값은 null 로. */
	private static Integer parseIntSafe(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).intValue();
		String s = o.toString().trim();
		if (s.isEmpty()) return null;
		try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
	}

	// 87-x — saveAttrs 처리 순서: DELETE(0) → UPDATE(1) → ADD(2). attr_ord 충돌 회피.
	private static int modeOrder(String mode) {
		if (mode == null) return 99;
		String m = mode.trim().toUpperCase();
		if ("DELETE".equals(m)) return 0;
		if ("UPDATE".equals(m)) return 1;
		if ("ADD".equals(m))    return 2;
		return 99;
	}

	// 88번 — 이력 record 시 VO 직렬화 (최선 노력, 실패해도 변경 자체는 진행)
	private static String toJsonSafe(Object o) {
		if (o == null) return null;
		try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
		catch (Exception e) { return null; }
	}

	// ---------- 내부 헬퍼 ----------

	private String resolveLatestClctId(String dataModelId) {
		if (dataModelId == null) return null;
		return sqlSessionTemplate.selectOne("datamodel.selectLatestClctIdByDmId", dataModelId);
	}

	/**
	 * 한글명 → 용어/도메인 조회 공통 로직. /resolveStandard 와 /resolveAttrs 가 공유.
	 * 반환: {found, termsId, termsNm, termsEngAbrvNm, domainNm, domainId, dataType, dataLen, dataDecimalLen, domainMissing, message}
	 */
	private Map<String, Object> resolveTermsInternal(String termsNm, String dictId) {
		Map<String, Object> result = new HashMap<>();
		if (termsNm == null || termsNm.trim().isEmpty()) {
			result.put("found", false);
			result.put("message", "한글명이 비어있습니다.");
			return result;
		}
		try {
			// 104번 WP5-11 — 사전에 없으면 상위 사전 (가까운 사전 우선). 도메인은 용어를 찾은 사전에서
			com.ndata.quality.model.std.StdTermsVo terms = null;
			for (String d : dictService.lineage(dictId)) {
				terms = sqlSessionTemplate.selectOne("terms.selectApprovedTermsByNm",
					dictService.nameParam(d, "termsNm", termsNm.trim()));
				if (terms != null) {
					dictId = d;
					break;
				}
			}
			if (terms == null) {
				result.put("found", false);
				result.put("message", "'" + termsNm + "' 에 해당하는 표준 용어가 없습니다.");
				return result;
			}
			result.put("found", true);
			result.put("termsId", terms.getId());
			result.put("termsNm", terms.getTermsNm());
			result.put("termsEngAbrvNm", terms.getTermsEngAbrvNm());
			result.put("domainNm", terms.getDomainNm());

			if (terms.getDomainNm() != null) {
				// 98번 — 용어를 찾은 사전에서 도메인도 찾는다 (용어→도메인 FK 가 사전 단위다).
				com.ndata.quality.model.std.StdDomainVo domain =
					sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm", dictService.nameParam(dictId, "domainNm", terms.getDomainNm()));
				if (domain != null) {
					result.put("domainId", domain.getId());
					result.put("dataType", domain.getDataType());
					result.put("dataLen", domain.getDataLen());
					result.put("dataDecimalLen", domain.getDataDecimalLen());
				} else {
					result.put("domainMissing", true);
				}
			} else {
				result.put("domainMissing", true);
			}
		} catch (Exception e) {
			result.put("found", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	/**
	 * 단일 비표준 컬럼을 표준 컬럼으로 변환(rename + 타입/도메인/플래그 갱신).
	 * 실패 시 IllegalStateException 을 던짐 — 호출자는 catch 하여 failedList 로 수집.
	 */
	private void applyResolvedToAttr(StdDataModelAttrVo attr) {
		String attrNmKr = attr.getAttrNmKr();
		if (attrNmKr == null || attrNmKr.trim().isEmpty())
			throw new IllegalStateException("한글명 없음");

		// 98번 — 이 컬럼이 속한 모델의 사전으로 용어를 찾는다.
		Map<String, Object> resolved = resolveTermsInternal(attrNmKr,
				dictService.resolve(null, attr.getDataModelId()));
		Boolean found = (Boolean) resolved.get("found");
		if (found == null || !found)
			throw new IllegalStateException((String) resolved.getOrDefault("message", "용어 미등록"));
		if (Boolean.TRUE.equals(resolved.get("domainMissing")))
			throw new IllegalStateException("도메인 미등록");

		String newAttrNm = (String) resolved.get("termsEngAbrvNm");
		if (newAttrNm == null || newAttrNm.trim().isEmpty())
			throw new IllegalStateException("용어 영문약어명 누락");
		// 109번 #3 — 컬럼 이름·타입은 DDL 에 들어간다
		DdlSafety.ident("컬럼", newAttrNm);
		DdlSafety.dataType((String) resolved.get("dataType"));

		if (!newAttrNm.equals(attr.getAttrNm())) {
			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", attr.getDataModelId());
			dupParam.put("objOwner", attr.getObjOwner() == null ? "" : attr.getObjOwner());
			dupParam.put("objNm", attr.getObjNm());
			dupParam.put("attrNm", newAttrNm);
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelAttr", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 컬럼: " + newAttrNm);
		}

		StdDataModelAttrVo flagsVo = new StdDataModelAttrVo();
		flagsVo.setDataModelId(attr.getDataModelId());   // 98번 — 모델의 사전으로 검증
		flagsVo.setAttrNm(newAttrNm);
		flagsVo.setDataType((String) resolved.get("dataType"));
		validateAttrStandards(flagsVo);
		applyStandardFlags(flagsVo);

		long dataLen = 0L;
		Object dl = resolved.get("dataLen");
		if (dl instanceof Number) dataLen = ((Number) dl).longValue();
		short dataDecimalLen = 0;
		Object ddl = resolved.get("dataDecimalLen");
		if (ddl instanceof Number) dataDecimalLen = ((Number) ddl).shortValue();

		Map<String, Object> updateMap = new HashMap<>();
		updateMap.put("dataModelId", attr.getDataModelId());
		updateMap.put("objOwner", attr.getObjOwner() == null ? "" : attr.getObjOwner());
		updateMap.put("objNm", attr.getObjNm());
		updateMap.put("origAttrNm", attr.getAttrNm());
		updateMap.put("attrNm", newAttrNm);
		// 104번 WP7-8 — 유사어(이음동의어)로 맞았으면 한글명도 표준 용어명으로
		updateMap.put("attrNmKr", resolved.get("termsNm") != null ? resolved.get("termsNm") : attrNmKr);
		updateMap.put("dataType", resolved.get("dataType"));
		updateMap.put("dataLen", dataLen);
		updateMap.put("dataDecimalLen", dataDecimalLen);
		updateMap.put("termsStndYn", "Y");
		updateMap.put("domainStndYn", "Y");
		updateMap.put("wordLst", flagsVo.getWordLst());
		updateMap.put("wordStndLst", flagsVo.getWordStndLst());
		int updated = sqlSessionTemplate.update("datamodel.updateDataModelAttrKey", updateMap);
		if (updated == 0) {
			throw new IllegalStateException("UPDATE 매칭 실패 (owner='" + updateMap.get("objOwner")
				+ "', obj='" + attr.getObjNm() + "', attr='" + attr.getAttrNm() + "')");
		}
		// DEF-11 — 영문명이 바뀌면 그 이름을 참조하는 인덱스·제약조건·FK 도 따라가야 한다.
		// saveAttrs 는 이 cascade 를 호출하는데 이 경로만 빠져 있어,
		// 한글명 기준 표준화로 컬럼명을 바꾸면 참조가 옛 이름에 남았다.
		if (!newAttrNm.equals(attr.getAttrNm())) {
			Map<String, Object> ren = new HashMap<>();
			ren.put("dataModelId", attr.getDataModelId());
			ren.put("objOwner",    updateMap.get("objOwner"));
			ren.put("objNm",       attr.getObjNm());
			ren.put("origAttrNm",  attr.getAttrNm());
			ren.put("newAttrNm",   newAttrNm);
			sqlSessionTemplate.update("datamodel.renameAttrInIndex",         ren);
			sqlSessionTemplate.update("datamodel.renameAttrInConstraint",    ren);
			sqlSessionTemplate.update("datamodel.renameAttrInConstraintRef", ren);
			sqlSessionTemplate.update("datamodel.renameAttrInFkParent",      ren);

			// 거버넌스 이력도 남긴다. 그동안 이 경로는 컬럼 물리명을 바꾸면서도
			// 변경 이력에 나타나지 않아 승인 대상에서 통째로 빠졌다.
			Map<String, Object> beforeAttr = new HashMap<>();
			beforeAttr.put("objOwner", updateMap.get("objOwner"));
			beforeAttr.put("objNm",    attr.getObjNm());
			beforeAttr.put("attrNm",   attr.getAttrNm());
			beforeAttr.put("attrNmKr", attr.getAttrNmKr());
			changeHistory.record(null, attr.getDataModelId(), "MODIFY_ATTR", "TIER1",
					(String) updateMap.get("objOwner"), attr.getObjNm(), newAttrNm, null, null,
					toJsonSafe(beforeAttr), toJsonSafe(updateMap), null,
					changeHistory.resolveAprvStatusForUserChange());
		}
		// 88번 §16 — 변환 이력 기록 (사용자가 입력했던 한글 → 표준 매핑)
		recordResolveHistory(attr.getDataModelId(), attr.getObjOwner(), attr.getObjNm(),
				newAttrNm, attrNmKr, (String) updateMap.get("attrNmKr"), newAttrNm,
				(String) resolved.get("termsId"), (String) resolved.get("dataType"), dataLen,
				"한글명 기준 표준화 성공");
	}

	/** 88번 §16 — 변환 이력 기록 헬퍼 */
	private void recordResolveHistory(String dmId, String objOwner, String objNm, String attrNm,
	                                   String inputKr, String resolvedKr, String resolvedEn,
	                                   String termsId, String dataType, long dataLen, String reason) {
		try {
			Map<String, Object> r = new HashMap<>();
			r.put("dmId", dmId);
			r.put("objOwner", objOwner == null ? "" : objOwner);
			r.put("objNm", objNm);
			r.put("attrNm", attrNm);
			r.put("inputKrNm", inputKr);
			r.put("resolvedKrNm", resolvedKr);
			r.put("resolvedEnNm", resolvedEn);
			r.put("resolvedTermsId", termsId);
			r.put("resolvedDataType", dataType);
			r.put("resolvedDataLen", dataLen);
			r.put("resolveReason", reason);
			r.put("changeUserId", changeHistory.safeUserId());
			r.put("changeDt", changeHistory.currentDt());
			sqlSessionTemplate.insert("termResolve.insert", r);
		} catch (Exception e) {
			log.warn(">> recordResolveHistory failed: {}", e.getMessage());
		}
	}

	/**
	 * 영문명(ATTR_NM) 기준으로 표준 용어 조회해 한글명/데이터타입/길이/소수점 채움.
	 * 영문명은 그대로 유지.
	 */
	private void applyResolvedToAttrByEng(StdDataModelAttrVo attr) {
		String engNm = attr.getAttrNm();
		if (engNm == null || engNm.trim().isEmpty())
			throw new IllegalStateException("영문명 없음");

		// 98번 — 이 컬럼이 속한 모델의 사전에서 찾는다.
		com.ndata.quality.model.std.StdTermsVo terms = approvedTermByEng(dictService.resolve(null, attr.getDataModelId()), engNm.trim());
		if (terms == null)
			throw new IllegalStateException("'" + engNm + "' 에 해당하는 표준 용어가 없습니다.");

		String newAttrNmKr = terms.getTermsNm();
		String dataType    = terms.getDataType();
		long dataLen       = terms.getDataLen();
		short dataDecimalLen = (short) terms.getDataDecimalLen();

		// 영문명은 유지하면서 표준 검증 통과 여부 평가 (단어 분리)
		StdDataModelAttrVo flagsVo = new StdDataModelAttrVo();
		flagsVo.setDataModelId(attr.getDataModelId());   // 98번 — 모델의 사전으로 검증
		flagsVo.setAttrNm(engNm);
		flagsVo.setDataType(dataType);
		validateAttrStandards(flagsVo);
		applyStandardFlags(flagsVo);

		Map<String, Object> updateMap = new HashMap<>();
		updateMap.put("dataModelId", attr.getDataModelId());
		updateMap.put("objOwner", attr.getObjOwner() == null ? "" : attr.getObjOwner());
		updateMap.put("objNm", attr.getObjNm());
		updateMap.put("origAttrNm", engNm);
		updateMap.put("attrNm", engNm);   // 영문명 유지
		updateMap.put("attrNmKr", newAttrNmKr);
		updateMap.put("dataType", dataType);
		updateMap.put("dataLen", dataLen);
		updateMap.put("dataDecimalLen", dataDecimalLen);
		updateMap.put("termsStndYn", "Y");
		updateMap.put("domainStndYn", "Y");
		updateMap.put("wordLst", flagsVo.getWordLst());
		updateMap.put("wordStndLst", flagsVo.getWordStndLst());
		int updated = sqlSessionTemplate.update("datamodel.updateDataModelAttrKey", updateMap);
		if (updated == 0) {
			throw new IllegalStateException("UPDATE 매칭 실패 (owner='" + updateMap.get("objOwner")
				+ "', obj='" + attr.getObjNm() + "', attr='" + engNm + "')");
		}
		// 88번 §16 — 변환 이력 기록 (영문명 기준 표준화 — 한글이 채워진 케이스)
		String origKr = attr.getAttrNmKr() == null ? "" : attr.getAttrNmKr();
		recordResolveHistory(attr.getDataModelId(), attr.getObjOwner(), attr.getObjNm(),
				engNm, origKr, newAttrNmKr, engNm,
				terms.getId(), dataType, dataLen,
				"영문명 기준 표준화 성공");
	}

	/**
	 * 컬럼 표준 검증 — 영문명 토큰은 모두 표준 단어에 존재, 영문명 = 표준 용어, 한글명·도메인(타입/길이) 일치해야 함.
	 * 진단(DiagService) 로직과 동일한 기준. 저장 시 호출돼 미준수 발견되면 throw → 호출측에서 catch 후 termsStndYn/domainStndYn='N' 으로 강등.
	 * (테이블·인덱스·제약조건은 표준 검증 대상 아님 — 컬럼만 강제)
	 */
	private void validateAttrStandards(StdDataModelAttrVo attrVo) {
		String attrNm = attrVo.getAttrNm();
		if (attrNm == null || attrNm.trim().isEmpty())
			throw new IllegalArgumentException("컬럼 물리명(attrNm)은 필수입니다.");
		// 98번 — 단어·용어 모두 이 컬럼이 속한 모델의 사전에서 찾는다.
		String dictId = dictService.resolve(null, attrVo.getDataModelId());
		List<String> tokens = splitTokens(attrNm);
		List<String> missing = findMissingWords(tokens, dictId);
		if (!missing.isEmpty()) {
			throw new IllegalStateException("표준 미준수: 컬럼명에 표준 단어가 아닌 토큰이 있습니다 → " + String.join(", ", missing));
		}
		if (attrVo.getDataType() == null || attrVo.getDataType().trim().isEmpty()) {
			throw new IllegalArgumentException("데이터 타입은 필수입니다.");
		}

		// 영문명 기준 표준 용어 매칭 (TB_TERMS.TERMS_ENG_ABRV_NM = attrNm)
		// 104번 WP5-11 — 사전에 없으면 상위 사전 (가까운 사전 우선)
		com.ndata.quality.model.std.StdTermsVo term = null;
		for (String d : dictService.lineage(dictId)) {
			term = sqlSessionTemplate.selectOne("terms.selectApprovedTermsByEngNm",
				dictService.nameParam(d, "termsEngAbrvNm", attrNm.trim()));
			if (term != null) break;
		}
		if (term == null) {
			throw new IllegalStateException("표준 미준수: '" + attrNm + "' 에 해당하는 표준 용어가 등록되어있지 않습니다.");
		}

		// 한글명 일치 검증 — 표준 용어에 한글명이 있고, 입력 attrNmKr 가 있을 때만 비교
		String stdKr = term.getTermsNm();
		String inputKr = attrVo.getAttrNmKr();
		if (stdKr != null && !stdKr.trim().isEmpty()
				&& inputKr != null && !inputKr.trim().isEmpty()
				&& !stdKr.trim().equals(inputKr.trim())) {
			throw new IllegalStateException("표준 미준수: 한글명이 표준 용어와 다릅니다 (표준: " + stdKr + ", 입력: " + inputKr + ")");
		}

		// 도메인 타입/길이 일치 검증 (term 에 도메인이 지정된 경우만)
		if (term.getDomainNm() != null && !term.getDomainNm().trim().isEmpty()) {
			String stdType = term.getDataType();
			long   stdLen  = term.getDataLen();
			if (stdType != null && !isTypeEquivalent(stdType, attrVo.getDataType())) {
				throw new IllegalStateException("표준 미준수: 데이터 타입이 표준 도메인과 다릅니다 (표준: " + stdType + ", 입력: " + attrVo.getDataType() + ")");
			}
			if (stdLen > 0 && attrVo.getDataLen() != stdLen) {
				throw new IllegalStateException("표준 미준수: 데이터 길이가 표준 도메인과 다릅니다 (표준: " + stdLen + ", 입력: " + attrVo.getDataLen() + ")");
			}
		}
	}

	/** DBMS별 타입 동의어 비교 (DiagService.isTypeEquivalent 와 동일). */
	private boolean isTypeEquivalent(String stdType, String actualType) {
		if (stdType == null || actualType == null) return false;
		if (stdType.equalsIgnoreCase(actualType)) return true;
		String s = stdType.toUpperCase();
		String a = actualType.toUpperCase();
		if ((s.equals("DATE") && a.equals("DATETIME")) || (s.equals("DATETIME") && a.equals("DATE"))) return true;
		if (isStringFamily(s) && isStringFamily(a)) return true;
		if (isNumericFamily(s) && isNumericFamily(a)) return true;
		return false;
	}

	private boolean isStringFamily(String type) {
		return type.equals("CHAR") || type.equals("VARCHAR") || type.equals("VARCHAR2");
	}

	private boolean isNumericFamily(String type) {
		return type.equals("NUMBER") || type.equals("NUMERIC") || type.equals("DECIMAL");
	}

	private List<String> splitTokens(String physicalName) {
		List<String> tokens = new ArrayList<>();
		if (physicalName == null) return tokens;
		for (String t : physicalName.toUpperCase().split("_")) {
			String s = t.trim();
			if (!s.isEmpty()) tokens.add(s);
		}
		return tokens;
	}

	private List<String> findMissingWords(List<String> tokens, String dictId) {
		List<String> missing = new ArrayList<>();
		if (tokens.isEmpty()) return missing;
		java.util.Set<String> foundSet = new java.util.HashSet<>();
		// 104번 WP5-11 — 사전과 상위 사전들의 단어
		for (String d : dictService.lineage(dictId)) {
			List<StdWordVo> found = sqlSessionTemplate.selectList("word.selectWordsByEngAbrvNms", dictService.listParam(d, "engAbrvNms", tokens));
			for (StdWordVo w : found) {
				if (w.getWordEngAbrvNm() != null) foundSet.add(w.getWordEngAbrvNm().toUpperCase());
			}
		}
		for (String t : tokens) {
			if (!foundSet.contains(t)) missing.add(t);
		}
		return missing;
	}

	/**
	 * 검증 통과한 편집 값 기준으로 표준 플래그를 Y로 세팅
	 */
	private void applyStandardFlags(StdDataModelAttrVo attrVo) {
		attrVo.setTermsStndYn("Y");
		attrVo.setDomainStndYn("Y");
		List<String> tokens = splitTokens(attrVo.getAttrNm());
		String[] wordLst = new String[tokens.size()];
		String[] wordStndLst = new String[tokens.size()];
		for (int i = 0; i < tokens.size(); i++) {
			wordLst[i] = tokens.get(i);
			wordStndLst[i] = "Y";
		}
		attrVo.setWordLst(wordLst);
		attrVo.setWordStndLst(wordStndLst);
	}

	// ===================================================================
	// 53번 Phase 5: 엑셀 업로드 (테이블·컬럼)
	// ===================================================================

	// 테이블 화면 헤더 순서와 통일 (소유자/업무영역/한글명/영문명/테이블스페이스/설명).
	// 양식 다운로드(uploadTemplate)·파싱(parseTableWorkbook) 둘 다 이 배열을 사용.
	private static final String[] TABLE_HEADERS = { "소유자", "업무영역", "테이블명(한글)", "테이블명(영문)", "테이블스페이스", "설명" };
	// 86번 #11 — 업로드/다운로드 양식 통일. 다운로드한 파일 그대로 백업·재업로드 가능.
	private static final String[] ATTR_HEADERS = {
		"소유자", "테이블명(영문)", "테이블명(한글)", "컬럼명(영문)", "컬럼명(한글)",
		"데이터타입", "길이", "소수점자리", "컬럼 순서",
		"NULL여부", "PK여부", "FK여부", "디폴트값",
		"참조 테이블(한글)", "참조 컬럼(한글)", "삭제 규칙"
	};

	/**
	 * 테이블 목록 엑셀 업로드 — preview/commit 2단계
	 */
	@RequestMapping(value = "/uploadTables", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<Response> uploadTables(@RequestParam("file") MultipartFile file,
	                                    @RequestParam("dataModelId") String dataModelId,
	                                    @RequestParam(value = "mode", defaultValue = "preview") String mode) {
		Response result = new Response();
		try {
			if (dataModelId == null || dataModelId.trim().isEmpty())
				throw new IllegalArgumentException("dataModelId 누락");
			if (file == null || file.isEmpty())
				throw new IllegalArgumentException("파일이 비어있습니다.");
			String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
			if (!fn.endsWith(".xlsx"))
				throw new IllegalArgumentException("xlsx 파일만 허용됩니다.");

			Map<String, Object> parsed = parseTableWorkbook(file, dataModelId);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("rows");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> errors = (List<Map<String, Object>>) parsed.get("errors");

			int toInsert = 0, skipped = 0;
			for (Map<String, Object> r : rows) {
				if ("SKIP".equals(r.get("_action"))) skipped++;
				else if ("INSERT".equals(r.get("_action"))) toInsert++;
			}

			if ("commit".equalsIgnoreCase(mode) && errors.isEmpty()) {
				SqlSession session = sqlSessionFactory.openSession();
				try {
					Integer cnt = sqlSessionTemplate.selectOne("datamodel.countDataModelObjByDm", dataModelId);
					int seq = cnt == null ? 0 : cnt;
					for (Map<String, Object> r : rows) {
						if (!"INSERT".equals(r.get("_action"))) continue;
						StdDataModelObjVo vo = new StdDataModelObjVo();
						vo.setDataModelId(dataModelId);
						vo.setObjOwner(str(r.get("objOwner")));
						vo.setObjNmKr(str(r.get("objNmKr")));
						vo.setObjDesc(str(r.get("objDesc")));
						vo.setTablespaceNm(str(r.get("tablespaceNm")));
						vo.setBizAreaId(str(r.get("bizAreaId")));
						// 86번 #9 — 영문명 입력했으면 그대로, 비어있으면 TMP_TBL_N 자동
						String enNm = str(r.get("objNm"));
						if (isBlank(enNm)) {
							seq++;
							enNm = "TMP_TBL_" + seq;
						}
						vo.setObjNm(enNm);
						vo.setObjAttrCnt((short) 0);
						session.insert("datamodel.insertDataModelObj", vo);
						@SuppressWarnings("unchecked")
						Map<String, String> udpVals = (Map<String, String>) r.get("udpVals");
						udpService.save(session, dataModelId, vo.getObjOwner(), enNm, udpVals, changeHistory.safeUserId());   // 104번 WP8-31
						r.put("objNm", enNm);
					}
					session.commit();
				} catch (Exception e) {
					session.rollback();
					throw e;
				} finally {
					session.close();
				}
			}

			Map<String, Object> summary = new HashMap<>();
			summary.put("total", rows.size());
			summary.put("toInsert", toInsert);
			summary.put("skipped", skipped);
			summary.put("mode", mode);

			Map<String, Object> resp = new HashMap<>();
			resp.put("tables", rows);
			resp.put("errors", errors);
			resp.put("warnings", parsed.get("warnings"));
			resp.put("summary", summary);
			result.setResultInfo(RestResult.CODE_200);
			result.setContents(new ObjectMapper().writeValueAsString(resp));
		} catch (Exception e) {
			log.error(">> uploadTables failed : {}", e.getMessage(), e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 컬럼 목록 엑셀 업로드 — preview/commit 2단계 + FK 2-pass
	 */
	@RequestMapping(value = "/uploadAttrs", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<Response> uploadAttrs(@RequestParam("file") MultipartFile file,
	                                   @RequestParam("dataModelId") String dataModelId,
	                                   @RequestParam(value = "mode", defaultValue = "preview") String mode) {
		Response result = new Response();
		try {
			if (dataModelId == null || dataModelId.trim().isEmpty())
				throw new IllegalArgumentException("dataModelId 누락");
			if (file == null || file.isEmpty())
				throw new IllegalArgumentException("파일이 비어있습니다.");
			String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
			if (!fn.endsWith(".xlsx"))
				throw new IllegalArgumentException("xlsx 파일만 허용됩니다.");

			Map<String, Object> parsed = parseAttrWorkbook(file, dataModelId);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("rows");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> errors = (List<Map<String, Object>>) parsed.get("errors");

			int toInsertAttrs = 0, skipped = 0, toInsertFks = 0;
			for (Map<String, Object> r : rows) {
				if ("SKIP".equals(r.get("_action"))) skipped++;
				else if ("INSERT".equals(r.get("_action"))) {
					toInsertAttrs++;
					if ("Y".equals(r.get("fkYn"))) toInsertFks++;
				}
			}
			// 104번 WP7-25 — 표준 준수 필수 모델은 비표준 행을 오류로 (오류가 있으면 등록하지 않는다)
			if ("Y".equals(sqlSessionTemplate.selectOne("datamodel.selectStndReqYn", dataModelId))) {
				for (Map<String, Object> r : rows) {
					if (!"INSERT".equals(r.get("_action"))) continue;
					try {
						validateAttrStandards(uploadAttrVo(dataModelId, r, (short) 0));
					} catch (Exception stdErr) {
						Object rowNo = r.get("row");
						errors.add(errRow(rowNo instanceof Number ? ((Number) rowNo).intValue() : 0,
								STND_REQ_MSG + str(r.get("objNm")) + "." + str(r.get("attrNm")) + " — " + stdErr.getMessage()));
					}
				}
			}

			if ("commit".equalsIgnoreCase(mode) && errors.isEmpty()) {
				SqlSession session = sqlSessionFactory.openSession();
				try {
					// Pass 1: ATTR insert
					// 대상 테이블별로 현재 최대 ATTR_ORD 조회 후 증분
					Map<String, Short> ordMap = new HashMap<>();
					for (Map<String, Object> r : rows) {
						if (!"INSERT".equals(r.get("_action"))) continue;
						String objNm = str(r.get("objNm"));
						Short cur = ordMap.get(objNm);
						if (cur == null) {
							Map<String, Object> p = new HashMap<>();
							p.put("dataModelId", dataModelId);
							p.put("objNm", objNm);
							Short maxOrd = sqlSessionTemplate.selectOne("datamodel.selectMaxAttrOrd", p);
							cur = maxOrd == null ? (short) 0 : maxOrd;
						}
						cur = (short) (cur + 1);
						ordMap.put(objNm, cur);

						StdDataModelAttrVo vo = uploadAttrVo(dataModelId, r, cur);
						// 104번 WP7-25 — 화면 저장처럼 표준 여부를 평가해 둔다
						try {
							validateAttrStandards(vo);
							applyStandardFlags(vo);
						} catch (Exception stdErr) {
							vo.setTermsStndYn("N");
							vo.setDomainStndYn("N");
						}
						session.insert("datamodel.insertDataModelAttr", vo);
						r.put("attrNm", vo.getAttrNm());
					}
					// Pass 2: FK 제약 — 한글 참조를 물리명으로 매핑. 현 버전은 FK_YN 플래그만 저장하고
					// CONSTRAINT 테이블 INSERT 는 추후(§6-6 후속)로 연기. REF 매핑 검증만 수행해 오류 시 롤백.
					List<Map<String, Object>> fkRefs = new ArrayList<>();
					for (Map<String, Object> r : rows) {
						if (!"INSERT".equals(r.get("_action"))) continue;
						if (!"Y".equals(r.get("fkYn"))) continue;
						String refTbl = resolveObjNmByKr(session, dataModelId, str(r.get("refObjOwner")), str(r.get("refObjNmKr")));
						String refCol = null;
						if (refTbl != null) {
							refCol = resolveAttrNmByKr(session, dataModelId, refTbl, str(r.get("refAttrNmKr")));
						}
						Map<String, Object> ref = new HashMap<>();
						ref.put("row", r.get("row"));
						ref.put("objNm", r.get("objNm"));
						ref.put("attrNm", r.get("attrNm"));
						ref.put("refTableNm", refTbl);
						ref.put("refColumnNm", refCol);
						ref.put("deleteRule", r.get("deleteRule"));
						fkRefs.add(ref);
					}
					// 영향 받은 (owner, objNm) DISTINCT 로 OBJ_ATTR_CNT 동기화 — 누락 시 테이블 화면에 0개로 표시되던 버그
					Set<String> syncedObjs = new HashSet<>();
					for (Map<String, Object> r : rows) {
						if (!"INSERT".equals(r.get("_action"))) continue;
						String owner = str(r.get("objOwner"));
						String obj   = str(r.get("objNm"));
						String key = (owner == null ? "" : owner) + "|" + obj;
						if (syncedObjs.contains(key)) continue;
						syncedObjs.add(key);
						Map<String, Object> syncParam = new HashMap<>();
						syncParam.put("dataModelId", dataModelId);
						syncParam.put("objOwner",    owner == null ? "" : owner);
						syncParam.put("objNm",       obj);
						session.update("datamodel.syncDataModelObjAttrCnt", syncParam);
					}
					session.commit();
					parsed.put("fkRefs", fkRefs);
				} catch (Exception e) {
					session.rollback();
					throw e;
				} finally {
					session.close();
				}
			}

			Map<String, Object> summary = new HashMap<>();
			summary.put("total", rows.size());
			summary.put("toInsertAttrs", toInsertAttrs);
			summary.put("toInsertFks", toInsertFks);
			summary.put("skipped", skipped);
			summary.put("groups", parsed.get("groups"));
			summary.put("mode", mode);

			Map<String, Object> resp = new HashMap<>();
			resp.put("attrs", rows);
			resp.put("errors", errors);
			resp.put("warnings", parsed.get("warnings"));
			resp.put("fkRefs", parsed.getOrDefault("fkRefs", new ArrayList<>()));
			resp.put("summary", summary);
			result.setResultInfo(RestResult.CODE_200);
			result.setContents(new ObjectMapper().writeValueAsString(resp));
		} catch (Exception e) {
			log.error(">> uploadAttrs failed : {}", e.getMessage(), e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 업로드 양식 xlsx 다운로드 (resources/templates/ 의 고정 파일 서빙)
	 * scope=tables|attrs
	 */
	@RequestMapping(value = "/uploadTemplate", method = RequestMethod.GET)
	public void uploadTemplate(@RequestParam(value = "scope", defaultValue = "tables") String scope,
	                            HttpServletResponse res) throws Exception {
		// 86번 #9 — 양식 동적 생성 (TABLE_HEADERS / ATTR_HEADERS 변경 시 자동 반영)
		boolean isAttrs = "attrs".equalsIgnoreCase(scope);
		String[] headers = isAttrs ? ATTR_HEADERS : TABLE_HEADERS;
		if (!isAttrs) {
			// 104번 WP8-31 — 테이블 양식 끝에 사용 중인 사용자 정의 속성 열
			List<String> hs = new ArrayList<>(java.util.Arrays.asList(TABLE_HEADERS));
			for (Map<String, Object> d : udpService.activeDefs()) hs.add((String) d.get("udpNm"));
			headers = hs.toArray(new String[0]);
		}
		String fileName = isAttrs ? "dataq_attrs_template.xlsx" : "dataq_tables_template.xlsx";

		res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		res.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
		try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
			org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet(isAttrs ? "컬럼 양식" : "테이블 양식");
			// 헤더 스타일
			org.apache.poi.ss.usermodel.CellStyle hdrStyle = wb.createCellStyle();
			org.apache.poi.ss.usermodel.Font hdrFont = wb.createFont();
			hdrFont.setBold(true);
			hdrStyle.setFont(hdrFont);
			hdrStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
			hdrStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
			hdrStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

			org.apache.poi.ss.usermodel.Row hRow = sh.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				org.apache.poi.ss.usermodel.Cell c = hRow.createCell(i);
				c.setCellValue(headers[i]);
				c.setCellStyle(hdrStyle);
				sh.setColumnWidth(i, 18 * 256);
			}
			wb.write(res.getOutputStream());
			res.getOutputStream().flush();
		}
	}

	// ---------- 엑셀 파싱 헬퍼 ----------

	private Map<String, Object> parseTableWorkbook(MultipartFile file, String dataModelId) throws Exception {
		List<Map<String, Object>> rows = new ArrayList<>();
		List<Map<String, Object>> errors = new ArrayList<>();
		List<Map<String, Object>> warnings = new ArrayList<>();

		try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
			if (wb.getNumberOfSheets() > 1) {
				warnings.add(warnRow(0, "2번째 이후 시트는 무시됨"));
			}
			Sheet sh = wb.getSheetAt(0);
			Row header = sh.getRow(0);
			if (header == null) throw new IllegalArgumentException("시트가 비어있습니다.");
			Map<String, Integer> hIdx = mapHeaders(header, TABLE_HEADERS);
			// 104번 WP8-31 — 사용 중인 사용자 정의 속성 이름과 같은 헤더는 그 속성 값
			List<Map<String, Object>> udpDefs = udpService.activeDefs();
			String[] udpNms = new String[udpDefs.size()];
			for (int i = 0; i < udpDefs.size(); i++) udpNms[i] = (String) udpDefs.get(i).get("udpNm");
			Map<String, Integer> udpIdx = mapHeaders(header, udpNms);
			for (String h : TABLE_HEADERS) {
				// 소유자·테이블명(한글) 만 필수. 영문명/설명/업무영역/테이블스페이스는 옵션
				if (h.equals("설명") || h.equals("테이블명(영문)")
						|| h.equals("업무영역") || h.equals("테이블스페이스")) continue;
				if (!hIdx.containsKey(h))
					throw new IllegalArgumentException("필수 헤더 누락: " + h);
			}

			// 업무영역명 → biz_area_id 매핑 테이블 (엑셀엔 업무영역 '명' 을 적음)
			Map<String, String> bizAreaNmToId = new HashMap<>();
			List<Map<String, Object>> bizAreas = sqlSessionTemplate.selectList("area.selectBizAreas");
			for (Map<String, Object> ba : bizAreas) {
				String nm = str(ba.get("bizAreaNm"));
				if (nm != null && !nm.isEmpty()) bizAreaNmToId.put(nm.trim(), str(ba.get("bizAreaId")));
			}

			Set<String> seenKr = new HashSet<>();
			Set<String> seenEn = new HashSet<>();
			// DB 에 이미 존재하는 (owner, objNmKr) / (owner, objNm) 조합 조회
			Set<String> existingKr = loadExistingObjKrs(dataModelId);
			Set<String> existingEn = loadExistingObjEns(dataModelId);

			int last = sh.getLastRowNum();
			for (int r = 1; r <= last; r++) {
				Row row = sh.getRow(r);
				if (row == null || isRowEmpty(row)) continue;
				String owner = getStr(row, hIdx.get("소유자"));
				String enNm = hIdx.containsKey("테이블명(영문)") ? getStr(row, hIdx.get("테이블명(영문)")) : null;
				String krNm = getStr(row, hIdx.get("테이블명(한글)"));
				String desc = hIdx.containsKey("설명") ? getStr(row, hIdx.get("설명")) : null;
				String bizAreaNm = hIdx.containsKey("업무영역") ? getStr(row, hIdx.get("업무영역")) : null;
				String tablespace = hIdx.containsKey("테이블스페이스") ? getStr(row, hIdx.get("테이블스페이스")) : null;
				// 업무영역명 → id 매핑. 입력값이 등록된 업무영역과 안 맞으면 무시(null) + 경고
				String bizAreaId = null;
				if (bizAreaNm != null && !bizAreaNm.trim().isEmpty()) {
					bizAreaId = bizAreaNmToId.get(bizAreaNm.trim());
					if (bizAreaId == null) {
						warnings.add(warnRow(r + 1, "업무영역 '" + bizAreaNm + "' 미등록 — 무시됨"));
					}
				}
				Map<String, Object> m = new HashMap<>();
				m.put("row", r + 1);
				m.put("objOwner", owner);
				m.put("objNm", enNm);     // 영문명 — 비어있으면 commit 시 TMP_TBL_N 자동
				m.put("objNmKr", krNm);
				m.put("objDesc", desc);
				m.put("bizAreaNm", bizAreaNm);
				m.put("bizAreaId", bizAreaId);
				m.put("tablespaceNm", tablespace);
				// 109번 #3 — 소유자·영문명·테이블스페이스는 [DB 반영] DDL 에 들어간다
				String nameErr = null;
				try {
					DdlSafety.identOpt("소유자", owner);
					DdlSafety.identOpt("테이블", enNm);
					DdlSafety.identOpt("테이블스페이스", tablespace);
				} catch (DdlSafety.Invalid e) {
					nameErr = e.getMessage();
				}
				// 소유자 필수 + 영문/한글 중 하나는 필수 (둘 중 하나만 있어도 등록 가능)
				if (isBlank(owner) || (isBlank(krNm) && isBlank(enNm))) {
					m.put("_action", "ERROR");
					m.put("_msg", "소유자 필수 + 테이블명(영문/한글) 중 하나는 필수");
					errors.add(errRow(r + 1, "소유자 필수 + 테이블명(영문/한글) 중 하나는 필수"));
				} else if (nameErr != null) {
					m.put("_action", "ERROR");
					m.put("_msg", nameErr);
					errors.add(errRow(r + 1, nameErr));
				} else {
					String keyKr = isBlank(krNm) ? null : (owner + "|" + krNm);
					String keyEn = isBlank(enNm) ? null : (owner + "|" + enNm);
					if (keyKr != null && seenKr.contains(keyKr)) {
						m.put("_action", "ERROR");
						m.put("_msg", "파일 내 한글명 중복 (" + owner + ", " + krNm + ")");
						errors.add(errRow(r + 1, "파일 내 한글명 중복"));
					} else if (keyEn != null && seenEn.contains(keyEn)) {
						m.put("_action", "ERROR");
						m.put("_msg", "파일 내 영문명 중복 (" + owner + ", " + enNm + ")");
						errors.add(errRow(r + 1, "파일 내 영문명 중복"));
					} else if (keyKr != null && existingKr.contains(keyKr)) {
						m.put("_action", "SKIP");
						m.put("_msg", "이미 존재 (한글명) — 스킵");
						warnings.add(warnRow(r + 1, "이미 존재하는 한글명 (" + owner + ", " + krNm + ") — 스킵"));
					} else if (keyEn != null && existingEn.contains(keyEn)) {
						m.put("_action", "SKIP");
						m.put("_msg", "이미 존재 (영문명) — 스킵");
						warnings.add(warnRow(r + 1, "이미 존재하는 영문명 (" + owner + ", " + enNm + ") — 스킵"));
					} else {
						m.put("_action", "INSERT");
						if (keyKr != null) seenKr.add(keyKr);
						if (keyEn != null) seenEn.add(keyEn);
					}
				}
				// 104번 WP8-31 — 등록할 행만 속성 값 검사 (필수 빠짐·유형 틀림 → 오류 행)
				if ("INSERT".equals(m.get("_action"))) {
					Map<String, Object> sentUdp = new HashMap<>();
					for (Map<String, Object> d : udpDefs) {
						Integer ci = udpIdx.get((String) d.get("udpNm"));
						if (ci != null) sentUdp.put((String) d.get("udpId"), getStr(row, ci));
					}
					try {
						m.put("udpVals", udpService.check(new HashMap<>(), sentUdp));
					} catch (IllegalArgumentException udpErr) {
						m.put("_action", "ERROR");
						m.put("_msg", udpErr.getMessage());
						errors.add(errRow(r + 1, udpErr.getMessage()));
					}
				}
				rows.add(m);
			}
		}

		Map<String, Object> out = new HashMap<>();
		out.put("rows", rows);
		out.put("errors", errors);
		out.put("warnings", warnings);
		return out;
	}

	private Map<String, Object> parseAttrWorkbook(MultipartFile file, String dataModelId) throws Exception {
		List<Map<String, Object>> rows = new ArrayList<>();
		List<Map<String, Object>> errors = new ArrayList<>();
		List<Map<String, Object>> warnings = new ArrayList<>();

		// 86번 #11 — 테이블 매핑 양방향 (KR↔EN). 한·영 둘 중 하나만 있어도 objNm 해석 가능하게.
		Map<String, String> objKrToNm = loadObjKrToNm(dataModelId);
		Map<String, String> objEnToKr = loadObjEnToKr(dataModelId);
		// DB 에 이미 등록된 (owner|objNm|attrKr) / (owner|objNm|attrEn) — 중복 SKIP 용
		Set<String> existingAttrKr = loadExistingAttrKrs(dataModelId);
		Set<String> existingAttrEn = loadExistingAttrEns(dataModelId);
		// 같은 파일 내 중복 체크 (owner|tbl|col 한글 또는 영문 어느 쪽이든)
		Set<String> seenAttrs = new HashSet<>();
		Set<String> groups = new HashSet<>();

		try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
			if (wb.getNumberOfSheets() > 1) {
				warnings.add(warnRow(0, "2번째 이후 시트는 무시됨"));
			}
			Sheet sh = wb.getSheetAt(0);
			Row header = sh.getRow(0);
			if (header == null) throw new IllegalArgumentException("시트가 비어있습니다.");
			Map<String, Integer> hIdx = mapHeaders(header, ATTR_HEADERS);
			if (!hIdx.containsKey("소유자"))
				throw new IllegalArgumentException("필수 헤더 누락: 소유자");

			int last = sh.getLastRowNum();
			for (int r = 1; r <= last; r++) {
				Row row = sh.getRow(r);
				if (row == null || isRowEmpty(row)) continue;
				String owner = getStr(row, hIdx.get("소유자"));
				String tblEn = getStr(row, hIdx.get("테이블명(영문)"));
				String tblKr = getStr(row, hIdx.get("테이블명(한글)"));
				String colEn = getStr(row, hIdx.get("컬럼명(영문)"));
				String colKr = getStr(row, hIdx.get("컬럼명(한글)"));
				String dataType = getStr(row, hIdx.get("데이터타입"));
				String dataLenStr = getStr(row, hIdx.get("길이"));
				String dataDecStr = getStr(row, hIdx.get("소수점자리"));
				String nullable = getStr(row, hIdx.get("NULL여부"));
				String pk = getStr(row, hIdx.get("PK여부"));
				String fk = getStr(row, hIdx.get("FK여부"));
				String defaultVal = getStr(row, hIdx.get("디폴트값"));
				String refTbl = getStr(row, hIdx.get("참조 테이블(한글)"));
				String refCol = getStr(row, hIdx.get("참조 컬럼(한글)"));
				String delRule = getStr(row, hIdx.get("삭제 규칙"));

				Map<String, Object> m = new HashMap<>();
				m.put("row", r + 1);
				m.put("objOwner", owner);
				m.put("objNmKr", tblKr);
				m.put("attrNm", colEn);
				m.put("attrNmKr", colKr);
				m.put("dataType", dataType);
				m.put("dataLen", parseIntSafe(dataLenStr));
				m.put("dataDecimalLen", parseIntSafe(dataDecStr));
				m.put("nullableYn", "N".equalsIgnoreCase(nullable) ? "N" : "Y");
				m.put("pkYn", "Y".equalsIgnoreCase(pk) ? "Y" : "N");
				m.put("fkYn", "Y".equalsIgnoreCase(fk) ? "Y" : "N");
				m.put("defaultVal", defaultVal);
				m.put("refObjOwner", owner);
				m.put("refObjNmKr", refTbl);
				m.put("refAttrNmKr", refCol);
				m.put("deleteRule", normalizeDeleteRule(delRule, r + 1, warnings));

				if (isBlank(owner)) {
					m.put("_action", "ERROR");
					m.put("_msg", "소유자 필수");
					errors.add(errRow(r + 1, "소유자 필수"));
					rows.add(m);
					continue;
				}
				// 86번 #11 — (테이블 영문 + 컬럼 영문) 또는 (테이블 한글 + 컬럼 한글) 중 한 쌍은 필수
				boolean enPairOk = !isBlank(tblEn) && !isBlank(colEn);
				boolean krPairOk = !isBlank(tblKr) && !isBlank(colKr);
				if (!enPairOk && !krPairOk) {
					m.put("_action", "ERROR");
					m.put("_msg", "(테이블+컬럼) 영문 또는 한글 한 쌍은 필수");
					errors.add(errRow(r + 1, "(테이블+컬럼) 영문 또는 한글 한 쌍은 필수"));
					rows.add(m);
					continue;
				}
				// objNm 결정: 영문이 있으면 영문 우선, 없으면 한글 → DB 매핑
				String objNm = null;
				if (!isBlank(tblEn)) {
					String knownKr = objEnToKr.get(owner + "|" + tblEn);
					if (knownKr == null) {
						m.put("_action", "ERROR");
						m.put("_msg", "테이블 먼저 등록 필요(영문): (" + owner + ", " + tblEn + ")");
						errors.add(errRow(r + 1, "테이블 먼저 등록 필요(영문)"));
						rows.add(m);
						continue;
					}
					objNm = tblEn;
					if (m.get("objNmKr") == null) m.put("objNmKr", knownKr);
				} else {
					objNm = objKrToNm.get(owner + "|" + tblKr);
					if (objNm == null) {
						m.put("_action", "ERROR");
						m.put("_msg", "테이블 먼저 등록 필요(한글): (" + owner + ", " + tblKr + ")");
						errors.add(errRow(r + 1, "테이블 먼저 등록 필요(한글)"));
						rows.add(m);
						continue;
					}
				}
				m.put("objNm", objNm);
				groups.add(owner + "|" + objNm);

				// 파일 내 중복 (영문 또는 한글 키 어느 쪽이든)
				String dupKeyEn = !isBlank(colEn) ? (owner + "|" + objNm + "|EN|" + colEn) : null;
				String dupKeyKr = !isBlank(colKr) ? (owner + "|" + objNm + "|KR|" + colKr) : null;
				if ((dupKeyEn != null && seenAttrs.contains(dupKeyEn))
						|| (dupKeyKr != null && seenAttrs.contains(dupKeyKr))) {
					m.put("_action", "ERROR");
					m.put("_msg", "파일 내 중복 컬럼");
					errors.add(errRow(r + 1, "파일 내 중복 컬럼"));
					rows.add(m);
					continue;
				}
				if (dupKeyEn != null) seenAttrs.add(dupKeyEn);
				if (dupKeyKr != null) seenAttrs.add(dupKeyKr);

				// DB 에 이미 등록된 컬럼 → silent SKIP
				boolean dbDup = (dupKeyEn != null && existingAttrEn.contains(owner + "|" + objNm + "|" + colEn))
						|| (dupKeyKr != null && existingAttrKr.contains(owner + "|" + objNm + "|" + colKr));
				if (dbDup) {
					m.put("_action", "SKIP");
					m.put("_msg", "이미 등록된 컬럼");
					warnings.add(warnRow(r + 1, "이미 등록된 컬럼 — 스킵"));
					rows.add(m);
					continue;
				}

				if ("Y".equals(m.get("fkYn")) && (isBlank(refTbl) || isBlank(refCol))) {
					m.put("_action", "ERROR");
					m.put("_msg", "FK=Y 인데 참조 테이블/컬럼 누락");
					errors.add(errRow(r + 1, "FK 참조 정보 누락"));
					rows.add(m);
					continue;
				}
				// 109번 #3 — 소유자·컬럼 영문명·데이터 타입·길이·기본값은 [DB 반영] DDL 에 들어간다
				try {
					DdlSafety.identOpt("소유자", owner);
					DdlSafety.identOpt("컬럼", colEn);
					DdlSafety.dataType(dataType);
					Integer lenIn = parseIntSafe(dataLenStr);
					Integer decIn = parseIntSafe(dataDecStr);
					DdlSafety.length(lenIn == null ? 0 : lenIn, decIn == null ? 0 : decIn);
					DdlSafety.defaultValue(defaultVal, null);
				} catch (DdlSafety.Invalid e) {
					m.put("_action", "ERROR");
					m.put("_msg", e.getMessage());
					errors.add(errRow(r + 1, e.getMessage()));
					rows.add(m);
					continue;
				}

				m.put("_action", "INSERT");
				rows.add(m);
			}
		}

		Map<String, Object> out = new HashMap<>();
		out.put("rows", rows);
		out.put("errors", errors);
		out.put("warnings", warnings);
		out.put("groups", groups.size());
		return out;
	}

	private Map<String, Integer> mapHeaders(Row header, String[] expected) {
		Map<String, Integer> idx = new HashMap<>();
		for (int c = 0; c < header.getLastCellNum(); c++) {
			Cell cell = header.getCell(c);
			if (cell == null) continue;
			String v = cellString(cell).trim();
			for (String e : expected) {
				if (e.equals(v)) { idx.put(e, c); break; }
			}
		}
		return idx;
	}

	private boolean isRowEmpty(Row row) {
		short last = row.getLastCellNum();
		for (int c = 0; c < last; c++) {
			Cell cell = row.getCell(c);
			if (cell != null && !cellString(cell).trim().isEmpty()) return false;
		}
		return true;
	}

	private String getStr(Row row, Integer idx) {
		if (idx == null) return null;
		Cell c = row.getCell(idx);
		if (c == null) return null;
		String s = cellString(c).trim();
		return s.isEmpty() ? null : s;
	}

	private String cellString(Cell cell) {
		if (cell == null) return "";
		if (cell.getCellType() == CellType.NUMERIC) {
			double d = cell.getNumericCellValue();
			if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
			return String.valueOf(d);
		}
		if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
		if (cell.getCellType() == CellType.FORMULA) {
			try { return cell.getStringCellValue(); }
			catch (Exception e) {
				try { return String.valueOf(cell.getNumericCellValue()); }
				catch (Exception ignore) { return ""; }
			}
		}
		return cell.toString();
	}

	private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

	private String normalizeDeleteRule(String v, int row, List<Map<String, Object>> warnings) {
		if (isBlank(v)) return "NO ACTION";
		String up = v.toUpperCase();
		if ("CASCADE".equals(up) || "SET NULL".equals(up) || "NO ACTION".equals(up)) return up;
		warnings.add(warnRow(row, "삭제 규칙 '" + v + "' 허용 외 — NO ACTION 으로 대체"));
		return "NO ACTION";
	}

	/** 컬럼 엑셀 업로드 한 행 → 컬럼 VO (영문명이 없으면 TMP_COL_{순서}) */
	private static StdDataModelAttrVo uploadAttrVo(String dataModelId, Map<String, Object> r, short cur) {
		StdDataModelAttrVo vo = new StdDataModelAttrVo();
		vo.setDataModelId(dataModelId);
		vo.setObjOwner(str(r.get("objOwner")));
		vo.setObjNm(str(r.get("objNm")));
		String enIn = str(r.get("attrNm"));
		vo.setAttrNm((enIn != null && !enIn.trim().isEmpty()) ? enIn.trim() : ("TMP_COL_" + cur));
		vo.setAttrNmKr(str(r.get("attrNmKr")));
		vo.setAttrOrder(cur);
		String typeIn = str(r.get("dataType"));
		vo.setDataType((typeIn != null && !typeIn.trim().isEmpty()) ? typeIn.trim() : "VARCHAR");
		Integer lenIn = parseIntSafe(r.get("dataLen"));
		vo.setDataLen(lenIn != null ? lenIn.longValue() : 255L);
		Integer decIn = parseIntSafe(r.get("dataDecimalLen"));
		if (decIn != null) vo.setDataDecimalLen(decIn.shortValue());
		String pk = str(r.get("pkYn"));
		String fk = str(r.get("fkYn"));
		vo.setPkYn("Y".equals(pk) ? "Y" : "N");
		vo.setFkYn("Y".equals(fk) ? "Y" : "N");
		// 86번 #11 — NULL여부/디폴트값을 업로드 row 에서 그대로 보존 (PK 면 N 강제)
		String nullableIn = str(r.get("nullableYn"));
		vo.setNullableYn("Y".equals(vo.getPkYn()) ? "N"
				: (nullableIn == null || nullableIn.isEmpty() ? "Y" : nullableIn));
		vo.setDefaultVal(str(r.get("defaultVal")));
		vo.setTermsStndYn("N");
		vo.setDomainStndYn("N");
		return vo;
	}

	private Map<String, Object> errRow(int row, String msg) {
		Map<String, Object> m = new HashMap<>();
		m.put("row", row);
		m.put("message", msg);
		return m;
	}

	private Map<String, Object> warnRow(int row, String msg) {
		Map<String, Object> m = new HashMap<>();
		m.put("row", row);
		m.put("message", msg);
		return m;
	}

	private Set<String> loadExistingObjKrs(String dataModelId) {
		Set<String> out = new HashSet<>();
		List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		if (objs == null) return out;
		for (StdDataModelObjVo o : objs) {
			if (o.getObjNmKr() == null) continue;
			String owner = o.getObjOwner() == null ? "" : o.getObjOwner();
			out.add(owner + "|" + o.getObjNmKr());
		}
		return out;
	}

	/** 86번 #9 — 컬럼 업로드 시 DB 기존 컬럼 한글명 dup 체크 (owner|objNm|attrKr) */
	private Set<String> loadExistingAttrKrs(String dataModelId) {
		Set<String> out = new HashSet<>();
		List<StdDataModelAttrVo> attrs = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", dataModelId);
		if (attrs == null) return out;
		for (StdDataModelAttrVo a : attrs) {
			if (a.getAttrNmKr() == null || a.getObjNm() == null) continue;
			String owner = a.getObjOwner() == null ? "" : a.getObjOwner();
			out.add(owner + "|" + a.getObjNm() + "|" + a.getAttrNmKr());
		}
		return out;
	}

	/** 86번 #11 — 컬럼 업로드 시 DB 기존 컬럼 영문명 dup 체크 (owner|objNm|attrNm) */
	private Set<String> loadExistingAttrEns(String dataModelId) {
		Set<String> out = new HashSet<>();
		List<StdDataModelAttrVo> attrs = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", dataModelId);
		if (attrs == null) return out;
		for (StdDataModelAttrVo a : attrs) {
			if (a.getAttrNm() == null || a.getObjNm() == null) continue;
			String owner = a.getObjOwner() == null ? "" : a.getObjOwner();
			out.add(owner + "|" + a.getObjNm() + "|" + a.getAttrNm());
		}
		return out;
	}

	/** 86번 #11 — 테이블 영문(objNm) → 한글(objNmKr) 매핑 (영문 입력만으로 테이블 검증할 때) */
	private Map<String, String> loadObjEnToKr(String dataModelId) {
		Map<String, String> out = new HashMap<>();
		List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		if (objs == null) return out;
		for (StdDataModelObjVo o : objs) {
			if (o.getObjNm() == null) continue;
			String owner = o.getObjOwner() == null ? "" : o.getObjOwner();
			out.put(owner + "|" + o.getObjNm(), o.getObjNmKr() == null ? "" : o.getObjNmKr());
		}
		return out;
	}

	/** 86번 #9 — 영문명(물리) 중복 체크용 */
	private Set<String> loadExistingObjEns(String dataModelId) {
		Set<String> out = new HashSet<>();
		List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		if (objs == null) return out;
		for (StdDataModelObjVo o : objs) {
			if (o.getObjNm() == null) continue;
			String owner = o.getObjOwner() == null ? "" : o.getObjOwner();
			out.add(owner + "|" + o.getObjNm());
		}
		return out;
	}

	private Map<String, String> loadObjKrToNm(String dataModelId) {
		Map<String, String> out = new HashMap<>();
		List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		if (objs == null) return out;
		for (StdDataModelObjVo o : objs) {
			if (o.getObjNmKr() == null) continue;
			String owner = o.getObjOwner() == null ? "" : o.getObjOwner();
			out.put(owner + "|" + o.getObjNmKr(), o.getObjNm());
		}
		return out;
	}

	private String resolveObjNmByKr(SqlSession session, String dataModelId, String owner, String objKr) {
		if (isBlank(objKr)) return null;
		List<StdDataModelObjVo> objs = session.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		if (objs == null) return null;
		for (StdDataModelObjVo o : objs) {
			if (objKr.equals(o.getObjNmKr())) {
				if (owner == null || owner.equals(o.getObjOwner())) return o.getObjNm();
			}
		}
		return null;
	}

	private String resolveAttrNmByKr(SqlSession session, String dataModelId, String objNm, String attrKr) {
		if (isBlank(attrKr)) return null;
		List<StdDataModelAttrVo> attrs = session.selectList("datamodel.selectDataModelAttrListByClctId", dataModelId);
		if (attrs == null) return null;
		for (StdDataModelAttrVo a : attrs) {
			if (objNm.equals(a.getObjNm()) && attrKr.equals(a.getAttrNmKr())) return a.getAttrNm();
		}
		return null;
	}
}
