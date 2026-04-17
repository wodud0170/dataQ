"""
단어-용어 연쇄 반려 + 단어 선승인 테스트
로그인: jyjang(일반), space(관리자)

시나리오:
  STEP 1. jyjang: 일반단어 2건 등록 신청 (단어A, 단어B)
  STEP 2. jyjang: 용어 2건 등록 신청 (단어A+명 → 용어A, 단어B+명 → 용어B)
  STEP 3. space: 단어A 반려 → 용어A도 함께 cascade 삭제 확인 + 알림 메시지 확인
  STEP 4. space: 용어B 승인 시도 → "단어를 먼저 승인" alert 확인
  STEP 5. space: 단어B 승인 → 용어B 재승인 → 성공
  STEP 6. 정리: 승인된 단어B + 용어B 삭제
"""
import time
import sys
import random
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.action_chains import ActionChains

BASE_URL = "http://localhost:28091"
# API 직접 호출로 용어 등록하므로 OKT 제약 없음 → 고유 접미사 사용 가능
_SUFFIX = str(random.randint(100, 999))
WORD_A = f"셀연쇄{_SUFFIX}"
WORD_A_ENG = f"SELCAS{_SUFFIX}"
WORD_B = f"셀선승{_SUFFIX}"
WORD_B_ENG = f"SELPRE{_SUFFIX}"
TERM_A = f"{WORD_A}명"          # 단어A + 형식단어 "명"
TERM_B = f"{WORD_B}명"          # 단어B + 형식단어 "명"
SCREENSHOT_DIR = "C:/Users/장재영/Desktop/dataQ/dataQ설계/테스트/selenium/screenshots"

passed = 0
failed = 0
results = []


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


def check(name, condition):
    global passed, failed
    if condition:
        passed += 1
        results.append(f"  PASS: {name}")
        print(f"  PASS: {name}")
    else:
        failed += 1
        results.append(f"  FAIL: {name}")
        print(f"  FAIL: {name}")
    return condition


def login(driver, user_id, password="123"):
    driver.get(BASE_URL)
    time.sleep(3)
    if "/app/" in driver.current_url:
        driver.get(BASE_URL + "/logout")
        time.sleep(3)
        driver.get(BASE_URL)
        time.sleep(3)

    try:
        id_input = wait_for(driver, By.CSS_SELECTOR, "input[type='text']")
    except:
        inputs = driver.find_elements(By.CSS_SELECTOR, "input")
        if len(inputs) >= 2:
            id_input = inputs[0]
        else:
            return False

    id_input.clear()
    id_input.send_keys(user_id)
    try:
        pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    except:
        inputs = driver.find_elements(By.CSS_SELECTOR, "input")
        pw_input = inputs[1] if len(inputs) >= 2 else None
        if not pw_input:
            return False
    pw_input.clear()
    pw_input.send_keys(password)

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
    print(f"  [{user_id}] 로그인 {'성공' if ok else '실패'}")
    return ok


def logout(driver):
    driver.get(BASE_URL + "/logout")
    time.sleep(2)


def scroll_nav_to(driver, el):
    nav_drawer = driver.find_elements(By.CSS_SELECTOR, ".v-navigation-drawer__content")
    if nav_drawer:
        driver.execute_script("arguments[0].scrollTop = arguments[1].offsetTop - 100;", nav_drawer[0], el)
    else:
        driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", el)
    time.sleep(0.3)


def nav_click(driver, el):
    try:
        ActionChains(driver).move_to_element(el).click().perform()
    except:
        driver.execute_script("arguments[0].click();", el)


def click_nav_menu(driver, menu_text, nav_id=None, parent_group_text=None):
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
                        time.sleep(1.5)
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
            except:
                if attempt < 2:
                    time.sleep(1)
    # fallback
    nav_el = driver.find_elements(By.CSS_SELECTOR, ".v-navigation-drawer")
    search_root = nav_el[0] if nav_el else driver
    titles = search_root.find_elements(By.CSS_SELECTOR, ".v-list-item__title")
    for t in titles:
        try:
            if t.text.strip() == menu_text:
                scroll_nav_to(driver, t)
                nav_click(driver, t)
                time.sleep(2)
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


