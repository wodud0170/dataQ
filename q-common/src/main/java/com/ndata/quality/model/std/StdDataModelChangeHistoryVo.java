package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 88번 거버넌스 — 데이터 모델 변경 이력 (TB_DATA_MODEL_CHANGE_HISTORY)
 *
 * 모든 schema/메타데이터 변경 시 한 row 자동 INSERT.
 * Tier 1 변경은 DDL 재실행 가능 (ddl_snippet 기록).
 */
@Data
public class StdDataModelChangeHistoryVo {
	private Long   changeSeq;
	private String dmId;
	private String changeDt;
	private String changeUserId;
	private String changeType;     // ADD_OBJ / DEL_OBJ / RENAME_OBJ / ADD_ATTR / DEL_ATTR / MODIFY_ATTR_TYPE / MODIFY_ATTR_KR / SWAP_ATTR_ORD / ADD_PK / DEL_PK / ADD_FK / DEL_FK / ...
	private String changeTier;     // TIER1 / TIER1_5 / TIER2
	private String submissionId;   // 묶음 신청 ID (Tier 1 만)
	private String objOwner;
	private String objNm;
	private String attrNm;
	private String constraintNm;
	private String indexNm;
	private String beforeJson;
	private String afterJson;
	private String ddlSnippet;
	private String aprvStatus;     // DRAFT / SUBMITTED / APPROVED / REJECTED (해당 변경의 승인 상태)
	private String aprvUserId;
	private String aprvDt;
	private String aprvComment;
	private String ddlExecDt;
	private String ddlExecUserId;
	private String ddlExecResult; // SUCCESS / FAILED / SKIPPED / NO_PRIVILEGE
	private String ddlExecMessage;
}
