<template>
  <!-- 104번 WP7-26 — 영향도 다이얼로그 (용어·코드 → 컬럼, 컬럼 → 용어·단어·도메인·코드) -->
  <v-dialog :value="value" @input="v => $emit('input', v)" max-width="1000" scrollable>
    <v-card id="stdImpactDialog">
      <v-card-title class="font-weight-bold">영향도 분석 - '{{ title }}'</v-card-title>
      <v-card-text>
        <v-progress-linear v-if="loading" indeterminate color="indigo darken-2" />
        <template v-if="!loading && kind !== 'column'">
          <template v-if="kind === 'code'">
            <div class="font-weight-bold mb-2">이 코드를 쓰는 용어 ({{ terms.length }}건) · 코드값 {{ valueCnt }}개</div>
            <v-simple-table dense v-if="terms.length > 0">
              <thead><tr><th>용어명</th><th>영문약어명</th><th>도메인명</th><th>승인</th></tr></thead>
              <tbody>
                <tr v-for="(t, i) in terms" :key="'t' + i">
                  <td>{{ t.termsNm }}</td><td>{{ t.termsEngAbrvNm }}</td><td>{{ t.domainNm }}</td><td>{{ t.aprvYn }}</td>
                </tr>
              </tbody>
            </v-simple-table>
            <div class="font-weight-bold mb-2 mt-4">관련 코드 ({{ relatedCodes.length }}건)</div>
            <v-simple-table dense v-if="relatedCodes.length > 0">
              <thead><tr><th>관계</th><th>코드명</th><th>코드 그룹</th><th>코드 유형</th></tr></thead>
              <tbody>
                <tr v-for="(c, i) in relatedCodes" :key="'r' + i">
                  <td>{{ c.rel === 'CHILD' ? '이 코드를 상위로 씀' : '이 코드를 묶는 통합 코드' }}</td>
                  <td>{{ c.termsNm }}</td><td>{{ c.codeGrp }}</td><td>{{ codeTpNm(c.codeTp) }}</td>
                </tr>
              </tbody>
            </v-simple-table>
            <v-alert v-else type="info" dense text class="mt-1">관련 코드가 없습니다.</v-alert>
          </template>
          <div class="font-weight-bold mb-2 mt-4">연관 컬럼 ({{ columns.length }}건{{ columns.length >= 2000 ? ' — 2,000건까지' : '' }})</div>
          <div class="mb-2" style="font-size:.78rem; color:#546E7A;">사용 중인 데이터 모델 중 모델 사전이 같은 컬럼. "영문명" 은 컬럼명 = 용어 영문약어명, "한글명만" 은 영문명은 다르고 한글명이 용어명과 같은 컬럼(비표준 후보)입니다.</div>
          <v-simple-table dense v-if="columns.length > 0" id="impactColumnTable">
            <thead><tr><th v-if="kind !== 'term'">용어</th><th>데이터 모델</th><th>테이블</th><th>컬럼</th><th>컬럼 한글명</th><th>타입</th><th>일치</th><th>표준</th></tr></thead>
            <tbody>
              <tr v-for="(c, i) in columns" :key="'c' + i">
                <td v-if="kind !== 'term'">{{ c.termsNm }}</td>
                <td>{{ c.dataModelNm }}</td>
                <td>{{ (c.objOwner ? c.objOwner + '.' : '') + c.objNm }}</td>
                <td>{{ c.attrNm }}</td>
                <td>{{ c.attrNmKr }}</td>
                <td>{{ c.dataType }}{{ c.dataLen ? '(' + c.dataLen + ')' : '' }}</td>
                <td>
                  <v-chip x-small :color="c.matchType === 'ENG' ? 'green' : 'orange'" text-color="white">{{ c.matchType === 'ENG' ? '영문명' : '한글명만' }}</v-chip>
                </td>
                <td>{{ c.termsStndYn }}</td>
              </tr>
            </tbody>
          </v-simple-table>
          <v-alert v-else type="info" dense text class="mt-1">연관 컬럼이 없습니다.</v-alert>
        </template>

        <template v-if="!loading && kind === 'column'">
          <div class="font-weight-bold mb-2">표준 용어</div>
          <v-simple-table dense v-if="col.term" id="impactColTerm">
            <thead><tr><th>용어명</th><th>영문약어명</th><th>찾은 기준</th><th>코드 그룹</th></tr></thead>
            <tbody>
              <tr>
                <td>{{ col.term.termsNm }}</td><td>{{ col.term.termsEngAbrvNm }}</td>
                <td>{{ col.term.matchType === 'ENG' ? '영문명 = 용어 영문약어명' : '한글명 = 용어명(영문명은 다름)' }}</td>
                <td>{{ col.term.codeGrp || '-' }}</td>
              </tr>
            </tbody>
          </v-simple-table>
          <v-alert v-else type="warning" dense text class="mt-1">이 컬럼에 맞는 승인 용어가 없습니다 (아래는 영문명 토큰과 같은 영문약어의 단어).</v-alert>
          <div class="font-weight-bold mb-2 mt-4">단어 ({{ (col.words || []).length }}건)</div>
          <v-simple-table dense v-if="(col.words || []).length > 0">
            <thead><tr><th>단어명</th><th>영문약어명</th><th>형식단어</th></tr></thead>
            <tbody>
              <tr v-for="(w, i) in col.words" :key="'w' + i">
                <td>{{ w.wordNm }}</td><td>{{ w.wordEngAbrvNm }}</td><td>{{ w.wordClsfYn }}</td>
              </tr>
            </tbody>
          </v-simple-table>
          <v-alert v-else type="info" dense text class="mt-1">단어가 없습니다.</v-alert>
          <div class="font-weight-bold mb-2 mt-4">도메인</div>
          <v-simple-table dense v-if="col.domain">
            <thead><tr><th>도메인명</th><th>데이터 타입</th><th>길이</th></tr></thead>
            <tbody>
              <tr>
                <td>{{ col.domain.domainNm }}</td><td>{{ col.domain.dataType }}</td>
                <td>{{ col.domain.dataLen }}{{ col.domain.dataDecimalLen ? ',' + col.domain.dataDecimalLen : '' }}</td>
              </tr>
            </tbody>
          </v-simple-table>
          <v-alert v-else type="info" dense text class="mt-1">도메인이 없습니다.</v-alert>
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="ndColor" text @click="$emit('input', false)">닫기</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import axios from 'axios';

