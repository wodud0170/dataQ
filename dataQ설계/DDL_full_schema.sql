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
-- Name: tb_aprv_doc; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_aprv_doc (
    doc_id character varying(40) NOT NULL,
    req_tp character varying(20) NOT NULL,
    target_id character varying(50) NOT NULL,
    target_nm character varying(300),
    dm_id character varying(22),
    dict_id character varying(22),
    req_user_id character varying(50) NOT NULL,
    req_dt character varying(14) NOT NULL,
    doc_stat character varying(20) NOT NULL,
    cur_step_no integer NOT NULL,
    step_cnt integer NOT NULL,
    end_dt character varying(14)
);


--
-- Name: TABLE tb_aprv_doc; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_aprv_doc IS '결재 문서 (104번 WP1) — 표준사전 항목 하나 또는 데이터 모델 신청(SUBMISSION_ID) 하나';


--
-- Name: COLUMN tb_aprv_doc.target_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_doc.target_id IS 'WORD·TERMS·CODE·DOMAIN 은 항목 ID, DM_CHANGE 는 SUBMISSION_ID';


--
-- Name: COLUMN tb_aprv_doc.doc_stat; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_doc.doc_stat IS 'PROGRESS, APPROVED, REJECTED, CANCELED(대상이 다른 경로로 없어짐)';


--
-- Name: tb_aprv_doc_step; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_aprv_doc_step (
    doc_id character varying(40) NOT NULL,
    step_no integer NOT NULL,
    aprvr_user_id character varying(50) NOT NULL,
    step_nm character varying(100),
    agree_yn character(1) DEFAULT 'N'::bpchar NOT NULL,
    act_stat character varying(20) DEFAULT 'WAIT'::character varying NOT NULL,
    act_cmnt character varying(500),
    act_dt character varying(14),
    act_user_id character varying(50)
);


--
-- Name: TABLE tb_aprv_doc_step; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_aprv_doc_step IS '결재 문서의 단계별 결재자 (104번 WP1). 문서를 만들 때 결재자를 정해 둔다';


--
-- Name: COLUMN tb_aprv_doc_step.act_stat; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_doc_step.act_stat IS 'WAIT, APPROVED, REJECTED';


--
-- Name: COLUMN tb_aprv_doc_step.act_user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_doc_step.act_user_id IS '실제 처리자 — 관리자가 대신 처리하면 관리자 ID';


--
-- Name: tb_aprv_line; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_aprv_line (
    req_tp character varying(20) NOT NULL,
    line_nm character varying(100),
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_aprv_line; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_aprv_line IS '요청 유형별 결재선 (104번 WP1). 행이 없거나 USE_YN=N 이면 결재선 없이 지금처럼 승인';


--
-- Name: COLUMN tb_aprv_line.req_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_line.req_tp IS '요청 유형 — WORD, TERMS, CODE, DOMAIN, DM_CHANGE';


--
-- Name: tb_aprv_line_step; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_aprv_line_step (
    req_tp character varying(20) NOT NULL,
    step_no integer NOT NULL,
    step_nm character varying(100),
    aprvr_tp character varying(20) NOT NULL,
    aprvr_id character varying(50),
    agree_yn character(1) DEFAULT 'N'::bpchar NOT NULL
);


--
-- Name: TABLE tb_aprv_line_step; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_aprv_line_step IS '결재선 단계 (104번 WP1)';


--
-- Name: COLUMN tb_aprv_line_step.aprvr_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_tp IS '결재자 방식 — USER(지정 사용자), ROLE(역할의 사용자), ADMIN(관리자 전원), ORG_HEAD(요청자 부서장)';


--
-- Name: COLUMN tb_aprv_line_step.aprvr_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_id IS 'USER 면 사용자 ID, ROLE 이면 역할 ID, ADMIN·ORG_HEAD 면 NULL';


--
-- Name: COLUMN tb_aprv_line_step.agree_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_aprv_line_step.agree_yn IS '합의 — Y 면 단계 결재자 전원 승인, N 이면 한 명 승인으로 다음 단계';


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

COMMENT ON COLUMN quality.tb_change_history.change_source IS 'ADMIN_DIRECT / USER_REQUEST / ADMIN_APPROVE / ADMIN_REJECT / BULK_UPLOAD / AUTO_RECOMMEND / APPROVER_EDIT(결재자 수정, 104번 WP1)';


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
    updt_user_id character varying(50),
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL,
    parent_code_val character varying(50)
);


--
-- Name: COLUMN tb_code_data.parent_code_val; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_code_data.parent_code_val IS '상위 코드값 — 계층 코드 값 (상위 코드 그룹의 코드값, 104번 WP6-3)';


--
-- Name: tb_code_integ; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_code_integ (
    dict_id character varying(22) NOT NULL,
    code_grp character varying(50) NOT NULL,
    sub_code_grp character varying(50) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50)
);


