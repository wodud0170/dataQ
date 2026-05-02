package com.ndata.quality.model.std;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 값 프로파일링 결과 (TB_QUAL_PROFILE_RESULT, 67번 §3)
 */
@Data
public class QualProfileResultVo {
    private String dmId;
    private String dataModelNm;
    private String objNm;
    private String attrNm;
    private String diagId;
    private Long totalCnt;
    private Long nullCnt;
    private Long distinctCnt;
    private Long emptyCnt;
    private String minVal;
    private String maxVal;
    private BigDecimal avgVal;
    private BigDecimal stdVal;
    private Integer minLen;
    private Integer maxLen;
    private String topValues;          // JSON [{v,cnt}]
    private String updatedDt;
}
