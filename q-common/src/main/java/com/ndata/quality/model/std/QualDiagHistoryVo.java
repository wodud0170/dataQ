package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 데이터 품질 진단 실행 이력 (TB_QUAL_DIAG_HISTORY, 67번 §5.2)
 */
@Data
public class QualDiagHistoryVo {
    private String diagId;
    private String dmId;
    private String dataModelNm;        // joined
    private String diagType;           // VALUE | RULE
    private String diagDt;
    private String endDt;
    private String status;             // READY | RUNNING | DONE | ERROR
    private String targetObjList;      // JSON
    private Integer sampleRate;        // 100/10/1/-1(=1만건 모드)
    private String incrementalYn;      // Y/N
    private String lastDiagDt;
    private Integer totalRules;
    private Integer totalCols;
    private Integer totalViolations;
    private String execUserId;
    private String errorMsg;
    // 83번 Step5 — 진행률
    private Integer progressDone;
    private Integer progressTotal;
}
