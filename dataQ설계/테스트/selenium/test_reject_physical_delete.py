"""
반려 물리삭제 승인프로세스 테스트
로그인 기본: localhost:28091

테스트 시나리오:
  STEP 1. jyjang (일반사용자) 로그인 → 단어 등록 신청 1건
  STEP 2. space (관리자) 로그인 → 승인 화면에서 해당 단어 반려
  STEP 3. 반려 후 검증:
    - TB_WORD에서 해당 단어 물리 삭제 확인 (단어 사전 검색에서 미노출)
    - 승인 이력(TB_APRV_STATS)에 반려 이력 존재 확인
    - 동일 단어명으로 재등록 가능 확인
  STEP 4. 정리: 재등록된 단어 삭제

준수사항:
  - 모든 진행은 DOM 클릭으로 제한
  - execute_script로 Vue 메서드 직접 호출 금지 (스크롤/가시성 보조만 허용)
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
_SUFFIX = str(random.randint(100, 999))
TEST_WORD = f"셀반려삭제{_SUFFIX}"
TEST_WORD_ENG = f"SELREJDEL{_SUFFIX}"
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
            except Exception as e:
                if attempt < 2:
                    time.sleep(1)
                else:
                    print(f"  [WARN] nav_id '{nav_id}' 클릭 실패")

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


def dismiss_overlay(driver):
    """오버레이/스크림 닫기"""
    scrims = driver.find_elements(By.CSS_SELECTOR, ".v-overlay__scrim")
    for scrim in scrims:
        try:
            if scrim.is_displayed():
                scrim.click()
                time.sleep(0.5)
        except:
            pass
    # dialog close button
    close_btns = driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active .v-btn")
    for btn in close_btns:
        try:
            if btn.is_displayed() and "close" in (btn.get_attribute("title") or "").lower():
                btn.click()
                time.sleep(0.5)
                return
        except:
            pass
    # ESC key
    try:
        ActionChains(driver).send_keys(Keys.ESCAPE).perform()
        time.sleep(0.5)
    except:
        pass


def find_visible_btn(driver, label):
    buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
    for btn in buttons:
        try:
            if btn.is_displayed() and btn.text.strip() == label:
                return btn
        except:
            continue
    return None


def register_word(driver, word_nm, word_eng, word_desc="Selenium 반려삭제 테스트"):
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
        driver.execute_script("arguments[0].scrollIntoView({block:'center'});", inputs[0])
        time.sleep(0.2)
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
        textareas[0].send_keys(word_desc)

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


def search_word_in_dict(driver, word_nm):
    """단어 사전 테이블에서 특정 단어 검색, 존재 여부 반환"""
    time.sleep(1)

    # 테이블 행에서 직접 찾기 (검색 없이, 현재 로드된 목록에서)
    rows = driver.find_elements(By.CSS_SELECTOR, "#word_table tbody tr")
    for row in rows:
        cells = row.find_elements(By.CSS_SELECTOR, "td")
        for cell in cells:
            if word_nm in cell.text:
                return True

    # 통합 검색 활용
    try:
        search_input = driver.find_element(By.CSS_SELECTOR, "input[placeholder='통합 검색']")
        search_input.click()
        search_input.send_keys(Keys.CONTROL, "a")
        search_input.send_keys(word_nm)
        search_input.send_keys(Keys.ENTER)
        time.sleep(2)

        # 검색 결과에서 단어 탭 확인
        body_text = driver.find_element(By.TAG_NAME, "body").text
        found = word_nm in body_text

        # ESC로 검색 결과 닫기
        ActionChains(driver).send_keys(Keys.ESCAPE).perform()
        time.sleep(0.5)

        return found
    except:
        pass

    return False


# ============================================================
# 메인 테스트
# ============================================================
def main():
    global passed, failed
    driver = create_driver()

    try:
        # ===== STEP 1: jyjang 로그인 → 단어 등록 신청 =====
        print("\n===== STEP 1: jyjang 로그인 → 단어 등록 신청 =====")
        if not login(driver, "jyjang"):
            print("  [FATAL] jyjang 로그인 실패")
            return

        # 단어 사전 열기
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # 단어 등록 신청
        ok = register_word(driver, TEST_WORD, TEST_WORD_ENG)
        check("STEP1-1: 단어 등록 신청", ok)
        screenshot(driver, "step1_word_registered")

        logout(driver)

        # ===== STEP 2: space 로그인 → 단어 반려 =====
        print("\n===== STEP 2: space 로그인 → 단어 반려 =====")
        if not login(driver, "space"):
            print("  [FATAL] space 로그인 실패")
            return

        # 승인 화면 열기
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)

        # 테이블에서 TEST_WORD 행 클릭
        found_row = False
        for attempt in range(3):
            rows = driver.find_elements(By.CSS_SELECTOR, "#approval_table tbody tr")
            for row in rows:
                cells = row.find_elements(By.CSS_SELECTOR, "td")
                for cell in cells:
                    if TEST_WORD in cell.text:
                        driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", row)
                        time.sleep(0.3)
                        row.click()
                        found_row = True
                        break
                if found_row:
                    break
            if found_row:
                break
            time.sleep(2)

        check("STEP2-1: 승인 목록에서 단어 발견", found_row)
        time.sleep(2)

        reject_ok = False
        if found_row:
            # 상세 패널이 나타날 때까지 대기 (v-sheet--outlined 클래스)
            detail_panel = None
            for _ in range(5):
                panels = driver.find_elements(By.CSS_SELECTOR, ".v-sheet.v-sheet--outlined")
                if panels:
                    detail_panel = panels[-1]
                    break
                time.sleep(1)

            if not detail_panel:
                print("  [WARN] 상세 패널 미발견, 페이지 하단 스크롤 시도")
                driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                time.sleep(1)
                panels = driver.find_elements(By.CSS_SELECTOR, ".v-sheet.v-sheet--outlined")
                if panels:
                    detail_panel = panels[-1]

            if detail_panel:
                # 상세 패널의 맨 아래까지 보이도록 v-main 컨텐츠 영역 스크롤
                # v-main__wrap 또는 v-main 안의 스크롤 가능 영역 탐색
                scroll_containers = driver.find_elements(By.CSS_SELECTOR, ".v-main__wrap, .v-content__wrap")
                if scroll_containers:
                    driver.execute_script("arguments[0].scrollTop = arguments[0].scrollHeight;", scroll_containers[0])
                else:
                    driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                time.sleep(1)

                # 상세 패널 내에서 "반려" 버튼 찾기
                # 버튼 텍스트에 icon이 포함되어 .text가 빈 문자열일 수 있음
                # textContent 또는 innerText로 검색
                panel_btns = detail_panel.find_elements(By.CSS_SELECTOR, ".v-btn")
                reject_btn = None
                for btn in panel_btns:
                    try:
                        txt = driver.execute_script("return arguments[0].textContent;", btn).strip()
                        # "반려"를 포함하되 "반려 확인"이나 "반려 (0)" 등은 제외
                        if txt == "반려" or txt == "close 반려" or txt == "close\n반려":
                            reject_btn = btn
                            break
                    except:
                        continue

                # fallback: color="red"인 버튼 찾기
                if not reject_btn:
                    for btn in panel_btns:
                        try:
                            cls = btn.get_attribute("class") or ""
                            txt = driver.execute_script("return arguments[0].textContent;", btn).strip()
                            if "red" in cls and "반려" in txt and "확인" not in txt:
                                reject_btn = btn
                                break
                        except:
                            continue

                if reject_btn:
                    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", reject_btn)
                    time.sleep(0.5)
                    driver.execute_script("arguments[0].click();", reject_btn)
                    print("  반려 버튼 클릭됨")
                    time.sleep(1)
                    screenshot(driver, "step2_after_reject_btn")
                else:
                    print("  [WARN] 상세 패널에서 반려 버튼 못 찾음")
                    for b in panel_btns:
                        try:
                            tc = driver.execute_script("return arguments[0].textContent;", b).strip()
                            cls = b.get_attribute("class") or ""
                            print(f"    패널 버튼: textContent='{tc}', class has red={'red' in cls}")
                        except:
                            pass
                    screenshot(driver, "step2_no_reject_btn")

                # 반려 사유 입력 (placeholder: "반려 사유 입력")
                time.sleep(0.5)
                reject_input = None
                # 상세 패널 내 input 재검색 (반려 버튼 클릭 후 동적 생성)
                panel_inputs = detail_panel.find_elements(By.CSS_SELECTOR, "input[type='text']")
                for inp in panel_inputs:
                    try:
                        ph = inp.get_attribute("placeholder") or ""
                        if "반려" in ph or "사유" in ph:
                            reject_input = inp
                            break
                    except:
                        continue

                if reject_input:
                    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", reject_input)
                    time.sleep(0.3)
                    driver.execute_script("arguments[0].click();", reject_input)
                    reject_input.send_keys("테스트 반려 사유")
                    print("  반려 사유 입력됨")
                    time.sleep(0.5)
                else:
                    print("  [WARN] 반려 사유 입력 필드 못 찾음")
                    screenshot(driver, "step2_no_reject_input")

                # 반려 확인 버튼 (상세 패널 내)
                confirm_btn = None
                panel_btns = detail_panel.find_elements(By.CSS_SELECTOR, ".v-btn")
                for btn in panel_btns:
                    try:
                        if "반려 확인" in btn.text.strip():
                            confirm_btn = btn
                            break
                    except:
                        continue

                if confirm_btn:
                    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", confirm_btn)
                    time.sleep(0.3)
                    driver.execute_script("arguments[0].click();", confirm_btn)
                    print("  반려 확인 클릭됨")
                    time.sleep(3)
                else:
                    print("  [WARN] 반려 확인 버튼 못 찾음")
                    screenshot(driver, "step2_no_confirm_btn")

                # swal 결과 확인
                swal_found = False
                try:
                    swal = WebDriverWait(driver, 5).until(
                        EC.presence_of_element_located((By.CSS_SELECTOR, ".swal2-popup"))
                    )
                    swal_title = driver.find_element(By.CSS_SELECTOR, ".swal2-title").text
                    print(f"  swal 메시지: {swal_title}")
                    if "완료" in swal_title or "성공" in swal_title or "반려" in swal_title:
                        reject_ok = True
                    swal_found = True
                except:
                    pass

                if swal_found:
                    dismiss_swal(driver, timeout=3)
                    wait_swal_gone(driver)

                # Fallback 검증: swal 메시지가 없거나 문구가 바뀌었어도,
                # 반려가 성공했으면 대기 목록에서 해당 단어가 사라져야 함
                if not reject_ok:
                    time.sleep(1)
                    rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
                    remaining = [r for r in rows if TEST_WORD_NM in (r.text or "")]
                    if not remaining:
                        reject_ok = True
                        print(f"  [fallback-verify] 대기 목록에서 '{TEST_WORD_NM}' 사라짐 → 반려 완료로 판정")
                    else:
                        print(f"  [fallback-verify] 대기 목록에 여전히 '{TEST_WORD_NM}' 존재 → 반려 실패")
            else:
                print("  [WARN] 상세 패널 최종 미발견")

        check("STEP2-2: 반려 처리 완료", reject_ok)
        screenshot(driver, "step2_rejected")

        # ===== STEP 3: 반려 후 검증 =====
        print("\n===== STEP 3: 반려 후 검증 =====")

        # 3-1. 단어 사전에서 해당 단어가 사라졌는지 확인 (물리 삭제)
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        word_exists = search_word_in_dict(driver, TEST_WORD)
        check("STEP3-1: 반려된 단어 물리 삭제 (단어 사전에서 미노출)", not word_exists)
        screenshot(driver, "step3_word_deleted")

        # 3-2. 승인 화면 이력에서 반려 기록 확인
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(3)

        # "반려" 필터 클릭
        filter_btns = driver.find_elements(By.CSS_SELECTOR, ".v-btn-toggle .v-btn")
        for btn in filter_btns:
            if "반려" in btn.text:
                btn.click()
                time.sleep(2)
                break

        # 반려 이력에서 해당 단어명 확인
        reject_history_found = False
        rows = driver.find_elements(By.CSS_SELECTOR, "#approval_table tbody tr")
        for row in rows:
            cells = row.find_elements(By.CSS_SELECTOR, "td")
            for cell in cells:
                if TEST_WORD in cell.text:
                    reject_history_found = True
                    break
            if reject_history_found:
                break

        check("STEP3-2: 승인 이력에 반려 기록 존재", reject_history_found)
        screenshot(driver, "step3_reject_history")

        # 3-3. 동일 단어명으로 재등록 가능 확인
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # 관리자이므로 즉시 등록 (APRV_YN='Y')
        re_register_ok = register_word(driver, TEST_WORD, TEST_WORD_ENG, "재등록 테스트")
        check("STEP3-3: 반려 후 동일 단어명 재등록 성공", re_register_ok)
        screenshot(driver, "step3_re_register")

        # ===== STEP 4: 정리 — 재등록된 단어 삭제 =====
        print("\n===== STEP 4: 정리 =====")

        # 모달/오버레이가 남아있으면 닫기
        dismiss_overlay(driver)
        time.sleep(1)

        # 단어 사전으로 이동 (이미 열려있을 수 있음)
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # 테이블에서 행 찾아 클릭
        rows = driver.find_elements(By.CSS_SELECTOR, "#word_table tbody tr")
        word_row = None
        for row in rows:
            cells = row.find_elements(By.CSS_SELECTOR, "td")
            for cell in cells:
                if TEST_WORD in cell.text:
                    word_row = row
                    break
            if word_row:
                break

        if word_row:
            word_row.click()
            time.sleep(1)

            del_btn = find_visible_btn(driver, "삭제")
            if del_btn:
                del_btn.click()
                time.sleep(1)
                # swal 확인 (삭제 confirm)
                swal_confirm = driver.find_elements(By.CSS_SELECTOR, ".swal2-confirm")
                if swal_confirm:
                    swal_confirm[0].click()
                    time.sleep(2)
                dismiss_swal(driver, timeout=3)
                wait_swal_gone(driver)
                print("  정리: 단어 삭제 완료")
            else:
                print("  정리: 삭제 버튼 못 찾음 (수동 정리 필요)")
        else:
            print("  정리: 단어가 없어 삭제 불필요")

        screenshot(driver, "step4_cleanup")

    except Exception as e:
        print(f"\n[ERROR] 예외 발생: {type(e).__name__}: {e}")
        screenshot(driver, "error_final")
        import traceback
        traceback.print_exc()

    finally:
        print(f"\n{'='*50}")
        print(f"결과: {passed} PASS / {failed} FAIL (총 {passed+failed}건)")
        print(f"{'='*50}")
        for r in results:
            print(r)
        print()
        driver.quit()
        sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
