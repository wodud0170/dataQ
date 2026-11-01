<template>
  <!-- 108 §4.37 (92번) — 분석 > 종합 분석 보고서: 모델 하나를 여러 시트의 xlsx 로 -->
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-file-chart</v-icon>&nbsp;&nbsp;종합 분석 보고서</h2>
        <span class="ml-4 grey--text text--darken-1 rep-desc">
          데이터 모델의 구조·표준화·DBA 점검·변경·진단 결과를 시트 여러 개의 엑셀 파일 하나로 내려받습니다.
        </span>
        <v-spacer />
        <!-- 조건 카드가 길어 버튼은 제목 줄에 (1366×768 에서 스크롤 없이 보이게) -->
        <v-btn id="repDownload" class="btn-auto" color="primary" depressed :loading="downloading"
          :disabled="!dmId || !checked.length" @click="download">
          <v-icon small left>mdi-microsoft-excel</v-icon>보고서 내려받기 (엑셀, 시트 {{ checked.length }}개)
        </v-btn>
      </v-row>

      <v-row>
        <!-- 왼쪽: 조건 -->
        <v-col cols="12" md="5" class="pb-0">
          <v-card outlined class="pa-3 rep-card">
            <div class="rep-sec">대상</div>
            <v-autocomplete id="repModel" v-model="dmId" :items="models" item-text="label" item-value="dataModelId"
              label="데이터 모델 *" dense outlined hide-details clearable color="ndColor" class="mb-3"
              no-data-text="데이터 모델이 없습니다" @change="onModel" />
            <v-select id="repOwners" v-model="owners" :items="ownerItems" label="소유자 (비우면 전체)" multiple chips small-chips
              deletable-chips dense outlined hide-details color="ndColor" :disabled="!dmId" class="mb-1"
              :no-data-text="dmId ? '소유자가 없습니다' : '데이터 모델을 먼저 고르세요'" />

            <div class="rep-sec mt-3">프리셋</div>
            <v-radio-group id="repPreset" v-model="preset" row dense hide-details class="mt-0" @change="onPreset">
              <v-radio v-for="p in presetItems" :key="p.value" :label="p.text" :value="p.value" color="ndColor" class="mr-3 mb-1" />
            </v-radio-group>

            <div class="rep-sec mt-3">
              포함 시트 <span class="grey--text" style="font-weight:normal;">({{ checked.length }}/{{ sheets.length }}{{ preset === 'CUSTOM' ? '' : ' — 커스텀일 때 고칠 수 있습니다' }})</span>
            </div>
            <div v-for="g in groups" :key="g.cat" class="rep-group">
              <div class="rep-cat">{{ g.cat }}</div>
              <div class="rep-checks">
                <v-checkbox v-for="s in g.items" :key="s.id" v-model="checked" :value="s.id" :id="'repSheet_' + s.id"
                  :label="s.id + ' ' + s.title" :disabled="preset !== 'CUSTOM'" dense hide-details color="ndColor" class="mt-0 rep-check" />
              </div>
            </div>

            <div class="rep-sec mt-3">옵션</div>
            <v-row dense>
              <v-col cols="6">
                <v-select id="repDays" v-model="historyDays" :items="dayItems" label="변경 이력 기간" dense outlined hide-details color="ndColor" />
              </v-col>
              <v-col cols="6">
                <v-select id="repDbType" v-model="ddlDbType" :items="dbItems" label="DDL DBMS" dense outlined hide-details color="ndColor" />
              </v-col>
            </v-row>
          </v-card>
        </v-col>

        <!-- 오른쪽: 미리보기 -->
        <v-col cols="12" md="7" class="pb-0">
          <v-card outlined class="pa-3 rep-card">
            <div class="d-flex align-center" style="gap:8px;">
              <div class="rep-sec">미리보기</div>
              <v-select id="repPreviewSheet" v-model="previewId" :items="previewItems" label="시트" dense outlined hide-details
                color="ndColor" style="max-width:260px;" :disabled="!dmId" @change="loadPreview" />
              <span v-if="preview" class="grey--text" style="font-size:.8rem;">앞 {{ preview.rows.length }}행 / 전체 {{ preview.total }}행</span>
              <v-progress-circular v-if="previewLoading" indeterminate size="18" width="2" color="primary" />
            </div>
            <div v-if="!dmId" class="rep-empty">데이터 모델을 고르면 시트 앞 10행을 미리 볼 수 있습니다.</div>
            <div v-else-if="previewError" class="rep-empty red--text">{{ previewError }}</div>
            <template v-else-if="preview">
              <div v-for="(n, i) in preview.notes" :key="i" class="rep-note">{{ n }}</div>
              <div class="rep-table-wrap">
                <table id="repPreviewTable" class="rep-table">
                  <thead><tr><th v-for="(h, i) in preview.headers" :key="i">{{ h }}</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, i) in preview.rows" :key="i" :class="{ 'rep-flag': preview.flagged.includes(i) }">
                      <td v-for="(c, j) in r" :key="j">{{ c === null || c === undefined ? '' : c }}</td>
                    </tr>
                    <tr v-if="!preview.rows.length"><td :colspan="preview.headers.length" class="grey--text">해당하는 행이 없습니다.</td></tr>
                  </tbody>
                </table>
              </div>
              <div class="grey--text mt-1" style="font-size:.72rem;">연한 빨강 행 — 이슈·점검에 걸린 행 (엑셀에도 같은 색)</div>
            </template>
          </v-card>
        </v-col>
      </v-row>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';

