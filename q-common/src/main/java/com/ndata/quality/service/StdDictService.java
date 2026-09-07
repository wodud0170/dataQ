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
