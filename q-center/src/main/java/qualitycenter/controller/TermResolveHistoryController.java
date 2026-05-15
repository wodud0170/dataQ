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

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 88번 §16 — 한글 변환 이력 (매핑 정의서 작성용).
 * 표준화 액션 시점에 자동으로 input vs resolved 기록.
 * 관리자: 전체 / 사용자: 본인 변환 이력만 조회.
 */
@Slf4j
@RestController
@RequestMapping("/api/termResolve")
public class TermResolveHistoryController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private ExcelDownloadService excelDownloadService;

	@PostMapping("/list")
	public List<Map<String, Object>> list(@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>(body == null ? new HashMap<>() : body);
		p.put("isAdmin",       sessionService.isAdmin());
		p.put("currentUserId", sessionService.getUserId());
		return sqlSessionTemplate.selectList("termResolve.selectList", p);
	}

	@GetMapping("/excel")
	public void excel(@RequestParam(required = false) String inputKrNm,
	                  @RequestParam(required = false) String resolvedKrNm,
	                  @RequestParam(required = false) String resolvedEnNm,
	                  @RequestParam(required = false) String changeUserId,
	                  @RequestParam(required = false) String nmChangedYn,
	                  @RequestParam(required = false) String dmId,
	                  @RequestParam(required = false) String fromDt,
	                  @RequestParam(required = false) String toDt,
	                  HttpServletRequest request, HttpServletResponse response) throws Exception {
		Map<String, Object> p = new HashMap<>();
		p.put("inputKrNm",     inputKrNm);
		p.put("resolvedKrNm",  resolvedKrNm);
		p.put("resolvedEnNm",  resolvedEnNm);
		p.put("changeUserId",  changeUserId);
		p.put("nmChangedYn",   nmChangedYn);
		p.put("dmId",          dmId);
		p.put("fromDt",        fromDt);
		p.put("toDt",           toDt);
		p.put("isAdmin",       sessionService.isAdmin());
		p.put("currentUserId", sessionService.getUserId());
		excelDownloadService.getTermResolveHistoryExcel(p, request, response);
	}
}
