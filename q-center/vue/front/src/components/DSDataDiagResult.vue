<template>
  <v-container fluid class="pa-2" style="height: 100%; overflow: auto;">
    <!-- 필터 바 1행: 진단 이력, 이슈 여부, 진단유형 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-1" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span class="filterLabel">진단 이력</span>
      <v-select
        v-model="selectedJobId"
        :items="jobList"
        item-text="jobDisplayText"
        item-value="diagJobId"
        dense outlined hide-details clearable
        placeholder="진단 이력 선택"
        style="width:680px; flex-grow:0;"
        @change="loadResults"
      />



      <span class="filterLabel">진단유형</span>
      <v-select v-model="selectedDiagTypes" :items="diagTypeOptions" item-text="label" item-value="value"
        dense outlined hide-details multiple chips small-chips deletable-chips
        style="width:260px; flex-grow:0;" />
              <v-checkbox v-model="onlyIssue" dense hide-details class="mt-0 pt-0"
        label="이슈" style="flex-grow:0;" />
    </v-sheet>

    <!-- 필터 바 2행: 명칭 검색 (각각 모드 셀렉트 + 입력) -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <!-- 테이블명 -->
      <span class="filterLabel">테이블명</span>
      <v-select v-model="searchTableMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchTable" dense outlined hide-details clearable
        placeholder="테이블명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 테이블 한글명 -->
      <span class="filterLabel">테이블 한글명</span>
      <v-select v-model="searchTableKrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchTableKr" dense outlined hide-details clearable
        placeholder="테이블 한글명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼명 -->
      <span class="filterLabel">컬럼명</span>
      <v-select v-model="searchAttrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchAttr" dense outlined hide-details clearable
        placeholder="컬럼명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 한글명 -->
      <span class="filterLabel">컬럼 한글명</span>
      <v-select v-model="searchAttrKrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchAttrKr" dense outlined hide-details clearable
        placeholder="컬럼 한글명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 타입 -->
      <span class="filterLabel">컬럼 타입</span>
      <v-text-field v-model="searchDataType" dense outlined hide-details clearable
        placeholder="ex) VARCHAR" style="width:140px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 길이 -->
      <span class="filterLabel">컬럼 길이</span>
      <v-text-field v-model="searchDataLen" dense outlined hide-details clearable
        placeholder="ex) 10" style="width:100px; flex-grow:0;" />
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
          :page.sync="summaryPage"
          :items-per-page="summaryItemsPerPage"
          hide-default-footer
          dense
          class="elevation-1"
        >
          <template v-slot:item.objNm="{ item }">
            <span class="text-caption font-weight-bold ndColor--text" style="cursor:pointer;"
              @click="drillToDetail(item.objNm)">{{ item.objNm }}</span>
          </template>
          <template v-slot:item.issueCnt="{ item }">
            <v-chip x-small color="red" text-color="white">{{ item.issueCnt }}</v-chip>
          </template>
        </v-data-table>
          <div class="d-flex align-center px-4 pt-2 pb-2">
          <v-spacer />
          <v-pagination v-if="summaryPageCount > 1" v-model="summaryPage" :length="summaryPageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
            color="ndColor" :total-visible="10" />
          <v-spacer />
          <v-select v-model="summaryItemsPerPage" :items="[10,20,30,50]" dense outlined hide-details
            style="max-width:100px; font-size:.75rem;" suffix="건" />
        </div>
      </v-tab-item>

      <!-- 컬럼 상세: 전체 컬럼 1건=1행, 실제값/표준값 병렬 표시 -->
      <v-tab-item>
        <v-data-table
          :key="`detail-${filterKey}`"
          :headers="detailHeaders"
          :items="filteredDetail"
          :page.sync="detailPage"
          :items-per-page="detailItemsPerPage"
          hide-default-footer
          dense
          class="elevation-1"
        >
          <!-- 테이블 한글명 -->
          <template v-slot:item.objNmKr="{ item }">
            <span v-if="item.objNmKr" class="text-caption">{{ item.objNmKr }}</span>
            <span v-else class="text-caption grey--text">-</span>
          </template>
          <!-- 컬럼 영문명 -->
          <template v-slot:item.attrNm="{ item }">
            <span class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'attrNm') }">{{ item.attrNm }}</span>
          </template>
          <!-- 컬럼 한글명 -->
          <template v-slot:item.attrNmKr="{ item }">
            <span :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'attrNmKr') }]">
              {{ item.attrNmKr || '-' }}
            </span>
          </template>
          <!-- 컬럼 데이터 타입 -->
          <template v-slot:item.actualDataType="{ item }">
            <span class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'actualDataType') }">{{ item.actualDataType }}</span>
          </template>
          <!-- 컬럼 길이 (소수점 있으면 10,2 형태) -->
          <template v-slot:item.actualDataLen="{ item }">
            <span class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'actualDataLen') }">{{ formatLen(item.actualDataLen, item.actualDataDecimalLen) }}</span>
          </template>
          <!-- 표준 한글명 -->
          <template v-slot:item.stdTermsNm="{ item }">
            <span :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdTermsNm') }]">
              {{ item.stdTermsNm || '-' }}
            </span>
          </template>
          <!-- 표준 타입 -->
          <template v-slot:item.stdDataType="{ item }">
            <span :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdDataType') }]">
              {{ item.stdDataType || '-' }}
            </span>
          </template>
          <!-- 표준 길이 (소수점 있으면 10,2 형태) -->
          <template v-slot:item.stdDataLen="{ item }">
            <span :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdDataLen') }]">
              {{ formatLen(item.stdDataLen, item.stdDataDecimalLen) }}
            </span>
          </template>
          <!-- 진단결과: 이슈 유형을 칩으로 복수 표시, mouseover 시 관련 셀 하이라이트 -->
          <template v-slot:item.diagTypes="{ item }">
            <template v-if="item.diagTypeList && item.diagTypeList.length > 0">
              <v-tooltip bottom v-for="dt in item.diagTypeList" :key="dt">
                <template v-slot:activator="{ on }">
                  <v-chip v-on="on" x-small :color="diagTypeColor(dt)" text-color="white" class="mr-1"
                    @mouseenter.native="onChipEnter(item, dt)"
                    @mouseleave.native="onChipLeave()">
                    {{ diagTypeLabel(dt) }}
                  </v-chip>
                </template>
                <span>{{ diagTypeDesc(dt) }}</span>
              </v-tooltip>
            </template>
            <span v-else class="text-caption green--text">OK</span>
          </template>
        </v-data-table>
        <div class="d-flex align-center px-4 pt-2 pb-2">
          <v-spacer />
          <v-pagination v-if="detailPageCount > 1" v-model="detailPage" :length="detailPageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
            color="ndColor" :total-visible="10" />
          <v-spacer />
          <v-select v-model="detailItemsPerPage" :items="[20,50,100]" dense outlined hide-details
            style="max-width:100px; font-size:.75rem;" suffix="건" />
        </div>
      </v-tab-item>
    </v-tabs-items>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSDataDiagResult',
  data() {
    return {
      jobList: [],
      selectedJobId: null,
      activeTab: 0,
      summaryList: [],
      detailList: [],
      pendingJobId: null,
      // 페이지네이션
      summaryPage: 1,
      summaryItemsPerPage: 20,
      detailPage: 1,
      detailItemsPerPage: 50,
      // 이슈 여부 필터 (체크 시 이슈 있는 컬럼만 표시)
      onlyIssue: true,
      // 검색 모드 옵션 (포함/앞/뒤)
      searchModeOptions: [
        { value: 'contains', label: '포함' },
        { value: 'start',   label: '앞' },
        { value: 'end',     label: '뒤' },
      ],
      // 테이블명 검색
      searchTable: '',
      searchTableMode: 'contains',
      // 테이블 한글명 검색
      searchTableKr: '',
      searchTableKrMode: 'contains',
      // 컬럼명 검색
      searchAttr: '',
      searchAttrMode: 'contains',
      // 컬럼 한글명 검색
      searchAttrKr: '',
      searchAttrKrMode: 'contains',
      // 컬럼 타입 검색
      searchDataType: '',
      // 컬럼 길이 검색
      searchDataLen: '',
      // 칩 mouseover 시 관련 셀 하이라이트 상태
      highlightRow: { itemKey: null, diagType: null },
      // 진단유형 필터
      selectedDiagTypes: [],
      diagTypeOptions: [
        { value: 'TERM_NOT_EXIST',       label: '용어 미존재' },
        { value: 'TERM_KR_NM_MISMATCH',  label: '한글명 불일치' },
        { value: 'DATA_TYPE_MISMATCH',   label: '타입 불일치' },
        { value: 'DATA_LEN_MISMATCH',    label: '길이 불일치' },
      ],
      summaryHeaders: [
        { text: '테이블명',     value: 'objNm',                width: '200px' },
        { text: '이슈 컬럼',    value: 'issueColCnt',          width: '80px',  align: 'end' },
        { text: '총 이슈',      value: 'issueCnt',             width: '80px',  align: 'end' },
        { text: '용어 미존재',  value: 'termNotExistCnt',      width: '100px', align: 'end' },
        { text: '한글명 불일치',value: 'termKrNmMismatchCnt',  width: '100px', align: 'end' },
        { text: '타입 불일치',  value: 'dataTypeMismatchCnt',  width: '100px', align: 'end' },
        { text: '길이 불일치',  value: 'dataLenMismatchCnt',   width: '100px', align: 'end' },
      ],
      detailHeaders: [
        { text: '테이블명',       value: 'objNm',         width: '110px', align: 'center' },
        { text: '테이블 한글명',  value: 'objNmKr',       width: '120px' },
        { text: '컬럼 영문명',    value: 'attrNm',        width: '100px' },
        { text: '컬럼 한글명',    value: 'attrNmKr',      width: '110px' },
        { text: '컬럼 타입',      value: 'actualDataType',width: '80px', align: 'center' },
        { text: '컬럼 길이',      value: 'actualDataLen', width: '70px', align: 'end' },
        { text: '표준 한글명',    value: 'stdTermsNm',    width: '110px', class: 'std-header' },
        { text: '표준 타입',      value: 'stdDataType',   width: '80px',  class: 'std-header', align: 'center' },
        { text: '표준 길이',      value: 'stdDataLen',    width: '70px',  class: 'std-header', align: 'end' },
        { text: '진단결과',       value: 'diagTypes',     width: '200px', sortable: false, align: 'center' },
      ],
    };
  },
  computed: {
    summaryPageCount() {
      return Math.ceil(this.filteredSummary.length / this.summaryItemsPerPage);
    },
    detailPageCount() {
      return Math.ceil(this.filteredDetail.length / this.detailItemsPerPage);
    },
    /** 필터 변경 시 테이블 리마운트용 key */
    filterKey() {
      return [
        this.searchTable, this.searchTableMode,
        this.searchTableKr, this.searchTableKrMode,
        this.searchAttr, this.searchAttrMode,
        this.searchAttrKr, this.searchAttrKrMode,
        this.searchDataType,
        this.searchDataLen,
        this.selectedDiagTypes.join(','),
        this.onlyIssue,
      ].join('|');
    },
    filteredSummary() {
      // 이슈 체크 해제 시 테이블 집계는 이슈 기반이므로 빈 결과
      if (!this.onlyIssue) return [];

      return this.summaryList.filter(item => {
        // 테이블명 검색
        if (!this.matchName(item.objNm, this.searchTable, this.searchTableMode)) return false;
        // 진단유형 필터: 선택된 유형의 건수가 1 이상인 테이블만
        if (this.selectedDiagTypes.length > 0) {
          const typeCountMap = {
            'TERM_NOT_EXIST':      item.termNotExistCnt,
            'TERM_KR_NM_MISMATCH': item.termKrNmMismatchCnt,
            'DATA_TYPE_MISMATCH':  item.dataTypeMismatchCnt,
            'DATA_LEN_MISMATCH':   item.dataLenMismatchCnt,
          };
          const hasSelected = this.selectedDiagTypes.some(dt => (typeCountMap[dt] || 0) > 0);
          if (!hasSelected) return false;
        }
        return true;
      });
    },
    filteredDetail() {
      return this.detailList.filter(item => {
        // 이슈 여부 필터: 체크=이슈 있는 컬럼만, 해제=이슈 없는 컬럼만
        const hasIssue = item.diagTypeList && item.diagTypeList.length > 0;
        if (this.onlyIssue && !hasIssue) return false;
        if (!this.onlyIssue && hasIssue) return false;

        // 명칭 검색 (모드별)
        if (!this.matchName(item.objNm,    this.searchTable,   this.searchTableMode))   return false;
        if (!this.matchName(item.objNmKr,  this.searchTableKr, this.searchTableKrMode)) return false;
        if (!this.matchName(item.attrNm,   this.searchAttr,    this.searchAttrMode))    return false;
        if (!this.matchName(item.attrNmKr, this.searchAttrKr,  this.searchAttrKrMode))  return false;

        // 컬럼 타입 검색 (포함 매칭)
        if (this.searchDataType) {
          const v = (item.actualDataType || '').toLowerCase();
          if (!v.includes(this.searchDataType.toLowerCase())) return false;
        }

        // 컬럼 길이 검색
        if (this.searchDataLen) {
          const display = this.formatLen(item.actualDataLen, item.actualDataDecimalLen);
          if (!display.includes(this.searchDataLen)) return false;
        }

        // 진단유형 필터
        if (this.selectedDiagTypes.length > 0) {
          if (!hasIssue) return false;
          if (!item.diagTypeList.some(d => this.selectedDiagTypes.includes(d))) return false;
        }
        return true;
      });
    },
  },
  mounted() {
    this.checkPendingJob();
    this.loadJobList();
  },
  /**
   * keep-alive 캐시에서 복원될 때 호출 (두 번째 이후 진입)
   */
  activated() {
    this.checkPendingJob();
    if (this.pendingJobId) {
      this.loadJobList();
    }
  },
  methods: {
    /**
     * 명칭 검색 매칭 (포함/앞/뒤 모드)
     * @param {String} value  - 실제 값
     * @param {String} keyword - 검색어
     * @param {String} mode   - 'contains' | 'start' | 'end'
     */
    matchName(value, keyword, mode) {
      if (!keyword) return true;
      const v = (value || '').toLowerCase();
      const k = keyword.toLowerCase();
      if (mode === 'start') return v.startsWith(k);
      if (mode === 'end')   return v.endsWith(k);
      return v.includes(k);  // contains (default)
    },
    loadJobList() {
      axios.post(this.$APIURL.base + 'api/diag/getDiagJobList').then(res => {
        this.jobList = (res.data || []).map(job => ({
          ...job,
          jobDisplayText: `[${job.dataModelNm}] ${job.clctDt || ''} - ${this.statusLabel(job.status)} (이슈 ${job.resultCnt}건)`,
        }));
        if (this.pendingJobId) {
          this.selectJob(this.pendingJobId);
        }
      });
    },
    /**
     * eventBus에 저장된 pendingDiagJobId를 확인하여 pendingJobId에 세팅
     */
    checkPendingJob() {
      if (eventBus.pendingDiagJobId) {
        this.pendingJobId = eventBus.pendingDiagJobId;
        eventBus.pendingDiagJobId = null;
      }
    },
    selectJob(diagJobId) {
      this.selectedJobId = diagJobId;
      this.pendingJobId = null;
      this.loadResults(diagJobId);
    },
    /**
     * 진단 결과 조회
     * - 테이블 집계: getDiagResultSummary (GROUP BY OBJ_NM)
     * - 컬럼 상세: getDiagResultDetail (전체 컬럼 1행, 용어/도메인 JOIN)
     */
    loadResults(diagJobId) {
      if (!diagJobId) {
        this.summaryList = [];
        this.detailList = [];
        return;
      }
      Promise.all([
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultSummary', { params: { diagJobId } }),
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultDetail',  { params: { diagJobId } }),
      ]).then(([summaryRes, detailRes]) => {
        this.summaryList = summaryRes.data || [];
        // diagTypes 문자열을 배열로 변환
        this.detailList = (detailRes.data || []).map(item => ({
          ...item,
          diagTypeList: item.diagTypes ? item.diagTypes.split(',') : [],
        }));
      });
    },
    /** 테이블 집계에서 테이블명 클릭 → 컬럼 상세 탭으로 이동 + 테이블명 검색조건 세팅 */
    drillToDetail(objNm) {
      this.searchTable = objNm;
      this.searchTableMode = 'contains';
      this.activeTab = 1;
    },
    diagTypeColor(type) {
      const map = {
        TERM_NOT_EXIST:       'red darken-2',
        TERM_KR_NM_MISMATCH:  'orange darken-2',
        DATA_TYPE_MISMATCH:   'blue darken-2',
        DATA_LEN_MISMATCH:    'purple darken-1',
      };
      return map[type] || 'grey';
    },
    diagTypeDesc(type) {
      const map = {
        TERM_NOT_EXIST:       '컬럼 영문명에 해당하는 표준 용어가 등록되어 있지 않습니다.',
        TERM_KR_NM_MISMATCH:  '컬럼 한글명(COMMENTS)이 표준 용어의 한글명과 일치하지 않습니다.',
        DATA_TYPE_MISMATCH:   '컬럼의 데이터 타입이 표준 도메인의 타입과 일치하지 않습니다.',
        DATA_LEN_MISMATCH:    '컬럼의 데이터 길이가 표준 도메인의 길이와 일치하지 않습니다.',
      };
      return map[type] || type;
    },
    diagTypeLabel(type) {
      const map = {
        TERM_NOT_EXIST:       '용어 미존재',
        TERM_KR_NM_MISMATCH:  '한글명 불일치',
        DATA_TYPE_MISMATCH:   '타입 불일치',
        DATA_LEN_MISMATCH:    '길이 불일치',
      };
      return map[type] || type;
    },
    /**
     * 데이터 길이 표시: 소수점 길이가 있으면 "10,2" 형태, 없으면 "10"
     */
    formatLen(len, decimalLen) {
      if (len == null) return '-';
      if (decimalLen && decimalLen > 0) return len + ',' + decimalLen;
      return String(len);
    },
    statusLabel(status) {
      const map = { READY: '대기', RUNNING: '진행중', DONE: '완료', STOPPED: '중지', ERROR: '오류' };
      return map[status] || status;
    },
    /**
     * 진단 칩 mouseenter: 해당 row + 진단유형에 해당하는 셀 하이라이트 활성화
     */
    onChipEnter(item, diagType) {
      this.highlightRow = { itemKey: item.objNm + '|' + item.attrNm, diagType };
    },
    onChipLeave() {
      this.highlightRow = { itemKey: null, diagType: null };
    },
    /**
     * 진단유형별 하이라이트 대상 셀 매핑:
     * - TERM_NOT_EXIST       → attrNm (컬럼 영문명)
     * - TERM_KR_NM_MISMATCH  → attrNmKr, stdTermsNm
     * - DATA_TYPE_MISMATCH   → actualDataType, stdDataType
     * - DATA_LEN_MISMATCH    → actualDataLen, stdDataLen
     */
    isHighlighted(item, field) {
      const h = this.highlightRow;
      if (!h.itemKey || !h.diagType) return false;
      if ((item.objNm + '|' + item.attrNm) !== h.itemKey) return false;
      const fieldMap = {
        TERM_NOT_EXIST:       ['attrNm'],
        TERM_KR_NM_MISMATCH:  ['attrNmKr', 'stdTermsNm'],
        DATA_TYPE_MISMATCH:   ['actualDataType', 'stdDataType'],
        DATA_LEN_MISMATCH:    ['actualDataLen', 'stdDataLen'],
      };
      const targets = fieldMap[h.diagType] || [];
      return targets.includes(field);
    },
  },
};
</script>

<style scoped>
.filterLabel {
  font-size: .8rem;
  white-space: nowrap;
}
/* 진단 칩 mouseover 시 관련 셀 빨간 배경 하이라이트 */
.diag-highlight {
  background-color: #FFCDD2 !important;
  border-radius: 2px;
  padding: 1px 3px;
  transition: background-color 0.15s ease;
}
</style>

<style>
/* 표준 기준 컬럼 헤더 배경색 (연초록) - scoped 밖에서 정의해야 v-data-table 내부에 적용 */
.std-header {
  background-color: #E8F5E9 !important;
}
/* 모든 테이블 헤더 가운데 정렬 (Vuetify가 th에 text-start/text-end 클래스 적용하므로 강제 override) */
.v-data-table th.text-start,
.v-data-table th.text-end,
.v-data-table th.text-center {
  text-align: center !important;
  position: relative;
}
/* 정렬 화살표를 항상 오른쪽에 배치 (align:'end' 시 왼쪽에 생기는 것 방지) */
.v-data-table th .v-data-table-header__icon {
  position: absolute !important;
  right: 4px;
}
</style>
