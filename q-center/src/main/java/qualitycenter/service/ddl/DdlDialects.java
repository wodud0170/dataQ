package qualitycenter.service.ddl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 104번 WP3 — DBMS(데이터소스 DBMS_TP) → dialect */
@Service
public class DdlDialects {

	@Autowired
	private DdlRuleService rules;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/** DDL 규칙이 있는 DBMS 면 dialect, 없으면(Cubrid·Altibase·Goldilocks 등) null */
	public SqlDialect forDbms(String dbmsTp) {
		if (dbmsTp == null) return null;
		String t = dbmsTp.trim().toLowerCase();
		if (t.contains("tibero")) return new TiberoDialect(rules);
		if (t.contains("oracle")) return new OracleDialect(rules);
		if (t.contains("postgres")) return new PostgresDialect(rules);
		if (t.contains("maria")) return new MariaDbDialect(rules);
		if (t.contains("sqlserver") || t.contains("mssql")) return new SqlServerDialect(rules);
		return null;
	}

	/** 모델의 데이터소스 {dsId, dsn, dbmsTp}. 데이터소스가 없는 모델(논리 모델 등)은 null */
	public Map<String, Object> modelDs(String dmId) {
		return sqlSessionTemplate.selectOne("ddlRule.selectModelDs", dmId);
	}
}
