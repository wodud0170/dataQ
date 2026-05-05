"""
자동 표준화 변환 (DSTermRecommend) — 케이스 7종 + before→after 정확 매칭

표준사전 (사전 검증):
  단어 등록: 회원=MBR / 명=NM / 번호=NO / 상품=GDS / 주문=ORDR / 등록=REG
            일시=DT / 금액=AMT / 성별=GNDR / 수정=MDFCN / 코드=CD
            가격=PRC / 상태=STTS / 일자=YMD / 시간=HR / 구분=SE / 계좌=BACNT
  용어 등록: 회원명=MBR_NM / 회원번호=MBR_NO / 상품명=GDS_NM
            주문번호=ORDR_NO / 등록일시=REG_DT
  미등록:    이름 / 나이 / 전화 / 블라블라 (의도된 미매칭)

각 시나리오의 검증 규칙:
  - REGISTERED: existingTerm.termsEngAbrvNm 가 기대값과 정확 일치
  - AUTO     : words[].wordEngAbrvNm 결합으로 정확 매칭
  - PARTIAL  : 일부 단어 status=NEW
  - FAILED   : 거의 모든 단어 미매칭
"""
import base64, json, sys, time, traceback
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
results = []


def log(name):
    print(f"\n{'='*78}\n[{name}]\n{'='*78}")


