-- 88번 §16 — 한글 변환 이력 (매핑 정의서 작성용)
-- 작성일: 2026-05-15
-- 적용:
--   docker exec -i dataq-db psql -U admin -d postgres < PC_align_2026-05-15_term_resolve.sql

SET search_path TO quality, public;

CREATE TABLE IF NOT EXISTS tb_term_resolve_history (
  history_seq        BIGSERIAL PRIMARY KEY,
  dm_id              VARCHAR(40),
  obj_owner          VARCHAR(100),
  obj_nm             VARCHAR(255),
  attr_nm            VARCHAR(255),
  input_kr_nm        VARCHAR(500) NOT NULL,      -- A: 사용자가 원래 입력한 한글
  resolved_kr_nm     VARCHAR(500),                -- B: 표준 매핑된 한글
  resolved_en_nm     VARCHAR(255),                -- B: 표준 영문약어
  resolved_terms_id  VARCHAR(40),                 -- 매칭 TB_TERMS.TERMS_ID
  resolved_data_type VARCHAR(50),
  resolved_data_len  BIGINT,
  resolve_reason     VARCHAR(500),                -- 변환 사유 / 실패 사유
  change_user_id     VARCHAR(50),                 -- 변환 수행한 사용자
  change_dt          VARCHAR(14)
);
CREATE INDEX IF NOT EXISTS ix_term_resolve_user  ON tb_term_resolve_history(change_user_id);
CREATE INDEX IF NOT EXISTS ix_term_resolve_dt    ON tb_term_resolve_history(change_dt DESC);
CREATE INDEX IF NOT EXISTS ix_term_resolve_input ON tb_term_resolve_history(input_kr_nm);
