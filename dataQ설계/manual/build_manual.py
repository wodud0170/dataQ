# -*- coding: utf-8 -*-
"""사용자 매뉴얼 렌더러.

본문은 manual_content.py 에 있다. 이 파일은 그걸 HTML 로 만든다.

  index.html     로컬·PDF 용 (워크플로는 ASCII, 오프라인에서 그대로 열림)
  artifact.html  웹 공유용 (워크플로는 mermaid)

스크린샷은 base64 로 내장해 단일 파일이 된다.

실행: python build_manual.py
PDF : msedge --headless --disable-gpu --print-to-pdf="..." "file:///.../index.html"
"""
import base64
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manual_content import PARTS, SCREEN_INDEX, ERROR_DICT, KNOWN_LIMITS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")

ROLE_LABEL = {"admin": "관리자", "user": "일반 사용자", "both": "모두"}


def img(name):
    if not name:
        return None
    p = os.path.join(ASSETS, name + ".png")
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


CSS = """
:root{
  --ground:#FAFAFC; --surface:#FFFFFF; --surface-2:#F2F4FA;
  --ink:#1A1D28; --ink-2:#5A6178; --line:#DFE2EC;
  --accent:#3D4DB7; --accent-soft:#ECEFFB;
  --ok:#186B45;  --ok-bg:#E7F4ED;  --ok-line:#9FD2BA;
  --warn:#8A4B00; --warn-bg:#FDF1E2; --warn-line:#E6BE8B;
  --admin:#7A2E8E; --admin-bg:#F6EAF9; --admin-line:#D5AEDF;
  --note-bg:#FFFAEF; --note-line:#D7A31E;
  --shadow:0 1px 2px rgba(26,29,40,.05), 0 10px 26px -14px rgba(26,29,40,.16);
}
@media (prefers-color-scheme: dark){
  :root:not([data-theme="light"]){
    --ground:#0F1117; --surface:#171A22; --surface-2:#1E222C;
    --ink:#E6E8F0; --ink-2:#99A0B4; --line:#2A2F3C;
    --accent:#96A2F7; --accent-soft:#1D2340;
    --ok:#6FD3A2;  --ok-bg:#0F2E22;  --ok-line:#1F5C41;
    --warn:#EFB268; --warn-bg:#2E210E; --warn-line:#6A4A1C;
    --admin:#D9A6E8; --admin-bg:#2A1730; --admin-line:#5A3366;
    --note-bg:#231E11; --note-line:#7E621C;
    --shadow:0 1px 2px rgba(0,0,0,.4), 0 10px 26px -14px rgba(0,0,0,.6);
  }
}
:root[data-theme="dark"]{
  --ground:#0F1117; --surface:#171A22; --surface-2:#1E222C;
  --ink:#E6E8F0; --ink-2:#99A0B4; --line:#2A2F3C;
  --accent:#96A2F7; --accent-soft:#1D2340;
  --ok:#6FD3A2;  --ok-bg:#0F2E22;  --ok-line:#1F5C41;
  --warn:#EFB268; --warn-bg:#2E210E; --warn-line:#6A4A1C;
  --admin:#D9A6E8; --admin-bg:#2A1730; --admin-line:#5A3366;
  --note-bg:#231E11; --note-line:#7E621C;
  --shadow:0 1px 2px rgba(0,0,0,.4), 0 10px 26px -14px rgba(0,0,0,.6);
}

*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:"IBM Plex Sans KR","Malgun Gothic",system-ui,-apple-system,sans-serif;
  font-size:16px;line-height:1.75;-webkit-text-size-adjust:100%}
code,.mono{font-family:"IBM Plex Mono","D2Coding",ui-monospace,Consolas,monospace}
code{background:var(--surface-2);border:1px solid var(--line);
  padding:.5px 5px;border-radius:5px;font-size:.86em}
a{color:var(--accent);text-underline-offset:3px}
a:focus-visible{outline:2px solid var(--accent);outline-offset:3px;border-radius:4px}
b,strong{font-weight:600}
p{margin:10px 0}
ul{padding-left:19px;margin:8px 0}
li{margin:4px 0}

.wrap{max-width:920px;margin:0 auto;padding:0 18px 90px}
@media(min-width:760px){ body{font-size:15.5px} .wrap{padding:0 30px 120px} }

.cover{padding:52px 0 30px;border-bottom:1px solid var(--line)}
.eyebrow{font-family:"IBM Plex Mono",monospace;font-size:11px;font-weight:500;
  letter-spacing:.15em;text-transform:uppercase;color:var(--accent)}
.cover h1{font-size:clamp(29px,7.2vw,44px);line-height:1.16;letter-spacing:-.028em;
  font-weight:600;margin:12px 0 12px;text-wrap:balance}
.lede{color:var(--ink-2);font-size:clamp(14.5px,3.6vw,17px);margin:0 0 24px;max-width:36em}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(128px,1fr));gap:10px}
.stat{background:var(--surface);border:1px solid var(--line);border-radius:12px;padding:13px 15px}
.stat .n{font-family:"IBM Plex Mono",monospace;font-size:25px;font-weight:600;
  font-variant-numeric:tabular-nums;line-height:1.1;letter-spacing:-.02em;color:var(--accent)}
.stat .l{font-size:12px;color:var(--ink-2);margin-top:4px;line-height:1.45}

/* 파트 */
.part{margin:60px 0 0}
.part > h2{font-size:clamp(21px,5vw,27px);font-weight:600;letter-spacing:-.022em;
  margin:0 0 6px;text-wrap:balance}
.part > .sub{color:var(--ink-2);margin:0 0 6px;max-width:40em}
.part-rule{height:3px;background:var(--accent);border-radius:2px;width:52px;margin:14px 0 4px}

/* 작업 카드 — 번호는 실제 순서를 뜻한다 (앞 작업이 뒤 작업의 사전 조건) */
.task{background:var(--surface);border:1px solid var(--line);border-radius:14px;
  padding:20px;margin:22px 0;box-shadow:var(--shadow)}
@media(min-width:760px){ .task{padding:26px 28px} }
.task > h3{font-size:clamp(17px,4vw,20px);font-weight:600;letter-spacing:-.018em;
  margin:0 0 4px;display:flex;gap:10px;align-items:baseline;flex-wrap:wrap;text-wrap:balance}
.tnum{font-family:"IBM Plex Mono",monospace;font-size:12px;font-weight:600;
  color:var(--accent);background:var(--accent-soft);border-radius:6px;
  padding:2px 7px;flex:none;letter-spacing:.02em}
.role{font-family:"IBM Plex Mono",monospace;font-size:10px;font-weight:500;
  letter-spacing:.08em;padding:3px 8px;border-radius:999px;white-space:nowrap;
  color:var(--ok);background:var(--ok-bg);border:1px solid var(--ok-line)}
.role.admin{color:var(--admin);background:var(--admin-bg);border-color:var(--admin-line)}
.role.user{color:var(--warn);background:var(--warn-bg);border-color:var(--warn-line)}
.goal{color:var(--ink-2);margin:2px 0 14px}

.block{margin:14px 0}
.block > .h{font-family:"IBM Plex Mono",monospace;font-size:10px;font-weight:600;
  letter-spacing:.11em;text-transform:uppercase;color:var(--ink-2);margin-bottom:6px}
ol.steps{counter-reset:s;list-style:none;padding:0;margin:0}
ol.steps > li{counter-increment:s;position:relative;padding-left:30px;margin:9px 0}
ol.steps > li::before{content:counter(s);position:absolute;left:0;top:.15em;
  font-family:"IBM Plex Mono",monospace;font-size:11px;font-weight:600;
  width:20px;height:20px;line-height:20px;text-align:center;border-radius:50%;
  background:var(--accent-soft);color:var(--accent)}
.rule{background:var(--surface-2);border-bottom:1px dashed var(--ink-2);padding:0 2px}

.verify{background:var(--ok-bg);border:1px solid var(--ok-line);border-radius:10px;
  padding:11px 14px;font-size:.94em}
.verify .h{color:var(--ok)}

.traps{border-top:1px solid var(--line);margin-top:16px;padding-top:12px}
.trap{margin:12px 0}
.trap .sym{font-weight:600}
.trap .why{color:var(--ink-2);font-size:.94em;margin:2px 0}
.trap .fix{font-size:.94em}
.trap .fix::before{content:"→ ";color:var(--accent);font-weight:600}

.note{background:var(--note-bg);border:1px solid var(--note-line);
  border-radius:10px;padding:11px 14px;font-size:.94em;margin:12px 0}

figure{margin:14px 0 0;border:1px solid var(--line);border-radius:10px;
  overflow:hidden;background:var(--surface-2)}
figure img{display:block;width:100%;height:auto}

.tw{overflow-x:auto;-webkit-overflow-scrolling:touch;margin:12px 0;
  border:1px solid var(--line);border-radius:10px;background:var(--surface)}
table{width:100%;min-width:460px;border-collapse:collapse;font-size:.92em}
th,td{padding:10px 13px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}
tbody tr:last-child td{border-bottom:0}
th{background:var(--surface-2);font-weight:600;font-size:12.5px}

h2.app{font-size:clamp(20px,4.6vw,25px);font-weight:600;letter-spacing:-.02em;
  margin:60px 0 10px;padding-bottom:9px;border-bottom:1px solid var(--line)}

.toc{background:var(--surface);border:1px solid var(--line);border-radius:14px;
  padding:16px 18px;margin:24px 0}
.toc .pt{font-family:"IBM Plex Mono",monospace;font-size:10px;font-weight:600;
  letter-spacing:.1em;text-transform:uppercase;color:var(--accent);
  margin:14px 0 4px}
.toc .pt:first-child{margin-top:0}
.toc ol{list-style:none;margin:0;padding:0}
.toc a{display:flex;gap:10px;align-items:baseline;padding:5px 0;
  color:var(--ink);text-decoration:none;font-size:14.5px}
.toc a:hover{color:var(--accent)}
.toc .n{font-family:"IBM Plex Mono",monospace;font-size:11px;color:var(--ink-2);
  flex:none;width:22px}

.flow{background:var(--surface);border:1px solid var(--line);border-radius:12px;
  padding:15px;margin:14px 0;overflow-x:auto;-webkit-overflow-scrolling:touch;
  font-family:"IBM Plex Mono",monospace;font-size:12px;white-space:pre;line-height:1.85}
pre.mermaid{background:var(--surface);border:1px solid var(--line);border-radius:12px;
  padding:14px;margin:14px 0;overflow-x:auto;-webkit-overflow-scrolling:touch}

footer{margin-top:70px;padding-top:20px;border-top:1px solid var(--line);
  color:var(--ink-2);font-size:12.5px;line-height:1.7}

@media(prefers-reduced-motion:reduce){ *{animation:none!important;transition:none!important} }

@media print{
  *{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  body{background:#fff;font-size:10.5px;line-height:1.6}
  .wrap{max-width:none;padding:0 9mm}
  .cover{padding-top:20px;page-break-after:always}
  .part > h2{page-break-after:avoid}
  .task{page-break-inside:avoid;box-shadow:none;margin:16px 0;padding:16px}
  figure,.note,.tw,.verify,.stat{page-break-inside:avoid}
  h2.app{page-break-before:always}
  a{color:inherit;text-decoration:none}
}
"""

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
         'family=IBM+Plex+Mono:wght@400;500;600&'
         'family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">')

