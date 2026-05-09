"""
T08 — 테이블 추가 시 영문명 정규식 검증

검증 (프론트 + 백엔드 양쪽):
  · 정상 케이스: TB_VALID — 통과
  · 백틱: `EVIL_TABLE` — 거부
  · 공백: TB EVIL — 거부
  · 한글: 한글테이블 — 거부
  · 특수문자: TB-EVIL, TB.EVIL, TB$EVIL — 거부
  · 숫자 시작: 1TB — 거부
  · 길이 초과 (>128자): 거부

검증 방식: 백엔드 API 직접 호출 (UI 우회 가능 → 백엔드 검증 누락 케이스 잡기)
"""
import sys, os, time
import requests
sys.path.insert(0, os.path.dirname(__file__))
from common import (db_query, BASE_URL, TestRun)


def call_addObj(session, dm_id, obj_nm, owner=""):
    return session.post(f"{BASE_URL}/api/dm/addObj",
        json={"dataModelId": dm_id, "objNm": obj_nm, "objNmKr": "테스트", "objOwner": owner, "objDesc": ""})


def login_session(user="space", pwd="123"):
    s = requests.Session()
    s.post(f"{BASE_URL}/api/login",
           json={"userId": user, "password": pwd})
    return s


def run():
    t = TestRun("T08 테이블 영문명 정규식 (프론트+백엔드)")
    try:
        # 임시 모델 ID — 실제 모델 1개 가져옴
        rows = db_query("SELECT DM_ID FROM TB_DATA_MODEL WHERE USE_YN='Y' LIMIT 1")
        if not rows:
            t.step("모델 존재", False, "모델 0건")
            return t
        dm_id = rows[0][0]

        s = login_session()
        # session 기반 인증 — login API 가 무엇인지 모르면 cookie 만으로는 안 될 수 있음
        # 일단 백엔드 reachable 확인
        r = s.get(f"{BASE_URL}/api/dm/getDataModelStatsList")
        t.step("API reachable", r.status_code in (200, 401, 403, 405),
               f"GET status={r.status_code}")

        invalid_cases = [
            ("`EVIL_BACKTICK`",   "백틱"),
            ("TB EVIL",           "공백"),
            ("한글테이블",         "한글"),
            ("TB-EVIL",           "하이픈"),
            ("TB.EVIL",           "도트"),
            ("TB$EVIL",           "달러"),
            ("1TB",               "숫자 시작"),
            ("A" * 200,           "200자 (너무 김)"),
        ]
        for nm, label in invalid_cases:
            r = call_addObj(s, dm_id, nm)
            # 정상이면 200, 검증 실패면 500 with message
            try:
                body = r.json()
            except Exception:
                body = {}
            rc = body.get("resultCode", r.status_code)
            rejected = rc != 200 or "허용" in (body.get("resultMessage", "") + "")
            t.step(f"거부됨: {label} ({nm[:30]})", rejected,
                   f"rc={rc} msg={body.get('resultMessage', '')[:60]}")
            if rc == 200:
                # 정리: 잘못 들어갔으면 삭제
                db_query(f"""
                    DELETE FROM TB_DATA_MODEL_OBJ
                    WHERE DM_ID='{dm_id}' AND OBJ_NM='{nm.replace("'","''")}'
                """)

        # 정상 케이스
        valid_nm = f"TB_VALID_{int(time.time())}"
        r = call_addObj(s, dm_id, valid_nm)
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        t.step(f"정상 영문명 통과: {valid_nm}", rc == 200,
               f"rc={rc}")
        # cleanup
        db_query(f"""
            DELETE FROM TB_DATA_MODEL_OBJ
            WHERE DM_ID='{dm_id}' AND OBJ_NM='{valid_nm}'
        """)

    except Exception as e:
        t.step("예외", False, str(e))
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
