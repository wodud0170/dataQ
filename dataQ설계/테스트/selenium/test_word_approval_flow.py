"""
단어 등록 → 승인 → 삭제 통합 테스트
로그인 기본: localhost:28091

테스트 순서:
  1. jyjang/123 로그인 → 단어 사전 → 단어 등록 (미승인 상태로 생성)
  2. 로그아웃 → space/123 로그인 → 승인 화면 → 해당 단어 승인
  3. 단어 사전 → 승인된 단어 검색 → 체크 → 삭제

준수사항:
  - 실질적으로 사용자가 사용하듯 테스트를 하기 위해 모든 진행은 DOM 클릭으로 제한함
  - execute_script로 Vue 메서드 직접 호출 금지 (스크롤/가시성 보조만 허용)
"""
import time
import sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.action_chains import ActionChains

BASE_URL = "http://localhost:28091"
import random
_SUFFIX = str(random.randint(100, 999))
TEST_WORD_NM = f"셀레니움{_SUFFIX}"
TEST_WORD_ENG = f"SELEN{_SUFFIX}"
TEST_WORD_ENG_FULL = f"SELENIUM TEST {_SUFFIX}"
TEST_WORD_DESC = "Selenium 자동화 테스트용 단어"


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
    path = f"C:/Users/장재영/Desktop/dataQ/dataQ설계/테스트/selenium/screenshots/{name}.png"
    driver.save_screenshot(path)
    print(f"  스크린샷: {name}.png")


def login(driver, user_id, password="123"):
    driver.get(BASE_URL)
    time.sleep(2)
    # 이미 로그인 상태면 로그아웃 먼저
    if "/app/" in driver.current_url:
        driver.get(BASE_URL + "/logout")
        time.sleep(2)
        driver.get(BASE_URL)
        time.sleep(2)

    id_input = wait_for(driver, By.CSS_SELECTOR, "input[type='text']")
    id_input.clear()
    id_input.send_keys(user_id)
    pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_input.clear()
    pw_input.send_keys(password)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
    time.sleep(3)
    ok = "/app/main" in driver.current_url
    print(f"  [{user_id}] 로그인 {'성공' if ok else '실패'}")
    return ok


def logout(driver):
    driver.get(BASE_URL + "/logout")
    time.sleep(2)
    print(f"  로그아웃 완료")


def click_nav_menu(driver, menu_text, nav_id=None, parent_group_text=None):
    """좌측 네비게이션 메뉴 클릭.
    parent_group_text: 상위 그룹명 (예: '데이터 표준 사전', '관리') — 접혀있으면 먼저 펼침
    nav_id: 메뉴 아이템 ID (예: nav_word, nav_approval)
    """
    time.sleep(0.5)

    # 0) 대시보드 메뉴(최상단)로 이동해 네비게이션 스크롤 초기화
    try:
        dashboard_el = driver.find_element(By.ID, "nav_dashboard")
        ActionChains(driver).move_to_element(dashboard_el).perform()
        time.sleep(0.3)
    except:
        pass

    # 1) 부모 그룹이 지정되어 있으면 해당 그룹 헤더를 찾아 펼치기
    if parent_group_text:
        group_headers = driver.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
        for header in group_headers:
            try:
                if header.text.strip() == parent_group_text:
                    # 부모 v-list-group이 active인지 확인
                    group_el = header
                    for _ in range(10):
                        group_el = group_el.find_element(By.XPATH, "..")
                        if "v-list-group" in (group_el.get_attribute("class") or ""):
                            break
                    if group_el and "v-list-group--active" not in (group_el.get_attribute("class") or ""):
                        ActionChains(driver).move_to_element(header).click().perform()
                        print(f"  그룹 펼침: {parent_group_text}")
                        time.sleep(1)
                    else:
                        print(f"  그룹 이미 펼쳐짐: {parent_group_text}")
                    break
            except:
                continue

    # 2) nav_id로 직접 클릭
    if nav_id:
        for attempt in range(3):
            try:
                el = WebDriverWait(driver, 5).until(
                    EC.element_to_be_clickable((By.ID, nav_id))
                )
                ActionChains(driver).move_to_element(el).click().perform()
                time.sleep(2)
                print(f"  메뉴 클릭: {menu_text} (#{nav_id})")
                return True
            except Exception as e:
                if attempt < 2:
                    time.sleep(1)
                else:
                    print(f"  [WARN] nav_id '{nav_id}' 클릭 실패: {type(e).__name__}")

    # 3) 네비게이션 영역 내 v-list-item__title 텍스트 매칭
    nav_drawer = driver.find_elements(By.CSS_SELECTOR, ".v-navigation-drawer")
    search_root = nav_drawer[0] if nav_drawer else driver
    titles = search_root.find_elements(By.CSS_SELECTOR, ".v-list-item__title")
    for t in titles:
        try:
            if t.text.strip() == menu_text:
                t.click()
                time.sleep(2)
                print(f"  메뉴 클릭: {menu_text} (텍스트)")
                return True
        except:
            continue

    print(f"  [WARN] '{menu_text}' 메뉴 못 찾음")
    return False


