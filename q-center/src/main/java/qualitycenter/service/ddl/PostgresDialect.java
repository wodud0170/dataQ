package qualitycenter.service.ddl;

import com.ndata.quality.model.std.StdDataModelAttrVo;

public class PostgresDialect extends AbstractDialect {

	public PostgresDialect(DdlRuleService rules) {
		super(rules);
	}

	@Override
	public String dbmsTp() { return "PostgreSQL"; }

	@Override
	public String changeOwner(String owner, String table, String newOwner) {
		return "ALTER TABLE " + qualify(owner, table) + " SET SCHEMA " + id("소유자", newOwner);
	}

	@Override
	public String addColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD COLUMN " + columnDef(a);
	}

	@Override
	public String modifyColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ALTER COLUMN " + id("컬럼", a.getAttrNm()) + " TYPE "
				+ columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen());
	}
}
