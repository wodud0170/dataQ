-- ============================================================
-- 99. 데이터 품질 진단 테스트 — 전체 정리
-- ============================================================
-- 본 SQL 은 01~04 SQL 로 등록한 모든 테스트 데이터를 삭제한다.
-- 본 시스템 운영 데이터(quality.TB_DATA_MODEL 의 다른 모델 등)는 건드리지 않음.
-- ============================================================

-- 1) 룰 진단 결과/이력 정리
DELETE FROM quality.TB_QUAL_VIOLATION_SAMPLE
 WHERE DIAG_ID IN (
   SELECT DIAG_ID FROM quality.TB_QUAL_DIAG_HISTORY WHERE DM_ID = 'TESTQUALDM00000000001A'
 );

DELETE FROM quality.TB_QUAL_RULE_RESULT
 WHERE DIAG_ID IN (
   SELECT DIAG_ID FROM quality.TB_QUAL_DIAG_HISTORY WHERE DM_ID = 'TESTQUALDM00000000001A'
 );

DELETE FROM quality.TB_QUAL_PROFILE_RESULT WHERE DM_ID = 'TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_DIAG_HISTORY    WHERE DM_ID = 'TESTQUALDM00000000001A';
DELETE FROM quality.TB_QUAL_RULE            WHERE DM_ID = 'TESTQUALDM00000000001A';

-- 2) dataQ 메타 정리
DELETE FROM quality.TB_DATA_MODEL_ATTR WHERE DM_ID = 'TESTQUALDM00000000001A';
DELETE FROM quality.TB_DATA_MODEL_OBJ  WHERE DM_ID = 'TESTQUALDM00000000001A';
DELETE FROM quality.TB_DATA_MODEL      WHERE DM_ID = 'TESTQUALDM00000000001A';

DELETE FROM ndata.TB_DATA_SOURCE       WHERE DS_ID = 'TESTPGQUAL';

-- 3) 진단 대상 테이블/스키마 정리
DROP SCHEMA IF EXISTS testdata CASCADE;
