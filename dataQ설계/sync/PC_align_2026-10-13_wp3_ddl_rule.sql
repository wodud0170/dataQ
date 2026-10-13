-- 104번 WP3 — DBMS 별 DDL·DB 반영 (2026-10-13, PC1)
--
-- 멱등: 이미 있으면 건너뛴다. 양쪽 PC 모두 같은 파일을 한 번 적용한다.
--   docker exec -i dataq-db psql -U admin -d postgres -v ON_ERROR_STOP=1 < dataQ설계/sync/PC_align_2026-10-13_wp3_ddl_rule.sql
--   (PC2 네이티브 PostgreSQL: psql -h localhost -p 25433 -U admin -d postgres -v ON_ERROR_STOP=1 -f …)
--
-- 내용
--   1. TB_DATA_TYPE_MAP 신규 — 데이터 타입 → DBMS 별 물리 타입 틀 (관리 > DDL 규칙)
--      틀: {len}·{dec} = 길이·소수점, [...] = 안의 값이 모두 0 보다 클 때만 남는 구간. 행이 없는 타입은 이름(길이,소수점) 그대로.
--   2. TB_SYS_CONF 신규 — 설정. DDL_PK_NM_TMPL = PK 이름 규칙 (지금 규칙 PK_{TABLE})
--   3. TB_DATA_MODEL_CHANGE_HISTORY.DDL_DBMS_TP 신규 — 변경 조각을 만든 DBMS (모델 데이터소스의 DBMS_TP)
--   4. 타입 매핑 시드 — Oracle·Tibero·PostgreSQL·MariaDB·SQLServer. Oracle·PostgreSQL 은 그동안 코드에 있던 13종 규칙 그대로,
--      빠져 있던 DATETIME·FLOAT·DOUBLE·TIME·BOOLEAN 과 N 계열·정수 계열·이진 계열을 더했다. 이미 있는 행은 덮어쓰지 않는다.

SET search_path TO quality;

BEGIN;

CREATE TABLE IF NOT EXISTS quality.tb_data_type_map (
    data_type      character varying(50)  NOT NULL,
    dbms_tp        character varying(20)  NOT NULL,
    phys_data_type character varying(100) NOT NULL,
    dflt_data_len  integer,
    cret_dt        character varying(14),
    cret_user_id   character varying(50),
    updt_dt        character varying(14),
    updt_user_id   character varying(50),
    CONSTRAINT tb_data_type_map_pk PRIMARY KEY (data_type, dbms_tp)
);
COMMENT ON TABLE  quality.tb_data_type_map IS '데이터 타입 → DBMS 별 물리 타입 (104번 WP3, 관리 > DDL 규칙)';
COMMENT ON COLUMN quality.tb_data_type_map.data_type IS '모델·도메인의 데이터 타입 (대문자)';
COMMENT ON COLUMN quality.tb_data_type_map.dbms_tp IS '데이터소스 DBMS_TP — Oracle, Tibero, PostgreSQL, MariaDB, SQLServer';
COMMENT ON COLUMN quality.tb_data_type_map.phys_data_type IS '물리 타입 틀. {len}·{dec} = 길이·소수점, [...] = 안의 값이 모두 있을 때만 남는 구간';
COMMENT ON COLUMN quality.tb_data_type_map.dflt_data_len IS '길이가 없는 컬럼에 쓸 길이 ([ ] 밖에 {len} 이 있으면 필수)';

CREATE TABLE IF NOT EXISTS quality.tb_sys_conf (
    conf_key     character varying(50)  NOT NULL,
    conf_val     character varying(500),
    conf_desc    character varying(500),
    updt_dt      character varying(14),
    updt_user_id character varying(50),
    CONSTRAINT tb_sys_conf_pk PRIMARY KEY (conf_key)
);
COMMENT ON TABLE quality.tb_sys_conf IS '설정 (104번 WP3~)';

ALTER TABLE quality.tb_data_model_change_history ADD COLUMN IF NOT EXISTS ddl_dbms_tp character varying(20);
COMMENT ON COLUMN quality.tb_data_model_change_history.ddl_dbms_tp IS 'DDL_SNIPPET 을 만든 DBMS (모델 데이터소스 DBMS_TP). 데이터소스 없는 모델은 NULL (104번 WP3)';

INSERT INTO quality.tb_sys_conf (conf_key, conf_val, conf_desc, updt_dt, updt_user_id)
VALUES ('DDL_PK_NM_TMPL', 'PK_{TABLE}', 'PK 이름 규칙. {TABLE} = 테이블 이름. PK 생성 기본 이름과 DDL 의 PK 줄에 쓴다', to_char(now(), 'YYYYMMDDHH24MISS'), 'system')
ON CONFLICT (conf_key) DO NOTHING;

