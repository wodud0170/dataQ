# -*- coding: utf-8 -*-
"""사용자 매뉴얼 HTML 생성기.

특징
  - 스크린샷을 base64 로 내장 → 단일 파일. PDF 변환·공유·오프라인 열람이 모두 된다.
  - 각 기능에 검증 배지를 단다. 매뉴얼이 "있다고 주장하는 것" 과
    "실제로 검증된 것" 을 구분하는 게 이 문서의 핵심이다.

실행
  python build_manual.py
  → index.html

PDF
  msedge --headless --disable-gpu --no-pdf-header-footer \
         --print-to-pdf="Narae_DataQ_사용자매뉴얼_v3.pdf" "file:///<abs>/index.html"
"""
import base64
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")

# 검증 상태
V_OK = ("verified", "검증됨", "셀레니움 또는 API 로 실제 동작을 확인")
V_UI = ("uionly", "화면만", "화면 진입·렌더만 확인. 기능 동작은 미검증")
V_NO = ("none", "미구현", "매뉴얼에만 있고 코드에 없음")


def img(name):
    p = os.path.join(ASSETS, name + ".png")
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


# (앵커, 제목, 스크린샷, 검증상태, 설명 HTML, [주의사항])
SECTIONS = [
    ("login", "로그인", "00_login", V_OK,
     "<p>계정과 비밀번호로 로그인합니다. 권한은 <b>관리자</b>와 <b>일반 사용자</b> 두 가지이며, "
     "일반 사용자에게는 <b>관리</b> 메뉴 그룹이 아예 렌더되지 않습니다.</p>"
     "<ul><li>일반 사용자도 표준 사전 <b>등록 신청</b>은 가능합니다. 등록분은 미승인 상태로 저장되고 관리자 승인 후 조회됩니다.</li>"
     "<li>수정·삭제는 관리자 전용입니다. UI 뿐 아니라 서버에서도 재검증합니다.</li></ul>",
     ["비밀번호는 base64 로 인코딩해 전송합니다. 평문으로 보내면 응답은 200 이지만 인증은 실패합니다."]),

    ("dashboard", "대시보드", "01_dashboard", V_OK,
     "<p>표준 현황(용어·단어·도메인 건수), 선택한 데이터 모델의 표준 준수율·구조 일치율, "
     "구조 변경 감지 결과, 최근 변경 이력, 빠른 액션을 한 화면에서 봅니다.</p>"
     "<p><b>표준 준수율</b> = (전체 컬럼수 − 이슈 컬럼수) ÷ 전체 컬럼수 × 100. "
     "이슈 컬럼수는 이슈 <i>건수</i>가 아니라 <code>COUNT(DISTINCT 테이블.컬럼)</code> 입니다.</p>",
     ["용어 건수가 5자리가 되면 카드 폭을 넘어 숫자가 잘립니다 (예: 14,840 → '14840' 이 잘려 보임).",
      "준수율은 동명이 테이블이 있는 모델에서 부정확할 수 있습니다. 아래 '알려진 제약' 참조."]),

    ("word", "단어", "02_word", V_OK,
     "<p>표준 단어를 등록·수정·삭제하고 승인 상태를 관리합니다. 용어를 구성하는 최소 단위입니다.</p>"
     "<ul><li><b>영문약어</b>: <code>^[A-Z][A-Z0-9]*$</code> — 대문자로 시작, 대문자·숫자만</li>"
     "<li><b>영문명</b>: <code>^[A-Za-z][A-Za-z0-9]*$</code> — <b>공백·언더바 불허</b> (단어는 원자)</li>"
     "<li><b>형식단어</b>로 지정하면 도메인 분류명이 필수입니다</li>"
     "<li>다른 단어의 <b>금칙어</b>와 같은 이름은 등록이 차단됩니다</li>"
     "<li>기존 단어의 <b>이음동의어</b>와 같으면 등록은 되고 경고만 표시됩니다</li>"
     "<li>사용 중인 용어가 있으면 삭제가 차단되고 해당 용어명이 표시됩니다</li></ul>"
     "<p><b>영향도 분석</b> 버튼으로 이 단어를 쓰는 용어와 컬럼을 확인할 수 있습니다.</p>", []),

    ("term", "용어", "03_term", V_OK,
     "<p>단어를 조합해 표준 용어를 만듭니다. 컬럼 표준화의 기준이 됩니다.</p>"
     "<ul><li>용어명을 입력하면 <b>1초 뒤 자동 분석</b>이 돌아 구성 단어를 분리하고 영문약어를 채웁니다</li>"
     "<li><b>마지막 단어는 반드시 형식단어</b>여야 합니다</li>"
     "<li>마지막 단어의 약어가 <code>CD</code>면 <b>도메인 유형</b> 토글이 자동으로 나타나고 코드 선택기를 쓸 수 있습니다</li>"
     "<li>분석 결과에서 매칭된 단어는 <b>미리 선택된 상태</b>로 표시됩니다</li>"
     "<li>미등록 단어는 모달을 벗어나지 않고 인라인으로 등록할 수 있습니다</li></ul>", []),

    ("code", "코드", "04_code", V_UI,
     "<p>코드와 코드 항목(코드값)을 관리합니다. 코드 도메인 용어가 참조합니다.</p>",
     ["매뉴얼은 코드 일괄등록용 정적 xlsx 양식 제공을 기술하지만, 해당 기능은 없습니다."]),

    ("domain", "도메인", "05_domain", V_OK,
     "<p>데이터 타입·길이·소수점 등 물리 속성의 표준을 정의합니다. 용어가 도메인을 참조합니다.</p>"
     "<p>도메인명을 변경하면 이를 참조하는 용어의 도메인명이 <b>자동으로 따라 바뀝니다</b> (DB FK cascade). "
     "반대로 참조하는 용어가 있는 도메인은 삭제되지 않습니다.</p>", []),

    ("domain_group", "도메인 그룹", "06_domain_group", V_UI,
     "<p>도메인을 묶는 상위 분류입니다.</p>", []),

    ("domain_class", "도메인 분류", "07_domain_class", V_UI,
     "<p>형식단어와 도메인을 잇는 매핑입니다. 표준화 도구가 형식단어로 도메인을 추천할 때 씁니다.</p>", []),

    ("change_history", "변경 이력", "08_change_history", V_UI,
     "<p>표준 사전의 등록·수정·삭제 이력입니다. 대상 유형·변경 유형·기간으로 필터합니다.</p>"
     "<p><b>기록 시점 주의</b> — 관리자가 직접 등록하면 등록 시점에, 일반 사용자가 신청한 건은 "
     "<b>관리자 승인 시점</b>에 기록됩니다. 반려는 이력을 남기지 않습니다.</p>", []),

    ("dm_collection", "데이터 모델 수집", "09_dm_collection", V_OK,
     "<p>데이터소스에 접속해 테이블·컬럼·인덱스·제약조건 메타데이터를 수집합니다.</p>"
     "<ul><li>수집 범위(스키마)를 먼저 지정합니다</li>"
     "<li>재수집은 <b>MERGE</b> 입니다 — 물리명이 같으면 덮어쓰고, 이번에 없는 기존 행은 <b>그대로 보존</b>합니다</li>"
     "<li>사용자가 입력한 <b>한글명·표준 flag·진단 제외 설정은 재수집해도 보존</b>됩니다</li></ul>",
     ["인덱스·제약조건 수집은 현재 <b>Oracle 전용</b>입니다. PostgreSQL 수집 쿼리는 없습니다.",
      "같은 모델을 동시에 수집하는 것을 막는 장치가 없습니다."]),

    ("dm_status", "데이터 모델 현황", "10_dm_status", V_OK,
     "<p>모델별 테이블·컬럼 수와 표준 준수율, 진단 결과를 봅니다. <b>DDL 다운로드</b>로 "
     "CREATE TABLE + PK/FK + COMMENT + TABLESPACE 구문을 받을 수 있습니다.</p>", []),

    ("dm_table", "테이블", "11_dm_table", V_OK,
     "<p>수집된 테이블 목록입니다. 한글명·소유자·설명·테이블스페이스·업무영역을 편집합니다.</p>"
     "<p>물리명을 바꾸면 하위 컬럼·인덱스·제약조건·참조 제약이 <b>함께 갱신</b>되며, "
     "[수정] 전에 영향 범위를 미리 볼 수 있습니다.</p>", []),

    ("dm_column", "컬럼", "12_dm_column", V_OK,
     "<p>컬럼 목록과 그리드 편집 화면입니다.</p>"
     "<ul><li>행을 인라인으로 추가하고 <b>TSV 붙여넣기</b>로 여러 행을 한 번에 채울 수 있습니다</li>"
     "<li><b>한글명 기준 표준화</b> — 한글명으로 표준 용어를 찾아 영문명·타입·길이를 채웁니다</li>"
     "<li><b>영문명 기준 표준화</b> — 영문약어로 용어를 찾아 한글명·타입·길이를 채웁니다</li>"
     "<li>변환 실패 시 <b>변환 불가 사유</b>가 표시됩니다</li>"
     "<li>저장은 배치 트랜잭션입니다. 한 건이라도 실패하면 전체가 롤백됩니다</li></ul>", []),

    ("dm_index", "인덱스", "13_dm_index", V_UI,
     "<p>수집된 인덱스입니다. 조회 전용입니다.</p>", []),

    ("dm_constraint", "제약조건", "14_dm_constraint", V_UI,
     "<p>PK/FK/UNIQUE/CHECK 제약조건입니다. 유형으로 필터할 수 있습니다.</p>",
     ["매뉴얼은 FK 갱신규칙(UPDATE_RULE) 표시를 기술하지만 수집·화면 모두 삭제규칙만 다룹니다."]),

    ("dm_history", "데이터 모델 수집이력", "15_dm_history", V_UI,
     "<p>회차별 수집 로그입니다. 시스템·모델·수집일로 필터합니다.</p>", []),

    ("diag_target", "진단 제외 관리", "16_diag_target", V_OK,
     "<p>테이블·컬럼 단위로 표준/구조/품질 진단 대상 여부를 켜고 끕니다. 관리자 전용입니다.</p>"
     "<p>테이블을 끄면 <b>하위 컬럼이 자동으로 함께 제외</b>됩니다. 끌 때 사유를 남길 수 있고, "
     "다시 켜면 사유는 지워집니다.</p>",
     ["품질 진단은 이 설정을 아직 참조하지 않습니다 (표준·구조 진단만 반영)."]),

    ("erwin_import", "모델링 도구 임포트", "17_erwin_import", V_OK,
     "<p>ERwin XML 과 XMI 2.1 파일을 읽어 데이터 모델로 적재합니다. 파싱 결과를 먼저 미리 보고 임포트합니다. "
     "반대로 <b>XMI 내보내기</b>도 됩니다.</p>",
     ["매뉴얼은 임포트 전 정규식·PK/FK 일관성·표준 매칭률 사전 검증을 기술하지만, 실제로는 파싱 결과만 반환합니다."]),

    ("dm_visualization", "데이터 모델 시각화 (ERD)", "18_dm_visualization", V_UI,
     "<p>테이블과 FK 관계를 ERD 로 그립니다. 우측 상단에 테이블·컬럼·관계(FK) 수가 표시됩니다.</p>"
     "<ul><li><b>레이아웃</b> — 계층(FK 방향으로 층을 쌓음) / 자율 배치(2D 로 펼침)</li>"
     "<li><b>표시</b> — 전체 / 영문 / 한글</li>"
     "<li><b>테이블 검색</b>으로 특정 테이블을 찾고, <b>맞춤</b>으로 화면에 맞춰 배율을 되돌립니다</li>"
     "<li>PNG·PDF 로 내보낼 수 있습니다</li></ul>"
     "<p>테이블이 20개를 넘으면 계층 레이아웃은 세로로 길어져 배율이 크게 작아집니다. "
     "이때는 <b>자율 배치</b>가 읽기 좋습니다 (위 그림이 자율 배치입니다).</p>",
     ["매뉴얼이 기술한 업무영역 필터는 없습니다. 업무영역은 그룹핑에만 쓰입니다."]),

    ("dm_change_history", "데이터 모델 변경 이력", "19_dm_change_history", V_OK,
     "<p>테이블·컬럼 변경 이력과 승인 상태입니다. 관리자는 전체를, 일반 사용자는 본인 것과 승인된 것만 봅니다. "
     "모델 ID·사용자 ID·Tier·상태로 거를 수 있습니다.</p>"
     "<p><b>Tier</b> 는 변경의 파급 범위이자 승인 정책입니다.</p>"
     "<ul><li><code>TIER1</code> — 스키마에 영향을 주는 변경(테이블·컬럼 추가/삭제 등). "
     "일반 사용자가 하면 초안·신청 단계를 거치고, 관리자가 하면 즉시 승인됩니다</li>"
     "<li><code>TIER1_5</code> — 컬럼 순서 변경. <b>일반 사용자는 UI 에서 차단</b>되고 관리자만 할 수 있으며, 즉시 승인됩니다</li>"
     "<li><code>TIER2</code> — 한글명·설명 같은 메타데이터 변경. 누가 하든 즉시 승인됩니다</li></ul>"
     "<p>DDL 이 필요한 유형에는 <b>DDL 조각</b>이 함께 생성돼 [복사] 하거나 [DB 반영] 할 수 있습니다.</p>", []),

    ("diag_execute", "표준 진단 실행", "20_diag_execute", V_OK,
     "<p>수집된 컬럼을 표준 사전과 대조합니다. 같은 모델에 진행 중인 진단이 있으면 거부됩니다.</p>"
     "<p>진단이 정상 완료되면 컬럼의 <b>표준 flag 가 자동 갱신</b>됩니다 — 이슈가 하나라도 있으면 '비표준', 없으면 '표준'.</p>", []),

    ("diag_result", "표준 진단 결과", "21_diag_result", V_OK,
     "<p>이슈를 4가지로 분류합니다.</p>"
     "<ul><li><b>용어 미존재</b> — 컬럼 영문명에 해당하는 표준 용어가 없음</li>"
     "<li><b>한글명 불일치</b> — 용어는 있으나 한글명이 다름</li>"
     "<li><b>타입 불일치</b> — 도메인이 정한 타입과 다름 (VARCHAR↔VARCHAR2 같은 동의어는 불일치 아님)</li>"
     "<li><b>길이 불일치</b> — 도메인이 정한 길이와 다름</li></ul>"
     "<p>결과에서 곧바로 용어를 등록하거나, <code>COMMENT ON</code>·컬럼 표준화 DDL 스크립트를 생성할 수 있습니다.</p>",
     ["동명이 테이블이 있는 모델에서 집계가 부정확합니다. 아래 '알려진 제약' 참조."]),

    ("struct_diag", "구조 변경 진단", "22_struct_diag", V_OK,
     "<p>수집 시점 스냅샷과 현재 운영 DB 스키마를 비교해 변경을 찾습니다. "
     "데이터소스가 없는 논리 모델은 대상이 아닙니다.</p>", []),

    ("struct_diag_result", "구조 변경 진단 결과", "23_struct_diag_result", V_UI,
     "<p>데이터 모델과 진단 이력을 고르면 결과가 나옵니다. 상단에 전체 테이블·컬럼·인덱스·제약조건 수와 "
     "변경 항목 수가 요약되고, 아래는 <b>컬럼 변경 / 인덱스 변경 / 제약조건 변경</b> 탭으로 나뉩니다.</p>"
     "<p>각 탭에서 <b>전체 / 추가 / 변경 / 삭제</b>로 다시 거를 수 있고, 컬럼 변경은 이전·현재의 "
     "타입·길이·Nullable 을 나란히 보여줍니다. 탭 단위로 엑셀 다운로드가 됩니다.</p>"
     "<p>이 화면은 <b>오너(스키마)를 구분해 표시</b>합니다. 이름이 같고 스키마가 다른 테이블도 각각 나옵니다.</p>", []),

    ("term_recommend", "한글컬럼 일괄 표준화", "24_term_recommend", V_OK,
     "<p>한글 컬럼명 목록을 넣으면 단어로 분리하고 영문약어·도메인을 추천합니다. 3단계로 진행합니다.</p>"
     "<ol><li><b>입력</b> — 엑셀 드래그앤드롭 / 붙여넣기 / 직접 입력</li>"
     "<li><b>분석</b> — 기등록 / 자동완성 / 부분매칭 / 미매칭으로 분류</li>"
     "<li><b>검토·등록</b> — 등록 가능한 행만 활성화되고, 불가한 행에는 사유 칩이 붙습니다</li></ol>",
     ["매뉴얼의 'Step 4 완료 화면 + DDL 생성' 은 없습니다. 3단계가 끝입니다."]),

    ("term_resolve_history", "한글 변환 이력", "25_term_resolve_history", V_UI,
     "<p>한글명 → 영문약어 변환 이력입니다. 매핑 정의서용 엑셀로 받을 수 있습니다.</p>", []),

    ("board_notice", "공지사항", "26_board_notice", V_UI,
     "<p>공지 게시판입니다. 상단 고정(PIN), 첨부파일, 댓글을 지원합니다.</p>", []),

    ("board_qna", "Q&amp;A", "27_board_qna", V_UI,
     "<p>질의응답 게시판입니다. 본인 또는 관리자만 수정·삭제할 수 있습니다.</p>", []),

    ("schedule_manage", "스케줄 관리", "28_schedule_manage", V_OK,
     "<p>표준 진단·구조 진단을 자동 실행하도록 예약합니다. 관리자 전용입니다.</p>"
     "<ul><li><b>간편 설정</b>(매일/매주/매월 + 시각) 또는 <b>Cron 표현식</b></li>"
     "<li>Cron 은 다음 5회 실행 시각을 미리 보여줍니다</li>"
     "<li><b>BOTH</b> 로 지정하면 표준·구조 진단이 함께 돌고, 둘 다 끝나야 완료로 마감됩니다</li>"
     "<li>같은 모델·유형이 이미 실행 중이면 건너뛰고 SKIPPED 로 기록합니다</li></ul>", []),

    ("schedule_log", "스케줄 실행 이력", "29_schedule_log", V_OK,
     "<p>실행 결과입니다. 실패 사유에는 원인을 나타내는 접두어가 붙습니다 — "
     "<code>[CONFIG]</code> 설정 문제, <code>[DB]</code> 접속 문제, <code>[DATA_NOT_FOUND]</code> 수집 이력 없음, "
     "<code>[TIMEOUT]</code> 60분 초과, <code>[SKIPPED]</code> 중복 방지.</p>"
     "<p>스케줄을 삭제해도 이력의 스케줄명은 보존됩니다.</p>", []),

    ("my_profile", "내 정보", "30_my_profile", V_UI,
     "<p>본인 정보 확인과 비밀번호 변경입니다.</p>", []),

    ("my_request", "표준 사전 변경 요청 현황", "31_my_request", V_OK,
     "<p>본인이 신청한 표준 사전 등록 건의 상태입니다. 전체·승인대기·승인완료·반려 카드로 집계됩니다.</p>", []),

    ("my_dm_changes", "내 변경 신청", "32_my_dm_changes", V_OK,
     "<p>본인이 만든 데이터 모델 변경 초안(DRAFT)입니다. 묶어서 신청하거나 되돌릴 수 있습니다.</p>",
     ["매뉴얼이 기술한 '신청 사유 입력' 과 '제출 후 회수' 는 없습니다."]),

    ("admin_approval", "표준 사전 변경 승인", "33_admin_approval", V_OK,
     "<p>일반 사용자가 신청한 단어·용어·도메인을 승인하거나 반려합니다.</p>"
     "<ul><li><b>용어 승인 가드</b> — 구성 단어 중 미승인이 있으면 승인이 거부되고 해당 단어명이 표시됩니다</li>"
     "<li><b>연쇄 반려</b> — 단어를 반려하면 그 단어를 쓰는 <b>미승인</b> 용어가 함께 삭제됩니다. 반려 전에 대상이 안내됩니다</li>"
     "<li>승인된 용어가 그 단어를 쓰고 있으면 반려가 차단됩니다 (먼저 용어를 정리해야 합니다)</li>"
     "<li>반려된 항목은 삭제되므로 <b>같은 이름으로 다시 등록</b>할 수 있습니다</li></ul>", []),

    ("admin_dm_approval", "데이터 모델 변경 승인", "34_admin_dm_approval", V_OK,
     "<p>신청 묶음 단위로 데이터 모델 변경을 승인·반려합니다.</p>"
     "<p>테이블을 반려하면 그 테이블의 대기 중 컬럼이, 컬럼을 반려하면 이를 참조하는 대기 중 컬럼이 함께 반려됩니다.</p>",
     ["연쇄 반려는 1단계까지만 전파됩니다.",
      "컬럼 삭제·수정은 승인 전에 이미 반영되므로 반려해도 되돌아오지 않습니다."]),

    ("admin_area", "영역 관리", "35_admin_area", V_UI,
     "<p>업무영역을 등록·수정·삭제합니다. 테이블에 매핑해 분류·필터에 씁니다.</p>",
     ["매뉴얼이 기술한 주제영역 탭, 영역 코드, 색상 지정은 화면에 없습니다."]),

    ("admin_datasource", "데이터 소스", "36_admin_datasource", V_OK,
     "<p>수집 대상 DB 접속 정보를 등록하고 연결을 테스트합니다. "
     "Oracle 은 <b>SID</b> 와 <b>Service Name</b> 두 방식을 모두 지원합니다.</p>", []),

    ("admin_user", "사용자", "37_admin_user", V_UI,
     "<p>계정을 등록·수정·삭제하고 관리자 권한을 부여합니다. 비밀번호 초기화도 여기서 합니다.</p>",
     ["매뉴얼이 기술한 '부서' 와 '상태(활성/비활성)' 입력란은 없습니다."]),
]

