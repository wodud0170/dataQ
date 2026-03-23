package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelCollectVo {
	private String clctId;
	private String dataModelId;
	private String dataModelNm;
	private String dataModelSysCd;
	private String dataModelSysNm;
	private String dataModelDsId;
	private String dataModelDsNm;
	private String dataModelVer;
    private String clctStartDt;
	private String clctEndDt;
	private String clctCmptnYn;
    private String cretUserId;
    // 통계 필드
    private int objCnt;
    private int attrCnt;
    private double totalStndRate;
    private double termsStndRate;
    private double wordStndRate;
    private double domainStndRate;
    // 현황 필드 (용어 기준 재정의)
    private double stndRate;          // 표준 준수율 (용어+도메인 모두 일치)
    private int nonStndCnt;           // 비표준 건수
    private int noTermsCnt;           // 용어 미존재 건수
    private int domainMismatchCnt;    // 도메인 불일치 건수
}