INSERT INTO quality.tb_data_type_map (data_type, dbms_tp, phys_data_type, dflt_data_len, cret_dt, cret_user_id, updt_dt, updt_user_id)
SELECT v.data_type, v.dbms_tp, v.phys_data_type, v.dflt_data_len,
       to_char(now(), 'YYYYMMDDHH24MISS'), 'system', to_char(now(), 'YYYYMMDDHH24MISS'), 'system'
  FROM (VALUES
    -- Oracle
    ('VARCHAR',   'Oracle', 'VARCHAR2({len} CHAR)', 255),
    ('VARCHAR2',  'Oracle', 'VARCHAR2({len} CHAR)', 255),
    ('NVARCHAR',  'Oracle', 'NVARCHAR2({len})', 255),
    ('NVARCHAR2', 'Oracle', 'NVARCHAR2({len})', 255),
    ('CHAR',      'Oracle', 'CHAR({len})', 1),
    ('NCHAR',     'Oracle', 'NCHAR({len})', 1),
    ('NUMBER',    'Oracle', 'NUMBER[({len}[,{dec}])]', NULL),
    ('NUMERIC',   'Oracle', 'NUMBER[({len}[,{dec}])]', NULL),
    ('DECIMAL',   'Oracle', 'NUMBER[({len}[,{dec}])]', NULL),
    ('INT',       'Oracle', 'NUMBER(10)', NULL),
    ('INTEGER',   'Oracle', 'NUMBER(10)', NULL),
    ('BIGINT',    'Oracle', 'NUMBER(19)', NULL),
    ('SMALLINT',  'Oracle', 'NUMBER(5)', NULL),
    ('TINYINT',   'Oracle', 'NUMBER(3)', NULL),
    ('FLOAT',     'Oracle', 'BINARY_FLOAT', NULL),
    ('REAL',      'Oracle', 'BINARY_FLOAT', NULL),
    ('DOUBLE',    'Oracle', 'BINARY_DOUBLE', NULL),
    ('DATE',      'Oracle', 'DATE', NULL),
    ('DATETIME',  'Oracle', 'DATE', NULL),
    ('TIME',      'Oracle', 'DATE', NULL),
    ('TIMESTAMP', 'Oracle', 'TIMESTAMP', NULL),
    ('TEXT',      'Oracle', 'CLOB', NULL),
    ('CLOB',      'Oracle', 'CLOB', NULL),
    ('NCLOB',     'Oracle', 'NCLOB', NULL),
    ('BLOB',      'Oracle', 'BLOB', NULL),
    ('BYTEA',     'Oracle', 'BLOB', NULL),
    ('BINARY',    'Oracle', 'RAW({len})', 2000),
    ('VARBINARY', 'Oracle', 'RAW({len})', 2000),
    ('BOOLEAN',   'Oracle', 'NUMBER(1)', NULL),
    ('BOOL',      'Oracle', 'NUMBER(1)', NULL),
    ('BIT',       'Oracle', 'NUMBER(1)', NULL),
    -- Tibero (Oracle 과 같은 타입 체계)
    ('VARCHAR',   'Tibero', 'VARCHAR2({len})', 255),
    ('VARCHAR2',  'Tibero', 'VARCHAR2({len})', 255),
    ('NVARCHAR',  'Tibero', 'NVARCHAR2({len})', 255),
    ('NVARCHAR2', 'Tibero', 'NVARCHAR2({len})', 255),
    ('CHAR',      'Tibero', 'CHAR({len})', 1),
    ('NCHAR',     'Tibero', 'NCHAR({len})', 1),
    ('NUMBER',    'Tibero', 'NUMBER[({len}[,{dec}])]', NULL),
    ('NUMERIC',   'Tibero', 'NUMBER[({len}[,{dec}])]', NULL),
    ('DECIMAL',   'Tibero', 'NUMBER[({len}[,{dec}])]', NULL),
    ('INT',       'Tibero', 'NUMBER(10)', NULL),
    ('INTEGER',   'Tibero', 'NUMBER(10)', NULL),
    ('BIGINT',    'Tibero', 'NUMBER(19)', NULL),
    ('SMALLINT',  'Tibero', 'NUMBER(5)', NULL),
    ('TINYINT',   'Tibero', 'NUMBER(3)', NULL),
    ('FLOAT',     'Tibero', 'BINARY_FLOAT', NULL),
    ('REAL',      'Tibero', 'BINARY_FLOAT', NULL),
    ('DOUBLE',    'Tibero', 'BINARY_DOUBLE', NULL),
    ('DATE',      'Tibero', 'DATE', NULL),
    ('DATETIME',  'Tibero', 'DATE', NULL),
    ('TIME',      'Tibero', 'TIME', NULL),
    ('TIMESTAMP', 'Tibero', 'TIMESTAMP', NULL),
    ('TEXT',      'Tibero', 'CLOB', NULL),
    ('CLOB',      'Tibero', 'CLOB', NULL),
    ('NCLOB',     'Tibero', 'NCLOB', NULL),
    ('BLOB',      'Tibero', 'BLOB', NULL),
    ('BYTEA',     'Tibero', 'BLOB', NULL),
    ('BINARY',    'Tibero', 'RAW({len})', 2000),
    ('VARBINARY', 'Tibero', 'RAW({len})', 2000),
    ('BOOLEAN',   'Tibero', 'NUMBER(1)', NULL),
    ('BOOL',      'Tibero', 'NUMBER(1)', NULL),
    ('BIT',       'Tibero', 'NUMBER(1)', NULL),
    -- PostgreSQL
    ('VARCHAR',   'PostgreSQL', 'VARCHAR({len})', 255),
    ('VARCHAR2',  'PostgreSQL', 'VARCHAR({len})', 255),
    ('NVARCHAR',  'PostgreSQL', 'VARCHAR({len})', 255),
    ('NVARCHAR2', 'PostgreSQL', 'VARCHAR({len})', 255),
    ('CHAR',      'PostgreSQL', 'CHAR({len})', 1),
    ('NCHAR',     'PostgreSQL', 'CHAR({len})', 1),
    ('NUMBER',    'PostgreSQL', 'NUMERIC[({len}[,{dec}])]', NULL),
    ('NUMERIC',   'PostgreSQL', 'NUMERIC[({len}[,{dec}])]', NULL),
    ('DECIMAL',   'PostgreSQL', 'NUMERIC[({len}[,{dec}])]', NULL),
    ('INT',       'PostgreSQL', 'INTEGER', NULL),
    ('INTEGER',   'PostgreSQL', 'INTEGER', NULL),
    ('BIGINT',    'PostgreSQL', 'BIGINT', NULL),
    ('SMALLINT',  'PostgreSQL', 'SMALLINT', NULL),
    ('TINYINT',   'PostgreSQL', 'SMALLINT', NULL),
    ('FLOAT',     'PostgreSQL', 'REAL', NULL),
    ('REAL',      'PostgreSQL', 'REAL', NULL),
    ('DOUBLE',    'PostgreSQL', 'DOUBLE PRECISION', NULL),
    ('DATE',      'PostgreSQL', 'DATE', NULL),
    ('DATETIME',  'PostgreSQL', 'TIMESTAMP', NULL),
    ('TIME',      'PostgreSQL', 'TIME', NULL),
    ('TIMESTAMP', 'PostgreSQL', 'TIMESTAMP', NULL),
    ('TEXT',      'PostgreSQL', 'TEXT', NULL),
    ('CLOB',      'PostgreSQL', 'TEXT', NULL),
    ('NCLOB',     'PostgreSQL', 'TEXT', NULL),
    ('BLOB',      'PostgreSQL', 'BYTEA', NULL),
    ('BYTEA',     'PostgreSQL', 'BYTEA', NULL),
    ('BINARY',    'PostgreSQL', 'BYTEA', NULL),
    ('VARBINARY', 'PostgreSQL', 'BYTEA', NULL),
    ('BOOLEAN',   'PostgreSQL', 'BOOLEAN', NULL),
    ('BOOL',      'PostgreSQL', 'BOOLEAN', NULL),
    ('BIT',       'PostgreSQL', 'BOOLEAN', NULL),
    -- MariaDB
    ('VARCHAR',   'MariaDB', 'VARCHAR({len})', 255),
    ('VARCHAR2',  'MariaDB', 'VARCHAR({len})', 255),
    ('NVARCHAR',  'MariaDB', 'VARCHAR({len})', 255),
    ('NVARCHAR2', 'MariaDB', 'VARCHAR({len})', 255),
    ('CHAR',      'MariaDB', 'CHAR({len})', 1),
    ('NCHAR',     'MariaDB', 'CHAR({len})', 1),
    ('NUMBER',    'MariaDB', 'DECIMAL[({len}[,{dec}])]', NULL),
    ('NUMERIC',   'MariaDB', 'DECIMAL[({len}[,{dec}])]', NULL),
    ('DECIMAL',   'MariaDB', 'DECIMAL[({len}[,{dec}])]', NULL),
    ('INT',       'MariaDB', 'INT', NULL),
    ('INTEGER',   'MariaDB', 'INT', NULL),
    ('BIGINT',    'MariaDB', 'BIGINT', NULL),
    ('SMALLINT',  'MariaDB', 'SMALLINT', NULL),
    ('TINYINT',   'MariaDB', 'TINYINT', NULL),
    ('FLOAT',     'MariaDB', 'FLOAT', NULL),
    ('REAL',      'MariaDB', 'DOUBLE', NULL),
    ('DOUBLE',    'MariaDB', 'DOUBLE', NULL),
    ('DATE',      'MariaDB', 'DATE', NULL),
    ('DATETIME',  'MariaDB', 'DATETIME', NULL),
    ('TIME',      'MariaDB', 'TIME', NULL),
    ('TIMESTAMP', 'MariaDB', 'DATETIME(6)', NULL),
    ('TEXT',      'MariaDB', 'LONGTEXT', NULL),
    ('CLOB',      'MariaDB', 'LONGTEXT', NULL),
    ('NCLOB',     'MariaDB', 'LONGTEXT', NULL),
    ('BLOB',      'MariaDB', 'LONGBLOB', NULL),
    ('BYTEA',     'MariaDB', 'LONGBLOB', NULL),
    ('BINARY',    'MariaDB', 'BINARY({len})', 1),
    ('VARBINARY', 'MariaDB', 'VARBINARY({len})', 255),
    ('BOOLEAN',   'MariaDB', 'TINYINT(1)', NULL),
    ('BOOL',      'MariaDB', 'TINYINT(1)', NULL),
    ('BIT',       'MariaDB', 'TINYINT(1)', NULL),
    -- SQL Server (TIMESTAMP 는 SQL Server 에서 행 버전이라 DATETIME2 로)
    ('VARCHAR',   'SQLServer', 'VARCHAR({len})', 255),
    ('VARCHAR2',  'SQLServer', 'VARCHAR({len})', 255),
    ('NVARCHAR',  'SQLServer', 'NVARCHAR({len})', 255),
    ('NVARCHAR2', 'SQLServer', 'NVARCHAR({len})', 255),
    ('CHAR',      'SQLServer', 'CHAR({len})', 1),
    ('NCHAR',     'SQLServer', 'NCHAR({len})', 1),
    ('NUMBER',    'SQLServer', 'NUMERIC[({len}[,{dec}])]', NULL),
    ('NUMERIC',   'SQLServer', 'NUMERIC[({len}[,{dec}])]', NULL),
    ('DECIMAL',   'SQLServer', 'DECIMAL[({len}[,{dec}])]', NULL),
    ('INT',       'SQLServer', 'INT', NULL),
    ('INTEGER',   'SQLServer', 'INT', NULL),
    ('BIGINT',    'SQLServer', 'BIGINT', NULL),
    ('SMALLINT',  'SQLServer', 'SMALLINT', NULL),
    ('TINYINT',   'SQLServer', 'TINYINT', NULL),
    ('FLOAT',     'SQLServer', 'REAL', NULL),
    ('REAL',      'SQLServer', 'REAL', NULL),
    ('DOUBLE',    'SQLServer', 'FLOAT', NULL),
    ('DATE',      'SQLServer', 'DATE', NULL),
    ('DATETIME',  'SQLServer', 'DATETIME2', NULL),
    ('TIME',      'SQLServer', 'TIME', NULL),
    ('TIMESTAMP', 'SQLServer', 'DATETIME2', NULL),
    ('TEXT',      'SQLServer', 'VARCHAR(MAX)', NULL),
    ('CLOB',      'SQLServer', 'VARCHAR(MAX)', NULL),
    ('NCLOB',     'SQLServer', 'NVARCHAR(MAX)', NULL),
    ('BLOB',      'SQLServer', 'VARBINARY(MAX)', NULL),
    ('BYTEA',     'SQLServer', 'VARBINARY(MAX)', NULL),
    ('BINARY',    'SQLServer', 'BINARY({len})', 1),
    ('VARBINARY', 'SQLServer', 'VARBINARY({len})', 255),
    ('BOOLEAN',   'SQLServer', 'BIT', NULL),
    ('BOOL',      'SQLServer', 'BIT', NULL),
    ('BIT',       'SQLServer', 'BIT', NULL)
  ) AS v (data_type, dbms_tp, phys_data_type, dflt_data_len)
ON CONFLICT (data_type, dbms_tp) DO NOTHING;

COMMIT;

-- 되돌리기 (필요할 때만)
-- BEGIN;
-- ALTER TABLE quality.tb_data_model_change_history DROP COLUMN IF EXISTS ddl_dbms_tp;
-- DROP TABLE IF EXISTS quality.tb_sys_conf;
-- DROP TABLE IF EXISTS quality.tb_data_type_map;
-- COMMIT;