def get_swal_text(driver, timeout=5):
    """swal 팝업의 제목+내용 텍스트 반환"""
    try:
        WebDriverWait(driver, timeout).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".swal2-popup"))
        )
        title = ""
        content = ""
        try:
            title = driver.find_element(By.CSS_SELECTOR, ".swal2-title").text
        except:
            pass
        try:
            content = driver.find_element(By.CSS_SELECTOR, ".swal2-html-container, .swal2-content").text
        except:
            pass
        return f"{title} {content}".strip()
    except:
        return ""


def find_visible_btn(driver, label):
    buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
    for btn in buttons:
        try:
            if btn.is_displayed() and btn.text.strip() == label:
                return btn
        except:
            continue
    return None


def register_word(driver, word_nm, word_eng):
    """단어 등록 (등록 신청 or 등록)"""
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
        driver.execute_script("arguments[0].click();", inputs[0])
        inputs[0].send_keys(Keys.CONTROL, "a")
        inputs[0].send_keys(word_nm)
        time.sleep(0.3)
        driver.execute_script("arguments[0].click();", inputs[1])
        inputs[1].send_keys(Keys.CONTROL, "a")
        inputs[1].send_keys(word_eng)
        time.sleep(0.5)
        driver.execute_script("arguments[0].click();", inputs[2])
        inputs[2].send_keys(Keys.CONTROL, "a")
        inputs[2].send_keys(word_eng + " FULL")
        time.sleep(0.3)

    if textareas:
        driver.execute_script("arguments[0].click();", textareas[0])
        textareas[0].send_keys(Keys.CONTROL, "a")
        textareas[0].send_keys(f"Selenium 테스트 단어: {word_nm}")

    time.sleep(0.5)

    modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
    save_btn = None
    for btn in reversed(modal_btns):
        txt = btn.text.strip()
        if txt in ("등록 신청", "등록"):
            save_btn = btn
            break
    if not save_btn:
        for btn in reversed(modal_btns):
            if "gradient" in (btn.get_attribute("class") or ""):
                save_btn = btn
                break

    if save_btn:
        driver.execute_script("arguments[0].scrollIntoView({block:'center'});", save_btn)
        time.sleep(0.3)
        driver.execute_script("arguments[0].click();", save_btn)
        time.sleep(2)

    dismiss_swal(driver, timeout=5)
    wait_swal_gone(driver)
    return True


def get_word_id(driver, word_nm):
    """DB에서 단어 ID 조회 (브라우저 세션의 XHR 활용)"""
    driver.set_script_timeout(10)
    result = driver.execute_async_script("""
        var callback = arguments[arguments.length - 1];
        var wordNm = arguments[0];
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/api/std/getWordList?wordNm=' + encodeURIComponent(wordNm), true);
        xhr.onload = function() {
            try {
                var data = JSON.parse(xhr.responseText);
                for (var i = 0; i < data.length; i++) {
                    if (data[i].wordNm === wordNm) {
                        callback(data[i].id);
                        return;
                    }
                }
                callback(null);
            } catch(e) { callback('err:' + e.message); }
        };
        xhr.onerror = function() { callback(null); };
        xhr.send();
    """, word_nm)
    return result


