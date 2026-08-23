# DDL 변경 이력 (사람용 changelog)

`dataQ설계/DDL_full_schema.sql` 은 schema 의 단일 진실 (pg_dump 결과). 본 파일은 **무엇이 / 왜 / 언제** 바뀌었는지 한 줄씩 기록 — 다른 PC·세션이 컨텍스트 파악용.

---

## 형식

```
- YYYY-MM-DD | PC | 세션설명 | 변경 | 사유 | 적용 PC (PC1/PC2/양쪽)
```

---

## 이력

- 2026-05-06 | PC2 | DDL sync 정렬 | `tb_data_model_clct.added_cnt/deleted_cnt/modified_cnt INTEGER DEFAULT 0` 추가 | 44/48번 CLCT 폐기 재설계의 변경 통계 컬럼. PC1 만 적용돼 있던 누락분 PC2 에도 보충 | PC2 (PC1 이미 적용)
- 2026-05-06 | PC2 | DDL sync 정렬 | `tb_domain_rule.cret_user_id / updt_user_id`, `tb_qual_col_rule.updt_user_id`: VARCHAR(40) → VARCHAR(50) | PC1·PC2 길이 불일치 (40 vs 50). 큰쪽으로 통일 | PC2
- 2026-05-06 | PC2 | DDL sync 정렬 | `tb_data_model_attr.use_yn / tb_data_model_obj.use_yn / tb_domain_rule.use_yn / tb_qual_col_rule.exclude_yn`: CHAR(1) → VARCHAR(1) (DEFAULT cast 도 ::varchar) | PC1·PC2 cosmetic 차이 통일. PC1 표준이 varchar(1) | PC2
- 2026-05-06 | PC1 (예정) | DDL sync 정렬 | `tb_domain_rule.descr` VARCHAR(500) → TEXT, `sort_ord/use_yn` NOT NULL 추가 | PC1·PC2 의미 차이. PC2 가 더 strict/wider — 그 쪽으로 통일 | PC1 → `dataQ설계/sync/PC1_align_2026-05-06.sql` 실행 후 적용 완료 처리
- 2026-05-07 | PC2 | 83번 데이터 품질 진단 재설계 Step 1 | `TB_QUAL_RUNNING_LOCK` 신규 (PK: DM_ID+OBJ_NM+ATTR_NM, START_DT, USER_ID, DIAG_ID) | 컬럼 단위 application-level 동시 진단 방지 — 운영 DB 락 절대 X 원칙. stale lock 30분 경과 시 자동 정리 | PC2 (PC1 도 sync 필요)
- 2026-05-07 | PC2 | 83번 Step 2 | `TB_QUAL_RULE_CATALOG` 에 `IS_BUILT_IN VARCHAR(1) DEFAULT 'N'` + `DOMAIN_CLSF_NM VARCHAR(50)` 추가 + 인덱스 (DOMAIN_CLSF_NM, IS_BUILT_IN) | 시스템 기본 (Y, 읽기전용) vs 사용자 정의 (N) 분리 + 행안부 도메인 분류 자동 매칭 키 | PC2 (PC1 도 sync 필요) |
- 2026-05-07 | PC2 | 83번 Step 2 시드 | TB_QUAL_RULE_CATALOG 에 행안부 도메인 분류 시스템 기본 룰 43건 시드 (`CATALOG_ID LIKE 'SEED_%'`) | 33개 분류 자동 추천 + 공통 NOT_NULL. 시드 SQL: `dataQ설계/sync/qual_rule_catalog_seed_2026-05-07.sql` | PC2 (PC1 도 같은 시드 SQL 실행 필요) |
- 2026-05-07 | PC2 | 83번 Step 5 | `TB_QUAL_DIAG_HISTORY` 에 `PROGRESS_DONE INTEGER DEFAULT 0` + `PROGRESS_TOTAL INTEGER DEFAULT 0` 추가 | 진단 실행 시 컬럼 단위 진행률 추적 (실시간 폴링용). 30s setTimeout 대신 정확한 % 표시 | PC2 (PC1 도 sync 필요 — `dataQ설계/sync/qual_diag_progress_2026-05-07.sql`) |
- 2026-05-08 | PC2 | 85번 SFR-22 | `TB_DATA_MODEL_ATTR` 에 `FK_PARENT_OBJ_NM VARCHAR(255)` + `FK_PARENT_ATTR_NM VARCHAR(255)` 추가 | XMI 2.1 import/export 의 관계(type id 참조) 매핑용. FK 컬럼이 가리키는 부모 테이블/컬럼 보존 | PC2 (PC1 도 sync 필요 — `dataQ설계/sync/dm_attr_fk_parent_2026-05-08.sql`) |
- 2026-05-09 | PC2 | 86번 #11 OWNER PK 정합성 | `TB_DATA_MODEL_OBJ.OBJ_OWNER` / `TB_DATA_MODEL_ATTR.OBJ_OWNER` SET DEFAULT '' + SET NOT NULL, PK 재정의 — OBJ: (DM_ID, OBJ_OWNER, OBJ_NM), ATTR: (DM_ID, OBJ_OWNER, OBJ_NM, ATTR_NM) | 같은 OBJ_NM 다른 OWNER (스키마) 케이스 (예: SCHEMA_A.TB_USER vs SCHEMA_B.TB_USER) 동시 등록 가능. 매퍼 INSERT ON CONFLICT 컬럼셋 + 모든 WHERE/JOIN 도 OBJ_OWNER 매칭 추가 | PC2 (PC1 도 sync 필요 — DROP OLD PK + ADD NEW PK 동일 ALTER) |
- 2026-05-14 | PC | 88번 거버넌스 워크플로우 1단계 | (1) 5개 모델 테이블 (tb_data_model / _obj / _attr / _index / _constraint) 에 거버넌스 7컬럼 (aprv_status DEFAULT 'APPROVED' + requester_user_id + req_dt + aprv_user_id + aprv_dt + aprv_comment + submission_id) 추가. (2) tb_data_model_obj 에 tablespace_nm + biz_area_id + subj_area_id 추가. (3) tb_biz_area / tb_subj_area / tb_data_model_change_history 신규 (각 거버넌스 컬럼 포함). (4) 기존 row 일괄 APPROVED 마이그레이션. | 88_거버넌스_승인워크플로우 설계의 schema 기반 — DRAFT/SUBMITTED/APPROVED/REJECTED 상태머신 + 묶음 신청(submission_id) + 변경이력 추적. 적용 sql: `dataQ설계/sync/PC_align_2026-05-14_governance.sql` | 양쪽 (멱등 IF NOT EXISTS — 다른 PC pull 후 동일 SQL 적용)

