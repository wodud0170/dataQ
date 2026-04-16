"""
STOMP WebSocket 로그인/로그아웃 테스트
테스트 순서:
  1. space/123 로그인
  2. 아바타 → 로그아웃
  3. jyjang/123 로그인
  4. 메인 페이지에 머무르는지 확인 (로그인 화면으로 튕기면 실패)
"""
import time
import sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.edge.service import Service as EdgeService

BASE_URL = "http://localhost:28091"

def create_driver():
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1280, 900)
    return driver

def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, value))
    )

def wait_clickable(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((by, value))
    )

def get_console_logs(driver):
    """브라우저 콘솔 로그 수집 (Edge/Chrome)"""
    try:
        logs = driver.get_log("browser")
        return [l for l in logs if "STOMP" in l.get("message", "") or "Websocket" in l.get("message", "")]
    except Exception:
        return []

def login(driver, user_id, password, step_name):
    print(f"\n{'='*60}")
    print(f"[STEP] {step_name}: {user_id}/{password} 로그인")
    print(f"{'='*60}")

    # 로그인 페이지 대기
    time.sleep(1)
    current = driver.current_url
    print(f"  현재 URL: {current}")

    # 로그인 폼 찾기
    try:
        # id 입력
        id_input = wait_for(driver, By.CSS_SELECTOR, "input[type='text'], input[name='username'], input#username, input#userId")
        id_input.clear()
        id_input.send_keys(user_id)

        # password 입력
        pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
        pw_input.clear()
        pw_input.send_keys(password)

        # 로그인 버튼 클릭
        login_btn = driver.find_element(By.CSS_SELECTOR, "button[type='submit'], button.login-btn, .v-btn")
        login_btn.click()
        print(f"  로그인 버튼 클릭 완료")

    except Exception as e:
        print(f"  [ERROR] 로그인 폼 조작 실패: {e}")
        # 페이지 소스로 디버깅
        print(f"  Page title: {driver.title}")
        driver.save_screenshot(f"C:/Users/장재영/Desktop/dataQ/test_login_error_{user_id}.png")
        print(f"  스크린샷 저장됨: test_login_error_{user_id}.png")
        return False

    # 로그인 후 페이지 전환 대기
    time.sleep(3)
    current = driver.current_url
    print(f"  로그인 후 URL: {current}")

    # 5초간 0.5초 간격으로 URL 체크 (튕기는지 감시)
    print(f"  [WATCH] 5초간 로그인 유지 여부 감시...")
    bounced = False
    for i in range(10):
        time.sleep(0.5)
        url = driver.current_url
        if "/signin" in url or "/login" in url:
            print(f"  [FAIL] {(i+1)*0.5}초 후 로그인 화면으로 튕김! URL: {url}")
            bounced = True
            break

    if not bounced:
        final_url = driver.current_url
        print(f"  [PASS] 5초간 메인 페이지 유지. URL: {final_url}")
        driver.save_screenshot(f"C:/Users/장재영/Desktop/dataQ/test_login_success_{user_id}.png")
        print(f"  스크린샷 저장됨: test_login_success_{user_id}.png")
        return True
    else:
        driver.save_screenshot(f"C:/Users/장재영/Desktop/dataQ/test_login_bounced_{user_id}.png")
        print(f"  스크린샷 저장됨: test_login_bounced_{user_id}.png")
        return False

