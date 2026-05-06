-- ==========================================================================
-- PC1 DataQ DB DDL 덤프 — PC2 비교용
-- 추출일시 : 2026-05-06 17:12:56
-- 추출소스 : localhost:25433 / postgres / pg_dump --schema-only -n quality -n ndata
-- 스키마   : quality (46 tables) + ndata (37 tables) = 총 83 tables
-- 비교대상 : PC2 동일 환경의 quality + ndata 스키마
-- 주의     : --no-owner --no-privileges. 데이터 제외 (schema-only).
-- ==========================================================================

--
-- PostgreSQL database dump
--

-- Dumped from database version 13.10
-- Dumped by pg_dump version 13.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ndata; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ndata;


--
-- Name: quality; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA quality;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: tb_job_log; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_job_log (
    job_id character varying(50) NOT NULL,
    time_val timestamp(3) with time zone NOT NULL,
    p_obj_id character varying(50) NOT NULL,
    p_obj_nm character varying(100),
    obj_id character varying(50) NOT NULL,
    obj_nm character varying(100),
    obj_tp smallint,
    job_ex_svc character varying(30),
    status smallint,
    prcs_cnt bigint,
    succ_cnt bigint,
    msg character varying(1000),
    job_ex_user_id character varying(50),
    job_ex_group_id character varying(50)
);


--
-- Name: tb_event_log; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_event_log (
    time_val timestamp(3) with time zone NOT NULL,
    hostname character varying(50),
    job_id character varying(50),
    obj_id character varying(50),
    obj_nm character varying(100),
    obj_tp smallint,
    job_ex_svc character varying(30),
    severity character varying(10),
    msg character varying(4000),
    job_ex_user_id character varying(50)
);


--
-- Name: tb_event_log; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_event_log (
    time_val timestamp(3) with time zone NOT NULL,
    hostname character varying(50),
    job_id character varying(50),
    obj_id character varying(50),
    obj_nm character varying(100),
    obj_tp smallint,
    job_ex_svc character varying(30),
    severity character varying(10),
    msg character varying(1000),
    job_ex_user_id character varying(50)
);


--
-- Name: dual; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.dual (
    c1 character(1)
);


--
-- Name: qrtz_blob_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_blob_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    blob_data bytea
);


--
-- Name: qrtz_calendars; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_calendars (
    sched_name character varying(120) NOT NULL,
    calendar_name character varying(200) NOT NULL,
    calendar bytea NOT NULL
);


--
-- Name: qrtz_cron_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_cron_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    cron_expression character varying(120) NOT NULL,
    time_zone_id character varying(80)
);


--
-- Name: qrtz_fired_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_fired_triggers (
    sched_name character varying(120) NOT NULL,
    entry_id character varying(95) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    instance_name character varying(200) NOT NULL,
    fired_time bigint NOT NULL,
    sched_time bigint NOT NULL,
    priority integer NOT NULL,
    state character varying(16) NOT NULL,
    job_name character varying(200),
    job_group character varying(200),
    is_nonconcurrent boolean,
    requests_recovery boolean
);


--
-- Name: qrtz_job_details; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_job_details (
    sched_name character varying(120) NOT NULL,
    job_name character varying(200) NOT NULL,
    job_group character varying(200) NOT NULL,
    description character varying(250),
    job_class_name character varying(250) NOT NULL,
    is_durable boolean NOT NULL,
    is_nonconcurrent boolean NOT NULL,
    is_update_data boolean NOT NULL,
    requests_recovery boolean NOT NULL,
    job_data bytea
);


--
-- Name: qrtz_locks; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_locks (
    sched_name character varying(120) NOT NULL,
    lock_name character varying(40) NOT NULL
);


--
-- Name: qrtz_paused_trigger_grps; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_paused_trigger_grps (
    sched_name character varying(120) NOT NULL,
    trigger_group character varying(200) NOT NULL
);


--
-- Name: qrtz_scheduler_state; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_scheduler_state (
    sched_name character varying(120) NOT NULL,
    instance_name character varying(200) NOT NULL,
    last_checkin_time bigint NOT NULL,
    checkin_interval bigint NOT NULL
);


--
-- Name: qrtz_simple_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_simple_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    repeat_count bigint NOT NULL,
    repeat_interval bigint NOT NULL,
    times_triggered bigint NOT NULL
);


--
-- Name: qrtz_simprop_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_simprop_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    str_prop_1 character varying(512),
    str_prop_2 character varying(512),
    str_prop_3 character varying(512),
    int_prop_1 integer,
    int_prop_2 integer,
    long_prop_1 bigint,
    long_prop_2 bigint,
    dec_prop_1 numeric(13,4),
    dec_prop_2 numeric(13,4),
    bool_prop_1 boolean,
    bool_prop_2 boolean
);


--
-- Name: qrtz_triggers; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.qrtz_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(200) NOT NULL,
    trigger_group character varying(200) NOT NULL,
    job_name character varying(200) NOT NULL,
    job_group character varying(200) NOT NULL,
    description character varying(250),
    next_fire_time bigint,
    prev_fire_time bigint,
    priority integer,
    trigger_state character varying(16) NOT NULL,
    trigger_type character varying(8) NOT NULL,
    start_time bigint NOT NULL,
    end_time bigint,
    calendar_name character varying(200),
    misfire_instr smallint,
    job_data bytea
);


--
-- Name: tb_data_source; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_data_source (
    ds_id character varying(50) NOT NULL,
    dsn character varying(50) NOT NULL,
    ds_tp smallint NOT NULL,
    dbms_tp character varying(30),
    driver_nm character varying(50),
    svr_addr character varying(50),
    port integer,
    user_id character varying(50),
    pwd character varying(200),
    charset character varying(20),
    private_key character varying(4000),
    db_name character varying(50),
    rm_dir character varying(200),
    conn_props character varying(500),
    secure_yn character varying(10),
    conn_test_yn character(1) DEFAULT 'N'::bpchar,
    conn_test_dt timestamp without time zone
);


--
-- Name: tb_data_source_bak; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_data_source_bak (
    ds_id character varying(50) NOT NULL,
    dsn character varying(50) NOT NULL,
    ds_tp smallint NOT NULL,
    dbms_tp character varying(30),
    driver_nm character varying(50),
    svr_addr character varying(50),
    port integer,
    user_id character varying(50),
    pwd character varying(200),
    charset character varying(20),
    private_key character varying(4000),
    db_name character varying(50),
    rm_dir character varying(200)
);


