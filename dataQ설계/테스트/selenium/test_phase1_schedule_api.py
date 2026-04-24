"""
Phase 1 — 진단 스케줄러 API 스모크 테스트 (65번 문서 §10 작업 1~2, 4)

검증 범위 (Phase 1):
  - POST /api/diag/schedule/create   (관리자)
  - GET  /api/diag/schedule/list
  - GET  /api/diag/schedule/{id}    (경로는 ?scheduleId=로 호출)
  - POST /api/diag/schedule/update
  - POST /api/diag/schedule/toggle
  - POST /api/diag/schedule/cronPreview
  - POST /api/diag/schedule/runNow   (Phase 1: LOG RUNNING 만 기록, 실행은 Phase 2)
  - GET  /api/diag/schedule/logs
  - POST /api/diag/schedule/delete
  - 비관리자 거부 (403)

의존:
  - 서버 28091 기동
  - 관리자 계정 space/123, 일반 jyjang/123
  - TB_DATA_MODEL 에 최소 1건 있어야 함 (없으면 테스트 내부에서 skip)
"""
import base64
import os
import sys
import traceback
from datetime import datetime

import requests

BASE = "http://localhost:28091"

results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except AssertionError as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", str(e)))
        print(f"  >> FAIL: {e}")
        return False
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def login(session, user, pw):
    # Spring Security formLogin: POST /login with id/password(base64)
    # NdLogin.vue 와 동일: loginData.append("password", btoa(data.password))
    enc = base64.b64encode(pw.encode("utf-8")).decode("ascii")
    r = session.post(BASE + "/login",
                     data={"id": user, "password": enc},
                     allow_redirects=False, timeout=10)
    # 성공 응답: customAuthSuccessHandler 가 200 + success JSON
    assert r.status_code == 200, f"login {user} status={r.status_code} body={r.text[:200]}"
    try:
        b = r.json()
        assert b.get("success") is True, f"login {user} failed: {b}"
    except ValueError:
        pass  # JSON 아닐 수도 있음
    assert any("SESSION" in c.upper() or "JSESSIONID" in c.upper() for c in session.cookies.keys()), \
        f"session cookie missing after login: {list(session.cookies.keys())}"


def get_any_data_model(session):
    r = session.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
    r.raise_for_status()
    arr = r.json() or []
    if not arr:
        return None
    return arr[0]


state = {}


