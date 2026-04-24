package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 진단 스케줄 (TB_DIAG_SCHEDULE) — 65번 문서 3-1
 */
@Data
public class DiagScheduleVo {
    private String scheduleId;
    private String scheduleNm;
    private String diagType;           // STANDARD | STRUCT | BOTH
    private String dataModelId;
    private String dataModelNm;        // joined

    private String scheduleType;       // SIMPLE | CRON
    private String repeatCycle;        // DAILY | WEEKLY | MONTHLY
    private String repeatTime;         // "HH:mm"
    private Integer repeatDayOfWeek;   // 1(월)~7(일)
    private Integer repeatDayOfMonth;  // 1~28
    private String cronExpr;

    private String useYn;              // 활성/비활성
    private String lastExecDt;
    private String lastExecStatus;
    private String lastExecLogId;

    private String cretUserId;
    private String cretDt;
    private String updtUserId;
    private String updtDt;
}
