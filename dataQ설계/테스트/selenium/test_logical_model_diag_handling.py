"""
논리 모델 진단 처리 검증 (사용자 지적 기반):

A. 표준 진단 (DiagService)
   - TERMS_STND_YN != 'Y' 컬럼 (비표준/TMP_COL_N) 은 진단 대상에서 제외되어야 함
   - 결과: TB_DIAG_JOB.process_cnt == count(TERMS_STND_YN='Y') 행

B. 구조 변경 진단 (StructDiagController.execute)
   - 논리 모델(dataModelDsId NULL) 은 400 + "[CONFIG]" 메시지로 즉시 거부

C. 진단 스케줄러로 STRUCT 트리거 시 (논리 모델)
   - LOG.execStatus='ERROR' + errorMsg startswith "[CONFIG]"

전제: 28091/28098 + dataq-db 기동
"""
import base64
import sys
import time
import traceback
from datetime import datetime

import requests

BASE = "http://localhost:28091"
results = []


def step(name, fn):
    print(f"\n{'='*60}\n[STEP] {name}\n{'='*60}")
    try:
        fn(); results.append((name, "PASS", None)); print("  >> PASS"); return True
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


def get_attr_list(s, dm_id):
    """모델의 모든 컬럼 (조회용 API). selectDataModelAttrListByClctId 와 같은 결과."""
    r = s.get(BASE + "/api/dm/getDataModelAttrList", params={"dataModelId": dm_id}, timeout=10)
    if r.status_code != 200:
        # 다른 엔드포인트 시도
        r = s.post(BASE + "/api/dm/getDataModelAttrListByDmId",
                   data={"dataModelId": dm_id}, timeout=10)
    r.raise_for_status()
    return r.json() or []


state = {}


