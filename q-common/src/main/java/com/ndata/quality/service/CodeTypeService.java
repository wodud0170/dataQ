package com.ndata.quality.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.StdCodeDataVo;
import com.ndata.quality.model.std.StdTermsVo;

/**
 * 104번 WP6-3 — 코드 유형 (SMETA 교육자료 p.44 의 다섯 가지).
 *
 * <ul>
 *   <li>SINGLE 단일 — 코드값을 직접 관리</li>
 *   <li>HIER 계층 — 상위 코드(단일·계층)를 정하고, 값마다 상위 코드값을 둔다. 계층을 이어 3단계 이상도 된다</li>
 *   <li>DERIV 파생 — 상위 코드를 참조해 그 값 중 일부(또는 전부)만 쓴다</li>
 *   <li>INTEG 통합 — 하위 코드 2개 이상을 묶는다. 값은 하위 코드의 값을 모아 보여 주고 직접 넣지 않는다</li>
 *   <li>INDIV 개별 — 코드 테이블이 따로 있어 메타에서 값을 관리하지 않는다</li>
 * </ul>
 *
 * <p>코드 = CODE_GRP 가 있는 용어, 코드값 = 같은 사전·같은 CODE_GRP 의 TB_CODE_DATA. q-center(화면)와
 * q-executor(엑셀 일괄등록)가 함께 쓴다. 검사에 어긋나면 화면에 그대로 보일 문구로 {@link IllegalArgumentException} 을 던진다.</p>
 */
@Service
public class CodeTypeService {

	public static final List<String> CODE_TPS = Arrays.asList("SINGLE", "HIER", "DERIV", "INTEG", "INDIV");

	@Autowired
	private StdDictService dictService;

	public static String tpNm(String tp) {
		switch (tp == null ? "SINGLE" : tp) {
			case "HIER": return "계층";
			case "DERIV": return "파생";
			case "INTEG": return "통합";
			case "INDIV": return "개별";
			default: return "단일";
		}
	}

	private Map<String, Object> grpParam(String dictId, String codeGrp) {
		return dictService.nameParam(dictId, "codeGrp", codeGrp);
	}

	/** 코드 그룹으로 코드 한 건 (없으면 null) — termsId·termsNm·codeGrp·codeTp·parentCodeGrp */
	public Map<String, Object> codeByGrp(SqlSession session, String dictId, String codeGrp) {
		if (codeGrp == null || codeGrp.trim().isEmpty()) return null;
		return session.selectOne("codeType.selectCodeByGrp", grpParam(dictId, codeGrp.trim()));
	}

