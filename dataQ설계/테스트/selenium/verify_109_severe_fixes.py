# -*- coding: utf-8 -*-
"""
109번 심각 결함 1~3 (+낮음 21) 조치 검증 — API

  S1. 표준 승인 승인값 (#1·#21)
      - 3단계 결재선(관리자 전원)에서 aprvStat 없음·0·1 은 거부(400), 문서·단어 그대로
      - 2 로 1·2·3 단계를 차례로 넘겨야 단어가 승인된다
      - 3(반려)은 어느 단계든 문서 반려 + 단어 삭제 (승인되지 않음)
      - 여러 건 중 앞 건 403 이 뒤 건 200 에 덮이지 않는다 (#21)
  S2. 시스템 단위 DB 반영 권한 우회 (#2)
      - 일반 사용자는 모델 등록·수정에서 데이터소스를 지정·변경할 수 없다(403). 이름만 바꾸는 수정은 된다
      - 시스템 A 에만 DDL_EXEC 가 있는 DB 담당은 B 모델의 시스템을 A 로 못 바꾼다(403)
      - 모델 직접 반영 권한까지 있어 B 모델을 A 로 바꿔도 [DB 반영] 은 거부(데이터소스가 A 의 것이 아님), B 의 DB 에 테이블 없음
      - A 모델(A 의 데이터소스)은 그 사용자가 반영할 수 있다. 관리자 확인창은 그대로 RUN
  S3. [DB 반영] DDL 주입 (#3) — PostgreSQL(개발 DB 시험 스키마)·Oracle 19c(oracle-19c, 11521) 각각
      - 저장 때 거부: 기본값·테이블스페이스·컬럼·PK·FK 이름·데이터 타입에 주입, XMI 가져오기
      - 허용: 숫자·문자열·NULL·DBMS 함수 기본값
      - 확인창 API(ddlPreview)가 실제 CREATE TABLE 을 돌려준다
      - Tier1 6종(테이블 추가·삭제, 컬럼 추가·타입 변경·이름 변경·삭제) 반영 SUCCESS, 실제 DB 구조 확인
      - DB 에 직접 넣은 나쁜 값(기본값·이력 JSON)은 [DB 반영] 때 거부, 저장된 조각을 바꿔 넣어도 실행되지 않음

필요:
  - q-center(28091) 기동
  - 환경 변수 PGPASSWORD (개발 DB admin), 선택 PSQL (psql.exe 경로)
  - Docker oracle-19c 실행 (sqlplus / as sysdba 로 시험 계정을 만들고 지운다)
시험 데이터(결재선·단어·결재 문서·알림·역할·시험 사용자·시스템·데이터소스·모델·대상 DB 시험 스키마)는 끝에서 지운다.
"""
import base64, json, os, secrets, subprocess, sys, time, traceback
import requests

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
PSQL = os.environ.get("PSQL", r"C:\Program Files\PostgreSQL\13\bin\psql.exe")
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


