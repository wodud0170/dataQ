"""
104번 WP3 — DBMS 별 DDL·DB 반영

검증:
  D1. DDL 규칙 API — 규칙 DBMS 5종, Oracle 매핑 행 있음, 일반 사용자 403
  D2. UI — 관리자: 관리 > DDL 규칙 화면에 PK 이름 규칙·매핑 목록
  D3. UI — 일반 사용자: DDL 규칙 메뉴 없음
  D4. [DB 반영] — 개발 DB 에 임시 스키마를 만들어 PostgreSQL 데이터소스·모델로 테이블 추가 → CREATE TABLE SUCCESS,
      뒤 컬럼 추가는 포함 처리, 실제 테이블 생성 확인. 임시 스키마·계정·데이터는 끝에서 지운다
"""
import base64, secrets, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
SCHEMA = "wp3sel" + TAG
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
    enc = base64.b64encode(pwd.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def main():
    sa = login_api("space")
    su = login_api("jyjang")

    def _d1():
        r = sa.get(BASE + "/api/ddlRule/typeMap/list", params={"dbmsTp": "Oracle"}, timeout=10).json()
        import json
        c = json.loads(r["contents"])
        print(f"  DBMS={c['dbmsList']} Oracle 행={len(c['items'])}")
        assert c["dbmsList"] == ["Oracle", "Tibero", "PostgreSQL", "MariaDB", "SQLServer"]
        assert len(c["items"]) > 0
        ru = su.get(BASE + "/api/ddlRule/typeMap/list", timeout=10).json()
        assert ru.get("resultCode") == 403, ru
    step("D1. DDL 규칙 API — DBMS 5종·매핑·일반 사용자 403", _d1)

    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=headless(options))
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

    def _open_mm():
        for g in driver.find_elements(By.CSS_SELECTOR, "#mmGroup .v-list-group__header"):
            if "v-list-group__header--active" not in (g.get_attribute("class") or ""):
                driver.execute_script("arguments[0].click();", g)
                time.sleep(1)

    try:
        def _d2():
            _ui_login("space")
            _open_mm()
            driver.execute_script("arguments[0].click();", driver.find_element(By.ID, "nav_ddlRule"))
            time.sleep(3)
            tab = driver.find_element(By.ID, "tab_ddl_rule")
            pk = tab.find_element(By.CSS_SELECTOR, "input[placeholder='PK_{TABLE}']").get_attribute("value")
            rows = tab.find_elements(By.CSS_SELECTOR, "tbody tr")
            print(f"  PK 이름 규칙={pk} 매핑 행={len(rows)}")
            assert "{TABLE}" in pk and len(rows) > 0
        step("D2. UI — 관리자 DDL 규칙 화면", _d2)

        def _d3():
            _ui_login("jyjang")
            _open_mm()
            found = driver.find_elements(By.ID, "nav_ddlRule")
            print(f"  일반 사용자 DDL 규칙 메뉴 = {len(found)}")
            assert not found
        step("D3. UI — 일반 사용자 DDL 규칙 메뉴 없음", _d3)
    finally:
        driver.quit()

    ds_nm = "WP3셀레니움_" + TAG
    dm_nm = "WP3셀레니움모델_" + TAG
    pw = "Wp3" + secrets.token_hex(4)
    try:
        def _d4():
            psql(f"create role {SCHEMA} login password '{pw}'")
            psql(f"create schema {SCHEMA} authorization {SCHEMA}")
            sa.post(BASE + "/api/sysinfo/createDataSource", json={"dsn": ds_nm, "dsTp": 0, "dbmsTp": "PostgreSQL", "driverName": "PostgreSQL",
                                                                 "svrAddr": "127.0.0.1", "port": 25433, "userId": SCHEMA, "pwd": pw, "dbName": "postgres"}, timeout=10)
            ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{ds_nm}'")
            sa.post(BASE + "/api/dm/createDataModel", json={"dataModelNm": dm_nm, "dataModelDsId": ds, "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT"}, timeout=10)
            dm = psql(f"select dm_id from quality.tb_data_model where dm_nm='{dm_nm}'")
            sa.post(BASE + "/api/dm/addObj", json={"dataModelId": dm, "objOwner": SCHEMA, "objNm": "SEL_T1", "objNmKr": "셀레니움"}, timeout=10)
            sa.post(BASE + "/api/dm/saveAttrs", json={"dataModelId": dm, "objNm": "SEL_T1", "objOwner": SCHEMA, "attrs": [
                {"mode": "ADD", "attrNm": "COL_A", "attrNmKr": "가", "dataType": "VARCHAR", "dataLen": 20, "nullableYn": "Y"}]}, timeout=10)
            seq = psql(f"select change_seq from quality.tb_data_model_change_history where dm_id='{dm}' and change_type='ADD_OBJ'")
            r = sa.post(BASE + "/api/dmApproval/execDdl", json={"changeSeqList": [int(seq)]}, timeout=30).json()
            print(f"  execDdl = {r.get('contents')}")
            left = psql(f"select count(*) from quality.tb_data_model_change_history where dm_id='{dm}' and ddl_exec_dt is null")
            col = psql(f"select column_name from information_schema.columns where table_schema='{SCHEMA}' and table_name='sel_t1'")
            print(f"  미반영 이력={left} 생성된 컬럼={col}")
            assert '"success":1' in (r.get("contents") or "") and left == "0" and col == "col_a"
        step("D4. [DB 반영] — PostgreSQL 신규 테이블 CREATE TABLE", _d4)
    finally:
        dm = psql(f"select dm_id from quality.tb_data_model where dm_nm='{dm_nm}'")
        if dm:
            psql(f"delete from quality.tb_data_model_change_history where dm_id='{dm}'")
            psql(f"delete from quality.tb_data_model_attr where dm_id='{dm}'")
            psql(f"delete from quality.tb_data_model_obj where dm_id='{dm}'")
            psql(f"delete from quality.tb_data_model where dm_id='{dm}'")
        psql(f"delete from ndata.tb_data_source where dsn='{ds_nm}'")
        psql(f"drop schema if exists {SCHEMA} cascade")
        psql(f"drop role if exists {SCHEMA}")


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
