"""
T02 — Oracle 4 schema 수집 시 같은 OBJ_NM 다른 OWNER 분리 저장 검증

전제: 사전에 oracle-xe 컨테이너에 다음 테이블 존재 (사용자가 기 등록)
  · HRM_APP.COMPANY_INFO  · INV_APP.COMPANY_INFO  · SALES_APP.COMPANY_INFO
  · HRM_APP.TB_USER       · INV_APP.TB_USER       · SALES_APP.TB_USER

검증:
  · 수집 후 TB_DATA_MODEL_OBJ 에 (OBJ_OWNER, OBJ_NM) 별로 분리 row
  · 한 OWNER 의 OBJ 가 다른 OWNER 를 덮어쓰지 않음 (PK 분리)
  · 컬럼도 마찬가지로 (OBJ_OWNER, OBJ_NM, ATTR_NM) 별 분리

이전엔 PK = (DM_ID, OBJ_NM) 라 ON CONFLICT 에서 한 쪽이 다른 쪽을 덮어씀.
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, screenshot, db_query,
                    BASE_URL, TestRun, navigate_to_tab)
from selenium.webdriver.common.by import By


def run():
    t = TestRun("T02 수집 — 같은 OBJ_NM 다른 OWNER 분리 저장")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # 수집 전 DB snapshot
        before_rows = db_query("""
            SELECT DM_ID, OBJ_OWNER, OBJ_NM
            FROM TB_DATA_MODEL_OBJ
            WHERE OBJ_NM IN ('COMPANY_INFO','TB_USER') AND USE_YN='Y'
            ORDER BY DM_ID, OBJ_OWNER, OBJ_NM
        """)
        before_count = len(before_rows)
        t.step(f"BEFORE 수집 — COMPANY_INFO/TB_USER row 수", True, f"{before_count}건")

        # NOTE: 실제 수집 트리거는 대시보드에서 사용자가 모델 선택 + [수집 실행].
        #       이 테스트는 수집 후 결과 검증을 가정하고, 사용자가 미리 한 번 수집해 둔다.
        #       자동 수집이 어려운 이유: 수집은 비동기 STOMP 이벤트로 진행되며 파라미터가 많음.
        #       이 테스트는 user 가 수집 후 돌리는 시나리오.
        navigate_to_tab(drv, "tab_datamodelStatusTable")
        time.sleep(2)
        screenshot(drv, "t02_01_table_screen")

        # AFTER 수집 검증 — 같은 OBJ_NM 이 (OBJ_OWNER 별) 분리되어 있는지
        rows = db_query("""
            SELECT OBJ_OWNER, OBJ_NM
            FROM TB_DATA_MODEL_OBJ
            WHERE OBJ_NM IN ('COMPANY_INFO','TB_USER')
              AND OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
              AND USE_YN='Y'
            ORDER BY OBJ_NM, OBJ_OWNER
        """)
        owners_per_objnm = {}
        for owner, obj in rows:
            owners_per_objnm.setdefault(obj, set()).add(owner)

        # 각 OBJ_NM 이 최소 2개 이상 OWNER 로 분리되어 있어야 PK 분리가 동작
        for obj_nm, owners in owners_per_objnm.items():
            ok = len(owners) >= 2
            t.step(f"{obj_nm} 가 ≥2 OWNER 로 분리", ok,
                   f"owners={sorted(owners)}")

        if not owners_per_objnm:
            t.step("같은 OBJ_NM 다른 OWNER 가 모델에 등록됨", False,
                   "수집을 먼저 하셔야 합니다 (HRM_APP/INV_APP/SALES_APP 4스키마 모델 수집)")

        # ATTR 도 분리되어 있는지 — 한 (OWNER, OBJ_NM) 의 컬럼들이 다른 OWNER 와 안 섞이는지
        sql = """
            SELECT OBJ_OWNER, OBJ_NM, COUNT(*) AS cnt
            FROM TB_DATA_MODEL_ATTR
            WHERE OBJ_NM IN ('COMPANY_INFO','TB_USER') AND USE_YN='Y'
              AND OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
            GROUP BY OBJ_OWNER, OBJ_NM
            ORDER BY OBJ_NM, OBJ_OWNER
        """
        attr_rows = db_query(sql)
        for owner, obj, cnt in attr_rows:
            t.step(f"ATTR — {owner}.{obj}", int(cnt) > 0, f"{cnt}개")

        screenshot(drv, "t02_99_done")

    except Exception as e:
        t.step("예외", False, str(e))
        screenshot(drv, "t02_exception")
    finally:
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
