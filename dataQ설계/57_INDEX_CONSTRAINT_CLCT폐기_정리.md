# INDEX / CONSTRAINT 테이블 CLCT 폐기 정리

**작성일**: 2026-04-21
**상태**: ✅ 완료
**관련 문서**: [49_CLCT폐기_영향도분석_및_작업계획.md](49_CLCT폐기_영향도분석_및_작업계획.md), [56_DataQ_데이터모델관리_현재기능_심층분석.md](56_DataQ_데이터모델관리_현재기능_심층분석.md)

---

## 1. 배경

49번 문서 기준 CLCT 폐기 1차 작업에서는 `TB_DATA_MODEL_OBJ` / `TB_DATA_MODEL_ATTR` 만
`DM_ID + 자연키 PK + UPSERT + soft-delete` 패턴으로 이관되었고,
`TB_DATA_MODEL_INDEX` / `TB_DATA_MODEL_CONSTRAINT` 는 `DM_CLCT_ID` 기반이 남아 있었다.

49번 문서 섹션 6에는 "수집 로그 참조용 의도적 유지"로 기록되어 있었으나, 실제로는
- 구조 진단 스냅샷은 이미 `TB_STRUCT_DIAG_INDEX_DETAIL` / `TB_STRUCT_DIAG_CONSTRAINT_DETAIL` 에 별도 저장됨
- 따라서 원본 INDEX/CONSTRAINT 테이블이 회차 기반으로 남을 기술적 이유는 없음
- 결론: "의도적 유지"가 아니라 **작업 누락**

2026-04-21 에 OBJ/ATTR 와 동일한 규칙으로 정리 완료.

---

## 2. 변경 전/후 비교

| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| PK | `(DM_CLCT_ID, SEQ)` | INDEX: `(DM_ID, OBJ_OWNER, TABLE_NM, INDEX_NM, COLUMN_POS)` <br> CONSTRAINT: `(DM_ID, OBJ_OWNER, TABLE_NM, CONSTRAINT_NM, COLUMN_POS)` |
| 수집 방식 | 매 회차 INSERT (누적) | UPSERT (ON CONFLICT DO UPDATE) |
| 삭제 처리 | 없음 (이전 회차 그대로 보존) | soft-delete: `USE_YN='N' + DELETED_DT` |
| 조회 필터 | `DM_CLCT_ID = ?` | `DM_ID = ? AND USE_YN = 'Y'` |
| API 엔드포인트 | `/getDataModelIndexListByClctId` | `/getDataModelIndexListByDmId` |

---

## 3. 작업 범위

### 3-1. DDL (`dataQ설계/DDL_claude_generated.sql` 부가 블록)

```sql
-- USE_YN / DELETED_DT 컬럼 추가
ALTER TABLE TB_DATA_MODEL_INDEX ADD COLUMN IF NOT EXISTS USE_YN CHAR(1) DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_INDEX ADD COLUMN IF NOT EXISTS DELETED_DT VARCHAR(14);

-- NULL → '' 변환 (PK 구성 가능하도록)
UPDATE TB_DATA_MODEL_INDEX SET OBJ_OWNER = '' WHERE OBJ_OWNER IS NULL;

-- 이전 회차 중복 정리 (가장 최근 회차만 남김)
DELETE FROM TB_DATA_MODEL_INDEX t1 USING TB_DATA_MODEL_INDEX t2, ...;

-- NOT NULL 강제
ALTER TABLE TB_DATA_MODEL_INDEX ALTER COLUMN DM_ID SET NOT NULL;
ALTER TABLE TB_DATA_MODEL_INDEX ALTER COLUMN OBJ_OWNER SET NOT NULL;
-- ...

-- PK 교체
ALTER TABLE TB_DATA_MODEL_INDEX DROP CONSTRAINT IF EXISTS PK_TB_DATA_MODEL_INDEX;
ALTER TABLE TB_DATA_MODEL_INDEX ADD CONSTRAINT PK_TB_DATA_MODEL_INDEX
    PRIMARY KEY (DM_ID, OBJ_OWNER, TABLE_NM, INDEX_NM, COLUMN_POS);

-- 구 컬럼 제거
ALTER TABLE TB_DATA_MODEL_INDEX DROP COLUMN IF EXISTS DM_CLCT_ID;
ALTER TABLE TB_DATA_MODEL_INDEX DROP COLUMN IF EXISTS SEQ;

-- TB_DATA_MODEL_CONSTRAINT 도 동일 패턴(자연키에 CONSTRAINT_NM 포함)
```

