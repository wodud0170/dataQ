# -*- coding: utf-8 -*-
"""Navid Meta 제품 소개 생성기.

스크린샷을 base64 로 내장해 단일 파일로 만든다.
  intro.html  — 로컬·PDF·아티팩트 공용

실행: python build_intro.py
"""
import base64
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")


def img(name):
    p = os.path.join(ASSETS, name + ".png")
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def shot(name, cap):
    d = img(name)
    if not d:
        return ""
    return ('<figure class="shot"><img src="%s" alt="%s" loading="lazy">'
            '<figcaption>%s</figcaption></figure>' % (d, cap, cap))


CSS = """
:root{
  --ground:#F5F6FB; --surface:#FFFFFF; --surface-2:#EDEFF7;
  --ink:#14172A; --ink-2:#5C6480; --line:#DCDFEC; --line-2:#C3C8DC;
  --accent:#3D4DB7; --accent-soft:#E7EAFA;
  --dev:#B4553A; --dev-soft:#F8EBE6;
  --ok:#1E7A56; --ok-soft:#E4F2EC;
  --shadow:0 1px 2px rgba(20,23,42,.05), 0 14px 34px -18px rgba(20,23,42,.20);
}
@media (prefers-color-scheme: dark){
  :root:not([data-theme="light"]){
    --ground:#0C0E17; --surface:#141726; --surface-2:#1B1F31;
    --ink:#E8EAF4; --ink-2:#9AA1BC; --line:#272C42; --line-2:#3A4059;
    --accent:#8E9AF5; --accent-soft:#1B2044;
    --dev:#E08967; --dev-soft:#301C15;
    --ok:#5FCC9E; --ok-soft:#0F2C21;
    --shadow:0 1px 2px rgba(0,0,0,.5), 0 14px 34px -18px rgba(0,0,0,.7);
  }
}
:root[data-theme="dark"]{
  --ground:#0C0E17; --surface:#141726; --surface-2:#1B1F31;
  --ink:#E8EAF4; --ink-2:#9AA1BC; --line:#272C42; --line-2:#3A4059;
  --accent:#8E9AF5; --accent-soft:#1B2044;
  --dev:#E08967; --dev-soft:#301C15;
  --ok:#5FCC9E; --ok-soft:#0F2C21;
  --shadow:0 1px 2px rgba(0,0,0,.5), 0 14px 34px -18px rgba(0,0,0,.7);
}

*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:"IBM Plex Sans KR","Malgun Gothic",system-ui,-apple-system,sans-serif;
  font-size:16px;line-height:1.8;-webkit-text-size-adjust:100%}
h1,h2,h3,h4{font-family:"Gothic A1","Malgun Gothic",system-ui,sans-serif;
  text-wrap:balance;margin:0}
.mono,code{font-family:"IBM Plex Mono","D2Coding",ui-monospace,Consolas,monospace}
code{background:var(--surface-2);border:1px solid var(--line);
  padding:1px 5px;border-radius:4px;font-size:.85em}
b,strong{font-weight:600}
p{margin:0}
a{color:var(--accent)}
a:focus-visible{outline:2px solid var(--accent);outline-offset:3px;border-radius:3px}

.wrap{max-width:900px;margin:0 auto;padding:0 20px 110px}
@media(min-width:760px){ .wrap{padding:0 32px 140px} }

section{padding:54px 0;border-top:1px solid var(--line)}
section:first-of-type{border-top:0}
@media(min-width:760px){ section{padding:74px 0} }
.eyebrow{font-size:11.5px;font-weight:600;letter-spacing:.06em;color:var(--accent);
  display:flex;align-items:center;gap:10px;margin-bottom:16px}
.eyebrow::after{content:"";flex:1;height:1px;background:var(--line)}
h2{font-size:clamp(23px,5.2vw,32px);font-weight:900;letter-spacing:-.035em;line-height:1.28}
h3{font-size:clamp(16px,3.4vw,18px);font-weight:700;letter-spacing:-.02em;line-height:1.45}
h4{font-size:14px;font-weight:700;letter-spacing:-.01em}
.lede{color:var(--ink-2);font-size:clamp(15px,3.4vw,17px);margin-top:14px;max-width:35em}
.body{margin-top:16px;max-width:35em;color:var(--ink-2);font-size:15px}
.body + .body{margin-top:12px}

/* 표지 */
.cover{padding:66px 0 52px;border-top:0}
.brand{display:flex;align-items:center;gap:11px;margin-bottom:32px}
.brand .mark{display:grid;grid-template-columns:1fr 1fr;gap:2.5px;width:26px;height:26px;flex:none}
.brand .mark i{display:block;border-radius:3px;background:var(--accent);opacity:.4}
.brand .mark i:nth-child(2){opacity:.62}
.brand .mark i:nth-child(3){opacity:.8}
.brand .mark i:nth-child(4){opacity:1}
.brand .name{font-family:"Gothic A1",sans-serif;font-weight:900;font-size:17px;letter-spacing:-.01em}
.brand .name span{font-weight:500;color:var(--ink-2);letter-spacing:.14em;font-size:11px;
  display:block;line-height:1;margin-bottom:2px}
.cover h1{font-size:clamp(30px,7.6vw,50px);font-weight:900;letter-spacing:-.045em;line-height:1.16}
.cover h1 em{font-style:normal;color:var(--dev)}
.cover .sub{color:var(--ink-2);font-size:clamp(15px,3.5vw,17.5px);margin-top:20px;
  max-width:31em;line-height:1.78}

/* 격차 그래픽 */
.gap-fig{margin-top:40px;background:var(--surface);border:1px solid var(--line);
  border-radius:14px;padding:24px 20px 18px;box-shadow:var(--shadow);overflow:hidden}
@media(min-width:760px){ .gap-fig{padding:30px 28px 22px} }
.gap-row{display:flex;flex-direction:column;gap:6px;margin-bottom:20px}
.gap-row:last-of-type{margin-bottom:0}
.gap-label{font-size:12px;font-weight:600;letter-spacing:.02em;color:var(--ink-2)}
.ruler{height:26px;position:relative;border-radius:4px;overflow:hidden;border:1px solid var(--line)}
.ruler i{position:absolute;inset:0;background:repeating-linear-gradient(90deg,
  var(--line-2) 0 1px, transparent 1px calc(100% / 24))}
.ruler .fill{position:absolute;top:0;bottom:0;left:0;border-radius:3px 0 0 3px}
.ruler.std .fill{width:100%;background:var(--accent);opacity:.16}
.ruler.real .fill{width:16.8%;background:var(--accent);opacity:.5}
.ruler.real .rest{position:absolute;top:0;bottom:0;left:16.8%;right:0;
  background:repeating-linear-gradient(135deg,var(--dev) 0 1.5px,transparent 1.5px 7px);opacity:.42}
.gap-note{display:flex;flex-wrap:wrap;align-items:baseline;gap:10px;margin-top:18px;
  padding-top:16px;border-top:1px dashed var(--line-2)}
.gap-note .n{font-family:"IBM Plex Mono",monospace;font-size:clamp(28px,6.6vw,40px);
  font-weight:600;letter-spacing:-.04em;color:var(--dev);
  font-variant-numeric:tabular-nums;line-height:1}
.gap-note .t{color:var(--ink-2);font-size:14px;line-height:1.6}

/* 수치 */
.figures{display:grid;grid-template-columns:repeat(auto-fit,minmax(146px,1fr));gap:1px;
  background:var(--line);border:1px solid var(--line);border-radius:12px;
  overflow:hidden;margin-top:28px}
.fig{background:var(--surface);padding:19px 17px}
.fig .n{font-family:"IBM Plex Mono",monospace;font-size:clamp(25px,5.6vw,32px);
  font-weight:600;letter-spacing:-.04em;line-height:1.05;
  font-variant-numeric:tabular-nums;color:var(--accent)}
.fig .n small{font-size:.46em;font-weight:500;letter-spacing:0;color:var(--ink-2);margin-left:3px}
.fig .l{font-size:12.5px;color:var(--ink-2);margin-top:6px;line-height:1.5}

/* 카드 */
.cards{display:grid;gap:13px;margin-top:26px}
@media(min-width:700px){ .cards.two{grid-template-columns:1fr 1fr} }
.card{background:var(--surface);border:1px solid var(--line);border-radius:13px;
  padding:21px 19px;box-shadow:var(--shadow)}
.card h3{margin-bottom:8px}
.card p{color:var(--ink-2);font-size:14.5px;line-height:1.72}
.card .tag{font-size:11.5px;font-weight:600;letter-spacing:.03em;
  font-variant-numeric:tabular-nums;color:var(--accent);display:block;margin-bottom:8px}

/* 스크린샷 */
figure.shot{margin:26px 0 0;border:1px solid var(--line);border-radius:13px;
  overflow:hidden;background:var(--surface-2);box-shadow:var(--shadow)}
figure.shot img{display:block;width:100%;height:auto}
figure.shot figcaption{font-size:12.5px;color:var(--ink-2);padding:11px 15px;
  background:var(--surface);border-top:1px solid var(--line);line-height:1.6}

/* 이슈 분류 */
.taxo{display:grid;gap:1px;background:var(--line);border:1px solid var(--line);
  border-radius:12px;overflow:hidden;margin-top:26px}
.tx{background:var(--surface);padding:17px 19px;display:grid;gap:5px}
@media(min-width:640px){ .tx{grid-template-columns:172px 1fr;gap:6px 22px;align-items:baseline} }
.tx .k{font-weight:600;font-size:15px;display:flex;align-items:center;gap:8px}
.tx .k::before{content:"";width:7px;height:7px;border-radius:2px;background:var(--dev);flex:none}
.tx .v{color:var(--ink-2);font-size:14.5px;line-height:1.7}
.tx .v em{font-style:normal;color:var(--ink);font-weight:500}

pre.mermaid{background:var(--surface);border:1px solid var(--line);border-radius:13px;
  padding:18px;margin-top:26px;overflow-x:auto;-webkit-overflow-scrolling:touch}
.flow{background:var(--surface);border:1px solid var(--line);border-radius:13px;
  padding:16px;margin-top:26px;overflow-x:auto;-webkit-overflow-scrolling:touch;
  font-family:"IBM Plex Mono",monospace;font-size:12px;white-space:pre;line-height:1.9}

/* 표 */
.tw{overflow-x:auto;-webkit-overflow-scrolling:touch;margin-top:24px;
  border:1px solid var(--line);border-radius:12px;background:var(--surface)}
table{width:100%;min-width:470px;border-collapse:collapse;font-size:14.5px}
th,td{padding:12px 15px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}
tbody tr:last-child td{border-bottom:0}
th{background:var(--surface-2);font-weight:600;font-size:12.5px;color:var(--ink-2)}
td .yes{color:var(--ok);font-weight:600}
td .no{color:var(--dev);font-weight:600}

/* 역할 */
.roles{display:grid;gap:13px;margin-top:26px}
@media(min-width:700px){ .roles{grid-template-columns:1fr 1fr} }
.role{border:1px solid var(--line);border-radius:13px;padding:21px 19px;background:var(--surface)}
.role .who{font-family:"Gothic A1",sans-serif;font-weight:900;font-size:19px;letter-spacing:-.025em}
.role .does{color:var(--ink-2);font-size:14.5px;margin-top:7px;line-height:1.7}
.role ul{margin:13px 0 0;padding-left:17px;font-size:14px;color:var(--ink-2);line-height:1.8}
.role li{margin:2px 0}

/* 단계 목록 */
ol.steps{counter-reset:s;list-style:none;padding:0;margin:24px 0 0;display:grid;gap:14px}
ol.steps > li{counter-increment:s;position:relative;padding-left:44px;min-height:32px}
ol.steps > li::before{content:counter(s);position:absolute;left:0;top:-1px;
  font-family:"IBM Plex Mono",monospace;font-size:12px;font-weight:600;
  width:30px;height:30px;line-height:29px;text-align:center;border-radius:9px;
  background:var(--accent-soft);color:var(--accent)}
ol.steps h4{margin-bottom:3px}
ol.steps p{color:var(--ink-2);font-size:14.5px;line-height:1.7}

/* 정직 상자 */
.scope{background:var(--dev-soft);border:1px solid var(--dev);border-radius:13px;
  padding:21px 19px;margin-top:26px}
.scope h3{color:var(--dev);margin-bottom:9px}
.scope p,.scope li{font-size:14.5px;line-height:1.72}
.scope ul{margin:9px 0 0;padding-left:17px}

footer{border-top:1px solid var(--line);padding-top:26px;margin-top:22px;
  color:var(--ink-2);font-size:12.5px;line-height:1.8}

@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}

@media print{
  *{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  body{background:#fff;font-size:11px;line-height:1.65}
  .wrap{max-width:none;padding:0 9mm}
  .cover{page-break-after:always;padding-top:24px}
  section{page-break-inside:auto;padding:26px 0}
  h2{page-break-after:avoid}
  .card,.tx,figure.shot,.scope,.tw,.gap-fig,.fig{page-break-inside:avoid}
  a{color:inherit;text-decoration:none}
}
"""

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
         'family=Gothic+A1:wght@500;700;900&'
         'family=IBM+Plex+Mono:wght@400;500;600&'
         'family=IBM+Plex+Sans+KR:wght@400;500;600&display=swap">')

