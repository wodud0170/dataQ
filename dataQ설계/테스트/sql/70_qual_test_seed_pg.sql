-- ============================================================
-- 70번 신 시드: 도메인 룰 + 컬럼 매핑 (기존 04_qual_test_rules 폐기)
-- 적용: docker exec dataq-db psql -U admin -d postgres -f ...
-- ============================================================

-- 0. 구 룰/매핑 정리
DELETE FROM quality.TB_QUAL_RULE_RESULT
 WHERE DIAG_ID IN (SELECT DIAG_ID FROM quality.TB_QUAL_DIAG_HISTORY WHERE DM_ID='TESTQUALDM00000000001A');
DELETE FROM quality.TB_QUAL_PROFILE_RESULT  WHERE DM_ID='TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_PROFILE_HISTORY WHERE DM_ID='TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_DIAG_HISTORY    WHERE DM_ID='TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_COL_RULE        WHERE DM_ID='TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_RULE            WHERE DM_ID='TESTQUALDM00000000001A';

-- 테스트용 도메인 룰 정리 (재실행 멱등)
DELETE FROM quality.TB_DOMAIN_RULE WHERE DOMAIN_RULE_ID LIKE 'DR_TEST_%';

-- ============================================================
-- 1. 도메인 룰 시드
--    - 전화번호 도메인은 1:N (default = SORT_ORD 1: '-' 있음)
--    - 사용자 시나리오: PHONE 컬럼 default 진단 후 위반률 보고 SORT_ORD 2(없음)로 변경 → 재진단
-- ============================================================
-- TB_DOMAIN 시드는 FK 제약(tb_domain_grp) 때문에 생략. TB_DOMAIN_RULE.DOMAIN_ID 는 가상 ID 사용.
-- (LEFT JOIN 으로 도메인명만 NULL 로 받음. 컬럼별 매핑은 DOMAIN_RULE_ID 를 통해 직접 연결)

INSERT INTO quality.TB_DOMAIN_RULE (DOMAIN_RULE_ID, DOMAIN_ID, RULE_NM, RULE_TYPE, RULE_PARAMS, SORT_ORD, USE_YN, DESCR, CRET_USER_ID, CRET_DT)
VALUES
  ('DR_TEST_EMAIL_1',     'TESTDOM_EMAIL', '이메일 표준',                'REGEX',
   '{"pattern":"^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"}', 1, 'Y',
   '표준 이메일 형식', 'space', CURRENT_TIMESTAMP),

  ('DR_TEST_PHONE_DASH',  'TESTDOM_PHONE', '전화번호 (하이픈)',          'REGEX',
   '{"pattern":"^010-\\d{3,4}-\\d{4}$"}', 1, 'Y',
   '010-XXXX-XXXX 형식 — default', 'space', CURRENT_TIMESTAMP),

  ('DR_TEST_PHONE_NODASH','TESTDOM_PHONE', '전화번호 (하이픈 없음)',      'REGEX',
   '{"pattern":"^010\\d{8}$"}', 2, 'Y',
   '01012345678 형식', 'space', CURRENT_TIMESTAMP),

  ('DR_TEST_PHONE_AREA',  'TESTDOM_PHONE', '전화번호 (지역 + 하이픈)',   'REGEX',
   '{"pattern":"^0\\d{1,2}-\\d{3,4}-\\d{4}$"}', 3, 'Y',
   '02-XXX-XXXX / 031-XXXX-XXXX', 'space', CURRENT_TIMESTAMP);

