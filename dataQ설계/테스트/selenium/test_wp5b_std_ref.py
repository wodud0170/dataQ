"""
104번 WP5(14·11) — 참조 사전·공통표준 가져오기, 상위 사전 상속

검증 (시험 사전 4개를 API·SQL 로 만들고 화면은 그 사전으로 연다 — 사전 선택은 세션스토리지):
  F1. 관리 > 표준사전 관리 — 원본 사전 수정 창에서 "참조 사전 (읽기 전용)" 켜고 저장 → 사전 유형 REF
  F2. 단어 — 참조 사전을 고르면 "참조 사전 — 읽기 전용" 표시, [등록] 없음
  F3. 공통표준 가져오기 — 원본(참조 사전) 용어를 골라 [가져오기] → 결과 "가져옴 1건", 대상 사전에 용어·구성 단어·도메인
  F4. 단어 — 하위 사전에서 "상위 사전 포함" → 상위 사전 단어가 "상위" 칩과 함께
시험 데이터는 끝에서 지운다.
"""
import base64, json, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
results = []
D = {}


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
    if r.returncode != 0:
        print("  [psql] " + (r.stderr or "").strip()[:200])
    return (r.stdout or "").strip()


def setup():
    s = requests.Session()
    s.post(BASE + "/login", data={"id": "space", "password": base64.b64encode(b"123").decode()}, allow_redirects=False, timeout=10)

    def mk(key, nm, **opt):
        body = {"dictNm": nm, "termLastWordClsfYn": "N"}
        body.update(opt)
        D[key] = json.loads(s.post(BASE + "/api/dict/save", json=body, timeout=10).json()["contents"])["dictId"]
    mk("src", f"셀WP5B원본{TAG}")
    mk("tgt", f"셀WP5B기관{TAG}")
    mk("par", f"셀WP5B상위{TAG}")
    mk("chd", f"셀WP5B하위{TAG}", parentDictId=D["par"])
    S, P, C = D["src"], D["par"], D["chd"]
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    for d in (S, P):
        psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values (substr(md5(random()::text),1,22), '셀그룹5', 'Y', '{d}')")
        psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values (substr(md5(random()::text),1,22), '셀분류5', '셀그룹5', 'Y', '{d}')")
        psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, comm_stnd_yn, dict_id, cret_dt) "
             f"values (substr(md5(random()::text),1,22), '셀번호5', '셀그룹5', '셀분류5', 'x', 'VARCHAR', 20, 'Y', 'Y', '{d}', {now})")
    for d, nm, ab in [(S, "셀오가", "SOGA"), (S, "셀오나", "SONA"), (P, "셀상위단어", "SPAR"), (C, "셀하위단어", "SCHD")]:
        s.post(BASE + "/api/std/createWord", json={"wordNm": nm, "wordEngAbrvNm": ab, "wordEngNm": ab.title(), "wordDesc": nm, "wordClsfYn": "N", "commStndYn": "Y", "dictId": d}, timeout=10)
    wl = [{"wordId": psql(f"select word_id from quality.tb_word where dict_id='{S}' and word_nm='{w}'"), "wordNm": w, "wordOrd": i} for i, w in enumerate(["셀오가", "셀오나"])]
    r = s.post(BASE + "/api/std/createTerms", json={"termsNm": "셀오가오나", "termsEngAbrvNm": "SOGA_SONA", "termsDesc": "x", "domainNm": "셀번호5",
                                                    "wordList": wl, "dictId": S, "commStndYn": "Y"}, timeout=10).json()
    assert r.get("resultCode") == 200, r


def cleanup():
    ids = ",".join(f"'{v}'" for v in D.values())
    if not ids:
        return
    for sql in [
        f"update quality.tb_std_dict set parent_dict_id=null where dict_id in ({ids})",
        f"delete from quality.tb_code_data where dict_id in ({ids})",
        f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id in ({ids}))",
        f"delete from quality.tb_terms where dict_id in ({ids})",
        f"delete from quality.tb_word where dict_id in ({ids})",
        f"delete from quality.tb_domain where dict_id in ({ids})",
        f"delete from quality.tb_domain_clsf where dict_id in ({ids})",
        f"delete from quality.tb_domain_grp where dict_id in ({ids})",
        f"delete from quality.tb_std_dict where dict_id in ({ids})",
        "delete from quality.tb_change_history where change_source='STD_IMPORT' and target_nm like '%셀WP5B%'",
        "delete from quality.tb_change_history where target_nm in ('셀오가','셀오나','셀상위단어','셀하위단어','셀오가오나')",
    ]:
        psql(sql)


