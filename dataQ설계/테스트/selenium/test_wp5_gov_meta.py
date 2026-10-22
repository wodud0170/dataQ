"""
104번 WP5 — 개인정보·암호화 표시(16번), 범정부 메타 구현 ①(78번)

검증 (시험 데이터는 SQL 로 만든다 — 데이터소스는 접속하지 않는 가짜 행):
  B1. 데이터 모델 > 개인정보 컬럼 — 용어 값이 없으면 도메인 값(Y, 출처 도메인), 선택 컬럼 [암호화 Y] → 컬럼 값 저장
  B2. 범정부 메타 > 정보시스템·DB — 시스템 선택, 정보시스템명 입력·저장, 소속 DB 표시
  B3. 범정부 메타 > 테이블·컬럼 — 모델 테이블 선택 → [전송대상 추가]
  B4. 범정부 메타 > 생성·전송 — [검증] → 이력 '오류 있음' + 검증 결과 목록
시험 시스템·데이터소스·모델·도메인·용어·이력은 끝에서 지운다.
"""
import base64, subprocess, sys, time, traceback
from edgeopt import headless
from dbcmd import PSQL
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:28091"
TAG = time.strftime("%H%M%S")
SYS = "SELWP5S" + TAG
DS = "SELWP5D" + TAG
DM = "SELWP5M" + TAG
DOM = "셀도메인WP5S" + TAG
TERM = "셀용어WP5S" + TAG
TID = "SELWP5T" + TAG
results = []


def step(name, fn):
    print(f"\n=== {name}")
    try:
        fn()
        results.append((name, "PASS"))
        print("  >> PASS")
    except Exception:
        traceback.print_exc()
        results.append((name, "FAIL"))


def psql(sql):
    r = subprocess.run(PSQL + ["-At", "-c", sql],
                       capture_output=True, text=True, timeout=30, encoding="utf-8", errors="replace")
    if r.returncode != 0 or "ERROR" in (r.stderr or ""):
        print("  [psql] " + (r.stderr or "").strip()[:200])
    return (r.stdout or "").strip()


def setup():
    now = "to_char(now(),'YYYYMMDDHH24MISS')"
    grp_clsf = psql("select domain_grp_nm || '|' || domain_clsf_nm from quality.tb_domain where dict_id='DEFAULT' "
                    "and domain_grp_nm is not null and domain_clsf_nm is not null limit 1").split("|")
    psql(f"insert into ndata.tb_data_source (ds_id, dsn, ds_tp, dbms_tp, db_name) values ('{DS}', '셀WP5DB{TAG}', 0, 'Oracle', 'SELWP5')")
    psql(f"insert into quality.tb_sys_info (sys_cd, sys_tp, sys_nm, sys_ds_lst, cret_dt) values ('{SYS}', 1, '셀WP5시스템{TAG}', array['{DS}'], {now})")
    psql(f"insert into quality.tb_data_model (dm_id, dm_nm, dm_sys_cd, dm_ds_id, ver, use_yn, dict_id, cret_dt) "
         f"values ('{DM}', '셀WP5모델{TAG}', '{SYS}', '{DS}', '1', 'Y', 'DEFAULT', {now})")
    psql(f"insert into quality.tb_data_model_obj (dm_id, obj_owner, obj_nm, obj_nm_kr, obj_attr_cnt, use_yn) values "
         f"('{DM}', 'SEL', 'SEL_CUST', '셀 고객', 2, 'Y'), ('{DM}', 'SEL', 'SEL_ORDER', '셀 주문', 1, 'Y')")
    psql(f"insert into quality.tb_data_model_attr (dm_id, obj_owner, obj_nm, attr_nm, attr_nm_kr, data_type, data_len, attr_ord, use_yn) values "
         f"('{DM}', 'SEL', 'SEL_CUST', 'SEL_WP5_NO', '셀번호', 'VARCHAR', 20, 1, 'Y'), "
         f"('{DM}', 'SEL', 'SEL_CUST', 'SEL_WP5_NM', '셀이름', 'VARCHAR', 50, 2, 'Y'), "
         f"('{DM}', 'SEL', 'SEL_ORDER', 'SEL_WP5_ID', '셀주문번호', 'NUMERIC', 10, 1, 'Y')")
    psql(f"insert into quality.tb_domain (domain_id, domain_nm, domain_grp_nm, domain_clsf_nm, domain_desc, data_type, data_len, priv_yn, encr_yn, aprv_yn, dict_id, cret_dt) "
         f"values ('SELWP5DOM{TAG}', '{DOM}', '{grp_clsf[0]}', '{grp_clsf[1]}', '셀레니움 시험', 'VARCHAR', 20, 'Y', 'N', 'Y', 'DEFAULT', {now})")
    psql(f"insert into quality.tb_terms (terms_id, terms_nm, terms_eng_abrv_nm, terms_desc, domain_nm, aprv_yn, dict_id, cret_dt) "
         f"values ('{TID}', '{TERM}', 'SEL_WP5_NO', '셀레니움 시험', '{DOM}', 'Y', 'DEFAULT', {now})")


