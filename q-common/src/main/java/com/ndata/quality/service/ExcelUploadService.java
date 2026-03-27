package com.ndata.quality.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

@Service
@Slf4j
public class ExcelUploadService {
	
	public final String SPLIT_DELEMETER = ",";
	
	@Autowired
	private SqlSessionFactory sqlSessionFactory;	// transaction 사용할 경우 사용
	
	//단어 일괄 저장
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
		return result;
	}
	
	//용어 일괄 저장
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
					for (String wordEngAbrvNm : wordEngAbrvNms) {
						StdWordVo wordVo = session.selectOne("word.selectWordByEngAbrvNm", wordEngAbrvNm);
						if (wordVo == null) {
							invalidWords.add(wordEngAbrvNm);
						}
					}
					if (!invalidWords.isEmpty()) {
						throw new Exception(String.format("단어(%s)가 유효하지 않음", String.join(", ", invalidWords)));
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
		return result;
	}

	//코드 일괄 저장
	public UploadResult uploadCodeInfoList(String userId, MultipartFile multiPart) throws Exception {
		return uploadTermsList(userId, multiPart);
	}

	//코드데이터(항목값) 일괄 저장
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
		return result;
	}

	//도메인 일괄 저장
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
		return result;
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