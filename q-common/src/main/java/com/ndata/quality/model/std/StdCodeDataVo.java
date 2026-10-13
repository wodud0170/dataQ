package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdCodeDataVo {
    private String id;
    private String codeGrp;
    private String codeNm;
    private String codeEngNm;
    private String codeVal;
    private String codeValDesc;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
    private String dictId;
    // 104번 WP6-3 — 계층 코드 값의 상위 코드값 (상위 코드 그룹의 값)
    private String parentCodeVal;
    private String srcCodeGrp;     // 조회 전용 — 통합 코드에서 값이 나온 하위 코드 그룹
}
