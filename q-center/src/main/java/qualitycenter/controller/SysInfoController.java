package qualitycenter.controller;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.bean.DriverLoader;
import com.ndata.bean.SecurityManager;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.datasource.dbms.connection.DBConnection;
import com.ndata.model.DataSourceVo;
import com.ndata.model.DriverVo;
import com.ndata.module.StringUtils;
import com.ndata.quality.model.mgmt.SysInfoVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ws.WebSocketService;
import reactor.core.publisher.Mono;

@Tag(name = "시스템정보", description = "시스템정보 API")
@Slf4j
@RestController
@RequestMapping("/api/sysinfo")
public class SysInfoController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	SecurityManager securityUtils;

	@Autowired
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private WebSocketService websocketService;

	// 시스템정보
	@RequestMapping(value = "/createSysInfo", method = RequestMethod.POST)
	public Mono<Response> createSysInfo(@RequestBody SysInfoVo dataVo) {
		dataVo.setSysCd(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		log.info(">> createSysInfo : {}", dataVo);

		Response result = new Response();

		try {
			sqlSessionTemplate.insert("sysinfo.insertSysInfo", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateSysInfo", method = RequestMethod.POST)
	public Mono<Response> updateSysInfo(@RequestBody SysInfoVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateSysInfo : {}", dataVo);

		Response result = new Response();

		try {
			sqlSessionTemplate.update("sysinfo.updateSysInfo", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteSysInfos", method = RequestMethod.POST)
	public Mono<Response> deleteSysInfos(@RequestBody List<SysInfoVo> dataVos) {
		Response result = new Response();

		try {
			sqlSessionTemplate.delete("sysinfo.deleteSysInfos", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getSysInfoList", method = RequestMethod.GET)
	public List<SysInfoVo> getSysInfoList() {
		return sqlSessionTemplate.selectList("sysinfo.selectSysInfoList");
	}

	@RequestMapping(value = "/getSysInfoBySysCd", method = RequestMethod.GET)
	public SysInfoVo getSysInfoBySysCd(String sysCd) {
		return sqlSessionTemplate.selectOne("sysinfo.selectSysInfoBySysCd", sysCd);
	}

	// 데이터소스
	@RequestMapping(value = "/createDataSource", method = RequestMethod.POST)
	public Mono<Response> createDataSource(@RequestBody DataSourceVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		log.info(">> createDataSource : {}", dataVo);

		Response result = new Response();

		try {
			dataVo.setPwd(securityUtils.encryptStr(dataVo.getPwd()));	// password를 암호화해서 저장한다.
			sqlSessionTemplate.insert("sysinfo.insertDataSource", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateDataSource", method = RequestMethod.POST)
	public Mono<Response> updateDataSource(@RequestBody DataSourceVo dataVo) {
		log.info(">> updateDataSource : {}", dataVo);
		
		Response result = new Response();

		try {
			dataVo.setPwd(securityUtils.encryptStr(dataVo.getPwd()));	// password를 암호화해서 저장한다.
			sqlSessionTemplate.update("sysinfo.updateDataSource", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteDataSources", method = RequestMethod.POST)
	public Mono<Response> deleteDataSources(@RequestBody List<DataSourceVo> dataVos) {
		Response result = new Response();

		try {
			sqlSessionTemplate.delete("sysinfo.deleteDataSources", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getDataSourceList", method = RequestMethod.GET)
	public List<DataSourceVo> getDataSourceList() {
		List<DataSourceVo> dsVos = sqlSessionTemplate.selectList("sysinfo.selectDataSourceList");
		// password를 복호화한다.
		for (DataSourceVo dsVo : dsVos) {
			dsVo.setPwd(securityUtils.decryptStr(dsVo.getPwd()));
		}
		return dsVos;
	}

	@RequestMapping(value = "/getDataSourceListBySysCd", method = RequestMethod.GET)
	public List<DataSourceVo> getDataSourceListBySysCd(String sysCd) {
		List<DataSourceVo> dsVos = sqlSessionTemplate.selectList("sysinfo.selectDataSourceListBySysCd", sysCd);
		return dsVos;
	}

	@RequestMapping(value = "/getDataSourceById", method = RequestMethod.GET)
	public DataSourceVo getDataSourceById(String dsId) {
		DataSourceVo dsVo = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
		dsVo.setPwd(securityUtils.decryptStr(dsVo.getPwd()));	// password를 복호화해서 전달한다.
		return dsVo;
	}

	@RequestMapping(value = "/testDataSource", method = RequestMethod.POST)
	public Mono<Response> testDataSource(@RequestBody DataSourceVo dataVo) {
		Response result = new Response();
		DBConnection dbConn = null;

		try {
			log.info(">> testDataSourceById : {}", dataVo);
			dbConn = new DBConnection(dataVo, false);
			dbConn.connect();
			// set response message
			result.setResultInfo(RestResult.CODE_200);
			log.info("test datasource success - data={}", dataVo);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			log.error("test datasource failed", e);
		} finally {
			try {
				dbConn.close();
			} catch (Exception se) {
			}
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getDataSourceDrivers", method = RequestMethod.GET)
	public List<DriverVo> getDataSourceDrivers() {
		DriverLoader.load();	//데이터소스 드라이버 목록을 로드한다.
		return DriverLoader.getDrivers();
	}
}
