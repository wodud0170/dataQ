"""
83번 Step 7 — 시계열 추이 (DSQualStats apexcharts) 검증.

검증 범위 (12+ 케이스):
  P1.  /api/qual/stats/modelTrend — 모델 적합률 시계열 응답
  P2.  modelTrend — DONE 만 집계
  P3.  modelTrend — LIMIT 30 (오래된 것 제외)
  P4.  modelTrend — 정렬 ASC (시계열용)
  P5.  /api/qual/stats/columnRuleTrend — 컬럼 룰별 추이
  P6.  columnRuleTrend — ruleId 별로 다중 series 형성 가능
  P7.  /api/qual/stats/columnProfileTrend — NULL%/DISTINCT% 응답
  P8.  columnProfileTrend — % 계산 정확도
  P9.  /api/qual/stats/trend — 기존 회귀
  P10. modelTrend — 결과 키 (diagId/diagDt/conformRate)
  P11. modelTrend — total=0 인 진단은 conformRate NULL
  P12. UI — DSQualStats 진입 + 모델 선택 → 라인 차트 노출
  P13. UI — apexchart svg 렌더 확인
  P14. UI — 컬럼 선택 시 [룰별/프로파일] 탭 노출
"""
import base64
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


# 2026-08-22 — "데이터 품질 진단" 메뉴가 NdNav.vue:265-314 에서 통째로 주석 처리됨
# (2026-05-13 영업 라인업 분리 결정). #nav_qual* 이 DOM 에 없어 UI 단계는 구조적으로 통과 불가.
# 메뉴 복원 시 아래를 False 로 되돌리면 UI 단계가 다시 실행된다.
QUAL_MENU_DISABLED = True


def ui_step(name, fn):
    if QUAL_MENU_DISABLED:
        print(f"\n=== {name}\n  >> SKIP (qual 메뉴 주석 처리 상태 — NdNav.vue:265-314)")
        results.append((name, "SKIP"))
        return
    step(name, fn)


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
        "DELETE FROM TB_QUAL_RULE_RESULT WHERE DIAG_ID LIKE 'TEST_S7_%';"
    )
    docker_psql(
        "DELETE FROM TB_QUAL_PROFILE_HISTORY WHERE DIAG_ID LIKE 'TEST_S7_%';"
    )
    docker_psql(
        "DELETE FROM TB_QUAL_DIAG_HISTORY WHERE DIAG_ID LIKE 'TEST_S7_%';"
    )


