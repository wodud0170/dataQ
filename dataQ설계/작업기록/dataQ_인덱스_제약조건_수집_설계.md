# 인덱스/제약조건 수집 설계

---

## 1. 현재 수집 구조

### 수집 대상 (현재)
| 대상 | 테이블 | 수집 항목 |
|------|--------|----------|
| 테이블 | TB_DATA_MODEL_OBJ | owner, 테이블명, 한글명, 컬럼수, 생성일, 수정일 |
| 컬럼 | TB_DATA_MODEL_ATTR | 테이블명, 컬럼명, 한글명, 타입, 길이, Nullable, PK, FK, 기본값 |

### 미수집 항목
| 대상 | 현재 상태 |
|------|----------|
| 인덱스 | ❌ 미수집 |
| 제약조건 (PK) | ⚠️ 컬럼 단위로 Y/N만 수집 (제약조건명, 복합키 순서 없음) |
| 제약조건 (FK) | ⚠️ 컬럼 단위로 Y/N만 수집 (참조 테이블/컬럼 없음) |
| 제약조건 (UK/CHECK) | ❌ 미수집 |

---

## 2. 추가 수집 대상

### 2.1 인덱스 (TB_DATA_MODEL_INDEX)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| DM_CLCT_ID | VARCHAR(40) PK | 수집 ID |
| DM_ID | VARCHAR(40) | 데이터모델 ID |
| OBJ_OWNER | VARCHAR(100) | 스키마명 |
| TABLE_NM | VARCHAR(200) | 테이블명 |
| INDEX_NM | VARCHAR(200) | 인덱스명 |
| INDEX_TYPE | VARCHAR(50) | NORMAL, BITMAP, UNIQUE 등 |
| UNIQUENESS | VARCHAR(10) | UNIQUE / NONUNIQUE |
| COLUMN_NM | VARCHAR(200) | 인덱스 구성 컬럼명 |
| COLUMN_POS | INTEGER | 컬럼 순서 |
| SORT_ORDER | VARCHAR(10) | ASC / DESC |
| TABLESPACE_NM | VARCHAR(100) | 테이블스페이스 |

### 2.2 제약조건 (TB_DATA_MODEL_CONSTRAINT)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| DM_CLCT_ID | VARCHAR(40) PK | 수집 ID |
| DM_ID | VARCHAR(40) | 데이터모델 ID |
| OBJ_OWNER | VARCHAR(100) | 스키마명 |
| TABLE_NM | VARCHAR(200) | 테이블명 |
| CONSTRAINT_NM | VARCHAR(200) | 제약조건명 |
| CONSTRAINT_TYPE | VARCHAR(10) | P(PK), R(FK), U(UK), C(CHECK) |
| COLUMN_NM | VARCHAR(200) | 제약조건 구성 컬럼명 |
| COLUMN_POS | INTEGER | 컬럼 순서 (복합키) |
| REF_OWNER | VARCHAR(100) | FK 참조 스키마 |
| REF_TABLE_NM | VARCHAR(200) | FK 참조 테이블 |
| REF_COLUMN_NM | VARCHAR(200) | FK 참조 컬럼 |
| DELETE_RULE | VARCHAR(20) | FK 삭제 규칙 (CASCADE, SET NULL 등) |
| STATUS | VARCHAR(10) | ENABLED / DISABLED |

---

## 3. DBMS별 수집 SQL

### 3.1 Oracle 인덱스

```sql
SELECT
    I.OWNER         AS objOwner,
    I.TABLE_NAME    AS tableNm,
    I.INDEX_NAME    AS indexNm,
    I.INDEX_TYPE    AS indexType,
    I.UNIQUENESS    AS uniqueness,
    IC.COLUMN_NAME  AS columnNm,
    IC.COLUMN_POSITION AS columnPos,
    IC.DESCEND      AS sortOrder,
    I.TABLESPACE_NAME AS tablespaceNm
FROM ALL_INDEXES I
JOIN ALL_IND_COLUMNS IC
    ON I.OWNER = IC.INDEX_OWNER
    AND I.INDEX_NAME = IC.INDEX_NAME
WHERE UPPER(I.OWNER) = :owner
ORDER BY I.TABLE_NAME, I.INDEX_NAME, IC.COLUMN_POSITION
```

### 3.2 Oracle 제약조건

