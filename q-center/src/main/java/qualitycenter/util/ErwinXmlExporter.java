package qualitycenter.util;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDataModelObjVo;

/**
 * 104번 WP8-40 — 데이터 모델 → ERwin XML 내보내기 (108 §4.25).
 *
 * <p>이 제품의 ERwin XML 가져오기(ErwinXmlParser)가 읽는 형식 그대로 쓴다 — 시험 견본(test_erwin_sample.xml)과 같은 뼈대
 * erwin > model > entities > entity > attributes > attribute. 가져오기가 함께 읽는 항목(owner·comment·definition·scale·fk·
 * parent_owner·parent_entity·parent_attribute·default)을 더해 내보낸 파일을 다시 가져오면 같은 테이블·컬럼·PK·FK 가 된다.
 * ERwin Data Modeler 에서 직접 여는 것은 ERwin 이 없어 확인하지 못했다(108 보류 §2-26).</p>
 */
public class ErwinXmlExporter {

	/**
	 * objs: 테이블 목록(이 순서로 entity — 컬럼 없는 테이블 포함), attrs: 컬럼 목록(이 순서로 같은 (소유자, 테이블) entity 에 붙인다).
	 * 테이블 목록에 없는 (소유자, 테이블)의 컬럼은 뺀다.
	 */
	public static String export(String modelName, String exportedAt, List<StdDataModelObjVo> objs, List<StdDataModelAttrVo> attrs) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("erwin");
		root.setAttribute("exported_by", "NavidM Meta");
		root.setAttribute("exported_at", exportedAt);
		doc.appendChild(root);
		Element model = doc.createElement("model");
		model.setAttribute("name", modelName);
		root.appendChild(model);
		Element entities = doc.createElement("entities");
		model.appendChild(entities);

		Map<String, Element> attrLists = new LinkedHashMap<>();
		int e = 0;
		for (StdDataModelObjVo o : objs) {
			String owner = o.getObjOwner() == null ? "" : o.getObjOwner();
			Element entity = doc.createElement("entity");
			entity.setAttribute("id", "E" + (++e));
			text(doc, entity, "name", o.getObjNmKr());
			text(doc, entity, "physical_name", o.getObjNm());
			tag(doc, entity, "owner", owner);
			text(doc, entity, "comment", o.getObjComment());
			text(doc, entity, "definition", o.getObjDesc());
			Element list = doc.createElement("attributes");
			entity.appendChild(list);
			entities.appendChild(entity);
			attrLists.put(owner + "|" + o.getObjNm(), list);
		}

		for (StdDataModelAttrVo c : attrs) {
			Element list = attrLists.get((c.getObjOwner() == null ? "" : c.getObjOwner()) + "|" + c.getObjNm());
			if (list == null) continue;
			Element attr = doc.createElement("attribute");
			text(doc, attr, "name", c.getAttrNmKr());
			text(doc, attr, "physical_name", c.getAttrNm());
			text(doc, attr, "datatype", c.getDataType());
			if (c.getDataLen() > 0) text(doc, attr, "length", String.valueOf(c.getDataLen()));
			if (c.getDataDecimalLen() > 0) text(doc, attr, "scale", String.valueOf(c.getDataDecimalLen()));
			// DDL 은 NULLABLE_YN 이 'Y' 가 아니면 NOT NULL 로 본다
			text(doc, attr, "nullable", "Y".equals(c.getNullableYn()) ? "Y" : "N");
			text(doc, attr, "pk", "Y".equals(c.getPkYn()) ? "Y" : "N");
			text(doc, attr, "fk", "Y".equals(c.getFkYn()) ? "Y" : "N");
			if (c.getFkParentObjNm() != null && !c.getFkParentObjNm().isEmpty() && c.getFkParentObjOwner() != null)
				tag(doc, attr, "parent_owner", c.getFkParentObjOwner());
			text(doc, attr, "parent_entity", c.getFkParentObjNm());
			text(doc, attr, "parent_attribute", c.getFkParentAttrNm());
			text(doc, attr, "default", c.getDefaultVal());
			text(doc, attr, "comment", c.getAttrComment());
			list.appendChild(attr);
		}
		// id 는 파일 안 순서대로 — 컬럼 목록은 테이블 이름·순서로 정렬돼 같은 이름 두 소유자 테이블의 컬럼이 번갈아 온다
		NodeList all = doc.getElementsByTagName("attribute");
		for (int i = 0; i < all.getLength(); i++) ((Element) all.item(i)).setAttribute("id", "A" + (i + 1));

		Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		t.setOutputProperty(OutputKeys.INDENT, "yes");
		t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		StringWriter w = new StringWriter();
		t.transform(new DOMSource(doc), new StreamResult(w));
		return w.toString();
	}

	/** 값이 있을 때만 자식 태그를 단다 */
	private static void text(Document doc, Element parent, String tag, String v) {
		if (v == null || v.isEmpty()) return;
		tag(doc, parent, tag, v);
	}

	/** 빈 값이어도 태그를 단다 — 빈 소유자(빈 태그)와 소유자 없음(태그 없음 = 가져오기 기본 소유자)을 가른다 */
	private static void tag(Document doc, Element parent, String tag, String v) {
		Element el = doc.createElement(tag);
		el.setTextContent(v);
		parent.appendChild(el);
	}
}
