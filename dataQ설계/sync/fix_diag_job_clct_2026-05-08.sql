-- 86번 (수동 테스트 발견) — TB_DIAG_JOB.DM_CLCT_ID NOT NULL 제거
-- CLCT 폐기 정책 (49번) 이후 INSERT 시 NULL 발생 → 표준 진단 시작 실패
-- DM_CLCT_ID 는 옵션 (legacy) 로 두고 NULL 허용
SET search_path TO quality;
ALTER TABLE TB_DIAG_JOB ALTER COLUMN DM_CLCT_ID DROP NOT NULL;
