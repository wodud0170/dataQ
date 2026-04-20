# 44. 데이터모델 CLCT 폐기 재설계 (DM_ID 단일 식별 + MERGE 수집)

**작성일**: 2026-04-19
**상태**: 설계 초안 (구현 착수 전)
**관련 문서**: 40_데이터모델_관리_재설계.md, 41_데이터모델_재설계_구현가이드.md

---

## 1. 배경 및 문제 인식

현재 데이터 모델은 `TB_DATA_MODEL_CLCT (수집 이력)` 을 중심 식별자로 사용한다.

- `TB_DATA_MODEL_OBJ`, `TB_DATA_MODEL_ATTR` 는 `(DM_CLCT_ID, OBJ_NM[, ATTR_NM])` 로 식별
- "현재 모델 상태" = "최신 CLCT 스냅샷"
- 수집할 때마다 새로운 CLCT 행 + OBJ/ATTR 전체 복제

이 구조에서 `TB_DATA_MODEL_CLCT` 는 **두 역할을 겸한다**:

1. **시점 스냅샷 (history)** — 과거 상태 보존
2. **현재 상태 식별자 (identity)** — OBJ/ATTR 의 논리 키

### 1.1 드러난 문제

- **논리(설계) 모델**은 수집 이벤트가 없어 CLCT 가 비어버림 → OBJ/ATTR 을 담을 곳이 없음
- 1단계 재설계에서 "수동 MANUAL 스냅샷"을 만들어 우회하려 했으나, 이는 CLCT 의 원래 의미(시점 이력)를 희석시킴
- 수집 모델·논리 모델이 **근본적으로 다른 데이터 모양**을 가지게 되어 Controller/Mapper/Frontend 곳곳에 분기 로직 발생 우려
- 편집 UI 의 `isLatestClct` 같은 "최신 스냅샷인가" 체크가 논리 모델에선 아예 성립 안 함

### 1.2 이력 관리의 실질 가치 재검토

CLCT 스냅샷이 실제로 쓰이는 곳:

| 용도 | 실제 사용 여부 | 대체 가능 여부 |
|---|---|---|
| 구조 변경 진단 (뭐가 변했는가) | 사용 중 | `TB_STRUCT_DIAG_HISTORY / DETAIL` 이 독자적으로 변경 이력 보관 → **대체 가능** |
| 통계 추이 (객체·컬럼 수 추이) | 사용 중 | `TB_DATA_MODEL_STATS` 는 날짜·카운트만 있으면 됨 → **대체 가능** |
| 과거 스냅샷 전체 열람 UI (수집일시 드롭다운) | 제공 중 | **대체 불가** (유일한 가치) |
| 진단(표준화) 시 특정 시점 기준 | 이론상 가능 | 실무상 최신만 사용 |

**결론**: 과거 스냅샷 전체 열람 기능 하나를 위해 현재의 복잡도를 유지하는 건 비용 대비 효율이 낮다. 이 기능을 포기 또는 축소하면 CLCT 를 폐기할 수 있다.

---

## 2. 설계 목표

1. **DM_ID 를 모델의 유일한 식별자로 삼는다.**
2. 수집은 **UPSERT/MERGE** 로 수행: 기존 행 갱신, 신규 행 삽입, 사라진 행은 소프트 삭제
3. 논리 모델과 물리 모델의 **데이터 모양을 통일**한다 — 차이는 `MODEL_TYPE` 컬럼과 수집 동작 유무 뿐
4. 사용자 수동 편집(논리명·설명)은 재수집 시 **보존**한다 /* 사용자가 편집 하지 않은 수집 only시 논리명에 데이터베이스의 논리항목(코멘트 등) 을 넣어줘야 함 */

5. 변경 이력은 기존 `TB_STRUCT_DIAG_HISTORY / DETAIL` 에 위임

---

## 3. 스키마 변경 설계

### 3.1 TB_DATA_MODEL_OBJ

