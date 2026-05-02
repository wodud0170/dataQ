"""
67번 데이터 품질 진단 — 스모크 테스트 (API 위주)

시나리오:
  A. 카탈로그 조회 → 카탈로그 → 룰 1건 등록 (NOT_NULL on CAMS.TB_*.PK)
  B. 룰 목록 조회 → 등록 확인
  C. 룰 진단 실행 (sampleRate=10) → diagId 발급 → DONE 또는 ERROR 마감 폴링
  D. 룰 결과 조회
  E. 값 프로파일링 실행 (1만건 샘플) → 30초 후 결과 조회
  F. UI 메뉴 진입 (간단 스모크)
  Z. 정리
"""
import base64
import sys
import time
import traceback

import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"

results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def login(s, user="space", pw="123"):
    enc = base64.b64encode(pw.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


state = {}


def main():
    s = requests.Session()
    if not step("0. 관리자 로그인", lambda: login(s)):
        return

    # 모델 선택 (CAMS — Oracle)
    def _pick_model():
        r = s.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        arr = r.json() or []
        cams = next((m for m in arr if m.get("dataModelNm") == "CAMS"), None)
        assert cams, "CAMS 모델 필요"
        state["dmId"] = cams["dataModelId"]
        # 임의 OBJ + ATTR 하나 추출
        r2 = s.get(BASE + "/api/dm/getDataModelObjListByClctId",
                   params={"clctId": state["dmId"]}, timeout=10)
        objs = r2.json() or []
        assert objs, "OBJ 없음"
        first_obj = objs[0]
        state["objNm"] = first_obj["objNm"]
        r3 = s.get(BASE + "/api/dm/getDataModelAttrListByClctId",
                   params={"clctId": state["dmId"], "objNm": state["objNm"]}, timeout=10)
        attrs = r3.json() or []
        assert attrs, "ATTR 없음"
        state["attrNm"] = attrs[0]["attrNm"]
        print(f"  dmId={state['dmId'][:8]}.. obj={state['objNm']} attr={state['attrNm']}")

    if not step("1. 모델/테이블/컬럼 선택", _pick_model):
        return

    # A. 카탈로그 → 룰 등록
    def _import_rule():
        r = s.get(BASE + "/api/qual/rule/catalog", timeout=10)
        catalog = r.json() or []
        assert catalog, "카탈로그 비어있음"
        # NOT_NULL 카탈로그 선택
        nn = next((c for c in catalog if c.get("ruleType") == "NOT_NULL"), None)
        if nn is None:
            nn = catalog[0]
        body = {
            "dataModelId": state["dmId"],
            "catalogId":   nn["catalogId"],
            "objNm":       state["objNm"],
            "attrNm":      state["attrNm"]
        }
        r2 = s.post(BASE + "/api/qual/rule/importFromCatalog", json=body, timeout=10)
        print(f"  status={r2.status_code} body={r2.json()}")
        assert r2.json().get("resultCode") == 200, "카탈로그 import 실패"
        state["ruleId"] = r2.json().get("contents")
        print(f"  ruleId={state['ruleId'][:8]}..")

    step("A. 카탈로그에서 룰 등록", _import_rule)

    # B. 룰 목록
    def _list_rules():
        rr = s.post(BASE + "/api/qual/rule/list",
                    json={"dmId": state["dmId"], "useYn": "Y"}, timeout=10)
        body = rr.json()
        print(f"  status={rr.status_code} type={type(body).__name__} body[:300]={str(body)[:300]}")
        rules = body if isinstance(body, list) else []
        print(f"  rules count={len(rules)}")
        assert any(isinstance(x, dict) and x.get("ruleId") == state.get("ruleId") for x in rules), \
            "방금 등록한 룰 미발견"

    step("B. 룰 목록 조회", _list_rules)

    # C. 룰 진단 실행
    def _run_rule():
        body = {
            "dataModelId":   state["dmId"],
            "sampleRate":    10,
            "incrementalYn": "N"
        }
        r = s.post(BASE + "/api/qual/rule/run", json=body, timeout=30)
        print(f"  run status={r.status_code} body={r.json()}")
        assert r.json().get("resultCode") == 200, f"run 실패: {r.json()}"
        state["ruleDiagId"] = r.json().get("contents")
        print(f"  diagId={state['ruleDiagId'][:8]}..")

        # DONE/ERROR 폴링 (최대 90초)
        deadline = time.time() + 90
        last_status = None
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result",
                       params={"diagId": state["ruleDiagId"]}, timeout=10)
            content = r2.json().get("contents")
            if isinstance(content, str):
                import json as _json
                content = _json.loads(content)
            h = (content or {}).get("history") or {}
            last_status = h.get("status")
            print(f"    polling status={last_status}")
            if last_status in ("DONE", "ERROR"):
                state["ruleStatus"] = last_status
                state["ruleResult"] = content
                return
            time.sleep(5)
        raise AssertionError(f"룰 진단 마감 timeout, last={last_status}")

    step("C. 룰 진단 실행 + 마감 폴링", _run_rule)

    # D. 결과 출력
    def _print_rule_result():
        c = state.get("ruleResult") or {}
        print(f"  최종 status={state.get('ruleStatus')}")
        h = c.get("history") or {}
        print(f"  totalRules={h.get('totalRules')} totalViolations={h.get('totalViolations')}")
        for r in (c.get("results") or [])[:5]:
            print(f"   - rule={r.get('ruleNm')} obj={r.get('objNm')} attr={r.get('attrNm')} "
                  f"viol={r.get('violationCnt')}/{r.get('totalCnt')} rate={r.get('violationRate')}%")

    step("D. 룰 결과 조회 (요약)", _print_rule_result)

    # E. 값 프로파일링
    def _run_value():
        body = {
            "dataModelId": state["dmId"],
            "objNm":       state["objNm"],
            "sampleRate":  10
        }
        r = s.post(BASE + "/api/qual/value/run", json=body, timeout=30)
        assert r.json().get("resultCode") == 200, f"value run 실패: {r.json()}"
        state["valueDiagId"] = r.json().get("contents")
        print(f"  diagId={state['valueDiagId'][:8]}..")
        # 폴링
        deadline = time.time() + 120
        while time.time() < deadline:
            r2 = s.get(BASE + f"/api/qual/value/history/{state['valueDiagId']}",
                       params={"diagId": state["valueDiagId"]}, timeout=10)
            h = r2.json() or {}
            st = h.get("status")
            print(f"    polling status={st}")
            if st in ("DONE", "ERROR"):
                state["valueStatus"] = st
                state["valueHistory"] = h
                # 결과 요약
                r3 = s.get(BASE + "/api/qual/value/result",
                           params={"dataModelId": state["dmId"], "objNm": state["objNm"]}, timeout=10)
                profiles = r3.json() or []
                print(f"  profile rows={len(profiles)}")
                for p in profiles[:3]:
                    print(f"   - {p.get('attrNm')}: total={p.get('totalCnt')} null={p.get('nullCnt')} distinct={p.get('distinctCnt')}")
                return
            time.sleep(5)
        raise AssertionError("값 프로파일링 timeout")

    step("E. 값 프로파일링 실행 + 결과 조회", _run_value)

    # F. UI 진입 스모크 (Edge headless 가능 시)
    def _ui_smoke():
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
            time.sleep(5)  # Vue mount 대기 충분히
            # v-list-group 의 activator(__header) 클릭이 펼침 트리거 (root div click 은 안 됨)
            WebDriverWait(d, 15).until(EC.presence_of_element_located((By.ID, "qualGroup")))
            if not d.find_elements(By.ID, "nav_ruleManage") or not d.find_elements(By.ID, "nav_ruleManage")[0].is_displayed():
                act = d.find_element(By.XPATH,
                    "//div[@id='qualGroup']//div[contains(@class,'v-list-group__header')]")
                d.execute_script("arguments[0].click();", act); time.sleep(2)
            m = WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.ID, "nav_ruleManage")))
            d.execute_script("arguments[0].click();", m)
            time.sleep(3)
            # 룰 추가 버튼 존재 (관리자)
            btn = d.find_elements(By.ID, "btn-rule-add")
            d.save_screenshot("dataQ설계/테스트/selenium/screenshots/qual_smoke_rule_ui.png")
            assert btn, "btn-rule-add 미발견 (스크린샷 참고)"
        finally:
            d.quit()

    step("F. UI 메뉴 진입 스모크", _ui_smoke)

    # Z. 정리
    def _cleanup():
        if state.get("ruleId"):
            s.post(BASE + "/api/qual/rule/delete", json={"ruleId": state["ruleId"]}, timeout=10)
            print("  룰 soft-delete 완료")

    step("Z. 정리", _cleanup)


if __name__ == "__main__":
    main()
    p = sum(1 for _, st, _ in results if st == "PASS")
    f = sum(1 for _, st, _ in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for name, st, _ in results:
        print(f"  [{st}] {name}")
    sys.exit(0 if f == 0 else 1)