--
-- Name: TABLE tb_code_integ; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_code_integ IS '통합 코드 구성 — 통합 코드 그룹 → 하위 코드 그룹 (104번 WP6-3)';


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
    submission_id character varying(40),
    dict_id character varying(22)
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
-- Name: COLUMN tb_data_model.dict_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model.dict_id IS '이 모델에 적용할 표준사전. NULL 이면 스케줄 진단 불가';


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
    submission_id character varying(40),
    fk_parent_obj_owner character varying(100),
    priv_yn character(1),
    encr_yn character(1),
    dbms_data_type character varying(100),
    open_tp character varying(10),
    open_limit_reason character varying(500),
    cnstr_desc character varying(1000)
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
-- Name: COLUMN tb_data_model_attr.priv_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.priv_yn IS '개인정보여부 — NULL 이면 용어 → 도메인 값 (104번 WP5-16)';


--
-- Name: COLUMN tb_data_model_attr.encr_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.encr_yn IS '암호화여부 — NULL 이면 용어 → 도메인 값 (104번 WP5-16)';


--
-- Name: COLUMN tb_data_model_attr.dbms_data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.dbms_data_type IS '수집 원본 데이터타입 (DBMS 종속 타입명, 104번 WP5-78)';


--
-- Name: COLUMN tb_data_model_attr.open_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.open_tp IS '컬럼 공개/비공개여부 (별표 4)';


--
-- Name: COLUMN tb_data_model_attr.open_limit_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.open_limit_reason IS '컬럼 비공개 사유 (별표 4)';


--
-- Name: COLUMN tb_data_model_attr.cnstr_desc; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_attr.cnstr_desc IS '제약조건 — 허용범위·구분값·기본값 등 (별표 4)';


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
    ddl_exec_message character varying(2000),
    ddl_dbms_tp character varying(20)
);


--
-- Name: COLUMN tb_data_model_change_history.ddl_dbms_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_change_history.ddl_dbms_tp IS 'DDL_SNIPPET 을 만든 DBMS (모델 데이터소스 DBMS_TP). 데이터소스 없는 모델은 NULL (104번 WP3)';


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
    subj_area_id character varying(40),
    gov_send_yn character(1) DEFAULT 'N'::bpchar,
    tbl_tp character varying(20),
    occr_cycle character varying(20),
    open_tp character varying(10),
    open_limit_reason character varying(500),
    open_limit_basis character varying(500),
    open_data_lst_nm character varying(500)
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
-- Name: COLUMN tb_data_model_obj.gov_send_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.gov_send_yn IS '범정부 메타 전송대상 (104번 WP5-78)';


--
-- Name: COLUMN tb_data_model_obj.tbl_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.tbl_tp IS '테이블유형 (별표 4)';


--
-- Name: COLUMN tb_data_model_obj.occr_cycle; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.occr_cycle IS '발생주기 (별표 4)';


--
-- Name: COLUMN tb_data_model_obj.open_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.open_tp IS '공개/비공개여부 — 공개·부분공개·비공개 (별표 4)';


--
-- Name: COLUMN tb_data_model_obj.open_limit_reason; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.open_limit_reason IS '부분공개·비공개 사유 (별표 4)';


--
-- Name: COLUMN tb_data_model_obj.open_limit_basis; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.open_limit_basis IS '부분공개·비공개 관련근거 (별표 4)';


--
-- Name: COLUMN tb_data_model_obj.open_data_lst_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_model_obj.open_data_lst_nm IS '개방데이터목록 (별표 4)';


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
-- Name: tb_data_type_map; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_data_type_map (
    data_type character varying(50) NOT NULL,
    dbms_tp character varying(20) NOT NULL,
    phys_data_type character varying(100) NOT NULL,
    dflt_data_len integer,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_data_type_map; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_data_type_map IS '데이터 타입 → DBMS 별 물리 타입 (104번 WP3, 관리 > DDL 규칙)';


--
-- Name: COLUMN tb_data_type_map.data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_type_map.data_type IS '모델·도메인의 데이터 타입 (대문자)';


--
-- Name: COLUMN tb_data_type_map.dbms_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_type_map.dbms_tp IS '데이터소스 DBMS_TP — Oracle, Tibero, PostgreSQL, MariaDB, SQLServer';


--
-- Name: COLUMN tb_data_type_map.phys_data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_type_map.phys_data_type IS '물리 타입 틀. {len}·{dec} = 길이·소수점, [...] = 안의 값이 모두 있을 때만 남는 구간';


--
-- Name: COLUMN tb_data_type_map.dflt_data_len; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_data_type_map.dflt_data_len IS '길이가 없는 컬럼에 쓸 길이 ([ ] 밖에 {len} 이 있으면 필수)';


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
    end_dt character varying(14),
    dict_id character varying(22)
);


--
-- Name: COLUMN tb_diag_job.dict_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_job.dict_id IS '이 진단이 사용한 표준사전';


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
    actual_value character varying(500),
    obj_owner character varying(100)
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
    updt_dt timestamp without time zone,
    dict_id character varying(22)
);


--
-- Name: TABLE tb_diag_schedule; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_diag_schedule IS '진단 스케줄 정의 (65번 문서)';