- 2026-08-23 | PC | 진단 결과 소유자 구분 (결함 ⑩) | `TB_DIAG_RESULT` 에 `OBJ_OWNER VARCHAR(100)` 추가 + 인덱스 `IX_DIAG_RESULT_OWNER_OBJ (DIAG_JOB_ID, OBJ_OWNER, OBJ_NM, ATTR_NM)`. 기존 7,246행 중 (모델, 테이블명) 이 소유자 하나로만 존재하는 6,187행 백필, 다중 스키마 중복 1,059행은 복원 정보가 없어 NULL 유지 | 2026-05-09 OWNER PK 정합성 작업이 OBJ/ATTR 만 다루고 진단 결과 테이블을 빠뜨렸다. 그 결과 `R.OBJ_NM = O.OBJ_NM` 조인이 팬아웃해 같은 이름·다른 스키마 테이블이 서로의 이슈를 물려받았고, 오라클테스트 모델에서 "전체 테이블 19 / 이슈 테이블 22" 라는 불가능한 표시가 나왔다. 준수율(ISSUE_COL_CNT) 과 표준 flag 동기화(syncAttrStndYnFromDiag) 도 같은 이유로 부정확 | 양쪽 (멱등 `ADD COLUMN IF NOT EXISTS` + 백필 UPDATE)

- 2026-08-23 | PC | FK 부모 소유자 (DEF-08b / DEF-03) | `TB_DATA_MODEL_ATTR` 에 `FK_PARENT_OBJ_OWNER VARCHAR(100)` 추가. FK 참조 80행 중 부모 테이블 이름이 유일한 79행 백필, 1행은 부모 테이블이 모델에 없어 미확정 | `FK_PARENT_OBJ_NM` 만으로는 다중 스키마에서 부모를 특정할 수 없어 `clearFkParentRefByAttr` / `renameAttrInFkParent` / 반려 cascade 가 엉뚱한 스키마 행까지 건드렸다. 테이블 rename 시 자식의 `FK_PARENT_OBJ_NM` 갱신 누락(DEF-03)도 이 컬럼이 있어야 정확히 고칠 수 있다 | 양쪽 (멱등 `ADD COLUMN IF NOT EXISTS` + 백필 UPDATE)