-- ============================================================
-- 2. 사용자 커스텀 룰 시드 (TB_QUAL_RULE)
-- ============================================================
INSERT INTO quality.TB_QUAL_RULE (
    RULE_ID, DM_ID, OBJ_NM, ATTR_NM, RULE_NM, RULE_TYPE, RULE_PARAMS,
    SEVERITY, USE_YN, EST_COST, DESCR, CRET_USER_ID, CRET_DT
) VALUES
('CR_AGE_RANGE',      'TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'AGE',          'AGE_RANGE',           'RANGE',     '{"min":0,"max":150}',           'ERROR', 'Y', 'MID', '나이 0~150', 'space', CURRENT_TIMESTAMP),
('CR_GENDER_ENUM',    'TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'GENDER',       'GENDER_ENUM',         'ENUM',      '{"values":["M","F","U"]}',      'WARN',  'Y', 'LOW', 'M/F/U',     'space', CURRENT_TIMESTAMP),
('CR_AMOUNT_POS',     'TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'AMOUNT',       'AMOUNT_POSITIVE',     'RANGE',     '{"min":0}',                     'ERROR', 'Y', 'MID', '금액 0+',   'space', CURRENT_TIMESTAMP),
('CR_STATUS_ENUM',    'TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'STATUS',       'STATUS_ENUM',         'ENUM',      '{"values":["PAID","PENDING","SHIPPED","CANCELED"]}', 'WARN', 'Y', 'LOW', '주문 상태', 'space', CURRENT_TIMESTAMP),
('CR_DATE_COMPARE',   'TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'END_DT',       'DATE_COMPARE',        'COMPARE',   '{"leftCol":"END_DT","op":">=","rightCol":"START_DT"}', 'ERROR', 'Y', 'MID', '종료>=시작', 'space', CURRENT_TIMESTAMP),
('CR_MEMBER_FK',      'TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'MEMBER_ID',    'MEMBER_FK',           'REFERENCE', '{"refTable":"TB_TEST_MEMBER","refCol":"MEMBER_ID"}', 'ERROR', 'Y', 'MID', '회원 존재', 'space', CURRENT_TIMESTAMP),
('CR_CODE_LEN8',      'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRODUCT_CODE', 'CODE_LEN8',           'LENGTH',    '{"minLen":8,"maxLen":8}',       'WARN',  'Y', 'MID', '코드 8자',   'space', CURRENT_TIMESTAMP),
('CR_NAME_NN',        'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'NAME',         'NAME_NOT_NULL',       'NOT_NULL',  '{}',                            'WARN',  'Y', 'LOW', '상품명 NN', 'space', CURRENT_TIMESTAMP),
('CR_PRICE_POS',      'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRICE',        'PRICE_POSITIVE',      'RANGE',     '{"min":0}',                     'ERROR', 'Y', 'MID', '가격 0+',   'space', CURRENT_TIMESTAMP),
('CR_CATEGORY_ENUM',  'TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'CATEGORY',     'CATEGORY_ENUM',       'ENUM',      '{"values":["ELEC","CLOTH","FOOD","BOOK"]}', 'WARN', 'Y', 'LOW', '카테고리', 'space', CURRENT_TIMESTAMP);

-- ============================================================
-- 3. 컬럼 매핑 시드 (TB_QUAL_COL_RULE)
--    - 도메인 룰 사용: EMAIL, PHONE (PHONE 은 default = SORT_ORD 1)
--    - 커스텀 룰 사용: 위 9개
--    - PK/이름/날짜는 제외 (EXCLUDE_YN='Y')
-- ============================================================
INSERT INTO quality.TB_QUAL_COL_RULE (DM_ID, OBJ_NM, ATTR_NM, DOMAIN_RULE_ID, CUSTOM_RULE_ID, EXCLUDE_YN, UPDT_USER_ID, UPDT_DT) VALUES
-- TB_TEST_MEMBER
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'MEMBER_ID',    NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'EMAIL',        'DR_TEST_EMAIL_1',     NULL, 'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'PHONE',        'DR_TEST_PHONE_DASH',  NULL, 'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'NAME',         NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'AGE',          NULL, 'CR_AGE_RANGE',       'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'GENDER',       NULL, 'CR_GENDER_ENUM',     'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'REG_DT',       NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_MEMBER',  'UPDT_DT',      NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
-- TB_TEST_ORDER
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'ORDER_ID',     NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'MEMBER_ID',    NULL, 'CR_MEMBER_FK',       'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'AMOUNT',       NULL, 'CR_AMOUNT_POS',      'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'STATUS',       NULL, 'CR_STATUS_ENUM',     'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'START_DT',     NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'END_DT',       NULL, 'CR_DATE_COMPARE',    'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'REG_DT',       NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_ORDER',   'UPDT_DT',      NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
-- TB_TEST_PRODUCT
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRODUCT_CODE', NULL, 'CR_CODE_LEN8',       'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'NAME',         NULL, 'CR_NAME_NN',         'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'PRICE',        NULL, 'CR_PRICE_POS',       'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'CATEGORY',     NULL, 'CR_CATEGORY_ENUM',   'N', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'REG_DT',       NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP),
('TESTQUALDM00000000001A', 'TB_TEST_PRODUCT', 'UPDT_DT',      NULL,                  NULL, 'Y', 'space', CURRENT_TIMESTAMP);

-- ============================================================
-- 예상 위반 (모델 단위 풀스캔)
--   EMAIL_REGEX           : 8 (NULL 5 + 형식 위반 3)  ← REGEX 는 NULL 도 위반으로 처리됨 (RuleSqlBuilder)
--                            * 내부 빌더가 NULL 제외라 = 3
--   PHONE_DASH_DEFAULT    : 5 (NULL 3 + 형식 위반 2 — 내부 빌더 NULL 제외 → 2)
--                            * 단, default 010-X 패턴 vs 데이터에 010-X 외 다른 케이스
--                            * MEMBER 데이터 모두 010-XXXX-XXXX 표준이므로 = 2 (badph1='12345', badph2='01012345678901')
--   AGE_RANGE             : 4
--   GENDER_ENUM           : 3
--   AMOUNT_POS            : 5
--   STATUS_ENUM           : 3
--   DATE_COMPARE          : 4
--   MEMBER_FK             : 4
--   CODE_LEN8             : 3
--   NAME_NN               : 2
--   PRICE_POS             : 3
--   CATEGORY_ENUM         : 2
-- 합계 = 38 (NULL 처리에 따라 ±)
-- ============================================================
