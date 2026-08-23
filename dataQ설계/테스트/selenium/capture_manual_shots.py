# -*- coding: utf-8 -*-
"""사용자 매뉴얼용 스크린샷 일괄 캡처.

매뉴얼이 기술하는 화면을 현재 빌드 기준으로 새로 찍는다.
기존 manual_screenshots/ 는 촬영 시점이 불명확해 UI 변경분이 반영돼 있는지 알 수 없다.

출력: dataQ설계/manual/assets/*.png
실행: python capture_manual_shots.py [--user jyjang]   (기본 space=관리자)
"""
import os
import sys
import time

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from console_check import make_options, collect_severe  # noqa: E402

BASE = "http://localhost:28091"
OUT = os.path.abspath(os.path.join(
    os.path.dirname(__file__), "..", "..", "manual", "assets"))

# (파일명, nav id, 캡처 전 추가 대기초)
SHOTS = [
    ("01_dashboard",            "nav_dashboard",                 3),
    ("02_word",                 "nav_word",                      2),
    ("03_term",                 "nav_term",                      2),
    ("04_code",                 "nav_dsCode",                    2),
    ("05_domain",               "nav_domain",                    2),
    ("06_domain_group",         "nav_domainGroup",               2),
    ("07_domain_class",         "nav_domainClassification",      2),
    ("08_change_history",       "nav_changeHistory",             2),
    ("09_dm_collection",        "nav_datamodelCollection",       2),
    ("10_dm_status",            "nav_datamodelStatus",           2),
    ("11_dm_table",             "nav_datamodelStatusTable",      3),
    ("12_dm_column",            "nav_datamodelStatusColumn",     3),
    ("13_dm_index",             "nav_datamodelStatusIndex",      2),
    ("14_dm_constraint",        "nav_datamodelStatusConstraint", 2),
    ("15_dm_history",           "nav_datamodelHistory",          2),
    ("16_diag_target",          "nav_diagTargetMgmt",            3),
    ("17_erwin_import",         "nav_erwinImport",               2),
    ("18_dm_visualization",     "nav_datamodelVisualization",    5),
    ("19_dm_change_history",    "nav_dm_history",                2),
    ("20_diag_execute",         "nav_dataDiag",                  2),
    ("21_diag_result",          "nav_dataDiagResult",            3),
    ("22_struct_diag",          "nav_structDiag",                2),
    ("23_struct_diag_result",   "nav_structDiagResult",          3),
    ("24_term_recommend",       "nav_termRecommend",             2),
    ("25_term_resolve_history", "nav_termResolveHistory",        2),
    ("26_board_notice",         "nav_boardNotice",               2),
    ("27_board_qna",            "nav_boardQna",                  2),
    ("28_schedule_manage",      "nav_scheduleManage",            2),
    ("29_schedule_log",         "nav_scheduleLog",               2),
    ("30_my_profile",           "nav_myProfile",                 2),
    ("31_my_request",           "nav_myRequest",                 2),
    ("32_my_dm_changes",        "nav_my_dm_changes",             2),
    ("33_admin_approval",       "nav_approval",                  3),
    ("34_admin_dm_approval",    "nav_dm_approval",               2),
    ("35_admin_area",           "nav_area_mgmt",                 2),
    ("36_admin_datasource",     "nav_datasource",                2),
    ("37_admin_user",           "nav_user",                      2),
]


# 매뉴얼 스크린샷은 "실제 데이터가 보이는 상태" 여야 한다.
# 개발 DB 에는 테스트가 만든 빈 모델이 다수라 기본 선택이 0건인 모델로 잡히는 경우가 있다.
MANUAL_MODEL = "오라클테스트"
MODEL_SCREENS = ['01_dashboard', '10_dm_status', '11_dm_table', '12_dm_column', '13_dm_index', '14_dm_constraint', '16_diag_target', '18_dm_visualization', '19_dm_change_history', '20_diag_execute', '21_diag_result', '22_struct_diag', '23_struct_diag_result']   # 테이블 22 / 컬럼 125 / 진단완료 21회 / 수집 1회


def _close_menu(drv):
    """열려 있는 드롭다운을 닫는다. 열린 채로 캡처되면 화면을 가린다."""
    try:
        from selenium.webdriver.common.keys import Keys
        drv.find_element(By.TAG_NAME, "body").send_keys(Keys.ESCAPE)
        time.sleep(0.4)
    except Exception:
        pass
    try:
        drv.execute_script(
            "document.querySelectorAll('.v-menu__content').forEach(function(m){m.style.display='none';});")
        time.sleep(0.2)
    except Exception:
        pass