- 2026-08-23 | PC | 품질 진단 소유자 구분 | `TB_QUAL_COL_RULE` / `TB_QUAL_RULE_RESULT` / `TB_QUAL_RUNNING_LOCK` 에 `OBJ_OWNER VARCHAR(100)` 추가. col_rule 46행 중 45행 백필 | 결함 ⑩ 과 같은 계열. 이 3개 테이블만 `OBJ_OWNER` 가 없어 `qualColRule.xml` / `qualDiag.xml` 조인이 다중 스키마에서 팬아웃한다. 품질 진단 메뉴는 비활성이지만 서버 기능은 살아 있어 함께 정리 | 양쪽 (멱등)

- 2026-08-23 | PC | 품질 컬럼 룰 식별 키 정정 | `TB_QUAL_COL_RULE.OBJ_OWNER` DEFAULT `''` + NOT NULL, PK 재정의 `(DM_ID, OBJ_OWNER, OBJ_NM, ATTR_NM)`. `TB_QUAL_PROFILE_RESULT` / `TB_QUAL_VIOLATION_SAMPLE` 에도 `OBJ_OWNER` 추가 (profile_result 46행 백필) | 조회 조인에만 소유자를 넣고 **쓰기 경로를 안 고쳐** 저장은 되는데 화면에 반영이 안 되는 상태가 됐다 (회귀 `test_qual_col_rule` P8 로 발각). 키 컬럼을 추가할 땐 INSERT·ON CONFLICT·DELETE·VO·컨트롤러까지 같이 가야 한다 | 양쪽 (NULL → `''` 정규화 후 PK 교체)

---

## 79번 진단 제외 관리 (직전 작업)

- 2026-05-04 | PC2 | 79번 진단 제외 관리 | `tb_data_model_obj` + `tb_data_model_attr` 에 `STND/STRUCT/QUAL × TARGET_YN+REASON` + `DIAG_TARGET_UPDT_USER_ID` + `DIAG_TARGET_UPDT_DT` (8 컬럼 × 2 테이블) DEFAULT 'Y' | 표준/구조/품질 진단 대상 OBJ/ATTR 단위 제외 + 사유 + 변경 이력 | 양쪽 (PC1 b5a56e0 commit 으로 자동 동기화)

---

## 사고 케이스 (반복 방지)

| 사고 | 일자 | 원인 | 대응 |
|---|---|---|---|
| `0281003` PC2 미커밋 | 2026-04-22 | DDL 변경 적용 후 파일 갱신 누락 | DDL_full_schema.sql + DDL_변경이력.md 두 파일 동시 갱신 의무화 |
| 5월 PC1·PC2 schema 불일치 | 2026-05-04 ~ 06 | PC1 만 ALTER 적용된 컬럼 다수 (added/deleted/modified_cnt 등) | 본 파일 도입. push 전 self-check + sync ALTER 디렉토리 운영 |
| 진단 결과 OBJ_OWNER 누락 | 2026-05-09 도입 → 2026-08-23 발견 | OWNER 를 PK 에 넣는 작업을 OBJ/ATTR 두 테이블에만 하고, 그 두 테이블을 **참조하는** TB_DIAG_RESULT 를 빠뜨림. 3개월간 준수율이 틀린 채로 표시됨 | 식별 키를 바꿀 때는 그 키로 조인되는 **모든** 테이블을 함께 훑는다. 체크 명령: `grep -rn "OBJ_NM *= *[A-Z]\.OBJ_NM" q-common/src/main/resources/mapper/` 로 owner 조건 없는 조인을 찾는다 |

---

## 신규 환경 구축 절차

```bash
# 1. 빈 dataq-db 컨테이너 기동
# 2. quality 스키마 + 사용자 생성 (필요 시)
# 3. 단일 파일 적용
docker exec -i dataq-db psql -U admin -d postgres < dataQ설계/DDL_full_schema.sql
# 4. ndata.tb_data_source 도 별도 적용 (dataQ 앱에서 사용)
# 5. 시드 데이터: dict_seed_*.sql 적용
```
