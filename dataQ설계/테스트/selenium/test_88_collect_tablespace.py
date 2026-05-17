"""
88번 §15 — Oracle 수집 시 테이블스페이스(TABLESPACE) 수집 통합테스트

dm-collect.xml 의 OracleGetObjs 에 추가한 S1.TABLESPACE_NAME 수집이
실제 Oracle 수집 → tb_data_model_obj.tablespace_nm 까지 반영되는지 검증한다.

대상 Oracle 19c 컨테이너:
  localhost:1522 / SID ORCLCDB / system / oracle
  스키마 SALES_APP / HR_APP / FIN_APP (총 50테이블)
  테이블스페이스 TBS_SALES / TBS_HR / TBS_FIN / TBS_INV / TBS_COMMON

선행조건: q-center(28091) + q-executor + docker(oracle-19c, dataq-db) 기동
실행: python test_88_collect_tablespace.py
"""
import sys
import time
import base64
import subprocess
import traceback
from datetime import datetime

import requests

BASE_URL = "http://localhost:28091"
DS_NM = "E2E_ORA19C"
DM_NM = "E2E_COLLECT_" + datetime.now().strftime("%m%d%H%M%S")

# 데이터소스 VO — host 필드명은 svrAddr (DataSourceVo)
ORACLE = {
    "dbmsTp": "Oracle",
    "svrAddr": "localhost",
    "port": 1522,
    "userId": "system",
    "pwd": "oracle",
    "dbName": "ORCLCDB",
    "driverName": "Oracle",
    "connProps": "SID",
}

results = []
state = {}


def record(name, ok, msg=""):
    icon = "PASS" if ok else "FAIL"
    results.append((name, ok, msg))
    print(f"  [{icon}] {name}: {msg}")


def login(uid):
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(f"{BASE_URL}/login", data={"id": uid, "password": enc}, timeout=10)
    if not r.json().get("success"):
        raise RuntimeError(f"{uid} 로그인 실패: {r.text}")
    return s


def db_exec(sql, schema="quality"):
    return subprocess.run(
        ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
         "-t", "-c", f"SET search_path TO {schema}; " + sql],
        check=True, capture_output=True, text=True, timeout=15
    ).stdout.strip()


def delete_test_datasources():
    """1522/ORCLCDB 테스트 데이터소스 정리 (잔여 포함)."""
    ss = state.get("ss")
    if not ss:
        return
    try:
        r = ss.get(f"{BASE_URL}/api/sysinfo/getDataSourceList", timeout=10)
        for d in (r.json() if r.status_code == 200 else []):
            if str(d.get("port")) == "1522" and d.get("dbName") == "ORCLCDB":
                ss.post(f"{BASE_URL}/api/sysinfo/deleteDataSources",
                        json=[{"id": d.get("id")}], timeout=10)
    except Exception:
        pass


def cleanup():
    dm_id = state.get("dm_id")
    if dm_id:
        for t in ("tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint",
                  "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema"):
            try:
                db_exec(f"DELETE FROM {t} WHERE dm_id = '{dm_id}';")
            except Exception:
                pass
        try:
            db_exec(f"DELETE FROM tb_data_model WHERE dm_id = '{dm_id}';")
        except Exception:
            pass
    delete_test_datasources()


