# 72. Cubrid 11.x — 시스템 카탈로그 + SQL Dialect 정리 (학습 노트)

작성일: 2026-05-01
대상 환경: Cubrid 11.2 testdb (dba 계정)
목적: dataQ 의 메타 수집 (GetObjs/GetAttrs/GetIndexes/GetConstraints) 누락 케이스 + RuleSqlBuilder/ValueProfileService 의 dialect 분기 보강

---

## 0. 가장 핵심 차이 한 장 요약

| 영역 | Oracle/PostgreSQL | Cubrid |
|---|---|---|
| 시스템 catalog | `ALL_TABLES` / `information_schema` | **`db_class`, `db_attribute`, `db_index`, `db_index_key`, `db_user`, `db_serial`, `db_partition`** |
| identifier 기본 표기 | 대문자 | **소문자** (single-quote 로 묶이면 그대로) |
| TEXT 타입 | `TEXT` | **없음 — VARCHAR/CHAR + STRING(=VARCHAR(1G))** |
| BIGINT/INT | INT, BIGINT, NUMERIC | INTEGER, BIGINT, NUMERIC (호환) |
| REGEX 연산자 | PG `~`, Oracle `REGEXP_LIKE` | **`col REGEXP 'p'`** (MySQL 호환 syntax) |
| SAMPLING | TABLESAMPLE / SAMPLE | **미지원** — `ORDER BY RAND() LIMIT n` 만 |
| LIMIT | LIMIT / FETCH FIRST | **`LIMIT n`** 표준 |
| DATETIME 리터럴 | `TIMESTAMP '...'` | **`DATETIME '...'`** (또는 `TIMESTAMP` 도 호환) |
| DUAL | 사용 가능 (Oracle) | `db_root` (`SELECT 1+1 FROM db_root;`) |
| PL/SQL `BEGIN..EXCEPTION` | Oracle | 미지원 — DROP IF EXISTS 사용 |
| `INSERT ALL ... SELECT FROM DUAL` | Oracle | 미지원 — multi-row VALUES `INSERT INTO t VALUES (..),(..),...` |
| boolean | 0/1 또는 `t`/`f` | 0/1 (Cubrid 11+ BOOLEAN 도입) |

---

## 1. 시스템 카탈로그 핵심 6 테이블

### 1.1 `db_class` — 테이블/뷰 메타
```sql
SELECT class_name, owner_name, class_type, is_system_class, partitioned, sub_classes
  FROM db_class
 WHERE is_system_class='NO' AND class_type='CLASS' AND owner_name='DBA';
```
| 컬럼 | 의미 |
|---|---|
| class_name | 테이블/뷰 이름 (lowercase) |
| owner_name | 소유자 (= 사용자, dba/PUBLIC) |
| class_type | `CLASS`(테이블) / `VCLASS`(뷰) |
| is_system_class | `'YES'`/`'NO'` (소문자 유의) |
| partitioned | `'YES'`/`'NO'` |
| sub_classes | 상속 자식 |

### 1.2 `db_attribute` — 컬럼 메타 (가장 중요)
```sql
SELECT class_name, attr_name,
       data_type,                 -- 'STRING','INTEGER','DATETIME','NUMERIC','DATE'
       prec,                      -- 길이 (VARCHAR 의 n, NUMERIC 의 p)
       scale,                     -- NUMERIC 의 소수점
       is_nullable,               -- 'YES'/'NO'
       def_order,                 -- 컬럼 순서
       default_value,
       from_class_name            -- 상속 컬럼이면 부모 class
  FROM db_attribute
 WHERE class_name='tb_test_member' AND from_class_name IS NULL
 ORDER BY def_order;
```

