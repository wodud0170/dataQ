package qualitycenter.service.ddl;

public interface SqlDialect {
	String name();

	String columnType(String dataType, Long dataLen, Short dataDecimalLen);

	String commentOnTable(String tableName, String comment);

	String commentOnColumn(String tableName, String columnName, String comment);

	default String escapeStringLiteral(String v) {
		if (v == null) return "";
		return v.replace("'", "''");
	}
}
