-- ====================================================
-- 데이터 모델: E2E_LOGIC_0421122951 (DM_ID: 8xn2e6X6AwrbwaeICLe1kP)
-- 생성일시: 2026-04-21 12:30:42
-- DB 타입: postgres
-- 총 테이블: 1, 총 컬럼: 2
-- ====================================================

CREATE TABLE TMP_TBL_1 (
    USER_NM                        VARCHAR(100)    ,
    CUST_NM                        VARCHAR(100)    
);
COMMENT ON TABLE TMP_TBL_1 IS '고객정보';
COMMENT ON COLUMN TMP_TBL_1.USER_NM IS '사용자명';
COMMENT ON COLUMN TMP_TBL_1.CUST_NM IS '고객명';

