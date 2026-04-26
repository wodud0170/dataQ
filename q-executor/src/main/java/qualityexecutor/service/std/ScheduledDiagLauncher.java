package qualityexecutor.service.std;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.DiagScheduleLogVo;
import com.ndata.quality.model.std.DiagScheduleVo;
import com.ndata.quality.model.std.StdDiagJobVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 스케줄 트리거 시 실제 진단 서비스를 기동하는 런처.
 *
 * <ol>
 *   <li>TB_DIAG_SCHEDULE_LOG 에 RUNNING 이력 insert (scheduleNmSnapshot 포함)</li>
 *   <li>DIAG_TYPE 분기:
 *     <ul>
 *       <li>STANDARD: 최신 CLCT_ID resolve → TB_DIAG_JOB insert (READY) → DiagService Runnable 기동</li>
 *       <li>STRUCT: TB_STRUCT_DIAG_HISTORY 는 StructDiagService 내부에서 생성됨. diagId 만 UUID 로 발급해 넘김</li>
 *       <li>BOTH: STANDARD 를 먼저 실행. STRUCT 는 완료 폴링 단계에서 STANDARD DONE 관찰 후 이어 기동하도록 후속 훅 필요 (Phase 2 1차는 BOTH 를 순차 실행하지 않고 STANDARD 만 실행 + 후속 세션에서 보강)</li>
 *     </ul>
 *   </li>
 *   <li>실패 시 LOG 를 즉시 ERROR 로 마감</li>
 * </ol>
 */
@Slf4j
@Service
public class ScheduledDiagLauncher {

    @Autowired
    private SqlSessionTemplate sql;

    @Autowired
    private ApplicationContext appContext;

    /**
     * 스케줄 1건 실행. 이미 LOG_ID 가 있으면(runNow) 그 로그를 사용, 없으면 새로 생성.
     *
     * @return 사용된 LOG_ID
     */
    public String launch(DiagScheduleVo sc, String triggerType, String existingLogId) {
        String logId = existingLogId != null ? existingLogId : UUID.randomUUID().toString().replace("-", "");

        // 1. RUNNING LOG 확보 (이미 있으면 스킵)
        if (existingLogId == null) {
            DiagScheduleLogVo log = new DiagScheduleLogVo();
            log.setLogId(logId);
            log.setScheduleId(sc.getScheduleId());
            log.setScheduleNmSnapshot(sc.getScheduleNm());
            log.setTriggerType(triggerType);
            log.setDiagType(sc.getDiagType());
            log.setExecStatus("RUNNING");
            sql.insert("diagSchedule.insertLog", log);
        }

        try {
            String diagType = sc.getDiagType();
            // STANDARD + BOTH: 표준화 진단 기동
            if ("STANDARD".equals(diagType) || "BOTH".equals(diagType)) {
                String diagJobId = startStandardDiag(sc);
                Map<String, Object> p = new HashMap<>();
                p.put("logId",     logId);
                p.put("diagJobId", diagJobId);
                sql.update("diagSchedule.setLogDiagJobId", p);
            }
            // STRUCT + BOTH: 구조변경 진단 기동 (BOTH 는 병행 실행)
            if ("STRUCT".equals(diagType) || "BOTH".equals(diagType)) {
                String structDiagId = startStructDiag(sc);
                Map<String, Object> p = new HashMap<>();
                p.put("logId",        logId);
                p.put("structDiagId", structDiagId);
                sql.update("diagSchedule.setLogStructDiagId", p);
            }

            return logId;
        } catch (RuntimeException e) {
            log.error(">> ScheduledDiagLauncher.launch failed: schedule={}, logId={}",
                    sc.getScheduleId(), logId, e);
            // 실패한 LOG 를 즉시 ERROR 로 마감
            Map<String, Object> p = new HashMap<>();
            p.put("logId",      logId);
            p.put("execStatus", "ERROR");
            p.put("errorMsg",   buildErrorMsg(e));
            p.put("diagJobId",    null);
            p.put("structDiagId", null);
            try { sql.update("diagSchedule.updateLogFinish", p); } catch (Exception ignore) {}
            throw e;
        }
    }

    /** TB_DIAG_JOB 생성 + DiagService 기동 */
    private String startStandardDiag(DiagScheduleVo sc) {
        // 1. 최신 CLCT_ID resolve
        String clctId = sql.selectOne("datamodel.selectLatestClctIdByDmId", sc.getDataModelId());
        if (clctId == null || clctId.isEmpty()) {
            throw new IllegalStateException("[DATA_NOT_FOUND] 데이터모델 "
                    + sc.getDataModelId() + " 에 수집 이력이 없어 표준화 진단을 실행할 수 없습니다.");
        }

        // 2. TB_DIAG_JOB READY 생성
        String diagJobId = UUID.randomUUID().toString().replace("-", "");
        StdDiagJobVo job = new StdDiagJobVo();
        job.setDiagJobId(diagJobId);
        job.setClctId(clctId);
        job.setDataModelId(sc.getDataModelId());
        job.setCretUserId(sc.getCretUserId() != null ? sc.getCretUserId() : "SCHEDULER");
        sql.insert("diag.insertDiagJob", job);

        // 3. DiagService Runnable 을 별도 Thread 로 실행
        DiagService svc = new DiagService(diagJobId, clctId, sc.getDataModelId(),
                job.getCretUserId());
        appContext.getAutowireCapableBeanFactory().autowireBean(svc);
        Thread t = new Thread(svc, "DiagService-" + diagJobId.substring(0, 8));
        t.setDaemon(true);
        t.start();

        return diagJobId;
    }

    /**
     * StructDiagService 기동.
     * StructDiagService.updateStatus 는 UPDATE 이므로 TB_STRUCT_DIAG_HISTORY 에
     * READY 행을 먼저 insert 해야 한다 (q-center StructDiagController 의 패턴과 동일).
     */
    private String startStructDiag(DiagScheduleVo sc) {
        String diagId = UUID.randomUUID().toString().replace("-", "");
        String userId = sc.getCretUserId() != null ? sc.getCretUserId() : "SCHEDULER";

        // TB_STRUCT_DIAG_HISTORY READY 생성 (StructDiagService 의 UPDATE 가 유효하도록)
        Map<String, Object> history = new HashMap<>();
        history.put("diagId",      diagId);
        history.put("dataModelId", sc.getDataModelId());
        history.put("status",      "READY");
        history.put("cretUserId",  userId);
        sql.insert("structdiag.insertStructDiagHistory", history);

        StructDiagService svc = new StructDiagService(diagId, sc.getDataModelId(), userId, null);
        appContext.getAutowireCapableBeanFactory().autowireBean(svc);
        Thread t = new Thread(svc, "StructDiagService-" + diagId.substring(0, 8));
        t.setDaemon(true);
        t.start();
        return diagId;
    }

    /** 실패 메시지를 ERROR_MSG 형식(prefix + 한 줄)으로 요약 */
    public static String buildErrorMsg(Throwable e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        // 이미 prefix 있으면 그대로, 없으면 [UNKNOWN] 붙임
        if (msg != null && msg.startsWith("[")) return trim(msg, 1800);
        String cls = e.getClass().getSimpleName();
        String prefix = cls.toLowerCase().contains("timeout") ? "[TIMEOUT]"
                : cls.toLowerCase().contains("sql") ? "[DB]"
                : "[UNKNOWN]";
        return trim(prefix + " " + cls + ": " + msg, 1800);
    }

    private static String trim(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) + "..." : s);
    }
}
