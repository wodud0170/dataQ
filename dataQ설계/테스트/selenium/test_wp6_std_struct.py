"""
104번 WP6 — 표준사전 구조 보강 (운영 옵션·도메인 그룹 다단계·코드 유형·옛 표준 매핑)

검증 (시험 전용 사전을 API·SQL 로 만들고 화면은 그 사전으로 연다 — 사전 선택은 세션스토리지):
  D1. 관리 > 표준사전 관리 — 시험 사전 수정 창에서 '표준 대문자' 끄고 저장 → 사전 옵션 N
  D2. 도메인 그룹 — 등록 창에서 상위 그룹을 골라 등록 → 상위 그룹 저장, 목록에 경로 들여쓰기
  D3. 코드 — 목록에 코드 유형(통합·계층), 통합 코드의 값 탭에 하위 코드 값
  D4. 표준 매핑 — 매핑 추가(이름으로 새 표준 찾기) → 저장
시험 사전과 그 안의 데이터는 끝에서 지운다.
"""
import base64, json, subprocess, sys, time, traceback
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
DICT_NM = f"셀WP6사전{TAG}"
results = []
state = {}


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception:
        traceback.print_exc()
        results.append((name, "FAIL"))


def psql(sql):
    r = subprocess.run(PSQL + ["-At", "-c", sql],
                       capture_output=True, text=True, timeout=30, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


def login_api(user, pwd="123"):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": base64.b64encode(pwd.encode()).decode()}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def setup(sa):
    r = sa.post(BASE + "/api/dict/save", json={"dictNm": DICT_NM, "dictDesc": "셀레니움 WP6", "termLastWordClsfYn": "N",
                                               "upperYn": "Y", "wordEngNmDupYn": "Y", "termEngMaxLen": "", "oldStdYn": "Y"}, timeout=10).json()
    d = json.loads(r["contents"])["dictId"]
    state["dict"] = d
    psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('S6G{TAG}', '셀상위그룹', 'N', '{d}')")
    psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values ('S6C{TAG}', '셀분류', '셀상위그룹', 'N', '{d}')")
    psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, dict_id) "
         f"values ('S6D{TAG}', '셀도메인S6', '셀상위그룹', '셀분류', '셀레니움', 'VARCHAR', 10, 'Y', '{d}')")
    for nm, ab in [("셀칠가", "SSEVA"), ("셀칠나", "SSEVB"), ("셀칠다", "SSEVC")]:
        sa.post(BASE + "/api/std/createWord", json={"wordNm": nm, "wordEngAbrvNm": ab, "wordEngNm": ab.title(), "wordDesc": nm,
                                                    "wordClsfYn": "N", "dictId": d}, timeout=10)

    def wid(nm):
        return psql(f"select word_id from quality.tb_word where dict_id='{d}' and word_nm='{nm}'")

    def code(nm, abrv, grp, w, tp, parent=None, subs=None):
        r = sa.post(BASE + "/api/std/createCode", json={"termsNm": nm, "termsEngAbrvNm": abrv, "termsDesc": nm, "domainNm": "셀도메인S6",
                                                        "codeGrp": grp, "wordList": [{"wordId": wid(w), "wordNm": w, "wordOrd": 0}],
                                                        "dictId": d, "codeTp": tp, "parentCodeGrp": parent, "integSubGrps": subs or []}, timeout=10).json()
        assert r.get("resultCode") == 200, r
    state["A"], state["B"], state["E"] = f"S6A{TAG}", f"S6B{TAG}", f"S6E{TAG}"
    code("셀칠가코드", "SSEVA", state["A"], "셀칠가", "SINGLE")
    code("셀칠나코드", "SSEVB", state["B"], "셀칠나", "HIER", state["A"])
    code("셀칠다코드", "SSEVC", state["E"], "셀칠다", "INTEG", None, [state["A"], state["B"]])
    for v, p, g in [("01", None, state["A"]), ("B1", "01", state["B"])]:
        r = sa.post(BASE + "/api/std/createCodeData", json={"codeGrp": g, "codeNm": f"셀값{g}", "codeEngNm": "VAL", "codeVal": v,
                                                            "codeValDesc": v, "parentCodeVal": p, "dictId": d}, timeout=10).json()
        assert r.get("resultCode") == 200, r


def cleanup():
    d = state.get("dict")
    if not d:
        return
    for sql in [
        f"delete from quality.tb_std_map where dict_id='{d}'",
        f"delete from quality.tb_code_integ where dict_id='{d}'",
        f"delete from quality.tb_code_data where dict_id='{d}'",
        f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id='{d}')",
        f"delete from quality.tb_terms where dict_id='{d}'",
        f"delete from quality.tb_word where dict_id='{d}'",
        f"delete from quality.tb_domain where dict_id='{d}'",
        f"delete from quality.tb_domain_clsf where dict_id='{d}'",
        f"update quality.tb_domain_grp set parent_grp_id=null where dict_id='{d}'",
        f"delete from quality.tb_domain_grp where dict_id='{d}'",
        f"delete from quality.tb_change_history where target_nm like '셀칠%'",
        f"delete from quality.tb_std_dict where dict_id='{d}'",
    ]:
        psql(sql)


