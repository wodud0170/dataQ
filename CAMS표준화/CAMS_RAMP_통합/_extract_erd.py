"""
CAMS ERD PDF 추출 — 폰트 크기·색·bbox 활용해 (업무영역 헤더) / (테이블명) / (컬럼명) 분리.
출력:
  _erd_areas.tsv       : 업무영역 헤더 후보 (큰 폰트 라벨)
  _erd_tables.tsv      : 테이블명 후보 (테이블 헤더 폰트)
  _erd_blocks.json     : 테이블별 컬럼 묶음 (bbox 클러스터)
"""
import fitz, json, sys, collections, re
from pathlib import Path

PDF = r"C:\CAMS 매뉴얼\CAMS ERD\191210_CAMS ERD_최종.pdf"
OUT = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\CAMS_RAMP_통합")

doc = fitz.open(PDF)
page = doc[0]
W, H = page.rect.width, page.rect.height
print(f"page size: {W:.0f} x {H:.0f}")

# rawdict 로 spans 추출 (font, size, color, bbox)
data = page.get_text("rawdict")

spans = []  # (text, x0,y0,x1,y1, size, font, color)
for block in data["blocks"]:
    if block["type"] != 0:  # text block만
        continue
    for line in block["lines"]:
        for sp in line["spans"]:
            text = "".join(ch["c"] for ch in sp["chars"]).strip()
            if not text:
                continue
            x0,y0,x1,y1 = sp["bbox"]
            spans.append({
                "text": text,
                "bbox": (x0,y0,x1,y1),
                "size": round(sp["size"], 1),
                "font": sp["font"],
                "color": sp["color"],
            })

print(f"total spans: {len(spans)}")

# 폰트 크기 분포
size_dist = collections.Counter(s["size"] for s in spans)
print("\n=== font size distribution (size: count) ===")
for sz, cnt in sorted(size_dist.items(), reverse=True):
    print(f"  {sz}: {cnt}")

# 색상 분포 (top 10)
color_dist = collections.Counter(s["color"] for s in spans)
print("\n=== color distribution (top 15) ===")
for c, cnt in color_dist.most_common(15):
    # color는 int (RGB packed). hex 변환
    print(f"  #{c:06x}: {cnt}")

# 폰트 분포
font_dist = collections.Counter(s["font"] for s in spans)
print("\n=== font distribution (top 10) ===")
for f, cnt in font_dist.most_common(10):
    print(f"  {f}: {cnt}")

# 가장 큰 폰트(=업무영역 헤더), 두번째 큰 폰트(=테이블명), 작은 폰트(=컬럼) 추정
sizes_sorted = sorted(size_dist.keys(), reverse=True)
print(f"\n=== sample large-font texts (top 3 sizes) ===")
for sz in sizes_sorted[:3]:
    samples = [s["text"] for s in spans if s["size"] == sz][:30]
    print(f"\n-- size {sz} ({size_dist[sz]} spans) --")
    for s in samples:
        print(f"  {s}")
