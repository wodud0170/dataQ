package qualitycenter.controller;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import qualitycenter.service.ddl.DdlGenerator;
import qualitycenter.service.ws.WebSocketService;
import qualitycenter.util.ErwinXmlParser;
import qualitycenter.util.ErwinXmlParser.ErwinParseResult;
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
	private ExcelDownloadService excelDownloadService;

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

		try {
			sqlSessionTemplate.insert("datamodel.insertDataModel", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> createDataModel failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
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
			sqlSessionTemplate.update("datamodel.updateDataModel", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateDataModel failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
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
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteDataModels failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
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
	 * @param dbType      DB 타입 (oracle/postgres 등, 미지정 시 모델의 데이터소스 타입으로 자동 판정)
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
				resolvedDbType = resolveDbTypeByModel(dataModelId);
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

	private String resolveDbTypeByModel(String dataModelId) {
		try {
			Map<String, Object> model = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
			if (model == null) return "postgres";
			String dsId = model.get("dataModelDsId") == null ? null : String.valueOf(model.get("dataModelDsId"));
			if (dsId == null || dsId.trim().isEmpty() || "null".equals(dsId)) return "postgres";
			DataSourceVo ds = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
			if (ds == null) return "postgres";
			String driverName = ds.getDriverName();
			if (driverName == null) return "postgres";
			String lower = driverName.toLowerCase();
			if (lower.contains("oracle")) return "oracle";
			return "postgres";
		} catch (Exception e) {
			log.warn("resolveDbTypeByModel failed: {}", e.getMessage());
			return "postgres";
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
		return sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", clctId);
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
		return sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", clctId);
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
		DBHandler dbHandler = null;
		try {
			DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dataVo.getDataModelDsId());
			dbHandler = dataSourceUtils.getDBHandler(dataSource);
			currentUser = dataSource.getUserId();

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
		result.put("schemas", schemaList);
		result.put("currentUser", currentUser);
		return result;
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

			// 물리명이 비어있으면 TMP_TBL_N 자동 생성 (한글명만 입력된 논리 모델 케이스)
			if (objVo.getObjNm() == null || objVo.getObjNm().trim().isEmpty()) {
				Integer cnt = sqlSessionTemplate.selectOne("datamodel.countDataModelObjByDm", objVo.getDataModelId());
				int seq = (cnt == null ? 0 : cnt) + 1;
				objVo.setObjNm("TMP_TBL_" + seq);
			}

			// 물리명 중복 체크
			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", objVo.getDataModelId());
			dupParam.put("objNm", objVo.getObjNm());
			Integer dup = sqlSessionTemplate.selectOne("datamodel.countDataModelObjByDmId", dupParam);
			if (dup != null && dup > 0) throw new IllegalStateException("이미 존재하는 테이블입니다: " + objVo.getObjNm());

			if (objVo.getObjAttrCnt() == 0) objVo.setObjAttrCnt((short) 0);
			session.insert("datamodel.insertDataModelObj", objVo);
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

	/**
	 * 테이블(OBJ) 수정 — 논리명/오너/설명
	 */
	@RequestMapping(value = "/updateObj", method = RequestMethod.POST)
	public Mono<Response> updateObj(@RequestBody StdDataModelObjVo objVo) {
		Response result = new Response();
		try {
			sqlSessionTemplate.update("datamodel.updateDataModelObj", objVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateObj failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
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
			param.put("objNm", objVo.getObjNm());
			session.delete("datamodel.deleteDataModelAttrsByObj", param);
			session.delete("datamodel.deleteDataModelObj", param);
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
	 * 한글명으로 표준 용어 조회 → 영문약어명 + 도메인(타입/길이) 반환
	 * 컬럼 추가 시 [표준 적용] 버튼에서 호출
	 */
	@RequestMapping(value = "/resolveStandard", method = RequestMethod.GET)
	public Map<String, Object> resolveStandard(@RequestParam String termsNm) {
		return resolveTermsInternal(termsNm);
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
		List<StdDataModelAttrVo> targets;
		if (attrs == null || attrs.isEmpty()) {
			targets = sqlSessionTemplate.selectList("datamodel.selectNonStandardAttrs", dataModelId);
		} else {
			Map<String, Object> p = new HashMap<>();
			p.put("dataModelId", dataModelId);
			p.put("attrs", attrs);
			targets = sqlSessionTemplate.selectList("datamodel.selectAttrListByKeys", p);
		}

		for (StdDataModelAttrVo attr : targets) {
			tried++;
			try {
				applyResolvedToAttr(attr);
				succeeded++;
			} catch (Exception e) {
				failed++;
				Map<String, Object> fi = new HashMap<>();
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
		return result;
	}

	/**
	 * 컬럼(ATTR) 추가 — 표준 검증 후 INSERT
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

			boolean isStandard = !"N".equals(attrVo.getTermsStndYn());
			// 표준 컬럼: 물리명/타입 필수 + 표준 검증
			// 비표준 컬럼: 물리명 자동 생성(TMP_COL_{순번}) + 타입 기본값(VARCHAR(255))
			Map<String, Object> ordParam = new HashMap<>();
			ordParam.put("dataModelId", attrVo.getDataModelId());
			ordParam.put("objNm", attrVo.getObjNm());
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

			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", attrVo.getDataModelId());
			dupParam.put("objNm", attrVo.getObjNm());
			dupParam.put("attrNm", attrVo.getAttrNm());
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelAttr", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 컬럼입니다: " + attrVo.getAttrNm());

			session.insert("datamodel.insertDataModelAttr", attrVo);
			session.update("datamodel.syncDataModelObjAttrCnt", dupParam);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
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
			boolean isStandard = !"N".equals(attrVo.getTermsStndYn());
			if (isStandard) {
				validateAttrStandards(attrVo);
				applyStandardFlags(attrVo);
			} else {
				attrVo.setTermsStndYn("N");
				attrVo.setDomainStndYn("N");
			}
			sqlSessionTemplate.update("datamodel.updateDataModelAttr", attrVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateAttr failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 컬럼(ATTR) 삭제
	 */
	@RequestMapping(value = "/deleteAttr", method = RequestMethod.POST)
	public Mono<Response> deleteAttr(@RequestBody StdDataModelAttrVo attrVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			Map<String, Object> param = new HashMap<>();
			param.put("dataModelId", attrVo.getDataModelId());
			param.put("objNm", attrVo.getObjNm());
			param.put("attrNm", attrVo.getAttrNm());
			session.delete("datamodel.deleteDataModelAttr", param);
			session.update("datamodel.syncDataModelObjAttrCnt", param);
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
			Object rawAttrs = body.get("attrs");

			if (dataModelId == null || dataModelId.trim().isEmpty())
				throw new IllegalArgumentException("dataModelId 누락");
			if (objNm == null || objNm.trim().isEmpty())
				throw new IllegalArgumentException("objNm 누락");
			if (!(rawAttrs instanceof List))
				throw new IllegalArgumentException("attrs 배열이 아닙니다.");

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> attrs = (List<Map<String, Object>>) rawAttrs;

			// 현재 최대 ATTR_ORD 조회 (ADD 시 nextOrd 증분 기준)
			Map<String, Object> ordParam = new HashMap<>();
			ordParam.put("dataModelId", dataModelId);
			ordParam.put("objNm", objNm);
			Short maxOrdObj = sqlSessionTemplate.selectOne("datamodel.selectMaxAttrOrd", ordParam);
			short nextOrd = (short) (maxOrdObj == null ? 0 : maxOrdObj);

			int added = 0, updated = 0, deleted = 0;
			List<Map<String, Object>> errors = new ArrayList<>();

			for (int i = 0; i < attrs.size(); i++) {
				Map<String, Object> row = attrs.get(i);
				String mode = str(row.get("mode"));
				try {
					if ("ADD".equalsIgnoreCase(mode)) {
						String attrNmKr = str(row.get("attrNmKr"));
						if (attrNmKr == null || attrNmKr.trim().isEmpty())
							throw new IllegalArgumentException("컬럼 한글명 누락");
						nextOrd++;
						StdDataModelAttrVo vo = new StdDataModelAttrVo();
						vo.setDataModelId(dataModelId);
						vo.setObjNm(objNm);
						vo.setAttrNm("TMP_COL_" + nextOrd);
						vo.setAttrNmKr(attrNmKr);
						vo.setAttrOrder(nextOrd);
						vo.setDataType("VARCHAR");
						vo.setDataLen(255);
						vo.setPkYn("Y".equalsIgnoreCase(str(row.get("pkYn"))) ? "Y" : "N");
						vo.setFkYn("Y".equalsIgnoreCase(str(row.get("fkYn"))) ? "Y" : "N");
						String nullableYn = str(row.get("nullableYn"));
						vo.setNullableYn("Y".equalsIgnoreCase(vo.getPkYn()) ? "N"
								: (nullableYn == null || nullableYn.isEmpty() ? "Y" : nullableYn));
						vo.setDefaultVal(str(row.get("defaultVal")));
						vo.setTermsStndYn("N");
						vo.setDomainStndYn("N");
						session.insert("datamodel.insertDataModelAttr", vo);
						added++;
					} else if ("UPDATE".equalsIgnoreCase(mode)) {
						String attrNm = str(row.get("attrNm"));
						if (attrNm == null || attrNm.trim().isEmpty())
							throw new IllegalArgumentException("attrNm 누락");
						StdDataModelAttrVo vo = new StdDataModelAttrVo();
						vo.setDataModelId(dataModelId);
						vo.setObjNm(objNm);
						vo.setAttrNm(attrNm);
						vo.setAttrNmKr(str(row.get("attrNmKr")));
						vo.setPkYn("Y".equalsIgnoreCase(str(row.get("pkYn"))) ? "Y" : "N");
						vo.setFkYn("Y".equalsIgnoreCase(str(row.get("fkYn"))) ? "Y" : "N");
						String nullableYn = str(row.get("nullableYn"));
						vo.setNullableYn("Y".equalsIgnoreCase(vo.getPkYn()) ? "N"
								: (nullableYn == null || nullableYn.isEmpty() ? "Y" : nullableYn));
						vo.setDefaultVal(str(row.get("defaultVal")));
						// 물리명/타입은 수정하지 않음 — 53번 §6-0 원칙
						vo.setTermsStndYn("N");
						vo.setDomainStndYn("N");
						session.update("datamodel.updateDataModelAttr", vo);
						updated++;
					} else if ("DELETE".equalsIgnoreCase(mode)) {
						String attrNm = str(row.get("attrNm"));
						if (attrNm == null || attrNm.trim().isEmpty())
							throw new IllegalArgumentException("attrNm 누락");
						Map<String, Object> p = new HashMap<>();
						p.put("dataModelId", dataModelId);
						p.put("objNm", objNm);
						p.put("attrNm", attrNm);
						session.delete("datamodel.deleteDataModelAttr", p);
						deleted++;
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

			// 컬럼 개수 동기화
			Map<String, Object> syncParam = new HashMap<>();
			syncParam.put("dataModelId", dataModelId);
			syncParam.put("objNm", objNm);
			session.update("datamodel.syncDataModelObjAttrCnt", syncParam);
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

	private static String str(Object o) {
		return o == null ? null : o.toString();
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
	private Map<String, Object> resolveTermsInternal(String termsNm) {
		Map<String, Object> result = new HashMap<>();
		if (termsNm == null || termsNm.trim().isEmpty()) {
			result.put("found", false);
			result.put("message", "한글명이 비어있습니다.");
			return result;
		}
		try {
			com.ndata.quality.model.std.StdTermsVo terms =
				sqlSessionTemplate.selectOne("terms.selectTermsByNm", termsNm.trim());
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
				com.ndata.quality.model.std.StdDomainVo domain =
					sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm", terms.getDomainNm());
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

		Map<String, Object> resolved = resolveTermsInternal(attrNmKr);
		Boolean found = (Boolean) resolved.get("found");
		if (found == null || !found)
			throw new IllegalStateException((String) resolved.getOrDefault("message", "용어 미등록"));
		if (Boolean.TRUE.equals(resolved.get("domainMissing")))
			throw new IllegalStateException("도메인 미등록");

		String newAttrNm = (String) resolved.get("termsEngAbrvNm");
		if (newAttrNm == null || newAttrNm.trim().isEmpty())
			throw new IllegalStateException("용어 영문약어명 누락");

		if (!newAttrNm.equals(attr.getAttrNm())) {
			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("dataModelId", attr.getDataModelId());
			dupParam.put("objNm", attr.getObjNm());
			dupParam.put("attrNm", newAttrNm);
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelAttr", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 컬럼: " + newAttrNm);
		}

		StdDataModelAttrVo flagsVo = new StdDataModelAttrVo();
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
		updateMap.put("objNm", attr.getObjNm());
		updateMap.put("origAttrNm", attr.getAttrNm());
		updateMap.put("attrNm", newAttrNm);
		updateMap.put("attrNmKr", attrNmKr);
		updateMap.put("dataType", resolved.get("dataType"));
		updateMap.put("dataLen", dataLen);
		updateMap.put("dataDecimalLen", dataDecimalLen);
		updateMap.put("termsStndYn", "Y");
		updateMap.put("domainStndYn", "Y");
		updateMap.put("wordLst", flagsVo.getWordLst());
		updateMap.put("wordStndLst", flagsVo.getWordStndLst());
		sqlSessionTemplate.update("datamodel.updateDataModelAttrKey", updateMap);
	}

	/**
	 * 컬럼 표준 검증 — 영문명 토큰은 모두 표준 단어에 존재하고, 도메인(타입/길이)은 TB_DOMAIN과 일치해야 함
	 * (테이블·인덱스·제약조건은 표준 검증 대상 아님 — 컬럼만 강제)
	 */
	private void validateAttrStandards(StdDataModelAttrVo attrVo) {
		String attrNm = attrVo.getAttrNm();
		if (attrNm == null || attrNm.trim().isEmpty())
			throw new IllegalArgumentException("컬럼 물리명(attrNm)은 필수입니다.");
		List<String> tokens = splitTokens(attrNm);
		List<String> missing = findMissingWords(tokens);
		if (!missing.isEmpty()) {
			throw new IllegalStateException("표준 미준수: 컬럼명에 표준 단어가 아닌 토큰이 있습니다 → " + String.join(", ", missing));
		}
		if (attrVo.getDataType() == null || attrVo.getDataType().trim().isEmpty()) {
			throw new IllegalArgumentException("데이터 타입은 필수입니다.");
		}
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

	private List<String> findMissingWords(List<String> tokens) {
		List<String> missing = new ArrayList<>();
		if (tokens.isEmpty()) return missing;
		List<StdWordVo> found = sqlSessionTemplate.selectList("word.selectWordsByEngAbrvNms", tokens);
		java.util.Set<String> foundSet = new java.util.HashSet<>();
		for (StdWordVo w : found) {
			if (w.getWordEngAbrvNm() != null) foundSet.add(w.getWordEngAbrvNm().toUpperCase());
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

	private static final String[] TABLE_HEADERS = { "소유자", "테이블명(한글)", "설명" };
	private static final String[] ATTR_HEADERS = {
		"소유자", "테이블명(한글)", "컬럼명(한글)", "컬럼 순서",
		"PK여부", "FK여부", "참조 테이블(한글)", "참조 컬럼(한글)", "삭제 규칙"
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
						seq++;
						StdDataModelObjVo vo = new StdDataModelObjVo();
						vo.setDataModelId(dataModelId);
						vo.setObjOwner(str(r.get("objOwner")));
						vo.setObjNmKr(str(r.get("objNmKr")));
						vo.setObjDesc(str(r.get("objDesc")));
						vo.setObjNm("TMP_TBL_" + seq);
						vo.setObjAttrCnt((short) 0);
						session.insert("datamodel.insertDataModelObj", vo);
						r.put("objNm", vo.getObjNm());
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

						StdDataModelAttrVo vo = new StdDataModelAttrVo();
						vo.setDataModelId(dataModelId);
						vo.setObjOwner(str(r.get("objOwner")));
						vo.setObjNm(objNm);
						vo.setAttrNm("TMP_COL_" + cur);
						vo.setAttrNmKr(str(r.get("attrNmKr")));
						vo.setAttrOrder(cur);
						vo.setDataType("VARCHAR");
						vo.setDataLen(255);
						String pk = str(r.get("pkYn"));
						String fk = str(r.get("fkYn"));
						vo.setPkYn("Y".equals(pk) ? "Y" : "N");
						vo.setFkYn("Y".equals(fk) ? "Y" : "N");
						vo.setNullableYn("Y".equals(vo.getPkYn()) ? "N" : "Y");
						vo.setTermsStndYn("N");
						vo.setDomainStndYn("N");
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
	 * 업로드 양식 xlsx 스트리밍 다운로드 (서버에서 POI 로 동적 생성)
	 * scope=tables|attrs
	 */
	@RequestMapping(value = "/uploadTemplate", method = RequestMethod.GET)
	public void uploadTemplate(@RequestParam(value = "scope", defaultValue = "tables") String scope,
	                            HttpServletResponse res) throws Exception {
		String[] headers;
		String fileName;
		if ("attrs".equalsIgnoreCase(scope)) {
			headers = ATTR_HEADERS;
			fileName = "dataq_attrs_template.xlsx";
		} else {
			headers = TABLE_HEADERS;
			fileName = "dataq_tables_template.xlsx";
		}
		try (XSSFWorkbook wb = new XSSFWorkbook()) {
			Sheet sh = wb.createSheet("Sheet1");
			Row h = sh.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				h.createCell(i).setCellValue(headers[i]);
				sh.setColumnWidth(i, 5000);
			}
			res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			res.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
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
			for (String h : TABLE_HEADERS) {
				if (!h.equals("설명") && !hIdx.containsKey(h))
					throw new IllegalArgumentException("필수 헤더 누락: " + h);
			}

			Set<String> seen = new HashSet<>();
			// DB 에 이미 존재하는 (owner, objNmKr) 조합 조회
			Set<String> existing = loadExistingObjKrs(dataModelId);

			int last = sh.getLastRowNum();
			for (int r = 1; r <= last; r++) {
				Row row = sh.getRow(r);
				if (row == null || isRowEmpty(row)) continue;
				String owner = getStr(row, hIdx.get("소유자"));
				String krNm = getStr(row, hIdx.get("테이블명(한글)"));
				String desc = hIdx.containsKey("설명") ? getStr(row, hIdx.get("설명")) : null;
				Map<String, Object> m = new HashMap<>();
				m.put("row", r + 1);
				m.put("objOwner", owner);
				m.put("objNmKr", krNm);
				m.put("objDesc", desc);
				if (isBlank(owner) || isBlank(krNm)) {
					m.put("_action", "ERROR");
					m.put("_msg", "소유자·테이블명(한글) 필수");
					errors.add(errRow(r + 1, "소유자·테이블명(한글) 필수"));
				} else {
					String key = owner + "|" + krNm;
					if (seen.contains(key)) {
						m.put("_action", "ERROR");
						m.put("_msg", "파일 내 중복 (" + owner + ", " + krNm + ")");
						errors.add(errRow(r + 1, "파일 내 중복"));
					} else if (existing.contains(key)) {
						m.put("_action", "SKIP");
						m.put("_msg", "이미 존재 — 스킵");
						warnings.add(warnRow(r + 1, "이미 존재하는 (" + owner + ", " + krNm + ") — 스킵"));
					} else {
						m.put("_action", "INSERT");
						seen.add(key);
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

		// DB 의 (owner, objNmKr) → objNm 매핑
		Map<String, String> objKrToNm = loadObjKrToNm(dataModelId);
		// 같은 파일 내 (owner, tableKr, colKr) 중복 체크
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
			for (String h : new String[] { "소유자", "테이블명(한글)", "컬럼명(한글)" }) {
				if (!hIdx.containsKey(h)) throw new IllegalArgumentException("필수 헤더 누락: " + h);
			}

			int last = sh.getLastRowNum();
			for (int r = 1; r <= last; r++) {
				Row row = sh.getRow(r);
				if (row == null || isRowEmpty(row)) continue;
				String owner = getStr(row, hIdx.get("소유자"));
				String tblKr = getStr(row, hIdx.get("테이블명(한글)"));
				String colKr = getStr(row, hIdx.get("컬럼명(한글)"));
				String pk = getStr(row, hIdx.get("PK여부"));
				String fk = getStr(row, hIdx.get("FK여부"));
				String refTbl = getStr(row, hIdx.get("참조 테이블(한글)"));
				String refCol = getStr(row, hIdx.get("참조 컬럼(한글)"));
				String delRule = getStr(row, hIdx.get("삭제 규칙"));

				Map<String, Object> m = new HashMap<>();
				m.put("row", r + 1);
				m.put("objOwner", owner);
				m.put("objNmKr", tblKr);
				m.put("attrNmKr", colKr);
				m.put("pkYn", "Y".equalsIgnoreCase(pk) ? "Y" : "N");
				m.put("fkYn", "Y".equalsIgnoreCase(fk) ? "Y" : "N");
				m.put("refObjOwner", owner); // 같은 오너 내 참조로 가정
				m.put("refObjNmKr", refTbl);
				m.put("refAttrNmKr", refCol);
				m.put("deleteRule", normalizeDeleteRule(delRule, r + 1, warnings));

				if (isBlank(owner) || isBlank(tblKr) || isBlank(colKr)) {
					m.put("_action", "ERROR");
					m.put("_msg", "소유자·테이블명·컬럼명 필수");
					errors.add(errRow(r + 1, "소유자·테이블명·컬럼명 필수"));
					rows.add(m);
					continue;
				}
				String groupKey = owner + "|" + tblKr;
				groups.add(groupKey);
				String objNm = objKrToNm.get(groupKey);
				if (objNm == null) {
					m.put("_action", "ERROR");
					m.put("_msg", "테이블 먼저 등록 필요: (" + owner + ", " + tblKr + ")");
					errors.add(errRow(r + 1, "테이블 먼저 등록 필요"));
					rows.add(m);
					continue;
				}
				m.put("objNm", objNm);

				String dupKey = owner + "|" + tblKr + "|" + colKr;
				if (seenAttrs.contains(dupKey)) {
					m.put("_action", "ERROR");
					m.put("_msg", "파일 내 중복 (" + owner + ", " + tblKr + ", " + colKr + ")");
					errors.add(errRow(r + 1, "파일 내 중복 컬럼"));
					rows.add(m);
					continue;
				}
				seenAttrs.add(dupKey);

				if ("Y".equals(m.get("fkYn")) && (isBlank(refTbl) || isBlank(refCol))) {
					m.put("_action", "ERROR");
					m.put("_msg", "FK=Y 인데 참조 테이블/컬럼 누락");
					errors.add(errRow(r + 1, "FK 참조 정보 누락"));
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
