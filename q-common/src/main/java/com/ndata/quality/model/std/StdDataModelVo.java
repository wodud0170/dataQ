package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelVo {
	private String dataModelId;
	private String dataModelNm;
	private String dataModelSysCd;
	private String dataModelSysNm;
	private String dataModelDsId;
	private String dataModelDsNm;
	private String ver;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
	private String useYn;

	private StdDataModelStatsVo dataModelStats;
}