def ora(sql):
    """oracle-19c 에서 sysdba 로 실행 (결과 텍스트)"""
    script = "set heading off feedback off pagesize 0 linesize 400\n" + sql + "\nexit\n"
    r = subprocess.run(["docker", "exec", "-i", "oracle-19c", "bash", "-c", "sqlplus -s / as sysdba"],
                       input=script, capture_output=True, text=True, timeout=120, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


def login_api(user, pwd="123"):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": base64.b64encode(pwd.encode()).decode()},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {user} {r.status_code}"
    return s


def post(s, path, body, timeout=30):
    r = s.post(BASE + path, json=body, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"http": r.status_code, "text": r.text[:300]}


def contents(r):
    c = r.get("contents")
    return json.loads(c) if isinstance(c, str) and c else (c or {})


def code(r):
    return r.get("resultCode")


def main():
    sa = login_api("space")
    su = login_api("jyjang")
    start = time.strftime("%Y%m%d%H%M%S")
    assert psql("select count(*) from quality.tb_user_role where user_id='jyjang'") == "0", "jyjang 에 이미 역할이 있다 — 시험이 권한을 바꾸므로 멈춘다"
    assert psql("select count(*) from quality.tb_aprv_line where req_tp='WORD'") == "0", "단어 결재선이 이미 있다 — 시험이 결재선을 바꾸므로 멈춘다"

    try:
        s1(sa, su, start)
        s2(sa, su)
        s3(sa)
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


# ---------------------------------------------------------------- S1
def s1(sa, su, start):
    words = ["ZZ단어가" + TAG, "ZZ단어나" + TAG, "ZZ단어다" + TAG, "ZZ단어라" + TAG]
    uid = "zz109u" + TAG
    upw = secrets.token_hex(6)

    def _clean():
        post(sa, "/api/aprv/line/save", {"reqTp": "WORD", "useYn": "N", "steps": []})
        psql("delete from quality.tb_aprv_line_step where req_tp='WORD'")
        psql("delete from quality.tb_aprv_line where req_tp='WORD'")
        for w in words:
            psql(f"delete from quality.tb_change_history where target_nm = '{w}'")
            psql(f"delete from quality.tb_aprv_stats where req_item_nm = '{w}'")
            psql(f"delete from quality.tb_word where word_nm = '{w}'")
        psql(f"delete from quality.tb_user where user_id = '{uid}'")
    cleanup.append(_clean)

    def word(w):
        i = words.index(w)
        r = post(su, "/api/std/createWord", {"wordNm": w, "wordEngAbrvNm": f"ZZW{i}{TAG}", "wordEngNm": f"ZZWORD{i}{TAG}",
                                             "wordDesc": "109 시험", "wordClsfYn": "N", "dictId": "DEFAULT"})
        wid = psql(f"select word_id from quality.tb_word where word_nm='{w}'")
        assert wid, f"단어 등록 실패: {r}"
        return wid

    def doc(wid):
        return psql(f"select doc_stat||'|'||cur_step_no from quality.tb_aprv_doc where target_id='{wid}' order by req_dt desc limit 1")

    def aprv_yn(wid):
        return psql(f"select aprv_yn from quality.tb_word where word_id='{wid}'")

    st = {}

    def _line():
        r = post(sa, "/api/aprv/line/save", {"reqTp": "WORD", "lineNm": "109 시험", "useYn": "Y", "steps": [
            {"stepNm": "1단계", "aprvrTp": "ADMIN", "agreeYn": "N"},
            {"stepNm": "2단계", "aprvrTp": "ADMIN", "agreeYn": "N"},
            {"stepNm": "3단계", "aprvrTp": "ADMIN", "agreeYn": "N"}]})
        assert code(r) == 200, r
        st["w1"] = word(words[0])
        st["w2"] = word(words[1])
        print(f"  문서 w1={doc(st['w1'])} w2={doc(st['w2'])}")
        assert doc(st["w1"]) == "PROGRESS|1" and doc(st["w2"]) == "PROGRESS|1"
    step("S1-1. 3단계 결재선 + 일반 사용자 단어 2건 등록 → 결재 문서", _line)

    def _bad_stat():
        base = {"reqTp": "WORD", "reqItemId": st["w1"], "reqItemNm": words[0], "aprvStatUpdtRsn": "aprvStat 시험"}
        outs = []
        for extra in ({}, {"aprvStat": 0}, {"aprvStat": 1}, {"aprvStat": None}):
            b = dict(base); b.update(extra)
            r = post(sa, "/api/std/putStdAprvStat", [b])
            outs.append((extra, code(r), r.get("resultMessage")))
        for o in outs:
            print("  ", o)
        print(f"  문서={doc(st['w1'])} 단어 승인={aprv_yn(st['w1'])}")
        assert all(o[1] == 400 for o in outs)
        assert doc(st["w1"]) == "PROGRESS|1" and aprv_yn(st["w1"]) == "N"
        assert psql(f"select count(*) from quality.tb_aprv_stats where req_item_id='{st['w1']}'") == "0"
    step("S1-2. aprvStat 없음·0·1·null → 400, 문서 1단계 그대로·단어 미승인", _bad_stat)

    def _steps():
        trail = []
        for i in range(3):
            r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": st["w1"], "reqItemNm": words[0], "aprvStat": 2}])
            trail.append((code(r), doc(st["w1"]), aprv_yn(st["w1"])))
        for t in trail:
            print("  ", t)
        assert trail[0] == (200, "PROGRESS|2", "N")
        assert trail[1] == (200, "PROGRESS|3", "N")
        assert trail[2][0] == 200 and trail[2][1].startswith("APPROVED") and trail[2][2] == "Y"
    step("S1-3. aprvStat 2 로 1→2→3 단계, 마지막 단계에서만 단어 승인", _steps)

    def _reject():
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": st["w2"], "reqItemNm": words[1],
                                                  "aprvStat": 3, "aprvStatUpdtRsn": "반려 시험"}])
        d = psql(f"select doc_stat from quality.tb_aprv_doc where target_id='{st['w2']}'")
        left = psql(f"select count(*) from quality.tb_word where word_id='{st['w2']}'")
        stats = psql(f"select string_agg(aprv_stat::text, ',') from quality.tb_aprv_stats where req_item_id='{st['w2']}'")
        print(f"  결과={code(r)} 문서={d} 단어 남음={left} 처리 이력={stats}")
        assert code(r) == 200 and d == "REJECTED" and left == "0" and stats == "3"
    step("S1-4. aprvStat 3 → 1단계에서 문서 반려·단어 삭제 (승인 이력 없음)", _reject)

    def _overwrite():
        # 결재선을 끄고 단어 다 를 등록(문서 없음), 결재선을 USER(시험 사용자) 1단계로 바꾸고 단어 라 등록(문서 있음)
        post(sa, "/api/aprv/line/save", {"reqTp": "WORD", "useYn": "N", "steps": []})
        st["w3"] = word(words[2])
        r = post(sa, "/api/login/createUser", {"id": uid, "password": base64.b64encode(upw.encode()).decode(),
                                               "name": "109 시험", "email": "", "admin": False})
        assert code(r) == 200, r
        r = post(sa, "/api/aprv/line/save", {"reqTp": "WORD", "lineNm": "109 시험", "useYn": "Y", "steps": [
            {"stepNm": "시험 사용자", "aprvrTp": "USER", "aprvrId": uid, "agreeYn": "N"}]})
        assert code(r) == 200, r
        st["w4"] = word(words[3])
        assert doc(st["w3"]) == "" and doc(st["w4"]) == "PROGRESS|1"
        sx = login_api(uid, upw)
        r = post(sx, "/api/std/putStdAprvStat", [
            {"reqTp": "WORD", "reqItemId": st["w3"], "reqItemNm": words[2], "aprvStat": 2},
            {"reqTp": "WORD", "reqItemId": st["w4"], "reqItemNm": words[3], "aprvStat": 2}])
        print(f"  결과={code(r)} {r.get('resultMessage')}")
        print(f"  단어 다 승인={aprv_yn(st['w3'])} 단어 라 승인={aprv_yn(st['w4'])} 문서 라={doc(st['w4'])}")
        assert code(r) == 403 and "처리 1건, 실패 1건" in (r.get("resultMessage") or "")
        assert aprv_yn(st["w3"]) == "N" and aprv_yn(st["w4"]) == "Y"
    step("S1-5. 여러 건 — 앞 건 403(권한 없음)이 뒤 건 200(현재 결재자)에 덮이지 않음 (#21)", _overwrite)