const STORE = 'navidmeta.analysisReport';

export default {
  name: 'AnalysisReport',
  props: ['isMobile'],
  data: () => ({
    models: [],
    dmId: null,
    ownerItems: [],
    owners: [],
    sheets: [],
    presets: {},
    preset: 'ALL',
    checked: [],
    historyDays: 30,
    ddlDbType: null,
    presetItems: [
      { text: '전체 종합', value: 'ALL' }, { text: 'DBA 점검', value: 'DBA' }, { text: '표준화 현황', value: 'STD' },
      { text: '거버넌스', value: 'GOV' }, { text: '커스텀', value: 'CUSTOM' },
    ],
    dayItems: [{ text: '최근 7일', value: 7 }, { text: '최근 30일', value: 30 }, { text: '최근 90일', value: 90 }, { text: '최근 365일', value: 365 }],
    dbItems: [
      { text: '모델 데이터 소스 따름', value: null }, { text: 'Oracle', value: 'Oracle' }, { text: 'Tibero', value: 'Tibero' },
      { text: 'PostgreSQL', value: 'PostgreSQL' }, { text: 'MariaDB', value: 'MariaDB' }, { text: 'SQL Server', value: 'SQLServer' },
    ],
    previewId: 'A1',
    preview: null,
    previewError: '',
    previewLoading: false,
    downloading: false,
  }),
  computed: {
    groups() {
      const out = [];
      this.sheets.forEach(s => {
        let g = out.find(x => x.cat === s.cat);
        if (!g) { g = { cat: s.cat, items: [] }; out.push(g); }
        g.items.push(s);
      });
      return out;
    },
    previewItems() {
      return this.sheets.filter(s => this.checked.includes(s.id)).map(s => ({ text: s.id + ' ' + s.title, value: s.id }));
    },
  },
  watch: {
    owners() { this.save(); this.loadPreview(); },
    historyDays() { this.save(); if (/^E/.test(this.previewId)) this.loadPreview(); },
    ddlDbType() { this.save(); if (this.previewId === 'G1') this.loadPreview(); },
    checked() {
      this.save();
      if (this.checked.length && !this.checked.includes(this.previewId)) { this.previewId = this.checked[0]; this.loadPreview(); }
    },
  },
  mounted() {
    axios.post(this.$APIURL.base + 'api/report/sheets', {}).then(r => {
      this.sheets = r.data.sheets || [];
      this.presets = r.data.presets || {};
      this.restore();
    });
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(r => {
      this.models = (Array.isArray(r.data) ? r.data : [])
        .map(m => ({ dataModelId: m.dataModelId, label: m.dataModelNm + (m.dataModelSysNm ? ' · ' + m.dataModelSysNm : '') }))
        .sort((a, b) => a.label.localeCompare(b.label));
    });
  },
  methods: {
    // 마지막 선택은 이 브라우저에 기억 (없거나 못 읽어도 기본값으로 동작)
    restore() {
      let v = null;
      try { v = JSON.parse(localStorage.getItem(STORE) || 'null'); } catch (e) { v = null; }
      if (v) {
        this.preset = this.presetItems.some(p => p.value === v.preset) ? v.preset : 'ALL';
        this.historyDays = [7, 30, 90, 365].includes(v.historyDays) ? v.historyDays : 30;
        this.ddlDbType = v.ddlDbType || null;
      }
      this.checked = this.preset === 'CUSTOM' && v && Array.isArray(v.checked)
        ? v.checked.filter(id => this.sheets.some(s => s.id === id)) : (this.presets[this.preset] || []).slice();
      if (v && v.dmId) { this.dmId = v.dmId; this.onModel(); }
    },
    save() {
      try {
        localStorage.setItem(STORE, JSON.stringify({ dmId: this.dmId, preset: this.preset, checked: this.checked,
          historyDays: this.historyDays, ddlDbType: this.ddlDbType }));
      } catch (e) { /* 저장 못 해도 화면은 그대로 */ }
    },
    onPreset() {
      if (this.preset !== 'CUSTOM') this.checked = (this.presets[this.preset] || []).slice();
      this.save();
    },
    onModel() {
      this.owners = [];
      this.ownerItems = [];
      this.preview = null;
      this.save();
      if (!this.dmId) return;
      axios.get(this.$APIURL.base + 'api/dm/getDataModelObjListByClctId', { params: { clctId: this.dmId } }).then(r => {
        const set = new Set((r.data || []).map(o => o.objOwner || ''));
        this.ownerItems = [...set].sort().map(o => ({ text: o || '(소유자 없음)', value: o }));
      });
      this.loadPreview();
    },
    body() {
      return { dataModelId: this.dmId, owners: this.owners, historyDays: this.historyDays, ddlDbType: this.ddlDbType };
    },
    loadPreview() {
      if (!this.dmId || !this.previewId) return;
      this.previewLoading = true;
      this.previewError = '';
      const id = this.previewId;
      axios.post(this.$APIURL.base + 'api/report/preview', { ...this.body(), sheet: id }).then(r => {
        if (id !== this.previewId) return;
        if (r.data.resultCode !== 200) { this.preview = null; this.previewError = r.data.resultMessage || '미리보기를 만들지 못했습니다.'; return; }
        this.preview = JSON.parse(r.data.contents);
      }).catch(() => { this.preview = null; this.previewError = '미리보기를 만들지 못했습니다.'; })
        .finally(() => { this.previewLoading = false; });
    },
    download() {
      this.downloading = true;
      axios.post(this.$APIURL.base + 'api/report/download', { ...this.body(), sheets: this.checked }, { responseType: 'blob' }).then(r => {
        const cd = r.headers['content-disposition'] || '';
        const m = /filename="?([^";]+)"?/.exec(cd);
        const name = m ? decodeURIComponent(m[1]) : '종합분석.xlsx';
        const url = URL.createObjectURL(r.data);
        const a = document.createElement('a');
        a.href = url; a.download = name;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }).catch(e => {
        // 실패 본문은 JSON {resultMessage} — blob 으로 받았으니 글로 읽는다
        const blob = e.response && e.response.data;
        const show = t => this.$swal.fire({ title: '내려받기 실패', text: t || '보고서를 만들지 못했습니다.', icon: 'error', confirmButtonText: '확인' });
        if (blob && blob.text) blob.text().then(t => { try { show(JSON.parse(t).resultMessage); } catch (x) { show(''); } });
        else show('');
      }).finally(() => { this.downloading = false; });
    },
  },
};
</script>