def main():
    admin = requests.Session()
    normal = requests.Session()

    def _login_admin(): login(admin, "space", "123")
    def _login_normal(): login(normal, "jyjang", "123")

    if not step("1. 관리자 로그인 (space)", _login_admin): return
    if not step("2. 일반 사용자 로그인 (jyjang)", _login_normal): return

    def _pick_model():
        dm = get_any_data_model(admin)
        if not dm:
            raise RuntimeError("TB_DATA_MODEL 에 데이터모델 없음 — 테스트 전 1건 이상 등록 필요")
        state["dmId"] = dm.get("dataModelId")
        state["dmNm"] = dm.get("dataModelNm")
        print(f"  picked dataModelId={state['dmId']} nm={state['dmNm']}")

    if not step("3. 데이터모델 1건 확보", _pick_model): return

    # 4. 비관리자 create 거부 (403)
    def _forbid_create_non_admin():
        r = normal.post(BASE + "/api/diag/schedule/create",
                        json={"scheduleNm": "denyme", "diagType": "STANDARD",
                              "dataModelId": state["dmId"], "scheduleType": "SIMPLE",
                              "repeatCycle": "DAILY", "repeatTime": "02:00"},
                        timeout=10)
        assert r.status_code == 200, f"status={r.status_code}"
        body = r.json()
        assert body.get("resultCode") == 403, f"expected 403 resultCode, got {body}"
    if not step("4. 비관리자 create 403 거부", _forbid_create_non_admin): return

    # 5. create (관리자)
    def _create():
        body = {"scheduleNm": "P1_TEST_" + datetime.now().strftime("%H%M%S"),
                "diagType": "STANDARD", "dataModelId": state["dmId"],
                "scheduleType": "SIMPLE", "repeatCycle": "DAILY",
                "repeatTime": "02:30"}
        r = admin.post(BASE + "/api/diag/schedule/create", json=body, timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200, f"resultCode={b.get('resultCode')} msg={b.get('resultMessage')}"
        assert b.get("contents"), "scheduleId contents missing"
        state["scheduleId"] = b["contents"]
        state["scheduleNm"] = body["scheduleNm"]
        print(f"  created scheduleId={state['scheduleId']}")
    if not step("5. create 스케줄", _create): return

    # 6. list 에 포함
    def _list_contains():
        r = admin.get(BASE + "/api/diag/schedule/list", timeout=10)
        r.raise_for_status()
        arr = r.json() or []
        ids = [s.get("scheduleId") for s in arr]
        assert state["scheduleId"] in ids, f"created schedule not in list ({len(arr)}개)"
        mine = next(s for s in arr if s.get("scheduleId") == state["scheduleId"])
        assert mine.get("useYn") == "Y", f"default useYn expected Y, got {mine.get('useYn')}"
        assert mine.get("scheduleNm") == state["scheduleNm"]
        assert mine.get("dataModelNm") == state["dmNm"], "joined dataModelNm 누락"
    if not step("6. list 에 신규 스케줄 포함 + join 필드", _list_contains): return

    # 7. detail
    def _detail():
        r = admin.get(BASE + f"/api/diag/schedule/{state['scheduleId']}",
                      params={"scheduleId": state["scheduleId"]}, timeout=10)
        r.raise_for_status()
        s = r.json()
        assert s.get("scheduleId") == state["scheduleId"]
    if not step("7. detail 조회", _detail): return

    # 8. toggle OFF
    def _toggle_off():
        r = admin.post(BASE + "/api/diag/schedule/toggle",
                       json={"scheduleId": state["scheduleId"], "useYn": "N"}, timeout=10)
        r.raise_for_status()
        assert r.json().get("resultCode") == 200
        # 확인
        r2 = admin.get(BASE + f"/api/diag/schedule/{state['scheduleId']}",
                       params={"scheduleId": state["scheduleId"]}, timeout=10)
        assert r2.json().get("useYn") == "N"
    if not step("8. toggle useYn=N", _toggle_off): return

    # 9. update scheduleNm
    def _update():
        new_nm = state["scheduleNm"] + "_UPD"
        r = admin.post(BASE + "/api/diag/schedule/update",
                       json={"scheduleId": state["scheduleId"], "scheduleNm": new_nm,
                             "diagType": "STANDARD", "dataModelId": state["dmId"],
                             "scheduleType": "SIMPLE", "repeatCycle": "DAILY",
                             "repeatTime": "03:00", "useYn": "Y"},
                       timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200, f"update failed: {b}"
        r2 = admin.get(BASE + f"/api/diag/schedule/{state['scheduleId']}",
                       params={"scheduleId": state["scheduleId"]}, timeout=10)
        assert r2.json().get("scheduleNm") == new_nm
        assert r2.json().get("repeatTime") == "03:00"
        state["scheduleNm"] = new_nm
    if not step("9. update scheduleNm + repeatTime", _update): return

    # 10. cronPreview
    def _cron_preview():
        r = admin.post(BASE + "/api/diag/schedule/cronPreview",
                       json={"cronExpr": "0 0 3 * * MON"}, timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200, f"cron preview failed: {b}"
        contents = b.get("contents")
        assert contents and '"next"' in contents, f"contents missing 'next': {contents}"
    if not step("10. cronPreview 유효 표현식", _cron_preview): return

    # 11. cronPreview 오류
    def _cron_preview_bad():
        r = admin.post(BASE + "/api/diag/schedule/cronPreview",
                       json={"cronExpr": "invalid"}, timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 400, f"expected 400, got {b}"
    if not step("11. cronPreview 잘못된 표현식 400", _cron_preview_bad): return

    # 12. runNow (Phase 1: LOG 만 기록)
    def _run_now():
        r = admin.post(BASE + "/api/diag/schedule/runNow",
                       json={"scheduleId": state["scheduleId"]}, timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200, f"runNow failed: {b}"
        state["logId"] = b.get("contents")
        assert state["logId"], "logId 미리턴"
        print(f"  logId={state['logId']}")
    if not step("12. runNow (LOG RUNNING 기록)", _run_now): return

    # 13. logs 에 포함
    def _logs_contains():
        r = admin.get(BASE + "/api/diag/schedule/logs",
                      params={"scheduleId": state["scheduleId"]}, timeout=10)
        r.raise_for_status()
        arr = r.json() or []
        ids = [l.get("logId") for l in arr]
        assert state["logId"] in ids, f"log not in list: {ids}"
        mine = next(l for l in arr if l.get("logId") == state["logId"])
        assert mine.get("triggerType") == "MANUAL"
        assert mine.get("execStatus") == "RUNNING"
        assert mine.get("scheduleNmSnapshot") == state["scheduleNm"], "snapshot 누락"
    if not step("13. logs 목록에 포함 + snapshot 기록", _logs_contains): return

    # 14. delete
    def _delete():
        r = admin.post(BASE + "/api/diag/schedule/delete",
                       json={"scheduleId": state["scheduleId"]}, timeout=10)
        r.raise_for_status()
        assert r.json().get("resultCode") == 200
        r2 = admin.get(BASE + "/api/diag/schedule/list", timeout=10)
        ids = [s.get("scheduleId") for s in (r2.json() or [])]
        assert state["scheduleId"] not in ids, "삭제 후에도 목록에 있음"
    if not step("14. 물리 삭제", _delete): return

    # 15. 삭제 후에도 LOG 는 SCHEDULE_NM_SNAPSHOT 으로 이름 유지
    def _log_survives():
        r = admin.get(BASE + "/api/diag/schedule/logs",
                      params={"scheduleId": state["scheduleId"]}, timeout=10)
        r.raise_for_status()
        arr = r.json() or []
        mine = next((l for l in arr if l.get("logId") == state["logId"]), None)
        assert mine is not None, "로그 사라짐 (물리 삭제 시 LOG 도 사라지면 안 됨)"
        assert mine.get("scheduleNmSnapshot") == state["scheduleNm"], \
            "삭제 후 snapshot 에서 스케줄명을 못 읽음"
    if not step("15. 삭제 후 LOG 에 snapshot 으로 이름 보존", _log_survives): return


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL (총 {len(results)}건)\n{'='*60}")
    for name, status, err in results:
        print(f"  [{status}] {name}")
    sys.exit(0 if f == 0 else 1)
