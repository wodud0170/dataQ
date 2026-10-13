package qualitycenter.service.ddl;

import com.ndata.quality.model.std.StdDataModelAttrVo;

public class OracleDialect extends AbstractDialect {

	public OracleDialect(DdlRuleService rules) {
		super(rules);
	}

	@Override
	public String dbmsTp() { return "Oracle"; }

	/** Oracle 은 테이블을 다른 스키마로 옮기는 DDL 이 없다 (새 스키마에 다시 만들어야 한다) */
	@Override
	public String changeOwner(String owner, String table, String newOwner) {
		return null;
	}

	@Override
	public String addColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD (" + columnDef(a) + ")";
	}

	/** 타입만 바꾼다. NULL 여부를 같이 적으면 이미 그 상태일 때 ORA-01442·01451 이 난다 */
	@Override
	public String modifyColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " MODIFY (" + a.getAttrNm() + " "
				+ columnType(a.getDataType(), a.getDataLen(), a.getDataDecimalLen()) + ")";
	}
}
