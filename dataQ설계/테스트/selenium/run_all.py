"""
셀레니움 통합 러너 — 비-품질진단 테스트 일괄 실행 + 결과 집계.

제외: test_qual_*.py (값/룰 진단 — 개발중)
포함: 모든 test_*.py 그 외 + 신규 test_perm_matrix.py / test_crud_std_dict.py / test_date_range_filter.py

실행: python run_all.py [--filter pattern]  (filter 로 부분 실행 가능)
"""
import os, subprocess, sys, time, glob, re

HERE = os.path.dirname(os.path.abspath(__file__))
PYTHON = sys.executable
TIMEOUT_PER_TEST = 600  # 10 min

# 각 테스트 사이 폴루션 정리.
# 테스트가 INSERT 하는 패턴만 좁혀서 삭제 (실 데이터 영향 없음).
CLEANUP_SQL = (
    "DELETE FROM TB_TERMS_WORDS WHERE TERMS_NM ~ '^(셀|테스트)';"
    "DELETE FROM TB_TERMS       WHERE TERMS_NM ~ '^(셀|테스트)';"
    "DELETE FROM TB_WORD        WHERE WORD_NM  ~ '^(셀|테스트단어_)';"
    "DELETE FROM TB_DOMAIN      WHERE DOMAIN_NM LIKE '셀도메인%';"
    "DELETE FROM TB_DOMAIN_GRP  WHERE DOMAIN_GRP_NM LIKE '테스트그룹_%' OR DOMAIN_GRP_NM LIKE '분류용그룹_%';"
    "DELETE FROM TB_DOMAIN_CLSF WHERE DOMAIN_CLSF_NM LIKE '테스트분류_%';"
    "DELETE FROM TB_APRV_STATS  WHERE REQ_ITEM_NM ~ '^(셀|테스트)';"
    "DELETE FROM TB_DATA_MODEL_ATTR WHERE OBJ_NM LIKE 'IMSI_%';"
    "DELETE FROM TB_DATA_MODEL_OBJ  WHERE OBJ_NM LIKE 'IMSI_%';"
)


def cleanup_db():
    """테스트 폴루션 정리. 실패해도 무시."""
    try:
        cmd = ["docker", "exec", "-i", "dataq-db", "psql", "-U", "admin", "-d", "postgres",
               "-c", "SET search_path TO quality;" + CLEANUP_SQL]
        subprocess.run(cmd, capture_output=True, text=True, timeout=15)
    except Exception:
        pass


def cleanup_browsers():
    """좀비 Edge 프로세스 정리. Selenium 누수 방어."""
    if sys.platform != "win32":
        return
    try:
        subprocess.run(["taskkill", "/F", "/IM", "msedgedriver.exe"],
                       capture_output=True, timeout=10)
    except Exception:
        pass

# 우선순위 그룹 — 빠른 거 먼저, UI 무거운 거 나중. 같은 그룹 안에선 알파벳 순.
GROUPS = [
    ("API/Login (가벼움)", [
        "test_login.py",
        "test_phase1_schedule_api.py",
        "test_date_range_filter.py",
        "test_perm_matrix.py",
        "test_crud_std_dict.py",
    ]),
    ("표준 사전 (단어/용어/도메인)", [
        "test_word_approval_flow.py",
        "test_full_approval_flow.py",
        "test_global_search.py",
        "test_term_recommend_cases.py",
    ]),
    ("데이터 모델 (Phase 5 / attr / cascade)", [
        "test_53_grid_add_save.py",
        "test_53_paste.py",
        "test_53_resolve_trigger.py",
        "test_53_upload_attrs.py",
        "test_53_upload_tables.py",
        "test_attr_display_and_resolve_btn.py",
        "test_attr_update_preserves_meta.py",
        "test_obj_rename_cascade.py",
        "test_cascade_and_word_first.py",
        "test_ca8858d_clsf_domain.py",
        "test_reject_physical_delete.py",
    ]),
    ("논리/물리 모델 진단", [
        "test_logical_model_diag_handling.py",
        "test_logical_model_e2e.py",
        "test_diag_includes_nonstandard.py",
    ]),
    ("진단 스케줄 (Phase 2~4)", [
        "test_phase2_schedule_exec.py",
        "test_phase3_schedule_ui.py",
        "test_phase4_schedule_coverage.py",
        "test_phase4_ui_admin_gate.py",
    ]),
    ("진단 제외 관리 (79번)", [
        "test_diag_target_imsi.py",
    ]),
]


