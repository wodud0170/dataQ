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

---

## 79번 진단 제외 관리 (직전 작업)

- 2026-05-04 | PC2 | 79번 진단 제외 관리 | `tb_data_model_obj` + `tb_data_model_attr` 에 `STND/STRUCT/QUAL × TARGET_YN+REASON` + `DIAG_TARGET_UPDT_USER_ID` + `DIAG_TARGET_UPDT_DT` (8 컬럼 × 2 테이블) DEFAULT 'Y' | 표준/구조/품질 진단 대상 OBJ/ATTR 단위 제외 + 사유 + 변경 이력 | 양쪽 (PC1 b5a56e0 commit 으로 자동 동기화)

---

## 사고 케이스 (반복 방지)

| 사고 | 일자 | 원인 | 대응 |
|---|---|---|---|
| `0281003` PC2 미커밋 | 2026-04-22 | DDL 변경 적용 후 파일 갱신 누락 | DDL_full_schema.sql + DDL_변경이력.md 두 파일 동시 갱신 의무화 |
| 5월 PC1·PC2 schema 불일치 | 2026-05-04 ~ 06 | PC1 만 ALTER 적용된 컬럼 다수 (added/deleted/modified_cnt 등) | 본 파일 도입. push 전 self-check + sync ALTER 디렉토리 운영 |

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
