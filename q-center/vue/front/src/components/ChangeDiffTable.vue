<!--
  104번 WP9-55 — 변경 전후 비교 표

  before·after 는 JSON 문자열·객체, VO 문자열 "Cls(a=1, …)", Map 문자열 "{a=1, …}" 어느 것이든 된다 (changeDiff.js).
  둘 다 있으면 항목 / 변경 전 / 변경 후 (기본은 바뀐 항목만, [모두 보기]), 한쪽만 있으면 항목 / 등록된 값·지운 값.
-->
<template>
  <div class="cdt">
    <div v-if="mode === 'none'" class="cdt-empty">변경 전후 값이 없습니다.</div>
    <template v-else>
      <v-simple-table dense class="cdt-table">
        <thead>
          <tr>
            <th class="cdt-th" style="width:24%">항목</th>
            <template v-if="mode === 'both'">
              <th class="cdt-th">변경 전</th>
              <th class="cdt-th">변경 후</th>
            </template>
            <th v-else class="cdt-th">{{ mode === 'after' ? '등록된 값' : '지운 값' }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.key">
            <td class="cdt-label">{{ r.label }}</td>
            <template v-if="mode === 'both'">
              <td class="cdt-val"><span :class="{ 'cdt-prev': r.changed }">{{ r.prev || '(없음)' }}</span></td>
              <td class="cdt-val"><span :class="{ 'cdt-curr': r.changed }">{{ r.curr || '(없음)' }}</span></td>
            </template>
            <td v-else class="cdt-val">{{ (mode === 'after' ? r.curr : r.prev) || '(없음)' }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td :colspan="mode === 'both' ? 3 : 2" class="cdt-empty">바뀐 항목이 없습니다.</td>
          </tr>
        </tbody>
      </v-simple-table>
      <div v-if="mode === 'both' && sameCnt > 0" class="cdt-note">
        {{ showAll ? '같은 항목도 보이는 중입니다.' : '같은 항목 ' + sameCnt + '개는 감췄습니다.' }}
        <a class="cdt-link" @click="showAll = !showAll">{{ showAll ? '바뀐 항목만' : '모두 보기' }}</a>
      </div>
    </template>
  </div>
</template>

<script>
import { diffRows, parseValue } from './changeDiff';

export default {
  name: 'ChangeDiffTable',
  props: {
    before: { default: null },
    after: { default: null },
    kind: { type: String, default: 'DM' },   // DM | STD — 항목 이름표
  },
  data: () => ({ showAll: false }),
  computed: {
    mode() {
      const b = Object.keys(parseValue(this.before)).length > 0;
      const a = Object.keys(parseValue(this.after)).length > 0;
      if (a && b) return 'both';
      if (a) return 'after';
      if (b) return 'before';
      return 'none';
    },
    rows() {
      return diffRows(this.before, this.after, this.kind, !this.showAll);
    },
    sameCnt() {
      return diffRows(this.before, this.after, this.kind, false).filter(r => !r.changed).length;
    },
  },
};
</script>

<style scoped>
.cdt-table { border: 1px solid #E0E0E0; }
.cdt-th { background: #F5F7FA; font-size: .78rem !important; color: #455A64 !important; }
.cdt-label { font-size: .8rem; color: #455A64; white-space: nowrap; }
.cdt-val { font-size: .8rem; word-break: keep-all; overflow-wrap: anywhere; white-space: pre-wrap; }
.cdt-prev { color: #C62828; text-decoration: line-through; }
.cdt-curr { color: #2E7D32; font-weight: 600; }
.cdt-empty { font-size: .8rem; color: #90A4AE; padding: 6px 0; }
.cdt-note { font-size: .75rem; color: #78909C; margin-top: 4px; }
.cdt-link { cursor: pointer; text-decoration: underline; margin-left: 4px; }
</style>
