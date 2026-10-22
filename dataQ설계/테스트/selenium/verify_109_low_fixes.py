# -*- coding: utf-8 -*-
"""
109번 낮음 결함 15~18·20·22~24 조치 검증 — API (20번 HTML 이스케이프는 화면에서 따로 확인)

  L15. /api/aprv/openDocs — 요청자·결재자·관리자·그 유형의 승인 권한자인 문서만 (#15)
  L16. /api/org/users — 사용자·권한 관리 권한(USER_ADMIN)만 (#16)
  L17. [DB 반영] — 같은 건 동시 실행은 한 번만, 중간 실패 뒤 다시 실행하면 반영된 문장은 건너뜀, 기록과 다르면 거부 (#17)
  L18. 참조 사전 판정 — 표준 매핑 수정·통합 코드 구성 저장은 저장된 항목의 사전으로 (#18)
  L22. 코드 유형·상위 코드 변경 때 기존 값 재검사, 코드 그룹 이름 변경 때 옛 이름에 남는 것이 있으면 거부 (#22)
  L23. 구조 진단 반영 — 최근 진단만, 지금 모델 기준 판정(반려되면 다시 반영, 진단 뒤 모델이 바뀌면 거부) (#23)
  L24. 상위 사전 항목의 미사용·영향도가 하위 사전 모델도 본다 — 화면 API 와 q-executor 예약 실행(미사용 표준) (#24)

필요:
  - q-center(28091)·q-executor(28098) 기동
  - 환경 변수 PGPASSWORD (개발 DB admin), DATAQ_TEST_PW (시험 계정 space·jyjang 비밀번호), 선택 PSQL (psql.exe 경로)
  - 인자로 돌릴 묶음을 고를 수 있다 (예: python verify_109_low_fixes.py L15 L17). 없으면 전부
시험 데이터(결재선·단어·용어·코드·사전·매핑·결재 문서·알림·역할·시험 사용자·부서·모델·진단 결과·예약·시험 스키마 등)는 끝에서 지운다.
"""
import base64, json, os, secrets, subprocess, sys, threading, time, traceback
import requests

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
PSQL = os.environ.get("PSQL", r"C:\Program Files\PostgreSQL\13\bin\psql.exe")
PW = os.environ["DATAQ_TEST_PW"]
results = []
cleanup = []          # 끝에서 거꾸로 실행할 정리 함수


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
    r = subprocess.run([PSQL, "-h", "localhost", "-p", "25433", "-U", "admin", "-d", "postgres", "-At", "-c", sql],
                       capture_output=True, text=True, timeout=60, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError("psql 실패: " + (r.stderr or "")[:300])
    return (r.stdout or "").strip()


def login_api(user, pwd=None):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": base64.b64encode((pwd or PW).encode()).decode()},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {user} {r.status_code}"
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


def contents(r):
    c = r.get("contents")
    return json.loads(c) if isinstance(c, str) and c else (c or {})


def code(r):
    return r.get("resultCode")


def q(v):
    return "'" + str(v).replace("'", "''") + "'"


# ---------------------------------------------------------------- 공통 준비
def aprv_line(sa, req_tp, steps):
    r = post(sa, "/api/aprv/line/save", {"reqTp": req_tp, "lineNm": "109 시험", "useYn": "Y", "steps": steps})
    assert code(r) == 200, r

    def _off():
        post(sa, "/api/aprv/line/save", {"reqTp": req_tp, "useYn": "N", "steps": []})
        psql(f"delete from quality.tb_aprv_line_step where req_tp='{req_tp}'")
        psql(f"delete from quality.tb_aprv_line where req_tp='{req_tp}'")
    cleanup.append(_off)


def line_off(sa, req_tp):
    post(sa, "/api/aprv/line/save", {"reqTp": req_tp, "useYn": "N", "steps": []})


def make_user(sa, uid):
    upw = secrets.token_hex(6)
    r = post(sa, "/api/login/createUser", {"id": uid, "password": base64.b64encode(upw.encode()).decode(),
                                           "name": "109 시험 " + uid, "email": "", "admin": False})
    assert code(r) == 200, r
    cleanup.append(lambda: psql(f"delete from quality.tb_user where user_id='{uid}'"))
    return upw


def make_role(sa, nm, perms):
    r = post(sa, "/api/role/save", {"roleNm": nm, "roleDesc": "109 시험", "perms": perms})
    assert code(r) == 200, r
    rid = r.get("contents")

    def _drop():
        psql(f"delete from quality.tb_user_role where role_id='{rid}'")
        psql(f"delete from quality.tb_role_perm where role_id='{rid}'")
        psql(f"delete from quality.tb_role where role_id='{rid}'")
    cleanup.append(_drop)
    return rid


def assign(sa, uid, rid, sys_cd="*"):
    r = post(sa, "/api/role/assign", {"userId": uid, "roleId": rid, "sysCd": sys_cd})
    assert code(r) == 200, r


MYUNG = {}


def myung():
    """DEFAULT 사전의 승인된 형식단어 '명'"""
    if not MYUNG:
        v = psql("select word_id from quality.tb_word where dict_id='DEFAULT' and word_nm='명' and aprv_yn='Y' and word_clsf_yn='Y'")
        assert v, "DEFAULT 사전에 형식단어 '명' 이 없다"
        MYUNG["id"] = v
    return MYUNG["id"]


def word(s, nm, abrv, eng):
    r = post(s, "/api/std/createWord", {"wordNm": nm, "wordEngAbrvNm": abrv, "wordEngNm": eng,
                                        "wordDesc": "109 시험", "wordClsfYn": "N", "dictId": "DEFAULT"})
    wid = psql(f"select word_id from quality.tb_word where word_nm={q(nm)} and dict_id='DEFAULT'")
    assert wid, f"단어 등록 실패: {r}"

    def _drop():
        psql(f"delete from quality.tb_change_history where target_id='{wid}'")
        psql(f"delete from quality.tb_aprv_stats where req_item_id='{wid}'")
        psql(f"delete from quality.tb_word where word_id='{wid}'")
    cleanup.append(_drop)
    return wid


def make_model(s, nm, ds=None, dict_id="DEFAULT"):
    r = post(s, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds, "modelType": "PHYSICAL", "ver": "1",
                                            "dictId": dict_id, "stndReqYn": "N"})
    dm = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
    assert code(r) == 200 and dm, r
    cleanup.append(lambda: drop_model(dm))
    return dm


