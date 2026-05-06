"""
Narae DataQ 종합 시연 PPT 생성기.
36 슬라이드 + 슬라이드 노트에 발표 대본.
"""
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Narae_DataQ_종합시연_2026-05-06.pptx")

# 색상 팔레트
ND_BLUE   = RGBColor(0x1A, 0x23, 0x7E)  # 네이비
ND_ACCENT = RGBColor(0x3F, 0x51, 0xB5)  # 인디고
ND_GREEN  = RGBColor(0x2E, 0x7D, 0x32)
ND_ORANGE = RGBColor(0xE6, 0x5C, 0x00)
ND_GREY   = RGBColor(0x54, 0x6E, 0x7A)
ND_BG     = RGBColor(0xF5, 0xF7, 0xFA)
ND_WHITE  = RGBColor(0xFF, 0xFF, 0xFF)

prs = Presentation()
prs.slide_width  = Inches(13.33)
prs.slide_height = Inches(7.5)

BLANK = prs.slide_layouts[6]


def add_slide():
    return prs.slides.add_slide(BLANK)


def add_bg(slide, color=ND_BG):
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    bg.line.fill.background()
    bg.fill.solid()
    bg.fill.fore_color.rgb = color
    return bg


def add_textbox(slide, left, top, width, height, text, font_size=18, bold=False,
                color=None, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, font='맑은 고딕'):
    box = slide.shapes.add_textbox(left, top, width, height)
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    if isinstance(text, str):
        text = [text]
    for i, line in enumerate(text):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        run = p.add_run()
        run.text = line
        run.font.name = font
        run.font.size = Pt(font_size)
        run.font.bold = bold
        if color:
            run.font.color.rgb = color
    return box


def add_title_band(slide, title, subtitle=None):
    """상단 색띠 + 제목"""
    band = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, Inches(1.0))
    band.line.fill.background()
    band.fill.solid()
    band.fill.fore_color.rgb = ND_BLUE
    add_textbox(slide, Inches(0.5), Inches(0.15), Inches(12.5), Inches(0.6),
                title, font_size=28, bold=True, color=ND_WHITE)
    if subtitle:
        add_textbox(slide, Inches(0.5), Inches(0.65), Inches(12.5), Inches(0.4),
                    subtitle, font_size=14, color=ND_WHITE)


def add_bullets(slide, left, top, width, height, items, font_size=18, font='맑은 고딕'):
    box = slide.shapes.add_textbox(left, top, width, height)
    tf = box.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        if isinstance(item, tuple):
            indent_lvl, txt = item
        else:
            indent_lvl, txt = 0, item
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = PP_ALIGN.LEFT
        bullet = "  " * indent_lvl + ("• " if indent_lvl == 0 else "– ")
        run = p.add_run()
        run.text = bullet + txt
        run.font.name = font
        run.font.size = Pt(font_size)
        if indent_lvl == 0:
            run.font.bold = True
            run.font.color.rgb = ND_BLUE
        else:
            run.font.color.rgb = ND_GREY
    return box


def add_notes(slide, text):
    notes = slide.notes_slide.notes_text_frame
    notes.text = text


# ─────────────────────────────────────────────────────────
# 슬라이드 1 — 표지
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s, ND_BLUE)
add_textbox(s, Inches(0.8), Inches(2.4), Inches(11.7), Inches(1.0),
            "Narae DataQ", font_size=64, bold=True, color=ND_WHITE,
            align=PP_ALIGN.CENTER)
add_textbox(s, Inches(0.8), Inches(3.5), Inches(11.7), Inches(0.8),
            "데이터 품질 / 표준화 관리 플랫폼 — 종합 시연",
            font_size=28, color=ND_WHITE, align=PP_ALIGN.CENTER)
add_textbox(s, Inches(0.8), Inches(4.5), Inches(11.7), Inches(0.5),
            "단어·용어·도메인 표준 → 데이터 모델 수집 → 표준화·구조·품질 진단 → 자동 표준화 → 스케줄링",
            font_size=16, color=RGBColor(0xC5, 0xCA, 0xE9), align=PP_ALIGN.CENTER)
add_textbox(s, Inches(0.8), Inches(6.6), Inches(11.7), Inches(0.4),
            "2026-05-06 / 발표자 — 장재영", font_size=14, color=RGBColor(0xC5, 0xCA, 0xE9),
            align=PP_ALIGN.CENTER)
add_notes(s, "안녕하세요. Narae DataQ — 데이터 품질 관리 플랫폼의 종합 시연을 시작하겠습니다. "
            "단어·용어·도메인 표준 관리부터 데이터 모델 수집·표준화 진단·구조 변경 감지·"
            "데이터 품질 검증·진단 스케줄링까지, 데이터 표준화 라이프사이클 전체를 한 솔루션에서 다룹니다. "
            "이번 시연은 좌측 메뉴 순서대로 모든 핵심 기능을 보여드리는 종합 시연입니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 2 — 메뉴 구조 (Agenda)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "Agenda — 메뉴 순서를 그대로 따라가는 종합 시연")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(6.0), Inches(5.7), [
    "1. 시스템 개요 + 환경",
    "2. 로그인 + 대시보드",
    "3. 데이터 표준 사전 (단어/용어/코드/도메인/그룹/분류/이력)",
    "4. 데이터 모델 (수집/그리드/DDL/진단 제외 관리 79번)",
    "5. 표준화 진단 (실행/결과/빠른 등록)",
    "6. 구조 변경 진단",
])
add_bullets(s, Inches(6.8), Inches(1.3), Inches(6.0), Inches(5.7), [
    "7. 자동 표준화 지원 (컬럼 표준화)",
    "8. 데이터 품질 진단 (룰/검증/위반 샘플)",
    "9. 진단 스케줄",
    "10. 마이페이지 + 관리",
    "11. 자동화 검증 (셀레니움 29 PASS)",
    "12. SMETA 비교 + Q&A",
])
add_notes(s, "오늘 시연 순서입니다. 좌측 네비게이션 메뉴 순서를 그대로 따라가면서 모든 메뉴의 핵심 기능을 차례대로 보여드립니다. "
            "마지막에는 셀레니움 자동 회귀 결과와 SMETA 대비 차별화 포인트를 정리합니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 3 — 환경
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "1. 시스템 환경", "Java 1.8 + Spring Boot 2.7 + Vue 2 + PostgreSQL 13")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(6.2), Inches(5.0), [
    "Backend (2 modules)",
    (1, "q-center — 웹 서버, 포트 28091"),
    (1, "q-executor — 백그라운드 워커, 포트 28098"),
    (1, "q-common — 공유 VO + MyBatis Mapper"),
    "Frontend",
    (1, "Vue 2.5 + Vuetify 2.6 (SPA, keep-alive 탭)"),
    (1, "WebSocket (STOMP) — 진단 실시간 진행률"),
])
add_bullets(s, Inches(6.9), Inches(1.3), Inches(6.0), Inches(5.0), [
    "Database",
    (1, "메타: PostgreSQL 13 (외부 25433)"),
    (1, "외부: Oracle SID/Service, Cubrid, MySQL/MariaDB"),
    "Build / Deploy",
    (1, "Maven 루트 reactor (~2분)"),
    (1, "Docker 컨테이너: dataq-db, oracle-xe"),
    (1, "DDL_full_schema.sql 단일 진실 → 신규 환경 1파일 실행"),
])
add_notes(s, "백엔드는 Spring Boot 두 모듈 — q-center 웹과 q-executor 워커. 프론트는 Vue 2 SPA, "
            "데이터 모델 진단은 WebSocket 으로 실시간 진행률 push. 메타DB 는 PostgreSQL 13 컨테이너이고, "
            "외부 데이터소스로 Oracle SID/Service Name 둘 다 지원, Cubrid 호환성도 확보했습니다. "
            "DDL 은 pg_dump 결과 한 파일로 단일 진실 운영 — 신규 환경 구축은 그 파일 한 번 실행으로 끝납니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 4 — 로그인
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "2. 로그인", "ID/PW + SHA-256 솔트 + 권한 (admin / user 2단계)")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "시연 절차",
    (1, "http://localhost:28091 진입"),
    (1, "ID 'space' (관리자) / PW '123' 입력"),
    (1, "[로그인] 클릭 → /app/main 진입"),
    "검증 포인트",
    (1, "비밀번호: SHA-256 + 솔트 (ndata-quality-secret)"),
    (1, "세션: HttpSession + STOMP WebSocket 동시 인증"),
    (1, "권한: TB_USER.ADMIN_YN — 'Y' 면 [관리] 메뉴 노출"),
    "Fallback",
    (1, "DB 연결 실패 시 → docker restart dataq-db → 재시도"),
])
add_notes(s, "ID 와 비밀번호는 SHA-256 + 솔트로 저장합니다. 권한은 admin/user 두 단계인데, "
            "중요한 건 admin 만 [관리] 그룹 메뉴가 DOM 에 렌더되고 API 도 403 거부합니다. "
            "지금 로그인합니다 — 'space' / '123'.")


