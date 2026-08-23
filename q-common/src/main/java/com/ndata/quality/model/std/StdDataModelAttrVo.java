package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelAttrVo {
	private String clctId;
	private String dataModelId;
	private String objOwner;
	private String objNm;
	private String objNmKr;
	private String attrNm;
	private String attrNmKr;
	private String attrComment;
	private String dataType;
	private long dataLen;
	private short dataDecimalLen;
	private String nullableYn;
	private String termsStndYn;
	private String domainStndYn;
	private String[] wordLst;
	private String[] wordStndLst;
	private String pkYn;
	private String fkYn;
	// 85번 — XMI 2.1 관계 매핑용. FK 일 때 참조 부모 테이블/컬럼.
	private String fkParentObjNm;
	private String fkParentObjOwner;   // 부모 테이블 소유자(스키마). 이름만으로는 다중 스키마에서 부모를 특정할 수 없다
	private String fkParentAttrNm;
	private String defaultVal;
	private short attrOrder;
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

	// 88번 거버넌스 — 승인 워크플로우
	private String aprvStatus;
	private String requesterUserId;
	private String reqDt;
	private String aprvUserId;
	private String aprvDt;
	private String aprvComment;
	private String submissionId;
}
