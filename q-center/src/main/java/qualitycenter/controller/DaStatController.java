package qualitycenter.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.DaStatService;
import com.ndata.quality.service.ExcelDownloadService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP9-62·66 — 시스템 단위 차이 분석과 DA 목표 달성도 (데이터 모델 > 시스템별 현황, 대시보드 카드).
 *
 * <p>시스템 트리(TB_SYS_INFO)마다 그 시스템과 하위 시스템에 속한 모델을 묶어 표준 준수율(전체·이슈 컬럼 합으로 가중)과
 * 구조 일치율(전체·차이 합으로 가중)을 낸다. 시스템이 없는 모델은 "(시스템 미지정)" 한 줄로 묶는다.
 * 목표(TB_DA_TARGET)는 모델 → 속한 시스템 → 상위 시스템 순으로 가까운 것을 쓴다. 조회는 누구나, 목표 저장·삭제는 시스템 관리 권한(SYS_ADMIN).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/daStat")
public class DaStatController {

	/** 시스템이 없는 모델을 묶는 줄의 코드 */
	private static final String UNASSIGNED = "";

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private DaStatService daStat;
	@Autowired private ExcelDownloadService excelDownloadService;

	// ===== 공통 =====

	private static class Ctx {
		Map<String, Map<String, Object>> sys = new LinkedHashMap<>();   // DFS 순서
		Map<String, List<String>> children = new HashMap<>();
		List<Map<String, Object>> models;
		Map<String, Map<String, Object>> targets = new HashMap<>();     // "SYS:cd" / "DM:id"
	}

	private Ctx load() {
		Ctx c = new Ctx();
		List<Map<String, Object>> all = sqlSessionTemplate.selectList("daStat.selectSystems");
		Map<String, Map<String, Object>> byCd = new HashMap<>();
		for (Map<String, Object> s : all) byCd.put((String) s.get("sysCd"), s);
		List<String> roots = new ArrayList<>();
		for (Map<String, Object> s : all) {
			String p = (String) s.get("pSysCd");
			if (p == null || p.isEmpty() || !byCd.containsKey(p)) roots.add((String) s.get("sysCd"));
			else c.children.computeIfAbsent(p, k -> new ArrayList<>()).add((String) s.get("sysCd"));
		}
		for (String r : roots) walk(c, byCd, r, 1, "");
		c.models = daStat.modelMetrics(null);
		for (Map<String, Object> t : sqlSessionTemplate.<Map<String, Object>>selectList("daStat.selectTargets")) {
			c.targets.put(t.get("targetTp") + ":" + t.get("targetId"), t);
		}
		return c;
	}

	private void walk(Ctx c, Map<String, Map<String, Object>> byCd, String cd, int level, String parentPath) {
		Map<String, Object> s = byCd.get(cd);
		String path = parentPath.isEmpty() ? (String) s.get("sysNm") : parentPath + " > " + s.get("sysNm");
		s.put("level", level);
		s.put("path", path);
		c.sys.put(cd, s);
		for (String ch : c.children.getOrDefault(cd, new ArrayList<>())) walk(c, byCd, ch, level + 1, path);
	}

	/** 그 시스템과 하위 시스템 코드 */
	private Set<String> subtree(Ctx c, String cd) {
		Set<String> out = new HashSet<>();
		out.add(cd);
		for (String ch : c.children.getOrDefault(cd, new ArrayList<>())) out.addAll(subtree(c, ch));
		return out;
	}

