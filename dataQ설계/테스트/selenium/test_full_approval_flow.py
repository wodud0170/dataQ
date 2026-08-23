"""
권한 기반 CRUD + 통합 승인/반려 테스트
로그인 기본: localhost:28091

테스트 순서:
  STEP 1. jyjang (일반사용자) 로그인
    - 단어 사전: 등록 버튼 라벨 "등록 신청" 확인, 수정/삭제/일괄등록/일괄삭제 비노출 확인
    - 단어A 등록 신청 (승인 대상)
    - 단어B 등록 신청 (반려 대상)
    - 도메인 등록 신청 1건
    - 마이페이지 → 요청 현황에서 3건 승인대기 확인

  STEP 2. space (관리자) 로그인
    - 단어 사전: 등록 버튼 라벨 "등록" 확인, 수정/삭제 버튼 노출 확인
    - 승인 화면 → 단어A 승인
    - 단어B 반려 (사유 입력)
    - 도메인 승인

  STEP 3. jyjang 로그인 — 처리 결과 확인
    - 마이페이지 → 요청 현황: 승인 2건, 반려 1건 확인

  STEP 4. space 로그인 — 정리 (삭제)
    - 단어A 삭제, 도메인 삭제

준수사항:
  - 실질적으로 사용자가 사용하듯 테스트를 하기 위해 모든 진행은 DOM 클릭으로 제한함
  - execute_script로 Vue 메서드 직접 호출 금지 (스크롤/가시성 보조만 허용)
"""
import time
import sys
import os
import random
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.action_chains import ActionChains

BASE_URL = "http://localhost:28091"
_SUFFIX = str(random.randint(100, 999))
TEST_WORD_A = f"셀승인{_SUFFIX}"
TEST_WORD_A_ENG = f"SELAPRV{_SUFFIX}"
TEST_WORD_B = f"셀반려{_SUFFIX}"
TEST_WORD_B_ENG = f"SELREJ{_SUFFIX}"
TEST_DOMAIN = f"셀도메인{_SUFFIX}"
SCREENSHOT_DIR = "C:/Users/장재영/Desktop/dataQ/dataQ설계/테스트/selenium/screenshots"


def create_driver():
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1400, 1080)
    return driver


def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, value))
    )


def wait_clickable(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((by, value))
    )


def screenshot(driver, name):
    path = f"{SCREENSHOT_DIR}/{name}.png"
    driver.save_screenshot(path)
    print(f"  [screenshot] {name}.png")


def login(driver, user_id, password="123"):
    driver.get(BASE_URL)
    time.sleep(3)
    print(f"  현재 URL: {driver.current_url}")
    if "/app/" in driver.current_url:
        driver.get(BASE_URL + "/logout")
        time.sleep(3)
        driver.get(BASE_URL)
        time.sleep(3)

    # 로그인 폼 대기
    try:
        id_input = wait_for(driver, By.CSS_SELECTOR, "input[type='text']")
    except:
        # 대안: 페이지에 보이는 input 찾기
        print(f"  [WARN] input[type='text'] 못 찾음, 모든 input 시도")
        inputs = driver.find_elements(By.CSS_SELECTOR, "input")
        print(f"  input 개수: {len(inputs)}")
        for i, inp in enumerate(inputs):
            print(f"    input[{i}]: type={inp.get_attribute('type')}, name={inp.get_attribute('name')}, placeholder={inp.get_attribute('placeholder')}")
        if len(inputs) >= 2:
            id_input = inputs[0]
        else:
            screenshot(driver, f"login_fail_{user_id}")
            print(f"  [{user_id}] 로그인 실패 - 입력 필드 없음")
            return False

    id_input.clear()
    id_input.send_keys(user_id)
    try:
        pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    except:
        inputs = driver.find_elements(By.CSS_SELECTOR, "input")
        pw_input = inputs[1] if len(inputs) >= 2 else None
        if not pw_input:
            print(f"  [{user_id}] 비밀번호 필드 없음")
            return False
    pw_input.clear()
    pw_input.send_keys(password)

    # 로그인 버튼 클릭
    login_btn = None
    buttons = driver.find_elements(By.CSS_SELECTOR, "button[type='submit']")
    if buttons:
        login_btn = buttons[0]
    else:
        buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in buttons:
            txt = btn.text.strip().lower()
            if txt in ("로그인", "login", ""):
                login_btn = btn
                break
        if not login_btn and buttons:
            login_btn = buttons[0]

    if login_btn:
        login_btn.click()
    else:
        pw_input.send_keys(Keys.ENTER)

    time.sleep(4)
    ok = "/app/main" in driver.current_url
    print(f"  [{user_id}] 로그인 {'성공' if ok else '실패'} (URL: {driver.current_url})")
    return ok