KNOWN_ISSUES = [
    ("진단 결과가 동명이 테이블을 구분하지 못함",
     "진단 결과 테이블에 소유자(스키마) 정보가 없어, 이름이 같고 스키마가 다른 테이블이 서로의 이슈를 "
     "그대로 물려받습니다. 오라클테스트 모델(스키마 3개)에서 <code>TB_USER</code> 의 실제 이슈는 "
     "11컬럼·14건 <b>1건</b>인데 화면에는 스키마마다 같은 수치가 <b>3번</b> 표시됩니다. "
     "그 결과 '전체 테이블 19개 / 이슈 테이블 22개' 처럼 이슈가 전체보다 많은 표시가 나옵니다. "
     "표준 준수율도 다른 스키마의 같은 이름 컬럼을 하나로 합쳐 세므로 부정확할 수 있습니다.",
     "다중 스키마를 한 모델로 수집한 경우. 단일 스키마 모델은 영향 없음."),

    ("대시보드 용어 카드의 숫자 잘림",
     "용어 건수가 5자리가 되면 카드 폭을 넘어 마지막 자리가 잘려 보입니다.", "용어 10,000건 이상"),

    ("인덱스·제약조건 수집은 Oracle 전용",
     "매뉴얼은 9종 DBMS 를 표방하지만 실제 수집 쿼리는 Oracle(테이블·컬럼·인덱스·제약), "
     "MariaDB(테이블·컬럼), Cubrid(테이블·컬럼) 뿐입니다. <b>PostgreSQL 수집 쿼리는 없습니다.</b>", "Oracle 외 DBMS"),

    ("컬럼 단건 편집은 승인 절차를 타지 않음",
     "그리드 배치 저장은 승인 절차를 따르지만, 컬럼 단건 추가·삭제와 PK/FK 생성·삭제는 "
     "이력도 남지 않고 즉시 반영됩니다.", "거버넌스를 엄격히 적용해야 하는 환경"),

    ("품질 진단 메뉴 비활성",
     "데이터 품질 진단(도메인 룰·값 프로파일링·업무 규칙) 메뉴는 영업 라인업 분리 결정으로 "
     "화면에서 제거돼 있습니다. 서버 기능은 살아 있습니다.", "해당 메뉴 사용 시"),
]

