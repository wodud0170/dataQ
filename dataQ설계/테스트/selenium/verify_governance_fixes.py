# -*- coding: utf-8 -*-
"""거버넌스·표준 결함 수정 검증 (DEF-04/05/06/07/09/12/13/14/15).

각 항목을 "고쳤다" 가 아니라 "고쳐진 상태에서만 통과하는 조건" 으로 확인한다.
실패하면 어느 결함이 남았는지 번호로 나온다.
"""
import base64
import subprocess
import sys

import requests

BASE = "http://localhost:28091"
ADMIN = ("space", "123")
USER = ("jyjang", "123")

results = []


def psql(sql):
    out = subprocess.run(
        ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
         "-tAc", "SET search_path TO quality; " + sql],
        capture_output=True, text=True, timeout=30, encoding="utf-8")
    return (out.stdout or "").strip()


def login(user, pw):
    s = requests.Session()
    r = s.post(BASE + "/login",
               data={"id": user, "password": base64.b64encode(pw.encode()).decode("ascii")},
               allow_redirects=False, timeout=15)
    if r.status_code != 200:
        raise RuntimeError("로그인 실패 %s %s" % (user, r.status_code))
    return s


def check(defect, desc, ok, detail=""):
    results.append((defect, desc, ok, detail))
    print("  [%s] %-9s %s%s" % ("PASS" if ok else "FAIL", defect, desc,
                                ("  — " + detail) if detail else ""))