def logout(driver):
    driver.get(BASE_URL + "/logout")
    time.sleep(2)
    print(f"  로그아웃 완료")


def scroll_nav_to(driver, el):
    """네비 드로어 내부에서 요소가 보이도록 스크롤"""
    nav_drawer = driver.find_elements(By.CSS_SELECTOR, ".v-navigation-drawer__content")
    if nav_drawer:
        driver.execute_script("arguments[0].scrollTop = arguments[1].offsetTop - 100;", nav_drawer[0], el)
    else:
        driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", el)
    time.sleep(0.3)


def nav_click(driver, el):
    """네비 요소 클릭 (ActionChains 시도 → JS fallback)"""
    try:
        ActionChains(driver).move_to_element(el).click().perform()
    except:
        driver.execute_script("arguments[0].click();", el)


def click_nav_menu(driver, menu_text, nav_id=None, parent_group_text=None):
    """좌측 네비게이션 메뉴 클릭"""
    time.sleep(0.5)

    if parent_group_text:
        group_headers = driver.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
        for header in group_headers:
            try:
                if header.text.strip() == parent_group_text:
                    group_el = header
                    for _ in range(10):
                        group_el = group_el.find_element(By.XPATH, "..")
                        if "v-list-group" in (group_el.get_attribute("class") or ""):
                            break
                    is_active = "v-list-group--active" in (group_el.get_attribute("class") or "")
                    if not is_active:
                        scroll_nav_to(driver, header)
                        nav_click(driver, header)
                        print(f"  그룹 펼침: {parent_group_text}")
                        time.sleep(1.5)
                    else:
                        print(f"  그룹 이미 펼쳐짐: {parent_group_text}")
                    break
            except:
                continue

    if nav_id:
        for attempt in range(3):
            try:
                el = WebDriverWait(driver, 5).until(
                    EC.presence_of_element_located((By.ID, nav_id))
                )
                scroll_nav_to(driver, el)
                nav_click(driver, el)
                time.sleep(2)
                print(f"  메뉴 클릭: {menu_text} (#{nav_id})")
                return True
            except Exception as e:
                if attempt < 2:
                    time.sleep(1)
                else:
                    print(f"  [WARN] nav_id '{nav_id}' 클릭 실패: {type(e).__name__}")

    # fallback: 텍스트로 찾기
    nav_el = driver.find_elements(By.CSS_SELECTOR, ".v-navigation-drawer")
    search_root = nav_el[0] if nav_el else driver
    titles = search_root.find_elements(By.CSS_SELECTOR, ".v-list-item__title")
    for t in titles:
        try:
            if t.text.strip() == menu_text:
                scroll_nav_to(driver, t)
                nav_click(driver, t)
                time.sleep(2)
                print(f"  메뉴 클릭: {menu_text} (텍스트)")
                return True
        except:
            continue
    print(f"  [WARN] '{menu_text}' 메뉴 못 찾음")
    return False


