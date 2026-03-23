package qualitycenter.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import com.ndata.quality.common.NDQualityConstant;
import com.ndata.quality.common.NDQualityRetrieveCond;
import com.ndata.quality.common.NDQualityStdObjectType;
import com.ndata.quality.model.std.StdApproveStatVo;
import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdCodeInfoVo;
import com.ndata.quality.model.std.StdDomainClassificationVo;
import com.ndata.quality.model.std.StdDomainGroupVo;
import com.ndata.quality.model.std.StdDomainVo;
import com.ndata.quality.model.std.StdTermsVo;
import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.service.ExcelDownloadService;
import com.ndata.quality.service.ExcelUploadService;
import com.ndata.quality.tool.StringWordAnalyzer;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.model.data.WordDataVo;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ws.WebSocketService;
import reactor.core.publisher.Mono;

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

	// 단어
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
			sqlSessionTemplate.insert("word.insertWord", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> createWord failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateWord", method = RequestMethod.POST)
	public Mono<Response> updateWord(@RequestBody StdWordVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateWord : {}", dataVo);

		Response result = new Response();

		try {
			sqlSessionTemplate.update("word.updateWord", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateWord failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

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
			sqlSessionTemplate.delete("word.deleteWords", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteWords failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), "단어 삭제 중 오류가 발생했습니다.");
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getWordList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdWordVo> getWordList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getWordList : {}", retCond);
		return sqlSessionTemplate.selectList("word.selectWordList", retCond);
	}

	@RequestMapping(value = "/getWordInfoByNm", method = RequestMethod.GET)
	public List<StdWordVo> getWordInfoByNm(String wordNm) {
		return sqlSessionTemplate.selectList("word.selectWordInfoByNm", wordNm);
	}

	@RequestMapping(value = "/getWordInfoById", method = RequestMethod.GET)
	public List<StdWordVo> getWordInfoById(String wordId) {
		return sqlSessionTemplate.selectList("word.selectWordInfoById", wordId);
	}

	@RequestMapping(value = "/uploadWords", method = RequestMethod.POST)
	// public Response uploadWords(MultipartHttpServletRequest request) {
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

	// 용어
	@RequestMapping(value = "/createTerms", method = RequestMethod.POST)
	public Mono<Response> createTerms(@RequestBody StdTermsVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
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
			// 신규 용어 등록
			session.insert("terms.insertTerms", dataVo);
			List<StdTermsVo.Word> wordList = Arrays.asList(dataVo.getWordList());
			if (wordList != null) {
				wordList.stream().forEach(v -> v.setTermsId(dataVo.getId()));
			} else {
				throw new Exception("terms word list is invalid");
			}
			session.insert("terms.insertTermsWords", wordList);
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			session.rollback();
			log.error("create terms : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}

		return Mono.just(result);
	}

	// 용어 : 입력 (단어)형태소 분석
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

	@RequestMapping(value = "/updateTerms", method = RequestMethod.POST)
	public Mono<Response> updateTerms(@RequestBody StdTermsVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateTerms : {}", dataVo);

		SqlSession session = sqlSessionFactory.openSession();

		Response result = new Response();

		try {
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
		} catch (Exception e) {
			session.rollback();
			log.error("update terms : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		} finally {
			session.close();
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteTermsList", method = RequestMethod.POST)
	public Mono<Response> deleteTermsList(@RequestBody List<StdTermsVo> dataVos) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("terms.deleteTermsList", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteTermsList failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getTermsList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdTermsVo> getTermsList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getTermsList : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectTermsList", retCond);
	}

	@RequestMapping(value = "/getTermsListByCond", method = RequestMethod.POST)
	public List<StdTermsVo> getTermsListByCond(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getTermsListByCond : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectTermsList", retCond);
	}

	@RequestMapping(value = "/getTermsInfoByNm", method = RequestMethod.GET)
	public List<StdTermsVo> getTermsInfoByNm(String termsNm) {
		return sqlSessionTemplate.selectList("terms.selectTermsInfoByNm", termsNm);
	}

	@RequestMapping(value = "/getTermsWordInfoList", method = RequestMethod.GET)
	public List<StdWordVo> getTermsWordInfoList(String termsId) {
		return sqlSessionTemplate.selectList("word.selectTermsWordInfoList", termsId);
	}

	@RequestMapping(value = "/uploadTermsList", method = RequestMethod.POST)
	// public Response uploadTermsList(MultipartHttpServletRequest request) {
	public Mono<Response> uploadTermsList(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile) {

		log.info(">> started uploadTermsList file : {}", excelFile.getOriginalFilename());

		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + "/api/std/uploadTermsList", MediaType.MULTIPART_FORM_DATA_VALUE);
		Mono<Response> mResponse = webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);

		log.info(">> finished uploadTermsList file : {}", excelFile.getOriginalFilename());

		return mResponse;
	}

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

	// 코드정보
	@RequestMapping(value = "/getCodeInfoList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdCodeInfoVo> getCodeInfoList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getCodeInfoList : {}", retCond);
		return sqlSessionTemplate.selectList("terms.selectCodeInfoList", retCond);
	}

	@RequestMapping(value = "/getCodeInfoListByNm", method = RequestMethod.GET)
	public List<StdCodeInfoVo> getCodeInfoListByNm(String codeNm) {
		return sqlSessionTemplate.selectList("terms.selectCodeInfoListByNm", "%" + codeNm + "%");
	}

	@RequestMapping(value = "/createCode", method = RequestMethod.POST)
	public Mono<Response> createCode(@RequestBody StdTermsVo dataVo) {
		return createTerms(dataVo);
	}

	@RequestMapping(value = "/updateCode", method = RequestMethod.POST)
	public Mono<Response> updateCode(@RequestBody StdTermsVo dataVo) {
		return updateTerms(dataVo);
	}

	@RequestMapping(value = "/deleteCodeList", method = RequestMethod.POST)
	public Mono<Response> deleteCodeList(@RequestBody List<StdTermsVo> dataVos) {
		return deleteTermsList(dataVos);
	}

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

	// 코드데이터(항목값)
	@RequestMapping(value = "/getCodeDataList", method = RequestMethod.GET)
	public List<StdCodeDataVo> getCodeDataList() {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataList");
	}

	@RequestMapping(value = "/getCodeDataListByNm", method = RequestMethod.GET)
	public List<StdCodeDataVo> getCodeDataListByNm(String codeNm) {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataListByNm", codeNm);
	}

	@RequestMapping(value = "/createCodeData", method = RequestMethod.POST)
	public Mono<Response> createCodeData(@RequestBody StdCodeDataVo dataVo) {
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		log.info(">> createCodeData : {}", dataVo);

		Response result = new Response();
		try {
			sqlSessionTemplate.insert("codedata.insertCodeData", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> createCodeData failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateCodeData", method = RequestMethod.POST)
	public Mono<Response> updateCodeData(@RequestBody StdCodeDataVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateCodeData : {}", dataVo);

		Response result = new Response();
		try {
			sqlSessionTemplate.update("codedata.updateCodeData", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateCodeData failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteCodeDatas", method = RequestMethod.POST)
	public Mono<Response> deleteCodeDatas(@RequestBody List<StdCodeDataVo> dataVos) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("codedata.deleteCodeDatas", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteCodeDatas failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

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

	// 도메인
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
		} catch (Exception e) {
			log.error(">> createDomain failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/updateDomain", method = RequestMethod.POST)
	public Mono<Response> updateDomain(@RequestBody StdDomainVo dataVo) {
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateDomain : {}", dataVo);

		Response result = new Response();
		try {
			sqlSessionTemplate.update("domain.updateDomain", dataVo);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> updateDomain failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}

		return Mono.just(result);
	}

	@RequestMapping(value = "/deleteDomains", method = RequestMethod.POST)
	public Mono<Response> deleteDomains(@RequestBody List<StdDomainVo> dataVos) {
		Response result = new Response();
		try {
			sqlSessionTemplate.delete("domain.deleteDomains", dataVos);
			result.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> deleteDomains failed : {}", e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(result);
	}

	@RequestMapping(value = "/getDomainList", method = { RequestMethod.GET, RequestMethod.POST })
	public List<StdDomainVo> getDomainList(@RequestBody(required = false) NDQualityRetrieveCond retCond) {
		log.info(">> getDomainList : {}", retCond);
		return sqlSessionTemplate.selectList("domain.selectDomainList", retCond);
	}

	@RequestMapping(value = "/getDomainInfoByNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByNm(String domainNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByNm", domainNm);
	}

	@RequestMapping(value = "/getDomainInfoByClsfNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByClsfNm(String clsfNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByClsfNm", clsfNm);
	}

	@RequestMapping(value = "/createDomainGroup", method = RequestMethod.POST)
	@ResponseBody
	public Response createDomainGroup(@RequestBody StdDomainGroupVo dataVo) {

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());

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

	@RequestMapping(value = "/updateDomainGroup", method = RequestMethod.POST)
	@ResponseBody
	public Response updateDomainGroup(@RequestBody StdDomainGroupVo dataVo) {

		Response result = new Response();

		try {

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

	@RequestMapping(value = "/getDomainGroupList", method = RequestMethod.GET)
	public List<StdDomainGroupVo> getDomainGroupList() {
		return sqlSessionTemplate.selectList("domain.selectDomainGroupList");
	}

	@RequestMapping(value = "/createDomainClassification", method = RequestMethod.POST)
	@ResponseBody
	public Response createDomainClassification(@RequestBody StdDomainClassificationVo dataVo) {

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());

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

	@RequestMapping(value = "/updateDomainClassification", method = RequestMethod.POST)
	@ResponseBody
	public Response updateDomainClassification(@RequestBody StdDomainClassificationVo dataVo) {
		Response result = new Response();
		try {
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

	@RequestMapping(value = "/getDomainClassificationList", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationList() {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationList");
	}

	@RequestMapping(value = "/getDomainClassificationListByNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByNm(String domainClsfNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByNm", "%" + domainClsfNm + "%");
	}

	@RequestMapping(value = "/getDomainClassificationListByDomainGrpNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByGrpNm(String domainGrpNm) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByGrpNm", domainGrpNm);
	}

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

	// 승인처리
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

	@RequestMapping(value = "/putStdAprvStat", method = RequestMethod.POST)
	public Mono<Response> putStdAprvStat(@RequestBody List<StdApproveStatVo> dataVos) {
		log.info(">> putStdAprvStat : {}", dataVos);

		Response result = new Response();

		for (StdApproveStatVo dataVo : dataVos) {
			dataVo.setId(StringUtils.getUUID());
			dataVo.setAprvUserId(sessionService.getUserId());

			SqlSession session = sqlSessionFactory.openSession();

			try {
				session.insert("approve.insertStdAprvStat", dataVo);
				switch (NDQualityStdObjectType.valueOf(dataVo.getReqTp())) {
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
			} catch (Exception e) {
				session.rollback();
				log.error("update standard approve stat : {}", e.getMessage());
				result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			} finally {
				session.close();
			}
		}

		return Mono.just(result);
	}
}
