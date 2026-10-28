package qualitycenter.service.ddl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 104번 WP3 — DDL 규칙: 데이터 타입 매핑(TB_DATA_TYPE_MAP)과 PK 이름 규칙(TB_SYS_CONF.DDL_PK_NM_TMPL).
 * 관리 화면에서 바꾸면 invalidate() 로 바로 다시 읽는다. 다른 서버 인스턴스는 10초 안에 다시 읽는다.
 */
@Service
public class DdlRuleService {

	public static final String CONF_PK_NM_TMPL = "DDL_PK_NM_TMPL";
	private static final long TTL_MS = 10_000L;
	private static final Pattern OPTIONAL = Pattern.compile("\\[([^\\[\\]]*)\\]");

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/** DBMS_TP → DATA_TYPE → 행 */
	private volatile Map<String, Map<String, Map<String, Object>>> typeMap;
	private volatile String pkTemplate;
	private volatile long loadedAt;

	public void invalidate() {
		loadedAt = 0L;
	}

	private void load() {
		if (System.currentTimeMillis() - loadedAt < TTL_MS && typeMap != null) return;
		Map<String, Map<String, Map<String, Object>>> m = new HashMap<>();
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("ddlRule.selectTypeMapList", new HashMap<String, Object>());
		for (Map<String, Object> r : rows) {
			m.computeIfAbsent(String.valueOf(r.get("dbmsTp")), k -> new HashMap<>()).put(String.valueOf(r.get("dataType")), r);
		}
		Map<String, Object> conf = sqlSessionTemplate.selectOne("ddlRule.selectConf", CONF_PK_NM_TMPL);
		typeMap = m;
		pkTemplate = conf == null ? null : (String) conf.get("confVal");
		loadedAt = System.currentTimeMillis();
	}

	/**
	 * 데이터 타입·길이·소수점 → DBMS 의 물리 타입. 타입이 비어 있으면 VARCHAR 로 본다.
	 * 109번 #3 — 매핑 행이 있는 타입만 받는다(허용 목록). 예전에는 행이 없으면 입력한 타입 이름을 그대로 DDL 에 넣었다.
	 * 길이·소수점은 0 이상, 만든 물리 타입도 {@link DdlSafety#physType} 으로 다시 본다.
	 */
	public String physType(String dbmsTp, String dataType, Long dataLen, Short dataDecimalLen) {
		load();
		String t = (dataType == null || dataType.trim().isEmpty()) ? "VARCHAR" : dataType.trim().toUpperCase();
		long len = dataLen == null ? 0L : dataLen;
		int dec = dataDecimalLen == null ? 0 : dataDecimalLen;
		DdlSafety.length(len, dec);
		Map<String, Object> row = typeMap.getOrDefault(dbmsTp, new HashMap<>()).get(t);
		if (row == null)
			throw new DdlSafety.Invalid("DDL 규칙에 없는 데이터 타입입니다: " + DdlSafety.show(t) + " (" + dbmsTp
					+ ") — 관리 > DDL 규칙에서 매핑을 추가하거나 컬럼 타입을 고치세요");
		if (len <= 0 && row.get("dfltDataLen") != null) len = ((Number) row.get("dfltDataLen")).longValue();
		return DdlSafety.physType(render(String.valueOf(row.get("physDataType")), len, dec));
	}

	/** 114 권고 채택(10-28) — 그 DBMS 의 DDL 규칙에 이 타입 매핑이 있는지. 타입이 비면 VARCHAR 로 본다(physType 과 같다) */
	public boolean mapped(String dbmsTp, String dataType) {
		load();
		String t = (dataType == null || dataType.trim().isEmpty()) ? "VARCHAR" : dataType.trim().toUpperCase();
		return typeMap.getOrDefault(dbmsTp, new HashMap<>()).containsKey(t);
	}

	/**
	 * 114 권고 채택(10-28) — DDL 내려받기에서 매핑 없는 타입은 타입 이름 그대로 낸다(길이·소수점이 있으면 이름(길이[,소수점])).
	 * 타입 이름·길이·만든 타입은 저장 때와 같은 규칙으로 본다. [DB 반영]은 {@link #physType} 으로 계속 막는다.
	 */
	public static String rawType(String dataType, Long dataLen, Short dataDecimalLen) {
		String t = DdlSafety.dataType(dataType);
		long len = dataLen == null ? 0L : dataLen;
		int dec = dataDecimalLen == null ? 0 : dataDecimalLen;
		DdlSafety.length(len, dec);
		return DdlSafety.physType(render(t + "[({len}[,{dec}])]", len, dec));
	}

	/** 틀 → 타입. [...] 는 안쪽부터 보고, 안의 {len}·{dec} 값이 0 보다 클 때만 남긴다 */
	public static String render(String tmpl, long len, int dec) {
		String s = tmpl;
		Matcher m;
		while ((m = OPTIONAL.matcher(s)).find()) {
			String body = m.group(1);
			boolean keep = (!body.contains("{len}") || len > 0) && (!body.contains("{dec}") || dec > 0);
			s = s.substring(0, m.start()) + (keep ? body : "") + s.substring(m.end());
		}
		return s.replace("{len}", String.valueOf(len)).replace("{dec}", String.valueOf(dec));
	}

	public String pkTemplate() {
		load();
		return pkTemplate;
	}

	/** PK 이름 = 규칙의 {TABLE} 을 테이블 이름으로 */
	public String pkName(String table) {
		return pkTemplate().replace("{TABLE}", table);
	}

