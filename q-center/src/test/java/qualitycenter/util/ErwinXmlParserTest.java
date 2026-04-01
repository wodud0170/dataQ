package qualitycenter.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import qualitycenter.util.ErwinXmlParser.ErwinParseResult;

/**
 * ErwinXmlParser 단위 테스트.
 *
 * test_erwin_sample.xml 기반 파싱 결과를 검증한다.
 * - 테이블 5개: TB_USER, TB_DEPT, TB_ORDER, TB_ORDER_DTL, TB_PRODUCT
 * - 컬럼 34개
 */
class ErwinXmlParserTest {

    private static ErwinParseResult sampleResult;

    @BeforeAll
    static void parseSampleXml() throws Exception {
        try (InputStream is = ErwinXmlParserTest.class.getClassLoader()
                .getResourceAsStream("test_erwin_sample.xml")) {
            assertNotNull(is, "test_erwin_sample.xml이 classpath에 없음");
            sampleResult = ErwinXmlParser.parse(is);
        }
    }

    // ========== 기본 파싱 검증 ==========

    @Nested
    @DisplayName("샘플 XML 파싱 기본 검증")
    class BasicParsing {

        @Test
        @DisplayName("파싱 결과가 null이 아니어야 한다")
        void resultNotNull() {
            assertNotNull(sampleResult);
        }

        @Test
        @DisplayName("테이블 수는 5개여야 한다")
        void tableCount() {
            assertEquals(5, sampleResult.getTableCount());
            assertEquals(5, sampleResult.getTables().size());
        }

        @Test
        @DisplayName("컬럼 수는 34개여야 한다")
        void columnCount() {
            assertEquals(34, sampleResult.getColumnCount());
            assertEquals(34, sampleResult.getColumns().size());
        }
    }

    // ========== 테이블 상세 검증 ==========

    @Nested
    @DisplayName("테이블 상세 검증")
    class TableDetails {

        @Test
        @DisplayName("TB_USER 테이블이 존재하고 논리명이 '사용자'여야 한다")
        void tbUser() {
            Map<String, Object> table = findTable("TB_USER");
            assertNotNull(table, "TB_USER 테이블이 없음");
            assertEquals("사용자", table.get("objNmKr"));
            assertEquals(7, table.get("objAttrCnt"));
        }

        @Test
        @DisplayName("TB_DEPT 테이블이 존재하고 컬럼 6개여야 한다")
        void tbDept() {
            Map<String, Object> table = findTable("TB_DEPT");
            assertNotNull(table, "TB_DEPT 테이블이 없음");
            assertEquals("부서", table.get("objNmKr"));
            assertEquals(6, table.get("objAttrCnt"));
        }

        @Test
        @DisplayName("TB_ORDER 테이블이 존재하고 컬럼 8개여야 한다")
        void tbOrder() {
            Map<String, Object> table = findTable("TB_ORDER");
            assertNotNull(table, "TB_ORDER 테이블이 없음");
            assertEquals("주문", table.get("objNmKr"));
            assertEquals(8, table.get("objAttrCnt"));
        }

        @Test
        @DisplayName("TB_ORDER_DTL 테이블이 존재하고 컬럼 7개여야 한다")
        void tbOrderDtl() {
            Map<String, Object> table = findTable("TB_ORDER_DTL");
            assertNotNull(table, "TB_ORDER_DTL 테이블이 없음");
            assertEquals("주문상세", table.get("objNmKr"));
            assertEquals(7, table.get("objAttrCnt"));
        }

        @Test
        @DisplayName("TB_PRODUCT 테이블이 존재하고 컬럼 6개여야 한다")
        void tbProduct() {
            Map<String, Object> table = findTable("TB_PRODUCT");
            assertNotNull(table, "TB_PRODUCT 테이블이 없음");
            assertEquals("상품", table.get("objNmKr"));
            assertEquals(6, table.get("objAttrCnt"));
        }
    }

    // ========== 컬럼 상세 검증 ==========

    @Nested
    @DisplayName("컬럼 상세 검증")
    class ColumnDetails {

