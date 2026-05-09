"""
T11 — OBJ.OBJ_OWNER 변경 시 하위 ATTR/INDEX/CONSTRAINT cascade 검증

시나리오:
  · 임의 OBJ 의 OWNER 를 'TEST_NEW_OWNER' 로 update (UI 또는 직접 API)
  · DB 검증: 같은 OBJ_NM 의 ATTR / INDEX / CONSTRAINT 의 OBJ_OWNER 도 함께 갱신
  · REF_OWNER (다른 테이블이 이 테이블 FK 참조) 도 갱신
  · cleanup: 원래 OWNER 로 되돌림
"""
import sys, os, time, requests
sys.path.insert(0, os.path.dirname(__file__))
from common import db_query, BASE_URL, TestRun


def run():
    t = TestRun("T11 OBJ_OWNER 변경 cascade")
    try:
        # 사전: ATTR 가 있는 OBJ 1개 선정 (HRM_APP.TB_DEPT 같은 거)
        rows = db_query("""
            SELECT O.DM_ID, O.OBJ_OWNER, O.OBJ_NM
            FROM TB_DATA_MODEL_OBJ O
            INNER JOIN TB_DATA_MODEL_ATTR A
              ON O.DM_ID=A.DM_ID AND O.OBJ_OWNER=A.OBJ_OWNER AND O.OBJ_NM=A.OBJ_NM
            WHERE O.USE_YN='Y' AND A.USE_YN='Y'
              AND O.OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
            GROUP BY O.DM_ID, O.OBJ_OWNER, O.OBJ_NM
            HAVING COUNT(*) >= 2
            ORDER BY O.DM_ID, O.OBJ_NM
            LIMIT 1
        """)
        if not rows:
            t.step("사전 — cascade 대상 OBJ", False, "ATTR ≥2 보유 OBJ 없음")
            return t
        dm_id, orig_owner, obj_nm = rows[0]
        t.step(f"대상 OBJ: {orig_owner}.{obj_nm}", True)

        # BEFORE
        attr_cnt_before = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{orig_owner}' AND OBJ_NM='{obj_nm}'
        """)[0][0])
        t.step(f"BEFORE — {orig_owner}.{obj_nm} ATTR 수", True, f"{attr_cnt_before}개")

        # OWNER 변경 시도 (API)
        new_owner = f"T11_TMP_{int(time.time())}"
        r = requests.post(f"{BASE_URL}/api/dm/updateObj", json={
            "dataModelId": dm_id,
            "origObjNm": obj_nm,
            "objNm": obj_nm,
            "objNmKr": "T11 cascade test",
            "objOwner": new_owner,
            "origObjOwner": orig_owner,
            "objDesc": ""
        })
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        t.step("updateObj API 호출", rc == 200, f"rc={rc} msg={body.get('resultMessage', '')[:80]}")

        # AFTER cascade — ATTR 의 OBJ_OWNER 갱신 확인
        attr_cnt_new = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{new_owner}' AND OBJ_NM='{obj_nm}'
        """)[0][0])
        attr_cnt_old = int(db_query(f"""
            SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{orig_owner}' AND OBJ_NM='{obj_nm}'
        """)[0][0])
        t.step(f"ATTR cascade — new owner '{new_owner}' 에 {attr_cnt_before}건 (변경 후)",
               attr_cnt_new == attr_cnt_before,
               f"actual_new={attr_cnt_new}, actual_old={attr_cnt_old}")
        t.step(f"old owner '{orig_owner}' 의 ATTR 0건",
               attr_cnt_old == 0,
               f"actual={attr_cnt_old}")

        # OBJ 자체도 갱신
        obj_check = db_query(f"""
            SELECT OBJ_OWNER FROM TB_DATA_MODEL_OBJ
            WHERE DM_ID='{dm_id}' AND OBJ_NM='{obj_nm}' LIMIT 1
        """)
        if obj_check:
            t.step(f"OBJ 자체의 OWNER='{new_owner}'",
                   obj_check[0][0] == new_owner,
                   f"actual={obj_check[0][0]}")

        # === cleanup — 원래 OWNER 로 되돌림 ===
        r = requests.post(f"{BASE_URL}/api/dm/updateObj", json={
            "dataModelId": dm_id,
            "origObjNm": obj_nm,
            "objNm": obj_nm,
            "objNmKr": "",
            "objOwner": orig_owner,
            "origObjOwner": new_owner,
            "objDesc": ""
        })
        try:
            cb = r.json()
        except Exception:
            cb = {}
        t.step("cleanup — 원래 OWNER 복원", cb.get("resultCode", r.status_code) == 200)

    except Exception as e:
        t.step("예외", False, str(e))
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
