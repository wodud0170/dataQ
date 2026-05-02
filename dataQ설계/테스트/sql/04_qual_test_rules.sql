-- ============================================================
-- 04. 데이터 품질 진단 테스트용 — TB_QUAL_RULE 시나리오 룰 등록
-- ============================================================
-- 모델 ID = TESTQUALDM00000000001A (TEST_QUAL_MODEL)
-- 룰 16개 — 각 룰별 예상 위반 카운트는 README.md 참고.
--
-- ⚠️ 룰 SQL 빌더 (q-executor RuleSqlBuilder) 가 INCREMENTAL_COL,
--    EST_COST 등을 활용하므로 함께 등록.
-- ============================================================

DELETE FROM quality.TB_QUAL_RULE WHERE DM_ID = 'TESTQUALDM00000000001A';

INSERT INTO quality.TB_QUAL_RULE (
    RULE_ID, DM_ID, OBJ_NM, ATTR_NM, DOMAIN_ID,
    RULE_NM, RULE_TYPE, RULE_PARAMS,
    SEVERITY, USE_YN, INCREMENTAL_COL, EST_COST,
    DESCR, CRET_USER_ID, CRET_DT
) VALUES
-- ============================================================
-- TB_TEST_MEMBER 룰 (6건)
-- ============================================================
('R_TEST_MEMBER_01', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'EMAIL', NULL,
 'EMAIL_NOT_NULL', 'NOT_NULL', '{}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '이메일은 NULL 금지 (예상 위반: 5)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_MEMBER_02', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'EMAIL', NULL,
 'EMAIL_REGEX', 'REGEX', '{"pattern":"^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"}',
 'WARN', 'Y', 'UPDT_DT', 'HIGH',
 '이메일 표준 형식 (예상 위반: 3 — NULL 제외)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_MEMBER_03', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'PHONE', NULL,
 'PHONE_NOT_NULL', 'NOT_NULL', '{}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '전화번호 NULL 금지 (예상 위반: 3)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_MEMBER_04', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'PHONE', NULL,
 'PHONE_REGEX', 'REGEX', '{"pattern":"^0\\d{1,2}-\\d{3,4}-\\d{4}$"}',
 'WARN', 'Y', 'UPDT_DT', 'HIGH',
 '한국 전화번호 형식 (예상 위반: 2 — NULL 제외)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_MEMBER_05', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'AGE', NULL,
 'AGE_RANGE', 'RANGE', '{"min":0,"max":150}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '나이는 0~150 (예상 위반: 4)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_MEMBER_06', 'TESTQUALDM00000000001A', 'TB_TEST_MEMBER', 'GENDER', NULL,
 'GENDER_ENUM', 'ENUM', '{"values":["M","F","U"]}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '성별은 M/F/U 만 허용 (예상 위반: 3 — NULL 제외)',
 'space', CURRENT_TIMESTAMP),

-- ============================================================
-- TB_TEST_ORDER 룰 (5건)
-- ============================================================
('R_TEST_ORDER_01', 'TESTQUALDM00000000001A', 'TB_TEST_ORDER', 'MEMBER_ID', NULL,
 'ORDER_MEMBER_NOT_NULL', 'NOT_NULL', '{}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '주문은 회원ID 필수 (예상 위반: 2)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_ORDER_02', 'TESTQUALDM00000000001A', 'TB_TEST_ORDER', 'MEMBER_ID', NULL,
 'ORDER_MEMBER_FK', 'REFERENCE',
 '{"refTable":"TB_TEST_MEMBER","refCol":"MEMBER_ID"}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '주문의 회원ID 가 회원 테이블에 존재 (예상 위반: 4 — NULL 제외)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_ORDER_03', 'TESTQUALDM00000000001A', 'TB_TEST_ORDER', 'AMOUNT', NULL,
 'ORDER_AMOUNT_POSITIVE', 'RANGE', '{"min":0}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '주문 금액은 0 이상 (예상 위반: 5)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_ORDER_04', 'TESTQUALDM00000000001A', 'TB_TEST_ORDER', 'STATUS', NULL,
 'ORDER_STATUS_ENUM', 'ENUM',
 '{"values":["PAID","PENDING","SHIPPED","CANCELED"]}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '주문 상태 표준값 (예상 위반: 3)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_ORDER_05', 'TESTQUALDM00000000001A', 'TB_TEST_ORDER', 'END_DT', NULL,
 'ORDER_DATE_COMPARE', 'COMPARE',
 '{"leftCol":"END_DT","op":">=","rightCol":"START_DT"}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '종료일 >= 시작일 (예상 위반: 4)',
 'space', CURRENT_TIMESTAMP),

-- ============================================================
-- TB_TEST_PRODUCT 룰 (5건)
-- ============================================================
('R_TEST_PRODUCT_01', 'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRODUCT_CODE', NULL,
 'PRODUCT_CODE_LENGTH', 'LENGTH', '{"minLen":8,"maxLen":8}',
 'WARN', 'Y', 'UPDT_DT', 'MID',
 '상품코드는 정확히 8자 (예상 위반: 3)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_PRODUCT_02', 'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRODUCT_CODE', NULL,
 'PRODUCT_CODE_UNIQUE', 'UNIQUE', '{}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '상품코드 중복 금지 (예상 위반 행: 4 — 2쌍)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_PRODUCT_03', 'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'NAME', NULL,
 'PRODUCT_NAME_NOT_NULL', 'NOT_NULL', '{}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '상품명 NULL 금지 (예상 위반: 2)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_PRODUCT_04', 'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRICE', NULL,
 'PRODUCT_PRICE_POSITIVE', 'RANGE', '{"min":0}',
 'ERROR', 'Y', 'UPDT_DT', 'MID',
 '상품 가격은 0 이상 (예상 위반: 3)',
 'space', CURRENT_TIMESTAMP),

('R_TEST_PRODUCT_05', 'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'CATEGORY', NULL,
 'PRODUCT_CATEGORY_ENUM', 'ENUM',
 '{"values":["ELEC","CLOTH","FOOD","BOOK"]}',
 'WARN', 'Y', 'UPDT_DT', 'LOW',
 '카테고리 표준값 (예상 위반: 2)',
 'space', CURRENT_TIMESTAMP);