# ─────────────────────────────────────────────────────────
# 슬라이드 5 — 대시보드
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "3. 대시보드", "표준 현황 + 데이터 모델 현황 + 승인 현황 + 추이 차트")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "카드 4개 (클릭 시 해당 메뉴로 이동)",
    (1, "표준 현황 — 단어/용어/도메인 승인된 건수"),
    (1, "데이터 모델 현황 — 모델/테이블/컬럼 합계"),
    (1, "승인 현황 — 대기 / 승인 완료 / 반려"),
    (1, "표준화 준수율 — 모델별 percent"),
    "추이 차트",
    (1, "최근 N회 진단의 표준 준수율 시계열"),
    (1, "준수율 = (전체 컬럼수 - 이슈 컬럼수) / 전체 컬럼수 × 100"),
    (1, "RESULT_CNT(이슈 건수) 가 아닌 ISSUE_COL_CNT(이슈 컬럼수) 사용"),
])
add_notes(s, "대시보드는 한 화면 안에 표준화 운영 KPI 를 보여줍니다. 카드 클릭하면 해당 메뉴로 점프. "
            "표준화 준수율은 컬럼 단위 — DISTINCT(테이블.컬럼) 로 카운트해서 한 컬럼이 여러 이슈여도 1로 처리. "
            "이렇게 해야 의미 있는 백분율이 나옵니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 6 — 단어
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-1. 데이터 표준 사전 — 단어", "행안부 공통 표준 단어 + 사용자 추가 + 형식단어")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "검색",
    (1, "단어명 / 영문약어 / 형식단어 여부 / 등록일자 범위 / 승인 여부"),
    "그리드",
    (1, "행 클릭 → 상세 패널 (영문 풀명, 단어 설명, 동의어 리스트)"),
    "버튼",
    (1, "[등록] / [일괄 등록] / [템플릿 다운로드] / [다운로드] / [삭제]"),
    "등록 모달",
    (1, "한글 단어명 → 영문약어 (자동 대문자) → 영문 풀명"),
    (1, "도메인 분류 (금액/일자/코드 등) + 형식단어 여부 토글"),
    (1, "동의어 리스트 + 금칙어 리스트"),
])
add_notes(s, "단어 사전은 행안부 공통 표준 약 3,300건 + 우리 추가 단어. 검색 필터에 등록일자 범위가 있어서 "
            "특정 기간 내 등록된 단어만 추적할 수 있고, 형식단어 여부로 명사·분류어 별도 검색이 가능합니다. "
            "등록 모달에서 동의어 리스트가 핵심 — 자동 표준화 시 이 동의어를 통해 미등록 단어를 자동 매칭합니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 7 — 용어 등록 v2 (81/82번 신규)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-2. 용어 등록 v2 (81/82번 신규)",
               "단일 폼 + 1초 디바운스 자동 분석 + 코드 picker")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "v1 → v2 핵심 변화",
    (1, "3-step stepper 폐지 → 단일 폼"),
    (1, "한글 용어명 입력 → 1초 디바운스 → 자동 analyzeTermsBatch API 호출"),
    (1, "DSTermRecommend 와 동일 알고리즘 (DP 점수 + 동의어 cascade)"),
    "응답 (단어 1개당 분류 1개 — 부분문자열 잡음 없음)",
    (1, "MATCHED → wordLst[selected wordCandidate] + 자동 체크"),
    (1, "NEW / UNRECOGNIZED → 인라인 등록 폼 즉시 노출"),
    "추가 기능",
    (1, "추천 도메인 자동 채움 (recommendedDomainNm)"),
    (1, "마지막 단어가 'CD' → 도메인 유형 자동 '코드' 토글 + picker 다이얼로그"),
])
add_notes(s, "v1 은 stepper 3 단계로 사용자 클릭이 많았고 부분문자열 매칭이 너무 많이 노출됐습니다. "
            "예를 들어 '가로세로일시' 입력 시 27개 분류가 동시에 나와서 어디를 선택해야 할지 헷갈렸어요. "
            "v2 는 백엔드의 analyzeTermsBatch API 로 교체했는데, 이게 DP 점수 + 가장 긴 매칭 우선 + 동의어 cascade 적용해서 "
            "단어 1개당 분류 1개만 깨끗하게 반환합니다. MATCHED 단어는 자동 체크되고, 추천 도메인은 자동 채움. "
            "마지막 단어가 'CD' 면 코드 picker 가 자동으로 활성화. 사용자 클릭 횟수가 v1 대비 3분의 1 수준으로 줄었습니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 8 — 용어 분석 케이스 (4종)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-2. 용어 분석 4종 status",
               "AUTO / PARTIAL / FAILED / REGISTERED — 자동 셀레니움 검증")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "AUTO — 모든 단어 매칭",
    (1, "예: '회원전화번호' → [회원, 전화, 번호] → MBR_TEL_NO 즉시 등록 가능"),
    "PARTIAL — 일부 미등록",
    (1, "예: '블라블라일자' → '일자' MATCHED + '블라블라' UNRECOGNIZED"),
    (1, "→ 미등록 칩 + 인라인 등록 폼 노출 (단어 한글명/영문약어/영문명 입력 + [단어 등록])"),
    "FAILED — 전 토큰 미인식",
    (1, "예: '라랄라룰루' → 모든 토큰 UNRECOGNIZED → swal 안내"),
    "REGISTERED — 이미 등록된 용어",
    (1, "중복 swal 알림 + 자동 분석은 그대로 표시"),
    "→ test_term_register_v2.py 8/8 PASS",
])
add_notes(s, "용어 분석 결과는 4가지로 분류됩니다. AUTO 는 모두 매칭 — 그대로 등록 가능. "
            "PARTIAL 은 일부 미등록인데, 미등록 단어를 모달 안에서 인라인으로 즉시 등록할 수 있어서 화면 전환 없이 흐름이 끊기지 않습니다. "
            "FAILED 는 다시 입력하라는 안내, REGISTERED 는 이미 있으니 중복이라고 알려줍니다. "
            "이 4가지가 셀레니움 8 케이스로 자동 검증됩니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 9 — 코드/도메인/그룹/분류/이력
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-3. 코드 / 도메인 / 그룹 / 분류 / 변경 이력",
               "표준 사전의 나머지 5개 메뉴")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "코드 — 코드 그룹 + 코드 항목",
    (1, "예: '성별 (GENDER_CD)' = M / F / N"),
    (1, "등록일자 범위 검색 신규 적용"),
    "도메인 — 데이터 타입/길이 표준",
    (1, "예: '금액 (AMT)' = NUMBER(15,2)"),
    "도메인 그룹 / 도메인 분류",
    (1, "도메인을 분류로 묶어 자동 표준화 시 cascade 추천"),
    "변경 이력",
    (1, "단어/용어/도메인/코드 모든 항목 등록·수정·삭제 이력"),
    (1, "변경 사용자, 시각, before/after 값 모두 추적"),
])
add_notes(s, "코드는 코드 그룹과 항목 — 예를 들어 성별 코드 그룹에 M, F, N 같은 항목들. "
            "도메인은 데이터 타입+길이 표준화. 도메인을 분류로 묶으면 자동 표준화 시 마지막 단어의 분류명에 따라 도메인이 cascade 됩니다. "
            "변경 이력은 모든 항목의 before/after 값을 보존 — 누가 언제 뭘 바꿨는지 100% 추적 가능합니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 10 — 일괄 등록 + 다운로드
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-4. 일괄 등록 양식 + 결과 다운로드", "정적 XLSX 5종 + 동적 POI")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "정적 XLSX 양식 (5종)",
    (1, "단어 / 용어 / 도메인 / 테이블 / 컬럼"),
    (1, "[템플릿 다운로드] → 즉시 다운로드 (ClassPathResource)"),
    "일괄 등록 흐름",
    (1, "양식 입력 → [일괄 등록] → 미리보기 (검증 결과 색 구분) → [커밋]"),
    (1, "검증 실패 행은 빨강 + 사유 인라인"),
    "결과 다운로드 (동적)",
    (1, "검색 필터 적용 결과를 POI 동적 생성으로 XLSX"),
    (1, "현재 그리드 상태 그대로 — 사용자 의도 반영"),
])
add_notes(s, "양식 다운로드는 정적 XLSX 5종 — 미리 디자인된 헤더만 있는 빈 양식. 사용자가 입력해서 다시 일괄 등록하면 미리보기 단계에서 "
            "검증 결과를 색으로 구분해서 보여주고 사용자 확인 후 커밋. 결과 다운로드는 정적이 아니라 POI 로 동적 생성 — "
            "현재 검색·필터 상태 그대로 다운로드되니까 사용자 의도가 반영됩니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 11 — 승인 워크플로
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "4-5. 승인 워크플로 (관리 메뉴)", "단어 선승인 + cascade 반려 + 반려 후 물리 삭제")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "승인 흐름",
    (1, "일반 사용자 등록 → APRV_YN='N' → [관리 > 승인] 대기열"),
    (1, "관리자 행별 [승인]/[반려] 인라인 (사유 입력)"),
    (1, "관리자 등록 시 APRV_YN='Y' 즉시 승인"),
    "정책",
    (1, "단어 선승인 — 구성 단어 미승인 시 용어 승인 거부 (alert)"),
    (1, "cascade 반려 — 단어 반려 시 연관 미승인 용어 동시 반려 + 알림"),
    (1, "반려 후 물리 삭제 — 표준 사전에서 미노출, 동일 단어명 재등록 가능"),
    "검증",
    (1, "test_word_approval_flow / test_full_approval_flow / test_cascade_and_word_first / test_reject_physical_delete"),
])
add_notes(s, "승인 워크플로는 일반 사용자 등록 시 APRV_YN='N' 으로 들어가고 관리자가 승인 화면에서 행별로 승인/반려. "
            "정책이 핵심입니다 — 단어가 승인 안 되면 그 단어를 쓴 용어도 승인 거부. 단어 반려하면 연관된 미승인 용어도 동시 반려. "
            "반려된 항목은 물리 삭제해서 동일 이름 재등록이 가능. 이 정책들이 셀레니움 4건 + cascade 12건으로 자동 검증됩니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 12 — 데이터 모델 등록 + 수집
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-1. 데이터 모델 — 등록 + 자동 수집",
               "Oracle/Postgres/Cubrid/MySQL/MariaDB DBMS 메타데이터 자동 수집")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "데이터 소스 등록 (관리 > 데이터 소스)",
    (1, "DBMS 유형 선택 — Oracle SID / Oracle Service Name / Postgres / Cubrid / MySQL / MariaDB"),
    (1, "호스트 / 포트 / 계정 / 비밀번호 (jasypt 암호화)"),
    (1, "[연결 테스트] — 즉시 검증"),
    "데이터 모델 등록 (데이터 모델 > 관리)",
    (1, "데이터소스 선택 + 스키마 다중 선택 → 저장 + 자동 수집"),
    (1, "수집: 테이블 + 컬럼 + 인덱스 + 제약조건 4 영역"),
    (1, "재수집 시: ADDED_CNT / DELETED_CNT / MODIFIED_CNT 통계 (44/48번 설계)"),
    "물리 모델 / 논리 모델 분리",
    (1, "DSID NULL 이면 논리 모델 — 표준 진단만 (구조 진단 거부)"),
])
add_notes(s, "외부 DBMS 메타데이터를 자동으로 수집합니다. Oracle 은 SID 와 Service Name 둘 다 지원. "
            "Cubrid 는 시스템 카탈로그 호환성을 추가로 작업해서 사용 가능. 모델 등록 시 스키마 다중 선택해서 한 번에 수집. "
            "재수집 시 추가/삭제/변경 통계가 자동 누적되는데, 이 컬럼이 어제 PC1 과 동기화 작업한 부분입니다. "
            "물리 모델은 데이터소스 연결돼있고, 논리 모델은 DSID 없는 모델 — 둘이 진단 적용 범위가 달라요.")


