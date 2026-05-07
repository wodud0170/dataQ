<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">

      <!-- 상단 필터 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" @change="onModelChange"
          id="cmb-stats-model"></v-autocomplete>
        <v-autocomplete v-model="objNm" :items="objList" label="테이블" dense hide-details clearable
          style="max-width:200px" @change="onObjChange" id="cmb-stats-obj"></v-autocomplete>
        <v-autocomplete v-model="attrNm" :items="attrList" label="컬럼" dense hide-details clearable
          style="max-width:200px" id="cmb-stats-attr"></v-autocomplete>
        <v-btn small @click="loadAll" :disabled="!dmId" id="btn-stats-load" class="ml-2">
          <v-icon small left>mdi-chart-line</v-icon>조회
        </v-btn>
        <v-spacer></v-spacer>
        <span style="font-size:.75rem; color:#90A4AE;">DONE 진단 최근 30회 기준</span>
      </v-sheet>

      <!-- 모델 단위 적합률 추이 -->
      <v-sheet class="pa-3" style="border-bottom:1px solid #ECEFF1;">
        <h4 style="margin:0 0 4px 0; font-size:1rem;">
          모델 적합률 추이
          <span style="font-size:.75rem; color:#90A4AE; font-weight:400;">— 룰 진단 N회 평균</span>
        </h4>
        <div v-if="!dmId" style="padding:24px; text-align:center; color:#9E9E9E;">
          모델을 선택하세요
        </div>
        <div v-else-if="modelSeries[0] && modelSeries[0].data.length === 0"
          style="padding:24px; text-align:center; color:#9E9E9E;" id="empty-model-trend">
          DONE 진단 이력 없음
        </div>
        <apexchart v-else type="line" height="220" :options="modelChartOpts"
          :series="modelSeries" :key="modelChartKey"></apexchart>
      </v-sheet>

      <!-- 컬럼 단위: 룰별 + 프로파일 추이 -->
      <v-sheet v-if="objNm && attrNm" class="pa-3">
        <h4 style="margin:0 0 4px 0; font-size:1rem;">
          [{{ objNm }}.{{ attrNm }}] 컬럼 추이
        </h4>
        <v-tabs v-model="colTab" dense>
          <v-tab id="tab-col-rule">룰별 적합률</v-tab>
          <v-tab id="tab-col-profile">NULL% / DISTINCT%</v-tab>
        </v-tabs>
        <v-tabs-items v-model="colTab">
          <v-tab-item>
            <div v-if="ruleSeries.length === 0" style="padding:24px; text-align:center; color:#9E9E9E;"
              id="empty-rule-trend">
              룰 진단 이력 없음
            </div>
            <apexchart v-else type="line" height="280" :options="ruleChartOpts"
              :series="ruleSeries" :key="ruleChartKey"></apexchart>
          </v-tab-item>
          <v-tab-item>
            <div v-if="profSeries[0] && profSeries[0].data.length === 0"
              style="padding:24px; text-align:center; color:#9E9E9E;" id="empty-prof-trend">
              값 진단 이력 없음
            </div>
            <apexchart v-else type="line" height="280" :options="profChartOpts"
              :series="profSeries" :key="profChartKey"></apexchart>
          </v-tab-item>
        </v-tabs-items>
      </v-sheet>

    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';
import VueApexCharts from 'vue-apexcharts';

