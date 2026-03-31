package qualitycenter.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 구조 진단 (StructDiagController) 단위 테스트
 *
 * DB 의존 없이 diff 계산 로직, 헬퍼 메서드를 검증한다.
 */
class StructDiagTest {

	// ========== 1. toAttrMap 테스트 ==========

	@Nested
	@DisplayName("toAttrMap")
	class ToAttrMapTest {

		@Test
		@DisplayName("정상 변환: tableNm|columnNm 키로 매핑")
		void normalConversion() {
			List<Map<String, Object>> attrs = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "Y"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "N"),
				makeAttr("TB_ORDER", "ORDER_NO", "NUMBER", 10, "N")
			);
			Map<String, Map<String, Object>> map = toAttrMap(attrs);

			assertEquals(3, map.size());
			assertNotNull(map.get("TB_USER|USER_ID"));
			assertNotNull(map.get("TB_USER|USER_NM"));
			assertNotNull(map.get("TB_ORDER|ORDER_NO"));
		}

		@Test
		@DisplayName("빈 리스트 → 빈 맵")
		void emptyList() {
			Map<String, Map<String, Object>> map = toAttrMap(Collections.emptyList());
			assertTrue(map.isEmpty());
		}

		@Test
		@DisplayName("동일 테이블+컬럼 중복 시 마지막 것이 유지")
		void duplicateKey() {
			List<Map<String, Object>> attrs = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "Y"),
				makeAttr("TB_USER", "USER_ID", "NUMBER", 10, "N")
			);
			Map<String, Map<String, Object>> map = toAttrMap(attrs);

			assertEquals(1, map.size());
			assertEquals("NUMBER", map.get("TB_USER|USER_ID").get("dataType"));
		}
	}

	// ========== 2. nullSafeEquals 테스트 ==========

	@Nested
	@DisplayName("nullSafeEquals")
	class NullSafeEqualsTest {

		@Test
		@DisplayName("둘 다 null → true")
		void bothNull() {
			assertTrue(nullSafeEquals(null, null));
		}

		@Test
		@DisplayName("하나만 null → false")
		void oneNull() {
			assertFalse(nullSafeEquals(null, "A"));
			assertFalse(nullSafeEquals("A", null));
		}

		@Test
		@DisplayName("같은 값 → true")
		void sameValue() {
			assertTrue(nullSafeEquals("VARCHAR", "VARCHAR"));
			assertTrue(nullSafeEquals(20, 20));
		}

		@Test
		@DisplayName("다른 값 → false")
		void differentValue() {
			assertFalse(nullSafeEquals("VARCHAR", "NUMBER"));
			assertFalse(nullSafeEquals(20, 30));
		}

		@Test
		@DisplayName("타입 다른 같은 값 (toString 비교) → true")
		void crossType() {
			assertTrue(nullSafeEquals(20, "20"));
			assertTrue(nullSafeEquals(20L, "20"));
		}
	}

	// ========== 3. diff 계산 로직 테스트 ==========

	@Nested
	@DisplayName("diff 계산")
	class DiffTest {

		@Test
		@DisplayName("변경 없음 → 빈 결과")
		void noChanges() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "Y")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(0, diff.changes.size());
			assertEquals(0, diff.addedColumns);
			assertEquals(0, diff.modifiedColumns);
			assertEquals(0, diff.deletedColumns);
		}

		@Test
		@DisplayName("컬럼 추가 (ADDED)")
		void columnAdded() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "EMAIL", "VARCHAR", 200, "Y")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("ADDED", diff.changes.get(0).get("changeType"));
			assertEquals("EMAIL", diff.changes.get(0).get("columnNm"));
			assertEquals(1, diff.addedColumns);
		}

		@Test
		@DisplayName("컬럼 삭제 (DELETED)")
		void columnDeleted() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "OLD_FLAG", "CHAR", 1, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("DELETED", diff.changes.get(0).get("changeType"));
			assertEquals("OLD_FLAG", diff.changes.get(0).get("columnNm"));
			assertEquals(1, diff.deletedColumns);
		}

		@Test
		@DisplayName("컬럼 타입 변경 (MODIFIED)")
		void columnTypeModified() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_ORDER", "AMT", "NUMBER", 10, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_ORDER", "AMT", "NUMBER", 15, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
			assertEquals(1, diff.modifiedColumns);
		}

		@Test
		@DisplayName("nullable 변경도 MODIFIED")
		void nullableModified() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
		}

		@Test
		@DisplayName("테이블 추가 (새 테이블의 모든 컬럼 ADDED)")
		void tableAdded() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_NEW", "COL1", "VARCHAR", 50, "Y"),
				makeAttr("TB_NEW", "COL2", "NUMBER", 10, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(2, diff.addedColumns);
			assertEquals(1, diff.addedTables);
			assertTrue(diff.changes.stream().allMatch(c -> "ADDED".equals(c.get("changeType"))));
		}

		@Test
		@DisplayName("테이블 삭제 (해당 테이블의 모든 컬럼 DELETED)")
		void tableDeleted() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_OLD", "FLAG", "CHAR", 1, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.deletedColumns);
			assertEquals(1, diff.deletedTables);
		}

		@Test
		@DisplayName("복합: 추가+수정+삭제 동시")
		void mixed() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 50, "Y"),
				makeAttr("TB_USER", "OLD_COL", "CHAR", 1, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "Y"),  // 길이 변경
				makeAttr("TB_USER", "NEW_COL", "DATE", 0, "Y")       // 신규
				// OLD_COL 삭제
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(3, diff.changes.size());
			assertEquals(1, diff.addedColumns);
			assertEquals(1, diff.modifiedColumns);
			assertEquals(1, diff.deletedColumns);
		}

		@Test
		@DisplayName("타입만 변경 (MODIFIED) - 길이/nullable 동일")
		void typeOnlyModified() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "CHAR", 20, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
			assertEquals(1, diff.modifiedColumns);
			assertEquals(0, diff.addedColumns);
			assertEquals(0, diff.deletedColumns);
		}

		@Test
		@DisplayName("길이만 변경 (MODIFIED) - 타입/nullable 동일")
		void lengthOnlyModified() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 50, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
			assertEquals(1, diff.modifiedColumns);
		}

		@Test
		@DisplayName("nullable만 변경 (MODIFIED) - 타입/길이 동일")
		void nullableOnlyModified() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_ORDER", "AMT", "NUMBER", 10, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_ORDER", "AMT", "NUMBER", 10, "N")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
			assertEquals(1, diff.modifiedColumns);
		}

		@Test
		@DisplayName("대량 데이터 diff (100개 이상 컬럼)")
		void largeDataDiff() {
			List<Map<String, Object>> prev = new ArrayList<>();
			List<Map<String, Object>> curr = new ArrayList<>();
			// 100개 동일 컬럼
			for (int i = 0; i < 100; i++) {
				prev.add(makeAttr("TB_BIG", "COL_" + i, "VARCHAR", 20, "N"));
				curr.add(makeAttr("TB_BIG", "COL_" + i, "VARCHAR", 20, "N"));
			}
			// 10개 삭제 (prev에만)
			for (int i = 100; i < 110; i++) {
				prev.add(makeAttr("TB_BIG", "OLD_" + i, "CHAR", 1, "Y"));
			}
			// 15개 추가 (curr에만)
			for (int i = 0; i < 15; i++) {
				curr.add(makeAttr("TB_BIG", "NEW_" + i, "NUMBER", 10, "N"));
			}
			// 5개 수정 (타입 변경)
			for (int i = 0; i < 5; i++) {
				prev.add(makeAttr("TB_BIG", "MOD_" + i, "VARCHAR", 20, "N"));
				curr.add(makeAttr("TB_BIG", "MOD_" + i, "NUMBER", 20, "N"));
			}

			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(15, diff.addedColumns);
			assertEquals(10, diff.deletedColumns);
			assertEquals(5, diff.modifiedColumns);
			assertEquals(30, diff.changes.size());
		}

		@Test
		@DisplayName("같은 테이블 내 추가+삭제+수정 동시 (상세 검증)")
		void sameTableMixedDetailed() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_EMP", "EMP_ID", "VARCHAR", 10, "N"),     // 유지
				makeAttr("TB_EMP", "EMP_NM", "VARCHAR", 50, "Y"),     // 길이 변경
				makeAttr("TB_EMP", "DEPT_CD", "CHAR", 5, "N"),        // nullable 변경
				makeAttr("TB_EMP", "OLD_FLAG", "CHAR", 1, "Y"),       // 삭제
				makeAttr("TB_EMP", "RETIRE_DT", "DATE", 0, "Y")       // 삭제
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_EMP", "EMP_ID", "VARCHAR", 10, "N"),     // 유지
				makeAttr("TB_EMP", "EMP_NM", "VARCHAR", 100, "Y"),    // 길이 변경
				makeAttr("TB_EMP", "DEPT_CD", "CHAR", 5, "Y"),        // nullable 변경
				makeAttr("TB_EMP", "EMAIL", "VARCHAR", 200, "Y"),     // 추가
				makeAttr("TB_EMP", "PHONE", "VARCHAR", 20, "N")       // 추가
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(2, diff.addedColumns);
			assertEquals(2, diff.modifiedColumns);
			assertEquals(2, diff.deletedColumns);
			assertEquals(6, diff.changes.size());
			// 테이블 추가/삭제는 없음
			assertEquals(0, diff.addedTables);
			assertEquals(0, diff.deletedTables);
		}

		@Test
		@DisplayName("테이블명/컬럼명에 한글 포함")
		void koreanTableColumnNames() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_사용자", "사용자_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_사용자", "사용자_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_사용자", "이름", "VARCHAR", 100, "Y")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.addedColumns);
			assertEquals("이름", diff.changes.get(0).get("columnNm"));
			assertEquals("TB_사용자", diff.changes.get(0).get("tableNm"));
		}

		@Test
		@DisplayName("컬럼명에 특수문자 포함 (_$#)")
		void specialCharColumnNames() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_TEST", "COL_$AMT", "NUMBER", 10, "N"),
				makeAttr("TB_TEST", "COL#FLAG", "CHAR", 1, "Y")
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_TEST", "COL_$AMT", "NUMBER", 15, "N"),  // 길이 변경
				makeAttr("TB_TEST", "COL#FLAG", "CHAR", 1, "Y")      // 동일
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.changes.size());
			assertEquals("COL_$AMT", diff.changes.get(0).get("columnNm"));
			assertEquals("MODIFIED", diff.changes.get(0).get("changeType"));
		}

		@Test
		@DisplayName("여러 테이블 동시 추가+삭제+수정")
		void multiTableMixed() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_A", "COL1", "VARCHAR", 20, "N"),
				makeAttr("TB_B", "COL1", "NUMBER", 10, "N"),       // TB_B 삭제될 테이블
				makeAttr("TB_C", "COL1", "CHAR", 1, "Y")           // TB_C에 수정
			);
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_A", "COL1", "VARCHAR", 20, "N"),
				makeAttr("TB_C", "COL1", "VARCHAR", 1, "Y"),       // 타입 변경
				makeAttr("TB_D", "COL1", "DATE", 0, "N"),          // TB_D 신규 테이블
				makeAttr("TB_D", "COL2", "VARCHAR", 50, "Y")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.deletedTables);   // TB_B 삭제
			assertEquals(1, diff.addedTables);      // TB_D 추가
			assertEquals(1, diff.deletedColumns);   // TB_B.COL1
			assertEquals(2, diff.addedColumns);     // TB_D.COL1, TB_D.COL2
			assertEquals(1, diff.modifiedColumns);  // TB_C.COL1
		}

		@Test
		@DisplayName("빈 이전 → 전부 ADDED")
		void emptyPrev() {
			List<Map<String, Object>> prev = Collections.emptyList();
			List<Map<String, Object>> curr = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N"),
				makeAttr("TB_USER", "USER_NM", "VARCHAR", 100, "Y")
			);
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(2, diff.addedColumns);
			assertEquals(1, diff.addedTables);
			assertEquals(0, diff.deletedColumns);
		}

		@Test
		@DisplayName("빈 현재 → 전부 DELETED")
		void emptyCurr() {
			List<Map<String, Object>> prev = Arrays.asList(
				makeAttr("TB_USER", "USER_ID", "VARCHAR", 20, "N")
			);
			List<Map<String, Object>> curr = Collections.emptyList();
			DiffResult diff = calculateDiff(prev, curr);

			assertEquals(1, diff.deletedColumns);
			assertEquals(1, diff.deletedTables);
			assertEquals(0, diff.addedColumns);
		}
	}

	// ========== diff 계산 로직 재현 (StructDiagController와 동일) ==========

	static class DiffResult {
		List<Map<String, Object>> changes = new ArrayList<>();
		int addedTables, addedColumns, modifiedColumns, deletedTables, deletedColumns;
	}

	private DiffResult calculateDiff(List<Map<String, Object>> prevAttrs, List<Map<String, Object>> currAttrs) {
		DiffResult result = new DiffResult();

		Map<String, Map<String, Object>> prevMap = toAttrMap(prevAttrs);
		Map<String, Map<String, Object>> currMap = toAttrMap(currAttrs);

		Set<String> prevTableSet = new HashSet<>();
		Set<String> currTableSet = new HashSet<>();
		for (String key : prevMap.keySet()) prevTableSet.add(key.split("\\|")[0]);
		for (String key : currMap.keySet()) currTableSet.add(key.split("\\|")[0]);

		// ADDED
		for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
			if (!prevMap.containsKey(entry.getKey())) {
				Map<String, Object> detail = new HashMap<>();
				Map<String, Object> curr = entry.getValue();
				detail.put("tableNm", curr.get("tableNm"));
				detail.put("columnNm", curr.get("columnNm"));
				detail.put("changeType", "ADDED");
				detail.put("currDataType", curr.get("dataType"));
				detail.put("currDataLen", curr.get("dataLen"));
				result.changes.add(detail);
				result.addedColumns++;
			}
		}

		// DELETED
		for (Map.Entry<String, Map<String, Object>> entry : prevMap.entrySet()) {
			if (!currMap.containsKey(entry.getKey())) {
				Map<String, Object> detail = new HashMap<>();
				Map<String, Object> prev = entry.getValue();
				detail.put("tableNm", prev.get("tableNm"));
				detail.put("columnNm", prev.get("columnNm"));
				detail.put("changeType", "DELETED");
				detail.put("prevDataType", prev.get("dataType"));
				detail.put("prevDataLen", prev.get("dataLen"));
				result.changes.add(detail);
				result.deletedColumns++;
			}
		}

		// MODIFIED
		for (Map.Entry<String, Map<String, Object>> entry : currMap.entrySet()) {
			if (prevMap.containsKey(entry.getKey())) {
				Map<String, Object> prev = prevMap.get(entry.getKey());
				Map<String, Object> curr = entry.getValue();
				boolean typeChanged = !nullSafeEquals(prev.get("dataType"), curr.get("dataType"));
				boolean lenChanged = !nullSafeEquals(prev.get("dataLen"), curr.get("dataLen"));
				boolean nullableChanged = !nullSafeEquals(prev.get("nullableYn"), curr.get("nullableYn"));
				if (typeChanged || lenChanged || nullableChanged) {
					Map<String, Object> detail = new HashMap<>();
					detail.put("tableNm", curr.get("tableNm"));
					detail.put("columnNm", curr.get("columnNm"));
					detail.put("changeType", "MODIFIED");
					result.changes.add(detail);
					result.modifiedColumns++;
				}
			}
		}

		for (String t : currTableSet) { if (!prevTableSet.contains(t)) result.addedTables++; }
		for (String t : prevTableSet) { if (!currTableSet.contains(t)) result.deletedTables++; }

		return result;
	}

	// ========== 헬퍼 ==========

	private Map<String, Map<String, Object>> toAttrMap(List<Map<String, Object>> attrs) {
		Map<String, Map<String, Object>> map = new LinkedHashMap<>();
		for (Map<String, Object> attr : attrs) {
			String key = attr.get("tableNm") + "|" + attr.get("columnNm");
			map.put(key, attr);
		}
		return map;
	}

	private boolean nullSafeEquals(Object a, Object b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return a.toString().equals(b.toString());
	}

	private Map<String, Object> makeAttr(String tableNm, String columnNm, String dataType, int dataLen, String nullableYn) {
		Map<String, Object> attr = new HashMap<>();
		attr.put("tableNm", tableNm);
		attr.put("columnNm", columnNm);
		attr.put("dataType", dataType);
		attr.put("dataLen", dataLen);
		attr.put("nullableYn", nullableYn);
		return attr;
	}
}
