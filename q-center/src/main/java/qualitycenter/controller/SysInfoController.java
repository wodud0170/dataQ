package qualitycenter.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.bean.DriverLoader;
import com.ndata.bean.SecurityManager;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.datasource.dbms.connection.DBConnection;
import com.ndata.model.DataSourceVo;
import com.ndata.model.DriverVo;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.model.mgmt.DataSourceExtVo;
import com.ndata.quality.model.mgmt.SysInfoVo;
import com.ndata.quality.service.AprvService;

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

	private static final ObjectMapper OM = new ObjectMapper();

	@Autowired
	private AprvService aprvService;

	@Autowired
	private WebSocketService websocketService;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	SecurityManager securityUtils;

	// 시스템정보
	@RequestMapping(value = "/createSysInfo", method = RequestMethod.POST)
	public Mono<Response> createSysInfo(@RequestBody SysInfoVo dataVo) {
		Response denied = rejectWithout(PermCode.SYS_ADMIN);
		if (denied != null) return Mono.just(denied);
		dataVo.setSysCd(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		log.info(">> createSysInfo : {}", dataVo);

		Response result = new Response();

		try {
			// 108 §4.35 — 시스템 결재선이 켜져 있으면 결재 뒤 반영
			String aprvMsg = requestAprv("SAVE", dataVo, null);
			if (aprvMsg == null) sqlSessionTemplate.insert("sysinfo.insertSysInfo", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			if (aprvMsg != null) result.setContents(aprvContents(aprvMsg));
		} catch (Exception e) {
			log.error(">> createSysInfo failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateSysInfo", method = RequestMethod.POST)
	public Mono<Response> updateSysInfo(@RequestBody SysInfoVo dataVo) {
		Response denied = rejectWithout(PermCode.SYS_ADMIN);
		if (denied != null) return Mono.just(denied);
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateSysInfo : {}", dataVo);

		Response result = new Response();

		try {
			String aprvMsg = requestAprv("SAVE", dataVo, sysOf(dataVo.getSysCd()));
			if (aprvMsg == null) sqlSessionTemplate.update("sysinfo.updateSysInfo", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			if (aprvMsg != null) result.setContents(aprvContents(aprvMsg));
		} catch (Exception e) {
			log.error(">> updateSysInfo failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteSysInfos", method = RequestMethod.POST)
	public Mono<Response> deleteSysInfos(@RequestBody List<SysInfoVo> dataVos) {
		Response denied = rejectWithout(PermCode.SYS_ADMIN);
		if (denied != null) return Mono.just(denied);
		Response result = new Response();

		try {
			// 108 §4.35 — 결재선이 켜져 있으면 시스템마다 결재 문서 (진행 중 결재가 있는 시스템이 하나라도 있으면 아무것도 안 함)
			for (SysInfoVo v : dataVos) {
				if (aprvService.openDoc("SYS", v.getSysCd()) != null) throw new IllegalStateException(
						"결재 진행 중인 변경이 있습니다 — 결재가 끝난 뒤 다시 하세요. (" + v.getSysNm() + ")");
			}
			if (aprvService.isLineOn("SYS")) {
				List<String> msgs = new ArrayList<>();
				for (SysInfoVo v : dataVos) msgs.add(requestAprv("DELETE", v, sysOf(v.getSysCd())));
				result.setContents(aprvContents(msgs.get(0) + (msgs.size() > 1 ? " (시스템 " + msgs.size() + "개)" : "")));
			} else {
				sqlSessionTemplate.delete("sysinfo.deleteSysInfos", dataVos);
			}
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteSysInfos failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	// ===== 108 §4.35 — 시스템 결재 (요청 유형 SYS) =====

	private static String aprvContents(String aprvMsg) throws Exception {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("aprvMsg", aprvMsg);
		return OM.writeValueAsString(c);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> sysOf(String sysCd) {
		for (SysInfoVo v : sqlSessionTemplate.<SysInfoVo>selectList("sysinfo.selectSysInfoList")) {
			if (Objects.equals(v.getSysCd(), sysCd)) return OM.convertValue(v, Map.class);
		}
		return null;
	}

	/**
	 * 같은 시스템에 진행 중 결재가 있으면 거부. 시스템 결재선이 꺼져 있으면 null(호출한 쪽이 바로 반영),
	 * 켜져 있으면 결재 문서를 열고 안내 문구 — 반영은 최종 승인 때 {@link #applyPayload}
	 */
	@SuppressWarnings("unchecked")
	private String requestAprv(String action, SysInfoVo v, Map<String, Object> before) throws Exception {
		if (aprvService.openDoc("SYS", v.getSysCd()) != null) throw new IllegalStateException("결재 진행 중인 변경이 있습니다 — 결재가 끝난 뒤 다시 하세요.");
		if (!aprvService.isLineOn("SYS")) return null;
		String userId = sessionService.getUserId();
		String pickMsg = aprvService.checkPicks("SYS", v.getAprvrPicks(), userId);
		if (pickMsg != null) throw new IllegalArgumentException(pickMsg);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("action", action);
		payload.put("kind", "SYS");
		Map<String, Object> data = OM.convertValue(v, Map.class);
		data.remove("aprvrPicks");
		payload.put("data", data);
		payload.put("before", before);
		// 결재 문서 대상 이름 — 이미 있는 시스템은 지금 이름(수정으로 바꿀 이름은 상세에)
		String nm = before != null ? (String) before.get("sysNm") : v.getSysNm();
		AprvService.Result r = aprvService.open("SYS", v.getSysCd(), nm, null, null, userId, v.getAprvrPicks(), OM.writeValueAsString(payload));
		websocketService.sendUserNotis(r.notis);
		return r.message;
	}

	/** 최종 승인 때 대기 내용을 반영한다 (결재와 같은 트랜잭션 s). 수정·삭제 대상이 그사이 없어졌으면 거부 */
	@SuppressWarnings("unchecked")
	public void applyPayload(SqlSession s, Map<String, Object> payload) {
		SysInfoVo v = OM.convertValue(payload.get("data"), SysInfoVo.class);
		boolean exists = sysOf(v.getSysCd()) != null;
		if ((payload.get("before") != null || "DELETE".equals(payload.get("action"))) && !exists) throw new IllegalStateException("대상이 없습니다 (다른 경로로 삭제됨)");
		if ("DELETE".equals(payload.get("action"))) s.delete("sysinfo.deleteSysInfos", java.util.Collections.singletonList(v));
		else if (payload.get("before") == null) s.insert("sysinfo.insertSysInfo", v);
		else s.update("sysinfo.updateSysInfo", v);
	}

	@RequestMapping(value = "/getSysInfoList", method = RequestMethod.GET)
	public List<SysInfoVo> getSysInfoList() {
		return sqlSessionTemplate.selectList("sysinfo.selectSysInfoList");
	}

	/** 104번 WP2 — 시스템에 속한 데이터 모델 (TB_DATA_MODEL.DM_SYS_CD) */
	@RequestMapping(value = "/getModelsBySysCd", method = RequestMethod.GET)
	public List<java.util.Map<String, Object>> getModelsBySysCd(String sysCd) {
		return sqlSessionTemplate.selectList("sysinfo.selectModelsBySysCd", sysCd);
	}

	/**
	 * 권한이 있으면 null, 없으면 거부 응답 (resultCode 403).
	 * 데이터 소스 등록·수정·삭제·연결 테스트는 DS_ADMIN — 수정·테스트는 화면이 모르는 저장 비밀번호를
	 * 서버에서 이어 쓰므로 서버에서 막는다. 시스템 트리 등록·수정·삭제는 SYS_ADMIN (104번 WP2).
	 * 관리자는 모든 권한을 갖는다.
	 */
	private Response rejectWithout(PermCode code) {
		if (sessionService.hasPerm(code)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (" + code.label + " 권한 필요)");
		return r;
	}

	// 데이터소스
	@RequestMapping(value = "/createDataSource", method = RequestMethod.POST)
	public Mono<Response> createDataSource(@RequestBody DataSourceVo dataVo) {
		Response denied = rejectWithout(PermCode.DS_ADMIN);
		if (denied != null) return Mono.just(denied);
		dataVo.setId(StringUtils.getUUID());
		log.info(">> createDataSource : id={}, dsn={}, dbmsTp={}, svrAddr={}:{}, userId={}",
				dataVo.getId(), dataVo.getDsn(), dataVo.getDbmsTp(), dataVo.getSvrAddr(), dataVo.getPort(), dataVo.getUserId());

		Response result = new Response();

		try {
			dataVo.setPwd(securityUtils.encryptStr(dataVo.getPwd()));	// password를 암호화해서 저장한다.
			sqlSessionTemplate.insert("sysinfo.insertDataSource", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> createDataSource failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateDataSource", method = RequestMethod.POST)
	public Mono<Response> updateDataSource(@RequestBody DataSourceVo dataVo) {
		Response denied = rejectWithout(PermCode.DS_ADMIN);
		if (denied != null) return Mono.just(denied);
		log.info(">> updateDataSource : id={}, dsn={}, dbmsTp={}, svrAddr={}:{}, userId={}",
				dataVo.getId(), dataVo.getDsn(), dataVo.getDbmsTp(), dataVo.getSvrAddr(), dataVo.getPort(), dataVo.getUserId());
		
		Response result = new Response();

		try {
			// 목록·상세 응답에 비밀번호를 싣지 않으므로 수정 창은 비밀번호 칸이 빈 채로 열린다.
			// 비어 있으면 "바꾸지 않음" — 저장된 값을 그대로 둔다 (sysinfo.updateDataSource 의 PWD 조건).
			if (StringUtils.isNotEmpty(dataVo.getPwd())) {
				dataVo.setPwd(securityUtils.encryptStr(dataVo.getPwd()));	// password를 암호화해서 저장한다.
			} else {
				dataVo.setPwd(null);
			}
			sqlSessionTemplate.update("sysinfo.updateDataSource", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateDataSource failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteDataSources", method = RequestMethod.POST)
	public Mono<Response> deleteDataSources(@RequestBody List<DataSourceVo> dataVos) {
		Response denied = rejectWithout(PermCode.DS_ADMIN);
		if (denied != null) return Mono.just(denied);
		Response result = new Response();

		try {
			// 110 11.7-19 — 데이터 모델·시스템이 쓰는 데이터 소스가 하나라도 있으면 아무것도 지우지 않는다
			// (지우면 수집·진단·범정부 메타 생성이 데이터 소스를 못 찾는다). 구조 진단 이력·범정부 전송 이력은 보지 않는다.
			// 범정부 메타 DB 항목은 데이터 소스와 함께 지운다 (sysinfo.deleteDataSources).
			List<Map<String, Object>> refs = sqlSessionTemplate.selectList("sysinfo.selectDataSourceRefs", dataVos);
			if (!refs.isEmpty()) {
				List<String> lines = new ArrayList<>();
				for (Map<String, Object> ref : refs) {
					List<String> parts = new ArrayList<>();
					if (ref.get("models") != null) parts.add("데이터 모델 " + ref.get("models"));
					if (ref.get("systems") != null) parts.add("시스템 " + ref.get("systems"));
					lines.add(ref.get("dsn") + ": " + String.join(", ", parts));
				}
				result.setResultInfo(400, "사용 중인 데이터 소스는 삭제할 수 없습니다 — " + String.join(" / ", lines));
				return Mono.just(result);
			}
			sqlSessionTemplate.delete("sysinfo.deleteDataSources", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteDataSources failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/** 데이터 소스 목록. 비밀번호는 조회하지 않는다 (sysinfo.selectDataSourceList 에 PWD 없음). */
	@RequestMapping(value = "/getDataSourceList", method = RequestMethod.GET)
	public List<DataSourceExtVo> getDataSourceList() {
		return sqlSessionTemplate.selectList("sysinfo.selectDataSourceList");
	}

	@RequestMapping(value = "/getDataSourceListBySysCd", method = RequestMethod.GET)
	public List<DataSourceVo> getDataSourceListBySysCd(String sysCd) {
		List<DataSourceVo> dsVos = sqlSessionTemplate.selectList("sysinfo.selectDataSourceListBySysCd", sysCd);
		return dsVos;
	}

	/** 데이터 소스 단건. 비밀번호는 응답에 싣지 않는다 (수집·진단은 서버 안에서 sysinfo.selectDataSourceById 로 직접 읽는다). */
	@RequestMapping(value = "/getDataSourceById", method = RequestMethod.GET)
	public DataSourceVo getDataSourceById(String dsId) {
		DataSourceVo dsVo = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dsId);
		dsVo.setPwd(null);
		return dsVo;
	}

	@RequestMapping(value = "/testDataSource", method = RequestMethod.POST)
	public Mono<Response> testDataSource(@RequestBody DataSourceVo dataVo) {
		Response denied = rejectWithout(PermCode.DS_ADMIN);
		if (denied != null) return Mono.just(denied);
		Response result = new Response();
		DBConnection dbConn = null;

		try {
			log.info(">> testDataSourceById : id={}, dsn={}, dbmsTp={}, svrAddr={}:{}, userId={}",
					dataVo.getId(), dataVo.getDsn(), dataVo.getDbmsTp(), dataVo.getSvrAddr(), dataVo.getPort(), dataVo.getUserId());
			// 화면은 저장된 비밀번호를 모른다 (목록·상세 응답에서 뺐다). 행 테스트·수정 창 테스트처럼
			// 등록된 데이터 소스(id 있음)를 비밀번호 없이 보내면 저장된 비밀번호로 시험한다.
			// 저장된 비밀번호는 저장된 서버·포트·사용자에게만 보낸다 — 접속 대상을 바꿔 보내 비밀번호를
			// 다른 서버로 빼낼 수 없게. 대상을 바꿨으면 비밀번호를 다시 입력해야 한다.
			if (StringUtils.isEmpty(dataVo.getPwd()) && StringUtils.isNotEmpty(dataVo.getId())) {
				DataSourceVo stored = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dataVo.getId());
				if (!Objects.equals(stored.getSvrAddr(), dataVo.getSvrAddr()) || stored.getPort() != dataVo.getPort()
						|| !Objects.equals(stored.getUserId(), dataVo.getUserId())) {
					result.setResultInfo(RestResult.CODE_500.getCode(),
							"서버주소·포트·사용자를 바꿨으면 비밀번호를 다시 입력해야 연결 테스트를 할 수 있습니다.");
					return Mono.just(result);
				}
				dataVo.setPwd(securityUtils.decryptStr(stored.getPwd()));
			}
			// Oracle/Tibero Service Name: driverName 임시 변경
			if ("Service Name".equals(dataVo.getConnProps())) {
				String dbmsTp = dataVo.getDbmsTp();
				if ("Oracle".equals(dbmsTp) || "Tibero".equals(dbmsTp)) {
					dataVo.setDriverName(dbmsTp + "(Service Name)");
				}
			}
			dbConn = new DBConnection(dataVo, false);
			dbConn.connect();
			if (dataVo.getId() != null && !dataVo.getId().isEmpty()) {
				sqlSessionTemplate.update("sysinfo.updateDataSourceConnTest", dataVo.getId());
			}
			result.setResultInfo(RestResult.CODE_200);
			log.info("test datasource success - id={}, dsn={}", dataVo.getId(), dataVo.getDsn());
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), translateDbError(e.getMessage()));
			log.error("test datasource failed", e);
		} finally {
			if (dbConn != null) {
				try {
					dbConn.close();
				} catch (Exception se) {
				}
			}
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getDataSourceDrivers", method = RequestMethod.GET)
	public List<DriverVo> getDataSourceDrivers() {
		DriverLoader.load();
		return DriverLoader.getDrivers();
	}

	private String translateDbError(String msg) {
		if (msg == null) return "알 수 없는 오류가 발생했습니다.";
		if (msg.contains("ORA-12505")) return "접속 실패: SID를 찾을 수 없습니다. SID/Service Name 설정을 확인하세요.";
		if (msg.contains("ORA-12541")) return "접속 실패: 리스너가 응답하지 않습니다. 서버 주소와 포트를 확인하세요.";
		if (msg.contains("ORA-12514")) return "접속 실패: Service Name을 찾을 수 없습니다. 접속 방식과 데이터베이스명을 확인하세요.";
		if (msg.contains("ORA-01017")) return "접속 실패: 사용자명 또는 비밀번호가 올바르지 않습니다.";
		if (msg.contains("ORA-28000")) return "접속 실패: 계정이 잠겨 있습니다.";
		if (msg.contains("ORA-28001")) return "접속 실패: 비밀번호가 만료되었습니다.";
		if (msg.contains("Connection refused")) return "접속 실패: 서버에 연결할 수 없습니다. 주소, 포트, 방화벽을 확인하세요.";
		if (msg.contains("UnknownHost") || msg.contains("No such host")) return "접속 실패: 호스트를 찾을 수 없습니다. 서버 주소를 확인하세요.";
		if (msg.contains("Network is unreachable")) return "접속 실패: 네트워크에 연결할 수 없습니다.";
		if (msg.contains("Connection timed out") || msg.contains("connect timed out")) return "접속 실패: 연결 시간이 초과되었습니다. 서버 상태를 확인하세요.";
		if (msg.contains("password")) return "접속 실패: 인증에 실패했습니다. 사용자명과 비밀번호를 확인하세요.";
		if (msg.contains("FATAL") && msg.contains("does not exist")) return "접속 실패: 데이터베이스가 존재하지 않습니다. 데이터베이스명을 확인하세요.";
		if (msg.contains("Invalid number format for port")) return "접속 실패: 포트 번호가 올바르지 않습니다.";
		return "접속 실패: " + msg.substring(0, Math.min(200, msg.length()));
	}
}
