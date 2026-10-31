"""
104번 WP8-40 · 108 §4.23-3 — ERwin XML 내보내기 (모델링 도구 임포트 화면 [ERwin XML 추출])

시험 모델 W10_ERW_<tag>_SRC·_DST 는 psql 로 만들고, 일반 사용자는 API 로 만든다. 끝에서 모두 지운다.
SRC: OWN_A.TB_PARENT·OWN_A.TB_CHILD(NUMERIC(10,2)·기본값·FK 두 개 — OWN_A·OWN_B 부모, 한글명 없는 컬럼)·OWN_B.TB_PARENT(같은 이름)·
     빈 소유자 TB_EMPTY(컬럼 없음), 다른 사용자가 요청한 DRAFT 테이블, 시험 일반 사용자가 요청한 DRAFT 컬럼 1개

  E1. API 되읽기 — 내보낸 파일을 parseErwinXml → 빈 모델(DST)에 importErwinModel → 테이블·컬럼·PK·FK·부모·기본값·코멘트가 SRC(보이는 행)와 같음,
      관리자 파일에는 남의 DRAFT 가 없고, 일반 사용자 파일에는 자기 DRAFT 컬럼만 더 있음.
      SRC 에 다시 가져오면 거부, 일반 사용자가 지워 승인 대기(USE_YN='N')인 DST 테이블에 다시 가져와도 거부(테이블 그대로)
  U1. [ERwin XML 추출] — 라벨·모델 선택 전 비활성·선택 뒤 활성, 누르면 스낵바와 내려받은 파일(<모델명>.xml), [XMI 2.1 추출]도 스낵바·파일,
      서버가 거부하면(400) 두 버튼 모두 오류 스낵바 "… 추출 중 오류: 서버 응답 400"·파일 없음
  U2. 내려받은 파일을 'ERwin native XML' 로 미리보기 — 테이블 4·컬럼 9, '소유자' 열·OWN_A/OWN_B 의 TB_PARENT 두 줄,
      각각 누르면 그 소유자 컬럼만·선택 행 배경, TB_CHILD 의 'FK' 열 아이콘(부모 소유자.테이블.컬럼 제목)
  U3. 옛 견본(test_erwin_sample.xml) 미리보기 — '소유자'·'FK' 열 없음 (기존 모습)
  U4. 시험 일반 사용자 — 메뉴와 버튼이 보이고, 내려받은 파일에 자기 DRAFT 컬럼이 있고 남의 DRAFT 는 없음
"""
import base64, json, os, secrets, shutil, subprocess, sys, tempfile, time, traceback
import xml.etree.ElementTree as ET
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
HERE = os.path.dirname(os.path.abspath(__file__))
SAMPLE = os.path.join(os.path.dirname(HERE), "test_erwin_sample.xml")
TAG = time.strftime("%H%M%S")
DM_S, DM_D = f"W10_ERW_{TAG}_S", f"W10_ERW_{TAG}_D"
NM_S, NM_D = f"W10_ERW_{TAG}_SRC", f"W10_ERW_{TAG}_DST"
USER = "w10erw" + TAG
OTHER = "w10erw_other"
results = []
FX = {}

# 탭 안 Vue 컴포넌트 — 자식부터 찾고 없으면 위로 (첫 요소가 keep-alive 로 잡히는 화면이 있다)
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
C = VM + "const c = vm('tab_erwinImport', 'DSErwinImport');"

