# -*- coding: utf-8 -*-
"""
109번 중간 결함 4~14 조치 검증 — API

  S4.  결재 문서 진행과 건 처리가 한 트랜잭션 (#4)
       - 결재선이 있는 모델 신청(테이블 추가 + 그 컬럼)을 반려 → 200, 두 건 모두 반려, 문서 반려
       - 결재선 없는 신청도 테이블 + 컬럼을 함께 반려 → 200 (컬럼은 테이블 반려에 함께 반려된 것으로 건너뜀)
       - 표준 반려가 항목 처리에서 실패(도메인을 승인 용어가 참조) → 문서 단계도 그대로
       - 결재자 수정 후 승인이 승인 단계에서 실패(구성 단어 미승인) → 설명 수정·이력도 되돌림
  S7.  결재함 box 는 TURN·MINE·DONE·ALL 만 (#7)
  S8.  표준 승인·반려 대상 검사 (#8) — 승인된 항목 반려 거부, 유형 바꿔 보내기 거부, 참조 사전 항목 거부
  S10. 합의 단계 결재자 둘이 동시에 승인 → 단계가 끝난다 (#10)
  S11. 결재자 수정 후 승인 — 쓰는 대기 용어가 있으면 단어 영문약어 수정 거부, 등록과 같은 검사 (#11)
  S5.  [DB 반영] 생성 문장에 승인 안 된 수정이 들어가지 않음, /updateAttr 권한·승인 상태 (#5)
  S6.  WebSocket — 로그인 없는 연결·헤더 사용자명으로 남의 알림을 못 받음, 남의 알림 목적지 구독 거부 (#6)
  S9.  표준 준수 필수 우회 경로 (#9) — 설정 변경 권한, addAttr·updateAttr·ERwin·XMI
  S12. USER_ADMIN 자기 권한 올리기 (#12)
  S13. 도메인 그룹·분류·코드값 쓰기 권한, 분류 삭제 (#13)
  S14. 테이블 이름·소유자 변경 반려 — 컬럼·인덱스·제약조건·속성 값이 변경 전 이름으로 (#14)

필요:
  - q-center(28091) 기동
  - 환경 변수 PGPASSWORD (개발 DB admin), DATAQ_TEST_PW (시험 계정 space·jyjang 비밀번호), 선택 PSQL (psql.exe 경로)
  - 인자로 돌릴 묶음을 고를 수 있다 (예: python verify_109_medium_fixes.py S4 S7). 없으면 전부
시험 데이터(결재선·단어·용어·도메인·결재 문서·알림·역할·시험 사용자·모델·사전 등)는 끝에서 지운다.
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


ADMIN_STEP = [{"stepNm": "관리자", "aprvrTp": "ADMIN", "agreeYn": "N"}]


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
    """DEFAULT 사전의 승인된 형식단어 '명' (용어의 마지막 단어)"""
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
        psql(f"delete from quality.tb_terms where terms_id in (select terms_id from quality.tb_terms_words where word_id='{wid}')")
        psql(f"delete from quality.tb_word where word_id='{wid}'")
    cleanup.append(_drop)
    return wid


def term(s, w_id, w_nm, w_abrv):
    nm = w_nm + "명"
    r = post(s, "/api/std/createTerms", {"termsNm": nm, "termsEngAbrvNm": w_abrv + "_NM", "termsDesc": "109 시험 용어",
                                         "domainNm": "명V40", "dictId": "DEFAULT",
                                         "wordList": [{"wordId": w_id, "wordNm": w_nm, "wordOrd": 0},
                                                      {"wordId": myung(), "wordNm": "명", "wordOrd": 1}]})
    tid = psql(f"select terms_id from quality.tb_terms where terms_nm={q(nm)} and dict_id='DEFAULT'")
    assert tid, f"용어 등록 실패: {r}"

    def _drop():
        psql(f"delete from quality.tb_change_history where target_id='{tid}'")
        psql(f"delete from quality.tb_aprv_stats where req_item_id='{tid}'")
        psql(f"delete from quality.tb_terms where terms_id='{tid}'")
    cleanup.append(_drop)
    return tid, nm


def doc_of(target_id):
    return psql(f"select doc_stat||'|'||cur_step_no from quality.tb_aprv_doc where target_id='{target_id}' order by req_dt desc limit 1")


def doc_id(target_id):
    return psql(f"select doc_id from quality.tb_aprv_doc where target_id='{target_id}' order by req_dt desc limit 1")


def make_model(s, nm, ds=None, stnd_req="N"):
    r = post(s, "/api/dm/createDataModel", {"dataModelNm": nm, "dataModelDsId": ds, "modelType": "PHYSICAL", "ver": "1",
                                            "dictId": "DEFAULT", "stndReqYn": stnd_req})
    dm = psql(f"select dm_id from quality.tb_data_model where dm_nm={q(nm)}")
    assert code(r) == 200 and dm, r
    cleanup.append(lambda: drop_model(dm))
    return dm


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


def drop_model(dm):
    for t in ("tb_data_model_change_history", "tb_data_model_attr", "tb_data_model_constraint", "tb_data_model_index",
              "tb_data_model_obj", "tb_data_model_clct", "tb_data_model_schema", "tb_udp_val", "tb_term_resolve_history"):
        psql(f"delete from quality.{t} where dm_id='{dm}'")
    psql(f"delete from quality.tb_data_model where dm_id='{dm}'")


def hist(dm, where=""):
    return psql(f"select string_agg(change_type||':'||coalesce(obj_nm,'')||':'||coalesce(attr_nm,'')||':'||aprv_status, ',' order by change_seq) "
                f"from quality.tb_data_model_change_history where dm_id='{dm}' {where}")


def seqs(dm, status):
    v = psql(f"select string_agg(change_seq::text, ',' order by change_seq) from quality.tb_data_model_change_history "
             f"where dm_id='{dm}' and aprv_status='{status}'")
    return [int(x) for x in v.split(",")] if v else []


def main(only):
    sa = login_api("space")
    su = login_api("jyjang")
    start = time.strftime("%Y%m%d%H%M%S")
    assert psql("select count(*) from quality.tb_user_role where user_id='jyjang'") == "0", "jyjang 에 이미 역할이 있다 — 시험이 권한을 바꾸므로 멈춘다"
    assert psql("select count(*) from quality.tb_aprv_line where use_yn='Y'") == "0", "켜진 결재선이 있다 — 시험이 결재선을 바꾸므로 멈춘다"

    groups = [("S4", s4), ("S7", s7), ("S8", s8), ("S10", s10), ("S11", s11), ("S5", s5), ("S9", s9), ("S6", s6),
              ("S12", s12), ("S13", s13), ("S14", s14)]
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


# ---------------------------------------------------------------- S4
def s4(sa, su):
    st = {}

    def _dm_doc():
        aprv_line(sa, "DM_CHANGE", ADMIN_STEP)
        st["dm"] = make_model(su, "ZZ109중간모델_" + TAG)
        dm = st["dm"]
        assert code(post(su, "/api/dm/addObj", {"dataModelId": dm, "objOwner": "", "objNm": "ZZ_M4", "objNmKr": "시험테이블"})) == 200
        r = post(su, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": "ZZ_M4", "objOwner": "", "attrs": [
            {"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10}]})
        assert code(r) == 200, r
        drafts = seqs(dm, "DRAFT")
        r = post(su, "/api/dmApproval/submit", {"changeSeqList": drafts})
        assert code(r) == 200, r
        sub = contents(r)["submissionId"]
        print(f"  신청 {drafts} 문서={doc_of(sub)}")
        assert doc_of(sub) == "PROGRESS|1"
        r = post(sa, "/api/dmApproval/reject", {"changeSeqList": drafts, "aprvComment": "109 반려 시험"})
        print(f"  반려={code(r)} {r.get('resultMessage')} {r.get('contents')}\n  이력={hist(dm)} 문서={doc_of(sub)}")
        assert code(r) == 200 and doc_of(sub).startswith("REJECTED")
        assert hist(dm) == "ADD_OBJ:ZZ_M4::REJECTED,ADD_ATTR:ZZ_M4:C1:REJECTED"
        assert psql(f"select aprv_status from quality.tb_data_model_obj where dm_id='{dm}' and obj_nm='ZZ_M4'") == "REJECTED"
    step("S4-1. 결재선 있는 모델 신청(테이블 추가 + 그 컬럼) 반려 → 200, 두 건 반려, 문서 반려", _dm_doc)

    def _dm_plain():
        line_off(sa, "DM_CHANGE")
        dm = st["dm"]
        assert code(post(su, "/api/dm/addObj", {"dataModelId": dm, "objOwner": "", "objNm": "ZZ_M4B", "objNmKr": "시험테이블"})) == 200
        assert code(post(su, "/api/dm/saveAttrs", {"dataModelId": dm, "objNm": "ZZ_M4B", "objOwner": "", "attrs": [
            {"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10}]})) == 200
        drafts = seqs(dm, "DRAFT")
        assert code(post(su, "/api/dmApproval/submit", {"changeSeqList": drafts})) == 200
        r = post(sa, "/api/dmApproval/reject", {"changeSeqList": drafts, "aprvComment": "109 반려 시험"})
        h = hist(dm, "and obj_nm='ZZ_M4B'")
        print(f"  반려={code(r)} {r.get('resultMessage')} {r.get('contents')}\n  이력={h}")
        assert code(r) == 200 and h == "ADD_OBJ:ZZ_M4B::REJECTED,ADD_ATTR:ZZ_M4B:C1:REJECTED"
        # 이미 처리된 건을 다시 보내면 지금처럼 거부 (DEF-09)
        r = post(sa, "/api/dmApproval/reject", {"changeSeqList": drafts[:1], "aprvComment": "다시"})
        print(f"  다시 반려={code(r)} {r.get('resultMessage')}")
        assert code(r) == 500 and "이미 처리된 항목" in (r.get("resultMessage") or "")
    step("S4-2. 결재선 없는 신청 — 테이블 + 컬럼 함께 반려 200, 처리된 건 다시 보내면 거부", _dm_plain)

    def _std_rollback():
        aprv_line(sa, "DOMAIN", ADMIN_STEP)
        dnm = "ZZ109D" + TAG
        r = post(su, "/api/std/createDomain", {"domainNm": dnm, "domainGrpNm": "명칭", "domainClsfNm": "명", "domainDesc": "109 시험",
                                               "dataType": "VARCHAR", "dataLen": 30, "dictId": "DEFAULT"})
        did = psql(f"select domain_id from quality.tb_domain where domain_nm={q(dnm)}")
        assert did, r
        tid = "ZZ109T" + TAG

        def _drop():
            psql(f"delete from quality.tb_terms where terms_id='{tid}'")
            psql(f"delete from quality.tb_aprv_stats where req_item_id='{did}'")
            psql(f"delete from quality.tb_domain where domain_id='{did}'")
        cleanup.append(_drop)
        # 승인된 용어가 이 도메인을 참조 — 반려(물리 삭제)가 FK 로 실패한다
        psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, aprv_yn, dict_id) "
             f"values ('{tid}', 'ZZ109참조용어{TAG}', 'ZZ109REF{TAG}', '109 시험', {q(dnm)}, 'Y', 'DEFAULT')")
        assert doc_of(did) == "PROGRESS|1"
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "DOMAIN", "reqItemId": did, "reqItemNm": dnm, "aprvStat": 3,
                                                  "aprvStatUpdtRsn": "109 반려 시험"}])
        left = psql(f"select count(*) from quality.tb_domain where domain_id='{did}'")
        stats = psql(f"select count(*) from quality.tb_aprv_stats where req_item_id='{did}'")
        noti = psql(f"select count(*) from quality.tb_notification where link_id='{doc_id(did)}' and noti_tp='APRV_REJECT'")
        print(f"  반려={code(r)} {str(r.get('resultMessage'))[:120]}\n  문서={doc_of(did)} 도메인 남음={left} 처리 이력={stats} 반려 알림={noti}")
        assert code(r) == 500 and doc_of(did) == "PROGRESS|1" and left == "1" and stats == "0" and noti == "0"
        psql(f"delete from quality.tb_terms where terms_id='{tid}'")
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "DOMAIN", "reqItemId": did, "reqItemNm": dnm, "aprvStat": 3,
                                                  "aprvStatUpdtRsn": "109 반려 시험"}])
        left = psql(f"select count(*) from quality.tb_domain where domain_id='{did}'")
        print(f"  참조 용어를 지운 뒤 반려={code(r)} 문서={doc_of(did)} 도메인 남음={left}")
        assert code(r) == 200 and doc_of(did) == "REJECTED|1" and left == "0"
        line_off(sa, "DOMAIN")
    step("S4-3. 표준 반려가 항목 처리(물리 삭제)에서 실패 → 결재 문서도 그대로(PROGRESS), 알림 없음", _std_rollback)

    def _edit_rollback():
        aprv_line(sa, "TERMS", ADMIN_STEP)
        wnm = "ZZ109수정단어" + TAG
        st["w"] = word(su, wnm, "ZZEW" + TAG, "Zzeword" + TAG)      # 단어 결재선 없음 — 승인 대기
        tid, tnm = term(su, st["w"], wnm, "ZZEW" + TAG)
        st["t"] = tid
        assert doc_of(tid) == "PROGRESS|1"
        r = post(sa, "/api/aprv/editAndApprove", {"docId": doc_id(tid), "desc": "결재자가 고친 설명", "comment": "수정 승인"})
        desc = psql(f"select terms_desc||'|'||aprv_yn from quality.tb_terms where terms_id='{tid}'")
        h = psql(f"select count(*) from quality.tb_change_history where target_id='{tid}' and change_source='APPROVER_EDIT'")
        print(f"  수정 후 승인={code(r)} {str(r.get('resultMessage'))[:120]}\n  용어={desc} 문서={doc_of(tid)} 결재자 수정 이력={h}")
        assert code(r) == 500 and "아직 승인되지 않았습니다" in (r.get("resultMessage") or "")
        assert desc == "109 시험 용어|N" and doc_of(tid) == "PROGRESS|1" and h == "0"
        # 단어를 승인한 뒤에는 수정·승인이 함께 된다
        assert code(post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": st["w"], "reqItemNm": wnm, "aprvStat": 2}])) == 200
        r = post(sa, "/api/aprv/editAndApprove", {"docId": doc_id(tid), "desc": "결재자가 고친 설명", "comment": "수정 승인"})
        desc = psql(f"select terms_desc||'|'||aprv_yn from quality.tb_terms where terms_id='{tid}'")
        h = psql(f"select count(*) from quality.tb_change_history where target_id='{tid}' and change_source='APPROVER_EDIT'")
        print(f"  단어 승인 뒤 수정 후 승인={code(r)} 용어={desc} 문서={doc_of(tid)} 결재자 수정 이력={h}")
        assert code(r) == 200 and desc == "결재자가 고친 설명|Y" and doc_of(tid).startswith("APPROVED") and h == "1"
        line_off(sa, "TERMS")
    step("S4-4. 결재자 수정 후 승인이 승인 단계에서 실패 → 설명 수정·이력 되돌림, 성공하면 함께 반영", _edit_rollback)


# ---------------------------------------------------------------- S7
def s7(sa, su):
    def _box():
        outs = {b: code(get(su, "/api/aprv/box", {"box": b})) for b in ("TURN", "MINE", "DONE", "ALL", "XYZ", "turn")}
        print("  ", outs)
        assert outs == {"TURN": 200, "MINE": 200, "DONE": 200, "ALL": 403, "XYZ": 400, "turn": 400}
        assert code(get(sa, "/api/aprv/box", {"box": "ALL"})) == 200
    step("S7-1. 결재함 box — TURN·MINE·DONE 200, ALL 은 관리자만, 그 밖의 값 400", _box)


# ---------------------------------------------------------------- S8
def s8(sa, su):
    def _approved():
        wnm = "ZZ109승인단어" + TAG
        wid = word(sa, wnm, "ZZAP" + TAG, "Zzapword" + TAG)     # 관리자 등록 — 바로 승인
        assert psql(f"select aprv_yn from quality.tb_word where word_id='{wid}'") == "Y"
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": wid, "reqItemNm": wnm, "aprvStat": 3,
                                                  "aprvStatUpdtRsn": "승인된 단어 반려 시험"}])
        left = psql(f"select count(*) from quality.tb_word where word_id='{wid}'")
        print(f"  승인된 단어 반려={code(r)} {r.get('resultMessage')} / 남음={left}")
        assert code(r) == 400 and "승인 대기 항목이 아닙니다" in r.get("resultMessage") and left == "1"
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": "ZZ없는항목", "aprvStat": 2}])
        print(f"  없는 항목={code(r)} {r.get('resultMessage')}")
        assert code(r) == 400
    step("S8-1. 승인된 단어를 반려로 보내면 400, 단어 그대로 / 없는 항목 400", _approved)

    def _swap():
        aprv_line(sa, "TERMS", ADMIN_STEP)
        wnm = "ZZ109유형단어" + TAG
        wid = word(su, wnm, "ZZTP" + TAG, "Zztpword" + TAG)
        tid, tnm = term(su, wid, wnm, "ZZTP" + TAG)
        assert doc_of(tid) == "PROGRESS|1"
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "CODE", "reqItemId": tid, "reqItemNm": tnm, "aprvStat": 3,
                                                  "aprvStatUpdtRsn": "유형 바꾸기"}])
        left = psql(f"select aprv_yn from quality.tb_terms where terms_id='{tid}'")
        print(f"  용어를 CODE 로 반려={code(r)} {r.get('resultMessage')} / 용어={left} 문서={doc_of(tid)}")
        assert code(r) == 400 and "유형" in r.get("resultMessage") and left == "N" and doc_of(tid) == "PROGRESS|1"
        line_off(sa, "TERMS")
    step("S8-2. 결재 문서가 있는 용어를 reqTp=CODE 로 보내 결재선 우회 → 400, 용어·문서 그대로", _swap)

    def _ref():
        did = "ZZ109R" + TAG
        wid = "ZZ109RW" + TAG
        psql(f"insert into quality.tb_std_dict (dict_id, dict_nm, dict_tp, use_yn) values ('{did}', '109 참조 시험 {TAG}', 'REF', 'Y')")
        psql(f"insert into quality.tb_word (word_id, word_nm, word_eng_abrv_nm, word_eng_nm, word_desc, word_clsf_yn, aprv_yn, dict_id) "
             f"values ('{wid}', 'ZZ109참조단어{TAG}', 'ZZRW{TAG}', 'Zzrword{TAG}', '109 시험', 'N', 'N', '{did}')")

        def _drop():
            psql(f"delete from quality.tb_aprv_stats where req_item_id='{wid}'")
            psql(f"delete from quality.tb_word where word_id='{wid}'")
            psql(f"delete from quality.tb_std_dict where dict_id='{did}'")
        cleanup.append(_drop)
        time.sleep(11)   # 사전 정보 캐시(10초)
        r = post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": wid, "aprvStat": 3, "aprvStatUpdtRsn": "참조 사전"}])
        left = psql(f"select count(*) from quality.tb_word where word_id='{wid}'")
        print(f"  참조 사전 단어 반려={code(r)} {r.get('resultMessage')} / 남음={left}")
        assert code(r) == 400 and "참조 사전" in r.get("resultMessage") and left == "1"
    step("S8-3. 참조 사전 항목 승인·반려 → 400, 그대로", _ref)


# ---------------------------------------------------------------- S10
def s10(sa, su):
    def _agree():
        ua, ub = "zz109a" + TAG, "zz109b" + TAG
        pa, pb = make_user(sa, ua), make_user(sa, ub)
        rid = make_role(sa, "ZZ109합의_" + TAG, [])
        assign(sa, ua, rid)
        assign(sa, ub, rid)
        aprv_line(sa, "WORD", [{"stepNm": "합의", "aprvrTp": "ROLE", "aprvrId": rid, "agreeYn": "Y"}])
        sx = {ua: login_api(ua, pa), ub: login_api(ub, pb)}
        out = []
        for i in range(5):
            wnm = f"ZZ109합의{i}{TAG}"
            wid = word(su, wnm, f"ZZAG{i}{TAG}", f"Zzagword{i}{TAG}")
            assert doc_of(wid) == "PROGRESS|1"
            bar = threading.Barrier(2)
            codes = {}

            def go(u):
                bar.wait()
                codes[u] = code(post(sx[u], "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": wid, "reqItemNm": wnm, "aprvStat": 2}]))
            ts = [threading.Thread(target=go, args=(u,)) for u in (ua, ub)]
            for t in ts:
                t.start()
            for t in ts:
                t.join()
            acted = psql(f"select string_agg(act_stat, ',' order by aprvr_user_id) from quality.tb_aprv_doc_step where doc_id='{doc_id(wid)}'")
            out.append((codes.get(ua), codes.get(ub), doc_of(wid), acted, psql(f"select aprv_yn from quality.tb_word where word_id='{wid}'")))
        for o in out:
            print("  ", o)
        assert all(o[0] == 200 and o[1] == 200 and o[2].startswith("APPROVED") and o[3] == "APPROVED,APPROVED" and o[4] == "Y" for o in out)
        line_off(sa, "WORD")
    step("S10-1. 합의 단계(결재자 2명) 동시 승인 5회 — 매번 문서 최종 승인·단어 승인", _agree)


# ---------------------------------------------------------------- S11
def s11(sa, su):
    def _abrv():
        aprv_line(sa, "WORD", ADMIN_STEP)
        aprv_line(sa, "TERMS", ADMIN_STEP)
        wnm = "ZZ109약어단어" + TAG
        abrv = "ZZAB" + TAG
        wid = word(su, wnm, abrv, "Zzabword" + TAG)
        tid, tnm = term(su, wid, wnm, abrv)
        wd = doc_id(wid)
        r1 = post(sa, "/api/aprv/editAndApprove", {"docId": wd, "engNm": "Zzabword" + TAG, "engAbrvNm": "ZZXB" + TAG, "desc": "설명"})
        r2 = post(sa, "/api/aprv/editAndApprove", {"docId": wd, "engNm": "Zz abword", "engAbrvNm": abrv, "desc": "설명"})
        r3 = post(sa, "/api/aprv/editAndApprove", {"docId": wd, "engNm": "Zzabword" + TAG, "engAbrvNm": "1" + abrv, "desc": "설명"})
        now = psql(f"select word_eng_abrv_nm||'|'||word_eng_nm||'|'||aprv_yn from quality.tb_word where word_id='{wid}'")
        print(f"  약어 변경(대기 용어 있음)={code(r1)} {r1.get('resultMessage')}\n  영문명 공백={code(r2)} {r2.get('resultMessage')}"
              f"\n  약어 숫자 시작={code(r3)} {r3.get('resultMessage')}\n  단어={now} 문서={doc_of(wid)}")
        assert code(r1) == 500 and "승인 대기 용어" in r1.get("resultMessage") and tnm in r1.get("resultMessage")
        assert code(r2) == 500 and "영문명" in r2.get("resultMessage")
        assert code(r3) == 500 and "영문약어" in r3.get("resultMessage")
        assert now == f"{abrv}|Zzabword{TAG}|N" and doc_of(wid) == "PROGRESS|1"
        # 약어를 그대로 두면(소문자로 보내도 표준 대문자 옵션으로 같은 값) 수정 후 승인
        r = post(sa, "/api/aprv/editAndApprove", {"docId": wd, "engNm": "ZzabwordNew" + TAG, "engAbrvNm": abrv.lower(), "desc": "결재자 설명"})
        now = psql(f"select word_eng_abrv_nm||'|'||word_eng_nm||'|'||word_desc||'|'||aprv_yn from quality.tb_word where word_id='{wid}'")
        print(f"  약어 그대로·영문명 수정={code(r)} 단어={now} 문서={doc_of(wid)}")
        assert code(r) == 200 and now == f"{abrv}|ZzabwordNew{TAG}|결재자 설명|Y" and doc_of(wid).startswith("APPROVED")
        line_off(sa, "WORD")
        line_off(sa, "TERMS")
    step("S11-1. 결재자 수정 후 승인 — 대기 용어가 쓰는 단어 약어 변경 거부, 등록과 같은 형식 검사, 약어 그대로면 승인", _abrv)


# ---------------------------------------------------------------- S5
def s5(sa, su):
    st = {}

    def _pending_edit():
        nm, pw = pg_schema("zz109m")
        ds = make_ds(sa, "ZZ109_M_" + TAG, nm, pw)
        dm = make_model(sa, "ZZ109반영모델_" + TAG, ds)
        st.update(dm=dm, owner=nm)
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": nm, "objNm": "ZZ_T5", "objNmKr": "시험테이블"})) == 200
        assert code(save_attrs(sa, dm, nm, "ZZ_T5", [{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        # 일반 사용자가 컬럼 길이를 바꿈 — 모델 행은 바로 바뀌고 이력은 DRAFT
        r = save_attrs(su, dm, nm, "ZZ_T5", [{"mode": "UPDATE", "origAttrNm": "C1", "attrNm": "C1", "attrNmKr": "일",
                                               "dataType": "VARCHAR", "dataLen": 99, "nullableYn": "Y"}])
        assert code(r) == 200, r
        mod = seq_of(dm, "MODIFY_ATTR", "ZZ_T5", "C1", "DRAFT")
        seq = seq_of(dm, "ADD_OBJ", "ZZ_T5")
        pv = contents(post(sa, "/api/dmApproval/ddlPreview", {"changeSeq": seq}))
        ex = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
        made = psql(f"select count(*) from information_schema.tables where table_schema='{nm}' and table_name='zz_t5'")
        print(f"  대기 수정 있음 — 확인창 {pv.get('status')} {pv.get('message')}\n  실행 {ex} / 테이블={made}")
        assert pv.get("status") == "DENY" and "승인 대기 중인 수정이 1건" in pv.get("message") and ex.get("failed") == 1 and made == "0"
        # 수정을 승인하면 승인된 값(길이 99)으로 생성 반영
        assert code(post(sa, "/api/dmApproval/approve", {"changeSeqList": [mod]})) == 200
        pv = contents(post(sa, "/api/dmApproval/ddlPreview", {"changeSeq": seq}))
        ex = contents(post(sa, "/api/dmApproval/execDdl", {"changeSeqList": [seq]}, timeout=60))
        ln = psql(f"select character_maximum_length from information_schema.columns where table_schema='{nm}' and table_name='zz_t5' and column_name='c1'")
        print(f"  승인 뒤 — 확인창 {pv.get('status')} {(pv.get('statements') or [''])[0][:80]!r}\n  실행 {ex} / C1 길이={ln}")
        assert pv.get("status") == "RUN" and ex.get("success") == 1 and ln == "99"
    step("S5-1. 승인 대기 컬럼 수정이 있는 테이블은 생성 반영 거부, 수정을 승인하면 승인된 값으로 CREATE TABLE", _pending_edit)

    def _update_attr():
        # 110 2묶음(4.12-1) — 호출 화면이 없던 /updateAttr 를 지웠다. 승인 상태를 요청값으로 바꿀 길이 없는지만 본다
        dm, nm = st["dm"], st["owner"]
        assert code(save_attrs(su, dm, nm, "ZZ_T5", [{"mode": "ADD", "attrNm": "C2", "attrNmKr": "이", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        body = {"dataModelId": dm, "objOwner": nm, "objNm": "ZZ_T5", "attrNm": "C2", "attrNmKr": "이", "dataType": "VARCHAR",
                "dataLen": 20, "nullableYn": "Y", "pkYn": "N", "fkYn": "N", "aprvStatus": "APPROVED", "aprvUserId": "jyjang"}
        r2 = sa.post(BASE + "/api/dm/updateAttr", json=body, timeout=30)
        now = psql(f"select aprv_status||'|'||data_len from quality.tb_data_model_attr where dm_id='{dm}' and obj_nm='ZZ_T5' and attr_nm='C2'")
        print(f"  /updateAttr HTTP={r2.status_code} / 컬럼={now}")
        assert r2.status_code in (404, 405) and now == "DRAFT|10"
    step("S5-2. /updateAttr 없음(110 2묶음에서 지움) — 승인 상태·값 그대로", _update_attr)


# ---------------------------------------------------------------- S9
def s9(sa, su):
    st = {}

    def _setting():
        dm = make_model(sa, "ZZ109필수모델_" + TAG)
        st["dm"] = dm
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": "", "objNm": "ZZ_T9", "objNmKr": "시험테이블"})) == 200
        assert code(save_attrs(sa, dm, "", "ZZ_T9", [{"mode": "ADD", "attrNm": "ZZ_NOSTD", "attrNmKr": "비표준", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        cur = {"dataModelId": dm, "dataModelNm": "ZZ109필수모델_" + TAG, "ver": "1", "dictId": "DEFAULT"}
        r0 = post(sa, "/api/dm/updateDataModel", dict(cur, stndReqYn="Y"))
        r1 = post(su, "/api/dm/updateDataModel", dict(cur, stndReqYn="N"))
        yn1 = psql(f"select stnd_req_yn from quality.tb_data_model where dm_id='{dm}'")
        r2 = post(su, "/api/dm/updateDataModel", dict(cur, stndReqYn="Y", ver="2"))
        print(f"  관리자 켬={code(r0)} / 일반 사용자 끔={code(r1)} {r1.get('resultMessage')} 설정={yn1} / 일반 사용자 그대로 두고 버전 수정={code(r2)}")
        assert code(r0) == 200 and code(r1) == 403 and yn1 == "Y" and code(r2) == 200
    step("S9-1. 표준 준수 필수 — 일반 사용자는 모델 수정으로 못 끔(403), 설정을 그대로 두면 다른 항목 수정 200", _setting)

    def _paths():
        dm = st["dm"]
        # 110 2묶음(4.12-1) — 호출 화면이 없던 /addAttr·/updateAttr 를 지웠다 (우회 경로 자체가 없음)
        r1 = sa.post(BASE + "/api/dm/addAttr", json={"dataModelId": dm, "objOwner": "", "objNm": "ZZ_T9", "attrNm": "ZZ_ADD", "attrNmKr": "추가",
                                                     "dataType": "VARCHAR", "dataLen": 10, "termsStndYn": "N"}, timeout=30)
        r2 = sa.post(BASE + "/api/dm/updateAttr", json={"dataModelId": dm, "objOwner": "", "objNm": "ZZ_T9", "attrNm": "ZZ_NOSTD", "attrNmKr": "비표준",
                                                        "dataType": "VARCHAR", "dataLen": 30, "nullableYn": "Y", "pkYn": "N", "fkYn": "N"}, timeout=30)
        imp = {"dataModelId": dm, "tables": [{"objNm": "ZZ_IMP", "objNmKr": "가져오기", "objAttrCnt": 1}],
               "columns": [{"objNm": "ZZ_IMP", "attrNm": "ZZ_IMPCOL", "attrNmKr": "가져온컬럼", "dataType": "VARCHAR", "dataLen": 10,
                            "nullableYn": "Y", "pkYn": "N", "fkYn": "N", "attrOrder": 1}]}
        r3 = post(sa, "/api/dm/importXmiModel", imp)
        r4 = post(sa, "/api/dm/importErwinModel", imp)
        cols = psql(f"select string_agg(attr_nm||':'||data_len, ',' order by attr_nm) from quality.tb_data_model_attr where dm_id='{dm}'")
        objs = psql(f"select string_agg(obj_nm, ',' order by obj_nm) from quality.tb_data_model_obj where dm_id='{dm}'")
        print(f"  addAttr HTTP={r1.status_code} / updateAttr HTTP={r2.status_code}"
              f"\n  XMI={r3.get('success')} {str(r3.get('message'))[:90]}\n  ERwin={r4.get('success')}\n  컬럼={cols} 테이블={objs}")
        assert r1.status_code in (404, 405) and r2.status_code in (404, 405)
        assert r3.get("success") is False and r3.get("message").startswith("표준 준수 필수 모델입니다") and r4.get("success") is False
        assert cols == "ZZ_NOSTD:10" and objs == "ZZ_T9"
        # 필수가 아닌 모델은 지금처럼
        dm2 = make_model(sa, "ZZ109일반모델_" + TAG)
        imp["dataModelId"] = dm2
        r5 = post(sa, "/api/dm/importXmiModel", imp)
        print(f"  필수 아닌 모델 XMI={r5.get('success')}")
        assert r5.get("success") is True
    step("S9-2. 필수 모델 — XMI·ERwin 가져오기의 비표준 컬럼 거부(addAttr·updateAttr 는 110 2묶음에서 지움), 필수 아닌 모델은 그대로", _paths)


# ---------------------------------------------------------------- S6
def ws_open(s):
    """SockJS 의 websocket 전송으로 연결 (s 가 있으면 그 로그인 쿠키). 연결이 거부되면 예외"""
    import websocket
    url = f"ws://localhost:28091/sockjs/000/{secrets.token_hex(4)}/websocket"
    hdr = ["Cookie: " + "; ".join(f"{k}={v}" for k, v in s.cookies.items())] if s else []
    ws = websocket.create_connection(url, header=hdr, timeout=5)
    assert ws.recv() == "o"
    return ws


def stomp(ws, cmd, headers, body=""):
    ws.send(json.dumps([cmd + "\n" + "".join(f"{k}:{v}\n" for k, v in headers.items()) + "\n" + body + "\x00"]))


def ws_frames(ws, secs):
    """secs 초 동안 받은 STOMP 프레임 (명령, 헤더 dict, 본문)"""
    import websocket
    out, end = [], time.time() + secs
    while time.time() < end:
        ws.settimeout(max(0.1, end - time.time()))
        try:
            m = ws.recv()
        except (websocket.WebSocketTimeoutException, websocket.WebSocketConnectionClosedException):
            break
        if not m or m[0] != "a":
            continue
        for f in json.loads(m[1:]):
            head, _, body = f.partition("\n\n")
            lines = head.split("\n")
            out.append((lines[0], dict(l.split(":", 1) for l in lines[1:] if ":" in l), body.rstrip("\x00")))
    return out


def s6(sa, su):
    def _anon():
        # 로그인 없는 연결은 받는다(q-executor 가 이 연결로 메시지를 보낸다). 헤더에 남의 이름을 넣어도 그 사람 알림은 오지 않아야 한다
        aprv_line(sa, "WORD", ADMIN_STEP)
        ws = ws_open(None)
        stomp(ws, "CONNECT", {"accept-version": "1.2", "heart-beat": "0,0", "useSocketSession": "true", "socketUsername": "space"})
        assert ws_frames(ws, 2)[0][0] == "CONNECTED"
        stomp(ws, "SUBSCRIBE", {"id": "sub-1", "destination": "/user/exchange/amq.direct/message.user"})
        time.sleep(1)
        wnm = "ZZ109익명단어" + TAG
        word(su, wnm, "ZZAN" + TAG, "Zzanword" + TAG)                     # space 에게 결재 요청 알림
        got = ws_frames(ws, 3)
        ws.close()
        msgs = [f for f in got if f[0] == "MESSAGE" and f[1].get("subscription") == "sub-1"]
        print(f"  로그인 없는 연결(헤더 socketUsername=space)이 받은 알림 {len(msgs)}건")
        assert msgs == []
        line_off(sa, "WORD")
    step("S6-1. 로그인 없는 연결 — 헤더에 space 를 넣고 /user/ 알림 구독해도 space 결재 요청 알림이 오지 않음", _anon)

    def _subscribe():
        ws = ws_open(su)
        stomp(ws, "CONNECT", {"accept-version": "1.2", "heart-beat": "0,0", "useSocketSession": "true", "socketUsername": "space"})
        f1 = ws_frames(ws, 2)
        stomp(ws, "SUBSCRIBE", {"id": "sub-x", "destination": "/exchange/amq.direct/message.user-userspace"})
        f2 = ws_frames(ws, 2)
        ws.close()
        print(f"  CONNECT → {[f[0] for f in f1]} / 남의 목적지 구독 → {[(f[0], f[1].get('message')) for f in f2]}")
        assert f1 and f1[0][0] == "CONNECTED"
        assert any(f[0] == "ERROR" for f in f2)
    step("S6-2. jyjang 연결(헤더에 socketUsername=space)에서 /exchange/...message.user-userspace 바로 구독 → ERROR", _subscribe)

    def _own():
        aprv_line(sa, "WORD", ADMIN_STEP)
        ws = ws_open(su)
        stomp(ws, "CONNECT", {"accept-version": "1.2", "heart-beat": "0,0", "useSocketSession": "true", "socketUsername": "space"})
        assert ws_frames(ws, 2)[0][0] == "CONNECTED"
        stomp(ws, "SUBSCRIBE", {"id": "sub-0", "destination": "/topic/onMessage"})
        stomp(ws, "SUBSCRIBE", {"id": "sub-1", "destination": "/user/exchange/amq.direct/message.user"})
        time.sleep(1)
        wnm = "ZZ109알림단어" + TAG
        wid = word(su, wnm, "ZZNT" + TAG, "Zzntword" + TAG)          # space 에게 결재 요청 알림
        got1 = ws_frames(ws, 3)
        assert code(post(sa, "/api/std/putStdAprvStat", [{"reqTp": "WORD", "reqItemId": wid, "reqItemNm": wnm, "aprvStat": 2}])) == 200
        got2 = ws_frames(ws, 3)                                         # jyjang 에게 최종 승인 알림
        ws.close()
        def body(b):
            v = json.loads(b)
            return json.loads(v) if isinstance(v, str) else v
        tps = lambda fs: [body(f[2]).get("notiTp") for f in fs if f[0] == "MESSAGE" and f[1].get("subscription") == "sub-1"]
        print(f"  등록 뒤 받은 알림={tps(got1)} / 승인 뒤 받은 알림={tps(got2)}")
        assert tps(got1) == [] and tps(got2) == ["APRV_DONE"]
        line_off(sa, "WORD")
    step("S6-3. 자기 사용자 목적지(/user/...)는 자기 알림만 — space 결재 요청은 안 오고 자기 최종 승인은 온다", _own)


# ---------------------------------------------------------------- S12
def s12(sa, su):
    def _self():
        ua, ub = "zz109ua" + TAG, "zz109ub" + TAG
        pa = make_user(sa, ua)
        make_user(sa, ub)
        r_ua = make_role(sa, "ZZ109사용자관리_" + TAG, ["USER_ADMIN"])
        assign(sa, ua, r_ua)
        sx = login_api(ua, pa)
        r1 = post(sx, "/api/role/save", {"roleNm": "ZZ109새역할_" + TAG, "roleDesc": "109", "perms": ["DDL_EXEC"]})
        r2 = post(sx, "/api/role/save", {"roleId": r_ua, "roleNm": "ZZ109사용자관리_" + TAG, "roleDesc": "109", "perms": ["USER_ADMIN", "DM_MANAGE"]})
        perms = psql(f"select string_agg(perm_cd, ',' order by perm_cd) from quality.tb_role_perm where role_id='{r_ua}'")
        r3 = post(sx, "/api/role/save", {"roleId": r_ua, "roleNm": "ZZ109사용자관리2_" + TAG, "roleDesc": "이름만", "perms": ["USER_ADMIN"]})
        r4 = post(sx, "/api/role/save", {"roleNm": "ZZ109가진권한_" + TAG, "roleDesc": "109", "perms": ["USER_ADMIN"]})
        rid4 = r4.get("contents")
        if rid4:
            cleanup.append(lambda: (psql(f"delete from quality.tb_user_role where role_id='{rid4}'"),
                                    psql(f"delete from quality.tb_role_perm where role_id='{rid4}'"),
                                    psql(f"delete from quality.tb_role where role_id='{rid4}'")))
        r5 = post(sx, "/api/role/assign", {"userId": ua, "roleId": rid4, "sysCd": "*"})
        r6 = post(sx, "/api/role/assign", {"userId": ub, "roleId": rid4, "sysCd": "*"})
        mine = psql(f"select count(*) from quality.tb_user_role where user_id='{ua}'")
        made = psql(f"select count(*) from quality.tb_role where role_nm='ZZ109새역할_{TAG}'")
        print(f"  없는 권한으로 새 역할={code(r1)} {r1.get('resultMessage')} (만들어진 역할 {made})\n  자기 역할에 DM_MANAGE 추가={code(r2)} 권한={perms}"
              f"\n  자기 역할 이름만 수정={code(r3)} / 가진 권한으로 새 역할={code(r4)}\n  자기에게 지정={code(r5)} {r5.get('resultMessage')} / 남에게 지정={code(r6)} / 내 역할 수={mine}")
        assert code(r1) == 403 and made == "0" and code(r2) == 403 and perms == "USER_ADMIN"
        assert code(r3) == 200 and code(r4) == 200 and code(r5) == 403 and code(r6) == 200 and mine == "1"
        r7 = post(sa, "/api/role/save", {"roleId": r_ua, "roleNm": "ZZ109사용자관리_" + TAG, "roleDesc": "관리자", "perms": ["USER_ADMIN", "DDL_EXEC"]})
        print(f"  관리자가 권한 추가={code(r7)}")
        assert code(r7) == 200
    step("S12-1. USER_ADMIN(관리자 아님) — 없는 권한을 역할에 넣기·자기에게 역할 지정 403, 이름 수정·가진 권한·남에게 지정은 200", _self)


# ---------------------------------------------------------------- S13
def s13(sa, su):
    def _perm():
        outs = {}
        outs["그룹 등록"] = code(post(su, "/api/std/createDomainGroup", {"domainGrpNm": "ZZ109G" + TAG, "commStndYn": "N", "dictId": "DEFAULT"}))
        outs["그룹 수정"] = code(post(su, "/api/std/updateDomainGroup", {"id": "x", "domainGrpNm": "x"}))
        outs["그룹 삭제"] = code(post(su, "/api/std/deleteDomainGroups", [{"id": "x"}]))
        outs["분류 등록"] = code(post(su, "/api/std/createDomainClassification", {"domainClsfNm": "ZZ109C" + TAG, "domainGrpNm": "명칭", "commStndYn": "N", "dictId": "DEFAULT"}))
        outs["분류 수정"] = code(post(su, "/api/std/updateDomainClassification", {"id": "x", "domainClsfNm": "x"}))
        outs["분류 삭제"] = code(post(su, "/api/std/deleteDomainClassifications", [{"id": "x"}]))
        outs["코드값 등록"] = code(post(su, "/api/std/createCodeData", {"codeGrp": "X", "codeVal": "1", "dictId": "DEFAULT"}))
        outs["코드값 수정"] = code(post(su, "/api/std/updateCodeData", {"id": "x", "codeVal": "1"}))
        outs["코드값 삭제"] = code(post(su, "/api/std/deleteCodeDatas", [{"id": "x"}]))
        for path in ("uploadDomainGroups", "uploadDomainClsfs", "uploadCodeDataList"):
            r = su.post(BASE + "/api/std/" + path, files={"file": ("x.xlsx", b"PK", "application/octet-stream")}, data={"dictId": "DEFAULT"}, timeout=30)
            outs[path] = r.json().get("resultCode")
        left = psql(f"select count(*) from quality.tb_domain_grp where domain_grp_nm='ZZ109G{TAG}'") + "/" + \
            psql(f"select count(*) from quality.tb_domain_clsf where domain_clsf_nm='ZZ109C{TAG}'")
        print("  ", outs, "/ 만들어진 그룹·분류", left)
        assert all(v == 403 for v in outs.values()) and left == "0/0"
    step("S13-1. 일반 사용자 — 도메인 그룹·분류·코드값 등록·수정·삭제·엑셀 업로드 403", _perm)

    def _clsf():
        g, c, d = "ZZ109G" + TAG, "ZZ109C" + TAG, "ZZ109CD" + TAG

        def _drop():
            psql(f"delete from quality.tb_domain where domain_nm='{d}'")
            psql(f"delete from quality.tb_domain_clsf where domain_clsf_nm='{c}'")
            psql(f"delete from quality.tb_domain_grp where domain_grp_nm='{g}'")
        cleanup.append(_drop)
        assert code(post(sa, "/api/std/createDomainGroup", {"domainGrpNm": g, "commStndYn": "N", "dictId": "DEFAULT"})) == 200
        assert code(post(sa, "/api/std/createDomainClassification", {"domainClsfNm": c, "domainGrpNm": g, "commStndYn": "N", "dictId": "DEFAULT"})) == 200
        r = post(sa, "/api/std/createDomain", {"domainNm": d, "domainGrpNm": g, "domainClsfNm": c, "domainDesc": "109 시험",
                                               "dataType": "VARCHAR", "dataLen": 10, "dictId": "DEFAULT"})
        assert code(r) == 200, r
        cid = psql(f"select domain_clsf_id from quality.tb_domain_clsf where domain_clsf_nm='{c}'")
        r1 = post(sa, "/api/std/deleteDomainClassifications", [{"id": cid}])
        left = psql(f"select count(*) from quality.tb_domain where domain_nm='{d}'") + "/" + psql(f"select count(*) from quality.tb_domain_clsf where domain_clsf_id='{cid}'")
        print(f"  도메인이 있는 분류 삭제={code(r1)} {r1.get('resultMessage')} / 도메인·분류 남음={left}")
        assert code(r1) == 500 and "도메인 1개" in r1.get("resultMessage") and left == "1/1"
        psql(f"delete from quality.tb_domain where domain_nm='{d}'")
        r2 = post(sa, "/api/std/deleteDomainClassifications", [{"id": cid}])
        gid = psql(f"select domain_grp_id from quality.tb_domain_grp where domain_grp_nm='{g}'")
        r3 = post(sa, "/api/std/deleteDomainGroups", [{"id": gid}])
        print(f"  도메인을 지운 뒤 분류 삭제={code(r2)} / 그룹 삭제={code(r3)}")
        assert code(r2) == 200 and code(r3) == 200
    step("S13-2. 관리자 — 도메인이 있는 분류 삭제 거부(도메인 그대로), 도메인이 없으면 삭제", _clsf)


# ---------------------------------------------------------------- S14
def s14(sa, su):
    def _rename_reject():
        dm = make_model(sa, "ZZ109이름모델_" + TAG)
        o1, o2 = "OWN1", "OWN2"
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": o1, "objNm": "ZZ_T14", "objNmKr": "부모"})) == 200
        assert code(save_attrs(sa, dm, o1, "ZZ_T14", [{"mode": "ADD", "attrNm": "C1", "attrNmKr": "일", "dataType": "VARCHAR", "dataLen": 10, "nullableYn": "N"},
                                                    {"mode": "ADD", "attrNm": "C2", "attrNmKr": "이", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        assert code(post(sa, "/api/dm/createPk", {"dataModelId": dm, "objOwner": o1, "tableNm": "ZZ_T14", "columns": ["C1"]})) == 200
        assert code(post(sa, "/api/dm/addObj", {"dataModelId": dm, "objOwner": o1, "objNm": "ZZ_C14", "objNmKr": "자식"})) == 200
        assert code(save_attrs(sa, dm, o1, "ZZ_C14", [{"mode": "ADD", "attrNm": "P1", "attrNmKr": "부모키", "dataType": "VARCHAR", "dataLen": 10}])) == 200
        r = post(sa, "/api/dm/createFk", {"dataModelId": dm, "objOwner": o1, "tableNm": "ZZ_C14", "refOwner": o1, "refTableNm": "ZZ_T14",
                                          "mappings": [{"ownAttrNm": "P1", "refAttrNm": "C1"}]})
        assert code(r) == 200, r
        udp = "ZZ109UDP" + TAG
        psql(f"insert into quality.tb_udp_def (udp_id, udp_nm, use_yn) values ('{udp}', '109 시험', 'N')")
        psql(f"insert into quality.tb_udp_val (udp_id, dm_id, obj_owner, obj_nm, val) values ('{udp}', '{dm}', '{o1}', 'ZZ_T14', '값')")
        cleanup.append(lambda: (psql(f"delete from quality.tb_udp_val where udp_id='{udp}'"), psql(f"delete from quality.tb_udp_def where udp_id='{udp}'")))

        def where(owner, nm):
            return psql(
                "select (select count(*) from quality.tb_data_model_attr where dm_id='{d}' and obj_owner='{o}' and obj_nm='{n}')||'/'||"
                "(select count(*) from quality.tb_data_model_index where dm_id='{d}' and obj_owner='{o}' and table_nm='{n}')||'/'||"
                "(select count(*) from quality.tb_data_model_constraint where dm_id='{d}' and obj_owner='{o}' and table_nm='{n}')||'/'||"
                "(select count(*) from quality.tb_data_model_constraint where dm_id='{d}' and ref_owner='{o}' and ref_table_nm='{n}')||'/'||"
                "(select count(*) from quality.tb_data_model_attr where dm_id='{d}' and fk_parent_obj_nm='{n}')||'/'||"
                "(select count(*) from quality.tb_udp_val where dm_id='{d}' and obj_owner='{o}' and obj_nm='{n}')||'/'||"
                "(select coalesce(max(aprv_status),'-') from quality.tb_data_model_obj where dm_id='{d}' and obj_owner='{o}' and obj_nm='{n}')"
                .format(d=dm, o=owner, n=nm))
        base = where(o1, "ZZ_T14")
        print(f"  처음 OWN1.ZZ_T14 컬럼/인덱스/제약/참조제약/FK부모/속성값/상태 = {base}")
        r = post(su, "/api/dm/updateObj", {"dataModelId": dm, "origObjNm": "ZZ_T14", "objNm": "ZZ_T14R", "origObjOwner": o1, "objOwner": o2,
                                           "objNmKr": "부모"})
        assert code(r) == 200, r
        moved = where(o2, "ZZ_T14R")
        fkp = psql(f"select coalesce(fk_parent_obj_owner,'(null)')||'.'||fk_parent_obj_nm||'.'||fk_parent_attr_nm from quality.tb_data_model_attr "
                   f"where dm_id='{dm}' and obj_nm='ZZ_C14' and attr_nm='P1'")
        print(f"  이름·소유자 변경(일반 사용자) 뒤 OWN2.ZZ_T14R = {moved} / ZZ_C14.P1 의 FK 부모 = {fkp}")
        seq = seq_of(dm, "MODIFY_OBJ", "ZZ_T14R", status="DRAFT")
        r = post(sa, "/api/dmApproval/reject", {"changeSeqList": [seq], "aprvComment": "이름 변경 반려"})
        back, left = where(o1, "ZZ_T14"), where(o2, "ZZ_T14R")
        fkp = psql(f"select coalesce(fk_parent_obj_owner,'(null)')||'.'||fk_parent_obj_nm||'.'||fk_parent_attr_nm from quality.tb_data_model_attr "
                   f"where dm_id='{dm}' and obj_nm='ZZ_C14' and attr_nm='P1'")
        print(f"  반려={code(r)} {r.get('resultMessage')}\n  반려 뒤 OWN1.ZZ_T14 = {back} / OWN2.ZZ_T14R = {left} / ZZ_C14.P1 의 FK 부모 = {fkp}")
        assert base == "2/1/1/1/1/1/APPROVED" and moved.startswith("2/1/1/1/1/1/")
        assert code(r) == 200 and back == base and left == "0/0/0/0/0/0/-"
    step("S14-1. 테이블 이름·소유자 변경 반려 — 컬럼·인덱스·제약조건·참조 제약조건·FK 부모·속성 값이 변경 전 이름으로", _rename_reject)


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
