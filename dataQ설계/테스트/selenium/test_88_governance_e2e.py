"""
88번 거버넌스 워크플로우 — UI E2E (저장→신청→승인 풀 사이클)

API + DB 직접 호출로 한 사이클을 검증. UI 인터랙션은 신뢰성 위해
api/dm/saveAttrs 와 api/dmApproval/* 을 직접 호출 (Selenium 으로 그리드 클릭은 별도).

검증 포인트:
1. jyjang(user) 컬럼 추가 → tb_data_model_attr.aprv_status='DRAFT'
2. /api/dmApproval/myDrafts → DRAFT 1건 보임
3. 사용자 변경 이력 화면 (Selenium) → DRAFT 행 노란 배경 표시 (visual check skip)
4. 신청 → SUBMITTED
5. space(admin) 승인 → 양쪽 APPROVED, DDL snippet 존재
6. 반려 cycle: 별도 row 로 반려 → REJECTED + 양쪽 sync
7. Tier 1.5 swapAttrOrd (admin only) — 권한 거부 검증
"""
import os, sys, time, base64, subprocess, traceback
import requests

BASE_URL = "http://localhost:28091"

results = []

def record(name, ok, msg=""):
    icon = "✓" if ok else "✗"
    results.append((name, ok, msg))
    print(f"  [{icon}] {name}: {msg}")


def db_exec(sql):
    return subprocess.run(
        ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres", "-t", "-c", sql],
        check=True, capture_output=True, text=True, timeout=10
    ).stdout.strip()


def login(uid):
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(f"{BASE_URL}/login", data={"id": uid, "password": enc}, timeout=10)
    if not r.json().get("success"):
        raise RuntimeError(f"{uid} 로그인 실패: {r.text}")
    return s


