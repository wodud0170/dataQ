"""
모든 CAMS 매뉴얼에서 도메인 지식 자동 추출.

추출 패턴:
  1) 목차 — 페이지 첫 부분 "X.Y. 제목" 패턴
  2) 등록 입력 필드 — "주요 등록 항목 설명" 이후 N라인
  3) 코드값 정의 — "A:문서대장" / "01:일반문서" / "(코드=값)" 패턴
  4) 업무 흐름 키워드 — "절차", "흐름", "프로세스"
  5) 용어 정의 — 영문 약어·KIKWANCODE·BSID 등 코드명
"""
import re
from pathlib import Path
from collections import defaultdict, Counter

SRC = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\05_CAMS기록물유형_2026-05-21\_manual_txt")
OUT_DIR = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\05_CAMS기록물유형_2026-05-21\_mining")
OUT_DIR.mkdir(exist_ok=True)

# 추출 패턴
PAT_TOC = re.compile(r"^(\d+(?:\.\d+){1,4})\.?\s+([가-힣A-Za-z][^\n]{0,40})$", re.MULTILINE)
PAT_CODE_KV = re.compile(r"([A-Z0-9]{1,4})\s*[:=]\s*([가-힣A-Za-z][가-힣A-Za-z0-9_/·\(\)\s]{1,20})")
PAT_NUM_CODE = re.compile(r"(\d{1,2})\s*[:=]\s*([가-힣][가-힣A-Za-z0-9_/·\s]{1,15})")
PAT_INPUT_FIELD_HEADER = re.compile(r"(?:주요\s*)?등록\s*항목\s*(?:설명)?|입력\s*항목|검색\s*조건")

files = sorted(SRC.glob("*.txt"))
print(f"매뉴얼 텍스트 파일: {len(files)}")

# === 1) 목차 추출 ===
toc_all = {}
for f in files:
    text = f.read_text(encoding="utf-8")
    tocs = []
    for m in PAT_TOC.finditer(text):
        num, title = m.group(1), m.group(2).strip()
        # 페이지 표시 라인이나 "=== PAGE" 무시
        if "PAGE" in title or "참고" in title:
            continue
        tocs.append((num, title))
    toc_all[f.name] = tocs

with open(OUT_DIR / "_TOC_전체.txt", "w", encoding="utf-8") as out:
    for fn, tocs in toc_all.items():
        out.write(f"\n{'='*80}\n{fn}\n{'='*80}\n")
        for num, title in tocs:
            depth = num.count(".")
            indent = "  " * depth
            out.write(f"{indent}{num}. {title}\n")
print(f"  → 목차 추출 ({sum(len(v) for v in toc_all.values())}개 항목)")

# === 2) 등록 입력 필드 — 매뉴얼 별로 ===
input_fields = {}
for f in files:
    text = f.read_text(encoding="utf-8")
    lines = text.split("\n")
    fields = []   # (section, field_name, description)
    current_section = ""

    # 매뉴얼별 검색
    for i, line in enumerate(lines):
        ls = line.strip()
        # 섹션 헤더 캐치
        m = re.match(r"^(\d+\.\d+(?:\.\d+){0,3})\.?\s+([가-힣A-Za-z].{2,30})$", ls)
        if m:
            current_section = f"{m.group(1)} {m.group(2)}"

        # "등록 항목 설명" 또는 유사
        if PAT_INPUT_FIELD_HEADER.search(ls):
            # 다음 30라인에서 필드명 후보 추출
            fld_pairs = []
            j = i + 1
            buf = []
            while j < min(i+80, len(lines)):
                nl = lines[j].strip()
                if not nl:
                    j += 1; continue
                # 다음 섹션 표시면 stop
                if re.match(r"^\d+\.\d+\.\d+", nl) or re.match(r"^=== PAGE", nl):
                    break
                buf.append(nl)
                j += 1
            # 짧은 한글 라벨 (≤12자) + 설명 패턴 캐치
            for k in range(len(buf)-1):
                lab = buf[k]
                if 2 <= len(lab) <= 12 and re.match(r"^[가-힣][가-힣A-Za-z0-9/·()\s]*$", lab):
                    desc = buf[k+1] if k+1 < len(buf) else ""
                    if len(desc) > 5 and "입력" in desc or "선택" in desc or "검색" in desc:
                        fld_pairs.append((lab, desc[:60]))
            if fld_pairs:
                fields.append((current_section, fld_pairs))
    input_fields[f.name] = fields