def drop_model(dm):
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_constraint", "tb_data_model_index",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_udp_val", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id='{dm}'")
    psql(f"delete from quality.tb_data_model where dm_id='{dm}'")


def pg_schema(prefix):
    """개발 DB 에 시험 스키마·로그인 역할을 만든다 (이름은 소문자)"""
    nm = (prefix + TAG).lower()
    pw = "Zz" + secrets.token_hex(6)
    psql(f"create role {nm} login password '{pw}'")
    psql(f"create schema {nm} authorization {nm}")

    def _drop():
        psql(f"drop schema if exists {nm} cascade")
        psql(f"drop role if exists {nm}")
    cleanup.append(_drop)
    return nm, pw


def make_ds(sa, dsn, user, pw):
    r = post(sa, "/api/sysinfo/createDataSource", {"dsn": dsn, "dsTp": 0, "dbmsTp": "PostgreSQL", "driverName": "PostgreSQL",
                                                   "svrAddr": "127.0.0.1", "port": 25433, "userId": user, "pwd": pw, "dbName": "postgres"})
    assert code(r) == 200, r
    ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
    cleanup.append(lambda: psql(f"delete from ndata.tb_data_source where ds_id='{ds}'"))
    return ds


def seq_of(dm, change_type, obj=None, attr=None, status=None):
    w = f"dm_id='{dm}' and change_type='{change_type}'"
    if obj:
        w += f" and obj_nm='{obj}'"
    if attr:
        w += f" and attr_nm='{attr}'"
    if status:
        w += f" and aprv_status='{status}'"
    v = psql(f"select max(change_seq) from quality.tb_data_model_change_history where {w}")
    assert v, f"이력 없음 {change_type} {obj} {attr}"
    return int(v)


def save_attrs(s, dm, owner, obj, attrs):
    return post(s, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": obj, "objOwner": owner, "attrs": attrs})


def dict_with_domain(did, nm, dict_tp="ORG", parent=None, old_std="N"):
    """시험 사전과 그 사전의 도메인 한 벌 (용어는 같은 사전 도메인을 가리켜야 한다 — FK). 도메인 이름을 돌려준다"""
    psql(f"insert into quality.tb_std_dict (dict_id, dict_nm, dict_tp, use_yn, parent_dict_id, old_std_yn) "
         f"values ('{did}', {q(nm)}, '{dict_tp}', 'Y', {q(parent) if parent else 'null'}, '{old_std}')")
    g, c, d = "ZZ109그룹" + TAG, "ZZ109분류" + TAG, "ZZ109도메인" + TAG
    psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('{did}G', '{g}', 'N', '{did}')")
    psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) values ('{did}C', '{c}', '{g}', 'N', '{did}')")
    psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, dict_id, aprv_yn) "
         f"values ('{did}D', '{d}', '{g}', '{c}', '109 시험', 'VARCHAR', '{did}', 'Y')")

    def _drop():
        for t in ("tb_code_integ", "tb_code_data", "tb_std_map"):
            psql(f"delete from quality.{t} where dict_id='{did}'")
        psql(f"delete from quality.tb_terms where dict_id='{did}'")
        psql(f"delete from quality.tb_word where dict_id='{did}'")
        psql(f"delete from quality.tb_domain_grp where dict_id='{did}'")     # 분류·도메인은 CASCADE
        psql(f"update quality.tb_std_dict set parent_dict_id=null where parent_dict_id='{did}'")
        psql(f"delete from quality.tb_std_dict where dict_id='{did}'")
    cleanup.append(_drop)
    return d


def raw_word(wid, did, nm, abrv):
    psql(f"insert into quality.tb_word (word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc, word_clsf_yn, aprv_yn, dict_id) "
         f"values ('{wid}', {q(nm)}, '{abrv}', '{abrv.capitalize()}', '109 시험', 'N', 'Y', '{did}')")


def raw_code(tid, did, nm, eng, grp, domain="명V40", tp=None, parent=None):
    """SQL 로 넣는 코드(코드 그룹이 있는 용어). 지우기는 사전 정리나 codes_cleanup"""
    psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, code_grp, code_tp, parent_code_grp, aprv_yn, dict_id) "
         f"values ('{tid}', {q(nm)}, '{eng}', '109 시험 코드', {q(domain)}, '{grp}', {q(tp) if tp else 'null'}, {q(parent) if parent else 'null'}, 'Y', '{did}')")


def raw_value(cid, did, grp, val, parent_val=None):
    psql(f"insert into quality.tb_code_data (code_id, code_grp, code_nm, code_eng_nm, code_val, dict_id, parent_code_val) "
         f"values ('{cid}', '{grp}', '{grp}값', '{grp}VAL', '{val}', '{did}', {q(parent_val) if parent_val else 'null'})")


def code_body(tid, nm, eng, grp, tp, parent=None, subs=None, dict_id="DEFAULT", desc="109 시험 코드"):
    return {"id": tid, "termsNm": nm, "termsEngAbrvNm": eng, "termsDesc": desc, "domainNm": "명V40", "codeGrp": grp,
            "codeTp": tp, "parentCodeGrp": parent, "integSubGrps": subs or [],
            "wordList": [{"termsId": tid, "wordId": myung(), "wordNm": "명", "wordOrd": 0}], "dictId": dict_id}


