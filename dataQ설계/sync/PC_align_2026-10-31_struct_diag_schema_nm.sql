-- 110번 6.10-26 — 구조 진단 이력에 진단한 스키마 목록을 남길 수 있게 칸 길이를 푼다 (2026-10-31, PC1)
--
-- 멱등: 여러 번 적용해도 같다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-31_struct_diag_schema_nm.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   TB_STRUCT_DIAG_HISTORY.SCHEMA_NM varchar(100) → text.
--   구조 진단이 끝날 때 진단한 스키마 이름들을 쉼표로 이어 이 칸에 쓴다(structdiag.updateStructDiagResult). 예전에는 이 칸을 쓰지 않아 늘 NULL 이었다.
--   모델 스키마(TB_DATA_MODEL_SCHEMA.SCHEMA_NM) 하나가 100자까지라 여럿을 이으면 100자를 넘을 수 있고, 넘으면 진단 결과 저장이 실패해 진단이 ERROR 가 된다.
--   기존 행은 그대로(NULL). 의존 뷰 없음(2026-10-31 확인).
-- 시드 없음.

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_struct_diag_history ALTER COLUMN schema_nm TYPE text;
COMMENT ON COLUMN quality.tb_struct_diag_history.schema_nm IS '진단한 스키마 목록 (쉼표로 이음, 110번 6.10-26, 이전 이력은 NULL)';

COMMIT;

-- 되돌리기 (필요할 때만 — 100자를 넘는 값이 있으면 실패한다)
-- BEGIN;
-- ALTER TABLE quality.tb_struct_diag_history ALTER COLUMN schema_nm TYPE character varying(100);
-- COMMIT;