--
-- Name: COLUMN tb_diag_schedule.diag_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.diag_type IS 'STANDARD | STRUCT | BOTH | GOV_META';


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
-- Name: COLUMN tb_diag_schedule.dict_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule.dict_id IS '예약 진단이 사용할 표준사전. NULL 이면 모델 값';


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
    exec_duration_sec integer,
    gov_meta_send_id character varying(40)
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
-- Name: COLUMN tb_diag_schedule_log.gov_meta_send_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_diag_schedule_log.gov_meta_send_id IS '범정부 메타 생성 이력 (TB_GOV_META_SEND.SEND_ID, DIAG_TYPE=GOV_META, 104번 WP5-78)';


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
    use_yn character(1) DEFAULT 'Y'::bpchar,
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL,
    priv_yn character(1) DEFAULT 'N'::bpchar,
    encr_yn character(1) DEFAULT 'N'::bpchar
);


--
-- Name: COLUMN tb_domain.priv_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain.priv_yn IS '개인정보여부 (104번 WP5-16)';


--
-- Name: COLUMN tb_domain.encr_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain.encr_yn IS '암호화여부 (104번 WP5-16)';


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
    updt_user_id character varying(50),
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL
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
    updt_user_id character varying(50),
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL,
    parent_grp_id character varying(22),
    dflt_data_type character varying(50),
    clsf_word_nm character varying(100),
    sort_order integer DEFAULT 0
);


--
-- Name: COLUMN tb_domain_grp.parent_grp_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain_grp.parent_grp_id IS '상위 도메인 그룹 (같은 사전, NULL 이면 최상위, 3단계까지 — 104번 WP6-4)';


--
-- Name: COLUMN tb_domain_grp.dflt_data_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain_grp.dflt_data_type IS '그룹 기본 데이터 타입 — 도메인 등록 때 채움 (104번 WP6-4)';


--
-- Name: COLUMN tb_domain_grp.clsf_word_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain_grp.clsf_word_nm IS '분류어 (형식단어명, 104번 WP6-4)';


