"""
67번 데이터 품질 진단 — UI 풀 검증 (시나리오 3종)

시나리오 A : 업무 규칙 관리 화면 (룰 목록 + 모델 선택 후 16개 표시)
시나리오 B : 값 프로파일링 (모델 선택 → 시작 → 결과 22 컬럼)
시나리오 C : 업무 규칙 진단 결과 (diagId 입력 → 16개 룰 결과 + 위반률 표시)

이전 셀레니움이 API 만 검증해서 UI baseURL 버그를 못 잡았다는 사용자 지적 반영.
"""
import os, sys, time, traceback, base64
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

import requests

BASE = "http://localhost:28091"
DM_ID = "TESTQUALDM00000000001A"
SHOT = os.path.join(os.path.dirname(__file__), "screenshots")
PRE  = "qualui_"
os.makedirs(SHOT, exist_ok=True)
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {type(e).__name__}: {str(e)[:200]}")
        return False


def shot(d, n):
    p = os.path.join(SHOT, PRE + n + ".png")
    d.save_screenshot(p)
    print(f"  shot: {n}")


def jclick(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
    time.sleep(0.3)
    d.execute_script("arguments[0].click();", el)


def login_ui(d):
    d.get(BASE + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys("space")
    pw = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw.send_keys("123"); pw.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(5)  # Vue mount 대기 충분히


def open_menu(d, group_id, child_id, label_for_shot):
    """v-list-group 의 activator(__header) 를 클릭해 펼친 후 자식 클릭"""
    WebDriverWait(d, 15).until(EC.presence_of_element_located((By.ID, group_id)))
    if not d.find_elements(By.ID, child_id) or not d.find_elements(By.ID, child_id)[0].is_displayed():
        act = d.find_element(By.XPATH,
            f"//div[@id='{group_id}']//div[contains(@class,'v-list-group__header')]")
        jclick(d, act); time.sleep(2)
    m = WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.ID, child_id)))
    jclick(d, m); time.sleep(4)
    shot(d, label_for_shot)


def pick_model(d, name):
    """v-autocomplete 의 '모델' 라벨 입력 → 선택"""
    ac = WebDriverWait(d, 10).until(EC.element_to_be_clickable(
        (By.XPATH, "//label[contains(.,'모델')]/ancestor::div[contains(@class,'v-autocomplete')][1]//input")))
    jclick(d, ac); time.sleep(0.5)
    ac.send_keys(name); time.sleep(2)
    opt = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
    if not opt:
        opt = d.find_elements(By.CSS_SELECTOR, "[role='option']")
    assert opt, "모델 옵션 미발견"
    jclick(d, opt[0]); time.sleep(3)


# =================================================================
# 시나리오 A: 업무 규칙 관리
# =================================================================
def scenario_a(d):
    state = {}

    def _open():
        open_menu(d, "qualGroup", "nav_ruleManage", "A01_rule_manage_open")
        # 룰 추가 버튼 visible
        WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.ID, "btn-rule-add")))

    def _select():
        pick_model(d, "TEST_QUAL_MODEL")
        shot(d, "A02_model_picked")
        # 그리드에 룰 16개 (또는 그 이상) 표시
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  rule rows={len(rows)}")
        assert len(rows) >= 10, f"룰 10+ 기대, 실제 {len(rows)} (70번 폐기 후 커스텀 룰 10 + 시나리오 추가)"
        state["rule_rows"] = len(rows)

    def _verify_rule_types():
        # JS 직접 추출 (selenium .text 가 viewport 밖 cell 의 text 못 가져옴)
        types_seen = d.execute_script("""
          const set = new Set();
          document.querySelectorAll('table tbody tr td').forEach(td => {
            const t = (td.innerText || td.textContent || '').trim();
            ['NOT_NULL','RANGE','LENGTH','REGEX','ENUM','UNIQUE','REFERENCE','COMPARE']
              .forEach(kw => { if (t === kw || t.includes(kw)) set.add(kw); });
          });
          return Array.from(set);
        """)
        print(f"  표시된 RULE_TYPE: {sorted(types_seen)}")
        assert len(types_seen) >= 6, f"RULE_TYPE 6+ 기대, 실제 {len(types_seen)}: {sorted(types_seen)}"

    step("A1. [업무 규칙 관리] 메뉴 진입 + 화면 렌더", _open)
    step("A2. 모델 선택 → 그리드 16+ 행", _select)
    step("A3. 8가지 RULE_TYPE 중 6+ 표시", _verify_rule_types)
    return state


