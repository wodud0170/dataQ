package com.ndata.quality.model.std;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 룰 진단 결과 (TB_QUAL_RULE_RESULT, 67번 §5.4)
 */
@Data
public class QualRuleResultVo {
    private String diagId;
    private String ruleId;
    private String ruleNm;             // joined
    private String ruleType;           // joined
    private String severity;           // joined
    private String objNm;
    private String attrNm;
    private Long totalCnt;
    private Long violationCnt;
    private BigDecimal violationRate;
    private Integer sampleCnt;
    private String errorMsg;
}