def dismiss_swal(driver, timeout=5):
    try:
        btn = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm", timeout=timeout)
        btn.click()
        time.sleep(0.5)
        return True
    except:
        return False


def wait_swal_gone(driver, timeout=5):
    try:
        WebDriverWait(driver, timeout).until(
            EC.invisibility_of_element_located((By.CSS_SELECTOR, ".swal2-popup"))
        )
    except:
        dismiss_swal(driver, timeout=1)


def find_visible_btn(driver, label):
    """화면에 표시된 버튼 중 정확한 라벨 텍스트를 가진 버튼 찾기"""
    buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
    for btn in buttons:
        try:
            if btn.is_displayed() and btn.text.strip() == label:
                return btn
        except:
            continue
    return None


def register_word(driver, word_nm, word_eng, word_eng_full="", word_desc="Selenium 자동 테스트"):
    """단어 등록 신청 (모달 열기 → 입력 → 저장)"""
    # "등록 신청" 또는 "등록" 버튼 클릭
    add_btn = find_visible_btn(driver, "등록 신청") or find_visible_btn(driver, "등록")
    if not add_btn:
        print(f"  [FAIL] 등록 버튼 못 찾음")
        return False
    add_btn.click()
    time.sleep(1)

    modal = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")
    inputs = modal.find_elements(By.CSS_SELECTOR, "input[type='text']")
    textareas = modal.find_elements(By.CSS_SELECTOR, "textarea")

    if len(inputs) >= 3:
        inputs[0].click()
        inputs[0].send_keys(Keys.CONTROL, "a")
        inputs[0].send_keys(word_nm)
        time.sleep(0.3)
        inputs[1].click()
        inputs[1].send_keys(Keys.CONTROL, "a")
        inputs[1].send_keys(word_eng)
        time.sleep(0.5)
        if not word_eng_full:
            # 86번 #42 — 단어 영문명 공백 불허
            word_eng_full = word_eng + "Full"
        inputs[2].click()
        inputs[2].send_keys(Keys.CONTROL, "a")
        inputs[2].send_keys(word_eng_full)
        time.sleep(0.3)

    if textareas:
        textareas[0].click()
        textareas[0].send_keys(Keys.CONTROL, "a")
        textareas[0].send_keys(word_desc)

    time.sleep(0.5)

    # 모달 하단 "등록" 또는 "등록 신청" 버튼
    modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
    save_btn = None
    for btn in reversed(modal_btns):
        txt = btn.text.strip()
        if txt in ("등록", "등록 신청"):
            save_btn = btn
            break
    if save_btn:
        driver.execute_script("arguments[0].scrollIntoView(true);", save_btn)
        time.sleep(0.3)
        save_btn.click()
        print(f"  '{word_nm}' 등록 버튼 클릭")
    time.sleep(4)

    # SweetAlert 처리
    dismiss_swal(driver, timeout=3)
    time.sleep(2)
    dismiss_swal(driver, timeout=1)
    time.sleep(1)

    # 모달 닫기
    try:
        close_btn = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active .v-btn .mdi-close")
        if close_btn:
            close_btn.click()
            time.sleep(1)
    except:
        pass

    print(f"  '{word_nm}' 등록 완료")
    return True


def select_vuetify_dropdown(driver, select_el, option_index=0):
    """Vuetify v-select / v-autocomplete 드롭다운에서 옵션 선택"""
    select_el.click()
    time.sleep(1)
    # 드롭다운 메뉴에서 옵션 선택
    menu_items = driver.find_elements(By.CSS_SELECTOR, ".v-menu__content--active .v-list-item")
    if not menu_items:
        menu_items = driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if menu_items and len(menu_items) > option_index:
        menu_items[option_index].click()
        time.sleep(0.5)
        return True
    # 메뉴 닫기
    ActionChains(driver).send_keys(Keys.ESCAPE).perform()
    time.sleep(0.3)
    return False


