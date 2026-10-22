package qualitycenter.service.governance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelChangeHistoryVo;
import com.ndata.quality.model.std.StdDataModelObjVo;

import qualitycenter.service.auth.SessionService;
import qualitycenter.service.ddl.DdlDialects;
import qualitycenter.service.ddl.DdlRuleService;
import qualitycenter.service.ddl.DdlSafety;
import qualitycenter.service.ddl.OracleDialect;
import qualitycenter.service.ddl.SqlDialect;

/**
 * 88번 거버넌스 — 데이터 모델 변경 이력 INSERT 헬퍼.
 *
 * 사용 패턴:
 *   - 변경 endpoint (saveAttrs / addAttr / deleteAttr / updateObj / addObj / deleteObj /
 *     createPk / deletePk / createFk / deleteFk) 가 변경 직후 호출
 *   - 단일 트랜잭션 유지 시 session 인자로 SqlSession 전달
 *   - 트랜잭션 외부에선 sqlSessionTemplate 자동 사용
 *
 * Tier 정책 (88_거버넌스_승인워크플로우_설계 §2):
 *   - TIER1   : schema 영향 변경. 사용자=DRAFT/SUBMITTED, 관리자=APPROVED 즉시
 *   - TIER1_5 : 사용자 UI 차단, 관리자만 가능 (컬럼 순서). 관리자=APPROVED
 *   - TIER2   : 메타 변경. 항상 APPROVED 즉시
 *
 * 104번 WP3 — DDL 조각은 모델 데이터소스의 DBMS 문법(SqlDialect)으로 만든다. 데이터소스가 없는 모델은
 * DDL 내려받기와 같이 Oracle 문법, DDL 규칙이 없는 DBMS 는 "-- … 규칙이 없습니다" 주석 조각([DB 반영] 은 SKIPPED).
 * 이력의 DDL_DBMS_TP 에 조각의 DBMS 를 남긴다 (데이터소스 없는 모델은 NULL).
 */
@Service
public class ChangeHistoryService {

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private DdlDialects dialects;

	@Autowired
	private DdlRuleService ddlRules;

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/**
	 * 변경 이력 INSERT. tier / aprvStatus 는 호출자가 결정.
	 * session 이 null 이면 새 트랜잭션, 아니면 그 session 트랜잭션 안에 INSERT.
	 */
	public void record(SqlSession session,
	                   String dmId, String changeType, String tier,
	                   String objOwner, String objNm, String attrNm,
	                   String constraintNm, String indexNm,
	                   String beforeJson, String afterJson, String ddlSnippet,
	                   String aprvStatus) {
		record(session, dmId, changeType, tier, objOwner, objNm, attrNm, constraintNm, indexNm,
				beforeJson, afterJson, ddlSnippet, aprvStatus, null);
	}

	/** 104번 WP7-25 — 표준 예외 신청 사유를 함께 남긴다 (표준 준수 필수 모델의 비표준 컬럼) */
	public void record(SqlSession session,
	                   String dmId, String changeType, String tier,
	                   String objOwner, String objNm, String attrNm,
	                   String constraintNm, String indexNm,
	                   String beforeJson, String afterJson, String ddlSnippet,
	                   String aprvStatus, String stndExceptReason) {
		StdDataModelChangeHistoryVo vo = new StdDataModelChangeHistoryVo();
		vo.setStndExceptReason(stndExceptReason);
		vo.setDmId(dmId);
		vo.setChangeDt(LocalDateTime.now().format(DT_FMT));
		vo.setChangeUserId(safeUserId());
		vo.setChangeType(changeType);
		vo.setChangeTier(tier);
		vo.setObjOwner(objOwner);
		vo.setObjNm(objNm);
		vo.setAttrNm(attrNm);
		vo.setConstraintNm(constraintNm);
		vo.setIndexNm(indexNm);
		vo.setBeforeJson(beforeJson);
		vo.setAfterJson(afterJson);
		vo.setDdlSnippet(ddlSnippet);
		vo.setDdlDbmsTp(ddlSnippet == null ? null : modelDbms(dmId));
		vo.setAprvStatus(aprvStatus == null ? "APPROVED" : aprvStatus);
		if ("APPROVED".equals(vo.getAprvStatus())) {
			vo.setAprvUserId(safeUserId());
			vo.setAprvDt(vo.getChangeDt());
		}
		if (session != null) session.insert("dmChangeHistory.insert", vo);
		else sqlSessionTemplate.insert("dmChangeHistory.insert", vo);
	}

