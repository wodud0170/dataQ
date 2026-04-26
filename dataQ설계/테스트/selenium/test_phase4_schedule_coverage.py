"""
Phase 4 커버리지 보강 테스트 (API) — 65번 문서 § 10 작업 8 의 공백 채움

커버:
  A. SIMPLE 자동 트리거 — REPEAT_TIME 을 현재 +1분으로 설정 → 최대 90초 내 AUTO 이력 생성
  B. STRUCT 진단 경로 — runNow 시 structDiagId 가 LOG 에 세팅되는지
  C. STANDARD 실패 prefix — 수집 이력 없는 모델 → [DATA_NOT_FOUND] ERROR
  D. scheduleType 전환 — SIMPLE ↔ CRON update 왕복
  E. (선택) SKIP 정책 — runNow 로 RUNNING 만든 상태에서 자동 트리거 도래 시 SKIPPED 이력 확인

주의:
  - 서버는 이미 기동 중이어야 함 (28091 + 28098)
  - 테스트 소요 시간: ~3~4분 (A 가 60~90s 대기 필요)
"""
import base64
import sys
import time
import traceback
from datetime import datetime, timedelta

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
    except Exception as e:
        tb = traceback.format_exc()
        results.append((name, "FAIL", tb))
        print(f"  >> FAIL: {e}\n{tb}")
        return False


def login(s, user, pw):
    enc = base64.b64encode(pw.encode("utf-8")).decode("ascii")
    r = s.post(BASE + "/login", data={"id": user, "password": enc}, allow_redirects=False, timeout=10)
    assert r.status_code == 200
    try: assert r.json().get("success") is True
    except ValueError: pass


state = {}


def poll_log_until(admin, log_id, target_statuses, max_sec=90, interval=3):
    deadline = time.time() + max_sec
    last = None
    while time.time() < deadline:
        r = admin.get(BASE + f"/api/diag/schedule/logs/{log_id}",
                      params={"logId": log_id}, timeout=10)
        if r.ok:
            body = r.json() or {}
            last = body
            if body.get("execStatus") in target_statuses:
                return body
        time.sleep(interval)
    raise AssertionError(f"log {log_id} 상태 대기 타임아웃 (target={target_statuses}, last={last})")


def find_logs_by_schedule(admin, schedule_id):
    r = admin.get(BASE + "/api/diag/schedule/logs",
                  params={"scheduleId": schedule_id, "limit": 50}, timeout=10)
    r.raise_for_status()
    return r.json() or []


def create_schedule(admin, **overrides):
    body = {
        "scheduleNm": overrides.get("scheduleNm", "cov_" + datetime.now().strftime("%H%M%S%f")[:-3]),
        "diagType":    overrides.get("diagType", "STANDARD"),
        "dataModelId": overrides["dataModelId"],
        "scheduleType": overrides.get("scheduleType", "SIMPLE"),
        "repeatCycle": overrides.get("repeatCycle", "DAILY"),
        "repeatTime":  overrides.get("repeatTime", "23:59"),
        "useYn":       overrides.get("useYn", "Y"),
    }
    for k in ("repeatDayOfWeek", "repeatDayOfMonth", "cronExpr"):
        if k in overrides:
            body[k] = overrides[k]
    r = admin.post(BASE + "/api/diag/schedule/create", json=body, timeout=10)
    r.raise_for_status()
    b = r.json()
    assert b.get("resultCode") == 200, f"create failed: {b}"
    return b["contents"], body


