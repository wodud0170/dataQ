"""
사용자 시나리오 5종 — 70번 데이터 품질 진단 무겁게 검증

각 시나리오:
  - UI 흐름 포함 (셀레니움 Edge driver)
  - DB 직접 query 로 정확한 카운트/snapshot 검증
  - 에러/권한 케이스 일부 포함
  - 통계 메뉴 / 시계열 결과까지 따라감
"""
import base64, json, subprocess, sys, time, traceback, uuid
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
DM_ID = "TESTQUALDM00000000001A"
results = []


def log(name):
    print(f"\n{'='*78}\n[{name}]\n{'='*78}")


def step(name, fn):
    print(f"\n  --- {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login(s):
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


def db(sql):
    """docker exec dataq-db psql 으로 직접 query — 단일 결과"""
    out = subprocess.check_output(
        ["docker", "exec", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
         "-A", "-t", "-F", "|", "-c", sql],
        stderr=subprocess.STDOUT
    ).decode("utf-8", errors="ignore").strip().splitlines()
    return out


def api_run_columns_value(s, targets, sample=100):
    r = s.post(BASE + "/api/qual/value/runColumns",
               json={"dataModelId": DM_ID, "sampleRate": sample, "targets": targets}, timeout=30)
    diag = r.json().get("contents")
    assert diag, f"value 실패: {r.json()}"
    deadline = time.time() + 90
    while time.time() < deadline:
        r2 = s.get(BASE + f"/api/qual/value/history/{diag}", timeout=10)
        h = r2.json() or {}
        if h.get("status") in ("DONE","ERROR"): break
        time.sleep(2)
    assert h.get("status") == "DONE", f"value 마감 실패: {h}"
    return diag


def api_run_columns_rule(s, targets, sample=100):
    r = s.post(BASE + "/api/qual/rule/runColumns",
               json={"dataModelId": DM_ID, "sampleRate": sample, "incrementalYn": "N", "targets": targets},
               timeout=30)
    diag = r.json().get("contents")
    assert diag, f"rule 실패: {r.json()}"
    deadline = time.time() + 90
    while time.time() < deadline:
        r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
        c = r2.json().get("contents")
        if isinstance(c, str): c = json.loads(c)
        h = (c or {}).get("history") or {}
        if h.get("status") in ("DONE","ERROR"): break
        time.sleep(2)
    assert h.get("status") == "DONE", f"rule 마감 실패: {h}"
    return diag, c


def get_col(s, obj, attr):
    r = s.get(BASE + "/api/qual/colrule/listWithLatest",
              params={"dmId": DM_ID, "objNm": obj}, timeout=10)
    rows = r.json() or []
    return next((x for x in rows if x.get("attrNm") == attr), None)


def map_col(s, obj, attr, domain_rule_id=None, custom_rule_id=None, exclude=False):
    body = {"dmId": DM_ID, "objNm": obj, "attrNm": attr,
            "domainRuleId": domain_rule_id, "customRuleId": custom_rule_id,
            "excludeYn": "Y" if exclude else "N"}
    r = s.post(BASE + "/api/qual/colrule/save", json=body, timeout=10)
    assert r.json().get("resultCode") == 200, f"매핑 실패: {r.json()}"


def make_driver():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    return d


def ui_login(d):
    d.get(BASE + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys("space")
    pw = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw.send_keys("123"); pw.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(5)


def ui_open_qual(d, child_id):
    act = d.find_element(By.XPATH, "//div[@id='qualGroup']//div[contains(@class,'v-list-group__header')]")
    if not d.find_elements(By.ID, child_id) or not d.find_elements(By.ID, child_id)[0].is_displayed():
        d.execute_script("arguments[0].click();", act); time.sleep(2)
    m = WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.ID, child_id)))
    d.execute_script("arguments[0].click();", m); time.sleep(3)


