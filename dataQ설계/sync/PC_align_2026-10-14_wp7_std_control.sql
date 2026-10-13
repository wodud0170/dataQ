-- 104번 WP7 — 표준 입력 통제 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp7_std_control.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 25번 표준 준수 강제 — TB_DATA_MODEL.STND_REQ_YN (기본 N = 지금 동작)
--   2. 표준 예외 신청 사유 — TB_DATA_MODEL_CHANGE_HISTORY.STND_EXCEPT_REASON (비표준 컬럼을 결재로 넣을 때)
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_data_model ADD COLUMN IF NOT EXISTS stnd_req_yn character(1) DEFAULT 'N'::bpchar;
COMMENT ON COLUMN quality.tb_data_model.stnd_req_yn IS '표준 준수 필수 — Y 면 비표준 컬럼 추가·수정 저장을 거부하고, 예외는 결재(변경 승인)로 (104번 WP7-25)';

ALTER TABLE quality.tb_data_model_change_history ADD COLUMN IF NOT EXISTS stnd_except_reason character varying(500);
COMMENT ON COLUMN quality.tb_data_model_change_history.stnd_except_reason IS '표준 예외 신청 사유 — 표준 준수 필수 모델에 비표준 컬럼을 넣을 때 (104번 WP7-25)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_change_history DROP COLUMN IF EXISTS stnd_except_reason;
-- ALTER TABLE quality.tb_data_model DROP COLUMN IF EXISTS stnd_req_yn;
-- COMMIT;