# ─────────────────────────────────────────────────────────
# 슬라이드 13 — 수집 이력
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-1-2. 수집 이력", "수집 이벤트 로그 + 변경 통계")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "테이블 컬럼",
    (1, "수집 ID / 모델명 / 시작·종료 시각 / 완료 여부"),
    (1, "ADDED_CNT — 신규 추가된 테이블·컬럼 합계"),
    (1, "DELETED_CNT — 삭제된 테이블·컬럼 합계"),
    (1, "MODIFIED_CNT — 변경된 테이블·컬럼 합계"),
    "활용",
    (1, "운영 추적 — 'X 모델 마지막 수집 일시 + 변경량' 한 화면 조회"),
    (1, "장애 시 — '어제 수집 vs 오늘 수집' 차이로 원인 추정"),
    (1, "감사 — 누가 언제 수집을 트리거했는지 변경 이력에 남김"),
])
add_notes(s, "수집 이력은 단순한 로그가 아니라 변경 통계까지 같이 표시되는 감사 로그입니다. "
            "44번 설계에서 정의한 ADDED_CNT/DELETED_CNT/MODIFIED_CNT 가 핵심 — 운영 중에 어떤 모델에 변경이 컸는지 한눈에 보입니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 14 — 그리드 편집 (53번)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-2. 테이블 + 컬럼 그리드 편집 (53번 재설계)",
               "3가지 입력 경로 + 인라인 검증 + dirty 표시")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "3가지 입력 경로",
    (1, "그리드 직접 — 인라인 행 추가, 셀 편집, 한글명 중복 검증"),
    (1, "TSV 붙여넣기 — 엑셀에서 다중 셀 복사 → Ctrl+V → 자동 행 분배"),
    (1, "엑셀 일괄 업로드 — 양식 다운로드 → 입력 → 미리보기 → 커밋"),
    "헤더 라벨 통일 (PC1 4-27 보강)",
    (1, "'테이블명' → '테이블 영문명 (물리)' 등 명확화"),
    (1, "헤더 3색 + 인라인 편집 + dirty 노란 배경"),
    "변환 (자동 표준화 연동)",
    (1, "한글명 입력 → [변환] → 영문약어/타입/길이 자동"),
    (1, "변환 실패 시 → TMP_COL_{n} + VARCHAR(255) 비표준 자동 저장"),
])
add_notes(s, "컬럼 편집은 53번 설계로 재작업한 영역인데, 입력 경로가 3개입니다. "
            "그리드 직접 편집, TSV 붙여넣기, 엑셀 업로드. TSV 붙여넣기가 운영 중 가장 자주 쓰이는데 "
            "엑셀에서 표 복사해서 그리드에 붙이면 행이 자동으로 분배됩니다. "
            "한글명을 입력하고 [변환] 누르면 자동 표준화 엔진이 영문약어와 타입을 자동으로 채워줍니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 15 — 테이블 cascade rename
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-2-2. 테이블 물리명 변경 (cascade rename)",
               "5단계 cascade — OBJ_NM / ATTR / INDEX / CONSTRAINT / REF_TABLE_NM")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "변경 영향도 미리보기",
    (1, "previewObjRename API — 5개 카운트 (OBJ/ATTR/INDEX/CONSTRAINT/REF_TABLE)"),
    (1, "사용자 확인 swal → 진행 여부 결정"),
    "실제 변경",
    (1, "updateObj API — 5단계 cascade rename 트랜잭션"),
    (1, "DM_ID + 자연키 PK 기준 (CLCT 폐기 후)"),
    "검증",
    (1, "test_obj_rename_cascade.py — 8/8 PASS"),
])
add_notes(s, "테이블 물리명을 바꿀 때 그 테이블 컬럼들, 인덱스, 제약조건, 그리고 외래키로 이 테이블을 참조하는 다른 테이블의 REF_TABLE_NM 까지 "
            "5단계로 cascade rename 됩니다. 변경 전에 영향도 카운트가 미리보기로 swal 에 뜨고 사용자 확인 받은 후에만 진행. "
            "셀레니움 test_obj_rename_cascade.py 로 8 phase 자동 검증.")


