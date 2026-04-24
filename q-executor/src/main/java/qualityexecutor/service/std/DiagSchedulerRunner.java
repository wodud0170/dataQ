package qualityexecutor.service.std;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.ndata.quality.model.std.DiagScheduleLogVo;
import com.ndata.quality.model.std.DiagScheduleVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 진단 스케줄러 — q-executor 에서 @Scheduled 로 주기 평가 + 완료 폴링.
 *
 * <ol>
 *   <li><b>evaluateAndRun()</b> 매 1분: 활성 스케줄 평가 → 트리거 시점 도래한 것만 launch</li>
 *   <li><b>pollCompletion()</b> 매 30초: RUNNING LOG 들의 실제 DiagJob/StructDiagHistory 상태를 조회해 DONE/ERROR 로 마감</li>
 *   <li><b>expireStale()</b> 매 5분: 60분 이상 RUNNING 고착된 LOG 를 타임아웃 처리</li>
 * </ol>
 */
@Slf4j
@Component
public class DiagSchedulerRunner {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private ScheduledDiagLauncher launcher;

    // ============ 1분 주기 평가 + 기동 ============
    @Scheduled(fixedRate = 60_000L, initialDelay = 30_000L)
    public void evaluateAndRun() {
        List<DiagScheduleVo> active;
        try {
            active = sql.selectList("diagSchedule.selectActiveSchedules");
        } catch (Exception e) {
            log.error(">> DiagSchedulerRunner.evaluateAndRun: 활성 스케줄 조회 실패", e);
            return;
        }
        if (active == null || active.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int triggered = 0, skipped = 0;

        for (DiagScheduleVo sc : active) {
            try {
                if (!shouldTrigger(sc, now)) continue;
                if (isAlreadyRunningForModel(sc)) {
                    logSkipped(sc, "동일 모델+유형 RUNNING 존재");
                    skipped++;
                    continue;
                }
                launcher.launch(sc, "AUTO", null);
                triggered++;
            } catch (Exception e) {
                log.error(">> 스케줄 {} 평가/기동 중 오류", sc.getScheduleId(), e);
            }
        }
        if (triggered > 0 || skipped > 0) {
            log.info(">> DiagSchedulerRunner: 평가 대상 {}, 기동 {}, 스킵 {}",
                    active.size(), triggered, skipped);
        }
    }

    /** 스케줄의 현재 시각 트리거 도래 여부 */
    private boolean shouldTrigger(DiagScheduleVo sc, LocalDateTime now) {
        // LAST_EXEC_DT 가 같은 "슬롯" 에 찍혀있으면 이미 돌았다는 뜻 → skip
        // SIMPLE: 날짜+HH:mm 이 일치해야 실행. 같은 날 같은 시각 재실행 방지.
        // CRON : CronExpression.next(lastExec or epoch) < now 이면 실행
        if ("CRON".equalsIgnoreCase(sc.getScheduleType())) {
            if (sc.getCronExpr() == null) return false;
            try {
                CronExpression cron = CronExpression.parse(sc.getCronExpr());
                LocalDateTime cursor = parseLastExec(sc.getLastExecDt());
                if (cursor == null) cursor = now.minusMinutes(2);
                LocalDateTime nextFire = cron.next(cursor);
                return nextFire != null && !nextFire.isAfter(now);
            } catch (Exception e) {
                return false;
            }
        }

        // SIMPLE
        String time = sc.getRepeatTime();
        if (time == null || !time.matches("^[0-2]\\d:[0-5]\\d$")) return false;
        String nowHm = now.format(TIME_FMT);
        if (!time.equals(nowHm)) return false;

        String cycle = sc.getRepeatCycle();
        if ("DAILY".equals(cycle)) {
            return !isAlreadyRunToday(sc, now);
        } else if ("WEEKLY".equals(cycle)) {
            Integer dow = sc.getRepeatDayOfWeek();
            if (dow == null) return false;
            DayOfWeek nowDow = now.getDayOfWeek();  // MON=1 ~ SUN=7
            if (dow != nowDow.getValue()) return false;
            return !isAlreadyRunToday(sc, now);
        } else if ("MONTHLY".equals(cycle)) {
            Integer dom = sc.getRepeatDayOfMonth();
            if (dom == null) return false;
            if (dom != now.getDayOfMonth()) return false;
            return !isAlreadyRunToday(sc, now);
        }
        return false;
    }

    private boolean isAlreadyRunToday(DiagScheduleVo sc, LocalDateTime now) {
        LocalDateTime last = parseLastExec(sc.getLastExecDt());
        if (last == null) return false;
        return last.toLocalDate().isEqual(now.toLocalDate());
    }

    private LocalDateTime parseLastExec(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            // MyBatis timestamp 가 "yyyy-MM-dd HH:mm:ss.*" 형식으로 올 것이라 가정
            String normalized = s.replace("T", " ");
            if (normalized.length() >= 19) normalized = normalized.substring(0, 19);
            return LocalDateTime.parse(normalized,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 동일 dataModelId + diagType 에 RUNNING 이력이 있으면 true */
    private boolean isAlreadyRunningForModel(DiagScheduleVo sc) {
        Map<String, Object> p = new HashMap<>();
        p.put("dataModelId", sc.getDataModelId());
        // BOTH 는 STANDARD 와 STRUCT 두 유형 다 봐야 하지만 1차 구현은 DIAG_TYPE 그대로 비교
        p.put("diagType",    sc.getDiagType());
        Integer cnt = sql.selectOne("diagSchedule.countRunningByModelAndType", p);
        return cnt != null && cnt > 0;
    }

    private void logSkipped(DiagScheduleVo sc, String reason) {
        DiagScheduleLogVo log = new DiagScheduleLogVo();
        log.setLogId(java.util.UUID.randomUUID().toString().replace("-", ""));
        log.setScheduleId(sc.getScheduleId());
        log.setScheduleNmSnapshot(sc.getScheduleNm());
        log.setTriggerType("AUTO");
        log.setDiagType(sc.getDiagType());
        log.setExecStatus("SKIPPED");
        log.setErrorMsg("[SKIPPED] " + reason);
        sql.insert("diagSchedule.insertLog", log);
        Map<String, Object> p = new HashMap<>();
        p.put("logId",      log.getLogId());
        p.put("execStatus", "SKIPPED");
        p.put("errorMsg",   log.getErrorMsg());
        p.put("diagJobId",    null);
        p.put("structDiagId", null);
        sql.update("diagSchedule.updateLogFinish", p);
    }

    // ============ 30초 주기 완료 감지 ============
    @Scheduled(fixedRate = 30_000L, initialDelay = 45_000L)
    public void pollCompletion() {
        List<DiagScheduleLogVo> running;
        try {
            running = sql.selectList("diagSchedule.selectRunningLogs");
        } catch (Exception e) {
            log.error(">> pollCompletion: RUNNING 로그 조회 실패", e);
            return;
        }
        if (running == null || running.isEmpty()) return;

        for (DiagScheduleLogVo row : running) {
            try {
                String underlyingStatus = resolveUnderlyingStatus(row);
                if (underlyingStatus == null) continue;        // 아직 진행 중이면 skip
                if ("DONE".equalsIgnoreCase(underlyingStatus)) {
                    markFinish(row, "DONE", null);
                } else if ("ERROR".equalsIgnoreCase(underlyingStatus)) {
                    markFinish(row, "ERROR", "[DIAG] 기저 진단이 ERROR 로 종료됨");
                } else if ("STOPPED".equalsIgnoreCase(underlyingStatus)) {
                    markFinish(row, "ERROR", "[DIAG] 기저 진단이 STOPPED 상태로 중단됨");
                }
                // READY/RUNNING 은 그대로 RUNNING 유지
            } catch (Exception e) {
                log.error(">> pollCompletion: log {} 처리 중 오류", row.getLogId(), e);
            }
        }
    }

    /** 로그에 연결된 기저 진단(DIAG_JOB / STRUCT_DIAG) 의 status 를 조회. 연결이 아직 안 됐으면 null */
    private String resolveUnderlyingStatus(DiagScheduleLogVo row) {
        if (row.getDiagJobId() != null && !row.getDiagJobId().isEmpty()) {
            return sql.selectOne("diagSchedule.selectDiagJobStatus", row.getDiagJobId());
        }
        if (row.getStructDiagId() != null && !row.getStructDiagId().isEmpty()) {
            return sql.selectOne("diagSchedule.selectStructDiagStatus", row.getStructDiagId());
        }
        return null;
    }

    private void markFinish(DiagScheduleLogVo row, String status, String errorMsg) {
        Map<String, Object> p = new HashMap<>();
        p.put("logId",        row.getLogId());
        p.put("execStatus",   status);
        p.put("errorMsg",     errorMsg);
        p.put("diagJobId",    row.getDiagJobId());
        p.put("structDiagId", row.getStructDiagId());
        sql.update("diagSchedule.updateLogFinish", p);

        // 스케줄 요약 업데이트 (LAST_EXEC_*)
        Map<String, Object> p2 = new HashMap<>();
        p2.put("scheduleId", row.getScheduleId());
        p2.put("status",     status);
        p2.put("logId",      row.getLogId());
        sql.update("diagSchedule.updateScheduleLastExec", p2);
    }

    // ============ 5분 주기 타임아웃 마감 ============
    @Scheduled(fixedRate = 300_000L, initialDelay = 120_000L)
    public void expireStale() {
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("minutes", 60);
            int n = sql.update("diagSchedule.expireStaleRunningLogs", p);
            if (n > 0) log.warn(">> 타임아웃 만료된 스케줄 로그 {} 건", n);
        } catch (Exception e) {
            log.error(">> expireStale 실패", e);
        }
    }
}
