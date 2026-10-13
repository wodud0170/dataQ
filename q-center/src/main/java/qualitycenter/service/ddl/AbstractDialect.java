package qualitycenter.service.ddl;

/** 타입 변환은 DDL 규칙(TB_DATA_TYPE_MAP)에서 읽는다 */
public abstract class AbstractDialect implements SqlDialect {

	protected final DdlRuleService rules;

	protected AbstractDialect(DdlRuleService rules) {
		this.rules = rules;
	}

	@Override
	public String columnType(String dataType, Long dataLen, Short dataDecimalLen) {
		return rules.physType(dbmsTp(), dataType, dataLen, dataDecimalLen);
	}
}
