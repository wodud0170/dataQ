-- 110번 4.12-41-b·5.8-18 — 재수집 때 승인되지 않은 변경 자동 반려 수를 수집이력에 (2026-10-28, PC2)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-28_recollect_reject.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   TB_DATA_MODEL_CLCT.AUTO_REJECT_CNT — 그 수집(DBMS 재수집)이 자동 반려한 승인되지 않은 변경(DRAFT·SUBMITTED 이력) 수. 수집이 끝날 때 채운다.
--   사용자 결정(10-28): 데이터 모델을 다시 수집하면 그 모델의 승인되지 않은 변경은 자동 반려하고, 수집 결과가 그대로 모델이 된다.
--   기존 행·ERwin·XMI 가져오기·실패한 수집은 NULL(빈칸으로 보인다).
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_data_model_clct ADD COLUMN IF NOT EXISTS auto_reject_cnt integer;
COMMENT ON COLUMN quality.tb_data_model_clct.auto_reject_cnt IS '이 수집이 자동 반려한 승인되지 않은 변경 수 (110번 4.12-41-b, 가져오기·실패·이전 이력은 NULL)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_clct DROP COLUMN IF EXISTS auto_reject_cnt;
-- COMMIT;
