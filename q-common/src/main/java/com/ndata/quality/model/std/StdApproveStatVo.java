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

    @Data
	public static class RetrieveCond {
		private String reqUserId;
		private String from;//YYYYMMDDHH24MISS
		private String to;	//YYYYMMDDHH24MISS
		private String aprvUserId;
		private List<NDQualityApproveStat> statusList;
		private int limit;
	}
}
