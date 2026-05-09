# 86번 #11 통합 테스트 v2 (2026-05-09)

기존 selenium 테스트가 잡지 못한 **의미 검증 (semantic verification)** 을 깊이 있게 cover.

## 한계 인식 (이전 회귀 실패 원인)

이전 통합테스트의 약점:
- API 200 응답만 보고 "성공" 판정 → 백엔드 비즈니스 실패 (resultCode=500) 못 잡음
- DB 상태 비교 X → INSERT 0건 / 잘못된 row 변경 등 못 잡음
- 같은 OBJ_NM 다른 OWNER 케이스 부재

**v2 의 원칙**:
1. 모든 액션 BEFORE / AFTER 두 시점에서 **DB 직접 조회**
2. resultCode 검사 (200 만 통과)
3. 같은 OBJ_NM 다른 OWNER 케이스 명시적 cover (T02, T04, T07)
4. 진단 제외 → 재진단 → before/after 결과 비교 (T05)

## 케이스

| ID | 시나리오 | 핵심 검증 |
|---|---|---|
| T01 | 로그인 + 관리자 인식 | DB admin_yn=Y |
| T02 | 4 schema 수집 (같은 OBJ_NM 다른 OWNER) | TB_DATA_MODEL_OBJ row 분리 |
| T03 | 테이블/컬럼 화면 (검색·페이징·가로스크롤·자동검색) | OWNER 컬럼 + 페이지네이션 가림 X |
| T04 ★ | 진단 제외 — INV_APP.COMPANY_INFO 만 OFF | DB: INV_APP='N', HRM_APP/SALES_APP='Y' 그대로 |
| T05 ★ | 표준 진단 전/후 — 제외된 OBJ 의 컬럼이 결과에서 빠짐 | TB_DIAG_RESULT row 차이 |
| T06 | 진단 결과 매칭 칩 + 표준화 ALTER 모달 | 매칭 시 [표준화] 만, 비매칭 시 [용어 등록] |
| T07 | 컬럼 추가 시 부모 OBJ_OWNER 자동 상속 | 다른 OWNER TB_USER 영향 X |
| T08 | 테이블 영문명 정규식 (백엔드 검증) | 백틱·공백·한글·특수문자 거부 |
| T09 | Excel 양식 ↔ 데이터 헤더 일치 | round-trip 가능 |
| T10 ★ | 가짜 success 차단 (한글명 충돌) | resultCode != 200 시 error swal |
| T11 | OBJ_OWNER 변경 cascade (ATTR/INDEX/CONSTRAINT) | 모두 갱신, ref_owner 도 |
| T12 | 페이지네이션이 마지막 행 안 가림 (16 화면) | row.bottom <= pag.top |

★ = 이번 86번 #11 fix 의 핵심 검증 케이스

## 실행

```bash
cd "dataQ설계/테스트/selenium/v2_2026-05-09"

# 전체
python run_all.py

# 특정 케이스만
python run_all.py t04 t05
```

## 사전 조건

1. **q-center 28091** 떠 있음 (실행: `cd q-center && java -jar target/q-center-0.0.1-SNAPSHOT.jar`)
2. **q-executor 28098** 떠 있음 (진단 케이스용)
3. **dataq-db** 컨테이너 동작
4. **oracle-xe** 컨테이너 동작 — 다음 테이블 사전 등록 (사용자가 이미 만든 상태):
   - HRM_APP.COMPANY_INFO, HRM_APP.TB_USER
   - INV_APP.COMPANY_INFO, INV_APP.TB_USER
   - SALES_APP.COMPANY_INFO, SALES_APP.TB_USER
5. **dataq 모델 등록** — "수동DB등록모델" 같은 모델 1개 + 4 schema 수집 완료
6. **TB_WORD** 에 '주소' 단어 (T10 사전조건)
7. **Microsoft Edge** 브라우저 + msedgedriver

## 빌드 + 재기동 절차 (사용자가 자고 일어나서 실행)

```powershell
# 1) q-center / q-executor 정지
Get-Process java | Where-Object { $_.MainWindowTitle -match "q-center|q-executor" } | Stop-Process

# 2) 빌드 (Java 변경 — 진단 제외 OBJ_OWNER fix 포함)
cd C:\Users\장재영\Desktop\dataQ
JAVA_HOME="/c/Program Files/Java/jdk1.8.0_202" mvn package install -DskipTests -T 1C

# 3) 재기동
cd q-center; java -jar target/q-center-0.0.1-SNAPSHOT.jar
cd q-executor; java -jar target/q-executor.jar

# 4) 테스트 실행
cd "dataQ설계/테스트/selenium/v2_2026-05-09"
python run_all.py
```

## 결과 리포트

`reports/report_YYYYMMDD_HHMMSS.md` 에 전체 테스트별 PASS/FAIL + 단계별 검증 결과.

## 화면 캡처

`screenshots/tNN_*.png` — 각 케이스 주요 단계.
