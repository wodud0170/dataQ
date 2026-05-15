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
-- Name: quality; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA quality;


SET default_tablespace = '';

SET default_table_access_method = heap;

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
-- Name: COLUMN tb_aprv_stats.req_item_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_stats.req_item_nm IS '요청 항목명 (반려 시 원본 삭제되므로 이력 보존용)';


--
-- Name: tb_biz_area; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_biz_area (
    biz_area_id character varying(40) NOT NULL,
    biz_area_nm character varying(200) NOT NULL,
    biz_area_desc character varying(500),
    parent_id character varying(40),
    sort_order integer DEFAULT 0,
    use_yn character(1) DEFAULT 'Y'::bpchar,
    cret_user_id character varying(50),
    cret_dt character varying(14),
    updt_user_id character varying(50),
    updt_dt character varying(14),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
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
    model_type character varying(10) DEFAULT 'PHYSICAL'::character varying,
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
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
    obj_owner character varying(100) DEFAULT ''::character varying NOT NULL,
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
    diag_target_updt_dt character varying(14),
    fk_parent_obj_nm character varying(255),
    fk_parent_attr_nm character varying(255),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
);


--
-- Name: COLUMN tb_data_model_attr.attr_nm_kr; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.attr_nm_kr IS '컬럼 논리명 (편집 가능). 최초 수집 시 ATTR_COMMENT 값 복사';


--
-- Name: COLUMN tb_data_model_attr.attr_comment; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.attr_comment IS 'DB에서 수집한 컬럼 코멘트 원본 (수집 시 자동, 읽기 전용)';


--
-- Name: COLUMN tb_data_model_attr.use_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.use_yn IS '사용여부 Y/N (N=소프트 삭제)';


--
-- Name: COLUMN tb_data_model_attr.deleted_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.deleted_dt IS '소프트 삭제 일시 YYYYMMDDHH24MISS';


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
-- Name: COLUMN tb_data_model_attr.fk_parent_obj_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.fk_parent_obj_nm IS '85번 — FK 일 때 참조 부모 테이블 (XMI 2.1 type id 참조 해석)';


--
-- Name: COLUMN tb_data_model_attr.fk_parent_attr_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.fk_parent_attr_nm IS '85번 — FK 일 때 참조 부모 컬럼 (없으면 부모 PK 추정)';


--
-- Name: tb_data_model_change_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_model_change_history (
    change_seq bigint NOT NULL,
    dm_id character varying(40) NOT NULL,
    change_dt character varying(14) NOT NULL,
    change_user_id character varying(50) NOT NULL,
    change_type character varying(30) NOT NULL,
    change_tier character varying(10) NOT NULL,
    submission_id character varying(40),
    obj_owner character varying(100),
    obj_nm character varying(255),
    attr_nm character varying(255),
    constraint_nm character varying(200),
    index_nm character varying(200),
    before_json text,
    after_json text,
    ddl_snippet text,
    aprv_status character varying(20),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    ddl_exec_dt character varying(14),
    ddl_exec_user_id character varying(50),
    ddl_exec_result character varying(20),
    ddl_exec_message character varying(2000)
);


--
-- Name: tb_data_model_change_history_change_seq_seq; Type: SEQUENCE; Schema: quality; Owner: -
--

CREATE SEQUENCE quality.tb_data_model_change_history_change_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tb_data_model_change_history_change_seq_seq; Type: SEQUENCE OWNED BY; Schema: quality; Owner: -
--

ALTER SEQUENCE quality.tb_data_model_change_history_change_seq_seq OWNED BY quality.tb_data_model_change_history.change_seq;


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
-- Name: COLUMN tb_data_model_clct.clct_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_clct.clct_type IS '스냅샷 원천 (DBMS: 수집, MANUAL: 수동편집, ERWIN: ERwin 임포트)';


--
-- Name: COLUMN tb_data_model_clct.added_cnt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_clct.added_cnt IS '재수집 시 추가된 테이블·컬럼 합계';


--
-- Name: COLUMN tb_data_model_clct.deleted_cnt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_clct.deleted_cnt IS '재수집 시 삭제된 테이블·컬럼 합계';


