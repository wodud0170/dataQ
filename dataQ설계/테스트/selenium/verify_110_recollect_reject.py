# -*- coding: utf-8 -*-
"""
110번 4.12-41-b·5.8-18 (사용자 결정 10-28) — 데이터 모델을 다시 수집하면 그 모델의 승인되지 않은 변경은 자동 반려 — API + 화면

  R1. 준비 — 개발 DB PostgreSQL 에 시험 역할·스키마(정상 a, 컬럼 단계에서 실패하는 c)·테이블, 데이터 소스(맞는 비밀번호·틀린 비밀번호), 모델 수집
  R2. 변경 — 일반 사용자(jyjang)가 컬럼 추가·길이 수정·영문명 변경·삭제, 테이블 추가(+컬럼)·이름 변경·삭제, FK 추가, PK 추가·삭제를 DRAFT 로 두고
      일부는 신청(결재선 DM_CHANGE 1단계 관리자를 켠 신청 = 결재 문서, 결재선을 끈 신청 = 문서 없음). 관리자가 승인한 변경·관리자 직접 변경
  R3. 수집 실패 — 틀린 비밀번호(접속 단계), 컬럼 단계 실패(테이블 단계 뒤) → 아무것도 반려되지 않음(이력·결재 문서·알림·자동 반려 수).
      114 권고 채택(10-28) — 수집은 한 트랜잭션이라 컬럼 단계에서 실패하면 테이블 단계도 남지 않는다(모델 행 그대로)
  R4. 재수집(화면 — 관리자 수집 확인창·진행 창, 요청자는 다른 창에 로그인) → 대기 이력 전부 REJECTED(반려자 system·사유·일시), 결재 문서 반려,
      요청자 알림(실시간 토스트·헤더 종·알림 테이블), 모델은 실제 DB 구조, 수집 결과(진행 창·수집이력·모델 목록)에 자동 반려 N건.
      권고 채택(10-28) — 수집이 읽은 칸은 수집 값, 수집이 덮지 않는 칸(컬럼 한글명, 테이블 한글명·설명)은 신청 전 값, 수집에 없는 컬럼·테이블의
      수정은 전부 신청 전 값(이름 포함), 이름 변경 사본의 수집이 덮지 않는 칸(한글명·개인정보·속성 값)은 처음 이름 행으로,
      DB 에서도 지워진 삭제 대기(컬럼·테이블·PK)는 물리 삭제(인덱스·속성 값까지), 읽지 않은 스키마의 삭제 대기는 감춘 채
  R5. 이미 승인된 변경은 그대로, 자동 반려된 건은 다시 승인할 수 없음, 물리 삭제된 이름으로 다시 추가, 대기 변경이 없으면 0건

필요:
  - q-center(28091)·q-executor(28098) 기동
  - 환경 변수 PGPASSWORD (개발 DB admin), DATAQ_TEST_PW (시험 계정 space·jyjang 비밀번호), 선택 PSQL (psql.exe 경로)
시험 데이터(시험 스키마·역할, 데이터 소스, 모델과 그 테이블·컬럼·제약·이력, 결재선·결재 문서·알림)는 끝에서 지운다.
"""
import base64, json, os, secrets, subprocess, sys, time, traceback
import requests

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
PSQL = os.environ.get("PSQL", r"C:\Program Files\PostgreSQL\13\bin\psql.exe")
PW = os.environ["DATAQ_TEST_PW"]
REASON = "재수집으로 실제 DB 구조로 바뀜"
EXEC_LOG = os.environ.get("QEXEC_LOG")      # 선택 — q-executor 로그 파일(STOMP 오류 확인)
results = []
cleanup = []
FX = {}


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
    r = subprocess.run([PSQL, "-h", "localhost", "-p", "25433", "-U", "admin", "-d", "postgres", "-w", "-At", "-c", sql],
                       capture_output=True, text=True, timeout=60, encoding="utf-8", errors="replace",
                       env=dict(os.environ, PGCLIENTENCODING="UTF8"))
    if r.returncode != 0:
        raise RuntimeError("psql 실패: " + (r.stderr or "")[:300])
    return (r.stdout or "").strip()


