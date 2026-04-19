# 45. 데이터 모델 DDL 생성 기능 설계

**작성일**: 2026-04-19
**상태**: 설계 초안
**관련 화면**: 데이터 표준 > 데이터 모델 > 데이터 모델 현황 (`DSDatamodelStatus.vue`)

---

## 1. 목적

데이터 모델 현황 그리드에서 각 모델 행의 우측에 `DDL 생성` 버튼을 추가한다. 버튼 클릭 시 해당 모델의 테이블·컬럼·제약(필요 시 인덱스) 정의를 DDL(SQL) 파일로 브라우저 다운로드한다.

목적:

- 수집된 모델을 다른 DBMS 환경으로 이식·재현할 때 사용 가능한 DDL 산출물 제공
- 논리 모델(수동 설계)에서도 실제 테이블 생성 SQL 손쉽게 추출

---

## 2. UI 설계

### 2.1 진입점

- 위치: `DSDatamodelStatus.vue` 상단 그리드 마지막 열 (헤더 뒤 한 칸 추가)
- 헤더 텍스트: `DDL` (좁은 너비, 아이콘 기반)
- 각 행: `mdi-file-download` 아이콘 버튼 1개
- 클릭 시: 확인 다이얼로그 없이 즉시 다운로드 (간단 동작)

```
| 데이터 모델명 | 데이터소스 | ... | 구조진단 일치율 | DDL |
|---|---|---|---|---|
| ORDER_DB    | ORACLE19   | ... | 95%            | [↓] |
```

### 2.2 동작

1. 행의 `DDL` 버튼 클릭
2. 프론트 → `GET /api/dm/downloadDdl?dataModelId={id}&dbType={oracle|postgres}`
   - `dbType` 는 일단 모델의 데이터소스 `DS_TP` 값으로 자동 결정. 없으면 기본 `postgres`.
   - (추후 확장: DB 타입 선택 드롭다운 추가)
3. 응답: `Content-Type: application/sql`, `Content-Disposition: attachment; filename="{DM_NM}_{YYYYMMDDHHMMSS}.sql"`
4. 브라우저가 자동 다운로드

### 2.3 예외 처리

- 테이블이 0건 (수집되지 않음 + 수동 등록 안 됨): 다운로드 시 주석만 있는 빈 SQL (`-- 테이블이 없습니다.`) 제공 또는 버튼 비활성화
- 네트워크 실패: sweetalert 에러 팝업

---

## 3. Backend 설계

### 3.1 엔드포인트

```
GET /api/dm/downloadDdl
 Query: dataModelId (String, required), dbType (String, optional; default 'postgres')
 Response: text/plain 혹은 application/sql 바이너리 스트림
```

Controller: `DataModelController` 에 `downloadDdl` 메서드 신설.

### 3.2 데이터 조회

DDL 생성에 필요한 데이터 (`DM_ID` 기준 최신 수집분):

| 테이블 | 용도 |
|---|---|
| `TB_DATA_MODEL` | 모델명(파일명·주석 용) |
| `TB_DATA_MODEL_OBJ` | 테이블 목록 + OBJ_NM_KR, OBJ_COMMENT |
| `TB_DATA_MODEL_ATTR` | 컬럼 목록 + DATA_TYPE, DATA_LEN, DATA_DECIMAL_LEN, NULLABLE_YN, DEFAULT_VAL, ATTR_ORD, ATTR_COMMENT |
| `TB_DATA_MODEL_CONSTRAINT` | PK/UK/FK/CHECK 제약 (있으면) |
| `TB_DATA_MODEL_INDEX` | 인덱스 (옵션, Phase 2) |

조회 전략:

- 최신 `DM_CLCT_ID` 가 있는 모델은 해당 CLCT 기준으로 조회
- CLCT 가 없는 모델 (순수 논리): OBJ/ATTR 는 수동 등록분을 직접 조회 (기존 `selectDataModelObjList`, `selectDataModelAttrListByObj` 활용)

새 Mapper statement (필요 시):
- `selectDataModelDdlData` — 하나의 resultMap 으로 모델+OBJ+ATTR 한 번에 (또는 기존 3개 statement 재사용)
- `selectDataModelConstraintListByDmId` — DM_ID 기준 제약 조회 (기존 clct 기준 쿼리 변형)

### 3.3 DDL 생성 로직

`DdlGenerator` 서비스 클래스 신설 (`qualitycenter.service.DdlGenerator`).

```java
public class DdlGenerator {
    public String generate(String dataModelId, String dbType) {
        StdDataModelVo model = fetchModel(dataModelId);
        List<StdDataModelObjVo> objs = fetchObjs(dataModelId);
        List<StdDataModelAttrVo> attrs = fetchAttrs(dataModelId);
        List<ConstraintVo> constraints = fetchConstraints(dataModelId);

        StringBuilder sb = new StringBuilder();
        appendHeaderComment(sb, model);
        for (StdDataModelObjVo obj : objs) {
            appendCreateTable(sb, obj, attrsOf(attrs, obj), constraintsOf(constraints, obj), dbType);
            appendTableComment(sb, obj, dbType);
            appendColumnComments(sb, obj, attrsOf(attrs, obj), dbType);
        }
        return sb.toString();
    }
}
```

### 3.4 DBMS 별 방언(Dialect)