def register_term_via_api(driver, term_nm, word_nm, domain_name="명V100"):
    """
    용어 등록 - createTerms API 직접 호출
    (서버에 OKT 라이브러리 미설치로 UI 3-step stepper 불가 → API 직접 호출로 우회)
    """
    import json

    # 1. 일반 단어 ID 조회
    word_id = get_word_id(driver, word_nm)
    if not word_id or str(word_id).startswith("err"):
        print(f"  [FAIL] 단어 '{word_nm}' ID 조회 실패: {word_id}")
        return False
    print(f"  [INFO] 단어 '{word_nm}' ID: {word_id}")

    # 2. 형식단어 "명" ID 조회
    myung_id = get_word_id(driver, "명")
    if not myung_id or str(myung_id).startswith("err"):
        print(f"  [FAIL] 형식단어 '명' ID 조회 실패: {myung_id}")
        return False
    print(f"  [INFO] 형식단어 '명' ID: {myung_id}")

    # 3. 단어 영문약어 조회 (용어 영문약어 구성)
    word_eng = driver.execute_async_script("""
        var callback = arguments[arguments.length - 1];
        var wordNm = arguments[0];
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/api/std/getWordList?wordNm=' + encodeURIComponent(wordNm), true);
        xhr.onload = function() {
            try {
                var data = JSON.parse(xhr.responseText);
                for (var i = 0; i < data.length; i++) {
                    if (data[i].wordNm === wordNm) {
                        callback(data[i].wordEngAbrvNm || 'UNKNOWN');
                        return;
                    }
                }
                callback('UNKNOWN');
            } catch(e) { callback('UNKNOWN'); }
        };
        xhr.onerror = function() { callback('UNKNOWN'); };
        xhr.send();
    """, word_nm)
    # 형식단어 "명"의 영문약어는 "NM"
    terms_eng_abrv = f"{word_eng}_NM"

    # 4. createTerms API 호출
    payload = json.dumps({
        "termsNm": term_nm,
        "termsEngAbrvNm": terms_eng_abrv,
        "termsDesc": f"Selenium 테스트 용어: {term_nm}",
        "domainNm": domain_name,
        "wordList": [
            {"wordId": word_id, "wordNm": word_nm, "wordOrd": 0},
            {"wordId": myung_id, "wordNm": "명", "wordOrd": 1}
        ]
    }, ensure_ascii=False)

    driver.set_script_timeout(10)
    result = driver.execute_async_script("""
        var callback = arguments[arguments.length - 1];
        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/std/createTerms', true);
        xhr.setRequestHeader('Content-Type', 'application/json;charset=UTF-8');
        xhr.onload = function() {
            callback('status=' + xhr.status + '|body=' + xhr.responseText.substring(0, 500));
        };
        xhr.onerror = function() { callback('xhr-error'); };
        xhr.send(arguments[0]);
    """, payload)

    print(f"  용어 등록 API 결과: {result}")
    if result and "resultCode\":200" in str(result):
        return True
    return False


def _unused_select_word_checkboxes(driver, modal):
    """(미사용 - OKT 미설치로 UI stepper 불가) Step2 단어 테이블에서 모든 체크박스 선택."""
    word_tables = modal.find_elements(By.CSS_SELECTOR, "#addTerm_wordList_table")
    selected_count = 0
    for table in word_tables:
        try:
            # 방법1: header "전체 선택" 체크박스 (가장 안정적)
            header_cbs = table.find_elements(By.CSS_SELECTOR, "thead th .v-simple-checkbox")
            if header_cbs:
                driver.execute_script("arguments[0].click();", header_cbs[0])
                time.sleep(0.5)
                selected_count += 1
                continue
            # 방법2: tbody 각 행 체크박스
            row_cbs = table.find_elements(By.CSS_SELECTOR, "tbody tr td .v-simple-checkbox")
            if row_cbs:
                for cb in row_cbs:
                    driver.execute_script("arguments[0].click();", cb)
                    time.sleep(0.3)
                selected_count += 1
                continue
            # 방법3: 행 자체 클릭
            rows = table.find_elements(By.CSS_SELECTOR, "tbody tr")
            if rows:
                for row in rows:
                    driver.execute_script("arguments[0].click();", row)
                    time.sleep(0.3)
                selected_count += 1
        except Exception as e:
            print(f"  [WARN] 단어 체크박스 클릭 실패: {e}")
    print(f"  [INFO] 단어 테이블 {len(word_tables)}개 발견, {selected_count}개 선택 처리")
    return selected_count > 0


