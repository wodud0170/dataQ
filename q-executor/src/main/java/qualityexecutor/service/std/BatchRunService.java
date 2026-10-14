package qualityexecutor.service.std;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.service.DaStatService;

import lombok.extern.slf4j.Slf4j;

/**
 * 104번 WP9-67 — 예약 실행 대상 확대: 준수 현황 스냅샷·미사용 표준.
 *
 * <p>실행 기록(TB_BATCH_RUN)을 RUNNING 으로 만들고 별도 스레드에서 처리한 뒤 DONE·ERROR 로 마감한다.
 * 예약 실행 완료 감지(DiagSchedulerRunner)는 실행 이력의 BATCH_RUN_ID 로 이 상태를 본다.</p>
 * <ul>
 *   <li>COMPLIANCE_SNAPSHOT — 모델(비우면 사용 중 모델 전부)마다 그 시각의 지표를 TB_COMPLIANCE_SNAPSHOT 에 쌓는다</li>
 *   <li>UNUSED_STD — 사전(모델을 고르면 모델 사전, 아니면 기본 사전)의 미사용 단어·용어·도메인(승인된 것)을 TB_UNUSED_STD_RESULT 에 저장</li>
 * </ul>
 */
@Slf4j
@Service
public class BatchRunService {

	@Autowired private SqlSessionTemplate sql;
	@Autowired private DaStatService daStat;

	/** 실행 기록을 만들고 스레드를 띄운다 — 실행 ID 를 돌려준다 */
	public String start(String runTp, String dmId, String userId, String scheduleLogId) {
		String runId = UUID.randomUUID().toString().replace("-", "");
		String dictId = null;
		if ("UNUSED_STD".equals(runTp)) {
			if (dmId != null) dictId = sql.selectOne("daStat.selectModelDictId", dmId);
			if (dictId == null) dictId = sql.selectOne("daStat.selectDefaultDictId");
		}
		Map<String, Object> p = new HashMap<>();
		p.put("runId", runId);
		p.put("runTp", runTp);
		p.put("dmId", dmId);
		p.put("dictId", dictId);
		p.put("scheduleLogId", scheduleLogId);
		p.put("userId", userId);
		sql.insert("daStat.insertBatchRun", p);

		final String dict = dictId;
		Thread t = new Thread(() -> run(runId, runTp, dmId, dict), "BatchRun-" + runId.substring(0, 8));
		t.setDaemon(true);
		t.start();
		return runId;
	}

	private void run(String runId, String runTp, String dmId, String dictId) {
		Map<String, Object> fin = new HashMap<>();
		fin.put("runId", runId);
		try {
			fin.put("summary", "COMPLIANCE_SNAPSHOT".equals(runTp) ? snapshot(runId, dmId) : unused(runId, dictId));
			fin.put("status", "DONE");
		} catch (Exception e) {
			log.error(">> BatchRun {} {} failed", runTp, runId, e);
			fin.put("status", "ERROR");
			fin.put("errorMsg", ScheduledDiagLauncher.buildErrorMsg(e));
		}
		sql.update("daStat.finishBatchRun", fin);
	}

	private String snapshot(String runId, String dmId) {
		String snapDt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		List<Map<String, Object>> rows = daStat.modelMetrics(dmId);
		int diag = 0;
		for (Map<String, Object> r : rows) {
			r.put("runId", runId);
			r.put("snapDt", snapDt);
			sql.insert("daStat.insertSnapshot", r);
			if (r.get("stndRate") != null) diag++;
		}
		return "모델 " + rows.size() + "개 (표준 진단한 모델 " + diag + "개)";
	}

	private String unused(String runId, String dictId) {
		if (dictId == null) throw new IllegalStateException("[CONFIG] 기본 표준사전이 없어 미사용 표준을 찾을 수 없습니다.");
		Map<String, Object> p = new HashMap<>();
		p.put("dictId", dictId);
		p.put("aprvOnly", "Y");
		p.put("noLimit", "Y");   // 화면은 5,000건까지 보이지만 저장은 전부
		String[][] kinds = { { "WORD", "unusedStd.selectUnusedWords", "단어" }, { "TERM", "unusedStd.selectUnusedTerms", "용어" },
				{ "DOMAIN", "unusedStd.selectUnusedDomains", "도메인" } };
		StringBuilder sb = new StringBuilder();
		for (String[] k : kinds) {
			List<Map<String, Object>> rows = sql.selectList(k[1], p);
			for (int i = 0; i < rows.size(); i += 500) {
				Map<String, Object> ins = new HashMap<>();
				ins.put("runId", runId);
				ins.put("kind", k[0]);
				ins.put("rows", rows.subList(i, Math.min(i + 500, rows.size())));
				sql.insert("daStat.insertUnusedResults", ins);
			}
			if (sb.length() > 0) sb.append(" · ");
			sb.append(k[2]).append(' ').append(rows.size());
		}
		return sb.toString();
	}
}
