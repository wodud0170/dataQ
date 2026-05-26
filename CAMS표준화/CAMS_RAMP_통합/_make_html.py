# -*- coding: utf-8 -*-
import json, html
data = json.load(open('_w7_mapping.json', encoding='utf-8'))

CONF_COLOR = {'A':'#16a34a','B':'#ca8a04','C':'#ea580c','D':'#94a3b8'}
CONF_BG    = {'A':'#dcfce7','B':'#fef9c3','C':'#ffedd5','D':'#f1f5f9'}

def esc(s):
    return html.escape(str(s)) if s else ''

def row_html(r):
    c = r.get('cams'); rmp = r.get('ramp'); conf = r['conf']
    color = CONF_COLOR[conf]; bg = CONF_BG[conf]
    if c:
        pk = '🔑 ' if c.get('pk') else ''
        cams_cell = f"{pk}<div class='col-en'>{esc(c['en'])}</div><div class='col-cmt'>{esc(c['cmt'])}</div><div class='col-dt'>{esc(c['dt'])}({esc(c['dl'])})</div>"
    else:
        cams_cell = "<div class='empty'>—</div>"
    if rmp:
        pk = '🔑 ' if rmp.get('pk') else ''
        ramp_cell = f"{pk}<div class='col-en'>{esc(rmp['en'])}</div><div class='col-cmt'>{esc(rmp['kr'])}</div><div class='col-dt'>{esc(rmp['dt'])}({esc(rmp['dl'])})</div>"
    else:
        ramp_cell = "<div class='empty'>—</div>"
    arrow = '↔' if c and rmp else ('→' if c else '←')
    return f"<tr class='conf-{conf}' style='background:{bg}'><td class='cams-col'>{cams_cell}</td><td class='arrow-col' style='color:{color}'><div class='arrow'>{arrow}</div><div class='conf-badge' style='background:{color};color:white'>{conf}</div><div class='signal'>{esc(r['signal']) or '-'}</div></td><td class='ramp-col'>{ramp_cell}</td><td class='note-col'>{esc(r['note'])}</td></tr>"

def stat_bar(stat, total_label):
    return f"<div class='stat-bar'><div class='stat-card'><div class='stat-num conf-A'>{stat.get('A',0)}</div><div class='stat-num conf-B'>{stat.get('B',0)}</div><div class='stat-num conf-C'>{stat.get('C',0)}</div><div class='stat-num conf-D'>{stat.get('D',0)}</div><div class='stat-label'>{total_label}</div></div></div>"

