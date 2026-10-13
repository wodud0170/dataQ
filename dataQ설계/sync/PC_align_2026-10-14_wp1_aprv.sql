-- 104번 WP1 — 결재선·조직·알림 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp1_aprv.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. TB_ORG 신규 + TB_USER.ORG_ID — 부서 트리·부서장, 사용자의 부서
--   2. TB_APRV_LINE / TB_APRV_LINE_STEP 신규 — 요청 유형별 결재선과 단계
--   3. TB_APRV_DOC / TB_APRV_DOC_STEP 신규 — 결재 문서와 단계별 결재자
--   4. TB_NOTIFICATION 신규 — 사용자별 알림
-- 결재선 시드는 없다. 결재선이 없는 요청 유형은 지금처럼(표준사전 STD_APPROVE, 데이터 모델 DM_APPROVE) 동작한다.

SET search_path TO quality;

BEGIN;

CREATE TABLE IF NOT EXISTS quality.tb_org (
    org_id       character varying(40)  NOT NULL,
    org_nm       character varying(100) NOT NULL,
    p_org_id     character varying(40),
    head_user_id character varying(50),
    sort_ord     integer DEFAULT 0 NOT NULL,
    use_yn       character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt      character varying(14),
    cret_user_id character varying(50),
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_org_pk PRIMARY KEY (org_id),
    CONSTRAINT tb_org_parent_fk FOREIGN KEY (p_org_id) REFERENCES quality.tb_org (org_id)
);
COMMENT ON TABLE  quality.tb_org IS '부서 (104번 WP1)';
COMMENT ON COLUMN quality.tb_org.p_org_id IS '상위 부서 (NULL = 최상위)';
COMMENT ON COLUMN quality.tb_org.head_user_id IS '부서장 (TB_USER.USER_ID) — 결재선의 "요청자 부서장"';

ALTER TABLE quality.tb_user ADD COLUMN IF NOT EXISTS org_id character varying(40);
COMMENT ON COLUMN quality.tb_user.org_id IS '부서 (TB_ORG.ORG_ID) (104번 WP1)';

CREATE TABLE IF NOT EXISTS quality.tb_aprv_line (
    req_tp       character varying(20)  NOT NULL,
    line_nm      character varying(100),
    use_yn       character(1) DEFAULT 'Y'::bpchar NOT NULL,
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_aprv_line_pk PRIMARY KEY (req_tp)
);
COMMENT ON TABLE  quality.tb_aprv_line IS '요청 유형별 결재선 (104번 WP1). 행이 없거나 USE_YN=N 이면 결재선 없이 지금처럼 승인';
COMMENT ON COLUMN quality.tb_aprv_line.req_tp IS '요청 유형 — WORD, TERMS, CODE, DOMAIN, DM_CHANGE';

CREATE TABLE IF NOT EXISTS quality.tb_aprv_line_step (
    req_tp    character varying(20)  NOT NULL,
    step_no   integer                NOT NULL,
    step_nm   character varying(100),
    aprvr_tp  character varying(20)  NOT NULL,
    aprvr_id  character varying(50),
    agree_yn  character(1) DEFAULT 'N'::bpchar NOT NULL,
    CONSTRAINT tb_aprv_line_step_pk PRIMARY KEY (req_tp, step_no),
    CONSTRAINT tb_aprv_line_step_line_fk FOREIGN KEY (req_tp) REFERENCES quality.tb_aprv_line (req_tp) ON DELETE CASCADE
);
COMMENT ON TABLE  quality.tb_aprv_line_step IS '결재선 단계 (104번 WP1)';
COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_tp IS '결재자 방식 — USER(지정 사용자), ROLE(역할의 사용자), ADMIN(관리자 전원), ORG_HEAD(요청자 부서장)';
COMMENT ON COLUMN quality.tb_aprv_line_step.aprvr_id IS 'USER 면 사용자 ID, ROLE 이면 역할 ID, ADMIN·ORG_HEAD 면 NULL';
COMMENT ON COLUMN quality.tb_aprv_line_step.agree_yn IS '합의 — Y 면 단계 결재자 전원 승인, N 이면 한 명 승인으로 다음 단계';

