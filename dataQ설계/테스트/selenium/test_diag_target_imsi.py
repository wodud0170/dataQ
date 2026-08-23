"""
79번 진단 대상 제외 관리 — IMSI 시나리오 12 Phase

검증 범위:
  P1.  Oracle 에 IMSI_TEST_001/002/003 생성
  P2.  dataq-db 에 메타 INSERT (수집 흉내 — CAMS 모델 OBJ/ATTR 에 IMSI_* 행 추가)
  P3.  단건 OFF — TEST_001 표준 + 사유
  P4.  일괄 OFF — TEST_002/003 구조 + 사유
  P5.  컬럼 단건 OFF — TEST_001.NAME 구조 + 사유 빈칸
  P6.  컬럼 일괄 OFF — TEST_002.CODE/VALUE 표준 + 사유
  P7.  표준화 진단 매퍼 검증 — TARGET=N row 들 모수 제외
  P8.  구조 변경 진단 매퍼 검증 — baseline cascade 효과
  P9.  ALTER TABLE — TEST_001 의 XYZ_DATA 변경 + 메타 갱신
  P10. 구조 매퍼 재검증 — TEST_002/003 변경은 미등장 시뮬레이션
  P11. ON 복귀 + 매퍼 재검증 — 모수 복귀
  P12. Cleanup — Oracle DROP + dataq-db row 정리

매퍼 검증 = SQL 직접 실행. 실제 DiagService 호출 X (시간 + 환경 영향 최소화).
"""
import base64, json, sys, time, traceback, subprocess
import requests

BASE = "http://localhost:28091"
DM_ID = "c2IN5c_Z4u*9kK3MkmJKq3"  # CAMS 모델
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


