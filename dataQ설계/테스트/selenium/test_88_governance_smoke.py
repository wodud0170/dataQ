"""
88번 거버넌스 워크플로우 — 1차 smoke 테스트

검증 범위:
1. 관리자(space) 로그인 → 신규 3개 메뉴 클릭 → 페이지 로드 에러 없음
2. 사용자(jyjang) 로그인 → '내 변경 신청' / '데이터 모델 변경 이력' 정상
3. API 기본 동작 — submissions / myDrafts / history 응답 형식
4. DRAFT 행 직접 INSERT → API 응답 / Vue 가시성 검증은 별도 회귀 테스트

상세 승인 플로우 (사용자 변경 → DRAFT → 신청 → 관리자 승인 → APPROVED) 는
saveAttrs 분기 + UI 인테그레이션이 함께 동작해야 해서 1차 smoke 범위 외.
"""
import os
import sys
import time
import traceback

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:28091"
SHOTS = os.path.join(os.path.dirname(__file__), "screenshots", "88_governance")
os.makedirs(SHOTS, exist_ok=True)

results = []


def shot(driver, name):
    path = os.path.join(SHOTS, f"{name}.png")
    driver.save_screenshot(path)
    return path


def mk_driver():
    opt = webdriver.EdgeOptions()
    opt.add_argument("--log-level=3")
    opt.add_argument("--headless=new")
    opt.add_argument("--window-size=1400,900")
    d = webdriver.Edge(options=opt)
    return d


def login(driver, uid, pw):
    driver.get(BASE_URL)
    time.sleep(2)
    WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text'], input#userId"))
    )
    id_input = driver.find_element(By.CSS_SELECTOR, "input[type='text'], input#userId")
    id_input.clear(); id_input.send_keys(uid)
    pw_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_input.clear(); pw_input.send_keys(pw)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
    time.sleep(3)
    return "signin" not in driver.current_url and "login" not in driver.current_url


def expand_group(driver, group_id):
    """v-list-group 펼치기 — 자식 표시 여부로 판단."""
    try:
        # 자식 v-list-item 보이면 펼쳐있음
        children = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__items .v-list-item")
        if children and children[0].is_displayed():
            return True
        # 자식 안 보이면 activator 클릭
        activator = driver.find_element(
            By.CSS_SELECTOR, f"#{group_id} .v-list-group__header"
        )
        driver.execute_script("arguments[0].click();", activator)
        time.sleep(0.8)
        return True
    except Exception:
        return False


def click_nav_menu(driver, menu_label, group_id=None, timeout=10):
    """좌측 네비에서 메뉴 텍스트로 클릭."""
    if group_id:
        expand_group(driver, group_id)
    end = time.time() + timeout
    while time.time() < end:
        try:
            xp = (
                f"//div[contains(@class,'v-list-item__title') and normalize-space(.)='{menu_label}']"
            )
            els = driver.find_elements(By.XPATH, xp)
            for el in els:
                try:
                    if not el.is_displayed():
                        continue
                    driver.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
                    time.sleep(0.2)
                    driver.execute_script("arguments[0].click();", el)
                    time.sleep(2)
                    return True
                except Exception:
                    continue
        except Exception:
            pass
        time.sleep(0.5)
    return False


def check_page_loaded(driver, expected_text_keywords):
    """페이지 로드 후 기대 텍스트가 화면에 보이는지"""
    src = driver.page_source
    for kw in expected_text_keywords:
        if kw not in src:
            return False, f"기대 텍스트 누락: '{kw}'"
    return True, "OK"


def record(name, ok, msg=""):
    icon = "✓" if ok else "✗"
    results.append((name, ok, msg))
    print(f"  [{icon}] {name}: {msg}")