# ---------------------------------------------------------------- S2
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


def make_ds(sa, dsn, body):
    b = {"dsn": dsn, "dsTp": 0}
    b.update(body)
    r = post(sa, "/api/sysinfo/createDataSource", b)
    assert code(r) == 200, r
    ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
    cleanup.append(lambda: psql(f"delete from ndata.tb_data_source where ds_id='{ds}'"))
    return ds


def make_model(s, nm, ds=None, sys_cd=None):
    r = post(s, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds, "dataModelSysCd": sys_cd,
                                            "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT"})
    dm = psql(f"select dm_id from quality.tb_data_model where dm_nm='{nm}'")
    if dm:
        cleanup.append(lambda: drop_model(dm))
    return r, dm


def drop_model(dm):
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_constraint", "tb_data_model_index",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_udp_val", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id='{dm}'")
    psql(f"delete from quality.tb_data_model where dm_id='{dm}'")


def make_sys(sa, nm, ds_list):
    r = post(sa, "/api/sysinfo/createSysInfo", {"sysNm": nm, "sysTp": 1, "sysDsLst": ds_list, "sysDesc": "109 시험"})
    assert code(r) == 200, r
    cd = psql(f"select sys_cd from quality.tb_sys_info where sys_nm='{nm}'")
    cleanup.append(lambda: psql(f"delete from quality.tb_sys_info where sys_cd='{cd}'"))
    return cd


def make_role(sa, nm, perms):
    r = post(sa, "/api/role/save", {"roleNm": nm, "roleDesc": "109 시험", "perms": perms})
    assert code(r) == 200, r
    rid = r.get("contents")

    def _drop():
        psql(f"delete from quality.tb_user_role where role_id='{rid}'")
        post(sa, "/api/role/delete", {"roleId": rid})
        psql(f"delete from quality.tb_role_perm where role_id='{rid}'")
        psql(f"delete from quality.tb_role where role_id='{rid}'")
    cleanup.append(_drop)
    return rid


def add_obj(s, dm, owner, obj, **extra):
    b = {"dataModelId": dm, "objOwner": owner, "objNm": obj, "objNmKr": "시험테이블"}
    b.update(extra)
    return post(s, "/api/dm/addObj", b)


def seq_of(dm, change_type, obj=None, attr=None):
    w = f"dm_id='{dm}' and change_type='{change_type}'"
    if obj:
        w += f" and obj_nm='{obj}'"
    if attr:
        w += f" and attr_nm='{attr}'"
    v = psql(f"select max(change_seq) from quality.tb_data_model_change_history where {w}")
    assert v, f"이력 없음 {change_type} {obj} {attr}"
    return int(v)


def exec_ddl(s, seq):
    r = post(s, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60)
    return r, contents(r)


def preview(s, seq):
    r = post(s, "/api/dmApproval/ddlPreview", {"changeSeq": seq}, timeout=60)
    return r, contents(r)