W_DATA = [
    ('W1','기록물 생산·등록','🔵','RAMP만','tb_rd*, tb_storgdfile, tb_stfolderneofile'),
    ('W2','분류·기능분류','🟢','균등','CM_CLASS_BASTABLE ↔ tb_zzfnctclsf/tb_zzunit*'),
    ('W3','생산현황 통보','🟡','CAMS 풍부','RG_S*, TB_GENR_* ↔ tb_crfolder/tb_crrecord'),
    ('W4','이관 계획·승인','🟢','균등','RG_STRANS* ↔ tb_tkorgacptnplan, tb_rdtkovrplan'),
    ('W5','이관 인수','🟢','균등','RG_M*, CP_TRANSFER_* ↔ tb_tk*, tb_mg*'),
    ('W6','검수','🟡','CAMS 풍부','RG_MCHECK_*, RG_MONITOR ↔ tb_tkfilecheck, tb_tkviruscheck'),
    ('W7','정식 등록 ★','🟢','균등 — 메인','RG_DOCUMENT/RG_DETAIL ↔ tb_rdfolder/tb_rdrecord'),
    ('W8','보관·서고','🟢','균등','SV_LIBRARY, SV_BOOKSHELF, RF_* ↔ tb_sr*, tb_rf*'),
    ('W9','보존매체 수록','🟡','CAMS 압도','SV_OD/MF/DVD_* ↔ tb_sroptidisk*, tb_srmfphtg'),
    ('W10','영구포맷 변환','🟢','균등','SV_NEO_FILE, SV_PDF_FILE ↔ tb_stfolderneofile, tb_stformat_*'),
    ('W11','스캐닝','🔵','CAMS만','SV_DOCUMENT_SCANNING_*'),
    ('W12','보존처리','🔵','CAMS 중심','SV_PRESERV_*, SV_RESTOR_*, SV_COPY_PAPER'),
    ('W13','정수점검·매체이전','🔵','CAMS 중심','SV_QUANTITY_*, SV_MEDIA_MIGRATION_*'),
    ('W14','공개 분류','🟢','균등','SV_DOCUMENT_OPEN, SV_OPEN_LMT ↔ tb_strlslist, tb_rdrls*'),
    ('W15','정보공개 청구','🔵','CAMS만','CA_INFOREQUEST, CA_APPROVAL'),
    ('W16','열람 신청','🟡','다름','US_INSPREQ_* ↔ tb_rd*prsldtl, tb_rd*accs'),
    ('W17','다운로드·워터마크','🔵','CAMS만','SM_USER_DOWNLOAD_IP, SM_WMDOWNLOG'),
    ('W18','보존기간 재평가','🟡','CAMS 풍부','SV_ARCHIVE_PARITY*, SV_REAPPR_* ↔ tb_streqrevlopnn*'),
    ('W19','폐기 심의','🟢','균등','SV_DISUSE_*, RG_SEXHAUSTARCIVE ↔ tb_df*'),
    ('W20','색인·시소러스','🟡','CAMS 압도','TH_*, SV_KEYWORD, KH_AUTH_* ↔ tb_rd*kwrd'),
    ('W21','검색엔진','🔵','CAMS만','K2_QUEUE_*, KN_SEARCH_*'),
    ('W22','개인정보 필터링','🔵','CAMS만','CN_RFILE_FILTER, CN_RITEM_FILTER'),
    ('W23','대국민 포털·UCI','🔵','CAMS만','UCI_*, CN_TRANSFER_*, SV_HP_SERVICE_*'),
    ('W24','결재·전자결재','🔵','RAMP만','tb_rdmultiaprovcreat'),
    ('W25','사용자·조직·시스템','🟢','균등','SM_USER_* ↔ tb_stuser, tb_stdept, tb_storg'),
]
W_BG = {'🟢':'#dcfce7','🟡':'#fef9c3','🔵':'#dbeafe'}
W_BORDER = {'🟢':'#16a34a','🟡':'#ca8a04','🔵':'#2563eb'}

w_cards = ''
for code, name, mark, kind, tables in W_DATA:
    is_w7 = (code == 'W7')
    style = f"background:{W_BG[mark]};"
    if is_w7:
        style += "border:3px solid #dc2626;box-shadow:0 4px 12px rgba(220,38,38,0.2);"
    else:
        style += f"border:2px solid {W_BORDER[mark]};"
    w_cards += f"<div class='w-card' style='{style}'><div class='w-head'><span class='w-code'>{code}</span><span class='w-mark'>{mark}</span></div><div class='w-name'>{esc(name)}</div><div class='w-kind'>{esc(kind)}</div><div class='w-tables'>{esc(tables)}</div></div>"

fold_rows = ''.join(row_html(r) for r in data['folder']['rows'])
rec_rows = ''.join(row_html(r) for r in data['record']['rows'])

