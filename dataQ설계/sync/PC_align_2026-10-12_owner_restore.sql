-- =============================================================
-- 2026-08-23 변경분 복구 — OBJ_OWNER / FK_PARENT_OBJ_OWNER + 컬럼 COMMENT (2026-10-12)
-- =============================================================
-- 배경
--   2026-09-07 (4393879, 98번 1단계) 에 DDL_full_schema.sql / 01_schema.sql 을
--   다시 뜬 DB 가 2026-08-23 ALTER (22187df, 3389f4f) 를 받지 않은 DB 였다.
--   덤프가 그 DB 를 그대로 옮겨 적으면서 아래가 스키마 파일과 로컬 DB 양쪽에서 사라졌다.
--   매퍼(diag.xml / datamodel.xml / dmChangeHistory.xml / qualColRule.xml / qualDiag.xml)
--   는 이 컬럼들을 계속 쓰고 있어 표준 진단 결과 저장, 컬럼 목록 조회 등이 SQL 에러가 난다.
--
--   (1) tb_data_model_attr.fk_parent_obj_owner                      (3389f4f)
--   (2) tb_diag_result.obj_owner + ix_diag_result_owner_obj         (22187df)
--   (3) tb_qual_col_rule.obj_owner DEFAULT '' NOT NULL
--       + pk_tb_qual_col_rule = (dm_id, obj_owner, obj_nm, attr_nm)  (3389f4f)
--   (4) tb_qual_profile_result / tb_qual_rule_result / tb_qual_running_lock /
--       tb_qual_violation_sample .obj_owner                          (3389f4f)
--   (5) 컬럼 COMMENT 23건 (덤프 106 → 89 의 실제 누락분)
--
--   정의는 `git show 4393879^:dataQ설계/DDL_full_schema.sql` 기준.
--   백필 규칙은 DDL_변경이력.md 2026-08-23 항목과 같다
--   — (모델, 테이블명) 이 소유자 하나로만 존재할 때만 채우고, 여럿이면 NULL 로 둔다.
--
-- 적용
--   export PGPASSWORD='admin!123'
--   "/c/Program Files/PostgreSQL/13/bin/psql.exe" -h localhost -p 25433 -U admin -d postgres \
--     -v ON_ERROR_STOP=1 -f dataQ설계/sync/PC_align_2026-10-12_owner_restore.sql
--   (도커 DB 면: docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < 이 파일)
--
--   적용 후 pg_dump 결과가 dataQ설계/DDL_full_schema.sql 과 같아야 한다.
--
-- 멱등: 여러 번 돌려도 안전하다. 이미 적용된 PC 에서는 아무것도 바꾸지 않는다.
-- =============================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. FK 부모 소유자
-- ─────────────────────────────────────────────────────────────
ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS fk_parent_obj_owner character varying(100);

-- 부모 테이블 이름이 모델 안에서 소유자 하나로만 존재하는 행만 채운다.
UPDATE quality.tb_data_model_attr a
   SET fk_parent_obj_owner = p.obj_owner
  FROM (SELECT dm_id, obj_nm, MIN(obj_owner) AS obj_owner
          FROM quality.tb_data_model_obj
         GROUP BY dm_id, obj_nm
        HAVING COUNT(DISTINCT obj_owner) = 1) p
 WHERE a.fk_parent_obj_nm IS NOT NULL
   AND a.fk_parent_obj_owner IS NULL
   AND p.dm_id  = a.dm_id
   AND p.obj_nm = a.fk_parent_obj_nm;

-- ─────────────────────────────────────────────────────────────
-- 2. 표준 진단 결과 소유자
-- ─────────────────────────────────────────────────────────────
ALTER TABLE quality.tb_diag_result ADD COLUMN IF NOT EXISTS obj_owner character varying(100);

UPDATE quality.tb_diag_result r
   SET obj_owner = o.obj_owner
  FROM quality.tb_diag_job j,
       (SELECT dm_id, obj_nm, MIN(obj_owner) AS obj_owner
          FROM quality.tb_data_model_obj
         GROUP BY dm_id, obj_nm
        HAVING COUNT(DISTINCT obj_owner) = 1) o
 WHERE r.obj_owner IS NULL
   AND j.diag_job_id = r.diag_job_id
   AND o.dm_id  = j.dm_id
   AND o.obj_nm = r.obj_nm;

