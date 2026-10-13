package qualitycenter.service.auth;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.module.StringUtils;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.PermService;

/**
 * 104번 WP2 — 역할 관리 (TB_ROLE · TB_ROLE_PERM · TB_USER_ROLE).
 *
 * <p>역할은 권한 코드({@link PermCode})의 묶음이다. 내장 역할(BUILTIN_YN='Y')은 이름·권한을 고칠 수 있지만
 * 지울 수는 없다. 역할·사용자 역할을 바꾸면 권한 캐시를 바로 비운다.</p>
 */
@Service
public class RoleService {

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	@Autowired
	private PermService permService;

	@Autowired
	private SessionService sessionService;

	/** 역할 목록 + 각 역할의 권한 코드 */
	public List<Map<String, Object>> list() {
		List<Map<String, Object>> roles = sqlSessionTemplate.selectList("role.selectRoleList");
		for (Map<String, Object> r : roles) {
			r.put("perms", sqlSessionTemplate.selectList("role.selectRolePerms", r.get("roleId")));
		}
		return roles;
	}

	/**
	 * 역할 저장 — roleId 가 없으면 새로 만든다. 권한 목록은 통째로 바꾼다.
	 * @return 저장한 역할 ID
	 */
	public String save(String roleId, String roleNm, String roleDesc, List<String> perms) {
		if (roleNm == null || roleNm.trim().isEmpty()) {
			throw new IllegalArgumentException("역할 이름은 필수입니다.");
		}
		roleNm = roleNm.trim();
		if (roleNm.length() > 100) {
			throw new IllegalArgumentException("역할 이름이 너무 깁니다 (최대 100자).");
		}
		Set<String> permSet = new LinkedHashSet<>();
		if (perms != null) {
			for (String p : perms) {
				if (!PermCode.isValid(p)) throw new IllegalArgumentException("없는 권한 코드입니다: " + p);
				permSet.add(p);
			}
		}
		Map<String, Object> dup = new HashMap<>();
		dup.put("roleNm", roleNm);
		dup.put("roleId", (roleId == null || roleId.isEmpty()) ? null : roleId);
		int same = sqlSessionTemplate.selectOne("role.countRoleByNm", dup);
		if (same > 0) throw new IllegalArgumentException("같은 이름의 역할이 이미 있습니다: " + roleNm);

		String now = LocalDateTime.now().format(DT_FMT);
		Map<String, Object> p = new HashMap<>();
		p.put("roleNm", roleNm);
		p.put("roleDesc", roleDesc);
		p.put("now", now);
		p.put("userId", sessionService.getUserId());

		try (SqlSession session = sqlSessionFactory.openSession(false)) {
			if (roleId == null || roleId.isEmpty()) {
				roleId = StringUtils.getUUID();
				p.put("roleId", roleId);
				session.insert("role.insertRole", p);
			} else {
				if (session.selectOne("role.selectRole", roleId) == null) {
					throw new IllegalArgumentException("없는 역할입니다: " + roleId);
				}
				p.put("roleId", roleId);
				session.update("role.updateRole", p);
			}
			session.delete("role.deleteRolePerms", roleId);
			for (String code : permSet) {
				Map<String, Object> rp = new HashMap<>();
				rp.put("roleId", roleId);
				rp.put("permCd", code);
				session.insert("role.insertRolePerm", rp);
			}
			session.commit();
		}
		permService.invalidate();
		return roleId;
	}

	/** 내장 역할은 지우지 않는다. 역할을 지우면 그 역할의 권한·사용자 지정도 함께 지워진다(FK CASCADE). */
	public void delete(String roleId) {
		Map<String, Object> role = sqlSessionTemplate.selectOne("role.selectRole", roleId);
		if (role == null) throw new IllegalArgumentException("없는 역할입니다: " + roleId);
		if ("Y".equals(role.get("builtinYn"))) throw new IllegalArgumentException("내장 역할은 삭제할 수 없습니다.");
		sqlSessionTemplate.delete("role.deleteRole", roleId);
		permService.invalidate();
	}

	public List<Map<String, Object>> roleUsers(String roleId) {
		return sqlSessionTemplate.selectList("role.selectRoleUsers", roleId);
	}

	public List<Map<String, Object>> userRoles(String userId) {
		if (userId == null) return new ArrayList<>();
		return sqlSessionTemplate.selectList("role.selectUserRoles", userId);
	}

	/** 사용자에게 역할을 준다. sysCd 가 비면 모든 시스템('*'). */
	public void assign(String userId, String roleId, String sysCd) {
		if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("사용자는 필수입니다.");
		if (roleId == null || sqlSessionTemplate.selectOne("role.selectRole", roleId) == null) {
			throw new IllegalArgumentException("없는 역할입니다: " + roleId);
		}
		if (sessionService.getUserDetail(userId) == null) throw new IllegalArgumentException("없는 사용자입니다: " + userId);
		String scope = (sysCd == null || sysCd.trim().isEmpty()) ? PermService.ALL_SYSTEMS : sysCd.trim();
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("roleId", roleId);
		p.put("sysCd", scope);
		p.put("now", LocalDateTime.now().format(DT_FMT));
		p.put("cretUserId", sessionService.getUserId());
		sqlSessionTemplate.insert("role.insertUserRole", p);
		permService.invalidate();
	}

	public void unassign(String userId, String roleId, String sysCd) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("roleId", roleId);
		p.put("sysCd", (sysCd == null || sysCd.trim().isEmpty()) ? PermService.ALL_SYSTEMS : sysCd.trim());
		sqlSessionTemplate.delete("role.deleteUserRole", p);
		permService.invalidate();
	}
}