```sql
SELECT
    C.OWNER             AS objOwner,
    C.TABLE_NAME        AS tableNm,
    C.CONSTRAINT_NAME   AS constraintNm,
    C.CONSTRAINT_TYPE   AS constraintType,
    CC.COLUMN_NAME      AS columnNm,
    CC.POSITION         AS columnPos,
    R.OWNER             AS refOwner,
    R.TABLE_NAME        AS refTableNm,
    RC.COLUMN_NAME      AS refColumnNm,
    C.DELETE_RULE        AS deleteRule,
    C.STATUS            AS status
FROM ALL_CONSTRAINTS C
JOIN ALL_CONS_COLUMNS CC
    ON C.OWNER = CC.OWNER
    AND C.CONSTRAINT_NAME = CC.CONSTRAINT_NAME
LEFT JOIN ALL_CONSTRAINTS R
    ON C.R_OWNER = R.OWNER
    AND C.R_CONSTRAINT_NAME = R.CONSTRAINT_NAME
LEFT JOIN ALL_CONS_COLUMNS RC
    ON R.OWNER = RC.OWNER
    AND R.CONSTRAINT_NAME = RC.CONSTRAINT_NAME
    AND CC.POSITION = RC.POSITION
WHERE UPPER(C.OWNER) = :owner
    AND C.CONSTRAINT_TYPE IN ('P', 'R', 'U', 'C')
ORDER BY C.TABLE_NAME, C.CONSTRAINT_TYPE, C.CONSTRAINT_NAME, CC.POSITION
```

### 3.3 PostgreSQL 인덱스

```sql
SELECT
    schemaname      AS objOwner,
    tablename       AS tableNm,
    indexname        AS indexNm,
    indexdef         AS indexDef
FROM pg_indexes
WHERE schemaname = :owner
ORDER BY tablename, indexname
```

### 3.4 PostgreSQL 제약조건

```sql
SELECT
    tc.table_schema     AS objOwner,
    tc.table_name       AS tableNm,
    tc.constraint_name  AS constraintNm,
    tc.constraint_type  AS constraintType,
    kcu.column_name     AS columnNm,
    kcu.ordinal_position AS columnPos,
    ccu.table_schema    AS refOwner,
    ccu.table_name      AS refTableNm,
    ccu.column_name     AS refColumnNm
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
LEFT JOIN information_schema.constraint_column_usage ccu
    ON tc.constraint_name = ccu.constraint_name
    AND tc.table_schema = ccu.table_schema
    AND tc.constraint_type = 'FOREIGN KEY'
WHERE tc.table_schema = :owner
ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name, kcu.ordinal_position
```

---

## 4. 수집 프로세스 변경

### 현재 프로세스
```
1. TB_DATA_MODEL_CLCT 시작
2. OBJ (테이블) 수집 → TB_DATA_MODEL_OBJ
3. ATTR (컬럼) 수집 → TB_DATA_MODEL_ATTR
4. STATS (통계) 저장 → TB_DATA_MODEL_STATS
5. TB_DATA_MODEL_CLCT 완료
```

### 변경 후 프로세스
```
1. TB_DATA_MODEL_CLCT 시작
2. OBJ (테이블) 수집 → TB_DATA_MODEL_OBJ
3. ATTR (컬럼) 수집 → TB_DATA_MODEL_ATTR
4. INDEX (인덱스) 수집 → TB_DATA_MODEL_INDEX      ← 추가
5. CONSTRAINT (제약조건) 수집 → TB_DATA_MODEL_CONSTRAINT  ← 추가
6. STATS (통계) 저장 → TB_DATA_MODEL_STATS
7. TB_DATA_MODEL_CLCT 완료
```

---

## 5. 화면 설계

### 5.1 데이터 모델 > 테이블 상세 (기존 화면 확장)

현재 테이블 선택 시 컬럼 목록만 표시. 탭을 추가:

```
[컬럼] [인덱스] [제약조건]
```

- **컬럼 탭**: 기존 컬럼 목록 (변경 없음)
- **인덱스 탭**: 해당 테이블의 인덱스 목록 + 구성 컬럼
- **제약조건 탭**: 해당 테이블의 PK/FK/UK/CHECK + FK 참조 정보

### 5.2 구조 변경 진단 확장

인덱스/제약조건 변경도 감지:
- 인덱스 추가/삭제/변경
- 제약조건 추가/삭제/변경

### 5.3 데이터 모델 현황 통계 확장

| 현재 | 추가 |
|------|------|
| 테이블 수 | 인덱스 수 |
| 컬럼 수 | 제약조건 수 |

---

## 6. 수집 쿼리 등록 위치

### Named Query (dm-collect.xml)
```
OracleGetIndexes    → Oracle 인덱스 수집
OracleGetConstraints → Oracle 제약조건 수집
PostgreSQLGetIndexes → PostgreSQL 인덱스 수집
PostgreSQLGetConstraints → PostgreSQL 제약조건 수집
MariaDBGetIndexes   → MariaDB 인덱스 수집
MariaDBGetConstraints → MariaDB 제약조건 수집
```

---

## 7. 작업 순서

| 순서 | 작업 | 예상 |
|------|------|------|
| 1 | DB 테이블 생성 (INDEX, CONSTRAINT) | 10분 |
| 2 | Named Query 추가 (Oracle/PostgreSQL/MariaDB) | 30분 |
| 3 | DataModelService 수집 로직 추가 | 30분 |
| 4 | MyBatis 매퍼 추가 (INSERT/SELECT) | 20분 |
| 5 | 프론트 테이블 상세 탭 추가 | 1시간 |
| 6 | 구조 변경 진단 확장 | 1시간 |
| 7 | 통계 화면 반영 | 20분 |
