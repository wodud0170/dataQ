"""
85번 — XMI 견고성 검증 (빈 모델 / 수정 가능성 / 트랜잭션 rollback / 성능 100+).

영역별 phase:
  A. 빈 모델 graceful 처리 (3 phase)
  B. import 후 ATTR/OBJ 수정 가능성 (4 phase)
  C. import 트랜잭션 rollback (3 phase)
  D. 100+ 테이블 import 성능 (2 phase)
  → 총 12 phase
"""
import base64
import os
import subprocess
import sys
import time
import traceback
import uuid

import requests

BASE = "http://localhost:28091"
HERE = os.path.dirname(os.path.abspath(__file__))
SAMPLE_XMI = os.path.join(HERE, "data", "sample_xmi_2.1.xmi")
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def docker_psql(sql):
    cmd = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
           "-t", "-A", "-c", "SET search_path TO quality;" + sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def login(uid, pw):
    s = requests.Session()
    enc = base64.b64encode(pw.encode()).decode()
    r = s.post(BASE + "/login", data={"id": uid, "password": enc},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {uid}"
    return s


def cleanup():
    docker_psql("DELETE FROM TB_DATA_MODEL_ATTR WHERE DM_ID LIKE 'TEST_RB_%';")
    docker_psql("DELETE FROM TB_DATA_MODEL_OBJ  WHERE DM_ID LIKE 'TEST_RB_%';")
    docker_psql("DELETE FROM TB_DATA_MODEL      WHERE DM_ID LIKE 'TEST_RB_%';")


def insert_empty_model(dm_id, name="RobustTest"):
    docker_psql(
        f"INSERT INTO TB_DATA_MODEL (DM_ID, DM_NM, MODEL_TYPE, USE_YN, CRET_USER_ID, VER) "
        f"VALUES ('{dm_id}', '{name}_{dm_id[-6:]}', 'PHYSICAL', 'Y', 'space', '1.0');"
    )


def main():
    cleanup()
    admin = login("space", "123")
    assert os.path.exists(SAMPLE_XMI), f"샘플 XMI 없음"
    with open(SAMPLE_XMI, "rb") as f:
        sample_bytes = f.read()
    parsed = admin.post(BASE + "/api/dm/parseXmi",
                         files={"file": ("s.xmi", sample_bytes, "application/xml")}, timeout=10).json()

    # ============================================================
    # A. 빈 모델 graceful 처리
    # ============================================================
    empty_id = "TEST_RB_E_" + uuid.uuid4().hex[:6]
    insert_empty_model(empty_id, "EmptyModel")

    # A-1. OBJ 목록 API — 0건 응답 (200 OK)
    def _a1():
        r = admin.get(BASE + "/api/dm/getDataModelObjListByClctId",
                       params={"clctId": empty_id}, timeout=10)
        assert r.status_code == 200, f"HTTP {r.status_code}"
        rows = r.json()
        assert isinstance(rows, list) and len(rows) == 0, \
            f"빈 list 기대, {len(rows) if isinstance(rows, list) else type(rows)}"
    step("A-1. 빈 모델 — OBJ 목록 API 0건 응답", _a1)

    # A-2. exportXmi — 빈 모델도 graceful (xmi:XMI root + uml:Model 만)
    def _a2():
        r = admin.get(BASE + "/api/dm/exportXmi",
                       params={"dataModelId": empty_id}, timeout=10)
        assert r.status_code == 200
        x = r.text
        assert "xmi:XMI" in x and "uml:Model" in x and "uml:Class" not in x, \
            "빈 모델 export — root 만 있고 클래스 없어야"
    step("A-2. 빈 모델 — exportXmi 200 + 클래스 0건", _a2)

    # A-3. UI — 메뉴 진입 graceful (모델 콤보 검색 후 빈 모델 선택)
    #       UI 메시지 텍스트 확인은 모델 선택 흐름이 복잡 → 메뉴 진입 + 화면 렌더만 검증
    def _a3():
        from selenium import webdriver
        from selenium.webdriver.common.by import By
        from selenium.webdriver.common.action_chains import ActionChains
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC
        opts = webdriver.EdgeOptions()
        opts.add_argument("--log-level=3")
        drv = webdriver.Edge(options=opts)
        drv.set_window_size(1500, 1000)
        try:
            drv.get(BASE)
            time.sleep(2)
            inp = WebDriverWait(drv, 10).until(EC.presence_of_element_located(
                (By.CSS_SELECTOR, "input[type='text']")))
            inp.send_keys("space")
            drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
            drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
            time.sleep(3)
            hdrs = drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
            for h in hdrs:
                if h.text.strip() == "데이터 모델":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_datamodelStatusTable")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 화면 자체가 에러 없이 떠야 (page_source 에 'Error' 없음)
            page = drv.page_source
            # Vuetify 에러 또는 Java stack trace 흔적 미존재
            assert "Whitelabel Error Page" not in page, "Whitelabel 에러"
            assert "java.lang." not in page, "Java stack 노출"
        finally:
            time.sleep(1)
            drv.quit()
    step("A-3. UI — 메뉴 진입 시 에러 페이지 안 뜸", _a3)

    # ============================================================
    # B. import 후 ATTR/OBJ 수정 가능성
    # ============================================================
    edit_id = "TEST_RB_M_" + uuid.uuid4().hex[:6]
    insert_empty_model(edit_id, "EditTest")
    # XMI import
    rj = admin.post(BASE + "/api/dm/importXmiModel", json={
        "dataModelId": edit_id, "tables": parsed["tables"], "columns": parsed["columns"]
    }, timeout=15).json()
    assert rj.get("success") is True, "import 실패 — 후속 테스트 진행 불가"

    # B-1. /updateAttr API 호출 — dataType 변경
    def _b1():
        body = {
            "dataModelId": edit_id,
            "objNm": "EMPLOYEE",
            "attrNm": "EMP_NAME",
            "attrNmKr": "직원명",
            "dataType": "VARCHAR",
            "dataLen": 100,
            "nullableYn": "N",
            "pkYn": "N",
            "fkYn": "N",
            "termsStndYn": "N"
        }
        r = admin.post(BASE + "/api/dm/updateAttr", json=body, timeout=10)
        rj2 = r.json()
        rc = rj2.get("resultCode")
        assert rc == 200, f"updateAttr 200 기대, {rc}"
        # DB 재조회 — dataType / dataLen 갱신 확인
        out = docker_psql(
            f"SELECT DATA_TYPE || '|' || DATA_LEN FROM TB_DATA_MODEL_ATTR "
            f"WHERE DM_ID='{edit_id}' AND OBJ_NM='EMPLOYEE' AND ATTR_NM='EMP_NAME';"
        )
        assert out == "VARCHAR|100", f"DB 갱신 안됨: {out}"
    step("B-1. /updateAttr — dataType/dataLen 변경 + DB 반영", _b1)

    # B-2. /updateAttr — nullable 토글
    def _b2():
        body = {
            "dataModelId": edit_id,
            "objNm": "EMPLOYEE",
            "attrNm": "EMAIL",
            "attrNmKr": "이메일",
            "dataType": "VARCHAR",
            "dataLen": 200,
            "nullableYn": "N",  # 기존 Y → N
            "pkYn": "N",
            "fkYn": "N",
            "termsStndYn": "N"
        }
        r = admin.post(BASE + "/api/dm/updateAttr", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
        out = docker_psql(
            f"SELECT NULLABLE_YN FROM TB_DATA_MODEL_ATTR "
            f"WHERE DM_ID='{edit_id}' AND OBJ_NM='EMPLOYEE' AND ATTR_NM='EMAIL';"
        )
        assert out == "N", f"nullable N 기대, {out}"
    step("B-2. /updateAttr — nullable 토글 반영", _b2)

    # B-3. /updateObj API — 테이블 메타 변경
    def _b3():
        body = {
            "dataModelId": edit_id,
            "objNm": "EMPLOYEE",
            "objNmKr": "직원테이블",
            "objComment": "수정테스트"
        }
        r = admin.post(BASE + "/api/dm/updateObj", json=body, timeout=10)
        rc = r.json().get("resultCode")
        # updateObj endpoint 존재 검증 + 200 응답
        assert rc == 200, f"updateObj 200 기대, {rc}"
        out = docker_psql(
            f"SELECT OBJ_NM_KR FROM TB_DATA_MODEL_OBJ "
            f"WHERE DM_ID='{edit_id}' AND OBJ_NM='EMPLOYEE';"
        )
        assert out == "직원테이블", f"OBJ_NM_KR 변경 미반영, {out}"
    step("B-3. /updateObj — 테이블 한글명 변경 + DB 반영", _b3)

    # B-4. /deleteAttr API — 컬럼 삭제 (USE_YN=N 또는 실 DELETE)
    def _b4():
        body = {
            "dataModelId": edit_id,
            "objNm": "EMPLOYEE",
            "attrNm": "EMAIL"
        }
        r = admin.post(BASE + "/api/dm/deleteAttr", json=body, timeout=10)
        rc = r.json().get("resultCode")
        assert rc == 200, f"deleteAttr 200 기대, {rc}"
        # 삭제 후 active 컬럼 0건
        out = docker_psql(
            f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR "
            f"WHERE DM_ID='{edit_id}' AND OBJ_NM='EMPLOYEE' AND ATTR_NM='EMAIL' AND USE_YN='Y';"
        )
        assert int(out) == 0, f"deleteAttr 후 active row 0 기대, {out}"
    step("B-4. /deleteAttr — 컬럼 삭제 + DB 반영", _b4)

    # ============================================================
    # C. import 트랜잭션 rollback
    # ============================================================
    rb_id = "TEST_RB_R_" + uuid.uuid4().hex[:6]
    insert_empty_model(rb_id, "RollbackTest")

    # C-1. 의도적 실패 — 두번째 컬럼의 attrNm 길이 256자 (DB VARCHAR 255 초과)
    #      controller 가 catch + rollback 호출하므로 첫 OBJ INSERT 도 사라져야
    def _c1():
        long_name = "X" * 260  # 256자 초과
        body = {
            "dataModelId": rb_id,
            "tables": [
                {"objNm": "T1", "objNmKr": "T1", "objAttrCnt": 2}
            ],
            "columns": [
                {"objNm": "T1", "attrNm": "GOOD_COL", "attrNmKr": "GOOD_COL",
                 "dataType": "VARCHAR", "dataLen": 0, "nullableYn": "Y",
                 "pkYn": "N", "fkYn": "N", "attrOrder": 1},
                {"objNm": "T1", "attrNm": long_name, "attrNmKr": "BAD",
                 "dataType": "VARCHAR", "dataLen": 0, "nullableYn": "Y",
                 "pkYn": "N", "fkYn": "N", "attrOrder": 2}
            ]
        }
        r = admin.post(BASE + "/api/dm/importXmiModel", json=body, timeout=10)
        rj2 = r.json()
        # 실패 응답이어야
        assert rj2.get("success") is False, f"실패 응답 기대: {rj2}"
    step("C-1. 의도적 실패 — long attrNm INSERT 거부", _c1)

    # C-2. rollback 검증 — 첫번째 OBJ도 DB 에 남으면 안됨
    def _c2():
        cnt_obj = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_DATA_MODEL_OBJ WHERE DM_ID='{rb_id}';"
        ))
        cnt_attr = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{rb_id}';"
        ))
        # rollback 됐다면 둘 다 0 — partial commit 이면 OBJ 1 / ATTR 1 (GOOD_COL 만)
        assert cnt_obj == 0 and cnt_attr == 0, \
            f"rollback 실패! OBJ={cnt_obj} ATTR={cnt_attr} (partial commit)"
    step("C-2. rollback — OBJ/ATTR 둘 다 0 (partial commit 없음)", _c2)

    # C-3. rollback 후 정상 import 가능 (DB 정합성 회복)
    def _c3():
        body = {
            "dataModelId": rb_id,
            "tables": [{"objNm": "T1", "objNmKr": "T1", "objAttrCnt": 1}],
            "columns": [{"objNm": "T1", "attrNm": "OK", "attrNmKr": "OK",
                          "dataType": "VARCHAR", "dataLen": 0, "nullableYn": "Y",
                          "pkYn": "N", "fkYn": "N", "attrOrder": 1}]
        }
        r = admin.post(BASE + "/api/dm/importXmiModel", json=body, timeout=10)
        assert r.json().get("success") is True, "rollback 후 정상 import 실패"
        cnt = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{rb_id}';"
        ))
        assert cnt == 1, f"정상 import 후 ATTR 1 기대, {cnt}"
    step("C-3. rollback 후 정상 import — DB 정합성 회복", _c3)

    # ============================================================
    # D. 100+ 테이블 import 성능
    # ============================================================
    perf_id = "TEST_RB_P_" + uuid.uuid4().hex[:6]
    insert_empty_model(perf_id, "PerfTest")

    # 100 테이블 / 각 5 컬럼 = 500 컬럼
    big_tables = [{"objNm": f"BIG_TBL_{i:03d}", "objNmKr": f"BIG_TBL_{i:03d}", "objAttrCnt": 5}
                   for i in range(100)]
    big_columns = []
    for i in range(100):
        for j in range(5):
            big_columns.append({
                "objNm": f"BIG_TBL_{i:03d}", "attrNm": f"COL_{j}", "attrNmKr": f"COL_{j}",
                "dataType": "VARCHAR", "dataLen": 0, "nullableYn": "Y",
                "pkYn": "Y" if j == 0 else "N", "fkYn": "N", "attrOrder": j + 1
            })

    # D-1. 100 테이블 import < 30s
    def _d1():
        t0 = time.time()
        r = admin.post(BASE + "/api/dm/importXmiModel", json={
            "dataModelId": perf_id, "tables": big_tables, "columns": big_columns
        }, timeout=60)
        elapsed = time.time() - t0
        assert r.json().get("success") is True, f"100 테이블 import 실패: {r.json()}"
        assert elapsed < 30, f"100 테이블 < 30s 기대, {elapsed:.1f}s"
        print(f"  100 테이블 / 500 컬럼 import {elapsed:.2f}s")
    step("D-1. 100 테이블 / 500 컬럼 import < 30s", _d1)

    # D-2. 100 테이블 export < 15s
    def _d2():
        t0 = time.time()
        r = admin.get(BASE + "/api/dm/exportXmi", params={"dataModelId": perf_id}, timeout=30)
        elapsed = time.time() - t0
        assert r.status_code == 200
        cls_cnt = r.text.count("uml:Class")
        assert cls_cnt >= 100, f"100 클래스 기대, {cls_cnt}"
        assert elapsed < 15, f"100 테이블 export < 15s 기대, {elapsed:.1f}s"
        print(f"  100 테이블 export {elapsed:.2f}s, 본문 {len(r.text)} bytes")
    step("D-2. 100 테이블 export < 15s", _d2)

    cleanup()


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.1f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
