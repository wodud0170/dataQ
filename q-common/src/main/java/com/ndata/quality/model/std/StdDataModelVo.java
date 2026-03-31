package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelVo {
	private String dataModelId;
	private String dataModelNm;
	private String dataModelSysCd;
	private String dataModelSysNm;
	private String dataModelDsId;
	private String dataModelDsNm;
	private String ver;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
	private String useYn;

	private StdDataModelStatsVo dataModelStats;

    // 현황 필드 (용어 기준 재정의)
    private double stndRate;          // 표준 준수율 (용어+도메인 모두 일치)
    private int nonStndCnt;           // 비표준 건수
    private int noTermsCnt;           // 용어 미존재 건수
    private int domainMismatchCnt;    // 도메인 불일치 건수

    // 구조 진단 결과
    private String structDiagYn;      // 구조진단 일치여부 (Y/N)
    private String structDiagDt;      // 구조진단 최종 실행일시

    // 표준화 진단 결과 (최신 Job 기준)
    private double diagStndRate;      // 표준화 진단 준수율 (%)
    private String diagDt;            // 표준화 진단 최종 실행일시
}