### 3-2. MyBatis Mapper (`q-common/.../stnd/datamodel.xml`)

| 쿼리 | 변경 내용 |
|------|-----------|
| `selectDataModelIndexListByDmId` | `DM_ID = ? AND USE_YN = 'Y'` 필터 |
| `selectDataModelConstraintListByDmId` | 동일 |
| `insertDataModelIndex` | UPSERT (ON CONFLICT … DO UPDATE SET … USE_YN='Y', DELETED_DT=NULL) |
| `insertDataModelConstraint` | 동일 |
| `softDeleteMissingIndexes` | `USE_YN='N', DELETED_DT` 업데이트. 자연키가 복합이라 `CONCAT(OBJ_OWNER, '|', TABLE_NM, '|', INDEX_NM, '|', COLUMN_POS)` 형태로 NOT IN 비교 |
| `softDeleteMissingConstraints` | 동일 |
| `selectDataModelIndexListByClctId` | **제거** |
| `selectDataModelConstraintListByClctId` | **제거** |

### 3-3. q-executor 수집 로직 (`DataModelService.java`)

- INSERT 호출에서 `clctId`, `seq` 파라미터 제거
- 수집 루프에서 `collectedIndexKeys` / `collectedConstraintKeys` List 수집
  (자연키를 `|` 로 join 한 문자열)
- 루프 종료 후 `softDeleteMissingIndexes` / `softDeleteMissingConstraints` 호출하여
  이번 수집에 포함되지 않은 기존 레코드를 soft-delete

### 3-4. Controller (`q-center DataModelController.java`)

- `/api/dm/getDataModelIndexListByClctId` → `/api/dm/getDataModelIndexListByDmId` 교체
- `/api/dm/getDataModelConstraintListByClctId` → `/api/dm/getDataModelConstraintListByDmId` 교체
- 파라미터 `dmClctId` → `dataModelId`
- 내부 쿼리 `selectDataModelIndexListByDmId` / `selectDataModelConstraintListByDmId` 호출

### 3-5. Vue 프론트엔드

- `DSDatamodelStatusIndex.vue`: axios URL/파라미터 변경
- `DSDatamodelStatusConstraint.vue`: axios URL/파라미터 변경
- 두 파일 모두 기존 수집일시 드롭다운은 이미 Phase B 에서 제거 완료된 상태에서 작업

---

## 4. 마이그레이션 고려사항

1. **중복 데이터 정리**: 기존에 회차별 INSERT 로 누적된 다중 레코드가 존재하므로,
   PK 교체 전에 DELETE 로 "가장 최근 회차(CLCT_END_DT + SEQ 기준)" 만 남기고 제거.
2. **OBJ_OWNER NULL 처리**: 기존 NULL 값이 복합 PK 에 참여할 수 없으므로
   `UPDATE ... SET OBJ_OWNER = '' WHERE OBJ_OWNER IS NULL` 선행.
3. **DM_CLCT_ID 컬럼 drop**: 구조 진단 스냅샷은 `TB_STRUCT_DIAG_*_DETAIL` 에
   독립 저장되므로 원본 테이블의 회차 컬럼은 완전 제거해도 영향 없음.

---

## 5. 검증 방법

1. DDL 적용 후 `\d TB_DATA_MODEL_INDEX` / `\d TB_DATA_MODEL_CONSTRAINT` 로 PK 확인
2. q-executor 재기동 후 데이터모델 수집 1회 실행
   - 신규 행은 `USE_YN='Y'` 로 INSERT
   - 기존 행 중 이번 수집에 없는 것은 `USE_YN='N' + DELETED_DT` 로 변경
3. 데이터모델 현황 > 인덱스 / 제약조건 탭에서 정상 조회되는지 확인
4. 동일 모델 재수집 시 레코드 수가 누적되지 않고 UPSERT 되는지 확인

---

## 6. 잔존 CLCT 참조 (현재 의도된 유지)

- `TB_DATA_MODEL_CLCT` 테이블 자체 (수집 이력 로그)
- `TB_DATA_MODEL_OBJ` / `TB_DATA_MODEL_ATTR` SELECT 절의 `T.DM_CLCT_ID as clctId` alias
  (UI 호환용, 마지막 수집 회차 표시)

INDEX / CONSTRAINT 에서는 완전 제거됨.
