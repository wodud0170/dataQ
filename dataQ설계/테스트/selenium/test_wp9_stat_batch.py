"""
104번 WP9 — 통계·배치 (55 변경 전후 비교, 67 준수 현황 스냅샷·미사용 표준 예약, 62 시스템별 현황, 66 DA 목표 달성도)

검증 (시험 시스템·모델·진단 결과·신청·단어·스케줄은 API·SQL 로 만들고 끝에서 지운다):
  I1. 데이터 모델 변경 승인 — 신청 묶음에 모델 이름, 컬럼 수정 행을 펼치면 바뀐 항목만(길이 50 → 100, 없는 "컬럼 순서" 변경 없음),
      컬럼 삭제 행은 "지운 값" 에 값이 있는 항목만, 신청 시각은 YYYY-MM-DD HH:mm
  I2. 데이터 모델 변경 이력 — 컬럼 삭제 상세가 전후 표(지운 값), [원문 JSON 보기] 로 원문
  I3. 표준 사전 변경 승인 — 상세의 "변경 이력" 행을 펼치면 영문명·단어설명 전후 (사전 ID 는 없는 변경이라 안 보임)
  I4. 스케줄 관리 — 진단 유형 "준수 현황 스냅샷" 은 데이터모델 (선택) + 안내, 실행 이력 상세에 배치 결과(DONE·요약)
  I5. 미사용 표준 — 기준을 예약 실행 결과로 바꾸면 저장된 건수·실행 요약 안내, 엑셀 가능
  I6. 시스템별 현황 — 본부·하위 시스템 준수율·목표·달성, 시스템을 고르면 모델별 표(데이터소스 없는 모델 구조 "대상 아님")·추이 그래프,
      하위 시스템 목표 창에 상속 안내 → 95% 저장하면 미달
  I7. 대시보드 — DA 목표 달성도 카드에 목표 둔 시스템, 미달 먼저
  I8. 권한 없는 사용자 — 시스템별 현황에 [목표] 열·버튼이 없다
"""
import base64, json, re, subprocess, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
results = []
D = {"sch": [], "runs": []}
S1, S2 = f"CW9A{TAG}", f"CW9B{TAG}"
S1_NM, S2_NM = f"셀W9본부{TAG}", f"셀W9시스템{TAG}"
WORD = f"셀화면전후{TAG}"


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


def login(u):
    s = requests.Session()
    s.post(BASE + "/login", data={"id": u, "password": base64.b64encode(b"123").decode()}, allow_redirects=False, timeout=10)
    return s


