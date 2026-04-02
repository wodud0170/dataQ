package qualitycenter.controller;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityApproveStat;
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.common.NDQualityStdObjectType;
import com.ndata.quality.model.std.StdApproveStatVo;
import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdCodeInfoVo;
import com.ndata.quality.model.std.StdDomainClassificationVo;
import com.ndata.quality.model.std.StdDomainGroupVo;
import com.ndata.quality.model.std.StdDomainVo;
import com.ndata.quality.model.std.TermAnalysisResult;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.service.ExcelDownloadService;
import com.ndata.quality.service.ExcelUploadService;
import com.ndata.quality.tool.StringWordAnalyzer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.model.data.WordDataVo;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ws.WebSocketService;
import reactor.core.publisher.Mono;

/**
 * 데이터 표준화 컨트롤러 (단어/용어/도메인/코드/승인/표준화 추천/변경이력/영향도 분석)
 *
 * <p>표준 사전(단어, 용어, 도메인, 코드) CRUD 및 Excel 업/다운로드,
 * 승인 처리, 표준화 추천(형태소 분석 기반 일괄 분석/등록),
 * 변경 이력 관리, 영향도 분석 API를 제공한다.</p>
 */
@Tag(name = "데이터표준화", description = "데이터표준화 API")
@Slf4j
@RestController
@RequestMapping("/api/std")
public class DataStandardController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private ExcelUploadService excelUploadService;

	@Autowired
	private ExcelDownloadService excelDownloadService;

	@Autowired
	private WebSocketService websocketService;

	/**
	 * 통합 검색 API - 단어/용어/도메인/컬럼을 키워드로 일괄 검색
	 *
	 * @param keyword 검색 키워드
	 * @return 단어(words), 용어(terms), 도메인(domains), 컬럼(columns) 검색 결과 Map
	 */
	@GetMapping("/search")
	public Map<String, Object> globalSearch(@RequestParam String keyword) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> param = new HashMap<>();
		param.put("keyword", keyword);
		result.put("words", sqlSessionTemplate.selectList("search.selectSearchWords", param));
		result.put("terms", sqlSessionTemplate.selectList("search.selectSearchTerms", param));
		result.put("domains", sqlSessionTemplate.selectList("search.selectSearchDomains", param));
		result.put("columns", sqlSessionTemplate.selectList("search.selectSearchColumns", param));
		return result;
	}

	/**
	 * 단어 단건 등록 API
	 *
	 * - 금칙어 체크: 기존 단어의 금칙어 목록에 포함되면 등록 거부
	 * - 유사어 체크: 기존 단어의 유사어 목록에 포함되면 경고 반환
	 * - 관리자: APRV_YN = 'Y' (즉시 승인), 일반 사용자: APRV_YN = 'N'
	 *
	 * @param dataVo 단어 정보 (wordNm, wordEngAbrvNm, wordEngNm 등)
	 * @return 등록 결과 (성공/실패 + 유사어 경고 메시지)
	 */
	@RequestMapping(value = "/createWord", method = RequestMethod.POST)
	public Mono<Response> createWord(@RequestBody StdWordVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.isAdmin()) {
			dataVo.setAprvYn("Y");
		}
		log.info(">> createWord : {}", dataVo);

		Response result = new Response();

		try {
			// 영문약어 규격 체크: 대문자 영문(A-Z) + 숫자(0-9)만 허용
			if (dataVo.getWordEngAbrvNm() != null && !dataVo.getWordEngAbrvNm().matches("^[A-Z0-9]+$")) {
				throw new Exception("단어 영문약어는 대문자 영문(A-Z)과 숫자(0-9)만 사용할 수 있습니다. (입력값: " + dataVo.getWordEngAbrvNm() + ")");
			}
			// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
			String forbiddenMsg = checkForbiddenWord(dataVo.getWordNm());
			if (forbiddenMsg != null) {
				result.setResultInfo(RestResult.CODE_500.getCode(), forbiddenMsg);
				return Mono.just(result);
			}
			// 유사어 체크: 경고만 (등록은 허용, warning 메시지를 resultMessage에 포함)
			String synonymMsg = checkSynonymWord(dataVo.getWordNm());

			sqlSessionTemplate.insert("word.insertWord", dataVo);
			if (synonymMsg != null) {
				result.setResultInfo(RestResult.CODE_200.getCode(), synonymMsg);
			} else {
				result.setResultInfo(RestResult.CODE_200);
			}
			// 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
			if (sessionService.isAdmin()) {
				saveChangeHistory("INSERT", "WORD", dataVo.getId(), dataVo.getWordNm(),
						null, dataVo.toString(), "단어 등록: " + dataVo.getWordNm());
			}
		} catch (Exception e) {
			log.error(">> createWord failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 단어 수정 API - 변경 전 값을 조회하여 변경이력과 함께 저장
	 *
	 * @param dataVo 수정할 단어 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateWord", method = RequestMethod.POST)
	public Mono<Response> updateWord(@RequestBody StdWordVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateWord : {}", dataVo);

		Response result = new Response();

		try {
			// 변경 전 값 조회
			List<StdWordVo> prevList = sqlSessionTemplate.selectList("word.selectWordInfoById", dataVo.getId());
			String prevValue = prevList != null && !prevList.isEmpty() ? prevList.get(0).toString() : null;
			sqlSessionTemplate.update("word.updateWord", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "WORD", dataVo.getId(), dataVo.getWordNm(),
					prevValue, dataVo.toString(), "단어 수정: " + dataVo.getWordNm());
		} catch (Exception e) {
			log.error(">> updateWord failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 단어 다건 삭제 API
	 *
	 * - 삭제 전 참조 중인 용어가 있으면 삭제 차단
	 * - 삭제된 단어명을 변경이력에 기록
	 *
	 * @param dataVos 삭제 대상 단어 목록
	 * @return 삭제 결과 (참조 용어 존재 시 에러 메시지 포함)
	 */
	@RequestMapping(value = "/deleteWords", method = RequestMethod.POST)
	public Mono<Response> deleteWords(@RequestBody List<StdWordVo> dataVos) {
		Response result = new Response();
		try {
			// 삭제 전 참조 중인 용어 확인
			List<String> usingTerms = sqlSessionTemplate.selectList("word.selectTermsUsingWords", dataVos);
			if (usingTerms != null && !usingTerms.isEmpty()) {
				String termList = usingTerms.size() <= 3
						? String.join(", ", usingTerms)
						: String.join(", ", usingTerms.subList(0, 3)) + " 외 " + (usingTerms.size() - 3) + "건";
				result.setResultInfo(RestResult.CODE_500.getCode(),
						"다음 용어에서 사용 중이므로 삭제할 수 없습니다: " + termList);
				return Mono.just(result);
			}
			// 삭제 전 기존 값 보존
			List<String> deletedNames = new ArrayList<>();
			for (StdWordVo vo : dataVos) {
				if (vo.getWordNm() != null) deletedNames.add(vo.getWordNm());
				else if (vo.getId() != null) {
					List<StdWordVo> prev = sqlSessionTemplate.selectList("word.selectWordInfoById", vo.getId());
					if (prev != null && !prev.isEmpty()) deletedNames.add(prev.get(0).getWordNm());
				}
			}
			sqlSessionTemplate.delete("word.deleteWords", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			String names = String.join(", ", deletedNames);
			saveChangeHistory("DELETE", "WORD", null, names,
					names, null, "단어 삭제 " + dataVos.size() + "건: " + names);
		} catch (Exception e) {
			log.error(">> deleteWords failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), "단어 삭제 중 오류가 발생했습니다.");
		}
		return Mono.just(result);
	}

	/**
	 * 단어 목록 조회 API - 검색 조건(키워드, 정렬 등)에 따라 필터링
	 *
	 * @param retCond 검색 조건 (null이면 전체 조회)
	 * @return 단어 목록
	 */
	@RequestMapping(value = "/getWordList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdWordVo> getWordList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getWordList : {}", retCond);
		return sqlSessionTemplate.selectList("word.selectWordList", retCond);
	}

	/**
	 * 단어명으로 단어 정보 조회
	 *
	 * @param wordNm 단어 한글명
	 * @return 해당 단어 정보 목록 (동음이의어 포함)
	 */
	@RequestMapping(value = "/getWordInfoByNm", method = RequestMethod.GET)
	public List<StdWordVo> getWordInfoByNm(String wordNm) {
		return sqlSessionTemplate.selectList("word.selectWordInfoByNm", wordNm);
	}

	/**
	 * 단어 ID로 단어 정보 조회
	 *
	 * @param wordId 단어 고유 ID (UUID)
	 * @return 해당 단어 정보 목록
	 */
	@RequestMapping(value = "/getWordInfoById", method = RequestMethod.GET)
	public List<StdWordVo> getWordInfoById(String wordId) {
		return sqlSessionTemplate.selectList("word.selectWordInfoById", wordId);
	}

	/** 영문약어로 단어 조회 (용어 빠른 등록용) */
	@RequestMapping(value = "/getWordByEngAbrvNm", method = RequestMethod.GET)
	public StdWordVo getWordByEngAbrvNm(String wordEngAbrvNm) {
		List<StdWordVo> list = sqlSessionTemplate.selectList("word.selectWordByEngAbrvNm", wordEngAbrvNm);
		return list.isEmpty() ? null : list.get(0);
	}

	/** 영문약어 목록으로 단어 일괄 조회 (용어 빠른 등록용) */
	@RequestMapping(value = "/getWordsByEngAbrvNms", method = RequestMethod.POST)
	public List<StdWordVo> getWordsByEngAbrvNms(@RequestBody List<String> engAbrvNms) {
		if (engAbrvNms == null || engAbrvNms.isEmpty()) return java.util.Collections.emptyList();
		return sqlSessionTemplate.selectList("word.selectWordsByEngAbrvNms", engAbrvNms);
	}

	/**
	 * 단어 Excel 일괄 업로드 API - q-executor로 멀티파트 전달
	 *
	 * @param request HTTP 요청 (세션 SSID 추출용)
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과 (성공/실패 건수)
	 */
	@RequestMapping(value = "/uploadWords", method = RequestMethod.POST)
	public Mono<Response> uploadWords(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile) {

		// websocketService.sendMessage(Objects.toString(request.getSession().getAttribute("SSID"), null),
		// WsNoticeLevel.INFO, ">> uploadWords start");
		// String fileName = request.getFileNames().next();
		// log.info(">>> fileName : {}", fileName);
		// MultipartFile excelFile = request.getFile(fileName);

		log.info(">> started uploadWords file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadWords", MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadWords file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

	/**
	 * 단어 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답 (Excel 스트림 출력)
	 * @param searchKey 검색 키워드 (null이면 전체)
	 */
	@RequestMapping(value = "/downloadWords", method = RequestMethod.GET)
	public void downloadWordsExcel(HttpServletRequest request, HttpServletResponse response, String searchKey) {
		log.info(">> download words excel started : {}", searchKey);

		try {
			excelDownloadService.getWordsExcel(searchKey, request, response);
		} catch (Exception e) {
			log.error(">> download words excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 용어 등록 API (트랜잭션 사용)
	 *
	 * - 중복 체크: 동일 한글명 용어가 이미 존재하면 등록 거부
	 * - 구성 단어 승인 여부 체크: 미승인 단어가 포함되면 등록 거부
	 * - 용어-단어 관계(TB_TERMS_WORDS)도 함께 등록
	 * - 관리자: APRV_YN = 'Y' (즉시 승인), 일반 사용자: APRV_YN = 'N'
	 *
	 * @param dataVo 용어 정보 (termsNm, wordList 등)
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createTerms", method = RequestMethod.POST)
	public Mono<Response> createTerms(@RequestBody StdTermsVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// termsDesc가 null이면 termsNm을 자동으로 채움
		if (dataVo.getTermsDesc() == null || dataVo.getTermsDesc().isEmpty()) {
			dataVo.setTermsDesc(dataVo.getTermsNm());
		}
		// domainNm이 null이면 빈 문자열 대신 null 유지 방지
		if (dataVo.getDomainNm() == null || dataVo.getDomainNm().trim().isEmpty()) {
			dataVo.setDomainNm(null);
		}
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.isAdmin()) {
			dataVo.setAprvYn("Y");
		}

		log.info(">> createTerms : {}", dataVo);

		SqlSession session = sqlSessionFactory.openSession();

		Response result = new Response();

		try {
			// 용어가 이미 존재하는 경우에는 Exception 발생시킨다.
			dataVo.setTermsNm(dataVo.getTermsNm().replaceAll("\\s", ""));
			if (session.selectOne("terms.selectTermsByNm", dataVo.getTermsNm()) != null) {
				throw new Exception("terms(" + dataVo.getTermsNm() + ") is already registered");
			}
			// 구성 단어 목록 확인 (승인 여부 체크는 용어 승인 시점에 수행)
			List<StdTermsVo.Word> wordList = Arrays.asList(dataVo.getWordList());
			if (wordList == null) {
				throw new Exception("terms word list is invalid");
			}
			// 분류어 검증: 용어는 최소 2개 이상의 단어로 구성되어야 하며, 마지막 단어는 분류어여야 함
			if (wordList.size() < 2) {
				throw new Exception("용어는 최소 2개 이상의 단어로 구성되어야 합니다");
			}
			StdTermsVo.Word lastWord = wordList.get(wordList.size() - 1);
			if (lastWord.getWordId() != null) {
				List<StdWordVo> lastWordInfo = session.selectList("word.selectWordInfoById", lastWord.getWordId());
				if (!lastWordInfo.isEmpty() && !"Y".equals(lastWordInfo.get(0).getWordClsfYn())) {
					throw new Exception("용어의 마지막 단어는 분류어여야 합니다. (현재: " + lastWordInfo.get(0).getWordNm() + ")");
				}
			}
			// 신규 용어 등록
			session.insert("terms.insertTerms", dataVo);
			wordList.stream().forEach(v -> v.setTermsId(dataVo.getId()));
			session.insert("terms.insertTermsWords", wordList);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장 (관리자 즉시 승인 시에만)
			if (sessionService.isAdmin()) {
				saveChangeHistory("INSERT", "TERM", dataVo.getId(), dataVo.getTermsNm(),
						null, dataVo.toString(), "용어 등록: " + dataVo.getTermsNm());
			}
		} catch (Exception e) {
			session.rollback();
			log.error("create terms : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}

		return Mono.just(result);
	}

	/**
	 * 용어 형태소 분석 API (Map 형태 반환)
	 *
	 * - 입력된 용어명을 OKT 형태소 분석기로 분리하여 단어 후보를 반환
	 * - 단일 단어이면서 형식단어(도메인)인 경우 즉시 반환
	 *
	 * @param termsNm 분석할 용어 한글명
	 * @return 토큰명 -> 단어 정보 목록 (LinkedHashMap, 순서 보존)
	 */
	@RequestMapping(value = "/getTermsTokensByNm", method = RequestMethod.GET)
	public Map<String, List<StdWordVo>> getTermsTokens(String termsNm) {
		log.info(">> request termsNm : {}", termsNm);
		Map<String, List<StdWordVo>> wordMap = new LinkedHashMap<String, List<StdWordVo>>();
		List<StdWordVo> wordVoLst = new ArrayList<StdWordVo>();

		// 요청된 용어가 단어이고, 형식단어(도메인)인 경우에는 바로 리턴한다.
		wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", termsNm);
		log.info(">> word info by termsNm : {}", wordVoLst);
		if (wordVoLst.size() == 1) {
			StdWordVo wordVo = wordVoLst.get(0);
			wordVo.setPartOfSpeech("NN");
			wordMap.put(termsNm, wordVoLst);
			return wordMap;
		}

		// 요청된 용어의 형태소 분석을 통해서 단어들을 추출한다.
		Map<String, String> tokenMap = StringWordAnalyzer.getWordsFromStringByOkt(termsNm, null, false);
		for (String token : tokenMap.keySet()) {
			token = token.substring(0, token.contains("#") ? token.indexOf("#") : token.length());// 동일한 토큰이 여러개인 경우에
																									// '#' 위치에서 잘라낸다.
			String pos = tokenMap.get(token);
			wordVoLst = new ArrayList<StdWordVo>();
			if (pos.equals("NN")) {// 단어가 명사인 경우
				wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", token);
				log.info(">> word list : {}-{}", token, wordVoLst);
				if (wordVoLst.size() > 0) {
					for (StdWordVo wordVo : wordVoLst) {
						wordVo.setPartOfSpeech("NN");
					}
				} else {
					StdWordVo wordVo = new StdWordVo();
					wordVo.setWordNm(token);
					wordVo.setPartOfSpeech("NN");
					wordVoLst.add(wordVo);
				}
			} else {// 단어가 명사가 아닌 경우
				StdWordVo wordVo = new StdWordVo();
				wordVo.setWordNm(token);
				wordVo.setPartOfSpeech(pos);
				wordVoLst.add(wordVo);
			}
			wordMap.put(token, wordVoLst);
		}

		return wordMap;
	}

	/**
	 * 용어 형태소 분석 API (List 형태 반환, Vue 화면 바인딩용)
	 *
	 * @param termsNm 분석할 용어 한글명
	 * @return WordDataVo 목록 (토큰명 + 후보 단어 리스트)
	 */
	@RequestMapping(value = "/getTermsTokenListByNm", method = RequestMethod.GET)
	public List<WordDataVo> getTermsTokenListByNm(String termsNm) {
		log.info(">> request termsNm : {}", termsNm);
		List<WordDataVo> wordDatas = new ArrayList<>();
		List<StdWordVo> wordVoLst = new ArrayList<StdWordVo>();

		// 요청된 용어가 단어이고, 형식단어(도메인)인 경우에는 바로 리턴한다.
		wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", termsNm);
		log.info(">> word info by termsNm : {}", wordVoLst);
		if (wordVoLst.size() == 1) {
			StdWordVo wordVo = wordVoLst.get(0);
			wordVo.setPartOfSpeech("NN");
			// set result
			WordDataVo wordData = new WordDataVo();
			wordData.setWordNm(termsNm);
			wordData.getWordLst().add(wordVo);
			wordDatas.add(wordData);
			return wordDatas;
		}

		// 요청된 용어의 형태소 분석을 통해서 단어들을 추출한다.
		Map<String, String> tokenMap = StringWordAnalyzer.getWordsFromStringByOkt(termsNm, null, false);
		for (String token : tokenMap.keySet()) {
			token = token.substring(0, token.contains("#") ? token.indexOf("#") : token.length());// 동일한 토큰이 여러개인 경우에
																									// '#' 위치에서 잘라낸다.
			String pos = tokenMap.get(token);
			wordVoLst = new ArrayList<StdWordVo>();
			if (pos.equals("NN")) {// 단어가 명사인 경우
				wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", token);
				log.info(">> word list : {}-{}", token, wordVoLst);
				if (wordVoLst.size() > 0) {
					for (StdWordVo wordVo : wordVoLst) {
						wordVo.setPartOfSpeech("NN");
					}
				} else {
					StdWordVo wordVo = new StdWordVo();
					wordVo.setWordNm(token);
					wordVo.setPartOfSpeech("NN");
					wordVoLst.add(wordVo);
				}
			} else {// 단어가 명사가 아닌 경우
				StdWordVo wordVo = new StdWordVo();
				wordVo.setWordNm(token);
				wordVo.setPartOfSpeech(pos);
				wordVoLst.add(wordVo);
			}
			// set result
			WordDataVo wordData = new WordDataVo();
			wordData.setWordNm(token);
			for (StdWordVo wordVo : wordVoLst) {
				wordData.getWordLst().add(wordVo);
			}
			wordDatas.add(wordData);
		}

		return wordDatas;
	}

	/**
	 * 용어 수정 API (트랜잭션 사용)
	 *
	 * - 중복 체크: 다른 용어의 한글명과 충돌하면 수정 거부
	 * - 구성 단어(TB_TERMS_WORDS) 삭제 후 재등록
	 *
	 * @param dataVo 수정할 용어 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateTerms", method = RequestMethod.POST)
	public Mono<Response> updateTerms(@RequestBody StdTermsVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateTerms : {}", dataVo);

		SqlSession session = sqlSessionFactory.openSession();

		Response result = new Response();

		try {
			// 변경 전 값 조회
			StdTermsVo prevTerms = session.selectOne("terms.selectTermsByNm", dataVo.getTermsNm());
			String prevValue = prevTerms != null ? prevTerms.toString() : null;

			// 기존 용어를 이미 존재하는 용어로 업데이트하는 경우에는 Exception 발생시킨다.
			dataVo.setTermsNm(dataVo.getTermsNm().replaceAll("\\s", ""));
			StdTermsVo stdTermsVo = session.selectOne("terms.selectTermsByNm", dataVo.getTermsNm());
			if (stdTermsVo != null && !dataVo.getId().equals(stdTermsVo.getId())) {
				throw new Exception("previously registered terms can not be updated to existing terms("
						+ dataVo.getTermsNm() + ")");
			}

			session.update("terms.updateTerms", dataVo);
			session.delete("terms.deleteTermsWords", dataVo.getId());
			session.insert("terms.insertTermsWords", Arrays.asList(dataVo.getWordList()));
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "TERM", dataVo.getId(), dataVo.getTermsNm(),
					prevValue, dataVo.toString(), "용어 수정: " + dataVo.getTermsNm());
		} catch (Exception e) {
			session.rollback();
			log.error("update terms : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}

		return Mono.just(result);
	}

	/**
	 * 용어 다건 삭제 API
	 *
	 * @param dataVos 삭제 대상 용어 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteTermsList", method = RequestMethod.POST)
	public Mono<Response> deleteTermsList(@RequestBody List<StdTermsVo> dataVos) {
		Response result = new Response();
		try {
			List<String> deletedNames = new ArrayList<>();
			for (StdTermsVo vo : dataVos) {
				if (vo.getTermsNm() != null) deletedNames.add(vo.getTermsNm());
			}
			sqlSessionTemplate.delete("terms.deleteTermsList", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			String names = String.join(", ", deletedNames);
			saveChangeHistory("DELETE", "TERM", null, names,
					names, null, "용어 삭제 " + dataVos.size() + "건: " + names);
		} catch (Exception e) {
			log.error(">> deleteTermsList failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 용어 목록 조회 API
	 *
	 * @param retCond 검색 조건 (null이면 전체 조회)
	 * @return 용어 목록
	 */
	@RequestMapping(value = "/getTermsList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdTermsVo> getTermsList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getTermsList : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectTermsList", retCond);
	}

	/**
	 * 용어 목록 조건 조회 API (POST 전용)
	 *
	 * @param retCond 검색 조건
	 * @return 조건에 맞는 용어 목록
	 */
	@RequestMapping(value = "/getTermsListByCond", method = RequestMethod.POST)
	public List<StdTermsVo> getTermsListByCond(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getTermsListByCond : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectTermsList", retCond);
	}

	/**
	 * 용어명으로 용어 상세 정보 조회
	 *
	 * @param termsNm 용어 한글명
	 * @return 해당 용어 정보 목록
	 */
	@RequestMapping(value = "/getTermsInfoByNm", method = RequestMethod.GET)
	public List<StdTermsVo> getTermsInfoByNm(String termsNm) {
		return sqlSessionTemplate.selectList("terms.selectTermsInfoByNm", termsNm);
	}

	/**
	 * 용어 구성 단어 목록 조회 - 용어 ID 기준
	 *
	 * @param termsId 용어 고유 ID
	 * @return 구성 단어 정보 목록 (순서대로)
	 */
	@RequestMapping(value = "/getTermsWordInfoList", method = RequestMethod.GET)
	public List<StdWordVo> getTermsWordInfoList(String termsId) {
		return sqlSessionTemplate.selectList("word.selectTermsWordInfoList", termsId);
	}

	/**
	 * 용어 Excel 일괄 업로드 API - q-executor로 멀티파트 전달
	 *
	 * @param request HTTP 요청
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과
	 */
	@RequestMapping(value = "/uploadTermsList", method = RequestMethod.POST)
	public Mono<Response> uploadTermsList(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile) {

		log.info(">> started uploadTermsList file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadTermsList", MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadTermsList file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

	/**
	 * 용어 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답 (Excel 스트림 출력)
	 * @param searchKey 검색 키워드
	 */
	@RequestMapping(value = "/downloadTermsList", method = RequestMethod.GET)
	public void downloadTermsListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey) {
		log.info(">> download terms excel started : {}", searchKey);

		try {
			excelDownloadService.getTermsListExcel(searchKey, request, response);
		} catch (Exception e) {
			log.error(">> download terms excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 코드 정보 목록 조회 API
	 *
	 * @param retCond 검색 조건 (null이면 전체 조회)
	 * @return 코드 정보 목록
	 */
	@RequestMapping(value = "/getCodeInfoList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdCodeInfoVo> getCodeInfoList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getCodeInfoList : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectCodeInfoList", retCond);
	}

	/**
	 * 코드명으로 코드 정보 검색 (LIKE 검색)
	 *
	 * @param codeNm 코드명 키워드
	 * @return 매칭되는 코드 정보 목록
	 */
	@RequestMapping(value = "/getCodeInfoListByNm", method = RequestMethod.GET)
	public List<StdCodeInfoVo> getCodeInfoListByNm(String codeNm) {
		return sqlSessionTemplate.selectList("terms.selectCodeInfoListByNm", "%" + codeNm + "%");
	}

	/**
	 * 코드 등록 API - 내부적으로 createTerms를 호출하고 코드 이력을 추가 저장
	 *
	 * @param dataVo 코드(=용어) 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createCode", method = RequestMethod.POST)
	public Mono<Response> createCode(@RequestBody StdTermsVo dataVo) {
		Mono<Response> resp = createTerms(dataVo);
		// 코드 이력을 별도로 저장 (관리자 즉시 승인 시에만)
		if (sessionService.isAdmin()) {
			saveChangeHistory("INSERT", "CODE", dataVo.getId(), dataVo.getTermsNm(),
					null, dataVo.toString(), "코드 등록: " + dataVo.getTermsNm());
		}
		return resp;
	}

	/**
	 * 코드 수정 API - 내부적으로 updateTerms를 호출하고 코드 이력을 추가 저장
	 *
	 * @param dataVo 수정할 코드 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateCode", method = RequestMethod.POST)
	public Mono<Response> updateCode(@RequestBody StdTermsVo dataVo) {
		Mono<Response> resp = updateTerms(dataVo);
		saveChangeHistory("UPDATE", "CODE", dataVo.getId(), dataVo.getTermsNm(),
				null, dataVo.toString(), "코드 수정: " + dataVo.getTermsNm());
		return resp;
	}

	/**
	 * 코드 다건 삭제 API
	 *
	 * @param dataVos 삭제 대상 코드 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteCodeList", method = RequestMethod.POST)
	public Mono<Response> deleteCodeList(@RequestBody List<StdTermsVo> dataVos) {
		List<String> deletedNames = new ArrayList<>();
		for (StdTermsVo vo : dataVos) {
			if (vo.getTermsNm() != null) deletedNames.add(vo.getTermsNm());
		}
		Mono<Response> resp = deleteTermsList(dataVos);
		String names = String.join(", ", deletedNames);
		saveChangeHistory("DELETE", "CODE", null, names,
				names, null, "코드 삭제 " + dataVos.size() + "건: " + names);
		return resp;
	}

	/**
	 * 코드 Excel 일괄 업로드 API - q-executor로 멀티파트 전달
	 *
	 * @param request HTTP 요청
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과
	 */
	@RequestMapping(value = "/uploadCodeInfoList", method = RequestMethod.POST)
	public Mono<Response> uploadCodeInfoList(HttpServletRequest request,
			@RequestParam("file") MultipartFile excelFile) {

		log.info(">> started uploadCodeInfoList file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadCodeInfoList",
				MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadCodeInfoList file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

	/**
	 * 코드 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답
	 * @param searchKey 검색 키워드
	 */
	@RequestMapping(value = "/downloadCodeInfoList", method = RequestMethod.GET)
	public void downloadCodeInfoListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey) {
		log.info(">> download code excel started : {}", searchKey);

		try {
			excelDownloadService.getCodeInfoListExcel(searchKey, request, response);
		} catch (Exception e) {
			log.error(">> download code infos excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 코드데이터(항목값) 전체 목록 조회
	 *
	 * @return 코드데이터 전체 목록
	 */
	@RequestMapping(value = "/getCodeDataList", method = RequestMethod.GET)
	public List<StdCodeDataVo> getCodeDataList() {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataList");
	}

	/**
	 * 코드명으로 코드데이터 검색
	 *
	 * @param codeNm 코드명
	 * @return 해당 코드의 데이터(항목값) 목록
	 */
	@RequestMapping(value = "/getCodeDataListByNm", method = RequestMethod.GET)
	public List<StdCodeDataVo> getCodeDataListByNm(String codeNm) {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataListByNm", codeNm);
	}

	/**
	 * 코드데이터(항목값) 단건 등록
	 *
	 * @param dataVo 코드데이터 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createCodeData", method = RequestMethod.POST)
	public Mono<Response> createCodeData(@RequestBody StdCodeDataVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		log.info(">> createCodeData : {}", dataVo);

		Response result = new Response();
		try {
			sqlSessionTemplate.insert("codedata.insertCodeData", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("INSERT", "CODE_DATA", dataVo.getId(), dataVo.getCodeNm(),
					null, dataVo.toString(), "코드데이터 등록: " + dataVo.getCodeNm());
		} catch (Exception e) {
			log.error(">> createCodeData failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 코드데이터(항목값) 수정
	 *
	 * @param dataVo 수정할 코드데이터 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateCodeData", method = RequestMethod.POST)
	public Mono<Response> updateCodeData(@RequestBody StdCodeDataVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateCodeData : {}", dataVo);

		Response result = new Response();
		try {
			sqlSessionTemplate.update("codedata.updateCodeData", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "CODE_DATA", dataVo.getId(), dataVo.getCodeNm(),
					null, dataVo.toString(), "코드데이터 수정: " + dataVo.getCodeNm());
		} catch (Exception e) {
			log.error(">> updateCodeData failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 코드데이터(항목값) 다건 삭제
	 *
	 * @param dataVos 삭제 대상 코드데이터 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteCodeDatas", method = RequestMethod.POST)
	public Mono<Response> deleteCodeDatas(@RequestBody List<StdCodeDataVo> dataVos) {
		Response result = new Response();
		try {
			List<String> deletedNames = new ArrayList<>();
			for (StdCodeDataVo vo : dataVos) {
				if (vo.getCodeNm() != null) deletedNames.add(vo.getCodeNm());
			}
			sqlSessionTemplate.delete("codedata.deleteCodeDatas", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			String names = String.join(", ", deletedNames);
			saveChangeHistory("DELETE", "CODE_DATA", null, names,
					names, null, "코드데이터 삭제 " + dataVos.size() + "건: " + names);
		} catch (Exception e) {
			log.error(">> deleteCodeDatas failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 코드데이터 Excel 일괄 업로드 - q-executor로 전달
	 *
	 * @param request HTTP 요청
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과
	 */
	@RequestMapping(value = "/uploadCodeDataList", method = RequestMethod.POST)
	public Mono<Response> uploadCodeDataList(HttpServletRequest request,
			@RequestParam("file") MultipartFile excelFile) {

		log.info(">> started uploadCodeDataList file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadCodeDataList",
				MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadCodeDataList file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

	/**
	 * 코드데이터 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답
	 * @param searchKey 검색 키워드
	 */
	@RequestMapping(value = "/downloadCodeDataList", method = RequestMethod.GET)
	public void downloadCodeDataListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey) {
		log.info(">> download code excel started : {}", searchKey);

		try {
			excelDownloadService.getCodeDataListExcel(searchKey, request, response);
		} catch (Exception e) {
			log.error(">> download code datas excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 도메인 등록 API
	 *
	 * - 중복 체크: 동일 도메인명이 이미 존재하면 등록 거부
	 * - 관리자: APRV_YN = 'Y' (즉시 승인), 일반 사용자: APRV_YN = 'N'
	 *
	 * @param dataVo 도메인 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createDomain", method = RequestMethod.POST)
	public Mono<Response> createDomain(@RequestBody StdDomainVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.isAdmin()) {
			dataVo.setAprvYn("Y");
		}
		log.info(">> createDomain : {}", dataVo);

		Response result = new Response();
		try {
			if (sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm", dataVo.getDomainNm()) != null) {
				throw new Exception("domain(" + dataVo.getDomainNm() + ") is already registered");
			}
			sqlSessionTemplate.insert("domain.insertDomain", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
			if (sessionService.isAdmin()) {
				saveChangeHistory("INSERT", "DOMAIN", dataVo.getId(), dataVo.getDomainNm(),
						null, dataVo.toString(), "도메인 등록: " + dataVo.getDomainNm());
			}
		} catch (Exception e) {
			log.error(">> createDomain failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 도메인 수정 API - 변경 전 값 조회 후 이력과 함께 저장
	 *
	 * @param dataVo 수정할 도메인 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateDomain", method = RequestMethod.POST)
	public Mono<Response> updateDomain(@RequestBody StdDomainVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateDomain : {}", dataVo);

		Response result = new Response();
		try {
			// 변경 전 값 조회
			List<StdDomainVo> prevList = sqlSessionTemplate.selectList("domain.selectDomainInfoByNm", dataVo.getDomainNm());
			String prevValue = prevList != null && !prevList.isEmpty() ? prevList.get(0).toString() : null;
			sqlSessionTemplate.update("domain.updateDomain", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "DOMAIN", dataVo.getId(), dataVo.getDomainNm(),
					prevValue, dataVo.toString(), "도메인 수정: " + dataVo.getDomainNm());
		} catch (Exception e) {
			log.error(">> updateDomain failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	/**
	 * 도메인 다건 삭제 API
	 *
	 * @param dataVos 삭제 대상 도메인 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteDomains", method = RequestMethod.POST)
	public Mono<Response> deleteDomains(@RequestBody List<StdDomainVo> dataVos) {
		Response result = new Response();
		try {
			List<String> deletedNames = new ArrayList<>();
			for (StdDomainVo vo : dataVos) {
				if (vo.getDomainNm() != null) deletedNames.add(vo.getDomainNm());
			}
			sqlSessionTemplate.delete("domain.deleteDomains", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			String names = String.join(", ", deletedNames);
			saveChangeHistory("DELETE", "DOMAIN", null, names,
					names, null, "도메인 삭제 " + dataVos.size() + "건: " + names);
		} catch (Exception e) {
			log.error(">> deleteDomains failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 도메인 목록 조회 API
	 *
	 * @param retCond 검색 조건 (null이면 전체)
	 * @return 도메인 목록
	 */
	@RequestMapping(value = "/getDomainList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDomainVo> getDomainList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getDomainList : {}", retCond);
		return sqlSessionTemplate.selectList("domain.selectDomainList", retCond);
	}

	/**
	 * 도메인명으로 도메인 정보 조회
	 *
	 * @param domainNm 도메인명
	 * @return 해당 도메인 정보 목록
	 */
	@RequestMapping(value = "/getDomainInfoByNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByNm(String domainNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByNm", domainNm);
	}

	/**
	 * 도메인분류명으로 도메인 정보 조회
	 *
	 * @param clsfNm 도메인분류명
	 * @return 해당 분류의 도메인 목록
	 */
	@RequestMapping(value = "/getDomainInfoByClsfNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByClsfNm(String clsfNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByClsfNm", clsfNm);
	}

	/**
	 * 도메인 그룹 등록
	 *
	 * @param dataVo 도메인 그룹 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createDomainGroup", method = RequestMethod.POST)
	@ResponseBody
	public Response createDomainGroup(@RequestBody StdDomainGroupVo dataVo) {

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());
			// commStndYn이 null이면 기본값 'N'
			if (dataVo.getCommStndYn() == null || dataVo.getCommStndYn().isEmpty()) {
				dataVo.setCommStndYn("N");
			}

			int cnt = sqlSessionTemplate.insert("domain.insertDomainGroup", dataVo);

			if (cnt == 1) {
				result.setResultInfo(RestResult.CODE_200);
			} else {
				result.setResultInfo(RestResult.CODE_500.getCode(), "insert failed");
			}

		} catch (Exception e) {

			log.error("createDomainGroup error", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 도메인 그룹 수정
	 *
	 * @param dataVo 수정할 도메인 그룹 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateDomainGroup", method = RequestMethod.POST)
	@ResponseBody
	public Response updateDomainGroup(@RequestBody StdDomainGroupVo dataVo) {

		Response result = new Response();

		try {
			if (dataVo.getId() == null || dataVo.getId().isEmpty()) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "필수 파라미터 누락: id");
				return result;
			}

			dataVo.setUpdtUserId(sessionService.getUserId());

			int cnt = sqlSessionTemplate.update("domain.updateDomainGroup", dataVo);

			if (cnt == 1) {
				result.setResultInfo(RestResult.CODE_200);
			} else {
				result.setResultInfo(RestResult.CODE_500.getCode(), "update failed");
			}

		} catch (Exception e) {

			log.error("updateDomainGroup error", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 도메인 그룹 다건 삭제
	 *
	 * @param dataVos 삭제 대상 도메인 그룹 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteDomainGroups", method = RequestMethod.POST)
	public Mono<Response> deleteDomainGroups(@RequestBody List<StdDomainGroupVo> dataVos) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("domain.deleteDomainGroups", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteDomainGroups failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 도메인 그룹 전체 목록 조회
	 *
	 * @return 도메인 그룹 목록
	 */
	@RequestMapping(value = "/getDomainGroupList", method = RequestMethod.GET)
	public List<StdDomainGroupVo> getDomainGroupList() {
		return sqlSessionTemplate.selectList("domain.selectDomainGroupList");
	}

	/**
	 * 도메인 분류 등록
	 *
	 * @param dataVo 도메인 분류 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createDomainClassification", method = RequestMethod.POST)
	@ResponseBody
	public Response createDomainClassification(@RequestBody StdDomainClassificationVo dataVo) {

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());
			// commStndYn이 null이면 기본값 'N'
			if (dataVo.getCommStndYn() == null || dataVo.getCommStndYn().isEmpty()) {
				dataVo.setCommStndYn("N");
			}

			int cnt = sqlSessionTemplate.insert("domain.insertDomainClassification", dataVo);

			if (cnt == 1) {
				result.setResultInfo(RestResult.CODE_200);
			} else {
				result.setResultInfo(RestResult.CODE_500.getCode(), "insert failed");
			}

		} catch (Exception e) {

			log.error("createDomainClassification error", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());

		}

		return result;
	}

	/**
	 * 도메인 분류 수정
	 *
	 * @param dataVo 수정할 도메인 분류 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateDomainClassification", method = RequestMethod.POST)
	@ResponseBody
	public Response updateDomainClassification(@RequestBody StdDomainClassificationVo dataVo) {
		Response result = new Response();
		try {
			if (dataVo.getId() == null || dataVo.getId().isEmpty()) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "필수 파라미터 누락: id");
				return result;
			}
			dataVo.setUpdtUserId(sessionService.getUserId());
			int cnt = sqlSessionTemplate.update("domain.updateDomainClassification", dataVo);
			if (cnt == 1) {
				result.setResultInfo(RestResult.CODE_200);
			} else {
				result.setResultInfo(RestResult.CODE_500.getCode(), "update failed");
			}
		} catch (Exception e) {
			log.error("updateDomainClassification error", e);
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return result;
	}

	/**
	 * 도메인 분류 다건 삭제
	 *
	 * @param dataVos 삭제 대상 도메인 분류 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteDomainClassifications", method = RequestMethod.POST)
	public Mono<Response> deleteDomainClassifications(@RequestBody List<StdDomainClassificationVo> dataVos) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("domain.deleteDomainClassifications", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	/**
	 * 도메인 분류 전체 목록 조회
	 *
	 * @return 도메인 분류 목록
	 */
	@RequestMapping(value = "/getDomainClassificationList", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationList() {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationList");
	}

	/**
	 * 도메인분류명으로 도메인 분류 검색 (LIKE 검색)
	 *
	 * @param domainClsfNm 도메인분류명 키워드
	 * @return 매칭되는 도메인 분류 목록
	 */
	@RequestMapping(value = "/getDomainClassificationListByNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByNm(String domainClsfNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByNm", "%" + domainClsfNm + "%");
	}

	/**
	 * 도메인그룹명으로 도메인 분류 조회
	 *
	 * @param domainGrpNm 도메인 그룹명
	 * @return 해당 그룹의 도메인 분류 목록
	 */
	@RequestMapping(value = "/getDomainClassificationListByDomainGrpNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByGrpNm(String domainGrpNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByGrpNm", domainGrpNm);
	}

	/**
	 * 도메인 Excel 일괄 업로드 - q-executor로 전달
	 *
	 * @param request HTTP 요청
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과
	 */
	@RequestMapping(value = "/uploadDomains", method = RequestMethod.POST)
	public Mono<Response> uploadDomains(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile) {

		log.info(">> started uploadDomains file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadDomains", MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadDomains file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

	/**
	 * 도메인 목록 Excel 다운로드
	 *
	 * @param request  HTTP 요청
	 * @param response HTTP 응답
	 * @param searchKey 검색 키워드
	 */
	@RequestMapping(value = "/downloadDomains", method = RequestMethod.GET)
	public void downloadDomains(HttpServletRequest request, HttpServletResponse response, String searchKey) {
		log.info(">> download domains excel started : {}", searchKey);

		try {
			excelDownloadService.getDomainsExcel(searchKey, request, response);
		} catch (Exception e) {
			log.error(">> download domains excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 표준 승인 요청 목록 조회
	 *
	 * - 관리자: 전체 목록 조회
	 * - 일반 사용자: 자신이 신청한 내역만 조회
	 *
	 * @param retCond 검색 조건 (구분, 기간 등)
	 * @return 승인 요청 목록
	 */
	@RequestMapping(value = "/getStdAprvStatList", method = RequestMethod.POST)
	public List<StdApproveStatVo> getStdAprvStatList(@RequestBody StdApproveStatVo.RetrieveCond retCond) {
		log.info(">> getStdAprvStatList : {}", retCond);
		String reqUserId = null;
		if (!sessionService.isAdmin()) {// 관리자가 아닌 경우에는 자신 신청내역만 확인
			reqUserId = sessionService.getUserId();
			retCond.setReqUserId(reqUserId);
		}
		return sqlSessionTemplate.selectList("approve.selectStdAprvStatList", retCond);
	}

	/**
	 * 내 요청 현황 조회 API
	 * - 로그인 사용자가 등록한 항목의 승인 상태 조회
	 */
	@PostMapping("/getMyRequestList")
	public List<StdApproveStatVo> getMyRequestList(@RequestBody StdApproveStatVo.RetrieveCond retCond) {
		log.info(">> getMyRequestList : {}", retCond);
		retCond.setReqUserId(sessionService.getUserId());
		return sqlSessionTemplate.selectList("approve.selectStdAprvStatList", retCond);
	}

	/**
	 * 표준 승인/반려 처리 API (트랜잭션 사용)
	 *
	 * - 승인 대상별(TERMS, WORD, DOMAIN) APRV_YN 갱신
	 * - 승인 완료 시 변경 이력 저장
	 *
	 * @param dataVos 승인/반려 대상 목록
	 * @return 처리 결과
	 */
	@RequestMapping(value = "/putStdAprvStat", method = RequestMethod.POST)
	public Mono<Response> putStdAprvStat(@RequestBody List<StdApproveStatVo> dataVos) {
		log.info(">> putStdAprvStat : {}", dataVos);

		Response result = new Response();
		List<Map<String, Object>> warnings = new ArrayList<>();

		for (StdApproveStatVo dataVo : dataVos) {
			dataVo.setId(StringUtils.getUUID());
			dataVo.setAprvUserId(sessionService.getUserId());

			SqlSession session = sqlSessionFactory.openSession();

			try {
				NDQualityStdObjectType objType = NDQualityStdObjectType.valueOf(dataVo.getReqTp());

				// 용어 승인 시: 구성 단어 미승인 여부 체크
				if (objType == NDQualityStdObjectType.TERMS
						&& dataVo.getAprvStat() == NDQualityApproveStat.APPROVED.getValue()) {
					List<Map<String, Object>> unapprovedWords = session.selectList(
							"approve.selectUnapprovedWordsByTermsId", dataVo.getReqItemId());
					if (unapprovedWords != null && !unapprovedWords.isEmpty()) {
						List<String> wordNames = new ArrayList<>();
						for (Map<String, Object> w : unapprovedWords) {
							wordNames.add((String) w.get("wordNm"));
						}
						throw new Exception("다음 단어가 아직 승인되지 않았습니다: " + String.join(", ", wordNames)
								+ ". 해당 단어를 먼저 승인해주세요.");
					}
				}

				// 단어 반려 시: 연관 미승인 용어 경고
				if (objType == NDQualityStdObjectType.WORD
						&& dataVo.getAprvStat() == NDQualityApproveStat.REJECTED.getValue()) {
					List<Map<String, Object>> relatedTerms = session.selectList(
							"approve.selectUnapprovedTermsByWordId", dataVo.getReqItemId());
					if (relatedTerms != null && !relatedTerms.isEmpty()) {
						Map<String, Object> warning = new HashMap<>();
						warning.put("wordId", dataVo.getReqItemId());
						warning.put("wordNm", dataVo.getReqItemNm());
						warning.put("relatedTerms", relatedTerms);
						warnings.add(warning);
					}
				}

				session.insert("approve.insertStdAprvStat", dataVo);
				switch (objType) {
					case TERMS:
						session.update("approve.updateTermsAprvStat", dataVo);
						break;
					case WORD:
						session.update("approve.updateWordAprvStat", dataVo);
						break;
					case DOMAIN:
						session.update("approve.updateDomainAprvStat", dataVo);
						break;
				}
				session.commit();
				result.setResultInfo(RestResult.CODE_200);

				// 승인 완료 시(aprvStat=2) 변경 이력 저장
				if (dataVo.getAprvStat() == NDQualityApproveStat.APPROVED.getValue()) {
					String targetType = objType == NDQualityStdObjectType.TERMS ? "TERM"
							: objType == NDQualityStdObjectType.WORD ? "WORD" : "DOMAIN";
					String targetNm = dataVo.getReqItemNm();
					// 승인된 항목의 현재 값 조회
					String currValue = null;
					try {
						switch (objType) {
							case TERMS:
								Object termsObj = sqlSessionTemplate.selectOne("terms.selectTermsInfo", dataVo.getReqItemId());
								currValue = termsObj != null ? termsObj.toString() : null;
								break;
							case WORD:
								List<StdWordVo> wordInfoList = sqlSessionTemplate.selectList("word.selectWordInfoById", dataVo.getReqItemId());
								currValue = !wordInfoList.isEmpty() ? wordInfoList.get(0).toString() : null;
								if (targetNm == null && !wordInfoList.isEmpty()) targetNm = wordInfoList.get(0).getWordNm();
								break;
							case DOMAIN:
								if (targetNm != null) {
									Object domainObj = sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm", targetNm);
									currValue = domainObj != null ? domainObj.toString() : null;
								}
								break;
						}
					} catch (Exception ex) {
						log.warn("승인 이력 조회 실패: {}", ex.getMessage());
					}
					saveChangeHistory("INSERT", targetType, dataVo.getReqItemId(), targetNm,
							null, currValue, targetType + " 승인 등록: " + targetNm);
				}
			} catch (Exception e) {
				session.rollback();
				log.error("update standard approve stat : {}", e.getMessage());
				result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			} finally {
				session.close();
			}
		}

		// 단어 반려 시 연관 미승인 용어 경고 정보를 응답에 포함
		if (!warnings.isEmpty() && result.getResultCode() == 200) {
			StringBuilder warnMsg = new StringBuilder();
			for (Map<String, Object> w : warnings) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> relTerms = (List<Map<String, Object>>) w.get("relatedTerms");
				List<String> termNames = new ArrayList<>();
				for (Map<String, Object> t : relTerms) {
					termNames.add((String) t.get("termsNm"));
				}
				warnMsg.append("단어 '").append(w.get("wordNm")).append("' 반려 시 연관 미승인 용어: ")
						.append(String.join(", ", termNames)).append(". ");
			}
			result.setResultInfo(RestResult.CODE_200.getCode(), warnMsg.toString());
		}

		return Mono.just(result);
	}

	/**
	 * 연관 미승인 용어 조회 API (단어 반려 시 프론트에서 사전 확인용)
	 */
	@GetMapping("/getRelatedUnapprovedTerms")
	public List<Map<String, Object>> getRelatedUnapprovedTerms(@RequestParam String wordId) {
		return sqlSessionTemplate.selectList("approve.selectUnapprovedTermsByWordId", wordId);
	}

	/**
	 * 표준화 추천 - 용어 일괄 분석 API
	 *
	 * <p>입력된 한글 용어명 목록을 형태소 분석하여 다음을 수행한다:</p>
	 * <ol>
	 *   <li>기등록 용어 체크 (REGISTERED)</li>
	 *   <li>OKT 형태소 분석 + TB_WORD/TB_WORD_DICT 기반 greedy 분리</li>
	 *   <li>1순위/2순위 단어 분리 + 영문약어 자동 조합</li>
	 *   <li>마지막 단어 기준 도메인 추천</li>
	 * </ol>
	 *
	 * @param body { termNames: ["고객번호", "주문일자", ...] }
	 * @return 용어별 분석 결과 목록 (상태: AUTO/PARTIAL/FAILED/REGISTERED)
	 */
	@RequestMapping(value = "/analyzeTermsBatch", method = RequestMethod.POST)
	public List<TermAnalysisResult> analyzeTermsBatch(@RequestBody Map<String, List<String>> body) {
		List<String> termNames = body.get("termNames");
		if (termNames == null) termNames = new ArrayList<>();
		log.info(">> analyzeTermsBatch started, count: {}", termNames.size());

		// 캐시: 전체 용어/단어/도메인 로드
		List<StdTermsVo> allTerms = sqlSessionTemplate.selectList("terms.selectAllTermsByNm");
		Map<String, StdTermsVo> termsByNm = new HashMap<>();
		for (StdTermsVo t : allTerms) { termsByNm.put(t.getTermsNm(), t); }

		List<StdWordVo> allWords = sqlSessionTemplate.selectList("word.selectAllWords");
		Map<String, List<StdWordVo>> wordsByNm = new HashMap<>();
		for (StdWordVo w : allWords) {
			wordsByNm.computeIfAbsent(w.getWordNm(), k -> new ArrayList<>()).add(w);
		}

		List<Map<String, Object>> usageCounts = sqlSessionTemplate.selectList("word.selectWordUsageCounts");
		Map<String, Integer> usageMap = new HashMap<>();
		for (Map<String, Object> row : usageCounts) {
			String nm = (String) row.get("wordNm");
			Number cnt = (Number) row.get("cnt");
			if (nm != null && cnt != null) usageMap.put(nm, cnt.intValue());
		}

		List<StdDomainVo> allDomains = sqlSessionTemplate.selectList("domain.selectAllDomains");
		Map<String, List<StdDomainVo>> domainsByClsf = new HashMap<>();
		for (StdDomainVo d : allDomains) {
			String clsf = d.getDomainClsfNm() != null ? d.getDomainClsfNm() : "";
			domainsByClsf.computeIfAbsent(clsf, k -> new ArrayList<>()).add(d);
		}

		// 추천 사전 로드 (TB_WORD_DICT)
		List<Map<String, Object>> dictRows = sqlSessionTemplate.selectList("word.selectAllWordDict");
		Map<String, Map<String, Object>> wordDict = new HashMap<>();
		for (Map<String, Object> row : dictRows) {
			String kor = (String) row.get("wordKor");
			if (kor != null) wordDict.put(kor, row);
		}
		log.info("[TermAnalysis] wordDict loaded: {} entries, wordsByNm: {} entries", wordDict.size(), wordsByNm.size());

		List<TermAnalysisResult> results = new ArrayList<>();

		for (String name : termNames) {
			String cleanName = name.replaceAll("\\s", "");
			if (cleanName.isEmpty()) continue;

			TermAnalysisResult result = new TermAnalysisResult();
			result.setInputNm(cleanName);

			// 기등록 체크
			if (termsByNm.containsKey(cleanName)) {
				result.setStatus("REGISTERED");
				result.setExistingTerm(termsByNm.get(cleanName));
				results.add(result);
				continue;
			}

			// 형태소 분석
			Map<String, String> tokens;
			try {
				tokens = StringWordAnalyzer.getWordsFromStringByOkt(cleanName, null, false);
			} catch (Exception e) {
				log.error("Tokenization failed for: {}", cleanName, e);
				result.setStatus("FAILED");
				result.setWords(new ArrayList<>());
				results.add(result);
				continue;
			}

			if (tokens == null || tokens.isEmpty()) {
				log.info("[TermAnalysis] OKT returned empty for: {}", cleanName);
				result.setStatus("FAILED");
				result.setWords(new ArrayList<>());
				results.add(result);
				continue;
			}

			log.info("[TermAnalysis] OKT tokens for '{}': {}", cleanName, tokens);

			// NN 토큰만 추출 (중복 제거, #번호 제거)
			List<String> nnTokens = new ArrayList<>();
			for (Map.Entry<String, String> entry : tokens.entrySet()) {
				if ("NN".equals(entry.getValue())) {
					String t = entry.getKey().replaceAll("#\\d+$", "");
					if (!nnTokens.contains(t)) nnTokens.add(t);
				}
			}

			log.info("[TermAnalysis] NN tokens for '{}': {}", cleanName, nnTokens);

			// 후보 토큰 세트 구성 (OKT + TB_WORD에서 입력에 포함된 단어)
			Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
			for (String wordNm : wordsByNm.keySet()) {
				if (wordNm.length() >= 2 && cleanName.contains(wordNm)) {
					candidateSet.add(wordNm);
				}
			}

			// 1순위: TB_WORD > DICT > OKT longest
			List<String> primaryTokens = dpSplit(cleanName, candidateSet, wordsByNm, wordDict);
			log.info("[TermAnalysis] 1순위 for '{}': {}", cleanName, primaryTokens);

			// 2순위: 1순위에서 미등록 단어를 추가 분리
			List<String> altTokens = generateAlternativeSplit(primaryTokens, candidateSet, wordsByNm);
			log.info("[TermAnalysis] 2순위 for '{}': {}", cleanName, altTokens);

			// 1순위 토큰 → WordAnalysis 변환
			List<TermAnalysisResult.WordAnalysis> wordAnalyses = buildWordAnalyses(primaryTokens, wordsByNm, wordDict, usageMap);
			result.setWords(wordAnalyses);
			result.setRecommendedEngAbrvNm(composeEngAbrv(wordAnalyses));

			// 2순위가 1순위와 다르면 세팅
			if (!primaryTokens.equals(altTokens)) {
				List<TermAnalysisResult.WordAnalysis> altAnalyses = buildWordAnalyses(altTokens, wordsByNm, wordDict, usageMap);
				result.setAlternativeWords(altAnalyses);
				result.setAlternativeEngAbrvNm(composeEngAbrv(altAnalyses));
			}

			// 도메인 추천 (마지막 단어 기준)
			if (!wordAnalyses.isEmpty()) {
				TermAnalysisResult.WordAnalysis lastWord = wordAnalyses.get(wordAnalyses.size() - 1);
				String clsfNm = null;
				if ("MATCHED".equals(lastWord.getStatus()) && lastWord.getSelected() != null) {
					clsfNm = lastWord.getSelected().getDomainClsfNm();
				}
				if (clsfNm != null && domainsByClsf.containsKey(clsfNm)) {
					List<StdDomainVo> domCandidates = domainsByClsf.get(clsfNm);
					result.setDomainCandidates(domCandidates);
					if (!domCandidates.isEmpty()) {
						StdDomainVo first = domCandidates.get(0);
						result.setRecommendedDomainNm(first.getDomainNm());
						result.setRecommendedDataType(first.getDataType());
						result.setRecommendedDataLen(first.getDataLen());
					}
				}
			}

			// 상태 판정
			result.setStatus(judgeStatus(wordAnalyses));
			results.add(result);
		}

		return results;
	}

	/**
	 * 단어 단건 등록 API (표준화 추천에서 NEW 단어 선등록용)
	 */
	@RequestMapping(value = "/registerWord", method = RequestMethod.POST)
	public Map<String, Object> registerWord(@RequestBody Map<String, Object> body) {
		Map<String, Object> res = new HashMap<>();
		String wordNm = (String) body.get("wordNm");
		String wordEngAbrvNm = (String) body.get("wordEngAbrvNm");
		String wordEngNm = (String) body.get("wordEngNm");
		String wordDesc = (String) body.get("wordDesc");
		String domainClsfNm = (String) body.get("domainClsfNm");

		if (wordNm == null || wordNm.trim().isEmpty()
				|| wordEngAbrvNm == null || wordEngAbrvNm.trim().isEmpty()
				|| wordEngNm == null || wordEngNm.trim().isEmpty()) {
			res.put("success", false);
			res.put("message", "한글명, 영문약어, 영문명은 필수입니다.");
			return res;
		}

		// 영문약어 규격 체크: 대문자 영문(A-Z) + 숫자(0-9)만 허용
		if (!wordEngAbrvNm.trim().matches("^[A-Z0-9]+$")) {
			res.put("success", false);
			res.put("message", "단어 영문약어는 대문자 영문(A-Z)과 숫자(0-9)만 사용할 수 있습니다. (입력값: " + wordEngAbrvNm + ")");
			return res;
		}

		// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
		String forbiddenMsg = checkForbiddenWord(wordNm.trim());
		if (forbiddenMsg != null) {
			res.put("success", false);
			res.put("message", forbiddenMsg);
			return res;
		}

		// 중복 체크
		List<StdWordVo> existing = sqlSessionTemplate.selectList("word.selectWordInfoByNm", wordNm.trim());
		if (existing != null && !existing.isEmpty()) {
			// 이미 등록된 단어 → 기존 정보 반환
			StdWordVo existWord = existing.get(0);
			res.put("success", true);
			res.put("alreadyExists", true);
			res.put("wordId", existWord.getId());
			res.put("wordNm", existWord.getWordNm());
			res.put("wordEngAbrvNm", existWord.getWordEngAbrvNm());
			res.put("wordEngNm", existWord.getWordEngNm());
			res.put("domainClsfNm", existWord.getDomainClsfNm());
			return res;
		}

		// 유사어 체크: 경고 메시지만 (등록은 진행)
		String synonymMsg = checkSynonymWord(wordNm.trim());

		String userId = sessionService.getUserId();
		boolean isAdmin = sessionService.isAdmin();

		StdWordVo wordVo = new StdWordVo();
		String wordId = StringUtils.getUUID();
		wordVo.setId(wordId);
		wordVo.setWordNm(wordNm.trim());
		wordVo.setWordEngAbrvNm(wordEngAbrvNm.trim().toUpperCase());
		wordVo.setWordEngNm(wordEngNm.trim());
		wordVo.setWordDesc(wordDesc != null ? wordDesc.trim() : wordNm.trim());
		wordVo.setDomainClsfNm(domainClsfNm != null ? domainClsfNm.trim() : "");
		wordVo.setWordClsfYn("N");
		wordVo.setCommStndYn("N");
		wordVo.setCretUserId(userId);
		if (isAdmin) wordVo.setAprvYn("Y");
		else wordVo.setAprvYn("N");

		sqlSessionTemplate.insert("word.insertWord", wordVo);

		// 이력 저장 (관리자 즉시 승인 시에만)
		if (isAdmin) {
			saveChangeHistory("INSERT", "WORD", wordId, wordVo.getWordNm(),
					null, wordVo.toString(), "단어 등록(표준화 추천): " + wordVo.getWordNm());
		}

		res.put("success", true);
		res.put("alreadyExists", false);
		res.put("wordId", wordId);
		res.put("wordNm", wordVo.getWordNm());
		res.put("wordEngAbrvNm", wordVo.getWordEngAbrvNm());
		res.put("wordEngNm", wordVo.getWordEngNm());
		res.put("domainClsfNm", wordVo.getDomainClsfNm());
		if (synonymMsg != null) {
			res.put("warning", synonymMsg);
		}
		return res;
	}

	/**
	 * 표준화 추천 - 용어 일괄 등록 API (트랜잭션 사용)
	 *
	 * <p>분석 결과를 바탕으로 신규 단어 등록 + 용어 등록을 한 번에 처리한다.</p>
	 * <ul>
	 *   <li>신규 단어: 금칙어 체크 후 자동 등록</li>
	 *   <li>구성 단어 승인 여부 체크 (미승인 단어 포함 시 실패)</li>
	 *   <li>도메인 유효성 체크</li>
	 *   <li>관리자: 일괄 등록 변경이력(BULK_INSERT) 저장</li>
	 * </ul>
	 *
	 * @param body { items: [{termsNm, termsEngAbrvNm, words, newWords, ...}] }
	 * @return 등록 결과 (registeredTerms, registeredWords, skipped, failed, details)
	 */
	@RequestMapping(value = "/registerTermsBatch", method = RequestMethod.POST)
	public Map<String, Object> registerTermsBatch(@RequestBody Map<String, Object> body) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
		if (items == null || items.isEmpty()) {
			Map<String, Object> res = new HashMap<>();
			res.put("success", 0);
			res.put("fail", 0);
			res.put("details", new ArrayList<>());
			return res;
		}

		String userId = sessionService.getUserId();
		boolean isAdmin = sessionService.isAdmin();
		int registeredTerms = 0;
		int registeredWords = 0;
		int skipped = 0;
		int failed = 0;
		List<Map<String, Object>> details = new ArrayList<>();

		for (Map<String, Object> item : items) {
			String termsNm = (String) item.get("termsNm");
			String termsEngAbrvNm = (String) item.get("termsEngAbrvNm");
			String termsDesc = (String) item.get("termsDesc");
			String domainNm = (String) item.get("domainNm");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> words = (List<Map<String, Object>>) item.get("words");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> newWords = (List<Map<String, Object>>) item.get("newWords");

			SqlSession session = sqlSessionFactory.openSession();
			try {
				// 중복 체크
				if (session.selectOne("terms.selectTermsByNm", termsNm) != null) {
					skipped++;
					Map<String, Object> detail = new HashMap<>();
					detail.put("termsNm", termsNm);
					detail.put("status", "SKIPPED");
					detail.put("message", "이미 등록된 용어");
					details.add(detail);
					continue;
				}

				// 신규 단어 등록
				int newWordCount = 0;
				Map<Integer, String> newWordIdMap = new HashMap<>();
				if (newWords != null) {
					for (int i = 0; i < newWords.size(); i++) {
						Map<String, Object> nw = newWords.get(i);
						String wordNm = (String) nw.get("wordNm");
						String wordEngAbrvNm = (String) nw.get("wordEngAbrvNm");
						String wordEngNm = (String) nw.get("wordEngNm");
						if (wordNm == null || wordNm.trim().isEmpty()
								|| wordEngAbrvNm == null || wordEngAbrvNm.trim().isEmpty()
								|| wordEngNm == null || wordEngNm.trim().isEmpty()) {
							throw new RuntimeException("신규 단어 필수 항목 누락: 한글명, 영문약어, 영문명은 필수입니다. (단어: " + wordNm + ")");
						}
						// 금칙어 체크
						String forbiddenMsg = checkForbiddenWord(wordNm.trim());
						if (forbiddenMsg != null) {
							throw new RuntimeException(forbiddenMsg);
						}
						StdWordVo wordVo = new StdWordVo();
						String wordId = StringUtils.getUUID();
						wordVo.setId(wordId);
						wordVo.setWordNm((String) nw.get("wordNm"));
						wordVo.setWordEngAbrvNm((String) nw.get("wordEngAbrvNm"));
						wordVo.setWordEngNm((String) nw.get("wordEngNm"));
						wordVo.setWordDesc((String) nw.get("wordDesc"));
						wordVo.setDomainClsfNm((String) nw.get("domainClsfNm"));
						wordVo.setWordClsfYn("N");
						wordVo.setCommStndYn("N");
						wordVo.setCretUserId(userId);
						if (isAdmin) wordVo.setAprvYn("Y");
						else wordVo.setAprvYn("N");
						session.insert("word.insertWord", wordVo);
						newWordIdMap.put(i, wordId);
						newWordCount++;
					}
				}

				// 구성 단어 승인 여부 체크는 용어 승인 시점에 수행 (등록은 자유)

				// 분류어 검증: 용어는 최소 2개 이상의 단어로 구성되어야 하며, 마지막 단어는 분류어여야 함
				if (words == null || words.size() < 2) {
					throw new RuntimeException("용어는 최소 2개 이상의 단어로 구성되어야 합니다");
				}
				Map<String, Object> lastWordEntry = words.get(words.size() - 1);
				String lastWordId = (String) lastWordEntry.get("wordId");
				if (lastWordId == null) {
					// 신규 단어인 경우 newWordIndex로 ID 가져오기
					Number lastNewWordIndex = (Number) lastWordEntry.get("newWordIndex");
					if (lastNewWordIndex != null) {
						lastWordId = newWordIdMap.get(lastNewWordIndex.intValue());
					}
				}
				if (lastWordId != null) {
					List<StdWordVo> lastWordInfo = session.selectList("word.selectWordInfoById", lastWordId);
					if (!lastWordInfo.isEmpty() && !"Y".equals(lastWordInfo.get(0).getWordClsfYn())) {
						throw new RuntimeException("용어의 마지막 단어는 분류어여야 합니다. (현재: " + lastWordInfo.get(0).getWordNm() + ")");
					}
				}

				// 도메인 유효성 체크
				if (domainNm != null && !domainNm.trim().isEmpty()) {
					Object domainCheck = session.selectOne("domain.selectDomainInfoByNm", domainNm.trim());
					if (domainCheck == null) {
						throw new RuntimeException("등록되지 않은 도메인입니다: " + domainNm);
					}
				} else {
					domainNm = null; // 빈 문자열을 null로 변환하여 FK 에러 방지
				}

				// 용어 등록
				StdTermsVo termsVo = new StdTermsVo();
				String termsId = StringUtils.getUUID();
				termsVo.setId(termsId);
				termsVo.setTermsNm(termsNm);
				termsVo.setTermsEngAbrvNm(termsEngAbrvNm);
				termsVo.setTermsDesc(termsDesc != null ? termsDesc : termsNm);
				termsVo.setDomainNm(domainNm);
				termsVo.setCommStndYn("N");
				termsVo.setCretUserId(userId);
				if (isAdmin) termsVo.setAprvYn("Y");
				session.insert("terms.insertTerms", termsVo);

				// 용어-단어 관계 등록
				if (words != null && !words.isEmpty()) {
					List<StdTermsVo.Word> wordList = new ArrayList<>();
					for (Map<String, Object> w : words) {
						StdTermsVo.Word tw = new StdTermsVo.Word();
						tw.setTermsId(termsId);
						Number wordOrd = (Number) w.get("wordOrd");
						tw.setWordOrd(wordOrd != null ? wordOrd.shortValue() : (short) 0);

						String wordId = (String) w.get("wordId");
						if (wordId != null) {
							tw.setWordId(wordId);
						} else {
							// 신규 단어 매핑
							Number newWordIndex = (Number) w.get("newWordIndex");
							if (newWordIndex != null) {
								tw.setWordId(newWordIdMap.get(newWordIndex.intValue()));
							}
						}
						// wordNm은 selectWordInfoById로 조회하지 않고, 단어 테이블에서 가져와야 하지만
						// insertTermsWords에 필요하므로 간단히 처리
						if (tw.getWordId() != null) {
							List<StdWordVo> wordInfo = session.selectList("word.selectWordInfoById", tw.getWordId());
							if (!wordInfo.isEmpty()) {
								tw.setWordNm(wordInfo.get(0).getWordNm());
							}
						}
						wordList.add(tw);
					}
					if (!wordList.isEmpty()) {
						session.insert("terms.insertTermsWords", wordList);
					}
				}

				session.commit();
				registeredTerms++;
				registeredWords += newWordCount;

				Map<String, Object> detail = new HashMap<>();
				detail.put("termsNm", termsNm);
				detail.put("status", "SUCCESS");
				details.add(detail);
			} catch (Exception e) {
				session.rollback();
				failed++;
				log.error("registerTermsBatch item failed: {} - {}", termsNm, e.getMessage());

				Map<String, Object> detail = new HashMap<>();
				detail.put("termsNm", termsNm);
				detail.put("status", "FAIL");
				detail.put("message", e.getMessage());
				details.add(detail);
			} finally {
				session.close();
			}
		}

		// 일괄 등록 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
		if (isAdmin) {
			try {
				String changeId = StringUtils.getUUID();
				Map<String, Object> history = new HashMap<>();
				history.put("changeId", changeId);
				history.put("changeType", "BULK_INSERT");
				history.put("targetType", "TERM");
				history.put("changeCnt", registeredTerms);
				history.put("summary", String.format("용어 일괄등록(표준화 추천) %d건 (성공:%d, 건너뜀:%d, 실패:%d)", items.size(), registeredTerms, skipped, failed));
				history.put("changeUserId", userId);
				sqlSessionTemplate.insert("changehistory.insertChangeHistory", history);
				// 상세 건 저장
				int seq = 1;
				for (Map<String, Object> detail : details) {
					Map<String, Object> historyDetail = new HashMap<>();
					historyDetail.put("changeId", changeId);
					historyDetail.put("seq", seq++);
					historyDetail.put("targetNm", detail.get("termsNm"));
					historyDetail.put("detailType", detail.get("status"));
					historyDetail.put("remark", detail.get("message"));
					sqlSessionTemplate.insert("changehistory.insertChangeHistoryDetail", historyDetail);
				}
			} catch (Exception e) {
				log.warn("일괄등록 이력 저장 실패: {}", e.getMessage());
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("registeredTerms", registeredTerms);
		result.put("registeredWords", registeredWords);
		result.put("skipped", skipped);
		result.put("failed", failed);
		result.put("details", details);
		return result;
	}

	/**
	 * Excel 파일에서 컬럼명(한글명) 목록을 파싱하여 반환 (표준화 추천용)
	 *
	 * - 첫 번째 시트의 A열에서 값을 읽음
	 * - 헤더행 자동 감지 ("컬럼", "용어", "한글" 등 포함 시 건너뜀)
	 *
	 * @param file 업로드 Excel 파일
	 * @return 파싱된 컬럼명 목록
	 */
	@PostMapping(value = "/parseExcelColumnNames", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public List<String> parseExcelColumnNames(@RequestParam("file") MultipartFile file) {
		List<String> columnNames = new ArrayList<>();
		try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
			Sheet sheet = workbook.getSheetAt(0);
			// 첫 번째 행이 헤더인지 확인 - 헤더가 있으면 건너뜀
			int startRow = 0;
			Row firstRow = sheet.getRow(0);
			if (firstRow != null) {
				Cell firstCell = firstRow.getCell(0);
				if (firstCell != null) {
					String val = getCellStringValue(firstCell).trim();
					// 헤더 판별: "컬럼", "용어", "한글명" 등이면 헤더로 간주
					if (val.contains("컬럼") || val.contains("용어") || val.contains("한글") || val.contains("이름") || val.contains("명칭")) {
						startRow = 1;
					}
				}
			}
			for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) continue;
				Cell cell = row.getCell(0);
				if (cell == null) continue;
				String value = getCellStringValue(cell).trim();
				if (!value.isEmpty()) {
					columnNames.add(value);
				}
			}
		} catch (Exception e) {
			log.error("Excel parsing failed: {}", e.getMessage());
		}
		return columnNames;
	}

	private String getCellStringValue(Cell cell) {
		if (cell.getCellType() == CellType.STRING) {
			return cell.getStringCellValue();
		} else if (cell.getCellType() == CellType.NUMERIC) {
			return String.valueOf((long) cell.getNumericCellValue());
		}
		return "";
	}

	/**
	 * DP 기반 최적 단어 분리
	 *
	 * 입력 문자열의 모든 위치에서 모든 가능한 토큰을 시도하고,
	 * 총 점수가 가장 높은 분리를 선택한다.
	 *
	 * @param input      입력 문자열 (공백 제거됨)
	 * @param candidates OKT 토큰 후보 세트
	 * @param wordsByNm  TB_WORD 등록 단어 맵
	 * @param wordDict   TB_WORD_DICT 추천 사전 맵
	 * @return 최적 분리된 토큰 목록
	 */
	private List<String> dpSplit(String input, Set<String> candidates,
			Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		int n = input.length();
		if (n == 0) return new ArrayList<>();

		int[] dp = new int[n + 1];
		int[] parent = new int[n + 1];
		String[] token = new String[n + 1];
		Arrays.fill(dp, Integer.MIN_VALUE / 2);
		Arrays.fill(parent, -1);
		dp[0] = 0;

		for (int i = 0; i < n; i++) {
			if (dp[i] == Integer.MIN_VALUE / 2) continue;

			// 모든 후보 토큰 시도
			for (String c : candidates) {
				if (c.equals(input)) continue; // 전체 입력과 동일한 토큰 제외
				if (i + c.length() <= n && input.startsWith(c, i)) {
					int score = calculateTokenScore(c, wordsByNm, wordDict);
					if (dp[i] + score > dp[i + c.length()]) {
						dp[i + c.length()] = dp[i] + score;
						parent[i + c.length()] = i;
						token[i + c.length()] = c;
					}
				}
			}

			// 1글자 스킵 (매칭 안 될 때)
			String oneChar = input.substring(i, i + 1);
			int skipScore = dp[i] - 500;
			if (skipScore > dp[i + 1]) {
				dp[i + 1] = skipScore;
				parent[i + 1] = i;
				token[i + 1] = oneChar;
			}
		}

		// 역추적
		List<String> result = new ArrayList<>();
		int pos = n;
		while (pos > 0 && parent[pos] >= 0) {
			result.add(0, token[pos]);
			pos = parent[pos];
		}
		// 도달 못한 경우 전체를 하나로
		if (result.isEmpty()) {
			result.add(input);
		}
		return result;
	}

	/**
	 * DP 분리용 토큰 점수 계산
	 *
	 * TB_WORD 등록 단어 > TB_WORD_DICT 추천 사전 > OKT 토큰 순으로 점수 부여.
	 * 같은 유형 내에서는 긴 토큰이 더 높은 점수를 받는다.
	 */
	private int calculateTokenScore(String token, Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		if (wordsByNm.containsKey(token)) {
			return 10000 + token.length() * 100;
		}
		if (wordDict.containsKey(token)) {
			return 5000 + token.length() * 100;
		}
		int len = token.length();
		if (len >= 2 && len <= 4) return 500 + len * 50;
		if (len >= 5) return 100;
		return 10;
	}

	/**
	 * 입력 문자열을 순서대로 커버하는 최적 토큰 조합을 선택한다.
	 * - 등록된 단어(wordsByNm에 존재)를 우선 매칭
	 * - 같은 조건이면 긴 토큰 우선 (greedy)
	 * - 예: "API코드" → ["API", "코드"] (등록단어), "API코드"나 "코", "드" 등은 제외
	 */
	/**
	 * 1순위 분리: TB_WORD > DICT > OKT longest 순으로 greedy 매칭 (fallback용)
	 */
	private List<String> greedySplit(String input, Set<String> candidates,
			Map<String, List<StdWordVo>> wordsByNm, Map<String, Map<String, Object>> wordDict) {
		List<String> result = new ArrayList<>();
		Set<String> dictKeys = wordDict.keySet();
		String remaining = input;
		int maxIter = 100;

		while (!remaining.isEmpty() && maxIter-- > 0) {
			String selected = null;

			// 1. TB_WORD longest match
			for (String c : candidates) {
				if (wordsByNm.containsKey(c) && remaining.startsWith(c) && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 2. DICT longest match
			for (String c : candidates) {
				if (dictKeys.contains(c) && remaining.startsWith(c) && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 3. remaining 자체가 OKT 토큰이면 사용 (1글자 마지막 토큰 처리)
			if (candidates.contains(remaining) && !remaining.equals(input)) {
				result.add(remaining); remaining = ""; continue;
			}

			// 4. OKT longest 2+ chars
			for (String c : candidates) {
				if (c.length() >= 2 && remaining.startsWith(c) && c.length() < remaining.length() && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 5. 매칭 불가 → 다음 매칭점까지 스킵
			boolean skipped = false;
			for (int skip = 1; skip < remaining.length(); skip++) {
				String sub = remaining.substring(skip);
				for (String c : candidates) {
					if (sub.startsWith(c) && !c.equals(input)) { skipped = true; break; }
				}
				if (skipped) { result.add(remaining.substring(0, skip)); remaining = remaining.substring(skip); break; }
			}
			if (!skipped) { result.add(remaining); remaining = ""; }
		}
		return result;
	}

	/**
	 * 2순위 생성: 1순위에서 미등록(TB_WORD에 없는) 토큰을 추가 분리
	 */
	private List<String> generateAlternativeSplit(List<String> primarySplit, Set<String> candidates, Map<String, List<StdWordVo>> wordsByNm) {
		List<String> alt = new ArrayList<>();
		for (String token : primarySplit) {
			if (wordsByNm.containsKey(token) || token.length() < 3) {
				alt.add(token);
				continue;
			}
			// 미등록 토큰을 shortest 2+ 로 재분리
			List<String> sub = subSplit(token, candidates);
			if (sub.size() > 1) {
				alt.addAll(sub);
			} else {
				alt.add(token);
			}
		}
		return alt;
	}

	private List<String> subSplit(String token, Set<String> candidates) {
		List<String> result = new ArrayList<>();
		String remaining = token;
		int maxIter = 50;
		while (!remaining.isEmpty() && maxIter-- > 0) {
			String best = null;
			for (String c : candidates) {
				if (c.length() >= 2 && remaining.startsWith(c) && c.length() < remaining.length()) {
					if (best == null || c.length() < best.length()) best = c;
				}
			}
			if (best != null) { result.add(best); remaining = remaining.substring(best.length()); continue; }
			if (candidates.contains(remaining)) { result.add(remaining); remaining = ""; continue; }
			result.add(remaining); remaining = "";
		}
		return result;
	}

	/**
	 * 토큰 목록 → WordAnalysis 목록 변환
	 */
	private List<TermAnalysisResult.WordAnalysis> buildWordAnalyses(List<String> tokens,
			Map<String, List<StdWordVo>> wordsByNm, Map<String, Map<String, Object>> wordDict,
			Map<String, Integer> usageMap) {
		List<TermAnalysisResult.WordAnalysis> list = new ArrayList<>();
		for (String token : tokens) {
			TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
			wa.setWordNm(token);

			List<StdWordVo> matches = wordsByNm.get(token);
			if (matches != null && !matches.isEmpty()) {
				wa.setStatus("MATCHED");
				List<TermAnalysisResult.WordCandidate> cands = new ArrayList<>();
				TermAnalysisResult.WordCandidate best = null;
				int bestScore = -1;
				for (StdWordVo w : matches) {
					TermAnalysisResult.WordCandidate c = new TermAnalysisResult.WordCandidate();
					c.setWordId(w.getId());
					c.setWordNm(w.getWordNm());
					c.setWordEngAbrvNm(w.getWordEngAbrvNm());
					c.setWordEngNm(w.getWordEngNm());
					c.setDomainClsfNm(w.getDomainClsfNm());
					int score = 0;
					Integer usage = usageMap.get(w.getWordNm());
					if (usage != null) score += usage * 100;
					if (w.getWordEngAbrvNm() != null) score += Math.min(w.getWordEngAbrvNm().length(), 10) * 10;
					c.setScore(score);
					if (score > bestScore) { bestScore = score; best = c; }
					cands.add(c);
				}
				cands.sort((a, b) -> b.getScore() - a.getScore());
				if (best != null) best.setSelected(true);
				wa.setCandidates(cands);
				wa.setSelected(best);
			} else {
				Map<String, Object> dictEntry = wordDict.get(token);
				if (dictEntry != null) {
					wa.setStatus("NEW");
					wa.setCandidates(new ArrayList<>());
					TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
					suggestion.setWordEngAbrvNm(dictEntry.get("wordAbrv") != null ? (String) dictEntry.get("wordAbrv") : "");
					suggestion.setWordEngNm(dictEntry.get("wordEng") != null ? (String) dictEntry.get("wordEng") : "");
					suggestion.setDomainClsfNm(dictEntry.get("domainClsfNm") != null ? (String) dictEntry.get("domainClsfNm") : "");
					wa.setNewWord(suggestion);
				} else {
					wa.setStatus("UNRECOGNIZED");
					wa.setCandidates(new ArrayList<>());
					TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
					suggestion.setWordEngAbrvNm("");
					suggestion.setWordEngNm("");
					suggestion.setDomainClsfNm("");
					wa.setNewWord(suggestion);
				}
			}
			list.add(wa);
		}
		return list;
	}

	private String composeEngAbrv(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
		StringBuilder sb = new StringBuilder();
		for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
			if (sb.length() > 0) sb.append("_");
			if ("MATCHED".equals(wa.getStatus()) && wa.getSelected() != null) {
				sb.append(wa.getSelected().getWordEngAbrvNm());
			} else if (wa.getNewWord() != null) {
				sb.append(wa.getNewWord().getWordEngAbrvNm());
			}
		}
		return sb.toString();
	}

	private String judgeStatus(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
		boolean hasMatched = false, hasNew = false;
		for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
			if ("MATCHED".equals(wa.getStatus())) hasMatched = true;
			else hasNew = true;
		}
		if (wordAnalyses.isEmpty() || !hasMatched) return "FAILED";
		if (hasNew) return "PARTIAL";
		return "AUTO";
	}

	// ==================== 변경 이력 관리 ====================

	/**
	 * 변경 이력 목록 조회 - 기간/유형/대상별 필터링
	 *
	 * @param params 검색 조건 (changeType, targetType, fromDt, toDt 등)
	 * @return 변경 이력 목록
	 */
	@PostMapping("/getChangeHistoryList")
	public List<Map<String, Object>> getChangeHistoryList(@RequestBody Map<String, Object> params) {
		return sqlSessionTemplate.selectList("changehistory.selectChangeHistoryList", params);
	}

	/**
	 * 변경 이력 상세 조회 (마스터 + 상세 목록)
	 *
	 * @param changeId 변경 이력 ID
	 * @return { history: 마스터 정보, details: 상세 목록 }
	 */
	@GetMapping("/getChangeHistoryDetail")
	public Map<String, Object> getChangeHistoryDetail(@RequestParam String changeId) {
		Map<String, Object> result = new HashMap<>();
		result.put("history", sqlSessionTemplate.selectOne("changehistory.selectChangeHistoryById", changeId));
		result.put("details", sqlSessionTemplate.selectList("changehistory.selectChangeHistoryDetailList", changeId));
		return result;
	}

	/**
	 * 특정 대상의 변경 이력 조회 (단어/용어/도메인 등 대상 ID 기준)
	 *
	 * @param params { targetType, targetId }
	 * @return 해당 대상의 변경 이력 목록
	 */
	@PostMapping("/getChangeHistoryByTarget")
	public List<Map<String, Object>> getChangeHistoryByTarget(@RequestBody Map<String, Object> params) {
		return sqlSessionTemplate.selectList("changehistory.selectChangeHistoryByTarget", params);
	}

	/**
	 * 입력된 단어명이 기존 단어의 금칙어 목록에 포함되는지 체크
	 * @return 금칙어에 해당하면 에러 메시지, 아니면 null
	 */
	private String checkForbiddenWord(String wordNm) {
		if (wordNm == null || wordNm.trim().isEmpty()) return null;
		Map<String, Object> found = sqlSessionTemplate.selectOne("word.selectWordByForbiddenNm", wordNm.trim());
		if (found != null) {
			String stdWordNm = (String) found.get("wordNm");
			return "'" + wordNm.trim() + "'은(는) '" + stdWordNm + "'의 금칙어입니다. '" + stdWordNm + "'를 사용해주세요.";
		}
		return null;
	}

	/**
	 * 입력된 단어명이 기존 단어의 유사어 목록에 포함되는지 체크
	 * @return 유사어에 해당하면 안내 메시지, 아니면 null
	 */
	private String checkSynonymWord(String wordNm) {
		if (wordNm == null || wordNm.trim().isEmpty()) return null;
		Map<String, Object> found = sqlSessionTemplate.selectOne("word.selectWordBySynonymNm", wordNm.trim());
		if (found != null) {
			String stdWordNm = (String) found.get("wordNm");
			return "'" + wordNm.trim() + "'은(는) '" + stdWordNm + "'의 유사어입니다. '" + stdWordNm + "' 사용을 권장합니다.";
		}
		return null;
	}

	// ========== 영향도 분석 ==========

	/**
	 * 단어 영향도 분석 - 해당 단어를 사용하는 용어/컬럼 목록 반환
	 *
	 * @param wordId 단어 고유 ID
	 * @return { terms: 참조 용어 목록, columns: 참조 컬럼 목록 }
	 */
	@GetMapping("/impact/word")
	public Map<String, Object> getWordImpact(@RequestParam String wordId) {
		Map<String, Object> result = new HashMap<>();
		result.put("terms", sqlSessionTemplate.selectList("impact.selectImpactTermsByWordId", wordId));
		result.put("columns", sqlSessionTemplate.selectList("impact.selectImpactColumnsByWordId", wordId));
		return result;
	}

	/**
	 * 도메인 영향도 분석 - 해당 도메인을 사용하는 용어 목록 반환
	 *
	 * @param domainNm 도메인명
	 * @return { terms: 참조 용어 목록 }
	 */
	@GetMapping("/impact/domain")
	public Map<String, Object> getDomainImpact(@RequestParam String domainNm) {
		Map<String, Object> result = new HashMap<>();
		result.put("terms", sqlSessionTemplate.selectList("impact.selectImpactTermsByDomainNm", domainNm));
		return result;
	}

	private void saveChangeHistory(String changeType, String targetType, String targetId, String targetNm,
			String prevValue, String currValue, String summary) {
		try {
			Map<String, Object> history = new HashMap<>();
			history.put("changeId", StringUtils.getUUID());
			history.put("changeType", changeType);
			history.put("targetType", targetType);
			history.put("targetId", targetId);
			history.put("targetNm", targetNm);
			history.put("changeCnt", 1);
			history.put("summary", summary);
			history.put("prevValue", prevValue);
			history.put("currValue", currValue);
			history.put("changeUserId", sessionService.getUserId());
			sqlSessionTemplate.insert("changehistory.insertChangeHistory", history);
		} catch (Exception e) {
			log.warn("변경이력 저장 실패: {}", e.getMessage());
		}
	}
}