def login_api(user):
    s = requests.Session()
    r = s.post(BASE + "/login", data={"id": user, "password": base64.b64encode(PW.encode()).decode()},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 {user} {r.status_code}"
    return s


def post(s, path, body, timeout=30):
    r = s.post(BASE + path, json=body, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"http": r.status_code, "text": r.text[:300]}


def code(r):
    return r.get("resultCode")


def msg(r):
    return r.get("resultMessage") or ""


def q(v):
    return "'" + str(v).replace("'", "''") + "'"


def drop_model(dm):
    psql(f"delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where dm_id='{dm}')")
    psql(f"delete from quality.tb_aprv_doc where dm_id='{dm}'")
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_constraint", "tb_data_model_index",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_udp_val", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id='{dm}'")
    psql(f"delete from quality.tb_data_model where dm_id='{dm}'")


def save_attrs(s, dm, owner, obj, attrs):
    return post(s, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": obj, "objOwner": owner, "attrs": attrs})


def add_col(nm, kr, ln=10):
    return {"mode": "ADD", "attrNm": nm, "attrNmKr": kr, "dataType": "VARCHAR", "dataLen": ln, "nullableYn": "Y"}


def attr(owner, obj, col, cols="aprv_status||'|'||use_yn"):
    return psql(f"select {cols} from quality.tb_data_model_attr where dm_id='{FX['dm']}' and coalesce(obj_owner,'')='{owner}' "
                f"and obj_nm='{obj}' and attr_nm='{col}'")


def obj(owner, nm):
    return psql(f"select aprv_status||'|'||use_yn from quality.tb_data_model_obj where dm_id='{FX['dm']}' "
                f"and coalesce(obj_owner,'')='{owner}' and obj_nm='{nm}'")


def seq_of(change_type, obj_nm, col=None):
    w = f"dm_id='{FX['dm']}' and change_type='{change_type}' and obj_nm='{obj_nm}'"
    if col:
        w += f" and attr_nm='{col}'"
    v = psql(f"select max(change_seq) from quality.tb_data_model_change_history where {w}")
    assert v, f"이력 없음 {change_type} {obj_nm} {col}"
    return int(v)


def status_summary():
    """대기 이력 상태별 수, 결재 문서 상태, 요청자 반려 알림 수, 마지막 수집의 자동 반려 수"""
    dm = FX["dm"]
    st = psql(f"select coalesce(string_agg(aprv_status||'='||c, ',' order by aprv_status),'') from (select aprv_status, count(*) c "
              f"from quality.tb_data_model_change_history where dm_id='{dm}' and change_seq = any(array[{','.join(map(str, FX['pending']))}]) "
              f"group by aprv_status) x")
    doc = psql(f"select doc_stat from quality.tb_aprv_doc where doc_id='{FX['docId']}'")
    noti = psql(f"select count(*) from quality.tb_notification where user_id='jyjang' and noti_tp='APRV_REJECT' and cret_dt >= '{FX['start']}'")
    return st, doc, noti


def wait_clct(n_before):
    dm = FX["dm"]
    for _ in range(60):
        time.sleep(2)
        if int(psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is not null")) > n_before:
            break
    return psql(f"select clct_cmptn_yn||'|'||coalesce(obj_cnt::text,'')||'|'||coalesce(attr_cnt::text,'')||'|'||coalesce(auto_reject_cnt::text,'') "
                f"from quality.tb_data_model_clct where dm_id='{dm}' order by clct_start_dt desc, clct_end_dt desc limit 1")


def collect(sa, schemas, ds):
    dm = FX["dm"]
    assert code(post(sa, "/api/dm/saveDataModelSchemas", [{"dataModelId": dm, "schemaNm": s, "useYn": "Y"} for s in schemas])) == 200
    before = int(psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is not null"))
    r = post(sa, "/api/dm/collectDataModel", {"dataModelId": dm, "dataModelDsId": ds, "dataModelNm": FX["dmNm"]}, timeout=60)
    assert code(r) == 200, r
    return wait_clct(before)


def line(sa, on):
    if on:
        r = post(sa, "/api/aprv/line/save", {"reqTp": "DM_CHANGE", "lineNm": "ZZ110재수집", "useYn": "Y",
                                              "steps": [{"stepNm": "관리자", "aprvrTp": "ADMIN", "agreeYn": "N"}]})
    else:
        r = post(sa, "/api/aprv/line/save", {"reqTp": "DM_CHANGE", "lineNm": "ZZ110재수집", "useYn": "N", "steps": []})
    assert code(r) == 200, r


# ---------------------------------------------------------------- R1
def r1(sa, su):
    def _fixture():
        role = ("zz110rj" + TAG).lower()
        pw = "Zz" + secrets.token_hex(6)
        a, c = f"zz110rja{TAG}", f"zz110rjc{TAG}"
        psql(f"create role {role} login password '{pw}'")
        for s in (a, c):
            psql(f"create schema {s} authorization {role}")

        def _drop():
            for s in (a, c):
                psql(f"drop schema if exists {s} cascade")
            psql(f"drop role if exists {role}")
        cleanup.append(_drop)
        for ddl in (f"create table {a}.t_main (id int primary key, nm varchar(20), old_col varchar(10), del_col varchar(10), gone_col varchar(10))",
                    f"create table {a}.t_ref (id int primary key, main_id int)",
                    f"create table {a}.t_ren (id int primary key, c1 varchar(10))",
                    f"create table {a}.t_nopk (id int, nm varchar(10))",
                    f"create table {a}.t_pk (id int primary key)",
                    f"create table {a}.t_del (id int)",
                    f"create table {a}.t_gone (id int)",
                    f"create table {a}.t_pk2 (id int primary key)",
                    f"create table {a}.t_dbren (id int)",
                    f"create table {c}.t_bad (c1 int)"):
            psql(ddl)
            psql(f"alter table {ddl.split()[2]} owner to {role}")
        psql(f"create index ix_gone on {a}.t_main (gone_col)")
        psql(f"comment on column {a}.t_main.nm is '이름'")
        psql(f"comment on column {c}.t_bad.c1 is '{'가' * 300}'")    # ATTR_NM_KR(255) 를 넘어 컬럼 단계에서 실패
        # 사용자 정의 속성 정의(쓰지 않음 — 값만 시험) — 테이블 이름 변경·삭제 때 값이 따라가는지
        udp = "ZZ110RJUDP" + TAG
        psql(f"insert into quality.tb_udp_def (udp_id, target_tp, udp_nm, data_tp, required_yn, sort_order, use_yn, dflt_set_yn, cret_dt, cret_user_id) "
             f"values ('{udp}', 'OBJ', 'ZZ110RJ속성{TAG}', 'TEXT', 'N', 99, 'N', 'N', to_char(now(),'YYYYMMDDHH24MISS'), 'space')")
        cleanup.append(lambda: psql(f"delete from quality.tb_udp_def where udp_id='{udp}'"))
        FX["udp"] = udp
        ds = {}
        for k, p in (("ok", pw), ("bad", pw + "x")):
            dsn = f"ZZ110RJ_{k}_{TAG}"
            r = post(sa, "/api/sysinfo/createDataSource", {"dsn": dsn, "dsTp": 0, "dbmsTp": "PostgreSQL", "driverName": "PostgreSQL",
                                                           "svrAddr": "127.0.0.1", "port": 25433, "userId": role, "pwd": p, "dbName": "postgres"})
            assert code(r) == 200, r
            ds[k] = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
            cleanup.append(lambda i=ds[k]: psql(f"delete from ndata.tb_data_source where ds_id='{i}'"))
            cleanup.append(lambda i=ds[k]: psql(f"delete from quality.tb_gov_meta_db where ds_id='{i}'"))   # 수집이 남기는 DBMS 정보
        psql(f"update ndata.tb_data_source set conn_test_yn='Y' where ds_id in ('{ds['ok']}', '{ds['bad']}')")
        FX.update(role=role, a=a, c=c, ds=ds)
        nm = "ZZ110재수집모델_" + TAG
        r = post(sa, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds["ok"], "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT"})
        assert code(r) == 200, r
        FX["dm"] = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
        FX["dmNm"] = nm
        cleanup.append(lambda: drop_model(FX["dm"]))
        res = collect(sa, [a], ds["ok"])
        print(f"  첫 수집 완료|테이블|컬럼|자동 반려={res}")
        assert res == "Y|9|16|0", res
        # 승인된 모델 값(수집이 덮지 않는 칸) — 화면에서 정해 둔 한글명·개인정보 여부·테이블 설명·사용자 정의 속성 값
        dm = FX["dm"]
        psql(f"update quality.tb_data_model_attr set attr_nm_kr='옛컬럼', priv_yn='Y' where dm_id='{dm}' and obj_nm='t_main' and attr_nm='old_col'")
        psql(f"update quality.tb_data_model_attr set attr_nm_kr='씨원', priv_yn='Y' where dm_id='{dm}' and obj_nm='t_ren' and attr_nm='c1'")
        psql(f"update quality.tb_data_model_obj set obj_nm_kr='이름바꿀테이블', obj_desc='원래설명' where dm_id='{dm}' and obj_nm='t_ren'")
        psql(f"update quality.tb_data_model_obj set obj_nm_kr='디비테이블' where dm_id='{dm}' and obj_nm='t_dbren'")
        for t in ("t_ren", "t_gone"):
            psql(f"insert into quality.tb_udp_val (udp_id, dm_id, obj_owner, obj_nm, attr_nm, val, updt_dt, updt_user_id) "
                 f"values ('{FX['udp']}', '{dm}', '{a}', '{t}', '', '값_{t}', to_char(now(),'YYYYMMDDHH24MISS'), 'space')")
        idx = psql(f"select count(*) from quality.tb_data_model_index where dm_id='{dm}' and index_nm='ix_gone'")
        print(f"  gone_col 인덱스 행={idx}")
        assert idx == "1"
    step("R1. 준비 — 시험 스키마·테이블·데이터 소스, 첫 수집(대기 변경 없음 → 자동 반려 0)", _fixture)


# ---------------------------------------------------------------- R2
def r2(sa, su):
    a, dm = FX["a"], FX["dm"]

    def _changes():
        r = save_attrs(su, dm, a, "t_main", [
            add_col("zz_add", "추가컬럼"), add_col("zz_dba", "디비에도추가"), add_col("zz_ok", "승인될컬럼"),
            {"mode": "UPDATE", "origAttrNm": "nm", "attrNm": "nm", "dataLen": 50, "attrNmKr": "길이와한글명수정", "nullableYn": "Y", "pkYn": "N", "fkYn": "N"},
            {"mode": "UPDATE", "origAttrNm": "old_col", "attrNm": "new_col", "attrNmKr": "새컬럼", "nullableYn": "Y", "pkYn": "N", "fkYn": "N"},
            {"mode": "DELETE", "attrNm": "del_col"}, {"mode": "DELETE", "attrNm": "gone_col"}])
        assert code(r) == 200, r
        # 관리자가 승인한 일반 사용자 변경, 관리자 직접 변경 — 자동 반려 대상이 아니다
        ok = seq_of("ADD_ATTR", "t_main", "ZZ_OK")
        assert code(post(sa, "/api/dmApproval/approve", {"changeSeqList": [ok]})) == 200
        assert code(save_attrs(sa, dm, a, "t_main", [add_col("zz_admin", "관리자컬럼")])) == 200
        FX["approved"] = [ok, seq_of("ADD_ATTR", "t_main", "ZZ_ADMIN")]
        assert code(post(su, "/api/dm/addObj", {"dataModelId": dm, "objOwner": a, "objNm": "t_new", "objNmKr": "새테이블"})) == 200
        assert code(save_attrs(su, dm, a, "t_new", [add_col("c1", "새컬럼")])) == 200
        r = post(su, "/api/dm/createFk", {"dataModelId": dm, "objOwner": a, "tableNm": "t_ref", "refOwner": a, "refTableNm": "t_main",
                                          "mappings": [{"ownAttrNm": "main_id", "refAttrNm": "id"}]})
        assert code(r) == 200, r
        FX["fk"] = r["contents"]
        r = post(su, "/api/dm/updateObj", {"dataModelId": dm, "origObjNm": "t_ren", "objNm": "t_ren2", "objOwner": a, "origObjOwner": a, "objNmKr": "이름변경", "objDesc": "설명변경"})
        assert code(r) == 200, r
        # PK 추가(DB 에 없음)·PK 삭제(DB 에 남음)·테이블 삭제(DB 에 남음)
        r = post(su, "/api/dm/createPk", {"dataModelId": dm, "objOwner": a, "tableNm": "t_nopk", "pkName": "PK_T_NOPK", "columns": ["id"]})
        assert code(r) == 200, r
        assert code(post(su, "/api/dm/deletePk", {"dataModelId": dm, "objOwner": a, "tableNm": "t_pk", "pkName": "t_pk_pkey"})) == 200
        assert code(post(su, "/api/dm/deleteObj", {"dataModelId": dm, "objOwner": a, "objNm": "t_del"})) == 200
        # 권고 채택(10-28) — 수집에 없는 컬럼·테이블의 수정(전부 신청 전 값), DB 에도 이름이 바뀐 테이블(함께 바꾼 한글명만 신청 전 값),
        # DB 에서도 지워질 컬럼·테이블·PK 의 삭제 대기(물리 삭제)
        assert code(save_attrs(su, dm, a, "t_main", [{"mode": "UPDATE", "origAttrNm": "ZZ_OK", "attrNm": "zz_ok2", "attrNmKr": "바꾼이름",
                                                      "dataLen": 30, "nullableYn": "Y", "pkYn": "N", "fkYn": "N"}])) == 200
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": a, "objNm": "t_model", "objNmKr": "모델전용"})) == 200
        assert code(save_attrs(sa, dm, a, "t_model", [add_col("zz_m1", "모델컬럼")])) == 200
        for orig, new, kr in (("t_model", "t_model2", "모델이름변경"), ("t_dbren", "t_dbren2", "디비이름변경")):
            r = post(su, "/api/dm/updateObj", {"dataModelId": dm, "origObjNm": orig, "objNm": new, "objOwner": a, "origObjOwner": a, "objNmKr": kr})
            assert code(r) == 200, r
        assert code(post(su, "/api/dm/deleteObj", {"dataModelId": dm, "objOwner": a, "objNm": "t_gone"})) == 200
        assert code(post(su, "/api/dm/deletePk", {"dataModelId": dm, "objOwner": a, "tableNm": "t_pk2", "pkName": "t_pk2_pkey"})) == 200
        # 신청 — 결재선을 켠 신청(결재 문서), 끈 신청(문서 없음)
        add, nm = seq_of("ADD_ATTR", "t_main", "ZZ_ADD"), seq_of("MODIFY_ATTR", "t_main", "nm")
        line(sa, True)
        r = post(su, "/api/dmApproval/submit", {"changeSeqList": [add, nm]})
        line(sa, False)
        assert code(r) == 200, r
        sub = json.loads(r["contents"])["submissionId"]
        FX["docId"] = psql(f"select doc_id from quality.tb_aprv_doc where req_tp='DM_CHANGE' and target_id='{sub}'")
        dl = seq_of("DEL_ATTR", "t_main", "del_col")
        assert code(post(su, "/api/dmApproval/submit", {"changeSeqList": [dl]})) == 200
        FX["pending"] = [int(x) for x in psql(f"select string_agg(change_seq::text, ',' order by change_seq) from quality.tb_data_model_change_history "
                                               f"where dm_id='{dm}' and aprv_status in ('DRAFT','SUBMITTED')").split(",")]
        rows = psql(f"select string_agg(change_type||':'||obj_nm||coalesce('.'||attr_nm,'')||':'||aprv_status, ', ' order by change_seq) "
                    f"from quality.tb_data_model_change_history where change_seq = any(array[{','.join(map(str, FX['pending']))}])")
        doc = psql(f"select doc_stat from quality.tb_aprv_doc where doc_id='{FX['docId']}'")
        print(f"  대기 이력 {len(FX['pending'])}건: {rows}\n  결재 문서={doc} / 승인된 이력={FX['approved']}")
        assert len(FX["pending"]) == 18 and doc == "PROGRESS"
        assert attr(a, "t_main", "NEW_COL", "attr_nm_kr") == "새컬럼" and attr(a, "t_main", "nm", "attr_nm_kr") == "길이와한글명수정"
        assert attr(a, "t_main", "ZZ_OK2", "attr_nm_kr||'|'||data_len") == "바꾼이름|30" and attr(a, "t_main", "gone_col") == "APPROVED|N"
        assert obj(a, "t_model2") == "APPROVED|Y" and obj(a, "t_dbren2") == "APPROVED|Y" and obj(a, "t_gone") == "APPROVED|N"
        js = psql(f"select before_json::json->>'objNmKr' from quality.tb_data_model_change_history where dm_id='{dm}' and change_type='MODIFY_OBJ' and obj_nm='t_ren2'")
        print(f"  테이블 이름 변경 이력의 변경 전 한글명={js}")
        assert js == "이름바꿀테이블"
        assert attr(a, "t_main", "nm", "data_len") == "50" and attr(a, "t_main", "NEW_COL") == "APPROVED|Y" and attr(a, "t_main", "del_col") == "APPROVED|N"
        assert obj(a, "t_ren2") == "APPROVED|Y" and obj(a, "t_new") == "DRAFT|Y" and obj(a, "t_del") == "APPROVED|N"
        assert attr(a, "t_ref", "main_id", "fk_yn||'|'||coalesce(fk_parent_obj_nm,'')") == "Y|t_main"
        assert attr(a, "t_nopk", "id", "pk_yn") == "Y" and attr(a, "t_pk", "id", "pk_yn") == "N"
        assert psql(f"select string_agg(distinct aprv_status||'|'||use_yn, ',') from quality.tb_data_model_constraint where dm_id='{dm}' "
                    f"and constraint_nm in ('PK_T_NOPK','t_pk_pkey')") == "APPROVED|N,DRAFT|Y"
    step("R2. 일반 사용자 변경 18건(DRAFT 15·SUBMITTED 3 — 결재 문서 1), 관리자 승인 변경 2건", _changes)


# ---------------------------------------------------------------- R3
def r3(sa, su):
    a, c, dm = FX["a"], FX["c"], FX["dm"]

    def _bad_pw():
        psql(f"update quality.tb_data_model set dm_ds_id='{FX['ds']['bad']}' where dm_id='{dm}'")
        rows0 = int(psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}'"))
        try:
            r = post(sa, "/api/dm/collectDataModel", {"dataModelId": dm, "dataModelDsId": FX["ds"]["bad"], "dataModelNm": FX["dmNm"]}, timeout=60)
            assert code(r) == 200, r
            # 접속 단계에서 실패하면 수집 이력 행을 만들기 전이라 행이 없다. 만든 뒤 실패하면 미완료(N)로 닫힌다
            res = "(수집 이력 없음)"
            for _ in range(15):
                time.sleep(2)
                if int(psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}'")) > rows0 and \
                        psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is null") == "0":
                    res = wait_clct(0)
                    break
        finally:
            psql(f"update quality.tb_data_model set dm_ds_id='{FX['ds']['ok']}' where dm_id='{dm}'")
        st, doc, noti = status_summary()
        print(f"  틀린 비밀번호 수집 완료|테이블|컬럼|자동 반려={res} / 대기 이력={st} 결재 문서={doc} 반려 알림={noti}")
        assert res in ("(수집 이력 없음)", "N|||") and st == "DRAFT=15,SUBMITTED=3" and doc == "PROGRESS" and noti == "0"
    step("R3-1. 수집 실패(데이터 소스 비밀번호 틀림) → 아무것도 반려되지 않음", _bad_pw)

    def model_sig():
        # 모델 행 전체(테이블·컬럼·인덱스·제약조건)의 지문 — 실패한 수집 앞뒤로 같아야 한다
        return psql("select " + "||'/'||".join(
            f"(select count(*)||':'||coalesce(md5(string_agg(t::text, '|' order by t::text)),'') from quality.{tb} t where dm_id='{dm}')"
            for tb in ("tb_data_model_obj", "tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint")))

    def _attr_fail():
        # DBA 가 삭제 신청과 같이 실제 DB 에서도 지운 경우(컬럼 — 인덱스도 함께 없어짐, 테이블, PK), DB 에서도 이름을 바꾼 테이블
        psql(f"alter table {a}.t_main drop column gone_col")
        psql(f"drop table {a}.t_gone")
        psql(f"alter table {a}.t_pk2 drop constraint t_pk2_pkey")
        psql(f"alter table {a}.t_dbren rename to t_dbren2")
        sig0 = model_sig()
        res = collect(sa, [a, c], FX["ds"]["ok"])
        st, doc, noti = status_summary()
        sig1 = model_sig()
        print(f"  컬럼 단계 실패 수집={res} / 대기 이력={st} 결재 문서={doc} 반려 알림={noti} / 모델 행 그대로={sig0 == sig1}")
        assert res == "N|||" and st == "DRAFT=15,SUBMITTED=3" and doc == "PROGRESS" and noti == "0"
        # 114 권고 채택(10-28) — 수집 한 트랜잭션: 테이블 단계(c.t_bad, a.t_dbren2 MERGE)도 되돌려져 모델 행이 하나도 바뀌지 않는다
        assert sig0 == sig1 and obj(c, "t_bad") == ""
        assert code(post(sa, "/api/dm/saveDataModelSchemas", [{"dataModelId": dm, "schemaNm": a, "useYn": "Y"},
                                                              {"dataModelId": dm, "schemaNm": c, "useYn": "N"}])) == 200
        # 읽지 않는 c 스키마의 테이블을 삭제 대기로 — 다음 수집은 c 를 읽지 않으므로 감춘 채 남아야 한다(물리 삭제 안 함).
        # 실패한 수집이 넣지 않으므로 관리자가 모델에 직접 추가해 둔다
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": c, "objNm": "t_bad", "objNmKr": "읽지않는스키마"})) == 200
        assert obj(c, "t_bad") == "APPROVED|Y"
        assert code(post(su, "/api/dm/deleteObj", {"dataModelId": dm, "objOwner": c, "objNm": "t_bad"})) == 200
        FX["pending"].append(seq_of("DEL_OBJ", "t_bad"))
    step("R3-2. 수집 실패(테이블 단계 뒤 컬럼 단계) → 아무것도 반려되지 않고 모델 행도 그대로", _attr_fail)


# ---------------------------------------------------------------- R4 (화면)
def r4(sa, su):
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from edgeopt import headless

    a, dm = FX["a"], FX["dm"]

    def new_driver():
        opts = webdriver.EdgeOptions()
        opts.add_argument("--log-level=3")
        d = webdriver.Edge(options=headless(opts))
        d.set_window_size(1600, 1000)
        return d

    def login(d, user):
        d.delete_all_cookies()
        d.get(BASE)
        WebDriverWait(d, 15).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']"))).send_keys(user)
        d.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(PW)
        d.find_element(By.CSS_SELECTOR, "button[type='submit'], .v-btn").click()
        WebDriverWait(d, 15).until(lambda x: "/app/main" in x.current_url)
        time.sleep(3)

    def nav(d, nav_id, group_id):
        if not d.find_elements(By.ID, nav_id):
            d.execute_script("arguments[0].click();", d.find_element(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header"))
            time.sleep(1)
        el = WebDriverWait(d, 15).until(EC.presence_of_element_located((By.ID, nav_id)))
        d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)
        time.sleep(2)

    def swal_text(d, has):
        def _t(x):
            for e in x.find_elements(By.CSS_SELECTOR, ".swal2-popup"):
                if e.is_displayed() and has in e.text:
                    return e.text
            return None
        return WebDriverWait(d, 10).until(_t)

    adm, req = new_driver(), new_driver()
    try:
        # 요청자는 다른 창에 로그인해 둔다 — 알림 토스트(실시간)를 모은다
        login(req, "jyjang")
        badge0 = req.execute_script("var b=document.querySelector('#headerNotiBtn .v-badge__badge'); return b && b.offsetParent ? b.innerText : '0';")
        req.execute_script("window.__toasts=[]; new MutationObserver(function(){document.querySelectorAll('.swal2-toast').forEach(function(t){"
                           "var s=t.innerText; if(window.__toasts.indexOf(s)<0) window.__toasts.push(s);});})"
                           ".observe(document.body,{childList:true,subtree:true});")
        # DBA 가 신청과 같은 컬럼을 실제 DB 에 만든 경우 — 수집되면 그 컬럼은 수집 값(APPROVED)으로
        psql(f'alter table {a}.t_main add column "ZZ_DBA" varchar(10)')
        login(adm, "space")

        def _ui_collect():
            nav(adm, "nav_datamodelCollection", "dmGroup")
            cell = WebDriverWait(adm, 15).until(EC.presence_of_element_located(
                (By.XPATH, f"//div[@id='tab_datamodelCollection']//span[normalize-space()='{FX['dmNm']}']")))
            adm.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", cell)
            time.sleep(3)
            adm.execute_script("arguments[0].click();", adm.find_element(By.XPATH, "//div[@id='tab_datamodelCollection']//button[contains(., '수집')]"))
            t = swal_text(adm, "수집 대상 스키마")
            for b in adm.find_elements(By.CSS_SELECTOR, ".swal2-confirm"):
                if b.is_displayed():
                    b.click()
            WebDriverWait(adm, 120).until(lambda x: "수집이 완료되었습니다" in x.page_source or "수집에 실패했습니다" in x.page_source)
            box = next(e.text for e in adm.find_elements(By.CSS_SELECTOR, ".v-dialog--active") if "데이터 모델 수집 진행" in e.text)
            print(f"  확인창 자동 반려 안내={'승인되지 않은 변경(신청 전·신청 중)은 자동 반려됩니다' in t}\n  진행 창:\n    " + box.replace("\n", "\n    "))
            assert "승인되지 않은 변경(신청 전·신청 중)은 자동 반려됩니다" in t
            assert "승인되지 않은 변경 자동 반려 19건 (" + REASON + ")" in box and "수집이 완료되었습니다" in box
            for b in adm.find_elements(By.XPATH, "//div[contains(@class,'v-dialog--active')]//button[normalize-space()='닫기']"):
                adm.execute_script("arguments[0].click();", b)
            time.sleep(2)
            row = next(r.text for r in adm.find_elements(By.CSS_SELECTOR, "#dataModel_table tbody tr") if FX["dmNm"] in r.text)
            hdr = adm.find_element(By.CSS_SELECTOR, "#dataModel_table thead").text
            print(f"  모델 목록 머리글에 '자동 반려 건수'={'자동 반려 건수' in hdr} / 행={row!r}")
            assert "자동 반려 건수" in hdr and row.split()[-1] == "19"
        step("R4-1. 화면 재수집 — 확인창 안내, 진행 창에 '자동 반려 19건', 모델 목록의 자동 반려 건수", _ui_collect)

        def _hist():
            dm_ = FX["dm"]
            st = psql(f"select string_agg(distinct aprv_status||'|'||aprv_user_id||'|'||aprv_comment||'|'||(aprv_dt is not null), ',') "
                      f"from quality.tb_data_model_change_history where change_seq = any(array[{','.join(map(str, FX['pending']))}])")
            doc = psql(f"select doc_stat||'|'||(end_dt is not null) from quality.tb_aprv_doc where doc_id='{FX['docId']}'")
            stp = psql(f"select string_agg(distinct act_stat||'|'||act_user_id||'|'||act_cmnt, ',') from quality.tb_aprv_doc_step where doc_id='{FX['docId']}'")
            clct = wait_clct(0)
            print(f"  대기였던 이력={st}\n  결재 문서={doc} / 단계={stp} / 마지막 수집 완료|테이블|컬럼|자동 반려={clct}")
            assert st == f"REJECTED|system|{REASON}|true"
            assert doc == "REJECTED|true" and stp == f"REJECTED|system|{REASON}"
            assert clct == "Y|8|15|19"
            hl = post(sa, "/api/dm/getDataModelHistoryList", {"schId": dm_})
            cnts = [(h["clctCmptnYn"], h["autoRejectCnt"]) for h in hl]
            print(f"  수집이력 API (완료, 자동 반려)={cnts}")
            assert cnts[0] == ("Y", 19) and ("N", None) in cnts and ("Y", 0) in cnts
        step("R4-2. 대기 이력 19건 REJECTED(반려자 system·사유·일시), 결재 문서·단계 반려, 수집이력 자동 반려 19", _hist)

        def _model():
            rows = {
                "ZZ_ADD(추가·신청)": attr(a, "t_main", "ZZ_ADD"),
                "nm 길이(수정·신청)": attr(a, "t_main", "nm", "data_len::text"),
                "old_col(이름 변경 전)": attr(a, "t_main", "old_col"),
                "NEW_COL(이름 변경 후)": attr(a, "t_main", "NEW_COL") or "(없음)",
                "del_col(삭제·신청)": attr(a, "t_main", "del_col"),
                "ZZ_DBA(추가, DB 에도 있음)": attr(a, "t_main", "ZZ_DBA"),
                "t_new(테이블 추가)": obj(a, "t_new"),
                "t_new.C1": attr(a, "t_new", "C1"),
                "t_ren2(이름 변경 후)": obj(a, "t_ren2") or "(없음)",
                "t_ren2 컬럼": psql(f"select count(*) from quality.tb_data_model_attr where dm_id='{dm}' and obj_nm='t_ren2'"),
                "t_ren": obj(a, "t_ren"),
                "t_ren 컬럼": psql(f"select string_agg(attr_nm||':'||aprv_status, ',' order by attr_nm) from quality.tb_data_model_attr "
                                   f"where dm_id='{dm}' and obj_nm='t_ren'"),
                "FK 제약": psql(f"select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and constraint_nm='{FX['fk']}'"),
                "t_ref.main_id FK(신청 전 값 = FK 아닌 id 와 같아야)": attr(a, "t_ref", "main_id", "coalesce(fk_yn,'(null)')||'|'||coalesce(fk_parent_obj_nm,'')") + " / id " + attr(a, "t_ref", "id", "coalesce(fk_yn,'(null)')||'|'||coalesce(fk_parent_obj_nm,'')"),
                "PK_T_NOPK(PK 추가) 제약·인덱스": psql(f"select (select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and constraint_nm='PK_T_NOPK')"
                                                 f"||'/'||(select count(*) from quality.tb_data_model_index where dm_id='{dm}' and index_nm='PK_T_NOPK')"),
                "t_nopk.id PK(같은 테이블 PK 아닌 nm 과 같아야)": attr(a, "t_nopk", "id", "coalesce(pk_yn,'(null)')") + " / nm " + attr(a, "t_nopk", "nm", "coalesce(pk_yn,'(null)')"),
                "t_pk_pkey(PK 삭제) 제약·인덱스": psql(f"select (select string_agg(distinct aprv_status||'|'||use_yn, ',') from quality.tb_data_model_constraint "
                                                 f"where dm_id='{dm}' and constraint_nm='t_pk_pkey')||'/'||(select string_agg(distinct aprv_status||'|'||use_yn, ',') "
                                                 f"from quality.tb_data_model_index where dm_id='{dm}' and index_nm='t_pk_pkey')"),
                "t_pk.id PK": attr(a, "t_pk", "id", "pk_yn"),
                "t_del(테이블 삭제)": obj(a, "t_del") + " / " + attr(a, "t_del", "id"),
                # 권고 채택(10-28) 1 — 수집이 덮지 않는 칸은 신청 전 값
                "nm 한글명(수정 — 수집됨)": attr(a, "t_main", "nm", "attr_nm_kr"),
                "old_col 한글명·개인정보(이름 변경 사본에서 옮김)": attr(a, "t_main", "old_col", "attr_nm_kr||'|'||priv_yn"),
                "ZZ_OK(수집에 없는 컬럼 수정 — 전부 되돌림)": attr(a, "t_main", "ZZ_OK", "attr_nm_kr||'|'||data_len||'|'||aprv_status") + " / ZZ_OK2 "
                                                     + (attr(a, "t_main", "ZZ_OK2") or "(없음)"),
                "t_ren 한글명·설명": psql(f"select obj_nm_kr||'|'||obj_desc from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='t_ren'"),
                "t_ren.c1 한글명·개인정보": attr(a, "t_ren", "c1", "attr_nm_kr||'|'||priv_yn"),
                "t_ren 속성 값": psql(f"select string_agg(obj_nm||':'||val, ',') from quality.tb_udp_val where dm_id='{dm}' and obj_nm like 't_ren%'"),
                "t_model(수집에 없는 테이블 이름 변경 — 되돌림)": psql(f"select obj_nm||'|'||obj_nm_kr||'|'||aprv_status from quality.tb_data_model_obj "
                                                           f"where dm_id='{dm}' and obj_nm like 't_model%'")
                                                      + " / " + psql(f"select string_agg(obj_nm||'.'||attr_nm, ',') from quality.tb_data_model_attr "
                                                                     f"where dm_id='{dm}' and obj_nm like 't_model%'"),
                "t_dbren2(DB 에도 이름 바뀜 — 한글명만 되돌림)": psql(f"select string_agg(obj_nm||'|'||coalesce(obj_nm_kr,'')||'|'||aprv_status, ',') "
                                                           f"from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm like 't_dbren%'"),
                # 권고 채택(10-28) 4 — DB 에서도 지워진 삭제 대기는 물리 삭제, 읽지 않은 스키마는 감춘 채
                "gone_col 컬럼·인덱스": psql(f"select (select count(*) from quality.tb_data_model_attr where dm_id='{dm}' and attr_nm='gone_col')"
                                         f"||'/'||(select count(*) from quality.tb_data_model_index where dm_id='{dm}' and index_nm='ix_gone')"),
                "t_gone 테이블·컬럼·속성 값": psql(f"select (select count(*) from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='t_gone')"
                                              f"||'/'||(select count(*) from quality.tb_data_model_attr where dm_id='{dm}' and obj_nm='t_gone')"
                                              f"||'/'||(select count(*) from quality.tb_udp_val where dm_id='{dm}' and obj_nm='t_gone')"),
                "t_pk2_pkey 제약·인덱스": psql(f"select (select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and constraint_nm='t_pk2_pkey')"
                                            f"||'/'||(select count(*) from quality.tb_data_model_index where dm_id='{dm}' and index_nm='t_pk2_pkey')"),
                "t_bad(읽지 않은 스키마)": obj(FX["c"], "t_bad"),
                "대기 행": psql(f"select (select count(*) from quality.tb_data_model_attr where dm_id='{dm}' and aprv_status in ('DRAFT','SUBMITTED'))"
                              f"||'/'||(select count(*) from quality.tb_data_model_obj where dm_id='{dm}' and aprv_status in ('DRAFT','SUBMITTED'))"
                              f"||'/'||(select count(*) from quality.tb_data_model_constraint where dm_id='{dm}' and aprv_status in ('DRAFT','SUBMITTED'))"),
            }
            for k, v in rows.items():
                print(f"  {k} = {v}")
            assert rows["ZZ_ADD(추가·신청)"] == "REJECTED|Y" and rows["nm 길이(수정·신청)"] == "20"
            assert rows["old_col(이름 변경 전)"] == "APPROVED|Y" and rows["NEW_COL(이름 변경 후)"] == "(없음)"
            assert rows["del_col(삭제·신청)"] == "APPROVED|Y" and rows["ZZ_DBA(추가, DB 에도 있음)"] == "APPROVED|Y"
            assert rows["t_new(테이블 추가)"] == "REJECTED|Y" and rows["t_new.C1"] == "REJECTED|Y"
            assert rows["t_ren2(이름 변경 후)"] == "(없음)" and rows["t_ren2 컬럼"] == "0"
            assert rows["t_ren"] == "APPROVED|Y" and rows["t_ren 컬럼"] == "c1:APPROVED,id:APPROVED"
            assert rows["FK 제약"] == "0" and rows["t_ref.main_id FK(신청 전 값 = FK 아닌 id 와 같아야)"] in ("N| / id N|", "(null)| / id (null)|") and rows["대기 행"] == "0/0/0"
            assert rows["PK_T_NOPK(PK 추가) 제약·인덱스"] == "0/0" and rows["t_nopk.id PK(같은 테이블 PK 아닌 nm 과 같아야)"] in ("N / nm N", "(null) / nm (null)")
            assert rows["t_pk_pkey(PK 삭제) 제약·인덱스"] == "APPROVED|Y/APPROVED|Y" and rows["t_pk.id PK"] == "Y"
            assert rows["t_del(테이블 삭제)"] == "APPROVED|Y / APPROVED|Y"
            assert rows["nm 한글명(수정 — 수집됨)"] == "이름" and rows["old_col 한글명·개인정보(이름 변경 사본에서 옮김)"] == "옛컬럼|Y"
            assert rows["ZZ_OK(수집에 없는 컬럼 수정 — 전부 되돌림)"] == "승인될컬럼|10|APPROVED / ZZ_OK2 (없음)"
            assert rows["t_ren 한글명·설명"] == "이름바꿀테이블|원래설명" and rows["t_ren.c1 한글명·개인정보"] == "씨원|Y"
            assert rows["t_ren 속성 값"] == "t_ren:값_t_ren"
            assert rows["t_model(수집에 없는 테이블 이름 변경 — 되돌림)"] == "t_model|모델전용|APPROVED / t_model.ZZ_M1"
            assert rows["t_dbren2(DB 에도 이름 바뀜 — 한글명만 되돌림)"] == "t_dbren2|디비테이블|APPROVED"
            assert rows["gone_col 컬럼·인덱스"] == "0/0" and rows["t_gone 테이블·컬럼·속성 값"] == "0/0/0" and rows["t_pk2_pkey 제약·인덱스"] == "0/0"
            assert rows["t_bad(읽지 않은 스키마)"] == "APPROVED|N"
            # 관리자 화면 목록(남의 반려 행은 안 보임) = 실제 DB 컬럼 + 승인된 모델 전용 컬럼
            vis = sorted(x["attrNm"] for x in sa.get(BASE + "/api/dm/getDataModelAttrListByClctId", params={"clctId": dm}, timeout=30).json()
                         if x["objNm"] == "t_main")
            print(f"  관리자 컬럼 목록 t_main={vis}")
            assert vis == sorted(["id", "nm", "old_col", "del_col", "ZZ_DBA", "ZZ_OK", "ZZ_ADMIN"])
        step("R4-3. 모델은 실제 DB 구조 — 수집한 칸은 수집 값, 수집이 덮지 않는 칸·수집에 없는 행은 신청 전 값, 추가 대기 반려, DB 에서도 지운 삭제 대기는 물리 삭제", _model)

        def _noti():
            n = psql(f"select string_agg(title||' / '||coalesce(msg,'')||' / '||coalesce(link_tp,'-'), E'\\n' order by noti_id) "
                     f"from quality.tb_notification where user_id='jyjang' and noti_tp='APRV_REJECT' and cret_dt >= '{FX['start']}'")
            print("  요청자 반려 알림:\n    " + (n or "").replace("\n", "\n    "))
            lines = (n or "").split("\n")
            assert len(lines) == 2
            assert any(l.startswith(f"반려: 데이터 모델 변경 {FX['dmNm']} 변경 2건 / 1/1 단계 (관리자) 시스템 — {REASON} / APRV_DOC") for l in lines)
            assert f"반려: 데이터 모델 변경 {FX['dmNm']} / 시스템 — {REASON} (17건) / -" in lines
            time.sleep(2)
            toasts = req.execute_script("return window.__toasts;")
            badge = req.execute_script("var b=document.querySelector('#headerNotiBtn .v-badge__badge'); return b && b.offsetParent ? b.innerText : '0';")
            print(f"  요청자 창 실시간 토스트={toasts} / 종 배지 {badge0} → {badge}")
            assert any("변경 2건" in t and REASON in t for t in toasts) and any("(17건)" in t for t in toasts)
            assert int(badge.replace("+", "")) >= int(badge0.replace("+", "") or 0) + 2
            req.execute_script("document.getElementById('headerNotiBtn').click();")
            time.sleep(2)
            items = [e.text for e in req.find_elements(By.CSS_SELECTOR, ".noti-card .v-list-item") if REASON in e.text]
            print(f"  종 목록의 자동 반려 알림 {len(items)}건")
            assert len(items) == 2
        step("R4-4. 요청자 알림 — 결재 문서 반려 1건 + 문서 없는 반려 1건(17건), 다른 창에 실시간 토스트·종 배지·목록", _noti)

        def _hist_screen():
            nav(adm, "nav_datamodelHistory", "dmGroup")
            box = WebDriverWait(adm, 15).until(EC.presence_of_element_located((By.CSS_SELECTOR, "#tab_datamodelHistory input[placeholder='모델 선택']")))
            box.click()
            box.send_keys(FX["dmNm"])
            time.sleep(1)
            WebDriverWait(adm, 15).until(EC.presence_of_element_located(
                (By.XPATH, f"//div[contains(@class,'v-list-item__title')][normalize-space()='{FX['dmNm']}']"))).click()
            adm.find_element(By.XPATH, "//div[@id='tab_datamodelHistory']//button[contains(., '조회')]").click()
            time.sleep(2)
            hdr = adm.find_element(By.CSS_SELECTOR, "#dmHistory_table thead").text.replace("\n", " ")
            rows = [r.text.split() for r in adm.find_elements(By.CSS_SELECTOR, "#dmHistory_table tbody tr")]
            print(f"  수집이력 머리글={hdr!r}\n  행(마지막 칸=자동 반려)={[r[-3:] for r in rows]}")
            assert "자동 반려 건수" in hdr and rows[0][-1] == "19" and rows[0][-3:-1] == ["8", "15"]
        step("R4-5. 수집이력 화면 — '자동 반려 건수' 열, 마지막 수집 19건", _hist_screen)
    finally:
        adm.quit()
        req.quit()


# ---------------------------------------------------------------- R5
def r5(sa, su):
    a, dm = FX["a"], FX["dm"]

    def _approved():
        st = psql(f"select string_agg(change_seq||':'||aprv_status||'|'||aprv_user_id, ',' order by change_seq) "
                  f"from quality.tb_data_model_change_history where change_seq = any(array[{','.join(map(str, FX['approved']))}])")
        rows = attr(a, "t_main", "ZZ_OK") + "," + attr(a, "t_main", "ZZ_ADMIN")
        again = post(sa, "/api/dmApproval/approve", {"changeSeqList": [FX["pending"][0]]})
        print(f"  승인된 이력={st} / 행={rows} / 자동 반려된 건 승인={code(again)} {msg(again)[:60]}")
        assert st == ",".join(f"{s}:APPROVED|space" for s in FX["approved"]) and rows == "APPROVED|Y,APPROVED|Y"
        assert code(again) == 500 and "이미 처리된 항목입니다" in msg(again)
    step("R5-1. 이미 승인된 변경은 그대로, 자동 반려된 건은 다시 승인할 수 없음", _approved)

    def _readd():
        r1 = post(sa, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": "t_main", "objOwner": a, "keepNmCase": True,
                                            "attrs": [add_col("gone_col", "다시추가")]})
        r2 = post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": a, "objNm": "t_gone", "objNmKr": "다시추가"})
        print(f"  물리 삭제된 이름으로 다시 추가 — 컬럼 gone_col={code(r1)} {msg(r1)[:40]} / 테이블 t_gone={code(r2)} {msg(r2)[:40]}")
        assert code(r1) == 200 and code(r2) == 200 and attr(a, "t_main", "gone_col") == "APPROVED|Y" and obj(a, "t_gone") == "APPROVED|Y"
    step("R5-2. 물리 삭제된 컬럼·테이블은 같은 이름으로 다시 추가할 수 있다 (권고 채택 10-28)", _readd)

    def _zero():
        res = collect(sa, [a], FX["ds"]["ok"])
        print(f"  대기 변경 없이 다시 수집={res}")
        assert res == "Y|8|15|0"
    step("R5-3. 대기 변경이 없으면 자동 반려 0건", _zero)

    def _stomp():
        if not EXEC_LOG:
            print("  QEXEC_LOG 없음 — 건너뜀")
            return
        with open(EXEC_LOG, encoding="utf-8", errors="replace") as f:
            bad = [l.strip() for l in f if ("STOMP" in l or "Stomp" in l or "stomp" in l) and ("ERROR" in l or "Exception" in l or "lost" in l.lower())]
        print(f"  q-executor 로그 STOMP 오류 {len(bad)}줄 {bad[:3]}")
        assert not bad
    step("R5-4. q-executor 로그에 STOMP 연결 오류 없음", _stomp)


def main():
    sa = login_api("space")
    su = login_api("jyjang")
    FX["start"] = time.strftime("%Y%m%d%H%M%S")
    assert psql("select count(*) from quality.tb_aprv_line where use_yn='Y'") == "0", "켜진 결재선이 있다 — 시험이 결재선을 켜고 끄므로 멈춘다"
    assert psql("select count(*) from quality.tb_aprv_line where req_tp='DM_CHANGE'") == "0", "DM_CHANGE 결재선 행이 있다 — 시험이 지우므로 멈춘다"

    def _line_clean():
        psql("delete from quality.tb_aprv_line_step where req_tp='DM_CHANGE'")
        psql("delete from quality.tb_aprv_line where req_tp='DM_CHANGE'")
    cleanup.append(_line_clean)
    try:
        for fn in (r1, r2, r3, r4, r5):
            fn(sa, su)
            if any(st == "FAIL" for _, st in results) and fn in (r1, r2):
                break
    finally:
        print("\n=== 정리")
        for fn in reversed(cleanup):
            try:
                fn()
            except Exception as e:
                print("  정리 실패:", e)
        psql(f"delete from quality.tb_notification where cret_dt >= '{FX['start']}'")
        left = psql(f"select (select count(*) from quality.tb_data_model where dm_nm like 'ZZ110재수집%')||'/'||"
                    f"(select count(*) from quality.tb_data_model_attr a where not exists (select 1 from quality.tb_data_model m where m.dm_id=a.dm_id))||'/'||"
                    f"(select count(*) from quality.tb_data_model_change_history h where h.change_dt >= '{FX['start']}' "
                    f"and not exists (select 1 from quality.tb_data_model m where m.dm_id=h.dm_id))||'/'||"
                    f"(select count(*) from ndata.tb_data_source where dsn like 'ZZ110RJ%')||'/'||"
                    f"(select count(*) from quality.tb_aprv_doc where req_dt >= '{FX['start']}')||'/'||"
                    f"(select count(*) from quality.tb_aprv_line where req_tp='DM_CHANGE')||'/'||"
                    f"(select count(*) from quality.tb_notification where cret_dt >= '{FX['start']}')||'/'||"
                    f"(select count(*) from pg_namespace where nspname like 'zz110rj%')||'/'||"
                    f"(select count(*) from pg_roles where rolname like 'zz110rj%')||'/'||"
                    f"(select count(*) from quality.tb_udp_def where udp_id like 'ZZ110RJ%')||'/'||"
                    f"(select count(*) from quality.tb_udp_val v where not exists (select 1 from quality.tb_data_model m where m.dm_id=v.dm_id))")
        print(f"  남은 시험 데이터 모델/고아 컬럼/고아 이력/데이터 소스/결재 문서/결재선/알림/스키마/역할/속성 정의/고아 속성 값 = {left}")
        FX["left"] = left


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
