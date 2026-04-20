# CLCT 폐기 영향도 분석 및 작업 계획

**작성일**: 2026-04-20
**현재 상태**: DB 이관 완료 (PK 변경, STATS DROP), 일부 매퍼/프론트 수정 완료, 잔여 에러 있음

---

## 1. 완료된 작업

| 항목 | 상태 |
|------|------|
| DB: OBJ/ATTR PK 변경 (DM_CLCT_ID → DM_ID) | ✅ |
| DB: USE_YN, DELETED_DT 컬럼 추가 | ✅ |
| DB: TB_DATA_MODEL_STATS DROP | ✅ |
| DB: CLCT에 수집로그 컬럼 추가 | ✅ |
| VO: ObjVo/AttrVo에 useYn/deletedDt 추가 | ✅ |
| Mapper: OBJ/ATTR INSERT → UPSERT 변경 | ✅ |
| Mapper: 소프트 삭제 쿼리 추가 | ✅ |
| Mapper: selectDataModelStatsList STATS JOIN → 실시간 COUNT | ✅ |
| Service: DataModelService STATS INSERT 제거 + 소프트 삭제 호출 | ✅ |
| 프론트: 테이블/컬럼/인덱스/제약조건 수집일시 드롭다운 제거 | ✅ |
| 프론트: 표준진단/구조진단 수집일시 드롭다운 제거 | ✅ |

---

## 2. 잔여 에러 — TB_DATA_MODEL_STATS 참조

STATS 테이블을 DROP했는데 아직 참조하는 곳이 남아있어 에러 발생.

### 2-1. 매퍼 (즉시 수정 필요)

| # | 파일 | SQL ID | 라인 | 내용 | 수정 방향 |
|---|------|--------|------|------|----------|
| 1 | datamodel.xml | `selectDataModelHistoryList` | 369 | STATS LEFT JOIN | JOIN 제거, OBJ/ATTR 실시간 COUNT |
| 2 | search.xml | `selectTopDataModelList` | 98 | STATS JOIN으로 준수율 계산 | DIAG_JOB 기반으로 변경 |

### 2-2. 컨트롤러 (즉시 수정 필요)

| # | 파일 | 메서드 | 라인 | 내용 | 수정 방향 |
|---|------|--------|------|------|----------|
| 3 | DataModelController.java | ERwin 임포트 | 577-580 | `insertDataModelStats` 호출 | 호출 제거 |
| 4 | DataModelController.java | `getDataModelStatsByClctId` | 236-238 | STATS 기반 조회 | 실시간 COUNT로 변경 또는 제거 |

### 2-3. VO (나중에 정리)

| # | 파일 | 내용 | 수정 방향 |
|---|------|------|----------|
| 5 | StdDataModelStatsVo.java | STATS VO | 당장 삭제하면 참조처 전부 에러. resultMap에서 사용 중이므로 단계적 제거 |
| 6 | StdDataModelVo.java:21 | `dataModelStats` 필드 | StatsVo 폐기 시 함께 제거 |
| 7 | datamodel.xml:20-31 | `stdDataModelStatsMap` resultMap | StatsVo 폐기 시 함께 제거 |

---

## 3. 잔여 에러 — DM_CLCT_ID 참조

### 3-1. 매퍼에서 DM_CLCT_ID 직접 사용 (백엔드 156건)

**핵심 수정 대상** (나머지는 SELECT alias로 남겨둬도 됨):

| # | 파일 | SQL ID | 내용 | 수정 방향 |
|---|------|--------|------|----------|
| 8 | datamodel.xml | `selectDataModelHistoryList` | DM_CLCT_ID 기반 서브쿼리 | DM_ID 기반으로 변경 |
| 9 | datamodel.xml | `selectDataModelAttrListByRetreiveCond` | DM_CLCT_ID WHERE | DM_ID WHERE로 변경 |
| 10 | datamodel.xml | `insertDataModelIndex/Constraint` | DM_CLCT_ID INSERT | DM_CLCT_ID 유지 (수집 로그 참조용) |
| 11 | datamodel.xml | `selectDataModelIndexListByClctId` | DM_CLCT_ID WHERE | DM_ID 기반으로 변경 (이미 DmId 버전 추가됨) |
| 12 | structdiag.xml | `selectRecentClctIds` | CLCT 기반 최신 2건 | DM_ID 기반으로 변경 |

### 3-2. 프론트에서 clctId/clctList 참조 (94건)

**수정 완료된 화면:**
- DSDatamodelStatusTable ✅
- DSDatamodelStatusColumn (일부) 
- DSDatamodelStatusIndex ✅
- DSDatamodelStatusConstraint ✅
- DSStructDiag ✅
- DSDataDiag ✅

**미수정 화면:**

| # | 파일 | 내용 | 수정 방향 |
|---|------|------|----------|
| 13 | DSDatamodelStatusColumn.vue | clctList/selectedClctId 잔여 로직 | DM_ID 기반으로 정리 |
| 14 | DSDatamodelStatus.vue | 모델 현황 하단 상세에서 CLCT 참조 | DM_ID 기반으로 변경 |
| 15 | DSDatamodelHistory.vue | 수집이력 화면 — CLCT 목록 표시 | CLCT는 수집 로그로 유지, STATS JOIN만 제거 |
| 16 | DSDatamodelCollection.vue | 수집 버튼/이력 — CLCT 관련 | 수집 로그로 유지 |
| 17 | DSStructDiagResult.vue | 수집일시 기반 필터 | 진단이력 기반으로 단순화 |
| 18 | DSSchemaCompare.vue | 수집일시 드롭다운 | 제거 |
| 19 | QDashboard.vue | 모델 통계 STATS 참조 | 이미 수정됨 (selectDataModelStatsList 변경) |

---

## 4. 작업 순서 (우선순위)

### Phase A: 에러 해소 (즉시)

| # | 작업 | 파일 |
|---|------|------|
| A1 | selectDataModelHistoryList STATS JOIN 제거 | datamodel.xml |
| A2 | selectTopDataModelList STATS JOIN 제거 | search.xml |
| A3 | ERwin 임포트 insertDataModelStats 호출 제거 | DataModelController.java |
| A4 | getDataModelStatsByClctId 실시간 COUNT로 변경 | datamodel.xml + DataModelController.java |

### Phase B: 프론트 정리

| # | 작업 | 파일 |
|---|------|------|
| B1 | DSDatamodelStatusColumn clctList 잔여 제거 | DSDatamodelStatusColumn.vue |
| B2 | DSDatamodelStatus CLCT 참조 정리 | DSDatamodelStatus.vue |
| B3 | DSStructDiagResult 수집일시 필터 단순화 | DSStructDiagResult.vue |
| B4 | DSSchemaCompare 수집일시 제거 | DSSchemaCompare.vue |

### Phase C: 구조 진단 CLCT 참조 제거

| # | 작업 | 파일 |
|---|------|------|
| C1 | StructDiagService에서 CLCT 기반 → DM_ID 기반 | StructDiagService.java |
| C2 | structdiag.xml selectRecentClctIds 변경 | structdiag.xml |

### Phase D: VO/resultMap 정리 (최종)

| # | 작업 | 파일 |
|---|------|------|
| D1 | StdDataModelStatsVo 폐기 | StdDataModelStatsVo.java |
| D2 | StdDataModelVo에서 dataModelStats 필드 제거 | StdDataModelVo.java |
| D3 | stdDataModelStatsMap resultMap 제거 | datamodel.xml |
