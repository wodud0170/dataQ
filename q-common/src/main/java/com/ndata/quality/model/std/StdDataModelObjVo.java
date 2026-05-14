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

	// 88번 거버넌스 — 승인 워크플로우 + 분류
	private String aprvStatus;          // DRAFT / SUBMITTED / APPROVED / REJECTED (default APPROVED)
	private String requesterUserId;
	private String reqDt;
	private String aprvUserId;
	private String aprvDt;
	private String aprvComment;
	private String submissionId;
	private String tablespaceNm;        // 물리 — DDL `TABLESPACE xxx` 절
	private String bizAreaId;           // 논리 분류 (FK → TB_BIZ_AREA)
	private String subjAreaId;          // 물리 분류 (FK → TB_SUBJ_AREA)
}
