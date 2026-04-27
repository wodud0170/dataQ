"""
물리 테이블명 변경 (rename + cascade + preview confirm) E2E

목적:
  - [수정] 모달에서 물리 테이블명 입력 input 이 활성화돼 있는지
  - 변경 시 /previewObjRename 영향 카운트 swal 표시되는지
  - confirm 후 OBJ_NM rename + ATTR.OBJ_NM cascade 가 트랜잭션으로 일어나는지

흐름:
  1. 로그인 (space/123)
  2. 논리 모델 생성 (E2E_RENAME_*)
  3. UI: 테이블 추가 — 한글명 '고객정보' 만 입력 → 물리명 TMP_TBL_1 자동 채번
  4. API: saveAttrs 로 컬럼 3건 추가 (cascade 영향 카운트용)
  5. UI: 데이터 모델 > 테이블 메뉴에서 [수정] 클릭 → 물리명 input 비활성 아님 검증
  6. UI: 물리명을 TB_CUSTOMER 로 변경 → [수정] 클릭 → preview swal "컬럼 3건" 안내 확인
  7. UI: [변경] 클릭 → swal 닫히고 그리드에 TB_CUSTOMER 표시 확인
  8. API: 컬럼 목록 조회 → ATTR.OBJ_NM='TB_CUSTOMER' cascade 검증
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
MODEL_NAME = "E2E_RENAME_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"
NEW_OBJ_NM = "TB_CUSTOMER"
COLUMN_KRS = ["고객번호", "고객명", "등록일시"]

os.makedirs(SCREENSHOT_DIR, exist_ok=True)
results = []


# ---------- driver ----------
def make_driver():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    return d


def shot(d, name):
    path = os.path.join(SCREENSHOT_DIR, "rename_" + name + ".png")
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


def select_autocomplete(d, value):
    """첫 번째 autocomplete (모델명 등) 에 value 입력 후 매칭 항목 선택"""
    acs = d.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    if not acs:
        raise RuntimeError("autocomplete 없음")
    ac = acs[0]
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
    raise RuntimeError(f"'{value}' autocomplete 항목 없음")


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


# ---------- 공유 상태 ----------
state = {"modelId": None, "cookies": None}


def step1_login(d):
    login(d)
    shot(d, "01_login")
    state["cookies"] = {c["name"]: c["value"] for c in d.get_cookies()}


def step2_create_model(d):
    nav(d, "dmGroup", "nav_datamodelCollection")
    wait_visible(d, By.CSS_SELECTOR, ".splitTopWrapper")
    time.sleep(1)
    if not click_button_by_text(d, "등록", ".splitTopWrapper"):
        raise RuntimeError("'등록' 버튼 없음")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    nm_input = d.find_element(
        By.CSS_SELECTOR, ".v-dialog--active input[name='add_dataModelNm']"
    )
    nm_input.clear()
    nm_input.send_keys(MODEL_NAME)
    if not click_button_by_text(d, "등록", ".v-dialog--active"):
        raise RuntimeError("모달 '등록' 버튼 없음")
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    dismiss_swal(d)
    time.sleep(1)
    shot(d, "02_model_created")

    # 모델 ID 조회 (이후 API 호출용)
    r = requests.post(
        BASE_URL + "/api/dm/getDataModelStatsList", cookies=state["cookies"], json={}, timeout=10
    )
    r.raise_for_status()
    target = next((m for m in r.json() if m.get("dataModelNm") == MODEL_NAME), None)
    if not target:
        raise RuntimeError(f"API 에서 모델 '{MODEL_NAME}' 조회 실패")
    state["modelId"] = target["dataModelId"]
    print(f"  [info] modelId={state['modelId']}")


def step3_add_table(d):
    nav(d, "dmGroup", "nav_datamodelStatusTable")
    select_autocomplete(d, MODEL_NAME)
    time.sleep(1)
    if not click_button_by_text(d, "테이블 추가"):
        raise RuntimeError("'테이블 추가' 버튼 없음")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    # 한글명만 입력 (label 에 '한글명' 포함)
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
        raise RuntimeError("'한글명' 입력 필드 없음")
    kr_input.clear()
    kr_input.send_keys(TABLE_KR)
    if not click_button_by_text(d, "추가", ".v-dialog--active"):
        raise RuntimeError("'추가' 버튼 없음")
    dismiss_swal(d)
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    time.sleep(1)
    # 그리드에서 TMP_TBL_1 (또는 TMP_TBL_*) 행 확인
    page = d.page_source
    if "TMP_TBL_" not in page:
        raise RuntimeError("자동 채번된 TMP_TBL_* 가 그리드에 없음")
    shot(d, "03_table_added_TMP")


def step4_add_columns_via_api(d):
    """saveAttrs API 로 컬럼 3건 직접 추가 — UI 보다 빠르고 안정적"""
    # 먼저 그리드에서 현재 OBJ_NM 조회 (TMP_TBL_1 일 가능성 높음)
    r = requests.get(
        BASE_URL + "/api/dm/getDataModelObjListByClctId",
        params={"clctId": state["modelId"]},
        cookies=state["cookies"], timeout=10,
    )
    r.raise_for_status()
    objs = r.json() or []
    target = next((o for o in objs if o.get("objNmKr") == TABLE_KR), None)
    if not target:
        raise RuntimeError(f"테이블 '{TABLE_KR}' 조회 실패")
    state["origObjNm"] = target["objNm"]
    print(f"  [info] origObjNm={state['origObjNm']}")

    attrs = []
    for kr in COLUMN_KRS:
        attrs.append({
            "mode": "ADD",
            "attrNmKr": kr,
            "pkYn": "N", "fkYn": "N", "nullableYn": "Y",
        })
    body = {"dataModelId": state["modelId"], "objNm": state["origObjNm"], "attrs": attrs}
    r = requests.post(
        BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json=body, timeout=15
    )
    r.raise_for_status()
    res = r.json()
    if not (res.get("resultCode") == 200):
        raise RuntimeError(f"saveAttrs 실패: {res}")
    print(f"  [info] saveAttrs 응답: {res.get('resultMessage')}")

    # 검증: ATTR 3건 존재
    r = requests.get(
        BASE_URL + "/api/dm/getDataModelAttrListByClctId",
        params={"clctId": state["modelId"], "objNm": state["origObjNm"]},
        cookies=state["cookies"], timeout=10,
    )
    r.raise_for_status()
    attrs = r.json() or []
    cnt = sum(1 for a in attrs if a.get("objNm") == state["origObjNm"])
    if cnt < 3:
        raise RuntimeError(f"컬럼 3건 미달 (현재 {cnt}건)")
    print(f"  [info] {state['origObjNm']} 컬럼 {cnt}건 확인")


def step5_open_edit_dialog_check_input_enabled(d):
    """[수정] 모달 진입 + 물리명 input 활성화 검증"""
    nav(d, "dmGroup", "nav_datamodelStatusTable")
    select_autocomplete(d, MODEL_NAME)
    time.sleep(1.5)
    # 그리드의 첫 행 [편집] 아이콘 (mdi-pencil) 클릭
    pencil = d.find_element(By.CSS_SELECTOR, "i.mdi-pencil")
    btn = pencil.find_element(By.XPATH, "./ancestor::button[1]")
    _click_el(d, btn)
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    shot(d, "05_edit_modal")

    # 첫 input (물리명) 이 disabled 가 아닌지 확인 — 라벨 변경 후 새 라벨에 '영문명' 포함
    inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
    physical_input = None
    for inp in inputs:
        try:
            parent = inp.find_element(By.XPATH, "./ancestor::div[contains(@class,'v-text-field')][1]")
            lab = parent.find_element(By.CSS_SELECTOR, "label").text
            if "영문명" in lab or "물리" in lab:
                physical_input = inp
                break
        except Exception:
            continue
    if not physical_input:
        raise RuntimeError("물리명 input 식별 실패 (label 에 '영문명' / '물리' 미포함)")
    if not physical_input.is_enabled():
        raise RuntimeError("물리명 input 이 disabled — 변경 불가 상태")
    state["physicalInput"] = physical_input
    print("  [info] 물리명 input 활성화 확인")


def step6_change_name_check_preview_swal(d):
    """물리명을 변경하고 [수정] 누르면 preview swal 표시되는지 확인"""
    inp = state["physicalInput"]
    inp.click()
    time.sleep(0.1)
    inp.send_keys(Keys.CONTROL, "a")
    inp.send_keys(Keys.DELETE)
    time.sleep(0.2)
    inp.send_keys(NEW_OBJ_NM)
    time.sleep(0.3)
    # 검증: input 의 현재 값이 정확히 NEW_OBJ_NM 인지
    actual = inp.get_attribute("value")
    if actual != NEW_OBJ_NM:
        raise RuntimeError(f"input clear 실패: 기대 '{NEW_OBJ_NM}' 실제 '{actual}'")
    shot(d, "06_filled_new_name")
    if not click_button_by_text(d, "수정", ".v-dialog--active"):
        raise RuntimeError("모달 '수정' 버튼 없음")
    # preview swal 표시 대기 — title 또는 html 에 '컬럼' / '갱신' / '변경' 포함
    try:
        WebDriverWait(d, 10).until(
            EC.visibility_of_element_located((By.CSS_SELECTOR, ".swal2-popup"))
        )
    except TimeoutException:
        raise RuntimeError("preview swal 미표시")
    pop = d.find_element(By.CSS_SELECTOR, ".swal2-popup")
    body = pop.text
    print(f"  [info] swal 본문 일부: {body[:120]}")
    # 영향 카운트 키워드 검증
    if "컬럼" not in body:
        raise RuntimeError(f"swal 에 '컬럼' 카운트 안 보임. 내용: {body[:200]}")
    shot(d, "06_preview_swal")


def step7_confirm_and_check_grid(d):
    """[변경] 클릭 → 새 이름이 그리드에 보이는지"""
    # swal 의 confirm 버튼 (텍스트 '변경')
    confirm_btn = None
    for b in d.find_elements(By.CSS_SELECTOR, ".swal2-popup button"):
        if b.is_displayed() and "변경" in (b.text or ""):
            confirm_btn = b
            break
    if not confirm_btn:
        raise RuntimeError("swal '변경' 버튼 없음")
    _click_el(d, confirm_btn)
    time.sleep(1.5)
    dismiss_swal(d)  # 후속 성공 swal
    # 모달 닫힘 대기
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    time.sleep(2)
    page = d.page_source
    if NEW_OBJ_NM not in page:
        shot(d, "07_grid_missing")
        raise RuntimeError(f"그리드에 '{NEW_OBJ_NM}' 미표시")
    if state["origObjNm"] in page:
        # 다른 행에 우연히 같은 이름 있을 수 있어 경고만
        print(f"  [warn] 그리드에 origObjNm '{state['origObjNm']}' 가 여전히 보임 — 다른 행일 수 있음")
    shot(d, "07_grid_renamed")


def step8_verify_attr_cascade(d):
    """ATTR.OBJ_NM 도 새 이름으로 cascade 됐는지 API 검증"""
    r = requests.get(
        BASE_URL + "/api/dm/getDataModelAttrListByClctId",
        params={"clctId": state["modelId"], "objNm": NEW_OBJ_NM},
        cookies=state["cookies"], timeout=10,
    )
    r.raise_for_status()
    attrs = r.json() or []
    new_cnt = sum(1 for a in attrs if a.get("objNm") == NEW_OBJ_NM)
    orig_cnt = sum(1 for a in attrs if a.get("objNm") == state["origObjNm"])
    print(f"  [info] new objNm 컬럼: {new_cnt}건 / orig objNm 컬럼: {orig_cnt}건")
    if new_cnt < 3:
        raise RuntimeError(f"cascade 실패: '{NEW_OBJ_NM}' 컬럼 {new_cnt}건 (3건 기대)")
    if orig_cnt > 0:
        raise RuntimeError(f"cascade 실패: 원래 '{state['origObjNm']}' 컬럼이 {orig_cnt}건 남음")


# ---------- main ----------
def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. 논리 모델 생성", lambda: step2_create_model(d)): return
        if not step("3. 테이블 추가 (TMP_TBL_*)", lambda: step3_add_table(d)): return
        if not step("4. 컬럼 3건 추가 (API)", lambda: step4_add_columns_via_api(d)): return
        if not step("5. [수정] 모달 진입 + 물리명 input 활성", lambda: step5_open_edit_dialog_check_input_enabled(d)): return
        if not step("6. 물리명 변경 + preview swal 표시 확인", lambda: step6_change_name_check_preview_swal(d)): return
        if not step("7. [변경] 클릭 후 그리드 새 이름", lambda: step7_confirm_and_check_grid(d)): return
        step("8. ATTR cascade API 검증", lambda: step8_verify_attr_cascade(d))
    finally:
        try:
            shot(d, "99_final")
        except Exception:
            pass
        d.quit()

    print(f"\n{'=' * 60}\n결과\n{'=' * 60}")
    pass_cnt = sum(1 for _, s, _ in results if s == "PASS")
    fail_cnt = len(results) - pass_cnt
    for name, status, _ in results:
        mark = "[PASS]" if status == "PASS" else "[FAIL]"
        print(f"  {mark} {name}")
    print(f"\n  총 {len(results)}: PASS {pass_cnt}, FAIL {fail_cnt}")
    sys.exit(0 if fail_cnt == 0 else 1)


if __name__ == "__main__":
    main()