def main():
    ss = login("space")
    state["ss"] = ss

    print("[0] 선제 cleanup")
    delete_test_datasources()

    # ---- Step 1. 데이터소스 등록 ----
    print("\n[Step 1] Oracle 19c 데이터소스 등록")
    r = ss.post(f"{BASE_URL}/api/sysinfo/createDataSource",
                json={**ORACLE, "dsn": DS_NM}, timeout=15)
    record("createDataSource_200", r.status_code == 200, f"status={r.status_code}")

    r = ss.get(f"{BASE_URL}/api/sysinfo/getDataSourceList", timeout=10)
    dslist = r.json() if r.status_code == 200 else []
    ds = next((d for d in dslist
               if str(d.get("port")) == "1522" and d.get("dbName") == "ORCLCDB"
               and d.get("svrAddr") == "localhost"), None)
    state["ds_id"] = ds.get("id") if ds else None
    record("datasource_registered", state["ds_id"] is not None, f"ds_id={state['ds_id']}")
    if not state["ds_id"]:
        raise RuntimeError("데이터소스 등록 실패 — 이후 단계 중단")

    # ---- Step 2. 연결 테스트 ----
    print("\n[Step 2] 데이터소스 연결 테스트")
    r = ss.post(f"{BASE_URL}/api/sysinfo/testDataSource",
                json={**ORACLE, "id": state["ds_id"], "dsn": DS_NM}, timeout=25)
    body = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
    record("testDataSource_ok", str(body.get("resultCode")) == "200",
           f"resultCode={body.get('resultCode')} msg={body.get('resultMessage')}")

    # ---- Step 3. 데이터모델 생성 ----
    print("\n[Step 3] 데이터모델 생성 (PHYSICAL)")
    r = ss.post(f"{BASE_URL}/api/dm/createDataModel", json={
        "dataModelNm": DM_NM, "dataModelDsId": state["ds_id"], "modelType": "PHYSICAL", "ver": "1.0",
    }, timeout=10)
    record("createDataModel_200", r.status_code == 200, f"status={r.status_code}")

    r = ss.post(f"{BASE_URL}/api/dm/getDataModelStatsList", json={}, timeout=10)
    models = r.json() if r.status_code == 200 else []
    dm = next((m for m in models if m.get("dataModelNm") == DM_NM), None)
    state["dm_id"] = dm.get("dataModelId") if dm else None
    record("datamodel_created", state["dm_id"] is not None, f"dm_id={state['dm_id']}")
    if not state["dm_id"]:
        raise RuntimeError("데이터모델 생성 실패 — 이후 단계 중단")

    # ---- Step 3.5. 수집 대상 스키마 지정 ----
    print("\n[Step 3.5] 수집 대상 스키마 지정 (SALES_APP/HR_APP/FIN_APP)")
    r = ss.post(f"{BASE_URL}/api/dm/getSchemaList",
                json={"dataModelDsId": state["ds_id"]}, timeout=25)
    sch = r.json() if r.status_code == 200 else {}
    schemas = sch.get("schemas", []) if isinstance(sch, dict) else []
    targets = [x for x in schemas if x in ("SALES_APP", "HR_APP", "FIN_APP")]
    record("schema_list_loaded", len(targets) == 3,
           f"대상={targets} / 전체 {len(schemas)}개")
    ss.post(f"{BASE_URL}/api/dm/saveDataModelSchemas",
            json=[{"dataModelId": state["dm_id"], "schemaNm": s, "useYn": "Y"}
                  for s in targets], timeout=10)

    # ---- Step 4. 수집 실행 ----
    print("\n[Step 4] 데이터모델 수집 실행 (collectDataModel)")
    r = ss.post(f"{BASE_URL}/api/dm/collectDataModel", json={
        "dataModelId": state["dm_id"], "dataModelNm": DM_NM,
        "dataModelDsId": state["ds_id"], "modelType": "PHYSICAL",
    }, timeout=30)
    record("collectDataModel_200", r.status_code == 200, f"status={r.status_code}")

    # ---- Step 5. 수집 완료 폴링 ----
    print("\n[Step 5] 수집 완료 폴링 (최대 300초)")
    done = False
    for i in range(60):
        time.sleep(5)
        yn = db_exec("SELECT clct_cmptn_yn FROM tb_data_model_clct "
                     f"WHERE dm_id='{state['dm_id']}' ORDER BY clct_start_dt DESC LIMIT 1;")
        objc = db_exec(f"SELECT COUNT(*) FROM tb_data_model_obj WHERE dm_id='{state['dm_id']}';")
        print(f"  [{(i+1)*5}s] clct_cmptn_yn='{yn}' obj={objc}")
        if yn == "Y":
            done = True
            break
    record("collect_completed", done, f"clct_cmptn_yn={'Y' if done else 'timeout'}")

    # ---- Step 6. tablespace_nm 수집 검증 ----
    print("\n[Step 6] 수집된 테이블의 tablespace_nm 검증")
    obj_cnt = int(db_exec(f"SELECT COUNT(*) FROM tb_data_model_obj WHERE dm_id='{state['dm_id']}';") or "0")
    record("obj_collected", obj_cnt > 0, f"테이블 {obj_cnt}개 수집")

    ts_cnt = int(db_exec("SELECT COUNT(*) FROM tb_data_model_obj "
                         f"WHERE dm_id='{state['dm_id']}' AND tablespace_nm LIKE 'TBS%';") or "0")
    record("tablespace_collected", ts_cnt > 0,
           f"TBS* 테이블스페이스가 채워진 테이블 {ts_cnt}개")

    ts_list = db_exec("SELECT string_agg(DISTINCT tablespace_nm, ',' ORDER BY tablespace_nm) "
                      f"FROM tb_data_model_obj WHERE dm_id='{state['dm_id']}' "
                      "AND tablespace_nm IS NOT NULL;")
    record("tablespace_distinct", "TBS" in (ts_list or ""), f"수집된 테이블스페이스=[{ts_list}]")

    idx_ts = int(db_exec("SELECT COUNT(*) FROM tb_data_model_index "
                         f"WHERE dm_id='{state['dm_id']}' AND tablespace_nm IS NOT NULL;") or "0")
    record("index_tablespace_collected", idx_ts >= 0,
           f"인덱스 tablespace_nm 채워진 행 {idx_ts}개")

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
