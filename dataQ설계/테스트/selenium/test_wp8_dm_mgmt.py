"""
104번 WP8 — 데이터 모델 관리 보강 (38 주제영역, 63 구조 진단 결과로 현행화, 31·75 사용자 정의 속성)

검증 (시험 모델·영역·속성은 API·SQL 로 만들고 끝에서 지운다):
  G1. 관리 > 사용자 정의 속성 — [속성 추가] 에서 이름 넣고 "필수" 켜고 저장 → 정의 저장(필수)
  G2. 데이터 모델 > 테이블 — [테이블 추가] 창에 필수 속성(*) 칸, 비우고 추가하면 "필수 관리 항목" 으로 거부,
      값 넣고 주제영역 골라 추가하면 저장 → 목록 "주제영역" 열에 경로, "관리 항목" 열에 값 요약
  G3. 관리 > 영역 관리 [주제영역] — 3단계 트리(들여쓰기), 영역을 고르고 [테이블 연결] 로 테이블을 연결 → 연결된 테이블 목록
  G4. 구조 변경 진단 > 결과 — 컬럼 변경 행을 모두 골라 [모델에 반영] → 모델에 추가·변경·삭제가 반영되고 행에 "반영됨", 다시 고를 수 없음
      (진단 결과는 논리 모델에 SQL 로 만든다 — 반영은 실 DB 에 접속하지 않는다)
"""
import base64, json, subprocess, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
results = []
D = {"subj": []}
UDP_NM = f"셀담당{TAG}"


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

    def subj(nm, parent=None):
        r = s.post(BASE + "/api/area/subj/save", json={"subjAreaNm": nm, "parentId": parent, "sortOrder": 1}, timeout=10).json()
        i = json.loads(r["contents"])["subjAreaId"]
        D["subj"].append(i)
        return i
    D["sa"] = subj(f"셀주제{TAG}_A")
    D["sb"] = subj(f"셀주제{TAG}_B", D["sa"])
    D["sc"] = subj(f"셀주제{TAG}_C", D["sb"])

    nm = f"셀WP8모델{TAG}"
    s.post(BASE + "/api/dm/createDataModel", json={"dataModelNm": nm, "modelType": "LOGICAL", "ver": "1", "dictId": "DEFAULT"}, timeout=10)
    D["dm"] = dm = psql(f"select dm_id from quality.tb_data_model where dm_nm='{nm}'")
    D["dmNm"] = nm
    r = s.post(BASE + "/api/dm/addObj", json={"dataModelId": dm, "objOwner": "APP", "objNm": "CELL_T", "objNmKr": "셀테이블"}, timeout=10).json()
    assert r.get("resultCode") == 200, r
    r = s.post(BASE + "/api/dm/saveAttrs", json={"dataModelId": dm, "objNm": "CELL_T", "objOwner": "APP", "attrs": [
        {"mode": "ADD", "attrNm": "ID", "attrNmKr": "아이디", "dataType": "NUMBER", "dataLen": 10, "pkYn": "Y"},
        {"mode": "ADD", "attrNm": "NM", "attrNmKr": "이름", "dataType": "VARCHAR", "dataLen": 50},
        {"mode": "ADD", "attrNm": "AMT", "attrNmKr": "금액", "dataType": "NUMBER", "dataLen": 12}]}, timeout=10).json()
    assert r.get("resultCode") == 200, r

    # 구조 진단 결과 (반영 전) — 추가 2(새 테이블 1)·변경 1·삭제 1
    D["diag"] = did = f"CELLWP8{TAG}"
    psql(f"insert into quality.tb_struct_diag_history (diag_id, data_model_id, status, diag_dt, cret_user_id, total_tables, total_columns, "
         f"added_tables, added_columns, modified_columns, deleted_tables, deleted_columns, total_indexes, added_indexes, modified_indexes, deleted_indexes, "
         f"total_constraints, added_constraints, modified_constraints, deleted_constraints) "
         f"values ('{did}', '{dm}', 'DONE', now(), 'space', 2, 4, 1, 2, 1, 0, 1, 0,0,0,0, 0,0,0,0)")
    rows = [
        (1, "CELL_T", "MEMO", "ADDED", None, "VARCHAR", None, 200, None, "Y", "메모", None, "N", None, 4),
        (2, "CELL_T", "NM", "MODIFIED", "VARCHAR", "VARCHAR", 50, 100, "Y", "Y", "이름", None, "N", None, 2),
        (3, "CELL_T", "AMT", "DELETED", "NUMBER", None, 12, None, "Y", None, None, None, None, None, None),
        (4, "CELL_NEW", "K", "ADDED", None, "NUMBER", None, 5, None, "N", "키", None, "Y", None, 1),
    ]
    q = lambda v: "null" if v is None else (str(v) if isinstance(v, int) else f"'{v}'")
    for r in rows:
        psql("insert into quality.tb_struct_diag_detail (diag_id, seq, owner, table_nm, column_nm, change_type, prev_data_type, curr_data_type, "
             "prev_data_len, curr_data_len, prev_nullable, curr_nullable, curr_attr_nm_kr, curr_data_decimal_len, curr_pk_yn, curr_default_val, curr_attr_ord) "
             f"values ('{did}', {r[0]}, 'APP', {', '.join(q(v) for v in r[1:])})")


