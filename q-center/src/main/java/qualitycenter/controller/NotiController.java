package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import qualitycenter.service.auth.SessionService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP1 — 로그인 사용자의 알림(헤더 종 아이콘). 자기 알림만 읽고 읽음 처리한다.
 * 알림은 결재 엔진(AprvService)이 만들고, 실시간 전달은 WebSocketService.sendUserNotis.
 */
@RestController
@RequestMapping("/api/noti")
public class NotiController {

	@Autowired private SessionService sessionService;
	@Autowired private SqlSessionTemplate sqlSessionTemplate;

	/** 최근 알림 30건 */
	@GetMapping("/list")
	public List<Map<String, Object>> list() {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", sessionService.getUserId());
		p.put("limit", 30);
		return sqlSessionTemplate.selectList("aprv.selectNotiList", p);
	}

	@GetMapping("/unreadCount")
	public int unreadCount() {
		return sqlSessionTemplate.selectOne("aprv.countUnreadNoti", sessionService.getUserId());
	}

	/** 읽음 — {notiIds: [..]}. 비우면 모두 읽음 */
	@PostMapping("/read")
	public Mono<Response> read(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", sessionService.getUserId());
		p.put("notiIds", body.get("notiIds"));
		sqlSessionTemplate.update("aprv.markNotiRead", p);
		Response r = new Response();
		r.setResultInfo(RestResult.CODE_200);
		return Mono.just(r);
	}
}
