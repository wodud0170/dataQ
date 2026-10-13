package com.ndata.quality.model.std;

import java.util.List;

import com.ndata.quality.common.NDQualityApproveStat;

import lombok.Data;

@Data
public class StdApproveStatVo {
    private String id;
	private String reqTp;
	private String reqItemId;
    private String reqItemNm;
    private String reqItemEngNm;
    private short aprvStat;
    private String reqSysCd;
    private String reqUserId;
    private String reqCretDt;
    private String reqUpdtDt;
    private String aprvUserId;
    private String aprvStatUpdtDt;
    private String aprvStatUpdtRsn;
    /** 98번 — 신청 항목이 속한 표준사전 (승인 화면의 상세 조회가 이 사전으로 찾는다) */
    private String dictId;

    @Data
	public static class RetrieveCond {
		private String reqUserId;
		private String from;//YYYYMMDDHH24MISS
		private String to;	//YYYYMMDDHH24MISS
		private String aprvUserId;
		private List<NDQualityApproveStat> statusList;
		private int limit;
		private String dictId;
	}
}
