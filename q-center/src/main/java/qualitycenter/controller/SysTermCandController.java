package qualitycenter.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.service.StdDictService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP7-23 — 시스템 단위 표준 후보 (108 §4.29, 화면 표준화 도구 > 시스템 단위 표준 후보).
 *
 * <p>시스템(하위 시스템 포함)에 속한 모델들의 비표준 컬럼을 모아 <b>모델마다 그 모델 사전(상위 사전 포함)</b>으로 용어 후보를 찾고
 * ({@link DataModelController#suggestItems} — 모델 단위 [표준 후보 찾기]와 같은 규칙·점수, DM-073), 고른 용어를 테이블마다
 * 컬럼 저장({@link DataModelController#saveAttrs})으로 적용한다. 그래서 영문명 cascade·표준 재평가·표준 준수 필수 거부·변경 이력·
 * 결재(모델 직접 반영 권한이 없으면 승인 대기)가 컬럼 화면 [저장]과 같다. 구조 진단 현행화({@link StructDiagController#apply})처럼
 * 테이블 하나가 실패해도 나머지 테이블은 적용한다. 새 권한은 없다 — 컬럼 저장과 같은 권한(114 §1 권고 채택).</p>
 *
 * <p>{@code DataModelController} 가 3,100줄이 넘어 따로 둔다 (경로는 같은 {@code /api/dm}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/dm")
public class SysTermCandController {

	/** 한 번에 보이는·적용하는 컬럼 수 (모델 단위 suggestTerms 와 같음) */
	private static final int LIMIT = 2000;

	/** 후보 목록 행에 담는 컬럼 값 */
	private static final String[] ITEM_KEYS = { "dmId", "dmNm", "objOwner", "objNm", "objNmKr", "attrNm", "attrNmKr",
			"dataType", "dataLen", "dataDecimalLen", "aprvStatus" };

	@Autowired private DataModelController dataModelController;
	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private StdDictService dictService;

	/**
	 * 104번 WP7-23 (108 §4.29) — 시스템(하위 포함) 모델들의 비표준 컬럼과 용어 후보.
	 *
	 * <p>대상은 표준이 Y 가 아니고 한글명이 있고, 컬럼·테이블이 사용 중이고, 테이블·컬럼 모두 표준 진단 대상이며(114 §1 권고 채택),
	 * 화면에 보이는({@link DataModelController#visibleTo}) 컬럼이다. 같은 사전을 쓰는 모델끼리 묶어 후보를 찾고, 후보 용어가
	 * 모델 사전이 아닌 상위 사전의 것이면 {@code inheritedDictNm} 을 붙인다. 컬럼은 앞 2,000개만 돌려준다(total 은 거른 뒤 전체).</p>
	 *
	 * @param body {sysCd, dmIds?:[], objNm?, attrNm?} — dmIds 중 그 시스템 밖 모델은 무시, 비면 전체
	 * @return contents {models:[{dmId, dmNm, sysCd, sysNm, stndReqYn, dictId, dictNm, nonStdCnt}],
	 *         items:[{dmId, dmNm, objOwner, objNm, objNmKr, attrNm, attrNmKr, dataType, dataLen, dataDecimalLen, aprvStatus, candidates}],
	 *         total, limit}
	 */
	@PostMapping("/suggestTermsBySys")
	public Mono<Response> suggestTermsBySys(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			String sysCd = (String) body.get("sysCd");
			if (sysCd == null || sysCd.trim().isEmpty()) throw new IllegalArgumentException("시스템을 고르세요.");
			List<?> pick = body.get("dmIds") instanceof List ? (List<?>) body.get("dmIds") : Collections.emptyList();

			Map<String, String> dictNms = new HashMap<>();
			for (Map<String, Object> d : dictService.list()) dictNms.put((String) d.get("dictId"), (String) d.get("dictNm"));

			Map<String, Object> mp = new HashMap<>();
			mp.put("sysCd", sysCd);
			List<Map<String, Object>> models = new ArrayList<>();
			Map<String, Map<String, Object>> modelById = new HashMap<>();
			for (Map<String, Object> m : sqlSessionTemplate.<Map<String, Object>>selectList("datamodel.selectModelsBySys", mp)) {
				if (!pick.isEmpty() && !pick.contains(m.get("dmId"))) continue;
				String dictId = dictService.resolve(null, (String) m.get("dmId"));
				m.put("dictId", dictId);
				m.put("dictNm", dictNms.get(dictId));
				m.put("nonStdCnt", 0);
				models.add(m);
				modelById.put((String) m.get("dmId"), m);
			}

			List<Map<String, Object>> attrs = new ArrayList<>();
			if (!models.isEmpty()) {
				Map<String, Object> ap = new HashMap<>();
				ap.put("dmIds", new ArrayList<>(modelById.keySet()));
				ap.put("objNm", body.get("objNm"));
				ap.put("attrNm", body.get("attrNm"));
				String userId = sessionService.getUserId();
				for (Map<String, Object> a : sqlSessionTemplate.<Map<String, Object>>selectList("datamodel.selectNonStdAttrsByDmIds", ap)) {
					if (!DataModelController.visibleTo((String) a.get("aprvStatus"), (String) a.get("requesterUserId"), userId)) continue;
					Map<String, Object> m = modelById.get(a.get("dmId"));
					m.put("nonStdCnt", (Integer) m.get("nonStdCnt") + 1);
					attrs.add(a);
				}
			}
			int total = attrs.size();
			List<Map<String, Object>> shown = total > LIMIT ? attrs.subList(0, LIMIT) : attrs;

			// 같은 사전을 쓰는 모델끼리 묶어 후보를 찾는다 (사전마다 단어 분석 한 번)
			Map<String, List<Integer>> byDict = new LinkedHashMap<>();
			for (int i = 0; i < shown.size(); i++) {
				byDict.computeIfAbsent((String) modelById.get(shown.get(i).get("dmId")).get("dictId"), k -> new ArrayList<>()).add(i);
			}
			List<Object> cands = new ArrayList<>(Collections.nCopies(shown.size(), null));
			for (Map.Entry<String, List<Integer>> e : byDict.entrySet()) {
				String dictId = e.getKey();
				String own = dictService.lineage(dictId).get(0);   // 모델 사전 (없으면 기본 사전 — 후보도 기본 사전부터 찾는다)
				List<Map<String, Object>> in = new ArrayList<>();
				for (int i : e.getValue()) in.add(shown.get(i));
				List<Map<String, Object>> found = dataModelController.suggestItems(dictId, in);
				for (int k = 0; k < found.size(); k++) {
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> cs = (List<Map<String, Object>>) found.get(k).get("candidates");
					for (Map<String, Object> c : cs) {
						if (!Objects.equals(c.get("dictId"), own)) c.put("inheritedDictNm", dictNms.get(c.get("dictId")));
					}
					cands.set(e.getValue().get(k), cs);
				}
			}

			List<Map<String, Object>> items = new ArrayList<>();
			for (int i = 0; i < shown.size(); i++) {
				Map<String, Object> a = shown.get(i);
				Map<String, Object> it = new LinkedHashMap<>();
				for (String k : ITEM_KEYS) it.put(k, a.get(k));
				it.put("candidates", cands.get(i));
				items.add(it);
			}

			// 104번 WP7-23 리뷰 — 보이는 행의 테이블마다 모든 컬럼 영문명. 화면 영문명 겹침 검사가 목록에 없는 같은 테이블 컬럼
			// (표준 Y·한글명 없음·표준 진단 제외·남의 미승인·삭제 대기)과의 겹침도 본다 — 컬럼 저장이 그 겹침으로 테이블째 거부한다
			Map<String, List<String>> tableAttrs = new LinkedHashMap<>();
			Map<String, Map<String, Object>> tbls = new LinkedHashMap<>();
			for (Map<String, Object> a : shown) {
				String key = a.get("dmId") + "|" + a.get("objOwner") + "|" + a.get("objNm");
				if (tbls.containsKey(key)) continue;
				Map<String, Object> t = new HashMap<>();
				t.put("dmId", a.get("dmId"));
				t.put("objOwner", a.get("objOwner"));
				t.put("objNm", a.get("objNm"));
				tbls.put(key, t);
				tableAttrs.put(key, new ArrayList<>());
			}
			if (!tbls.isEmpty()) {
				Map<String, Object> tp = new HashMap<>();
				List<Object> tblDmIds = new ArrayList<>();
				for (Map<String, Object> t : tbls.values()) if (!tblDmIds.contains(t.get("dmId"))) tblDmIds.add(t.get("dmId"));
				tp.put("dmIds", tblDmIds);
				tp.put("tables", new ArrayList<>(tbls.values()));
				for (Map<String, Object> n : sqlSessionTemplate.<Map<String, Object>>selectList("datamodel.selectAttrNmsByTables", tp)) {
					tableAttrs.get(n.get("dmId") + "|" + n.get("objOwner") + "|" + n.get("objNm")).add((String) n.get("attrNm"));
				}
			}

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("models", models);
			data.put("items", items);
			data.put("tableAttrs", tableAttrs);
			data.put("total", total);
			data.put("limit", LIMIT);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new ObjectMapper().writeValueAsString(data));
		} catch (Exception e) {
			log.error(">> suggestTermsBySys failed: {}", e.getMessage());
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * 104번 WP7-23 (108 §4.29) — 고른 용어를 컬럼에 적용한다. (모델, 소유자, 테이블)마다 컬럼 저장 요청 하나 — 테이블 하나가
	 * 한 트랜잭션이고, 실패한 테이블은 errors 에 담고 다음 테이블로 간다(114 §1 권고 채택).
	 *
	 * 고른 용어로 바뀌는 값이 없는 행(영문명 대소문자 무시·한글명·타입·길이·소수가 같음)은 컬럼 저장으로 보내지 않고 표준 여부만 다시 판정한다
	 * (rechecked — 이력 없음, 표준이 아니면 notStd 에 이유).
	 *
	 * @param body {items:[{dmId, objOwner, objNm, attrNm, attrNmKr(조회 때 한글명), termsId}]} — 2,000개까지
	 * @return contents {applied(적용한 테이블의 컬럼 수 — rechecked 포함), tables(적용한 테이블 수), rechecked(표준 여부만 다시 판정한 컬럼 수),
	 *         notStd:[{dmId, objOwner, objNm, attrNm, message}](다시 판정해도 표준이 아닌 컬럼), errors:[{dmId, dmNm, objOwner, objNm, message}]}
	 */
	@PostMapping("/applyTermsBySys")
	public Mono<Response> applyTermsBySys(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> items = body.get("items") instanceof List ? (List<Map<String, Object>>) body.get("items") : Collections.emptyList();
			if (items.isEmpty()) throw new IllegalArgumentException("적용할 컬럼을 고르세요.");
			if (items.size() > LIMIT) throw new IllegalArgumentException("한 번에 2,000개 컬럼까지 적용할 수 있습니다.");

			Map<List<String>, List<Map<String, Object>>> groups = new LinkedHashMap<>();   // (dmId, 소유자, 테이블) → 행 (모델·테이블 순)
			for (Map<String, Object> it : items) {
				String owner = it.get("objOwner") == null ? "" : it.get("objOwner").toString();
				groups.computeIfAbsent(Arrays.asList((String) it.get("dmId"), owner, (String) it.get("objNm")), k -> new ArrayList<>()).add(it);
			}

			int applied = 0, tables = 0, rechecked = 0;
			List<Map<String, Object>> notStd = new ArrayList<>();
			List<Map<String, Object>> errors = new ArrayList<>();
			for (Map.Entry<List<String>, List<Map<String, Object>>> g : groups.entrySet()) {
				String dmId = g.getKey().get(0), owner = g.getKey().get(1), objNm = g.getKey().get(2);
				try {
					Map<String, String> same = applyTable(dmId, owner, objNm, g.getValue());
					applied += g.getValue().size();
					tables++;
					rechecked += same.size();
					for (Map.Entry<String, String> s : same.entrySet()) {
						if (s.getValue() == null) continue;
						Map<String, Object> ns = new LinkedHashMap<>();
						ns.put("dmId", dmId);
						ns.put("objOwner", owner);
						ns.put("objNm", objNm);
						ns.put("attrNm", s.getKey());
						ns.put("message", s.getValue());
						notStd.add(ns);
					}
				} catch (Exception e) {
					Map<String, Object> m = sqlSessionTemplate.selectOne("datamodel.selectDataModelById", dmId);
					Map<String, Object> err = new LinkedHashMap<>();
					err.put("dmId", dmId);
					err.put("dmNm", m == null ? null : m.get("dataModelNm"));
					err.put("objOwner", owner);
					err.put("objNm", objNm);
					err.put("message", e.getMessage());
					errors.add(err);
				}
			}

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("applied", applied);
			data.put("tables", tables);
			data.put("rechecked", rechecked);
			data.put("notStd", notStd);
			data.put("errors", errors);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(new ObjectMapper().writeValueAsString(data));
		} catch (Exception e) {
			log.error(">> applyTermsBySys failed: {}", e.getMessage());
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * 테이블 하나 적용 — 실패하면 던진다 (메시지는 화면에 그대로).
	 * ① 고른 용어를 모델 사전(상위 사전 포함) 범위의 승인 용어에서 다시 읽고 ② 컬럼이 조회 때 그대로인지(사용 중·삭제 대기 아님·한글명 같음)
	 * 본다 — 한 행이라도 걸리면 이 테이블은 보내지 않는다(구조 진단 현행화 109번 #23 과 같은 방식). ③ UPDATE 행은 컬럼 화면이
	 * 용어를 고를 때(applyTermToRow)와 같게 한글명 = 용어명, 영문명 = 용어 영문약어, 도메인 타입이 있으면 타입·길이·소수 = 도메인 값,
	 * 나머지(PK·FK·NULL·기본값)는 지금 값 — saveAttrs UPDATE 는 보낸 값으로 덮어쓴다. 순서·속성 값·표준 예외 사유는 보내지 않는다.
	 * ④ 바뀌는 값이 없는 행은 보내지 않고(saveAttrs 는 "변경 없음" 이라 플래그를 그대로 두고 전후가 같은 이력만 남긴다), 컬럼 저장이 끝난 뒤
	 * 표준 여부만 다시 판정한다({@link DataModelController#recheckAttrStandard}) ⑤ 보내는 행은 이름 사슬 순서로({@link #renameOrder}).
	 *
	 * @return 표준 여부만 다시 판정한 행 — 영문명 → 표준이 아니면 그 이유(표준이면 null)
	 */
	private Map<String, String> applyTable(String dmId, String owner, String objNm, List<Map<String, Object>> rows) {
		String dictId = dictService.resolve(null, dmId);
		List<String> stale = new ArrayList<>();
		List<Map<String, Object>> attrs = new ArrayList<>();
		List<StdDataModelAttrVo> same = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			String attrNm = (String) r.get("attrNm");
			String col = objNm + "." + attrNm;

			Map<String, Object> tp = dictService.lineageParam(dictId);
			tp.put("termsId", r.get("termsId"));
			Map<String, Object> term = sqlSessionTemplate.selectOne("terms.selectTermCandById", tp);
			if (term == null) {
				stale.add("이 모델 사전(상위 사전 포함)의 승인 용어가 아닙니다 — 다시 조회하세요: " + col);
				continue;
			}

			Map<String, Object> k = new HashMap<>();
			k.put("dataModelId", dmId);
			k.put("objOwner", owner);
			k.put("objNm", objNm);
			k.put("attrNm", attrNm);
			StdDataModelAttrVo ex = sqlSessionTemplate.selectOne("datamodel.selectDataModelAttrOne", k);
			// 삭제 대기(USE_YN='N')는 stdDataModelAttrMap 에 useYn 매핑이 없어 countHiddenAttr 로 본다
			if (ex == null || (Integer) sqlSessionTemplate.selectOne("datamodel.countHiddenAttr", k) > 0
					|| !Objects.equals(ex.getAttrNmKr(), r.get("attrNmKr"))) {
				stale.add("조회 뒤 컬럼이 바뀌었습니다 — 다시 조회하세요: " + col);
				continue;
			}

			Map<String, Object> a = new HashMap<>();
			a.put("mode", "UPDATE");
			a.put("origAttrNm", ex.getAttrNm());
			a.put("attrNm", term.get("termsEngAbrvNm"));
			a.put("attrNmKr", term.get("termsNm"));
			if (term.get("dataType") != null) {
				a.put("dataType", term.get("dataType"));
				a.put("dataLen", term.get("dataLen"));
				a.put("dataDecimalLen", term.get("dataDecimalLen"));
			} else {
				a.put("dataType", ex.getDataType());
				a.put("dataLen", ex.getDataLen());
				a.put("dataDecimalLen", ex.getDataDecimalLen());
			}
			a.put("pkYn", ex.getPkYn());
			a.put("fkYn", ex.getFkYn());
			a.put("nullableYn", ex.getNullableYn());
			a.put("defaultVal", ex.getDefaultVal());
			if (unchanged(a, ex)) same.add(ex);
			else attrs.add(a);
		}
		if (!stale.isEmpty()) throw new IllegalStateException(String.join(" / ", stale));

		if (!attrs.isEmpty()) {
			Map<String, Object> sb = new HashMap<>();
			sb.put("dataModelId", dmId);
			sb.put("objOwner", owner);
			sb.put("objNm", objNm);
			sb.put("attrs", renameOrder(attrs));
			Response r = dataModelController.saveAttrs(sb).block();
			if (r.getResultCode() != RestResult.CODE_200.getCode()) throw new IllegalStateException(r.getResultMessage());
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (StdDataModelAttrVo ex : same) out.put(ex.getAttrNm(), dataModelController.recheckAttrStandard(ex));
		return out;
	}

	/**
	 * 고른 용어로 바뀌는 값이 없는 행 — 영문명(대소문자만 다르면 saveAttrs 가 지금 이름을 둔다)·한글명·타입·길이·소수가 지금과 같다.
	 * 길이·소수가 비어 있으면 saveAttrs 처럼 지금 값을 쓴다.
	 */
	private static boolean unchanged(Map<String, Object> a, StdDataModelAttrVo ex) {
		return ex.getAttrNm().equalsIgnoreCase((String) a.get("attrNm"))
				&& Objects.equals(ex.getAttrNmKr(), a.get("attrNmKr"))
				&& Objects.equals(ex.getDataType(), a.get("dataType"))
				&& (a.get("dataLen") == null || ((Number) a.get("dataLen")).longValue() == ex.getDataLen())
				&& (a.get("dataDecimalLen") == null || ((Number) a.get("dataDecimalLen")).shortValue() == ex.getDataDecimalLen());
	}

	/**
	 * 같은 테이블 안의 이름 사슬(A→B, B→C) 순서. saveAttrs 는 행마다 그때의 DB 이름으로 영문명 중복을 보므로, 다른 행의 지금 이름을
	 * 새 이름으로 쓰는 행은 그 행 뒤로 보낸다(나머지는 받은 순서 — 컬럼 순서). 맞바꿈(A→B, B→A)처럼 순서로 풀 수 없으면 거부한다.
	 * 이름은 대소문자를 무시하고 비교한다(화면 영문명 겹침 검사와 같음).
	 */
	private static List<Map<String, Object>> renameOrder(List<Map<String, Object>> attrs) {
		List<Map<String, Object>> out = new ArrayList<>();
		List<Map<String, Object>> rest = new ArrayList<>(attrs);
		while (!rest.isEmpty()) {
			List<Map<String, Object>> ready = new ArrayList<>();
			for (Map<String, Object> r : rest) {
				String to = ((String) r.get("attrNm")).toUpperCase();
				boolean waits = false;
				for (Map<String, Object> s : rest) {
					if (s != r && to.equals(((String) s.get("origAttrNm")).toUpperCase())) { waits = true; break; }
				}
				if (!waits) ready.add(r);
			}
			if (ready.isEmpty()) {
				List<String> pairs = new ArrayList<>();
				for (Map<String, Object> r : rest) pairs.add(r.get("origAttrNm") + " → " + r.get("attrNm"));
				throw new IllegalStateException("같은 테이블에서 영문명을 맞바꾸는 변경은 한 번에 적용할 수 없습니다 — 한 컬럼씩 나눠 적용하세요: "
						+ String.join(", ", pairs));
			}
			out.addAll(ready);
			rest.removeAll(ready);
		}
		return out;
	}
}
