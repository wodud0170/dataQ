package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 진단 스케줄 실행 이력 (TB_DIAG_SCHEDULE_LOG) — 65번 문서 3-2
 */
@Data
public class DiagScheduleLogVo {
    private String logId;
    private String scheduleId;
    private String scheduleNmSnapshot; // 실행 당시 스케줄명 스냅샷

    private String execDt;             // 트리거 시각
    private String execEndDt;          // 완료 시각
    private String execStatus;         // RUNNING | DONE | ERROR | SKIPPED
    private String triggerType;        // AUTO | MANUAL

    private String diagType;           // STANDARD | STRUCT
    private String diagJobId;          // STANDARD → TB_DIAG_JOB
    private String structDiagId;       // STRUCT → TB_STRUCT_DIAG_HISTORY

    private String errorMsg;           // prefix 예: [CONFIG] / [DB] / [TIMEOUT] / [DATA_NOT_FOUND] / [UNKNOWN]
    private Integer execDurationSec;
}
