# -*- coding: utf-8 -*-
"""
110번 기존 기능 보완 4묶음(로그인·보안 1.10 / 게시판 10.3 / 사용자·데이터 소스·시스템 11.7) 조치 검증 — API + 화면 (2026-10-31, PC1)

  A. 로그인·세션 — 1.10-S5(없는 아이디·틀린 비밀번호 같은 문구), 1.10-U6-a·b(실패 로그 한 건·성공 로그 문구), 1.10-B1(내장 관리자 없음,
     삭제 표시된 space 를 createAdmin 이 되살림), 1.10-S8(CORS 헤더 없음), 11.7-14(삭제된 사용자 세션 만료·관리자 지정 즉시 반영), 1.10-S7·U5·U3-a(소스 확인)
  P. 사용자·비밀번호 — 11.7-3b·3c(비밀번호 변경 순서·실패 횟수·POST), 11.7-4(내장 관리자 삭제 거부), 11.7-11a(삭제된 ID 되살리기·부서장 지정 비움),
     11.7-11b(거부 사유 본문), 11.7-12a·12b(전화번호·수정 일시), 11.7-23b(소스 확인)
  B. 게시판 — 10.3-1·2a·2b·2c·3·4·6·7·9·11, 1.10-U4(NULL 컬럼도 키로)
  D. 데이터 소스·시스템·진단 — 11.7-19(사용 중 데이터 소스 삭제 거부), 11.7-24a·24b·25(지운 API·쿼리), 11.7-26(DataSourceUtils 가 VO 를 안 바꿈),
     1.10-U7(진단 실행·중지·구조 진단·즉시 실행이 소켓 세션 ID 없이 동작)
  M. 빌드 결과·소스 — 1.10-S11(소스맵 없음), 1.10-B2·B3·B4(소스·번들 확인), 1.10-S6-b(q-center 기동 문자셋 UTF-8 고정)
  U. 화면 — 셀레니움 Edge(창 없이): 1.10-S6-b(한글 비밀번호 로그인·변경), 11.7-14(내 정보 저장이 관리자 지정을 풀지 않음), 11.7-9(사용자 목록 페이지), 11.7-11b·12a(삭제 실패 사유·전화번호),
     11.7-14(삭제된 사용자 화면은 로그인 안내), 11.7-15·16·17·18(데이터 소스), 11.7-20(시스템 버튼), 1.10-B12·B13(메뉴 그룹·탭 다시 열기),
     10.3-4·6(첨부 안내·X 는 저장 때 반영), 10.3-5·8(화면 판단을 속여도 서버가 거부하고 사유를 보임), 콘솔 오류

필요:
  - q-center(28091)·q-executor(28098) 기동
  - 환경 변수 DATAQ_TEST_PW (관리자 space 비밀번호). 시험 사용자는 시험 안에서 만들고 지운다
  - 개발 DB 는 dbcmd.PSQL (PC1 dataq-db 컨테이너, PC2 는 DATAQ_PSQL=native + PGPASSWORD)
  - 인자: 돌릴 묶음(예: A B — 없으면 전부), --keep (정리를 건너뛰고 남긴다 — 화면 검수용, 뒤에 --purge 로 지운다)
  - A 묶음은 내장 관리자(space)를 잠깐 삭제 표시(DEL_YN)했다가 되돌린다 — 다른 시험과 동시에 돌리지 말 것
시험 데이터(사용자·역할, 게시글·댓글·첨부와 디스크 파일, 데이터 소스, 시스템, 모델과 수집·진단·구조 진단·스케줄, 시험 스키마·역할, 로그인 로그)는
끝에서 지운다. 이름은 모두 ZZB4·zzb4 로 시작한다.
"""
import base64, json, os, re, secrets, subprocess, sys, tempfile, time, traceback
import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dbcmd import PSQL  # noqa: E402

BASE = "http://localhost:28091"
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
PW = os.environ["DATAQ_TEST_PW"]
TAG = time.strftime("%H%M%S")
TMP = tempfile.mkdtemp(prefix="v110b4_")
results = []
cleanup = []          # 끝에서 거꾸로 실행할 정리 함수
FX = {}               # 공통 시험 데이터
KEEP = "--keep" in sys.argv


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception:
        traceback.print_exc()
        results.append((name, "FAIL"))


_off = time.localtime().tm_gmtoff
TZ_SQL = f"SET TIME ZONE INTERVAL '{'+' if _off >= 0 else '-'}{abs(_off) // 3600:02d}:{abs(_off) % 3600 // 60:02d}' HOUR TO MINUTE;\n"


def psql(sql):
    r = subprocess.run(PSQL + ["-At", "-q", "-v", "ON_ERROR_STOP=1"], input=(TZ_SQL + sql).encode("utf-8"),
                       capture_output=True, timeout=60)
    if r.returncode != 0:
        raise RuntimeError("psql 실패: " + r.stderr.decode("utf-8", "replace")[:300])
    return r.stdout.decode("utf-8", "replace").strip()


def q(v):
    return "NULL" if v is None else "'" + str(v).replace("'", "''") + "'"


def b64(s):
    """화면의 $encodePw 와 같다 — UTF-8 바이트를 Base64"""
    return base64.b64encode(s.encode("utf-8")).decode()


def try_login(user, pwd):
    """로그인 시도 — (세션, 응답 본문)"""
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": b64(pwd)}, allow_redirects=False, timeout=15)
    try:
        return s, r.json()
    except Exception:
        return s, {"http": r.status_code, "text": r.text[:200]}


def login_api(user, pwd=None):
    s, body = try_login(user, pwd or PW)
    assert body.get("success") is not False and "http" not in body, f"로그인 실패 {user} {body}"
    return s


def post(s, path, body, timeout=30):
    r = s.post(BASE + path, json=body, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"http": r.status_code, "text": r.text[:300]}


def get(s, path, params=None, timeout=30):
    r = s.get(BASE + path, params=params, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"http": r.status_code, "text": r.text[:300]}


def code(r):
    return r.get("resultCode") if isinstance(r, dict) else None


def msg(r):
    return (r.get("resultMessage") or "") if isinstance(r, dict) else ""


def contents(r):
    c = r.get("contents")
    return json.loads(c) if isinstance(c, str) and c[:1] in "{[" else c


def wait_until(fn, timeout=60, every=1.0):
    end = time.time() + timeout
    v = fn()
    while not v and time.time() < end:
        time.sleep(every)
        v = fn()
    return v


def src(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def evlog(uid):
    """시험 시작 뒤 그 사용자의 로그인 로그 (심각도|메시지)"""
    out = psql(f"select severity||'|'||replace(msg, E'\\n', '\\n') from quality.tb_event_log "
               f"where job_id='authentication' and job_ex_user_id={q(uid)} and time_val >= '{FX['T0']}'::timestamptz order by time_val")
    return out.splitlines() if out else []


def urow(uid, cols="coalesce(login_fail_count,0)||'|'||coalesce(block_time,'-')||'|'||coalesce(del_yn::text,'-')"):
    return psql(f"select {cols} from quality.tb_user where user_id={q(uid)}")


# ---------------------------------------------------------------- 시험 데이터 헬퍼
def make_user(sa, uid, perms=None, upw=None, admin=False, name=None, phone=None):
    upw = upw or ("Zz" + secrets.token_hex(6))
    body = {"id": uid, "password": b64(upw), "name": name or ("110 4묶음 시험 " + uid), "email": "", "admin": admin}
    if phone is not None:
        body["phone"] = phone
    r = post(sa, "/api/login/createUser", body)
    assert code(r) == 200, r
    if perms:
        r = post(sa, "/api/role/save", {"roleNm": "ZZB4역할_" + uid, "roleDesc": "110 4묶음 시험", "perms": perms})
        assert code(r) == 200, r
        rid = r.get("contents")
        assert code(post(sa, "/api/role/assign", {"userId": uid, "roleId": rid, "sysCd": "*"})) == 200
    return upw


def make_ds(sa, k, dbms="PostgreSQL", port=25433, user="x", pwd="x", svr="127.0.0.1"):
    dsn = f"ZZB4_{k}_{TAG}"
    r = post(sa, "/api/sysinfo/createDataSource", {"dsn": dsn, "dsTp": 0, "dbmsTp": dbms, "driverName": dbms, "svrAddr": svr,
                                                   "port": port, "userId": user, "pwd": pwd, "dbName": "postgres"})
    assert code(r) == 200, r
    ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
    psql(f"update ndata.tb_data_source set conn_test_yn='Y' where ds_id='{ds}'")
    return ds, dsn


def make_model(sa, nm, ds=None, sys_cd=None):
    r = post(sa, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds, "dataModelSysCd": sys_cd,
                                            "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT"})
    dm = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
    assert code(r) == 200 and dm, r
    return dm


def drop_model(dm):
    psql(f"""delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where dm_id='{dm}');
             delete from quality.tb_aprv_doc where dm_id='{dm}';
             delete from quality.tb_diag_schedule_log where schedule_id in (select schedule_id from quality.tb_diag_schedule where data_model_id='{dm}');
             delete from quality.tb_diag_schedule where data_model_id='{dm}';
             delete from quality.tb_diag_result where diag_job_id in (select diag_job_id from quality.tb_diag_job where dm_id='{dm}');
             delete from quality.tb_diag_job where dm_id='{dm}';
             delete from quality.tb_struct_diag_detail where diag_id in (select diag_id from quality.tb_struct_diag_history where data_model_id='{dm}');
             delete from quality.tb_struct_diag_index_detail where diag_id in (select diag_id from quality.tb_struct_diag_history where data_model_id='{dm}');
             delete from quality.tb_struct_diag_constraint_detail where diag_id in (select diag_id from quality.tb_struct_diag_history where data_model_id='{dm}');
             delete from quality.tb_struct_diag_history where data_model_id='{dm}';
             delete from quality.tb_gov_meta_send where dm_id='{dm}';
             delete from quality.tb_batch_run where dm_id='{dm}';
             delete from quality.tb_data_model_change_history where dm_id='{dm}';
             delete from quality.tb_data_model_attr where dm_id='{dm}';
             delete from quality.tb_data_model_constraint where dm_id='{dm}';
             delete from quality.tb_data_model_index where dm_id='{dm}';
             delete from quality.tb_data_model_obj where dm_id='{dm}';
             delete from quality.tb_data_model_clct where dm_id='{dm}';
             delete from quality.tb_data_model_schema where dm_id='{dm}';
             delete from quality.tb_udp_val where dm_id='{dm}';
             delete from quality.tb_term_resolve_history where dm_id='{dm}';
             delete from quality.tb_data_model where dm_id='{dm}';""")


def board_create(s, btype, title, files=(), pin="N", content="110 4묶음 시험"):
    data = {"boardType": btype, "title": title, "content": content, "pinYn": pin}
    fl = [("files", (nm, body, "application/octet-stream")) for nm, body in files]
    r = s.post(BASE + "/api/board/createWithFile", data=data, files=fl or None, timeout=30)
    return r.json()


def board_update(s, bid, title, files=(), pin="N", delete_ids=(), content="110 4묶음 시험 수정"):
    data = [("boardId", bid), ("title", title), ("content", content), ("pinYn", pin)] + [("deleteFileIds", i) for i in delete_ids]
    fl = [("files", (nm, body, "application/octet-stream")) for nm, body in files]
    r = s.post(BASE + "/api/board/updateWithFile", data=data, files=fl or None, timeout=30)
    return r.json()


def board_id(title):
    return psql(f"select board_id from quality.tb_board where title={q(title)}")


def board_files(bid):
    out = psql(f"select file_id||'|'||file_nm||'|'||file_path from quality.tb_board_file where board_id='{bid}' order by file_nm")
    return [l.split("|", 2) for l in out.splitlines()] if out else []


