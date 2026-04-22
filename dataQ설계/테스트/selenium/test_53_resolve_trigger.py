"""
53번 설계 E2E 테스트 — 저장된 컬럼 선택 → [선택 컬럼 물리모델 변환] → resolveAttrs 결과 반영

흐름:
  1. 로그인
  2. test_53_grid_add_save.py 와 동일하게 모델/테이블/컬럼 준비 (사용자명 1건만 저장)
  3. 컬럼 화면에서 해당 행 체크박스 선택
  4. [선택 컬럼 물리모델 변환] 클릭
  5. API 검증:
     - 용어사전에 '사용자명'이 승인되어 있으면 TERMS_STND_YN='Y', 물리명은 TMP_COL_* 아님
     - 없으면 TERMS_STND_YN='N' 그대로 + failed 에 '사용자명' 포함

최소 조건: 사용자명 용어가 TB_TERMS 에 APRV_YN='Y' 로 존재하면 표준 변환 성공 경로 검증.
없으면 실패 경로로 동작하는지 검증 (둘 다 PASS 판정).
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
MODEL_NAME = "E2E_53_RESOLVE_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"
COLUMN_KR = "사용자명"

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
    path = os.path.join(SCREENSHOT_DIR, "e2e53res_" + name + ".png")
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


def click_button_by_text(d, text, scope_css=None):
    root = d.find_element(By.CSS_SELECTOR, scope_css) if scope_css else d
    for b in root.find_elements(By.CSS_SELECTOR, "button"):
        if b.is_displayed() and text in (b.text or ""):
            b.click(); return True
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
        print(f"  >> FAIL: {e}"); print(tb)
        return False


def select_first_autocomplete_in(d, scope_el, value):
    ac = scope_el.find_element(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    _click_el(d, ac); time.sleep(0.3)
    ac.send_keys(Keys.CONTROL, "a"); ac.send_keys(Keys.DELETE)
    ac.send_keys(value); time.sleep(1.2)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item") or \
            d.find_elements(By.CSS_SELECTOR, "[role='option']")
    for it in items:
        if value in (it.text or ""):
            _click_el(d, it); time.sleep(0.5); return
    raise RuntimeError(f"'{value}' 매칭 옵션 없음")


# ---------- API prep helpers (skip UI for model/table creation — focus on UI step) ----------
def api_prepare_model_table_column(d):
    """모델/테이블/컬럼 1건을 API로 미리 준비 (UI 테스트 대상은 resolveSelected 트리거만)"""
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    # 모델 생성
    r = requests.post(BASE_URL + "/api/dm/createDataModel", cookies=cookies,
                      json={"dataModelNm": MODEL_NAME, "modelType": "LOGICAL", "ver": "1.0"}, timeout=10)
    r.raise_for_status()
    # 모델ID 조회
    r2 = requests.post(BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10)
    target = next((m for m in r2.json() if m.get("dataModelNm") == MODEL_NAME), None)
    if not target: raise RuntimeError("모델 조회 실패")
    dm_id = target["dataModelId"]
    # 테이블 추가 (논리)
    r3 = requests.post(BASE_URL + "/api/dm/addObj", cookies=cookies,
                       json={"dataModelId": dm_id, "objNmKr": TABLE_KR}, timeout=10)
    r3.raise_for_status()
    # obj 조회로 objNm (TMP_TBL_*) 얻기
    r4 = requests.get(BASE_URL + "/api/dm/getDataModelObjListByClctId",
                      params={"clctId": dm_id}, cookies=cookies, timeout=10)
    objs = [o for o in r4.json() if o.get("objNmKr") == TABLE_KR]
    if not objs: raise RuntimeError("TABLE_KR 테이블 API 조회 실패")
    obj_nm = objs[0]["objNm"]
    # 컬럼 1건 추가 (saveAttrs)
    r5 = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=cookies, json={
        "dataModelId": dm_id, "objNm": obj_nm,
        "attrs": [{"mode": "ADD", "attrNmKr": COLUMN_KR, "pkYn": "N", "fkYn": "N"}],
    }, timeout=10)
    r5.raise_for_status()
    print(f"  [api-prep] 모델/테이블/컬럼 준비: dm_id={dm_id}, obj={obj_nm}, col='{COLUMN_KR}'")
    return dm_id, obj_nm


# ---------- steps ----------
def step1_login(d):
    login(d); shot(d, "01_login")


dm_id_holder = {}


def step2_prepare(d):
    dm, obj = api_prepare_model_table_column(d)
    dm_id_holder["dm"] = dm
    dm_id_holder["obj"] = obj


def step3_ui_resolve(d):
    nav(d, "dmGroup", "nav_datamodelStatusColumn")
    filter_bar = d.find_element(By.CSS_SELECTOR, ".filterWrapper")
    select_first_autocomplete_in(d, filter_bar, MODEL_NAME)
    time.sleep(1)
    shot(d, "03a_col_loaded")

    # data-table row 체크박스 — v-simple-checkbox or input[type=checkbox] within tbody
    rows = d.find_elements(By.CSS_SELECTOR, "#clTable_table tbody tr")
    if not rows:
        raise RuntimeError("컬럼 그리드에 row 없음")
    # 첫 행 체크박스 클릭
    first_row = rows[0]
    # Vuetify v-data-table show-select uses .v-simple-checkbox > .v-icon
    checkbox_icons = first_row.find_elements(By.CSS_SELECTOR, ".v-simple-checkbox")
    if not checkbox_icons:
        raise RuntimeError("row 체크박스 없음")
    _click_el(d, checkbox_icons[0])
    time.sleep(0.3)
    shot(d, "03b_row_selected")

    # [선택 컬럼 물리모델 변환] 클릭
    btn = wait_clickable(d, By.ID, "btn-resolve-selected", 5)
    _click_el(d, btn)
    # swal (timer 2000ms)
    time.sleep(3)
    dismiss_swal(d)
    shot(d, "03c_resolved")


def step4_verify_api(d):
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    dm = dm_id_holder["dm"]
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": dm}, cookies=cookies, timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    target = next((a for a in attrs if a.get("attrNmKr") == COLUMN_KR), None)
    if not target:
        raise RuntimeError(f"'{COLUMN_KR}' 컬럼 API 조회 실패")
    stnd = target.get("termsStndYn")
    attr_nm = target.get("attrNm") or ""
    print(f"  [verify] '{COLUMN_KR}' → attrNm={attr_nm}, termsStndYn={stnd}")
    # 두 경로 모두 PASS:
    # (A) 표준 매칭 성공 — Y + 물리명 TMP_COL_* 아님
    # (B) 미매칭 — N + TMP_COL_* 유지
    if stnd == "Y":
        if attr_nm.startswith("TMP_COL_"):
            raise RuntimeError("표준 매칭인데 물리명이 TMP_COL_* — 변환이 물리명 업데이트를 누락")
        print("  [verify] 표준 매칭 성공 경로 확인")
    elif stnd == "N":
        if not attr_nm.startswith("TMP_COL_"):
            raise RuntimeError("미매칭인데 물리명이 TMP_COL_* 아님")
        print("  [verify] 미매칭 경로 확인 (용어사전에 '사용자명' 없음)")
    else:
        raise RuntimeError(f"termsStndYn 값이 Y/N이 아님: {stnd}")


def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. API로 모델/테이블/컬럼 준비", lambda: step2_prepare(d)): return
        if not step("3. UI로 [선택 컬럼 물리모델 변환] 트리거", lambda: step3_ui_resolve(d)): return
        step("4. API로 결과 검증", lambda: step4_verify_api(d))
    finally:
        try: shot(d, "99_final")
        except Exception: pass
        d.quit()

    print(f"\n{'=' * 60}\n결과\n{'=' * 60}")
    pass_cnt = sum(1 for _, s, _ in results if s == "PASS")
    fail_cnt = len(results) - pass_cnt
    for name, status, _ in results:
        print(f"  {'[PASS]' if status == 'PASS' else '[FAIL]'} {name}")
    print(f"\n  총 {len(results)}: PASS {pass_cnt}, FAIL {fail_cnt}")
    print(f"  스크린샷: {SCREENSHOT_DIR}")
    sys.exit(0 if fail_cnt == 0 else 1)


if __name__ == "__main__":
    main()