**주의**: `data_type` 이 표준 SQL 타입과 다름 — Cubrid 내부 표기:
- `STRING` ↔ VARCHAR
- `CHAR` ↔ CHAR
- `INTEGER` / `BIGINT` / `SMALLINT`
- `NUMERIC` / `FLOAT` / `DOUBLE`
- `DATE` / `TIME` / `DATETIME` / `TIMESTAMP`
- `BIT` / `BIT VARYING`
- `OBJECT` (참조 타입)
- `SET_OF(t)` / `MULTISET_OF` / `SEQUENCE_OF` (집합 타입)

### 1.3 `db_index` — 인덱스 + PK 정보
```sql
SELECT class_name, index_name,
       is_unique,                 -- 'YES'/'NO'
       is_primary_key,            -- 'YES'/'NO'
       is_foreign_key,            -- 'YES'/'NO'
       key_count                  -- 키 컬럼 수
  FROM db_index
 WHERE class_name='tb_test_member';
```

### 1.4 `db_index_key` — 인덱스의 키 컬럼들
```sql
SELECT index_name, class_name, key_attr_name, key_order, asc_desc
  FROM db_index_key
 WHERE class_name='tb_test_member'
 ORDER BY index_name, key_order;
```

### 1.5 `db_user` — 사용자/스키마
```sql
SELECT name, comment FROM db_user;
-- DBA, PUBLIC + 사용자 정의
```

### 1.6 `db_serial` — 시퀀스
```sql
SELECT name, owner.name AS owner, current_val, increment_val
  FROM db_serial;
```

---

## 2. dataQ 메타 수집 — Cubrid 호환 SQL 초안

### 2.1 GetObjs (테이블 목록)
**Oracle 케이스 반환 컬럼 (추정)**: owner / tableNm / tableComment

```sql
-- Cubrid
SELECT owner_name AS owner,
       class_name AS tableNm,
       NULL       AS tableComment
  FROM db_class
 WHERE is_system_class = 'NO'
   AND class_type      = 'CLASS'
   AND owner_name      = :owner
 ORDER BY class_name;
```

### 2.2 GetAttrs (컬럼 목록)
**예상 반환 alias** (StructDiagService 코드 + 다른 DBMS 케이스 추정):
`owner / tableNm / columnNm / dataType / dataLen / dataDecimalLen / nullableYn / defaultVal / pkYn`

```sql
-- Cubrid
SELECT a.from_class_name             AS srcOwner,    -- usually NULL
       c.owner_name                  AS owner,
       a.class_name                  AS tableNm,
       a.attr_name                   AS columnNm,
       -- Cubrid data_type 을 표준 명으로 정규화
       CASE a.data_type
            WHEN 'STRING'   THEN 'VARCHAR'
            WHEN 'INTEGER'  THEN 'INTEGER'
            WHEN 'BIGINT'   THEN 'BIGINT'
            WHEN 'SMALLINT' THEN 'SMALLINT'
            WHEN 'NUMERIC'  THEN 'NUMERIC'
            WHEN 'FLOAT'    THEN 'FLOAT'
            WHEN 'DOUBLE'   THEN 'DOUBLE'
            WHEN 'DATE'     THEN 'DATE'
            WHEN 'TIME'     THEN 'TIME'
            WHEN 'DATETIME' THEN 'DATETIME'
            WHEN 'TIMESTAMP' THEN 'TIMESTAMP'
            WHEN 'CHAR'     THEN 'CHAR'
            WHEN 'BIT'      THEN 'BIT'
            ELSE a.data_type
       END                           AS dataType,
       a.prec                        AS dataLen,
       a.scale                       AS dataDecimalLen,
       CASE WHEN a.is_nullable='YES' THEN 'Y' ELSE 'N' END AS nullableYn,
       a.default_value               AS defaultVal,
       CASE WHEN i.is_primary_key='YES' THEN 'Y' ELSE 'N' END AS pkYn
  FROM db_attribute a
  JOIN db_class c
    ON c.class_name = a.class_name
  LEFT JOIN db_index_key ik
    ON ik.class_name = a.class_name AND ik.key_attr_name = a.attr_name
  LEFT JOIN db_index i
    ON i.class_name = ik.class_name AND i.index_name = ik.index_name
       AND i.is_primary_key = 'YES'
 WHERE c.is_system_class = 'NO'
   AND c.class_type      = 'CLASS'
   AND c.owner_name      = :owner
 ORDER BY a.class_name, a.def_order;
```

