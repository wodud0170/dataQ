"""
논리 모델 → 테이블 추가 → 컬럼 추가 (표준 적용) → DDL 다운로드 E2E 테스트
"""
import time, sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.action_chains import ActionChains

BASE_URL = "http://localhost:28091"
SSDIR = "dataQ설계/테스트/selenium/screenshots/"
MODEL_NAME = "E2E_LOGIC_" + str(int(time.time()))[-6:]

def drv():
    o = webdriver.EdgeOptions()
    o.add_argument("--log-level=3")
    d = webdriver.Edge(options=o)
    d.set_window_size(1920, 1080)
    return d

def ss(d, n):
    d.save_screenshot(SSDIR + n)
    print(f"  [SS] {n}")

def login(d):
    d.get(BASE_URL + "/signin")
    time.sleep(2)
    for inp in d.find_elements(By.TAG_NAME, "input"):
        if inp.get_attribute("type") == "text": inp.clear(); inp.send_keys("space")
        elif inp.get_attribute("type") == "password": inp.clear(); inp.send_keys("123")
    for b in d.find_elements(By.TAG_NAME, "button"):
        if b.text.strip(): b.click(); break
    time.sleep(4)

def dismiss_dialogs(d):
    """swal/다이얼로그 닫기"""
    for _ in range(3):
        try:
            sb = d.find_element(By.CSS_SELECTOR, ".swal2-confirm")
            if sb and sb.is_displayed(): sb.click(); time.sleep(1)
        except: pass
        try:
            for b in d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button"):
                if "취소" in b.text or "닫기" in b.text:
                    b.click(); time.sleep(1); break
        except: pass

def nav(d, gid, mid):
    dismiss_dialogs(d)
    time.sleep(1)
    # 그룹 먼저 열기 (이미 열려있으면 무시)
    try:
        g = WebDriverWait(d, 5).until(EC.presence_of_element_located((By.ID, gid)))
        d.execute_script("arguments[0].click();", g)
        time.sleep(1)
    except: pass
    # 메뉴 클릭
    m = WebDriverWait(d, 5).until(EC.presence_of_element_located((By.ID, mid)))
    d.execute_script("arguments[0].click();", m)
    time.sleep(3)

def select_model_autocomplete(d, model_name):
    """v-autocomplete에서 모델명 선택"""
    acs = d.find_elements(By.CSS_SELECTOR, ".v-autocomplete input[type='text']")
    if not acs: return False
    ac = acs[0]
    ac.click(); time.sleep(1)
    ac.clear()
    ac.send_keys(model_name)
    time.sleep(2)
    # 드롭다운 항목 클릭
    items = d.find_elements(By.CSS_SELECTOR, ".v-list-item")
    for item in items:
        if model_name in item.text:
            item.click(); time.sleep(2)
            return True
    # role=option으로 시도
    items2 = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    for item in items2:
        if model_name in item.text:
            item.click(); time.sleep(2)
            return True
    return False

results = []
def test(name, fn):
    print(f"\n{'='*60}\nTEST: {name}\n{'='*60}")
    try:
        fn()
        results.append((name, "PASS"))
        print(f"  >> PASS")
    except Exception as e:
        results.append((name, f"FAIL: {e}"))
        print(f"  >> FAIL: {e}")