FLOW_STD_ASCII = (
    "일반 사용자                     관리자\n"
    "     |\n"
    "  [등록 신청] -------------->  승인 대기\n"
    "     |                            |\n"
    "     |                       +----+----+\n"
    "     |                    [승인]     [반려]\n"
    "     |                       |          |\n"
    "     |                 사전에 반영   행 삭제 + 사유\n"
    "     |                 이력 기록        |\n"
    "     |                             이 단어를 쓰는\n"
    "     <---------------------      미승인 용어도 삭제\n"
    "  같은 이름으로 재등록 가능     (승인된 용어는 보존)")

FLOW_STD_MMD = """flowchart TD
  A["일반 사용자<br>등록 신청"] --> B["승인 대기"]
  B --> C{"관리자 검토"}
  C -->|승인| D["사전에 반영<br>변경 이력 기록"]
  C -->|반려| E["행 삭제 + 사유"]
  E --> F["이 단어를 쓰는 미승인 용어도 삭제<br>(승인된 용어는 보존)"]
  E --> G["같은 이름으로 재등록 가능"]"""

FLOW_GOV_ASCII = (
    "일반 사용자                          관리자\n"
    "     |\n"
    "  컬럼 추가 --> 초안(DRAFT)\n"
    "                   |  * 나에게만 보임\n"
    "            [묶어서 신청]\n"
    "                   |\n"
    "              신청(SUBMITTED) --->  변경 승인 화면\n"
    "                                        |\n"
    "                                   +----+----+\n"
    "                                [승인]     [반려]\n"
    "                                   |          |\n"
    "                            전원에게 반영   변경 전 값으로\n"
    "                            DDL 조각 생성      되돌림\n"
    "                                   |\n"
    "                          [복사] 또는 [DB 반영]")

