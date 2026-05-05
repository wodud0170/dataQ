package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelObjVo {
	private String clctId;
	private String dataModelId;
	private String objNm;
	private String objNmKr;
	private String objOwner;
	private String objDesc;
	private String objComment;
	private short objAttrCnt;
	private String objCretDt;
	private String objUpdtDt;
	private String useYn;
	private String deletedDt;

	// 79번 진단 대상 제외 관리
	private String stndDiagTargetYn;
	private String stndDiagTargetReason;
	private String structDiagTargetYn;
	private String structDiagTargetReason;
	private String qualDiagTargetYn;
	private String qualDiagTargetReason;
	private String diagTargetUpdtUserId;
	private String diagTargetUpdtDt;
}
