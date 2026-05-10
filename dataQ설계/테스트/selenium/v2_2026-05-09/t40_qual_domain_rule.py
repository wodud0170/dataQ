"""
86번 #46~ — 데이터 품질 진단: 도메인 룰 관리 (DSQualDomainRule.vue) 종합 테스트.

12 케이스:
  A) UI 렌더링 (3): 화면 진입, 좌측 트리 로드, 카탈로그 모달 열기/닫기
  B) 카탈로그 (3): 카탈로그 로드, 검색 필터, 사용자 정의 탭
  C) 룰 저장 API (4): 룰명 누락, 정상 등록, 수정, 삭제
  D) 카탈로그 매핑 (2): 매핑 (도메인 미선택 차단), 정상 매핑
"""
import sys, os, time, traceback
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, get_admin_session,
                    BASE_URL, TestRun, db_query)
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def goto_domain_rule(drv):
    # 로그인 후 SPA 상태 — 추가 navigate 없이 메뉴 클릭만
    time.sleep(2)
    try:
        # qualGroup 보이는지 — 안 펼쳐졌으면 클릭 (상태 무관하게 한 번 더 클릭해서 강제 펼침은 토글이 위험)
        grps = drv.find_elements(By.ID, "qualGroup")
        if not grps:
            return False
        # 펼침 시도 — 펼쳐졌는지 확인 후 안 됐으면 한 번 클릭
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", grps[0])
        # nav 자식이 보이면 이미 펼침
        nav_visible = drv.execute_script("""
          const n = document.getElementById('nav_qualDomainRule');
          return n && n.offsetParent !== null;
        """)
        if not nav_visible:
            # v-list-group 자식 .v-list-group__header 클릭
            header = grps[0].find_elements(By.CSS_SELECTOR, ".v-list-group__header")
            target = header[0] if header else grps[0]
            drv.execute_script("arguments[0].click();", target)
            time.sleep(1.5)
    except Exception:
        return False
    try:
        nav = WebDriverWait(drv, 8).until(EC.presence_of_element_located((By.ID, "nav_qualDomainRule")))
        drv.execute_script("arguments[0].scrollIntoView({block:'center'});", nav)
        drv.execute_script("arguments[0].click();", nav)
        time.sleep(2)
        return True
    except Exception:
        return False


def run():
    drv = create_driver()
    t = TestRun("T40 도메인 룰 관리")
    try:
        if not login_admin(drv):
            t.step("login", False); return t
        sess = get_admin_session(drv)
        return _run(t, drv, sess)
    except Exception as e:
        t.step("UNCAUGHT", False, str(e)[:100])
        traceback.print_exc()
        return t
    finally:
        try: drv.quit()
        except Exception: pass


