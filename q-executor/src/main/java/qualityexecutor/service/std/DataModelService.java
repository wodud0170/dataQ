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
			// DB Handler 생성
			dbHandler = dataSourceUtils.getDBHandler(dataSource);
			//log.info(">> get dbHandler={}", dbHandler);
			// 1. 수집테이블(TB_DATA_MODEL_CLCT) 입력
			String clctId = StringUtils.getUUID();
			String dataModelId = dataVo.getDataModelId();
			stdDataModelCollectVo.setClctId(clctId);
			stdDataModelCollectVo.setDataModelId(dataModelId);
			stdDataModelCollectVo.setCretUserId(userId);
			session.insert("datamodel.updateDataModelCollect", stdDataModelCollectVo);
			// 2. OBJ 모델 수집
			String query = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetObjs");//DBMS타입별 쿼리 조회
			//log.info(">> get dm-objs query={}", query);
			// OBJ 모델을 데이터베이스에 저장
			NamedParamStatement pstmt = dbHandler.namedParamStatement(query);
			pstmt.setString("owner", StringUtils.upperCase(dbHandler.getSchema()));
			ResultSet rs = dbHandler.executeSql(pstmt);
			stdDataModelObjVo.setClctId(clctId);
			while (rs.next()) {
				stdDataModelObjVo.setDataModelId(dataModelId);
				stdDataModelObjVo.setObjNm(rs.getString("objNm"));
				stdDataModelObjVo.setObjNmKr(rs.getString("objNmKr"));
				stdDataModelObjVo.setObjOwner(rs.getString("objOwner"));
				stdDataModelObjVo.setObjAttrCnt(rs.getShort("objAttrCnt"));
				stdDataModelObjVo.setObjCretDt(rs.getString("objCretDt"));
				stdDataModelObjVo.setObjUpdtDt(rs.getString("objUpdtDt"));
				session.insert("datamodel.insertDataModelObj", stdDataModelObjVo);
			}
			pstmt.close();
			rs.close();
			session.commit();
			// 3. ATTR 모델 수집
			query = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetAttrs");//DBMS타입별 쿼리 조회
			//log.info(">> get dm-attrs query={}", query);
			// ATTR 모델을 데이터베이스에 저장
			pstmt = dbHandler.namedParamStatement(query);
			pstmt.setString("owner", StringUtils.upperCase(dbHandler.getSchema()));
			rs = dbHandler.executeSql(pstmt);
			stdDataModelAttrVo.setClctId(clctId);
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
				String[] words = stdDataModelAttrVo.getAttrNm().toUpperCase().split("_");	//단어로 분리
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
			}
			pstmt.close();
			rs.close();
			session.commit();
			// 4. 데이터 모델 통계 저장
			stdDataModelStatsVo.setClctId(clctId);
			stdDataModelStatsVo.setDataModelId(dataModelId);
			session.insert("datamodel.insertDataModelStats", stdDataModelStatsVo);
			session.commit();
			// 5. 데이터 모델 수집 완료 처리
			stdDataModelCollectVo.setClctEndDt(DateUtils.dateToStr(new Date(), "yyyyMMddHHmmss"));
			stdDataModelCollectVo.setClctCmptnYn("Y");
			session.insert("datamodel.updateDataModelCollect", stdDataModelCollectVo);
			session.commit();
		} catch (Exception e) {
			// TODO Auto-generated catch block
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
