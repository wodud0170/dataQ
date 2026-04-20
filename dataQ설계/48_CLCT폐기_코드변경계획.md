# CLCT 폐기 코드 변경 계획

**작성일**: 2026-04-20
**기반 문서**: 44_데이터모델_CLCT폐기_재설계.md
**전제**: 9장 결정 항목 확정 후 착수

---

## 결정 필요 항목 (작업 전 확정)

| # | 항목 | 권장 | 비고 |
|---|------|------|------|
| 1 | 사라진 객체 처리 | 소프트 삭제 (USE_YN='N') | 재수집 시 복원 가능 |
| 2 | 논리명(NM_KR) 머지 정책 | 수동 편집 유지 | ATTR_COMMENT에 DB 값 별도 보관 |
| 3 | 과거 CLCT 스냅샷 폐기 | 허용 (백업 후) | 개발 환경 기준 |
| 4 | 물리속성 수동 편집 | 허용 (재수집 시 덮어쓰기 경고) | |
| 5 | TB_DATA_MODEL_STATS | 유지 (DM_CLCT_ID 참조만 수집 로그 용도로 변경) | |

---

## Phase 1: DDL + 이관 스크립트

### 1-1. OBJ/ATTR 테이블 스키마 변경

```sql
-- OBJ: USE_YN, DELETED_DT 추가
ALTER TABLE TB_DATA_MODEL_OBJ ADD COLUMN IF NOT EXISTS USE_YN VARCHAR(1) DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_OBJ ADD COLUMN IF NOT EXISTS DELETED_DT VARCHAR(14);

-- ATTR: USE_YN, DELETED_DT 추가
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN IF NOT EXISTS USE_YN VARCHAR(1) DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN IF NOT EXISTS DELETED_DT VARCHAR(14);
```

### 1-2. 이관 스크립트 (CLCT_ID → DM_ID 전환)

```sql
-- 백업
CREATE TABLE TB_DATA_MODEL_OBJ_BAK AS SELECT * FROM TB_DATA_MODEL_OBJ;
CREATE TABLE TB_DATA_MODEL_ATTR_BAK AS SELECT * FROM TB_DATA_MODEL_ATTR;

-- 최신 CLCT만 남기고 나머지 삭제
DELETE FROM TB_DATA_MODEL_ATTR WHERE DM_CLCT_ID NOT IN (
    SELECT DM_CLCT_ID FROM TB_DATA_MODEL_CLCT C
    WHERE C.CLCT_END_DT = (SELECT MAX(CLCT_END_DT) FROM TB_DATA_MODEL_CLCT WHERE DM_ID = C.DM_ID)
);
DELETE FROM TB_DATA_MODEL_OBJ WHERE DM_CLCT_ID NOT IN (
    SELECT DM_CLCT_ID FROM TB_DATA_MODEL_CLCT C
    WHERE C.CLCT_END_DT = (SELECT MAX(CLCT_END_DT) FROM TB_DATA_MODEL_CLCT WHERE DM_ID = C.DM_ID)
);

-- PK 변경: (DM_CLCT_ID, OBJ_NM) → (DM_ID, OBJ_NM)
-- DM_ID는 이미 존재하는 컬럼
ALTER TABLE TB_DATA_MODEL_OBJ DROP CONSTRAINT IF EXISTS pk_tb_data_model_obj;
ALTER TABLE TB_DATA_MODEL_OBJ ADD CONSTRAINT pk_tb_data_model_obj PRIMARY KEY (DM_ID, OBJ_NM);

ALTER TABLE TB_DATA_MODEL_ATTR DROP CONSTRAINT IF EXISTS pk_tb_data_model_attr;
ALTER TABLE TB_DATA_MODEL_ATTR ADD CONSTRAINT pk_tb_data_model_attr PRIMARY KEY (DM_ID, OBJ_NM, ATTR_NM);
```

### 1-3. INDEX/CONSTRAINT 테이블도 동일 처리

```sql
-- INDEX: 최신 CLCT만 남기고 PK 변경
DELETE FROM TB_DATA_MODEL_INDEX WHERE DM_CLCT_ID NOT IN (...);
ALTER TABLE TB_DATA_MODEL_INDEX DROP CONSTRAINT IF EXISTS pk_tb_data_model_index;
-- PK: (DM_ID, TABLE_NM, INDEX_NM, COLUMN_NM) 또는 (DM_ID, SEQ)

-- CONSTRAINT: 동일
DELETE FROM TB_DATA_MODEL_CONSTRAINT WHERE DM_CLCT_ID NOT IN (...);
ALTER TABLE TB_DATA_MODEL_CONSTRAINT DROP CONSTRAINT IF EXISTS pk_tb_data_model_constraint;
```

