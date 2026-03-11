package com.ndata.quality.common;

import lombok.Data;

@Data
public class NDQualityRetrieveCond {
    private String schId;
    private String schNm;
    private String schDesc;
    private String schObjNm;
    private String schAttrNm;
    private String schSysNm;
    private String schCretUserId;
    private String schUpdtUserId;
    private String schAprvYn;
    private String schStndYn;
    private String schCommStndYn;
    private String schUseYn;
    private String from;//YYYYMMDDHH24MISS
	private String to;	//YYYYMMDDHH24MISS
}
