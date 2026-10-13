package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;
import com.ndata.quality.service.StdDictService;

import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP6-15 — 옛 표준 → 새 표준 매핑 (표준 사전 > 표준 매핑).
 *
 * <p>구표준을 쓰는 사전(운영 옵션 "구표준 사용", TB_STD_DICT.OLD_STD_YN)에서 옛 단어·용어 이름(과 영문약어명)을 새 표준
 * 단어·용어에 잇는다. 표준 진단 결과는 컬럼 영문명이 옛 용어 영문약어명과 같으면 새 용어를 안내한다.
 * 조회는 누구나, 등록·수정·삭제는 표준 직접 관리 권한(STD_MANAGE).</p>
 */
@RestController
@RequestMapping("/api/stdMap")
public class StdMapController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private StdDictService dictService;

	private static final ObjectMapper OM = new ObjectMapper();

	private Response result(int code, String msg, Object data) {
		Response r = new Response();
		r.setResultInfo(code, msg);
		if (data != null) {
			try {
				r.setContents(OM.writeValueAsString(data));
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(e);
			}
		}
		return r;
	}

	private static String s(Object o) {
		return o == null ? null : o.toString().trim();
	}

	/** 목록 — {dictId, mapTp?, keyword?}. 사전의 구표준 사용 여부를 함께 준다 */
	@PostMapping("/list")
	public Mono<Response> list(@RequestBody Map<String, Object> body) {
		String dictId = s(body.get("dictId"));
		Map<String, Object> p = dictService.dictParam(dictId);
		p.put("mapTp", s(body.get("mapTp")));
		p.put("keyword", s(body.get("keyword")));
		Map<String, Object> m = new HashMap<>();
		m.put("oldStdYn", dictService.isOldStdUsed((String) p.get("dictId")) ? "Y" : "N");
		m.put("items", sqlSessionTemplate.selectList("stdMap.selectList", p));
		return Mono.just(result(RestResult.CODE_200.getCode(), "OK", m));
	}

	/** 저장 — {mapId?, dictId, mapTp: WORD|TERM, oldNm, oldEngAbrvNm?, newId, mapReason?} */
	@PostMapping("/save")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			return Mono.just(result(403, "권한이 없습니다 (표준 직접 관리 권한 필요)", null));
		}
		Map<String, Object> p = new HashMap<>();
		String mapId = s(body.get("mapId"));
		String dictId = (String) dictService.dictParam(s(body.get("dictId"))).get("dictId");
		String mapTp = s(body.get("mapTp"));
		String oldNm = s(body.get("oldNm"));
		String oldEng = s(body.get("oldEngAbrvNm"));
		String newId = s(body.get("newId"));
		if (!dictService.isOldStdUsed(dictId)) {
			return Mono.just(result(RestResult.CODE_500.getCode(), "이 사전은 구표준을 쓰지 않습니다. 관리 > 표준사전 관리에서 '구표준 사용' 을 켜세요.", null));
		}
		if (!"WORD".equals(mapTp) && !"TERM".equals(mapTp)) return Mono.just(result(RestResult.CODE_500.getCode(), "유형은 단어 또는 용어입니다.", null));
		if (oldNm == null || oldNm.isEmpty()) return Mono.just(result(RestResult.CODE_500.getCode(), "옛 표준 이름을 넣어 주세요.", null));
		if (newId == null || newId.isEmpty()) return Mono.just(result(RestResult.CODE_500.getCode(), "새 표준을 골라 주세요.", null));
		if (oldEng != null && !oldEng.isEmpty()) oldEng = oldEng.toUpperCase();
		p.put("mapId", mapId);
		p.put("dictId", dictId);
		p.put("mapTp", mapTp);
		p.put("oldNm", oldNm);
		p.put("oldEngAbrvNm", oldEng);
		p.put("newId", newId);
		p.put("mapReason", s(body.get("mapReason")));
		p.put("userId", sessionService.getUserId());
		int target = sqlSessionTemplate.selectOne("stdMap.countNewTarget", p);
		if (target == 0) return Mono.just(result(RestResult.CODE_500.getCode(), "새 표준이 이 사전에 없습니다.", null));
		int dup = sqlSessionTemplate.selectOne("stdMap.countDup", p);
		if (dup > 0) return Mono.just(result(RestResult.CODE_500.getCode(), "같은 옛 이름의 매핑이 이미 있습니다: " + oldNm, null));
		if (oldEng != null && !oldEng.isEmpty() && "TERM".equals(mapTp)) {
			Map<String, Object> q = new HashMap<>(p);
			// ArrayList — MyBatis OGNL 이 size() 를 부를 때 java.util 내부 클래스(SingletonList)는 Java 17 모듈 접근에 막힌다
			q.put("engList", new java.util.ArrayList<>(java.util.Collections.singletonList(oldEng)));
			List<Map<String, Object>> same = sqlSessionTemplate.selectList("stdMap.selectTermMapByEng", q);
			boolean other = same.stream().anyMatch(x -> !oldNm.equals(x.get("oldNm")));
			if (other) return Mono.just(result(RestResult.CODE_500.getCode(), "같은 옛 영문약어명의 용어 매핑이 이미 있습니다: " + oldEng, null));
		}
		if (mapId == null || mapId.isEmpty()) {
			p.put("mapId", UUID.randomUUID().toString().replace("-", ""));
			sqlSessionTemplate.insert("stdMap.insert", p);
		} else {
			sqlSessionTemplate.update("stdMap.update", p);
		}
		return Mono.just(result(RestResult.CODE_200.getCode(), "OK", p.get("mapId")));
	}

	/** 삭제 — {mapIds: [...]} */
	@PostMapping("/delete")
	public Mono<Response> delete(@RequestBody Map<String, Object> body) {
		if (!sessionService.hasPerm(PermCode.STD_MANAGE)) {
			return Mono.just(result(403, "권한이 없습니다 (표준 직접 관리 권한 필요)", null));
		}
		@SuppressWarnings("unchecked")
		List<String> ids = (List<String>) body.get("mapIds");
		if (ids == null || ids.isEmpty()) return Mono.just(result(RestResult.CODE_500.getCode(), "지울 매핑을 골라 주세요.", null));
		int n = sqlSessionTemplate.delete("stdMap.delete", ids);
		return Mono.just(result(RestResult.CODE_200.getCode(), "OK", n));
	}
}
