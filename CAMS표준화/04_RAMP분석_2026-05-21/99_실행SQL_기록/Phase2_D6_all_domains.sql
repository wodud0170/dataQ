-- Phase 2-2 D6 — RAMP 전체 (분류,타입,길이) 도메인 등록
BEGIN;

-- 신규 도메인 5건
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('SN-HjYdNyqIrrqyGGWgqf_','주소V20','기타','주소','주소 VARCHAR(20) — 기관표준','VARCHAR',20,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('1VEAWgKOVqa_mZb5IXWhwO','주소V16','기타','주소','주소 VARCHAR(16) — 기관표준','VARCHAR',16,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('JB5CdsMK0VYxqQUyENZw67','번호V91','기타','번호','번호 VARCHAR(91) — 기관표준','VARCHAR',91,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('p4_8sEvIMq1d01l1HEy3VN','주소V30','기타','주소','주소 VARCHAR(30) — 기관표준','VARCHAR',30,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('l5C7Uo9dvmwen8opjEJpnb','주소V50','기타','주소','주소 VARCHAR(50) — 기관표준','VARCHAR',50,0,'',ARRAY[]::text[],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');

SELECT 'tb_domain N' as t, count(*) FROM quality.tb_domain WHERE comm_stnd_yn='N';

COMMIT;