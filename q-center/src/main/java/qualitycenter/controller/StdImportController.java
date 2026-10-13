package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.StdDictService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

/**
 * 104번 WP5-14 — 공통표준 가져오기 (표준 사전 > 공통표준 가져오기).
 *
 * <p>원본 사전(참조 사전 — 보통 행안부 공통표준)의 단어·용어·도메인을 골라 기관 사전으로 복사한다.
 * 사전 간 이름 참조 FK(98번) 때문에 용어는 구성 단어·도메인(그룹·분류)을, 도메인은 그룹·분류를 대상 사전에 먼저 만든다.
 * 가져온 항목은 승인(Y)·공통표준여부 Y. 대상 사전에 같은 이름(용어·단어는 같은 영문약어도)이 있으면 건너뛴다.
 * 항목마다 따로 커밋해 한 건이 실패해도 나머지는 들어간다. 목록은 누구나, 가져오기는 표준 직접 관리 권한(STD_MANAGE).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/stdImport")
public class StdImportController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SqlSessionFactory sqlSessionFactory;
	@Autowired private SessionService sessionService;
	@Autowired private StdDictService dictService;

	private static String s(Object o) {
		return o == null ? null : o.toString().trim();
	}

	/** 목록 — {sourceDictId, targetDictId, kind: WORD|TERM|DOMAIN, keyword?, commOnly: Y(기본)|N}. 2,000건까지 */
	@PostMapping("/list")
	public Map<String, Object> list(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("sourceDictId", dictService.dictParam(s(body.get("sourceDictId"))).get("dictId"));
		p.put("targetDictId", dictService.dictParam(s(body.get("targetDictId"))).get("dictId"));
		p.put("keyword", s(body.get("keyword")));
		p.put("commOnly", "N".equals(body.get("commOnly")) ? "N" : "Y");
		String kind = s(body.get("kind"));
		String sql = "TERM".equals(kind) ? "stdImport.selectSourceTerms" : "DOMAIN".equals(kind) ? "stdImport.selectSourceDomains" : "stdImport.selectSourceWords";
		Map<String, Object> out = new HashMap<>();
		out.put("items", sqlSessionTemplate.selectList(sql, p));
		return out;
	}

	/**
	 * 가져오기 — {sourceDictId, targetDictId, kind, ids: [...]}.
	 *
	 * @return {imported, skipped, failed, addedWords, addedDomains, details: [{nm, status: IMPORTED|SKIPPED|FAILED, message}]}
	 */
	@PostMapping("/run")
	public Map<String, Object> run(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new LinkedHashMap<>();
		String src = (String) dictService.dictParam(s(body.get("sourceDictId"))).get("dictId");
		String tgt = (String) dictService.dictParam(s(body.get("targetDictId"))).get("dictId");
		String kind = s(body.get("kind"));
		@SuppressWarnings("unchecked")
		List<String> ids = (List<String>) body.get("ids");
		String err = null;
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) err = "권한이 없습니다 (표준 직접 관리 권한 필요)";
		else if (src.equals(tgt)) err = "원본 사전과 대상 사전이 같습니다.";
		else if (dictService.isRef(tgt)) err = "참조 사전에는 가져올 수 없습니다. 기관 사전을 고르세요.";
		else if (ids == null || ids.isEmpty()) err = "가져올 항목을 골라 주세요.";
		else if (ids.size() > 2000) err = "한 번에 2,000건까지 가져올 수 있습니다.";
		else if (!"WORD".equals(kind) && !"TERM".equals(kind) && !"DOMAIN".equals(kind)) err = "유형은 단어·용어·도메인입니다.";
		if (err != null) {
			out.put("error", err);
			return out;
		}
		Ctx ctx = new Ctx(src, tgt, sessionService.getUserId());
		List<Map<String, Object>> details = new ArrayList<>();
		int imported = 0, skipped = 0, failed = 0;
		for (String id : ids) {
			Map<String, Object> d = new LinkedHashMap<>();
			try (SqlSession ss = sqlSessionFactory.openSession()) {
				ctx.ss = ss;
				int words = ctx.words, domains = ctx.domains;
				try {
					String skip = "WORD".equals(kind) ? importWord(ctx, id, d) : "TERM".equals(kind) ? importTerm(ctx, id, d) : importDomain(ctx, id, d);
					if (skip == null) {
						ss.commit();
						d.put("status", "IMPORTED");
						imported++;
					} else {
						ss.rollback();
						d.put("status", "SKIPPED");
						d.put("message", skip);
						skipped++;
					}
				} catch (Exception e) {
					ss.rollback();
					ctx.words = words;
					ctx.domains = domains;
					d.put("status", "FAILED");
					d.put("message", e.getMessage());
					failed++;
				}
			}
			details.add(d);
		}
		out.put("imported", imported);
		out.put("skipped", skipped);
		out.put("failed", failed);
		out.put("addedWords", ctx.words);
		out.put("addedDomains", ctx.domains);
		out.put("details", details);
		saveHistory(kind, src, imported, skipped, failed);
		log.info(">> stdImport {} {} → {}: imported={} skipped={} failed={}", kind, src, tgt, imported, skipped, failed);
		return out;
	}

	/** 가져오기 한 번의 상태 — 대상·원본 사전, 실행 사용자, 함께 만든 단어·도메인 수 */
	private static class Ctx {
		final String src, tgt, userId;
		SqlSession ss;
		int words, domains;

		Ctx(String src, String tgt, String userId) {
			this.src = src;
			this.tgt = tgt;
			this.userId = userId;
		}

		Map<String, Object> p() {
			Map<String, Object> p = new HashMap<>();
			p.put("sourceDictId", src);
			p.put("targetDictId", tgt);
			p.put("dictId", tgt);
			p.put("userId", userId);
			return p;
		}
	}

	// ---- 단어 ----

	private String importWord(Ctx c, String id, Map<String, Object> d) {
		Map<String, Object> w = c.ss.selectOne("stdImport.selectWord", id);
		if (w == null || !c.src.equals(w.get("dictId"))) throw new IllegalArgumentException("원본 사전에 없는 단어입니다: " + id);
		d.put("nm", w.get("nm"));
		Map<String, Object> p = c.p();
		p.put("nm", w.get("nm"));
		if (c.ss.selectOne("stdImport.selectTargetWordByNm", p) != null) return "이미 있는 단어";
		p.put("engAbrvNm", w.get("engAbrvNm"));
		if ((Integer) c.ss.selectOne("stdImport.countTargetWordByEng", p) > 0) return "같은 영문약어(" + w.get("engAbrvNm") + ")의 단어가 있음";
		copyWord(c, w);
		return null;
	}

	/** 단어 복사 — 대상 사전 운영 옵션 검사, 형식단어면 도메인 분류도 */
	private String copyWord(Ctx c, Map<String, Object> w) {
		String newId = StringUtils.getUUID();
		dictService.checkWordEngAbrv(c.tgt, (String) w.get("engAbrvNm"));
		dictService.checkWordEngNmDup(c.tgt, (String) w.get("engNm"), newId);
		if ("Y".equals(w.get("clsfYn")) && w.get("domainClsfNm") != null && !((String) w.get("domainClsfNm")).isEmpty()) {
			ensureClsf(c, (String) w.get("domainClsfNm"));
		}
		Map<String, Object> p = c.p();
		p.put("newId", newId);
		p.put("srcId", w.get("id"));
		c.ss.insert("stdImport.copyWord", p);
		c.words++;
		return newId;
	}

	/** 용어의 구성 단어 — 대상에 같은 이름이 있으면 그 단어, 없으면 복사 */
	private String ensureWord(Ctx c, String srcWordId) {
		Map<String, Object> w = c.ss.selectOne("stdImport.selectWord", srcWordId);
		if (w == null) throw new IllegalArgumentException("구성 단어가 원본 사전에 없습니다: " + srcWordId);
		Map<String, Object> p = c.p();
		p.put("nm", w.get("nm"));
		Map<String, Object> hit = c.ss.selectOne("stdImport.selectTargetWordByNm", p);
		if (hit != null) return (String) hit.get("id");
		p.put("engAbrvNm", w.get("engAbrvNm"));
		if ((Integer) c.ss.selectOne("stdImport.countTargetWordByEng", p) > 0) {
			throw new IllegalArgumentException("구성 단어 '" + w.get("nm") + "'(" + w.get("engAbrvNm") + ") 를 가져올 수 없습니다 — 대상 사전에 같은 영문약어의 다른 단어가 있습니다.");
		}
		return copyWord(c, w);
	}

	// ---- 도메인 ----

	private String importDomain(Ctx c, String id, Map<String, Object> d) {
		Map<String, Object> dm = c.ss.selectOne("stdImport.selectDomain", id);
		if (dm == null || !c.src.equals(dm.get("dictId"))) throw new IllegalArgumentException("원본 사전에 없는 도메인입니다: " + id);
		d.put("nm", dm.get("nm"));
		Map<String, Object> p = c.p();
		p.put("nm", dm.get("nm"));
		if (c.ss.selectOne("stdImport.selectDomainIdByNm", p) != null) return "이미 있는 도메인";
		copyDomain(c, dm);
		return null;
	}

	private void copyDomain(Ctx c, Map<String, Object> dm) {
		ensureGrp(c, (String) dm.get("grpNm"));
		ensureClsf(c, (String) dm.get("clsfNm"));
		Map<String, Object> p = c.p();
		p.put("newId", StringUtils.getUUID());
		p.put("srcId", dm.get("id"));
		c.ss.insert("stdImport.copyDomain", p);
		c.domains++;
	}

	/** 용어의 도메인 — 대상에 같은 이름이 없으면 원본 도메인을 복사 */
	private void ensureDomain(Ctx c, String domainNm) {
		Map<String, Object> p = c.p();
		p.put("nm", domainNm);
		if (c.ss.selectOne("stdImport.selectDomainIdByNm", p) != null) return;
		Map<String, Object> sp = new HashMap<>();
		sp.put("dictId", c.src);
		sp.put("nm", domainNm);
		String srcId = c.ss.selectOne("stdImport.selectDomainIdByNm", sp);
		if (srcId == null) throw new IllegalArgumentException("도메인 '" + domainNm + "' 이 원본 사전에 없습니다.");
		copyDomain(c, c.ss.selectOne("stdImport.selectDomain", srcId));
	}

	private void ensureGrp(Ctx c, String nm) {
		Map<String, Object> p = c.p();
		p.put("nm", nm);
		if ((Integer) c.ss.selectOne("stdImport.countTargetGrp", p) > 0) return;
		p.put("newId", StringUtils.getUUID());
		if (c.ss.insert("stdImport.copyGrp", p) == 0) c.ss.insert("stdImport.insertGrp", p);
	}

	private void ensureClsf(Ctx c, String nm) {
		Map<String, Object> p = c.p();
		p.put("nm", nm);
		if ((Integer) c.ss.selectOne("stdImport.countTargetClsf", p) > 0) return;
		String grpNm = c.ss.selectOne("stdImport.selectSourceClsfGrp", p);
		if (grpNm == null) throw new IllegalArgumentException("도메인 분류 '" + nm + "' 이 원본 사전에 없습니다.");
		ensureGrp(c, grpNm);
		p.put("newId", StringUtils.getUUID());
		p.put("grpNm", grpNm);
		c.ss.insert("stdImport.insertClsf", p);
	}

	// ---- 용어 ----

	private String importTerm(Ctx c, String id, Map<String, Object> d) {
		Map<String, Object> t = c.ss.selectOne("stdImport.selectTerm", id);
		if (t == null || !c.src.equals(t.get("dictId"))) throw new IllegalArgumentException("원본 사전에 없는 용어입니다: " + id);
		d.put("nm", t.get("nm"));
		Map<String, Object> p = c.p();
		p.put("nm", t.get("nm"));
		p.put("engAbrvNm", t.get("engAbrvNm"));
		if ((Integer) c.ss.selectOne("stdImport.countTargetTerm", p) > 0) return "같은 이름이나 영문약어(" + t.get("engAbrvNm") + ")의 용어가 있음";
		String eng = dictService.checkTermEngAbrv(c.tgt, (String) t.get("engAbrvNm"));
		List<Map<String, Object>> words = c.ss.selectList("stdImport.selectTermWords", id);
		List<String> targetWordIds = new ArrayList<>();
		for (Map<String, Object> w : words) targetWordIds.add(ensureWord(c, (String) w.get("wordId")));
		if (t.get("domainNm") != null) ensureDomain(c, (String) t.get("domainNm"));
		// 코드 — 상위 코드가 대상에 없으면 단일로, 통합은 구성(TB_CODE_INTEG)을 옮기지 않으므로 단일로
		String codeTp = (String) t.get("codeTp");
		String parent = (String) t.get("parentCodeGrp");
		if (parent != null) {
			Map<String, Object> cp = c.p();
			cp.put("codeGrp", parent);
			if ((Integer) c.ss.selectOne("stdImport.countTargetCode", cp) == 0) {
				codeTp = "SINGLE";
				parent = null;
			}
		}
		if ("INTEG".equals(codeTp)) codeTp = "SINGLE";
		String newId = StringUtils.getUUID();
		Map<String, Object> ip = c.p();
		ip.put("newId", newId);
		ip.put("srcId", id);
		ip.put("engAbrvNm", eng);
		ip.put("codeTp", t.get("codeGrp") == null ? null : codeTp);
		ip.put("parentCodeGrp", parent);
		c.ss.insert("stdImport.copyTerm", ip);
		for (int i = 0; i < words.size(); i++) {
			Map<String, Object> tw = new HashMap<>();
			tw.put("termsId", newId);
			tw.put("wordId", targetWordIds.get(i));
			tw.put("wordNm", words.get(i).get("wordNm"));
			tw.put("wordOrd", words.get(i).get("wordOrd"));
			c.ss.insert("stdImport.insertTermWord", tw);
		}
		if (t.get("codeGrp") != null) {
			Map<String, Object> cp = c.p();
			cp.put("codeGrp", t.get("codeGrp"));
			c.ss.insert("stdImport.copyCodeData", cp);
		}
		return null;
	}

	/** 변경 이력 한 건 (BULK_INSERT, 등록 경로 STD_IMPORT) */
	private void saveHistory(String kind, String src, int imported, int skipped, int failed) {
		try {
			String kindNm = "WORD".equals(kind) ? "단어" : "TERM".equals(kind) ? "용어" : "도메인";
			Object srcNm = dictService.list().stream().filter(x -> src.equals(x.get("dictId"))).map(x -> x.get("dictNm")).findFirst().orElse(src);
			Map<String, Object> h = new HashMap<>();
			h.put("changeId", StringUtils.getUUID());
			h.put("changeType", "BULK_INSERT");
			h.put("targetType", kind);
			h.put("targetId", null);
			h.put("targetNm", kindNm + " 공통표준 가져오기 (" + srcNm + ")");
			h.put("changeCnt", imported);
			h.put("summary", kindNm + " 가져오기 (가져옴:" + imported + ", 건너뜀:" + skipped + ", 실패:" + failed + ")");
			h.put("prevValue", null);
			h.put("currValue", null);
			h.put("changeUserId", sessionService.getUserId());
			h.put("changeSource", "STD_IMPORT");
			sqlSessionTemplate.insert("changehistory.insertChangeHistory", h);
		} catch (Exception e) {
			log.warn("공통표준 가져오기 이력 저장 실패: {}", e.getMessage());
		}
	}
}
