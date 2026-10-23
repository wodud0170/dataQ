package com.ndata.quality.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
import com.ndata.quality.common.PermCode;
import com.ndata.quality.common.StdInputRule;

import lombok.extern.slf4j.Slf4j;

import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdDomainClassificationVo;
import com.ndata.quality.model.std.StdDomainGroupVo;
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

	@Autowired
	private StdDictService dictService;

	@Autowired
	private PermService permService;

	@Autowired
	private CodeTypeService codeTypeService;

	/** 104번 WP1 — 승인 대기로 들어간 행에 결재선이 있으면 결재 문서. 알림은 저장만 하고(화면은 헤더가 주기적으로 읽는다) 여기서 실시간 전송은 하지 않는다 */
	@Autowired
	private AprvService aprvService;

	/**
	 * 98번 — 이름으로 사전 항목을 찾을 때 쓰는 파라미터.
	 *
	 * <p>사전을 나눈 뒤로 "영문약어가 BTWN 인 단어" 만으로는 답이 하나가 아니다.
	 * 반드시 어느 사전에서 찾는지를 같이 줘야 한다.</p>
	 */
	private Map<String, Object> byName(String dictId, String key, String value) {
		Map<String, Object> p = new HashMap<String, Object>();
		p.put("dictId", dictId);
		p.put(key, value);
		return p;
	}

	/**
	 * 업로드 대상 사전을 확정한다. 각 업로드 메서드 첫 줄에서 부른다.
	 *
	 * <p>비어 있으면 기본 사전. <b>null 을 그대로 흘리면 안 된다</b> —
	 * 매퍼의 <code>DICT_ID = #{dictId}</code> 가 SQL 에서 <code>DICT_ID = NULL</code>
	 * 이 되어 항상 거짓이다. 그러면 중복 검사가 절대 안 걸려 기존 단어가 SKIP 이 아니라
	 * 유니크 위반 FAIL 로 떨어지고, 용어의 구성단어 조회도 절대 안 걸려 모든 용어가
	 * "단어가 유효하지 않음" 으로 실패한다. (2026-09-14 발견 — 화면이 사전을 안 보내던 상태)</p>
	 */
	private String resolveDict(String dictId) {
		return (dictId == null || dictId.trim().isEmpty()) ? dictService.defaultDictId() : dictId.trim();
	}

	/**
	 * 104번 WP2 — 승인 없이 바로 등록(APRV_YN='Y')할 수 있는 사용자인지.
	 * 관리자 또는 "표준 직접 관리"(STD_MANAGE) 권한. 화면 등록(createWord 등)과 같은 기준이다.
	 */
	private boolean canRegisterApproved(String userId) {
		return permService.has(userId, PermCode.STD_MANAGE);
	}
	
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
	public UploadResult uploadWords(String userId, String dictId, MultipartFile multiPart) throws Exception {
		dictId = resolveDict(dictId);
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();
		String aprvYn = canRegisterApproved(userId) ? "Y" : "N";

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdWordVo stdWordVo = new StdWordVo();
				try {
					// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자(켜면 대문자로 바꿔 받고, 끄면 소문자인 행은 실패)
					String engAbrv = dictService.checkWordEngAbrv(dictId, dataRow.get(3));
					// 단어가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("word.selectWordByEngAbrvNm", byName(dictId, "wordEngAbrvNm", engAbrv)) != null) {
						result.addSkip();
						continue;
					}
					stdWordVo.setId(StringUtils.getUUID());
					stdWordVo.setWordNm(dataRow.get(2));

					// 104번 WP6-13 — 사전 운영 옵션: 단어 영문명 중복
					dictService.checkWordEngNmDup(dictId, dataRow.get(4), stdWordVo.getId());
					// 영문약어 규격 체크 — 110번 3.12-12-c: 화면 등록과 같은 규칙 (대문자 영문으로 시작, 대문자·숫자만)
					String abrvErr = StdInputRule.wordEngAbrvError(engAbrv);
					if (abrvErr != null) {
						throw new Exception(abrvErr);
					}
					// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
					Map<String, Object> forbiddenWord = session.selectOne("word.selectWordByForbiddenNm", byName(dictId, "wordNm", dataRow.get(2)));
					if (forbiddenWord != null) {
						String stdWordNm = (String) forbiddenWord.get("wordNm");
						throw new Exception("'" + dataRow.get(2) + "'은(는) '" + stdWordNm + "'의 금칙어입니다. '" + stdWordNm + "'를 사용해주세요.");
					}
					// 110번 3.12-12-c — 화면 등록과 같이, 다른 단어의 유사어(이음동의어)인 이름은 받지 않는다
					List<StdWordVo> sameNm = session.selectList("word.selectWordInfoByNm", byName(dictId, "wordNm", dataRow.get(2)));
					for (StdWordVo w : sameNm) {
						if (!w.getWordNm().equals(dataRow.get(2))) {
							throw new Exception(StdInputRule.synonymOfMsg(dataRow.get(2), w.getWordNm()));
						}
					}

					stdWordVo.setWordEngAbrvNm(engAbrv);
					stdWordVo.setWordEngNm(dataRow.get(4));
					stdWordVo.setWordDesc(dataRow.get(5));
					stdWordVo.setWordClsfYn(dataRow.get(6));
					stdWordVo.setDomainClsfNm(checkInvalidVal(dataRow.get(7)));
					// 형식단어인데 도메인 분류명이 없으면 등록 차단
					if ("Y".equals(stdWordVo.getWordClsfYn()) && stdWordVo.getDomainClsfNm() == null) {
						throw new Exception("형식 단어는 도메인 분류명이 필수입니다.");
					}
					stdWordVo.setAllophSynmLst(splitArrayString(dataRow.get(8)));
					stdWordVo.setForbdnWordLst(splitArrayString(dataRow.get(9)));
					String reqSysNm = checkInvalidVal(dataRow.get(10));
					if (reqSysNm != null) {
						stdWordVo.setReqSysCd(session.selectOne("sysinfo.selectSysCdBySysNm", reqSysNm));
					}
					if (dataRow.size() > 11) {
						stdWordVo.setCommStndYn(dataRow.get(11));
					}
					stdWordVo.setMagntdOrd(dataRow.get(1));
					stdWordVo.setCretUserId(userId);
					stdWordVo.setUpdtUserId(userId);
					stdWordVo.setAprvYn(aprvYn);
					stdWordVo.setDictId(dictId);
					session.insert("word.insertWord", stdWordVo);
					session.commit();
					result.addSuccess(stdWordVo.getId(), stdWordVo.getWordNm());
					if ("N".equals(aprvYn)) aprvService.open("WORD", stdWordVo.getId(), stdWordVo.getWordNm(), null, dictId, userId);
				} catch (Exception e) {
					session.rollback();
					String errMsg = StdInputRule.translateDuplicateError(e.getMessage());
					if (errMsg == null) errMsg = e.getMessage();
					String detail = String.format("단어(%s): %s", stdWordVo.getWordNm() != null ? stdWordVo.getWordNm() : dataRow.get(2), errMsg);
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
	public UploadResult uploadTermsList(String userId, String dictId, MultipartFile multiPart) throws Exception {
		return uploadTermsList(userId, dictId, multiPart, null);
	}

	public UploadResult uploadTermsList(String userId, String dictId, MultipartFile multiPart, BiConsumer<Integer,Integer> progressCallback) throws Exception {
		return uploadTermsList(userId, dictId, multiPart, progressCallback, "TERM", "용어");
	}

	/** 110번 3.12-7-e — 코드 일괄등록도 같은 처리라 이력 대상(TERM·CODE)만 받아 한 건으로 남긴다 (예전에는 코드가 TERM·CODE 두 건) */
	private UploadResult uploadTermsList(String userId, String dictId, MultipartFile multiPart, BiConsumer<Integer,Integer> progressCallback,
			String histTargetType, String histLabel) throws Exception {
		dictId = resolveDict(dictId);
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();
		String aprvYn = canRegisterApproved(userId) ? "Y" : "N";

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();
			int total = excelDatas.size();
			int processed = 0;
			int progressInterval = 1000;

			for (List<String> dataRow : excelDatas) {
				StdTermsVo stdTermsVo = new StdTermsVo();
				try {
					// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자, 용어 영문약어명 최대 길이 (어긋나면 그 행 실패)
					String termsEngAbrv = dictService.checkTermEngAbrv(dictId, dataRow.get(4));
					// 용어가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("terms.selectTermsByEngNm", byName(dictId, "termsEngAbrvNm", termsEngAbrv)) != null) {
						result.addSkip();
						continue;
					}
					stdTermsVo.setId(StringUtils.getUUID());
					stdTermsVo.setTermsNm(dataRow.get(2));
					stdTermsVo.setTermsDesc(dataRow.get(3));
					stdTermsVo.setTermsEngAbrvNm(termsEngAbrv);
					stdTermsVo.setDomainNm(checkInvalidVal(dataRow.get(5)));
					stdTermsVo.setCodeGrp(checkInvalidVal(dataRow.get(9)));
					stdTermsVo.setChrgOrg(checkInvalidVal(dataRow.get(10)));
					stdTermsVo.setAllophSynmLst(splitArrayString(dataRow.get(11)));
					String reqSysNm = checkInvalidVal(dataRow.get(12));
					if (reqSysNm != null) {
						stdTermsVo.setReqSysCd(session.selectOne("sysinfo.selectSysCdBySysNm", reqSysNm));
					}
					if (dataRow.size() > 13) {
						stdTermsVo.setCommStndYn(dataRow.get(13));
					}
					// 104번 WP5-16 — 빈칸이면 도메인 값을 따른다
					stdTermsVo.setPrivYn(ynCell(dataRow, 14, "개인정보여부"));
					stdTermsVo.setEncrYn(ynCell(dataRow, 15, "암호화여부"));
					stdTermsVo.setMagntdOrd(dataRow.get(1));
					stdTermsVo.setCretUserId(userId);
					stdTermsVo.setUpdtUserId(userId);
					stdTermsVo.setAprvYn(aprvYn);
					stdTermsVo.setDictId(dictId);

					// 도메인 유효성 검증
					if (stdTermsVo.getDomainNm() != null) {
						Object domainCheck = session.selectOne("domain.selectDomainInfoByNm", byName(dictId, "domainNm", stdTermsVo.getDomainNm()));
						if (domainCheck == null) {
							throw new Exception(String.format("도메인(%s)이 유효하지 않음", stdTermsVo.getDomainNm()));
						}
					}

					// 구성단어 유효성 검증 (insert 전에 먼저 확인, 승인 여부는 용어 승인 시점에 체크)
					String[] wordEngAbrvNms = stdTermsVo.getTermsEngAbrvNm().split("_");
					List<String> invalidWords = new ArrayList<>();
					for (String wordEngAbrvNm : wordEngAbrvNms) {
						StdWordVo wordVo = session.selectOne("word.selectWordByEngAbrvNm", byName(dictId, "wordEngAbrvNm", wordEngAbrvNm));
						if (wordVo == null) {
							invalidWords.add(wordEngAbrvNm);
						}
					}
					if (!invalidWords.isEmpty()) {
						throw new Exception(String.format("단어(%s)가 유효하지 않음", String.join(", ", invalidWords)));
					}

					// 형식단어 검증: 마지막 단어가 형식단어(WORD_CLSF_YN='Y')여야 함.
					// 98번 — 사전 속성이다. 기관마다 표준화 관례가 달라서 켠 사전에서만 본다.
					// 단일 단어 용어도 허용 (그 자체가 형식단어면 유효)
					if (dictService.isTermLastWordClsfRequired(dictId)) {
						String lastEngAbrv = wordEngAbrvNms[wordEngAbrvNms.length - 1];
						StdWordVo lastWordVo = session.selectOne("word.selectWordByEngAbrvNm", byName(dictId, "wordEngAbrvNm", lastEngAbrv));
						if (lastWordVo != null && !"Y".equals(lastWordVo.getWordClsfYn())) {
							throw new Exception("용어의 마지막 단어는 형식단어여야 합니다. (현재: " + lastWordVo.getWordNm() + ")");
						}
					}

					session.insert("terms.insertTerms", stdTermsVo);

					List<StdTermsVo.Word> wordList = new ArrayList<StdTermsVo.Word>();
					short loop = 0;
					for (String wordEngAbrvNm : wordEngAbrvNms) {
						StdWordVo wordVo = session.selectOne("word.selectWordByEngAbrvNm", byName(dictId, "wordEngAbrvNm", wordEngAbrvNm));
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
					result.addSuccess(stdTermsVo.getId(), stdTermsVo.getTermsNm());
					if ("N".equals(aprvYn)) {
						String reqTp = stdTermsVo.getCodeGrp() != null && !stdTermsVo.getCodeGrp().isEmpty() ? "CODE" : "TERMS";
						aprvService.open(reqTp, stdTermsVo.getId(), stdTermsVo.getTermsNm(), null, dictId, userId);
					}
				} catch (Exception e) {
					session.rollback();
					String errMsg = StdInputRule.translateDuplicateError(e.getMessage());
					if (errMsg == null) errMsg = e.getMessage();
					String detail = String.format("용어(%s): %s", stdTermsVo.getTermsNm() != null ? stdTermsVo.getTermsNm() : dataRow.get(2), errMsg);
					log.error("upload terms row failed: {}", detail);
					result.addFail(detail);
				}
				processed++;
				if (progressCallback != null && (processed % progressInterval == 0 || processed == total)) {
					try { progressCallback.accept(processed, total); } catch (Exception ignored) {}
				}
			}
		} finally {
			session.close();
		}
		// 일괄 등록 이력 저장
		saveUploadHistory(userId, histTargetType, histLabel, result);
		return result;
	}

	/**
	 * 코드 Excel 일괄 등록 - 내부적으로 uploadTermsList를 호출 (이력은 CODE 한 건)
	 *
	 * @param userId    실행 사용자 ID
	 * @param multiPart 업로드된 Excel 파일
	 * @return 업로드 결과
	 * @throws Exception Excel 파싱 실패 시
	 */
	public UploadResult uploadCodeInfoList(String userId, String dictId, MultipartFile multiPart) throws Exception {
		return uploadTermsList(userId, dictId, multiPart, null, "CODE", "코드");
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
	public UploadResult uploadCodeDataList(String userId, String dictId, MultipartFile multiPart) throws Exception {
		dictId = resolveDict(dictId);
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
					stdCodeDataVo.setDictId(dictId);
					// 코드데이터가 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("codedata.selectCodeDataByNmAndVal", stdCodeDataVo) != null) {
						result.addSkip();
						continue;
					}
					stdCodeDataVo.setCodeValDesc(dataRow.get(5));
					// 104번 WP6-3 — 양식 끝의 상위코드값(계층 코드). 열이 없는 예전 양식도 받는다. 코드 유형별 값 검사
					if (dataRow.size() > 6) stdCodeDataVo.setParentCodeVal(checkInvalidVal(dataRow.get(6)));
					codeTypeService.checkValue(session, stdCodeDataVo);
					session.insert("codedata.insertCodeData", stdCodeDataVo);
					session.commit();
					result.addSuccess(stdCodeDataVo.getId(), stdCodeDataVo.getCodeNm() + " / " + stdCodeDataVo.getCodeVal());
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
	public UploadResult uploadDomains(String userId, String dictId, MultipartFile multiPart) throws Exception {
		dictId = resolveDict(dictId);
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();
		String aprvYn = canRegisterApproved(userId) ? "Y" : "N";

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdDomainVo stdDomainVo = new StdDomainVo();
				try {
					// 도메인이 이미 존재하는 경우에는 Skip 한다.
					if (session.selectOne("domain.selectDomainInfoByNm", byName(dictId, "domainNm", dataRow.get(4))) != null) {
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
						stdDomainVo.setReqSysCd(session.selectOne("sysinfo.selectSysCdBySysNm", reqSysNm));
					}
					if (dataRow.size() > 14) {
						stdDomainVo.setCommStndYn(dataRow.get(14));
					}
					// 104번 WP5-16 — 빈칸이면 'N' (insertDomain 의 COALESCE)
					stdDomainVo.setPrivYn(ynCell(dataRow, 15, "개인정보여부"));
					stdDomainVo.setEncrYn(ynCell(dataRow, 16, "암호화여부"));
					stdDomainVo.setMagntdOrd(dataRow.get(1));
					stdDomainVo.setCretUserId(userId);
					stdDomainVo.setUpdtUserId(userId);
					stdDomainVo.setAprvYn(aprvYn);
					stdDomainVo.setDictId(dictId);
					session.insert("domain.insertDomain", stdDomainVo);
					session.commit();
					result.addSuccess(stdDomainVo.getId(), stdDomainVo.getDomainNm());
					if ("N".equals(aprvYn)) aprvService.open("DOMAIN", stdDomainVo.getId(), stdDomainVo.getDomainNm(), null, dictId, userId);
				} catch (Exception e) {
					session.rollback();
					String domainNm = stdDomainVo.getDomainNm() != null ? stdDomainVo.getDomainNm() : dataRow.get(4);
					String dupMsg = StdInputRule.translateDuplicateError(e.getMessage());
					String reason = dupMsg != null ? dupMsg : translateDomainInsertError(e, stdDomainVo);
					String detail = String.format("도메인(%s): %s", domainNm, reason);
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

	/**
	 * 도메인 그룹 Excel 일괄 등록
	 *
	 * <p>각 행을 파싱하여 도메인 그룹을 등록한다. 이미 존재하면 Skip.
	 * 컬럼: [No, 도메인그룹명, 표준여부]</p>
	 */
	public UploadResult uploadDomainGroups(String userId, String dictId, MultipartFile multiPart) throws Exception {
		dictId = resolveDict(dictId);
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdDomainGroupVo vo = new StdDomainGroupVo();
				try {
					String grpNm = dataRow.size() > 1 ? dataRow.get(1) : null;
					if (grpNm == null || grpNm.trim().isEmpty()) {
						throw new Exception("도메인 그룹명이 비어 있습니다");
					}
					if (session.selectOne("domain.selectDomainGroupByNm", byName(dictId, "domainGrpNm", grpNm)) != null) {
						result.addSkip();
						continue;
					}
					vo.setId(StringUtils.getUUID());
					vo.setDomainGrpNm(grpNm);
					vo.setCommStndYn(dataRow.size() > 2 && checkInvalidVal(dataRow.get(2)) != null ? dataRow.get(2) : "N");
					vo.setCretUserId(userId);
					vo.setDictId(dictId);
					session.insert("domain.insertDomainGroup", vo);
					session.commit();
					result.addSuccess(vo.getId(), vo.getDomainGrpNm());
				} catch (Exception e) {
					session.rollback();
					String detail = String.format("도메인그룹(%s): %s",
							vo.getDomainGrpNm() != null ? vo.getDomainGrpNm() : "?", e.getMessage());
					log.error("upload domain groups row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		saveUploadHistory(userId, "DOMAIN_GRP", "도메인그룹", result);
		return result;
	}

	/**
	 * 도메인 분류 Excel 일괄 등록
	 *
	 * <p>각 행을 파싱하여 도메인 분류를 등록한다. 이미 존재하면 Skip.
	 * 컬럼: [No, 도메인그룹명, 도메인분류명, 표준여부]
	 * 도메인 그룹이 없으면 FK 에러를 사용자가 이해할 수 있는 메시지로 변환.</p>
	 */
	public UploadResult uploadDomainClsfs(String userId, String dictId, MultipartFile multiPart) throws Exception {
		dictId = resolveDict(dictId);
		SqlSession session = sqlSessionFactory.openSession();
		UploadResult result = new UploadResult();

		try {
			ExcelSheetHandler sheetHandler = readExcel(multiPart);
			List<List<String>> excelDatas = sheetHandler.getRows();

			for (List<String> dataRow : excelDatas) {
				StdDomainClassificationVo vo = new StdDomainClassificationVo();
				try {
					String grpNm = dataRow.size() > 1 ? dataRow.get(1) : null;
					String clsfNm = dataRow.size() > 2 ? dataRow.get(2) : null;
					if (clsfNm == null || clsfNm.trim().isEmpty()) {
						throw new Exception("도메인 분류명이 비어 있습니다");
					}
					if (grpNm == null || grpNm.trim().isEmpty()) {
						throw new Exception("도메인 그룹명이 비어 있습니다");
					}
					if (session.selectOne("domain.selectDomainClsfByNm", byName(dictId, "domainClsfNm", clsfNm)) != null) {
						result.addSkip();
						continue;
					}
					vo.setId(StringUtils.getUUID());
					vo.setDomainGrpNm(grpNm);
					vo.setDomainClsfNm(clsfNm);
					vo.setCommStndYn(dataRow.size() > 3 && checkInvalidVal(dataRow.get(3)) != null ? dataRow.get(3) : "N");
					vo.setCretUserId(userId);
					vo.setDictId(dictId);
					session.insert("domain.insertDomainClassification", vo);
					session.commit();
					result.addSuccess(vo.getId(), vo.getDomainClsfNm());
				} catch (Exception e) {
					session.rollback();
					String reason = translateDomainClsfInsertError(e, vo);
					String detail = String.format("도메인분류(%s): %s",
							vo.getDomainClsfNm() != null ? vo.getDomainClsfNm() : "?", reason);
					log.error("upload domain clsfs row failed: {}", detail);
					result.addFail(detail);
				}
			}
		} finally {
			session.close();
		}
		saveUploadHistory(userId, "DOMAIN_CLSF", "도메인분류", result);
		return result;
	}

	// 도메인 분류 INSERT 실패 메시지 변환. FK 위반(존재하지 않는 도메인 그룹) 감지.
	private String translateDomainClsfInsertError(Exception e, StdDomainClassificationVo vo) {
		String msg = e.getMessage();
		if (msg == null) {
			return "알 수 없는 오류";
		}
		if (msg.contains("tb_domain_clsf_fk_1") || msg.contains("(domain_grp_nm)")) {
			return "도메인 그룹 '" + vo.getDomainGrpNm() + "'이(가) 등록되지 않았습니다";
		}
		return msg;
	}

	// 도메인 INSERT 실패 메시지를 사용자가 알기 쉬운 형태로 변환.
	// FK 위반(도메인 그룹/분류 미등록)을 우선 감지하여 친절한 메시지로 바꾸고, 그 외는 원본 메시지 반환.
	private String translateDomainInsertError(Exception e, StdDomainVo vo) {
		String msg = e.getMessage();
		if (msg == null) {
			return "알 수 없는 오류";
		}
		if (msg.contains("tb_domain_fk_1") || msg.contains("(domain_grp_nm)")) {
			return "도메인 그룹 '" + vo.getDomainGrpNm() + "'이(가) 등록되지 않았습니다";
		}
		if (msg.contains("tb_domain_fk_2") || msg.contains("(domain_clsf_nm)")) {
			return "도메인 분류 '" + vo.getDomainClsfNm() + "'이(가) 등록되지 않았습니다";
		}
		return msg;
	}

	// 일괄 등록 이력 저장 헬퍼 — 110번 3.12-7-e: 등록 경로(BULK_UPLOAD)와 등록된 행을 상세 건(TB_CHANGE_HISTORY_DETAIL)으로
	private void saveUploadHistory(String userId, String targetType, String targetLabel, UploadResult result) {
		try {
			SqlSession histSession = sqlSessionFactory.openSession();
			try {
				String changeId = StringUtils.getUUID();
				Map<String, Object> history = new HashMap<>();
				history.put("changeId", changeId);
				history.put("changeType", "BULK_INSERT");
				history.put("targetType", targetType);
				history.put("changeCnt", result.getSuccessCount());
				history.put("summary", String.format("%s 일괄등록 (성공:%d, 건너뜀:%d, 실패:%d)",
						targetLabel, result.getSuccessCount(), result.getSkipCount(), result.getFailCount()));
				history.put("changeUserId", userId);
				history.put("changeSource", "BULK_UPLOAD");
				histSession.insert("changehistory.insertChangeHistory", history);
				int seq = 0;
				for (String[] item : result.getSuccessItems()) {
					Map<String, Object> d = new HashMap<>();
					d.put("changeId", changeId);
					d.put("seq", ++seq);
					d.put("targetId", item[0]);
					d.put("targetNm", item[1]);
					d.put("detailType", "SUCCESS");
					histSession.insert("changehistory.insertChangeHistoryDetail", d);
				}
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

	/**
	 * 104번 WP5-16 — 양식 끝의 개인정보여부·암호화여부 칸. 열이 없는 예전 양식·빈칸은 null.
	 * Y·N 이 아니면 그 행을 실패로 남긴다.
	 */
	private String ynCell(List<String> dataRow, int idx, String label) throws Exception {
		if (dataRow.size() <= idx) return null;
		String v = checkInvalidVal(dataRow.get(idx) == null ? "" : dataRow.get(idx).trim());
		if (v == null) return null;
		v = v.toUpperCase();
		if (!"Y".equals(v) && !"N".equals(v)) {
			throw new Exception(label + "는 Y 또는 N 이어야 합니다 (현재: " + dataRow.get(idx) + ")");
		}
		return v;
	}
}