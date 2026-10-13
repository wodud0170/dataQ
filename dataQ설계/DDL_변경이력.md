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
| 덤프가 이전 ALTER 를 덮어씀 | 2026-09-07 (4393879) → 2026-10-12 발견 | 98번 스키마를 받은 DB 가 2026-08-23 ALTER (22187df / 3389f4f) 를 안 받은 DB 였다. 그 DB 를 pg_dump 해서 DDL_full_schema.sql / 01_schema.sql 을 덮어써, 커밋돼 있던 OBJ_OWNER 7컬럼·col_rule PK·인덱스·COMMENT 23건이 diff 상 **삭제**로 들어갔고 리뷰 없이 지나갔다. 매퍼는 계속 그 컬럼을 써서 표준 진단 결과 저장 등이 SQL 에러 | 덤프로 파일을 갱신한 뒤 `git diff` 에 **`-` 로 빠지는 컬럼·제약·COMMENT 가 있으면 멈춘다** — 이번 변경이 지운 게 아니면 덤프 뜬 DB 가 뒤처진 것. 덤프 전 `DDL_full_schema.sql` 과 현재 DB 덤프를 먼저 diff 해 DB 가 파일보다 뒤처져 있지 않은지 확인 |

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

- 2026-09-03 | PC | 98번 표준사전 세트 1단계 | (1) `TB_STD_DICT` 신규 (dict_id PK, dict_nm UQ, default_yn, use_yn, **term_last_word_clsf_yn** — 사전별 표준화 관례). (2) 사전 계열 6개 테이블 (tb_word / tb_terms / tb_domain / tb_domain_grp / tb_domain_clsf / tb_code_data) 에 `dict_id NOT NULL DEFAULT 'DEFAULT'` 추가. (3) `tb_data_model` / `tb_diag_job` / `tb_diag_schedule` 에 `dict_id` 추가 (NULL 허용). (4) 이름 유니크 8개를 `(dict_id, 이름)` 복합으로 재정의 + 같은 컬럼에 두 벌씩 걸려 있던 중복 인덱스 정리 (uix_word_nm / uix_word_eng_abrv_nm / uix_terms_nm / uix_domain_nm 제거). (5) **이름 참조 FK 4개를 `(dict_id, 이름)` 복합으로 재정의** — tb_domain→tb_domain_grp, tb_domain→tb_domain_clsf, tb_domain_clsf→tb_domain_grp, tb_terms→tb_domain. (6) 기존 데이터 전량 (`단어 3,290 / 용어 13,159 / 도메인 126 / 모델 63 / 진단잡 50`) 을 'DEFAULT' 사전에 귀속 — 행 변경·삭제 없음 | 한 인스턴스에서 표준사전을 여러 벌 굴리기 위함. 계기는 국가기록원(RAMS) SMETA 자료 적재 — 기존 행안부 공통표준 사전과 섞이는데, 일괄등록이 중복을 실패가 아니라 **skip** 으로 넘겨서 (ExcelUploadService:87) 다른 뜻의 같은 영문약어가 조용히 누락되고 "성공" 으로 보고된다. 설계: `dataQ설계/98_표준사전_세트_설계_2026-09-03.md`, 적용 sql: `dataQ설계/sync/std_dict_2026-09-03.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC1 도 같은 SQL 적용 필요)
  - **주의 — FK 가 이름으로 참조하고 있었다.** `tb_word_ux_1 (word_id, word_nm)` 은 `tb_terms_words_fk_2` 가, `tb_domain_ux_1 (domain_nm)` 은 `tb_terms_fk` 가 붙잡고 있어 DROP 이 거부된다. 전자는 word_id 가 전역 UUID 라 그대로 두고 사전별 유니크는 `tb_word_ux_3 (dict_id, word_nm)` 로 신설, 후자는 FK 를 복합으로 재생성하는 방식으로 풀었다. 이 덕에 **사전 간 참조(RAMS 용어 → DEFAULT 도메인)를 DB 가 막아준다** — 실제로 시험해 차단 확인

- 2026-10-12 | PC | 2026-08-23 변경분 복구 (회귀 수정 세션) | `tb_data_model_attr.fk_parent_obj_owner`, `tb_diag_result.obj_owner` + `ix_diag_result_owner_obj`, `tb_qual_col_rule.obj_owner DEFAULT '' NOT NULL` + `pk_tb_qual_col_rule (dm_id, obj_owner, obj_nm, attr_nm)`, `tb_qual_profile_result` / `tb_qual_rule_result` / `tb_qual_running_lock` / `tb_qual_violation_sample` `.obj_owner` 재추가, 컬럼 COMMENT 23건 복원. 백필: diag_result 14,322행 중 14,249행, FK 부모 78행 전부 (소유자 유일한 경우만) | 2026-09-07 (4393879) 덤프가 08-23 ALTER 를 안 받은 DB 에서 떠져 위 항목이 스키마 파일과 로컬 DB 에서 함께 사라졌다 (사고 케이스 참조). 매퍼는 계속 사용 중이라 표준 진단 결과 INSERT 등이 실패. 적용 sql: `dataQ설계/sync/PC_align_2026-10-12_owner_restore.sql` (멱등) | 양쪽 (다른 PC 도 같은 SQL 적용 필요 — 이미 있으면 건너뜀)

- 2026-10-13 | 104 WP2 세션 | PC1 | 역할·권한·시스템 계층 | (1) `TB_ROLE`(역할, ROLE_NM 유일, BUILTIN_YN) · `TB_ROLE_PERM`(역할의 권한 코드, 역할 삭제 시 CASCADE) · `TB_USER_ROLE`(사용자 역할, SYS_CD 기본 '*'=모든 시스템, 역할·사용자 삭제 시 CASCADE) + `ix_user_role_role`. (2) `TB_SYS_INFO.DICT_ID`(시스템 기본 표준사전). (3) 내장 역할 3개 시드 — 표준 담당·모델 담당·DB 담당(권한 10건). 권한 코드 목록은 q-common `PermCode` 가 기준 | 지금까지 관리자만 하던 일을 권한 코드 14개로 나눠 역할로 줄 수 있게 한다(104번 §2 WP2, §3.3 역할 테이블은 quality 에 새로). 관리자(ADM_YN)는 모든 권한. 적용 sql: `dataQ설계/sync/PC_align_2026-10-13_wp2_role.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요 — 이미 있으면 건너뜀)
  - **덤프 갱신 방식 — 이번만 예외.** PC1 DB 를 pg_dump 하면 WP2 객체 말고도 PC2 덤프와 다른 곳이 나온다: DQ 테이블 주석 5건의 문구, `tb_event_log` 의 TimescaleDB 트리거(`ts_insert_blocker`, PC2 에만 있음), 일부 테이블의 컬럼 순서. 그대로 올리면 PC1 쪽 차이가 공유 덤프에 섞인다. 그래서 두 덤프에는 **PC1 pg_dump 출력에서 WP2 객체 블록만 떼어 pg_dump 가 두는 위치에 그대로 넣었다**(글자 단위로 같은지 확인, 빈 DB 에 적재 시험 통과). PC 간 차이를 어느 쪽으로 맞출지는 결정 대기(108번 §2-3)
  - **시드는 덤프에 없다.** 덤프는 스키마 전용이라 내장 역할 3개는 sync 파일로만 들어간다. 신규 환경에서 내장 역할이 필요하면 sync 파일을 한 번 적용한다
