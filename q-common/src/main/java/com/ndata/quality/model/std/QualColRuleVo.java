package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 컬럼 → 적용 규칙 매핑 (TB_QUAL_COL_RULE, 70번 §2.2)
 * 행 없으면 도메인의 SORT_ORD=1 룰이 자동 default.
 */
@Data
public class QualColRuleVo {
    private String dmId;
    private String objOwner;         // 스키마. 컬럼 식별 키의 일부
    private String objNm;
    private String attrNm;
    private String domainRuleId;     // 도메인 룰 사용 시
    private String customRuleId;     // 사용자 커스텀 룰 사용 시
    private String excludeYn;        // Y = 진단 제외 명시
    private String updtUserId;
    private String updtDt;

    // joined effective rule 정보 (조회용)
    private String effectiveRuleNm;
    private String effectiveRuleType;
    private String effectiveRuleParams;
    private Integer effectiveSortOrd;
    private String effectiveSource;  // 'DOMAIN' / 'CUSTOM' / 'DEFAULT' / 'NONE'
}
