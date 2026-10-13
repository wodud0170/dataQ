<template>
  <v-main>
    <!-- 104번 WP5-14 — 공통표준 가져오기: 원본 사전(참조 사전)의 단어·용어·도메인을 골라 기관 사전으로 복사 -->
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3" style="gap:8px;">
        <h2><v-icon>mdi-database-import-outline</v-icon>&nbsp;&nbsp;공통표준 가져오기</h2>
        <v-spacer />
        <span class="si-label">가져올 사전</span>
        <DictSelector v-model="dictId" @change="load" @meta="targetMeta = $event" />
      </v-row>

      <div class="si-guide">
        원본 사전(보통 행안부 공통표준 같은 참조 사전)에서 골라 지금 고른 사전으로 복사합니다. 용어를 가져오면 구성 단어와 도메인(그룹·분류 포함)도
        없으면 함께 가져오고, 코드면 코드값도 가져옵니다. 가져온 항목은 승인 상태이고 공통표준여부가 Y 입니다. 이 사전에 같은 이름(단어·용어는 같은 영문약어도)이 있으면 건너뜁니다.
      </div>
      <v-alert v-if="targetMeta && targetMeta.dictTp === 'REF'" dense text type="warning" class="mb-2">
        지금 고른 사전은 참조 사전입니다. 가져올 기관 사전을 고르세요.
      </v-alert>

      <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
        <span class="si-label">원본 사전</span>
        <v-select id="importSource" v-model="sourceDictId" :items="sourceOptions" item-text="label" item-value="dictId"
          dense outlined hide-details style="max-width:240px;" @change="load" />
        <v-btn-toggle id="importKind" v-model="kind" mandatory dense color="indigo" @change="load">
          <v-btn small value="WORD">단어</v-btn>
          <v-btn small value="TERM">용어</v-btn>
          <v-btn small value="DOMAIN">도메인</v-btn>
        </v-btn-toggle>
        <v-text-field id="importKeyword" v-model="keyword" label="이름·영문약어" dense outlined hide-details clearable
          style="max-width:220px;" v-on:keyup.enter="load" />
        <v-checkbox v-model="commOnly" true-value="Y" false-value="N" label="공통표준만" dense hide-details class="ma-0 pa-0" @change="load" />
        <v-btn id="importSearch" small outlined color="primary" v-on:click="load">조회</v-btn>
        <v-spacer />
        <span class="si-count">총 {{ items.length }}건{{ items.length >= 2000 ? ' (2,000건까지)' : '' }} · 선택 {{ selected.length }}건</span>
        <v-btn id="importRun" small color="primary" :disabled="!canRun" :loading="running" v-on:click="run">
          <v-icon small left>mdi-download</v-icon>가져오기
        </v-btn>
      </v-row>

      <v-data-table id="importTable" v-model="selected" :headers="headers" :items="items" item-key="id" show-select
        selectable-key="selectable" dense :items-per-page="50" :loading="loading">
        <template #item.existYn="{ item }">
          <v-chip v-if="item.existYn === 'Y'" x-small outlined color="grey">이미 있음</v-chip>
        </template>
        <template #item.commStndYn="{ item }">{{ item.commStndYn }}</template>
        <template #no-data>항목이 없습니다.</template>
      </v-data-table>

      <!-- 결과 -->
      <v-dialog v-model="resultDialog" max-width="760" scrollable>
        <v-card id="importResult">
          <v-card-title>가져오기 결과</v-card-title>
          <v-card-text>
            <div class="mb-2">가져옴 {{ result.imported }}건 · 건너뜀 {{ result.skipped }}건 · 실패 {{ result.failed }}건
              <span v-if="result.addedWords || result.addedDomains" class="si-sub"> (함께 만든 단어 {{ result.addedWords }}개 · 도메인 {{ result.addedDomains }}개 포함)</span>
            </div>
            <v-simple-table dense>
              <thead><tr><th>이름</th><th>결과</th><th>사유</th></tr></thead>
              <tbody>
                <tr v-for="(d, i) in result.details" :key="i">
                  <td>{{ d.nm }}</td>
                  <td>
                    <v-chip x-small :color="d.status === 'IMPORTED' ? 'green' : d.status === 'SKIPPED' ? 'grey' : 'red'" text-color="white">
                      {{ d.status === 'IMPORTED' ? '가져옴' : d.status === 'SKIPPED' ? '건너뜀' : '실패' }}</v-chip>
                  </td>
                  <td>{{ d.message }}</td>
                </tr>
              </tbody>
            </v-simple-table>
          </v-card-text>
          <v-card-actions><v-spacer /><v-btn text v-on:click="resultDialog = false">닫기</v-btn></v-card-actions>
        </v-card>
      </v-dialog>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