# ---------------------------------------------------------------- 준비
def setup(sa):
    FX["T0"] = psql("select now()::text")
    t = TAG
    FX["uA"], FX["uB"], FX["uM"], FX["uN"], FX["uAdm"], FX["uP"], FX["uR"] = (f"zzb4a{t}", f"zzb4b{t}", f"zzb4m{t}", f"zzb4n{t}",
                                                                             f"zzb4d{t}", f"zzb4p{t}", f"zzb4r{t}")
    FX["pwA"] = make_user(sa, FX["uA"], upw=os.environ.get("DATAQ_B4_USER_PW"))   # --keep 뒤 화면 검수 때 일반 사용자로 들어갈 비밀번호 (없으면 임의)
    FX["pwB"] = make_user(sa, FX["uB"])
    FX["pwM"] = make_user(sa, FX["uM"], ["USER_ADMIN"])
    FX["pwN"] = make_user(sa, FX["uN"], ["BOARD_ADMIN"])
    FX["pwAdm"] = make_user(sa, FX["uAdm"], admin=True)
    FX["pwP"] = make_user(sa, FX["uP"])
    print(f"  사용자 일반 {FX['uA']}·{FX['uB']} / 사용자 관리 {FX['uM']} / 공지 관리 {FX['uN']} / 관리자 {FX['uAdm']} / 비밀번호 {FX['uP']}")


# ---------------------------------------------------------------- A 로그인·세션
def a_auth(sa):
    uA, uP = FX["uA"], FX["uP"]
    WRONG = "아이디 또는 비밀번호가 올바르지 않습니다."

    def _same_msg():
        nouser = "zzb4none" + TAG
        _, r1 = try_login(nouser, "x")
        _, r2 = try_login(uP, "틀린비번")
        lg1, lg2 = evlog(nouser), evlog(uP)
        print(f"  없는 아이디 → {r1} / 로그 {lg1}\n  틀린 비밀번호 → {r2} / 로그 {lg2} / 실패 횟수|차단|삭제 = {urow(uP)}")
        assert r1 == {"success": False, "message": WRONG} and r2 == {"success": False, "message": WRONG}
        # 1.10-U6-a — 실패 한 번에 로그 한 건 (메시지에 ||id 가 붙지 않음)
        assert lg1 == [f"ERROR|{WRONG}"] and lg2 == [f"ERROR|{WRONG}"]
        assert urow(uP).startswith("1|-|")
    step("A-1. 없는 아이디·틀린 비밀번호 같은 문구, 실패 로그 한 건·실패 횟수 +1 (1.10-S5·U6-a)", _same_msg)

    def _success_log():
        s = login_api(uP, FX["pwP"])
        lg = evlog(uP)
        print(f"  성공 뒤 로그 {lg} / 실패 횟수|차단|삭제 = {urow(uP)} / getUser={get(s, '/api/login/getUser')}")
        assert lg[-1] == "INFO|로그인에 성공했습니다." and len([x for x in lg if x.startswith("INFO")]) == 1
        assert urow(uP).startswith("0|-|")
        u = get(s, "/api/login/getUser")
        assert u.get("id") == uP and "password" not in u
    step("A-2. 로그인 성공 로그 문구 '로그인에 성공했습니다.' (||id 없음, 사용자 ID 는 JOB_EX_USER_ID), 실패 횟수 초기화 (1.10-U6-b)", _success_log)

    def _no_admin():
        nm_space = "space"
        before_space = len(evlog(nm_space))
        psql("update quality.tb_user set del_yn=true where user_id='space'")
        try:
            ex = get(requests.Session(), "/api/login/existAdmin")
            _, r1 = try_login("space", PW)
            _, r2 = try_login(uA, FX["pwA"])
        finally:
            psql("update quality.tb_user set del_yn=false where user_id='space'")
        lg_s = evlog(nm_space)[before_space:]
        lg_a = evlog(uA)
        print(f"  existAdmin={ex} / space → {r1} / {uA} → {r2}\n  로그 space {lg_s} / {uA} {lg_a} / {uA} 실패 횟수|차단|삭제 = {urow(uA)}")
        assert ex is False
        assert r1 == {"success": False, "message": "내장 관리자 계정이 없습니다. 관리자 계정을 먼저 만드세요."}
        assert r2 == {"success": False, "message": "관리자 초기설정에 실패했습니다.\n관리자에게 문의하십시오."}
        # provider 가 한 번만 불려 로그도 한 건 (1.10-U6-a), 메시지에 ||id 가 없어 실패 횟수는 그대로
        assert lg_s == ["ERROR|내장 관리자 계정이 없습니다. 관리자 계정을 먼저 만드세요."], lg_s
        assert lg_a == ["ERROR|관리자 초기설정에 실패했습니다.\\n관리자에게 문의하십시오."], lg_a
        assert urow(uA).startswith("0|-|") and urow("space", "del_yn::text") == "false"
        assert get(sa, "/api/login/isAdmin") is True
    step("A-3. 내장 관리자가 없으면 space 는 '관리자 계정을 먼저 만드세요', 다른 ID 는 초기설정 실패 — 로그 한 건, 실패 횟수 그대로 (1.10-B1·U6-a)", _no_admin)

    def _create_admin_restore():
        # space 행이 삭제 표시로 남아 있을 때 안내대로 createAdmin 을 부르면 그 행을 되살린다 (INSERT 면 기본키 중복).
        # 시험 뒤 space 행은 비밀번호 해시까지 시험 전 값으로 되돌린다 (값은 찍지 않는다)
        cols = "pwd, nm, email, phone, adm_yn, cret_dt, updt_dt, org_id, login_fail_count, block_time"
        bak = psql(f"select row_to_json(t)::text from (select {cols} from quality.tb_user where user_id='space') t")
        psql("update quality.tb_user set del_yn=true, login_fail_count=2 where user_id='space'")
        try:
            r = get(requests.Session(), "/api/login/createAdmin", {"password": b64(PW)})
            row = urow("space", "coalesce(del_yn::text,'-')||'|'||adm_yn::text||'|'||login_fail_count||'|'||coalesce(block_time,'-')")
            ex = get(requests.Session(), "/api/login/existAdmin")
            _, lr = try_login("space", PW)
            again = get(requests.Session(), "/api/login/createAdmin", {"password": b64(PW)})
        finally:
            psql("update quality.tb_user u set del_yn=false, pwd=b.pwd, nm=b.nm, email=b.email, phone=b.phone, adm_yn=b.adm_yn, "
                 "cret_dt=b.cret_dt, updt_dt=b.updt_dt, org_id=b.org_id, login_fail_count=b.login_fail_count, block_time=b.block_time "
                 f"from json_populate_record(null::quality.tb_user, {q(bak)}::json) b where u.user_id='space'")
        same = psql(f"select (pwd = ({q(bak)}::json)->>'pwd')::text from quality.tb_user where user_id='space'")
        print(f"  삭제 표시된 space 에 createAdmin → {code(r)} {msg(r)} / 삭제|관리자|실패 횟수|차단 = {row} / existAdmin={ex} / "
              f"로그인 {lr} / 다시 createAdmin → {code(again)} {msg(again)} / 시험 뒤 비밀번호 해시 원래대로 {same}")
        assert code(r) == 200 and row == "false|true|0|-" and ex is True and lr.get("success") is True
        assert (code(again), msg(again)) == (403, "관리자 계정이 이미 있습니다.") and same == "true"
        assert urow("space", "del_yn::text") == "false" and get(sa, "/api/login/isAdmin") is True
    step("A-3b. 내장 관리자 행이 삭제 표시로 남아 있어도 createAdmin 이 그 행을 되살려 관리자를 만든다 — 실패 횟수·차단 초기화, 이미 있으면 403 (1.10-B1 리뷰 후속)", _create_admin_restore)

    def _cors():
        evil = "http://evil.example"
        r1 = requests.post(BASE + "/login", data={"id": FX["uB"], "password": b64(FX["pwB"])}, headers={"Origin": evil}, timeout=10)
        s = requests.Session()
        s.cookies.update(r1.cookies)
        r2 = s.get(BASE + "/api/login/getUser", headers={"Origin": evil}, timeout=10)
        r3 = requests.options(BASE + "/api/board/list", headers={"Origin": evil, "Access-Control-Request-Method": "POST"}, timeout=10)
        h = [(r.status_code, r.headers.get("Access-Control-Allow-Origin"), r.headers.get("Access-Control-Allow-Credentials")) for r in (r1, r2, r3)]
        print(f"  (상태, Allow-Origin, Allow-Credentials) 로그인={h[0]} / API={h[1]} / 사전 요청={h[2]}")
        assert r1.json().get("success") is True
        assert all(x[1] is None and x[2] is None for x in h)
    step("A-4. 다른 출처(Origin) 요청에 Access-Control-Allow-Origin·Credentials 를 주지 않음 — 로그인·API·사전 요청 (1.10-S8)", _cors)

    def _expire():
        uX = "zzb4x" + TAG
        pwX = make_user(sa, uX)
        s1, s2 = login_api(uX, pwX), login_api(uX, pwX)
        assert get(s1, "/api/login/getUser").get("id") == uX
        rm = sa.post(BASE + "/api/login/removeUser", data={"id": uX}, timeout=10)
        r1 = s1.get(BASE + "/api/login/getUser", timeout=10)
        r2 = s2.get(BASE + "/app/main", allow_redirects=False, timeout=10)
        print(f"  삭제 → {rm.status_code} {rm.text} / 만료 세션 API → {r1.status_code} {r1.text} / 화면 → {r2.status_code} {r2.headers.get('Location')}")
        assert rm.status_code == 200 and rm.text == "true"
        assert r1.status_code == 401 and r1.json() == {"resultCode": 401, "resultMessage": "로그인이 필요합니다."}
        assert r2.status_code == 302 and r2.headers.get("Location", "").endswith("/login")
        assert get(sa, "/api/login/getUser").get("id") == "space"
    step("A-5. 사용자를 지우면 그 사용자의 로그인 세션이 끊김 — API 는 401, 화면은 로그인으로 (11.7-14 ①②③)", _expire)

    def _admin_now():
        s = login_api(FX["uB"], FX["pwB"])
        before = (get(s, "/api/login/isAdmin"), get(s, "/api/login/myPerms").get("admin"))
        r = post(sa, "/api/login/updateUser", {"id": FX["uB"], "name": "110 4묶음 시험 " + FX["uB"], "email": "", "admin": True})
        mid = (get(s, "/api/login/isAdmin"), get(s, "/api/login/myPerms").get("admin"))
        ul = s.get(BASE + "/api/login/getUserList", timeout=10).status_code
        r2 = post(sa, "/api/login/updateUser", {"id": FX["uB"], "name": "110 4묶음 시험 " + FX["uB"], "email": "", "admin": False})
        after = (get(s, "/api/login/isAdmin"), get(s, "/api/login/myPerms").get("admin"))
        ul2 = s.get(BASE + "/api/login/getUserList", timeout=10).status_code
        print(f"  지정 전 {before} → 지정({code(r)}) 뒤 {mid}·사용자 목록 {ul} → 해제({code(r2)}) 뒤 {after}·사용자 목록 {ul2}")
        assert before == (False, False) and mid == (True, True) and ul == 200 and after == (False, False) and ul2 == 403
    step("A-6. 관리자 지정·해제가 그 사용자의 다음 요청부터 반영 (다시 로그인하지 않아도) (11.7-14 ③④)", _admin_now)

    def _src():
        prov = src("q-center/src/main/java/qualitycenter/security/CustomAuthenticationProvider.java")
        fh = src("q-center/src/main/java/qualitycenter/security/CustomAuthFailureHandler.java")
        wsc = src("q-center/src/main/java/qualitycenter/security/WebSecurityConfiguration.java")
        sh = src("q-center/src/main/java/qualitycenter/security/CustomAuthSuccessHandler.java")
        wr = src("q-center/src/main/java/qualitycenter/config/WebResourceConfig.java")
        # 1.10-S7 — 세션 사용자·토큰에 평문 비밀번호를 남기지 않음 (응답으로는 볼 수 없어 소스로 확인)
        assert "user.setPassword(null);" in prov and "new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())" in prov
        # 1.10-U5 — 쓰지 않는 예외 분기·문구 삭제, Internal 을 AuthenticationServiceException 보다 먼저
        for gone in ("동일한 아이디로 접속되어 있습니다", "error.BadCredentials", "error.Disaled", "error.CredentialsExpired", "SessionAuthenticationException"):
            assert gone not in fh, gone
        assert fh.index("InternalAuthenticationServiceException") < fh.index("e instanceof AuthenticationServiceException")
        # 1.10-U3-a·S8 — PasswordEncoder 빈·CORS 설정 삭제, 세션 목록 빈은 별도 설정 클래스
        assert "PasswordEncoder" not in wsc and "corsConfigurationSource" not in wsc and "Access-Control-Allow" not in sh
        assert "addCorsMappings" not in wr and "maximumSessions(-1)" in wsc
        assert os.path.exists(os.path.join(ROOT, "q-center/src/main/java/qualitycenter/config/SessionRegistryConfig.java"))
        print("  provider·실패 처리기·보안 설정·리소스 설정 소스 확인")
    step("A-7. 소스 확인 — 세션 사용자·토큰에 비밀번호 없음, 쓰지 않는 실패 분기·PasswordEncoder·CORS 설정 삭제 (1.10-S7·U5·U3-a·S8)", _src)


