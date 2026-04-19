package qualitycenter.service.ddl;

public class OracleDialect implements SqlDialect {

	@Override
	public String name() { return "oracle"; }

	@Override
	public String columnType(String dataType, Long dataLen, Short dataDecimalLen) {
		if (dataType == null) return "VARCHAR2(255 CHAR)";
		String t = dataType.trim().toUpperCase();
		long len = dataLen == null ? 0L : dataLen;
		short dec = dataDecimalLen == null ? 0 : dataDecimalLen;

		switch (t) {
			case "VARCHAR":
			case "VARCHAR2":
				return "VARCHAR2(" + (len > 0 ? len : 255) + " CHAR)";
			case "CHAR":
				return "CHAR(" + (len > 0 ? len : 1) + ")";
			case "NUMERIC":
			case "NUMBER":
			case "DECIMAL":
				if (len > 0 && dec > 0) return "NUMBER(" + len + "," + dec + ")";
				if (len > 0) return "NUMBER(" + len + ")";
				return "NUMBER";
			case "INT":
			case "INTEGER":
				return "NUMBER(10)";
			case "BIGINT":
				return "NUMBER(19)";
			case "DATE":
				return "DATE";
			case "TIMESTAMP":
				return "TIMESTAMP";
			case "TEXT":
			case "CLOB":
				return "CLOB";
			case "BLOB":
			case "BYTEA":
				return "BLOB";
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
