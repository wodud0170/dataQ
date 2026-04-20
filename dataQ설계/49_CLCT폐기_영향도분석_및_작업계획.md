# CLCT 폐기 영향도 분석 및 작업 계획

**작성일**: 2026-04-20
**최종 갱신**: 2026-04-20 (Phase A/B/C/D 전부 완료)
**현재 상태**: ✅ 전 단계 완료. STATS 테이블/VO 제거, CLCT→DM_ID 이관 마무리.

---

## 1. 완료된 작업 (DB·백엔드·프론트·VO 전수)

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

## 2. Phase A — STATS 참조 제거 (완료)

| # | 파일 | 항목 | 상태 |
|---|------|------|------|
| A1 | datamodel.xml | `selectDataModelHistoryList` STATS JOIN 제거 → 실시간 COUNT + USE_YN='Y' | ✅ |
| A2 | search.xml | `selectTopDataModelList` STATS JOIN 제거 → TB_DIAG_JOB 기반 | ✅ |
| A3 | DataModelController.java | ERwin 임포트의 `insertDataModelStats` 호출 제거 | ✅ |
| A4 | datamodel.xml + Controller | `selectDataModelStatsByClctId` 실시간 COUNT로 전환 (STATS JOIN 없음) | ✅ |

---

## 3. Phase B — 프론트 CLCT 참조 정리 (완료)

커밋 `19cc4c0`에서 수집일시 드롭다운 전면 제거.

| # | 파일 | 상태 |
|---|------|------|
| B1 | DSDatamodelStatusColumn.vue `clctList` 잔재 제거 | ✅ |
| B2 | DSDatamodelStatus.vue CLCT 참조 정리 | ✅ |
| B3 | DSStructDiagResult.vue 수집일시 필터 단순화 | ✅ |
| B4 | DSSchemaCompare.vue 수집일시 제거 | ✅ |
| B5 | DSDataDiag.vue / DSStructDiag.vue 드롭다운 제거 | ✅ |

---

## 4. Phase C — StructDiagService CLCT→DM_ID (완료)

| # | 파일 | 상태 |
|---|------|------|
| C1 | StructDiagService.java `DM_CLCT_ID`/`CLCT_ID` 참조 0건 | ✅ |
| C2 | structdiag.xml DM_ID 기반 스냅샷 로드 전환 | ✅ |

---

## 5. Phase D — VO/resultMap 정리 (완료)

| # | 파일 | 상태 |
|---|------|------|
| D1 | `StdDataModelStatsVo.java` 파일 삭제 | ✅ |
| D2 | StdDataModelVo `dataModelStats` 필드 제거 | ✅ |
| D3 | `stdDataModelStatsMap` resultMap 제거 | ✅ |

---

## 6. 잔존 `DM_CLCT_ID` 사용처 (정상 — 유지)

수집 로그 참조용으로 의도적으로 남긴 컬럼:
- `TB_DATA_MODEL_CLCT.DM_CLCT_ID` (수집 이력 PK 자체)
- `TB_DATA_MODEL_INDEX.DM_CLCT_ID` / `TB_DATA_MODEL_CONSTRAINT.DM_CLCT_ID` (수집 로그 참조)
- OBJ/ATTR SELECT 절의 `T.DM_CLCT_ID as clctId` alias (UI 호환용)

---

## 7. 검증 방법

1. q-center 기동 후 로그에 `TB_DATA_MODEL_STATS` 참조 에러가 없어야 함
2. Selenium:
   - `dataQ설계/테스트/selenium/test_clct_migration.py` — 수집일시 드롭다운 제거/대시보드/테이블·컬럼/구조진단
   - `dataQ설계/테스트/selenium/test_logical_model_to_ddl.py` — 논리모델 생성→테이블 추가→표준 적용→DDL
