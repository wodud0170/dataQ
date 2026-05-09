"""
T01 — 로그인 + 관리자 인식 검증

검증 포인트:
  · space/123 로그인 성공
  · 5초간 anti-bounce
  · 헤더에 "관리자" 배지 노출
  · DB 의 TB_USER (또는 admin 테이블) 에 isAdmin=Y 인지 확인
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, screenshot, db_query,
                    BASE_URL, TestRun)
from selenium.webdriver.common.by import By


def run():
    t = TestRun("T01 로그인 + 관리자 인식")
    drv = create_driver()
    try:
        ok = login_admin(drv, "space", "123")
        t.step("space/123 로그인", ok, drv.current_url)
        screenshot(drv, "t01_01_login")
        if not ok:
            return t

        # 관리자 배지
        time.sleep(1)
        page = drv.page_source
        has_admin_badge = "관리자" in page
        t.step("페이지에 '관리자' 배지", has_admin_badge,
               "found" if has_admin_badge else "missing")

        # DB 검증 (quality.tb_user — adm_yn boolean)
        rows = db_query("SELECT user_id, adm_yn FROM tb_user WHERE user_id='space'")
        if rows:
            uid, admin = rows[0]
            t.step("DB 의 space 사용자 adm_yn=true", admin == "t",
                   f"{uid}/adm_yn={admin}")
        else:
            t.step("DB 의 space 사용자 row 존재", False, "row not found")

        screenshot(drv, "t01_99_done")
    except Exception as e:
        t.step("예외", False, str(e))
        screenshot(drv, "t01_exception")
    finally:
        drv.quit()
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