### 1-4. CLCT 테이블 용도 변경

```sql
-- 수집 이벤트 로그용 컬럼 추가
ALTER TABLE TB_DATA_MODEL_CLCT ADD COLUMN IF NOT EXISTS ADDED_CNT INTEGER DEFAULT 0;
ALTER TABLE TB_DATA_MODEL_CLCT ADD COLUMN IF NOT EXISTS DELETED_CNT INTEGER DEFAULT 0;
ALTER TABLE TB_DATA_MODEL_CLCT ADD COLUMN IF NOT EXISTS MODIFIED_CNT INTEGER DEFAULT 0;
```

---

## Phase 2: VO 수정 (q-common)

| 파일 | 변경 |
|------|------|
| `StdDataModelObjVo.java` | `clctId` 제거 불가 (기존 호환). `useYn`, `deletedDt` 필드 추가 |
| `StdDataModelAttrVo.java` | 동일 |
| `StdDataModelCollectVo.java` | `addedCnt`, `deletedCnt`, `modifiedCnt` 추가 |

**주의**: `clctId` 필드를 바로 제거하면 기존 코드 전체에 영향. 단계적으로 deprecated 처리 후 제거.

---

## Phase 3: Mapper XML 전수 수정 (q-common)

### 3-1. datamodel.xml — 주요 변경 대상

| SQL ID | 현재 | 변경 |
|--------|------|------|
| `selectDataModelObjListByClctId` | WHERE DM_CLCT_ID = #{clctId} | WHERE DM_ID = #{dataModelId} AND USE_YN = 'Y' |
| `selectDataModelAttrListByClctId` | WHERE A.DM_CLCT_ID = #{clctId} | WHERE A.DM_ID = #{dataModelId} AND A.USE_YN = 'Y' |
| `selectDataModelAttrListByClctIdRaw` | DM_CLCT_ID 기반 JOIN | DM_ID 기반 직접 조회 |
| `insertDataModelObj` | DM_CLCT_ID, DM_ID 둘 다 INSERT | DM_ID만 (DM_CLCT_ID 컬럼 유지하되 선택적) |
| `insertDataModelAttr` | 동일 | 동일 |
| `insertDataModelStats` | DM_CLCT_ID 기반 | DM_CLCT_ID 유지 (수집 로그 참조) |
| `selectDataModelIndexListByClctId` | DM_CLCT_ID 기반 | DM_ID 기반 |
| `selectDataModelConstraintListByClctId` | DM_CLCT_ID 기반 | DM_ID 기반 |

### 3-2. 신규 MERGE 쿼리

```sql
-- 테이블 UPSERT
<insert id="mergeDataModelObj">
    INSERT INTO TB_DATA_MODEL_OBJ (DM_ID, OBJ_NM, OBJ_NM_KR, OBJ_COMMENT, OBJ_OWNER, OBJ_ATTR_CNT, USE_YN)
    VALUES (#{dataModelId}, #{objNm}, #{objNmKr}, #{objComment}, #{objOwner}, #{objAttrCnt}, 'Y')
    ON CONFLICT (DM_ID, OBJ_NM) DO UPDATE SET
        OBJ_COMMENT = EXCLUDED.OBJ_COMMENT,
        OBJ_OWNER = EXCLUDED.OBJ_OWNER,
        OBJ_ATTR_CNT = EXCLUDED.OBJ_ATTR_CNT,
        USE_YN = 'Y',
        DELETED_DT = NULL
        -- OBJ_NM_KR은 기존값 유지 (수동 편집 보존)
</insert>

-- 컬럼 UPSERT
<insert id="mergeDataModelAttr">
    INSERT INTO TB_DATA_MODEL_ATTR (DM_ID, OBJ_NM, ATTR_NM, ATTR_NM_KR, ATTR_COMMENT, ...)
    VALUES (...)
    ON CONFLICT (DM_ID, OBJ_NM, ATTR_NM) DO UPDATE SET
        ATTR_COMMENT = EXCLUDED.ATTR_COMMENT,
        DATA_TYPE = EXCLUDED.DATA_TYPE,
        DATA_LEN = EXCLUDED.DATA_LEN,
        ...
        USE_YN = 'Y',
        DELETED_DT = NULL
        -- ATTR_NM_KR 기존값 유지
</insert>

-- 소프트 삭제 (이번 수집에 없는 행)
<update id="softDeleteMissingObjs">
    UPDATE TB_DATA_MODEL_OBJ SET USE_YN = 'N', DELETED_DT = #{now}
    WHERE DM_ID = #{dataModelId} AND USE_YN = 'Y'
    AND OBJ_NM NOT IN
    <foreach item="nm" collection="collectedObjNames" open="(" separator="," close=")">#{nm}</foreach>
</update>
```

