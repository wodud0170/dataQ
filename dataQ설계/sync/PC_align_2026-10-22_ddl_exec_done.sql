-- 109번 #17 — [DB 반영] 중간 실패 뒤 다시 실행 (2026-10-22, PC2)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-22_ddl_exec_done.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   TB_DATA_MODEL_CHANGE_HISTORY.DDL_EXEC_DONE — 여러 문장 중 중간에 실패한 [DB 반영] 에서 이미 실행된 문장(JSON 배열).
--   다시 실행하면 이 문장들을 건너뛰고 나머지만 실행한다. 성공하면 비운다. 기존 행은 NULL(지금 동작).
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_data_model_change_history ADD COLUMN IF NOT EXISTS ddl_exec_done text;
COMMENT ON COLUMN quality.tb_data_model_change_history.ddl_exec_done IS '중간에 실패한 [DB 반영] 에서 이미 실행된 문장 (JSON 배열). 다시 실행하면 건너뛰고 나머지만 실행, 성공하면 비움 (109번 #17)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_change_history DROP COLUMN IF EXISTS ddl_exec_done;
-- COMMIT;
