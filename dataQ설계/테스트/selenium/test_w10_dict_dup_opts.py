"""
104번 WP6-13 · 108 §4.23-6 (§4.31) — 사전 옵션 "동음이의어 허용"·"용어 영문약어명 중복 허용"

시험 데이터(API·psql, 끝에서 모두 지움):
  사전 H(두 옵션 켬)·N(끔). H: 도메인 W10번호20 VARCHAR(20)·W10번호30 VARCHAR(30)·W10수량 NUMERIC(10),
     단어 고객 CUST·번호 NO·식별 IDNT·수 NUM(수량)·수 CNT(개수), 용어 고객번호 CUST_NO(20)·고객식별번호 CUST_NO(30)·
     번호수 NO_CNT·고객번호수 CUST_NO_CNT(수 CNT 사용 빈도 2 → 분석 1순위). N: 단어 수 NUM.
  H 모델(테이블 넷에 CUST_NO — 고객번호 VARCHAR(20)·고객식별번호 VARCHAR(30)·거래처번호 VARCHAR(20)·고객번호 VARCHAR(30)).
  일반 사용자 w10ddo<tag>(API).

  S1. 관리 > 표준사전 관리 — 목록 운영 옵션 문구(H 허용·N 불가), 수정 창 스위치 2개·설명, 동음이의어가 있는 채로 끄기 → "저장 실패" 창 문구·DB 그대로,
      사전 추가 창은 두 스위치 꺼짐
  S2. 단어 등록 창 — H 에서 "수" → 회색 안내(같은 이름 단어 목록)·등록 성공(수 3개), N 에서 "수" → 빨간 "이미 승인된 단어입니다."
  S3. 용어 등록 "고객수" — 구성 단어 '수' 후보 3행(S2 의 QTY 포함)·"동음이의어 3" 칩·1순위(CNT, 사용 빈도) 선택·NUM 을 고르면 하나만 골라지고
      용어 영문약어 CUST_NUM·등록(구성 단어 NUM)
  S4. 한글컬럼 일괄 표준화 "식별수" — [수정] 창 영문약어 선택 목록("영문약어 — 설명", CNT 먼저)·칩, NUM 으로 바꾸면 용어 영문약어 IDNT_NUM·확인 뒤 목록
  S5. H 모델 표준 진단 결과 — 한글명이 같은 용어로 판정(앞 둘 이슈 없음), 거래처번호 한글명 불일치(표준값 "고객번호, 고객식별번호"), 고객번호 VARCHAR(30) 길이 불일치(표준 20)
  S6. 시험 일반 사용자 — H 에서 "수" 회색 안내·등록 신청 → 승인 대기(APRV_YN N) → 관리자 승인(putStdAprvStat — 승인 Y·승인 이력 2)
"""
import base64, json, secrets, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
USER = "w10ddo" + TAG
DICTNM = {k: f"W10DDO사전{k}{TAG}" for k in "HN"}
DM = f"W10DDOM{TAG}"
DMNM = f"W10DDO_{TAG}_모델"
results = []
FX = {}

# 탭 안 Vue 컴포넌트 — 자식부터 찾고 없으면 위로 (첫 요소가 keep-alive 로 잡히는 화면이 있다)
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
DICT = VM + "const c = vm('tab_std_dict', 'MMStdDict');"
WORD = VM + "const c = vm('tab_word', 'DSWord');"
TERM = VM + "const c = vm('tab_term', 'DSTerm');"
REC = VM + "const c = vm('tab_termRecommend', 'DSTermRecommend');"
DIAG = VM + "const c = vm('tab_dataDiagResult', 'DSDataDiagResult');"
GREY, RED = "rgb(117, 117, 117)", "rgb(211, 47, 47)"


def step(name, fn):
    print(f"\n=== {name}", flush=True)
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS", flush=True)
    except Exception:
        traceback.print_exc()
        results.append((name, "FAIL"))