FLOW_GOV_MMD = """flowchart TD
  A["일반 사용자<br>컬럼 추가"] --> B["초안 DRAFT<br>나에게만 보임"]
  B --> C["묶어서 신청"]
  C --> D["신청 SUBMITTED"]
  D --> E{"관리자 검토"}
  E -->|승인| F["전원에게 반영<br>DDL 조각 생성"]
  E -->|반려| G["변경 전 값으로 되돌림<br>참조하던 신청도 함께 반려"]
  F --> H["복사 또는 DB 반영"]"""

FLOW_LOOP_ASCII = (
    "   +---------------------------------------------+\n"
    "   |                                             |\n"
    "   v                                             |\n"
    " 수집  -->  표준 진단  -->  진단 결과            |\n"
    "                               |                 |\n"
    "                +--------------+--------------+  |\n"
    "                |              |              |  |\n"
    "         용어 미존재     한글명 불일치   타입/길이 불일치\n"
    "                |              |              |  |\n"
    "          용어 등록      일괄 표준화      DDL 생성 --+\n"
    "           (작업 14)      (작업 15)       (작업 16)")

FLOW_LOOP_MMD = """flowchart LR
  A["수집"] --> B["표준 진단"]
  B --> C["진단 결과"]
  C --> D["용어 미존재<br>→ 용어 등록"]
  C --> E["한글명 불일치<br>→ 일괄 표준화"]
  C --> F["타입·길이 불일치<br>→ DDL 생성·반영"]
  D --> B
  E --> B
  F --> A"""