LOOP_MMD = """flowchart LR
  A["데이터 소스<br>등록"] --> B["모델 수집"]
  B --> C["표준 진단"]
  C --> D["이슈별<br>조치 스크립트"]
  D --> E["변경 신청"]
  E --> F["관리자 승인"]
  F --> G["DDL 을 실제<br>DB 에 반영"]
  G --> B"""

LOOP_ASCII = (
    "  데이터 소스 등록\n"
    "        |\n"
    "        v\n"
    "  +-> 모델 수집 --> 표준 진단 --> 이슈별 조치 스크립트\n"
    "  |                                      |\n"
    "  |                                      v\n"
    "  |                                 변경 신청\n"
    "  |                                      |\n"
    "  |                                      v\n"
    "  |                                 관리자 승인\n"
    "  |                                      |\n"
    "  |                                      v\n"
    "  +---------------------------  DDL 을 실제 DB 에 반영")

ARCH_MMD = """flowchart TB
  U["사용자 브라우저"] --> C["q-center<br>화면 · API · 승인"]
  C --> M[("메타 DB<br>표준사전 · 모델 · 진단결과")]
  C -.작업 요청.-> X["q-executor<br>수집 · 진단 · 스케줄"]
  X --> M
  X -.JDBC 조회.-> T[("대상 운영 DB")]"""

