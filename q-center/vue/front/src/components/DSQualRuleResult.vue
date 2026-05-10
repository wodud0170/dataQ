<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">

      <!-- 상단: 모델 + 진단 이력 선택 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <span style="font-size:1.1rem; font-weight:600; color:#1A237E;">진단 결과</span>
        <span style="font-size:.8rem; color:#9E9E9E; margin-right:8px;">— 룰 진단 이력별 결과 분석 (룰별/도메인분류/상세)</span>
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" @change="loadHistoryList"
          id="cmb-rr-model"></v-autocomplete>
        <v-select v-model="diagType" :items="['RULE','VALUE']" label="유형" dense hide-details
          style="max-width:120px" @change="loadHistoryList" id="cmb-rr-type"></v-select>
        <v-autocomplete v-model="diagId" :items="histories" :item-text="histLabel" item-value="diagId"
          label="진단 이력 (최근 50)" dense hide-details clearable
          style="max-width:380px" @change="loadAll" id="cmb-rr-diag"></v-autocomplete>
        <v-spacer></v-spacer>
        <v-btn small text @click="loadAll" :disabled="!diagId" id="btn-rr-reload">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-sheet>

      <!-- 진단 헤더 요약 -->
      <v-sheet v-if="history" class="pa-2" style="background:#F5F5F5; border-bottom:1px solid #E0E0E0;">
        <span style="font-size:.85rem;">
          상태:
          <v-chip x-small :color="statusColor(history.status)" text-color="white">{{ history.status }}</v-chip>
          | 모델: <b>{{ history.dataModelNm }}</b>
          | 룰 {{ history.totalRules || 0 }} | 위반 합 {{ history.totalViolations || 0 }}
          | 컬럼 {{ history.totalCols || 0 }}
          | 실행 {{ history.execUserId || '-' }}
          | {{ history.diagDt || '' }}
        </span>
      </v-sheet>

      <!-- 4 탭 -->
      <v-tabs v-model="tab" dense>
        <v-tab id="tab-rr-rule">진단 단위 (룰별)</v-tab>
        <v-tab id="tab-rr-clsf">도메인 분류 단위</v-tab>
        <v-tab id="tab-rr-detail">상세 (컬럼+룰)</v-tab>
      </v-tabs>

      <v-tabs-items v-model="tab" style="flex:1; overflow:auto;">

        <!-- 탭 1: 룰 단위 집계 -->
        <v-tab-item>
          <v-data-table :headers="ruleHeaders" :items="ruleAgg" dense hide-default-footer
            :items-per-page="200" class="elevation-0" :loading="loading">
            <template v-slot:item.severity="{ item }">
              <v-chip x-small :color="sevColor(item.severity)" text-color="white">{{ item.severity || '-' }}</v-chip>
            </template>
            <template v-slot:item.conformRate="{ item }">
              <span v-if="item.conformRate != null"
                :style="{color: rateColor(item.conformRate), 'font-weight': 600}">
                {{ Number(item.conformRate).toFixed(1) }}%
              </span>
              <span v-else style="color:#9E9E9E">-</span>
            </template>
          </v-data-table>
        </v-tab-item>

        <!-- 탭 2: 도메인 분류 단위 -->
        <v-tab-item>
          <v-sheet class="pa-3">
            <p style="font-size:.85rem; color:#546E7A;">분류 클릭 시 컬럼별 결과로 drill-down</p>
            <div v-if="clsfAgg.length === 0 && !loading" style="padding:24px; text-align:center; color:#9E9E9E;">
              결과 없음
            </div>
            <div v-for="row in clsfAgg" :key="row.domainClsfNm" class="clsf-row"
                 @click="drillClsf(row.domainClsfNm)" :class="{active: drillTarget === row.domainClsfNm}">
              <div class="clsf-label">
                <b>{{ row.domainClsfNm }}</b>
                <span style="font-size:.7rem; color:#90A4AE; margin-left:6px;">
                  컬럼 {{ row.colCnt }} / 룰 {{ row.ruleCnt }}
                </span>
              </div>
              <div class="clsf-bar-wrap">
                <div class="clsf-bar" :style="{width: barPct(row.conformRate),
                    background: rateColor(row.conformRate)}"></div>
              </div>
              <div class="clsf-rate" :style="{color: rateColor(row.conformRate)}">
                {{ row.conformRate != null ? Number(row.conformRate).toFixed(1) + '%' : '-' }}
              </div>
            </div>

            <!-- drill-down 결과 -->
            <v-divider class="my-3" v-if="drillRows.length"></v-divider>
            <p v-if="drillRows.length" style="font-size:.85rem;">
              <b>분류 [{{ drillTarget }}]</b> — 컬럼별 결과 ({{ drillRows.length }}건)
            </p>
            <v-data-table v-if="drillRows.length"
              :headers="drillHeaders" :items="drillRows" dense hide-default-footer
              :items-per-page="200" class="elevation-0">
              <template v-slot:item.conformRate="{ item }">
                <span v-if="item.conformRate != null" :style="{color: rateColor(item.conformRate), 'font-weight':600}">
                  {{ Number(item.conformRate).toFixed(1) }}%
                </span>
                <span v-else style="color:#9E9E9E">-</span>
              </template>
              <template v-slot:item.actions="{ item }">
                <v-btn x-small text @click="openSamples(item)" :disabled="!item.violationCnt"
                  id="btn-violation-sample">
                  <v-icon x-small left>mdi-eye</v-icon>위반 샘플
                </v-btn>
              </template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>

        <!-- 탭 3: 상세 (raw) -->
        <v-tab-item>
          <v-data-table :headers="rawHeaders" :items="rawResults" dense hide-default-footer
            :items-per-page="500" class="elevation-0" :loading="loading">
            <template v-slot:item.severity="{ item }">
              <v-chip x-small :color="sevColor(item.severity)" text-color="white">{{ item.severity || '-' }}</v-chip>
            </template>
            <template v-slot:item.violationRate="{ item }">
              <span :style="{color: rateColor(100 - (item.violationRate || 0))}">
                {{ Number(item.violationRate || 0).toFixed(1) }}%
              </span>
            </template>
          </v-data-table>
        </v-tab-item>
      </v-tabs-items>
    </v-sheet>

    <!-- 위반 샘플 drawer -->
    <v-navigation-drawer v-model="sampleDrawer" right temporary fixed width="600" style="z-index:99">
      <v-sheet v-if="sampleCtx" class="pa-4">
        <h3>위반 샘플 — {{ sampleCtx.objNm }}.{{ sampleCtx.attrNm }}</h3>
        <p style="font-size:.8rem; color:#90A4AE;">룰: {{ sampleCtx.ruleNm }}</p>
        <v-divider class="my-2"></v-divider>
        <v-data-table :headers="sampleHeaders" :items="samples" dense hide-default-footer
          :items-per-page="100" class="elevation-0">
        </v-data-table>
        <p v-if="samples.length === 0" style="font-size:.8rem; color:#9E9E9E; padding:12px;">
          저장된 위반 샘플 없음
        </p>
      </v-sheet>
    </v-navigation-drawer>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualRuleResult',
  data() {
    return {
      tab: 0,
      dataModels: [],
      dmId: null,
      diagType: 'RULE',
      histories: [],
      diagId: null,
      history: null,
      loading: false,
      // 탭 1: 룰 단위
      ruleAgg: [],
      ruleHeaders: [
        { text: '룰명',     value: 'ruleNm' },
        { text: '유형',     value: 'ruleType', width: 90 },
        { text: '심각도',   value: 'severity', width: 80 },
        { text: '컬럼수',   value: 'colCnt',   width: 80 },
        { text: '적용수',   value: 'applyCnt', width: 80 },
        { text: '전체',     value: 'totalCnt', width: 100 },
        { text: '위반',     value: 'violationCnt', width: 100 },
        { text: '적합률',   value: 'conformRate', width: 90 }
      ],
      // 탭 2: 분류 단위
      clsfAgg: [],
      drillTarget: null,
      drillRows: [],
      drillHeaders: [
        { text: '테이블',   value: 'objNm' },
        { text: '컬럼',     value: 'attrNm' },
        { text: '도메인',   value: 'domainNm' },
        { text: '룰명',     value: 'ruleNm' },
        { text: '전체',     value: 'totalCnt', width: 90 },
        { text: '위반',     value: 'violationCnt', width: 90 },
        { text: '적합률',   value: 'conformRate', width: 90 },
        { text: '',         value: 'actions', sortable: false, width: 110 }
      ],
      // 탭 3: 상세
      rawResults: [],
      rawHeaders: [
        { text: '룰명',     value: 'ruleNm' },
        { text: '유형',     value: 'ruleType', width: 80 },
        { text: '심각도',   value: 'severity', width: 80 },
        { text: '테이블',   value: 'objNm' },
        { text: '컬럼',     value: 'attrNm' },
        { text: '전체',     value: 'totalCnt', width: 100 },
        { text: '위반',     value: 'violationCnt', width: 100 },
        { text: '위반률',   value: 'violationRate', width: 90 },
        { text: '에러',     value: 'errorMsg' }
      ],
      // 위반 샘플
      sampleDrawer: false,
      sampleCtx: null,
      samples: [],
      sampleHeaders: [
        { text: 'PK',       value: 'pkValues' },
        { text: '위반 값',   value: 'violatingVal' },
        { text: '#',        value: 'seq', width: 50 }
      ]
    };
  },
  mounted() {
    var self = this;
    // 86번 #46 — 모든 axios 에 .catch 추가
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {})
      .then(function(r) {
        self.dataModels = (r.data || []).filter(function(m) { return m.modelType === 'PHYSICAL'; });
      })
      .catch(function(err) { console.error('모델 목록 로드 실패:', err); self.dataModels = []; });
  },
  methods: {
    histLabel(item) {
      if (!item) return '';
      var s = item.status || '';
      var dt = (item.diagDt || '').toString().substring(0, 19).replace('T', ' ');
      return `[${s}] ${dt}  rules=${item.totalRules || 0} viol=${item.totalViolations || 0}`;
    },
    loadHistoryList() {
      if (!this.dmId) { this.histories = []; this.diagId = null; return; }
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/rule/historyList',
                { params: { dmId: this.dmId, diagType: this.diagType } })
        .then(function(r) {
          self.histories = (r.data || []);
          var d = self.histories.filter(function(h) { return h.status === 'DONE'; });
          self.diagId = (d[0] && d[0].diagId) || null;
          if (self.diagId) self.loadAll();
        })
        .catch(function(err) { console.error('이력 로드 실패:', err); self.histories = []; });
    },
    loadAll() {
      if (!this.diagId) return;
      this.loading = true;
      var self = this;
      var base = this.$APIURL.base;
      Promise.all([
        axios.get(base + 'api/qual/rule/history/' + this.diagId),
        axios.get(base + 'api/qual/rule/result',         { params: { diagId: this.diagId } }),
        axios.get(base + 'api/qual/rule/resultByRule',   { params: { diagId: this.diagId } }),
        axios.get(base + 'api/qual/rule/resultByClsf',   { params: { diagId: this.diagId } })
      ]).then(function(arr) {
        self.history = arr[0].data || null;
        var c = arr[1].data && arr[1].data.contents;
        if (typeof c === 'string') { try { c = JSON.parse(c); } catch (e) { c = {}; } }
        self.rawResults = (c && c.results) || [];
        self.ruleAgg = arr[2].data || [];
        self.clsfAgg = arr[3].data || [];
        self.drillTarget = null;
        self.drillRows = [];
      }).catch(function(err) {
        console.error('진단 결과 로드 실패:', err);
        self.history = null; self.rawResults = []; self.ruleAgg = []; self.clsfAgg = [];
      }).finally(function() { self.loading = false; });
    },
    drillClsf(clsfNm) {
      if (this.drillTarget === clsfNm) {
        this.drillTarget = null;
        this.drillRows = [];
        return;
      }
      var self = this;
      this.drillTarget = clsfNm;
      axios.get(this.$APIURL.base + 'api/qual/rule/resultByClsfDrill',
                { params: { diagId: this.diagId, domainClsfNm: clsfNm } })
        .then(function(r) { self.drillRows = r.data || []; })
        .catch(function(err) { console.error('드릴다운 로드 실패:', err); self.drillRows = []; });
    },
    openSamples(item) {
      var self = this;
      this.sampleCtx = item;
      this.sampleDrawer = true;
      axios.get(this.$APIURL.base + 'api/qual/rule/violationSample',
                { params: { diagId: this.diagId, ruleId: item.ruleId } })
        .then(function(r) {
          self.samples = (r.data || []).filter(function(s) {
            return s.objNm === item.objNm && s.attrNm === item.attrNm;
          });
        })
        .catch(function(err) { console.error('위반 샘플 로드 실패:', err); self.samples = []; });
    },
    barPct(r) {
      if (r == null) return '0%';
      var v = Math.max(0, Math.min(100, r));
      return v + '%';
    },
    statusColor(s) {
      return s === 'DONE' ? 'green' : s === 'ERROR' ? 'red' :
             s === 'RUNNING' ? 'orange' : s === 'SKIPPED' ? 'grey' : 'blue';
    },
    sevColor(s) { return s === 'ERROR' ? 'red' : s === 'WARN' ? 'orange' : 'blue'; },
    rateColor(r) {
      if (r == null) return '#9E9E9E';
      if (r >= 95) return '#2E7D32';
      if (r >= 80) return '#F57F17';
      return '#C62828';
    }
  }
};
</script>

<style scoped>
.clsf-row {
  display: flex; align-items: center;
  padding: 6px 8px; gap: 12px;
  border-bottom: 1px solid #ECEFF1;
  cursor: pointer;
}
.clsf-row:hover { background: #F5F5F5; }
.clsf-row.active { background: #E3F2FD; }
.clsf-label { width: 200px; font-size: .85rem; }
.clsf-bar-wrap { flex: 1; height: 18px; background: #ECEFF1; border-radius: 4px; overflow: hidden; }
.clsf-bar { height: 100%; transition: width .3s; }
.clsf-rate { width: 80px; text-align: right; font-weight: 600; }
</style>
