package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 룰 카탈로그 (TB_QUAL_RULE_CATALOG, 67번 §5.6)
 */
@Data
public class QualRuleCatalogVo {
    private String catalogId;
    private String catalogNm;
    private String ruleType;
    private String ruleParams;
    private String category;
    private String descr;
    private String useYn;
}
