"""
104번 WP7-23 · 108 §4.23-5 (§4.29) — 시스템 단위 표준 후보 (표준화 도구 > 시스템 단위 표준 후보)

시험 데이터(psql·API, 끝에서 모두 지움):
  사전 A·B(서로 남)·C(A 의 하위). A: 단어 고객 CUST·번호 NO·명 NM, 도메인 W10번호 VARCHAR(20)·W10명 VARCHAR(50),
     용어 고객번호 CUST_NO·고객명 CUST_NM. B: 단어 고객 CSTMR·번호 NO, 도메인 W10번호 VARCHAR(15), 용어 고객번호 CSTMR_NO. C: 비어 있음(상위 A).
  시스템 P(상위)·Q(P 의 하위)·E(모델 없음)·N(비표준 없는 모델만).
  모델 M1(P·A, 이름 40자)·M2(Q·B)·M4(P·C·표준 준수 필수)·M6(N·A). 일반 사용자 w10stc<tag>(API).

  T1. 메뉴·조회 전 안내·[조회] 비활성, 시스템을 바꾸면 고른 모델 비움·모델 목록은 하위 시스템 모델만, P 조회 → 모델 요약(사전·필수 칩·건수),
      같은 한글명에 M1 CUST_NO·M2 CSTMR_NO 1순위, M4 상위 사전 표시, 적용 뒤 칸, 제외 행 없음, 1366 폭 가로 스크롤 없음
  T2. 같은 테이블 영문명 겹침 → 빨간 행·[적용] 은 "영문명 겹침" 안내만, 목록에서 "(적용 안 함)" 을 고르면 풀림
  T3. 두 모델 적용 → 확인 창 문구 → 결과 → 다시 조회에 빠짐·DB(영문명·타입·표준 Y/Y·이력 TIER1 APPROVED)
  T4. 일부 실패(조회 뒤 psql 로 한글명 바꿈) → 확인 창 표준 준수 필수 문구·"일부 적용 실패"·결과 칸 문구 → [조회] 하면 지워짐
  T5. 테이블 이름 → 컬럼 탭(그 모델·소유자·테이블)
  T6. 시험 일반 사용자 — 메뉴, 자기 DRAFT "승인 대기" 칩, 남의 DRAFT 없음, 확인 창 결재 문구, 적용 → 이력 DRAFT·내 변경 신청
  T7. 빈 상태 — 모델 없는 시스템, 비표준 없는 시스템
"""
import base64, json, os, secrets, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
USER = "w10stc" + TAG
OTHER = "w10stcoth" + TAG
SYS = {k: f"W10STC{k}{TAG}" for k in "PQEN"}
SYSNM = {k: f"W10STC시스템{k}{TAG}" for k in "PQEN"}
DM = {k: f"W10STCM{k}{TAG}" for k in "1246"}
DMNM = {"1": f"W10STC_{TAG}_M1_고객관리시스템통합데이터모델장기보관용시험명칭", "2": f"W10STC_{TAG}_M2",
        "4": f"W10STC_{TAG}_M4", "6": f"W10STC_{TAG}_M6"}
DICTNM = {k: f"W10STC사전{k}{TAG}" for k in "ABC"}
LONG_T = "T_CUSTOMER_CONTRACT_HISTORY_DETAIL_ARCHIVE"
LONG_TKR = "고객계약이력상세보관테이블고객계약이력상세보관테이블"
LONG_C = "CUSTOMER_IDENTIFICATION_NUMBER_VALUE"
results = []
FX = {}

# 탭 안 Vue 컴포넌트 — 자식부터 찾고 없으면 위로 (첫 요소가 keep-alive 로 잡히는 화면이 있다)
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
C = VM + "const c = vm('tab_sysTermCand', 'DSSysTermCand');"
# 목록 행 — 칸: 0 데이터 모델 / 1 테이블 / 2 컬럼 / 3 타입 / 4 용어 후보 / 5 적용 뒤 / 6 결과
# (선택 칸의 innerText 앞에 붙는 폭 없는 공백은 뺀다)
ROWS = ("return [...document.querySelectorAll('#sysTermCandTable tbody tr')].map(tr => ({ cls: tr.className, "
        "cells: [...tr.children].map(td => td.innerText.replace(/\\u200b/g, '').trim()) }));")


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


