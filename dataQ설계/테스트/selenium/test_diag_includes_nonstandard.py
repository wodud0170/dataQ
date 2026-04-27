"""
표준 진단 — 비표준 컬럼이 진단 대상에 포함되는지 회귀 테스트

배경:
  e4aaeaf (2026-04-26) 에서 DiagService 가 TERMS_STND_YN='Y' 만 진단 대상으로 필터.
  의도: 비표준(TMP_COL_*) 이 TERM_NOT_EXIST 로 잡혀 준수율이 부당 하락하는 부작용 방지.
  부작용: 사용자 직접 입력한 진짜 비표준 컬럼도 진단에서 빠짐 → 표준 진단 본래 목적
  (= 모델의 표준 유효성 진단) 와 어긋남.

  → 가드 제거: 모든 ATTR 가 진단 대상. 비표준이면 TERM_NOT_EXIST 로 정직하게 잡힘.
  → 준수율은 모델 표준화 완성도를 정직하게 반영.

흐름:
  1. 로그인 (space/123)
  2. cams테스트 모델 ID + 첫 테이블 + 최신 CLCT 조회 (API)
  3. 그 테이블에 임시 비표준 컬럼 1개 추가 (saveAttrs ADD with attrNmKr only)
     → 백엔드가 TMP_COL_N + termsStndYn='N' 자동 채움
  4. 표준 진단 시작 (POST /api/diag/startDiag)
  5. Job 상태 DONE 까지 polling (최대 180초)
  6. 진단 결과 조회 — 우리 임시 컬럼이 결과 row 에 포함됐는지 검증
     (e4aaeaf 가드 살아있었다면 termsStndYn='N' 이라 빠졌을 것)
  7. cleanup — 임시 attr 삭제

검증 포인트:
  A. 진단 Job 이 정상 DONE
  B. 진단 결과 row 수 > 0 (전체 attrs 진단됨)
  C. 우리 임시 컬럼 (TMP_COL_*) 의 진단 결과가 존재
"""
import os
import sys
import time
import traceback
from datetime import datetime

import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
TEST_ATTR_KR = "회귀비표준_" + datetime.now().strftime("%H%M%S")
TARGET_MODEL_NM = "cams테스트용19이름변경"

os.makedirs(SCREENSHOT_DIR, exist_ok=True)
results = []
state = {}


def make_driver():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)
    return d


def shot(d, name):
    path = os.path.join(SCREENSHOT_DIR, "diag_nonstd_" + name + ".png")
    d.save_screenshot(path)
    print(f"  [SHOT] {name}")


def login(d, user="space", pw="123"):
    d.get(BASE_URL + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw)
    pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(1)


def step(name, fn):
    print(f"\n{'=' * 60}\nSTEP: {name}\n{'=' * 60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}")
        print(tb)
        return False


# ---------- steps ----------
def step1_login(d):
    login(d)
    shot(d, "01_login")
    state["cookies"] = {c["name"]: c["value"] for c in d.get_cookies()}


def step2_resolve_model_and_table(d):
    cookies = state["cookies"]
    # 모델 목록 조회 후 cams테스트 ID 식별
    r = requests.post(BASE_URL + "/api/dm/getDataModelStatsList", cookies=cookies, json={}, timeout=10)
    r.raise_for_status()
    target = next((m for m in r.json() if m.get("dataModelNm") == TARGET_MODEL_NM), None)
    if not target:
        raise RuntimeError(f"모델 '{TARGET_MODEL_NM}' 없음")
    state["modelId"] = target["dataModelId"]
    print(f"  [info] modelId={state['modelId']}")

    # 그 모델의 첫 OBJ
    r = requests.get(BASE_URL + "/api/dm/getDataModelObjListByClctId",
                     params={"clctId": state["modelId"]}, cookies=cookies, timeout=10)
    r.raise_for_status()
    objs = r.json() or []
    if not objs:
        raise RuntimeError("OBJ 없음")
    state["objNm"] = objs[0]["objNm"]
    print(f"  [info] objNm={state['objNm']} (총 {len(objs)} 테이블 중 첫 번째)")

    # 최신 CLCT 조회
    r = requests.post(BASE_URL + "/api/dm/selectDataModelClctList", cookies=cookies, json={
        "dataModelId": state["modelId"],
    }, timeout=10)
    if r.status_code == 200:
        clcts = r.json() or []
        if clcts:
            # 가장 최신 (clctEndDt 또는 clctStartDt 기준)
            state["clctId"] = clcts[0].get("dmClctId") or clcts[0].get("clctId")
            print(f"  [info] clctId={state['clctId']}")


