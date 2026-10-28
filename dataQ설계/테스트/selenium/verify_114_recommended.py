# -*- coding: utf-8 -*-
"""
114번 §2 "권고대로 진행할 것" 구현 검증 — API + 화면 (2026-10-28, PC2)

  V1. 기본값 규칙(109 심각 결정 3) — 작은따옴표 문자열 안의 # 허용('#FFFFFF'), 문자열 밖의 #·인자 있는 함수·따옴표 없는 A 거부
  V2. [DB 반영] — '#FFFFFF' 기본값이 PostgreSQL 에 그대로 반영
  V3. DDL 내려받기(109 심각 결정 2) — 매핑 없는 타입은 타입 이름 그대로 + "-- DDL 규칙 매핑 없음", [DB 반영]만 거부
  V4. [DB 반영] 버튼(109 #17·110 5.8-21-c) 준비 — 실제로 실패(FAILED)한 컬럼 추가, 건너뜀·권한 없음 행
  V5. 승인 대기 공개 범위(110 3.12-15-a) — 표준 승인 권한이 없으면 단어·용어·코드·도메인 목록의 승인 대기 항목은 본인 것만
  V6. 도메인 길이(110 3.12-15-c) — 빈칸은 0 그대로, VARCHAR 등 문자열 타입만 1 이상 필수(화면 등록·수정·엑셀 일괄등록)
  V7. 신청 시각(110 5.8-26-c) — 신청할 때 SUBMIT_DT, 이력 API 의 submitDt
  V8. 수집 한 트랜잭션(재수집 자동 반려 후속) — 수집 시작 기록만 먼저 커밋(진행 중 409), 테이블 단계도 끝까지 안 보임,
      컬럼 단계에서 실패하면 모델 행이 하나도 바뀌지 않음, 실패한 수집은 닫혀 바로 다시 수집
  U1~U9. 화면 — 대시보드 기준 문구(2.4-8-a), 변경 이력 FAILED 행만 [DB 반영]·다시 실행·신청 시각, 모델 등록·수정 창 시스템(4.12-5-b),
      도메인 등록 창 길이 안내, 용어 등록 "추가 메타" 시스템CD(3.12-15-d), 일반 사용자 시스템 칸 비활성, 신청 창 안내 문구(4.12-9·5.8-19),
      내 변경 신청·변경 승인 화면의 신청 시각
  V9. q-executor 로그에 STOMP 오류 없음

필요:
  - q-center(28091)·q-executor(28098) 기동
  - 환경 변수 PGPASSWORD (개발 DB admin), DATAQ_TEST_PW (시험 계정 space·jyjang 비밀번호), 선택 PSQL (psql.exe 경로), QEXEC_LOG (q-executor 로그 파일)
시험 데이터(시험 사전과 그 안의 단어·용어·코드·도메인, 시험 스키마·역할, 데이터 소스, 시스템, 모델과 이력, 알림)는 끝에서 지운다.
"""
import base64, json, os, re, secrets, subprocess, sys, tempfile, time, traceback
import requests
import openpyxl

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
PSQL = os.environ.get("PSQL", r"C:\Program Files\PostgreSQL\13\bin\psql.exe")
PW = os.environ["DATAQ_TEST_PW"]
EXEC_LOG = os.environ.get("QEXEC_LOG")
TMP = tempfile.mkdtemp(prefix="v114_")
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


def psql_args(sql):
    return [PSQL, "-h", "localhost", "-p", "25433", "-U", "admin", "-d", "postgres", "-w", "-At", "-c", sql]


def psql(sql):
    r = subprocess.run(psql_args(sql), capture_output=True, text=True, timeout=60, encoding="utf-8", errors="replace",
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
    return r.get("resultCode") if isinstance(r, dict) else None


def msg(r):
    return (r.get("resultMessage") or "") if isinstance(r, dict) else ""


def contents(r):
    c = r.get("contents")
    return json.loads(c) if isinstance(c, str) and c else (c or {})


def q(v):
    return "'" + str(v).replace("'", "''") + "'"


def now14():
    return time.strftime("%Y%m%d%H%M%S")


def drop_model(dm):
    psql(f"delete from quality.tb_aprv_doc_step where doc_id in (select doc_id from quality.tb_aprv_doc where dm_id='{dm}')")
    psql(f"delete from quality.tb_aprv_doc where dm_id='{dm}'")
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_constraint", "tb_data_model_index",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_udp_val", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id='{dm}'")
    psql(f"delete from quality.tb_data_model where dm_id='{dm}'")


def make_model(s, nm, ds=None, sys_cd=None):
    r = post(s, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds, "dataModelSysCd": sys_cd,
                                            "modelType": "PHYSICAL", "ver": "1", "dictId": "DEFAULT"})
    dm = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
    assert code(r) == 200 and dm, r
    cleanup.append(lambda: drop_model(dm))
    return dm