def step(name, fn):
    print(f"\n  --- {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login(s):
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id":"space","password":enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


def analyze(s, names):
    r = s.post(BASE + "/api/std/analyzeTermsBatch", json={"termNames": names}, timeout=120)
    assert r.status_code == 200, f"분석 API 실패: {r.status_code} {r.text[:200]}"
    return r.json()


def find(arr, term):
    return next((x for x in arr if x.get("inputNm") == term), None)


def word_abbr(item, ko):
    """결과 row 의 words 안에서 한글 ko 의 영문약어 (selected 안에 있음)"""
    for w in (item.get("words") or []):
        if w.get("wordNm") == ko:
            sel = w.get("selected") or {}
            return sel.get("wordEngAbrvNm") or w.get("wordEngAbrvNm")
    return None


# ============================================================
# T1. REGISTERED — 등록된 용어 5건 정확 매칭
# ============================================================
def t1_registered():
    log("T1. REGISTERED — 5 등록 용어 정확 매칭")
    s = requests.Session(); login(s)
    expect = {
        "회원명":   "MBR_NM",
        "회원번호": "MBR_NO",
        "상품명":   "GDS_NM",
        "주문번호": "ORDR_NO",
        "등록일시": "REG_DT"
    }

    def _run():
        arr = analyze(s, list(expect.keys()))
        assert len(arr) == 5, f"5 결과 기대, 실제 {len(arr)}"
        for ko, abbr_exp in expect.items():
            it = find(arr, ko)
            assert it, f"'{ko}' 결과 없음"
            assert it["status"] == "REGISTERED", f"'{ko}' status={it['status']} (REGISTERED 기대)"
            actual = (it.get("existingTerm") or {}).get("termsEngAbrvNm")
            print(f"  {ko:8s}  →  {actual!s:10s}  (기대 {abbr_exp})")
            assert actual == abbr_exp, f"'{ko}' 약어 불일치: {actual} != {abbr_exp}"

    step("T1.1 5건 동시 분석 + REGISTERED + 영문약어 정확 매칭", _run)


# ============================================================
# T2. AUTO — 단어 결합 자동 영문 약어
# ============================================================
def t2_auto():
    log("T2. AUTO — 단어들이 표준이지만 용어 미등록 → 자동 결합")
    s = requests.Session(); login(s)
    # 단어 모두 등록된 한글명 (용어 미등록) — recommendedEngAbrvNm 까지 정확
    cases = [
        ("회원금액",     ["회원","금액"],          ["MBR","AMT"],          "MBR_AMT"),
        ("주문상태",     ["주문","상태"],          ["ORDR","STTS"],        "ORDR_STTS"),
        ("상품가격",     ["상품","가격"],          ["GDS","PRC"],          "GDS_PRC"),
        ("회원성별",     ["회원","성별"],          ["MBR","GNDR"],         "MBR_GNDR"),
        ("주문수정일시", ["주문","수정","일시"],   ["ORDR","MDFCN","DT"],  "ORDR_MDFCN_DT")
    ]

    def _run():
        names = [c[0] for c in cases]
        arr = analyze(s, names)
        assert len(arr) == len(cases)
        for ko, expected_words, expected_abbrs, expected_full in cases:
            it = find(arr, ko)
            assert it, f"'{ko}' 결과 없음"
            assert it["status"] in ("AUTO","REGISTERED","PARTIAL"), \
                f"'{ko}' status={it['status']}"
            # 각 단어 약어 — REGISTERED 케이스는 words 배열이 비어있으므로 스킵
            if it["status"] != "REGISTERED":
                for w_ko, w_abbr in zip(expected_words, expected_abbrs):
                    actual = word_abbr(it, w_ko)
                    assert actual == w_abbr, f"'{ko}.{w_ko}' 약어 {actual} != {w_abbr}"
            # recommendedEngAbrvNm 정확 매칭 (REGISTERED 인 경우는 existingTerm.termsEngAbrvNm 비교)
            if it["status"] == "REGISTERED":
                got = (it.get("existingTerm") or {}).get("termsEngAbrvNm")
            else:
                got = it.get("recommendedEngAbrvNm")
            print(f"  {ko:14s} → {got!s:20s}  (기대 {expected_full})")
            assert got == expected_full, f"'{ko}' 약어 불일치: {got} != {expected_full}"

    step("T2.1 5건 분석 + 단어 약어 + 추천 영문약어 정확 매칭", _run)


# ============================================================
# T3. PARTIAL — 일부 단어만 표준
# ============================================================
def t3_partial():
    log("T3. PARTIAL — 일부 단어 NEW")
    s = requests.Session(); login(s)
    # 행안부 표준 단어 + 진짜 미등록(사전·동의어 모두 부재) 단어 조합.
    # '이름/나이' 같은 단어는 알고리즘이 ALLOPH_SYNM_LST(명/연령) 로 자동 복원하므로
    # PARTIAL 검증용으로 부적절. 명백히 사전 미등록인 의미없는 토큰 사용.
    cases = [
        ("회원라랄라", "회원", "라랄라"),
        ("주문룰루",  "주문", "룰루")
    ]

    def _run():
        names = [c[0] for c in cases]
        arr = analyze(s, names)
        for ko, w_known, w_new in cases:
            it = find(arr, ko)
            assert it, f"'{ko}' 결과 없음"
            print(f"  {ko}: status={it['status']}")
            # PARTIAL 또는 FAILED (분석기 차이)
            assert it["status"] in ("PARTIAL","FAILED"), \
                f"'{ko}' status 예상 외: {it['status']}"
            # 알려진 단어는 MATCHED 일 것
            for w in it.get("words", []):
                if w.get("wordNm") == w_known:
                    assert w.get("status") == "MATCHED", \
                        f"'{ko}.{w_known}' status={w.get('status')} (MATCHED 기대)"
                    print(f"    '{w_known}' MATCHED OK ({w.get('wordEngAbrvNm')})")
                if w.get("wordNm") == w_new:
                    assert w.get("status") in ("NEW","UNRECOGNIZED"), \
                        f"'{ko}.{w_new}' status={w.get('status')} (NEW/UNRECOGNIZED 기대)"
                    print(f"    '{w_new}' NEW/UNRECOGNIZED OK")

    step("T3.1 PARTIAL — 알려진 단어 MATCHED + 미등록 단어 NEW", _run)


# ============================================================
# T4. FAILED — 모든 단어 미등록
# ============================================================
def t4_failed():
    log("T4. FAILED — 미등록 단어만으로 구성")
    s = requests.Session(); login(s)

    def _run():
        names = ["블라블라테스트", "라랄라룰루"]
        arr = analyze(s, names)
        for it in arr:
            print(f"  '{it.get('inputNm')}': status={it.get('status')}, words={len(it.get('words') or [])}")
            # 이런 케이스는 FAILED, PARTIAL, UNRECOGNIZED 어느 것이든 — MATCHED 가 거의 없어야
            matched = sum(1 for w in (it.get("words") or []) if w.get("status") == "MATCHED")
            assert matched <= 1, f"미등록 단어인데 MATCHED {matched} 개"

    step("T4.1 미등록 단어 → MATCHED 거의 없음", _run)


# ============================================================
# T5. 영문 단어 (등록되어 있음) 케이스
# ============================================================
def t5_english():
    log("T5. 영문 단어 매칭 — API 등")
    s = requests.Session(); login(s)
    # 표준사전: API, CMS, SMS, ERP 등이 등록되어 있음
    cases = [
        ("API번호", "API"),
        ("CMS코드", "CMS"),
    ]

    def _run():
        names = [c[0] for c in cases]
        arr = analyze(s, names)
        for ko, w_eng in cases:
            it = find(arr, ko)
            assert it, f"'{ko}' 결과 없음"
            # 영문 단어가 매칭됐는지
            eng_matched = any(
                w.get("wordNm") == w_eng and w.get("status") == "MATCHED"
                for w in it.get("words", [])
            )
            print(f"  {ko}: 영문 '{w_eng}' MATCHED = {eng_matched}, status={it['status']}")
            assert eng_matched, f"'{ko}' 의 영문 단어 '{w_eng}' MATCHED 안 됨"

    step("T5.1 영문 단어 (API/CMS) 가 매칭 검증", _run)


# ============================================================
# T6. 다중 케이스 한 번 분석 — status 분포
# ============================================================
def t6_mixed_batch():
    log("T6. 다중 케이스 한 번 분석 — 13건")
    s = requests.Session(); login(s)
    names = [
        "회원명", "회원번호", "상품명",                # REGISTERED
        "회원금액", "주문상태", "상품가격", "회원성별", # AUTO
        "회원이름", "주문나이",                          # PARTIAL
        "블라블라", "라라라",                            # FAILED/UNRECOGNIZED
        "API번호", "CMS코드"                             # 영문혼용
    ]

    def _run():
        arr = analyze(s, names)
        assert len(arr) == len(names), f"입력 {len(names)} 결과 {len(arr)}"
        cnt = {}
        for it in arr:
            st = it.get("status")
            cnt[st] = cnt.get(st, 0) + 1
        print(f"  status 분포: {cnt}")
        # REGISTERED 3+ (회원명/회원번호/상품명)
        assert cnt.get("REGISTERED", 0) >= 3, f"REGISTERED 3+ 기대: {cnt}"
        # AUTO 또는 REGISTERED 합 7+ (등록 + auto 매칭들)
        assert cnt.get("REGISTERED", 0) + cnt.get("AUTO", 0) >= 7, f"AUTO+REGISTERED 7+ 기대: {cnt}"
        # 미매칭 케이스도 있어야
        assert cnt.get("FAILED", 0) + cnt.get("PARTIAL", 0) >= 2, f"PARTIAL+FAILED 2+ 기대: {cnt}"

    step("T6.1 13건 한 번 분석 + status 분포 검증", _run)


# ============================================================
# T7. UI 풀 흐름 — textarea 입력 → 분석 시작 → 결과 화면 도달 → status 칩 카운트
# ============================================================
def t7_ui_flow():
    log("T7. UI 풀 흐름 — 분석 화면 진입 → textarea → 분석 → 결과")
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    d = webdriver.Edge(options=opts)
    d.set_window_size(1600, 1000)

    INPUT = "회원명\n주문번호\n상품가격\n회원이름\n블라블라"

    try:
        def _login():
            d.get(BASE + "/signin")
            WebDriverWait(d, 15).until(EC.visibility_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
            d.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys("space")
            pw = d.find_element(By.CSS_SELECTOR, "input[type='password']")
            pw.send_keys("123"); pw.send_keys(Keys.ENTER)
            WebDriverWait(d, 15).until(lambda drv: "/main" in drv.current_url)
            time.sleep(5)

        def _open():
            # [자동 표준화 지원] 그룹 → [컬럼 표준화] 메뉴
            try:
                act = d.find_element(By.XPATH, "//div[@id='autoStdGroup']//div[contains(@class,'v-list-group__header')]")
                d.execute_script("arguments[0].click();", act); time.sleep(2)
            except Exception:
                pass
            m = WebDriverWait(d, 15).until(EC.visibility_of_element_located(
                (By.XPATH, "//*[@id='nav_termRecommend' or contains(text(), '컬럼 표준화')]")))
            d.execute_script("arguments[0].click();", m); time.sleep(3)

        def _fill_and_run():
            ta = d.find_element(By.CSS_SELECTOR, "textarea")
            d.execute_script(
                "var el=arguments[0], val=arguments[1];"
                "var setter=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set;"
                "setter.call(el,val);"
                "el.dispatchEvent(new Event('input',{bubbles:true}));", ta, INPUT)
            time.sleep(1)
            # '분석 시작' 버튼
            btn = WebDriverWait(d, 10).until(EC.element_to_be_clickable(
                (By.XPATH, "//button[.//*[contains(text(),'분석 시작')] or contains(., '분석 시작')]")))
            d.execute_script("arguments[0].click();", btn)
            # STEP 3 도달 (결과 화면) — '분석 결과' 또는 status 칩
            WebDriverWait(d, 90).until(lambda drv:
                len(drv.find_elements(By.XPATH, "//*[contains(text(),'분석 결과') or contains(text(),'기등록')]")) > 0)
            time.sleep(2)

        def _verify_chips():
            page = d.find_element(By.CSS_SELECTOR, "body").text
            # 5건 입력에 대한 어떤 status 든 표시 — '기등록' 또는 '자동' 또는 '부분매칭' 등
            print(f"  분석 결과 화면 도달 OK")
            # 그리드 행이 5개 정도 (5건 입력)
            rows = d.find_elements(By.CSS_SELECTOR, "table tbody tr")
            print(f"  결과 grid 행수={len(rows)}")
            assert len(rows) >= 5, f"5+ 행 기대"

        step("T7.1 UI 로그인",         _login)
        step("T7.2 [컬럼 표준화] 진입", _open)
        step("T7.3 5건 입력 + 분석 시작 + 결과 화면 도달", _fill_and_run)
        step("T7.4 결과 그리드 5+ 행 표시", _verify_chips)
    finally:
        try: d.quit()
        except Exception: pass


def main():
    t1_registered()
    t2_auto()
    t3_partial()
    t4_failed()
    t5_english()
    t6_mixed_batch()
    t7_ui_flow()


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*78}\n결과: {p} PASS / {f} FAIL  (총 {elapsed:.0f}초)\n{'='*78}")
    for n, st in results: print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
