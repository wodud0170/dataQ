<template>
  <v-main>
    <!-- 104번 WP7-27 — 쓰이지 않는 표준 (단어·용어·도메인) -->
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3" style="gap:8px;">
        <h2><v-icon>mdi-archive-search-outline</v-icon>&nbsp;&nbsp;미사용 표준</h2>
        <v-spacer />
        <DictSelector v-model="dictId" @change="onDict" />
      </v-row>

      <div class="us-guide">
        이 사전에서 쓰이지 않는 표준입니다. 단어 — 어떤 용어의 구성 단어도 아니고 어떤 컬럼명(‘_’ 로 나눈 토큰)에도 없음. 용어(코드 포함) — 컬럼명이 용어 영문약어명과 같은 컬럼이 없음.
        도메인 — 어떤 용어도 쓰지 않음. 컬럼은 사용 중인 데이터 모델 중 모델 사전이 이 사전이거나 이 사전을 상위로 둔 하위 사전인 것만 봅니다. 지우려면 이름을 눌러 그 화면에서 지우세요.
      </div>

      <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
        <v-btn-toggle id="unusedKind" v-model="kind" mandatory dense color="indigo" @change="load">
          <v-btn small value="WORD">단어</v-btn>
          <v-btn small value="TERM">용어</v-btn>
          <v-btn small value="DOMAIN">도메인</v-btn>
        </v-btn-toggle>
        <!-- 104번 WP9-67 — 지금 찾기 또는 예약 실행(스케줄 "미사용 표준")이 저장한 결과 -->
        <v-select id="unusedRun" v-model="runId" :items="runOptions" item-text="text" item-value="value"
          label="기준" dense outlined hide-details style="max-width:260px;" @change="load">
          <template #selection="{ item }">{{ item.short }}</template>
        </v-select>
        <v-text-field id="unusedKeyword" v-model="keyword" label="이름·영문약어" dense outlined hide-details clearable
          style="max-width:240px;" v-on:keyup.enter="load" />
        <v-checkbox v-model="aprvOnly" true-value="Y" false-value="N" label="승인만" dense hide-details class="ma-0 pa-0"
          :disabled="!!runId" @change="load" />
        <v-btn id="unusedSearch" small outlined color="primary" class="btn-auto" v-on:click="load">조회</v-btn>
        <v-spacer />
        <span class="us-count">총 {{ items.length }}건{{ !runId && items.length >= 5000 ? ' (5,000건까지)' : '' }}</span>
        <v-btn id="unusedExcel" small outlined color="deep-purple" class="btn-auto" :disabled="!items.length" v-on:click="download">
          <v-icon small left>mdi-file-excel</v-icon>엑셀
        </v-btn>
      </v-row>
      <div v-if="runId" class="us-run-note">예약 실행 결과를 보는 중입니다 ({{ selRun ? fmtDt(selRun.startDt) + ' 실행 — ' + (selRun.summary || '') : '' }}).
        실행할 때의 승인된 미사용 표준입니다. 지금 상태는 기준을 "지금"으로 바꾸세요.</div>

      <v-data-table id="unusedTable" :headers="headers" :items="items" item-key="id" dense :items-per-page="30" :loading="loading">
        <template #item.nm="{ item }">
          <span class="ndColor--text" style="cursor:pointer;" @click="goTo(item)">{{ item.nm }}</span>
        </template>
        <template #item.aprvYn="{ item }">
          <v-chip v-if="item.aprvYn !== 'Y'" x-small outlined color="orange">승인 대기</v-chip>
          <span v-else>Y</span>
        </template>
        <template #no-data>쓰이지 않는 표준이 없습니다.</template>
      </v-data-table>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
