# -*- coding: utf-8 -*-
"""브라우저 콘솔 SEVERE 로그 검사 유틸.

배경 (2026-08-23)
  셀레니움 46개 중 브라우저 콘솔을 읽는 파일이 1개뿐이라 프론트엔드 런타임 에러가
  통째로 사각지대였다. 실제로 스캔해보니 2종이 나왔다.
    - changeNavItem 의 무가드 DOM 접근 → 4개 화면에서 매 진입마다 TypeError
    - ApexCharts 도넛이 탭 전환 시 NaN 렌더 → SEVERE 16건
  둘 다 46개 전건 통과 상태에서 살아 있었다.

사용법
  1) 드라이버 생성 시 로깅 활성화가 필요하다:
       opts.set_capability("goog:loggingPrefs", {"browser": "ALL"})
  2) 화면 조작 후:
       from console_check import assert_no_console_errors
       assert_no_console_errors(drv, screen="단어")

주의
  get_log 는 호출 시 버퍼를 비운다(drain). 화면별로 귀속시키려면
  각 화면 진입 직후에 호출해야 한다. 앞 화면의 지연 렌더가 뒤 화면으로 딸려올 수 있다.
"""

# 제품 결함이 아닌 잡음. 추가할 때는 반드시 사유를 같이 적을 것.
DEFAULT_ALLOW = (
    "favicon.ico",                 # 파비콘 404 — 기능 무관
    "Failed to load resource: net::ERR_INTERNET_DISCONNECTED",
)


def collect_severe(driver, allow=DEFAULT_ALLOW):
    """SEVERE 로그를 수집하고 버퍼를 비운다. 로깅 미활성 드라이버면 빈 리스트."""
    try:
        logs = driver.get_log("browser")
    except Exception:
        return []
    out = []
    for l in logs:
        if l.get("level") != "SEVERE":
            continue
        msg = l.get("message", "") or ""
        if any(a in msg for a in allow):
            continue
        out.append(msg)
    return out


def drain(driver):
    """앞 화면의 잔여 로그를 버린다. 화면 진입 전에 호출."""
    collect_severe(driver)


def assert_no_console_errors(driver, screen="", allow=DEFAULT_ALLOW, soft=False):
    """SEVERE 가 있으면 AssertionError. soft=True 면 경고만 출력하고 건수를 반환."""
    errs = collect_severe(driver, allow)
    if not errs:
        return 0
    head = "[%s] " % screen if screen else ""
    # 같은 메시지 반복은 접어서 보여준다
    uniq, seen = [], set()
    for m in errs:
        k = m[:120]
        if k in seen:
            continue
        seen.add(k)
        uniq.append(m)
    detail = "\n".join("    " + m[:200] for m in uniq[:5])
    text = "%s브라우저 콘솔 SEVERE %d건 (고유 %d종)\n%s" % (head, len(errs), len(uniq), detail)
    if soft:
        print("  [WARN] " + text)
        return len(errs)
    raise AssertionError(text)


def make_options(webdriver):
    """콘솔 로깅이 켜진 EdgeOptions 생성 헬퍼."""
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.set_capability("goog:loggingPrefs", {"browser": "ALL"})
    return opts
