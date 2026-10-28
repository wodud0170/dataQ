-- 114번 §2 권고 채택(10-28) · 110번 5.8-26-c — 데이터 모델 변경 이력에 신청 시각 (2026-10-28, PC2)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-28_submit_dt.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   TB_DATA_MODEL_CHANGE_HISTORY.SUBMIT_DT — 신청(DRAFT → SUBMITTED)한 시각 yyyyMMddHHmmss. 신청할 때 채운다.
--   화면의 "신청 시각" 열(내 변경 신청·데이터 모델 변경 승인·데이터 모델 변경 이력)이 이 값을 보인다 — 예전에는 저장 시각(CHANGE_DT)이었다.
--   신청을 거치지 않은 이력(신청 전 DRAFT, 관리자 직접 반영, TIER2 자동 승인)과 기존 행은 NULL(빈칸으로 보인다).
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_data_model_change_history ADD COLUMN IF NOT EXISTS submit_dt character varying(14);
COMMENT ON COLUMN quality.tb_data_model_change_history.submit_dt IS '신청(DRAFT → SUBMITTED) 시각 yyyyMMddHHmmss (114 권고 채택, 신청하지 않은 이력·이전 이력은 NULL)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_change_history DROP COLUMN IF EXISTS submit_dt;
-- COMMIT;
