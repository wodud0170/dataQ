"""
53번 설계 E2E 테스트 — 그리드 인라인 편집으로 컬럼 추가 + 배치 저장

흐름:
  1. 로그인 (space/123)
  2. 논리 모델 생성
  3. 테이블 추가 (한글명만)
  4. 컬럼 화면 이동 → 추가 대상 테이블 선택
  5. [+ 컬럼 추가] 버튼 3번 클릭 → 그리드에 빈 행 3개 추가
  6. 각 행의 inline input에 한글명 입력 (사용자명, 고객명, 등록일시)
  7. [저장] 클릭 → saveAttrs API 호출
  8. API로 저장 결과 검증 (TMP_COL_N 3건 + TERMS_STND_YN='N')
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
MODEL_NAME = "E2E_53_GRID_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"
COLUMN_KRS = ["사용자명", "고객명", "등록일시"]

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
    path = os.path.join(SCREENSHOT_DIR, "e2e53_" + name + ".png")
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
                time.sleep(0.5)
                continue
        except Exception:
            pass
        break


def login(d, user="space", pw="123"):
    d.get(BASE_URL + "/signin")
    wait_visible(d, By.CSS_SELECTOR, "input[type='text']", 15)
    time.sleep(1)
    id_in = d.find_element(By.CSS_SELECTOR, "input[type='text']")
    id_in.clear()
    id_in.send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.clear()
    pw_in.send_keys(pw)
    pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(2)


def _click_el(d, el):
    try:
        el.click()
    except Exception:
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        time.sleep(0.2)
        try:
            el.click()
        except Exception:
            d.execute_script("arguments[0].click();", el)


def nav(d, group_id, menu_id):
    dismiss_swal(d)
    menu_items = d.find_elements(By.ID, menu_id)
    need_expand = not menu_items or not menu_items[0].is_displayed()
    if need_expand:
        g = wait_clickable(d, By.ID, group_id, 10)
        _click_el(d, g)
        try:
            wait_visible(d, By.ID, menu_id, 5)
        except TimeoutException:
            _click_el(d, g)
            wait_visible(d, By.ID, menu_id, 5)
    m = wait_visible(d, By.ID, menu_id, 10)
    _click_el(d, m)
    time.sleep(2)


def click_button_by_text(d, text, scope_css=None):
    root = d
    if scope_css:
        root = d.find_element(By.CSS_SELECTOR, scope_css)
    buttons = root.find_elements(By.CSS_SELECTOR, "button")
    for b in buttons:
        if b.is_displayed() and text in (b.text or ""):
            b.click()
            return True
    return False


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
        print(f"  >> FAIL: {e}")
        print(tb)
        return False


def select_first_autocomplete(d, scope_el, value):
    """scope_el 내 첫 번째 autocomplete에 value 입력 후 첫 옵션 선택"""
    ac = scope_el.find_element(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    _click_el(d, ac)
    time.sleep(0.3)
    ac.send_keys(Keys.CONTROL, "a")
    ac.send_keys(Keys.DELETE)
    ac.send_keys(value)
    time.sleep(1.2)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not items:
        items = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    for it in items:
        if value in (it.text or ""):
            _click_el(d, it)
            time.sleep(0.5)
            return
    raise RuntimeError(f"'{value}' 매칭 항목 없음 (후보 {len(items)}개)")


# ---------- steps ----------
def step1_login(d):
    login(d)
    shot(d, "01_login")


def step2_create_model(d):
    nav(d, "dmGroup", "nav_datamodelCollection")
    wait_visible(d, By.CSS_SELECTOR, ".splitTopWrapper")
    time.sleep(1)
    if not click_button_by_text(d, "등록", ".splitTopWrapper"):
        raise RuntimeError("상단 '등록' 버튼 없음")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    nm_input = d.find_element(By.CSS_SELECTOR, ".v-dialog--active input[name='add_dataModelNm']")
    nm_input.clear()
    nm_input.send_keys(MODEL_NAME)
    time.sleep(0.3)
    if not click_button_by_text(d, "등록", ".v-dialog--active"):
        raise RuntimeError("모달 '등록' 버튼 없음")
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    dismiss_swal(d)
    time.sleep(1)
    shot(d, "02_model_created")


def step3_add_table(d):
    nav(d, "dmGroup", "nav_datamodelStatusTable")
    # 필터바의 모델 autocomplete
    filter_bar = d.find_element(By.CSS_SELECTOR, ".filterWrapper")
    select_first_autocomplete(d, filter_bar, MODEL_NAME)
    shot(d, "03a_model_selected")
    if not click_button_by_text(d, "테이블 추가"):
        raise RuntimeError("'테이블 추가' 버튼 없음")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    # 한글명 입력
    inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
    kr_input = None
    for inp in inputs:
        try:
            parent = inp.find_element(By.XPATH, "./ancestor::div[contains(@class,'v-text-field')][1]")
            lab = parent.find_element(By.CSS_SELECTOR, "label").text
            if "한글명" in lab:
                kr_input = inp
                break
        except Exception:
            continue
    if not kr_input:
        raise RuntimeError("'한글명' 필드 없음")
    kr_input.clear()
    kr_input.send_keys(TABLE_KR)
    if not click_button_by_text(d, "추가", ".v-dialog--active"):
        raise RuntimeError("다이얼로그 '추가' 버튼 없음")
    dismiss_swal(d)
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    time.sleep(1)
    if TABLE_KR not in d.page_source:
        raise RuntimeError(f"'{TABLE_KR}' 테이블 그리드에 없음")
    shot(d, "03b_table_added")


def step4_grid_add_rows(d):
    nav(d, "dmGroup", "nav_datamodelStatusColumn")
    filter_bar = d.find_element(By.CSS_SELECTOR, ".filterWrapper")
    # Row 1: 데이터모델명 선택
    select_first_autocomplete(d, filter_bar, MODEL_NAME)
    time.sleep(1)
    shot(d, "04a_col_screen")
    # Row 3: 추가 대상 테이블 autocomplete (2번째 autocomplete)
    acs = filter_bar.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    if len(acs) < 2:
        raise RuntimeError(f"추가 대상 테이블 autocomplete 없음 (autocomplete {len(acs)}개)")
    target_ac = acs[1]
    _click_el(d, target_ac)
    time.sleep(0.5)
    target_ac.send_keys(TABLE_KR)
    time.sleep(1.0)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not items:
        items = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    if not items:
        raise RuntimeError(f"추가 대상 테이블 옵션 없음 ('{TABLE_KR}')")
    _click_el(d, items[0])
    time.sleep(0.5)
    shot(d, "04b_target_selected")

    # [+ 컬럼 추가] 3번 클릭
    add_btn = wait_clickable(d, By.ID, "btn-add-col-row", 5)
    for i in range(3):
        _click_el(d, add_btn)
        time.sleep(0.3)
    shot(d, "04c_3_empty_rows")

    # 각 행의 placeholder="컬럼 한글명" input에 순서대로 입력
    inline_inputs = d.find_elements(By.CSS_SELECTOR, "input[placeholder='컬럼 한글명']")
    if len(inline_inputs) < 3:
        raise RuntimeError(f"인라인 한글명 input이 3개 이상 아님 (있는 수: {len(inline_inputs)})")
    for i, kr in enumerate(COLUMN_KRS):
        inp = inline_inputs[i]
        _click_el(d, inp)
        inp.clear()
        inp.send_keys(kr)
        time.sleep(0.2)
    shot(d, "04d_rows_filled")

    # [저장] 버튼 클릭
    save_btn = wait_clickable(d, By.ID, "btn-save-attrs", 5)
    _click_el(d, save_btn)
    # 저장 성공 swal (timer 1500ms)
    time.sleep(2.5)
    dismiss_swal(d)
    shot(d, "04e_saved")


def step5_verify_via_api(d):
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    # 모델 ID 조회
    r = requests.post(BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10)
    r.raise_for_status()
    models = r.json()
    target = next((m for m in models if m.get("dataModelNm") == MODEL_NAME), None)
    if not target:
        raise RuntimeError(f"모델 '{MODEL_NAME}' API 조회 실패")
    dm_id = target.get("dataModelId")
    # 컬럼 리스트 조회
    r2 = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                      params={"clctId": dm_id}, cookies=cookies, timeout=10)
    r2.raise_for_status()
    attrs = r2.json() or []
    print(f"  [api] 모델 '{MODEL_NAME}' 컬럼 수: {len(attrs)}")
    if len(attrs) < 3:
        raise RuntimeError(f"컬럼 3건 이상 기대, 실제 {len(attrs)}건")
    # 한글명 매칭 확인
    krs = {a.get("attrNmKr") for a in attrs}
    missing = [kr for kr in COLUMN_KRS if kr not in krs]
    if missing:
        raise RuntimeError(f"저장되지 않은 한글명: {missing}")
    # 53번 §6-0: 물리명은 TMP_COL_N, TERMS_STND_YN='N'
    for a in attrs:
        if a.get("attrNmKr") in COLUMN_KRS:
            attr_nm = a.get("attrNm") or ""
            stnd = a.get("termsStndYn")
            if not attr_nm.startswith("TMP_COL_"):
                raise RuntimeError(f"'{a.get('attrNmKr')}' 물리명이 TMP_COL_* 가 아님: {attr_nm}")
            if stnd != "N":
                raise RuntimeError(f"'{a.get('attrNmKr')}' 비표준(N) 기대, 실제 {stnd}")
    print(f"  [verify] 3건 모두 TMP_COL_* + TERMS_STND_YN='N' 확인")


def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. 논리 모델 생성", lambda: step2_create_model(d)): return
        if not step("3. 테이블 추가", lambda: step3_add_table(d)): return
        if not step("4. 그리드 빈 행 3개 + 한글명 입력 + 저장", lambda: step4_grid_add_rows(d)): return
        step("5. API 검증 (TMP_COL_N + TERMS_STND_YN='N')", lambda: step5_verify_via_api(d))
    finally:
        try: shot(d, "99_final")
        except Exception: pass
        d.quit()

    print(f"\n{'=' * 60}\n결과\n{'=' * 60}")
    pass_cnt = sum(1 for _, s, _ in results if s == "PASS")
    fail_cnt = len(results) - pass_cnt
    for name, status, _ in results:
        mark = "[PASS]" if status == "PASS" else "[FAIL]"
        print(f"  {mark} {name}")
    print(f"\n  총 {len(results)}: PASS {pass_cnt}, FAIL {fail_cnt}")
    print(f"  스크린샷: {SCREENSHOT_DIR}")
    sys.exit(0 if fail_cnt == 0 else 1)


if __name__ == "__main__":
    main()
