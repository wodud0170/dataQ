package com.ndata.quality.model.std;

import java.util.Map;

import lombok.Data;

@Data
public class StdDomainVo {
	/** 98번 — 이 항목이 속한 표준사전. */
	private String dictId;
	private String id;
	private String domainNm;
	private String domainGrpNm;
	private String domainClsfNm;
	private String domainDesc;
	private String dataType;
	private short dataLen;
	private short dataDecimalLen;
	private String dataUnit;
	private String storFmt;
	private String[] exprFmtLst;
	private String[] allowValLst;
	private String commStndYn;
	private String privYn;   // 104번 WP5-16 — 개인정보여부 (용어는 NULL 이면 도메인 값)
	private String encrYn;   // 104번 WP5-16 — 암호화여부 (용어는 NULL 이면 도메인 값)
	private String magntdOrd;
	private String aprvYn = "N";	// default : N
	private String aprvUserId;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
	private String aprvStatUpdtDt;
	private String reqSysCd;
	private String reqSysNm;
	/** 108 §4.33 — 등록 신청 때 "요청자 지정" 결재 단계의 결재자 {stepNo: userId} (요청 본문만, 매퍼에 안 씀) */
	private Map<String, String> aprvrPicks;
}