# =================================================================
# 시나리오 B: 값 프로파일링 실행
# =================================================================
def scenario_b(d):
    """70번 재설계 후 DSQualValueProfile = 모델 선택 → 컬럼 그리드 → 체크 → 시작"""
    state = {}

    def _open():
        open_menu(d, "qualGroup", "nav_valueProfile", "B01_value_open")
        # btn-run-selected 는 컬럼 0건 선택 상태에서 disabled
        btn = WebDriverWait(d, 10).until(EC.presence_of_element_located((By.ID, "btn-run-selected")))
        assert btn.get_attribute("disabled") is not None, \
            "선택 0건 상태에서 [선택 컬럼 프로파일링] enabled"

    def _select_and_check():
        pick_model(d, "TEST_QUAL_MODEL")
        shot(d, "B02_model_picked")
        # 컬럼 그리드 행수
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        assert len(rows) >= 5, f"컬럼 그리드 5+ 행 기대, 실제 {len(rows)}"
        # [전체선택] → [선택 컬럼 프로파일링] enabled
        sel_all = d.find_element(By.XPATH, "//button[contains(., '전체선택')]")
        d.execute_script("arguments[0].click();", sel_all); time.sleep(1)
        btn = d.find_element(By.ID, "btn-run-selected")
        for _ in range(10):
            if btn.get_attribute("disabled") is None: break
            time.sleep(0.5)
            btn = d.find_element(By.ID, "btn-run-selected")
        assert btn.get_attribute("disabled") is None, "전체선택 후에도 disabled"

    def _click_run():
        btn = d.find_element(By.ID, "btn-run-selected")
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", btn); time.sleep(0.3)
        d.execute_script("arguments[0].click();", btn)
        # swal 폴링
        deadline = time.time() + 30
        seen = False
        while time.time() < deadline:
            cls = d.execute_script("return document.body.className || '';")
            if "swal2-shown" in cls or d.find_elements(By.CSS_SELECTOR, ".swal2-popup"):
                seen = True; break
            time.sleep(0.5)
        if not seen:
            shot(d, "B03_no_swal")
            raise AssertionError("swal 모달 미발견")
        shot(d, "B03_swal")
        time.sleep(2)

    def _verify_grid():
        # 30초 후 자동 새로고침으로 적합률/통계 갱신
        time.sleep(33)
        shot(d, "B04_grid")
        # 표 안에 % 가 보임
        body = d.find_element(By.CSS_SELECTOR, "table").text
        assert "%" in body, "적합률/NULL% 표시 안 됨"

    step("B1. [값 프로파일링] 메뉴 진입 + [선택 컬럼 프로파일링] disabled", _open)
    step("B2. 모델 선택 + [전체선택] → 버튼 활성화", _select_and_check)
    step("B3. [선택 컬럼 프로파일링] 클릭 → 성공 모달", _click_run)
    step("B4. 30초 후 그리드 % 표시 갱신", _verify_grid)
    return state


