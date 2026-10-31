-- dataQ설계/sync/PC_align_2026-11-01_dict_dup_opts.sql — 108 §4.31 보완 6 (104번 WP6-13 사전 옵션: 동음이의어 허용·용어 영문약어명 중복 허용)
-- 사전 옵션 2개(TB_STD_DICT.HOMONYM_YN·TERM_ENG_ABRV_DUP_YN, 기본 N — 지금 사전 동작 그대로)와
-- 행마다 "유일 검사 대상" 표시 칸(TB_WORD.WORD_NM_UNIQ_YN·TB_TERMS.TERMS_ENG_ABRV_UNIQ_YN, 기본 Y)을 더하고,
-- tb_word_ux_3·tb_terms_ux_2 를 표시='Y' 인 행만 보는 부분 유니크 인덱스로 바꾼다 (부분 인덱스 조건에는 다른 테이블의 사전 옵션을 쓸 수 없다).
-- 표시 칸은 등록 INSERT(word.insertWord·stdImport.copyWord·terms.insertTerms·stdImport.copyTerm)가 사전 옵션으로 채우고,
-- 옵션을 바꾸면 StdDictService.update 가 같은 트랜잭션에서 그 사전 행의 표시를 고친다.
-- 적용: PC1 docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-11-01_dict_dup_opts.sql
--       PC2 네이티브 psql 로 같은 파일 (psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …).
-- 멱등(두 번 적용해도 같다). 적용 뒤 pg_dump 블록(tb_std_dict·tb_word·tb_terms·인덱스)을 덤프 2종(DDL_full_schema.sql·docker/dataq-db/initdb/01_schema.sql)에 splice.
SET search_path TO quality;

BEGIN;
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS homonym_yn character(1) DEFAULT 'N'::bpchar;
ALTER TABLE quality.tb_std_dict ADD COLUMN IF NOT EXISTS term_eng_abrv_dup_yn character(1) DEFAULT 'N'::bpchar;
COMMENT ON COLUMN quality.tb_std_dict.homonym_yn IS '동음이의어 허용 — 같은 사전에 같은 단어명 단어 여러 개 (영문약어는 달라야 함, 104번 WP6-13)';
COMMENT ON COLUMN quality.tb_std_dict.term_eng_abrv_dup_yn IS '용어 영문약어명 중복 허용 — 같은 사전에 같은 영문약어명 용어 여러 개 (104번 WP6-13)';
ALTER TABLE quality.tb_word ADD COLUMN IF NOT EXISTS word_nm_uniq_yn character(1) DEFAULT 'Y'::bpchar NOT NULL;
ALTER TABLE quality.tb_terms ADD COLUMN IF NOT EXISTS terms_eng_abrv_uniq_yn character(1) DEFAULT 'Y'::bpchar NOT NULL;
COMMENT ON COLUMN quality.tb_word.word_nm_uniq_yn IS '단어명 유일 검사 대상 — 사전이 동음이의어를 허용하면 N (tb_word_ux_3 부분 인덱스 조건, 사전 옵션을 따르는 내부 칸, 104번 WP6-13)';
COMMENT ON COLUMN quality.tb_terms.terms_eng_abrv_uniq_yn IS '용어 영문약어명 유일 검사 대상 — 사전이 용어 영문약어명 중복을 허용하면 N (tb_terms_ux_2 부분 인덱스 조건, 사전 옵션을 따르는 내부 칸, 104번 WP6-13)';
-- 다시 적용할 때 이미 켠 사전이 있어도 표시가 옵션과 맞게
UPDATE quality.tb_word W SET word_nm_uniq_yn = CASE WHEN D.homonym_yn = 'Y' THEN 'N' ELSE 'Y' END
  FROM quality.tb_std_dict D WHERE D.dict_id = W.dict_id AND W.word_nm_uniq_yn IS DISTINCT FROM CASE WHEN D.homonym_yn = 'Y' THEN 'N' ELSE 'Y' END;
UPDATE quality.tb_terms T SET terms_eng_abrv_uniq_yn = CASE WHEN D.term_eng_abrv_dup_yn = 'Y' THEN 'N' ELSE 'Y' END
  FROM quality.tb_std_dict D WHERE D.dict_id = T.dict_id AND T.terms_eng_abrv_uniq_yn IS DISTINCT FROM CASE WHEN D.term_eng_abrv_dup_yn = 'Y' THEN 'N' ELSE 'Y' END;
-- 유니크 인덱스를 옵션을 따르는 부분 인덱스로 (이름 그대로 — StdInputRule.translateDuplicateError 가 이름으로 가른다)
DROP INDEX IF EXISTS quality.tb_word_ux_3;
CREATE UNIQUE INDEX tb_word_ux_3 ON quality.tb_word USING btree (dict_id, word_nm) WHERE (word_nm_uniq_yn = 'Y'::bpchar);
DROP INDEX IF EXISTS quality.tb_terms_ux_2;
CREATE UNIQUE INDEX tb_terms_ux_2 ON quality.tb_terms USING btree (dict_id, terms_eng_abrv_nm) WHERE (terms_eng_abrv_uniq_yn = 'Y'::bpchar);
-- 조회용 (부분 인덱스는 조건 없는 이름·영문약어 조회에 쓰이지 않는다)
CREATE INDEX IF NOT EXISTS tb_word_ix_nm ON quality.tb_word USING btree (dict_id, word_nm);
CREATE INDEX IF NOT EXISTS tb_terms_ix_eng ON quality.tb_terms USING btree (dict_id, terms_eng_abrv_nm);
COMMIT;

-- 되돌리기 (필요할 때만 — 켠 사전에 같은 이름 단어·같은 영문약어 용어가 남아 있으면 유니크 인덱스 생성이 실패한다. 먼저 정리할 것)
-- BEGIN;
-- DROP INDEX IF EXISTS quality.tb_word_ix_nm;
-- DROP INDEX IF EXISTS quality.tb_terms_ix_eng;
-- DROP INDEX IF EXISTS quality.tb_word_ux_3;
-- CREATE UNIQUE INDEX tb_word_ux_3 ON quality.tb_word USING btree (dict_id, word_nm);
-- DROP INDEX IF EXISTS quality.tb_terms_ux_2;
-- CREATE UNIQUE INDEX tb_terms_ux_2 ON quality.tb_terms USING btree (dict_id, terms_eng_abrv_nm);
-- ALTER TABLE quality.tb_word DROP COLUMN IF EXISTS word_nm_uniq_yn;
-- ALTER TABLE quality.tb_terms DROP COLUMN IF EXISTS terms_eng_abrv_uniq_yn;
-- ALTER TABLE quality.tb_std_dict DROP COLUMN IF EXISTS homonym_yn;
-- ALTER TABLE quality.tb_std_dict DROP COLUMN IF EXISTS term_eng_abrv_dup_yn;
-- COMMIT;
