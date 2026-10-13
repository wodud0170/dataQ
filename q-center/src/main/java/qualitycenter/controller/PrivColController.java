package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.ExcelDownloadService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP5-16 — 데이터 모델 > 개인정보 컬럼.
 *
 * <p>컬럼의 개인정보여부·암호화여부 실제 값과 출처(컬럼·용어·도메인)를 보이고, 컬럼 단위로 지정하거나
 * 상속으로 되돌린다. 조회는 로그인 사용자 누구나, 지정은 모델 직접 반영 권한(DM_MANAGE)이 필요하다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/privCol")
public class PrivColController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private ExcelDownloadService excelDownloadService;

	private Response ok(Object data) throws JsonProcessingException {
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		r.setContents(new ObjectMapper().writeValueAsString(data));
		return r;
	}

	/** 목록 — {sysCd, dmId, objOwner, objNm, attrNm, privOnly, unencOnly} */
	@PostMapping("/list")
	public Mono<Response> list(@RequestBody Map<String, Object> filter) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("privCol.selectPrivColList", filter)));
	}

	/** 필터용 모델 목록 */
	@GetMapping("/models")
	public Mono<Response> models() throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("privCol.selectModelOptions", new HashMap<>())));
	}

	/** 필터용 소유자 목록 */
	@GetMapping("/owners")
	public Mono<Response> owners(@RequestParam("dmId") String dmId) throws JsonProcessingException {
		return Mono.just(ok(sqlSessionTemplate.selectList("privCol.selectOwnerOptions", dmId)));
	}

	/**
	 * 컬럼 단위 지정 — {field: PRIV|ENCR, val: Y|N|"" (""=상속으로 되돌리기), keys:[{dmId, objOwner, objNm, attrNm}]}
	 */
	@PostMapping("/set")
	public Mono<Response> set(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		if (!sessionService.hasPerm(PermCode.DM_MANAGE)) {
			res.setResultInfo(403, "권한이 없습니다 (모델 직접 반영 권한 필요)");
			return Mono.just(res);
		}
		String field = (String) body.get("field");
		String val = (String) body.get("val");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
		if (!"PRIV".equals(field) && !"ENCR".equals(field)) {
			res.setResultInfo(RestResult.CODE_500.getCode(), "지정할 항목이 올바르지 않습니다.");
			return Mono.just(res);
		}
		if (val != null && !val.isEmpty() && !"Y".equals(val) && !"N".equals(val)) {
			res.setResultInfo(RestResult.CODE_500.getCode(), "값은 Y 또는 N 이어야 합니다.");
			return Mono.just(res);
		}
		if (keys == null || keys.isEmpty()) {
			res.setResultInfo(RestResult.CODE_500.getCode(), "지정할 컬럼을 선택해 주세요.");
			return Mono.just(res);
		}
		Map<String, Object> p = new HashMap<>();
		p.put("val", (val == null || val.isEmpty()) ? null : val);
		p.put("keys", new ArrayList<>(keys));
		int n = sqlSessionTemplate.update("PRIV".equals(field) ? "privCol.updateAttrPrivYn" : "privCol.updateAttrEncrYn", p);
		log.info(">> privCol set field={} val={} rows={} by {}", field, val, n, sessionService.getUserId());
		res.setResultInfo(RestResult.CODE_200);
		res.setContents(String.valueOf(n));
		return Mono.just(res);
	}

	/** 엑셀 내려받기 — 목록과 같은 필터 (query string) */
	@GetMapping("/download")
	public void download(@RequestParam Map<String, Object> filter, HttpServletRequest request, HttpServletResponse response) {
		try {
			excelDownloadService.getPrivColExcel(filter, request, response);
		} catch (Exception e) {
			log.error(">> download privCol excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
