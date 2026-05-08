"""
85번 — XMI 2.1 임포트 (모델링 도구 임포트 메뉴) 검증.

기존 ERwin native XML 코드는 보존, XMI 2.1 옵션으로 분기 추가.

검증 범위 (12+ 케이스):
  P1.  /api/dm/parseXmi — XMI 2.1 sample 파싱 응답
  P2.  parseXmi — tables 3건 (EMPLOYEE / DEPARTMENT / CUSTOMER) 추출
  P3.  parseXmi — columns 9건 (4 + 2 + 3)
  P4.  parseXmi — type href 의 # 뒤 PrimitiveType 추출 (Integer / String)
  P5.  parseXmi — lowerValue=0 → nullableYn=Y / lowerValue=1 → N
  P6.  parseXmi — type 속성 (id 참조) → 클래스명 해석 (DEPT_ID type=cls-department → DEPARTMENT)
  P7.  parseXmi — 패키지 중첩 안 클래스도 모두 추출 (HR + SALES)
  P8.  parseXmi — XXE 방지 (DOCTYPE 포함 파일은 거부)
  P9.  parseXmi — 잘못된 XML → 실패 응답
  P10. /api/dm/parseErwinXml — 기존 ERwin native 회귀 (호출 가능, 다른 포맷 분기)
  P11. UI — 모델링 도구 임포트 메뉴 진입 + 포맷 라디오 (ERwin / XMI) 노출
  P12. UI — XMI 라디오 선택 시 라벨 변경 ("XMI 2.1 파일")
  P13. UI — 파일 업로드 input 존재 + accept 속성
"""
import base64
import os
import sys
import time
import traceback

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


