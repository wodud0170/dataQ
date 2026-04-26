"""
Phase 4 커버리지 — UI 권한 게이트 (비관리자 jyjang)

시나리오:
  1. 일반 사용자 jyjang 로그인
  2. "관리" 대메뉴가 네비게이션에 **렌더되지 않아야** 함 (v-if="isAdmin")
  3. 혹시 렌더되더라도 nav_scheduleManage / nav_scheduleLog 아이템이 노출되지 않아야 함
"""
import os
import sys
import time
import traceback

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
PREFIX = "admin_gate_"
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def shot(d, name):
    d.save_screenshot(os.path.join(SCREENSHOT_DIR, PREFIX + name + ".png"))
    print(f"  [SHOT] {name}")


def login(d, user, pw):
    d.get(BASE + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw); pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(2)


def main():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    try:
        if not step("1. 일반 사용자 jyjang 로그인", lambda: login(d, "jyjang", "123")): return

        def _verify_admin_group_hidden():
            # mmGroup 자체가 v-if="isAdmin" 이므로 DOM 에 아예 없어야 함
            groups = d.find_elements(By.ID, "mmGroup")
            assert not groups, f"mmGroup 이 비관리자에게 보임 (elements={len(groups)})"
            # 혹시 숨김 스타일로만 처리했을 수도 있으니 safety check
            if groups:
                assert not groups[0].is_displayed(), "mmGroup is displayed for non-admin"
            shot(d, "01_jyjang_nav")
        if not step("2. '관리' 그룹(mmGroup) DOM 부재 확인", _verify_admin_group_hidden): return

        def _verify_schedule_nav_items_absent():
            sm = d.find_elements(By.ID, "nav_scheduleManage")
            sl = d.find_elements(By.ID, "nav_scheduleLog")
            assert not sm, f"nav_scheduleManage 가 비관리자에게 노출됨 ({len(sm)}개)"
            assert not sl, f"nav_scheduleLog 가 비관리자에게 노출됨 ({len(sl)}개)"
        if not step("3. nav_scheduleManage / nav_scheduleLog 부재 확인", _verify_schedule_nav_items_absent): return

        def _verify_api_still_rejects():
            # UI 는 숨어도 API 자체 권한이 지켜져야 함
            import requests, base64
            s = requests.Session()
            enc = base64.b64encode(b"123").decode()
            r = s.post(BASE + "/login", data={"id": "jyjang", "password": enc},
                       allow_redirects=False, timeout=10)
            assert r.status_code == 200 and r.json().get("success") is True
            r2 = s.post(BASE + "/api/diag/schedule/create",
                        json={"scheduleNm": "deny", "diagType": "STANDARD",
                              "dataModelId": "whatever",
                              "scheduleType": "SIMPLE", "repeatCycle": "DAILY",
                              "repeatTime": "04:00"},
                        timeout=10)
            b = r2.json()
            assert b.get("resultCode") == 403, f"API 도 거부해야 함: {b}"
        if not step("4. API 도 여전히 403 (2중 방어)", _verify_api_still_rejects): return

    finally:
        time.sleep(1)
        try: d.quit()
        except Exception: pass


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL (총 {len(results)})\n{'='*60}")
    for name, status, _ in results:
        print(f"  [{status}] {name}")
    sys.exit(0 if f == 0 else 1)
