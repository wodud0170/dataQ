package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdWordVo {
	private String id;
	private String wordNm;
	private String wordEngAbrvNm;
	private String wordEngNm;
	private String wordDesc;
	private String wordClsfYn;
	private String domainClsfNm;
	//private List<String> allophSynmLst = new ArrayList<String>();
	//private List<String> forbdnWordLst = new ArrayList<String>();
	private String[] allophSynmLst;
	private String[] forbdnWordLst;
	private String partOfSpeech;	// parts of speech : 품사
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
