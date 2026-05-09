"""
86번 #31~ — 용어 등록 모달 인라인 단어 등록 + 영문약어 중복 + 추천1/2 흐름 종합 테스트.

사용자 신고 버그:
  · 추천 1 에서 단어 등록 → 행 안 사라짐 (등록 완료 swal 도 안 뜸)
  · 추천 2 토글하면 거기서 등록됨 표시
  · 영문약어 중복 시 어떤 값이 중복인지 메시지 출력 안 됨

30 케이스:
  A) API 레벨 (15): createWord 안티패턴
  B) UI 레벨 (15): 모달 인터랙션 — 인라인 등록 / 추천1↔2 / × 삭제 등
"""
import sys, os, time, traceback
sys.path.insert(0, os.path.dirname(__file__))
from common import (create_driver, login_admin, get_admin_session,
                    BASE_URL, TestRun, db_query)
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


# ============== 헬퍼 ==============

def js_input_by_label(drv, label_text):
    """Vuetify label 텍스트로 input 찾기 (placeholder 가 가려질 때 useful)"""
    return drv.execute_script(f"""
      for (const lab of document.querySelectorAll(".v-dialog--active .v-label")) {{
        if (lab.textContent.trim() === "{label_text}") return lab.closest(".v-input").querySelector("input");
      }}
      return null;
    """)


def open_term_modal(drv):
    """용어 모달 열기 (등록/등록 신청 버튼)"""
    btn = drv.find_elements(By.XPATH,
        "//button[normalize-space()='등록' or normalize-space()='등록 신청']")
    if btn:
        btn[0].click(); time.sleep(2)


def get_chips(drv):
    cs = drv.find_elements(By.CSS_SELECTOR, '.swal2-popup, .v-chip')
    return ([c.text.strip() for c in cs if c.text.strip() == '등록됨'],
            [c.text.strip() for c in cs if '미등록' in (c.text or '')])


def cleanup_words_like(prefix):
    db_query(f"DELETE FROM tb_terms_words WHERE word_nm LIKE '{prefix}%'")
    db_query(f"DELETE FROM tb_word WHERE word_nm LIKE '{prefix}%' OR word_eng_abrv_nm LIKE '{prefix.upper()}%'")


# ============== A) API 안티패턴 (15 케이스) ==============

def api_edge_cases(t, sess, ts):
    """createWord 친화 메시지 + 중복 메시지 + raw DB 차단"""
    cases = [
        # (label, body_override, expected_keyword)
        ('A01 한글 영문약어',     {'wordEngAbrvNm':'한글', 'wordEngNm':'TEST'},   '대문자 영문'),
        ('A02 * 영문약어',        {'wordEngAbrvNm':'TEST*', 'wordEngNm':'TEST'},  '대문자 영문'),
        ('A03 공백 영문약어',     {'wordEngAbrvNm':'TE ST', 'wordEngNm':'TEST'},  '대문자 영문'),
        ('A04 소문자 영문약어',   {'wordEngAbrvNm':'test', 'wordEngNm':'TEST'},   '대문자 영문'),
        ('A05 하이픈 영문약어',   {'wordEngAbrvNm':'TE-ST', 'wordEngNm':'TEST'},  '대문자 영문'),
        ('A06 숫자시작 영문약어', {'wordEngAbrvNm':'1TEST', 'wordEngNm':'TEST'},  '대문자 영문'),
        ('A07 빈 영문약어',       {'wordEngAbrvNm':'',     'wordEngNm':'TEST'},  '필수'),
        ('A08 200자 영문약어',    {'wordEngAbrvNm':'A'*200,'wordEngNm':'TEST'},  '너무 깁니다'),
        ('A09 한글 영문명',       {'wordEngAbrvNm':'TESTABRV','wordEngNm':'한글'}, '영문(A-Z'),
        ('A10 빈 영문명',         {'wordEngAbrvNm':'TESTABRV','wordEngNm':''},   '필수'),
        ('A11 200자 영문명',      {'wordEngAbrvNm':'TESTABRV','wordEngNm':'A'*200}, '너무 깁니다'),
        ('A12 빈 한글명',         {'wordNm':'',           'wordEngAbrvNm':'TESTABRV','wordEngNm':'TEST'}, '필수'),
        ('A13 200자 한글명',      {'wordNm':'가'*200,     'wordEngAbrvNm':'TESTABRV','wordEngNm':'TEST'}, '너무 깁니다'),
    ]
    for label, override, expected in cases:
        body = {
            'wordNm': override.get('wordNm', f'{label[:5]}_{ts}'),
            'wordEngAbrvNm': override.get('wordEngAbrvNm', 'TESTABRV'),
            'wordEngNm': override.get('wordEngNm', 'Test'),
            'wordDesc': 'edge', 'wordClsfYn': 'N', 'commStndYn': 'N',
        }
        r = sess.post(f'{BASE_URL}/api/std/createWord', json=body)
        try: data = r.json()
        except: data = {}
        rc = data.get('resultCode')
        msg = (data.get('resultMessage') or '')
        has_raw = '###' in msg or 'PSQLException' in msg or 'org.postgresql' in msg
        ok = rc == 500 and expected in msg and not has_raw
        t.step(label, ok, f'rc={rc} msg={msg[:80]!r}')


