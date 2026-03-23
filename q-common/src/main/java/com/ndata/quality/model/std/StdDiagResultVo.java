package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDiagResultVo {
    private Long resultId;
    private String diagJobId;
    private String objNm;       // 테이블명
    private String attrNm;      // 컬럼 영문명
    private String attrNmKr;    // 컬럼 한글명
    private String diagType;    // TERM_NOT_EXIST, TERM_KR_NM_MISMATCH, TERM_ENG_NM_MISMATCH, DATA_TYPE_MISMATCH, DATA_LEN_MISMATCH
    private String diagDetail;  // 상세 설명
    private String stdValue;    // 기준값 (용어/도메인 기준)
    private String actualValue; // 실제값 (스키마 실제값)
}
