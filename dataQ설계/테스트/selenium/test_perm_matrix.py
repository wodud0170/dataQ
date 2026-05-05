"""
권한 매트릭스 — admin (space) vs 일반 사용자 (jyjang)

검증:
  P1. /api/login/isAdmin — space=true / jyjang=false
  P2. UI — 관리자: [관리] 메뉴 표시
  P3. UI — 일반 사용자: [관리] 메뉴 미표시
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


def login_api(user, pwd="123"):
    s = requests.Session()
    enc = base64.b64encode(pwd.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def main():
    sa = login_api("space")
    su = login_api("jyjang")

    def _p1():
        ra = sa.get(BASE + "/api/login/isAdmin", params={"user": "space"}, timeout=10).json()
        ru = su.get(BASE + "/api/login/isAdmin", params={"user": "jyjang"}, timeout=10).json()
        print(f"  isAdmin: space={ra} / jyjang={ru}")
        assert ra is True
        assert ru is False
    step("P1. /api/login/isAdmin — admin 분기", _p1)

    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1600, 900)

    def _ui_login(user):
        driver.get(BASE + "/logout")
        time.sleep(1.5)
        driver.get(BASE)
        time.sleep(2)
        id_input = WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        id_input.clear(); id_input.send_keys(user)
        pw = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
        pw.clear(); pw.send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(2)

    try:
        def _p2():
            _ui_login("space")
            els = driver.find_elements(By.CSS_SELECTOR, "#mmGroup")
            visible = any(e.is_displayed() for e in els) if els else False
            print(f"  관리자 [관리] visible={visible} (요소수={len(els)})")
            assert visible, "관리자 — [관리] 그룹 보여야 함"
        step("P2. UI — 관리자 [관리] 메뉴 표시", _p2)

        def _p3():
            _ui_login("jyjang")
            els = driver.find_elements(By.CSS_SELECTOR, "#mmGroup")
            visible = any(e.is_displayed() for e in els) if els else False
            print(f"  일반 [관리] visible={visible} (요소수={len(els)})")
            assert not visible, "일반 사용자 — [관리] 그룹 안 보여야 함"
        step("P3. UI — 일반 사용자 [관리] 메뉴 미표시", _p3)
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
