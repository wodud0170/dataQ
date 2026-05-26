"""
CAMS ERD PDF — 폰트 크기별로 추출 + bbox 기반 클러스터로 테이블-컬럼 묶기.
"""
import fitz, json, collections
from pathlib import Path

PDF = r"C:\CAMS 매뉴얼\CAMS ERD\191210_CAMS ERD_최종.pdf"
OUT = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\CAMS_RAMP_통합")

doc = fitz.open(PDF)
page = doc[0]
data = page.get_text("rawdict")

spans = []
for block in data["blocks"]:
    if block["type"] != 0: continue
    for line in block["lines"]:
        for sp in line["spans"]:
            text = "".join(ch["c"] for ch in sp["chars"]).strip()
            if not text: continue
            x0,y0,x1,y1 = sp["bbox"]
            spans.append({"text":text,"x0":x0,"y0":y0,"x1":x1,"y1":y1,
                          "size":round(sp["size"],1),"font":sp["font"]})

# 1) 업무영역 (size 2.2 + 2.3)
areas = [s for s in spans if s["size"] in (2.2, 2.3)]
# 괄호 표기는 다음 span과 묶일 가능성 — 그냥 텍스트만 모음
area_texts = []
i = 0
while i < len(areas):
    t = areas[i]["text"]
    if t == "(" and i+2 < len(areas) and areas[i+2]["text"] == ")":
        # ( X ) 패턴
        area_texts.append({"text": "(" + areas[i+1]["text"] + ")", "size": areas[i+1]["size"],
                           "x0": areas[i]["x0"], "y0": areas[i]["y0"]})
        i += 3
    elif t in ("(", ")"):
        i += 1
    else:
        area_texts.append({"text": t, "size": areas[i]["size"],
                           "x0": areas[i]["x0"], "y0": areas[i]["y0"]})
        i += 1

with open(OUT/"_erd_areas.tsv","w",encoding="utf-8") as f:
    f.write("size\tx\ty\ttext\n")
    for a in sorted(area_texts, key=lambda r:(r["y0"],r["x0"])):
        f.write(f"{a['size']}\t{a['x0']:.0f}\t{a['y0']:.0f}\t{a['text']}\n")
print(f"areas: {len(area_texts)} -> _erd_areas.tsv")

# 2) 테이블명 후보 (size 1.3 = Batang) + (size 1.2 = Gulim, 가능성 있음)
tbl_size = [1.3]
tables = [s for s in spans if s["size"] in tbl_size]
with open(OUT/"_erd_tables.tsv","w",encoding="utf-8") as f:
    f.write("size\tx\ty\ttext\n")
    for t in sorted(tables, key=lambda r:(r["y0"],r["x0"])):
        f.write(f"{t['size']}\t{t['x0']:.0f}\t{t['y0']:.0f}\t{t['text']}\n")
print(f"tables (size 1.3): {len(tables)} -> _erd_tables.tsv")

# 3) Gulim 1.2 도 따로
gulims = [s for s in spans if s["size"] == 1.2]
with open(OUT/"_erd_size12.tsv","w",encoding="utf-8") as f:
    f.write("size\tx\ty\tfont\ttext\n")
    for t in sorted(gulims, key=lambda r:(r["y0"],r["x0"])):
        f.write(f"{t['size']}\t{t['x0']:.0f}\t{t['y0']:.0f}\t{t['font']}\t{t['text']}\n")
print(f"size 1.2: {len(gulims)} -> _erd_size12.tsv")

# 4) 컬럼 (size 1.1 = Dotum 본문)
cols = [s for s in spans if s["size"] == 1.1]
with open(OUT/"_erd_columns.tsv","w",encoding="utf-8") as f:
    f.write("x\ty\ttext\n")
    for c in sorted(cols, key=lambda r:(r["y0"],r["x0"])):
        f.write(f"{c['x0']:.0f}\t{c['y0']:.0f}\t{c['text']}\n")
print(f"columns: {len(cols)} -> _erd_columns.tsv")