def _unused_register_term(driver, term_nm, domain_name="명V100"):
    """(미사용 - OKT 미설치로 UI stepper 불가)"""
    # 등록 신청 / 등록 버튼 (JS click으로 overlay 문제 방지)
    add_btn = find_visible_btn(driver, "등록 신청") or find_visible_btn(driver, "등록")
    if not add_btn:
        print(f"  [FAIL] 용어 등록 버튼 못 찾음")
        return False
    driver.execute_script("arguments[0].click();", add_btn)
    time.sleep(1.5)

    modal = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")

    # === Step 1: 용어명 입력 ===
    step1_inputs = modal.find_elements(By.CSS_SELECTOR, "input[type='text']")
    term_input = None
    for inp in step1_inputs:
        try:
            if inp.is_displayed():
                term_input = inp
                break
        except:
            continue

    if term_input:
        driver.execute_script("arguments[0].click();", term_input)
        term_input.send_keys(Keys.CONTROL, "a")
        term_input.send_keys(term_nm)
        time.sleep(0.5)
    else:
        print(f"  [FAIL] 용어명 입력 필드 못 찾음")
        return False

    # "다음" 버튼
    next_btn = None
    modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
    for btn in modal_btns:
        try:
            tc = driver.execute_script("return arguments[0].textContent;", btn).strip()
            if tc == "다음" and btn.is_displayed():
                next_btn = btn
                break
        except:
            continue
    # 직접 API 호출 테스트 (디버깅)
    api_result = driver.execute_script("""
        return new Promise(function(resolve) {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/api/std/getTermsTokenListByNm?termsNm=' + encodeURIComponent(arguments[0]), true);
            xhr.onload = function() {
                resolve('status=' + xhr.status + '|body=' + xhr.responseText.substring(0, 500));
            };
            xhr.onerror = function() { resolve('xhr-error'); };
            xhr.send();
        });
    """, term_nm)
    # execute_async_script으로 Promise 결과 받기
    driver.set_script_timeout(10)
    api_result = driver.execute_async_script("""
        var callback = arguments[arguments.length - 1];
        var xhr = new XMLHttpRequest();
        xhr.open('GET', '/api/std/getTermsTokenListByNm?termsNm=' + encodeURIComponent(arguments[0]), true);
        xhr.onload = function() {
            callback('status=' + xhr.status + '|body=' + xhr.responseText.substring(0, 500));
        };
        xhr.onerror = function() { callback('xhr-error'); };
        xhr.send();
    """, term_nm)
    print(f"  [DEBUG] API direct call: {api_result}")

    if next_btn:
        driver.execute_script("arguments[0].click();", next_btn)
        time.sleep(3)
    else:
        print(f"  [FAIL] Step1 다음 버튼 못 찾음")
        return False

    # 중복 swal 체크
    swal_text = get_swal_text(driver, timeout=2)
    if swal_text and ("이미" in swal_text or "중복" in swal_text):
        print(f"  [WARN] 중복 용어: {swal_text}")
        dismiss_swal(driver, timeout=2)
        wait_swal_gone(driver)
        return False

    # === Step 2: 단어 목록 선택 (최대 2회 시도) ===
    # 단어 목록 API 응답 대기 (OKT 형태소 분석 포함)
    for wait_i in range(10):
        time.sleep(1)
        # Vue 컴포넌트의 addTerm_wordListArr 확인 (부모 방향 탐색)
        debug_info = driver.execute_script("""
            var dlg = document.querySelector('.v-dialog--active');
            if (!dlg || !dlg.__vue__) return 'no-dlg';
            var p = dlg.__vue__;
            while (p) {
                if (p.addTerm_wordListArr !== undefined) {
                    return 'arr=' + p.addTerm_wordListArr.length +
                           '|step=' + p.addModalStep +
                           '|termNm=' + p.addTerm_termNm +
                           '|modalShow=' + p.addTermModalShow;
                }
                p = p.$parent;
            }
            return 'no-comp';
        """)
        tables = modal.find_elements(By.CSS_SELECTOR, "#addTerm_wordList_table")
        sheets = modal.find_elements(By.CSS_SELECTOR, ".v-sheet.v-sheet--outlined")
        print(f"  [DEBUG] wait {wait_i+1}s: {debug_info}, tables={len(tables)}, sheets={len(sheets)}")
        if len(tables) > 0 or len(sheets) > 0:
            break
        if debug_info and "arr=" in str(debug_info):
            arr_str = str(debug_info).split("arr=")[1].split("|")[0]
            if arr_str != "0":
                break

    for attempt in range(2):
        time.sleep(1)
        select_word_checkboxes(driver, modal)
        screenshot(driver, f"term_step2_{term_nm}")
        time.sleep(0.5)

        # "다음" 버튼 (Step 2) - 현재 보이는 stepper content에서 찾기
        next_btn2 = None
        modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in modal_btns:
            try:
                tc = driver.execute_script("return arguments[0].textContent;", btn).strip()
                if tc == "다음" and btn.is_displayed():
                    next_btn2 = btn
                    break
            except:
                continue
        if next_btn2:
            driver.execute_script("arguments[0].click();", next_btn2)
            time.sleep(2)
        else:
            print(f"  [FAIL] Step2 다음 버튼 못 찾음")
            return False

        # swal 체크 (선택 오류)
        swal_text = get_swal_text(driver, timeout=2)
        if swal_text and "선택" in swal_text:
            print(f"  [WARN] Step2 swal (attempt {attempt+1}): {swal_text}")
            dismiss_swal(driver, timeout=2)
            wait_swal_gone(driver)
            if attempt == 1:
                print(f"  [FAIL] 단어 선택 2회 실패")
                return False
            continue  # retry
        elif swal_text:
            print(f"  [WARN] Step2 swal: {swal_text}")
            dismiss_swal(driver, timeout=2)
            wait_swal_gone(driver)
        break  # success - moved to Step 3

    # === Step 3: 용어 정보 입력 ===
    time.sleep(1)

    # 도메인 선택 (v-autocomplete)
    domain_selects = modal.find_elements(By.CSS_SELECTOR, ".v-autocomplete input")
    for sel in domain_selects:
        try:
            if sel.is_displayed():
                driver.execute_script("arguments[0].click();", sel)
                sel.send_keys(Keys.CONTROL, "a")
                sel.send_keys(domain_name)
                time.sleep(1.5)
                # 드롭다운 항목 선택
                menu_items = driver.find_elements(By.CSS_SELECTOR, ".v-menu__content .v-list-item")
                for item in menu_items:
                    try:
                        if item.is_displayed() and domain_name in item.text:
                            driver.execute_script("arguments[0].click();", item)
                            time.sleep(0.5)
                            break
                    except:
                        continue
                break
        except:
            continue

    # 용어 설명 입력
    textareas = modal.find_elements(By.CSS_SELECTOR, "textarea")
    for ta in textareas:
        try:
            if ta.is_displayed():
                driver.execute_script("arguments[0].click();", ta)
                ta.send_keys(Keys.CONTROL, "a")
                ta.send_keys(f"Selenium 테스트 용어: {term_nm}")
                break
        except:
            continue

    time.sleep(0.5)
    screenshot(driver, f"term_step3_{term_nm}")

    # 모달 하단 "등록" 또는 "등록 신청" 버튼
    footer_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
    submit_btn = None
    for btn in reversed(footer_btns):
        try:
            tc = driver.execute_script("return arguments[0].textContent;", btn).strip()
            if tc in ("등록", "등록 신청") and btn.is_displayed():
                submit_btn = btn
                break
        except:
            continue
    if submit_btn:
        driver.execute_script("arguments[0].scrollIntoView({block:'center'});", submit_btn)
        time.sleep(0.3)
        driver.execute_script("arguments[0].click();", submit_btn)
        time.sleep(3)
    else:
        print(f"  [FAIL] 용어 등록 submit 버튼 못 찾음")
        return False

    swal_text = get_swal_text(driver, timeout=5)
    print(f"  용어 등록 결과: {swal_text}")
    dismiss_swal(driver, timeout=3)
    wait_swal_gone(driver)

    # 모달 닫힘 대기
    time.sleep(1)
    # 모달이 아직 열려 있으면 닫기 시도
    try:
        active_dialogs = driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        if active_dialogs:
            close_btns = active_dialogs[0].find_elements(By.CSS_SELECTOR, ".v-btn")
            for btn in close_btns:
                tc = driver.execute_script("return arguments[0].textContent;", btn).strip()
                if tc in ("닫기", "취소", "close"):
                    driver.execute_script("arguments[0].click();", btn)
                    time.sleep(1)
                    break
    except:
        pass

    if swal_text is None:
        return False
    return "실패" not in swal_text and "오류" not in swal_text and "필수" not in swal_text


