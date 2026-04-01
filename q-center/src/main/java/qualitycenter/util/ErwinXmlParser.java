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
import org.w3c.dom.NodeList;

/**
 * ERwin XML Export 형식의 파일을 파싱하여 테이블/컬럼 정보를 추출하는 유틸리티.
 */
public class ErwinXmlParser {

    /**
     * ERwin XML을 파싱하여 테이블/컬럼 목록을 반환한다.
     *
     * @param xmlInput ERwin XML InputStream
     * @return 파싱 결과 (테이블 목록, 컬럼 목록, 건수)
     */
    public static ErwinParseResult parse(InputStream xmlInput) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE 방지
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlInput);
        doc.getDocumentElement().normalize();

        List<Map<String, Object>> tables = new ArrayList<>();
        List<Map<String, Object>> columns = new ArrayList<>();

        NodeList entityNodes = doc.getElementsByTagName("entity");
        for (int i = 0; i < entityNodes.getLength(); i++) {
            Element entity = (Element) entityNodes.item(i);

            String logicalName = getTagValue(entity, "name");
            String physicalName = getTagValue(entity, "physical_name");

            Map<String, Object> table = new HashMap<>();
            table.put("objNm", physicalName);       // 물리명 (영문)
            table.put("objNmKr", logicalName);       // 논리명 (한글)

            // 속성(컬럼) 파싱
            NodeList attrNodes = entity.getElementsByTagName("attribute");
            int attrCnt = attrNodes.getLength();
            table.put("objAttrCnt", attrCnt);

            for (int j = 0; j < attrCnt; j++) {
                Element attr = (Element) attrNodes.item(j);

                Map<String, Object> col = new HashMap<>();
                col.put("objNm", physicalName);
                col.put("objNmKr", logicalName);
                col.put("attrNm", getTagValue(attr, "physical_name"));
                col.put("attrNmKr", getTagValue(attr, "name"));
                col.put("dataType", getTagValue(attr, "datatype"));

                String lenStr = getTagValue(attr, "length");
                col.put("dataLen", lenStr != null ? Long.parseLong(lenStr) : 0L);

                String nullable = getTagValue(attr, "nullable");
                col.put("nullableYn", "Y".equalsIgnoreCase(nullable) ? "Y" : "N");

                String pk = getTagValue(attr, "pk");
                col.put("pkYn", "Y".equalsIgnoreCase(pk) ? "Y" : "N");

                col.put("attrOrder", (short)(j + 1));

                columns.add(col);
            }

            tables.add(table);
        }

        ErwinParseResult result = new ErwinParseResult();
        result.setTables(tables);
        result.setColumns(columns);
        result.setTableCount(tables.size());
        result.setColumnCount(columns.size());
        return result;
    }

    /**
     * 엘리먼트의 직속 자식 태그 텍스트 값을 가져온다.
     * (getElementsByTagName은 하위 전체를 탐색하므로 직속 자식만 검색)
     */
    private static String getTagValue(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                Element el = (Element) nodes.item(i);
                if (tagName.equals(el.getTagName())) {
                    return el.getTextContent().trim();
                }
            }
        }
        return null;
    }

    /**
     * ERwin XML 파싱 결과를 담는 DTO.
     */
    public static class ErwinParseResult {
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