NOT_IMPLEMENTED = [
    ("종합 분석 보고서", "설계 문서에 20개 시트·5개 프리셋으로 정의돼 있으나 코드가 없습니다."),
    ("표준화 도구 4단계 (DDL 생성)", "3단계까지만 있습니다. DBMS 선택 후 CREATE TABLE 생성 기능은 없습니다."),
    ("진단 제외 패턴 룰", "이름 규칙으로 일괄 제외하는 기능은 없습니다. 개별 토글만 가능합니다."),
    ("변경 신청 사유 입력 / 제출 후 회수", "신청 시 사유를 남기거나 제출한 신청을 되돌리는 경로가 없습니다."),
    ("주제영역 관리 화면", "서버 기능은 있으나 화면에 진입 경로가 없습니다."),
    ("영역 코드·색상 지정", "해당 입력 항목이 없습니다."),
    ("사용자 부서·상태 관리", "해당 입력 항목이 없습니다."),
    ("제약조건 갱신규칙 표시", "삭제규칙만 다룹니다."),
    ("코드 일괄등록 양식 다운로드", "코드 화면에 양식 다운로드가 없습니다."),
]

CSS = """
:root{
  --ground:#FAFAFC; --surface:#FFFFFF; --surface-2:#F2F4FA;
  --ink:#1A1D28; --ink-2:#5A6178; --line:#DFE2EC;
  --accent:#3D4DB7; --accent-soft:#ECEFFB;
  --ok:#186B45;  --ok-bg:#E7F4ED;  --ok-line:#9FD2BA;
  --warn:#8A4B00; --warn-bg:#FDF1E2; --warn-line:#E6BE8B;
  --stop:#A32218; --stop-bg:#FBEAE8; --stop-line:#E4A79F;
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
    --stop:#F0918A; --stop-bg:#331A18; --stop-line:#6B322D;
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
  --stop:#F0918A; --stop-bg:#331A18; --stop-line:#6B322D;
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
ul,ol{padding-left:19px;margin:9px 0}
li{margin:4px 0}

.wrap{max-width:940px;margin:0 auto;padding:0 18px 90px}
@media(min-width:760px){ body{font-size:15.5px} .wrap{padding:0 30px 120px} }

.cover{padding:52px 0 32px;border-bottom:1px solid var(--line)}
.eyebrow{font-family:"IBM Plex Mono",monospace;font-size:11px;font-weight:500;
  letter-spacing:.15em;text-transform:uppercase;color:var(--accent)}
.cover h1{font-size:clamp(29px,7.2vw,44px);line-height:1.16;letter-spacing:-.028em;
  font-weight:600;margin:12px 0 12px;text-wrap:balance}
.lede{color:var(--ink-2);font-size:clamp(14.5px,3.6vw,17px);margin:0 0 26px;max-width:36em}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(132px,1fr));gap:10px}
.stat{background:var(--surface);border:1px solid var(--line);border-radius:12px;padding:13px 15px}
.stat .n{font-family:"IBM Plex Mono",monospace;font-size:25px;font-weight:600;
  font-variant-numeric:tabular-nums;line-height:1.1;letter-spacing:-.02em}
.stat .l{font-size:12px;color:var(--ink-2);margin-top:4px;line-height:1.45}
.stat.ok .n{color:var(--ok)} .stat.warn .n{color:var(--warn)} .stat.stop .n{color:var(--stop)}

h2{font-size:clamp(20px,4.8vw,26px);font-weight:600;letter-spacing:-.022em;
  margin:64px 0 12px;padding-bottom:10px;border-bottom:1px solid var(--line);text-wrap:balance}

.screen{--rail:var(--line);border-left:3px solid var(--rail);
  padding:1px 0 1px 15px;margin:36px 0}
@media(min-width:760px){ .screen{padding-left:22px} }
.screen.ok{--rail:var(--ok-line)}
.screen.ui{--rail:var(--warn-line)}
.screen h3{font-size:clamp(17px,4vw,20px);font-weight:600;letter-spacing:-.018em;
  margin:0 0 9px;display:flex;gap:9px;align-items:center;flex-wrap:wrap;text-wrap:balance}

.chip{font-family:"IBM Plex Mono",monospace;font-size:10px;font-weight:500;
  letter-spacing:.09em;text-transform:uppercase;padding:3px 8px;border-radius:999px;white-space:nowrap}
.chip.ok{color:var(--ok);background:var(--ok-bg);border:1px solid var(--ok-line)}
.chip.ui{color:var(--warn);background:var(--warn-bg);border:1px solid var(--warn-line)}
.chip.no{color:var(--stop);background:var(--stop-bg);border:1px solid var(--stop-line)}

figure{margin:16px 0 4px;border:1px solid var(--line);border-radius:12px;
  overflow:hidden;background:var(--surface);box-shadow:var(--shadow)}
figure img{display:block;width:100%;height:auto}

.note{margin:14px 0;background:var(--note-bg);border:1px solid var(--note-line);
  border-radius:10px;padding:11px 14px;font-size:.93em}
.note .h{font-family:"IBM Plex Mono",monospace;font-size:10px;font-weight:600;
  letter-spacing:.1em;text-transform:uppercase;color:var(--warn);margin-bottom:5px}
.note ul{margin:0;padding-left:17px}

.tw{overflow-x:auto;-webkit-overflow-scrolling:touch;margin:16px 0;
  border:1px solid var(--line);border-radius:12px;background:var(--surface)}
table{width:100%;min-width:500px;border-collapse:collapse;font-size:.93em}
th,td{padding:11px 14px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}
tbody tr:last-child td{border-bottom:0}
th{background:var(--surface-2);font-weight:600;font-size:12.5px;letter-spacing:.01em}

.toc{background:var(--surface);border:1px solid var(--line);border-radius:14px;
  padding:16px 18px;margin:24px 0}
.toc ol{list-style:none;margin:0;padding:0;display:grid;gap:0}
@media(min-width:640px){ .toc ol{grid-template-columns:1fr 1fr;column-gap:28px} }
.toc a{display:flex;justify-content:space-between;gap:12px;align-items:baseline;
  padding:6px 0;color:var(--ink);text-decoration:none;font-size:14.5px;
  border-bottom:1px solid var(--line)}
.toc a:hover{color:var(--accent)}
.toc .st{font-family:"IBM Plex Mono",monospace;font-size:10px;letter-spacing:.07em;
  color:var(--ink-2);flex:none}
.toc .st.ok{color:var(--ok)} .toc .st.ui{color:var(--warn)}

.legend{display:grid;gap:10px;margin:16px 0}
@media(min-width:640px){ .legend{grid-template-columns:repeat(3,1fr)} }
.legend div{border:1px solid var(--line);border-radius:11px;padding:13px 15px;
  background:var(--surface);font-size:13.5px;line-height:1.6}
.legend .d{color:var(--ink-2);display:block;margin-top:6px}

.flow{background:var(--surface);border:1px solid var(--line);border-radius:12px;
  padding:15px;margin:16px 0;overflow-x:auto;-webkit-overflow-scrolling:touch;
  font-family:"IBM Plex Mono",monospace;font-size:12px;white-space:pre;line-height:1.85}
pre.mermaid{background:var(--surface);border:1px solid var(--line);border-radius:12px;
  padding:14px;margin:16px 0;overflow-x:auto;-webkit-overflow-scrolling:touch}

footer{margin-top:72px;padding-top:20px;border-top:1px solid var(--line);
  color:var(--ink-2);font-size:12.5px;line-height:1.7}

@media(prefers-reduced-motion:reduce){ *{animation:none!important;transition:none!important} }

@media print{
  *{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  body{background:#fff;font-size:10.5px;line-height:1.62}
  .wrap{max-width:none;padding:0 9mm}
  .cover{padding-top:20px;page-break-after:always}
  h2{margin-top:26px;page-break-after:avoid}
  .screen{margin:20px 0}
  .screen h3{page-break-after:avoid}
  figure,.note,.tw,.stat,.legend div{page-break-inside:avoid}
  figure{box-shadow:none}
  a{color:inherit;text-decoration:none}
  .toc ol{grid-template-columns:1fr 1fr}
}
"""

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
         '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
         'family=IBM+Plex+Mono:wght@400;500;600&'
         'family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">')