CSS = """
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');
*{box-sizing:border-box}
body{font-family:'Noto Sans KR','Malgun Gothic',sans-serif;color:#1e293b;margin:0;background:#f8fafc;line-height:1.55}
.container{max-width:1280px;margin:0 auto;padding:32px}
h1{font-size:32px;font-weight:700;margin:0 0 8px 0;color:#0f172a}
h2{font-size:24px;font-weight:700;margin:48px 0 16px 0;color:#0f172a;border-left:6px solid #2563eb;padding-left:12px}
h3{font-size:18px;font-weight:700;margin:24px 0 12px 0;color:#1e293b}
.subtitle{color:#64748b;font-size:14px;margin-bottom:32px}
.cover{background:linear-gradient(135deg,#1e3a8a 0%,#1e40af 100%);color:white;padding:48px 32px;border-radius:12px;margin-bottom:32px}
.cover h1{color:white;font-size:36px}
.cover .subtitle{color:#bfdbfe;font-size:16px;margin-bottom:0}
.cover-meta{margin-top:24px;font-size:14px;color:#bfdbfe}
.alert{background:#fef2f2;border-left:4px solid #dc2626;padding:16px 20px;margin:16px 0;border-radius:6px}
.alert-title{font-weight:700;color:#991b1b;margin-bottom:6px}
.info{background:#eff6ff;border-left:4px solid #2563eb;padding:16px 20px;margin:16px 0;border-radius:6px}
.w-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:14px;margin:20px 0}
.w-card{padding:14px;border-radius:8px}
.w-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:6px}
.w-code{font-weight:700;color:#475569;font-size:14px}
.w-mark{font-size:18px}
.w-name{font-weight:700;font-size:15px;margin-bottom:4px;color:#0f172a}
.w-kind{font-size:12px;color:#64748b;font-weight:500;margin-bottom:6px}
.w-tables{font-size:11.5px;color:#475569;font-family:'Consolas',monospace;line-height:1.4}
.stat-bar{display:flex;gap:16px;margin:16px 0 24px 0;justify-content:flex-start}
.stat-card{background:white;border-radius:8px;padding:16px 20px;box-shadow:0 1px 3px rgba(0,0,0,0.08);display:flex;gap:12px;align-items:center}
.stat-num{font-size:24px;font-weight:700;padding:4px 10px;border-radius:4px;color:white;min-width:36px;text-align:center}
.stat-num.conf-A{background:#16a34a}
.stat-num.conf-B{background:#ca8a04}
.stat-num.conf-C{background:#ea580c}
.stat-num.conf-D{background:#94a3b8}
.stat-label{font-weight:600;color:#0f172a;margin-left:8px}
.mapping-table{width:100%;border-collapse:collapse;margin:16px 0;background:white;box-shadow:0 1px 3px rgba(0,0,0,0.08);border-radius:8px;overflow:hidden}
.mapping-table thead th{background:#0f172a;color:white;padding:10px 14px;text-align:left;font-size:13px;font-weight:600;letter-spacing:0.5px}
.mapping-table tbody td{padding:10px 14px;font-size:13px;border-top:1px solid #e2e8f0;vertical-align:top}
.mapping-table .cams-col,.mapping-table .ramp-col{width:35%}
.mapping-table .arrow-col{width:14%;text-align:center;font-weight:700}
.mapping-table .note-col{width:16%;font-size:12px;color:#64748b}
.col-en{font-family:'Consolas',monospace;font-weight:700;font-size:13px;color:#1e3a8a}
.col-cmt{font-size:12.5px;margin-top:2px;color:#334155}
.col-dt{font-size:11px;color:#64748b;margin-top:2px;font-family:'Consolas',monospace}
.arrow{font-size:20px;margin-bottom:4px}
.conf-badge{display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:700;letter-spacing:0.5px}
.signal{font-size:10px;color:#64748b;margin-top:3px;font-family:'Consolas',monospace}
.empty{color:#cbd5e1;font-style:italic;text-align:center}
.legend{display:flex;gap:24px;margin:16px 0;padding:14px 20px;background:white;border-radius:8px;font-size:13px;flex-wrap:wrap}
.legend-item{display:flex;align-items:center;gap:8px}
.legend-color{width:18px;height:18px;border-radius:4px}
.signal-key{background:white;padding:14px 20px;border-radius:8px;margin:12px 0;font-size:13px}
.signal-key h4{margin:0 0 8px 0;font-size:14px;color:#0f172a}
.signal-key dl{margin:0;display:grid;grid-template-columns:120px 1fr;gap:6px 16px}
.signal-key dt{font-weight:700;color:#1e3a8a;font-family:'Consolas',monospace}
.signal-key dd{margin:0;color:#475569}
.decision-list{background:white;border-radius:8px;padding:18px 24px;margin:14px 0;box-shadow:0 1px 3px rgba(0,0,0,0.08)}
.decision-list li{margin:8px 0;line-height:1.6}
.w7-highlight{background:#fef2f2;border:2px solid #dc2626;padding:14px;border-radius:8px;margin:18px 0}
.w7-highlight strong{color:#991b1b}
@media print{body{background:white}.container{max-width:none;padding:12px}.mapping-table{box-shadow:none;page-break-inside:auto}.mapping-table tr{page-break-inside:avoid}}
"""