# ─────────────────────────────────────────────────────────
# 슬라이드 16 — DDL 다운로드
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-3. DDL 다운로드", "방언 자동 / PostgreSQL / Oracle 선택")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "방언 선택 (이번 세션 v-menu 드롭다운)",
    (1, "자동 — 모델의 데이터소스 DBMS 타입 기준"),
    (1, "PostgreSQL — varchar / integer / timestamp"),
    (1, "Oracle — varchar2 / number / date"),
    (1, "물리 미연결 시 oracle 폴백"),
    "포함 항목",
    (1, "CREATE TABLE (모든 컬럼 + COMMENT)"),
    (1, "PK / FK / UK 제약조건"),
    (1, "INDEX 정의"),
    "출력",
    (1, ".sql 파일 즉시 다운로드"),
])
add_notes(s, "DDL 다운로드는 모델 행에서 [DDL 다운로드] 드롭다운 클릭하면 방언 선택. 자동 선택하면 모델의 DBMS 타입에 맞춰 "
            "Oracle 이면 varchar2/number, Postgres 면 varchar/integer 로 출력. CREATE TABLE 만 아니라 PK/FK/UK + 인덱스 + COMMENT 까지 통합. "
            "데이터 모델 변경 후 DBA 한테 전달할 SQL 한 파일.")


# ─────────────────────────────────────────────────────────
# 슬라이드 17 — 진단 제외 관리 (79번 신규)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-4. 진단 제외 관리 (79번 신규)",
               "임시·폐기 테이블/컬럼 → 진단 모수에서 명시적 제외 + cascade + 사유")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "화면 구성",
    (1, "테이블 단위 / 컬럼 단위 탭"),
    (1, "각 행에 표준 / 구조 / 품질 3개 진단별 ON/OFF 토글 아이콘"),
    "OFF 시 사유 모달",
    (1, "선택입력 — 빈 사유로도 OFF 가능 (NULL 저장)"),
    (1, "마지막 변경자 / 일시 자동 기록"),
    "Cascade 정책",
    (1, "테이블 OFF → 그 테이블의 모든 컬럼이 자동 모수 제외"),
    (1, "테이블 ON + 컬럼 OFF → 해당 컬럼만 제외"),
    "일괄 토글",
    (1, "다중 선택 → [표준 OFF / 구조 OFF / 품질 OFF / ...ON] 버튼"),
    "권한",
    (1, "관리자 전용 — 일반 사용자는 화면 진입 후 토글 시 403"),
])
add_notes(s, "이번 세션 신규 메뉴입니다. 임시 테이블, 폐기 예정 테이블, 의미 없는 컬럼을 진단 모수에서 명시적으로 제외하는 기능. "
            "기존엔 진단 결과를 그냥 무시했는데 그러면 표준 준수율이 왜곡됩니다. 명시적 OFF 하면 모수에서 빠지므로 정확한 준수율. "
            "Cascade 가 핵심 — 테이블 OFF 하면 그 안의 컬럼 다 자동 빠지고, 테이블 ON + 컬럼만 OFF 하면 그 컬럼만. "
            "일괄 토글로 한 번에 여러 행 OFF 가능. 모든 변경은 사유와 함께 마지막 변경자/일시 기록.")


