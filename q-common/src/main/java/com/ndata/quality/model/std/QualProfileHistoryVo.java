package com.ndata.quality.model.std;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 값 진단 시계열 누적 (TB_QUAL_PROFILE_HISTORY, 70번 §2.3)
 * 통계 메뉴의 시간 축 진단율 추이용.
 */
@Data
public class QualProfileHistoryVo {
    private String     diagId;
    private String     dmId;
    private String     objNm;
    private String     attrNm;
    private Long       totalCnt;
    private Long       nullCnt;
    private Long       distinctCnt;
    private Long       emptyCnt;
    private String     minVal;
    private String     maxVal;
    private BigDecimal avgVal;
    private BigDecimal stdVal;
    private Integer    minLen;
    private Integer    maxLen;
    private String     diagDt;
}
