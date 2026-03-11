package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelStatsVo {
	private String clctId;
	private String dataModelId;
	private int objCnt;
	private int attrCnt;
	private double totalStndRate;
	private double termsStndRate;
	private double wordStndRate;
	private double domainStndRate;
	private String clctDt;
}
