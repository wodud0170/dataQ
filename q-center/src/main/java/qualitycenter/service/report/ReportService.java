package qualitycenter.service.report;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.service.StdDictService;

import qualitycenter.controller.DaStatController;
import qualitycenter.controller.DataModelController;
import qualitycenter.service.ddl.DdlDialects;
import qualitycenter.service.ddl.DdlGenerator;

/**
 * 108 §4.37 (92번) — 종합 분석 보고서. 모델 하나를 여러 시트로 — 미리보기(앞 10행)와 xlsx 내려받기가 같은 시트 생성기를 쓴다.
 *
 * <p>테이블·컬럼·제약조건은 화면과 같이 다른 사용자의 미승인 행을 뺀다({@link DataModelController#visibleTo}).
 * 표지의 표준 준수율·구조 일치율·DA 목표·달성은 시스템별 현황({@link DaStatController#models})과 같은 값이다(104 WP9-62·66).
 * 데이터 품질 진단은 DQ 제품으로 분리돼 넣지 않는다.</p>
 */
@Service
public class ReportService {

	/** 시트 — id, 이름, 묶음 */
	public static final String[][] SHEETS = {
			{ "A1", "표지·요약", "A 모델 개요" }, { "A2", "테이블 목록", "A 모델 개요" },
			{ "B1", "컬럼 전수", "B 구조 메타" }, { "B2", "인덱스", "B 구조 메타" }, { "B3", "제약조건", "B 구조 메타" }, { "B4", "FK 관계", "B 구조 메타" },
			{ "C1", "표준 준수율", "C 표준화 현황" }, { "C2", "비표준 컬럼", "C 표준화 현황" }, { "C3", "미등재 단어", "C 표준화 현황" }, { "C4", "누락 메타", "C 표준화 현황" },
			{ "D1", "PK 없는 테이블", "D DBA 점검" }, { "D2", "FK 인덱스 누락", "D DBA 점검" }, { "D3", "명명 충돌", "D DBA 점검" },
			{ "D4", "큰 테이블·긴 컬럼", "D DBA 점검" }, { "D5", "이름 규칙", "D DBA 점검" }, { "D6", "데이터 타입 분포", "D DBA 점검" },
			{ "D7", "NULL 허용 PK·FK", "D DBA 점검" },
			{ "E1", "최근 변경", "E 변경·거버넌스" }, { "E2", "승인 대기", "E 변경·거버넌스" }, { "E3", "변경 활동", "E 변경·거버넌스" },
			{ "F1", "표준 진단 최신", "F 진단 결과" }, { "F2", "구조 변경 진단", "F 진단 결과" },
			{ "G1", "DDL", "G DDL" } };

	/** 프리셋 — 92번의 "데이터 품질"은 품질 진단이 DQ 제품으로 분리돼 없앤다 */
	public static final Map<String, List<String>> PRESETS = new LinkedHashMap<>();
	static {
		List<String> all = new ArrayList<>();
		for (String[] s : SHEETS) all.add(s[0]);
		PRESETS.put("ALL", all);
		PRESETS.put("DBA", Arrays.asList("A1", "A2", "B2", "B3", "B4", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "G1"));
		PRESETS.put("STD", Arrays.asList("A1", "A2", "B1", "C1", "C2", "C3", "C4"));
		PRESETS.put("GOV", Arrays.asList("A1", "E1", "E2", "E3", "F2"));
	}

	private static final Set<String> LOB = new HashSet<>(Arrays.asList("CLOB", "NCLOB", "BLOB", "LONG", "LONG RAW", "TEXT", "NTEXT",
			"LONGTEXT", "MEDIUMTEXT", "LONGBLOB", "MEDIUMBLOB", "BYTEA", "IMAGE", "XML", "XMLTYPE"));

	@Autowired private SqlSessionTemplate sql;
	@Autowired private DaStatController daStat;
	@Autowired private DdlGenerator ddlGenerator;
	@Autowired private DdlDialects ddlDialects;
	@Autowired private StdDictService dictService;

	/** 보고서 요청 */
	public static class Req {
		public String dmId;
		public List<String> owners = new ArrayList<>();
		public int historyDays = 30;
		public String ddlDbType;
		public String userId;
		public String userNm;
		/** 데이터 모델 승인 권한(또는 관리자) — 변경 이력 시트에 모든 사용자 건 */
		public boolean allChanges;
	}

	/** 시트 하나 — 머리 위 안내 줄, 머리, 행, 표시할 행(연한 빨강) */
	public static class SheetData {
		public String id;
		public String title;
		public List<String> notes = new ArrayList<>();
		public List<String> headers = new ArrayList<>();
		public List<List<Object>> rows = new ArrayList<>();
		public Set<Integer> flagged = new HashSet<>();
		/** 표지처럼 항목·값 두 열 */
		public boolean keyValue;

		void row(boolean flag, Object... cells) {
			if (flag) flagged.add(rows.size());
			rows.add(new ArrayList<>(Arrays.asList(cells)));
		}
	}

	/** 한 요청 안에서 한 번만 읽는 데이터 */
	private class Ctx {
		final Req req;
		final Map<String, Object> cache = new HashMap<>();

		Ctx(Req req) { this.req = req; }

		@SuppressWarnings("unchecked")
		<T> T get(String k, Function<Ctx, T> f) {
			if (!cache.containsKey(k)) cache.put(k, f.apply(this));
			return (T) cache.get(k);
		}

