-- ====================================================
-- 데이터 모델: E2E_LOGIC_0506130611 (DM_ID: 9YsdkiQ2Q288_mgS7YQTvZ)
-- 생성일시: 2026-05-06 13:06:55
-- DB 타입: postgres
-- 총 테이블: 1, 총 컬럼: 5
-- ====================================================

CREATE TABLE TMP_TBL_1 (
    TMP_COL_1                      VARCHAR(255)    ,
    TMP_COL_2                      VARCHAR(255)    ,
    TMP_COL_3                      VARCHAR(255)    ,
    TMP_COL_4                      VARCHAR(255)    ,
    TMP_COL_5                      VARCHAR(255)    
);
COMMENT ON TABLE TMP_TBL_1 IS '고객정보';
COMMENT ON COLUMN TMP_TBL_1.TMP_COL_1 IS '사용자명';
COMMENT ON COLUMN TMP_TBL_1.TMP_COL_2 IS '고객명';
COMMENT ON COLUMN TMP_TBL_1.TMP_COL_3 IS '등록일시';
COMMENT ON COLUMN TMP_TBL_1.TMP_COL_4 IS '주소';
COMMENT ON COLUMN TMP_TBL_1.TMP_COL_5 IS '전화번호';