	private List<Map<String, Object>> modelsOf(Ctx c, String sysCd, boolean inclChildren) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (UNASSIGNED.equals(sysCd)) {
			for (Map<String, Object> m : c.models) if (m.get("sysCd") == null || !c.sys.containsKey(m.get("sysCd"))) out.add(m);
			return out;
		}
		Set<String> codes = inclChildren ? subtree(c, sysCd) : java.util.Collections.singleton(sysCd);
		for (Map<String, Object> m : c.models) if (codes.contains(m.get("sysCd"))) out.add(m);
		return out;
	}

	/** 모델 묶음 집계 — 진단한 모델만 가중 평균에 들어간다 */
	private static Map<String, Object> aggregate(List<Map<String, Object>> models) {
		long obj = 0, attr = 0, total = 0, issue = 0, sTotal = 0, sDiff = 0, added = 0, modified = 0, deleted = 0;
		int undiagStnd = 0, undiagStruct = 0, stndDiag = 0, structDiag = 0, structTarget = 0;
		for (Map<String, Object> m : models) {
			if ("Y".equals(m.get("structTargetYn")) || m.get("structRate") != null) structTarget++;
			obj += num(m.get("objCnt"));
			attr += num(m.get("attrCnt"));
			if (m.get("stndRate") != null) { total += num(m.get("totalColCnt")); issue += num(m.get("issueColCnt")); stndDiag++; }
			else undiagStnd++;
			if (m.get("structRate") != null) {
				sTotal += num(m.get("structTotal")); sDiff += num(m.get("structDiff"));
				added += num(m.get("addedColumns")); modified += num(m.get("modifiedColumns")); deleted += num(m.get("deletedColumns"));
				structDiag++;
			} else if ("Y".equals(m.get("structTargetYn"))) {
				undiagStruct++;   // 데이터소스가 없는 모델은 구조 진단 대상이 아니다
			}
		}
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("modelCnt", models.size());
		a.put("objCnt", obj);
		a.put("attrCnt", attr);
		a.put("totalColCnt", total);
		a.put("issueColCnt", issue);
		a.put("stndRate", stndDiag > 0 ? DaStatService.stndRate(total, issue) : null);
		a.put("structTotal", sTotal);
		a.put("structDiff", sDiff);
		a.put("structRate", structDiag > 0 ? DaStatService.structRate(sTotal, sDiff) : null);
		a.put("addedColumns", added);
		a.put("modifiedColumns", modified);
		a.put("deletedColumns", deleted);
		a.put("undiagStndCnt", undiagStnd);
		a.put("undiagStructCnt", undiagStruct);
		a.put("structTargetCnt", structTarget);   // 구조 진단 대상 모델 수 — 0 이면 화면이 "대상 아님"
		return a;
	}

	private static long num(Object o) {
		return o == null ? 0 : ((Number) o).longValue();
	}

	/** 가까운 목표 — DM 이면 모델 → 시스템 → 상위 시스템, SYS 면 시스템 → 상위 */
	private Map<String, Object> effectiveTarget(Ctx c, String dmId, String sysCd) {
		if (dmId != null && c.targets.containsKey("DM:" + dmId)) return withSrc(c.targets.get("DM:" + dmId), "모델");
		String cd = sysCd;
		while (cd != null && c.sys.containsKey(cd)) {
			if (c.targets.containsKey("SYS:" + cd)) {
				return withSrc(c.targets.get("SYS:" + cd), cd.equals(sysCd) && dmId == null ? "이 시스템" : (String) c.sys.get(cd).get("sysNm"));
			}
			cd = (String) c.sys.get(cd).get("pSysCd");
		}
		return null;
	}

	private static Map<String, Object> withSrc(Map<String, Object> t, String src) {
		Map<String, Object> o = new LinkedHashMap<>(t);
		o.put("goalSrc", src);
		return o;
	}

	/** 목표·달성 붙이기 — 현재 ≥ 목표면 Y */
	private static void putGoal(Map<String, Object> row, Map<String, Object> t) {
		Double sg = t == null ? null : dbl(t.get("stndRateGoal"));
		Double tg = t == null ? null : dbl(t.get("structRateGoal"));
		row.put("stndGoal", sg);
		row.put("structGoal", tg);
		row.put("goalSrc", t == null ? null : t.get("goalSrc"));
		row.put("stndAchv", achv(dbl(row.get("stndRate")), sg));
		row.put("structAchv", achv(dbl(row.get("structRate")), tg));
	}

	private static String achv(Double rate, Double goal) {
		if (rate == null || goal == null) return null;
		return rate >= goal ? "Y" : "N";
	}

	private static Double dbl(Object o) {
		return o == null ? null : ((Number) o).doubleValue();
	}

	private List<Map<String, Object>> systemRows(Ctx c) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Map<String, Object> s : c.sys.values()) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("sysCd", s.get("sysCd"));
			r.put("pSysCd", s.get("pSysCd"));
			r.put("sysNm", s.get("sysNm"));
			r.put("sysTp", s.get("sysTp"));
			r.put("level", s.get("level"));
			r.put("path", s.get("path"));
			r.put("childCnt", c.children.getOrDefault((String) s.get("sysCd"), new ArrayList<>()).size());
			r.putAll(aggregate(modelsOf(c, (String) s.get("sysCd"), true)));
			putGoal(r, effectiveTarget(c, null, (String) s.get("sysCd")));
			r.put("ownTarget", c.targets.containsKey("SYS:" + s.get("sysCd")) ? "Y" : "N");
			rows.add(r);
		}
		List<Map<String, Object>> un = modelsOf(c, UNASSIGNED, false);
		if (!un.isEmpty()) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("sysCd", UNASSIGNED);
			r.put("sysNm", "(시스템 미지정)");
			r.put("level", 1);
			r.put("path", "(시스템 미지정)");
			r.put("childCnt", 0);
			r.putAll(aggregate(un));
			putGoal(r, null);
			r.put("ownTarget", "N");
			rows.add(r);
		}
		return rows;
	}

	// ===== API =====

	/** 시스템 트리 + 집계 + 목표·달성 */
	@PostMapping("/systems")
	public List<Map<String, Object>> systems(@RequestBody(required = false) Map<String, Object> body) {
		return systemRows(load());
	}

	/** 시스템(하위 포함, inclChildren=N 이면 그 시스템만, sysCd='' 이면 미지정)의 모델별 지표 + 목표·달성 */
	@PostMapping("/models")
	public List<Map<String, Object>> models(@RequestBody Map<String, Object> body) {
		Ctx c = load();
		String sysCd = body.get("sysCd") == null ? UNASSIGNED : body.get("sysCd").toString();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> m : modelsOf(c, sysCd, !"N".equals(body.get("inclChildren")))) {
			Map<String, Object> r = new LinkedHashMap<>(m);
			Map<String, Object> s = c.sys.get(m.get("sysCd"));
			r.put("sysNm", s == null ? null : s.get("sysNm"));
			putGoal(r, effectiveTarget(c, (String) m.get("dmId"), (String) m.get("sysCd")));
			r.put("ownTarget", c.targets.containsKey("DM:" + m.get("dmId")) ? "Y" : "N");
			out.add(r);
		}
		return out;
	}

	/** 준수 현황 스냅샷 추이 — {sysCd} (하위 포함, '' 이면 미지정) 또는 {dmId}. 실행마다 가중 평균 */
	@PostMapping("/trend")
	public List<Map<String, Object>> trend(@RequestBody Map<String, Object> body) {
		List<String> dmIds = new ArrayList<>();
		if (body.get("dmId") != null && !body.get("dmId").toString().isEmpty()) {
			dmIds.add(body.get("dmId").toString());
		} else {
			Ctx c = load();
			for (Map<String, Object> m : modelsOf(c, body.get("sysCd") == null ? UNASSIGNED : body.get("sysCd").toString(), true)) dmIds.add((String) m.get("dmId"));
		}
		List<Map<String, Object>> out = new ArrayList<>();
		if (dmIds.isEmpty()) return out;
		Map<String, Object> p = new HashMap<>();
		p.put("dmIds", dmIds);
		Map<String, long[]> byRun = new LinkedHashMap<>();   // runId → {total, issue, stndModels, sTotal, sDiff, structModels}
		Map<String, String> runDt = new HashMap<>();
		for (Map<String, Object> s : sqlSessionTemplate.<Map<String, Object>>selectList("daStat.selectSnapshots", p)) {
			String run = (String) s.get("runId");
			runDt.put(run, (String) s.get("snapDt"));
			long[] a = byRun.computeIfAbsent(run, k -> new long[6]);
			if (s.get("totalColCnt") != null && num(s.get("totalColCnt")) > 0) { a[0] += num(s.get("totalColCnt")); a[1] += num(s.get("issueColCnt")); a[2]++; }
			if (s.get("structTotal") != null) { a[3] += num(s.get("structTotal")); a[4] += num(s.get("structDiff")); a[5]++; }
		}
		for (Map.Entry<String, long[]> e : byRun.entrySet()) {
			long[] a = e.getValue();
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("runId", e.getKey());
			r.put("snapDt", runDt.get(e.getKey()));
			r.put("stndRate", a[2] > 0 ? DaStatService.stndRate(a[0], a[1]) : null);
			r.put("structRate", a[5] > 0 ? DaStatService.structRate(a[3], a[4]) : null);
			out.add(r);
		}
		return out;
	}

	/** 대시보드 — 목표를 정한 시스템·모델의 현재·목표·달성. 미달 먼저 */
	@PostMapping("/achievement")
	public List<Map<String, Object>> achievement(@RequestBody(required = false) Map<String, Object> body) {
		Ctx c = load();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> t : c.targets.values()) {
			Map<String, Object> r = new LinkedHashMap<>();
			String tp = (String) t.get("targetTp");
			String id = (String) t.get("targetId");
			r.put("targetTp", tp);
			r.put("targetId", id);
			if ("SYS".equals(tp)) {
				Map<String, Object> s = c.sys.get(id);
				if (s == null) continue;
				r.put("nm", s.get("sysNm"));
				r.put("path", s.get("path"));
				r.putAll(aggregate(modelsOf(c, id, true)));
			} else {
				Map<String, Object> m = c.models.stream().filter(x -> id.equals(x.get("dmId"))).findFirst().orElse(null);
				if (m == null) continue;
				Map<String, Object> s = c.sys.get(m.get("sysCd"));
				r.put("nm", m.get("dmNm"));
				r.put("path", s == null ? "(시스템 미지정)" : s.get("path") + " > " + m.get("dmNm"));
				r.put("modelCnt", 1);
				r.put("stndRate", m.get("stndRate"));
				r.put("structRate", m.get("structRate"));
				r.put("structTargetCnt", "Y".equals(m.get("structTargetYn")) || m.get("structRate") != null ? 1 : 0);
			}
			putGoal(r, withSrc(t, "SYS".equals(tp) ? "이 시스템" : "모델"));
			out.add(r);
		}
		out.sort((a, b) -> {
			int ma = ("N".equals(a.get("stndAchv")) || "N".equals(a.get("structAchv"))) ? 0 : 1;
			int mb = ("N".equals(b.get("stndAchv")) || "N".equals(b.get("structAchv"))) ? 0 : 1;
			return ma != mb ? ma - mb : String.valueOf(a.get("path")).compareTo(String.valueOf(b.get("path")));
		});
		return out;
	}

	/** 목표 저장 — {targetTp: SYS|DM, targetId, stndRateGoal, structRateGoal, targetDesc}. 시스템 관리 권한 */
	@PostMapping("/target/save")
	public Mono<Response> saveTarget(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.SYS_ADMIN)) throw new IllegalStateException("권한이 없습니다 (시스템 관리 권한 필요)");
			String tp = String.valueOf(body.get("targetTp"));
			String id = body.get("targetId") == null ? "" : body.get("targetId").toString();
			if (!"SYS".equals(tp) && !"DM".equals(tp)) throw new IllegalArgumentException("목표 대상은 시스템 또는 모델입니다.");
			if (id.isEmpty()) throw new IllegalArgumentException("목표 대상을 골라 주세요.");
			Integer exists = sqlSessionTemplate.selectOne("daStat.countTargetObject", body);
			if (exists == null || exists == 0) throw new IllegalArgumentException("SYS".equals(tp) ? "시스템을 찾을 수 없습니다." : "데이터 모델을 찾을 수 없습니다.");
			BigDecimal sg = goal(body.get("stndRateGoal"), "표준 준수율 목표");
			BigDecimal tg = goal(body.get("structRateGoal"), "구조 일치율 목표");
			if (sg == null && tg == null) throw new IllegalArgumentException("표준 준수율·구조 일치율 목표 중 하나는 넣어 주세요.");
			Map<String, Object> p = new HashMap<>();
			p.put("targetTp", tp);
			p.put("targetId", id);
			p.put("stndRateGoal", sg);
			p.put("structRateGoal", tg);
			p.put("targetDesc", body.get("targetDesc"));
			p.put("userId", sessionService.getUserId());
			sqlSessionTemplate.insert("daStat.upsertTarget", p);
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	private static BigDecimal goal(Object o, String nm) {
		if (o == null || o.toString().trim().isEmpty()) return null;
		BigDecimal v;
		try {
			v = new BigDecimal(o.toString().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(nm + "는 숫자여야 합니다.");
		}
		if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException(nm + "는 0~100 사이여야 합니다.");
		return v.setScale(1, java.math.RoundingMode.HALF_UP);
	}

	/** 목표 삭제 — {targetTp, targetId} */
	@PostMapping("/target/delete")
	public Mono<Response> deleteTarget(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.SYS_ADMIN)) throw new IllegalStateException("권한이 없습니다 (시스템 관리 권한 필요)");
			sqlSessionTemplate.delete("daStat.deleteTarget", body);
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 시스템별 현황 엑셀 */
	@GetMapping("/download")
	public void download(HttpServletRequest request, HttpServletResponse response) {
		try {
			excelDownloadService.getDaSystemsExcel(systemRows(load()), request, response);
		} catch (Exception e) {
			log.error(">> daStat download failed : {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
