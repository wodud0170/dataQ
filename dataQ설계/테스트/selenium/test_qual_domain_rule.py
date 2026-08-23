"""
83번 Step 3 — 도메인 룰 관리 UI 검증.

검증 범위 (12+ 케이스):
  P1.  /api/qual/domain/tree — 분류별 그룹화 + 룰 카운트
  P2.  /api/qual/domain/rules?domainId=... — 도메인별 룰 조회
  P3.  관리자 — 룰 추가 (NOT_NULL)
  P4.  관리자 — 룰 추가 (RANGE) — 파라미터 JSON 정확
  P5.  관리자 — 룰 추가 (REGEX)
  P6.  관리자 — 룰 추가 (ENUM)
  P7.  관리자 — 룰 수정 (sortOrd 변경)
  P8.  관리자 — 룰 삭제
  P9.  카탈로그 → 도메인 매핑 (importFromCatalog)
  P10. 매핑 후 룰 카운트 갱신 (tree API 재조회 시 +1)
  P11. 일반 사용자 — 룰 추가 시도 → 403
  P12. 일반 사용자 — 룰 조회 OK
  P13. UI — 메뉴 진입 → 트리 렌더 → 도메인 클릭 → 우측 룰 그리드 표시
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
    assert r.status_code == 200, f"로그인 실패 {uid}: {r.status_code}"
    return s


def main():
    # 사전 정리
    docker_psql("DELETE FROM TB_DOMAIN_RULE WHERE RULE_NM LIKE 'TEST_DR_%';")

    admin = login("space", "123")
    user  = login("jyjang", "123")

    # 시드 도메인 ID 하나 픽업 (예: 첫 번째 APRV='Y' 도메인)
    domain_id = docker_psql("SELECT DOMAIN_ID FROM TB_DOMAIN WHERE APRV_YN='Y' ORDER BY DOMAIN_NM LIMIT 1;")
    assert domain_id, "테스트용 도메인 없음"
    print(f"  테스트 도메인 ID: {domain_id}")

    # P1. 트리 API
    def _p1():
        r = admin.get(BASE + "/api/qual/domain/tree", timeout=10)
        assert r.status_code == 200
        rows = r.json()
        assert len(rows) > 0, "트리 결과 0건"
        # 최소 분류 컬럼 + 룰카운트 필드 존재
        first = rows[0]
        assert "domainNm" in first and "domainClsfNm" in first and "ruleCnt" in first, f"필드 누락: {first}"
    step("P1. /api/qual/domain/tree — 트리 데이터 + 룰 카운트", _p1)

    # P2. 룰 조회 (도메인 ID 로)
    def _p2():
        r = admin.get(BASE + "/api/qual/domain/rules", params={"domainId": domain_id}, timeout=10)
        assert r.status_code == 200, r.status_code
    step("P2. /api/qual/domain/rules — 도메인별 룰 조회", _p2)

    # P3. NOT_NULL 룰 추가
    def _p3():
        body = {
            "domainId": domain_id,
            "ruleNm": "TEST_DR_NN",
            "ruleType": "NOT_NULL",
            "ruleParams": "{}",
            "sortOrd": 1, "useYn": "Y",
            "descr": "테스트 NOT NULL"
        }
        r = admin.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200, r.text
    step("P3. 룰 추가 — NOT_NULL", _p3)

    # P4. RANGE
    def _p4():
        body = {
            "domainId": domain_id,
            "ruleNm": "TEST_DR_RANGE",
            "ruleType": "RANGE",
            "ruleParams": '{"min":0,"max":100,"integer":true}',
            "sortOrd": 2, "useYn": "Y"
        }
        r = admin.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
        # DB 검증
        params = docker_psql(
            "SELECT RULE_PARAMS FROM TB_DOMAIN_RULE WHERE RULE_NM='TEST_DR_RANGE';")
        assert "min" in params and "max" in params, f"파라미터 저장 안 됨: {params}"
    step("P4. 룰 추가 — RANGE (min/max/integer)", _p4)

    # P5. REGEX
    def _p5():
        body = {
            "domainId": domain_id,
            "ruleNm": "TEST_DR_REGEX",
            "ruleType": "REGEX",
            "ruleParams": '{"pattern":"^[A-Z]+$"}',
            "sortOrd": 3
        }
        r = admin.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
    step("P5. 룰 추가 — REGEX", _p5)

    # P6. ENUM
    def _p6():
        body = {
            "domainId": domain_id,
            "ruleNm": "TEST_DR_ENUM",
            "ruleType": "ENUM",
            "ruleParams": '{"values":["A","B","C"]}',
            "sortOrd": 4
        }
        r = admin.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
    step("P6. 룰 추가 — ENUM (칩 입력)", _p6)

    # P7. 룰 수정
    def _p7():
        rule_id = docker_psql(
            "SELECT DOMAIN_RULE_ID FROM TB_DOMAIN_RULE WHERE RULE_NM='TEST_DR_RANGE';")
        body = {
            "domainRuleId": rule_id,
            "domainId": domain_id,
            "ruleNm": "TEST_DR_RANGE",
            "ruleType": "RANGE",
            "ruleParams": '{"min":0,"max":50}',  # max 변경
            "sortOrd": 99,                        # sortOrd 변경
            "useYn": "Y"
        }
        r = admin.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200
        s = docker_psql(
            f"SELECT SORT_ORD FROM TB_DOMAIN_RULE WHERE DOMAIN_RULE_ID='{rule_id}';")
        assert s == "99", f"sortOrd 99 기대, 실제 {s}"
    step("P7. 룰 수정 — sortOrd 99 + max 50", _p7)

    # P8. 룰 삭제
    def _p8():
        rule_id = docker_psql(
            "SELECT DOMAIN_RULE_ID FROM TB_DOMAIN_RULE WHERE RULE_NM='TEST_DR_NN';")
        r = admin.post(BASE + "/api/qual/domain/rule/delete",
                       json={"domainRuleId": rule_id}, timeout=10)
        assert r.json().get("resultCode") == 200
        cnt = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_DOMAIN_RULE WHERE DOMAIN_RULE_ID='{rule_id}';"))
        assert cnt == 0
    step("P8. 룰 삭제", _p8)

    # P9. 카탈로그 → 도메인 매핑
    def _p9():
        r = admin.post(BASE + "/api/qual/domain/rule/importFromCatalog",
                       json={"domainId": domain_id, "catalogId": "SEED_NOT_NULL"}, timeout=10)
        assert r.json().get("resultCode") == 200, r.text
        cnt = int(docker_psql(
            f"SELECT COUNT(*) FROM TB_DOMAIN_RULE "
            f"WHERE DOMAIN_ID='{domain_id}' AND RULE_NM='NOT NULL';"))
        assert cnt == 1, f"매핑 후 1건 기대, 실제 {cnt}"
    step("P9. 카탈로그 → 도메인 매핑 (importFromCatalog)", _p9)

    # P10. 매핑 후 트리에 룰 카운트 반영
    def _p10():
        rows = admin.get(BASE + "/api/qual/domain/tree", timeout=10).json()
        match = [r for r in rows if r.get("domainId") == domain_id]
        assert match, f"트리에 도메인 {domain_id} 없음"
        assert match[0]["ruleCnt"] >= 4, f"4개 이상 기대, 실제 {match[0]['ruleCnt']}"
    step("P10. 매핑 후 트리 룰 카운트 갱신", _p10)

    # P11. 일반 사용자 룰 추가 시도 → 403
    def _p11():
        body = {
            "domainId": domain_id,
            "ruleNm": "TEST_DR_DENY",
            "ruleType": "NOT_NULL"
        }
        r = user.post(BASE + "/api/qual/domain/rule/save", json=body, timeout=10)
        rc = r.json().get("resultCode")
        assert rc == 403, f"403 기대, 실제 {rc}"
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_DOMAIN_RULE WHERE RULE_NM='TEST_DR_DENY';"))
        assert cnt == 0, "DB 변경 X 기대"
    step("P11. 일반 사용자 룰 추가 → 403", _p11)

    # P12. 일반 사용자 트리 조회 OK
    def _p12():
        r = user.get(BASE + "/api/qual/domain/tree", timeout=10)
        assert r.status_code == 200
        assert len(r.json()) > 0
    step("P12. 일반 사용자 트리 조회 OK", _p12)

    # P13. UI — Selenium 으로 화면 진입 + 트리 + 룰 그리드
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
            # 로그인
            inp = WebDriverWait(drv, 10).until(EC.presence_of_element_located(
                (By.CSS_SELECTOR, "input[type='text']")))
            inp.send_keys("space")
            drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
            drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
            time.sleep(3)
            assert "/app/main" in drv.current_url
            # 데이터 품질 진단 그룹 펼치기
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
            # 도메인 룰 관리 클릭
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_qualDomainRule")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 트리 노드 (분류 텍스트 또는 도메인) 1개 이상 렌더
            tree_nodes = drv.find_elements(By.CSS_SELECTOR, ".v-treeview-node__content")
            assert len(tree_nodes) > 0, f"트리 노드 0건 — 렌더 실패"
            print(f"  트리 노드: {len(tree_nodes)} 개 렌더 확인")
            # [카탈로그] 버튼 존재
            btns = drv.find_elements(By.XPATH, "//button[contains(., '카탈로그')]")
            assert len(btns) > 0, "[카탈로그] 버튼 없음"
        finally:
            time.sleep(1)
            drv.quit()
    ui_step("P13. UI — 메뉴 진입 + 트리 렌더 + [카탈로그] 버튼", _p13)

    # cleanup
    docker_psql("DELETE FROM TB_DOMAIN_RULE WHERE RULE_NM LIKE 'TEST_DR_%' OR RULE_NM = 'NOT NULL';")


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
