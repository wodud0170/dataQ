// 102 기능명세서 md + 템플릿 → 대표 보고용 HTML. 사용: node build.js <md> <template> <out>
const fs = require('fs');
const [mdPath, tplPath, outPath] = process.argv.slice(2);
const lines = fs.readFileSync(mdPath, 'utf8').split(/\r?\n/);
const tpl = fs.readFileSync(tplPath, 'utf8');

function cells(line) {
  const parts = []; let cur = '';
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '\\' && line[i + 1] === '|') { cur += '|'; i++; continue; }
    if (c === '|') { parts.push(cur.trim()); cur = ''; continue; }
    cur += c;
  }
  parts.push(cur.trim());
  return parts.slice(1, -1);
}
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function inline(s) {
  return s.split(/<br\s*\/?>/i).map(part => esc(part)
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')).join('<br>');
}
const plain = s => s.replace(/<br\s*\/?>/gi, ' ').replace(/[`*]/g, '').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

// ---- 장·절·기능 행
const chapters = []; let ch = null, sec = null, menuRows = [], inMenu = false;
for (const line of lines) {
  if (/^## 부록/.test(line)) break;
  let m;
  if ((m = line.match(/^## (\d+)\. (.+)$/))) { ch = { no: +m[1], title: m[2].trim(), sections: [] }; chapters.push(ch); sec = null; continue; }
  if (!ch || ch.no === 0) continue;
  if ((m = line.match(/^### (\d+\.\d+) (.+)$/))) { sec = { no: m[1], title: m[2].trim(), rows: [] }; ch.sections.push(sec); inMenu = false; continue; }
  if (/^\| 그룹 \| 메뉴 \(탭 제목\)/.test(line)) { inMenu = true; continue; }
  if (inMenu) {
    if (!/^\|/.test(line)) { inMenu = false; continue; }
    if (/^\|---/.test(line)) continue;
    const c = cells(line);
    menuRows.push({ group: c[0].replace(/\s+\(.*$/, '').trim(), menu: c[1].replace(/ — 탭 제목은.*$/, ''), auth: c[4], state: c[5] });
    continue;
  }
  if (/^\|\s*[A-Z]+-[A-Z]?\d+/.test(line)) {
    const c = cells(line);
    if (!sec) { sec = { no: ch.no + '.0', title: '', rows: [] }; ch.sections.push(sec); }
    sec.rows.push({ id: c[0], name: c[1], action: c[2] });
  }
}
const body = chapters.filter(c => c.no > 0);

// ---- 장 요약 (사람이 쓴 한두 문장)
const SUMMARY = {
  1: '로그인과 계정 잠금, 세션, 역할로 나눠 주는 권한(관리자는 모든 권한), 헤더 알림, 탭으로 여는 화면 구조와 메뉴, 화면 사이 이동, 작업 진행을 알려 주는 실시간 알림(WebSocket), 서버 설정을 다룬다.',
  2: '로그인하면 처음 열리는 대시보드(표준 현황 건수, 표준 준수율·구조 일치율, 준수율 추이, 구조 변경 감지, 최근 변경 이력, 빠른 실행)와 단어·용어·도메인·컬럼을 한 번에 찾는 통합 검색.',
  3: '단어·용어·코드·도메인·도메인 그룹·도메인 분류의 등록·수정·삭제, 엑셀 일괄등록과 내려받기, 영향도 분석, 변경 이력, 그리고 기관별로 사전을 나누는 표준사전 관리.',
  4: '데이터 소스에 연결해 스키마 범위를 정하고 테이블·컬럼·인덱스·제약조건을 수집한다. 수집한 모델을 화면과 엑셀로 편집하고, 모델링 도구 파일(ERwin XML·XMI 2.1)을 들여오고 내보내며, ERD 로 그린다.',
  5: '모델 직접 반영 권한이 없는 사용자의 데이터 모델 변경은 저장(DRAFT) → 신청 → 모델 승인 권한자의 승인·반려를 거친다. 변경 이력에 모델 데이터소스 DBMS 문법의 DDL 조각을 남기고 그 시스템의 DB 반영 권한자가 모델의 데이터소스에 반영한다. 진단 제외 대상과 업무영역도 여기서 관리한다.',
  6: '표준 진단은 컬럼마다 용어 존재·한글명·데이터 타입·길이를 판정해 준수율을 낸다. 구조 변경 진단은 수집본과 현재 DB 를 비교한다. 두 진단 모두 스케줄(간편 주기·Cron)로 돌릴 수 있다.',
  7: '한글 컬럼명 목록을 넣으면 형태소 분석으로 단어를 나누고, 영문약어를 조합하고, 도메인을 추천한다. 사람이 리뷰·수정한 뒤 용어로 일괄 등록하고 변환 이력을 남긴다.',
  8: '표준 직접 관리 권한이 없는 사용자가 등록한 단어·용어·도메인·코드를 표준 승인 권한자가 승인하거나 반려한다. 요청 유형에 결재선을 두면 결재 문서가 만들어져 단계마다 결재자(지정 사용자·역할·부서장·관리자)가 결재함에서 승인하고, 단계마다 알림이 간다. 요청자는 자기 요청의 처리 상태를 본다. 용어는 구성 단어가 먼저 승인돼야 승인된다.',
  9: '공지사항과 Q&A 게시판. 글·첨부파일·상단 고정·댓글.',
  10: '사용자 관리, 내 정보와 비밀번호 변경, 대상 DB 접속 정보(데이터 소스)와 연결 테스트, 시스템 정보(시스템별 기본 표준사전), 권한 묶음인 역할을 만들고 사용자에게 주는 역할 및 권한, 부서 트리(조직)와 요청 유형별 결재선, DDL 을 만들 때 쓰는 PK 이름 규칙과 DBMS 별 데이터 타입 매핑(DDL 규칙).',
};
const SHORT = { 1: '공통', 2: '대시보드·검색', 3: '표준 사전', 4: '데이터 모델', 5: '거버넌스', 6: '진단·스케줄', 7: '표준화 도구', 8: '표준 사전 승인', 9: '커뮤니티', 10: '관리' };

// ---- 집계
let total = 0;
const stats = body.map(c => {
  let screen = 0, bg = 0, noui = 0, screens = [];
  for (const s of c.sections) {
    const isNoUi = /화면 없는 API/.test(s.title);
    const comp = s.title.match(/`([A-Za-z]+\.vue)`/);
    // 화면 틀(레이아웃·메뉴·헤더)과 공용 부품(사전 선택기)은 화면 수에서 뺀다
    if (comp && !/^(App|NdNav|NdHaeder|DictSelector)\.vue$/.test(comp[1])) screens.push({ name: s.title.replace(/\s*—.*$/, ''), comp: comp[1] });
    for (const r of s.rows) {
      if (isNoUi) noui++;
      else if (/-[BR]\d+$/.test(r.id)) bg++;
      else screen++;
    }
  }
  const n = screen + bg + noui; total += n;
  return { no: c.no, title: c.title, short: SHORT[c.no], n, screen, bg, noui, screens };
});
const max = Math.max(...stats.map(s => s.n));

// 영역별 막대 (화면 기능 / 백그라운드·규칙 / 화면 없는 API)
const bars = stats.map(s => {
  const w = v => (v / max * 100).toFixed(2);
  return `<li class="bar-row">
  <a class="bar-label" href="#ch${s.no}"><span class="bar-no">${s.no}</span>${s.short}</a>
  <span class="bar-track" role="img" aria-label="${s.short}: 화면 기능 ${s.screen}, 백그라운드·규칙 ${s.bg}, 화면 없는 API ${s.noui}">
    <span class="seg seg-ui" style="width:${w(s.screen)}%"></span><span class="seg seg-bg" style="width:${w(s.bg)}%"></span><span class="seg seg-api" style="width:${w(s.noui)}%"></span>
  </span>
  <span class="bar-num">${s.n}</span>
</li>`;
}).join('\n');
const sum = k => stats.reduce((a, s) => a + s[k], 0);

// ---- 메뉴 지도
const groups = [];
// 주석 처리된 메뉴는 화면에 없으므로 지도에서 뺀다
for (const r of menuRows.filter(x => !/주석/.test(x.state))) {
  let g = groups.find(x => x.name === r.group);
  if (!g) { g = { name: r.group, items: [] }; groups.push(g); }
  g.items.push(r);
}
// 권한 코드 이름은 1.10 절 표에서 읽는다
const PERM_NM = {};
for (const line of lines) {
  const m = line.match(/^\| ([A-Z]+(?:_[A-Z]+)+) \| ([^|]+) \|/);
  if (m) PERM_NM[m[1]] = m[2].trim();
}
const menuState = r => {
  if (/주석/.test(r.state)) return ['off', '메뉴 꺼 둠'];
  if (/숨김/.test(r.state)) return ['hide', '숨김'];
  const p = (r.auth.match(/\b[A-Z]+(?:_[A-Z]+)+\b/) || [])[0];
  if (p && /없는/.test(r.auth)) return ['usr', PERM_NM[p] + ' 없음'];
  if (p) return ['adm', PERM_NM[p]];
  if (/관리자만/.test(r.auth)) return ['adm', '관리자'];
  if (/일반 사용자만/.test(r.auth)) return ['usr', '일반 사용자'];
  return ['on', ''];
};
let activeMenus = 0;
const menuHtml = groups.map(g => {
  const groupName = g.name === '(그룹 없음)' ? '대시보드' : g.name;
  const items = g.items.map(r => {
    const [cls, label] = menuState(r);
    if (cls !== 'off' && cls !== 'hide') activeMenus++;
    return `<li class="mi mi-${cls}"><span>${esc(r.menu)}</span>${label ? `<span class="mi-tag">${label}</span>` : ''}</li>`;
  }).join('');
  const allOff = g.items.every(r => /주석/.test(r.state));
  return `<div class="mg${allOff ? ' mg-off' : ''}${g.name === '관리' ? ' mg-adm' : ''}"><h4>${esc(groupName)}</h4><ul>${items}</ul></div>`;
}).join('\n');

// ---- 기능 목록
const TAGRE = /\s*\((메뉴 없음|코드 주석|호출부 없음|다른 화면)\)\s*$/;
const chHtml = body.map(c => {
  const s = stats.find(x => x.no === c.no);
  const rows = c.sections.map(sec => {
    if (!sec.rows.length) return '';
    const title = sec.title ? `<tr class="sec"><th colspan="3" scope="colgroup">${inline(sec.no + ' ' + sec.title)}</th></tr>` : '';
    return title + sec.rows.map(r => {
      const tm = r.name.match(TAGRE);
      const name = tm ? r.name.replace(TAGRE, '') : r.name;
      const tag = tm ? ` <span class="tag tag-note">${tm[1]}</span>` : '';
      const hay = esc((r.id + ' ' + plain(r.name) + ' ' + plain(r.action)).toLowerCase());
      return `<tr data-q="${hay}"><td class="fid">${esc(r.id)}</td><td class="fname">${inline(name)}${tag}</td><td class="fact">${inline(r.action)}</td></tr>`;
    }).join('\n');
  }).join('\n');
  const scr = s.screens.length ? `<ul class="screens">${s.screens.map(x => `<li>${esc(x.name)}</li>`).join('')}</ul>` : '';
  return `<section class="chap" id="ch${c.no}">
  <header class="chap-head">
    <span class="chap-no">${c.no}</span>
    <div class="chap-title"><h3>${esc(c.title)}</h3><p>${SUMMARY[c.no]}</p></div>
    <dl class="chap-meta"><div><dt>기능</dt><dd>${s.n}</dd></div><div><dt>화면</dt><dd>${s.screens.length}</dd></div></dl>
  </header>
  ${scr}
  <details class="flist" data-ch="${c.no}">
    <summary>기능 목록 <b>${s.n}</b>건 펼쳐 보기</summary>
    <div class="tbl-wrap"><table class="ftable"><thead><tr><th scope="col">기능ID</th><th scope="col">기능명</th><th scope="col">동작</th></tr></thead><tbody>
${rows}
    </tbody></table></div>
  </details>
</section>`;
}).join('\n');

const tocCh = stats.map(s => `<li><a href="#ch${s.no}"><span class="n">${s.no}</span>${s.short}</a></li>`).join('');

const out = tpl
  .replace('{{BARS}}', bars)
  .replace('{{MENU}}', menuHtml)
  .replace('{{CHAPTERS}}', chHtml)
  .replace('{{TOC_CH}}', tocCh)
  .replace(/\{\{TOTAL\}\}/g, total)
  .replace(/\{\{SUM_UI\}\}/g, sum('screen'))
  .replace(/\{\{SUM_BG\}\}/g, sum('bg'))
  .replace(/\{\{SUM_API\}\}/g, sum('noui'))
  .replace(/\{\{SCREENS\}\}/g, stats.reduce((a, s) => a + s.screens.length, 0))
  .replace(/\{\{MENUS\}\}/g, activeMenus);
fs.writeFileSync(outPath, out);
console.log('rows', total, 'ui', sum('screen'), 'bg', sum('bg'), 'noui', sum('noui'), 'screens', stats.reduce((a, s) => a + s.screens.length, 0), 'activeMenus', activeMenus, 'menuRows', menuRows.length, 'bytes', Buffer.byteLength(out));
const left = out.match(/\{\{[A-Z_]+\}\}/g); if (left) console.log('UNFILLED', left);