def flow(ascii_art, mmd, artifact):
    if artifact:
        return '<pre class="mermaid">%s</pre>' % mmd
    return '<div class="flow">%s</div>' % ascii_art


def render_table(spec):
    head, rows = spec
    out = ['<div class="tw"><table><thead><tr>']
    out += ["<th>%s</th>" % h for h in head]
    out.append("</tr></thead><tbody>")
    for r in rows:
        out.append("<tr>" + "".join("<td>%s</td>" % c for c in r) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def build(artifact=False):
    tasks = [t for _, _, ts in PARTS for t in ts]
    p = []
    if not artifact:
        p.append('<meta charset="utf-8">')
        p.append('<meta name="viewport" content="width=device-width,initial-scale=1">')
    p.append("<title>Narae DataQ 사용자 매뉴얼</title>")
    p.append(FONTS)
    p.append("<style>%s</style>" % CSS)
    p.append('<div class="wrap">')

    # 표지
    p.append('<header class="cover">')
    p.append('<div class="eyebrow">User Manual &middot; v4.0</div>')
    p.append("<h1>Narae DataQ 사용자 매뉴얼</h1>")
    p.append('<p class="lede">데이터 표준 관리 · 모델 수집 · 표준 진단 플랫폼. '
             '화면 설명이 아니라 <b>하려는 일</b> 순서로 정리했습니다. '
             '각 작업은 목적 · 사전 조건 · 단계 · 확인 방법 · 자주 겪는 문제로 되어 있습니다.</p>')
    p.append('<div class="stats">')
    p.append('<div class="stat"><div class="n">%d</div><div class="l">작업</div></div>' % len(tasks))
    p.append('<div class="stat"><div class="n">%d</div><div class="l">부</div></div>' % len(PARTS))
    p.append('<div class="stat"><div class="n">%d</div><div class="l">자주 겪는 문제</div></div>'
             % sum(len(t.get("traps") or []) for t in tasks))
    p.append('<div class="stat"><div class="n">%d</div><div class="l">오류 메시지</div></div>'
             % len(ERROR_DICT))
    p.append("</div></header>")

    # 목차
    p.append('<nav class="toc">')
    n = 0
    for title, _sub, ts in PARTS:
        p.append('<div class="pt">%s</div><ol>' % title)
        for t in ts:
            n += 1
            p.append('<li><a href="#%s"><span class="n">%02d</span><span>%s</span></a></li>'
                     % (t["id"], n, t["title"]))
        p.append("</ol>")
    p.append('<div class="pt">부록</div><ol>')
    p.append('<li><a href="#flows"><span class="n">A</span><span>업무 흐름 한눈에 보기</span></a></li>')
    p.append('<li><a href="#screens"><span class="n">B</span><span>화면 찾아보기</span></a></li>')
    p.append('<li><a href="#errors"><span class="n">C</span><span>오류 메시지 사전</span></a></li>')
    p.append('<li><a href="#limits"><span class="n">D</span><span>알려진 제약</span></a></li>')
    p.append("</ol></nav>")

    # 본문
    n = 0
    missing = []
    for title, sub, ts in PARTS:
        p.append('<section class="part">')
        p.append('<h2>%s</h2><div class="part-rule"></div><p class="sub">%s</p>' % (title, sub))
        for t in ts:
            n += 1
            p.append('<article class="task" id="%s">' % t["id"])
            p.append('<h3><span class="tnum">%02d</span><span>%s</span>'
                     '<span class="role %s">%s</span></h3>'
                     % (n, t["title"], t["role"], ROLE_LABEL[t["role"]]))
            p.append('<p class="goal">%s</p>' % t["goal"])

            if t.get("pre"):
                p.append('<div class="block"><div class="h">먼저 필요한 것</div><ul>')
                p += ["<li>%s</li>" % x for x in t["pre"]]
                p.append("</ul></div>")

            if t.get("steps"):
                p.append('<div class="block"><div class="h">하는 법</div><ol class="steps">')
                p += ["<li>%s</li>" % s for s in t["steps"]]
                p.append("</ol></div>")

            if t.get("note"):
                p.append('<div class="note">%s</div>' % t["note"])
            if t.get("table"):
                p.append(render_table(t["table"]))

            d = img(t.get("shot"))
            if d:
                p.append('<figure><img src="%s" alt="%s" loading="lazy"></figure>'
                         % (d, t["title"]))
            elif t.get("shot"):
                missing.append(t["shot"])

            if t.get("verify"):
                p.append('<div class="verify block"><div class="h">이렇게 되면 성공</div>%s</div>'
                         % t["verify"])

            if t.get("traps"):
                p.append('<div class="traps"><div class="h" '
                         'style="font-family:\'IBM Plex Mono\',monospace;font-size:10px;'
                         'font-weight:600;letter-spacing:.11em;text-transform:uppercase;'
                         'color:var(--ink-2);margin-bottom:8px">자주 겪는 문제</div>')
                for sym, why, fix in t["traps"]:
                    p.append('<div class="trap"><div class="sym">%s</div>' % sym)
                    if why:
                        p.append('<div class="why">%s</div>' % why)
                    if fix:
                        p.append('<div class="fix">%s</div>' % fix)
                    p.append("</div>")
                p.append("</div>")
            p.append("</article>")
        p.append("</section>")

    # 부록 A — 흐름
    p.append('<h2 class="app" id="flows">부록 A. 업무 흐름 한눈에 보기</h2>')
    p.append("<h3>표준 준수율 개선 루프</h3>")
    p.append("<p>이 제품의 중심 흐름입니다. 진단 결과의 이슈 유형마다 조치가 다르고, "
             "조치 후에는 반드시 다시 진단해야 반영됩니다.</p>")
    p.append(flow(FLOW_LOOP_ASCII, FLOW_LOOP_MMD, artifact))
    p.append("<h3>표준 사전 승인</h3>")
    p.append(flow(FLOW_STD_ASCII, FLOW_STD_MMD, artifact))
    p.append("<h3>데이터 모델 변경 승인</h3>")
    p.append(flow(FLOW_GOV_ASCII, FLOW_GOV_MMD, artifact))
    p.append("<p>관리자가 직접 바꾸면 초안·신청 단계를 건너뛰고 곧바로 반영됩니다.</p>")

    # 부록 B — 화면 찾아보기
    p.append('<h2 class="app" id="screens">부록 B. 화면 찾아보기</h2>')
    p.append("<p>화면 이름을 알 때 어디로 가는지 찾는 표입니다.</p>")
    p.append('<div class="tw"><table><thead><tr><th>화면</th><th>메뉴 경로</th>'
             "</tr></thead><tbody>")
    for nm, path, _ in SCREEN_INDEX:
        p.append("<tr><td><b>%s</b></td><td>%s</td></tr>" % (nm, path))
    p.append("</tbody></table></div>")

    # 부록 C — 오류 사전
    p.append('<h2 class="app" id="errors">부록 C. 오류 메시지 사전</h2>')
    p.append("<p>화면에 뜨는 메시지를 그대로 찾아보세요.</p>")
    p.append('<div class="tw"><table><thead><tr><th>메시지</th><th>뜻</th><th>할 일</th>'
             "</tr></thead><tbody>")
    for msg, mean, act in ERROR_DICT:
        p.append("<tr><td><b>%s</b></td><td>%s</td><td>%s</td></tr>" % (msg, mean, act))
    p.append("</tbody></table></div>")

    # 부록 D — 제약
    p.append('<h2 class="app" id="limits">부록 D. 알려진 제약</h2>')
    p.append("<p>고칠 수 있는 결함이 아니라, 현재 제품이 의도적으로 하지 않거나 "
             "아직 지원하지 않는 것들입니다.</p>")
    p.append('<div class="tw"><table><thead><tr><th>항목</th><th>내용</th><th>영향 범위</th>'
             "</tr></thead><tbody>")
    for t, d, scope in KNOWN_LIMITS:
        p.append("<tr><td><b>%s</b></td><td>%s</td><td>%s</td></tr>" % (t, d, scope))
    p.append("</tbody></table></div>")

    p.append("<footer>Narae DataQ 사용자 매뉴얼 v4.0 · 2026-08-23<br>"
             "작업 %d개 · 스크린샷은 현재 빌드에서 촬영 (개발 환경 데이터라 건수·이름은 운영과 다릅니다)"
             "</footer>" % len(tasks))
    p.append("</div>")

    out = os.path.join(HERE, "artifact.html" if artifact else "index.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(p))
    print("생성: %-14s %.1fMB" % (os.path.basename(out), os.path.getsize(out) / 1048576))
    if missing:
        print("  스크린샷 없음: %s" % ", ".join(sorted(set(missing))))
    return len(tasks)


if __name__ == "__main__":
    cnt = build(artifact=False)
    build(artifact=True)
    print("작업 %d개 / %d부" % (cnt, len(PARTS)))
    sys.exit(0)
