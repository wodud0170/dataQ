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
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelCollectVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.model.std.StdDataModelSchemaVo;
import com.ndata.quality.model.std.StdDataModelStatsVo;
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.quality.service.ExcelDownloadService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ws.WebSocketService;
import qualitycenter.util.ErwinXmlParser;
import qualitycenter.util.ErwinXmlParser.ErwinParseResult;
import reactor.core.publisher.Mono;

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
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private WebSocketService websocketService;

	// 데이터모델 정보
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

	//데이터모델 현황 목록 조회 : 조회조건 - 모델명, 시스템명
	@RequestMapping(value = "/getDataModelStatsList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelVo> getDataModelStatsList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelStatsList", retCond);
	}

	//데이터모델 현황 정보 조회 : 조회조건 - 수집ID
	@RequestMapping(value = "/getDataModelStatsByClctId", method = RequestMethod.GET)
	public StdDataModelVo getDataModelStatsByClctId(String clctId) {
		return sqlSessionTemplate.selectOne("datamodel.selectDataModelStatsByClctId", clctId);
	}

	//데이터모델 목록 조회 : 조회조건 - 모델명, 시스템명
	@RequestMapping(value = "/getDataModelList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelList", retCond);
	}

	//데이터모델 수집 목록 조회 : 조회조건 - 모델ID, 모델명, 시스템명
	@RequestMapping(value = "/getDataModelClctList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelClctList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelClctList", retCond);
	}

	//데이터모델 객체(테이블) 수집 목록 조회
	@RequestMapping(value = "/getDataModelObjListByClctId", method = RequestMethod.GET)
	public List<StdDataModelObjVo> getDataModelObjListByClctId(String clctId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", clctId);
	}

	//데이터모델 객체(테이블) 수집 목록 검색
	@RequestMapping(value = "/getDataModelObjListByObjNm", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelAttrVo> getDataModelObjListByObjNm(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByObjNm", retCond);
	}

	//데이터모델 객체(테이블) 수집 목록 다운로드
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

	//데이터모델 속성(컬럼) 수집 목록 조회
	@RequestMapping(value = "/getDataModelAttrListByClctId", method = RequestMethod.GET)
	public List<StdDataModelAttrVo> getDataModelAttrListByClctId(String clctId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", clctId);
	}

	//데이터모델 속성(컬럼) 수집 목록 검색
	@RequestMapping(value = "/getDataModelAttrListByRetreiveCond", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelAttrVo> getDataModelAttrListByRetreiveCond(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByRetreiveCond", retCond);
	}

	//데이터모델 속성(컬럼) 수집 목록 다운로드
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

	// 데이터모델 스키마 목록 조회 (대상 DB에 직접 접속하여 스키마 조회)
	@RequestMapping(value = "/getSchemaList", method = RequestMethod.POST)
	public List<String> getSchemaList(@RequestBody StdDataModelVo dataVo) {
		log.info(">> getSchemaList : dsId={}", dataVo.getDataModelDsId());
		List<String> schemaList = new ArrayList<>();
		DBHandler dbHandler = null;
		try {
			DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dataVo.getDataModelDsId());
			dataSource.setPwd(securityUtils.decryptStr(dataSource.getPwd()));
			dbHandler = DBHandler.getDBHandler(dataSource);
			String sql = getSchemaListSql(dataSource.getDbmsTp());
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
		return schemaList;
	}

	private String getSchemaListSql(String dbmsTp) {
		if (dbmsTp == null) {
			return "SELECT schema_name AS schemaNm FROM information_schema.schemata"
				+ " WHERE schema_owner = current_user ORDER BY schema_name";
		}
		switch (dbmsTp) {
			case "Oracle":
				// 현재 접속 유저가 소유한 스키마만 반환
				return "SELECT USER AS schemaNm FROM DUAL";
			case "MariaDB":
				// 현재 접속 유저가 접근 가능한 DB만 반환
				return "SELECT SCHEMA_NAME AS schemaNm FROM information_schema.SCHEMATA"
					+ " WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys')"
					+ " AND SCHEMA_NAME = DATABASE()"
					+ " ORDER BY SCHEMA_NAME";
			case "Cubrid":
				// 현재 접속 유저가 소유한 오브젝트의 owner만 반환
				return "SELECT DISTINCT OWNER_NAME AS schemaNm FROM DB_CLASS"
					+ " WHERE is_system_class = 'NO' AND CLASS_TYPE = 'CLASS'"
					+ " AND OWNER_NAME = CURRENT_USER ORDER BY OWNER_NAME";
			case "MSSQL":
				// 현재 접속 유저가 소유한 스키마만 반환
				return "SELECT name AS schemaNm FROM sys.schemas WHERE principal_id = USER_ID() ORDER BY name";
			case "PostgreSQL":
			default:
				// 현재 접속 유저가 소유한 스키마만 반환
				return "SELECT schema_name AS schemaNm FROM information_schema.schemata"
					+ " WHERE schema_owner = current_user"
					+ " ORDER BY schema_name";
		}
	}

	// 데이터모델 스키마 수집 필터 조회
	@RequestMapping(value = "/getDataModelSchemas", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelSchemaVo> getDataModelSchemas(String dataModelId) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelSchemaList", dataModelId);
	}

	// 데이터모델 스키마 수집 필터 저장 (기존 전체 삭제 후 재저장)
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

	//데이터모델 수집이력 목록 조회
	@RequestMapping(value = "/getDataModelHistoryList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDataModelCollectVo> getDataModelHistoryList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		return sqlSessionTemplate.selectList("datamodel.selectDataModelHistoryList", retCond);
	}

	//데이터모델 수집
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
}
