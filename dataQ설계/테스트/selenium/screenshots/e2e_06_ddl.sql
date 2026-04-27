-- ====================================================
-- 데이터 모델: E2E_LOGIC_0427110211 (DM_ID: 8ByhFmfJ48r9Q_PNct6eM1)
-- 생성일시: 2026-04-27 11:02:52
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

