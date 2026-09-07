# -*- coding: utf-8 -*-
"""build_docx.py 산출물의 목차를 Word 로 한 번 채워 넣고 저장한다.

build_docx.py 는 목차를 TOC '필드' 로만 넣는다. 필드는 결과가 비어 있어서
그대로 두면 목차가 안 보인다. w:updateFields 로 자동 갱신을 켜는 방법도 있지만,
그러면 Word 가 문서를 열 때마다 "다른 파일을 참조하는 필드가 있습니다" 를 묻는다.

그래서 여기서 Word 를 한 번만 띄워 목차를 계산해 문서에 박아 넣는다.
이후로는 열어도 아무것도 묻지 않고 목차가 그대로 보인다.

실행: python finalize_toc.py       (build_docx.py 를 먼저 실행할 것)
"""
import os
import sys

import win32com.client as win32

from build_docx import OUT  # noqa: E402

WD_FORMAT_DOCX = 16


def main(path=OUT):
    path = os.path.abspath(path)
    if not os.path.exists(path):
        sys.exit("문서가 없다. build_docx.py 를 먼저 실행할 것: " + path)

    word = win32.gencache.EnsureDispatch("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0
    doc = word.Documents.Open(path)
    try:
        n = doc.TablesOfContents.Count
        for i in range(n):
            doc.TablesOfContents(i + 1).Update()
        doc.Repaginate()
        pages = doc.ComputeStatistics(2)  # wdStatisticPages
        doc.SaveAs(path, FileFormat=WD_FORMAT_DOCX)
        print("목차 %d개 갱신 · %d 페이지" % (n, pages))
    finally:
        doc.Close(SaveChanges=0)
        word.Quit()
    print("저장:", path)


if __name__ == "__main__":
    main(*sys.argv[1:])