with open(OUT_DIR / "_입력필드_매뉴얼별.txt", "w", encoding="utf-8") as out:
    for fn, lst in input_fields.items():
        if not lst: continue
        out.write(f"\n{'='*80}\n{fn}\n{'='*80}\n")
        for section, pairs in lst:
            out.write(f"\n--- {section} ---\n")
            for lab, desc in pairs:
                out.write(f"  · {lab:<14}  {desc}\n")
print(f"  → 입력필드 추출")

# === 3) 코드 값 정의 (단일 영문/숫자 → 한글) ===
# A02 매뉴얼에서 본 예: "A: 문서대장", "01: 일반문서"
code_defs = defaultdict(Counter)   # group_key → Counter({"코드": "값"})
for f in files:
    text = f.read_text(encoding="utf-8")
    # 영문 단일 코드 (A:xx)
    for m in re.finditer(r"([A-Z])\s*[:=]\s*([가-힣][가-힣A-Za-z0-9·\s/]{1,15})", text):
        code, val = m.group(1), m.group(2).strip()
        if val and len(val) <= 15:
            code_defs[f"단일영문_in_{f.name}"][(code, val)] += 1
    # 숫자 코드 (01:xx, 1:xx 등)
    for m in re.finditer(r"(\d{1,2})\s*[:=]\s*([가-힣][가-힣A-Za-z0-9·\s/]{1,15})", text):
        code, val = m.group(1), m.group(2).strip()
        if val and 2 <= len(val) <= 15:
            code_defs[f"숫자_in_{f.name}"][(code, val)] += 1

with open(OUT_DIR / "_코드정의_후보.txt", "w", encoding="utf-8") as out:
    for group, counter in sorted(code_defs.items()):
        if len(counter) < 3: continue  # 너무 적은 노이즈 제외
        out.write(f"\n{'='*60}\n{group}\n{'='*60}\n")
        for (code, val), cnt in counter.most_common(50):
            out.write(f"  {code:>4} = {val:<25} ({cnt}회)\n")
print(f"  → 코드 정의 후보 추출")

# === 4) 기록물 유형 키워드 빈도 (도메인 어휘 발견용) ===
type_keywords = ["일반문서", "역사기록물", "총독부", "시청각", "정부간행물", "행정박물",
                 "구술", "해외기록물", "민간기록물", "일반도서", "이중보존매체",
                 "비전자", "전자기록물", "기록물철", "기록물건", "BSID", "DSID",
                 "단위과제", "기능분류", "보존기간", "공개구분", "이관", "인수",
                 "RFID", "NEO", "PDF/A", "장기보존포맷", "보존매체", "M/F",
                 "광디스크", "DVD", "마이크로필름", "스캐닝", "디지털화",
                 "분류체계", "ISAD", "ISO", "전거"]
freq_per_manual = defaultdict(Counter)
for f in files:
    text = f.read_text(encoding="utf-8")
    for kw in type_keywords:
        cnt = text.count(kw)
        if cnt > 0:
            freq_per_manual[f.name][kw] = cnt

with open(OUT_DIR / "_도메인키워드_빈도.tsv", "w", encoding="utf-8") as out:
    out.write("매뉴얼\t" + "\t".join(type_keywords) + "\n")
    for fn, c in freq_per_manual.items():
        row = [fn] + [str(c.get(kw, 0)) for kw in type_keywords]
        out.write("\t".join(row) + "\n")
print(f"  → 도메인 키워드 빈도")

print(f"\nSAVED to {OUT_DIR}/")