	/** 사용자 변경 시 사용할 aprvStatus 결정 (관리자·모델 직접 반영 권한 DM_MANAGE=APPROVED, 그 밖=DRAFT) */
	public String resolveAprvStatusForUserChange() {
		return sessionService.hasPerm(PermCode.DM_MANAGE) ? "APPROVED" : "DRAFT";
	}

	/** 현재 사용자 ID. 시스템 호출 시 'system' fallback */
	public String safeUserId() {
		String uid = sessionService.getUserId();
		return (uid == null || uid.isEmpty()) ? "system" : uid;
	}

	public String currentDt() {
		return LocalDateTime.now().format(DT_FMT);
	}

	// ---- 104번 WP3 — DBMS 별 DDL 조각

	/** 모델 데이터소스의 DBMS_TP. 데이터소스가 없으면 null */
	private String modelDbms(String dmId) {
		Map<String, Object> ds = dialects.modelDs(dmId);
		return ds == null ? null : (String) ds.get("dbmsTp");
	}

	/** 조각을 만들 dialect. 데이터소스 없으면 Oracle, 규칙 없는 DBMS 면 null */
	private SqlDialect dialectOf(String dbms) {
		return dbms == null ? new OracleDialect(ddlRules) : dialects.forDbms(dbms);
	}

	private static String noRule(String dbms) {
		return "-- " + dbms + " 용 DDL 규칙이 없습니다";
	}

	/** 109번 #3 — 이름·타입·기본값 검사에 걸려 문장을 만들 수 없는 변경. 주석 조각이라 [DB 반영] 은 SKIPPED */
	private static String cannot(DdlSafety.Invalid e) {
		return "-- DDL 을 만들 수 없습니다: " + DdlSafety.oneLine(e.getMessage());
	}

	/** 문장들을 ';' 로 잇는다 */
	private static String join(List<String> stmts) {
		return stmts == null || stmts.isEmpty() ? null : String.join(";\n", stmts) + ";";
	}

	/**
	 * 88번 §8 — 테이블 추가·삭제, 컬럼 추가·삭제의 조각. NULL 반환 = 조각 없음.
	 * 테이블 추가는 표시(`(...)`)만 남기고, [DB 반영] 때 승인된 컬럼으로 CREATE TABLE 을 만든다.
	 */
	public String generateDdlSnippet(String dmId, String changeType, String objOwner, String objNm, String attrNm,
	                                  StdDataModelAttrVo attrVo, StdDataModelObjVo objVo) {
		if (changeType == null) return null;
		String dbms = modelDbms(dmId);
		SqlDialect d = dialectOf(dbms);
		if (d == null) return noRule(dbms);
		try {
			switch (changeType) {
				case "ADD_OBJ":
					StringBuilder sb = new StringBuilder("CREATE TABLE ").append(d.qualify(objOwner, objNm)).append(" (...)");
					if (objVo != null && objVo.getTablespaceNm() != null && !objVo.getTablespaceNm().trim().isEmpty())
						sb.append(" TABLESPACE ").append(d.id("테이블스페이스", objVo.getTablespaceNm().trim()));
					return sb.append(";").toString();
				case "DEL_OBJ":
				case "ADD_ATTR":
				case "DEL_ATTR":
					return join(simpleStmts(d, changeType, objOwner, objNm, attrNm, attrVo));
				default:
					return null;
			}
		} catch (DdlSafety.Invalid e) {
			return cannot(e);
		}
	}

	/** 테이블 삭제, 컬럼 추가·삭제 문장 */
	private static List<String> simpleStmts(SqlDialect d, String changeType, String objOwner, String objNm, String attrNm,
	                                        StdDataModelAttrVo attrVo) {
		List<String> out = new ArrayList<>();
		switch (changeType) {
			case "DEL_OBJ":
				out.add(d.dropTable(objOwner, objNm));
				break;
			case "ADD_ATTR":
				if (attrVo != null) out.add(d.addColumn(objOwner, objNm, attrVo));
				break;
			case "DEL_ATTR":
				out.add(d.dropColumn(objOwner, objNm, attrNm));
				break;
			default:
		}
		return out;
	}

