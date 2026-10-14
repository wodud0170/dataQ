-- 104번 WP9 — 통계·배치 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp9_stat_batch.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 67번 예약 실행 대상 확대 — 배치 실행 기록(TB_BATCH_RUN), 준수 현황 스냅샷(TB_COMPLIANCE_SNAPSHOT),
--      미사용 표준 결과(TB_UNUSED_STD_RESULT), 실행 이력에 배치 실행 ID(TB_DIAG_SCHEDULE_LOG.BATCH_RUN_ID)
--   2. 66번 DA 목표 달성도 — 시스템·모델별 목표(TB_DA_TARGET)
-- 55번 변경 전후 비교·62번 시스템 단위 차이 분석은 DDL 변경 없음.

SET search_path TO quality;

BEGIN;

-- ===== 67번 =====
CREATE TABLE IF NOT EXISTS quality.tb_batch_run (
    run_id          character varying(40)  NOT NULL,
    run_tp          character varying(30)  NOT NULL,
    status          character varying(10)  NOT NULL,
    start_dt        character varying(14),
    end_dt          character varying(14),
    dm_id           character varying(22),
    dict_id         character varying(22),
    summary         character varying(1000),
    error_msg       character varying(2000),
    schedule_log_id character varying(40),
    cret_user_id    character varying(50),
    CONSTRAINT tb_batch_run_pk PRIMARY KEY (run_id)
);
COMMENT ON TABLE  quality.tb_batch_run IS '배치 실행 기록 — 준수 현황 스냅샷·미사용 표준 (104번 WP9-67)';
COMMENT ON COLUMN quality.tb_batch_run.run_tp IS 'COMPLIANCE_SNAPSHOT·UNUSED_STD';
COMMENT ON COLUMN quality.tb_batch_run.status IS 'RUNNING·DONE·ERROR';
CREATE INDEX IF NOT EXISTS ix_batch_run_tp ON quality.tb_batch_run (run_tp, start_dt);

CREATE TABLE IF NOT EXISTS quality.tb_compliance_snapshot (
    run_id          character varying(40)  NOT NULL,
    snap_dt         character varying(14)  NOT NULL,
    dm_id           character varying(22)  NOT NULL,
    dm_nm           character varying(100),
    sys_cd          character varying(22),
    dict_id         character varying(22),
    obj_cnt         integer,
    attr_cnt        integer,
    total_col_cnt   integer,
    issue_col_cnt   integer,
    stnd_rate       numeric(5,1),
    diag_end_dt     character varying(14),
    struct_total    integer,
    struct_diff     integer,
    struct_rate     numeric(5,1),
    struct_diag_dt  character varying(14),
    CONSTRAINT tb_compliance_snapshot_pk PRIMARY KEY (run_id, dm_id)
);
COMMENT ON TABLE  quality.tb_compliance_snapshot IS '준수 현황 스냅샷 — 그 시각 모델별 표준 준수율·구조 일치율 (104번 WP9-67, 추이는 62·66번이 쓴다)';
COMMENT ON COLUMN quality.tb_compliance_snapshot.stnd_rate IS '최신 완료 표준 진단의 (전체 컬럼 - 이슈 컬럼) / 전체 컬럼 × 100. 진단 없으면 NULL';
COMMENT ON COLUMN quality.tb_compliance_snapshot.struct_rate IS '최신 완료 구조 진단의 (1 - 차이 / 전체) × 100. 진단 없으면 NULL';
CREATE INDEX IF NOT EXISTS ix_comp_snap_dm ON quality.tb_compliance_snapshot (dm_id, snap_dt);

CREATE TABLE IF NOT EXISTS quality.tb_unused_std_result (
    run_id          character varying(40)  NOT NULL,
    kind            character varying(10)  NOT NULL,
    std_id          character varying(40)  NOT NULL,
    std_nm          character varying(200),
    eng_abrv_nm     character varying(200),
    extra           character varying(500),
    aprv_yn         character(1),
    cret_dt         character varying(10),
    CONSTRAINT tb_unused_std_result_pk PRIMARY KEY (run_id, kind, std_id)
);
COMMENT ON TABLE quality.tb_unused_std_result IS '미사용 표준 예약 실행 결과 — 실행 시각의 미사용 단어·용어·도메인 (104번 WP9-67)';

ALTER TABLE quality.tb_diag_schedule_log ADD COLUMN IF NOT EXISTS batch_run_id character varying(40);
-- 준수 현황 스냅샷·미사용 표준은 모델 없이도 예약한다 (스냅샷은 사용 중 모델 전부, 미사용 표준은 기본 사전)
ALTER TABLE quality.tb_diag_schedule ALTER COLUMN data_model_id DROP NOT NULL;
COMMENT ON COLUMN quality.tb_diag_schedule_log.batch_run_id IS '준수 현황 스냅샷·미사용 표준 실행 (TB_BATCH_RUN, 104번 WP9-67)';

-- ===== 66번 =====
CREATE TABLE IF NOT EXISTS quality.tb_da_target (
    target_tp        character varying(4)   NOT NULL,
    target_id        character varying(22)  NOT NULL,
    stnd_rate_goal   numeric(5,1),
    struct_rate_goal numeric(5,1),
    target_desc      character varying(500),
    updt_dt          character varying(14),
    updt_user_id     character varying(50),
    CONSTRAINT tb_da_target_pk PRIMARY KEY (target_tp, target_id)
);
COMMENT ON TABLE  quality.tb_da_target IS 'DA 목표 — 시스템·모델별 목표 표준 준수율·구조 일치율 (104번 WP9-66)';
COMMENT ON COLUMN quality.tb_da_target.target_tp IS 'SYS(시스템 — 하위 시스템·모델에 상속)·DM(모델)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_da_target, quality.tb_unused_std_result, quality.tb_compliance_snapshot, quality.tb_batch_run;
-- ALTER TABLE quality.tb_diag_schedule_log DROP COLUMN IF EXISTS batch_run_id;
-- (모델 없는 스케줄을 지운 뒤) ALTER TABLE quality.tb_diag_schedule ALTER COLUMN data_model_id SET NOT NULL;
-- COMMIT;