def find_approval_row(driver, item_name, max_attempts=3):
    """승인 테이블에서 특정 항목 행 클릭, 성공 여부 반환 (정확 매칭)"""
    for attempt in range(max_attempts):
        rows = driver.find_elements(By.CSS_SELECTOR, "#approval_table tbody tr")
        for row in rows:
            cells = row.find_elements(By.CSS_SELECTOR, "td")
            # 항목명 컬럼(첫번째 텍스트 셀)에서 정확 매칭
            for cell in cells:
                ct = cell.text.strip()
                if ct == item_name:
                    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", row)
                    time.sleep(0.3)
                    driver.execute_script("arguments[0].click();", row)
                    time.sleep(2)
                    return True
        time.sleep(2)
    return False


def scroll_to_detail_panel(driver):
    """상세 패널까지 스크롤"""
    scroll_containers = driver.find_elements(By.CSS_SELECTOR, ".v-main__wrap, .v-content__wrap")
    if scroll_containers:
        driver.execute_script("arguments[0].scrollTop = arguments[0].scrollHeight;", scroll_containers[0])
    else:
        driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
    time.sleep(1)


def get_detail_panel(driver):
    """상세 패널 반환"""
    panels = driver.find_elements(By.CSS_SELECTOR, ".v-sheet.v-sheet--outlined")
    return panels[-1] if panels else None


