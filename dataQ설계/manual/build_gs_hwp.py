# -*- coding: utf-8 -*-
"""GS인증 제출 문서 생성 — 참고본(NavidM AI ETL)을 NavidM Meta 판으로.

한컴오피스를 COM 으로 몰아 원본 HWP 를 열고 내용만 바꾼다.
원본 서식(표·머리말·도장란·쪽번호)은 그대로 남는다.

  python build_gs_hwp.py            # 제품설명서 + 상담요청서 둘 다
  python build_gs_hwp.py 상담       # 상담요청서만

만들고 나서 한컴에서 한 번 해줘야 하는 것:
  제품설명서 목차 우클릭 -> 차례 새로 고침
  (그림 높이가 바뀌어 쪽번호가 밀린다. 차례 갱신은 COM 액션으로 노출돼 있지 않다.)
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hwp_tools import open_hwp, replace_all, find_text, pictures, swap_picture  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
GS = os.path.join(ROOT, "GS인증")
REF = os.path.join(GS, "참고")
ASSETS = os.path.join(HERE, "assets")

# 문서에 등장하는 순서대로. 원본 그림의 표시 크기를 물려받는다.
PICTURES = [
    ("arch_engine.png", "1.1 제품 개요 — 제품의 구성"),
    ("arch_product.png", "1.5 제품의 운영환경"),
    ("02_word.png", "2.2 (1) 데이터 표준 사전"),
    ("11_dm_table.png", "2.2 (2) 데이터 모델"),
    ("21_diag_result.png", "2.2 (3) 표준 진단"),
    ("01_dashboard.png", "2.2 (4) 표준화 도구 및 대시보드"),
]

PHASE_A = [  # 본문 문단
 # 1.1 개요
 ("본 제품은 다양한 소스(Source) 데이터소스(DBMS등)으로부터 대용량의 데이터를 추출(Extract)하고 변환(Transform) 및 가공해서 타겟(Target) 데이터소스에 효율적으로 로드(Load)하기 위한 데이터이관 소프트웨어입니다.",
  "본 제품은 기업 및 공공기관이 보유한 데이터베이스의 테이블·컬럼 메타데이터를 수집하여 데이터 표준 사전(단어·용어·도메인·코드)과 대조하고, 표준을 벗어난 항목을 유형별로 판정하여 조치까지 연결하기 위한 데이터 표준화 및 메타데이터 관리 소프트웨어입니다."),

 # 1.2 용도 및 목적
 ("다양한 데이터소스간에 데이터를 이관하기 위한 소프트웨어입니다.",
  "데이터 표준 사전을 관리하고, 운영 데이터베이스의 실제 구조가 그 표준을 따르고 있는지 진단하여 표준 준수율을 개선하기 위한 소프트웨어입니다."),
 ("본 제품을 사용하기 위해서는 데이터이관의 실행대상인 데이터소스(예: DBMS)들에 접근하고, 데이터를 조회하고 입력하기 위해 사용되는 SQL에 대한 사전지식과 프로세스 간에 데이터 연계에 필요한 파일 입/출력 구조에 대하여 이해가 필요합니다.",
  "본 제품을 사용하기 위해서는 진단 대상 데이터소스(예: DBMS)에 접근하기 위한 계정 정보와, 데이터베이스의 테이블·컬럼 구조 및 데이터베이스 표준화 지침에 대한 이해가 필요합니다."),

 # 2.1 주요 제공 작업 및 서비스
 ("본 제품이 데이터소스간의 데이터이관을 처리하기 위해서 제공하는 기능은 다음과 같습니다.",
  "본 제품이 데이터 표준화와 메타데이터 관리를 위해서 제공하는 기능은 다음과 같습니다."),
 ("▪ 프로젝트 : 데이터이관을 처리하기 위한 프로젝트와 태스크를 생성 및 실행하고 데이터이관",
  "▪ 데이터 표준 사전 : 단어·용어·도메인·코드 표준을 등록하고 승인 절차를 거쳐"),
 ("실행 결과를 모니터링하는 기능 제공",
  "관리하는 기능 제공"),
 ("▪ 설정 : 데이터이관 프로젝트를 생성하기 위해 필요한 설정정보를 관리하는 기능 제공",
  "▪ 데이터 모델 : 데이터소스에서 테이블·컬럼·인덱스·제약조건을 수집하고 관리하는 기능 제공"),
 ("▪ 대시보드 및 분석 : 데이터이관 현황을 그래프로 제공하고 보고서로 출력하는 기능 제공",
  "▪ 진단 및 대시보드 : 표준 준수율과 구조 변경 현황을 그래프로 제공하고 보고서로 출력하는 기능 제공"),

 # 2.2 주요 기능
 ("1) 프로젝트", "1) 데이터 표준 사전"),
 ("데이터이관 프로젝트 및 태스크를 관리하고 실행 결과를 확인하고 모니터링하는 기능",
  "단어·용어·도메인·코드 표준을 등록하고 승인 워크플로를 거쳐 관리하는 기능"),
 ("2) 카탈로그", "2) 데이터 모델"),
 ("커넥션을 등록하고 테이블/컬럼 스키마를 확인한다.",
  "데이터소스를 등록하고 테이블·컬럼·인덱스·제약조건을 수집하여 조회 및 편집한다."),
 ("3) 대시보드 및 분석", "3) 표준 진단 및 구조 변경 진단"),
 ("데이터이관 현황을 그래프와 보고서로 출력함.",
  "수집된 모델을 표준 사전과 대조해 이슈를 유형별로 판정하고, 직전 수집 시점 대비 구조 변경을 감지함."),
 ("4) AI 활용", "4) 표준화 도구 및 대시보드"),
 ("AI 기능을 쓰는 환경에서는 화면 오른쪽 어시스턴스 패널에서 대화할 수 있습니다.",
  "한글 컬럼명에서 표준 단어와 영문약어를 자동으로 도출하고, 표준 준수율 현황을 대시보드와 보고서로 출력합니다."),

 # 2.3 제한사항 및 성능
 ("▪ 본 제품은 동시 태스크 실행개수와 데이터 커밋 단위등의 속성값 변경을 통해서 데이터로드 성능의 조절이 가능합니다.",
  "▪ 본 제품의 모델 수집은 Oracle · PostgreSQL · Cubrid · MariaDB 를 지원하며, 인덱스 및 제약조건 수집은 Oracle · PostgreSQL 에서 지원합니다. 표준 진단은 수집된 메타데이터만을 대상으로 하므로 운영 데이터베이스에 접속하지 않으며, 진단 대상 제외 설정과 스케줄 실행 시각 조정을 통해서 진단 부하의 조절이 가능합니다."),

 # 2.4 사용자 오류방지
 ("본 제품에서는 사용자의 잘못 입력한 데이터에 대해 자동으로 점검하는 기능을 제공하지 않습니다.",
  "본 제품은 표준 사전 등록 시 필수 입력값 누락과 중복 등록을 검증하며, 용어 승인 시 이를 구성하는 단어의 승인 여부를 점검하여 미승인 단어가 포함된 용어의 승인을 차단합니다."),

 # 2.6 보안
 ("본 제품은 인가된 사용자만 사용할 수 있도록 로그인 기능을 제공합니다.",
  "본 제품은 인가된 사용자만 사용할 수 있도록 로그인 기능을 제공하며, 관리자와 일반 사용자의 권한을 분리하여 메뉴 및 기능 접근을 제어합니다. 로그인 연속 실패 시 계정을 일정 시간 차단합니다."),

 # 2.7 전용 운영 자원
 ("서버에 설치된 데이터베이스와 메시지큐 솔루션은 NavidM AI ETL 에서 전용으로 사용합니다.",
  "서버에 설치된 메타 데이터베이스는 NavidM Meta 에서 전용으로 사용하며, 진단 대상이 되는 운영 데이터베이스와는 분리되어 있습니다."),
]

PHASE_B = [  # 표 셀 · 날짜 · 환경
 ("2022.01.01", "2026.09.03"),
 ("최정근", "박현수"),
 ("2022.02.07", "2026.09.03"),
 ("주소 : 서울시 영등포구 선유선로25길 28, 703호",
  "주소 : 서울특별시 영등포구 선유서로25길 28, 703호"),
 ("Google Chrome 96.0", "Google Chrome 96.0 이상"),
 ("docker 20.10.6, RabbitMQ 3.8.14", "docker 20.10.6 이상, OpenJDK(Temurin) 8"),
 ("PostgreSQL 12.0", "PostgreSQL 13 (TimescaleDB 2.10)"),
 ("GS인증기관 : 부산IT융합부품연구소(동의대학교 산학협력단)",
  "GS인증기관 : 한국화학융합시험연구원(KTR)"),
]

PHASE_C = [  # 고유명사 (반드시 마지막)
 ("Narae DataM V3.0 제품설명서", "NavidM Meta V1.0 제품설명서"),
 ("NavidM AI ETL V3.0", "NavidM Meta V1.0"),
 ("Narae DataM V3.0", "NavidM Meta V1.0"),
 ("NavidM AI ETL", "NavidM Meta"),
 ("V3.0", "V1.0"),
]

ALL = PHASE_A + PHASE_B + PHASE_C

상담요청서 = [
    ("NavidM AI ETL V3.0", "NavidM Meta V1.0"),
    ("AI 기반 자연어 데이터 파이프라인 생성 솔루션",
     "데이터 표준화 진단 및 메타데이터 관리 솔루션"),
    ("08월", "09월"),
    ("25일", "03일"),
]


def build_제품설명서():
    src = os.path.join(REF, "NavidM AI ETL 제품설명서_v3.0.hwp")
    dst = os.path.join(GS, "NavidM Meta 제품설명서_v1.0.hwp")
    hwp = open_hwp(src)

    miss = [f for f, r in ALL if not replace_all(hwp, f, r)]
    print("치환 %d건 · 실패 %s" % (len(ALL) - len(miss), miss or "없음"))

    # 개정이력 Rev.2 행 삭제 — V1.0 최초 배포라 개정 이력이 하나뿐이다
    if find_text(hwp, "Rev.2"):
        hwp.HAction.Run("TableDeleteRow")
    print("Rev.2 행:", "삭제됨" if "Rev.2" not in hwp.GetTextFile("UNICODE", "") else "★남음")

    assert len(pictures(hwp)) == len(PICTURES), "그림 개수가 안 맞는다"
    for i, (name, where) in enumerate(PICTURES):
        path = os.path.join(ASSETS, name)
        want, got = swap_picture(hwp, i, path, Image.open(path))
        flag = "OK" if abs(got - want) <= max(60, want * 0.02) else "★크기이상"
        print("  [%d] %-20s %s  %s" % (i, name, flag, where))

    hwp.SaveAs(dst, "HWP", "")
    hwp.Quit()
    print("저장:", dst)


def build_상담요청서():
    src = os.path.join(REF, "[KTR]GS인증_상담요청서_(주)나래데이터.hwp")
    dst = os.path.join(GS, "[KTR]GS인증_상담요청서_(주)나래데이터_NavidM_Meta.hwp")
    hwp = open_hwp(src)
    for f, r in 상담요청서:
        n = replace_all(hwp, f, r)
        print("  %s %s" % ("OK" if n else "★", f[:50]))
    hwp.SaveAs(dst, "HWP", "")
    hwp.Quit()
    print("저장:", dst)


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "제품"):
        build_제품설명서()
    if what in ("all", "상담"):
        build_상담요청서()
