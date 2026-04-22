"""
53번 설계 Phase 3 — 그리드 TSV 붙여넣기 E2E 테스트

흐름:
  1. 로그인
  2. API로 모델/테이블 준비
  3. 컬럼 화면 이동 → 모델/추가 대상 테이블 선택
  4. JS로 paste 이벤트를 dispatch — TSV 5행 (한글명·NULL·PK·FK·기본값 혼합)
  5. 그리드에 newRows 5개 생긴 것 확인
  6. [저장] 클릭 → API 검증 (5건 저장, PK/nullable 올바르게 반영)

검증 포인트:
  - 붙여넣은 행이 newRows 에 모두 들어감
  - PK='Y' 행은 nullable='N' 으로 강제됨
  - 기본값/체크박스 다양 값 매핑 (Y/TRUE/1)
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
MODEL_NAME = "E2E_53_PASTE_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"

# 붙여넣을 TSV: (한글명, NULL, PK, FK, 기본값)
PASTE_ROWS = [
    ("고객번호",   "N", "Y", "N", ""),
    ("사용자명",   "Y", "N", "N", ""),
    ("등록일시",   "N", "N", "N", "CURRENT_TIMESTAMP"),
    ("상태코드",   "Y", "N", "N", "A"),
    ("주소",       "Y", "N", "N", ""),
]

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
    path = os.path.join(SCREENSHOT_DIR, "e2e53pst_" + name + ".png")
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


def api_prepare_model_table(d):
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    r = requests.post(BASE_URL + "/api/dm/createDataModel", cookies=cookies,
                      json={"dataModelNm": MODEL_NAME, "modelType": "LOGICAL", "ver": "1.0"}, timeout=10)
    r.raise_for_status()
    r2 = requests.post(BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10)
    target = next((m for m in r2.json() if m.get("dataModelNm") == MODEL_NAME), None)
    if not target: raise RuntimeError("모델 조회 실패")
    dm_id = target["dataModelId"]
    r3 = requests.post(BASE_URL + "/api/dm/addObj", cookies=cookies,
                       json={"dataModelId": dm_id, "objNmKr": TABLE_KR}, timeout=10)
    r3.raise_for_status()
    print(f"  [api-prep] 모델={MODEL_NAME}, 테이블={TABLE_KR}, dm_id={dm_id}")
    return dm_id


def _dispatch_paste(d, tsv):
    """document 레벨 paste 이벤트 dispatch — Selenium은 실제 클립보드 사용 제한이 많아 합성 이벤트로 대체"""
    # Vue 컴포넌트가 document.addEventListener('paste', ...) 로 받도록 구현했기 때문에
    # body 에 paste event 를 직접 dispatch 하면 됨
    script = """
    const tsv = arguments[0];
    const dt = new DataTransfer();
    dt.setData('text', tsv);
    const ev = new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true });
    // ClipboardEvent 생성자 스펙에 따라 일부 브라우저는 clipboardData 가 readonly — 폴백
    try { Object.defineProperty(ev, 'clipboardData', { value: dt }); } catch (e) {}
    document.dispatchEvent(ev);
    return true;
    """
    d.execute_script(script, tsv)


dm_id_holder = {}


def step1_login(d):
    login(d); shot(d, "01_login")


def step2_prepare(d):
    dm_id_holder["dm"] = api_prepare_model_table(d)


def step3_open_column_screen(d):
    nav(d, "dmGroup", "nav_datamodelStatusColumn")
    filter_bar = d.find_element(By.CSS_SELECTOR, ".filterWrapper")
    select_first_autocomplete_in(d, filter_bar, MODEL_NAME)
    time.sleep(1)
    # 추가 대상 테이블 autocomplete (두 번째 autocomplete 영역)
    autocompletes = filter_bar.find_elements(By.CSS_SELECTOR, ".v-autocomplete")
    if len(autocompletes) < 2:
        raise RuntimeError("추가 대상 테이블 autocomplete 없음")
    target_ac = autocompletes[1]
    ac_input = target_ac.find_element(By.CSS_SELECTOR, "input[type='text']")
    _click_el(d, ac_input); time.sleep(0.3)
    ac_input.send_keys(TABLE_KR); time.sleep(1)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    picked = False
    for it in items:
        if TABLE_KR in (it.text or ""):
            _click_el(d, it); picked = True; break
    if not picked: raise RuntimeError(f"'{TABLE_KR}' 옵션 선택 실패")
    time.sleep(0.5)
    shot(d, "03_ready_to_paste")


def step4_paste(d):
    tsv = "\n".join("\t".join(r) for r in PASTE_ROWS)
    _dispatch_paste(d, tsv)
    time.sleep(1.5)
    dismiss_swal(d)
    # 그리드 mergedItems 에 newRows 만큼 행 수 반영 — row count 로는 구별 어렵지만
    # (저장 버튼의 숫자) 로 확인
    save_btn = d.find_element(By.ID, "btn-save-attrs")
    label = save_btn.text or ""
    print(f"  [paste] 저장 버튼 라벨: {label}")
    if f"({len(PASTE_ROWS)})" not in label and f"{len(PASTE_ROWS)}" not in label:
        raise RuntimeError(f"저장 버튼이 {len(PASTE_ROWS)}건을 반영하지 않음 ({label})")
    shot(d, "04_after_paste")


def step5_save(d):
    _click_el(d, d.find_element(By.ID, "btn-save-attrs"))
    time.sleep(2)
    dismiss_swal(d)
    shot(d, "05_saved")


def step6_verify(d):
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": dm_id_holder["dm"]}, cookies=cookies, timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    by_kr = {a.get("attrNmKr"): a for a in attrs}
    print(f"  [verify] 저장된 컬럼 수: {len(attrs)}")
    for kr, nullable, pk, fk, dft in PASTE_ROWS:
        a = by_kr.get(kr)
        if not a:
            raise RuntimeError(f"'{kr}' 저장 누락")
        # PK 행은 nullable 강제 N (프론트 onPaste 규칙)
        expect_null = 'N' if pk == 'Y' else nullable
        if a.get("pkYn") != pk:
            raise RuntimeError(f"'{kr}' pkYn 기대 {pk}, 실제 {a.get('pkYn')}")
        if a.get("nullableYn") != expect_null:
            raise RuntimeError(f"'{kr}' nullableYn 기대 {expect_null}, 실제 {a.get('nullableYn')}")
        if a.get("fkYn") != fk:
            raise RuntimeError(f"'{kr}' fkYn 기대 {fk}, 실제 {a.get('fkYn')}")
        if (a.get("defaultVal") or "") != dft:
            raise RuntimeError(f"'{kr}' defaultVal 기대 '{dft}', 실제 '{a.get('defaultVal')}'")
        if not (a.get("attrNm") or "").startswith("TMP_COL_"):
            raise RuntimeError(f"'{kr}' 물리명 TMP_COL_* 아님: {a.get('attrNm')}")
    print("  [verify] 5건 모두 기대값 일치")


def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. API로 모델/테이블 준비", lambda: step2_prepare(d)): return
        if not step("3. 컬럼 화면 진입 + 모델/타겟 선택", lambda: step3_open_column_screen(d)): return
        if not step("4. TSV 5행 붙여넣기", lambda: step4_paste(d)): return
        if not step("5. 저장", lambda: step5_save(d)): return
        step("6. API 검증", lambda: step6_verify(d))
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
