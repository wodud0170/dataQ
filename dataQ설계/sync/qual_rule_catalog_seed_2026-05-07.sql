-- =============================================================
-- 83번 §3 — 행안부 도메인 분류 시스템 기본 카탈로그 시드
-- (TB_QUAL_RULE_CATALOG, IS_BUILT_IN='Y', 읽기 전용)
-- =============================================================
SET search_path TO quality;

-- 기존 시스템 시드가 있을 수 있으면 멱등 처리: catalog_id 가 SEED_ 접두사인 row 만 대상
-- 사용자 정의 룰 (IS_BUILT_IN='N') 은 손대지 않음.
DELETE FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID LIKE 'SEED_%';

-- ── 전화번호 / 팩스번호 / 휴대전화번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_TEL_PHONE',    '전화번호 형식',    'REGEX', '{"pattern":"^0\\d{1,2}-?\\d{3,4}-?\\d{4}$"}',           '행안부 표준', '지역번호 + 4-4 자리, 하이픈 옵션', 'Y','Y','전화번호'),
('SEED_TEL_FAX',      '팩스번호 형식',    'REGEX', '{"pattern":"^0\\d{1,2}-?\\d{3,4}-?\\d{4}$"}',           '행안부 표준', '전화번호와 동일', 'Y','Y','팩스번호'),
('SEED_TEL_MOBILE',   '휴대전화번호 형식','REGEX', '{"pattern":"^01[016789]-?\\d{3,4}-?\\d{4}$"}',          '행안부 표준', '010/011/016/017/018/019', 'Y','Y','휴대전화번호');

-- ── 우편번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_ZIP_NEW',      '우편번호 형식 (신)','REGEX', '{"pattern":"^\\d{5}$"}',                                '행안부 표준', '5자리 신우편번호', 'Y','Y','우편번호'),
('SEED_ZIP_OLD',      '구우편번호 형식',  'REGEX', '{"pattern":"^\\d{3}-?\\d{3}$"}',                         '행안부 표준', '3-3 자리 (옛 우편번호)', 'Y','Y','구우편번호');

-- ── 사업자등록번호 / 법인등록번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_BIZ_REG',      '사업자등록번호 형식','REGEX','{"pattern":"^\\d{3}-?\\d{2}-?\\d{5}$"}',                '행안부 표준', '3-2-5 자리', 'Y','Y','사업자등록번호'),
('SEED_CORP_REG',     '법인등록번호 형식', 'REGEX', '{"pattern":"^\\d{6}-?\\d{7}$"}',                        '행안부 표준', '6-7 자리, 13자리', 'Y','Y','법인등록번호');

-- ── 주민등록번호 / 외국인등록번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_RRN',          '주민등록번호 형식','REGEX', '{"pattern":"^\\d{6}-?[1-4]\\d{6}$"}',                    '행안부 표준', '6-7 자리, 7자리 첫글자 1~4', 'Y','Y','주민등록번호'),
('SEED_FRN',          '외국인등록번호 형식','REGEX','{"pattern":"^\\d{6}-?[5-8]\\d{6}$"}',                   '행안부 표준', '7자리 첫글자 5~8', 'Y','Y','외국인등록번호');

-- ── 운전면허번호 / 여권번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_DRIVER_LIC',   '운전면허번호 형식','REGEX', '{"pattern":"^\\d{2}-?\\d{2}-?\\d{6}-?\\d{2}$"}',         '행안부 표준', '2-2-6-2 자리', 'Y','Y','운전면허번호'),
('SEED_PASSPORT',     '여권번호 형식',    'REGEX', '{"pattern":"^[A-Z]{1,2}\\d{7,8}$"}',                     '행안부 표준', '대문자 1-2 + 숫자 7-8', 'Y','Y','여권번호');

-- ── 신용카드번호 / 차대번호 / 자동차등록번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_CARD',         '신용카드번호 형식','REGEX', '{"pattern":"^\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}$"}',         '행안부 표준', '16자리, 4-4-4-4', 'Y','Y','신용카드번호'),
('SEED_VIN',          '차대번호 형식 (VIN)','REGEX','{"pattern":"^[A-HJ-NPR-Z0-9]{17}$"}',                   '행안부 표준', 'I/O/Q 제외 17자리', 'Y','Y','차대번호'),
('SEED_CAR_REG',      '자동차등록번호 형식','REGEX','{"pattern":"^\\d{2,3}[가-힣]\\d{4}$"}',                 '행안부 표준', '예: 12가1234', 'Y','Y','자동차등록번호');

-- ── 계좌번호 / 아이핀번호 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_ACCOUNT',      '계좌번호 형식 (느슨)','REGEX','{"pattern":"^\\d{10,16}(-\\d+)*$"}',                   '행안부 표준', '은행별 다름 — 느슨한 패턴', 'Y','Y','계좌번호'),
('SEED_IPIN',         '아이핀번호 형식',  'REGEX', '{"pattern":"^\\d{17}$"}',                                '행안부 표준', '17자리', 'Y','Y','아이핀번호');