	// ---- 관리 화면 (관리 > DDL 규칙)

	/** 규칙이 있는 DBMS (DdlDialects 와 같은 5종) */
	public static final List<String> DBMS_LIST = java.util.Arrays.asList("Oracle", "Tibero", "PostgreSQL", "MariaDB", "SQLServer");

	public List<Map<String, Object>> listTypeMap(String dbmsTp) {
		Map<String, Object> p = new HashMap<>();
		p.put("dbmsTp", dbmsTp);
		return sqlSessionTemplate.selectList("ddlRule.selectTypeMapList", p);
	}

	/** 매핑 저장. isNew 면 추가(같은 타입·DBMS 가 있으면 거부), 아니면 수정 */
	public void saveTypeMap(boolean isNew, String dataType, String dbmsTp, String physDataType, Integer dfltDataLen, String userId) {
		String t = dataType == null ? "" : dataType.trim().toUpperCase();
		if (t.isEmpty()) throw new IllegalArgumentException("데이터 타입은 필수입니다.");
		if (t.length() > 50 || !t.matches("^[A-Z][A-Z0-9_ ]*$"))
			throw new IllegalArgumentException("데이터 타입은 영문 대문자·숫자·밑줄·공백으로 50자까지입니다: " + dataType);
		if (!DBMS_LIST.contains(dbmsTp)) throw new IllegalArgumentException("DDL 규칙이 없는 DBMS 입니다: " + dbmsTp);
		String phys = physDataType == null ? "" : physDataType.trim();
		if (phys.isEmpty()) throw new IllegalArgumentException("물리 타입은 필수입니다.");
		if (phys.length() > 100) throw new IllegalArgumentException("물리 타입이 너무 깁니다 (최대 100자).");
		DdlSafety.physTemplate(phys);   // 109번 #3 — 틀이 DDL 에 그대로 들어간다
		checkTemplate(phys, dfltDataLen);
		if (dfltDataLen != null && dfltDataLen <= 0) throw new IllegalArgumentException("기본 길이는 1 이상이어야 합니다.");
		DdlSafety.physType(render(phys, 10, 2));
		DdlSafety.physType(render(phys, dfltDataLen == null ? 0 : dfltDataLen, 0));
		Map<String, Object> p = new HashMap<>();
		p.put("dataType", t);
		p.put("dbmsTp", dbmsTp);
		p.put("physDataType", phys);
		p.put("dfltDataLen", dfltDataLen);
		p.put("now", now());
		p.put("userId", userId);
		int cnt = sqlSessionTemplate.selectOne("ddlRule.countTypeMap", p);
		if (isNew) {
			if (cnt > 0) throw new IllegalArgumentException("이미 있는 매핑입니다: " + dbmsTp + " " + t);
			sqlSessionTemplate.insert("ddlRule.insertTypeMap", p);
		} else {
			if (cnt == 0) throw new IllegalArgumentException("없는 매핑입니다: " + dbmsTp + " " + t);
			sqlSessionTemplate.update("ddlRule.updateTypeMap", p);
		}
		invalidate();
	}

	public void deleteTypeMap(String dataType, String dbmsTp) {
		Map<String, Object> p = new HashMap<>();
		p.put("dataType", dataType);
		p.put("dbmsTp", dbmsTp);
		sqlSessionTemplate.delete("ddlRule.deleteTypeMap", p);
		invalidate();
	}

	public void savePkTemplate(String tmpl, String userId) {
		String v = tmpl == null ? "" : tmpl.trim();
		if (!v.contains("{TABLE}")) throw new IllegalArgumentException("PK 이름 규칙에는 {TABLE} 이 있어야 합니다.");
		if (v.length() > 100) throw new IllegalArgumentException("PK 이름 규칙이 너무 깁니다 (최대 100자).");
		if (!v.replace("{TABLE}", "").matches("^[A-Za-z0-9_]*$"))
			throw new IllegalArgumentException("PK 이름 규칙에는 영문·숫자·밑줄과 {TABLE} 만 쓸 수 있습니다.");
		Map<String, Object> p = new HashMap<>();
		p.put("confKey", CONF_PK_NM_TMPL);
		p.put("confVal", v);
		p.put("now", now());
		p.put("userId", userId);
		sqlSessionTemplate.update("ddlRule.updateConf", p);
		invalidate();
	}

	/** 틀 검사 — 대괄호 짝, 자리표시는 {len}·{dec} 만, 대괄호 밖 {len} 은 기본 길이 필요 */
	private void checkTemplate(String phys, Integer dfltDataLen) {
		int depth = 0;
		for (char c : phys.toCharArray()) {
			if (c == '[') depth++;
			if (c == ']' && --depth < 0) break;
		}
		if (depth != 0) throw new IllegalArgumentException("물리 타입의 [ ] 짝이 맞지 않습니다: " + phys);
		if (phys.replace("{len}", "").replace("{dec}", "").matches(".*[{}].*"))
			throw new IllegalArgumentException("물리 타입의 자리표시는 {len}·{dec} 만 쓸 수 있습니다: " + phys);
		String outside = phys, prev;
		do {
			prev = outside;
			outside = outside.replaceAll("\\[[^\\[\\]]*\\]", "");
		} while (!outside.equals(prev));
		if (outside.contains("{len}") && dfltDataLen == null)
			throw new IllegalArgumentException("[ ] 밖에 {len} 이 있으면 기본 길이가 필요합니다 (길이 없는 컬럼에 쓸 값).");
	}

	private static String now() {
		return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
	}
}