def psql(sql):
    r = subprocess.run(PSQL + ["-v", "ON_ERROR_STOP=1", "-At", "-c", sql],
                       capture_output=True, text=True, timeout=60, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError("psql: " + (r.stderr or "")[:400])
    return (r.stdout or "").strip()


def q(v):
    return "NULL" if v is None else "'" + str(v).replace("'", "''") + "'"


def b64(s):
    return base64.b64encode(s.encode("utf-8")).decode()


def api_login(uid, pw):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": uid, "password": b64(pw)}, allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {uid}: {r.status_code}"
    return s


def okc(r):
    r = r.json()
    assert r.get("resultCode") == 200, r
    return r


def wait_until(fn, timeout=30, interval=1.0):
    t0 = time.time()
    while time.time() - t0 < timeout:
        v = fn()
        if v:
            return v
        time.sleep(interval)
    raise AssertionError("시간 초과")


def setup():
    sa = FX["sa"] = api_login("space", "123")
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    d = FX["dict"] = {}
    for k, yn in (("H", "Y"), ("N", "N")):
        body = {"dictNm": DICTNM[k], "termLastWordClsfYn": "N", "homonymYn": yn, "termEngAbrvDupYn": yn}
        d[k] = json.loads(okc(sa.post(BASE + "/api/dict/save", json=body, timeout=20))["contents"])["dictId"]
    H = d["H"]
    psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('W10DDOG{TAG}', 'W10그룹', 'N', {q(H)})")
    psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values ('W10DDOC{TAG}', 'W10분류', 'W10그룹', 'N', {q(H)})")
    for i, (nm, tp, ln) in enumerate((("W10번호20", "VARCHAR", 20), ("W10번호30", "VARCHAR", 30), ("W10수량", "NUMERIC", 10))):
        psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, dict_id, cret_dt) "
             f"values ('W10DDOD{i}{TAG}', {q(nm)}, 'W10그룹', 'W10분류', 'x', {q(tp)}, {ln}, 'Y', {q(H)}, {now})")
    for k, words in (("H", (("고객", "CUST", "CUSTOMER", "고객"), ("번호", "NO", "NUMBER", "번호"), ("식별", "IDNT", "IDENTIFICATION", "식별"),
                            ("수", "NUM", "NUMERIC", "수량"), ("수", "CNT", "COUNT", "개수"))),
                     ("N", (("수", "NUM", "NUMERIC", "수량"),))):
        for nm, ab, en, desc in words:
            okc(sa.post(BASE + "/api/std/createWord", json={"wordNm": nm, "wordEngAbrvNm": ab, "wordEngNm": en, "wordDesc": desc,
                                                            "wordClsfYn": "N", "dictId": d[k], "allophSynmLst": []}, timeout=20))

    def wid(ab):
        return psql(f"select word_id from quality.tb_word where dict_id={q(H)} and word_eng_abrv_nm={q(ab)}")
    FX["wid"] = {ab: wid(ab) for ab in ("CUST", "NO", "IDNT", "NUM", "CNT")}
    for nm, ab, dom, ws in (("고객번호", "CUST_NO", "W10번호20", (("고객", "CUST"), ("번호", "NO"))),
                            ("고객식별번호", "CUST_NO", "W10번호30", (("고객", "CUST"), ("식별", "IDNT"), ("번호", "NO"))),
                            ("번호수", "NO_CNT", "W10수량", (("번호", "NO"), ("수", "CNT"))),
                            ("고객번호수", "CUST_NO_CNT", "W10수량", (("고객", "CUST"), ("번호", "NO"), ("수", "CNT")))):
        wl = [{"wordId": FX["wid"][a], "wordNm": w, "wordOrd": i} for i, (w, a) in enumerate(ws)]
        okc(sa.post(BASE + "/api/std/createTerms", json={"termsNm": nm, "termsEngAbrvNm": ab, "termsDesc": nm, "domainNm": dom,
                                                        "wordList": wl, "dictId": H}, timeout=20))

    psql(f"insert into quality.tb_data_model (dm_id, dm_nm, ver, use_yn, dict_id, stnd_req_yn, cret_dt) "
         f"values ({q(DM)}, {q(DMNM)}, '1', 'Y', {q(H)}, 'N', {now})")
    attrs = [("T_A", "고객번호", 20), ("T_B", "고객식별번호", 30), ("T_C", "거래처번호", 20), ("T_D", "고객번호", 30)]
    for o, _kr, _ln in attrs:
        psql(f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) values ({q(DM)}, 'APP', {q(o)}, {q(o)}, 1, 'Y')")
    vals = ",".join(f"({q(DM)}, 'APP', {q(o)}, 'CUST_NO', {q(kr)}, 'VARCHAR', {ln}, 1, 'N', 'Y', 'Y', 'N', 'APPROVED')" for o, kr, ln in attrs)
    psql("insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, attr_ord, pk_yn, nullable_yn, "
         "use_yn, terms_stnd_yn, aprv_status) values " + vals)

    FX["upw"] = "Zz" + secrets.token_hex(6)
    okc(sa.post(BASE + "/api/login/createUser", json={"id": USER, "password": b64(FX["upw"]), "name": "W10 사전 옵션 시험 " + USER,
                                                       "email": "", "admin": False}, timeout=10))