# ---------------------------------------------------------------- P 사용자·비밀번호
def p_user(sa):
    uP = FX["uP"]

    def _change_pw():
        s = login_api(uP, FX["pwP"])
        old = FX["pwP"]
        g = s.get(BASE + "/api/login/changePassword", params={"oldPassword": b64(old), "newPassword": b64("x")}, timeout=10).status_code
        psql(f"update quality.tb_user set updt_dt='20200101000000' where user_id='{uP}'")
        res = []

        def ch(o, n):
            r = post(s, "/api/login/changePassword", {"oldPassword": b64(o), "newPassword": b64(n)})
            res.append((code(r), msg(r), urow(uP)))
            return r
        ch("틀림", old)            # 현재 비밀번호가 틀리면 새=현재 여부는 알려주지 않음
        ch(old, old)               # 맞으면 실패 횟수 0 으로, 그다음 새=현재 거부
        for _ in range(5):
            ch("틀림", "ZzNew1")   # 5회 → 10분 차단
        ch(old, "ZzNew1")          # 차단 중 — 맞는 비밀번호도 거부
        _, lr = try_login(uP, old)
        for r in res:
            print(f"  {r}")
        print(f"  GET → {g} / 차단 중 로그인 → {lr}")
        assert g == 405
        assert res[0][:2] == (406, "기존 비밀번호가 틀렸습니다.") and res[0][2].startswith("1|-|")
        assert res[1][:2] == (406, "기존 비밀번호와 다른 비밀번호를 입력해야 합니다.") and res[1][2].startswith("0|-|")
        assert [r[2].split("|")[0] for r in res[2:7]] == ["1", "2", "3", "4", "5"] and res[6][2].split("|")[1] != "-"
        assert res[7][0] == 406 and re.fullmatch(r"로그인 차단 시간\[.+\]이 지나지 않았습니다\.", res[7][1]), res[7]
        assert lr.get("success") is False and lr.get("message", "").startswith("로그인 차단 시간[")
        assert urow(uP, "updt_dt") == "20200101000000"
        # 차단을 풀고 정상 변경 — UPDT_DT 갱신 (11.7-12b), 새 비밀번호로 로그인, 옛 비밀번호는 거부
        psql(f"update quality.tb_user set login_fail_count=0, block_time=null where user_id='{uP}'")
        new = "ZzNew" + secrets.token_hex(4)
        r = ch(old, new)
        ud = urow(uP, "updt_dt")
        _, ok = try_login(uP, new)
        _, ng = try_login(uP, old)
        print(f"  변경 → {res[-1][:2]} / UPDT_DT {ud} / 새 비밀번호 로그인 {ok} / 옛 비밀번호 {ng}")
        assert code(r) == 200 and ud > time.strftime("%Y%m%d") and ok.get("success") is True and ng.get("success") is False
        FX["pwP"] = new
        psql(f"update quality.tb_user set login_fail_count=0, block_time=null where user_id='{uP}'")
        # 세션 중에 삭제 표시된 사용자 (세션 만료를 거치지 않게 DB 로 바로) — 404
        s2 = login_api(uP, new)
        psql(f"update quality.tb_user set del_yn=true where user_id='{uP}'")
        try:
            r = post(s2, "/api/login/changePassword", {"oldPassword": b64(new), "newPassword": b64("ZzOther1")})
        finally:
            psql(f"update quality.tb_user set del_yn=false where user_id='{uP}'")
        print(f"  삭제된 사용자 → {code(r)} {msg(r)}")
        assert (code(r), msg(r)) == (404, "존재하지 않는 사용자입니다.")
    step("P-1. 비밀번호 변경 — POST 본문만, 차단 중 거부·틀리면 실패 횟수(5회 차단)·맞으면 초기화·그다음 새=현재 거부, 삭제된 사용자 404, UPDT_DT (11.7-3b·3c·12b)", _change_pw)

    def _deny():
        sB, sM = login_api(FX["uB"], FX["pwB"]), login_api(FX["uM"], FX["pwM"])

        def rm(s, uid):
            r = s.post(BASE + "/api/login/removeUser", data={"id": uid}, timeout=10)
            try:
                return r.status_code, r.json()
            except Exception:
                return r.status_code, r.text
        a = rm(sa, "space")
        b = rm(sB, FX["uA"])
        c = rm(sM, FX["uAdm"])
        lst = sB.get(BASE + "/api/login/getUserList", timeout=10)
        print(f"  관리자→space {a}\n  일반→{FX['uA']} {b}\n  사용자 관리→관리자 {c}\n  일반 사용자 목록 {lst.status_code} {lst.text}")
        assert a == (403, {"success": False, "message": "내장 관리자 계정은 삭제할 수 없습니다."})
        assert b == (403, {"success": False, "message": "사용자를 삭제할 권한이 없습니다 (사용자·권한 관리 권한 필요)."})
        assert c == (403, {"success": False, "message": "관리자 계정은 관리자만 삭제할 수 있습니다."})
        assert lst.status_code == 403 and lst.json() == {"success": False, "message": "사용자 목록을 볼 권한이 없습니다 (사용자·권한 관리 권한 필요)."}
        assert psql(f"select string_agg(coalesce(del_yn, false)::text, ',') from quality.tb_user "
                    f"where user_id in ('space', '{FX['uA']}', '{FX['uAdm']}')") == "false,false,false"
    step("P-2. 사용자 삭제 거부 사유가 본문(message)으로 — 내장 관리자·권한 없음·관리자 계정, 사용자 목록 권한 없음 (11.7-4·11b)", _deny)

    def _restore():
        uR = FX["uR"]
        pw1 = make_user(sa, uR, ["BOARD_ADMIN"], name="ZZB4옛이름", phone="010-1111-2222")
        first = urow(uR, "coalesce(phone,'-')")
        psql(f"update quality.tb_user set org_id='ZZB4ORG', login_fail_count=3, cret_dt='20200101000000' where user_id='{uR}'")
        # 예전 사용자가 부서장이던 부서 — 되살리면 부서장 지정을 비운다 (리뷰 후속)
        org = "ZZB4ORG" + TAG
        psql(f"insert into quality.tb_org (org_id, org_nm, head_user_id, use_yn, cret_dt, updt_dt, updt_user_id) "
             f"values ({q(org)}, 'ZZB4부서', {q(uR)}, 'Y', '20200101000000', '20200101000000', 'zzb4old')")
        roles0 = psql(f"select count(*) from quality.tb_user_role where user_id='{uR}'")
        r = sa.post(BASE + "/api/login/removeUser", data={"id": uR}, timeout=10)
        assert r.text == "true"
        dup = post(sa, "/api/login/createUser", {"id": FX["uA"], "password": b64("x"), "name": "x", "email": "", "admin": False})
        pw2 = "ZzNew" + secrets.token_hex(4)
        r2 = post(sa, "/api/login/createUser", {"id": uR, "password": b64(pw2), "name": "ZZB4새이름", "email": "new@zzb4.test",
                                                "phone": "010-3333-4444", "admin": False})
        row = urow(uR, "nm||'|'||coalesce(email,'-')||'|'||coalesce(phone,'-')||'|'||coalesce(adm_yn::text,'-')||'|'||coalesce(del_yn::text,'-')||'|'||"
                       "coalesce(login_fail_count,0)||'|'||coalesce(block_time,'-')||'|'||coalesce(org_id,'-')||'|'||(cret_dt > '2026')::text")
        roles = psql(f"select count(*) from quality.tb_user_role where user_id='{uR}'")
        head = psql(f"select coalesce(head_user_id,'-')||'|'||coalesce(updt_user_id,'-')||'|'||(updt_dt > '2026')::text "
                    f"from quality.tb_org where org_id={q(org)}")
        _, ok = try_login(uR, pw2)
        _, ng = try_login(uR, pw1)
        perms = get(login_api(uR, pw2), "/api/login/myPerms").get("perms")
        print(f"  처음 추가 전화번호 {first} / 역할 {roles0} → 삭제 → 다시 추가 {code(r2)} / 행 {row} / 역할 {roles}\n"
              f"  부서장|부서 수정자|수정 일시 갱신 = {head}\n"
              f"  새 비밀번호 로그인 {ok} / 옛 비밀번호 {ng} / 권한 {perms} / 미삭제 중복 → {code(dup)} {msg(dup)}")
        assert first == "010-1111-2222" and roles0 == "1"
        assert code(r2) == 200 and row == "ZZB4새이름|new@zzb4.test|010-3333-4444|false|false|0|-|-|true" and roles == "0"
        assert head == "-|space|true", head
        assert ok.get("success") is True and ng.get("success") is False and not (perms or {}).get("BOARD_ADMIN")
        assert code(dup) == 500 and msg(dup) == f"이미 존재하는 사용자입니다({FX['uA']})"
    step("P-3. 삭제된 같은 ID 를 사용자 추가로 되살림 — 새 이름·전화·비밀번호, 역할·부서·부서장 지정·실패 횟수 비움, 미삭제 중복은 그대로 거부 (11.7-11a·12a)", _restore)

    def _update_dt():
        psql(f"update quality.tb_user set updt_dt='20200101000000' where user_id='{FX['uA']}'")
        r = post(sa, "/api/login/updateUser", {"id": FX["uA"], "name": "110 4묶음 시험 " + FX["uA"], "email": "", "phone": "010-5555-6666", "admin": False})
        row = urow(FX["uA"], "updt_dt||'|'||coalesce(phone,'-')")
        print(f"  수정 → {code(r)} / UPDT_DT|전화 = {row}")
        assert code(r) == 200 and row.split("|")[0] > time.strftime("%Y%m%d") and row.endswith("|010-5555-6666")
    step("P-4. 사용자 수정이 UPDT_DT 를 현재 시각으로 (11.7-12b)", _update_dt)

    def _src():
        ss = src("q-center/src/main/java/qualitycenter/service/auth/SessionService.java")
        for gone in ("getRoleList", "createRole", "updateRoleName", "deleteRole", "updateRoleAuthority", "addUserRole", "removeUserRole",
                     "StringEncryptor", "AuthorityManager"):
            assert gone not in ss, gone
        print("  SessionService 의 호출처 없는 역할 메서드 7개·주입 정리 확인")
    step("P-5. 소스 확인 — SessionService 의 쓰지 않는 역할 메서드·주입 삭제 (11.7-23b)", _src)