doc = f"""<!DOCTYPE html>
<html lang='ko'><head><meta charset='utf-8'><title>CAMS↔RAMP 통합 매핑 보고서</title>
<style>{CSS}</style></head><body><div class='container'>

<div class='cover'>
  <h1>CAMS ↔ RAMP DB 통합 매핑 보고서</h1>
  <div class='subtitle'>업무별 테이블 그룹 분류 → 컬럼 단위 매핑 (W7 메인테이블 시범 적용)</div>
  <div class='cover-meta'>
    작성일 2026-05-19 · 입력: cams_workflow_study.pdf · ramp기관스키마정보.xlsx · CAMS_SCHEMA_원본.xlsx<br>
    자립형 HTML — 인쇄 가능 (Ctrl+P) · 브라우저로 열기
  </div>
</div>

<div class='alert'>
  <div class='alert-title'>⚠️ 본 보고서의 검토 대상</div>
  통합팀 + 업무 이해관계자 의견일치가 필요. 컬럼 매핑은 메타데이터 기반 1차 안.
  CAMS 데이터 접근 불가 · RAMP 데이터 향후 확보 시 A·B 등급 샘플 재검증.
</div>

<h2>1. 통합 매핑 정책 (요약)</h2>
<div class='info'>
  ① <strong>표준화 이전(원시 상태)</strong> 기준으로 매핑 — CAMS 표준화 결과 대기 안 함<br>
  ② <strong>RAMP를 표준 기준으로 간주</strong> (RAMP의 R8 미준수 등 인지하나 우선)<br>
  ③ <strong>흡수 통합 = CAMS 컬럼명을 RAMP 컬럼명으로 변경</strong>
</div>

<h2>2. 25개 업무 영역 매트릭스</h2>
<p>국가기록원 기록물관리 프로세스를 25개 업무 단계(=시스템 메뉴/화면)로 분해. 양쪽 시스템에서 같은 업무에 속한 테이블 그룹 쌍이 통합 후보군.</p>
<div class='legend'>
  <div class='legend-item'><span class='legend-color' style='background:#dcfce7;border:2px solid #16a34a'></span>🟢 양쪽 균등 · 통합 1순위 (9개)</div>
  <div class='legend-item'><span class='legend-color' style='background:#fef9c3;border:2px solid #ca8a04'></span>🟡 한쪽 풍부 · 흡수 정책 (6개)</div>
  <div class='legend-item'><span class='legend-color' style='background:#dbeafe;border:2px solid #2563eb'></span>🔵 단방향 · 영역 유지 (10개)</div>
</div>
<div class='w-grid'>{w_cards}</div>

<h2>3. 매핑 방법론 핵심</h2>
<div class='signal-key'>
<h4>매핑 신호 (Signals)</h4>
<dl>
<dt>S1a</dt><dd>한글명 완전일치 (CAMS 코멘트 == RAMP 한글명)</dd>
<dt>S1b</dt><dd>한글명 부분일치 (포함관계 또는 head noun 일치)</dd>
<dt>S1c</dt><dd>행안부 표준 동의어 관계 (년도↔연도, 순번↔일련번호)</dd>
<dt>S2</dt><dd>영문명 패턴 일치 (드묾 — CAMS는 약어, RAMP는 단어)</dd>
<dt>S3</dt><dd>데이터타입+길이 호환 (VARCHAR2↔STRING, NUMBER↔NUMERIC)</dd>
<dt>S4</dt><dd>PK/FK 위치 일치</dd>
<dt>S5</dt><dd>업무 의미 추론 (컬럼 그룹·코멘트 문맥)</dd>
<dt>S6</dt><dd>사용자 확정 매핑 (BSID↔fls_id, DSID↔ritm_id)</dd>
</dl>
</div>
<div class='signal-key'>
<h4>신뢰도 등급 (Confidence)</h4>
<dl>
<dt><span class='conf-badge' style='background:#16a34a;color:white'>A 확정</span></dt><dd>사용자 확정 또는 S1a + S3 다중 강신호</dd>
<dt><span class='conf-badge' style='background:#ca8a04;color:white'>B 후보</span></dt><dd>S1a 또는 (S1b + S3)</dd>
<dt><span class='conf-badge' style='background:#ea580c;color:white'>C 추정</span></dt><dd>약신호만 — 사람 확인 권장</dd>
<dt><span class='conf-badge' style='background:#94a3b8;color:white'>D 미매핑</span></dt><dd>한 쪽에만 존재</dd>
</dl>
</div>

<h2>4. W7 메인테이블 컬럼 매핑 ⭐</h2>
<div class='w7-highlight'>
<strong>이 영역이 통합의 머릿돌입니다.</strong>
CAMS RG_DOCUMENT(160컬럼) ↔ RAMP tb_rdfolder(170), CAMS RG_DETAIL(116) ↔ RAMP tb_rdrecord(149).
사용자 확정 키 매핑: BSID↔fls_id, DSID↔ritm_id.
</div>

<h3>4-1. 기록물철 · RG_DOCUMENT ↔ tb_rdfolder</h3>
{stat_bar(data['folder']['stat'], '전체 ' + str(len(data['folder']['rows'])) + '행')}
<table class='mapping-table'>
<thead><tr><th>CAMS RG_DOCUMENT (영문명 · 코멘트 · 도메인)</th><th>매핑</th><th>RAMP tb_rdfolder (영문명 · 한글명 · 도메인)</th><th>비고</th></tr></thead>
<tbody>{fold_rows}</tbody>
</table>

<h3>4-2. 기록물건 · RG_DETAIL ↔ tb_rdrecord</h3>
{stat_bar(data['record']['stat'], '전체 ' + str(len(data['record']['rows'])) + '행')}
<table class='mapping-table'>
<thead><tr><th>CAMS RG_DETAIL (영문명 · 코멘트 · 도메인)</th><th>매핑</th><th>RAMP tb_rdrecord (영문명 · 한글명 · 도메인)</th><th>비고</th></tr></thead>
<tbody>{rec_rows}</tbody>
</table>

<h2>5. 의사결정 포인트 (통합팀·이해관계자 의견 필요)</h2>
<div class='decision-list'>
<ol>
<li><strong>키 매핑 정렬 — BSID/DSID(12) ↔ fls_id/ritm_id(14)</strong>: VARCHAR(14) 한 컬럼에 양쪽 값 공존(가변길이). 충돌 검사는 RAMP 데이터 확보 후. <em>사용자 확정 — 강제 정렬</em></li>
<li><strong>D 등급 미매핑 정책</strong>: CAMS-only 컬럼을 통합 후 RAMP에 신규 컬럼으로 추가? 폐기? 매핑테이블 보존?</li>
<li><strong>C 등급 추정 매핑</strong>: 약신호 매핑은 전수 검토 필요. 업무 이해관계자 검토 라운드.</li>
<li><strong>흡수 후 컬럼명</strong>: RAMP 컬럼명 그대로 vs 표준화 후 컬럼명 (구 DB 사용자 소통 vs 표준 일치).</li>
<li><strong>1순위 9개 영역 확장</strong>: W7 외 W2, W4, W5, W8, W10, W14, W19, W25 컬럼 매핑 진행 우선순위.</li>
</ol>
</div>

<h2>6. 다음 단계</h2>
<div class='decision-list'>
<ol>
<li>본 문서를 통합팀에 공유 → 매핑 방법론·25개 영역 매트릭스 합의</li>
<li>W7 컬럼 매핑 결과 등급별 검토 — A 일괄 / B 그룹 / C 전수 / D 정책</li>
<li>합의된 매핑을 별도 TSV로 정리하여 ETL 입력 자료화</li>
<li>🟢 1순위 나머지 8개 영역 컬럼 매핑 확장 (W2·W4·W5·W8·W10·W14·W19·W25)</li>
<li>🟡 2순위 6개 영역의 풍부측 보존·빈약측 연결 정책 수립</li>
<li>RAMP 데이터 확보 후 A·B 등급 샘플 실데이터 검증</li>
</ol>
</div>

<div style='margin-top:48px;padding:16px;border-top:1px solid #e2e8f0;color:#94a3b8;font-size:12px;text-align:center'>
CAMS_RAMP_통합/12_통합_매핑_시각화.html · 자립형 HTML 보고서 · 인쇄: Ctrl+P
</div>

</div></body></html>"""

with open('12_통합_매핑_시각화.html', 'w', encoding='utf-8') as f:
    f.write(doc)
print(f"→ 12_통합_매핑_시각화.html ({len(doc)//1024} KB)")
