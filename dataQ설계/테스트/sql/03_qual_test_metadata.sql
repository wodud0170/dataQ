-- ============================================================
-- 03. 데이터 품질 진단 테스트용 — dataQ 메타 등록
-- ============================================================
-- 목적: 별도의 UI 수집 단계 없이 SQL 만으로 testdata 스키마를
--       dataQ 의 데이터 모델로 등록.
-- 적용 위치: dataq-db 의 ndata + quality 스키마
-- 생성 ID :
--   ds_id        = 'TESTPGQUAL'
--   data_model_id= 'TESTQUALDM00000000001A'  (22자, char varying(22))
-- ============================================================

-- ------------------------------------------------------------
-- TB_DATA_SOURCE — 자기 자신(dataq-db) 의 testdata 스키마를 데이터소스로 등록
--
--   ⚠️ driver_nm 은 lib/drivers.xml 에 정의된 PostgreSQL 키와 일치해야 함.
--   dataQ UI 에서 데이터소스 추가 시 자동 채워지는 값이지만, SQL 직접 INSERT
--   시에는 PostgreSQL 의 표준 키 ("PostgreSQL") 로 시도. 작동 안 하면 dataQ UI
--   에서 한 번 등록 후 ds_id 만 본 SQL 의 testdata 모델로 매핑.
-- ------------------------------------------------------------
INSERT INTO ndata.TB_DATA_SOURCE (
    DS_ID, DSN, DS_TP, DBMS_TP, DRIVER_NM,
    SVR_ADDR, PORT, USER_ID, PWD, CHARSET,
    DB_NAME, CONN_PROPS, SECURE_YN, CONN_TEST_YN
) VALUES (
    'TESTPGQUAL',
    '품질진단 테스트 (dataq-db/testdata)',
    1,                          -- DS_TP: 1=일반 RDBMS (값 미상이면 1로 시도)
    'POSTGRESQL',
    'PostgreSQL',
    'localhost',
    '25433',
    'admin',
    'admin!123',
    'UTF-8',
    'postgres',
    NULL,
    false,
    'N'
)
ON CONFLICT (DS_ID) DO UPDATE SET
    DSN       = EXCLUDED.DSN,
    SVR_ADDR  = EXCLUDED.SVR_ADDR,
    PORT      = EXCLUDED.PORT,
    USER_ID   = EXCLUDED.USER_ID,
    PWD       = EXCLUDED.PWD,
    DB_NAME   = EXCLUDED.DB_NAME;

-- ------------------------------------------------------------
-- TB_DATA_MODEL — TEST_QUAL_MODEL (testdata 스키마 매핑)
-- ------------------------------------------------------------
INSERT INTO quality.TB_DATA_MODEL (
    DM_ID, DM_NM, DM_DS_ID, VER, MODEL_TYPE, USE_YN,
    CRET_DT, CRET_USER_ID, STRUCT_DIAG_YN
) VALUES (
    'TESTQUALDM00000000001A',
    'TEST_QUAL_MODEL',
    'TESTPGQUAL',
    '1.0',
    'PHYSICAL',
    'Y',
    CURRENT_TIMESTAMP,
    'space',
    'N'
)
ON CONFLICT (DM_ID) DO UPDATE SET
    DM_NM         = EXCLUDED.DM_NM,
    DM_DS_ID      = EXCLUDED.DM_DS_ID,
    USE_YN        = 'Y';

-- ------------------------------------------------------------
-- TB_DATA_MODEL_OBJ — 3개 테이블
-- ------------------------------------------------------------
INSERT INTO quality.TB_DATA_MODEL_OBJ (DM_ID, OBJ_NM, OBJ_NM_KR, OBJ_OWNER, OBJ_ATTR_CNT, USE_YN) VALUES
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  '품질진단_회원', 'testdata', 8, 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   '품질진단_주문', 'testdata', 8, 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', '품질진단_상품', 'testdata', 6, 'Y')
ON CONFLICT (DM_ID, OBJ_NM) DO UPDATE SET
    OBJ_NM_KR    = EXCLUDED.OBJ_NM_KR,
    OBJ_OWNER    = EXCLUDED.OBJ_OWNER,
    OBJ_ATTR_CNT = EXCLUDED.OBJ_ATTR_CNT,
    USE_YN       = 'Y';

