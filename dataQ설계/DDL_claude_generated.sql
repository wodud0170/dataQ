-- =============================================================
-- Claude가 제공한 DDL 모음 (PostgreSQL)
-- 프로젝트: ndata-quality
-- 정리일: 2026-03-23
-- =============================================================

-- -------------------------------------------------------------
-- 1. TB_DATA_MODEL_SCHEMA
--    데이터모델별 수집 대상 스키마 관리
--    (세션: 278cae7d / 데이터모델 수집 스키마 기능 추가 시)
-- -------------------------------------------------------------
CREATE TABLE TB_DATA_MODEL_SCHEMA (
    DM_ID         VARCHAR(36)  NOT NULL,
    SCHEMA_NM     VARCHAR(100) NOT NULL,
    USE_YN        CHAR(1)      NOT NULL DEFAULT 'Y',
    CRET_DT       VARCHAR(14),
    CRET_USER_ID  VARCHAR(50),
    PRIMARY KEY (DM_ID, SCHEMA_NM)
);

-- -------------------------------------------------------------
-- 2. TB_DIAG_JOB
--    표준화 진단 작업(Job) 이력
--    (세션: 3f1ffb70 / 표준화 진단 기능 구현 시)
-- -------------------------------------------------------------
CREATE TABLE TB_DIAG_JOB (
    DIAG_JOB_ID     VARCHAR(50)     NOT NULL,
    DM_CLCT_ID      VARCHAR(50)     NOT NULL,
    DM_ID           VARCHAR(50)     NOT NULL,
    STATUS          VARCHAR(20)     NOT NULL DEFAULT 'READY',
    TOTAL_CNT       INTEGER         DEFAULT 0,
    PROCESS_CNT     INTEGER         DEFAULT 0,
    RESULT_CNT      INTEGER         DEFAULT 0,
    CRET_DT         VARCHAR(14),
    CRET_USER_ID    VARCHAR(50),
    START_DT        VARCHAR(14),
    END_DT          VARCHAR(14),
    CONSTRAINT PK_TB_DIAG_JOB PRIMARY KEY (DIAG_JOB_ID)
);

-- -------------------------------------------------------------
-- 3. TB_DIAG_RESULT
--    표준화 진단 결과 상세 (1건 = 1이슈)
--    (세션: 3f1ffb70 / 표준화 진단 기능 구현 시)
-- -------------------------------------------------------------
CREATE TABLE TB_DIAG_RESULT (
    RESULT_ID       BIGSERIAL       NOT NULL,
    DIAG_JOB_ID     VARCHAR(50)     NOT NULL,
    OBJ_NM          VARCHAR(200),
    ATTR_NM         VARCHAR(200),
    ATTR_NM_KR      VARCHAR(200),
    DIAG_TYPE       VARCHAR(50)     NOT NULL,
    DIAG_DETAIL     TEXT,
    STD_VALUE       VARCHAR(500),
    ACTUAL_VALUE    VARCHAR(500),
    CONSTRAINT PK_TB_DIAG_RESULT PRIMARY KEY (RESULT_ID)
);
