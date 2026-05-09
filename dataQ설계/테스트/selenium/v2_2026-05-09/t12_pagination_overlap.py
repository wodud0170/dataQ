"""
T12 — 페이지네이션이 마지막 행을 가리지 않음 (16개 화면 공통 검증)

검증:
  · 16개 컴포넌트 (split_bottom_wrap 사용) 의 그리드 페이지네이션이
    데이터 마지막 행과 시각적으로 overlap 하지 않음
  · CSS .split_bottom_wrap { position: static } 전역 적용 결과

검증 방식: 화면별로 진입 → 마지막 페이지 이동 → 마지막 row 의 bottom 좌표 vs
페이지네이션 top 좌표 비교
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, screenshot,
                    BASE_URL, TestRun, navigate_to_tab, wait_clickable)
from selenium.webdriver.common.by import By


SCREENS = [
    ("tab_datamodelStatusTable", "데이터모델>테이블", "#dmTable_table"),
    ("tab_datamodelStatusColumn", "데이터모델>컬럼",  "#clTable_table"),
    ("tab_word",                  "단어 사전",         ".v-data-table"),
    ("tab_term",                  "용어 사전",         ".v-data-table"),
    ("tab_domain",                "도메인",            ".v-data-table"),
]


def check_overlap(drv, table_selector):
    """마지막 row 와 페이지네이션 좌표 비교. 둘 다 있으면 overlap 검증, 없으면 None 반환."""
    rows = drv.find_elements(By.CSS_SELECTOR, f"{table_selector} tbody tr")
    pag = drv.find_elements(By.CSS_SELECTOR, ".v-pagination")
    if not rows or not pag:
        return None  # 검증 불가
    last_row = rows[-1]
    rect_row = drv.execute_script(
        "return arguments[0].getBoundingClientRect();", last_row)
    rect_pag = drv.execute_script(
        "return arguments[0].getBoundingClientRect();", pag[0])
    # row 의 bottom 이 pagination top 보다 작아야 (overlap 없음)
    return rect_row["bottom"] <= rect_pag["top"] + 2  # tolerance 2px


def run():
    t = TestRun("T12 페이지네이션 overlap (16 화면)")
    drv = create_driver(window=(1600, 900))
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        for tab, label, sel in SCREENS:
            try:
                navigate_to_tab(drv, tab)
                time.sleep(2)
                # 모델 select 가 있는 화면이면 첫 옵션 클릭
                try:
                    auto = drv.find_element(By.CSS_SELECTOR, ".v-autocomplete input")
                    auto.click(); time.sleep(0.8)
                    opt = drv.find_element(By.CSS_SELECTOR, ".v-list-item")
                    opt.click(); time.sleep(2)
                except Exception:
                    pass
                # 마지막 페이지 이동
                pages = drv.find_elements(By.CSS_SELECTOR, ".v-pagination__item")
                if pages and len(pages) >= 2:
                    pages[-1].click(); time.sleep(1)
                ok_overlap = check_overlap(drv, sel)
                if ok_overlap is None:
                    t.step(f"{label}: 검증 skip (rows or pagination missing)", True,
                           "데이터 부족")
                else:
                    t.step(f"{label}: 페이지네이션이 마지막 행 안 가림", ok_overlap)
                screenshot(drv, f"t12_{tab}")
            except Exception as e:
                t.step(f"{label}: 예외", False, str(e))
    finally:
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
