"""
Convert all .md files in this folder to PDF using:
  1) python-markdown (md -> html)
  2) Microsoft Edge headless (html -> pdf)
"""
import os
import sys
import glob
import subprocess
import markdown
import tempfile
import time
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

CSS = """
@page { size: A4; margin: 18mm 16mm; }
html { -webkit-print-color-adjust: exact; }
body {
  font-family: "Malgun Gothic", "맑은 고딕", "Noto Sans KR", sans-serif;
  font-size: 10.5pt;
  line-height: 1.65;
  color: #1f2328;
  max-width: 100%;
}
h1 { font-size: 20pt; border-bottom: 2px solid #1f2328; padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 15pt; margin-top: 1.6em; border-bottom: 1px solid #d0d7de; padding-bottom: 4px; }
h3 { font-size: 12.5pt; margin-top: 1.3em; }
h4 { font-size: 11pt; margin-top: 1.1em; }
p  { margin: 0.6em 0; }
ul, ol { margin: 0.4em 0 0.8em 0; padding-left: 1.4em; }
li { margin: 0.15em 0; }
code {
  font-family: "Consolas", "D2Coding", "Courier New", monospace;
  background: #f4f4f4;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 9.5pt;
}
pre {
  background: #f6f8fa;
  border: 1px solid #e0e4e8;
  border-radius: 4px;
  padding: 10px 12px;
  overflow-x: auto;
  font-size: 9pt;
  line-height: 1.45;
  page-break-inside: avoid;
}
pre code { background: transparent; padding: 0; font-size: 9pt; }
table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.8em 0;
  font-size: 9.5pt;
  page-break-inside: avoid;
}
th, td { border: 1px solid #d0d7de; padding: 6px 9px; text-align: left; vertical-align: top; }
th { background: #f4f6f8; font-weight: 600; }
blockquote {
  border-left: 3px solid #c8ccd1;
  margin: 0.8em 0;
  padding: 0.2em 0.9em;
  color: #57606a;
}
hr { border: none; border-top: 1px solid #d0d7de; margin: 1.4em 0; }
a { color: #0969da; text-decoration: none; }
h1, h2, h3 { page-break-after: avoid; }
"""

HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>{title}</title>
<style>{css}</style>
</head>
<body>
{body}
</body>
</html>
"""


def md_to_html(md_path):
    with open(md_path, "r", encoding="utf-8") as f:
        md_text = f.read()
    body = markdown.markdown(
        md_text,
        extensions=["tables", "fenced_code", "toc", "sane_lists"],
    )
    title = os.path.splitext(os.path.basename(md_path))[0]
    return HTML_TEMPLATE.format(title=title, css=CSS, body=body)


def html_to_pdf(html_path, pdf_path, profile_dir):
    # URL-encode path segments (Korean chars need percent-encoding for file://)
    posix_path = html_path.replace("\\", "/")
    # Split drive from rest
    if len(posix_path) >= 2 and posix_path[1] == ":":
        drive = posix_path[:2]
        rest = posix_path[2:]
    else:
        drive = ""
        rest = posix_path
    encoded_rest = "/".join(urllib.parse.quote(seg) for seg in rest.split("/"))
    url = "file:///" + drive + encoded_rest
    cmd = [
        EDGE,
        "--headless",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--user-data-dir={profile_dir}",
        f"--print-to-pdf={pdf_path}",
        url,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0 or not os.path.exists(pdf_path):
        print("STDERR:", result.stderr[:500], file=sys.stderr)
        print("STDOUT:", result.stdout[:500], file=sys.stderr)
        return False
    return True


def main():
    md_files = sorted(
        [p for p in glob.glob(os.path.join(HERE, "*.md"))]
    )
    print(f"Found {len(md_files)} markdown files")
    ok = 0
    fail = 0
    for idx, md_path in enumerate(md_files):
        base = os.path.splitext(os.path.basename(md_path))[0]
        # fully isolated temp dir per file (separate mkdtemp)
        per_file_tmp = tempfile.mkdtemp(prefix=f"md2pdf_{idx}_")
        html_path = os.path.join(per_file_tmp, "doc.html")
        pdf_path = os.path.join(HERE, base + ".pdf")
        profile_dir = os.path.join(per_file_tmp, "p")
        html = md_to_html(md_path)
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html)
        print(f"-> {base}.pdf ... ", end="", flush=True)
        success = html_to_pdf(html_path, pdf_path, profile_dir)
        if success:
            print("OK")
            ok += 1
        else:
            print("FAIL")
            fail += 1
        time.sleep(2.0)
    print(f"\nDone: {ok} ok, {fail} fail")


if __name__ == "__main__":
    main()
