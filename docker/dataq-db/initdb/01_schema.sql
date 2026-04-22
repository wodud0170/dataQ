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
-- Name: quality; Type: SCHEMA; Schema: -; Owner: admin
--

CREATE SCHEMA quality;


ALTER SCHEMA quality OWNER TO admin;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: tb_event_log; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_event_log OWNER TO admin;

--
-- Name: dual; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.dual (
    c1 character(1)
);


ALTER TABLE quality.dual OWNER TO admin;

--
-- Name: imsi_comment; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.imsi_comment (
    attr_name character varying(100) DEFAULT NULL::character varying,
    data_type character varying(100) DEFAULT NULL::character varying,
    comment1 character varying(100) DEFAULT NULL::character varying,
    comment2 character varying(1000) DEFAULT NULL::character varying,
    attr_name_new character varying(100)
);


ALTER TABLE quality.imsi_comment OWNER TO admin;

--
-- Name: imsi_comment_comdb; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.imsi_comment_comdb (
    attr_name character varying(100) DEFAULT NULL::character varying,
    data_type character varying(100) DEFAULT NULL::character varying,
    data_len character varying(100) DEFAULT NULL::character varying,
    comment1 character varying(200) DEFAULT NULL::character varying,
    comment2 character varying(200) DEFAULT NULL::character varying,
    attr_name_new character varying(100)
);


ALTER TABLE quality.imsi_comment_comdb OWNER TO admin;

--
-- Name: tb_aprv_stats; Type: TABLE; Schema: quality; Owner: admin
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
    aprv_stat_updt_rsn character varying(50)
);


ALTER TABLE quality.tb_aprv_stats OWNER TO admin;

--
-- Name: tb_board; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_board OWNER TO admin;

--
-- Name: tb_board_comment; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_board_comment (
    comment_id character varying(40) NOT NULL,
    board_id character varying(40) NOT NULL,
    content text,
    cret_user_id character varying(40),
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_dt timestamp without time zone
);


ALTER TABLE quality.tb_board_comment OWNER TO admin;

--
-- Name: tb_board_file; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_board_file OWNER TO admin;

--
-- Name: tb_change_history; Type: TABLE; Schema: quality; Owner: admin
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
    change_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE quality.tb_change_history OWNER TO admin;

--
-- Name: TABLE tb_change_history; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_change_history IS '변경 이력 마스터';


--
-- Name: COLUMN tb_change_history.change_type; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_change_history.change_type IS 'INSERT, UPDATE, DELETE, BULK_INSERT';


--
-- Name: COLUMN tb_change_history.target_type; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_change_history.target_type IS 'WORD, TERM, DOMAIN, CODE, CODE_DATA';


--
-- Name: tb_change_history_detail; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_change_history_detail OWNER TO admin;

--
-- Name: TABLE tb_change_history_detail; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_change_history_detail IS '변경 이력 상세 (일괄 등록 시)';


--
-- Name: tb_code_data; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_code_data OWNER TO admin;

--
-- Name: tb_data_model; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model (
    dm_id character varying(22) NOT NULL,
    dm_nm character varying(100) NOT NULL,
    dm_sys_cd character varying(22),
    dm_ds_id character varying(50) NOT NULL,
    ver character varying(10) NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    use_yn character(1) DEFAULT 'Y'::bpchar,
    struct_diag_yn character(1) DEFAULT 'N'::bpchar,
    struct_diag_dt timestamp without time zone
);


ALTER TABLE quality.tb_data_model OWNER TO admin;

--
-- Name: COLUMN tb_data_model.struct_diag_yn; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_data_model.struct_diag_yn IS '구조진단 일치여부 (Y=일치, N=불일치/미진단)';


--
-- Name: COLUMN tb_data_model.struct_diag_dt; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_data_model.struct_diag_dt IS '구조진단 최종 실행일시';


--
-- Name: tb_data_model_attr; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_attr (
    dm_clct_id character varying(22) NOT NULL,
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
    obj_owner character varying(100) DEFAULT ''::character varying
);


ALTER TABLE quality.tb_data_model_attr OWNER TO admin;

