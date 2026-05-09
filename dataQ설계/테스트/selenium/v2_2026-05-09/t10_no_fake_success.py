"""
T10 — 가짜 success 패턴 차단 — 한글명 충돌 케이스 (★)

시나리오:
  · TB_WORD 에 '주소' = ADDR (이미 존재) 가정
  · 진단 결과에서 컬럼 'ADDRESS' / 한글 '주소' 의 [용어 등록] 시도
  · 백엔드 createWord: 한글명 '주소' 중복 → resultCode=500
  · 백엔드 createTerms: 한글명 '주소' 중복 → resultCode=500
  · 프론트가 'success' 가짜 표시 X — error swal 노출

검증:
  · API resultCode 정확히 검사
  · 5xx body.message 가 raw exception 이면 친화적 메세지로 치환됨
"""
import sys, os, time, requests
sys.path.insert(0, os.path.dirname(__file__))
from common import db_query, BASE_URL, TestRun


def run():
    t = TestRun("T10 가짜 success 차단 — 한글명 중복 케이스")
    try:
        # 사전: TB_WORD 에 '주소' (어떤 영문약어든 OK) 가 등록되어 있는지
        rows = db_query("""
            SELECT WORD_ID, WORD_NM, WORD_ENG_ABRV_NM, APRV_YN FROM TB_WORD
            WHERE WORD_NM='주소' LIMIT 1
        """)
        if not rows:
            t.step("사전조건 — 단어 '주소' 등록", False,
                   "TB_WORD 에 '주소' 단어 없음")
            return t
        existing = rows[0]
        t.step(f"사전조건 — '주소' 단어 존재 ({existing[2]})", True)

        # createWord — 한글명 '주소', 영문약어 'EVIL_DUP' 시도 → 중복으로 거부되어야 함
        r = requests.post(f"{BASE_URL}/api/std/createWord",
            json={"wordNm": "주소", "wordEngAbrvNm": "EVILDUP",
                  "wordEngNm": "Address", "wordDesc": "T10 자동테스트",
                  "wordClsfYn": "N", "domainClsfNm": ""})
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        msg = body.get("resultMessage", "")
        is_dup_msg = "중복" in msg or "이미" in msg or "duplicate" in msg.lower()
        t.step("createWord — 한글명 중복 시 resultCode != 200",
               rc != 200, f"rc={rc} msg={msg}")
        t.step("거부 메세지가 의미 있음 (중복/이미 등 키워드)",
               is_dup_msg, f"msg={msg}")
        # raw exception 누출 검증
        raw_keywords = ["NullPointer", "Exception at", "MismatchedInput",
                        "Cannot deserialize", "JSON parse"]
        is_raw = any(k in msg for k in raw_keywords)
        t.step("raw stack trace 노출 안 됨", not is_raw,
               f"raw_found={is_raw}")
        # 잘못 들어갔으면 cleanup
        if rc == 200:
            db_query("""DELETE FROM TB_WORD WHERE WORD_ENG_ABRV_NM='EVILDUP'""")

        # createTerms — 한글명 '주소' 중복 시도
        r = requests.post(f"{BASE_URL}/api/std/createTerms",
            json={"termsNm": "주소", "termsEngAbrvNm": "EVILDUPTERM",
                  "termsDesc": "T10", "domainNm": "",
                  "wordList": [], "allophSynmLst": []})
        try:
            body = r.json()
        except Exception:
            body = {}
        rc = body.get("resultCode", r.status_code)
        msg = body.get("resultMessage", "")
        # 단어 미존재이거나 한글명 중복 케이스
        t.step("createTerms — 비정상 입력 시 resultCode != 200",
               rc != 200, f"rc={rc} msg={msg}")
        if rc == 200:
            db_query("DELETE FROM TB_TERMS WHERE TERMS_ENG_ABRV_NM='EVILDUPTERM'")

    except Exception as e:
        t.step("예외", False, str(e))
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
