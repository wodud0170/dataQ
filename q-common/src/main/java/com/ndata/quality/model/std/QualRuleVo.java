package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 데이터 품질 진단 — 룰 정의 (TB_QUAL_RULE, 67번 §4.2)
 */
@Data
public class QualRuleVo {
    private String ruleId;
    private String dmId;
    private String dataModelNm;        // joined
    private String objNm;
    private String attrNm;
    private String domainId;           // 도메인 묶음 룰
    private String domainNm;           // joined
    private String ruleNm;
    private String ruleType;           // NOT_NULL/RANGE/LENGTH/REGEX/ENUM/UNIQUE/REFERENCE/COMPARE/EXPRESSION
    private String ruleParams;         // JSON string
    private String severity;           // ERROR/WARN/INFO
    private String useYn;
    private String incrementalCol;     // 증분 트리거 컬럼 (NULL 이면 풀스캔만)
    private String estCost;            // LOW/MID/HIGH (자동 추정)
    private String descr;
    private String cretUserId;
    private String cretDt;
    private String updtUserId;
    private String updtDt;
    // 결과 조회 시 join 되는 마지막 검증 정보
    private String lastDiagDt;
    private Long lastViolationCnt;
    private Double lastViolationRate;
}