def setup():
    A, U = login("space"), login("jyjang")
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    psql(f"insert into quality.tb_sys_info (sys_cd, p_sys_cd, sys_tp, sys_nm, cret_dt, cret_user_id) values "
         f"('{S1}', null, 0, '{S1_NM}', {now}, 'space'), ('{S2}', '{S1}', 1, '{S2_NM}', {now}, 'space')")

    def model(nm, sys_cd):
        A.post(BASE + "/api/dm/createDataModel", json={"dataModelNm": nm, "modelType": "LOGICAL", "ver": "1", "dictId": "DEFAULT"}, timeout=10)
        dm = psql(f"select dm_id from quality.tb_data_model where dm_nm='{nm}'")
        psql(f"update quality.tb_data_model set dm_sys_cd='{sys_cd}' where dm_id='{dm}'")
        return dm
    D["m1nm"], D["m2nm"] = f"셀W9모델1{TAG}", f"셀W9모델2{TAG}"
    D["m1"] = m1 = model(D["m1nm"], S2)
    D["m2"] = model(D["m2nm"], S1)
    # 표준 진단 — 전체 100 컬럼 중 이슈 10 컬럼 → 90%
    D["job"] = job = f"CW9J{TAG}"
    psql(f"insert into quality.tb_diag_job (diag_job_id, dm_id, status, total_cnt, end_dt, cret_dt) values ('{job}', '{m1}', 'DONE', 100, {now}, {now})")
    for i in range(10):
        psql(f"insert into quality.tb_diag_result (diag_job_id, obj_nm, attr_nm, diag_type, obj_owner) values ('{job}', 'T', 'C{i}', 'TERM', 'O')")
    r = A.post(BASE + "/api/daStat/target/save", json={"targetTp": "SYS", "targetId": S1, "stndRateGoal": 85, "structRateGoal": 90}, timeout=10).json()
    assert r.get("resultCode") == 200, r

    # 모델 변경 신청 — 관리자가 만든 컬럼을 권한 없는 사용자가 수정(길이)·삭제
    r = A.post(BASE + "/api/dm/addObj", json={"dataModelId": m1, "objOwner": "APP", "objNm": "CELL_CUST", "objNmKr": "셀고객"}, timeout=10).json()
    assert r.get("resultCode") == 200, r
    r = A.post(BASE + "/api/dm/saveAttrs", json={"dataModelId": m1, "objNm": "CELL_CUST", "objOwner": "APP", "attrs": [
        {"mode": "ADD", "attrNm": "CUST_NM", "attrNmKr": "고객명", "dataType": "VARCHAR", "dataLen": 50},
        {"mode": "ADD", "attrNm": "CUST_TEL", "attrNmKr": "고객전화번호", "dataType": "VARCHAR", "dataLen": 20}]}, timeout=10).json()
    assert r.get("resultCode") == 200, r
    r = U.post(BASE + "/api/dm/saveAttrs", json={"dataModelId": m1, "objNm": "CELL_CUST", "objOwner": "APP", "attrs": [
        {"mode": "UPDATE", "origAttrNm": "CUST_NM", "attrNm": "CUST_NM", "attrNmKr": "고객명", "dataType": "VARCHAR", "dataLen": 100, "nullableYn": "Y"},
        {"mode": "DELETE", "attrNm": "CUST_TEL"}]}, timeout=10).json()
    assert r.get("resultCode") == 200, r
    seqs = [int(x) for x in psql(f"select string_agg(change_seq::text, ',') from quality.tb_data_model_change_history "
                                  f"where dm_id='{m1}' and aprv_status='DRAFT'").split(",") if x]
    r = U.post(BASE + "/api/dmApproval/submit", json={"changeSeqList": seqs}, timeout=10).json()
    assert r.get("resultCode") == 200 and len(seqs) == 2, (r, seqs)

    # 표준 — 권한 없는 사용자 단어 등록(승인 대기) → 관리자가 영문명·설명 수정
    U.post(BASE + "/api/std/createWord", json={"wordNm": WORD, "wordEngAbrvNm": f"CWD{TAG}", "wordEngNm": f"CWD{TAG}",
                                               "wordDesc": "화면 확인", "wordClsfYn": "N", "dictId": "DEFAULT"}, timeout=10)
    D["word"] = wid = psql(f"select word_id from quality.tb_word where word_nm='{WORD}'")
    r = A.post(BASE + "/api/std/updateWord", json={"id": wid, "wordNm": WORD, "wordEngAbrvNm": f"CWD{TAG}", "wordEngNm": f"CellWord{TAG}",
                                                   "wordDesc": "변경 전후 비교 확인", "wordClsfYn": "N", "dictId": "DEFAULT"}, timeout=10).json()
    assert r.get("resultCode") == 200, r

    # 예약 실행 — 준수 현황 스냅샷(모델 없이 = 사용 중 모델 전부), 미사용 표준(모델1 의 사전)
    def run(diag_type, dm):
        r = A.post(BASE + "/api/diag/schedule/create", json={"scheduleNm": f"셀W9{diag_type}{TAG}", "diagType": diag_type, "dataModelId": dm,
                                                             "scheduleType": "SIMPLE", "repeatCycle": "DAILY", "repeatTime": "03:00", "useYn": "N"}, timeout=10).json()
        assert r.get("resultCode") == 200, r
        sid = r["contents"]
        D["sch"].append(sid)
        log_id = A.post(BASE + "/api/diag/schedule/runNow", json={"scheduleId": sid}, timeout=10).json()["contents"]
        st = ""
        for _ in range(40):
            time.sleep(5)
            st = psql(f"select exec_status || '|' || coalesce(batch_run_id,'') from quality.tb_diag_schedule_log where log_id='{log_id}'")
            if not st.startswith("RUNNING"):
                break
        print(f"  {diag_type} 실행={st}")
        assert st.startswith("DONE|"), st
        D["runs"].append(st.split("|")[1])
        return st.split("|")[1]
    D["snapRun"] = run("COMPLIANCE_SNAPSHOT", None)
    D["unusedRun"] = run("UNUSED_STD", m1)