# ---------------------------------------------------------------- B 게시판
def b_board(sa):
    sA, sB, sN = login_api(FX["uA"], FX["pwA"]), login_api(FX["uB"], FX["pwB"]), login_api(FX["uN"], FX["pwN"])
    NOT_OWNER = "작성자나 공지 관리 권한이 있는 사용자만 수정·삭제할 수 있습니다."
    FX["sA"] = sA

    def _type():
        r = board_create(sA, "FOO", "ZZB4유형_" + TAG)
        r2 = board_create(sA, "NOTICE", "ZZB4공지_" + TAG)
        print(f"  FOO → {code(r)} {msg(r)} / 일반 사용자 NOTICE → {code(r2)} {msg(r2)}")
        assert (code(r), msg(r)) == (400, "게시판 유형이 올바르지 않습니다.") and not board_id("ZZB4유형_" + TAG)
        assert code(r2) != 200 and not board_id("ZZB4공지_" + TAG)
    step("B-1. 게시판 유형이 NOTICE·QNA 가 아니면 400 (10.3-2c)", _type)

    def _pin():
        tA, tN = "ZZB4고정A_" + TAG, "ZZB4고정N_" + TAG
        board_create(sA, "QNA", tA, pin="Y")
        board_create(sN, "QNA", tN, pin="Y")
        a, n = board_id(tA), board_id(tN)
        p0 = psql(f"select string_agg(title||':'||pin_yn, ',' order by title) from quality.tb_board where board_id in ('{a}','{n}')")
        psql(f"update quality.tb_board set pin_yn='Y' where board_id='{a}'")
        r1 = board_update(sA, a, tA, pin="N")
        p1 = psql(f"select pin_yn from quality.tb_board where board_id='{a}'")
        r2 = board_update(sN, a, tA, pin="N")
        p2 = psql(f"select pin_yn from quality.tb_board where board_id='{a}'")
        print(f"  등록(일반 Y·공지 관리 Y) → {p0} / 관리자가 고정한 글을 작성자가 N 으로 수정 {code(r1)} → {p1} / 공지 관리 권한자가 N → {code(r2)} {p2}")
        assert p0 == f"{tA}:N,{tN}:Y" and code(r1) == 200 and p1 == "Y" and code(r2) == 200 and p2 == "N"
        FX["bA"] = a
    step("B-2. 상단 고정은 공지 관리 권한만 — 일반 사용자 등록은 N, 작성자가 고쳐도 지금 값 유지 (10.3-2a)", _pin)

    def _owner():
        a = FX["bA"]
        r = [board_update(sB, a, "남이 고침"), post(sB, "/api/board/delete", {"boardId": a}),
             board_update(sA, "ZZB4없는글", "x"), post(sA, "/api/board/delete", {"boardId": "ZZB4없는글"})]
        post(sA, f"/api/board/{a}/comment", {"content": "ZZB4댓글A"})
        cid = psql(f"select comment_id from quality.tb_board_comment where board_id='{a}' and content='ZZB4댓글A'")
        rc = [post(sB, "/api/board/comment/update", {"commentId": cid, "content": "남이 고침"}),
              post(sB, "/api/board/comment/delete", {"commentId": cid}),
              post(sA, "/api/board/comment/update", {"commentId": "ZZB4없는댓글", "content": "x"}),
              post(sA, "/api/board/comment/delete", {"commentId": "ZZB4없는댓글"}),
              post(sA, "/api/board/comment/update", {"commentId": cid, "content": "ZZB4댓글A수정"}),
              post(sN, "/api/board/comment/update", {"commentId": cid, "content": "ZZB4댓글A공지관리"})]
        rn = board_update(sN, a, "ZZB4고정A_" + TAG, content="공지 관리 권한자가 고침")
        fd = sA.post(BASE + "/api/board/file/delete", json={"fileId": "x"}, timeout=10).status_code
        st = psql(f"select title||'|'||content from quality.tb_board where board_id='{a}'")
        cm = psql(f"select content from quality.tb_board_comment where comment_id='{cid}'")
        for x in r + rc:
            print(f"  {code(x)} {msg(x)}")
        print(f"  공지 관리 권한자 수정 → {code(rn)} / 글 {st} / 댓글 {cm} / 첨부 단건 삭제 API → {fd}")
        assert [(code(x), msg(x)) for x in r] == [(403, NOT_OWNER), (403, NOT_OWNER), (404, "게시글을 찾을 수 없습니다."), (404, "게시글을 찾을 수 없습니다.")]
        assert [(code(x), msg(x)) for x in rc[:4]] == [(403, NOT_OWNER), (403, NOT_OWNER), (404, "댓글을 찾을 수 없습니다."), (404, "댓글을 찾을 수 없습니다.")]
        assert code(rc[4]) == 200 and code(rc[5]) == 200 and code(rn) == 200
        assert st == f"ZZB4고정A_{TAG}|공지 관리 권한자가 고침" and cm == "ZZB4댓글A공지관리" and fd in (404, 405)
        FX["cA"] = cid
    step("B-3. 글·댓글 수정·삭제는 작성자나 공지 관리 권한만 (403), 없는 글·댓글 404, 첨부 단건 삭제 API 없음 (10.3-1)", _owner)

    def _notice():
        nid = "ZZB4N" + TAG
        psql(f"insert into quality.tb_board (board_id, board_type, title, content, cret_user_id, cret_dt, view_cnt, pin_yn) "
             f"values ('{nid}', 'NOTICE', 'ZZB4공지작성자_{TAG}', 'x', '{FX['uA']}', current_timestamp, 0, 'N')")
        r = board_update(sA, nid, "작성자가 고침")
        r2 = board_update(sN, nid, "ZZB4공지작성자_" + TAG, content="공지 관리 권한자가 고침")
        print(f"  작성자(공지 관리 권한 없음) → {code(r)} {msg(r)} / 공지 관리 권한자 → {code(r2)}")
        assert (code(r), msg(r)) == (403, "공지사항을 수정할 권한이 없습니다 (공지 관리 권한 필요).") and code(r2) == 200
    step("B-4. 공지는 작성자여도 공지 관리 권한이 있어야 수정 (10.3-2b)", _notice)

    def _files():
        t = "ZZB4첨부_" + TAG
        bad = board_create(sA, "QNA", t, files=[("ok.txt", b"a"), ("run.exe", b"MZ")])
        many = board_create(sA, "QNA", t, files=[(f"f{i}.txt", b"a") for i in range(11)])
        nofile = board_id(t)
        ok = board_create(sA, "QNA", t, files=[(f"f{i}.txt", b"a") for i in range(9)] + [("대문자.PDF", b"%PDF")])
        bid = board_id(t)
        fs = board_files(bid)
        over = board_update(sA, bid, t, files=[("more.txt", b"a")])
        swap = board_update(sA, bid, t, files=[("more.txt", b"a")], delete_ids=[fs[0][0]])
        fs2 = board_files(bid)
        print(f"  exe → {code(bad)} {msg(bad)} / 11개 → {code(many)} {msg(many)} / 거부 때 글 {nofile!r}\n"
              f"  10개(대문자 확장자) → {code(ok)} 첨부 {len(fs)} / 수정 +1 → {code(over)} {msg(over)} / 하나 빼고 +1 → {code(swap)} 첨부 {len(fs2)}")
        assert (code(bad), msg(bad)) == (400, "첨부할 수 없는 파일 형식입니다: run.exe")
        assert (code(many), msg(many)) == (400, "첨부 파일은 글당 10개까지입니다.") and nofile == ""
        assert code(ok) == 200 and len(fs) == 10 and (code(over), msg(over)) == (400, "첨부 파일은 글당 10개까지입니다.")
        assert code(swap) == 200 and len(fs2) == 10 and fs[0][0] not in [f[0] for f in fs2] and not os.path.exists(fs[0][2])
        FX["bF"] = bid
    step("B-5. 첨부 확장자·글당 10개 — 등록·수정 모두 DB·디스크 쓰기 전에 400, 수정은 기존 − 삭제 표시 + 새 파일 (10.3-4·6)", _files)

    def _del_ids():
        bid, other = FX["bF"], FX["bA"]
        board_update(sA, other, "ZZB4고정A_" + TAG, files=[("남의.txt", b"a")])
        ofid = board_files(other)[0][0]
        fs = board_files(bid)
        r = board_update(sA, bid, "ZZB4첨부_" + TAG, delete_ids=[ofid])
        r2 = board_update(sA, bid, "ZZB4첨부_" + TAG, delete_ids=[fs[1][0], fs[2][0]])
        fs2 = board_files(bid)
        print(f"  남의 글 첨부 → {code(r)} {msg(r)} / 내 글 첨부 2개 → {code(r2)} 남은 {len(fs2)} / 지운 파일 디스크 {[os.path.exists(f[2]) for f in fs[1:3]]}")
        assert (code(r), msg(r)) == (400, f"이 글의 첨부 파일이 아닙니다: {ofid}") and len(board_files(other)) == 1
        assert code(r2) == 200 and len(fs2) == 8 and not any(os.path.exists(f[2]) for f in fs[1:3]) and all(os.path.exists(f[2]) for f in fs2)
    step("B-6. 수정 때 지울 첨부(deleteFileIds)는 그 글의 것만 — 남의 첨부 400, 커밋 뒤 디스크 파일 삭제 (10.3-6)", _del_ids)

    def _list_dl():
        bid = FX["bF"]
        fl = get(sA, f"/api/board/{bid}/files")
        name = "ZZB4 한글 파일+1.txt"
        board_update(sA, bid, "ZZB4첨부_" + TAG, files=[(name, "내용".encode("utf-8"))], delete_ids=[board_files(bid)[0][0]])
        fid = psql(f"select file_id from quality.tb_board_file where board_id='{bid}' and file_nm={q(name)}")
        r = sA.get(BASE + f"/api/board/file/download/{fid}", timeout=10)
        cd = r.headers.get("Content-Disposition")
        print(f"  목록 키 {sorted(fl[0].keys())}\n  내려받기 {r.status_code} {cd} / 본문 {r.content.decode('utf-8')}")
        assert fl and all("filePath" not in f for f in fl)
        enc = "ZZB4%20%ED%95%9C%EA%B8%80%20%ED%8C%8C%EC%9D%BC%2B1.txt"
        assert r.status_code == 200 and cd == f'attachment; filename="{enc}"; filename*=UTF-8\'\'{enc}' and r.content.decode("utf-8") == "내용"
    step("B-7. 첨부 목록에 서버 경로(filePath) 없음, 내려받기 파일명은 공백 %20·filename*=UTF-8 (10.3-3·7)", _list_dl)

    def _views():
        bid = FX["bF"]
        psql(f"update quality.tb_board set view_cnt=0 where board_id='{bid}'")
        sB2 = login_api(FX["uB"], FX["pwB"])
        b1 = get(sB, f"/api/board/{bid}")
        get(sB, f"/api/board/{bid}")
        get(sB, f"/api/board/{bid}")
        v1 = psql(f"select view_cnt from quality.tb_board where board_id='{bid}'")
        get(sB2, f"/api/board/{bid}")
        v2 = psql(f"select view_cnt from quality.tb_board where board_id='{bid}'")
        print(f"  같은 세션 3번 → {v1} / 다른 세션(다시 로그인) → {v2}")
        assert v1 == "1" and v2 == "2" and b1.get("boardId") == bid
    step("B-8. 조회수는 세션당 한 번 (10.3-9)", _views)

    def _null_key():
        t = "ZZB4널키_" + TAG
        board_create(sA, "QNA", t)
        b = get(sA, f"/api/board/{board_id(t)}")
        print(f"  새 글 응답 키 {sorted(b.keys())} / updtDt={b.get('updtDt')!r}")
        assert "updtDt" in b and b["updtDt"] is None
    step("B-9. 결과가 NULL 인 컬럼도 응답 키로 남음 — 새 글의 updtDt: null (1.10-U4 callSettersOnNulls)", _null_key)

    def _delete_all():
        t = "ZZB4삭제_" + TAG
        board_create(sA, "QNA", t, files=[("d1.txt", b"a"), ("d2.png", b"b")])
        bid = board_id(t)
        fs = board_files(bid)
        post(sA, f"/api/board/{bid}/comment", {"content": "c1"})
        post(sB, f"/api/board/{bid}/comment", {"content": "c2"})
        r = post(sA, "/api/board/delete", {"boardId": bid})
        left = psql(f"select (select count(*) from quality.tb_board where board_id='{bid}')||'/'||(select count(*) from quality.tb_board_file where board_id='{bid}')"
                    f"||'/'||(select count(*) from quality.tb_board_comment where board_id='{bid}')")
        print(f"  삭제 → {code(r)} / 남은 글/첨부/댓글 {left} / 디스크 {[os.path.exists(f[2]) for f in fs]}")
        assert code(r) == 200 and left == "0/0/0" and len(fs) == 2 and not any(os.path.exists(f[2]) for f in fs)
    step("B-10. 글 삭제는 첨부·댓글·글을 한 트랜잭션으로, 커밋 뒤 디스크 파일 삭제 (10.3-11)", _delete_all)


