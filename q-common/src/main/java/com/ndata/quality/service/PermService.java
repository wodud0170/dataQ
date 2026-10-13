package com.ndata.quality.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.common.PermCode;

/**
 * 104번 WP2 — 권한 판정.
 *
 * <p>관리자(TB_USER.ADM_YN)는 모든 권한을 갖는다. 관리자가 아니면 받은 역할의 권한(TB_USER_ROLE →
 * TB_ROLE_PERM)만 갖는다. 시스템 단위 권한({@link PermCode#systemScoped})은 모든 시스템('*')에
 * 받았거나 그 시스템·상위 시스템에 받았을 때 통한다.</p>
 *
 * <p>q-center(화면 요청)와 q-executor(엑셀 일괄등록 승인값) 양쪽에서 쓰므로 q-common 에 있다.
 * 요청마다 조회하지 않도록 사용자별로 {@value #TTL_MS}ms 캐시한다. 역할을 바꾸면 q-center 는
 * 즉시 무효화하고, q-executor 는 TTL 뒤 따라온다.</p>
 */
@Service
public class PermService {

	public static final String ALL_SYSTEMS = "*";

	private static final long TTL_MS = 10_000L;

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	private static final class Entry {
		final boolean admin;
		/** 권한 코드 → 받은 시스템 범위 목록 ('*' 포함) */
		final Map<String, List<String>> perms;
		final long loadedAt;

		Entry(boolean admin, Map<String, List<String>> perms) {
			this.admin = admin;
			this.perms = perms;
			this.loadedAt = System.currentTimeMillis();
		}
	}

	private final Map<String, Entry> cache = new ConcurrentHashMap<>();

	/** 시스템 범위와 상관없이 그 권한이 있는지. 시스템 단위 권한이면 어느 시스템이든 하나라도 있으면 true. */
	public boolean has(String userId, PermCode code) {
		if (userId == null) return false;
		Entry e = load(userId);
		return e.admin || e.perms.containsKey(code.name());
	}

	/**
	 * 그 시스템에 대해 권한이 있는지.
	 * sysCd 가 비면 모든 시스템('*')으로 받은 경우만 통한다.
	 */
	public boolean has(String userId, PermCode code, String sysCd) {
		if (userId == null) return false;
		Entry e = load(userId);
		if (e.admin) return true;
		List<String> scopes = e.perms.get(code.name());
		if (scopes == null) return false;
		if (scopes.contains(ALL_SYSTEMS)) return true;
		if (sysCd == null || sysCd.trim().isEmpty()) return false;
		try (SqlSession session = sqlSessionFactory.openSession()) {
			List<String> chain = session.selectList("role.selectSysWithAncestors", sysCd);
			for (String s : chain) {
				if (scopes.contains(s)) return true;
			}
		}
		return false;
	}

	/** 관리자 여부 (TB_USER.ADM_YN). */
	public boolean isAdmin(String userId) {
		return userId != null && load(userId).admin;
	}

	/** 화면용 — 권한 코드 → 시스템 범위 목록. 관리자면 모든 코드에 '*'. */
	public Map<String, List<String>> perms(String userId) {
		Map<String, List<String>> out = new HashMap<>();
		if (userId == null) return out;
		Entry e = load(userId);
		if (e.admin) {
			for (PermCode p : PermCode.values()) {
				out.put(p.name(), Collections.singletonList(ALL_SYSTEMS));
			}
			return out;
		}
		out.putAll(e.perms);
		return out;
	}

	/** 역할·사용자 역할을 바꾼 뒤 부른다. */
	public void invalidate() {
		cache.clear();
	}

	private Entry load(String userId) {
		Entry e = cache.get(userId);
		if (e != null && System.currentTimeMillis() - e.loadedAt < TTL_MS) {
			return e;
		}
		try (SqlSession session = sqlSessionFactory.openSession()) {
			Boolean admYn = session.selectOne("sysinfo.selectIsAdmin", userId);
			Map<String, List<String>> perms = new HashMap<>();
			List<Map<String, Object>> rows = session.selectList("role.selectUserPerms", userId);
			for (Map<String, Object> r : rows) {
				String code = (String) r.get("permCd");
				String sys = (String) r.get("sysCd");
				perms.computeIfAbsent(code, k -> new ArrayList<>()).add(sys == null ? ALL_SYSTEMS : sys);
			}
			e = new Entry(Boolean.TRUE.equals(admYn), perms);
		}
		cache.put(userId, e);
		return e;
	}
}