def cleanup():
    for run in D["runs"]:
        for t in ("tb_compliance_snapshot", "tb_unused_std_result", "tb_batch_run"):
            psql(f"delete from quality.{t} where run_id='{run}'")
    for sid in D["sch"]:
        psql(f"delete from quality.tb_diag_schedule_log where schedule_id='{sid}'")
        psql(f"delete from quality.tb_diag_schedule where schedule_id='{sid}'")
    if D.get("job"):
        psql(f"delete from quality.tb_diag_result where diag_job_id='{D['job']}'")
        psql(f"delete from quality.tb_diag_job where diag_job_id='{D['job']}'")
    for dm in (D.get("m1"), D.get("m2")):
        if not dm:
            continue
        psql(f"delete from quality.tb_da_target where target_id='{dm}'")
        for t in ("tb_aprv_doc_step", "tb_aprv_doc"):
            psql(f"delete from quality.{t} where doc_id in (select doc_id from quality.tb_aprv_doc where target_id in "
                 f"(select submission_id from quality.tb_data_model_change_history where dm_id='{dm}'))")
        for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_obj"):
            psql(f"delete from quality.{t} where dm_id='{dm}'")
        psql(f"delete from quality.tb_data_model where dm_id='{dm}'")
    psql(f"delete from quality.tb_da_target where target_id in ('{S1}', '{S2}')")
    psql(f"delete from quality.tb_sys_info where sys_cd in ('{S2}')")
    psql(f"delete from quality.tb_sys_info where sys_cd in ('{S1}')")
    if D.get("word"):
        psql(f"delete from quality.tb_change_history where target_id='{D['word']}'")
        psql(f"delete from quality.tb_aprv_stats where req_item_id='{D['word']}'")
        psql(f"delete from quality.tb_word where word_id='{D['word']}'")


# 탭 안 첫 Vue 요소에서 이름이 같은 컴포넌트 — 아래(자식)부터 찾고 없으면 위로. 첫 요소가 keep-alive 로 잡히는 화면(대시보드)이 있다
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")