# ─────────────────────────────────────────────────────────
# 슬라이드 18 — 79번 12 phase 검증
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "5-4-2. 79번 12 phase 자동 검증",
               "test_diag_target_imsi.py — Oracle DDL + 매퍼 SQL 직접 검증")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    (0, "P1~P2: Oracle IMSI_TEST_001/002/003 생성 + 메타 INSERT (12 ATTR)"),
    (0, "P3: OBJ 단건 OFF — TEST_001 표준 + 사유"),
    (0, "P4: OBJ 일괄 OFF — TEST_002/003 구조 + 사유"),
    (0, "P5: ATTR 단건 OFF — TEST_001.NAME 구조 + 사유 빈칸 (NULL)"),
    (0, "P6: ATTR 일괄 OFF — TEST_002.CODE/VALUE 표준"),
    (0, "P7: 표준 진단 매퍼 — 모수 5 / 제외 7 (cascade 적용)"),
    (0, "P8: 구조 진단 매퍼 — 모수 4 (TEST_001 cascade)"),
    (0, "P9: ALTER (XYZ_DATA 길이 / NEW_COL 추가 / ETC 길이)"),
    (0, "P10: 구조 매퍼 재검증 — OFF 변경은 모수 미포함"),
    (0, "P11: ON 복귀 후 모수 13 회복 (12 + NEW_COL) / 사유 NULL 클리어"),
    (0, "P12: cleanup — Oracle DROP + 메타 DELETE"),
    (0, "→ 229초 PASS"),
])
add_notes(s, "79번 검증은 12 phase 로 끊어서 매퍼 SQL 까지 직접 검증합니다. "
            "Oracle 컨테이너에 임시 테이블 만들고 메타 INSERT 한 다음 단건/일괄 OFF 적용. "
            "그 다음 표준 진단 매퍼와 구조 진단 매퍼가 OFF row 를 정확히 모수에서 빼는지 SQL 수준에서 카운트 확인. "
            "ALTER 후 재검증 단계는 OFF 처리된 테이블의 변경이 결과에 안 떠야 한다는 핵심 동작 검증. "
            "마지막에 ON 복귀 후 모수 회복까지 풀 사이클 — 229초 PASS.")


# ─────────────────────────────────────────────────────────
# 슬라이드 19 — 표준화 진단 실행
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "6-1. 표준화 진단 — 실행", "q-executor 백그라운드 + STOMP 실시간 진행률")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "실행 절차",
    (1, "[표준 진단 > 진단 실행] → 모델 선택 → [진단 시작]"),
    (1, "q-executor 가 백그라운드 처리 (q-center 와 STOMP 통신)"),
    "이슈 6종",
    (1, "용어 미존재 / 한글명 불일치 / 영문약어 불일치"),
    (1, "타입 불일치 / 길이 불일치 / 도메인 불일치"),
    "진행률 표시",
    (1, "현재 처리 중 / 전체 컬럼수 / 발견된 이슈 / 예상 완료 시각"),
    "결과 저장",
    (1, "TB_DIAG_JOB (헤더) + TB_DIAG_RESULT (이슈 건별)"),
    (1, "TARGET_YN='Y' 만 모수 — 79번 진단 제외 cascade 즉시 반영"),
])
add_notes(s, "표준 진단은 사용자가 모델 선택하고 [진단 시작] 누르면 q-executor 가 백그라운드에서 처리. "
            "진행률은 WebSocket STOMP 로 실시간 push 되니까 사용자는 화면 닫아도 됩니다. "
            "이슈 6종 — 용어 미존재, 한글명 불일치, 영문약어 불일치, 타입 불일치, 길이 불일치, 도메인 불일치. "
            "결과는 TB_DIAG_JOB 헤더 + TB_DIAG_RESULT 이슈별 row 로 저장. 79번 진단 제외 cascade 가 매퍼 단계에서 즉시 반영됩니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 20 — 진단 결과
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "6-2. 표준화 진단 — 결과", "필터 + 상세 drawer + 표준 준수율")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "필터",
    (1, "이슈 유형 / 테이블 / 컬럼 / 표준 준수 여부 / 확인 여부"),
    "그리드",
    (1, "행 클릭 → 상세 drawer (현재값 vs 권장값 비교)"),
    "표준 준수율",
    (1, "(전체 컬럼수 - 이슈 컬럼수) / 전체 컬럼수 × 100"),
    (1, "RESULT_CNT(이슈 건수) 가 아닌 ISSUE_COL_CNT(이슈 컬럼수)"),
    (1, "한 컬럼이 6 이슈라도 1로 카운트 — 의미 있는 백분율"),
    "결과 다운로드",
    (1, "현재 필터 상태 그대로 POI 동적 XLSX"),
])
add_notes(s, "진단 결과 화면은 필터로 빠르게 좁혀들어갑니다. 행 클릭하면 우측에 drawer 가 열려서 현재값과 권장값을 나란히 비교. "
            "표준 준수율 계산식이 중요한데, 이슈 건수가 아니라 이슈 컬럼 수로 계산합니다. "
            "한 컬럼이 6개 이슈가 있어도 1로 카운트 — 그래야 의미있는 백분율이 나오고 사용자가 컬럼 단위로 작업 우선순위를 잡을 수 있어요.")


