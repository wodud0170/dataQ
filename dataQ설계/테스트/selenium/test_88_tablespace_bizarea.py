"""
88번 §15 — 테이블스페이스 / 업무영역 통합테스트
  + 데이터 모델 변경 승인(selectSubmissions) alias 회귀

API + DB 직접 검증 방식 (test_88_governance_e2e.py 패턴).

검증 항목:
  Step 1. 업무영역 마스터 생성 (api/area/biz/save) → DB tb_biz_area
  Step 2. 테이블 addObj 시 테이블스페이스/업무영역 저장 → DB tb_data_model_obj
  Step 3. getDataModelObjListByClctId 응답에 tablespaceNm / bizAreaId / bizAreaNm
          (bizAreaNm 은 tb_biz_area JOIN 으로 노출 — 매퍼 서브쿼리 검증)
  Step 4. updateObj 시 테이블스페이스 변경 + 업무영역 해제 반영 → DB
          (updateDataModelObj 매퍼에 3개 컬럼 추가한 수정의 회귀)
  Step 5. 인덱스 목록 API 응답에 tablespaceNm 키 존재
  Step 6. dmApproval/submissions 응답 키가 camelCase(submissionId) 인지
          (selectSubmissions SQL alias 쌍따옴표 누락 → 소문자 폴딩 버그의 회귀)

선행조건: q-center(28091) 기동, docker dataq-db 기동.
실행: python test_88_tablespace_bizarea.py
"""
import sys
import json
import base64
import subprocess
import traceback

import requests

BASE_URL = "http://localhost:28091"
# 논리 모델 (test_88_governance_e2e.py 와 동일한 고정 모델 ID)
DM = "9ek4pZ2c4_Wab1k*g1_0yt"

results = []


def record(name, ok, msg=""):
    icon = "PASS" if ok else "FAIL"
    results.append((name, ok, msg))
    print(f"  [{icon}] {name}: {msg}")


def login(uid):
    """API 세션 로그인 (패스워드 base64)"""
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(f"{BASE_URL}/login", data={"id": uid, "password": enc}, timeout=10)
    if not r.json().get("success"):
        raise RuntimeError(f"{uid} 로그인 실패: {r.text}")
    return s


def db_exec(sql):
    """docker dataq-db 에서 psql 직접 실행 (quality 스키마)"""
    return subprocess.run(
        ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
         "-t", "-c", "SET search_path TO quality; " + sql],
        check=True, capture_output=True, text=True, timeout=15
    ).stdout.strip()


def cleanup():
    """테스트가 만든 데이터 정리 — 패턴 한정 삭제"""
    db_exec("DELETE FROM tb_data_model_change_history WHERE attr_nm = 'E2E_TS_COL';")
    db_exec("DELETE FROM tb_data_model_change_history WHERE obj_nm LIKE 'IMSI_TS%';")
    db_exec("DELETE FROM tb_data_model_attr WHERE attr_nm = 'E2E_TS_COL';")
    db_exec("DELETE FROM tb_data_model_obj WHERE obj_nm LIKE 'IMSI_TS%';")
    db_exec("DELETE FROM tb_biz_area WHERE biz_area_nm = 'E2E_BIZ_AREA';")


