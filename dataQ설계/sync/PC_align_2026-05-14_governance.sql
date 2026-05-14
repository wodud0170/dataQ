-- 88번 거버넌스 워크플로우 — 1단계: DDL 마이그레이션
-- 작성일: 2026-05-14
-- 적용 절차:
--   docker exec -i dataq-db psql -U admin -d postgres < PC_align_2026-05-14_governance.sql
-- 멱등: 모두 IF NOT EXISTS / DO $$ 패턴

SET search_path TO quality, public;

-- =========================================================================
-- 1. 거버넌스 공통 컬럼 — 5개 모델 테이블 + 신규 분류 2 + 변경이력
-- =========================================================================

-- tb_data_model
ALTER TABLE tb_data_model
  ADD COLUMN IF NOT EXISTS aprv_status        VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requester_user_id  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS req_dt             VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_user_id       VARCHAR(50),
  ADD COLUMN IF NOT EXISTS aprv_dt            VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_comment       VARCHAR(500),
  ADD COLUMN IF NOT EXISTS submission_id      VARCHAR(40);

-- tb_data_model_obj — 거버넌스 + 분류 + 테이블스페이스
ALTER TABLE tb_data_model_obj
  ADD COLUMN IF NOT EXISTS aprv_status        VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requester_user_id  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS req_dt             VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_user_id       VARCHAR(50),
  ADD COLUMN IF NOT EXISTS aprv_dt            VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_comment       VARCHAR(500),
  ADD COLUMN IF NOT EXISTS submission_id      VARCHAR(40),
  ADD COLUMN IF NOT EXISTS tablespace_nm      VARCHAR(100),
  ADD COLUMN IF NOT EXISTS biz_area_id        VARCHAR(40),
  ADD COLUMN IF NOT EXISTS subj_area_id       VARCHAR(40);

-- tb_data_model_attr
ALTER TABLE tb_data_model_attr
  ADD COLUMN IF NOT EXISTS aprv_status        VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requester_user_id  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS req_dt             VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_user_id       VARCHAR(50),
  ADD COLUMN IF NOT EXISTS aprv_dt            VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_comment       VARCHAR(500),
  ADD COLUMN IF NOT EXISTS submission_id      VARCHAR(40);

-- tb_data_model_index
ALTER TABLE tb_data_model_index
  ADD COLUMN IF NOT EXISTS aprv_status        VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requester_user_id  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS req_dt             VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_user_id       VARCHAR(50),
  ADD COLUMN IF NOT EXISTS aprv_dt            VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_comment       VARCHAR(500),
  ADD COLUMN IF NOT EXISTS submission_id      VARCHAR(40);

-- tb_data_model_constraint
ALTER TABLE tb_data_model_constraint
  ADD COLUMN IF NOT EXISTS aprv_status        VARCHAR(20) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requester_user_id  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS req_dt             VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_user_id       VARCHAR(50),
  ADD COLUMN IF NOT EXISTS aprv_dt            VARCHAR(14),
  ADD COLUMN IF NOT EXISTS aprv_comment       VARCHAR(500),
  ADD COLUMN IF NOT EXISTS submission_id      VARCHAR(40);