def main(only):
    sa = login_api("space")
    su = login_api("jyjang")
    start = time.strftime("%Y%m%d%H%M%S")
    assert psql("select count(*) from quality.tb_user_role where user_id='jyjang'") == "0", "jyjang 에 이미 역할이 있다 — 시험이 권한을 바꾸므로 멈춘다"
    assert psql("select count(*) from quality.tb_aprv_line where use_yn='Y'") == "0", "켜진 결재선이 있다 — 시험이 결재선을 바꾸므로 멈춘다"

    groups = [("L15", l15), ("L16", l16), ("L17", l17), ("L18", l18), ("L22", l22), ("L23", l23), ("L24", l24)]
    try:
        for nm, fn in groups:
            if not only or nm in only:
                fn(sa, su)
    finally:
        print("\n=== 정리")
        for fn in reversed(cleanup):
            try:
                fn()
            except Exception as e:
                print("  정리 실패:", e)
        psql(f"delete from quality.tb_notification where cret_dt >= '{start}'")
        psql(f"delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where req_dt >= '{start}')")
        psql(f"delete from quality.tb_aprv_doc where req_dt >= '{start}'")
        psql(f"delete from quality.tb_change_history where change_dt >= to_timestamp('{start}', 'YYYYMMDDHH24MISS') "
             f"and (target_nm like 'ZZ%' or summary like '%ZZ%')")


# ---------------------------------------------------------------- L15
def l15(sa, su):
    def _open_docs():
        ua, ub, uc, ud = ("zz109o" + x + TAG for x in "abcd")
        pa, pb, pc, pd = (make_user(sa, u) for u in (ua, ub, uc, ud))
        assign(sa, ub, make_role(sa, "ZZ109표준승인_" + TAG, ["STD_APPROVE"]))
        aprv_line(sa, "WORD", [{"stepNm": "지정", "aprvrTp": "USER", "aprvrId": uc, "agreeYn": "N"}])
        sx = {u: login_api(u, p) for u, p in ((ua, pa), (ub, pb), (uc, pc), (ud, pd))}
        w1 = word(sx[ua], "ZZ109열람단어가" + TAG, "ZZOA" + TAG, "Zzoaword" + TAG)      # 요청자 ua, 결재자 uc
        w2 = word(su, "ZZ109열람단어나" + TAG, "ZZOB" + TAG, "Zzobword" + TAG)          # 요청자 jyjang, 결재자 uc
        assert psql(f"select count(*) from quality.tb_aprv_doc where target_id in ('{w1}','{w2}') and doc_stat='PROGRESS'") == "2"
        body = {"targetIds": [w1, w2]}
        seen = {}
        for nm, s in (("관리자", sa), ("jyjang(요청자 w2)", su), ("ua(요청자 w1)", sx[ua]), ("ub(STD_APPROVE)", sx[ub]),
                      ("uc(결재자)", sx[uc]), ("ud(관계없음)", sx[ud])):
            r = post(s, "/api/aprv/openDocs", body)
            seen[nm] = sorted("w1" if k == w1 else "w2" for k in r.keys())
        print("  ", seen)
        assert seen == {"관리자": ["w1", "w2"], "jyjang(요청자 w2)": ["w2"], "ua(요청자 w1)": ["w1"], "ub(STD_APPROVE)": ["w1", "w2"],
                        "uc(결재자)": ["w1", "w2"], "ud(관계없음)": []}
        line_off(sa, "WORD")
    step("L15-1. openDocs — 관리자·STD_APPROVE·결재자는 둘 다, 요청자는 자기 것만, 관계없는 사용자는 없음", _open_docs)


# ---------------------------------------------------------------- L16
def l16(sa, su):
    def _org_users():
        ua, ub = "zz109ga" + TAG, "zz109gb" + TAG
        make_user(sa, ua)
        pb = make_user(sa, ub)
        assign(sa, ub, make_role(sa, "ZZ109사용자관리_" + TAG, ["USER_ADMIN"]))
        r = post(sa, "/api/org/save", {"orgNm": "ZZ109부서" + TAG, "sortOrd": 0})
        assert code(r) == 200, r
        oid = r.get("contents")

        def _drop():
            psql(f"update quality.tb_user set org_id=null where org_id='{oid}'")
            psql(f"delete from quality.tb_org where org_id='{oid}'")
        cleanup.append(_drop)
        assert code(post(sa, "/api/org/assignUser", {"userId": ua, "orgId": oid})) == 200
        out = {}
        for nm, s in (("jyjang", su), ("USER_ADMIN 사용자", login_api(ub, pb)), ("관리자", sa)):
            r = s.get(BASE + "/api/org/users", params={"orgId": oid}, timeout=10)
            out[nm] = (r.status_code, [u.get("userId") for u in r.json()] if r.status_code == 200 else None)
        print("  ", out)
        assert out["jyjang"][0] == 403 and out["USER_ADMIN 사용자"] == (200, [ua]) and out["관리자"] == (200, [ua])
        # 부서 목록(트리)은 지금처럼 누구나
        assert su.get(BASE + "/api/org/list", timeout=10).status_code == 200
    step("L16-1. /api/org/users — 일반 사용자 403, USER_ADMIN·관리자 200 (부서 목록은 누구나)", _org_users)