import DictSelector from './DictSelector.vue';
export default {
  name: 'DSUnusedStd',
  components: { DictSelector },
  props: ['isMobile'],
  data: () => ({
    dictId: null,
    kind: 'WORD',
    keyword: '',
    aprvOnly: 'Y',
    items: [],
    loading: false,
    // 104번 WP9-67 — 예약 실행 결과
    runId: null,
    runs: [],
  }),
  computed: {
    runOptions() {
      // short — 고른 값 칸(좁다)에 보일 글. 요약은 목록과 아래 안내에서 본다
      return [{ text: '지금 (실시간으로 찾기)', short: '지금 (실시간으로 찾기)', value: null }].concat(this.runs.map(r => ({
        text: '예약 실행 ' + this.fmtDt(r.startDt) + ' — ' + (r.summary || ''), short: '예약 실행 ' + this.fmtDt(r.startDt), value: r.runId, summary: r.summary,
      })));
    },
    selRun() {
      return this.runs.find(r => r.runId === this.runId) || null;
    },
    headers() {
      const nm = { WORD: '단어명', TERM: '용어명', DOMAIN: '도메인명' }[this.kind];
      const extra = { WORD: '구분', TERM: '도메인', DOMAIN: '데이터타입' }[this.kind];
      const h = [{ text: nm, value: 'nm' }];
      if (this.kind !== 'DOMAIN') h.push({ text: '영문약어명', value: 'engAbrvNm' });
      h.push({ text: extra, value: 'extra' }, { text: '승인여부', value: 'aprvYn', width: '100px' }, { text: '등록일', value: 'cretDt', width: '110px' });
      return h;
    },
  },
  methods: {
    params() {
      return { dictId: this.dictId, kind: this.kind, keyword: this.keyword || null, aprvOnly: this.aprvOnly, runId: this.runId || null };
    },
    onDict() {
      this.runId = null;
      this.runs = [];
      if (this.dictId) {
        axios.post(this.$APIURL.base + 'api/batchRun/list', { runTp: 'UNUSED_STD', dictId: this.dictId, doneOnly: 'Y' })
          .then(r => { this.runs = r.data || []; }).catch(() => {});
      }
      this.load();
    },
    fmtDt(s) {
      return s && s.length >= 12 ? s.substr(0, 4) + '-' + s.substr(4, 2) + '-' + s.substr(6, 2) + ' ' + s.substr(8, 2) + ':' + s.substr(10, 2) : (s || '');
    },
    load() {
      this.loading = true;
      if (this.runId) {
        // 104번 WP9-67 — 저장된 결과
        axios.post(this.$APIURL.base + 'api/batchRun/unusedItems', { runId: this.runId, kind: this.kind, keyword: this.keyword || null })
          .then(res => { this.items = (res.data && res.data.items) || []; })
          .catch(() => { this.items = []; })
          .finally(() => { this.loading = false; });
        return;
      }
      axios.post(this.$APIURL.base + 'api/std/unused/list', this.params())
        .then(res => { this.items = (res.data && res.data.items) || []; })
        .catch(() => { this.items = []; })
        .finally(() => { this.loading = false; });
    },
    download() {
      const p = this.params();
      axios.get(this.$APIURL.base + 'api/std/unused/download', { params: p, responseType: 'blob' }).then(res => {
        const kindNm = { WORD: '단어', TERM: '용어', DOMAIN: '도메인' }[this.kind];
        const url = window.URL.createObjectURL(new Blob([res.data]));
        const a = document.createElement('a');
        a.href = url;
        a.download = '미사용표준_' + kindNm + '_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      }).catch(() => {
        this.$swal.fire({ title: '미사용 표준 다운로드 실패 - API 확인 필요', icon: 'error' });
      });
    },
    // 이름 → 그 화면에서 검색
    goTo(item) {
      const t = {
        WORD: { type: 'word', field: 'wordNm', title: '단어', tab: 'word' },
        TERM: { type: 'term', field: 'termsNm', title: '용어', tab: 'term' },
        DOMAIN: { type: 'domain', field: 'domainNm', title: '도메인', tab: 'domain' },
      }[this.kind];
      eventBus.pendingSearch = { type: t.type, field: t.field, value: item.nm };
      eventBus.$emit('openStdSearch', t.title, t.tab);
    },
  },
};
</script>
<style scoped>
.us-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.us-count { font-size: .85rem; color: #546E7A; }
.us-run-note { font-size: .78rem; color: #3949AB; background: #E8EAF6; border-radius: 4px; padding: 4px 10px; margin-bottom: 8px; }
</style>