# ---------------------------------------------------------------- D 데이터 소스·시스템·진단
def d_ds(sa):
    def _refs():
        dm_, dsy, dg, doff, dfr = (make_ds(sa, k) for k in ("1m", "2s", "3g", "4off", "5free"))
        FX["dsUsed"] = dm_
        mA = make_model(sa, f"ZZB4모델A_{TAG}", dm_[0])
        mB = make_model(sa, f"ZZB4모델B_{TAG}", dm_[0])
        mS = make_model(sa, f"ZZB4모델S_{TAG}", dsy[0])
        mO = make_model(sa, f"ZZB4모델OFF_{TAG}", doff[0])
        psql(f"update quality.tb_data_model set use_yn='N' where dm_id='{mO}'; update quality.tb_data_model set use_yn=null where dm_id='{mB}'")
        r = post(sa, "/api/sysinfo/createSysInfo", {"sysNm": f"ZZB4시스템_{TAG}", "sysTp": 1, "sysDsLst": [dsy[0]], "sysDesc": "110 4묶음 시험"})
        assert code(r) == 200, r
        psql(f"insert into quality.tb_gov_meta_db (ds_id, db_nm_kr, cret_dt, cret_user_id) values ('{dg[0]}', 'ZZB4범정부', '20261031000000', 'space')")
        sB = login_api(FX["uB"], FX["pwB"])

        def dl(s, *ds):
            return post(s, "/api/sysinfo/deleteDataSources", [{"id": d[0]} for d in ds])
        r0 = dl(sB, dfr)
        r1 = dl(sa, dm_, dfr)
        r2 = dl(sa, dg, dsy, dm_)
        alive = psql("select count(*) from ndata.tb_data_source where dsn like 'ZZB4%'")
        r3 = dl(sa, doff, dfr, dg)
        alive2 = psql("select string_agg(dsn, ',' order by dsn) from ndata.tb_data_source where dsn like 'ZZB4%'")
        gov = psql(f"select count(*) from quality.tb_gov_meta_db where ds_id='{dg[0]}'")
        print(f"  권한 없음 → {code(r0)} {msg(r0)}\n  [{dm_[1]}, 미사용] → {code(r1)} {msg(r1)}\n  [범정부 항목만, 시스템, 모델] → {code(r2)} {msg(r2)}\n"
              f"  거부 뒤 남은 수 {alive} / [사용 안 함 모델만, 미사용, 범정부 항목만] → {code(r3)} / 남은 {alive2} / 범정부 메타 DB 항목 {gov}")
        assert code(r0) != 200
        assert (code(r1), msg(r1)) == (400, f"사용 중인 데이터 소스는 삭제할 수 없습니다 — {dm_[1]}: 데이터 모델 ZZB4모델A_{TAG}·ZZB4모델B_{TAG}")
        assert (code(r2), msg(r2)) == (400, f"사용 중인 데이터 소스는 삭제할 수 없습니다 — {dm_[1]}: 데이터 모델 ZZB4모델A_{TAG}·ZZB4모델B_{TAG} / "
                                            f"{dsy[1]}: 데이터 모델 ZZB4모델S_{TAG}, 시스템 ZZB4시스템_{TAG}")
        # 범정부 메타 DB 항목은 수집 때 자동으로 생겨 막지 않고 데이터 소스와 함께 지운다
        assert alive == "5" and code(r3) == 200 and alive2 == f"{dm_[1]},{dsy[1]}" and gov == "0"
    step("D-1. 데이터 모델(사용 중)·시스템이 쓰는 데이터 소스는 삭제 거부, 쓰는 곳을 사유로 — 하나라도 있으면 아무것도 안 지움, 범정부 메타 DB 항목은 함께 지움 (11.7-19)", _refs)

    def _gone():
        r = sa.get(BASE + "/api/sysinfo/getSysInfoBySysCd", params={"sysCd": "x"}, timeout=10)
        x = src("q-common/src/main/resources/mapper/stnd/sysinfo.xml")
        c = src("q-center/src/main/java/qualitycenter/controller/SysInfoController.java")
        ids = [i for i in ("selectSysInfoBySysCd", "deleteSysInfoList", "selectDataSourceByDsTp", 'id="deleteDataSource"') if i in x]
        # 처리하는 API 가 없으면 WebResourceConfig 의 화면 폴백이 index.html 을 준다 (JSON 이 아님)
        print(f"  getSysInfoBySysCd → {r.status_code} {r.headers.get('Content-Type')} {r.text[:40]!r} / 남은 쿼리 {ids} / "
              f"컨트롤러 WebSocketService·SqlSessionFactory {('WebSocketService' in c, 'SqlSessionFactory' in c)}")
        assert "json" not in (r.headers.get("Content-Type") or "") and "<div id=app" in r.text.replace('"', "")
        assert not ids and "WebSocketService" not in c and "SqlSessionFactory" not in c and "getSysInfoBySysCd" not in c
    step("D-2. 호출처 없는 API getSysInfoBySysCd 없음(화면 폴백), 쿼리 4개·컨트롤러 주입 삭제 (11.7-24a·24b·25)", _gone)

    def _pg():
        role = ("zzb4r" + TAG).lower()
        pw = "Zz" + secrets.token_hex(6)
        sch = f"zzb4s{TAG}"
        psql(f"create role {role} login password '{pw}'")
        psql(f"create schema {sch} authorization {role}")
        psql(f"create table {sch}.t1 (id int primary key, nm varchar(20)); alter table {sch}.t1 owner to {role}")
        ds, dsn = make_ds(sa, "pg", user=role, pwd=pw)
        enc = psql(f"select pwd from ndata.tb_data_source where ds_id='{ds}'")
        s1 = post(sa, "/api/dm/getSchemaList", {"dataModelDsId": ds})
        s2 = post(sa, "/api/dm/getSchemaList", {"dataModelDsId": ds})
        dm = make_model(sa, f"ZZB4진단모델_{TAG}", ds)
        assert code(post(sa, "/api/dm/saveDataModelSchemas", [{"dataModelId": dm, "schemaNm": sch, "useYn": "Y"}])) == 200
        clct = []
        for _ in range(2):
            n0 = psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}'")
            r = post(sa, "/api/dm/collectDataModel", {"dataModelId": dm, "dataModelDsId": ds, "dataModelNm": f"ZZB4진단모델_{TAG}"}, timeout=60)
            assert code(r) == 200, r
            clct.append(wait_until(lambda: psql(f"select string_agg(clct_cmptn_yn, ',' order by clct_start_dt) from quality.tb_data_model_clct "
                                                f"where dm_id='{dm}' and clct_end_dt is not null having count(*) > {n0}"), 90, 2))
        print(f"  스키마 목록 1·2회 → {sch in s1.get('schemas', [])}·{sch in s2.get('schemas', [])} (접속 사용자 {s2.get('currentUser')}) / "
              f"수집 2회 → {clct} / 저장된 비밀번호 그대로 {enc == psql(f'select pwd from ndata.tb_data_source where ds_id={q(ds)}')}")
        assert sch in s1.get("schemas", []) and sch in s2.get("schemas", []) and clct[-1] == "Y,Y"
        assert enc == psql(f"select pwd from ndata.tb_data_source where ds_id='{ds}'") and enc != pw
        FX.update(pgDm=dm, pgDs=ds, pgSch=sch, pgRole=role)
    step("D-3. 같은 데이터 소스로 스키마 목록·수집을 거듭해도 접속됨 — 넘겨받은 VO 를 바꾸지 않음 (11.7-26)", _pg)

    def _socket():
        dm = FX["pgDm"]
        r = post(sa, "/api/diag/startDiag", {"dataModelId": dm})
        job = r.get("contents")
        st = wait_until(lambda: psql(f"select status from quality.tb_diag_job where diag_job_id='{job}' and status not in ('READY','RUNNING')"), 90)
        stop = post(sa, "/api/diag/stopDiag", {"diagJobId": job})
        r2 = post(sa, "/api/std/structDiag/execute", {"dataModelId": dm})
        did = r2.get("contents")
        st2 = wait_until(lambda: psql(f"select status from quality.tb_struct_diag_history where diag_id='{did}' and status not in ('READY','RUNNING')"), 90, 2)
        r3 = post(sa, "/api/diag/schedule/create", {"scheduleNm": "ZZB4즉시_" + TAG, "diagType": "STRUCT", "dataModelId": dm, "scheduleType": "SIMPLE",
                                                  "repeatCycle": "DAILY", "repeatTime": "02:00", "useYn": "N"})
        sid = r3.get("contents")
        r4 = post(sa, "/api/diag/schedule/runNow", {"scheduleId": sid})
        lg = wait_until(lambda: psql(f"select exec_status from quality.tb_diag_schedule_log where schedule_id='{sid}' and exec_status <> 'RUNNING'"), 90, 2)
        print(f"  진단 실행 {code(r)} → Job {st} / 중지 {code(stop)} / 구조 진단 {code(r2)} → {st2} / 스케줄 {code(r3)} 즉시 실행 {code(r4)} → LOG {lg}")
        assert code(r) == 200 and st == "DONE" and code(stop) == 200 and code(r2) == 200 and st2 == "DONE"
        assert code(r3) == 200 and code(r4) == 200 and lg == "DONE"
        for f, ln in (("DiagController.java", 2), ("StructDiagController.java", 1), ("DiagScheduleController.java", 1)):
            c = src("q-center/src/main/java/qualitycenter/controller/" + f)
            assert c.count("postData(sessionService.getUserId(), null,") + c.count("postData(userId, null,") >= ln, f
    step("D-4. 표준 진단 실행·중지, 구조 진단 실행, 스케줄 즉시 실행이 소켓 세션 ID 없이 끝까지 (1.10-U7)", _socket)

    def _after_collect():
        dm, ds = FX["pgDm"], FX["pgDs"]
        gov0 = psql(f"select coalesce(dbms_nm,'-') from quality.tb_gov_meta_db where ds_id='{ds}'")
        r1 = post(sa, "/api/sysinfo/deleteDataSources", [{"id": ds}])
        psql(f"update quality.tb_data_model set use_yn='N' where dm_id='{dm}'")
        r2 = post(sa, "/api/sysinfo/deleteDataSources", [{"id": ds}])
        left = psql(f"select (select count(*) from ndata.tb_data_source where ds_id='{ds}')||'/'||(select count(*) from quality.tb_gov_meta_db where ds_id='{ds}')")
        print(f"  수집이 남긴 범정부 메타 DB 항목(DBMS) {gov0} / 모델 사용 중 삭제 → {code(r1)} {msg(r1)} / 모델 사용 안 함 뒤 삭제 → {code(r2)} / 남은 데이터 소스/항목 {left}")
        assert gov0 not in ("", "-") and code(r1) == 400 and code(r2) == 200 and left == "0/0"
    step("D-5. 수집한 데이터 소스도 모델을 안 쓰게 되면 지울 수 있음 — 수집이 자동으로 만든 범정부 메타 DB 항목은 함께 지움 (11.7-19)", _after_collect)