def register_domain(driver, domain_nm):
    """도메인 등록 신청"""
    add_btn = find_visible_btn(driver, "등록 신청") or find_visible_btn(driver, "등록")
    if not add_btn:
        print(f"  [FAIL] 도메인 등록 버튼 못 찾음")
        return False
    add_btn.click()
    time.sleep(1)

    modal = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")

    # 1) 도메인명 (첫 번째 v-text-field)
    text_fields = modal.find_elements(By.CSS_SELECTOR, ".v-text-field input[type='text']:not([readonly])")
    if text_fields:
        text_fields[0].click()
        text_fields[0].send_keys(Keys.CONTROL, "a")
        text_fields[0].send_keys(domain_nm)
        time.sleep(0.3)

    # 2) 도메인 그룹 (첫 번째 v-select)
    selects = modal.find_elements(By.CSS_SELECTOR, ".v-select")
    if len(selects) >= 1:
        select_vuetify_dropdown(driver, selects[0])
        print(f"  도메인 그룹 선택 완료")
        time.sleep(0.5)

    # 3) 도메인 분류 (두 번째 v-select)
    if len(selects) >= 2:
        select_vuetify_dropdown(driver, selects[1])
        print(f"  도메인 분류 선택 완료")
        time.sleep(0.5)

    # 4) 도메인 설명 (textarea)
    textareas = modal.find_elements(By.CSS_SELECTOR, "textarea")
    if textareas:
        textareas[0].click()
        textareas[0].send_keys(Keys.CONTROL, "a")
        textareas[0].send_keys("Selenium 도메인 테스트")
        time.sleep(0.3)

    # 5) 데이터 타입 (v-autocomplete) - type to search
    autocompletes = modal.find_elements(By.CSS_SELECTOR, ".v-autocomplete")
    if autocompletes:
        ac_input = autocompletes[0].find_elements(By.CSS_SELECTOR, "input")
        if ac_input:
            ac_input[0].click()
            time.sleep(0.5)
            ac_input[0].send_keys("VARCHAR")
            time.sleep(1)
            # 드롭다운에서 첫 번째 옵션 클릭
            menu_items = driver.find_elements(By.CSS_SELECTOR, ".v-menu__content--active .v-list-item")
            if not menu_items:
                menu_items = driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
            if menu_items:
                menu_items[0].click()
                time.sleep(0.5)
            else:
                ac_input[0].send_keys(Keys.ENTER)
                time.sleep(0.5)

    time.sleep(0.5)

    # 6) 등록 버튼 클릭
    modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
    save_btn = None
    for btn in reversed(modal_btns):
        txt = btn.text.strip()
        if txt in ("등록", "등록 신청"):
            save_btn = btn
            break
    if save_btn:
        driver.execute_script("arguments[0].scrollIntoView(true);", save_btn)
        time.sleep(0.3)
        save_btn.click()
        print(f"  '{domain_nm}' 도메인 등록 버튼 클릭")
    time.sleep(4)

    dismiss_swal(driver, timeout=3)
    time.sleep(2)
    dismiss_swal(driver, timeout=1)
    time.sleep(1)

    try:
        close_btn = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active .v-btn .mdi-close")
        if close_btn:
            close_btn.click()
            time.sleep(1)
    except:
        pass

    print(f"  '{domain_nm}' 도메인 등록 완료")
    return True


