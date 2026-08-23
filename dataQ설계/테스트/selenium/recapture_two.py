# -*- coding: utf-8 -*-
"""매뉴얼 스크린샷 재촬영 — 1차 촬영에서 화면이 제대로 안 나온 2건만.

23_struct_diag_result : 진단이력(2번째 셀렉트)이 미선택이라 빈 상태로 찍혔다.
18_dm_visualization   : 계층 레이아웃 + 22테이블이라 fit 배율이 너무 작아 판독 불가.
                        자율 배치로 2D 로 펼친 뒤 맞춤.
"""
import os
import sys
import time

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from console_check import make_options                      # noqa: E402
from capture_manual_shots import pick_model, open_all_groups, _close_menu, OUT  # noqa: E402

BASE = "http://localhost:28091"


def click_by_text(drv, text, tags=("button", "div", "span")):
    for t in tags:
        for e in drv.find_elements(By.TAG_NAME, t):
            try:
                if e.is_displayed() and (e.text or "").strip() == text:
                    drv.execute_script("arguments[0].click();", e)
                    return True
            except Exception:
                pass
    return False


def pick_nth_select(drv, idx, wait=3):
    """idx 번째(0-base) 보이는 v-select 를 열고 첫 항목을 고른다."""
    boxes = [b for b in drv.find_elements(By.CSS_SELECTOR, ".v-select__slot input")
             if b.is_displayed()]
    if len(boxes) <= idx:
        return False
    try:
        drv.execute_script("arguments[0].click();", boxes[idx])
        time.sleep(0.8)
        menus = [m for m in drv.find_elements(By.CSS_SELECTOR, ".v-menu__content")
                 if m.is_displayed()]
        if not menus:
            return False
        opts = menus[0].find_elements(By.CSS_SELECTOR, ".v-list-item")
        if not opts:
            _close_menu(drv)
            return False
        drv.execute_script("arguments[0].click();", opts[0])
        time.sleep(wait)
        _close_menu(drv)
        return True
    except Exception:
        _close_menu(drv)
        return False


def main():
    drv = webdriver.Edge(options=make_options(webdriver))
    drv.set_window_size(1600, 1000)
    done, bad = [], []
    try:
        drv.get(BASE)
        time.sleep(2)
        WebDriverWait(drv, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
        time.sleep(8)
        open_all_groups(drv)

        # ---- 23 구조 변경 진단 결과 ----
        el = drv.find_element(By.ID, "nav_structDiagResult")
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        drv.execute_script("arguments[0].click();", el)
        time.sleep(3)
        if not pick_model(drv):
            print("  ! 모델 선택 실패")
        time.sleep(1)
        ok = pick_nth_select(drv, 1, wait=5)        # 진단이력
        print("  진단이력 선택: %s" % ("OK" if ok else "실패"))
        time.sleep(2)
        _close_menu(drv)
        p = os.path.join(OUT, "23_struct_diag_result.png")
        drv.save_screenshot(p)
        (done if ok else bad).append(("23_struct_diag_result", os.path.getsize(p)))
        print("  저장 23_struct_diag_result  %.0fKB" % (os.path.getsize(p) / 1024))

        # ---- 18 ERD ----
        el = drv.find_element(By.ID, "nav_datamodelVisualization")
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        drv.execute_script("arguments[0].click();", el)
        time.sleep(4)
        if not pick_model(drv, wait=5):
            print("  ! 모델 선택 실패")
        time.sleep(3)
        print("  자율 배치: %s" % click_by_text(drv, "자율 배치"))
        time.sleep(6)
        print("  맞춤: %s" % click_by_text(drv, "맞춤"))
        time.sleep(3)
        _close_menu(drv)
        p = os.path.join(OUT, "18_dm_visualization.png")
        drv.save_screenshot(p)
        done.append(("18_dm_visualization", os.path.getsize(p)))
        print("  저장 18_dm_visualization  %.0fKB" % (os.path.getsize(p) / 1024))
    finally:
        drv.quit()
    print("\n재촬영 %d건 / 미흡 %d건" % (len(done), len(bad)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