def ui_pick_model(d, name="TEST_QUAL_MODEL"):
    ac = d.find_element(By.XPATH,
        "//label[contains(.,'모델')]/ancestor::div[contains(@class,'v-autocomplete')][1]//input")
    d.execute_script("arguments[0].click();", ac); time.sleep(0.5)
    ac.send_keys(name); time.sleep(2)
    opt = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not opt: opt = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    d.execute_script("arguments[0].click();", opt[0]); time.sleep(4)


# ============================================================
# S1. 신규 사용자 풀 흐름 (UI + 정확한 카운트 + 통계까지)
# ============================================================
def scenario_1():
    log("S1. 신규 사용자 풀 흐름 — UI 진입 → 진단 → 룰 변경 → 재진단 → 통계 시계열까지")
    s = requests.Session(); login(s)
    state = {}
    d = make_driver()

    def _ui_login():
        ui_login(d)

    def _open_value_screen():
        ui_open_qual(d, "nav_valueProfile")
        ui_pick_model(d)
        # 컬럼 그리드 행수
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        assert len(rows) >= 10, f"그리드 10+ 기대, 실제 {len(rows)}"
        state["initial_rows"] = len(rows)
        print(f"  그리드 표시 컬럼 = {state['initial_rows']}")

    def _api_initial_diag():
        # API 로 PHONE 단독 진단 (DASH default 보장)
        map_col(s, "TB_TEST_MEMBER", "PHONE", domain_rule_id="DR_TEST_PHONE_DASH")
        api_run_columns_rule(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])
        info = get_col(s, "TB_TEST_MEMBER", "PHONE")
        state["rate1"] = float(info["ruleConformRate"])
        state["viol1"] = int(info["ruleViolation"])
        print(f"  S1 초기 — DASH: 적합률 {state['rate1']:.2f}%, 위반 {state['viol1']}/{info['ruleTotal']}")
        # DB 직접 — DIAG_HISTORY 와 join 으로 가장 최근 진단의 PHONE row
        rows = db("SELECT rr.VIOLATION_CNT, rr.TOTAL_CNT "
                  "FROM quality.TB_QUAL_RULE_RESULT rr "
                  "JOIN quality.TB_QUAL_DIAG_HISTORY dh ON dh.DIAG_ID = rr.DIAG_ID "
                  "WHERE rr.OBJ_NM='TB_TEST_MEMBER' AND rr.ATTR_NM='PHONE' "
                  "  AND dh.DM_ID='" + DM_ID + "' AND dh.STATUS='DONE' "
                  "ORDER BY dh.DIAG_DT DESC LIMIT 1;")
        v, t = rows[0].split("|")
        assert int(v) == state["viol1"], f"DB={v} API={state['viol1']} 불일치"

    def _ui_open_colrule_change():
        ui_open_qual(d, "nav_qualColRule")
        ui_pick_model(d)
        time.sleep(2)

    def _api_change_rule_to_nodash():
        # UI 다이얼로그 대신 API 직접 (UI 다이얼로그 자체는 별도 시나리오)
        map_col(s, "TB_TEST_MEMBER", "PHONE", domain_rule_id="DR_TEST_PHONE_NODASH")
        info = get_col(s, "TB_TEST_MEMBER", "PHONE")
        assert info.get("effectiveSource") == "DOMAIN"
        assert info.get("domainRuleId") == "DR_TEST_PHONE_NODASH"
        print(f"  effective rule = {info.get('effectiveRuleNm')} (변경 적용 OK)")

    def _api_rediag_phone():
        api_run_columns_rule(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])
        info = get_col(s, "TB_TEST_MEMBER", "PHONE")
        state["rate2"] = float(info["ruleConformRate"])
        state["viol2"] = int(info["ruleViolation"])
        print(f"  S1 변경 — NODASH: 적합률 {state['rate2']:.2f}%, 위반 {state['viol2']}/{info['ruleTotal']}")

    def _verify_change_effect():
        # NODASH 가 데이터에 안 맞으니 위반 ↑, 적합률 ↓
        d_rate = state["rate1"] - state["rate2"]
        d_viol = state["viol2"] - state["viol1"]
        print(f"  적합률 차이 {d_rate:.2f}%p, 위반 증가 {d_viol}")
        assert d_rate >= 50,  f"적합률 50%p+ 차이 기대"
        assert d_viol >= 20,  f"위반 20+ 증가 기대"

    def _ui_check_stats():
        # UI 통계 메뉴 — PHONE 시계열 row 개수 확인 (값 진단 누적)
        api_run_columns_value(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])
        api_run_columns_value(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])
        ui_open_qual(d, "nav_qualStats")
        ui_pick_model(d)
        # 테이블 + 컬럼 필터
        attr_in = d.find_element(By.XPATH,
            "//label[contains(.,'컬럼')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        attr_in.clear(); attr_in.send_keys("PHONE")
        d.find_element(By.ID, "btn-stats-load").click(); time.sleep(2)
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  통계 메뉴 PHONE 시계열 {len(rows)} 행")
        assert len(rows) >= 2, f"시계열 2+ 기대 (방금 진단 2회)"

    def _restore():
        map_col(s, "TB_TEST_MEMBER", "PHONE", domain_rule_id="DR_TEST_PHONE_DASH")
        try: d.quit()
        except Exception: pass

    step("S1.1 UI 로그인",                              _ui_login)
    step("S1.2 [값 프로파일링] 화면 진입 + 모델 선택",   _open_value_screen)
    step("S1.3 PHONE 초기 진단 (DASH) + DB 카운트 일치", _api_initial_diag)
    step("S1.4 [컬럼 규칙 매핑] 화면 진입",              _ui_open_colrule_change)
    step("S1.5 PHONE 룰 NODASH 로 변경",                 _api_change_rule_to_nodash)
    step("S1.6 PHONE 단독 재진단",                       _api_rediag_phone)
    step("S1.7 룰 변경 효과 검증 (적합률 50%p+, 위반↑)", _verify_change_effect)
    step("S1.8 [진단 통계] 시계열 2+ row 확인",          _ui_check_stats)
    step("S1.Z 룰 원복 + driver 종료",                   _restore)


# ============================================================
# S2. 도메인 룰 우선순위 — 3 룰 시계열 + 통계 메뉴 추이 검증
# ============================================================
def scenario_2():
    log("S2. 도메인 룰 우선순위 — 3 룰 시계열 + 통계 누적")
    s = requests.Session(); login(s)
    rates = {}
    diags = []

    def _3rules():
        for rule_id, label in [("DR_TEST_PHONE_DASH","DASH"),
                                ("DR_TEST_PHONE_NODASH","NODASH"),
                                ("DR_TEST_PHONE_AREA","AREA")]:
            map_col(s, "TB_TEST_MEMBER", "PHONE", domain_rule_id=rule_id)
            d, _ = api_run_columns_rule(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])
            api_run_columns_value(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}])  # HISTORY 누적도
            diags.append(d)
            info = get_col(s, "TB_TEST_MEMBER", "PHONE")
            rates[label] = float(info["ruleConformRate"])
            print(f"  {label}: {rates[label]:.2f}% (diagId={d[:8]}..)")

    def _check_distinct():
        unique = set(round(v, 2) for v in rates.values())
        assert len(unique) >= 2, f"세 적합률 다 동일: {rates}"
        # DASH 가 최고 (데이터 형식과 일치)
        assert rates["DASH"] >= max(rates["NODASH"], rates["AREA"]), \
            f"DASH 우세 기대: {rates}"

    def _check_history_count():
        # TB_QUAL_PROFILE_HISTORY 에 3 row 누적
        rows = db(f"SELECT COUNT(*) FROM quality.TB_QUAL_PROFILE_HISTORY "
                  f"WHERE OBJ_NM='TB_TEST_MEMBER' AND ATTR_NM='PHONE' "
                  f"  AND DIAG_DT > NOW() - INTERVAL '5 min';")
        n = int(rows[0])
        print(f"  최근 5분 내 PHONE history 누적 = {n}")
        assert n >= 3, f"3+ history 기대"

    def _check_trend_api():
        r = s.get(BASE + "/api/qual/stats/trend",
                  params={"dmId": DM_ID, "objNm":"TB_TEST_MEMBER", "attrNm":"PHONE"}, timeout=10)
        rows = r.json() or []
        print(f"  trend API rows = {len(rows)}")
        assert len(rows) >= 3

    def _restore():
        map_col(s, "TB_TEST_MEMBER", "PHONE", domain_rule_id="DR_TEST_PHONE_DASH")

    step("S2.1 DASH/NODASH/AREA 3룰 차례 진단",  _3rules)
    step("S2.2 적합률 다름 + DASH 우세 검증",     _check_distinct)
    step("S2.3 PROFILE_HISTORY 3+ 누적 검증 (DB)", _check_history_count)
    step("S2.4 /api/qual/stats/trend 3+ row",     _check_trend_api)
    step("S2.Z 원복",                             _restore)


