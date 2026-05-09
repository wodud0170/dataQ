"""
T04 — 진단 제외 관리 — 같은 OBJ_NM 다른 OWNER 분리 토글 검증 (★ 핵심)

이번 86번 #11 fix 의 가장 중요한 검증.
이전 매퍼는 WHERE 절에 OBJ_OWNER 가 빠져서 같은 OBJ_NM 의 모든 OWNER row 가 한 번에 변경됨.
fix 후 매퍼: WHERE DM_ID=#{dmId} AND OBJ_OWNER=COALESCE(NULLIF(#{objOwner},''),'') AND OBJ_NM=#{objNm}

검증 시나리오:
  · 1) DB 직접: 3 OWNER 모두 STND='Y' reset
  · 2) 로그인 후 admin 세션 쿠키 획득 → API /api/dm/setObjDiagTarget 직접 호출
        body: {dmId, objOwner='INV_APP', objNm='COMPANY_INFO', diagType='STND', targetYn='N', reason}
  · 3) DB 검증:
        - INV_APP.COMPANY_INFO STND='N'  (의도한 변경)
        - HRM_APP.COMPANY_INFO STND='Y'  (★ 안 변경)
        - SALES_APP.COMPANY_INFO STND='Y' (★ 안 변경)
  · 4) cleanup — 3 owner 모두 'Y' 로 reset

Selenium UI 토글 대신 API 직접 호출 — UI 흐름은 T03 에서 cover.
이 테스트는 mapper WHERE 의 OBJ_OWNER 격리만 검증.
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, db_query, BASE_URL, TestRun,
                    get_admin_session)


def get_target_yn(diag_type, dm_id, owner, obj_nm):
    col = {"STND": "STND_DIAG_TARGET_YN",
           "STRUCT": "STRUCT_DIAG_TARGET_YN",
           "QUAL": "QUAL_DIAG_TARGET_YN"}[diag_type]
    sql = (f"SELECT COALESCE({col},'Y') FROM TB_DATA_MODEL_OBJ "
           f"WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{owner}' AND OBJ_NM='{obj_nm}' LIMIT 1")
    rows = db_query(sql)
    return rows[0][0] if rows else None


def reset_all(dm_id, owners, obj_nm):
    for ow in owners:
        db_query(f"""
            UPDATE TB_DATA_MODEL_OBJ
            SET STND_DIAG_TARGET_YN='Y', STND_DIAG_TARGET_REASON=NULL,
                STRUCT_DIAG_TARGET_YN='Y', STRUCT_DIAG_TARGET_REASON=NULL
            WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{ow}' AND OBJ_NM='{obj_nm}'
        """)


def run():
    t = TestRun("T04 진단 제외 — OBJ_OWNER 분리 (★)")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # 사전조건: 3 OWNER 의 COMPANY_INFO 가 같은 모델에 존재
        rows = db_query("""
            SELECT DM_ID, OBJ_OWNER FROM TB_DATA_MODEL_OBJ
            WHERE OBJ_NM='COMPANY_INFO'
              AND OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
              AND USE_YN='Y'
            ORDER BY DM_ID, OBJ_OWNER
        """)
        if not rows:
            t.step("사전조건", False, "COMPANY_INFO 가 없음")
            return t
        # 같은 모델에 가장 많이 있는 dm_id 선택
        from collections import Counter
        dm_counter = Counter(r[0] for r in rows)
        dm_id = dm_counter.most_common(1)[0][0]
        owners = sorted({r[1] for r in rows if r[0] == dm_id})
        t.step("사전조건 — 3 OWNER 의 COMPANY_INFO 존재 (같은 모델)",
               len(owners) >= 2, f"dm={dm_id} owners={owners}")
        if len(owners) < 2:
            return t

        # 1) BEFORE — 모두 'Y' reset
        reset_all(dm_id, owners, "COMPANY_INFO")
        for ow in owners:
            yn = get_target_yn("STND", dm_id, ow, "COMPANY_INFO")
            if yn != "Y":
                t.step(f"BEFORE — {ow}.COMPANY_INFO STND='Y' (reset 확인)", False, f"actual={yn}")
                return t
        t.step("BEFORE — 3 OWNER 모두 STND='Y' reset 완료", True)

        # 2) admin 세션 → API 직접 호출 — INV_APP 만 OFF
        sess = get_admin_session(drv)
        target_owner = "INV_APP" if "INV_APP" in owners else owners[0]
        other_owners = [o for o in owners if o != target_owner]

        r = sess.post(f"{BASE_URL}/api/dm/setObjDiagTarget", json={
            "dmId":     dm_id,
            "objOwner": target_owner,
            "objNm":    "COMPANY_INFO",
            "diagType": "STND",
            "targetYn": "N",
            "reason":   "T04 자동테스트 — INV_APP 만 OFF"
        })
        api_ok = r.status_code == 200
        try:
            body = r.json()
        except Exception:
            body = {}
        t.step(f"API setObjDiagTarget (objOwner={target_owner})",
               api_ok and body.get("success", False),
               f"status={r.status_code} count={body.get('count')}")

        # 3) DB 검증 (★ 핵심)
        target_yn = get_target_yn("STND", dm_id, target_owner, "COMPANY_INFO")
        t.step(f"AFTER — {target_owner}.COMPANY_INFO STND='N' (의도한 변경)",
               target_yn == "N", f"actual={target_yn}")

        for ow in other_owners:
            yn = get_target_yn("STND", dm_id, ow, "COMPANY_INFO")
            t.step(f"AFTER — {ow}.COMPANY_INFO STND='Y' (★ OWNER 분리: 안 변경)",
                   yn == "Y", f"actual={yn}")

        # 4) batch API 도 같이 검증 (다른 매퍼)
        # batch — INV_APP 만 다시 'Y' 로 (targets array 사용)
        r = sess.post(f"{BASE_URL}/api/dm/setObjDiagTargetBatch", json={
            "dmId":     dm_id,
            "targets":  [{"objOwner": target_owner, "objNm": "COMPANY_INFO"}],
            "diagType": "STND",
            "targetYn": "Y",
            "reason":   None
        })
        try:
            body = r.json()
        except Exception:
            body = {}
        t.step("API setObjDiagTargetBatch (targets tuple)",
               r.status_code == 200 and body.get("success", False),
               f"status={r.status_code} count={body.get('count')}")
        # 다시 모두 Y 인지 확인
        all_y_after_batch = all(get_target_yn("STND", dm_id, ow, "COMPANY_INFO") == "Y"
                                for ow in owners)
        t.step("Batch 후 — 3 OWNER 모두 'Y' 로 복원",
               all_y_after_batch,
               f"per-owner: {[(o, get_target_yn('STND', dm_id, o, 'COMPANY_INFO')) for o in owners]}")

        # 5) STRUCT 진단 타입도 같이 검증 (별도 매퍼 segment)
        r = sess.post(f"{BASE_URL}/api/dm/setObjDiagTarget", json={
            "dmId":     dm_id,
            "objOwner": target_owner,
            "objNm":    "COMPANY_INFO",
            "diagType": "STRUCT",
            "targetYn": "N",
            "reason":   "T04 — STRUCT 격리 검증"
        })
        try:
            body = r.json()
        except Exception:
            body = {}
        t.step(f"STRUCT — {target_owner}.COMPANY_INFO 만 OFF API",
               r.status_code == 200 and body.get("success", False),
               f"count={body.get('count')}")
        for ow in owners:
            yn = get_target_yn("STRUCT", dm_id, ow, "COMPANY_INFO")
            expected = "N" if ow == target_owner else "Y"
            t.step(f"STRUCT — {ow}.COMPANY_INFO == '{expected}'",
                   yn == expected, f"actual={yn}")

    except Exception as e:
        t.step("예외", False, str(e))
    finally:
        # cleanup — 모두 Y
        try:
            reset_all(dm_id, owners, "COMPANY_INFO")
        except Exception:
            pass
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
