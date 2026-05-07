package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 룰 카탈로그 (TB_QUAL_RULE_CATALOG, 67번 §5.6 + 83번 §2-1).
 *
 * <p>83번 재설계 — 시스템 기본 (IS_BUILT_IN='Y') 과 사용자 정의 (IS_BUILT_IN='N') 분리.
 * 시스템 기본은 읽기 전용, [복사] 로만 사용자 정의 신규 row 생성 가능.
 * DOMAIN_CLSF_NM 으로 행안부 도메인 분류 자동 추천.</p>
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
    private String isBuiltIn;       // 'Y' = 시스템 기본 (read-only) / 'N' = 사용자 정의 (default)
    private String domainClsfNm;    // 행안부 도메인 분류명 (예: 전화번호, 금액). NULL 가능 (공통 룰)
}
