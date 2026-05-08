"""
Selenium 메뉴 크롤러 — 모든 좌측 메뉴 진입 + 스크린샷 + 버튼/필드 카운트.

사용법:
    python selenium_menu_crawler.py [--user space] [--out crawl_report.md]

출력:
  - dataQ설계/테스트/uitools/screenshots/<menu_id>.png
  - crawl_report.md — 메뉴별 진입 결과 / 버튼 수 / 응답 시간 / 에러
"""
import argparse
import os
import sys
import time
import traceback
from collections import OrderedDict

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "screenshots")
os.makedirs(SHOTS, exist_ok=True)

# 메뉴 ID + 그룹 텍스트 (그룹은 메뉴가 들어있는 좌측 그룹의 헤더 텍스트)
# 사용자가 묻는 "전부" 라 NdNav 의 모든 nav_* 시도. 실패하면 결과에 SKIP 기록.
MENUS = [
    # (group_text, menu_id, label)
    ("",                      "nav_dashboard",                 "대시보드"),
    # 데이터 표준 사전
    ("데이터 표준 사전",      "nav_dsCode",                    "코드"),
    ("데이터 표준 사전",      "nav_domain",                    "도메인 사전"),
    ("데이터 표준 사전",      "nav_domainClassification",      "도메인 분류"),
    ("데이터 표준 사전",      "nav_domainGroup",               "도메인 그룹"),
    ("데이터 표준 사전",      "nav_changeHistory",             "변경 이력"),
    # 데이터 모델
    ("데이터 모델",           "nav_datamodelStatusTable",      "테이블"),
    ("데이터 모델",           "nav_datamodelStatusColumn",     "컬럼"),
    ("데이터 모델",           "nav_datamodelStatusIndex",      "인덱스"),
    ("데이터 모델",           "nav_datamodelStatusConstraint", "제약조건"),
    ("데이터 모델",           "nav_datamodelCollection",       "데이터 모델 관리"),
    ("데이터 모델",           "nav_datamodelHistory",          "데이터 모델 수집이력"),
    ("데이터 모델",           "nav_datamodelStatus",           "데이터 모델 현황"),
    ("데이터 모델",           "nav_diagTargetMgmt",            "진단 제외 관리"),
    ("데이터 모델",           "nav_erwinImport",               "모델링 도구 임포트"),
    # 표준 진단
    ("표준 진단",             "nav_dataDiag",                  "진단 실행"),
    ("표준 진단",             "nav_dataDiagResult",            "진단 결과"),
    # nav_scurrent — 주석 처리된 비활성 메뉴 (NdNav 안 <!-- ... -->)
    # nav_roles — :style="display:none" 명시 숨김
    # 데이터 품질 진단
    ("데이터 품질 진단",      "nav_qualDomainRule",            "도메인 룰 관리"),
    ("데이터 품질 진단",      "nav_valueProfile",              "값 프로파일링"),
    ("데이터 품질 진단",      "nav_ruleManage",                "업무 규칙 관리"),
    ("데이터 품질 진단",      "nav_ruleResult",                "업무 규칙 진단 결과"),
    ("데이터 품질 진단",      "nav_qualColRule",               "컬럼 규칙 매핑"),
    ("데이터 품질 진단",      "nav_qualStats",                 "진단 통계"),
    # 진단 스케줄
    ("진단 스케줄",           "nav_scheduleManage",            "스케줄 관리"),
    ("진단 스케줄",           "nav_scheduleLog",               "스케줄 실행 이력"),
    # 마이페이지
    ("마이페이지",            "nav_myProfile",                 "내 정보"),
    ("마이페이지",            "nav_myRequest",                 "요청 현황"),
    # 관리 (시스템 관리)
    ("관리",                  "nav_datasource",                "데이터 소스"),
    ("관리",                  "nav_approval",                  "승인 처리"),
    # 커뮤니티
    ("커뮤니티",              "nav_boardNotice",               "공지사항"),
    ("커뮤니티",              "nav_boardQna",                  "Q&A"),
]


def login(drv, user="space", pw="123"):
    drv.get(BASE)
    time.sleep(2)
    inp = WebDriverWait(drv, 10).until(EC.presence_of_element_located(
        (By.CSS_SELECTOR, "input[type='text']")))
    inp.send_keys(user)
    drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(pw)
    drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
    time.sleep(3)
    if "/app/main" not in drv.current_url:
        raise Exception(f"로그인 실패: {drv.current_url}")


def open_group(drv, group_text):
    """좌측 그룹 헤더 펼치기. 이미 active 면 클릭 안 함 (토글 회피)."""
    if not group_text:
        return
    hdrs = drv.find_elements(By.CSS_SELECTOR, ".v-list-group__header .v-list-item__title")
    for h in hdrs:
        if (h.text or "").strip() == group_text:
            # 그룹 element 추적해서 active class 확인
            grp = h
            for _ in range(8):
                try:
                    grp = grp.find_element(By.XPATH, "..")
                    if "v-list-group" in (grp.get_attribute("class") or ""):
                        break
                except Exception:
                    break
            cls = (grp.get_attribute("class") or "") if grp else ""
            if "v-list-group--active" in cls:
                return  # 이미 펼쳐짐 — 토글 안 함
            try:
                drv.execute_script("arguments[0].scrollIntoView({block:'center'});", h)
                h.click()
                time.sleep(0.7)
            except Exception:
                pass
            return


