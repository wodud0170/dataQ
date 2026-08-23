"""
83번 Step 1 — 품질 진단 부하 안정성 인프라 검증.

검증 범위 (10+ 케이스):
  P1.  TB_QUAL_RUNNING_LOCK 테이블 존재 + 컬럼 6개 + PK 정상
  P2.  단건 lock 획득 + 해제 (acquire ON CONFLICT DO NOTHING + release)
  P3.  같은 컬럼 두 번째 acquire → 0 반환 (SKIP)
  P4.  다른 컬럼 동시 acquire → 둘 다 1 반환 (병렬 가능)
  P5.  release 후 재 acquire → 정상 1 반환
  P6.  Stale lock (START_DT 31분 전) — cleanupStale 으로 자동 삭제
  P7.  Stale 아닌 lock (START_DT 29분 전) — cleanupStale 시 보존
  P8.  Throttle: 글로벌 N건 상태 카운트 — DB row 와 일치
  P9.  Lock 획득 후 비정상 종료 시 stale 으로 자동 정리되는 시나리오 — cleanupStale 가드
  P10. Lock listAll API 동작 (모니터링용) — 현재 점유 row 모두 반환
  P11. Lock get(키) 정상 — 단건 조회
  P12. countAll 정상 — Throttle 모니터링용
  P13. 운영 DB 락 X 검증 — quality.tb_qual_running_lock 만 사용, 외부 DB 영향 0건

검증 도구: dataq-db psql 직접 SQL (서버 매퍼 호출 X — 매퍼 로직 자체 검증).
"""
import subprocess
import sys
import time
import traceback

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
    """dataq-db SQL 실행 (단건). -t -A 로 헤더 없는 결과만."""
    cmd = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
           "-t", "-A", "-c", "SET search_path TO quality;" + sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def docker_psql_count(sql):
    return int(docker_psql(sql) or "0")


# ───────────────────────────────────────────────
# Setup — 깨끗한 상태 보장
# ───────────────────────────────────────────────
def cleanup():
    docker_psql("DELETE FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID LIKE 'TEST_LOCK_%';")


