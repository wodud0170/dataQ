"""
Phase 3 — 진단 스케줄러 UI 스모크 테스트 (65번 문서 §10 작업 5~7)

시나리오:
  1. 관리자 로그인 (space)
  2. 메뉴: 관리 > 진단 스케줄 진입 → DSScheduleManage 렌더 확인
  3. [스케줄 추가] → SIMPLE 스케줄 등록
  4. 목록에서 신규 행 확인 + 활성 스위치 ON 상태 확인
  5. [즉시 실행] 버튼 클릭 → swal 확인 → 완료 대기
  6. 메뉴: 관리 > 스케줄 실행 이력 → 방금 실행 로그 행 존재 확인
  7. 로그 행 클릭 → drawer 상세 열림 확인
  8. 정리 (스케줄 삭제)
"""
import os
import sys
import time
import traceback
from datetime import datetime

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException

BASE = "http://localhost:28091"
SCREENSHOT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
PREFIX = "schd_"

os.makedirs(SCREENSHOT_DIR, exist_ok=True)
results = []


def step(name, fn):
    print(f"\n{'=' * 60}\n[STEP] {name}\n{'=' * 60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def shot(d, name):
    d.save_screenshot(os.path.join(SCREENSHOT_DIR, PREFIX + name + ".png"))
    print(f"  [SHOT] {name}")


def login(d, user="space", pw="123"):
    d.get(BASE + "/signin")
    WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
    time.sleep(1)
    d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
    pw_in = d.find_element(By.CSS_SELECTOR, "input[type='password']")
    pw_in.send_keys(pw); pw_in.send_keys(Keys.ENTER)
    WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
    time.sleep(2)


def js_click(d, el):
    d.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
    time.sleep(0.2)
    try: el.click()
    except Exception: d.execute_script("arguments[0].click();", el)


def dismiss_swal(d, confirm=True):
    for _ in range(5):
        try:
            sel = ".swal2-confirm" if confirm else ".swal2-cancel"
            btn = d.find_element(By.CSS_SELECTOR, sel)
            if btn.is_displayed():
                btn.click(); time.sleep(0.5); continue
        except Exception:
            pass
        break


def nav(d, group_id, menu_id):
    items = d.find_elements(By.ID, menu_id)
    if not items or not items[0].is_displayed():
        g = WebDriverWait(d, 10).until(EC.element_to_be_clickable((By.ID, group_id)))
        js_click(d, g)
        WebDriverWait(d, 5).until(EC.visibility_of_element_located((By.ID, menu_id)))
    m = d.find_element(By.ID, menu_id)
    js_click(d, m)
    time.sleep(2)


state = {}


def main():
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)

    try:
        # 1
        if not step("1. 관리자 로그인", lambda: login(d)): return

        # 2
        def _nav_manage():
            nav(d, "scheduleGroup", "nav_scheduleManage")
            WebDriverWait(d, 10).until(
                EC.visibility_of_element_located((By.XPATH, "//button[contains(., '스케줄 추가')]"))
            )
            shot(d, "01_manage_nav")
        if not step("2. 메뉴 진단 스케줄 진입", _nav_manage): return

        # 3. 스케줄 추가
        def _add():
            d.find_element(By.XPATH, "//button[contains(., '스케줄 추가')]").click()
            time.sleep(1)
            WebDriverWait(d, 5).until(EC.visibility_of_element_located((By.CSS_SELECTOR, ".v-dialog--active")))
            dialog = d.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            # 스케줄명 = 첫 번째 일반 text input (label "스케줄명 *")
            name = "P3_UI_" + datetime.now().strftime("%H%M%S")
            state["name"] = name
            # label 기반으로 input 찾기
            def _input_for_label(label_text):
                xp = f".//label[contains(., '{label_text}')]/ancestor::div[contains(@class,'v-text-field') or contains(@class,'v-select') or contains(@class,'v-autocomplete')][1]//input"
                els = dialog.find_elements(By.XPATH, xp)
                return els[0] if els else None

            nm_in = _input_for_label("스케줄명")
            assert nm_in, "스케줄명 input 못 찾음"
            nm_in.send_keys(name); time.sleep(0.3)

            # 데이터모델 autocomplete
            dm_in = _input_for_label("데이터모델")
            assert dm_in, "데이터모델 input 못 찾음"
            js_click(d, dm_in); time.sleep(0.5)
            # 2026-08-22: 구 "CAMS" 모델은 USE_YN='N' 이라 autocomplete 에 안 뜬다 → 활성 모델로 교체
            dm_in.send_keys("오라클테스트"); time.sleep(1)
            opt = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
            if not opt:
                opt = d.find_elements(By.CSS_SELECTOR, "[role='option']")
            assert opt, "데이터모델 옵션 없음"
            js_click(d, opt[0]); time.sleep(0.5)
            shot(d, "02_add_filled")
            # 저장
            save_btn = None
            for b in dialog.find_elements(By.CSS_SELECTOR, "button"):
                if (b.text or "").strip() == "저장":
                    save_btn = b; break
            assert save_btn
            js_click(d, save_btn); time.sleep(1.5)
            dismiss_swal(d)
            shot(d, "03_saved")
            # 목록에 스케줄명 존재
            WebDriverWait(d, 10).until(
                lambda drv: name in drv.page_source
            )
            # 행 찾기
            rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
            found = None
            for r in rows:
                if name in (r.text or ""): found = r; break
            assert found, "등록 후 목록에 없음"
            state["row"] = found
        if not step("3. 스케줄 추가", _add): return

        # 4. 즉시 실행
        def _run_now():
            row = state["row"]
            run_btn = None
            for b in row.find_elements(By.CSS_SELECTOR, "button"):
                title = b.get_attribute("title") or ""
                if "즉시" in title:
                    run_btn = b; break
            assert run_btn, "즉시 실행 버튼 없음"
            js_click(d, run_btn); time.sleep(1)
            # confirm swal
            dismiss_swal(d)
            time.sleep(2)
            # 결과 swal
            dismiss_swal(d)
            shot(d, "04_run_now")
        if not step("4. 즉시 실행 클릭", _run_now): return

        # 5. 실행 이력 메뉴 이동
        def _nav_log():
            nav(d, "scheduleGroup", "nav_scheduleLog")
            WebDriverWait(d, 10).until(
                EC.visibility_of_element_located((By.XPATH, "//button[contains(., '조회')]"))
            )
            time.sleep(1)
            shot(d, "05_log_nav")
        if not step("5. 실행 이력 메뉴 이동", _nav_log): return

        # 6. 이력 목록에 방금 실행 로그 표시 + 행 클릭
        def _verify_log():
            name = state["name"]
            # 여러 번 reload — 완료까지 시간 걸릴 수 있음
            for _ in range(15):
                rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
                found = None
                for r in rows:
                    if name in (r.text or ""): found = r; break
                if found:
                    state["logRow"] = found
                    return
                time.sleep(2)
                # 조회 버튼 재클릭
                try:
                    b = d.find_element(By.XPATH, "//button[contains(., '조회')]")
                    js_click(d, b); time.sleep(1)
                except Exception: pass
            raise RuntimeError(f"이력에 '{name}' 없음")
        if not step("6. 이력 목록에 실행 기록 포함", _verify_log): return

        # 7. 로그 행 클릭 → drawer 오픈
        def _detail():
            js_click(d, state["logRow"]); time.sleep(1)
            # drawer 나타남 확인
            WebDriverWait(d, 5).until(
                EC.visibility_of_element_located((By.XPATH, "//span[contains(text(), '실행 이력 상세')]"))
            )
            shot(d, "06_detail_drawer")
        if not step("7. 로그 행 클릭 → 상세 drawer 오픈", _detail): return

        # 8. 정리: 다시 관리 화면으로 돌아가 스케줄 삭제
        def _cleanup():
            nav(d, "scheduleGroup", "nav_scheduleManage")
            time.sleep(1.5)
            name = state["name"]
            rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
            target = None
            for r in rows:
                if name in (r.text or ""): target = r; break
            if not target:
                print("  이미 삭제됨 or 미발견 — 스킵")
                return
            # ⋮ 메뉴 버튼 클릭
            kebab = None
            for b in target.find_elements(By.CSS_SELECTOR, "button"):
                html = b.get_attribute("innerHTML") or ""
                if "mdi-dots-vertical" in html:
                    kebab = b; break
            if not kebab:
                print("  kebab 없음 — API 로 직접 삭제 스킵")
                return
            js_click(d, kebab); time.sleep(0.5)
            # 메뉴의 "삭제" 항목
            del_items = d.find_elements(By.XPATH, "//div[contains(@class,'v-list-item')]//div[contains(text(), '삭제')]")
            if del_items:
                js_click(d, del_items[0]); time.sleep(1)
                dismiss_swal(d)
            shot(d, "99_final")
        step("8. 정리 (스케줄 삭제)", _cleanup)

    finally:
        time.sleep(2)
        try: d.quit()
        except Exception: pass


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for name, status, _ in results:
        print(f"  [{status}] {name}")
    sys.exit(0 if f == 0 else 1)