```sql
ALTER TABLE TB_DATA_MODEL_OBJ ADD COLUMN USE_YN VARCHAR(1) DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_OBJ ADD COLUMN DELETED_DT VARCHAR(14);
-- DM_CLCT_ID → DM_ID 전환 (아래 4. 이관 참조)
-- 최종 PK: (DM_ID, OBJ_NM)
```

### 3.2 TB_DATA_MODEL_ATTR

```sql
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN USE_YN VARCHAR(1) DEFAULT 'Y';
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN DELETED_DT VARCHAR(14);
-- DM_CLCT_ID → DM_ID 전환
-- 최종 PK: (DM_ID, OBJ_NM, ATTR_NM)
```

### 3.3 TB_DATA_MODEL_CLCT (재활용)

스냅샷 테이블이 아닌 **수집 이벤트 로그** 로 용도 변경:

```
TB_DATA_MODEL_CLCT
- DM_CLCT_ID    : 수집 이벤트 ID (여전히 PK)
- DM_ID         : 대상 모델
- CLCT_START_DT : 시작 시각
- CLCT_END_DT   : 종료 시각
- CLCT_TYPE     : DBMS / MANUAL / ERWIN
- CLCT_CMPTN_YN : 성공 여부
- ADDED_CNT     : (신규) 추가된 테이블·컬럼 합계
- DELETED_CNT   : (신규) 삭제된 테이블·컬럼 합계
- MODIFIED_CNT  : (신규) 변경된 테이블·컬럼 합계
- CRET_USER_ID
```

- OBJ/ATTR 와의 FK 관계 제거
- "수집을 몇 번 했고 뭐가 바뀌었는지" 감사 로그 역할

### 3.4 TB_DATA_MODEL_STATS

- `DM_CLCT_ID` 를 유지하되 의미를 "이 수집 시점의 스냅샷 통계" 로 축소
- 혹은 테이블 자체 폐기하고 `TB_DATA_MODEL_CLCT` 에 통합 (결정 필요)

---

## 4. 데이터 이관 전략

### 4.1 기본 원칙

- 각 모델의 **최신 CLCT 에 속한 OBJ/ATTR 만 살림** → DM_ID 기준으로 재배치
- 과거 CLCT 에 속한 OBJ/ATTR 는 폐기 (대신 `TB_STRUCT_DIAG_HISTORY / DETAIL` 이력 유지)
- `TB_DATA_MODEL_CLCT` 자체는 보존 (수집 이벤트 로그로 재사용)

### 4.2 이관 스크립트 개요

```sql
-- 1) 임시로 DM_ID 컬럼 추가
ALTER TABLE TB_DATA_MODEL_OBJ ADD COLUMN DM_ID VARCHAR(32);
ALTER TABLE TB_DATA_MODEL_ATTR ADD COLUMN DM_ID VARCHAR(32);

-- 2) 최신 CLCT 만 남기고 DM_ID 채우기
UPDATE TB_DATA_MODEL_OBJ O SET DM_ID = (
    SELECT DM_ID FROM TB_DATA_MODEL_CLCT WHERE DM_CLCT_ID = O.DM_CLCT_ID
);
-- ATTR 동일

-- 3) 오래된 CLCT 의 OBJ/ATTR 삭제 (최신만 유지)
DELETE FROM TB_DATA_MODEL_OBJ WHERE DM_CLCT_ID NOT IN (
    SELECT MAX(DM_CLCT_ID) FROM TB_DATA_MODEL_CLCT GROUP BY DM_ID
);
-- ATTR 동일

-- 4) DM_ID 를 NOT NULL + PK 구성으로
ALTER TABLE TB_DATA_MODEL_OBJ ALTER COLUMN DM_ID SET NOT NULL;
-- 기존 PK 제거 후 (DM_ID, OBJ_NM) 로 재정의

-- 5) DM_CLCT_ID 컬럼 제거
ALTER TABLE TB_DATA_MODEL_OBJ DROP COLUMN DM_CLCT_ID;
ALTER TABLE TB_DATA_MODEL_ATTR DROP COLUMN DM_CLCT_ID;
```

### 4.3 롤백 고려

