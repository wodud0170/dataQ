// 104번 WP9-55 — 변경 전후 비교 공용 (표준 변경 이력 상세·데이터 모델 변경 승인·모델 변경 이력·표준사전 승인 상세)
//
// 값 모양 세 가지를 읽는다:
//   JSON 문자열 또는 객체  — 데이터 모델 변경 이력(BEFORE_JSON·AFTER_JSON)
//   VO 문자열 "Cls(a=1, b=2)" — 표준 변경 이력(PREV_VALUE·CURR_VALUE)
//   Map 문자열 "{a=1, b=2}"   — 결재자 수정 후 승인의 변경 전 값

// 화면에 보이지 않는 항목 — 시스템·승인 관리용
const HIDDEN = new Set([
  // 표준 (86번 #34·#37 에서 감추던 것)
  'id', 'aprvUserId', 'aprvStatUpdtDt', 'updtUserId', 'cretUserId', 'cretDt', 'updtDt', 'partOfSpeech', 'reqSysCd', 'reqSysNm', 'useYn',
  // 사전은 수정으로 바뀌지 않는다 — 이전 값 VO 에는 비어 있어 없는 변경으로 보였다
  'dictId',
  // 데이터 모델
  'clctId', 'dataModelId', 'deletedDt', 'stndDiagTargetYn', 'stndDiagTargetReason', 'structDiagTargetYn', 'structDiagTargetReason',
  'qualDiagTargetYn', 'qualDiagTargetReason', 'diagTargetUpdtUserId', 'diagTargetUpdtDt', 'aprvStatus', 'requesterUserId', 'reqDt',
  'aprvDt', 'aprvComment', 'submissionId', 'subjAreaKeep', 'objCretDt', 'objUpdtDt', 'bizAreaId', 'subjAreaId', 'udpVals', 'wordStndLst',
]);

const STD_LABELS = {
  wordNm: '단어명', wordEngAbrvNm: '영문약어', wordEngNm: '영문명', wordDesc: '단어설명',
  wordClsfYn: '형식단어', domainClsfNm: '도메인분류', commStndYn: '공통표준',
  magntdOrd: '제정차수', allophSynmLst: '이음동의어', forbdnWordLst: '금칙어',
  aprvYn: '승인상태', useYn: '사용여부', reqSysCd: '요청시스템',
  termsNm: '용어명', termsEngAbrvNm: '영문약어', termsDesc: '용어설명',
  domainNm: '도메인명', codeGrp: '코드그룹', chrgOrg: '담당기관',
  domainGrpNm: '도메인그룹', dataType: '데이터타입', dataLen: '데이터길이',
  dataDecimalLen: '소수점길이', dataUnit: '단위', storFmt: '저장형식',
  exprFmtLst: '표현형식', allowValLst: '허용값',
  codeNm: '코드명', codeEngNm: '코드영문명', codeDesc: '코드설명',
  codeDataNm: '코드값명', codeDataNmEng: '코드값영문명', codeDataDesc: '코드값설명',
};

const DM_LABELS = {
  objOwner: '소유자', objNm: '테이블 영문명', objNmKr: '테이블 한글명', objDesc: '테이블 설명', objComment: '테이블 코멘트',
  objAttrCnt: '컬럼 수', tablespaceNm: '테이블스페이스', bizAreaNm: '업무영역', subjAreaNm: '주제영역',
  attrNm: '컬럼 영문명', origAttrNm: '원래 컬럼 영문명', attrNmKr: '컬럼 한글명', attrComment: '컬럼 코멘트',
  dataType: '데이터 타입', dbmsDataType: '원본 타입', dataLen: '길이', dataDecimalLen: '소수점',
  nullableYn: 'NULL 허용', pkYn: 'PK', fkYn: 'FK', defaultVal: '기본값', attrOrder: '컬럼 순서',
  fkParentObjOwner: '참조 소유자', fkParentObjNm: '참조 테이블', fkParentAttrNm: '참조 컬럼',
  termsStndYn: '용어 표준', domainStndYn: '도메인 표준', wordLst: '구성 단어',
  privYn: '개인정보', encYn: '암호화',
  pkName: 'PK 이름', fkName: 'FK 이름', refOwner: '참조 소유자', refTableNm: '참조 테이블',
  mappings: '컬럼 매핑', columns: '컬럼', cascadedFks: '함께 지운 FK',
};

export function fieldLabel(key, kind) {
  const map = kind === 'DM' ? DM_LABELS : STD_LABELS;
  return map[key] || key;
}

function show(v) {
  if (v === null || v === undefined) return '';
  if (Array.isArray(v)) return v.map(show).join(', ');
  if (typeof v === 'object') return JSON.stringify(v);
  const s = String(v);
  return (s === 'null' || s === '[]') ? '' : s;
}

// "a=1, b=2" 를 나눈다 — 값에 쉼표가 있어도 다음 "이름=" 앞에서만 자른다
function splitPairs(inner) {
  const out = {};
  for (const t of inner.split(/, (?=[A-Za-z_][A-Za-z0-9_]*=)/)) {
    const eq = t.indexOf('=');
    if (eq < 0) continue;
    out[t.substring(0, eq).trim()] = show(t.substring(eq + 1).trim());
  }
  return out;
}

/** 값 하나를 {항목: 문자열} 로 — 감출 항목은 뺀다 */
export function parseValue(v) {
  if (v === null || v === undefined || v === '') return {};
  let obj = null;
  if (typeof v === 'object') {
    obj = v;
  } else {
    const s = String(v).trim();
    if (s.startsWith('{') && s.endsWith('}')) {
      try { obj = JSON.parse(s); } catch (e) { obj = splitPairs(s.substring(1, s.length - 1)); }
    } else {
      const m = s.match(/^[A-Za-z0-9_]+\(([\s\S]*)\)$/);   // `s` 플래그 대신 [\s\S] (구 babel 호환)
      obj = m ? splitPairs(m[1]) : {};
    }
  }
  const out = {};
  Object.keys(obj).forEach(k => { if (!HIDDEN.has(k)) out[k] = show(obj[k]); });
  return out;
}

/** VO 문자열을 [{key, value}] 로 — 표준 변경 이력 상세(STD-081)가 쓰던 모양 */
export function parseVoString(s) {
  const o = parseValue(s);
  return Object.keys(o).map(k => ({ key: k, value: o[k] }));
}

/**
 * 전후 비교 행 — [{key, label, prev, curr, changed}]. 둘 다 있으면 바뀐 항목만(onlyChanged), 한쪽만 있으면 값이 있는 항목 전부.
 */
export function diffRows(before, after, kind, onlyChanged) {
  const a = parseValue(before);
  const b = parseValue(after);
  const keys = [];
  Object.keys(a).concat(Object.keys(b)).forEach(k => { if (!keys.includes(k)) keys.push(k); });
  const both = Object.keys(a).length > 0 && Object.keys(b).length > 0;
  const rows = keys.map(k => ({
    key: k, label: fieldLabel(k, kind),
    prev: a[k] === undefined ? '' : a[k], curr: b[k] === undefined ? '' : b[k],
    changed: (a[k] || '') !== (b[k] || ''),
  }));
  if (!both) return rows.filter(r => (r.prev || r.curr) !== '');
  return onlyChanged ? rows.filter(r => r.changed) : rows;
}