def step3_add_temp_attr(d):
    """saveAttrs API 로 한글명만 입력 → 백엔드가 TMP_COL_* + termsStndYn='N' 자동 채움"""
    body = {
        "dataModelId": state["modelId"],
        "objNm": state["objNm"],
        "attrs": [{
            "mode": "ADD",
            "attrNmKr": TEST_ATTR_KR,
            "pkYn": "N", "fkYn": "N", "nullableYn": "Y",
        }],
    }
    r = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json=body, timeout=15)
    r.raise_for_status()
    res = r.json()
    if not (res.get("resultCode") == 200):
        raise RuntimeError(f"saveAttrs 실패: {res}")

    # 확인: 추가된 attr 의 attrNm (TMP_COL_*) 조회
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": state["modelId"], "objNm": state["objNm"]},
                     cookies=state["cookies"], timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    target = next((a for a in attrs if a.get("attrNmKr") == TEST_ATTR_KR), None)
    if not target:
        raise RuntimeError(f"임시 attr '{TEST_ATTR_KR}' 등록 후 조회 실패")
    state["tempAttrNm"] = target["attrNm"]
    state["tempAttrTermsStndYn"] = target.get("termsStndYn")
    print(f"  [info] 임시 attr 추가됨: attrNm={state['tempAttrNm']}, attrNmKr='{TEST_ATTR_KR}', "
          f"termsStndYn={state['tempAttrTermsStndYn']}")
    if state["tempAttrTermsStndYn"] == "Y":
        raise RuntimeError("기대: termsStndYn='N' (비표준), 실제: 'Y'")


def step4_start_diag(d):
    body = {
        "clctId":      state.get("clctId") or state["modelId"],  # CLCT 폐기 후 dataModelId 가 곧 clctId
        "dataModelId": state["modelId"],
    }
    r = requests.post(BASE_URL + "/api/diag/startDiag", cookies=state["cookies"], json=body, timeout=15)
    r.raise_for_status()
    res = r.json()
    if not (res.get("resultCode") == 200):
        raise RuntimeError(f"startDiag 실패: {res}")
    # contents 가 곧 diagJobId (단순 string 형태로 응답)
    c = res.get("contents")
    if isinstance(c, str) and c.strip():
        diag_job_id = c.strip()
    elif isinstance(c, dict):
        diag_job_id = c.get("diagJobId") or c.get("jobId")
    else:
        diag_job_id = None
    if not diag_job_id:
        raise RuntimeError(f"diagJobId 식별 실패. 응답: {res}")
    state["diagJobId"] = diag_job_id
    print(f"  [info] diagJobId={state['diagJobId']}")


def step5_wait_done(d):
    """진단 완료 대기 — getDiagResultList 가 응답할 때까지 polling.
       (cams19 모델 ≈ 366 attr 라 즉시 끝남. timeout 60s 면 충분)"""
    timeout_sec = 60
    interval = 2
    elapsed = 0
    while elapsed < timeout_sec:
        r = requests.get(BASE_URL + "/api/diag/getDiagResultList",
                         params={"diagJobId": state["diagJobId"]},
                         cookies=state["cookies"], timeout=10)
        if r.status_code == 200 and r.text.strip():
            try:
                rows = r.json() or []
            except Exception:
                rows = []
            if rows:
                state["resultRows"] = rows
                print(f"  [info] 진단 완료 — 결과 row {len(rows)}건 ({elapsed}s 만에)")
                return
        time.sleep(interval); elapsed += interval
    raise RuntimeError(f"진단 결과 조회 timeout ({timeout_sec}s)")


