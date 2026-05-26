-- Phase 2-2 시간 도메인 5건 INSERT
BEGIN;

-- 도메인분류 2건 (일자, 일시) 먼저 등록 (FK 충족)
INSERT INTO quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, cret_dt, cret_user_id) VALUES ('NYNlT3Fovqj-xvLjIUtIwa','일자','날짜/시간','N',to_char(now(),'YYYYMMDDHH24MISS'),'admin');
INSERT INTO quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, cret_dt, cret_user_id) VALUES ('UGUHneU07OP3FIkevc_9Eu','일시','날짜/시간','N',to_char(now(),'YYYYMMDDHH24MISS'),'admin');

-- 도메인 5건
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('LoZ5hCdTQqP11CRxz4RNv6','일자V8','날짜/시간','일자','YYYYMMDD 형식의 8자리 일자 (VARCHAR)','VARCHAR',8,0,'YYYYMMDD',ARRAY['YYYY-MM-DD'],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('Z0DhOhQ3pwf7ufgtNigKfN','일시V14','날짜/시간','일시','YYYYMMDDHH24MISS 형식의 14자리 일시 (VARCHAR)','VARCHAR',14,0,'YYYYMMDDHH24MISS',ARRAY['YYYY-MM-DD HH:MI:SS'],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('gVf24aXi3NUs22Lbxxpv9Q','일자DT','날짜/시간','일자','DATE 타입 일자 (Oracle DATE)','DATE',0,0,'YYYY-MM-DD',ARRAY['YYYY-MM-DD'],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('AmEWU6bR3MVF8VxGt9yhSW','일시DT','날짜/시간','일시','DATE 타입 일시 (Oracle DATE, 시분초 포함)','DATE',0,0,'YYYYMMDDHH24MISS',ARRAY['YYYY-MM-DD HH:MI:SS'],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');
INSERT INTO quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, data_decimal_len, stor_fmt, expr_fmt_lst, comm_stnd_yn, aprv_yn, cret_dt, cret_user_id, use_yn) VALUES ('o2q7MoJ22bBFP11EK3T8zx','일시TS','날짜/시간','일시','TIMESTAMP 타입 일시 (Oracle TIMESTAMP)','TIMESTAMP',0,0,'YYYYMMDDHH24MISS.FF',ARRAY['YYYY-MM-DD HH:MI:SS.FF'],'N','Y',to_char(now(),'YYYYMMDDHH24MISS'),'admin','Y');

SELECT domain_nm, domain_clsf_nm, data_type, data_len, stor_fmt FROM quality.tb_domain WHERE domain_nm IN ('일자V8','일시V14','일자DT','일시DT','일시TS') ORDER BY domain_nm;

COMMIT;