--
-- Name: tb_data_model_clct; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_clct (
    dm_clct_id character varying(22) NOT NULL,
    dm_id character varying(22) NOT NULL,
    clct_start_dt character varying(14),
    clct_end_dt character varying(14),
    clct_cmptn_yn character(1),
    cret_user_id character varying(50)
);


ALTER TABLE quality.tb_data_model_clct OWNER TO admin;

--
-- Name: TABLE tb_data_model_clct; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_data_model_clct IS '데이터수집';


--
-- Name: tb_data_model_constraint; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_constraint (
    dm_clct_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    dm_id character varying(40),
    obj_owner character varying(100),
    table_nm character varying(200),
    constraint_nm character varying(200),
    constraint_type character varying(10),
    column_nm character varying(200),
    column_pos integer,
    ref_owner character varying(100),
    ref_table_nm character varying(200),
    ref_column_nm character varying(200),
    delete_rule character varying(20),
    status character varying(10),
    search_condition character varying(2000)
);


ALTER TABLE quality.tb_data_model_constraint OWNER TO admin;

--
-- Name: tb_data_model_index; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_index (
    dm_clct_id character varying(40) NOT NULL,
    seq integer NOT NULL,
    dm_id character varying(40),
    obj_owner character varying(100),
    table_nm character varying(200),
    index_nm character varying(200),
    index_type character varying(50),
    uniqueness character varying(10),
    column_nm character varying(200),
    column_pos integer,
    sort_order character varying(10),
    tablespace_nm character varying(100)
);


ALTER TABLE quality.tb_data_model_index OWNER TO admin;

--
-- Name: tb_data_model_map; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_data_model_map OWNER TO admin;

--
-- Name: tb_data_model_obj; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_obj (
    dm_clct_id character varying(22) NOT NULL,
    dm_id character varying(22) NOT NULL,
    obj_nm character varying(255) NOT NULL,
    obj_nm_kr character varying(255),
    obj_owner character varying(50),
    obj_desc character varying(500),
    obj_attr_cnt numeric(6,0)
);


ALTER TABLE quality.tb_data_model_obj OWNER TO admin;

--
-- Name: tb_data_model_schema; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_schema (
    dm_id character varying(36) NOT NULL,
    schema_nm character varying(100) NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50)
);


ALTER TABLE quality.tb_data_model_schema OWNER TO admin;

--
-- Name: tb_data_model_stats; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_data_model_stats (
    dm_clct_id character varying(22) NOT NULL,
    dm_id character varying(22) NOT NULL,
    obj_cnt numeric(6,0),
    attr_cnt numeric(6,0),
    terms_stnd_rate numeric(5,2),
    word_stnd_rate numeric(5,2),
    domain_stnd_rate numeric(5,2)
);


ALTER TABLE quality.tb_data_model_stats OWNER TO admin;

--
-- Name: tb_diag_job; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_diag_job OWNER TO admin;

--
-- Name: tb_diag_result; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_diag_result OWNER TO admin;

--
-- Name: tb_diag_result_result_id_seq; Type: SEQUENCE; Schema: quality; Owner: admin
--

CREATE SEQUENCE quality.tb_diag_result_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE quality.tb_diag_result_result_id_seq OWNER TO admin;

--
-- Name: tb_diag_result_result_id_seq; Type: SEQUENCE OWNED BY; Schema: quality; Owner: admin
--

ALTER SEQUENCE quality.tb_diag_result_result_id_seq OWNED BY quality.tb_diag_result.result_id;


--
-- Name: tb_domain; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_domain OWNER TO admin;

--
-- Name: tb_domain_clsf; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_domain_clsf OWNER TO admin;

--
-- Name: tb_domain_grp; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_domain_grp OWNER TO admin;

--
-- Name: tb_struct_diag_constraint_detail; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_struct_diag_constraint_detail OWNER TO admin;

--
-- Name: tb_struct_diag_detail; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_struct_diag_detail OWNER TO admin;

--
-- Name: TABLE tb_struct_diag_detail; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_struct_diag_detail IS '구조 진단 상세 (변경 컬럼)';


