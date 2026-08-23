"""
81번 — 데이터 사전 > 용어 등록 (DSTerm.vue) 단일 폼 + 자동 분석 + 코드 picker 검증

대상 화면: 데이터 표준 사전 > 용어
대상 동작:
  1. 자동 분석 디바운스 (한글 용어명 입력 → 1초 후 단어 분리 + 매칭)
  2. 영문약어 자동 합성
  3. 'CD' 마지막 단어일 때 도메인 유형 토글 자동 활성화
  4. 코드 선택 picker 다이얼로그 (검색 + 선택)
  5. 메타 영역 v-expansion-panel (default 접힘)
  6. 이상한 케이스: 빈 용어명 / 중복 용어명 / 신규 단어 포함

준수사항:
  - 모든 진행 DOM 클릭 (execute_script 는 스크롤/가시성 보조만)
  - 등록까지 가는 풀 시나리오는 단어 데이터 의존이 커서 1차 단계는 UX 동작만 검증
"""
import os
import sys
import time
import random
import traceback
import requests
import base64
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"
SCREEN_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")
os.makedirs(SCREEN_DIR, exist_ok=True)

_SUFFIX = str(random.randint(100, 999))
# 2026-08-22: 유니크 접미사를 뒤에 붙이면 형태소 분석의 "마지막 단어" 가 접미사 숫자가 돼
# 각 CASE 의 전제(마지막 단어 = 명 / 코드)가 깨진다.
#   "셀유형코드_761" -> [셀, 유형, 코드, _, 761]  → 마지막 = 761 (UNRECOGNIZED)
# 접미사를 형식단어 앞에 넣어 마지막 단어가 형식단어로 남게 한다.
TERM_NORMAL = f"셀{_SUFFIX}명"   # 마지막 단어 '명' (일반 도메인)
# CASE B 는 등록까지 가지 않고 "도메인 유형 토글 + 코드 picker" 만 확인하므로 유니크할 필요가 없다.
# 오히려 접미사를 붙이면 형태소 분석이 `셀유형323` 을 한 덩어리 UNRECOGNIZED 로 묶어
# 선택 단어 리스트(addTerm_wordList)가 흐트러지고 토글 조건이 깨진다.
# 전 토큰이 MATCHED 되는 고정 이름을 쓴다 — [셀(CELL), 유형(TYPE), 코드(CD)].
TERM_CODE   = "셀유형코드"
# 82번 §3 v2 케이스 — analyzeTermsBatch 단어 정확 분리 검증
# (사전 등록 단어가 늘어나면 케이스 무용 — 미등록 토큰을 일부러 의미없는 문자열로 구성)
TERM_MIXED  = "블라블라일자"           # UNRECOGNIZED(블라블라) + MATCHED(일자) 혼합
TERM_FAILED = "라랄라룰루"             # 전 토큰 미인식 → FAILED status