CREATE TABLE IF NOT EXISTS quality.tb_aprv_doc (
    doc_id       character varying(40)  NOT NULL,
    req_tp       character varying(20)  NOT NULL,
    target_id    character varying(50)  NOT NULL,
    target_nm    character varying(300),
    dm_id        character varying(22),
    dict_id      character varying(22),
    req_user_id  character varying(50)  NOT NULL,
    req_dt       character varying(14)  NOT NULL,
    doc_stat     character varying(20)  NOT NULL,
    cur_step_no  integer                NOT NULL,
    step_cnt     integer                NOT NULL,
    end_dt       character varying(14),
    CONSTRAINT tb_aprv_doc_pk PRIMARY KEY (doc_id)
);
COMMENT ON TABLE  quality.tb_aprv_doc IS '결재 문서 (104번 WP1) — 표준사전 항목 하나 또는 데이터 모델 신청(SUBMISSION_ID) 하나';
COMMENT ON COLUMN quality.tb_aprv_doc.target_id IS 'WORD·TERMS·CODE·DOMAIN 은 항목 ID, DM_CHANGE 는 SUBMISSION_ID';
COMMENT ON COLUMN quality.tb_aprv_doc.doc_stat IS 'PROGRESS, APPROVED, REJECTED, CANCELED(대상이 다른 경로로 없어짐)';
CREATE INDEX IF NOT EXISTS ix_aprv_doc_target ON quality.tb_aprv_doc (req_tp, target_id);
CREATE INDEX IF NOT EXISTS ix_aprv_doc_req_user ON quality.tb_aprv_doc (req_user_id);

CREATE TABLE IF NOT EXISTS quality.tb_aprv_doc_step (
    doc_id        character varying(40)  NOT NULL,
    step_no       integer                NOT NULL,
    aprvr_user_id character varying(50)  NOT NULL,
    step_nm       character varying(100),
    agree_yn      character(1) DEFAULT 'N'::bpchar NOT NULL,
    act_stat      character varying(20) DEFAULT 'WAIT'::character varying NOT NULL,
    act_cmnt      character varying(500),
    act_dt        character varying(14),
    act_user_id   character varying(50),
    CONSTRAINT tb_aprv_doc_step_pk PRIMARY KEY (doc_id, step_no, aprvr_user_id),
    CONSTRAINT tb_aprv_doc_step_doc_fk FOREIGN KEY (doc_id) REFERENCES quality.tb_aprv_doc (doc_id) ON DELETE CASCADE
);
COMMENT ON TABLE  quality.tb_aprv_doc_step IS '결재 문서의 단계별 결재자 (104번 WP1). 문서를 만들 때 결재자를 정해 둔다';
COMMENT ON COLUMN quality.tb_aprv_doc_step.act_stat IS 'WAIT, APPROVED, REJECTED';
COMMENT ON COLUMN quality.tb_aprv_doc_step.act_user_id IS '실제 처리자 — 관리자가 대신 처리하면 관리자 ID';
CREATE INDEX IF NOT EXISTS ix_aprv_doc_step_user ON quality.tb_aprv_doc_step (aprvr_user_id, act_stat);

CREATE TABLE IF NOT EXISTS quality.tb_notification (
    noti_id  bigserial NOT NULL,
    user_id  character varying(50)  NOT NULL,
    noti_tp  character varying(20)  NOT NULL,
    title    character varying(200) NOT NULL,
    msg      character varying(1000),
    link_tp  character varying(20),
    link_id  character varying(50),
    read_yn  character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt  character varying(14)  NOT NULL,
    CONSTRAINT tb_notification_pk PRIMARY KEY (noti_id)
);
COMMENT ON TABLE  quality.tb_notification IS '사용자 알림 (104번 WP1)';

-- 결재자 수정 후 승인의 변경 이력 출처값 추가 (컬럼 주석만 — 값 제약은 없다)
COMMENT ON COLUMN quality.tb_change_history.change_source IS 'ADMIN_DIRECT / USER_REQUEST / ADMIN_APPROVE / ADMIN_REJECT / BULK_UPLOAD / AUTO_RECOMMEND / APPROVER_EDIT(결재자 수정, 104번 WP1)';
COMMENT ON COLUMN quality.tb_notification.noti_tp IS 'APRV_REQ(결재 요청), APRV_STEP(단계 승인), APRV_DONE(최종 승인), APRV_REJECT(반려)';
COMMENT ON COLUMN quality.tb_notification.link_tp IS '누르면 열 대상 — APRV_DOC';
CREATE INDEX IF NOT EXISTS ix_notification_user ON quality.tb_notification (user_id, read_yn);

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_notification;
-- DROP TABLE IF EXISTS quality.tb_aprv_doc_step;
-- DROP TABLE IF EXISTS quality.tb_aprv_doc;
-- DROP TABLE IF EXISTS quality.tb_aprv_line_step;
-- DROP TABLE IF EXISTS quality.tb_aprv_line;
-- ALTER TABLE quality.tb_user DROP COLUMN IF EXISTS org_id;
-- DROP TABLE IF EXISTS quality.tb_org;
-- COMMIT;