--
-- Name: COLUMN tb_data_model_clct.modified_cnt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_clct.modified_cnt IS '재수집 시 변경된 테이블·컬럼 합계';


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
    deleted_dt character varying(14),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
);


--
-- Name: COLUMN tb_data_model_constraint.use_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_constraint.use_yn IS '사용 여부 (Y/N). soft-delete 대상은 N';


--
-- Name: COLUMN tb_data_model_constraint.deleted_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_constraint.deleted_dt IS 'soft-delete 시각 (YYYYMMDDHH24MISS)';


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
    deleted_dt character varying(14),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
);


--
-- Name: COLUMN tb_data_model_index.use_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_index.use_yn IS '사용 여부 (Y/N). soft-delete 대상은 N';


--
-- Name: COLUMN tb_data_model_index.deleted_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_index.deleted_dt IS 'soft-delete 시각 (YYYYMMDDHH24MISS)';


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
    obj_owner character varying(50) DEFAULT ''::character varying NOT NULL,
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
    diag_target_updt_dt character varying(14),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40),
    tablespace_nm character varying(100),
    biz_area_id character varying(40),
    subj_area_id character varying(40)
);


--
-- Name: COLUMN tb_data_model_obj.obj_nm_kr; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.obj_nm_kr IS '테이블 논리명 (편집 가능). 최초 수집 시 OBJ_COMMENT 값 복사';


--
-- Name: COLUMN tb_data_model_obj.obj_comment; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.obj_comment IS 'DB에서 수집한 테이블 코멘트 원본 (수집 시 자동, 읽기 전용)';


--
-- Name: COLUMN tb_data_model_obj.use_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.use_yn IS '사용여부 Y/N (N=소프트 삭제)';


--
-- Name: COLUMN tb_data_model_obj.deleted_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.deleted_dt IS '소프트 삭제 일시 YYYYMMDDHH24MISS';


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
    dm_clct_id character varying(50),
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
    sort_ord integer DEFAULT 1 NOT NULL,
    use_yn character varying(1) DEFAULT 'Y'::character varying NOT NULL,
    descr text,
    cret_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    cret_user_id character varying(50),
    updt_dt timestamp without time zone,
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_domain_rule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_domain_rule IS '도메인별 검증 규칙 1:N (70번 §2.1)';


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
-- Name: tb_qual_col_rule; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_col_rule (
    dm_id character varying(40) NOT NULL,
    obj_nm character varying(100) NOT NULL,
    attr_nm character varying(100) NOT NULL,
    domain_rule_id character varying(40),
    custom_rule_id character varying(40),
    exclude_yn character varying(1) DEFAULT 'N'::character varying,
    updt_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_qual_col_rule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_col_rule IS '컬럼 → 적용 규칙 매핑 (70번 §2.2). 행 없으면 도메인 SORT_ORD=1 default';


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
    error_msg text,
    progress_done integer DEFAULT 0,
    progress_total integer DEFAULT 0
);


--
-- Name: TABLE tb_qual_diag_history; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_diag_history IS '데이터 품질 진단 실행 이력 (값/룰 공용, 67번 §5)';


--
-- Name: COLUMN tb_qual_diag_history.progress_done; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_diag_history.progress_done IS '83번 Step5 — 처리 완료 컬럼 수 (실시간 갱신)';


--
-- Name: COLUMN tb_qual_diag_history.progress_total; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_diag_history.progress_total IS '83번 Step5 — 진단 대상 총 컬럼 수';


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

COMMENT ON TABLE quality.tb_qual_profile_history IS '값 진단 시계열 누적 (통계 메뉴용, 70번 §2.3)';


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
    use_yn character(1) DEFAULT 'Y'::bpchar,
    is_built_in character varying(1) DEFAULT 'N'::character varying,
    domain_clsf_nm character varying(50)
);


--
-- Name: TABLE tb_qual_rule_catalog; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_rule_catalog IS '룰 템플릿 (이메일/주민번호 등 표준 정규식)';


