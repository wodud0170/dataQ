"""
85번 — XMI 2.1 export (DataQ 모델 → 표준 포맷 추출) 검증.

검증 범위 (10+ 케이스):
  P1.  DDL — TB_DATA_MODEL_ATTR 에 FK_PARENT_OBJ_NM, FK_PARENT_ATTR_NM 컬럼 존재
  P2.  /api/dm/exportXmi — 200 + Content-Type=application/xml
  P3.  exportXmi — Content-Disposition attachment + filename .xmi
  P4.  exportXmi — XMI 루트 (xmi:XMI version=2.1) + uml:Model
  P5.  exportXmi — packagedElement xmi:type=uml:Class 출력
  P6.  exportXmi — ownedAttribute xmi:type=uml:Property 출력
  P7.  exportXmi — PK 컬럼 isID="true"
  P8.  exportXmi — FK 컬럼 type="cls-{parent}" 참조
  P9.  exportXmi — nullable=N → lowerValue=1
  P10. round-trip — export 한 XMI 를 parseXmi 로 다시 import 시 테이블/컬럼 일치
  P11. UI — [XMI 2.1 추출] 버튼 노출 + 모델 미선택 시 disabled
  P12. UI — 모델 선택 후 추출 버튼 활성
"""
import base64
import io
import os
import subprocess
import sys
import time
import traceback
import uuid

import requests

BASE = "http://localhost:28091"
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
    assert r.status_code == 200, f"로그인 실패 {uid}: {r.status_code}"
    return s


def cleanup():
    docker_psql(
        "DELETE FROM TB_DATA_MODEL_ATTR WHERE DM_ID LIKE 'TEST_XEXP_%';"
    )
    docker_psql(
        "DELETE FROM TB_DATA_MODEL_OBJ WHERE DM_ID LIKE 'TEST_XEXP_%';"
    )
    docker_psql(
        "DELETE FROM TB_DATA_MODEL WHERE DM_ID LIKE 'TEST_XEXP_%';"
    )


