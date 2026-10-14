package qualityexecutor.service.std;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 104번 WP4 보완 — 카탈로그 SQL 로 FK 의 참조 대상(테이블·컬럼·삭제 규칙)을 읽을 수 없는 DBMS(Cubrid)는
 * 제약조건 수집 쿼리가 그 칸을 비워 두고, 여기서 JDBC 메타데이터(getImportedKeys)로 채운다.
 * 수집(DataModelService)과 구조 진단(StructDiagService)이 같은 방식으로 채워야 둘을 비교할 때 차이가 생기지 않는다.
 *
 * <p>Cubrid 11.2 의 JDBC 는 "소유자.테이블" 을 접속 사용자 기준으로 찾으므로 데이터소스 계정이 그 스키마의 소유자여야 한다.
 * 찾지 못하면 참조 칸은 빈 채로 둔다.</p>
 */
class FkRefFiller {

	private static final Logger log = LoggerFactory.getLogger(FkRefFiller.class);

	private final Connection conn;
	private final Map<String, List<Map<String, Object>>> cache = new HashMap<>();

	FkRefFiller(Connection conn) {
		this.conn = conn;
	}

	/** row: constraintType·tableNm·constraintNm·columnNm → refOwner·refTableNm·refColumnNm·deleteRule 를 채운다 (FK 이고 비어 있을 때만) */
	void fill(String schema, Map<String, Object> row) {
		if (!"R".equals(row.get("constraintType")) || row.get("refTableNm") != null) return;
		String table = (String) row.get("tableNm");
		List<Map<String, Object>> keys = cache.get(schema + "|" + table);
		if (keys == null) {
			keys = load(schema, table);
			cache.put(schema + "|" + table, keys);
		}
		for (Map<String, Object> k : keys) {
			if (eq(k.get("fkName"), row.get("constraintNm")) && eq(k.get("fkColumn"), row.get("columnNm"))) {
				row.put("refOwner", k.get("refOwner"));
				row.put("refTableNm", k.get("refTableNm"));
				row.put("refColumnNm", k.get("refColumnNm"));
				row.put("deleteRule", k.get("deleteRule"));
				return;
			}
		}
	}

	private List<Map<String, Object>> load(String schema, String table) {
		List<Map<String, Object>> out = new ArrayList<>();
		try (ResultSet rs = conn.getMetaData().getImportedKeys(null, null, schema + "." + table)) {
			while (rs.next()) {
				Map<String, Object> k = new HashMap<>();
				k.put("fkName", rs.getString("FK_NAME"));
				k.put("fkColumn", rs.getString("FKCOLUMN_NAME"));
				// Cubrid 는 참조 테이블을 "소유자.테이블" 로 돌려준다
				String pk = rs.getString("PKTABLE_NAME");
				int dot = pk == null ? -1 : pk.indexOf('.');
				k.put("refOwner", dot > 0 ? pk.substring(0, dot).toUpperCase() : rs.getString("PKTABLE_SCHEM"));
				k.put("refTableNm", dot > 0 ? pk.substring(dot + 1) : pk);
				k.put("refColumnNm", rs.getString("PKCOLUMN_NAME"));
				k.put("deleteRule", deleteRule(rs.getShort("DELETE_RULE")));
				out.add(k);
			}
		} catch (SQLException e) {
			// 접속 계정이 소유자가 아니면 Cubrid JDBC 가 테이블을 찾지 못한다 — 참조 칸은 빈 채로 둔다
			log.warn(">> FK 참조 대상을 읽지 못했습니다 {}.{} : {}", schema, table, e.getMessage());
		}
		return out;
	}

	private static String deleteRule(short r) {
		switch (r) {
			case DatabaseMetaData.importedKeyCascade: return "CASCADE";
			case DatabaseMetaData.importedKeySetNull: return "SET NULL";
			case DatabaseMetaData.importedKeySetDefault: return "SET DEFAULT";
			case DatabaseMetaData.importedKeyRestrict: return "RESTRICT";
			default: return "NO ACTION";
		}
	}

	private static boolean eq(Object a, Object b) {
		return a != null && b != null && a.toString().equalsIgnoreCase(b.toString());
	}
}