def s2(sa, su):
    st = {}

    def _setup():
        st["a"] = pg_schema("zz109a")
        st["b"] = pg_schema("zz109b")
        st["dsA"] = make_ds(sa, "ZZ109_A_" + TAG, {"dbmsTp": "PostgreSQL", "driverName": "PostgreSQL", "svrAddr": "127.0.0.1",
                                                   "port": 25433, "userId": st["a"][0], "pwd": st["a"][1], "dbName": "postgres"})
        st["dsB"] = make_ds(sa, "ZZ109_B_" + TAG, {"dbmsTp": "PostgreSQL", "driverName": "PostgreSQL", "svrAddr": "127.0.0.1",
                                                   "port": 25433, "userId": st["b"][0], "pwd": st["b"][1], "dbName": "postgres"})
        st["sysA"] = make_sys(sa, "ZZ109시스템A_" + TAG, [st["dsA"]])
        st["sysB"] = make_sys(sa, "ZZ109시스템B_" + TAG, [st["dsB"]])
        r, st["mA"] = make_model(sa, "ZZ109모델A_" + TAG, st["dsA"], st["sysA"])
        assert code(r) == 200, r
        r, st["mB"] = make_model(sa, "ZZ109모델B_" + TAG, st["dsB"], st["sysB"])
        assert code(r) == 200, r
        print(f"  시스템 A={st['sysA']} B={st['sysB']} 모델 A={st['mA']} B={st['mB']}")
    step("S2-0. 준비 — PostgreSQL 시험 스키마 2개·데이터소스·시스템 A/B·모델 A/B (관리자)", _setup)

    def _normal():
        r1, dm1 = make_model(su, "ZZ109일반DS_" + TAG, st["dsA"])
        r2, dm2 = make_model(su, "ZZ109일반_" + TAG)
        cur = {"dataModelId": st["mB"], "dataModelNm": "ZZ109모델B_" + TAG, "dataModelSysCd": st["sysB"], "ver": "1", "dictId": "DEFAULT"}
        r3 = post(su, "/api/dm/updateDataModel", dict(cur, dataModelDsId=st["dsA"]))
        r4 = post(su, "/api/dm/updateDataModel", dict(cur, dataModelDsId=st["dsB"], dataModelNm="ZZ109모델B_" + TAG + "_이름"))
        post(sa, "/api/dm/updateDataModel", dict(cur, dataModelDsId=st["dsB"]))   # 이름 원복
        ds_now = psql(f"select dm_ds_id from quality.tb_data_model where dm_id='{st['mB']}'")
        print(f"  데이터소스 지정 등록={code(r1)} 모델={dm1 or '-'} / 데이터소스 없는 등록={code(r2)} / 데이터소스 변경={code(r3)} {r3.get('resultMessage')} / 이름만 수정={code(r4)}")
        assert code(r1) == 403 and not dm1
        assert code(r2) == 200 and dm2
        assert code(r3) == 403 and ds_now == st["dsB"]
        assert code(r4) == 200
    step("S2-1. 일반 사용자 — 데이터소스 지정 등록·변경 403, 데이터소스 없는 등록·이름만 수정 200", _normal)

    def _db_only():
        st["rDb"] = make_role(sa, "ZZ109DB담당_" + TAG, ["DDL_EXEC"])
        r = post(sa, "/api/role/assign", {"userId": "jyjang", "roleId": st["rDb"], "sysCd": st["sysA"]})
        assert code(r) == 200, r
        r = post(su, "/api/dm/updateDataModel", {"dataModelId": st["mB"], "dataModelNm": "ZZ109모델B_" + TAG,
                                                 "dataModelSysCd": st["sysA"], "dataModelDsId": st["dsB"], "ver": "1", "dictId": "DEFAULT"})
        sys_now = psql(f"select dm_sys_cd from quality.tb_data_model where dm_id='{st['mB']}'")
        print(f"  B 모델 시스템 → A = {code(r)} {r.get('resultMessage')} / 지금 시스템={sys_now}")
        assert code(r) == 403 and sys_now == st["sysB"]
        # 관리자가 B 모델에 테이블 추가(승인) → 시스템 A 담당의 실행은 시스템 권한으로 거부
        assert code(add_obj(sa, st["mB"], st["b"][0], "ZZ_T_B")) == 200
        st["seqB"] = seq_of(st["mB"], "ADD_OBJ", "ZZ_T_B")
        r, c = exec_ddl(su, st["seqB"])
        print(f"  B 테이블 반영(시스템 B) = {c}")
        assert c.get("failed") == 1 and "권한이 없습니다" in " ".join(c.get("messages", []))
    step("S2-2. 시스템 A 의 DDL_EXEC 만 — B 모델 시스템을 A 로 변경 403, B 모델 반영 거부", _db_only)

    def _with_dm():
        st["rDm"] = make_role(sa, "ZZ109모델반영_" + TAG, ["DM_MANAGE"])
        r = post(sa, "/api/role/assign", {"userId": "jyjang", "roleId": st["rDm"], "sysCd": "*"})
        assert code(r) == 200, r
        r = post(su, "/api/dm/updateDataModel", {"dataModelId": st["mB"], "dataModelNm": "ZZ109모델B_" + TAG,
                                                 "dataModelSysCd": st["sysA"], "dataModelDsId": st["dsB"], "ver": "1", "dictId": "DEFAULT"})
        assert code(r) == 200, r
        pr, pv = preview(su, st["seqB"])
        r, c = exec_ddl(su, st["seqB"])
        made = psql(f"select count(*) from information_schema.tables where table_schema='{st['b'][0]}' and table_name='zz_t_b'")
        res = psql(f"select coalesce(ddl_exec_result,'-') from quality.tb_data_model_change_history where change_seq={st['seqB']}")
        print(f"  확인창={pv.get('status')} {pv.get('message')}")
        print(f"  실행={c} / B 스키마 테이블={made} / 이력 결과={res}")
        assert pv.get("status") == "DENY" and "등록된 데이터소스가 아닙니다" in (pv.get("message") or "")
        assert c.get("failed") == 1 and c.get("success") == 0 and made == "0" and res == "-"
    step("S2-3. DM_MANAGE 로 B 모델을 시스템 A 로 바꿔도 [DB 반영] 거부 — 데이터소스가 A 의 것이 아님", _with_dm)

    def _own():
        assert code(add_obj(sa, st["mA"], st["a"][0], "ZZ_T_A")) == 200
        r = post(sa, "/api/dm/saveAttrs", {"dataModelId": st["mA"], "objNm": "ZZ_T_A", "objOwner": st["a"][0], "attrs": [
            {"mode": "ADD", "attrNm": "COL_A", "attrNmKr": "가", "dataType": "VARCHAR", "dataLen": 20, "nullableYn": "Y"}]})
        assert code(r) == 200, r
        seq = seq_of(st["mA"], "ADD_OBJ", "ZZ_T_A")
        r, c = exec_ddl(su, seq)
        made = psql(f"select count(*) from information_schema.tables where table_schema='{st['a'][0]}' and table_name='zz_t_a'")
        pr, pv = preview(sa, st["seqB"])
        print(f"  A 모델 반영(시스템 A 담당)={c} 테이블={made} / 관리자 확인창(B 데이터소스·시스템 A 모델)={pv.get('status')}")
        assert c.get("success") == 1 and made == "1"
        assert pv.get("status") == "RUN"
    step("S2-4. A 모델(A 의 데이터소스)은 시스템 A 담당이 반영 SUCCESS, 관리자는 그대로 RUN", _own)

    def _unassign():
        post(sa, "/api/role/unassign", {"userId": "jyjang", "roleId": st.get("rDb"), "sysCd": st.get("sysA")})
        post(sa, "/api/role/unassign", {"userId": "jyjang", "roleId": st.get("rDm"), "sysCd": "*"})
    cleanup.append(_unassign)


