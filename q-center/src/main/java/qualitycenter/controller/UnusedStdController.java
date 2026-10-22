package qualitycenter.controller;

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

import com.ndata.quality.service.ExcelDownloadService;
import com.ndata.quality.service.StdDictService;

import lombok.extern.slf4j.Slf4j;

/**
 * 104번 WP7-27 — 쓰이지 않는 표준 (표준 사전 > 미사용 표준).
 *
 * <p>사전 안에서 단어(어떤 용어의 구성 단어도, 어떤 컬럼명 토큰도 아님), 용어(코드 포함 — 컬럼명 = 영문약어명인 컬럼이 없음),
 * 도메인(어떤 용어도 쓰지 않음)을 찾는다. 컬럼은 사용 중·승인 모델 중 모델 사전이 같은 것만 본다
 * (109번 #24 — 이 사전을 상위로 둔 하위 사전의 모델도). 조회는 누구나.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/std/unused")
public class UnusedStdController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private StdDictService dictService;
	@Autowired private ExcelDownloadService excelDownloadService;

	private Map<String, Object> filter(Map<String, Object> body) {
		Map<String, Object> p = dictService.dictParam(body.get("dictId") == null ? null : body.get("dictId").toString());
		p.put("keyword", body.get("keyword") == null ? null : body.get("keyword").toString().trim());
		p.put("aprvOnly", "N".equals(body.get("aprvOnly")) ? "N" : "Y");
		// 104번 WP9-67 — 예약 실행이 저장한 결과 (엑셀)
		if (body.get("runId") != null && !body.get("runId").toString().isEmpty()) {
			p.put("runId", body.get("runId").toString());
			p.put("kind", body.get("kind") == null ? "TERM" : body.get("kind").toString());
		}
		return p;
	}

	private static String sqlId(Object kind) {
		if ("WORD".equals(kind)) return "unusedStd.selectUnusedWords";
		if ("DOMAIN".equals(kind)) return "unusedStd.selectUnusedDomains";
		return "unusedStd.selectUnusedTerms";
	}

	/** 목록 — {dictId, kind: WORD|TERM|DOMAIN, keyword?, aprvOnly: Y(기본)|N}. 5,000건까지 */
	@PostMapping("/list")
	public Map<String, Object> list(@RequestBody Map<String, Object> body) {
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList(sqlId(body.get("kind")), filter(body));
		Map<String, Object> out = new HashMap<>();
		out.put("items", rows);
		return out;
	}

	/** 엑셀 내려받기 — 목록과 같은 필터 (query string) */
	@GetMapping("/download")
	public void download(@RequestParam Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
		try {
			Object kind = body.get("kind");
			excelDownloadService.getUnusedStdExcel(kind == null ? "TERM" : kind.toString(), filter(body), request, response);
		} catch (Exception e) {
			log.error(">> download unused std excel failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
