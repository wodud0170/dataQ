package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdTermsVo {
	/** 98번 — 이 항목이 속한 표준사전. */
	private String dictId;
	private String id;
	private String termsNm;
	private String termsEngAbrvNm;
	private String termsDesc;
	private String domainNm;
	private String dataType;
	private short dataLen;
	private short dataDecimalLen;
	private String[] allowValLst;
	private String storFmt;
	private String[] exprFmtLst;
	private String codeGrp;
	private String chrgOrg;
	private String[] allophSynmLst;
	private String commStndYn;
	private String privYn;   // 104번 WP5-16 — 개인정보여부 (용어는 NULL 이면 도메인 값)
	private String encrYn;   // 104번 WP5-16 — 암호화여부 (용어는 NULL 이면 도메인 값)
	private String domainPrivYn;   // 조회 전용 — 용어 도메인의 개인정보여부
	private String domainEncrYn;   // 조회 전용 — 용어 도메인의 암호화여부
	// 104번 WP6-3 — 코드 유형 (코드 = CODE_GRP 가 있는 용어)
	private String codeTp;           // SINGLE·HIER·DERIV·INTEG·INDIV
	private String parentCodeGrp;    // 계층·파생의 상위 코드 그룹
	private String[] integSubGrps;   // 통합 코드의 하위 코드 그룹 (등록·수정 때 받음)
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

	private Word[] wordList;

	@Data
	public static class Word {
		private String termsId;
		private String wordId;
		private String wordNm;
		private short wordOrd;
	}
}