def main():
    sa = login_api("space")
    setup(sa)
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=options)
    driver.set_window_size(1600, 900)

    def js(script, *args):
        return driver.execute_script(script, *args)

    def open_menu(group_id, nav_id):
        groups = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
        if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
            js("arguments[0].click();", groups[0])
            time.sleep(1)
        js(f"document.getElementById('{nav_id}').click();")
        time.sleep(2.5)

    def pick(el, text):
        """v-select·v-autocomplete 항목 고르기"""
        js("arguments[0].closest('.v-input').querySelector('.v-input__slot').click();", el)
        time.sleep(0.8)
        items = [i for i in driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item") if text in i.text]
        assert items, f"선택지 '{text}' 없음"
        js("arguments[0].click();", items[0])
        time.sleep(0.6)

    try:
        driver.get(BASE)
        time.sleep(2)
        id_input = WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        id_input.send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)
        js("sessionStorage.setItem('navidmeta.dictId', arguments[0]);", state["dict"])

        def _d1():
            open_menu("mmGroup", "nav_std_dict")
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "tbody tr") if DICT_NM in r.text]
            assert rows, "사전 목록에 시험 사전이 없음"
            js("arguments[0].querySelector('.v-data-table__checkbox, .v-simple-checkbox').click();", rows[0])
            time.sleep(0.5)
            btn = [b for b in driver.find_elements(By.CSS_SELECTOR, "button") if b.text.strip() == "수정"][0]
            js("arguments[0].click();", btn)
            time.sleep(1)
            js("document.getElementById('dictUpperYn').closest('.v-input').querySelector('.v-input--selection-controls__ripple').click();")
            time.sleep(0.3)
            save = [b for b in driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active button") if b.text.strip() == "저장"][0]
            js("arguments[0].click();", save)
            time.sleep(2)
            v = psql(f"select upper_yn from quality.tb_std_dict where dict_id='{state['dict']}'")
            print(f"  표준 대문자={v}")
            assert v == "N"
            assert "대문자 거부" in driver.find_element(By.TAG_NAME, "body").text
        step("D1. 표준사전 관리 — 운영 옵션 저장", _d1)

        def _d2():
            open_menu("dsGroup", "nav_domainGroup")
            btn = [b for b in driver.find_elements(By.CSS_SELECTOR, "#tab_domainGroup button, button") if b.text.strip() == "등록"][0]
            js("arguments[0].click();", btn)
            time.sleep(1)
            dlg = driver.find_element(By.CSS_SELECTOR, ".v-dialog--active")
            dlg.find_element(By.CSS_SELECTOR, "input[placeholder='금액']").send_keys("셀하위그룹")
            pick(driver.find_element(By.ID, "addGrpParent"), "셀상위그룹")
            sub = [b for b in dlg.find_elements(By.CSS_SELECTOR, "button") if b.text.strip() == "등록"][0]
            js("arguments[0].click();", sub)
            time.sleep(2)
            v = psql(f"select p.domain_grp_nm from quality.tb_domain_grp g join quality.tb_domain_grp p on p.domain_grp_id = g.parent_grp_id "
                     f"where g.dict_id='{state['dict']}' and g.domain_grp_nm='셀하위그룹'")
            rows = [r.text for r in driver.find_elements(By.CSS_SELECTOR, "#domainGroup_table tbody tr") if "셀하위그룹" in r.text]
            print(f"  상위={v} 행={rows}")
            assert v == "셀상위그룹" and rows and "셀상위그룹" in rows[0]
        step("D2. 도메인 그룹 — 상위 그룹 지정 등록", _d2)

        def _d3():
            open_menu("dsGroup", "nav_dsCode")
            time.sleep(1)
            rows = [r.text.replace("\n", " ") for r in driver.find_elements(By.CSS_SELECTOR, "#code_table tbody tr") if "셀칠" in r.text]
            print(f"  목록={rows}")
            assert any("셀칠다코드" in r and "통합" in r for r in rows) and any("셀칠나코드" in r and "계층" in r for r in rows)
            link = [e for e in driver.find_elements(By.CSS_SELECTOR, "#code_table tbody span.ndColor--text") if e.text == "셀칠다코드"][0]
            js("arguments[0].click();", link)
            time.sleep(1.5)
            tab = [t for t in driver.find_elements(By.CSS_SELECTOR, ".v-tab") if "코드 항목" in t.text][0]
            js("arguments[0].click();", tab)
            time.sleep(1.5)
            vals = [r.text for r in driver.find_elements(By.CSS_SELECTOR, "#codeVal_table tbody tr")]
            print(f"  값={vals}")
            assert any(state["A"] in v and "01" in v for v in vals) and any(state["B"] in v and "B1" in v for v in vals)
        step("D3. 코드 — 코드 유형 열, 통합 코드 값 모음", _d3)

        def _d4():
            open_menu("dsGroup", "nav_stdMap")
            js("document.getElementById('stdMapAdd').click();")
            time.sleep(1)
            driver.find_element(By.ID, "stdMapOldNm").send_keys("셀옛코드")
            driver.find_element(By.ID, "stdMapOldEng").send_keys("OLD_SSEV")
            nw = driver.find_element(By.ID, "stdMapNew")
            js("arguments[0].closest('.v-input').querySelector('.v-input__slot').click();", nw)
            nw.send_keys("셀칠가")
            time.sleep(1.5)
            items = [i for i in driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item") if "셀칠가코드" in i.text]
            assert items, "새 표준 검색 결과 없음"
            js("arguments[0].click();", items[0])
            time.sleep(0.5)
            js("document.getElementById('stdMapSave').click();")
            time.sleep(2)
            v = psql(f"select m.old_nm || '>' || t.terms_nm from quality.tb_std_map m join quality.tb_terms t on t.terms_id = m.new_id where m.dict_id='{state['dict']}'")
            print(f"  매핑={v}")
            assert v == "셀옛코드>셀칠가코드"
            assert "셀옛코드" in driver.find_element(By.ID, "stdMapTable").text
        step("D4. 표준 매핑 — 새 표준 찾아 매핑 추가", _d4)
    finally:
        driver.quit()
        cleanup()


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