		boolean ownerOk(Object owner) {
			return req.owners == null || req.owners.isEmpty() || req.owners.contains(owner == null ? "" : owner.toString());
		}

		Map<String, Object> model() { return get("model", c -> sql.selectOne("datamodel.selectDataModelById", req.dmId)); }

		List<Map<String, Object>> objs() {
			return get("objs", c -> filter(sql.selectList("report.selectObjs", req.dmId)));
		}

		List<Map<String, Object>> attrs() {
			return get("attrs", c -> filter(sql.selectList("report.selectAttrs", req.dmId)));
		}

		List<Map<String, Object>> indexes() {
			return get("indexes", c -> filter(sql.selectList("report.selectIndexes", req.dmId)));
		}

		List<Map<String, Object>> constraints() {
			return get("constraints", c -> filter(sql.selectList("datamodel.selectDataModelConstraintListByDmId", req.dmId)));
		}

		Map<String, Object> metrics() {
			return get("metrics", c -> {
				Map<String, Object> body = new HashMap<>();
				Object sysCd = model() == null ? null : model().get("dataModelSysCd");
				body.put("sysCd", sysCd == null ? "" : sysCd);
				body.put("inclChildren", "N");
				for (Map<String, Object> m : daStat.models(body)) if (req.dmId.equals(m.get("dmId"))) return m;
				return new HashMap<>();
			});
		}

		Map<String, Object> diagJob() { return get("diagJob", c -> sql.selectOne("report.selectLatestDiagJob", req.dmId)); }

		List<Map<String, Object>> diagResults() {
			return get("diagResults", c -> {
				List<Map<String, Object>> out = new ArrayList<>();
				if (diagJob() == null) return out;
				for (Map<String, Object> r : sql.<Map<String, Object>>selectList("report.selectDiagResults", diagJob().get("diagJobId")))
					if (ownerOk(r.get("objOwner"))) out.add(r);
				return out;
			});
		}

		List<Map<String, Object>> changes(boolean pendingOnly) {
			return get("changes" + pendingOnly, c -> {
				Map<String, Object> p = new HashMap<>();
				p.put("dmId", req.dmId);
				p.put("pendingOnly", pendingOnly);
				p.put("fromDt", pendingOnly ? null
						: new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(System.currentTimeMillis() - req.historyDays * 86400000L)));
				p.put("userId", req.allChanges ? null : req.userId);
				List<Map<String, Object>> out = new ArrayList<>();
				for (Map<String, Object> h : sql.<Map<String, Object>>selectList("report.selectChanges", p)) if (ownerOk(h.get("objOwner"))) out.add(h);
				return out;
			});
		}

