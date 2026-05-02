package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 위반 샘플 행 (TB_QUAL_VIOLATION_SAMPLE, 67번 §5.5)
 */
@Data
public class QualViolationSampleVo {
    private String diagId;
    private String ruleId;
    private String objNm;
    private String attrNm;
    private Integer seq;
    private String pkValues;           // JSON {col1:v1, col2:v2}
    private String violatingVal;
}
