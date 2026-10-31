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
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.common.handler.WebClientHandler;
import com.ndata.module.StringUtils;
import com.ndata.quality.common.NDQualityApproveStat;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.common.StdInputRule;
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
import com.ndata.quality.service.AprvService;
import com.ndata.quality.service.CodeTypeService;
import com.ndata.quality.service.ExcelDownloadService;
import com.ndata.quality.service.ExcelUploadService;
import com.ndata.quality.tool.StringWordAnalyzer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
	private com.ndata.quality.service.StdDictService dictService;

	/**
	 * 98번 — 목록 조회 검색조건에 사전을 보정한다.
	 *
	 * <p>화면이 사전을 안 보내면 기본 사전으로 채운다. 비워 두면 매퍼의
	 * <code>&lt;if&gt;</code> 가 건너뛰어 <b>전 사전을 훑는</b> 결과가 나온다.
	 * 그건 사전을 나눈 의미가 없으므로 여기서 반드시 채운다.</p>
	 */
	/**
	 * 98번 — 요청 본문의 dictId 를 사전 id 로 확정한다.
	 *
	 * <p>비어 있으면 기본 사전. null 을 그대로 흘리면 INSERT 의 COALESCE 가
	 * 'DEFAULT' 로 받아주긴 하지만, 어느 사전에 넣었는지 로그·응답에서
	 * 확인할 수 없게 되므로 여기서 값을 정한다.</p>
	 */
	private String resolveDictOrDefault(Object raw) {
		String v = (raw instanceof String) ? ((String) raw).trim() : null;
		return (v == null || v.isEmpty()) ? dictService.defaultDictId() : v;
	}

	// ===== 104번 WP5-14 — 참조 사전은 고치지 못한다 =====

	/** 사전이 참조 사전이면 오류 응답, 아니면 null */
	private Response refBlocked(Object dictId) {
		try {
			dictService.checkWritable(resolveDictOrDefault(dictId));
			return null;
		} catch (IllegalArgumentException e) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return r;
		}
	}

	/** 항목들 중 하나라도 참조 사전에 있으면 오류 응답, 아니면 null */
	private Response refBlockedOf(String table, String idCol, java.util.Collection<String> ids) {
		try {
			for (String id : ids) dictService.checkWritableOf(table, idCol, id);
			return null;
		} catch (IllegalArgumentException e) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return r;
		}
	}

	/**
	 * 109번 #13 — 도메인 그룹·도메인 분류·코드값(항목값) 쓰기는 표준 직접 관리 권한(STD_MANAGE). 이 셋은 승인 흐름이 없어
	 * 일반 사용자의 등록·수정·삭제가 바로 반영됐다. 권한이 있으면 null
	 */
	private Response noStdManage() {
		if (sessionService.hasPerm(PermCode.STD_MANAGE)) return null;
		Response r = new Response();
		r.setResultInfo(403, "권한이 없습니다 (표준 직접 관리 권한 필요).");
		return r;
	}

	private static <T> List<String> ids(List<T> vos, java.util.function.Function<T, String> f) {
		List<String> out = new ArrayList<>();
		if (vos != null) for (T v : vos) out.add(f.apply(v));
		return out;
	}

	private NDQualityRetrieveCond withDict(NDQualityRetrieveCond cond) {
		NDQualityRetrieveCond c = (cond != null) ? cond : new NDQualityRetrieveCond();
		if (c.getDictId() == null || c.getDictId().trim().isEmpty()) {
			c.setDictId(dictService.defaultDictId());
		}
		// 104번 WP5-11 — 목록에 상위 사전 항목도
		if ("Y".equals(c.getInclParentYn())) c.setDictIds(dictService.lineage(c.getDictId()));
		return c;
	}

	/**
	 * 114 권고 채택(10-28)·110번 3.12-15-a — 단어·용어·코드·도메인 목록에서 표준 승인 권한(STD_APPROVE)이 없는 사용자는
	 * 승인 대기 항목을 본인이 등록한 것만 본다("승인 여부" 를 풀었을 때 다른 사용자의 승인 대기 항목이 보였다). 승인 화면과 같은 기준
	 */
	private NDQualityRetrieveCond pendingScope(NDQualityRetrieveCond c) {
		if (!sessionService.hasPerm(PermCode.STD_APPROVE)) {
			String uid = sessionService.getUserId();
			c.setPendingOwnerId(uid == null ? "" : uid);   // 로그인하지 않은 호출은 승인 대기 항목을 보지 않는다
		}
		return c;
	}

	/**
	 * 98번 — 엑셀 일괄등록 파일을 q-executor 로 넘긴다. 화면에서 고른 사전을 함께 보낸다.
	 *
	 * <p>외부 jar 의 WebClientHandler.postMultipartData 는 파일 파트만 싣기 때문에 사전은
	 * 쿼리 파라미터(dictId)로 붙인다. q-executor 업로드 API 는 이미 dictId 를 받는다.
	 * 사전 id 는 서버가 만든 값이므로 사용 중인 사전인지 확인하고 넘긴다 — 중지된 사전이나
	 * 엉뚱한 값이 주소에 섞이지 않게.</p>
	 */
	private Mono<Response> forwardUploadToExecutor(HttpServletRequest request, MultipartFile excelFile,
			String path, String dictIdParam) {
		String dictId = resolveDictOrDefault(dictIdParam);
		if (dictService.list().stream().noneMatch(d -> dictId.equals(d.get("dictId")))) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "사용할 수 없는 표준사전입니다: " + dictId);
			return Mono.just(r);
		}
		// 104번 WP5-14 — 참조 사전에는 일괄등록하지 않는다
		Response rb0 = refBlocked(dictId);
		if (rb0 != null) return Mono.just(rb0);
		log.info(">> forward {} file : {}, dictId : {}", path, excelFile.getOriginalFilename(), dictId);
		WebClientHandler webClientHandler = new WebClientHandler(
				NDQualityConstant.SVC_Q_EXECUTOR_URL + path + "?dictId=" + dictId, MediaType.MULTIPART_FORM_DATA_VALUE);
		return webClientHandler.postMultipartData(sessionService.getUserId(),
				Objects.toString(request.getSession().getAttribute("SSID"), null), excelFile);
	}

	/**
	 * 98번 — 용어의 마지막 단어가 형식단어인지 검사한다. 사전 속성 "용어의 마지막 단어를 형식단어로 강제"
	 * (TB_STD_DICT.TERM_LAST_WORD_CLSF_YN) 가 켜진 사전에 등록할 때만 부른다 —
	 * 화면 등록(createTerms, 진단 결과 등록 포함)과 한글컬럼 일괄 표준화(registerTermsBatch).
	 * 엑셀 일괄등록은 q-executor(ExcelUploadService)에서 같은 사전 속성을 읽는다.
	 */
	private void checkLastWordClsf(SqlSession session, String lastWordId) {
		List<StdWordVo> lastWordInfo = session.selectList("word.selectWordInfoById", lastWordId);
		if (!lastWordInfo.isEmpty() && !"Y".equals(lastWordInfo.get(0).getWordClsfYn())) {
			throw new IllegalStateException("용어의 마지막 단어는 형식단어여야 합니다. (현재: " + lastWordInfo.get(0).getWordNm() + ")");
		}
	}

	@Autowired
	private SqlSessionFactory sqlSessionFactory; // transaction 사용할 경우 사용

	@Autowired
	private ExcelUploadService excelUploadService;

	@Autowired
	private ExcelDownloadService excelDownloadService;

	@Autowired
	private WebSocketService websocketService;

	@Autowired
	private AprvService aprvService;

	@Autowired
	private CodeTypeService codeTypeService;

	/** 104번 WP6-3 — 코드(용어) 등록·수정의 코드 유형 검사와 통합 코드 구성 저장 (같은 트랜잭션) */
	private void checkCodeType(SqlSession session, StdTermsVo vo, boolean isNew) {
		codeTypeService.checkAndSave(session, vo, isNew, sessionService.getUserId());
	}

	/** 코드도 TB_TERMS 에 저장된다 — 코드 그룹이 있으면 코드 (승인 목록·결재 유형과 같은 기준) */
	private static boolean isCode(String codeGrp) {
		return codeGrp != null && !codeGrp.trim().isEmpty();
	}

	/** 110번 3.12-10-a·8.5-4 — 도메인들을 쓰는 용어 이름 ("A, B, C 외 N건"), 없으면 null */
	private String termsUsingDomainsMsg(List<String> domainIds) {
		if (domainIds.isEmpty()) return null;
		List<String> terms = sqlSessionTemplate.selectList("domain.selectTermsUsingDomains", domainIds);
		if (terms.isEmpty()) return null;
		return terms.size() <= 3 ? String.join(", ", terms)
				: String.join(", ", terms.subList(0, 3)) + " 외 " + (terms.size() - 3) + "건";
	}

	/**
	 * 통합 검색 API - 단어/용어/도메인/컬럼을 키워드로 일괄 검색
	 *
	 * @param keyword 검색 키워드
	 * @param dictId  표준사전 (110번 2.4-2-a — 화면에서 고른 사전, 비면 기본 사전). 단어·용어·도메인에만 쓴다 — 컬럼은 모든 모델
	 * @return 단어(words), 용어(terms), 도메인(domains), 컬럼(columns) 검색 결과 Map
	 */
	@GetMapping("/search")
	public Map<String, Object> globalSearch(@RequestParam String keyword,
			@RequestParam(value = "dictId", required = false) String dictId) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> param = new HashMap<>();
		param.put("keyword", keyword);
		param.put("dictId", resolveDictOrDefault(dictId));
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return Mono.just(rb0);
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 98번 — 어느 사전에 등록하는지. 화면이 안 보내면 기본 사전.
		dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
			dataVo.setAprvYn("Y");
		}
		log.info(">> createWord : {}", dataVo);

		Response result = new Response();

		try {
			// 86번 #29 — 입력 검증 강화 (createWord/updateWord 공통)
			validateWordInput(dataVo);
			// 형식단어인데 도메인 분류명이 없으면 등록 차단
			if ("Y".equals(dataVo.getWordClsfYn()) && (dataVo.getDomainClsfNm() == null || dataVo.getDomainClsfNm().isEmpty())) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "형식 단어는 도메인 분류명이 필수입니다.");
				return Mono.just(result);
			}
			// 중복 체크: 동일 단어명이 이미 존재하면 승인 상태에 따라 메시지 분기
			StdWordVo existingWord = sqlSessionTemplate.selectOne("word.selectWordInfoByNm", dictService.nameParam(dataVo.getDictId(), "wordNm", dataVo.getWordNm()));
			if (existingWord != null) {
				// 104번 WP7-8 — 다른 단어의 이음동의어(유사어)와 같으면 표준 단어를 안내한다
				String dupMsg = !existingWord.getWordNm().equals(dataVo.getWordNm())
						? StdInputRule.synonymOfMsg(dataVo.getWordNm(), existingWord.getWordNm())
						: "Y".equals(existingWord.getAprvYn())
						? "이미 승인된 단어명입니다."
						: "승인 대기 중인 단어명입니다.";
				result.setResultInfo(RestResult.CODE_500.getCode(), dupMsg);
				return Mono.just(result);
			}
			// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
			String forbiddenMsg = checkForbiddenWord(dataVo.getWordNm(), dataVo.getDictId());
			if (forbiddenMsg != null) {
				result.setResultInfo(RestResult.CODE_500.getCode(), forbiddenMsg);
				return Mono.just(result);
			}
			// 유사어 체크: 경고만 (등록은 허용, warning 메시지를 resultMessage에 포함)
			String synonymMsg = checkSynonymWord(dataVo.getWordNm(), dataVo.getDictId());

			sqlSessionTemplate.insert("word.insertWord", dataVo);
			String msg = synonymMsg;
			if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
				String approvalMsg = pendingMsg(openAprv("WORD", dataVo.getId(), dataVo.getWordNm(), dataVo.getDictId()));
				msg = msg != null ? msg + " " + approvalMsg : approvalMsg;
			}
			if (msg != null) {
				result.setResultInfo(RestResult.CODE_200.getCode(), msg);
			} else {
				result.setResultInfo(RestResult.CODE_200);
			}
			// 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
			if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
				saveChangeHistory("INSERT", "WORD", dataVo.getId(), dataVo.getWordNm(),
						null, dataVo.toString(), "단어 등록: " + dataVo.getWordNm());
			}
		} catch (Exception e) {
			log.error(">> createWord failed : {}", e.getMessage());
			String dupMsg = StdInputRule.translateDuplicateError(e.getMessage());
			String userMsg = dupMsg != null ? dupMsg : e.getMessage();
			// 86번 #29 — raw DB/MyBatis stacktrace 노출 차단 (사용자 친화적 fallback)
			if (userMsg != null && (userMsg.startsWith("\r\n### Error") || userMsg.contains("PSQLException") || userMsg.contains("org.postgresql"))) {
				userMsg = "단어 등록 중 오류가 발생했습니다. 입력값을 확인해주세요.";
			}
			result.setResultInfo(RestResult.CODE_500.getCode(), userMsg);
		}

		return Mono.just(result);
	}

	/**
	 * 104번 WP1 — 승인 대기로 등록한 항목에 결재선이 있으면 결재 문서를 만들고 1단계 결재자에게 알린다.
	 * 결재선이 없으면 null (지금처럼 표준 승인 권한자가 승인).
	 */
	private String openAprv(String reqTp, String itemId, String itemNm, String dictId) {
		AprvService.Result r = aprvService.open(reqTp, itemId, itemNm, null, dictId, sessionService.getUserId());
		if (r == null) return null;
		websocketService.sendUserNotis(r.notis);
		return r.message;
	}

	/** 승인 대기 등록 안내 — 결재 문서가 생겼으면 결재 단계, 아니면 기존 문구 */
	private static String pendingMsg(String aprvMsg) {
		return aprvMsg == null ? "관리자 승인 후 조회 가능합니다." : aprvMsg + ". 결재가 끝나면 조회 가능합니다.";
	}

	/**
	 * 단어 수정 API - 변경 전 값을 조회하여 변경이력과 함께 저장
	 *
	 * @param dataVo 수정할 단어 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateWord", method = RequestMethod.POST)
	public Mono<Response> updateWord(@RequestBody StdWordVo dataVo) {
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_WORD", "WORD_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "수정 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateWord : {}", dataVo);

		Response result = new Response();

		try {
			// 104번 WP6-13 — 수정 화면은 사전을 보내지 않는다. 그 단어의 사전 옵션으로 검사하려고 사전을 읽는다
			dataVo.setDictId(sqlSessionTemplate.selectOne("word.selectWordDictIdById", dataVo.getId()));
			// 86번 #29 — updateWord 도 createWord 와 동일 검증
			validateWordInput(dataVo);
			// 변경 전 값 조회
			List<StdWordVo> prevList = sqlSessionTemplate.selectList("word.selectWordInfoById", dataVo.getId());
			String prevValue = prevList != null && !prevList.isEmpty() ? prevList.get(0).toString() : null;
			// 110번 3.12-7-g — 수정은 승인 여부를 바꾸지 않는다. 요청 VO 의 기본값('N')이 이력에 남아 승인상태가 Y→N 으로 보였다
			if (prevList != null && !prevList.isEmpty()) dataVo.setAprvYn(prevList.get(0).getAprvYn());
			sqlSessionTemplate.update("word.updateWord", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "WORD", dataVo.getId(), dataVo.getWordNm(),
					prevValue, dataVo.toString(), "단어 수정: " + dataVo.getWordNm());
		} catch (Exception e) {
			log.error(">> updateWord failed : {}", e.getMessage());
			String userMsg = e.getMessage();
			if (userMsg != null && (userMsg.startsWith("\r\n### Error") || userMsg.contains("PSQLException") || userMsg.contains("org.postgresql"))) {
				userMsg = "단어 수정 중 오류가 발생했습니다. 입력값을 확인해주세요.";
			}
			result.setResultInfo(RestResult.CODE_500.getCode(), userMsg);
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_WORD", "WORD_ID", ids(dataVos, StdWordVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "삭제 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
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
			// 삭제 전 기존 값 보존 — 110번 3.12-7-c·d: 화면은 id 만 보내므로 지우기 전에 읽어 항목마다 이력(대상명·이전 값)을 남긴다
			List<StdWordVo> prevs = new ArrayList<>();
			for (StdWordVo vo : dataVos) {
				List<StdWordVo> prev = sqlSessionTemplate.selectList("word.selectWordInfoById", vo.getId());
				if (prev != null && !prev.isEmpty()) prevs.add(prev.get(0));
			}
			sqlSessionTemplate.delete("word.deleteWords", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			for (StdWordVo p : prevs) {
				saveChangeHistory("DELETE", "WORD", p.getId(), p.getWordNm(), p.toString(), null, "단어 삭제: " + p.getWordNm());
			}
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
		return sqlSessionTemplate.selectList("word.selectWordList", pendingScope(withDict(retCond)));
	}

	/**
	 * 단어명으로 단어 정보 조회
	 *
	 * @param wordNm 단어 한글명
	 * @return 해당 단어 정보 목록 (동음이의어 포함)
	 */
	@RequestMapping(value = "/getWordInfoByNm", method = RequestMethod.GET)
	public List<StdWordVo> getWordInfoByNm(@RequestParam("wordNm") String wordNm,
			@RequestParam(value = "dictId", required = false) String dictId) {
		// 98번 — 화면에서 고른 사전 안에서 찾는다 (없으면 기본 사전).
		return sqlSessionTemplate.selectList("word.selectWordInfoByNm", dictService.nameParam(dictId, "wordNm", wordNm));
	}

	/**
	 * 104번 WP7-8 — 등록하려는 단어명이 사전에서 어떤 단어와 겹치는지 본다.
	 * EXACT(같은 단어), SYNONYM(다른 단어의 유사어 — 이음동의어), FORBIDDEN(승인 단어의 금칙어), NONE.
	 * SYNONYM·FORBIDDEN 이면 권장(표준) 단어를 함께 준다.
	 */
	@RequestMapping(value = "/checkWordNm", method = RequestMethod.GET)
	public Map<String, Object> checkWordNm(@RequestParam("wordNm") String wordNm,
			@RequestParam(value = "dictId", required = false) String dictId) {
		Map<String, Object> result = new HashMap<>();
		String nm = wordNm == null ? "" : wordNm.trim();
		result.put("status", "NONE");
		if (nm.isEmpty()) return result;
		List<StdWordVo> words = sqlSessionTemplate.selectList("word.selectWordInfoByNm", dictService.nameParam(dictId, "wordNm", nm));
		if (words != null && !words.isEmpty()) {
			StdWordVo w = words.stream().filter(x -> nm.equals(x.getWordNm())).findFirst().orElse(words.get(0));
			result.put("status", nm.equals(w.getWordNm()) ? "EXACT" : "SYNONYM");
			result.put("wordId", w.getId());
			result.put("wordNm", w.getWordNm());
			result.put("wordEngAbrvNm", w.getWordEngAbrvNm());
			result.put("wordEngNm", w.getWordEngNm());
			result.put("aprvYn", w.getAprvYn());
			return result;
		}
		Map<String, Object> forbidden = sqlSessionTemplate.selectOne("word.selectWordByForbiddenNm", dictService.nameParam(dictId, "wordNm", nm));
		if (forbidden != null) {
			result.put("status", "FORBIDDEN");
			result.put("wordNm", forbidden.get("wordNm"));
			result.put("wordEngAbrvNm", forbidden.get("wordEngAbrvNm"));
			result.put("aprvYn", "Y");
		}
		return result;
	}

	/**
	 * 단어명으로 TB_WORD + TB_WORD_DICT 동시 조회 (실시간 입력 조회용, 경량)
	 */
	@RequestMapping(value = "/lookupWord", method = RequestMethod.GET)
	public Map<String, Object> lookupWord(@RequestParam("wordNm") String wordNm,
			@RequestParam(value = "dictId", required = false) String dictId) {
		Map<String, Object> result = new HashMap<>();
		if (wordNm == null || wordNm.trim().isEmpty()) {
			result.put("found", false);
			return result;
		}
		// 1. TB_WORD 조회 — 98번: 화면에서 고른 사전 안에서
		List<StdWordVo> words = sqlSessionTemplate.selectList("word.selectWordInfoByNm", dictService.nameParam(dictId, "wordNm", wordNm.trim()));
		if (words != null && !words.isEmpty()) {
			StdWordVo w = words.get(0);
			result.put("found", true);
			result.put("source", "WORD");
			// 104번 WP7-8 — 입력이 이 단어의 유사어(이음동의어)면 표시
			if (!w.getWordNm().equals(wordNm.trim())) result.put("synonymOf", w.getWordNm());
			result.put("wordId", w.getId());
			result.put("wordNm", w.getWordNm());
			result.put("wordEngAbrvNm", w.getWordEngAbrvNm());
			result.put("wordEngNm", w.getWordEngNm());
			result.put("domainClsfNm", w.getDomainClsfNm());
			result.put("wordClsfYn", w.getWordClsfYn());
			return result;
		}
		// 2. TB_WORD_DICT 조회
		Map<String, Object> dict = sqlSessionTemplate.selectOne("word.selectWordDictByNm", wordNm.trim());
		if (dict != null) {
			String dictAbrv = dict.get("wordAbrv") != null ? ((String) dict.get("wordAbrv")).trim() : "";
			// 영문약어가 TB_WORD에 이미 등록되어 있으면 중복 경고
			boolean abrvDuplicate = false;
			if (!dictAbrv.isEmpty()) {
				StdWordVo existing = sqlSessionTemplate.selectOne("word.selectWordByEngAbrvNm", dictService.nameParam(dictId, "wordEngAbrvNm", dictAbrv));
				if (existing != null) {
					abrvDuplicate = true;
				}
			}
			result.put("found", true);
			result.put("source", "DICT");
			result.put("wordNm", wordNm.trim());
			result.put("wordEngAbrvNm", dictAbrv);
			result.put("wordEngNm", dict.get("wordEng") != null ? dict.get("wordEng") : "");
			result.put("domainClsfNm", "");
			if (abrvDuplicate) {
				result.put("abrvDuplicate", true);
				result.put("abrvDuplicateMsg", "영문약어 '" + dictAbrv + "'는 이미 다른 단어에 등록되어 있습니다.");
			}
			return result;
		}
		result.put("found", false);
		return result;
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

	/** 영문약어로 단어 조회 (용어 빠른 등록용). 98번 — 진단 결과 화면은 그 진단의 사전을 보낸다. */
	@RequestMapping(value = "/getWordByEngAbrvNm", method = RequestMethod.GET)
	public StdWordVo getWordByEngAbrvNm(String wordEngAbrvNm, String dictId) {
		List<StdWordVo> list = sqlSessionTemplate.selectList("word.selectWordByEngAbrvNm", dictService.nameParam(dictId, "wordEngAbrvNm", wordEngAbrvNm));
		return list.isEmpty() ? null : list.get(0);
	}

	/** 영문약어 목록으로 단어 일괄 조회 (용어 빠른 등록용). 본문은 영문약어 배열이라 사전은 쿼리 파라미터로 받는다. */
	@RequestMapping(value = "/getWordsByEngAbrvNms", method = RequestMethod.POST)
	public List<StdWordVo> getWordsByEngAbrvNms(@RequestBody List<String> engAbrvNms,
			@RequestParam(value = "dictId", required = false) String dictId) {
		if (engAbrvNms == null || engAbrvNms.isEmpty()) return java.util.Collections.emptyList();
		return sqlSessionTemplate.selectList("word.selectWordsByEngAbrvNms", dictService.listParam(dictId, "engAbrvNms", engAbrvNms));
	}

	/**
	 * 단어 Excel 일괄 업로드 API - q-executor로 멀티파트 전달
	 *
	 * @param request HTTP 요청 (세션 SSID 추출용)
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과 (성공/실패 건수)
	 */
	@RequestMapping(value = "/uploadWords", method = RequestMethod.POST)
	public Mono<Response> uploadWords(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {

		// websocketService.sendMessage(Objects.toString(request.getSession().getAttribute("SSID"), null),
		// WsNoticeLevel.INFO, ">> uploadWords start");
		// String fileName = request.getFileNames().next();
		// log.info(">>> fileName : {}", fileName);
		// MultipartFile excelFile = request.getFile(fileName);

		log.info(">> started uploadWords file : {}", excelFile.getOriginalFilename());

		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadWords", dictId);

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
	public void downloadWordsExcel(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download words excel started : {}", searchKey);

		try {
			excelDownloadService.getWordsExcel(searchKey, dictId, request, response);
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return Mono.just(rb0);
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 98번 — 어느 사전에 등록하는지. 용어 화면은 고른 사전, 진단 결과 화면은 그 진단의 사전을 보낸다.
		dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
		// termsDesc가 null이면 termsNm을 자동으로 채움
		if (dataVo.getTermsDesc() == null || dataVo.getTermsDesc().isEmpty()) {
			dataVo.setTermsDesc(dataVo.getTermsNm());
		}
		// 110번 3.12-15-e — 도메인은 필수 (TB_TERMS.DOMAIN_NM NOT NULL). 예전에는 null 로 넣어 DB 오류 원문이 나갔다
		if (dataVo.getDomainNm() == null || dataVo.getDomainNm().trim().isEmpty()) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "도메인명은 필수입니다.");
			return Mono.just(r);
		}
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
			dataVo.setAprvYn("Y");
		}

		log.info(">> createTerms : {}", dataVo);

		SqlSession session = sqlSessionFactory.openSession();

		Response result = new Response();

		try {
			// 중복 체크: 동일 용어명이 이미 존재하면 승인 상태에 따라 메시지 분기
			dataVo.setTermsNm(dataVo.getTermsNm().replaceAll("\\s", ""));
			StdTermsVo existingTerms = session.selectOne("terms.selectTermsByNm", dictService.nameParam(dataVo.getDictId(), "termsNm", dataVo.getTermsNm()));
			if (existingTerms != null) {
				String dupMsg = "Y".equals(existingTerms.getAprvYn())
						? "이미 승인된 용어명입니다."
						: "승인 대기 중인 용어명입니다.";
				throw new Exception(dupMsg);
			}
			// 구성 단어 목록 확인 (승인 여부 체크는 용어 승인 시점에 수행)
			List<StdTermsVo.Word> wordList = Arrays.asList(dataVo.getWordList());
			if (wordList == null) {
				throw new Exception("terms word list is invalid");
			}
			if (wordList.isEmpty()) {
				throw new Exception("용어는 최소 1개 이상의 단어로 구성되어야 합니다");
			}
			// 형식단어 검증: 등록할 사전이 요구할 때만 마지막 단어가 형식단어인지 본다 (단일 단어 용어도 허용)
			StdTermsVo.Word lastWord = wordList.get(wordList.size() - 1);
			if (dictService.isTermLastWordClsfRequired(dataVo.getDictId()) && lastWord.getWordId() != null) {
				checkLastWordClsf(session, lastWord.getWordId());
			}
			// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자, 용어 영문약어명 최대 길이
			dataVo.setTermsEngAbrvNm(dictService.checkTermEngAbrv(dataVo.getDictId(), dataVo.getTermsEngAbrvNm()));
			// 104번 WP6-3 — 코드 유형 검사 (코드일 때)
			checkCodeType(session, dataVo, true);
			// 신규 용어 등록
			session.insert("terms.insertTerms", dataVo);
			wordList.stream().forEach(v -> v.setTermsId(dataVo.getId()));
			session.insert("terms.insertTermsWords", wordList);
			session.commit();
			if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
				// 코드도 TB_TERMS 에 저장된다 — 코드 그룹이 있으면 결재 요청 유형은 CODE
				String reqTp = dataVo.getCodeGrp() != null && !dataVo.getCodeGrp().isEmpty() ? "CODE" : "TERMS";
				result.setResultInfo(RestResult.CODE_200.getCode(),
						pendingMsg(openAprv(reqTp, dataVo.getId(), dataVo.getTermsNm(), dataVo.getDictId())));
			} else {
				result.setResultInfo(RestResult.CODE_200);
			}
			// 이력 저장 (관리자 즉시 승인 시에만). 110번 3.12-7-b — 코드(코드 그룹이 있는 용어)는 CODE 한 건 (createCode 가 따로 남기던 것은 뺐다)
			if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
				boolean code = isCode(dataVo.getCodeGrp());
				saveChangeHistory("INSERT", code ? "CODE" : "TERM", dataVo.getId(), dataVo.getTermsNm(),
						null, dataVo.toString(), (code ? "코드 등록: " : "용어 등록: ") + dataVo.getTermsNm());
			}
		} catch (Exception e) {
			session.rollback();
			log.error("create terms : {}", e.getMessage());
			String dupMsg = StdInputRule.translateDuplicateError(e.getMessage());
			result.setResultInfo(RestResult.CODE_500.getCode(), dupMsg != null ? dupMsg : e.getMessage());
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
	public Map<String, List<StdWordVo>> getTermsTokens(String termsNm, String dictId) {
		log.info(">> request termsNm : {}, dictId : {}", termsNm, dictId);
		Map<String, List<StdWordVo>> wordMap = new LinkedHashMap<String, List<StdWordVo>>();
		List<StdWordVo> wordVoLst = new ArrayList<StdWordVo>();

		// 요청된 용어가 단어이고, 형식단어(도메인)인 경우에는 바로 리턴한다. 98번 — 요청 사전 안에서 찾는다.
		wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", dictService.nameParam(dictId, "wordNm", termsNm));
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
				wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", dictService.nameParam(dictId, "wordNm", token));
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
	public List<WordDataVo> getTermsTokenListByNm(String termsNm, String dictId) {
		log.info(">> request termsNm : {}, dictId : {}", termsNm, dictId);
		List<WordDataVo> wordDatas = new ArrayList<>();
		List<StdWordVo> wordVoLst = new ArrayList<StdWordVo>();

		// 요청된 용어가 단어이고, 형식단어(도메인)인 경우에는 바로 리턴한다. 98번 — 요청 사전 안에서 찾는다.
		wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", dictService.nameParam(dictId, "wordNm", termsNm));
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
				wordVoLst = sqlSessionTemplate.selectList("word.selectWordByNm", dictService.nameParam(dictId, "wordNm", token));
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_TERMS", "TERMS_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "수정 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
		dataVo.setUpdtUserId(sessionService.getUserId());
		// 109번 #18 — 중복 검사·운영 옵션·통합 코드 구성 저장은 저장된 용어의 사전으로 한다 (요청의 dictId 를 믿지 않는다)
		String storedDictId = dictService.dictIdOf("TB_TERMS", "TERMS_ID", dataVo.getId());
		if (storedDictId == null) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "없는 용어입니다: " + dataVo.getId());
			return Mono.just(r);
		}
		dataVo.setDictId(storedDictId);
		log.info(">> updateTerms : {}", dataVo);

		SqlSession session = sqlSessionFactory.openSession();

		Response result = new Response();

		try {
			// 변경 전 값 조회 — 110번 3.12-7-g: 이름을 바꾸면 새 이름으로는 못 찾아 이전 값이 비었다. ID 로 찾는다
			List<StdTermsVo> prevList = session.selectList("terms.selectTermsInfoById", dataVo.getId());
			StdTermsVo prevTerms = prevList.isEmpty() ? null : prevList.get(0);
			String prevValue = prevTerms != null ? prevTerms.toString() : null;
			// 수정은 승인 여부를 바꾸지 않는다 (요청 VO 의 기본값 'N' 이 이력에 남지 않게)
			if (prevTerms != null) dataVo.setAprvYn(prevTerms.getAprvYn());

			// 기존 용어를 이미 존재하는 용어로 업데이트하는 경우에는 Exception 발생시킨다.
			dataVo.setTermsNm(dataVo.getTermsNm().replaceAll("\\s", ""));
			StdTermsVo stdTermsVo = session.selectOne("terms.selectTermsByNm", dictService.nameParam(dataVo.getDictId(), "termsNm", dataVo.getTermsNm()));
			if (stdTermsVo != null && !dataVo.getId().equals(stdTermsVo.getId())) {
				throw new Exception("previously registered terms can not be updated to existing terms("
						+ dataVo.getTermsNm() + ")");
			}
			// 110번 3.12-3-b — 단어 구성을 보내지 않으면(코드 수정 창은 1·2단계를 거치지 않고 [수정] 할 수 있다) 구성은 그대로 둔다.
			// 예전에는 지운 뒤 빈 목록을 넣어 SQL 오류로 수정이 실패했다
			StdTermsVo.Word[] words = dataVo.getWordList();
			boolean wordsGiven = words != null && words.length > 0;
			// 110번 3.12-9-b — 등록과 같이, 사전이 요구하면 마지막 단어가 형식단어인지 본다 (구성을 바꿀 때)
			if (wordsGiven && dictService.isTermLastWordClsfRequired(dataVo.getDictId()) && words[words.length - 1].getWordId() != null) {
				checkLastWordClsf(session, words[words.length - 1].getWordId());
			}
			// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자, 용어 영문약어명 최대 길이
			dataVo.setTermsEngAbrvNm(dictService.checkTermEngAbrv(dataVo.getDictId(), dataVo.getTermsEngAbrvNm()));
			// 104번 WP6-3 — 코드 유형 검사 (코드일 때)
			checkCodeType(session, dataVo, false);

			session.update("terms.updateTerms", dataVo);
			if (wordsGiven) {
				session.delete("terms.deleteTermsWords", dataVo.getId());
				session.insert("terms.insertTermsWords", Arrays.asList(words));
			}
			session.commit();
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장 — 110번 3.12-7-a: 코드는 CODE 한 건 (updateCode 가 결과와 관계없이 따로 남기던 것은 뺐다)
			boolean code = isCode(dataVo.getCodeGrp()) || (prevTerms != null && isCode(prevTerms.getCodeGrp()));
			saveChangeHistory("UPDATE", code ? "CODE" : "TERM", dataVo.getId(), dataVo.getTermsNm(),
					prevValue, dataVo.toString(), (code ? "코드 수정: " : "용어 수정: ") + dataVo.getTermsNm());
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_TERMS", "TERMS_ID", ids(dataVos, StdTermsVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "삭제 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
		Response result = new Response();
		try {
			// 110번 3.12-7-c·d — 화면은 id 만 보내므로 지우기 전에 읽어 항목마다 이력(대상명·이전 값)을 남긴다. 코드(코드 그룹이 있는 용어)는 CODE
			List<StdTermsVo> prevs = new ArrayList<>();
			for (StdTermsVo vo : dataVos) {
				List<StdTermsVo> prev = sqlSessionTemplate.selectList("terms.selectTermsInfoById", vo.getId());
				if (!prev.isEmpty()) prevs.add(prev.get(0));
			}
			sqlSessionTemplate.delete("terms.deleteTermsList", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			for (StdTermsVo p : prevs) {
				boolean code = isCode(p.getCodeGrp());
				saveChangeHistory("DELETE", code ? "CODE" : "TERM", p.getId(), p.getTermsNm(), p.toString(), null,
						(code ? "코드 삭제: " : "용어 삭제: ") + p.getTermsNm());
			}
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
		return sqlSessionTemplate.selectList("terms.selectTermsList", pendingScope(withDict(retCond)));
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
		return sqlSessionTemplate.selectList("terms.selectTermsList", pendingScope(withDict(retCond)));
	}

	/**
	 * 용어명으로 용어 상세 정보 조회
	 *
	 * @param termsNm 용어 한글명
	 * @return 해당 용어 정보 목록
	 */
	@RequestMapping(value = "/getTermsInfoByNm", method = RequestMethod.GET)
	public List<StdTermsVo> getTermsInfoByNm(String termsNm, String dictId) {
		return sqlSessionTemplate.selectList("terms.selectTermsInfoByNm",
				dictService.nameParam(dictId, "termsNm", termsNm));
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
	public Mono<Response> uploadTermsList(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {

		log.info(">> started uploadTermsList file : {}", excelFile.getOriginalFilename());

		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadTermsList", dictId);

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
	public void downloadTermsListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download terms excel started : {}", searchKey);

		try {
			excelDownloadService.getTermsListExcel(searchKey, dictId, request, response);
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
		return sqlSessionTemplate.selectList("terms.selectCodeInfoList", pendingScope(withDict(retCond)));
	}

	/**
	 * 코드명으로 코드 정보 검색 (LIKE 검색)
	 *
	 * @param codeNm 코드명 키워드
	 * @return 매칭되는 코드 정보 목록
	 */
	@RequestMapping(value = "/getCodeInfoListByNm", method = RequestMethod.GET)
	public List<StdCodeInfoVo> getCodeInfoListByNm(String codeNm, String dictId) {
		return sqlSessionTemplate.selectList("terms.selectCodeInfoListByNm", dictService.nameParam(dictId, "codeNm", "%" + codeNm + "%"));
	}

	/**
	 * 코드 등록 API - 내부적으로 createTerms를 호출하고 코드 이력을 추가 저장
	 *
	 * @param dataVo 코드(=용어) 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createCode", method = RequestMethod.POST)
	public Mono<Response> createCode(@RequestBody StdTermsVo dataVo) {
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return Mono.just(rb0);
		// 110번 3.12-7-b — 이력은 createTerms 가 성공했을 때 CODE 한 건으로 남긴다
		return createTerms(dataVo);
	}

	/**
	 * 코드 수정 API - 내부적으로 updateTerms를 호출하고 코드 이력을 추가 저장
	 *
	 * @param dataVo 수정할 코드 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateCode", method = RequestMethod.POST)
	public Mono<Response> updateCode(@RequestBody StdTermsVo dataVo) {
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_TERMS", "TERMS_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return Mono.just(rb0);
		// 110번 3.12-7-a — 이력은 updateTerms 가 성공했을 때 CODE 한 건으로 남긴다 (예전에는 권한 거부·실패여도 남았다)
		return updateTerms(dataVo);
	}

	/**
	 * 코드 다건 삭제 API
	 *
	 * @param dataVos 삭제 대상 코드 목록
	 * @return 삭제 결과
	 */
	@RequestMapping(value = "/deleteCodeList", method = RequestMethod.POST)
	public Mono<Response> deleteCodeList(@RequestBody List<StdTermsVo> dataVos) {
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_TERMS", "TERMS_ID", ids(dataVos, StdTermsVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		// 104번 WP6-3 — 다른 코드가 상위·하위로 쓰는 코드는 지우지 않는다. 지우면 통합 구성도 지운다
		List<Map<String, Object>> codes = new ArrayList<>();
		try (SqlSession chk = sqlSessionFactory.openSession()) {
			for (StdTermsVo vo : dataVos) {
				Map<String, Object> c = chk.selectOne("codeType.selectCodeById", vo.getId());
				if (c == null || c.get("codeGrp") == null) continue;
				Map<String, Object> p = dictService.nameParam((String) c.get("dictId"), "codeGrp", (String) c.get("codeGrp"));
				int child = chk.selectOne("codeType.countChildCodes", p);
				int integ = chk.selectOne("codeType.countIntegParents", p);
				if (child > 0 || integ > 0) {
					Response r = new Response();
					r.setResultInfo(RestResult.CODE_500.getCode(), "코드 '" + c.get("termsNm") + "' 를 상위로 쓰는 계층·파생 코드 " + child
							+ "개, 하위로 쓰는 통합 코드 " + integ + "개가 있어 지울 수 없습니다.");
					return Mono.just(r);
				}
				p.put("termsNm", c.get("termsNm"));
				codes.add(p);
			}
		}
		// 삭제 이력(항목마다 CODE)은 deleteTermsList 가 성공했을 때 남긴다 — 110번 3.12-7-a: 예전에는 결과와 관계없이 남았다
		Mono<Response> resp = deleteTermsList(dataVos);
		Response r0 = resp.block();
		if (r0 != null && r0.getResultCode() == RestResult.CODE_200.getCode()) {
			for (Map<String, Object> p : codes) {
				sqlSessionTemplate.delete("codeType.deleteInteg", p);
				// 110번 3.12-10-b — 그 코드 그룹을 가진 용어가 더 없으면 코드값도 지운다 (FK 가 없어 코드값이 남았다)
				int holders = sqlSessionTemplate.selectOne("codeType.countGrpHolders", p);
				if (holders == 0) {
					int n = sqlSessionTemplate.delete("codeType.deleteValuesByGrp", p);
					if (n > 0) {
						saveChangeHistory("DELETE", "CODE_DATA", null, (String) p.get("codeGrp"), null, null,
								"코드 '" + p.get("termsNm") + "' 삭제로 코드값 " + n + "건 함께 삭제");
					}
				}
			}
		}
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
			@RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {

		log.info(">> started uploadCodeInfoList file : {}", excelFile.getOriginalFilename());

		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadCodeInfoList", dictId);

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
	public void downloadCodeInfoListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download code excel started : {}", searchKey);

		try {
			excelDownloadService.getCodeInfoListExcel(searchKey, dictId, request, response);
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
	public List<StdCodeDataVo> getCodeDataList(String dictId) {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataList", dictService.dictParam(dictId));
	}

	/**
	 * 코드명으로 코드데이터 검색
	 *
	 * @param codeNm 코드명
	 * @return 해당 코드의 데이터(항목값) 목록
	 */
	@RequestMapping(value = "/getCodeDataListByNm", method = RequestMethod.GET)
	public List<StdCodeDataVo> getCodeDataListByNm(String codeNm, String dictId) {
		return sqlSessionTemplate.selectList("codedata.selectCodeDataListByNm", dictService.nameParam(dictId, "codeNm", codeNm));
	}

	/**
	 * 104번 WP6-3 — 코드 그룹의 값 (코드 유형에 맞게). 통합 코드는 하위 코드 값을 모아 주고(값마다 하위 코드 그룹),
	 * 계층 코드는 값마다 상위 코드값이 있다. 개별 코드는 값이 없다.
	 */
	@RequestMapping(value = "/getCodeValues", method = RequestMethod.GET)
	public Map<String, Object> getCodeValues(String codeGrp, String dictId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			return codeTypeService.values(session, resolveDictOrDefault(dictId), codeGrp);
		}
	}

	/**
	 * 코드데이터(항목값) 단건 등록
	 *
	 * @param dataVo 코드데이터 정보
	 * @return 등록 결과
	 */
	@RequestMapping(value = "/createCodeData", method = RequestMethod.POST)
	public Mono<Response> createCodeData(@RequestBody StdCodeDataVo dataVo) {
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return Mono.just(rb0);
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 98번 — 코드 화면에서 고른 사전에 넣는다.
		dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
		log.info(">> createCodeData : {}", dataVo);

		Response result = new Response();
		try (SqlSession chk = sqlSessionFactory.openSession()) {
			// 104번 WP6-3 — 코드 유형별 값 검사 (계층 상위 코드값, 파생 범위, 통합·개별 거부)
			codeTypeService.checkValue(chk, dataVo);
		} catch (IllegalArgumentException e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return Mono.just(result);
		}
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_CODE_DATA", "CODE_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return Mono.just(rb0);
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateCodeData : {}", dataVo);

		Response result = new Response();
		try (SqlSession chk = sqlSessionFactory.openSession()) {
			// 104번 WP6-3 — 값의 사전은 저장된 행에서 읽어 코드 유형별 값 검사. 값을 바꾸면 이 값을 상위로 쓰는 계층 값이 없어야 한다
			StdCodeDataVo cur = chk.selectOne("codeType.selectCodeDataById", dataVo.getId());
			if (cur == null) throw new IllegalArgumentException("코드값을 찾을 수 없습니다.");
			dataVo.setDictId(cur.getDictId());
			if (!cur.getCodeVal().equals(dataVo.getCodeVal()) || !cur.getCodeGrp().equals(dataVo.getCodeGrp())) {
				codeTypeService.checkValueDeletable(chk, cur);
			}
			codeTypeService.checkValue(chk, dataVo);
		} catch (IllegalArgumentException e) {
			result.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
			return Mono.just(result);
		}
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_CODE_DATA", "CODE_ID", ids(dataVos, StdCodeDataVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		Response result = new Response();
		try {
			// 110번 3.12-7-c·d — 화면은 id 만 보내므로 지우기 전에 읽어 항목마다 이력(대상명·이전 값)을 남긴다
			List<StdCodeDataVo> prevs = new ArrayList<>();
			for (StdCodeDataVo vo : dataVos) {
				StdCodeDataVo prev = sqlSessionTemplate.selectOne("codedata.selectCodeDataInfoById", vo.getId());
				if (prev != null) prevs.add(prev);
			}
			// 104번 WP6-3 — 계층 코드값이 상위로 쓰는 값은 지우지 않는다 (함께 지우는 하위 값은 세지 않는다)
			java.util.Set<String> delIds = new java.util.HashSet<>();
			for (StdCodeDataVo vo : dataVos) delIds.add(vo.getId());
			try (SqlSession chk = sqlSessionFactory.openSession()) {
				for (StdCodeDataVo vo : dataVos) {
					StdCodeDataVo cur = chk.selectOne("codeType.selectCodeDataById", vo.getId());
					if (cur == null) continue;
					Map<String, Object> code = codeTypeService.codeByGrp(chk, cur.getDictId(), cur.getCodeGrp());
					if (code == null) continue;
					Map<String, Object> p = dictService.nameParam(cur.getDictId(), "codeGrp", cur.getCodeGrp());
					p.put("codeVal", cur.getCodeVal());
					List<StdCodeDataVo> kids = chk.selectList("codeType.selectChildValues", p);
					long left = kids.stream().filter(k -> !delIds.contains(k.getId())).count();
					if (left > 0) {
						result.setResultInfo(RestResult.CODE_500.getCode(), "코드값 '" + cur.getCodeVal() + "' 를 상위로 쓰는 계층 코드값이 " + left + "건 있어 지울 수 없습니다.");
						return Mono.just(result);
					}
				}
			}
			sqlSessionTemplate.delete("codedata.deleteCodeDatas", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			for (StdCodeDataVo p : prevs) {
				saveChangeHistory("DELETE", "CODE_DATA", p.getId(), p.getCodeNm(), p.toString(), null,
						"코드데이터 삭제: " + p.getCodeNm() + " / " + p.getCodeVal());
			}
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
			@RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {

		log.info(">> started uploadCodeDataList file : {}", excelFile.getOriginalFilename());
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);

		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadCodeDataList", dictId);

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
	public void downloadCodeDataListExcel(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download code excel started : {}", searchKey);

		try {
			excelDownloadService.getCodeDataListExcel(searchKey, dictId, request, response);
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return Mono.just(rb0);
		dataVo.setId(StringUtils.getUUID());
		dataVo.setCretUserId(sessionService.getUserId());
		// 98번 — 도메인 화면에서 고른 사전에 넣는다.
		dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
		// 사용자가 어드민인 경우에 자동승인 처리
		if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
			dataVo.setAprvYn("Y");
		}
		log.info(">> createDomain : {}", dataVo);

		Response result = new Response();
		try {
			// 86번 #30 — 입력 검증 강화 (raw DB 에러 노출 방지 + 빈값/이상치 차단)
			validateDomainInput(dataVo);
			// 중복 체크: 같은 사전에 동일 도메인명이 이미 존재하면 승인 상태에 따라 메시지 분기
			StdDomainVo existingDomain = sqlSessionTemplate.selectOne("domain.selectDomainInfoByNm", dictService.nameParam(dataVo.getDictId(), "domainNm", dataVo.getDomainNm()));
			if (existingDomain != null) {
				String dupMsg = "Y".equals(existingDomain.getAprvYn())
						? "이미 승인된 도메인명입니다."
						: "승인 대기 중인 도메인명입니다.";
				throw new Exception(dupMsg);
			}
			sqlSessionTemplate.insert("domain.insertDomain", dataVo);
			if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
				result.setResultInfo(RestResult.CODE_200.getCode(),
						pendingMsg(openAprv("DOMAIN", dataVo.getId(), dataVo.getDomainNm(), dataVo.getDictId())));
			} else {
				result.setResultInfo(RestResult.CODE_200);
			}
			// 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
			if (sessionService.hasPerm(PermCode.STD_MANAGE)) {
				saveChangeHistory("INSERT", "DOMAIN", dataVo.getId(), dataVo.getDomainNm(),
						null, dataVo.toString(), "도메인 등록: " + dataVo.getDomainNm());
			}
		} catch (Exception e) {
			log.error(">> createDomain failed : {}", e.getMessage());
			String dupMsg = StdInputRule.translateDuplicateError(e.getMessage());
			String userMsg = dupMsg != null ? dupMsg : translateDomainInsertError(e, dataVo);
			// 86번 #30 — raw DB stacktrace 차단
			if (userMsg != null && (userMsg.startsWith("\r\n### Error") || userMsg.contains("PSQLException") || userMsg.contains("org.postgresql"))) {
				userMsg = "도메인 등록 중 오류가 발생했습니다. 입력값을 확인해주세요.";
			}
			result.setResultInfo(RestResult.CODE_500.getCode(), userMsg);
		}

		return Mono.just(result);
	}

	/** 86번 #29 — 단어 입력 공통 검증 (createWord / updateWord 재사용) */
	void validateWordInput(StdWordVo vo) throws Exception {   // 109번 #11 — 결재자 수정 후 승인(AprvController)도 쓴다
		String wnm = vo.getWordNm();
		if (wnm == null || wnm.trim().isEmpty()) {
			throw new Exception("단어 한글명은 필수입니다.");
		}
		if (wnm.length() > 100) {
			throw new Exception("단어 한글명이 너무 깁니다 (최대 100자, 입력 길이: " + wnm.length() + ")");
		}
		// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자(켜면 대문자로 바꿔 받고, 끄면 소문자 거부)
		vo.setWordEngAbrvNm(dictService.checkWordEngAbrv(vo.getDictId(), vo.getWordEngAbrvNm()));
		String abrv = vo.getWordEngAbrvNm();
		if (abrv == null || abrv.trim().isEmpty()) {
			throw new Exception("단어 영문약어는 필수입니다.");
		}
		if (abrv.length() > 100) {
			throw new Exception("단어 영문약어가 너무 깁니다 (최대 100자, 입력 길이: " + abrv.length() + ")");
		}
		// 86번 #42 — 단어는 원자(atomic). 언더바는 용어(단어 조합)의 구분자라 단어 영문약어에 허용 안 함
		// 110번 3.12-12-c — 엑셀 일괄등록·한글컬럼 일괄 표준화와 같은 규칙(StdInputRule)
		String abrvErr = StdInputRule.wordEngAbrvError(abrv);
		if (abrvErr != null) {
			throw new Exception(abrvErr);
		}
		String eng = vo.getWordEngNm();
		if (eng == null || eng.trim().isEmpty()) {
			throw new Exception("단어 영문명은 필수입니다.");
		}
		if (eng.length() > 100) {
			throw new Exception("단어 영문명이 너무 깁니다 (최대 100자, 입력 길이: " + eng.length() + ")");
		}
		// 86번 #42 — 단어 영문명도 원자. 공백/언더바 불허 (CamelCase 또는 단일 토큰)
		if (!eng.matches("^[A-Za-z][A-Za-z0-9]*$")) {
			throw new Exception("단어 영문명은 영문(A-Z,a-z)으로 시작하고 영문/숫자(0-9)만 사용할 수 있습니다. (입력값: " + eng + ")");
		}
		// 104번 WP6-13 — 단어 영문명 중복을 허용하지 않는 사전
		dictService.checkWordEngNmDup(vo.getDictId(), eng, vo.getId());
	}

	/** 86번 #30 — 도메인 입력 공통 검증 (createDomain / updateDomain 재사용) */
	private void validateDomainInput(StdDomainVo vo) throws Exception {
		// domainNm: 필수 + 100자
		String dnm = vo.getDomainNm();
		if (dnm == null || dnm.trim().isEmpty()) {
			throw new Exception("도메인명은 필수입니다.");
		}
		if (dnm.length() > 100) {
			throw new Exception("도메인명이 너무 깁니다 (최대 100자, 입력 길이: " + dnm.length() + ")");
		}
		// domainGrpNm: 필수 + 100자
		String grp = vo.getDomainGrpNm();
		if (grp == null || grp.trim().isEmpty()) {
			throw new Exception("도메인 그룹명은 필수입니다.");
		}
		if (grp.length() > 100) {
			throw new Exception("도메인 그룹명이 너무 깁니다 (최대 100자)");
		}
		// domainClsfNm: 필수 + 100자
		String clsf = vo.getDomainClsfNm();
		if (clsf == null || clsf.trim().isEmpty()) {
			throw new Exception("도메인 분류명은 필수입니다.");
		}
		if (clsf.length() > 100) {
			throw new Exception("도메인 분류명이 너무 깁니다 (최대 100자)");
		}
		// domainDesc: 필수 + 500자
		String desc = vo.getDomainDesc();
		if (desc == null || desc.trim().isEmpty()) {
			throw new Exception("도메인 설명은 필수입니다.");
		}
		if (desc.length() > 500) {
			throw new Exception("도메인 설명이 너무 깁니다 (최대 500자, 입력 길이: " + desc.length() + ")");
		}
		// dataType: 필수 + 50자 + 허용 타입 화이트리스트 (대소문자 무시)
		String dt = vo.getDataType();
		if (dt == null || dt.trim().isEmpty()) {
			throw new Exception("데이터 타입은 필수입니다.");
		}
		if (dt.length() > 50) {
			throw new Exception("데이터 타입이 너무 깁니다 (최대 50자)");
		}
		String dtUp = dt.trim().toUpperCase();
		java.util.Set<String> ALLOWED_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
				"VARCHAR","VARCHAR2","CHAR","NVARCHAR","NVARCHAR2","NCHAR","TEXT","CLOB","NCLOB",
				"NUMBER","NUMERIC","DECIMAL","INT","INTEGER","BIGINT","SMALLINT","TINYINT",
				"FLOAT","DOUBLE","REAL",
				"DATE","TIME","TIMESTAMP","DATETIME",
				"BLOB","BYTEA","BINARY","VARBINARY",
				"BOOLEAN","BOOL","BIT"));
		if (!ALLOWED_TYPES.contains(dtUp)) {
			throw new Exception("지원하지 않는 데이터 타입입니다: " + dt + " (허용: VARCHAR/CHAR/NUMBER/DATE/TIMESTAMP/INT/DECIMAL 등)");
		}
		// dataLen: 음수 차단 (smallint 범위 초과는 JSON 역직렬화에서 미리 잡힘). 0 허용 (DATE 등 길이 없는 타입)
		short len = vo.getDataLen();
		if (len < 0) {
			throw new Exception("데이터 길이는 0 이상이어야 합니다 (입력값: " + len + ")");
		}
		// 114 권고 채택(10-28)·110번 3.12-15-c — 빈칸은 지금처럼 0(길이 없음)이지만, 길이가 있어야 하는 타입(VARCHAR 등)은 1 이상
		String lenErr = StdInputRule.domainLenError(dt, len);
		if (lenErr != null) {
			throw new Exception(lenErr);
		}
		// dataDecimalLen: 음수 차단
		short dec = vo.getDataDecimalLen();
		if (dec < 0) {
			throw new Exception("데이터 소수점 길이는 0 이상이어야 합니다 (입력값: " + dec + ")");
		}
	}

	// 도메인 INSERT/UPDATE 실패 메시지를 사용자가 알기 쉬운 형태로 변환.
	// FK 위반(도메인 그룹/분류 미등록)을 우선 감지하여 친절한 메시지로 바꿈.
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

	/**
	 * 도메인 수정 API - 변경 전 값 조회 후 이력과 함께 저장
	 *
	 * @param dataVo 수정할 도메인 정보
	 * @return 수정 결과
	 */
	@RequestMapping(value = "/updateDomain", method = RequestMethod.POST)
	public Mono<Response> updateDomain(@RequestBody StdDomainVo dataVo) {
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN", "DOMAIN_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "수정 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
		dataVo.setUpdtUserId(sessionService.getUserId());
		log.info(">> updateDomain : {}", dataVo);

		Response result = new Response();
		try {
			// 86번 #30 — updateDomain 도 동일 검증
			validateDomainInput(dataVo);
			// 변경 전 값 조회 — 110번 3.12-7-g: ID 로 찾는다 (이름을 바꾸면 새 이름으로는 못 찾았다)
			StdDomainVo prev = sqlSessionTemplate.selectOne("domain.selectDomainInfoById", dataVo.getId());
			String prevValue = prev != null ? prev.toString() : null;
			// 수정은 승인 여부를 바꾸지 않는다 (요청 VO 의 기본값 'N' 이 이력에 남지 않게)
			if (prev != null) dataVo.setAprvYn(prev.getAprvYn());
			sqlSessionTemplate.update("domain.updateDomain", dataVo);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			saveChangeHistory("UPDATE", "DOMAIN", dataVo.getId(), dataVo.getDomainNm(),
					prevValue, dataVo.toString(), "도메인 수정: " + dataVo.getDomainNm());
		} catch (Exception e) {
			log.error(">> updateDomain failed : {}", e.getMessage());
			String userMsg = translateDomainInsertError(e, dataVo);
			if (userMsg != null && (userMsg.startsWith("\r\n### Error") || userMsg.contains("PSQLException") || userMsg.contains("org.postgresql"))) {
				userMsg = "도메인 수정 중 오류가 발생했습니다. 입력값을 확인해주세요.";
			}
			result.setResultInfo(RestResult.CODE_500.getCode(), userMsg);
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
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN", "DOMAIN_ID", ids(dataVos, StdDomainVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			Response r = new Response();
			r.setResultInfo(RestResult.CODE_500.getCode(), "삭제 권한이 없습니다 (표준 직접 관리 권한 필요).");
			return Mono.just(r);
		}
		Response result = new Response();
		try {
			// 110번 3.12-10-a — 쓰는 용어가 있는 도메인은 지우지 않는다 (TB_TERMS FK 에 ON DELETE 가 없어 DB 오류 원문이 나갔다)
			String using = termsUsingDomainsMsg(ids(dataVos, StdDomainVo::getId));
			if (using != null) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "다음 용어에서 사용 중이므로 삭제할 수 없습니다: " + using);
				return Mono.just(result);
			}
			// 110번 3.12-7-c·d — 화면은 id 만 보내므로 지우기 전에 읽어 항목마다 이력(대상명·이전 값)을 남긴다
			List<StdDomainVo> prevs = new ArrayList<>();
			for (StdDomainVo vo : dataVos) {
				StdDomainVo prev = sqlSessionTemplate.selectOne("domain.selectDomainInfoById", vo.getId());
				if (prev != null) prevs.add(prev);
			}
			sqlSessionTemplate.delete("domain.deleteDomains", dataVos);
			result.setResultInfo(RestResult.CODE_200);
			// 이력 저장
			for (StdDomainVo p : prevs) {
				saveChangeHistory("DELETE", "DOMAIN", p.getId(), p.getDomainNm(), p.toString(), null, "도메인 삭제: " + p.getDomainNm());
			}
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
		return sqlSessionTemplate.selectList("domain.selectDomainList", pendingScope(withDict(retCond)));
	}

	/**
	 * 도메인명으로 도메인 정보 조회
	 *
	 * @param domainNm 도메인명
	 * @return 해당 도메인 정보 목록
	 */
	@RequestMapping(value = "/getDomainInfoByNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByNm(String domainNm, String dictId) {
		// 98번 — 승인 화면은 신청 항목의 사전을 보낸다.
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByNm", dictService.nameParam(dictId, "domainNm", domainNm));
	}

	/**
	 * 도메인분류명으로 도메인 정보 조회
	 *
	 * @param clsfNm 도메인분류명
	 * @param dictId 표준사전 (없으면 기본 사전)
	 * @return 해당 분류의 도메인 목록
	 */
	@RequestMapping(value = "/getDomainInfoByClsfNm", method = RequestMethod.GET)
	public List<StdDomainVo> getDomainInfoByClsfNm(String clsfNm, String dictId, String schAprvYn) {
		Map<String, Object> p = dictService.nameParam(dictId, "domainClsfNm", clsfNm);
		// 110번 3.12-5-e-2 — 용어·코드 화면의 도메인 후보는 승인된 도메인만 (schAprvYn=Y)
		p.put("schAprvYn", schAprvYn);
		return sqlSessionTemplate.selectList("domain.selectDomainInfoByClsfNm", p);
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return np;
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return rb0;

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());
			// 98번 — 도메인 그룹 화면에서 고른 사전에 넣는다.
			dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
			// 104번 WP6-4 — 상위 그룹 (같은 사전, 3단계까지)
			checkDomainGroupTree(dataVo.getDictId(), dataVo.getId(), dataVo.getParentGrpId());
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
	 * 104번 WP6-4 — 상위 그룹 검사. 상위는 같은 사전의 그룹이어야 하고, 자기 자신·하위 그룹을 상위로 둘 수 없으며,
	 * 옮긴 뒤 깊이가 3단계를 넘으면 안 된다(옮기는 그룹 아래 단계까지 센다). 상위가 비면(최상위) 검사하지 않는다.
	 */
	private void checkDomainGroupTree(String dictId, String id, String parentGrpId) throws Exception {
		if (parentGrpId == null || parentGrpId.isEmpty()) return;
		List<StdDomainGroupVo> all = sqlSessionTemplate.selectList("domain.selectDomainGroupList", dictService.dictParam(dictId));
		Map<String, StdDomainGroupVo> byId = new HashMap<>();
		Map<String, List<String>> children = new HashMap<>();
		for (StdDomainGroupVo g : all) {
			byId.put(g.getId(), g);
			if (g.getParentGrpId() != null) children.computeIfAbsent(g.getParentGrpId(), k -> new ArrayList<>()).add(g.getId());
		}
		StdDomainGroupVo parent = byId.get(parentGrpId);
		if (parent == null) {
			throw new Exception("상위 도메인 그룹을 찾을 수 없습니다 (같은 사전의 그룹만 상위로 지정할 수 있습니다).");
		}
		for (StdDomainGroupVo p = parent; p != null; p = p.getParentGrpId() == null ? null : byId.get(p.getParentGrpId())) {
			if (p.getId().equals(id)) throw new Exception("자기 자신이나 하위 그룹을 상위 그룹으로 지정할 수 없습니다.");
		}
		int height = groupHeight(id, children);
		if (parent.getLevel() + height > 3) {
			throw new Exception("도메인 그룹은 3단계까지입니다 (상위 '" + parent.getPath() + "' 아래로 옮기면 " + (parent.getLevel() + height) + "단계가 됩니다).");
		}
	}

	/** 그룹과 그 아래 단계 수 (하위가 없으면 1) */
	private static int groupHeight(String id, Map<String, List<String>> children) {
		int h = 0;
		for (String c : children.getOrDefault(id, new ArrayList<>())) h = Math.max(h, groupHeight(c, children));
		return h + 1;
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return np;
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN_GRP", "DOMAIN_GRP_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return rb0;

		Response result = new Response();

		try {
			if (dataVo.getId() == null || dataVo.getId().isEmpty()) {
				result.setResultInfo(RestResult.CODE_500.getCode(), "필수 파라미터 누락: id");
				return result;
			}

			dataVo.setUpdtUserId(sessionService.getUserId());
			// 104번 WP6-4 — 상위 그룹을 바꾸면(이동) 같은 사전·순환·3단계를 본다. 화면은 사전을 보내지 않아 그룹에서 읽는다
			if (dataVo.getParentGrpId() != null) {
				StdDomainGroupVo cur = sqlSessionTemplate.selectOne("domain.selectDomainGroupById", dataVo.getId());
				if (cur == null) {
					result.setResultInfo(RestResult.CODE_500.getCode(), "도메인 그룹을 찾을 수 없습니다.");
					return result;
				}
				checkDomainGroupTree(cur.getDictId(), dataVo.getId(), dataVo.getParentGrpId());
			}

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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN_GRP", "DOMAIN_GRP_ID", ids(dataVos, StdDomainGroupVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		Response result = new Response();
		try {
			// 104번 WP6-4 — 하위 그룹·분류·도메인이 있는 그룹은 지우지 않는다 (선택한 하위 그룹을 함께 지우는 경우는 허용)
			java.util.Set<String> ids = new java.util.HashSet<>();
			for (StdDomainGroupVo v : dataVos) ids.add(v.getId());
			for (StdDomainGroupVo v : dataVos) {
				Map<String, Object> use = sqlSessionTemplate.selectOne("domain.selectDomainGroupUse", v.getId());
				if (use == null) continue;
				long child = ((Number) use.get("childCnt")).longValue();
				if (child > 0) {
					List<StdDomainGroupVo> children = sqlSessionTemplate.selectList("domain.selectDomainGroupChildren", v.getId());
					child = children.stream().filter(c -> !ids.contains(c.getId())).count();
				}
				long clsf = ((Number) use.get("clsfCnt")).longValue();
				long dom = ((Number) use.get("domainCnt")).longValue();
				if (child > 0 || clsf > 0 || dom > 0) {
					result.setResultInfo(RestResult.CODE_500.getCode(), "도메인 그룹 '" + use.get("domainGrpNm") + "' 에 하위 그룹 " + child
							+ "개·도메인 분류 " + clsf + "개·도메인 " + dom + "개가 있어 지울 수 없습니다.");
					return Mono.just(result);
				}
			}
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
	public List<StdDomainGroupVo> getDomainGroupList(String dictId) {
		return sqlSessionTemplate.selectList("domain.selectDomainGroupList", dictService.dictParam(dictId));
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return np;
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlocked(dataVo.getDictId());
		if (rb0 != null) return rb0;

		Response result = new Response();

		try {

			dataVo.setId(StringUtils.getUUID());
			dataVo.setCretUserId(sessionService.getUserId());
			// 98번 — 도메인 분류 화면에서 고른 사전에 넣는다.
			dataVo.setDictId(resolveDictOrDefault(dataVo.getDictId()));
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return np;
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN_CLSF", "DOMAIN_CLSF_ID", java.util.Collections.singletonList(dataVo.getId()));
		if (rb0 != null) return rb0;
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
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		Response rb0 = refBlockedOf("TB_DOMAIN_CLSF", "DOMAIN_CLSF_ID", ids(dataVos, StdDomainClassificationVo::getId));
		if (rb0 != null) return Mono.just(rb0);
		Response result = new Response();
		try {
			// 109번 #13 — 도메인이 있는 분류는 지우지 않는다 (그룹 삭제와 같다). FK(tb_domain_fk_2)가 ON DELETE CASCADE 라
			// 분류를 지우면 그 분류의 도메인(승인된 것 포함)이 이력 없이 함께 지워졌다
			for (StdDomainClassificationVo v : dataVos) {
				Map<String, Object> use = sqlSessionTemplate.selectOne("domain.selectDomainClsfUse", v.getId());
				if (use != null && ((Number) use.get("domainCnt")).longValue() > 0) {
					result.setResultInfo(RestResult.CODE_500.getCode(), "도메인 분류 '" + use.get("domainClsfNm") + "' 에 도메인 "
							+ use.get("domainCnt") + "개가 있어 지울 수 없습니다.");
					return Mono.just(result);
				}
			}
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
	public List<StdDomainClassificationVo> getDomainClassificationList(String dictId) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationList", dictService.dictParam(dictId));
	}

	/**
	 * 도메인분류명으로 도메인 분류 검색 (LIKE 검색)
	 *
	 * @param domainClsfNm 도메인분류명 키워드
	 * @return 매칭되는 도메인 분류 목록
	 */
	@RequestMapping(value = "/getDomainClassificationListByNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByNm(String domainClsfNm, String dictId) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByNm", dictService.nameParam(dictId, "domainClsfNm", "%" + domainClsfNm + "%"));
	}

	/**
	 * 도메인그룹명으로 도메인 분류 조회
	 *
	 * @param domainGrpNm 도메인 그룹명
	 * @return 해당 그룹의 도메인 분류 목록
	 */
	@RequestMapping(value = "/getDomainClassificationListByDomainGrpNm", method = RequestMethod.GET)
	public List<StdDomainClassificationVo> getDomainClassificationListByGrpNm(String domainGrpNm, String dictId) {
		return sqlSessionTemplate.selectList("domain.selectDomainClassificationListByGrpNm", dictService.nameParam(dictId, "domainGrpNm", domainGrpNm));
	}

	/**
	 * 도메인 Excel 일괄 업로드 - q-executor로 전달
	 *
	 * @param request HTTP 요청
	 * @param excelFile 업로드 Excel 파일
	 * @return 업로드 처리 결과
	 */
	@RequestMapping(value = "/uploadDomains", method = RequestMethod.POST)
	public Mono<Response> uploadDomains(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {

		log.info(">> started uploadDomains file : {}", excelFile.getOriginalFilename());

		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadDomains", dictId);

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
	public void downloadDomains(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download domains excel started : {}", searchKey);

		try {
			excelDownloadService.getDomainsExcel(searchKey, dictId, request, response);
		} catch (Exception e) {
			log.error(">> download domains excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 도메인 그룹 Excel 일괄 업로드 - q-executor로 전달
	 */
	@RequestMapping(value = "/uploadDomainGroups", method = RequestMethod.POST)
	public Mono<Response> uploadDomainGroups(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {
		log.info(">> started uploadDomainGroups file : {}", excelFile.getOriginalFilename());
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadDomainGroups", dictId);
		log.info(">> finished uploadDomainGroups file : {}", excelFile.getOriginalFilename());
		return mResponse;
	}

	/**
	 * 도메인 그룹 목록 Excel 다운로드
	 */
	@RequestMapping(value = "/downloadDomainGroups", method = RequestMethod.GET)
	public void downloadDomainGroups(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download domain groups excel started : {}", searchKey);
		try {
			excelDownloadService.getDomainGroupsExcel(searchKey, dictId, request, response);
		} catch (Exception e) {
			log.error(">> download domain groups excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 도메인 분류 Excel 일괄 업로드 - q-executor로 전달
	 */
	@RequestMapping(value = "/uploadDomainClsfs", method = RequestMethod.POST)
	public Mono<Response> uploadDomainClsfs(HttpServletRequest request, @RequestParam("file") MultipartFile excelFile,
			@RequestParam(value = "dictId", required = false) String dictId) {
		log.info(">> started uploadDomainClsfs file : {}", excelFile.getOriginalFilename());
		Response np = noStdManage();   // 109번 #13
		if (np != null) return Mono.just(np);
		Mono<Response> mResponse = forwardUploadToExecutor(request, excelFile, "/api/std/uploadDomainClsfs", dictId);
		log.info(">> finished uploadDomainClsfs file : {}", excelFile.getOriginalFilename());
		return mResponse;
	}

	/**
	 * 도메인 분류 목록 Excel 다운로드
	 */
	@RequestMapping(value = "/downloadDomainClsfs", method = RequestMethod.GET)
	public void downloadDomainClsfs(HttpServletRequest request, HttpServletResponse response, String searchKey, String dictId) {
		log.info(">> download domain clsfs excel started : {}", searchKey);
		try {
			excelDownloadService.getDomainClsfsExcel(searchKey, dictId, request, response);
		} catch (Exception e) {
			log.error(">> download domain clsfs excel failed : {}", e.getMessage());
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
		if (!sessionService.hasPerm(PermCode.STD_APPROVE)) {// 승인 권한이 없으면 자신 신청내역만 확인
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
		return Mono.just(aprvItems(dataVos, null));
	}

	/**
	 * 표준 승인·반려 — 항목마다 한 트랜잭션.
	 * 109번 #4 — 결재 문서의 단계 진행(AprvService.act)도 항목 처리와 같은 세션·트랜잭션이다. 항목 처리가 실패하면 단계 진행도 되돌린다.
	 * beforeAct 가 있으면(결재자 수정 후 승인 — AprvController.editAndApprove) 그 항목 수정을 같은 트랜잭션에서 먼저 한다.
	 */
	Response aprvItems(List<StdApproveStatVo> dataVos, java.util.function.Consumer<SqlSession> beforeAct) {
		Response result = new Response();
		List<Map<String, Object>> warnings = new ArrayList<>();

		// DEF-15 — 승인·반려는 승인 권한(관리자 또는 STD_APPROVE)만. 화면에서는 메뉴가 안 보이지만 이 API 는 열려 있어
		// 일반 사용자가 자기 신청을 스스로 승인할 수 있었다. 서버에서 다시 막는다.
		// 104번 WP1 — 결재 문서가 있는 항목은 승인 권한 대신 그 문서의 현재 단계 결재자(관리자 대결 포함)가 처리한다.
		// 단계 승인이면 단계만 넘기고, 마지막 단계 승인·반려일 때 아래 기존 처리를 그대로 한다.
		boolean stdApprover = sessionService.hasPerm(PermCode.STD_APPROVE);
		List<String> stepMsgs = new ArrayList<>();
		// 109번 #21 — 건별 실패를 모아 둔다. 예전에는 앞 건의 403·500 을 뒤 건의 200 이 덮어써 실패가 성공으로 보였다
		List<String[]> fails = new ArrayList<>();   // {항목명, 메시지}
		int failCode = 0;
		int doneCnt = 0;

		for (StdApproveStatVo dataVo : dataVos) {
			dataVo.setId(StringUtils.getUUID());
			dataVo.setAprvUserId(sessionService.getUserId());
			String itemNm = dataVo.getReqItemNm() != null ? dataVo.getReqItemNm() : dataVo.getReqItemId();

			// 109번 #1 — 승인값은 2(승인)·3(반려)만 받는다. 그 밖의 값(빠지면 0)이면 결재 엔진에는 반려로, 업무 처리에는 승인으로
			// 갈려 결재 단계를 건너뛰고 항목이 승인됐다. 아래에서는 엔진 호출과 업무 분기에 같은 approve 값을 쓴다.
			short stat = dataVo.getAprvStat();
			if (stat != NDQualityApproveStat.APPROVED.getValue() && stat != NDQualityApproveStat.REJECTED.getValue()) {
				fails.add(new String[] { itemNm, "승인값(aprvStat)은 2(승인) 또는 3(반려)이어야 합니다: " + stat });
				if (failCode == 0) failCode = 400;
				continue;
			}
			boolean approve = stat == NDQualityApproveStat.APPROVED.getValue();

			// 109번 #8 — 승인 대기 항목만, 요청 유형이 항목의 실제 유형과 같을 때만, 참조 사전 항목은 안 된다.
			// 예전에는 승인된 항목도 "반려"로 물리 삭제됐고, 용어를 코드로 보내면 용어 결재선을 건너뛰었다
			Map<String, Object> target = Arrays.asList("WORD", "TERMS", "CODE", "DOMAIN").contains(dataVo.getReqTp())
					? sqlSessionTemplate.selectOne("approve.selectAprvTarget", dataVo) : null;
			String targetErr = aprvTargetError(dataVo, target);
			if (targetErr != null) {
				fails.add(new String[] { itemNm, targetErr });
				if (failCode == 0) failCode = 400;
				continue;
			}
			// 110번 8.5-9 — 처리 행의 요청자·요청일은 항목의 등록자·등록 시각 (화면이 보낸 값을 쓰지 않는다 — 예전에는 요청일이 처리 시각,
			// 결재함에서 처리하면 요청자가 비었다)
			dataVo.setReqUserId((String) target.get("cretUserId"));
			dataVo.setReqCretDt((String) target.get("cretDt"));
			// 110번 8.5-6 — 처리 사유 칸(TB_APRV_STATS.APRV_STAT_UPDT_RSN)은 50자. 넘으면 INSERT 가 실패해 처리가 안 됐다
			// (결재 문서의 의견에는 전문을 넘긴다)
			String fullRsn = dataVo.getAprvStatUpdtRsn();
			dataVo.setAprvStatUpdtRsn(rsn50(fullRsn));

			Map<String, Object> aprvDoc = aprvService.openDoc(dataVo.getReqTp(), dataVo.getReqItemId());
			if (aprvDoc == null && !stdApprover) {
				fails.add(new String[] { itemNm, "승인·반려 권한이 없습니다 (표준 승인 권한 필요)." });
				if (failCode == 0) failCode = 403;
				continue;
			}

			SqlSession session = sqlSessionFactory.openSession();
			List<AprvService.Noti> notis = new ArrayList<>();

			try {
				NDQualityStdObjectType objType = NDQualityStdObjectType.valueOf(dataVo.getReqTp());
				if (beforeAct != null) beforeAct.accept(session);

				// 단어 반려 가드 — 결재 문서 단계를 넘기기 전에 먼저 본다 (반려는 어느 단계든 바로 끝나므로)
				if (objType == NDQualityStdObjectType.WORD && !approve) {
					// 승인된 용어가 이 단어를 참조 중이면 물리삭제가 FK(tb_terms_words_fk_2, ON DELETE 없음)
					// 위반으로 터지면서 트랜잭션 전체가 롤백된다. raw PSQLException 이 그대로 나가지 않도록
					// deleteWords 와 동일한 형태의 친화 메시지로 미리 차단한다.
					List<String> approvedTerms = session.selectList(
							"approve.selectApprovedTermsByWordId", dataVo.getReqItemId());
					if (approvedTerms != null && !approvedTerms.isEmpty()) {
						String termList = approvedTerms.size() <= 3
								? String.join(", ", approvedTerms)
								: String.join(", ", approvedTerms.subList(0, 3)) + " 외 " + (approvedTerms.size() - 3) + "건";
						throw new Exception("승인된 용어에서 사용 중이므로 반려할 수 없습니다: " + termList
								+ " (해당 용어를 먼저 정리한 뒤 반려하세요)");
					}
				}
				// 110번 8.5-4 — 도메인 반려 가드: 이 도메인을 쓰는 용어가 있으면 물리 삭제가 FK(tb_terms_fk, ON DELETE 없음)로 실패해
				// DB 원문이 나갔다. 단어처럼 미리 이름을 알려 거부한다
				if (objType == NDQualityStdObjectType.DOMAIN && !approve) {
					String using = termsUsingDomainsMsg(java.util.Collections.singletonList(dataVo.getReqItemId()));
					if (using != null) {
						throw new Exception("이 도메인을 쓰는 용어가 있어 반려할 수 없습니다: " + using
								+ " (해당 용어를 먼저 정리한 뒤 반려하세요)");
					}
				}

				if (aprvDoc != null) {
					AprvService.Result step = aprvService.act(session, (String) aprvDoc.get("docId"), sessionService.getUserId(),
							approve, fullRsn);
					notis.addAll(step.notis);
					if (!step.done) {
						session.commit();
						websocketService.sendUserNotis(notis);
						stepMsgs.add(dataVo.getReqItemNm() + ": " + step.message);
						doneCnt++;
						continue;
					}
				}

				// 용어 승인 시: 구성 단어 미승인 여부 체크 (결재 문서가 있으면 마지막 단계 승인에서만 — 걸리면 위 단계 진행도 되돌린다)
				// DEF-13 — 코드도 TB_TERMS 에 저장되는 용어라 같은 가드를 태워야 한다.
				// TERMS 만 검사하면 코드로 등록한 용어는 미승인 단어로 이뤄져도 승인됐다.
				if ((objType == NDQualityStdObjectType.TERMS || objType == NDQualityStdObjectType.CODE) && approve) {
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

				if (!approve) {
					// === 반려: 물리 삭제 ===
					session.insert("approve.insertStdAprvStat", dataVo);
					// 110번 8.5-7 — 반려도 변경 이력(DELETE·관리자 반려)에 남긴다. 지우기 전 값을 읽어 둔다 {대상 유형, id, 이름, 이전 값, 요약}
					List<String[]> rejectHist = new ArrayList<>();
					rejectHist.add(new String[] { histTargetType(objType), dataVo.getReqItemId(), dataVo.getReqItemNm(),
							aprvItemValue(session, objType, dataVo.getReqItemId()),
							"반려: " + dataVo.getReqItemNm() + (fullRsn != null && !fullRsn.isEmpty() ? " (" + dataVo.getAprvStatUpdtRsn() + ")" : "") });

					switch (objType) {
						case WORD:
							// 단어 반려 → 연관 미승인 용어 cascade 삭제
							List<Map<String, Object>> relatedTerms = session.selectList(
									"approve.selectUnapprovedTermsByWordId", dataVo.getReqItemId());
							if (relatedTerms != null && !relatedTerms.isEmpty()) {
								// cascade 대상 용어의 반려 이력도 남김
								for (Map<String, Object> t : relatedTerms) {
									StdApproveStatVo cascadeVo = new StdApproveStatVo();
									cascadeVo.setId(StringUtils.getUUID());
									cascadeVo.setReqTp("TERMS");
									cascadeVo.setReqItemId((String) t.get("termsId"));
									cascadeVo.setReqItemNm((String) t.get("termsNm"));
									cascadeVo.setAprvStat((short) NDQualityApproveStat.REJECTED.getValue());
									cascadeVo.setAprvUserId(sessionService.getUserId());
									// 110번 8.5-8 — 요청자·요청일은 그 용어의 등록자·등록 시각 (비어 있어 요청자의 변경 요청 현황에 안 나왔다)
									cascadeVo.setReqUserId((String) t.get("cretUserId"));
									cascadeVo.setReqCretDt((String) t.get("cretDt"));
									cascadeVo.setAprvStatUpdtRsn(rsn50("단어 '" + dataVo.getReqItemNm() + "' 반려에 의한 연쇄 삭제"));
									session.insert("approve.insertStdAprvStat", cascadeVo);
									rejectHist.add(new String[] { "TERM", (String) t.get("termsId"), (String) t.get("termsNm"),
											aprvItemValue(session, NDQualityStdObjectType.TERMS, (String) t.get("termsId")),
											"반려: " + t.get("termsNm") + " (단어 '" + dataVo.getReqItemNm() + "' 반려에 의한 연쇄 삭제)" });
								}
								// 용어 먼저 삭제 (TB_TERMS_WORDS는 FK CASCADE 자동 정리)
								Map<String, Object> cascadeParam = new HashMap<>();
								cascadeParam.put("wordId", dataVo.getReqItemId());
								session.delete("approve.cascadeDeleteTermsByWordReject", cascadeParam);

								// 경고 정보 응답에 포함
								Map<String, Object> warning = new HashMap<>();
								warning.put("wordId", dataVo.getReqItemId());
								warning.put("wordNm", dataVo.getReqItemNm());
								warning.put("relatedTerms", relatedTerms);
								warnings.add(warning);
							}
							// 단어 삭제
							onePending(session.delete("approve.deleteWordByReject", dataVo));
							break;
						case TERMS:
						case CODE:
							onePending(session.delete("approve.deleteTermsByReject", dataVo));
							break;
						case DOMAIN:
							onePending(session.delete("approve.deleteDomainByReject", dataVo));
							break;
					}
					session.commit();
					websocketService.sendUserNotis(notis);
					doneCnt++;
					for (String[] h : rejectHist) {
						saveChangeHistory("DELETE", h[0], h[1], h[2], h[3], null, h[4], "ADMIN_REJECT");
					}

				} else {
					// === 승인 ===
					session.insert("approve.insertStdAprvStat", dataVo);
					switch (objType) {
						case TERMS:
							onePending(session.update("approve.updateTermsAprvStat", dataVo));
							break;
						case WORD:
							onePending(session.update("approve.updateWordAprvStat", dataVo));
							break;
						case DOMAIN:
							onePending(session.update("approve.updateDomainAprvStat", dataVo));
							break;
						case CODE:
							onePending(session.update("approve.updateTermsAprvStat", dataVo));
							break;
					}
					session.commit();
					websocketService.sendUserNotis(notis);
					doneCnt++;

					// 승인 완료 시 변경 이력 저장
					String targetType = objType == NDQualityStdObjectType.TERMS ? "TERM"
							: objType == NDQualityStdObjectType.WORD ? "WORD"
							: objType == NDQualityStdObjectType.CODE ? "CODE" : "DOMAIN";
					String targetNm = dataVo.getReqItemNm();
					String currValue = null;
					try {
						switch (objType) {
							case TERMS:
								List<StdTermsVo> termsInfoList = sqlSessionTemplate.selectList("terms.selectTermsInfoById", dataVo.getReqItemId());
								currValue = !termsInfoList.isEmpty() ? termsInfoList.get(0).toString() : null;
								if (targetNm == null && !termsInfoList.isEmpty()) targetNm = termsInfoList.get(0).getTermsNm();
								break;
							case WORD:
								List<StdWordVo> wordInfoList = sqlSessionTemplate.selectList("word.selectWordInfoById", dataVo.getReqItemId());
								currValue = !wordInfoList.isEmpty() ? wordInfoList.get(0).toString() : null;
								if (targetNm == null && !wordInfoList.isEmpty()) targetNm = wordInfoList.get(0).getWordNm();
								break;
							case DOMAIN:
								// 98번 — 이름이 아니라 ID 로 찾는다. 사전이 여럿이면 같은 이름의 도메인이 사전마다 있다.
								Object domainObj = sqlSessionTemplate.selectOne("domain.selectDomainInfoById", dataVo.getReqItemId());
								currValue = domainObj != null ? domainObj.toString() : null;
								break;
						}
					} catch (Exception ex) {
						log.warn("승인 이력 조회 실패: {}", ex.getMessage());
					}
					saveChangeHistory("INSERT", targetType, dataVo.getReqItemId(), targetNm,
							null, currValue, targetType + " 승인 등록: " + targetNm, "ADMIN_APPROVE");
				}
			} catch (Exception e) {
				session.rollback();
				log.error("update standard approve stat : {}", e.getMessage());
				fails.add(new String[] { itemNm, e.getMessage() });
				if (failCode == 0) failCode = RestResult.CODE_500.getCode();
			} finally {
				session.close();
			}
		}

		// 안내 문구 — 결재 단계만 넘긴 항목(104번 WP1), 단어 반려 시 연관 미승인 용어
		List<String> notes = new ArrayList<>(stepMsgs);
		if (!warnings.isEmpty()) {
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
			notes.add(warnMsg.toString());
		}

		// 109번 #21 — 한 건이라도 실패하면 응답은 실패 코드(첫 실패의 코드). 여러 건이면 건별로 "항목: 사유" 와 처리·실패 건수
		if (!fails.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (String[] f : fails) parts.add(dataVos.size() > 1 ? f[0] + ": " + f[1] : f[1]);
			String head = dataVos.size() > 1 ? "처리 " + doneCnt + "건, 실패 " + fails.size() + "건 — " : "";
			result.setResultInfo(failCode, head + String.join(" / ", parts) + (notes.isEmpty() ? "" : " / " + String.join(" / ", notes)));
		} else if (!notes.isEmpty()) {
			result.setResultInfo(RestResult.CODE_200.getCode(), String.join(" / ", notes));
		} else {
			result.setResultInfo(RestResult.CODE_200);
		}

		return result;
	}

	/**
	 * 109번 #8 — 승인·반려 대상 검사. 대상이 있고, 승인 대기(APRV_YN='N')이고, 요청 유형이 항목의 실제 유형과 같고(용어·코드는
	 * 같은 TB_TERMS 라 CODE_GRP 로 가른다 — 승인 목록과 같은 기준), 참조 사전 항목이 아니어야 한다. 문제가 없으면 null
	 */
	private String aprvTargetError(StdApproveStatVo vo, Map<String, Object> t) {
		if (!Arrays.asList("WORD", "TERMS", "CODE", "DOMAIN").contains(vo.getReqTp()))
			return "요청 유형(reqTp)은 WORD·TERMS·CODE·DOMAIN 중 하나여야 합니다: " + vo.getReqTp();
		if (t == null) return "승인·반려할 항목이 없습니다: " + vo.getReqItemId();
		if (!vo.getReqTp().equals(t.get("reqTp")))
			return "요청 유형(" + vo.getReqTp() + ")이 항목의 유형(" + t.get("reqTp") + ")과 다릅니다.";
		if (!"N".equals(t.get("aprvYn"))) return "승인 대기 항목이 아닙니다 (승인된 항목은 승인·반려할 수 없습니다).";
		try {
			dictService.checkWritable((String) t.get("dictId"));
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
		return null;
	}

	/** 109번 #8 — 승인·반려 문장은 승인 대기 행 하나만 바꾼다. 0건이면 그 사이 다른 처리가 있었던 것 */
	private static void onePending(int n) {
		if (n != 1) throw new IllegalStateException("승인 대기 항목이 아닙니다 (다른 사용자가 먼저 처리했을 수 있습니다).");
	}

	/** 110번 8.5-6 — TB_APRV_STATS.APRV_STAT_UPDT_RSN varchar(50) 에 맞춘다 */
	private static String rsn50(String rsn) {
		return rsn != null && rsn.length() > 50 ? rsn.substring(0, 50) : rsn;
	}

	/** 110번 8.5-7 — 반려(물리 삭제) 전 항목 값 (변경 이력의 이전 값) */
	private String aprvItemValue(SqlSession session, NDQualityStdObjectType objType, String id) {
		Object v;
		switch (objType) {
			case WORD: { List<StdWordVo> l = session.selectList("word.selectWordInfoById", id); v = l.isEmpty() ? null : l.get(0); break; }
			case DOMAIN: v = session.selectOne("domain.selectDomainInfoById", id); break;
			default: { List<StdTermsVo> l = session.selectList("terms.selectTermsInfoById", id); v = l.isEmpty() ? null : l.get(0); break; }
		}
		return v != null ? v.toString() : null;
	}

	/** 승인 유형 → 변경 이력 대상 유형 */
	private static String histTargetType(NDQualityStdObjectType objType) {
		return objType == NDQualityStdObjectType.TERMS ? "TERM"
				: objType == NDQualityStdObjectType.WORD ? "WORD"
				: objType == NDQualityStdObjectType.CODE ? "CODE" : "DOMAIN";
	}


	/**
	 * 반려 항목의 승인 이력 조회 API
	 */
	@GetMapping("/getApprovalHistory")
	public List<Map<String, Object>> getApprovalHistory(@RequestParam String reqItemId, @RequestParam String reqTp) {
		Map<String, Object> params = new HashMap<>();
		params.put("reqItemId", reqItemId);
		params.put("reqTp", reqTp);
		return sqlSessionTemplate.selectList("approve.selectApprovalHistoryByItem", params);
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
	public List<TermAnalysisResult> analyzeTermsBatch(@RequestBody Map<String, Object> body) {
		@SuppressWarnings("unchecked")
		List<String> termNames = (List<String>) body.get("termNames");
		if (termNames == null) termNames = new ArrayList<>();
		// 98번 — 화면에서 고른 사전 안에서만 분석한다.
		String dictId = resolveDictOrDefault(body.get("dictId"));
		Map<String, Object> dp = dictService.dictParam(dictId);
		log.info(">> analyzeTermsBatch started, count: {}, dictId: {}", termNames.size(), dictId);

		// 캐시: 사전의 용어/단어/도메인 로드
		List<StdTermsVo> allTerms = sqlSessionTemplate.selectList("terms.selectAllTermsByNm", dp);
		Map<String, StdTermsVo> termsByNm = new HashMap<>();
		for (StdTermsVo t : allTerms) { termsByNm.put(t.getTermsNm(), t); }

		List<StdWordVo> allWords = sqlSessionTemplate.selectList("word.selectAllWords", dp);
		Map<String, List<StdWordVo>> wordsByNm = new HashMap<>();
		// 동의어 → 원본 단어 매핑 (예: 카테고리 → 범주)
		Map<String, StdWordVo> synmToWord = new HashMap<>();
		for (StdWordVo w : allWords) {
			wordsByNm.computeIfAbsent(w.getWordNm(), k -> new ArrayList<>()).add(w);
			if (w.getAllophSynmLst() != null) {
				for (String syn : w.getAllophSynmLst()) {
					if (syn != null && !syn.trim().isEmpty()) {
						synmToWord.putIfAbsent(syn.trim(), w);
					}
				}
			}
		}

		List<Map<String, Object>> usageCounts = sqlSessionTemplate.selectList("word.selectWordUsageCounts", dp);
		Map<String, Integer> usageMap = new HashMap<>();
		for (Map<String, Object> row : usageCounts) {
			String nm = (String) row.get("wordNm");
			Number cnt = (Number) row.get("cnt");
			if (nm != null && cnt != null) usageMap.put(nm, cnt.intValue());
		}

		List<StdDomainVo> allDomains = sqlSessionTemplate.selectList("domain.selectAllDomains", dp);
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

			// 후보 토큰 세트 구성 (OKT + TB_WORD + TB_WORD_DICT에서 입력에 포함된 단어)
			Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
			for (String wordNm : wordsByNm.keySet()) {
				if (wordNm.length() >= 2 && cleanName.contains(wordNm)) {
					candidateSet.add(wordNm);
				}
			}
			for (String dictNm : wordDict.keySet()) {
				if (dictNm.length() >= 2 && cleanName.contains(dictNm)) {
					candidateSet.add(dictNm);
				}
			}

			// 1순위: DP 기반 최적 분리
			log.info("[TermAnalysis] candidateSet size for '{}': {}", cleanName, candidateSet.size());
			List<String> primaryTokens = dpSplit(cleanName, candidateSet, wordsByNm, wordDict);
			log.info("[TermAnalysis] 1순위 (DP) for '{}': {}", cleanName, primaryTokens);

			// 사후처리: DP 결과에서 미신뢰 토큰 연속 구간을 합쳐 사전(TB_WORD/DICT/동의어) 재검색
			primaryTokens = resolveUncertainRuns(primaryTokens, wordsByNm, wordDict, synmToWord);
			log.info("[TermAnalysis] 1순위 (사후처리) for '{}': {}", cleanName, primaryTokens);

			// 2순위: 1순위에서 미등록 단어를 추가 분리
			List<String> altTokens = generateAlternativeSplit(primaryTokens, candidateSet, wordsByNm);
			log.info("[TermAnalysis] 2순위 for '{}': {}", cleanName, altTokens);

			// 1순위 토큰 → WordAnalysis 변환
			List<TermAnalysisResult.WordAnalysis> wordAnalyses = buildWordAnalyses(primaryTokens, wordsByNm, wordDict, usageMap, synmToWord);
			result.setWords(wordAnalyses);
			result.setRecommendedEngAbrvNm(composeEngAbrv(wordAnalyses));

			// 2순위가 1순위와 다르면 세팅
			if (!primaryTokens.equals(altTokens)) {
				List<TermAnalysisResult.WordAnalysis> altAnalyses = buildWordAnalyses(altTokens, wordsByNm, wordDict, usageMap, synmToWord);
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
		// 98번 — 자동표준화가 어느 사전에 등록하는지. 화면에서 고른 값이 온다.
		String dictId = resolveDictOrDefault(body.get("dictId"));
		// 104번 WP5-14 — 참조 사전은 고치지 못한다
		if (dictService.isRef(dictId)) {
			res.put("success", false);
			res.put("message", refBlocked(dictId).getResultMessage());
			return res;
		}

		if (wordNm == null || wordNm.trim().isEmpty()
				|| wordEngAbrvNm == null || wordEngAbrvNm.trim().isEmpty()
				|| wordEngNm == null || wordEngNm.trim().isEmpty()) {
			res.put("success", false);
			res.put("message", "한글명, 영문약어, 영문명은 필수입니다.");
			return res;
		}

		// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자
		try {
			wordEngAbrvNm = dictService.checkWordEngAbrv(dictId, wordEngAbrvNm.trim());
		} catch (IllegalArgumentException e) {
			res.put("success", false);
			res.put("message", e.getMessage());
			return res;
		}

		// 영문약어 규격 체크 — 110번 3.12-12-c: 화면 단어 등록과 같은 규칙 (대문자 영문으로 시작, 대문자·숫자만)
		String abrvErr = StdInputRule.wordEngAbrvError(wordEngAbrvNm.trim());
		if (abrvErr != null) {
			res.put("success", false);
			res.put("message", abrvErr);
			return res;
		}

		// 금칙어 체크: 등록하려는 단어명이 다른 단어의 금칙어인 경우 등록 차단
		String forbiddenMsg = checkForbiddenWord(wordNm.trim(), dictId);
		if (forbiddenMsg != null) {
			res.put("success", false);
			res.put("message", forbiddenMsg);
			return res;
		}

		// 중복 체크 — 같은 사전 안에서만 본다. 다른 사전의 동명 단어는 별개다.
		List<StdWordVo> existing = sqlSessionTemplate.selectList("word.selectWordInfoByNm", dictService.nameParam(dictId, "wordNm", wordNm.trim()));
		if (existing != null && !existing.isEmpty()) {
			// 이미 등록된 단어 → 기존 정보 반환
			StdWordVo existWord = existing.get(0);
			res.put("success", true);
			res.put("alreadyExists", true);
			// 104번 WP7-8 — 유사어로 맞춘 경우 표준 단어로 바꿨다고 알린다
			if (!existWord.getWordNm().equals(wordNm.trim())) {
				res.put("synonymOf", existWord.getWordNm());
				res.put("message", "'" + wordNm.trim() + "'은(는) '" + existWord.getWordNm() + "'의 유사어입니다. 표준 단어 '" + existWord.getWordNm() + "'를 씁니다.");
			}
			res.put("wordId", existWord.getId());
			res.put("wordNm", existWord.getWordNm());
			res.put("wordEngAbrvNm", existWord.getWordEngAbrvNm());
			res.put("wordEngNm", existWord.getWordEngNm());
			res.put("domainClsfNm", existWord.getDomainClsfNm());
			res.put("wordClsfYn", existWord.getWordClsfYn());
			return res;
		}

		// 104번 WP6-13 — 단어 영문명 중복을 허용하지 않는 사전 (같은 한글명 단어는 위에서 기존 단어로 돌려줬다)
		try {
			dictService.checkWordEngNmDup(dictId, wordEngNm.trim(), null);
		} catch (IllegalArgumentException e) {
			res.put("success", false);
			res.put("message", e.getMessage());
			return res;
		}

		// 유사어 체크: 경고 메시지만 (등록은 진행)
		String synonymMsg = checkSynonymWord(wordNm.trim(), dictId);

		String userId = sessionService.getUserId();
		boolean directApprove = sessionService.hasPerm(PermCode.STD_MANAGE);

		StdWordVo wordVo = new StdWordVo();
		String wordId = StringUtils.getUUID();
		wordVo.setId(wordId);
		wordVo.setDictId(dictId);
		wordVo.setWordNm(wordNm.trim());
		wordVo.setWordEngAbrvNm(wordEngAbrvNm.trim());
		wordVo.setWordEngNm(wordEngNm.trim());
		wordVo.setWordDesc(wordDesc != null ? wordDesc.trim() : wordNm.trim());
		wordVo.setDomainClsfNm(domainClsfNm != null ? domainClsfNm.trim() : "");
		wordVo.setWordClsfYn("N");
		wordVo.setCommStndYn("N");
		wordVo.setCretUserId(userId);
		if (directApprove) wordVo.setAprvYn("Y");
		else wordVo.setAprvYn("N");

		sqlSessionTemplate.insert("word.insertWord", wordVo);

		// 이력 저장 (관리자 즉시 승인 시에만)
		if (directApprove) {
			saveChangeHistory("INSERT", "WORD", wordId, wordVo.getWordNm(),
					null, wordVo.toString(), "단어 등록(표준화 추천): " + wordVo.getWordNm(), "AUTO_RECOMMEND");
		} else {
			openAprv("WORD", wordId, wordVo.getWordNm(), dictId);
		}

		res.put("success", true);
		res.put("alreadyExists", false);
		res.put("wordId", wordId);
		res.put("wordNm", wordVo.getWordNm());
		res.put("wordEngAbrvNm", wordVo.getWordEngAbrvNm());
		res.put("wordEngNm", wordVo.getWordEngNm());
		res.put("domainClsfNm", wordVo.getDomainClsfNm());
		res.put("wordClsfYn", wordVo.getWordClsfYn());
		if (synonymMsg != null) {
			res.put("warning", synonymMsg);
		}
		return res;
	}

	/**
	 * 표준화 추천 - 용어 일괄 등록 API (트랜잭션 사용)
	 *
	 * <p>분석 결과를 바탕으로 용어를 등록한다. 구성 단어는 모두 등록된 단어여야 한다
	 * (화면은 모든 단어가 MATCHED 일 때만 등록하고, 미등록 단어는 [단어등록]으로 먼저 등록한다).</p>
	 * <ul>
	 *   <li>형식단어 체크 (사전이 요구할 때)</li>
	 *   <li>도메인 필수·유효성 체크</li>
	 *   <li>관리자: 일괄 등록 변경이력(BULK_INSERT) 저장</li>
	 * </ul>
	 *
	 * @param body { items: [{termsNm, termsEngAbrvNm, words, ...}] }
	 * @return 등록 결과 (registeredTerms, skipped, failed, details)
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
		boolean directApprove = sessionService.hasPerm(PermCode.STD_MANAGE);
		// 98번 — 이 일괄 등록이 들어갈 사전. 화면에서 고른 값이 온다.
		String dictId = resolveDictOrDefault(body.get("dictId"));
		// 104번 WP5-14 — 참조 사전에는 등록하지 않는다 (항목마다 실패)
		if (dictService.isRef(dictId)) {
			String msg = refBlocked(dictId).getResultMessage();
			List<Map<String, Object>> fails = new ArrayList<>();
			for (Map<String, Object> it : items) {
				Map<String, Object> d = new HashMap<>();
				d.put("termsNm", it.get("termsNm"));
				d.put("status", "FAIL");
				d.put("message", msg);
				fails.add(d);
			}
			Map<String, Object> res = new HashMap<>();
			res.put("registeredTerms", 0);
			res.put("skipped", 0);
			res.put("failed", items.size());
			res.put("details", fails);
			return res;
		}
		int registeredTerms = 0;
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

			SqlSession session = sqlSessionFactory.openSession();
			try {
				// 중복 체크 — 같은 사전 안에서만. 다른 사전의 동명 용어는 별개다.
				if (session.selectOne("terms.selectTermsByNm", dictService.nameParam(dictId, "termsNm", termsNm)) != null) {
					skipped++;
					Map<String, Object> detail = new HashMap<>();
					detail.put("termsNm", termsNm);
					detail.put("status", "SKIPPED");
					detail.put("message", "이미 등록된 용어");
					details.add(detail);
					continue;
				}

				// 110번 7.5-6 — 신규 단어 함께 등록(newWords) 경로는 뺐다. 화면은 모든 단어가 MATCHED 일 때만 등록하고
				// 미등록 단어는 [단어등록](registerWord)으로 먼저 등록한다. 그래서 구성 단어는 모두 wordId 가 있다
				// 구성 단어 승인 여부 체크는 용어 승인 시점에 수행 (등록은 자유)

				if (words == null || words.isEmpty()) {
					throw new RuntimeException("용어는 최소 1개 이상의 단어로 구성되어야 합니다");
				}
				// 형식단어 검증: 등록할 사전이 요구할 때만 마지막 단어가 형식단어인지 본다 (단일 단어 용어도 허용)
				if (dictService.isTermLastWordClsfRequired(dictId)) {
					String lastWordId = (String) words.get(words.size() - 1).get("wordId");
					if (lastWordId == null) {
						throw new RuntimeException("마지막 단어의 ID를 확인할 수 없습니다. 단어 등록 후 다시 시도해주세요.");
					}
					checkLastWordClsf(session, lastWordId);
				}

				// 도메인 필수·유효성 체크 — 110번 7.5-7: TB_TERMS.DOMAIN_NM NOT NULL. 예전에는 null 로 넣어 DB 오류로 실패했다 (createTerms 3.12-15-e 와 같은 문구)
				if (domainNm == null || domainNm.trim().isEmpty()) {
					throw new RuntimeException("도메인명은 필수입니다.");
				}
				Object domainCheck = session.selectOne("domain.selectDomainInfoByNm", dictService.nameParam(dictId, "domainNm", domainNm.trim()));
				if (domainCheck == null) {
					throw new RuntimeException("등록되지 않은 도메인입니다: " + domainNm);
				}

				// 용어 등록
				StdTermsVo termsVo = new StdTermsVo();
				String termsId = StringUtils.getUUID();
				termsVo.setId(termsId);
				termsVo.setDictId(dictId);
				termsVo.setTermsNm(termsNm);
				// 104번 WP6-13 — 사전 운영 옵션: 표준 대문자, 용어 영문약어명 최대 길이
				termsVo.setTermsEngAbrvNm(dictService.checkTermEngAbrv(dictId, termsEngAbrvNm));
				termsVo.setTermsDesc(termsDesc != null ? termsDesc : termsNm);
				termsVo.setDomainNm(domainNm);
				termsVo.setCommStndYn("N");
				termsVo.setCretUserId(userId);
				if (directApprove) termsVo.setAprvYn("Y");
				else termsVo.setAprvYn("N");
				session.insert("terms.insertTerms", termsVo);

				// 용어-단어 관계 등록
				if (words != null && !words.isEmpty()) {
					List<StdTermsVo.Word> wordList = new ArrayList<>();
					for (Map<String, Object> w : words) {
						StdTermsVo.Word tw = new StdTermsVo.Word();
						tw.setTermsId(termsId);
						Number wordOrd = (Number) w.get("wordOrd");
						tw.setWordOrd(wordOrd != null ? wordOrd.shortValue() : (short) 0);

						tw.setWordId((String) w.get("wordId"));
						if (tw.getWordId() == null) {
							throw new RuntimeException("단어 ID를 확인할 수 없습니다. 단어 등록 후 다시 시도해주세요. (순서: " + tw.getWordOrd() + ")");
						}
						List<StdWordVo> wordInfo = session.selectList("word.selectWordInfoById", tw.getWordId());
						if (!wordInfo.isEmpty()) {
							tw.setWordNm(wordInfo.get(0).getWordNm());
						}
						wordList.add(tw);
					}
					if (!wordList.isEmpty()) {
						session.insert("terms.insertTermsWords", wordList);
					}
				}

				session.commit();
				registeredTerms++;
				// 104번 WP1 — 승인 대기로 들어간 용어에 결재선이 있으면 결재 문서
				if (!directApprove) {
					openAprv("TERMS", termsId, termsNm, dictId);
				}

				// 88번 §16 — 변환 이력 기록 (사용자 원본 입력 → 등록된 표준 용어)
				String originalInput = (String) item.get("originalInput");
				if (originalInput == null || originalInput.isEmpty()) originalInput = termsNm;
				try {
					Map<String, Object> r = new HashMap<>();
					r.put("dmId", null);
					r.put("objOwner", null);
					r.put("objNm", null);
					r.put("attrNm", null);
					r.put("inputKrNm", originalInput);
					r.put("resolvedKrNm", termsNm);
					r.put("resolvedEnNm", termsEngAbrvNm);
					r.put("resolvedTermsId", termsId);
					r.put("resolvedDataType", null);
					r.put("resolvedDataLen", 0L);
					r.put("resolveReason",
						originalInput.equals(termsNm) ? "용어 등록 (입력 그대로)" : "수정 모달에서 변환 후 등록");
					r.put("changeUserId", userId == null ? "system" : userId);
					r.put("changeDt", java.time.LocalDateTime.now()
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
					sqlSessionTemplate.insert("termResolve.insert", r);
				} catch (Exception ex) {
					log.warn(">> termResolve insert failed: {}", ex.getMessage());
				}

				Map<String, Object> detail = new HashMap<>();
				detail.put("termsNm", termsNm);
				detail.put("status", "SUCCESS");
				details.add(detail);
			} catch (Exception e) {
				session.rollback();
				failed++;
				log.error("registerTermsBatch item failed: {} - {}", termsNm, e.getMessage());
				// 110번 7.5-8 — DB 오류 원문은 내보내지 않는다 (createWord 와 같은 방식). 일괄 등록 이력 상세 비고도 이 문구
				String dupMsg = StdInputRule.translateDuplicateError(e.getMessage());
				String userMsg = dupMsg != null ? dupMsg : e.getMessage();
				if (userMsg != null && (userMsg.startsWith("\r\n### Error") || userMsg.contains("PSQLException") || userMsg.contains("org.postgresql"))) {
					userMsg = "용어 등록 중 오류가 발생했습니다. 입력값을 확인해주세요.";
				}

				Map<String, Object> detail = new HashMap<>();
				detail.put("termsNm", termsNm);
				detail.put("status", "FAIL");
				detail.put("message", userMsg);
				details.add(detail);
			} finally {
				session.close();
			}
		}

		// 일괄 등록 이력 저장 (관리자 즉시 승인 시에만, 일반 사용자는 승인 시점에 저장)
		if (directApprove) {
			try {
				String changeId = StringUtils.getUUID();
				Map<String, Object> history = new HashMap<>();
				history.put("changeId", changeId);
				history.put("changeType", "BULK_INSERT");
				history.put("targetType", "TERM");
				history.put("changeCnt", registeredTerms);
				history.put("summary", String.format("용어 일괄등록(표준화 추천) %d건 (성공:%d, 건너뜀:%d, 실패:%d)", items.size(), registeredTerms, skipped, failed));
				history.put("changeUserId", userId);
				history.put("changeSource", "BULK_UPLOAD");
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
			// 110번 7.5-11 — 예전에는 예외를 삼켜 빈 목록을 돌려줘 화면의 '엑셀 파싱 실패' 창이 뜨지 않았다
			log.error("Excel parsing failed: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "엑셀 파일을 읽지 못했습니다");
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
	 * 분류어 단어 목록 조회 (자동 표준화 수정 모달용).
	 *
	 * TB_WORD 에서 WORD_CLSF_YN='Y' + 승인/사용 플래그 필터. 응답에 domainClsfNm 포함되어
	 * 프론트가 이 값으로 도메인 드롭다운 cascade 로드 가능.
	 */
	@GetMapping(value = "/getClassificationWords")
	public List<Map<String, Object>> getClassificationWords(@RequestParam(value = "dictId", required = false) String dictId) {
		// 98번 — 화면에서 고른 사전의 형식단어만 (없으면 기본 사전). 예전엔 사전 조건 없이 전 사전을 읽었다.
		List<StdWordVo> all = sqlSessionTemplate.selectList("word.selectAllWords", dictService.dictParam(dictId));
		List<Map<String, Object>> result = new ArrayList<>();
		for (StdWordVo w : all) {
			if (!"Y".equals(w.getWordClsfYn())) continue;
			if (!"Y".equals(w.getAprvYn())) continue;
			Map<String, Object> m = new HashMap<>();
			m.put("wordId", w.getId());
			m.put("wordNm", w.getWordNm());
			m.put("wordEngAbrvNm", w.getWordEngAbrvNm());
			m.put("wordEngNm", w.getWordEngNm());
			m.put("domainClsfNm", w.getDomainClsfNm());
			result.add(m);
		}
		return result;
	}

	/**
	 * 분류어로 묶인 도메인 목록 조회 (자동 표준화 수정 모달 cascade 용).
	 */
	@GetMapping(value = "/getDomainsByClsf")
	public List<Map<String, Object>> getDomainsByClsf(@RequestParam("domainClsfNm") String domainClsfNm,
			@RequestParam(value = "dictId", required = false) String dictId) {
		List<StdDomainVo> domains = sqlSessionTemplate.selectList("domain.selectDomainInfoByClsfNm", dictService.nameParam(dictId, "domainClsfNm", domainClsfNm));
		List<Map<String, Object>> result = new ArrayList<>();
		for (StdDomainVo d : domains) {
			if (!"Y".equals(d.getAprvYn())) continue;
			Map<String, Object> m = new HashMap<>();
			m.put("domainId", d.getId());
			m.put("domainNm", d.getDomainNm());
			m.put("dataType", d.getDataType());
			m.put("dataLen", d.getDataLen());
			m.put("dataDecimalLen", d.getDataDecimalLen());
			result.add(m);
		}
		return result;
	}

	/**
	 * 표준화 추천 엑셀 업로드용 양식 다운로드 (resources/templates/ 의 고정 파일 서빙).
	 */
	@GetMapping(value = "/downloadTermRecommendTemplate")
	public void downloadTermRecommendTemplate(HttpServletResponse res) throws Exception {
		streamTemplate(res, "templates/term_recommend_template.xlsx", "term_recommend_template.xlsx");
	}

	/**
	 * 용어 일괄등록 양식 다운로드 — 헤더만 있는 빈 xlsx (용어 내려받기 헤더와 같다).
	 * 104번 WP5-16 에서 끝에 개인정보여부·암호화여부를 붙이며 정적 파일 대신 동적 생성으로 바꿨다.
	 */
	@GetMapping(value = "/downloadTermTemplate")
	public void downloadTermTemplate(HttpServletResponse res) throws Exception {
		writeHeaderOnlyTemplate(res, TERM_HEADERS, "용어_일괄등록_템플릿.xlsx", "Sheet1");
	}

	/**
	 * 단어 일괄등록 양식 다운로드 (resources/templates/word_template.xlsx).
	 */
	@GetMapping(value = "/downloadWordTemplate")
	public void downloadWordTemplate(HttpServletResponse res) throws Exception {
		streamTemplate(res, "templates/word_template.xlsx", "단어_일괄등록_템플릿.xlsx");
	}

	private void streamTemplate(HttpServletResponse res, String resourcePath, String fileName) throws Exception {
		res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		res.setHeader("Content-Disposition", "attachment; filename=\""
				+ java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
		try (java.io.InputStream in = new org.springframework.core.io.ClassPathResource(resourcePath).getInputStream()) {
			org.springframework.util.StreamUtils.copy(in, res.getOutputStream());
			res.getOutputStream().flush();
		}
	}

	// 86번 #11 — 도메인 / 도메인그룹 / 도메인분류 업로드 양식 (헤더만 있는 빈 xlsx, POI 동적 생성).
	// 다운로드 헤더와 정확히 동일하게 맞춰서 다운로드 → 재업로드 백업 가능.
	private static final String[] DOMAIN_HEADERS = {
		"No", "제정차수", "도메인그룹명", "도메인분류명", "도메인명", "도메인설명",
		"데이터타입", "데이터길이", "데이터소수점길이", "저장형식", "표현형식", "단위",
		"허용값", "요청시스템", "표준여부", "개인정보여부", "암호화여부"
	};
	private static final String[] TERM_HEADERS = {
		"No", "제정차수", "용어명", "용어설명", "용어영문약어명", "도메인명",
		"허용값", "저장형식", "표현형식", "코드그룹명", "소관기관명", "이음동의어목록",
		"요청시스템", "표준여부", "개인정보여부", "암호화여부"
	};
	private static final String[] DOMAIN_GROUP_HEADERS = {
		"No", "도메인그룹명", "표준여부"
	};
	private static final String[] DOMAIN_CLSF_HEADERS = {
		"No", "도메인그룹명", "도메인분류명", "표준여부"
	};

	@GetMapping(value = "/downloadDomainTemplate")
	public void downloadDomainTemplate(HttpServletResponse res) throws Exception {
		writeHeaderOnlyTemplate(res, DOMAIN_HEADERS, "도메인_일괄등록_템플릿.xlsx", "도메인 양식");
	}

	@GetMapping(value = "/downloadDomainGroupTemplate")
	public void downloadDomainGroupTemplate(HttpServletResponse res) throws Exception {
		writeHeaderOnlyTemplate(res, DOMAIN_GROUP_HEADERS, "도메인그룹_일괄등록_템플릿.xlsx", "도메인그룹 양식");
	}

	@GetMapping(value = "/downloadDomainClsfTemplate")
	public void downloadDomainClsfTemplate(HttpServletResponse res) throws Exception {
		writeHeaderOnlyTemplate(res, DOMAIN_CLSF_HEADERS, "도메인분류_일괄등록_템플릿.xlsx", "도메인분류 양식");
	}

	/** 헤더만 있는 빈 양식 xlsx 동적 생성 (POI). 단순/일관/유지보수 쉬움. */
	private void writeHeaderOnlyTemplate(HttpServletResponse res, String[] headers, String fileName, String sheetName) throws Exception {
		res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		res.setHeader("Content-Disposition", "attachment; filename=\""
				+ java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
		try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
			org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet(sheetName);
			org.apache.poi.ss.usermodel.CellStyle hdrStyle = wb.createCellStyle();
			org.apache.poi.ss.usermodel.Font hdrFont = wb.createFont();
			hdrFont.setBold(true);
			hdrStyle.setFont(hdrFont);
			hdrStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
			hdrStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
			hdrStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
			hdrStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

			org.apache.poi.ss.usermodel.Row hRow = sh.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				org.apache.poi.ss.usermodel.Cell c = hRow.createCell(i);
				c.setCellValue(headers[i]);
				c.setCellStyle(hdrStyle);
				sh.setColumnWidth(i, 18 * 256);
			}
			wb.write(res.getOutputStream());
			res.getOutputStream().flush();
		}
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

		// [B] 토큰 추가 시 적용하는 split penalty.
		// 같은 길이를 단일 매칭으로 cover 가능하면 그쪽을 우선시키기 위함.
		// (예: 상품카테고리 → 상품+카테고리 vs 상품+카테고+리 — 후자가 분리 점수 합이 더 커지는 함정 방지)
		final int SPLIT_PENALTY = 8000;

		for (int i = 0; i < n; i++) {
			if (dp[i] == Integer.MIN_VALUE / 2) continue;

			// 모든 후보 토큰 시도
			for (String c : candidates) {
				// 전체 입력이 하나의 등록 단어인 경우는 허용
				if (c.equals(input) && !wordsByNm.containsKey(c) && !wordDict.containsKey(c)) continue;
				if (i + c.length() <= n && input.startsWith(c, i)) {
					int score = calculateTokenScore(c, wordsByNm, wordDict) - SPLIT_PENALTY;
					if (dp[i] + score > dp[i + c.length()]) {
						dp[i + c.length()] = dp[i] + score;
						parent[i + c.length()] = i;
						token[i + c.length()] = c;
					}
				}
			}

			// 1글자 스킵 (매칭 안 될 때)
			String oneChar = input.substring(i, i + 1);
			int skipScore = dp[i] - 500 - SPLIT_PENALTY;
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
		// 후처리: 연속 1글자 토큰을 병합 후 알려진 단어 재탐색
		result = mergeAndResplit(result, wordsByNm, wordDict);
		return result;
	}

	/**
	 * 후처리: 연속 1글자 토큰을 병합한 뒤 TB_WORD/DICT 단어를 재탐색
	 * 예: [코, 일시, 발, 제, 목] → "코"(1글자) 병합 대상 → 주변 재탐색
	 * 예: [시, 발, 제, 목] → 병합 "시발제목" → 재분리 → [시발, 제목]
	 */
	private List<String> mergeAndResplit(List<String> tokens,
			Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		if (tokens.size() <= 1) return tokens;

		List<String> merged = new ArrayList<>();
		int i = 0;
		while (i < tokens.size()) {
			if (tokens.get(i).length() == 1) {
				// 연속 1글자 토큰 수집
				StringBuilder sb = new StringBuilder();
				while (i < tokens.size() && tokens.get(i).length() == 1) {
					sb.append(tokens.get(i));
					i++;
				}
				String chunk = sb.toString();
				// 병합된 청크에서 알려진 단어를 greedy longest match로 추출
				List<String> resplit = resplitChunk(chunk, wordsByNm, wordDict);
				merged.addAll(resplit);
			} else {
				merged.add(tokens.get(i));
				i++;
			}
		}
		return merged;
	}

	/**
	 * 1글자 토큰 병합 청크를 greedy longest match로 재분리
	 */
	private List<String> resplitChunk(String chunk,
			Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		List<String> result = new ArrayList<>();
		int pos = 0;
		while (pos < chunk.length()) {
			String bestMatch = null;
			int bestLen = 0;
			int bestScore = 0;
			// 현재 위치에서 가능한 모든 길이의 서브스트링 시도 (긴 것부터)
			for (int len = chunk.length() - pos; len >= 2; len--) {
				String sub = chunk.substring(pos, pos + len);
				int score = 0;
				if (wordsByNm.containsKey(sub)) {
					score = 10000 + len * 100;
				} else if (wordDict.containsKey(sub)) {
					score = 5000 + len * 100;
				}
				if (score > bestScore) {
					bestScore = score;
					bestMatch = sub;
					bestLen = len;
				}
			}
			if (bestMatch != null) {
				// 매칭 전 남은 1글자들을 하나로 합쳐서 추가
				result.add(bestMatch);
				pos += bestLen;
			} else {
				// 매칭 불가 → 1글자씩 진행하되 연속된 미매칭을 하나로 합침
				int start = pos;
				while (pos < chunk.length()) {
					pos++;
					// 다음 위치에서 2글자 이상 매칭 가능한지 확인
					boolean hasMatch = false;
					for (int len = Math.min(chunk.length() - pos, 10); len >= 2; len--) {
						String sub = chunk.substring(pos, pos + len);
						if (wordsByNm.containsKey(sub) || wordDict.containsKey(sub)) {
							hasMatch = true;
							break;
						}
					}
					if (hasMatch) break;
				}
				result.add(chunk.substring(start, pos));
			}
		}
		return result;
	}

	/**
	 * DP 분리용 토큰 점수 계산
	 *
	 * TB_WORD 등록 단어 > TB_WORD_DICT 추천 사전 > OKT 토큰 순으로 점수 부여.
	 * 같은 유형 내에서는 긴 토큰이 더 높은 점수를 받는다.
	 *
	 * [D] 1자 + 비분류어(WORD_CLSF_YN='N') TB_WORD 는 점수 격하.
	 *   - 분류어(명/수/월 등 clsf='Y') 는 정상 점수 → "고객명" → 고객+명 정상 분리
	 *   - 비분류어 1자(리/산/층/팀 등) 는 격하 → 카테고리/메모리 같은 외래어 끝 우연 매칭 방지
	 */
	private int calculateTokenScore(String token, Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		if (wordsByNm.containsKey(token)) {
			if (token.length() == 1) {
				boolean isClsf = false;
				for (StdWordVo w : wordsByNm.get(token)) {
					if ("Y".equals(w.getWordClsfYn())) { isClsf = true; break; }
				}
				if (!isClsf) return 5000;  // 1자 비분류어: 격하 (TB_WORD 보너스 약화)
			}
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
			Map<String, Integer> usageMap, Map<String, StdWordVo> synmToWord) {
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
					c.setWordClsfYn(w.getWordClsfYn());
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
			} else if (synmToWord != null && synmToWord.containsKey(token)) {
				// 동의어 매칭: 입력은 동의어이지만 원본 단어 정보로 처리 (예: 카테고리 → 범주)
				StdWordVo w = synmToWord.get(token);
				wa.setStatus("MATCHED");
				TermAnalysisResult.WordCandidate c = new TermAnalysisResult.WordCandidate();
				c.setWordId(w.getId());
				c.setWordNm(w.getWordNm());
				c.setWordEngAbrvNm(w.getWordEngAbrvNm());
				c.setWordEngNm(w.getWordEngNm());
				c.setDomainClsfNm(w.getDomainClsfNm());
				c.setWordClsfYn(w.getWordClsfYn());
				c.setSelected(true);
				int score = 0;
				Integer usage = usageMap.get(w.getWordNm());
				if (usage != null) score += usage * 100;
				if (w.getWordEngAbrvNm() != null) score += Math.min(w.getWordEngAbrvNm().length(), 10) * 10;
				c.setScore(score);
				List<TermAnalysisResult.WordCandidate> cands = new ArrayList<>();
				cands.add(c);
				wa.setCandidates(cands);
				wa.setSelected(c);
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

	/**
	 * 토큰이 "미신뢰" 인지 판정.
	 * - 트러스트: TB_WORD ≥2자 / TB_WORD 1자 분류어 / DICT 매치
	 * - 미신뢰: OKT-only / TB_WORD 1자 비분류어 (예: 리/산/팀)
	 */
	private boolean isUncertainToken(String token,
			Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict) {
		if (token == null || token.isEmpty()) return false;
		List<StdWordVo> matches = wordsByNm.get(token);
		if (matches != null && !matches.isEmpty()) {
			if (token.length() == 1) {
				for (StdWordVo w : matches) {
					if ("Y".equals(w.getWordClsfYn())) return false;  // 1자 분류어: 트러스트
				}
				return true;  // 1자 비분류어: 미신뢰
			}
			return false;  // 2자+ TB_WORD: 트러스트
		}
		if (wordDict.containsKey(token)) return false;  // DICT 매치: 트러스트
		return true;  // OKT-only / 미매치: 미신뢰
	}

	/**
	 * DP 결과에서 미신뢰 토큰 연속 구간(2개 이상)을 합쳐 사전(TB_WORD/DICT/동의어목록) 재검색.
	 * 발견되면 단일 토큰으로 치환, 없으면 원래 분리 유지.
	 *
	 * 예: [상품, 카테고, 리] 에서 [카테고, 리] 가 미신뢰 → "카테고리" 합쳐서 검색
	 *      → 범주의 동의어로 발견 → [상품, 카테고리]
	 */
	private List<String> resolveUncertainRuns(List<String> tokens,
			Map<String, List<StdWordVo>> wordsByNm,
			Map<String, Map<String, Object>> wordDict,
			Map<String, StdWordVo> synmToWord) {
		if (tokens == null || tokens.size() <= 1) return tokens;
		List<String> result = new ArrayList<>();
		int i = 0;
		while (i < tokens.size()) {
			if (isUncertainToken(tokens.get(i), wordsByNm, wordDict)) {
				int start = i;
				StringBuilder sb = new StringBuilder();
				while (i < tokens.size() && isUncertainToken(tokens.get(i), wordsByNm, wordDict)) {
					sb.append(tokens.get(i));
					i++;
				}
				int runSize = i - start;
				String chunk = sb.toString();
				boolean foundInDict = wordsByNm.containsKey(chunk)
						|| wordDict.containsKey(chunk)
						|| (synmToWord != null && synmToWord.containsKey(chunk));
				if (runSize >= 2 && foundInDict) {
					result.add(chunk);  // 사전 매칭: 통째 치환
				} else {
					for (int j = start; j < i; j++) result.add(tokens.get(j));  // 그대로 유지
				}
			} else {
				result.add(tokens.get(i));
				i++;
			}
		}
		return result;
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
	private String checkForbiddenWord(String wordNm, String dictId) {
		if (wordNm == null || wordNm.trim().isEmpty()) return null;
		Map<String, Object> found = sqlSessionTemplate.selectOne("word.selectWordByForbiddenNm", dictService.nameParam(dictId, "wordNm", wordNm.trim()));
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
	private String checkSynonymWord(String wordNm, String dictId) {
		if (wordNm == null || wordNm.trim().isEmpty()) return null;
		Map<String, Object> found = sqlSessionTemplate.selectOne("word.selectWordBySynonymNm", dictService.nameParam(dictId, "wordNm", wordNm.trim()));
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
	 * 도메인 영향도 분석 - 해당 도메인을 사용하는 용어 목록과, 그 용어를 쓰는 컬럼 (104번 WP7-26)
	 *
	 * @param domainNm 도메인명
	 * @param dictId   사전 (없으면 기본 사전)
	 * @return { terms: 참조 용어 목록, columns: 그 용어들의 컬럼 }
	 */
	@GetMapping("/impact/domain")
	public Map<String, Object> getDomainImpact(@RequestParam String domainNm,
			@RequestParam(value = "dictId", required = false) String dictId) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> terms = sqlSessionTemplate.selectList("impact.selectImpactTermsByDomainNm", dictService.nameParam(dictId, "domainNm", domainNm));
		result.put("terms", terms);
		result.put("columns", impactColumns(terms));
		return result;
	}

	/**
	 * 104번 WP7-26 — 용어 영향도: 이 용어를 쓰는 컬럼 (모델 사전 = 용어 사전 또는 그 하위 사전(109번 #24), 영문명 일치 ENG / 한글명만 일치 KR)
	 */
	@GetMapping("/impact/term")
	public Map<String, Object> getTermImpact(@RequestParam String termsId) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> one = new HashMap<>();
		one.put("termsId", termsId);
		result.put("columns", impactColumns(java.util.Collections.singletonList(one)));
		return result;
	}

	/**
	 * 104번 WP7-26 — 코드 영향도: 이 코드 그룹을 쓰는 용어(코드 자신 포함)와 그 컬럼(코드값이 들어가는 컬럼),
	 * 이 코드를 상위로 쓰는 계층·파생 코드, 하위로 묶는 통합 코드, 코드값 수
	 */
	@GetMapping("/impact/code")
	public Map<String, Object> getCodeImpact(@RequestParam String termsId) {
		Map<String, Object> result = new HashMap<>();
		try (SqlSession session = sqlSessionFactory.openSession()) {
			Map<String, Object> code = session.selectOne("codeType.selectCodeById", termsId);
			if (code == null || code.get("codeGrp") == null) {
				result.put("terms", new ArrayList<>());
				result.put("columns", new ArrayList<>());
				result.put("relatedCodes", new ArrayList<>());
				result.put("valueCnt", 0);
				return result;
			}
			Map<String, Object> p = dictService.nameParam((String) code.get("dictId"), "codeGrp", (String) code.get("codeGrp"));
			List<Map<String, Object>> terms = session.selectList("impact.selectTermsByCodeGrp", p);
			result.put("terms", terms);
			result.put("columns", impactColumns(terms));
			result.put("relatedCodes", session.selectList("impact.selectRelatedCodes", p));
			result.put("valueCnt", session.selectOne("codeType.countValuesByGrp", p));
		}
		return result;
	}

	/** 용어 목록의 연관 컬럼 (용어가 없으면 빈 목록) */
	private List<Map<String, Object>> impactColumns(List<Map<String, Object>> terms) {
		List<String> ids = new ArrayList<>();
		for (Map<String, Object> t : terms) if (t.get("termsId") != null) ids.add((String) t.get("termsId"));
		if (ids.isEmpty()) return new ArrayList<>();
		Map<String, Object> p = new HashMap<>();
		p.put("termsIds", ids);
		return sqlSessionTemplate.selectList("impact.selectImpactColumnsByTermIds", p);
	}

	/**
	 * 변경 이력 저장. 등록 경로(CHANGE_SOURCE)는 세션 권한에서 파생한다.
	 *
	 * 그동안 이 값을 아무도 안 넣어 전 행이 NULL 이었고, 변경 이력 화면의
	 * '등록 경로' 필터가 항상 빈 결과를 냈다. 호출처가 18곳이라 기본값을 여기서
	 * 정하고, 승인·추천처럼 경로가 분명한 곳만 아래 오버로드로 명시한다.
	 */
	private void saveChangeHistory(String changeType, String targetType, String targetId, String targetNm,
			String prevValue, String currValue, String summary) {
		boolean directApprove = false;
		try { directApprove = sessionService.hasPerm(PermCode.STD_MANAGE); } catch (Exception ignore) {}
		saveChangeHistory(changeType, targetType, targetId, targetNm, prevValue, currValue, summary,
				directApprove ? "ADMIN_DIRECT" : "USER_REQUEST");
	}

	private void saveChangeHistory(String changeType, String targetType, String targetId, String targetNm,
			String prevValue, String currValue, String summary, String changeSource) {
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
			history.put("changeSource", changeSource);
			sqlSessionTemplate.insert("changehistory.insertChangeHistory", history);
		} catch (Exception e) {
			log.warn("변경이력 저장 실패: {}", e.getMessage());
		}
	}

}