export default {
  name: 'StdImpactDialog',
  props: {
    value: { type: Boolean, default: false },
    kind: { type: String, default: 'term' },     // term | code | column
    title: { type: String, default: '' },
    termsId: { type: String, default: null },
    dmId: { type: String, default: null },
    attrNm: { type: String, default: null },
    attrNmKr: { type: String, default: null },
  },
  data: () => ({
    loading: false,
    terms: [],
    columns: [],
    relatedCodes: [],
    valueCnt: 0,
    col: {},
  }),
  watch: {
    value(v) {
      if (v) this.load();
    },
  },
  methods: {
    codeTpNm(tp) {
      return { SINGLE: '단일', HIER: '계층', DERIV: '파생', INTEG: '통합', INDIV: '개별' }[tp] || tp;
    },
    load() {
      this.loading = true;
      this.terms = [];
      this.columns = [];
      this.relatedCodes = [];
      this.valueCnt = 0;
      this.col = {};
      let req;
      if (this.kind === 'column') {
        req = axios.get(this.$APIURL.base + 'api/dm/impact/column', { params: { dmId: this.dmId, attrNm: this.attrNm, attrNmKr: this.attrNmKr } })
          .then(res => { this.col = res.data || {}; });
      } else {
        req = axios.get(this.$APIURL.base + 'api/std/impact/' + this.kind, { params: { termsId: this.termsId } })
          .then(res => {
            const d = res.data || {};
            this.terms = d.terms || [];
            this.columns = d.columns || [];
            this.relatedCodes = d.relatedCodes || [];
            this.valueCnt = d.valueCnt || 0;
          });
      }
      req.catch(err => console.error('영향도 분석 조회 실패', err)).finally(() => { this.loading = false; });
    },
  },
};
</script>
