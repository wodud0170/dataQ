package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDiagJobVo {
    private String diagJobId;
    private String clctId;
    private String dataModelId;
    private String dataModelNm;   // joined
    private String clctDt;        // joined
    private String status;        // READY, RUNNING, DONE, STOPPED, ERROR
    private int totalCnt;
    private int processCnt;
    private int resultCnt;
    private String cretDt;
    private String cretUserId;
    private String startDt;
    private String endDt;
    private String dbmsTp;        // joined (데이터소스 DBMS 타입)
}
