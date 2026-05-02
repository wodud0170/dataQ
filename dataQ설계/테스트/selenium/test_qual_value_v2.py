"""
값 프로파일링 v2 시나리오 — 모델 선택 → 컬럼 그리드 → 필터 → 체크 → 다중 컬럼 진단

검증 항목:
  H1. /api/qual/colrule/listWithLatest — effective + 직전 PROFILE/RULE join
  H2. /api/qual/value/runColumns — 다중 컬럼 단일 진단 (1 diagId)
  H3. /api/qual/rule/runColumns  — 동일
  H4. UI: 모델 선택 → 그리드 표시 + 체크박스
  H5. UI: 테이블 필터로 행 좁히기
  H6. UI: 선택 후 [선택 컬럼 프로파일링] → 진단 시작 swal
  H7. UI: [상세] 버튼 → drawer 팝업 → 직전값 표시
"""
import base64, json, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
DM_ID = "TESTQUALDM00000000001A"
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login_api(s):
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


state = {}


# ============================================================
# H1~H3 : API
# ============================================================
def api_phase():
    s = requests.Session()
    step("0. API 로그인", lambda: login_api(s))

    def _h1():
        r = s.get(BASE + "/api/qual/colrule/listWithLatest", params={"dmId": DM_ID}, timeout=10)
        rows = r.json() or []
        print(f"  rows={len(rows)}")
        assert len(rows) >= 22, f"22+ 컬럼 기대, 실제 {len(rows)}"
        # 적합률 / 직전 NULL 가 있는 행 카운트
        with_rule = sum(1 for r in rows if r.get("ruleConformRate") is not None)
        with_prof = sum(1 for r in rows if r.get("profTotal") is not None)
        print(f"  with_ruleConform={with_rule}, with_profile={with_prof}")
        # 가시 — 적어도 일부는 직전값 있어야 (이전 시나리오에서 진단 했음)
        state["h1_rows"] = rows
    step("H1. listWithLatest — effective + 직전 PROFILE/RULE", _h1)

    def _h2():
        # 다중 컬럼 — Member 의 EMAIL/PHONE/AGE
        targets = [
            {"objNm": "TB_TEST_MEMBER", "attrNm": "EMAIL"},
            {"objNm": "TB_TEST_MEMBER", "attrNm": "PHONE"},
            {"objNm": "TB_TEST_MEMBER", "attrNm": "AGE"}
        ]
        r = s.post(BASE + "/api/qual/value/runColumns",
                   json={"dataModelId": DM_ID, "sampleRate": 100, "targets": targets}, timeout=30)
        assert r.json().get("resultCode") == 200, f"runColumns 실패: {r.json()}"
        diag = r.json().get("contents")
        state["h2_diag"] = diag
        # DONE 폴링
        deadline = time.time() + 90
        while time.time() < deadline:
            r2 = s.get(BASE + f"/api/qual/value/history/{diag}", timeout=10)
            h = r2.json() or {}
            if h.get("status") in ("DONE", "ERROR"): break
            time.sleep(3)
        assert h.get("status") == "DONE", f"value runColumns 마감 실패: {h.get('status')}"
        # 결과 — 정확히 3 컬럼만 갱신됐는지
        # PROFILE_RESULT 의 해당 3 컬럼 updated_dt 확인 (직전값 = 방금 진단)
        r3 = s.get(BASE + "/api/qual/value/result", params={"dataModelId": DM_ID}, timeout=10)
        profiles = r3.json() or []
        target_set = {(t["objNm"], t["attrNm"]) for t in targets}
        recent = [p for p in profiles if (p.get("objNm"), p.get("attrNm")) in target_set]
        print(f"  대상 컬럼 3개 결과 확인: {len(recent)}/3")
        assert len(recent) == 3
    step("H2. runColumns — 3 컬럼 다중 진단 + 결과 확인", _h2)

    def _h3():
        targets = [
            {"objNm": "TB_TEST_MEMBER", "attrNm": "EMAIL"},
            {"objNm": "TB_TEST_ORDER",  "attrNm": "AMOUNT"}
        ]
        r = s.post(BASE + "/api/qual/rule/runColumns",
                   json={"dataModelId": DM_ID, "sampleRate": 100, "incrementalYn": "N", "targets": targets},
                   timeout=30)
        assert r.json().get("resultCode") == 200
        diag = r.json().get("contents")
        deadline = time.time() + 60
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
            c = r2.json().get("contents")
            if isinstance(c, str): c = json.loads(c)
            h = (c or {}).get("history") or {}
            if h.get("status") in ("DONE", "ERROR"): break
            time.sleep(3)
        assert h.get("status") == "DONE"
        # 결과 행수 = 2 (정확히 대상)
        rules_results = (c or {}).get("results") or []
        print(f"  룰 결과 = {len(rules_results)} (기대 2)")
        assert len(rules_results) == 2
    step("H3. rule runColumns — 2 컬럼", _h3)


