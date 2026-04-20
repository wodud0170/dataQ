"""
CLCT 폐기 이관 후 주요 기능 검증 테스트
테스트 항목:
  1. 로그인
  2. 대시보드 데이터 모델 현황 표시 확인 (STATS 폐기 후 실시간 COUNT)
  3. 데이터 모델 > 테이블 조회 (수집일시 드롭다운 없이 DM_ID 직접)
  4. 데이터 모델 > 컬럼 조회
  5. 구조 변경 진단 > 진단 실행 화면 (수집일시 없이 모델만 선택)
"""
import time
import sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"
SCREENSHOT_DIR = "dataQ설계/테스트/selenium/screenshots/"

def create_driver():
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1920, 1080)
    return driver

def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, value))
    )

def wait_clickable(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.element_to_be_clickable((by, value))
    )

def login(driver, user_id="space", password="123"):
    driver.get(BASE_URL + "/signin")
    time.sleep(2)
    id_input = wait_for(driver, By.CSS_SELECTOR, "input[type='text']")
    id_input.clear()
    id_input.send_keys(user_id)
    pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_input.clear()
    pw_input.send_keys(password)
    login_btn = driver.find_element(By.CSS_SELECTOR, "button[type='submit'], .login-btn, button")
    login_btn.click()
    time.sleep(3)

def save_screenshot(driver, name):
    driver.save_screenshot(SCREENSHOT_DIR + name)
    print(f"  [SCREENSHOT] {name}")

def click_nav_menu(driver, menu_id):
    """네비게이션 메뉴 클릭"""
    try:
        menu = wait_clickable(driver, By.ID, menu_id)
        menu.click()
        time.sleep(2)
    except Exception as e:
        print(f"  [WARN] 메뉴 클릭 실패 ({menu_id}): {e}")

def click_nav_group(driver, group_id):
    """네비게이션 그룹 열기"""
    try:
        group = wait_clickable(driver, By.ID, group_id)
        group.click()
        time.sleep(1)
    except Exception as e:
        print(f"  [WARN] 그룹 클릭 실패 ({group_id}): {e}")

results = []

def test(name, fn):
    print(f"\n{'='*60}")
    print(f"TEST: {name}")
    print(f"{'='*60}")
    try:
        fn()
        results.append((name, "PASS"))
        print(f"  >> PASS")
    except Exception as e:
        results.append((name, f"FAIL: {e}"))
        print(f"  >> FAIL: {e}")

def main():
    driver = create_driver()

    try:
        # 1. 로그인
        def test_login():
            login(driver, "space", "123")
            assert "/app/main" in driver.current_url or "/main" in driver.current_url, \
                f"로그인 후 메인 페이지가 아님: {driver.current_url}"
            save_screenshot(driver, "clct_01_login.png")

        test("1. 로그인", test_login)

        # 2. 대시보드 확인 (STATS 폐기 후 데이터 표시)
        def test_dashboard():
            time.sleep(3)
            page_source = driver.page_source
            # 대시보드에 데이터 모델 관련 숫자가 표시되는지
            assert "데이터 모델" in page_source or "테이블" in page_source or "컬럼" in page_source, \
                "대시보드에 데이터 모델 현황이 표시되지 않음"
            save_screenshot(driver, "clct_02_dashboard.png")

        test("2. 대시보드 데이터 모델 현황", test_dashboard)

        # 3. 데이터 모델 > 테이블 조회
        def test_table_view():
            click_nav_group(driver, "dmGroup")
            time.sleep(1)
            click_nav_menu(driver, "nav_datamodelStatusTable")
            time.sleep(2)
            page_source = driver.page_source
            # 수집일시 드롭다운이 없어야 함
            assert "수집일시" not in page_source, "수집일시 드롭다운이 아직 남아있음"
            # 데이터모델명 선택 드롭다운은 있어야 함
            assert "데이터모델명" in page_source or "모델 선택" in page_source, \
                "데이터모델명 선택 UI가 없음"
            save_screenshot(driver, "clct_03_table_view.png")

        test("3. 테이블 조회 (수집일시 드롭다운 제거 확인)", test_table_view)

        # 4. 데이터 모델 > 컬럼 조회
        def test_column_view():
            click_nav_menu(driver, "nav_datamodelStatusColumn")
            time.sleep(2)
            page_source = driver.page_source
            assert "수집일시" not in page_source, "컬럼 화면에 수집일시 드롭다운이 남아있음"
            save_screenshot(driver, "clct_04_column_view.png")

        test("4. 컬럼 조회 (수집일시 드롭다운 제거 확인)", test_column_view)

        # 5. 구조 변경 진단 > 진단 실행
        def test_struct_diag():
            click_nav_group(driver, "structDiagGroup")
            time.sleep(1)
            click_nav_menu(driver, "nav_structDiag")
            time.sleep(2)
            # 수집일시 드롭다운(v-select)이 필터 바에 없는지 확인
            # 이력 그리드 헤더의 "수집일시"는 허용
            filter_area = driver.find_elements(By.CSS_SELECTOR, ".filterLabel")
            filter_labels = [el.text for el in filter_area]
            assert "수집일시" not in filter_labels, \
                f"구조진단 필터에 수집일시 드롭다운이 남아있음: {filter_labels}"
            save_screenshot(driver, "clct_05_struct_diag.png")

        test("5. 구조진단 (수집일시 드롭다운 제거 확인)", test_struct_diag)

    finally:
        driver.quit()

    # 결과 출력
    print(f"\n{'='*60}")
    print("테스트 결과 요약")
    print(f"{'='*60}")
    passed = 0
    failed = 0
    for name, status in results:
        icon = "✅" if status == "PASS" else "❌"
        print(f"  {icon} {name}: {status}")
        if status == "PASS":
            passed += 1
        else:
            failed += 1
    print(f"\n  총 {len(results)}건: PASS {passed}, FAIL {failed}")

    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
