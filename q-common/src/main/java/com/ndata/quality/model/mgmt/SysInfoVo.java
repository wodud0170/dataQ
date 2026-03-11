package com.ndata.quality.model.mgmt;

import lombok.Data;

@Data
public class SysInfoVo {
    private String sysCd;
	private String parentSysCd;
	private short sysTp;       //0 : system group, 1 : system
	private String sysNm;
	private String[] sysDsLst;  //data source list
	private String sysDesc;
    private short level;
    private String[] path;      //system path
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId; 
}