def step6_verify_result(d):
    """진단 결과에 우리 임시 attr 가 포함됐는지 검증"""
    rows = state.get("resultRows") or []
    state["resultRowCnt"] = len(rows)
    print(f"  [info] 진단 결과 row 수: {state['resultRowCnt']}")
    if state["resultRowCnt"] == 0:
        raise RuntimeError("진단 결과 0건 — 가드 제거 동작 안 함 (모든 attr 가 진단 대상이어야)")
    # 우리 임시 attr 가 결과에 있는지
    matched = [r for r in rows
               if r.get("attrNm") == state["tempAttrNm"] and r.get("objNm") == state["objNm"]]
    print(f"  [info] 임시 attr({state['tempAttrNm']}) 매칭 결과: {len(matched)}건")
    if not matched:
        raise RuntimeError(
            f"임시 비표준 attr '{state['tempAttrNm']}' 가 진단 결과에 없음 — "
            "비표준 컬럼이 진단 대상에 포함되어야 함"
        )
    sample = matched[0]
    diag_keys = [k for k in ('diagType', 'diagDvCd', 'resultMsg', 'resultDtl') if sample.get(k)]
    print(f"  [info] 첫 매칭 row: { {k: sample.get(k) for k in diag_keys} }")


def step7_cleanup(d):
    """임시 attr 삭제"""
    body = {
        "dataModelId": state["modelId"],
        "objNm": state["objNm"],
        "attrs": [{"mode": "DELETE", "attrNm": state.get("tempAttrNm")}],
    }
    if not state.get("tempAttrNm"):
        print("  [info] 삭제할 임시 attr 없음 (이미 정리됨 또는 미생성)")
        return
    r = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json=body, timeout=10)
    if r.status_code == 200 and (r.json() or {}).get("resultCode") == 200:
        print(f"  [info] 임시 attr '{state['tempAttrNm']}' 삭제 완료")
    else:
        print(f"  [warn] cleanup 실패: {r.status_code} {r.text[:120]}")


# ---------- main ----------
def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. 대상 모델/테이블/CLCT 식별", lambda: step2_resolve_model_and_table(d)): return
        if not step("3. 임시 비표준 attr 추가 (TMP_COL_*, termsStndYn='N')",
                    lambda: step3_add_temp_attr(d)): return
        if not step("4. 표준 진단 시작 (startDiag)", lambda: step4_start_diag(d)):
            step("Z. cleanup", lambda: step7_cleanup(d)); return
        if not step("5. Job 상태 DONE 까지 polling", lambda: step5_wait_done(d)):
            step("Z. cleanup", lambda: step7_cleanup(d)); return
        step("6. 결과 검증 — 비표준 attr 진단 결과에 포함",
             lambda: step6_verify_result(d))
        step("7. cleanup", lambda: step7_cleanup(d))
    finally:
        try: shot(d, "99_final")
        except Exception: pass
        d.quit()

    print(f"\n{'=' * 60}\n결과\n{'=' * 60}")
    pass_cnt = sum(1 for _, s, _ in results if s == "PASS")
    fail_cnt = len(results) - pass_cnt
    for name, status, _ in results:
        mark = "[PASS]" if status == "PASS" else "[FAIL]"
        print(f"  {mark} {name}")
    print(f"\n  총 {len(results)}: PASS {pass_cnt}, FAIL {fail_cnt}")
    sys.exit(0 if fail_cnt == 0 else 1)


if __name__ == "__main__":
    main()