def logout(driver):
    print(f"\n{'='*60}")
    print(f"[STEP] 로그아웃")
    print(f"{'='*60}")

    try:
        # 아바타/프로필 버튼 찾기 (Vuetify v-avatar 또는 우측 상단 메뉴)
        avatar = None
        selectors = [
            ".v-avatar",
            ".v-btn .v-avatar",
            "header .v-avatar",
            ".v-app-bar .v-btn:last-child",
            ".v-toolbar .v-btn:last-child",
            "button.v-btn .mdi-account",
            ".mdi-account-circle",
        ]
        for sel in selectors:
            try:
                avatar = wait_clickable(driver, By.CSS_SELECTOR, sel, timeout=3)
                if avatar:
                    print(f"  아바타 찾음: {sel}")
                    break
            except:
                continue

        if not avatar:
            print("  [WARN] 아바타를 못 찾음. 전체 버튼 탐색...")
            buttons = driver.find_elements(By.CSS_SELECTOR, ".v-btn")
            for btn in buttons:
                try:
                    txt = btn.text.strip()
                    if txt and ("로그아웃" in txt or "logout" in txt.lower()):
                        avatar = btn
                        print(f"  로그아웃 버튼 직접 찾음: {txt}")
                        break
                except:
                    continue

        if avatar:
            avatar.click()
            time.sleep(1)

            # 드롭다운 메뉴에서 로그아웃 찾기
            try:
                logout_items = driver.find_elements(By.CSS_SELECTOR, ".v-list-item, .v-menu__content .v-list-item, .menuable__content__active .v-list-item")
                for item in logout_items:
                    if "로그아웃" in item.text or "Logout" in item.text:
                        item.click()
                        print(f"  로그아웃 메뉴 클릭")
                        time.sleep(1)

                        # swal 확인 버튼
                        try:
                            confirm = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm, .swal2-actions button", timeout=3)
                            confirm.click()
                            print(f"  확인 버튼 클릭")
                        except:
                            pass

                        time.sleep(2)
                        print(f"  로그아웃 후 URL: {driver.current_url}")
                        return True

                # 메뉴 항목에 없으면 텍스트로 재탐색
                all_clickable = driver.find_elements(By.XPATH, "//*[contains(text(), '로그아웃')]")
                for el in all_clickable:
                    try:
                        el.click()
                        print(f"  로그아웃 텍스트 클릭")
                        time.sleep(1)
                        try:
                            confirm = wait_clickable(driver, By.CSS_SELECTOR, ".swal2-confirm", timeout=3)
                            confirm.click()
                        except:
                            pass
                        time.sleep(2)
                        print(f"  로그아웃 후 URL: {driver.current_url}")
                        return True
                    except:
                        continue

            except Exception as e:
                print(f"  [ERROR] 로그아웃 메뉴 탐색 실패: {e}")

        print("  [WARN] 로그아웃 버튼/메뉴를 찾지 못함. URL로 직접 로그아웃 시도")
        driver.get(BASE_URL + "/logout")
        time.sleep(2)
        print(f"  로그아웃 후 URL: {driver.current_url}")
        return True

    except Exception as e:
        print(f"  [ERROR] 로그아웃 실패: {e}")
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_logout_error.png")
        return False

def main():
    print("STOMP WebSocket 로그인/로그아웃 테스트 시작")
    print(f"대상: {BASE_URL}")

    driver = create_driver()

    try:
        # 1. 로그인 페이지 열기
        print(f"\n브라우저 열기: {BASE_URL}")
        driver.get(BASE_URL)
        time.sleep(3)
        print(f"초기 URL: {driver.current_url}")
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_00_initial.png")

        # 2. space/123 로그인
        result1 = login(driver, "space", "123", "STEP 1")

        if not result1:
            print("\n[RESULT] STEP 1 실패 - space 로그인 후 튕김 발생!")
            return 1

        # 3. 로그아웃
        logout(driver)

        # 4. jyjang/123 로그인
        time.sleep(1)

        # 로그인 페이지가 아니면 이동
        if "/signin" not in driver.current_url and "/login" not in driver.current_url:
            driver.get(BASE_URL)
            time.sleep(2)

        result2 = login(driver, "jyjang", "123", "STEP 3")

        if not result2:
            print("\n[RESULT] STEP 3 실패 - jyjang 로그인 후 튕김 발생!")
            return 1

        # 최종 결과
        print(f"\n{'='*60}")
        print(f"[FINAL] 모든 테스트 통과!")
        print(f"  - space 로그인: PASS")
        print(f"  - 로그아웃: PASS")
        print(f"  - jyjang 로그인: PASS (튕김 없음)")
        print(f"{'='*60}")
        return 0

    except Exception as e:
        print(f"\n[ERROR] 테스트 중 예외: {e}")
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_exception.png")
        return 1
    finally:
        print("\n10초 후 브라우저 종료...")
        time.sleep(10)
        driver.quit()

if __name__ == "__main__":
    sys.exit(main())