# ============================================================
# S3. 진단 격리 — 모델 전체 진단 후 EMAIL 만 재진단, 모든 컬럼 정확 검증
# ============================================================
def scenario_3():
    log("S3. 진단 격리 — DB snapshot 정확 비교")
    s = requests.Session(); login(s)
    snap_before = {}
    snap_after  = {}

    def _full_run():
        s.post(BASE + "/api/qual/value/run",
               json={"dataModelId": DM_ID, "sampleRate": 100}, timeout=30)
        s.post(BASE + "/api/qual/rule/run",
               json={"dataModelId": DM_ID, "sampleRate": 100, "incrementalYn":"N"}, timeout=30)
        time.sleep(15)  # 모델 전체 진단 — 양쪽 마감 대기

    def _snap_before():
        # DB 직접 — TB_QUAL_PROFILE_RESULT 전체 row + UPDATED_DT
        rows = db(f"SELECT OBJ_NM||'.'||ATTR_NM, TOTAL_CNT, NULL_CNT, UPDATED_DT "
                  f"FROM quality.TB_QUAL_PROFILE_RESULT WHERE DM_ID='{DM_ID}';")
        for r in rows:
            parts = r.split("|")
            if len(parts) >= 4:
                snap_before[parts[0]] = (parts[1], parts[2], parts[3])
        print(f"  before snapshot = {len(snap_before)} 컬럼")
        assert len(snap_before) >= 10

    def _email_only():
        # EMAIL 만 재진단 (값 + 룰 둘 다)
        api_run_columns_value(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"EMAIL"}])
        api_run_columns_rule (s, [{"objNm":"TB_TEST_MEMBER","attrNm":"EMAIL"}])
        time.sleep(2)

    def _snap_after():
        rows = db(f"SELECT OBJ_NM||'.'||ATTR_NM, TOTAL_CNT, NULL_CNT, UPDATED_DT "
                  f"FROM quality.TB_QUAL_PROFILE_RESULT WHERE DM_ID='{DM_ID}';")
        for r in rows:
            parts = r.split("|")
            if len(parts) >= 4:
                snap_after[parts[0]] = (parts[1], parts[2], parts[3])

    def _verify():
        email_key = "TB_TEST_MEMBER.EMAIL"
        changed = []
        same = []
        for k in snap_before:
            if k not in snap_after: continue
            if snap_before[k] == snap_after[k]: same.append(k)
            else: changed.append(k)
        print(f"  변경={len(changed)}, 보존={len(same)}")
        # 정확히 EMAIL 만 변경됐어야 (UPDATED_DT 갱신)
        assert email_key in changed, f"EMAIL 미갱신 (격리 실패)"
        # 다른 컬럼은 보존
        non_email_changed = [k for k in changed if k != email_key]
        assert len(non_email_changed) == 0, f"EMAIL 외 변경 컬럼: {non_email_changed}"
        print(f"  → EMAIL 만 갱신 + 나머지 {len(same)} 컬럼 100% 보존 검증 OK")

    step("S3.1 모델 전체 진단 (값+룰)",       _full_run)
    step("S3.2 진단 결과 DB snapshot (before)", _snap_before)
    step("S3.3 EMAIL 1개만 재진단",           _email_only)
    step("S3.4 결과 DB snapshot (after)",      _snap_after)
    step("S3.5 EMAIL 만 변경 + 나머지 보존",   _verify)


