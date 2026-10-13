-- 104번 WP6 — 표준사전 구조 보강 (2026-10-14, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-14_wp6_std_struct.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. 13번 표준 운영 옵션 — TB_STD_DICT 에 사전별 옵션 4개 (기본값은 지금 동작과 같다)
--   2. 4번 도메인 그룹 다단계 — TB_DOMAIN_GRP 상위 그룹·기본 데이터 타입·분류어·순서
--   3. 3번 코드 유형 — TB_TERMS.CODE_TP·PARENT_CODE_GRP, TB_CODE_DATA.PARENT_CODE_VAL, TB_CODE_INTEG(통합 코드 구성)
--   4. 15번 옛 표준 → 새 표준 매핑 — TB_STD_MAP
-- 시드 없음. 기존 코드(CODE_GRP 가 있는 용어)는 CODE_TP 가 비어 있으면 단일 코드로 본다.

SET search_path TO quality;

BEGIN;

-- ===== 13번 표준 운영 옵션 =====
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS upper_yn character(1) DEFAULT 'Y'::bpchar;
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS word_eng_nm_dup_yn character(1) DEFAULT 'Y'::bpchar;
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS term_eng_max_len integer;
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS old_std_yn character(1) DEFAULT 'N'::bpchar;
COMMENT ON COLUMN quality.tb_std_dict.upper_yn IS '표준 대문자 — 단어·용어 영문약어 소문자 입력을 대문자로 바꿔 받음 (N 이면 소문자 거부, 104번 WP6-13)';
COMMENT ON COLUMN quality.tb_std_dict.word_eng_nm_dup_yn IS '단어 영문명 중복 허용 (104번 WP6-13)';
COMMENT ON COLUMN quality.tb_std_dict.term_eng_max_len IS '용어 영문약어명 최대 길이(Byte), NULL 이면 제한 없음 (104번 WP6-13)';
COMMENT ON COLUMN quality.tb_std_dict.old_std_yn IS '구표준 사용 — 옛 표준 → 새 표준 매핑 (104번 WP6-13·15)';

-- ===== 4번 도메인 그룹 다단계 =====
ALTER TABLE quality.tb_domain_grp ADD COLUMN IF NOT EXISTS parent_grp_id character varying(22);
ALTER TABLE quality.tb_domain_grp ADD COLUMN IF NOT EXISTS dflt_data_type character varying(50);
ALTER TABLE quality.tb_domain_grp ADD COLUMN IF NOT EXISTS clsf_word_nm character varying(100);
ALTER TABLE quality.tb_domain_grp ADD COLUMN IF NOT EXISTS sort_order integer DEFAULT 0;
COMMENT ON COLUMN quality.tb_domain_grp.parent_grp_id IS '상위 도메인 그룹 (같은 사전, NULL 이면 최상위, 3단계까지 — 104번 WP6-4)';
COMMENT ON COLUMN quality.tb_domain_grp.dflt_data_type IS '그룹 기본 데이터 타입 — 도메인 등록 때 채움 (104번 WP6-4)';
COMMENT ON COLUMN quality.tb_domain_grp.clsf_word_nm IS '분류어 (형식단어명, 104번 WP6-4)';
COMMENT ON COLUMN quality.tb_domain_grp.sort_order IS '같은 상위 안 순서 (104번 WP6-4)';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tb_domain_grp_fk_parent') THEN
    ALTER TABLE quality.tb_domain_grp ADD CONSTRAINT tb_domain_grp_fk_parent
      FOREIGN KEY (parent_grp_id) REFERENCES quality.tb_domain_grp (domain_grp_id);
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_domain_grp_parent ON quality.tb_domain_grp (parent_grp_id);

-- ===== 3번 코드 유형 =====
ALTER TABLE quality.tb_terms ADD COLUMN IF NOT EXISTS code_tp character varying(10);
ALTER TABLE quality.tb_terms ADD COLUMN IF NOT EXISTS parent_code_grp character varying(50);
COMMENT ON COLUMN quality.tb_terms.code_tp IS '코드 유형 — SINGLE 단일·HIER 계층·DERIV 파생·INTEG 통합·INDIV 개별 (코드인데 NULL 이면 단일, 104번 WP6-3)';
COMMENT ON COLUMN quality.tb_terms.parent_code_grp IS '상위 코드 그룹 — 계층·파생 코드 (104번 WP6-3)';

ALTER TABLE quality.tb_code_data ADD COLUMN IF NOT EXISTS parent_code_val character varying(50);
COMMENT ON COLUMN quality.tb_code_data.parent_code_val IS '상위 코드값 — 계층 코드 값 (상위 코드 그룹의 코드값, 104번 WP6-3)';

CREATE TABLE IF NOT EXISTS quality.tb_code_integ (
    dict_id       character varying(22) NOT NULL,
    code_grp      character varying(50) NOT NULL,
    sub_code_grp  character varying(50) NOT NULL,
    sort_order    integer DEFAULT 0 NOT NULL,
    cret_dt       character varying(14),
    cret_user_id  character varying(50),
    CONSTRAINT tb_code_integ_pk PRIMARY KEY (dict_id, code_grp, sub_code_grp)
);
COMMENT ON TABLE  quality.tb_code_integ IS '통합 코드 구성 — 통합 코드 그룹 → 하위 코드 그룹 (104번 WP6-3)';

-- ===== 15번 옛 표준 → 새 표준 매핑 =====
CREATE TABLE IF NOT EXISTS quality.tb_std_map (
    map_id           character varying(40)  NOT NULL,
    dict_id          character varying(22)  NOT NULL,
    map_tp           character varying(10)  NOT NULL,
    old_nm           character varying(200) NOT NULL,
    old_eng_abrv_nm  character varying(200),
    new_id           character varying(22)  NOT NULL,
    map_reason       character varying(500),
    use_yn           character(1) DEFAULT 'Y'::bpchar NOT NULL,
    cret_dt          character varying(14),
    cret_user_id     character varying(50),
    updt_dt          character varying(14),
    updt_user_id     character varying(50),
    CONSTRAINT tb_std_map_pk PRIMARY KEY (map_id)
);
COMMENT ON TABLE  quality.tb_std_map IS '옛 표준 → 새 표준 매핑 (AS-IS / TO-BE, 104번 WP6-15)';
COMMENT ON COLUMN quality.tb_std_map.map_tp IS 'WORD(단어), TERM(용어)';
COMMENT ON COLUMN quality.tb_std_map.new_id IS '새 표준 ID — TB_WORD.WORD_ID 또는 TB_TERMS.TERMS_ID';
CREATE INDEX IF NOT EXISTS ix_std_map_old_eng ON quality.tb_std_map (dict_id, map_tp, old_eng_abrv_nm);

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_std_map; DROP TABLE IF EXISTS quality.tb_code_integ;
-- ALTER TABLE quality.tb_code_data DROP COLUMN IF EXISTS parent_code_val;
-- ALTER TABLE quality.tb_terms DROP COLUMN IF EXISTS code_tp, DROP COLUMN IF EXISTS parent_code_grp;
-- ALTER TABLE quality.tb_domain_grp DROP CONSTRAINT IF EXISTS tb_domain_grp_fk_parent;
-- DROP INDEX IF EXISTS quality.ix_domain_grp_parent;
-- ALTER TABLE quality.tb_domain_grp DROP COLUMN IF EXISTS parent_grp_id, DROP COLUMN IF EXISTS dflt_data_type, DROP COLUMN IF EXISTS clsf_word_nm, DROP COLUMN IF EXISTS sort_order;
-- ALTER TABLE quality.tb_std_dict DROP COLUMN IF EXISTS upper_yn, DROP COLUMN IF EXISTS word_eng_nm_dup_yn, DROP COLUMN IF EXISTS term_eng_max_len, DROP COLUMN IF EXISTS old_std_yn;
-- COMMIT;
