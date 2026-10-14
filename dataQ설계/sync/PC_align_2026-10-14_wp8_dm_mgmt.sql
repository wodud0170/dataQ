-- 104번 WP8 — 데이터 모델 관리 보강 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp8_dm_mgmt.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 63번 구조 진단 결과로 현행화 — TB_STRUCT_DIAG_DETAIL 에 실 DB 컬럼 값(코멘트·소수·PK·기본값·순서)과 반영 표시
--   2. 31·75번 사용자 정의 속성 — TB_UDP_DEF(정의) + TB_UDP_VAL(값), 기본 세트 2개(보존기간·데이터 볼륨, 필수 아님)
-- 38번 주제영역은 기존 TB_SUBJ_AREA·TB_DATA_MODEL_OBJ.SUBJ_AREA_ID 를 쓴다 (DDL 변경 없음).

SET search_path TO quality;

BEGIN;

-- ===== 63번 =====
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS curr_attr_nm_kr character varying(500);
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS curr_data_decimal_len integer;
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS curr_pk_yn character(1);
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS curr_default_val character varying(4000);
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS curr_attr_ord integer;
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS applied_dt character varying(14);
ALTER TABLE quality.tb_struct_diag_detail ADD COLUMN IF NOT EXISTS applied_user_id character varying(50);
COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_attr_nm_kr IS '실 DB 컬럼 코멘트 — 모델에 반영할 때 한글명 (104번 WP8-63)';
COMMENT ON COLUMN quality.tb_struct_diag_detail.applied_dt IS '모델에 반영한 시각 — 비면 아직 반영 안 함 (104번 WP8-63)';

-- ===== 31·75번 =====
CREATE TABLE IF NOT EXISTS quality.tb_udp_def (
    udp_id        character varying(40)  NOT NULL,
    target_tp     character varying(10)  DEFAULT 'OBJ'::character varying NOT NULL,
    udp_nm        character varying(100) NOT NULL,
    data_tp       character varying(10)  DEFAULT 'TEXT'::character varying NOT NULL,
    select_vals   text[],
    required_yn   character(1) DEFAULT 'N'::bpchar,
    sort_order    integer DEFAULT 0,
    use_yn        character(1) DEFAULT 'Y'::bpchar,
    dflt_set_yn   character(1) DEFAULT 'N'::bpchar,
    udp_desc      character varying(500),
    cret_dt       character varying(14),
    cret_user_id  character varying(50),
    updt_dt       character varying(14),
    updt_user_id  character varying(50),
    CONSTRAINT tb_udp_def_pk PRIMARY KEY (udp_id)
);
COMMENT ON TABLE  quality.tb_udp_def IS '사용자 정의 속성 정의 — 데이터 모델 테이블에 기관이 더하는 관리 항목 (104번 WP8-31·75)';
COMMENT ON COLUMN quality.tb_udp_def.target_tp IS '대상 — OBJ(테이블)';
COMMENT ON COLUMN quality.tb_udp_def.data_tp IS 'TEXT·NUMBER·DATE·SELECT';
COMMENT ON COLUMN quality.tb_udp_def.dflt_set_yn IS '기본 세트(보존기간·데이터 볼륨)';
CREATE UNIQUE INDEX IF NOT EXISTS tb_udp_def_ux_1 ON quality.tb_udp_def (target_tp, udp_nm);

CREATE TABLE IF NOT EXISTS quality.tb_udp_val (
    udp_id        character varying(40)  NOT NULL,
    dm_id         character varying(40)  NOT NULL,
    obj_owner     character varying(100) DEFAULT ''::character varying NOT NULL,
    obj_nm        character varying(255) NOT NULL,
    attr_nm       character varying(255) DEFAULT ''::character varying NOT NULL,
    val           character varying(4000),
    updt_dt       character varying(14),
    updt_user_id  character varying(50),
    CONSTRAINT tb_udp_val_pk PRIMARY KEY (udp_id, dm_id, obj_owner, obj_nm, attr_nm),
    CONSTRAINT tb_udp_val_fk_def FOREIGN KEY (udp_id) REFERENCES quality.tb_udp_def (udp_id) ON DELETE CASCADE
);
COMMENT ON TABLE quality.tb_udp_val IS '사용자 정의 속성 값 — 테이블(ATTR_NM 빈 값)마다 (104번 WP8-31·75)';
CREATE INDEX IF NOT EXISTS ix_udp_val_obj ON quality.tb_udp_val (dm_id, obj_owner, obj_nm);

-- 기본 세트 (필수 아님 — 기관이 필수로 바꿀 수 있다)
INSERT INTO quality.tb_udp_def (udp_id, target_tp, udp_nm, data_tp, select_vals, required_yn, sort_order, use_yn, dflt_set_yn, udp_desc, cret_dt, cret_user_id)
VALUES ('UDP_RETENTION', 'OBJ', '보존기간', 'SELECT', ARRAY['1년','3년','5년','10년','준영구','영구'], 'N', 1, 'Y', 'Y', '테이블 데이터 보존기간', to_char(now(), 'YYYYMMDDHH24MISS'), 'system'),
       ('UDP_VOLUME',    'OBJ', '데이터 볼륨(건)', 'NUMBER', NULL, 'N', 2, 'Y', 'Y', '예상 또는 현재 행 수', to_char(now(), 'YYYYMMDDHH24MISS'), 'system')
ON CONFLICT (udp_id) DO NOTHING;

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_udp_val; DROP TABLE IF EXISTS quality.tb_udp_def;
-- ALTER TABLE quality.tb_struct_diag_detail DROP COLUMN IF EXISTS curr_attr_nm_kr, DROP COLUMN IF EXISTS curr_data_decimal_len,
--   DROP COLUMN IF EXISTS curr_pk_yn, DROP COLUMN IF EXISTS curr_default_val, DROP COLUMN IF EXISTS curr_attr_ord,
--   DROP COLUMN IF EXISTS applied_dt, DROP COLUMN IF EXISTS applied_user_id;
-- COMMIT;