import DictSelector from './DictSelector.vue';
export default {
  name: 'DSStdImport',
  components: { DictSelector },
  props: ['isMobile'],
  data: () => ({
    dictId: null,
    targetMeta: null,
    dicts: [],
    sourceDictId: null,
    kind: 'WORD',
    keyword: '',
    commOnly: 'Y',
    items: [],
    selected: [],
    loading: false,
    running: false,
    resultDialog: false,
    result: {},
  }),
  computed: {
    // 원본 후보 — 고른 사전을 뺀 사전, 참조 사전을 앞에
    sourceOptions() {
      return this.dicts.filter(d => d.dictId !== this.dictId)
        .sort((a, b) => (a.dictTp === 'REF' ? 0 : 1) - (b.dictTp === 'REF' ? 0 : 1))
        .map(d => ({ dictId: d.dictId, label: d.dictNm + (d.dictTp === 'REF' ? ' (참조)' : '') + (d.defaultYn === 'Y' ? ' (기본)' : '') }));
    },
    canRun() {
      return this.$can('STD_MANAGE') && this.selected.length > 0 && this.sourceDictId && !(this.targetMeta && this.targetMeta.dictTp === 'REF');
    },
    headers() {
      const nm = { WORD: '단어명', TERM: '용어명', DOMAIN: '도메인명' }[this.kind];
      const extra = { WORD: '영문명', TERM: '도메인', DOMAIN: '데이터타입' }[this.kind];
      const h = [{ text: nm, value: 'nm' }];
      if (this.kind !== 'DOMAIN') h.push({ text: '영문약어명', value: 'engAbrvNm' });
      h.push({ text: extra, value: 'extra' }, { text: '공통표준', value: 'commStndYn', width: '90px' }, { text: '이 사전', value: 'existYn', width: '100px' });
      return h;
    },
  },
  watch: {
    // 가져올 사전을 바꿨는데 원본과 같아지면 원본을 다시 고른다
    dictId() {
      if (this.dicts.length && (!this.sourceDictId || this.sourceDictId === this.dictId)) this.pickSource();
    },
  },
  mounted() {
    axios.post(this.$APIURL.base + 'api/dict/list', {}).then(res => {
      this.dicts = res.data || [];
      this.pickSource();
      this.load();
    });
  },
  methods: {
    // 원본 기본값 — 참조 사전, 없으면 기본 사전 (가져올 사전이 아닌 것)
    pickSource() {
      const ref = this.dicts.find(d => d.dictTp === 'REF' && d.dictId !== this.dictId);
      const def = this.dicts.find(d => d.defaultYn === 'Y' && d.dictId !== this.dictId);
      this.sourceDictId = (ref || def || {}).dictId || null;
    },
    load() {
      this.selected = [];
      if (!this.sourceDictId || !this.dictId) return;
      this.loading = true;
      axios.post(this.$APIURL.base + 'api/stdImport/list', {
        sourceDictId: this.sourceDictId, targetDictId: this.dictId, kind: this.kind, keyword: this.keyword || null, commOnly: this.commOnly,
      }).then(res => {
        this.items = ((res.data && res.data.items) || []).map(x => Object.assign(x, { selectable: x.existYn !== 'Y' }));
      }).catch(() => { this.items = []; })
        .finally(() => { this.loading = false; });
    },
    run() {
      this.running = true;
      axios.post(this.$APIURL.base + 'api/stdImport/run', {
        sourceDictId: this.sourceDictId, targetDictId: this.dictId, kind: this.kind, ids: this.selected.map(x => x.id),
      }).then(res => {
        const d = res.data || {};
        if (d.error) {
          this.$swal.fire({ title: d.error, icon: 'error' });
          return;
        }
        this.result = d;
        this.resultDialog = true;
        this.load();
      }).catch(() => {
        this.$swal.fire({ title: '가져오기 실패 - API 확인 필요', icon: 'error' });
      }).finally(() => { this.running = false; });
    },
  },
};
</script>
<style scoped>
.si-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.si-label { font-size: .85rem; color: #37474F; }
.si-count { font-size: .85rem; color: #546E7A; }
.si-sub { font-size: .8rem; color: #78909C; }
</style>
