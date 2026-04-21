# 56. DataQ 데이터 모델 관리 — 현재 기능 심층 분석

작성일: 2026-04-21
범위: 현 시점 코드베이스 기준. 외부 시스템 비교나 과거 설계 문서는 배제하고, 실제 구현체(컨트롤러/매퍼/Vue)로부터 도출한 기능과 포인트만 기술한다.

---

## 0. 분석 대상 파일

| 계층          | 경로                                                                                                                                                                                               |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Controller  | [DataModelController.java](q-center/src/main/java/qualitycenter/controller/DataModelController.java)                                                                                             |
| Mapper XML  | [datamodel.xml](q-common/src/main/resources/mapper/stnd/datamodel.xml)                                                                                                                           |
| 모델 등록/수집    | [DSDatamodelCollection.vue](q-center/vue/front/src/components/DSDatamodelCollection.vue)                                                                                                         |
| 모델 현황(대시보드) | [DSDatamodelStatus.vue](q-center/vue/front/src/components/DSDatamodelStatus.vue)                                                                                                                 |
| 테이블 관리      | [DSDatamodelStatusTable.vue](q-center/vue/front/src/components/DSDatamodelStatusTable.vue)                                                                                                       |
| 컬럼 관리 (그리드) | [DSDatamodelStatusColumn.vue](q-center/vue/front/src/components/DSDatamodelStatusColumn.vue)                                                                                                     |
| 수집 이력       | [DSDatamodelHistory.vue](q-center/vue/front/src/components/DSDatamodelHistory.vue)                                                                                                               |
| 인덱스/제약조건 뷰  | [DSDatamodelStatusIndex.vue](q-center/vue/front/src/components/DSDatamodelStatusIndex.vue), [DSDatamodelStatusConstraint.vue](q-center/vue/front/src/components/DSDatamodelStatusConstraint.vue) |
| DDL 스냅샷     | [DDL_claude_generated.sql](dataQ설계/DDL_claude_generated.sql)                                                                                                                                     |

---

## 1. 데이터 모델링 — 물리 테이블 레이아웃

현재 구조는 CLCT(수집회차) 폐기가 끝난 상태다. 테이블/컬럼의 식별자는 회차가 아니라 모델(DM_ID)에 직접 종속한다.

### 1-1. 핵심 테이블

| 테이블                      | PK                       | 역할                                               |
| ------------------------ | ------------------------ | ------------------------------------------------ |
| TB_DATA_MODEL            | DM_ID                    | 데이터 모델 헤더 (모델명, 버전, 데이터소스, 시스템)                  |
| TB_DATA_MODEL_CLCT       | CLCT_ID                  | 수집 이력 로그. CLCT_TYPE: `DBMS` / `MANUAL` / `ERWIN` |
| TB_DATA_MODEL_SCHEMA     | (DM_ID, SCHEMA_NM)       | 수집 대상 스키마 필터 (USE_YN)                            |
| TB_DATA_MODEL_OBJ        | (DM_ID, OBJ_NM)          | 테이블. OBJ_OWNER는 nullable (논리모델 대응)               |
| TB_DATA_MODEL_ATTR       | (DM_ID, OBJ_NM, ATTR_NM) | 컬럼                                               |
| TB_DATA_MODEL_INDEX      | (DM_CLCT_ID, SEQ)        | 인덱스 (회차 단위)                                      |
| TB_DATA_MODEL_CONSTRAINT | (DM_CLCT_ID, SEQ)        | 제약조건. TYPE: P/R/U/C + REF_* + DELETE_RULE        |

### 1-2. 편집 가능성 분리

수집된 원본과 사용자가 편집한 논리명을 구분한다.

- `OBJ_COMMENT` / `ATTR_COMMENT`: DB에서 수집한 코멘트 **원본**. 수집 시 자동 채움, 화면에선 읽기 전용.
- `OBJ_NM_KR` / `ATTR_NM_KR`: 편집 가능한 **논리명**. 최초 수집 시 COMMENT 값이 복사되며, 이후 사용자가 자유롭게 수정한다.