	/**
	 * 코드(용어) 등록·수정 때 코드 유형을 검사하고 통합 구성을 저장한다. 같은 세션(트랜잭션) 안에서 부른다.
	 *
	 * @param isNew 등록이면 true. 수정인데 코드 유형을 보내지 않으면(용어 화면 수정) 그대로 둔다
	 */
	public void checkAndSave(SqlSession session, StdTermsVo vo, boolean isNew, String userId) {
		String grp = vo.getCodeGrp() == null ? null : vo.getCodeGrp().trim();
		if (grp == null || grp.isEmpty()) {
			vo.setCodeTp(null);
			vo.setParentCodeGrp(null);
			return;
		}
		if (!isNew && vo.getCodeTp() == null) return;
		String tp = vo.getCodeTp() == null || vo.getCodeTp().isEmpty() ? "SINGLE" : vo.getCodeTp();
		if (!CODE_TPS.contains(tp)) throw new IllegalArgumentException("코드 유형이 올바르지 않습니다: " + tp);
		vo.setCodeTp(tp);
		String dictId = vo.getDictId();

		if (!isNew && ("INTEG".equals(tp) || "INDIV".equals(tp))) {
			Integer n = session.selectOne("codeType.countValuesByGrp", grpParam(dictId, grp));
			if (n != null && n > 0) {
				throw new IllegalArgumentException("코드값이 " + n + "건 있는 코드는 " + tpNm(tp) + " 코드로 바꿀 수 없습니다. 값을 먼저 지우세요.");
			}
		}

		if ("HIER".equals(tp) || "DERIV".equals(tp)) {
			String parent = vo.getParentCodeGrp() == null ? "" : vo.getParentCodeGrp().trim();
			if (parent.isEmpty()) throw new IllegalArgumentException(tpNm(tp) + " 코드는 상위 코드를 골라야 합니다.");
			if (parent.equals(grp)) throw new IllegalArgumentException("자기 자신을 상위 코드로 지정할 수 없습니다.");
			Map<String, Object> p = codeByGrp(session, dictId, parent);
			if (p == null) throw new IllegalArgumentException("상위 코드 그룹 '" + parent + "' 의 코드가 이 사전에 없습니다.");
			String ptp = String.valueOf(p.get("codeTp"));
			if ("HIER".equals(tp) && !("SINGLE".equals(ptp) || "HIER".equals(ptp))) {
				throw new IllegalArgumentException("계층 코드의 상위는 단일 또는 계층 코드여야 합니다 (지금: " + tpNm(ptp) + ").");
			}
			if ("DERIV".equals(tp) && !("SINGLE".equals(ptp) || "HIER".equals(ptp) || "DERIV".equals(ptp))) {
				throw new IllegalArgumentException("파생 코드의 상위는 값을 가진 코드(단일·계층·파생)여야 합니다 (지금: " + tpNm(ptp) + ").");
			}
			// 위로 올라가며 순환을 막는다
			Set<String> seen = new LinkedHashSet<>();
			seen.add(grp);
			String cur = parent;
			for (int i = 0; cur != null && i < 20; i++) {
				if (!seen.add(cur)) throw new IllegalArgumentException("상위 코드가 돌고 돕니다: " + String.join(" → ", seen) + " → " + cur);
				Map<String, Object> c = codeByGrp(session, dictId, cur);
				cur = c == null ? null : (String) c.get("parentCodeGrp");
			}
			vo.setParentCodeGrp(parent);
		} else {
			vo.setParentCodeGrp(null);
		}

		session.delete("codeType.deleteInteg", grpParam(dictId, grp));
		if ("INTEG".equals(tp)) {
			Set<String> subs = new LinkedHashSet<>();
			if (vo.getIntegSubGrps() != null) {
				for (String s : vo.getIntegSubGrps()) if (s != null && !s.trim().isEmpty()) subs.add(s.trim());
			}
			if (subs.size() < 2) throw new IllegalArgumentException("통합 코드는 하위 코드를 2개 이상 골라야 합니다.");
			for (String s : subs) {
				if (s.equals(grp)) throw new IllegalArgumentException("자기 자신을 하위 코드로 넣을 수 없습니다.");
				Map<String, Object> c = codeByGrp(session, dictId, s);
				if (c == null) throw new IllegalArgumentException("하위 코드 그룹 '" + s + "' 의 코드가 이 사전에 없습니다.");
				String ctp = String.valueOf(c.get("codeTp"));
				if ("INTEG".equals(ctp) || "INDIV".equals(ctp)) {
					throw new IllegalArgumentException("통합 코드의 하위는 값을 가진 코드여야 합니다 ('" + c.get("termsNm") + "' 는 " + tpNm(ctp) + " 코드).");
				}
			}
			Map<String, Object> p = grpParam(dictId, grp);
			p.put("subs", new ArrayList<>(subs));
			p.put("userId", userId);
			session.insert("codeType.insertInteg", p);
		}
	}

