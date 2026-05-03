<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" @change="loadTrend"></v-autocomplete>
        <v-text-field v-model="objNm" label="테이블 (선택)" dense hide-details style="max-width:200px"></v-text-field>
        <v-text-field v-model="attrNm" label="컬럼 (선택)" dense hide-details style="max-width:200px"></v-text-field>
        <v-btn small @click="loadTrend" :disabled="!dmId" id="btn-stats-load">
          <v-icon small left>mdi-chart-line</v-icon>조회
        </v-btn>
      </v-sheet>

      <v-sheet class="pa-2" style="font-size:.85rem; color:#9E9E9E;">
        ※ 1차 구현: 시계열 표 형식. 2차에서 그래프 (Chart.js 등) 보강 예정.
      </v-sheet>

      <v-data-table :headers="headers" :items="rows" dense hide-default-footer
        :items-per-page="100" class="elevation-0" :loading="loading">
        <template v-slot:item.nullPct="{ item }">
          {{ pct(item.nullCnt, item.totalCnt) }}
        </template>
      </v-data-table>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualStats',
  data() {
    return {
      dataModels: [], dmId: null, objNm: '', attrNm: '',
      rows: [], loading: false,
      headers: [
        { text: '진단일시', value: 'diagDt' },
        { text: '테이블', value: 'objNm' },
        { text: '컬럼', value: 'attrNm' },
        { text: '총행수', value: 'totalCnt' },
        { text: 'NULL', value: 'nullCnt' },
        { text: 'NULL%', value: 'nullPct' },
        { text: '유일값', value: 'distinctCnt' },
        { text: '최소', value: 'minVal' },
        { text: '최대', value: 'maxVal' }
      ]
    };
  },
  mounted() {
    var self = this;
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { connectedOnly: 'Y' })
      .then(function(r) {
        self.dataModels = (r.data || []).filter(function(m) { return m.modelType === 'PHYSICAL'; });
      });
  },
  methods: {
    loadTrend() {
      if (!this.dmId) { this.rows = []; return; }
      this.loading = true;
      var params = { dmId: this.dmId };
      if (this.objNm)  params.objNm  = this.objNm;
      if (this.attrNm) params.attrNm = this.attrNm;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/stats/trend', { params: params })
        .then(function(r) { self.rows = r.data || []; })
        .finally(function() { self.loading = false; });
    },
    pct(n, t) {
      if (!t || t === 0) return '0.0%';
      return ((n / t) * 100).toFixed(1) + '%';
    }
  }
};
</script>
