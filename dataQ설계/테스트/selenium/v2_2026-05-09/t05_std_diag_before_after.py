"""
T05 — 표준 진단 실행 → 진단 제외 → 재진단 → 빠진 컬럼 검증 (★)

검증:
  · 1차 진단: TB_DIAG_RESULT row 수 N1, INV_APP.COMPANY_INFO 컬럼 결과 발생
  · INV_APP.COMPANY_INFO 표준 진단 OFF (DB 직접 — UI 검증은 T04)
  · 2차 진단 (같은 모델)
  · 결과 비교:
     - 2차 결과의 INV_APP.COMPANY_INFO 컬럼 result 0건 (제외됨)
     - HRM_APP.COMPANY_INFO / SALES_APP.COMPANY_INFO 결과는 유지
     - total result count 가 1차보다 줄어들거나 같음

Selenium UI 진단 시작 대신 /api/diag/startDiag API 직접 호출 — UI 흐름은 다른 테스트에서 cover.
이 테스트는 OBJ_OWNER 격리가 진단 실행단까지 일관되게 적용되는지 검증.
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, db_query, BASE_URL, TestRun,
                    get_admin_session)


def wait_diag_done(dm_id, exclude_job_ids, timeout=120):
    """새 Job 이 DONE 될 때까지 대기. 새 job_id 반환."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        rows = db_query(f"""
            SELECT DIAG_JOB_ID, STATUS FROM TB_DIAG_JOB
            WHERE DM_ID='{dm_id}' ORDER BY CRET_DT DESC LIMIT 1
        """)
        if rows:
            jid, st = rows[0]
            if jid not in exclude_job_ids and st in ("DONE", "ERROR", "STOPPED"):
                return jid, st
        time.sleep(2)
    return None, "TIMEOUT"


def run():
    t = TestRun("T05 표준 진단 — 제외 전후 결과 비교 (★)")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # 사전조건: 같은 OBJ_NM 다른 OWNER 가 있는 모델 우선 — 4g2CealZkDK8jA9XGpWH6c (수동DB등록모델) 같은
        rows = db_query("""
            SELECT O.DM_ID, COUNT(DISTINCT O.OBJ_OWNER) AS owners
            FROM TB_DATA_MODEL_OBJ O
            INNER JOIN TB_DATA_MODEL DM ON O.DM_ID=DM.DM_ID
            WHERE O.OBJ_NM='COMPANY_INFO' AND O.USE_YN='Y'
              AND DM.USE_YN='Y'
              AND O.OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
            GROUP BY O.DM_ID
            ORDER BY owners DESC LIMIT 1
        """)
        if not rows:
            t.step("사전조건 — COMPANY_INFO 다중 owner 모델", False)
            return t
        dm_id = rows[0][0]
        owner_cnt = int(rows[0][1])
        t.step("최근 모델 (COMPANY_INFO 다중 owner)", True,
               f"DM_ID={dm_id}, owner_cnt={owner_cnt}")
        if owner_cnt < 2:
            t.step("OWNER 분리 사전조건", False, "COMPANY_INFO 가 1개 owner 만 존재")
            return t

        # reset 모든 owner 'Y'
        db_query(f"""
            UPDATE TB_DATA_MODEL_OBJ
            SET STND_DIAG_TARGET_YN='Y', STND_DIAG_TARGET_REASON=NULL
            WHERE DM_ID='{dm_id}' AND OBJ_NM='COMPANY_INFO'
        """)
        t.step("BEFORE — COMPANY_INFO 모든 owner STND='Y' reset", True)

        # 기존 RUNNING / READY 좀비 정리
        db_query(f"""
            UPDATE TB_DIAG_JOB SET STATUS='STOPPED'
            WHERE DM_ID='{dm_id}' AND STATUS IN ('READY','RUNNING')
        """)

        sess = get_admin_session(drv)

        # === 1차 진단 시작 ===
        existing = {r[0] for r in db_query(
            f"SELECT DIAG_JOB_ID FROM TB_DIAG_JOB WHERE DM_ID='{dm_id}'")}
        r = sess.post(f"{BASE_URL}/api/diag/startDiag",
                      json={"dataModelId": dm_id})
        rc1 = r.status_code
        try:
            body = r.json()
        except Exception:
            body = {}
        api_ok1 = rc1 == 200 and body.get("resultCode", 200) == 200
        t.step("1차 startDiag API", api_ok1,
               f"status={rc1} rc={body.get('resultCode')} msg={body.get('resultMessage','')}")
        if not api_ok1:
            return t

        before_job_id, before_status = wait_diag_done(dm_id, existing, timeout=120)
        t.step("1차 진단 완료", before_status == "DONE",
               f"job_id={before_job_id} status={before_status}")
        if before_status != "DONE":
            return t

        # NOTE: TB_DIAG_RESULT 에 OBJ_OWNER 컬럼이 없음 → ATTR_NM 으로 구분.
        # INV_APP.COMPANY_INFO 의 unique attr 조회 (HRM/SALES 와 안 겹치는 것)
        inv_attrs_rows = db_query(f"""
            SELECT ATTR_NM FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='INV_APP' AND OBJ_NM='COMPANY_INFO' AND USE_YN='Y'
              AND ATTR_NM NOT IN (
                SELECT ATTR_NM FROM TB_DATA_MODEL_ATTR
                WHERE DM_ID='{dm_id}' AND OBJ_OWNER<>'INV_APP' AND OBJ_NM='COMPANY_INFO' AND USE_YN='Y'
              )
        """)
        inv_unique_attrs = [r[0] for r in inv_attrs_rows]
        if not inv_unique_attrs:
            t.step("INV_APP unique attrs 존재", False, "다른 owner 와 겹치지 않는 attr 없음")
            return t
        attr_in = "','".join(inv_unique_attrs)
        t.step("INV_APP unique attrs", True, f"{inv_unique_attrs}")

        before_total = int(db_query(
            f"SELECT COUNT(*) FROM TB_DIAG_RESULT WHERE DIAG_JOB_ID='{before_job_id}'"
        )[0][0])
        before_inv = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DIAG_RESULT
            WHERE DIAG_JOB_ID='{before_job_id}'
              AND OBJ_NM='COMPANY_INFO' AND ATTR_NM IN ('{attr_in}')
        """)[0][0])
        before_other = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DIAG_RESULT
            WHERE DIAG_JOB_ID='{before_job_id}'
              AND OBJ_NM='COMPANY_INFO' AND ATTR_NM NOT IN ('{attr_in}')
        """)[0][0])
        t.step(f"1차 결과 — total={before_total}, INV_unique={before_inv}건, 그 외 COMPANY_INFO={before_other}건",
               True)

        # === INV_APP.COMPANY_INFO 만 OFF ===
        db_query(f"""
            UPDATE TB_DATA_MODEL_OBJ
            SET STND_DIAG_TARGET_YN='N', STND_DIAG_TARGET_REASON='T05 자동제외'
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='INV_APP' AND OBJ_NM='COMPANY_INFO'
        """)
        t.step("INV_APP.COMPANY_INFO 표준 진단 OFF", True)

        # === 2차 진단 ===
        existing2 = {r[0] for r in db_query(
            f"SELECT DIAG_JOB_ID FROM TB_DIAG_JOB WHERE DM_ID='{dm_id}'")}
        r = sess.post(f"{BASE_URL}/api/diag/startDiag",
                      json={"dataModelId": dm_id})
        try:
            body = r.json()
        except Exception:
            body = {}
        rc2 = r.status_code
        api_ok2 = rc2 == 200 and body.get("resultCode", 200) == 200
        t.step("2차 startDiag API", api_ok2,
               f"status={rc2} rc={body.get('resultCode')} msg={body.get('resultMessage','')}")
        if not api_ok2:
            return t

        after_job_id, after_status = wait_diag_done(dm_id, existing2, timeout=120)
        t.step("2차 진단 완료", after_status == "DONE",
               f"job_id={after_job_id} status={after_status}")
        if after_status != "DONE":
            return t

        after_total = int(db_query(
            f"SELECT COUNT(*) FROM TB_DIAG_RESULT WHERE DIAG_JOB_ID='{after_job_id}'"
        )[0][0])
        after_inv = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DIAG_RESULT
            WHERE DIAG_JOB_ID='{after_job_id}'
              AND OBJ_NM='COMPANY_INFO' AND ATTR_NM IN ('{attr_in}')
        """)[0][0])
        after_other = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DIAG_RESULT
            WHERE DIAG_JOB_ID='{after_job_id}'
              AND OBJ_NM='COMPANY_INFO' AND ATTR_NM NOT IN ('{attr_in}')
        """)[0][0])

        # ★ 검증
        t.step(f"2차 — INV_unique attrs 결과 0건 (제외 적용)",
               after_inv == 0, f"actual={after_inv} (before={before_inv})")
        t.step(f"2차 — 다른 OWNER 의 COMPANY_INFO 결과는 유지 (★ OWNER 분리)",
               after_other == before_other,
               f"before={before_other} after={after_other}")
        t.step(f"2차 total ≤ 1차 total ({before_total} → {after_total})",
               after_total <= before_total)

    except Exception as e:
        t.step("예외", False, str(e))
    finally:
        try:
            db_query(f"""
                UPDATE TB_DATA_MODEL_OBJ
                SET STND_DIAG_TARGET_YN='Y', STND_DIAG_TARGET_REASON=NULL
                WHERE DM_ID='{dm_id}' AND OBJ_NM='COMPANY_INFO'
            """)
        except Exception:
            pass
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
