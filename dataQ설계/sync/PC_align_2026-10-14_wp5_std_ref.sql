-- 104번 WP5(14·11) — 참조 사전·상위 사전 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp5_std_ref.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 14번 공통표준 골라 넣기 — TB_STD_DICT.DICT_TP: ORG(기관 사전, 기본) / REF(참조 사전 — 읽기 전용)
--   2. 11번 상위 표준 상속 — TB_STD_DICT.PARENT_DICT_ID (같은 테이블 자기 참조, NULL 이면 상위 없음)
-- 시드 없음. 기존 사전은 모두 ORG·상위 없음 (지금 동작).

SET search_path TO quality;

BEGIN;

ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS dict_tp character varying(10) DEFAULT 'ORG'::character varying;
COMMENT ON COLUMN quality.tb_std_dict.dict_tp IS 'ORG(기관 사전) / REF(참조 사전 — 읽기 전용, 공통표준 가져오기의 원본) (104번 WP5-14)';

ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS parent_dict_id character varying(22);
COMMENT ON COLUMN quality.tb_std_dict.parent_dict_id IS '상위 사전 — 진단·컬럼 표준 검사·목록이 상위 사전 항목도 본다 (가까운 사전 우선, 104번 WP5-11)';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tb_std_dict_fk_parent') THEN
    ALTER TABLE quality.tb_std_dict ADD CONSTRAINT tb_std_dict_fk_parent
      FOREIGN KEY (parent_dict_id) REFERENCES quality.tb_std_dict (dict_id);
  END IF;
END $$;

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_std_dict DROP CONSTRAINT IF EXISTS tb_std_dict_fk_parent;
-- ALTER TABLE quality.tb_std_dict DROP COLUMN IF EXISTS parent_dict_id, DROP COLUMN IF EXISTS dict_tp;
-- COMMIT;