def click_panel_btn(driver, panel, label):
    """상세 패널 내 버튼 클릭 (textContent 기반)"""
    if not panel:
        return False
    btns = panel.find_elements(By.CSS_SELECTOR, ".v-btn")
    for btn in btns:
        try:
            tc = driver.execute_script("return arguments[0].textContent;", btn).strip()
            cls = btn.get_attribute("class") or ""
            # 정확 매치 또는 icon+텍스트 패턴
            if label in tc:
                # "반려"가 "반려 확인"에도 매치되므로 정확도 체크
                if label == "반려" and "확인" in tc:
                    continue
                driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", btn)
                time.sleep(0.3)
                driver.execute_script("arguments[0].click();", btn)
                return True
        except:
            continue
    return False


def do_reject(driver, item_name, reason="테스트 반려"):
    """승인 목록에서 항목 찾아 반려 처리, swal 메시지 반환"""
    if not find_approval_row(driver, item_name):
        print(f"  [WARN] '{item_name}' 승인 목록에서 못 찾음")
        return None

    scroll_to_detail_panel(driver)
    panel = get_detail_panel(driver)
    if not panel:
        print(f"  [WARN] 상세 패널 미발견")
        return None

    # 반려 버튼 클릭
    if not click_panel_btn(driver, panel, "반려"):
        print(f"  [WARN] 반려 버튼 못 찾음")
        return None
    time.sleep(1)

    # 반려 사유 입력
    panel_inputs = panel.find_elements(By.CSS_SELECTOR, "input[type='text']")
    for inp in panel_inputs:
        try:
            ph = inp.get_attribute("placeholder") or ""
            if "반려" in ph or "사유" in ph:
                driver.execute_script("arguments[0].click();", inp)
                inp.send_keys(reason)
                break
        except:
            continue
    time.sleep(0.5)

    # 반려 확인 클릭
    if not click_panel_btn(driver, panel, "반려 확인"):
        print(f"  [WARN] 반려 확인 버튼 못 찾음")
        return None
    time.sleep(3)

    swal_text = get_swal_text(driver, timeout=5)
    print(f"  반려 결과 swal: {swal_text}")
    dismiss_swal(driver, timeout=3)
    wait_swal_gone(driver)
    return swal_text


def do_approve(driver, item_name):
    """승인 목록에서 항목 찾아 승인 처리, swal 메시지 반환"""
    if not find_approval_row(driver, item_name):
        print(f"  [WARN] '{item_name}' 승인 목록에서 못 찾음")
        return None

    scroll_to_detail_panel(driver)
    panel = get_detail_panel(driver)
    if not panel:
        print(f"  [WARN] 상세 패널 미발견")
        return None

    # 승인 버튼 클릭
    if not click_panel_btn(driver, panel, "승인"):
        print(f"  [WARN] 승인 버튼 못 찾음")
        return None
    time.sleep(3)

    swal_text = get_swal_text(driver, timeout=5)
    print(f"  승인 결과 swal: {swal_text}")
    dismiss_swal(driver, timeout=3)
    wait_swal_gone(driver)
    return swal_text