### 1-3. 표준 연계 플래그

| 컬럼             | 의미                                     |
| -------------- | -------------------------------------- |
| TERMS_STND_YN  | 용어 표준 적용 여부 (물리명이 표준용어 조합으로 도출되었는가)    |
| DOMAIN_STND_YN | 도메인 표준 적용 여부 (데이터 타입/길이가 표준 도메인에서 왔는가) |

### 1-4. Upsert 전략

`insertDataModelObj` / `insertDataModelAttr` 매퍼는 `ON CONFLICT DO UPDATE`로 작성되어 있다. 반복 수집/편집 시 같은 PK로 들어오면 덮어쓰고, 없어진 항목은 `softDeleteMissingObjs` / `softDeleteMissingAttrs` 로 USE_YN='N' + DELETED_DT 처리한다.

---

## 2. 데이터 모델 등록 — DSDatamodelCollection.vue

### 2-1. 입력 필드

- **데이터 모델명** (필수)
- **시스템** (Treeselect, parentSysCd 재귀 구성)
- **데이터 소스** (옵션 — 비워두면 논리 전용 모델)
- **버전** (빈값이면 `'1.0'`)

### 2-2. 등록 전 가드

데이터 소스를 지정한 경우, 해당 소스의 `connTestYn !== 'Y'` 이면 등록을 차단하고 "관리 > 데이터 소스에서 연결 테스트를 먼저 수행하세요" SWAL을 띄운다. 연결 불가 소스로 모델이 생기는 것을 원천 차단한다.

### 2-3. 삭제

`deleteDataModels`는 배열로 다건 처리. 하위 OBJ/ATTR/CLCT 까지 cascade.

---

## 3. DBMS 수집 — 물리 소스 기반

### 3-1. 스키마 필터 UX

모델 상세 진입(`loadSchemas`) 시 다음 3단계가 돌아간다.

1. `api/dm/getSchemaList` — 데이터 소스에 접속해 스키마 목록 + 접속 유저명을 얻는다. 매퍼 `selectSchemaListSql`은 DBMS별 분기 (Oracle/Tibero/MariaDB/Cubrid/SQLServer/default).
2. `api/dm/getDataModelSchemas` — TB_DATA_MODEL_SCHEMA에 저장된 필터를 읽는다.
3. 둘을 머지해서 트리 구성. 저장된 값이 있으면 그걸, 없으면 **접속 유저와 동일한 스키마만 Y로 체크**.
4. 머지한 결과를 즉시 `saveDataModelSchemas`로 돌려 저장 (stale 스키마 제거).

### 3-2. 수집 실행

`collectionAction()`은 선택된 스키마를 필터에 실어 `api/dm/collectDataModel` 호출 → q-executor로 디스패치. 진행 상황은 WebSocket 이벤트로 수신한다.

- `eventBus.$on('NOTICE', ...)` — INFO/ERROR 로그 라인
- `eventBus.$on('RELOAD', ...)` — `DATA_MODEL_ATTR_RELOAD` 메시지를 받으면 수집 완료로 판정, 목록 새로고침

수집 결과는 TB_DATA_MODEL_CLCT (CLCT_TYPE='DBMS') + TB_DATA_MODEL_OBJ/ATTR/INDEX/CONSTRAINT에 기록.

---

## 4. ERwin 임포트

컨트롤러의 `ErwinXmlParser` 경로로 XML을 파싱해 OBJ/ATTR/CONSTRAINT/INDEX를 적재한다. CLCT_TYPE='ERWIN'.

- DBMS 접속 없이 논리 구조를 가져올 수 있는 경로.
- 수집 루트가 같기 때문에 이후의 편집/진단/DDL 생성 파이프라인을 그대로 탄다.

