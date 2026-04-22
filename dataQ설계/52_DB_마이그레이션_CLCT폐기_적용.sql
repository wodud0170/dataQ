-- =============================================================
-- DB 마이그레이션: CLCT 폐기 적용 (PC 간 동기화)
-- 작성일: 2026-04-21
--
-- [중요] 개발 환경 전제
--   본 프로젝트는 각 PC(작업자/세션)가 자기 로컬 Docker PostgreSQL
--   (컨테이너명 dataq-db, 127.0.0.1:25433, schema=quality)로 개발한다.
--   공유 개발 DB가 없으므로, 한쪽 PC에서 DDL을 바꾸면 다른 PC는
--   코드 pull 후에도 런타임 오류(column "use_yn" does not exist 등)가 난다.
--   따라서 코드/DDL_claude_generated.sql 변경은 반드시 본 52번 파일에도
--   누적 반영하여, 다른 PC/세션이 한 번에 자기 로컬 DB를 맞출 수 있어야 한다.
--
-- 사유(최초): PC2 세션에서 CLCT 폐기 코드를 커밋했으나 이 PC(PC1)의
--            로컬 DB에는 DDL이 적용되지 않아 런타임 오류 발생.
--
-- 적용 대상 DB: 로컬 PostgreSQL (127.0.0.1:25433, schema=quality)
-- 실행 방법:
--   docker cp 52_DB_마이그레이션_CLCT폐기_적용.sql dataq-db:/tmp/
--   docker exec -i dataq-db psql -U admin -d quality -f /tmp/52_DB_마이그레이션_CLCT폐기_적용.sql
-- 실행 순서: 위에서 아래로 순서대로.
-- 모두 IF EXISTS / IF NOT EXISTS / DROP NOT NULL 등으로 보호되어 있어 재실행 안전.
-- =============================================================

-- -------------------------------------------------------------
-- 1. TB_DATA_MODEL_OBJ / TB_DATA_MODEL_ATTR 소프트삭제 컬럼
--    (수집 시 upsert + 이번 수집에 없던 행 USE_YN='N' 처리용)
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL_OBJ  ADD COLUMN IF NOT EXISTS USE_YN     CHAR(1)     DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_OBJ  ADD COLUMN IF NOT EXISTS DELETED_DT VARCHAR(14);
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN IF NOT EXISTS USE_YN     CHAR(1)     DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN IF NOT EXISTS DELETED_DT VARCHAR(14);

-- 기존 행들은 USE_YN을 'Y'로 채움 (NOT NULL이 아니므로 null 방지용)
UPDATE TB_DATA_MODEL_OBJ  SET USE_YN = 'Y' WHERE USE_YN IS NULL;
UPDATE TB_DATA_MODEL_ATTR SET USE_YN = 'Y' WHERE USE_YN IS NULL;

COMMENT ON COLUMN TB_DATA_MODEL_OBJ.USE_YN     IS '사용여부 Y/N (N=소프트 삭제)';
COMMENT ON COLUMN TB_DATA_MODEL_OBJ.DELETED_DT IS '소프트 삭제 일시 YYYYMMDDHH24MISS';
COMMENT ON COLUMN TB_DATA_MODEL_ATTR.USE_YN     IS '사용여부 Y/N (N=소프트 삭제)';
COMMENT ON COLUMN TB_DATA_MODEL_ATTR.DELETED_DT IS '소프트 삭제 일시 YYYYMMDDHH24MISS';

-- -------------------------------------------------------------
-- 2. OBJ/ATTR PK 변경 (DM_CLCT_ID 제거 → DM_ID 기반)
--    UPSERT 쿼리의 ON CONFLICT 대상을 (DM_ID, OBJ_NM) / (DM_ID, OBJ_NM, ATTR_NM) 로 맞춤
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL_OBJ  DROP CONSTRAINT IF EXISTS tb_data_model_obj_pkey;
ALTER TABLE TB_DATA_MODEL_ATTR DROP CONSTRAINT IF EXISTS tb_data_model_attr_pkey;

-- 중복 정리: 구 PK에 DM_CLCT_ID가 있었으므로 같은 (DM_ID,OBJ_NM)이 여러 CLCT에 존재할 수 있음.
-- 최신 1건만 남기고 삭제 (가장 큰 ctid 기준).
DELETE FROM TB_DATA_MODEL_OBJ a
USING TB_DATA_MODEL_OBJ b
WHERE a.DM_ID = b.DM_ID AND a.OBJ_NM = b.OBJ_NM AND a.ctid < b.ctid;

DELETE FROM TB_DATA_MODEL_ATTR a
USING TB_DATA_MODEL_ATTR b
WHERE a.DM_ID = b.DM_ID AND a.OBJ_NM = b.OBJ_NM AND a.ATTR_NM = b.ATTR_NM AND a.ctid < b.ctid;