### 2.3 GetIndexes
```sql
-- Cubrid
SELECT i.class_name      AS tableNm,
       i.index_name      AS indexNm,
       CASE WHEN i.is_unique='YES'      THEN 'Y' ELSE 'N' END AS uniqueYn,
       CASE WHEN i.is_primary_key='YES' THEN 'Y' ELSE 'N' END AS pkYn,
       ik.key_attr_name  AS columnNm,
       ik.key_order      AS columnOrd,
       ik.asc_desc       AS sortOrder
  FROM db_index i
  JOIN db_index_key ik
    ON ik.class_name = i.class_name AND ik.index_name = i.index_name
  JOIN db_class c
    ON c.class_name = i.class_name AND c.owner_name = :owner
 WHERE c.is_system_class = 'NO'
 ORDER BY i.class_name, i.index_name, ik.key_order;
```

### 2.4 GetConstraints (FK + UNIQUE + CHECK)
Cubrid 의 제약조건은 인덱스로 표현됨:
- PK: `db_index.is_primary_key='YES'`
- UNIQUE: `db_index.is_unique='YES' AND is_primary_key='NO'`
- FK: `db_index.is_foreign_key='YES'` + `db_partition` 등 보조 + 별도 시스템 카탈로그 부족

```sql
-- Cubrid (PK + UNIQUE만 — FK 정확 추출은 복잡)
SELECT i.class_name      AS tableNm,
       i.index_name      AS constraintNm,
       CASE
         WHEN i.is_primary_key='YES' THEN 'PK'
         WHEN i.is_unique='YES'      THEN 'UNIQUE'
         WHEN i.is_foreign_key='YES' THEN 'FK'
         ELSE 'OTHER'
       END               AS constraintType,
       ik.key_attr_name  AS columnNm,
       ik.key_order      AS columnOrd
  FROM db_index i
  JOIN db_index_key ik
    ON ik.class_name = i.class_name AND ik.index_name = i.index_name
  JOIN db_class c
    ON c.class_name = i.class_name AND c.owner_name = :owner
 WHERE c.is_system_class = 'NO'
   AND (i.is_primary_key='YES' OR i.is_unique='YES' OR i.is_foreign_key='YES')
 ORDER BY i.class_name, i.index_name, ik.key_order;
```

> FK 의 reference table/column 은 별도 카탈로그 (`db_index` + `db_index_key` 만으로는 한계). FK 본격 지원이 필요하면 `pragma` 또는 `SHOW CREATE TABLE` 같은 metadata 파싱이 더 안정적.

---

## 3. SQL Dialect 차이 — RuleSqlBuilder/ValueProfileService 적용 가이드

### 3.1 정규식 (REGEX 룰)
```sql
-- Oracle:    REGEXP_LIKE(col, '^...$')
-- PG:        col ~ '^...$'
-- Cubrid:    col REGEXP '^...$'                   ★ MySQL 호환
-- MSSQL:     LIKE 만 — 정규식 미지원
```
Cubrid 의 `REGEXP` 는 POSIX 호환. `\d` 미지원이라 `[0-9]` 사용 필요. (POSIX class `[[:digit:]]`)

### 3.2 길이 함수
```sql
-- LENGTH(col)         — Cubrid 표준 (byte 가 아니라 char 길이)
-- BIT_LENGTH / OCTET_LENGTH 도 지원
-- CHAR_LENGTH 동일
```
**fix 불필요** — default 분기의 `LENGTH` 가 그대로 동작.

