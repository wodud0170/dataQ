-- 104번 WP2 — 역할·권한·시스템 계층 (2026-10-13, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-13_wp2_role.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. TB_ROLE / TB_ROLE_PERM / TB_USER_ROLE 신규 — 역할(권한 코드 묶음)과 사용자 지정(시스템 범위)
--   2. TB_SYS_INFO.DICT_ID 신규 — 시스템의 기본 표준사전 (시스템을 고른 모델 등록 때 기본값)
--   3. 내장 역할 3개 시드 — 표준 담당 / 모델 담당 / DB 담당 (관리자는 TB_USER.ADM_YN, 일반 사용자는 역할 없음)
--
-- 권한 코드 목록은 코드(q-common PermCode)가 단일 기준이다. TB_ROLE_PERM.PERM_CD 는 그 이름을 담는다.

SET search_path TO quality;

BEGIN;

CREATE TABLE IF NOT EXISTS quality.tb_role (
    role_id      character varying(40)  NOT NULL,
    role_nm      character varying(100) NOT NULL,
    role_desc    character varying(500),
    builtin_yn   character(1) DEFAULT 'N'::bpchar NOT NULL,
    cret_dt      character varying(14),
    cret_user_id character varying(50),
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_role_pk PRIMARY KEY (role_id),
    CONSTRAINT tb_role_nm_uk UNIQUE (role_nm)
);
COMMENT ON TABLE  quality.tb_role IS '역할 — 권한 코드(PermCode) 묶음 (104번 WP2)';
COMMENT ON COLUMN quality.tb_role.builtin_yn IS '내장 역할 여부 (Y 는 삭제 불가)';

CREATE TABLE IF NOT EXISTS quality.tb_role_perm (
    role_id  character varying(40) NOT NULL,
    perm_cd  character varying(50) NOT NULL,
    CONSTRAINT tb_role_perm_pk PRIMARY KEY (role_id, perm_cd),
    CONSTRAINT tb_role_perm_role_fk FOREIGN KEY (role_id) REFERENCES quality.tb_role (role_id) ON DELETE CASCADE
);
COMMENT ON TABLE  quality.tb_role_perm IS '역할에 든 권한 코드 (104번 WP2)';
COMMENT ON COLUMN quality.tb_role_perm.perm_cd IS '권한 코드 — q-common PermCode 이름';

CREATE TABLE IF NOT EXISTS quality.tb_user_role (
    user_id      character varying(50) NOT NULL,
    role_id      character varying(40) NOT NULL,
    sys_cd       character varying(22) DEFAULT '*'::character varying NOT NULL,
    cret_dt      character varying(14),
    cret_user_id character varying(50),
    CONSTRAINT tb_user_role_pk PRIMARY KEY (user_id, role_id, sys_cd),
    CONSTRAINT tb_user_role_role_fk FOREIGN KEY (role_id) REFERENCES quality.tb_role (role_id) ON DELETE CASCADE,
    CONSTRAINT tb_user_role_user_fk FOREIGN KEY (user_id) REFERENCES quality.tb_user (user_id) ON DELETE CASCADE
);
COMMENT ON TABLE  quality.tb_user_role IS '사용자에게 준 역할 (104번 WP2)';
COMMENT ON COLUMN quality.tb_user_role.sys_cd IS '권한 범위 시스템 (TB_SYS_INFO.SYS_CD). ''*'' = 모든 시스템. 시스템 단위 권한(DDL_EXEC)만 의미가 있다';

CREATE INDEX IF NOT EXISTS ix_user_role_role ON quality.tb_user_role (role_id);

ALTER TABLE quality.tb_sys_info ADD COLUMN IF NOT EXISTS dict_id character varying(22);
COMMENT ON COLUMN quality.tb_sys_info.dict_id IS '시스템 기본 표준사전 (TB_STD_DICT.DICT_ID). 이 시스템의 모델 등록 때 기본값 (104번 WP2)';

-- 내장 역할 — 이미 있으면 건너뛴다 (이름·권한을 화면에서 고쳤을 수 있으므로 덮어쓰지 않는다)
INSERT INTO quality.tb_role (role_id, role_nm, role_desc, builtin_yn, cret_dt, cret_user_id, updt_dt, updt_user_id)
VALUES ('BUILTIN_STD',   '표준 담당',   '표준사전을 직접 관리하고 변경 요청을 승인한다',           'Y', to_char(now(), 'YYYYMMDDHH24MISS'), 'system', to_char(now(), 'YYYYMMDDHH24MISS'), 'system'),
       ('BUILTIN_MODEL', '모델 담당',   '데이터 모델 변경을 반영·승인하고 영역·진단 제외·스케줄을 관리한다', 'Y', to_char(now(), 'YYYYMMDDHH24MISS'), 'system', to_char(now(), 'YYYYMMDDHH24MISS'), 'system'),
       ('BUILTIN_DB',    'DB 담당',     '승인된 변경을 DB 에 반영하고 데이터 소스를 관리한다',      'Y', to_char(now(), 'YYYYMMDDHH24MISS'), 'system', to_char(now(), 'YYYYMMDDHH24MISS'), 'system')
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO quality.tb_role_perm (role_id, perm_cd)
SELECT v.role_id, v.perm_cd
  FROM (VALUES ('BUILTIN_STD', 'STD_MANAGE'), ('BUILTIN_STD', 'STD_APPROVE'), ('BUILTIN_STD', 'DICT_ADMIN'),
               ('BUILTIN_MODEL', 'DM_MANAGE'), ('BUILTIN_MODEL', 'DM_APPROVE'), ('BUILTIN_MODEL', 'AREA_ADMIN'),
               ('BUILTIN_MODEL', 'DIAG_TARGET_ADMIN'), ('BUILTIN_MODEL', 'SCHEDULE_ADMIN'),
               ('BUILTIN_DB', 'DDL_EXEC'), ('BUILTIN_DB', 'DS_ADMIN')) AS v (role_id, perm_cd)
 WHERE NOT EXISTS (SELECT 1 FROM quality.tb_role_perm p WHERE p.role_id = v.role_id)
ON CONFLICT DO NOTHING;

COMMIT;

-- 롤백 (필요할 때만)
-- BEGIN;
-- DROP TABLE IF EXISTS quality.tb_user_role;
-- DROP TABLE IF EXISTS quality.tb_role_perm;
-- DROP TABLE IF EXISTS quality.tb_role;
-- ALTER TABLE quality.tb_sys_info DROP COLUMN IF EXISTS dict_id;
-- COMMIT;
