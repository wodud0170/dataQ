"""
92번 · 108 §4.23-10 (§4.37) — 분석 > 종합 분석 보고서

시험 데이터(psql, 끝에서 지움): 모델 W10AR_<tag>(테이블 둘 — T_PK(PK 있음)·T_NOPK(PK 없음), 컬럼 셋).

  S1. 메뉴 분석 > 종합 분석 보고서 — 모델 고르기 전 안내, 시트 23개 체크(전체 종합), 프리셋 바꾸면 체크 바뀜(DBA 13개·표준화 7개·거버넌스 5개),
      프리셋이면 체크 칸 잠김·커스텀이면 풀림, 버튼 글자 "보고서 내려받기 (엑셀, 시트 n개)"
  S2. 모델 고르기 → 미리보기 A1(테이블 수 2·컬럼 수 3), D1 미리보기 T_NOPK 한 행(연한 빨강)
  S3. 내려받기 — 화면과 같은 요청을 브라우저에서 보내 xlsx(PK 시그니처)·시트 수 = 체크 수
"""
import base64, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
DM = "W10ARM" + TAG
DMNM = "W10AR_" + TAG
results = []

VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
REP = VM + "const c = vm('tab_analysisReport', 'AnalysisReport');"


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
    r = subprocess.run(PSQL + ["-v", "ON_ERROR_STOP=1", "-At", "-c", sql], capture_output=True, text=True, timeout=60, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError("psql: " + (r.stderr or "")[:400])
    return (r.stdout or "").strip()


def q(v):
    return "NULL" if v is None else "'" + str(v).replace("'", "''") + "'"


def setup():
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    psql(f"insert into quality.tb_data_model (dm_id, dm_nm, ver, use_yn, stnd_req_yn, cret_dt) values ({q(DM)}, {q(DMNM)}, '1', 'Y', 'N', {now})")
    for t in ("T_PK", "T_NOPK"):
        psql(f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) values ({q(DM)}, 'APP', {q(t)}, {q(t)}, 1, 'Y')")
    for t, n, pk in (("T_PK", "ID_NO", "Y"), ("T_PK", "NM", "N"), ("T_NOPK", "VAL", "N")):
        psql("insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_ord, attr_nm, attr_nm_kr, data_type, data_len, pk_yn, fk_yn, nullable_yn, use_yn) "
             f"values ({q(DM)}, 'APP', {q(t)}, 1, {q(n)}, {q(n)}, 'VARCHAR', 10, {q(pk)}, 'N', 'N', 'Y')")


def cleanup():
    for t in ("tb_data_model_attr", "tb_data_model_obj"):
        psql(f"delete from quality.{t} where dm_id={q(DM)}")
    psql(f"delete from quality.tb_data_model where dm_id={q(DM)}")


def main():
    setup()
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    d = webdriver.Edge(options=headless(opts))
    d.set_window_size(1366, 768)
    js = lambda s, *a: d.execute_script(s, *a)
    try:
        d.get(BASE)
        time.sleep(2)
        WebDriverWait(d, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys("space")
        d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        d.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(d, 15).until(lambda x: "/signin" not in x.current_url)
        time.sleep(3)
        js("try { localStorage.removeItem('navidmeta.analysisReport'); } catch (e) {}")

        def _s1():
            g = d.find_elements(By.CSS_SELECTOR, "#analysisGroup .v-list-group__header")
            js("arguments[0].click();", g[0])
            time.sleep(1)
            js("document.getElementById('nav_analysisReport').click();")
            WebDriverWait(d, 15).until(lambda x: js(REP + "return !!c && c.sheets.length === 23;"))
            time.sleep(1)
            empty = js("return document.querySelector('#tab_analysisReport .rep-empty').innerText.trim();")
            assert empty == "데이터 모델을 고르면 시트 앞 10행을 미리 볼 수 있습니다.", empty
            assert js(REP + "return c.checked.length;") == 23
            btn = js("return document.getElementById('repDownload');")
            assert btn.text.strip() == "보고서 내려받기 (엑셀, 시트 23개)" and not btn.is_enabled(), btn.text
            assert js("return document.getElementById('repSheet_B1').disabled;") is True
            for label, n in (("DBA 점검", 13), ("표준화 현황", 7), ("거버넌스", 5)):
                js("[...document.querySelectorAll('#repPreset .v-radio')].find(r => r.innerText.trim() === arguments[0]).querySelector('input').click();", label)
                time.sleep(0.5)
                got = js(REP + "return c.checked.length;")
                print("  ", label, got)
                assert got == n, (label, got)
            js("[...document.querySelectorAll('#repPreset .v-radio')].find(r => r.innerText.trim() === '커스텀').querySelector('input').click();")
            time.sleep(0.5)
            assert js("return document.getElementById('repSheet_B1').disabled;") is False
            js("[...document.querySelectorAll('#repPreset .v-radio')].find(r => r.innerText.trim() === '전체 종합').querySelector('input').click();")
            time.sleep(0.5)
        step("S1. 메뉴·프리셋·체크·버튼", _s1)

        def _s2():
            js(REP + "c.dmId = arguments[0]; c.onModel();", DM)
            WebDriverWait(d, 15).until(lambda x: js(REP + "return !!c.preview && c.preview.id === 'A1';"))
            time.sleep(0.5)
            tbl = js("return document.getElementById('repPreviewTable').innerText;")
            assert "테이블 수\t2" in tbl and "컬럼 수\t3" in tbl, tbl
            js(REP + "c.previewId = 'D1'; c.loadPreview();")
            WebDriverWait(d, 15).until(lambda x: js(REP + "return !!c.preview && c.preview.id === 'D1';"))
            time.sleep(0.5)
            rows = js("return [...document.querySelectorAll('#repPreviewTable tbody tr')].map(r => [r.innerText.trim(), r.classList.contains('rep-flag')]);")
            print("  D1", rows)
            assert len(rows) == 1 and "T_NOPK" in rows[0][0] and rows[0][1], rows
        step("S2. 모델 고르기 → 미리보기 A1·D1(연한 빨강)", _s2)

        def _s3():
            res = d.execute_async_script(
                "const done = arguments[arguments.length - 1]; " + REP +
                "fetch('/api/report/download', {method: 'POST', headers: {'Content-Type': 'application/json'}, credentials: 'same-origin',"
                " body: JSON.stringify(Object.assign(c.body(), {sheets: c.checked}))})"
                ".then(r => r.arrayBuffer().then(b => done([r.status, r.headers.get('content-type'), new Uint8Array(b).slice(0, 2).join(','), b.byteLength])))"
                ".catch(e => done(['err', String(e)]));")
            print("  응답", res)
            assert res[0] == 200 and "spreadsheetml" in res[1] and res[2] == "80,75" and res[3] > 5000, res
        step("S3. 내려받기 — xlsx", _s3)
    finally:
        d.quit()


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
