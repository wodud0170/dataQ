"""
Cubrid 11.2 testdb 환경에서 70번 데이터 품질 진단 시나리오 검증
모델: TEST_CUBRID_MODEL (DM_ID=TESTCUBRIDDM00000000A1)

사전:
  - cubrid-test 컨테이너 (포트 33000) — testdb 의 PUBLIC 스키마에 TB_TEST_*
  - dataq-db 의 ndata.TB_DATA_SOURCE 에 TESTCUBRID 등록 (driver_nm=Cubrid)
  - dataq-db 의 quality.TB_QUAL_RULE/COL_RULE 에 룰 + 매핑 시드

검증 포인트 (Cubrid dialect 막힘 실증):
  V1. listWithLatest API — effective rule 22건 확인
  V2. NOT_NULL/RANGE/LENGTH/ENUM 단순 룰 진단 (Cubrid 호환)
  V3. REGEX 룰 진단 — RuleSqlBuilder CUBRID case (col REGEXP 'pattern')
  V4. REFERENCE 룰 — NOT EXISTS (Cubrid 호환)
  V5. COMPARE 룰 — 컬럼 간 비교
  V6. 값 프로파일링 — 22 컬럼 통계 (Cubrid 의 LENGTH/MIN/MAX/AVG)
"""
import base64, json, sys, time, traceback
import requests

BASE = "http://localhost:28091"
DM_ID = "TESTCUBRIDDM00000000A1"
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def login(s):
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


def run_value(s, targets, sample=100):
    r = s.post(BASE + "/api/qual/value/runColumns",
               json={"dataModelId": DM_ID, "sampleRate": sample, "targets": targets}, timeout=30)
    diag = r.json().get("contents")
    deadline = time.time() + 90
    while time.time() < deadline:
        r2 = s.get(BASE + f"/api/qual/value/history/{diag}", timeout=10)
        h = r2.json() or {}
        if h.get("status") in ("DONE", "ERROR"): break
        time.sleep(2)
    return h


def run_rule(s, targets, sample=100):
    r = s.post(BASE + "/api/qual/rule/runColumns",
               json={"dataModelId": DM_ID, "sampleRate": sample, "incrementalYn": "N", "targets": targets},
               timeout=30)
    diag = r.json().get("contents")
    deadline = time.time() + 90
    while time.time() < deadline:
        r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag}, timeout=10)
        c = r2.json().get("contents")
        if isinstance(c, str): c = json.loads(c)
        h = (c or {}).get("history") or {}
        if h.get("status") in ("DONE", "ERROR"): break
        time.sleep(2)
    return h, c


def get_col(s, obj, attr):
    r = s.get(BASE + "/api/qual/colrule/listWithLatest",
              params={"dmId": DM_ID, "objNm": obj}, timeout=10)
    return next((x for x in (r.json() or []) if x.get("attrNm") == attr), None)