--
-- Name: COLUMN tb_struct_diag_detail.diag_id; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.diag_id IS '진단 ID';


--
-- Name: COLUMN tb_struct_diag_detail.seq; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.seq IS '순번';


--
-- Name: COLUMN tb_struct_diag_detail.table_nm; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.table_nm IS '테이블명';


--
-- Name: COLUMN tb_struct_diag_detail.column_nm; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.column_nm IS '컬럼명';


--
-- Name: COLUMN tb_struct_diag_detail.change_type; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.change_type IS '변경유형 (ADDED/MODIFIED/DELETED)';


--
-- Name: COLUMN tb_struct_diag_detail.prev_data_type; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_data_type IS '이전 데이터타입';


--
-- Name: COLUMN tb_struct_diag_detail.curr_data_type; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_data_type IS '현재 데이터타입';


--
-- Name: COLUMN tb_struct_diag_detail.prev_data_len; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_data_len IS '이전 데이터 길이';


--
-- Name: COLUMN tb_struct_diag_detail.curr_data_len; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_data_len IS '현재 데이터 길이';


--
-- Name: COLUMN tb_struct_diag_detail.prev_nullable; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.prev_nullable IS '이전 Nullable 여부';


--
-- Name: COLUMN tb_struct_diag_detail.curr_nullable; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_detail.curr_nullable IS '현재 Nullable 여부';


--
-- Name: tb_struct_diag_history; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_struct_diag_history OWNER TO admin;

--
-- Name: TABLE tb_struct_diag_history; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_struct_diag_history IS '구조 진단 이력';


--
-- Name: COLUMN tb_struct_diag_history.diag_id; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.diag_id IS '진단 ID';


--
-- Name: COLUMN tb_struct_diag_history.data_model_id; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.data_model_id IS '데이터모델 ID';


--
-- Name: COLUMN tb_struct_diag_history.ds_id; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.ds_id IS '데이터소스 ID';


--
-- Name: COLUMN tb_struct_diag_history.schema_nm; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.schema_nm IS '스키마명';


--
-- Name: COLUMN tb_struct_diag_history.status; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.status IS '진단 상태 (READY/RUNNING/DONE/ERROR)';


--
-- Name: COLUMN tb_struct_diag_history.diag_dt; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.diag_dt IS '진단 일시';


--
-- Name: COLUMN tb_struct_diag_history.prev_collect_dt; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.prev_collect_dt IS '이전 수집 일시';


--
-- Name: COLUMN tb_struct_diag_history.total_tables; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.total_tables IS '전체 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.total_columns; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.total_columns IS '전체 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.added_tables; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.added_tables IS '추가된 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.added_columns; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.added_columns IS '추가된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.modified_columns; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.modified_columns IS '변경된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.deleted_tables; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.deleted_tables IS '삭제된 테이블 수';


--
-- Name: COLUMN tb_struct_diag_history.deleted_columns; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.deleted_columns IS '삭제된 컬럼 수';


--
-- Name: COLUMN tb_struct_diag_history.cret_user_id; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_struct_diag_history.cret_user_id IS '실행자 ID';


--
-- Name: tb_struct_diag_index_detail; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_struct_diag_index_detail OWNER TO admin;

--
-- Name: tb_sys_info; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_sys_info OWNER TO admin;

--
-- Name: tb_terms; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_terms OWNER TO admin;

--
-- Name: tb_terms_words; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_terms_words (
    terms_id character varying(22) NOT NULL,
    word_id character varying(22) NOT NULL,
    word_nm character varying(100) NOT NULL,
    word_ord smallint NOT NULL
);


ALTER TABLE quality.tb_terms_words OWNER TO admin;

--
-- Name: tb_user; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_user OWNER TO admin;

--
-- Name: tb_word; Type: TABLE; Schema: quality; Owner: admin
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


ALTER TABLE quality.tb_word OWNER TO admin;

--
-- Name: tb_word_dict; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_word_dict (
    word_kor character varying(100) NOT NULL,
    word_eng character varying(200),
    word_abrv character varying(50),
    domain_clsf_nm character varying(100)
);