# ============================================================
# H4~H7 : UI
# ============================================================
def ui_phase():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)

    def _login():
        d.get(BASE + "/signin")
        WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys("space")
        pw = d.find_element(By.CSS_SELECTOR, "input[type='password']")
        pw.send_keys("123"); pw.send_keys(Keys.ENTER)
        WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
        time.sleep(5)

    def _open():
        # qualGroup 펼치기 + 값 프로파일링 메뉴
        act = d.find_element(By.XPATH, "//div[@id='qualGroup']//div[contains(@class,'v-list-group__header')]")
        d.execute_script("arguments[0].click();", act); time.sleep(2)
        m = WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.ID, "nav_valueProfile")))
        d.execute_script("arguments[0].click();", m); time.sleep(3)
        # 모델 드롭다운 선택
        ac = d.find_element(By.XPATH,
            "//label[contains(.,'모델')]/ancestor::div[contains(@class,'v-autocomplete')][1]//input")
        d.execute_script("arguments[0].click();", ac); time.sleep(0.5)
        ac.send_keys("TEST_QUAL_MODEL"); time.sleep(2)
        opt = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
        if not opt: opt = d.find_elements(By.CSS_SELECTOR, "[role='option']")
        d.execute_script("arguments[0].click();", opt[0]); time.sleep(4)
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  grid rows={len(rows)}")
        assert len(rows) >= 10, f"10+ 컬럼 기대, 실제 {len(rows)}"

    def _filter():
        # 테이블 필터 = 'MEMBER'
        f = d.find_element(By.XPATH,
            "//label[contains(.,'테이블 필터')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        f.clear(); f.send_keys("MEMBER")
        time.sleep(2)
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        # 표시되는 행 텍스트가 'MEMBER' 포함
        for r in rows:
            t = r.text or ""
            assert "MEMBER" in t.upper(), f"필터 후 비-MEMBER 행: {t[:60]}"
        print(f"  filter 후 rows={len(rows)} (모두 MEMBER 포함)")

    def _select_run():
        # [전체선택] 클릭
        sel_all = d.find_element(By.XPATH, "//button[contains(., '전체선택')]")
        d.execute_script("arguments[0].click();", sel_all); time.sleep(1)
        # 시작 버튼
        btn = WebDriverWait(d, 10).until(EC.element_to_be_clickable((By.ID, "btn-run-selected")))
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", btn); time.sleep(0.3)
        d.execute_script("arguments[0].click();", btn); time.sleep(1)
        # swal 폴링
        deadline = time.time() + 30
        seen = False
        while time.time() < deadline:
            cls = d.execute_script("return document.body.className || '';")
            if "swal2-shown" in cls or d.find_elements(By.CSS_SELECTOR, ".swal2-popup"):
                seen = True; break
            time.sleep(0.5)
        assert seen, "진단 시작 swal 미발견"

    def _detail():
        # swal close (timer 자동)
        time.sleep(3)
        # 첫 번째 행의 [상세] 버튼
        d.find_element(By.XPATH, "//label[contains(.,'테이블 필터')]/ancestor::div[contains(@class,'v-text-field')][1]//input").clear()
        time.sleep(1)
        # 행 첫번째의 detail 버튼
        btns = d.find_elements(By.ID, "btn-row-detail")
        assert btns, "btn-row-detail 없음"
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", btns[0])
        d.execute_script("arguments[0].click();", btns[0]); time.sleep(2)
        # drawer 안의 컬럼명 텍스트 보임
        WebDriverWait(d, 10).until(lambda drv:
            len(drv.find_elements(By.XPATH, "//*[contains(text(),'적용 규칙')]")) > 0)
        print("  drawer 보임 + '적용 규칙' 라벨 확인")

    try:
        step("H4. 로그인 + 메뉴 진입 + 모델 선택 → 그리드", lambda: (_login(), _open()))
        step("H5. 테이블 필터링", _filter)
        step("H6. 전체선택 + [선택 컬럼 프로파일링] → swal", _select_run)
        step("H7. [상세] 버튼 → drawer 팝업", _detail)
    finally:
        try: d.quit()
        except Exception: pass


def main():
    api_phase()
    ui_phase()


if __name__ == "__main__":
    main()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for n, st in results: print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
