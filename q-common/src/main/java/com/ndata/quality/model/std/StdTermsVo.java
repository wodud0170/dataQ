package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdTermsVo {
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