ALTER TABLE quality.tb_word_dict OWNER TO admin;

--
-- Name: TABLE tb_word_dict; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON TABLE quality.tb_word_dict IS '미등록 단어 영문 추천 사전';


--
-- Name: COLUMN tb_word_dict.word_kor; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_word_dict.word_kor IS '한글 단어명';


--
-- Name: COLUMN tb_word_dict.word_eng; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_word_dict.word_eng IS '영문 풀네임';


--
-- Name: COLUMN tb_word_dict.word_abrv; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_word_dict.word_abrv IS '영문 약어';


--
-- Name: COLUMN tb_word_dict.domain_clsf_nm; Type: COMMENT; Schema: quality; Owner: admin
--

COMMENT ON COLUMN quality.tb_word_dict.domain_clsf_nm IS '도메인 분류명';


--
-- Name: tb_word_dict_bak; Type: TABLE; Schema: quality; Owner: admin
--

CREATE TABLE quality.tb_word_dict_bak (
    word_kor character varying(100),
    word_eng character varying(200),
    word_abrv character varying(50),
    domain_clsf_nm character varying(100)
);


ALTER TABLE quality.tb_word_dict_bak OWNER TO admin;

--
-- Name: tb_diag_result result_id; Type: DEFAULT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_diag_result ALTER COLUMN result_id SET DEFAULT nextval('quality.tb_diag_result_result_id_seq'::regclass);


--
-- Name: tb_board pk_tb_board; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_board
    ADD CONSTRAINT pk_tb_board PRIMARY KEY (board_id);


--
-- Name: tb_board_comment pk_tb_board_comment; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_board_comment
    ADD CONSTRAINT pk_tb_board_comment PRIMARY KEY (comment_id);


--
-- Name: tb_change_history pk_tb_change_history; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_change_history
    ADD CONSTRAINT pk_tb_change_history PRIMARY KEY (change_id);


--
-- Name: tb_change_history_detail pk_tb_change_history_detail; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_change_history_detail
    ADD CONSTRAINT pk_tb_change_history_detail PRIMARY KEY (change_id, seq);


--
-- Name: tb_data_model_constraint pk_tb_data_model_constraint; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_constraint
    ADD CONSTRAINT pk_tb_data_model_constraint PRIMARY KEY (dm_clct_id, seq);


--
-- Name: tb_data_model_index pk_tb_data_model_index; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_index
    ADD CONSTRAINT pk_tb_data_model_index PRIMARY KEY (dm_clct_id, seq);


--
-- Name: tb_diag_job pk_tb_diag_job; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_diag_job
    ADD CONSTRAINT pk_tb_diag_job PRIMARY KEY (diag_job_id);


--
-- Name: tb_diag_result pk_tb_diag_result; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_diag_result
    ADD CONSTRAINT pk_tb_diag_result PRIMARY KEY (result_id);


--
-- Name: tb_struct_diag_constraint_detail pk_tb_struct_diag_constraint_detail; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_struct_diag_constraint_detail
    ADD CONSTRAINT pk_tb_struct_diag_constraint_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_struct_diag_detail pk_tb_struct_diag_detail; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_struct_diag_detail
    ADD CONSTRAINT pk_tb_struct_diag_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_struct_diag_history pk_tb_struct_diag_history; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_struct_diag_history
    ADD CONSTRAINT pk_tb_struct_diag_history PRIMARY KEY (diag_id);


--
-- Name: tb_struct_diag_index_detail pk_tb_struct_diag_index_detail; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_struct_diag_index_detail
    ADD CONSTRAINT pk_tb_struct_diag_index_detail PRIMARY KEY (diag_id, seq);


--
-- Name: tb_word_dict pk_tb_word_dict; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_word_dict
    ADD CONSTRAINT pk_tb_word_dict PRIMARY KEY (word_kor);


--
-- Name: tb_aprv_stats tb_aprv_req_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_aprv_stats
    ADD CONSTRAINT tb_aprv_req_pkey PRIMARY KEY (req_id);