def login():
    s = requests.Session()
    enc = base64.b64encode("123".encode()).decode()
    r = s.post(BASE + "/login", data={"id": "space", "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    return s


def docker_psql(sql, db="dataq-db"):
    """dataq-db 직접 SQL 실행"""
    cmd = ["docker", "exec", "-i", db, "psql", "-U", "admin", "-d", "postgres", "-t", "-A", "-c", "SET search_path TO quality;" + sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def docker_oracle(sql):
    """oracle-xe sqlplus 실행"""
    cmd = ["docker", "exec", "-i", "oracle-xe", "sqlplus", "-S", "system/oracle@XEPDB1"]
    r = subprocess.run(cmd, input=sql + "\nEXIT;\n", capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def main():
    s = login()

    # ============================================================
    # P1. Oracle DDL
    # ============================================================
    def _p1():
        # 잔여 cleanup
        for tbl in ("IMSI_TEST_001","IMSI_TEST_002","IMSI_TEST_003"):
            docker_oracle(f"DROP TABLE {tbl};")
        oracle_ddl = """
CREATE TABLE IMSI_TEST_001 (
  ID VARCHAR2(20) NOT NULL,
  NAME VARCHAR2(50),
  CREATE_DT DATE,
  XYZ_DATA VARCHAR2(100),
  TST_FLAG CHAR(1),
  CONSTRAINT PK_IMSI_TEST_001 PRIMARY KEY (ID)
);
CREATE TABLE IMSI_TEST_002 (
  CODE VARCHAR2(10) NOT NULL,
  VALUE VARCHAR2(200),
  REG_DT DATE,
  CONSTRAINT PK_IMSI_TEST_002 PRIMARY KEY (CODE)
);
CREATE TABLE IMSI_TEST_003 (
  SEQ_NO NUMBER(10) NOT NULL,
  TITLE VARCHAR2(100),
  CONTENT CLOB,
  ETC VARCHAR2(50),
  CONSTRAINT PK_IMSI_TEST_003 PRIMARY KEY (SEQ_NO)
);
"""
        out = docker_oracle(oracle_ddl)
        check = docker_oracle("SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME LIKE 'IMSI_%';")
        assert "3" in check, f"3 테이블 기대, 실제 출력:\n{check}"
    step("P1. Oracle IMSI_TEST_001/002/003 생성", _p1)

    # ============================================================
    # P2. dataq-db 메타 INSERT (수집 흉내)
    # ============================================================
    def _p2():
        # 잔여 정리
        docker_psql(f"DELETE FROM TB_DATA_MODEL_ATTR WHERE DM_ID = '{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        docker_psql(f"DELETE FROM TB_DATA_MODEL_OBJ  WHERE DM_ID = '{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        # OBJ
        docker_psql(f"""
INSERT INTO TB_DATA_MODEL_OBJ (DM_ID, OBJ_NM, OBJ_NM_KR, OBJ_OWNER, OBJ_ATTR_CNT, USE_YN)
VALUES
  ('{DM_ID}', 'IMSI_TEST_001', '임시테스트1', 'SYSTEM', 5, 'Y'),
  ('{DM_ID}', 'IMSI_TEST_002', '임시테스트2', 'SYSTEM', 3, 'Y'),
  ('{DM_ID}', 'IMSI_TEST_003', '임시테스트3', 'SYSTEM', 4, 'Y');""")
        # ATTR — TEST_001
        docker_psql(f"""
INSERT INTO TB_DATA_MODEL_ATTR (DM_ID, OBJ_NM, ATTR_NM, ATTR_NM_KR, DATA_TYPE, DATA_LEN, NULLABLE_YN, PK_YN, FK_YN, ATTR_ORD, OBJ_OWNER, USE_YN)
VALUES
  ('{DM_ID}','IMSI_TEST_001','ID',       '아이디',     'VARCHAR', 20, 'N','Y','N',1,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_001','NAME',     '이름',       'VARCHAR', 50, 'Y','N','N',2,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_001','CREATE_DT','생성일자',   'DATE',     0, 'Y','N','N',3,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_001','XYZ_DATA', 'XYZ데이터',  'VARCHAR',100, 'Y','N','N',4,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_001','TST_FLAG', '테스트플래그','CHAR',     1, 'Y','N','N',5,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_002','CODE',     '코드',       'VARCHAR', 10, 'N','Y','N',1,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_002','VALUE',    '값',         'VARCHAR',200, 'Y','N','N',2,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_002','REG_DT',   '등록일시',   'DATE',     0, 'Y','N','N',3,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_003','SEQ_NO',   '순번',       'NUMERIC', 10, 'N','Y','N',1,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_003','TITLE',    '제목',       'VARCHAR',100, 'Y','N','N',2,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_003','CONTENT',  '내용',       'CLOB',     0, 'Y','N','N',3,'SYSTEM','Y'),
  ('{DM_ID}','IMSI_TEST_003','ETC',      '기타',       'VARCHAR', 50, 'Y','N','N',4,'SYSTEM','Y');""")
        cnt = docker_psql(f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        assert cnt == "12", f"12 ATTR 기대, 실제 {cnt}"
    step("P2. dataq-db 메타 INSERT (12 ATTR)", _p2)

    # ============================================================
    # P3. 단건 OFF — TEST_001 표준 + 사유
    # ============================================================
    def _p3():
        r = s.post(BASE + "/api/dm/setObjDiagTarget", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNm": "IMSI_TEST_001",
            "diagType": "STND", "targetYn": "N", "reason": "단건 OFF 테스트"
        }, timeout=10)
        assert r.status_code == 200, f"{r.status_code}"
        check = docker_psql(f"""
SELECT STND_DIAG_TARGET_YN || '|' || COALESCE(STND_DIAG_TARGET_REASON,'NULL') || '|' ||
       COALESCE(STRUCT_DIAG_TARGET_YN,'Y') || '|' || COALESCE(QUAL_DIAG_TARGET_YN,'Y')
  FROM TB_DATA_MODEL_OBJ WHERE DM_ID='{DM_ID}' AND OBJ_NM='IMSI_TEST_001';""")
        print(f"  값: {check}")
        assert check == "N|단건 OFF 테스트|Y|Y", f"기대 N|단건 OFF 테스트|Y|Y, 실제 {check}"
    step("P3. 단건 OFF — TEST_001 표준 + 사유", _p3)

    # ============================================================
    # P4. 일괄 OFF — TEST_002/003 구조 + 사유
    # ============================================================
    def _p4():
        r = s.post(BASE + "/api/dm/setObjDiagTargetBatch", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNms": ["IMSI_TEST_002","IMSI_TEST_003"],
            "diagType": "STRUCT", "targetYn": "N", "reason": "일괄 OFF 사유 입력"
        }, timeout=10)
        assert r.status_code == 200
        check = docker_psql(f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_OBJ
 WHERE DM_ID='{DM_ID}' AND OBJ_NM IN ('IMSI_TEST_002','IMSI_TEST_003')
   AND STRUCT_DIAG_TARGET_YN='N' AND STRUCT_DIAG_TARGET_REASON='일괄 OFF 사유 입력';""")
        assert check == "2", f"2 기대, 실제 {check}"
    step("P4. 일괄 OFF — TEST_002/003 구조 + 사유", _p4)

    # ============================================================
    # P5. 컬럼 단건 OFF — TEST_001.NAME 구조 + 사유 빈칸 (null)
    # ============================================================
    def _p5():
        r = s.post(BASE + "/api/dm/setAttrDiagTarget", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNm": "IMSI_TEST_001", "attrNm": "NAME",
            "diagType": "STRUCT", "targetYn": "N", "reason": ""
        }, timeout=10)
        assert r.status_code == 200
        check = docker_psql(f"""
SELECT STRUCT_DIAG_TARGET_YN || '|' || COALESCE(STRUCT_DIAG_TARGET_REASON,'NULL')
  FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{DM_ID}' AND OBJ_NM='IMSI_TEST_001' AND ATTR_NM='NAME';""")
        assert check == "N|NULL", f"기대 N|NULL (사유 빈칸 NULL 저장), 실제 {check}"
    step("P5. 컬럼 단건 OFF — TEST_001.NAME 구조 + 사유 빈칸", _p5)

    # ============================================================
    # P6. 컬럼 일괄 OFF — TEST_002.CODE/VALUE 표준 + 사유
    # ============================================================
    def _p6():
        r = s.post(BASE + "/api/dm/setAttrDiagTargetBatch", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNm": "IMSI_TEST_002", "attrNms": ["CODE","VALUE"],
            "diagType": "STND", "targetYn": "N", "reason": "컬럼 일괄 OFF"
        }, timeout=10)
        assert r.status_code == 200
        check = docker_psql(f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR
 WHERE DM_ID='{DM_ID}' AND OBJ_NM='IMSI_TEST_002' AND ATTR_NM IN ('CODE','VALUE')
   AND STND_DIAG_TARGET_YN='N' AND STND_DIAG_TARGET_REASON='컬럼 일괄 OFF';""")
        assert check == "2", f"2 기대, 실제 {check}"
    step("P6. 컬럼 일괄 OFF — TEST_002.CODE/VALUE 표준", _p6)

    # ============================================================
    # P7. 표준화 진단 매퍼 검증 — selectAttrListForStndDiag 의 모수에서 OFF 행이 빠지는지
    # ============================================================
    def _p7():
        # selectAttrListForStndDiag 와 동일한 SQL 실행
        sql = f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR A
INNER JOIN TB_DATA_MODEL_OBJ O ON A.DM_ID=O.DM_ID AND A.OBJ_NM=O.OBJ_NM
WHERE A.DM_ID='{DM_ID}' AND A.OBJ_NM LIKE 'IMSI_%'
  AND A.USE_YN='Y' AND O.USE_YN='Y'
  AND COALESCE(O.STND_DIAG_TARGET_YN,'Y')='Y'
  AND COALESCE(A.STND_DIAG_TARGET_YN,'Y')='Y';"""
        cnt = docker_psql(sql)
        # IMSI_TEST_001: OBJ STND OFF cascade 전체 5 컬럼 제외
        # IMSI_TEST_002: ATTR CODE/VALUE STND OFF — REG_DT 만 포함 (1)
        # IMSI_TEST_003: 모두 ON (4)
        # 기대 모수 = 1 + 4 = 5
        print(f"  표준화 진단 모수 (IMSI_*): {cnt} (기대 5)")
        assert cnt == "5", f"5 기대, 실제 {cnt}"

        # 제외 카운트
        sql_excl = f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR A
INNER JOIN TB_DATA_MODEL_OBJ O ON A.DM_ID=O.DM_ID AND A.OBJ_NM=O.OBJ_NM
WHERE A.DM_ID='{DM_ID}' AND A.OBJ_NM LIKE 'IMSI_%'
  AND A.USE_YN='Y' AND O.USE_YN='Y'
  AND (COALESCE(O.STND_DIAG_TARGET_YN,'Y')='N' OR COALESCE(A.STND_DIAG_TARGET_YN,'Y')='N');"""
        excl = docker_psql(sql_excl)
        # 5 (TEST_001 cascade) + 2 (TEST_002 CODE/VALUE) = 7
        print(f"  표준화 진단 제외 (IMSI_*): {excl} (기대 7)")
        assert excl == "7", f"7 기대, 실제 {excl}"
    step("P7. 표준화 진단 매퍼 — IMSI_* 모수 5 / 제외 7", _p7)

    # ============================================================
    # P8. 구조 변경 진단 매퍼 검증 — baseline cascade
    # ============================================================
    def _p8():
        sql = f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR A
INNER JOIN TB_DATA_MODEL_OBJ O ON A.DM_ID=O.DM_ID AND A.OBJ_NM=O.OBJ_NM
WHERE A.DM_ID='{DM_ID}' AND A.OBJ_NM LIKE 'IMSI_%'
  AND A.USE_YN='Y' AND O.USE_YN='Y'
  AND COALESCE(O.STRUCT_DIAG_TARGET_YN,'Y')='Y'
  AND COALESCE(A.STRUCT_DIAG_TARGET_YN,'Y')='Y';"""
        cnt = docker_psql(sql)
        # IMSI_TEST_001: OBJ STRUCT ON / 컬럼 NAME STRUCT OFF — 4 컬럼 (ID/CREATE_DT/XYZ_DATA/TST_FLAG)
        # IMSI_TEST_002: OBJ STRUCT OFF cascade — 0
        # IMSI_TEST_003: OBJ STRUCT OFF cascade — 0
        # 기대 모수 = 4
        print(f"  구조 변경 진단 모수 (IMSI_*): {cnt} (기대 4)")
        assert cnt == "4", f"4 기대, 실제 {cnt}"
    step("P8. 구조 변경 진단 매퍼 — IMSI_* 모수 4 (cascade 적용)", _p8)

    # ============================================================
    # P9. ALTER + 메타 갱신 (수집 흉내)
    # ============================================================
    def _p9():
        # Oracle ALTER
        docker_oracle("""
ALTER TABLE IMSI_TEST_001 MODIFY (XYZ_DATA VARCHAR2(200));
ALTER TABLE IMSI_TEST_002 ADD NEW_COL VARCHAR2(50);
ALTER TABLE IMSI_TEST_003 MODIFY (ETC VARCHAR2(100));""")
        # 메타 갱신 (수집 흉내) — TEST_001.XYZ_DATA len 100→200
        docker_psql(f"""
UPDATE TB_DATA_MODEL_ATTR SET DATA_LEN=200
 WHERE DM_ID='{DM_ID}' AND OBJ_NM='IMSI_TEST_001' AND ATTR_NM='XYZ_DATA';""")
        # TEST_002 에 NEW_COL row 추가
        docker_psql(f"""
INSERT INTO TB_DATA_MODEL_ATTR (DM_ID, OBJ_NM, ATTR_NM, ATTR_NM_KR, DATA_TYPE, DATA_LEN, NULLABLE_YN, PK_YN, FK_YN, ATTR_ORD, OBJ_OWNER, USE_YN)
VALUES ('{DM_ID}','IMSI_TEST_002','NEW_COL','신규컬럼','VARCHAR',50,'Y','N','N',4,'SYSTEM','Y');""")
        # TEST_003.ETC len 변경
        docker_psql(f"""
UPDATE TB_DATA_MODEL_ATTR SET DATA_LEN=100
 WHERE DM_ID='{DM_ID}' AND OBJ_NM='IMSI_TEST_003' AND ATTR_NM='ETC';""")
    step("P9. ALTER + 메타 갱신", _p9)

    # ============================================================
    # P10. 구조 매퍼 재검증 — TEST_002.NEW_COL / TEST_003.ETC 변경 미반영
    # ============================================================
    def _p10():
        # TEST_002.NEW_COL 은 STRUCT OFF cascade 라 모수에 안 들어감
        sql = f"""
SELECT A.OBJ_NM || '.' || A.ATTR_NM
  FROM TB_DATA_MODEL_ATTR A
  INNER JOIN TB_DATA_MODEL_OBJ O ON A.DM_ID=O.DM_ID AND A.OBJ_NM=O.OBJ_NM
 WHERE A.DM_ID='{DM_ID}' AND A.OBJ_NM LIKE 'IMSI_%'
   AND A.USE_YN='Y' AND O.USE_YN='Y'
   AND COALESCE(O.STRUCT_DIAG_TARGET_YN,'Y')='Y'
   AND COALESCE(A.STRUCT_DIAG_TARGET_YN,'Y')='Y'
 ORDER BY A.OBJ_NM, A.ATTR_NM;"""
        result = docker_psql(sql)
        print(f"  구조 진단 대상 IMSI_*:\n{result}")
        # IMSI_TEST_001 의 XYZ_DATA 만 진단 대상 (변경됨)
        # NAME (컬럼 OFF), TEST_002/003 (테이블 OFF) — 모두 미포함
        assert "IMSI_TEST_001.XYZ_DATA" in result, "TEST_001.XYZ_DATA 진단 대상 기대"
        assert "IMSI_TEST_001.NAME" not in result, "TEST_001.NAME 컬럼 OFF — 제외 기대"
        assert "IMSI_TEST_002.NEW_COL" not in result, "TEST_002.NEW_COL 테이블 OFF — 제외 기대"
        assert "IMSI_TEST_003.ETC" not in result, "TEST_003.ETC 테이블 OFF — 제외 기대"
    step("P10. 구조 매퍼 재검증 — OFF 변경은 모수 미포함", _p10)

    # ============================================================
    # P11. ON 복귀 + 매퍼 재검증
    # ============================================================
    def _p11():
        # 모든 OBJ ON
        s.post(BASE + "/api/dm/setObjDiagTargetBatch", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNms": ["IMSI_TEST_001","IMSI_TEST_002","IMSI_TEST_003"],
            "diagType": "STND", "targetYn": "Y", "reason": ""
        }, timeout=10)
        s.post(BASE + "/api/dm/setObjDiagTargetBatch", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNms": ["IMSI_TEST_001","IMSI_TEST_002","IMSI_TEST_003"],
            "diagType": "STRUCT", "targetYn": "Y", "reason": ""
        }, timeout=10)
        # ATTR ON 복귀
        s.post(BASE + "/api/dm/setAttrDiagTarget", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNm":"IMSI_TEST_001","attrNm":"NAME",
            "diagType":"STRUCT","targetYn":"Y","reason":""
        }, timeout=10)
        s.post(BASE + "/api/dm/setAttrDiagTargetBatch", json={
            "dmId": DM_ID, "objOwner": "SYSTEM","objNm":"IMSI_TEST_002","attrNms":["CODE","VALUE"],
            "diagType":"STND","targetYn":"Y","reason":""
        }, timeout=10)
        # 검증 — 사유 NULL 클리어
        check = docker_psql(f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_OBJ
 WHERE DM_ID='{DM_ID}' AND OBJ_NM LIKE 'IMSI_%'
   AND (STND_DIAG_TARGET_YN='N' OR STRUCT_DIAG_TARGET_YN='N'
        OR STND_DIAG_TARGET_REASON IS NOT NULL OR STRUCT_DIAG_TARGET_REASON IS NOT NULL);""")
        assert check == "0", f"0 기대 (모두 ON, 사유 NULL), 실제 {check}"

        # 표준화 매퍼 모수 = 13 (12 + NEW_COL)
        sql = f"""
SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR A
INNER JOIN TB_DATA_MODEL_OBJ O ON A.DM_ID=O.DM_ID AND A.OBJ_NM=O.OBJ_NM
WHERE A.DM_ID='{DM_ID}' AND A.OBJ_NM LIKE 'IMSI_%'
  AND A.USE_YN='Y' AND O.USE_YN='Y'
  AND COALESCE(O.STND_DIAG_TARGET_YN,'Y')='Y'
  AND COALESCE(A.STND_DIAG_TARGET_YN,'Y')='Y';"""
        cnt = docker_psql(sql)
        print(f"  ON 복귀 후 표준화 진단 모수: {cnt} (기대 13)")
        assert cnt == "13", f"13 기대, 실제 {cnt}"
    step("P11. ON 복귀 + 모수 13 / 사유 NULL", _p11)

    # ============================================================
    # P12. Cleanup
    # ============================================================
    def _p12():
        for tbl in ("IMSI_TEST_001","IMSI_TEST_002","IMSI_TEST_003"):
            docker_oracle(f"DROP TABLE {tbl};")
        docker_psql(f"DELETE FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        docker_psql(f"DELETE FROM TB_DATA_MODEL_OBJ  WHERE DM_ID='{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        chk = docker_psql(f"SELECT COUNT(*) FROM TB_DATA_MODEL_ATTR WHERE DM_ID='{DM_ID}' AND OBJ_NM LIKE 'IMSI_%';")
        assert chk == "0", "메타 정리 실패"
    step("P12. Cleanup — Oracle DROP + 메타 DELETE", _p12)


if __name__ == "__main__":
    t0 = time.time()
    main()
    elapsed = time.time() - t0
    p = sum(1 for _, st in results if st == "PASS")
    f = sum(1 for _, st in results if st == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL  ({elapsed:.0f}초)\n{'='*60}")
    for n, st in results:
        print(f"  [{st}] {n}")
    sys.exit(0 if f == 0 else 1)
