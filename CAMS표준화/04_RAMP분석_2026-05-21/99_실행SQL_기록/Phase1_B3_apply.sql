-- Phase 1 B-3 옵션 A 실행 SQL
-- 작성: 2026-05-23
-- 1) 행안부 용어 108건 DELETE (CASCADE로 tb_terms_words 자동 삭제)
-- 2) 행안부 단어 3건 DELETE
-- 3) RAMP 신규 단어 3건 INSERT (comm_stnd_yn='N')

BEGIN;

-- 1) 행안부 용어 삭제 (SEQ/ELCT/SQL 단어 사용분)
DELETE FROM quality.tb_terms
WHERE comm_stnd_yn='Y'
  AND terms_id IN (
    SELECT DISTINCT tw.terms_id
    FROM quality.tb_terms_words tw
    JOIN quality.tb_word w ON (tw.word_id, tw.word_nm) = (w.word_id, w.word_nm)
    WHERE w.comm_stnd_yn='Y' AND w.word_eng_abrv_nm IN ('SEQ','ELCT','SQL')
  );

-- 2) 행안부 단어 삭제
DELETE FROM quality.tb_word
WHERE comm_stnd_yn='Y' AND word_eng_abrv_nm IN ('SEQ','ELCT','SQL');
-- 2-1) 기존 N 등록된 '질의' (CAMS 테스트분, 사용 0건) DELETE — UNIQUE word_nm 충돌 회피
DELETE FROM quality.tb_word WHERE word_nm='질의' AND comm_stnd_yn='N';

-- 3) RAMP 신규 단어 등록
INSERT INTO quality.tb_word (
  word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc,
  word_clsf_yn, domain_clsf_nm, comm_stnd_yn, aprv_yn,
  cret_dt, cret_user_id, use_yn
) VALUES (
  'hwCBAVH9rrFmc8PC5HUvDZ', '순번', 'SEQ', 'SEQUENCIAL NUMBER', '順番
 순서대로 매겨지는 번호.',
  'Y', '번호', 'N', 'Y',
  to_char(now(),'YYYYMMDDHH24MISS'), 'admin', 'Y'
);
INSERT INTO quality.tb_word (
  word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc,
  word_clsf_yn, domain_clsf_nm, comm_stnd_yn, aprv_yn,
  cret_dt, cret_user_id, use_yn
) VALUES (
  'Mz4PuruugoOd7fjOKb__Ir', '전자', 'ELCT', 'ELECTRON', '電子
 원자를 이루는 기본적 소립자의 한 가지. 음전하(陰電荷)를 가지고 질량이 매우 작으면서 안정되어 있음.',
  'N', '', 'N', 'Y',
  to_char(now(),'YYYYMMDDHH24MISS'), 'admin', 'Y'
);
INSERT INTO quality.tb_word (
  word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc,
  word_clsf_yn, domain_clsf_nm, comm_stnd_yn, aprv_yn,
  cret_dt, cret_user_id, use_yn
) VALUES (
  'GIcMLRZqkhSGEdr38HfaKF', '질의', 'SQL', 'SQL', '질의',
  'N', '', 'N', 'Y',
  to_char(now(),'YYYYMMDDHH24MISS'), 'admin', 'Y'
);

-- 검증
SELECT word_nm, word_eng_abrv_nm, comm_stnd_yn FROM quality.tb_word WHERE word_eng_abrv_nm IN ('SEQ','ELCT','SQL');
SELECT count(*) FROM quality.tb_terms WHERE comm_stnd_yn='Y';
SELECT count(*) FROM quality.tb_word WHERE comm_stnd_yn='Y';

-- 문제 없으면 COMMIT, 아니면 ROLLBACK
COMMIT;
-- ROLLBACK;