--
-- Name: COLUMN tb_qual_rule_catalog.is_built_in; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_rule_catalog.is_built_in IS '시스템 기본 (Y, 읽기전용 + fork만 가능) / 사용자 정의 (N) — 83번';


--
-- Name: COLUMN tb_qual_rule_catalog.domain_clsf_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_rule_catalog.domain_clsf_nm IS '행안부 도메인 분류명 (전화번호/금액/연월일 등). 분류 단위 자동 추천 키';


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
-- Name: tb_qual_running_lock; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_qual_running_lock (
    dm_id character varying(36) NOT NULL,
    obj_nm character varying(200) NOT NULL,
    attr_nm character varying(200) NOT NULL,
    diag_id character varying(50),
    user_id character varying(50),
    start_dt character varying(14) NOT NULL
);


--
-- Name: TABLE tb_qual_running_lock; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_running_lock IS '품질 진단 컬럼 단위 동시 실행 방지 — application-level mutex (운영 DB 락 X). 83번';


--
-- Name: COLUMN tb_qual_running_lock.dm_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.dm_id IS '데이터 모델 ID';


--
-- Name: COLUMN tb_qual_running_lock.obj_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.obj_nm IS '테이블명';


--
-- Name: COLUMN tb_qual_running_lock.attr_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.attr_nm IS '컬럼명';


--
-- Name: COLUMN tb_qual_running_lock.diag_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.diag_id IS '진행 중 진단 ID';


--
-- Name: COLUMN tb_qual_running_lock.user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.user_id IS '진단 트리거한 사용자';