---

## 5. 테이블 관리 — DSDatamodelStatusTable.vue

### 5-1. 주요 버튼

| 버튼      | 동작                                                                          |
| ------- | --------------------------------------------------------------------------- |
| 조회      | 선택 모델의 OBJ 리스트                                                              |
| 테이블 추가  | `addObj` — 물리명 공백이면 `TMP_TBL_{count+1}` 자동 채번. (DM_ID, OBJ_NM) 중복 체크        |
| 엑셀 업로드  | 2-phase preview/commit (아래)                                                 |
| 양식 다운로드 | `api/dm/uploadTemplate?scope=tables` — 동적으로 XSSFWorkbook 생성해 헤더만 깔린 xlsx 반환 |
| 삭제      | 선택 행 soft delete                                                            |

### 5-2. 엑셀 업로드 2-phase

헤더 고정: `소유자`, `테이블명(한글)`, `설명`.

1. **Preview 호출**: multipart로 파일 전송 → 컨트롤러 `parseTableWorkbook` 이 파싱. (DM_ID, OBJ_NM) 중복·기존 존재 여부를 플래그로 붙여 결과 프리뷰를 그리드로 보여준다.
2. **Commit 실행**: 사용자가 [등록 실행]을 클릭하면 플래그된 대상만 insert. 이 단계에서 OBJ_NM 공백 행에 `TMP_TBL_{seq}`를 일괄 채번한다.

---

## 6. 컬럼 관리 — 논리모델 그리드 편집 (핵심 기능)

DSDatamodelStatusColumn.vue. 이 화면이 "DBMS 접속 없이 논리 모델을 설계하고 물리로 변환한다"는 워크플로우의 중심이다.

### 6-1. 그리드 구조

| 영역    | 컬럼                                                |
| ----- | ------------------------------------------------- |
| 식별    | 소유자, 테이블 한글명, 테이블명, 컬럼 한글명, 컬럼명                   |
| 물리 속성 | 데이터 타입, 길이, 소수점, NULL, PK, FK, 디폴트                |
| 표준    | ✓/✗ (TERMS_STND_YN), **변환 불가 사유** (resolveReason) |
| 액션    | 행 단위 편집/삭제                                        |

### 6-2. 입력 편의 기능

- **[+ 컬럼 추가]** — 1행 추가
- **[+ 빈 행 10개]** — 10행을 한번에 추가
- **TSV 클립보드 붙여넣기**: 엑셀에서 범위 복사 → 그리드에 Ctrl+V. 한글명/NULL/PK/FK/기본값이 자동 파싱된다. **최대 100행 상한**.
- 신규 행은 `_mode='add'` 로 마킹, 삭제는 `pendingDeletes`에 집계 (즉시 삭제 X).

### 6-3. 저장 흐름 — `saveAll` → `/api/dm/saveAttrs`

batch로 ADD/UPDATE/DELETE 모드를 단일 트랜잭션으로 처리.

- 신규 컬럼 중 ATTR_NM이 비어 있으면 `TMP_COL_{maxOrd+1}` 채번 (`selectMaxAttrOrd`).
- 신규 컬럼의 dataType 기본값은 `VARCHAR(255)`.
- 한 번에 여러 테이블(OBJ_NM 다름)이 섞여도 OBJ 단위로 그룹핑해서 처리.

### 6-4. 표준 적용 변환 — `[선택 컬럼 물리모델 변환]`

선택된 행을 모아 `/api/dm/resolveAttrs`에 보낸다. 컨트롤러의 `resolveStandard` → `applyResolvedToAttr` 로직:

1. 한글 컬럼명 토큰 분리
2. TB_TERMS에서 용어 매칭 → `termsEngAbrvNm`을 순서대로 `_` 조인 → 물리 ATTR_NM 후보
3. TB_DOMAIN 매칭 → `dataType`, `dataLen`, `dataDecimalLen` 채움
4. ATTR_NM rename: `updateDataModelAttrKey` (origAttrNm → new)
5. `TERMS_STND_YN = 'Y'` / `DOMAIN_STND_YN = 'Y'` 마킹

