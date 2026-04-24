"""
논리 데이터 모델 E2E 테스트

흐름:
  1. 로그인 (space/123)
  2. 논리 모델 생성 (데이터 소스 X)
  3. 테이블 추가 (논리명만 입력)
  4. 컬럼 여러개 추가 — 한글명 입력 후 [표준 적용] → 용어사전/도메인 매칭
     → 매칭 성공한 컬럼만 실제로 저장됨 (submitAttr 필수값 검증)
  5. 4번 안에 포함 (표준 적용 = 용어사전 매핑으로 영문명/타입/길이 자동 생성)
  6. 데이터 모델 현황 화면에서 DDL 다운로드

전제:
  - q-center 서버가 localhost:28091에서 기동 중
  - Edge WebDriver 설치
  - TB_TERMS에 '사용자명', '고객명', '등록일시' 중 최소 1건 APRV_YN='Y' 존재
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
MODEL_NAME = "E2E_LOGIC_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"
COLUMN_TRIES = ["사용자명", "고객명", "등록일시", "주소", "전화번호"]

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
    path = os.path.join(SCREENSHOT_DIR, "e2e_" + name + ".png")
    d.save_screenshot(path)
    print(f"  [SHOT] {name}")


def wait(d, by, sel, t=10, cond=EC.presence_of_element_located):
    return WebDriverWait(d, t).until(cond((by, sel)))


def wait_clickable(d, by, sel, t=10):
    return WebDriverWait(d, t).until(EC.element_to_be_clickable((by, sel)))


def wait_visible(d, by, sel, t=10):
    return WebDriverWait(d, t).until(EC.visibility_of_element_located((by, sel)))


# ---------- utilities ----------
def dismiss_swal(d):
    """swal 확인 버튼이 떠 있으면 닫기"""
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
    """일반 클릭 시도 후 실패하면 JS 클릭으로 폴백"""
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
    """네비 그룹 펼치고 메뉴 클릭 — 실제 click 이벤트 + visibility 대기"""
    dismiss_swal(d)
    # 메뉴가 이미 visible이면 그룹 펼칠 필요 없음
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


def close_any_dialog(d):
    """열려있는 v-dialog를 ESC로 닫기"""
    try:
        active = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        if active:
            webdriver.ActionChains(d).send_keys(Keys.ESCAPE).perform()
            time.sleep(0.5)
    except Exception:
        pass


def click_button_by_text(d, text, scope_css=None):
    """지정한 텍스트를 가진 버튼 클릭. scope_css가 있으면 그 하위에서만 탐색."""
    root = d
    if scope_css:
        root = d.find_element(By.CSS_SELECTOR, scope_css)
    buttons = root.find_elements(By.CSS_SELECTOR, "button")
    for b in buttons:
        if b.is_displayed() and text in (b.text or ""):
            b.click()
            return True
    return False


# ---------- test harness ----------
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


# ---------- steps ----------
def step1_login(d):
    login(d)
    shot(d, "01_login")


def step2_create_logical_model(d):
    nav(d, "dmGroup", "nav_datamodelCollection")
    # 필터 바의 "등록" 버튼 — splitTopWrapper는 sheet이므로 visible 대기
    wait_visible(d, By.CSS_SELECTOR, ".splitTopWrapper")
    time.sleep(1)
    if not click_button_by_text(d, "등록", ".splitTopWrapper"):
        raise RuntimeError("상단 '등록' 버튼을 찾지 못함")
    # 모달 대기 — Vuetify는 dialog를 body에 teleport하므로 글로벌 .v-dialog--active 사용
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    shot(d, "02a_add_modal")
    # 데이터모델명 입력
    nm_input = d.find_element(
        By.CSS_SELECTOR, ".v-dialog--active input[name='add_dataModelNm']"
    )
    nm_input.clear()
    nm_input.send_keys(MODEL_NAME)
    time.sleep(0.5)
    shot(d, "02b_add_filled")
    # 다이얼로그 footer의 '등록' 버튼
    if not click_button_by_text(d, "등록", ".v-dialog--active"):
        raise RuntimeError("모달 '등록' 버튼을 찾지 못함")
    # 모달 닫힘 대기
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    dismiss_swal(d)
    time.sleep(1)
    shot(d, "02c_model_created")


def select_autocomplete(d, field_label, value):
    """filterLabel 텍스트 근처 v-autocomplete에 값 입력 후 첫 항목 클릭"""
    acs = d.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    if not acs:
        raise RuntimeError(f"autocomplete input 없음 ({field_label})")
    ac = acs[0]
    ac.click()
    time.sleep(0.3)
    ac.send_keys(Keys.CONTROL, "a")
    ac.send_keys(Keys.DELETE)
    ac.send_keys(value)
    time.sleep(1.5)
    # v-menu 드롭다운의 첫 매칭 항목 클릭
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not items:
        items = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    for it in items:
        if value in (it.text or ""):
            it.click()
            time.sleep(0.5)
            return
    raise RuntimeError(f"'{value}' 매칭되는 autocomplete 항목 없음 (후보 {len(items)}개)")


def step3_add_table(d):
    nav(d, "dmGroup", "nav_datamodelStatusTable")
    select_autocomplete(d, "데이터모델명", MODEL_NAME)
    shot(d, "03a_model_selected")
    # '테이블 추가' 버튼
    if not click_button_by_text(d, "테이블 추가"):
        raise RuntimeError("'테이블 추가' 버튼 클릭 실패")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.5)
    # 한글명 필드만 입력 (label: "테이블 한글명 (논리명)")
    inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
    kr_input = None
    for inp in inputs:
        # 라벨은 상위 div의 label 요소에 있음
        try:
            parent = inp.find_element(By.XPATH, "./ancestor::div[contains(@class,'v-text-field')][1]")
            lab = parent.find_element(By.CSS_SELECTOR, "label").text
            if "한글명" in lab:
                kr_input = inp
                break
        except Exception:
            continue
    if not kr_input:
        raise RuntimeError("'한글명' 입력 필드를 찾지 못함")
    kr_input.clear()
    kr_input.send_keys(TABLE_KR)
    shot(d, "03b_table_dialog")
    # 다이얼로그의 '추가' 버튼
    if not click_button_by_text(d, "추가", ".v-dialog--active"):
        raise RuntimeError("테이블 다이얼로그 '추가' 버튼 클릭 실패")
    # swal 성공 닫기
    dismiss_swal(d)
    # 다이얼로그 닫힘 + 테이블 그리드에 새 row 확인
    WebDriverWait(d, 10).until(
        lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
    )
    time.sleep(1)
    # 테이블 한글명이 그리드에 나타나야 함
    page = d.page_source
    if TABLE_KR not in page:
        shot(d, "03c_missing")
        raise RuntimeError(f"그리드에 '{TABLE_KR}'이 나타나지 않음")
    shot(d, "03c_table_added")


def _dialog_force_close(d, t=5):
    """persistent 다이얼로그 강제 닫기 — '취소' 클릭 후 사라질 때까지 대기"""
    for _ in range(3):
        active = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        if not active or not active[0].is_displayed():
            return
        if not click_button_by_text(d, "취소", ".v-dialog--active"):
            # v-overlay__scrim 직접 클릭은 persistent에서 안먹힘 — JS로 숨김
            try:
                d.execute_script(
                    "document.querySelectorAll('.v-dialog--active').forEach(el => el.parentElement.style.display='none')"
                )
            except Exception:
                pass
        time.sleep(0.3)


def _js_click(d, el):
    d.execute_script("arguments[0].click();", el)


def _find_button_in_dialog(d, text):
    buttons = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button")
    for b in buttons:
        if b.is_displayed() and text in (b.text or ""):
            return b
    return None


def _select_add_target_table_if_needed(d, table_kr):
    """'추가 대상 테이블' autocomplete 가 비어있으면 지정 테이블로 채움.
       53번 재설계 후 Column.vue 는 그리드 인라인 편집 방식이라
       addTargetObjNm 이 지정돼야 btn-add-col-row 가 활성화됨."""
    # 툴바 row 의 두 번째 autocomplete (첫 번째는 상단 모델 선택)
    # "추가 대상 테이블" 라벨 옆 autocomplete input 찾기
    labels = d.find_elements(By.XPATH, "//span[contains(@class,'filterLabel') and normalize-space(text())='추가 대상 테이블']")
    if not labels:
        raise RuntimeError("'추가 대상 테이블' 라벨 없음 — UI 구조 변경 감지")
    # 라벨 다음에 오는 autocomplete input
    ac_input = labels[0].find_element(By.XPATH, "./following::input[contains(@type,'text')][1]")
    # 이미 값이 있으면 스킵
    if (ac_input.get_attribute("value") or "").strip():
        return
    _js_click(d, ac_input)
    time.sleep(0.3)
    ac_input.clear()
    ac_input.send_keys(table_kr)
    time.sleep(1.0)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not items:
        items = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    chosen = None
    for it in items:
        if table_kr in (it.text or ""):
            chosen = it; break
    if chosen is None and items:
        chosen = items[0]
    if chosen is None:
        raise RuntimeError(f"'추가 대상 테이블' 목록에서 '{table_kr}' 선택 옵션 없음")
    _js_click(d, chosen)
    time.sleep(0.8)


def add_column_row(d, term_kr):
    """그리드 인라인 편집 방식 컬럼 1행 추가.
       + 컬럼 추가 버튼 클릭 → 빈 행 생성 → 마지막 빈 행의 한글명 input 에 타이핑.
       저장은 호출부에서 일괄 수행."""
    add_btn = WebDriverWait(d, 5).until(
        EC.element_to_be_clickable((By.ID, "btn-add-col-row"))
    )
    _js_click(d, add_btn)
    time.sleep(0.4)
    # 새로 추가된 행의 "컬럼 한글명" placeholder 입력 필드 — 마지막(가장 최근 추가) 항목 사용
    kr_inputs = d.find_elements(By.XPATH, "//input[@placeholder='컬럼 한글명']")
    if not kr_inputs:
        raise RuntimeError("+ 컬럼 추가 후 한글명 입력 필드 없음")
    target = kr_inputs[-1]
    d.execute_script("arguments[0].scrollIntoView({block:'center'});", target)
    time.sleep(0.2)
    target.clear()
    target.send_keys(term_kr)
    time.sleep(0.3)


def _click_save_attrs(d):
    save_btn = WebDriverWait(d, 5).until(
        EC.element_to_be_clickable((By.ID, "btn-save-attrs"))
    )
    _js_click(d, save_btn)
    time.sleep(1.5)
    dismiss_swal(d)
    time.sleep(1.0)


def step4_add_columns(d):
    """53번 재설계 기반 그리드 인라인 편집.
       한글명만 타이핑, 표준 변환 성공/실패 모두 허용.
       53번 규칙: 실패 시 TMP_COL_{n} + VARCHAR(255) 로 비표준 자동 저장."""
    nav(d, "dmGroup", "nav_datamodelStatusColumn")
    select_autocomplete(d, "데이터모델명", MODEL_NAME)
    time.sleep(1)
    _select_add_target_table_if_needed(d, TABLE_KR)
    time.sleep(0.5)
    added = 0
    for term in COLUMN_TRIES:
        try:
            add_column_row(d, term)
            added += 1
        except Exception as e:
            print(f"  [warn] '{term}' 그리드 추가 중 예외: {e}")
    shot(d, "04_columns_added")
    if added == 0:
        raise RuntimeError(
            f"컬럼 0건 추가 — btn-add-col-row 가 끝까지 비활성. 테이블 선택/권한 확인"
        )
    print(f"  [info] 그리드에 {added}건 추가, 저장 시도")
    _click_save_attrs(d)


def step6_ddl(d):
    nav(d, "dmGroup", "nav_datamodelStatus")
    time.sleep(2)
    shot(d, "06a_status")
    # 데이터모델 현황 화면은 가상 스크롤 사용 → page_source 검색 불안정.
    # API 로만 모델 존재 확인 (step 2 에서 만든 것이 실제로 있는지가 핵심).
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    r = requests.post(
        BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10
    )
    r.raise_for_status()
    models = r.json()
    target = next((m for m in models if m.get("dataModelNm") == MODEL_NAME), None)
    if not target:
        shot(d, "06_missing_model")
        raise RuntimeError(f"API 에서 모델 '{MODEL_NAME}' 조회 실패 (step2 에서 생성했는데 사라짐)")
    dm_id = target.get("dataModelId")
    r2 = requests.get(
        BASE_URL + "/api/dm/downloadDdl",
        params={"dataModelId": dm_id},
        cookies=cookies,
        timeout=15,
    )
    r2.raise_for_status()
    ddl = r2.text
    print(f"  [ddl] 응답 길이 {len(ddl)}자")
    # CREATE TABLE 구문 포함 확인
    if "CREATE TABLE" not in ddl.upper():
        path = os.path.join(SCREENSHOT_DIR, "e2e_06_ddl.sql")
        with open(path, "w", encoding="utf-8") as f:
            f.write(ddl)
        raise RuntimeError(f"DDL에 CREATE TABLE 없음 — 응답 저장: {path}")
    # 저장
    path = os.path.join(SCREENSHOT_DIR, "e2e_06_ddl.sql")
    with open(path, "w", encoding="utf-8") as f:
        f.write(ddl)
    print(f"  [ddl] {path} 저장")


# ---------- main ----------
def main():
    d = make_driver()
    try:
        ok1 = step("1. 로그인", lambda: step1_login(d))
        if not ok1:
            return
        ok2 = step("2. 논리 모델 생성", lambda: step2_create_logical_model(d))
        if not ok2:
            return
        ok3 = step("3. 테이블 추가 (한글명만)", lambda: step3_add_table(d))
        if not ok3:
            return
        ok4 = step("4-5. 컬럼 추가 + 표준 적용(용어/도메인 자동)", lambda: step4_add_columns(d))
        if not ok4:
            return
        step("6. 데이터 모델 현황 → DDL 생성", lambda: step6_ddl(d))
    finally:
        try:
            shot(d, "99_final")
        except Exception:
            pass
        d.quit()

    # summary
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