def setup():
    sa = FX["sa"] = api_login("space", "123")
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    d = FX["dict"] = {}
    for k in "ABC":
        body = {"dictNm": DICTNM[k], "termLastWordClsfYn": "N"}
        if k == "C":
            body["parentDictId"] = d["A"]
        d[k] = json.loads(okc(sa.post(BASE + "/api/dict/save", json=body, timeout=20))["contents"])["dictId"]
    for k, doms in (("A", (("W10번호", 20), ("W10명", 50))), ("B", (("W10번호", 15),))):
        psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('W10STCG{k}{TAG}', 'W10그룹', 'N', {q(d[k])})")
        psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values ('W10STCC{k}{TAG}', 'W10분류', 'W10그룹', 'N', {q(d[k])})")
        for i, (nm, ln) in enumerate(doms):
            psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, dict_id, cret_dt) "
                 f"values ('W10STCD{k}{i}{TAG}', {q(nm)}, 'W10그룹', 'W10분류', 'x', 'VARCHAR', {ln}, 'Y', {q(d[k])}, {now})")
    for k, words in (("A", (("고객", "CUST"), ("번호", "NO"), ("명", "NM"))), ("B", (("고객", "CSTMR"), ("번호", "NO")))):
        for nm, ab in words:
            okc(sa.post(BASE + "/api/std/createWord", json={"wordNm": nm, "wordEngAbrvNm": ab, "wordEngNm": ab.title() + "w", "wordDesc": nm,
                                                            "wordClsfYn": "N", "dictId": d[k], "allophSynmLst": []}, timeout=20))

    def wid(k, nm):
        return psql(f"select word_id from quality.tb_word where dict_id={q(d[k])} and word_nm={q(nm)}")
    for k, nm, ab, dom, ws in (("A", "고객번호", "CUST_NO", "W10번호", ("고객", "번호")), ("A", "고객명", "CUST_NM", "W10명", ("고객", "명")),
                               ("B", "고객번호", "CSTMR_NO", "W10번호", ("고객", "번호"))):
        wl = [{"wordId": wid(k, w), "wordNm": w, "wordOrd": i} for i, w in enumerate(ws)]
        okc(sa.post(BASE + "/api/std/createTerms", json={"termsNm": nm, "termsEngAbrvNm": ab, "termsDesc": nm, "domainNm": dom,
                                                        "wordList": wl, "dictId": d[k]}, timeout=20))

    for k, parent in (("P", None), ("Q", "P"), ("E", None), ("N", None)):
        psql(f"insert into quality.tb_sys_info (sys_cd, p_sys_cd, sys_tp, sys_nm, cret_dt) values ({q(SYS[k])}, {q(SYS[parent] if parent else None)}, 1, {q(SYSNM[k])}, {now})")
    for k, s, dk, req in (("1", "P", "A", "N"), ("2", "Q", "B", "N"), ("4", "P", "C", "Y"), ("6", "N", "A", "N")):
        psql(f"insert into quality.tb_data_model (dm_id, dm_nm, dm_sys_cd, ver, use_yn, dict_id, stnd_req_yn, cret_dt) "
             f"values ({q(DM[k])}, {q(DMNM[k])}, {q(SYS[s])}, '1', 'Y', {q(d[dk])}, {q(req)}, {now})")
    objs = [("1", "T_CUST", "고객"), ("1", "T_OV", "겹침"), ("1", "T_FAIL", "실패"), ("1", "T_USR", "사용자"), ("1", LONG_T, LONG_TKR),
            ("2", "T_CUST", "고객"), ("4", "T_CUST", "고객"), ("6", "T_STD", "표준")]
    for dm, o, kr in objs:
        psql(f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) "
             f"values ({q(DM[dm])}, 'APP', {q(o)}, {q(kr)}, 1, 'Y')")
    # (모델, 테이블, 컬럼, 한글명, 길이, 순서, 표준, 승인 상태, 요청자)
    attrs = [("1", "T_CUST", "CUST_NUM", "고객번호", 30, 1, "N", None, None),
             ("1", "T_CUST", "STD_COL", "표준컬럼", 10, 2, "Y", None, None),
             ("1", "T_CUST", "NOKR", "", 10, 3, "N", None, None),
             ("1", "T_OV", "CUST_N1", "고객번호", 30, 1, "N", None, None),
             ("1", "T_OV", "CUST_N2", "고객번호", 30, 2, "N", None, None),
             ("1", "T_OV", "CUST_NO", "가나다라", 30, 3, "N", None, None),
             ("1", "T_FAIL", "C_KR", "고객번호", 30, 1, "N", None, None),
             ("1", "T_USR", "U_NUM", "고객번호", 30, 1, "N", None, None),
             ("1", "T_USR", "U_DRAFT", "고객명", 30, 2, "N", "DRAFT", USER),
             ("1", "T_USR", "O_DRAFT", "고객명", 30, 3, "N", "DRAFT", OTHER),
             ("1", LONG_T, LONG_C, "고객번호", 30, 1, "N", None, None),
             ("1", LONG_T, "CUST_LONG_KR", "고객계약이력상세정보고객계약이력상세정보값", 30, 2, "N", None, None),
             ("2", "T_CUST", "CUST_NUM", "고객번호", 30, 1, "N", None, None),
             ("4", "T_CUST", "CUST_NUM", "고객번호", 30, 1, "N", None, None),
             ("6", "T_STD", "STD_ONLY", "고객번호", 30, 1, "Y", None, None)]
    vals = ",".join(f"({q(DM[dm])}, 'APP', {q(o)}, {q(a)}, {q(kr)}, 'VARCHAR', {ln}, {od}, 'N', 'Y', 'Y', {q(st)}, {q(ap or 'APPROVED')}, {q(rq)})"
                    for dm, o, a, kr, ln, od, st, ap, rq in attrs)
    psql("insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, attr_ord, pk_yn, nullable_yn, "
         "use_yn, terms_stnd_yn, aprv_status, requester_user_id) values " + vals)

    FX["upw"] = "Zz" + secrets.token_hex(6)
    okc(sa.post(BASE + "/api/login/createUser", json={"id": USER, "password": b64(FX["upw"]), "name": "W10 시스템 후보 시험 " + USER,
                                                       "email": "", "admin": False}, timeout=10))


