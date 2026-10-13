"""
104번 WP7 — 표준 입력 통제·영향도 (유사어 → 권장, 용어 자동완성, 표준 후보, 표준 준수 강제, 영향도, 미사용 표준)

검증 (시험 사전·시스템·모델을 API·SQL 로 만들고 화면은 그 사전으로 연다 — 사전 선택은 세션스토리지):
  E1. 단어 등록 — 유사어를 넣으면 권장 단어 안내, [권장 단어 보기] → 목록을 권장 단어로 검색
  E2. 컬럼 — 한글명 입력 중 용어 자동완성을 고르면 영문명·타입·길이가 채워짐, [표준 후보 찾기] → [적용] → [저장] → 표준 Y
  E3. 컬럼 — 표준 준수 필수 모델에서 비표준 수정 저장 → 예외 신청 창 → 사유 → 결재 대상(DRAFT)·사유 저장
  E4. 미사용 표준 — 단어·용어·도메인 목록, 용어 영향도 다이얼로그
시험 데이터는 끝에서 지운다.
"""
import base64, json, subprocess, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
DICT_NM = f"셀WP7사전{TAG}"
SYS = f"SW7S{TAG}"
DM = f"SW7M{TAG}"
results = []
state = {}


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
    r = subprocess.run(["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres", "-At", "-c", sql],
                       capture_output=True, text=True, timeout=30, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("  [psql] " + (r.stderr or "").strip()[:200])
    return (r.stdout or "").strip()


def setup():
    s = requests.Session()
    s.post(BASE + "/login", data={"id": "space", "password": base64.b64encode(b"123").decode()}, allow_redirects=False, timeout=10)
    d = json.loads(s.post(BASE + "/api/dict/save", json={"dictNm": DICT_NM, "termLastWordClsfYn": "N"}, timeout=10).json()["contents"])["dictId"]
    state["dict"] = d
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    for sql in [
        f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('S7G{TAG}', '셀그룹7', 'N', '{d}')",
        f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values ('S7C{TAG}', '셀분류7', '셀그룹7', 'N', '{d}')",
        f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, dict_id, cret_dt) values "
        f"('S7D1{TAG}', '셀번호7', '셀그룹7', '셀분류7', 'x', 'VARCHAR', 20, 'Y', '{d}', {now}), ('S7D2{TAG}', '셀안쓰는도메인7', '셀그룹7', '셀분류7', 'x', 'CHAR', 1, 'Y', '{d}', {now})",
        f"insert into quality.tb_sys_info (sys_cd, sys_tp, sys_nm, cret_dt) values ('{SYS}', 1, '셀WP7시스템{TAG}', {now})",
        f"insert into quality.tb_data_model (dm_id, dm_nm, dm_sys_cd, ver, use_yn, dict_id, stnd_req_yn, cret_dt) values ('{DM}', '셀WP7모델{TAG}', '{SYS}', '1', 'Y', '{d}', 'N', {now})",
        f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) values ('{DM}', 'S7', 'S7_CUST', '고객', 2, 'Y')",
        f"insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, attr_ord, use_yn, terms_stnd_yn) values "
        f"('{DM}', 'S7', 'S7_CUST', 'CUST_NUM', '셀고객번호', 'VARCHAR', 30, 1, 'Y', 'N'), ('{DM}', 'S7', 'S7_CUST', 'MEMO', '메모', 'VARCHAR', 100, 2, 'Y', 'N')",
    ]:
        psql(sql)
    for nm, ab, syn in [("셀고객", "SCUST", ["셀손님"]), ("셀번호", "SNO", []), ("셀안쓰는단어", "SUNUSED", [])]:
        s.post(BASE + "/api/std/createWord", json={"wordNm": nm, "wordEngAbrvNm": ab, "wordEngNm": ab.title(), "wordDesc": nm,
                                                   "wordClsfYn": "N", "dictId": d, "allophSynmLst": syn}, timeout=10)

    def wid(nm):
        return psql(f"select word_id from quality.tb_word where dict_id='{d}' and word_nm='{nm}'")
    wl = [{"wordId": wid(w), "wordNm": w, "wordOrd": i} for i, w in enumerate(["셀고객", "셀번호"])]
    r = s.post(BASE + "/api/std/createTerms", json={"termsNm": "셀고객번호", "termsEngAbrvNm": "SCUST_SNO", "termsDesc": "x", "domainNm": "셀번호7",
                                                    "wordList": wl, "dictId": d}, timeout=10).json()
    assert r.get("resultCode") == 200, r


