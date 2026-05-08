package qualitycenter.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 85번 — OMG XMI 2.1 (UML 2.x) 파서.
 *
 * RFP SFR-22 항목 C "표준 포맷 추출·변환·적재" 의 표준 포맷 구현.
 * ERwin 9.x+ / Enterprise Architect / Visual Paradigm / Modelio / DA# 등
 * 모든 모델링 도구의 XMI 2.x export 와 호환.
 *
 * <p>지원 구조 (1차 POC):
 * <ul>
 *   <li>root: &lt;xmi:XMI&gt; 또는 &lt;uml:Model&gt;</li>
 *   <li>클래스: &lt;packagedElement xmi:type="uml:Class"&gt; → 테이블</li>
 *   <li>속성: &lt;ownedAttribute xmi:type="uml:Property"&gt; → 컬럼</li>
 *   <li>타입: type 속성 (id 참조) 또는 자식 &lt;type href="..."&gt; PrimitiveType</li>
 *   <li>중첩 패키지(&lt;packagedElement xmi:type="uml:Package"&gt;) 재귀 탐색</li>
 * </ul>
 *
 * <p>기존 {@link ErwinXmlParser} (ERwin native XML, entity/attribute 태그) 와 별개.
 * 두 파서는 화면에서 포맷 옵션으로 선택.
 */
public class XmiParser {

    /** XMI 표준 namespace prefix (대부분 'xmi', 'uml' 이지만 다른 prefix 도 허용). */
    private static final String XMI_TYPE_CLASS    = "uml:Class";
    private static final String XMI_TYPE_PROPERTY = "uml:Property";
    private static final String XMI_TYPE_PACKAGE  = "uml:Package";

