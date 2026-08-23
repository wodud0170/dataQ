# -*- coding: utf-8 -*-
"""전 화면 브라우저 콘솔 SEVERE 스캔.

2026-08-23 신규. 셀레니움 46개 중 콘솔을 읽는 파일이 1개뿐이라
프론트엔드 런타임 에러가 사각지대였다. 실제 스캔에서 2종이 나왔고
둘 다 46개 전건 통과 상태에서 살아 있었다.
  - changeNavItem 무가드 DOM 접근 → 4개 화면 TypeError (NdLayout.vue)
  - ApexCharts 도넛 탭 전환 시 NaN 렌더 → SEVERE 16건 (QDashboard.vue)

화면 귀속 주의: get_log 는 호출 시 버퍼를 비운다. 앞 화면의 지연 렌더가
뒤 화면으로 딸려올 수 있으므로, 위 ⑨ 는 처음에 '단어 화면 결함' 으로 오인됐다.
첫 이탈 화면을 바꿔 재현해야 진짜 출처가 드러난다.
"""
import os
import sys
import time

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from console_check import collect_severe, drain, make_options  # noqa: E402

BASE = "http://localhost:28091"

# NdNav.vue:265-314 의 qual 메뉴 6종은 주석 처리 상태라 제외
# (nav_qualDomainRule / valueProfile / ruleManage / ruleResult / qualColRule / qualStats)
MENUS = [
    "nav_dashboard", "nav_word", "nav_term", "nav_dsCode", "nav_domain",
    "nav_domainGroup", "nav_domainClassification", "nav_changeHistory",
    "nav_datamodelCollection", "nav_datamodelStatus", "nav_datamodelStatusTable",
    "nav_datamodelStatusColumn", "nav_datamodelStatusIndex", "nav_datamodelStatusConstraint",
    "nav_datamodelHistory", "nav_diagTargetMgmt", "nav_erwinImport", "nav_datamodelVisualization",
    "nav_dm_history", "nav_dataDiag", "nav_dataDiagResult",
    "nav_structDiag", "nav_structDiagResult",
    "nav_termRecommend", "nav_termResolveHistory",
    "nav_boardNotice", "nav_boardQna",
    "nav_scheduleManage", "nav_scheduleLog",
    "nav_myProfile", "nav_myRequest", "nav_my_dm_changes",
    "nav_approval", "nav_dm_approval", "nav_area_mgmt", "nav_datasource", "nav_user",
]

results = []


def open_all_groups(drv):
    for _ in range(2):
        for h in drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header"):
            try:
                cls = h.find_element(By.XPATH, "..").get_attribute("class") or ""
                if "v-list-group--active" not in cls:
                    drv.execute_script("arguments[0].click();", h)
                    time.sleep(0.3)
            except Exception:
                pass


def main():
    drv = webdriver.Edge(options=make_options(webdriver))
    drv.set_window_size(1500, 1000)
    try:
        drv.get(BASE)
        time.sleep(2)
        WebDriverWait(drv, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()

        # 대시보드 차트 렌더가 끝날 때까지 충분히 기다린 뒤 버퍼를 비운다
        time.sleep(10)
        boot = collect_severe(drv)
        results.append(("로그인+대시보드", boot))
        print("  [%-30s] %s" % ("로그인+대시보드", "clean" if not boot else "SEVERE %d건" % len(boot)))
        for m in boot[:3]:
            print("       ", m[:170])

        open_all_groups(drv)
        drain(drv)

        for mid in MENUS:
            try:
                el = drv.find_element(By.ID, mid)
                drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
                time.sleep(0.2)
                drv.execute_script("arguments[0].click();", el)
                time.sleep(3.0)
                errs = collect_severe(drv)
                results.append((mid, errs))
                if errs:
                    print("  [%-30s] SEVERE %d건" % (mid, len(errs)))
                    seen = set()
                    for m in errs:
                        k = m[:90]
                        if k in seen:
                            continue
                        seen.add(k)
                        print("       ", m[:175])
                        if len(seen) >= 3:
                            break
                else:
                    print("  [%-30s] clean" % mid)
            except Exception as ex:
                print("  [%-30s] 진입 실패 %s" % (mid, type(ex).__name__))
                results.append((mid, None))
    finally:
        drv.quit()


if __name__ == "__main__":
    t0 = time.time()
    main()
    clean = [m for m, e in results if e == []]
    bad = [(m, e) for m, e in results if e]
    fail = [m for m, e in results if e is None]
    print("\n%s" % ("=" * 60))
    print("결과: %d PASS / %d FAIL  (%.1f초)" % (len(clean), len(bad) + len(fail), time.time() - t0))
    print("%s" % ("=" * 60))
    for m, e in bad:
        print("  [FAIL] %-30s 콘솔 SEVERE %d건" % (m, len(e)))
    for m in fail:
        print("  [FAIL] %-30s 진입 실패" % m)
    for m in clean:
        print("  [PASS] %-30s clean" % m)
    sys.exit(0 if (not bad and not fail) else 1)
