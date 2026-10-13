// 합친 명세서에서 엔드포인트별로 어느 기능ID 행에 나오는지 찾아 부록 표를 만든다 — 분석용
const fs = require('fs');
const path = require('path');
const dir = __dirname;
const spec = fs.readFileSync(process.argv[2], 'utf8').split(/\r?\n/);
// 데이터 품질 진단 컨트롤러(Qual*)는 DQ 제품으로 분리돼 102번 범위 밖이라 뺀다
const rows = fs.readFileSync(path.join(dir, 'api_list.tsv'), 'utf8').trim().split('\n').map(l => l.split('\t'))
  .filter(r => !/^Qual/.test(r[3]));
const norm = s => s.replace(/\{[^}]*\}/g, '{}');
// 표 행만: 첫 칸이 기능ID
const tableRows = spec.filter(l => /^\|\s*[A-Z]+-B?\d+/.test(l)).map(l => ({ id: l.split('|')[1].trim(), text: norm(l) }));
const seen = new Map();
for (const [mod, me, p, fn, loc] of rows) {
  const key = mod + ' ' + me + ' ' + p;
  if (seen.has(key)) continue;
  const np = norm(p);
  // 경로 뒤에 다른 글자가 바로 이어지면 다른 경로(접두어 일치)로 본다
  const re = new RegExp(np.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(?![A-Za-z0-9_/{])');
  const ids = tableRows.filter(r => re.test(r.text)).map(r => r.id);
  seen.set(key, { mod, me, p, fn, loc, ids: [...new Set(ids)] });
}
const list = [...seen.values()];
const miss = list.filter(x => x.ids.length === 0);
let md = '| 모듈 | 메서드 | 경로 | 컨트롤러 메서드 | 근거 | 명세 기능ID |\n|---|---|---|---|---|---|\n';
for (const x of list) {
  md += `| ${x.mod} | ${x.me} | \`${x.p}\` | ${x.fn.split('.').pop()} | \`${x.loc}\` | ${x.ids.length ? x.ids.slice(0, 6).join(', ') + (x.ids.length > 6 ? ' 외' : '') : '**없음**'} |\n`;
}
fs.writeFileSync(path.join(dir, 'api_appendix.md'), md);
console.log('endpoints', list.length, 'covered', list.length - miss.length, 'missing', miss.length);
miss.forEach(x => console.log('MISSING', x.mod, x.me, x.p, x.loc));