# ---------------------------------------------------------------- L17
def l17(sa, su):
    st = {}

    def _setup():
        nm, pw = pg_schema("zz109l")
        ds = make_ds(sa, "ZZ109_L_" + TAG, nm, pw)
        st.update(nm=nm, dm=make_model(sa, "ZZ109낮음반영_" + TAG, ds))

    def _concurrent():
        _setup()
        dm, nm = st["dm"], st["nm"]
        out = []
        for i in range(3):
            t = f"ZZ_T17A{i}"
            assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": nm, "objNm": t, "objNmKr": "시험테이블"})) == 200
            assert code(save_attrs(sa, dm, nm, t, [{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10}])) == 200
            seq = seq_of(dm, "ADD_OBJ", t)
            bar = threading.Barrier(2)
            res = [None, None]

            def go(k):
                bar.wait()
                res[k] = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
            ts = [threading.Thread(target=go, args=(k,)) for k in (0, 1)]
            for x in ts:
                x.start()
            for x in ts:
                x.join()
            row = psql(f"select ddl_exec_result from quality.tb_data_model_change_history where change_seq={seq}")
            made = psql(f"select count(*) from information_schema.tables where table_schema='{nm}' and table_name='{t.lower()}'")
            msgs = [m for r in res for m in (r.get("messages") or [])]
            out.append((sum(r.get("success", 0) for r in res), sum(r.get("failed", 0) for r in res), row, made, msgs))
        for o in out:
            print("  ", o)
        assert all(o[0] == 1 and o[1] == 1 and o[2] == "SUCCESS" and o[3] == "1"
                   and any("다른 요청이 이 변경을 DB 에 반영하고 있습니다" in m or "이미 반영된 변경입니다" in m for m in o[4]) for o in out)
    step("L17-1. 같은 테이블 추가를 두 요청이 동시에 [DB 반영] 3회 — 매번 한 번만 실행, 이력 SUCCESS", _concurrent)

    def _resume():
        dm, nm = st["dm"], st["nm"]
        t = "ZZ_T17B"
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": nm, "objNm": t, "objNmKr": "시험테이블"})) == 200
        assert code(save_attrs(sa, dm, nm, t, [{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10, "nullableYn": "Y"}])) == 200
        assert contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq_of(dm, "ADD_OBJ", t)]}, timeout=60)).get("success") == 1
        # 컬럼을 쓰는 뷰가 있으면 이름 변경은 되고 타입 변경은 실패한다
        psql(f"create view {nm}.zz_v17 as select c1 from {nm}.zz_t17b")
        r = save_attrs(sa, dm, nm, t, [{"mode": "UPDATE", "origAttrNm": "C1", "attrNm": "C1R", "attrNmKr": "일",
                                        "dataType": "VARCHAR", "dataLen": 20, "nullableYn": "Y"}])
        assert code(r) == 200, r
        seq = seq_of(dm, "MODIFY_ATTR", t, "C1R")
        st["seq_b"] = seq
        pv = contents(post(sa, "/api/dmApproval/ddlPreview", {"changeSeq": seq}))
        ex = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
        col = psql(f"select column_name||':'||character_maximum_length from information_schema.columns where table_schema='{nm}' and table_name='zz_t17b'")
        done = psql(f"select coalesce(ddl_exec_done,'(null)')||'|'||ddl_exec_result from quality.tb_data_model_change_history where change_seq={seq}")
        print(f"  확인창 {pv.get('status')} {len(pv.get('statements') or [])}문장\n  첫 실행 {ex}\n  DB 컬럼={col} / 기록={done}")
        assert pv.get("status") == "RUN" and len(pv.get("statements")) == 2
        assert ex.get("failed") == 1 and "(2/2번째 문장, 앞 1문장은 반영됨)" in ex["messages"][0]
        assert col == "c1r:10" and done.endswith("|FAILED") and json.loads(done.split("|")[0]) == [pv["statements"][0]]
        # 뷰를 지우고 다시 실행 — 이름 변경은 건너뛰고 타입 변경만
        psql(f"drop view {nm}.zz_v17")
        pv2 = contents(post(sa, "/api/dmApproval/ddlPreview", {"changeSeq": seq}))
        ex2 = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
        col = psql(f"select column_name||':'||character_maximum_length from information_schema.columns where table_schema='{nm}' and table_name='zz_t17b'")
        row = psql(f"select coalesce(ddl_exec_done,'(null)')||'|'||ddl_exec_result||'|'||ddl_exec_message from quality.tb_data_model_change_history where change_seq={seq}")
        snip = psql(f"select ddl_snippet from quality.tb_data_model_change_history where change_seq={seq}")
        print(f"  다시 확인창 {pv2.get('status')} {pv2.get('statements')} / {pv2.get('message')}\n  다시 실행 {ex2}\n  DB 컬럼={col} / 기록={row}")
        assert pv2.get("status") == "RUN" and pv2.get("statements") == [pv["statements"][1]] and "1문장은 이미 반영" in pv2.get("message")
        assert ex2.get("success") == 1 and col == "c1r:20" and row.startswith("(null)|SUCCESS|") and "1문장은 건너뜀" in row
        assert snip.count(";") == 2
    step("L17-2. 두 문장 중 둘째가 실패한 뒤 다시 실행 — 반영된 이름 변경은 건너뛰고 타입 변경만 실행, 기록 비움", _resume)

    def _mismatch():
        dm, nm = st["dm"], st["nm"]
        t = "ZZ_T17B"
        assert code(save_attrs(sa, dm, nm, t, [{"mode": "UPDATE", "origAttrNm": "C1R", "attrNm": "C1S", "attrNmKr": "일",
                                                 "dataType": "VARCHAR", "dataLen": 20, "nullableYn": "Y"}])) == 200
        seq = seq_of(dm, "MODIFY_ATTR", t, "C1S")
        psql("update quality.tb_data_model_change_history set ddl_exec_done='[\"ALTER TABLE zz.x RENAME COLUMN a TO b\"]', "
             f"ddl_exec_result='FAILED' where change_seq={seq}")
        pv = contents(post(sa, "/api/dmApproval/ddlPreview", {"changeSeq": seq}))
        ex = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
        col = psql(f"select column_name from information_schema.columns where table_schema='{nm}' and table_name='zz_t17b'")
        print(f"  확인창 {pv.get('status')} {str(pv.get('message'))[:90]}\n  실행 {ex} / DB 컬럼={col}")
        assert pv.get("status") == "DENY" and "앞부분과 다릅니다" in pv.get("message")
        assert ex.get("failed") == 1 and ex.get("success") == 0 and col == "c1r"
    step("L17-3. 기록된 반영 문장이 지금 만든 문장의 앞부분과 다르면 거부 — 실행 안 됨", _mismatch)


