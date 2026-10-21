"""
104번 WP2 — 역할·권한 (관리자가 아닌 사용자에게 역할로 권한을 준다)

검증:
  R1. myPerms — 관리자(space) 권한 코드 전부(/api/role/perms 목록 수) / 일반 사용자(jyjang) 없음
  R2. UI — 관리자: 관리 > 역할 및 권한 화면에 내장 역할 3개
  R3. 표준 담당 지정 → 일반 사용자 UI: [관리] 그룹에 '표준 사전 변경 승인'·'표준사전 관리'만 (사용자·역할 및 권한·데이터 소스 없음)
  R4. 표준 담당 해제 → 일반 사용자 UI: [관리] 그룹 미표시
"""
import base64, json, subprocess, sys, time, traceback
from dbcmd import PSQL
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
    except Exception:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login_api(user, pwd="123"):
    s = requests.Session()
    enc = base64.b64encode(pwd.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def clear_roles(user):
    subprocess.run(PSQL + ["-c",
                    f"DELETE FROM quality.tb_user_role WHERE user_id='{user}'"],
                   capture_output=True, text=True, timeout=20, encoding="utf-8", errors="replace")


def main():
    sa = login_api("space")
    su = login_api("jyjang")
    clear_roles("jyjang")

    def _r1():
        pa = sa.get(BASE + "/api/login/myPerms", timeout=10).json()
        pu = su.get(BASE + "/api/login/myPerms", timeout=10).json()
        print(f"  space admin={pa.get('admin')} perms={len(pa.get('perms', {}))} / jyjang admin={pu.get('admin')} perms={pu.get('perms')}")
        cat = json.loads(sa.get(BASE + "/api/role/perms", timeout=10).json()["contents"])
        assert pa.get("admin") is True and len(pa.get("perms", {})) == len(cat), f"권한 코드 {len(cat)}개와 다름"
        assert pu.get("admin") is False and pu.get("perms") == {}
    step("R1. myPerms — 관리자 전부 / 일반 사용자 없음", _r1)

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
        time.sleep(3)

    def _mm_items():
        """[관리] 그룹을 펼치고 보이는 메뉴 id 목록. 그룹이 없으면 None."""
        groups = [e for e in driver.find_elements(By.CSS_SELECTOR, "#mmGroup") if e.is_displayed()]
        if not groups:
            return None
        head = groups[0].find_element(By.CSS_SELECTOR, ".v-list-group__header")
        if "v-list-group__header--active" not in (head.get_attribute("class") or ""):
            head.click()
            time.sleep(1)
        return [e.get_attribute("id") for e in driver.find_elements(By.CSS_SELECTOR, "#mmGroup .v-list-item[id^='nav_']") if e.is_displayed()]

    try:
        def _r2():
            _ui_login("space")
            items = _mm_items()
            print(f"  관리자 [관리] 메뉴 = {items}")
            assert items and "nav_roles" in items and "nav_system" in items
            driver.find_element(By.ID, "nav_roles").click()
            time.sleep(2.5)
            body = driver.find_element(By.TAG_NAME, "body").text
            found = [n for n in ("표준 담당", "모델 담당", "DB 담당") if n in body]
            print(f"  역할 화면 내장 역할 = {found}")
            assert len(found) == 3
        step("R2. UI — 관리자 역할 및 권한 화면 (내장 역할 3개)", _r2)

        def _r3():
            r = sa.post(BASE + "/api/role/assign", json={"userId": "jyjang", "roleId": "BUILTIN_STD"}, timeout=10).json()
            assert r.get("resultCode") == 200, r
            _ui_login("jyjang")
            items = _mm_items()
            print(f"  표준 담당 [관리] 메뉴 = {items}")
            assert items == ["nav_approval", "nav_std_dict"] or items == ["nav_std_dict", "nav_approval"], items
        step("R3. 표준 담당 지정 → [관리] 에 표준 사전 변경 승인·표준사전 관리만", _r3)

        def _r4():
            r = sa.post(BASE + "/api/role/unassign", json={"userId": "jyjang", "roleId": "BUILTIN_STD", "sysCd": "*"}, timeout=10).json()
            assert r.get("resultCode") == 200, r
            _ui_login("jyjang")
            items = _mm_items()
            print(f"  해제 뒤 [관리] = {items}")
            assert items is None
        step("R4. 표준 담당 해제 → [관리] 그룹 미표시", _r4)
    finally:
        driver.quit()
        clear_roles("jyjang")


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