def cleanup():
    dm = D.get("dm")
    if dm:
        for t in ("tb_struct_diag_detail",):
            psql(f"delete from quality.{t} where diag_id='{D.get('diag')}'")
        psql(f"delete from quality.tb_struct_diag_history where data_model_id='{dm}'")
        for t in ("tb_udp_val", "tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint", "tb_data_model_obj"):
            psql(f"delete from quality.{t} where dm_id='{dm}'")
        psql(f"delete from quality.tb_data_model where dm_id='{dm}'")
    psql(f"delete from quality.tb_udp_def where udp_nm='{UDP_NM}'")
    for s in reversed(D["subj"]):
        psql(f"delete from quality.tb_subj_area where subj_area_id='{s}'")


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
        time.sleep(3)

    def swal():
        t = driver.find_elements(By.CSS_SELECTOR, ".swal2-title")
        h = driver.find_elements(By.CSS_SELECTOR, ".swal2-html-container")
        return (t[0].get_attribute("textContent") if t else "") + " | " + (h[0].get_attribute("textContent") if h else "")

    def swal_ok():
        if driver.find_elements(By.CSS_SELECTOR, ".swal2-confirm"):
            js("document.querySelector('.swal2-confirm').click();")
            time.sleep(1)

    def btn(text, scope="body"):
        return [b for b in driver.find_elements(By.CSS_SELECTOR, f"{scope} button") if (b.get_attribute("textContent") or "").strip() == text]

    try:
        driver.get(BASE)
        time.sleep(2)
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)

        def _g1():
            open_menu("mmGroup", "nav_udpDef")
            js("document.getElementById('udpAdd').click();")
            time.sleep(1)
            driver.find_element(By.ID, "udpNm").send_keys(UDP_NM)
            js("document.getElementById('udpRequired').closest('.v-input').querySelector('.v-input--selection-controls__ripple').click();")
            time.sleep(0.3)
            js("document.getElementById('udpSave').click();")
            time.sleep(2)
            row = psql(f"select udp_id || '|' || data_tp || '|' || required_yn || '|' || use_yn from quality.tb_udp_def where udp_nm='{UDP_NM}'")
            print(f"  정의={row}")
            assert row.endswith("|TEXT|Y|Y")
            D["udp"] = row.split("|")[0]
            names = [r.get_attribute("textContent") for r in driver.find_elements(By.CSS_SELECTOR, "#udpTable tbody tr")]
            assert any(UDP_NM in n and "필수" in n for n in names), names
        step("G1. 사용자 정의 속성 — 필수 속성 추가", _g1)

        def _g2():
            open_menu("dmGroup", "nav_datamodelStatusTable")
            vm = "let c = document.getElementById('dmTable_table').__vue__; while (c && !('objForm' in c.$data)) c = c.$parent;"
            js(vm + "c.selectedModelId = arguments[0]; c.load(); c.loadAreaOptions();", D["dm"])
            time.sleep(2.5)
            js("arguments[0].click();", btn("테이블 추가")[0])
            time.sleep(1.5)
            fld = driver.find_elements(By.ID, "udp_" + D["udp"])
            assert fld, "필수 속성 칸이 없음"
            label = js("return arguments[0].closest('.v-input').querySelector('label').textContent;", fld[0])
            print(f"  칸 이름={label}")
            assert label.strip() == UDP_NM + " *"
            js(vm + "c.objForm.objNm = 'CELL_T2'; c.objForm.objOwner = 'APP'; c.objForm.objNmKr = '셀테이블2'; c.objForm.subjAreaId = arguments[0];", D["sc"])
            time.sleep(0.3)
            js("arguments[0].click();", btn("추가", ".v-dialog--active")[0])
            time.sleep(1.5)
            msg = swal()
            print(f"  비우고 추가={msg}")
            assert "필수 관리 항목" in msg and UDP_NM in msg
            swal_ok()
            fld[0].send_keys("총무과")
            js("arguments[0].click();", btn("추가", ".v-dialog--active")[0])
            time.sleep(2)
            swal_ok()
            v = psql(f"select val from quality.tb_udp_val where dm_id='{D['dm']}' and obj_nm='CELL_T2' and udp_id='{D['udp']}'")
            sa = psql(f"select subj_area_id from quality.tb_data_model_obj where dm_id='{D['dm']}' and obj_nm='CELL_T2'")
            print(f"  값={v} 주제영역={sa == D['sc']}")
            assert v == "총무과" and sa == D["sc"]
            time.sleep(1.5)
            trs = [r for r in driver.find_elements(By.CSS_SELECTOR, "#dmTable_table tbody tr") if "CELL_T2" in r.get_attribute("textContent")]
            assert trs, "추가한 테이블이 목록에 없음"
            path = trs[0].find_element(By.CSS_SELECTOR, ".subj-path")
            udp = [d.get_attribute("textContent") for d in trs[0].find_elements(By.CSS_SELECTOR, ".udp-summary")]
            print(f"  주제영역={path.get_attribute('textContent')} (경로 {path.get_attribute('title')}) 관리 항목={udp}")
            assert path.get_attribute("textContent") == f"셀주제{TAG}_C" and path.get_attribute("title") == f"셀주제{TAG}_A > 셀주제{TAG}_B > 셀주제{TAG}_C"
            assert f"{UDP_NM}: 총무과" in udp
        step("G2. 테이블 — 필수 속성 거부·저장, 주제영역 경로·관리 항목 열", _g2)

        def _g3():
            psql(f"update quality.tb_udp_def set use_yn='N' where udp_nm='{UDP_NM}'")
            open_menu("mmGroup", "nav_area_mgmt")
            js("document.getElementById('tabSubj').click();")
            time.sleep(1.5)
            rows = {js("return arguments[0].querySelector('.subj-nm').textContent.trim();", r): r for r in driver.find_elements(By.CSS_SELECTOR, "#subjTable tbody tr")}
            pads = {k: js("return arguments[0].querySelector('.subj-nm').style.paddingLeft;", v) for k, v in rows.items() if k.startswith(f"셀주제{TAG}")}
            print(f"  들여쓰기={pads}")
            assert pads.get(f"셀주제{TAG}_A") == "0px" and pads.get(f"셀주제{TAG}_B") == "18px" and pads.get(f"셀주제{TAG}_C") == "36px"
            js("arguments[0].click();", rows[f"셀주제{TAG}_B"])
            time.sleep(1.5)
            title = driver.find_element(By.ID, "subjTablesTitle").get_attribute("textContent")
            print(f"  제목={title}")
            assert f"셀주제{TAG}_A > 셀주제{TAG}_B" in title and "0건" in title
            js("document.getElementById('subjLinkOpen').click();")
            time.sleep(1)
            vm = "let c = document.getElementById('subjTable').__vue__; while (c && !('linkModelId' in c.$data)) c = c.$parent;"
            js(vm + "c.linkModelId = arguments[0]; c.loadModelTables();", D["dm"])
            time.sleep(2)
            trs = [r for r in driver.find_elements(By.CSS_SELECTOR, "#linkTablesGrid tbody tr") if "CELL_T" in r.get_attribute("textContent") and "CELL_T2" not in r.get_attribute("textContent")]
            assert trs, "연결할 테이블이 목록에 없음"
            js("arguments[0].querySelector('.v-data-table__checkbox').click();", trs[0])
            time.sleep(0.5)
            js("document.getElementById('linkRun').click();")
            time.sleep(2)
            sa = psql(f"select subj_area_id from quality.tb_data_model_obj where dm_id='{D['dm']}' and obj_nm='CELL_T'")
            grid = [r.get_attribute("textContent") for r in driver.find_elements(By.CSS_SELECTOR, "#subjTablesGrid tbody tr")]
            print(f"  연결={sa == D['sb']} 목록={grid}")
            assert sa == D["sb"] and any("CELL_T" in g for g in grid)
        step("G3. 영역 관리 — 주제영역 트리·테이블 연결", _g3)

        def _g4():
            open_menu("structDiagGroup", "nav_structDiagResult")
            vm = "let c = document.querySelector('#tab_structDiagResult .v-sheet').__vue__; while (c && !('colSel' in c.$data)) c = c.$parent;"
            js(vm + "c.loadAllHistory(arguments[0]);", D["diag"])
            time.sleep(3)
            trs = driver.find_elements(By.CSS_SELECTOR, "#structColTable tbody tr")
            print(f"  행={len(trs)}")
            assert len(trs) == 4
            js("document.querySelector('#structColTable thead .v-data-table__checkbox').click();")
            time.sleep(0.5)
            label = driver.find_element(By.ID, "structApply").get_attribute("textContent").strip()
            assert "(4)" in label, label
            js("document.getElementById('structApply').click();")
            time.sleep(1)
            swal_ok()
            time.sleep(2.5)
            msg = swal()
            print(f"  결과={msg}")
            assert "반영 완료" in msg and "4건" in msg
            swal_ok()
            time.sleep(1.5)
            cols = psql(f"select string_agg(obj_nm || '.' || attr_nm || ':' || coalesce(attr_nm_kr,'') || ':' || data_len, ',' order by obj_nm, attr_nm) "
                        f"from quality.tb_data_model_attr where dm_id='{D['dm']}' and obj_nm in ('CELL_T','CELL_NEW')")
            print(f"  모델={cols}")
            assert "CELL_T.MEMO:메모:200" in cols and "CELL_T.NM:이름:100" in cols and "CELL_T.AMT" not in cols and "CELL_NEW.K:키:5" in cols
            chips = [c.get_attribute("textContent").strip() for c in driver.find_elements(By.CSS_SELECTOR, "#structColTable tbody .v-chip") if "반영됨" in c.get_attribute("textContent")]
            disabled = js("return [...document.querySelectorAll('#structColTable tbody .v-simple-checkbox')].filter(e => e.classList.contains('v-simple-checkbox--disabled')).length;")
            print(f"  반영됨 칩={len(chips)} 고를 수 없는 행={disabled}")
            assert len(chips) == 4 and disabled == 4
        step("G4. 구조 진단 결과 — 모델에 반영", _g4)

        def _g5():
            # 권한 없는 사용자(역할 없음): 사용자 정의 속성·영역 관리 메뉴가 없고, 테이블 창에는 관리 항목 칸이 있다
            d2 = webdriver.Edge(options=options)
            d2.set_window_size(1600, 900)
            try:
                d2.get(BASE)
                time.sleep(2)
                WebDriverWait(d2, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("jyjang")
                d2.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
                d2.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
                WebDriverWait(d2, 15).until(lambda d: "/signin" not in d.current_url)
                time.sleep(3)
                menus = d2.execute_script("return ['nav_udpDef', 'nav_area_mgmt'].filter(id => document.getElementById(id));")
                print(f"  보이는 관리 메뉴={menus}")
                assert menus == []
                g = d2.find_elements(By.CSS_SELECTOR, "#dmGroup .v-list-group__header")
                d2.execute_script("arguments[0].click();", g[0])
                time.sleep(1)
                d2.execute_script("document.getElementById('nav_datamodelStatusTable').click();")
                time.sleep(3)
                vm = "let c = document.getElementById('dmTable_table').__vue__; while (c && !('objForm' in c.$data)) c = c.$parent;"
                d2.execute_script(vm + "c.selectedModelId = arguments[0]; c.openAddObjDialog();", D["dm"])
                time.sleep(1.5)
                assert d2.find_elements(By.ID, "udp_UDP_RETENTION") and d2.find_elements(By.ID, "objSubjArea"), "관리 항목·주제영역 칸이 없음"
            finally:
                d2.quit()
        step("G5. 권한 없는 사용자 — 메뉴 숨김, 테이블 창의 관리 항목", _g5)
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