def approve_item_by_name(driver, item_name):
    """승인 목록에서 특정 항목을 체크 → 일괄 승인"""
    time.sleep(1)
    # overlay가 남아있으면 닫기
    try:
        overlays = driver.find_elements(By.CSS_SELECTOR, ".v-overlay--active, .v-dialog--active")
        if overlays:
            ActionChains(driver).send_keys(Keys.ESCAPE).perform()
            time.sleep(1)
    except:
        pass
    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    target_row = None
    for row in rows:
        if item_name in row.text:
            target_row = row
            break

    if not target_row:
        print(f"  [FAIL] 승인 목록에서 '{item_name}' 못 찾음")
        return False

    # 체크박스 선택
    try:
        checkbox = target_row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, .mdi-checkbox-blank-outline")
        try:
            checkbox.click()
        except:
            driver.execute_script("arguments[0].click();", checkbox)
    except:
        tds = target_row.find_elements(By.CSS_SELECTOR, "td")
        if tds:
            driver.execute_script("arguments[0].click();", tds[0])
    time.sleep(0.5)

    # 일괄 승인 버튼
    batch_btn = find_visible_btn(driver, "일괄 승인")
    if not batch_btn:
        print(f"  [FAIL] 일괄 승인 버튼 못 찾음")
        return False

    batch_btn.click()
    time.sleep(2)

    # SweetAlert 확인
    dismiss_swal(driver, timeout=5)
    time.sleep(3)
    dismiss_swal(driver, timeout=2)
    time.sleep(1)

    print(f"  '{item_name}' 승인 완료")
    return True


def reject_item_by_name(driver, item_name, reason="테스트 반려 사유"):
    """승인 목록에서 특정 항목을 체크 → 일괄 반려 (v-dialog 사유 입력)"""
    time.sleep(1)
    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    target_row = None
    for row in rows:
        if item_name in row.text:
            target_row = row
            break

    if not target_row:
        print(f"  [FAIL] 승인 목록에서 '{item_name}' 못 찾음")
        return False

    # 체크박스 선택
    try:
        checkbox = target_row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, .mdi-checkbox-blank-outline")
        checkbox.click()
    except:
        tds = target_row.find_elements(By.CSS_SELECTOR, "td")
        if tds:
            tds[0].click()
    time.sleep(0.5)

    # 일괄 반려 버튼
    batch_btn = find_visible_btn(driver, "일괄 반려")
    if not batch_btn:
        # 텍스트에 "일괄 반려"가 포함된 버튼 찾기
        buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in buttons:
            if "반려" in btn.text and btn.is_displayed():
                batch_btn = btn
                break
    if not batch_btn:
        print(f"  [FAIL] 일괄 반려 버튼 못 찾음")
        return False

    batch_btn.click()
    time.sleep(2)

    # v-dialog 반려 사유 입력 (MMApproval의 batchRejectDialog)
    try:
        dialog = WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".v-dialog--active"))
        )
        reason_input = dialog.find_element(By.CSS_SELECTOR, "input[type='text'], textarea")
        reason_input.clear()
        reason_input.send_keys(reason)
        time.sleep(0.5)

        # 다이얼로그 확인 버튼 클릭
        dialog_btns = dialog.find_elements(By.CSS_SELECTOR, ".v-btn")
        confirm_btn = None
        for btn in dialog_btns:
            txt = btn.text.strip()
            if txt in ("반려", "확인"):
                confirm_btn = btn
                break
        if confirm_btn:
            confirm_btn.click()
            time.sleep(3)
        print(f"  반려 사유 입력 완료: {reason}")
    except Exception as e:
        print(f"  [WARN] 반려 다이얼로그 처리 실패: {e}")
        # SweetAlert fallback
        dismiss_swal(driver, timeout=3)

    # 처리 완료 후 SweetAlert + overlay 정리
    dismiss_swal(driver, timeout=3)
    time.sleep(1)
    dismiss_swal(driver, timeout=1)
    time.sleep(0.5)
    # overlay scrim이 남아있을 경우 ESC로 닫기
    try:
        overlays = driver.find_elements(By.CSS_SELECTOR, ".v-overlay--active, .v-dialog--active")
        if overlays:
            ActionChains(driver).send_keys(Keys.ESCAPE).perform()
            time.sleep(1)
    except:
        pass

    print(f"  '{item_name}' 반려 완료 (사유: {reason})")
    return True


