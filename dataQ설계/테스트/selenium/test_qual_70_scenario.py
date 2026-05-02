"""
70번 시나리오 검증 — 도메인 룰 1:N + 컬럼별 단일 적용 + 단위 재진단 + 통계

시나리오:
  G1. effective rule 매퍼 — 컬럼당 1행 + EXCLUDE/CUSTOM/DOMAIN/DEFAULT/NONE 분류
  G2. 모델 단위 진단 (1차) — PHONE 은 default(SORT_ORD=1, '-' 패턴)
       → 모든 PHONE 데이터는 '-' 형식이라 위반 0~소수
  G3. 시뮬레이션 — '-' 없는 데이터 컬럼이 있다고 가정
       PHONE 컬럼의 적용 룰을 SORT_ORD=2 (NODASH) 로 변경
       → 그 컬럼만 재진단 → 위반률 변화 검증
  G4. 컬럼 진단 제외 토글
  G5. HISTORY 시계열 — 진단 후 행수 증가 확인 (통계 메뉴 데이터 소스)
  G6. UI 메뉴 진입 — 컬럼 규칙 매핑 + 진단 통계
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


def login(s):
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


state = {}


def main():
    s = requests.Session()
    step("0. 로그인", lambda: login(s))

    # ---------- G1: effective rule 매퍼 ----------
    def _eff():
        r = s.get(BASE + "/api/qual/colrule/list", params={"dmId": DM_ID}, timeout=10)
        rows = r.json() or []
        print(f"  total cols={len(rows)}")
        sources = {}
        for row in rows:
            src = row.get("effectiveSource") or "?"
            sources[src] = sources.get(src, 0) + 1
        print(f"  sources={sources}")
        assert len(rows) >= 22, f"22+ 컬럼 기대"
        # 적어도 EXCLUDED, CUSTOM, DOMAIN 모두 존재
        assert sources.get("EXCLUDED", 0) >= 1, "EXCLUDED 없음"
        assert sources.get("CUSTOM",  0) >= 1, "CUSTOM 없음"
        assert sources.get("DOMAIN",  0) >= 1, "DOMAIN 없음"
        state["eff_rows"] = rows
    step("G1. effective rule 매퍼 (소스 분류)", _eff)

    # ---------- G2: 모델 단위 진단 (1차) ----------
    def _g2_run():
        r = s.post(BASE + "/api/qual/rule/run", json={"dataModelId": DM_ID, "sampleRate": 100}, timeout=30)
        diag = r.json().get("contents")
        state["g2_diag"] = diag
        # 마감 폴링
        deadline = time.time() + 180
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
            c = r2.json().get("contents")
            if isinstance(c, str): c = json.loads(c)
            h = (c or {}).get("history") or {}
            st = h.get("status")
            if st in ("DONE","ERROR"): break
            time.sleep(5)
        assert h.get("status") == "DONE", f"진단 마감 실패 {h.get('status')} {h.get('errorMsg')}"
        # PHONE 컬럼의 위반 카운트 (default = '-' 패턴) 기록
        for r2 in (c or {}).get("results") or []:
            if r2.get("objNm") == "TB_TEST_MEMBER" and r2.get("attrNm") == "PHONE":
                state["g2_phone_viol"] = r2.get("violationCnt")
                state["g2_phone_total"] = r2.get("totalCnt")
                print(f"  PHONE (1차, default '-'): violation={r2.get('violationCnt')} / total={r2.get('totalCnt')}")
                break
    step("G2. 모델 단위 진단 + PHONE default 위반 카운트", _g2_run)

    # ---------- G3: PHONE 컬럼 룰 변경 → 재진단 ----------
    def _g3_change():
        # PHONE 도메인 룰 SORT_ORD=2 (NODASH) 의 ID
        body = {"dmId": DM_ID, "objNm": "TB_TEST_MEMBER", "attrNm": "PHONE",
                "domainRuleId": "DR_TEST_PHONE_NODASH", "customRuleId": None, "excludeYn": "N"}
        r = s.post(BASE + "/api/qual/colrule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200, f"colrule save 실패: {r.json()}"

    def _g3_rediag():
        r = s.post(BASE + "/api/qual/rule/runColumn",
                   json={"dataModelId": DM_ID, "objNm": "TB_TEST_MEMBER", "attrNm": "PHONE",
                         "sampleRate": 100, "incrementalYn": "N"}, timeout=30)
        diag = r.json().get("contents")
        state["g3_diag"] = diag
        deadline = time.time() + 60
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
            c = r2.json().get("contents")
            if isinstance(c, str): c = json.loads(c)
            h = (c or {}).get("history") or {}
            if h.get("status") in ("DONE","ERROR"): break
            time.sleep(3)
        assert h.get("status") == "DONE"
        for r2 in (c or {}).get("results") or []:
            if r2.get("objNm") == "TB_TEST_MEMBER" and r2.get("attrNm") == "PHONE":
                state["g3_phone_viol"] = r2.get("violationCnt")
                state["g3_phone_total"] = r2.get("totalCnt")
                print(f"  PHONE (2차, NODASH 패턴): violation={r2.get('violationCnt')} / total={r2.get('totalCnt')}")
                break
        # 위반 카운트가 1차와 다르면 룰 변경 효과
        # 우리 데이터는 모두 '-' 형식 → NODASH 로 바꾸면 거의 다 위반
        v1 = state.get("g2_phone_viol")
        v2 = state.get("g3_phone_viol")
        assert v1 != v2, f"룰 변경 후에도 위반 카운트 동일 (v1={v1}, v2={v2}) — 매핑 미적용"
        print(f"  → 룰 변경 효과 검증 OK: {v1} → {v2}")

    step("G3a. PHONE 컬럼 룰 NODASH 로 매핑 변경", _g3_change)
    step("G3b. PHONE 컬럼만 재진단 → 위반 카운트 변화 검증", _g3_rediag)

    # ---------- G4: 컬럼 진단 제외 토글 ----------
    def _g4_exclude():
        body = {"dmId": DM_ID, "objNm": "TB_TEST_MEMBER", "attrNm": "PHONE",
                "domainRuleId": None, "customRuleId": None, "excludeYn": "Y"}
        r = s.post(BASE + "/api/qual/colrule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
        # effective rule 다시 조회
        r2 = s.get(BASE + "/api/qual/colrule/list",
                   params={"dmId": DM_ID, "objNm": "TB_TEST_MEMBER", "attrNm": "PHONE"}, timeout=10)
        rows = r2.json() or []
        assert rows and rows[0].get("effectiveSource") == "EXCLUDED", f"EXCLUDED 안 됨: {rows[0]}"
        print(f"  PHONE 진단 제외 토글 후 effectiveSource={rows[0].get('effectiveSource')}")

    step("G4. 컬럼 진단 제외 토글", _g4_exclude)

    # ---------- G5: HISTORY 누적 ----------
    def _g5_history():
        # 값 진단 1회 실행 — HISTORY 행 추가
        r = s.post(BASE + "/api/qual/value/run", json={"dataModelId": DM_ID, "sampleRate": 100}, timeout=30)
        diag = r.json().get("contents")
        deadline = time.time() + 180
        while time.time() < deadline:
            r2 = s.get(BASE + f"/api/qual/value/history/{diag}", timeout=10)
            h = r2.json() or {}
            if h.get("status") in ("DONE","ERROR"): break
            time.sleep(5)
        assert h.get("status") == "DONE"
        # trend 조회
        r3 = s.get(BASE + "/api/qual/stats/trend", params={"dmId": DM_ID}, timeout=10)
        rows = r3.json() or []
        print(f"  trend rows={len(rows)}")
        assert len(rows) >= 22, f"trend 22+ 행 기대 (방금 진단 22 컬럼)"

    step("G5. 값 진단 → HISTORY 누적 → trend API", _g5_history)

    # ---------- G6: UI 메뉴 진입 ----------
    def _g6_ui():
        opts = webdriver.EdgeOptions()
        opts.add_argument("--log-level=3")
        opts.add_experimental_option("excludeSwitches", ["enable-logging"])
        d = webdriver.Edge(options=opts)
        d.set_window_size(1600, 1000)
        try:
            d.get(BASE + "/signin")
            WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
            d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys("space")
            pw = d.find_element(By.CSS_SELECTOR, "input[type='password']")
            pw.send_keys("123"); pw.send_keys(Keys.ENTER)
            WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
            time.sleep(5)

            # qualGroup 펼치기
            act = d.find_element(By.XPATH, "//div[@id='qualGroup']//div[contains(@class,'v-list-group__header')]")
            d.execute_script("arguments[0].click();", act); time.sleep(2)

            # 컬럼 규칙 매핑 메뉴
            m = WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.ID, "nav_qualColRule")))
            d.execute_script("arguments[0].click();", m); time.sleep(3)
            d.find_element(By.ID, "btn-colrule-reload")  # 존재 확인
            print("  qualColRule 메뉴 OK")

            # 진단 통계 메뉴
            m2 = WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.ID, "nav_qualStats")))
            d.execute_script("arguments[0].click();", m2); time.sleep(3)
            d.find_element(By.ID, "btn-stats-load")
            print("  qualStats 메뉴 OK")
        finally:
            d.quit()

    step("G6. UI 메뉴 진입 (컬럼 규칙 매핑 + 진단 통계)", _g6_ui)


if __name__ == "__main__":
    main()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for n, st in results: print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
