package com.ndata.quality.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.ndata.module.StringUtils;

import lombok.extern.slf4j.Slf4j;

import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdDomainVo;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.model.std.UploadResult;
import com.ndata.quality.tool.ExcelSheetHandler;

/**
 * Excel 일괄 업로드 서비스
 *
 * <p>단어/용어/코드/코드데이터/도메인의 Excel 파일을 파싱하여 DB에 일괄 등록한다.
 * SAX 방식(XSSFReader)으로 메모리 효율적으로 대용량 Excel을 처리한다.
 * 각 행 단위로 트랜잭션 처리하여, 일부 행 실패 시에도 나머지는 정상 등록된다.</p>
 */
@Service
@Slf4j
public class ExcelUploadService {
	
	public final String SPLIT_DELEMETER = ",";
	
	@Autowired
	private SqlSessionFactory sqlSessionFactory;	// transaction 사용할 경우 사용
	
	/**
	 * 단어 Excel 일괄 등록
	 *
	 * <p>각 행을 파싱하여 단어를 등록한다. 이미 존재하면 Skip, 금칙어이면 Fail.</p>
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과 (성공/건너뜀/실패 건수 + 실패 상세)
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadWords(String userId, MultipartFile multiPart) throws Exception {
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdWordVo stdWordVo = new StdWordVo();
				try {
					// 단어가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("word.selectWordByEngAbrvNm", dataRow.get(3)) != null) {
						result.addSkip();
						continue;
					}
					stdWordVo.setId(StringUtils.getUUID());
					stdWordVo.setWordNm(dataRow.get(2));

					// 영문약어에 언더바(_) 체크
					if (dataRow.get(3) != null && dataRow.get(3).contains("_")) {
						throw new Exception("단어 영문약어에 언더바(_)는 사용할 수 없습니다.");
					}
					// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
					Map<String, Object> forbiddenWord = session.selectOne("word.selectWordByForbiddenNm", dataRow.get(2));
					if (forbiddenWord != null) {
						String stdWordNm = (String) forbiddenWord.get("wordNm");
						throw new Exception("'" + dataRow.get(2) + "'은(는) '" + stdWordNm + "'의 금칙어입니다. '" + stdWordNm + "'를 사용해주세요.");
					}

					stdWordVo.setWordEngAbrvNm(dataRow.get(3));
					stdWordVo.setWordEngNm(dataRow.get(4));
					stdWordVo.setWordDesc(dataRow.get(5));
					stdWordVo.setWordClsfYn(dataRow.get(6));
					stdWordVo.setDomainClsfNm(checkInvalidVal(dataRow.get(7)));
					stdWordVo.setAllophSynmLst(splitArrayString(dataRow.get(8)));
					stdWordVo.setForbdnWordLst(splitArrayString(dataRow.get(9)));
					String reqSysNm = checkInvalidVal(dataRow.get(10));
					if (reqSysNm != null) {
						stdWordVo.setReqSysCd(session.selectOne("sysinfo.selectSysInfoBySysNm", reqSysNm));
					}
					if (dataRow.size() > 11) {
						stdWordVo.setCommStndYn(dataRow.get(11));
					}
					stdWordVo.setMagntdOrd(dataRow.get(1));
					stdWordVo.setCretUserId(userId);
					stdWordVo.setUpdtUserId(userId);
					stdWordVo.setAprvYn("Y");
					session.insert("word.insertWord", stdWordVo);
					session.commit();
					result.addSuccess();
				} catch (Exception e) {
					session.rollback();
					String detail = String.format("단어(%s): %s", stdWordVo.getWordNm() != null ? stdWordVo.getWordNm() : dataRow.get(2), e.getMessage());
					log.error("upload words row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		// 일괄 등록 이력 저장
		saveUploadHistory(userId, "WORD", "단어", result);
		return result;
	}

	/**
	 * 용어 Excel 일괄 등록
	 *
	 * <p>각 행을 파싱하여 용어 + 구성단어 관계를 등록한다.
	 * 도메인 유효성 검증, 구성단어 존재/승인 여부 체크를 수행한다.</p>
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadTermsList(String userId, MultipartFile multiPart) throws Exception {
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdTermsVo stdTermsVo = new StdTermsVo();
				try {
					// 용어가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("terms.selectTermsByEngNm", dataRow.get(4)) != null) {
						result.addSkip();
						continue;
					}
					stdTermsVo.setId(StringUtils.getUUID());
					stdTermsVo.setTermsNm(dataRow.get(2));
					stdTermsVo.setTermsDesc(dataRow.get(3));
					stdTermsVo.setTermsEngAbrvNm(dataRow.get(4));
					stdTermsVo.setDomainNm(checkInvalidVal(dataRow.get(5)));
					stdTermsVo.setCodeGrp(checkInvalidVal(dataRow.get(9)));
					stdTermsVo.setChrgOrg(checkInvalidVal(dataRow.get(10)));
					stdTermsVo.setAllophSynmLst(splitArrayString(dataRow.get(11)));
					String reqSysNm = checkInvalidVal(dataRow.get(12));
					if (reqSysNm != null) {
						stdTermsVo.setReqSysCd(session.selectOne("sysinfo.selectSysInfoBySysNm", reqSysNm));
					}
					if (dataRow.size() > 13) {
						stdTermsVo.setCommStndYn(dataRow.get(13));
					}
					stdTermsVo.setMagntdOrd(dataRow.get(1));
					stdTermsVo.setCretUserId(userId);
					stdTermsVo.setUpdtUserId(userId);
					stdTermsVo.setAprvYn("Y");

					// 도메인 유효성 검증
					if (stdTermsVo.getDomainNm() != null) {
						Object domainCheck = session.selectOne("domain.selectDomainInfoByNm", stdTermsVo.getDomainNm());
						if (domainCheck == null) {
							throw new Exception(String.format("도메인(%s)이 유효하지 않음", stdTermsVo.getDomainNm()));
						}
					}

					// 구성단어 유효성 검증 (insert 전에 먼저 확인)
					String[] wordEngAbrvNms = stdTermsVo.getTermsEngAbrvNm().split("_");
					List<String> invalidWords = new ArrayList<>();
					List<String> unapprovedWords = new ArrayList<>();
					for (String wordEngAbrvNm : wordEngAbrvNms) {
						StdWordVo wordVo = session.selectOne("word.selectWordByEngAbrvNm", wordEngAbrvNm);
						if (wordVo == null) {
							invalidWords.add(wordEngAbrvNm);
						} else if (!"Y".equals(wordVo.getAprvYn())) {
							unapprovedWords.add(wordEngAbrvNm);
						}
					}
					if (!invalidWords.isEmpty()) {
						throw new Exception(String.format("단어(%s)가 유효하지 않음", String.join(", ", invalidWords)));
					}
					if (!unapprovedWords.isEmpty()) {
						throw new Exception(String.format("단어 미승인: %s", String.join(", ", unapprovedWords)));
					}

					session.insert("terms.insertTerms", stdTermsVo);

					List<StdTermsVo.Word> wordList = new ArrayList<StdTermsVo.Word>();
					short loop = 0;
					for (String wordEngAbrvNm : wordEngAbrvNms) {
						StdWordVo wordVo = session.selectOne("word.selectWordByEngAbrvNm", wordEngAbrvNm);
						StdTermsVo.Word word = new StdTermsVo.Word();
						word.setTermsId(stdTermsVo.getId());
						word.setWordId(wordVo.getId());
						word.setWordNm(wordVo.getWordNm());
						word.setWordOrd(loop);
						wordList.add(word);
						loop++;
					}
					session.insert("terms.insertTermsWords", wordList);
					session.commit();
					result.addSuccess();
				} catch (Exception e) {
					session.rollback();
					String detail = String.format("용어(%s): %s", stdTermsVo.getTermsNm() != null ? stdTermsVo.getTermsNm() : dataRow.get(2), e.getMessage());
					log.error("upload terms row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		// 일괄 등록 이력 저장
		saveUploadHistory(userId, "TERM", "용어", result);
		return result;
	}

	/**
	 * 코드 Excel 일괄 등록 - 내부적으로 uploadTermsList를 호출
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadCodeInfoList(String userId, MultipartFile multiPart) throws Exception {
		UploadResult result = uploadTermsList(userId, multiPart);
		// 코드 일괄 등록 이력 (uploadTermsList에서 TERM으로 저장되므로 CODE로도 기록)
		saveUploadHistory(userId, "CODE", "코드", result);
		return result;
	}

	/**
	 * 코드데이터(항목값) Excel 일괄 등록
	 *
	 * <p>각 행의 코드그룹/코드명/코드값을 파싱하여 등록한다. 이미 존재하면 Skip.</p>
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadCodeDataList(String userId, MultipartFile multiPart) throws Exception {
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdCodeDataVo stdCodeDataVo = new StdCodeDataVo();
				try {
					stdCodeDataVo.setId(StringUtils.getUUID());
					stdCodeDataVo.setCodeGrp(dataRow.get(1));
					stdCodeDataVo.setCodeNm(dataRow.get(2));
					stdCodeDataVo.setCodeEngNm(dataRow.get(3));
					stdCodeDataVo.setCodeVal(dataRow.get(4));
					// 코드데이터가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("codedata.selectCodeDataByNmAndVal", stdCodeDataVo) != null) {
						result.addSkip();
						continue;
					}
					stdCodeDataVo.setCodeValDesc(dataRow.get(5));
					session.insert("codedata.insertCodeData", stdCodeDataVo);
					session.commit();
					result.addSuccess();
				} catch (Exception e) {
					session.rollback();
					String detail = String.format("코드데이터(%s/%s): %s", stdCodeDataVo.getCodeNm(), stdCodeDataVo.getCodeVal(), e.getMessage());
					log.error("upload code datas row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		// 일괄 등록 이력 저장
		saveUploadHistory(userId, "CODE_DATA", "코드데이터", result);
		return result;
	}

	/**
	 * 도메인 Excel 일괄 등록
	 *
	 * <p>각 행의 도메인그룹/분류/도메인명/타입/길이 등을 파싱하여 등록한다.
	 * 이미 존재하면 Skip.</p>
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadDomains(String userId, MultipartFile multiPart) throws Exception {
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdDomainVo stdDomainVo = new StdDomainVo();
				try {
					// 도메인이 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("domain.selectDomainInfoByNm", dataRow.get(4)) != null) {
						result.addSkip();
						continue;
					}
					stdDomainVo.setId(StringUtils.getUUID());
					stdDomainVo.setDomainGrpNm(dataRow.get(2));
					stdDomainVo.setDomainClsfNm(dataRow.get(3));
					stdDomainVo.setDomainNm(dataRow.get(4));
					stdDomainVo.setDomainDesc(dataRow.get(5));
					stdDomainVo.setDataType(dataRow.get(6));
					if (checkInvalidVal(dataRow.get(7)) != null) {
						stdDomainVo.setDataLen(Short.parseShort(dataRow.get(7)));
					}
					if (checkInvalidVal(dataRow.get(8)) != null) {
						stdDomainVo.setDataDecimalLen(Short.parseShort(dataRow.get(8)));
					}
					if (!dataRow.get(9).contains("CHAR")) {
						stdDomainVo.setStorFmt(dataRow.get(9));
					}
					stdDomainVo.setExprFmtLst(splitArrayString(dataRow.get(10),"\\n"));
					stdDomainVo.setAllowValLst(splitArrayString(dataRow.get(12),"\\n"));
					stdDomainVo.setDataUnit(dataRow.get(11));
					String reqSysNm = checkInvalidVal(dataRow.get(13));
					if (reqSysNm != null) {
						stdDomainVo.setReqSysCd(session.selectOne("sysinfo.selectSysInfoBySysNm", reqSysNm));
					}
					if (dataRow.size() > 14) {
						stdDomainVo.setCommStndYn(dataRow.get(14));
					}
					stdDomainVo.setMagntdOrd(dataRow.get(1));
					stdDomainVo.setCretUserId(userId);
					stdDomainVo.setUpdtUserId(userId);
					stdDomainVo.setAprvYn("Y");
					session.insert("domain.insertDomain", stdDomainVo);
					session.commit();
					result.addSuccess();
				} catch (Exception e) {
					session.rollback();
					String detail = String.format("도메인(%s): %s", stdDomainVo.getDomainNm() != null ? stdDomainVo.getDomainNm() : dataRow.get(4), e.getMessage());
					log.error("upload domains row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		// 일괄 등록 이력 저장
		saveUploadHistory(userId, "DOMAIN", "도메인", result);
		return result;
	}

	// 일괄 등록 이력 저장 헬퍼
	private void saveUploadHistory(String userId, String targetType, String targetLabel, UploadResult result) {
		try {
			SqlSession histSession = sqlSessionFactory.openSession();
			try {
				Map<String, Object> history = new HashMap<>();
				history.put("changeId", StringUtils.getUUID());
				history.put("changeType", "BULK_INSERT");
				history.put("targetType", targetType);
				history.put("changeCnt", result.getSuccessCount());
				history.put("summary", String.format("%s 일괄등록 (성공:%d, 건너뜀:%d, 실패:%d)",
						targetLabel, result.getSuccessCount(), result.getSkipCount(), result.getFailCount()));
				history.put("changeUserId", userId);
				histSession.insert("changehistory.insertChangeHistory", history);
				histSession.commit();
			} finally {
				histSession.close();
			}
		} catch (Exception e) {
			log.warn("일괄등록 이력 저장 실패: {}", e.getMessage());
		}
	}

	public ExcelSheetHandler readExcel(MultipartFile multiPart) throws Exception {
	    //SheetcontentHandler를 재정의 해서 만든 Class
		ExcelSheetHandler sheetHandler = new ExcelSheetHandler();

	    try {
	        OPCPackage opc = OPCPackage.open(multiPart.getInputStream());

	        //메모리를 적게 사용하며 sax 형식을 사용할 수 있게 함
	        XSSFReader xssfReader = new XSSFReader(opc);

	        //파일의 데이터를 Table형식으로 읽을 수 있도록 지원
	        ReadOnlySharedStringsTable data = new ReadOnlySharedStringsTable(opc);

	        //읽어온 Table에 적용되어 있는 Style
	        StylesTable styles = xssfReader.getStylesTable();

	        //엑셀의 첫번째 sheet정보만 읽어오기 위해 사용 만약 다중 sheet 처리를 위해서는 반복문 필요
	        InputStream sheetStream = xssfReader.getSheetsData().next();
	        InputSource sheetSource = new InputSource(sheetStream);

	        //XMLHandler 생성
	        ContentHandler handler = new XSSFSheetXMLHandler(styles, data, sheetHandler, false);

	        //SAX 형식의 XMLReader 생성
	        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
	        parserFactory.setNamespaceAware(true);
	        SAXParser parser = parserFactory.newSAXParser();
	        XMLReader reader = parser.getXMLReader();

	        //XMLReader에 재정의하여 구현한 인터페이스 설정
	        reader.setContentHandler(handler);

	        //파싱하여 처리
	        reader.parse(sheetSource);
	        sheetStream.close();
	        opc.close();

	     // Header List : sheetHandler.getHeader();
	     // Rows List : sheetHandler.getRows();

	    } catch (Exception e) {
	    	log.error("[Excel Read Error] Cause: {}", e.getCause(), e);
	        throw new RuntimeException(e);
	    }

	    return sheetHandler;
	}

	private String[] splitArrayString(String str) {
		return splitArrayString(str, SPLIT_DELEMETER);
	}
	
	private String[] splitArrayString(String str, String delimiter) {
		if (StringUtils.isEmpty(checkInvalidVal(str))) {
			return null;
		}
		//split & trim
		String[] result = Arrays.stream(StringUtils.split(str, delimiter)).map(String::trim).toArray(String[]::new);
		return result;
	}
	
	private String checkInvalidVal(String str) {
		if ("".equals(str) || "-".equals(str)) {
			return null;
		} else {
			return str;
		}
	}
}