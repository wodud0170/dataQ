"""셀레니움 Edge 옵션 공통 처리.

기본은 창 없이(headless) 띄운다 — 시험이 도는 동안 Edge 창이 계속 뜨지 않게.
창을 보면서 확인할 때만 DATAQ_HEADED=1 을 주고 돌린다.
"""
import os


def headless(opts):
    if os.environ.get("DATAQ_HEADED") != "1":
        opts.add_argument("--headless=new")
        opts.add_argument("--window-size=1920,1080")
    return opts
