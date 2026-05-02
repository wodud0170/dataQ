-- ============================================================
-- 01. 데이터 품질 진단 테스트용 — 진단 대상 테이블 DDL
-- ============================================================
-- 적용 대상 DB: dataq-db 컨테이너 (PostgreSQL 13, 포트 25433)
-- 적용 위치  : 신규 스키마 testdata
-- 사용 목적  : 67번 값 진단 / 업무 규칙 진단의 셀레니움/수동 테스트 대상
-- 정리       : 99_qual_test_cleanup.sql 참고
-- ============================================================

CREATE SCHEMA IF NOT EXISTS testdata;
SET search_path TO testdata;

-- ------------------------------------------------------------
-- TB_TEST_MEMBER  (회원)
--   진단 시나리오:
--     EMAIL_NOT_NULL, EMAIL_REGEX, PHONE_NOT_NULL, PHONE_REGEX,
--     AGE_RANGE, GENDER_ENUM
-- ------------------------------------------------------------
DROP TABLE IF EXISTS TB_TEST_MEMBER CASCADE;
CREATE TABLE TB_TEST_MEMBER (
    MEMBER_ID    VARCHAR(20)  NOT NULL,
    EMAIL        VARCHAR(100),
    PHONE        VARCHAR(20),
    NAME         VARCHAR(50),
    AGE          INTEGER,
    GENDER       VARCHAR(2),
    REG_DT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDT_DT      TIMESTAMP,
    CONSTRAINT PK_TB_TEST_MEMBER PRIMARY KEY (MEMBER_ID)
);
COMMENT ON TABLE TB_TEST_MEMBER IS '품질진단 테스트용 회원';

-- ------------------------------------------------------------
-- TB_TEST_ORDER  (주문)
--   진단 시나리오:
--     MEMBER_FK (REFERENCE), AMOUNT_RANGE,
--     STATUS_ENUM, START_LE_END (COMPARE)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS TB_TEST_ORDER CASCADE;
CREATE TABLE TB_TEST_ORDER (
    ORDER_ID     VARCHAR(20)  NOT NULL,
    MEMBER_ID    VARCHAR(20),
    AMOUNT       NUMERIC(15,2),
    STATUS       VARCHAR(10),
    START_DT     DATE,
    END_DT       DATE,
    REG_DT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDT_DT      TIMESTAMP,
    CONSTRAINT PK_TB_TEST_ORDER PRIMARY KEY (ORDER_ID)
);
COMMENT ON TABLE TB_TEST_ORDER IS '품질진단 테스트용 주문';

-- ------------------------------------------------------------
-- TB_TEST_PRODUCT  (상품)
--   진단 시나리오:
--     CODE_LENGTH (정확히 8자), CODE_UNIQUE,
--     NAME_NOT_NULL, PRICE_RANGE, CATEGORY_ENUM
-- ------------------------------------------------------------
DROP TABLE IF EXISTS TB_TEST_PRODUCT CASCADE;
CREATE TABLE TB_TEST_PRODUCT (
    PRODUCT_CODE VARCHAR(20),                     -- LENGTH 룰 검증을 위해 PK 안 둠
    NAME         VARCHAR(100),
    PRICE        NUMERIC(15,2),
    CATEGORY     VARCHAR(20),
    REG_DT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDT_DT      TIMESTAMP
);
COMMENT ON TABLE TB_TEST_PRODUCT IS '품질진단 테스트용 상품 (UNIQUE/LENGTH 룰 검증 위해 PK 미정의)';

-- 권한
GRANT USAGE ON SCHEMA testdata TO admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA testdata TO admin;