# ---------------------------------------------------------------- M 빌드 결과·소스
def m_misc(sa):
    def _maps():
        html = requests.get(BASE + "/", timeout=10).text
        js = re.findall(r'src=/?(static/js/[^ >"]+\.js)', html) + re.findall(r'src="/?(static/js/[^"]+\.js)"', html)
        css = re.findall(r'href=/?(static/css/[^ >"]+\.css)', html) + re.findall(r'href="/?(static/css/[^"]+\.css)"', html)
        res = []
        for p in sorted(set(js + css)):
            body = requests.get(f"{BASE}/{p}", timeout=20).text
            m = requests.get(f"{BASE}/{p}.map", timeout=20)
            res.append((p, "sourceMappingURL=" + os.path.basename(p) in body, m.status_code, '"mappings"' in m.text))
        disk = [f for _, _, fs in os.walk(os.path.join(ROOT, "q-center/src/main/resources/static/static")) for f in fs if f.endswith(".map")]
        for x in res:
            print(f"  {x}")
        print(f"  빌드 폴더 .map {disk}")
        assert len(res) >= 3 and all(not x[1] and not x[3] for x in res) and not disk
        FX["appJs"] = [p for p in js if "/app." in p][0]
    step("M-1. 운영 빌드에 소스맵 없음 — .map 파일·sourceMappingURL 없음 (1.10-S11)", _maps)

    def _front():
        app = requests.get(f"{BASE}/{FX['appJs']}", timeout=20).text
        notice = re.search(r'TEST NOTICE \["\+\w+\.noticeType\+"\] : "\+\w+\.data\+"<br /><br />"', app)
        reload_ = re.search(r'TEST RELOAD \["\+\w+\.dest\+"\] : "\+\w+\.data\+"<br /><br />"', app)
        mj, cj = src("q-center/vue/front/src/main.js"), src("q-center/vue/front/static/js/common.js")
        ws_gone = not os.path.exists(os.path.join(ROOT, "q-center/src/main/java/qualitycenter/controller/WebSocketController.java"))
        print(f"  번들 알림 문구 결합 {bool(notice)}·{bool(reload_)} / $checkSession 남음 {('$checkSession' in mj, 'checkSession' in cj)} / WebSocketController 삭제 {ws_gone}")
        assert notice and reload_ and "NaN" not in notice.group(0)
        assert "$checkSession" not in mj and "checkSession" not in cj and "import axios" not in cj and ws_gone
        assert "btoa(" not in "".join(src(f"q-center/vue/front/src/{p}") for p in
                                      ("views/login/NdLogin.vue", "components/DSMyProfile.vue", "components/MMUser.vue"))
    step("M-2. 화면 소스·번들 — 알림 문구 '+ +' 결합 고침, 결과를 못 쓰던 $checkSession·STOMP 수신 컨트롤러 삭제, btoa 직접 호출 없음 (1.10-B3·B2·B4·S6-b)", _front)

    def _encoding():
        # 비밀번호 해시가 JVM 기본 문자셋을 따르므로 q-center 기동 명령은 문자셋을 UTF-8 로 고정한다 (1.10-S6-b 리뷰 후속)
        dk = src("docker/q-center/Dockerfile")
        bb = [ln for ln in src("build.bat").splitlines() if "q-center-0.0.1-SNAPSHOT.jar" in ln and "java" in ln]
        ent = [ln for ln in dk.splitlines() if ln.startswith("ENTRYPOINT")]
        print(f"  Dockerfile {ent} / build.bat q-center 기동 {len(bb)}줄")
        assert ent == ['ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]']
        assert bb and all("java -Dfile.encoding=UTF-8 -jar q-center-0.0.1-SNAPSHOT.jar" in ln for ln in bb)
    step("M-3. q-center 기동 명령(Dockerfile·build.bat)에 -Dfile.encoding=UTF-8 — 비밀번호 해시 문자셋 고정 (1.10-S6-b 리뷰 후속)", _encoding)


# ---------------------------------------------------------------- 화면(셀레니움)
VM = ("const vm = (tab, name) => { const e = [...document.querySelectorAll('#' + tab + ' *')].find(x => x.__vue__); "
      "const down = v => { if (v.$options.name === name) return v; for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; }; "
      "let c = e && e.__vue__; while (c) { const d = down(c); if (d) return d; c = c.$parent; } return null; };")
NAV = ("const nav = (() => { const e = document.querySelector('#nav_dashboard'); let v = e && e.__vue__; "
       "while (v && v.$options.name !== 'NdNav') v = v.$parent; return v; })();")
LAYOUT = ("const lay = (() => { const e = document.querySelector('#nav_dashboard'); let v = e && e.__vue__; "
          "while (v && !('navScheduleGroup' in v.$data)) v = v.$parent; return v; })();")
CONTENT = LAYOUT + ("const cnt = (function down(v) { if (v.$options.name === 'NdContent') return v; "
                     "for (const ch of v.$children) { const r = down(ch); if (r) return r; } return null; })(lay);")
HOOK = ("if (!window.__errs) { window.__errs = []; const oe = console.error; console.error = function() { window.__errs.push([...arguments].map(String).join(' ')); "
        "oe.apply(console, arguments); }; window.addEventListener('error', e => window.__errs.push('onerror ' + e.message)); "
        "window.addEventListener('unhandledrejection', e => window.__errs.push('rejection ' + (e.reason && e.reason.message || e.reason))); }")


class UI:
    inst = None

    @classmethod
    def get(cls):
        if not cls.inst:
            cls.inst = UI()
        return cls.inst

    def __init__(self):
        from selenium import webdriver
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import WebDriverWait
        from edgeopt import headless
        opts = webdriver.EdgeOptions()
        opts.add_argument("--log-level=3")
        self.drv = webdriver.Edge(options=headless(opts))
        self.drv.set_window_size(1366, 768)
        self.By, self.Wait = By, WebDriverWait
        self.errs = []
        self.user = None

    def js(self, s, *a):
        return self.drv.execute_script(s, *a)

    def collect_errs(self):
        try:
            self.errs += self.js("return window.__errs || []") or []
        except Exception:
            pass

    def login(self, user, pwd=None, force=False):
        if self.user == user and not force:
            return
        if self.user:
            self.collect_errs()
        By = self.By
        self.drv.delete_all_cookies()
        self.drv.get(BASE + "/signin")
        self.js("sessionStorage.clear();")
        self.drv.get(BASE + "/signin")
        self.Wait(self.drv, 15).until(lambda d: d.find_elements(By.CSS_SELECTOR, "input[type='text']"))
        self.drv.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user)
        self.drv.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(pwd or PW)
        self.drv.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
        self.Wait(self.drv, 15).until(lambda d: "/app/main" in d.current_url)
        time.sleep(3)
        self.js(HOOK)
        self.user = user

    def open(self, key, title):
        self.js(NAV + "nav.addTabItem(arguments[0], arguments[1]);", title, key)
        time.sleep(2.5)

    def vm(self, tab, name, expr, *a):
        return self.js(VM + f"const c = vm('{tab}', '{name}'); " + expr, *a)

    def click_text(self, css, text, exact=True):
        """보이는(렌더된) 요소를 글자로 찾아 화면 안으로 스크롤한 뒤 누른다 — 아래쪽에 있어 셀레니움이 '안 보임'으로 치는 버튼도 찾는다"""
        ok = self.js("const els = [...document.querySelectorAll(arguments[0])].filter(e => e.offsetParent !== null && "
                     "(arguments[2] ? e.innerText.trim() === arguments[1] : e.innerText.includes(arguments[1]))); "
                     "if (!els.length) return false; els[0].scrollIntoView({block: 'center'}); els[0].click(); return true;", css, text, exact)
        assert ok, f"'{text}' 없음 ({css})"
        time.sleep(1)

    def swal(self, has="", t=10):
        """보이는 확인창의 제목\n본문"""
        def _t(d):
            for e in d.find_elements(self.By.CSS_SELECTOR, ".swal2-popup"):
                if e.is_displayed() and has in e.text:
                    parts = [x.text.strip() for x in e.find_elements(self.By.CSS_SELECTOR, ".swal2-title, .swal2-html-container")
                             if x.is_displayed() and x.text.strip()]
                    return "\n".join(parts)
            return None
        return self.Wait(self.drv, t).until(_t)

    def swal_confirm(self):
        self.js("document.querySelectorAll('.swal2-confirm').forEach(b => b.click());")
        time.sleep(1.2)

    def shot(self, nm):
        try:
            self.drv.save_screenshot(os.path.join(TMP, nm + ".png"))
        except Exception:
            pass

    def quit(self):
        self.collect_errs()
        self.drv.quit()


