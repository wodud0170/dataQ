package qualitycenter.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelObjVo;

import qualitycenter.util.ErwinXmlParser.ErwinParseResult;

/**
 * ErwinXmlExporter 단위 테스트 (104번 WP8-40, 108 §4.25).
 *
 * 내보낸 파일을 ErwinXmlParser 로 되읽으면 테이블·컬럼 칸이 모두 같은지 본다.
 * - 테이블 5개: SALES.TB_ORDER / HR.TB_ORDER(두 소유자에 같은 이름) / HR.TB_DEPT / 빈 소유자 TB_CODE / 컬럼 없는 SALES.TB_EMPTY
 * - 컬럼 7개 + 보이지 않는 테이블(SALES.TB_HIDDEN)의 컬럼 1개(빠져야 함)
 */
class ErwinXmlExporterTest {

    private static List<StdDataModelObjVo> objs;
    private static List<StdDataModelAttrVo> attrs;
    private static String xml;
    private static ErwinParseResult parsed;

    @BeforeAll
    static void exportAndParse() throws Exception {
        objs = Arrays.asList(
                obj("SALES", "TB_ORDER", "주문", "주문 테이블", "주문 한 건"),
                obj("HR", "TB_ORDER", "인사 주문", null, null),
                obj("HR", "TB_DEPT", "부서", null, null),
                obj("", "TB_CODE", null, "코드", null),
                obj("SALES", "TB_EMPTY", "빈 테이블", null, null));

        attrs = new ArrayList<>();
        // SALES.TB_ORDER — 복합 PK, NUMERIC(10,2), 기본값, 다른 소유자 부모 FK, 한글명 없는 컬럼
        attrs.add(attr("SALES", "TB_ORDER", "ORDER_NO", "주문번호", "VARCHAR", 20, 0, "N", "Y", "N", null, null, null, null, "주문 번호"));
        attrs.add(attr("SALES", "TB_ORDER", "SEQ_NO", "순번", "NUMERIC", 5, 0, "N", "Y", "N", null, null, null, null, null));
        attrs.add(attr("SALES", "TB_ORDER", "AMT", null, "NUMERIC", 10, 2, "Y", "N", "N", null, null, null, "0", null));
        attrs.add(attr("SALES", "TB_ORDER", "DEPT_CD", "부서코드", "VARCHAR", 10, 0, null, "N", "Y", "HR", "TB_DEPT", "DEPT_CD", null, null));
        attrs.add(attr("SALES", "TB_ORDER", "USE_YN", "사용여부", "CHAR", 1, 0, "N", "N", "N", null, null, null, "'N'", null));
        // 보이지 않는 테이블의 컬럼 — 빠져야 한다
        attrs.add(attr("SALES", "TB_HIDDEN", "X", "엑스", "VARCHAR", 1, 0, "Y", "N", "N", null, null, null, null, null));
        // HR.TB_ORDER — 같은 이름 다른 소유자
        attrs.add(attr("HR", "TB_ORDER", "REG_DT", "등록일시", "TIMESTAMP", 0, 0, "N", "N", "N", null, null, null, "CURRENT_TIMESTAMP", null));
        attrs.add(attr("HR", "TB_DEPT", "DEPT_CD", "부서코드", "VARCHAR", 10, 0, "N", "Y", "N", null, null, null, null, null));
        // 빈 소유자 — 부모도 빈 소유자
        attrs.add(attr("", "TB_CODE", "CD", "코드", "VARCHAR", 10, 0, "N", "Y", "Y", "", "TB_CODE", "CD", null, null));

        xml = ErwinXmlExporter.export("시험 모델", "2026-10-31 12:00:00", objs, attrs);
        parsed = ErwinXmlParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("파일 뼈대")
    class Skeleton {

        @Test
        @DisplayName("erwin > model > entities > entity id > attributes > attribute id")
        void skeleton() {
            assertTrue(xml.contains("<erwin exported_at=\"2026-10-31 12:00:00\" exported_by=\"NavidM Meta\">")
                    || xml.contains("<erwin exported_by=\"NavidM Meta\" exported_at=\"2026-10-31 12:00:00\">"), xml);
            assertTrue(xml.contains("<model name=\"시험 모델\">"), xml);
            assertTrue(xml.contains("<entities>"), xml);
            assertTrue(xml.contains("<entity id=\"E1\">"), xml);
            assertTrue(xml.contains("<entity id=\"E5\">"), xml);
            assertFalse(xml.contains("<entity id=\"E6\">"), xml);
            assertTrue(xml.contains("<attribute id=\"A1\">"), xml);
            assertTrue(xml.contains("<attribute id=\"A8\">"), xml);
            assertFalse(xml.contains("<attribute id=\"A9\">"), xml);
        }

        @Test
        @DisplayName("attribute id 는 파일 안 순서대로 — 컬럼 목록에서 같은 이름 두 소유자 테이블의 컬럼이 번갈아 와도")
        void attributeIdsInDocumentOrder() throws Exception {
            // 실제 쿼리는 OBJ_NM, ATTR_ORD 순이라 A.T·B.T 의 컬럼이 번갈아 온다
            List<StdDataModelAttrVo> mixed = Arrays.asList(
                    attr("A", "T", "A1", null, "INTEGER", 0, 0, "N", "Y", "N", null, null, null, null, null),
                    attr("B", "T", "B1", null, "INTEGER", 0, 0, "N", "Y", "N", null, null, null, null, null),
                    attr("A", "T", "A2", null, "INTEGER", 0, 0, "Y", "N", "N", null, null, null, null, null),
                    attr("B", "T", "B2", null, "INTEGER", 0, 0, "Y", "N", "N", null, null, null, null, null));
            String x = ErwinXmlExporter.export("m", "t", Arrays.asList(obj("A", "T", null, null, null), obj("B", "T", null, null, null)), mixed);
            int a1 = x.indexOf("<physical_name>A1<"), a2 = x.indexOf("<physical_name>A2<");
            int b1 = x.indexOf("<physical_name>B1<"), b2 = x.indexOf("<physical_name>B2<");
            assertTrue(a1 < a2 && a2 < b1 && b1 < b2, x);
            assertTrue(x.lastIndexOf("<attribute id=\"A2\">", a2) > a1, x);   // A.T 의 둘째 컬럼이 A2
            assertTrue(x.lastIndexOf("<attribute id=\"A3\">", b1) > a2, x);   // B.T 의 첫 컬럼이 A3
        }

        @Test
        @DisplayName("빈 소유자는 빈 태그로 쓴다")
        void emptyOwnerTag() {
            assertTrue(xml.contains("<owner/>"), xml);
        }

        @Test
        @DisplayName("한글명이 없으면 name 태그를 쓰지 않는다")
        void noKoreanName() {
            int amt = xml.indexOf("<physical_name>AMT</physical_name>");
            int open = xml.lastIndexOf("<attribute id=", amt);
            assertFalse(xml.substring(open, amt).contains("<name>"), xml);
        }
    }

    @Nested
    @DisplayName("내보내기 → 파싱")
    class RoundTrip {

        @Test
        @DisplayName("테이블 수 5·컬럼 수 8 (보이지 않는 테이블의 컬럼은 빠짐)")
        void counts() {
            assertEquals(5, parsed.getTableCount());
            assertEquals(8, parsed.getColumnCount());
            assertNull(findColumn("SALES", "TB_HIDDEN", "X"));
        }

        @Test
        @DisplayName("테이블 칸이 모두 같고 순서도 같다")
        void tables() {
            List<Map<String, Object>> tables = parsed.getTables();
            for (int i = 0; i < objs.size(); i++) {
                StdDataModelObjVo o = objs.get(i);
                Map<String, Object> t = tables.get(i);
                assertEquals(o.getObjOwner(), t.get("objOwner"), "owner " + i);
                assertEquals(o.getObjNm(), t.get("objNm"), "objNm " + i);
                assertEquals(o.getObjNmKr(), t.get("objNmKr"), "objNmKr " + i);
                assertEquals(o.getObjComment(), t.get("objComment"), "objComment " + i);
                assertEquals(o.getObjDesc(), t.get("objDesc"), "objDesc " + i);
            }
            assertEquals(5, tables.get(0).get("objAttrCnt"));
            assertEquals(1, tables.get(1).get("objAttrCnt"));
            assertEquals(0, tables.get(4).get("objAttrCnt"));
        }

        @Test
        @DisplayName("컬럼 칸이 모두 같다")
        void columns() {
            for (StdDataModelAttrVo a : attrs) {
                if ("TB_HIDDEN".equals(a.getObjNm())) continue;
                Map<String, Object> c = findColumn(a.getObjOwner(), a.getObjNm(), a.getAttrNm());
                String k = a.getObjOwner() + "." + a.getObjNm() + "." + a.getAttrNm();
                assertNotNull(c, k);
                assertEquals(a.getAttrNmKr(), c.get("attrNmKr"), k);
                assertEquals(a.getDataType(), c.get("dataType"), k);
                assertEquals(a.getDataLen(), c.get("dataLen"), k);
                assertEquals(a.getDataDecimalLen(), c.get("dataDecimalLen"), k);
                assertEquals("Y".equals(a.getNullableYn()) ? "Y" : "N", c.get("nullableYn"), k);
                assertEquals(a.getPkYn(), c.get("pkYn"), k);
                assertEquals(a.getFkYn(), c.get("fkYn"), k);
                assertEquals(a.getFkParentObjOwner(), c.get("fkParentObjOwner"), k);
                assertEquals(a.getFkParentObjNm(), c.get("fkParentObjNm"), k);
                assertEquals(a.getFkParentAttrNm(), c.get("fkParentAttrNm"), k);
                assertEquals(a.getDefaultVal(), c.get("defaultVal"), k);
                assertEquals(a.getAttrComment(), c.get("attrComment"), k);
            }
        }

        @Test
        @DisplayName("컬럼 순서는 테이블 안에서 나온 순서")
        void order() {
            assertEquals((short) 1, findColumn("SALES", "TB_ORDER", "ORDER_NO").get("attrOrder"));
            assertEquals((short) 5, findColumn("SALES", "TB_ORDER", "USE_YN").get("attrOrder"));
            assertEquals((short) 1, findColumn("HR", "TB_ORDER", "REG_DT").get("attrOrder"));
        }

        @Test
        @DisplayName("NULL 허용이 비어 있으면 N (DDL 은 'Y' 가 아니면 NOT NULL)")
        void nullableNull() {
            assertEquals("N", findColumn("SALES", "TB_ORDER", "DEPT_CD").get("nullableYn"));
        }

        @Test
        @DisplayName("같은 이름 테이블의 컬럼이 섞이지 않는다")
        void sameNameTables() {
            assertNull(findColumn("HR", "TB_ORDER", "ORDER_NO"));
            assertNull(findColumn("SALES", "TB_ORDER", "REG_DT"));
        }
    }

    @Nested
    @DisplayName("오류·옛 형식")
    class Errors {

        @Test
        @DisplayName("소수점이 숫자가 아니면 한국어 문구")
        void scaleNotNumber() {
            String bad = "<erwin><model><entities><entity id=\"E1\"><physical_name>T1</physical_name><attributes>"
                    + "<attribute id=\"A1\"><physical_name>C1</physical_name><datatype>NUMERIC</datatype>"
                    + "<length>10</length><scale>x</scale></attribute></attributes></entity></entities></model></erwin>";
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ErwinXmlParser.parse(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))));
            assertEquals("길이·소수점은 숫자여야 합니다: T1.C1 — 'x'", e.getMessage());
        }

        @Test
        @DisplayName("길이가 숫자가 아니면 한국어 문구")
        void lengthNotNumber() {
            String bad = "<erwin><entity><physical_name>T1</physical_name>"
                    + "<attribute><physical_name>C1</physical_name><length>1O</length></attribute></entity></erwin>";
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ErwinXmlParser.parse(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))));
            assertEquals("길이·소수점은 숫자여야 합니다: T1.C1 — '1O'", e.getMessage());
        }

        @Test
        @DisplayName("옛 형식(견본)은 소유자 null·FK N·소수점 0")
        void legacySample() throws Exception {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("test_erwin_sample.xml")) {
                ErwinParseResult r = ErwinXmlParser.parse(is);
                for (Map<String, Object> t : r.getTables()) {
                    assertNull(t.get("objOwner"));
                    assertNull(t.get("objComment"));
                    assertNull(t.get("objDesc"));
                }
                for (Map<String, Object> c : r.getColumns()) {
                    assertNull(c.get("objOwner"));
                    assertEquals("N", c.get("fkYn"));
                    assertEquals((short) 0, c.get("dataDecimalLen"));
                    assertNull(c.get("fkParentObjOwner"));
                    assertNull(c.get("fkParentObjNm"));
                    assertNull(c.get("defaultVal"));
                    assertNull(c.get("attrComment"));
                }
            }
        }
    }

    // ========== 헬퍼 ==========

    private static StdDataModelObjVo obj(String owner, String nm, String nmKr, String comment, String desc) {
        StdDataModelObjVo o = new StdDataModelObjVo();
        o.setObjOwner(owner);
        o.setObjNm(nm);
        o.setObjNmKr(nmKr);
        o.setObjComment(comment);
        o.setObjDesc(desc);
        return o;
    }

    private static StdDataModelAttrVo attr(String owner, String obj, String nm, String nmKr, String type, long len, int dec,
            String nullable, String pk, String fk, String pOwner, String pObj, String pAttr, String dflt, String comment) {
        StdDataModelAttrVo a = new StdDataModelAttrVo();
        a.setObjOwner(owner);
        a.setObjNm(obj);
        a.setAttrNm(nm);
        a.setAttrNmKr(nmKr);
        a.setDataType(type);
        a.setDataLen(len);
        a.setDataDecimalLen((short) dec);
        a.setNullableYn(nullable);
        a.setPkYn(pk);
        a.setFkYn(fk);
        a.setFkParentObjOwner(pOwner);
        a.setFkParentObjNm(pObj);
        a.setFkParentAttrNm(pAttr);
        a.setDefaultVal(dflt);
        a.setAttrComment(comment);
        return a;
    }

    private static Map<String, Object> findColumn(String owner, String obj, String attr) {
        return parsed.getColumns().stream()
                .filter(c -> owner.equals(c.get("objOwner")) && obj.equals(c.get("objNm")) && attr.equals(c.get("attrNm")))
                .findFirst()
                .orElse(null);
    }
}