def cleanup():
    d = state.get("dict")
    for sql in [
        f"delete from quality.tb_data_model_change_history where dm_id='{DM}'",
        f"delete from quality.tb_term_resolve_history where dm_id='{DM}'",
        f"delete from quality.tb_data_model_attr where dm_id='{DM}'",
        f"delete from quality.tb_data_model_obj where dm_id='{DM}'",
        f"delete from quality.tb_data_model where dm_id='{DM}'",
        f"delete from quality.tb_sys_info where sys_cd='{SYS}'",
    ]:
        psql(sql)
    if d:
        for sql in [
            f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id='{d}')",
            f"delete from quality.tb_terms where dict_id='{d}'",
            f"delete from quality.tb_word where dict_id='{d}'",
            f"delete from quality.tb_domain where dict_id='{d}'",
            f"delete from quality.tb_domain_clsf where dict_id='{d}'",
            f"delete from quality.tb_domain_grp where dict_id='{d}'",
            f"delete from quality.tb_std_dict where dict_id='{d}'",
            "delete from quality.tb_change_history where target_nm in ('셀고객','셀번호','셀안쓰는단어','셀고객번호')",
        ]:
            psql(sql)


def main():
    setup()
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1600, 900)

    def js(script, *args):
        return driver.execute_script(script, *args)

    def open_menu(group_id, nav_id):
        groups = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
        if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
            js("arguments[0].click();", groups[0])
            time.sleep(1)
        js(f"document.getElementById('{nav_id}').click();")
        time.sleep(2.5)

    def swal_title():
        e = driver.find_elements(By.CSS_SELECTOR, ".swal2-title")
        return e[0].text if e else ""

    def col_vm():
        return "let c = document.getElementById('clTable_table').__vue__; while (c && !('candDialog' in c.$data)) c = c.$parent;"

    try:
        driver.get(BASE)
        time.sleep(2)
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)
        js("sessionStorage.setItem('navidmeta.dictId', arguments[0]);", state["dict"])

        def _e1():
            open_menu("dsGroup", "nav_word")
            btn = [b for b in driver.find_elements(By.CSS_SELECTOR, "#tab_word button") if b.text.strip() == "등록"][0]
            js("arguments[0].click();", btn)
            time.sleep(1)
            nm = driver.find_element(By.ID, "addWordNm")
            nm.send_keys("셀손님")
            nm.send_keys(Keys.TAB)
            time.sleep(2)
            msg = driver.find_element(By.ID, "addWordNmCheck").text
            print(f"  안내={msg}")
            assert "유사어" in msg and "셀고객" in msg
            js("document.querySelector('#addWordNmCheck a').click();")
            time.sleep(2)
            rows = [r.text for r in driver.find_elements(By.CSS_SELECTOR, "#tab_word tbody tr") if "SCUST" in r.text]
            print(f"  목록={rows[:1]}")
            assert rows and not driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        step("E1. 단어 등록 — 유사어 → 권장 단어 안내·검색", _e1)

        def _e2():
            open_menu("dmGroup", "nav_datamodelStatusColumn")
            js(col_vm() + "c.selectedModelId = arguments[0]; c.onModelChange(arguments[0]);", DM)
            time.sleep(3)
            memo = [i for i in driver.find_elements(By.CSS_SELECTOR, "#clTable_table tbody input") if i.get_attribute("value") == "메모"][0]
            js("arguments[0].select();", memo)
            memo.send_keys(Keys.CONTROL, "a")
            memo.send_keys("셀고객")
            time.sleep(2)
            items = [i for i in driver.find_elements(By.CSS_SELECTOR, ".term-suggest-list .v-list-item") if "SCUST_SNO" in i.text]
            assert items, "자동완성 목록 없음"
            js("arguments[0].click();", items[0])
            time.sleep(1)
            filled = js(col_vm() + "const r = c.mergedItems.find(x => x._orig && x._orig.attrNm === 'MEMO'); return [r.attrNmKr, r.attrNm, r.dataType, r.dataLen];")
            print(f"  자동완성으로 채운 행={filled}")
            assert filled == ["셀고객번호", "SCUST_SNO", "VARCHAR", 20]
            js(col_vm() + "c.load();")       # 변경 버림 — 같은 테이블에 같은 영문명이 생기지 않게
            time.sleep(2.5)
            js("document.getElementById('btn-term-cand').click();")
            time.sleep(3)
            cand = [r.text.replace("\n", " ") for r in driver.find_elements(By.CSS_SELECTOR, "#candTable tbody tr")]
            print(f"  후보={cand}")
            assert any("CUST_NUM" in t and "셀고객번호" in t and "한글명 일치" in t for t in cand)
            js(col_vm() + "c.candRows.forEach(r => { if (r.attrNm !== 'CUST_NUM') r.pick = null; });")
            js("document.getElementById('btn-cand-apply').click();")
            time.sleep(2.5)
            js("document.getElementById('btn-save-attrs').click();")
            time.sleep(1.5)
            if "cascade" in swal_title():
                js("document.querySelector('.swal2-confirm').click();")
            time.sleep(3)
            v = psql(f"select attr_nm || ':' || attr_nm_kr || ':' || terms_stnd_yn from quality.tb_data_model_attr where dm_id='{DM}' and attr_ord=1")
            print(f"  저장={v}")
            assert v == "SCUST_SNO:셀고객번호:Y"
        step("E2. 컬럼 — 용어 자동완성, 표준 후보 적용·저장", _e2)

        def _e3():
            psql(f"update quality.tb_data_model set stnd_req_yn='Y' where dm_id='{DM}'")
            js(col_vm() + "c.getModelList(); c.load();")
            time.sleep(3)
            assert driver.find_elements(By.ID, "colStndReqChip"), "표준 준수 필수 표시 없음"
            js(col_vm() + "const r = c.mergedItems.find(x => x.attrNm === 'MEMO'); r.attrNmKr = '메모내용';")
            time.sleep(0.5)
            js("document.getElementById('btn-save-attrs').click();")
            time.sleep(2.5)
            t = swal_title()
            print(f"  거부 창={t}")
            assert "표준 준수 필수" in t
            ta = driver.find_element(By.CSS_SELECTOR, ".swal2-textarea")
            ta.send_keys("셀레니움 예외 사유")
            js("document.querySelector('.swal2-confirm').click();")
            time.sleep(3)
            t2 = swal_title()
            h = psql(f"select aprv_status || '|' || coalesce(stnd_except_reason,'-') from quality.tb_data_model_change_history where dm_id='{DM}' and attr_nm='MEMO' order by change_seq desc limit 1")
            print(f"  결과 창={t2} 이력={h}")
            assert "표준 예외로 저장" in t2 and h == "DRAFT|셀레니움 예외 사유"
            if driver.find_elements(By.CSS_SELECTOR, ".swal2-confirm"):
                js("document.querySelector('.swal2-confirm').click();")
        step("E3. 컬럼 — 표준 준수 필수, 예외 신청", _e3)

        def _e4():
            open_menu("dsGroup", "nav_unusedStd")
            got = {}
            for k in ["WORD", "TERM", "DOMAIN"]:
                js("let c = document.getElementById('unusedTable').__vue__; while (c && !('aprvOnly' in c.$data)) c = c.$parent; c.dictId = arguments[0]; c.kind = arguments[1]; c.load();", state["dict"], k)
                time.sleep(2)
                got[k] = [r.text.split("\n")[0] for r in driver.find_elements(By.CSS_SELECTOR, "#unusedTable tbody tr")]
            print(f"  미사용={got}")
            assert got["WORD"][0].startswith("셀안쓰는단어") and got["DOMAIN"][0].startswith("셀안쓰는도메인7") and got["TERM"] and "쓰이지 않는 표준이 없습니다" in got["TERM"][0]
            open_menu("dsGroup", "nav_term")
            js("let c = document.querySelector('#tab_term .v-data-table').__vue__; while (c && !('impactShow' in c.$data)) c = c.$parent; c.dictId = arguments[0]; c.getTermData();", state["dict"])
            rows = []
            for _ in range(10):
                time.sleep(1)
                rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "#tab_term tbody tr") if r.text.startswith("셀고객번호")]
                if rows:
                    break
            if not rows:
                print("  용어 목록=", [r.text[:40] for r in driver.find_elements(By.CSS_SELECTOR, "#tab_term tbody tr")][:3])
            row = rows[0]
            js("const el = [...arguments[0].querySelectorAll('*')].find(e => e.children.length === 0 && e.innerText && e.innerText.trim() === '셀고객번호'); el.click();", row)
            time.sleep(1.5)
            js("document.getElementById('termImpactBtn').click();")
            time.sleep(2.5)
            cols = [r.text.replace("\n", " ") for r in driver.find_elements(By.CSS_SELECTOR, "#impactColumnTable tbody tr")]
            print(f"  영향도={cols}")
            assert any("SCUST_SNO" in c and "영문명" in c for c in cols)
        step("E4. 미사용 표준, 용어 영향도", _e4)
    finally:
        driver.quit()
        cleanup()


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
