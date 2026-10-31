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

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("models", models);
			data.put("items", items);
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
	 * @param body {items:[{dmId, objOwner, objNm, attrNm, attrNmKr(조회 때 한글명), termsId}]} — 2,000개까지
	 * @return contents {applied(적용한 컬럼 수), tables(적용한 테이블 수), errors:[{dmId, dmNm, objOwner, objNm, message}]}
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

			int applied = 0, tables = 0;
			List<Map<String, Object>> errors = new ArrayList<>();
			for (Map.Entry<List<String>, List<Map<String, Object>>> g : groups.entrySet()) {
				String dmId = g.getKey().get(0), owner = g.getKey().get(1), objNm = g.getKey().get(2);
				try {
					applyTable(dmId, owner, objNm, g.getValue());
					applied += g.getValue().size();
					tables++;
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
	 */
	private void applyTable(String dmId, String owner, String objNm, List<Map<String, Object>> rows) {
		String dictId = dictService.resolve(null, dmId);
		List<String> stale = new ArrayList<>();
		List<Map<String, Object>> attrs = new ArrayList<>();
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
			attrs.add(a);
		}
		if (!stale.isEmpty()) throw new IllegalStateException(String.join(" / ", stale));

		Map<String, Object> sb = new HashMap<>();
		sb.put("dataModelId", dmId);
		sb.put("objOwner", owner);
		sb.put("objNm", objNm);
		sb.put("attrs", attrs);
		Response r = dataModelController.saveAttrs(sb).block();
		if (r.getResultCode() != RestResult.CODE_200.getCode()) throw new IllegalStateException(r.getResultMessage());
	}
}
