"""
86번 #11 통합 테스트 v2 — 전체 실행

사용:
  cd dataQ설계/테스트/selenium/v2_2026-05-09
  python run_all.py            # 전체 실행
  python run_all.py t04        # 특정 테스트만
  python run_all.py t04 t05    # 여러 개

전제:
  · q-center 28091 떠 있음 (admin 로그인 가능)
  · q-executor 28098 떠 있음 (진단 실행 필요한 테스트용)
  · dataq-db 컨테이너 동작 (DB 검증)
  · oracle-xe 컨테이너 동작 (수집 검증)
  · Edge 브라우저 + msedgedriver
  · 사전 데이터: HRM_APP/INV_APP/SALES_APP 의 COMPANY_INFO 테이블 (T02 시나리오대로)
  · 사용자가 "수동DB등록모델" 같은 모델 1개 등록 + 4 schema 수집 완료
"""
import sys, os, time

sys.path.insert(0, os.path.dirname(__file__))
from common import write_report

# 모듈 import
import t01_login_admin
import t02_collect_dup_obj_nm
import t03_table_column_screen
import t04_diag_target_owner_isolation
import t05_std_diag_before_after
import t06_diag_result_match_chip
import t07_column_owner_inherit
import t08_table_regex_validation
import t09_excel_round_trip
import t10_no_fake_success
import t11_owner_cascade
import t12_pagination_overlap
import t30_term_register_edge
import t40_qual_domain_rule
import t41_qual_rule_manage
import t42_qual_rule_result
import t43_qual_value_profile
import t44_qual_col_rule
import t45_qual_stats
import t46_schedule_admin_check

ALL = [
    ("t01", t01_login_admin),
    ("t02", t02_collect_dup_obj_nm),
    ("t03", t03_table_column_screen),
    ("t04", t04_diag_target_owner_isolation),
    ("t05", t05_std_diag_before_after),
    ("t06", t06_diag_result_match_chip),
    ("t07", t07_column_owner_inherit),
    ("t08", t08_table_regex_validation),
    ("t09", t09_excel_round_trip),
    ("t10", t10_no_fake_success),
    ("t11", t11_owner_cascade),
    ("t12", t12_pagination_overlap),
    ("t30", t30_term_register_edge),
    ("t40", t40_qual_domain_rule),
    ("t41", t41_qual_rule_manage),
    ("t42", t42_qual_rule_result),
    ("t43", t43_qual_value_profile),
    ("t44", t44_qual_col_rule),
    ("t45", t45_qual_stats),
    ("t46", t46_schedule_admin_check),
]


def main():
    args = sys.argv[1:]
    selected = ALL
    if args:
        selected = [(k, m) for (k, m) in ALL if k in args]
    if not selected:
        print(f"매칭 테스트 없음. 사용 가능: {[k for k,_ in ALL]}")
        return 1

    print(f"\n{'='*70}")
    print(f"86번 #11 통합 테스트 v2 — {len(selected)} 케이스")
    print(f"{'='*70}\n")

    runs = []
    for key, mod in selected:
        print(f"\n>>> {key}: {mod.__doc__.strip().splitlines()[0] if mod.__doc__ else key}")
        try:
            t = mod.run()
            runs.append(t)
        except Exception as e:
            print(f"  [{key} 실패] {e}")
        print()

    name = f"report_{time.strftime('%Y%m%d_%H%M%S')}.md"
    write_report(runs, name)
    passed = sum(1 for r in runs if r.passed)
    print(f"\n{'='*70}")
    print(f"FINAL: {passed}/{len(runs)} 통과")
    print(f"{'='*70}")
    return 0 if passed == len(runs) else 1


if __name__ == "__main__":
    sys.exit(main())
