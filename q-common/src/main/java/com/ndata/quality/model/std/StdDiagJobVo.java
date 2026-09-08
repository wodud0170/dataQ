package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDiagJobVo {
    private String diagJobId;
    private String clctId;
    private String dataModelId;
    /** 98번 — 이 진단이 사용할/사용한 표준사전. */
    private String dictId;
    /** 98번 — 화면 표시용 사전명. */
    private String dictNm;
    private String dataModelNm;   // joined
    private String clctDt;        // joined
    private String status;        // READY, RUNNING, DONE, STOPPED, ERROR
    private int totalCnt;
    private int processCnt;
    private int resultCnt;
    private int issueColCnt;      // 이슈가 있는 고유 컬럼 수
    private String cretDt;
    private String cretUserId;
    private String startDt;
    private String endDt;
    private String dbmsTp;        // joined (데이터소스 DBMS 타입)
}
