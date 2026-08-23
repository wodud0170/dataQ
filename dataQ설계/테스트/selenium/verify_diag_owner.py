# -*- coding: utf-8 -*-
"""결함 ⑩ 검증 — 진단 결과가 소유자(스키마)를 구분하는가.

증상이었던 것:
  다중 스키마 모델에서 TB_DIAG_RESULT 에 OBJ_OWNER 가 없어 `R.OBJ_NM = O.OBJ_NM`
  조인이 팬아웃 → 같은 이름 다른 스키마 테이블이 서로의 이슈를 물려받고,
  "전체 테이블 19 / 이슈 테이블 22" 처럼 이슈가 전체보다 많아졌다.

검증 방식:
  진단을 새로 돌린 뒤, 화면이 쓰는 집계(요약 API)의 이슈 건수 합이
  실제 저장된 이슈 건수와 정확히 일치하는지 본다. 팬아웃이 있으면 합이 더 크다.
"""
import base64
import subprocess
import sys
import time

import requests

BASE = "http://localhost:28091"
MODEL_NM = "오라클테스트"


def unwrap(resp):
    """진단 조회 API 는 배열을 그대로 준다. 래핑 형태도 대비."""
    body = resp.json()
    if isinstance(body, list):
        return body
    return body.get("data") or body.get("result") or []


def psql(sql):
    out = subprocess.run(
        ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
         "-tAc", "SET search_path TO quality; " + sql],
        capture_output=True, text=True, timeout=30, encoding="utf-8")
    return (out.stdout or "").strip()


def main():
    # Spring Security formLogin — NdLogin.vue 와 동일하게 password 는 base64
    s = requests.Session()
    r = s.post(BASE + "/login",
               data={"id": "space", "password": base64.b64encode(b"123").decode("ascii")},
               allow_redirects=False, timeout=15)
    if r.status_code != 200:
        print("[FAIL] 로그인 %s %s" % (r.status_code, r.text[:150]))
        return 1
    print("[1] 로그인 OK")

    dm_id = psql("SELECT DM_ID FROM TB_DATA_MODEL WHERE DM_NM='%s' LIMIT 1;" % MODEL_NM)
    if not dm_id:
        print("[FAIL] 모델 '%s' 없음" % MODEL_NM)
        return 1
    print("[2] 모델 %s = %s" % (MODEL_NM, dm_id))

    owners = psql("SELECT COUNT(DISTINCT OBJ_OWNER) FROM TB_DATA_MODEL_OBJ WHERE DM_ID='%s';" % dm_id)
    dup = psql("SELECT COUNT(*) FROM (SELECT OBJ_NM FROM TB_DATA_MODEL_OBJ WHERE DM_ID='%s' "
               "GROUP BY OBJ_NM HAVING COUNT(DISTINCT OBJ_OWNER)>1) t;" % dm_id)
    print("[3] 스키마 %s개 / 이름 중복 테이블 %s개 — 중복이 0이면 이 검증은 무의미" % (owners, dup))
    if dup == "0":
        print("[FAIL] 중복 테이블이 없어 결함을 재현할 수 없다")
        return 1

    r = s.post(BASE + "/api/diag/startDiag", json={"dataModelId": dm_id})
    if r.status_code != 200 or r.json().get("code") not in (200, 0, None):
        print("[FAIL] 진단 시작 %s %s" % (r.status_code, r.text[:200]))
        return 1
    print("[4] 진단 시작 요청 OK")

    job = None
    for _ in range(60):
        time.sleep(2)
        row = psql("SELECT DIAG_JOB_ID||'|'||STATUS FROM TB_DIAG_JOB WHERE DM_ID='%s' "
                   "ORDER BY CRET_DT DESC LIMIT 1;" % dm_id)
        if not row:
            continue
        job, status = row.split("|")
        if status in ("DONE", "ERROR", "STOPPED"):
            print("[5] 진단 종료 status=%s job=%s" % (status, job))
            if status != "DONE":
                print("[FAIL] 진단이 정상 종료되지 않음")
                return 1
            break
    else:
        print("[FAIL] 진단 타임아웃")
        return 1

    stored = int(psql("SELECT COUNT(*) FROM TB_DIAG_RESULT WHERE DIAG_JOB_ID='%s';" % job))
    null_owner = int(psql("SELECT COUNT(*) FROM TB_DIAG_RESULT WHERE DIAG_JOB_ID='%s' "
                          "AND OBJ_OWNER IS NULL;" % job))
    print("[6] 저장된 이슈 %d건 / OBJ_OWNER 누락 %d건" % (stored, null_owner))
    if null_owner:
        print("[FAIL] 새 진단인데 OBJ_OWNER 가 비어 있다 — executor 가 안 채우고 있음")
        return 1

    r = s.get(BASE + "/api/diag/getDiagResultSummary", params={"diagJobId": job})
    rows = unwrap(r)
    if not rows:
        print("[FAIL] 요약 API 응답 비어 있음: %s" % r.text[:200])
        return 1
    summed = sum(int(x.get("issueCnt") or 0) for x in rows)
    issue_tbl = sum(1 for x in rows if int(x.get("issueCnt") or 0) > 0)
    total_tbl = len(rows)
    print("[7] 요약 API — 전체 테이블 %d / 이슈 테이블 %d / 이슈 건수합 %d"
          % (total_tbl, issue_tbl, summed))

    ok = True
    if summed != stored:
        print("[FAIL] 집계 %d != 저장 %d — 조인 팬아웃이 남아 있다" % (summed, stored))
        ok = False
    else:
        print("[PASS] 집계 = 저장 (%d) — 팬아웃 없음" % stored)

    if issue_tbl > total_tbl:
        print("[FAIL] 이슈 테이블(%d) > 전체 테이블(%d)" % (issue_tbl, total_tbl))
        ok = False
    else:
        print("[PASS] 이슈 테이블 %d <= 전체 테이블 %d" % (issue_tbl, total_tbl))

    # 중복 이름 테이블이 스키마별로 서로 다른 값을 갖는지
    dupname = psql("SELECT OBJ_NM FROM TB_DATA_MODEL_OBJ WHERE DM_ID='%s' "
                   "GROUP BY OBJ_NM HAVING COUNT(DISTINCT OBJ_OWNER)>1 LIMIT 1;" % dm_id)
    per = [x for x in rows if x.get("objNm") == dupname]
    print("[8] 중복 이름 '%s' 스키마별 이슈: %s"
          % (dupname, ", ".join("%s=%s" % (x.get("objOwner"), x.get("issueCnt")) for x in per)))
    if len({x.get("issueCnt") for x in per}) == 1 and len(per) > 1:
        print("      (모두 같은 값 — 우연일 수 있으나 팬아웃 잔존 가능성)")

    detail = s.get(BASE + "/api/diag/getDiagResultDetail", params={"diagJobId": job})
    drows = unwrap(detail)
    dup_key = len(drows) - len({(x.get("objOwner"), x.get("objNm"), x.get("attrNm")) for x in drows})
    print("[9] 컬럼 상세 %d행 / (소유자,테이블,컬럼) 중복 %d행" % (len(drows), dup_key))
    if dup_key:
        print("[FAIL] 컬럼 상세에 중복 행이 있다")
        ok = False
    else:
        print("[PASS] 컬럼 상세 중복 없음")

    print("\n%s" % ("=" * 56))
    print("결과: %s" % ("PASS — 결함 ⑩ 해소" if ok else "FAIL — 아직 남아 있음"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