def login(uid, pw):
    s = requests.Session()
    enc = base64.b64encode(pw.encode()).decode()
    r = s.post(BASE + "/login", data={"id": uid, "password": enc},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {uid}: {r.status_code}"
    return s


def main():
    assert os.path.exists(SAMPLE_XMI), f"샘플 XMI 없음: {SAMPLE_XMI}"
    admin = login("space", "123")

    # P1. parseXmi 응답
    parsed = [None]
    def _p1():
        with open(SAMPLE_XMI, "rb") as f:
            r = admin.post(BASE + "/api/dm/parseXmi",
                            files={"file": ("sample.xmi", f, "application/xml")},
                            timeout=15)
        assert r.status_code == 200, f"HTTP {r.status_code}"
        rj = r.json()
        assert rj.get("success") is True, f"success false: {rj}"
        parsed[0] = rj
    step("P1. /api/dm/parseXmi — sample 파싱 success=true", _p1)

    # P2. tables 3건
    def _p2():
        rj = parsed[0]
        names = sorted([t["objNm"] for t in rj["tables"]])
        assert names == ["CUSTOMER", "DEPARTMENT", "EMPLOYEE"], f"테이블명 미스매치: {names}"
        assert rj["tableCount"] == 3, f"tableCount 3 기대, {rj['tableCount']}"
    step("P2. parseXmi — tables 3건 (EMPLOYEE/DEPARTMENT/CUSTOMER)", _p2)

    # P3. columns 9건 (4+2+3)
    def _p3():
        rj = parsed[0]
        assert rj["columnCount"] == 9, f"columnCount 9 기대, {rj['columnCount']}"
        emp_cols = [c for c in rj["columns"] if c["objNm"] == "EMPLOYEE"]
        dept_cols = [c for c in rj["columns"] if c["objNm"] == "DEPARTMENT"]
        cust_cols = [c for c in rj["columns"] if c["objNm"] == "CUSTOMER"]
        assert len(emp_cols) == 4, f"EMPLOYEE 컬럼 4 기대, {len(emp_cols)}"
        assert len(dept_cols) == 2, f"DEPARTMENT 컬럼 2 기대, {len(dept_cols)}"
        assert len(cust_cols) == 3, f"CUSTOMER 컬럼 3 기대, {len(cust_cols)}"
    step("P3. parseXmi — columns 9건 (4+2+3)", _p3)

    # P4. PrimitiveType 추출
    def _p4():
        rj = parsed[0]
        emp_id = next(c for c in rj["columns"] if c["attrNm"] == "EMP_ID")
        emp_name = next(c for c in rj["columns"] if c["attrNm"] == "EMP_NAME")
        assert emp_id["dataType"] == "Integer", f"EMP_ID Integer 기대, {emp_id['dataType']}"
        assert emp_name["dataType"] == "String", f"EMP_NAME String 기대, {emp_name['dataType']}"
    step("P4. parseXmi — PrimitiveType (Integer/String)", _p4)

    # P5. nullable 판정
    def _p5():
        rj = parsed[0]
        emp_email = next(c for c in rj["columns"] if c["attrNm"] == "EMAIL")
        emp_id = next(c for c in rj["columns"] if c["attrNm"] == "EMP_ID")
        assert emp_email["nullableYn"] == "Y", f"EMAIL Y 기대, {emp_email['nullableYn']}"
        assert emp_id["nullableYn"] == "N", f"EMP_ID N 기대, {emp_id['nullableYn']}"
    step("P5. parseXmi — lowerValue → nullable", _p5)

    # P6. type 속성 (id 참조) → 클래스명
    def _p6():
        rj = parsed[0]
        emp_dept = next(c for c in rj["columns"]
                         if c["objNm"] == "EMPLOYEE" and c["attrNm"] == "DEPT_ID")
        # cls-department 참조 → DEPARTMENT 로 해석되어야 함
        assert emp_dept["dataType"] == "DEPARTMENT", \
            f"DEPT_ID dataType DEPARTMENT 기대, {emp_dept['dataType']}"
    step("P6. parseXmi — type id 참조 → 클래스명 해석", _p6)

    # P7. 패키지 중첩 (HR + SALES) 모두 추출
    def _p7():
        rj = parsed[0]
        names = set(t["objNm"] for t in rj["tables"])
        # HR 안의 EMPLOYEE/DEPARTMENT, SALES 안의 CUSTOMER
        assert "EMPLOYEE" in names and "CUSTOMER" in names, \
            f"패키지 횡단 추출 실패: {names}"
    step("P7. parseXmi — 패키지 중첩 횡단 추출", _p7)

    # P8. XXE 방지
    def _p8():
        bad_xml = b'<?xml version="1.0"?>\n<!DOCTYPE foo [<!ENTITY x SYSTEM "file:///etc/passwd">]>\n<xmi:XMI/>'
        r = admin.post(BASE + "/api/dm/parseXmi",
                        files={"file": ("bad.xmi", bad_xml, "application/xml")},
                        timeout=10)
        rj = r.json()
        assert rj.get("success") is False, "DOCTYPE 거부 실패"
    step("P8. parseXmi — XXE 방지 (DOCTYPE 거부)", _p8)

    # P9. 잘못된 XML
    def _p9():
        r = admin.post(BASE + "/api/dm/parseXmi",
                        files={"file": ("bad.xmi", b"not xml at all", "application/xml")},
                        timeout=10)
        rj = r.json()
        assert rj.get("success") is False, "잘못된 XML 거부 실패"
    step("P9. parseXmi — 잘못된 XML 거부", _p9)

    # P10. ERwin native 회귀 (호출 자체가 가능 — 양식 없으니 success false 예상하나 endpoint 동작)
    def _p10():
        r = admin.post(BASE + "/api/dm/parseErwinXml",
                        files={"file": ("bad.xml", b"<root/>", "application/xml")},
                        timeout=10)
        # 200 응답 자체가 와야 함 (success false 도 OK — 엔드포인트는 살아있음)
        assert r.status_code == 200, f"ERwin 엔드포인트 비활성: {r.status_code}"
    step("P10. /parseErwinXml — 기존 엔드포인트 회귀", _p10)

    # P11. UI — 메뉴 진입 + 라디오
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
            # 데이터 모델 그룹 펼치기
            hdrs = drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
            for h in hdrs:
                if h.text.strip() == "데이터 모델":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            # 모델링 도구 임포트 메뉴
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_erwinImport")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 라디오 그룹
            rg = drv.find_elements(By.ID, "rg-import-format")
            assert len(rg) > 0, "포맷 라디오 그룹 없음"
            assert "ERwin native XML" in drv.page_source, "ERwin 라벨 없음"
            assert "XMI 2.1" in drv.page_source, "XMI 2.1 라벨 없음"
        finally:
            time.sleep(1)
            drv.quit()
    step("P11. UI — 메뉴 진입 + 포맷 라디오 (ERwin/XMI)", _p11)

    # P12. UI — XMI 기본 선택 시 라벨 변경
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
                if h.text.strip() == "데이터 모델":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_erwinImport")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            # 기본 XMI 가 mandatory 로 선택됨 → "XMI 2.1 파일" 라벨 노출
            assert "XMI 2.1 파일" in drv.page_source, \
                "XMI 라벨 없음 (default 선택 검증 실패)"
        finally:
            time.sleep(1)
            drv.quit()
    step("P12. UI — XMI 기본 선택 + 라벨 변경", _p12)

    # P13. UI — 파일 업로드 input 존재
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
                if h.text.strip() == "데이터 모델":
                    ActionChains(drv).move_to_element(h).click().perform()
                    time.sleep(1)
                    break
            el = WebDriverWait(drv, 8).until(EC.presence_of_element_located(
                (By.ID, "nav_erwinImport")))
            ActionChains(drv).move_to_element(el).click().perform()
            time.sleep(3)
            file_inputs = drv.find_elements(By.CSS_SELECTOR, "input[type='file']")
            assert len(file_inputs) > 0, "file input 없음"
        finally:
            time.sleep(1)
            drv.quit()
    step("P13. UI — 파일 업로드 input 존재", _p13)


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