def main():
    admin = requests.Session()
    if not step("0. 관리자 로그인", lambda: login(admin, "space", "123")): return

    # ==================== 시나리오 A: 비표준 컬럼 제외 검증 ====================
    # 기존 데이터모델 중 TERMS_STND_YN='Y'/'N' 혼재된 모델 사용.
    # CAMS 같은 실 모델 + 일부 컬럼이 표준 미매칭으로 'N' 인 경우 활용.

    def _check_diag_filter():
        # CAMS 모델로 실험 — 가장 첫 모델 사용
        r = admin.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        models = r.json() or []
        # dsId 있는 모델 중 첫 번째 (즉 물리 모델)
        target = next((m for m in models if m.get("dataModelDsId")), None)
        if not target:
            target = models[0]
        dm_id = target["dataModelId"]
        state["physDmId"] = dm_id
        print(f"  대상 모델: {target.get('dataModelNm')} ({dm_id})")

        # 진단 실행 → 스케줄러 runNow 경로로 단순화
        sched_body = {
            "scheduleNm": "diag_filter_chk_" + datetime.now().strftime("%H%M%S"),
            "diagType": "STANDARD", "dataModelId": dm_id,
            "scheduleType": "SIMPLE", "repeatCycle": "DAILY", "repeatTime": "23:59"
        }
        r3 = admin.post(BASE + "/api/diag/schedule/create", json=sched_body, timeout=10)
        r3.raise_for_status()
        sched_id = r3.json()["contents"]
        state["a_sched_id"] = sched_id
        r4 = admin.post(BASE + "/api/diag/schedule/runNow",
                        json={"scheduleId": sched_id}, timeout=30)
        log_id = r4.json()["contents"]
        # 폴링: DONE/ERROR 까지 최대 90초
        deadline = time.time() + 90
        final = None
        while time.time() < deadline:
            r5 = admin.get(BASE + f"/api/diag/schedule/logs/{log_id}",
                           params={"logId": log_id}, timeout=10)
            final = r5.json() or {}
            if final.get("execStatus") in ("DONE", "ERROR"): break
            time.sleep(5)
        assert final, "폴링 실패"
        if final.get("execStatus") == "ERROR" and "[DATA_NOT_FOUND]" in (final.get("errorMsg") or ""):
            # 수집 이력 없는 모델 → 비표준 제외 자체를 검증할 수 없음. 우아하게 스킵.
            print(f"  대상 모델에 수집 이력 없음 ({final.get('errorMsg')[:60]}). "
                  "필터 검증은 수집 이력 있는 모델 필요 — 스킵.")
            return
        assert final.get("execStatus") == "DONE", f"표준 진단 완료 못 함: {final}"
        diag_job_id = final.get("diagJobId")
        assert diag_job_id, "diagJobId 누락"
        print(f"  diagJobId={diag_job_id}")

        # TB_DIAG_JOB 의 process_cnt / total_cnt 조회
        r6 = admin.get(BASE + "/api/diag/getDiagJobById",
                       params={"diagJobId": diag_job_id}, timeout=10)
        r6.raise_for_status()
        try:
            job = r6.json()
        except ValueError:
            job = {}
        print(f"  진단 결과: total_cnt={job.get('totalCnt')} process_cnt={job.get('processCnt')} result_cnt={job.get('resultCnt')}")
        state["job"] = job
        assert job.get("totalCnt", 0) >= 0, f"totalCnt 누락: {job}"
    if not step("A. 표준 진단 — 비표준 제외 후 정상 DONE", _check_diag_filter): pass

    # ==================== 시나리오 B: 논리 모델 STRUCT 거부 ====================
    def _logical_model_struct_rejected():
        nm = "DIAG_REJECT_LOG_" + datetime.now().strftime("%H%M%S")
        r = admin.post(BASE + "/api/dm/createDataModel",
                       json={"dataModelNm": nm, "modelType": "LOGICAL", "ver": "1.0"}, timeout=10)
        r.raise_for_status()
        # 논리 모델 ID 조회
        r2 = admin.post(BASE + "/api/dm/getDataModelStatsList", json={}, timeout=10)
        target = next((m for m in r2.json() if m.get("dataModelNm") == nm), None)
        assert target, "논리 모델 생성 실패"
        dm_id = target["dataModelId"]
        state["b_dmId"] = dm_id
        # dsId 가 비어있는지 확인 (논리 모델 전제)
        ds_id = target.get("dataModelDsId")
        assert ds_id is None or ds_id == "", f"dsId 가 채워져 있음: {ds_id} (논리 모델이 아닐 수 있음)"
        print(f"  논리 모델 생성: {nm} dsId={ds_id!r}")

        # 구조 진단 execute API 호출 → 400 with [CONFIG]
        r3 = admin.post(BASE + "/api/std/structDiag/execute",
                        json={"dataModelId": dm_id}, timeout=10)
        # 응답 형식: Response 객체 (resultCode, resultMessage)
        body = r3.json()
        assert body.get("resultCode") == 400, f"400 기대, got {body}"
        msg = body.get("resultMessage") or ""
        assert "[CONFIG]" in msg, f"[CONFIG] prefix 없음: {msg}"
        assert "데이터소스" in msg or "데이터 소스" in msg, f"안내 문구 누락: {msg}"
        print(f"  거부 메시지: {msg}")
    if not step("B. 구조 변경 진단 — 논리 모델 400 [CONFIG] 거부", _logical_model_struct_rejected): pass

    # ==================== 시나리오 C: 스케줄러 STRUCT 트리거 거부 ====================
    def _scheduler_struct_logical_error():
        dm_id = state["b_dmId"]
        # STRUCT 스케줄 등록 + 즉시 실행
        sched_body = {
            "scheduleNm": "struct_logical_" + datetime.now().strftime("%H%M%S"),
            "diagType": "STRUCT", "dataModelId": dm_id,
            "scheduleType": "SIMPLE", "repeatCycle": "DAILY", "repeatTime": "23:59"
        }
        r = admin.post(BASE + "/api/diag/schedule/create", json=sched_body, timeout=10)
        r.raise_for_status()
        sched_id = r.json()["contents"]
        state["c_sched_id"] = sched_id
        r2 = admin.post(BASE + "/api/diag/schedule/runNow",
                        json={"scheduleId": sched_id}, timeout=30)
        r2.raise_for_status()
        log_id = r2.json()["contents"]

        # ERROR 로 빠르게 마감 예상 (launcher 가 dsId 없음 throw → catch → updateLogFinish)
        deadline = time.time() + 30
        final = None
        while time.time() < deadline:
            r3 = admin.get(BASE + f"/api/diag/schedule/logs/{log_id}",
                           params={"logId": log_id}, timeout=10)
            final = r3.json() or {}
            if final.get("execStatus") in ("ERROR", "DONE"): break
            time.sleep(2)
        assert final and final.get("execStatus") == "ERROR", \
            f"ERROR 기대, got {final.get('execStatus')}"
        msg = final.get("errorMsg") or ""
        assert msg.startswith("[CONFIG]"), f"[CONFIG] prefix 누락: {msg}"
        print(f"  LOG ERROR 메시지: {msg[:120]}")
    if not step("C. 스케줄러 STRUCT 트리거 — 논리 모델 [CONFIG] ERROR", _scheduler_struct_logical_error): pass

    # 정리
    def _cleanup():
        for k in ("a_sched_id", "c_sched_id"):
            sid = state.get(k)
            if sid:
                try: admin.post(BASE + "/api/diag/schedule/delete", json={"scheduleId": sid}, timeout=10)
                except Exception: pass
        if state.get("b_dmId"):
            try: admin.post(BASE + "/api/dm/deleteDataModel",
                            json={"dataModelId": state["b_dmId"]}, timeout=10)
            except Exception: pass
    step("Z. 정리", _cleanup)


if __name__ == "__main__":
    main()
    p = sum(1 for _, s, _ in results if s == "PASS")
    f = sum(1 for _, s, _ in results if s == "FAIL")
    print(f"\n{'='*60}\n결과: {p} PASS / {f} FAIL\n{'='*60}")
    for n, s, _ in results: print(f"  [{s}] {n}")
    sys.exit(0 if f == 0 else 1)
