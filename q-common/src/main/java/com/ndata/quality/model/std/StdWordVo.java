package com.ndata.quality.model.std;

import java.util.Map;

import lombok.Data;

@Data
public class StdWordVo {
	/** 98번 — 이 항목이 속한 표준사전. */
	private String dictId;
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
	/** 108 §4.33 — 등록 신청 때 "요청자 지정" 결재 단계의 결재자 {stepNo: userId} (요청 본문만, 매퍼에 안 씀) */
	private Map<String, String> aprvrPicks;
}