def main():
    cleanup()
    admin = login("space", "123")

    # P1. DDL
    def _p1():
        out = docker_psql(
            "SELECT COUNT(*) FROM information_schema.columns "
            "WHERE table_schema='quality' AND table_name='tb_data_model_attr' "
            "AND column_name IN ('fk_parent_obj_nm','fk_parent_attr_nm');"
        )
        assert out == "2", f"FK_PARENT 컬럼 2개 기대, {out}"
    step("P1. DDL — FK_PARENT_OBJ_NM, FK_PARENT_ATTR_NM 추가", _p1)

    # 테스트용 데이터 모델 INSERT
    dm_id = "TEST_XEXP_" + uuid.uuid4().hex[:6]
    docker_psql(
        f"INSERT INTO TB_DATA_MODEL (DM_ID, DM_NM, MODEL_TYPE, USE_YN, CRET_USER_ID, VER) "
        f"VALUES ('{dm_id}', 'XmiExportTest', 'PHYSICAL', 'Y', 'space', '1.0');"
    )
    # OBJ 2건 (PARENT_TBL, CHILD_TBL)
    for obj in ["PARENT_TBL", "CHILD_TBL"]:
        docker_psql(
            f"INSERT INTO TB_DATA_MODEL_OBJ (DM_ID, OBJ_NM, USE_YN) "
            f"VALUES ('{dm_id}', '{obj}', 'Y');"
        )
    # ATTR — PARENT_TBL: ID(PK), NAME / CHILD_TBL: ID(PK), PARENT_ID(FK→PARENT_TBL.ID), DESCR
    docker_psql(
        f"""
        INSERT INTO TB_DATA_MODEL_ATTR
            (DM_ID, OBJ_NM, ATTR_NM, DATA_TYPE, NULLABLE_YN, PK_YN, FK_YN, ATTR_ORD, USE_YN)
        VALUES
            ('{dm_id}', 'PARENT_TBL', 'ID',   'INTEGER',     'N', 'Y', 'N', 1, 'Y'),
            ('{dm_id}', 'PARENT_TBL', 'NAME', 'VARCHAR',     'N', 'N', 'N', 2, 'Y'),
            ('{dm_id}', 'CHILD_TBL',  'ID',   'INTEGER',     'N', 'Y', 'N', 1, 'Y'),
            ('{dm_id}', 'CHILD_TBL',  'DESCR','VARCHAR',     'Y', 'N', 'N', 3, 'Y');
        """
    )
    # FK row 별도 INSERT (FK_PARENT_OBJ_NM 까지 명시)
    docker_psql(
        f"""
        INSERT INTO TB_DATA_MODEL_ATTR
            (DM_ID, OBJ_NM, ATTR_NM, DATA_TYPE, NULLABLE_YN, PK_YN, FK_YN,
             FK_PARENT_OBJ_NM, FK_PARENT_ATTR_NM, ATTR_ORD, USE_YN)
        VALUES
            ('{dm_id}', 'CHILD_TBL', 'PARENT_ID', 'INTEGER', 'N', 'N', 'Y',
             'PARENT_TBL', 'ID', 2, 'Y');
        """
    )

    # P2. exportXmi 응답
    body = [None]
    def _p2():
        r = admin.get(BASE + "/api/dm/exportXmi", params={"dataModelId": dm_id}, timeout=15)
        assert r.status_code == 200, f"HTTP {r.status_code}"
        ct = r.headers.get("Content-Type", "")
        assert "xml" in ct.lower(), f"Content-Type xml 기대, {ct}"
        body[0] = r.text
    step("P2. /api/dm/exportXmi — 200 + Content-Type xml", _p2)

    # P3. Content-Disposition
    def _p3():
        r = admin.get(BASE + "/api/dm/exportXmi", params={"dataModelId": dm_id}, timeout=15)
        cd = r.headers.get("Content-Disposition", "")
        assert "attachment" in cd, f"attachment 기대, {cd}"
        assert ".xmi" in cd, f"filename .xmi 기대, {cd}"
    step("P3. Content-Disposition — attachment + .xmi", _p3)

    # P4. XMI 루트
    def _p4():
        x = body[0]
        assert "xmi:XMI" in x and "version=\"2.1\"" in x, "XMI root 누락"
        assert "uml:Model" in x, "uml:Model 누락"
    step("P4. XMI root + uml:Model", _p4)

    # P5. uml:Class 출력
    def _p5():
        x = body[0]
        assert "uml:Class" in x and "PARENT_TBL" in x and "CHILD_TBL" in x, \
            "Class 미출력"
    step("P5. packagedElement xmi:type=uml:Class", _p5)

    # P6. ownedAttribute
    def _p6():
        x = body[0]
        assert "ownedAttribute" in x and "uml:Property" in x, "ownedAttribute 누락"
        # 이름 확인
        assert "name=\"NAME\"" in x and "name=\"DESCR\"" in x, "컬럼명 누락"
    step("P6. ownedAttribute xmi:type=uml:Property", _p6)

    # P7. PK isID
    def _p7():
        x = body[0]
        # PARENT_TBL.ID 가 PK 이므로 isID=true 표시
        # 정규식 또는 substring 검사
        assert "isID=\"true\"" in x, "PK isID=true 누락"
    step("P7. PK 컬럼 isID=true", _p7)

    # P8. FK type 참조
    def _p8():
        x = body[0]
        # CHILD_TBL.PARENT_ID 가 FK → type="cls-PARENT_TBL"
        assert "type=\"cls-PARENT_TBL\"" in x, "FK type 참조 누락"
    step("P8. FK 컬럼 type=cls-{parent}", _p8)

    # P9. nullable=N → lowerValue=1
    def _p9():
        x = body[0]
        # lowerValue value="1" 가 최소 1번 이상 나와야 함 (NOT NULL 컬럼들)
        cnt_lower1 = x.count("LiteralInteger\" value=\"1\"")
        assert cnt_lower1 >= 1, f"nullable=N → lowerValue=1 누락"
    step("P9. nullable=N → lowerValue=1", _p9)

    # P10. round-trip — export 결과를 parseXmi 로 다시 import
    def _p10():
        x = body[0]
        r = admin.post(BASE + "/api/dm/parseXmi",
                        files={"file": ("export.xmi", x.encode("utf-8"), "application/xml")},
                        timeout=15)
        rj = r.json()
        assert rj.get("success") is True, f"round-trip parse 실패: {rj}"
        assert rj["tableCount"] == 2, f"round-trip tableCount 2 기대, {rj['tableCount']}"
        assert rj["columnCount"] == 5, f"round-trip columnCount 5 기대, {rj['columnCount']}"
        # FK 확인 — CHILD_TBL.PARENT_ID 의 fkYn=Y, fkParentObjNm=PARENT_TBL
        fk = next((c for c in rj["columns"]
                    if c["objNm"] == "CHILD_TBL" and c["attrNm"] == "PARENT_ID"), None)
        assert fk is not None, "PARENT_ID 컬럼 round-trip 누락"
        assert fk.get("fkYn") == "Y", f"fkYn Y 기대, {fk.get('fkYn')}"
        assert fk.get("fkParentObjNm") == "PARENT_TBL", \
            f"fkParentObjNm PARENT_TBL 기대, {fk.get('fkParentObjNm')}"
    step("P10. round-trip — export → parseXmi 일치", _p10)

    # P11. UI — 추출 버튼 (모델 미선택 시 disabled)
    def _p11():
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
                (By.ID, "nav_erwinImport")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            btn = drv.find_element(By.ID, "btn-export-xmi")
            disabled = btn.get_attribute("disabled")
            assert disabled, f"모델 미선택 시 disabled 기대, {disabled}"
        finally:
            time.sleep(1)
            drv.quit()
    step("P11. UI — [XMI 2.1 추출] 버튼 + 모델 미선택 disabled", _p11)

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