def pick_model(drv, model=MANUAL_MODEL, wait=3):
    """모델 선택 필드가 있으면 지정 모델을 고른다.

    v-select 는 타이핑 필터가 안 먹고, 개발 DB 에는 테스트가 만든 모델이 200개 가까이 있어
    대상이 목록 아래쪽에 있다. 메뉴를 스크롤하며 찾는다.
    못 찾으면 반드시 메뉴를 닫고 False — 열린 드롭다운이 화면을 가리면 안 된다.
    """
    boxes = [b for b in drv.find_elements(
        By.CSS_SELECTOR, ".v-select__slot input, .v-autocomplete input") if b.is_displayed()]
    for b in boxes:
        try:
            drv.execute_script("arguments[0].scrollIntoView({block:'center'});", b)
            time.sleep(0.2)
            drv.execute_script("arguments[0].click();", b)
            time.sleep(0.6)
            # autocomplete 면 타이핑으로 좁혀본다 (v-select 면 무시됨)
            try:
                b.send_keys(model)
                time.sleep(1.0)
            except Exception:
                pass
            menus = [m for m in drv.find_elements(By.CSS_SELECTOR, ".v-menu__content")
                     if m.is_displayed()]
            if not menus:
                continue
            menu = menus[0]
            for _ in range(40):
                opts = menu.find_elements(By.CSS_SELECTOR, ".v-list-item")
                hit = [o for o in opts if model in (o.text or "")]
                if hit:
                    drv.execute_script("arguments[0].scrollIntoView({block:'center'});", hit[0])
                    time.sleep(0.2)
                    drv.execute_script("arguments[0].click();", hit[0])
                    time.sleep(wait)
                    _close_menu(drv)
                    return True
                prev = drv.execute_script("return arguments[0].scrollTop;", menu)
                drv.execute_script(
                    "arguments[0].scrollTop = arguments[0].scrollTop + arguments[0].clientHeight;", menu)
                time.sleep(0.25)
                if drv.execute_script("return arguments[0].scrollTop;", menu) == prev:
                    break   # 더 내려갈 곳 없음
            _close_menu(drv)
        except Exception:
            _close_menu(drv)
            continue
    _close_menu(drv)
    return False


def open_all_groups(drv):
    for _ in range(2):
        for h in drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header"):
            try:
                cls = h.find_element(By.XPATH, "..").get_attribute("class") or ""
                if "v-list-group--active" not in cls:
                    drv.execute_script("arguments[0].click();", h)
                    time.sleep(0.25)
            except Exception:
                pass


def main(user="space"):
    os.makedirs(OUT, exist_ok=True)
    drv = webdriver.Edge(options=make_options(webdriver))
    drv.set_window_size(1600, 1000)
    made, failed = [], []
    try:
        drv.get(BASE)
        time.sleep(2)
        WebDriverWait(drv, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
        drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
        time.sleep(8)
        collect_severe(drv)

        # 로그인 화면은 별도 세션으로 따로 찍는다 (아래 capture_login)
        open_all_groups(drv)

        for name, nav_id, wait in SHOTS:
            path = os.path.join(OUT, name + ".png")
            try:
                el = drv.find_element(By.ID, nav_id)
                drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
                time.sleep(0.2)
                drv.execute_script("arguments[0].click();", el)
                time.sleep(wait)
                if name in MODEL_SCREENS:
                    picked = pick_model(drv)
                    if not picked:
                        print("       (모델 선택 실패 — 기본 상태로 캡처)")
                    time.sleep(1.0)
                _close_menu(drv)          # 열린 오버레이가 화면을 가리지 않게
                drv.save_screenshot(path)
                sz = os.path.getsize(path)
                made.append((name, sz))
                print("  OK   %-26s %6.1fKB" % (name, sz / 1024))
            except Exception as ex:
                failed.append((name, type(ex).__name__))
                print("  FAIL %-26s %s" % (name, type(ex).__name__))
    finally:
        drv.quit()

    # 로그인 화면
    drv2 = webdriver.Edge(options=make_options(webdriver))
    drv2.set_window_size(1600, 1000)
    try:
        drv2.get(BASE)
        time.sleep(3)
        p = os.path.join(OUT, "00_login.png")
        drv2.save_screenshot(p)
        made.append(("00_login", os.path.getsize(p)))
        print("  OK   %-26s %6.1fKB" % ("00_login", os.path.getsize(p) / 1024))
    finally:
        drv2.quit()

    print("\n캡처 %d개 / 실패 %d개 → %s" % (len(made), len(failed), OUT))
    return 0 if not failed else 1


if __name__ == "__main__":
    u = "space"
    if "--user" in sys.argv:
        u = sys.argv[sys.argv.index("--user") + 1]
    sys.exit(main(u))