--
-- Name: tb_board_file tb_board_file_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_board_file
    ADD CONSTRAINT tb_board_file_pkey PRIMARY KEY (file_id);


--
-- Name: tb_code_data tb_code_data_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_code_data
    ADD CONSTRAINT tb_code_data_pkey PRIMARY KEY (code_id);


--
-- Name: tb_data_model_attr tb_data_model_attr_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_attr
    ADD CONSTRAINT tb_data_model_attr_pkey PRIMARY KEY (dm_clct_id, dm_id, obj_owner, obj_nm, attr_nm);


--
-- Name: tb_data_model_clct tb_data_model_clct_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_clct
    ADD CONSTRAINT tb_data_model_clct_pkey PRIMARY KEY (dm_clct_id, dm_id);


--
-- Name: tb_data_model_map tb_data_model_map_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_map
    ADD CONSTRAINT tb_data_model_map_pkey PRIMARY KEY (dm_id, obj_nm, attr_nm);


--
-- Name: tb_data_model_obj tb_data_model_obj_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_obj
    ADD CONSTRAINT tb_data_model_obj_pkey PRIMARY KEY (dm_clct_id, dm_id, obj_nm);


--
-- Name: tb_data_model tb_data_model_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model
    ADD CONSTRAINT tb_data_model_pkey PRIMARY KEY (dm_id);


--
-- Name: tb_data_model_schema tb_data_model_schema_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_schema
    ADD CONSTRAINT tb_data_model_schema_pkey PRIMARY KEY (dm_id, schema_nm);


--
-- Name: tb_data_model_stats tb_data_model_stats_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model_stats
    ADD CONSTRAINT tb_data_model_stats_pkey PRIMARY KEY (dm_clct_id);


--
-- Name: tb_domain_clsf tb_domain_clsf_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_pkey PRIMARY KEY (domain_clsf_id);


--
-- Name: tb_domain_grp tb_domain_grp_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain_grp
    ADD CONSTRAINT tb_domain_grp_pkey PRIMARY KEY (domain_grp_id);


--
-- Name: tb_domain tb_domain_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_pkey PRIMARY KEY (domain_id);


--
-- Name: tb_event_log tb_event_log_pk; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_event_log
    ADD CONSTRAINT tb_event_log_pk PRIMARY KEY (time_val);


--
-- Name: tb_sys_info tb_sys_pk; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_sys_info
    ADD CONSTRAINT tb_sys_pk PRIMARY KEY (sys_cd);


--
-- Name: tb_terms tb_terms_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_terms
    ADD CONSTRAINT tb_terms_pkey PRIMARY KEY (terms_id);


--
-- Name: tb_terms_words tb_terms_words_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_pkey PRIMARY KEY (terms_id, word_id, word_ord);


--
-- Name: tb_user tb_user_pk; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_user
    ADD CONSTRAINT tb_user_pk PRIMARY KEY (user_id);


--
-- Name: tb_word tb_word_pkey; Type: CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_word
    ADD CONSTRAINT tb_word_pkey PRIMARY KEY (word_id);


--
-- Name: domain_grp_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX domain_grp_ux_1 ON quality.tb_domain_grp USING btree (domain_grp_nm);


--
-- Name: idx_change_history_dt; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX idx_change_history_dt ON quality.tb_change_history USING btree (change_dt DESC);


--
-- Name: idx_change_history_target; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX idx_change_history_target ON quality.tb_change_history USING btree (target_type, change_dt DESC);


--
-- Name: imsi_comment_attr_name_idx; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX imsi_comment_attr_name_idx ON quality.imsi_comment USING btree (attr_name);


--
-- Name: tb_code_data_ix_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_code_data_ix_1 ON quality.tb_code_data USING btree (code_grp);


--
-- Name: tb_code_data_ix_2; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_code_data_ix_2 ON quality.tb_code_data USING btree (code_eng_nm);


--
-- Name: tb_code_data_ix_3; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_code_data_ix_3 ON quality.tb_code_data USING btree (code_val);