# ---------------------------------------------------------------- L18
def l18(sa, su):
    st = {}

    def _std_map():
        r_id, o_id = "ZZ109R" + TAG, "ZZ109O" + TAG
        st["r"] = r_id
        st["r_dom"] = dict_with_domain(r_id, "109 참조 시험 " + TAG, "REF", old_std="Y")
        dict_with_domain(o_id, "109 기관 시험 " + TAG, "ORG", old_std="Y")
        raw_word("ZZ109WR" + TAG, r_id, "ZZ109참조새단어" + TAG, "ZZWR" + TAG)
        raw_word("ZZ109WO" + TAG, o_id, "ZZ109기관새단어" + TAG, "ZZWO" + TAG)
        mid = "ZZ109MAP" + TAG
        psql(f"insert into quality.tb_std_map (map_id, dict_id, map_tp, old_nm, new_id, use_yn) "
             f"values ('{mid}', '{r_id}', 'WORD', 'ZZ109옛단어{TAG}', 'ZZ109WR{TAG}', 'Y')")
        time.sleep(11)   # 사전 정보 캐시(10초)
        r = post(sa, "/api/stdMap/save", {"mapId": mid, "dictId": o_id, "mapTp": "WORD", "oldNm": "ZZ109고침" + TAG, "newId": "ZZ109WO" + TAG})
        now = psql(f"select old_nm||'|'||new_id from quality.tb_std_map where map_id='{mid}'")
        print(f"  참조 사전 매핑을 기관 사전 dictId 로 수정={code(r)} {r.get('resultMessage')} / 매핑={now}")
        assert code(r) == 500 and "참조 사전은 고칠 수 없습니다" in r.get("resultMessage") and now == f"ZZ109옛단어{TAG}|ZZ109WR{TAG}"
        # 기관 사전 자기 매핑은 지금처럼 등록·수정
        r1 = post(sa, "/api/stdMap/save", {"dictId": o_id, "mapTp": "WORD", "oldNm": "ZZ109기관옛" + TAG, "newId": "ZZ109WO" + TAG})
        mid2 = contents(r1) if isinstance(contents(r1), str) else r1.get("contents")
        r2 = post(sa, "/api/stdMap/save", {"mapId": mid2, "dictId": o_id, "mapTp": "WORD", "oldNm": "ZZ109기관옛2" + TAG, "newId": "ZZ109WO" + TAG})
        now2 = psql(f"select dict_id||'|'||old_nm from quality.tb_std_map where map_id='{mid2}'")
        print(f"  기관 사전 매핑 등록={code(r1)} 수정={code(r2)} / {now2}")
        assert code(r1) == 200 and code(r2) == 200 and now2 == f"{o_id}|ZZ109기관옛2{TAG}"
    step("L18-1. 표준 매핑 — 참조 사전 매핑을 요청 dictId 만 바꿔 수정하면 거부, 기관 사전 매핑은 그대로 등록·수정", _std_map)

    def _integ():
        r_id = st["r"]
        s1, s2, g = "ZZ109S1" + TAG, "ZZ109S2" + TAG, "ZZ109G" + TAG
        ids = {k: "ZZ109C" + k + TAG for k in ("S1", "S2", "G")}

        def _drop():
            for t in ids.values():
                psql(f"delete from quality.tb_change_history where target_id='{t}'")
                psql(f"delete from quality.tb_terms where terms_id='{t}'")
            psql(f"delete from quality.tb_code_integ where dict_id='DEFAULT' and code_grp='{g}'")
        cleanup.append(_drop)
        raw_code(ids["S1"], "DEFAULT", "ZZ109하위코드일" + TAG, "ZZS1" + TAG, s1)
        raw_code(ids["S2"], "DEFAULT", "ZZ109하위코드이" + TAG, "ZZS2" + TAG, s2)
        raw_code(ids["G"], "DEFAULT", "ZZ109통합코드" + TAG, "ZZG" + TAG, g, tp="INTEG")
        psql(f"insert into quality.tb_code_integ (dict_id, code_grp, sub_code_grp, sort_order) values ('DEFAULT', '{g}', '{s1}', 0), ('DEFAULT', '{g}', '{s2}', 1)")
        # 참조 사전에도 같은 이름의 코드와 통합 구성이 있다
        raw_code("ZZ109RS1" + TAG, r_id, "ZZ109참조하위일" + TAG, "ZZRS1" + TAG, s1, domain=st["r_dom"])
        raw_code("ZZ109RS2" + TAG, r_id, "ZZ109참조하위이" + TAG, "ZZRS2" + TAG, s2, domain=st["r_dom"])
        psql(f"insert into quality.tb_code_integ (dict_id, code_grp, sub_code_grp, sort_order) values ('{r_id}', '{g}', 'ZZX{TAG}', 0)")
        r = post(sa, "/api/std/updateCode", code_body(ids["G"], "ZZ109통합코드" + TAG, "ZZG" + TAG, g, "INTEG", subs=[s2, s1], dict_id=r_id))
        ref = psql(f"select string_agg(sub_code_grp, ',' order by sort_order) from quality.tb_code_integ where dict_id='{r_id}' and code_grp='{g}'")
        own = psql(f"select string_agg(sub_code_grp, ',' order by sort_order) from quality.tb_code_integ where dict_id='DEFAULT' and code_grp='{g}'")
        print(f"  기관 사전 통합 코드를 dictId=참조 사전으로 수정={code(r)} {r.get('resultMessage')}\n  참조 사전 구성={ref} / 기관 사전 구성={own}")
        assert code(r) == 200 and ref == f"ZZX{TAG}" and own == f"{s2},{s1}"
    step("L18-2. 통합 코드 구성 저장 — 요청 dictId 가 참조 사전이어도 저장된 코드의 사전(기관)에 저장, 참조 사전 구성 그대로", _integ)


