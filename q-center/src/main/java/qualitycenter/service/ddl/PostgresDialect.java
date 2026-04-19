package qualitycenter.service.ddl;

public class PostgresDialect implements SqlDialect {

	@Override
	public String name() { return "postgres"; }

	@Override
	public String columnType(String dataType, Long dataLen, Short dataDecimalLen) {
		if (dataType == null) return "VARCHAR(255)";
		String t = dataType.trim().toUpperCase();
		long len = dataLen == null ? 0L : dataLen;
		short dec = dataDecimalLen == null ? 0 : dataDecimalLen;

		switch (t) {
			case "VARCHAR":
			case "VARCHAR2":
				return "VARCHAR(" + (len > 0 ? len : 255) + ")";
			case "CHAR":
				return "CHAR(" + (len > 0 ? len : 1) + ")";
			case "NUMERIC":
			case "NUMBER":
			case "DECIMAL":
				if (len > 0 && dec > 0) return "NUMERIC(" + len + "," + dec + ")";
				if (len > 0) return "NUMERIC(" + len + ")";
				return "NUMERIC";
			case "INT":
			case "INTEGER":
				return "INTEGER";
			case "BIGINT":
				return "BIGINT";
			case "DATE":
				return "DATE";
			case "TIMESTAMP":
				return "TIMESTAMP";
			case "TEXT":
			case "CLOB":
				return "TEXT";
			case "BLOB":
			case "BYTEA":
				return "BYTEA";
			default:
				if (len > 0 && dec > 0) return t + "(" + len + "," + dec + ")";
				if (len > 0) return t + "(" + len + ")";
				return t;
		}
	}

	@Override
	public String commentOnTable(String tableName, String comment) {
		return "COMMENT ON TABLE " + tableName + " IS '" + escapeStringLiteral(comment) + "';";
	}

	@Override
	public String commentOnColumn(String tableName, String columnName, String comment) {
		return "COMMENT ON COLUMN " + tableName + "." + columnName + " IS '" + escapeStringLiteral(comment) + "';";
	}
}
