"""
ca8858d (DSTermRecommend.vue 분류어 autocomplete + 도메인 cascade) E2E 테스트

확인 포인트:
  1. API /api/std/getClassificationWords 정상 응답 (word_clsf_yn='Y' 필터, domainClsfNm 포함)
  2. API /api/std/getDomainsByClsf?domainClsfNm=XX 정상 응답
  3. UI 메뉴 진입: 자동 표준화 지원 > 컬럼 표준화
  4. 입력 단계에서 한글명 입력 후 분석 시작 → STEP 3 도달
  5. 리뷰 행의 [수정] 버튼 → 모달 열림
  6. 모달에 분류어 autocomplete + [분류어 추가] + 도메인 v-select 존재 확인
  7. 분류어 선택 → [분류어 추가] 클릭 → 단어 테이블에 행이 한 건 늘어나는지
"""
import os
import sys
import time
import traceback
from datetime import datetime

import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException

BASE_URL = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
PREFIX = "clsfdom_"

INPUT_KOREAN = "테스트상품카테고리명_" + datetime.now().strftime("%m%d%H%M%S")  # 기등록 회피

os.makedirs(SCREENSHOT_DIR, exist_ok=True)
results = []


def make_driver():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    return d


def shot(d, name):
    path = os.path.join(SCREENSHOT_DIR, PREFIX + name + ".png")
    d.save_screenshot(path)
    print(f"  [SHOT] {name}")


def wait_visible(d, by, sel, t=10):
    return WebDriverWait(d, t).until(EC.visibility_of_element_located((by, sel)))


def wait_clickable(d, by, sel, t=10):
    return WebDriverWait(d, t).until(EC.element_to_be_clickable((by, sel)))


def dismiss_swal(d):
    for _ in range(5):
        try:
            btn = d.find_element(By.CSS_SELECTOR, ".swal2-confirm")
            if btn.is_displayed():
                btn.click()
                time.sleep(0.4)
                continue
        except Exception:
            pass
        break


def login(d, user="space", pw="123"):
    d.get(BASE_URL + "/signin")
    wait_visible(d, By.CSS_SELECTOR, "input[type='text']", 15)
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw); pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(2)


def _click_el(d, el):
    try:
        el.click()
    except Exception:
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        time.sleep(0.2)
        try: el.click()
        except Exception: d.execute_script("arguments[0].click();", el)


def nav(d, group_id, menu_id):
    dismiss_swal(d)
    menu_items = d.find_elements(By.ID, menu_id)
    need_expand = not menu_items or not menu_items[0].is_displayed()
    if need_expand:
        g = wait_clickable(d, By.ID, group_id, 10)
        _click_el(d, g)
        try: wait_visible(d, By.ID, menu_id, 5)
        except TimeoutException:
            _click_el(d, g); wait_visible(d, By.ID, menu_id, 5)
    m = wait_visible(d, By.ID, menu_id, 10)
    _click_el(d, m)
    time.sleep(2)


