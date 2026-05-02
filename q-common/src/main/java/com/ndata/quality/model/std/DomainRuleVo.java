package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 도메인별 검증 규칙 (TB_DOMAIN_RULE, 70번 §2.1)
 * 도메인 1개 → 룰 N개 (전화번호: -있음/없음/슬래시 등)
 * SORT_ORD = 1 이 default.
 */
@Data
public class DomainRuleVo {
    private String  domainRuleId;
    private String  domainId;
    private String  domainNm;       // joined
    private String  ruleNm;
    private String  ruleType;       // REGEX/RANGE/LENGTH/ENUM
    private String  ruleParams;     // JSON
    private Integer sortOrd;
    private String  useYn;
    private String  descr;
    private String  cretUserId;
    private String  cretDt;
    private String  updtUserId;
    private String  updtDt;
}