**실패 시**: alert 팝업 대신 그리드의 `변환 불가 사유` 컬럼에 사유를 누적해 표시한다 (예: "용어 미등록: 고객", "도메인 매칭 없음"). alert 금지 원칙.

### 6-5. 단어 검증 — `validateAttrStandards`

`splitTokens` 가 ATTR_NM을 `_` 로 분리 → 각 토큰이 TB_WORD 에 있는지 조회. 없는 토큰은 `findMissingWords`가 리턴. 이후 승인/진단에서 경고로 사용된다.

### 6-6. 컬럼 엑셀 업로드

11 컬럼 헤더: `소유자`, `테이블명(한글)`, `컬럼명(한글)`, `컬럼 순서`, `PK여부`, `FK여부`, `참조 테이블(한글)`, `참조 컬럼(한글)`, `삭제 규칙` 등. `parseAttrWorkbook`은 2-pass:
1. 1-pass — 모든 ATTR insert
2. 2-pass — FK 참조(ref_owner/ref_table/ref_column)를 실제 PK 기준으로 resolve. 1-pass에서 채번된 TMP_COL_N이 2-pass에서 FK 타깃으로 연결된다.

---

## 7. 현황 대시보드 — DSDatamodelStatus.vue

### 7-1. 모델 헤더 집계

`selectDataModelStatsList` 매퍼가 TB_DATA_MODEL에 최신 TB_DIAG_JOB + TB_STRUCT_DIAG_HISTORY를 LEFT JOIN해 다음을 한번에 돌려준다.

| 컬럼               | 산식                           |
| ---------------- | ---------------------------- |
| objCnt / attrCnt | USE_YN='Y' 건수                |
| clctDt           | 최신 수집 일시                     |
| diagDt           | 최신 표준진단 일시                   |
| structDiagDt     | 최신 구조진단 일시                   |
| diagStndRate     | (전체 컬럼수 - 이슈 컬럼수) / 전체 × 100 |
| structDiagRate   | 구조 일치율                       |

이슈 컬럼수는 `COUNT(DISTINCT OBJ_NM || '.' || ATTR_NM) FROM TB_DIAG_RESULT`. RESULT_CNT(이슈 건수)가 아니라 ISSUE_COL_CNT(이슈 컬럼 수)를 기준으로 한다.

### 7-2. 하위 탭

- **테이블 탭**: OBJ 목록 (OBJ_NM, OBJ_NM_KR, OBJ_OWNER, 컬럼수)
- **컬럼 탭**: ATTR 목록 + 표준 단어 매핑 (wordLst, wordStndLst pair → "단어 : 표준" 형식으로 조립)
- **수집 이력 모달**: `selectDataModelClctList`, 기간 필터

### 7-3. DDL 다운로드

모델 단위로 `api/dm/downloadDdl?dataModelId=...` 호출 → CREATE TABLE + 컬럼 정의 + PK/FK/Index/Check 제약을 포함한 ddl 파일을 내려준다. 편집된 논리 모델로도 실행 가능한 DDL을 추출할 수 있다.

### 7-4. 논리/물리 뷰 토글

컬럼 탭은 `modelViewMode` 에 따라 헤더 배치가 달라진다.
- 물리 뷰: ATTR_NM, dataType, dataLen ... 중심
- 논리 뷰: ATTR_NM_KR, OBJ_NM_KR 중심 (한글명 선두)

---

## 8. 진단 연동 지점

데이터 모델은 두 진단의 입력 소스다.

### 8-1. 표준 진단 (TB_DIAG_JOB / TB_DIAG_RESULT)

