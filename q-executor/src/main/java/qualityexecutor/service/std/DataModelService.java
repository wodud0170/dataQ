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
import com.ndata.quality.model.std.StdDataModelVo;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.tool.DataSourceUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.stomp.StompSessionService;

/**
 * 데이터 모델 수집 서비스 (백그라운드 워커)
 *
 * <p>대상 DBMS에 접속하여 테이블/컬럼 메타데이터를 수집하고
 * TB_DATA_MODEL_CLCT, TB_DATA_MODEL_OBJ, TB_DATA_MODEL_ATTR에 저장한다.
 * WebSocket을 통해 수집 진행 상황을 실시간 전송한다.</p>
 */
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

	/**
	 * 데이터 모델 수집 실행
	 *
	 * <p>처리 흐름:</p>
	 * <ol>
	 *   <li>데이터소스 조회 및 DB 접속</li>
	 *   <li>수집 이력(TB_DATA_MODEL_CLCT) 생성</li>
	 *   <li>스키마 필터(TB_DATA_MODEL_SCHEMA) 적용 (없으면 기본 스키마)</li>
	 *   <li>테이블(OBJ) 메타 수집 → TB_DATA_MODEL_OBJ UPSERT (소프트 삭제 포함)</li>
	 *   <li>컬럼(ATTR) 메타 수집 → TB_DATA_MODEL_ATTR UPSERT (소프트 삭제 포함)</li>
	 *   <li>수집 완료 처리 + WebSocket 알림</li>
	 * </ol>
	 *
	 * @param userId 실행 사용자 ID
	 * @param ssId   WebSocket 세션 ID (진행 상황 전송용)
	 * @param dataVo 데이터모델 정보 (dataModelId, dataModelDsId 필수)
	 */
	public void collectDataModel(String userId, String ssId, StdDataModelVo dataVo) {
		log.info(">> websocket SSID={}, userId={}", ssId, userId);
		stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, ">> collectDataModel start");

		SqlSession session = sqlSessionFactory.openSession();

		StdDataModelCollectVo stdDataModelCollectVo = new StdDataModelCollectVo();
		StdDataModelAttrVo stdDataModelAttrVo = new StdDataModelAttrVo();
		StdDataModelObjVo stdDataModelObjVo = new StdDataModelObjVo();

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
			stdDataModelCollectVo.setClctType("DBMS");
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
			java.util.List<String> collectedObjNames = new java.util.ArrayList<>();
			java.util.List<String> collectedAttrNames = new java.util.ArrayList<>();
			for (String schemaNm : schemas) {
				NamedParamStatement pstmt = dbHandler.namedParamStatement(objQuery);
				pstmt.setString("owner", StringUtils.upperCase(schemaNm));
				ResultSet rs = dbHandler.executeSql(pstmt);
				while (rs.next()) {
					stdDataModelObjVo.setDataModelId(dataModelId);
					stdDataModelObjVo.setObjNm(rs.getString("objNm"));
					// 최초 수집: DB 코멘트를 논리명(OBJ_NM_KR)과 코멘트 원본(OBJ_COMMENT) 양쪽에 세팅
					String objDbComment = rs.getString("objNmKr");
					stdDataModelObjVo.setObjNmKr(objDbComment);
					stdDataModelObjVo.setObjComment(objDbComment);
					stdDataModelObjVo.setObjOwner(rs.getString("objOwner"));
					stdDataModelObjVo.setObjAttrCnt(rs.getShort("objAttrCnt"));
					stdDataModelObjVo.setObjCretDt(rs.getString("objCretDt"));
					stdDataModelObjVo.setObjUpdtDt(rs.getString("objUpdtDt"));
					session.insert("datamodel.insertDataModelObj", stdDataModelObjVo);
					collectedObjNames.add(stdDataModelObjVo.getObjNm());
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
					stdDataModelAttrVo.setObjOwner(schemaNm);
					stdDataModelAttrVo.setObjNm(rs.getString("objNm"));
					stdDataModelAttrVo.setAttrNm(rs.getString("attrNm"));
					// 최초 수집: DB 코멘트를 논리명(ATTR_NM_KR)과 코멘트 원본(ATTR_COMMENT) 양쪽에 세팅
					String attrDbComment = rs.getString("attrNmKr");
					stdDataModelAttrVo.setAttrNmKr(attrDbComment);
					stdDataModelAttrVo.setAttrComment(attrDbComment);
					stdDataModelAttrVo.setDataType(rs.getString("dataType"));
					stdDataModelAttrVo.setDataLen(rs.getLong("dataLen"));
					stdDataModelAttrVo.setDataDecimalLen(rs.getShort("dataDecimalLen"));
					stdDataModelAttrVo.setNullableYn(rs.getString("nullableYn"));
					stdDataModelAttrVo.setPkYn(rs.getString("pkYn"));
					stdDataModelAttrVo.setFkYn(rs.getString("fkYn"));
					stdDataModelAttrVo.setDefaultVal(rs.getString("defaultVal"));
					stdDataModelAttrVo.setAttrOrder(rs.getShort("attrOrder"));
					// 86번 #11 — 수집 단계 표준여부 검사. 진단(Diag) 에서도 다시 하지만, 수집 직후 화면에 표준 여부가 보이도록 일치화.
					// 표준여부 체크 - 용어,도메인
					String attrNmUp = stdDataModelAttrVo.getAttrNm() == null ? "" : stdDataModelAttrVo.getAttrNm().toUpperCase();
					StdTermsVo stdTermsVo = session.selectOne("terms.selectTermsByEngNm", attrNmUp);
					if (stdTermsVo != null) {
						stdDataModelAttrVo.setTermsStndYn("Y");
						stdDataModelAttrVo.setDomainStndYn(isDomainStnd(stdTermsVo, stdDataModelAttrVo) ? "Y" : "N");
					} else {
						stdDataModelAttrVo.setTermsStndYn("N");
						stdDataModelAttrVo.setDomainStndYn("N");
					}
					// 표준여부 체크 - 단어 (영문 약어 기준 매칭, 단어/표준여부 배열 보존)
					List<String> wordLst = new ArrayList<String>();
					List<String> wordStndLst = new ArrayList<String>();
					if (!attrNmUp.isEmpty()) {
						for (String word : attrNmUp.split("_")) {
							if (word.isEmpty()) continue;
							StdWordVo stdWordVo = session.selectOne("word.selectWordByEngAbrvNm", word);
							if (stdWordVo != null) {
								wordLst.add(word + "(" + stdWordVo.getWordNm() + ")");
								wordStndLst.add("Y");
							} else {
								wordLst.add(word);
								wordStndLst.add("N");
							}
						}
					}
					stdDataModelAttrVo.setWordLst(wordLst.toArray(new String[0]));
					stdDataModelAttrVo.setWordStndLst(wordStndLst.toArray(new String[0]));
					session.insert("datamodel.insertDataModelAttr", stdDataModelAttrVo);
					collectedAttrNames.add(stdDataModelAttrVo.getObjNm() + "." + stdDataModelAttrVo.getAttrNm());
					totalAttrCnt++;
				}
				pstmt.close();
				rs.close();
			}
			session.commit();
			stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "컬럼 정보 수집 완료 (" + totalAttrCnt + "개)");
			// 86번 #11 — 소프트 삭제 비활성화. 수집은 MERGE 만 수행 (물리명 일치 시 덮어쓰기).
			// 기존 모델 행은 유지. 삭제는 사용자가 명시적으로 수행해야 함.
			// (예전엔 OWNER 누락된 softDeleteMissingObjs/Attrs 가 다른 스키마 행을 통째 soft-delete 시키는 버그.)
			String nowDt = com.ndata.module.DateUtils.dateToStr(new Date(), "yyyyMMddHHmmss");
			// 3-1. INDEX (인덱스) 수집 — UPSERT + soft-delete
			int totalIndexCnt = 0;
			try {
				String indexQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetIndexes");
				if (indexQuery != null) {
					stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "인덱스 정보 수집 중...");
					java.util.List<String> collectedIndexKeys = new java.util.ArrayList<>();
					for (String schemaNm : schemas) {
						NamedParamStatement pstmt = dbHandler.namedParamStatement(indexQuery);
						pstmt.setString("owner", StringUtils.upperCase(schemaNm));
						ResultSet rs = dbHandler.executeSql(pstmt);
						while (rs.next()) {
							java.util.Map<String, Object> param = new java.util.HashMap<>();
							param.put("dataModelId", dataModelId);
							param.put("objOwner", schemaNm);
							param.put("tableNm", rs.getString("tableNm"));
							param.put("indexNm", rs.getString("indexNm"));
							param.put("indexType", rs.getString("indexType"));
							param.put("uniqueness", rs.getString("uniqueness"));
							param.put("columnNm", rs.getString("columnNm"));
							param.put("columnPos", rs.getInt("columnPos"));
							param.put("sortOrder", rs.getString("sortOrder"));
							param.put("tablespaceNm", rs.getString("tablespaceNm"));
							session.insert("datamodel.insertDataModelIndex", param);
							collectedIndexKeys.add(schemaNm + "|" + rs.getString("tableNm") + "|" + rs.getString("indexNm") + "|" + rs.getInt("columnPos"));
							totalIndexCnt++;
						}
						pstmt.close();
						rs.close();
					}
					// 86번 #11 — soft-delete 제거 (수집은 MERGE 전용)
					session.commit();
					stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "인덱스 정보 수집 완료 (" + totalIndexCnt + "개)");
				}
			} catch (Exception e) {
				log.warn(">> index collect skipped: {}", e.getMessage());
			}
			// 3-2. CONSTRAINT (제약조건) 수집 — UPSERT + soft-delete
			int totalConstraintCnt = 0;
			try {
				String constraintQuery = dataSourceUtils.getQueryString(dataSource.getDbmsTp() + "GetConstraints");
				if (constraintQuery != null) {
					stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "제약조건 정보 수집 중...");
					java.util.List<String> collectedConstraintKeys = new java.util.ArrayList<>();
					for (String schemaNm : schemas) {
						NamedParamStatement pstmt = dbHandler.namedParamStatement(constraintQuery);
						pstmt.setString("owner", StringUtils.upperCase(schemaNm));
						ResultSet rs = dbHandler.executeSql(pstmt);
						while (rs.next()) {
							java.util.Map<String, Object> param = new java.util.HashMap<>();
							param.put("dataModelId", dataModelId);
							param.put("objOwner", schemaNm);
							param.put("tableNm", rs.getString("tableNm"));
							param.put("constraintNm", rs.getString("constraintNm"));
							param.put("constraintType", rs.getString("constraintType"));
							param.put("columnNm", rs.getString("columnNm"));
							param.put("columnPos", rs.getInt("columnPos"));
							param.put("refOwner", rs.getString("refOwner"));
							param.put("refTableNm", rs.getString("refTableNm"));
							param.put("refColumnNm", rs.getString("refColumnNm"));
							param.put("deleteRule", rs.getString("deleteRule"));
							param.put("status", rs.getString("status"));
							try { param.put("searchCondition", rs.getString("searchCondition")); } catch (Exception ignore) {}
							session.insert("datamodel.insertDataModelConstraint", param);
							collectedConstraintKeys.add(schemaNm + "|" + rs.getString("tableNm") + "|" + rs.getString("constraintNm") + "|" + rs.getInt("columnPos"));
							totalConstraintCnt++;
						}
						pstmt.close();
						rs.close();
					}
					// 86번 #11 — soft-delete 제거 (수집은 MERGE 전용)
					session.commit();
					stompSessionService.sendMessage(ssId, WsNoticeLevel.INFO, "제약조건 정보 수집 완료 (" + totalConstraintCnt + "개)");
				}
			} catch (Exception e) {
				log.warn(">> constraint collect skipped: {}", e.getMessage());
			}
			// 4. 데이터 모델 수집 완료 처리
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
