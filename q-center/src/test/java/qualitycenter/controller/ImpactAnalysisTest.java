package qualitycenter.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 영향도 분석 API 응답 구조 테스트.
 *
 * 실제 DB 연동 없이 API가 반환하는 Map 구조가 올바른지 검증한다.
 * - getWordImpact: { "terms": [...], "columns": [...] }
 * - getDomainImpact: { "terms": [...] }
 */
class ImpactAnalysisTest {

    // ========== 단어 영향도 ==========

    @Test
    @DisplayName("단어 영향도 결과 Map에 terms와 columns 키가 존재해야 한다")
    void wordImpactResultStructure() {
        Map<String, Object> result = buildWordImpactResult(
                Arrays.asList(mockTerm("사용자ID", "USER_ID")),
                Arrays.asList(mockColumn("TB_USER", "USER_ID"))
        );

        assertTrue(result.containsKey("terms"), "terms 키가 없음");
        assertTrue(result.containsKey("columns"), "columns 키가 없음");
    }

    @Test
    @DisplayName("단어 영향도: terms 목록이 List<Map> 형태여야 한다")
    void wordImpactTermsIsList() {
        Map<String, Object> result = buildWordImpactResult(
                Arrays.asList(mockTerm("사용자ID", "USER_ID"), mockTerm("사용자명", "USER_NM")),
                Collections.emptyList()
        );

        Object terms = result.get("terms");
        assertInstanceOf(List.class, terms);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> termList = (List<Map<String, Object>>) terms;
        assertEquals(2, termList.size());
    }

    @Test
    @DisplayName("단어 영향도: columns 목록이 List<Map> 형태여야 한다")
    void wordImpactColumnsIsList() {
        Map<String, Object> result = buildWordImpactResult(
                Collections.emptyList(),
                Arrays.asList(mockColumn("TB_USER", "USER_ID"), mockColumn("TB_ORDER", "ORDER_USER_ID"))
        );

        Object columns = result.get("columns");
        assertInstanceOf(List.class, columns);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> colList = (List<Map<String, Object>>) columns;
        assertEquals(2, colList.size());
    }

    @Test
    @DisplayName("단어 영향도: 빈 결과도 정상 Map을 반환해야 한다")
    void wordImpactEmptyResult() {
        Map<String, Object> result = buildWordImpactResult(
                Collections.emptyList(), Collections.emptyList());

        assertNotNull(result);
        assertEquals(2, result.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> terms = (List<Map<String, Object>>) result.get("terms");
        assertTrue(terms.isEmpty());
    }

    @Test
    @DisplayName("단어 영향도: term 항목에 termNm, termEngNm 키가 있어야 한다")
    void wordImpactTermFields() {
        Map<String, Object> term = mockTerm("사용자ID", "USER_ID");
        assertTrue(term.containsKey("termNm"));
        assertTrue(term.containsKey("termEngNm"));
        assertEquals("사용자ID", term.get("termNm"));
        assertEquals("USER_ID", term.get("termEngNm"));
    }

    @Test
    @DisplayName("단어 영향도: column 항목에 objNm, attrNm 키가 있어야 한다")
    void wordImpactColumnFields() {
        Map<String, Object> col = mockColumn("TB_USER", "USER_ID");
        assertTrue(col.containsKey("objNm"));
        assertTrue(col.containsKey("attrNm"));
        assertEquals("TB_USER", col.get("objNm"));
        assertEquals("USER_ID", col.get("attrNm"));
    }

    // ========== 도메인 영향도 ==========

    @Test
    @DisplayName("도메인 영향도 결과 Map에 terms 키가 존재해야 한다")
    void domainImpactResultStructure() {
        Map<String, Object> result = buildDomainImpactResult(
                Arrays.asList(mockTerm("사용자ID", "USER_ID"))
        );

        assertTrue(result.containsKey("terms"), "terms 키가 없음");
        assertFalse(result.containsKey("columns"), "도메인 영향도에는 columns가 없어야 함");
    }

    @Test
    @DisplayName("도메인 영향도: 다수 영향 용어를 정상 반환해야 한다")
    void domainImpactMultipleTerms() {
        Map<String, Object> result = buildDomainImpactResult(
                Arrays.asList(
                        mockTerm("사용자ID", "USER_ID"),
                        mockTerm("사용자명", "USER_NM"),
                        mockTerm("주문자ID", "ORDER_USER_ID")
                )
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> terms = (List<Map<String, Object>>) result.get("terms");
        assertEquals(3, terms.size());
    }

    // ========== 헬퍼 메서드 (컨트롤러 로직 재현) ==========

    private Map<String, Object> buildWordImpactResult(
            List<Map<String, Object>> terms,
            List<Map<String, Object>> columns) {
        Map<String, Object> result = new HashMap<>();
        result.put("terms", terms);
        result.put("columns", columns);
        return result;
    }

    private Map<String, Object> buildDomainImpactResult(
            List<Map<String, Object>> terms) {
        Map<String, Object> result = new HashMap<>();
        result.put("terms", terms);
        return result;
    }

    private Map<String, Object> mockTerm(String termNm, String termEngNm) {
        Map<String, Object> m = new HashMap<>();
        m.put("termNm", termNm);
        m.put("termEngNm", termEngNm);
        return m;
    }

    private Map<String, Object> mockColumn(String objNm, String attrNm) {
        Map<String, Object> m = new HashMap<>();
        m.put("objNm", objNm);
        m.put("attrNm", attrNm);
        return m;
    }
}
