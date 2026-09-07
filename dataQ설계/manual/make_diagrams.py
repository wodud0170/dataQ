# -*- coding: utf-8 -*-
"""NavidM Meta 구성도 2종 생성.

  arch_engine.png   서버 엔진 아키텍처 — 매뉴얼 서문 [그림 1], 제품설명서 1.1
  arch_product.png  제품 구성      — 제품설명서 1.5

실행: python make_diagrams.py  (assets/ 에 덮어씀)
"""
from PIL import Image, ImageDraw, ImageFont
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
os.makedirs(OUT, exist_ok=True)
FONT = r"C:\Windows\Fonts\malgun.ttf"
FONTB = r"C:\Windows\Fonts\malgunbd.ttf"
S = 2  # 2배 렌더 후 축소 (안티앨리어싱)

def f(size, bold=False):
    return ImageFont.truetype(FONTB if bold else FONT, size * S)

INK   = (26, 29, 40)
LINE  = (150, 158, 178)
BLUE  = (222, 232, 247)
BLUEB = (60, 105, 180)
HEAD  = (54, 96, 168)
GRAY  = (238, 240, 245)
ORNG  = (253, 235, 214)
ORNGB = (214, 138, 40)

def box(d, xy, fill, outline, r=10, w=2):
    d.rounded_rectangle([c * S for c in xy], radius=r * S, fill=fill,
                        outline=outline, width=w * S)

def text(d, xy, s, size=13, bold=False, color=INK, anchor="mm"):
    d.text((xy[0] * S, xy[1] * S), s, font=f(size, bold), fill=color, anchor=anchor)

def arrow(d, p1, p2, color=LINE, w=2, dash=False):
    x1, y1 = p1[0] * S, p1[1] * S
    x2, y2 = p2[0] * S, p2[1] * S
    if dash:
        import math
        dist = math.hypot(x2 - x1, y2 - y1)
        n = max(int(dist / (8 * S)), 1)
        for i in range(n):
            if i % 2:
                continue
            a = i / n; b = min((i + 1) / n, 1)
            d.line([x1 + (x2 - x1) * a, y1 + (y2 - y1) * a,
                    x1 + (x2 - x1) * b, y1 + (y2 - y1) * b], fill=color, width=w * S)
    else:
        d.line([x1, y1, x2, y2], fill=color, width=w * S)
    # 화살촉
    import math
    ang = math.atan2(y2 - y1, x2 - x1)
    h = 7 * S
    d.polygon([(x2, y2),
               (x2 - h * math.cos(ang - 0.45), y2 - h * math.sin(ang - 0.45)),
               (x2 - h * math.cos(ang + 0.45), y2 - h * math.sin(ang + 0.45))], fill=color)

def cylinder(d, cx, cy, w, h, label, subs=(), fill=GRAY):
    x1, x2 = cx - w / 2, cx + w / 2
    ry = 9
    d.ellipse([x1 * S, (cy - h / 2) * S, x2 * S, (cy - h / 2 + 2 * ry) * S],
              fill=fill, outline=LINE, width=2 * S)
    d.rectangle([x1 * S, (cy - h / 2 + ry) * S, x2 * S, (cy + h / 2 - ry) * S],
                fill=fill, outline=None)
    d.line([x1 * S, (cy - h / 2 + ry) * S, x1 * S, (cy + h / 2 - ry) * S], fill=LINE, width=2 * S)
    d.line([x2 * S, (cy - h / 2 + ry) * S, x2 * S, (cy + h / 2 - ry) * S], fill=LINE, width=2 * S)
    d.chord([x1 * S, (cy + h / 2 - 2 * ry) * S, x2 * S, (cy + h / 2) * S], 0, 180,
            fill=fill, outline=LINE, width=2 * S)
    top = cy - (len(subs) * 15) / 2 - 2
    text(d, (cx, top), label, 12, True)
    for i, t in enumerate(subs):
        text(d, (cx, top + 20 + i * 15), t, 10, False, (90, 97, 120))

# ────────────────────────── 1. 제품 구성 이미지
W, H = 1100, 420
im = Image.new("RGB", (W * S, H * S), "white")
d = ImageDraw.Draw(im)

for x, label in ((165, "대상 데이터베이스"), (550, "서버 엔진"), (940, "클라이언트")):
    box(d, (x - 92, 12, x + 92, 44), HEAD, HEAD, r=4)
    text(d, (x, 28), label, 14, True, "white")
for x in (300, 790):
    for y in range(60, 405, 12):
        d.line([x * S, y * S, x * S, (y + 6) * S], fill=(196, 202, 218), width=2 * S)