        @Test
        @DisplayName("TB_USER.USER_ID 컬럼: PK이고 VARCHAR2(20)")
        void userIdColumn() {
            Map<String, Object> col = findColumn("TB_USER", "USER_ID");
            assertNotNull(col, "TB_USER.USER_ID 컬럼이 없음");
            assertEquals("사용자ID", col.get("attrNmKr"));
            assertEquals("VARCHAR2", col.get("dataType"));
            assertEquals(20L, col.get("dataLen"));
            assertEquals("N", col.get("nullableYn"));
            assertEquals("Y", col.get("pkYn"));
            assertEquals((short) 1, col.get("attrOrder"));
        }

        @Test
        @DisplayName("TB_USER.EMAIL 컬럼: nullable이고 VARCHAR2(200)")
        void emailColumn() {
            Map<String, Object> col = findColumn("TB_USER", "EMAIL");
            assertNotNull(col, "TB_USER.EMAIL 컬럼이 없음");
            assertEquals("이메일", col.get("attrNmKr"));
            assertEquals("Y", col.get("nullableYn"));
            assertEquals("N", col.get("pkYn"));
            assertEquals(200L, col.get("dataLen"));
        }

        @Test
        @DisplayName("TB_ORDER.TOTAL_AMT 컬럼: NUMBER(15), nullable")
        void totalAmtColumn() {
            Map<String, Object> col = findColumn("TB_ORDER", "TOTAL_AMT");
            assertNotNull(col, "TB_ORDER.TOTAL_AMT 컬럼이 없음");
            assertEquals("총금액", col.get("attrNmKr"));
            assertEquals("NUMBER", col.get("dataType"));
            assertEquals(15L, col.get("dataLen"));
            assertEquals("Y", col.get("nullableYn"));
        }

        @Test
        @DisplayName("TB_ORDER_DTL의 복합 PK (ORDER_NO, SEQ_NO)")
        void compositePk() {
            Map<String, Object> orderNo = findColumn("TB_ORDER_DTL", "ORDER_NO");
            Map<String, Object> seqNo = findColumn("TB_ORDER_DTL", "SEQ_NO");
            assertNotNull(orderNo);
            assertNotNull(seqNo);
            assertEquals("Y", orderNo.get("pkYn"));
            assertEquals("Y", seqNo.get("pkYn"));
        }

        @Test
        @DisplayName("TB_USER.REG_DT 컬럼: DATE 타입, length 0")
        void dateColumn() {
            Map<String, Object> col = findColumn("TB_USER", "REG_DT");
            assertNotNull(col);
            assertEquals("DATE", col.get("dataType"));
            assertEquals(0L, col.get("dataLen"));
        }
    }

    // ========== 에러 케이스 ==========

    @Nested
    @DisplayName("에러 케이스")
    class ErrorCases {

        @Test
        @DisplayName("빈 XML (엔터티 없음)은 0건을 반환해야 한다")
        void emptyEntities() throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><erwin><model><entities></entities></model></erwin>";
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            ErwinParseResult result = ErwinXmlParser.parse(is);

            assertNotNull(result);
            assertEquals(0, result.getTableCount());
            assertEquals(0, result.getColumnCount());
            assertTrue(result.getTables().isEmpty());
            assertTrue(result.getColumns().isEmpty());
        }

        @Test
        @DisplayName("잘못된 XML은 Exception을 던져야 한다")
        void invalidXml() {
            String xml = "this is not xml";
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

            assertThrows(Exception.class, () -> ErwinXmlParser.parse(is));
        }

        @Test
        @DisplayName("엔터티 태그가 없는 XML은 0건을 반환해야 한다")
        void noEntityTags() throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><erwin><model></model></erwin>";
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            ErwinParseResult result = ErwinXmlParser.parse(is);

            assertEquals(0, result.getTableCount());
            assertEquals(0, result.getColumnCount());
        }
    }

    // ========== 헬퍼 메서드 ==========

    private Map<String, Object> findTable(String physicalName) {
        return sampleResult.getTables().stream()
                .filter(t -> physicalName.equals(t.get("objNm")))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findColumn(String tableName, String columnName) {
        return sampleResult.getColumns().stream()
                .filter(c -> tableName.equals(c.get("objNm")) && columnName.equals(c.get("attrNm")))
                .findFirst()
                .orElse(null);
    }
}