def discover_extra():
    """그룹에 명시 안 된 (그러면서 qual 이 아닌) 테스트 자동 포함."""
    listed = set()
    for _, files in GROUPS:
        for f in files:
            listed.add(f)
    all_tests = sorted(os.path.basename(p) for p in glob.glob(os.path.join(HERE, "test_*.py")))
    extra = []
    for f in all_tests:
        if f.startswith("test_qual_"):
            continue
        if f in listed:
            continue
        extra.append(f)
    if extra:
        GROUPS.append(("기타 (자동 발견)", extra))


def run_test(filename, filter_pat):
    if filter_pat and filter_pat not in filename:
        return ("SKIP", 0, "filter 미일치")
    path = os.path.join(HERE, filename)
    if not os.path.exists(path):
        return ("MISSING", 0, "파일 없음")
    t0 = time.time()
    try:
        proc = subprocess.run([PYTHON, path], capture_output=True, text=True,
                               timeout=TIMEOUT_PER_TEST, encoding="utf-8", errors="replace")
        elapsed = time.time() - t0
        # 결과 라인 추출
        tail_lines = proc.stdout.splitlines()[-30:]
        summary = next((l for l in reversed(tail_lines) if "결과:" in l or "PASS /" in l), "")
        # PASS 판정
        if proc.returncode == 0:
            return ("PASS", elapsed, summary or "exit 0")
        else:
            # 실패한 step 추출
            fail_steps = [l for l in tail_lines if "[FAIL]" in l]
            msg = (summary or f"exit {proc.returncode}")
            if fail_steps:
                msg += " — " + "; ".join(s.strip()[:60] for s in fail_steps[:3])
            return ("FAIL", elapsed, msg)
    except subprocess.TimeoutExpired:
        return ("TIMEOUT", time.time() - t0, f"timeout > {TIMEOUT_PER_TEST}s")
    except Exception as e:
        return ("ERROR", time.time() - t0, str(e))


def main():
    filter_pat = None
    if len(sys.argv) > 2 and sys.argv[1] == "--filter":
        filter_pat = sys.argv[2]
        print(f"[필터] {filter_pat}")

    discover_extra()

    # 시작 시 한 번 폴루션 청소
    cleanup_db()
    cleanup_browsers()

    all_results = []
    overall_t0 = time.time()
    for group_name, files in GROUPS:
        print(f"\n{'#'*60}\n# {group_name} ({len(files)}개)\n{'#'*60}")
        for f in files:
            print(f"\n[RUN] {f}")
            status, elapsed, msg = run_test(f, filter_pat)
            print(f"  → [{status}] {elapsed:.0f}s — {msg[:120]}")
            all_results.append((group_name, f, status, elapsed, msg))
            # 각 테스트 끝나고 폴루션 + 좀비 정리 (다음 테스트 영향 차단)
            cleanup_db()
            cleanup_browsers()

    overall_elapsed = time.time() - overall_t0

    # 집계
    print(f"\n\n{'='*70}\n[FINAL SUMMARY] {overall_elapsed:.0f}초 총소요\n{'='*70}")
    counts = {"PASS": 0, "FAIL": 0, "TIMEOUT": 0, "ERROR": 0, "MISSING": 0, "SKIP": 0}
    for grp, f, status, elapsed, msg in all_results:
        counts[status] = counts.get(status, 0) + 1

    for status, n in counts.items():
        if n: print(f"  [{status}] {n}")

    print(f"\n{'-'*70}\n[GROUP 별 상세]")
    cur_grp = None
    for grp, f, status, elapsed, msg in all_results:
        if grp != cur_grp:
            print(f"\n## {grp}")
            cur_grp = grp
        marker = {"PASS": "✓", "FAIL": "✗", "TIMEOUT": "⏱", "ERROR": "!", "MISSING": "?", "SKIP": "-"}.get(status, "?")
        print(f"  {marker} [{status:7s}] {elapsed:5.0f}s  {f}")
        if status not in ("PASS", "SKIP"):
            print(f"        └ {msg[:140]}")

    # 종료코드: PASS 가 아닌 실 실패만 카운트 (SKIP/MISSING 제외)
    fail_total = counts["FAIL"] + counts["TIMEOUT"] + counts["ERROR"]
    return 0 if fail_total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
