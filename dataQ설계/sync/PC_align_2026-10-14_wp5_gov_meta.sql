-- 104번 WP5 — 개인정보·암호화 표시(16번) + 범정부 메타 연계 구현 ①(78번) (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp5_gov_meta.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 16번 — TB_DOMAIN·TB_TERMS·TB_DATA_MODEL_ATTR 의 PRIV_YN·ENCR_YN (개인정보여부·암호화여부).
--      도메인은 기본 'N', 용어·컬럼은 비우면 위(용어 → 도메인)를 따른다.
--   2. 78번 — 기관·연계 설정, 업무분류체계(BRM), 정보시스템·DB 항목, 코드 매핑, 생성 이력·검증 오류 (105번 §6.2)
--   3. 78번 — TB_DATA_MODEL_OBJ 테이블 항목 7열, TB_DATA_MODEL_ATTR 컬럼 항목 4열(원본 데이터타입 포함), TB_DIAG_SCHEDULE_LOG 생성 이력 연결
-- 시드 없음 — 코드표·규격을 받으면 TB_GOV_META_CODE_MAP 에 데이터만 넣는다.

SET search_path TO quality;

BEGIN;

-- ===== 16번 개인정보·암호화 =====
ALTER TABLE quality.tb_domain ADD COLUMN IF NOT EXISTS priv_yn character(1) DEFAULT 'N'::bpchar;
ALTER TABLE quality.tb_domain ADD COLUMN IF NOT EXISTS encr_yn character(1) DEFAULT 'N'::bpchar;
COMMENT ON COLUMN quality.tb_domain.priv_yn IS '개인정보여부 (104번 WP5-16)';
COMMENT ON COLUMN quality.tb_domain.encr_yn IS '암호화여부 (104번 WP5-16)';

ALTER TABLE quality.tb_terms ADD COLUMN IF NOT EXISTS priv_yn character(1);
ALTER TABLE quality.tb_terms ADD COLUMN IF NOT EXISTS encr_yn character(1);
COMMENT ON COLUMN quality.tb_terms.priv_yn IS '개인정보여부 — NULL 이면 도메인 값 (104번 WP5-16)';
COMMENT ON COLUMN quality.tb_terms.encr_yn IS '암호화여부 — NULL 이면 도메인 값 (104번 WP5-16)';

ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS priv_yn character(1);
ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS encr_yn character(1);
COMMENT ON COLUMN quality.tb_data_model_attr.priv_yn IS '개인정보여부 — NULL 이면 용어 → 도메인 값 (104번 WP5-16)';
COMMENT ON COLUMN quality.tb_data_model_attr.encr_yn IS '암호화여부 — NULL 이면 용어 → 도메인 값 (104번 WP5-16)';

-- ===== 78번 범정부 메타 — 테이블·컬럼 항목 =====
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS gov_send_yn character(1) DEFAULT 'N'::bpchar;
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS tbl_tp character varying(20);
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS occr_cycle character varying(20);
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS open_tp character varying(10);
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS open_limit_reason character varying(500);
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS open_limit_basis character varying(500);
ALTER TABLE quality.tb_data_model_obj ADD COLUMN IF NOT EXISTS open_data_lst_nm character varying(500);
COMMENT ON COLUMN quality.tb_data_model_obj.gov_send_yn IS '범정부 메타 전송대상 (104번 WP5-78)';
COMMENT ON COLUMN quality.tb_data_model_obj.tbl_tp IS '테이블유형 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_obj.occr_cycle IS '발생주기 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_obj.open_tp IS '공개/비공개여부 — 공개·부분공개·비공개 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_obj.open_limit_reason IS '부분공개·비공개 사유 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_obj.open_limit_basis IS '부분공개·비공개 관련근거 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_obj.open_data_lst_nm IS '개방데이터목록 (별표 4)';

ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS dbms_data_type character varying(100);
ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS open_tp character varying(10);
ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS open_limit_reason character varying(500);
ALTER TABLE quality.tb_data_model_attr ADD COLUMN IF NOT EXISTS cnstr_desc character varying(1000);
COMMENT ON COLUMN quality.tb_data_model_attr.dbms_data_type IS '수집 원본 데이터타입 (DBMS 종속 타입명, 104번 WP5-78)';
COMMENT ON COLUMN quality.tb_data_model_attr.open_tp IS '컬럼 공개/비공개여부 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_attr.open_limit_reason IS '컬럼 비공개 사유 (별표 4)';
COMMENT ON COLUMN quality.tb_data_model_attr.cnstr_desc IS '제약조건 — 허용범위·구분값·기본값 등 (별표 4)';

COMMENT ON COLUMN quality.tb_diag_schedule.diag_type IS 'STANDARD | STRUCT | BOTH | GOV_META';
ALTER TABLE quality.tb_diag_schedule_log ADD COLUMN IF NOT EXISTS gov_meta_send_id character varying(40);
COMMENT ON COLUMN quality.tb_diag_schedule_log.gov_meta_send_id IS '범정부 메타 생성 이력 (TB_GOV_META_SEND.SEND_ID, DIAG_TYPE=GOV_META, 104번 WP5-78)';

-- ===== 78번 범정부 메타 — 기관·시스템·DB·코드·이력 =====
CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_conf (
    conf_id            character varying(20)  NOT NULL,
    inst_nm            character varying(200),
    inst_cd            character varying(20),
    send_sys_cd        character varying(50),
    link_mthd          character varying(20),
    inst_meta_user_id  character varying(100),
    sftp_host          character varying(200),
    sftp_port          integer,
    sftp_user_id       character varying(100),
    sftp_pwd           character varying(500),
    sftp_dir           character varying(500),
    tmp_file_dir       character varying(500),
    meta_file_dir      character varying(500),
    file_keep_cnt      integer,
    file_fmt           character varying(20),
    file_encoding      character varying(20),
    dflt_brm_id        character varying(40),
    cret_dt            character varying(14),
    cret_user_id       character varying(50),
    updt_dt            character varying(14),
    updt_user_id       character varying(50),
    CONSTRAINT tb_gov_meta_conf_pk PRIMARY KEY (conf_id)
);
COMMENT ON TABLE  quality.tb_gov_meta_conf IS '범정부 메타 기관·연계 설정 (기관 1행, 104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_meta_conf.link_mthd IS '연계 방식 — INST_META(기관메타 업로드), CENTRAL(중앙메타), SFTP';
COMMENT ON COLUMN quality.tb_gov_meta_conf.sftp_pwd IS 'SFTP 비밀번호 (암호화 저장, 조회 응답에 넣지 않음)';

CREATE TABLE IF NOT EXISTS quality.tb_gov_brm (
    brm_id         character varying(40)  NOT NULL,
    brm_nm         character varying(300) NOT NULL,
    parent_brm_id  character varying(40),
    brm_lvl        integer,
    brm_path       character varying(1000),
    inst_cd        character varying(20),
    brm_src        character varying(10)  NOT NULL,
    use_yn         character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt        character varying(14),
    cret_user_id   character varying(50),
    updt_dt        character varying(14),
    updt_user_id   character varying(50),
    CONSTRAINT tb_gov_brm_pk PRIMARY KEY (brm_id)
);
COMMENT ON TABLE  quality.tb_gov_brm IS '업무분류체계(BRM) — 정부기능분류체계 반입 또는 기관 자체 (104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_brm.brm_src IS 'GOV(정부기능분류체계 반입), INST(기관 자체)';
CREATE INDEX IF NOT EXISTS ix_gov_brm_parent ON quality.tb_gov_brm (parent_brm_id);

CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_sys (
    sys_cd          character varying(22)  NOT NULL,
    gov_sys_nm      character varying(200),
    gov_sys_cd      character varying(20),
    gov_sys_tp      character varying(20),
    std_dstb_tp     character varying(20),
    oper_dept_nm    character varying(200),
    chrg_user_nm    character varying(100),
    chrg_telno      character varying(50),
    chrg_email      character varying(200),
    rlt_law_cn      character varying(1000),
    bld_ym          character varying(6),
    last_upgrd_ym   character varying(6),
    send_yn         character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt         character varying(14),
    cret_user_id    character varying(50),
    updt_dt         character varying(14),
    updt_user_id    character varying(50),
    CONSTRAINT tb_gov_meta_sys_pk PRIMARY KEY (sys_cd)
);
COMMENT ON TABLE  quality.tb_gov_meta_sys IS '범정부 메타 정보시스템 항목 (TB_SYS_INFO 시스템 노드, 104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_meta_sys.gov_sys_nm IS '정보시스템명 — IRM 등록명과 같게';
COMMENT ON COLUMN quality.tb_gov_meta_sys.gov_sys_cd IS '정보시스템코드 — 기관코드 7자리 + "_" + 일련번호 5자리';
COMMENT ON COLUMN quality.tb_gov_meta_sys.send_yn IS '전송대상';

CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_db (
    ds_id              character varying(50)  NOT NULL,
    rep_dm_id          character varying(22),
    db_nm_kr           character varying(200),
    db_nm_en           character varying(200),
    db_desc            character varying(1000),
    db_form_cd         character varying(20),
    brm_id             character varying(40),
    dbms_nm            character varying(100),
    dbms_ver           character varying(100),
    os_nm              character varying(100),
    os_ver             character varying(100),
    dbms_info_clct_dt  character varying(14),
    cret_dt            character varying(14),
    cret_user_id       character varying(50),
    updt_dt            character varying(14),
    updt_user_id       character varying(50),
    CONSTRAINT tb_gov_meta_db_pk PRIMARY KEY (ds_id)
);
COMMENT ON TABLE  quality.tb_gov_meta_db IS '범정부 메타 DB 항목 (ndata.TB_DATA_SOURCE 단위, 104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_meta_db.rep_dm_id IS '대표 모델 — 한 DB 를 여러 모델이 쓸 때 테이블·컬럼을 가져올 모델 (104번 §3.3 결정)';
COMMENT ON COLUMN quality.tb_gov_meta_db.dbms_nm IS 'DBMS 제품명 (수집 때 JDBC 로 읽음)';
COMMENT ON COLUMN quality.tb_gov_meta_db.dbms_ver IS 'DBMS 버전 (수집 때 JDBC 로 읽음)';

CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_code_map (
    code_grp     character varying(30)  NOT NULL,
    local_val    character varying(100) NOT NULL,
    gov_code     character varying(50)  NOT NULL,
    gov_code_nm  character varying(200),
    sort_order   integer DEFAULT 0 NOT NULL,
    use_yn       character(1) DEFAULT 'Y'::bpchar NOT NULL,
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_gov_meta_code_map_pk PRIMARY KEY (code_grp, local_val)
);
COMMENT ON TABLE  quality.tb_gov_meta_code_map IS '우리 값 → 범정부 코드 (104번 WP5-78). 그룹: DB_FORM, DBMS, DBMS_VER, OS, OS_VER, TBL_TP, OCCR_CYCLE, OPEN_TP';

CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_send (
    send_id          character varying(40)  NOT NULL,
    dm_id            character varying(22),
    sys_cd           character varying(22),
    ds_id            character varying(50),
    trigger_type     character varying(20)  NOT NULL,
    schedule_log_id  character varying(40),
    status           character varying(20)  NOT NULL,
    start_dt         character varying(14),
    end_dt           character varying(14),
    sys_cnt          integer DEFAULT 0,
    db_cnt           integer DEFAULT 0,
    tbl_cnt          integer DEFAULT 0,
    col_cnt          integer DEFAULT 0,
    err_cnt          integer DEFAULT 0,
    warn_cnt         integer DEFAULT 0,
    file_nm          character varying(300),
    file_path        character varying(1000),
    link_mthd        character varying(20),
    send_dt          character varying(14),
    send_status      character varying(20),
    send_msg         character varying(2000),
    cret_user_id     character varying(50),
    CONSTRAINT tb_gov_meta_send_pk PRIMARY KEY (send_id)
);
COMMENT ON TABLE  quality.tb_gov_meta_send IS '범정부 메타 생성·전송 이력 (104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_meta_send.trigger_type IS 'MANUAL(화면), SCHEDULE(예약 실행)';
COMMENT ON COLUMN quality.tb_gov_meta_send.status IS 'READY, RUNNING, DONE, ERROR';
CREATE INDEX IF NOT EXISTS ix_gov_meta_send_dt ON quality.tb_gov_meta_send (start_dt);

CREATE TABLE IF NOT EXISTS quality.tb_gov_meta_send_err (
    send_id    character varying(40)  NOT NULL,
    err_seq    integer                NOT NULL,
    err_lvl    character varying(10)  NOT NULL,
    sev        character varying(10)  NOT NULL,
    sys_cd     character varying(22),
    ds_id      character varying(50),
    obj_owner  character varying(100),
    obj_nm     character varying(255),
    attr_nm    character varying(255),
    item_nm    character varying(100),
    err_msg    character varying(1000) NOT NULL,
    CONSTRAINT tb_gov_meta_send_err_pk PRIMARY KEY (send_id, err_seq),
    CONSTRAINT tb_gov_meta_send_err_fk FOREIGN KEY (send_id) REFERENCES quality.tb_gov_meta_send (send_id) ON DELETE CASCADE
);
COMMENT ON TABLE  quality.tb_gov_meta_send_err IS '범정부 메타 생성 전 검증 결과 (104번 WP5-78)';
COMMENT ON COLUMN quality.tb_gov_meta_send_err.err_lvl IS 'SYS, DB, TBL, COL';
COMMENT ON COLUMN quality.tb_gov_meta_send_err.sev IS 'ERROR(생성 불가), WARN(규격 확인 전까지 경고)';

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_gov_meta_send_err; DROP TABLE IF EXISTS quality.tb_gov_meta_send;
-- DROP TABLE IF EXISTS quality.tb_gov_meta_code_map; DROP TABLE IF EXISTS quality.tb_gov_meta_db;
-- DROP TABLE IF EXISTS quality.tb_gov_meta_sys; DROP TABLE IF EXISTS quality.tb_gov_brm; DROP TABLE IF EXISTS quality.tb_gov_meta_conf;
-- ALTER TABLE quality.tb_diag_schedule_log DROP COLUMN IF EXISTS gov_meta_send_id;
-- ALTER TABLE quality.tb_data_model_attr DROP COLUMN IF EXISTS dbms_data_type, DROP COLUMN IF EXISTS open_tp, DROP COLUMN IF EXISTS open_limit_reason, DROP COLUMN IF EXISTS cnstr_desc, DROP COLUMN IF EXISTS priv_yn, DROP COLUMN IF EXISTS encr_yn;
-- ALTER TABLE quality.tb_data_model_obj DROP COLUMN IF EXISTS gov_send_yn, DROP COLUMN IF EXISTS tbl_tp, DROP COLUMN IF EXISTS occr_cycle, DROP COLUMN IF EXISTS open_tp, DROP COLUMN IF EXISTS open_limit_reason, DROP COLUMN IF EXISTS open_limit_basis, DROP COLUMN IF EXISTS open_data_lst_nm;
-- ALTER TABLE quality.tb_terms DROP COLUMN IF EXISTS priv_yn, DROP COLUMN IF EXISTS encr_yn;
-- ALTER TABLE quality.tb_domain DROP COLUMN IF EXISTS priv_yn, DROP COLUMN IF EXISTS encr_yn;
-- COMMIT;
