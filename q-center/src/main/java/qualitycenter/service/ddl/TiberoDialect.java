package qualitycenter.service.ddl;

/** Tibero 는 Oracle 과 DDL 문법이 같다. 타입 매핑은 DBMS_TP 'Tibero' 행을 따로 읽는다 */
public class TiberoDialect extends OracleDialect {

	public TiberoDialect(DdlRuleService rules) {
		super(rules);
	}

	@Override
	public String dbmsTp() { return "Tibero"; }
}