-- ------------------------------------------------------------
-- TB_DATA_MODEL_ATTR — 컬럼 메타
-- ------------------------------------------------------------
INSERT INTO quality.TB_DATA_MODEL_ATTR (
    DM_ID, OBJ_NM, ATTR_NM, ATTR_NM_KR,
    DATA_TYPE, DATA_LEN, DATA_DECIMAL_LEN,
    NULLABLE_YN, PK_YN, FK_YN, ATTR_ORD, OBJ_OWNER, USE_YN
) VALUES
-- TB_TEST_MEMBER
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'MEMBER_ID', '회원ID',     'VARCHAR',  20, 0, 'N', 'Y', 'N', 1, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'EMAIL',     '이메일',     'VARCHAR', 100, 0, 'Y', 'N', 'N', 2, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'PHONE',     '전화번호',   'VARCHAR',  20, 0, 'Y', 'N', 'N', 3, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'NAME',      '이름',       'VARCHAR',  50, 0, 'Y', 'N', 'N', 4, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'AGE',       '나이',       'INTEGER',   0, 0, 'Y', 'N', 'N', 5, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'GENDER',    '성별',       'VARCHAR',   2, 0, 'Y', 'N', 'N', 6, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'REG_DT',    '등록일시',   'TIMESTAMP', 0, 0, 'N', 'N', 'N', 7, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'UPDT_DT',   '수정일시',   'TIMESTAMP', 0, 0, 'Y', 'N', 'N', 8, 'testdata', 'Y'),
-- TB_TEST_ORDER
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'ORDER_ID',  '주문ID',     'VARCHAR',  20, 0, 'N', 'Y', 'N', 1, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'MEMBER_ID', '회원ID',     'VARCHAR',  20, 0, 'Y', 'N', 'Y', 2, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'AMOUNT',    '금액',       'NUMERIC', 15, 2, 'Y', 'N', 'N', 3, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'STATUS',    '상태',       'VARCHAR',  10, 0, 'Y', 'N', 'N', 4, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'START_DT',  '시작일',     'DATE',      0, 0, 'Y', 'N', 'N', 5, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'END_DT',    '종료일',     'DATE',      0, 0, 'Y', 'N', 'N', 6, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'REG_DT',    '등록일시',   'TIMESTAMP', 0, 0, 'N', 'N', 'N', 7, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',  'UPDT_DT',   '수정일시',   'TIMESTAMP', 0, 0, 'Y', 'N', 'N', 8, 'testdata', 'Y'),
-- TB_TEST_PRODUCT
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRODUCT_CODE', '상품코드','VARCHAR',  20, 0, 'Y', 'N', 'N', 1, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'NAME',         '상품명',  'VARCHAR', 100, 0, 'Y', 'N', 'N', 2, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRICE',        '가격',    'NUMERIC', 15, 2, 'Y', 'N', 'N', 3, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'CATEGORY',     '카테고리','VARCHAR',  20, 0, 'Y', 'N', 'N', 4, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'REG_DT',       '등록일시','TIMESTAMP', 0, 0, 'N', 'N', 'N', 5, 'testdata', 'Y'),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'UPDT_DT',      '수정일시','TIMESTAMP', 0, 0, 'Y', 'N', 'N', 6, 'testdata', 'Y')
ON CONFLICT (DM_ID, OBJ_NM, ATTR_NM) DO UPDATE SET
    ATTR_NM_KR = EXCLUDED.ATTR_NM_KR,
    DATA_TYPE  = EXCLUDED.DATA_TYPE,
    DATA_LEN   = EXCLUDED.DATA_LEN,
    USE_YN     = 'Y';
