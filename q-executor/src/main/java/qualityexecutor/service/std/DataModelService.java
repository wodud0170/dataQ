package qualityexecutor.service.std;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.datasource.dbms.handler.DBHandler;
import com.ndata.datasource.dbms.ext.NamedParamStatement;
import com.ndata.model.DataSourceVo;
import com.ndata.model.ws.WsDestType;
import com.ndata.model.ws.WsNoticeLevel;
import com.ndata.module.DateUtils;
import com.ndata.module.StringUtils;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelCollectVo;
import com.ndata.quality.model.std.StdDataModelObjVo;
import com.ndata.quality.model.std.StdDataModelSchemaVo;
import com.ndata.quality.model.std.StdDataModelStatsVo;
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.stomp.StompSessionService;

@Slf4j
@NoArgsConstructor
@Service
public class DataModelService implements Runnable {
	public static final String JOB_TYPE_COLLECT_DATA_MODEL = "collectDataModel";

	private String jobType;
	private String userId;
	private String ssId;
	private StdDataModelVo dataVo;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private DataSourceUtils dataSourceUtils;

	@Autowired
	private StompSessionService stompSessionService;

	public DataModelService(String jobType, String userId, String ssId, StdDataModelVo dataVo) {
		this.jobType = jobType;
		this.userId = userId;
		this.ssId = ssId;
		this.dataVo = dataVo;
	}

	@Override
	public void run() {
		switch(jobType) {
			case JOB_TYPE_COLLECT_DATA_MODEL:
				collectDataModel(userId, ssId, dataVo);
				break;
		}
	}

	// 데이터모델 수집
	public void collectDataModel(String userId, String ssId, StdDataModelVo dataVo) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> collectDataModel start");

		SqlSession session = sqlSessionFactory.openSession();

		StdDataModelCollectVo stdDataModelCollectVo = new StdDataModelCollectVo();
		StdDataModelAttrVo stdDataModelAttrVo = new StdDataModelAttrVo();
		StdDataModelObjVo stdDataModelObjVo = new StdDataModelObjVo();
		StdDataModelStatsVo stdDataModelStatsVo = new StdDataModelStatsVo();

		DBHandler dbHandler = null;