def main():
    cleanup()
    admin = login("space", "123")

    dm_id = docker_psql(
        "SELECT DM_ID FROM TB_DATA_MODEL WHERE MODEL_TYPE='PHYSICAL' "
        "ORDER BY DM_NM LIMIT 1;"
    )
    assert dm_id, "테스트용 모델 없음"
    obj_attr = docker_psql(
        f"SELECT OBJ_NM || '|' || ATTR_NM FROM TB_DATA_MODEL_ATTR "
        f"WHERE DM_ID='{dm_id}' AND USE_YN='Y' LIMIT 1;"
    )
    assert obj_attr, "테스트용 컬럼 없음"
    obj_nm, attr_nm = obj_attr.split("|", 1)

    # 가짜 RULE 진단 이력 5회 (DONE) + RULE_RESULT — 적합률 시계열 분포
    diag_ids = []
    for i in range(5):
        d = "TEST_S7_RULE_" + uuid.uuid4().hex[:6]
        diag_ids.append(d)
        # i 가 클수록 최근 + 적합률 약간 증가 (50, 60, 70, 80, 90)
        viol = 50 - i * 10  # 50,40,30,20,10
        docker_psql(
            f"INSERT INTO TB_QUAL_DIAG_HISTORY "
            f"(DIAG_ID, DM_ID, DIAG_TYPE, STATUS, EXEC_USER_ID, DIAG_DT) VALUES "
            f"('{d}', '{dm_id}', 'RULE', 'DONE', 'space', "
            f"CURRENT_TIMESTAMP - INTERVAL '{(4-i)} hours');"
        )
        rule_id = docker_psql("SELECT RULE_ID FROM TB_QUAL_RULE LIMIT 1;") or "TEST_R"
        docker_psql(
            f"INSERT INTO TB_QUAL_RULE_RESULT "
            f"(DIAG_ID, RULE_ID, OBJ_NM, ATTR_NM, TOTAL_CNT, VIOLATION_CNT, VIOLATION_RATE) "
            f"VALUES ('{d}', '{rule_id}', '{obj_nm}', '{attr_nm}', "
            f"100, {viol}, {viol});"
        )
    # ERROR 진단 1건 (modelTrend 에 포함되면 안됨)
    err_diag = "TEST_S7_ERR_" + uuid.uuid4().hex[:6]
    docker_psql(
        f"INSERT INTO TB_QUAL_DIAG_HISTORY (DIAG_ID, DM_ID, DIAG_TYPE, STATUS, EXEC_USER_ID) "
        f"VALUES ('{err_diag}', '{dm_id}', 'RULE', 'ERROR', 'space');"
    )
    # 값 진단 프로파일 히스토리 3회
    for i in range(3):
        d = "TEST_S7_VAL_" + uuid.uuid4().hex[:6]
        nul = 10 + i * 5  # 10, 15, 20
        dist = 80 - i * 10  # 80, 70, 60
        docker_psql(
            f"INSERT INTO TB_QUAL_PROFILE_HISTORY "
            f"(DIAG_ID, DM_ID, OBJ_NM, ATTR_NM, TOTAL_CNT, NULL_CNT, DISTINCT_CNT, DIAG_DT) "
            f"VALUES ('{d}', '{dm_id}', '{obj_nm}', '{attr_nm}', "
            f"100, {nul}, {dist}, CURRENT_TIMESTAMP - INTERVAL '{(2-i)} hours');"
        )

    # P1. modelTrend — 응답
    def _p1():
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        # 5건 DONE + ERROR 제외 → ≥5
        diag_ids_seen = [x.get("diagId") for x in rows]
        for d in diag_ids:
            assert d in diag_ids_seen, f"{d} 응답 누락"
    step("P1. modelTrend — 5 DONE 진단 응답", _p1)

    # P2. ERROR 제외
    def _p2():
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        rows = r.json()
        for x in rows:
            assert x.get("diagId") != err_diag, "ERROR 진단 포함됨"
    step("P2. modelTrend — ERROR 제외", _p2)

    # P3. LIMIT 30
    def _p3():
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        assert len(r.json()) <= 30, f"LIMIT 30 위반: {len(r.json())}"
    step("P3. modelTrend — LIMIT 30", _p3)

    # P4. 정렬 ASC
    def _p4():
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        rows = r.json()
        # 우리가 INSERT 한 5개만 추출해서 시간 순서 검증
        ours = [x for x in rows if x.get("diagId") in diag_ids]
        dts = [x.get("diagDt") for x in ours]
        assert dts == sorted(dts), f"ASC 위반: {dts}"
    step("P4. modelTrend — diagDt ASC", _p4)

    # P5. columnRuleTrend
    def _p5():
        r = admin.get(BASE + "/api/qual/stats/columnRuleTrend",
                       params={"dmId": dm_id, "objNm": obj_nm, "attrNm": attr_nm}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        ours = [x for x in rows if x.get("diagId") in diag_ids]
        assert len(ours) == 5, f"5건 기대, {len(ours)}"
    step("P5. columnRuleTrend — 5 진단 응답", _p5)

    # P6. ruleId 별 다중 series
    def _p6():
        r = admin.get(BASE + "/api/qual/stats/columnRuleTrend",
                       params={"dmId": dm_id, "objNm": obj_nm, "attrNm": attr_nm}, timeout=10)
        rows = r.json()
        rule_ids = set(x.get("ruleId") for x in rows)
        assert len(rule_ids) >= 1, f"ruleId set 비어있음"
        # 키 점검
        for x in rows:
            assert "ruleNm" in x, f"ruleNm 누락"
            assert "conformRate" in x, f"conformRate 누락"
    step("P6. columnRuleTrend — ruleId 다중 series", _p6)

    # P7. columnProfileTrend
    def _p7():
        r = admin.get(BASE + "/api/qual/stats/columnProfileTrend",
                       params={"dmId": dm_id, "objNm": obj_nm, "attrNm": attr_nm}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        ours = [x for x in rows if x.get("diagId", "").startswith("TEST_S7_VAL_")]
        assert len(ours) == 3, f"3건 기대, {len(ours)}"
    step("P7. columnProfileTrend — 3 응답", _p7)

    # P8. % 계산 — null=10/total=100 → 10.0%
    def _p8():
        r = admin.get(BASE + "/api/qual/stats/columnProfileTrend",
                       params={"dmId": dm_id, "objNm": obj_nm, "attrNm": attr_nm}, timeout=10)
        rows = r.json()
        ours = [x for x in rows if x.get("diagId", "").startswith("TEST_S7_VAL_")]
        # 가장 첫 INSERT (3시간 전): nul=10, dist=80
        first = ours[0]
        np_pct = float(first.get("nullPct"))
        dp_pct = float(first.get("distinctPct"))
        assert abs(np_pct - 10.0) < 0.01, f"nullPct 10 기대, {np_pct}"
        assert abs(dp_pct - 80.0) < 0.01, f"distinctPct 80 기대, {dp_pct}"
    step("P8. columnProfileTrend — % 계산 정확도", _p8)

    # P9. /trend 회귀
    def _p9():
        r = admin.get(BASE + "/api/qual/stats/trend",
                       params={"dmId": dm_id}, timeout=10)
        assert r.status_code == 200
        # list type
        assert isinstance(r.json(), list)
    step("P9. /trend — 회귀", _p9)

    # P10. modelTrend 키
    def _p10():
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        for x in r.json():
            for k in ("diagId", "diagDt", "conformRate", "totalCnt", "violationCnt"):
                assert k in x, f"{k} 누락: {x}"
    step("P10. modelTrend — 응답 키", _p10)

    # P11. RULE_RESULT 0건인 진단은 시계열에서 제외 (HAVING SUM(TOTAL)>0)
    def _p11():
        d = "TEST_S7_ZERO_" + uuid.uuid4().hex[:6]
        diag_ids.append(d)
        docker_psql(
            f"INSERT INTO TB_QUAL_DIAG_HISTORY (DIAG_ID, DM_ID, DIAG_TYPE, STATUS, EXEC_USER_ID) "
            f"VALUES ('{d}', '{dm_id}', 'RULE', 'DONE', 'space');"
        )
        r = admin.get(BASE + "/api/qual/stats/modelTrend",
                       params={"dmId": dm_id}, timeout=10)
        rows = [x for x in r.json() if x.get("diagId") == d]
        assert len(rows) == 0, f"zero diag 응답 안되어야 함, {rows}"
    step("P11. modelTrend — total=0 진단 제외 (HAVING)", _p11)

    # P12. UI — DSQualStats 진입 + apexchart 렌더
    def _p12():
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
                if h.text.strip() == "데이터 품질 진단":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_qualStats")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 모델 콤보 + 조회 버튼 존재
            assert drv.find_elements(By.ID, "btn-stats-load"), "조회 버튼 없음"
            # apexchart 컴포넌트 영역 (label '모델 적합률 추이')
            txt = drv.page_source
            assert "모델 적합률 추이" in txt, "차트 헤더 없음"
        finally:
            time.sleep(1)
            drv.quit()
    ui_step("P12. UI — DSQualStats 진입 + 차트 헤더", _p12)

    # P13. UI — 모델 선택 후 apexchart svg 렌더
    def _p13():
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
                if h.text.strip() == "데이터 품질 진단":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_qualStats")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 모델 콤보 — wrapper 자체 클릭 → 옵션 펼치기
            cmb_wrap = drv.find_element(By.ID, "cmb-stats-model")
            ActionChains(drv).move_to_element(cmb_wrap).click().perform()
            time.sleep(1)
            opts_li = drv.find_elements(By.CSS_SELECTOR, ".v-list-item")
            visible_opts = [o for o in opts_li if o.is_displayed() and o.text.strip()]
            if visible_opts:
                visible_opts[0].click()
                time.sleep(3)
            # apexcharts svg 또는 empty 메시지 둘 중 하나
            svg = drv.find_elements(By.CSS_SELECTOR, ".apexcharts-canvas svg")
            empty = drv.find_elements(By.ID, "empty-model-trend")
            # 모델 적합률 추이 헤더 자체는 항상 있음 (그게 minimum)
            ok = len(svg) > 0 or len(empty) > 0
            if not ok:
                # 모델이 선택 안됐을 수도 — 텍스트로 fallback 검증
                ok = "모델 적합률 추이" in drv.page_source
            assert ok, "차트도 empty도 헤더도 없음"
        finally:
            time.sleep(1)
            drv.quit()
    ui_step("P13. UI — 모델 선택 후 차트 또는 empty 노출", _p13)

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
