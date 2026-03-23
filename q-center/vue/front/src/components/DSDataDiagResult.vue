<template>
  <v-container fluid class="pa-2">
    <!-- 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span class="filterLabel">진단 이력</span>
      <v-select
        v-model="selectedJobId"
        :items="jobList"
        item-text="jobDisplayText"
        item-value="diagJobId"
        dense outlined hide-details clearable
        placeholder="진단 이력 선택"
        style="width:520px; flex-grow:0;"
        @change="loadResults"
      />

      <span class="filterLabel">테이블명</span>
      <v-text-field v-model="searchTable" dense outlined hide-details clearable
        placeholder="테이블명" style="width:120px; flex-grow:0;" />

      <span class="filterLabel">컬럼명</span>
      <v-text-field v-model="searchAttr" dense outlined hide-details clearable
        placeholder="컬럼명" style="width:120px; flex-grow:0;" />

      <span class="filterLabel">진단유형</span>
      <v-select v-model="selectedDiagTypes" :items="diagTypeOptions" item-text="label" item-value="value"
        dense outlined hide-details multiple chips small-chips style="width:200px; flex-grow:0;" />
    </v-sheet>

    <!-- 요약 + 상세 탭 -->
    <v-tabs v-model="activeTab" dense class="mb-0">
      <v-tab>테이블 집계</v-tab>
      <v-tab>컬럼 상세</v-tab>
    </v-tabs>

    <v-tabs-items v-model="activeTab">
      <!-- 테이블 집계 -->
      <v-tab-item>
        <v-data-table
          :headers="summaryHeaders"
          :items="filteredSummary"
          dense
          :items-per-page="20"
          :footer-props="{ 'items-per-page-options': [10, 20, 50] }"
          class="elevation-1"
        >
          <template v-slot:item.objNm="{ item }">
            <span class="text-caption font-weight-bold">{{ item.objNm }}</span>
          </template>
          <template v-slot:item.issueCnt="{ item }">
            <v-chip x-small color="red" text-color="white">{{ item.issueCnt }}</v-chip>
          </template>
        </v-data-table>
      </v-tab-item>

      <!-- 컬럼 상세 -->
      <v-tab-item>
        <v-data-table
          :key="`detail-${searchTable}|${searchAttr}|${selectedDiagTypes.join(',')}`"
          :headers="detailHeaders"
          :items="filteredDetail"
          dense
          :items-per-page="50"
          :footer-props="{ 'items-per-page-options': [20, 50, 100] }"
          class="elevation-1"
        >
          <template v-slot:item.diagType="{ item }">
            <v-chip x-small :color="diagTypeColor(item.diagType)" text-color="white">
              {{ diagTypeLabel(item.diagType) }}
            </v-chip>
          </template>
        </v-data-table>
      </v-tab-item>
    </v-tabs-items>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSDataDiagResult',
  data() {
    return {
      jobList: [],
      selectedJobId: null,
      activeTab: 0,
      summaryList: [],
      detailList: [],
      searchTable: '',
      searchAttr: '',
      selectedDiagTypes: [],
      diagTypeOptions: [
        { value: 'TERM_NOT_EXIST',       label: '용어 미존재' },
        { value: 'TERM_KR_NM_MISMATCH',  label: '한글명 불일치' },
        { value: 'TERM_ENG_NM_MISMATCH', label: '영문명 불일치' },
        { value: 'DATA_TYPE_MISMATCH',   label: '타입 불일치' },
        { value: 'DATA_LEN_MISMATCH',    label: '길이 초과' },
      ],
      summaryHeaders: [
        { text: '테이블명',     value: 'objNm',                width: '200px' },
        { text: '총 이슈',      value: 'issueCnt',             width: '80px'  },
        { text: '용어 미존재',  value: 'termNotExistCnt',      width: '90px'  },
        { text: '한글명 불일치',value: 'termKrNmMismatchCnt',  width: '100px' },
        { text: '영문명 불일치',value: 'termEngNmMismatchCnt', width: '100px' },
        { text: '타입 불일치',  value: 'dataTypeMismatchCnt',  width: '90px'  },
        { text: '길이 초과',    value: 'dataLenMismatchCnt',   width: '80px'  },
      ],
      detailHeaders: [
        { text: '테이블명',   value: 'objNm',       width: '160px' },
        { text: '컬럼 영문명',value: 'attrNm',      width: '160px' },
        { text: '컬럼 한글명',value: 'attrNmKr',    width: '160px' },
        { text: '진단유형',   value: 'diagType',    width: '120px' },
        { text: '상세',       value: 'diagDetail',  width: '120px' },
        { text: '기준값',     value: 'stdValue',    width: '160px' },
        { text: '실제값',     value: 'actualValue', width: '160px' },
      ],
    };
  },
  computed: {
    filteredSummary() {
      const tbl = (this.searchTable || '').toLowerCase();
      return this.summaryList.filter(item => {
        const t = !tbl || (item.objNm || '').toLowerCase().includes(tbl);
        return t;
      });
    },
    filteredDetail() {
      const tbl = (this.searchTable || '').toLowerCase();
      const atr = (this.searchAttr  || '').toLowerCase();
      return this.detailList.filter(item => {
        const t  = !tbl || (item.objNm  || '').toLowerCase().includes(tbl);
        const a  = !atr || (item.attrNm || '').toLowerCase().includes(atr);
        const dt = this.selectedDiagTypes.length === 0 || this.selectedDiagTypes.includes(item.diagType);
        return t && a && dt;
      });
    },
  },
  mounted() {
    this.loadJobList();
  },
  methods: {
    loadJobList() {
      axios.post(this.$APIURL.base + 'api/diag/getDiagJobList').then(res => {
        this.jobList = (res.data || []).map(job => ({
          ...job,
          jobDisplayText: `[${job.dataModelNm}] ${job.clctDt || ''} - ${this.statusLabel(job.status)} (이슈 ${job.resultCnt}건)`,
        }));
      });
    },
    loadResults(diagJobId) {
      if (!diagJobId) {
        this.summaryList = [];
        this.detailList = [];
        return;
      }
      Promise.all([
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultSummary', { params: { diagJobId } }),
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultList',    { params: { diagJobId } }),
      ]).then(([summaryRes, detailRes]) => {
        this.summaryList = summaryRes.data || [];
        this.detailList  = detailRes.data  || [];
      });
    },
    diagTypeColor(type) {
      const map = {
        TERM_NOT_EXIST:       'red darken-2',
        TERM_KR_NM_MISMATCH:  'orange darken-2',
        TERM_ENG_NM_MISMATCH: 'orange',
        DATA_TYPE_MISMATCH:   'blue darken-2',
        DATA_LEN_MISMATCH:    'purple darken-1',
      };
      return map[type] || 'grey';
    },
    diagTypeLabel(type) {
      const map = {
        TERM_NOT_EXIST:       '용어 미존재',
        TERM_KR_NM_MISMATCH:  '한글명 불일치',
        TERM_ENG_NM_MISMATCH: '영문명 불일치',
        DATA_TYPE_MISMATCH:   '타입 불일치',
        DATA_LEN_MISMATCH:    '길이 초과',
      };
      return map[type] || type;
    },
    statusLabel(status) {
      const map = { READY: '대기', RUNNING: '진행중', DONE: '완료', STOPPED: '중지', ERROR: '오류' };
      return map[status] || status;
    },
  },
};
</script>

<style scoped>
.filterLabel {
  font-size: .8rem;
  white-space: nowrap;
}
</style>