# ============================================================
def main():
    global passed, failed
    driver = create_driver()

    try:
        # ===== STEP 1: jyjang → 단어 2건 등록 신청 =====
        print(f"\n===== STEP 1: jyjang → 단어 2건 등록 신청 ({WORD_A}, {WORD_B}) =====")
        if not login(driver, "jyjang"):
            print("  [FATAL] jyjang 로그인 실패")
            return

        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        ok_a = register_word(driver, WORD_A, WORD_A_ENG)
        check("STEP1-1: 단어A 등록 신청", ok_a)

        ok_b = register_word(driver, WORD_B, WORD_B_ENG)
        check("STEP1-2: 단어B 등록 신청", ok_b)

        screenshot(driver, "step1_words_registered")

        # ===== STEP 2: jyjang → 용어 2건 등록 신청 (API 직접 호출) =====
        print(f"\n===== STEP 2: jyjang → 용어 2건 등록 신청 ({TERM_A}, {TERM_B}) =====")
        # OKT 라이브러리 미설치로 UI stepper 불가 → createTerms API 직접 호출
        time.sleep(1)

        ok_ta = register_term_via_api(driver, TERM_A, WORD_A)
        check("STEP2-1: 용어A 등록 신청", ok_ta)

        ok_tb = register_term_via_api(driver, TERM_B, WORD_B)
        check("STEP2-2: 용어B 등록 신청", ok_tb)

        screenshot(driver, "step2_terms_registered")
        logout(driver)

        # ===== STEP 3: space → 단어A 반려 → 용어A cascade 삭제 확인 =====
        print(f"\n===== STEP 3: space → 단어A 반려 (cascade 검증) =====")
        if not login(driver, "space"):
            print("  [FATAL] space 로그인 실패")
            return

        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)

        # 단어A 반려
        reject_msg = do_reject(driver, WORD_A, "연쇄 삭제 테스트")
        reject_ok = reject_msg is not None and "완료" in reject_msg
        check("STEP3-1: 단어A 반려 처리", reject_ok)

        # cascade 알림 메시지 확인 (연관 용어 삭제 안내)
        # swal text에 "연관 미승인 용어" 메시지가 포함되어야 함
        cascade_notified = reject_msg is not None and (
            TERM_A in reject_msg or "용어" in reject_msg or "연쇄" in reject_msg or "연관" in reject_msg)
        check("STEP3-2: cascade 삭제 알림 메시지 표시", cascade_notified)
        screenshot(driver, "step3_cascade_reject")

        # 승인 목록에서 용어A도 사라졌는지 확인 (승인대기 필터에서)
        time.sleep(1)
        # 페이지 새로고침하여 최신 데이터
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)

        term_a_gone = not find_approval_row(driver, TERM_A, max_attempts=1)
        check("STEP3-3: 용어A도 승인대기 목록에서 사라짐 (cascade 삭제)", term_a_gone)

        # 반려 이력에서 확인
        filter_btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn-toggle .v-btn")
        for btn in filter_btns:
            if "반려" in btn.text:
                btn.click()
                time.sleep(2)
                break

        # 단어A 반려 이력 (항목명 컬럼 정확 매칭)
        word_a_history = False
        term_a_history = False
        rows = driver.find_elements(By.CSS_SELECTOR, "#approval_table tbody tr")
        for row in rows:
            cells = row.find_elements(By.CSS_SELECTOR, "td")
            for cell in cells:
                ct = cell.text.strip()
                if ct == WORD_A:
                    word_a_history = True
                if ct == TERM_A:
                    term_a_history = True

        check("STEP3-4: 반려 이력에 단어A 존재", word_a_history)
        check("STEP3-5: 반려 이력에 용어A 연쇄삭제 존재", term_a_history)
        screenshot(driver, "step3_reject_history")

        # ===== STEP 4: space → 용어B 승인 시도 → 단어 미승인 alert =====
        print(f"\n===== STEP 4: space → 용어B 승인 시도 (단어 미승인 alert) =====")

        # 승인대기 필터로 전환 (STEP3에서 "반려" 탭 선택 상태이므로 명시적 전환 필요)
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)
        # keep-alive 캐시로 "반려" 탭이 유지될 수 있으므로 "승인대기" 버튼 클릭
        filter_btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn-toggle .v-btn")
        for btn in filter_btns:
            if "승인대기" in btn.text:
                driver.execute_script("arguments[0].click();", btn)
                time.sleep(2)
                break

        approve_msg = do_approve(driver, TERM_B)
        # "다음 단어가 아직 승인되지 않았습니다" 메시지 기대
        word_first_alert = approve_msg is not None and ("승인되지 않았습니다" in approve_msg or "먼저 승인" in approve_msg or WORD_B in approve_msg)
        check("STEP4-1: 용어B 승인 시 단어 미승인 alert 표시", word_first_alert)
        screenshot(driver, "step4_word_first_alert")

        # ===== STEP 5: space → 단어B 승인 → 용어B 재승인 성공 =====
        print(f"\n===== STEP 5: space → 단어B 승인 후 용어B 승인 =====")

        # 목록 새로고침 + 승인대기 필터 명시 전환
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)
        filter_btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn-toggle .v-btn")
        for btn in filter_btns:
            if "승인대기" in btn.text:
                driver.execute_script("arguments[0].click();", btn)
                time.sleep(2)
                break

        # 단어B 승인
        approve_word_msg = do_approve(driver, WORD_B)
        word_b_approved = approve_word_msg is not None and ("완료" in approve_word_msg or "승인" in approve_word_msg)
        check("STEP5-1: 단어B 승인 성공", word_b_approved)

        # 목록 새로고침 + 승인대기 필터 명시 전환
        time.sleep(1)
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)
        filter_btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn-toggle .v-btn")
        for btn in filter_btns:
            if "승인대기" in btn.text:
                driver.execute_script("arguments[0].click();", btn)
                time.sleep(2)
                break

        # 용어B 재승인
        approve_term_msg = do_approve(driver, TERM_B)
        term_b_approved = approve_term_msg is not None and ("완료" in approve_term_msg or "승인" in approve_term_msg)
        check("STEP5-2: 용어B 승인 성공 (단어 승인 후)", term_b_approved)
        screenshot(driver, "step5_term_b_approved")

        # ===== STEP 6: 정리 =====
        print(f"\n===== STEP 6: 정리 =====")
        # 용어B 삭제
        click_nav_menu(driver, "용어", nav_id="nav_term", parent_group_text="데이터 표준 사전")
        time.sleep(2)
        rows = driver.find_elements(By.CSS_SELECTOR, "#term_table tbody tr, .v-data-table tbody tr")
        for row in rows:
            if TERM_B in row.text:
                row.click()
                time.sleep(1)
                del_btn = find_visible_btn(driver, "삭제")
                if del_btn:
                    del_btn.click()
                    time.sleep(1)
                    dismiss_swal(driver, timeout=3)
                    time.sleep(1)
                    dismiss_swal(driver, timeout=3)
                    wait_swal_gone(driver)
                    print(f"  정리: 용어B 삭제")
                break

        # 단어B 삭제
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)
        rows = driver.find_elements(By.CSS_SELECTOR, "#word_table tbody tr, .v-data-table tbody tr")
        for row in rows:
            if WORD_B in row.text:
                row.click()
                time.sleep(1)
                del_btn = find_visible_btn(driver, "삭제")
                if del_btn:
                    del_btn.click()
                    time.sleep(1)
                    dismiss_swal(driver, timeout=3)
                    time.sleep(1)
                    dismiss_swal(driver, timeout=3)
                    wait_swal_gone(driver)
                    print(f"  정리: 단어B 삭제")
                break

        screenshot(driver, "step6_cleanup")

    except Exception as e:
        print(f"\n[ERROR] 예외 발생: {type(e).__name__}: {e}")
        screenshot(driver, "error_final")
        import traceback
        traceback.print_exc()

    finally:
        print(f"\n{'='*60}")
        print(f"결과: {passed} PASS / {failed} FAIL (총 {passed+failed}건)")
        print(f"{'='*60}")
        for r in results:
            print(r)
        print()
        driver.quit()
        sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