	/** 컬럼 변경 — 이름이 바뀌면 이름 변경, 타입·길이·소수점이 바뀌면 타입 변경 (둘 다면 두 문장, 이름 변경 먼저) */
	public String generateModifyAttrSnippet(String dmId, String objOwner, String objNm, StdDataModelAttrVo before, StdDataModelAttrVo after) {
		String dbms = modelDbms(dmId);
		SqlDialect d = dialectOf(dbms);
		if (d == null) return noRule(dbms);
		try {
			return join(modifyAttrStmts(d, objOwner, objNm, before, after));
		} catch (DdlSafety.Invalid e) {
			return cannot(e);
		}
	}

	private static List<String> modifyAttrStmts(SqlDialect d, String objOwner, String objNm, StdDataModelAttrVo before, StdDataModelAttrVo after) {
		List<String> stmts = new ArrayList<>();
		if (!Objects.equals(before.getAttrNm(), after.getAttrNm()))
			stmts.add(d.renameColumn(objOwner, objNm, before.getAttrNm(), after.getAttrNm(), after));
		if (!Objects.equals(before.getDataType(), after.getDataType()) || before.getDataLen() != after.getDataLen()
				|| before.getDataDecimalLen() != after.getDataDecimalLen())
			stmts.add(d.modifyColumn(objOwner, objNm, after));
		return stmts;
	}

	/** 테이블 이름·소유자 변경. 소유자를 옮기는 DDL 이 없는 DBMS(Oracle·Tibero)는 주석 조각 */
	public String generateModifyObjSnippet(String dmId, String origOwner, String origObjNm, String objOwner, String objNm) {
		String dbms = modelDbms(dmId);
		SqlDialect d = dialectOf(dbms);
		if (d == null) return noRule(dbms);
		try {
			List<String> stmts = modifyObjStmts(d, origOwner, origObjNm, objOwner, objNm);
			if (stmts.contains(null))
				return "-- " + d.dbmsTp() + " 은 테이블 소유자를 바꾸는 DDL 이 없습니다 (" + DdlSafety.oneLine(origOwner) + " → " + DdlSafety.oneLine(objOwner) + ")";
			return join(stmts);
		} catch (DdlSafety.Invalid e) {
			return cannot(e);
		}
	}

	/** 소유자 이동 DDL 이 없는 DBMS 면 그 자리에 null 이 들어간다 */
	private static List<String> modifyObjStmts(SqlDialect d, String origOwner, String origObjNm, String objOwner, String objNm) {
		List<String> stmts = new ArrayList<>();
		if (!Objects.equals(origObjNm, objNm)) stmts.add(d.renameTable(origOwner, origObjNm, objNm));
		if (!Objects.equals(origOwner, objOwner)) stmts.add(d.changeOwner(origOwner, objNm, objOwner));
		return stmts;
	}

	/**
	 * WP0 — PK/FK 변경(ADD_PK·DEL_PK·ADD_FK·DEL_FK)의 조각. 컬럼은 제약조건 안의 순서대로 준다.
	 * DEL_PK 에서 cascade 면 이 PK 를 참조하는 FK 도 함께 지운다 (DBMS 가 지원할 때).
	 */
	public String generateConstraintDdlSnippet(String dmId, String changeType, String objOwner, String objNm, String constraintNm,
	                                           List<String> columns,
	                                           String refOwner, String refTableNm, List<String> refColumns,
	                                           boolean cascade) {
		String dbms = modelDbms(dmId);
		SqlDialect d = dialectOf(dbms);
		if (d == null) return noRule(dbms);
		try {
			String s = constraintStmt(d, changeType, objOwner, objNm, constraintNm, columns, refOwner, refTableNm, refColumns, cascade);
			return s == null ? null : s + ";";
		} catch (DdlSafety.Invalid e) {
			return cannot(e);
		}
	}

	private static String constraintStmt(SqlDialect d, String changeType, String objOwner, String objNm, String constraintNm,
	                                     List<String> columns, String refOwner, String refTableNm, List<String> refColumns,
	                                     boolean cascade) {
		switch (changeType) {
			case "ADD_PK":
				return d.addPrimaryKey(objOwner, objNm, constraintNm, columns);
			case "ADD_FK":
				return d.addForeignKey(objOwner, objNm, constraintNm, columns, refOwner, refTableNm, refColumns);
			case "DEL_PK":
				return d.dropPrimaryKey(objOwner, objNm, constraintNm, cascade);
			case "DEL_FK":
				return d.dropForeignKey(objOwner, objNm, constraintNm);
			default:
				return null;
		}
	}

	// ---- 109번 #3 — [DB 반영] 때 다시 만들기