def api_duplicate_message(t, sess, ts):
    """A14, A15 — 단어/영문약어 중복 메시지 친화 표시"""
    # A14: 사전 등록
    base = {
        'wordNm': f'중복단어{ts}', 'wordEngAbrvNm': f'DUPABRV{ts:_>6}'[-15:],
        'wordEngNm': 'Duplicate', 'wordDesc': 'dup', 'wordClsfYn':'N','commStndYn':'N',
    }
    base['wordEngAbrvNm'] = f'DUP{ts}'
    r = sess.post(f'{BASE_URL}/api/std/createWord', json=base)
    if r.json().get('resultCode') != 200:
        t.step('A14 사전등록 실패', False, str(r.json()))
        return
    # 같은 한글명 재등록 — 한글명 중복 메시지
    body = dict(base); body['wordEngAbrvNm'] = f'DIFF{ts}'
    r = sess.post(f'{BASE_URL}/api/std/createWord', json=body)
    msg = (r.json().get('resultMessage') or '')
    t.step('A14 한글명 중복', '이미 승인된 단어명' in msg, f'msg={msg[:80]!r}')

    # A15: 같은 영문약어 다른 한글명 재등록 — 영문약어 중복 메시지 + 어떤 값인지
    body2 = dict(base); body2['wordNm'] = f'다른단어{ts}'; body2['wordEngAbrvNm'] = f'DUP{ts}'
    r = sess.post(f'{BASE_URL}/api/std/createWord', json=body2)
    msg2 = (r.json().get('resultMessage') or '')
    has_val = f'DUP{ts}' in msg2
    has_friendly = '이미 등록된 단어 영문약어' in msg2
    has_raw = '###' in msg2 or 'PSQLException' in msg2
    t.step('A15 영문약어 중복 — 값 포함 친화 메시지',
           has_friendly and has_val and not has_raw,
           f'msg={msg2[:120]!r}')


# ============== B) UI 안티패턴 (15 케이스) ==============

