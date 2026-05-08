package qualitycenter.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 85번 — DataQ 모델 → OMG XMI 2.1 (UML 2.x) export.
 *
 * RFP SFR-22 항목 C "표준 포맷 추출" 의 추출(Export) 구현.
 * ERwin 9.x+ / Enterprise Architect / Visual Paradigm / Modelio 가
 * import 가능한 표준 XMI 2.1 형태로 직렬화.
 *
 * <p>지원 매핑:
 * <ul>
 *   <li>테이블 → &lt;packagedElement xmi:type="uml:Class"&gt;</li>
 *   <li>컬럼 → &lt;ownedAttribute xmi:type="uml:Property"&gt;</li>
 *   <li>PK 컬럼 → isID="true"</li>
 *   <li>FK 컬럼 (fkYn=Y, fkParentObjNm 있음) → type="cls-{parent}" id 참조</li>
 *   <li>nullable=N → lowerValue=1, nullable=Y → lowerValue=0</li>
 *   <li>PrimitiveType → type href (Integer/String/Date 등)</li>
 * </ul>
 */
public class XmiExporter {

    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";

    /**
     * tables + columns → XMI 2.1 XML String.
     *
     * @param modelName 모델명 (uml:Model name)
     * @param tables    테이블 list (objNm 만 사용)
     * @param columns   컬럼 list (objNm/attrNm/dataType/nullableYn/pkYn/fkYn/fkParentObjNm/attrOrder)
     */
    public static String export(String modelName,
                                 List<Map<String, Object>> tables,
                                 List<Map<String, Object>> columns) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<xmi:XMI xmi:version=\"2.1\"\n");
        xml.append("         xmlns:uml=\"").append(UML_NS).append("\"\n");
        xml.append("         xmlns:xmi=\"").append(XMI_NS).append("\">\n");
        xml.append("  <xmi:Documentation exporter=\"DataQ\" exporterVersion=\"85.1\"/>\n");
        xml.append("  <uml:Model xmi:id=\"model-").append(slug(modelName))
           .append("\" name=\"").append(esc(modelName)).append("\">\n");

        // 테이블당 클래스 출력 (id = cls-{tableName})
        for (Map<String, Object> tbl : tables) {
            String tableName = str(tbl.get("objNm"));
            if (tableName.isEmpty()) continue;
            xml.append("    <packagedElement xmi:type=\"uml:Class\"")
               .append(" xmi:id=\"cls-").append(slug(tableName)).append("\"")
               .append(" name=\"").append(esc(tableName)).append("\">\n");

            // 해당 테이블의 컬럼들 (attrOrder 정렬)
            List<Map<String, Object>> cols = filterAndSort(columns, tableName);
            for (Map<String, Object> col : cols) {
                appendOwnedAttribute(xml, col, tableName);
            }
            xml.append("    </packagedElement>\n");
        }

        xml.append("  </uml:Model>\n");
        xml.append("</xmi:XMI>\n");
        return xml.toString();
    }

    private static void appendOwnedAttribute(StringBuilder xml,
                                              Map<String, Object> col,
                                              String tableName) {
        String attrName = str(col.get("attrNm"));
        if (attrName.isEmpty()) return;

        String pkYn       = strOr(col.get("pkYn"), "N");
        String fkYn       = strOr(col.get("fkYn"), "N");
        String nullableYn = strOr(col.get("nullableYn"), "Y");
        String dataType   = str(col.get("dataType"));
        String fkParent   = str(col.get("fkParentObjNm"));

        xml.append("      <ownedAttribute xmi:type=\"uml:Property\"")
           .append(" xmi:id=\"attr-").append(slug(tableName)).append("-")
           .append(slug(attrName)).append("\"")
           .append(" name=\"").append(esc(attrName)).append("\"");

        if ("Y".equals(pkYn)) xml.append(" isID=\"true\"");

        // FK 일 때: type 속성으로 다른 클래스 ID 참조
        if ("Y".equals(fkYn) && !fkParent.isEmpty()) {
            xml.append(" type=\"cls-").append(slug(fkParent)).append("\"");
            xml.append(">\n");
        } else {
            xml.append(">\n");
            // PrimitiveType 은 href 로 표현
            String prim = mapToPrimitive(dataType);
            xml.append("        <type xmi:type=\"uml:PrimitiveType\" href=\"")
               .append("pathmap://UML_LIBRARIES/UMLPrimitiveTypes.library.uml#")
               .append(esc(prim))
               .append("\"/>\n");
        }

        // lowerValue: nullable=N → 1, Y → 0
        String lower = "N".equals(nullableYn) ? "1" : "0";
        xml.append("        <lowerValue xmi:type=\"uml:LiteralInteger\" value=\"")
           .append(lower).append("\"/>\n");
        xml.append("        <upperValue xmi:type=\"uml:LiteralUnlimitedNatural\" value=\"1\"/>\n");

        xml.append("      </ownedAttribute>\n");
    }

    /** DataQ 의 자유로운 dataType 을 XMI PrimitiveType 으로 normalize. */
    private static String mapToPrimitive(String dataType) {
        if (dataType == null || dataType.isEmpty()) return "String";
        String t = dataType.toUpperCase();
        if (t.startsWith("INT") || t.startsWith("BIGINT") || t.startsWith("SMALLINT")
                || t.startsWith("NUMBER") || t.startsWith("NUMERIC") || t.startsWith("DECIMAL")) {
            return "Integer";
        }
        if (t.startsWith("FLOAT") || t.startsWith("DOUBLE") || t.startsWith("REAL")) {
            return "Real";
        }
        if (t.startsWith("BOOL")) return "Boolean";
        if (t.startsWith("DATE") || t.startsWith("TIMESTAMP") || t.startsWith("TIME")) {
            return "Date";
        }
        // 그 외 VARCHAR/CHAR/TEXT/CLOB 등은 String
        return "String";
    }

    private static List<Map<String, Object>> filterAndSort(List<Map<String, Object>> all,
                                                            String tableName) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> c : all) {
            if (tableName.equals(str(c.get("objNm")))) out.add(c);
        }
        out.sort((a, b) -> {
            int oa = parseShort(a.get("attrOrder"), 0);
            int ob = parseShort(b.get("attrOrder"), 0);
            return Integer.compare(oa, ob);
        });
        return out;
    }

    private static int parseShort(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (Exception e) { return def; }
        }
        return def;
    }

    private static String str(Object v) { return v == null ? "" : v.toString(); }
    private static String strOr(Object v, String def) {
        String s = str(v); return s.isEmpty() ? def : s;
    }

    /** XML 엔티티 이스케이프. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** xmi:id 안전 문자열 (영숫자/하이픈만). */
    private static String slug(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
