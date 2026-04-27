"""
컬럼 인라인 UPDATE — dataType 보존 + 한글명 변경 시 표준 강등 회귀 테스트

배경:
  saveAttrs UPDATE 분기를 정합성 보강 (오늘 작업, e4aaeaf 와 별개):
   - 기존: 매번 termsStndYn='N' 강제 + dataType/dataLen 빈값으로 덮어쓰기 = 데이터 손상
   - 변경: 기존 ATTR 먼저 select → dataType/dataLen/wordLst 보존
     → 한글명이 변경됐을 때만 termsStndYn='N' 강등 (영문명-한글명 매칭 깨짐 회피)
     → 한글명 동일하면 termsStndYn 기존값 보존 (PK/null/FK 만 바꾼 케이스)

흐름:
  1. 로그인
  2. 새 논리 모델 생성
  3. 테이블 1개 + 컬럼 1개 추가 (한글명 '고객명' → TMP_COL_, termsStndYn='N')
  4. resolveAttrs 호출 → '고객명' 표준 매칭 → termsStndYn='Y', attrNm='CUST_NM',
                                              dataType=VARCHAR, dataLen=100
  5. **UPDATE 케이스 A**: 한글명 그대로, nullableYn 만 'Y'→'N'
     → 검증: termsStndYn='Y' **보존**, dataType/dataLen 그대로
  6. **UPDATE 케이스 B**: 한글명 '고객명' → '주문일자' 로 변경
     → 검증: termsStndYn='N' **강등**, dataType/dataLen 그대로 (보존)
  7. cleanup
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
MODEL_NAME = "E2E_UPD_" + datetime.now().strftime("%m%d%H%M%S")
TABLE_KR = "고객정보"
ORIG_KR  = "고객명"
NEW_KR   = "주문일자"

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
    path = os.path.join(SCREENSHOT_DIR, "upd_meta_" + name + ".png")
    d.save_screenshot(path)
    print(f"  [SHOT] {name}")


def login(d, user="space", pw="123"):
    d.get(BASE_URL + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw); pw_in.send_keys(Keys.ENTER)
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


def fetch_attr(state_, attr_nm):
    """현재 모델의 특정 attr 단건 조회"""
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": state_["modelId"], "objNm": state_["objNm"]},
                     cookies=state_["cookies"], timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    return next((a for a in attrs if a.get("attrNm") == attr_nm), None)


# ---------- steps ----------
def step1_login(d):
    login(d); shot(d, "01_login")
    state["cookies"] = {c["name"]: c["value"] for c in d.get_cookies()}


def step2_create_model(d):
    """API 로 모델 생성 (UI 보다 빠름)"""
    r = requests.post(BASE_URL + "/api/dm/createDataModel", cookies=state["cookies"],
                      json={"dataModelNm": MODEL_NAME, "ver": "1.0"}, timeout=10)
    r.raise_for_status()
    res = r.json()
    if not (res.get("resultCode") == 200):
        raise RuntimeError(f"createDataModel 실패: {res}")
    # 모델 ID 조회
    r = requests.post(BASE_URL + "/api/dm/getDataModelStatsList", cookies=state["cookies"], json={}, timeout=10)
    r.raise_for_status()
    target = next((m for m in r.json() if m.get("dataModelNm") == MODEL_NAME), None)
    if not target:
        raise RuntimeError(f"생성한 모델 '{MODEL_NAME}' 조회 실패")
    state["modelId"] = target["dataModelId"]
    print(f"  [info] modelId={state['modelId']}")


def step3_add_table_and_attr(d):
    """테이블 추가 (addObj) + 컬럼 추가 (saveAttrs ADD with attrNmKr only)"""
    # 테이블 추가 — addObj API
    r = requests.post(BASE_URL + "/api/dm/addObj", cookies=state["cookies"], json={
        "dataModelId": state["modelId"], "objNmKr": TABLE_KR,
    }, timeout=10)
    r.raise_for_status()
    if not (r.json().get("resultCode") == 200):
        raise RuntimeError(f"addObj 실패: {r.json()}")
    # 추가된 OBJ 조회
    r = requests.get(BASE_URL + "/api/dm/getDataModelObjListByClctId",
                     params={"clctId": state["modelId"]}, cookies=state["cookies"], timeout=10)
    r.raise_for_status()
    objs = r.json() or []
    target_obj = next((o for o in objs if o.get("objNmKr") == TABLE_KR), None)
    if not target_obj:
        raise RuntimeError("테이블 추가 후 조회 실패")
    state["objNm"] = target_obj["objNm"]
    print(f"  [info] objNm={state['objNm']} (TMP_TBL_*)")

    # 컬럼 추가
    r = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json={
        "dataModelId": state["modelId"], "objNm": state["objNm"],
        "attrs": [{"mode": "ADD", "attrNmKr": ORIG_KR, "pkYn": "N", "fkYn": "N", "nullableYn": "Y"}],
    }, timeout=10)
    r.raise_for_status()
    if not (r.json().get("resultCode") == 200):
        raise RuntimeError(f"saveAttrs ADD 실패: {r.json()}")
    # 추가된 컬럼 확인 (TMP_COL_*, termsStndYn='N')
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": state["modelId"], "objNm": state["objNm"]},
                     cookies=state["cookies"], timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    target_attr = next((a for a in attrs if a.get("attrNmKr") == ORIG_KR), None)
    if not target_attr:
        raise RuntimeError("컬럼 추가 후 조회 실패")
    state["tempAttrNm"] = target_attr["attrNm"]
    print(f"  [info] 컬럼 추가됨 (변환 전): attrNm={state['tempAttrNm']}, "
          f"termsStndYn={target_attr.get('termsStndYn')}, "
          f"dataType={target_attr.get('dataType')}({target_attr.get('dataLen')})")
    if target_attr.get("termsStndYn") == "Y":
        raise RuntimeError("기대: termsStndYn='N' (변환 전 비표준)")


def step4_resolve(d):
    """resolveAttrs — 비표준 컬럼을 표준 매칭으로 'Y' 로 전환"""
    r = requests.post(BASE_URL + "/api/dm/resolveAttrs", cookies=state["cookies"], json={
        "dataModelId": state["modelId"],
        "attrs": [{"objNm": state["objNm"], "attrNm": state["tempAttrNm"]}],
    }, timeout=15)
    r.raise_for_status()
    res = r.json() or {}
    print(f"  [info] resolveAttrs: tried={res.get('tried')}, succeeded={res.get('succeeded')}, "
          f"failed={res.get('failed')}")
    if (res.get("succeeded") or 0) < 1:
        raise RuntimeError(f"resolveAttrs 실패: {res.get('failedList')}")

    # 변환 후 attrNm 이 바뀜 — 한글명으로 다시 찾기
    r = requests.get(BASE_URL + "/api/dm/getDataModelAttrListByClctId",
                     params={"clctId": state["modelId"], "objNm": state["objNm"]},
                     cookies=state["cookies"], timeout=10)
    r.raise_for_status()
    attrs = r.json() or []
    resolved = next((a for a in attrs if a.get("attrNmKr") == ORIG_KR), None)
    if not resolved:
        raise RuntimeError(f"변환 후 컬럼 조회 실패")
    state["resolvedAttrNm"] = resolved["attrNm"]
    state["resolvedDataType"] = resolved.get("dataType")
    state["resolvedDataLen"] = resolved.get("dataLen")
    state["resolvedDataDecimalLen"] = resolved.get("dataDecimalLen")
    print(f"  [info] 변환 후: attrNm={state['resolvedAttrNm']}, "
          f"termsStndYn={resolved.get('termsStndYn')}, "
          f"dataType={state['resolvedDataType']}({state['resolvedDataLen']})")
    if resolved.get("termsStndYn") != "Y":
        raise RuntimeError(f"기대: termsStndYn='Y' (변환 후), 실제: {resolved.get('termsStndYn')}")
    if not state["resolvedDataType"]:
        raise RuntimeError("dataType 채워지지 않음")


def step5_update_keep_kr(d):
    """UPDATE 케이스 A — 한글명 그대로, nullableYn 만 변경
       검증: termsStndYn='Y' 보존, dataType/dataLen 그대로"""
    r = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json={
        "dataModelId": state["modelId"], "objNm": state["objNm"],
        "attrs": [{
            "mode": "UPDATE",
            "attrNm": state["resolvedAttrNm"],
            "attrNmKr": ORIG_KR,                    # 그대로
            "nullableYn": "N",                      # Y → N
            "pkYn": "N", "fkYn": "N", "defaultVal": "",
        }],
    }, timeout=10)
    r.raise_for_status()
    if not (r.json().get("resultCode") == 200):
        raise RuntimeError(f"UPDATE A 실패: {r.json()}")

    after = fetch_attr(state, state["resolvedAttrNm"])
    if after is None:
        raise RuntimeError("UPDATE A 후 attr 조회 실패")
    print(f"  [info] UPDATE A 후: termsStndYn={after.get('termsStndYn')}, "
          f"dataType={after.get('dataType')}({after.get('dataLen')}), "
          f"nullableYn={after.get('nullableYn')}")
    if after.get("termsStndYn") != "Y":
        raise RuntimeError(f"한글명 안 바꿨는데 termsStndYn 강등됨: {after.get('termsStndYn')}")
    if after.get("dataType") != state["resolvedDataType"]:
        raise RuntimeError(f"dataType 손상: 기대={state['resolvedDataType']}, 실제={after.get('dataType')}")
    if after.get("dataLen") != state["resolvedDataLen"]:
        raise RuntimeError(f"dataLen 손상: 기대={state['resolvedDataLen']}, 실제={after.get('dataLen')}")
    if after.get("nullableYn") != "N":
        raise RuntimeError(f"nullableYn 미반영: 기대=N, 실제={after.get('nullableYn')}")


def step6_update_change_kr(d):
    """UPDATE 케이스 B — 한글명 '고객명' → '주문일자' 변경
       검증: termsStndYn='N' 강등 + dataType/dataLen 보존 (변경 안 됨)"""
    r = requests.post(BASE_URL + "/api/dm/saveAttrs", cookies=state["cookies"], json={
        "dataModelId": state["modelId"], "objNm": state["objNm"],
        "attrs": [{
            "mode": "UPDATE",
            "attrNm": state["resolvedAttrNm"],
            "attrNmKr": NEW_KR,                     # 한글명 변경
            "nullableYn": "N",
            "pkYn": "N", "fkYn": "N", "defaultVal": "",
        }],
    }, timeout=10)
    r.raise_for_status()
    if not (r.json().get("resultCode") == 200):
        raise RuntimeError(f"UPDATE B 실패: {r.json()}")

    after = fetch_attr(state, state["resolvedAttrNm"])
    if after is None:
        raise RuntimeError("UPDATE B 후 attr 조회 실패")
    print(f"  [info] UPDATE B 후: attrNmKr={after.get('attrNmKr')}, "
          f"termsStndYn={after.get('termsStndYn')}, "
          f"dataType={after.get('dataType')}({after.get('dataLen')})")
    if after.get("attrNmKr") != NEW_KR:
        raise RuntimeError(f"한글명 미반영: 기대={NEW_KR}, 실제={after.get('attrNmKr')}")
    if after.get("termsStndYn") != "N":
        raise RuntimeError(f"한글명 변경됐는데 표준 강등 안 됨: {after.get('termsStndYn')}")
    # dataType/dataLen 은 변경 안 되어야 (resolveAttrs 시점 값 그대로)
    if after.get("dataType") != state["resolvedDataType"]:
        raise RuntimeError(f"dataType 손상: 기대={state['resolvedDataType']}, 실제={after.get('dataType')}")
    if after.get("dataLen") != state["resolvedDataLen"]:
        raise RuntimeError(f"dataLen 손상: 기대={state['resolvedDataLen']}, 실제={after.get('dataLen')}")


def step7_cleanup(d):
    """모델 삭제 (cascade)"""
    # body 가 List<StdDataModelVo> 형식이라 array of objects 로
    r = requests.post(BASE_URL + "/api/dm/deleteDataModels", cookies=state["cookies"],
                      json=[{"dataModelId": state["modelId"]}], timeout=10)
    if r.status_code == 200 and (r.json() or {}).get("resultCode") == 200:
        print(f"  [info] 모델 삭제 완료: {MODEL_NAME}")
    else:
        print(f"  [warn] 모델 삭제 실패 (테스트는 PASS 유지): {r.status_code} {r.text[:120]}")


# ---------- main ----------
def main():
    d = make_driver()
    try:
        if not step("1. 로그인", lambda: step1_login(d)): return
        if not step("2. 모델 생성 (API)", lambda: step2_create_model(d)): return
        if not step("3. 테이블 + 컬럼 추가 (TMP_COL, termsStndYn='N')",
                    lambda: step3_add_table_and_attr(d)):
            step("Z. cleanup", lambda: step7_cleanup(d)); return
        if not step("4. resolveAttrs — 표준 매칭 → termsStndYn='Y'",
                    lambda: step4_resolve(d)):
            step("Z. cleanup", lambda: step7_cleanup(d)); return
        step("5. UPDATE A: 한글명 그대로 + null 변경 → termsStndYn 'Y' 보존, meta 보존",
             lambda: step5_update_keep_kr(d))
        step("6. UPDATE B: 한글명 변경 → termsStndYn 'N' 강등, dataType 그대로 보존",
             lambda: step6_update_change_kr(d))
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