### 3.3 캐스팅 (값 진단 의 toText)
```sql
-- PG default:    CAST(col AS TEXT)               ← Cubrid TEXT 미지원
-- Cubrid:        CAST(col AS VARCHAR(200))       ★
-- Oracle:        TO_CHAR(col)
-- MSSQL:         CAST(col AS NVARCHAR(200))
```

### 3.4 datetime 리터럴
```sql
-- 표준 SQL:    DATE '2025-01-01' / TIMESTAMP '2025-01-01 00:00:00'
-- Cubrid:      DATETIME '2025-01-01 00:00:00'    ← 정밀도(milli) 반영
-- TIMESTAMP 도 호환 (단, 1970-2038 unix epoch 범위 제한)
```

### 3.5 sampling
```sql
-- PG:          TABLESAMPLE BERNOULLI(1)
-- Oracle:      SAMPLE(1)
-- MSSQL:       TABLESAMPLE (1 PERCENT)
-- Cubrid:      TABLESAMPLE 미지원
--              차선책: SELECT * FROM t ORDER BY RAND() LIMIT N    (full-scan 후 정렬 — 큰 테이블 비추)
```
**fix 결과**: default(빈 string = 풀스캔) 적용 — 데이터 적은 테스트에는 OK.

### 3.6 LIMIT / pagination
```sql
-- Cubrid:      SELECT * FROM t LIMIT 10
--              SELECT * FROM t LIMIT 10, 20         ← (offset 10, count 20)
-- 또는 OFFSET 키워드:  LIMIT 20 OFFSET 10
```
표준에 가깝게 LIMIT 사용 → `RuleSqlBuilder.limitClause()` 의 default 분기가 OK.

### 3.7 PostgreSQL 식 multi-row VALUES
```sql
-- Cubrid 11+ : INSERT INTO t VALUES (..), (..), (..)    ★ 지원
```
→ Oracle 의 `INSERT ALL ... SELECT FROM DUAL` 변환 불필요. PG 시드 그대로 통한 이유.

---

## 4. dataQ 통합 시 추가 작업

### 4.1 메타 query NamedQuery 등록 — **위치 확인됨**
- `createNamedQuery("CubridGetObjs")` 형태로 호출됨 (DataModelService.java)
- 등록 위치: **`q-executor/src/main/resources/META-INF/dm-collect.xml`**
- 형식: `<named-native-query name="CubridGetObjs"><query><![CDATA[...]]></query></named-native-query>`

**Cubrid 정의 현황** (2026-05-01 점검):

| Query | 상태 | 비고 |
|---|---|---|
| `CubridGetObjs` | ✅ 정의 | DB_CLASS + count(attr) |
| `CubridGetAttrs` | ✅ 정의 | DB_ATTRIBUTE + DB_INDEX + DB_INDEX_KEY join, STRING→VARCHAR 캐스팅, pkYn/fkYn 산출 |
| `CubridGetIndexes` | ❌ 미정의 | STRUCT 진단 시 인덱스 변경 감지 불가 |
| `CubridGetConstraints` | ❌ 미정의 | STRUCT 진단 시 제약조건 변경 감지 불가 |

→ §2.1/§2.2 의 "신규 작성" 표기는 잘못된 평가. **기존 SQL 그대로 살아있고 사용 가능**. §2.3 (Indexes), §2.4 (Constraints) 만 **신규 추가 필요**.

