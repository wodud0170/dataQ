package com.ndata.quality.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 110번 3.12-12 — 표준 사전 입력 규칙을 화면 등록(q-center DataStandardController)과 엑셀 일괄등록(ExcelUploadService)이
 * 같이 쓴다. 예전에는 일괄등록·한글컬럼 일괄 표준화의 단어 영문약어 규칙이 화면 등록보다 느슨했고(숫자로 시작해도 통과),
 * DB 유니크 위반 문구 변환이 지금 인덱스 이름을 몰라 DB 원문이 그대로 나갔다.
 */
public final class StdInputRule {

	private StdInputRule() {}

	/** 단어 영문약어 — 대문자 영문으로 시작, 대문자·숫자만 (언더바는 용어의 단어 구분자라 안 된다) */
	public static final String WORD_ENG_ABRV_REGEX = "^[A-Z][A-Z0-9]*$";

	/** 단어 영문약어가 규칙에 맞지 않으면 안내 문구, 맞으면 null */
	public static String wordEngAbrvError(String abrv) {
		if (abrv != null && abrv.matches(WORD_ENG_ABRV_REGEX)) return null;
		return "단어 영문약어는 대문자 영문(A-Z)으로 시작하고 영문/숫자(0-9)만 사용할 수 있습니다. (입력값: " + abrv + ")";
	}

	/** 114 권고 채택(10-28)·110번 3.12-15-c — 길이가 있어야 하는 도메인 데이터 타입(문자열). 그 밖의 타입은 길이 0(빈칸)이 "길이 없음" 이다 */
	public static final java.util.Set<String> LEN_REQUIRED_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
			"VARCHAR", "VARCHAR2", "CHAR", "NVARCHAR", "NVARCHAR2", "NCHAR"));

	/** 도메인 데이터 길이가 그 타입에 맞지 않으면 안내 문구, 맞으면 null */
	public static String domainLenError(String dataType, long dataLen) {
		if (dataType == null || !LEN_REQUIRED_TYPES.contains(dataType.trim().toUpperCase()) || dataLen >= 1) return null;
		return "데이터 타입 " + dataType.trim().toUpperCase() + " 은(는) 데이터 길이를 1 이상으로 입력해야 합니다.";
	}

	/** 입력한 단어명이 다른 단어의 유사어(이음동의어)일 때의 안내 */
	public static String synonymOfMsg(String wordNm, String stdWordNm) {
		return "'" + wordNm + "'은(는) '" + stdWordNm + "'의 유사어입니다. 표준 단어 '" + stdWordNm + "'를 사용해주세요.";
	}

	private static final Pattern KEY_VAL = Pattern.compile("\\(([a-z_, ]+)\\)=\\(([^)]*)\\)");

	/** 유니크 위반 원문의 "Key (dict_id, word_nm)=(DEFAULT, 고객)" 에서 마지막 컬럼 값 */
	private static String dupValue(String msg) {
		Matcher m = KEY_VAL.matcher(msg);
		if (!m.find()) return null;
		String[] vals = m.group(2).split(",\\s*");
		return vals[vals.length - 1];
	}

	/**
	 * 단어·용어·도메인 유니크 위반을 사용자 문구로. 인덱스 이름(DDL 의 tb_word_ux_2·3, tb_terms_ux_1·2, tb_domain_ux_1 — 모두
	 * 사전(dict_id)별)으로 가른다 — 컬럼 이름으로 보면 FK 위반(예: 용어의 (dict_id, domain_nm))도 중복으로 읽힌다. 해당이 없으면 null
	 */
	public static String translateDuplicateError(String msg) {
		if (msg == null) return null;
		String v = dupValue(msg);
		String tail = v != null ? ": '" + v + "'" : ".";
		if (msg.contains("tb_word_ux_2")) return "이미 등록된 단어 영문약어입니다" + tail;
		if (msg.contains("tb_word_ux_3")) return "이미 등록된 단어명입니다" + tail;
		if (msg.contains("tb_terms_ux_2")) return "이미 등록된 용어 영문약어입니다" + tail;
		if (msg.contains("tb_terms_ux_1")) return "이미 등록된 용어명입니다" + tail;
		if (msg.contains("tb_domain_ux_1")) return "이미 등록된 도메인명입니다" + tail;
		return null;
	}
}
