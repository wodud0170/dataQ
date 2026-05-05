# 02. 그룹 1 — API/Login (가벼움) (5건)

가장 빠르게 도는 그룹. 평균 1~30초. 환경 검증용으로 먼저 돌려보면 좋음.

---

## 2-1. test_login.py

| 항목 | 내용 |
|------|------|
| 검증 | space/jyjang 로그인 → 메인 진입 → 5초간 로그인 화면으로 튕기지 않음 → 로그아웃 → 재로그인 |
| 사전 조건 | TB_USER 에 space/jyjang 계정 존재 (비밀번호 123) |
| 실행 시간 | ~65초 |
| 시연 포인트 | 로그인 폼 → 메인 화면. 두 계정 모두 통과해야 STOMP/세션 정상 |

```bash
python "dataQ설계/테스트/selenium/test_login.py"
```

---

## 2-2. test_phase1_schedule_api.py

| 항목 | 내용 |
|------|------|
| 검증 | 진단 스케줄 API CRUD — create / list / update / delete / run-now |
| 사전 조건 | DB 정상, 임시 모델 필요 없음 (스케줄만 검증) |
| 실행 시간 | ~4초 |
| 시연 포인트 | 콘솔에 API 응답만 — UI 안 띄움 |

```bash
python "dataQ설계/테스트/selenium/test_phase1_schedule_api.py"
```

---

## 2-3. test_date_range_filter.py (이번 세션 신규)

| 항목 | 내용 |
|------|------|
| 검증 | 용어/단어/코드 등록일자 범위 검색 — 무필터 vs 과거(0건) vs 광역(=전체) vs 미래(0건) 4 비교 |
| 사전 조건 | TB_TERMS / TB_WORD / TB_TERMS(코드) 데이터 1건 이상 |
| 실행 시간 | ~20초 |
| 시연 포인트 | API 4가지 비교 + Selenium 으로 3개 화면에 type=date input 2개씩 존재 확인 |

```bash
python "dataQ설계/테스트/selenium/test_date_range_filter.py"
```

기대 출력:
```
[용어] 무필터=13184 과거(0기대)=0 광역(=전체)=13184 미래(0기대)=0
[단어] 무필터=3304  과거(0기대)=0 광역(=전체)=3304  미래(0기대)=0
[코드] 무필터=80    과거(0기대)=0 광역(=전체)=80    미래(0기대)=0
```

---

## 2-4. test_perm_matrix.py (이번 세션 신규)

| 항목 | 내용 |
|------|------|
| 검증 | `/api/login/isAdmin` admin/일반 분기 + UI 메뉴 [관리] 표시 차이 |
| 사전 조건 | space=관리자 / jyjang=일반 사용자 데이터 |
| 실행 시간 | ~20초 |
| 시연 포인트 | space 로그인 → [관리] 메뉴 보임 / jyjang 로그인 → [관리] 메뉴 안 보임 |

```bash
python "dataQ설계/테스트/selenium/test_perm_matrix.py"
```

기대 출력:
```
isAdmin: space=True / jyjang=False
관리자 [관리] visible=True (요소수=1)
일반 [관리] visible=False (요소수=0)
```

---

## 2-5. test_crud_std_dict.py (이번 세션 신규)

| 항목 | 내용 |
|------|------|
| 검증 | 단어 / 도메인 그룹 / 도메인 분류 — Create → Read → Update → Delete (각 4단계 완전 흐름) |
| 사전 조건 | space 로그인 권한 |
| 실행 시간 | ~1초 (API 만 사용) |
| 시연 포인트 | 콘솔에 4단계 PASS 라인 — UI 안 띄움 |

```bash
python "dataQ설계/테스트/selenium/test_crud_std_dict.py"
```

기대 출력:
```
C1. 단어 CRUD: CREATE OK / UPDATE OK / DELETE OK / PASS
C2. 도메인 그룹 CRUD: CREATE OK / UPDATE OK / DELETE OK / PASS
C3. 도메인 분류 CRUD: CREATE OK / UPDATE/DELETE OK + cleanup / PASS
```

---

## 그룹 합계 — 5/5 PASS / ~110초

다음 그룹: [03_표준사전.md](03_표준사전.md)
