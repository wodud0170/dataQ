package qualitycenter.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.bean.SecurityManager;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelCollectVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.quality.service.ExcelDownloadService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ws.WebSocketService;
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
			// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
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
			// TODO Auto-generated catch block
			log.error(">> download data model objects excel failed : ", e);
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
			// TODO Auto-generated catch block
			log.error(">> download data model attributes excel failed : ", e);
		}
	}

	//데이터모델 수집
	@RequestMapping(value = "/collectDataModel", method = RequestMethod.POST)
	public Mono<Response> collectDataModel(HttpServletRequest request, @RequestBody StdDataModelVo dataVo) {
		log.info(">> started collect data model : {}", dataVo);

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/dm/collectDataModel");
		Mono<Response> mResponse = webClientHandler.postData(sessionService.getUserId(),
				String.valueOf(request.getSession().getAttribute("SSID")), dataVo);

		log.info(">> finished collect data model : {}", dataVo);
		return mResponse;
	}
}