def main():
    # 0. 선제 cleanup (이전 실행 잔여물)
    print("[0] 선제 cleanup")
    cleanup()

    ss = login("space")
    biz_area_id = None

    # ---- Step 1. 업무영역 마스터 생성 ----
    print("\n[Step 1] 업무영역 마스터 생성 (api/area/biz/save)")
    r = ss.post(f"{BASE_URL}/api/area/biz/save", json={
        "bizAreaNm": "E2E_BIZ_AREA", "bizAreaDesc": "통합테스트용", "sortOrder": 0,
    }, timeout=10)
    record("biz_save_200", r.status_code == 200 and r.json().get("resultCode") == 200,
           f"status={r.status_code}")
    contents = r.json().get("contents") or "{}"
    biz_area_id = json.loads(contents).get("bizAreaId")
    record("biz_area_id_returned", bool(biz_area_id), f"id={biz_area_id}")
    db_nm = db_exec(f"SELECT biz_area_nm FROM tb_biz_area WHERE biz_area_id = '{biz_area_id}';")
    record("biz_area_in_db", db_nm == "E2E_BIZ_AREA", f"db='{db_nm}'")

    # ---- Step 2. addObj — 테이블스페이스 + 업무영역 저장 ----
    print("\n[Step 2] addObj — 테이블스페이스/업무영역 저장")
    r = ss.post(f"{BASE_URL}/api/dm/addObj", json={
        "dataModelId": DM, "objNm": "IMSI_TS_E2E", "objNmKr": "E2E_TS_테이블",
        "objOwner": "E2E", "objDesc": "통합테스트",
        "tablespaceNm": "E2E_TBS", "bizAreaId": biz_area_id,
    }, timeout=10)
    record("addObj_200", r.status_code == 200 and r.json().get("resultCode") == 200,
           f"status={r.status_code}")
    row = db_exec("SELECT COALESCE(tablespace_nm,'<null>')||'/'||COALESCE(biz_area_id,'<null>') "
                  f"FROM tb_data_model_obj WHERE dm_id='{DM}' AND obj_nm='IMSI_TS_E2E';")
    record("addObj_db_saved", row == f"E2E_TBS/{biz_area_id}", f"db='{row}'")

    # ---- Step 3. getDataModelObjListByClctId — tablespaceNm/bizAreaId/bizAreaNm ----
    print("\n[Step 3] getDataModelObjListByClctId — JOIN 노출 확인")
    # DM 모델 ID 에 '*' 가 포함 — requests params 는 '%2A' 로 인코딩하므로
    # axios 와 동일하게 raw 로 보내려면 URL 에 직접 박는다.
    r = ss.get(f"{BASE_URL}/api/dm/getDataModelObjListByClctId?clctId={DM}", timeout=10)
    arr = r.json() if r.status_code == 200 else []
    obj = next((x for x in arr if x.get("objNm") == "IMSI_TS_E2E"), None)
    record("list_has_obj", obj is not None,
           f"found={obj is not None} / arr={len(arr)}건 objNms={[x.get('objNm') for x in arr]}")
    if obj:
        record("list_tablespaceNm", obj.get("tablespaceNm") == "E2E_TBS",
               f"v={obj.get('tablespaceNm')}")
        record("list_bizAreaId", obj.get("bizAreaId") == biz_area_id,
               f"v={obj.get('bizAreaId')}")
        record("list_bizAreaNm_join", obj.get("bizAreaNm") == "E2E_BIZ_AREA",
               f"v={obj.get('bizAreaNm')}")

    # ---- Step 4. updateObj — 테이블스페이스 변경 + 업무영역 해제 ----
    print("\n[Step 4] updateObj — 수정 반영 (updateDataModelObj 회귀)")
    r = ss.post(f"{BASE_URL}/api/dm/updateObj", json={
        "dataModelId": DM, "origObjNm": "IMSI_TS_E2E", "objNm": "IMSI_TS_E2E",
        "objNmKr": "E2E_TS_테이블", "objOwner": "E2E", "origObjOwner": "E2E",
        "objDesc": "통합테스트", "tablespaceNm": "E2E_TBS_UPD", "bizAreaId": None,
    }, timeout=10)
    record("updateObj_200", r.status_code == 200 and r.json().get("resultCode") == 200,
           f"status={r.status_code}")
    ts = db_exec("SELECT COALESCE(tablespace_nm,'<null>') "
                 f"FROM tb_data_model_obj WHERE dm_id='{DM}' AND obj_nm='IMSI_TS_E2E';")
    record("updateObj_tablespace_changed", ts == "E2E_TBS_UPD", f"db='{ts}'")
    ba = db_exec("SELECT COALESCE(biz_area_id,'<null>') "
                 f"FROM tb_data_model_obj WHERE dm_id='{DM}' AND obj_nm='IMSI_TS_E2E';")
    record("updateObj_bizarea_cleared", ba == "<null>", f"db='{ba}'")

    # ---- Step 5. 인덱스 목록 API — tablespaceNm 키 ----
    print("\n[Step 5] 인덱스 목록 API — tablespaceNm 키 존재")
    r = ss.post(f"{BASE_URL}/api/dm/getDataModelStatsList", json={}, timeout=10)
    models = r.json() if r.status_code == 200 else []
    oracle = next((m for m in models if m.get("dataModelNm") == "오라클테스트"), None)
    if oracle:
        r = ss.get(f"{BASE_URL}/api/dm/getDataModelIndexListByDmId",
                   params={"dataModelId": oracle["dataModelId"]}, timeout=10)
        idx = r.json() if r.status_code == 200 else []
        if idx:
            record("index_has_tablespaceNm_key", "tablespaceNm" in idx[0],
                   f"keys={list(idx[0].keys())}")
        else:
            record("index_list_empty_skip", True, "오라클테스트 인덱스 0건 — 키 검증 skip")
    else:
        record("oracle_model_missing_skip", True, "오라클테스트 모델 없음 — skip")

    # ---- Step 6. dmApproval/submissions 응답 키 회귀 ----
    print("\n[Step 6] submissions 응답 키 camelCase 회귀 (selectSubmissions alias)")
    sj = login("jyjang")
    sj.post(f"{BASE_URL}/api/dm/saveAttrs", json={
        "dataModelId": DM, "objNm": "IMSI_TS_E2E", "objOwner": "E2E",
        "attrs": [{"mode": "ADD", "attrNmKr": "E2E_TS_컬럼", "attrNm": "E2E_TS_COL",
                   "pkYn": "N", "fkYn": "N", "nullableYn": "Y"}],
    }, timeout=10)
    seq = db_exec("SELECT change_seq FROM tb_data_model_change_history "
                  "WHERE attr_nm='E2E_TS_COL' ORDER BY change_seq DESC LIMIT 1;")
    if seq:
        sj.post(f"{BASE_URL}/api/dmApproval/submit",
                json={"changeSeqList": [int(seq)]}, timeout=10)
        r = ss.post(f"{BASE_URL}/api/dmApproval/submissions", json={}, timeout=10)
        subs = r.json() if r.status_code == 200 else []
        mine = [x for x in subs if x.get("changeUserId") == "jyjang"]
        # 버그 시: 키가 'submissionid'(소문자) → .get("submissionId") == None → FAIL
        record("submissions_camelCase_key",
               len(mine) >= 1 and mine[0].get("submissionId") is not None,
               f"keys={list(subs[0].keys()) if subs else 'empty'}")
    else:
        record("draft_seq_found", False, "DRAFT change_seq 조회 실패 — saveAttrs 미반영")

    # ---- cleanup ----
    print("\n[cleanup]")
    cleanup()

    # ---- summary ----
    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print(f"\n{'=' * 60}")
    print(f"[SUMMARY] 총 {len(results)}건 — PASS {passed}, FAIL {failed}")
    print(f"{'=' * 60}")
    if failed:
        print("실패 항목:")
        for n, ok, m in results:
            if not ok:
                print(f"  - {n}: {m}")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[ERROR] 테스트 중 예외: {e}")
        traceback.print_exc()
        try:
            cleanup()
        except Exception:
            pass
        sys.exit(1)