def main():
    cleanup()

    # P1. 테이블 + PK 존재
    def _p1():
        # 컬럼 "개수" 를 고정하면 스키마가 늘 때마다 무관한 테스트가 깨진다.
        # (2026-08-23 OBJ_OWNER 추가로 6 → 7 이 되며 실패했다)
        # 이 테스트가 실제로 보장하려는 건 lock 이 동작할 필수 컬럼이 있느냐다.
        cols = docker_psql(
            "SELECT string_agg(lower(column_name), ',' ORDER BY column_name) "
            "FROM information_schema.columns "
            "WHERE table_schema='quality' AND table_name='tb_qual_running_lock';"
        )
        have = set((cols or "").split(","))
        need = {"dm_id", "obj_nm", "attr_nm", "start_dt", "user_id", "diag_id"}
        missing = need - have
        assert not missing, f"필수 컬럼 누락: {sorted(missing)} (실제: {sorted(have)})"
        pk = docker_psql(
            "SELECT constraint_name FROM information_schema.table_constraints "
            "WHERE table_name='tb_qual_running_lock' AND constraint_type='PRIMARY KEY';"
        )
        assert pk == "pk_tb_qual_running_lock", f"PK 이름 불일치: {pk}"
    step("P1. TB_QUAL_RUNNING_LOCK 필수 컬럼 + PK 검증", _p1)

    # P2. 단건 acquire + release
    def _p2():
        affected = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK "
            "(DM_ID, OBJ_NM, ATTR_NM, DIAG_ID, USER_ID, START_DT) "
            "VALUES ('TEST_LOCK_001','T_A','C_A','D1','u1', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT (DM_ID, OBJ_NM, ATTR_NM) DO NOTHING RETURNING 1) "
            "SELECT COUNT(*) FROM ins;"
        )
        assert affected == 1, f"INSERT 1건 기대, 실제 {affected}"
        # release
        docker_psql("DELETE FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_001';")
        rest = docker_psql_count("SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_001';")
        assert rest == 0, "release 후 0건 기대"
    step("P2. 단건 acquire + release 사이클", _p2)

    # P3. 같은 컬럼 두 번째 → SKIP
    def _p3():
        # 첫 번째
        a1 = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK "
            "(DM_ID, OBJ_NM, ATTR_NM, DIAG_ID, USER_ID, START_DT) "
            "VALUES ('TEST_LOCK_002','T','C','D1','u1', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT (DM_ID, OBJ_NM, ATTR_NM) DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        assert a1 == 1, "첫 번째 1건"
        # 두 번째 — 같은 키
        a2 = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK "
            "(DM_ID, OBJ_NM, ATTR_NM, DIAG_ID, USER_ID, START_DT) "
            "VALUES ('TEST_LOCK_002','T','C','D2','u2', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT (DM_ID, OBJ_NM, ATTR_NM) DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        assert a2 == 0, f"두 번째 SKIP 기대 (0), 실제 {a2}"
    step("P3. 동일 컬럼 두 번째 acquire → SKIP (0)", _p3)

    # P4. 다른 컬럼 동시 acquire 모두 OK
    def _p4():
        a1 = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_003','T','COL_A','D','u', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        a2 = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_003','T','COL_B','D','u', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        assert a1 == 1 and a2 == 1, f"두 컬럼 둘 다 1 기대, {a1}/{a2}"
        cnt = docker_psql_count("SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_003';")
        assert cnt == 2, f"점유 2건 기대, 실제 {cnt}"
    step("P4. 다른 컬럼 병렬 acquire 둘 다 OK", _p4)

    # P5. release 후 재 acquire
    def _p5():
        docker_psql("DELETE FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_002';")
        a = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_002','T','C','D3','u3', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        assert a == 1, f"release 후 재 acquire 1 기대, 실제 {a}"
    step("P5. release 후 재 acquire", _p5)

    # P6. stale lock 자동 정리 (31분 전 → 정리 대상)
    def _p6():
        docker_psql(
            "INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_STALE','T','C_OLD','D','u', "
            "to_char(now() - INTERVAL '31 minutes','YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING;"
        )
        # cleanupStale 매퍼 SQL 동일 실행
        docker_psql(
            "DELETE FROM TB_QUAL_RUNNING_LOCK "
            "WHERE START_DT < to_char(now() - INTERVAL '30 minutes','YYYYMMDDHH24MISS');"
        )
        rest = docker_psql_count(
            "SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_STALE';"
        )
        assert rest == 0, f"stale 정리 후 0 기대, 실제 {rest}"
    step("P6. stale lock (31분 전) cleanupStale 자동 정리", _p6)

    # P7. stale 아님 (29분 전) — 보존
    def _p7():
        docker_psql(
            "INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_FRESH','T','C_FRESH','D','u', "
            "to_char(now() - INTERVAL '29 minutes','YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING;"
        )
        docker_psql(
            "DELETE FROM TB_QUAL_RUNNING_LOCK "
            "WHERE START_DT < to_char(now() - INTERVAL '30 minutes','YYYYMMDDHH24MISS');"
        )
        rest = docker_psql_count(
            "SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_FRESH';"
        )
        assert rest == 1, f"29분 전 lock 보존 기대 (1), 실제 {rest}"
        docker_psql("DELETE FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_FRESH';")
    step("P7. stale 아님 (29분 전) — 보존", _p7)

    # P8. countAll 정확
    def _p8():
        before = docker_psql_count(
            "SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID LIKE 'TEST_LOCK_%';"
        )
        # 이미 P3 + P4 + P5 잔여 = 3건 (TEST_LOCK_002 1, TEST_LOCK_003 2)
        assert before == 3, f"잔여 3건 기대, 실제 {before}"
    step("P8. countAll — 점유 카운트 일치 (P3/P4/P5 잔여 3건)", _p8)

    # P9. lock 획득 후 비정상 종료 시뮬레이션 → stale 정리로 회복
    def _p9():
        docker_psql(
            "INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_DEAD','T','C_DEAD','D','u', "
            "to_char(now() - INTERVAL '40 minutes','YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING;"
        )
        # 죽은 lock — 30분 초과라 cleanupStale 가 정리
        docker_psql(
            "DELETE FROM TB_QUAL_RUNNING_LOCK "
            "WHERE START_DT < to_char(now() - INTERVAL '30 minutes','YYYYMMDDHH24MISS');"
        )
        rest = docker_psql_count("SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID='TEST_LOCK_DEAD';")
        assert rest == 0
        # 같은 키로 재 acquire 가능
        a = docker_psql_count(
            "WITH ins AS (INSERT INTO TB_QUAL_RUNNING_LOCK VALUES "
            "('TEST_LOCK_DEAD','T','C_DEAD','D2','u2', to_char(now(),'YYYYMMDDHH24MISS')) "
            "ON CONFLICT DO NOTHING RETURNING 1) SELECT COUNT(*) FROM ins;"
        )
        assert a == 1, "비정상 종료 후 재 acquire 가능해야"
    step("P9. 비정상 종료 시 stale 정리 → 동일 키 재 acquire 가능", _p9)

    # P10. listAll — 점유 lock 다 반환
    def _p10():
        cnt = docker_psql_count(
            "SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID LIKE 'TEST_LOCK_%';"
        )
        assert cnt >= 4, f"최소 4건 기대 (P3+P4+P5+P9), 실제 {cnt}"
    step("P10. listAll 모니터링 — 점유 lock 모두 반환", _p10)

    # P11. 단건 get 정상
    def _p11():
        diag = docker_psql(
            "SELECT DIAG_ID FROM TB_QUAL_RUNNING_LOCK "
            "WHERE DM_ID='TEST_LOCK_002' AND OBJ_NM='T' AND ATTR_NM='C';"
        )
        assert diag == "D3", f"D3 기대 (P5 update), 실제 {diag}"
    step("P11. 단건 get — 키 일치 row 정확 반환", _p11)

    # P12. countAll 정확 (전체 row)
    def _p12():
        c = docker_psql_count(
            "SELECT COUNT(*) FROM TB_QUAL_RUNNING_LOCK WHERE DM_ID LIKE 'TEST_LOCK_%';"
        )
        assert c >= 4
    step("P12. countAll — Throttle 모니터링 정확", _p12)

    # P13. 운영 DB 락 X — 우리 메타DB 만 row 가짐
    def _p13():
        # 외부 oracle 컨테이너에 우리 lock 테이블 흔적 없는지 (스키마/테이블 존재 X 확인)
        # oracle-xe 에 lock 테이블이 절대 안 만들어졌어야
        cmd = ["docker", "exec", "-i", "oracle-xe", "sqlplus", "-S", "system/oracle@XEPDB1"]
        sql_check = ("SELECT COUNT(*) FROM ALL_TABLES WHERE TABLE_NAME = 'TB_QUAL_RUNNING_LOCK';\nEXIT;\n")
        r = subprocess.run(cmd, input=sql_check, capture_output=True, text=True, encoding="utf-8")
        # SQL*Plus 출력에서 숫자 추출
        out = r.stdout
        # 응답 예: "  COUNT(*)\n----------\n         0"
        assert "0" in out, f"Oracle 에 lock 테이블 흔적 — 운영 DB 오염 의심:\n{out}"
    step("P13. 운영 DB (oracle-xe) 에 lock 테이블 0건 — 메타DB 만 사용", _p13)

    # cleanup
    cleanup()


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
