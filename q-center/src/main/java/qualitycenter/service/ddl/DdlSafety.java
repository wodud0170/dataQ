package qualitycenter.service.ddl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 109번 #3 — [DB 반영] DDL 에 들어가는 값 검사. 저장(입력) 때와 DDL 을 만들 때 같은 규칙을 쓴다.
 *
 * <ul>
 * <li>이름(테이블·컬럼·소유자·제약조건·인덱스·테이블스페이스): 첫 글자 영문·한글·밑줄, 다음은 영문·숫자·밑줄·$·#·한글, 128자까지.
 *     따옴표로 감싸지 않고 그대로 쓴다 (MariaDB 는 # 이 주석 시작이라 # 도 받지 않는다 — {@link MariaDbDialect}).</li>
 * <li>데이터 타입: 저장 때는 영문으로 시작하는 단어(영문·숫자·밑줄)와 사이 공백, 50자까지. DDL 은 DDL 규칙(TB_DATA_TYPE_MAP)에
 *     그 DBMS 매핑이 있는 타입만 만든다({@link DdlRuleService#physType}). 길이·소수점은 0 이상의 정수.</li>
 * <li>기본값: 아래 중 하나. 바깥 괄호는 벗겨서 본다 (SQL Server 의 ((0)) 등).
 *   <ul>
 *   <li>숫자 — 부호·소수점·지수 (예: 0, -1, 3.14, 1e3)</li>
 *   <li>문자열 — 작은따옴표로 감싼 값, 안의 따옴표는 '' 로 (N'…' 허용)</li>
 *   <li>NULL</li>
 *   <li>DBMS 별 함수·키워드 목록({@link #FUNCS}) — 끝의 () 는 있어도 없어도 된다. DBMS 를 모르는 저장 때는 목록 전체</li>
 *   <li>PostgreSQL: 위 값 뒤의 ::타입, nextval('시퀀스'::regclass)</li>
 *   </ul>
 *   어디에 있든 ; -- /* *&#47; # \ 와 줄바꿈 같은 제어 문자는 받지 않는다. 255자까지.</li>
 * </ul>
 * 실행 직전에는 문장마다 {@link #checkStatement} 로 한 문장인지(문자열 밖 ; 없음)·주석 표시가 없는지·괄호와 따옴표 짝이 맞는지 다시 본다.
 */
public final class DdlSafety {

	private DdlSafety() {}

	/** 검사에 걸린 값. 메시지는 화면에 그대로 보인다 */
	public static class Invalid extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		public Invalid(String msg) { super(msg); }
	}

	private static final Pattern IDENT = Pattern.compile("^[A-Za-z_\\uAC00-\\uD7A3][A-Za-z0-9_$#\\uAC00-\\uD7A3]{0,127}$");
	private static final Pattern TYPE_IN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*( [A-Za-z][A-Za-z0-9_]*)*$");
	/** DDL 규칙으로 만든 물리 타입 — 예: VARCHAR2(10 CHAR), NUMBER(10,2), DOUBLE PRECISION, NVARCHAR(MAX) */
	private static final Pattern PHYS_OUT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*( [A-Za-z][A-Za-z0-9_]*)*(\\([A-Za-z0-9]+( [A-Za-z]+)?(,[0-9]+)?\\))?$");
	/** DDL 규칙의 물리 타입 틀 — 영문·숫자·밑줄·공백·( ) , 와 자리표시 {len}·{dec}, 선택 구간 [ ] */
	private static final Pattern PHYS_TMPL = Pattern.compile("^[A-Za-z0-9_ (),{}\\[\\]]+$");

	private static final Pattern NUM = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");
	private static final Pattern STR = Pattern.compile("^[Nn]?'([^']|'')*'$");
	private static final Pattern FUNC = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(\\(\\s*\\))?$");
	private static final Pattern PG_CAST = Pattern.compile("^(.+)::([A-Za-z_][A-Za-z0-9_ ]*(\\(\\d+(,\\d+)?\\))?(\\[\\])?)$");
	private static final Pattern PG_NEXTVAL = Pattern.compile("^(?i)nextval\\s*\\(\\s*'[A-Za-z_][A-Za-z0-9_$.]*'(::regclass)?\\s*\\)$");

	public static final List<String> DELETE_RULES = Arrays.asList("CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION");

	/** DBMS 별 기본값 함수·키워드 (대문자). 키는 데이터소스 DBMS_TP */
	public static final Map<String, Set<String>> FUNCS = new HashMap<>();
	private static final Set<String> ALL_FUNCS = new HashSet<>();
	static {
		FUNCS.put("Oracle", set("SYSDATE", "SYSTIMESTAMP", "CURRENT_DATE", "CURRENT_TIMESTAMP", "LOCALTIMESTAMP", "USER", "SYS_GUID"));
		FUNCS.put("Tibero", FUNCS.get("Oracle"));
		FUNCS.put("PostgreSQL", set("CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "LOCALTIME", "LOCALTIMESTAMP", "NOW",
				"CURRENT_USER", "SESSION_USER", "CLOCK_TIMESTAMP", "STATEMENT_TIMESTAMP", "TRANSACTION_TIMESTAMP",
				"GEN_RANDOM_UUID", "TRUE", "FALSE"));
		FUNCS.put("MariaDB", set("CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW", "CURDATE", "CURTIME", "LOCALTIME",
				"LOCALTIMESTAMP", "SYSDATE", "UTC_TIMESTAMP", "UUID", "TRUE", "FALSE"));
		FUNCS.put("SQLServer", set("GETDATE", "GETUTCDATE", "SYSDATETIME", "SYSUTCDATETIME", "SYSDATETIMEOFFSET", "CURRENT_TIMESTAMP",
				"NEWID", "NEWSEQUENTIALID", "SUSER_SNAME", "CURRENT_USER"));
		for (Set<String> s : FUNCS.values()) ALL_FUNCS.addAll(s);
	}

	private static Set<String> set(String... v) {
		return new HashSet<>(Arrays.asList(v));
	}

	// ---- 이름

	/** 이름 검사. what = 화면에 보일 항목 이름 (테이블·컬럼·소유자·제약조건·테이블스페이스 …) */
	public static String ident(String what, String v) {
		if (v == null || v.isEmpty()) throw new Invalid(what + " 이름이 비어 있습니다.");
		if (!IDENT.matcher(v).matches())
			throw new Invalid(what + " 이름에 쓸 수 없습니다: '" + show(v) + "' — 영문·한글·숫자·밑줄(_)·$·# 만, 첫 글자는 영문·한글·밑줄, 128자까지");
		return v;
	}

	/** 비어 있으면 통과 (소유자·테이블스페이스처럼 선택 항목) */
	public static String identOpt(String what, String v) {
		return (v == null || v.isEmpty()) ? v : ident(what, v);
	}

	// ---- 데이터 타입·길이

	/** 저장 때 데이터 타입 검사 (비어 있으면 통과 — 호출자가 기본 타입을 넣는다) */
	public static String dataType(String v) {
		if (v == null || v.trim().isEmpty()) return v;
		String t = v.trim();
		if (t.length() > 50 || !TYPE_IN.matcher(t).matches())
			throw new Invalid("데이터 타입에 쓸 수 없습니다: '" + show(v) + "' — 영문으로 시작하는 단어(영문·숫자·밑줄)만, 50자까지. 길이·소수점은 따로 넣습니다");
		return t;
	}

	public static void length(long len, long dec) {
		if (len < 0 || dec < 0) throw new Invalid("길이·소수점은 0 이상의 정수여야 합니다: " + len + ", " + dec);
	}

	/** DDL 규칙으로 만든 물리 타입 검사 */
	public static String physType(String v) {
		if (v == null || !PHYS_OUT.matcher(v).matches())
			throw new Invalid("DDL 규칙으로 만든 데이터 타입이 올바르지 않습니다: '" + show(v) + "' — 관리 > DDL 규칙의 물리 타입을 확인하세요");
		return v;
	}

	/** DDL 규칙 저장 때 물리 타입 틀 검사 */
	public static void physTemplate(String v) {
		if (v == null || !PHYS_TMPL.matcher(v).matches())
			throw new Invalid("물리 타입에는 영문·숫자·밑줄·공백·( ) , 와 {len}·{dec}·[ ] 만 쓸 수 있습니다: " + show(v));
	}

	// ---- 기본값

	/**
	 * 기본값 검사. dbmsTp 가 null 이면(저장 때) 모든 DBMS 의 함수 목록과 PostgreSQL 표기를 받는다.
	 * 비어 있으면 null, 아니면 앞뒤 공백을 뺀 값을 돌려준다.
	 */
	public static String defaultValue(String v, String dbmsTp) {
		if (v == null || v.trim().isEmpty()) return null;
		String t = v.trim();
		String why = forbidden(t);
		if (why == null && t.length() > 255) why = "255자를 넘습니다";
		if (why == null && !defaultForm(t, dbmsTp, 0))
			why = "숫자, 작은따옴표 문자열('' 로 따옴표 표현), NULL, 날짜·시각 함수(" + funcList(dbmsTp) + ")만 쓸 수 있습니다";
		if (why != null) throw new Invalid("기본값을 쓸 수 없습니다: " + show(v) + " — " + why);
		return t;
	}

	private static String forbidden(String t) {
		for (char c : t.toCharArray()) {
			if (c < 0x20 || c == 0x7f) return "줄바꿈·제어 문자는 쓸 수 없습니다";
		}
		if (t.indexOf(';') >= 0) return "; 는 쓸 수 없습니다";
		if (t.contains("--") || t.contains("/*") || t.contains("*/") || t.indexOf('#') >= 0) return "주석 표시(-- /* */ #)는 쓸 수 없습니다";
		if (t.indexOf('\\') >= 0) return "\\ 는 쓸 수 없습니다";
		return null;
	}

	private static boolean defaultForm(String t, String dbmsTp, int depth) {
		if (depth > 5) return false;
		t = t.trim();
		if (t.isEmpty()) return false;
		if (NUM.matcher(t).matches() || STR.matcher(t).matches() || "NULL".equalsIgnoreCase(t)) return true;
		Matcher f = FUNC.matcher(t);
		if (f.matches()) {
			Set<String> funcs = dbmsTp == null ? ALL_FUNCS : FUNCS.get(canonDbms(dbmsTp));
			return funcs != null && funcs.contains(f.group(1).toUpperCase());
		}
		if (t.startsWith("(") && t.endsWith(")")) return defaultForm(t.substring(1, t.length() - 1), dbmsTp, depth + 1);
		boolean pg = dbmsTp == null || "PostgreSQL".equals(canonDbms(dbmsTp));
		if (pg) {
			if (PG_NEXTVAL.matcher(t).matches()) return true;
			Matcher c = PG_CAST.matcher(t);
			if (c.matches()) return defaultForm(c.group(1), dbmsTp, depth + 1);
		}
		return false;
	}

	private static String funcList(String dbmsTp) {
		Set<String> s = dbmsTp == null ? null : FUNCS.get(canonDbms(dbmsTp));
		if (s == null) return "SYSDATE·CURRENT_TIMESTAMP·now()·getdate() 등";
		List<String> l = new java.util.ArrayList<>(s);
		java.util.Collections.sort(l);
		return String.join("·", l);
	}

	/** DdlDialects 와 같은 판정으로 DBMS_TP 를 FUNCS 키로 */
	static String canonDbms(String dbmsTp) {
		String t = dbmsTp.trim().toLowerCase();
		if (t.contains("tibero")) return "Tibero";
		if (t.contains("oracle")) return "Oracle";
		if (t.contains("postgres")) return "PostgreSQL";
		if (t.contains("maria")) return "MariaDB";
		if (t.contains("sqlserver") || t.contains("mssql")) return "SQLServer";
		return dbmsTp;
	}

	/** FK 삭제 규칙 */
	public static String deleteRule(String v) {
		String r = v == null ? "" : v.trim().toUpperCase();
		if (!DELETE_RULES.contains(r)) throw new Invalid("FK 삭제 규칙에 쓸 수 없습니다: " + show(v) + " — " + String.join("·", DELETE_RULES) + " 만");
		return r;
	}

	// ---- 실행 직전 문장 검사

	/**
	 * 실행할 한 문장 검사 — 문자열 밖에 ; 가 없고(한 문장), 주석 표시(-- /* *&#47;, MariaDB 는 #)가 없고,
	 * 문자열 따옴표와 괄호 짝이 맞아야 한다. backslashEscapes = MariaDB(문자열 안 \ 가 다음 글자를 감싼다).
	 */
	public static void checkStatement(String stmt, boolean backslashEscapes) {
		if (stmt == null || stmt.trim().isEmpty()) throw new Invalid("빈 문장입니다.");
		boolean inStr = false;
		int depth = 0;
		char[] s = stmt.toCharArray();
		for (int i = 0; i < s.length; i++) {
			char c = s[i];
			if (inStr) {
				if (backslashEscapes && c == '\\') { i++; continue; }
				if (c == '\'') {
					if (i + 1 < s.length && s[i + 1] == '\'') { i++; continue; }
					inStr = false;
				}
				continue;
			}
			char n = i + 1 < s.length ? s[i + 1] : 0;
			if (c == '\'') inStr = true;
			else if (c == ';') throw new Invalid("한 번에 한 문장만 실행합니다 — 문장 안에 ; 가 있습니다");
			else if ((c == '-' && n == '-') || (c == '/' && n == '*') || (c == '*' && n == '/') || (backslashEscapes && c == '#'))
				throw new Invalid("DDL 에 주석 표시가 있습니다");
			else if (c == '(') depth++;
			else if (c == ')' && --depth < 0) throw new Invalid("DDL 의 괄호 짝이 맞지 않습니다");
		}
		if (inStr) throw new Invalid("DDL 의 따옴표 짝이 맞지 않습니다");
		if (depth != 0) throw new Invalid("DDL 의 괄호 짝이 맞지 않습니다");
	}

	/** 메시지에 넣을 값 — 제어 문자는 공백으로, 60자까지 */
	static String show(String v) {
		if (v == null) return "(없음)";
		String s = oneLine(v);
		return s.length() > 60 ? s.substring(0, 60) + "…" : s;
	}

	/** 한 줄로 — 제어 문자는 공백으로 (DDL 주석 조각·메시지용) */
	public static String oneLine(String v) {
		if (v == null) return "";
		StringBuilder b = new StringBuilder();
		for (char c : v.toCharArray()) b.append(c < 0x20 || c == 0x7f ? ' ' : c);
		return b.toString();
	}
}
