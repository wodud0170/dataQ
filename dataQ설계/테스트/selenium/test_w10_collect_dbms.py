"""
104번 WP4 보완 — MariaDB·SQL Server·Cubrid 구조 수집 (화면)

시험 DB(이 PC 의 Docker 컨테이너)가 필요하다: MariaDB `wp4-mariadb`(13306, DB wp4maria), SQL Server `wp4-mssql`(11433, wp4db 스키마 wp4),
Cubrid `cubrid-test`(33000, DB wp4utf 소유자 WP4CUB). 비밀번호는 환경 변수 W10_TESTDB_PW (저장소에 두지 않는다).
데이터소스·모델은 API 로 만들고 끝에서 지운다.

  K1~K3. 데이터 모델 관리 — 모델을 고르면 "스키마 수집 범위" 에 그 DB 의 사용자 스키마만(SQL Server 의 sys·guest 등은 없음),
         [수집] → [수집 진행] → "수집이 완료되었습니다." → 테이블 3·컬럼 12·인덱스·제약조건(FK 참조 대상 포함)
"""
import base64, os, subprocess, sys, time, traceback
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
PW = os.environ.get("W10_TESTDB_PW", "")
results = []
made = {"ds": [], "dm": []}

TARGETS = [
    ("MariaDB", {"dbmsTp": "MariaDB", "driverName": "MariaDB", "port": 13306, "userId": "root", "dbName": "wp4maria"}, "wp4maria"),
    ("SQLServer", {"dbmsTp": "SQLServer", "driverName": "SQLServer", "port": 11433, "userId": "sa", "dbName": "wp4db"}, "wp4"),
    ("Cubrid", {"dbmsTp": "Cubrid", "driverName": "Cubrid", "port": 33000, "userId": "wp4cub", "dbName": "wp4utf"}, "WP4CUB"),
]


# 탭 안 Vue 컴포넌트 — 자식부터 찾고 없으면 위로 (첫 요소가 keep-alive 로 잡히는 화면이 있다)
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")


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


def setup():
    s = requests.Session()
    s.post(BASE + "/login", data={"id": "space", "password": base64.b64encode(b"123").decode()}, allow_redirects=False, timeout=10)
    for label, body, schema in TARGETS:
        dsn = f"셀W10_{label}_{TAG}"
        s.post(BASE + "/api/sysinfo/createDataSource", json=dict(body, dsTp=0, svrAddr="127.0.0.1", dsn=dsn, pwd=PW), timeout=10)
        ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
        made["ds"].append(ds)
        nm = f"셀W10모델_{label}_{TAG}"
        s.post(BASE + "/api/dm/createDataModel", json={"dataModelNm": nm, "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT", "dataModelDsId": ds}, timeout=10)
        made["dm"].append(psql(f"select dm_id from quality.tb_data_model where dm_nm='{nm}'"))


def cleanup():
    for dm in made["dm"]:
        if not dm:
            continue
        for t in ("tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint", "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema"):
            psql(f"delete from quality.{t} where dm_id='{dm}'")
        psql(f"delete from quality.tb_data_model where dm_id='{dm}'")
    for ds in made["ds"]:
        if ds:
            psql(f"delete from quality.tb_gov_meta_db where ds_id='{ds}'")
            psql(f"delete from ndata.tb_data_source where ds_id='{ds}'")


def main():
    setup()
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1600, 900)

    def js(script, *args):
        return driver.execute_script(script, *args)

    try:
        driver.get(BASE)
        time.sleep(2)
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)
        g = driver.find_elements(By.CSS_SELECTOR, "#dmGroup .v-list-group__header")
        js("arguments[0].click();", g[0])
        time.sleep(1)
        js("document.getElementById('nav_datamodelCollection').click();")
        time.sleep(3)

        def make(i, label, schema):
            def _k():
                dm = made["dm"][i]
                nm = f"셀W10모델_{label}_{TAG}"
                js(VM + "const c = vm('tab_datamodelCollection', 'DSDatamodelCollection'); c.searchModel = arguments[0]; c.getDataModel();", nm)
                time.sleep(2.5)
                link = [a for a in driver.find_elements(By.CSS_SELECTOR, "#tab_datamodelCollection tbody td span.ndColor--text") if a.text.strip() == nm]
                assert link, "모델이 목록에 없음"
                js("arguments[0].click();", link[0])
                time.sleep(2.5)
                body = driver.find_element(By.ID, "tab_datamodelCollection").text
                i0 = body.find("스키마 수집 범위")
                scope = body[i0:i0 + 200]
                print(f"  스키마 범위={scope!r}")
                assert schema in scope and "sys" not in scope.split("\n")[2:] and "guest" not in scope
                cb = [e for e in driver.find_elements(By.CSS_SELECTOR, "#tab_datamodelCollection .v-input") if e.text.strip() == schema]
                assert cb, "스키마 체크박스가 없음"
                if "v-input--is-label-active" not in (cb[0].get_attribute("class") or ""):
                    js("arguments[0].querySelector('input').click();", cb[0])
                    time.sleep(1)
                btn = [b for b in driver.find_elements(By.CSS_SELECTOR, "#tab_datamodelCollection button") if b.text.strip() == "수집"]
                js("arguments[0].click();", btn[0])
                time.sleep(1.5)
                js("[...document.querySelectorAll('.swal2-confirm')].forEach(b => b.click());")
                WebDriverWait(driver, 90).until(lambda d: "수집이 완료되었습니다" in d.find_element(By.TAG_NAME, "body").text)
                cnt = psql(f"select (select count(*) from quality.tb_data_model_obj where dm_id='{dm}') || '|' || (select count(*) from quality.tb_data_model_attr where dm_id='{dm}') || '|' || "
                           f"(select count(*) from quality.tb_data_model_index where dm_id='{dm}') || '|' || "
                           f"(select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and constraint_type='R') || '|' || "
                           f"(select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and constraint_type='R' and ref_table_nm is not null)")
                print(f"  테이블|컬럼|인덱스 행|FK 행|참조 있는 FK 행 = {cnt}")
                o, a, ix, fk, fk_ref = (int(x) for x in cnt.split("|"))
                # 시험 DB 는 다른 시험(구조 진단)이 컬럼·FK 를 바꿔 둘 수 있어 테이블 수와 "FK 행 전부가 참조 대상을 가진다" 만 본다
                assert o == 3 and a >= 11 and ix >= 8 and fk >= 2 and fk_ref == fk, cnt
                for b in driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active button"):
                    if b.text.strip() == "닫기":
                        js("arguments[0].click();", b)
                time.sleep(1)
            return _k

        for i, (label, _b, schema) in enumerate(TARGETS):
            step(f"K{i + 1}. {label} — 스키마 범위·화면 수집·인덱스·FK 참조", make(i, label, schema))
    finally:
        driver.quit()
        cleanup()


if __name__ == "__main__":
    t0 = time.time()
    main()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({time.time() - t0:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