def visit_menu(drv, menu_id, group_text):
    """메뉴 진입 + 응답 시간 측정 + 스크린샷 + 화면 분석."""
    result = {
        "menu_id": menu_id,
        "group": group_text,
        "status": "OK",
        "elapsed_s": None,
        "btn_count": 0,
        "input_count": 0,
        "tbl_rows": 0,
        "error_text": None,
        "screenshot": None
    }
    try:
        # 그룹 펼치기 (있으면)
        for _ in range(2):
            try:
                el = drv.find_element(By.ID, menu_id)
                if el.is_displayed():
                    break
            except Exception:
                pass
            open_group(drv, group_text)
        try:
            el = WebDriverWait(drv, 5).until(EC.visibility_of_element_located((By.ID, menu_id)))
        except Exception:
            result["status"] = "MENU_NOT_VISIBLE"
            return result
        t0 = time.time()
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", el)
        try:
            el.click()
        except Exception:
            drv.execute_script("arguments[0].click();", el)
        time.sleep(2.5)  # 페이지 로드/transition 대기
        result["elapsed_s"] = round(time.time() - t0, 2)

        # 화면 카운트
        result["btn_count"]   = len([b for b in drv.find_elements(By.CSS_SELECTOR, ".v-btn") if b.is_displayed()])
        result["input_count"] = len([i for i in drv.find_elements(By.CSS_SELECTOR, ".v-input input, .v-input textarea")
                                      if i.is_displayed()])
        # data-table tbody tr (대시보드 등 그리드 잡힘)
        result["tbl_rows"]    = len(drv.find_elements(By.CSS_SELECTOR, ".v-data-table__wrapper tbody tr"))

        # 에러 페이지 흔적
        page = drv.page_source
        for marker in ("Whitelabel Error Page", "java.lang.", "ERR_CONNECTION"):
            if marker in page:
                result["status"] = "ERROR_PAGE"
                result["error_text"] = marker
                break

        # 스크린샷
        path = os.path.join(SHOTS, f"{menu_id}.png")
        drv.save_screenshot(path)
        result["screenshot"] = os.path.relpath(path, os.path.dirname(HERE))
    except Exception as e:
        result["status"] = "EXCEPTION"
        result["error_text"] = str(e)[:120]
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--user", default="space")
    parser.add_argument("--out", default=os.path.join(HERE, "crawl_report.md"))
    parser.add_argument("--limit", type=int, default=0, help="0 = 전체")
    args = parser.parse_args()

    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    drv = webdriver.Edge(options=opts)
    drv.set_window_size(1500, 1000)

    results = []
    try:
        login(drv, args.user)
        menus = MENUS[:args.limit] if args.limit > 0 else MENUS
        print(f"[crawl] {len(menus)} menus, user={args.user}")
        for i, (group, menu_id, label) in enumerate(menus, 1):
            r = visit_menu(drv, menu_id, group)
            r["label"] = label
            r["index"] = i
            print(f"  [{i:02d}/{len(menus)}] {menu_id:32s} {r['status']:18s} btn={r['btn_count']} {r['elapsed_s']}s")
            results.append(r)
    finally:
        time.sleep(1)
        drv.quit()

    # MD 리포트
    md = ["# Selenium 메뉴 크롤링 리포트\n"]
    md.append(f"- 사용자: `{args.user}`")
    md.append(f"- 메뉴 수: **{len(results)}**")
    ok = sum(1 for r in results if r["status"] == "OK")
    md.append(f"- 정상 진입: **{ok}** / 미노출/실패: **{len(results)-ok}**\n")
    # 표
    md.append("| # | 그룹 | 메뉴 | ID | 상태 | 응답(s) | 버튼 | 입력 | 행 | 에러 |")
    md.append("|---|---|---|---|---|---|---|---|---|---|")
    for r in results:
        err = (r["error_text"] or "")[:40]
        md.append(f"| {r['index']} | {r.get('group','')} | {r['label']} | `{r['menu_id']}` "
                  f"| {r['status']} | {r['elapsed_s'] if r['elapsed_s'] is not None else '-'} "
                  f"| {r['btn_count']} | {r['input_count']} | {r['tbl_rows']} | {err} |")
    md.append("")

    # 상세 — 화면별 스크린샷 링크
    md.append("## 화면 스크린샷\n")
    for r in results:
        if r.get("screenshot"):
            rel = r["screenshot"].replace("\\", "/")
            md.append(f"### {r['index']}. {r['label']} (`{r['menu_id']}`)")
            md.append(f"![{r['menu_id']}]({rel})")
            md.append("")

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    print(f"[out] {args.out}")
    print(f"[out] screenshots in {SHOTS}/")


if __name__ == "__main__":
    main()