# ─────────────────────────────────────────────────────────
# 슬라이드 21 — 빠른 등록 + 코멘트
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "6-3. 진단 결과 — 용어 빠른 등록 + 코멘트",
               "이슈 → 즉시 표준 사전 보강 → 진단 재실행 없이 결과 갱신")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "용어 빠른 등록",
    (1, "이슈 행 → [용어 빠른 등록] → 즉시 등록"),
    (1, "진단 재실행 없이 결과 row 갱신 (TB_DIAG_RESULT 한 row update)"),
    "코멘트",
    (1, "진단 결과에 메모 (예: '검토 완료', '현재 시스템 제약상 불가')"),
    (1, "변경 이력에 자동 등록 — 누가 언제 코멘트했는지 추적"),
    "[해결] 버튼",
    (1, "PARTIAL/FAILED 행 → 자동 표준화 추천 모달 진입"),
    (1, "53번 컬럼 그리드와 동일 모달 재사용"),
])
add_notes(s, "진단 결과에서 이슈 행마다 [용어 빠른 등록] 버튼이 있어요. 표준 사전에 없는 용어가 발견되면 그 자리에서 등록해버리고 "
            "진단 재실행 없이 결과 row 만 갱신. 운영 중 이걸로 빠르게 표준 사전 채워나갑니다. "
            "코멘트는 진단 결과에 메모를 남길 수 있고, 누가 언제 코멘트했는지 변경 이력에 자동 등록. "
            "[해결] 버튼은 자동 표준화 추천 모달로 점프 — 53번 컬럼 그리드의 모달과 동일한 모달이라 학습 비용 없습니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 22 — 구조 변경 진단
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "7. 구조 변경 진단",
               "DBMS 실 스키마 vs 수집 스냅샷 — 추가/변경/삭제 자동 감지")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "실행",
    (1, "[구조 변경 진단 > 진단 실행] → 모델 선택 → [실행]"),
    (1, "물리 모델만 (DSID 있는) — 논리 모델은 거부 (400)"),
    "결과 화면 3단계 조회",
    (1, "이력 (TB_STRUCT_DIAG_HISTORY) → 상세 (DETAIL) → 컬럼/제약/인덱스별"),
    (1, "각 변경 — 컬럼명, 변경 전/후 타입·길이·NULL 여부"),
    "79번 진단 제외 cascade",
    (1, "OFF 표시된 테이블/컬럼의 변경은 결과 미등장"),
    (1, "PC1 보강 (2bef4e8) — prev/curr OFF set 통합 + toUpperCase 정렬"),
])
add_notes(s, "구조 변경 진단은 데이터베이스의 실제 스키마와 우리가 수집한 스냅샷을 비교해서 컬럼이 추가/변경/삭제됐는지 감지. "
            "결과는 3단계로 조회 — 진단 이력, 그 안의 상세, 그 안의 컬럼/제약/인덱스별. "
            "79번 진단 제외 cascade 가 여기서도 적용 — OFF 처리된 테이블이나 컬럼의 변경은 결과에 안 떠서 노이즈가 줄어듭니다. "
            "이 cascade 통합 작업은 어제 PC1 에서 보강한 부분이고 4 케이스 전수 검증됐습니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 23 — 자동 표준화 분석
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "8-1. 자동 표준화 — 컬럼 표준화 분석",
               "한글 컬럼명 → 단어 분리 → 영문약어/도메인 추천 → 자동 등록")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "입력",
    (1, "[자동 표준화 지원 > 컬럼 표준화] → textarea 한글 컬럼명 N건 (줄바꿈 구분)"),
    (1, "공백 무시, 중복 제거"),
    "분석 알고리즘 (DataStandardController + analyzeTermsBatch)",
    (1, "DP 점수 — SPLIT_PENALTY=8000 / 1자 비형식단어 격하(5000)"),
    (1, "사후처리 resolveUncertainRuns — 미신뢰 토큰 합쳐 사전(TB_WORD/DICT/alloph_synm) 재검색"),
    (1, "동의어 매핑 synmToWord — 카테고리 → 범주 자동 매칭"),
    "결과",
    (1, "REGISTERED / AUTO / PARTIAL / FAILED 4 status 자동 판정"),
    (1, "추천 영문약어 / 도메인 / 데이터타입 / 길이 자동"),
])
add_notes(s, "자동 표준화는 DataQ 의 핵심 차별화 영역입니다. 한글 컬럼명을 textarea 에 줄바꿈 구분해서 여러 건 동시 입력 → 분석. "
            "알고리즘은 DP 기반으로 가장 그럴듯한 단어 분리를 찾고, DP 만으로 부족한 미신뢰 토큰은 사후처리로 사전 재검색해서 보강. "
            "동의어 매핑이 결정적인데, 예를 들어 '카테고리'를 입력하면 알고리즘이 '범주' 단어로 자동 매칭해줍니다. "
            "결과는 4가지 status 로 자동 분류되고 추천 영문약어/도메인/타입/길이까지 채워줍니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 24 — 수정 모달
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "8-2. 자동 표준화 — 수정 모달 (cascade)",
               "행안부 지침 — '분류어' → '형식단어' 가시 텍스트 통일")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "모달 구조",
    (1, "단어 테이블 — 자동 분리 결과 + 수동 추가/제거"),
    (1, "형식단어 검색·자동완성 + [형식단어 추가] 버튼"),
    (1, "용어 도메인 — 마지막 단어의 분류명 cascade"),
    (1, "용어 미리보기 — 입력값으로 만들어질 한글/영문 즉시 표시"),
    "Cascade 동작",
    (1, "단어 테이블 변경 → 마지막 단어의 분류명 → 도메인 후보 자동 재로드"),
    (1, "마지막 단어가 형식단어가 아니면 도메인 비활성 + 빨간 안내"),
    "검증",
    (1, "test_ca8858d_clsf_domain.py — 10/10 PASS"),
])
add_notes(s, "이 수정 모달이 우리 시스템의 UX 차별화 중 하나예요. PARTIAL 이나 FAILED 행에서 [수정] 누르면 진입. "
            "단어 테이블에서 형식단어를 검색·자동완성으로 골라서 추가하면 마지막 단어의 분류명에 따라 도메인 드롭다운이 자동으로 cascade 됩니다. "
            "마지막 단어가 형식단어 (분류어) 가 아니면 도메인이 비활성 + 빨간 안내. "
            "한글/영문 미리보기가 실시간으로 갱신되니까 사용자가 결과를 즉시 확인. test_ca8858d_clsf_domain 10 케이스 PASS.")


# ─────────────────────────────────────────────────────────
# 슬라이드 25 — 데이터 품질 검증항목
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "9-1. 데이터 품질 진단 — 검증항목 (룰 정의)",
               "DQI / CTQ / BR + 도메인 룰 1:N + 컬럼 매핑")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "룰 종류",
    (1, "품질지표 (DQI) — 데이터 품질 지표"),
    (1, "핵심관리항목 (CTQ) — 비즈니스 우선순위 컬럼"),
    (1, "업무규칙 (BR) — 도메인 정합성"),
    "룰 카탈로그 (TB_QUAL_RULE_CATALOG)",
    (1, "이메일 / 주민번호 / 사업자번호 / 휴대전화번호 등 표준 정규식"),
    "도메인 룰 (1:N)",
    (1, "한 도메인에 여러 룰 매핑 (예: 이메일 도메인 + 정규식 + NOT NULL)"),
    "컬럼별 룰 매핑",
    (1, "effective rule = 도메인 룰 ∪ 컬럼 직접 매핑 — 1 SQL JOIN 으로 효과 룰 추출"),
])
add_notes(s, "데이터 품질 진단은 67/70번 설계의 결과로 신규 추가된 영역입니다. 룰을 도메인 단위로 정의해서 같은 도메인 컬럼에 일괄 적용하고, "
            "컬럼별로 룰을 직접 매핑할 수도 있습니다. effective rule 은 도메인 룰과 컬럼 매핑을 합집합으로 1 SQL 에 JOIN. "
            "룰 카탈로그에는 이메일·주민번호 같은 표준 정규식이 미리 들어있어서 즉시 사용 가능.")


# ─────────────────────────────────────────────────────────
# 슬라이드 26 — 검증대상 + 품질검증
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "9-2. 검증대상 + 품질검증 실행",
               "컬럼 매핑 + 값 프로파일 + 룰 위반 동시")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "검증대상 (DSQualColRule)",
    (1, "컬럼 그리드 → 효과 룰 (effective rule) 표시"),
    (1, "단위 재진단 — 한 컬럼만 즉시 재실행"),
    "품질검증 실행 (DSQualValueProfile)",
    (1, "모델 선택 → [DB 연결된 모델만] 필터 자동 (connectedOnly='Y')"),
    (1, "컬럼 체크 → [진단 시작]"),
    "처리 (q-executor BusinessRuleService + ValueProfileService)",
    (1, "값 프로파일 — null/distinct/min/max/avg/quantile 등"),
    (1, "룰 위반 검사 — RuleSqlBuilder dialect 분기 (Postgres/Oracle/Cubrid)"),
    "결과 저장",
    (1, "TB_QUAL_PROFILE_RESULT (UPSERT) + TB_QUAL_RULE_RESULT + TB_QUAL_VIOLATION_SAMPLE"),
])
add_notes(s, "검증대상 화면에서 컬럼별로 어떤 룰이 적용되는지 효과 룰을 확인하고, 단위 재진단으로 한 컬럼만 즉시 재실행 가능. "
            "품질검증 실행은 DB 연결된 모델만 자동 필터되니까 논리 모델은 안 보입니다. "
            "컬럼 체크해서 진단 시작하면 q-executor 가 값 프로파일과 룰 위반 검사를 동시 수행. "
            "RuleSqlBuilder 가 dialect 분기 — Postgres/Oracle/Cubrid 별로 정규식 표현이 달라서 그 부분 호환성 직접 작업.")