# ---------------------------------------------------------------- L22
def l22(sa, su):
    def _type_change():
        p, c, h = "ZZ109P" + TAG, "ZZ109CH" + TAG, "ZZ109H" + TAG
        ids = {k: "ZZ109K" + k + TAG for k in ("P", "C", "H")}

        def _drop():
            for t in ids.values():
                psql(f"delete from quality.tb_change_history where target_id='{t}'")
                psql(f"delete from quality.tb_terms where terms_id='{t}'")
            psql(f"delete from quality.tb_code_data where dict_id='DEFAULT' and code_grp in ('{p}','{c}','{h}')")
        cleanup.append(_drop)
        raw_code(ids["P"], "DEFAULT", "ZZ109상위코드" + TAG, "ZZKP" + TAG, p)
        raw_code(ids["C"], "DEFAULT", "ZZ109자식코드" + TAG, "ZZKC" + TAG, c)
        raw_code(ids["H"], "DEFAULT", "ZZ109계층코드" + TAG, "ZZKH" + TAG, h, tp="HIER", parent=p)
        for i, v in enumerate(("A", "B")):
            raw_value(f"ZZ109VP{i}{TAG}", "DEFAULT", p, v)
        raw_value("ZZ109VC0" + TAG, "DEFAULT", c, "A")
        raw_value("ZZ109VC1" + TAG, "DEFAULT", c, "X")
        raw_value("ZZ109VH0" + TAG, "DEFAULT", h, "Q")         # 상위 코드값 없는 (예전에 들어간) 계층 값
        body = lambda tp, parent=None, desc="109 시험 코드": code_body(ids["C"], "ZZ109자식코드" + TAG, "ZZKC" + TAG, c, tp, parent, desc=desc)
        r1 = post(sa, "/api/std/updateCode", body("DERIV", p))
        r2 = post(sa, "/api/std/updateCode", body("HIER", p))
        tp1 = psql(f"select coalesce(code_tp,'-') from quality.tb_terms where terms_id='{ids['C']}'")
        print(f"  값 A·X 인 코드를 파생으로={code(r1)} {r1.get('resultMessage')}\n  계층으로={code(r2)} {r2.get('resultMessage')} / 유형={tp1}")
        assert code(r1) == 500 and "파생 코드로 바꾸려면" in r1["resultMessage"] and "1건: X" in r1["resultMessage"]
        assert code(r2) == 500 and "계층 코드로 바꾸려면" in r2["resultMessage"] and "2건" in r2["resultMessage"] and tp1 == "-"
        psql(f"delete from quality.tb_code_data where code_id='ZZ109VC1{TAG}'")
        r3 = post(sa, "/api/std/updateCode", body("DERIV", p))
        tp2 = psql(f"select code_tp||'|'||parent_code_grp from quality.tb_terms where terms_id='{ids['C']}'")
        print(f"  X 를 지운 뒤 파생으로={code(r3)} / {tp2}")
        assert code(r3) == 200 and tp2 == f"DERIV|{p}"
        # 유형·상위 코드를 바꾸지 않는 수정은 기존 값을 다시 보지 않는다 (예전에 들어간 값이 있어도 설명 수정은 된다)
        r4 = post(sa, "/api/std/updateCode", code_body(ids["H"], "ZZ109계층코드" + TAG, "ZZKH" + TAG, h, "HIER", p, desc="설명만 고침"))
        print(f"  계층 코드 설명만 수정={code(r4)} {r4.get('resultMessage')}")
        assert code(r4) == 200 and psql(f"select terms_desc from quality.tb_terms where terms_id='{ids['H']}'") == "설명만 고침"
    step("L22-1. 코드 유형 변경 — 기존 값이 파생·계층 규칙에 안 맞으면 거부, 맞으면 바꿈, 유형을 그대로 두면 다시 보지 않음", _type_change)

    def _rename():
        r_, e_ = "ZZ109RN" + TAG, "ZZ109EM" + TAG
        ids = {k: "ZZ109N" + k + TAG for k in ("R", "E", "U")}

        def _drop():
            for t in ids.values():
                psql(f"delete from quality.tb_change_history where target_id='{t}'")
                psql(f"delete from quality.tb_terms where terms_id='{t}'")
            psql(f"delete from quality.tb_code_data where dict_id='DEFAULT' and code_grp like 'ZZ109RN{TAG}%'")
        cleanup.append(_drop)
        raw_code(ids["R"], "DEFAULT", "ZZ109값있는코드" + TAG, "ZZNR" + TAG, r_)
        raw_code(ids["E"], "DEFAULT", "ZZ109빈코드" + TAG, "ZZNE" + TAG, e_)
        raw_value("ZZ109VR0" + TAG, "DEFAULT", r_, "1")
        r1 = post(sa, "/api/std/updateCode", code_body(ids["R"], "ZZ109값있는코드" + TAG, "ZZNR" + TAG, r_ + "B", "SINGLE"))
        g1 = psql(f"select code_grp from quality.tb_terms where terms_id='{ids['R']}'")
        r2 = post(sa, "/api/std/updateCode", code_body(ids["E"], "ZZ109빈코드" + TAG, "ZZNE" + TAG, e_ + "B", "SINGLE"))
        g2 = psql(f"select code_grp from quality.tb_terms where terms_id='{ids['E']}'")
        print(f"  값 있는 코드 그룹 이름 변경={code(r1)} {r1.get('resultMessage')} / 그룹={g1}\n  값 없는 코드 이름 변경={code(r2)} / 그룹={g2}")
        assert code(r1) == 500 and "옛 이름에 남는 것이 있어" in r1["resultMessage"] and "코드값 1건" in r1["resultMessage"] and g1 == r_
        assert code(r2) == 200 and g2 == e_ + "B"
        # 같은 코드 그룹을 쓰는 다른 용어가 있으면 옛 그룹은 남으므로 바꿀 수 있다
        raw_code(ids["U"], "DEFAULT", "ZZ109같은그룹용어" + TAG, "ZZNU" + TAG, r_)
        r3 = post(sa, "/api/std/updateCode", code_body(ids["R"], "ZZ109값있는코드" + TAG, "ZZNR" + TAG, r_ + "B", "SINGLE"))
        g3 = psql(f"select code_grp from quality.tb_terms where terms_id='{ids['R']}'")
        print(f"  같은 그룹을 쓰는 용어가 있을 때 이름 변경={code(r3)} / 그룹={g3}")
        assert code(r3) == 200 and g3 == r_ + "B"
    step("L22-2. 코드 그룹 이름 변경 — 옛 그룹에 값이 남으면 거부, 값이 없거나 같은 그룹을 쓰는 다른 용어가 있으면 바꿈", _rename)


