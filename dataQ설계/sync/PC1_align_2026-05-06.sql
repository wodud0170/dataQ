-- =============================================================
-- PC1 schema align — PC2 와 일치시키기 위한 ALTER (2026-05-06)
-- =============================================================
-- 배경: PC1 / PC2 dataq-db 비교 결과 quality.tb_domain_rule 3건 차이
-- - descr: PC1 varchar(500) → text  (큰쪽으로 통일 — 길이 무제한)
-- - sort_ord: NOT NULL 추가 (PC2 가 이미 보유, 더 strict)
-- - use_yn: NOT NULL 추가
--
-- 적용 절차:
-- 1) PC1 컨테이너에서 아래 SQL 실행 (NULL 데이터 사전 점검 포함)
-- 2) 실행 후 pg_dump 결과가 dataQ설계/DDL_full_schema.sql 과 일치 확인
-- =============================================================

SET search_path TO quality;

-- 0) NULL 데이터 사전 점검 (NOT NULL 추가 전)
\echo '=== NULL 점검 (sort_ord / use_yn) ==='
SELECT COUNT(*) AS null_sort_ord FROM tb_domain_rule WHERE sort_ord IS NULL;
SELECT COUNT(*) AS null_use_yn   FROM tb_domain_rule WHERE use_yn   IS NULL;

-- 1) NULL 이면 기본값 채우기 (NOT NULL 추가 안전)
UPDATE tb_domain_rule SET sort_ord = 1   WHERE sort_ord IS NULL;
UPDATE tb_domain_rule SET use_yn   = 'Y' WHERE use_yn   IS NULL;

-- 2) 타입/제약 변경
ALTER TABLE tb_domain_rule ALTER COLUMN descr    TYPE TEXT;
ALTER TABLE tb_domain_rule ALTER COLUMN sort_ord SET NOT NULL;
ALTER TABLE tb_domain_rule ALTER COLUMN use_yn   SET NOT NULL;

-- 3) 적용 검증
\echo '=== 적용 결과 ==='
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema='quality' AND table_name='tb_domain_rule'
   AND column_name IN ('descr','sort_ord','use_yn')
 ORDER BY 1;