ARCH_ASCII = (
    "  사용자 브라우저\n"
    "        |\n"
    "        v\n"
    "  q-center  (화면 · API · 승인)\n"
    "     |    \\\n"
    "     |     \\ 작업 요청\n"
    "     v      v\n"
    "  메타 DB   q-executor  (수집 · 진단 · 스케줄)\n"
    "     ^          |    \\\n"
    "     +----------+     \\ JDBC 조회\n"
    "                       v\n"
    "                  대상 운영 DB")


def flow(ascii_art, mmd, artifact):
    if artifact:
        return '<pre class="mermaid">%s</pre>' % mmd
    return '<div class="flow">%s</div>' % ascii_art


def build(artifact=False):
    p = []
    p.append('<meta charset="utf-8">')
    p.append('<meta name="viewport" content="width=device-width,initial-scale=1">')
    p.append("<title>Navid Meta 제품 소개</title>")
    p.append(FONTS)
    p.append("<style>%s</style>" % CSS)
    p.append('<div class="wrap">')

    # ── 표지
    p.append('<section class="cover">')
    p.append('<div class="brand"><span class="mark" aria-hidden="true">'
             '<i></i><i></i><i></i><i></i></span>'
             '<span class="name"><span>NAVID</span>Meta</span></div>')
    p.append('<h1>표준은 문서에 있고,<br>데이터는 <em>DB 에 있다</em>.</h1>')
    p.append('<p class="sub">두 개가 얼마나 다른지는 대개 아무도 모릅니다. '
             '확인하려면 컬럼을 하나씩 사전과 맞춰봐야 하는데, 수천 개면 하지 않게 됩니다.<br><br>'
             'Navid Meta 는 그 차이를 측정합니다. 그리고 줄이는 데까지 갑니다.</p>')
    p.append('<figure class="gap-fig">'
             '<div class="gap-row"><div class="gap-label">표준 사전이 정의한 것</div>'
             '<div class="ruler std"><i></i><span class="fill"></span></div></div>'
             '<div class="gap-row"><div class="gap-label">운영 DB 의 실제 컬럼</div>'
             '<div class="ruler real"><i></i><span class="fill"></span>'
             '<span class="rest"></span></div></div>'
             '<figcaption class="gap-note"><span class="n">83.2%</span>'
             '<span class="t">가 표준을 벗어나 있었습니다.<br>'
             '실제 진단 예시 — 컬럼 125개 중 104개에 이슈, 준수율 16.8%</span></figcaption>'
             '</figure>')
    p.append('</section>')

    # ── 문제
    p.append('<section><div class="eyebrow">문제</div>')
    p.append('<h2>재지 않는 격차는 없는 것으로 취급됩니다</h2>')
    p.append('<p class="lede">표준화 지침도 있고 단어 사전도 있습니다. '
             '문제는 어제 만들어진 테이블이 그 사전을 따랐는지 확인할 방법입니다.</p>')
    p.append('<p class="body">확인 작업은 대개 이렇게 흘러갑니다. '
             '누군가 DB 에서 컬럼 목록을 뽑습니다. 엑셀에 붙여넣고 사전과 대조합니다. '
             '몇백 줄쯤에서 속도가 떨어지고, 다음 달에는 다시 하지 않습니다. '
             '그 사이 컬럼은 계속 늘어납니다.</p>')
    p.append('<div class="figures">'
             '<div class="fig"><div class="n">4<small>종</small></div>'
             '<div class="l">컬럼이 표준에서 벗어나는 방식</div></div>'
             '<div class="fig"><div class="n">0<small>회</small></div>'
             '<div class="l">진단이 운영 DB 에 접속하는 횟수</div></div>'
             '<div class="fig"><div class="n">1<small>번</small></div>'
             '<div class="l">한글명만 넣으면 영문약어까지 나오는 입력</div></div>'
             '</div>')
    p.append('</section>')

    # ── 계측
    p.append('<section><div class="eyebrow">계측</div>')
    p.append('<h2>어긋남을 네 가지로 나눕니다</h2>')
    p.append('<p class="lede">"표준을 안 지켰다" 만으로는 무엇을 고칠지 정해지지 않습니다. '
             'Navid Meta 는 컬럼마다 다음 넷 중 하나로 판정하고, 유형마다 다른 조치를 붙입니다.</p>')
    p.append('<div class="taxo">'
             '<div class="tx"><div class="k">용어 미존재</div>'
             '<div class="v">컬럼 영문명에 맞는 표준 용어가 사전에 없습니다. '
             '<em>용어를 등록하거나 한글명에서 자동 생성합니다.</em></div></div>'
             '<div class="tx"><div class="k">한글명 불일치</div>'
             '<div class="v">용어는 찾았는데 컬럼 코멘트가 표준 한글명과 다릅니다. '
             '<em>COMMENT 스크립트를 받습니다.</em></div></div>'
             '<div class="tx"><div class="k">타입 불일치</div>'
             '<div class="v">도메인이 정한 데이터 타입과 다릅니다. '
             'VARCHAR·VARCHAR2·CHAR 처럼 같은 계열은 어긋남으로 보지 않습니다. '
             '<em>ALTER 스크립트를 받습니다.</em></div></div>'
             '<div class="tx"><div class="k">길이 불일치</div>'
             '<div class="v">도메인이 정한 길이와 다릅니다. '
             '<em>ALTER 스크립트를 받습니다.</em></div></div>'
             '</div>')
    p.append(shot("21_diag_result",
                  "진단 결과 화면. 준수율과 이슈 유형별 분포를 먼저 보여주고, "
                  "아래 컬럼 상세에서 이슈 유형에 맞는 조치 버튼이 행마다 붙습니다."))
    p.append('<div class="cards two">'
             '<div class="card"><span class="tag">운영 무부하</span>'
             '<h3>진단은 운영 DB 를 건드리지 않습니다</h3>'
             '<p>수집해 둔 스냅샷과 승인된 용어 사전만 비교합니다. '
             '대상 DB 에 접속하지 않으므로 업무 시간에 돌려도 됩니다.</p></div>'
             '<div class="card"><span class="tag">단일 지표</span>'
             '<h3>준수율 하나로 추적합니다</h3>'
             '<p>(전체 컬럼 − 이슈 컬럼) ÷ 전체 컬럼. 이슈 건수가 아니라 '
             '이슈가 있는 컬럼 수로 셉니다. 한 컬럼에 문제가 셋이어도 하나입니다.</p></div>'
             '</div>')
    p.append('</section>')

    # ── 해소
    p.append('<section><div class="eyebrow">해소</div>')
    p.append('<h2>한글명 하나로 영문 표준명까지 만듭니다</h2>')
    p.append('<p class="lede">표준화가 실제로 막히는 지점은 "이 컬럼을 영어로 뭐라고 쓸 것인가" 입니다. '
             '사람마다 다르게 쓰고, 그래서 표준이 무너집니다.</p>')
    p.append('<p class="body">Navid Meta 는 한글 컬럼명 목록을 받아 형태소로 나눕니다. '
             '표준 단어 사전과 대조하고, 동의어를 따라가고, 사용 빈도로 후보를 정렬해 단어를 확정합니다. '
             '거기서 영문약어를 조합하고 도메인까지 추천합니다.</p>')
    p.append(shot("24_term_recommend",
                  "한글컬럼 일괄 표준화. 입력 → 분석 → 리뷰 3단계로 진행하며, "
                  "결과를 기등록·자동완성·부분매칭·미매칭으로 분류합니다."))
    p.append('<div class="cards two">'
             '<div class="card"><span class="tag">확정은 사람이</span>'
             '<h3>자동은 제안까지입니다</h3>'
             '<p>리뷰 단계에서 행마다 단어 분해를 직접 고칠 수 있습니다. '
             '검토를 거친 것만 등록됩니다.</p></div>'
             '<div class="card"><span class="tag">참조가 따라온다</span>'
             '<h3>물리명이 바뀌면 딸린 것들도</h3>'
             '<p>컬럼 물리명이 바뀌면 그 이름을 참조하던 인덱스·제약조건·FK 부모 참조가 '
             '함께 갱신됩니다. 변경 이력과 변환 이력도 남습니다.</p></div>'
             '</div>')
    p.append('</section>')

    # ── 통제
    p.append('<section><div class="eyebrow">통제</div>')
    p.append('<h2>고친 것이 실제 DB 에 닿기까지</h2>')
    p.append('<p class="lede">표준화 도구는 보통 "이렇게 바꾸세요" 에서 끝납니다. '
             '그 다음은 메일과 엑셀로 넘어가고, 무엇이 반영됐는지는 다시 알 수 없게 됩니다.</p>')
    p.append('<p class="body">Navid Meta 는 신청부터 DDL 실행까지를 한 흐름 안에 둡니다. '
             '일반 사용자의 변경은 초안으로 저장되어 다른 사람에게 보이지 않습니다. '
             '관리자가 승인하면 반영되고, 반려하면 변경 전 값으로 돌아갑니다.</p>')
    p.append(flow(LOOP_ASCII, LOOP_MMD, artifact))
    p.append(shot("19_dm_change_history",
                  "데이터 모델 변경 이력. 승인된 변경에는 DDL 조각이 붙고, "
                  "관리자는 여기서 실제 DB 에 반영합니다. 실행 결과도 이 화면에 남습니다."))
    p.append('<div class="cards two">'
             '<div class="card"><span class="tag">되돌릴 수 있다</span>'
             '<h3>반려하면 원래대로</h3>'
             '<p>변경 전 값으로 원복되고, 그 컬럼을 FK 부모로 참조하던 신청까지 '
             '연쇄 반려됩니다. 참조 관계를 따라 끝까지 전파됩니다.</p></div>'
             '<div class="card"><span class="tag">기록이 남는다</span>'
             '<h3>실행 결과까지 이력에</h3>'
             '<p>DDL 실행 결과가 성공·권한없음·실패로 기록됩니다. '
             '무엇이 반영됐고 무엇이 남았는지 나중에 확인됩니다.</p></div>'
             '</div>')
    p.append('</section>')

    # ── 구성
    p.append('<section><div class="eyebrow">구성</div>')
    p.append('<h2>세 덩어리로 나뉩니다</h2>')
    p.append('<p class="lede">화면과 API 를 담당하는 서버, 무거운 작업을 처리하는 워커, '
             '그리고 메타 정보를 담는 DB 입니다.</p>')
    p.append(flow(ARCH_ASCII, ARCH_MMD, artifact))
    p.append('<div class="cards two">'
             '<div class="card"><h3>q-center</h3>'
             '<p>화면과 API. 표준 사전 CRUD, 승인 워크플로, 진단 결과 조회를 담당합니다. '
             '무거운 작업은 직접 하지 않고 워커에 넘깁니다.</p></div>'
             '<div class="card"><h3>q-executor</h3>'
             '<p>워커. 대상 DB 접속이 필요한 수집·구조 진단, 시간이 걸리는 표준 진단, '
             '예약 실행을 맡습니다. 화면이 멈추지 않도록 분리돼 있습니다.</p></div>'
             '<div class="card"><h3>메타 DB (PostgreSQL)</h3>'
             '<p>표준 사전, 수집된 모델 스냅샷, 진단 결과, 변경 이력이 들어갑니다. '
             '대상 운영 DB 와는 별개입니다.</p></div>'
             '<div class="card"><h3>대상 운영 DB</h3>'
             '<p>수집과 구조 진단 때만 JDBC 로 조회합니다. 표준 진단은 접속하지 않습니다. '
             '쓰기는 관리자가 DDL 반영을 눌렀을 때만 일어납니다.</p></div>'
             '</div>')
    p.append('</section>')

    # ── 차별점
    p.append('<section><div class="eyebrow">차별점</div>')
    p.append('<h2>다른 표준화 도구와 갈리는 네 지점</h2>')
    p.append('<div class="cards two">'
             '<div class="card"><span class="tag">01</span>'
             '<h3>한글 단일 입력 표준화</h3>'
             '<p>형태소 분석에 동의어 매핑과 사용빈도 가중을 더해 한글명에서 표준 단어를 확정합니다. '
             '영문명을 사람이 짓지 않습니다.</p></div>'
             '<div class="card"><span class="tag">02</span>'
             '<h3>진단 결과가 곧 조치 스크립트</h3>'
             '<p>이슈 유형에 따라 행마다 다른 버튼이 붙고, 누르면 대상 DBMS 문법에 맞는 '
             'ALTER · COMMENT 스크립트가 나옵니다. 사이에 번역 단계가 없습니다.</p></div>'
             '<div class="card"><span class="tag">03</span>'
             '<h3>승인에서 DB 반영까지 닫힘</h3>'
             '<p>설계 변경이 실제 운영 DB 에 닿고 그 결과가 다시 제품에 남습니다. '
             '재수집·재진단으로 준수율이 올랐는지 확인됩니다.</p></div>'
             '<div class="card"><span class="tag">04</span>'
             '<h3>다중 스키마를 1급으로</h3>'
             '<p>소유자가 테이블·컬럼의 식별 키에 들어갑니다. 같은 이름이 여러 스키마에 있어도 '
             '서로 덮어쓰지 않고, 진단 집계와 제외 설정도 스키마 단위로 나뉩니다.</p></div>'
             '</div>')
    p.append('</section>')

    # ── 사용자
    p.append('<section><div class="eyebrow">사용자</div>')
    p.append('<h2>두 역할로 운영합니다</h2>')
    p.append('<p class="lede">표준을 정하고 통제하는 쪽, 현업에서 등록과 변경을 신청하는 쪽입니다.</p>')
    p.append('<div class="roles">'
             '<div class="role"><div class="who">표준화 담당 관리자</div>'
             '<div class="does">표준을 정하고, 신청을 검토하고, DB 반영을 결정합니다.</div>'
             '<ul><li>표준 사전 등록 · 수정 · 삭제</li>'
             '<li>등록 신청 승인 · 반려</li>'
             '<li>데이터 소스 · 사용자 · 업무영역 관리</li>'
             '<li>진단 예약, 진단 제외 설정</li>'
             '<li>승인된 변경의 DB 반영</li></ul></div>'
             '<div class="role"><div class="who">현업 · 개발자</div>'
             '<div class="does">표준을 조회하고, 필요한 것을 신청하고, 자기 모델을 진단합니다.</div>'
             '<ul><li>표준 사전 조회 · 등록 <b>신청</b></li>'
             '<li>모델 수집, 컬럼 편집 (초안 → 신청)</li>'
             '<li>표준 진단 · 구조 변경 진단 실행</li>'
             '<li>한글컬럼 일괄 표준화</li>'
             '<li>내 신청 상태 추적</li></ul></div>'
             '</div>')
    p.append(shot("01_dashboard",
                  "로그인 후 첫 화면. 표준 사전 규모, 선택한 모델의 준수율과 구조 일치율, "
                  "최근 구조 변경, 자주 쓰는 작업으로 가는 바로가기가 한 화면에 있습니다."))
    p.append('</section>')

    # ── 도입
    p.append('<section><div class="eyebrow">도입</div>')
    p.append('<h2>첫 준수율이 나오기까지</h2>')
    p.append('<p class="lede">표준 사전이 이미 있다면 3단계에서 시작합니다. '
             '없다면 1단계부터 쌓습니다.</p>')
    p.append('<ol class="steps">'
             '<li><h4>표준 사전 구축</h4><p>도메인 분류 → 단어 → 도메인 → 용어 순으로 쌓습니다. '
             '엑셀 일괄 업로드를 지원하므로 기존 사전이 있으면 그대로 올립니다.</p></li>'
             '<li><h4>데이터 소스 등록</h4><p>대상 DB 접속 정보를 넣고 연결을 확인합니다. '
             '수집 가능한 DBMS 는 아래 표에서 확인하세요.</p></li>'
             '<li><h4>모델 수집</h4><p>스키마를 지정해 테이블·컬럼·인덱스·제약조건을 읽어옵니다. '
             '설계 파일(ERwin XML / XMI)로 대신할 수도 있습니다.</p></li>'
             '<li><h4>첫 진단</h4><p>준수율과 이슈 분포가 나옵니다. '
             '여기서부터 어디를 먼저 손댈지 정해집니다.</p></li>'
             '<li><h4>조치와 재진단</h4><p>이슈 유형별로 조치하고 다시 진단합니다. '
             '이후에는 스케줄로 자동 실행해 추이를 봅니다.</p></li>'
             '</ol>')
    p.append('</section>')

    # ── 범위
    p.append('<section><div class="eyebrow">적용 범위</div>')
    p.append('<h2>지원하는 것과 지원하지 않는 것</h2>')
    p.append('<p class="lede">도입 판단에 필요한 사실입니다. '
             '연결이 되는 것과 수집이 되는 것은 다릅니다.</p>')
    p.append('<div class="tw"><table>'
             '<thead><tr><th>DBMS</th><th>테이블 · 컬럼</th><th>인덱스 · 제약조건</th></tr></thead>'
             '<tbody>'
             '<tr><td><b>Oracle</b> (SID / Service Name)</td>'
             '<td><span class="yes">지원</span></td><td><span class="yes">지원</span></td></tr>'
             '<tr><td><b>PostgreSQL</b></td>'
             '<td><span class="yes">지원</span></td><td><span class="yes">지원</span></td></tr>'
             '<tr><td><b>Cubrid</b></td>'
             '<td><span class="yes">지원</span></td><td><span class="no">미지원</span></td></tr>'
             '<tr><td><b>MariaDB</b></td>'
             '<td><span class="yes">지원</span></td><td><span class="no">미지원</span></td></tr>'
             '<tr><td>Tibero · SQLServer<br>Altibase · Goldilocks</td>'
             '<td><span class="no">수집 불가</span></td>'
             '<td><span class="no">수집 불가</span></td></tr>'
             '</tbody></table></div>')
    p.append('<div class="scope"><h3>미리 알아두실 것</h3><ul>'
             '<li>Tibero · SQLServer · Altibase · Goldilocks 는 데이터 소스 등록과 연결 테스트가 '
             '통과합니다. 그런데 모델 수집은 실패합니다. 이 경우 ERwin XML / XMI 2.1 '
             '설계 파일 임포트로 우회합니다.</li>'
             '<li>데이터 품질 진단(값 프로파일링 · 업무 규칙 검증)은 제품 라인업 분리로 '
             '현재 메뉴에 없습니다. 표준 진단과 구조 변경 진단이 제공 범위입니다.</li>'
             '<li>재수집은 병합 방식이라 운영 DB 에서 삭제된 대상을 자동으로 지우지 않습니다. '
             '구조 변경 진단으로 확인한 뒤 정리합니다.</li>'
             '</ul></div>')
    p.append('</section>')

    # ── 그 밖에
    p.append('<section><div class="eyebrow">그 밖에</div>')
    p.append('<h2>표준화 실무에 필요한 것들</h2>')
    p.append('<div class="cards two">'
             '<div class="card"><h3>공공기관 지침 관리 항목</h3>'
             '<p>데이터베이스 표준화 지침 별표 1 의 표준단어 관리 항목 9개를 모두 보유합니다 — '
             '표준단어명 · 영문명 · 영문약어명 · 설명 · 형식단어여부 · 도메인분류명 · '
             '이음동의어 · 금칙어 · 제정일자.</p></div>'
             '<div class="card"><h3>구조 변경 감지</h3>'
             '<p>수집 시점 스냅샷과 현재 DB 를 컬럼 · 인덱스 · 제약조건 3축으로 대조합니다. '
             '추가 · 변경 · 삭제를 건별로 남깁니다.</p></div>'
             '<div class="card"><h3>영향도 분석</h3>'
             '<p>단어를 바꾸기 전에 그 단어를 쓰는 용어와 실제 컬럼을 보여줍니다. '
             '도메인도 마찬가지입니다.</p></div>'
             '<div class="card"><h3>ERD · 설계 파일</h3>'
             '<p>수집된 모델로 ERD 를 그려 PNG · PDF 로 내보냅니다. '
             'ERwin XML 과 XMI 2.1 은 가져오기와 내보내기 양쪽을 지원합니다.</p></div>'
             '<div class="card"><h3>대량 입력 3경로</h3>'
             '<p>소량은 그리드 인라인 편집, 중량은 엑셀 붙여넣기, '
             '대량은 xlsx 업로드로 미리보기 검증 후 적재합니다. 저장은 단일 트랜잭션입니다.</p></div>'
             '<div class="card"><h3>진단 자동화</h3>'
             '<p>표준 · 구조 진단을 일 · 주 · 월 또는 Cron 으로 예약합니다. '
             '중복 실행을 막고 실패 원인을 유형별로 기록합니다.</p></div>'
             '</div>')
    p.append('</section>')

    p.append('<footer>Navid Meta &middot; 데이터 표준 관리 · 모델 수집 · 표준 진단 플랫폼<br>'
             '스크린샷은 개발 환경에서 촬영했습니다. 건수와 이름은 실제 운영과 다릅니다. '
             '&middot; 2026-08-25</footer>')
    p.append('</div>')

    out = os.path.join(HERE, "intro_artifact.html" if artifact else "intro.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(p))
    print("생성: %-20s %.1fMB" % (os.path.basename(out), os.path.getsize(out) / 1048576))


if __name__ == "__main__":
    build(artifact=False)
    build(artifact=True)
    sys.exit(0)
