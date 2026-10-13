package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.quality.common.PermCode;
import com.ndata.common.message.RestResult;
import com.ndata.quality.service.StdDictService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 98번 표준사전 세트 — 사전 CRUD.
 *
 * <p>사전 목록 조회는 모든 사용자가 쓴다 (화면마다 사전 선택기가 붙는다).
 * 생성·수정·중지는 표준사전 관리 권한(관리자 또는 DICT_ADMIN)만.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/dict")
public class StdDictController {

	@Autowired private StdDictService dictService;
	@Autowired private SessionService sessionService;

	/** 사전 목록 — 화면 선택기용. */
	@PostMapping("/list")
	public List<Map<String, Object>> list(@RequestBody(required = false) Map<String, Object> body) {
		return dictService.list();
	}

	/** 사전별 등록 건수 — 사전 관리 화면. */
	@PostMapping("/stats")
	public Map<String, Object> stats(@RequestBody Map<String, Object> body) {
		return dictService.stats((String) body.get("dictId"));
	}

	/** 신규 모델 등록 화면의 기본 선택값. */
	@PostMapping("/default")
	public Map<String, Object> defaultDict(@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> r = new HashMap<>();
		r.put("dictId", dictService.defaultDictId());
		return r;
	}

	@PostMapping("/save")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.DICT_ADMIN)) throw new IllegalStateException("권한이 없습니다 (표준사전 관리 권한 필요)");
			String id = (String) body.get("dictId");
			Map<String, Object> p = new HashMap<>(body);
			p.put("userId", sessionService.getUserId());
			// 104번 WP6-13 — 용어 영문약어명 최대 길이: 비우면 제한 없음, 넣으면 1 이상 정수
			Object maxLen = body.get("termEngMaxLen");
			String ml = maxLen == null ? "" : String.valueOf(maxLen).trim();
			if (ml.isEmpty()) {
				p.put("termEngMaxLen", null);
			} else {
				int v;
				try {
					v = Integer.parseInt(ml);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("용어 영문약어명 최대 길이는 숫자여야 합니다.");
				}
				if (v < 1) throw new IllegalArgumentException("용어 영문약어명 최대 길이는 1 이상이어야 합니다.");
				p.put("termEngMaxLen", v);
			}
			// 104번 WP5-14 — 참조 사전(읽기 전용). 기본 사전은 참조 사전이 될 수 없다 (신규 모델·예약 진단의 기본값)
			String dictTp = body.get("dictTp") == null ? null : String.valueOf(body.get("dictTp"));
			if (dictTp != null && !"ORG".equals(dictTp) && !"REF".equals(dictTp)) throw new IllegalArgumentException("사전 유형이 올바르지 않습니다: " + dictTp);
			boolean isDefault = "Y".equals(body.get("defaultYn"))
					|| (id != null && !id.trim().isEmpty() && id.trim().equals(dictService.defaultDictId()));
			if ("REF".equals(dictTp) && isDefault) throw new IllegalArgumentException("기본 사전은 참조 사전으로 둘 수 없습니다.");
			// 104번 WP5-11 — 상위 사전: 있는 사전, 자기 자신·자기 하위 아님, 5단계까지
			String parent = body.get("parentDictId") == null ? "" : String.valueOf(body.get("parentDictId")).trim();
			if (!parent.isEmpty()) {
				java.util.List<String> up = dictService.lineage(parent);
				if (!up.get(0).equals(parent) || dictService.list().stream().noneMatch(d -> parent.equals(d.get("dictId")))) {
					throw new IllegalArgumentException("상위 사전을 찾을 수 없습니다: " + parent);
				}
				if (id != null && up.contains(id.trim())) throw new IllegalArgumentException("자기 자신이나 하위 사전을 상위 사전으로 둘 수 없습니다.");
				if (up.size() >= 5) throw new IllegalArgumentException("상위 사전은 5단계까지입니다.");
			}
			if (id == null || id.trim().isEmpty()) {
				p.put("dictId", UUID.randomUUID().toString().replace("-", "").substring(0, 22));
				dictService.create(p);
			} else {
				dictService.update(p);
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"dictId\":\"" + p.get("dictId") + "\"}");
		} catch (Exception e) {
			log.error(">> dict save failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * 사전 사용 중지.
	 *
	 * <p>지우지 않는다. 사전이 사라지면 그 사전으로 잰 진단 이력을 해석할 수 없다.</p>
	 */
	@PostMapping("/disable")
	public Mono<Response> disable(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.DICT_ADMIN)) throw new IllegalStateException("권한이 없습니다 (표준사전 관리 권한 필요)");
			dictService.disable((String) body.get("dictId"), sessionService.getUserId());
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> dict disable failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
