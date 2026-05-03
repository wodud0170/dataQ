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


    //용어 검색 조건 추가  - 260316
    private String searchEngTerm;
    private String searchDomain;

    // 검색 모드: "contains"(기본), "start", "end"
    private String schNmMode;
    private String searchEngTermMode;

    //단어 검색 조건 추가
    private String searchEngWord;

    // 분류어 여부 필터 (Y/N)
    private String wordClsfYn;

    //도메인 검색 조건 추가
    private String schDomainGrpNm;
    private String schDataType;
    private String schDataLen;

    // 데이터 품질 진단: DB 가 연결된 모델만 (Y 일 때 DM_DS_ID 가 있고 ndata.TB_DATA_SOURCE 에 존재하는 모델만 반환)
    private String connectedOnly;

}

