# 데이터 품질 진단 — 테스트 SQL 셋

본 디렉토리는 67/68/69번 설계의 **값 진단·업무 규칙 진단** 검증용 SQL 패키지다.
모든 SQL 은 **dataq-db (PostgreSQL 13, 외부 25433)** 의 testdata + quality + ndata 스키마에 적용된다.

## 파일 목록

| 순서 | 파일 | 내용 | 적용 영향 |
|---|---|---|---|
| 01 | `01_qual_test_ddl.sql` | testdata 스키마 + 진단 대상 테이블 3종 DDL | 신규 스키마 + 신규 테이블 |
| 02 | `02_qual_test_data.sql` | 시나리오별 INSERT (정상 + 위반 행 섞임) | 139 행 (Member 55 + Order 50 + Product 34) |
| 03 | `03_qual_test_metadata.sql` | dataQ 의 ndata.TB_DATA_SOURCE + quality.TB_DATA_MODEL/OBJ/ATTR INSERT | DSN 1 / 모델 1 / OBJ 3 / ATTR 22 |
| 04 | `04_qual_test_rules.sql` | TB_QUAL_RULE INSERT (16개 룰) | 룰 16건 |
| 99 | `99_qual_test_cleanup.sql` | 위 모든 항목 + 진단 결과/이력 일괄 삭제 | 위 항목만 cascade |
| - | `apply_all.ps1` | 01~04 SQL 일괄 적용 (PowerShell) | docker cp + psql -f |

## 사용 흐름

### 1. 적용 (PowerShell)
```powershell
cd dataQ설계\테스트\sql
.\apply_all.ps1
```

### 2. dataQ UI 에서 진단 실행
```
1) http://localhost:28091/login → space / 123 (관리자)
2) 좌측 메뉴 [데이터 품질 진단] > [업무 규칙 관리]
3) 모델 드롭다운 → TEST_QUAL_MODEL 선택
4) 룰 16개 표시 확인 → [진단 실행] 클릭 (sampleRate=100, 증분 X)
5) [업무 규칙 진단 결과] 메뉴에서 diagId 입력 후 [조회]
   → 룰별 위반 카운트가 아래 "예상 위반 표"와 일치하는지 확인
```

### 3. 정리
```powershell
docker cp 99_qual_test_cleanup.sql dataq-db:/tmp/
docker exec dataq-db psql -U admin -d postgres -f /tmp/99_qual_test_cleanup.sql
```

---

## 시나리오별 데이터 분포

### TB_TEST_MEMBER (55건)

| 분류 | 건수 | MEMBER_ID 범위 |
|---|---|---|
| 정상 | 35 | M00000001 ~ M00000035 |
| EMAIL NULL | 5 | M00000036 ~ M00000040 |
| EMAIL 형식 위반 | 3 | M00000041 ~ M00000043 |
| PHONE NULL | 3 | M00000044 ~ M00000046 |
| PHONE 형식 위반 | 2 | M00000047 ~ M00000048 |
| AGE 범위 위반 | 4 | M00000049 ~ M00000052 |
| GENDER ENUM 위반 | 3 | M00000053 ~ M00000055 |

### TB_TEST_ORDER (50건)

| 분류 | 건수 | 범위 |
|---|---|---|
| 정상 | 32 | O00000001 ~ O00000032 |
| MEMBER_ID NULL | 2 | O00000033 ~ O00000034 |
| 회원 미등록 (REFERENCE) | 4 | O00000035 ~ O00000038 |
| AMOUNT 음수 | 5 | O00000039 ~ O00000043 |
| STATUS ENUM 위반 | 3 | O00000044 ~ O00000046 |
| START > END (COMPARE) | 4 | O00000047 ~ O00000050 |

### TB_TEST_PRODUCT (34건, PK 미정의)

| 분류 | 건수 | 비고 |
|---|---|---|
| 정상 | 20 | PRD00001 ~ PRD00020 (CODE 정확히 8자) |
| CODE 길이 위반 | 3 | SHORT / VERYLONG_CODE_22 / TOOSHORT |
| NAME NULL | 2 | PRD90001 ~ PRD90002 |
| PRICE 음수 | 3 | PRD90003 ~ PRD90005 |
| CATEGORY ENUM 위반 | 2 | PRD90006 / PRD90007 |
| CODE 중복 | 4 (2쌍) | DUP00001 × 2, DUP00002 × 2 |

---

## 등록되는 룰 16개 + 예상 위반

> 한 행이 여러 룰을 위반할 수 있으므로 **위반 합 ≠ 위반 행 수**.
> 아래 표는 풀스캔(100%) 기준이며 샘플링 시 비율적 검출 예상.