- TB_DATA_MODEL_ATTR의 ATTR_NM / dataType / dataLen 을 규칙 엔진(TB_WORD / TB_TERMS / TB_DOMAIN)에 대조.
- 결과는 (이슈 유형, 모델, 테이블, 컬럼, 상세) 단위로 누적.
- 준수율 = 이슈 **컬럼** 기준으로 환산.

### 8-2. 구조 변경 진단 (TB_STRUCT_DIAG_HISTORY / TB_STRUCT_DIAG_DETAIL)

- 이전 수집 스냅샷과 현재 스냅샷을 diff하여 테이블/컬럼/인덱스/제약조건의 ADD/DROP/CHANGE 를 기록.
- 진단 시점의 변경 내역 자체는 `TB_STRUCT_DIAG_DETAIL` / `TB_STRUCT_DIAG_INDEX_DETAIL` / `TB_STRUCT_DIAG_CONSTRAINT_DETAIL` 쪽에 이미 스냅샷으로 떨어진다. 즉, 진단 결과를 남기기 위해 원본(TB_DATA_MODEL_*) 이 회차 기반일 필요는 없다.

---

## 9. 수집 이력 — DSDatamodelHistory.vue

- 모델명 + 수집일 From/To로 조회.
- TB_DATA_MODEL_CLCT 기준으로 (수집 시작/완료일시, 완료 여부, 테이블/컬럼 개수) 표시.
- CLCT_TYPE이 DBMS/MANUAL/ERWIN 중 무엇이냐에 따라 원천이 구분된다.

---

## 10. 논리 → 물리 워크플로우 요약

현재 구현에서 가장 특징적인 설계 결정을 한 줄로 요약하면:

> **물리 PK 자리에 임시 채번(`TMP_TBL_N`, `TMP_COL_N`)을 먼저 채워두고, 이후 표준 사전(용어 + 도메인)으로 변환하는 2단계 모델링.**

덕분에 다음이 모두 같은 저장소 한 장(`TB_DATA_MODEL_OBJ`, `TB_DATA_MODEL_ATTR`)에서 공존한다.

1. DBMS 수집으로 들어온 **기존 물리 모델**
2. ERwin에서 들여온 **외부 논리/물리 모델**
3. 화면에서 한글만 입력해 만든 **순수 논리 모델 (변환 전)**
4. 한글 + 표준 변환까지 마친 **파생 물리 모델**

공통 식별자가 `(DM_ID, OBJ_NM)` / `(DM_ID, OBJ_NM, ATTR_NM)` 이므로, 어떤 경로로 들어왔든 그리드 편집 / 표준 적용 / 진단 / DDL 생성 파이프라인을 그대로 탄다.

---

## 11. 지점별 구현 포인트

