package qualityexecutor.service.std;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

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
 *   <li><b>expireStale()</b> 매 5분: 60분 이상 RUNNING 고착된 LOG 를 타임아웃 처리 (기저 작업이 아직 진행 중이면 제외)</li>
 *   <li><b>cleanupOrphanDiags()</b> 기동 때 1회: 죽은 executor 가 남긴 READY/RUNNING 기저 작업을 ERROR 로</li>
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

    // ============ 기동 때 정리 ============
    /**
     * 110 6.10-6 — 진단 Job·구조 진단·예약 범정부 메타 검증·배치는 이 executor 의 스레드에서만 돈다.
     * 기동 때 READY/RUNNING 으로 남은 건 실행하던 executor 가 죽은 것이라 ERROR 로 바꾼다.
     * 그대로 두면 같은 모델 진단이 진행 중 건수로 늘 409 였고, 연결된 스케줄 LOG 는 타임아웃 마감에서도 빠진다.
     * 웹 포트가 열리기 전이라 새 요청과 겹치지 않는다 (그 사이 center 가 만든 READY 는 호출 실패로 center 가 이미 ERROR 로 바꾼다).
     * 연결된 스케줄 LOG 는 pollCompletion 이 기저 ERROR 를 보고 마감한다 (QualLockService.init 의 cleanupStale 와 같은 방식).
     */
    @PostConstruct
    public void cleanupOrphanDiags() {
        try {
            int jobs    = sql.update("diag.updateOrphanDiagJobs");
            int structs = sql.update("structdiag.updateOrphanStructDiags");
            int govs    = sql.update("diagSchedule.updateOrphanGovMetaSends");
            int batches = sql.update("diagSchedule.updateOrphanBatchRuns");
            if (jobs + structs + govs + batches > 0) {
                log.warn(">> 기동 정리 — 중단된 표준화 진단 Job {} 건, 구조 진단 {} 건, 범정부 메타 검증 {} 건, 배치 {} 건을 ERROR 로",
                        jobs, structs, govs, batches);
            }
        } catch (Exception e) {
            log.error(">> 기동 정리(cleanupOrphanDiags) 실패", e);
        }
    }

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
                String runningReason = runningReasonForModel(sc);
                if (runningReason != null) {
                    logSkipped(sc, runningReason);
                    skipped++;
                    continue;
                }
                // 110 6.10-13a — 자동 실행의 실행자는 스케줄 등록자 (없으면 런처가 SCHEDULER)
                launcher.launch(sc, "AUTO", null, sc.getCretUserId());
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
        if (time == null || !time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) return false;   // 110 6.10-11b — 00~23시
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

    /**
     * 같은 모델에 겹치는 실행이 있으면 SKIPPED 사유, 없으면 null.
     * <ul>
     *   <li>스케줄 LOG: 같은 모델의 RUNNING 중 진단 유형이 겹치는 것 (110 6.10-12b — STANDARD 는 STANDARD·BOTH,
     *       STRUCT 는 STRUCT·BOTH, BOTH 는 셋 다, 그 밖 유형은 자기 유형)</li>
     *   <li>진단 Job·구조 진단 이력: 화면에서 돌린 진단이 진행 중인 것 (110 6.10-12a — 화면 실행의 409 와 같은 기준)</li>
     * </ul>
     */
    private String runningReasonForModel(DiagScheduleVo sc) {
        String diagType = sc.getDiagType();
        List<String> overlap;
        if ("STANDARD".equals(diagType))    overlap = Arrays.asList("STANDARD", "BOTH");
        else if ("STRUCT".equals(diagType)) overlap = Arrays.asList("STRUCT", "BOTH");
        else if ("BOTH".equals(diagType))   overlap = Arrays.asList("STANDARD", "STRUCT", "BOTH");
        else                                overlap = Collections.singletonList(diagType);
        Map<String, Object> p = new HashMap<>();
        p.put("dataModelId", sc.getDataModelId());
        p.put("diagTypes",   overlap);
        int logCnt = sql.selectOne("diagSchedule.countRunningByModelAndType", p);
        if (logCnt > 0) return "동일 모델+유형 RUNNING 존재";

        int active = 0;
        if ("STANDARD".equals(diagType) || "BOTH".equals(diagType)) {
            int jobCnt = sql.selectOne("diag.selectActiveDiagJobCount", sc.getDataModelId());
            active += jobCnt;
        }
        if ("STRUCT".equals(diagType) || "BOTH".equals(diagType)) {
            int structCnt = sql.selectOne("structdiag.selectActiveStructDiagCount", sc.getDataModelId());
            active += structCnt;
        }
        return active > 0 ? "같은 모델에 진행 중인 진단이 있음" : null;
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
        // 110 6.10-9 — 이 슬롯을 소비한다 (CRON 은 LAST_EXEC_DT 다음 시각으로 도래를 보므로, 안 바꾸면 분마다 SKIPPED 가 쌓였다).
        // 자기 실행이 진행 중이 아니면 마지막 실행 상태·로그도 이 SKIPPED 로
        Map<String, Object> pSc = new HashMap<>();
        pSc.put("scheduleId", sc.getScheduleId());
        pSc.put("logId",      log.getLogId());
        sql.update("diagSchedule.updateScheduleLastExecSkipped", pSc);
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
                CompositeStatus cs = resolveCompositeStatus(row);
                if (cs == null || cs.isPending()) continue;    // 아직 진행 중이면 skip
                if (cs.anyErrorOrStopped()) {
                    markFinish(row, "ERROR", cs.errorReason());
                } else if (cs.allDone()) {
                    markFinish(row, "DONE", null);
                }
            } catch (Exception e) {
                log.error(">> pollCompletion: log {} 처리 중 오류", row.getLogId(), e);
            }
        }
    }

    /** 기저 진단(DIAG_JOB / STRUCT_DIAG) 중 연결된 것들의 status 를 모아서 합성.
     *  BOTH 는 두 ID 모두 있을 수 있음 — 모두 DONE 이어야 DONE, 하나라도 ERROR/STOPPED 면 ERROR. */
    private CompositeStatus resolveCompositeStatus(DiagScheduleLogVo row) {
        String stdStatus = null, structStatus = null, govStatus = null, batchStatus = null;
        if (row.getDiagJobId() != null && !row.getDiagJobId().isEmpty()) {
            stdStatus = sql.selectOne("diagSchedule.selectDiagJobStatus", row.getDiagJobId());
        }
        if (row.getStructDiagId() != null && !row.getStructDiagId().isEmpty()) {
            structStatus = sql.selectOne("diagSchedule.selectStructDiagStatus", row.getStructDiagId());
        }
        // 104번 WP5-78 — 범정부 메타 검증 (TB_GOV_META_SEND.STATUS: RUNNING·DONE·ERROR)
        if (row.getGovMetaSendId() != null && !row.getGovMetaSendId().isEmpty()) {
            govStatus = sql.selectOne("diagSchedule.selectGovMetaSendStatus", row.getGovMetaSendId());
        }
        // 104번 WP9-67 — 준수 현황 스냅샷·미사용 표준 (TB_BATCH_RUN.STATUS: RUNNING·DONE·ERROR)
        if (row.getBatchRunId() != null && !row.getBatchRunId().isEmpty()) {
            batchStatus = sql.selectOne("daStat.selectBatchRunStatus", row.getBatchRunId());
        }
        if (stdStatus == null && structStatus == null && govStatus == null && batchStatus == null) return null;
        return new CompositeStatus(stdStatus, structStatus, govStatus, batchStatus);
    }

    /** STANDARD / STRUCT / GOV_META / 배치(스냅샷·미사용 표준) 의 기저 상태를 합성해 LOG 최종 상태를 판단 */
    private static final class CompositeStatus {
        final String std;
        final String strct;
        final String gov;
        final String batch;
        CompositeStatus(String std, String strct, String gov, String batch) { this.std = std; this.strct = strct; this.gov = gov; this.batch = batch; }

        boolean isPending() {
            return isRunningLike(std) || isRunningLike(strct) || isRunningLike(gov) || isRunningLike(batch);
        }
        boolean anyErrorOrStopped() {
            return isBadLike(std) || isBadLike(strct) || isBadLike(gov) || isBadLike(batch);
        }
        boolean allDone() {
            // 연결된 쪽이 모두 DONE 이면 전체 DONE. null(미연결) 은 DONE 으로 간주되지 않고, 다른 쪽만 있으면 그 쪽만 판정.
            boolean stdOk   = (std == null)   || "DONE".equalsIgnoreCase(std);
            boolean structOk = (strct == null) || "DONE".equalsIgnoreCase(strct);
            boolean govOk   = (gov == null)   || "DONE".equalsIgnoreCase(gov);
            boolean batchOk = (batch == null) || "DONE".equalsIgnoreCase(batch);
            // 넷 다 null 은 null 반환이라 여기 들어오지 않음.
            return stdOk && structOk && govOk && batchOk;
        }
        String errorReason() {
            if ("ERROR".equalsIgnoreCase(batch))
                return "[BATCH] 배치 실행이 ERROR 로 끝났습니다 — 실행 이력의 배치 결과에서 확인하세요";
            if ("ERROR".equalsIgnoreCase(gov))
                return "[GOV_META] 범정부 메타 검증에서 오류가 나왔습니다 — 범정부 메타 > 생성·전송 이력에서 확인하세요";
            if ("ERROR".equalsIgnoreCase(std) || "ERROR".equalsIgnoreCase(strct))
                return "[DIAG] 기저 진단이 ERROR 로 종료됨 (std=" + std + ", struct=" + strct + ")";
            return "[DIAG] 기저 진단이 STOPPED 로 중단됨 (std=" + std + ", struct=" + strct + ")";
        }
        private static boolean isRunningLike(String s) {
            return "READY".equalsIgnoreCase(s) || "RUNNING".equalsIgnoreCase(s);
        }
        private static boolean isBadLike(String s) {
            return "ERROR".equalsIgnoreCase(s) || "STOPPED".equalsIgnoreCase(s);
        }
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
    /**
     * 60분 넘게 RUNNING 인 LOG 를 ERROR 로 마감한다.
     * 110 6.10-8a — 한 건씩 markFinish 로 닫아 스케줄 LAST_EXEC_* 도 갱신 (예전 일괄 UPDATE 는 LOG 만 바꿨다).
     * 110 6.10-8b — 기저 작업이 아직 진행 중인 LOG 는 빼서 pollCompletion 이 실제 결과로 마감하게 둔다.
     */
    @Scheduled(fixedRate = 300_000L, initialDelay = 120_000L)
    public void expireStale() {
        List<DiagScheduleLogVo> stale;
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("minutes", 60);
            stale = sql.selectList("diagSchedule.selectStaleRunningLogs", p);
        } catch (Exception e) {
            log.error(">> expireStale: 타임아웃 대상 조회 실패", e);
            return;
        }
        if (stale.isEmpty()) return;
        for (DiagScheduleLogVo row : stale) {
            try {
                markFinish(row, "ERROR", "[TIMEOUT] 진단 실행이 비정상 종료됨 (타임아웃 초과)");
            } catch (Exception e) {
                log.error(">> expireStale: log {} 마감 중 오류", row.getLogId(), e);
            }
        }
        log.warn(">> 타임아웃 만료된 스케줄 로그 {} 건", stale.size());
    }
}