def cleanup():
    psql(f"delete from quality.tb_gov_meta_send where sys_cd='{SYS}' or dm_id='{DM}' or (sys_cd is null and dm_id is null and cret_user_id='space' "
         f"and start_dt >= to_char(now() - interval '1 hour','YYYYMMDDHH24MISS'))")
    psql(f"delete from quality.tb_gov_meta_sys where sys_cd='{SYS}'")
    psql(f"delete from quality.tb_gov_meta_db where ds_id='{DS}'")
    psql(f"delete from quality.tb_data_model_attr where dm_id='{DM}'; delete from quality.tb_data_model_obj where dm_id='{DM}'; "
         f"delete from quality.tb_data_model where dm_id='{DM}'")
    psql(f"delete from quality.tb_sys_info where sys_cd='{SYS}'; delete from ndata.tb_data_source where ds_id='{DS}'")
    psql(f"delete from quality.tb_terms where terms_id='{TID}'; delete from quality.tb_domain where domain_nm='{DOM}'")


def main():
    setup()
    options = webdriver.EdgeOptions()
    options.add_argument("--log-level=3")
    driver = webdriver.Edge(options=headless(options))
    driver.set_window_size(1600, 900)

    def js(script, *args):
        return driver.execute_script(script, *args)

    def open_menu(group_id, nav_id):
        groups = driver.find_elements(By.CSS_SELECTOR, f"#{group_id} .v-list-group__header")
        if groups and "v-list-group__header--active" not in (groups[0].get_attribute("class") or ""):
            js("arguments[0].click();", groups[0])
            time.sleep(1)
        js(f"document.getElementById('{nav_id}').click();")
        time.sleep(2.5)

    def pick(input_id, text):
        """v-autocomplete 에서 항목 고르기 — 목록이 길어 앞쪽만 그려지므로 글자로 거른다"""
        el = driver.find_element(By.ID, input_id)
        js("arguments[0].closest('.v-input').querySelector('.v-input__slot').click();", el)
        time.sleep(0.5)
        el.send_keys(text)
        time.sleep(0.8)
        items = [i for i in driver.find_elements(By.CSS_SELECTOR, ".menuable__content__active .v-list-item") if text in i.text]
        assert items, f"선택지 '{text}' 없음"
        js("arguments[0].click();", items[0])
        time.sleep(0.8)

    try:
        driver.get(BASE)
        time.sleep(2)
        id_input = WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        id_input.send_keys("space")
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys("123")
        driver.find_element(By.CSS_SELECTOR, ".login-btn, button.v-btn").click()
        WebDriverWait(driver, 15).until(lambda d: "/signin" not in d.current_url)
        time.sleep(3)

        def _b1():
            open_menu("dmGroup", "nav_privColumn")
            pick("privColModel", f"셀WP5모델{TAG}")
            js("document.getElementById('privColSearch').click();")
            time.sleep(3)
            tab = driver.find_element(By.ID, "tab_privColumn")
            rows = [r for r in tab.find_elements(By.CSS_SELECTOR, "#privColTable tbody tr") if "SEL_WP5_NO" in r.text]
            print(f"  행={[r.text.replace(chr(10), ' ') for r in rows]}")
            assert rows and "Y" in rows[0].text and "도메인" in rows[0].text and TERM in rows[0].text
            js("arguments[0].querySelector('.v-data-table__checkbox').click();", rows[0])
            time.sleep(0.5)
            js("document.getElementById('privColSetEncrY').click();")
            time.sleep(2.5)
            v = psql(f"select coalesce(encr_yn,'-') from quality.tb_data_model_attr where dm_id='{DM}' and attr_nm='SEL_WP5_NO'")
            print(f"  컬럼 암호화여부={v}")
            assert v == "Y"
        step("B1. 개인정보 컬럼 — 도메인 값 상속, 컬럼 암호화 지정", _b1)

        def _b2():
            open_menu("govMetaGroup", "nav_govMetaSys")
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "#govSysTable tbody tr") if f"셀WP5시스템{TAG}" in r.text]
            assert rows, "시스템 목록에 시험 시스템이 없음"
            js("arguments[0].click();", rows[0])
            time.sleep(2)
            inp = driver.find_element(By.ID, "govSysNm")
            inp.clear(); inp.send_keys("셀정보시스템")
            dept = driver.find_element(By.ID, "govSysDept")
            dept.clear(); dept.send_keys("셀운영부서")
            js("document.getElementById('govSysSendYn').click();")
            js("document.getElementById('govSysSave').click();")
            time.sleep(2.5)
            v = psql(f"select gov_sys_nm || '|' || oper_dept_nm || '|' || send_yn from quality.tb_gov_meta_sys where sys_cd='{SYS}'")
            tab = driver.find_element(By.ID, "tab_govMetaSys")
            print(f"  저장={v}")
            assert v == "셀정보시스템|셀운영부서|Y"
            assert f"셀WP5DB{TAG}" in tab.text, "소속 DB 가 안 보임"
        step("B2. 정보시스템·DB — 시스템 항목 저장, 소속 DB", _b2)

        def _b3():
            open_menu("govMetaGroup", "nav_govMetaItems")
            pick("govItemsModel", f"셀WP5모델{TAG}")
            js("document.getElementById('govItemsSearch').click();")
            time.sleep(3)
            rows = [r for r in driver.find_elements(By.CSS_SELECTOR, "#govItemsTable tbody tr") if "SEL_" in r.text]
            assert len(rows) == 2, f"테이블 2개가 아님: {len(rows)}"
            js("document.querySelector('#govItemsTable thead .v-data-table__checkbox').click();")
            time.sleep(0.5)
            js("document.getElementById('govItemsSendY').click();")
            time.sleep(2.5)
            v = psql(f"select count(*) from quality.tb_data_model_obj where dm_id='{DM}' and gov_send_yn='Y'")
            print(f"  전송대상={v}")
            assert v == "2"
        step("B3. 테이블·컬럼 — 전송대상 추가", _b3)

        def _b4():
            open_menu("govMetaGroup", "nav_govMetaSend")
            js("document.getElementById('govSendValidate').click();")
            time.sleep(4)
            js("var b=document.querySelector('.swal2-confirm'); if (b) b.click();")
            time.sleep(1)
            tab = driver.find_element(By.ID, "tab_govMetaSend")
            hist = driver.find_elements(By.CSS_SELECTOR, "#govSendHist tbody tr")
            errs = driver.find_elements(By.CSS_SELECTOR, "#govSendErrs tbody tr")
            print(f"  이력 첫 행={hist[0].text.replace(chr(10), ' ') if hist else None} 검증 결과={len(errs)}행")
            assert hist and "오류 있음" in hist[0].text
            assert errs and any("한글 DB명" in e.text for e in errs), "DB 항목 오류가 안 보임"
        step("B4. 생성·전송 — 검증 → 이력·오류 목록", _b4)
    finally:
        driver.quit()
        cleanup()


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