| 포인트              | 위치                                                | 설명                            |                   |
| ---------------- | ------------------------------------------------- | ----------------------------- | ----------------- |
| 모델 생성 시 연결테스트 가드 | DSDatamodelCollection.vue `createDataModel`       | connTestYn='N'이면 SWAL로 차단     |                   |
| 스키마 필터 기본 정책     | `loadSchemas`                                     | 최초엔 "접속 유저 == 스키마"만 Y         |                   |
| OBJ_NM 자동 채번     | Controller `addObj` / `uploadTables`              | `TMP_TBL_{count+1}`           |                   |
| ATTR_NM 자동 채번    | Controller `addAttr` / `saveAttrs`                | `TMP_COL_{maxOrd+1}`          |                   |
| 표준 적용 배치         | Controller `resolveAttrs` + `applyResolvedToAttr` | 한글 → 용어 → 물리명 + 도메인           |                   |
| 변환 실패 UX         | DSDatamodelStatusColumn.vue `resolveReason` 컬럼    | alert 대신 그리드 인라인 사유           |                   |
| TSV 붙여넣기         | DSDatamodelStatusColumn.vue `onPaste`             | 100행 상한                       |                   |
| 2-phase 엑셀 업로드   | `_runTablesUpload('preview'                       | 'commit')`                    | 미리보기 후 명시적 commit |
| FK 참조 해결         | Controller `parseAttrWorkbook` 2-pass             | TMP_COL_N과 연결까지 처리            |                   |
| 수집 실시간 로그        | eventBus NOTICE / RELOAD                          | WebSocket 구독, 대화상자 자동 스크롤     |                   |
| 준수율 통일           | `selectDataModelStatsList`                        | **이슈 컬럼수 기준** (RESULT_CNT 아님) |                   |
| 논리/물리 뷰 토글       | DSDatamodelStatus.vue `modelViewMode`             | 헤더 배열 스왑                      |                   |
| DDL 생성           | `api/dm/downloadDdl`                              | 편집된 논리 모델도 실행 가능한 DDL 추출      |                   |
| 소유자 nullable     | DDL `ALTER ... DROP NOT NULL`                     | 논리 모델은 OWNER 없이도 입력 가능        |                   |
| 코멘트 원본 보존        | `OBJ_COMMENT` / `ATTR_COMMENT` 읽기 전용              | 편집은 `*_NM_KR` 쪽에              |                   |

---

## 12. 미정리 지점 (사실 기반)

> ~~INDEX / CONSTRAINT 가 CLCT_ID 기반이라 OBJ/ATTR 와 불일치~~ → **2026-04-21 작업에서 정리 완료**. `(DM_ID, OBJ_OWNER, TABLE_NM, INDEX_NM/CONSTRAINT_NM, COLUMN_POS)` 자연키 PK + UPSERT + soft-delete 로 전환. 상세는 [57_INDEX_CONSTRAINT_CLCT폐기_정리.md](dataQ설계/57_INDEX_CONSTRAINT_CLCT폐기_정리.md) 참고.

- 컬럼 그리드 편집은 있지만, **인덱스/제약조건은 현재 조회 전용**이다 ([DSDatamodelStatusIndex.vue](q-center/vue/front/src/components/DSDatamodelStatusIndex.vue), [DSDatamodelStatusConstraint.vue](q-center/vue/front/src/components/DSDatamodelStatusConstraint.vue)). 논리 모델 편집에서 PK/FK/UK/CHECK나 인덱스를 직접 설계하는 UI는 없음.
- 표준 적용 변환이 실패한 컬럼의 **사유 기록은 응답 세션에만** 남고 TB 쪽에 영속화되지 않는다. 재조회 시 사유 확인 불가.
- ERwin 임포트 경로가 존재하나 화면 진입 경로 / 사용 빈도는 본 분석에서 확인되지 않음 (컨트롤러/파서는 있음).

---

## 13. 한 눈에 보는 워크플로우

```
[모델 등록]
  └── 데이터소스 있음 ──→ [수집] ──→ DBMS 원본 스냅샷
  └── 데이터소스 없음 ──→ (논리 전용)
  └── ERwin XML 임포트 ──→ 외부 구조 스냅샷

[편집]  (TB_DATA_MODEL_OBJ / ATTR)
  ├── 테이블 추가/엑셀 업로드 ──→ TMP_TBL_N
  └── 컬럼 그리드 편집 / 엑셀 업로드 / TSV 붙여넣기 ──→ TMP_COL_N

[표준 적용]  (한글 → TB_TERMS + TB_DOMAIN)
  └── TMP_COL_N → 표준 기반 물리명 / 타입 / 길이
      실패 시 그리드 "변환 불가 사유" 컬럼 표시

[진단]
  ├── 표준 진단 ──→ 준수율 (이슈 컬럼 기준)
  └── 구조 진단 ──→ 스냅샷 diff

[출력]
  ├── 현황 대시보드 (논리/물리 뷰 토글)
  ├── DDL 다운로드 (모델 단위)
  └── 수집 이력 조회
```