cylinder(d, 165, 120, 150, 76, "Oracle", ("SID / Service Name",))
cylinder(d, 165, 250, 150, 76, "PostgreSQL", ("Cubrid · MariaDB",))
text(d, (165, 340), "테이블 · 컬럼 · 인덱스 · 제약조건", 11, False, (90, 97, 120))

box(d, (390, 78, 710, 330), BLUE, BLUEB, r=12)
box(d, (390, 78, 710, 118), (204, 220, 243), BLUEB, r=12)
text(d, (550, 98), "NavidM Meta Engine", 16, True, (32, 62, 118))
for i, s in enumerate(["표준 사전 관리 (단어 · 용어 · 도메인 · 코드)",
                       "데이터 모델 수집 및 관리",
                       "표준 진단 · 구조 변경 진단",
                       "승인 워크플로 · 변경 이력 관리",
                       "대시보드 · 분석 보고서"]):
    y = 145 + i * 34
    d.rectangle([404 * S, (y - 4) * S, 411 * S, (y + 3) * S], fill=BLUEB)
    text(d, (422, y), s, 12, False, INK, anchor="lm")

box(d, (872, 150, 1008, 224), GRAY, LINE, r=8)
text(d, (940, 178), "데이터 관리자", 13, True)
text(d, (940, 202), "웹 브라우저", 11, False, (90, 97, 120))

arrow(d, (245, 150), (386, 175), BLUEB, 2, dash=True)
arrow(d, (245, 240), (386, 210), BLUEB, 2, dash=True)
text(d, (330, 143), "JDBC 수집", 10, False, (60, 105, 180))
arrow(d, (714, 190), (868, 187), ORNGB, 2)
text(d, (791, 172), "HTTPS", 10, False, ORNGB)

im.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, "arch_product.png"))

# ────────────────────────── 2. 서버 엔진 아키텍처
W, H = 1100, 620
im = Image.new("RGB", (W * S, H * S), "white")
d = ImageDraw.Draw(im)

# 사용자 브라우저
box(d, (430, 20, 670, 76), BLUE, BLUEB, r=8)
text(d, (550, 40), "사용자 브라우저", 14, True, (32, 62, 118))
text(d, (550, 61), "Vue.js SPA (WEB UI)", 10, False, (90, 97, 120))

def engine(x1, x2, title, sub, items):
    box(d, (x1, 150, x2, 320), BLUE, BLUEB, r=12)
    cx = (x1 + x2) / 2
    text(d, (cx, 176), title, 16, True, (32, 62, 118))
    text(d, (cx, 199), sub, 11, False, (90, 97, 120))
    for i, s_ in enumerate(items):
        px = x1 + 16 + (i % 2) * ((x2 - x1 - 32) / 2)
        py = 228 + (i // 2) * 44
        pw = (x2 - x1 - 44) / 2
        box(d, (px, py, px + pw, py + 34), "white", BLUEB, r=17, w=2)
        text(d, (px + pw / 2, py + 17), s_, 11, False)

engine(120, 520, "q-center", "화면 · API · 승인",
       ["사용자 인증 / 권한", "표준 사전 CRUD", "승인 워크플로", "진단 결과 조회"])
engine(580, 980, "q-executor", "수집 · 진단 · 스케줄",
       ["데이터 모델 수집", "표준 진단", "구조 변경 진단", "스케줄 실행"])

cylinder(d, 320, 500, 340, 118, "메타 데이터베이스",
         ("PostgreSQL 13 (TimescaleDB 2.10)",
          "표준 사전 · 모델 스냅샷 · 진단 결과 · 변경 이력"), (232, 240, 250))
cylinder(d, 830, 500, 300, 118, "대상 운영 데이터베이스",
         ("Oracle · PostgreSQL · Cubrid · MariaDB",
          "수집 · 구조 변경 진단 시에만 조회"))

# 연결
arrow(d, (550, 80), (550, 146) if False else (400, 146), BLUEB)
text(d, (455, 108), "HTTPS", 10, False, (60, 105, 180))
arrow(d, (524, 235), (576, 235), ORNGB, 2)
text(d, (550, 137), "REST API 호출", 10, False, ORNGB)
arrow(d, (240, 324), (240, 448), BLUEB)
arrow(d, (700, 324), (420, 448), BLUEB)
arrow(d, (900, 324), (880, 448), BLUEB, 2, dash=True)
text(d, (912, 386), "JDBC 조회", 10, False, (60, 105, 180), anchor="lm")

im.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, "arch_engine.png"))
print("생성:", os.listdir(OUT))
