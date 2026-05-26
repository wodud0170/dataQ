-- RAMP 용어사전 INSERT (Phase 2 종합)
BEGIN;


SELECT 'tb_terms Y' as t, count(*) FROM quality.tb_terms WHERE comm_stnd_yn='Y';
SELECT 'tb_terms N' as t, count(*) FROM quality.tb_terms WHERE comm_stnd_yn='N';

COMMIT;