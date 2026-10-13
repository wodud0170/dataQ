package com.ndata.quality.model.std;

import lombok.Data;

@Data
public class StdDomainGroupVo {
	private String id;
	private String domainGrpNm;
	private String commStndYn;
	private String magntdOrd;
    private String cretDt;
    private String cretUserId;
    private String updtDt;
    private String updtUserId;
    private String dictId;
    // 104번 WP6-4 — 다단계 도메인 그룹
    private String parentGrpId;
    private String parentGrpNm;     // 조회 전용
    private String dfltDataType;
    private String clsfWordNm;
    private Integer sortOrder;
    private Integer level;          // 조회 전용 — 1 = 최상위
    private String path;            // 조회 전용 — "상위 > 하위"
    private Integer childCnt;       // 조회 전용



}
