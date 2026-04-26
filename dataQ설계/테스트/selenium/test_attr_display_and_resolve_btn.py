"""
DSDatamodelStatusColumn 화면 변경 검증:

1. 비표준 컬럼(termsStndYn='N') 의 attrNm 컬럼은 빈값으로 표시 (TMP_COL_N 노출 안 됨)
2. [선택 컬럼 물리모델 변환] 버튼은 체크 전에도 노출 + 체크 안 했을 때 disabled, 체크 후 enabled

전제: 서버 28091 기동, CAMS 모델에 비표준 컬럼이 다수 존재 (TERMS_STND_YN='N')
"""
import os, sys, time, traceback
from datetime import datetime

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
PREFIX = "attrcol_"
os.makedirs(SCREENSHOT_DIR, exist_ok=True)
results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn(); results.append((name, "PASS", None)); print("  >> PASS"); return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def shot(d, name):
    d.save_screenshot(os.path.join(SCREENSHOT_DIR, PREFIX + name + ".png"))
    print(f"  [SHOT] {name}")


def login(d, user="space", pw="123"):
    d.get(BASE + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw); pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(2)


def js_click(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
    time.sleep(0.2)
    try: el.click()
    except Exception: d.execute_script("arguments[0].click();", el)


def nav(d, group_id, menu_id):
    items = d.find_elements(By.ID, menu_id)
    if not items or not items[0].is_displayed():
        g = WebDriverWait(d, 10).until(EC.element_to_be_clickable((By.ID, group_id)))
        js_click(d, g)
        WebDriverWait(d, 5).until(EC.visibility_of_element_located((By.ID, menu_id)))
    js_click(d, d.find_element(By.ID, menu_id))
    time.sleep(2)


def select_model_in_filter(d, value):
    """filterWrapper 의 첫 v-autocomplete input 에서 value 검색·선택"""
    filter_bar = d.find_element(By.CSS_SELECTOR, ".filterWrapper")
    ac = filter_bar.find_element(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    js_click(d, ac); time.sleep(0.4)
    ac.send_keys(value); time.sleep(1.0)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    for it in items:
        if value in (it.text or ""):
            js_click(d, it); time.sleep(0.5); return
    raise RuntimeError(f"모델 autocomplete 에서 '{value}' 옵션 없음")


def main():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    try:
        if not step("1. 로그인", lambda: login(d)): return

        def _nav():
            nav(d, "dmGroup", "nav_datamodelStatusColumn")
            WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.ID, "btn-resolve-selected")))
            shot(d, "01_nav")
        if not step("2. 컬럼 화면 진입", _nav): return

        def _check_resolve_btn_disabled_before_select():
            btn = d.find_element(By.ID, "btn-resolve-selected")
            assert btn.is_displayed(), "변환 버튼이 화면에 보이지 않음 (체크 전인데 사라짐)"
            disabled = btn.get_attribute("disabled") or btn.get_attribute("aria-disabled")
            cls = btn.get_attribute("class") or ""
            assert disabled == "true" or "v-btn--disabled" in cls, \
                f"변환 버튼 disabled 상태 아님 (체크 전인데 활성): disabled={disabled} class={cls}"
            print(f"  변환 버튼 visible=True, disabled=True ✓ (class 일부: {[c for c in cls.split() if 'btn' in c][:3]})")
        if not step("3. 변환 버튼 — 체크 전 visible + disabled", _check_resolve_btn_disabled_before_select): return

        def _select_model():
            select_model_in_filter(d, "CAMS")
            time.sleep(2)
            shot(d, "02_model_selected")
        if not step("4. CAMS 모델 선택", _select_model): return

        def _check_attr_nm_no_tmp():
            # 그리드의 "컬럼명" 컬럼 (attrNm) 값들 중 TMP_COL_N 패턴이 없어야 함
            rows = d.find_elements(By.CSS_SELECTOR, ".v-data-table tbody tr")
            assert rows, "행이 없음 (모델 선택 안 됐거나 컬럼 데이터 없음)"
            tmp_visible = []
            checked = 0
            import re
            for r in rows[:30]:  # 상위 30행만 검사
                cells = r.find_elements(By.TAG_NAME, "td")
                if len(cells) < 5: continue
                # 헤더 매핑: select / 테이블한글명 / 테이블명 / 컬럼한글명 / 컬럼명 / ...
                # 정확한 인덱스는 환경 따라 변동 가능 → 모든 셀 텍스트 검사
                row_text = (r.text or "")
                checked += 1
                m = re.search(r'TMP_COL_\d+', row_text)
                if m:
                    tmp_visible.append(m.group())
            assert checked > 0, "검사된 행 0건"
            print(f"  검사된 행: {checked}, TMP_COL 노출: {len(tmp_visible)}")
            assert not tmp_visible, f"비표준 컬럼명이 그리드에 노출됨: {tmp_visible[:5]}"
        if not step("5. 비표준 컬럼명(TMP_COL_N) 그리드 노출 안 됨", _check_attr_nm_no_tmp): return

        def _select_first_row_then_check_btn_enabled():
            # 첫 행의 체크박스 클릭
            row_checkbox = d.find_elements(By.CSS_SELECTOR,
                ".v-data-table tbody tr:first-child .v-simple-checkbox")
            if not row_checkbox:
                row_checkbox = d.find_elements(By.CSS_SELECTOR,
                    ".v-data-table tbody tr:first-child input[type='checkbox']")
            if not row_checkbox:
                # vuetify 의 v-data-table show-select 는 .v-data-table__checkbox
                row_checkbox = d.find_elements(By.CSS_SELECTOR,
                    ".v-data-table tbody tr:first-child td:first-child")
            assert row_checkbox, "첫 행 체크박스 못 찾음"
            js_click(d, row_checkbox[0])
            time.sleep(0.6)

            # 변환 버튼 확인 — 체크 후 enabled
            btn = d.find_element(By.ID, "btn-resolve-selected")
            assert btn.is_displayed(), "체크 후에도 버튼 안 보임"
            disabled = btn.get_attribute("disabled") or btn.get_attribute("aria-disabled")
            cls = btn.get_attribute("class") or ""
            assert disabled != "true" and "v-btn--disabled" not in cls, \
                f"체크 후에도 비활성: disabled={disabled} class={cls}"
            print(f"  체크 후: 변환 버튼 enabled ✓")
            shot(d, "03_resolve_btn_enabled")
        if not step("6. 행 체크 후 변환 버튼 enabled", _select_first_row_then_check_btn_enabled): return

    finally:
        time.sleep(1)
        try: d.quit()
        except Exception: pass


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for n, s, _ in results: print(f"  [{s}] {n}")
    sys.exit(0 if f == 0 else 1)
