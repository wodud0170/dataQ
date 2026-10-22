package qualitycenter.service.ddl;

import java.util.ArrayList;
import java.util.List;

import com.ndata.quality.model.std.StdDataModelAttrVo;

/**
 * DBMS 별 DDL 문법. 문장은 끝에 ';' 를 붙이지 않고 돌려준다 — 여러 문장을 이을 때만 ';' 로 나눈다 (Oracle JDBC 는 끝의 ';' 를 받지 않는다).
 * 104번 WP3 — 변경 조각(ChangeHistoryService)과 전체 DDL(DdlGenerator)이 같은 dialect 를 쓴다.
 * 109번 #3 — 문장에 넣는 이름·타입·기본값은 여기서 {@link DdlSafety} 로 검사한다. 걸리면 {@link DdlSafety.Invalid}.
 */
public interface SqlDialect {

	/** 데이터소스 DBMS_TP (Oracle · Tibero · PostgreSQL · MariaDB · SQLServer) */
	String dbmsTp();

	/** 데이터 타입·길이·소수점 → 이 DBMS 의 물리 타입 (TB_DATA_TYPE_MAP) */
	String columnType(String dataType, Long dataLen, Short dataDecimalLen);

	/** 이름 검사 — what 은 메시지에 보일 항목 이름 */
	default String id(String what, String name) {
		return DdlSafety.ident(what, name);
	}

	default List<String> ids(String what, List<String> names) {
		List<String> out = new ArrayList<>();
		if (names == null || names.isEmpty()) throw new DdlSafety.Invalid(what + " 목록이 비어 있습니다.");
		for (String n : names) out.add(id(what, n));
		return out;
	}

	default String qualify(String owner, String name) {
		return (owner == null || owner.isEmpty()) ? id("테이블", name) : id("소유자", owner) + "." + id("테이블", name);
	}

	/** 문자열 상수 안에 넣을 값 — 작은따옴표를 두 번 쓴다 */
	default String escapeStringLiteral(String v) {
		if (v == null) return "";
		return v.replace("'", "''");
	}

	/** 기본값 — 이 DBMS 의 규칙으로 검사한 값 (비어 있으면 null) */
	default String defaultExpr(String v) {
		return DdlSafety.defaultValue(v, dbmsTp());
	}

	/** 컬럼 정의 — 이름 타입 [DEFAULT 값] [NOT NULL] */
	default String columnDef(StdDataModelAttrVo a) {
		StringBuilder b = new StringBuilder(id("컬럼", a.getAttrNm())).append(' ')
				.append(columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen()));
		String dflt = defaultExpr(a.getDefaultVal());
		if (dflt != null) b.append(" DEFAULT ").append(dflt);
		if ("N".equalsIgnoreCase(a.getNullableYn())) b.append(" NOT NULL");
		return b.toString();
	}

	/** 주석을 CREATE TABLE 안에 넣는 DBMS(MariaDB)면 true. 그때는 commentOn* 대신 inline* 를 쓴다 */
	default boolean inlineComments() { return false; }

	/** 문자열 안 역슬래시가 다음 글자를 감싸는 DBMS(MariaDB)면 true — 실행 전 문장 검사에 쓴다 */
	default boolean backslashEscapes() { return false; }

	default String inlineColumnComment(String comment) { return " COMMENT '" + escapeStringLiteral(comment) + "'"; }

	default String inlineTableComment(String comment) { return " COMMENT='" + escapeStringLiteral(comment) + "'"; }

	default String commentOnTable(String owner, String table, String comment) {
		return "COMMENT ON TABLE " + qualify(owner, table) + " IS '" + escapeStringLiteral(comment) + "'";
	}

	default String commentOnColumn(String owner, String table, String column, String comment) {
		return "COMMENT ON COLUMN " + qualify(owner, table) + "." + id("컬럼", column) + " IS '" + escapeStringLiteral(comment) + "'";
	}

	default String dropTable(String owner, String table) {
		return "DROP TABLE " + qualify(owner, table);
	}

	default String renameTable(String owner, String table, String newTable) {
		return "ALTER TABLE " + qualify(owner, table) + " RENAME TO " + id("테이블", newTable);
	}

	/** 테이블을 다른 소유자(스키마)로 옮기는 문장. 그런 DDL 이 없는 DBMS 는 null */
	String changeOwner(String owner, String table, String newOwner);

	String addColumn(String owner, String table, StdDataModelAttrVo a);

	default String dropColumn(String owner, String table, String column) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP COLUMN " + id("컬럼", column);
	}

	/** 타입·길이·소수점 변경. a 는 바뀐 뒤의 컬럼 (이름도 바뀐 뒤 이름) */
	String modifyColumn(String owner, String table, StdDataModelAttrVo a);

	/** a 는 바뀐 뒤의 컬럼 — 이름 변경에 정의 전체가 필요한 DBMS(MariaDB CHANGE COLUMN)가 쓴다 */
	default String renameColumn(String owner, String table, String column, String newColumn, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " RENAME COLUMN " + id("컬럼", column) + " TO " + id("컬럼", newColumn);
	}

	default String addPrimaryKey(String owner, String table, String name, List<String> columns) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD CONSTRAINT " + id("제약조건", name)
				+ " PRIMARY KEY (" + String.join(", ", ids("PK 컬럼", columns)) + ")";
	}

	default String addForeignKey(String owner, String table, String name, List<String> columns,
	                             String refOwner, String refTable, List<String> refColumns) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD CONSTRAINT " + id("제약조건", name)
				+ " FOREIGN KEY (" + String.join(", ", ids("FK 컬럼", columns)) + ") REFERENCES " + qualify(refOwner, refTable)
				+ " (" + String.join(", ", ids("참조 컬럼", refColumns)) + ")";
	}

	/** cascade 면 이 PK 를 참조하는 FK 도 함께 지운다 */
	default String dropPrimaryKey(String owner, String table, String name, boolean cascade) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP CONSTRAINT " + id("제약조건", name) + (cascade ? " CASCADE" : "");
	}

	default String dropForeignKey(String owner, String table, String name) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP CONSTRAINT " + id("제약조건", name);
	}
}
