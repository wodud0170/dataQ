-- Phase 2-2 D5 — R8 미종결 형식단어 보충 + 기관표준 도메인 추가
BEGIN;

-- 신규 기관표준 도메인 3건
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('REkYfZ9Eoej7mwNRX8wweL','값N3','기타','값','값 NUMERIC(3) — R8 미종결 보충용','NUMERIC',3,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('IPIttyxHK54rZAXtdhF2hU','값N38','기타','값','값 NUMERIC(38) — R8 미종결 보충용','NUMERIC',38,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('87AKxPViYu3us_U_zF4zqr','값N5','기타','값','값 NUMERIC(5) — R8 미종결 보충용','NUMERIC',5,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');

COMMIT;