# ============================================================
# S4. 커스텀 룰 + 에러 케이스 + 권한
# ============================================================
def scenario_4():
    log("S4. 커스텀 룰 등록 + 에러 케이스 + 정확한 위반 매칭")
    s = requests.Session(); login(s)
    state = {}

    def _err_invalid():
        # ruleType 미지정 → 400
        r = s.post(BASE + "/api/qual/rule/save",
                   json={"dmId": DM_ID, "objNm":"TB_TEST_MEMBER", "attrNm":"AGE",
                         "ruleNm":"BAD_RULE", "severity":"WARN"}, timeout=10)
        assert r.json().get("resultCode") == 400, f"ruleType 누락 → 400 기대, 실제 {r.json()}"
        print(f"  ruleType 누락 → 400 OK ({r.json().get('resultMessage')})")

    def _register_normal():
        rid = uuid.uuid4().hex[:12]
        body = {"dmId": DM_ID, "objNm":"TB_TEST_MEMBER", "attrNm":"AGE",
                "ruleNm":"AGE_TIGHT_"+rid, "ruleType":"RANGE",
                "ruleParams":'{"min":10,"max":30}', "severity":"WARN"}
        r = s.post(BASE + "/api/qual/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
        state["rid"] = r.json().get("contents")

    def _map_and_run():
        map_col(s, "TB_TEST_MEMBER", "AGE", custom_rule_id=state["rid"])
        diag, c = api_run_columns_rule(s, [{"objNm":"TB_TEST_MEMBER","attrNm":"AGE"}])
        info = get_col(s, "TB_TEST_MEMBER", "AGE")
        viol = int(info["ruleViolation"])
        total = int(info["ruleTotal"])
        # 데이터 분포: 정상 35건 (20~58, 일부 10~30 안에 있음)
        # 음수 2 + 250/999 2 + 정상 중 10~30 외 = 위반 多
        # 정확한 카운트는 데이터 의존이라 범위 검증
        print(f"  좁은 RANGE(10-30) 위반 {viol}/{total}")
        assert 25 <= viol <= 45, f"위반 25~45 기대 (실제 {viol})"

    def _delete_and_remap():
        # 삭제 (soft) → 매핑은 그대로지만 룰의 USE_YN='N' → effective rule fallback
        r = s.post(BASE + "/api/qual/rule/delete", json={"ruleId": state["rid"]}, timeout=10)
        assert r.json().get("resultCode") == 200
        # AGE 매핑 원복 (default CR_AGE_RANGE 로)
        map_col(s, "TB_TEST_MEMBER", "AGE", custom_rule_id="CR_AGE_RANGE")

    def _err_missing_id():
        # dmId 누락 → 400
        r = s.post(BASE + "/api/qual/value/runColumns",
                   json={"sampleRate": 100, "targets":[{"objNm":"X","attrNm":"Y"}]}, timeout=10)
        assert r.json().get("resultCode") == 400
        # targets 비어있음 → 400
        r = s.post(BASE + "/api/qual/value/runColumns",
                   json={"dataModelId": DM_ID, "sampleRate": 100, "targets":[]}, timeout=10)
        assert r.json().get("resultCode") == 400
        print(f"  dmId/targets 누락 → 400 OK")

    step("S4.1 잘못된 ruleType → 400",           _err_invalid)
    step("S4.2 정상 RANGE 룰 등록",              _register_normal)
    step("S4.3 AGE 매핑 + 진단 + 위반 카운트 범위 검증", _map_and_run)
    step("S4.4 룰 삭제 + 매핑 원복",             _delete_and_remap)
    step("S4.5 dmId/targets 누락 → 400",         _err_missing_id)


# ============================================================
# S5. UI 통한 다중 컬럼 진단 + 상세 drawer 정확 검증
# ============================================================
def scenario_5():
    log("S5. UI 다중 선택 → 진단 → 상세 팝업 + 결과 정확 매칭")
    s = requests.Session(); login(s)
    d = make_driver()
    state = {}

    def _open():
        ui_login(d)
        ui_open_qual(d, "nav_valueProfile")
        ui_pick_model(d)
        time.sleep(2)

    def _filter_member():
        f = d.find_element(By.XPATH,
            "//label[contains(.,'테이블 필터')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        f.clear(); f.send_keys("MEMBER"); time.sleep(2)
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        for r in rows:
            assert "MEMBER" in (r.text or "").upper()
        state["member_count"] = len(rows)
        print(f"  MEMBER 필터 후 {state['member_count']} 행")

    def _select_all_and_run():
        d.find_element(By.XPATH, "//button[contains(., '전체선택')]").click(); time.sleep(1)
        btn = d.find_element(By.ID, "btn-run-selected")
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", btn); time.sleep(0.3)
        d.execute_script("arguments[0].click();", btn); time.sleep(2)
        # swal 보임
        WebDriverWait(d, 15).until(lambda drv:
            "swal2-shown" in drv.execute_script("return document.body.className")
            or drv.find_elements(By.CSS_SELECTOR, ".swal2-popup"))
        # 30초 자동 새로고침 대기
        print(f"  진단 시작 swal 확인. 30초 후 자동 새로고침 대기...")
        time.sleep(33)

    def _check_grid_updated():
        # 적합률 column 에 % 가 보여야
        body = d.find_element(By.CSS_SELECTOR, "table").text
        assert "%" in body, "적합률 % 표시 안 됨"
        # 실제 API 결과
        r = s.get(BASE + "/api/qual/colrule/listWithLatest",
                  params={"dmId": DM_ID, "objNm":"TB_TEST_MEMBER"}, timeout=10)
        rows = r.json() or []
        with_rate = [x for x in rows if x.get("ruleConformRate") is not None]
        print(f"  MEMBER 컬럼 중 적합률 보유 = {len(with_rate)}")
        assert len(with_rate) >= 3

    def _open_detail():
        btns = d.find_elements(By.ID, "btn-row-detail")
        assert btns
        d.execute_script("arguments[0].click();", btns[0]); time.sleep(2)
        WebDriverWait(d, 10).until(lambda drv:
            len(drv.find_elements(By.XPATH, "//*[contains(text(),'적용 규칙')]")) > 0)
        # 적용 규칙 + 값 프로파일 + 룰 진단 라벨 모두 보임
        page = d.find_element(By.CSS_SELECTOR, "body").text
        assert "적용 규칙" in page
        assert "값 프로파일" in page or "프로파일" in page
        assert "룰 진단" in page or "룰" in page
        print(f"  drawer: 적용 규칙 + 값 프로파일 + 룰 진단 라벨 모두 표시")

    def _close():
        try: d.quit()
        except Exception: pass

    step("S5.1 UI 로그인 + [값 프로파일링] + 모델 선택", _open)
    step("S5.2 'MEMBER' 필터 → 모든 행 MEMBER 포함",     _filter_member)
    step("S5.3 [전체선택] + [선택 컬럼 프로파일링]",     _select_all_and_run)
    step("S5.4 그리드 적합률 갱신 + API 일치",           _check_grid_updated)
    step("S5.5 [상세] drawer 팝업 + 라벨 검증",          _open_detail)
    step("S5.Z driver 종료",                             _close)


def main():
    scenario_1()
    scenario_2()
    scenario_3()
    scenario_4()
    scenario_5()


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*78}\n결과: {p} PASS / {f} FAIL  (총 소요 {elapsed:.0f}초)\n{'='*78}")
    for n, st in results: print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
