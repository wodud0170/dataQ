"""
104번 WP1 · 108 §4.23-7 (§4.33) — 결재자 "요청자 지정"(REQ_PICK)

시험 데이터(API·psql, 끝에서 모두 지움 — 결재선 5개는 처음 값을 읽어 두었다가 그대로 되돌림):
  사전 W10ARS사전<tag>, 일반 사용자 A(요청자)·B(결재자), 모델 W10ARSM<tag>(테이블 T1 — A 가 컬럼을 추가한 DRAFT 1건).
  데이터 모델 변경 결재선은 API 로 1단계 "모델 검토" 요청자 지정.

  S1. 관리 > 결재선(관리자) — 단어 결재선 1단계 방식을 "요청자 지정"으로 고르면 결재자 칸 "요청할 때 요청자가 고름"·저장 → DB REQ_PICK·결재자 ID 없음·왼쪽 요약
  S2. 일반 사용자 A 단어 등록 신청 — 창 안 "결재자 지정"(1단계 · 팀장 검토), 고르지 않고 [등록] → 안내 창·행 없음, B 고르고 [등록] → 결재 문서 1단계 결재자 B 하나
  S3. 일반 사용자 A 내 변경 신청 — "결재자 지정"(1단계 · 모델 검토), 고르지 않고 [선택 신청] → 안내 창·DRAFT 그대로, B 고르고 신청 → 신청·결재 문서 1단계 B
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
U = {"A": "w10arsa" + TAG, "B": "w10arsb" + TAG}
DICTNM = "W10ARS사전" + TAG
DM = "W10ARSM" + TAG
results = []
FX = {}

VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
LINE = VM + "const c = vm('tab_aprv_line', 'MMAprvLine');"
WORD = VM + "const c = vm('tab_word', 'DSWord');"
MYDM = VM + "const c = vm('tab_my_dm_changes', 'MMMyDataModelChanges');"


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


def save_line(reqTp, steps, useYn="Y"):
    return okc(FX["sa"].post(BASE + "/api/aprv/line/save", json={"reqTp": reqTp, "lineNm": "W10ARS " + reqTp, "useYn": useYn, "steps": steps}, timeout=20))


def doc_steps(target_id):
    return psql("select string_agg(s.step_no || ':' || s.aprvr_user_id, ',' order by s.step_no, s.aprvr_user_id) from quality.tb_aprv_doc d "
                f"join quality.tb_aprv_doc_step s on s.doc_id = d.doc_id where d.target_id={q(target_id)}")


def setup():
    sa = FX["sa"] = api_login("space", "123")
    FX["lines"] = json.loads(okc(sa.get(BASE + "/api/aprv/lines", timeout=20))["contents"])
    d = FX["dict"] = json.loads(okc(sa.post(BASE + "/api/dict/save", json={"dictNm": DICTNM, "termLastWordClsfYn": "N"}, timeout=20))["contents"])["dictId"]
    FX["pw"] = {}
    for k, uid in U.items():
        FX["pw"][k] = "Zz" + secrets.token_hex(6)
        okc(sa.post(BASE + "/api/login/createUser", json={"id": uid, "password": b64(FX["pw"][k]), "name": f"W10 요청자지정 시험 {k}", "email": "", "admin": False}, timeout=20))
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    psql(f"insert into quality.tb_data_model (dm_id, dm_nm, ver, use_yn, dict_id, stnd_req_yn, cret_dt) values ({q(DM)}, {q('W10ARS_' + TAG)}, '1', 'Y', {q(d)}, 'N', {now})")
    psql(f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) values ({q(DM)}, 'APP', 'T1', 'T1', 0, 'Y')")
    sA = api_login(U["A"], FX["pw"]["A"])
    okc(sA.post(BASE + "/api/dm/saveAttrs", json={"dataModelId": DM, "objOwner": "APP", "objNm": "T1",
                                                   "attrs": [{"mode": "ADD", "attrNm": "W10_SEL", "attrNmKr": "셀레니움", "dataType": "VARCHAR", "dataLen": 10,
                                                              "pkYn": "N", "fkYn": "N", "nullableYn": "Y"}]}, timeout=20))
    sA.post(BASE + "/logout", timeout=10)
    save_line("DM_CHANGE", [{"stepNm": "모델 검토", "aprvrTp": "REQ_PICK", "agreeYn": "N"}])
    save_line("WORD", [{"stepNm": "팀장 검토", "aprvrTp": "ORG_HEAD", "agreeYn": "N"}, {"stepNm": "", "aprvrTp": "ADMIN", "agreeYn": "N"}])


def cleanup():
    sa = FX.get("sa") or api_login("space", "123")
    for l in FX.get("lines", []):
        steps = [{"stepNm": s.get("stepNm") or "", "aprvrTp": s["aprvrTp"], "aprvrId": s.get("aprvrId"), "agreeYn": s.get("agreeYn") or "N"} for s in l["steps"]]
        r = sa.post(BASE + "/api/aprv/line/save", json={"reqTp": l["reqTp"], "lineNm": l.get("lineNm"), "useYn": l.get("useYn") or "N", "steps": steps}, timeout=20).json()
        if r.get("resultCode") != 200:
            print("  [결재선 되돌리기 실패]", l["reqTp"], r)
        # 처음에 없던 결재선(이름·단계 없음)은 되돌리며 생긴 행을 지운다
        if not l.get("lineNm") and not l["steps"]:
            psql(f"delete from quality.tb_aprv_line where req_tp={q(l['reqTp'])}")
    d = psql(f"select dict_id from quality.tb_std_dict where dict_nm={q(DICTNM)}")
    ids = f"select word_id from quality.tb_word where dict_id={q(d)}"
    subs = f"select submission_id from quality.tb_data_model_change_history where dm_id={q(DM)}"
    docs = f"select doc_id from quality.tb_aprv_doc where target_id in ({ids}) or target_id in ({subs})"
    for sql in (f"delete from quality.tb_notification where link_id in ({docs})",
                f"delete from quality.tb_aprv_doc_step where doc_id in ({docs})",
                f"delete from quality.tb_aprv_doc where doc_id in ({docs})",
                f"delete from quality.tb_aprv_stats where req_item_id in ({ids})",
                f"delete from quality.tb_data_model_change_history where dm_id={q(DM)}",
                f"delete from quality.tb_data_model_attr where dm_id={q(DM)}",
                f"delete from quality.tb_data_model_obj where dm_id={q(DM)}",
                f"delete from quality.tb_data_model where dm_id={q(DM)}",
                f"delete from quality.tb_word where dict_id={q(d)}",
                f"delete from quality.tb_std_dict where dict_nm={q(DICTNM)}"):
        psql(sql)
    for uid in U.values():
        for t in ("tb_user_role", "tb_notification", "tb_user"):
            psql(f"delete from quality.{t} where user_id={q(uid)}")
        psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id={q(uid)}")


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
    d.execute_script("sessionStorage.setItem('navidmeta.dictId', arguments[0]);", FX["dict"])
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

    def buttons(self, scope, text):
        return [b for b in self.d.find_elements(By.CSS_SELECTOR, scope + " button") if self.js("return arguments[0].innerText.trim();", b) == text]

    def click(self, el):
        self.js("arguments[0].click();", el)
        time.sleep(0.8)

    def swal_title(self, timeout=20):
        WebDriverWait(self.d, timeout).until(lambda x: self.js("return !!document.querySelector('.swal2-popup.swal2-show .swal2-title');"))
        return self.js("return document.querySelector('.swal2-popup.swal2-show .swal2-title').innerText.trim();")

    def swal_close(self):
        self.js("const b = document.querySelector('.swal2-popup .swal2-confirm'); if (b) b.click();")
        WebDriverWait(self.d, 10).until(lambda x: not self.js("return !!document.querySelector('.swal2-popup.swal2-show');"))
        time.sleep(0.3)

    def pick(self, input_id, text):
        """결재자 지정 칸(v-autocomplete)을 열고 항목을 고른다"""
        self.js("const e = document.getElementById(arguments[0]); e.scrollIntoView({block: 'center'}); "
                "e.closest('.v-input__slot').click();", input_id)
        WebDriverWait(self.d, 10).until(lambda x: self.js("return document.querySelectorAll('.menuable__content__active .v-list-item').length > 0;"))
        time.sleep(0.3)
        ok = self.js("const it = [...document.querySelectorAll('.menuable__content__active .v-list-item')].find(x => x.innerText.includes(arguments[0])); "
                     "if (!it) return false; it.click(); return true;", text)
        assert ok, f"결재자 목록에 {text} 없음"
        time.sleep(0.5)


def main():
    setup()
    d = new_driver("space", "123")
    u = Ui(d)
    try:
        def _s1():
            # 108 §4.35 — 요청 유형 7개(영역·시스템 추가)
            u.open_menu("mmGroup", "nav_aprvLine", LINE + "return !!c && c.lines.length === 7 && !!c.form;")
            assert u.js(LINE + "return c.form.reqTp;") == "WORD"
            # 1단계 방식 칸을 열어 "요청자 지정" 고르기
            u.js("const r = document.querySelectorAll('#tab_aprv_line tbody tr')[0]; r.querySelectorAll('.v-select .v-input__slot')[0].click();")
            WebDriverWait(d, 10).until(lambda x: u.js("return document.querySelectorAll('.menuable__content__active .v-list-item').length > 0;"))
            items = u.js("return [...document.querySelectorAll('.menuable__content__active .v-list-item')].map(x => x.innerText.trim());")
            print("  방식 목록", items)
            assert items == ["지정 사용자", "역할", "요청자 부서장", "관리자 전원", "요청자 지정"], items
            u.js("[...document.querySelectorAll('.menuable__content__active .v-list-item')].find(x => x.innerText.trim() === '요청자 지정').click();")
            time.sleep(0.5)
            cell = u.js("return document.querySelectorAll('#tab_aprv_line tbody tr')[0].querySelectorAll('td')[3].innerText.trim();")
            print("  결재자 칸", cell)
            assert cell == "요청할 때 요청자가 고름", cell
            u.click(u.buttons("#tab_aprv_line", "저장")[0])
            got = wait_until(lambda: psql("select string_agg(step_no || ':' || aprvr_tp || ':' || coalesce(aprvr_id, '-') || ':' || coalesce(step_nm, ''), ',' order by step_no) "
                                          "from quality.tb_aprv_line_step where req_tp='WORD'") == "1:REQ_PICK:-:팀장 검토,2:ADMIN:-:", 10)
            assert got
            WebDriverWait(d, 10).until(lambda x: "팀장 검토:요청자 지정" in u.js("return document.querySelector('#tab_aprv_line').innerText;"))
        step("S1. 결재선 — 단어 1단계 \"요청자 지정\" 고르기·결재자 칸 문구·저장·왼쪽 요약", _s1)
    finally:
        d.quit()

    du = new_driver(U["A"], FX["pw"]["A"])
    uu = Ui(du)
    try:
        def _s2():
            uu.open_menu("dsGroup", "nav_word", WORD + "return !!c && c.dictId === " + q(FX["dict"]) + ";")
            btn = du.find_element(By.ID, "addWordBtn")
            assert btn.text.strip() == "등록 신청", btn.text
            uu.click(btn)
            WebDriverWait(du, 10).until(lambda x: uu.js("return !!document.querySelector('.v-dialog--active .aprv-picker');"))
            time.sleep(0.8)
            box = uu.js("return document.querySelector('.v-dialog--active .aprv-picker').innerText.replace(/\\s+/g, ' ').trim();")
            print("  결재자 지정", box)
            assert box.startswith("결재자 지정 1단계 · 팀장 검토") and box.count("결재자 *") == 1, box
            uu.js(WORD + "c.addWord_wordNm = '셀요청'; c.addWord_wordEngAbrvNm = 'SELRQ'; c.addWord_wordEngNm = 'SELREQUEST'; c.addWord_wordDesc = '요청자 지정 셀레니움';")
            uu.click(uu.buttons(".v-dialog--active", "등록")[-1])
            t = uu.swal_title()
            print("  안내", t)
            assert t == "결재자를 지정하세요: 단어 1단계 (팀장 검토)", t
            uu.swal_close()
            assert psql(f"select count(*) from quality.tb_word where dict_id={q(FX['dict'])}") == "0"
            uu.pick("aprvPick_WORD_1", U["B"])
            uu.click(uu.buttons(".v-dialog--active", "등록")[-1])
            wid = wait_until(lambda: psql(f"select word_id from quality.tb_word where dict_id={q(FX['dict'])} and word_nm='셀요청'"), 15)
            st = wait_until(lambda: doc_steps(wid), 10)
            print("  결재 문서", st)
            assert st == f"1:{U['B']}," + ",".join("2:" + x for x in psql("select string_agg(user_id, ',' order by user_id) from quality.tb_user "
                                                                           "where adm_yn and coalesce(del_yn,false)=false").split(",")), st
        step("S2. 일반 사용자 단어 등록 신청 — 결재자 지정 칸·안 고르면 안내·B 지정 → 1단계 결재자 B", _s2)

        def _s3():
            uu.open_menu("myPageGroup", "nav_mypage_dm_changes", MYDM + "return !!c && c.drafts.length === 1;")
            WebDriverWait(du, 10).until(lambda x: uu.js("return !!document.querySelector('#tab_my_dm_changes .aprv-picker');"))
            time.sleep(0.5)
            box = uu.js("return document.querySelector('#tab_my_dm_changes .aprv-picker').innerText.replace(/\\s+/g, ' ').trim();")
            print("  결재자 지정", box)
            assert box.startswith("결재자 지정 1단계 · 모델 검토"), box
            uu.js("document.querySelector('#tab_my_dm_changes tbody tr .v-data-table__checkbox, #tab_my_dm_changes tbody tr .v-simple-checkbox').click();")
            time.sleep(0.5)
            b = [x for x in du.find_elements(By.CSS_SELECTOR, "#tab_my_dm_changes button") if "선택 신청" in uu.js("return arguments[0].innerText;", x)][0]
            uu.click(b)
            t = uu.swal_title()
            print("  안내", t)
            assert t == "결재자를 지정하세요: 데이터 모델 변경 1단계 (모델 검토)", t
            uu.swal_close()
            assert psql(f"select string_agg(aprv_status, ',') from quality.tb_data_model_change_history where dm_id={q(DM)}") == "DRAFT"
            uu.pick("aprvPick_DM_CHANGE_1", U["B"])
            uu.click(b)
            assert uu.swal_title() == "1건 신청"
            uu.js("document.querySelector('.swal2-popup .swal2-confirm').click();")
            sub = wait_until(lambda: psql(f"select submission_id from quality.tb_data_model_change_history where dm_id={q(DM)} and aprv_status='SUBMITTED'"), 15)
            st = wait_until(lambda: doc_steps(sub), 10)
            print("  결재 문서", st)
            assert st == f"1:{U['B']}", st
        step("S3. 일반 사용자 내 변경 신청 — 결재자 지정·안 고르면 안내·B 지정 → 1단계 결재자 B", _s3)
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