| 항목 | Oracle | PostgreSQL | 비고 |
|---|---|---|---|
| 식별자 인용 | 기본 없음 (대문자) | 기본 없음 (소문자) | 예약어 충돌 시 `"..."` |
| VARCHAR | `VARCHAR2(n CHAR)` | `VARCHAR(n)` | |
| NUMERIC | `NUMBER(p,s)` | `NUMERIC(p,s)` | |
| DATE | `DATE` | `DATE` | |
| TIMESTAMP | `TIMESTAMP` | `TIMESTAMP` | |
| CHAR | `CHAR(n)` | `CHAR(n)` | |
| 테이블 코멘트 | `COMMENT ON TABLE X IS '...'` | 동일 | |
| 컬럼 코멘트 | `COMMENT ON COLUMN X.Y IS '...'` | 동일 | |
| PK | `CONSTRAINT PK_X PRIMARY KEY (...)` | 동일 | |
| FK | `CONSTRAINT FK_X FOREIGN KEY (...) REFERENCES ...` | 동일 | |

초기 지원: **Oracle, PostgreSQL 2종**. 추후 MySQL 등 추가.

내부 인터페이스:

```java
interface SqlDialect {
    String quoteIdentifier(String name);
    String columnType(String dataType, Integer len, Integer decimalLen);
    String commentOnTable(String tableName, String comment);
    String commentOnColumn(String tableName, String columnName, String comment);
}
class OracleDialect implements SqlDialect { ... }
class PostgresDialect implements SqlDialect { ... }
```

### 3.5 출력 예시 (PostgreSQL)

```sql
-- ====================================================
-- 데이터 모델: ORDER_DB (DM_ID: abc123)
-- 생성일시: 2026-04-19 23:45:10
-- DB 타입: postgres
-- 총 테이블: 3, 총 컬럼: 27
-- ====================================================

CREATE TABLE TB_ORDER (
    ORDER_ID       VARCHAR(20)    NOT NULL,
    CUST_ID        VARCHAR(20)    NOT NULL,
    ORDER_DT       TIMESTAMP      NOT NULL,
    AMOUNT         NUMERIC(15,2)  DEFAULT 0,
    STATUS_CD      CHAR(2),
    CONSTRAINT PK_TB_ORDER PRIMARY KEY (ORDER_ID)
);
COMMENT ON TABLE TB_ORDER IS '주문';
COMMENT ON COLUMN TB_ORDER.ORDER_ID IS '주문번호';
COMMENT ON COLUMN TB_ORDER.CUST_ID IS '고객번호';
...

CREATE TABLE TB_ORDER_ITEM (
    ...
    CONSTRAINT PK_TB_ORDER_ITEM PRIMARY KEY (ORDER_ID, ITEM_SEQ),
    CONSTRAINT FK_TB_ORDER_ITEM_ORDER FOREIGN KEY (ORDER_ID)
        REFERENCES TB_ORDER (ORDER_ID)
);
...
```

---

## 4. 영향 범위

### 4.1 Backend

| 파일 | 변경 |
|---|---|
| `DataModelController.java` | `downloadDdl` 엔드포인트 추가 (GET, 스트림 응답) |
| `qualitycenter.service.DdlGenerator` | 신설 |
| `qualitycenter.service.dialect.SqlDialect` + `OracleDialect`, `PostgresDialect` | 신설 |
| `datamodel.xml` | (필요 시) `selectDataModelConstraintListByDmId` 추가. 기존 `selectDataModelObjList` / `selectDataModelAttrList` 재사용 가능 여부 확인 |

### 4.2 Frontend

| 파일 | 변경 |
|---|---|
| `DSDatamodelStatus.vue` | `dataModelHeaders` 마지막에 `DDL` 열 추가. 행 템플릿에 다운로드 아이콘 버튼 + 클릭 핸들러 `downloadDdl(item)` 추가 |

프론트 다운로드 처리:
```js
downloadDdl(item) {
    const url = this.$APIURL.base + 'api/dm/downloadDdl'
              + '?dataModelId=' + encodeURIComponent(item.dataModelId);
    window.location.href = url;
}
```

### 4.3 DDL

없음 (신규 테이블·컬럼 없음).

---

## 5. 단계적 구현

### Phase A (MVP)
1. Backend: DdlGenerator + PostgresDialect 1종
2. 컬럼 정의 + PK 만 생성 (FK/UK/인덱스 제외)
3. 테이블/컬럼 코멘트 포함
4. 프론트: 버튼 추가 + 다운로드 트리거

### Phase B
5. OracleDialect 추가 및 DB 타입 자동 판정 (모델의 DS_TP)
6. FK/UK CHECK 제약 포함

### Phase C
7. 인덱스 생성 구문 추가 (`CREATE INDEX ...`)
8. 사용자 옵션 다이얼로그 (DB 타입 수동 선택, 인덱스/제약 포함 여부 체크박스)

---

## 6. 결정 필요 항목

- [ ] DB 타입 자동 판정 vs 선택 UI? (MVP 는 자동)
- [ ] 식별자 대소문자 정책: 원문 보존? 대문자 강제? (권장: 원문 보존)
- [ ] 파일명 규칙 확정: `{DM_NM}_{timestamp}.sql` 로 통일
- [ ] 버튼 접근 권한: 모든 로그인 사용자 vs 특정 권한? (기존 조회 권한과 동일로 판단)
- [ ] 인덱스를 Phase A 부터 넣을지 여부

---

## 7. 참고

- 기존 수집 데이터 구조는 `TB_DATA_MODEL_OBJ` / `TB_DATA_MODEL_ATTR` 이 이미 CREATE TABLE 생성에 필요한 모든 정보(타입/길이/NULL/PK) 를 가지고 있어 추가 스키마 변경 없이 구현 가능.
- 제약/인덱스 정보는 `TB_DATA_MODEL_CONSTRAINT`, `TB_DATA_MODEL_INDEX` 에 별도 보관돼 있으므로 Phase B/C 에서 활용.
