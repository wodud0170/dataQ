"""
67번 데이터 품질 진단 — 풀 시나리오 검증 테스트

대상 모델: TEST_QUAL_MODEL (DM_ID=TESTQUALDM00000000001A)
데이터  : Oracle XE / TESTUSER 의 TB_TEST_MEMBER/ORDER/PRODUCT (139 행)
룰      : 16건 (NOT_NULL/RANGE/LENGTH/REGEX/ENUM/UNIQUE/REFERENCE/COMPARE)

시나리오:
  A. 모델/룰 메타 사전 검증 (PostgreSQL via API)
  B. 값 진단(VALUE) 풀스캔 → 컬럼별 통계 적재 검증 (Member 8 + Order 8 + Product 6 = 22 컬럼)
  C. 룰 진단(RULE) 풀스캔 → 16개 룰 결과 적재
  D. **예상 위반 vs 실제 위반** 비교 (룰별 PASS/FAIL 출력)
  E. 결과 요약 + 정리(룰/이력 보존, 데이터 보존)
"""
import base64
import json
import sys
import time
import traceback

import requests

BASE = "http://localhost:28091"
DM_ID = "TESTQUALDM00000000001A"

# 룰별 예상 위반 (README §룰 16개 + 예상 위반 표 기준)
EXPECTED = {
    "EMAIL_NOT_NULL":       5,
    "EMAIL_REGEX":          3,
    "PHONE_NOT_NULL":       3,
    "PHONE_REGEX":          2,
    "AGE_RANGE":            4,
    "GENDER_ENUM":          3,
    "ORDER_MEMBER_NOT_NULL":2,
    "ORDER_MEMBER_FK":      4,
    "ORDER_AMOUNT_POSITIVE":5,
    "ORDER_STATUS_ENUM":    3,
    "ORDER_DATE_COMPARE":   4,
    "PRODUCT_CODE_LENGTH":  3,
    "PRODUCT_CODE_UNIQUE":  4,
    "PRODUCT_NAME_NOT_NULL":2,
    "PRODUCT_PRICE_POSITIVE":3,
    "PRODUCT_CATEGORY_ENUM":2,
}

