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
	private String bizAreaNm;           // 업무영역명 (조회용 — tb_biz_area JOIN)
	private String subjAreaNm;          // 104번 WP8-38 — 주제영역명 (조회용)
	private String subjAreaId;          // 물리 분류 (FK → TB_SUBJ_AREA)
	/** 104번 WP8-38 — 테이블 수정에서 주제영역을 보내지 않으면 그대로 둔다 (updateDataModelObj) */
	private boolean subjAreaKeep;
	/** 104번 WP8-31 — 테이블 추가 때 보내는 사용자 정의 속성 값 {udpId: 값} (TB_UDP_VAL 에 저장) */
	private java.util.Map<String, Object> udpVals;
}
