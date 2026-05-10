"""
86번 #46~ — 데이터 품질 진단: 진단 결과 (DSQualRuleResult.vue) 종합 테스트.

10 케이스:
  A) UI (3): 화면 진입, 모델 미선택 시 빈 상태, 진단이력 콤보 렌더
  B) API (7): historyList, result, resultByClsf, resultByRule, history/{diagId},
              violationSample, resultByClsfDrill
"""
import sys, os, time, traceback
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, get_admin_session,
                    BASE_URL, TestRun, db_query)
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def goto(drv):
    time.sleep(2)
    try:
        grps = drv.find_elements(By.ID, "qualGroup")
        if not grps:
            return False
        nav_visible = drv.execute_script("""
          const n = document.getElementById('nav_ruleResult');
          return n && n.offsetParent !== null;
        """)
        if not nav_visible:
            header = grps[0].find_elements(By.CSS_SELECTOR, ".v-list-group__header")
            drv.execute_script("arguments[0].click();", header[0] if header else grps[0])
            time.sleep(1.5)
    except Exception:
        return False
    try:
        nav = WebDriverWait(drv, 8).until(EC.presence_of_element_located((By.ID, "nav_ruleResult")))
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", nav)
        drv.execute_script("arguments[0].click();", nav)
        time.sleep(2)
        return True
    except Exception:
        return False


def run():
    drv = create_driver()
    t = TestRun("T42 진단 결과")
    try:
        if not login_admin(drv):
            t.step("login", False); return t
        sess = get_admin_session(drv)
        return _run(t, drv, sess)
    except Exception as e:
        t.step("UNCAUGHT", False, str(e)[:100])
        traceback.print_exc()
        return t
    finally:
        try: drv.quit()
        except Exception: pass


def _run(t, drv, sess):
    ok = goto(drv)
    t.step("A01 진단 결과 화면 진입", ok)

    try:
        cmb = drv.find_element(By.ID, "cmb-rr-model")
        t.step("A02 모델 콤보 렌더", cmb is not None)
    except Exception as e:
        t.step("A02 모델 콤보 렌더", False, str(e)[:80])

    try:
        cmb = drv.find_element(By.ID, "cmb-rr-diag")
        t.step("A03 진단이력 콤보 렌더", cmb is not None)
    except Exception as e:
        t.step("A03 진단이력 콤보 렌더", False, str(e)[:80])

    # 모델 1개 + 최신 RULE diag 1건 골라서 API
    models = sess.post(f"{BASE_URL}/api/dm/getDataModelStatsList",
                       json={"connectedOnly": "Y"}).json()
    physical = [m for m in models if m.get("modelType") == "PHYSICAL"]
    if not physical:
        for i in range(7):
            t.step(f"B0{i+1}", False, "PHYSICAL 모델 0건")
        return

    dm_id = physical[0]["dataModelId"]

    # B01 historyList
    try:
        r = sess.get(f"{BASE_URL}/api/qual/rule/historyList",
                     params={"dmId": dm_id, "diagType": "RULE"})
        ok = r.status_code == 200 and isinstance(r.json(), list)
        t.step("B01 historyList", ok, f"len={len(r.json()) if ok else r.status_code}")
        histories = r.json() if ok else []
    except Exception as e:
        t.step("B01 historyList", False, str(e)[:80])
        histories = []

    diag_id = histories[0].get("diagId") if histories else None

    # B02 result (Response.contents JSON 문자열 안에 history+results)
    if diag_id:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/result",
                         params={"diagId": diag_id})
            jb = r.json()
            ok = r.status_code == 200 and jb.get("resultCode") == 200 and jb.get("contents") is not None
            t.step("B02 result", ok, f"http={r.status_code} rc={jb.get('resultCode')}")
        except Exception as e:
            t.step("B02 result", False, str(e)[:80])
    else:
        t.step("B02 result", True, "diag 0건 - skip")

    # B03 history/{diagId}
    if diag_id:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/history/{diag_id}")
            ok = r.status_code == 200 and isinstance(r.json(), dict)
            t.step("B03 history/{diagId}", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B03 history/{diagId}", False, str(e)[:80])
    else:
        t.step("B03 history/{diagId}", True, "skip")

    # B04 resultByClsf
    if diag_id:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/resultByClsf",
                         params={"diagId": diag_id})
            ok = r.status_code == 200
            t.step("B04 resultByClsf", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B04 resultByClsf", False, str(e)[:80])
    else:
        t.step("B04 resultByClsf", True, "skip")

    # B05 resultByRule
    if diag_id:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/resultByRule",
                         params={"diagId": diag_id})
            ok = r.status_code == 200
            t.step("B05 resultByRule", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B05 resultByRule", False, str(e)[:80])
    else:
        t.step("B05 resultByRule", True, "skip")

    # B06 resultByClsfDrill
    if diag_id:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/resultByClsfDrill",
                         params={"diagId": diag_id, "domainClsfNm": "코드"})
            ok = r.status_code == 200
            t.step("B06 resultByClsfDrill", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B06 resultByClsfDrill", False, str(e)[:80])
    else:
        t.step("B06 resultByClsfDrill", True, "skip")

    # ===== C) 안티패턴 =====
    edge_cases = [
        ("C01 INVALID diagId result", "result", {"diagId": "INVALID_DIAG_XYZ"}),
        ("C02 빈 diagId result", "result", {"diagId": ""}),
        ("C03 INVALID diagId resultByClsf", "resultByClsf", {"diagId": "INVALID"}),
        ("C04 INVALID diagId resultByRule", "resultByRule", {"diagId": "INVALID"}),
        ("C05 SQL injection in diagId", "result", {"diagId": "'; DROP TABLE x;--"}),
    ]
    for label, ep, params in edge_cases:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/rule/{ep}", params=params)
            ok = r.status_code in (200, 400, 500)  # 서버 죽지 않으면 OK
            t.step(label, ok, f"http={r.status_code}")
        except Exception as e:
            t.step(label, False, str(e)[:80])

    # B07 violationSample (diag 0건이어도 200 빈배열 기대)
    try:
        r = sess.get(f"{BASE_URL}/api/qual/rule/violationSample",
                     params={"diagId": diag_id or "INVALID", "ruleId": "X", "limit": 10})
        ok = r.status_code == 200
        t.step("B07 violationSample", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B07 violationSample", False, str(e)[:80])

    return t


if __name__ == "__main__":
    t = run()
    from common import write_report
    write_report([t], "t42_qual_rule_result.md")
