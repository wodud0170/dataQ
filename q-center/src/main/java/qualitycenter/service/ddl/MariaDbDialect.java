package qualitycenter.service.ddl;

import com.ndata.quality.model.std.StdDataModelAttrVo;

/**
 * MariaDB — 주석은 열 정의 안 COMMENT, MODIFY·CHANGE 는 정의 전체를 다시 쓴다
 * (NULL 여부·기본값·주석을 빼면 지워지므로 모두 넣는다).
 */
public class MariaDbDialect extends AbstractDialect {

	public MariaDbDialect(DdlRuleService rules) {
		super(rules);
	}

	@Override
	public String dbmsTp() { return "MariaDB"; }

	@Override
	public boolean inlineComments() { return true; }

	@Override
	public String commentOnTable(String owner, String table, String comment) {
		return "ALTER TABLE " + qualify(owner, table) + inlineTableComment(comment);
	}

	@Override
	public String commentOnColumn(String owner, String table, String column, String comment) {
		throw new UnsupportedOperationException("MariaDB 는 컬럼 주석을 열 정의 안에 넣는다 (inlineComments)");
	}

	@Override
	public String renameTable(String owner, String table, String newTable) {
		return "RENAME TABLE " + qualify(owner, table) + " TO " + qualify(owner, newTable);
	}

	@Override
	public String changeOwner(String owner, String table, String newOwner) {
		return "RENAME TABLE " + qualify(owner, table) + " TO " + qualify(newOwner, table);
	}

	@Override
	public String addColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " ADD COLUMN " + fullDef(a);
	}

	@Override
	public String modifyColumn(String owner, String table, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " MODIFY COLUMN " + fullDef(a);
	}

	@Override
	public String renameColumn(String owner, String table, String column, String newColumn, StdDataModelAttrVo a) {
		return "ALTER TABLE " + qualify(owner, table) + " CHANGE COLUMN " + column + " " + fullDef(a);
	}

	@Override
	public String dropPrimaryKey(String owner, String table, String name, boolean cascade) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP PRIMARY KEY";
	}

	@Override
	public String dropForeignKey(String owner, String table, String name) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP FOREIGN KEY " + name;
	}

	/** 정의 + 주석 (주석이 없으면 한글명) */
	private String fullDef(StdDataModelAttrVo a) {
		String c = a.getAttrComment() != null && !a.getAttrComment().trim().isEmpty() ? a.getAttrComment().trim()
				: (a.getAttrNmKr() != null && !a.getAttrNmKr().trim().isEmpty() ? a.getAttrNmKr().trim() : null);
		return columnDef(a) + (c == null ? "" : inlineColumnComment(c));
	}
}
