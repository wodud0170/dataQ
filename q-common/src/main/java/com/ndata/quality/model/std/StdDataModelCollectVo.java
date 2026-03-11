package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelCollectVo {
	private String clctId;
	private String dataModelId;
	private String dataModelNm;
	private String dataModelSysCd;
	private String dataModelSysNm;
	private String dataModelDsId;
	private String dataModelDsNm;
	private String dataModelVer;
    private String clctStartDt;
	private String clctEndDt;
	private String clctCmptnYn;
    private String cretUserId;
}
