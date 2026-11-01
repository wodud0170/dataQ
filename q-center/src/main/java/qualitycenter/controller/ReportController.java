package qualitycenter.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.report.ReportService;
import reactor.core.publisher.Mono;

/**
 * 108 §4.37 (92번) — 분석 > 종합 분석 보고서. 로그인 사용자 누구나(모델 조회와 같은 범위).
 * 시트 목록·프리셋, 시트 미리보기(앞 10행), xlsx 내려받기.
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
public class ReportController {

	private static final List<Integer> DAYS = java.util.Arrays.asList(7, 30, 90, 365);

	@Autowired private ReportService reportService;
	@Autowired private SessionService sessionService;
	@Autowired private SqlSessionTemplate sqlSessionTemplate;

	/** 시트 목록 [{id, title, cat}]·프리셋 {ALL·DBA·STD·GOV: [id]} */
	@PostMapping("/sheets")
	public Map<String, Object> sheets() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (String[] s : ReportService.SHEETS) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", s[0]);
			m.put("title", s[1]);
			m.put("cat", s[2]);
			list.add(m);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("sheets", list);
		out.put("presets", ReportService.PRESETS);
		return out;
	}

	/** 요청 본문 → 보고서 요청. 모델이 없거나 기간이 이상하면 IllegalArgumentException(화면에 그대로) */
	@SuppressWarnings("unchecked")
	private ReportService.Req req(Map<String, Object> body) {
		ReportService.Req r = new ReportService.Req();
		r.dmId = body.get("dataModelId") == null ? "" : body.get("dataModelId").toString().trim();
		if (r.dmId.isEmpty()) throw new IllegalArgumentException("데이터 모델을 고르세요.");
		Map<String, Object> m = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", r.dmId);
		if (m == null) throw new IllegalArgumentException("없는 데이터 모델입니다: " + r.dmId);
		if (body.get("owners") instanceof List) for (Object o : (List<Object>) body.get("owners")) r.owners.add(o == null ? "" : o.toString());
		if (body.get("historyDays") != null) {
			int d = ((Number) body.get("historyDays")).intValue();
			if (!DAYS.contains(d)) throw new IllegalArgumentException("변경 이력 기간은 7·30·90·365일 중 하나입니다.");
			r.historyDays = d;
		}
		r.ddlDbType = (String) body.get("ddlDbType");
		r.userId = sessionService.getUserId();
		r.userNm = sqlSessionTemplate.selectOne("aprv.selectUserNm", r.userId);
		r.allChanges = sessionService.hasPerm(PermCode.DM_APPROVE);
		return r;
	}

	private static Response fail(String msg) {
		Response res = new Response();
		res.setResultInfo(RestResult.CODE_500.getCode(), msg);
		return res;
	}

	/** 시트 하나의 앞 10행 — {dataModelId, sheet, owners, historyDays, ddlDbType} → {notes, headers, rows, total} */
	@PostMapping("/preview")
	public Mono<Response> preview(@RequestBody Map<String, Object> body) {
		try {
			ReportService.SheetData d = reportService.build(req(body), (String) body.get("sheet"));
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("id", d.id);
			out.put("title", d.title);
			out.put("notes", d.notes);
			out.put("headers", d.headers);
			out.put("rows", d.rows.subList(0, Math.min(10, d.rows.size())));
			List<Integer> flagged = new ArrayList<>();
			for (int i = 0; i < Math.min(10, d.rows.size()); i++) if (d.flagged.contains(i)) flagged.add(i);
			out.put("flagged", flagged);
			out.put("total", d.rows.size());
			Response res = new Response();
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new ObjectMapper().writeValueAsString(out));
			return Mono.just(res);
		} catch (IllegalArgumentException e) {
			return Mono.just(fail(e.getMessage()));
		} catch (Exception e) {
			log.error(">> report preview failed: {}", e.getMessage(), e);
			return Mono.just(fail("미리보기를 만들지 못했습니다: " + e.getMessage()));
		}
	}

	/**
	 * xlsx 내려받기 — {dataModelId, sheets, owners, historyDays, ddlDbType}. 검사 실패면 400 과 JSON {resultMessage}
	 * (화면은 blob 으로 받으므로 실패 본문을 읽어 안내한다)
	 */
	@PostMapping("/download")
	@SuppressWarnings("unchecked")
	public void download(@RequestBody Map<String, Object> body, HttpServletResponse response) throws Exception {
		ReportService.Req r;
		List<String> ids = new ArrayList<>();
		try {
			r = req(body);
			if (body.get("sheets") instanceof List) for (Object o : (List<Object>) body.get("sheets")) ids.add(String.valueOf(o));
			if (ids.isEmpty()) throw new IllegalArgumentException("시트를 하나 이상 고르세요.");
			for (String id : ids) if (ReportService.title(id) == null) throw new IllegalArgumentException("없는 시트입니다: " + id);
		} catch (IllegalArgumentException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.setContentType("application/json; charset=UTF-8");
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("resultCode", 400);
			m.put("resultMessage", e.getMessage());
			response.getWriter().write(new ObjectMapper().writeValueAsString(m));
			return;
		}
		String fn = reportService.fileName(r);
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(fn, "UTF-8").replace("+", "%20") + "\"");
		long t0 = System.currentTimeMillis();
		reportService.write(r, ids, response.getOutputStream());
		log.info(">> report download dm={} sheets={} {}ms", r.dmId, ids.size(), System.currentTimeMillis() - t0);
	}
}