--
-- Name: tb_data_tmpl; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_data_tmpl (
    data_tmpl_id character varying(50) NOT NULL,
    p_data_tmpl_id character varying(50),
    data_tmpl_nm character varying(50) NOT NULL,
    data_tmpl_tp smallint NOT NULL,
    data_names character varying[],
    data_types character varying[],
    data_lens bigint[],
    data_precisions smallint[],
    data_descs character varying[],
    data_sep character varying(10),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_job_status; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_job_status (
    job_name character varying(200) NOT NULL,
    job_group character varying(200) NOT NULL,
    trigger_type character varying(8) NOT NULL,
    description character varying(250),
    last_fire_time timestamp without time zone,
    last_success_time timestamp without time zone,
    result_msg character varying(100)
);


--
-- Name: tb_object; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_object (
    obj_id character varying(50) NOT NULL,
    p_obj_id character varying(50),
    obj_tp smallint
);


--
-- Name: tb_prcd_cols_map; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_cols_map (
    prcd_id character varying(50) NOT NULL,
    trgt_vars character varying[],
    asgn_vars character varying[],
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_flow; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_flow (
    prcd_id character varying(50) NOT NULL,
    from_prcd_id character varying(50) NOT NULL,
    to_prcd_id character varying(50) NOT NULL,
    task_id character varying(50) NOT NULL,
    flow_tp smallint NOT NULL,
    obj_tp smallint NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_input; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_input (
    prcd_in_id character varying(50) NOT NULL,
    prcd_id character varying(50) NOT NULL,
    data_file_path character varying(200),
    data_tmpl_id character varying(100),
    data_start_num bigint,
    data_end_num bigint,
    data_file_alias character varying(10),
    charset character varying(20),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_join_method; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_join_method (
    prcd_join_id character varying(50) NOT NULL,
    prcd_id character varying(50) NOT NULL,
    join_method smallint,
    join_left_input_id character varying(50),
    join_left_key character varying,
    join_right_input_id character varying(50),
    join_right_key character varying,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_load; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_load (
    prcd_id character varying(50) NOT NULL,
    prcd_nm character varying(100) NOT NULL,
    task_id character varying(50) NOT NULL,
    obj_tp smallint NOT NULL,
    load_script character varying(8000),
    pre_script character varying(2000),
    pre_script_ex_yn boolean,
    after_script character varying(2000),
    after_script_ex_yn boolean,
    data_tmpl_id character varying(50),
    expl character varying(200),
    xpos smallint,
    ypos smallint,
    xposw smallint,
    yposw smallint,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_output; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_output (
    prcd_out_id character varying(50) NOT NULL,
    prcd_id character varying(50) NOT NULL,
    data_file_path character varying(200),
    data_tmpl_id character varying(100),
    data_flt_reg_exp character varying(500),
    charset character varying(20),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_trnf; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_trnf (
    prcd_id character varying(50) NOT NULL,
    prcd_nm character varying(100) NOT NULL,
    task_id character varying(50) NOT NULL,
    obj_tp smallint NOT NULL,
    expl character varying(200),
    xpos smallint,
    ypos smallint,
    xposw smallint,
    yposw smallint,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_unld; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_unld (
    prcd_id character varying(50) NOT NULL,
    prcd_nm character varying(100) NOT NULL,
    task_id character varying(50) NOT NULL,
    obj_tp smallint NOT NULL,
    unld_script character varying(8000),
    pre_script character varying(2000),
    pre_script_ex_yn boolean,
    after_script character varying(2000),
    after_script_ex_yn boolean,
    data_tmpl_id character varying(50),
    expl character varying(200),
    xpos smallint,
    ypos smallint,
    xposw smallint,
    yposw smallint,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_prcd_xsql; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_prcd_xsql (
    prcd_id character varying(50) NOT NULL,
    prcd_nm character varying(100) NOT NULL,
    task_id character varying(50) NOT NULL,
    obj_tp smallint NOT NULL,
    ds_id character varying(50),
    query_script character varying(2000),
    expl character varying(200),
    xpos smallint,
    ypos smallint,
    xposw smallint,
    yposw smallint,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_proj; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_proj (
    proj_id character varying(50) NOT NULL,
    p_proj_id character varying(50),
    proj_tp smallint NOT NULL,
    proj_nm character varying(100) NOT NULL,
    work_path character varying(200),
    ver smallint,
    expl character varying(200),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_property; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_property (
    obj_id character varying(50) NOT NULL,
    p_obj_id character varying(50),
    attr_ki character varying(50) NOT NULL,
    attr_tp character varying(50) NOT NULL,
    obj_tp smallint NOT NULL,
    val1 character varying(2000),
    val1_type character varying(10),
    val2 character varying(2000),
    val2_type character varying(10),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_setting; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_setting (
    ki character varying(50) NOT NULL,
    tp character varying(10) NOT NULL,
    val character varying(100),
    val_type character varying(10),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_task; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_task (
    task_id character varying(50) NOT NULL,
    task_nm character varying(100) NOT NULL,
    proj_id character varying(100) NOT NULL,
    work_path character varying(200),
    ver smallint,
    expl character varying(200),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_task_param; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_task_param (
    task_id character varying(50) NOT NULL,
    prcd_nm character varying(100) NOT NULL,
    param_nm character varying(100) NOT NULL,
    param_type character varying(10),
    param_len bigint,
    param_precision smallint,
    param_default_val character varying(200),
    param_script character varying(1000),
    param_ord smallint NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_user; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_user (
    user_id character varying(50) NOT NULL,
    pwd character varying(200),
    nm character varying(50),
    email character varying(50),
    adm_yn boolean,
    cret_dt character varying(14),
    updt_dt character varying(14),
    block_time character varying(14),
    login_fail_count smallint,
    del_yn boolean,
    phone character varying(20)
);


--
-- Name: tb_user_asgn_role; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_user_asgn_role (
    user_id character varying(50) NOT NULL,
    role_nm character varying(50) NOT NULL,
    cret_dt character varying(14)
);


--
-- Name: tb_user_role; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_user_role (
    role_nm character varying(50) NOT NULL,
    proj_access_rights smallint,
    tmpl_access_rights smallint,
    cret_dt character varying(14),
    updt_dt character varying(14),
    cret_user_id character varying(50),
    updt_user_id character varying(50)
);


--
-- Name: tb_user_role_authority; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_user_role_authority (
    role_nm character varying(50) NOT NULL,
    a_obj_id character varying(50) NOT NULL,
    a_obj_tp character(1) NOT NULL,
    a_obj_depth smallint,
    all_subs_yn boolean,
    cret_dt character varying(14),
    cret_user_id character varying(50)
);


--
-- Name: dual; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.dual (
    c1 character(1)
);


--
-- Name: imsi_comment; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.imsi_comment (
    attr_name character varying(100) DEFAULT NULL::character varying,
    data_type character varying(100) DEFAULT NULL::character varying,
    comment1 character varying(100) DEFAULT NULL::character varying,
    comment2 character varying(1000) DEFAULT NULL::character varying,
    attr_name_new character varying(100)
);


--
-- Name: imsi_comment_comdb; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.imsi_comment_comdb (
    attr_name character varying(100) DEFAULT NULL::character varying,
    data_type character varying(100) DEFAULT NULL::character varying,
    data_len character varying(100) DEFAULT NULL::character varying,
    comment1 character varying(200) DEFAULT NULL::character varying,
    comment2 character varying(200) DEFAULT NULL::character varying,
    attr_name_new character varying(100)
);


--
-- Name: tb_aprv_stats; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_aprv_stats (
    req_id character varying(22) NOT NULL,
    req_tp character varying(10) NOT NULL,
    req_item_id character varying(22) NOT NULL,
    aprv_stat smallint,
    req_user_id character varying(50),
    req_cret_dt character varying(14),
    req_updt_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_stat_updt_dt character varying(14),
    aprv_stat_updt_rsn character varying(50),
    req_item_nm character varying(200)
);


--
-- Name: tb_board; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_board (
    board_id character varying(40) NOT NULL,
    board_type character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    content text,
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_dt timestamp without time zone,
    view_cnt integer DEFAULT 0,
    pin_yn character(1) DEFAULT 'N'::bpchar
);


--
-- Name: tb_board_comment; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_board_comment (
    comment_id character varying(40) NOT NULL,
    board_id character varying(40) NOT NULL,
    content text,
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_dt timestamp without time zone
);


--
-- Name: tb_board_file; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_board_file (
    file_id character varying(40) NOT NULL,
    board_id character varying(40) NOT NULL,
    file_nm character varying(300) NOT NULL,
    file_path character varying(500) NOT NULL,
    file_size bigint DEFAULT 0,
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: tb_change_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_change_history (
    change_id character varying(40) NOT NULL,
    change_type character varying(20) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(40),
    target_nm character varying(200),
    change_cnt integer DEFAULT 1,
    summary character varying(500),
    prev_value text,
    curr_value text,
    change_user_id character varying(40),
    change_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    change_source character varying(20)
);


--
-- Name: TABLE tb_change_history; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_change_history IS '변경 이력 마스터';


--
-- Name: COLUMN tb_change_history.change_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_change_history.change_type IS 'INSERT, UPDATE, DELETE, BULK_INSERT';


--
-- Name: COLUMN tb_change_history.target_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_change_history.target_type IS 'WORD, TERM, DOMAIN, CODE, CODE_DATA';


--
-- Name: COLUMN tb_change_history.change_source; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_change_history.change_source IS 'ADMIN_DIRECT / USER_REQUEST / ADMIN_APPROVE / ADMIN_REJECT / BULK_UPLOAD / AUTO_RECOMMEND';


--
-- Name: tb_change_history_detail; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_change_history_detail (
    change_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    target_id character varying(40),
    target_nm character varying(200),
    detail_type character varying(20),
    prev_value text,
    curr_value text,
    remark character varying(500)
);


--
-- Name: TABLE tb_change_history_detail; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_change_history_detail IS '변경 이력 상세 (일괄 등록 시)';


--
-- Name: tb_code_data; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_code_data (
    code_id character varying(22) NOT NULL,
    code_grp character varying(50) NOT NULL,
    code_nm character varying(100) NOT NULL,
    code_eng_nm character varying(100) NOT NULL,
    code_val character varying(50) NOT NULL,
    code_val_desc character varying(100),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_data_model; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model (
    dm_id character varying(22) NOT NULL,
    dm_nm character varying(100) NOT NULL,
    dm_sys_cd character varying(22),
    dm_ds_id character varying(50),
    ver character varying(10) NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    use_yn character(1) DEFAULT 'Y'::bpchar,
    struct_diag_yn character(1) DEFAULT 'N'::bpchar,
    struct_diag_dt timestamp without time zone,
    model_type character varying(10) DEFAULT 'PHYSICAL'::character varying
);


--
-- Name: COLUMN tb_data_model.struct_diag_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model.struct_diag_yn IS '구조진단 일치여부 (Y=일치, N=불일치/미진단)';


--
-- Name: COLUMN tb_data_model.struct_diag_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model.struct_diag_dt IS '구조진단 최종 실행일시';


--
-- Name: COLUMN tb_data_model.model_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model.model_type IS 'PHYSICAL(물리만), LOGICAL(논리만), BOTH(논리+물리)';


--
-- Name: tb_data_model_attr; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_attr (
    dm_id character varying(22) NOT NULL,
    obj_nm character varying(255) NOT NULL,
    attr_nm character varying(255) NOT NULL,
    attr_nm_kr character varying(255),
    data_type character varying(50),
    data_len bigint,
    data_decimal_len smallint,
    terms_stnd_yn character(1),
    domain_stnd_yn character(1),
    word_lst character varying[],
    word_stnd_lst character varying[],
    nullable_yn character(1),
    pk_yn character(1),
    fk_yn character(1),
    default_val character varying(255),
    attr_ord smallint,
    obj_owner character varying(100) DEFAULT ''::character varying,
    attr_comment character varying(500),
    use_yn character varying(1) DEFAULT 'Y'::character varying,
    deleted_dt character varying(14),
    stnd_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    stnd_diag_target_reason character varying(200),
    struct_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    struct_diag_target_reason character varying(200),
    qual_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    qual_diag_target_reason character varying(200),
    diag_target_updt_user_id character varying(50),
    diag_target_updt_dt character varying(14)
);


--
-- Name: COLUMN tb_data_model_attr.stnd_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.stnd_diag_target_yn IS '표준 진단 대상 (Y=대상/N=제외, DEFAULT Y) — 79번';


--
-- Name: COLUMN tb_data_model_attr.stnd_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.stnd_diag_target_reason IS '표준 OFF 사유';


--
-- Name: COLUMN tb_data_model_attr.struct_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.struct_diag_target_yn IS '구조 변경 진단 대상';


--
-- Name: COLUMN tb_data_model_attr.struct_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.struct_diag_target_reason IS '구조 OFF 사유';


--
-- Name: COLUMN tb_data_model_attr.qual_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.qual_diag_target_yn IS '품질 진단 대상 (매퍼 통합 보류)';


--
-- Name: COLUMN tb_data_model_attr.qual_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.qual_diag_target_reason IS '품질 OFF 사유';


--
-- Name: COLUMN tb_data_model_attr.diag_target_updt_user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.diag_target_updt_user_id IS '진단 대상 마지막 변경자';


--
-- Name: COLUMN tb_data_model_attr.diag_target_updt_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.diag_target_updt_dt IS '진단 대상 마지막 변경일시';


--
-- Name: tb_data_model_clct; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_clct (
    dm_clct_id character varying(22) NOT NULL,
    dm_id character varying(22) NOT NULL,
    clct_start_dt character varying(14),
    clct_end_dt character varying(14),
    clct_cmptn_yn character(1),
    cret_user_id character varying(50),
    clct_type character varying(20) DEFAULT 'DBMS'::character varying,
    added_cnt integer DEFAULT 0,
    deleted_cnt integer DEFAULT 0,
    modified_cnt integer DEFAULT 0
);


--
-- Name: TABLE tb_data_model_clct; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_data_model_clct IS '데이터수집';


--
-- Name: tb_data_model_constraint; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_constraint (
    dm_id character varying(40) NOT NULL,
    obj_owner character varying(100) NOT NULL,
    table_nm character varying(200) NOT NULL,
    constraint_nm character varying(200) NOT NULL,
    constraint_type character varying(10),
    column_nm character varying(200),
    column_pos integer NOT NULL,
    ref_owner character varying(100),
    ref_table_nm character varying(200),
    ref_column_nm character varying(200),
    delete_rule character varying(20),
    status character varying(10),
    search_condition character varying(2000),
    use_yn character(1) DEFAULT 'Y'::bpchar,
    deleted_dt character varying(14)
);


--
-- Name: tb_data_model_index; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_index (
    dm_id character varying(40) NOT NULL,
    obj_owner character varying(100) NOT NULL,
    table_nm character varying(200) NOT NULL,
    index_nm character varying(200) NOT NULL,
    index_type character varying(50),
    uniqueness character varying(10),
    column_nm character varying(200),
    column_pos integer NOT NULL,
    sort_order character varying(10),
    tablespace_nm character varying(100),
    use_yn character(1) DEFAULT 'Y'::bpchar,
    deleted_dt character varying(14)
);


--
-- Name: tb_data_model_map; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_map (
    dm_id character varying(22) NOT NULL,
    obj_nm character varying(255) NOT NULL,
    attr_nm character varying(255) NOT NULL,
    attr_nm_kr character varying(255),
    attr_desc character varying(1000),
    data_type character varying(50),
    data_len bigint,
    data_decimal_len smallint,
    nullable_yn character(1),
    aft_obj_nm character varying(255) NOT NULL,
    aft_attr_nm character varying(255) NOT NULL,
    aft_attr_nm_kr character varying(255),
    aft_data_type character varying(50),
    aft_data_len bigint,
    aft_data_decimal_len smallint,
    aft_nullable_yn character(1),
    trnf_script character varying(2000)
);


--
-- Name: tb_data_model_obj; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_obj (
    dm_id character varying(22) NOT NULL,
    obj_nm character varying(255) NOT NULL,
    obj_nm_kr character varying(255),
    obj_owner character varying(50),
    obj_desc character varying(500),
    obj_attr_cnt numeric(6,0),
    obj_comment character varying(500),
    use_yn character varying(1) DEFAULT 'Y'::character varying,
    deleted_dt character varying(14),
    stnd_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    stnd_diag_target_reason character varying(200),
    struct_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    struct_diag_target_reason character varying(200),
    qual_diag_target_yn character varying(1) DEFAULT 'Y'::character varying,
    qual_diag_target_reason character varying(200),
    diag_target_updt_user_id character varying(50),
    diag_target_updt_dt character varying(14)
);


--
-- Name: COLUMN tb_data_model_obj.stnd_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.stnd_diag_target_yn IS '표준 진단 대상 (Y=대상/N=제외, DEFAULT Y) — 79번';


--
-- Name: COLUMN tb_data_model_obj.stnd_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.stnd_diag_target_reason IS '표준 OFF 사유 (선택)';


--
-- Name: COLUMN tb_data_model_obj.struct_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.struct_diag_target_yn IS '구조 변경 진단 대상';


--
-- Name: COLUMN tb_data_model_obj.struct_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.struct_diag_target_reason IS '구조 OFF 사유';


--
-- Name: COLUMN tb_data_model_obj.qual_diag_target_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.qual_diag_target_yn IS '품질 진단 대상 (매퍼 통합은 67/70번 정식 구현 후 — 핸드오버 필수)';


--
-- Name: COLUMN tb_data_model_obj.qual_diag_target_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.qual_diag_target_reason IS '품질 OFF 사유';


--
-- Name: COLUMN tb_data_model_obj.diag_target_updt_user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.diag_target_updt_user_id IS '진단 대상 마지막 변경자';


--
-- Name: COLUMN tb_data_model_obj.diag_target_updt_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.diag_target_updt_dt IS '진단 대상 마지막 변경일시 (YYYYMMDDHH24MISS)';


--
-- Name: tb_data_model_schema; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_schema (
    dm_id character varying(36) NOT NULL,
    schema_nm character varying(100) NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50)
);


--
-- Name: tb_diag_job; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_diag_job (
    diag_job_id character varying(50) NOT NULL,
    dm_clct_id character varying(50) NOT NULL,
    dm_id character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'READY'::character varying NOT NULL,
    total_cnt integer DEFAULT 0,
    process_cnt integer DEFAULT 0,
    result_cnt integer DEFAULT 0,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    start_dt character varying(14),
    end_dt character varying(14)
);


--
-- Name: tb_diag_result; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_diag_result (
    result_id bigint NOT NULL,
    diag_job_id character varying(50) NOT NULL,
    obj_nm character varying(200),
    attr_nm character varying(200),
    attr_nm_kr character varying(200),
    diag_type character varying(50) NOT NULL,
    diag_detail text,
    std_value character varying(500),
    actual_value character varying(500)
);


--
-- Name: tb_diag_result_result_id_seq; Type: SEQUENCE; Schema: quality; Owner: -
--

CREATE SEQUENCE quality.tb_diag_result_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tb_diag_result_result_id_seq; Type: SEQUENCE OWNED BY; Schema: quality; Owner: -
--

ALTER SEQUENCE quality.tb_diag_result_result_id_seq OWNED BY quality.tb_diag_result.result_id;


--
-- Name: tb_diag_schedule; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_diag_schedule (
    schedule_id character varying(40) NOT NULL,
    schedule_nm character varying(200) NOT NULL,
    diag_type character varying(20) NOT NULL,
    data_model_id character varying(40) NOT NULL,
    schedule_type character varying(20) DEFAULT 'SIMPLE'::character varying,
    repeat_cycle character varying(20),
    repeat_time character varying(5),
    repeat_day_of_week integer,
    repeat_day_of_month integer,
    cron_expr character varying(100),
    use_yn character(1) DEFAULT 'Y'::bpchar,
    last_exec_dt timestamp without time zone,
    last_exec_status character varying(20),
    last_exec_log_id character varying(40),
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_user_id character varying(40),
    updt_dt timestamp without time zone
);


--
-- Name: TABLE tb_diag_schedule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_diag_schedule IS '진단 스케줄 정의 (65번 문서)';


--
-- Name: COLUMN tb_diag_schedule.diag_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.diag_type IS 'STANDARD | STRUCT | BOTH';


--
-- Name: COLUMN tb_diag_schedule.schedule_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.schedule_type IS 'SIMPLE | CRON';


--
-- Name: COLUMN tb_diag_schedule.use_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.use_yn IS '활성/비활성 토글';


--
-- Name: COLUMN tb_diag_schedule.last_exec_log_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.last_exec_log_id IS '최근 실행 LOG 참조 (빠른 조회용)';


--
-- Name: tb_diag_schedule_log; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_diag_schedule_log (
    log_id character varying(40) NOT NULL,
    schedule_id character varying(40) NOT NULL,
    exec_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    exec_end_dt timestamp without time zone,
    exec_status character varying(20),
    trigger_type character varying(20),
    diag_type character varying(20),
    diag_job_id character varying(40),
    struct_diag_id character varying(40),
    schedule_nm_snapshot character varying(200),
    error_msg character varying(2000),
    exec_duration_sec integer
);


--
-- Name: TABLE tb_diag_schedule_log; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_diag_schedule_log IS '진단 스케줄 실행 이력 (65번 문서)';


--
-- Name: COLUMN tb_diag_schedule_log.trigger_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule_log.trigger_type IS 'AUTO(스케줄러) | MANUAL(runNow)';


--
-- Name: COLUMN tb_diag_schedule_log.schedule_nm_snapshot; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule_log.schedule_nm_snapshot IS '실행 당시 스케줄명. 원 스케줄 삭제 후에도 이력에서 조회 가능';


--
-- Name: COLUMN tb_diag_schedule_log.error_msg; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule_log.error_msg IS '실패 원인 요약. prefix 예: [CONFIG], [DB], [TIMEOUT], [DATA_NOT_FOUND], [UNKNOWN]';


--
-- Name: tb_domain; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_domain (
    domain_id character varying(22) NOT NULL,
    domain_nm character varying(100) NOT NULL,
    domain_grp_nm character varying(100) NOT NULL,
    domain_clsf_nm character varying(100) NOT NULL,
    domain_desc character varying(500) NOT NULL,
    data_type character varying(50) NOT NULL,
    data_len smallint,
    data_decimal_len smallint,
    data_unit character varying(10),
    stor_fmt character varying(50),
    expr_fmt_lst character varying[],
    comm_stnd_yn character(1),
    allow_val_lst character varying[],
    magntd_ord character varying(10),
    aprv_yn character(1) DEFAULT 'N'::bpchar,
    aprv_user_id character varying(50),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    aprv_stat_updt_dt character varying(14),
    req_sys_cd character varying(50),
    use_yn character(1) DEFAULT 'Y'::bpchar
);


--
-- Name: tb_domain_clsf; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_domain_clsf (
    domain_clsf_id character varying(22) NOT NULL,
    domain_clsf_nm character varying(100) NOT NULL,
    domain_grp_nm character varying(100) NOT NULL,
    comm_stnd_yn character(1) NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_domain_grp; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_domain_grp (
    domain_grp_id character varying(22) NOT NULL,
    domain_grp_nm character varying(100) NOT NULL,
    comm_stnd_yn character(1) NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_domain_rule; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_domain_rule (
    domain_rule_id character varying(40) NOT NULL,
    domain_id character varying(40) NOT NULL,
    rule_nm character varying(200) NOT NULL,
    rule_type character varying(20) NOT NULL,
    rule_params text,
    sort_ord integer DEFAULT 1,
    use_yn character varying(1) DEFAULT 'Y'::character varying,
    descr character varying(500),
    cret_user_id character varying(50),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_user_id character varying(50),
    updt_dt timestamp without time zone
);


--
-- Name: TABLE tb_domain_rule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_domain_rule IS '도메인별 룰 정의 (1:N) — 70번';


--
-- Name: tb_qual_col_rule; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_col_rule (
    dm_id character varying(40) NOT NULL,
    obj_nm character varying(100) NOT NULL,
    attr_nm character varying(100) NOT NULL,
    domain_rule_id character varying(40),
    custom_rule_id character varying(40),
    exclude_yn character varying(1) DEFAULT 'N'::character varying,
    updt_user_id character varying(50),
    updt_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE tb_qual_col_rule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_col_rule IS '컬럼별 룰 매핑 (도메인룰 우선 / 커스텀 / 제외) — 70번';


--
-- Name: tb_qual_diag_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_diag_history (
    diag_id character varying(40) NOT NULL,
    dm_id character varying(40) NOT NULL,
    diag_type character varying(10) NOT NULL,
    diag_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    end_dt timestamp without time zone,
    status character varying(20) DEFAULT 'READY'::character varying NOT NULL,
    target_obj_list text,
    sample_rate integer DEFAULT 100,
    incremental_yn character(1) DEFAULT 'N'::bpchar,
    last_diag_dt timestamp without time zone,
    total_rules integer DEFAULT 0,
    total_cols integer DEFAULT 0,
    total_violations integer DEFAULT 0,
    exec_user_id character varying(40),
    error_msg text
);


--
-- Name: TABLE tb_qual_diag_history; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_diag_history IS '데이터 품질 진단 실행 이력 (값/룰 공용, 67번 §5)';


--
-- Name: tb_qual_profile_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_profile_history (
    diag_id character varying(40) NOT NULL,
    dm_id character varying(40) NOT NULL,
    obj_nm character varying(100) NOT NULL,
    attr_nm character varying(100) NOT NULL,
    total_cnt bigint,
    null_cnt bigint,
    distinct_cnt bigint,
    empty_cnt bigint,
    min_val character varying(200),
    max_val character varying(200),
    avg_val numeric(20,4),
    std_val numeric(20,4),
    min_len integer,
    max_len integer,
    diag_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE tb_qual_profile_history; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_profile_history IS '값 프로파일 시계열 누적 (MIN/MAX/AVG/STD/LEN/COUNT) — 70번';


--
-- Name: tb_qual_profile_result; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_profile_result (
    dm_id character varying(40) NOT NULL,
    obj_nm character varying(100) NOT NULL,
    attr_nm character varying(100) NOT NULL,
    diag_id character varying(40) NOT NULL,
    total_cnt bigint,
    null_cnt bigint,
    distinct_cnt bigint,
    empty_cnt bigint,
    min_val character varying(200),
    max_val character varying(200),
    avg_val numeric(20,4),
    std_val numeric(20,4),
    min_len integer,
    max_len integer,
    top_values text,
    updated_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE tb_qual_profile_result; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_profile_result IS '값 프로파일링 결과 (UPSERT, 직전 1회분만)';


--
-- Name: tb_qual_rule; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_rule (
    rule_id character varying(40) NOT NULL,
    dm_id character varying(40) NOT NULL,
    obj_nm character varying(100),
    attr_nm character varying(100),
    domain_id character varying(40),
    rule_nm character varying(200) NOT NULL,
    rule_type character varying(20) NOT NULL,
    rule_params text,
    severity character varying(10) DEFAULT 'WARN'::character varying NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    incremental_col character varying(100),
    est_cost character varying(10),
    descr text,
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_user_id character varying(40),
    updt_dt timestamp without time zone
);


--
-- Name: TABLE tb_qual_rule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_rule IS '데이터 품질 진단 — 룰 정의 (67번 §4.2)';


--
-- Name: tb_qual_rule_catalog; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_rule_catalog (
    catalog_id character varying(40) NOT NULL,
    catalog_nm character varying(200) NOT NULL,
    rule_type character varying(20),
    rule_params text,
    category character varying(50),
    descr text,
    use_yn character(1) DEFAULT 'Y'::bpchar
);


--
-- Name: TABLE tb_qual_rule_catalog; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_rule_catalog IS '룰 템플릿 (이메일/주민번호 등 표준 정규식)';


--
-- Name: tb_qual_rule_result; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_rule_result (
    diag_id character varying(40) NOT NULL,
    rule_id character varying(40) NOT NULL,
    obj_nm character varying(100) DEFAULT ''::character varying NOT NULL,
    attr_nm character varying(100) DEFAULT ''::character varying NOT NULL,
    total_cnt bigint,
    violation_cnt bigint,
    violation_rate numeric(7,4),
    sample_cnt integer,
    error_msg text
);


--
-- Name: TABLE tb_qual_rule_result; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_rule_result IS '업무 규칙 진단 결과 (DIAG_ID + RULE_ID + 도메인 룰의 경우 OBJ/ATTR)';


--
-- Name: tb_qual_violation_sample; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_violation_sample (
    diag_id character varying(40) NOT NULL,
    rule_id character varying(40) NOT NULL,
    obj_nm character varying(100) DEFAULT ''::character varying NOT NULL,
    attr_nm character varying(100) DEFAULT ''::character varying NOT NULL,
    seq integer NOT NULL,
    pk_values text,
    violating_val character varying(500)
);


--
-- Name: TABLE tb_qual_violation_sample; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_violation_sample IS '위반 샘플 행 (PK + 위반값, 룰당 기본 100건)';


--
-- Name: tb_struct_diag_constraint_detail; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_struct_diag_constraint_detail (
    diag_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    owner character varying(100),
    table_nm character varying(200),
    constraint_nm character varying(200),
    change_type character varying(20),
    prev_constraint_type character varying(10),
    curr_constraint_type character varying(10),
    prev_columns character varying(1000),
    curr_columns character varying(1000),
    prev_ref_table character varying(200),
    curr_ref_table character varying(200),
    prev_ref_columns character varying(1000),
    curr_ref_columns character varying(1000),
    prev_delete_rule character varying(20),
    curr_delete_rule character varying(20),
    prev_status character varying(10),
    curr_status character varying(10)
);


--
-- Name: tb_struct_diag_detail; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_struct_diag_detail (
    diag_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    table_nm character varying(200),
    column_nm character varying(200),
    change_type character varying(20),
    prev_data_type character varying(50),
    curr_data_type character varying(50),
    prev_data_len integer,
    curr_data_len integer,
    prev_nullable character(1),
    curr_nullable character(1),
    owner character varying(100)
);


--
-- Name: TABLE tb_struct_diag_detail; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_struct_diag_detail IS '구조 진단 상세 (변경 컬럼)';


--
-- Name: COLUMN tb_struct_diag_detail.diag_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.diag_id IS '진단 ID';


--
-- Name: COLUMN tb_struct_diag_detail.seq; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.seq IS '순번';


--
-- Name: COLUMN tb_struct_diag_detail.table_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.table_nm IS '테이블명';


--
-- Name: COLUMN tb_struct_diag_detail.column_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.column_nm IS '컬럼명';


--
-- Name: COLUMN tb_struct_diag_detail.change_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.change_type IS '변경유형 (ADDED/MODIFIED/DELETED)';


--
-- Name: COLUMN tb_struct_diag_detail.prev_data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_data_type IS '이전 데이터타입';


--
-- Name: COLUMN tb_struct_diag_detail.curr_data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_data_type IS '현재 데이터타입';


--
-- Name: COLUMN tb_struct_diag_detail.prev_data_len; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_data_len IS '이전 데이터 길이';


--
-- Name: COLUMN tb_struct_diag_detail.curr_data_len; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_data_len IS '현재 데이터 길이';


--
-- Name: COLUMN tb_struct_diag_detail.prev_nullable; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_nullable IS '이전 Nullable 여부';


--
-- Name: COLUMN tb_struct_diag_detail.curr_nullable; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_nullable IS '현재 Nullable 여부';


--
-- Name: tb_struct_diag_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_struct_diag_history (
    diag_id character varying(40) NOT NULL,
    data_model_id character varying(40),
    ds_id character varying(40),
    schema_nm character varying(100),
    status character varying(20) DEFAULT 'READY'::character varying,
    diag_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    prev_collect_dt timestamp without time zone,
    total_tables integer DEFAULT 0,
    total_columns integer DEFAULT 0,
    added_tables integer DEFAULT 0,
    added_columns integer DEFAULT 0,
    modified_columns integer DEFAULT 0,
    deleted_tables integer DEFAULT 0,
    deleted_columns integer DEFAULT 0,
    cret_user_id character varying(40),
    total_indexes integer DEFAULT 0,
    added_indexes integer DEFAULT 0,
    modified_indexes integer DEFAULT 0,
    deleted_indexes integer DEFAULT 0,
    total_constraints integer DEFAULT 0,
    added_constraints integer DEFAULT 0,
    modified_constraints integer DEFAULT 0,
    deleted_constraints integer DEFAULT 0
);


--
-- Name: TABLE tb_struct_diag_history; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_struct_diag_history IS '구조 진단 이력';


--
-- Name: COLUMN tb_struct_diag_history.diag_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.diag_id IS '진단 ID';


--
-- Name: COLUMN tb_struct_diag_history.data_model_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.data_model_id IS '데이터모델 ID';


--
-- Name: COLUMN tb_struct_diag_history.ds_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.ds_id IS '데이터소스 ID';


--
-- Name: COLUMN tb_struct_diag_history.schema_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.schema_nm IS '스키마명';


--
-- Name: COLUMN tb_struct_diag_history.status; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.status IS '진단 상태 (READY/RUNNING/DONE/ERROR)';


--
-- Name: COLUMN tb_struct_diag_history.diag_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.diag_dt IS '진단 일시';


--
-- Name: COLUMN tb_struct_diag_history.prev_collect_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.prev_collect_dt IS '이전 수집 일시';


--
-- Name: COLUMN tb_struct_diag_history.total_tables; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.total_tables IS '전체 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.total_columns; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.total_columns IS '전체 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.added_tables; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.added_tables IS '추가된 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.added_columns; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.added_columns IS '추가된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.modified_columns; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.modified_columns IS '변경된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.deleted_tables; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.deleted_tables IS '삭제된 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.deleted_columns; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.deleted_columns IS '삭제된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.cret_user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_struct_diag_history.cret_user_id IS '실행자 ID';


--
-- Name: tb_struct_diag_index_detail; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_struct_diag_index_detail (
    diag_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    owner character varying(100),
    table_nm character varying(200),
    index_nm character varying(200),
    change_type character varying(20),
    prev_index_type character varying(50),
    curr_index_type character varying(50),
    prev_uniqueness character varying(10),
    curr_uniqueness character varying(10),
    prev_columns character varying(1000),
    curr_columns character varying(1000)
);


--
-- Name: tb_sys_info; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_sys_info (
    sys_cd character varying(22) NOT NULL,
    p_sys_cd character varying(22),
    sys_tp smallint NOT NULL,
    sys_nm character varying(100) NOT NULL,
    sys_ds_lst character varying[],
    sys_desc character varying(200),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: tb_terms; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_terms (
    terms_id character varying(22) NOT NULL,
    terms_nm character varying(255) NOT NULL,
    terms_eng_abrv_nm character varying(255) NOT NULL,
    terms_desc character varying(1000) NOT NULL,
    domain_nm character varying(100) NOT NULL,
    code_grp character varying(50),
    chrg_org character varying(50),
    alloph_synm_lst character varying[],
    comm_stnd_yn character(1),
    magntd_ord character varying(10),
    aprv_yn character(1) DEFAULT 'N'::bpchar,
    aprv_user_id character varying(50),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    aprv_stat_updt_dt character varying(14),
    req_sys_cd character varying(50),
    use_yn character(1) DEFAULT 'Y'::bpchar
);


--
-- Name: tb_terms_words; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_terms_words (
    terms_id character varying(22) NOT NULL,
    word_id character varying(22) NOT NULL,
    word_nm character varying(100) NOT NULL,
    word_ord smallint NOT NULL
);


--
-- Name: tb_user; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_user (
    user_id character varying(50) NOT NULL,
    pwd character varying(200),
    nm character varying(50),
    email character varying(50),
    adm_yn boolean,
    cret_dt character varying(14),
    updt_dt character varying(14),
    block_time character varying(14),
    login_fail_count smallint,
    del_yn boolean,
    phone character varying(20)
);


--
-- Name: tb_word; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_word (
    word_id character varying(22) NOT NULL,
    word_nm character varying(100) NOT NULL,
    word_eng_abrv_nm character varying(100) NOT NULL,
    word_eng_nm character varying(100) NOT NULL,
    word_desc character varying(1000) NOT NULL,
    word_clsf_yn character(1) NOT NULL,
    domain_clsf_nm character varying(50),
    alloph_synm_lst character varying[],
    forbdn_word_lst character varying[],
    comm_stnd_yn character(1),
    magntd_ord character varying(10),
    aprv_yn character(1) DEFAULT 'N'::bpchar,
    aprv_user_id character varying(50),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    aprv_stat_updt_dt character varying(14),
    req_sys_cd character varying(50),
    use_yn character(1) DEFAULT 'Y'::bpchar
);


--
-- Name: tb_word_dict; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_word_dict (
    word_kor character varying(100) NOT NULL,
    word_eng character varying(200),
    word_abrv character varying(50),
    domain_clsf_nm character varying(100)
);


--
-- Name: TABLE tb_word_dict; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_word_dict IS '미등록 단어 영문 추천 사전';


--
-- Name: COLUMN tb_word_dict.word_kor; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_word_dict.word_kor IS '한글 단어명';


--
-- Name: COLUMN tb_word_dict.word_eng; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_word_dict.word_eng IS '영문 풀네임';


--
-- Name: COLUMN tb_word_dict.word_abrv; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_word_dict.word_abrv IS '영문 약어';


--
-- Name: COLUMN tb_word_dict.domain_clsf_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_word_dict.domain_clsf_nm IS '도메인 분류명';


--
-- Name: tb_word_dict_bak; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_word_dict_bak (
    word_kor character varying(100),
    word_eng character varying(200),
    word_abrv character varying(50),
    domain_clsf_nm character varying(100)
);


--
-- Name: tb_diag_result result_id; Type: DEFAULT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_result ALTER COLUMN result_id SET DEFAULT nextval('quality.tb_diag_result_result_id_seq'::regclass);


--
-- Name: tb_prcd_unld newtable_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_unld
    ADD CONSTRAINT newtable_pk PRIMARY KEY (prcd_id);


--
-- Name: qrtz_blob_triggers qrtz_blob_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_blob_triggers
    ADD CONSTRAINT qrtz_blob_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_calendars qrtz_calendars_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_calendars
    ADD CONSTRAINT qrtz_calendars_pkey PRIMARY KEY (sched_name, calendar_name);


--
-- Name: qrtz_cron_triggers qrtz_cron_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_cron_triggers
    ADD CONSTRAINT qrtz_cron_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_fired_triggers qrtz_fired_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_fired_triggers
    ADD CONSTRAINT qrtz_fired_triggers_pkey PRIMARY KEY (sched_name, entry_id);


--
-- Name: qrtz_job_details qrtz_job_details_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_job_details
    ADD CONSTRAINT qrtz_job_details_pkey PRIMARY KEY (sched_name, job_name, job_group);


--
-- Name: qrtz_locks qrtz_locks_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_locks
    ADD CONSTRAINT qrtz_locks_pkey PRIMARY KEY (sched_name, lock_name);


--
-- Name: qrtz_paused_trigger_grps qrtz_paused_trigger_grps_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_paused_trigger_grps
    ADD CONSTRAINT qrtz_paused_trigger_grps_pkey PRIMARY KEY (sched_name, trigger_group);


--
-- Name: qrtz_scheduler_state qrtz_scheduler_state_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_scheduler_state
    ADD CONSTRAINT qrtz_scheduler_state_pkey PRIMARY KEY (sched_name, instance_name);


--
-- Name: qrtz_simple_triggers qrtz_simple_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_simple_triggers
    ADD CONSTRAINT qrtz_simple_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simprop_triggers qrtz_simprop_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_simprop_triggers
    ADD CONSTRAINT qrtz_simprop_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_triggers qrtz_triggers_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_triggers
    ADD CONSTRAINT qrtz_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: tb_data_source_bak tb_data_source_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_data_source_bak
    ADD CONSTRAINT tb_data_source_pk PRIMARY KEY (ds_id);


--
-- Name: tb_data_tmpl tb_data_tmpl_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_data_tmpl
    ADD CONSTRAINT tb_data_tmpl_pk PRIMARY KEY (data_tmpl_id);


--
-- Name: tb_event_log tb_event_log_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_event_log
    ADD CONSTRAINT tb_event_log_pk PRIMARY KEY (time_val);


--
-- Name: tb_job_log tb_job_log_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_job_log
    ADD CONSTRAINT tb_job_log_pk PRIMARY KEY (job_id, time_val, obj_id);


--
-- Name: tb_job_status tb_job_status_pkey; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_job_status
    ADD CONSTRAINT tb_job_status_pkey PRIMARY KEY (job_name, job_group);


--
-- Name: tb_prcd_cols_map tb_prcd_cols_map_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_cols_map
    ADD CONSTRAINT tb_prcd_cols_map_pk PRIMARY KEY (prcd_id);


--
-- Name: tb_prcd_flow tb_prcd_flow_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_flow
    ADD CONSTRAINT tb_prcd_flow_pk PRIMARY KEY (prcd_id);


--
-- Name: tb_prcd_join_method tb_prcd_join_method_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_join_method
    ADD CONSTRAINT tb_prcd_join_method_pk PRIMARY KEY (prcd_join_id);


--
-- Name: tb_prcd_load tb_prcd_load_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_load
    ADD CONSTRAINT tb_prcd_load_pk PRIMARY KEY (prcd_id);


--
-- Name: tb_object tb_prcd_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_object
    ADD CONSTRAINT tb_prcd_pk PRIMARY KEY (obj_id);


--
-- Name: tb_prcd_input tb_prcd_trnf_input_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_input
    ADD CONSTRAINT tb_prcd_trnf_input_pk PRIMARY KEY (prcd_in_id);


--
-- Name: tb_prcd_output tb_prcd_trnf_output_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_output
    ADD CONSTRAINT tb_prcd_trnf_output_pk PRIMARY KEY (prcd_out_id);


--
-- Name: tb_prcd_trnf tb_prcd_trnf_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_trnf
    ADD CONSTRAINT tb_prcd_trnf_pk PRIMARY KEY (prcd_id);


--
-- Name: tb_prcd_xsql tb_prcd_xsql_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_xsql
    ADD CONSTRAINT tb_prcd_xsql_pk PRIMARY KEY (prcd_id);


--
-- Name: tb_proj tb_proj_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_proj
    ADD CONSTRAINT tb_proj_pk PRIMARY KEY (proj_id);


--
-- Name: tb_property tb_property_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_property
    ADD CONSTRAINT tb_property_pk PRIMARY KEY (obj_id, attr_ki, attr_tp);


--
-- Name: tb_user_role_authority tb_role_authority_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_role_authority
    ADD CONSTRAINT tb_role_authority_pk PRIMARY KEY (role_nm, a_obj_id, a_obj_tp);


--
-- Name: tb_setting tb_setting_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_setting
    ADD CONSTRAINT tb_setting_pk PRIMARY KEY (ki, tp);


--
-- Name: tb_task_param tb_task_param_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_task_param
    ADD CONSTRAINT tb_task_param_pk PRIMARY KEY (task_id, prcd_nm, param_nm);


--
-- Name: tb_task tb_task_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_task
    ADD CONSTRAINT tb_task_pk PRIMARY KEY (task_id);


--
-- Name: tb_user_asgn_role tb_user_asgn_role_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_asgn_role
    ADD CONSTRAINT tb_user_asgn_role_pk PRIMARY KEY (user_id, role_nm);


--
-- Name: tb_user tb_user_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user
    ADD CONSTRAINT tb_user_pk PRIMARY KEY (user_id);


--
-- Name: tb_user_role tb_user_role_pk; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_role
    ADD CONSTRAINT tb_user_role_pk PRIMARY KEY (role_nm);


--
-- Name: tb_data_model_attr pk_data_model_attr; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_attr
    ADD CONSTRAINT pk_data_model_attr PRIMARY KEY (dm_id, obj_nm, attr_nm);


--
-- Name: tb_data_model_obj pk_data_model_obj; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_obj
    ADD CONSTRAINT pk_data_model_obj PRIMARY KEY (dm_id, obj_nm);


--
-- Name: tb_board pk_tb_board; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_board
    ADD CONSTRAINT pk_tb_board PRIMARY KEY (board_id);


--
-- Name: tb_board_comment pk_tb_board_comment; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_board_comment
    ADD CONSTRAINT pk_tb_board_comment PRIMARY KEY (comment_id);


--
-- Name: tb_change_history pk_tb_change_history; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_change_history
    ADD CONSTRAINT pk_tb_change_history PRIMARY KEY (change_id);


--
-- Name: tb_change_history_detail pk_tb_change_history_detail; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_change_history_detail
    ADD CONSTRAINT pk_tb_change_history_detail PRIMARY KEY (change_id, seq);


--
-- Name: tb_data_model_constraint pk_tb_data_model_constraint; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_constraint
    ADD CONSTRAINT pk_tb_data_model_constraint PRIMARY KEY (dm_id, obj_owner, table_nm, constraint_nm, column_pos);


--
-- Name: tb_data_model_index pk_tb_data_model_index; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_index
    ADD CONSTRAINT pk_tb_data_model_index PRIMARY KEY (dm_id, obj_owner, table_nm, index_nm, column_pos);


--
-- Name: tb_diag_job pk_tb_diag_job; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_job
    ADD CONSTRAINT pk_tb_diag_job PRIMARY KEY (diag_job_id);


--
-- Name: tb_diag_result pk_tb_diag_result; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_result
    ADD CONSTRAINT pk_tb_diag_result PRIMARY KEY (result_id);


--
-- Name: tb_diag_schedule pk_tb_diag_schedule; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_schedule
    ADD CONSTRAINT pk_tb_diag_schedule PRIMARY KEY (schedule_id);


--
-- Name: tb_diag_schedule_log pk_tb_diag_schedule_log; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_schedule_log
    ADD CONSTRAINT pk_tb_diag_schedule_log PRIMARY KEY (log_id);


--
-- Name: tb_domain_rule pk_tb_domain_rule; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_rule
    ADD CONSTRAINT pk_tb_domain_rule PRIMARY KEY (domain_rule_id);


--
-- Name: tb_qual_col_rule pk_tb_qual_col_rule; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_col_rule
    ADD CONSTRAINT pk_tb_qual_col_rule PRIMARY KEY (dm_id, obj_nm, attr_nm);


--
-- Name: tb_qual_diag_history pk_tb_qual_diag_history; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_diag_history
    ADD CONSTRAINT pk_tb_qual_diag_history PRIMARY KEY (diag_id);


--
-- Name: tb_qual_profile_history pk_tb_qual_profile_history; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_profile_history
    ADD CONSTRAINT pk_tb_qual_profile_history PRIMARY KEY (diag_id, dm_id, obj_nm, attr_nm);


--
-- Name: tb_qual_profile_result pk_tb_qual_profile_result; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_profile_result
    ADD CONSTRAINT pk_tb_qual_profile_result PRIMARY KEY (dm_id, obj_nm, attr_nm);


--
-- Name: tb_qual_rule pk_tb_qual_rule; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_rule
    ADD CONSTRAINT pk_tb_qual_rule PRIMARY KEY (rule_id);


--
-- Name: tb_qual_rule_catalog pk_tb_qual_rule_catalog; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_rule_catalog
    ADD CONSTRAINT pk_tb_qual_rule_catalog PRIMARY KEY (catalog_id);


--
-- Name: tb_qual_rule_result pk_tb_qual_rule_result; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_rule_result
    ADD CONSTRAINT pk_tb_qual_rule_result PRIMARY KEY (diag_id, rule_id, obj_nm, attr_nm);


--
-- Name: tb_qual_violation_sample pk_tb_qual_violation_sample; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_violation_sample
    ADD CONSTRAINT pk_tb_qual_violation_sample PRIMARY KEY (diag_id, rule_id, obj_nm, attr_nm, seq);


--
-- Name: tb_struct_diag_constraint_detail pk_tb_struct_diag_constraint_detail; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_struct_diag_constraint_detail
    ADD CONSTRAINT pk_tb_struct_diag_constraint_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_struct_diag_detail pk_tb_struct_diag_detail; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_struct_diag_detail
    ADD CONSTRAINT pk_tb_struct_diag_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_struct_diag_history pk_tb_struct_diag_history; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_struct_diag_history
    ADD CONSTRAINT pk_tb_struct_diag_history PRIMARY KEY (diag_id);


--
-- Name: tb_struct_diag_index_detail pk_tb_struct_diag_index_detail; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_struct_diag_index_detail
    ADD CONSTRAINT pk_tb_struct_diag_index_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_word_dict pk_tb_word_dict; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_word_dict
    ADD CONSTRAINT pk_tb_word_dict PRIMARY KEY (word_kor);


--
-- Name: tb_aprv_stats tb_aprv_req_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_stats
    ADD CONSTRAINT tb_aprv_req_pkey PRIMARY KEY (req_id);


--
-- Name: tb_board_file tb_board_file_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_board_file
    ADD CONSTRAINT tb_board_file_pkey PRIMARY KEY (file_id);


--
-- Name: tb_code_data tb_code_data_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_code_data
    ADD CONSTRAINT tb_code_data_pkey PRIMARY KEY (code_id);


--
-- Name: tb_data_model_clct tb_data_model_clct_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_clct
    ADD CONSTRAINT tb_data_model_clct_pkey PRIMARY KEY (dm_clct_id, dm_id);


--
-- Name: tb_data_model_map tb_data_model_map_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_map
    ADD CONSTRAINT tb_data_model_map_pkey PRIMARY KEY (dm_id, obj_nm, attr_nm);


--
-- Name: tb_data_model tb_data_model_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model
    ADD CONSTRAINT tb_data_model_pkey PRIMARY KEY (dm_id);


--
-- Name: tb_data_model_schema tb_data_model_schema_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_schema
    ADD CONSTRAINT tb_data_model_schema_pkey PRIMARY KEY (dm_id, schema_nm);


--
-- Name: tb_domain_clsf tb_domain_clsf_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_pkey PRIMARY KEY (domain_clsf_id);


--
-- Name: tb_domain_grp tb_domain_grp_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_grp
    ADD CONSTRAINT tb_domain_grp_pkey PRIMARY KEY (domain_grp_id);


--
-- Name: tb_domain tb_domain_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_pkey PRIMARY KEY (domain_id);


--
-- Name: tb_event_log tb_event_log_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_event_log
    ADD CONSTRAINT tb_event_log_pk PRIMARY KEY (time_val);


--
-- Name: tb_sys_info tb_sys_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_sys_info
    ADD CONSTRAINT tb_sys_pk PRIMARY KEY (sys_cd);


--
-- Name: tb_terms tb_terms_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms
    ADD CONSTRAINT tb_terms_pkey PRIMARY KEY (terms_id);


--
-- Name: tb_terms_words tb_terms_words_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_pkey PRIMARY KEY (terms_id, word_id, word_ord);


--
-- Name: tb_user tb_user_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_user
    ADD CONSTRAINT tb_user_pk PRIMARY KEY (user_id);


--
-- Name: tb_word tb_word_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_word
    ADD CONSTRAINT tb_word_pkey PRIMARY KEY (word_id);


--
-- Name: idx_qrtz_ft_inst_job_req_rcvry; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry ON ndata.qrtz_fired_triggers USING btree (sched_name, instance_name, requests_recovery);


--
-- Name: idx_qrtz_ft_j_g; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_j_g ON ndata.qrtz_fired_triggers USING btree (sched_name, job_name, job_group);


--
-- Name: idx_qrtz_ft_jg; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_jg ON ndata.qrtz_fired_triggers USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_ft_t_g; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_t_g ON ndata.qrtz_fired_triggers USING btree (sched_name, trigger_name, trigger_group);


--
-- Name: idx_qrtz_ft_tg; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_tg ON ndata.qrtz_fired_triggers USING btree (sched_name, trigger_group);


--
-- Name: idx_qrtz_ft_trig_inst_name; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_ft_trig_inst_name ON ndata.qrtz_fired_triggers USING btree (sched_name, instance_name);


--
-- Name: idx_qrtz_j_grp; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_j_grp ON ndata.qrtz_job_details USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_j_req_recovery; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_j_req_recovery ON ndata.qrtz_job_details USING btree (sched_name, requests_recovery);


--
-- Name: idx_qrtz_t_c; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_c ON ndata.qrtz_triggers USING btree (sched_name, calendar_name);


--
-- Name: idx_qrtz_t_g; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_g ON ndata.qrtz_triggers USING btree (sched_name, trigger_group);


--
-- Name: idx_qrtz_t_j; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_j ON ndata.qrtz_triggers USING btree (sched_name, job_name, job_group);


--
-- Name: idx_qrtz_t_jg; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_jg ON ndata.qrtz_triggers USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_t_n_g_state; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_n_g_state ON ndata.qrtz_triggers USING btree (sched_name, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_n_state; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_n_state ON ndata.qrtz_triggers USING btree (sched_name, trigger_name, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_next_fire_time; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_next_fire_time ON ndata.qrtz_triggers USING btree (sched_name, next_fire_time);


--
-- Name: idx_qrtz_t_nft_misfire; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_misfire ON ndata.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time);


--
-- Name: idx_qrtz_t_nft_st; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st ON ndata.qrtz_triggers USING btree (sched_name, trigger_state, next_fire_time);


--
-- Name: idx_qrtz_t_nft_st_misfire; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st_misfire ON ndata.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time, trigger_state);


--
-- Name: idx_qrtz_t_nft_st_misfire_grp; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st_misfire_grp ON ndata.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_state; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX idx_qrtz_t_state ON ndata.qrtz_triggers USING btree (sched_name, trigger_state);


--
-- Name: tb_event_log_obj_id_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_event_log_obj_id_idx ON ndata.tb_event_log USING btree (obj_id);


--
-- Name: tb_job_log_obj_id_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_job_log_obj_id_idx ON ndata.tb_job_log USING btree (obj_id);


--
-- Name: tb_job_log_p_obj_id_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_job_log_p_obj_id_idx ON ndata.tb_job_log USING btree (time_val, p_obj_id, obj_id);


--
-- Name: tb_job_log_time_val_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_job_log_time_val_idx ON ndata.tb_job_log USING btree (time_val DESC);


--
-- Name: tb_object_p_obj_id_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_object_p_obj_id_idx ON ndata.tb_object USING btree (p_obj_id, obj_id);


--
-- Name: tb_task_proj_id_idx; Type: INDEX; Schema: ndata; Owner: -
--

CREATE INDEX tb_task_proj_id_idx ON ndata.tb_task USING btree (proj_id);


--
-- Name: domain_grp_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX domain_grp_ux_1 ON quality.tb_domain_grp USING btree (domain_grp_nm);


--
-- Name: idx_change_history_dt; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX idx_change_history_dt ON quality.tb_change_history USING btree (change_dt DESC);


--
-- Name: idx_change_history_target; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX idx_change_history_target ON quality.tb_change_history USING btree (target_type, change_dt DESC);


--
-- Name: imsi_comment_attr_name_idx; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX imsi_comment_attr_name_idx ON quality.imsi_comment USING btree (attr_name);


--
-- Name: ix_diag_schedule_active; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_diag_schedule_active ON quality.tb_diag_schedule USING btree (use_yn, data_model_id);


--
-- Name: ix_diag_schedule_log_schedule; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_diag_schedule_log_schedule ON quality.tb_diag_schedule_log USING btree (schedule_id, exec_dt DESC);


--
-- Name: ix_domain_rule_domain; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_domain_rule_domain ON quality.tb_domain_rule USING btree (domain_id, use_yn);


--
-- Name: ix_qual_diag_dm_type; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_diag_dm_type ON quality.tb_qual_diag_history USING btree (dm_id, diag_type, diag_dt DESC);


--
-- Name: ix_qual_profile_history_attr; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_profile_history_attr ON quality.tb_qual_profile_history USING btree (dm_id, obj_nm, attr_nm, diag_dt DESC);


--
-- Name: ix_qual_rule_dm; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_rule_dm ON quality.tb_qual_rule USING btree (dm_id, use_yn);


--
-- Name: tb_code_data_ix_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_code_data_ix_1 ON quality.tb_code_data USING btree (code_grp);


--
-- Name: tb_code_data_ix_2; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_code_data_ix_2 ON quality.tb_code_data USING btree (code_eng_nm);


--
-- Name: tb_code_data_ix_3; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_code_data_ix_3 ON quality.tb_code_data USING btree (code_val);


--
-- Name: tb_code_data_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_code_data_ux_1 ON quality.tb_code_data USING btree (code_nm, code_val);


--
-- Name: tb_data_model_dm_nm_idx; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_data_model_dm_nm_idx ON quality.tb_data_model USING btree (dm_nm, dm_sys_cd, dm_ds_id, ver);


--
-- Name: tb_data_model_ix1; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_ix1 ON quality.tb_data_model_attr USING btree (attr_nm);


--
-- Name: tb_data_model_ix2; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_ix2 ON quality.tb_data_model_attr USING btree (attr_nm_kr);


--
-- Name: tb_data_model_ix3; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_ix3 ON quality.tb_data_model_attr USING btree (obj_nm);


--
-- Name: tb_data_model_map_ix1; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_map_ix1 ON quality.tb_data_model_map USING btree (attr_nm);


--
-- Name: tb_data_model_map_ix2; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_map_ix2 ON quality.tb_data_model_map USING btree (attr_nm_kr);


--
-- Name: tb_data_model_map_ix3; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_data_model_map_ix3 ON quality.tb_data_model_map USING btree (obj_nm);


--
-- Name: tb_domain_clsf_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_domain_clsf_ux_1 ON quality.tb_domain_clsf USING btree (domain_clsf_nm);


--
-- Name: tb_domain_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_domain_ux_1 ON quality.tb_domain USING btree (domain_nm);


--
-- Name: tb_terms_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_terms_ux_1 ON quality.tb_terms USING btree (terms_nm);


--
-- Name: tb_terms_ux_2; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_terms_ux_2 ON quality.tb_terms USING btree (terms_eng_abrv_nm);


--
-- Name: tb_word_ix_3; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_word_ix_3 ON quality.tb_word USING btree (word_eng_nm);


--
-- Name: tb_word_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_word_ux_1 ON quality.tb_word USING btree (word_id, word_nm);


--
-- Name: tb_word_ux_2; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_word_ux_2 ON quality.tb_word USING btree (word_eng_abrv_nm);


--
-- Name: uix_domain_nm; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX uix_domain_nm ON quality.tb_domain USING btree (domain_nm);


--
-- Name: uix_terms_nm; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX uix_terms_nm ON quality.tb_terms USING btree (terms_nm);


--
-- Name: uix_word_eng_abrv_nm; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX uix_word_eng_abrv_nm ON quality.tb_word USING btree (word_eng_abrv_nm);


--
-- Name: uix_word_nm; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX uix_word_nm ON quality.tb_word USING btree (word_nm);


--
-- Name: tb_event_log ts_insert_blocker; Type: TRIGGER; Schema: ndata; Owner: -
--

CREATE TRIGGER ts_insert_blocker BEFORE INSERT ON ndata.tb_event_log FOR EACH ROW EXECUTE FUNCTION _timescaledb_internal.insert_blocker();


--
-- Name: tb_job_log ts_insert_blocker; Type: TRIGGER; Schema: ndata; Owner: -
--

CREATE TRIGGER ts_insert_blocker BEFORE INSERT ON ndata.tb_job_log FOR EACH ROW EXECUTE FUNCTION _timescaledb_internal.insert_blocker();


--
-- Name: tb_event_log ts_insert_blocker; Type: TRIGGER; Schema: quality; Owner: -
--

CREATE TRIGGER ts_insert_blocker BEFORE INSERT ON quality.tb_event_log FOR EACH ROW EXECUTE FUNCTION _timescaledb_internal.insert_blocker();


--
-- Name: qrtz_blob_triggers qrtz_blob_triggers_sched_name_trigger_name_trigger_group_fkey; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_blob_triggers
    ADD CONSTRAINT qrtz_blob_triggers_sched_name_trigger_name_trigger_group_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES ndata.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_cron_triggers qrtz_cron_triggers_sched_name_trigger_name_trigger_group_fkey; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_cron_triggers
    ADD CONSTRAINT qrtz_cron_triggers_sched_name_trigger_name_trigger_group_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES ndata.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simple_triggers qrtz_simple_triggers_sched_name_trigger_name_trigger_group_fkey; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_simple_triggers
    ADD CONSTRAINT qrtz_simple_triggers_sched_name_trigger_name_trigger_group_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES ndata.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simprop_triggers qrtz_simprop_triggers_sched_name_trigger_name_trigger_grou_fkey; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_simprop_triggers
    ADD CONSTRAINT qrtz_simprop_triggers_sched_name_trigger_name_trigger_grou_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES ndata.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_triggers qrtz_triggers_sched_name_job_name_job_group_fkey; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.qrtz_triggers
    ADD CONSTRAINT qrtz_triggers_sched_name_job_name_job_group_fkey FOREIGN KEY (sched_name, job_name, job_group) REFERENCES ndata.qrtz_job_details(sched_name, job_name, job_group);


--
-- Name: tb_prcd_flow tb_prcd_flow_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_flow
    ADD CONSTRAINT tb_prcd_flow_fk FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_join_method tb_prcd_join_method_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_join_method
    ADD CONSTRAINT tb_prcd_join_method_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_prcd_trnf(prcd_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_load tb_prcd_load_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_load
    ADD CONSTRAINT tb_prcd_load_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_load tb_prcd_load_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_load
    ADD CONSTRAINT tb_prcd_load_fk_1 FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_trnf tb_prcd_trnf_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_trnf
    ADD CONSTRAINT tb_prcd_trnf_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_trnf tb_prcd_trnf_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_trnf
    ADD CONSTRAINT tb_prcd_trnf_fk_1 FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_input tb_prcd_trnf_input_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_input
    ADD CONSTRAINT tb_prcd_trnf_input_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_prcd_trnf(prcd_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_input tb_prcd_trnf_input_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_input
    ADD CONSTRAINT tb_prcd_trnf_input_fk_1 FOREIGN KEY (data_tmpl_id) REFERENCES ndata.tb_data_tmpl(data_tmpl_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_cols_map tb_prcd_trnf_map_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_cols_map
    ADD CONSTRAINT tb_prcd_trnf_map_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_prcd_trnf(prcd_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_output tb_prcd_trnf_output_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_output
    ADD CONSTRAINT tb_prcd_trnf_output_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_prcd_trnf(prcd_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_output tb_prcd_trnf_output_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_output
    ADD CONSTRAINT tb_prcd_trnf_output_fk_1 FOREIGN KEY (data_tmpl_id) REFERENCES ndata.tb_data_tmpl(data_tmpl_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_unld tb_prcd_unld_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_unld
    ADD CONSTRAINT tb_prcd_unld_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_unld tb_prcd_unld_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_unld
    ADD CONSTRAINT tb_prcd_unld_fk_1 FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_xsql tb_prcd_xsql_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_xsql
    ADD CONSTRAINT tb_prcd_xsql_fk FOREIGN KEY (prcd_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_prcd_xsql tb_prcd_xsql_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_prcd_xsql
    ADD CONSTRAINT tb_prcd_xsql_fk_1 FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_proj tb_proj_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_proj
    ADD CONSTRAINT tb_proj_fk FOREIGN KEY (proj_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_property tb_property_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_property
    ADD CONSTRAINT tb_property_fk FOREIGN KEY (obj_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_task tb_task_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_task
    ADD CONSTRAINT tb_task_fk FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_task tb_task_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_task
    ADD CONSTRAINT tb_task_fk_1 FOREIGN KEY (proj_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_task_param tb_task_param_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_task_param
    ADD CONSTRAINT tb_task_param_fk FOREIGN KEY (task_id) REFERENCES ndata.tb_object(obj_id) ON DELETE CASCADE;


--
-- Name: tb_user_asgn_role tb_user_asgn_role_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_asgn_role
    ADD CONSTRAINT tb_user_asgn_role_fk FOREIGN KEY (user_id) REFERENCES ndata.tb_user(user_id) ON DELETE CASCADE;


--
-- Name: tb_user_asgn_role tb_user_asgn_role_fk_1; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_asgn_role
    ADD CONSTRAINT tb_user_asgn_role_fk_1 FOREIGN KEY (role_nm) REFERENCES ndata.tb_user_role(role_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_user_role_authority tb_user_role_authority_fk; Type: FK CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_user_role_authority
    ADD CONSTRAINT tb_user_role_authority_fk FOREIGN KEY (role_nm) REFERENCES ndata.tb_user_role(role_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_data_model tb_data_model_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model
    ADD CONSTRAINT tb_data_model_fk FOREIGN KEY (dm_sys_cd) REFERENCES quality.tb_sys_info(sys_cd);


--
-- Name: tb_domain_clsf tb_domain_clsf_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_fk_1 FOREIGN KEY (domain_grp_nm) REFERENCES quality.tb_domain_grp(domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_1 FOREIGN KEY (domain_grp_nm) REFERENCES quality.tb_domain_grp(domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_2; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_2 FOREIGN KEY (domain_clsf_nm) REFERENCES quality.tb_domain_clsf(domain_clsf_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_terms tb_terms_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms
    ADD CONSTRAINT tb_terms_fk FOREIGN KEY (domain_nm) REFERENCES quality.tb_domain(domain_nm) ON UPDATE CASCADE;


--
-- Name: tb_terms_words tb_terms_words_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_fk_1 FOREIGN KEY (terms_id) REFERENCES quality.tb_terms(terms_id) ON DELETE CASCADE;


--
-- Name: tb_terms_words tb_terms_words_fk_2; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_fk_2 FOREIGN KEY (word_id, word_nm) REFERENCES quality.tb_word(word_id, word_nm) ON UPDATE CASCADE;


--
-- PostgreSQL database dump complete
--