def u_screens(sa):
    ui = UI.get()
    By = ui.By

    def _kor_pw():
        uK = "zzb4k" + TAG
        pw = "한글비번" + secrets.token_hex(3)
        make_user(sa, uK, upw=pw)
        ui.login(uK, pw)
        ok_login = "/app/main" in ui.drv.current_url
        ui.open("myProfile", "내 정보")
        new = "새한글" + secrets.token_hex(3)
        ui.vm("tab_myProfile", "DSMyProfile", "c.oldPassword = arguments[0]; c.newPassword = arguments[1]; c.confirmPassword = arguments[1];", pw, new)
        time.sleep(0.5)
        ui.click_text("#tab_myProfile .v-btn", "비밀번호 변경", exact=False)
        t = ui.swal("비밀번호")
        ui.swal_confirm()
        _, ok = try_login(uK, new)
        print(f"  한글 비밀번호 화면 로그인 {ok_login} / 내 정보 비밀번호 변경 → {t!r} / 새 한글 비밀번호 API 로그인 {ok}")
        assert ok_login and t == "비밀번호가 변경되었습니다." and ok.get("success") is True
        FX["uK"] = uK
    step("U-1. 한글 비밀번호로 화면 로그인, 내 정보에서 한글 비밀번호로 변경 — UTF-8 Base64 ($encodePw) (1.10-S6-b·11.7-3c)", _kor_pw)

    def _profile_admin():
        # 내 정보 탭을 연 뒤 관리자가 그 사용자를 관리자로 지정 → 그 사용자가 [저장] 해도 관리자 지정이 풀리지 않는다 (리뷰 후속)
        uB, nmB = FX["uB"], "110 4묶음 시험 " + FX["uB"]
        ui.login(uB, FX["pwB"])
        ui.open("myProfile", "내 정보")
        before = ui.vm("tab_myProfile", "DSMyProfile", "return c.isAdmin;")
        try:
            r = post(sa, "/api/login/updateUser", {"id": uB, "name": nmB, "email": "", "admin": True})
            ui.vm("tab_myProfile", "DSMyProfile", "c.userPhone = '010-1212-3434';")
            time.sleep(0.3)
            ui.click_text("#tab_myProfile .v-btn", "저장", exact=False)
            t = ui.swal("저장")
            time.sleep(1.6)
            row = urow(uB, "adm_yn::text||'|'||coalesce(phone,'-')")
            chip = ui.js("return [...document.querySelectorAll('#tab_myProfile .v-chip')].map(e => e.innerText.trim()).join(',');")
            ui.shot("u13_profile_admin")
        finally:
            post(sa, "/api/login/updateUser", {"id": uB, "name": nmB, "email": "", "phone": "", "admin": False})
        print(f"  탭을 열 때 관리자 {before} → 관리자 지정({code(r)}) → 전화번호 고쳐 [저장] → {t!r} / 관리자|전화 = {row} / 표시 {chip}")
        assert before is False and code(r) == 200 and t == "저장되었습니다."
        assert row == "true|010-1212-3434" and chip == "관리자"
    step("U-13. 내 정보 — 탭을 연 뒤 관리자로 지정된 사용자가 [저장] 해도 관리자 지정이 그대로 (저장 직전에 다시 읽음) (11.7-14 리뷰 후속)", _profile_admin)

    def _paging():
        n = int(psql("select count(*) from quality.tb_user where user_id <> 'space' and (del_yn = false or del_yn is null)"))
        need = 16 - n
        assert need >= 1, f"사용자가 이미 {n}명 — 마지막 페이지 시험을 할 수 없다"
        for i in range(need):
            long_nm = "ZZB4아주긴이름의사용자" + "가나다라마바사아자차카타파하" * 2 if i == 0 else None
            make_user(sa, f"zzb4z{TAG}{i:02d}", name=long_nm[:50] if long_nm else None)
        ui.login("space")
        ui.open("user", "사용자")
        st = lambda: ui.vm("tab_user", "MMUser", "return [c.page, c.pageCount, c.userItems.length, "
                           "document.querySelectorAll('#tab_user tbody tr').length, "
                           "!!document.querySelector('#tab_user .v-pagination') && document.querySelector('#tab_user .v-pagination').offsetParent !== null];")
        s1 = st()
        ui.shot("u2_user_page1")
        ui.click_text("#tab_user .v-pagination__item", "2")
        s2 = st()
        last = ui.vm("tab_user", "MMUser", "return c.userItems[c.userItems.length - 1].id;")
        ui.js("[...document.querySelectorAll('#tab_user tbody tr')].find(r => r.innerText.includes(arguments[0])).click();", last)
        time.sleep(0.8)
        ui.click_text("#tab_user .v-btn", "삭제")
        ui.swal("삭제합니까")
        ui.swal_confirm()
        time.sleep(2)
        s3 = st()
        print(f"  (페이지, 페이지 수, 사용자 수, 표 행, 페이지 버튼 보임) 처음 {s1} → 2쪽 {s2} → 2쪽 유일한 {last} 삭제 뒤 {s3}")
        assert s1 == [1, 2, 16, 15, True] and s2 == [2, 2, 16, 1, True] and last.startswith("zzb4z") and s3 == [1, 1, 15, 15, False]
    step("U-2. 사용자 목록 15명씩 페이지 — 2쪽 유일한 사용자를 지우면 1쪽으로, 페이지 버튼 숨김 (11.7-9)", _paging)

    def _add_phone():
        uU = "zzb4u" + TAG
        ui.click_text("#tab_user .v-btn", "사용자 추가")
        labels = ui.js("return [...document.querySelectorAll('.v-dialog--active label')].map(l => l.innerText.trim());")
        ui.vm("tab_user", "MMUser", "Object.assign(c.addForm, {id: arguments[0], name: 'ZZB4화면추가', email: '', phone: '010-7777-8888', "
              "password: 'Zz12345678', confirmPassword: 'Zz12345678'});", uU)
        time.sleep(0.5)
        ui.shot("u3_add_dialog")
        ui.click_text(".v-dialog--active .v-btn", "등록")
        t = ui.swal("추가")
        ui.swal_confirm()
        row = urow(uU, "nm||'|'||coalesce(phone,'-')")
        print(f"  추가 창 칸 {labels} / 등록 → {t!r} / 행 {row}")
        assert labels[:4] == ["계정 ID", "이름", "이메일", "전화번호"] and row == "ZZB4화면추가|010-7777-8888"
    step("U-3. 사용자 추가 창에 '전화번호' — 저장됨 (11.7-12a)", _add_phone)

    def _del_fail():
        ui.login(FX["uM"], FX["pwM"])
        ui.open("user", "사용자")
        ui.js("[...document.querySelectorAll('#tab_user tbody tr')].find(r => r.innerText.includes(arguments[0])).click();", FX["uAdm"])
        time.sleep(0.8)
        ui.click_text("#tab_user .v-btn", "삭제")
        ui.swal("삭제합니까")
        ui.swal_confirm()
        t1 = ui.swal("삭제 실패")
        ui.shot("u4_delete_admin_denied")
        ui.swal_confirm()
        victim = "zzb4z" + TAG + "01"
        ui.vm("tab_user", "MMUser", "c.getUserData();")
        time.sleep(1.5)
        ui.js("[...document.querySelectorAll('#tab_user tbody tr')].find(r => r.innerText.includes(arguments[0])).click();", victim)
        time.sleep(0.8)
        psql(f"delete from quality.tb_user where user_id='{victim}'")
        ui.click_text("#tab_user .v-btn", "삭제")
        ui.swal("삭제합니까")
        ui.swal_confirm()
        t2 = ui.swal("삭제 실패")
        ui.swal_confirm()
        print(f"  관리자 계정 삭제 → {t1!r} / 이미 없는 사용자 삭제 → {t2!r}")
        assert t1 == "삭제 실패\n관리자 계정은 관리자만 삭제할 수 있습니다." and t2 == "삭제 실패"
    step("U-4. 사용자 삭제 실패를 알림 — 서버 거부 사유 표시, 지우지 못하면(false) '삭제 실패' (11.7-11b)", _del_fail)

    def _expired_screen():
        uY = "zzb4y" + TAG
        pw = make_user(sa, uY)
        ui.login(uY, pw)
        sa.post(BASE + "/api/login/removeUser", data={"id": uY}, timeout=10)
        ui.open("boardQna", "Q&A")
        t = ui.swal("로그인")
        ui.swal_confirm()
        time.sleep(2)
        url = ui.drv.current_url
        ui.user = None
        print(f"  삭제된 사용자 화면 → {t!r} → {url}")
        assert t == "로그인이 필요합니다." and url.endswith("/signin")
    step("U-5. 로그인해 있던 사용자를 지우면 그 화면의 다음 요청에 '로그인이 필요합니다.' → 로그인 화면 (11.7-14)", _expired_screen)

    def _datasource():
        ui.login("space")
        ui.open("datasource", "데이터 소스")
        host = ui.js("return [...document.querySelectorAll('#tab_datasource tbody tr')].filter(r => r.innerText.includes('Cubrid')).map(r => r.innerText.replace(/\\s+/g, ' '));")
        ui.click_text("#tab_datasource .v-btn", "등록")
        items = ui.vm("tab_datasource", "MMDatasource", "return c.datasourceDbmsTpItems;")
        val = ui.vm("tab_datasource", "MMDatasource", "return arguments[0].map(a => [a, c.checkSvrAddr(a)]);",
                    ["dataq-db", "localhost", "192.168.0.10", "db.example.com", "999.1.1.1", "a b", "", "1.2.3", "-abc", "a..b", "01.2.3.4"])
        inp = [e for e in ui.drv.find_elements(By.CSS_SELECTOR, ".v-dialog--active input[placeholder='예: 192.168.0.10, db-server']") if e.is_displayed()]
        inp[0].send_keys("999.1.1.1")
        time.sleep(0.6)
        err = ui.js("return [...document.querySelectorAll('.v-dialog--active .v-messages__message')].map(m => m.innerText).filter(t => t);")
        ui.shot("u6_ds_add_bad_addr")
        ui.vm("tab_datasource", "MMDatasource", "Object.assign(c, {addDatasource_dsn: 'ZZB4화면_' + arguments[0], addDatasource_dsTp: '0', addDatasource_dbmsTp: 'PostgreSQL', "
              "addDatasource_driverName: 'PostgreSQL', addDatasource_port: 25433, addDatasource_userId: 'x', "
              "addDatasource_pwd: 'x', addDatasource_dbName: 'postgres'});", TAG)
        ui.click_text(".v-dialog--active .v-btn", "연결 테스트")
        t0 = ui.swal("유효하지")
        ui.swal_confirm()
        ui.vm("tab_datasource", "MMDatasource", "c.addDatasource_svrAddr = 'localhost';")
        time.sleep(0.5)
        err2 = ui.js("return [...document.querySelectorAll('.v-dialog--active .v-messages__message')].map(m => m.innerText).filter(t => t);")
        ui.drv.execute_cdp_cmd("Network.enable", {})
        ui.drv.execute_cdp_cmd("Network.setBlockedURLs", {"urls": ["*testDataSource*"]})
        try:
            ui.click_text(".v-dialog--active .v-btn", "연결 테스트")
            t = ui.swal("연결 테스트")
            prog = ui.vm("tab_datasource", "MMDatasource", "return c.progress;")
            ui.shot("u6_ds_test_fail")
            ui.swal_confirm()
        finally:
            ui.drv.execute_cdp_cmd("Network.setBlockedURLs", {"urls": []})
        overlay = ui.js("return [...document.querySelectorAll('.v-dialog--active')].some(d => d.innerText.includes('연결 테스트 진행 중'));")
        ui.click_text(".v-dialog--active .v-btn", "취소")
        print(f"  Cubrid 행 {host}\n  DB 종류 {items}\n  주소 검사 {val}\n  999.1.1.1 안내 {err} / 연결 테스트 → {t0!r} / localhost 안내 {err2}\n"
              f"  연결 테스트 요청 실패 → {t!r} / progress={prog} / 진행 창 {overlay}")
        assert host and " HOST " in f" {host[0]} " and "Custom" not in items and "Tibero" in items
        assert [v[1] for v in val] == [True, True, True, True, False, False, False, False, False, False, False]
        assert "IPv4 주소 또는 호스트명을 입력해주세요." in err and t0 == "서버 주소 값이 유효하지 않습니다." and "IPv4 주소 또는 호스트명을 입력해주세요." not in err2
        assert t.startswith("연결 테스트 실패") and prog is False and not overlay
    step("U-6. 데이터 소스 — 호스트 유형 'HOST' 표시, DB 종류에 Custom 없음, 서버주소는 IPv4·호스트명 입력·검사, 연결 테스트 요청 실패 때 진행 창 닫힘 (11.7-15·16·17·18)", _datasource)

    def _system():
        ui.open("system", "시스템")
        ui.vm("tab_system", "MMSystem", "c.selectedItem = [{sysCd: ''}];")
        time.sleep(0.5)
        btns = ui.js("return [...document.querySelectorAll('#tab_system .v-btn')].filter(b => b.offsetParent !== null).map(b => b.innerText.trim());")
        menu = ui.js("return !!document.querySelector('#sysStndReqMenu');")
        ui.click_text("#tab_system .v-btn", "수정")
        t1 = ui.swal("선택")
        ui.swal_confirm()
        ui.click_text("#tab_system .v-btn", "삭제")
        t2 = ui.swal("선택")
        ui.swal_confirm()
        ui.shot("u7_system_no_select")
        print(f"  선택 없음 — 버튼 {btns} / 표준 준수 필수 {menu} / 수정 → {t1!r} / 삭제 → {t2!r}")
        assert {"등록", "수정", "삭제"} <= set(btns) and not menu
        assert t1 == "변경할 시스템을 선택해주세요." and t2 == "삭제할 시스템을 선택해주세요."
    step("U-7. 시스템 — 선택 없이도 등록·수정·삭제 버튼, 수정·삭제는 선택 안내, '표준 준수 필수' 는 선택 뒤에만 (11.7-20)", _system)

    def _nav():
        cases = [("scheduleManage", "스케줄 관리", "scheduleGroup", "navScheduleGroup"), ("scheduleLog", "실행 이력", "scheduleGroup", "navScheduleGroup"),
                 ("termResolveHistory", "한글 변환 이력", "autoStdGroup", "navAutoStdGroup"), ("dmHistory", "데이터 모델 변경 이력", "dmGroup", "navDmGroup"),
                 ("diagTargetMgmt", "진단 제외 관리", "dmGroup", "navDmGroup"), ("datamodelStatusIndex", "인덱스", "dmGroup", "navDmGroup"),
                 ("myDmChanges", "내 변경 신청", "dmGroup", "navDmGroup"), ("dmApproval", "데이터 모델 변경 승인", "mmGroup", "navMmGroup"),
                 ("areaMgmt", "영역 관리", "mmGroup", "navMmGroup"), ("stdDict", "표준사전 관리", "mmGroup", "navMmGroup")]
        res = []
        for key, title, grp, flag in cases:
            # 탭 열기 → changeNavItem 이 그룹 헤더를 강조. 다른 그룹을 펼친 채 navAndTabSync → 이 탭의 그룹을 펼침.
            # 메뉴 항목은 그룹을 처음 펼칠 때 그려지므로(지연 렌더) 펼친 뒤 changeNavItem 으로 항목 강조를 본다 (기존 탭도 같다)
            ui.open(key, title)
            head = ui.js("const g = document.getElementById(arguments[0]); return !!(g && g.childNodes[0].classList.contains('v-list-item--active'));", grp)
            sync = ui.js(LAYOUT + "lay.navAllGroupClose(); lay.navDsGroup = true; lay.navAndTabSync(arguments[0]); return !!lay[arguments[1]];", key, flag)
            time.sleep(0.8)
            item = ui.js(LAYOUT + "lay.changeNavItem(arguments[0]); const el = document.getElementById(lay._navIdOf(arguments[0])); "
                         "return !!(el && el.classList.contains('v-list-item--active'));", key)
            res.append((key, head, item, sync))
        for r in res:
            print(f"  {r}")
        assert all(r[1] and r[2] and r[3] for r in res)
    step("U-8. 탭을 열거나 고르면 메뉴 그룹 헤더·항목 강조와 그룹 펼침이 맞음 — 스케줄·변환 이력·모델·관리 탭 (1.10-B12)", _nav)

    def _reopen():
        res = []
        for key, title, tab in (("dmApproval", "데이터 모델 변경 승인", "tab_dm_approval"), ("stdDict", "표준사전 관리", "tab_std_dict"),
                                ("termResolveHistory", "한글 변환 이력", "tab_term_resolve_history"), ("areaMgmt", "영역 관리", "tab_area_mgmt"),
                                ("myDmChanges", "내 변경 신청", "tab_my_dm_changes"), ("dmHistory", "데이터 모델 변경 이력", "tab_dm_history")):
            ui.open(key, title)
            k1 = ui.js(CONTENT + "return cnt[arguments[0] + 'Key'];", key)
            # 탭 화면 컴포넌트 = vnode key 가 NdContent 의 xxxKey 인 인스턴스
            FIND = (CONTENT + "const K = arguments[0]; const v = (function down(x) { if (x.$vnode && x.$vnode.key === K) return x; "
                    "for (const ch of x.$children) { const r = down(ch); if (r) return r; } return null; })(cnt) "
                    f"|| null; const shown = !!document.querySelector('#{tab}');")
            assert ui.js(FIND + "if (v) v.__zzb4 = 1; return !!v && shown;", k1), (key, k1)
            idx = ui.js(CONTENT + "return cnt.tabs.findIndex(t => t.name === arguments[0]);", key)
            ui.js(CONTENT + "const t = cnt.tabs[arguments[0]]; cnt.removeTabItem(t.index, t.title, t.name);", idx)
            time.sleep(1.2)
            ui.open(key, title)
            k2 = ui.js(CONTENT + "return cnt[arguments[0] + 'Key'];", key)
            fresh = ui.js(FIND + "return !!v && shown && v.__zzb4 !== 1;", k2)
            res.append((key, k1 != k2, fresh))
        print(f"  (탭, 키 바뀜, 새 인스턴스) {res}")
        assert all(r[1] and r[2] for r in res)
    step("U-9. 탭을 닫았다 다시 열면 화면을 새로 만든다 — 모델 승인·사전·변환 이력·영역·내 변경 신청·변경 이력 (1.10-B13)", _reopen)

    def _board_files():
        FX.setdefault("sA", login_api(FX["uA"], FX["pwA"]))
        ui.login(FX["uA"], FX["pwA"])
        ui.open("boardQna", "Q&A")
        t = "ZZB4화면첨부_" + TAG + " 아주 긴 제목 " + "가나다라마바사" * 6
        board_create(FX["sA"], "QNA", t, files=[("화면첨부1.txt", b"a"), ("화면첨부2.txt", b"b")])
        bid = board_id(t)
        ui.vm("tab_boardQna", "DSBoard", "c.loadList();")
        time.sleep(1.5)
        ui.vm("tab_boardQna", "DSBoard", "c.viewPost({boardId: arguments[0]});", bid)
        time.sleep(1.5)
        ui.shot("u10_board_view_long")
        ui.click_text("#tab_boardQna .v-btn", "수정")
        acc = ui.js("const i = document.querySelector('#tab_boardQna input[type=file]'); return i && i.getAttribute('accept');")
        hint = ui.js("return [...document.querySelectorAll('#tab_boardQna .v-messages__message')].map(m => m.innerText);")
        fid = board_files(bid)[0][0]
        ui.vm("tab_boardQna", "DSBoard", "c.removeExistingFile(arguments[0]);", fid)
        time.sleep(0.8)
        mid = len(board_files(bid))
        ui.click_text("#tab_boardQna .v-btn", "취소")
        ui.swal("작성을 취소")
        ui.swal_confirm()
        after_cancel = len(board_files(bid))
        ui.vm("tab_boardQna", "DSBoard", "c.viewPost({boardId: arguments[0]});", bid)
        time.sleep(1.2)
        ui.click_text("#tab_boardQna .v-btn", "수정")
        ui.vm("tab_boardQna", "DSBoard", "c.removeExistingFile(arguments[0]);", fid)
        ui.shot("u10_board_edit_files")
        ui.vm("tab_boardQna", "DSBoard", "c.submitPost();")
        time.sleep(2)
        sw = None
        try:
            sw = ui.swal("", 3)
            ui.swal_confirm()
        except Exception:
            pass
        left = [f[1] for f in board_files(bid)]
        print(f"  accept={acc}\n  안내 {hint} / X 직후 첨부 {mid} / 취소 뒤 {after_cancel} / 저장 뒤 {left} (알림 {sw!r})")
        assert acc == ".pdf,.hwp,.hwpx,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg,.gif,.zip"
        assert "글당 10개까지 (문서·이미지·zip)" in hint and mid == 2 and after_cancel == 2 and left == ["화면첨부2.txt"]
        FX["bUi"] = bid
    step("U-10. 게시판 수정 — 첨부 형식 제한(accept)·안내, X 는 서버를 부르지 않고 저장 때 반영, 취소하면 그대로 (10.3-4·6)", _board_files)

    def _tamper():
        bid = FX["bUi"]
        post(FX["sA"], f"/api/board/{bid}/comment", {"content": "ZZB4화면댓글"})
        ui.login(FX["uB"], FX["pwB"])
        ui.js("const s = JSON.parse(sessionStorage.getItem('loginStatus')); s.id = arguments[0]; sessionStorage.setItem('loginStatus', JSON.stringify(s));", FX["uA"])
        ui.drv.refresh()
        ui.Wait(ui.drv, 15).until(lambda d: d.find_elements(By.CSS_SELECTOR, "#nav_dashboard"))
        time.sleep(2.5)
        ui.js(HOOK)
        ui.open("boardQna", "Q&A")
        ui.vm("tab_boardQna", "DSBoard", "c.viewPost({boardId: arguments[0]});", bid)
        time.sleep(1.5)
        cur = ui.vm("tab_boardQna", "DSBoard", "return c.currentUserId;")
        ui.click_text("#tab_boardQna .v-btn", "삭제")
        ui.swal("삭제하시겠습니까")
        ui.swal_confirm()
        t1 = ui.swal("실패")
        ui.shot("u11_board_delete_denied")
        ui.swal_confirm()
        cid = psql(f"select comment_id from quality.tb_board_comment where board_id='{bid}'")
        ui.vm("tab_boardQna", "DSBoard", "c.deleteComment(arguments[0]);", cid)
        ui.swal("댓글을 삭제")
        ui.swal_confirm()
        t2 = ui.swal("실패")
        ui.swal_confirm()
        ui.vm("tab_boardQna", "DSBoard", "c.editCommentContent = '남이 고침'; c.saveCommentEdit(arguments[0]);", cid)
        t3 = ui.swal("실패")
        ui.swal_confirm()
        alive = psql(f"select (select count(*) from quality.tb_board where board_id='{bid}')||'/'||(select content from quality.tb_board_comment where comment_id='{cid}')")
        print(f"  화면 작성자 판단 {cur} → 글 삭제 {t1!r} / 댓글 삭제 {t2!r} / 댓글 수정 {t3!r} / 글·댓글 {alive}")
        NOT_OWNER = "작성자나 공지 관리 권한이 있는 사용자만 수정·삭제할 수 있습니다."
        assert cur == FX["uA"]
        assert t1 == f"삭제 실패\n{NOT_OWNER}" and t2 == f"댓글 삭제 실패\n{NOT_OWNER}" and t3 == f"수정 실패\n{NOT_OWNER}"
        assert alive == "1/ZZB4화면댓글"
        ui.user = None
    step("U-11. 화면의 작성자 판단(sessionStorage)을 바꿔도 서버가 403 — 글·댓글 삭제·수정 실패 사유를 오류창으로 (10.3-5·8)", _tamper)


