"""CAMS 모든 매뉴얼 PDF → 텍스트 일괄 추출. 큰 PDF는 분당 처리."""
import fitz
from pathlib import Path
import time

SRC = Path(r"C:\CAMS 매뉴얼")
DST = Path(r"C:\Users\장재영\Desktop\dataQ\CAMS표준화\05_CAMS기록물유형_2026-05-21\_manual_txt")
DST.mkdir(exist_ok=True)

pdfs = list(SRC.rglob("*.pdf"))
pdfs = [p for p in pdfs if "ERD" not in p.name]  # ERD는 별도 분석 완료
print(f"대상 PDF: {len(pdfs)}건")

summary = []
for i, pdf in enumerate(pdfs, 1):
    t0 = time.time()
    try:
        doc = fitz.open(pdf)
        npage = doc.page_count
        texts = []
        for j in range(npage):
            t = doc[j].get_text("text")
            if t.strip():
                texts.append(f"=== PAGE {j+1} ===\n{t}")
        content = "\n".join(texts)
        doc.close()

        # 파일명 — 상대경로 (괄호/공백 안전)
        rel = pdf.relative_to(SRC)
        out_name = str(rel).replace("\\", "_").replace(".pdf", ".txt")
        out_path = DST / out_name
        out_path.write_text(content, encoding="utf-8")

        elapsed = time.time() - t0
        size_kb = out_path.stat().st_size / 1024
        print(f"  [{i:2}/{len(pdfs)}] {pdf.name:<55} {npage:3}p  {size_kb:>6.0f}KB  {elapsed:.1f}s")
        summary.append({"file": pdf.name, "pages": npage, "txt_kb": size_kb, "out": out_name})
    except Exception as e:
        print(f"  [{i:2}/{len(pdfs)}] {pdf.name} — ERROR: {e}")
        summary.append({"file": pdf.name, "pages": -1, "txt_kb": 0, "out": str(e)})

print(f"\n=== 요약 ({len(summary)}건) ===")
total_p = sum(s["pages"] for s in summary if s["pages"] > 0)
total_kb = sum(s["txt_kb"] for s in summary)
print(f"총 {total_p:,} 페이지, 텍스트 {total_kb:.1f} KB → {DST}")