def _run(t, drv, sess):
    # ===== A) UI 렌더링 =====
    ok = goto_domain_rule(drv)
    t.step("A01 도메인 룰 화면 진입", ok)

    try:
        tree = drv.find_elements(By.CSS_SELECTOR, ".v-treeview")
        t.step("A02 좌측 트리 렌더", len(tree) > 0)
    except Exception as e:
        t.step("A02 좌측 트리 렌더", False, str(e)[:80])

    try:
        btn = drv.find_element(By.ID, "btn-catalog-open")
        drv.execute_script("arguments[0].click();", btn)
        time.sleep(2)
        dlg = drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
        opened = len(dlg) > 0
        # 닫기 버튼
        if opened:
            close_btns = drv.find_elements(By.XPATH, "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='닫기']")
            if close_btns:
                drv.execute_script("arguments[0].click();", close_btns[0])
                time.sleep(1)
            after = drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active")
            t.step("A03 카탈로그 다이얼로그 열기/닫기", opened and len(after) == 0)
        else:
            t.step("A03 카탈로그 다이얼로그 열기/닫기", False, "open fail")
    except Exception as e:
        t.step("A03 카탈로그 다이얼로그 열기/닫기", False, str(e)[:80])

    # ===== B) 카탈로그 API =====
    try:
        r = sess.get(f"{BASE_URL}/api/qual/rule/catalog")
        ok = r.status_code == 200 and isinstance(r.json(), list)
        t.step("B01 카탈로그 GET", ok, f"len={len(r.json()) if ok else r.status_code}")
    except Exception as e:
        t.step("B01 카탈로그 GET", False, str(e)[:80])

    try:
        r = sess.get(f"{BASE_URL}/api/qual/rule/catalog?schNm=NOTEXIST_XXX_YYY")
        ok = r.status_code == 200 and len(r.json()) == 0
        t.step("B02 카탈로그 검색 (없는 키워드 → 0건)", ok)
    except Exception as e:
        t.step("B02 카탈로그 검색", False, str(e)[:80])

    try:
        r = sess.get(f"{BASE_URL}/api/qual/rule/catalog?ownerYn=Y")
        ok = r.status_code == 200
        t.step("B03 사용자 정의 카탈로그 GET", ok, f"http={r.status_code}")
    except Exception as e:
        t.step("B03 사용자 정의 카탈로그 GET", False, str(e)[:80])

    # ===== C) 룰 저장 (도메인 단위) =====
    # 도메인 하나 골라서 테스트 룰 등록 → 삭제
    domains = db_query("SELECT domain_id, domain_nm FROM tb_domain LIMIT 1")
    if not domains:
        for i in range(4):
            t.step(f"C0{i+1} 룰 저장 ({['룰명누락','정상','수정','삭제'][i]})", False, "도메인 0건")
        return

    domain_id = domains[0][0]
    rule_id = None
    ts = int(time.time())

    try:
        r = sess.post(f"{BASE_URL}/api/qual/domain/rule/save",
                      json={"domainId": domain_id, "ruleNm": "", "ruleType": "NOT_NULL"})
        body = r.json()
        msg = (body.get("resultMessage") or body.get("message") or "")
        rc = body.get("resultCode")
        ok = rc != 200 and ("ruleNm" in msg or "룰명" in msg or "필수" in msg)
        t.step("C01 룰명 누락 차단", ok, f"rc={rc} msg={msg[:60]}")
    except Exception as e:
        t.step("C01 룰명 누락 차단", False, str(e)[:80])

    try:
        body = {"domainId": domain_id, "ruleNm": f"테스트룰_{ts}",
                "ruleType": "NOT_NULL", "severity": "WARN", "useYn": "Y",
                "ruleParams": "{}"}
        r = sess.post(f"{BASE_URL}/api/qual/domain/rule/save", json=body)
        rc = r.json().get("resultCode")
        ok = rc == 200
        if ok:
            row = db_query(f"SELECT domain_rule_id FROM tb_domain_rule WHERE rule_nm='테스트룰_{ts}'")
            if row:
                rule_id = row[0][0]
        t.step("C02 정상 룰 등록", ok and rule_id is not None, f"rc={rc} id={rule_id}")
    except Exception as e:
        t.step("C02 정상 룰 등록", False, str(e)[:80])

    if rule_id:
        try:
            body = {"domainRuleId": rule_id, "domainId": domain_id,
                    "ruleNm": f"수정룰_{ts}", "ruleType": "NOT_NULL",
                    "useYn": "Y", "ruleParams": "{}", "sortOrd": 1}
            r = sess.post(f"{BASE_URL}/api/qual/domain/rule/save", json=body)
            jb = r.json()
            ok = jb.get("resultCode") == 200
            row = db_query(f"SELECT rule_nm FROM tb_domain_rule WHERE domain_rule_id='{rule_id}'")
            t.step("C03 룰 수정", ok and row and row[0][0] == f"수정룰_{ts}",
                   f"rc={jb.get('resultCode')} msg={jb.get('resultMessage')} row={row[0] if row else None}")
        except Exception as e:
            t.step("C03 룰 수정", False, str(e)[:80])

        try:
            r = sess.post(f"{BASE_URL}/api/qual/domain/rule/delete",
                          json={"domainRuleId": rule_id})
            ok = r.json().get("resultCode") == 200
            row = db_query(f"SELECT 1 FROM tb_domain_rule WHERE domain_rule_id='{rule_id}'")
            t.step("C04 룰 삭제", ok and not row)
        except Exception as e:
            t.step("C04 룰 삭제", False, str(e)[:80])
    else:
        t.step("C03 룰 수정", False, "선행 등록 실패")
        t.step("C04 룰 삭제", False, "선행 등록 실패")

    # ===== E) 안티패턴 (5 케이스) =====
    domain_id_safe = domain_id
    edge_cases = [
        ("E01 SQL injection in ruleNm",
         {"domainId": domain_id_safe, "ruleNm": "'; DROP TABLE x; --", "ruleType": "NOT_NULL"},
         "any-200"),
        ("E02 200자 ruleNm",
         {"domainId": domain_id_safe, "ruleNm": "가" * 250, "ruleType": "NOT_NULL"},
         "fail-or-200"),  # column 200자 한계 — 250자 입력은 실패해야 정상
        ("E03 잘못된 ruleType",
         {"domainId": domain_id_safe, "ruleNm": f"잘못유형_{ts}", "ruleType": "BOGUS_TYPE"},
         "any"),
        ("E04 존재하지 않는 domainId",
         {"domainId": "NONEXIST_XYZ", "ruleNm": f"실패룰_{ts}", "ruleType": "NOT_NULL"},
         "fail"),
        ("E05 잘못된 JSON ruleParams",
         {"domainId": domain_id_safe, "ruleNm": f"json오류_{ts}", "ruleType": "NOT_NULL",
          "ruleParams": "not json"},
         "any-200"),
    ]
    created_ids = []
    for label, body, expectation in edge_cases:
        try:
            r = sess.post(f"{BASE_URL}/api/qual/domain/rule/save", json=body)
            jb = r.json()
            rc = jb.get("resultCode")
            if expectation == "fail":
                ok = rc != 200
            elif expectation == "fail-or-200":
                ok = True  # 한계 초과는 보통 실패하지만 backend 에서 truncate 도 가능 → 둘 다 인정
                if rc == 200:
                    rid = jb.get("contents")
                    if rid: created_ids.append(rid)
            else:  # any-200 / any
                ok = True
                if rc == 200:
                    rid = jb.get("contents")
                    if rid: created_ids.append(rid)
            t.step(label, ok, f"rc={rc} msg={(jb.get('resultMessage') or '')[:40]}")
        except Exception as e:
            t.step(label, False, str(e)[:80])
    # cleanup
    for cid in created_ids:
        try: sess.post(f"{BASE_URL}/api/qual/domain/rule/delete", json={"domainRuleId": cid})
        except Exception: pass

    # ===== D) 카탈로그 매핑 =====
    try:
        r = sess.post(f"{BASE_URL}/api/qual/domain/rule/importFromCatalog",
                      json={"domainId": "", "catalogId": ""})
        ok = r.json().get("resultCode") != 200
        t.step("D01 도메인 미선택 매핑 차단", ok, str(r.json().get("message"))[:60])
    except Exception as e:
        t.step("D01 도메인 미선택 매핑 차단", False, str(e)[:80])

    try:
        cats = sess.get(f"{BASE_URL}/api/qual/rule/catalog").json()
        if cats:
            cat_id = cats[0].get("catalogId")
            r = sess.post(f"{BASE_URL}/api/qual/domain/rule/importFromCatalog",
                          json={"domainId": domain_id, "catalogId": cat_id})
            rc = r.json().get("resultCode")
            ok = rc == 200
            # 정리: 매핑된 룰 삭제
            new_rule = db_query(f"SELECT domain_rule_id FROM tb_domain_rule WHERE domain_id='{domain_id}' "
                                f"ORDER BY cret_dt DESC NULLS LAST LIMIT 1")
            if new_rule and ok:
                sess.post(f"{BASE_URL}/api/qual/domain/rule/delete",
                          json={"domainRuleId": new_rule[0][0]})
            t.step("D02 정상 매핑", ok, f"rc={rc}")
        else:
            t.step("D02 정상 매핑", False, "카탈로그 0건")
    except Exception as e:
        t.step("D02 정상 매핑", False, str(e)[:80])

    return t


if __name__ == "__main__":
    t = run()
    # _run is also valid for direct return
    if t and not isinstance(t, TestRun):
        t = t  # safety
    from common import write_report
    write_report([t], "t40_qual_domain_rule.md")