	private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	/**
	 * [DB 반영] 할 문장을 변경 이력의 값(테이블·컬럼·제약조건 이름, 변경 전후 JSON)으로 지금의 규칙과 검사로 다시 만든다.
	 * 저장된 조각을 그대로 실행하지 않는다 — 이 검사 전에 저장된 조각이나 DB 에 직접 들어간 값이 실행되지 않게 하려는 것이다.
	 * 테이블 추가(ADD_OBJ)는 여기서 다루지 않는다 (DdlGenerator.createTableDdl). 검사에 걸리면 DdlSafety.Invalid,
	 * 다시 만들 값이 이력에 없으면 IllegalStateException.
	 *
	 * @param hist dmChangeHistory.selectByChangeSeq 행
	 */
	@SuppressWarnings("unchecked")
	public List<String> regenerate(Map<String, Object> hist, SqlDialect d) {
		String type = (String) hist.get("change_type");
		String owner = hist.get("obj_owner") == null ? "" : (String) hist.get("obj_owner");
		String objNm = (String) hist.get("obj_nm");
		String attrNm = (String) hist.get("attr_nm");
		String consNm = (String) hist.get("constraint_nm");
		try {
			switch (type) {
				case "DEL_OBJ":
				case "DEL_ATTR":
					return simpleStmts(d, type, owner, objNm, attrNm, null);
				case "ADD_ATTR":
					return simpleStmts(d, type, owner, objNm, attrNm, JSON.readValue(need(hist, "after_json"), StdDataModelAttrVo.class));
				case "MODIFY_ATTR":
					return modifyAttrStmts(d, owner, objNm,
							JSON.readValue(need(hist, "before_json"), StdDataModelAttrVo.class),
							JSON.readValue(need(hist, "after_json"), StdDataModelAttrVo.class));
				case "MODIFY_OBJ": {
					Map<String, Object> b = JSON.readValue(need(hist, "before_json"), Map.class);
					Map<String, Object> a = JSON.readValue(need(hist, "after_json"), Map.class);
					List<String> stmts = modifyObjStmts(d, nz(b.get("objOwner")), (String) b.get("objNm"), nz(a.get("objOwner")), (String) a.get("objNm"));
					if (stmts.contains(null)) throw new IllegalStateException(d.dbmsTp() + " 은 테이블 소유자를 바꾸는 DDL 이 없습니다");
					return stmts;
				}
				case "ADD_PK": {
					Map<String, Object> a = JSON.readValue(need(hist, "after_json"), Map.class);
					return one(constraintStmt(d, type, owner, objNm, consNm, (List<String>) a.get("columns"), null, null, null, false));
				}
				case "ADD_FK": {
					Map<String, Object> a = JSON.readValue(need(hist, "after_json"), Map.class);
					List<String> cols = new ArrayList<>();
					List<String> refCols = new ArrayList<>();
					List<Map<String, Object>> maps = (List<Map<String, Object>>) a.get("mappings");
					if (maps != null) {
						for (Map<String, Object> m : maps) {
							cols.add((String) m.get("ownAttrNm"));
							refCols.add((String) m.get("refAttrNm"));
						}
					}
					return one(constraintStmt(d, type, owner, objNm, consNm, cols, nz(a.get("refOwner")), (String) a.get("refTableNm"), refCols, false));
				}
				case "DEL_PK": {
					Map<String, Object> b = JSON.readValue(need(hist, "before_json"), Map.class);
					Object casc = b.get("cascadedFks");
					return one(constraintStmt(d, type, owner, objNm, consNm, null, null, null, null,
							casc instanceof List && !((List<Object>) casc).isEmpty()));
				}
				case "DEL_FK":
					return one(constraintStmt(d, type, owner, objNm, consNm, null, null, null, null, false));
				default:
					throw new IllegalStateException(type + " 변경은 DB 반영 대상이 아닙니다");
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException("변경 이력의 값을 읽지 못해 DDL 을 다시 만들 수 없습니다: " + e.getMessage());
		}
	}

	private static String need(Map<String, Object> hist, String key) {
		Object v = hist.get(key);
		if (v == null || v.toString().trim().isEmpty())
			throw new IllegalStateException("변경 이력에 DDL 을 다시 만들 값(" + key + ")이 없습니다");
		return v.toString();
	}

	private static String nz(Object v) {
		return v == null ? "" : v.toString();
	}

	private static List<String> one(String s) {
		List<String> out = new ArrayList<>();
		if (s != null) out.add(s);
		return out;
	}
}
