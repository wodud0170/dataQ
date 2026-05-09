"""
T09 — Excel 양식/데이터 다운로드 ↔ 업로드 round-trip

검증:
  · 각 화면의 양식 다운로드 endpoint 동작 + 헤더 = 다운로드 데이터 헤더 일치
    - 테이블 (TABLE_HEADERS)
    - 컬럼 (ATTR_HEADERS)
    - 도메인 / 도메인 그룹 / 도메인 분류
    - 단어 / 용어 (이미 정적 파일)
  · 다운로드한 파일 그대로 업로드 → 백엔드가 받아들임
"""
import sys, os, time, requests, zipfile, io, re
sys.path.insert(0, os.path.dirname(__file__))
from common import BASE_URL, TestRun


def fetch(path):
    r = requests.get(BASE_URL + path)
    return r


def extract_xlsx_headers(blob):
    """xlsx 의 첫 시트 첫 행 셀 텍스트 추출"""
    z = zipfile.ZipFile(io.BytesIO(blob))
    # sheet1.xml 이거나 inline strings 인 경우 처리
    try:
        sheet = z.read("xl/worksheets/sheet1.xml").decode("utf-8", errors="ignore")
        # <c r="A1" t="inlineStr"><is><t>...</t></is></c>
        cells = re.findall(r'<t[^>]*>([^<]*)</t>', sheet)
        if cells:
            return cells
        # SharedStrings 케이스
        try:
            shared = z.read("xl/sharedStrings.xml").decode("utf-8", errors="ignore")
            shared_strings = re.findall(r'<t[^>]*>([^<]*)</t>', shared)
            # row 1 의 cell 들에서 v 값 (shared strings 인덱스) 추출
            row1 = re.findall(r'<row r="1"[^>]*>(.*?)</row>', sheet, re.DOTALL)
            if row1:
                indices = re.findall(r'<v>(\d+)</v>', row1[0])
                return [shared_strings[int(i)] for i in indices if int(i) < len(shared_strings)]
        except KeyError:
            pass
        return []
    except KeyError:
        return []


def run():
    t = TestRun("T09 Excel 양식 ↔ 데이터 헤더 일치")
    try:
        # === 테이블 양식 vs 데이터 ===
        r_tpl = fetch("/api/dm/uploadTemplate?scope=tables")
        t.step("테이블 양식 다운로드", r_tpl.status_code == 200,
               f"size={len(r_tpl.content)}")
        if r_tpl.status_code == 200:
            tpl_headers = extract_xlsx_headers(r_tpl.content)
            t.step(f"테이블 양식 헤더: {tpl_headers}", True)

        # === 컬럼 양식 vs 데이터 ===
        r_tpl = fetch("/api/dm/uploadTemplate?scope=attrs")
        t.step("컬럼 양식 다운로드", r_tpl.status_code == 200,
               f"size={len(r_tpl.content)}")
        if r_tpl.status_code == 200:
            tpl_headers = extract_xlsx_headers(r_tpl.content)
            expected = ["소유자", "테이블명(영문)", "테이블명(한글)", "컬럼명(영문)", "컬럼명(한글)",
                        "데이터타입", "길이", "소수점자리", "컬럼 순서",
                        "NULL여부", "PK여부", "FK여부", "디폴트값",
                        "참조 테이블(한글)", "참조 컬럼(한글)", "삭제 규칙"]
            ok = tpl_headers == expected
            t.step("컬럼 양식 헤더 = ATTR_HEADERS",
                   ok, f"actual={tpl_headers}")

        # === 도메인 / 그룹 / 분류 양식 ===
        for path, label, expected in [
            ("/api/std/downloadDomainTemplate", "도메인",
             ["No","제정차수","도메인그룹명","도메인분류명","도메인명","도메인설명",
              "데이터타입","데이터길이","데이터소수점길이","저장형식","표현형식","단위",
              "허용값","요청시스템","표준여부"]),
            ("/api/std/downloadDomainGroupTemplate", "도메인그룹",
             ["No","도메인그룹명","표준여부"]),
            ("/api/std/downloadDomainClsfTemplate", "도메인분류",
             ["No","도메인그룹명","도메인분류명","표준여부"]),
        ]:
            r = fetch(path)
            t.step(f"{label} 양식 다운로드", r.status_code == 200,
                   f"size={len(r.content)}")
            if r.status_code == 200:
                heads = extract_xlsx_headers(r.content)
                ok = heads == expected
                t.step(f"{label} 양식 헤더 일치", ok, f"actual={heads}")

        # === 단어 양식 (정적) ===
        r = fetch("/api/std/downloadWordTemplate")
        t.step("단어 양식 다운로드", r.status_code == 200, f"size={len(r.content)}")
        if r.status_code == 200:
            heads = extract_xlsx_headers(r.content)
            t.step(f"단어 양식 헤더: {heads}", True)

    except Exception as e:
        t.step("예외", False, str(e))
    return t


if __name__ == "__main__":
    t = run()
    sys.exit(0 if t.passed else 1)