- 이관 전 `TB_DATA_MODEL_OBJ_BAK_YYYYMMDD`, `TB_DATA_MODEL_ATTR_BAK_YYYYMMDD` 풀 백업
- 운영 배포 전 개발·스테이징에서 검증 완료 후 진행

---

## 5. 수집 동작 재설계 (MERGE)

### 5.1 q-executor DataModelService 로직

```
수집 시작
  INSERT INTO TB_DATA_MODEL_CLCT (수집 이벤트 로그 생성, 시작)
  
  DB 에서 테이블·컬럼 목록 조회
  
  테이블 단위 MERGE:
    - 기존 OBJ 있음 → UPDATE
        (OBJ_NM_KR 은 수동 편집값 유지 위해 조건부 UPDATE)
        (OBJ_COMMENT 는 항상 새 값으로)
        USE_YN='Y', DELETED_DT=NULL 복원
    - 기존 OBJ 없음 → INSERT
        (OBJ_NM_KR 은 OBJ_COMMENT 값으로 초기화)
  
  컬럼 단위 MERGE:
    - 동일 로직으로 ATTR 처리
    - DATA_TYPE/LEN/NULLABLE 등 물리 속성은 항상 수집값으로 덮어쓰기
    - ATTR_NM_KR 만 수동 편집값 유지
  
  이번 수집에 없는 기존 행 소프트 삭제:
    UPDATE TB_DATA_MODEL_OBJ SET USE_YN='N', DELETED_DT=NOW()
    WHERE DM_ID=? AND OBJ_NM NOT IN (수집한 OBJ 목록)
      AND USE_YN='Y'
    (ATTR 동일)
  
  TB_STRUCT_DIAG_HISTORY 에 변경 요약 기록 (기존 로직 재활용)
  
  UPDATE TB_DATA_MODEL_CLCT (종료 시각, ADDED/DELETED/MODIFIED 카운트)
```

### 5.2 머지 규칙 매트릭스

| 필드 | 최초 수집 | 재수집 (이미 있음) | 수동 편집 |
|---|---|---|---|
| OBJ_NM / ATTR_NM | 원천 DB | 키이므로 변경 없음 | 불가 |
| DATA_TYPE / DATA_LEN / NULLABLE / PK_YN / FK_YN / DEFAULT_VAL | 원천 DB | **DB 값으로 덮어쓰기** | 수동 입력 가능 (재수집 시 덮임) |
| OBJ_COMMENT / ATTR_COMMENT | DB 코멘트 | **DB 값으로 덮어쓰기** | 읽기 전용 |
| OBJ_NM_KR / ATTR_NM_KR (논리명) | **표준 기반 자동 생성** (물리명 토큰이 표준 단어에 매핑되면 한글명 조합) · 실패 시 OBJ_COMMENT 값 복사 · 둘 다 실패 시 공란 | **동일 규칙으로 재생성 가능** (수동 편집 여부 플래그 없어도 언제든 재계산) | 편집 가능하지만 표준 기반 자동 생성이 가능하므로 복구 용이 |
| OBJ_DESC | NULL | **기존 값 유지** | 편집 가능 |
| TERMS_STND_YN / DOMAIN_STND_YN / WORD_LST | 재계산 | **재계산** | - |
| USE_YN | 'Y' | 'Y' (소프트 삭제 상태였다면 복원) | - |

---

## 6. 편집(수동) 동작 재설계

### 6.1 논리 모델

- 모델 등록 직후부터 바로 테이블·컬럼 추가 가능 (CLCT 선결 조건 없음)
- 편집은 모두 `DM_ID` 기준 직접 INSERT/UPDATE/DELETE

### 6.2 물리 모델 (수집된 모델)

- 수집 이후에도 수동 편집 허용 (논리명·설명만 권장, 물리속성은 허용할지 정책 결정 필요)
- 다음 수집 시 MERGE 규칙에 따라 물리속성은 덮이고 논리명은 유지

### 6.3 삭제 동작