		private List<Map<String, Object>> filter(List<Map<String, Object>> rows) {
			List<Map<String, Object>> out = new ArrayList<>();
			for (Map<String, Object> r : rows) {
				if (!ownerOk(r.get("objOwner"))) continue;
				if (!DataModelController.visibleTo((String) r.get("aprvStatus"), (String) r.get("requesterUserId"), req.userId)) continue;
				out.add(r);
			}
			return out;
		}
	}

	// ===== 공개 =====

	public static String title(String id) {
		for (String[] s : SHEETS) if (s[0].equals(id)) return s[1];
		return null;
	}

	/** 시트 하나 만들기 (없는 id 면 IllegalArgumentException) */
	public SheetData build(Req req, String id) {
		return build(new Ctx(req), id);
	}

	/** 내려받기 — 고른 시트를 시트 목록 순서로 */
	public void write(Req req, List<String> ids, OutputStream os) throws Exception {
		Ctx c = new Ctx(req);
		try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
			Styles st = new Styles(wb);
			for (String[] s : SHEETS) {
				if (!ids.contains(s[0])) continue;
				SheetData d = build(c, s[0]);
				if ("A1".equals(s[0])) d.rows.add(Arrays.asList("포함 시트", String.join(", ", ids)));
				writeSheet(wb, st, d);
			}
			wb.write(os);
			wb.dispose();
		}
	}

	// ===== 시트 생성 =====

	private SheetData build(Ctx c, String id) {
		String t = title(id);
		if (t == null) throw new IllegalArgumentException("없는 시트입니다: " + id);
		SheetData d = new SheetData();
		d.id = id;
		d.title = t;
		switch (id) {
			case "A1": a1(c, d); break;
			case "A2": a2(c, d); break;
			case "B1": b1(c, d); break;
			case "B2": b2(c, d); break;
			case "B3": b3(c, d); break;
			case "B4": b4(c, d); break;
			case "C1": c1(c, d); break;
			case "C2": c2(c, d); break;
			case "C3": c3(c, d); break;
			case "C4": c4(c, d); break;
			case "D1": d1(c, d); break;
			case "D2": d2(c, d); break;
			case "D3": d3(c, d); break;
			case "D4": d4(c, d); break;
			case "D5": d5(c, d); break;
			case "D6": d6(c, d); break;
			case "D7": d7(c, d); break;
			case "E1": e1(c, d); break;
			case "E2": e2(c, d); break;
			case "E3": e3(c, d); break;
			case "F1": f1(c, d); break;
			case "F2": f2(c, d); break;
			default: g1(c, d); break;
		}
		return d;
	}

	private static String s(Object o) { return o == null ? "" : o.toString(); }

	private static String key(Map<String, Object> r) { return s(r.get("objOwner")) + "." + s(r.get("objNm")); }

	private static String tbl(Map<String, Object> r) {
		String o = s(r.get("objOwner"));
		return o.isEmpty() ? s(r.get("objNm")) : o + "." + r.get("objNm");
	}

	private static String typeOf(Map<String, Object> a) {
		String t = s(a.get("dataType"));
		long len = a.get("dataLen") == null ? 0 : ((Number) a.get("dataLen")).longValue();
		int dec = a.get("dataDecimalLen") == null ? 0 : ((Number) a.get("dataDecimalLen")).intValue();
		if (len <= 0) return t;
		return t + "(" + len + (dec > 0 ? "," + dec : "") + ")";
	}

	private static String rate(Object r) { return r == null ? "미진단" : r + "%"; }

	private void a1(Ctx c, SheetData d) {
		d.keyValue = true;
		d.headers = Arrays.asList("항목", "값");
		Map<String, Object> m = c.model();
		Map<String, Object> mt = c.metrics();
		Map<String, Object> ds = ddlDialects.modelDs(c.req.dmId);
		Map<String, Object> job = c.diagJob();
		Object sdo = sql.selectOne("structdiag.selectLatestStructDiagByModel", c.req.dmId);
		Map<String, Object> sd = sdo == null ? null : toMap(sdo);
		d.row(false, "데이터 모델", s(m.get("dataModelNm")));
		d.row(false, "시스템", mt.get("sysNm") == null ? "(시스템 미지정)" : mt.get("sysNm"));
		d.row(false, "데이터 소스", ds == null ? "-" : s(ds.get("dsn")));
		d.row(false, "DBMS", ds == null ? "-" : s(ds.get("dbmsTp")));
		d.row(false, "소유자 필터", c.req.owners == null || c.req.owners.isEmpty() ? "전체" : String.join(", ", c.req.owners));
		d.row(false, "테이블 수", c.objs().size());
		d.row(false, "컬럼 수", c.attrs().size());
		d.row(false, "인덱스 수", distinct(c.indexes(), "indexNm"));
		d.row(false, "제약조건 수", distinct(c.constraints(), "constraintNm"));
		d.row(false, "표준 준수율", rate(mt.get("stndRate")));
		d.row(false, "구조 일치율", rate(mt.get("structRate")));
		boolean stndMiss = "N".equals(mt.get("stndAchv")), structMiss = "N".equals(mt.get("structAchv"));
		d.row(stndMiss, "표준 준수율 목표", mt.get("stndGoal") == null ? "목표 없음" : mt.get("stndGoal") + "% — " + ("Y".equals(mt.get("stndAchv")) ? "달성" : stndMiss ? "미달" : "미진단")
				+ (mt.get("goalSrc") == null ? "" : " (" + mt.get("goalSrc") + " 목표)"));
		d.row(structMiss, "구조 일치율 목표", mt.get("structGoal") == null ? "목표 없음" : mt.get("structGoal") + "% — " + ("Y".equals(mt.get("structAchv")) ? "달성" : structMiss ? "미달" : "미진단"));
		d.row(false, "마지막 표준 진단", job == null ? "없음" : s(job.get("endDt")) + " (사전 " + s(job.get("dictNm")) + ")");
		d.row(false, "마지막 구조 진단", sd == null ? "없음" : s(sd.get("diagDt")));
		d.row(false, "만든 일시", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		d.row(false, "만든 사람", c.req.userNm == null ? c.req.userId : c.req.userNm + " (" + c.req.userId + ")");
		d.row(false, "변경 이력 기간", "최근 " + c.req.historyDays + "일" + (c.req.allChanges ? "" : " · 내 변경만 (데이터 모델 승인 권한이 없음)"));
	}

	private static int distinct(List<Map<String, Object>> rows, String k) {
		Set<String> set = new HashSet<>();
		for (Map<String, Object> r : rows) set.add(key(r) + "." + s(r.get("tableNm")) + "." + s(r.get(k)));
		return set.size();
	}

	private void a2(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블 영문명", "테이블 한글명", "업무영역", "주제영역", "컬럼 수", "인덱스 수", "테이블스페이스", "코멘트");
		Map<String, Integer> cols = new HashMap<>(), idx = new HashMap<>();
		for (Map<String, Object> a : c.attrs()) cols.merge(key(a), 1, Integer::sum);
		Set<String> seen = new HashSet<>();
		for (Map<String, Object> i : c.indexes()) {
			String k = s(i.get("objOwner")) + "." + s(i.get("tableNm"));
			if (seen.add(k + "." + i.get("indexNm"))) idx.merge(k, 1, Integer::sum);
		}
		for (Map<String, Object> o : c.objs()) {
			d.row(false, o.get("objOwner"), o.get("objNm"), o.get("objNmKr"), o.get("bizAreaNm"), o.get("subjAreaNm"),
					cols.getOrDefault(key(o), 0), idx.getOrDefault(key(o), 0), o.get("tablespaceNm"), o.get("objComment"));
		}
	}

	private void b1(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "순서", "컬럼 영문명", "컬럼 한글명", "데이터 타입", "PK", "FK", "NULL 허용", "기본값",
				"코멘트", "용어 표준", "도메인 표준", "구성 단어", "참조 테이블", "참조 컬럼");
		for (Map<String, Object> a : c.attrs()) {
			d.row(!"Y".equals(a.get("termsStndYn")), a.get("objOwner"), a.get("objNm"), a.get("attrOrd"), a.get("attrNm"), a.get("attrNmKr"), typeOf(a),
					a.get("pkYn"), a.get("fkYn"), a.get("nullableYn"), a.get("defaultVal"), a.get("attrComment"), a.get("termsStndYn"), a.get("domainStndYn"),
					a.get("wordLst"), a.get("fkParentObjNm") == null ? null : (s(a.get("fkParentObjOwner")).isEmpty() ? "" : a.get("fkParentObjOwner") + ".") + a.get("fkParentObjNm"),
					a.get("fkParentAttrNm"));
		}
	}

	private void b2(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "인덱스", "유형", "고유", "컬럼 순서", "컬럼", "정렬", "테이블스페이스");
		for (Map<String, Object> i : c.indexes()) {
			d.row(false, i.get("objOwner"), i.get("tableNm"), i.get("indexNm"), i.get("indexType"), i.get("uniqueness"), i.get("columnPos"),
					i.get("columnNm"), i.get("sortOrder"), i.get("tablespaceNm"));
		}
	}

	private static String consType(Object t) {
		switch (s(t)) {
			case "P": return "PK";
			case "R": return "FK";
			case "U": return "UNIQUE";
			case "C": return "CHECK";
			default: return s(t);
		}
	}

	private void b3(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "제약조건", "종류", "컬럼 순서", "컬럼", "참조 소유자", "참조 테이블", "참조 컬럼", "삭제 규칙", "조건", "상태");
		for (Map<String, Object> k : c.constraints()) {
			d.row(false, k.get("objOwner"), k.get("tableNm"), k.get("constraintNm"), consType(k.get("constraintType")), k.get("columnPos"), k.get("columnNm"),
					k.get("refOwner"), k.get("refTableNm"), k.get("refColumnNm"), k.get("deleteRule"), k.get("searchCondition"), k.get("status"));
		}
	}

	/** FK — 제약조건(R)마다 컬럼 목록 (순서대로) */
	private static Map<String, List<Map<String, Object>>> fks(Ctx c) {
		Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
		for (Map<String, Object> k : c.constraints()) {
			if (!"R".equals(k.get("constraintType"))) continue;
			m.computeIfAbsent(s(k.get("objOwner")) + "." + s(k.get("tableNm")) + "." + s(k.get("constraintNm")), x -> new ArrayList<>()).add(k);
		}
		return m;
	}

	private static String cols(List<Map<String, Object>> rows, String k) {
		List<String> out = new ArrayList<>();
		for (Map<String, Object> r : rows) out.add(s(r.get(k)));
		return String.join(", ", out);
	}

	private void b4(Ctx c, SheetData d) {
		d.headers = Arrays.asList("자식 테이블", "FK 이름", "자식 컬럼", "부모 테이블", "부모 컬럼", "삭제 규칙");
		for (List<Map<String, Object>> fk : fks(c).values()) {
			Map<String, Object> f = fk.get(0);
			String child = s(f.get("objOwner")).isEmpty() ? s(f.get("tableNm")) : f.get("objOwner") + "." + f.get("tableNm");
			String parent = s(f.get("refOwner")).isEmpty() ? s(f.get("refTableNm")) : f.get("refOwner") + "." + f.get("refTableNm");
			d.row(false, child, f.get("constraintNm"), cols(fk, "columnNm"), parent, cols(fk, "refColumnNm"), f.get("deleteRule"));
		}
	}

	private void c1(Ctx c, SheetData d) {
		d.headers = Arrays.asList("테이블", "테이블 한글명", "컬럼 수", "이슈 컬럼 수", "준수율");
		Map<String, Object> mt = c.metrics();
		if (c.diagJob() == null) {
			d.notes.add("표준 진단을 한 적이 없습니다 — 테이블별 준수율은 진단 뒤에 나옵니다.");
		} else {
			d.notes.add("준수율 = (전체 컬럼수 - 이슈 컬럼수) / 전체 컬럼수 × 100 — 마지막 표준 진단 " + c.diagJob().get("endDt"));
		}
		d.row(false, "(전체)", "", mt.get("totalColCnt"), mt.get("issueColCnt"), rate(mt.get("stndRate")));
		if (c.diagJob() == null) return;
		Map<String, Set<String>> issue = new HashMap<>();
		for (Map<String, Object> r : c.diagResults()) issue.computeIfAbsent(key(r), x -> new HashSet<>()).add(s(r.get("attrNm")));
		Map<String, Integer> cols = new HashMap<>();
		for (Map<String, Object> a : c.attrs()) cols.merge(key(a), 1, Integer::sum);
		for (Map<String, Object> o : c.objs()) {
			int n = cols.getOrDefault(key(o), 0), i = issue.getOrDefault(key(o), new HashSet<>()).size();
			Double r = n == 0 ? null : Math.round((n - i) * 1000.0 / n) / 10.0;
			d.row(i > 0, tbl(o), o.get("objNmKr"), n, i, r == null ? "-" : r + "%");
		}
	}

	private static String diagNm(Object t) {
		switch (s(t)) {
			case "TERM_NOT_EXIST": return "용어 미존재";
			case "TERM_KR_NM_MISMATCH": return "한글명 불일치";
			case "DATA_TYPE_MISMATCH": return "데이터 타입 불일치";
			case "DATA_LEN_MISMATCH": return "데이터 길이 불일치";
			default: return s(t);
		}
	}

	private void c2(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "컬럼 영문명", "컬럼 한글명", "이슈 유형", "표준값", "실제값", "내용");
		if (c.diagJob() == null) { d.notes.add("표준 진단을 한 적이 없습니다."); return; }
		d.notes.add("마지막 표준 진단 " + c.diagJob().get("endDt") + " 의 이슈");
		for (Map<String, Object> r : c.diagResults()) {
			d.row(true, r.get("objOwner"), r.get("objNm"), r.get("attrNm"), r.get("attrNmKr"), diagNm(r.get("diagType")), r.get("stdValue"),
					r.get("actualValue"), r.get("diagDetail"));
		}
	}

	private void c3(Ctx c, SheetData d) {
		d.headers = Arrays.asList("토큰", "쓰인 컬럼 수", "컬럼 예");
		Map<String, Object> p = new HashMap<>();
		p.put("dictIds", dictService.lineage(dictService.resolve(null, c.req.dmId)));
		Set<String> words = new HashSet<>(sql.<String>selectList("report.selectWordAbrvs", p));
		d.notes.add("컬럼 영문명을 _ 로 나눈 토큰 중 모델 표준사전(상위 사전 포함)의 승인 단어 영문약어에 없는 것 (숫자만인 토큰 뺌)");
		Map<String, List<String>> miss = new TreeMap<>();
		for (Map<String, Object> a : c.attrs()) {
			for (String tk : s(a.get("attrNm")).toUpperCase().split("_")) {
				if (tk.isEmpty() || tk.matches("\\d+") || words.contains(tk)) continue;
				List<String> l = miss.computeIfAbsent(tk, x -> new ArrayList<>());
				if (!l.contains(s(a.get("attrNm")))) l.add(s(a.get("attrNm")));
			}
		}
		List<Map.Entry<String, List<String>>> es = new ArrayList<>(miss.entrySet());
		es.sort((x, y) -> y.getValue().size() != x.getValue().size() ? y.getValue().size() - x.getValue().size() : x.getKey().compareTo(y.getKey()));
		for (Map.Entry<String, List<String>> e : es) {
			List<String> ex = e.getValue().subList(0, Math.min(3, e.getValue().size()));
			d.row(false, e.getKey(), e.getValue().size(), String.join(", ", ex) + (e.getValue().size() > 3 ? " 외" : ""));
		}
	}

	private void c4(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "컬럼 영문명", "컬럼 한글명", "누락");
		for (Map<String, Object> a : c.attrs()) {
			List<String> miss = new ArrayList<>();
			if (s(a.get("attrNmKr")).trim().isEmpty()) miss.add("한글명 없음");
			if (s(a.get("attrComment")).trim().isEmpty()) miss.add("코멘트 없음");
			if ("Y".equals(a.get("termsStndYn")) && !"Y".equals(a.get("domainStndYn"))) miss.add("표준 용어는 있으나 도메인 표준 아님");
			if (!miss.isEmpty()) d.row(miss.size() > 1, a.get("objOwner"), a.get("objNm"), a.get("attrNm"), a.get("attrNmKr"), String.join(" · ", miss));
		}
	}

	private void d1(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블 영문명", "테이블 한글명", "컬럼 수");
		Set<String> pk = new HashSet<>();
		for (Map<String, Object> a : c.attrs()) if ("Y".equals(a.get("pkYn"))) pk.add(key(a));
		for (Map<String, Object> k : c.constraints()) if ("P".equals(k.get("constraintType"))) pk.add(s(k.get("objOwner")) + "." + s(k.get("tableNm")));
		Map<String, Integer> cols = new HashMap<>();
		for (Map<String, Object> a : c.attrs()) cols.merge(key(a), 1, Integer::sum);
		for (Map<String, Object> o : c.objs()) if (!pk.contains(key(o))) d.row(true, o.get("objOwner"), o.get("objNm"), o.get("objNmKr"), cols.getOrDefault(key(o), 0));
	}

	private void d2(Ctx c, SheetData d) {
		d.headers = Arrays.asList("테이블", "FK 이름", "FK 컬럼", "부모 테이블");
		d.notes.add("FK 컬럼(순서대로)으로 시작하는 인덱스가 없는 FK — 조인·부모 삭제 때 전체 읽기 위험");
		Map<String, List<List<String>>> idx = new HashMap<>();
		Map<String, List<String>> cur = new LinkedHashMap<>();
		for (Map<String, Object> i : c.indexes())
			cur.computeIfAbsent(s(i.get("objOwner")) + "." + s(i.get("tableNm")) + "|" + s(i.get("indexNm")), x -> new ArrayList<>()).add(s(i.get("columnNm")).toUpperCase());
		for (Map.Entry<String, List<String>> e : cur.entrySet()) idx.computeIfAbsent(e.getKey().split("\\|")[0], x -> new ArrayList<>()).add(e.getValue());
		for (List<Map<String, Object>> fk : fks(c).values()) {
			Map<String, Object> f = fk.get(0);
			List<String> fc = new ArrayList<>();
			for (Map<String, Object> r : fk) fc.add(s(r.get("columnNm")).toUpperCase());
			boolean ok = false;
			for (List<String> ic : idx.getOrDefault(s(f.get("objOwner")) + "." + s(f.get("tableNm")), new ArrayList<>())) {
				if (ic.size() >= fc.size() && ic.subList(0, fc.size()).equals(fc)) { ok = true; break; }
			}
			if (!ok) d.row(true, s(f.get("objOwner")).isEmpty() ? f.get("tableNm") : f.get("objOwner") + "." + f.get("tableNm"), f.get("constraintNm"),
					cols(fk, "columnNm"), s(f.get("refOwner")).isEmpty() ? f.get("refTableNm") : f.get("refOwner") + "." + f.get("refTableNm"));
		}
	}

	private void d3(Ctx c, SheetData d) {
		d.headers = Arrays.asList("구분", "이름", "쓰인 다른 이름", "컬럼 수");
		Map<String, Set<String>> byKr = new TreeMap<>(), byEn = new TreeMap<>();
		Map<String, Integer> krCnt = new HashMap<>(), enCnt = new HashMap<>();
		for (Map<String, Object> a : c.attrs()) {
			String kr = s(a.get("attrNmKr")).trim(), en = s(a.get("attrNm")).trim().toUpperCase();
			if (kr.isEmpty() || en.isEmpty()) continue;
			byKr.computeIfAbsent(kr, x -> new java.util.TreeSet<>()).add(en);
			byEn.computeIfAbsent(en, x -> new java.util.TreeSet<>()).add(kr);
			krCnt.merge(kr, 1, Integer::sum);
			enCnt.merge(en, 1, Integer::sum);
		}
		for (Map.Entry<String, Set<String>> e : byKr.entrySet())
			if (e.getValue().size() > 1) d.row(true, "한글명 → 영문명 여럿", e.getKey(), String.join(", ", e.getValue()), krCnt.get(e.getKey()));
		for (Map.Entry<String, Set<String>> e : byEn.entrySet())
			if (e.getValue().size() > 1) d.row(true, "영문명 → 한글명 여럿", e.getKey(), String.join(", ", e.getValue()), enCnt.get(e.getKey()));
	}

	private void d4(Ctx c, SheetData d) {
		d.headers = Arrays.asList("구분", "테이블", "컬럼", "데이터 타입", "내용");
		Map<String, Integer> cols = new HashMap<>();
		for (Map<String, Object> a : c.attrs()) cols.merge(key(a), 1, Integer::sum);
		for (Map<String, Object> o : c.objs()) {
			int n = cols.getOrDefault(key(o), 0);
			if (n >= 100) d.row(true, "컬럼 100개 이상 테이블", tbl(o), "", "", "컬럼 " + n + "개");
		}
		for (Map<String, Object> a : c.attrs()) {
			String t = s(a.get("dataType")).toUpperCase();
			long len = a.get("dataLen") == null ? 0 : ((Number) a.get("dataLen")).longValue();
			if (LOB.contains(t)) d.row(false, "LOB 형", tbl(a), a.get("attrNm"), typeOf(a), "대용량 형");
			else if (len >= 4000) d.row(false, "긴 컬럼", tbl(a), a.get("attrNm"), typeOf(a), "길이 " + len);
		}
	}

	private static String nameIssue(String nm) {
		List<String> out = new ArrayList<>();
		if (!nm.matches("[A-Za-z0-9_]*")) out.add("영문·숫자·_ 아닌 글자");
		if (!nm.isEmpty() && Character.isDigit(nm.charAt(0))) out.add("숫자로 시작");
		if (nm.length() > 30) out.add("30자 넘음 (" + nm.length() + "자)");
		if (!nm.equals(nm.toUpperCase()) && !nm.equals(nm.toLowerCase())) out.add("대소문자 섞임");
		return String.join(" · ", out);
	}

	private void d5(Ctx c, SheetData d) {
		d.headers = Arrays.asList("구분", "테이블", "컬럼", "위반");
		for (Map<String, Object> o : c.objs()) {
			String v = nameIssue(s(o.get("objNm")));
			if (!v.isEmpty()) d.row(true, "테이블", tbl(o), "", v);
		}
		for (Map<String, Object> a : c.attrs()) {
			String v = nameIssue(s(a.get("attrNm")));
			if (!v.isEmpty()) d.row(true, "컬럼", tbl(a), a.get("attrNm"), v);
		}
	}

	private void d6(Ctx c, SheetData d) {
		d.headers = Arrays.asList("데이터 타입", "컬럼 수", "비율");
		Map<String, Integer> cnt = new TreeMap<>();
		for (Map<String, Object> a : c.attrs()) cnt.merge(s(a.get("dataType")).toUpperCase(), 1, Integer::sum);
		List<Map.Entry<String, Integer>> es = new ArrayList<>(cnt.entrySet());
		es.sort((x, y) -> y.getValue() - x.getValue());
		int total = c.attrs().size();
		for (Map.Entry<String, Integer> e : es)
			d.row(false, e.getKey().isEmpty() ? "(없음)" : e.getKey(), e.getValue(), Math.round(e.getValue() * 1000.0 / total) / 10.0 + "%");
	}

	private void d7(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "컬럼", "PK", "FK", "NULL 허용");
		for (Map<String, Object> a : c.attrs()) {
			if (("Y".equals(a.get("pkYn")) || "Y".equals(a.get("fkYn"))) && "Y".equals(a.get("nullableYn")))
				d.row(true, a.get("objOwner"), a.get("objNm"), a.get("attrNm"), a.get("pkYn"), a.get("fkYn"), a.get("nullableYn"));
		}
	}

	private static String dt(Object v) {
		String x = s(v);
		return x.length() >= 14 ? x.substring(0, 4) + "-" + x.substring(4, 6) + "-" + x.substring(6, 8) + " " + x.substring(8, 10) + ":" + x.substring(10, 12) : x;
	}

	private static String target(Map<String, Object> h) {
		String t = tbl(h);
		if (h.get("attrNm") != null) t += "." + h.get("attrNm");
		if (h.get("constraintNm") != null) t += " [" + h.get("constraintNm") + "]";
		if (h.get("indexNm") != null) t += " [" + h.get("indexNm") + "]";
		return t;
	}

	private void changeHead(Ctx c, SheetData d) {
		if (!c.req.allChanges) d.notes.add("내 변경만 — 데이터 모델 승인 권한이 있으면 모든 사용자의 변경이 들어갑니다.");
	}

	private void e1(Ctx c, SheetData d) {
		changeHead(c, d);
		d.notes.add("최근 " + c.req.historyDays + "일 변경");
		d.headers = Arrays.asList("변경 일시", "변경자", "유형", "대상", "상태", "승인자", "승인 일시");
		for (Map<String, Object> h : c.changes(false)) {
			d.row("REJECTED".equals(h.get("aprvStatus")), dt(h.get("changeDt")), h.get("changeUserNm") == null ? h.get("changeUserId") : h.get("changeUserNm"),
					h.get("changeType"), target(h), h.get("aprvStatus"), h.get("aprvUserId"), dt(h.get("aprvDt")));
		}
	}

	private void e2(Ctx c, SheetData d) {
		changeHead(c, d);
		d.headers = Arrays.asList("변경 일시", "변경자", "유형", "대상", "상태", "신청 일시");
		for (Map<String, Object> h : c.changes(true)) {
			d.row(false, dt(h.get("changeDt")), h.get("changeUserNm") == null ? h.get("changeUserId") : h.get("changeUserNm"), h.get("changeType"), target(h),
					"SUBMITTED".equals(h.get("aprvStatus")) ? "신청 (승인 대기)" : "저장만 (신청 전)", dt(h.get("submitDt")));
		}
	}

	private void e3(Ctx c, SheetData d) {
		changeHead(c, d);
		d.notes.add("최근 " + c.req.historyDays + "일 변경 건수");
		d.headers = Arrays.asList("구분", "이름", "건수", "승인", "반려", "대기");
		Map<String, int[]> byUser = new TreeMap<>(), byTbl = new TreeMap<>();
		for (Map<String, Object> h : c.changes(false)) {
			String u = h.get("changeUserNm") == null ? s(h.get("changeUserId")) : h.get("changeUserNm") + " (" + h.get("changeUserId") + ")";
			for (int[] a : new int[][] { byUser.computeIfAbsent(u, x -> new int[4]), byTbl.computeIfAbsent(tbl(h), x -> new int[4]) }) {
				a[0]++;
				if ("APPROVED".equals(h.get("aprvStatus"))) a[1]++;
				else if ("REJECTED".equals(h.get("aprvStatus"))) a[2]++;
				else a[3]++;
			}
		}
		for (Map.Entry<String, int[]> e : byUser.entrySet()) d.row(false, "사용자", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]);
		for (Map.Entry<String, int[]> e : byTbl.entrySet()) d.row(false, "테이블", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]);
	}

	private void f1(Ctx c, SheetData d) {
		d.keyValue = true;
		d.headers = Arrays.asList("항목", "값");
		Map<String, Object> job = c.diagJob();
		if (job == null) { d.notes.add("표준 진단을 한 적이 없습니다."); return; }
		Map<String, Object> mt = c.metrics();
		d.row(false, "진단 일시", job.get("endDt"));
		d.row(false, "진단한 사람", job.get("cretUserId"));
		d.row(false, "기준 사전", job.get("dictNm"));
		d.row(false, "진단 컬럼 수", job.get("totalCnt"));
		d.row(false, "이슈 건수", job.get("resultCnt"));
		d.row(false, "이슈 컬럼 수", mt.get("issueColCnt"));
		d.row(false, "표준 준수율", rate(mt.get("stndRate")));
		Map<String, Integer> byType = new LinkedHashMap<>();
		for (String t : new String[] { "TERM_NOT_EXIST", "TERM_KR_NM_MISMATCH", "DATA_TYPE_MISMATCH", "DATA_LEN_MISMATCH" }) byType.put(t, 0);
		for (Map<String, Object> r : c.diagResults()) byType.merge(s(r.get("diagType")), 1, Integer::sum);
		for (Map.Entry<String, Integer> e : byType.entrySet()) d.row(e.getValue() > 0, diagNm(e.getKey()), e.getValue() + "건");
		if (!c.req.owners.isEmpty()) d.notes.add("이슈 유형별 건수는 소유자 필터를 적용한 값");
	}

	private static String chgNm(Object t) {
		switch (s(t)) {
			case "ADDED": return "추가";
			case "DELETED": return "삭제";
			case "MODIFIED": return "변경";
			default: return s(t);
		}
	}

	private void f2(Ctx c, SheetData d) {
		d.headers = Arrays.asList("소유자", "테이블", "컬럼", "구분", "이전 타입", "현재 타입", "이전 길이", "현재 길이", "이전 NULL", "현재 NULL", "반영");
		Object sdo = sql.selectOne("structdiag.selectLatestStructDiagByModel", c.req.dmId);
		Map<String, Object> sd = sdo == null ? null : toMap(sdo);
		if (sd == null) { d.notes.add("구조 진단을 한 적이 없습니다."); return; }
		d.notes.add("마지막 구조 진단 " + sd.get("diagDt") + " — 데이터 소스 실제 구조와 모델의 차이");
		for (Object o : sql.selectList("structdiag.selectStructDiagDetailList", sd.get("diagId"))) {
			Map<String, Object> r = toMap(o);
			if (!c.ownerOk(r.get("owner"))) continue;
			d.row(true, r.get("owner"), r.get("tableNm"), r.get("columnNm"), chgNm(r.get("changeType")), r.get("prevDataType"), r.get("currDataType"),
					r.get("prevDataLen"), r.get("currDataLen"), r.get("prevNullable"), r.get("currNullable"), r.get("appliedDt") == null ? "" : "반영함");
		}
	}

	/** 구조 진단 결과 매퍼는 VO(resultMap)라 Map 으로 바꿔 읽는다 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> toMap(Object o) {
		if (o instanceof Map) return (Map<String, Object>) o;
		return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(o, Map.class);
	}

	private void g1(Ctx c, SheetData d) {
		d.headers = Arrays.asList("DDL");
		String dbType = c.req.ddlDbType;
		if (dbType == null || dbType.trim().isEmpty()) {
			Map<String, Object> ds = ddlDialects.modelDs(c.req.dmId);
			dbType = ds == null ? null : (String) ds.get("dbmsTp");
		}
		d.notes.add("DBMS " + (dbType == null ? "Oracle (모델에 데이터 소스가 없음)" : dbType) + " — 데이터 모델 > DDL 내려받기와 같은 스크립트"
				+ (c.req.owners.isEmpty() ? "" : " (소유자 필터는 DDL 에 적용하지 않음)"));
		for (String line : ddlGenerator.generate(c.req.dmId, dbType, c.req.userId).split("\\r?\\n")) d.row(false, line);
	}

	// ===== xlsx =====

	private static class Styles {
		final CellStyle head, flag, note, key;

		Styles(SXSSFWorkbook wb) {
			Font bold = wb.createFont();
			bold.setBold(true);
			head = wb.createCellStyle();
			head.setFont(bold);
			head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			head.setBorderBottom(BorderStyle.THIN);
			flag = wb.createCellStyle();
			flag.setFillForegroundColor(IndexedColors.ROSE.getIndex());
			flag.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			Font grey = wb.createFont();
			grey.setItalic(true);
			grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
			note = wb.createCellStyle();
			note.setFont(grey);
			key = wb.createCellStyle();
			key.setFont(bold);
		}
	}

	/** 시트 이름 — "A1 표지·요약" (31자, 금지 문자 뺌) */
	private static String sheetName(SheetData d) {
		String n = (d.id + " " + d.title).replaceAll("[\\\\/*?:\\[\\]]", "_");
		return n.length() > 31 ? n.substring(0, 31) : n;
	}

	private static void writeSheet(SXSSFWorkbook wb, Styles st, SheetData d) {
		SXSSFSheet sh = wb.createSheet(sheetName(d));
		int r = 0;
		for (String n : d.notes) {
			Cell cell = sh.createRow(r++).createCell(0);
			cell.setCellValue(n);
			cell.setCellStyle(st.note);
		}
		int headRow = r;
		Row h = sh.createRow(r++);
		int[] width = new int[d.headers.size()];
		for (int i = 0; i < d.headers.size(); i++) {
			Cell cell = h.createCell(i);
			cell.setCellValue(d.headers.get(i));
			cell.setCellStyle(st.head);
			width[i] = len(d.headers.get(i));
		}
		for (int i = 0; i < d.rows.size(); i++) {
			Row row = sh.createRow(r++);
			List<Object> vals = d.rows.get(i);
			for (int j = 0; j < vals.size() && j < Math.max(d.headers.size(), 1); j++) {
				Object v = vals.get(j);
				Cell cell = row.createCell(j);
				if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
				else cell.setCellValue(v == null ? "" : v.toString());
				if (d.flagged.contains(i)) cell.setCellStyle(st.flag);
				else if (d.keyValue && j == 0) cell.setCellStyle(st.key);
				if (j < width.length) width[j] = Math.max(width[j], len(v == null ? "" : v.toString()));
			}
		}
		for (int i = 0; i < width.length; i++) sh.setColumnWidth(i, Math.min(Math.max(width[i] + 2, 8), "G1".equals(d.id) ? 120 : 60) * 256);
		sh.createFreezePane(0, headRow + 1);
		if (!d.keyValue && !d.rows.isEmpty() && d.headers.size() > 1)
			sh.setAutoFilter(new CellRangeAddress(headRow, headRow + d.rows.size(), 0, d.headers.size() - 1));
	}

	/** 열 너비 — 한글은 두 칸 */
	private static int len(String v) {
		int n = 0;
		for (int i = 0; i < v.length() && n < 200; i++) n += v.charAt(i) > 0x2E80 ? 2 : 1;
		return n;
	}

	/** 내려받기 파일명 */
	public String fileName(Req req) {
		Map<String, Object> m = sql.selectOne("datamodel.selectDataModelById", req.dmId);
		String nm = m == null ? req.dmId : String.valueOf(m.get("dataModelNm"));
		return nm.replaceAll("[\\\\/:*?\"<>|]", "_") + "_종합분석_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".xlsx";
	}
}
