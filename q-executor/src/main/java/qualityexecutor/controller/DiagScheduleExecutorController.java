package qualityexecutor.controller;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.model.std.DiagScheduleVo;

import lombok.extern.slf4j.Slf4j;
import qualityexecutor.service.std.ScheduledDiagLauncher;
import reactor.core.publisher.Mono;

/**
 * 스케줄러 수동 트리거용 엔드포인트 (q-center runNow → q-executor 로 프록시)
 */
@Slf4j
@RestController
@RequestMapping("/api/diag/schedule")
public class DiagScheduleExecutorController {

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private ScheduledDiagLauncher launcher;

    /** 즉시 실행. q-center 가 LOG(RUNNING) 을 먼저 insert 하고 logId 를 전달한다. */
    @PostMapping("/runNow")
    public Mono<Response> runNow(@RequestBody Map<String, String> params) {
        Response res = new Response();
        String scheduleId = params.get("scheduleId");
        String logId      = params.get("logId");  // q-center 가 이미 insert 한 로그 ID
        log.info(">> schedule runNow: scheduleId={}, logId={}", scheduleId, logId);
        try {
            if (scheduleId == null) throw new IllegalArgumentException("scheduleId 필수");
            DiagScheduleVo sc = sql.selectOne("diagSchedule.selectScheduleById", scheduleId);
            if (sc == null) throw new IllegalStateException("[DATA_NOT_FOUND] 스케줄 없음: " + scheduleId);

            // launcher 가 logId null 이면 새로 생성, 있으면 기존 LOG 를 이어씀
            String usedLogId = launcher.launch(sc, "MANUAL", logId);

            res.setContents(usedLogId);
            res.setResultInfo(RestResult.CODE_200);
        } catch (Exception e) {
            log.error(">> schedule runNow failed", e);
            res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
        }
        return Mono.just(res);
    }
}