# ─────────────────────────────────────────────────────────
# 슬라이드 27 — 결과 + 시계열 + 위반 샘플
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "9-3. 데이터 품질 진단 — 결과 + 시계열",
               "위반율 / 위반 샘플 / 검증항목별 / 시계열 추이")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "품질검증 결과",
    (1, "진단별 위반율 + 위반 샘플 (PK + 위반값, 룰당 100건 default)"),
    (1, "drawer 상세 — 룰 텍스트 + 위반 행 그리드"),
    "테이블별 결과",
    (1, "테이블 단위 합계 + 컬럼별 위반율"),
    "검증항목별 결과",
    (1, "룰 단위 — 어떤 룰이 어디서 위반됐는지"),
    "데이터 품질 현황",
    (1, "시계열 — 진단 N회의 위반율 추이 차트"),
    "79번 진단 제외 (컬럼 단위)",
    (1, "QUAL_DIAG_TARGET_YN='N' 컬럼은 모수 자동 제외"),
    (1, "테이블 단위 cascade 는 67/70번 정식 통합 시 보강 예정"),
])
add_notes(s, "품질검증 결과 화면은 4가지 관점 — 진단 단위, 테이블 단위, 검증항목(룰) 단위, 시계열 추이. "
            "위반 샘플은 PK 와 위반값을 같이 보여주니까 운영자가 어떤 row 가 문제인지 즉시 확인 가능. 룰당 기본 100건. "
            "79번 진단 제외는 컬럼 단위로 즉시 적용되고, 테이블 단위 cascade 는 67/70번 정식 통합 시 보강 예정으로 메모돼있습니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 28 — 진단 스케줄 등록
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "10-1. 진단 스케줄 — 등록",
               "cron 표현식 + 진단 유형 + 즉시 실행 + 동시 실행 방어")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "스케줄 등록",
    (1, "[진단 스케줄] → [등록] 모달"),
    (1, "cron 표현식 (예: '0 0 9 * * MON-FRI' — 평일 9시)"),
    (1, "진단 유형 — STND / STRUCT / BOTH"),
    (1, "대상 모델 + 활성/비활성 토글"),
    "즉시 실행 (runNow)",
    (1, "테스트용 강제 트리거 — cron 무시 + 즉시 결과 row 생성"),
    "동시 실행 방어",
    (1, "같은 모델/유형 진행 중이면 SKIP (자동 검증)"),
    "권한",
    (1, "등록·수정·삭제·즉시 실행은 관리자 전용 (admin gate)"),
])
add_notes(s, "진단 스케줄러는 Phase 1~4 로 단계 구현했고 자동화 운영을 위한 핵심입니다. cron 표현식으로 스케줄 등록, "
            "진단 유형은 표준만/구조만/둘 다 셋 중 선택. 대상 모델 지정. 활성/비활성 토글로 일시 중단 가능. "
            "테스트할 때는 cron 기다리지 말고 즉시 실행 버튼으로 강제 트리거. "
            "같은 모델 같은 유형이 이미 돌고 있으면 자동으로 SKIP — 동시 실행 방어. 셀레니움으로 SKIP 정책까지 자동 검증.")


# ─────────────────────────────────────────────────────────
# 슬라이드 29 — 스케줄 실행 이력
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "10-2. 스케줄 실행 이력", "시간/모델/유형/상태/소요시간 + drawer 상세")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "그리드 컬럼",
    (1, "실행 시각 / 모델명 / 진단 유형 / 상태 (성공/실패/SKIP)"),
    (1, "소요 시간 / 실행 사용자 / 결과 카운트"),
    "필터",
    (1, "기간 (from/to) / 모델 / 유형 / 상태"),
    "Drawer 상세",
    (1, "행 클릭 → 상세 — 진단 ID 링크 (결과 화면으로 점프)"),
    (1, "에러 케이스 — 스택트레이스 + 원인 분석 단서"),
])
add_notes(s, "스케줄 실행 이력은 자동 진단의 운영 로그. 시각/모델/유형/상태/소요시간 전부 한 그리드. "
            "필터로 기간이나 상태별로 좁혀 보고, 행 클릭하면 drawer 가 열려서 진단 ID 링크로 진단 결과 화면 바로 점프. "
            "실패 케이스에는 에러 스택트레이스가 들어있어서 원인 추적 가능합니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 30 — 권한 (관리자 게이트)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "10-3. 진단 스케줄 — 권한 (관리자 게이트)",
               "일반 사용자는 조회만 — UI 비활성 + API 403 양쪽 검증")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "관리자 (isAdmin=true)",
    (1, "[등록] / [수정] / [삭제] / [즉시 실행] 버튼 노출"),
    (1, "스케줄 활성/비활성 토글 가능"),
    "일반 사용자",
    (1, "그리드 조회만 — 모든 액션 버튼 비활성"),
    (1, "API 직접 호출 시도 → 403 Forbidden (서버 검증)"),
    "검증",
    (1, "test_phase4_ui_admin_gate.py — 4/4 PASS"),
    (1, "test_perm_matrix.py — admin/user 권한 매트릭스 자동 검증"),
])
add_notes(s, "스케줄 자체는 일반 사용자도 조회 가능 (운영 투명성). 다만 등록/수정/삭제/즉시 실행은 관리자 전용. "
            "UI 비활성만으로는 부족하니까 API 도 서버 단에서 isAdmin 체크 후 403. "
            "셀레니움 phase4_ui_admin_gate 와 perm_matrix 두 테스트로 권한 매트릭스 자동 검증 — 회귀 즉시 감지.")


# ─────────────────────────────────────────────────────────
# 슬라이드 31 — 마이페이지
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "11-1. 마이페이지", "내 정보 + 요청 현황")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "내 정보",
    (1, "비밀번호 변경 (현재 비밀번호 검증 + 새 비밀번호 + 확인)"),
    (1, "마지막 로그인 시각"),
    "요청 현황 (DSMyRequest)",
    (1, "본인이 신청한 단어/용어/도메인 승인 상태"),
    (1, "카드 4개 — 전체 / 대기 / 승인 / 반려 (필터 클릭 가능)"),
    (1, "그리드 + 상세 패널 (반려 사유 표시)"),
    (1, "검색 — 유형 / 기간 (datetime range)"),
])
add_notes(s, "마이페이지는 사용자 본인이 신청한 항목들을 한눈에 보는 화면. 카드 4개에 전체/대기/승인/반려 카운트가 보이고 클릭하면 필터. "
            "행 클릭하면 상세 패널이 열려서 반려된 경우 사유까지 표시. 운영자가 자기 요청 진척도 빠르게 확인할 수 있어요.")


# ─────────────────────────────────────────────────────────
# 슬라이드 32 — 관리 (관리자 전용)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "11-2. 관리 (관리자 전용)", "사용자 + 승인 + 데이터 소스")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "사용자 관리",
    (1, "사용자 등록 / 비활성화 / 권한 변경 (admin 토글)"),
    (1, "비밀번호 초기화 (관리자 강제 리셋)"),
    "승인",
    (1, "행별 [승인]/[반려] 인라인 (사유 입력)"),
    (1, "단어 선승인 + cascade 반려 + 반려 후 물리 삭제 정책"),
    "데이터 소스",
    (1, "외부 DBMS 연결 정보 (jasypt 암호화)"),
    (1, "Oracle SID/Service Name 양쪽 지원 (drivers.xml swap)"),
    (1, "[연결 테스트] — 즉시 검증"),
    "메뉴 가시성",
    (1, "isAdmin=false → '관리' 그룹 자체 DOM 미렌더 (서버 + 클라이언트 양쪽)"),
])
add_notes(s, "관리 메뉴는 관리자 전용. 사용자 관리 / 승인 / 데이터 소스 3개. 데이터 소스가 핵심인데 외부 DBMS 연결 정보 등록하고 "
            "[연결 테스트] 로 즉시 확인. 비밀번호는 jasypt 로 암호화 저장. Oracle 은 SID 와 Service Name 둘 다 지원. "
            "관리 그룹은 isAdmin 체크해서 일반 사용자 화면에는 DOM 자체가 안 만들어집니다 — 클라이언트와 서버 양쪽 검증.")