--
-- Name: COLUMN tb_domain_grp.sort_order; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_domain_grp.sort_order IS '같은 상위 안 순서 (104번 WP6-4)';


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
-- Name: tb_gov_brm; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_brm (
    brm_id character varying(40) NOT NULL,
    brm_nm character varying(300) NOT NULL,
    parent_brm_id character varying(40),
    brm_lvl integer,
    brm_path character varying(1000),
    inst_cd character varying(20),
    brm_src character varying(10) NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_brm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_brm IS '업무분류체계(BRM) — 정부기능분류체계 반입 또는 기관 자체 (104번 WP5-78)';


--
-- Name: COLUMN tb_gov_brm.brm_src; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_brm.brm_src IS 'GOV(정부기능분류체계 반입), INST(기관 자체)';


--
-- Name: tb_gov_meta_code_map; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_code_map (
    code_grp character varying(30) NOT NULL,
    local_val character varying(100) NOT NULL,
    gov_code character varying(50) NOT NULL,
    gov_code_nm character varying(200),
    sort_order integer DEFAULT 0 NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_meta_code_map; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_code_map IS '우리 값 → 범정부 코드 (104번 WP5-78). 그룹: DB_FORM, DBMS, DBMS_VER, OS, OS_VER, TBL_TP, OCCR_CYCLE, OPEN_TP';


--
-- Name: tb_gov_meta_conf; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_conf (
    conf_id character varying(20) NOT NULL,
    inst_nm character varying(200),
    inst_cd character varying(20),
    send_sys_cd character varying(50),
    link_mthd character varying(20),
    inst_meta_user_id character varying(100),
    sftp_host character varying(200),
    sftp_port integer,
    sftp_user_id character varying(100),
    sftp_pwd character varying(500),
    sftp_dir character varying(500),
    tmp_file_dir character varying(500),
    meta_file_dir character varying(500),
    file_keep_cnt integer,
    file_fmt character varying(20),
    file_encoding character varying(20),
    dflt_brm_id character varying(40),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_meta_conf; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_conf IS '범정부 메타 기관·연계 설정 (기관 1행, 104번 WP5-78)';


--
-- Name: COLUMN tb_gov_meta_conf.link_mthd; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_conf.link_mthd IS '연계 방식 — INST_META(기관메타 업로드), CENTRAL(중앙메타), SFTP';


--
-- Name: COLUMN tb_gov_meta_conf.sftp_pwd; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_conf.sftp_pwd IS 'SFTP 비밀번호 (암호화 저장, 조회 응답에 넣지 않음)';


--
-- Name: tb_gov_meta_db; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_db (
    ds_id character varying(50) NOT NULL,
    rep_dm_id character varying(22),
    db_nm_kr character varying(200),
    db_nm_en character varying(200),
    db_desc character varying(1000),
    db_form_cd character varying(20),
    brm_id character varying(40),
    dbms_nm character varying(100),
    dbms_ver character varying(100),
    os_nm character varying(100),
    os_ver character varying(100),
    dbms_info_clct_dt character varying(14),
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_meta_db; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_db IS '범정부 메타 DB 항목 (ndata.TB_DATA_SOURCE 단위, 104번 WP5-78)';


--
-- Name: COLUMN tb_gov_meta_db.rep_dm_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_db.rep_dm_id IS '대표 모델 — 한 DB 를 여러 모델이 쓸 때 테이블·컬럼을 가져올 모델 (104번 §3.3 결정)';


--
-- Name: COLUMN tb_gov_meta_db.dbms_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_db.dbms_nm IS 'DBMS 제품명 (수집 때 JDBC 로 읽음)';


--
-- Name: COLUMN tb_gov_meta_db.dbms_ver; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_db.dbms_ver IS 'DBMS 버전 (수집 때 JDBC 로 읽음)';


--
-- Name: tb_gov_meta_send; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_send (
    send_id character varying(40) NOT NULL,
    dm_id character varying(22),
    sys_cd character varying(22),
    ds_id character varying(50),
    trigger_type character varying(20) NOT NULL,
    schedule_log_id character varying(40),
    status character varying(20) NOT NULL,
    start_dt character varying(14),
    end_dt character varying(14),
    sys_cnt integer DEFAULT 0,
    db_cnt integer DEFAULT 0,
    tbl_cnt integer DEFAULT 0,
    col_cnt integer DEFAULT 0,
    err_cnt integer DEFAULT 0,
    warn_cnt integer DEFAULT 0,
    file_nm character varying(300),
    file_path character varying(1000),
    link_mthd character varying(20),
    send_dt character varying(14),
    send_status character varying(20),
    send_msg character varying(2000),
    cret_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_meta_send; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_send IS '범정부 메타 생성·전송 이력 (104번 WP5-78)';


--
-- Name: COLUMN tb_gov_meta_send.trigger_type; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_send.trigger_type IS 'MANUAL(화면), SCHEDULE(예약 실행)';


--
-- Name: COLUMN tb_gov_meta_send.status; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_send.status IS 'READY, RUNNING, DONE, ERROR';


--
-- Name: tb_gov_meta_send_err; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_send_err (
    send_id character varying(40) NOT NULL,
    err_seq integer NOT NULL,
    err_lvl character varying(10) NOT NULL,
    sev character varying(10) NOT NULL,
    sys_cd character varying(22),
    ds_id character varying(50),
    obj_owner character varying(100),
    obj_nm character varying(255),
    attr_nm character varying(255),
    item_nm character varying(100),
    err_msg character varying(1000) NOT NULL
);


--
-- Name: TABLE tb_gov_meta_send_err; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_send_err IS '범정부 메타 생성 전 검증 결과 (104번 WP5-78)';


--
-- Name: COLUMN tb_gov_meta_send_err.err_lvl; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_send_err.err_lvl IS 'SYS, DB, TBL, COL';


--
-- Name: COLUMN tb_gov_meta_send_err.sev; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_send_err.sev IS 'ERROR(생성 불가), WARN(규격 확인 전까지 경고)';


--
-- Name: tb_gov_meta_sys; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_gov_meta_sys (
    sys_cd character varying(22) NOT NULL,
    gov_sys_nm character varying(200),
    gov_sys_cd character varying(20),
    gov_sys_tp character varying(20),
    std_dstb_tp character varying(20),
    oper_dept_nm character varying(200),
    chrg_user_nm character varying(100),
    chrg_telno character varying(50),
    chrg_email character varying(200),
    rlt_law_cn character varying(1000),
    bld_ym character varying(6),
    last_upgrd_ym character varying(6),
    send_yn character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_gov_meta_sys; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_gov_meta_sys IS '범정부 메타 정보시스템 항목 (TB_SYS_INFO 시스템 노드, 104번 WP5-78)';


--
-- Name: COLUMN tb_gov_meta_sys.gov_sys_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_sys.gov_sys_nm IS '정보시스템명 — IRM 등록명과 같게';


--
-- Name: COLUMN tb_gov_meta_sys.gov_sys_cd; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_sys.gov_sys_cd IS '정보시스템코드 — 기관코드 7자리 + "_" + 일련번호 5자리';


--
-- Name: COLUMN tb_gov_meta_sys.send_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_gov_meta_sys.send_yn IS '전송대상';


--
-- Name: tb_notification; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_notification (
    noti_id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    noti_tp character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    msg character varying(1000),
    link_tp character varying(20),
    link_id character varying(50),
    read_yn character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt character varying(14) NOT NULL
);


--
-- Name: TABLE tb_notification; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_notification IS '사용자 알림 (104번 WP1)';


--
-- Name: COLUMN tb_notification.noti_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_notification.noti_tp IS 'APRV_REQ(결재 요청), APRV_STEP(단계 승인), APRV_DONE(최종 승인), APRV_REJECT(반려)';


--
-- Name: COLUMN tb_notification.link_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_notification.link_tp IS '누르면 열 대상 — APRV_DOC';


--
-- Name: tb_notification_noti_id_seq; Type: SEQUENCE; Schema: quality; Owner: -
--

CREATE SEQUENCE quality.tb_notification_noti_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tb_notification_noti_id_seq; Type: SEQUENCE OWNED BY; Schema: quality; Owner: -
--

ALTER SEQUENCE quality.tb_notification_noti_id_seq OWNED BY quality.tb_notification.noti_id;


--
-- Name: tb_org; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_org (
    org_id character varying(40) NOT NULL,
    org_nm character varying(100) NOT NULL,
    p_org_id character varying(40),
    head_user_id character varying(50),
    sort_ord integer DEFAULT 0 NOT NULL,
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_org; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_org IS '부서 (104번 WP1)';


--
-- Name: COLUMN tb_org.p_org_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_org.p_org_id IS '상위 부서 (NULL = 최상위)';


--
-- Name: COLUMN tb_org.head_user_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_org.head_user_id IS '부서장 (TB_USER.USER_ID) — 결재선의 "요청자 부서장"';


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
    updt_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    obj_owner character varying(100) DEFAULT ''::character varying NOT NULL
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
    updated_dt timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    obj_owner character varying(100)
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

COMMENT ON COLUMN quality.tb_qual_rule_catalog.is_built_in IS '83번 Step2 — 시스템 기본 룰(Y, 읽기 전용) vs 사용자 정의(N) 분리';


--
-- Name: COLUMN tb_qual_rule_catalog.domain_clsf_nm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_qual_rule_catalog.domain_clsf_nm IS '83번 Step2 — 행안부 도메인 분류명 (자동 추천 매칭 키)';


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
    error_msg text,
    obj_owner character varying(100)
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
    start_dt character varying(14) NOT NULL,
    obj_owner character varying(100)
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
    violating_val character varying(500),
    obj_owner character varying(100)
);


--
-- Name: TABLE tb_qual_violation_sample; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_qual_violation_sample IS '위반 샘플 행 (PK + 위반값, 룰당 기본 100건)';


--
-- Name: tb_role; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_role (
    role_id character varying(40) NOT NULL,
    role_nm character varying(100) NOT NULL,
    role_desc character varying(500),
    builtin_yn character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_role; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_role IS '역할 — 권한 코드(PermCode) 묶음 (104번 WP2)';


--
-- Name: COLUMN tb_role.builtin_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_role.builtin_yn IS '내장 역할 여부 (Y 는 삭제 불가)';


--
-- Name: tb_role_perm; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_role_perm (
    role_id character varying(40) NOT NULL,
    perm_cd character varying(50) NOT NULL
);


--
-- Name: TABLE tb_role_perm; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_role_perm IS '역할에 든 권한 코드 (104번 WP2)';


--
-- Name: COLUMN tb_role_perm.perm_cd; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_role_perm.perm_cd IS '권한 코드 — q-common PermCode 이름';


--
-- Name: tb_std_dict; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_std_dict (
    dict_id character varying(22) NOT NULL,
    dict_nm character varying(100) NOT NULL,
    dict_desc character varying(500),
    default_yn character(1) DEFAULT 'N'::bpchar,
    use_yn character(1) DEFAULT 'Y'::bpchar,
    term_last_word_clsf_yn character(1) DEFAULT 'N'::bpchar,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50),
    upper_yn character(1) DEFAULT 'Y'::bpchar,
    word_eng_nm_dup_yn character(1) DEFAULT 'Y'::bpchar,
    term_eng_max_len integer,
    old_std_yn character(1) DEFAULT 'N'::bpchar
);


--
-- Name: TABLE tb_std_dict; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_std_dict IS '표준사전 (단어·용어·도메인·코드의 이름공간)';


--
-- Name: COLUMN tb_std_dict.default_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.default_yn IS '신규 모델 등록 시 기본 선택되는 사전';


--
-- Name: COLUMN tb_std_dict.term_last_word_clsf_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.term_last_word_clsf_yn IS '용어의 마지막 단어가 형식단어여야 하는지. Y=필수, N=미적용';


--
-- Name: COLUMN tb_std_dict.upper_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.upper_yn IS '표준 대문자 — 단어·용어 영문약어 소문자 입력을 대문자로 바꿔 받음 (N 이면 소문자 거부, 104번 WP6-13)';


--
-- Name: COLUMN tb_std_dict.word_eng_nm_dup_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.word_eng_nm_dup_yn IS '단어 영문명 중복 허용 (104번 WP6-13)';


--
-- Name: COLUMN tb_std_dict.term_eng_max_len; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.term_eng_max_len IS '용어 영문약어명 최대 길이(Byte), NULL 이면 제한 없음 (104번 WP6-13)';


--
-- Name: COLUMN tb_std_dict.old_std_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_dict.old_std_yn IS '구표준 사용 — 옛 표준 → 새 표준 매핑 (104번 WP6-13·15)';


--
-- Name: tb_std_map; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_std_map (
    map_id character varying(40) NOT NULL,
    dict_id character varying(22) NOT NULL,
    map_tp character varying(10) NOT NULL,
    old_nm character varying(200) NOT NULL,
    old_eng_abrv_nm character varying(200),
    new_id character varying(22) NOT NULL,
    map_reason character varying(500),
    use_yn character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_std_map; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_std_map IS '옛 표준 → 새 표준 매핑 (AS-IS / TO-BE, 104번 WP6-15)';


--
-- Name: COLUMN tb_std_map.map_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_map.map_tp IS 'WORD(단어), TERM(용어)';


--
-- Name: COLUMN tb_std_map.new_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_std_map.new_id IS '새 표준 ID — TB_WORD.WORD_ID 또는 TB_TERMS.TERMS_ID';


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
-- Name: tb_sys_conf; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_sys_conf (
    conf_key character varying(50) NOT NULL,
    conf_val character varying(500),
    conf_desc character varying(500),
    updt_dt character varying(14),
    updt_user_id character varying(50)
);


--
-- Name: TABLE tb_sys_conf; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_sys_conf IS '설정 (104번 WP3~)';


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
    updt_user_id character varying(50),
    dict_id character varying(22)
);


--
-- Name: COLUMN tb_sys_info.dict_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_sys_info.dict_id IS '시스템 기본 표준사전 (TB_STD_DICT.DICT_ID). 이 시스템의 모델 등록 때 기본값 (104번 WP2)';


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
    use_yn character(1) DEFAULT 'Y'::bpchar,
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL,
    priv_yn character(1),
    encr_yn character(1),
    code_tp character varying(10),
    parent_code_grp character varying(50)
);


--
-- Name: COLUMN tb_terms.priv_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_terms.priv_yn IS '개인정보여부 — NULL 이면 도메인 값 (104번 WP5-16)';


--
-- Name: COLUMN tb_terms.encr_yn; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_terms.encr_yn IS '암호화여부 — NULL 이면 도메인 값 (104번 WP5-16)';


--
-- Name: COLUMN tb_terms.code_tp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_terms.code_tp IS '코드 유형 — SINGLE 단일·HIER 계층·DERIV 파생·INTEG 통합·INDIV 개별 (코드인데 NULL 이면 단일, 104번 WP6-3)';


--
-- Name: COLUMN tb_terms.parent_code_grp; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_terms.parent_code_grp IS '상위 코드 그룹 — 계층·파생 코드 (104번 WP6-3)';


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
    phone character varying(20),
    org_id character varying(40)
);


--
-- Name: COLUMN tb_user.org_id; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_user.org_id IS '부서 (TB_ORG.ORG_ID) (104번 WP1)';


--
-- Name: tb_user_role; Type: TABLE; Schema: quality; Owner: -
--

CREATE TABLE quality.tb_user_role (
    user_id character varying(50) NOT NULL,
    role_id character varying(40) NOT NULL,
    sys_cd character varying(22) DEFAULT '*'::character varying NOT NULL,
    cret_dt character varying(14),
    cret_user_id character varying(50)
);


--
-- Name: TABLE tb_user_role; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON TABLE quality.tb_user_role IS '사용자에게 준 역할 (104번 WP2)';


--
-- Name: COLUMN tb_user_role.sys_cd; Type: COMMENT; Schema: quality; Owner: -
--

COMMENT ON COLUMN quality.tb_user_role.sys_cd IS '권한 범위 시스템 (TB_SYS_INFO.SYS_CD). ''*'' = 모든 시스템. 시스템 단위 권한(DDL_EXEC)만 의미가 있다';


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
    use_yn character(1) DEFAULT 'Y'::bpchar,
    dict_id character varying(22) DEFAULT 'DEFAULT'::character varying NOT NULL
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
-- Name: tb_notification noti_id; Type: DEFAULT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_notification ALTER COLUMN noti_id SET DEFAULT nextval('quality.tb_notification_noti_id_seq'::regclass);


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
    ADD CONSTRAINT pk_tb_qual_col_rule PRIMARY KEY (dm_id, obj_owner, obj_nm, attr_nm);


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
-- Name: tb_aprv_doc tb_aprv_doc_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_doc
    ADD CONSTRAINT tb_aprv_doc_pk PRIMARY KEY (doc_id);


--
-- Name: tb_aprv_doc_step tb_aprv_doc_step_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_doc_step
    ADD CONSTRAINT tb_aprv_doc_step_pk PRIMARY KEY (doc_id, step_no, aprvr_user_id);


--
-- Name: tb_aprv_line tb_aprv_line_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_line
    ADD CONSTRAINT tb_aprv_line_pk PRIMARY KEY (req_tp);


--
-- Name: tb_aprv_line_step tb_aprv_line_step_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_line_step
    ADD CONSTRAINT tb_aprv_line_step_pk PRIMARY KEY (req_tp, step_no);


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
-- Name: tb_code_integ tb_code_integ_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_code_integ
    ADD CONSTRAINT tb_code_integ_pk PRIMARY KEY (dict_id, code_grp, sub_code_grp);


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
-- Name: tb_data_type_map tb_data_type_map_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_type_map
    ADD CONSTRAINT tb_data_type_map_pk PRIMARY KEY (data_type, dbms_tp);


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
-- Name: tb_gov_brm tb_gov_brm_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_brm
    ADD CONSTRAINT tb_gov_brm_pk PRIMARY KEY (brm_id);


--
-- Name: tb_gov_meta_code_map tb_gov_meta_code_map_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_code_map
    ADD CONSTRAINT tb_gov_meta_code_map_pk PRIMARY KEY (code_grp, local_val);


--
-- Name: tb_gov_meta_conf tb_gov_meta_conf_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_conf
    ADD CONSTRAINT tb_gov_meta_conf_pk PRIMARY KEY (conf_id);


--
-- Name: tb_gov_meta_db tb_gov_meta_db_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_db
    ADD CONSTRAINT tb_gov_meta_db_pk PRIMARY KEY (ds_id);


--
-- Name: tb_gov_meta_send_err tb_gov_meta_send_err_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_send_err
    ADD CONSTRAINT tb_gov_meta_send_err_pk PRIMARY KEY (send_id, err_seq);


--
-- Name: tb_gov_meta_send tb_gov_meta_send_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_send
    ADD CONSTRAINT tb_gov_meta_send_pk PRIMARY KEY (send_id);


--
-- Name: tb_gov_meta_sys tb_gov_meta_sys_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_sys
    ADD CONSTRAINT tb_gov_meta_sys_pk PRIMARY KEY (sys_cd);


--
-- Name: tb_notification tb_notification_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_notification
    ADD CONSTRAINT tb_notification_pk PRIMARY KEY (noti_id);


--
-- Name: tb_org tb_org_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_org
    ADD CONSTRAINT tb_org_pk PRIMARY KEY (org_id);


--
-- Name: tb_role tb_role_nm_uk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_role
    ADD CONSTRAINT tb_role_nm_uk UNIQUE (role_nm);


--
-- Name: tb_role_perm tb_role_perm_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_role_perm
    ADD CONSTRAINT tb_role_perm_pk PRIMARY KEY (role_id, perm_cd);


--
-- Name: tb_role tb_role_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_role
    ADD CONSTRAINT tb_role_pk PRIMARY KEY (role_id);


--
-- Name: tb_std_dict tb_std_dict_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_std_dict
    ADD CONSTRAINT tb_std_dict_pk PRIMARY KEY (dict_id);


--
-- Name: tb_std_map tb_std_map_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_std_map
    ADD CONSTRAINT tb_std_map_pk PRIMARY KEY (map_id);


--
-- Name: tb_subj_area tb_subj_area_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_subj_area
    ADD CONSTRAINT tb_subj_area_pkey PRIMARY KEY (subj_area_id);


--
-- Name: tb_sys_conf tb_sys_conf_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_sys_conf
    ADD CONSTRAINT tb_sys_conf_pk PRIMARY KEY (conf_key);


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
-- Name: tb_user_role tb_user_role_pk; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_user_role
    ADD CONSTRAINT tb_user_role_pk PRIMARY KEY (user_id, role_id, sys_cd);


--
-- Name: tb_word tb_word_pkey; Type: CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_word
    ADD CONSTRAINT tb_word_pkey PRIMARY KEY (word_id);


--
-- Name: domain_grp_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX domain_grp_ux_1 ON quality.tb_domain_grp USING btree (dict_id, domain_grp_nm);


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
-- Name: ix_aprv_doc_req_user; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_aprv_doc_req_user ON quality.tb_aprv_doc USING btree (req_user_id);


--
-- Name: ix_aprv_doc_step_user; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_aprv_doc_step_user ON quality.tb_aprv_doc_step USING btree (aprvr_user_id, act_stat);


--
-- Name: ix_aprv_doc_target; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_aprv_doc_target ON quality.tb_aprv_doc USING btree (req_tp, target_id);


--
-- Name: ix_diag_result_owner_obj; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_diag_result_owner_obj ON quality.tb_diag_result USING btree (diag_job_id, obj_owner, obj_nm, attr_nm);


--
-- Name: ix_diag_schedule_active; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_diag_schedule_active ON quality.tb_diag_schedule USING btree (use_yn, data_model_id);


--
-- Name: ix_diag_schedule_log_schedule; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_diag_schedule_log_schedule ON quality.tb_diag_schedule_log USING btree (schedule_id, exec_dt DESC);


--
-- Name: ix_domain_grp_parent; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_domain_grp_parent ON quality.tb_domain_grp USING btree (parent_grp_id);


--
-- Name: ix_domain_rule_dom; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_domain_rule_dom ON quality.tb_domain_rule USING btree (domain_id, sort_ord);


--
-- Name: ix_gov_brm_parent; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_gov_brm_parent ON quality.tb_gov_brm USING btree (parent_brm_id);


--
-- Name: ix_gov_meta_send_dt; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_gov_meta_send_dt ON quality.tb_gov_meta_send USING btree (start_dt);


--
-- Name: ix_notification_user; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_notification_user ON quality.tb_notification USING btree (user_id, read_yn);


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
-- Name: ix_std_map_old_eng; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_std_map_old_eng ON quality.tb_std_map USING btree (dict_id, map_tp, old_eng_abrv_nm);


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
-- Name: ix_user_role_role; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX ix_user_role_role ON quality.tb_user_role USING btree (role_id);


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

CREATE UNIQUE INDEX tb_code_data_ux_1 ON quality.tb_code_data USING btree (dict_id, code_nm, code_val);


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

CREATE UNIQUE INDEX tb_domain_clsf_ux_1 ON quality.tb_domain_clsf USING btree (dict_id, domain_clsf_nm);


--
-- Name: tb_domain_ix_dict; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_domain_ix_dict ON quality.tb_domain USING btree (dict_id);


--
-- Name: tb_domain_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_domain_ux_1 ON quality.tb_domain USING btree (dict_id, domain_nm);


--
-- Name: tb_event_log_time_idx; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_event_log_time_idx ON quality.tb_event_log USING btree (time_val DESC);


--
-- Name: tb_std_dict_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_std_dict_ux_1 ON quality.tb_std_dict USING btree (dict_nm);


--
-- Name: tb_terms_ix_dict; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_terms_ix_dict ON quality.tb_terms USING btree (dict_id);


--
-- Name: tb_terms_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_terms_ux_1 ON quality.tb_terms USING btree (dict_id, terms_nm);


--
-- Name: tb_terms_ux_2; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_terms_ux_2 ON quality.tb_terms USING btree (dict_id, terms_eng_abrv_nm);


--
-- Name: tb_word_ix_3; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_word_ix_3 ON quality.tb_word USING btree (word_eng_nm);


--
-- Name: tb_word_ix_dict; Type: INDEX; Schema: quality; Owner: -
--

CREATE INDEX tb_word_ix_dict ON quality.tb_word USING btree (dict_id);


--
-- Name: tb_word_ux_1; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_word_ux_1 ON quality.tb_word USING btree (word_id, word_nm);


--
-- Name: tb_word_ux_2; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_word_ux_2 ON quality.tb_word USING btree (dict_id, word_eng_abrv_nm);


--
-- Name: tb_word_ux_3; Type: INDEX; Schema: quality; Owner: -
--

CREATE UNIQUE INDEX tb_word_ux_3 ON quality.tb_word USING btree (dict_id, word_nm);


--
-- Name: tb_event_log ts_insert_blocker; Type: TRIGGER; Schema: quality; Owner: -
--

CREATE TRIGGER ts_insert_blocker BEFORE INSERT ON quality.tb_event_log FOR EACH ROW EXECUTE FUNCTION _timescaledb_internal.insert_blocker();


--
-- Name: tb_aprv_doc_step tb_aprv_doc_step_doc_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_doc_step
    ADD CONSTRAINT tb_aprv_doc_step_doc_fk FOREIGN KEY (doc_id) REFERENCES quality.tb_aprv_doc(doc_id) ON DELETE CASCADE;


--
-- Name: tb_aprv_line_step tb_aprv_line_step_line_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_aprv_line_step
    ADD CONSTRAINT tb_aprv_line_step_line_fk FOREIGN KEY (req_tp) REFERENCES quality.tb_aprv_line(req_tp) ON DELETE CASCADE;


--
-- Name: tb_data_model tb_data_model_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_data_model
    ADD CONSTRAINT tb_data_model_fk FOREIGN KEY (dm_sys_cd) REFERENCES quality.tb_sys_info(sys_cd);


--
-- Name: tb_domain_clsf tb_domain_clsf_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_fk_1 FOREIGN KEY (dict_id, domain_grp_nm) REFERENCES quality.tb_domain_grp(dict_id, domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_1; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_1 FOREIGN KEY (dict_id, domain_grp_nm) REFERENCES quality.tb_domain_grp(dict_id, domain_grp_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain tb_domain_fk_2; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_2 FOREIGN KEY (dict_id, domain_clsf_nm) REFERENCES quality.tb_domain_clsf(dict_id, domain_clsf_nm) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: tb_domain_grp tb_domain_grp_fk_parent; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_domain_grp
    ADD CONSTRAINT tb_domain_grp_fk_parent FOREIGN KEY (parent_grp_id) REFERENCES quality.tb_domain_grp(domain_grp_id);


--
-- Name: tb_gov_meta_send_err tb_gov_meta_send_err_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_gov_meta_send_err
    ADD CONSTRAINT tb_gov_meta_send_err_fk FOREIGN KEY (send_id) REFERENCES quality.tb_gov_meta_send(send_id) ON DELETE CASCADE;


--
-- Name: tb_org tb_org_parent_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_org
    ADD CONSTRAINT tb_org_parent_fk FOREIGN KEY (p_org_id) REFERENCES quality.tb_org(org_id);


--
-- Name: tb_role_perm tb_role_perm_role_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_role_perm
    ADD CONSTRAINT tb_role_perm_role_fk FOREIGN KEY (role_id) REFERENCES quality.tb_role(role_id) ON DELETE CASCADE;


--
-- Name: tb_terms tb_terms_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_terms
    ADD CONSTRAINT tb_terms_fk FOREIGN KEY (dict_id, domain_nm) REFERENCES quality.tb_domain(dict_id, domain_nm) ON UPDATE CASCADE;


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
-- Name: tb_user_role tb_user_role_role_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_user_role
    ADD CONSTRAINT tb_user_role_role_fk FOREIGN KEY (role_id) REFERENCES quality.tb_role(role_id) ON DELETE CASCADE;


--
-- Name: tb_user_role tb_user_role_user_fk; Type: FK CONSTRAINT; Schema: quality; Owner: -
--

ALTER TABLE ONLY quality.tb_user_role
    ADD CONSTRAINT tb_user_role_user_fk FOREIGN KEY (user_id) REFERENCES quality.tb_user(user_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