---

## Phase 4: Controller/Service 수정

### 4-1. DataModelController.java (q-center)

| 메서드 | 변경 |
|--------|------|
| `getDataModelObjListByClctId` | `clctId` 파라미터 → `dataModelId`로 변경 (또는 신규 메서드 추가) |
| `getDataModelAttrListByClctId` | 동일 |
| `getDataModelIndexListByClctId` | 동일 |
| `getDataModelConstraintListByClctId` | 동일 |
| `downloadDataModelObjs/Attrs` | `clctId` → `dataModelId` |
| 편집 엔드포인트 (addObj/updateObj 등) | `clctId` 관련 로직 제거, `dataModelId`로 직접 |

### 4-2. DataModelService.java (q-executor)

**핵심 변경**: INSERT 루프 → MERGE 루프

```
현재:
  clctId 생성 → OBJ INSERT → ATTR INSERT → STATS INSERT

변경:
  clctId 생성 (수집 로그용) → OBJ MERGE → ATTR MERGE → 소프트 삭제 → STATS INSERT → CLCT 완료 (카운트 기록)
```

### 4-3. StructDiagService.java (q-executor)

| 현재 | 변경 |
|------|------|
| `selectDataModelAttrListByClctIdRaw` | `selectDataModelAttrListByDmId` (DM_ID 기반, USE_YN='Y') |
| `selectRecentClctIds` | 제거 또는 수집 로그 조회용으로 변경 |

---

## Phase 5: 프론트엔드 수정

### 5-1. 수집일시 드롭다운 제거

| 컴포넌트 | 변경 |
|----------|------|
| `DSDatamodelStatusTable.vue` | `clctList`, `selectedClctId` 제거. `selectedModelId`만으로 조회 |
| `DSDatamodelStatusColumn.vue` | 동일 |
| `DSDatamodelStatusIndex.vue` | 동일 |
| `DSDatamodelStatusConstraint.vue` | 동일 |
| `DSDatamodelCollection.vue` | 수집 이력은 유지 (로그 조회용), 스냅샷 개념 제거 |

### 5-2. 편집 관련

| 컴포넌트 | 변경 |
|----------|------|
| `DSDatamodelStatusTable.vue` | `isLatestClct` 체크 제거, 항상 편집 가능 |
| `DSDatamodelStatusColumn.vue` | 동일 |

### 5-3. 구조 진단

| 컴포넌트 | 변경 |
|----------|------|
| `DSStructDiag.vue` | 수집일시 드롭다운 제거, 모델 선택만으로 진단 실행 |
| `DSStructDiagResult.vue` | 수집일시 기반 필터 → 진단이력 기반 필터로 단순화 |

---

## Phase 6: 구조 진단 영향 확인

| 항목 | 현재 | 변경 후 |
|------|------|---------|
| 수집 스냅샷 vs 현재 DB 비교 | CLCT_ID 기준 스냅샷 로드 | DM_ID 기준 현재 OBJ/ATTR 로드 (USE_YN='Y') |
| 진단 이력의 PREV_COLLECT_DT | CLCT의 수집일시 | 가장 최근 수집 로그(CLCT)의 일시 참조 |
| TB_STRUCT_DIAG_HISTORY | DATA_MODEL_ID 기준 → 영향 없음 | 변경 없음 |
| TB_STRUCT_DIAG_*_DETAIL | DIAG_ID 기준 → 영향 없음 | 변경 없음 |

---

## 작업 순서 (예상 공수)

| # | 작업 | 파일 수 | 비고 |
|---|------|---------|------|
| 1 | DDL + 이관 스크립트 작성 | 1 | 백업 포함 |
| 2 | VO 수정 (useYn, deletedDt 추가) | 3 | q-common |
| 3 | Mapper XML 전수 수정 + MERGE 쿼리 | 1 (datamodel.xml) | 15개+ statement |
| 4 | DataModelService MERGE 재작성 | 1 | q-executor 핵심 |
| 5 | DataModelController clctId 제거 | 1 | q-center |
| 6 | StructDiagService CLCT 참조 제거 | 1 | q-executor |
| 7 | 프론트 수집일시 드롭다운/isLatestClct 제거 | 6 | Vue 컴포넌트 |
| 8 | 이관 SQL 실행 + 검증 | - | DB 작업 |
| 9 | 통합 테스트 (수집→편집→진단→DDL) | - | 시나리오 검증 |