# ─────────────────────────────────────────────────────────
# 슬라이드 33 — 마스터 러너
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "12-1. 셀레니움 자동화 회귀 — 마스터 러너",
               "29건 통합 테스트 자동 회귀 (~25분, 종료코드 0)")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "1 명령어 전체 실행",
    (1, "python dataQ설계/테스트/selenium/run_all.py"),
    "그룹별 진행",
    (1, "API/Login 5 / 표준 사전 5 / 데이터 모델 11 / 논리물리 3 / 진단 스케줄 4 / 79번 1"),
    "Per-test cleanup hook",
    (1, "DB 폴루션 자동 DELETE — ^(셀|테스트), 셀도메인%, IMSI_*"),
    (1, "좀비 Edge 프로세스 정리 — taskkill msedgedriver.exe"),
    (1, "→ 28건 순차 실행 시 누적 폴루션으로 인한 플레이키 0"),
    "변경 후 즉시 회귀",
    (1, "각 테스트 단일 실행 가능 — 변경 영향 영역만 빠르게 검증"),
])
add_notes(s, "셀레니움 통합 테스트 29건이 한 명령어로 자동 회귀. 약 25분 걸립니다. 종료코드 0 이면 모두 PASS. "
            "각 테스트 사이에 cleanup hook 이 DB 폴루션 자동 삭제하고 좀비 Edge 프로세스 정리 — 이걸 안 하면 누적 데이터로 플레이키 발생. "
            "변경 후엔 영향 영역의 단일 테스트만 빠르게 돌리고, 큰 변경 후엔 전체 회귀. 회귀 즉시 감지 패턴.")


# ─────────────────────────────────────────────────────────
# 슬라이드 34 — 회귀 결과
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "12-2. 회귀 결과 — 29 PASS / 0 FAIL",
               "오늘 마스터 러너 결과 (1675초, exit 0) — 동일 결과 2회 재현")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "그룹별 결과",
    (1, "API/Login (가벼움) — 5/5 PASS"),
    (1, "표준 사전 — 5/5 PASS (test_term_register_v2 v2 포함)"),
    (1, "데이터 모델 (Phase 5) — 11/11 PASS"),
    (1, "논리/물리 모델 진단 — 3/3 PASS"),
    (1, "진단 스케줄 (Phase 2~4) — 4/4 PASS"),
    (1, "진단 제외 관리 (79번) — 1/1 PASS (12 phase, 228초)"),
    "→ 합계 29 PASS / 0 FAIL / 1675초",
    "재현성",
    (1, "동일 환경에서 2회 연속 회귀 — 1624초 / 1675초 (편차 ±50초)"),
])
add_notes(s, "오늘 마스터 러너 회귀 — 29건 PASS 0 FAIL. 1675초 약 28분. 79번 진단 제외 관리는 12 phase 가 있어서 단일 테스트가 228초. "
            "표준 사전 그룹에 81/82번 용어 등록 v2 가 5번째로 추가됐고 8 케이스 PASS. "
            "동일 환경에서 2회 연속 동일 결과 — 1624초 / 1675초 (편차 ±50초). cleanup hook 으로 재현성 확보.")


# ─────────────────────────────────────────────────────────
# 슬라이드 35 — SMETA 비교 (강조)
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s)
add_title_band(s, "13. SMETA 대비 차별화 (75/76/77/78번 분석 결과)",
               "자동 표준화 + 진단 제외 관리 + 권한 자동 검증 + 한국 표준")
add_bullets(s, Inches(0.6), Inches(1.3), Inches(12.0), Inches(5.7), [
    "자동 표준화 엔진",
    (1, "DataQ — DP 점수 + 동의어 cascade + 사후처리 재검색 (자동)"),
    (1, "SMETA — 단어 분리 수동, 영문약어 사용자 직접 입력"),
    "진단 제외 관리",
    (1, "DataQ — 메뉴 화면 + cascade + 사유 + 변경 이력 (이번 세션 신규)"),
    (1, "SMETA — 미지원"),
    "권한 매트릭스 자동 검증",
    (1, "DataQ — test_perm_matrix / phase4_ui_admin_gate 자동 회귀"),
    (1, "SMETA — 수동 검증"),
    "한국 표준 직접 반영",
    (1, "DataQ — 행안부 형식단어, 분류어 → 형식단어 통일, 행안부 표준 단어 3,300건 시드"),
    (1, "SMETA — 일반 표준 (한국 특화 보강 별도)"),
])
add_notes(s, "75/76/77/78번 4개 문서로 SMETA 와 정밀 비교한 결과 4가지 차별화 영역. "
            "자동 표준화 엔진이 가장 큰 차이 — DataQ 는 한글 입력만으로 단어 분리, 영문약어, 도메인까지 자동인데 SMETA 는 수동. "
            "진단 제외 관리는 DataQ 만의 신규 기능. 권한 매트릭스도 DataQ 는 자동 셀레니움 회귀. "
            "그리고 한국 표준 — 행안부 형식단어 개념과 표준 단어 3,300건 시드까지 직접 반영했습니다.")


# ─────────────────────────────────────────────────────────
# 슬라이드 36 — 마무리 + Q&A
# ─────────────────────────────────────────────────────────
s = add_slide(); add_bg(s, ND_BLUE)
add_textbox(s, Inches(0.8), Inches(1.5), Inches(11.7), Inches(0.8),
            "Thank you", font_size=56, bold=True, color=ND_WHITE, align=PP_ALIGN.CENTER)
add_textbox(s, Inches(0.8), Inches(2.7), Inches(11.7), Inches(0.6),
            "Q & A", font_size=36, color=ND_WHITE, align=PP_ALIGN.CENTER)
add_textbox(s, Inches(0.8), Inches(4.0), Inches(11.7), Inches(2.5),
            ["오늘 시연한 영역 요약",
             "  • 모든 메뉴 (1~11) 핵심 기능 + 신규 기능 (79번 진단 제외, 81/82번 용어 등록 v2)",
             "  • 자동화 회귀 (29 PASS / 0 FAIL / 1624초)",
             "  • SMETA 대비 차별화 4 영역",
             "",
             "참조 문서",
             "  • dataQ설계/Narae_DataQ_사용자매뉴얼.md (v2.1)",
             "  • dataQ설계/테스트/통합테스트시연/ (그룹별 시연 절차서)",
             "  • dataQ설계/시연/종합시연_대본.md (본 발표 대본)"],
            font_size=16, color=RGBColor(0xC5, 0xCA, 0xE9), align=PP_ALIGN.CENTER)
add_notes(s, "이상 Narae DataQ 종합 시연 마칩니다. 오늘 좌측 메뉴 순서 그대로 모든 핵심 기능과 이번 세션 신규 기능 "
            "(79번 진단 제외, 81/82번 용어 등록 v2) 보여드렸고, 셀레니움 자동 회귀로 29 PASS / 0 FAIL 까지 검증된 상태입니다. "
            "SMETA 대비 차별화 4 영역도 정리. 질문 받겠습니다.")


# 저장
prs.save(OUT)
print(f"PPT 생성: {OUT}")
print(f"슬라이드 수: {len(prs.slides)}")
