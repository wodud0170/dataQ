-- 110번 4.12-14-a — 수집이력의 테이블·컬럼 수를 그 수집 시점 값으로 (2026-10-23, PC2)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-23_clct_cnt.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   TB_DATA_MODEL_CLCT.OBJ_CNT·ATTR_CNT — 그 수집(또는 ERwin·XMI 가져오기)에서 읽은 테이블·컬럼 수. 끝날 때 채운다.
--   수집이력 화면이 예전에는 모델의 지금 건수를 보여 줬다. 기존 행은 NULL(그때 건수를 알 수 없어 빈칸으로 보인다).
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_data_model_clct ADD COLUMN IF NOT EXISTS obj_cnt integer;
ALTER TABLE quality.tb_data_model_clct ADD COLUMN IF NOT EXISTS attr_cnt integer;
COMMENT ON COLUMN quality.tb_data_model_clct.obj_cnt IS '이 수집(가져오기)에서 읽은 테이블 수 (110번 4.12-14-a, 이전 이력은 NULL)';
COMMENT ON COLUMN quality.tb_data_model_clct.attr_cnt IS '이 수집(가져오기)에서 읽은 컬럼 수 (110번 4.12-14-a, 이전 이력은 NULL)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_clct DROP COLUMN IF EXISTS attr_cnt;
-- ALTER TABLE quality.tb_data_model_clct DROP COLUMN IF EXISTS obj_cnt;
-- COMMIT;
