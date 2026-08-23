"""
83번 Step 4 — 검증 대상 (DSQualColRule) 재작성 검증.

검증 범위 (15+ 케이스):
  P1.  /api/qual/colrule/listWithLatest — 모델 ID 만으로 조회 OK
  P2.  매퍼 alias 정확 — domainNm/domainClsfNm/effectiveSource 등 camelCase
  P3.  컬럼 검색 (objNm partial)  — ILIKE 동작
  P4.  컬럼 검색 (attrNm partial) — ILIKE 동작
  P5.  도메인 분류 필터 — 매칭만 반환
  P6.  적합률 범위 (rateMin) — 직전 룰 결과 있는 row 만
  P7.  적합률 범위 (rateMax) — 적합률 ≤ N
  P8.  EXCLUDED 행은 응답에 포함 (서버) — 단 클라가 필터
  P9.  /api/qual/colrule/detail — drawer 데이터 반환 (violationSamples + ruleResults)
  P10. detail — 위반 샘플 5건 limit
  P11. detail — 룰별 결과 20건 limit
  P12. /api/qual/colrule/save — 컬럼에 도메인 룰 매핑
  P13. /api/qual/colrule/exclude — 진단 제외 토글
  P14. 일반 사용자 — listWithLatest 조회 OK
  P15. 매퍼: 분류·검색·적합률 동시 적용 — 교집합 정확
  P16. UI — 메뉴 진입 + 모델 선택 + 그리드 렌더 + drawer 동작
"""
import base64
import subprocess
import sys
import time
import traceback

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
    assert r.status_code == 200
    return s


