package com.ndata.quality.model.std;

import lombok.Data;

/**
 * 품질 진단 컬럼 단위 application-level 동시 실행 방지 lock (TB_QUAL_RUNNING_LOCK, 83번 §6-2).
 * 운영 DB 락 절대 X — 우리 메타DB 안에서만 동작.
 */
@Data
public class QualRunningLockVo {
    private String dmId;
    private String objNm;
    private String attrNm;
    private String diagId;     // 진행 중 진단 ID
    private String userId;     // 진단 트리거한 사용자
    private String startDt;    // YYYYMMDDHH24MISS — 30분 경과 시 stale 자동 정리
}