results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def login(s, user="space", pw="123"):
    enc = base64.b64encode(pw.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200


state = {}


def main():
    s = requests.Session()
    if not step("0. 관리자 로그인", lambda: login(s)):
        return

    # ---------- A. 메타 사전 검증 ----------
    def _verify_meta():
        r = s.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        models = r.json() or []
        target = next((m for m in models if m.get("dataModelId") == DM_ID), None)
        assert target, f"모델 {DM_ID} 미발견 — 메타 SQL 미적용 의심"
        print(f"  모델: {target.get('dataModelNm')} dsId={target.get('dataModelDsId')[:8]}.. type={target.get('modelType')}")

        r2 = s.post(BASE + "/api/qual/rule/list", json={"dmId": DM_ID, "useYn": "Y"}, timeout=10)
        rules = r2.json() or []
        assert len(rules) >= 10, f"룰 10+ 기대, 실제 {len(rules)} (70번 시드 후 14개 기준)"
        rule_names = sorted(r["ruleNm"] for r in rules)
        print(f"  룰 {len(rules)} 건: {rule_names[:5]} ...")

    if not step("A. 모델 + 룰 메타 사전 검증", _verify_meta):
        return

    # ---------- B. 값 진단 (VALUE) ----------
    def _value_diag():
        r = s.post(BASE + "/api/qual/value/run",
                   json={"dataModelId": DM_ID, "sampleRate": 100}, timeout=30)
        assert r.json().get("resultCode") == 200, f"value run 실패: {r.json()}"
        diag_id = r.json().get("contents")
        state["value_diag_id"] = diag_id
        print(f"  diagId={diag_id[:12]}..")

        # DONE/ERROR 폴링
        deadline = time.time() + 180
        last = None
        while time.time() < deadline:
            r2 = s.get(BASE + f"/api/qual/value/history/{diag_id}", timeout=10)
            h = r2.json() or {}
            last = h.get("status")
            print(f"    polling status={last}")
            if last in ("DONE", "ERROR"):
                state["value_history"] = h
                break
            time.sleep(5)
        assert last == "DONE", f"VALUE 진단 마감 실패 (status={last}, msg={(state.get('value_history') or {}).get('errorMsg')})"
        print(f"  totalCols={state['value_history'].get('totalCols')}")

        # 결과 조회 (테이블 3개)
        r3 = s.get(BASE + "/api/qual/value/result", params={"dataModelId": DM_ID}, timeout=10)
        profiles = r3.json() or []
        print(f"  profile 행수={len(profiles)}")
        # 22 컬럼 기대
        assert len(profiles) >= 22, f"프로파일 결과 22 기대, 실제 {len(profiles)}"

        # MEMBER.EMAIL 통계 검증 — 55 행 / NULL 5 / 형식 위반은 통계로 안 잡힘
        email = next((p for p in profiles
                      if p.get("objNm") == "TB_TEST_MEMBER" and p.get("attrNm") == "EMAIL"), None)
        assert email, "MEMBER.EMAIL 프로파일 미발견"
        print(f"  EMAIL: total={email.get('totalCnt')} null={email.get('nullCnt')} distinct={email.get('distinctCnt')}")
        assert email.get("totalCnt") == 55, f"EMAIL total=55 기대, 실제 {email.get('totalCnt')}"
        assert email.get("nullCnt")  == 5,  f"EMAIL null=5 기대, 실제 {email.get('nullCnt')}"

        # MEMBER.AGE — min=-10 max=999 기대
        age = next((p for p in profiles
                    if p.get("objNm") == "TB_TEST_MEMBER" and p.get("attrNm") == "AGE"), None)
        assert age, "MEMBER.AGE 미발견"
        print(f"  AGE: total={age.get('totalCnt')} min={age.get('minVal')} max={age.get('maxVal')}")

    if not step("B. 값 진단(VALUE) 풀스캔 + 통계 검증", _value_diag):
        pass  # 다음 STEP 도 진행

    # ---------- C. 룰 진단 (RULE) ----------
    def _rule_diag():
        r = s.post(BASE + "/api/qual/rule/run",
                   json={"dataModelId": DM_ID, "sampleRate": 100, "incrementalYn": "N"}, timeout=30)
        assert r.json().get("resultCode") == 200, f"rule run 실패: {r.json()}"
        diag_id = r.json().get("contents")
        state["rule_diag_id"] = diag_id
        print(f"  diagId={diag_id[:12]}..")

        # DONE/ERROR 폴링
        deadline = time.time() + 240
        last = None
        result_obj = None
        while time.time() < deadline:
            r2 = s.get(BASE + "/api/qual/rule/result", params={"diagId": diag_id}, timeout=10)
            content = r2.json().get("contents")
            if isinstance(content, str):
                content = json.loads(content)
            h = (content or {}).get("history") or {}
            last = h.get("status")
            print(f"    polling status={last}")
            if last in ("DONE", "ERROR"):
                result_obj = content
                break
            time.sleep(5)
        state["rule_status"] = last
        state["rule_result"] = result_obj
        assert last == "DONE", f"RULE 진단 마감 실패 (status={last}, msg={(result_obj or {}).get('history',{}).get('errorMsg')})"

    if not step("C. 룰 진단(RULE) 풀스캔 + 마감", _rule_diag):
        # rule_result 없으면 D 건너뜀
        pass

    # ---------- D. 예상 vs 실제 ----------
    def _verify_violations():
        c = state.get("rule_result") or {}
        results_list = c.get("results") or []
        print(f"  결과 행수={len(results_list)} (룰 16 + 도메인 전개 포함)")

        # 룰명 → 위반 카운트 매핑
        actual = {}
        errors = {}
        for r in results_list:
            nm = r.get("ruleNm")
            actual[nm] = actual.get(nm, 0) + (r.get("violationCnt") or 0)
            if r.get("errorMsg"):
                errors.setdefault(nm, []).append(r.get("errorMsg"))

        print()
        print(f"  {'룰명':30s} {'예상':>5s} {'실제':>5s}  결과   에러")
        print(f"  {'-'*30} {'-'*5} {'-'*5}  ----   ----")
        ok = 0
        ko = 0
        for nm, exp in EXPECTED.items():
            act = actual.get(nm)
            err = errors.get(nm, [])
            if act is None:
                ko += 1
                print(f"  {nm:30s} {exp:5d} {'?':>5s}  MISS  {err[0][:40] if err else '결과없음'}")
                continue
            mark = "PASS" if act == exp else "FAIL"
            if act == exp:
                ok += 1
            else:
                ko += 1
            err_str = (err[0][:40] + "...") if err else ""
            print(f"  {nm:30s} {exp:5d} {act:5d}  {mark}  {err_str}")
        print()
        print(f"  ► 매칭 {ok}/16, 불일치 {ko}/16")

        # 합산 확인
        total_actual = sum(actual.values())
        total_expected = sum(EXPECTED.values())
        print(f"  ► 합산 위반: 기대 {total_expected} / 실제 {total_actual}")
        state["rule_match_ok"] = ok
        state["rule_match_ko"] = ko

    step("D. 예상 위반 vs 실제 위반 비교", _verify_violations)

    # ---------- E. 정리 ----------
    def _summary():
        ok = state.get("rule_match_ok", 0)
        ko = state.get("rule_match_ko", 16)
        v = state.get("value_history") or {}
        print(f"  값 진단(VALUE): status={v.get('status')} totalCols={v.get('totalCols')}")
        print(f"  룰 진단(RULE) : status={state.get('rule_status')}")
        print(f"  룰 매칭: {ok}/16 정확, {ko}/16 불일치")
        print(f"  → 데이터/룰/이력은 보존. 정리 시 99_qual_test_cleanup.sql 사용.")

    step("E. 종합 요약", _summary)


if __name__ == "__main__":
    main()
    p = sum(1 for _, st, _ in results if st == "PASS")
    f = sum(1 for _, st, _ in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for name, st, _ in results:
        print(f"  [{st}] {name}")
    sys.exit(0 if f == 0 else 1)