def main():
    admin = requests.Session()
    if not step("0. 관리자 로그인", lambda: login(admin, "space", "123")): return

    # 모델 2개 준비: 하나는 수집 이력 있는 것(CAMS), 하나는 수집 이력 없는 신규 모델
    def _prep_models():
        r = admin.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        arr = r.json() or []
        assert arr
        # 기본 모델 — 첫 번째 (CAMS 로 가정, 수집 이력 보유)
        state["dmId"] = arr[0]["dataModelId"]
        print(f"  기본 모델: {arr[0].get('dataModelNm')} ({state['dmId']})")

        # 수집 이력 없는 모델 신규 생성 — C 테스트용
        nm = "PHASE4_NO_CLCT_" + datetime.now().strftime("%H%M%S")
        r2 = admin.post(BASE + "/api/dm/createDataModel",
                        json={"dataModelNm": nm, "modelType": "LOGICAL", "ver": "1.0"},
                        timeout=10)
        r2.raise_for_status()
        r3 = admin.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        target = next((m for m in r3.json() if m.get("dataModelNm") == nm), None)
        assert target, "수집 이력 없는 신규 모델 생성 실패"
        state["dmIdNoClct"] = target["dataModelId"]
        state["dmNmNoClct"] = nm
        print(f"  수집無 모델: {nm} ({state['dmIdNoClct']})")
    if not step("1. 데이터모델 2개 준비 (기본 + 수집無)", _prep_models): return

    # ======================= A. SIMPLE 자동 트리거 =======================
    def _a_auto_trigger():
        now = datetime.now()
        fire_at = now + timedelta(minutes=1)
        repeat_time = fire_at.strftime("%H:%M")
        print(f"  fire_at={fire_at.strftime('%H:%M:%S')} (REPEAT_TIME={repeat_time})")
        sid, _ = create_schedule(admin, dataModelId=state["dmId"],
                                 repeatTime=repeat_time, repeatCycle="DAILY")
        state["a_sid"] = sid

        # 자동 트리거 대기 — 최대 120초 (scheduler 평가 60s 주기)
        deadline = time.time() + 120
        found = None
        while time.time() < deadline:
            logs = find_logs_by_schedule(admin, sid)
            auto_logs = [l for l in logs if l.get("triggerType") == "AUTO"]
            if auto_logs:
                found = auto_logs[0]
                break
            time.sleep(5)
        assert found, f"자동 트리거 AUTO 이력 대기 타임아웃 (sid={sid})"
        print(f"  AUTO 이력 생성됨 logId={found.get('logId')} status={found.get('execStatus')}")
        state["a_auto_log_id"] = found.get("logId")
    if not step("A. SIMPLE 자동 트리거 (60~120초 대기)", _a_auto_trigger): pass

    # ======================= B. STRUCT 진단 경로 =======================
    def _b_struct_path():
        sid, _ = create_schedule(admin, dataModelId=state["dmId"],
                                 diagType="STRUCT", scheduleNm="cov_struct_" + datetime.now().strftime("%H%M%S"))
        state["b_sid"] = sid
        r = admin.post(BASE + "/api/diag/schedule/runNow",
                       json={"scheduleId": sid}, timeout=30)
        r.raise_for_status()
        lid = r.json().get("contents")
        assert lid, "logId 미리턴"
        # structDiagId 설정 대기 — launcher 가 곧바로 set
        deadline = time.time() + 30
        struct_id = None
        while time.time() < deadline:
            r2 = admin.get(BASE + f"/api/diag/schedule/logs/{lid}",
                           params={"logId": lid}, timeout=10)
            body = r2.json() or {}
            if body.get("structDiagId"):
                struct_id = body["structDiagId"]; break
            time.sleep(2)
        assert struct_id, "structDiagId 가 LOG 에 세팅되지 않음 — STRUCT 런처 경로 연결 미흡"
        print(f"  structDiagId={struct_id}")
        # 종료 대기 (최대 120s) — DONE or ERROR 둘 다 허용
        final = poll_log_until(admin, lid, ("DONE", "ERROR"), max_sec=120, interval=5)
        print(f"  최종 status={final.get('execStatus')} duration={final.get('execDurationSec')}s")
    if not step("B. STRUCT 진단 경로 (structDiagId 연결 + 마감)", _b_struct_path): pass

    # ======================= C. STANDARD 실패 prefix =======================
    def _c_data_not_found():
        sid, _ = create_schedule(admin, dataModelId=state["dmIdNoClct"],
                                 diagType="STANDARD",
                                 scheduleNm="cov_nofound_" + datetime.now().strftime("%H%M%S"))
        state["c_sid"] = sid
        r = admin.post(BASE + "/api/diag/schedule/runNow",
                       json={"scheduleId": sid}, timeout=30)
        r.raise_for_status()
        lid = r.json().get("contents")
        assert lid
        # ERROR 로 빠르게 마감 예상 (launcher 가 resolveLatestClctId null → throw)
        final = poll_log_until(admin, lid, ("ERROR",), max_sec=20, interval=2)
        msg = final.get("errorMsg") or ""
        assert msg.startswith("[DATA_NOT_FOUND]"), f"prefix 불일치: {msg[:80]}"
        print(f"  errorMsg prefix OK: {msg[:100]}")
    if not step("C. STANDARD 실패 — [DATA_NOT_FOUND] prefix 확인", _c_data_not_found): pass

    # ======================= D. scheduleType 전환 (SIMPLE ↔ CRON) =======================
    def _d_type_switch():
        sid, body = create_schedule(admin, dataModelId=state["dmId"],
                                    scheduleNm="cov_type_" + datetime.now().strftime("%H%M%S"),
                                    scheduleType="SIMPLE", repeatCycle="DAILY", repeatTime="04:00")
        state["d_sid"] = sid
        # SIMPLE → CRON 으로 변경
        upd = dict(body)
        upd["scheduleId"]   = sid
        upd["scheduleType"] = "CRON"
        upd["cronExpr"]     = "0 0 3 * * MON"
        upd["repeatCycle"]  = None
        upd["repeatTime"]   = None
        r = admin.post(BASE + "/api/diag/schedule/update", json=upd, timeout=10)
        r.raise_for_status()
        assert r.json().get("resultCode") == 200, r.json()
        r2 = admin.get(BASE + f"/api/diag/schedule/{sid}",
                       params={"scheduleId": sid}, timeout=10)
        det = r2.json() or {}
        assert det.get("scheduleType") == "CRON"
        assert det.get("cronExpr") == "0 0 3 * * MON"
        # CRON → SIMPLE(WEEKLY) 로 되돌리기
        back = {"scheduleId": sid, "scheduleNm": det["scheduleNm"], "diagType": "STANDARD",
                "dataModelId": state["dmId"], "scheduleType": "SIMPLE",
                "repeatCycle": "WEEKLY", "repeatTime": "05:30", "repeatDayOfWeek": 3,
                "useYn": "Y"}
        r3 = admin.post(BASE + "/api/diag/schedule/update", json=back, timeout=10)
        r3.raise_for_status()
        r4 = admin.get(BASE + f"/api/diag/schedule/{sid}",
                       params={"scheduleId": sid}, timeout=10)
        det2 = r4.json() or {}
        assert det2.get("scheduleType") == "SIMPLE"
        assert det2.get("repeatCycle") == "WEEKLY"
        assert det2.get("repeatDayOfWeek") == 3
        assert det2.get("repeatTime") == "05:30"
        print("  SIMPLE → CRON → SIMPLE(WEEKLY) 왕복 성공")
    if not step("D. scheduleType SIMPLE ↔ CRON 전환", _d_type_switch): pass

    # ======================= E. BOTH 진단 (STANDARD + STRUCT 병행) =======================
    def _e_both():
        sid, _ = create_schedule(admin, dataModelId=state["dmId"],
                                 diagType="BOTH",
                                 scheduleNm="cov_both_" + datetime.now().strftime("%H%M%S"))
        state["e_sid"] = sid
        r = admin.post(BASE + "/api/diag/schedule/runNow",
                       json={"scheduleId": sid}, timeout=30)
        r.raise_for_status()
        lid = r.json().get("contents")
        assert lid
        # diagJobId + structDiagId 둘 다 세팅되는지 대기
        deadline = time.time() + 30
        both_set = False
        while time.time() < deadline:
            r2 = admin.get(BASE + f"/api/diag/schedule/logs/{lid}",
                           params={"logId": lid}, timeout=10)
            body = r2.json() or {}
            if body.get("diagJobId") and body.get("structDiagId"):
                both_set = True
                break
            time.sleep(2)
        assert both_set, f"BOTH 에서 diagJobId/structDiagId 둘 다 세팅되어야 함. last={body}"
        print(f"  diagJobId={body.get('diagJobId')[:8]}... structDiagId={body.get('structDiagId')[:8]}...")
        # 둘 다 완료까지 대기 (DONE 이어야 함, 하나라도 ERROR 면 ERROR 로 마감)
        final = poll_log_until(admin, lid, ("DONE", "ERROR"), max_sec=180, interval=5)
        print(f"  최종 status={final.get('execStatus')} duration={final.get('execDurationSec')}s")
        # DONE 기대 (실패하지 않았다면)
        assert final.get("execStatus") == "DONE", \
            f"BOTH 결과 DONE 기대, got {final.get('execStatus')} msg={final.get('errorMsg')}"
    if not step("E. BOTH — STANDARD + STRUCT 병행 실행 + 둘 다 DONE 시 LOG DONE", _e_both): pass

    # ======================= 정리 =======================
    def _cleanup():
        for k in ("a_sid", "b_sid", "c_sid", "d_sid", "e_sid"):
            sid = state.get(k)
            if sid:
                try:
                    admin.post(BASE + "/api/diag/schedule/delete",
                               json={"scheduleId": sid}, timeout=10)
                except Exception: pass
        # 수집無 신규 모델 정리 시도 (실패해도 무시)
        try:
            if state.get("dmIdNoClct"):
                admin.post(BASE + "/api/dm/deleteDataModel",
                           json={"dataModelId": state["dmIdNoClct"]}, timeout=10)
        except Exception: pass
    step("Z. 정리", _cleanup)


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL (총 {len(results)})\n{'='*60}")
    for name, status, err in results:
        mark = "PASS" if status == "PASS" else "FAIL"
        print(f"  [{mark}] {name}")
    sys.exit(0 if f == 0 else 1)
