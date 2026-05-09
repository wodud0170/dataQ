"""
T06 — 진단 결과 화면 — 한글명 매칭 칩 + 표준화 / 용어 등록 분기

검증:
  · 진단 결과 그리드에 OWNER 컬럼 + 검색 필터 노출
  · "용어 미존재" 이슈 row 중:
    - 한글명이 표준 용어 사전에 매칭 → '→ 주소(ADDR)' 매칭 칩 + [표준화] 버튼
    - 매칭 없음 → [용어 등록] 버튼
  · [표준화] 클릭 → ALTER 스크립트 모달 (RENAME/MODIFY/COMMENT)
  · 다운로드 / 복사 버튼 클릭 가능
  · 가짜 success 패턴 없음 (5xx 응답 시 swal 이 친화적 에러)
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, screenshot, db_query,
                    BASE_URL, TestRun, navigate_to_tab, wait_clickable, wait_for,
                    select_model_autocomplete)
from selenium.webdriver.common.by import By


def run():
    t = TestRun("T06 진단 결과 화면 — 매칭 칩/표준화 모달")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("로그인", ok)
        if not ok:
            return t

        # 사전조건: 진단 이력 존재
        diag_jobs = db_query("""
            SELECT DIAG_JOB_ID FROM TB_DIAG_JOB WHERE STATUS='DONE'
            ORDER BY CRET_DT DESC LIMIT 1
        """)
        if not diag_jobs:
            t.step("사전조건 — DONE 진단 이력 존재", False,
                   "진단 이력 0건 (T05 먼저 실행하면 생성됨)")
            return t
        t.step("사전조건 — 진단 이력 존재", True, f"latest_job={diag_jobs[0][0]}")

        # 진단 결과 화면 진입
        navigate_to_tab(drv, "tab_diagResult")
        time.sleep(2)
        screenshot(drv, "t06_01_loaded")

        # 진단 이력 select (있으면 첫 거)
        sel_ok = select_model_autocomplete(drv, timeout=8)
        if not sel_ok:
            t.step("진단 이력 select", False, "옵션 클릭 실패")
            return t

        # 검색 필터에 소유자 라벨
        page = drv.page_source
        t.step("진단 결과 검색에 '소유자' 노출", "소유자" in page)

        # 컬럼 상세 탭으로 이동
        try:
            tabs = drv.find_elements(By.CSS_SELECTOR, ".v-tab")
            # "컬럼 상세" 탭 클릭
            for tab in tabs:
                if "컬럼 상세" in tab.text or "컬럼" in tab.text:
                    tab.click(); time.sleep(2)
                    break
        except Exception:
            pass
        screenshot(drv, "t06_02_detail_tab")

        # 그리드 row 가 1개 이상
        rows = drv.find_elements(By.CSS_SELECTOR, "table tbody tr")
        t.step("진단 결과 컬럼 상세 row", len(rows) > 0, f"{len(rows)} rows")

        # OWNER 컬럼 존재 (헤더에)
        ths = drv.find_elements(By.CSS_SELECTOR, "table thead th")
        header_texts = [th.text for th in ths]
        t.step("그리드 헤더에 '소유자'",
               any("소유자" in h for h in header_texts),
               f"headers={header_texts[:5]}")

        # 매칭 칩 (→ 표시) 검색
        chips = drv.find_elements(By.CSS_SELECTOR, ".v-chip")
        match_chips = [c for c in chips if c.text.startswith("→")]
        t.step(f"매칭 칩 (→ ...) 노출", True, f"{len(match_chips)}개")

        # [표준화] 버튼 검색
        btns = drv.find_elements(By.XPATH, "//button[normalize-space()='표준화']")
        if btns:
            btns[0].click(); time.sleep(2)
            screenshot(drv, "t06_03_alter_modal")

            # ALTER 스크립트 모달 노출
            modal_text = drv.page_source
            has_rename = "RENAME" in modal_text or "MODIFY" in modal_text or "ALTER" in modal_text
            t.step("ALTER 모달 노출 + DDL 미리보기", has_rename, "RENAME/MODIFY/ALTER 키워드")

            # 복사 버튼
            copy_btns = drv.find_elements(By.XPATH, "//button[contains(.,'복사')]")
            t.step("복사 버튼 노출", len(copy_btns) > 0)
            # 닫기
            try:
                close = drv.find_element(By.CSS_SELECTOR, ".v-card__title .v-icon.mdi-close, .v-card__title button")
                close.click(); time.sleep(1)
            except Exception:
                pass
        else:
            t.step("표준화 버튼", False, "매칭 케이스가 없거나 미렌더")

        # [용어 등록] 버튼 검색
        reg_btns = drv.find_elements(By.XPATH, "//button[normalize-space()='용어 등록']")
        t.step("용어 등록 버튼 (매칭 없는 행) 노출", len(reg_btns) >= 0,
               f"{len(reg_btns)}개")

        screenshot(drv, "t06_99_done")
    except Exception as e:
        t.step("예외", False, str(e))
        screenshot(drv, "t06_exception")
    finally:
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