- UI 에서 "삭제" 버튼 → **소프트 삭제** (`USE_YN='N'`, `DELETED_DT` 기록)
- 이후 재수집에서 해당 객체가 다시 존재하면 **자동 복원**
- 완전 삭제(물리 삭제) 는 관리자 액션으로 분리 (별도 메뉴 또는 미제공)

---

## 7. 영향 범위

### 7.1 Backend

| 모듈 | 파일 | 변경 내용 |
|---|---|---|
| q-common | `StdDataModelObjVo.java` | `clctId` 제거, `useYn`/`deletedDt` 추가 |
| q-common | `StdDataModelAttrVo.java` | 동일 |
| q-common | `StdDataModelCollectVo.java` | `addedCnt`/`deletedCnt`/`modifiedCnt` 추가 |
| q-common | `datamodel.xml` | 모든 SELECT/INSERT/UPDATE 키 전환 (15개 이상 statement) |
| q-center | `DataModelController.java` | 편집 엔드포인트(6개) 파라미터 단순화, `resolveLatestClctId` 제거 |
| q-executor | `DataModelService.java` | 수집 로직 INSERT → MERGE 재작성 |
| q-executor | 구조 진단 로직 | CLCT 기반 조회를 DM_ID 기반으로 변경 |

### 7.2 Frontend

| 컴포넌트 | 변경 |
|---|---|
| `DSDatamodelCollection.vue` | 수집 이벤트 로그는 유지, 모델별 "수집일시" 선택 UI 제거 |
| `DSDatamodelStatusTable.vue` | `clctList`, `selectedClctId`, `isLatestClct` 제거. `dataModelId` 만 있으면 편집 가능 |
| `DSDatamodelStatusColumn.vue` | 동일 |
| 구조 진단 관련 화면 | 수집시점 비교 로직 재검토 |

### 7.3 DDL

- 신규 ALTER 섹션 (§17 예정)
- 이관 스크립트 별도 파일 (`dataQ설계/MIGRATION_44_clct_merge.sql`)

---

## 8. 리스크 및 대응

| 리스크 | 대응 |
|---|---|
| 기존 과거 스냅샷 데이터 전량 손실 | 이관 전 백업 테이블 보존. 필요 시 별도 아카이브 |
| 구조 진단 과거 이력이 CLCT 기반인데 깨지지 않는가 | `TB_STRUCT_DIAG_HISTORY` 는 `DATA_MODEL_ID` 기준이라 영향 없음. `TB_STRUCT_DIAG_DETAIL` 검토 필요 |
| 소프트 삭제된 객체의 "영원히 남는" 문제 | 수집 시 소프트 삭제 후 N일 경과 시 자동 물리 삭제 스케줄러 (옵션) |
| 수집 중 오류로 MERGE 중단 시 일관성 | 트랜잭션으로 수집 단위 묶기. 실패 시 `CLCT_CMPTN_YN='N'` 로만 기록, 데이터는 롤백 |
| 수동 편집 물리속성이 재수집에서 덮이는 경우 사용자 혼란 | UI 에서 "물리속성은 수집 시 덮어쓰기됨" 경고 표시 |

---

## 9. 결정 필요 항목

- [ ] 사라진 객체 처리: **소프트 삭제** 로 확정? (권장)
- [ ] 논리명(NM_KR) 머지 정책: **수동 편집 유지** 로 확정? (권장)
- [ ] 과거 CLCT 스냅샷 데이터 폐기 허용? (운영 환경 고려)
- [ ] 물리 모델에서 물리속성 수동 편집 허용 여부?
- [ ] `TB_DATA_MODEL_STATS` 폐기 vs 유지?

---

## 10. 작업 단계 (결정 후 착수)

1. DDL 작성 (`DDL_claude_generated.sql` §17 + 이관 SQL 별도)
2. VO 수정
3. Mapper XML 전수 수정
4. Controller 편집 엔드포인트 정리 (`clctId` 파라미터 제거)
5. Executor 수집 로직 MERGE 재작성
6. 구조 진단 로직 영향 확인
7. Frontend `isLatestClct` 관련 전부 제거, 수집일시 드롭다운 제거
8. 이관 SQL 실행 (백업 후)
9. 통합 테스트