def cleanup():
    dms = ",".join(q(v) for v in DM.values())
    psql(f"delete from quality.tb_notification where link_id in (select submission_id from quality.tb_data_model_change_history where dm_id in ({dms}) and submission_id is not null)")
    psql(f"delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where dm_id in ({dms}))")
    psql(f"delete from quality.tb_aprv_doc where dm_id in ({dms})")
    for t in ("tb_udp_val", "tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id in ({dms})")
    psql(f"delete from quality.tb_data_model where dm_id in ({dms})")
    psql("delete from quality.tb_sys_info where sys_cd in (" + ",".join(q(v) for v in SYS.values()) + ")")
    ds = f"select dict_id from quality.tb_std_dict where dict_nm like {q('W10STC사전%' + TAG)}"
    ids = (f"select word_id from quality.tb_word where dict_id in ({ds}) union all select terms_id from quality.tb_terms where dict_id in ({ds}) "
           f"union all select domain_id from quality.tb_domain where dict_id in ({ds})")
    psql(f"delete from quality.tb_change_history where target_id in ({ids})")
    psql(f"delete from quality.tb_aprv_stats where req_item_id in ({ids})")
    for sql in (f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id in ({ds}))",
                f"delete from quality.tb_terms where dict_id in ({ds})", f"delete from quality.tb_word where dict_id in ({ds})",
                f"delete from quality.tb_domain where dict_id in ({ds})", f"delete from quality.tb_domain_clsf where dict_id in ({ds})",
                f"delete from quality.tb_domain_grp where dict_id in ({ds})",
                f"delete from quality.tb_std_dict where dict_nm like {q('W10STC사전%' + TAG)} and parent_dict_id is not null",
                f"delete from quality.tb_std_dict where dict_nm like {q('W10STC사전%' + TAG)}"):
        psql(sql)
    for t in ("tb_user_role", "tb_notification", "tb_user"):
        psql(f"delete from quality.{t} where user_id={q(USER)}")
    psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id={q(USER)}")


def attr(dm, obj, nm):
    return psql("select attr_nm_kr||'|'||data_type||'|'||data_len||'|'||coalesce(terms_stnd_yn,'')||'|'||coalesce(domain_stnd_yn,'') "
                f"from quality.tb_data_model_attr where dm_id={q(DM[dm])} and obj_owner='APP' and obj_nm={q(obj)} and attr_nm={q(nm)}")


def hist(dm, obj, nm):
    return psql("select string_agg(change_type||':'||change_tier||':'||aprv_status, ',' order by change_seq) from quality.tb_data_model_change_history "
                f"where dm_id={q(DM[dm])} and obj_nm={q(obj)} and attr_nm={q(nm)}")


