"""
104번 WP1-51 · 108 §4.23-8 (§4.35) — 결재 대상 확대 (주제영역·시스템 설정)

시험 데이터(API·psql, 끝에서 모두 지움 — 결재선은 처음 값을 읽어 두었다가 되돌리고, 처음에 없던 영역·시스템 결재선은 지움):
  결재자 일반 사용자 B, 시스템 W10AAS<tag> 시스템(결재선 꺼진 채 등록), 영역·시스템 결재선 1단계 지정 사용자 B.

  S1. 관리 > 영역 관리(관리자) — 주제영역 추가 → "결재 요청" 안내(결재가 끝나면 반영됩니다)·목록에 없음·머리에 "결재 중인 영역 추가 1건"
  S2. 결재자 B 결재함 — 그 문서 상세 "주제영역 추가"·값 표 → [승인] → 주제영역이 생김(DB)
  S3. 관리 > 시스템(관리자) — 시스템 수정 → "결재 요청" 안내·그대로, 다시 고르면 "결재 중" 안내 띠
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
PFX = "W10AAS" + TAG
UB = "w10aasb" + TAG
results = []
FX = {}

VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
AREA = VM + "const c = vm('tab_area_mgmt', 'MMBizSubjArea');"
BOX = VM + "const c = vm('tab_aprv_box', 'DSAprvBox');"
SYS = VM + "const c = vm('tab_system', 'MMSystem');"


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


def save_line(reqTp, useYn):
    okc(FX["sa"].post(BASE + "/api/aprv/line/save", json={"reqTp": reqTp, "lineNm": "W10AAS " + reqTp, "useYn": useYn,
                                                           "steps": [{"stepNm": "담당 검토", "aprvrTp": "USER", "aprvrId": UB, "agreeYn": "N"}]}, timeout=20))


def setup():
    sa = FX["sa"] = api_login("space", "123")
    FX["lines"] = json.loads(okc(sa.get(BASE + "/api/aprv/lines", timeout=20))["contents"])
    FX["pw"] = "Zz" + secrets.token_hex(6)
    okc(sa.post(BASE + "/api/login/createUser", json={"id": UB, "password": b64(FX["pw"]), "name": "W10 결재대상 결재자", "email": "", "admin": False}, timeout=20))
    save_line("SYS", "N")
    okc(sa.post(BASE + "/api/sysinfo/createSysInfo", json={"sysNm": PFX + " 시스템", "sysTp": 1, "parentSysCd": None, "sysDsLst": [], "sysDesc": "셀레니움"}, timeout=20))
    save_line("AREA", "Y")
    save_line("SYS", "Y")


def cleanup():
    sa = FX.get("sa") or api_login("space", "123")
    for l in FX.get("lines", []):
        steps = [{"stepNm": s.get("stepNm") or "", "aprvrTp": s["aprvrTp"], "aprvrId": s.get("aprvrId"), "agreeYn": s.get("agreeYn") or "N"} for s in l["steps"]]
        sa.post(BASE + "/api/aprv/line/save", json={"reqTp": l["reqTp"], "lineNm": l.get("lineNm"), "useYn": l.get("useYn") or "N", "steps": steps}, timeout=20)
    for t in ("AREA", "SYS"):
        if not any(l["reqTp"] == t and l.get("lineNm") for l in FX.get("lines", [])):
            psql(f"delete from quality.tb_aprv_line_step where req_tp={q(t)}")
            psql(f"delete from quality.tb_aprv_line where req_tp={q(t)}")
    docs = f"select doc_id from quality.tb_aprv_doc where req_tp in ('AREA','SYS') and target_nm like {q(PFX + '%')}"
    for sql in (f"delete from quality.tb_notification where link_id in ({docs})",
                f"delete from quality.tb_aprv_doc_step where doc_id in ({docs})",
                f"delete from quality.tb_aprv_doc where doc_id in ({docs})",
                f"delete from quality.tb_subj_area where subj_area_nm like {q(PFX + '%')}",
                f"delete from quality.tb_sys_info where sys_nm like {q(PFX + '%')}"):
        psql(sql)
    for t in ("tb_user_role", "tb_notification", "tb_user"):
        psql(f"delete from quality.{t} where user_id={q(UB)}")
    psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id={q(UB)}")


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

    def swal(self, timeout=20):
        WebDriverWait(self.d, timeout).until(lambda x: self.js("return !!document.querySelector('.swal2-popup.swal2-show .swal2-title');"))
        return self.js("const p = document.querySelector('.swal2-popup.swal2-show'); return [p.querySelector('.swal2-title').innerText.trim(), "
                       "(p.querySelector('.swal2-html-container') || {}).innerText || ''];")

    def swal_close(self):
        self.js("const b = document.querySelector('.swal2-popup .swal2-confirm'); if (b) b.click();")
        WebDriverWait(self.d, 10).until(lambda x: not self.js("return !!document.querySelector('.swal2-popup.swal2-show');"))
        time.sleep(0.3)


def main():
    setup()
    d = new_driver("space", "123")
    u = Ui(d)
    try:
        def _s1():
            u.open_menu("mmGroup", "nav_area_mgmt", AREA + "return !!c;")
            u.js("document.getElementById('tabSubj').click();")
            time.sleep(0.8)
            u.js("document.getElementById('subjAdd').click();")
            WebDriverWait(d, 10).until(lambda x: u.js(AREA + "return c.subjDialog;"))
            time.sleep(0.5)
            d.find_element(By.ID, "subjNm").send_keys(PFX + " 영역")
            u.js("document.getElementById('subjSave').click();")
            t, body = u.swal()
            print("  안내", t, body)
            assert t == "결재 요청" and body.strip() == "결재 요청 — 1/1 단계. 결재가 끝나면 반영됩니다.", (t, body)
            u.swal_close()
            time.sleep(1.5)
            names = u.js(AREA + "return c.subjList.map(s => s.subjAreaNm);")
            assert PFX + " 영역" not in names, names
            head = WebDriverWait(d, 10).until(lambda x: u.js("const e = document.getElementById('areaPendingAdds'); return e && e.innerText.trim();"))
            print("  머리", head)
            assert "결재 중인 영역 추가 1건 — " + PFX + " 영역" in head, head
            assert psql(f"select count(*) from quality.tb_subj_area where subj_area_nm={q(PFX + ' 영역')}") == "0"
        step("S1. 영역 관리 — 주제영역 추가 → 결재 요청 안내·목록에 없음·결재 중인 추가 표시", _s1)

        def _s3():
            u.open_menu("mmGroup", "nav_system", SYS + "return !!c && c.systemInfoList !== undefined;")
            time.sleep(1)
            el = [e for e in d.find_elements(By.CSS_SELECTOR, "#systemTree .tree-anchor") if PFX in e.text]
            assert el, "트리에 시험 시스템 없음"
            u.js("arguments[0].click();", el[0])
            WebDriverWait(d, 10).until(lambda x: u.js(SYS + "return c.selectedItem[0].sysNm === arguments[0];", PFX + " 시스템"))
            time.sleep(0.8)
            assert not u.js("return !!document.getElementById('sysPendingAlert');")
            u.js("document.querySelector('button[title=\"수정\"]').click();")
            WebDriverWait(d, 10).until(lambda x: u.js(SYS + "return c.updateSystemModalShow;"))
            time.sleep(0.8)
            u.js(SYS + "c.updateSystem_sysNm = arguments[0];", PFX + " 시스템 개명")
            b = [x for x in d.find_elements(By.CSS_SELECTOR, ".v-dialog--active button") if x.text.strip() == "수정"]
            u.js("arguments[0].click();", b[-1])
            t, body = u.swal()
            print("  안내", t, body)
            assert t == "결재 요청" and "결재가 끝나면 반영됩니다." in body, (t, body)
            u.swal_close()
            assert psql(f"select count(*) from quality.tb_sys_info where sys_nm={q(PFX + ' 시스템')}") == "1"
            time.sleep(1)
            el = [e for e in d.find_elements(By.CSS_SELECTOR, "#systemTree .tree-anchor") if PFX in e.text]
            u.js("arguments[0].click();", el[0])
            al = WebDriverWait(d, 10).until(lambda x: u.js("const e = document.getElementById('sysPendingAlert'); return e && e.innerText.trim();"))
            print("  안내 띠", al)
            assert al.startswith("이 시스템의 변경이 결재 중입니다 (1/1 단계, 현재 결재자 W10 결재대상 결재자)."), al
        step("S3. 시스템 — 수정 → 결재 요청 안내·그대로·결재 중 안내 띠", _s3)
    finally:
        d.quit()

    db = new_driver(UB, FX["pw"])
    ub = Ui(db)
    try:
        def _s2():
            ub.open_menu("myPageGroup", "nav_aprvBox", BOX + "return !!c && c.docs.length > 0;")
            row = [r for r in db.find_elements(By.CSS_SELECTOR, "#tab_aprv_box tbody tr") if (PFX + " 영역") in r.text]
            assert row, "결재함에 그 문서 없음"
            ub.js("arguments[0].click();", row[0])
            head = WebDriverWait(db, 10).until(lambda x: ub.js("const e = document.getElementById('aprvPayloadHead'); return e && e.innerText.trim();"))
            tbl = ub.js("return document.getElementById('aprvPayloadTable').innerText.replace(/\\s+/g, ' ').trim();")
            print("  상세", head, "|", tbl)
            assert head == "주제영역 추가 — 최종 승인 때 반영됩니다" and tbl.startswith("항목 값 주제영역명 " + PFX + " 영역"), (head, tbl)
            ok = ub.js("const b = [...document.querySelectorAll('button')].filter(x => x.innerText.trim() === '승인'); "
                       "if (!b.length) return false; b[b.length - 1].click(); return true;")
            assert ok, "[승인] 버튼 없음"
            wait_until(lambda: psql(f"select count(*) from quality.tb_subj_area where subj_area_nm={q(PFX + ' 영역')} and use_yn='Y'") == "1", 15)
        step("S2. 결재자 결재함 — 영역 추가 상세·승인 → 주제영역 생김", _s2)
    finally:
        db.quit()


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