def login_driver(options, user):
    d = webdriver.Edge(options=options)
    d.set_window_size(1600, 900)
    d.get(BASE)
    time.sleep(2)
    WebDriverWait(d, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
    d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
    d.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
    WebDriverWait(d, 15).until(lambda x: "/signin" not in x.current_url)
    time.sleep(3)
    return d


def main():
    setup()
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = login_driver(options, "space")

    def js(script, *args):
        return driver.execute_script(script, *args)

    def open_menu(group_id, nav_id):
        if group_id:
            groups = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
            if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
                js("arguments[0].click();", groups[0])
                time.sleep(1)
        js(f"document.getElementById('{nav_id}').click();")
        time.sleep(3)

    def text(sel):
        e = driver.find_elements(By.CSS_SELECTOR, sel)
        return e[0].get_attribute("textContent") if e else ""

    def diff_rows(scope):
        """ChangeDiffTable — [(항목, 값…)]"""
        return js("return [...arguments[0].querySelectorAll('.cdt-table tbody tr')].map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim()));", scope)

    try:
        def _i1():
            open_menu("mmGroup", "nav_dm_approval")
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, ".submission-row") if D["m1nm"] in r.get_attribute("textContent")]
            assert rows, "신청 묶음에 모델 이름이 없음"
            js("arguments[0].click();", rows[0])
            time.sleep(2)
            trs = driver.find_elements(By.CSS_SELECTOR, "#dmAprvItems tbody tr")
            items = {}
            for tr in trs:
                t = tr.get_attribute("textContent")
                if "CELL_CUST.CUST_NM" in t:
                    items["mod"] = tr
                elif "CELL_CUST.CUST_TEL" in t:
                    items["del"] = tr
            assert set(items) == {"mod", "del"}, [t.get_attribute("textContent") for t in trs]
            dt = items["mod"].find_elements(By.CSS_SELECTOR, "td")[4].get_attribute("textContent").strip()
            print(f"  신청 시각={dt}")
            assert re.fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}", dt), dt
            for k in ("mod", "del"):
                js("arguments[0].querySelector('.v-data-table__expand-icon').click();", items[k])
                time.sleep(0.8)
            cells = driver.find_elements(By.CSS_SELECTOR, "#dmAprvItems .diff-cell")
            assert len(cells) == 2, len(cells)
            by = {("mod" if "변경 후" in c.get_attribute("textContent") else "del"): c for c in cells}
            mod, dele = diff_rows(by["mod"]), diff_rows(by["del"])
            print(f"  수정={mod}\n  삭제={dele}")
            # 길이를 늘리면 표준 재검사로 용어·도메인 표준·구성 단어가 함께 바뀔 수 있다 — 바꾸지 않은 항목(컬럼 순서 등)은 없어야 한다
            assert mod[0] == ["길이", "50", "100"] and all(r[0] not in ("컬럼 순서", "컬럼 한글명", "NULL 허용", "PK", "컬럼 코멘트") for r in mod), mod
            assert "지운 값" in by["del"].get_attribute("textContent")
            labels = [r[0] for r in dele]
            assert "컬럼 영문명" in labels and ["컬럼 영문명", "CUST_TEL"] in dele and all(r[1] != "(없음)" for r in dele), dele
        step("I1. 데이터 모델 변경 승인 — 모델 이름·바뀐 항목만·지운 값", _i1)

        def _i2():
            open_menu("dmGroup", "nav_dm_history")
            js(VM + "const c = vm('tab_dm_history', 'MMDataModelChangeHistory'); c.filter.dmId = arguments[0]; c.load();", D["m1"])
            time.sleep(2)
            js(VM + "const c = vm('tab_dm_history', 'MMDataModelChangeHistory'); c.showDetail(c.items.find(i => i.changeType === 'DEL_ATTR'));")
            time.sleep(1.5)
            box = driver.find_element(By.ID, "dmHistDiff")
            rows = diff_rows(box)
            print(f"  상세={rows}")
            assert "지운 값" in box.get_attribute("textContent") and ["컬럼 영문명", "CUST_TEL"] in rows
            assert not driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active pre.diff-box")
            js("document.getElementById('dmHistRawToggle').click();")
            time.sleep(0.5)
            raw = [p.get_attribute("textContent") for p in driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active pre.diff-box")]
            assert len(raw) == 2 and '"attrNm": "CUST_TEL"' in raw[0], raw
            js(VM + "vm('tab_dm_history', 'MMDataModelChangeHistory').detailDialog = false;")
            time.sleep(0.5)
        step("I2. 데이터 모델 변경 이력 — 전후 표·원문 JSON", _i2)

        def _i3():
            open_menu("mmGroup", "nav_approval")
            js(VM + "vm('tab_approval', 'MMApproval').getApprovalData();")
            time.sleep(2.5)
            ok = js(VM + "const c = vm('tab_approval', 'MMApproval'); const it = c.approvalAllItems.find(i => i.reqItemNm === arguments[0]); "
                    "if (!it) return false; c.bindDetail(it); return true;", WORD)
            assert ok, "승인 목록에 단어가 없음"
            time.sleep(2)
            sec = driver.find_element(By.ID, "aprvChangeHistory")
            js("arguments[0].querySelector('.v-data-table__expand-icon').click();", sec)
            time.sleep(0.8)
            rows = diff_rows(sec)
            print(f"  변경 이력={rows}")
            labels = [r[0] for r in rows]
            assert ["영문명", f"CWD{TAG}", f"CellWord{TAG}"] in rows and "단어설명" in labels and "dictId" not in labels, rows
        step("I3. 표준 사전 변경 승인 — 변경 이력 전후", _i3)

        def _i4():
            open_menu("scheduleGroup", "nav_scheduleManage")
            js(VM + "const c = vm('tab_scheduleManage', 'DSScheduleManage'); c.onAddScheduleClick(); setTimeout(() => { c.form.diagType = 'COMPLIANCE_SNAPSHOT'; }, 300);")
            time.sleep(1.5)
            dlg = text(".v-dialog--active")
            print(f"  등록 창={dlg[:160]}")
            assert "데이터모델 (선택)" in dlg and "사용 중인 모델 전부" in dlg
            js(VM + "vm('tab_scheduleManage', 'DSScheduleManage').editDialog = false;")
            time.sleep(0.5)
            open_menu("scheduleGroup", "nav_scheduleLog")
            js(VM + "const c = vm('tab_scheduleLog', 'DSScheduleLog'); c.openDetail(c.items.find(i => i.batchRunId === arguments[0]));", D["snapRun"])
            time.sleep(2)
            card = text("#schLogBatch")
            print(f"  배치 결과={card}")
            assert "DONE" in card and re.search(r"모델 \d+개 \(표준 진단한 모델 \d+개\)", card) and "시스템별 현황" in card
            js(VM + "vm('tab_scheduleLog', 'DSScheduleLog').drawerOpen = false;")
            time.sleep(0.5)
        step("I4. 스케줄 — 준수 현황 스냅샷 유형·실행 이력 배치 결과", _i4)

        def _i5():
            open_menu("dsGroup", "nav_unusedStd")
            js(VM + "const c = vm('tab_unusedStd', 'DSUnusedStd'); c.dictId = 'DEFAULT'; c.onDict();")
            time.sleep(2.5)
            opts = js(VM + "return vm('tab_unusedStd', 'DSUnusedStd').runOptions.map(o => o.value);")
            assert D["unusedRun"] in opts, opts
            js(VM + "const c = vm('tab_unusedStd', 'DSUnusedStd'); c.kind = 'TERM'; c.runId = arguments[0]; c.load();", D["unusedRun"])
            time.sleep(3)
            saved = psql(f"select count(*) from quality.tb_unused_std_result where run_id='{D['unusedRun']}' and kind='TERM'")
            cnt, note = text(".us-count").strip(), text(".us-run-note")
            sel = js("return document.getElementById('unusedRun').closest('.v-select__selections').textContent;")
            print(f"  건수={cnt} (저장 {saved}) 고른 값={sel.strip()} 안내={note[:90]}")
            assert cnt == f"총 {saved}건" and "예약 실행 결과를 보는 중입니다" in note and "용어 " + saved in note
            assert "예약 실행 " in sel and "용어" not in sel   # 좁은 칸에는 실행 시각만
            assert driver.find_element(By.ID, "unusedExcel").is_enabled()
        step("I5. 미사용 표준 — 예약 실행 결과 보기", _i5)

        def _i6():
            open_menu("dmGroup", "nav_daSystemStat")
            rows = {js("return arguments[0].querySelector('.da-sys-nm') && arguments[0].querySelector('.da-sys-nm').textContent.trim();", r): r
                    for r in driver.find_elements(By.CSS_SELECTOR, "#daSysTable tbody tr")}
            r1, r2 = rows.get(S1_NM), rows.get(S2_NM)
            assert r1 and r2, list(rows)
            t1, t2 = r1.get_attribute("textContent"), r2.get_attribute("textContent")
            print(f"  본부={t1}\n  시스템={t2}")
            for t in (t1, t2):
                assert "90%" in t and "목표 85%" in t and "달성" in t, t
            assert "대상 아님" in t1 and "목표 90%" in t1   # 데이터소스 없는 모델뿐 → 구조 진단 대상 아님
            js("arguments[0].click();", r1)
            time.sleep(2.5)
            models = {js("return arguments[0].querySelector('td').textContent.trim();", r): r.get_attribute("textContent")
                      for r in driver.find_elements(By.CSS_SELECTOR, "#daModelTable tbody tr")}
            print(f"  모델별={models}")
            assert set(models) == {D["m1nm"], D["m2nm"]}
            assert "90%" in models[D["m1nm"]] and "대상 아님" in models[D["m1nm"]] and "미진단" in models[D["m2nm"]]
            assert not driver.find_elements(By.ID, "daTrendEmpty") and driver.find_elements(By.CSS_SELECTOR, "#tab_daSystemStat .apexcharts-canvas")
            js(f"document.getElementById('daTarget_{S2}').click();")
            time.sleep(1)
            dlg = text(".v-dialog--active")
            print(f"  목표 창={dlg[:160]}")
            assert f"지금은 {S1_NM} (표준 85% · 구조 90%) 의 목표를 따르고 있습니다" in dlg
            driver.find_element(By.CSS_SELECTOR, "#daStndGoal").send_keys("95")
            js("document.getElementById('daTargetSave').click();")
            time.sleep(2.5)
            r2 = [r for r in driver.find_elements(By.CSS_SELECTOR, "#daSysTable tbody tr") if S2_NM in r.get_attribute("textContent")][0]
            t2 = r2.get_attribute("textContent")
            print(f"  저장 후 시스템={t2}")
            assert "목표 95%" in t2 and "미달" in t2
            assert psql(f"select stnd_rate_goal from quality.tb_da_target where target_tp='SYS' and target_id='{S2}'") in ("95", "95.0", "95.00")
        step("I6. 시스템별 현황 — 집계·모델별·추이·목표 상속과 저장", _i6)

        def _i7():
            open_menu(None, "nav_dashboard")
            js(VM + "vm('tab_dashboard', 'QDashboard').getAchievement();")
            time.sleep(2)
            rows = [r.get_attribute("textContent") for r in driver.find_elements(By.CSS_SELECTOR, "#dashDaAchv tbody tr")]
            mine = [r for r in rows if S1_NM in r]
            print(f"  카드={mine}")
            i1 = next(i for i, r in enumerate(rows) if S1_NM in r and S2_NM not in r)
            i2 = next(i for i, r in enumerate(rows) if S2_NM in r)
            assert i2 < i1 and "미달" in rows[i2] and "달성" in rows[i1]
        step("I7. 대시보드 — DA 목표 달성도 (미달 먼저)", _i7)

        def _i8():
            d2 = login_driver(options, "jyjang")
            try:
                g = d2.find_elements(By.CSS_SELECTOR, "#dmGroup .v-list-group__header")
                d2.execute_script("arguments[0].click();", g[0])
                time.sleep(1)
                d2.execute_script("document.getElementById('nav_daSystemStat').click();")
                time.sleep(3)
                heads = [h.get_attribute("textContent").strip() for h in d2.find_elements(By.CSS_SELECTOR, "#daSysTable thead th")]
                btns = d2.find_elements(By.CSS_SELECTOR, "[id^='daTarget_']")
                print(f"  머리={heads} 목표 버튼={len(btns)}")
                assert "목표" not in heads and not btns and "표준 준수율" in heads
            finally:
                d2.quit()
        step("I8. 권한 없는 사용자 — 목표 열 없음", _i8)
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