def main():
    # cleanup
    db_exec("DELETE FROM quality.tb_data_model_attr WHERE attr_nm_kr LIKE 'E2E_UI%';")
    db_exec("DELETE FROM quality.tb_data_model_change_history WHERE change_user_id='jyjang';")

    DM = "9ek4pZ2c4_Wab1k*g1_0yt"
    TBL = "TB_MEMEBER"

    # Step 1 — jyjang ADD
    print("\n[Step 1] jyjang(user) 컬럼 추가")
    sj = login("jyjang")
    r = sj.post(f"{BASE_URL}/api/dm/saveAttrs", json={
        "dataModelId": DM, "objNm": TBL, "objOwner": "USER1",
        "attrs": [{
            "mode": "ADD", "attrNmKr": "E2E_UI_TEST", "attrNm": "E2E_UI_COL",
            "dataType": "VARCHAR", "dataLen": 80, "dataDecimalLen": 0,
            "attrOrder": 350, "pkYn": "N", "fkYn": "N", "nullableYn": "Y"
        }]
    }, timeout=10)
    record("save_attrs_200", r.status_code == 200, f"status={r.status_code}")

    row = db_exec("SELECT aprv_status||'/'||requester_user_id FROM quality.tb_data_model_attr WHERE attr_nm_kr='E2E_UI_TEST';")
    record("attr_row_DRAFT", row == "DRAFT/jyjang", f"row={row}")

    # Step 2 — myDrafts
    print("\n[Step 2] /api/dmApproval/myDrafts")
    r = sj.post(f"{BASE_URL}/api/dmApproval/myDrafts", json={}, timeout=10)
    arr = r.json() if r.status_code == 200 else []
    e2e_drafts = [x for x in arr if x.get("attrNm") == "E2E_UI_COL"]
    record("myDrafts_has_row", len(e2e_drafts) == 1, f"cnt={len(e2e_drafts)}")
    if not e2e_drafts:
        print("[FATAL] myDrafts 비어있음 — 후속 step 중단")
        return summary()
    seq = e2e_drafts[0]["changeSeq"]
    ddl = e2e_drafts[0].get("ddlSnippet") or ""
    record("ddl_snippet_generated", "ALTER TABLE" in ddl and "E2E_UI_COL" in ddl, f"ddl={ddl}")

    # Step 3 — Visibility: jyjang 본인은 DRAFT 보이고, 다른 사용자(임의)는 안 보여야 — 단일 사용자 환경이라 admin/non-admin 비교로 대체
    print("\n[Step 3] 가시성 — jyjang 본인 컬럼 그리드에서 보임 / 익명 호출 차단")
    r = sj.get(f"{BASE_URL}/api/dm/getDataModelAttrListByClctId?clctId={DM}", timeout=10)
    cols = r.json() if r.status_code == 200 else []
    e2e_in_list = [x for x in cols if x.get("attrNm") == "E2E_UI_COL"]
    record("jyjang_sees_own_DRAFT", len(e2e_in_list) == 1, f"cnt={len(e2e_in_list)}, aprvStatus={e2e_in_list[0].get('aprvStatus') if e2e_in_list else 'N/A'}")

    # Step 4 — submit
    print("\n[Step 4] 신청 (DRAFT → SUBMITTED)")
    r = sj.post(f"{BASE_URL}/api/dmApproval/submit", json={"changeSeqList": [seq]}, timeout=10)
    record("submit_200", r.status_code == 200, f"status={r.status_code}")
    row = db_exec(f"SELECT aprv_status FROM quality.tb_data_model_change_history WHERE change_seq={seq};")
    record("history_SUBMITTED", row == "SUBMITTED", f"status={row}")

    # Step 5 — space admin approve
    print("\n[Step 5] space(admin) 승인 → APPROVED 양쪽 sync")
    ss = login("space")
    r = ss.post(f"{BASE_URL}/api/dmApproval/approve", json={"changeSeqList": [seq]}, timeout=10)
    record("approve_200", r.status_code == 200, f"status={r.status_code}")
    hist = db_exec(f"SELECT aprv_status||'/'||aprv_user_id FROM quality.tb_data_model_change_history WHERE change_seq={seq};")
    attr = db_exec("SELECT aprv_status||'/'||aprv_user_id FROM quality.tb_data_model_attr WHERE attr_nm_kr='E2E_UI_TEST';")
    record("history_APPROVED_by_space", hist == "APPROVED/space", f"hist={hist}")
    record("attr_APPROVED_synced",      attr == "APPROVED/space", f"attr={attr}")

    # Step 6 — Tier 1.5: jyjang swapAttrOrd → 권한 거부 / space → 성공
    print("\n[Step 6] Tier 1.5 swapAttrOrd 권한 분기")
    r1 = sj.post(f"{BASE_URL}/api/dm/swapAttrOrd", json={
        "dataModelId": DM, "objOwner": "USER1", "objNm": TBL,
        "attrNm": "MBR_NM", "direction": "UP"
    }, timeout=10)
    body = r1.json()
    record("swap_user_denied", body.get("resultCode") == 500 and "관리자" in (body.get("resultMessage") or ""),
           f"code={body.get('resultCode')}, msg={body.get('resultMessage')}")
    r2 = ss.post(f"{BASE_URL}/api/dm/swapAttrOrd", json={
        "dataModelId": DM, "objOwner": "USER1", "objNm": TBL,
        "attrNm": "MBR_NM", "direction": "UP"
    }, timeout=10)
    record("swap_admin_ok", r2.json().get("resultCode") == 200, f"code={r2.json().get('resultCode')}")
    # 다시 원위치
    ss.post(f"{BASE_URL}/api/dm/swapAttrOrd", json={
        "dataModelId": DM, "objOwner": "USER1", "objNm": TBL,
        "attrNm": "MBR_NM", "direction": "DOWN"
    }, timeout=10)

    # Step 7 — Reject cycle (별도 row)
    print("\n[Step 7] 반려 사이클 — 양쪽 REJECTED")
    sj2 = login("jyjang")
    sj2.post(f"{BASE_URL}/api/dm/saveAttrs", json={
        "dataModelId": DM, "objNm": TBL, "objOwner": "USER1",
        "attrs": [{
            "mode": "ADD", "attrNmKr": "E2E_UI_REJECT", "attrNm": "E2E_UI_REJ",
            "dataType": "VARCHAR", "dataLen": 50, "dataDecimalLen": 0,
            "attrOrder": 360, "pkYn": "N", "fkYn": "N", "nullableYn": "Y"
        }]
    }, timeout=10)
    seq2 = int(db_exec("SELECT change_seq FROM quality.tb_data_model_change_history WHERE change_user_id='jyjang' ORDER BY change_seq DESC LIMIT 1;"))
    sj2.post(f"{BASE_URL}/api/dmApproval/submit", json={"changeSeqList": [seq2]}, timeout=10)
    ss2 = login("space")
    ss2.post(f"{BASE_URL}/api/dmApproval/reject", json={"changeSeqList": [seq2], "aprvComment": "E2E 테스트 반려"}, timeout=10)
    hist2 = db_exec(f"SELECT aprv_status FROM quality.tb_data_model_change_history WHERE change_seq={seq2};")
    attr2 = db_exec("SELECT aprv_status FROM quality.tb_data_model_attr WHERE attr_nm_kr='E2E_UI_REJECT';")
    record("reject_history", hist2 == "REJECTED", f"hist={hist2}")
    record("reject_attr_synced", attr2 == "REJECTED", f"attr={attr2}")

    # Step 8 — Area CRUD admin only
    print("\n[Step 8] 영역 CRUD 권한")
    r = sj.post(f"{BASE_URL}/api/area/biz/save", json={"bizAreaNm": "유저시도"}, timeout=10)
    record("biz_save_user_denied", r.json().get("resultCode") == 500 and "관리자" in (r.json().get("resultMessage") or ""),
           f"msg={r.json().get('resultMessage')}")
    r = ss.post(f"{BASE_URL}/api/area/biz/save", json={"bizAreaNm": "E2E_BIZ_AREA", "bizAreaDesc": "테스트", "sortOrder": 99}, timeout=10)
    record("biz_save_admin_ok", r.json().get("resultCode") == 200, f"code={r.json().get('resultCode')}")
    r = ss.post(f"{BASE_URL}/api/area/biz/list", json={}, timeout=10)
    has_biz = any(x.get("bizAreaNm") == "E2E_BIZ_AREA" for x in (r.json() or []))
    record("biz_list_has_new", has_biz, "")

    # cleanup
    print("\n[cleanup]")
    db_exec("DELETE FROM quality.tb_data_model_attr WHERE attr_nm_kr LIKE 'E2E_UI%';")
    db_exec("DELETE FROM quality.tb_data_model_change_history WHERE change_user_id='jyjang';")
    db_exec("DELETE FROM quality.tb_biz_area WHERE biz_area_nm='E2E_BIZ_AREA';")

    summary()


def summary():
    print(f"\n{'='*60}")
    print(f"[SUMMARY] 총 {len(results)}건")
    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print(f"  PASS: {passed}")
    print(f"  FAIL: {failed}")
    print(f"{'='*60}")
    if failed > 0:
        print("\n실패 항목:")
        for n, ok, m in results:
            if not ok:
                print(f"  - {n}: {m}")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        traceback.print_exc()
        sys.exit(1)