ALTER TABLE TB_DATA_MODEL_OBJ  ADD CONSTRAINT tb_data_model_obj_pkey  PRIMARY KEY (DM_ID, OBJ_NM);
ALTER TABLE TB_DATA_MODEL_ATTR ADD CONSTRAINT tb_data_model_attr_pkey PRIMARY KEY (DM_ID, OBJ_NM, ATTR_NM);

-- -------------------------------------------------------------
-- 3. DM_CLCT_ID 컬럼 DROP (OBJ/ATTR만 — INDEX/CONSTRAINT는 수집로그로 유지)
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL_OBJ  DROP COLUMN IF EXISTS DM_CLCT_ID;
ALTER TABLE TB_DATA_MODEL_ATTR DROP COLUMN IF EXISTS DM_CLCT_ID;

-- -------------------------------------------------------------
-- 4. TB_DATA_MODEL_STATS 테이블 DROP (실시간 COUNT로 대체됨)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS TB_DATA_MODEL_STATS;

-- -------------------------------------------------------------
-- 5. 보조: 신규 컬럼/기능에서 요구되는 TB_APRV_STATS.REQ_ITEM_NM
--    (반려 물리삭제 후 이력 보존용 — 혹시 누락된 환경 대비)
-- -------------------------------------------------------------
ALTER TABLE TB_APRV_STATS ADD COLUMN IF NOT EXISTS REQ_ITEM_NM VARCHAR(200);
COMMENT ON COLUMN TB_APRV_STATS.REQ_ITEM_NM IS '요청 항목명 (반려 시 원본 삭제되므로 이력 보존용)';

-- -------------------------------------------------------------
-- 6. 보조: TB_DATA_MODEL 논리/물리 구분 + DM_DS_ID NULL 허용
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL ALTER COLUMN DM_DS_ID DROP NOT NULL;
ALTER TABLE TB_DATA_MODEL ADD COLUMN IF NOT EXISTS MODEL_TYPE VARCHAR(10) DEFAULT 'PHYSICAL';
COMMENT ON COLUMN TB_DATA_MODEL.MODEL_TYPE IS 'PHYSICAL(물리만), LOGICAL(논리만), BOTH(논리+물리)';

-- -------------------------------------------------------------
-- 7. 보조: TB_DATA_MODEL_CLCT 수집 타입, OBJ/ATTR 코멘트 컬럼
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL_CLCT ADD COLUMN IF NOT EXISTS CLCT_TYPE VARCHAR(20) DEFAULT 'DBMS';
COMMENT ON COLUMN TB_DATA_MODEL_CLCT.CLCT_TYPE IS '스냅샷 원천 (DBMS: 수집, MANUAL: 수동편집, ERWIN: ERwin 임포트)';

ALTER TABLE TB_DATA_MODEL_OBJ  ADD COLUMN IF NOT EXISTS OBJ_COMMENT  VARCHAR(500);
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN IF NOT EXISTS ATTR_COMMENT VARCHAR(500);
COMMENT ON COLUMN TB_DATA_MODEL_OBJ.OBJ_COMMENT   IS 'DB에서 수집한 테이블 코멘트 원본 (수집 시 자동, 읽기 전용)';
COMMENT ON COLUMN TB_DATA_MODEL_ATTR.ATTR_COMMENT IS 'DB에서 수집한 컬럼 코멘트 원본 (수집 시 자동, 읽기 전용)';

-- -------------------------------------------------------------
-- 8. 논리 모델 지원: OBJ_OWNER NOT NULL 제거
--    논리 모델은 DBMS 스키마가 없으므로 OBJ_OWNER를 NULL로 저장.
--    물리 모델(수집)은 여전히 OWNER를 채우지만 제약만 완화.
-- -------------------------------------------------------------
ALTER TABLE TB_DATA_MODEL_OBJ  ALTER COLUMN OBJ_OWNER DROP NOT NULL;
ALTER TABLE TB_DATA_MODEL_ATTR ALTER COLUMN OBJ_OWNER DROP NOT NULL;

-- =============================================================
-- 검증 쿼리 (적용 후 실행해서 확인)
-- =============================================================
-- SELECT column_name FROM information_schema.columns
--  WHERE table_name = 'tb_data_model_obj' AND column_name IN ('use_yn','deleted_dt','dm_clct_id');
--  (use_yn / deleted_dt 만 나와야 함. dm_clct_id는 없어야 함)
--
-- SELECT constraint_name, constraint_type FROM information_schema.table_constraints
--  WHERE table_name = 'tb_data_model_obj' AND constraint_type = 'PRIMARY KEY';
--  (pkey 의 constraint_keys 가 (dm_id, obj_nm) 이어야 함)
--
-- SELECT 1 FROM information_schema.tables WHERE table_name = 'tb_data_model_stats';
--  (0건 — 존재하지 않아야 함)