FLOW_APPROVE_ASCII = (
    "일반 사용자              관리자\n"
    "     |\n"
    "  [등록 신청] ---------> 승인 대기 목록\n"
    "     |                       |\n"
    "     |                  +----+----+\n"
    "     |                [승인]    [반려]\n"
    "     |                  |          |\n"
    "     |            사전에 반영   행 삭제 + 사유 기록\n"
    "     |            변경이력 기록      |\n"
    "     |                          연쇄: 이 단어를 쓰는\n"
    "     <-----------------------  미승인 용어도 함께 삭제\n"
    "  같은 이름으로 재등록 가능        (승인된 용어는 보존)")

FLOW_APPROVE_MMD = """flowchart TD
  A["일반 사용자<br>등록 신청"] --> B["승인 대기 목록"]
  B --> C{"관리자 검토"}
  C -->|승인| D["사전에 반영<br>변경이력 기록"]
  C -->|반려| E["행 삭제 + 사유 기록"]
  E --> F["연쇄 삭제: 이 단어를 쓰는 미승인 용어<br>(승인된 용어는 보존)"]
  E --> G["같은 이름으로 재등록 가능"]"""

FLOW_GOV_ASCII = (
    "일반 사용자                        관리자\n"
    "     |\n"
    "  컬럼 추가 --> 초안(DRAFT)\n"
    "                  |  * 본인에게만 보임\n"
    "            [묶어서 신청]\n"
    "                  |\n"
    "              신청(SUBMITTED) --> 변경 승인 화면\n"
    "                                       |\n"
    "                                  +----+----+\n"
    "                                [승인]    [반려]\n"
    "                                  |          |\n"
    "                            전원에게 노출   연쇄 반려\n"
    "                            DDL 조각 생성   (1단계까지)\n"
    "                                  |\n"
    "                            [복사] 또는 [DB 반영]")

