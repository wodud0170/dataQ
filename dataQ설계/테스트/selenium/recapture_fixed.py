# -*- coding: utf-8 -*-
"""수정 확인용 재촬영 — 대시보드(숫자 잘림) / 진단 결과(소유자 구분)."""
import os
import sys
import time

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from console_check import make_options
from capture_manual_shots import pick_model, open_all_groups, _close_menu, OUT

BASE = "http://localhost:28091"
SHOTS = [("01_dashboard", "nav_dashboard", 5), ("21_diag_result", "nav_dataDiagResult", 4)]

drv = webdriver.Edge(options=make_options(webdriver))
drv.set_window_size(1600, 1000)
try:
    drv.get(BASE)
    time.sleep(2)
    WebDriverWait(drv, 20).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
    drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
    drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
    time.sleep(9)
    open_all_groups(drv)
    for name, nav, wait in SHOTS:
        el = drv.find_element(By.ID, nav)
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        drv.execute_script("arguments[0].click();", el)
        time.sleep(wait)
        pick_model(drv)
        time.sleep(2)
        _close_menu(drv)
        p = os.path.join(OUT, name + ".png")
        drv.save_screenshot(p)
        print("  %s  %.0fKB" % (name, os.path.getsize(p) / 1024))
finally:
    drv.quit()
