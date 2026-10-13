package qualitycenter.service.ddl;

import java.util.List;

import com.ndata.quality.model.std.StdDataModelAttrVo;

/**
 * DBMS 별 DDL 문법. 문장은 끝에 ';' 를 붙이지 않고 돌려준다 — 여러 문장을 이을 때만 ';' 로 나눈다 (Oracle JDBC 는 끝의 ';' 를 받지 않는다).
 * 104번 WP3 — 변경 조각(ChangeHistoryService)과 전체 DDL(DdlGenerator)이 같은 dialect 를 쓴다.
 */
public interface SqlDialect {

	/** 데이터소스 DBMS_TP (Oracle · Tibero · PostgreSQL · MariaDB · SQLServer) */
	String dbmsTp();

	/** 데이터 타입·길이·소수점 → 이 DBMS 의 물리 타입 (TB_DATA_TYPE_MAP) */
	String columnType(String dataType, Long dataLen, Short dataDecimalLen);

	default String qualify(String owner, String name) {
		return (owner == null || owner.isEmpty()) ? name : owner + "." + name;
	}

	default String escapeStringLiteral(String v) {
		if (v == null) return "";
		return v.replace("'", "''");
	}

	/** 컬럼 정의 — 이름 타입 [DEFAULT 값] [NOT NULL] */
	default String columnDef(StdDataModelAttrVo a) {
		StringBuilder b = new StringBuilder(a.getAttrNm()).append(' ')
				.append(columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen()));
		if (a.getDefaultVal() != null && !a.getDefaultVal().trim().isEmpty()) b.append(" DEFAULT ").append(a.getDefaultVal().trim());
		if ("N".equalsIgnoreCase(a.getNullableYn())) b.append(" NOT NULL");
		return b.toString();
	}

	/** 주석을 CREATE TABLE 안에 넣는 DBMS(MariaDB)면 true. 그때는 commentOn* 대신 inline* 를 쓴다 */
	default boolean inlineComments() { return false; }

	default String inlineColumnComment(String comment) { return " COMMENT '" + escapeStringLiteral(comment) + "'"; }

	default String inlineTableComment(String comment) { return " COMMENT='" + escapeStringLiteral(comment) + "'"; }

	default String commentOnTable(String owner, String table, String comment) {
		return "COMMENT ON TABLE " + qualify(owner, table) + " IS '" + escapeStringLiteral(comment) + "'";
	}

	default String commentOnColumn(String owner, String table, String column, String comment) {
		return "COMMENT ON COLUMN " + qualify(owner, table) + "." + column + " IS '" + escapeStringLiteral(comment) + "'";
	}

	default String dropTable(String owner, String table) {
		return "DROP TABLE " + qualify(owner, table);
	}

	default String renameTable(String owner, String table, String newTable) {
		return "ALTER TABLE " + qualify(owner, table) + " RENAME TO " + newTable;
	}

	/** 테이블을 다른 소유자(스키마)로 옮기는 문장. 그런 DDL 이 없는 DBMS 는 null */
	String changeOwner(String owner, String table, String newOwner);

	String addColumn(String owner, String table, StdDataModelAttrVo a);

	default String dropColumn(String owner, String table, String column) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP COLUMN " + column;
	}

	/** 타입·길이·소수점 변경. a 는 바뀐 뒤의 컬럼 (이름도 바뀐 뒤 이름) */
	String modifyColumn(String owner, String table, StdDataModelAttrVo a);

	/** a 는 바뀐 뒤의 컬럼 — 이름 변경에 정의 전체가 필요한 DBMS(MariaDB CHANGE COLUMN)가 쓴다 */
	default String renameColumn(String owner, String table, String column, String newColumn, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " RENAME COLUMN " + column + " TO " + newColumn;
	}

	default String addPrimaryKey(String owner, String table, String name, List<String> columns) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD CONSTRAINT " + name + " PRIMARY KEY (" + String.join(", ", columns) + ")";
	}

	default String addForeignKey(String owner, String table, String name, List<String> columns,
	                             String refOwner, String refTable, List<String> refColumns) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD CONSTRAINT " + name
				+ " FOREIGN KEY (" + String.join(", ", columns) + ") REFERENCES " + qualify(refOwner, refTable)
				+ " (" + String.join(", ", refColumns) + ")";
	}

	/** cascade 면 이 PK 를 참조하는 FK 도 함께 지운다 */
	default String dropPrimaryKey(String owner, String table, String name, boolean cascade) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP CONSTRAINT " + name + (cascade ? " CASCADE" : "");
	}

	default String dropForeignKey(String owner, String table, String name) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP CONSTRAINT " + name;
	}
}
