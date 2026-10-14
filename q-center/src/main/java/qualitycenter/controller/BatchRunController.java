package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 104번 WP9-67 — 배치 실행 결과 조회 (준수 현황 스냅샷·미사용 표준). 실행은 예약 실행(스케줄)이 한다.
 * 조회는 로그인 사용자 누구나.
 */
@RestController
@RequestMapping("/api/batchRun")
public class BatchRunController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;

	/** 실행 1건 — {runId} */
	@PostMapping("/get")
	public Map<String, Object> get(@RequestBody Map<String, Object> body) {
		return sqlSessionTemplate.selectOne("daStat.selectBatchRun", body.get("runId"));
	}

	/** 실행 목록 — {runTp, dictId?, doneOnly?: Y} 최근 50건 */
	@PostMapping("/list")
	public List<Map<String, Object>> list(@RequestBody Map<String, Object> body) {
		return sqlSessionTemplate.selectList("daStat.selectBatchRuns", body);
	}

	/** 저장된 미사용 표준 — {runId, kind: WORD|TERM|DOMAIN}. 미사용 표준 화면 목록과 같은 모양 */
	@PostMapping("/unusedItems")
	public Map<String, Object> unusedItems(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new HashMap<>();
		out.put("items", sqlSessionTemplate.selectList("daStat.selectUnusedResults", body));
		return out;
	}
}
