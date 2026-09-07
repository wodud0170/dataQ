-- 98번 표준사전 세트 — 1단계 스키마 + 마이그레이션
--
-- 한 인스턴스에서 표준사전을 여러 벌 굴리기 위한 스키마 변경.
-- 기존 데이터는 전부 DEFAULT 사전에 담긴다. 행이 지워지거나 바뀌지 않는다.
--
-- 적용:
--   psql -h localhost -p 25433 -U admin -d postgres -f dataQ설계/sync/std_dict_2026-09-03.sql
--
-- 되돌리기: 파일 맨 아래 롤백 블록 참조 (컬럼만 드롭하면 원상 복구)
--
-- 멱등: 여러 번 돌려도 안전하다.

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. 표준사전 테이블
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS quality.tb_std_dict (
    dict_id      character varying(22)  NOT NULL,
    dict_nm      character varying(100) NOT NULL,
    dict_desc    character varying(500),
    default_yn   character(1) DEFAULT 'N'::bpchar,   -- 신규 모델에 기본 선택될 사전
    use_yn       character(1) DEFAULT 'Y'::bpchar,
    -- 사전 속성 (표준화 관례는 사전마다 다르다)
    term_last_word_clsf_yn character(1) DEFAULT 'N'::bpchar,  -- 용어 마지막 단어가 형식단어여야 하는가
    cret_dt      character varying(14),
    cret_user_id character varying(50),
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_std_dict_pk PRIMARY KEY (dict_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS tb_std_dict_ux_1
    ON quality.tb_std_dict USING btree (dict_nm);

COMMENT ON TABLE  quality.tb_std_dict IS '표준사전 (단어·용어·도메인·코드의 이름공간)';
COMMENT ON COLUMN quality.tb_std_dict.default_yn IS '신규 모델 등록 시 기본 선택되는 사전';
COMMENT ON COLUMN quality.tb_std_dict.term_last_word_clsf_yn IS '용어의 마지막 단어가 형식단어여야 하는지. Y=필수, N=미적용';

-- 기존 사전. 지금 동작을 그대로 유지해야 하므로 형식단어 규칙은 켠 상태(Y).
INSERT INTO quality.tb_std_dict
    (dict_id, dict_nm, dict_desc, default_yn, use_yn, term_last_word_clsf_yn,
     cret_dt, cret_user_id, updt_dt, updt_user_id)
VALUES
    ('DEFAULT', '기본 표준사전', '세트 도입(2026-09-03) 이전부터 있던 사전', 'Y', 'Y', 'Y',
     to_char(now(), 'YYYYMMDDHH24MISS'), 'SYSTEM',
     to_char(now(), 'YYYYMMDDHH24MISS'), 'SYSTEM')
ON CONFLICT (dict_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 2. 사전 계열 6개 테이블에 dict_id
--    추가 → 기존 행 DEFAULT 로 채움 → NOT NULL 승격
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tb_word', 'tb_terms', 'tb_domain',
                             'tb_domain_grp', 'tb_domain_clsf', 'tb_code_data']
    LOOP
        EXECUTE format(
            'ALTER TABLE quality.%I ADD COLUMN IF NOT EXISTS dict_id character varying(22)', t);
        EXECUTE format(
            'UPDATE quality.%I SET dict_id = ''DEFAULT'' WHERE dict_id IS NULL', t);
        EXECUTE format(
            'ALTER TABLE quality.%I ALTER COLUMN dict_id SET NOT NULL', t);
        EXECUTE format(
            'ALTER TABLE quality.%I ALTER COLUMN dict_id SET DEFAULT ''DEFAULT''', t);
    END LOOP;
END $$;

-- tb_terms_words 는 추가하지 않는다. terms_id 로 사전이 이미 결정된다.

-- ─────────────────────────────────────────────────────────────
-- 3. 모델 · 진단 · 스케줄에 dict_id
-- ─────────────────────────────────────────────────────────────

-- 모델: 등록 시 선택, 수정에서 변경 가능. NULL 허용(사전 없는 모델은 스케줄만 차단)
ALTER TABLE quality.tb_data_model      ADD COLUMN IF NOT EXISTS dict_id character varying(22);
UPDATE quality.tb_data_model      SET dict_id = 'DEFAULT' WHERE dict_id IS NULL;

-- 진단 잡: 이 진단이 어느 사전으로 쟀는지. 나중에 수치가 튄 이유를 답할 유일한 기록.
ALTER TABLE quality.tb_diag_job        ADD COLUMN IF NOT EXISTS dict_id character varying(22);
UPDATE quality.tb_diag_job        SET dict_id = 'DEFAULT' WHERE dict_id IS NULL;

-- 스케줄: NULL 이면 실행 시점에 모델의 dict_id 를 쓴다.
ALTER TABLE quality.tb_diag_schedule   ADD COLUMN IF NOT EXISTS dict_id character varying(22);
UPDATE quality.tb_diag_schedule   SET dict_id = 'DEFAULT' WHERE dict_id IS NULL;

COMMENT ON COLUMN quality.tb_data_model.dict_id    IS '이 모델에 적용할 표준사전. NULL 이면 스케줄 진단 불가';
COMMENT ON COLUMN quality.tb_diag_job.dict_id      IS '이 진단이 사용한 표준사전';
COMMENT ON COLUMN quality.tb_diag_schedule.dict_id IS '예약 진단이 사용할 표준사전. NULL 이면 모델 값';

-- ─────────────────────────────────────────────────────────────
-- 4. 유니크 · FK 재정의 — 전역 → (dict_id, …) 복합
--    같은 컬럼에 두 벌씩 걸려 있던 중복 인덱스도 여기서 한 벌로 정리한다.
--
--    사전 계열 FK 4개가 ID 가 아니라 '이름' 으로 참조하고 있다.
--      tb_domain      → tb_domain_grp   (domain_grp_nm)
--      tb_domain      → tb_domain_clsf  (domain_clsf_nm)
--      tb_domain_clsf → tb_domain_grp   (domain_grp_nm)
--      tb_terms       → tb_domain       (domain_nm)
--    이름 유니크를 사전별로 바꾸면 이 FK 들도 (dict_id, 이름) 복합이 되어야 한다.
--    그래야 RAMS 용어가 DEFAULT 도메인을 참조하는 사전 간 누수를 DB 가 막아준다.
-- ─────────────────────────────────────────────────────────────

-- 4-1. 이름 참조 FK 해제 (유니크 인덱스를 붙잡고 있다)
ALTER TABLE quality.tb_domain      DROP CONSTRAINT IF EXISTS tb_domain_fk_1;
ALTER TABLE quality.tb_domain      DROP CONSTRAINT IF EXISTS tb_domain_fk_2;
ALTER TABLE quality.tb_domain_clsf DROP CONSTRAINT IF EXISTS tb_domain_clsf_fk_1;
ALTER TABLE quality.tb_terms       DROP CONSTRAINT IF EXISTS tb_terms_fk;

-- 4-2. 유니크 재정의
-- tb_word_ux_1 (word_id, word_nm) 은 건드리지 않는다.
--   tb_terms_words_fk_2 (tb_terms_words → tb_word) 의 참조 대상이라 DROP 이 거부된다.
--   word_id 가 전역 UUID 라 사전을 섞어도 충돌하지 않으므로 그대로 둬도 무방하다.
DROP INDEX IF EXISTS quality.tb_word_ux_2;          -- word_eng_abrv_nm
DROP INDEX IF EXISTS quality.uix_word_eng_abrv_nm;  -- 위와 중복
DROP INDEX IF EXISTS quality.uix_word_nm;           -- word_nm
DROP INDEX IF EXISTS quality.tb_terms_ux_1;         -- terms_nm
DROP INDEX IF EXISTS quality.uix_terms_nm;          -- 위와 중복
DROP INDEX IF EXISTS quality.tb_terms_ux_2;         -- terms_eng_abrv_nm
DROP INDEX IF EXISTS quality.tb_domain_ux_1;        -- domain_nm
DROP INDEX IF EXISTS quality.uix_domain_nm;         -- 위와 중복
DROP INDEX IF EXISTS quality.domain_grp_ux_1;       -- domain_grp_nm
DROP INDEX IF EXISTS quality.tb_domain_clsf_ux_1;   -- domain_clsf_nm
DROP INDEX IF EXISTS quality.tb_code_data_ux_1;     -- (code_nm, code_val)

CREATE UNIQUE INDEX tb_word_ux_3        ON quality.tb_word        USING btree (dict_id, word_nm);
CREATE UNIQUE INDEX tb_word_ux_2        ON quality.tb_word        USING btree (dict_id, word_eng_abrv_nm);
CREATE UNIQUE INDEX tb_terms_ux_1       ON quality.tb_terms       USING btree (dict_id, terms_nm);
CREATE UNIQUE INDEX tb_terms_ux_2       ON quality.tb_terms       USING btree (dict_id, terms_eng_abrv_nm);
CREATE UNIQUE INDEX tb_domain_ux_1      ON quality.tb_domain      USING btree (dict_id, domain_nm);
CREATE UNIQUE INDEX domain_grp_ux_1     ON quality.tb_domain_grp  USING btree (dict_id, domain_grp_nm);
CREATE UNIQUE INDEX tb_domain_clsf_ux_1 ON quality.tb_domain_clsf USING btree (dict_id, domain_clsf_nm);
CREATE UNIQUE INDEX tb_code_data_ux_1   ON quality.tb_code_data   USING btree (dict_id, code_nm, code_val);

-- 4-3. FK 재생성 — (dict_id, 이름) 복합. 사전 간 참조를 DB 가 막는다.
ALTER TABLE quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_1 FOREIGN KEY (dict_id, domain_grp_nm)
    REFERENCES quality.tb_domain_grp (dict_id, domain_grp_nm)
    ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE quality.tb_domain
    ADD CONSTRAINT tb_domain_fk_2 FOREIGN KEY (dict_id, domain_clsf_nm)
    REFERENCES quality.tb_domain_clsf (dict_id, domain_clsf_nm)
    ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE quality.tb_domain_clsf
    ADD CONSTRAINT tb_domain_clsf_fk_1 FOREIGN KEY (dict_id, domain_grp_nm)
    REFERENCES quality.tb_domain_grp (dict_id, domain_grp_nm)
    ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE quality.tb_terms
    ADD CONSTRAINT tb_terms_fk FOREIGN KEY (dict_id, domain_nm)
    REFERENCES quality.tb_domain (dict_id, domain_nm)
    ON UPDATE CASCADE;

-- 조회 보조 인덱스 (사전 필터가 거의 모든 목록 쿼리에 붙는다)
CREATE INDEX IF NOT EXISTS tb_word_ix_dict   ON quality.tb_word   USING btree (dict_id);
CREATE INDEX IF NOT EXISTS tb_terms_ix_dict  ON quality.tb_terms  USING btree (dict_id);
CREATE INDEX IF NOT EXISTS tb_domain_ix_dict ON quality.tb_domain USING btree (dict_id);

COMMIT;

-- ─────────────────────────────────────────────────────────────
-- 롤백 (필요 시)
-- ─────────────────────────────────────────────────────────────
-- BEGIN;
-- DROP INDEX IF EXISTS quality.tb_word_ux_3, quality.tb_word_ux_2,
--   quality.tb_terms_ux_1, quality.tb_terms_ux_2, quality.tb_domain_ux_1,
--   quality.domain_grp_ux_1, quality.tb_domain_clsf_ux_1, quality.tb_code_data_ux_1,
--   quality.tb_word_ix_dict, quality.tb_terms_ix_dict, quality.tb_domain_ix_dict;
-- ALTER TABLE quality.tb_word          DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_terms         DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_domain        DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_domain_grp    DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_domain_clsf   DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_code_data     DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_data_model    DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_diag_job      DROP COLUMN IF EXISTS dict_id;
-- ALTER TABLE quality.tb_diag_schedule DROP COLUMN IF EXISTS dict_id;
-- DROP TABLE IF EXISTS quality.tb_std_dict;
-- CREATE UNIQUE INDEX uix_word_nm ON quality.tb_word (word_nm);
-- CREATE UNIQUE INDEX uix_word_eng_abrv_nm ON quality.tb_word (word_eng_abrv_nm);
-- CREATE UNIQUE INDEX uix_terms_nm ON quality.tb_terms (terms_nm);
-- CREATE UNIQUE INDEX uix_domain_nm ON quality.tb_domain (domain_nm);
-- COMMIT;