# ---------------------------------------------------------------- 정리
def purge():
    """이름으로 남은 시험 데이터를 지운다 (--purge, 정리 실패 대비)"""
    for dm in psql("select dm_id from quality.tb_data_model where dm_nm like 'ZZB4%'").split():
        drop_model(dm)
    for p in psql("select file_path from quality.tb_board_file where board_id in (select board_id from quality.tb_board where title like 'ZZB4%' "
                  "or board_id like 'ZZB4%' or cret_user_id like 'zzb4%')").splitlines():
        try:
            os.remove(p)
        except OSError:
            pass
    psql("""delete from quality.tb_board_file where board_id in (select board_id from quality.tb_board where title like 'ZZB4%' or board_id like 'ZZB4%' or cret_user_id like 'zzb4%');
            delete from quality.tb_board_comment where board_id in (select board_id from quality.tb_board where title like 'ZZB4%' or board_id like 'ZZB4%' or cret_user_id like 'zzb4%');
            delete from quality.tb_board where title like 'ZZB4%' or board_id like 'ZZB4%' or cret_user_id like 'zzb4%';
            delete from quality.tb_diag_schedule_log where schedule_id in (select schedule_id from quality.tb_diag_schedule where schedule_nm like 'ZZB4%');
            delete from quality.tb_diag_schedule where schedule_nm like 'ZZB4%';
            delete from quality.tb_gov_meta_db where ds_id in (select ds_id from ndata.tb_data_source where dsn like 'ZZB4%');
            delete from quality.tb_sys_info where sys_nm like 'ZZB4%';
            delete from ndata.tb_data_source where dsn like 'ZZB4%';
            delete from quality.tb_org where org_id like 'ZZB4%';
            delete from quality.tb_user_role where user_id like 'zzb4%';
            delete from quality.tb_user_role where role_id in (select role_id from quality.tb_role where role_nm like 'ZZB4%');
            delete from quality.tb_role_perm where role_id in (select role_id from quality.tb_role where role_nm like 'ZZB4%');
            delete from quality.tb_role where role_nm like 'ZZB4%';
            delete from quality.tb_user where user_id like 'zzb4%';
            delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id like 'zzb4%';""")
    for s in psql("select nspname from pg_namespace where nspname like 'zzb4%'").split():
        psql(f"drop schema if exists {s} cascade")
    for r in psql("select rolname from pg_roles where rolname like 'zzb4%'").split():
        psql(f"drop role if exists {r}")


def leftovers():
    return psql("select (select count(*) from quality.tb_user where user_id like 'zzb4%')||'/'||"
                "(select count(*) from quality.tb_role where role_nm like 'ZZB4%')||'/'||"
                "(select count(*) from quality.tb_board where title like 'ZZB4%' or board_id like 'ZZB4%')||'/'||"
                "(select count(*) from quality.tb_board_file f where not exists (select 1 from quality.tb_board b where b.board_id=f.board_id))||'/'||"
                "(select count(*) from quality.tb_data_model where dm_nm like 'ZZB4%')||'/'||"
                "(select count(*) from ndata.tb_data_source where dsn like 'ZZB4%')||'/'||"
                "(select count(*) from quality.tb_sys_info where sys_nm like 'ZZB4%')||'/'||"
                "(select count(*) from quality.tb_event_log where job_ex_user_id like 'zzb4%')||'/'||"
                "(select count(*) from pg_namespace where nspname like 'zzb4%')||'/'||"
                "(select count(*) from pg_roles where rolname like 'zzb4%')||'/'||"
                "(select coalesce(del_yn::text,'null') from quality.tb_user where user_id='space')")


def main(only):
    if "--purge" in sys.argv:
        purge()
        print("남은 시험 사용자/역할/게시글/고아 첨부/모델/데이터 소스/시스템/로그인 로그/스키마/역할, space 삭제 표시 =", leftovers())
        return
    sa = login_api("space")
    groups = [("A", a_auth), ("P", p_user), ("B", b_board), ("D", d_ds), ("M", m_misc), ("U", u_screens)]
    try:
        step("S0. 시험 데이터 준비 (일반·사용자 관리·공지 관리·관리자·비밀번호 시험 사용자)", lambda: setup(sa))
        for nm, fn in groups:
            if only and nm not in only:
                continue
            fn(sa)
    finally:
        if psql("select coalesce(del_yn, false)::text from quality.tb_user where user_id='space'") != "false":
            psql("update quality.tb_user set del_yn=false where user_id='space'")
            print("  !! space 삭제 표시가 남아 있어 되돌렸다")
        if UI.inst:
            UI.inst.quit()
            errs = [e for e in UI.inst.errs if e]
            print(f"\n=== 화면 콘솔 오류 {len(errs)}건")
            for e in errs[:20]:
                print("  ", e[:200])
            results.append(("U-12. 화면 콘솔 오류 없음", "PASS" if not [e for e in errs if re.search(r"TypeError|ReferenceError|Vue warn|rejection", e)] else "FAIL"))
        if KEEP:
            print("\n=== 정리 건너뜀 (--keep) — 끝나면 python verify_110_batch4.py --purge")
        else:
            print("\n=== 정리")
            for fn in reversed(cleanup):
                try:
                    fn()
                except Exception as e:
                    print("  정리 실패:", e)
            purge()
            if "T0" in FX:
                # 시험이 남긴 내장 관리자 로그인 로그 (로그인 성공·내장 관리자 없음)
                psql(f"delete from quality.tb_event_log where job_id='authentication' and job_ex_user_id='space' and time_val >= '{FX['T0']}'::timestamptz")
            print(f"  남은 시험 사용자/역할/게시글/고아 첨부/모델/데이터 소스/시스템/로그인 로그/스키마/역할, space 삭제 표시 = {leftovers()}")
        print(f"  화면 캡처: {TMP}")


if __name__ == "__main__":
    t0 = time.time()
    main({a for a in sys.argv[1:] if not a.startswith("--")})
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
