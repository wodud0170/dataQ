// 컨트롤러에서 엔드포인트 전수 추출 — 분석용 (저장소 파일은 건드리지 않음)
// 사용: node api_list.js [저장소 루트]  → 이 폴더에 api_list.tsv
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || path.resolve(__dirname, '../../..');
const dirs = [
  ['q-center', 'q-center/src/main/java/qualitycenter/controller'],
  ['q-executor', 'q-executor/src/main/java/qualityexecutor/controller'],
];
const out = [];
function paths(args) {
  if (!args) return [''];
  const m = args.match(/(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")/) || args.match(/^\s*(\{[^}]*\}|"[^"]*")/);
  if (!m) return [''];
  return (m[1].match(/"([^"]*)"/g) || ['""']).map(s => s.slice(1, -1));
}
function methods(ann, args) {
  if (ann !== 'RequestMapping') return [ann.replace('Mapping', '').toUpperCase()];
  const m = (args || '').match(/method\s*=\s*(\{[^}]*\}|RequestMethod\.\w+)/);
  if (!m) return ['ANY'];
  return (m[1].match(/RequestMethod\.(\w+)/g) || []).map(s => s.split('.')[1]);
}
for (const [mod, dir] of dirs) {
  for (const f of fs.readdirSync(path.join(root, dir)).filter(n => n.endsWith('.java'))) {
    const src = fs.readFileSync(path.join(root, dir, f), 'utf8');
    const lines = src.split(/\r?\n/);
    let prefix = '';
    const classIdx = lines.findIndex(l => /\bclass\s+\w+/.test(l));
    for (let i = 0; i < classIdx; i++) {
      const m = lines[i].match(/@RequestMapping\s*\(([^)]*)\)/);
      if (m) prefix = paths(m[1])[0];
    }
    for (let i = classIdx + 1; i < lines.length; i++) {
      const m = lines[i].match(/@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(?:\((.*)\))?/);
      if (!m || /^\s*\/\//.test(lines[i])) continue;
      let args = m[2] || '';
      let j = i;
      while ((args.match(/\(/g) || []).length > (args.match(/\)/g) || []).length && j < i + 5) { j++; args += lines[j]; }
      let name = '';
      for (let k = i + 1; k < Math.min(lines.length, i + 12); k++) {
        const nm = lines[k].match(/^\s*public\s+[^=(]*?\s(\w+)\s*\(/);
        if (nm) { name = nm[1]; break; }
      }
      for (const p of paths(args)) for (const me of methods(m[1] + 'Mapping', args)) {
        out.push([mod, me, (prefix + p).replace(/\/+/g, '/'), f.replace('.java', '') + '.' + name, f + ':' + (i + 1)].join('\t'));
      }
    }
  }
}
fs.writeFileSync(path.join(__dirname, 'api_list.tsv'), out.join('\n') + '\n');
const c = {}; out.forEach(r => { const k = r.split('\t')[0]; c[k] = (c[k] || 0) + 1; });
console.log('total rows', out.length, JSON.stringify(c));