def main():
    admin = login(*ADMIN)
    try:
        user = login(*USER)
    except Exception as e:
        print("일반 사용자 로그인 실패 — 권한 검증은 건너뛴다: %s" % e)
        user = None

    print("\n[DEF-12] 진단 기준 용어에 승인 필터")
    n = psql("SELECT COUNT(*) FROM TB_TERMS WHERE COALESCE(APRV_YN,'N') <> 'Y';")
    check("DEF-12", "미승인 용어 %s건 — 진단 쿼리에 APRV_YN='Y' 존재" % n,
          "APRV_YN = 'Y'" in open(
              "../../../q-common/src/main/resources/mapper/stnd/terms.xml",
              encoding="utf-8").read().split("selectAllTermsForDiag")[1][:600])

    print("\n[DEF-15] 승인 API 관리자 전용")
    if user:
        r = user.post(BASE + "/api/std/putStdAprvStat",
                      json=[{"reqTp": "WORD", "reqItemId": "nonexistent", "aprvStat": 2}],
                      timeout=15)
        body = r.text[:200]
        blocked = ('"code":403' in body.replace(" ", "")
                   or "관리자만" in body or r.status_code == 403)
        check("DEF-15", "일반 사용자의 승인 호출 차단", blocked, body[:90])
    else:
        check("DEF-15", "일반 사용자 계정 없음 — 미검증", False, "jyjang 로그인 실패")

    print("\n[DEF-09/14] 종료 상태 재전이 차단")
    seq = psql("SELECT CHANGE_SEQ FROM TB_DATA_MODEL_CHANGE_HISTORY "
               "WHERE APRV_STATUS = 'APPROVED' ORDER BY CHANGE_SEQ DESC LIMIT 1;")
    if seq:
        before = psql("SELECT APRV_STATUS FROM TB_DATA_MODEL_CHANGE_HISTORY "
                      "WHERE CHANGE_SEQ = %s;" % seq)
        r = admin.post(BASE + "/api/dmApproval/reject",
                       json={"changeSeqList": [int(seq)], "aprvComment": "가드 테스트"},
                       timeout=20)
        after = psql("SELECT APRV_STATUS FROM TB_DATA_MODEL_CHANGE_HISTORY "
                     "WHERE CHANGE_SEQ = %s;" % seq)
        body = r.text[:160]
        # 405/404 는 엔드포인트를 못 찾은 것 — 가드가 동작한 증거가 아니다.
        reached = r.status_code == 200
        rejected_by_guard = "이미 처리된" in body or '"resultCode":500' in body.replace(" ", "")
        check("DEF-09", "APPROVED 항목의 반려 거부 (상태 유지)",
              reached and before == after == "APPROVED" and rejected_by_guard,
              "before=%s after=%s http=%s body=%s" % (before, after, r.status_code, body[:70]))
    else:
        check("DEF-09", "APPROVED 이력 없음 — 미검증", False)

    print("\n[DEF-05] 승인 전 물리 삭제 금지 (소프트 삭제)")
    src = open("../../../q-center/src/main/java/qualitycenter/controller/DataModelController.java",
               encoding="utf-8").read()
    check("DEF-05", "삭제 3경로가 softDeletePending 분기를 가짐",
          src.count("softDeleteAttrPending") >= 2 and "softDeleteObjPending" in src,
          "attr %d회 / obj %s" % (src.count("softDeleteAttrPending"),
                                  "있음" if "softDeleteObjPending" in src else "없음"))
    check("DEF-05", "단건 deleteAttr 도 변경 이력 기록",
          '"DEL_ATTR", "TIER1"' in src and src.count('"DEL_ATTR"') >= 2)

    apv = open("../../../q-center/src/main/java/qualitycenter/controller/DataModelApprovalController.java",
               encoding="utf-8").read()
    check("DEF-05", "승인 시 확정 삭제 / 반려 시 복구 경로 존재",
          "restoreAttrPendingDelete" in apv and "restoreObjPendingDelete" in apv)

    print("\n[DEF-04] 수정 반려 시 원복")
    check("DEF-04", "MODIFY_OBJ 가 before/after JSON 기록",
          "beforeObj" in src and "toJsonSafe(beforeObj)" in src)
    check("DEF-04", "반려 시 restoreFromBeforeJson 호출", "restoreFromBeforeJson" in apv)

    print("\n[DEF-06/07] 반려 전파 (손자까지 + 이력 동기화)")
    check("DEF-06", "반복 전파 (큐 기반)", "cascadeRejectFrom" in apv and "queue" in apv)
    check("DEF-07", "이력도 함께 반려",
          "cascadeRejectHistoryByAttr" in apv and "cascadeRejectHistoryInObj" in apv)

    print("\n[DEF-13] 코드도 구성단어 승인 가드")
    ds = open("../../../q-center/src/main/java/qualitycenter/controller/DataStandardController.java",
              encoding="utf-8").read()
    check("DEF-13", "CODE 가 TERMS 와 같은 가드를 탐",
          "NDQualityStdObjectType.CODE" in ds.split("selectUnapprovedWordsByTermsId")[0][-500:])

    print("\n[CHANGE_SOURCE] 등록 경로 기록")
    check("CHG-SRC", "saveChangeHistory 가 changeSource 를 넣음",
          'history.put("changeSource"' in ds)

    print("\n[수집 동시성]")
    check("CONC", "collectDataModel 에 진행 중 수집 가드",
          "selectActiveCollectCount" in src)

    print("\n[PostgreSQL 수집]")
    dmc = open("../../../q-executor/src/main/resources/META-INF/dm-collect.xml",
               encoding="utf-8").read()
    have = [q for q in ("PostgreSQLGetObjs", "PostgreSQLGetAttrs",
                        "PostgreSQLGetIndexes", "PostgreSQLGetConstraints") if q in dmc]
    check("PG", "수집 쿼리 4종", len(have) == 4, "%d/4" % len(have))

    print("\n[품질 진단 소유자]")
    cols = psql("SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema='quality' AND lower(column_name)='obj_owner' "
                "AND lower(table_name) IN ('tb_qual_col_rule','tb_qual_rule_result','tb_qual_running_lock');")
    check("QUAL", "3개 테이블에 OBJ_OWNER", cols == "3", "%s/3" % cols)

    print("\n[DEF-02/03] 삭제·rename cascade")
    check("DEF-02", "테이블 삭제 시 인덱스·제약·역참조 정리",
          "cascadeDeleteObjRefs" in src)
    check("DEF-03", "테이블 rename 시 FK 부모명 갱신",
          "renameObjFkParentCascade" in src)

    ok = sum(1 for _, _, r, _ in results if r)
    print("\n%s\n결과: %d PASS / %d FAIL\n%s"
          % ("=" * 62, ok, len(results) - ok, "=" * 62))
    for d, desc, r, detail in results:
        if not r:
            print("  [FAIL] %-9s %s %s" % (d, desc, detail))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
