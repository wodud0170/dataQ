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

	/** 109번 #3 — MariaDB 는 # 이 줄 주석 시작이다 */
	@Override
	public String id(String what, String name) {
		DdlSafety.ident(what, name);
		if (name.indexOf('#') >= 0) throw new DdlSafety.Invalid(what + " 이름에 # 은 MariaDB 에서 쓸 수 없습니다: " + name);
		return name;
	}

	/** 109번 #3 — 문자열 안 역슬래시가 다음 글자를 감싸므로 역슬래시도 두 번 쓴다 (아니면 닫는 따옴표가 감싸진다) */
	@Override
	public boolean backslashEscapes() { return true; }

	@Override
	public String escapeStringLiteral(String v) {
		if (v == null) return "";
		return v.replace("\\", "\\\\").replace("'", "''");
	}

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
	public String dropForeignKey(String owner, String table, String name) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP FOREIGN KEY " + id("제약조건", name);
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
		return "ALTER TABLE " + qualify(owner, table) + " CHANGE COLUMN " + id("컬럼", column) + " " + fullDef(a);
	}

	@Override
	public String dropPrimaryKey(String owner, String table, String name, boolean cascade) {
		return "ALTER TABLE " + qualify(owner, table) + " DROP PRIMARY KEY";
	}

	/** 정의 + 주석 (주석이 없으면 한글명) */
	private String fullDef(StdDataModelAttrVo a) {
		String c = a.getAttrComment() != null && !a.getAttrComment().trim().isEmpty() ? a.getAttrComment().trim()
				: (a.getAttrNmKr() != null && !a.getAttrNmKr().trim().isEmpty() ? a.getAttrNmKr().trim() : null);
		return columnDef(a) + (c == null ? "" : inlineColumnComment(c));
	}
}