	/**
	 * 코드값 등록·수정 검사. 값의 코드 그룹에 맞는 코드가 없으면 유형 검사 없이 지금처럼 받는다.
	 * 계층은 상위 코드값이 상위 코드 그룹 값에 있어야 하고, 파생은 값이 상위 코드 그룹 값 중 하나여야 한다.
	 * 통합·개별 코드에는 값을 넣지 않는다.
	 */
	public void checkValue(SqlSession session, StdCodeDataVo vo) {
		Map<String, Object> code = codeByGrp(session, vo.getDictId(), vo.getCodeGrp());
		if (code == null) return;
		String tp = String.valueOf(code.get("codeTp"));
		String nm = String.valueOf(code.get("termsNm"));
		String parent = (String) code.get("parentCodeGrp");
		switch (tp) {
			case "INTEG": {
				List<String> subs = session.selectList("codeType.selectIntegSubs", grpParam(vo.getDictId(), vo.getCodeGrp()));
				throw new IllegalArgumentException("통합 코드 '" + nm + "' 는 값을 직접 넣지 않습니다. 하위 코드(" + String.join(", ", subs) + ")의 값을 씁니다.");
			}
			case "INDIV":
				throw new IllegalArgumentException("개별 코드 '" + nm + "' 는 값을 관리하지 않습니다 (코드 테이블이 따로 있습니다).");
			case "HIER": {
				String pv = vo.getParentCodeVal() == null ? "" : vo.getParentCodeVal().trim();
				if (pv.isEmpty()) throw new IllegalArgumentException("계층 코드 '" + nm + "' 의 값은 상위 코드값이 필요합니다 (상위 코드 그룹 " + parent + ").");
				if (!valueExists(session, vo.getDictId(), parent, pv)) {
					throw new IllegalArgumentException("상위 코드값 '" + pv + "' 가 상위 코드 그룹 " + parent + " 에 없습니다.");
				}
				vo.setParentCodeVal(pv);
				break;
			}
			case "DERIV":
				if (!valueExists(session, vo.getDictId(), parent, vo.getCodeVal())) {
					throw new IllegalArgumentException("파생 코드 '" + nm + "' 의 값은 상위 코드 그룹 " + parent + " 의 값 중에서만 쓸 수 있습니다 ('" + vo.getCodeVal() + "' 없음).");
				}
				vo.setParentCodeVal(null);
				break;
			default:
				vo.setParentCodeVal(null);
		}
	}

	/** 값을 지우기 전 — 계층 하위 값이 상위로 쓰고 있으면 거부 */
	public void checkValueDeletable(SqlSession session, StdCodeDataVo vo) {
		Map<String, Object> p = grpParam(vo.getDictId(), vo.getCodeGrp());
		p.put("codeVal", vo.getCodeVal());
		Integer n = session.selectOne("codeType.countChildValues", p);
		if (n != null && n > 0) {
			throw new IllegalArgumentException("코드값 '" + vo.getCodeVal() + "' 를 상위로 쓰는 계층 코드값이 " + n + "건 있어 지울 수 없습니다.");
		}
	}

	private boolean valueExists(SqlSession session, String dictId, String grp, String val) {
		if (grp == null || val == null) return false;
		Map<String, Object> p = grpParam(dictId, grp);
		p.put("codeVal", val);
		Integer n = session.selectOne("codeType.countValue", p);
		return n != null && n > 0;
	}

	/**
	 * 화면용 코드값 목록. 통합 코드는 하위 코드 값을 모아(값마다 나온 하위 코드 그룹을 붙여) 돌려주고,
	 * 계층 코드는 값마다 상위 코드값이 있다.
	 */
	public Map<String, Object> values(SqlSession session, String dictId, String codeGrp) {
		Map<String, Object> out = new HashMap<>();
		Map<String, Object> code = codeByGrp(session, dictId, codeGrp);
		String tp = code == null ? "SINGLE" : String.valueOf(code.get("codeTp"));
		out.put("code", code);
		List<StdCodeDataVo> vals = new ArrayList<>();
		if ("INTEG".equals(tp)) {
			List<String> subs = session.selectList("codeType.selectIntegSubs", grpParam(dictId, codeGrp));
			out.put("integSubGrps", subs);
			for (String s : subs) {
				for (StdCodeDataVo v : session.<StdCodeDataVo>selectList("codeType.selectValuesByGrp", grpParam(dictId, s))) {
					v.setSrcCodeGrp(s);
					vals.add(v);
				}
			}
		} else if (!"INDIV".equals(tp)) {
			vals = session.selectList("codeType.selectValuesByGrp", grpParam(dictId, codeGrp));
		}
		out.put("values", vals);
		return out;
	}
}