def save_attrs(s, dm, owner, obj, attrs):
    return post(s, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": obj, "objOwner": owner, "attrs": attrs})


def col(nm, kr, tp="VARCHAR", ln=10, **extra):
    a = {"mode": "ADD", "attrNm": nm, "attrNmKr": kr, "dataType": tp, "dataLen": ln, "nullableYn": "Y"}
    a.update(extra)
    return a


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
    return contents(post(s, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))


def preview(s, seq):
    return contents(post(s, "/api/dmApproval/ddlPreview", {"changeSeq": seq}, timeout=60))


def obj_row(dm, owner, nm):
    return psql(f"select aprv_status||'|'||use_yn from quality.tb_data_model_obj where dm_id='{dm}' "
                f"and coalesce(obj_owner,'')='{owner}' and obj_nm='{nm}'")


def model_sig(dm):
    """모델 행 전체(테이블·컬럼·인덱스·제약조건)의 지문"""
    return psql("select " + "||'/'||".join(
        f"(select count(*)||':'||coalesce(md5(string_agg(t::text, '|' order by t::text)),'') from quality.{tb} t where dm_id='{dm}')"
        for tb in ("tb_data_model_obj", "tb_data_model_attr", "tb_data_model_index", "tb_data_model_constraint")))


def ended(dm):
    return int(psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is not null"))


def last_clct(dm):
    return psql(f"select clct_cmptn_yn||'|'||coalesce(obj_cnt::text,'')||'|'||coalesce(attr_cnt::text,'')||'|'||coalesce(auto_reject_cnt::text,'') "
                f"from quality.tb_data_model_clct where dm_id='{dm}' order by clct_start_dt desc, clct_end_dt desc limit 1")


def wait_clct(dm, n_before, t=120):
    end = time.time() + t
    while time.time() < end:
        time.sleep(2)
        if ended(dm) > n_before:
            break
    return last_clct(dm)


def start_collect(sa, dm, schemas, ds, nm):
    assert code(post(sa, "/api/dm/saveDataModelSchemas", [{"dataModelId": dm, "schemaNm": s, "useYn": "Y"} for s in schemas])) == 200
    return post(sa, "/api/dm/collectDataModel", {"dataModelId": dm, "dataModelDsId": ds, "dataModelNm": nm}, timeout=60)


def collect(sa, dm, schemas, ds, nm):
    before = ended(dm)
    r = start_collect(sa, dm, schemas, ds, nm)
    assert code(r) == 200, r
    return wait_clct(dm, before)


def wait_bulk(target_type, since, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        v = psql(f"select summary from quality.tb_change_history where change_type='BULK_INSERT' and target_type='{target_type}' "
                 f"and change_user_id='space' and change_dt >= to_timestamp('{since}', 'YYYYMMDDHH24MISS') order by change_dt desc limit 1")
        if v:
            return v
        time.sleep(2)
    raise AssertionError("일괄등록 이력 없음")


# ---------------------------------------------------------------- V0 준비
def v0(sa, su):
    def _fixture():
        # 시험 사전 D — 도메인 그룹·분류·승인 도메인
        r = post(sa, "/api/dict/save", {"dictNm": "ZZ114사전" + TAG, "dictDesc": "114 시험", "termLastWordClsfYn": "N",
                                        "upperYn": "Y", "wordEngNmDupYn": "Y", "termEngMaxLen": "", "oldStdYn": "N"})
        assert code(r) == 200, r
        d = contents(r)["dictId"]
        FX["dict"] = d
        FX["grp"], FX["clsf"], FX["dom"] = "ZZ114그룹" + TAG, "ZZ114분류" + TAG, "ZZ114도메인" + TAG

        def _drop_dict():
            for sql in [f"delete from quality.tb_aprv_stats where req_item_nm like 'ZZ114%'",
                        f"delete from quality.tb_change_history where target_nm like 'ZZ114%' or summary like '%ZZ114%'",
                        f"delete from quality.tb_terms_words where terms_id in (select terms_id from quality.tb_terms where dict_id='{d}')",
                        f"delete from quality.tb_terms where dict_id='{d}'",
                        f"delete from quality.tb_word where dict_id='{d}'",
                        f"delete from quality.tb_domain where dict_id='{d}'",
                        f"delete from quality.tb_domain_clsf where dict_id='{d}'",
                        f"delete from quality.tb_domain_grp where dict_id='{d}'",
                        f"delete from quality.tb_std_dict where dict_id='{d}'"]:
                psql(sql)
        cleanup.append(_drop_dict)
        psql(f"insert into quality.tb_domain_grp (domain_grp_id, domain_grp_nm, comm_stnd_yn, dict_id) values ('Z4G{TAG}', {q(FX['grp'])}, 'N', '{d}')")
        psql(f"insert into quality.tb_domain_clsf (domain_clsf_id, domain_clsf_nm, domain_grp_nm, comm_stnd_yn, dict_id) "
             f"values ('Z4C{TAG}', {q(FX['clsf'])}, {q(FX['grp'])}, 'N', '{d}')")
        psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, dict_id) "
             f"values ('Z4D{TAG}', {q(FX['dom'])}, {q(FX['grp'])}, {q(FX['clsf'])}, '114 시험', 'VARCHAR', 30, 'Y', '{d}')")
        # PostgreSQL 시험 역할·스키마 — s: [DB 반영] 대상, a·c: 수집(c 는 컬럼 한글명이 255자를 넘어 컬럼 단계에서 실패)
        role = ("zz114r" + TAG).lower()
        pw = "Zz" + secrets.token_hex(6)
        s_, a_, c_ = f"zz114s{TAG}", f"zz114a{TAG}", f"zz114c{TAG}"
        psql(f"create role {role} login password '{pw}'")
        for sc in (s_, a_, c_):
            psql(f"create schema {sc} authorization {role}")

        def _drop_pg():
            for sc in (s_, a_, c_):
                psql(f"drop schema if exists {sc} cascade")
            psql(f"drop role if exists {role}")
        cleanup.append(_drop_pg)
        for ddl in (f"create table {a_}.t_a (id int primary key, nm varchar(10))", f"create table {c_}.t_bad (c1 int)"):
            psql(ddl)
            psql(f"alter table {ddl.split()[2]} owner to {role}")
        psql(f"comment on column {c_}.t_bad.c1 is '{'가' * 300}'")
        FX.update(role=role, s=s_, a=a_, c=c_)
        dsn = "ZZ114_DS_" + TAG
        r = post(sa, "/api/sysinfo/createDataSource", {"dsn": dsn, "dsTp": 0, "dbmsTp": "PostgreSQL", "driverName": "PostgreSQL",
                                                       "svrAddr": "127.0.0.1", "port": 25433, "userId": role, "pwd": pw, "dbName": "postgres"})
        assert code(r) == 200, r
        ds = psql(f"select ds_id from ndata.tb_data_source where dsn='{dsn}'")
        cleanup.append(lambda: psql(f"delete from quality.tb_gov_meta_db where ds_id='{ds}'"))
        cleanup.append(lambda: psql(f"delete from ndata.tb_data_source where ds_id='{ds}'"))
        psql(f"update ndata.tb_data_source set conn_test_yn='Y' where ds_id='{ds}'")
        FX["ds"] = ds
        # 시스템 2개 (모델 등록·수정 창, 용어 시스템CD)
        for k in ("sys1", "sys2"):
            nm = f"ZZ114시스템{k[-1]}_{TAG}"
            r = post(sa, "/api/sysinfo/createSysInfo", {"sysNm": nm, "sysTp": 1, "sysDsLst": [], "sysDesc": "114 시험"})
            assert code(r) == 200, r
            cd = psql(f"select sys_cd from quality.tb_sys_info where sys_nm={q(nm)}")
            cleanup.append(lambda cd=cd: psql(f"delete from quality.tb_sys_info where sys_cd='{cd}'"))
            FX[k], FX[k + "Nm"] = cd, nm
        FX["m1Nm"] = "ZZ114모델_" + TAG
        FX["m1"] = make_model(sa, FX["m1Nm"], ds)
        FX["m2Nm"] = "ZZ114수집모델_" + TAG
        FX["m2"] = make_model(sa, FX["m2Nm"], ds)
        print(f"  사전={d} 스키마={s_},{a_},{c_} 데이터 소스={ds} 시스템={FX['sys1']},{FX['sys2']} 모델={FX['m1']},{FX['m2']}")
    step("V0. 준비 — 시험 사전·도메인, PostgreSQL 시험 스키마·데이터 소스, 시스템 2개, 모델 2개", _fixture)


# ---------------------------------------------------------------- V1·V2 기본값
def v1(sa, su):
    dm, s_ = FX["m1"], FX["s"]

    def _rule():
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": s_, "objNm": "T1", "objNmKr": "기본값테이블"})) == 200
        ok = save_attrs(sa, dm, s_, "T1", [col("C1", "색상", defaultVal="'#FFFFFF'")])
        bad = {}
        for v in ("#FFFFFF", "'a'#", "TO_DATE('2026-01-01','YYYY-MM-DD')", "A"):
            bad[v] = save_attrs(sa, dm, s_, "T1", [col("C_X", "엑스", defaultVal=v)])
        stored = psql(f"select default_val from quality.tb_data_model_attr where dm_id='{dm}' and obj_nm='T1' and attr_nm='C1'")
        cx = psql(f"select count(*) from quality.tb_data_model_attr where dm_id='{dm}' and attr_nm='C_X'")
        print(f"  '#FFFFFF' → {code(ok)} 저장 값={stored}")
        for v, r in bad.items():
            print(f"  {v:38s} → {code(r)} {msg(r)[:110]}")
        assert code(ok) == 200 and stored == "'#FFFFFF'" and cx == "0"
        assert all(code(r) != 200 for r in bad.values())
        assert "문자열 밖의 #" in msg(bad["#FFFFFF"]) and "문자열 밖의 #" in msg(bad["'a'#"])
        assert "작은따옴표 문자열" in msg(bad["TO_DATE('2026-01-01','YYYY-MM-DD')"]) and "작은따옴표 문자열" in msg(bad["A"])
    step("V1. 기본값 — '#FFFFFF' 저장, 문자열 밖의 #·TO_DATE(…)·따옴표 없는 A 거부 (109 심각 결정 3·test_53_paste)", _rule)

    def _db():
        seq = seq_of(dm, "ADD_OBJ", "T1")
        pv = preview(sa, seq)
        c = exec_ddl(sa, seq)
        dflt = psql(f"select column_default from information_schema.columns where table_schema='{s_}' and table_name='t1' and column_name='c1'")
        print(f"  확인창={pv.get('status')} 문장={pv.get('statements')}\n  실행={c} / DB 기본값={dflt}")
        assert pv.get("status") == "RUN" and "DEFAULT '#FFFFFF'" in " ".join(pv.get("statements") or [])
        assert c.get("success") == 1 and "'#FFFFFF'" in dflt
    step("V2. [DB 반영] — '#FFFFFF' 기본값이 PostgreSQL 컬럼 기본값으로", _db)


# ---------------------------------------------------------------- V3 DDL 내려받기
def v3(sa, su):
    dm, s_ = FX["m1"], FX["s"]

    def _download():
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": s_, "objNm": "T2", "objNmKr": "매핑없는타입"})) == 200
        r = save_attrs(sa, dm, s_, "T2", [col("C1", "문자열", tp="STRING", ln=100), col("C2", "가변", ln=5)])
        assert code(r) == 200, r
        txt = sa.get(BASE + "/api/dm/downloadDdl", params={"dataModelId": dm, "dbType": "PostgreSQL"}, timeout=30).content.decode("utf-8")
        t2 = txt[txt.index("CREATE TABLE T2"):]
        t2 = t2[:t2.index(");") + 2]
        t1 = txt[txt.index("CREATE TABLE T1"):]
        t1 = t1[:t1.index(");") + 2]
        c1 = next(l for l in t2.splitlines() if l.strip().startswith("C1"))
        c2 = next(l for l in t2.splitlines() if l.strip().startswith("C2"))
        print("  내려받기 T2:\n    " + t2.replace("\n", "\n    "))
        assert "STRING(100)" in c1 and c1.rstrip().endswith("-- DDL 규칙 매핑 없음") and c1.index(",") < c1.index("--")
        assert "매핑 없음" not in c2 and "매핑 없음" not in t1 and "DDL 을 만들 수 없습니다" not in txt
        seq = seq_of(dm, "ADD_OBJ", "T2")
        pv = preview(sa, seq)
        c = exec_ddl(sa, seq)
        made = psql(f"select count(*) from information_schema.tables where table_schema='{s_}' and table_name='t2'")
        res = psql(f"select coalesce(ddl_exec_result,'-') from quality.tb_data_model_change_history where change_seq={seq}")
        print(f"  [DB 반영] 확인창={pv.get('status')} {pv.get('message')}\n  실행={c} / DB 테이블={made} / 이력 결과={res}")
        assert pv.get("status") == "DENY" and "DDL 규칙에 없는 데이터 타입" in (pv.get("message") or "")
        assert c.get("success") == 0 and c.get("failed") == 1 and made == "0" and res == "-"
    step("V3. DDL 내려받기 — 매핑 없는 타입(STRING)은 이름 그대로 + '-- DDL 규칙 매핑 없음', [DB 반영]은 거부 (109 심각 결정 2)", _download)


# ---------------------------------------------------------------- V4 [DB 반영] 실패 행
def v4(sa, su):
    dm, s_ = FX["m1"], FX["s"]

    def _failed():
        r = save_attrs(sa, dm, s_, "T1", [col("C2", "실패"), col("C3", "건너뜀"), col("C4", "권한없음")])
        assert code(r) == 200, r
        psql(f"alter table {s_}.t1 add column c2 varchar(10)")       # DBA 가 먼저 만든 컬럼 → ADD COLUMN 실패
        seq2 = seq_of(dm, "ADD_ATTR", "T1", "C2")
        c = exec_ddl(sa, seq2)
        for col_, res in (("C3", "SKIPPED"), ("C4", "NO_PRIVILEGE")):
            psql(f"update quality.tb_data_model_change_history set ddl_exec_dt='{now14()}', ddl_exec_user_id='space', "
                 f"ddl_exec_result='{res}', ddl_exec_message='114 시험' where change_seq={seq_of(dm, 'ADD_ATTR', 'T1', col_)}")
        st = psql(f"select string_agg(attr_nm||':'||coalesce(ddl_exec_result,'-'), ',' order by attr_nm) from quality.tb_data_model_change_history "
                  f"where dm_id='{dm}' and change_type='ADD_ATTR' and obj_nm='T1' and attr_nm in ('C2','C3','C4')")
        print(f"  C2 실행={c} / 결과={st}")
        assert c.get("failed") == 1 and st == "C2:FAILED,C3:SKIPPED,C4:NO_PRIVILEGE"
        FX["seqC2"] = seq2
    step("V4. [DB 반영] 실패(FAILED)한 컬럼 추가 · 건너뜀 · 권한 없음 행 준비", _failed)


# ---------------------------------------------------------------- V5 승인 대기 공개 범위
def v5(sa, su):
    d = FX["dict"]

    def _scope():
        now = now14()
        for i, (nm, yn, who) in enumerate((("내", "N", "jyjang"), ("남", "N", "zz114other"), ("승인", "Y", "space"))):
            psql(f"insert into quality.tb_word (word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc, word_clsf_yn, aprv_yn, cret_dt, cret_user_id, dict_id) "
                 f"values ('Z4W{i}{TAG}', 'ZZ114{nm}단어{TAG}', 'ZZW{i}{TAG}', 'Zzw{i}', '114 시험', 'N', '{yn}', '{now}', '{who}', '{d}')")
            psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, aprv_yn, cret_dt, cret_user_id, dict_id) "
                 f"values ('Z4E{i}{TAG}', 'ZZ114{nm}도메인{TAG}', {q(FX['grp'])}, {q(FX['clsf'])}, '114 시험', 'VARCHAR', 10, '{yn}', '{now}', '{who}', '{d}')")
            psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, aprv_yn, cret_dt, cret_user_id, dict_id) "
                 f"values ('Z4T{i}{TAG}', 'ZZ114{nm}용어{TAG}', 'ZZT{i}_{TAG}', '114 시험', {q(FX['dom'])}, '{yn}', '{now}', '{who}', '{d}')")
            psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, code_grp, aprv_yn, cret_dt, cret_user_id, dict_id) "
                 f"values ('Z4K{i}{TAG}', 'ZZ114{nm}코드{TAG}', 'ZZK{i}_{TAG}', '114 시험', {q(FX['dom'])}, 'ZZ114CG{i}{TAG}', '{yn}', '{now}', '{who}', '{d}')")

        def names(s, api, key, apr):
            body = {"dictId": d}
            if apr is not None:
                body["schAprvYn"] = apr
            rows = s.post(BASE + api, json=body, timeout=30).json()
            return sorted({re.sub(r"ZZ114(.+?)\d{6}$", r"\1", r[key]) for r in rows if str(r.get(key, "")).startswith("ZZ114")})

        apis = (("단어", "/api/std/getWordList", "wordNm"), ("용어", "/api/std/getTermsList", "termsNm"),
                ("코드", "/api/std/getCodeInfoList", "codeNm"), ("도메인", "/api/std/getDomainList", "domainNm"))
        got = {}
        for label, api, key in apis:
            for who, s in (("jyjang", su), ("space", sa)):
                for apr in ("N", None):
                    got[(label, who, apr)] = names(s, api, key, apr)
        for k, v in got.items():
            print(f"  {k[0]} {k[1]:7s} 승인여부={k[2] or '전체':4s} → {v}")
        for label, kind in (("단어", "단어"), ("도메인", "도메인")):
            assert got[(label, "jyjang", "N")] == [f"내{kind}"], got[(label, "jyjang", "N")]
            assert got[(label, "jyjang", None)] == sorted([f"내{kind}", f"승인{kind}"]) or \
                   set(got[(label, "jyjang", None)]) == {f"내{kind}", f"승인{kind}"} | ({"도메인"} if label == "도메인" else set())
            assert set(got[(label, "space", "N")]) == {f"내{kind}", f"남{kind}"}
        assert set(got[("용어", "jyjang", "N")]) == {"내용어", "내코드"} and set(got[("용어", "space", "N")]) == {"내용어", "내코드", "남용어", "남코드"}
        assert "남용어" not in got[("용어", "jyjang", None)] and "남코드" not in got[("용어", "jyjang", None)]
        assert got[("코드", "jyjang", "N")] == ["내코드"] and set(got[("코드", "space", "N")]) == {"내코드", "남코드"}
        assert "남코드" not in got[("코드", "jyjang", None)] and "승인코드" in got[("코드", "jyjang", None)]
    step("V5. 승인 대기 공개 범위 — 표준 승인 권한이 없으면 단어·용어·코드·도메인 목록의 승인 대기는 본인 것만 (110 3.12-15-a)", _scope)


