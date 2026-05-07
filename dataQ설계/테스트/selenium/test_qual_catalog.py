"""
83번 Step 2 — 카탈로그 시스템 기본 시드 + 사용자 정의 CRUD + 권한 + fork 검증.

검증 범위 (15+ 케이스):
  P1.  TB_QUAL_RULE_CATALOG 컬럼 IS_BUILT_IN / DOMAIN_CLSF_NM 추가 검증
  P2.  시스템 기본 시드 43건 — `IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%'`
  P3.  도메인 분류 매칭 42건 — DOMAIN_CLSF_NM 채워짐 (NOT_NULL 1건만 NULL)
  P4.  분류 'tel*' 검색 — 전화번호/팩스번호/휴대전화번호 3건
  P5.  분류 '금액' 검색 — RANGE min=0 1건
  P6.  관리자 — 사용자 정의 룰 추가 (POST /catalog/save)
  P7.  사용자 정의 룰 수정 (POST /catalog/save with catalogId)
  P8.  사용자 정의 룰 삭제 (POST /catalog/delete)
  P9.  시스템 기본 룰 수정 시도 → 거부 (가드 동작)
  P10. 시스템 기본 룰 삭제 시도 → 거부 (가드 동작)
  P11. 시스템 기본 [복사] (fork) — 사용자 정의 신규 row 자동 생성
  P12. 사용자 정의 [복사] — 동일하게 fork 동작
  P13. 일반 사용자 (jyjang) — 카탈로그 조회는 OK (admin 가드 X)
  P14. 일반 사용자 — 사용자 정의 추가 시도 → 403
  P15. 카탈로그 검색 필터 (isBuiltIn=Y, domainClsfNm=전화번호) 정확성
  P16. 시드 SQL 멱등성 — 재실행해도 43건 유지

매퍼 & Controller 동시 검증.
"""
import base64
import json
import subprocess
import sys
import time
import traceback

import requests

BASE = "http://localhost:28091"
ADMIN_ID = "space"
ADMIN_PW = "123"
USER_ID  = "jyjang"
USER_PW  = "123"

results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception as e:
        traceback.print_exc()
        results.append((name, "FAIL"))


def docker_psql(sql):
    cmd = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
           "-t", "-A", "-c", "SET search_path TO quality;" + sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def login(user_id, pw):
    s = requests.Session()
    enc = base64.b64encode(pw.encode()).decode()
    r = s.post(BASE + "/login", data={"id": user_id, "password": enc},
               allow_redirects=False, timeout=10)
    assert r.status_code == 200, f"로그인 실패 ({user_id}): {r.status_code}"
    return s


def cleanup_user_seed():
    docker_psql("DELETE FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID LIKE 'TEST_USER_%' OR CATALOG_ID LIKE 'TEST_FORK_%';")