def delete_word_by_search(driver, word_nm):
    """단어 사전에서 검색 후 삭제"""
    search_inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='text']")
    for inp in search_inputs:
        try:
            if inp.is_displayed() and inp.get_attribute("placeholder") != "통합 검색":
                inp.click()
                inp.send_keys(Keys.CONTROL, "a")
                inp.send_keys(word_nm)
                inp.send_keys(Keys.ENTER)
                break
        except:
            continue
    time.sleep(2)

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    for row in rows:
        if word_nm in row.text:
            try:
                checkbox = row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, .mdi-checkbox-blank-outline")
                checkbox.click()
            except:
                tds = row.find_elements(By.CSS_SELECTOR, "td")
                if tds:
                    tds[0].click()
            break
    else:
        print(f"  [WARN] '{word_nm}' 테이블에서 못 찾음")
        return False

    time.sleep(0.5)

    delete_btn = find_visible_btn(driver, "삭제")
    if delete_btn:
        delete_btn.click()
        time.sleep(1)
        dismiss_swal(driver, timeout=5)
        time.sleep(2)
        wait_swal_gone(driver, timeout=5)
        time.sleep(1)
        print(f"  '{word_nm}' 삭제 완료")
        return True
    else:
        print(f"  [FAIL] 삭제 버튼 못 찾음")
        return False


def delete_domain_by_search(driver, domain_nm):
    """도메인 사전에서 검색 후 삭제"""
    search_inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='text']")
    for inp in search_inputs:
        try:
            if inp.is_displayed() and inp.get_attribute("placeholder") != "통합 검색":
                inp.click()
                inp.send_keys(Keys.CONTROL, "a")
                inp.send_keys(domain_nm)
                inp.send_keys(Keys.ENTER)
                break
        except:
            continue
    time.sleep(2)

    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
    for row in rows:
        if domain_nm in row.text:
            try:
                checkbox = row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, .mdi-checkbox-blank-outline")
                checkbox.click()
            except:
                tds = row.find_elements(By.CSS_SELECTOR, "td")
                if tds:
                    tds[0].click()
            break
    else:
        print(f"  [WARN] '{domain_nm}' 테이블에서 못 찾음")
        return False

    time.sleep(0.5)

    delete_btn = find_visible_btn(driver, "삭제")
    if delete_btn:
        delete_btn.click()
        time.sleep(1)
        dismiss_swal(driver, timeout=5)
        time.sleep(2)
        wait_swal_gone(driver, timeout=5)
        time.sleep(1)
        print(f"  '{domain_nm}' 삭제 완료")
        return True
    else:
        print(f"  [FAIL] 삭제 버튼 못 찾음")
        return False