- 2026-10-13 | 104 WP3 세션 | PC1 | DBMS 별 DDL 규칙 | (1) `TB_DATA_TYPE_MAP`(데이터 타입 → DBMS 별 물리 타입 틀, PK DATA_TYPE+DBMS_TP). (2) `TB_SYS_CONF`(설정, PK CONF_KEY). (3) `TB_DATA_MODEL_CHANGE_HISTORY.DDL_DBMS_TP`(조각을 만든 DBMS). (4) 시드 — 타입 매핑 155행(Oracle·Tibero·PostgreSQL·MariaDB·SQLServer 각 31종), `DDL_PK_NM_TMPL`='PK_{TABLE}' | 변경 조각·DDL 내려받기·[DB 반영] 을 모델 데이터소스의 DBMS 문법으로 만들고, 타입 변환과 PK 이름 규칙을 관리 화면에서 바꿀 수 있게 한다(104번 §2 WP3). 적용 sql: `dataQ설계/sync/PC_align_2026-10-13_wp3_ddl_rule.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP2 와 같은 방식(PC1 pg_dump 출력에서 WP3 객체 블록만 같은 위치에 넣음). 시드(타입 매핑·PK 이름 규칙)는 덤프에 없으므로 신규 환경에서도 sync 파일을 한 번 적용해야 DDL 규칙이 동작한다 — 매핑 행이 없으면 타입 이름 그대로 나가고, PK 이름 규칙이 없으면 PK 자동 이름을 만들 수 없다.
- 2026-10-14 | 104 WP1 세션 | PC1 | 결재선·조직·알림 | (1) `TB_ORG`(부서 트리, HEAD_USER_ID 부서장, 상위 부서 FK) + `TB_USER.ORG_ID`. (2) `TB_APRV_LINE`(요청 유형별 결재선, PK REQ_TP) · `TB_APRV_LINE_STEP`(단계, 결재자 방식 USER·ROLE·ADMIN·ORG_HEAD, 합의). (3) `TB_APRV_DOC`(결재 문서, 대상=표준사전 항목 ID 또는 SUBMISSION_ID) · `TB_APRV_DOC_STEP`(단계별 결재자·결과·의견, 문서 삭제 시 CASCADE) + 인덱스 3개. (4) `TB_NOTIFICATION`(사용자 알림, bigserial) + 인덱스. (5) `TB_CHANGE_HISTORY.CHANGE_SOURCE` 주석에 APPROVER_EDIT | 두 승인 흐름 위에 결재 문서를 얹어 다단계 결재·부서장 결재·사용자 알림을 한다(104번 §2 WP1). 시드 없음 — 결재선이 없으면 지금처럼 동작. 적용 sql: `dataQ설계/sync/PC_align_2026-10-14_wp1_aprv.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP2·WP3 와 같은 방식(PC1 pg_dump 출력에서 WP1 객체 블록만 같은 위치에 넣음). `01_schema.sql` 은 ndata 스키마에도 같은 이름의 객체(tb_user_role 등)가 있어 위치를 quality 쪽으로 맞춤. 빈 DB 에 01_schema 적재 확인.
- 2026-10-14 | 104 WP5 세션 | PC1 | 개인정보·암호화 표시 + 범정부 메타 구현 ① | (1) 16번: `TB_DOMAIN.PRIV_YN·ENCR_YN`(기본 'N'), `TB_TERMS.PRIV_YN·ENCR_YN`, `TB_DATA_MODEL_ATTR.PRIV_YN·ENCR_YN`(NULL 이면 위 단계 값 — 컬럼 → 용어 → 도메인). (2) 78번 테이블·컬럼 항목: `TB_DATA_MODEL_OBJ` 7열(GOV_SEND_YN·TBL_TP·OCCR_CYCLE·OPEN_TP·OPEN_LIMIT_REASON·OPEN_LIMIT_BASIS·OPEN_DATA_LST_NM), `TB_DATA_MODEL_ATTR` 4열(DBMS_DATA_TYPE 수집 원본 타입·OPEN_TP·OPEN_LIMIT_REASON·CNSTR_DESC). (3) 새 테이블 7개: `TB_GOV_META_CONF`(기관·연계, SFTP 비밀번호 암호화), `TB_GOV_BRM`(업무분류체계, 인덱스 1), `TB_GOV_META_SYS`(정보시스템 항목), `TB_GOV_META_DB`(DB 항목·대표 모델·수집한 DBMS 제품/버전), `TB_GOV_META_CODE_MAP`(우리 값 → 범정부 코드), `TB_GOV_META_SEND`(생성 이력, 인덱스 1)·`TB_GOV_META_SEND_ERR`(검증 결과, 이력 삭제 시 CASCADE). (4) `TB_DIAG_SCHEDULE_LOG.GOV_META_SEND_ID`, `TB_DIAG_SCHEDULE.DIAG_TYPE` 주석에 GOV_META | 104번 §2 WP5(16·78 구현 ①), 105번 §6. 시드 없음 — 코드표·규격을 받으면 TB_GOV_META_CODE_MAP 에 데이터만 넣는다. 적용 sql: `dataQ설계/sync/PC_align_2026-10-14_wp5_gov_meta.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP1 과 같은 방식. 컬럼이 늘어난 기존 테이블 5개는 PC1 덤프의 테이블 블록으로 바꾸고(차이가 WP5 컬럼뿐인 것 확인), 새 객체 블록은 덤프 순서대로 같은 자리에 넣었다.
- 2026-10-14 | 104 WP6 세션 | PC1 | 표준사전 구조 보강 | (1) 13번 운영 옵션: `TB_STD_DICT.UPPER_YN`(기본 'Y')·`WORD_ENG_NM_DUP_YN`(기본 'Y')·`TERM_ENG_MAX_LEN`(NULL=제한 없음)·`OLD_STD_YN`(기본 'N'). (2) 4번 도메인 그룹 다단계: `TB_DOMAIN_GRP.PARENT_GRP_ID`(자기 참조 FK `TB_DOMAIN_GRP_FK_PARENT`, 인덱스 `IX_DOMAIN_GRP_PARENT`)·`DFLT_DATA_TYPE`·`CLSF_WORD_NM`·`SORT_ORDER`(기본 0). (3) 3번 코드 유형: `TB_TERMS.CODE_TP`(SINGLE·HIER·DERIV·INTEG·INDIV, NULL 이면 단일)·`PARENT_CODE_GRP`, `TB_CODE_DATA.PARENT_CODE_VAL`, 새 테이블 `TB_CODE_INTEG`(통합 코드 구성, PK DICT_ID+CODE_GRP+SUB_CODE_GRP). (4) 15번 옛 표준 매핑: 새 테이블 `TB_STD_MAP`(PK MAP_ID, 인덱스 `IX_STD_MAP_OLD_ENG`) | 104번 §2 WP6(3·4·13·15). 기본값이 지금 동작과 같아 기존 사전·코드는 그대로 동작. 시드 없음. 적용 sql: `dataQ설계/sync/PC_align_2026-10-14_wp6_std_struct.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP5 와 같은 방식. 컬럼이 늘어난 기존 테이블 4개(tb_std_dict·tb_domain_grp·tb_terms·tb_code_data)는 PC1 덤프의 테이블 블록으로 바꾸고(차이가 WP6 컬럼뿐인 것 확인), 새 객체 블록(테이블 2·제약·인덱스·주석)은 덤프 순서대로 같은 자리에 넣었다.
- 2026-10-14 | 104 WP7 세션 | PC1 | 표준 입력 통제 | `TB_DATA_MODEL.STND_REQ_YN`(표준 준수 필수, 기본 'N' — 지금 동작), `TB_DATA_MODEL_CHANGE_HISTORY.STND_EXCEPT_REASON`(표준 예외 신청 사유) | 104번 §2 WP7-25. 표준 준수 필수 모델은 비표준 컬럼 추가·수정을 거부하고, 예외는 사유와 함께 데이터 모델 변경 승인(결재)으로. 시드 없음. 적용 sql: `dataQ설계/sync/PC_align_2026-10-14_wp7_std_control.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP6 과 같은 방식. 컬럼이 늘어난 기존 테이블 2개(tb_data_model·tb_data_model_change_history)는 PC1 덤프의 테이블 블록으로 바꾸고, 새 주석 블록 2개를 덤프 순서대로 넣었다.
- 2026-10-14 | 104 WP5(14·11) 세션 | PC1 | 참조 사전·상위 사전 | `TB_STD_DICT.DICT_TP`(ORG 기관 사전 / REF 참조 사전 — 읽기 전용, 기본 'ORG'), `TB_STD_DICT.PARENT_DICT_ID`(상위 사전, 자기 참조 FK `TB_STD_DICT_FK_PARENT`) | 104번 §2 WP5 14번(공통표준 골라 넣기 — 참조 사전에서 기관 사전으로 가져오기)·11번(상위 표준 상속 — 진단·컬럼 표준 검사·목록이 상위 사전도 본다). 기존 사전은 모두 ORG·상위 없음(지금 동작). 시드 없음. 적용 sql: `dataQ설계/sync/PC_align_2026-10-14_wp5_std_ref.sql` (멱등, 롤백 블록 포함) | 양쪽 (PC2 도 같은 SQL 적용 필요)
  - 덤프 갱신은 WP7 과 같은 방식. 컬럼이 늘어난 기존 테이블 1개(tb_std_dict) 블록 교체, 새 주석 2개·FK 1개를 덤프 순서대로 넣었다.