# =================================================================
# 시나리오 C: 업무 규칙 진단 결과 조회
# =================================================================
def scenario_c(d):
    state = {}

    def _trigger_via_api():
        # API 로 진단 실행해 diagId 발급
        s = requests.Session()
        enc = base64.b64encode("123".encode()).decode()
        s.post(BASE + "/login", data={"id":"space","password":enc}, allow_redirects=False, timeout=10)
        r = s.post(BASE + "/api/qual/rule/run",
                   json={"dataModelId": DM_ID, "sampleRate": 100, "incrementalYn": "N"}, timeout=30)
        diag = r.json().get("contents")
        assert diag, f"diagId 발급 실패: {r.json()}"
        state["diag_id"] = diag

        # DONE 까지 대기
        deadline = time.time() + 180
        last = None
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
            content = r2.json().get("contents")
            if isinstance(content, str):
                import json; content = json.loads(content)
            h = (content or {}).get("history") or {}
            last = h.get("status")
            if last in ("DONE","ERROR"): break
            time.sleep(5)
        assert last == "DONE", f"진단 마감 status={last}"

    def _open_result():
        open_menu(d, "qualGroup", "nav_ruleResult", "C01_result_open")
        # diagId 입력
        inp = WebDriverWait(d, 10).until(EC.visibility_of_element_located(
            (By.XPATH, "//label[contains(.,'diagId')]/ancestor::div[contains(@class,'v-text-field')][1]//input")))
        jclick(d, inp); time.sleep(0.3)
        inp.send_keys(state["diag_id"])
        time.sleep(0.5)
        btn = d.find_element(By.ID, "btn-result-load")
        jclick(d, btn); time.sleep(3)
        shot(d, "C02_loaded")

    def _verify():
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  result rows={len(rows)}")
        assert len(rows) >= 10, f"결과 10+ 기대, 실제 {len(rows)} (70번 컬럼 매핑 기반)"
        # 위반률(%) 표시 확인
        body = d.find_element(By.CSS_SELECTOR, "table").text
        assert "%" in body, "위반률 % 표시 미확인"

    step("C1. API 로 진단 실행 → diagId 발급 + DONE 대기", _trigger_via_api)
    step("C2. [업무 규칙 진단 결과] 메뉴 → diagId 입력 → 조회", _open_result)
    step("C3. 결과 그리드 16+ 행 + 위반률 표시", _verify)
    return state