| # | 룰명 | 대상 | 유형 | 예상 위반 | 비고 |
|---|---|---|---|---|---|
| 1 | EMAIL_NOT_NULL | TB_TEST_MEMBER.EMAIL | NOT_NULL | **5** | M36~40 |
| 2 | EMAIL_REGEX | TB_TEST_MEMBER.EMAIL | REGEX | **3** | NULL 제외, M41~43 |
| 3 | PHONE_NOT_NULL | TB_TEST_MEMBER.PHONE | NOT_NULL | **3** | M44~46 |
| 4 | PHONE_REGEX | TB_TEST_MEMBER.PHONE | REGEX | **2** | NULL 제외, M47~48 |
| 5 | AGE_RANGE (0~150) | TB_TEST_MEMBER.AGE | RANGE | **4** | M49~52 |
| 6 | GENDER_ENUM (M/F/U) | TB_TEST_MEMBER.GENDER | ENUM | **3** | NULL 제외, M53~55 |
| 7 | ORDER_MEMBER_NOT_NULL | TB_TEST_ORDER.MEMBER_ID | NOT_NULL | **2** | O33~34 |
| 8 | ORDER_MEMBER_FK | TB_TEST_ORDER.MEMBER_ID | REFERENCE | **4** | NULL 제외, O35~38 |
| 9 | ORDER_AMOUNT_POSITIVE | TB_TEST_ORDER.AMOUNT | RANGE | **5** | O39~43 |
| 10 | ORDER_STATUS_ENUM | TB_TEST_ORDER.STATUS | ENUM | **3** | O44~46 |
| 11 | ORDER_DATE_COMPARE | TB_TEST_ORDER.END_DT | COMPARE | **4** | O47~50 |
| 12 | PRODUCT_CODE_LENGTH (8) | TB_TEST_PRODUCT.PRODUCT_CODE | LENGTH | **3** | SHORT / VERYLONG / TOOSHORT |
| 13 | PRODUCT_CODE_UNIQUE | TB_TEST_PRODUCT.PRODUCT_CODE | UNIQUE | **4** | DUP00001×2 + DUP00002×2 |
| 14 | PRODUCT_NAME_NOT_NULL | TB_TEST_PRODUCT.NAME | NOT_NULL | **2** | PRD90001~2 |
| 15 | PRODUCT_PRICE_POSITIVE | TB_TEST_PRODUCT.PRICE | RANGE | **3** | PRD90003~5 |
| 16 | PRODUCT_CATEGORY_ENUM | TB_TEST_PRODUCT.CATEGORY | ENUM | **2** | PRD90006~7 |

**합계 위반 카운트: 52**

---

## 값 진단 (VALUE) 검증 시나리오

룰 진단과 별개로 [값 프로파일링] 메뉴에서:

```
모델: TEST_QUAL_MODEL
테이블: TB_TEST_MEMBER (또는 비워서 모델 전체)
샘플링: 100% (풀스캔)
[프로파일링 시작]
```

예상 통계 (TB_TEST_MEMBER 기준):

| ATTR | TOTAL | NULL | DISTINCT | 비고 |
|---|---|---|---|---|
| MEMBER_ID | 55 | 0 | 55 | PK |
| EMAIL | 55 | 5 | ~50 | NULL률 9% |
| PHONE | 55 | 3 | ~52 | NULL률 5% |
| NAME | 55 | 0 | 55 | |
| AGE | 55 | 0 | ~40 | min=-10, max=999 |
| GENDER | 55 | 0 | 6 | M/F/X/Z/O 등 |
| REG_DT | 55 | 0 | 55 | |
| UPDT_DT | 55 | 20 | 35 | 위반행 일부 NULL |

---

## 트러블슈팅

### Q1. `03_qual_test_metadata.sql` 의 driver_nm 이 적용 안 됨
A. `lib/drivers.xml` 에 정의된 PostgreSQL 키와 일치해야 한다. 본 SQL 은 `'PostgreSQL'` 로 시도. 만약 진단이 [CONFIG] 에러로 떨어지면 dataQ UI 에서 데이터소스 추가 한 번 후 등록된 driver_nm 값을 본 SQL 에 반영.

### Q2. UNIQUE 룰 (PRODUCT_CODE) 결과가 4 가 아니라 2
A. RuleSqlBuilder 는 `IN (SELECT ... GROUP BY HAVING COUNT > 1)` 패턴이라 중복 키마다 모든 행 카운트. 2쌍 = 4건이 정상. 만약 2 가 나오면 DISTINCT 처리가 들어간 것 — 의도와 다름. 확인 필요.

### Q3. REFERENCE 룰이 NOT EXISTS 로 부착되는데 결과가 0
A. q-executor 가 동일 DB 의 schema 를 sub-query 에서 못 찾을 수 있음 (search_path 가 quality). 본 시나리오는 testdata 스키마 동일 DB 라 정상 작동해야 함. 안 될 경우 룰 RULE_PARAMS 에 `"refTable":"testdata.TB_TEST_MEMBER"` 로 schema-qualified 변경.

### Q4. 룰 등록은 됐는데 진단 결과가 비어있음
A. 진단이 실패한 경우 quality.TB_QUAL_DIAG_HISTORY 의 STATUS='ERROR' + ERROR_MSG 확인. 흔한 원인:
  - q-executor 가 testdata 에 접속 권한 없음 → ndata.TB_DATA_SOURCE 의 user_id/pwd 확인
  - q-executor 의 lib/drivers.xml 에 PostgreSQL 드라이버 누락 → C:\temp\dataq\lib\jdbc\postgresql-*.jar 존재 확인

---

## 첫 스모크 셸 명령

```powershell
# (다음 세션에서 실행)
cd C:\Users\장재영\Desktop\dataQ\dataQ설계\테스트\sql
.\apply_all.ps1

# 결과 행수 확인
docker exec dataq-db psql -U admin -d postgres -c "SELECT COUNT(*) FROM testdata.tb_test_member;"  # 55 (35+5+3+3+2+4+3)
docker exec dataq-db psql -U admin -d postgres -c "SELECT COUNT(*) FROM testdata.tb_test_order;"   # 50
docker exec dataq-db psql -U admin -d postgres -c "SELECT COUNT(*) FROM testdata.tb_test_product;" # 34
docker exec dataq-db psql -U admin -d postgres -c "SELECT COUNT(*) FROM quality.TB_QUAL_RULE WHERE DM_ID='TESTQUALDM00000000001A';"  # 16
```

---

## 커밋 정책

본 SQL 셋은 **현재 working tree only — 커밋·푸시 안 됨** (69번 핸드오버 정책에 따름).
사용자가 검토 후 반영 결정 시 67/68/69번 + Phase 1 코드와 함께 단일 커밋에 포함.