results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print(f"  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def create_driver():
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    drv = webdriver.Edge(options=options)
    drv.set_window_size(1500, 1000)
    return drv


def screenshot(driver, name):
    path = os.path.join(SCREEN_DIR, f"term_v2_{name}.png")
    try:
        driver.save_screenshot(path)
        print(f"  스크린샷: {os.path.basename(path)}")
    except Exception:
        pass


def login(driver, user_id="space", password="123"):
    driver.get(BASE_URL)
    time.sleep(2)
    if "/app/" in driver.current_url:
        driver.get(BASE_URL + "/logout")
        time.sleep(2)
        driver.get(BASE_URL)
        time.sleep(2)
    id_input = WebDriverWait(driver, 15).until(EC.presence_of_element_located(
        (By.CSS_SELECTOR, "input[type='text']")))
    id_input.clear(); id_input.send_keys(user_id)
    pw = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw.clear(); pw.send_keys(password)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
    time.sleep(3)
    assert "/app/main" in driver.current_url, f"로그인 실패: {driver.current_url}"
    print(f"  [{user_id}] 로그인 OK")


def nav_to_term(driver):
    """데이터 표준 사전 그룹 > 용어 메뉴 클릭"""
    time.sleep(1)
    # 그룹 펼치기
    headers = driver.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
    for h in headers:
        if h.text.strip() == "데이터 표준 사전":
            grp = h
            for _ in range(8):
                grp = grp.find_element(By.XPATH, "..")
                if "v-list-group" in (grp.get_attribute("class") or ""): break
            if "v-list-group--active" not in (grp.get_attribute("class") or ""):
                ActionChains(driver).move_to_element(h).click().perform()
                time.sleep(1)
            break
    # nav_term 클릭
    el = WebDriverWait(driver, 8).until(EC.presence_of_element_located((By.ID, "nav_term")))
    driver.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
    ActionChains(driver).move_to_element(el).click().perform()
    time.sleep(2)
    print("  용어 화면 진입")


def open_add_modal(driver):
    """추가 (신규 등록) 버튼 클릭 — 등록 모달 열림"""
    # '추가' 텍스트의 v-btn
    btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
    for b in btns:
        try:
            if b.text.strip() in ("추가", "등록", "신규"):
                title = (b.get_attribute("title") or "")
                if "검색" in title or "초기화" in title: continue
                ActionChains(driver).move_to_element(b).click().perform()
                time.sleep(1)
                break
        except: continue
    # 모달 떠 있는지 확인
    WebDriverWait(driver, 5).until(EC.presence_of_element_located(
        (By.XPATH, "//div[contains(@class,'v-dialog--active')]//*[contains(text(), '용어 등록')]")))
    print("  등록 모달 열림")


def find_term_input_in_modal(driver):
    """등록 모달 안의 용어명 input 찾기 (placeholder='가동개시일자')"""
    inputs = driver.find_elements(By.XPATH,
        "//div[contains(@class,'v-dialog--active')]//input[@placeholder='가동개시일자']")
    if not inputs:
        # fallback: 첫 input
        inputs = driver.find_elements(By.XPATH,
            "//div[contains(@class,'v-dialog--active')]//input[@type='text']")
    return inputs[0] if inputs else None


def get_modal_text(driver):
    el = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")
    return el.text


def select_all_words_in_modal(driver):
    """등록 모달의 각 단어 분류 v-data-table 의 첫 row 체크박스 클릭 (분류별 1개 후보 선택)"""
    rows = driver.find_elements(By.XPATH,
        "//div[contains(@class,'v-dialog--active')]//table//tbody//tr")
    clicked = 0
    for r in rows:
        try:
            cb = r.find_element(By.CSS_SELECTOR, ".v-simple-checkbox")
            # 2026-08-22: DSTerm._applyAnalyzedWords 는 MATCHED 단어를 이미 선택된 상태로 넣는다
            # (newSelectedList[i] = [item]). 무조건 클릭하면 오히려 선택이 해제돼
            # addTerm_wordList 가 비고 "도메인 유형" 토글이 사라진다.
            # → 이미 체크된 행은 건드리지 않고, 안 된 행만 클릭한다.
            if "mdi-checkbox-marked" in (cb.get_attribute("innerHTML") or ""):
                clicked += 1
                continue
            icon = cb.find_element(By.CSS_SELECTOR, "i") if cb.find_elements(By.CSS_SELECTOR, "i") else cb
            driver.execute_script("arguments[0].click();", icon)
            time.sleep(0.3)
            if "mdi-checkbox-marked" in (cb.get_attribute("innerHTML") or ""):
                clicked += 1
        except: continue
    print(f"  단어 체크박스 {clicked}개 선택 상태")
    time.sleep(1)
    return clicked


def close_modal(driver):
    btns = driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active .v-btn")
    for b in btns:
        try:
            if b.text.strip() in ("취소", "닫기"):
                ActionChains(driver).move_to_element(b).click().perform()
                time.sleep(1)
                return
        except: continue


# ========== 시나리오 ==========

def main():
    driver = create_driver()
    try:
        step("STEP 1. admin 로그인", lambda: login(driver, "space"))
        step("STEP 2. 데이터 사전 > 용어 화면 진입", lambda: nav_to_term(driver))

        # ---------- CASE A: 일반 도메인 (마지막 단어 '명' = NM) ----------
        def caseA():
            open_add_modal(driver)
            screenshot(driver, "A1_modal_opened")
            inp = find_term_input_in_modal(driver)
            assert inp is not None, "용어명 입력 input 못 찾음"
            inp.clear()
            inp.send_keys(TERM_NORMAL)
            print(f"  용어명 입력: {TERM_NORMAL} → 1초 디바운스 + 분석 대기")
            time.sleep(3)
            modal_text = get_modal_text(driver)
            screenshot(driver, "A2_after_analyze")
            assert "자동 분석 완료" in modal_text or "구성 단어" in modal_text, \
                "자동 분석 결과가 모달에 표시되지 않음"
            print(f"  자동 분석 결과 표시 확인")
            # 단어 선택 (분류별 1개)
            select_all_words_in_modal(driver)
            time.sleep(1)
            modal_text2 = get_modal_text(driver)
            screenshot(driver, "A3_after_select")
            # 마지막 단어 'NM' 이라 도메인 유형 토글 비표시여야
            assert "도메인 유형" not in modal_text2, \
                "마지막 단어가 'NM' 인데 도메인 유형 토글이 떴음"
            print(f"  도메인 유형 토글 비표시 확인 (단어 선택 후, 마지막 != CD)")
            close_modal(driver)
            time.sleep(1)
        step("STEP 3 [CASE A]. 일반 도메인 — 자동분석 + 단어선택 + 토글 비표시", caseA)

        # ---------- CASE B: 코드 도메인 (마지막 단어 '코드' = CD) ----------
        def caseB():
            open_add_modal(driver)
            inp = find_term_input_in_modal(driver)
            # Vuetify input 은 .clear() 만으로 v-model 이 안 비워져 잔여 텍스트에 이어붙는 경우가 있다
            # (실제로 "셀유형코드dIAKJFaj" 가 입력돼 마지막 단어가 CD 가 아니게 되는 현상 발생).
            # Ctrl+A 로 전체 선택 후 덮어쓰고, 실제 들어간 값을 확인한다.
            inp.clear()
            inp.send_keys(Keys.CONTROL, "a")
            inp.send_keys(TERM_CODE)
            time.sleep(0.3)
            actual = inp.get_attribute("value") or ""
            print(f"  용어명 입력: {TERM_CODE} (실제 입력값='{actual}') → 자동 분석 대기")
            assert actual == TERM_CODE, f"입력값 오염: 기대 '{TERM_CODE}', 실제 '{actual}'"
            time.sleep(3)
            # 단어 선택 (분류별 1개)
            select_all_words_in_modal(driver)
            time.sleep(1)
            modal_text = get_modal_text(driver)
            screenshot(driver, "B2_after_analyze")
            assert "도메인 유형" in modal_text, \
                "마지막 단어가 'CD' 인데 도메인 유형 토글이 안 떴음"
            print(f"  도메인 유형 토글 표시 확인 (마지막 단어 = CD)")
            # "코드" 라디오 클릭 → picker UI 활성화
            code_radios = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-dialog--active')]//div[contains(@class,'v-radio') and .//label[contains(text(),'코드')]]")
            assert code_radios, "코드 라디오 버튼 못 찾음"
            ActionChains(driver).move_to_element(code_radios[-1]).click().perform()
            time.sleep(1)
            print(f"  코드 라디오 선택")
            # 코드 picker 검색 버튼 클릭 (active 다이얼로그 내부, gradient 클래스 + '검색' 텍스트)
            search_btns = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-dialog--active')]//button[contains(@class,'gradient') and contains(., '검색')]")
            assert search_btns, "코드 검색 버튼 못 찾음"
            ActionChains(driver).move_to_element(search_btns[-1]).click().perform()
            time.sleep(1)
            # 코드 picker 다이얼로그 (모달 안의 모달) — title 'v-card__title' contains '코드 선택'
            picker_titles = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-card__title') and contains(text(),'코드 선택')]")
            screenshot(driver, "B3_code_picker")
            assert picker_titles, "코드 picker 다이얼로그가 열리지 않음"
            print(f"  코드 picker 열림 OK")
            # picker 닫기
            cancel_btns = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-dialog--active')]//button[.//span[text()='취소']]")
            if cancel_btns:
                ActionChains(driver).move_to_element(cancel_btns[-1]).click().perform()
                time.sleep(1)
            close_modal(driver)
            time.sleep(1)
        step("STEP 4 [CASE B]. 코드 도메인 — 토글 자동 + picker 다이얼로그", caseB)

        # ---------- CASE C1: 빈 용어명 → 등록 버튼 거부 ----------
        def caseC1():
            open_add_modal(driver)
            # 용어명 비운 채로 등록 클릭 — gradient 클래스의 footer submit 버튼 (NdModal)
            submit_btns = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-dialog--active')]//button[contains(@class,'gradient') and contains(., '등록')]")
            assert submit_btns, "등록 버튼 못 찾음"
            ActionChains(driver).move_to_element(submit_btns[0]).click().perform()
            time.sleep(2)
            # swal 떴는지 확인
            swal = driver.find_elements(By.CSS_SELECTOR, ".swal2-popup")
            screenshot(driver, "C1_empty_term")
            assert swal and ("필수" in swal[0].text or "용어명" in swal[0].text), \
                f"빈 용어명 등록 시 swal 검증 실패: {swal[0].text if swal else '(없음)'}"
            print(f"  빈 용어명 등록 거부 확인: {swal[0].text[:60]}")
            ok_btns = driver.find_elements(By.CSS_SELECTOR, ".swal2-confirm")
            if ok_btns: ActionChains(driver).move_to_element(ok_btns[0]).click().perform()
            time.sleep(1)
            close_modal(driver)
            time.sleep(1)
        step("STEP 5 [CASE C1]. 빈 용어명 등록 거부", caseC1)

        # ---------- CASE C2: 중복 용어명 → 자동 분석 시 swal 경고 ----------
        # 사전: API 로 용어 1건 만들어두고 그 이름을 다시 입력했을 때 swal 뜨는지 확인
        # 시간 절약 위해 cascade_and_word_first 패턴처럼 API 직접 등록은 복잡 → 기존 등록된 용어명 활용
        # PC1 DB 의 임의 1개 골라서 사용
        def caseC2():
            # 기존 용어 한 건 골라서 입력
            s = requests.Session()
            s.post(BASE_URL + "/login", data={"id":"space","password":base64.b64encode(b"123").decode()},
                   allow_redirects=False, timeout=10)
            r = s.get(BASE_URL + "/api/std/getTermsList", timeout=10)
            existing = None
            try:
                lst = r.json()
                if lst and len(lst) > 0:
                    existing = lst[0].get("termsNm")
            except:
                pass
            if not existing:
                print("  [SKIP] 기존 용어가 없어 중복 검증 건너뜀")
                return
            print(f"  기존 용어 활용: {existing}")
            open_add_modal(driver)
            inp = find_term_input_in_modal(driver)
            inp.clear()
            inp.send_keys(existing)
            time.sleep(3)  # debounce + API
            screenshot(driver, "C2_dup_term")
            swal = driver.find_elements(By.CSS_SELECTOR, ".swal2-popup")
            assert swal and "이미 등록된" in swal[0].text, \
                f"중복 용어 swal 검증 실패: {swal[0].text if swal else '(없음)'}"
            print(f"  중복 용어 swal 표시 확인: {swal[0].text[:60]}")
            ok_btns = driver.find_elements(By.CSS_SELECTOR, ".swal2-confirm")
            if ok_btns: ActionChains(driver).move_to_element(ok_btns[0]).click().perform()
            time.sleep(1)
            close_modal(driver)
            time.sleep(1)
        step("STEP 6 [CASE C2]. 중복 용어명 → swal 경고", caseC2)

        # ---------- CASE D: NEW + MATCHED 혼합 (analyzeTermsBatch 정확 분리 검증) ----------
        # 82번 §3: getTermsTokenListByNm 시절엔 부분문자열 매칭 잡음 다수.
        # analyzeTermsBatch 로 교체 후 정확히 N개 토큰만 표시되어야.
        # 의도적 미등록 토큰(블라블라) + 표준 매칭(일자) 혼합 케이스.
        def caseD():
            open_add_modal(driver)
            inp = find_term_input_in_modal(driver)
            inp.clear()
            inp.send_keys(TERM_MIXED)
            print(f"  용어명 입력: {TERM_MIXED} → 자동 분석 대기")
            time.sleep(3)
            screenshot(driver, "D1_mixed_analyzed")
            heads = driver.find_elements(By.XPATH,
                "//div[contains(@class,'v-dialog--active')]//div[contains(@class,'v-card__text')]//h4")
            head_texts = [h.text.strip().split('\n')[0] for h in heads if h.text.strip()]
            print(f"  분석된 토큰: {head_texts}")
            assert len(head_texts) <= 4, f"토큰이 너무 많음 (analyzeTermsBatch 미적용 의심): {len(head_texts)}개"
            modal_text = get_modal_text(driver)
            # MATCHED('일자') + UNRECOGNIZED('블라블라') 혼합 칩 검증
            assert "일자" in modal_text, "MATCHED 토큰 '일자' 가 표시되지 않음"
            assert "블라블라" in modal_text, "UNRECOGNIZED 토큰 '블라블라' 표시 누락"
            assert "미등록" in modal_text, "NEW/UNRECOGNIZED 칩 표시 누락"
            assert "등록됨" in modal_text, "MATCHED 칩 표시 누락"
            print(f"  '블라블라' 미등록 + '일자' 등록됨 혼합 칩 OK")
            close_modal(driver)
            time.sleep(1)
        step("STEP 7 [CASE D]. NEW+MATCHED 혼합 — analyzeTermsBatch 정확 분리", caseD)

        # ---------- CASE E: 전부 미인식 (FAILED) ----------
        def caseE():
            open_add_modal(driver)
            inp = find_term_input_in_modal(driver)
            inp.clear()
            inp.send_keys(TERM_FAILED)
            print(f"  용어명 입력: {TERM_FAILED} → 자동 분석 대기")
            time.sleep(3)
            screenshot(driver, "E1_failed_analyzed")
            modal_text = get_modal_text(driver)
            assert "미등록" in modal_text, "FAILED 케이스에서 '미등록' 칩 안 보임"
            assert "등록됨" not in modal_text, "FAILED 케이스에 '등록됨' 칩이 잘못 표시됨"
            print(f"  전 토큰 미등록 표시 OK")
            close_modal(driver)
            time.sleep(1)
        step("STEP 8 [CASE E]. 전 토큰 미인식 (FAILED)", caseE)

        # ---------- 종합 ----------
        screenshot(driver, "Z_final")
    finally:
        # 결과 출력
        print("\n" + "="*60)
        passed = sum(1 for _, st in results if st == "PASS")
        failed = sum(1 for _, st in results if st == "FAIL")
        for nm, st in results:
            mark = "✓" if st == "PASS" else "✗"
            print(f"  {mark} [{st}] {nm}")
        print(f"\n결과: {passed} PASS / {failed} FAIL (총 {len(results)})")
        time.sleep(1)
        try: driver.quit()
        except: pass
        sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
