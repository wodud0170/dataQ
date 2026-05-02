<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6">
        <v-text-field v-model="diagId" label="diagId" dense hide-details style="max-width:380px"></v-text-field>
        <v-btn small class="ml-3 gradient" @click="load" :disabled="!diagId" id="btn-result-load">조회</v-btn>
        <v-spacer></v-spacer>
        <span v-if="history" style="font-size:.85rem">
          상태:
          <v-chip x-small :color="statusColor(history.status)" text-color="white">{{ history.status }}</v-chip>
          모델: {{ history.dataModelNm }} | 룰 {{ history.totalRules || 0 }} | 위반 합 {{ history.totalViolations || 0 }}
        </span>
      </v-sheet>

      <v-data-table :headers="headers" :items="results" dense hide-default-footer :items-per-page="100"
        class="elevation-0" :loading="loading">
        <template v-slot:item.severity="{ item }">
          <v-chip x-small :color="sevColor(item.severity)" text-color="white">{{ item.severity }}</v-chip>
        </template>
        <template v-slot:item.violationRate="{ item }">
          <span :style="{color: rateColor(item.violationRate)}">{{ item.violationRate }} %</span>
        </template>
      </v-data-table>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualRuleResult',
  data() {
    return {
      diagId: '',
      history: null,
      results: [],
      loading: false,
      headers: [
        { text: '룰명', value: 'ruleNm' },
        { text: '유형', value: 'ruleType' },
        { text: '심각도', value: 'severity' },
        { text: '테이블', value: 'objNm' },
        { text: '컬럼', value: 'attrNm' },
        { text: '전체', value: 'totalCnt' },
        { text: '위반', value: 'violationCnt' },
        { text: '위반률', value: 'violationRate' },
        { text: '에러', value: 'errorMsg' }
      ]
    };
  },
  methods: {
    load() {
      this.loading = true;
      axios.get(this.$APIURL.base + 'api/qual/rule/result', { params: { diagId: this.diagId } })
        .then(r => {
          if (r.data.resultCode === 200) {
            var c = r.data.contents;
            if (typeof c === 'string') {
              try { c = JSON.parse(c); } catch (e) { c = {}; }
            }
            this.history = (c || {}).history || null;
            this.results = (c || {}).results || [];
          }
        })
        .finally(() => { this.loading = false; });
    },
    statusColor(s) {
      return s === 'DONE' ? 'green' : s === 'ERROR' ? 'red' :
             s === 'RUNNING' ? 'orange' : 'grey';
    },
    sevColor(s) { return s === 'ERROR' ? 'red' : s === 'WARN' ? 'orange' : 'blue'; },
    rateColor(r) { return r > 50 ? 'red' : r > 10 ? 'orange' : 'inherit'; }
  }
};
</script>
