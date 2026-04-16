"""
통합 검색 -> 사전 이동 테스트
로그인: space/123 @ localhost:28091

테스트 순서:
  1. 로그인
  2. 상단 통합 검색바에 "단어" 입력 → Enter
  3. 통합검색 탭에서 "단어등록TEST" 행 클릭
  4. 단어 사전 탭으로 이동, 검색조건에 "단어등록TEST" 세팅 + 데이터 조회 확인
"""
import time
import sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"


def create_driver():
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1400, 900)
    return driver


def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((by, value))
    )


def login(driver, user_id="space", password="123"):
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
    print(f"  [LOGIN] {user_id} 로그인 완료. URL: {driver.current_url}")
    return "/app/main" in driver.current_url


def main():
    print("=" * 60)
    print("통합 검색 -> 사전 이동 테스트")
    print("=" * 60)

    driver = create_driver()

    try:
        # 1. 로그인
        print("\n[STEP 1] space/123 로그인")
        if not login(driver):
            print("  [FAIL] 로그인 실패")
            return 1

        # 2. 상단 검색바에 "단어" 입력 후 Enter
        print("\n[STEP 2] 상단 통합 검색바에 '단어' 입력 + Enter")
        header_search = None
        inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='text']")
        for inp in inputs:
            ph = inp.get_attribute("placeholder") or ""
            if "통합" in ph or "검색" in ph:
                header_search = inp
                break

        if not header_search:
            print("  [FAIL] 상단 검색바 못 찾음")
            return 1

        header_search.clear()
        header_search.send_keys("단어")
        time.sleep(0.3)
        header_search.send_keys(Keys.RETURN)
        print("  '단어' 입력 후 Enter")

        # 통합검색 탭이 열리고 결과 로딩 대기
        time.sleep(4)
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_gs_step2.png")
        print("  스크린샷: test_gs_step2.png")

        # 3. "단어등록TEST" 행 클릭
        print("\n[STEP 3] 검색결과에서 '단어등록TEST' 클릭")

        target_found = False
        # expansion panel이 열려있을 수 있으니 단어 패널 클릭 시도
        try:
            panels = driver.find_elements(By.CSS_SELECTOR, ".v-expansion-panel-header")
            for panel in panels:
                if "단어" in panel.text:
                    # 이미 열려있을 수 있음, 클릭으로 토글
                    if "v-expansion-panel--active" not in panel.find_element(By.XPATH, "..").get_attribute("class"):
                        panel.click()
                        time.sleep(0.5)
                    break
        except:
            pass

        # 테이블에서 "단어등록TEST" 찾기
        time.sleep(1)
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  테이블 행 수: {len(rows)}")
        for row in rows:
            try:
                text = row.text
                if "단어등록TEST" in text:
                    row.click()
                    target_found = True
                    print(f"  '단어등록TEST' 행 클릭: {text[:60]}")
                    break
            except:
                continue

        if not target_found:
            # XPath로 시도
            try:
                cells = driver.find_elements(By.XPATH, "//td[contains(text(), '단어등록TEST')]")
                if cells:
                    cells[0].click()
                    target_found = True
                    print(f"  '단어등록TEST' 셀 클릭 (XPath)")
            except:
                pass

        if not target_found:
            print("  [FAIL] '단어등록TEST' 항목 못 찾음")
            # 디버깅: 테이블 내용 출력
            for i, row in enumerate(rows[:5]):
                print(f"    행[{i}]: {row.text[:80]}")
            driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_gs_step3_fail.png")
            return 1

        # 4. 단어 사전 탭 이동 + 검색조건 확인
        time.sleep(3)
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_gs_step4.png")

        print("\n[STEP 4] 단어 사전 탭 이동 확인")
        tab_found = False
        tabs = driver.find_elements(By.CSS_SELECTOR, ".v-tab")
        tab_texts = []
        for tab in tabs:
            t = tab.text.strip()
            tab_texts.append(t)
            if "단어" in t and "통합" not in t:
                tab_found = True
        print(f"  현재 탭 목록: {tab_texts}")
        print(f"  '단어' 탭 존재: {'PASS' if tab_found else 'FAIL'}")

        # 5. 검색조건 확인
        print("\n[STEP 5] 검색조건에 '단어등록TEST' 세팅 확인")

        search_value_found = False
        data_found = False

        # 모든 input 값 확인
        inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='text']")
        for inp in inputs:
            val = inp.get_attribute("value") or ""
            if "단어등록TEST" in val:
                search_value_found = True
                print(f"  [PASS] 검색조건: '{val}'")
                break

        if not search_value_found:
            print("  모든 input 값:")
            for i, inp in enumerate(inputs):
                val = inp.get_attribute("value") or ""
                ph = inp.get_attribute("placeholder") or ""
                if val:
                    print(f"    [{i}] value='{val}' placeholder='{ph}'")

        # 테이블에 "단어등록TEST" 데이터 확인
        time.sleep(1)
        rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
        for row in rows:
            if "단어등록TEST" in row.text:
                data_found = True
                print(f"  [PASS] 조회 결과에 '단어등록TEST' 확인")
                break

        if not data_found:
            print(f"  테이블 행 수: {len(rows)}")
            for i, r in enumerate(rows[:3]):
                print(f"    행[{i}]: {r.text[:80]}")

        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_gs_final.png")
        print(f"  최종 스크린샷: test_gs_final.png")

        # 결과
        print(f"\n{'=' * 60}")
        results = {
            "검색조건 세팅": search_value_found,
            "데이터 조회": data_found,
            "탭 이동": tab_found,
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
        driver.save_screenshot("C:/Users/장재영/Desktop/dataQ/test_gs_exception.png")
        return 1
    finally:
        print("\n10초 후 브라우저 종료...")
        time.sleep(10)
        driver.quit()


if __name__ == "__main__":
    sys.exit(main())