export default {
  name: 'DSQualStats',
  components: { 'apexchart': VueApexCharts },
  data() {
    return {
      dataModels: [], dmId: null,
      objList: [], objNm: null,
      attrList: [], attrNm: null,
      colTab: 0,
      // 모델 트렌드
      modelSeries: [{ name: '적합률 (%)', data: [] }],
      modelChartKey: 0,
      // 컬럼 룰 트렌드
      ruleSeries: [],
      ruleChartKey: 0,
      // 컬럼 프로파일 트렌드
      profSeries: [
        { name: 'NULL%',     data: [] },
        { name: 'DISTINCT%', data: [] }
      ],
      profChartKey: 0,
      // 모델 attr 캐시 (테이블/컬럼 콤보 채우기)
      modelAttrs: []
    };
  },
  computed: {
    modelChartOpts() {
      return {
        chart:   { id: 'model-trend', toolbar: { show: false }, animations: { enabled: true } },
        stroke:  { curve: 'smooth', width: 3 },
        markers: { size: 4 },
        colors:  ['#1976D2'],
        xaxis:   { type: 'datetime', labels: { format: 'MM/dd HH:mm' } },
        yaxis:   { min: 0, max: 100, decimalsInFloat: 1, title: { text: '%' } },
        tooltip: { x: { format: 'yyyy-MM-dd HH:mm' } },
        grid:    { borderColor: '#ECEFF1' }
      };
    },
    ruleChartOpts() {
      return {
        chart:   { id: 'rule-trend', toolbar: { show: false } },
        stroke:  { curve: 'smooth', width: 2 },
        markers: { size: 3 },
        xaxis:   { type: 'datetime', labels: { format: 'MM/dd HH:mm' } },
        yaxis:   { min: 0, max: 100, decimalsInFloat: 1, title: { text: '적합률 (%)' } },
        tooltip: { x: { format: 'yyyy-MM-dd HH:mm' } },
        legend:  { position: 'top' },
        grid:    { borderColor: '#ECEFF1' }
      };
    },
    profChartOpts() {
      return {
        chart:   { id: 'prof-trend', toolbar: { show: false } },
        stroke:  { curve: 'smooth', width: 2 },
        markers: { size: 3 },
        colors:  ['#E53935', '#43A047'],
        xaxis:   { type: 'datetime', labels: { format: 'MM/dd HH:mm' } },
        yaxis:   { min: 0, max: 100, decimalsInFloat: 1, title: { text: '%' } },
        tooltip: { x: { format: 'yyyy-MM-dd HH:mm' } },
        legend:  { position: 'top' },
        grid:    { borderColor: '#ECEFF1' }
      };
    }
  },
  mounted() {
    var self = this;
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { connectedOnly: 'Y' })
      .then(function(r) {
        self.dataModels = (r.data || []).filter(function(m) { return m.modelType === 'PHYSICAL'; });
      });
  },
  methods: {
    onModelChange() {
      this.objList = []; this.attrList = []; this.objNm = null; this.attrNm = null;
      this.modelAttrs = [];
      this.modelSeries = [{ name: '적합률 (%)', data: [] }];
      this.ruleSeries = [];
      this.profSeries[0].data = [];
      this.profSeries[1].data = [];
      if (!this.dmId) return;
      var self = this;
      axios.post(this.$APIURL.base + 'api/dm/getDataModelAttrList',
                  { dataModelId: this.dmId })
        .then(function(r) {
          self.modelAttrs = r.data || [];
          var objs = {};
          self.modelAttrs.forEach(function(a) {
            var n = a.tableNm || a.objNm;
            if (n) objs[n] = true;
          });
          self.objList = Object.keys(objs).sort();
        })
        .finally(function() { self.loadModelTrend(); });
    },
    onObjChange() {
      this.attrNm = null;
      this.attrList = [];
      if (!this.objNm) return;
      var self = this;
      this.modelAttrs.forEach(function(a) {
        if ((a.tableNm || a.objNm) === self.objNm) {
          var c = a.columnNm || a.attrNm;
          if (c) self.attrList.push(c);
        }
      });
      this.attrList.sort();
    },
    loadAll() {
      this.loadModelTrend();
      if (this.objNm && this.attrNm) {
        this.loadColumnTrends();
      }
    },
    loadModelTrend() {
      if (!this.dmId) return;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/stats/modelTrend',
                { params: { dmId: this.dmId } })
        .then(function(r) {
          var pts = (r.data || []).map(function(x) {
            return { x: new Date(x.diagDt).getTime(),
                     y: x.conformRate != null ? Number(Number(x.conformRate).toFixed(2)) : null };
          }).filter(function(p) { return p.y != null; });
          self.modelSeries = [{ name: '적합률 (%)', data: pts }];
          self.modelChartKey += 1;
        });
    },
    loadColumnTrends() {
      this.loadRuleTrend();
      this.loadProfileTrend();
    },
    loadRuleTrend() {
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/stats/columnRuleTrend',
                { params: { dmId: this.dmId, objNm: this.objNm, attrNm: this.attrNm } })
        .then(function(r) {
          // 룰별로 그룹 → 멀티 시리즈
          var byRule = {};
          (r.data || []).forEach(function(x) {
            var k = x.ruleNm || x.ruleId || '(unknown)';
            if (!byRule[k]) byRule[k] = [];
            if (x.conformRate != null) {
              byRule[k].push({
                x: new Date(x.diagDt).getTime(),
                y: Number(Number(x.conformRate).toFixed(2))
              });
            }
          });
          self.ruleSeries = Object.keys(byRule).map(function(k) {
            return { name: k, data: byRule[k] };
          });
          self.ruleChartKey += 1;
        });
    },
    loadProfileTrend() {
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/stats/columnProfileTrend',
                { params: { dmId: this.dmId, objNm: this.objNm, attrNm: this.attrNm } })
        .then(function(r) {
          var nullPts = [], distPts = [];
          (r.data || []).forEach(function(x) {
            var t = new Date(x.diagDt).getTime();
            if (x.nullPct != null)     nullPts.push({ x: t, y: Number(Number(x.nullPct).toFixed(2)) });
            if (x.distinctPct != null) distPts.push({ x: t, y: Number(Number(x.distinctPct).toFixed(2)) });
          });
          self.profSeries = [
            { name: 'NULL%',     data: nullPts },
            { name: 'DISTINCT%', data: distPts }
          ];
          self.profChartKey += 1;
        });
    }
  }
};
</script>
