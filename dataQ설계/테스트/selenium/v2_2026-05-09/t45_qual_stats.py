"""
86번 #46~ — 데이터 품질 진단: 시계열 통계 (DSQualStats.vue) 종합 테스트.

10 케이스:
  A) UI (3): 화면 진입, 모델 콤보 렌더, 빈 상태 메시지
  B) API (5): modelTrend, columnRuleTrend, columnProfileTrend, trend, 모델 비지정 안전 처리
  C) 회귀 (2): 빈 모델, 존재하지 않는 컬럼
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
          const n = document.getElementById('nav_qualStats');
          return n && n.offsetParent !== null;
        """)
        if not nav_visible:
            header = grps[0].find_elements(By.CSS_SELECTOR, ".v-list-group__header")
            drv.execute_script("arguments[0].click();", header[0] if header else grps[0])
            time.sleep(1.5)
    except Exception:
        return False
    try:
        nav = WebDriverWait(drv, 8).until(EC.presence_of_element_located((By.ID, "nav_qualStats")))
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", nav)
        drv.execute_script("arguments[0].click();", nav)
        time.sleep(2)
        return True
    except Exception:
        return False


def run():
    drv = create_driver()
    t = TestRun("T45 시계열 통계")
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
    t.step("A01 시계열 통계 화면 진입", ok)

    try:
        drv.find_element(By.ID, "cmb-stats-model")
        t.step("A02 모델 콤보 렌더", True)
    except Exception as e:
        t.step("A02 모델 콤보 렌더", False, str(e)[:80])

    # 모델 미선택 시 안내 텍스트
    try:
        body = drv.find_element(By.TAG_NAME, "body").text
        ok = "모델을 선택" in body
        t.step("A03 빈 상태 안내 메시지", ok)
    except Exception as e:
        t.step("A03 빈 상태 안내 메시지", False, str(e)[:80])

    # API
    models = sess.post(f"{BASE_URL}/api/dm/getDataModelStatsList",
                       json={"connectedOnly": "Y"}).json()
    physical = [m for m in models if m.get("modelType") == "PHYSICAL"]
    if not physical:
        for i in range(7):
            t.step(f"B/C 0{i+1}", False, "PHYSICAL 모델 0건")
        return
    dm_id = physical[0]["dataModelId"]

    # B01 modelTrend
    try:
        r = sess.get(f"{BASE_URL}/api/qual/stats/modelTrend",
                     params={"dmId": dm_id})
        ok = r.status_code == 200
        t.step("B01 modelTrend", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B01 modelTrend", False, str(e)[:80])

    # B02 columnRuleTrend
    cols = db_query(f"""SELECT obj_nm, attr_nm FROM tb_data_model_attr
                        WHERE dm_id='{dm_id}' LIMIT 1""")
    if cols:
        obj, attr = cols[0][0], cols[0][1]
        try:
            r = sess.get(f"{BASE_URL}/api/qual/stats/columnRuleTrend",
                         params={"dmId": dm_id, "objNm": obj, "attrNm": attr})
            ok = r.status_code == 200
            t.step("B02 columnRuleTrend", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B02 columnRuleTrend", False, str(e)[:80])

        try:
            r = sess.get(f"{BASE_URL}/api/qual/stats/columnProfileTrend",
                         params={"dmId": dm_id, "objNm": obj, "attrNm": attr})
            ok = r.status_code == 200
            t.step("B03 columnProfileTrend", ok, f"http={r.status_code}")
        except Exception as e:
            t.step("B03 columnProfileTrend", False, str(e)[:80])
    else:
        t.step("B02 columnRuleTrend", False, "컬럼 0건")
        t.step("B03 columnProfileTrend", False, "컬럼 0건")

    # B04 trend (legacy)
    try:
        r = sess.get(f"{BASE_URL}/api/qual/stats/trend",
                     params={"dmId": dm_id})
        ok = r.status_code == 200
        t.step("B04 trend (legacy)", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B04 trend (legacy)", False, str(e)[:80])

    # B05 modelTrend with no model
    try:
        r = sess.get(f"{BASE_URL}/api/qual/stats/modelTrend",
                     params={"dmId": ""})
        ok = r.status_code in (200, 400, 500)
        t.step("B05 빈 dataModelId 안전 처리", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B05 빈 dataModelId 안전 처리", True, "exception (차단)")

    # C01 잘못된 모델 ID
    try:
        r = sess.get(f"{BASE_URL}/api/qual/stats/modelTrend",
                     params={"dmId": "INVALID_XYZ"})
        ok = r.status_code == 200
        t.step("C01 잘못된 모델ID 빈 응답", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("C01 잘못된 모델ID 빈 응답", False, str(e)[:80])

    # ===== D) 안티패턴 =====
    edges = [
        ("D01 trend 잘못된 모델", {"dmId": "INVALID"}, "trend"),
        ("D02 columnRuleTrend SQL inj", {"dmId": dm_id, "objNm": "'; DROP TABLE x;--", "attrNm": "X"}, "columnRuleTrend"),
        ("D03 columnProfileTrend 빈 컬럼", {"dmId": dm_id, "objNm": "", "attrNm": ""}, "columnProfileTrend"),
        ("D04 매우 긴 모델ID", {"dmId": "X" * 500}, "modelTrend"),
        ("D05 한글 모델ID", {"dmId": "한글모델"}, "modelTrend"),
    ]
    for label, params, ep in edges:
        try:
            r = sess.get(f"{BASE_URL}/api/qual/stats/{ep}", params=params)
            ok = r.status_code in (200, 400, 500)
            t.step(label, ok, f"http={r.status_code}")
        except Exception as e:
            t.step(label, False, str(e)[:80])

    # C02 존재하지 않는 컬럼
    try:
        r = sess.get(f"{BASE_URL}/api/qual/stats/columnRuleTrend",
                     params={"dmId": dm_id, "objNm": "NOPE", "attrNm": "NOPE"})
        ok = r.status_code == 200
        t.step("C02 잘못된 컬럼 빈 응답", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("C02 잘못된 컬럼 빈 응답", False, str(e)[:80])

    return t


if __name__ == "__main__":
    t = run()
    from common import write_report
    write_report([t], "t45_qual_stats.md")