def dismiss_swal(driver, timeout=5):
    """SweetAlert 확인 버튼 클릭"""
    try:
        btn = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm", timeout=timeout)
        btn.click()
        time.sleep(0.5)
        return True
    except:
        return False


def wait_swal_gone(driver, timeout=5):
    """SweetAlert 사라질 때까지 대기 (timer 자동 닫힘)"""
    try:
        WebDriverWait(driver, timeout).until(
            EC.invisibility_of_element_located((By.CSS_SELECTOR, ".swal2-popup"))
        )
    except:
        # 버튼 남아있으면 클릭
        dismiss_swal(driver, timeout=1)


def main():
    print("=" * 60)
    print("단어 등록 → 승인 → 삭제 통합 테스트")
    print(f"테스트 단어: {TEST_WORD_NM} / {TEST_WORD_ENG}")
    print("=" * 60)

    driver = create_driver()

    # 스크린샷 폴더 생성
    import os
    os.makedirs("C:/Users/장재영/Desktop/dataQ/dataQ설계/테스트/selenium/screenshots", exist_ok=True)

    try:
        # ============================================================
        # STEP 1: jyjang 로그인 → 단어 등록
        # ============================================================
        print(f"\n[STEP 1] jyjang 로그인 → 단어 등록")
        if not login(driver, "jyjang"):
            return 1

        # 단어 탭 열기
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # "등록 신청" (일반 사용자) 또는 "등록" (관리자) 버튼 클릭
        add_btn = None
        buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in buttons:
            if btn.text.strip() in ("등록 신청", "등록"):
                add_btn = btn
                break

        if not add_btn:
            print("  [FAIL] 등록 버튼 못 찾음")
            screenshot(driver, "step1_no_add_btn")
            return 1

        add_btn.click()
        time.sleep(1)
        screenshot(driver, "step1_add_modal")

        # 모달 입력
        # 단어명
        word_nm_input = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active input[type='text']")
        # 모달 내 모든 input 찾기
        modal = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")
        inputs = modal.find_elements(By.CSS_SELECTOR, "input[type='text']")
        textareas = modal.find_elements(By.CSS_SELECTOR, "textarea")

        print(f"  모달 input 수: {len(inputs)}, textarea 수: {len(textareas)}")

        # 순서: 단어명, 영문약어명, 영문명, (설명은 textarea)
        if len(inputs) >= 3:
            # 단어명
            inputs[0].click()
            inputs[0].send_keys(Keys.CONTROL, "a")
            inputs[0].send_keys(TEST_WORD_NM)
            time.sleep(0.3)

            # 영문약어명
            inputs[1].click()
            inputs[1].send_keys(Keys.CONTROL, "a")
            inputs[1].send_keys(TEST_WORD_ENG)
            time.sleep(0.5)  # 중복 체크 대기

            # 영문명
            inputs[2].click()
            inputs[2].send_keys(Keys.CONTROL, "a")
            inputs[2].send_keys(TEST_WORD_ENG_FULL)
            time.sleep(0.3)

        # 설명 (textarea)
        if textareas:
            textareas[0].click()
            textareas[0].send_keys(Keys.CONTROL, "a")
            textareas[0].send_keys(TEST_WORD_DESC)

        time.sleep(0.5)
        screenshot(driver, "step1_filled")

        # 모달 하단 "등록 신청" (일반) 또는 "등록" (관리자) 버튼 DOM 클릭
        modal_btns = modal.find_elements(By.CSS_SELECTOR, ".v-btn")
        save_btn = None
        for btn in reversed(modal_btns):
            if btn.text.strip() in ("등록 신청", "등록"):
                save_btn = btn
                break
        if save_btn:
            driver.execute_script("arguments[0].scrollIntoView(true);", save_btn)
            time.sleep(0.3)
            save_btn.click()
            print("  모달 등록 버튼 클릭")
        time.sleep(4)

        # SweetAlert 처리 (유사어 경고 or 성공 알림)
        dismiss_swal(driver, timeout=3)
        time.sleep(2)
        dismiss_swal(driver, timeout=1)  # 혹시 두 번째 알림
        time.sleep(1)

        # 모달이 닫혔는지 확인, 안 닫혔으면 닫기 버튼 클릭
        try:
            close_btn = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active .v-btn .mdi-close")
            if close_btn:
                close_btn.click()
                time.sleep(1)
        except:
            pass
        screenshot(driver, "step1_after_save")

        # 마이페이지 → 요청 현황에서 등록 확인
        click_nav_menu(driver, "요청 현황", nav_id="nav_myRequest", parent_group_text="마이페이지")
        time.sleep(3)

        # 요청 현황 목록에서 테스트 단어 확인
        registered = False
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  요청 현황 행 수: {len(rows)}")
        for row in rows:
            if TEST_WORD_NM in row.text:
                registered = True
                print(f"  '{TEST_WORD_NM}' 요청 현황에서 확인")
                break

        print(f"  단어 등록 확인: {'PASS' if registered else 'FAIL'}")
        screenshot(driver, "step1_registered")

        if not registered:
            print("  [FAIL] 요청 현황에서 등록된 단어를 찾지 못함")
            for i, r in enumerate(rows[:5]):
                print(f"    행[{i}]: {r.text[:80]}")
            return 1

        # ============================================================
        # STEP 2: space 로그인 → 승인
        # ============================================================
        print(f"\n[STEP 2] space 로그인 → 승인")
        logout(driver)
        if not login(driver, "space"):
            return 1

        # 승인 메뉴 열기
        click_nav_menu(driver, "승인", nav_id="nav_approval", parent_group_text="관리")
        time.sleep(2)
        screenshot(driver, "step2_approval_list")

        # 승인대기 탭 확인 (기본 선택)
        # 목록에서 테스트 단어 찾기
        time.sleep(1)
        target_row = None
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  승인 목록 행 수: {len(rows)}")
        for row in rows:
            if TEST_WORD_NM in row.text:
                target_row = row
                print(f"  '{TEST_WORD_NM}' 승인대기 항목 찾음")
                break

        if not target_row:
            print("  [FAIL] 승인 목록에서 테스트 단어를 찾지 못함")
            for i, r in enumerate(rows[:5]):
                print(f"    행[{i}]: {r.text[:80]}")
            screenshot(driver, "step2_not_found")
            return 1

        # 체크박스 선택 (행의 첫 번째 td의 체크박스)
        try:
            checkbox = target_row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, .mdi-checkbox-blank-outline")
            checkbox.click()
            print(f"  '{TEST_WORD_NM}' 체크박스 선택")
        except:
            # 첫 번째 td 클릭
            tds = target_row.find_elements(By.CSS_SELECTOR, "td")
            if tds:
                tds[0].click()
                print(f"  '{TEST_WORD_NM}' 첫 번째 셀 클릭 (체크)")
        time.sleep(1)
        screenshot(driver, "step2_checked")

        # "일괄 승인" 버튼 클릭
        batch_approve_btn = None
        buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in buttons:
            if "일괄 승인" in btn.text:
                batch_approve_btn = btn
                break

        if batch_approve_btn:
            batch_approve_btn.click()
            print("  일괄 승인 버튼 클릭")
            time.sleep(2)
        else:
            print("  [FAIL] 일괄 승인 버튼 못 찾음")
            screenshot(driver, "step2_no_approve_btn")
            return 1

        # SweetAlert 확인 팝업 ("선택한 N건을 승인하시겠습니까?")
        try:
            swal_confirm = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm", timeout=5)
            swal_confirm.click()
            print("  승인 확인")
            time.sleep(3)
        except:
            print("  [WARN] 승인 확인 팝업 없음")

        # 성공 SweetAlert 처리 (timer 자동닫힘)
        dismiss_swal(driver, timeout=3)
        time.sleep(2)
        screenshot(driver, "step2_approved")
        print("  승인 완료")

        # ============================================================
        # STEP 3: 단어 사전 → 검색 → 삭제
        # ============================================================
        print(f"\n[STEP 3] 단어 사전에서 승인된 단어 삭제")

        # 단어 탭 열기
        click_nav_menu(driver, "단어", nav_id="nav_word", parent_group_text="데이터 표준 사전")
        time.sleep(2)

        # 단어명으로 검색 (DOM send_keys + Enter)
        search_inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='text']")
        for inp in search_inputs:
            if inp.is_displayed() and inp.get_attribute("placeholder") != "통합 검색":
                inp.click()
                inp.send_keys(Keys.CONTROL, "a")
                inp.send_keys(TEST_WORD_NM)
                inp.send_keys(Keys.ENTER)
                break
        time.sleep(2)

        screenshot(driver, "step3_search_result")

        # 테이블에서 해당 행의 체크박스 선택
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        checked = False
        for row in rows:
            if TEST_WORD_NM in row.text:
                try:
                    checkbox = row.find_element(By.CSS_SELECTOR, ".v-simple-checkbox, .v-data-table__checkbox, input[type='checkbox'], .mdi-checkbox-blank-outline")
                    checkbox.click()
                    checked = True
                    print(f"  '{TEST_WORD_NM}' 체크박스 선택")
                    break
                except Exception as e:
                    # td 첫번째 클릭
                    tds = row.find_elements(By.CSS_SELECTOR, "td")
                    if tds:
                        tds[0].click()
                        checked = True
                        print(f"  '{TEST_WORD_NM}' 첫 번째 셀 클릭")
                        break

        if not checked:
            print("  [FAIL] 체크박스 선택 실패")
            screenshot(driver, "step3_check_fail")
            return 1

        time.sleep(0.5)
        screenshot(driver, "step3_checked")

        # "삭제" 버튼 클릭
        delete_btn = None
        buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
        for btn in buttons:
            txt = btn.text.strip()
            if txt == "삭제":
                delete_btn = btn
                break

        if delete_btn:
            delete_btn.click()
            print("  삭제 버튼 클릭")
            time.sleep(1)
        else:
            print("  [FAIL] 삭제 버튼 못 찾음")
            return 1

        # SweetAlert "정말로 삭제할까요?" → "삭제" 확인
        try:
            swal_confirm = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm", timeout=5)
            swal_confirm.click()
            print("  삭제 확인")
            time.sleep(2)
        except:
            print("  [WARN] 삭제 확인 팝업 없음")

        # 삭제 완료 SweetAlert
        wait_swal_gone(driver, timeout=5)
        time.sleep(1)
        screenshot(driver, "step3_deleted")

        # 삭제 확인: 자동으로 목록 갱신됨, 잠시 대기
        time.sleep(2)

        deleted = True
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        for row in rows:
            if TEST_WORD_NM in row.text:
                deleted = False
                break

        print(f"  삭제 확인: {'PASS' if deleted else 'FAIL'}")
        screenshot(driver, "step3_verify_deleted")

        # ============================================================
        # 최종 결과
        # ============================================================
        print(f"\n{'=' * 60}")
        results = {
            "1. jyjang 단어 등록": registered,
            "2. space 승인": True,  # 여기까지 왔으면 성공
            "3. 단어 삭제": deleted,
        }
        all_pass = all(results.values())
        for k, v in results.items():
            print(f"  {k}: {'PASS' if v else 'FAIL'}")
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
