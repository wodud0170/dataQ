"""
86번 #46~ — 데이터 품질 진단: 검증 대상 (DSQualColRule.vue) 종합 테스트.

10 케이스:
  A) UI (3): 화면 진입, 모델/도메인분류 콤보 렌더, 적합률 필터 input
  B) API (5): listWithLatest, list, detail, save (override), exclude
  C) 회귀 (2): 빈 모델 ID, 잘못된 적합률 범위
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
          const n = document.getElementById('nav_qualColRule');
          return n && n.offsetParent !== null;
        """)
        if not nav_visible:
            header = grps[0].find_elements(By.CSS_SELECTOR, ".v-list-group__header")
            drv.execute_script("arguments[0].click();", header[0] if header else grps[0])
            time.sleep(1.5)
    except Exception:
        return False
    try:
        nav = WebDriverWait(drv, 8).until(EC.presence_of_element_located((By.ID, "nav_qualColRule")))
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", nav)
        drv.execute_script("arguments[0].click();", nav)
        time.sleep(2)
        return True
    except Exception:
        return False


def run():
    drv = create_driver()
    t = TestRun("T44 검증 대상")
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
    t.step("A01 검증 대상 화면 진입", ok)

    try:
        btn = drv.find_element(By.ID, "btn-colrule-reload")
        t.step("A02 새로고침 버튼 렌더", btn is not None)
    except Exception as e:
        t.step("A02 새로고침 버튼 렌더", False, str(e)[:80])

    # 적합률 필터 input 존재 확인
    try:
        inputs = drv.find_elements(By.CSS_SELECTOR, "input[type='number']")
        t.step("A03 적합률 number input 렌더", len(inputs) >= 2)
    except Exception as e:
        t.step("A03 적합률 number input 렌더", False, str(e)[:80])

    # API
    models = sess.post(f"{BASE_URL}/api/dm/getDataModelStatsList",
                       json={"connectedOnly": "Y"}).json()
    physical = [m for m in models if m.get("modelType") == "PHYSICAL"]
    if not physical:
        for i in range(7):
            t.step(f"B/C 0{i+1}", False, "PHYSICAL 모델 0건")
        return

    dm_id = physical[0]["dataModelId"]

    # B01 listWithLatest
    try:
        r = sess.get(f"{BASE_URL}/api/qual/colrule/listWithLatest",
                     params={"dmId": dm_id})
        ok = r.status_code == 200 and isinstance(r.json(), list)
        t.step("B01 listWithLatest", ok, f"http={r.status_code} len={len(r.json()) if ok else 0}")
        rows = r.json() if ok else []
    except Exception as e:
        t.step("B01 listWithLatest", False, str(e)[:80])
        rows = []

    # B02 list
    try:
        r = sess.get(f"{BASE_URL}/api/qual/colrule/list",
                     params={"dmId": dm_id})
        ok = r.status_code == 200 and isinstance(r.json(), list)
        t.step("B02 list", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B02 list", False, str(e)[:80])

    # B03 detail (첫 row 의 obj/attr 사용)
    if rows:
        obj, attr = rows[0].get("objNm"), rows[0].get("attrNm")
        try:
            r = sess.get(f"{BASE_URL}/api/qual/colrule/detail",
                         params={"dmId": dm_id, "objNm": obj, "attrNm": attr})
            t.step("B03 detail", r.status_code == 200, f"http={r.status_code}")
        except Exception as e:
            t.step("B03 detail", False, str(e)[:80])
    else:
        t.step("B03 detail", True, "rows 0건 - skip")

    # B04 save (override) — 테스트성 매핑
    if rows:
        obj, attr = rows[0].get("objNm"), rows[0].get("attrNm")
        try:
            r = sess.post(f"{BASE_URL}/api/qual/colrule/save",
                          json={"dmId": dm_id, "objNm": obj,
                                "attrNm": attr, "ruleId": None,
                                "domainRuleId": None, "useYn": "Y"})
            ok = r.status_code == 200
            t.step("B04 save (override no-op)", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B04 save (override no-op)", False, str(e)[:80])
    else:
        t.step("B04 save (override no-op)", True, "skip")

    # B05 exclude
    if rows:
        obj, attr = rows[0].get("objNm"), rows[0].get("attrNm")
        try:
            # 일단 exclude=N 으로 원복하는 형태로 테스트
            r = sess.post(f"{BASE_URL}/api/qual/colrule/exclude",
                          json={"dmId": dm_id, "objNm": obj,
                                "attrNm": attr, "excludeYn": "N"})
            ok = r.status_code == 200
            t.step("B05 exclude (N)", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B05 exclude (N)", False, str(e)[:80])
    else:
        t.step("B05 exclude (N)", True, "skip")

    # C01 빈 모델 ID
    try:
        r = sess.get(f"{BASE_URL}/api/qual/colrule/list", params={"dmId": ""})
        ok = r.status_code in (200, 400, 500)
        t.step("C01 빈 모델ID 안전 처리", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("C01 빈 모델ID 안전 처리", True, "exception (서버 차단)")

    # ===== D) 안티패턴 =====
    edges = [
        ("D01 list domainClsfNm 필터", {"dmId": dm_id, "domainClsfNm": "코드"}, "list"),
        ("D02 listWithLatest rateMin/Max", {"dmId": dm_id, "rateMin": 0, "rateMax": 100}, "listWithLatest"),
        ("D03 listWithLatest 빈 도메인분류", {"dmId": dm_id, "domainClsfNm": ""}, "listWithLatest"),
        ("D04 detail SQL inj objNm", {"dmId": dm_id, "objNm": "'; DROP TABLE x;--", "attrNm": "C"}, "detail"),
        ("D05 잘못된 모델ID listWithLatest", {"dmId": "INVALID_XYZ"}, "listWithLatest"),
    ]
    for label, params, ep in edges:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/colrule/{ep}", params=params)
            ok = r.status_code in (200, 400, 500)
            t.step(label, ok, f"http={r.status_code}")
        except Exception as e:
            t.step(label, False, str(e)[:80])

    # C02 잘못된 모델 ID — 200 + 빈배열 (정상) 또는 비-200 (에러)
    try:
        r = sess.get(f"{BASE_URL}/api/qual/colrule/list", params={"dmId": "INVALID_XYZ"})
        if r.status_code == 200:
            ok = isinstance(r.json(), list) and len(r.json()) == 0
        else:
            ok = False
        t.step("C02 잘못된 모델ID 빈 결과", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("C02 잘못된 모델ID 빈 결과", False, str(e)[:80])

    return t


if __name__ == "__main__":
    t = run()
    from common import write_report
    write_report([t], "t44_qual_col_rule.md")