def new_driver(user, pw):
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.set_capability("goog:loggingPrefs", {"browser": "ALL"})
    d = webdriver.Edge(options=headless(opts))
    d.set_window_size(1366, 768)
    d.get(BASE)
    time.sleep(2)
    WebDriverWait(d, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
    d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(pw)
    d.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
    WebDriverWait(d, 15).until(lambda x: "/signin" not in x.current_url)
    time.sleep(3)
    return d


class Screen:
    """한 드라이버의 시스템 단위 표준 후보 탭 조작"""

    def __init__(self, d):
        self.d = d

    def js(self, script, *args):
        return self.d.execute_script(script, *args)

    def open(self):
        g = self.d.find_element(By.CSS_SELECTOR, "#autoStdGroup")
        if "v-list-group--active" not in (g.get_attribute("class") or ""):
            self.js("arguments[0].click();", g.find_element(By.CSS_SELECTOR, ".v-list-group__header"))
            time.sleep(1)
        nav = self.d.find_element(By.ID, "nav_sysTermCand")
        assert nav.is_displayed() and nav.text.strip() == "시스템 단위 표준 후보", "메뉴가 안 보임"
        self.js("arguments[0].click();", nav)
        WebDriverWait(self.d, 10).until(lambda x: self.js(C + "return !!c && c.sysList.length > 0 && c.modelList.length > 0;"))
        time.sleep(0.5)

    def set_sys(self, key):
        self.js(C + "c.filter.sysCd = arguments[0];", SYS[key])
        time.sleep(0.3)

    def search(self):
        self.js("arguments[0].click();", self.d.find_element(By.ID, "sysTermCandSearch"))
        time.sleep(0.3)
        WebDriverWait(self.d, 30).until(lambda x: self.js(C + "return !c.loading && c.searched;"))
        time.sleep(0.5)

    def rows(self):
        return self.js(ROWS)

    def row(self, dm, obj, attr_nm):
        got = [r for r in self.rows() if r["cells"][0].startswith(DMNM[dm]) and r["cells"][1].split("\n")[0] == "APP." + obj
               and r["cells"][2].split("\n")[0].split(" ")[0] == attr_nm]
        assert len(got) == 1, f"행 없음 M{dm} {obj}.{attr_nm}: {[r['cells'][:3] for r in self.rows()]}"
        return got[0]

    def row_el(self, dm, obj, attr_nm):
        for tr in self.d.find_elements(By.CSS_SELECTOR, "#sysTermCandTable tbody tr"):
            c = [td.text.strip() for td in tr.find_elements(By.CSS_SELECTOR, "td")]
            if c[0].startswith(DMNM[dm]) and c[1].split("\n")[0] == "APP." + obj and c[2].split("\n")[0].split(" ")[0] == attr_nm:
                return tr
        raise AssertionError(f"행 없음 M{dm} {obj}.{attr_nm}")

    def set_pick(self, dm, obj, attr_nm, eng):
        """그 행의 고른 후보 (eng=None 이면 적용 안 함)"""
        n = self.js(C + "const it = c.items.find(i => i.dmId === arguments[0] && i.objNm === arguments[1] && i.attrNm === arguments[2]);"
                    "if (!it) return -1; it.pick = arguments[3] === null ? null : it.candidates.findIndex(t => t.termsEngAbrvNm === arguments[3]);"
                    "return it.pick;", DM[dm], obj, attr_nm, eng)
        assert n is None or n >= 0, (dm, obj, attr_nm, eng, n)
        time.sleep(0.2)

    def pick_none(self):
        self.js("arguments[0].click();", self.d.find_element(By.ID, "btn-sysTermCand-pickNone"))
        time.sleep(0.3)

    def swal(self, title, timeout=20):
        WebDriverWait(self.d, timeout).until(lambda x: self.js(
            "const t = document.querySelector('.swal2-popup.swal2-show .swal2-title'); return t && t.innerText.trim() === arguments[0];", title))
        return self.js("const h = document.querySelector('.swal2-popup .swal2-html-container'); return h ? h.innerText.trim() : '';")

    def swal_click(self, cls="swal2-confirm"):
        self.js(f"document.querySelector('.swal2-popup .{cls}').click();")
        WebDriverWait(self.d, 10).until(lambda x: not self.js("return !!document.querySelector('.swal2-popup.swal2-show');"))
        time.sleep(0.3)

    def wait_idle(self):
        WebDriverWait(self.d, 30).until(lambda x: self.js(C + "return !c.loading && !c.applying;"))
        time.sleep(0.5)

    def no_data(self):
        return self.js("const e = document.querySelector('#sysTermCandTable .v-data-table__empty-wrapper'); return e ? e.innerText.trim() : null;")


def main():
    setup()
    sa = FX["sa"]
    d = new_driver("space", "123")
    s = Screen(d)
    try:
        def _t1():
            s.open()
            assert s.no_data() == "시스템을 고르고 [조회] 하세요.", s.no_data()
            assert d.find_element(By.ID, "sysTermCandSearch").get_attribute("disabled"), "시스템 없이 [조회] 활성"
            ap = d.find_element(By.ID, "btn-sysTermCand-apply")
            assert ap.text.strip() == "적용 (0)" and ap.get_attribute("disabled"), ap.text
            assert s.js(C + "return c.modelOptions.length;") == 0
            # 시스템을 바꾸면 고른 모델을 비운다. 모델 목록은 그 시스템과 하위 시스템의 모델만
            s.set_sys("P")
            opts = s.js(C + "return c.modelOptions.map(m => m.dataModelId).sort();")
            assert opts == sorted([DM["1"], DM["2"], DM["4"]]), opts
            s.js(C + "c.filter.dmIds = [arguments[0]];", DM["1"])
            s.set_sys("Q")
            assert s.js(C + "return c.filter.dmIds.length;") == 0, "시스템을 바꿔도 고른 모델이 남음"
            assert s.js(C + "return c.modelOptions.map(m => m.dataModelId);") == [DM["2"]]
            s.set_sys("P")
            s.search()
            summ = s.js("return [...document.querySelectorAll('#sysTermCandModels tbody tr')].map(tr => [...tr.children].map(td => td.innerText.trim()));")
            print("  모델 요약", summ)
            assert summ == [[SYSNM["P"], DMNM["1"], DICTNM["A"], "-", "8"], [SYSNM["P"], DMNM["4"], DICTNM["C"], "필수", "1"],
                            [SYSNM["Q"], DMNM["2"], DICTNM["B"], "-", "1"]], summ
            cnt = d.find_element(By.CSS_SELECTOR, "#tab_sysTermCand .stc-count").text.strip()
            print("  건수", cnt)
            rows = s.rows()
            names = [(r["cells"][1].split("\n")[0], r["cells"][2].split("\n")[0]) for r in rows]
            for bad in (("APP.T_CUST", "STD_COL"), ("APP.T_CUST", "NOKR"), ("APP.T_USR", "O_DRAFT"), ("APP.T_USR", "U_DRAFT"), ("APP.T_STD", "STD_ONLY")):
                assert not [n for n in names if n[0] == bad[0] and n[1].startswith(bad[1])], f"빠져야 할 행 {bad}"
            assert len(rows) == 10, names
            r1, r2, r4 = s.row("1", "T_CUST", "CUST_NUM"), s.row("2", "T_CUST", "CUST_NUM"), s.row("4", "T_CUST", "CUST_NUM")
            print("  M1", r1["cells"], "\n  M2", r2["cells"], "\n  M4", r4["cells"])
            assert r1["cells"][0] == DMNM["1"] + "\n" + DICTNM["A"] and r1["cells"][1] == "APP.T_CUST\n고객"
            assert r1["cells"][2] == "CUST_NUM\n고객번호" and r1["cells"][3] == "VARCHAR(30)"
            assert r1["cells"][4] == "고객번호 (CUST_NO) — 한글명 일치", r1["cells"][4]
            assert r2["cells"][4] == "고객번호 (CSTMR_NO) — 한글명 일치", r2["cells"][4]
            assert r4["cells"][4] == f"고객번호 (CUST_NO) — 한글명 일치 · 상위 사전 {DICTNM['A']}", r4["cells"][4]
            assert r1["cells"][5] == "영문명 → CUST_NO\n타입 → VARCHAR(20)", r1["cells"][5]
            assert r2["cells"][5] == "영문명 → CSTMR_NO\n타입 → VARCHAR(15)", r2["cells"][5]
            assert s.row("1", "T_OV", "CUST_NO")["cells"][4] == "후보 없음"
            n_cand = sum(1 for r in rows if r["cells"][4] != "후보 없음")
            assert cnt == f"비표준 컬럼 10개 · 후보 있음 {n_cand}개 · 적용할 컬럼 {n_cand}개", cnt
            assert ap.text.strip() == f"적용 ({n_cand})" and not ap.get_attribute("disabled")
            # 1366 폭 — 목록·탭 가로 스크롤 없음 (긴 모델·테이블·컬럼 이름은 줄바꿈)
            ov = s.js("const w = document.querySelector('#sysTermCandTable .v-data-table__wrapper'); const t = document.getElementById('tab_sysTermCand');"
                      "return [w.scrollWidth, w.clientWidth, t.scrollWidth, t.clientWidth, window.innerWidth];")
            print("  폭", ov)
            assert ov[0] <= ov[1] and ov[2] <= ov[3], f"가로 스크롤 {ov}"
            for b in ("btn-sysTermCand-pickFirst", "btn-sysTermCand-pickNone", "btn-sysTermCand-apply", "sysTermCandSearch"):
                w = s.js("const e = document.getElementById(arguments[0]); const c = e.querySelector('.v-btn__content'); return [c.scrollWidth, e.clientWidth];", b)
                assert w[0] <= w[1], f"{b} 글자 잘림 {w}"
            # [모두 적용 안 함] → 0, [1순위로 모두 고르기] → 다시
            s.pick_none()
            assert ap.text.strip() == "적용 (0)" and ap.get_attribute("disabled") and s.row("1", "T_CUST", "CUST_NUM")["cells"][4] == "(적용 안 함)"
            assert s.row("1", "T_CUST", "CUST_NUM")["cells"][5] == ""
            s.js("arguments[0].click();", d.find_element(By.ID, "btn-sysTermCand-pickFirst"))
            time.sleep(0.3)
            assert ap.text.strip() == f"적용 ({n_cand})"
        step("T1. 메뉴·조회 전 안내·시스템 바꾸면 모델 비움·P 조회 → 모델 요약·두 모델 다른 1순위·상위 사전·적용 뒤·제외 행 없음·1366 가로 스크롤 없음", _t1)

        def _t2():
            reds = lambda: sorted(r["cells"][2].split("\n")[0] for r in s.rows() if "row-overlap" in r["cls"])
            assert reds() == ["CUST_N1", "CUST_N2", "CUST_NO"], reds()
            assert s.row("1", "T_OV", "CUST_N1")["cells"][6] == "같은 테이블에 영문명 'CUST_NO' 가 겹칩니다"
            bg = s.js("return getComputedStyle(arguments[0].querySelector('td')).backgroundColor;", s.row_el("1", "T_OV", "CUST_N1"))
            assert bg == "rgb(255, 235, 238)", bg
            s.js("arguments[0].click();", d.find_element(By.ID, "btn-sysTermCand-apply"))
            body = s.swal("영문명 겹침")
            print("  ", body.replace("\n", " / "))
            assert f"· {DMNM['1']} · APP.T_OV — CUST_NO" in body, body
            s.swal_click()
            assert hist("1", "T_OV", "CUST_NO") == "" and attr("1", "T_OV", "CUST_N1").startswith("고객번호|VARCHAR|30|N"), "겹침인데 보냄"
            # 목록에서 직접 "(적용 안 함)" 고르기 — N2 를 풀어도 N1 은 바꾸지 않는 CUST_NO 행과 겹친다
            for nm, left in (("CUST_N2", ["CUST_N1", "CUST_NO"]), ("CUST_N1", [])):
                tr = s.row_el("1", "T_OV", nm)
                s.js("arguments[0].click();", tr.find_element(By.CSS_SELECTOR, ".stc-cand .v-input__slot"))
                WebDriverWait(d, 5).until(lambda x: x.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item"))
                time.sleep(0.5)
                items = d.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
                txt = [s.js("return arguments[0].innerText.trim();", i) for i in items]
                assert txt[0] == "(적용 안 함)" and txt[1] == "고객번호 (CUST_NO) — 한글명 일치", txt
                s.js("arguments[0].click();", items[0])
                time.sleep(0.5)
                assert reds() == left, (nm, reds())
            assert s.row("1", "T_OV", "CUST_N1")["cells"][6] == ""
        step("T2. 같은 테이블 영문명 겹침 → 빨간 행·[적용] 은 '영문명 겹침' 안내만, '(적용 안 함)' 고르면 풀림", _t2)

        def _t3():
            s.pick_none()
            s.set_pick("1", "T_CUST", "CUST_NUM", "CUST_NO")
            s.set_pick("2", "T_CUST", "CUST_NUM", "CSTMR_NO")
            assert d.find_element(By.ID, "btn-sysTermCand-apply").text.strip() == "적용 (2)"
            s.js("arguments[0].click();", d.find_element(By.ID, "btn-sysTermCand-apply"))
            body = s.swal("시스템 단위 표준 후보 적용")
            print("  확인 창:", body.replace("\n", " / "))
            assert body == "모델 2개 · 테이블 2개 · 컬럼 2개에 적용합니다.\n영문명이 바뀌는 컬럼 2개 — 같은 모델의 인덱스·제약조건·FK 참조 이름도 함께 바뀝니다.", body
            s.swal_click("swal2-confirm")
            body = s.swal("2개 컬럼에 적용했습니다.")
            assert body == "테이블 2개", body
            s.swal_click()
            s.wait_idle()
            assert attr("1", "T_CUST", "CUST_NO") == "고객번호|VARCHAR|20|Y|Y" and attr("2", "T_CUST", "CSTMR_NO") == "고객번호|VARCHAR|15|Y|Y"
            assert hist("1", "T_CUST", "CUST_NO") == "MODIFY_ATTR:TIER1:APPROVED" and hist("2", "T_CUST", "CSTMR_NO") == "MODIFY_ATTR:TIER1:APPROVED"
            names = [(r["cells"][0].split("\n")[0], r["cells"][1].split("\n")[0]) for r in s.rows()]
            assert (DMNM["1"], "APP.T_CUST") not in names and (DMNM["2"], "APP.T_CUST") not in names, "적용한 컬럼이 다시 조회에 남음"
            assert len(names) == 8, names
            summ = s.js("return [...document.querySelectorAll('#sysTermCandModels tbody tr')].map(tr => tr.lastElementChild.innerText.trim());")
            assert summ == ["7", "1", "0"], summ
        step("T3. 두 모델 적용 → 확인 창 문구 → 결과 → 다시 조회에 빠짐·DB 표준 Y/Y·이력 TIER1 APPROVED", _t3)

        def _t4():
            s.pick_none()
            s.set_pick("1", "T_FAIL", "C_KR", "CUST_NO")
            s.set_pick("4", "T_CUST", "CUST_NUM", "CUST_NO")
            psql(f"update quality.tb_data_model_attr set attr_nm_kr='고객번호변경' where dm_id={q(DM['1'])} and obj_nm='T_FAIL' and attr_nm='C_KR'")
            s.js("arguments[0].click();", d.find_element(By.ID, "btn-sysTermCand-apply"))
            body = s.swal("시스템 단위 표준 후보 적용")
            assert body.endswith("표준 준수 필수 모델에서 표준 검사에 실패한 테이블은 적용되지 않습니다 (표준 예외는 컬럼 화면에서 신청)."), body
            assert body.startswith("모델 2개 · 테이블 2개 · 컬럼 2개에 적용합니다.")
            s.swal_click("swal2-confirm")
            body = s.swal("일부 적용 실패")
            print("  결과 창:", body.replace("\n", " / "))
            assert body == ("적용: 1개 컬럼 (테이블 1개)\n\n적용하지 못한 테이블 1개\n"
                            f"· {DMNM['1']} · APP.T_FAIL — 조회 뒤 컬럼이 바뀌었습니다 — 다시 조회하세요: T_FAIL.C_KR"), body
            s.swal_click()
            s.wait_idle()
            assert attr("4", "T_CUST", "CUST_NO") == "고객번호|VARCHAR|20|Y|Y", attr("4", "T_CUST", "CUST_NO")
            r = s.row("1", "T_FAIL", "C_KR")
            assert r["cells"][2] == "C_KR\n고객번호변경" and r["cells"][6] == "조회 뒤 컬럼이 바뀌었습니다 — 다시 조회하세요: T_FAIL.C_KR", r["cells"]
            col = s.js("return getComputedStyle(arguments[0].querySelectorAll('td')[6].querySelector('.stc-err')).color;", s.row_el("1", "T_FAIL", "C_KR"))
            assert col == "rgb(211, 47, 47)", col
            s.search()
            assert s.row("1", "T_FAIL", "C_KR")["cells"][6] == "", "[조회] 해도 결과 칸 문구가 남음"
        step("T4. 일부 실패 — 확인 창 표준 준수 필수 문구·'일부 적용 실패' 목록·결과 칸 빨간 문구(다시 조회까지), [조회] 하면 지워짐", _t4)

        def _t5():
            link = s.row_el("1", "T_USR", "U_NUM").find_element(By.CSS_SELECTOR, ".stc-link")
            assert link.text.strip() == "APP.T_USR"
            s.js("arguments[0].click();", link)
            col = VM + "const k = vm('tab_datamodelStatusColumn', 'DSDatamodelStatusColumn');"
            WebDriverWait(d, 15).until(lambda x: x.execute_script(col + "return !!k && k.selectedModelId === arguments[0] && !k.loadTable;", DM["1"]))
            time.sleep(1)
            st = d.execute_script(col + "return [k.searchOwner, k.searchOwnerMode, k.searchTable, k.searchTableMode];")
            assert st == ["APP", "exact", "T_USR", "exact"], st
            assert d.find_element(By.ID, "tab_datamodelStatusColumn").is_displayed()
            s.open()   # 다시 이 화면으로 (탭은 그대로)
            assert s.js(C + "return c.items.length;") == 7
        step("T5. 테이블 이름 → 컬럼 탭(그 모델·소유자·테이블 정확 일치)", _t5)

        def _t7():
            s.set_sys("E")
            s.search()
            assert s.no_data() == "이 시스템(하위 포함)에 데이터 모델이 없습니다.", s.no_data()
            assert not d.find_elements(By.ID, "sysTermCandModels")
            s.set_sys("N")
            s.search()
            assert s.no_data() == "비표준 컬럼이 없습니다.", s.no_data()
            summ = s.js("return [...document.querySelectorAll('#sysTermCandModels tbody tr')].map(tr => [...tr.children].map(td => td.innerText.trim()));")
            assert summ == [[SYSNM["N"], DMNM["6"], DICTNM["A"], "-", "0"]], summ
            assert d.find_element(By.CSS_SELECTOR, "#tab_sysTermCand .stc-count").text.strip() == "비표준 컬럼 0개 · 후보 있음 0개 · 적용할 컬럼 0개"
            sev = [l for l in d.get_log("browser") if l.get("level") == "SEVERE" and "favicon" not in l.get("message", "")]
            assert not sev, sev[:3]
        step("T7. 빈 상태 — 모델 없는 시스템·비표준 없는 시스템, 콘솔 오류 없음", _t7)
    finally:
        d.quit()

    def _t6():
        du = new_driver(USER, FX["upw"])
        su = Screen(du)
        try:
            su.open()
            su.set_sys("P")
            su.search()
            names = [" ".join(r["cells"][2].split()) for r in su.rows() if r["cells"][1].startswith("APP.T_USR")]
            print("  T_USR", names)
            assert names == ["U_NUM 고객번호", "U_DRAFT 승인 대기 고객명"], names
            chip = su.row_el("1", "T_USR", "U_DRAFT").find_element(By.CSS_SELECTOR, ".v-chip")
            assert chip.text.strip() == "승인 대기"
            su.pick_none()
            su.set_pick("1", "T_USR", "U_NUM", "CUST_NO")
            su.js("arguments[0].click();", du.find_element(By.ID, "btn-sysTermCand-apply"))
            body = su.swal("시스템 단위 표준 후보 적용")
            print("  확인 창:", body.replace("\n", " / "))
            assert body == ("모델 1개 · 테이블 1개 · 컬럼 1개에 적용합니다.\n영문명이 바뀌는 컬럼 1개 — 같은 모델의 인덱스·제약조건·FK 참조 이름도 함께 바뀝니다.\n"
                            "영문명·타입·길이가 바뀌는 변경은 데이터 모델 변경 승인을 거쳐 확정됩니다 (내 변경 신청에서 볼 수 있습니다)."), body
            su.swal_click("swal2-confirm")
            su.swal("1개 컬럼에 적용했습니다.")
            su.swal_click()
            su.wait_idle()
            assert attr("1", "T_USR", "CUST_NO") == "고객번호|VARCHAR|20|Y|Y"
            assert hist("1", "T_USR", "CUST_NO") == "MODIFY_ATTR:TIER1:DRAFT", hist("1", "T_USR", "CUST_NO")
            sess = api_login(USER, FX["upw"])
            drafts = sess.post(BASE + "/api/dmApproval/myDrafts", json={"dmId": DM["1"]}, timeout=20).json()
            assert [(x["objNm"], x["attrNm"], x["changeType"]) for x in drafts if x["objNm"] == "T_USR"] == [("T_USR", "CUST_NO", "MODIFY_ATTR")], drafts
        finally:
            du.quit()
    step("T6. 시험 일반 사용자 — 메뉴, 자기 DRAFT '승인 대기' 칩·남의 DRAFT 없음, 확인 창 결재 문구, 적용 → 이력 DRAFT·내 변경 신청", _t6)


if __name__ == "__main__":
    t0 = time.time()
    try:
        main()
    finally:
        cleanup()
        left = psql(f"select (select count(*) from quality.tb_data_model where dm_id like 'W10STCM%{TAG}')||'/'||"
                    f"(select count(*) from quality.tb_std_dict where dict_nm like 'W10STC사전%{TAG}')||'/'||"
                    f"(select count(*) from quality.tb_sys_info where sys_cd like 'W10STC%{TAG}')||'/'||"
                    f"(select count(*) from quality.tb_user where user_id={q(USER)})")
        print(f"\n정리 뒤 남은 모델/사전/시스템/사용자 = {left}")
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({time.time() - t0:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