FLOW_GOV_MMD = """flowchart TD
  A["일반 사용자<br>컬럼 추가"] --> B["초안 DRAFT<br>본인에게만 보임"]
  B --> C["묶어서 신청"]
  C --> D["신청 SUBMITTED"]
  D --> E{"관리자 검토"}
  E -->|승인| F["전원에게 노출<br>DDL 조각 생성"]
  E -->|반려| G["연쇄 반려 (1단계까지)"]
  F --> H["복사 또는 DB 반영"]"""


def flow(ascii_art, mermaid_src, artifact):
    """아티팩트는 mermaid 로 렌더된다. 로컬 단일 파일은 렌더러가 없으므로 ASCII."""
    if artifact:
        return '<pre class="mermaid">%s</pre>' % mermaid_src
    return '<div class="flow">%s</div>' % ascii_art


def build(artifact=False):
    ok = sum(1 for s in SECTIONS if s[3] is V_OK)
    ui = sum(1 for s in SECTIONS if s[3] is V_UI)
    p = []
    if not artifact:
        p.append('<meta charset="utf-8">')
        p.append('<meta name="viewport" content="width=device-width,initial-scale=1">')
    p.append("<title>Narae DataQ 사용자 매뉴얼</title>")
    p.append(FONTS)
    p.append("<style>%s</style>" % CSS)
    p.append('<div class="wrap">')

    p.append('<header class="cover">')
    p.append('<div class="eyebrow">User Manual &middot; v3.0</div>')
    p.append("<h1>Narae DataQ 사용자 매뉴얼</h1>")
    p.append('<p class="lede">데이터 표준 관리 · 모델 수집 · 품질 진단 플랫폼. '
             '모든 화면을 현재 빌드에서 다시 촬영하고, 각 기능이 실제로 검증됐는지 함께 표시했습니다.</p>')
    p.append('<div class="stats">')
    p.append('<div class="stat"><div class="n">%d</div><div class="l">문서에 담긴 화면</div></div>' % len(SECTIONS))
    p.append('<div class="stat ok"><div class="n">%d</div><div class="l">동작까지 검증된 화면</div></div>' % ok)
    p.append('<div class="stat warn"><div class="n">%d</div><div class="l">렌더만 확인된 화면</div></div>' % ui)
    p.append('<div class="stat stop"><div class="n">%d</div><div class="l">매뉴얼에만 있던 기능</div></div>'
             % len(NOT_IMPLEMENTED))
    p.append("</div></header>")

    p.append("<h2>이 문서를 읽는 법</h2>")
    p.append("<p>이전 매뉴얼은 설계 단계의 계획까지 함께 적혀 있어, 실제로 없는 기능이 여러 곳에 남아 있었습니다. "
             "이 문서는 <b>확인된 것과 확인되지 않은 것을 나눠 씁니다.</b> "
             "화면 제목 옆 표식과 왼쪽 세로선 색이 그 구분입니다.</p>")
    p.append('<div class="legend">')
    p.append('<div><span class="chip ok">검증됨</span>'
             '<span class="d">셀레니움 또는 API 로 실제 동작을 확인했습니다.</span></div>')
    p.append('<div><span class="chip ui">화면만</span>'
             '<span class="d">화면 진입과 렌더까지만 확인했습니다. 기능 동작은 아직 테스트가 없습니다.</span></div>')
    p.append('<div><span class="chip no">미구현</span>'
             '<span class="d">매뉴얼에는 있으나 코드에 없습니다. 맨 뒤에 따로 모았습니다.</span></div>')
    p.append("</div>")
    p.append("<p>노란 상자는 <b>주의사항</b>입니다. 대부분은 이전 매뉴얼이 있다고 적었지만 실제로는 없는 기능, "
             "또는 알아두지 않으면 오해하기 쉬운 동작입니다.</p>")
    p.append("<p>스크린샷은 개발 환경에서 찍었습니다. 건수와 이름은 실제 운영과 다릅니다.</p>")

    p.append('<nav class="toc"><ol>')
    for anc, title, _, state, _, _ in SECTIONS:
        st = "ok" if state is V_OK else "ui"
        lbl = "검증" if state is V_OK else "화면"
        p.append('<li><a href="#%s"><span>%s</span><span class="st %s">%s</span></a></li>'
                 % (anc, title, st, lbl))
    p.append('<li><a href="#workflow"><span>업무 흐름</span><span class="st">2</span></a></li>')
    p.append('<li><a href="#known"><span>알려진 제약</span><span class="st">%d</span></a></li>'
             % len(KNOWN_ISSUES))
    p.append('<li><a href="#notimpl"><span>미구현 기능</span><span class="st">%d</span></a></li>'
             % len(NOT_IMPLEMENTED))
    p.append("</ol></nav>")

    p.append("<h2>화면별 안내</h2>")
    missing = []
    for anc, title, shot, state, body, notes in SECTIONS:
        cls = "ok" if state is V_OK else "ui"
        p.append('<section class="screen %s" id="%s">' % (cls, anc))
        p.append('<h3>%s <span class="chip %s">%s</span></h3>' % (title, cls, state[1]))
        p.append(body)
        d = img(shot)
        if d:
            p.append('<figure><img src="%s" alt="%s 화면" loading="lazy"></figure>' % (d, title))
        else:
            missing.append(shot)
        if notes:
            p.append('<div class="note"><div class="h">주의</div><ul>')
            for n in notes:
                p.append("<li>%s</li>" % n)
            p.append("</ul></div>")
        p.append("</section>")

    p.append('<h2 id="workflow">업무 흐름</h2>')
    p.append("<h3>표준 사전 승인</h3>")
    p.append(flow(FLOW_APPROVE_ASCII, FLOW_APPROVE_MMD, artifact))
    p.append("<p><b>용어 승인 가드</b> — 용어를 승인하려면 구성 단어가 모두 승인돼 있어야 합니다. "
             "미승인 단어가 있으면 승인이 거부되고 어떤 단어가 걸렸는지 표시됩니다.</p>")
    p.append("<h3>데이터 모델 변경 거버넌스</h3>")
    p.append(flow(FLOW_GOV_ASCII, FLOW_GOV_MMD, artifact))
    p.append("<p>관리자가 직접 변경하면 초안·신청 단계를 건너뛰고 곧바로 승인 상태가 됩니다.</p>")

    p.append('<h2 id="known">알려진 제약</h2>')
    p.append("<p>2026-08-23 점검에서 확인한 것들입니다. 쓰기 전에 알아두면 오해를 줄일 수 있습니다.</p>")
    p.append('<div class="tw"><table><thead><tr>'
             '<th>항목</th><th>내용</th><th>영향 범위</th></tr></thead><tbody>')
    for t, d, scope in KNOWN_ISSUES:
        p.append("<tr><td><b>%s</b></td><td>%s</td><td>%s</td></tr>" % (t, d, scope))
    p.append("</tbody></table></div>")

    p.append('<h2 id="notimpl">매뉴얼에만 있고 구현되지 않은 기능</h2>')
    p.append("<p>이전 매뉴얼이 기술했으나 코드에 없는 기능입니다. 설계 단계의 계획이 매뉴얼에 그대로 남은 것으로 보입니다. "
             "구현 여부는 별도로 정해야 합니다.</p>")
    p.append('<div class="tw"><table><thead><tr>'
             '<th>기능</th><th>현재 상태</th></tr></thead><tbody>')
    for t, d in NOT_IMPLEMENTED:
        p.append("<tr><td><b>%s</b></td><td>%s</td></tr>" % (t, d))
    p.append("</tbody></table></div>")

    p.append("<footer>Narae DataQ 사용자 매뉴얼 v3.0 · 2026-08-23<br>"
             "화면 %d개 (검증됨 %d · 화면만 %d) · 스크린샷 %d장은 현재 빌드에서 촬영"
             "</footer>" % (len(SECTIONS), ok, ui, len(SECTIONS) - len(missing)))
    p.append("</div>")

    out = os.path.join(HERE, "artifact.html" if artifact else "index.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(p))
    print("생성: %-14s %.1fMB" % (os.path.basename(out), os.path.getsize(out) / 1048576))
    if missing:
        print("  스크린샷 없음: %s" % ", ".join(missing))
    return ok, ui


if __name__ == "__main__":
    _ok, _ui = build(artifact=False)
    build(artifact=True)
    print("화면 %d개 — 검증됨 %d / 화면만 %d" % (len(SECTIONS), _ok, _ui))
    sys.exit(0)
