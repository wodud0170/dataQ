package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDataModelAttrVo {
	private String clctId;
	private String dataModelId;
	private String objNm;
	private String objNmKr;
	private String attrNm;
	private String attrNmKr;
	private String dataType;
	private long dataLen;
	private short dataDecimalLen;
	private String nullableYn;
	private String termsStndYn;
	private String domainStndYn;
	private String[] wordLst;
	private String[] wordStndLst;
	private String pkYn;
	private String fkYn;
	private String defaultVal;
	private short attrOrder;
}