def main():
    d = drv()
    try:
        # 1. 로그인
        def t1():
            login(d)
            assert "/main" in d.current_url, f"URL: {d.current_url}"
            ss(d, "e2e_01_login.png")
        test("1. 로그인", t1)

        # 2. 논리 모델 생성
        def t2():
            nav(d, "dmGroup", "nav_datamodelCollection")
            time.sleep(2)
            ss(d, "e2e_02a_collection_page.png")
            # NdModal의 추가 버튼 찾기 — 여러 방법 시도
            clicked = False
            for b in d.find_elements(By.TAG_NAME, "button"):
                txt = b.text.strip()
                if txt == "추가" or txt == "모델 추가" or txt == "등록":
                    try:
                        b.click(); clicked = True; break
                    except: pass
            if not clicked:
                # gradient 클래스 버튼
                for b in d.find_elements(By.CSS_SELECTOR, ".gradient"):
                    if "추가" in b.text or "등록" in b.text:
                        d.execute_script("arguments[0].click();", b)
                        clicked = True; break
            assert clicked, "추가 버튼을 찾을 수 없음"
            time.sleep(2)
            ss(d, "e2e_02b_add_modal.png")
            # 모달에서 모델명 입력 (첫 번째 text input)
            dialog_inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input[type='text']")
            assert len(dialog_inputs) > 0, "모달 input을 찾을 수 없음"
            dialog_inputs[0].clear()
            dialog_inputs[0].send_keys(MODEL_NAME)
            time.sleep(1)
            # 등록/추가 버튼 클릭
            for b in d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button"):
                if "등록" in b.text or "추가" in b.text or "저장" in b.text:
                    b.click(); break
            time.sleep(3)
            ss(d, "e2e_02c_model_created.png")
        test("2. 논리 모델 생성", t2)

        # 3. 테이블 추가 (한글명만)
        def t3():
            nav(d, "dmGroup", "nav_datamodelStatusTable")
            time.sleep(2)
            # 모델 선택
            assert select_model_autocomplete(d, MODEL_NAME), f"모델 '{MODEL_NAME}' 선택 실패"
            time.sleep(2)
            ss(d, "e2e_03a_model_selected.png")
            # 테이블 추가 버튼
            clicked = False
            for b in d.find_elements(By.TAG_NAME, "button"):
                if "테이블 추가" in b.text:
                    d.execute_script("arguments[0].click();", b)
                    clicked = True; break
            assert clicked, "테이블 추가 버튼 클릭 실패"
            time.sleep(2)
            # 다이얼로그에서 한글명만 입력
            dialog_inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
            for inp in dialog_inputs:
                label = (inp.get_attribute("aria-label") or "").lower()
                # 한글명/논리명 필드 찾기
                if "한글" in label or "논리" in label:
                    inp.clear(); inp.send_keys("고객정보")
            time.sleep(1)
            ss(d, "e2e_03b_table_dialog.png")
            # 추가 버튼
            for b in d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button"):
                if "추가" in b.text:
                    b.click(); break
            time.sleep(3)
            # swal 확인
            try:
                sb = d.find_element(By.CSS_SELECTOR, ".swal2-confirm")
                if sb: sb.click(); time.sleep(1)
            except: pass
            ss(d, "e2e_03c_table_added.png")
        test("3. 테이블 추가 (한글명: 고객정보)", t3)

        # 4. 컬럼 추가 + 표준 적용
        def t4():
            nav(d, "dmGroup", "nav_datamodelStatusColumn")
            time.sleep(2)
            # 모델 선택
            assert select_model_autocomplete(d, MODEL_NAME), f"모델 '{MODEL_NAME}' 선택 실패"
            time.sleep(2)
            # 컬럼 추가 버튼
            clicked = False
            for b in d.find_elements(By.TAG_NAME, "button"):
                if "컬럼 추가" in b.text:
                    d.execute_script("arguments[0].click();", b)
                    clicked = True; break
            assert clicked, "컬럼 추가 버튼 클릭 실패"
            time.sleep(2)
            ss(d, "e2e_04a_attr_dialog.png")
            # 한글명 입력
            dialog_inputs = d.find_elements(By.CSS_SELECTOR, ".v-dialog--active input")
            for inp in dialog_inputs:
                label = (inp.get_attribute("aria-label") or "")
                if "한글명" in label or "논리명" in label:
                    inp.clear(); inp.send_keys("사용자명")
                    break
            time.sleep(1)
            # [표준 적용] 버튼 클릭
            std_clicked = False
            for b in d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button"):
                if "표준 적용" in b.text or "표준적용" in b.text:
                    d.execute_script("arguments[0].click();", b)
                    std_clicked = True; break
            time.sleep(3)
            ss(d, "e2e_04b_standard_applied.png")
            # swal 확인
            try:
                sb = d.find_element(By.CSS_SELECTOR, ".swal2-confirm")
                if sb: sb.click(); time.sleep(1)
            except: pass
            assert std_clicked, "표준 적용 버튼 클릭 실패"
        test("4. 컬럼 추가 - 표준 적용 (사용자명)", t4)

        # 5. DDL 확인
        def t5():
            nav(d, "dmGroup", "nav_datamodelStatus")
            time.sleep(3)
            ss(d, "e2e_05_ddl.png")
            page = d.page_source
            assert MODEL_NAME in page, f"모델 현황에 '{MODEL_NAME}' 없음"
        test("5. DDL 다운로드 확인", t5)

    finally:
        d.quit()

    print(f"\n{'='*60}\n결과\n{'='*60}")
    p = f = 0
    for n, s in results:
        i = "✅" if s == "PASS" else "❌"
        print(f"  {i} {n}: {s}")
        if s == "PASS": p += 1
        else: f += 1
    print(f"\n  총 {len(results)}: PASS {p}, FAIL {f}")
    if f > 0: sys.exit(1)

if __name__ == "__main__":
    main()