def step(name, fn):
    print(f"\n{'=' * 60}\nSTEP: {name}\n{'=' * 60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


# ======================================================================
# 시나리오 시작
# ======================================================================
clsf_words_cache = []
picked_clsf = None

def run():
    d = make_driver()
    cookies = {}
    try:
        # 1. 로그인
        def _login(): login(d)
        if not step("1. 로그인", _login): return

        # 2. API: 분류어 목록
        def _api_clsf():
            global clsf_words_cache, picked_clsf
            cks = {c["name"]: c["value"] for c in d.get_cookies()}
            cookies.update(cks)
            r = requests.get(BASE_URL + "/api/std/getClassificationWords", cookies=cookies, timeout=10)
            r.raise_for_status()
            data = r.json()
            assert isinstance(data, list), "응답이 리스트 아님"
            print(f"  [api] classificationWords count = {len(data)}")
            if len(data) == 0:
                raise RuntimeError("TB_WORD 에 word_clsf_yn='Y' 인 분류어가 없음 — 테스트 데이터 필요")
            for k in ["wordId", "wordNm", "domainClsfNm"]:
                assert k in data[0], f"필드 누락: {k}"
            clsf_words_cache = data
            picked_clsf = next((w for w in data if w.get("domainClsfNm")), data[0])
            print(f"  [api] picked classification word: {picked_clsf.get('wordNm')} / domainClsfNm={picked_clsf.get('domainClsfNm')}")
        if not step("2. API 분류어 목록", _api_clsf): return

        # 3. API: 도메인 cascade
        def _api_dom():
            if not picked_clsf or not picked_clsf.get("domainClsfNm"):
                raise RuntimeError("domainClsfNm 없는 분류어만 있음 — 도메인 cascade 검증 불가")
            r = requests.get(BASE_URL + "/api/std/getDomainsByClsf",
                             params={"domainClsfNm": picked_clsf["domainClsfNm"]},
                             cookies=cookies, timeout=10)
            r.raise_for_status()
            data = r.json()
            assert isinstance(data, list), "응답이 리스트 아님"
            print(f"  [api] domains for {picked_clsf['domainClsfNm']} = {len(data)}")
            if len(data) > 0:
                for k in ["domainId", "domainNm"]:
                    assert k in data[0], f"필드 누락: {k}"
        if not step("3. API 도메인 cascade", _api_dom): return

        # 4. UI 메뉴 진입
        def _nav():
            nav(d, "autoStdGroup", "nav_termRecommend")
            wait_visible(d, By.CSS_SELECTOR, "textarea", 10)
            shot(d, "01_nav")
        if not step("4. 메뉴 진입 (자동 표준화 지원 > 컬럼 표준화)", _nav): return

        # 5. 한글명 입력 + 분석 시작
        def _analyze():
            ta = d.find_element(By.CSS_SELECTOR, "textarea")
            _click_el(d, ta)
            ta.send_keys(INPUT_KOREAN)
            time.sleep(0.5)
            # 분석 시작 버튼 찾기 (v-btn 에 포함된 '분석 시작' 텍스트)
            btns = d.find_elements(By.CSS_SELECTOR, "button.v-btn")
            target = None
            for b in btns:
                if "분석 시작" in (b.text or ""):
                    target = b; break
            if not target: raise RuntimeError("분석 시작 버튼 없음")
            _click_el(d, target)
            # STEP 3 도달 대기 — "분석 결과" 제목 카드가 보이는 것까지. 분석 시간 최대 60초.
            def _step3_visible(drv):
                elems = drv.find_elements(By.XPATH, "//span[text()='분석 결과']")
                for e in elems:
                    if e.is_displayed(): return True
                return False
            WebDriverWait(d, 60).until(_step3_visible)
            # 추가로 행이 최소 1개 보일 때까지
            def _rows_visible(drv):
                rows = drv.find_elements(By.CSS_SELECTOR, ".v-data-table tbody tr")
                for r in rows:
                    if r.is_displayed() and (r.text or "").strip():
                        return True
                return False
            WebDriverWait(d, 20).until(_rows_visible)
            time.sleep(0.5)
            shot(d, "02_analyzed")
        if not step("5. 분석 실행 & STEP3 도달", _analyze): return

        # 6. 첫 행의 [수정] 버튼 클릭 → 모달 열림
        def _open_modal():
            rows = d.find_elements(By.CSS_SELECTOR, ".v-data-table tbody tr")
            if not rows: raise RuntimeError("분석 결과 행 0개")
            # 전체 행을 훑으며 '수정/재수정' 텍스트 포함 버튼 탐색 (REGISTERED 행은 버튼 자체 없음)
            edit_btn = None; target_row = None
            for row in rows:
                for b in row.find_elements(By.CSS_SELECTOR, "button"):
                    txt = (b.text or "").strip()
                    if "수정" in txt or "재수정" in txt:
                        edit_btn = b; target_row = row; break
                if edit_btn: break
            if not edit_btn:
                shot(d, "06a_no_edit_btn")
                # 행 상태 덤프
                dump = []
                for row in rows[:5]:
                    dump.append((row.text or "")[:120])
                raise RuntimeError("수정 버튼 있는 행 없음. 첫 5행:\n    " + "\n    ".join(dump))
            _click_el(d, edit_btn)
            # 모달 뜰 때까지 대기 — v-dialog .v-card-title
            WebDriverWait(d, 10).until(
                EC.visibility_of_element_located((By.CSS_SELECTOR, ".v-dialog--active"))
            )
            time.sleep(0.8)
            shot(d, "03_modal")
        if not step("6. 수정 모달 열기", _open_modal): return

        # 7. 모달 내 분류어/도메인 UI 요소 존재 확인
        def _check_elements():
            dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            html = dialog.get_attribute("innerHTML") or ""
            missing = []
            if "분류어 검색/선택" not in html: missing.append("분류어 autocomplete placeholder")
            if "분류어 추가" not in html: missing.append("[분류어 추가] 버튼 텍스트")
            if "도메인 선택" not in html: missing.append("도메인 v-select placeholder")
            if "용어 도메인" not in html: missing.append("도메인 라벨")
            if missing:
                raise RuntimeError("모달 요소 누락: " + ", ".join(missing))
            print("  [ui] 분류어 autocomplete + [분류어 추가] + 도메인 v-select 전부 렌더됨")
        if not step("7. 모달 UI 요소 확인", _check_elements): return

        # 8. 분류어 선택 → 도메인 드롭다운 활성화
        def _pick_clsf():
            dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            # v-autocomplete input 찾기 (placeholder 기반)
            ac_inputs = dialog.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
            if not ac_inputs: raise RuntimeError("v-autocomplete input 없음")
            ac = ac_inputs[0]
            _click_el(d, ac); time.sleep(0.4)
            ac.send_keys(picked_clsf["wordNm"])
            time.sleep(1.0)
            # 드롭다운 목록에서 일치 선택
            items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
            clicked = False
            for it in items:
                if picked_clsf["wordNm"] in (it.text or ""):
                    _click_el(d, it); clicked = True; break
            if not clicked: raise RuntimeError(f"autocomplete 에서 {picked_clsf['wordNm']} 선택 실패")
            time.sleep(0.5)
            shot(d, "04_clsf_picked")
        if not step("8. 분류어 autocomplete 선택", _pick_clsf): return

        # 9. [분류어 추가] 클릭 → 테이블에 행 증가
        def _add_clsf():
            dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            # 현재 테이블 행 수 — Vuetify 의 v-simple-table 는 실제 DOM 에서 단순 table 로 렌더됨
            def count_rows():
                # 다이얼로그 내 모든 table 의 tbody tr 합산
                return len(dialog.find_elements(By.XPATH, ".//table//tbody//tr"))
            before = count_rows()
            add_btn = None
            for b in dialog.find_elements(By.CSS_SELECTOR, "button"):
                if "분류어 추가" in (b.text or ""):
                    add_btn = b; break
            if not add_btn: raise RuntimeError("[분류어 추가] 버튼 없음")
            if not add_btn.is_enabled():
                raise RuntimeError("[분류어 추가] 버튼 disabled (selectedClsfWord 미설정)")
            _click_el(d, add_btn); time.sleep(0.8)
            after = count_rows()
            if after != before + 1:
                raise RuntimeError(f"행 수 변화 이상: {before} → {after}")
            print(f"  [ui] 단어 테이블 행: {before} → {after}")
            shot(d, "05_added")
        if not step("9. [분류어 추가] 로 단어 테이블에 push", _add_clsf): return

        # 10. 모달 닫기
        def _close():
            dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            for b in dialog.find_elements(By.CSS_SELECTOR, "button"):
                if (b.text or "").strip() == "취소":
                    _click_el(d, b); break
            time.sleep(1); shot(d, "99_final")
        if not step("10. 모달 닫기", _close): return

    finally:
        # 결과 요약
        print("\n" + "=" * 60 + "\n결과\n" + "=" * 60)
        p = sum(1 for _, s, _ in results if s == "PASS")
        f = sum(1 for _, s, _ in results if s == "FAIL")
        for name, status, _ in results:
            print(f"  [{status}] {name}")
        print(f"\n  총 {len(results)}: PASS {p}, FAIL {f}")
        print(f"  스크린샷: {SCREENSHOT_DIR} (prefix={PREFIX})")
        try: d.quit()
        except Exception: pass
        sys.exit(0 if f == 0 else 1)


if __name__ == "__main__":
    run()
