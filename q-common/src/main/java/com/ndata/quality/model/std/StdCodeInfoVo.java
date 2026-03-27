package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdCodeInfoVo {
    private String id;
	private String codeNm;
	private String codeEngAbrvNm;
	private String codeDesc;
	private String domainNm;
	private String dataType;
	private long dataLen;
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
}