기존 CubridGetAttrs 의 SQL (참고):
```sql
SELECT A.CLASS_NAME AS objNm,
       B.ATTR_NAME  AS attrNm,
       B.COMMENT    AS attrNmKr,
       CASE WHEN B.DATA_TYPE='STRING' THEN 'VARCHAR' ELSE B.DATA_TYPE END AS dataType,
       B.PREC       AS dataLen,
       B.SCALE      AS dataDecimalLen,
       CASE WHEN B.IS_NULLABLE='YES' THEN 'Y' END AS nullableYn,
       CASE WHEN D.KEY_ATTR_NAME IS NOT NULL THEN 'Y' END AS pkYn,
       CASE WHEN F.KEY_ATTR_NAME IS NOT NULL THEN 'Y' END AS fkYn,
       B.DEFAULT_VALUE AS defaultVal,
       B.DEF_ORDER + 1 AS attrOrder
  FROM DB_CLASS A
  INNER JOIN DB_ATTRIBUTE B ON B.CLASS_NAME = A.CLASS_NAME
  LEFT  JOIN DB_INDEX     C ON C.IS_PRIMARY_KEY='YES' AND C.CLASS_NAME = B.CLASS_NAME
  LEFT  JOIN DB_INDEX_KEY D ON D.CLASS_NAME = C.CLASS_NAME AND D.INDEX_NAME = C.INDEX_NAME
                            AND D.KEY_ATTR_NAME = B.ATTR_NAME
  LEFT  JOIN DB_INDEX     E ON E.IS_FOREIGN_KEY='YES' AND E.CLASS_NAME = B.CLASS_NAME
  LEFT  JOIN DB_INDEX_KEY F ON F.CLASS_NAME = E.CLASS_NAME AND F.INDEX_NAME = E.INDEX_NAME
                            AND F.KEY_ATTR_NAME = B.ATTR_NAME
 WHERE A.is_system_class='NO' AND A.CLASS_TYPE='CLASS'
   AND UPPER(A.OWNER_NAME) = :owner
 GROUP BY objNm, attrNm
 ORDER BY objNm, attrOrder
```
→ §2.2 의 학습용 초안과 동일 골격. 기존 코드 활용.

### 4.2 dialect 함수 추가 보강 (RuleSqlBuilder)
이미 fix 완료:
- `regexMatch` — Cubrid 의 `REGEXP`
- `literalTimestamp` — Cubrid 의 `DATETIME '...'`
- `sampleClause` — 빈 string (default) 으로 풀스캔
- `limitClause` — default 의 `LIMIT n`

미보강:
- `quoteId` — Cubrid 의 backtick `` ` `` 식별자 quoting (대소문자 구분 필요시) — 현재는 unquoted
- `concat` — Cubrid 표준 `CONCAT(a,b)` 또는 `||` 둘 다 지원

### 4.3 데이터소스 등록 jasypt 암호화
- 직접 INSERT 시 `EncryptionOperationNotPossibleException` 발생
- `/api/sysinfo/createDataSource` API 통해 등록 → 자동 암호화
- 또는 jasypt 의 secret key 알아내서 직접 암호화 (비추 — 보안)

---

## 5. 빠른 csql 명령 모음 (학습용)

```bash
# 컨테이너 안에서 csql 진입
docker exec -it cubrid-test bash
csql -u dba testdb

# 한 번에 SQL 실행
docker exec cubrid-test bash -c "csql -u dba testdb -c 'SELECT 1 FROM db_root;'"

# 파일 실행
docker cp my.sql cubrid-test:/tmp/my.sql
docker exec cubrid-test bash -c "csql -u dba testdb -i /tmp/my.sql"

# 시스템 카탈로그 살펴보기
SELECT class_name FROM db_class WHERE class_name LIKE 'db\_%' ESCAPE '\' ORDER BY class_name;
-- 결과: db_attribute, db_class, db_index, db_index_key, db_partition,
--       db_serial, db_user, db_view, db_trig, db_method, db_meth_arg, ...
```

---

## 6. 참고 링크 (외부)

- 시스템 카탈로그: https://www.cubrid.org/manual/en/11.2/sql/catalog.html
- SQL syntax: https://www.cubrid.org/manual/en/11.2/sql/index.html
- 정규식: https://www.cubrid.org/manual/en/11.2/sql/function/string_fn.html#regexp
- 데이터 타입: https://www.cubrid.org/manual/en/11.2/sql/datatype.html

---

## 변경 이력
- 2026-05-01 작성 — Cubrid 11.2 testdb 환경에서 직접 catalog 조회 검증 + dataQ 적용 가이드 정리