def test_admin_menu_navigation():
    print("\n[TC1] 관리자(space) 신규 메뉴 3개 진입 확인")
    driver = mk_driver()
    try:
        if not login(driver, "space", "123"):
            record("TC1-login", False, "space 로그인 실패")
            return
        record("TC1-login", True, "space 로그인 성공")
        shot(driver, "tc1_01_after_login")

        # 1. 데이터 모델 변경 승인 (관리 그룹 안)
        if click_nav_menu(driver, "데이터 모델 변경 승인", group_id="mmGroup"):
            time.sleep(1.5)
            ok, msg = check_page_loaded(driver, ["데이터 모델 변경 승인", "신청 묶음"])
            shot(driver, "tc1_02_dm_approval")
            record("TC1-dm_approval", ok, msg)
        else:
            shot(driver, "tc1_02_fail_dm_approval")
            record("TC1-dm_approval", False, "메뉴 클릭 실패")

        # 2. 내 변경 신청 (데이터 모델 그룹 안)
        if click_nav_menu(driver, "내 변경 신청", group_id="dmGroup"):
            time.sleep(1.5)
            ok, msg = check_page_loaded(driver, ["내 변경 신청", "DRAFT"])
            shot(driver, "tc1_03_my_changes")
            record("TC1-my_changes", ok, msg)
        else:
            shot(driver, "tc1_03_fail_my_changes")
            record("TC1-my_changes", False, "메뉴 클릭 실패")

        # 3. 데이터 모델 변경 이력 (데이터 모델 그룹 안)
        if click_nav_menu(driver, "데이터 모델 변경 이력", group_id="dmGroup"):
            time.sleep(1.5)
            ok, msg = check_page_loaded(driver, ["데이터 모델 변경 이력", "Tier"])
            shot(driver, "tc1_04_history")
            record("TC1-history", ok, msg)
        else:
            shot(driver, "tc1_04_fail_history")
            record("TC1-history", False, "메뉴 클릭 실패")
    except Exception as e:
        traceback.print_exc()
        try: shot(driver, "tc1_exception")
        except Exception: pass
        record("TC1-exception", False, str(e))
    finally:
        driver.quit()


def test_user_menu_navigation():
    print("\n[TC2] 일반 사용자(jyjang) 메뉴 진입 확인")
    driver = mk_driver()
    try:
        if not login(driver, "jyjang", "123"):
            record("TC2-login", False, "jyjang 로그인 실패")
            return
        record("TC2-login", True, "jyjang 로그인 성공")
        shot(driver, "tc2_01_after_login")

        # 내 변경 신청 (데이터 모델 그룹)
        if click_nav_menu(driver, "내 변경 신청", group_id="dmGroup"):
            time.sleep(1.5)
            ok, msg = check_page_loaded(driver, ["내 변경 신청"])
            shot(driver, "tc2_02_my_changes")
            record("TC2-my_changes", ok, msg)
        else:
            shot(driver, "tc2_02_fail_my_changes")
            record("TC2-my_changes", False, "메뉴 클릭 실패")

        # 변경 이력 — 사용자도 조회 가능 (본인 것 + APPROVED)
        if click_nav_menu(driver, "데이터 모델 변경 이력", group_id="dmGroup"):
            time.sleep(1.5)
            ok, msg = check_page_loaded(driver, ["데이터 모델 변경 이력"])
            shot(driver, "tc2_03_history")
            record("TC2-history", ok, msg)
        else:
            shot(driver, "tc2_03_fail_history")
            record("TC2-history", False, "메뉴 클릭 실패")
    except Exception as e:
        traceback.print_exc()
        try: shot(driver, "tc2_exception")
        except Exception: pass
        record("TC2-exception", False, str(e))
    finally:
        driver.quit()


def test_api_endpoints():
    print("\n[TC3] 거버넌스 API 엔드포인트 응답 형식")
    import requests, base64
    s = requests.Session()
    # 로그인 — 비번은 base64 인코딩 (FE 가 그렇게 보냄)
    enc_pw = base64.b64encode("123".encode()).decode()
    r = s.post(f"{BASE_URL}/login", data={"id": "space", "password": enc_pw}, timeout=10)
    try:
        body = r.json()
        if not body.get("success"):
            record("TC3-login", False, f"로그인 실패 msg={body.get('message')}")
            return
    except Exception:
        record("TC3-login", False, f"JSON parse 실패 status={r.status_code}")
        return
    record("TC3-login", True, "API 로그인 성공")

    # 1. /api/dmApproval/submissions — 관리자만
    r = s.post(f"{BASE_URL}/api/dmApproval/submissions", json={}, timeout=10)
    ok = r.status_code == 200 and isinstance(r.json(), list)
    record("TC3-submissions", ok, f"status={r.status_code}, type={type(r.json()).__name__}")

    # 2. /api/dmApproval/myDrafts
    r = s.post(f"{BASE_URL}/api/dmApproval/myDrafts", json={"dmId": ""}, timeout=10)
    ok = r.status_code == 200 and isinstance(r.json(), list)
    record("TC3-myDrafts", ok, f"status={r.status_code}")

    # 3. /api/dmApproval/history
    r = s.post(f"{BASE_URL}/api/dmApproval/history", json={"dmId": ""}, timeout=10)
    ok = r.status_code == 200 and isinstance(r.json(), list)
    record("TC3-history", ok, f"status={r.status_code}")


