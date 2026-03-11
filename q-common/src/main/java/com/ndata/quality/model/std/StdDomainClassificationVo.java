package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDomainClassificationVo {
	private String id;
	private String domainClsfNm;
	private String domainGrpNm;
	private String commStndYn;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;	
}
