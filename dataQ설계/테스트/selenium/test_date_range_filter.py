"""
용어/단어/코드 화면의 등록일자 검색조건(범위) 테스트

검증 항목:
  V1. 백엔드 SQL 필터 동작 — getTermsList/getWordList/getCodeInfoList
       - 무필터 vs 과거 범위(0건 기대) vs 광역 범위(전체 또는 동일)
  V2. 프론트 UI — 용어/단어/코드 화면에 등록일자 (from/to) input 존재
"""
import base64, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def api_login():
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"login failed: {r.status_code}"
    return s


def api_filter_test(s, endpoint, label, body_extra={}):
    """공통 패턴: 무필터 vs 과거-과거 vs 광역 비교"""
    url = BASE + endpoint
    # 무필터
    r0 = s.post(url, json={**body_extra, "schAprvYn": "Y"}, timeout=10)
    n0 = len(r0.json() or [])
    # 과거 범위 (1900~1901)
    r1 = s.post(url, json={**body_extra, "schAprvYn": "Y",
                            "from": "19000101000000", "to": "19010101235959"}, timeout=10)
    n1 = len(r1.json() or [])
    # 광역 범위 (1900~2099)
    r2 = s.post(url, json={**body_extra, "schAprvYn": "Y",
                            "from": "19000101000000", "to": "20991231235959"}, timeout=10)
    n2 = len(r2.json() or [])
    # 미래 범위 (2099)
    r3 = s.post(url, json={**body_extra, "schAprvYn": "Y",
                            "from": "20990101000000", "to": "20991231235959"}, timeout=10)
    n3 = len(r3.json() or [])

    print(f"  [{label}] 무필터={n0} 과거(0기대)={n1} 광역(=전체)={n2} 미래(0기대)={n3}")
    assert n1 == 0, f"{label} 과거 범위 0건 기대, 실제 {n1}"
    assert n3 == 0, f"{label} 미래 범위 0건 기대, 실제 {n3}"
    assert n2 == n0, f"{label} 광역 범위 = 무필터 기대, 실제 {n2} vs {n0}"


def main():
    s = api_login()

    # V1-1. 용어 (getTermsList)
    step("V1-1. 용어 등록일자 필터 — selectTermsList",
         lambda: api_filter_test(s, "/api/std/getTermsList", "용어"))

    # V1-2. 단어 (getWordList)
    step("V1-2. 단어 등록일자 필터 — selectWordList",
         lambda: api_filter_test(s, "/api/std/getWordList", "단어"))

    # V1-3. 코드 (getCodeInfoList)
    step("V1-3. 코드 등록일자 필터 — selectCodeInfoList",
         lambda: api_filter_test(s, "/api/std/getCodeInfoList", "코드"))

    # V2. UI — 셀레니움으로 각 화면의 등록일자 input 존재 확인
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1600, 900)

    try:
        def _login_ui():
            driver.get(BASE)
            time.sleep(2)
            id_input = WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
            id_input.send_keys("space")
            pw = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
            pw.send_keys("123")
            driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
            WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
            time.sleep(2)

        _login_ui()

        def _open_dsGroup():
            # 그룹 child 가 이미 visible 이면 아무 안 함 (idempotent)
            try:
                items = driver.find_elements(By.CSS_SELECTOR, "#nav_term")
                if items and items[0].is_displayed():
                    return
                grp = driver.find_element(By.CSS_SELECTOR, "#dsGroup .v-list-group__header")
                grp.click()
                time.sleep(1)
            except Exception:
                pass

        def _verify_date_inputs(nav_id, label):
            _open_dsGroup()
            link = WebDriverWait(driver, 10).until(EC.element_to_be_clickable((By.CSS_SELECTOR, f"#{nav_id}")))
            link.click()
            time.sleep(2)
            # type=date input 2개 이상 존재 확인
            inputs = driver.find_elements(By.CSS_SELECTOR, "input[type='date']")
            cnt = len(inputs)
            print(f"  [{label}] type=date input 개수 = {cnt}")
            assert cnt >= 2, f"{label} 화면에 date input 2개 이상 기대, 실제 {cnt}"

        step("V2-1. 용어 화면 — 등록일자 input 존재",
             lambda: _verify_date_inputs("nav_term", "용어"))
        step("V2-2. 단어 화면 — 등록일자 input 존재",
             lambda: _verify_date_inputs("nav_word", "단어"))
        step("V2-3. 코드 화면 — 등록일자 input 존재",
             lambda: _verify_date_inputs("nav_dsCode", "코드"))

    finally:
        driver.quit()


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