# ---------------------------------------------------------------- L23
def l23(sa, su):
    st = {}

    def result(diag):
        r = get(sa, f"/api/std/structDiag/result/{diag}")
        return {d["seq"]: d for d in r.get("details") or []}

    def apply(s, diag, seqs):
        return post(s, "/api/std/structDiag/apply", {"diagId": diag, "seqs": seqs})

    def _setup():
        dm = make_model(sa, "ZZ109구조반영_" + TAG)
        st["dm"] = dm
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": "", "objNm": "ZZ_T23", "objNmKr": "시험테이블"})) == 200
        assert code(save_attrs(sa, dm, "", "ZZ_T23", [{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10, "nullableYn": "Y"}])) == 200
        m = psql(f"select data_type||'|'||data_len||'|'||nullable_yn from quality.tb_data_model_attr where dm_id='{dm}' and attr_nm='C1'")
        assert m == "VARCHAR|10|Y", m
        ids = []
        for k, ago in (("1", "1 hour"), ("2", "0 second")):
            d = "ZZ109SD" + k + TAG
            ids.append(d)
            psql(f"insert into quality.tb_struct_diag_history (diag_id, data_model_id, status, diag_dt, cret_user_id) "
                 f"values ('{d}', '{dm}', 'DONE', now() - interval '{ago}', 'space')")
            psql(f"insert into quality.tb_struct_diag_detail (diag_id, seq, owner, table_nm, column_nm, change_type, prev_data_type, curr_data_type, "
                 f"prev_data_len, curr_data_len, prev_nullable, curr_nullable, curr_attr_ord) values "
                 f"('{d}', 1, '', 'ZZ_T23', 'C1', 'MODIFIED', 'VARCHAR', 'VARCHAR', 10, 30, 'Y', 'Y', 1), "
                 f"('{d}', 2, '', 'ZZ_T23', 'C2', 'ADDED', null, 'VARCHAR', null, 5, null, 'Y', 2)")
        cleanup.append(lambda: (psql(f"delete from quality.tb_struct_diag_detail where diag_id in ('{ids[0]}','{ids[1]}')"),
                                psql(f"delete from quality.tb_struct_diag_history where diag_id in ('{ids[0]}','{ids[1]}')")))
        st["old"], st["new"] = ids

    def _latest():
        _setup()
        old = result(st["old"])
        r = apply(sa, st["old"], [1])
        print(f"  지난 진단 판정={[(k, v['applicable'], v['notApplicableReason']) for k, v in old.items()]}\n  지난 진단 반영={code(r)} {r.get('resultMessage')}")
        assert all(v["applicable"] is False and "더 최근 구조 진단" in v["notApplicableReason"] for v in old.values())
        assert code(r) == 500 and "더 최근 구조 진단" in r.get("resultMessage")
        new = result(st["new"])
        assert new[1]["applicable"] is True and new[2]["applicable"] is True
    step("L23-1. 지난 진단 결과는 반영 불가(최근 결과에서만), 최근 결과는 반영 가능", _latest)

    def _reject_reapply():
        dm, d = st["dm"], st["new"]
        r1 = apply(su, d, [1])                                             # 일반 사용자 — 승인 대기로 저장
        c1 = contents(r1)
        after = result(d)[1]
        r2 = apply(su, d, [1])
        c2 = contents(r2)
        print(f"  일반 사용자 반영={code(r1)} {c1} / 판정={after['applicable']} 반영 표시={after.get('appliedDt')}\n  다시 반영={code(r2)} {c2}")
        assert code(r1) == 200 and c1.get("applied") == 1 and after["applicable"] is False and after.get("appliedDt")
        assert code(r2) == 200 and c2.get("applied") == 0 and "진단 뒤 모델 컬럼이 바뀌었습니다" in c2["errors"][0]["message"]
        seq = seq_of(dm, "MODIFY_ATTR", "ZZ_T23", "C1", "DRAFT")
        assert code(post(sa, "/api/dmApproval/reject", {"changeSeqList": [seq], "aprvComment": "반려 시험"})) == 200
        back = result(d)[1]
        r3 = apply(su, d, [1])
        c3 = contents(r3)
        ln = psql(f"select data_len from quality.tb_data_model_attr where dm_id='{dm}' and attr_nm='C1'")
        print(f"  반려 뒤 판정={back['applicable']} 반영 표시={back.get('appliedDt')} / 다시 반영={code(r3)} {c3} / 모델 길이={ln}")
        assert back["applicable"] is True and code(r3) == 200 and c3.get("applied") == 1 and ln == "30"
        seq = seq_of(dm, "MODIFY_ATTR", "ZZ_T23", "C1", "DRAFT")
        assert code(post(sa, "/api/dmApproval/reject", {"changeSeqList": [seq], "aprvComment": "반려 시험"})) == 200
    step("L23-2. 승인 대기로 반영한 행은 다시 반영 안 됨, 그 변경을 반려하면 같은 결과로 다시 반영", _reject_reapply)

    def _stale():
        dm, d = st["dm"], st["new"]
        assert code(save_attrs(sa, dm, "", "ZZ_T23", [{"mode": "UPDATE", "origAttrNm": "C1", "attrNm": "C1", "attrNmKr": "일",
                                                        "dataType": "VARCHAR", "dataLen": 15, "nullableYn": "Y"}])) == 200
        row = result(d)[1]
        r = apply(sa, d, [1])
        c = contents(r)
        ln = psql(f"select data_len from quality.tb_data_model_attr where dm_id='{dm}' and attr_nm='C1'")
        print(f"  진단 뒤 모델 길이 15 로 수정 → 판정={row['applicable']} {row['notApplicableReason']}\n  반영={c} / 모델 길이={ln}")
        assert row["applicable"] is False and "진단 뒤 모델 컬럼이 바뀌었습니다" in row["notApplicableReason"]
        assert c.get("applied") == 0 and len(c.get("errors")) == 1 and ln == "15"
        # 추가 행 — 반영하면 모델에 컬럼이 생기고 그 뒤로는 반영 불가
        c2 = contents(apply(sa, d, [2]))
        row2 = result(d)[2]
        print(f"  추가 행 반영={c2} / 판정={row2['applicable']} {row2['notApplicableReason']}")
        assert c2.get("applied") == 1 and row2["applicable"] is False and "모델에 이미 있는 컬럼" in row2["notApplicableReason"]
    step("L23-3. 진단 뒤 모델 컬럼을 고치면 그 행은 반영 불가(모델 값 그대로), 추가 행은 한 번만", _stale)


