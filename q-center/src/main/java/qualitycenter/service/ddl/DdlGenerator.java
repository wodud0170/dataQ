package qualitycenter.service.ddl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelObjVo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DdlGenerator {

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private DdlDialects dialects;

	@Autowired
	private DdlRuleService rules;

	/** 모델 전체 DDL (내려받기). dbType 이 규칙 없는 DBMS 면 Oracle 문법 */
	public String generate(String dataModelId, String dbType) {
		SqlDialect dialect = dialects.forDbms(dbType);
		if (dialect == null) dialect = new OracleDialect(rules);

		Map<String, Object> model = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dataModelId);
		String modelNm = model == null ? dataModelId : String.valueOf(model.get("dataModelNm"));

		// CLCT 폐기 이후 OBJ/ATTR 은 DM_ID 기반 — clctId 파라미터는 실제로 DM_ID가 들어감
		List<StdDataModelObjVo> objs = sqlSessionTemplate.selectList("datamodel.selectDataModelObjListByClctId", dataModelId);
		List<StdDataModelAttrVo> attrs = sqlSessionTemplate.selectList("datamodel.selectDataModelAttrListByClctId", dataModelId);
		List<Map<String, Object>> constraints = sqlSessionTemplate.selectList("datamodel.selectDataModelConstraintListByDmId", dataModelId);

		StringBuilder sb = new StringBuilder();
		appendHeader(sb, modelNm, dataModelId, dialect, objs.size(), attrs.size());

		if (objs.isEmpty()) {
			sb.append("-- 이 모델에는 테이블이 없습니다.\n");
			return sb.toString();
		}

		Map<String, List<StdDataModelAttrVo>> attrByObj = groupAttrsByObj(attrs);
		Map<String, List<Map<String, Object>>> consByObj = groupConstraintsByObj(constraints);

		for (StdDataModelObjVo obj : objs) {
			// 내려받기 파일은 소유자 접두어 없이 만든다 (받은 쪽 스키마에서 실행)
			for (String stmt : createTableStatements(null, obj, attrByObj.get(obj.getObjNm()), consByObj.get(obj.getObjNm()), dialect)) {
				sb.append(stmt).append(";\n");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * 104번 WP3 — [DB 반영] 의 신규 테이블. 그 테이블의 승인된 컬럼·제약조건으로 CREATE TABLE 과 주석 문장을 만든다.
	 * 소유자가 있으면 이름 앞에 붙인다. 모델에 그 테이블이 없으면 null.
	 */
	public List<String> createTableDdl(String dmId, String objOwner, String objNm, SqlDialect dialect) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		p.put("objOwner", objOwner == null ? "" : objOwner);
		p.put("objNm", objNm);
		StdDataModelObjVo obj = sqlSessionTemplate.selectOne("ddlRule.selectObj", p);
		if (obj == null) return null;
		List<StdDataModelAttrVo> attrs = sqlSessionTemplate.selectList("ddlRule.selectApprovedAttrsOfObj", p);
		List<Map<String, Object>> cons = sqlSessionTemplate.selectList("ddlRule.selectApprovedConstraintsOfObj", p);
		return createTableStatements(objOwner, obj, attrs, cons, dialect);
	}

	private void appendHeader(StringBuilder sb, String modelNm, String dmId, SqlDialect dialect, int objCnt, int attrCnt) {
		String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		sb.append("-- ====================================================\n");
		sb.append("-- 데이터 모델: ").append(modelNm).append(" (DM_ID: ").append(dmId).append(")\n");
		sb.append("-- 생성일시: ").append(now).append("\n");
		sb.append("-- DB 타입: ").append(dialect.dbmsTp()).append("\n");
		sb.append("-- 총 테이블: ").append(objCnt).append(", 총 컬럼: ").append(attrCnt).append("\n");
		sb.append("-- ====================================================\n\n");
	}

	private Map<String, List<StdDataModelAttrVo>> groupAttrsByObj(List<StdDataModelAttrVo> attrs) {
		Map<String, List<StdDataModelAttrVo>> m = new LinkedHashMap<>();
		for (StdDataModelAttrVo a : attrs) {
			m.computeIfAbsent(a.getObjNm(), k -> new ArrayList<>()).add(a);
		}
		return m;
	}

	private Map<String, List<Map<String, Object>>> groupConstraintsByObj(List<Map<String, Object>> cons) {
		Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
		if (cons == null) return m;
		for (Map<String, Object> c : cons) {
			String tbl = String.valueOf(c.get("tableNm"));
			m.computeIfAbsent(tbl, k -> new ArrayList<>()).add(c);
		}
		return m;
	}

	/** CREATE TABLE 1문장 + 주석 문장들 (끝 ';' 없음). owner 가 비면 접두어 없음 */
	private List<String> createTableStatements(String owner, StdDataModelObjVo obj, List<StdDataModelAttrVo> objAttrs,
			List<Map<String, Object>> objCons, SqlDialect dialect) {
		String tbl = obj.getObjNm();
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE ").append(dialect.qualify(owner, tbl)).append(" (\n");

		List<String> colLines = new ArrayList<>();
		List<StdDataModelAttrVo> pkAttrsFromFlag = new ArrayList<>();
		if (objAttrs != null) {
			for (StdDataModelAttrVo a : objAttrs) {
				StringBuilder line = new StringBuilder();
				line.append("    ").append(pad(a.getAttrNm(), 30)).append(" ");
				line.append(pad(dialect.columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen()), 16));
				if (a.getDefaultVal() != null && !a.getDefaultVal().trim().isEmpty()) {
					line.append(" DEFAULT ").append(a.getDefaultVal().trim());
				}
				if (!"Y".equalsIgnoreCase(a.getNullableYn())) {
					line.append(" NOT NULL");
				}
				String colComment = firstNonEmpty(a.getAttrComment(), a.getAttrNmKr());
				if (dialect.inlineComments() && colComment != null) line.append(dialect.inlineColumnComment(colComment));
				colLines.add(line.toString());
				if ("Y".equalsIgnoreCase(a.getPkYn())) {
					pkAttrsFromFlag.add(a);
				}
			}
		}

		String pkLine = buildPkLine(tbl, objCons, pkAttrsFromFlag);
		List<String> fkLines = buildFkLines(dialect, owner, objCons);
		List<String> ukLines = buildUkLines(objCons);

		List<String> allLines = new ArrayList<>(colLines);
		if (pkLine != null) allLines.add(pkLine);
		allLines.addAll(ukLines);
		allLines.addAll(fkLines);

		for (int i = 0; i < allLines.size(); i++) {
			sb.append(allLines.get(i));
			if (i < allLines.size() - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append(")");
		// 88번 §15 — TABLESPACE 절 (NULL 이면 생략)
		if (obj.getTablespaceNm() != null && !obj.getTablespaceNm().trim().isEmpty()) {
			sb.append(" TABLESPACE ").append(obj.getTablespaceNm().trim());
		}
		String tblComment = firstNonEmpty(obj.getObjComment(), obj.getObjNmKr(), obj.getObjDesc());
		if (dialect.inlineComments() && tblComment != null) sb.append(dialect.inlineTableComment(tblComment));

		List<String> out = new ArrayList<>();
		out.add(sb.toString());
		if (!dialect.inlineComments()) {
			if (tblComment != null) out.add(dialect.commentOnTable(owner, tbl, tblComment));
			if (objAttrs != null) {
				for (StdDataModelAttrVo a : objAttrs) {
					String colComment = firstNonEmpty(a.getAttrComment(), a.getAttrNmKr());
					if (colComment != null) out.add(dialect.commentOnColumn(owner, tbl, a.getAttrNm(), colComment));
				}
			}
		}
		return out;
	}

	private String buildPkLine(String tbl, List<Map<String, Object>> cons, List<StdDataModelAttrVo> pkAttrsFromFlag) {
		// 1) TB_DATA_MODEL_CONSTRAINT 의 P 타입 우선 사용
		if (cons != null) {
			Map<String, List<Map<String, Object>>> byName = new LinkedHashMap<>();
			for (Map<String, Object> c : cons) {
				String type = String.valueOf(c.get("constraintType"));
				if (!"P".equalsIgnoreCase(type)) continue;
				String nm = String.valueOf(c.get("constraintNm"));
				byName.computeIfAbsent(nm, k -> new ArrayList<>()).add(c);
			}
			if (!byName.isEmpty()) {
				Map.Entry<String, List<Map<String, Object>>> first = byName.entrySet().iterator().next();
				List<String> cols = new ArrayList<>();
				for (Map<String, Object> c : first.getValue()) cols.add(String.valueOf(c.get("columnNm")));
				return "    CONSTRAINT " + first.getKey() + " PRIMARY KEY (" + String.join(", ", cols) + ")";
			}
		}
		// 2) 제약 없고 컬럼 PK_YN='Y' 있으면 PK 이름 규칙(DDL 규칙)으로 생성
		if (!pkAttrsFromFlag.isEmpty()) {
			List<String> cols = new ArrayList<>();
			for (StdDataModelAttrVo a : pkAttrsFromFlag) cols.add(a.getAttrNm());
			return "    CONSTRAINT " + rules.pkName(tbl) + " PRIMARY KEY (" + String.join(", ", cols) + ")";
		}
		return null;
	}

	private List<String> buildUkLines(List<Map<String, Object>> cons) {
		List<String> out = new ArrayList<>();
		if (cons == null) return out;
		Map<String, List<Map<String, Object>>> byName = new LinkedHashMap<>();
		for (Map<String, Object> c : cons) {
			String type = String.valueOf(c.get("constraintType"));
			if (!"U".equalsIgnoreCase(type)) continue;
			String nm = String.valueOf(c.get("constraintNm"));
			byName.computeIfAbsent(nm, k -> new ArrayList<>()).add(c);
		}
		for (Map.Entry<String, List<Map<String, Object>>> e : byName.entrySet()) {
			List<String> cols = new ArrayList<>();
			for (Map<String, Object> c : e.getValue()) cols.add(String.valueOf(c.get("columnNm")));
			out.add("    CONSTRAINT " + e.getKey() + " UNIQUE (" + String.join(", ", cols) + ")");
		}
		return out;
	}

	/** owner 가 있으면(DB 반영) 참조 테이블에도 소유자를 붙인다 — 참조 소유자가 없으면 같은 소유자 */
	private List<String> buildFkLines(SqlDialect dialect, String owner, List<Map<String, Object>> cons) {
		List<String> out = new ArrayList<>();
		if (cons == null) return out;
		Map<String, List<Map<String, Object>>> byName = new LinkedHashMap<>();
		for (Map<String, Object> c : cons) {
			String type = String.valueOf(c.get("constraintType"));
			if (!"R".equalsIgnoreCase(type) && !"FK".equalsIgnoreCase(type)) continue;
			String nm = String.valueOf(c.get("constraintNm"));
			byName.computeIfAbsent(nm, k -> new ArrayList<>()).add(c);
		}
		for (Map.Entry<String, List<Map<String, Object>>> e : byName.entrySet()) {
			List<Map<String, Object>> rows = e.getValue();
			List<String> cols = new ArrayList<>();
			List<String> refCols = new ArrayList<>();
			String refTbl = null;
			String refOwner = null;
			for (Map<String, Object> c : rows) {
				cols.add(String.valueOf(c.get("columnNm")));
				refCols.add(String.valueOf(c.get("refColumnNm")));
				if (refTbl == null) refTbl = String.valueOf(c.get("refTableNm"));
				if (refOwner == null && c.get("refOwner") != null) refOwner = String.valueOf(c.get("refOwner"));
			}
			String line = "    CONSTRAINT " + e.getKey()
					+ " FOREIGN KEY (" + String.join(", ", cols) + ")"
					+ " REFERENCES " + ((owner == null || owner.isEmpty()) ? refTbl
							: dialect.qualify(refOwner != null && !refOwner.isEmpty() ? refOwner : owner, refTbl))
					+ " (" + String.join(", ", refCols) + ")";
			Map<String, Object> first = rows.get(0);
			Object delRule = first.get("deleteRule");
			if (delRule != null && !String.valueOf(delRule).trim().isEmpty()
					&& !"NO ACTION".equalsIgnoreCase(String.valueOf(delRule))) {
				line += " ON DELETE " + delRule;
			}
			out.add(line);
		}
		return out;
	}

	private String firstNonEmpty(String... vs) {
		for (String v : vs) {
			if (v != null && !v.trim().isEmpty()) return v.trim();
		}
		return null;
	}

	private String pad(String s, int w) {
		if (s == null) s = "";
		if (s.length() >= w) return s;
		StringBuilder b = new StringBuilder(s);
		while (b.length() < w) b.append(' ');
		return b.toString();
	}
}