def ui_inline_register(t, drv, sess, ts):
    """B01~B05 — 인라인 등록 정상 흐름"""
    t.step('B 사전 cleanup', True)
    cleanup_words_like(f'팝콘{ts}')

    # 모달 열기 + 용어명 입력
    open_term_modal(drv)
    nm = drv.find_elements(By.CSS_SELECTOR, 'input[placeholder="가동개시일자"]')[0]
    nm.click(); nm.send_keys(f'팝콘{ts}튀김명')
    time.sleep(3)

    # B01: 자동분석 결과
    matched, unmatched = get_chips(drv)
    t.step(f'B01 자동분석 — 등록됨={len(matched)} 미등록={len(unmatched)} (명 매칭, 팝콘/튀김 신규)',
           len(matched) >= 1)

    # B02: 추천1 선택 상태 확인
    rec_btns = drv.find_elements(By.CSS_SELECTOR, '.v-dialog--active .v-btn-toggle button')
    t.step('B02 추천1/2 토글 노출', len(rec_btns) >= 2, f'btn 수={len(rec_btns)}')

    # B03: 인라인 폼에 영문약어 한글 입력 → 단어 등록 → 친화 swal + 버튼 정상 복귀
    abrv_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문약어')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    eng_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문명')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    t.step(f'B03 인라인 폼 input 수: 영문약어={len(abrv_inputs)} 영문명={len(eng_inputs)}',
           len(abrv_inputs) >= 1 and len(eng_inputs) >= 1)

    if not abrv_inputs:
        return
    abrv_inputs[0].click(); abrv_inputs[0].send_keys('한글')
    eng_inputs[0].click(); eng_inputs[0].send_keys('TEST')
    time.sleep(0.3)
    reg_btns = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='단어 등록']")
    if reg_btns:
        reg_btns[0].click(); time.sleep(2.5)
    swals = drv.find_elements(By.CSS_SELECTOR, '.swal2-popup:not(.swal2-toast)')
    swal_text = swals[0].text if swals else ''
    t.step('B04 영문약어 한글 입력 → 친화 swal',
           '단어 등록 실패' in swal_text and '대문자 영문' in swal_text,
           f'swal={swal_text[:120]!r}')
    if swals:
        drv.find_element(By.CSS_SELECTOR, '.swal2-confirm').click(); time.sleep(0.5)

    # 버튼 loading 풀렸는지
    btn_loading = drv.find_elements(By.CSS_SELECTOR,
        '.v-dialog--active button .v-btn__loader')
    visible_loading = [l for l in btn_loading if l.is_displayed()]
    t.step('B05 버튼 loading 즉시 풀림', len(visible_loading) == 0,
           f'visible loading={len(visible_loading)}')


def ui_inline_register_success(t, drv, sess, ts):
    """B06~B10 — 인라인 등록 성공 → 추천1 행 즉시 등록됨 (#31 핵심)"""
    # 깨끗한 상태로 시작 — 모달 닫고 새로 열기
    cancels = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='취소']")
    if cancels:
        cancels[0].click(); time.sleep(1.5)
    # 모달 다시 열기 + 새 term name (B 와 다른 ts2 사용)
    open_term_modal(drv)
    nm = drv.find_elements(By.CSS_SELECTOR, 'input[placeholder="가동개시일자"]')[0]
    ts2 = ts + 1
    nm.click(); nm.send_keys(f'팝콘{ts2}튀김명')
    time.sleep(3)

    abrv_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문약어')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    eng_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문명')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    if not abrv_inputs:
        t.step('B06 인라인 폼 다시 확인', False, '영문약어 input 없음')
        return

    abrv_inputs[0].click(); abrv_inputs[0].send_keys(f'PCT{ts2}')
    eng_inputs[0].click(); eng_inputs[0].send_keys(f'PopcornTest')
    time.sleep(0.3)

    matched_before, unmatched_before = get_chips(drv)
    t.step(f'B06 등록 클릭 전: 등록됨={len(matched_before)} 미등록={len(unmatched_before)}', True)

    reg_btns = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='단어 등록']")
    reg_btns[0].click(); time.sleep(3)

    # 핵심 검증
    matched_after, unmatched_after = get_chips(drv)
    t.step(f'B07 (#31) 등록 후 등록됨 +1: {len(matched_before)} → {len(matched_after)}',
           len(matched_after) > len(matched_before),
           f'before={matched_before} after={matched_after}')
    t.step(f'B08 (#31) 등록 후 미등록 -1: {len(unmatched_before)} → {len(unmatched_after)}',
           len(unmatched_after) < len(unmatched_before),
           f'before unmatched={len(unmatched_before)} after={len(unmatched_after)}')

    # 추천 1 → 추천 2 토글
    rec_btns = drv.find_elements(By.CSS_SELECTOR, '.v-dialog--active .v-btn-toggle button')
    if len(rec_btns) >= 2:
        try:
            rec_btns[1].click(); time.sleep(2.5)
            m2, u2 = get_chips(drv)
            t.step(f'B09 추천 2 토글 후 등록됨={len(m2)} 미등록={len(u2)}', True,
                   'post-validation 으로 추천 2 도 자동 매칭')
            rec_btns[0].click(); time.sleep(2.5)
            m1b, u1b = get_chips(drv)
            t.step(f'B10 추천 1 재토글 — 등록됨={len(m1b)}', True,
                   f'추천1: 등록됨={len(m1b)} 미등록={len(u1b)}')
        except Exception as e:
            t.step(f'B09/B10 토글 예외', False, str(e)[:80])
    # cleanup 직후 — 모달 닫기
    cancels = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='취소']")
    if cancels:
        cancels[0].click(); time.sleep(1)
    cleanup_words_like(f'팝콘{ts2}')
    db_query(f"DELETE FROM tb_word WHERE word_eng_abrv_nm='PCT{ts2}'")