<style scoped>
.rep-card { min-height: 100%; }
.rep-desc { font-size: .8rem; word-break: keep-all; }
.rep-sec { font-size: .85rem; font-weight: 600; color: #1A237E; margin-bottom: 6px; }
.rep-group { margin-bottom: 4px; }
.rep-cat { font-size: .75rem; color: #546E7A; margin: 4px 0 0; }
.rep-checks { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); column-gap: 8px; }
.rep-check >>> label { font-size: .8rem; word-break: keep-all; }
.rep-empty { padding: 32px 8px; text-align: center; font-size: .85rem; color: #90A4AE; }
.rep-note { font-size: .78rem; color: #78909C; margin: 6px 0 2px; }
.rep-table-wrap { overflow: auto; max-height: calc(100vh - 290px); margin-top: 6px; border: 1px solid #E0E0E0; }
.rep-table { border-collapse: collapse; font-size: .76rem; min-width: 100%; }
.rep-table th { position: sticky; top: 0; background: #ECEFF1; text-align: left; padding: 4px 8px; white-space: nowrap; border-bottom: 1px solid #CFD8DC; }
.rep-table td { padding: 3px 8px; border-bottom: 1px solid #F0F0F0; white-space: nowrap; max-width: 320px; overflow: hidden; text-overflow: ellipsis; }
.rep-table tr.rep-flag td { background: #FDECEA; }
</style>
