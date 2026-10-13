package com.ndata.quality.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 98번 표준사전 세트 — 사전 조회 및 사전 속성 판정.
 *
 * <p>표준화 관례는 기관마다 다르다. "용어의 마지막 단어는 형식단어여야 한다" 는
 * 행안부 공통표준의 관례지 모든 사전의 규칙이 아니다. 그래서 이런 규칙은
 * 전역 설정이 아니라 <b>사전 속성</b>으로 둔다.</p>
 *
 * <p>q-center(화면)와 q-executor(일괄등록) 양쪽에서 쓰므로 q-common 에 있다.</p>
 *
 * <p>용어 일괄등록은 만 건 단위로 돈다. 행마다 사전 속성을 조회하면 그만큼
 * 쿼리가 나가므로 {@value #TTL_MS}ms 캐시를 둔다. 관리자가 사전 속성을 바꾸면
 * q-center 는 즉시(캐시 무효화), q-executor 는 TTL 이 지나면 따라온다.</p>
 */
@Service
@Slf4j
public class StdDictService {

	/** 사전을 못 고른 경우 쓰는 사전. 세트 도입 이전 데이터가 전부 여기 있다. */
	public static final String DEFAULT_DICT_ID = "DEFAULT";

	private static final long TTL_MS = 10_000L;

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	/** dictId → 사전 속성 */
	private volatile Map<String, Map<String, Object>> cache = null;
	private volatile long loadedAt = 0L;

	/** 사용 중인 사전 목록. */
	public List<Map<String, Object>> list() {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			return session.selectList("stddict.selectDictList");
		}
	}

	/** 사전별 등록 건수. */
	public Map<String, Object> stats(String dictId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			return session.selectOne("stddict.selectDictStats", dictId);
		}
	}

	/** 신규 모델 등록 시 기본 선택될 사전. */
	public String defaultDictId() {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			String id = session.selectOne("stddict.selectDefaultDictId");
			return id != null ? id : DEFAULT_DICT_ID;
		}
	}

	/**
	 * 작업에 쓸 사전을 정한다.
	 *
	 * <p>사람이 그 자리에서 고른 사전이 있으면 그것을, 없으면 모델에 저장된 사전을 쓴다.
	 * 둘 다 없으면 null — 호출자가 판단한다 (스케줄 진단은 여기서 막고,
	 * 수동 진단은 사용자에게 고르라고 한다).</p>
	 *
	 * @param explicitDictId 사용자가 지정한 사전 (없으면 null)
	 * @param dmId           대상 데이터 모델
	 */
	public String resolve(String explicitDictId, String dmId) {
		if (explicitDictId != null && !explicitDictId.trim().isEmpty()) {
			return explicitDictId.trim();
		}
		if (dmId == null || dmId.trim().isEmpty()) {
			return null;
		}
		try (SqlSession session = sqlSessionFactory.openSession()) {
			return session.selectOne("stddict.selectDictIdByModel", dmId);
		}
	}

	/**
	 * 이 사전에서 용어의 마지막 단어가 형식단어여야 하는가.
	 *
	 * <p>기본은 <b>미적용</b>. 사전에서 켜야 검증한다.
	 * 세트 도입 이전부터 있던 DEFAULT 사전만 'Y' 로 시드해 기존 동작을 유지한다.</p>
	 */
	public boolean isTermLastWordClsfRequired(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		return d != null && "Y".equalsIgnoreCase(String.valueOf(d.get("termLastWordClsfYn")));
	}

	/** 104번 WP6-13 — 표준 대문자: 영문약어 소문자를 대문자로 바꿔 받는가 (아니면 소문자를 거부). 기본 Y */
	public boolean isUpperAuto(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		return d == null || !"N".equalsIgnoreCase(String.valueOf(d.get("upperYn")));
	}

	/** 104번 WP5-14 — 참조 사전(읽기 전용)인가 */
	public boolean isRef(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		return d != null && "REF".equals(d.get("dictTp"));
	}

	/**
	 * 104번 WP5-14 — 참조 사전이면 고치지 못하게 막는다. 등록·수정·삭제·일괄등록 경로가 부른다.
	 *
	 * @throws IllegalArgumentException 참조 사전일 때 (메시지는 화면에 그대로 보인다)
	 */
	public void checkWritable(String dictId) {
		if (isRef(dictId)) {
			Map<String, Object> d = snapshot().get(dictId);
			throw new IllegalArgumentException("참조 사전은 고칠 수 없습니다 (" + d.get("dictNm") + "). 기관 사전으로 가져와(표준 사전 > 공통표준 가져오기) 쓰세요.");
		}
	}

	/** 104번 WP5-14 — 항목(단어·용어·도메인 등) 하나의 사전으로 checkWritable. 행이 없으면 넘어간다 */
	public void checkWritableOf(String table, String idCol, String id) {
		if (id == null) return;
		Map<String, Object> p = new HashMap<>();
		p.put("table", table);
		p.put("idCol", idCol);
		p.put("id", id);
		try (SqlSession session = sqlSessionFactory.openSession()) {
			String dictId = session.selectOne("stddict.selectDictIdOf", p);
			if (dictId != null) checkWritable(dictId);
		}
	}

	/**
	 * 104번 WP5-11 — 사전과 그 상위 사전들 (자기 자신이 먼저, 가까운 순). 5단계까지, 순환은 끊는다.
	 * dictId 가 null 이면 기본 사전부터.
	 */
	public java.util.List<String> lineage(String dictId) {
		java.util.List<String> out = new java.util.ArrayList<>();
		String cur = (dictId == null || dictId.trim().isEmpty()) ? defaultDictId() : dictId.trim();
		Map<String, Map<String, Object>> all = snapshot();
		while (cur != null && !out.contains(cur) && out.size() < 6) {
			out.add(cur);
			Map<String, Object> d = all.get(cur);
			cur = d == null ? null : (String) d.get("parentDictId");
		}
		return out;
	}

	/** 104번 WP5-11 — 사전과 상위 사전들을 함께 보는 쿼리 파라미터 (dictId = 자기, dictIds = 자기 + 상위, 가까운 순) */
	public Map<String, Object> lineageParam(String dictId) {
		java.util.List<String> ids = lineage(dictId);
		Map<String, Object> p = new HashMap<>();
		p.put("dictId", ids.get(0));
		p.put("dictIds", ids);
		return p;
	}

	/** 104번 WP6-13 — 단어 영문명 중복을 허용하는가. 기본 Y */
	public boolean isWordEngNmDupAllowed(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		return d == null || !"N".equalsIgnoreCase(String.valueOf(d.get("wordEngNmDupYn")));
	}

	/** 104번 WP6-13 — 용어 영문약어명 최대 길이(Byte). 제한이 없으면 null */
	public Integer termEngMaxLen(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		Object v = d == null ? null : d.get("termEngMaxLen");
		return v == null ? null : ((Number) v).intValue();
	}

	/** 104번 WP6-13·15 — 구표준(옛 표준 → 새 표준 매핑)을 쓰는가. 기본 N */
	public boolean isOldStdUsed(String dictId) {
		Map<String, Object> d = snapshot().get(dictId == null ? DEFAULT_DICT_ID : dictId);
		return d != null && "Y".equalsIgnoreCase(String.valueOf(d.get("oldStdYn")));
	}

	/**
	 * 104번 WP6-13 — 단어 영문약어를 사전 옵션대로 다듬는다. 표준 대문자가 켜져 있으면 대문자로 바꾸고,
	 * 꺼져 있으면 소문자가 있을 때 거부한다.
	 */
	public String checkWordEngAbrv(String dictId, String engAbrv) {
		if (engAbrv == null || engAbrv.equals(engAbrv.toUpperCase())) return engAbrv;
		if (!isUpperAuto(dictId)) {
			throw new IllegalArgumentException("단어 영문약어에 소문자를 쓸 수 없습니다 (표준 대문자 옵션이 꺼진 사전). (입력값: " + engAbrv + ")");
		}
		return engAbrv.toUpperCase();
	}

	/**
	 * 104번 WP6-13 — 단어 영문명 중복을 허용하지 않는 사전이면 같은 영문명(대소문자 무시)의 다른 단어가 있을 때 거부한다.
	 *
	 * @param wordId 수정할 때 자기 자신 (등록이면 새 ID)
	 */
	public void checkWordEngNmDup(String dictId, String wordEngNm, String wordId) {
		if (wordEngNm == null || isWordEngNmDupAllowed(dictId)) return;
		Map<String, Object> p = nameParam(dictId, "wordEngNm", wordEngNm.trim());
		p.put("wordId", wordId);
		try (SqlSession session = sqlSessionFactory.openSession()) {
			Integer n = session.selectOne("word.countWordEngNmDup", p);
			if (n != null && n > 0) {
				throw new IllegalArgumentException("이 사전은 단어 영문명 중복을 허용하지 않습니다. 이미 쓰는 영문명입니다: " + wordEngNm.trim());
			}
		}
	}

	/**
	 * 104번 WP6-13 — 용어 영문약어명을 사전 옵션대로 다듬고 검사한다.
	 * 표준 대문자가 켜져 있으면 대문자로 바꾸고, 꺼져 있으면 소문자가 있을 때 거부한다. 최대 길이(Byte)를 넘으면 거부한다.
	 *
	 * @return 다듬은 영문약어명
	 * @throws IllegalArgumentException 옵션에 어긋날 때 (메시지는 화면에 그대로 보인다)
	 */
	public String checkTermEngAbrv(String dictId, String engAbrv) {
		if (engAbrv == null) return null;
		String v = engAbrv;
		if (!v.equals(v.toUpperCase())) {
			if (!isUpperAuto(dictId)) {
				throw new IllegalArgumentException("용어 영문약어명에 소문자를 쓸 수 없습니다 (표준 대문자 옵션이 꺼진 사전). (입력값: " + engAbrv + ")");
			}
			v = v.toUpperCase();
		}
		Integer max = termEngMaxLen(dictId);
		int len = v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		if (max != null && len > max) {
			throw new IllegalArgumentException("용어 영문약어명이 이 사전의 최대 길이 " + max + " Byte 를 넘습니다 (" + len + " Byte: " + v + ")");
		}
		return v;
	}

	/**
	 * 사전 하나만 넘기는 쿼리 파라미터.
	 *
	 * <p>진단·자동표준화가 사전을 통째로 로드하는 쿼리(`selectAll*`)에 쓴다.
	 * dictId 가 null 이면 기본 사전으로 해석한다.</p>
	 */
	public Map<String, Object> dictParam(String dictId) {
		Map<String, Object> p = new HashMap<>();
		p.put("dictId", (dictId == null || dictId.trim().isEmpty()) ? defaultDictId() : dictId.trim());
		return p;
	}

	/**
	 * 이름으로 사전 항목을 찾는 쿼리의 파라미터.
	 *
	 * <p>사전을 나눈 뒤로 "영문약어가 BTWN 인 단어" 만으로는 답이 하나가 아니다.
	 * 어느 사전에서 찾는지를 반드시 같이 줘야 한다.</p>
	 *
	 * <p><b>dictId 가 null 이면 기본 사전으로 해석한다.</b> 화면이 아직 사전을
	 * 보내지 않는 단계(98번 3단계 이전)를 위한 것이다. 화면 작업이 끝나면
	 * 호출부가 요청의 dictId 를 넘기게 되고, 이 분기는 자연히 안 타게 된다.
	 * 조용히 전역을 뒤지는 것보다 "기본 사전을 봤다" 가 명시적이라 이렇게 둔다.</p>
	 */
	public Map<String, Object> nameParam(String dictId, String key, String value) {
		Map<String, Object> p = new HashMap<>();
		p.put("dictId", (dictId == null || dictId.trim().isEmpty()) ? defaultDictId() : dictId.trim());
		p.put(key, value);
		return p;
	}

	/** 이름 목록으로 찾는 쿼리의 파라미터 (IN 절). dictId 가 null 이면 기본 사전. */
	public Map<String, Object> listParam(String dictId, String key, java.util.List<?> values) {
		Map<String, Object> p = dictParam(dictId);
		p.put(key, values);
		return p;
	}

	/** 캐시 무효화. 사전 속성을 바꾼 뒤 호출한다. */
	public void invalidate() {
		loadedAt = 0L;
	}

	public void create(Map<String, Object> p) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			session.insert("stddict.insertDict", p);
			if ("Y".equals(p.get("defaultYn"))) {
				session.update("stddict.clearDefaultDict");
				session.update("stddict.setDefaultDict", p.get("dictId"));
			}
			session.commit();
		}
		invalidate();
		log.info(">> std dict created: {}", p.get("dictId"));
	}

	public void update(Map<String, Object> p) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			session.update("stddict.updateDict", p);
			if ("Y".equals(p.get("defaultYn"))) {
				session.update("stddict.clearDefaultDict");
				session.update("stddict.setDefaultDict", p.get("dictId"));
			}
			session.commit();
		}
		invalidate();
		log.info(">> std dict updated: {}", p.get("dictId"));
	}

	/**
	 * 사전 사용 중지.
	 *
	 * <p>물리 삭제하지 않는다. 사전이 사라지면 그 사전으로 잰 진단 이력을
	 * 해석할 수 없게 된다. 기본 사전은 중지할 수 없다.</p>
	 */
	public void disable(String dictId, String userId) {
		try (SqlSession session = sqlSessionFactory.openSession()) {
			Map<String, Object> p = new HashMap<>();
			p.put("dictId", dictId);
			p.put("userId", userId);
			session.update("stddict.disableDict", p);
			session.commit();
		}
		invalidate();
		log.info(">> std dict disabled: {} (by {})", dictId, userId);
	}

	private Map<String, Map<String, Object>> snapshot() {
		Map<String, Map<String, Object>> c = cache;
		if (c != null && System.currentTimeMillis() - loadedAt < TTL_MS) {
			return c;
		}
		Map<String, Map<String, Object>> loaded = new HashMap<>();
		try (SqlSession session = sqlSessionFactory.openSession()) {
			List<Map<String, Object>> rows = session.selectList("stddict.selectDictList");
			for (Map<String, Object> row : rows) {
				loaded.put((String) row.get("dictId"), row);
			}
		}
		cache = loaded;
		loadedAt = System.currentTimeMillis();
		return loaded;
	}
}