def main():
    s = requests.Session(); login(s)

    # V1
    def _v1():
        r = s.get(BASE + "/api/qual/colrule/list", params={"dmId": DM_ID}, timeout=10)
        rows = r.json() or []
        print(f"  effective rule rows = {len(rows)}")
        assert len(rows) >= 22, f"22+ 기대"
    step("V1. effective rule 매퍼 (Cubrid 모델)", _v1)

    # V2 NOT_NULL/RANGE/LENGTH/ENUM
    def _v2():
        targets = [
            {"objNm":"TB_TEST_PRODUCT","attrNm":"NAME"},     # NOT_NULL
            {"objNm":"TB_TEST_MEMBER", "attrNm":"AGE"},      # RANGE
            {"objNm":"TB_TEST_PRODUCT","attrNm":"PRODUCT_CODE"}, # LENGTH
            {"objNm":"TB_TEST_MEMBER", "attrNm":"GENDER"}    # ENUM
        ]
        h, c = run_rule(s, targets)
        assert h.get("status") == "DONE", f"마감 실패: {h}"
        for t in targets:
            info = get_col(s, t["objNm"], t["attrNm"])
            print(f"  {t['objNm']}.{t['attrNm']:13s}  적합률 {info.get('ruleConformRate')}%")
            assert info.get("ruleConformRate") is not None
    step("V2. 단순 룰 (NOT_NULL/RANGE/LENGTH/ENUM) — Cubrid dialect", _v2)

    # V3 REGEX
    def _v3():
        targets = [
            {"objNm":"TB_TEST_MEMBER","attrNm":"EMAIL"},  # REGEX 도메인 룰
            {"objNm":"TB_TEST_MEMBER","attrNm":"PHONE"}   # REGEX 도메인 룰
        ]
        h, c = run_rule(s, targets)
        if h.get("status") != "DONE":
            print(f"  ERROR msg = {h.get('errorMsg')}")
        assert h.get("status") == "DONE", f"REGEX 마감 실패 — Cubrid REGEXP 호환성 의심: {h.get('errorMsg', '')[:200]}"
        # 결과 정확성
        for t in targets:
            info = get_col(s, t["objNm"], t["attrNm"])
            print(f"  {t['objNm']}.{t['attrNm']:6s} 적합률 {info.get('ruleConformRate')}% (위반 {info.get('ruleViolation')}/{info.get('ruleTotal')})")
    step("V3. REGEX 룰 (이메일/전화) — Cubrid REGEXP 연산자", _v3)

    # V4 REFERENCE
    def _v4():
        h, c = run_rule(s, [{"objNm":"TB_TEST_ORDER","attrNm":"MEMBER_ID"}])
        if h.get("status") != "DONE":
            print(f"  ERROR msg = {h.get('errorMsg')}")
        assert h.get("status") == "DONE"
        info = get_col(s, "TB_TEST_ORDER", "MEMBER_ID")
        viol = int(info.get("ruleViolation"))
        # 미등록 회원 4 건 + NULL 2 (NULL 제외) → 4
        print(f"  REFERENCE 위반 = {viol} (기대 4)")
        assert viol == 4, f"REFERENCE 위반 4 기대, 실제 {viol}"
    step("V4. REFERENCE 룰 (NOT EXISTS) — Cubrid", _v4)

    # V5 COMPARE
    def _v5():
        h, c = run_rule(s, [{"objNm":"TB_TEST_ORDER","attrNm":"END_DT"}])
        assert h.get("status") == "DONE"
        info = get_col(s, "TB_TEST_ORDER", "END_DT")
        viol = int(info.get("ruleViolation"))
        print(f"  COMPARE 위반 = {viol} (기대 4)")
        assert viol == 4, f"COMPARE 위반 4 기대, 실제 {viol}"
    step("V5. COMPARE 룰 (END>=START) — Cubrid 컬럼간 비교", _v5)

    # V6 값 프로파일링
    def _v6():
        targets = [
            {"objNm":"TB_TEST_MEMBER","attrNm":"EMAIL"},
            {"objNm":"TB_TEST_MEMBER","attrNm":"AGE"}
        ]
        h = run_value(s, targets)
        if h.get("status") != "DONE":
            print(f"  ERROR msg = {h.get('errorMsg')}")
        assert h.get("status") == "DONE", f"값 프로파일 마감 실패: {h.get('errorMsg', '')[:200]}"
        info = get_col(s, "TB_TEST_MEMBER", "EMAIL")
        print(f"  EMAIL: total={info.get('profTotal')} null={info.get('profNull')} distinct={info.get('profDistinct')}")
        assert info.get("profTotal") == 55, f"EMAIL total=55 기대"
        assert info.get("profNull") == 5
    step("V6. 값 프로파일링 (LENGTH/MIN/MAX/AVG) — Cubrid", _v6)


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'='*60}")
    for n, st in results: print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
