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
	private String modelType;
	private String ver;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
	private String useYn;

    // 실시간 통계 (STATS 테이블 폐기 → 직접 필드)
    private int objCnt;
    private int attrCnt;
    private String clctDt;

    // 현황 필드 (용어 기준 재정의)
    private double stndRate;          // 표준 준수율 (용어+도메인 모두 일치)
    private int nonStndCnt;           // 비표준 건수
    private int noTermsCnt;           // 용어 미존재 건수
    private int domainMismatchCnt;    // 도메인 불일치 건수

    // 구조 진단 결과
    private String structDiagYn;      // 구조진단 일치여부 (Y/N)
    private String structDiagDt;      // 구조진단 최종 실행일시
    private double structDiagRate;    // 구조진단 일치율 (%)

    // 표준화 진단 결과 (최신 Job 기준)
    private double diagStndRate;      // 표준화 진단 준수율 (%)
    private String diagDt;            // 표준화 진단 최종 실행일시

    // 88번 거버넌스
    private String aprvStatus;
    private String requesterUserId;
    private String reqDt;
    private String aprvUserId;
    private String aprvDt;
    private String aprvComment;
    private String submissionId;
}
