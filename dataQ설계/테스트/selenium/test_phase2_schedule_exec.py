"""
Phase 2 — 스케줄러 실제 실행 통합 테스트 (65번 문서 §10 작업 3, 4)

검증:
  A. runNow 가 q-executor 를 거쳐 실제 DiagService/StructDiagService 를 기동
  B. 완료 폴링이 LOG 를 DONE 또는 ERROR 로 마감
  C. SCHEDULE.LAST_EXEC_DT/LAST_EXEC_STATUS 가 갱신됨

전제:
  - q-center 28091 + q-executor 28098 기동
  - TB_DATA_MODEL 에 1건 이상
  - STANDARD 진단은 수집 이력(TB_DATA_MODEL_CLCT) 필요. 없으면 [DATA_NOT_FOUND] ERROR 로 마감되는 것을 검증
"""
import base64
import sys
import time
import traceback
from datetime import datetime

import requests

BASE = "http://localhost:28091"
POLL_MAX_SEC = 90     # 완료 대기 최대
POLL_INTERVAL = 5

results = []


def step(name, fn):
    print(f"\n{'=' * 60}\n[STEP] {name}\n{'=' * 60}")
    try:
        fn()
        results.append((name, "PASS", None))
        print("  >> PASS")
        return True
    except AssertionError as e:
        results.append((name, "FAIL", str(e)))
        print(f"  >> FAIL: {e}")
        return False
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def login(session, user, pw):
    enc = base64.b64encode(pw.encode("utf-8")).decode("ascii")
    r = session.post(BASE + "/login", data={"id": user, "password": enc},
                     allow_redirects=False, timeout=10)
    assert r.status_code == 200
    try:
        b = r.json()
        assert b.get("success") is True, f"login failed: {b}"
    except ValueError:
        pass


state = {}


def main():
    admin = requests.Session()

    if not step("1. 관리자 로그인", lambda: login(admin, "space", "123")): return

    def _pick_model():
        r = admin.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        r.raise_for_status()
        arr = r.json() or []
        assert arr, "데이터모델 없음"
        state["dmId"] = arr[0]["dataModelId"]
        state["dmNm"] = arr[0].get("dataModelNm")
        print(f"  모델: {state['dmNm']} ({state['dmId']})")
    if not step("2. 데이터모델 확보", _pick_model): return

    # STANDARD 스케줄 생성 + runNow
    def _create():
        r = admin.post(BASE + "/api/diag/schedule/create",
                       json={"scheduleNm": "P2_STD_" + datetime.now().strftime("%H%M%S"),
                             "diagType": "STANDARD", "dataModelId": state["dmId"],
                             "scheduleType": "SIMPLE", "repeatCycle": "DAILY",
                             "repeatTime": "23:59"},  # 먼 시각 — 자동 트리거 안 되게
                       timeout=10)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200
        state["scheduleId"] = b["contents"]
        print(f"  scheduleId={state['scheduleId']}")
    if not step("3. STANDARD 스케줄 생성", _create): return

    def _run_now():
        r = admin.post(BASE + "/api/diag/schedule/runNow",
                       json={"scheduleId": state["scheduleId"]}, timeout=30)
        r.raise_for_status()
        b = r.json()
        assert b.get("resultCode") == 200, f"runNow failed: {b}"
        state["logId"] = b.get("contents")
        assert state["logId"], "logId 미리턴"
        print(f"  logId={state['logId']}")
    if not step("4. runNow — q-executor 로 전달", _run_now): return

    def _poll_completion():
        deadline = time.time() + POLL_MAX_SEC
        last_status = None
        while time.time() < deadline:
            r = admin.get(BASE + f"/api/diag/schedule/logs/{state['logId']}",
                          params={"logId": state["logId"]}, timeout=10)
            r.raise_for_status()
            body = r.json() or {}
            last_status = body.get("execStatus")
            print(f"  poll: execStatus={last_status}  diagJobId={body.get('diagJobId')}  "
                  f"durSec={body.get('execDurationSec')}")
            state["logFinal"] = body
            if last_status in ("DONE", "ERROR", "SKIPPED"):
                return
            time.sleep(POLL_INTERVAL)
        raise AssertionError(f"완료 대기 타임아웃 (마지막 status={last_status})")
    if not step("5. 완료 폴링 (최대 90초)", _poll_completion): return

    def _verify_finish():
        final = state["logFinal"]
        assert final.get("execStatus") in ("DONE", "ERROR"), \
            f"expected DONE/ERROR, got {final.get('execStatus')}"
        # ERROR 면 메시지 prefix 검증 (예: [DATA_NOT_FOUND] / [DIAG])
        if final.get("execStatus") == "ERROR":
            msg = final.get("errorMsg") or ""
            assert msg.startswith("["), f"error msg prefix 누락: {msg}"
            print(f"  ERROR prefix OK: {msg[:80]}")
        else:
            print("  DONE 판정 — 기저 진단 완료까지 폴링이 성공적으로 이행됨")
        # 소요시간 기록 확인
        dur = final.get("execDurationSec")
        assert dur is not None and dur >= 0, f"execDurationSec 미기록: {dur}"
    if not step("6. LOG 가 DONE/ERROR 로 마감 + errorMsg prefix + duration", _verify_finish): return

    def _verify_schedule_last_exec():
        r = admin.get(BASE + f"/api/diag/schedule/{state['scheduleId']}",
                      params={"scheduleId": state["scheduleId"]}, timeout=10)
        sc = r.json() or {}
        assert sc.get("lastExecStatus") in ("DONE", "ERROR"), \
            f"schedule.lastExecStatus 갱신 안 됨: {sc.get('lastExecStatus')}"
        assert sc.get("lastExecLogId") == state["logId"]
        print(f"  schedule LAST_EXEC_STATUS={sc.get('lastExecStatus')} LAST_EXEC_LOG_ID ok")
    if not step("7. schedule.LAST_EXEC_* 갱신", _verify_schedule_last_exec): return

    def _cleanup():
        admin.post(BASE + "/api/diag/schedule/delete",
                   json={"scheduleId": state["scheduleId"]}, timeout=10)
    step("8. 정리 (물리 삭제)", _cleanup)


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for name, status, _ in results:
        print(f"  [{status}] {name}")
    sys.exit(0 if f == 0 else 1)
