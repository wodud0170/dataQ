# UI 자동 검토 도구

수동 테스트 전 객관적 이슈 후보를 자동으로 추출. 사람의 시선으로 보면 어색한 사이즈/정렬은 사람이 직접 판단해야 하지만, 기계적 누락 (반응 없는 버튼·핸들러 깨짐·사이즈 혼재 등) 은 여기서 잡음.

## 도구 2종

### 1. `vue_lint_ui.py` — Vue 정적 분석기
스캔 대상: `q-center/vue/front/src/components/` + `views/`

검사 항목:
1. 반응 없는 버튼 — `<v-btn>` 인데 `@click` / `v-on:click` / `:to` / `href` / `type=submit` 모두 없음
2. 핸들러 깨짐 — `@click="someFn"` 인데 script 안에 `someFn` 정의 없음
3. v-btn 사이즈 혼재 — 같은 파일 안 `small` / `x-small` / 사이즈 없음 혼재
4. v-icon 사이즈 혼재 — 동일
5. disabled 빈 바인딩 — `:disabled=""` (값 비움)
6. icon+text 정렬 누락 — `<v-btn>` 안 `<v-icon>` 에 `left` / `right` 없음

실행:
```bash
python vue_lint_ui.py --out lint_report.md
```

### 2. `selenium_menu_crawler.py` — 메뉴 자동 크롤러
좌측 메뉴 31개 자동 진입 + 화면 스크린샷 + 버튼/입력/행 카운트 + 응답 시간.

실행 (q-center 기동 필요):
```bash
python selenium_menu_crawler.py
```

출력:
- `screenshots/nav_*.png` — 메뉴별 화면 캡처
- `crawl_report.md` — 메뉴별 진입 결과 표 + 스크린샷 임베드

---

## 직전 실행 결과 요약

### 정적 분석 (`lint_report.md`)
- **57 파일** 스캔 / v-btn **122** / v-icon **98**
- 이슈 파일: **20**
- 카테고리:
  - 반응 없는 버튼: **9 건** — 의도된 disabled 인지 사람 판단 필요
  - icon+text 정렬 누락: **23 건** — `left`/`right` 추가 권장
  - v-btn 사이즈 혼재: **4 파일**
  - v-icon 사이즈 혼재: **5 파일**
  - 핸들러 깨짐: **0 건** ✅

### Selenium 크롤링 (`crawl_report.md`)
- **31 메뉴** 모두 정상 진입
- 평균 응답 시간 ~2.7초 (페이지 transition 포함)
- 미노출 메뉴: nav_scurrent (주석 처리), nav_roles (display:none) — 의도된 숨김

---

## 수동 테스트 워크플로우

1. `lint_report.md` 항목별로 수정
2. `screenshots/` 폴더 한 번 훑으면서 어색한 사이즈/배치 표시
3. 수정 후 두 도구 재실행해서 회귀 확인
4. 자동 안 잡히는 부분 (애니메이션 / 색상 일관성 / 한글 띄어쓰기) 만 사람이 검토

---

## 한계 (사람이 봐야 하는 것)

- 글자 크기 어색함 (예: `font-size: .85rem` 이지만 너무 큰 영역)
- 색상 일관성 (gradient vs primary vs custom)
- 한글 띄어쓰기 / 맞춤법
- 애니메이션 / transition 어색함
- 모달 안의 흐름 (자동 진입 X)
- 권한별 버튼 노출 (admin 전용 / 일반사용자 전용)
- 모바일 레이아웃 / 반응형