def cleanup():
    psql(f"delete from quality.tb_diag_result where diag_job_id in (select diag_job_id from quality.tb_diag_job where dm_id={q(DM)})")
    psql(f"delete from quality.tb_diag_job where dm_id={q(DM)}")
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_obj", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id={q(DM)}")
    psql(f"delete from quality.tb_data_model where dm_id={q(DM)}")
    ds = f"select dict_id from quality.tb_std_dict where dict_nm like {q('W10DDO사전%' + TAG)}"
    ids = (f"select word_id from quality.tb_word where dict_id in ({ds}) union all select terms_id from quality.tb_terms where dict_id in ({ds}) "
           f"union all select domain_id from quality.tb_domain where dict_id in ({ds})")
    psql(f"delete from quality.tb_notification where link_id in ({ids}) or link_id in (select req_id from quality.tb_aprv_stats where req_item_id in ({ids})) "
         f"or link_id in (select doc_id from quality.tb_aprv_doc where dict_id in ({ds}))")
    psql(f"delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where dict_id in ({ds}))")
    psql(f"delete from quality.tb_aprv_doc where dict_id in ({ds})")
    psql(f"delete from quality.tb_change_history where target_id in ({ids})")
    psql(f"delete from quality.tb_aprv_stats where req_item_id in ({ids})")
    for sql in (f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id in ({ds}))",
                f"delete from quality.tb_terms where dict_id in ({ds})", f"delete from quality.tb_word where dict_id in ({ds})",
                f"delete from quality.tb_domain where dict_id in ({ds})", f"delete from quality.tb_domain_clsf where dict_id in ({ds})",
                f"delete from quality.tb_domain_grp where dict_id in ({ds})",
                f"delete from quality.tb_std_dict where dict_nm like {q('W10DDO사전%' + TAG)}"):
        psql(sql)
    for t in ("tb_user_role", "tb_notification", "tb_user"):
        psql(f"delete from quality.{t} where user_id={q(USER)}")
    psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id={q(USER)}")