# ---------------------------------------------------------------- S3
def pg_cols(schema, table):
    return psql(f"select string_agg(column_name||':'||data_type, ',' order by ordinal_position) from information_schema.columns "
                f"where table_schema='{schema}' and table_name='{table.lower()}'")


def ora_cols(owner, table):
    return ora(f"select listagg(column_name||':'||data_type, ',') within group (order by column_id) from all_tab_columns "
               f"where owner='{owner}' and table_name='{table}';")


def s3(sa):
    targets = []

    def _setup():
        nm, pw = pg_schema("zz109p")
        ds = make_ds(sa, "ZZ109_P_" + TAG, {"dbmsTp": "PostgreSQL", "driverName": "PostgreSQL", "svrAddr": "127.0.0.1",
                                            "port": 25433, "userId": nm, "pwd": pw, "dbName": "postgres"})
        r, dm = make_model(sa, "ZZ109주입P_" + TAG, ds)
        assert code(r) == 200, r
        psql(f"create table {nm}.zz_victim (id int)")
        targets.append({"dbms": "PostgreSQL", "owner": nm, "dm": dm, "cols": lambda t, o=nm: pg_cols(o, t),
                        "victim": lambda o=nm: psql(f"select count(*) from information_schema.tables where table_schema='{o}' and table_name='zz_victim'"),
                        "num": "NUMERIC", "fn": "CURRENT_TIMESTAMP", "ts_type": "TIMESTAMP"})

        onm = ("ZZ109O" + TAG).upper()
        opw = "Zz" + secrets.token_hex(6)
        out = ora(f"create user {onm} identified by \"{opw}\";\ngrant create session, create table, unlimited tablespace to {onm};\n"
                  f"create table {onm}.ZZ_VICTIM (ID number);\nselect 'ok' from dual;")
        cleanup.append(lambda: ora(f"drop user {onm} cascade;"))
        assert out.endswith("ok"), out
        ods = make_ds(sa, "ZZ109_O_" + TAG, {"dbmsTp": "Oracle", "driverName": "Oracle", "svrAddr": "127.0.0.1", "port": 11521,
                                             "userId": onm, "pwd": opw, "dbName": "ORCL", "connProps": "SID"})
        r, odm = make_model(sa, "ZZ109주입O_" + TAG, ods)
        assert code(r) == 200, r
        targets.append({"dbms": "Oracle", "owner": onm, "dm": odm, "cols": lambda t, o=onm: ora_cols(o, t),
                        "victim": lambda o=onm: ora(f"select count(*) from all_tables where owner='{o}' and table_name='ZZ_VICTIM';").strip(),
                        "num": "NUMBER", "fn": "SYSDATE", "ts_type": "DATE"})
        print(f"  대상 {[(t['dbms'], t['owner'], t['dm']) for t in targets]}")
    step("S3-0. 준비 — PostgreSQL 시험 스키마·Oracle 19c 시험 계정·데이터소스·모델, 피해 확인용 테이블 ZZ_VICTIM", _setup)

    for t in targets:
        s3_target(sa, t)

    def _xmi():
        dm = targets[0]["dm"]
        r = post(sa, "/api/dm/importXmiModel", {"dataModelId": dm, "tables": [{"objNm": "ZZ_XMI", "objNmKr": "x", "objAttrCnt": 1}],
                                                "columns": [{"objNm": "ZZ_XMI", "attrNm": "A INT); DROP TABLE ZZ_VICTIM; --", "attrNmKr": "x",
                                                             "dataType": "VARCHAR", "dataLen": 0, "nullableYn": "Y", "pkYn": "N", "fkYn": "N", "attrOrder": 1}]})
        r2 = post(sa, "/api/dm/importXmiModel", {"dataModelId": dm, "tables": [{"objNm": "ZZ_XMI", "objNmKr": "x", "objAttrCnt": 1}],
                                                 "columns": [{"objNm": "ZZ_XMI", "attrNm": "A1", "attrNmKr": "x",
                                                              "dataType": "VARCHAR(1); DROP", "dataLen": 0, "nullableYn": "Y", "pkYn": "N", "fkYn": "N", "attrOrder": 1}]})
        left = psql(f"select count(*) from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='ZZ_XMI'")
        print(f"  XMI 이름 주입={r.get('success')} {r.get('message')}\n  XMI 타입 주입={r2.get('success')} / 들어간 테이블={left}")
        assert r.get("success") is False and r2.get("success") is False and left == "0"
    step("S3-X. XMI 가져오기 — 컬럼 이름·데이터 타입 주입 거부", _xmi)