--
-- Name: tb_code_data_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_code_data_ux_1 ON quality.tb_code_data USING btree (code_nm, code_val);


--
-- Name: tb_data_model_dm_nm_idx; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_data_model_dm_nm_idx ON quality.tb_data_model USING btree (dm_nm, dm_sys_cd, dm_ds_id, ver);


--
-- Name: tb_data_model_ix1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_ix1 ON quality.tb_data_model_attr USING btree (attr_nm);


--
-- Name: tb_data_model_ix2; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_ix2 ON quality.tb_data_model_attr USING btree (attr_nm_kr);


--
-- Name: tb_data_model_ix3; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_ix3 ON quality.tb_data_model_attr USING btree (obj_nm);


--
-- Name: tb_data_model_map_ix1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_map_ix1 ON quality.tb_data_model_map USING btree (attr_nm);


--
-- Name: tb_data_model_map_ix2; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_map_ix2 ON quality.tb_data_model_map USING btree (attr_nm_kr);


--
-- Name: tb_data_model_map_ix3; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_data_model_map_ix3 ON quality.tb_data_model_map USING btree (obj_nm);


--
-- Name: tb_domain_clsf_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_domain_clsf_ux_1 ON quality.tb_domain_clsf USING btree (domain_clsf_nm);


--
-- Name: tb_domain_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_domain_ux_1 ON quality.tb_domain USING btree (domain_nm);


--
-- Name: tb_terms_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_terms_ux_1 ON quality.tb_terms USING btree (terms_nm);


--
-- Name: tb_terms_ux_2; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_terms_ux_2 ON quality.tb_terms USING btree (terms_eng_abrv_nm);


--
-- Name: tb_word_ix_3; Type: INDEX; Schema: quality; Owner: admin
--

CREATE INDEX tb_word_ix_3 ON quality.tb_word USING btree (word_eng_nm);


--
-- Name: tb_word_ux_1; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_word_ux_1 ON quality.tb_word USING btree (word_id, word_nm);


--
-- Name: tb_word_ux_2; Type: INDEX; Schema: quality; Owner: admin
--

CREATE UNIQUE INDEX tb_word_ux_2 ON quality.tb_word USING btree (word_eng_abrv_nm);


--
-- Name: tb_event_log ts_insert_blocker; Type: TRIGGER; Schema: quality; Owner: admin
--

CREATE TRIGGER ts_insert_blocker BEFORE INSERT ON quality.tb_event_log FOR EACH ROW EXECUTE FUNCTION _timescaledb_internal.insert_blocker();


--
-- Name: tb_data_model tb_data_model_fk; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_data_model
    ADD CONSTRAINT tb_data_model_fk FOREIGN KEY (dm_sys_cd) REFERENCES quality.tb_sys_info(sys_cd);


--
-- Name: tb_domain_clsf tb_domain_clsf_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_fk_1 FOREIGN KEY (domain_grp_nm) REFERENCES quality.tb_domain_grp(domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_1 FOREIGN KEY (domain_grp_nm) REFERENCES quality.tb_domain_grp(domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_2; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_2 FOREIGN KEY (domain_clsf_nm) REFERENCES quality.tb_domain_clsf(domain_clsf_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_terms tb_terms_fk; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_terms
    ADD CONSTRAINT tb_terms_fk FOREIGN KEY (domain_nm) REFERENCES quality.tb_domain(domain_nm) ON UPDATE CASCADE;


--
-- Name: tb_terms_words tb_terms_words_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_fk_1 FOREIGN KEY (terms_id) REFERENCES quality.tb_terms(terms_id) ON DELETE CASCADE;


--
-- Name: tb_terms_words tb_terms_words_fk_2; Type: FK CONSTRAINT; Schema: quality; Owner: admin
--

ALTER TABLE ONLY quality.tb_terms_words
    ADD CONSTRAINT tb_terms_words_fk_2 FOREIGN KEY (word_id, word_nm) REFERENCES quality.tb_word(word_id, word_nm) ON UPDATE CASCADE;


--
-- PostgreSQL database dump complete
--