-- =========================================================================
-- 2. 업무영역 (논리) — TB_BIZ_AREA
-- =========================================================================
CREATE TABLE IF NOT EXISTS tb_biz_area (
  biz_area_id        VARCHAR(40)  PRIMARY KEY,
  biz_area_nm        VARCHAR(200) NOT NULL,
  biz_area_desc      VARCHAR(500),
  parent_id          VARCHAR(40),
  sort_order         INTEGER      DEFAULT 0,
  use_yn             CHAR(1)      DEFAULT 'Y',
  cret_user_id       VARCHAR(50),
  cret_dt            VARCHAR(14),
  updt_user_id       VARCHAR(50),
  updt_dt            VARCHAR(14),
  aprv_status        VARCHAR(20)  DEFAULT 'APPROVED',
  requester_user_id  VARCHAR(50),
  req_dt             VARCHAR(14),
  aprv_user_id       VARCHAR(50),
  aprv_dt            VARCHAR(14),
  aprv_comment       VARCHAR(500),
  submission_id      VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS ix_tb_biz_area_parent ON tb_biz_area(parent_id);

-- =========================================================================
-- 3. 주제영역 (물리) — TB_SUBJ_AREA
-- =========================================================================
CREATE TABLE IF NOT EXISTS tb_subj_area (
  subj_area_id       VARCHAR(40)  PRIMARY KEY,
  subj_area_nm       VARCHAR(200) NOT NULL,
  subj_area_desc     VARCHAR(500),
  parent_id          VARCHAR(40),
  sort_order         INTEGER      DEFAULT 0,
  use_yn             CHAR(1)      DEFAULT 'Y',
  cret_user_id       VARCHAR(50),
  cret_dt            VARCHAR(14),
  updt_user_id       VARCHAR(50),
  updt_dt            VARCHAR(14),
  aprv_status        VARCHAR(20)  DEFAULT 'APPROVED',
  requester_user_id  VARCHAR(50),
  req_dt             VARCHAR(14),
  aprv_user_id       VARCHAR(50),
  aprv_dt            VARCHAR(14),
  aprv_comment       VARCHAR(500),
  submission_id      VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS ix_tb_subj_area_parent ON tb_subj_area(parent_id);

-- =========================================================================
-- 4. 변경 이력 테이블
-- =========================================================================
CREATE TABLE IF NOT EXISTS tb_data_model_change_history (
  change_seq          BIGSERIAL PRIMARY KEY,
  dm_id               VARCHAR(40)  NOT NULL,
  change_dt           VARCHAR(14)  NOT NULL,
  change_user_id      VARCHAR(50)  NOT NULL,
  change_type         VARCHAR(30)  NOT NULL,
  change_tier         VARCHAR(10)  NOT NULL,
  submission_id       VARCHAR(40),
  obj_owner           VARCHAR(100),
  obj_nm              VARCHAR(255),
  attr_nm             VARCHAR(255),
  constraint_nm       VARCHAR(200),
  index_nm            VARCHAR(200),
  before_json         TEXT,
  after_json          TEXT,
  ddl_snippet         TEXT,
  aprv_status         VARCHAR(20),
  aprv_user_id        VARCHAR(50),
  aprv_dt             VARCHAR(14),
  aprv_comment        VARCHAR(500),
  ddl_exec_dt         VARCHAR(14),
  ddl_exec_user_id    VARCHAR(50),
  ddl_exec_result     VARCHAR(20),
  ddl_exec_message    VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS ix_tb_dmch_dm        ON tb_data_model_change_history(dm_id, change_dt DESC);
CREATE INDEX IF NOT EXISTS ix_tb_dmch_user      ON tb_data_model_change_history(change_user_id);
CREATE INDEX IF NOT EXISTS ix_tb_dmch_status    ON tb_data_model_change_history(aprv_status);
CREATE INDEX IF NOT EXISTS ix_tb_dmch_submission ON tb_data_model_change_history(submission_id);

-- =========================================================================
-- 5. 기존 데이터 마이그레이션 — APRV_STATUS NULL → 'APPROVED'
-- =========================================================================
UPDATE tb_data_model            SET aprv_status = 'APPROVED' WHERE aprv_status IS NULL;
UPDATE tb_data_model_obj        SET aprv_status = 'APPROVED' WHERE aprv_status IS NULL;
UPDATE tb_data_model_attr       SET aprv_status = 'APPROVED' WHERE aprv_status IS NULL;
UPDATE tb_data_model_index      SET aprv_status = 'APPROVED' WHERE aprv_status IS NULL;
UPDATE tb_data_model_constraint SET aprv_status = 'APPROVED' WHERE aprv_status IS NULL;

-- =========================================================================
-- 6. APPROVED 한정 partial unique index (PENDING/DRAFT/REJECTED 끼리는 중복 허용)
--    원래 PK 는 그대로 유지 (전체 row 대상)
-- =========================================================================
-- NOTE: 기존 PK 가 이미 unique 제약 보장 중. partial unique 는 §12 정책 (PENDING 끼리 중복 허용) 위해 별도 추가.
-- 다만 기존 PK 가 그대로면 PENDING 끼리도 충돌. PK 를 partial 로 바꿀 수 없으므로
-- aprv_status 를 PK 의 일부로 포함하는 대안도 있으나, 본 단계에선 PK 그대로 두고 application 레벨에서 처리.
-- 차후 PK 자체 재설계 검토 (별도 차후 과제).

COMMIT;