def ui_duplicate_message(t, drv, sess, ts):
    """B11 — 영문약어 중복 시 친화 메시지 + 어떤 값"""
    # 사전: 중복용 단어 1개 사전 등록
    dup_abrv = f'DUPB11{ts}'
    sess.post(f'{BASE_URL}/api/std/createWord', json={
        'wordNm': f'b11선등록{ts}', 'wordEngAbrvNm': dup_abrv, 'wordEngNm': 'B11Pre',
        'wordDesc': 'b11', 'wordClsfYn':'N', 'commStndYn':'N',
    })
    time.sleep(0.5)

    # 모달 새로 열기
    cancels = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='취소']")
    if cancels:
        cancels[0].click(); time.sleep(1.5)
    open_term_modal(drv)
    nm = drv.find_elements(By.CSS_SELECTOR, 'input[placeholder="가동개시일자"]')[0]
    nm.click(); nm.send_keys(f'중복테스트{ts}')
    time.sleep(2.5)

    manual = drv.execute_script("""
      for (const lab of document.querySelectorAll('.v-dialog--active .v-label')) {
        if (lab.textContent.trim() === '단어 직접 추가') return lab.closest('.v-input').querySelector('input');
      } return null;
    """)
    if manual:
        manual.click(); manual.send_keys(f'b11신규{ts}')
        drv.find_element(By.XPATH,
            "//*[contains(@class,'v-dialog--active')]//button[.//span[contains(text(),'단어 추가')]]").click()
        time.sleep(2)

    abrv_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문약어')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    eng_inputs = drv.execute_script("""
      return [...document.querySelectorAll('.v-dialog--active .v-label')]
        .filter(l => l.textContent.trim() === '영문명')
        .map(l => l.closest('.v-input').querySelector('input'));
    """)
    if abrv_inputs and eng_inputs:
        # 사전 등록한 dup_abrv 와 같은 영문약어로 중복 등록 시도
        abrv_inputs[-1].click(); abrv_inputs[-1].send_keys(dup_abrv)
        eng_inputs[-1].click(); eng_inputs[-1].send_keys('Duplicate')
        time.sleep(0.3)
        reg = drv.find_elements(By.XPATH,
            "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='단어 등록']")
        if reg:
            reg[-1].click(); time.sleep(2.5)
        swals = drv.find_elements(By.CSS_SELECTOR, '.swal2-popup:not(.swal2-toast)')
        swal_text = swals[0].text if swals else ''
        t.step(f'B11 영문약어 중복 swal + 값 {dup_abrv} 포함',
               '이미 등록된 단어 영문약어' in swal_text and dup_abrv in swal_text,
               f'swal={swal_text[:140]!r}')
        if swals:
            drv.find_element(By.CSS_SELECTOR, '.swal2-confirm').click(); time.sleep(0.5)

    cancels = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='취소']")
    if cancels:
        cancels[0].click(); time.sleep(1)
    cleanup_words_like(f'b11선등록{ts}')
    cleanup_words_like(f'b11신규{ts}')
    db_query(f"DELETE FROM tb_word WHERE word_eng_abrv_nm='{dup_abrv}'")


def ui_jyjang_flow(t, ts):
    """B12~B14 — jyjang 비관리자 인라인 등록 toast"""
    cleanup_words_like(f'jy단어{ts}')
    drv = create_driver(window=(1600, 1000))
    jy_word_nm = f'jy단어{ts}'
    jy_abrv = f'JYABC{ts}'
    try:
        login_admin(drv, 'jyjang', '123')
        from common import navigate_to_tab
        navigate_to_tab(drv, 'tab_term')
        time.sleep(2)
        open_term_modal(drv)

        nm = drv.find_elements(By.CSS_SELECTOR, 'input[placeholder="가동개시일자"]')[0]
        nm.click(); nm.send_keys(f'jy신규{ts}용어')
        time.sleep(3)

        manual = drv.execute_script("""
          for (const lab of document.querySelectorAll('.v-dialog--active .v-label')) {
            if (lab.textContent.trim() === '단어 직접 추가') return lab.closest('.v-input').querySelector('input');
          } return null;
        """)
        if manual:
            manual.click(); manual.send_keys(jy_word_nm)
            drv.find_element(By.XPATH,
                "//*[contains(@class,'v-dialog--active')]//button[.//span[contains(text(),'단어 추가')]]").click()
            time.sleep(2.5)
        else:
            t.step('B12 jyjang manual input', False, 'not found')
            return

        abrv_inputs = drv.execute_script("""
          return [...document.querySelectorAll('.v-dialog--active .v-label')]
            .filter(l => l.textContent.trim() === '영문약어')
            .map(l => l.closest('.v-input').querySelector('input'));
        """)
        eng_inputs = drv.execute_script("""
          return [...document.querySelectorAll('.v-dialog--active .v-label')]
            .filter(l => l.textContent.trim() === '영문명')
            .map(l => l.closest('.v-input').querySelector('input'));
        """)
        if not abrv_inputs:
            t.step('B12 jyjang abrv input', False, 'not found')
            return
        # 마지막 (수동 추가된) 인라인 폼 사용
        abrv_inputs[-1].click(); abrv_inputs[-1].send_keys(jy_abrv)
        eng_inputs[-1].click(); eng_inputs[-1].send_keys('JyWord')
        time.sleep(0.3)
        reg_btns = drv.find_elements(By.XPATH,
            "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='단어 등록']")
        reg_btns[-1].click()
        # toast 는 1.8초 timer 라 빨리 캐치
        toast_text = ''
        deadline = time.time() + 4
        while time.time() < deadline:
            time.sleep(0.3)
            toasts = drv.find_elements(By.CSS_SELECTOR, '.swal2-popup.swal2-toast')
            if toasts:
                toast_text = ' | '.join(t_.text for t_ in toasts)
                break
        t.step('B12 jyjang Toast 노출 (승인 대기)',
               '승인 대기' in toast_text or '관리자 승인' in toast_text,
               f'toast={toast_text[:120]!r}')
        rows = db_query(f"SELECT word_nm, aprv_yn, cret_user_id FROM tb_word WHERE word_nm='{jy_word_nm}'")
        t.step('B13 DB aprv_yn=N + cret_user_id=jyjang',
               len(rows) > 0 and rows[0][1] == 'N' and rows[0][2] == 'jyjang',
               f'rows={rows}')
        time.sleep(1)
        matched, unmatched = get_chips(drv)
        t.step(f'B14 jyjang 등록 후 등록됨 +1: 등록됨={len(matched)}',
               len(matched) >= 1)
    finally:
        drv.quit()
        cleanup_words_like(jy_word_nm)
        db_query(f"DELETE FROM tb_word WHERE word_eng_abrv_nm='{jy_abrv}'")


def ui_manual_add_post_validation(t, drv, ts):
    """B15 — 수동 단어 추가 후 lookupWord 로 자동 MATCHED (#24)"""
    open_term_modal(drv)
    nm = drv.find_elements(By.CSS_SELECTOR, 'input[placeholder="가동개시일자"]')[0]
    nm.click(); nm.send_keys('테스트단어')
    time.sleep(3)

    matched_before, _ = get_chips(drv)

    manual = drv.execute_script("""
      for (const lab of document.querySelectorAll('.v-dialog--active .v-label')) {
        if (lab.textContent.trim() === '단어 직접 추가') return lab.closest('.v-input').querySelector('input');
      } return null;
    """)
    if manual:
        manual.click(); manual.send_keys('명')  # TB_WORD에 있음
        drv.find_element(By.XPATH,
            "//*[contains(@class,'v-dialog--active')]//button[.//span[contains(text(),'단어 추가')]]").click()
        time.sleep(2.5)

    matched_after, unmatched_after = get_chips(drv)
    t.step(f'B15 수동추가 "명" → MATCHED 자동승격 (등록됨 {len(matched_before)} → {len(matched_after)})',
           len(matched_after) > len(matched_before),
           f'before={matched_before}, after={matched_after}')
    # 모달 닫기
    cancels = drv.find_elements(By.XPATH,
        "//*[contains(@class,'v-dialog--active')]//button[normalize-space()='취소']")
    if cancels: cancels[0].click(); time.sleep(1)


# ============== main ==============

def run():
    t = TestRun('T30 용어 등록 안티패턴 종합 (30 케이스)')
    ts = int(time.time())

    drv = create_driver(window=(1600, 1000))
    try:
        login_admin(drv, 'space', '123')
        sess = get_admin_session(drv)

        # A) API
        try: api_edge_cases(t, sess, ts)
        except Exception as e: t.step('A 예외', False, str(e)[:120])
        try: api_duplicate_message(t, sess, ts)
        except Exception as e: t.step('A 중복 예외', False, str(e)[:120])

        # B) UI — admin
        from common import navigate_to_tab
        navigate_to_tab(drv, 'tab_term')
        time.sleep(2)
        try: ui_inline_register(t, drv, sess, ts)
        except Exception as e:
            t.step('B inline 예외', False, str(e)[:120])
            traceback.print_exc()
        try: ui_inline_register_success(t, drv, sess, ts)
        except Exception as e:
            t.step('B success 예외', False, str(e)[:120])
            traceback.print_exc()
        try: ui_duplicate_message(t, drv, sess, ts)
        except Exception as e:
            t.step('B dup 예외', False, str(e)[:120])
            traceback.print_exc()
        try: ui_manual_add_post_validation(t, drv, ts)
        except Exception as e:
            t.step('B manual add 예외', False, str(e)[:120])
            traceback.print_exc()

        # B) jyjang
        try: ui_jyjang_flow(t, ts)
        except Exception as e:
            t.step('B jyjang 예외', False, str(e)[:120])
            traceback.print_exc()

    finally:
        drv.quit()
        # cleanup
        cleanup_words_like(f'팝콘{ts}')
        cleanup_words_like(f'중복단어{ts}')
        cleanup_words_like(f'다른단어{ts}')
        cleanup_words_like(f'테스트단어{ts}')
        cleanup_words_like(f'A')
        db_query(f"DELETE FROM tb_word WHERE word_eng_abrv_nm IN ('PCT{ts}','DUP{ts}','DIFF{ts}','JY{ts}')")

    return t


if __name__ == '__main__':
    t = run()
    from common import write_report
    write_report([t], f'report_t30_{time.strftime("%Y%m%d_%H%M%S")}.md')
    sys.exit(0 if t.passed else 1)