def test_visibility_filter():
    print("\n[TC4] 가시성 필터 — DRAFT row 노출 격리")
    import subprocess
    import requests
    # DRAFT 행 1건 삽입 (test_user_xyz 가 신청자)
    sql = """
    INSERT INTO quality.tb_data_model_attr
    (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, data_decimal_len,
     nullable_yn, pk_yn, fk_yn, attr_ord, use_yn, terms_stnd_yn, domain_stnd_yn,
     aprv_status, requester_user_id, req_dt)
    VALUES
    ('9ek4pZ2c4_Wab1k*g1_0yt', 'USER1', 'TB_MEMEBER', 'TC4_DRAFT_COL', 'TC4테스트',
     'VARCHAR', 50, 0, 'Y', 'N', 'N', 100, 'Y', 'N', 'N',
     'DRAFT', 'test_user_xyz', '20260514220000')
    ON CONFLICT (dm_id, obj_owner, obj_nm, attr_nm) DO UPDATE SET
       aprv_status = 'DRAFT', requester_user_id = 'test_user_xyz';
    """
    try:
        subprocess.run(
            ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres", "-c", sql],
            check=True, capture_output=True, text=True, timeout=10
        )
    except Exception as e:
        record("TC4-setup", False, f"DRAFT 삽입 실패: {e}")
        return

    # jyjang 으로 로그인 (admin 아님)
    import base64
    enc_pw = base64.b64encode("123".encode()).decode()
    s = requests.Session()
    s.post(f"{BASE_URL}/login", data={"id": "jyjang", "password": enc_pw}, timeout=10)
    r = s.get(f"{BASE_URL}/api/dm/getDataModelAttrListByClctId?clctId=9ek4pZ2c4_Wab1k*g1_0yt", timeout=10)
    if r.status_code != 200:
        record("TC4-jyjang_api", False, f"status={r.status_code}")
    else:
        data = r.json()
        # TC4_DRAFT_COL 가 응답에 없어야 함 (jyjang 은 test_user_xyz 가 아니므로 가시성 차단)
        leaked = [x for x in data if x.get("attrNm") == "TC4_DRAFT_COL"]
        record("TC4-jyjang_no_leak", len(leaked) == 0,
               f"jyjang 시점 DRAFT 노출 {len(leaked)}건 (0 기대)")

    # space (admin) 으로 로그인 — 모든 row 보여야 함
    s2 = requests.Session()
    s2.post(f"{BASE_URL}/login", data={"id": "space", "password": enc_pw}, timeout=10)
    r2 = s2.get(f"{BASE_URL}/api/dm/getDataModelAttrListByClctId?clctId=9ek4pZ2c4_Wab1k*g1_0yt", timeout=10)
    if r2.status_code != 200:
        record("TC4-space_api", False, f"status={r2.status_code}")
    else:
        data2 = r2.json()
        # 2026-05-17 "88번 거버넌스 — 미승인 가시성 수정"(81e3acb) 으로 정책이 바뀌었다.
        # DataModelController.getDataModelAttrListByClctId 주석 그대로:
        #   "관리자도 남의 미승인(DRAFT/SUBMITTED)은 컬럼 메뉴에 노출 안 됨 — 승인 화면에서만 본다"
        # → admin 도 타인 DRAFT 는 0건이 정상.
        visible = [x for x in data2 if x.get("attrNm") == "TC4_DRAFT_COL"]
        record("TC4-space_no_draft_leak", len(visible) == 0,
               f"space(admin) 시점 타인 DRAFT 노출 {len(visible)}건 (0 기대 — 승인 화면 전용)")

    # cleanup
    try:
        subprocess.run(
            ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
             "-c", "DELETE FROM quality.tb_data_model_attr WHERE attr_nm='TC4_DRAFT_COL' AND requester_user_id='test_user_xyz';"],
            check=True, capture_output=True, text=True, timeout=10
        )
    except Exception:
        pass


def main():
    print(f"BASE_URL={BASE_URL}")
    test_admin_menu_navigation()
    test_user_menu_navigation()
    test_api_endpoints()
    test_visibility_filter()

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
    main()