    public static XmiParseResult parse(InputStream xmlInput) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE 방지
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlInput);
        doc.getDocumentElement().normalize();

        List<Map<String, Object>> tables = new ArrayList<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        // 클래스 id → name 매핑 (ownedAttribute type 참조 해석용)
        Map<String, String> classIdToName = new HashMap<>();

        Element root = doc.getDocumentElement();

        // 1단계: 모든 uml:Class 의 id → name 사전 수집 (type 참조 역추적)
        collectClassIdMap(root, classIdToName);

        // 2단계: uml:Class 추출 + ownedAttribute 추출
        collectClasses(root, tables, columns, classIdToName);

        XmiParseResult result = new XmiParseResult();
        result.setTables(tables);
        result.setColumns(columns);
        result.setTableCount(tables.size());
        result.setColumnCount(columns.size());
        return result;
    }

    /** 모든 packagedElement[type=uml:Class] 의 id → name 사전 수집 (재귀). */
    private static void collectClassIdMap(Node node, Map<String, String> out) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            if (isPackagedElementOfType(el, XMI_TYPE_CLASS)) {
                String id   = getXmiId(el);
                String name = el.getAttribute("name");
                if (id != null && !id.isEmpty() && name != null && !name.isEmpty()) {
                    out.put(id, name);
                }
            }
            // 재귀 — 패키지/모델 안의 클래스도 잡기
            collectClassIdMap(el, out);
        }
    }

    /** uml:Class 의 ownedAttribute 추출 → 테이블/컬럼 list 추가 (재귀). */
    private static void collectClasses(Node node,
                                        List<Map<String, Object>> tables,
                                        List<Map<String, Object>> columns,
                                        Map<String, String> classIdToName) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            if (isPackagedElementOfType(el, XMI_TYPE_CLASS)) {
                addClassAsTable(el, tables, columns, classIdToName);
            } else if (isPackagedElementOfType(el, XMI_TYPE_PACKAGE)) {
                // 패키지 안 클래스도 재귀
                collectClasses(el, tables, columns, classIdToName);
            } else {
                // 그 외 노드도 재귀 (root <xmi:XMI> → <uml:Model> → <packagedElement> 흐름)
                collectClasses(el, tables, columns, classIdToName);
            }
        }
    }

    private static void addClassAsTable(Element classEl,
                                         List<Map<String, Object>> tables,
                                         List<Map<String, Object>> columns,
                                         Map<String, String> classIdToName) {
        String tableName = classEl.getAttribute("name");
        if (tableName == null || tableName.isEmpty()) return;

        Map<String, Object> table = new HashMap<>();
        table.put("objNm", tableName);              // 물리명 = name (XMI 표준)
        table.put("objNmKr", tableName);            // 별도 한글명 없음 — 동일값
        // 컬럼 카운트
        int attrCnt = 0;
        NodeList directChildren = classEl.getChildNodes();
        for (int i = 0; i < directChildren.getLength(); i++) {
            Node n = directChildren.item(i);
            if (!(n instanceof Element)) continue;
            Element child = (Element) n;
            if ("ownedAttribute".equals(child.getLocalName() != null ? child.getLocalName() : child.getTagName())
                && XMI_TYPE_PROPERTY.equals(getXmiType(child))) {
                addAttributeAsColumn(child, tableName, attrCnt + 1, columns, classIdToName);
                attrCnt++;
            }
        }
        table.put("objAttrCnt", attrCnt);
        tables.add(table);
    }

    private static void addAttributeAsColumn(Element attrEl, String tableName, int order,
                                              List<Map<String, Object>> columns,
                                              Map<String, String> classIdToName) {
        String attrName = attrEl.getAttribute("name");
        if (attrName == null || attrName.isEmpty()) return;

        Map<String, Object> col = new HashMap<>();
        col.put("objNm", tableName);
        col.put("objNmKr", tableName);
        col.put("attrNm", attrName);
        col.put("attrNmKr", attrName);

        // 데이터타입 — 우선순위:
        // 1) 자식 <type href="...#String"> PrimitiveType → href 의 # 뒤 텍스트
        // 2) type 속성 (id 참조) → classIdToName 또는 raw
        String dataType = resolveDataType(attrEl, classIdToName);
        col.put("dataType", dataType != null ? dataType : "VARCHAR");
        col.put("dataLen", 0L);     // XMI 에 길이 정보 없음 (tagged value 로 가능하나 1차 POC 미지원)

        // nullable: lowerValue 가 0이면 null 허용
        // upperValue 가 *(또는 -1) 이면 multi — 무관
        col.put("nullableYn", parseNullable(attrEl) ? "Y" : "N");

        // PK 여부: XMI 표준엔 직접 표현 X. tagged value 또는 stereotype 으로 가능.
        // 1차 POC: stereotype 'PK' / 'PrimaryKey' 또는 isID 속성 체크
        col.put("pkYn", parsePk(attrEl) ? "Y" : "N");

        col.put("attrOrder", (short) order);
        columns.add(col);
    }

    /** type 자식의 href 또는 type 속성으로 데이터타입 추론. */
    private static String resolveDataType(Element attrEl, Map<String, String> classIdToName) {
        // 1) 자식 <type href="...#String">
        NodeList kids = attrEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element)) continue;
            Element child = (Element) n;
            String localName = child.getLocalName() != null ? child.getLocalName() : child.getTagName();
            if (!"type".equals(localName)) continue;
            String href = child.getAttribute("href");
            if (href != null && !href.isEmpty()) {
                int hash = href.indexOf('#');
                return hash >= 0 ? href.substring(hash + 1) : href;
            }
            // 또는 type 의 xmi:type 속성으로 PrimitiveType 분기
            String t = getXmiType(child);
            if (t != null) return t;
        }
        // 2) type 속성 (id 참조)
        String typeAttr = attrEl.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String resolved = classIdToName.get(typeAttr);
            return resolved != null ? resolved : typeAttr;
        }
        return null;
    }

    /** lowerValue 자식 element 의 value 가 0 이면 nullable. 없으면 default 1 (NOT NULL 기본 X — 보수적 Y 처리). */
    private static boolean parseNullable(Element attrEl) {
        NodeList kids = attrEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element)) continue;
            Element child = (Element) n;
            String localName = child.getLocalName() != null ? child.getLocalName() : child.getTagName();
            if (!"lowerValue".equals(localName)) continue;
            String v = child.getAttribute("value");
            return "0".equals(v);
        }
        return true; // 기본은 nullable
    }

    /** PK 여부 — isID 속성 또는 stereotype 텍스트. (XMI 표준엔 직접 PK 표현 없음 — vendor extension 의존) */
    private static boolean parsePk(Element attrEl) {
        // UML 2.5 의 isID 속성 (모든 도구가 쓰진 않음)
        String isId = attrEl.getAttribute("isID");
        if ("true".equalsIgnoreCase(isId)) return true;
        // 이름이 ID/PK 로 시작 — 휴리스틱
        String name = attrEl.getAttribute("name");
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.equals("id") || lower.endsWith("_id") || lower.startsWith("pk_")) {
                // 이름 휴리스틱은 hint 만 — false 유지하고 사용자가 화면에서 보정
                return false;
            }
        }
        return false;
    }

    private static boolean isPackagedElementOfType(Element el, String xmiType) {
        String localName = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
        if (!"packagedElement".equals(localName)) return false;
        String t = getXmiType(el);
        return xmiType.equals(t);
    }

    /** xmi:type 속성 — namespace-aware 또는 prefixed 형태 모두 처리. */
    private static String getXmiType(Element el) {
        // namespace-aware 우선
        String t = el.getAttributeNS("http://schema.omg.org/spec/XMI/2.1", "type");
        if (t == null || t.isEmpty()) t = el.getAttributeNS("http://www.omg.org/spec/XMI/20131001", "type");
        if (t == null || t.isEmpty()) t = el.getAttribute("xmi:type");
        return t == null || t.isEmpty() ? null : t;
    }

    /** xmi:id 속성 — namespace-aware. */
    private static String getXmiId(Element el) {
        String id = el.getAttributeNS("http://schema.omg.org/spec/XMI/2.1", "id");
        if (id == null || id.isEmpty()) id = el.getAttributeNS("http://www.omg.org/spec/XMI/20131001", "id");
        if (id == null || id.isEmpty()) id = el.getAttribute("xmi:id");
        return id == null || id.isEmpty() ? null : id;
    }

    /**
     * XMI 파싱 결과 DTO. ErwinParseResult 와 동일 구조 (재사용).
     */
    public static class XmiParseResult {
        private List<Map<String, Object>> tables;
        private List<Map<String, Object>> columns;
        private int tableCount;
        private int columnCount;

        public List<Map<String, Object>> getTables() { return tables; }
        public void setTables(List<Map<String, Object>> tables) { this.tables = tables; }

        public List<Map<String, Object>> getColumns() { return columns; }
        public void setColumns(List<Map<String, Object>> columns) { this.columns = columns; }

        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }

        public int getColumnCount() { return columnCount; }
        public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
    }
}
