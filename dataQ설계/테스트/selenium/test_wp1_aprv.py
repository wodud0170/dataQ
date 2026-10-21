"""
104번 WP1 — 결재선·알림·결재함

검증:
  A1. 결재선(단어, 1단계 관리자 전원)을 켜고 일반 사용자가 단어 등록 → 결재 문서 생성
  A2. UI — 관리자: 헤더 알림 배지 → 알림 누르면 결재함 상세 → [승인] → 단어 승인
  A3. UI — 요청자: 마이페이지 > 결재함 [내가 올린 결재] 에 '승인' 으로 보임
시험 결재선·단어·문서·알림은 끝에서 지운다.
"""
import base64, subprocess, sys, time, traceback
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
WORD = "셀결재" + TAG
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


def psql(sql):
    r = subprocess.run(PSQL + ["-At", "-c", sql],
                       capture_output=True, text=True, timeout=30, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


def login_api(user, pwd="123"):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": base64.b64encode(pwd.encode()).decode()}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def main():
    sa = login_api("space")
    su = login_api("jyjang")
    start = time.strftime("%Y%m%d%H%M%S")
    state = {}

    def _a1():
        r = sa.post(BASE + "/api/aprv/line/save", json={"reqTp": "WORD", "lineNm": "셀레니움", "useYn": "Y",
                                                        "steps": [{"stepNm": "관리자", "aprvrTp": "ADMIN", "agreeYn": "N"}]}, timeout=10).json()
        assert r.get("resultCode") == 200, r
        r = su.post(BASE + "/api/std/createWord", json={"wordNm": WORD, "wordEngAbrvNm": "SELAP" + TAG, "wordEngNm": "SELAPRV" + TAG,
                                                        "wordDesc": "셀레니움 결재", "wordClsfYn": "N", "dictId": "DEFAULT"}, timeout=10).json()
        wid = psql(f"select word_id from quality.tb_word where word_nm='{WORD}'")
        doc = psql(f"select doc_stat from quality.tb_aprv_doc where target_id='{wid}'")
        print(f"  등록={r.get('resultMessage')} 문서={doc}")
        assert doc == "PROGRESS"
        state["wid"] = wid
    step("A1. 결재선 켜고 단어 등록 → 결재 문서", _a1)

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

    try:
        def _a2():
            _ui_login("space")
            badge = driver.find_element(By.CSS_SELECTOR, "#headerNotiBtn .v-badge__badge").text
            print(f"  알림 배지={badge}")
            assert badge and int(badge.replace('+', '')) >= 1
            driver.execute_script("document.getElementById('headerNotiBtn').click();")
            time.sleep(1.5)
            items = [e for e in driver.find_elements(By.CSS_SELECTOR, ".noti-card .v-list-item") if WORD in e.text]
            assert items, "알림 목록에 결재 요청이 없음"
            driver.execute_script("arguments[0].click();", items[0])
            time.sleep(2.5)
            tab = driver.find_element(By.ID, "tab_aprv_box")
            assert WORD in tab.text, "결재함 상세에 대상이 안 보임"
            btn = [b for b in tab.find_elements(By.CSS_SELECTOR, "button") if b.text.strip() == "승인"][0]
            driver.execute_script("arguments[0].click();", btn)
            time.sleep(2.5)
            aprv = psql(f"select aprv_yn from quality.tb_word where word_id='{state['wid']}'")
            print(f"  단어 승인={aprv}")
            assert aprv == "Y"
        step("A2. UI — 관리자 알림 → 결재함 → 승인", _a2)

        def _a3():
            _ui_login("jyjang")
            groups = driver.find_elements(By.CSS_SELECTOR, "#myPageGroup .v-list-group__header")
            if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
                driver.execute_script("arguments[0].click();", groups[0])
                time.sleep(1)
            driver.execute_script("document.getElementById('nav_aprvBox').click();")
            time.sleep(2.5)
            tab = driver.find_element(By.ID, "tab_aprv_box")
            mine = [b for b in tab.find_elements(By.CSS_SELECTOR, "button") if "내가 올린 결재" in b.text][0]
            driver.execute_script("arguments[0].click();", mine)
            time.sleep(2)
            rows = [r.text for r in tab.find_elements(By.CSS_SELECTOR, "tbody tr") if WORD in r.text]
            print(f"  내가 올린 결재={rows}")
            assert rows and "승인" in rows[0]
        step("A3. UI — 요청자 결재함에서 승인 확인", _a3)
    finally:
        driver.quit()
        sa.post(BASE + "/api/aprv/line/save", json={"reqTp": "WORD", "useYn": "N", "steps": []}, timeout=10)
        psql("delete from quality.tb_aprv_line_step where req_tp='WORD'")
        psql("delete from quality.tb_aprv_line where req_tp='WORD'")
        psql(f"delete from quality.tb_notification where cret_dt >= '{start}'")
        psql(f"delete from quality.tb_aprv_doc where req_dt >= '{start}'")
        psql(f"delete from quality.tb_change_history where target_nm = '{WORD}'")
        psql(f"delete from quality.tb_aprv_stats where req_item_nm = '{WORD}'")
        psql(f"delete from quality.tb_word where word_nm = '{WORD}'")


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