def main():
    admin = login("space", "123")
    user  = login("jyjang", "123")

    # 테스트용 모델 — 'CAMS' (DB 연결된 PHYSICAL 모델)
    dm_row = docker_psql(
        "SELECT DM_ID FROM TB_DATA_MODEL WHERE DM_NM ILIKE '%CAMS%' AND DM_DS_ID IS NOT NULL LIMIT 1;")
    dm_id = dm_row.strip()
    if not dm_id:
        # 다른 PHYSICAL 모델
        dm_id = docker_psql(
            "SELECT DM_ID FROM TB_DATA_MODEL WHERE DM_DS_ID IS NOT NULL LIMIT 1;")
    assert dm_id, "테스트용 PHYSICAL 모델 없음"
    print(f"  테스트 모델 ID: {dm_id}")

    # P1. listWithLatest 기본 조회
    def _p1():
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id}, timeout=15)
        assert r.status_code == 200, r.status_code
        rows = r.json()
        assert isinstance(rows, list)
        assert len(rows) > 0, "컬럼 0건 — 모델에 데이터 없음"
    step("P1. listWithLatest 기본 조회", _p1)

    # P2. alias camelCase 정확
    def _p2():
        rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                         params={"dmId": dm_id}, timeout=15).json()
        first = rows[0]
        for k in ("dmId","objNm","attrNm","attrNmKr","domainClsfNm","effectiveSource"):
            assert k in first, f"필드 {k} 누락 — 응답 키: {list(first.keys())}"
    step("P2. 매퍼 alias camelCase (domainClsfNm 등)", _p2)

    # P3. 테이블 partial 검색
    def _p3():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        # 첫 OBJ_NM 의 앞 3글자로 검색
        sample = all_rows[0]["objNm"][:3]
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "objNm": sample}, timeout=15).json()
        # 모든 결과의 objNm 이 sample 포함
        assert all(sample.lower() in (x["objNm"] or '').lower() for x in r), \
            f"objNm 필터 정확성 실패"
    step("P3. 테이블 partial 검색 (ILIKE)", _p3)

    # P4. 컬럼 partial 검색
    def _p4():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        sample_attr = (all_rows[0]["attrNm"] or '')[:2]
        if not sample_attr: return
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "attrNm": sample_attr}, timeout=15).json()
        assert all(sample_attr.lower() in (x["attrNm"] or '').lower() for x in r)
    step("P4. 컬럼 partial 검색 (ILIKE)", _p4)

    # P5. 도메인 분류 필터
    def _p5():
        # 시드된 분류 중 하나로 필터링 (대부분 모델엔 매칭 0 일 가능성 — 그래도 0 이 정확한 응답)
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "domainClsfNm": "전화번호"}, timeout=15).json()
        # 모든 결과의 domainClsfNm 이 '전화번호'
        for x in r:
            assert x.get("domainClsfNm") == "전화번호", f"필터 미적용: {x}"
    step("P5. 도메인 분류 필터 — '전화번호'", _p5)

    # P6. rateMin 필터 — 적합률 ≥ 95% 인 row 만 반환되거나 NULL (직전 진단 없음)
    def _p6():
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "rateMin": 95}, timeout=15).json()
        for x in r:
            cr = x.get("ruleConformRate")
            if cr is not None:
                assert float(cr) >= 95, f"rateMin 95 위반: {cr}"
    step("P6. rateMin=95 필터 (ruleConformRate ≥ 95)", _p6)

    # P7. rateMax 필터
    def _p7():
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "rateMax": 80}, timeout=15).json()
        for x in r:
            cr = x.get("ruleConformRate")
            if cr is not None:
                assert float(cr) <= 80, f"rateMax 80 위반: {cr}"
    step("P7. rateMax=80 필터", _p7)

    # P8. EXCLUDED 처리 — 진단 제외 토글 후 클라가 필터하지만 서버는 그대로 반환
    def _p8():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        # 제외 토글
        admin.post(BASE + "/api/qual/colrule/exclude", json={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"],
            "excludeYn": "Y"
        }, timeout=10)
        rows2 = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                          params={"dmId": dm_id}, timeout=15).json()
        excluded = [x for x in rows2 if x["objNm"] == any_obj["objNm"]
                    and x["attrNm"] == any_obj["attrNm"]]
        assert len(excluded) == 1
        assert excluded[0]["effectiveSource"] == "EXCLUDED", \
            f"EXCLUDED 기대, 실제 {excluded[0]['effectiveSource']}"
        # 복원
        admin.post(BASE + "/api/qual/colrule/exclude", json={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"],
            "excludeYn": "N"
        }, timeout=10)
    step("P8. EXCLUDED 토글 — 서버는 EXCLUDED 행 그대로 반환", _p8)

    # P9. detail API 응답 구조
    def _p9():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        r = admin.get(BASE + "/api/qual/colrule/detail", params={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"]
        }, timeout=10)
        assert r.status_code == 200
        d = r.json()
        assert "violationSamples" in d and "ruleResults" in d, f"keys: {list(d.keys())}"
        assert isinstance(d["violationSamples"], list)
        assert isinstance(d["ruleResults"], list)
    step("P9. detail API — violationSamples + ruleResults", _p9)

    # P10. detail 위반 샘플 LIMIT 5
    def _p10():
        # 데이터 있어야 검증 가능 — 직전 진단 없으면 0건 반환 (limit 자체는 SQL 검증)
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        r = admin.get(BASE + "/api/qual/colrule/detail", params={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"]
        }, timeout=10).json()
        assert len(r["violationSamples"]) <= 5, f"5건 limit, 실제 {len(r['violationSamples'])}"
    step("P10. detail violationSamples LIMIT 5", _p10)

    # P11. detail 룰 결과 LIMIT 20
    def _p11():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        r = admin.get(BASE + "/api/qual/colrule/detail", params={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"]
        }, timeout=10).json()
        assert len(r["ruleResults"]) <= 20
    step("P11. detail ruleResults LIMIT 20", _p11)

    # P12. save — 컬럼에 도메인 룰 매핑
    def _p12():
        # 임의 도메인 룰 ID 하나 (또는 신규 생성)
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        # 도메인 룰 1건 신규 생성 (테스트용)
        domain_id = docker_psql(
            "SELECT DOMAIN_ID FROM TB_DOMAIN WHERE APRV_YN='Y' LIMIT 1;")
        body_dr = {
            "domainId": domain_id, "ruleNm": "TEST_COLRULE_DR",
            "ruleType": "NOT_NULL", "ruleParams": "{}", "sortOrd": 1, "useYn": "Y"
        }
        admin.post(BASE + "/api/qual/domain/rule/save", json=body_dr, timeout=10)
        dr_id = docker_psql(
            "SELECT DOMAIN_RULE_ID FROM TB_DOMAIN_RULE WHERE RULE_NM='TEST_COLRULE_DR';")
        body = {
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"],
            "domainRuleId": dr_id, "excludeYn": "N"
        }
        r = admin.post(BASE + "/api/qual/colrule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200, r.text
        # 정리
        admin.post(BASE + "/api/qual/domain/rule/delete",
                   json={"domainRuleId": dr_id}, timeout=10)
        docker_psql(
            f"DELETE FROM TB_QUAL_COL_RULE WHERE DM_ID='{dm_id}' "
            f"AND OBJ_NM='{any_obj['objNm']}' AND ATTR_NM='{any_obj['attrNm']}';")
    step("P12. save — 컬럼에 도메인 룰 매핑", _p12)

    # P13. exclude 토글
    def _p13():
        all_rows = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                             params={"dmId": dm_id}, timeout=15).json()
        any_obj = all_rows[0]
        r = admin.post(BASE + "/api/qual/colrule/exclude", json={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"],
            "excludeYn": "Y"
        }, timeout=10)
        assert r.json().get("resultCode") == 200
        cnt = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_QUAL_COL_RULE WHERE DM_ID='{dm_id}' "
            f"AND OBJ_NM='{any_obj['objNm']}' AND ATTR_NM='{any_obj['attrNm']}' AND EXCLUDE_YN='Y';"))
        assert cnt == 1
        # 복원
        admin.post(BASE + "/api/qual/colrule/exclude", json={
            "dmId": dm_id, "objNm": any_obj["objNm"], "attrNm": any_obj["attrNm"],
            "excludeYn": "N"
        }, timeout=10)
    step("P13. exclude 토글", _p13)

    # P14. 일반 사용자 조회 OK
    def _p14():
        r = user.get(BASE + "/api/qual/colrule/listWithLatest",
                     params={"dmId": dm_id}, timeout=15)
        assert r.status_code == 200
    step("P14. 일반 사용자 listWithLatest 조회 OK", _p14)

    # P15. 다중 필터 교집합
    def _p15():
        r = admin.get(BASE + "/api/qual/colrule/listWithLatest",
                      params={"dmId": dm_id, "objNm": "T", "rateMin": 0}, timeout=15).json()
        # 결과는 objNm 에 'T' 포함 + rateMin 위반 없음 (NULL 포함)
        for x in r:
            assert "T".lower() in (x["objNm"] or '').lower()
            cr = x.get("ruleConformRate")
            if cr is not None:
                assert float(cr) >= 0
    step("P15. 다중 필터 (테이블 + rateMin) 교집합", _p15)

    # P16. UI — 메뉴 진입 + 그리드 렌더
    def _p16():
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
            # 데이터 품질 진단 그룹
            headers = drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
            for h in headers:
                if h.text.strip() == "데이터 품질 진단":
                    grp = h
                    for _ in range(8):
                        grp = grp.find_element(By.XPATH, "..")
                        if "v-list-group" in (grp.get_attribute("class") or ""): break
                    if "v-list-group--active" not in (grp.get_attribute("class") or ""):
                        ActionChains(drv).move_to_element(h).click().perform()
                        time.sleep(1)
                    break
            # 검증 대상 (qualColRule) 클릭
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_qualColRule")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(2)
            # 모델 선택
            mdls = drv.find_elements(By.XPATH,
                "//div[contains(@class,'v-tabs-items') or contains(@class,'tab_contents active')]"
                "//label[contains(text(),'모델')]/following-sibling::div//input")
            # 그냥 페이지에 그리드 있는지만 검증 (모델 선택은 데이터 의존)
            # 그리드 헤더 row 가 보이면 화면 OK
            time.sleep(1)
            grid_headers = drv.find_elements(By.CSS_SELECTOR,
                ".v-data-table__wrapper thead th")
            assert len(grid_headers) >= 5, f"그리드 헤더 5+ 기대, 실제 {len(grid_headers)}"
            print(f"  그리드 헤더 {len(grid_headers)} 개 렌더 확인")
        finally:
            time.sleep(1)
            drv.quit()
    ui_step("P16. UI — 메뉴 진입 + 검증 대상 그리드 헤더 렌더", _p16)


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