CREATE INDEX IF NOT EXISTS ix_diag_result_owner_obj
    ON quality.tb_diag_result USING btree (diag_job_id, obj_owner, obj_nm, attr_nm);

-- ─────────────────────────────────────────────────────────────
-- 3. 품질 컬럼 룰 — 소유자를 키에 넣는다
--    NULL 이 남아 있으면 PK 를 못 거므로 '' 로 정규화한 뒤 NOT NULL.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE quality.tb_qual_col_rule ADD COLUMN IF NOT EXISTS obj_owner character varying(100) DEFAULT ''::character varying NOT NULL;
UPDATE quality.tb_qual_col_rule SET obj_owner = '' WHERE obj_owner IS NULL;
ALTER TABLE quality.tb_qual_col_rule ALTER COLUMN obj_owner SET DEFAULT ''::character varying;
ALTER TABLE quality.tb_qual_col_rule ALTER COLUMN obj_owner SET NOT NULL;

DO $$
BEGIN
    -- 옛 PK (obj_owner 없음) 이면 내린다
    IF EXISTS (
        SELECT 1
          FROM pg_constraint c
         WHERE c.conrelid = 'quality.tb_qual_col_rule'::regclass
           AND c.conname  = 'pk_tb_qual_col_rule'
           AND NOT EXISTS (SELECT 1 FROM pg_attribute a
                            WHERE a.attrelid = c.conrelid
                              AND a.attnum   = ANY (c.conkey)
                              AND a.attname  = 'obj_owner')
    ) THEN
        ALTER TABLE quality.tb_qual_col_rule DROP CONSTRAINT pk_tb_qual_col_rule;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'quality.tb_qual_col_rule'::regclass
           AND conname  = 'pk_tb_qual_col_rule'
    ) THEN
        ALTER TABLE quality.tb_qual_col_rule
            ADD CONSTRAINT pk_tb_qual_col_rule PRIMARY KEY (dm_id, obj_owner, obj_nm, attr_nm);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────
-- 4. 나머지 품질 진단 테이블 소유자
-- ─────────────────────────────────────────────────────────────
ALTER TABLE quality.tb_qual_profile_result   ADD COLUMN IF NOT EXISTS obj_owner character varying(100);
ALTER TABLE quality.tb_qual_rule_result      ADD COLUMN IF NOT EXISTS obj_owner character varying(100);
ALTER TABLE quality.tb_qual_running_lock     ADD COLUMN IF NOT EXISTS obj_owner character varying(100);
ALTER TABLE quality.tb_qual_violation_sample ADD COLUMN IF NOT EXISTS obj_owner character varying(100);