def main():
    cleanup_user_seed()
    admin = login(ADMIN_ID, ADMIN_PW)
    user  = login(USER_ID,  USER_PW)

    # P1. 컬럼 추가 검증
    def _p1():
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM information_schema.columns "
            "WHERE table_schema='quality' AND table_name='tb_qual_rule_catalog' "
            "AND column_name IN ('is_built_in','domain_clsf_nm');"))
        assert cnt == 2, f"2 컬럼 기대, 실제 {cnt}"
    step("P1. IS_BUILT_IN / DOMAIN_CLSF_NM 컬럼 추가", _p1)

    # P2. 시드 43건
    def _p2():
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG "
            "WHERE IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%';"))
        assert cnt == 43, f"시드 43건 기대, 실제 {cnt}"
    step("P2. 시스템 기본 시드 43건", _p2)

    # P3. 도메인 분류 42건 (1건 NOT_NULL 은 분류 NULL)
    def _p3():
        with_clsf = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG "
            "WHERE IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%' AND DOMAIN_CLSF_NM IS NOT NULL;"))
        without_clsf = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG "
            "WHERE IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%' AND DOMAIN_CLSF_NM IS NULL;"))
        assert with_clsf == 42, f"분류 매칭 42건 기대, 실제 {with_clsf}"
        assert without_clsf == 1, f"분류 NULL 1건 기대, 실제 {without_clsf}"
    step("P3. 분류 매칭 42건 + 공통(NULL) 1건", _p3)

    # P4. tel/팩스/휴대 3건
    def _p4():
        rows = admin.get(BASE + "/api/qual/rule/catalog",
                         params={"isBuiltIn": "Y"}, timeout=10).json()
        # 분류 ['전화번호','팩스번호','휴대전화번호']
        tel = [r for r in rows if r.get("domainClsfNm") in ("전화번호","팩스번호","휴대전화번호")]
        assert len(tel) == 3, f"3건 기대, 실제 {len(tel)} — {[r['domainClsfNm'] for r in tel]}"
    step("P4. 전화번호/팩스/휴대 분류 3건", _p4)

    # P5. 금액 1건
    def _p5():
        rows = admin.get(BASE + "/api/qual/rule/catalog",
                         params={"isBuiltIn": "Y", "domainClsfNm": "금액"}, timeout=10).json()
        assert len(rows) == 1, f"금액 1건 기대, 실제 {len(rows)}"
        params = json.loads(rows[0]["ruleParams"])
        assert params.get("min") == 0, f"min=0 기대, {params}"
    step("P5. 금액 분류 RANGE min=0 1건", _p5)

    # P6. 사용자 정의 룰 추가
    def _p6():
        body = {
            "catalogId": "TEST_USER_001",
            "catalogNm": "테스트 룰 1",
            "ruleType": "REGEX",
            "ruleParams": '{"pattern":"^[A-Z]+$"}',
            "category": "테스트",
            "descr": "단위 검증용",
            "domainClsfNm": "전화번호",
            "useYn": "Y"
        }
        r = admin.post(BASE + "/api/qual/rule/catalog/save", json=body, timeout=10)
        assert r.status_code == 200, f"{r.status_code}"
        result = r.json()
        assert result.get("resultCode") == 200, f"{result}"
        # DB 확인
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG "
            "WHERE CATALOG_ID='TEST_USER_001' AND IS_BUILT_IN='N';"))
        assert cnt == 1, "INSERT 후 1건 기대"
    step("P6. 사용자 정의 룰 추가 (admin)", _p6)

    # P7. 사용자 정의 룰 수정
    def _p7():
        body = {
            "catalogId": "TEST_USER_001",
            "catalogNm": "테스트 룰 1 (수정됨)",
            "ruleType": "REGEX",
            "ruleParams": '{"pattern":"^\\\\d+$"}',
            "category": "테스트",
            "descr": "수정 후",
            "domainClsfNm": "전화번호"
        }
        r = admin.post(BASE + "/api/qual/rule/catalog/save", json=body, timeout=10)
        assert r.json().get("resultCode") == 200, r.text
        nm = docker_psql(
            "SELECT CATALOG_NM FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='TEST_USER_001';")
        assert nm == "테스트 룰 1 (수정됨)", f"수정 반영 안 됨: {nm}"
    step("P7. 사용자 정의 룰 수정", _p7)

    # P8. 사용자 정의 룰 삭제
    def _p8():
        r = admin.post(BASE + "/api/qual/rule/catalog/delete",
                        json={"catalogId": "TEST_USER_001"}, timeout=10)
        assert r.json().get("resultCode") == 200, r.text
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='TEST_USER_001';"))
        assert cnt == 0, "삭제 후 0건"
    step("P8. 사용자 정의 룰 삭제", _p8)

    # P9. 시스템 기본 룰 수정 시도 → 거부
    def _p9():
        body = {
            "catalogId": "SEED_TEL_PHONE",
            "catalogNm": "해킹 시도",
            "ruleType": "REGEX",
            "ruleParams": '{}',
            "category": "X"
        }
        r = admin.post(BASE + "/api/qual/rule/catalog/save", json=body, timeout=10)
        rc = r.json()
        assert rc.get("resultCode") == 500, f"500 기대, 실제 {rc}"
        # 원본 보존 확인
        nm = docker_psql("SELECT CATALOG_NM FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='SEED_TEL_PHONE';")
        assert nm == "전화번호 형식", f"원본 변형 — {nm}"
    step("P9. 시스템 기본 룰 수정 시도 → 거부 + 원본 보존", _p9)

    # P10. 시스템 기본 룰 삭제 시도 → 거부
    def _p10():
        r = admin.post(BASE + "/api/qual/rule/catalog/delete",
                        json={"catalogId": "SEED_TEL_PHONE"}, timeout=10)
        rc = r.json()
        assert rc.get("resultCode") == 500, f"500 기대, 실제 {rc}"
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='SEED_TEL_PHONE';"))
        assert cnt == 1, "원본 보존"
    step("P10. 시스템 기본 룰 삭제 시도 → 거부 + 원본 보존", _p10)

    # P11. 시스템 기본 [복사] (fork)
    def _p11():
        r = admin.post(BASE + "/api/qual/rule/catalog/fork",
                        json={"srcCatalogId": "SEED_TEL_PHONE"}, timeout=10)
        rc = r.json()
        assert rc.get("resultCode") == 200, f"200 기대, 실제 {rc}"
        new_id = rc.get("contents")
        assert new_id, "신규 catalogId 반환 기대"
        # 신규 row 가 IS_BUILT_IN='N' + 이름 '(복사본)'
        row = docker_psql(
            f"SELECT IS_BUILT_IN || '|' || CATALOG_NM FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='{new_id}';")
        assert row.startswith("N|"), f"IS_BUILT_IN='N' 기대, {row}"
        assert "(복사본)" in row, f"이름에 (복사본) 기대, {row}"
        # 추적용 저장
        docker_psql(f"UPDATE TB_QUAL_RULE_CATALOG SET CATALOG_ID='TEST_FORK_001' WHERE CATALOG_ID='{new_id}';")
    step("P11. 시스템 기본 [복사] (fork) — 사용자 정의 신규 row + (복사본)", _p11)

    # P12. 사용자 정의 [복사] (다시 사용자 정의로 복제)
    def _p12():
        r = admin.post(BASE + "/api/qual/rule/catalog/fork",
                        json={"srcCatalogId": "TEST_FORK_001",
                              "newCatalogNm": "수동 이름 지정 복사본"}, timeout=10)
        rc = r.json()
        assert rc.get("resultCode") == 200, rc
        new_id = rc.get("contents")
        nm = docker_psql(f"SELECT CATALOG_NM FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='{new_id}';")
        assert nm == "수동 이름 지정 복사본", f"이름 override 기대, {nm}"
        docker_psql(f"DELETE FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='{new_id}';")
    step("P12. 사용자 정의 [복사] + 이름 override", _p12)

    # P13. 일반 사용자 — 카탈로그 조회 OK
    def _p13():
        r = user.get(BASE + "/api/qual/rule/catalog",
                      params={"isBuiltIn": "Y"}, timeout=10)
        assert r.status_code == 200, r.status_code
        rows = r.json()
        assert len(rows) >= 40, f"일반 사용자 조회 기대 (40+ 시드), 실제 {len(rows)}"
    step("P13. 일반 사용자 카탈로그 조회 OK", _p13)

    # P14. 일반 사용자 — 추가 시도 시 거부
    def _p14():
        body = {
            "catalogId": "TEST_USER_DENY",
            "catalogNm": "권한 미달",
            "ruleType": "REGEX",
            "ruleParams": "{}",
        }
        r = user.post(BASE + "/api/qual/rule/catalog/save", json=body, timeout=10)
        # admin 가드 → 500 + "관리자" 메시지 또는 401/403. 어느쪽이든 비-200
        rc = r.json()
        assert rc.get("resultCode") != 200, f"비-200 기대, 실제 {rc}"
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='TEST_USER_DENY';"))
        assert cnt == 0, "일반 사용자 추가 차단 — DB 변경 없어야"
    step("P14. 일반 사용자 추가 시도 → 거부", _p14)

    # P15. 검색 필터 정확
    def _p15():
        rows = admin.get(BASE + "/api/qual/rule/catalog",
                         params={"isBuiltIn": "Y", "domainClsfNm": "전화번호"}, timeout=10).json()
        assert len(rows) == 1, f"전화번호 1건 기대, {len(rows)}"
        assert rows[0]["catalogId"] == "SEED_TEL_PHONE"
    step("P15. 검색 필터 isBuiltIn=Y + 분류=전화번호 정확", _p15)

    # P16. 시드 SQL 멱등성 (재실행)
    def _p16():
        # 시드 SQL 파일 다시 실행
        with open(r"C:\Users\장재영\Desktop\dataQ\dataQ설계\sync\qual_rule_catalog_seed_2026-05-07.sql",
                  encoding="utf-8") as f:
            sql_text = f.read()
        cmd = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres"]
        r = subprocess.run(cmd, input=sql_text, capture_output=True, text=True, encoding="utf-8")
        # 결과 시드 43건 유지 (DELETE-INSERT 멱등)
        cnt = int(docker_psql(
            "SELECT COUNT(*) FROM TB_QUAL_RULE_CATALOG "
            "WHERE IS_BUILT_IN='Y' AND CATALOG_ID LIKE 'SEED_%';"))
        assert cnt == 43, f"멱등 재실행 후 43건 기대, 실제 {cnt}"
    step("P16. 시드 SQL 멱등성 (재실행 OK)", _p16)

    # cleanup
    docker_psql("DELETE FROM TB_QUAL_RULE_CATALOG WHERE CATALOG_ID='TEST_FORK_001';")


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.1f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