		try {
			// 데이터소스 조회
			DataSourceVo dataSource = sqlSessionTemplate.selectOne("sysinfo.selectDataSourceById", dataVo.getDataModelDsId());
			log.info(">> get dataSource={}", dataSource);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "데이터소스 연결 중...");
			// DB Handler 생성
			dbHandler = dataSourceUtils.getDBHandler(dataSource);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "데이터소스 연결 완료");
			// 1. 수집테이블(TB_DATA_MODEL_CLCT) 입력
			String clctId = StringUtils.getUUID();
			String dataModelId = dataVo.getDataModelId();
			stdDataModelCollectVo.setClctId(clctId);
			stdDataModelCollectVo.setDataModelId(dataModelId);
			stdDataModelCollectVo.setCretUserId(userId);
			session.insert("datamodel.updateDataModelCollect", stdDataModelCollectVo);
			// 수집 대상 스키마 목록 조회 (TB_DATA_MODEL_SCHEMA 필터 사용, 없으면 기본 스키마)
			List<StdDataModelSchemaVo> schemaFilter = sqlSessionTemplate.selectList("datamodel.selectDataModelSchemaList", dataModelId);
			List<String> schemas = new ArrayList<String>();
			for (StdDataModelSchemaVo sf : schemaFilter) {
				if ("Y".equals(sf.getUseYn())) {
					schemas.add(sf.getSchemaNm());
				}
			}
			if (schemas.isEmpty()) {
				schemas.add(dbHandler.getSchema());
			}
			log.info(">> collect schemas={}", schemas);
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "수집 대상 스키마: " + String.join(", ", schemas));
			// 2. OBJ 모델 수집
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "테이블 목록 수집 중...");
			String objQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetObjs");
			stdDataModelObjVo.setClctId(clctId);
			int totalObjCnt = 0;
			for (String schemaNm : schemas) {
				NamedParamStatement pstmt = dbHandler.namedParamStatement(objQuery);
				pstmt.setString("owner", StringUtils.upperCase(schemaNm));
				ResultSet rs = dbHandler.executeSql(pstmt);
				while (rs.next()) {
					stdDataModelObjVo.setDataModelId(dataModelId);
					stdDataModelObjVo.setObjNm(rs.getString("objNm"));
					stdDataModelObjVo.setObjNmKr(rs.getString("objNmKr"));
					stdDataModelObjVo.setObjOwner(rs.getString("objOwner"));
					stdDataModelObjVo.setObjAttrCnt(rs.getShort("objAttrCnt"));
					stdDataModelObjVo.setObjCretDt(rs.getString("objCretDt"));
					stdDataModelObjVo.setObjUpdtDt(rs.getString("objUpdtDt"));
					session.insert("datamodel.insertDataModelObj", stdDataModelObjVo);
					totalObjCnt++;
				}
				pstmt.close();
				rs.close();
			}
			session.commit();
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "테이블 목록 수집 완료 (" + totalObjCnt + "개)");
			// 3. ATTR 모델 수집
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "컬럼 정보 수집 중...");
			String attrQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetAttrs");
			stdDataModelAttrVo.setClctId(clctId);
			int totalAttrCnt = 0;
			for (String schemaNm : schemas) {
				NamedParamStatement pstmt = dbHandler.namedParamStatement(attrQuery);
				pstmt.setString("owner", StringUtils.upperCase(schemaNm));
				ResultSet rs = dbHandler.executeSql(pstmt);
				while (rs.next()) {
					stdDataModelAttrVo.setDataModelId(dataModelId);
					stdDataModelAttrVo.setObjNm(rs.getString("objNm"));
					stdDataModelAttrVo.setAttrNm(rs.getString("attrNm"));
					stdDataModelAttrVo.setAttrNmKr(rs.getString("attrNmKr"));
					stdDataModelAttrVo.setDataType(rs.getString("dataType"));
					stdDataModelAttrVo.setDataLen(rs.getLong("dataLen"));
					stdDataModelAttrVo.setDataDecimalLen(rs.getShort("dataDecimalLen"));
					stdDataModelAttrVo.setNullableYn(rs.getString("nullableYn"));
					stdDataModelAttrVo.setPkYn(rs.getString("pkYn"));
					stdDataModelAttrVo.setFkYn(rs.getString("fkYn"));
					stdDataModelAttrVo.setDefaultVal(rs.getString("defaultVal"));
					stdDataModelAttrVo.setAttrOrder(rs.getShort("attrOrder"));
					//표준여부 체크 - 용어,도메인
					StdTermsVo stdTermsVo = session.selectOne("terms.selectTermsByEngNm", stdDataModelAttrVo.getAttrNm().toUpperCase());
					if (stdTermsVo != null) {
						stdDataModelAttrVo.setTermsStndYn("Y");
						stdDataModelAttrVo.setDomainStndYn(isDomainStnd(stdTermsVo, stdDataModelAttrVo) ? "Y" : "N");
					} else {
						stdDataModelAttrVo.setTermsStndYn("N");
						stdDataModelAttrVo.setDomainStndYn("N");
					}
					//표준여부 체크 - 단어
					String[] words = stdDataModelAttrVo.getAttrNm().toUpperCase().split("_");
					List<String> wordLst = new ArrayList<String>();
					List<String> wordStndLst = new ArrayList<String>();
					for (String word : words) {
						StdWordVo stdWordVo = session.selectOne("word.selectWordByEngAbrvNm", word);
						if (stdWordVo != null) {
							wordLst.add(word + "(" + stdWordVo.getWordNm() + ")");
							wordStndLst.add("Y");
						} else {
							wordLst.add(word);
							wordStndLst.add("N");
						}
					}
					stdDataModelAttrVo.setWordLst(wordLst.toArray(new String[0]));
					stdDataModelAttrVo.setWordStndLst(wordStndLst.toArray(new String[0]));
					session.insert("datamodel.insertDataModelAttr", stdDataModelAttrVo);
					totalAttrCnt++;
				}
				pstmt.close();
				rs.close();
			}
			session.commit();
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "컬럼 정보 수집 완료 (" + totalAttrCnt + "개)");
			// 4. 데이터 모델 통계 저장
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "통계 처리 중...");
			stdDataModelStatsVo.setClctId(clctId);
			stdDataModelStatsVo.setDataModelId(dataModelId);
			session.insert("datamodel.insertDataModelStats", stdDataModelStatsVo);
			session.commit();
			// 5. 데이터 모델 수집 완료 처리
			stdDataModelCollectVo.setClctEndDt(DateUtils.dateToStr(new Date(), "yyyyMMddHHmmss"));
			stdDataModelCollectVo.setClctCmptnYn("Y");
			session.insert("datamodel.updateDataModelCollect", stdDataModelCollectVo);
			session.commit();
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "수집 완료!");
		} catch (Exception e) {
			session.rollback();
			log.error("collect data model : {}", e.getMessage());
			stompSessionService.sendNotice(WsNoticeLevel.ERROR, "데이터모델 수집에 실패하였습니다. : model=[" + dataVo.getDataModelNm() + "],error=" + e.getMessage());
		} finally {
			session.close();
			if (dbHandler != null) {
				try {
					dbHandler.close();
				} catch (Exception e) {
				}
			}
		}

		stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> collectDataModel finished");
		stompSessionService.sendReload(WsDestType.QUALITY_STANDARD, "DATA_MODEL_ATTR_RELOAD");
		stompSessionService.sendNotice(WsNoticeLevel.INFO, "데이터모델이 수집되었습니다.");
	}

	private boolean isDomainStnd(StdTermsVo stdTermsVo, StdDataModelAttrVo stdDataModelAttrVo) {
		if (stdTermsVo.getDataType().equals("NUMERIC")) {
			if (stdDataModelAttrVo.getDataType().equals(stdTermsVo.getDataType()) && 
				stdDataModelAttrVo.getDataLen() == stdTermsVo.getDataLen() && 
				stdDataModelAttrVo.getDataDecimalLen() == stdTermsVo.getDataDecimalLen()) {
				return true;
			} else {
				return false;
			}
		} else if (stdTermsVo.getDataType().equals("VARCHAR") || stdTermsVo.getDataType().equals("CHAR")) {
			if (stdDataModelAttrVo.getDataType().equals(stdTermsVo.getDataType()) && 
				stdDataModelAttrVo.getDataLen() == stdTermsVo.getDataLen()) {
				return true;
			} else {
				return false;
			}
		} else {
			if (stdDataModelAttrVo.getDataType().equals(stdTermsVo.getDataType())) {
				return true;
			} else {
				return false;
			}
		} 
	}
}