OBJS = [  # 소유자, 테이블, 한글명, 코멘트, 설명, 승인상태, 요청자
    ("OWN_A", "TB_PARENT", "부모 테이블", "부모 코멘트", "부모 설명", None, None),
    ("OWN_A", "TB_CHILD", "자식 테이블", "자식 코멘트", "자식 설명", None, None),
    ("OWN_B", "TB_PARENT", "부모B", None, "B 설명", None, None),
    ("", "TB_EMPTY", "빈 테이블", None, None, None, None),
    ("OWN_A", "TB_DRAFT_OTHER", "남의 요청 테이블", None, None, "DRAFT", OTHER),
]
# 소유자, 테이블, 컬럼, 한글명, 타입, 길이, 소수, NULL, PK, FK, 부모소유자, 부모테이블, 부모컬럼, 기본값, 코멘트, 순서, 승인상태, 요청자
ATTRS = [
    ("OWN_A", "TB_PARENT", "P_ID", "부모ID", "NUMERIC", 10, 0, "N", "Y", "N", None, None, None, None, "부모 키", 1, None, None),
    ("OWN_A", "TB_PARENT", "P_NM", "부모명", "VARCHAR", 100, 0, "Y", "N", "N", None, None, None, None, None, 2, None, None),
    ("OWN_A", "TB_CHILD", "C_ID", "자식ID", "INTEGER", 0, 0, "N", "Y", "N", None, None, None, None, None, 1, None, None),
    ("OWN_A", "TB_CHILD", "AMT", "금액", "NUMERIC", 10, 2, "Y", "N", "N", None, None, None, None, "금액 코멘트", 2, None, None),
    ("OWN_A", "TB_CHILD", "USE_YN", "사용여부", "CHAR", 1, 0, "N", "N", "N", None, None, None, "'N'", None, 3, None, None),
    ("OWN_A", "TB_CHILD", "P_ID", "부모ID", "NUMERIC", 10, 0, "N", "N", "Y", "OWN_A", "TB_PARENT", "P_ID", None, None, 5, None, None),
    ("OWN_A", "TB_CHILD", "B_ID", None, "VARCHAR", 20, 0, "Y", "N", "Y", "OWN_B", "TB_PARENT", "B_ID", None, "B 참조", 6, None, None),
    ("OWN_A", "TB_CHILD", "U_DRAFT_COL", "일반 사용자 요청", "VARCHAR", 10, 0, "Y", "N", "N", None, None, None, None, None, 7, "DRAFT", USER),
    ("OWN_B", "TB_PARENT", "B_ID", "B키", "VARCHAR", 20, 0, "N", "Y", "N", None, None, None, None, None, 1, None, None),
    ("OWN_B", "TB_PARENT", "B_NM", "B명", "VARCHAR", 50, 0, "Y", "N", "N", None, None, None, None, "B 이름", 2, None, None),
    ("OWN_A", "TB_DRAFT_OTHER", "X_ID", "남의 컬럼", "INTEGER", 0, 0, "N", "Y", "N", None, None, None, None, None, 1, "DRAFT", OTHER),
]


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


def setup():
    FX["sa"] = api_login("space", "123")
    FX["upw"] = "Zz" + secrets.token_hex(6)
    r = FX["sa"].post(BASE + "/api/login/createUser", json={"id": USER, "password": b64(FX["upw"]), "name": "W10 ERwin 시험 " + USER,
                                                            "email": "", "admin": False}, timeout=10).json()
    assert r.get("resultCode") == 200, r
    for dm, nm in ((DM_S, NM_S), (DM_D, NM_D)):
        psql("insert into quality.tb_data_model (dm_id, dm_nm, model_type, use_yn, cret_user_id, cret_dt, ver, dict_id, aprv_status) "
             f"values ({q(dm)}, {q(nm)}, 'PHYSICAL', 'Y', 'space', to_char(now(),'YYYYMMDDHH24MISS'), '1', 'DEFAULT', 'APPROVED')")
    for o in OBJS:
        psql("insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_comment, obj_desc, use_yn, aprv_status, requester_user_id) "
             f"values ({q(DM_S)}, {q(o[0])}, {q(o[1])}, {q(o[2])}, {q(o[3])}, {q(o[4])}, 'Y', {q(o[5] or 'APPROVED')}, {q(o[6])})")
    vals = ["(" + ", ".join([q(DM_S)] + [q(v) for v in a[:16]] + [q(a[16] or "APPROVED"), q(a[17]), "'Y'"]) + ")" for a in ATTRS]
    psql("insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, data_decimal_len, "
         "nullable_yn, pk_yn, fk_yn, fk_parent_obj_owner, fk_parent_obj_nm, fk_parent_attr_nm, default_val, attr_comment, attr_ord, "
         "aprv_status, requester_user_id, use_yn) values " + ", ".join(vals))


def cleanup():
    like = f"W10_ERW_{TAG}_%"
    for t in ("tb_data_model_attr", "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_index", "tb_data_model_constraint",
              "tb_data_model_schema", "tb_data_model_change_history"):
        psql(f"delete from quality.{t} where dm_id like {q(like)}")
    psql(f"delete from quality.tb_data_model where dm_id like {q(like)}")
    psql(f"delete from quality.tb_user_role where user_id={q(USER)}")
    psql(f"delete from quality.tb_user where user_id={q(USER)}")
    psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id={q(USER)}")
    for d in FX.get("dirs", []):
        shutil.rmtree(d, ignore_errors=True)


