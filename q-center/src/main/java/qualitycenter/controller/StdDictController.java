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
import com.ndata.common.message.RestResult;
import com.ndata.quality.service.StdDictService;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 98번 표준사전 세트 — 사전 CRUD.
 *
 * <p>사전 목록 조회는 모든 사용자가 쓴다 (화면마다 사전 선택기가 붙는다).
 * 생성·수정·중지는 관리자만.</p>
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			String id = (String) body.get("dictId");
			Map<String, Object> p = new HashMap<>(body);
			p.put("userId", sessionService.getUserId());
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
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			dictService.disable((String) body.get("dictId"), sessionService.getUserId());
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			log.error(">> dict disable failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
