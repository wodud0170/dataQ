package qualitycenter.service.ddl;

import com.ndata.quality.model.std.StdDataModelAttrVo;

/** SQL Server — 이름 변경은 sp_rename, 주석은 확장 속성(MS_Description). 소유자가 비면 dbo */
public class SqlServerDialect extends AbstractDialect {

	public SqlServerDialect(DdlRuleService rules) {
		super(rules);
	}

	@Override
	public String dbmsTp() { return "SQLServer"; }

	@Override
	public String commentOnTable(String owner, String table, String comment) {
		return "EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'" + escapeStringLiteral(comment)
				+ "', @level0type = N'SCHEMA', @level0name = N'" + schema(owner)
				+ "', @level1type = N'TABLE', @level1name = N'" + id("테이블", table) + "'";
	}

	@Override
	public String commentOnColumn(String owner, String table, String column, String comment) {
		return commentOnTable(owner, table, comment) + ", @level2type = N'COLUMN', @level2name = N'" + id("컬럼", column) + "'";
	}

	@Override
	public String renameTable(String owner, String table, String newTable) {
		return "EXEC sp_rename '" + qualify(owner, table) + "', '" + id("테이블", newTable) + "'";
	}

	@Override
	public String changeOwner(String owner, String table, String newOwner) {
		return "ALTER SCHEMA " + schema(newOwner) + " TRANSFER " + qualify(schema(owner), table);
	}

	@Override
	public String addColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD " + columnDef(a);
	}

	/** ALTER COLUMN 은 NULL 여부를 빼면 NULL 허용으로 바뀌므로 함께 적는다 */
	@Override
	public String modifyColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ALTER COLUMN " + id("컬럼", a.getAttrNm()) + " "
				+ columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen())
				+ ("N".equalsIgnoreCase(a.getNullableYn()) ? " NOT NULL" : " NULL");
	}

	@Override
	public String renameColumn(String owner, String table, String column, String newColumn, StdDataModelAttrVo a) {
		return "EXEC sp_rename '" + qualify(owner, table) + "." + id("컬럼", column) + "', '" + id("컬럼", newColumn) + "', 'COLUMN'";
	}

	/** SQL Server 는 DROP CONSTRAINT … CASCADE 가 없다 — 참조하는 FK 가 있으면 DB 가 거부한다 */
	@Override
	public String dropPrimaryKey(String owner, String table, String name, boolean cascade) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP CONSTRAINT " + id("제약조건", name);
	}

	private String schema(String owner) {
		return (owner == null || owner.isEmpty()) ? "dbo" : id("소유자", owner);
	}
}
