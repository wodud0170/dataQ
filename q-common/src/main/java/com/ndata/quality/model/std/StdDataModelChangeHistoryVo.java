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
	/** 114 권고 채택(10-28) — 신청(DRAFT → SUBMITTED)한 시각 yyyyMMddHHmmss. 신청을 거치지 않은 이력은 null */
	private String submitDt;
	private String objOwner;
	private String objNm;
	private String attrNm;
	private String constraintNm;
	private String indexNm;
	private String beforeJson;
	private String afterJson;
	private String ddlSnippet;
	private String ddlDbmsTp;      // 104번 WP3 — 조각을 만든 DBMS (모델 데이터소스 DBMS_TP, 데이터소스 없는 모델은 null)
	private String dsNm;           // 조회 전용 — 모델 데이터소스 이름 ([DB 반영] 대상)
	private String dsDbmsTp;       // 조회 전용 — 모델 데이터소스 DBMS
	private String aprvStatus;     // DRAFT / SUBMITTED / APPROVED / REJECTED (해당 변경의 승인 상태)
	private String aprvUserId;
	private String aprvDt;
	private String aprvComment;
	/** 104번 WP7-25 — 표준 준수 필수 모델에 비표준 컬럼을 넣을 때의 예외 신청 사유 */
	private String stndExceptReason;
	private String ddlExecDt;
	private String ddlExecUserId;
	private String ddlExecResult; // SUCCESS / FAILED / SKIPPED / NO_PRIVILEGE
	private String ddlExecMessage;
}
