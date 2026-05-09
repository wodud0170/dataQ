"""
T07 — 컬럼 추가 시 부모 OBJ_OWNER 정확 매핑 검증

86번 #11 fix:
  · saveAttrs 가 objOwner 명시받음 (frontend 가 OWNER.OBJ_NM 옵션 클릭 시 부모 owner 전달)
  · objOwner 미전달 시 부모 OBJ 조회로 lookup
  · ATTR row 에 정확한 OBJ_OWNER 저장 → composite PK (DM_ID, OBJ_OWNER, OBJ_NM, ATTR_NM)

검증 시나리오:
  · 같은 모델에 HRM_APP.TB_USER / INV_APP.TB_USER / SALES_APP.TB_USER 3 개
  · BEFORE — 각 owner 의 ATTR 카운트 snapshot
  · API /api/dm/saveAttrs body: {dataModelId, objOwner='INV_APP', objNm='TB_USER', attrs:[{mode:ADD, attrNm:..., attrNmKr:...}]}
  · DB 검증: 신규 ATTR 의 OBJ_OWNER='INV_APP' 만 들어감, 다른 owner 영향 X
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, db_query, BASE_URL, TestRun,
                    get_admin_session)


def run():
    t = TestRun("T07 컬럼 추가 — 부모 OBJ_OWNER 자동 상속")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # 사전: TB_USER 다중 OWNER 모델
        rows = db_query("""
            SELECT DM_ID, OBJ_OWNER FROM TB_DATA_MODEL_OBJ
            WHERE OBJ_NM='TB_USER' AND USE_YN='Y'
              AND OBJ_OWNER IN ('HRM_APP','INV_APP','SALES_APP')
            ORDER BY DM_ID, OBJ_OWNER
        """)
        if not rows or len(set(r[1] for r in rows)) < 2:
            t.step("사전조건 — TB_USER 가 ≥2 OWNER", False, f"rows={rows}")
            return t

        from collections import Counter
        dm_counter = Counter(r[0] for r in rows)
        dm_id = dm_counter.most_common(1)[0][0]
        owners_in_model = sorted({r[1] for r in rows if r[0] == dm_id})
        target_owner = "INV_APP" if "INV_APP" in owners_in_model else owners_in_model[0]
        t.step("사전조건 OK", True,
               f"dm={dm_id}, owners={owners_in_model}, target={target_owner}")

        # BEFORE — 각 owner ATTR 카운트
        before = {}
        for ow in owners_in_model:
            cnt = db_query(f"""
                SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
                WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{ow}' AND OBJ_NM='TB_USER' AND USE_YN='Y'
            """)
            before[ow] = int(cnt[0][0]) if cnt else 0
        t.step("BEFORE — owner 별 TB_USER ATTR 카운트", True, str(before))

        # === 케이스 A: objOwner 명시 ===
        sess = get_admin_session(drv)
        unique_kr_a = f"테스트_명시_{int(time.time())}"
        unique_en_a = f"T07_EXPLICIT_{int(time.time())}"
        r = sess.post(f"{BASE_URL}/api/dm/saveAttrs", json={
            "dataModelId": dm_id,
            "objOwner":    target_owner,
            "objNm":       "TB_USER",
            "attrs": [{
                "mode":      "ADD",
                "attrNm":    unique_en_a,
                "attrNmKr":  unique_kr_a,
                "dataType":  "VARCHAR",
                "dataLen":   100,
                "nullableYn": "Y"
            }]
        })
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        t.step(f"A) saveAttrs (objOwner='{target_owner}' 명시)",
               rc == 200, f"status={r.status_code} rc={rc} msg={body.get('resultMessage','')}")

        # AFTER 검증 — DB
        for ow in owners_in_model:
            cnt_now = int(db_query(f"""
                SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
                WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{ow}' AND OBJ_NM='TB_USER' AND USE_YN='Y'
            """)[0][0])
            expected = before[ow] + (1 if ow == target_owner else 0)
            t.step(f"A) {ow}.TB_USER ATTR 카운트", cnt_now == expected,
                   f"before={before[ow]} after={cnt_now} expected={expected}")

        # 신규 ATTR 의 OBJ_OWNER 정확 매핑
        new_a = db_query(f"""
            SELECT OBJ_OWNER FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_NM='TB_USER' AND ATTR_NM='{unique_en_a}'
            LIMIT 1
        """)
        if new_a:
            t.step(f"A) 신규 ATTR.OBJ_OWNER='{target_owner}'",
                   new_a[0][0] == target_owner, f"actual={new_a[0][0]}")
        else:
            t.step("A) 신규 ATTR row 존재", False, "DB 에서 못 찾음")

        # cleanup A
        db_query(f"""
            DELETE FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_NM='TB_USER' AND ATTR_NM='{unique_en_a}'
        """)

        # === 케이스 B: objOwner 빠짐 — 부모 OBJ lookup 이 일관되게 동작 ===
        # 미전달 시 lookup. 첫 매칭 owner 가 들어감 (코드 1273-1282).
        # 검증 포인트: 신규 ATTR 의 OBJ_OWNER 가 owners_in_model 중 정확히 1개임 (NULL 이거나 빈 문자열 아님)
        unique_kr_b = f"테스트_lookup_{int(time.time())}"
        unique_en_b = f"T07_LOOKUP_{int(time.time())}"
        r = sess.post(f"{BASE_URL}/api/dm/saveAttrs", json={
            "dataModelId": dm_id,
            "objNm":       "TB_USER",
            "attrs": [{
                "mode":      "ADD",
                "attrNm":    unique_en_b,
                "attrNmKr":  unique_kr_b,
                "dataType":  "VARCHAR",
                "dataLen":   100,
                "nullableYn": "Y"
            }]
        })
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        t.step("B) saveAttrs (objOwner 미전달 — 부모 lookup)",
               rc == 200, f"rc={rc} msg={body.get('resultMessage','')}")

        new_b = db_query(f"""
            SELECT OBJ_OWNER FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_NM='TB_USER' AND ATTR_NM='{unique_en_b}'
        """)
        if new_b:
            owners_set = {r[0] for r in new_b}
            t.step("B) 신규 ATTR row 정확히 1건 (다른 owner 에 중복 X)",
                   len(new_b) == 1, f"row수={len(new_b)} owners={owners_set}")
            t.step("B) 신규 ATTR.OBJ_OWNER 가 owners_in_model 안에 있음",
                   new_b[0][0] in owners_in_model,
                   f"actual={new_b[0][0]}")
        else:
            t.step("B) 신규 ATTR row 존재", False, "DB 에서 못 찾음")

        # cleanup B
        db_query(f"""
            DELETE FROM TB_DATA_MODEL_ATTR
            WHERE DM_ID='{dm_id}' AND OBJ_NM='TB_USER' AND ATTR_NM='{unique_en_b}'
        """)

        # 다른 owner 영향 없음 (최종)
        for ow in owners_in_model:
            cnt_final = int(db_query(f"""
                SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
                WHERE DM_ID='{dm_id}' AND OBJ_OWNER='{ow}' AND OBJ_NM='TB_USER' AND USE_YN='Y'
            """)[0][0])
            t.step(f"FINAL — {ow}.TB_USER ATTR 카운트 복원",
                   cnt_final == before[ow],
                   f"before={before[ow]} final={cnt_final}")

    except Exception as e:
        t.step("예외", False, str(e))
    finally:
        # 안전 cleanup (시간 prefix 로 두 unique 컬럼만)
        try:
            db_query(f"""
                DELETE FROM TB_DATA_MODEL_ATTR
                WHERE OBJ_NM='TB_USER' AND ATTR_NM LIKE 'T07_%'
            """)
        except Exception:
            pass
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