def s3_target(sa, t):
    dbms, owner, dm = t["dbms"], t["owner"], t["dm"]
    P = f"S3[{dbms}]"

    def save(attrs, obj="ZZ_T1"):
        return post(sa, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": obj, "objOwner": owner, "attrs": attrs})

    def _refuse():
        inj = "0); DROP TABLE ZZ_VICTIM; CREATE TABLE ZZ2 (A INT"
        checks = []
        checks.append(("테이블스페이스", add_obj(sa, dm, owner, "ZZ_TS", tablespaceNm="USERS; DROP TABLE ZZ_VICTIM")))
        assert code(add_obj(sa, dm, owner, "ZZ_T1")) == 200
        checks.append(("addAttr 기본값", post(sa, "/api/dm/addAttr", {"dataModelId": dm, "objOwner": owner, "objNm": "ZZ_T1",
                                                                       "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10,
                                                                       "defaultVal": inj, "termsStndYn": "N"})))
        checks.append(("saveAttrs 기본값 ;", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10,
                                                   "defaultVal": "'a'; DROP TABLE ZZ_VICTIM"}])))
        checks.append(("saveAttrs 기본값 쉼표", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10,
                                                    "defaultVal": "0, DROP COLUMN ID"}])))
        checks.append(("saveAttrs 기본값 주석", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10,
                                                    "defaultVal": "'a' --"}])))
        checks.append(("saveAttrs 기본값 따옴표", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10,
                                                     "defaultVal": "'a''"}])))
        checks.append(("saveAttrs 컬럼명", save([{"mode": "ADD", "attrNm": "C_X INT, ID", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": 10}])))
        checks.append(("saveAttrs 타입", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR(10)); DROP TABLE ZZ_VICTIM; --", "dataLen": 10}])))
        checks.append(("saveAttrs 길이 음수", save([{"mode": "ADD", "attrNm": "C_X", "attrNmKr": "x", "dataType": "VARCHAR", "dataLen": -1}])))
        assert code(save([{"mode": "ADD", "attrNm": "C_K", "attrNmKr": "키", "dataType": "VARCHAR", "dataLen": 10, "nullableYn": "N"}])) == 200
        checks.append(("PK 이름", post(sa, "/api/dm/createPk", {"dataModelId": dm, "objOwner": owner, "tableNm": "ZZ_T1",
                                                                "pkName": "PK_X PRIMARY KEY (C_K)); DROP TABLE ZZ_VICTIM; --", "columns": ["C_K"]})))
        checks.append(("PK 컬럼", post(sa, "/api/dm/createPk", {"dataModelId": dm, "objOwner": owner, "tableNm": "ZZ_T1",
                                                                "columns": ["C_K); DROP TABLE ZZ_VICTIM; --"]})))
        checks.append(("FK 이름", post(sa, "/api/dm/createFk", {"dataModelId": dm, "objOwner": owner, "tableNm": "ZZ_T1", "refOwner": owner,
                                                                "refTableNm": "ZZ_T1", "fkName": "FK_X; DROP TABLE ZZ_VICTIM",
                                                                "mappings": [{"ownAttrNm": "C_K", "refAttrNm": "C_K"}]})))
        checks.append(("FK 참조 컬럼", post(sa, "/api/dm/createFk", {"dataModelId": dm, "objOwner": owner, "tableNm": "ZZ_T1", "refOwner": owner,
                                                                    "refTableNm": "ZZ_T1",
                                                                    "mappings": [{"ownAttrNm": "C_K", "refAttrNm": "C_K) ON DELETE CASCADE --"}]})))
        checks.append(("updateObj 테이블스페이스", post(sa, "/api/dm/updateObj", {"dataModelId": dm, "origObjNm": "ZZ_T1", "objNm": "ZZ_T1",
                                                                              "objOwner": owner, "origObjOwner": owner, "objNmKr": "시험테이블",
                                                                              "tablespaceNm": "USERS /* x */"})))
        checks.append(("updateObj 소유자", post(sa, "/api/dm/updateObj", {"dataModelId": dm, "origObjNm": "ZZ_T1", "objNm": "ZZ_T1",
                                                                       "objOwner": "X; DROP", "origObjOwner": owner, "objNmKr": "시험테이블"})))
        for nm, r in checks:
            print(f"  {nm:20s} → {code(r)} {str(r.get('resultMessage'))[:110]}")
        assert all(code(r) not in (200, None) for _, r in checks)
        cols = psql(f"select string_agg(attr_nm, ',' order by attr_nm) from quality.tb_data_model_attr where dm_id='{dm}' and obj_nm='ZZ_T1'")
        ts = psql(f"select count(*) from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='ZZ_TS'")
        cons = psql(f"select count(*) from quality.tb_data_model_constraint where dm_id='{dm}'")
        tsp = psql(f"select coalesce(tablespace_nm,'') from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='ZZ_T1'")
        print(f"  저장된 컬럼={cols} / ZZ_TS 테이블={ts} / 제약조건={cons} / ZZ_T1 테이블스페이스='{tsp}'")
        assert cols == "C_K" and ts == "0" and cons == "0" and tsp == ""
    step(f"{P}-1. 저장 때 거부 — 기본값(; , -- 따옴표)·테이블스페이스·컬럼명·타입·길이·PK·FK 이름·소유자", _refuse)

    def _allow():
        attrs = [{"mode": "ADD", "attrNm": "C_S", "attrNmKr": "문자", "dataType": "VARCHAR", "dataLen": 20, "defaultVal": "'it''s'"},
                 {"mode": "ADD", "attrNm": "C_N", "attrNmKr": "숫자", "dataType": t["num"], "dataLen": 10, "dataDecimalLen": 2, "defaultVal": "-1.5"},
                 {"mode": "ADD", "attrNm": "C_D", "attrNmKr": "일시", "dataType": t["ts_type"], "dataLen": 0, "defaultVal": t["fn"]},
                 {"mode": "ADD", "attrNm": "C_Z", "attrNmKr": "널", "dataType": "VARCHAR", "dataLen": 5, "defaultVal": "NULL"}]
        r = save(attrs)
        print(f"  허용 기본값 저장={code(r)} {r.get('resultMessage') if code(r) != 200 else ''}")
        assert code(r) == 200
        r = post(sa, "/api/dm/createPk", {"dataModelId": dm, "objOwner": owner, "tableNm": "ZZ_T1", "columns": ["C_K"]})
        assert code(r) == 200, r
    step(f"{P}-2. 허용 — 문자열('' 포함)·음수 소수·{t['fn']}·NULL 기본값, PK", _allow)

    def _create():
        seq = seq_of(dm, "ADD_OBJ", "ZZ_T1")
        r, pv = preview(sa, seq)
        stmts = pv.get("statements") or []
        print(f"  확인창 status={pv.get('status')} 문장 {len(stmts)}개\n    " + (stmts[0].replace(chr(10), chr(10) + '    ') if stmts else ''))
        assert pv.get("status") == "RUN" and stmts and stmts[0].startswith("CREATE TABLE")
        assert "(...)" not in stmts[0] and "C_S" in stmts[0] and "DEFAULT 'it''s'" in stmts[0] and "PRIMARY KEY (C_K)" in stmts[0]
        r, c = exec_ddl(sa, seq)
        cols = t["cols"]("ZZ_T1")
        print(f"  실행={c}\n  DB 컬럼={cols}")
        assert c.get("success") == 1 and "C_K" in cols.upper() and "C_D" in cols.upper()
        ran = psql(f"select ddl_snippet from quality.tb_data_model_change_history where change_seq={seq}")
        assert ran.startswith("CREATE TABLE") and "(...)" not in ran
    step(f"{P}-3. 테이블 추가 — 확인창 API 가 실제 CREATE TABLE, 반영 SUCCESS", _create)

    def _tier1():
        out = []
        assert code(save([{"mode": "ADD", "attrNm": "C_ADD", "attrNmKr": "추가", "dataType": "VARCHAR", "dataLen": 20, "defaultVal": "'Y'"}])) == 200
        out.append(("컬럼 추가", exec_ddl(sa, seq_of(dm, "ADD_ATTR", "ZZ_T1", "C_ADD"))[1]))
        assert code(save([{"mode": "UPDATE", "origAttrNm": "C_ADD", "attrNm": "C_ADD", "attrNmKr": "추가", "dataType": "VARCHAR",
                           "dataLen": 50, "defaultVal": "'Y'", "nullableYn": "Y"}])) == 200
        out.append(("타입 변경", exec_ddl(sa, seq_of(dm, "MODIFY_ATTR", "ZZ_T1", "C_ADD"))[1]))
        assert code(save([{"mode": "UPDATE", "origAttrNm": "C_ADD", "attrNm": "C_REN", "attrNmKr": "추가", "dataType": "VARCHAR",
                           "dataLen": 50, "defaultVal": "'Y'", "nullableYn": "Y"}])) == 200
        out.append(("이름 변경", exec_ddl(sa, seq_of(dm, "MODIFY_ATTR", "ZZ_T1", "C_REN"))[1]))
        cols_mid = t["cols"]("ZZ_T1")
        assert code(save([{"mode": "DELETE", "attrNm": "C_REN"}])) == 200
        out.append(("컬럼 삭제", exec_ddl(sa, seq_of(dm, "DEL_ATTR", "ZZ_T1", "C_REN"))[1]))
        cols_end = t["cols"]("ZZ_T1")
        for nm, c in out:
            print(f"  {nm} → 성공 {c.get('success')} {c.get('messages')}")
        print(f"  이름 변경 뒤 컬럼={cols_mid}\n  삭제 뒤 컬럼={cols_end}")
        assert all(c.get("success") == 1 for _, c in out)
        assert "C_REN" in cols_mid.upper() and "C_ADD" not in cols_mid.upper() and "C_REN" not in cols_end.upper()
    step(f"{P}-4. Tier1 컬럼 추가·타입 변경·이름 변경·삭제 반영 SUCCESS", _tier1)

    def _forced():
        # (가) 새 테이블 컬럼의 기본값을 DB 에 직접 나쁜 값으로 — 테이블 추가 반영 때 거부
        assert code(add_obj(sa, dm, owner, "ZZ_T2")) == 200
        assert code(save([{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10}], obj="ZZ_T2")) == 200
        psql(f"update quality.tb_data_model_attr set default_val = '0); DROP TABLE ZZ_VICTIM; CREATE TABLE ZZ3 (A INT' "
             f"where dm_id='{dm}' and obj_nm='ZZ_T2' and attr_nm='C1'")
        seq2 = seq_of(dm, "ADD_OBJ", "ZZ_T2")
        r, pv = preview(sa, seq2)
        r, c = exec_ddl(sa, seq2)
        made = t["cols"]("ZZ_T2")
        print(f"  (가) 기본값 직접 변경 → 확인창 {pv.get('status')} {pv.get('message')}\n      실행 {c} / ZZ_T2={made or '없음'}")
        assert pv.get("status") == "DENY" and "DDL 검증 실패" in (pv.get("message") or "") and c.get("failed") == 1 and not made
        # (나) 데이터 타입을 DB 에 직접 매핑 없는 이름으로
        psql(f"update quality.tb_data_model_attr set default_val = null, data_type = 'STRING' where dm_id='{dm}' and obj_nm='ZZ_T2' and attr_nm='C1'")
        r, pv = preview(sa, seq2)
        print(f"  (나) 타입 STRING → 확인창 {pv.get('status')} {pv.get('message')}")
        assert pv.get("status") == "DENY" and "DDL 규칙에 없는 데이터 타입" in (pv.get("message") or "")
        # (다) 컬럼 추가 이력의 JSON 기본값을 직접 나쁜 값으로 — 다시 만들 때 거부
        assert code(save([{"mode": "ADD", "attrNm": "C_J", "attrNmKr": "제이", "dataType": "VARCHAR", "dataLen": 10, "defaultVal": "'a'"}])) == 200
        sj = seq_of(dm, "ADD_ATTR", "ZZ_T1", "C_J")
        psql(f"update quality.tb_data_model_change_history set after_json = replace(after_json, '\"defaultVal\":\"''a''\"', "
             f"'\"defaultVal\":\"0, DROP COLUMN C_K\"') where change_seq={sj}")
        r, pv = preview(sa, sj)
        print(f"  (다) 이력 JSON 기본값 → 확인창 {pv.get('status')} {pv.get('message')}")
        assert pv.get("status") == "DENY" and "기본값" in (pv.get("message") or "")
        # (라) 저장된 조각만 나쁜 문장으로 — 조각은 실행하지 않고 이력 값으로 만든 문장만 실행
        assert code(save([{"mode": "ADD", "attrNm": "C_Q", "attrNmKr": "큐", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        sq = seq_of(dm, "ADD_ATTR", "ZZ_T1", "C_Q")
        psql(f"update quality.tb_data_model_change_history set ddl_snippet = 'ALTER TABLE {owner}.ZZ_T1 ADD C_Q2 INT; DROP TABLE {owner}.ZZ_VICTIM;' "
             f"where change_seq={sq}")
        r, pv = preview(sa, sq)
        r, c = exec_ddl(sa, sq)
        cols = t["cols"]("ZZ_T1").upper()
        victim = t["victim"]()
        print(f"  (라) 조각 바꿔치기 → 확인창 {pv.get('statements')}\n      실행 {c} / C_Q={'C_Q:' in cols} C_Q2={'C_Q2' in cols} 피해 테이블={victim}")
        assert c.get("success") == 1 and "C_Q:" in cols and "C_Q2" not in cols and victim == "1"
        assert all("DROP" not in s for s in pv.get("statements") or [])
    step(f"{P}-5. DB 에 직접 넣은 나쁜 값 — 기본값·타입·이력 JSON 은 반영 때 거부, 바꿔 넣은 조각은 실행 안 됨", _forced)

    def _drop():
        r = post(sa, "/api/dm/deleteObjs", {"dataModelId": dm, "items": [{"objOwner": owner, "objNm": "ZZ_T1"}]})
        assert code(r) == 200, r
        r, c = exec_ddl(sa, seq_of(dm, "DEL_OBJ", "ZZ_T1"))
        cols = t["cols"]("ZZ_T1")
        print(f"  테이블 삭제 → {c} / 남은 컬럼={cols or '없음'} / 피해 테이블={t['victim']()}")
        assert c.get("success") == 1 and not cols and t["victim"]() == "1"
    step(f"{P}-6. Tier1 테이블 삭제 반영 SUCCESS, ZZ_VICTIM 그대로", _drop)


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
