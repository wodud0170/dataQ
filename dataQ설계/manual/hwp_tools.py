# -*- coding: utf-8 -*-
"""한컴오피스 COM 자동화 헬퍼 — 원본 서식을 보존한 채 내용만 바꾼다.

HWP 는 pip 라이브러리로 쓸 수 있는 게 없다. 설치된 한컴오피스를 COM 으로 몰아서
원본 파일을 열고 -> 텍스트를 치환하고 -> 다른 이름으로 저장한다.
이러면 표·서식·머리말·도장란이 원본 그대로 남는다.

필요: 한컴오피스 설치 + pip install pywin32 pillow

주의 — 두 번 데인 곳:
  1. GetTextFile("TEXT") 는 한글이 깨져서 돌아온다. 반드시 "UNICODE" 를 쓸 것.
     "TEXT" 로 받으면 한글 검색이 전부 0건이 되어 치환이 조용히 건너뛰어진다.
  2. InsertPicture 의 width/height 는 HWPUNIT 이 아니라 mm 다.
     HWPUNIT 을 그대로 넘기면 283배 크기로 들어가 그림이 다 깨진다.
     HWPUNIT -> mm 는 HWPUNIT_PER_MM 으로 나눈다.
"""
import win32com.client as win32

HWPUNIT_PER_MM = 7200.0 / 25.4  # 1 inch = 7200 HWPUNIT = 25.4 mm


def open_hwp(path):
    hwp = win32.gencache.EnsureDispatch("HWPFrame.HwpObject")
    try:
        hwp.RegisterModule("FilePathCheckDLL", "FilePathCheckerModule")
    except Exception:
        pass  # 모듈이 없어도 대개 그냥 열린다
    hwp.XHwpWindows.Item(0).Visible = False
    if not hwp.Open(path, "HWP", "forceopen:true"):
        raise RuntimeError("열기 실패: " + path)
    return hwp


def text_of(hwp):
    """본문 전체 텍스트. TEXT 가 아니라 UNICODE 여야 한글이 안 깨진다."""
    return hwp.GetTextFile("UNICODE", "")


def replace_all(hwp, find, repl):
    """전체 치환. 치환된 건수를 돌려준다 (0 이면 문자열을 못 찾은 것)."""
    before = text_of(hwp).count(find)
    if before == 0:
        return 0
    hwp.MovePos(2)  # 문서 처음으로
    hwp.HAction.GetDefault("AllReplace", hwp.HParameterSet.HFindReplace.HSet)
    o = hwp.HParameterSet.HFindReplace
    o.FindString = find
    o.ReplaceString = repl
    o.IgnoreMessage = 1
    o.ReplaceMode = 1
    o.Direction = 0
    o.MatchCase = 0
    o.AllWordForms = 0
    o.SeveralWords = 0
    o.UseWildCards = 0
    o.WholeWordOnly = 0
    o.AutoSpell = 0
    hwp.HAction.Execute("AllReplace", hwp.HParameterSet.HFindReplace.HSet)
    return before - text_of(hwp).count(find)


def find_text(hwp, needle):
    """needle 위치로 캐럿을 옮긴다. 찾으면 True."""
    hwp.MovePos(2)
    hwp.HAction.GetDefault("RepeatFind", hwp.HParameterSet.HFindReplace.HSet)
    o = hwp.HParameterSet.HFindReplace
    o.FindString = needle
    o.IgnoreMessage = 1
    o.Direction = 0
    o.MatchCase = 0
    o.WholeWordOnly = 0
    o.UseWildCards = 0
    return bool(hwp.HAction.Execute("RepeatFind", hwp.HParameterSet.HFindReplace.HSet))


def pictures(hwp):
    """문서에 든 그림(gso) 컨트롤을 등장 순서로."""
    out, c = [], hwp.HeadCtrl
    while c:
        if c.CtrlID == "gso":
            out.append(c)
        c = c.Next
    return out


def swap_picture(hwp, index, path, pil_image):
    """index 번째 그림을 path 로 교체. 원본의 표시 너비를 그대로 쓰고 높이는 비율로.

    돌려주는 값은 (목표 너비, 실제 너비) — 둘이 다르면 크기 지정이 안 먹은 것이다.
    """
    g = pictures(hwp)[index]
    w_hu = g.Properties.Item("Width")  # 원본 표시 너비 (HWPUNIT)
    h_hu = int(w_hu * pil_image.size[1] / pil_image.size[0])

    hwp.SetPosBySet(g.GetAnchorPos(0))
    hwp.FindCtrl()
    hwp.HAction.Run("Delete")
    # sizeoption=1 (지정 크기), width/height 는 mm
    hwp.InsertPicture(path, True, 1, False, False, 0,
                      w_hu / HWPUNIT_PER_MM, h_hu / HWPUNIT_PER_MM)
    return w_hu, pictures(hwp)[index].Properties.Item("Width")
