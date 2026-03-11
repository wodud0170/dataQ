package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDomainVo {
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
