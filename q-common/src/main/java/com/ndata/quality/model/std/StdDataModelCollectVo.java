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
	private String dictId;
	private String dictNm;
	/** 104번 WP7-25 — 표준 준수 필수. */
	private String stndReqYn;
	private String clctType;
    private String clctStartDt;
	private String clctEndDt;
	private String clctCmptnYn;
    private String cretUserId;
    // 통계 필드 — 110 4.12-14-a: 수집이력은 그 수집에서 읽은 건수(TB_DATA_MODEL_CLCT.OBJ_CNT·ATTR_CNT, 그 칸이 생기기 전 이력은 null)
    private Integer objCnt;
    private Integer attrCnt;
    // 110 4.12-41-b — 그 수집이 자동 반려한 승인되지 않은 변경 수(TB_DATA_MODEL_CLCT.AUTO_REJECT_CNT, 가져오기·실패·이전 이력은 null)
    private Integer autoRejectCnt;
    private double totalStndRate;
    private double termsStndRate;
    private double wordStndRate;
    private double domainStndRate;
    // 현황 필드 (용어 기준 재정의)
    private double stndRate;          // 표준 준수율 (용어+도메인 모두 일치)
    private int nonStndCnt;           // 비표준 건수
    private int noTermsCnt;           // 용어 미존재 건수
    private int domainMismatchCnt;    // 도메인 불일치 건수
    // 수집 로그 통계
    private int addedCnt;
    private int deletedCnt;
    private int modifiedCnt;
}
