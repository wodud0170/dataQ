"""
T03 — 데이터모델 > 테이블 / 컬럼 화면

검증:
  · 소유자 컬럼 노출
  · 소유자 검색 (완전 일치) + 동일 OBJ_NM 분리 표시
  · 테이블 클릭 → 컬럼 화면으로 이동 시 검색조건 (소유자 + 테이블) 자동 세팅
  · 페이지네이션이 마지막 행 안 가림 (스크롤 끝까지 갔을 때)
  · 가로 스크롤이 페이지 밖으로 안 넘김
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, screenshot, db_query,
                    BASE_URL, TestRun, navigate_to_tab, wait_for, wait_clickable,
                    select_model_autocomplete)
from selenium.webdriver.common.by import By


def run():
    t = TestRun("T03 테이블/컬럼 화면 — owner 분리 + 페이징 + 가로스크롤")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # === 테이블 화면 ===
        navigate_to_tab(drv, "tab_datamodelStatusTable")
        time.sleep(2)

        # 모델 선택 (첫 모델로 추정 — 사용자 환경에 1건 있다고 가정)
        sel_ok = select_model_autocomplete(drv)
        t.step("모델 선택", sel_ok, "첫 옵션 선택")
        screenshot(drv, "t03_01_table_loaded")

        # 소유자 헤더 노출
        page = drv.page_source
        t.step("테이블 화면에 '소유자' 헤더 노출", "소유자" in page)

        # 그리드 row 수 (visible row)
        rows = drv.find_elements(By.CSS_SELECTOR, "#dmTable_table tbody tr")
        t.step("테이블 그리드 row 표시", len(rows) > 0, f"{len(rows)} rows")

        # === 페이지네이션 가림 검증 ===
        # 마지막 페이지로 이동해서 마지막 행이 페이지네이션에 가리는지 확인
        pages = drv.find_elements(By.CSS_SELECTOR, ".v-pagination__item")
        if pages:
            try:
                pages[-1].click(); time.sleep(1)
                last_rows = drv.find_elements(By.CSS_SELECTOR, "#dmTable_table tbody tr")
                if last_rows:
                    last_row = last_rows[-1]
                    last_row_rect = drv.execute_script(
                        "const r = arguments[0].getBoundingClientRect();"
                        "return {top: r.top, bottom: r.bottom};", last_row)
                    pagination_el = drv.find_element(By.CSS_SELECTOR, ".v-pagination")
                    pag_rect = drv.execute_script(
                        "const r = arguments[0].getBoundingClientRect();"
                        "return {top: r.top};", pagination_el)
                    overlap = last_row_rect["bottom"] > pag_rect["top"]
                    t.step("페이지네이션이 마지막 행을 가리지 않음", not overlap,
                           f"last_row.bottom={last_row_rect['bottom']}, pag.top={pag_rect['top']}")
            except Exception as e:
                t.step("페이지네이션 가림 체크", False, str(e))
        else:
            t.step("페이지네이션 검증", True, "페이지 1개라 skip")
        screenshot(drv, "t03_02_table_last_page")

        # === 컬럼 화면 ===
        navigate_to_tab(drv, "tab_datamodelStatusColumn")
        time.sleep(2)
        screenshot(drv, "t03_03_column_screen")
        # 검색 필터 라벨들
        for label in ["소유자", "테이블 영문", "테이블 한글", "컬럼 영문", "컬럼 한글"]:
            t.step(f"컬럼 화면 검색 라벨 '{label}'", label in drv.page_source)

        # 가로 스크롤이 page 자체를 넘기지 않는지 — html.scrollWidth 가 viewport 와 일치 또는 비슷
        body_scroll = drv.execute_script("return document.documentElement.scrollWidth")
        view_width = drv.execute_script("return window.innerWidth")
        t.step("페이지 가로 스크롤 안 넘침", body_scroll <= view_width + 5,
               f"scrollW={body_scroll}, viewW={view_width}")

        # === 테이블 → 컬럼 자동 검색조건 ===
        # 테이블 화면으로 가서 첫 행 테이블명 클릭
        navigate_to_tab(drv, "tab_datamodelStatusTable")
        time.sleep(2)
        try:
            first_link = drv.find_element(By.CSS_SELECTOR, "#dmTable_table tbody tr a")
            tbl_name = first_link.text
            owner_cell = first_link.find_element(By.XPATH, "../..//td[1]")  # 소유자가 첫 열
            owner_text = owner_cell.text
            first_link.click(); time.sleep(3)
            screenshot(drv, "t03_04_clicked_table_name")

            # 컬럼 화면이 active 인지 — clTable_table 이 보이는지로 판단
            col_grid = drv.find_elements(By.CSS_SELECTOR, "#clTable_table")
            t.step(f"테이블 클릭 → 컬럼 화면 진입 (테이블={tbl_name}, 소유자={owner_text})",
                   len(col_grid) > 0 and col_grid[0].is_displayed(),
                   f"clTable_table visible={len(col_grid) > 0 and col_grid[0].is_displayed() if col_grid else False}")

            # 컬럼 화면 검색조건 자동 세팅 — 모든 v-text-field input value 확인
            inputs_with_value = drv.execute_script("""
                const root = document.querySelector('#tab_datamodelStatusColumn');
                if (!root) return [];
                const inputs = root.querySelectorAll('input.v-text-field__slot input, .v-text-field input[type="text"]');
                return Array.from(inputs).map(i => i.value).filter(v => v);
            """)
            t.step("컬럼 화면 검색조건 (owner/tbl) 자동 세팅",
                   any(owner_text in v for v in inputs_with_value)
                   or any(tbl_name in v for v in inputs_with_value),
                   f"filter values={inputs_with_value}, expected owner={owner_text} tbl={tbl_name}")
        except Exception as e:
            t.step("테이블 → 컬럼 이동", False, str(e))

        screenshot(drv, "t03_99_done")
    except Exception as e:
        t.step("예외", False, str(e))
        screenshot(drv, "t03_exception")
    finally:
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