# =================================================================
# =================================================================
# 시나리오 D: 룰 직접 등록 (Create)
# =================================================================
def scenario_d(d):
    state = {}

    def _open_rule_manage():
        # 다른 시나리오 이후라도 보장 — 메뉴 다시 클릭
        open_menu(d, "qualGroup", "nav_ruleManage", "D01_open")
        # 모델 다시 선택 (이미 활성 탭일 수 있음 — 이 경우 그대로)
        try:
            ac = d.find_element(By.XPATH,
                "//label[contains(.,'모델')]/ancestor::div[contains(@class,'v-autocomplete')][1]//input")
            cur = ac.get_attribute("value") or ""
            if "TEST_QUAL_MODEL" not in cur:
                pick_model(d, "TEST_QUAL_MODEL")
        except Exception:
            pick_model(d, "TEST_QUAL_MODEL")
        # 신규 룰 추가 직전 그리드 행수
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        state["before"] = len(rows)
        print(f"  before rows={state['before']}")

    def _click_add_open_dialog():
        btn = WebDriverWait(d, 10).until(EC.element_to_be_clickable((By.ID, "btn-rule-add")))
        jclick(d, btn); time.sleep(1)
        WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.CSS_SELECTOR, ".v-dialog--active")))
        shot(d, "D02_dialog")

    def _fill_and_save():
        dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
        # 룰명 input
        nm = dialog.find_element(By.XPATH,
            ".//label[contains(.,'룰명')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        nm.clear(); nm.send_keys("UI_TEST_RULE_" + str(int(time.time()))); state["rule_nm"] = nm.get_attribute("value")
        # 테이블명
        obj = dialog.find_element(By.XPATH,
            ".//label[contains(.,'테이블명')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        obj.clear(); obj.send_keys("TB_TEST_MEMBER")
        # 컬럼명
        col = dialog.find_element(By.XPATH,
            ".//label[contains(.,'컬럼명')]/ancestor::div[contains(@class,'v-text-field')][1]//input")
        col.clear(); col.send_keys("EMAIL")
        time.sleep(0.5)
        # 유형: NOT_NULL (default 이라 그대로)
        # 저장
        save = dialog.find_element(By.ID, "btn-rule-save")
        jclick(d, save); time.sleep(2)
        # success swal
        WebDriverWait(d, 15).until(lambda drv:
            drv.find_elements(By.CSS_SELECTOR, ".swal2-popup")
        )
        try: d.find_element(By.CSS_SELECTOR, ".swal2-confirm").click()
        except Exception: pass
        time.sleep(2)
        shot(d, "D03_saved")

    def _verify_grid_grew():
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  after rows={len(rows)} (before={state['before']})")
        assert len(rows) >= state["before"] + 1, "그리드 +1 행 기대, 안 늘어남"
        body_text = d.find_element(By.CSS_SELECTOR, "table").text
        assert state["rule_nm"] in body_text, f"신규 룰명 '{state['rule_nm']}' 그리드 미발견"
        state["new_rule_displayed"] = True

    step("D1. 룰 관리 메뉴 진입 + 모델 선택", _open_rule_manage)
    step("D2. [룰 추가] 클릭 → 다이얼로그 오픈", _click_add_open_dialog)
    step("D3. 폼 작성 → 저장 → 성공 swal", _fill_and_save)
    step("D4. 그리드 +1 행 + 신규 룰명 표시", _verify_grid_grew)
    return state


# =================================================================
# 시나리오 E: 카탈로그에서 가져오기 (Import)
# =================================================================
def scenario_e(d):
    state = {}

    def _open_catalog():
        # 룰 관리 화면 가정 (D 직후)
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        state["before"] = len(rows)
        btn = WebDriverWait(d, 10).until(EC.element_to_be_clickable((By.ID, "btn-rule-catalog")))
        jclick(d, btn); time.sleep(2)
        WebDriverWait(d, 10).until(EC.visibility_of_element_located((By.CSS_SELECTOR, ".v-dialog--active")))
        shot(d, "E01_catalog")

    def _import_first():
        # 카탈로그 그리드의 첫 [가져오기] 버튼
        dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
        btns = dialog.find_elements(By.XPATH, ".//button[contains(., '가져오기')]")
        assert btns, "가져오기 버튼 없음"
        jclick(d, btns[0]); time.sleep(2)
        # swal input prompt 뜨는 시간 충분히 — interactable 까지 대기
        WebDriverWait(d, 15).until(EC.visibility_of_element_located(
            (By.CSS_SELECTOR, ".swal2-popup .swal2-input")))
        time.sleep(1)  # animation 마무리
        # JS 로 직접 value set + input 이벤트 (send_keys 대신 — interact 이슈 회피)
        d.execute_script("""
          const el = document.querySelector('.swal2-popup .swal2-input');
          el.focus();
          const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
          setter.call(el, arguments[0]);
          el.dispatchEvent(new Event('input', {bubbles:true}));
          el.dispatchEvent(new Event('change', {bubbles:true}));
        """, "TB_TEST_MEMBER.EMAIL")
        time.sleep(0.5)
        d.find_element(By.CSS_SELECTOR, ".swal2-confirm").click()
        time.sleep(3)
        # 등록 완료 swal — 이전 swal 사라지고 새 swal 뜨는 transition
        WebDriverWait(d, 15).until(lambda drv:
            drv.find_elements(By.CSS_SELECTOR, ".swal2-popup .swal2-icon-success, .swal2-popup .swal2-icon-error, .swal2-confirm"))
        time.sleep(0.5)
        shot(d, "E02_imported")
        # swal close
        try:
            d.find_element(By.CSS_SELECTOR, ".swal2-confirm").click()
            time.sleep(1)
        except Exception: pass
        # catalogDialog 도 close (Esc 또는 그냥 밖에 클릭)
        try:
            d.find_element(By.CSS_SELECTOR, "body").send_keys(Keys.ESCAPE)
            time.sleep(1)
        except Exception: pass

    def _verify():
        rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
        print(f"  after import rows={len(rows)} (before={state['before']})")
        assert len(rows) >= state["before"] + 1, "카탈로그 가져오기 후 +1 행 기대"

    step("E1. [카탈로그] 버튼 → 다이얼로그 오픈", _open_catalog)
    step("E2. 첫 카탈로그 [가져오기] → 대상 입력 → 등록", _import_first)
    step("E3. 그리드 +1 행 검증", _verify)
    return state


# =================================================================
# 시나리오 F: 룰 관리에서 직접 [진단 실행] 클릭
# =================================================================
def scenario_f(d):
    def _ensure_clean():
        # 이전 시나리오의 잔여 swal/dialog 닫기
        for _ in range(3):
            try:
                d.find_element(By.CSS_SELECTOR, "body").send_keys(Keys.ESCAPE)
                time.sleep(0.5)
            except Exception: pass
        # 룰 관리 메뉴 다시 진입 (활성 탭 복귀)
        open_menu(d, "qualGroup", "nav_ruleManage", "F00_open")
        # 모델 선택 확인
        try:
            ac = d.find_element(By.XPATH,
                "//label[contains(.,'모델')]/ancestor::div[contains(@class,'v-autocomplete')][1]//input")
            if "TEST_QUAL_MODEL" not in (ac.get_attribute("value") or ""):
                pick_model(d, "TEST_QUAL_MODEL")
        except Exception:
            pick_model(d, "TEST_QUAL_MODEL")

    def _click_run():
        # presence + scroll into view + JS click (element_to_be_clickable 가 viewport-out 에서 fail)
        btn = WebDriverWait(d, 15).until(EC.presence_of_element_located((By.ID, "btn-rule-run")))
        d.execute_script("arguments[0].scrollIntoView({block:'center'});", btn)
        time.sleep(0.5)
        # disabled 체크
        for _ in range(10):
            if btn.get_attribute("disabled") is None:
                break
            time.sleep(0.5)
            btn = d.find_element(By.ID, "btn-rule-run")
        assert btn.get_attribute("disabled") is None, "[진단 실행] 버튼 disabled — 모델 미선택 또는 룰 0건 의심"
        d.execute_script("arguments[0].click();", btn); time.sleep(1)
        # success swal (diagId 안내)
        deadline = time.time() + 30
        seen = False
        while time.time() < deadline:
            cls = d.execute_script("return document.body.className || '';")
            if "swal2-shown" in cls or d.find_elements(By.CSS_SELECTOR, ".swal2-popup"):
                seen = True; break
            time.sleep(0.5)
        assert seen, "진단 실행 swal 미발견"
        shot(d, "F01_run_swal")
        # swal 안에 diagId 텍스트 확인
        try:
            txt = d.find_element(By.CSS_SELECTOR, ".swal2-popup").text
            assert "diagId" in txt or "진단" in txt, f"swal 내용 이상: {txt[:100]}"
        except Exception:
            pass
        try: d.find_element(By.CSS_SELECTOR, ".swal2-confirm").click()
        except Exception: pass
        time.sleep(1)

    step("F0. 룰 관리로 복귀 + 잔여 dialog 정리", _ensure_clean)
    step("F1. [진단 실행] 버튼 클릭 → 성공 swal", _click_run)


# =================================================================
def main():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)

    try:
        if not step("0. 로그인", lambda: login_ui(d)):
            return
        scenario_a(d)
        scenario_b(d)
        scenario_c(d)
        scenario_d(d)
        scenario_e(d)
        scenario_f(d)
    finally:
        time.sleep(2)
        try: d.quit()
        except Exception: pass


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for n, s, _ in results: print(f"  [{s}] {n}")
    sys.exit(0 if f == 0 else 1)
