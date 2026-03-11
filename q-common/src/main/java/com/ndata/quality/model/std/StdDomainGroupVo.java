package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDomainGroupVo {
	private String id;
	private String domainGrpNm;
	private String commStndYn;
	private String magntdOrd;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;	
}