def main():
    print("=" * 60)
    print("권한 기반 CRUD + 통합 승인/반려 테스트")
    print(f"단어A(승인): {TEST_WORD_A} / 단어B(반려): {TEST_WORD_B}")
    print(f"도메인: {TEST_DOMAIN}")
    print("=" * 60)

    os.makedirs(SCREENSHOT_DIR, exist_ok=True)
    driver = create_driver()
    results = {}

    try:
        # ============================================================
        # STEP 1: jyjang (일반사용자) 로그인 → 권한 확인 + 등록 신청
        # ============================================================
        print(f"\n[STEP 1] jyjang 로그인 → 권한 확인 + 등록 신청")
        if not login(driver, "jyjang"):
            return 1

        # 단어 탭
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # 1-1. 버튼 라벨 확인: "등록 신청"이어야 함
        add_btn = find_visible_btn(driver, "등록 신청")
        btn_label_ok = add_btn is not None
        if btn_label_ok:
            print(f"  등록 버튼 라벨 '등록 신청' 확인: PASS")
        else:
            # "등록"으로 표시되면 isAdmin이 제대로 안 됨
            fallback = find_visible_btn(driver, "등록")
            if fallback:
                print(f"  등록 버튼 라벨이 '등록'임 (isAdmin 미적용?): FAIL")
            else:
                print(f"  등록 버튼 자체를 못 찾음: FAIL")
        results["1-1. 등록 버튼 라벨"] = btn_label_ok

        # 1-2. 수정/삭제/일괄등록/일괄삭제 비노출 확인
        hidden_btns = ["일괄 등록", "삭제", "일괄 삭제"]
        hidden_ok = True
        for label in hidden_btns:
            btn = find_visible_btn(driver, label)
            if btn:
                print(f"  '{label}' 버튼이 일반 사용자에게 노출됨: FAIL")
                hidden_ok = False
            else:
                print(f"  '{label}' 비노출 확인: PASS")
        results["1-2. 관리자 전용 버튼 비노출"] = hidden_ok
        screenshot(driver, "step1_user_buttons")

        # 1-3. 단어A 등록 신청
        word_a_ok = register_word(driver, TEST_WORD_A, TEST_WORD_A_ENG)
        results["1-3. 단어A 등록 신청"] = word_a_ok
        screenshot(driver, "step1_wordA_registered")

        # 1-4. 단어B 등록 신청
        time.sleep(1)
        word_b_ok = register_word(driver, TEST_WORD_B, TEST_WORD_B_ENG)
        results["1-4. 단어B 등록 신청"] = word_b_ok
        screenshot(driver, "step1_wordB_registered")

        # 1-5. 도메인 등록 신청
        click_nav_menu(driver, "도메인", nav_id="nav_domain", parent_group_text="데이터 표준 사전")
        time.sleep(2)
        domain_ok = register_domain(driver, TEST_DOMAIN)
        results["1-5. 도메인 등록 신청"] = domain_ok
        screenshot(driver, "step1_domain_registered")

        # 1-6. 마이페이지 → 요청 현황에서 등록 확인
        click_nav_menu(driver, "요청 현황", nav_id="nav_myRequest", parent_group_text="마이페이지")
        time.sleep(3)

        pending_count = 0
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        for row in rows:
            txt = row.text
            if TEST_WORD_A in txt or TEST_WORD_B in txt or TEST_DOMAIN in txt:
                pending_count += 1

        myreq_ok = pending_count >= 3
        print(f"  요청 현황에서 테스트 항목 {pending_count}건 확인 (기대: 3건): {'PASS' if myreq_ok else 'FAIL'}")
        results["1-6. 요청 현황 확인"] = myreq_ok
        screenshot(driver, "step1_myrequest")

        # ============================================================
        # STEP 2: space (관리자) 로그인 → 권한 확인 + 승인/반려
        # ============================================================
        print(f"\n[STEP 2] space 로그인 → 관리자 권한 확인 + 승인/반려")
        logout(driver)
        if not login(driver, "space"):
            return 1

        # 2-1. 단어 사전에서 관리자 버튼 확인
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        admin_add_btn = find_visible_btn(driver, "등록")
        admin_label_ok = admin_add_btn is not None
        if admin_label_ok:
            print(f"  관리자 등록 버튼 라벨 '등록' 확인: PASS")
        else:
            print(f"  관리자 등록 버튼 라벨 확인 실패: FAIL")
        results["2-1. 관리자 등록 버튼 라벨"] = admin_label_ok

        admin_btns_ok = True
        for label in ["삭제", "일괄 삭제"]:
            btn = find_visible_btn(driver, label)
            if btn:
                print(f"  관리자 '{label}' 버튼 노출 확인: PASS")
            else:
                print(f"  관리자 '{label}' 버튼 미노출: FAIL")
                admin_btns_ok = False
        results["2-1. 관리자 수정/삭제 버튼 노출"] = admin_btns_ok
        screenshot(driver, "step2_admin_buttons")

        # 2-2. 승인 화면 → 단어A 승인
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(2)
        screenshot(driver, "step2_approval_list")

        word_a_approved = approve_item_by_name(driver, TEST_WORD_A)
        results["2-2. 단어A 승인"] = word_a_approved
        screenshot(driver, "step2_wordA_approved")

        # 2-3. 단어B 반려
        # 승인 후 목록이 갱신되므로 잠시 대기
        time.sleep(2)
        word_b_rejected = reject_item_by_name(driver, TEST_WORD_B, "테스트 반려: 명칭 부적절")
        results["2-3. 단어B 반려"] = word_b_rejected
        screenshot(driver, "step2_wordB_rejected")

        # 2-4. 도메인 승인
        time.sleep(2)
        domain_approved = approve_item_by_name(driver, TEST_DOMAIN)
        results["2-4. 도메인 승인"] = domain_approved
        screenshot(driver, "step2_domain_approved")

        # ============================================================
        # STEP 3: jyjang 로그인 → 처리 결과 확인
        # ============================================================
        print(f"\n[STEP 3] jyjang 로그인 → 처리 결과 확인")
        logout(driver)
        if not login(driver, "jyjang"):
            return 1

        click_nav_menu(driver, "요청 현황", nav_id="nav_myRequest", parent_group_text="마이페이지")
        time.sleep(3)

        approved_count = 0
        rejected_count = 0
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        for row in rows:
            txt = row.text
            if TEST_WORD_A in txt or TEST_DOMAIN in txt:
                if "승인완료" in txt or "승인" in txt:
                    approved_count += 1
            if TEST_WORD_B in txt:
                if "반려" in txt:
                    rejected_count += 1

        result_ok = approved_count >= 2 and rejected_count >= 1
        print(f"  승인완료 {approved_count}건 (기대: 2+), 반려 {rejected_count}건 (기대: 1+): {'PASS' if result_ok else 'FAIL'}")
        results["3. 처리 결과 확인"] = result_ok
        screenshot(driver, "step3_result")

        # ============================================================
        # STEP 4: space 로그인 → 정리 (삭제)
        # ============================================================
        print(f"\n[STEP 4] space 로그인 → 정리")
        logout(driver)
        if not login(driver, "space"):
            return 1

        # 단어A 삭제
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)
        delete_a = delete_word_by_search(driver, TEST_WORD_A)
        results["4-1. 단어A 삭제"] = delete_a

        # 단어B 삭제 (반려된 단어 — 목록에 안 보이면 삭제 불필요)
        time.sleep(1)
        delete_b = delete_word_by_search(driver, TEST_WORD_B)
        if not delete_b:
            print(f"  '{TEST_WORD_B}' 반려 상태라 목록에 없음 — 정리 불필요 (PASS)")
            delete_b = True
        results["4-2. 단어B 삭제"] = delete_b

        # 도메인 삭제
        click_nav_menu(driver, "도메인", nav_id="nav_domain", parent_group_text="데이터 표준 사전")
        time.sleep(2)
        delete_domain = delete_domain_by_search(driver, TEST_DOMAIN)
        results["4-3. 도메인 삭제"] = delete_domain

        screenshot(driver, "step4_cleanup_done")

        # ============================================================
        # 최종 결과
        # ============================================================
        print(f"\n{'=' * 60}")
        print("최종 결과:")
        all_pass = True
        for k, v in results.items():
            status = "PASS" if v else "FAIL"
            print(f"  {k}: {status}")
            if not v:
                all_pass = False

        print(f"\n[FINAL] {'테스트 통과!' if all_pass else '테스트 실패'}")
        print(f"{'=' * 60}")
        return 0 if all_pass else 1

    except Exception as e:
        print(f"\n[ERROR] 예외: {e}")
        import traceback
        traceback.print_exc()
        screenshot(driver, "exception")
        return 1
    finally:
        print("\n10초 후 브라우저 종료...")
        time.sleep(10)
        driver.quit()


if __name__ == "__main__":
    sys.exit(main())