-- ── 연도/연월/연월일/연월일시분/연월일시분초 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_YEAR',         '연도 형식',        'REGEX', '{"pattern":"^\\d{4}$"}',                                 '행안부 표준', 'YYYY', 'Y','Y','연도'),
('SEED_YM',           '연월 형식',        'REGEX', '{"pattern":"^(\\d{6}|\\d{4}-\\d{2})$"}',                 '행안부 표준', 'YYYYMM 또는 YYYY-MM', 'Y','Y','연월'),
('SEED_YMD',          '연월일 형식',      'REGEX', '{"pattern":"^(\\d{8}|\\d{4}-\\d{2}-\\d{2})$"}',          '행안부 표준', 'YYYYMMDD 또는 YYYY-MM-DD', 'Y','Y','연월일'),
('SEED_YMDHM',        '연월일시분 형식',  'REGEX', '{"pattern":"^\\d{12}$"}',                                '행안부 표준', 'YYYYMMDDHHMM', 'Y','Y','연월일시분'),
('SEED_YMDHMS',       '연월일시분초 형식','REGEX', '{"pattern":"^\\d{14}$"}',                                '행안부 표준', 'YYYYMMDDHHMMSS', 'Y','Y','연월일시분초');

-- ── 시분 / 시분초 / 월 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_HM',           '시분 형식',        'REGEX', '{"pattern":"^\\d{2}:\\d{2}$"}',                          '행안부 표준', 'HH:MM', 'Y','Y','시분'),
('SEED_HMS',          '시분초 형식',      'REGEX', '{"pattern":"^\\d{2}:\\d{2}:\\d{2}$"}',                   '행안부 표준', 'HH:MM:SS', 'Y','Y','시분초'),
('SEED_MONTH',        '월 범위',          'RANGE', '{"min":1,"max":12,"integer":true}',                      '행안부 표준', '1~12 정수', 'Y','Y','월');

-- ── 위경도 / 좌표 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_LAT',          '위도 범위',        'RANGE', '{"min":-90,"max":90}',                                   '행안부 표준', '-90 ~ 90', 'Y','Y','위도'),
('SEED_LNG',          '경도 범위',        'RANGE', '{"min":-180,"max":180}',                                 '행안부 표준', '-180 ~ 180', 'Y','Y','경도'),
('SEED_COORD',        '좌표 형식',        'REGEX', '{"pattern":"^-?\\d+\\.?\\d*,-?\\d+\\.?\\d*$"}',          '행안부 표준', 'lat,lng 쌍', 'Y','Y','좌표');

-- ── 면적 / 금액 / 가격 / 비용 / 요금 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_AREA',         '면적 범위',        'RANGE', '{"min":0}',                                              '행안부 표준', '0 이상', 'Y','Y','면적'),
('SEED_AMT',          '금액 범위',        'RANGE', '{"min":0}',                                              '행안부 표준', '0 이상 (음수 허용 시 오버라이드)', 'Y','Y','금액'),
('SEED_PRICE',        '가격 범위',        'RANGE', '{"min":0}',                                              '행안부 표준', '0 이상', 'Y','Y','가격'),
('SEED_COST',         '비용 범위',        'RANGE', '{"min":0}',                                              '행안부 표준', '0 이상', 'Y','Y','비용'),
('SEED_FEE',          '요금 범위',        'RANGE', '{"min":0}',                                              '행안부 표준', '0 이상', 'Y','Y','요금');

-- ── 율 / 본번 / 부번 / 건물본번/부번/번호 / 일련번호 / 순서 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_RATE',         '율 범위',          'RANGE', '{"min":0,"max":100}',                                    '행안부 표준', '0 ~ 100 (퍼센트)', 'Y','Y','율'),
('SEED_BON_NO',       '본번 범위',        'RANGE', '{"min":0,"integer":true}',                               '행안부 표준', '0 이상 정수', 'Y','Y','본번'),
('SEED_BU_NO',        '부번 범위',        'RANGE', '{"min":0,"integer":true}',                               '행안부 표준', '0 이상 정수', 'Y','Y','부번'),
('SEED_BLDG_BON',     '건물본번 범위',    'RANGE', '{"min":0,"integer":true}',                               '행안부 표준', '0 이상 정수', 'Y','Y','건물본번'),
('SEED_BLDG_BU',      '건물부번 범위',    'RANGE', '{"min":0,"integer":true}',                               '행안부 표준', '0 이상 정수', 'Y','Y','건물부번'),
('SEED_BLDG_NO',      '건물번호 범위',    'RANGE', '{"min":0,"integer":true}',                               '행안부 표준', '0 이상 정수', 'Y','Y','건물번호'),
('SEED_SEQ_NO',       '일련번호 범위',    'RANGE', '{"min":1,"integer":true}',                               '행안부 표준', '1 이상 정수', 'Y','Y','일련번호'),
('SEED_ORD',          '순서 범위',        'RANGE', '{"min":1,"integer":true}',                               '행안부 표준', '1 이상 정수', 'Y','Y','순서');

-- ── 여부 / 유무 ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_YN',           '여부 ENUM',        'ENUM',  '{"values":["Y","N"]}',                                    '행안부 표준', 'Y / N', 'Y','Y','여부'),
('SEED_YOUMUU',       '유무 ENUM',        'ENUM',  '{"values":["Y","N"]}',                                    '행안부 표준', 'Y / N', 'Y','Y','유무');

-- ── NOT NULL 공통 룰 (특정 분류 없음 — 분류 NULL) ──
INSERT INTO TB_QUAL_RULE_CATALOG (CATALOG_ID, CATALOG_NM, RULE_TYPE, RULE_PARAMS, CATEGORY, DESCR, USE_YN, IS_BUILT_IN, DOMAIN_CLSF_NM) VALUES
('SEED_NOT_NULL',     'NOT NULL',         'NOT_NULL','{}',                                                   '공통',       'NULL 금지', 'Y','Y',NULL);

\echo === 시드 결과 ===
SELECT COUNT(*) AS total, COUNT(DOMAIN_CLSF_NM) AS with_clsf
  FROM TB_QUAL_RULE_CATALOG
 WHERE IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%';
