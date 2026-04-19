package qualitycenter.controller;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
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
import com.ndata.quality.model.std.StdDataModelStatsVo;
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

	/** 수집된 인덱스 목록 조회 (수집 ID 기준) */
	@RequestMapping(value = "/getDataModelIndexListByClctId", method = RequestMethod.GET)
	public List<java.util.Map<String, Object>> getDataModelIndexListByClctId(String clctId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelIndexListByClctId", clctId);
	}

	/** 수집된 제약조건 목록 조회 (수집 ID 기준) */
	@RequestMapping(value = "/getDataModelConstraintListByClctId", method = RequestMethod.GET)
	public List<java.util.Map<String, Object>> getDataModelConstraintListByClctId(String clctId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelConstraintListByClctId", clctId);
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
	 * 기존 수집 구조(TB_DATA_MODEL_CLCT, TB_DATA_MODEL_OBJ, TB_DATA_MODEL_ATTR, TB_DATA_MODEL_STATS)를 재활용한다.
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

			// 5) 통계 등록
			StdDataModelStatsVo statsVo = new StdDataModelStatsVo();
			statsVo.setClctId(clctId);
			statsVo.setDataModelId(dataModelId);
			session.insert("datamodel.insertDataModelStats", statsVo);

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
			String clctId = resolveLatestClctId(objVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다. 먼저 수집하거나 모델을 생성하세요.");
			if (objVo.getObjNm() == null || objVo.getObjNm().trim().isEmpty())
				throw new IllegalArgumentException("테이블 물리명(objNm)은 필수입니다.");

			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("clctId", clctId);
			dupParam.put("objNm", objVo.getObjNm());
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelObj", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 테이블입니다: " + objVo.getObjNm());

			objVo.setClctId(clctId);
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
			String clctId = resolveLatestClctId(objVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다.");
			objVo.setClctId(clctId);
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
			String clctId = resolveLatestClctId(objVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다.");
			Map<String, Object> param = new HashMap<>();
			param.put("clctId", clctId);
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
	 * 컬럼(ATTR) 추가 — 표준 검증 후 INSERT
	 */
	@RequestMapping(value = "/addAttr", method = RequestMethod.POST)
	public Mono<Response> addAttr(@RequestBody StdDataModelAttrVo attrVo) {
		Response result = new Response();
		SqlSession session = sqlSessionFactory.openSession();
		try {
			String clctId = resolveLatestClctId(attrVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다.");
			if (attrVo.getObjNm() == null || attrVo.getAttrNm() == null)
				throw new IllegalArgumentException("테이블명/컬럼명은 필수입니다.");
			validateAttrStandards(attrVo);

			Map<String, Object> dupParam = new HashMap<>();
			dupParam.put("clctId", clctId);
			dupParam.put("objNm", attrVo.getObjNm());
			dupParam.put("attrNm", attrVo.getAttrNm());
			int dup = sqlSessionTemplate.selectOne("datamodel.countDataModelAttr", dupParam);
			if (dup > 0) throw new IllegalStateException("이미 존재하는 컬럼입니다: " + attrVo.getAttrNm());

			attrVo.setClctId(clctId);
			// 순번 자동 부여
			Map<String, Object> ordParam = new HashMap<>();
			ordParam.put("clctId", clctId);
			ordParam.put("objNm", attrVo.getObjNm());
			Short maxOrd = sqlSessionTemplate.selectOne("datamodel.selectMaxAttrOrd", ordParam);
			attrVo.setAttrOrder((short) ((maxOrd == null ? 0 : maxOrd) + 1));
			// 표준 검증 결과 Y/N 저장
			applyStandardFlags(attrVo);
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
			String clctId = resolveLatestClctId(attrVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다.");
			attrVo.setClctId(clctId);
			validateAttrStandards(attrVo);
			applyStandardFlags(attrVo);
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
			String clctId = resolveLatestClctId(attrVo.getDataModelId());
			if (clctId == null) throw new IllegalStateException("최신 스냅샷을 찾을 수 없습니다.");
			Map<String, Object> param = new HashMap<>();
			param.put("clctId", clctId);
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

	// ---------- 내부 헬퍼 ----------

	private String resolveLatestClctId(String dataModelId) {
		if (dataModelId == null) return null;
		return sqlSessionTemplate.selectOne("datamodel.selectLatestClctIdByDmId", dataModelId);
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
}