def main():
    setup()
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=headless(options))
    driver.set_window_size(1600, 900)

    def js(script, *args):
        return driver.execute_script(script, *args)

    def open_menu(group_id, nav_id):
        groups = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
        if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
            js("arguments[0].click();", groups[0])
            time.sleep(1)
        js(f"document.getElementById('{nav_id}').click();")
        time.sleep(3)

    def set_dict(d):
        js("sessionStorage.setItem('navidmeta.dictId', arguments[0]);", d)

    try:
        driver.get(BASE)
        time.sleep(2)
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)

        def _f1():
            open_menu("mmGroup", "nav_std_dict")
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "tbody tr") if f"셀WP5B원본{TAG}" in r.text]
            js("arguments[0].querySelector('.v-data-table__checkbox, .v-simple-checkbox').click();", rows[0])
            time.sleep(0.5)
            js("arguments[0].click();", [b for b in driver.find_elements(By.CSS_SELECTOR, "button") if (b.get_attribute("textContent") or "").strip() == "수정"][0])
            time.sleep(1)
            js("document.getElementById('dictRefTp').closest('.v-input').querySelector('.v-input--selection-controls__ripple').click();")
            time.sleep(0.3)
            js("arguments[0].click();", [b for b in driver.find_elements(By.CSS_SELECTOR, ".v-dialog--active button") if (b.get_attribute("textContent") or "").strip() == "저장"][0])
            time.sleep(2)
            v = psql(f"select dict_tp from quality.tb_std_dict where dict_id='{D['src']}'")
            print(f"  사전 유형={v}")
            assert v == "REF"
            row = [r.text for r in driver.find_elements(By.CSS_SELECTOR, "tbody tr") if f"셀WP5B원본{TAG}" in r.text][0]
            assert "참조" in row
        step("F1. 표준사전 관리 — 참조 사전으로", _f1)

        def _f2():
            set_dict(D["src"])
            open_menu("dsGroup", "nav_word")
            time.sleep(1)
            assert driver.find_elements(By.ID, "refDictChipWord"), "참조 사전 표시 없음"
            assert not driver.find_elements(By.ID, "addWordBtn"), "[등록] 이 보임"
            names = [r.text.split("\n")[0] for r in driver.find_elements(By.CSS_SELECTOR, "#tab_word tbody tr")]
            print(f"  목록={names}")
            assert any(n.startswith("셀오가") for n in names)
        step("F2. 단어 — 참조 사전은 읽기 전용", _f2)

        def _f3():
            set_dict(D["tgt"])
            open_menu("dsGroup", "nav_stdImport")
            js("let c = document.getElementById('importTable').__vue__; while (c && !('sourceDictId' in c.$data)) c = c.$parent;"
               "c.dictId = arguments[1]; c.sourceDictId = arguments[0]; c.kind = 'TERM'; c.load();", D["src"], D["tgt"])
            time.sleep(2.5)
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "#importTable tbody tr") if "셀오가오나" in r.text]
            assert rows, "원본 용어가 목록에 없음"
            js("arguments[0].querySelector('.v-data-table__checkbox').click();", rows[0])
            time.sleep(0.5)
            js("document.getElementById('importRun').click();")
            time.sleep(3)
            res = driver.find_element(By.ID, "importResult").text
            print(f"  결과={res[:80]}")
            assert "가져옴 1건" in res
            t = psql(f"select count(*) from quality.tb_terms where dict_id='{D['tgt']}' and terms_nm='셀오가오나' and comm_stnd_yn='Y' and aprv_yn='Y'")
            w = psql(f"select count(*) from quality.tb_word where dict_id='{D['tgt']}' and word_nm in ('셀오가','셀오나')")
            dm = psql(f"select count(*) from quality.tb_domain where dict_id='{D['tgt']}' and domain_nm='셀번호5'")
            print(f"  대상 사전 용어={t} 단어={w} 도메인={dm}")
            assert (t, w, dm) == ("1", "2", "1")
            js("[...document.querySelectorAll('#importResult button')].find(b => b.innerText.includes('닫기')).click();")
        step("F3. 공통표준 가져오기 — 용어와 구성 단어·도메인", _f3)

        def _f4():
            set_dict(D["chd"])
            open_menu("dsGroup", "nav_word")
            js("let c = document.querySelector('#tab_word .v-data-table').__vue__; while (c && !('inclParentYn' in c.$data)) c = c.$parent;"
               "c.dictId = arguments[0]; c.dictMeta = {dictId: arguments[0], parentDictId: arguments[1], dictTp: 'ORG'}; c.getWordData();", D["chd"], D["par"])
            time.sleep(2.5)
            cb = driver.find_element(By.ID, "inclParentWord")
            js("arguments[0].click();", cb)
            time.sleep(2.5)
            rows = {r.text.split("\n")[0]: r.text for r in driver.find_elements(By.CSS_SELECTOR, "#tab_word tbody tr")}
            chips = [c.text for c in driver.find_elements(By.CSS_SELECTOR, "#tab_word tbody tr .v-chip")]
            print(f"  목록={list(rows)} 칩={chips}")
            assert any(k.startswith("셀상위단어") for k in rows) and any(k.startswith("셀하위단어") for k in rows) and "상위" in chips
            assert not driver.find_elements(By.ID, "addWordBtn"), "상위 사전 포함으로 보는 동안 [등록] 이 보임"
        step("F4. 단어 — 상위 사전 포함", _f4)
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
