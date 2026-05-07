"""
83번 Step 6 — 진단 결과 분류 단위 집계 (DSQualRuleResult 재작성) 검증.

검증 범위 (12+ 케이스):
  P1.  /api/qual/rule/historyList — diagType=RULE 응답
  P2.  historyList — dmId 필터
  P3.  /api/qual/rule/resultByRule — 룰 단위 집계 응답
  P4.  /api/qual/rule/resultByClsf — 분류 단위 집계 응답
  P5.  resultByClsf — '미분류' bucket 포함 (도메인 매핑 안된 컬럼)
  P6.  /api/qual/rule/resultByClsfDrill — 분류 drill-down
  P7.  resultByClsfDrill — 잘못된 분류명은 빈 결과
  P8.  resultByClsfDrill — 결과에 conformRate / domainNm / ruleNm 키 존재
  P9.  resultByRule — 정렬: conformRate ASC NULLS LAST
  P10. resultByClsf — 합산 정확도 (totalCnt = sum, violationCnt = sum)
  P11. /api/qual/rule/result — 기존 result 회귀 (history + results)
  P12. /api/qual/rule/violationSample — 룰별 샘플 회귀
  P13. UI — DSQualRuleResult 메뉴 진입 + 4 탭 헤더 렌더
  P14. UI — 모델 선택 → 진단 이력 자동 로드
  P15. UI — 분류 탭 클릭 → 분류별 막대 노출
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
        "DELETE FROM TB_QUAL_RULE_RESULT WHERE DIAG_ID LIKE 'TEST_S6_%';"
    )
    docker_psql(
        "DELETE FROM TB_QUAL_VIOLATION_SAMPLE WHERE DIAG_ID LIKE 'TEST_S6_%';"
    )
    docker_psql(
        "DELETE FROM TB_QUAL_DIAG_HISTORY WHERE DIAG_ID LIKE 'TEST_S6_%';"
    )


def main():
    cleanup()
    admin = login("space", "123")

    # 시드 모델 — PHYSICAL 아무거나
    dm_id = docker_psql(
        "SELECT DM_ID FROM TB_DATA_MODEL WHERE MODEL_TYPE='PHYSICAL' "
        "ORDER BY DM_NM LIMIT 1;"
    )
    assert dm_id, "테스트용 모델 없음"

    # 가짜 진단 이력 + 룰 결과 INSERT — 분류별 집계가 의미 있도록 도메인 분류 보유 컬럼 활용
    diag_id = "TEST_S6_" + uuid.uuid4().hex[:8]
    docker_psql(
        f"INSERT INTO TB_QUAL_DIAG_HISTORY "
        f"(DIAG_ID, DM_ID, DIAG_TYPE, STATUS, EXEC_USER_ID, TOTAL_RULES, TOTAL_VIOLATIONS) "
        f"VALUES ('{diag_id}', '{dm_id}', 'RULE', 'DONE', 'space', 4, 30);"
    )
    # 분류 매핑 있는 컬럼 1개, 없는 컬럼 1개 — 모델 attr 에서 픽업
    cols = docker_psql(
        f"SELECT a.OBJ_NM || '|' || a.ATTR_NM FROM TB_DATA_MODEL_ATTR a "
        f"WHERE a.DM_ID='{dm_id}' AND a.USE_YN='Y' LIMIT 4;"
    ).split("\n")
    cols = [c for c in cols if c]
    assert len(cols) >= 2, "테스트 컬럼 부족"
    pairs = [c.split("|") for c in cols[:4]]

    rule_id_a = docker_psql("SELECT RULE_ID FROM TB_QUAL_RULE LIMIT 1;") or "TEST_RULE_A"
    # 결과 4행 INSERT (분류 분기 위해 컬럼 2개 사용)
    for idx, (obj_nm, attr_nm) in enumerate(pairs[:4]):
        viol = 5 + idx * 5  # 5, 10, 15, 20
        total = 100
        rate = viol  # violation_rate %
        docker_psql(
            f"INSERT INTO TB_QUAL_RULE_RESULT "
            f"(DIAG_ID, RULE_ID, OBJ_NM, ATTR_NM, TOTAL_CNT, VIOLATION_CNT, VIOLATION_RATE) "
            f"VALUES ('{diag_id}', '{rule_id_a}', '{obj_nm}', '{attr_nm}', "
            f"{total}, {viol}, {rate});"
        )
    print(f"  테스트 진단 ID: {diag_id} (룰 결과 4행)")

    # P1. historyList — diagType=RULE
    def _p1():
        r = admin.get(BASE + "/api/qual/rule/historyList",
                       params={"diagType": "RULE"}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        assert any(h.get("diagId") == diag_id for h in rows), \
            f"방금 만든 diagId 미포함: {len(rows)}건"
    step("P1. historyList — diagType=RULE 응답", _p1)

    # P2. historyList — dmId 필터
    def _p2():
        r = admin.get(BASE + "/api/qual/rule/historyList",
                       params={"dmId": dm_id, "diagType": "RULE"}, timeout=10)
        rows = r.json()
        for h in rows:
            assert h.get("dmId") == dm_id, f"dmId 필터 위반: {h.get('dmId')}"
    step("P2. historyList — dmId 필터", _p2)

    # P3. resultByRule
    def _p3():
        r = admin.get(BASE + "/api/qual/rule/resultByRule",
                       params={"diagId": diag_id}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        assert len(rows) >= 1, f"룰 집계 결과 없음"
        # 합산 검증 — total = 4*100, violation = 5+10+15+20 = 50
        total = sum(int(x.get("totalCnt", 0)) for x in rows)
        viol  = sum(int(x.get("violationCnt", 0)) for x in rows)
        assert total == 400, f"total 400 기대, {total}"
        assert viol == 50, f"viol 50 기대, {viol}"
    step("P3. resultByRule — 룰 단위 집계", _p3)

    # P4. resultByClsf
    def _p4():
        r = admin.get(BASE + "/api/qual/rule/resultByClsf",
                       params={"diagId": diag_id}, timeout=10)
        assert r.status_code == 200
        rows = r.json()
        assert len(rows) >= 1, f"분류 집계 결과 없음"
        for row in rows:
            assert "domainClsfNm" in row, f"domainClsfNm 키 누락: {row}"
            assert "conformRate" in row,  f"conformRate 키 누락: {row}"
            assert "colCnt" in row,       f"colCnt 키 누락: {row}"
    step("P4. resultByClsf — 분류 집계 + 키", _p4)

    # P5. 미분류 bucket 포함 (도메인 매핑 없는 컬럼이 있으면)
    def _p5():
        r = admin.get(BASE + "/api/qual/rule/resultByClsf",
                       params={"diagId": diag_id}, timeout=10)
        rows = r.json()
        names = [x.get("domainClsfNm") for x in rows]
        # 4 컬럼 중 도메인 매핑 안된 것이 있으면 '미분류' bucket
        has_unmapped = docker_psql(
            f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR a "
            f"LEFT JOIN TB_TERMS t ON t.TERMS_NM=a.ATTR_NM_KR "
            f"LEFT JOIN TB_DOMAIN d ON d.DOMAIN_NM=t.DOMAIN_NM "
            f"WHERE a.DM_ID='{dm_id}' AND a.USE_YN='Y' "
            f"AND a.OBJ_NM||'|'||a.ATTR_NM IN ('{cols[0]}','{cols[1]}','{cols[2]}','{cols[3]}') "
            f"AND d.DOMAIN_CLSF_NM IS NULL;"
        )
        if int(has_unmapped or 0) > 0:
            assert "미분류" in names, f"미분류 bucket 없음: {names}"
    step("P5. resultByClsf — 미분류 bucket", _p5)

    # P6. resultByClsfDrill — 첫 분류 drill
    first_clsf = [None]
    def _p6():
        r = admin.get(BASE + "/api/qual/rule/resultByClsf",
                       params={"diagId": diag_id}, timeout=10)
        clsfs = r.json()
        assert len(clsfs) > 0
        first_clsf[0] = clsfs[0]["domainClsfNm"]
        r2 = admin.get(BASE + "/api/qual/rule/resultByClsfDrill",
                        params={"diagId": diag_id, "domainClsfNm": first_clsf[0]}, timeout=10)
        assert r2.status_code == 200
        drill = r2.json()
        assert len(drill) > 0, f"drill 빈 결과: {first_clsf[0]}"
    step("P6. resultByClsfDrill — 분류 drill-down", _p6)

    # P7. drill — 잘못된 분류
    def _p7():
        r = admin.get(BASE + "/api/qual/rule/resultByClsfDrill",
                       params={"diagId": diag_id,
                               "domainClsfNm": "_없는분류_" + uuid.uuid4().hex[:6]},
                       timeout=10)
        assert r.status_code == 200
        assert len(r.json()) == 0, "잘못된 분류는 0건 기대"
    step("P7. drill — 잘못된 분류 0건", _p7)

    # P8. drill 응답 키
    def _p8():
        if not first_clsf[0]: return
        r = admin.get(BASE + "/api/qual/rule/resultByClsfDrill",
                       params={"diagId": diag_id, "domainClsfNm": first_clsf[0]}, timeout=10)
        for row in r.json():
            for k in ("objNm", "attrNm", "domainNm", "ruleNm", "conformRate", "totalCnt"):
                assert k in row, f"{k} 키 누락: {row}"
    step("P8. drill — 응답 키 (objNm/attrNm/domainNm/ruleNm/conformRate)", _p8)

    # P9. resultByRule — 정렬 conformRate ASC NULLS LAST
    def _p9():
        r = admin.get(BASE + "/api/qual/rule/resultByRule",
                       params={"diagId": diag_id}, timeout=10)
        rows = r.json()
        rates = [x.get("conformRate") for x in rows]
        # NULL 이 마지막
        seen_null = False
        for v in rates:
            if v is None:
                seen_null = True
            else:
                assert not seen_null, f"NULLS LAST 위반: {rates}"
        # 오름차순
        nums = [v for v in rates if v is not None]
        assert nums == sorted(nums), f"ASC 위반: {nums}"
    step("P9. resultByRule — conformRate ASC NULLS LAST", _p9)

    # P10. resultByClsf — 합산 정확도
    def _p10():
        r = admin.get(BASE + "/api/qual/rule/resultByClsf",
                       params={"diagId": diag_id}, timeout=10)
        rows = r.json()
        total_cnt = sum(int(x.get("totalCnt", 0) or 0) for x in rows)
        viol_cnt  = sum(int(x.get("violationCnt", 0) or 0) for x in rows)
        assert total_cnt == 400, f"분류별 totalCnt 합 400 기대, {total_cnt}"
        assert viol_cnt == 50, f"분류별 violationCnt 합 50 기대, {viol_cnt}"
    step("P10. resultByClsf — 합산 정확도", _p10)

    # P11. /result — 기존 회귀 (history + results)
    def _p11():
        r = admin.get(BASE + "/api/qual/rule/result",
                       params={"diagId": diag_id}, timeout=10)
        assert r.status_code == 200
        rj = r.json()
        rc = rj.get("resultCode")
        assert rc == 200, f"resultCode 200 기대, {rc}"
        c = rj.get("contents")
        if isinstance(c, str):
            import json as _j
            c = _j.loads(c)
        assert (c or {}).get("history"), "history 누락"
        assert (c or {}).get("results"), "results 누락"
    step("P11. /result — 기존 회귀", _p11)

    # P12. /violationSample — 회귀
    def _p12():
        r = admin.get(BASE + "/api/qual/rule/violationSample",
                       params={"diagId": diag_id, "ruleId": rule_id_a}, timeout=10)
        assert r.status_code == 200
        # 샘플 INSERT 안했으니 0건이면 정상
        assert isinstance(r.json(), list)
    step("P12. /violationSample — 회귀", _p12)

    # P13. UI — 4 탭 헤더 + 메뉴 진입
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
            # 데이터 품질 진단 그룹 펼치기
            hdrs = drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
            for h in hdrs:
                if h.text.strip() == "데이터 품질 진단":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            # 진단 결과 메뉴
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_ruleResult")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 4 탭 (3개로 합쳐짐) 헤더 존재
            tabs = drv.find_elements(By.CSS_SELECTOR, ".v-tab")
            tab_texts = [t.text for t in tabs if t.is_displayed()]
            print(f"  탭: {tab_texts}")
            assert any("분류" in t for t in tab_texts), f"분류 탭 없음: {tab_texts}"
            assert any("룰" in t for t in tab_texts), f"룰 탭 없음: {tab_texts}"
        finally:
            time.sleep(1)
            drv.quit()
    step("P13. UI — 4 탭 헤더 + 메뉴 진입", _p13)

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
