"""CAMS 사용자 매뉴얼 2장 (등록) PDF 텍스트 추출."""
import fitz
from pathlib import Path

PDF = r"C:\CAMS 매뉴얼\CAMS 사용자 매뉴얼\CAMS_manual_A02_등록_2018.v1.5.pdf"
OUT = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\05_CAMS기록물유형_2026-05-21\_manual_2장_등록.txt")

doc = fitz.open(PDF)
print(f"=== {Path(PDF).name} ===")
print(f"페이지 수: {doc.page_count}")

# 메타데이터
md = doc.metadata
print(f"제목: {md.get('title','')}")
print(f"저자: {md.get('author','')}")

# 전체 텍스트 추출
texts = []
for i in range(doc.page_count):
    page = doc[i]
    t = page.get_text("text")
    texts.append(f"=== PAGE {i+1} ===\n{t}")

content = "\n".join(texts)
OUT.write_text(content, encoding="utf-8")
print(f"\n저장: {OUT}")
print(f"크기: {len(content):,} 문자")

# 첫 페이지 미리보기
print("\n=== 첫 페이지 미리보기 ===")
print(texts[0][:1500])