-- ─────────────────────────────────────────────────────────────
-- 5. 누락된 컬럼 COMMENT (2026-08-23 덤프 원문 그대로)
-- ─────────────────────────────────────────────────────────────
COMMENT ON COLUMN quality.tb_aprv_stats.req_item_nm IS '요청 항목명 (반려 시 원본 삭제되므로 이력 보존용)';
COMMENT ON COLUMN quality.tb_data_model_attr.attr_comment IS 'DB에서 수집한 컬럼 코멘트 원본 (수집 시 자동, 읽기 전용)';
COMMENT ON COLUMN quality.tb_data_model_attr.attr_nm_kr IS '컬럼 논리명 (편집 가능). 최초 수집 시 ATTR_COMMENT 값 복사';
COMMENT ON COLUMN quality.tb_data_model_attr.deleted_dt IS '소프트 삭제 일시 YYYYMMDDHH24MISS';
COMMENT ON COLUMN quality.tb_data_model_attr.use_yn IS '사용여부 Y/N (N=소프트 삭제)';
COMMENT ON COLUMN quality.tb_data_model_clct.added_cnt IS '재수집 시 추가된 테이블·컬럼 합계';
COMMENT ON COLUMN quality.tb_data_model_clct.clct_type IS '스냅샷 원천 (DBMS: 수집, MANUAL: 수동편집, ERWIN: ERwin 임포트)';
COMMENT ON COLUMN quality.tb_data_model_clct.deleted_cnt IS '재수집 시 삭제된 테이블·컬럼 합계';
COMMENT ON COLUMN quality.tb_data_model_clct.modified_cnt IS '재수집 시 변경된 테이블·컬럼 합계';
COMMENT ON COLUMN quality.tb_data_model_constraint.deleted_dt IS 'soft-delete 시각 (YYYYMMDDHH24MISS)';
COMMENT ON COLUMN quality.tb_data_model_constraint.use_yn IS '사용 여부 (Y/N). soft-delete 대상은 N';
COMMENT ON COLUMN quality.tb_data_model_index.deleted_dt IS 'soft-delete 시각 (YYYYMMDDHH24MISS)';
COMMENT ON COLUMN quality.tb_data_model_index.use_yn IS '사용 여부 (Y/N). soft-delete 대상은 N';
COMMENT ON COLUMN quality.tb_data_model_obj.deleted_dt IS '소프트 삭제 일시 YYYYMMDDHH24MISS';
COMMENT ON COLUMN quality.tb_data_model_obj.obj_comment IS 'DB에서 수집한 테이블 코멘트 원본 (수집 시 자동, 읽기 전용)';
COMMENT ON COLUMN quality.tb_data_model_obj.obj_nm_kr IS '테이블 논리명 (편집 가능). 최초 수집 시 OBJ_COMMENT 값 복사';
COMMENT ON COLUMN quality.tb_data_model_obj.use_yn IS '사용여부 Y/N (N=소프트 삭제)';
COMMENT ON COLUMN quality.tb_qual_running_lock.attr_nm IS '컬럼명';
COMMENT ON COLUMN quality.tb_qual_running_lock.diag_id IS '진행 중 진단 ID';
COMMENT ON COLUMN quality.tb_qual_running_lock.dm_id IS '데이터 모델 ID';
COMMENT ON COLUMN quality.tb_qual_running_lock.obj_nm IS '테이블명';
COMMENT ON COLUMN quality.tb_qual_running_lock.start_dt IS 'lock 획득 시각 (YYYYMMDDHH24MISS) — 30분 경과 시 stale 자동 정리';
COMMENT ON COLUMN quality.tb_qual_running_lock.user_id IS '진단 트리거한 사용자';

COMMIT;

-- 적용 확인 (기대값: 7 / 1 / 4 / 23)
SELECT count(*) AS owner_cols
  FROM information_schema.columns
 WHERE table_schema = 'quality'
   AND ((table_name = 'tb_data_model_attr' AND column_name = 'fk_parent_obj_owner')
     OR (table_name IN ('tb_diag_result', 'tb_qual_col_rule', 'tb_qual_profile_result',
                        'tb_qual_rule_result', 'tb_qual_running_lock', 'tb_qual_violation_sample')
         AND column_name = 'obj_owner'));
SELECT count(*) AS owner_index FROM pg_indexes
 WHERE schemaname = 'quality' AND indexname = 'ix_diag_result_owner_obj';
SELECT array_length(c.conkey, 1) AS col_rule_pk_cols
  FROM pg_constraint c
 WHERE c.conrelid = 'quality.tb_qual_col_rule'::regclass AND c.conname = 'pk_tb_qual_col_rule';
SELECT count(*) AS restored_comments
  FROM pg_description d
  JOIN pg_class t     ON t.oid = d.objoid
  JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'quality'
  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.objsubid
 WHERE (t.relname, a.attname) IN (
        ('tb_aprv_stats', 'req_item_nm'),
        ('tb_data_model_attr', 'attr_comment'), ('tb_data_model_attr', 'attr_nm_kr'),
        ('tb_data_model_attr', 'deleted_dt'), ('tb_data_model_attr', 'use_yn'),
        ('tb_data_model_clct', 'added_cnt'), ('tb_data_model_clct', 'clct_type'),
        ('tb_data_model_clct', 'deleted_cnt'), ('tb_data_model_clct', 'modified_cnt'),
        ('tb_data_model_constraint', 'deleted_dt'), ('tb_data_model_constraint', 'use_yn'),
        ('tb_data_model_index', 'deleted_dt'), ('tb_data_model_index', 'use_yn'),
        ('tb_data_model_obj', 'deleted_dt'), ('tb_data_model_obj', 'obj_comment'),
        ('tb_data_model_obj', 'obj_nm_kr'), ('tb_data_model_obj', 'use_yn'),
        ('tb_qual_running_lock', 'attr_nm'), ('tb_qual_running_lock', 'diag_id'),
        ('tb_qual_running_lock', 'dm_id'), ('tb_qual_running_lock', 'obj_nm'),
        ('tb_qual_running_lock', 'start_dt'), ('tb_qual_running_lock', 'user_id'));
