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


def nav(d, group_id, menu_id):
    """네비 그룹 펼치고 메뉴 클릭 — 실제 click 이벤트 + visibility 대기"""
    dismiss_swal(d)
    # 메뉴가 이미 visible이면 그룹 펼칠 필요 없음
    menu_items = d.find_elements(By.ID, menu_id)
    need_expand = not menu_items or not menu_items[0].is_displayed()
    if need_expand:
        g = wait_clickable(d, By.ID, group_id, 10)
        g.click()
        try:
            wait_visible(d, By.ID, menu_id, 5)
        except TimeoutException:
            # 토글이 닫힌 상태였다면 한 번 더 시도
            g.click()
            wait_visible(d, By.ID, menu_id, 5)
    m = wait_clickable(d, By.ID, menu_id, 10)
    m.click()
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


def add_column(d, term_kr):
    """컬럼 추가 다이얼로그 열고 한글명 입력→표준 적용→추가.
       표준 매칭 성공 시 True, 미매칭이면 False 리턴."""
    close_any_dialog(d)
    if not click_button_by_text(d, "컬럼 추가"):
        raise RuntimeError("'컬럼 추가' 버튼 클릭 실패")
    wait_visible(d, By.CSS_SELECTOR, ".v-dialog--active")
    time.sleep(0.8)
    # 소속 테이블 선택 (첫 autocomplete)
    dialog_acs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active .v-autocomplete input[type='text']")
    if not dialog_acs:
        raise RuntimeError("다이얼로그 내 autocomplete input 없음")
    obj_ac = dialog_acs[0]
    obj_ac.click()
    time.sleep(0.5)
    items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if items:
        items[0].click()
        time.sleep(0.3)
    # 한글명 입력 (label에 "컬럼 한글명" 포함)
    inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
    kr_input = None
    for inp in inputs:
        try:
            parent = inp.find_element(By.XPATH, "./ancestor::div[contains(@class,'v-text-field')][1]")
            lab = parent.find_element(By.CSS_SELECTOR, "label").text
            if "컬럼 한글명" in lab:
                kr_input = inp
                break
        except Exception:
            continue
    if not kr_input:
        raise RuntimeError("'컬럼 한글명' 필드 없음")
    kr_input.clear()
    kr_input.send_keys(term_kr)
    time.sleep(0.3)
    # [표준 적용] 버튼
    if not click_button_by_text(d, "표준 적용", ".v-dialog--active"):
        raise RuntimeError("'표준 적용' 버튼 클릭 실패")
    # swal: '표준 적용 완료' (성공) 또는 '표준 용어 없음' (실패)
    try:
        WebDriverWait(d, 5).until(
            EC.visibility_of_element_located((By.CSS_SELECTOR, ".swal2-title"))
        )
    except TimeoutException:
        pass
    title_els = d.find_elements(By.CSS_SELECTOR, ".swal2-title")
    title = title_els[0].text if title_els else ""
    shot(d, f"04_apply_{term_kr}")
    matched = "완료" in title
    dismiss_swal(d)
    if not matched:
        print(f"  [skip] '{term_kr}' 표준 매칭 실패 → 컬럼 추가 스킵")
        close_any_dialog(d)
        return False
    # 추가 버튼
    if not click_button_by_text(d, "추가", ".v-dialog--active"):
        raise RuntimeError("컬럼 '추가' 버튼 클릭 실패")
    # swal 닫고 다이얼로그 닫힘 대기
    time.sleep(0.5)
    dismiss_swal(d)
    try:
        WebDriverWait(d, 8).until(
            lambda drv: not drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
            or not drv.find_element(By.CSS_SELECTOR, ".v-dialog--active").is_displayed()
        )
    except TimeoutException:
        close_any_dialog(d)
    time.sleep(0.5)
    return True


def step4_add_columns(d):
    nav(d, "dmGroup", "nav_datamodelStatusColumn")
    select_autocomplete(d, "데이터모델명", MODEL_NAME)
    time.sleep(1)
    added = 0
    for term in COLUMN_TRIES:
        try:
            if add_column(d, term):
                added += 1
        except Exception as e:
            print(f"  [warn] '{term}' 추가 중 예외: {e}")
            close_any_dialog(d)
        if added >= 2:
            break
    shot(d, "04_columns_added")
    if added == 0:
        raise RuntimeError(
            f"용어사전 매칭 0건 — 후보({COLUMN_TRIES}) 모두 표준 적용 실패. TB_TERMS 확인 필요"
        )
    print(f"  [info] 컬럼 {added}건 추가 성공")


def step6_ddl(d):
    nav(d, "dmGroup", "nav_datamodelStatus")
    time.sleep(2)
    # 그리드에서 MODEL_NAME row 찾기
    page = d.page_source
    if MODEL_NAME not in page:
        shot(d, "06_missing_model")
        raise RuntimeError(f"모델 현황에 '{MODEL_NAME}' 없음")
    shot(d, "06a_status")
    # DDL 다운로드 API 직접 호출로 응답 검증 (UI 클릭은 세션 쿠키 공유 필요)
    # 세션 쿠키를 가져와서 requests로 호출
    cookies = {c["name"]: c["value"] for c in d.get_cookies()}
    # dataModelId 얻기 위해 모델 목록 API 사용
    r = requests.post(
        BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10
    )
    r.raise_for_status()
    models = r.json()
    target = next((m for m in models if m.get("dataModelNm") == MODEL_NAME), None)
    if not target:
        raise RuntimeError(f"API에서 모델 '{MODEL_NAME}' 조회 실패")
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
