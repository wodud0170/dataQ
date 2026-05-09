"""
86번 #11 통합 테스트 v2 — 공통 helper

설계 원칙 (이전 회귀가 잡지 못한 한계 반복 안 하기):
  · 모든 액션 BEFORE / AFTER 두 시점에서 DB 직접 조회
  · UI 만 보지 않고 DB row 수치까지 비교 — "API 200 응답 = 통과" 아님
  · 같은 OBJ_NM 다른 OWNER 케이스 명시적 cover
  · 진단 결과 건수, 분리 매칭, cascade 모두 row 단위 검증
"""
import json
import os
import shlex
import subprocess
import sys
import time
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
REPORT_DIR = os.path.join(os.path.dirname(__file__), "reports")
PG_CONTAINER = "dataq-db"
PG_USER = "admin"
PG_DB = "postgres"
PG_SCHEMA = "quality"

os.makedirs(SCREENSHOT_DIR, exist_ok=True)
os.makedirs(REPORT_DIR, exist_ok=True)

# ============================ Selenium ============================

def create_driver(window=(1600, 1000)):
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    drv = webdriver.Edge(options=options)
    drv.set_window_size(*window)
    return drv

def wait_for(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(EC.presence_of_element_located((by, value)))

def wait_clickable(driver, by, value, timeout=15):
    return WebDriverWait(driver, timeout).until(EC.element_to_be_clickable((by, value)))

def screenshot(driver, name):
    path = os.path.join(SCREENSHOT_DIR, name if name.endswith(".png") else f"{name}.png")
    driver.save_screenshot(path)
    return path

def login_admin(driver, user="space", pwd="123"):
    """28091 직접 로그인 (8080 dev 우회 — admin session 필요)."""
    driver.get(BASE_URL)
    time.sleep(2)
    if "/signin" not in driver.current_url and "/login" not in driver.current_url:
        # 이미 로그인 상태일 수 있음
        if BASE_URL in driver.current_url:
            return True
    try:
        id_input = wait_for(driver, By.CSS_SELECTOR,
            "input[type='text'], input[name='username'], input#username, input#userId")
        id_input.clear(); id_input.send_keys(user)
        pw = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
        pw.clear(); pw.send_keys(pwd)
        btn = driver.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn")
        btn.click()
        time.sleep(3)
        # 5s anti-bounce
        for _ in range(10):
            time.sleep(0.5)
            if "/signin" in driver.current_url or "/login" in driver.current_url:
                return False
        return True
    except Exception as e:
        print(f"  [login fail] {e}")
        return False

_TAB_TO_NAV = {
    "tab_dashboard":              (None,              "nav_dashboard"),
    "tab_term":                   ("dsGroup",         "nav_term"),
    "tab_word":                   ("dsGroup",         "nav_word"),
    "tab_dsCode":                 ("dsGroup",         "nav_dsCode"),
    "tab_domain":                 ("dsGroup",         "nav_domain"),
    "tab_domainGroup":            ("dsGroup",         "nav_domainGroup"),
    "tab_domainClsf":             ("dsGroup",         "nav_domainClsf"),
    "tab_datamodelCollection":    ("dmGroup",         "nav_datamodelCollection"),
    "tab_datamodelStatusTable":   ("dmGroup",         "nav_datamodelStatusTable"),
    "tab_datamodelStatusColumn":  ("dmGroup",         "nav_datamodelStatusColumn"),
    "tab_datamodelStatusIndex":   ("dmGroup",         "nav_datamodelStatusIndex"),
    "tab_diagTargetMgmt":         ("dmGroup",         "nav_diagTargetMgmt"),
    "tab_dataDiag":               ("diagGroup",       "nav_dataDiag"),
    "tab_diagResult":             ("diagGroup",       "nav_dataDiagResult"),
    "tab_dataDiagResult":         ("diagGroup",       "nav_dataDiagResult"),
    "tab_structDiag":             ("structDiagGroup", "nav_structDiag"),
}

def navigate_to_tab(driver, tab_hash):
    """SPA 탭 이동 — 네비게이션 메뉴를 실제로 클릭해서 keep-alive 컴포넌트를 활성화 시킴.

    이전 방식 (hash 만 변경) 은 NdContent 의 activeContent 를 바꾸지 않아서 컴포넌트가 mount 안됨.
    """
    nav = _TAB_TO_NAV.get(tab_hash)
    if not nav:
        # fallback: 옛 방식 — hash 변경
        target = BASE_URL + "/app/main#" + tab_hash
        driver.get(target)
        time.sleep(2)
        return
    group_id, menu_id = nav
    # 메뉴 펼치기 (group_id 가 None 이면 펼칠 필요 없음)
    if group_id:
        try:
            menu_el = driver.find_element(By.ID, menu_id)
            if not menu_el.is_displayed():
                raise Exception("not visible")
        except Exception:
            try:
                grp = driver.find_element(By.ID, group_id)
                try: grp.click()
                except Exception: driver.execute_script("arguments[0].click();", grp)
                time.sleep(1)
            except Exception:
                pass
    # 메뉴 클릭
    menu_el = WebDriverWait(driver, 10).until(EC.visibility_of_element_located((By.ID, menu_id)))
    try: menu_el.click()
    except Exception: driver.execute_script("arguments[0].click();", menu_el)
    time.sleep(2)

def select_model_autocomplete(driver, value=None, timeout=10, wait_after=3.0):
    """모델 autocomplete 선택. value=None 이면 첫 의미있는 옵션. 성공시 True.

    test_obj_rename_cascade.py 의 select_autocomplete 패턴 사용 (.menuable__content__active)
    """
    acs = driver.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    if not acs:
        acs = driver.find_elements(By.CSS_SELECTOR, ".v-select input")
    if not acs:
        return False
    ac = acs[0]
    try:
        driver.execute_script("arguments[0].scrollIntoView({block:'center'});", ac)
    except Exception:
        pass
    try:
        ac.click()
    except Exception:
        driver.execute_script("arguments[0].click();", ac)
    time.sleep(0.6)
    if value:
        try:
            ac.send_keys(Keys.CONTROL, "a"); ac.send_keys(Keys.DELETE)
            ac.send_keys(value); time.sleep(1.0)
        except Exception:
            pass
    # 옵션 등장 대기
    deadline = time.time() + timeout
    items = []
    while time.time() < deadline:
        items = driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
        if not items:
            items = driver.find_elements(By.CSS_SELECTOR, "[role='option']")
        # 빈 항목·구분선 등 필터링
        items = [el for el in items
                 if (el.text or "").strip()
                 and "no-data" not in (el.get_attribute("class") or "").lower()
                 and "v-list-item--disabled" not in (el.get_attribute("class") or "")]
        if items: break
        time.sleep(0.4)
    if not items:
        return False
    target = items[0]
    if value:
        for it in items:
            if value in (it.text or ""):
                target = it; break
    try:
        target.click()
    except Exception:
        driver.execute_script("arguments[0].click();", target)
    time.sleep(wait_after)  # 모델 변경 → 데이터 로드 대기
    return True

def get_admin_session(driver):
    """Selenium 로그인 세션을 requests.Session 으로 변환 (admin API 호출용).

    NOTE: domain='localhost' 로 set 하면 requests 가 도메인 매칭 실패로 cookie 안 보냄.
    domain 인자 빼고 name/value 만 set 하면 모든 요청에 자동 포함됨.
    """
    s = requests.Session()
    for c in driver.get_cookies():
        s.cookies.set(c['name'], c['value'])
    return s

# ============================ DB (psql via docker) ============================

def db_query(sql):
    """SET search_path 적용 후 SQL 실행. JSON-friendly 한 줄씩 dict 리스트 반환."""
    full_sql = f"SET search_path TO {PG_SCHEMA}; {sql}"
    cmd = ["docker", "exec", "-i", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB,
           "-A", "-F", "|", "-t", "-c", full_sql]
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError(f"DB query failed: {proc.stderr}")
    rows = []
    for line in proc.stdout.strip().split("\n"):
        if not line: continue
        rows.append(line.split("|"))
    return rows

def db_count(table, where_clause=""):
    """간단 카운트. where_clause 는 'WHERE ...' 부분 제외하고 'col=val ...' 만."""
    sql = f"SELECT COUNT(*) FROM {table}"
    if where_clause:
        sql += f" WHERE {where_clause}"
    rows = db_query(sql)
    return int(rows[0][0]) if rows else 0

def db_one(sql):
    """단일 row 한 줄 반환 (없으면 None)."""
    rows = db_query(sql)
    return rows[0] if rows else None

# ============================ Test reporting ============================

class TestRun:
    def __init__(self, name):
        self.name = name
        self.steps = []
        self.start = time.time()
        self.passed = True

    def step(self, label, ok, detail=""):
        elapsed = time.time() - self.start
        mark = "✓" if ok else "✗"
        rec = {"label": label, "ok": ok, "detail": detail, "t": round(elapsed, 1)}
        self.steps.append(rec)
        if not ok:
            self.passed = False
        print(f"  [{mark} {elapsed:5.1f}s] {label}" + (f"  — {detail}" if detail else ""))

    def to_md(self):
        out = [f"## {self.name} {'✅ PASS' if self.passed else '❌ FAIL'}", ""]
        out.append("| 단계 | 결과 | 경과 | 상세 |")
        out.append("|---|---|---|---|")
        for s in self.steps:
            mark = "✓" if s["ok"] else "✗"
            out.append(f"| {s['label']} | {mark} | {s['t']}s | {s['detail']} |")
        out.append("")
        return "\n".join(out)


def write_report(runs, name="report.md"):
    path = os.path.join(REPORT_DIR, name)
    out = [f"# 86번 #11 통합 테스트 v2 리포트",
           f"실행: {time.strftime('%Y-%m-%d %H:%M:%S')}", ""]
    total = len(runs)
    passed = sum(1 for r in runs if r.passed)
    out.append(f"**{passed}/{total} 통과**")
    out.append("")
    for r in runs:
        out.append(r.to_md())
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(f"\n[report] {path}")
    return path
