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
    /** 104번 WP2 — 시스템 기본 표준사전 (TB_SYS_INFO.DICT_ID). 이 시스템의 모델 등록 때 기본값 */
    private String dictId;
    /** 화면 표시용 사전명 (조회 전용) */
    private String dictNm;
}