def new_driver(user, pw):
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    d = webdriver.Edge(options=headless(opts))
    d.set_window_size(1366, 768)
    d.get(BASE)
    time.sleep(2)
    WebDriverWait(d, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
    d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(pw)
    d.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
    WebDriverWait(d, 15).until(lambda x: "/signin" not in x.current_url)
    time.sleep(3)
    # 사전 선택은 세션스토리지 — 화면은 H 로 연다
    d.execute_script("sessionStorage.setItem('navidmeta.dictId', arguments[0]);", FX["dict"]["H"])
    return d


class Ui:
    def __init__(self, d):
        self.d = d

    def js(self, script, *args):
        return self.d.execute_script(script, *args)

    def open_menu(self, group_id, nav_id, ready):
        g = self.d.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
        if g and "v-list-group__header--active" not in (g[0].get_attribute("class") or ""):
            self.js("arguments[0].click();", g[0])
            time.sleep(1)
        self.js(f"document.getElementById('{nav_id}').click();")
        WebDriverWait(self.d, 15).until(lambda x: self.js(ready))
        time.sleep(0.8)

    def tab_button(self, tab, text):
        """탭 안 글자 버튼 — 행을 화면 가운데로 굴린 뒤라 .text 가 비어 있을 수 있어 innerText 로 찾는다"""
        ok = self.js("const b = [...document.querySelectorAll('#' + arguments[0] + ' button')].find(x => x.innerText.trim() === arguments[1]); "
                     "if (!b || b.disabled) return false; b.click(); return true;", tab, text)
        assert ok, f"[{text}] 버튼 없음 또는 비활성"
        time.sleep(0.5)

    def dlg_button(self, text):
        b = [b for b in self.d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button") if b.text.strip() == text]
        assert b, f"창에 [{text}] 버튼 없음"
        self.js("arguments[0].click();", b[-1])
        time.sleep(0.8)

    def swal(self, title, timeout=20):
        WebDriverWait(self.d, timeout).until(lambda x: self.js(
            "const t = document.querySelector('.swal2-popup.swal2-show .swal2-title'); return t && t.innerText.trim() === arguments[0];", title))
        return self.js("const h = document.querySelector('.swal2-popup .swal2-html-container'); return h ? h.innerText.trim() : '';")

    def swal_close(self):
        self.js("const b = document.querySelector('.swal2-popup .swal2-confirm'); if (b) b.click();")
        WebDriverWait(self.d, 10).until(lambda x: not self.js("return !!document.querySelector('.swal2-popup.swal2-show');"))
        time.sleep(0.3)

    def word_nm_check(self, nm):
        """단어 등록 창 — 단어명을 넣고 칸을 떠난 뒤 안내 [문구, 색]"""
        self.js(WORD + "c.addWord_wordNm = null;")
        el = self.d.find_element(By.ID, "addWordNm")
        el.send_keys(nm)
        self.js("document.getElementById('addWordNm').blur();")
        WebDriverWait(self.d, 10).until(lambda x: self.js("return !!document.getElementById('addWordNmCheck');"))
        time.sleep(0.3)
        return self.js("const e = document.getElementById('addWordNmCheck'); return [e.innerText.trim(), getComputedStyle(e).color];")


def main():
    setup()
    H, N = FX["dict"]["H"], FX["dict"]["N"]
    sa = FX["sa"]
    d = new_driver("space", "123")
    u = Ui(d)
    try:
        def _s1():
            u.open_menu("mmGroup", "nav_std_dict", DICT + "return !!c && c.dicts.length > 0;")
            rows = u.js("return [...document.querySelectorAll('#tab_std_dict tbody tr')].map(tr => tr.innerText.replace(/\\s+/g, ' '));")
            h = [r for r in rows if DICTNM["H"] in r]
            n = [r for r in rows if DICTNM["N"] in r]
            print("  H 행", h, "\n  N 행", n)
            # 운영 옵션 열은 옵션 하나를 꺾지 않고 옵션 사이에서만 줄바꿈 (가운뎃점은 CSS 라 innerText 에 없다)
            assert h and "구표준 안 씀 동음이의어 허용 용어 영문약어 중복 허용" in h[0], h
            assert n and "구표준 안 씀 동음이의어 불가 용어 영문약어 중복 불가" in n[0], n
            lines = u.js("const tr = [...document.querySelectorAll('#tab_std_dict tbody tr')].find(t => t.innerText.includes(arguments[0])); "
                         "return [...tr.querySelectorAll('.dict-opt-list > span')].map(x => [x.innerText.trim(), x.getBoundingClientRect().height]);", DICTNM["H"])
            print("  운영 옵션 줄", lines)
            assert [x[0] for x in lines][-2:] == ["동음이의어 허용", "용어 영문약어 중복 허용"] and len(lines) == 6 and all(x[1] < 25 for x in lines), lines
            # 수정 창 — 스위치 2개(켜짐)·설명
            # 목록이 길어 행이 화면 밖이면 .text 가 비므로 innerText 로 찾는다
            tr = u.js("return [...document.querySelectorAll('#tab_std_dict tbody tr')].find(t => t.innerText.includes(arguments[0]));", DICTNM["H"])
            assert tr, "H 행 없음"
            u.js("arguments[0].scrollIntoView({block: 'center'}); arguments[0].querySelector('.v-data-table__checkbox, .v-simple-checkbox').click();", tr)
            time.sleep(0.5)
            u.tab_button("tab_std_dict", "수정")
            WebDriverWait(d, 10).until(lambda x: u.js(DICT + "return c.modal && c.editing;"))
            time.sleep(0.8)
            sw = u.js("return ['dictHomonymYn', 'dictTermEngAbrvDupYn'].map(id => { const e = document.getElementById(id); "
                      "return e ? [e.getAttribute('aria-checked'), e.closest('.v-input').innerText.trim()] : null; });")
            print("  스위치", sw)
            assert sw == [["true", "동음이의어 허용"], ["true", "용어 영문약어명 중복 허용"]], sw
            body = d.find_element(By.CSS_SELECTOR, ".v-dialog--active").text
            assert "켜면 이 사전에 같은 단어명의 단어를 영문약어를 달리해 여러 개 등록할 수 있습니다." in body
            assert "표준 진단·컬럼 표준 검사는 영문명이 같은 용어 중 한글명이 같은 용어로 판정합니다." in body
            # 동음이의어(수 2개)가 있는 채로 끄기 → 거부 창, DB 그대로
            u.js("document.getElementById('dictHomonymYn').closest('.v-input').querySelector('.v-input--selection-controls__ripple').click();")
            time.sleep(0.3)
            assert u.js(DICT + "return c.form.homonymYn;") == "N"
            u.dlg_button("저장")
            msg = u.swal("저장 실패")
            print("  거부 문구", msg)
            assert msg == "같은 단어명의 단어가 있어 동음이의어 허용을 끌 수 없습니다: 수(2개) — 단어명을 정리한 뒤 끄세요.", msg
            u.swal_close()
            assert psql(f"select homonym_yn || term_eng_abrv_dup_yn from quality.tb_std_dict where dict_id={q(H)}") == "YY"
            assert u.js(DICT + "return c.modal;"), "거부 뒤 수정 창이 닫힘"
            u.dlg_button("취소")
            # 사전 추가 창 — 두 스위치 꺼짐
            u.tab_button("tab_std_dict", "사전 추가")
            WebDriverWait(d, 10).until(lambda x: u.js(DICT + "return c.modal && !c.editing;"))
            time.sleep(0.5)
            st = u.js(DICT + "return [c.form.homonymYn, c.form.termEngAbrvDupYn, document.getElementById('dictHomonymYn').getAttribute('aria-checked'), "
                      "document.getElementById('dictTermEngAbrvDupYn').getAttribute('aria-checked')];")
            assert st == ["N", "N", "false", "false"], st
            u.dlg_button("취소")
        step("S1. 표준사전 관리 — 목록 운영 옵션 문구·스위치 2개·끄기 거부 창·추가 창 기본 끔", _s1)

        def _s2():
            u.open_menu("dsGroup", "nav_word", WORD + "return !!c && c.dictId === " + q(H) + ";")
            u.js("document.getElementById('addWordBtn').click();")
            WebDriverWait(d, 10).until(lambda x: u.js(WORD + "return c.addWordModalShow;"))
            time.sleep(0.8)
            txt, color = u.word_nm_check("수")
            print("  H 안내", txt, color)
            assert txt.startswith("같은 이름의 단어가 있습니다: 수(CNT), 수(NUM) — 동음이의어 허용 사전이라 영문약어가 다르면 등록할 수 있습니다."), txt
            assert "권장 단어 보기" in txt and color == GREY, (txt, color)
            u.js(WORD + "c.addWord_wordEngAbrvNm = 'QTY'; c.addWord_wordEngNm = 'QUANTITY'; c.addWord_wordDesc = '수 — 수량 단위 시험';")
            u.dlg_button("등록")
            got = wait_until(lambda: psql(f"select string_agg(word_eng_abrv_nm || ':' || aprv_yn, ',' order by word_eng_abrv_nm) from quality.tb_word "
                                          f"where dict_id={q(H)} and word_nm='수'") == "CNT:Y,NUM:Y,QTY:Y", 15)
            assert got
            assert u.js(WORD + "return !c.addWordModalShow;"), "등록 뒤 창이 안 닫힘"
            # 끈 사전 — 지금처럼 빨간 안내
            u.js(WORD + "c.dictId = arguments[0];", N)
            time.sleep(1.5)
            u.js("document.getElementById('addWordBtn').click();")
            WebDriverWait(d, 10).until(lambda x: u.js(WORD + "return c.addWordModalShow;"))
            time.sleep(0.8)
            txt, color = u.word_nm_check("수")
            print("  N 안내", txt, color)
            assert txt.startswith("이미 승인된 단어입니다.") and color == RED, (txt, color)
            u.dlg_button("취소")
            u.js(WORD + "c.dictId = arguments[0];", H)
            time.sleep(1)
        step("S2. 단어 등록 창 — H 회색 안내·등록 성공, N 빨간 안내", _s2)

        def _s3():
            u.open_menu("dsGroup", "nav_term", TERM + "return !!c && c.dictId === " + q(H) + ";")
            u.js("arguments[0].click();", d.find_element(By.CSS_SELECTOR, "#tab_term button[title='등록']"))
            WebDriverWait(d, 10).until(lambda x: u.js(TERM + "return c.addTermModalShow;"))
            time.sleep(0.8)
            d.find_element(By.CSS_SELECTOR, ".v-dialog--active input[placeholder='가동개시일자']").send_keys("고객수")
            # S2 에서 수(QTY)를 더해 H 의 '수' 는 3개 (CNT 가 사용 빈도로 1순위, NUM·QTY 는 같은 점수)
            WebDriverWait(d, 20).until(lambda x: u.js(TERM + "return !c.addTerm_analyzing && c.addTerm_wordListArr.length === 2 "
                                                             "&& c.addTerm_wordListArr[1].wordLst.length === 3;"))
            time.sleep(1)
            st = u.js(TERM + "return [c.addTerm_wordListArr.map(w => [w.wordNm, w.wordLst.map(x => x.wordEngAbrvNm + '/' + x.wordDesc)]), "
                      "c.addTerm_selected_word_list.map(s => s.map(x => x.wordEngAbrvNm)), c.addTerm_termEngAbrvNm];")
            print("  분석", st)
            assert st[0][0] == ["고객", ["CUST/고객"]] and st[0][1][0] == "수" and st[0][1][1][0] == "CNT/개수", st
            assert sorted(st[0][1][1]) == ["CNT/개수", "NUM/수량", "QTY/수 — 수량 단위 시험"], st
            assert st[1:] == [[["CUST"], ["CNT"]], "CUST_CNT"], st
            box = ("const h = [...document.querySelectorAll('.v-dialog--active h4')].find(x => x.innerText.trim().startsWith('수'));"
                   "const t = h.closest('.col').querySelector('table');")
            dom = u.js(box + "return [h.innerText.replace(/\\s+/g, ' ').trim(), [...t.querySelectorAll('tbody tr')].map(r => r.innerText.replace(/\\s+/g, ' ').trim()), "
                       "t.querySelectorAll('tbody .mdi-checkbox-marked').length];")
            print("  구성 단어 표", dom)
            assert "등록됨" in dom[0] and "동음이의어 3" in dom[0] and len(dom[1]) == 3 and dom[2] == 1, dom
            # 다른 후보(NUM)를 고르면 하나만 골라지고 용어 영문약어가 바뀐다
            u.js(box + "const r = [...t.querySelectorAll('tbody tr')].find(r => r.innerText.includes('NUM')); "
                 "r.querySelector('.v-data-table__checkbox, .v-simple-checkbox').click();")
            WebDriverWait(d, 10).until(lambda x: u.js(TERM + "return c.addTerm_termEngAbrvNm === 'CUST_NUM';"))
            time.sleep(0.5)
            st = u.js(TERM + box + "return [t.querySelectorAll('tbody .mdi-checkbox-marked').length, "
                      "c.addTerm_selected_word_list[1].map(x => x.wordEngAbrvNm)];")
            print("  NUM 고른 뒤", st)
            assert st == [1, ["NUM"]], st
            u.js(TERM + "c.addTerm_termDesc = '고객 수 시험'; c.addTerm_domainNm = 'W10수량';")
            time.sleep(0.5)
            u.dlg_button("등록")
            got = wait_until(lambda: psql("select t.terms_eng_abrv_nm || '|' || string_agg(w.word_eng_abrv_nm, ',' order by tw.word_ord) "
                                          "from quality.tb_terms t join quality.tb_terms_words tw on tw.terms_id = t.terms_id "
                                          "join quality.tb_word w on w.word_id = tw.word_id "
                                          f"where t.dict_id={q(H)} and t.terms_nm='고객수' group by t.terms_eng_abrv_nm"), 15)
            print("  등록", got)
            assert got == "CUST_NUM|CUST,NUM", got
        step("S3. 용어 등록 \"고객수\" — 동음이의어 후보 2행·1순위 선택·하나만 고르기·용어 영문약어·등록", _s3)

        def _s4():
            u.open_menu("autoStdGroup", "nav_termRecommend", REC + "return !!c && c.dictList.length > 0;")
            u.js(REC + "c.dictId = arguments[0]; c.inputText = '식별수'; c.startAnalysis();", H)
            WebDriverWait(d, 60).until(lambda x: u.js(REC + "return c.currentStep == 3 && c.analysisResults.length === 1;"))
            time.sleep(1)
            st = u.js(REC + "const r = c.analysisResults[0]; return [r.words.map(w => [w.wordNm, w.status, w.selected && w.selected.wordEngAbrvNm, "
                      "(w.candidates || []).length]), r.recommendedEngAbrvNm];")
            print("  분석", st)
            assert st == [[["식별", "MATCHED", "IDNT", 1], ["수", "MATCHED", "CNT", 3]], "IDNT_CNT"], st
            tr = [t for t in d.find_elements(By.CSS_SELECTOR, "#tab_termRecommend tbody tr") if "식별수" in t.text][0]
            u.js("arguments[0].click();", [b for b in tr.find_elements(By.CSS_SELECTOR, "button") if b.text.strip() == "수정"][0])
            WebDriverWait(d, 10).until(lambda x: u.js(REC + "return c.editDialog;"))
            time.sleep(1)
            dom = u.js("const dl = document.querySelector('.v-dialog--active'); const s = dl.querySelectorAll('.homonym-select'); "
                       "return [s.length, s.length ? s[0].querySelector('.homonym-text').innerText.trim() : null, "
                       "[...dl.querySelectorAll('.term-homonym-chip')].map(x => x.innerText.trim())];")
            print("  편집 표", dom)
            assert dom == [1, "CNT — 개수", ["동음이의어 3"]], dom
            u.js("document.querySelector('.v-dialog--active .homonym-select .v-input__slot').click();")
            WebDriverWait(d, 10).until(lambda x: u.js("return document.querySelectorAll('.menuable__content__active .v-list-item').length > 0;"))
            time.sleep(0.5)
            items = u.js("return [...document.querySelectorAll('.menuable__content__active .v-list-item')].map(x => x.innerText.trim());")
            print("  선택 목록", items)
            assert items[0] == "CNT — 개수" and sorted(items) == ["CNT — 개수", "NUM — 수량", "QTY — 수 — 수량 단위 시험"], items
            u.js("[...document.querySelectorAll('.menuable__content__active .v-list-item')].find(x => x.innerText.trim().startsWith('NUM')).click();")
            time.sleep(0.8)
            st = u.js(REC + "const w = c.editingItem.words[1]; return [w.selected.wordEngAbrvNm, c.editingItem.recommendedEngAbrvNm, c.editPreviewEng];")
            row = u.js("return [...document.querySelectorAll('.v-dialog--active tbody tr')][1].innerText.replace(/\\s+/g, ' ').trim();")
            print("  NUM 고른 뒤", st, row)
            assert st == ["NUM", "IDNT_NUM", "IDNT_NUM"] and "NUMERIC" in row, (st, row)
            u.dlg_button("확인")
            WebDriverWait(d, 10).until(lambda x: u.js(REC + "return !c.editDialog;"))
            time.sleep(0.5)
            tr = [t for t in d.find_elements(By.CSS_SELECTOR, "#tab_termRecommend tbody tr") if "식별수" in t.text][0]
            assert "IDNT_NUM" in tr.text, tr.text
        step("S4. 한글컬럼 일괄 표준화 \"식별수\" — 영문약어 선택 목록·칩·바꾸면 용어 영문약어", _s4)

        def _s5():
            r = okc(sa.post(BASE + "/api/diag/startDiag", json={"dataModelId": DM}, timeout=20))
            job = r["contents"]
            wait_until(lambda: psql(f"select status from quality.tb_diag_job where diag_job_id={q(job)}") in ("DONE", "ERROR"), 90, 2)
            assert psql(f"select status from quality.tb_diag_job where diag_job_id={q(job)}") == "DONE"
            res = psql("select string_agg(obj_nm || ':' || diag_type || ':' || coalesce(std_value, '') || ':' || coalesce(actual_value, ''), ';' order by obj_nm, diag_type) "
                       f"from quality.tb_diag_result where diag_job_id={q(job)}")
            print("  진단 결과", res)
            assert res == "T_C:TERM_KR_NM_MISMATCH:고객번호, 고객식별번호:거래처번호;T_D:DATA_LEN_MISMATCH:20:30", res
            u.open_menu("diagGroup", "nav_dataDiagResult", DIAG + "return !!c;")
            u.js(DIAG + "c.selectJob(arguments[0]); c.activeTab = 2;", job)
            WebDriverWait(d, 20).until(lambda x: u.js(DIAG + "return c.detailList.length === 4;"))
            time.sleep(1)
            det = u.js(DIAG + "return c.detailList.slice().sort((a, b) => a.objNm < b.objNm ? -1 : 1).map(i => [i.objNm, i.attrNmKr, i.stdTermsNm, i.diagTypeList.join(',')]);")
            print("  컬럼 상세", det)
            assert det == [["T_A", "고객번호", "고객번호", ""], ["T_B", "고객식별번호", "고객식별번호", ""],
                           ["T_C", "거래처번호", "고객번호", "TERM_KR_NM_MISMATCH"], ["T_D", "고객번호", "고객번호", "DATA_LEN_MISMATCH"]], det
            body = d.find_element(By.ID, "tab_dataDiagResult").text
            assert "거래처번호" in body and "고객식별번호" in body, "컬럼 상세 화면에 행이 안 보임"
        step("S5. H 모델 표준 진단 결과 — 한글명이 같은 용어로 판정·한글명 불일치 표준값·길이 불일치", _s5)
    finally:
        d.quit()

    du = new_driver(USER, FX["upw"])
    uu = Ui(du)
    try:
        def _s6():
            uu.open_menu("dsGroup", "nav_word", WORD + "return !!c && c.dictId === " + q(H) + ";")
            btn = du.find_element(By.ID, "addWordBtn")
            assert btn.text.strip() == "등록 신청", btn.text
            uu.js("arguments[0].click();", btn)
            WebDriverWait(du, 10).until(lambda x: uu.js(WORD + "return c.addWordModalShow;"))
            time.sleep(0.8)
            txt, color = uu.word_nm_check("수")
            print("  안내", txt, color)
            assert txt.startswith("같은 이름의 단어가 있습니다: 수(CNT), 수(NUM), 수(QTY) — 동음이의어 허용 사전이라") and color == GREY, (txt, color)
            uu.js(WORD + "c.addWord_wordEngAbrvNm = 'SU'; c.addWord_wordEngNm = 'SU'; c.addWord_wordDesc = '수 — 일반 사용자 시험';")
            uu.dlg_button("등록")
            # 승인 대기 = 단어 APRV_YN N (승인 요청 행은 승인·반려 때 생긴다)
            got = wait_until(lambda: psql(f"select aprv_yn || ':' || cret_user_id || ':' || word_nm_uniq_yn || ':' || word_id from quality.tb_word "
                                          f"where dict_id={q(H)} and word_nm='수' and word_eng_abrv_nm='SU'"), 15)
            print("  신청", got)
            assert got.startswith(f"N:{USER}:N:"), got
            assert psql(f"select count(*) from quality.tb_word where dict_id={q(H)} and word_nm='수'") == "4"
            assert uu.js(WORD + "return !c.addWordModalShow;"), "신청 뒤 창이 안 닫힘"
            # 관리자 승인 (표준 승인 화면과 같은 API)
            w = got.split(":")[3]
            okc(sa.post(BASE + "/api/std/putStdAprvStat", json=[{"reqTp": "WORD", "reqItemId": w, "reqItemNm": "수", "aprvStat": 2}], timeout=30))
            st = psql(f"select w.aprv_yn || ':' || a.aprv_stat || ':' || a.req_user_id from quality.tb_word w join quality.tb_aprv_stats a on a.req_item_id = w.word_id "
                      f"where w.word_id={q(w)}")
            print("  승인", st)
            assert st == f"Y:2:{USER}", st
        step("S6. 시험 일반 사용자 — 동음이의어 안내·등록 신청 → 승인 대기 → 관리자 승인", _s6)
    finally:
        du.quit()


if __name__ == "__main__":
    t0 = time.time()
    try:
        main()
    finally:
        cleanup()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({time.time() - t0:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