# ---------------------------------------------------------------- L24
def l24(sa, su):
    def _child_dict():
        p_id, c_id = "ZZ109PD" + TAG, "ZZ109CD" + TAG
        dom = dict_with_domain(p_id, "109 상위 시험 " + TAG, "ORG")
        psql(f"insert into quality.tb_std_dict (dict_id, dict_nm, dict_tp, use_yn, parent_dict_id) values ('{c_id}', '109 하위 시험 {TAG}', 'ORG', 'Y', '{p_id}')")
        cleanup.append(lambda: psql(f"delete from quality.tb_std_dict where dict_id='{c_id}'"))
        wa, wb, wc, tid = "ZZ109WA" + TAG, "ZZ109WB" + TAG, "ZZ109WC" + TAG, "ZZ109T24" + TAG
        raw_word(wa, p_id, "ZZ109상위단어가" + TAG, "ZZA" + TAG)
        raw_word(wb, p_id, "ZZ109상위단어나" + TAG, "ZZB" + TAG)
        raw_word(wc, p_id, "ZZ109상위단어다" + TAG, "ZZC" + TAG)
        psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, aprv_yn, dict_id) "
             f"values ('{tid}', 'ZZ109상위용어{TAG}', 'ZZT{TAG}', '109 시험', '{dom}', 'Y', '{p_id}')")
        psql(f"insert into quality.tb_terms_words (terms_id, word_id, word_nm, word_ord) values ('{tid}', '{wa}', 'ZZ109상위단어가{TAG}', 0)")
        time.sleep(11)   # 사전 정보 캐시(10초)
        # 하위 사전 모델이 상위 사전 용어(영문명 그대로)·단어(토큰)를 쓴다
        dm = make_model(sa, "ZZ109하위사전모델_" + TAG, dict_id=c_id)
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": "", "objNm": "ZZ_T24", "objNmKr": "시험테이블"})) == 200
        assert code(save_attrs(sa, dm, "", "ZZ_T24", [
            {"mode": "ADD", "attrNm": "ZZT" + TAG, "attrNmKr": "상위용어컬럼", "dataType": "VARCHAR", "dataLen": 10},
            {"mode": "ADD", "attrNm": f"ZZB{TAG}_CD", "attrNmKr": "상위단어컬럼", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        assert psql(f"select coalesce(aprv_status,'-') from quality.tb_data_model where dm_id='{dm}'") in ("APPROVED", "-")
        terms = [x["id"] for x in post(sa, "/api/std/unused/list", {"dictId": p_id, "kind": "TERM", "aprvOnly": "Y"})["items"]]
        words = [x["id"] for x in post(sa, "/api/std/unused/list", {"dictId": p_id, "kind": "WORD", "aprvOnly": "Y"})["items"]]
        iw = [c["attrNm"] for c in get(sa, "/api/std/impact/word", {"wordId": wb}).get("columns") or []]
        it = [(c["attrNm"], c["matchType"]) for c in get(sa, "/api/std/impact/term", {"termsId": tid}).get("columns") or []]
        print(f"  상위 사전 미사용 용어={terms} / 미사용 단어={sorted(words)}\n  단어 나 영향 컬럼={iw} / 용어 영향 컬럼={it}")
        assert tid not in terms and wb not in words and wa not in words and wc in words
        assert iw == [f"ZZB{TAG}_CD"] and it == [("ZZT" + TAG, "ENG")]
        # q-executor 예약 실행(미사용 표준)도 같은 판정 — 상위 사전을 쓰는 모델로 예약을 만들어 바로 실행
        dmp = make_model(sa, "ZZ109상위사전모델_" + TAG, dict_id=p_id)
        r = post(sa, "/api/diag/schedule/create", {"scheduleNm": "ZZ109미사용_" + TAG, "diagType": "UNUSED_STD", "dataModelId": dmp,
                                                   "scheduleType": "SIMPLE", "repeatCycle": "DAILY", "repeatTime": "03:00", "useYn": "N"})
        assert code(r) == 200, r
        sid = r.get("contents")

        def _drop():
            psql(f"delete from quality.tb_unused_std_result where run_id in (select batch_run_id from quality.tb_diag_schedule_log where schedule_id='{sid}')")
            psql(f"delete from quality.tb_batch_run where run_id in (select batch_run_id from quality.tb_diag_schedule_log where schedule_id='{sid}')")
            psql(f"delete from quality.tb_diag_schedule_log where schedule_id='{sid}'")
            psql(f"delete from quality.tb_diag_schedule where schedule_id='{sid}'")
        cleanup.append(_drop)
        r = post(sa, "/api/diag/schedule/runNow", {"scheduleId": sid}, timeout=60)
        assert code(r) == 200, r
        run, stat = None, None
        for _ in range(60):
            run = psql(f"select batch_run_id from quality.tb_diag_schedule_log where schedule_id='{sid}' and batch_run_id is not null")
            stat = psql(f"select status from quality.tb_batch_run where run_id='{run}'") if run else None
            if stat in ("DONE", "ERROR"):
                break
            time.sleep(1)
        got = psql(f"select string_agg(kind||':'||std_id, ',' order by kind, std_id) from quality.tb_unused_std_result where run_id='{run}' and std_id like 'ZZ109%'")
        print(f"  예약 실행 상태={stat} / 저장된 미사용(시험 항목)={got}")
        assert stat == "DONE" and got == f"WORD:{wc}"
    step("L24-1. 상위 사전 단어·용어를 하위 사전 모델이 쓰면 미사용이 아니고 영향도에 그 컬럼이 나옴 (화면 API·예약 실행)", _child_dict)


if __name__ == "__main__":
    t0 = time.time()
    main(set(sys.argv[1:]))
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
