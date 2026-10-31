-- dataQ설계/sync/PC_align_2026-10-31_attr_udp.sql
-- 104번 WP8-75 컬럼 대상 사용자 정의 속성 (108 §4.27, 2026-10-31, PC1) — 주석만 바꾼다 (테이블·컬럼·인덱스 변경 없음)
-- 멱등: COMMENT ON 은 덮어쓴다. 양쪽 PC 모두 한 번 적용.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-31_attr_udp.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
SET search_path TO quality;

BEGIN;
COMMENT ON TABLE  quality.tb_udp_def IS '사용자 정의 속성 정의 — 데이터 모델 테이블·컬럼에 기관이 더하는 관리 항목 (104번 WP8-31·75)';
COMMENT ON COLUMN quality.tb_udp_def.target_tp IS '대상 — OBJ(테이블)·ATTR(컬럼). 만든 뒤 바꾸지 않는다';
COMMENT ON TABLE  quality.tb_udp_val IS '사용자 정의 속성 값 — 테이블(ATTR_NM 빈 값)·컬럼(ATTR_NM 컬럼 영문명)마다 (104번 WP8-31·75)';
COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- COMMENT ON TABLE  quality.tb_udp_def IS '사용자 정의 속성 정의 — 데이터 모델 테이블에 기관이 더하는 관리 항목 (104번 WP8-31·75)';
-- COMMENT ON COLUMN quality.tb_udp_def.target_tp IS '대상 — OBJ(테이블)';
-- COMMENT ON TABLE  quality.tb_udp_val IS '사용자 정의 속성 값 — 테이블(ATTR_NM 빈 값)마다 (104번 WP8-31·75)';
-- COMMIT;
