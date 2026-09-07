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

    /**
     * 98번 — 조회할 표준사전. 사전 계열 목록 조회는 전부 이 값으로 좁힌다.
     *
     * <p>비어 있으면 사전으로 좁히지 않는다. 화면이 사전을 안 보내던 시절과
     * 같은 동작이라 기존 호출이 깨지지 않는다. 컨트롤러에서 비어 있으면
     * 기본 사전을 채워 넣으므로, 실제로 전역 조회가 나가는 경로는 없다.</p>
     */
    private String dictId;

}