def table_rows(dm, cols, visible_to=None, order="obj_owner, obj_nm", table="tb_data_model_obj"):
    cond = f"dm_id={q(dm)} and use_yn='Y'"
    if visible_to:
        cond += f" and (aprv_status is null or aprv_status='APPROVED' or requester_user_id={q(visible_to)})"
    return json.loads(psql(f"select coalesce(json_agg(t order by {order}), '[]') from (select {cols} from quality.{table} where {cond}) t"))


ATTR_COLS = ("obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, data_decimal_len, nullable_yn, pk_yn, fk_yn, "
             "fk_parent_obj_owner, fk_parent_obj_nm, fk_parent_attr_nm, default_val, attr_comment, "
             "row_number() over (partition by obj_owner, obj_nm order by attr_ord) as rel_ord")


def new_driver(user, pw):
    dl = tempfile.mkdtemp(prefix="w10erw_")
    FX.setdefault("dirs", []).append(dl)
    opts = webdriver.EdgeOptions()
    opts.add_argument("--log-level=3")
    opts.add_experimental_option("prefs", {"download.default_directory": dl, "download.prompt_for_download": False})
    d = webdriver.Edge(options=headless(opts))
    # 새 프로필의 Edge 는 .xml 내려받기를 '확인되지 않음 …crdownload' 로 붙잡아 둔다(.xmi 는 그냥 받음) — DevTools 로 내려받기를 허용
    d.execute_cdp_cmd("Browser.setDownloadBehavior", {"behavior": "allow", "downloadPath": dl})
    d.set_window_size(1600, 900)
    d.get(BASE)
    time.sleep(2)
    WebDriverWait(d, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
    d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(pw)
    d.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
    WebDriverWait(d, 15).until(lambda x: "/signin" not in x.current_url)
    time.sleep(3)
    return d, dl


def open_tab(d):
    g = d.find_elements(By.CSS_SELECTOR, "#dmGroup .v-list-group__header")
    d.execute_script("arguments[0].click();", g[0])
    time.sleep(1)
    nav = d.find_element(By.ID, "nav_erwinImport")
    assert nav.is_displayed(), "모델링 도구 임포트 메뉴가 안 보임"
    d.execute_script("arguments[0].click();", nav)
    WebDriverWait(d, 10).until(lambda x: x.execute_script(C + "return !!c && c.dataModelList.length > 0;"))


def wait_file(dl, name, timeout=20):
    p = os.path.join(dl, name)
    end = time.time() + timeout
    while time.time() < end:
        if os.path.exists(p) and not any(f.endswith(".crdownload") for f in os.listdir(dl)):
            with open(p, encoding="utf-8") as f:
                return f.read()
        time.sleep(0.5)
    raise AssertionError(f"내려받은 파일이 없음: {name} (폴더: {os.listdir(dl)})")


def main():
    setup()
    sa = FX["sa"]

    def _e1():
        x = sa.get(BASE + "/api/dm/exportErwinXml", params={"dataModelId": DM_S}, timeout=20)
        assert x.status_code == 200 and x.headers.get("Content-Type", "").lower().startswith("application/xml"), x.status_code
        assert x.headers.get("Content-Disposition") == f'attachment; filename="{NM_S}.xml"', x.headers.get("Content-Disposition")
        xml = x.content.decode("utf-8")
        assert "TB_DRAFT_OTHER" not in xml and "U_DRAFT_COL" not in xml, "관리자 파일에 남의 DRAFT"
        root = ET.fromstring(x.content)
        emp = [e for e in root.iter("entity") if e.findtext("physical_name") == "TB_EMPTY"][0]
        assert emp.find("owner") is not None and (emp.find("owner").text or "") == "" and len(list(emp.find("attributes"))) == 0
        p = sa.post(BASE + "/api/dm/parseErwinXml", files={"file": ("m.xml", x.content, "application/xml")}, timeout=20).json()
        assert p.get("success") is True and p["tableCount"] == 4 and p["columnCount"] == 9, (p.get("tableCount"), p.get("columnCount"))
        r = sa.post(BASE + "/api/dm/importErwinModel", json={"dataModelId": DM_D, "tables": p["tables"], "columns": p["columns"]}, timeout=30).json()
        assert r.get("success") is True and r["message"] == "ERwin 모델 임포트 완료: 테이블 4건, 컬럼 9건", r
        tc = "obj_owner, obj_nm, obj_nm_kr, obj_comment, obj_desc"
        assert table_rows(DM_S, tc, "space") == table_rows(DM_D, tc)
        order = "obj_owner, obj_nm, rel_ord"
        src = table_rows(DM_S, ATTR_COLS, "space", order, "tb_data_model_attr")
        dst = table_rows(DM_D, ATTR_COLS, None, order, "tb_data_model_attr")
        for a, b in zip(src, dst):
            if a != b:
                print("  다름:", a, "\n       ", b)
        assert len(src) == len(dst) == 9 and src == dst
        # 같은 파일을 SRC 자체에 가져오면 덮어쓰기 거부
        r = sa.post(BASE + "/api/dm/importErwinModel", json={"dataModelId": DM_S, "tables": p["tables"], "columns": p["columns"]}, timeout=30).json()
        print("  SRC 에 다시:", r.get("message"))
        assert r.get("success") is False and r["message"].startswith("대상 모델에 이미 있는 테이블은 가져오기로 덮어쓸 수 없습니다 — ") and "OWN_A.TB_CHILD" in r["message"]
        su = api_login(USER, FX["upw"])
        xu = su.get(BASE + "/api/dm/exportErwinXml", params={"dataModelId": DM_S}, timeout=20).content.decode("utf-8")
        assert "U_DRAFT_COL" in xu and "TB_DRAFT_OTHER" not in xu, "일반 사용자 파일"
        # 일반 사용자가 DST 의 OWN_A.TB_CHILD 를 지우면 승인 대기(USE_YN='N') — 그 테이블만 담은 파일을 다시 가져오면 거부, 테이블은 숨은 채 그대로
        r = su.post(BASE + "/api/dm/deleteObj", json={"dataModelId": DM_D, "objOwner": "OWN_A", "objNm": "TB_CHILD"}, timeout=20).json()
        assert r.get("resultCode") == 200, r
        hidden = "select o.use_yn||'|'||count(a.*)||'|'||string_agg(a.use_yn||a.aprv_status||a.data_type, ',' order by a.attr_nm) " \
                 f"from quality.tb_data_model_obj o left join quality.tb_data_model_attr a on a.dm_id=o.dm_id and a.obj_owner=o.obj_owner and a.obj_nm=o.obj_nm " \
                 f"where o.dm_id={q(DM_D)} and o.obj_owner='OWN_A' and o.obj_nm='TB_CHILD' group by o.use_yn"
        before = psql(hidden)
        assert before.startswith("N|5|"), before
        child = {"tables": [t for t in p["tables"] if (t.get("objOwner"), t["objNm"]) == ("OWN_A", "TB_CHILD")],
                 "columns": [c for c in p["columns"] if (c.get("objOwner"), c["objNm"]) == ("OWN_A", "TB_CHILD")]}
        for c in child["columns"]:
            c["dataType"] = "CHAR"
        for s in (su, sa):
            r = s.post(BASE + "/api/dm/importErwinModel", json={"dataModelId": DM_D, **child}, timeout=30).json()
            print("  승인 대기 삭제 테이블에 다시:", r.get("message"))
            assert r.get("success") is False and r["message"] == ("대상 모델에 이미 있는 테이블은 가져오기로 덮어쓸 수 없습니다 — OWN_A.TB_CHILD"
                                                                 " (삭제 승인 대기 중이거나 삭제된 테이블 포함)"), r
        assert psql(hidden) == before, "거부했는데 숨은 테이블이 바뀜"
    step("E1. API 되읽기 — 내보내기 → 파싱 → 빈 모델 가져오기가 SRC 와 같음·남의 DRAFT 없음·SRC 덮어쓰기 거부·일반 사용자 자기 DRAFT·승인 대기 삭제 테이블도 거부", _e1)

    driver, dl = new_driver("space", "123")

    def js(script, *args):
        return driver.execute_script(script, *args)

    def tab():
        return driver.find_element(By.ID, "tab_erwinImport")

    def btn(text):
        b = [x for x in tab().find_elements(By.CSS_SELECTOR, "button") if x.text.strip() == text]
        assert b, f"버튼 없음: {text}"
        return b[0]

    def tables():
        return tab().find_elements(By.CSS_SELECTOR, ".v-data-table")

    def heads(t):
        return [h.text.strip() for h in t.find_elements(By.CSS_SELECTOR, "thead th")]

    def body_rows(t):
        return [[td.text.strip() for td in tr.find_elements(By.CSS_SELECTOR, "td")] for tr in t.find_elements(By.CSS_SELECTOR, "tbody tr")]

    def preview(path):
        js(C + "c.format = 'erwin'; c.onFormatChange();")
        time.sleep(0.5)
        tab().find_element(By.CSS_SELECTOR, "input[type='file']").send_keys(path)
        time.sleep(0.5)
        js("arguments[0].click();", btn("미리보기"))
        WebDriverWait(driver, 15).until(lambda x: js(C + "return !!c.previewResult;"))
        time.sleep(0.8)

    try:
        def _u1():
            open_tab(driver)
            b = tab().find_element(By.ID, "btn-export-erwin")
            assert b.text.strip() == "ERwin XML 추출", repr(b.text)
            assert b.get_attribute("title") == "현재 선택된 데이터 모델을 ERwin XML 로 내려받기"
            xmi = tab().find_element(By.ID, "btn-export-xmi")
            assert xmi.text.strip() == "XMI 2.1 추출" and b.get_attribute("disabled") and xmi.get_attribute("disabled"), "모델 선택 전 활성"
            # 글자가 버튼 안에 다 들어감 (전역 .v-btn 100px 고정에 잘리지 않음)
            for e in (b, xmi):
                w = js("const c = arguments[0].querySelector('.v-btn__content'); return [c.scrollWidth, arguments[0].clientWidth];", e)
                assert w[0] <= w[1], f"{e.text} 글자 잘림 {w}"
            js(C + "c.selectedModelId = arguments[0];", DM_S)
            time.sleep(0.5)
            assert not b.get_attribute("disabled") and not xmi.get_attribute("disabled"), "모델 선택 뒤 비활성"
            js("arguments[0].click();", b)
            WebDriverWait(driver, 5).until(lambda x: "ERwin XML 추출 요청 완료 (브라우저 다운로드 확인)" in x.find_element(By.TAG_NAME, "body").text)
            FX["file_admin"] = wait_file(dl, NM_S + ".xml")
            xml = FX["file_admin"]
            assert xml.count("<entity ") == 4 and "TB_DRAFT_OTHER" not in xml and "U_DRAFT_COL" not in xml, "내려받은 파일 내용"
            FX["path_admin"] = os.path.join(dl, NM_S + ".xml")
            # [XMI 2.1 추출]도 같은 방식(axios 로 받아 저장) — 스낵바·파일
            js(C + "c.snackbar = false;")
            js("arguments[0].click();", xmi)
            WebDriverWait(driver, 5).until(lambda x: "XMI 2.1 추출 요청 완료 (브라우저 다운로드 확인)" in x.find_element(By.TAG_NAME, "body").text)
            xmi_text = wait_file(dl, NM_S + ".xmi")
            assert "TB_CHILD" in xmi_text and "TB_DRAFT_OTHER" not in xmi_text, "내려받은 XMI 내용"
            # 서버가 거부하면(여기서는 dataModelId 를 떼어 400) 성공 스낵바 대신 오류 스낵바, 파일 없음 — 리뷰(링크 다운로드는 늘 성공 표시)
            files0 = sorted(os.listdir(dl))
            for b_, label in ((b, "ERwin XML"), (xmi, "XMI")):
                js(C + "c.snackbar = false;")
                js("const o = XMLHttpRequest.prototype.open; window.__w10Open = o;"
                   "XMLHttpRequest.prototype.open = function (m, u, ...r) { if (/export(ErwinXml|Xmi)/.test(u)) u = String(u).split('?')[0]; return o.call(this, m, u, ...r); };")
                try:
                    js("arguments[0].click();", b_)
                    WebDriverWait(driver, 5).until(lambda x: js(C + "return c.snackbar && c.snackbarColor === 'error';"))
                finally:
                    js("XMLHttpRequest.prototype.open = window.__w10Open;")
                txt = js(C + "return c.snackbarText;")
                print("  오류 스낵바:", txt)
                assert txt == f"{label} 추출 중 오류: 서버 응답 400", txt
                body = driver.find_element(By.TAG_NAME, "body").text
                assert txt in body and "추출 요청 완료" not in body, body[-300:]
            time.sleep(1)
            assert sorted(os.listdir(dl)) == files0, os.listdir(dl)
            js(C + "c.snackbar = false;")
        step("U1. [ERwin XML 추출] — 라벨·제목·글자 안 잘림·선택 전 비활성/뒤 활성·스낵바·내려받은 파일, [XMI 2.1 추출] 그대로, 서버 거부면 오류 스낵바", _u1)

        def _u2():
            preview(FX["path_admin"])
            vals = [e.text.strip() for e in tab().find_elements(By.CSS_SELECTOR, ".stat-value")]
            assert vals[:2] == ["4", "9"], vals
            t_tbl, t_col = tables()[0], tables()[1]
            h = heads(t_tbl)
            assert h == ["소유자", "물리명", "논리명", "컬럼수"], h
            rows = body_rows(t_tbl)
            print("  테이블 목록:", rows)
            assert ["OWN_A", "TB_PARENT", "부모 테이블", "2"] in rows and ["OWN_B", "TB_PARENT", "부모B", "2"] in rows and ["", "TB_EMPTY", "빈 테이블", "0"] in rows
            hc = heads(t_col)
            assert hc == ["PK", "FK", "물리명", "논리명", "데이터타입", "길이", "Nullable", "순서"], hc

            def pick(owner, nm):
                tr = [r for r in t_tbl.find_elements(By.CSS_SELECTOR, "tbody tr")
                      if [td.text.strip() for td in r.find_elements(By.CSS_SELECTOR, "td")][:2] == [owner, nm]][0]
                js("arguments[0].click();", tr)
                time.sleep(0.6)
                return tr

            tr = pick("OWN_A", "TB_PARENT")
            cols = [r[2] for r in body_rows(tables()[1])]
            assert cols == ["P_ID", "P_NM"], cols
            bg = js("return getComputedStyle(arguments[0]).backgroundColor;", tr)
            assert bg == "rgb(227, 242, 253)", f"선택 행 배경 {bg}"
            assert "- OWN_A.TB_PARENT" in tab().text
            pick("OWN_B", "TB_PARENT")
            cols = [r[2] for r in body_rows(tables()[1])]
            assert cols == ["B_ID", "B_NM"], cols
            assert "- OWN_B.TB_PARENT" in tab().text
            pick("OWN_A", "TB_CHILD")
            t_col = tables()[1]
            icons = t_col.find_elements(By.CSS_SELECTOR, "tbody .mdi-key-link")
            titles = sorted(i.get_attribute("title") for i in icons)
            print("  FK 아이콘 제목:", titles)
            assert titles == ["OWN_A.TB_PARENT.P_ID", "OWN_B.TB_PARENT.B_ID"], titles
            # 한글명 없는 컬럼은 논리명 칸이 비어 있다
            assert ["", "", "B_ID", "", "VARCHAR", "20", "Y", "5"] in body_rows(t_col), body_rows(t_col)
        step("U2. 미리보기 — 테이블 4·컬럼 9, 소유자 열·두 소유자 TB_PARENT 각각 그 컬럼만·선택 배경·FK 열 아이콘 제목", _u2)

        def _u3():
            preview(SAMPLE)
            vals = [e.text.strip() for e in tab().find_elements(By.CSS_SELECTOR, ".stat-value")]
            assert vals[:2] == ["5", "34"], vals
            h, hc = heads(tables()[0]), heads(tables()[1])
            assert h == ["물리명", "논리명", "컬럼수"], h
            assert hc == ["PK", "물리명", "논리명", "데이터타입", "길이", "Nullable", "순서"], hc
        step("U3. 옛 견본 미리보기 — 소유자·FK 열 없음 (기존 모습)", _u3)
    finally:
        driver.quit()

    def _u4():
        d, dl2 = new_driver(USER, FX["upw"])
        try:
            g = d.find_elements(By.CSS_SELECTOR, "#dmGroup .v-list-group__header")
            assert g, "일반 사용자에게 데이터 모델 메뉴가 없음"
            open_tab(d)
            b = d.find_element(By.ID, "btn-export-erwin")
            assert b.is_displayed() and b.get_attribute("disabled")
            d.execute_script(C + "c.selectedModelId = arguments[0];", DM_S)
            time.sleep(0.5)
            d.execute_script("arguments[0].click();", b)
            xml = wait_file(dl2, NM_S + ".xml")
            assert "U_DRAFT_COL" in xml and "TB_DRAFT_OTHER" not in xml and xml.count("<entity ") == 4
        finally:
            d.quit()
    step("U4. 시험 일반 사용자 — 메뉴·버튼 보임, 내려받은 파일에 자기 DRAFT 있음·남의 DRAFT 없음", _u4)


if __name__ == "__main__":
    t0 = time.time()
    try:
        main()
    finally:
        cleanup()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({time.time() - t0:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