--
-- Name: COLUMN tb_qual_running_lock.start_dt; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_running_lock.start_dt IS 'lock 획득 시각 (YYYYMMDDHH24MISS) — 30분 경과 시 stale 자동 정리';


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
-- Name: tb_subj_area; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_subj_area (
    subj_area_id character varying(40) NOT NULL,
    subj_area_nm character varying(200) NOT NULL,
    subj_area_desc character varying(500),
    parent_id character varying(40),
    sort_order integer DEFAULT 0,
    use_yn character(1) DEFAULT 'Y'::bpchar,
    cret_user_id character varying(50),
    cret_dt character varying(14),
    updt_user_id character varying(50),
    updt_dt character varying(14),
    aprv_status character varying(20) DEFAULT 'APPROVED'::character varying,
    requester_user_id character varying(50),
    req_dt character varying(14),
    aprv_user_id character varying(50),
    aprv_dt character varying(14),
    aprv_comment character varying(500),
    submission_id character varying(40)
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
-- Name: tb_term_resolve_history; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_term_resolve_history (
    history_seq bigint NOT NULL,
    dm_id character varying(40),
    obj_owner character varying(100),
    obj_nm character varying(255),
    attr_nm character varying(255),
    input_kr_nm character varying(500) NOT NULL,
    resolved_kr_nm character varying(500),
    resolved_en_nm character varying(255),
    resolved_terms_id character varying(40),
    resolved_data_type character varying(50),
    resolved_data_len bigint,
    resolve_reason character varying(500),
    change_user_id character varying(50),
    change_dt character varying(14)
);


--
-- Name: tb_term_resolve_history_history_seq_seq; Type: SEQUENCE; Schema: quality; Owner: -
--

CREATE SEQUENCE quality.tb_term_resolve_history_history_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tb_term_resolve_history_history_seq_seq; Type: SEQUENCE OWNED BY; Schema: quality; Owner: -
--

ALTER SEQUENCE quality.tb_term_resolve_history_history_seq_seq OWNED BY quality.tb_term_resolve_history.history_seq;


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
-- Name: tb_data_model_change_history change_seq; Type: DEFAULT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_change_history ALTER COLUMN change_seq SET DEFAULT nextval('quality.tb_data_model_change_history_change_seq_seq'::regclass);


--
-- Name: tb_diag_result result_id; Type: DEFAULT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_diag_result ALTER COLUMN result_id SET DEFAULT nextval('quality.tb_diag_result_result_id_seq'::regclass);


--
-- Name: tb_term_resolve_history history_seq; Type: DEFAULT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_term_resolve_history ALTER COLUMN history_seq SET DEFAULT nextval('quality.tb_term_resolve_history_history_seq_seq'::regclass);


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
-- Name: tb_qual_running_lock pk_tb_qual_running_lock; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_qual_running_lock
    ADD CONSTRAINT pk_tb_qual_running_lock PRIMARY KEY (dm_id, obj_nm, attr_nm);


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
-- Name: tb_biz_area tb_biz_area_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_biz_area
    ADD CONSTRAINT tb_biz_area_pkey PRIMARY KEY (biz_area_id);


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
-- Name: tb_data_model_attr tb_data_model_attr_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_attr
    ADD CONSTRAINT tb_data_model_attr_pkey PRIMARY KEY (dm_id, obj_owner, obj_nm, attr_nm);


--
-- Name: tb_data_model_change_history tb_data_model_change_history_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_change_history
    ADD CONSTRAINT tb_data_model_change_history_pkey PRIMARY KEY (change_seq);


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
-- Name: tb_data_model_obj tb_data_model_obj_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model_obj
    ADD CONSTRAINT tb_data_model_obj_pkey PRIMARY KEY (dm_id, obj_owner, obj_nm);


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
-- Name: tb_subj_area tb_subj_area_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_subj_area
    ADD CONSTRAINT tb_subj_area_pkey PRIMARY KEY (subj_area_id);


--
-- Name: tb_sys_info tb_sys_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_sys_info
    ADD CONSTRAINT tb_sys_pk PRIMARY KEY (sys_cd);


--
-- Name: tb_term_resolve_history tb_term_resolve_history_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_term_resolve_history
    ADD CONSTRAINT tb_term_resolve_history_pkey PRIMARY KEY (history_seq);


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
-- Name: idx_qual_rule_catalog_clsf; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX idx_qual_rule_catalog_clsf ON quality.tb_qual_rule_catalog USING btree (domain_clsf_nm, is_built_in);


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
-- Name: ix_domain_rule_dom; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_domain_rule_dom ON quality.tb_domain_rule USING btree (domain_id, sort_ord);


--
-- Name: ix_qual_diag_dm_type; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_diag_dm_type ON quality.tb_qual_diag_history USING btree (dm_id, diag_type, diag_dt DESC);


--
-- Name: ix_qual_profile_hist_timeline; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_profile_hist_timeline ON quality.tb_qual_profile_history USING btree (dm_id, obj_nm, attr_nm, diag_dt DESC);


--
-- Name: ix_qual_rule_dm; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_qual_rule_dm ON quality.tb_qual_rule USING btree (dm_id, use_yn);


--
-- Name: ix_tb_biz_area_parent; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_biz_area_parent ON quality.tb_biz_area USING btree (parent_id);


--
-- Name: ix_tb_dmch_dm; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_dmch_dm ON quality.tb_data_model_change_history USING btree (dm_id, change_dt DESC);


--
-- Name: ix_tb_dmch_status; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_dmch_status ON quality.tb_data_model_change_history USING btree (aprv_status);


--
-- Name: ix_tb_dmch_submission; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_dmch_submission ON quality.tb_data_model_change_history USING btree (submission_id);


--
-- Name: ix_tb_dmch_user; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_dmch_user ON quality.tb_data_model_change_history USING btree (change_user_id);


--
-- Name: ix_tb_subj_area_parent; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_tb_subj_area_parent ON quality.tb_subj_area USING btree (parent_id);


--
-- Name: ix_term_resolve_dt; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_term_resolve_dt ON quality.tb_term_resolve_history USING btree (change_dt DESC);


--
-- Name: ix_term_resolve_input; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_term_resolve_input ON quality.tb_term_resolve_history USING btree (input_kr_nm);


--
-- Name: ix_term_resolve_user; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_term_resolve_user ON quality.tb_term_resolve_history USING btree (change_user_id);


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
-- Name: tb_event_log_time_idx; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_event_log_time_idx ON quality.tb_event_log USING btree (time_val DESC);


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