# ---------------------------------------------------------------- V6 도메인 길이
def v6(sa, su):
    d = FX["dict"]

    def dom(nm, tp, ln=None, **extra):
        b = {"domainNm": nm, "domainGrpNm": FX["grp"], "domainClsfNm": FX["clsf"], "domainDesc": "114 시험", "dataType": tp, "dictId": d}
        if ln is not None:
            b["dataLen"] = ln
        b.update(extra)
        return b

    def _api():
        r = {
            "VARCHAR 빈칸": post(sa, "/api/std/createDomain", dom("ZZ114가변빈" + TAG, "VARCHAR")),
            "CHAR 0": post(sa, "/api/std/createDomain", dom("ZZ114고정빈" + TAG, "CHAR", 0)),
            "VARCHAR 20": post(sa, "/api/std/createDomain", dom("ZZ114가변" + TAG, "VARCHAR", 20)),
            "DATE 빈칸": post(sa, "/api/std/createDomain", dom("ZZ114날짜" + TAG, "DATE")),
            "NUMBER 0": post(sa, "/api/std/createDomain", dom("ZZ114숫자" + TAG, "NUMBER", 0)),
        }
        names = ", ".join(q(f"ZZ114{n}{TAG}") for n in ("가변빈", "고정빈", "가변", "날짜", "숫자"))
        rows = psql(f"select string_agg(domain_nm||':'||data_type||':'||data_len, ',' order by domain_nm) from quality.tb_domain "
                    f"where dict_id='{d}' and domain_nm in ({names})")
        vid = psql(f"select domain_id from quality.tb_domain where dict_id='{d}' and domain_nm={q('ZZ114가변' + TAG)}")
        up = post(sa, "/api/std/updateDomain", dom("ZZ114가변" + TAG, "VARCHAR", 0, id=vid))
        after = psql(f"select data_len from quality.tb_domain where domain_id='{vid}'")
        for k, v in r.items():
            print(f"  등록 {k:10s} → {code(v)} {msg(v)[:70]}")
        print(f"  저장된 도메인 {rows}\n  VARCHAR 를 길이 0 으로 수정 → {code(up)} {msg(up)[:70]} / 길이={after}")
        m = "데이터 타입 VARCHAR 은(는) 데이터 길이를 1 이상으로 입력해야 합니다."
        assert code(r["VARCHAR 빈칸"]) == 500 and msg(r["VARCHAR 빈칸"]) == m
        assert code(r["CHAR 0"]) == 500 and "CHAR 은(는) 데이터 길이를 1 이상" in msg(r["CHAR 0"])
        assert all(code(r[k]) == 200 for k in ("VARCHAR 20", "DATE 빈칸", "NUMBER 0"))
        assert rows == f"ZZ114가변{TAG}:VARCHAR:20,ZZ114날짜{TAG}:DATE:0,ZZ114숫자{TAG}:NUMBER:0"
        assert code(up) == 500 and msg(up) == m and after == "20"
    step("V6-1. 도메인 등록·수정 — VARCHAR·CHAR 는 길이 1 이상, DATE·NUMBER 는 빈칸·0 그대로 (110 3.12-15-c)", _api)

    def _upload():
        path = os.path.join(TMP, "domains.xlsx")
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.append(["No", "제정차수", "도메인그룹명", "도메인분류명", "도메인명", "도메인설명", "데이터타입", "데이터길이", "소수점길이",
                   "저장형식", "표현형식", "단위", "허용값", "요청시스템", "공통표준여부", "개인정보여부", "암호화여부"])
        for i, (nm, tp, ln) in enumerate((("ZZ114업로드가", "VARCHAR", 20), ("ZZ114업로드나", "VARCHAR", ""), ("ZZ114업로드다", "DATE", "")), 1):
            ws.append([i, "1", FX["grp"], FX["clsf"], nm + TAG, "114 업로드", tp, ln, "", "", "", "", "", "", "N", "N", "N"])
        wb.save(path)
        since = now14()
        # 일괄등록 이력(요약에 이름이 없다)과 상세 건 — 이 시각 뒤 space 의 도메인 일괄등록
        bulk = (f"select change_id from quality.tb_change_history where change_type='BULK_INSERT' and target_type='DOMAIN' "
                f"and change_user_id='space' and change_dt >= to_timestamp('{since}', 'YYYYMMDDHH24MISS')")
        # 정리는 거꾸로 돈다 — 상세 건을 먼저 지운다
        cleanup.append(lambda: psql(f"delete from quality.tb_change_history where change_id in ({bulk})"))
        cleanup.append(lambda: psql(f"delete from quality.tb_change_history_detail where change_id in ({bulk})"))
        with open(path, "rb") as f:
            r = sa.post(BASE + "/api/std/uploadDomains", files={"file": ("domains.xlsx", f.read(),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}, data={"dictId": d}, timeout=60)
        assert r.status_code == 200, r.text[:200]
        summ = wait_bulk("DOMAIN", since)
        rows = psql(f"select string_agg(domain_nm||':'||data_len, ',' order by domain_nm) from quality.tb_domain where dict_id='{d}' and domain_nm like 'ZZ114업로드%'")
        print(f"  일괄등록 요약={summ}\n  등록된 도메인={rows}")
        assert "(성공:2, 건너뜀:0, 실패:1)" in summ and rows == f"ZZ114업로드가{TAG}:20,ZZ114업로드다{TAG}:0"
    step("V6-2. 도메인 엑셀 일괄등록 — 길이 없는 VARCHAR 행만 실패, DATE 빈칸은 등록", _upload)


# ---------------------------------------------------------------- V7 신청 시각
def v7(sa, su):
    dm, s_ = FX["m1"], FX["s"]

    def _submit():
        assert code(save_attrs(su, dm, s_, "T1", [col("C9", "신청할컬럼"), col("C10", "신청전컬럼")])) == 200
        s9, s10 = seq_of(dm, "ADD_ATTR", "T1", "C9"), seq_of(dm, "ADD_ATTR", "T1", "C10")
        psql(f"update quality.tb_data_model_change_history set change_dt='20260101000000' where change_seq={s9}")
        t0 = now14()
        r = post(su, "/api/dmApproval/submit", {"changeSeqList": [s9]})
        assert code(r) == 200, r
        db = psql(f"select change_dt||'|'||coalesce(submit_dt,'-')||'|'||aprv_status from quality.tb_data_model_change_history where change_seq={s9}")
        d10 = psql(f"select coalesce(submit_dt,'-')||'|'||aprv_status from quality.tb_data_model_change_history where change_seq={s10}")
        hist = post(su, "/api/dmApproval/history", {"dmId": dm, "changeUserId": "jyjang", "aprvStatus": "SUBMITTED"})
        api = [h.get("submitDt") for h in hist if h["changeSeq"] == s9]
        drafts = [h.get("submitDt") for h in post(su, "/api/dmApproval/myDrafts", {"dmId": dm}) if h["changeSeq"] == s10]
        print(f"  C9 저장 시각|신청 시각|상태={db} / C10={d10} / 이력 API submitDt={api} / DRAFT API submitDt={drafts}")
        cdt, sdt, st = db.split("|")
        assert cdt == "20260101000000" and st == "SUBMITTED" and sdt >= t0 and sdt[:8] == t0[:8]
        assert d10 == "-|DRAFT" and api == [sdt] and drafts == [None]
        FX.update(seqC9=s9, seqC10=s10, submitDt=sdt)
    step("V7. 신청 시각 — 신청할 때 SUBMIT_DT(저장 시각 CHANGE_DT 와 따로), 이력 API submitDt, 신청 전은 비어 있음 (110 5.8-26-c)", _submit)


# ---------------------------------------------------------------- V8 수집 한 트랜잭션
def v8(sa, su):
    dm, a_, c_, ds, nm = FX["m2"], FX["a"], FX["c"], FX["ds"], FX["m2Nm"]

    def _first():
        res = collect(sa, dm, [a_], ds, nm)
        print(f"  첫 수집 완료|테이블|컬럼|자동 반려={res}")
        assert res == "Y|1|2|0", res
    step("V8-1. 첫 수집", _first)

    def _in_progress():
        psql(f"create table {a_}.t_new (id int)")
        psql(f"alter table {a_}.t_new owner to {FX['role']}")
        sig0 = model_sig(dm)
        before = ended(dm)
        # 컬럼 테이블에 잠금을 잡아 수집을 컬럼 단계에서 잠시 세운다 — 그동안 먼저 커밋한 수집 시작 기록만 보여야 한다
        lock = subprocess.Popen(psql_args("begin; lock table quality.tb_data_model_attr in share mode; select pg_sleep(8); commit;"),
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=dict(os.environ, PGCLIENTENCODING="UTF8"))
        try:
            time.sleep(1.5)
            r1 = start_collect(sa, dm, [a_], ds, nm)
            assert code(r1) == 200, r1
            running = "0"
            for _ in range(20):
                time.sleep(0.25)
                running = psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is null")
                if running == "1":
                    break
            time.sleep(1.5)
            mid_obj = obj_row(dm, a_, "t_new")
            mid_sig = model_sig(dm)
            r2 = post(sa, "/api/dm/collectDataModel", {"dataModelId": dm, "dataModelDsId": ds, "dataModelNm": nm}, timeout=60)
        finally:
            lock.wait(timeout=30)
        res = wait_clct(dm, before)
        print(f"  수집 중 — 시작 기록(끝난 시각 없음)={running}, 새 테이블 t_new 모델 행={mid_obj or '(안 보임)'}, 모델 행 그대로={mid_sig == sig0}, "
              f"두 번째 수집 요청={code(r2)} {msg(r2)}\n  끝난 뒤={res} / t_new={obj_row(dm, a_, 't_new')}")
        assert running == "1" and mid_obj == "" and mid_sig == sig0
        assert code(r2) == 409 and "이미 진행 중인 수집이 있습니다" in msg(r2)
        assert res == "Y|2|3|0" and obj_row(dm, a_, "t_new") == "APPROVED|Y"
    step("V8-2. 수집 중 — 시작 기록만 먼저 커밋(같은 모델 수집 409), 테이블 단계 결과도 끝날 때까지 안 보임", _in_progress)

    def _fail():
        psql(f"create table {a_}.t_new2 (id int)")
        psql(f"alter table {a_}.t_new2 owner to {FX['role']}")
        sig0 = model_sig(dm)
        res = collect(sa, dm, [a_, c_], ds, nm)
        sig1 = model_sig(dm)
        active = psql(f"select count(*) from quality.tb_data_model_clct where dm_id='{dm}' and clct_end_dt is null")
        print(f"  컬럼 단계 실패 수집={res} / 모델 행 그대로={sig0 == sig1} / t_new2={obj_row(dm, a_, 't_new2') or '(없음)'} "
              f"/ c.t_bad={obj_row(dm, c_, 't_bad') or '(없음)'} / 진행 중 수집={active}")
        assert res == "N|||" and sig0 == sig1 and obj_row(dm, a_, "t_new2") == "" and obj_row(dm, c_, "t_bad") == "" and active == "0"
        res2 = collect(sa, dm, [a_], ds, nm)
        print(f"  바로 다시 수집(a 만)={res2} / t_new2={obj_row(dm, a_, 't_new2')}")
        assert res2 == "Y|3|4|0" and obj_row(dm, a_, "t_new2") == "APPROVED|Y"
    step("V8-3. 컬럼 단계에서 실패 → 모델 행이 하나도 바뀌지 않음, 실패한 수집은 닫혀 바로 다시 수집", _fail)


# ---------------------------------------------------------------- U 화면
def ui(sa, su):
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.common.keys import Keys
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from edgeopt import headless

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

    def nav(d, nav_id, group_id=None):
        if group_id and not d.find_elements(By.ID, nav_id):
            d.execute_script("arguments[0].click();", d.find_element(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header"))
            time.sleep(1)
        el = WebDriverWait(d, 15).until(EC.presence_of_element_located((By.ID, nav_id)))
        d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)
        time.sleep(2.5)

    def click(d, el):
        d.execute_script("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el)

    def swal_text(d, has="", t=10):
        def _t(x):
            for e in x.find_elements(By.CSS_SELECTOR, ".swal2-popup"):
                if e.is_displayed() and has in e.text:
                    return e.text
            return None
        return WebDriverWait(d, t).until(_t)

    def swal_click(d, sel=".swal2-confirm"):
        for b in d.find_elements(By.CSS_SELECTOR, sel):
            if b.is_displayed():
                b.click()
                time.sleep(1)
                return

    def dialog(d):
        return WebDriverWait(d, 10).until(lambda x: next((e for e in x.find_elements(By.CSS_SELECTOR, ".v-dialog--active") if e.is_displayed()), None))

    def pick(d, input_id, text):
        inp = d.find_element(By.ID, input_id)
        click(d, inp)
        inp.send_keys(text)
        time.sleep(1)
        item = WebDriverWait(d, 10).until(lambda x: next((e for e in x.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item")
                                                           if e.is_displayed() and text in e.text), None))
        click(d, item)
        time.sleep(0.5)

    def vm(d, tab_id, name):
        return f"(function(){{var v=document.querySelector('#{tab_id}').firstElementChild.__vue__; while(v && v.$options.name!=='{name}') v=v.$parent; return v;}})()"

    dm, s_ = FX["m1"], FX["s"]
    adm, usr = new_driver(), new_driver()
    try:
        login(adm, "space")
        login(usr, "jyjang")

        def _dash():
            nav(adm, "nav_dashboard")
            a = WebDriverWait(adm, 15).until(EC.presence_of_element_located((By.ID, "dashStructRateBasis"))).text
            b = adm.find_element(By.ID, "dashStructDiagBasis").text
            print(f"  구조 일치율 기준='{a}' / 구조 변경 감지 기준='{b}'")
            assert a == "기준: 테이블·컬럼 (인덱스·제약조건 제외)" and b == "기준: 테이블·컬럼·인덱스·제약조건"
        step("U1. 대시보드 — 구조 일치율·구조 변경 감지 계산 기준 문구 (110 2.4-8-a)", _dash)

        def hist_rows(d):
            box = d.find_element(By.XPATH, "//div[@id='tab_dm_history']//label[normalize-space()='모델 ID']/following-sibling::input")
            box.send_keys(Keys.CONTROL, "a")
            box.send_keys(dm)
            click(d, d.find_element(By.XPATH, "//div[@id='tab_dm_history']//button[normalize-space()='조회']"))
            time.sleep(2.5)
            hdr = [th.text.strip() for th in d.find_elements(By.CSS_SELECTOR, "#dmHistTable thead th")]
            out = {}
            for tr in d.find_elements(By.CSS_SELECTOR, "#dmHistTable tbody tr"):
                tds = tr.find_elements(By.TAG_NAME, "td")
                if len(tds) < len(hdr):
                    continue
                out[tds[hdr.index("대상")].text.strip() + "|" + tds[hdr.index("유형")].text.strip()] = (tr, tds[hdr.index("신청 시각")].text.strip(),
                                                                                                    "DB 반영" in tr.text, tds[-1].text)
            return hdr, out

        def _history():
            nav(adm, "nav_dm_history", "dmGroup")
            hdr, rows = hist_rows(adm)
            p = lambda c: f"{s_}.T1.{c}"
            keys = {k.split("|")[0]: k for k in rows}
            info = {c: (rows[keys[p(c)]][2], rows[keys[p(c)]][3].replace("\n", " ")) for c in ("C2", "C3", "C4")}
            t1 = next(v for k, v in rows.items() if k.startswith(f"{s_}.T1|"))
            c9 = rows[keys[p("C9")]]
            fmt = FX["submitDt"]
            fmt = f"{fmt[:4]}-{fmt[4:6]}-{fmt[6:8]} {fmt[8:10]}:{fmt[10:12]}"
            print(f"  머리글={hdr}\n  [DB 반영] 버튼(있음, 칸)={info} / 테이블 추가 T1(SUCCESS) 버튼={t1[2]} 신청 시각='{t1[1]}'"
                  f"\n  C9(신청함) 신청 시각='{c9[1]}' (기대 {fmt})")
            assert "신청 시각" in hdr and info["C2"][0] and "FAILED" in info["C2"][1]
            assert not info["C3"][0] and not info["C4"][0] and not t1[2] and t1[1] == "" and c9[1] == fmt
            # DBA 가 만든 컬럼을 지운 뒤 FAILED 행의 [DB 반영] 으로 다시 실행 → SUCCESS
            psql(f"alter table {s_}.t1 drop column c2")
            btn = rows[keys[p("C2")]][0].find_element(By.XPATH, ".//button[contains(., 'DB 반영')]")
            click(adm, btn)
            t = swal_text(adm, "DB 직접 반영")
            swal_click(adm)
            done = swal_text(adm, "DB 반영: 성공", 20)
            swal_click(adm)
            res = psql(f"select ddl_exec_result from quality.tb_data_model_change_history where change_seq={FX['seqC2']}")
            colx = psql(f"select count(*) from information_schema.columns where table_schema='{s_}' and table_name='t1' and column_name='c2'")
            _, rows2 = hist_rows(adm)
            again = next(v for k, v in rows2.items() if k.startswith(p("C2") + "|"))
            print(f"  다시 실행 확인창에 ALTER TABLE={'ALTER TABLE' in t} / 결과 창='{done.splitlines()[0]}' / 이력={res} / DB 컬럼={colx} / 버튼 남음={again[2]}")
            assert "ALTER TABLE" in t and res == "SUCCESS" and colx == "1" and not again[2]
        step("U2. 변경 이력 — [DB 반영] 은 실패(FAILED) 행에만 다시 보이고(건너뜀·권한 없음·성공은 없음) 다시 실행 → SUCCESS, 신청 시각 열 (5.8-21-c·5.8-26-c)", _history)

        def _model_dialog():
            nav(adm, "nav_datamodelCollection", "dmGroup")
            click(adm, adm.find_element(By.XPATH, "//div[@id='tab_datamodelCollection']//button[normalize-space()='등록']"))
            dlg = dialog(adm)
            nm = "ZZ114시스템모델_" + TAG
            dlg.find_element(By.CSS_SELECTOR, "input[name='add_dataModelNm']").send_keys(nm)
            dis = adm.find_element(By.ID, "addDataModelSysCd").get_attribute("disabled")
            pick(adm, "addDataModelSysCd", FX["sys1Nm"])
            click(adm, dlg.find_element(By.XPATH, ".//button[normalize-space()='등록']"))
            time.sleep(2.5)
            mid = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
            if mid:
                cleanup.append(lambda: drop_model(mid))
            sys1 = psql(f"select coalesce(dm_sys_cd,'-') from quality.tb_data_model where dm_id='{mid}'")
            # 수정 창 — 지금 시스템이 보이고 다른 시스템으로 바꾼다
            cell = WebDriverWait(adm, 15).until(EC.presence_of_element_located(
                (By.XPATH, f"//div[@id='tab_datamodelCollection']//span[normalize-space()='{nm}']")))
            click(adm, cell)
            time.sleep(2)
            click(adm, adm.find_element(By.XPATH, "//div[@id='tab_datamodelCollection']//button[normalize-space()='수정']"))
            dlg = dialog(adm)
            time.sleep(1)
            shown = adm.find_element(By.ID, "updateDataModelSysCd").get_attribute("value")   # 자동완성은 고른 값을 입력 칸 글자로 보인다
            pick(adm, "updateDataModelSysCd", FX["sys2Nm"])
            click(adm, dlg.find_element(By.XPATH, ".//button[normalize-space()='수정']"))
            time.sleep(2.5)
            sys2 = psql(f"select coalesce(dm_sys_cd,'-') from quality.tb_data_model where dm_id='{mid}'")
            print(f"  관리자 등록 창 시스템 칸 비활성={dis} / 등록 → DM_SYS_CD={sys1} (기대 {FX['sys1']})"
                  f"\n  수정 창에 보인 시스템='{shown}' / 바꾼 뒤 DM_SYS_CD={sys2} (기대 {FX['sys2']})")
            assert dis is None and sys1 == FX["sys1"] and shown == FX["sys1Nm"] and sys2 == FX["sys2"]
        step("U3. 모델 등록·수정 창 — 시스템 선택(관리자: 활성), 등록·수정한 시스템이 DM_SYS_CD 로 (110 4.12-5-b)", _model_dialog)

        def _model_dialog_user():
            nav(usr, "nav_datamodelCollection", "dmGroup")
            click(usr, usr.find_element(By.XPATH, "//div[@id='tab_datamodelCollection']//button[normalize-space()='등록']"))
            dlg = dialog(usr)
            inp = usr.find_element(By.ID, "addDataModelSysCd")
            hint = dlg.text
            print(f"  일반 사용자 시스템 칸 비활성={inp.get_attribute('disabled')} / 안내={'시스템 지정은 모델 직접 반영 권한이 필요합니다' in hint}")
            assert inp.get_attribute("disabled") == "true" and "시스템 지정은 모델 직접 반영 권한이 필요합니다" in hint
            click(usr, dlg.find_element(By.XPATH, ".//button[normalize-space()='취소']"))
            time.sleep(1)
        step("U4. 일반 사용자 — 모델 등록 창의 시스템 칸 비활성·안내 (DM_MANAGE 없음)", _model_dialog_user)

        def _domain_dialog():
            nav(adm, "nav_domain", "dsGroup")
            click(adm, adm.find_element(By.XPATH, "//div[@id='tab_domain']//button[@title='등록']"))
            dlg = dialog(adm)
            nm = "ZZ114화면도메인" + TAG
            adm.execute_script(f"var v={vm(adm, 'tab_domain', 'DSDomain')}; v.addDomain_domainNm=arguments[0]; v.addDomain_domainGrpNm='그룹';"
                               "v.addDomain_domainClsfNm='분류'; v.addDomain_domainDesc='114 화면';", nm)
            adm.execute_script(f"var v={vm(adm, 'tab_domain', 'DSDomain')}; v.addDomain_dataType='VARCHAR'; v.addDomain_dataLen=null;")
            time.sleep(0.5)
            click(adm, dlg.find_element(By.XPATH, ".//button[normalize-space()='등록']"))
            t = swal_text(adm, "데이터 길이")
            swal_click(adm)
            made = psql(f"select count(*) from quality.tb_domain where domain_nm={q(nm)}")
            print(f"  안내='{t.splitlines()[0]}' / 등록됨={made}")
            assert t.splitlines()[0] == "데이터 타입 VARCHAR 은(는) 데이터 길이를 1 이상으로 입력해야 합니다." and made == "0"
            click(adm, dlg.find_element(By.XPATH, ".//button[normalize-space()='취소']"))
            time.sleep(1)
        step("U5. 도메인 등록 창 — VARCHAR 데이터 길이 빈칸이면 안내하고 보내지 않음 (110 3.12-15-c)", _domain_dialog)

        def _term_meta():
            nav(adm, "nav_term", "dsGroup")
            click(adm, adm.find_element(By.XPATH, "//div[@id='tab_term']//button[@title='등록']"))
            dlg = dialog(adm)
            # 등록되지 않은 용어명이어야 분석 결과(단어)가 채워져 "추가 메타" 가 보인다
            dlg.find_element(By.CSS_SELECTOR, "input[placeholder='가동개시일자']").send_keys("고객주소변경일자")
            hdr = WebDriverWait(adm, 20).until(lambda x: next((e for e in x.find_elements(By.CSS_SELECTOR, ".v-dialog--active .v-expansion-panel-header")
                                                               if e.is_displayed() and "추가 메타" in e.text), None))
            title = hdr.text.strip()
            click(adm, hdr)
            time.sleep(1)
            label = dlg.find_element(By.XPATH, ".//input[@id='addTermReqSysCd']/ancestor::div[contains(@class,'row')][1]//div[contains(@class,'v-subheader')]").text
            pick(adm, "addTermReqSysCd", FX["sys1Nm"])
            val = adm.execute_script(f"return {vm(adm, 'tab_term', 'DSTerm')}.addTerm_reqSysCd;")
            print(f"  추가 메타 제목='{title}'\n  입력 칸 라벨='{label}' / 고른 값={val} (기대 {FX['sys1']})")
            assert title == "추가 메타 (이음동의어 / 코드그룹 / 담당기관 / 공통표준여부 / 제정차수 / 시스템CD)"
            assert label == "시스템CD" and val == FX["sys1"]
            click(adm, dlg.find_element(By.XPATH, ".//button[normalize-space()='취소']"))
            time.sleep(1)
        step("U6. 용어 등록 '추가 메타' — 제목 그대로, 시스템CD 입력 칸(시스템 선택 → reqSysCd) (110 3.12-15-d)", _term_meta)

        def _submit_guide():
            nav(usr, "nav_datamodelStatusColumn", "dmGroup")
            click(usr, usr.find_element(By.XPATH, "//div[@id='tab_datamodelStatusColumn']//button[contains(., '내 변경 신청')]"))
            g = WebDriverWait(usr, 10).until(EC.visibility_of_element_located((By.ID, "dmSubmitGuide"))).text
            print("  신청 창 안내:\n    " + g.replace("\n", "\n    "))
            assert "승인 전까지는 본인에게만 보입니다" not in g
            for s in ("추가한 테이블·컬럼은 승인 전까지 본인에게만 보입니다", "수정(이름·타입·길이 등)은 저장할 때 이미 모델에 반영되어 모든 사용자에게 보이고",
                      "삭제는 저장할 때 모든 사용자 화면에서 감춰집니다", "반려되면 수정·삭제·PK·FK 변경은 변경 전으로 되돌아갑니다"):
                assert s in g, s
            click(usr, usr.find_element(By.XPATH, "//div[contains(@class,'v-dialog--active')]//button[normalize-space()='닫기']"))
            time.sleep(1)
        step("U7. 신청 창 안내 — 추가·수정·삭제·PK·FK 의 실제 동작대로 (110 4.12-9·5.8-19-a·b)", _submit_guide)

        def _my_changes():
            nav(usr, "nav_my_dm_changes", "dmGroup")
            time.sleep(1)
            out = {}
            for tbl in usr.find_elements(By.CSS_SELECTOR, "#tab_my_dm_changes .v-data-table"):
                hdr = [th.text.strip() for th in tbl.find_elements(By.CSS_SELECTOR, "thead th")]
                for tr in tbl.find_elements(By.CSS_SELECTOR, "tbody tr"):
                    tds = tr.find_elements(By.TAG_NAME, "td")
                    if len(tds) == len(hdr) and "대상" in hdr:
                        out[tds[hdr.index("대상")].text.strip()] = tds[hdr.index("신청 시각")].text.strip()
            c9, c10 = out.get(f"{s_}.T1.C9"), out.get(f"{s_}.T1.C10")
            print(f"  내 변경 신청 — C9(신청함) 신청 시각='{c9}' (기대 {FX['submitDt']}) / C10(DRAFT) 신청 시각='{c10}'")
            assert c9 == FX["submitDt"] and c10 == ""
        step("U8. 내 변경 신청 화면 — 신청 시각 열은 신청한 시각, 신청 전(DRAFT)은 빈칸", _my_changes)

        def _approval():
            nav(adm, "nav_dm_approval", "mmGroup")
            card = WebDriverWait(adm, 15).until(EC.presence_of_element_located(
                (By.XPATH, f"//div[contains(@class,'submission-row')][contains(., '{FX['m1Nm']}')]")))
            click(adm, card)
            time.sleep(2)
            hdr = [th.text.strip() for th in adm.find_elements(By.CSS_SELECTOR, "#dmAprvItems thead th")]
            vals = []
            for tr in adm.find_elements(By.CSS_SELECTOR, "#dmAprvItems tbody tr"):
                tds = tr.find_elements(By.TAG_NAME, "td")
                if len(tds) == len(hdr) and "C9" in tr.text:
                    vals.append(tds[hdr.index("신청 시각")].text.strip())
            fmt = FX["submitDt"]
            fmt = f"{fmt[:4]}-{fmt[4:6]}-{fmt[6:8]} {fmt[8:10]}:{fmt[10:12]}"
            print(f"  변경 승인 머리글={hdr} / C9 신청 시각={vals} (기대 {fmt}, 저장 시각은 2026-01-01 00:00)")
            assert vals == [fmt]
        step("U9. 데이터 모델 변경 승인 화면 — 항목의 신청 시각은 신청한 시각", _approval)
    finally:
        adm.quit()
        usr.quit()


def v9(sa, su):
    def _stomp():
        if not EXEC_LOG:
            print("  QEXEC_LOG 없음 — 건너뜀")
            return
        with open(EXEC_LOG, encoding="utf-8", errors="replace") as f:
            bad = [l.strip() for l in f if ("STOMP" in l or "Stomp" in l or "stomp" in l) and ("ERROR" in l or "Exception" in l or "lost" in l.lower())]
        print(f"  q-executor 로그 STOMP 오류 {len(bad)}줄 {bad[:3]}")
        assert not bad
    step("V9. q-executor 로그에 STOMP 연결 오류 없음", _stomp)


def main():
    sa = login_api("space")
    su = login_api("jyjang")
    FX["start"] = now14()
    assert psql("select count(*) from quality.tb_aprv_line where use_yn='Y'") == "0", "켜진 결재선이 있다 — 신청이 결재 문서를 만들어 시험이 맞지 않으므로 멈춘다"
    try:
        v0(sa, su)
        if results[-1][1] == "PASS":
            for fn in (v1, v3, v4, v5, v6, v7, v8, ui, v9):
                fn(sa, su)
    finally:
        print("\n=== 정리")
        for fn in reversed(cleanup):
            try:
                fn()
            except Exception as e:
                print("  정리 실패:", e)
        psql(f"delete from quality.tb_notification where cret_dt >= '{FX['start']}'")
        left = psql(f"select (select count(*) from quality.tb_data_model where dm_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_data_model_attr a where not exists (select 1 from quality.tb_data_model m where m.dm_id=a.dm_id))||'/'||"
                    f"(select count(*) from quality.tb_data_model_change_history h where h.change_dt >= '{FX['start']}' "
                    f"and not exists (select 1 from quality.tb_data_model m where m.dm_id=h.dm_id))||'/'||"
                    f"(select count(*) from ndata.tb_data_source where dsn like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_gov_meta_db g where not exists (select 1 from ndata.tb_data_source d where d.ds_id=g.ds_id) "
                    f"and g.cret_dt >= '{FX['start']}')||'/'||"
                    f"(select count(*) from quality.tb_sys_info where sys_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_std_dict where dict_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_word where word_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_terms where terms_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_domain where domain_nm like 'ZZ114%')||'/'||"
                    f"(select count(*) from quality.tb_change_history where target_nm like 'ZZ114%' or summary like '%ZZ114%' "
                    f"or (change_type='BULK_INSERT' and change_dt >= to_timestamp('{FX['start']}', 'YYYYMMDDHH24MISS')))||'/'||"
                    f"(select count(*) from quality.tb_aprv_doc where req_dt >= '{FX['start']}')||'/'||"
                    f"(select count(*) from quality.tb_notification where cret_dt >= '{FX['start']}')||'/'||"
                    f"(select count(*) from pg_namespace where nspname like 'zz114%')||'/'||"
                    f"(select count(*) from pg_roles where rolname like 'zz114%')")
        print(f"  남은 시험 데이터 모델/고아 컬럼/고아 이력/데이터 소스/고아 DBMS 정보/시스템/사전/단어/용어/도메인/표준 변경 이력/결재 문서/알림/스키마/역할 = {left}")


if __name__ == "__main__":
    t0 = time.time()
    main()
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'=' * 60}\n결과: {p} PASS / {f} FAIL  ({time.time() - t0:.0f}초)\n{'=' * 60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
