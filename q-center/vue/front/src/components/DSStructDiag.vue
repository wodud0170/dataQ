<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span class="filterLabel">데이터모델명</span>
      <v-autocomplete
        v-model="selectedModel"
        :items="dataModelList"
        item-text="dataModelNm"
        item-value="dataModelId"
        dense outlined hide-details clearable
        placeholder="데이터 모델 선택"
        style="width:220px; flex-grow:0;"
        @change="onModelChange"
      />

      <span class="filterLabel">이전 수집</span>
      <v-select
        v-model="prevClctId"
        :items="clctList"
        item-text="clctDisplayDt"
        item-value="clctId"
        dense outlined hide-details
        placeholder="이전 수집 선택"
        style="width:260px; flex-grow:0;"
        :disabled="!selectedModel"
      />

      <span class="filterLabel">현재 수집</span>
      <v-select
        v-model="currClctId"
        :items="clctList"
        item-text="clctDisplayDt"
        item-value="clctId"
        dense outlined hide-details
        placeholder="현재 수집 선택"
        style="width:260px; flex-grow:0;"
        :disabled="!selectedModel"
      />

      <v-spacer />

      <v-btn small color="primary" :disabled="!canExecute" :loading="executing" @click="executeCompare">
        <v-icon small left>mdi-compare-horizontal</v-icon>
        구조 비교 실행
      </v-btn>
    </v-sheet>

    <!-- 스탯 카드 4개 -->
    <v-row v-if="hasResult" dense class="mb-2">
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8EAF6;">
            <v-icon color="#3F51B5">mdi-table</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalTableCnt }}</div>
            <div class="stat-label">전체 테이블 수</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E3F2FD;">
            <v-icon color="#1976D2">mdi-view-column</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalColumnCnt }}</div>
            <div class="stat-label">전체 컬럼 수</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#FFF3E0;">
            <v-icon color="#E65100">mdi-swap-horizontal</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.changeCnt }}</div>
            <div class="stat-label">변경 항목 수</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8F5E9;">
            <v-icon color="#2E7D32">mdi-calendar-clock</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value stat-value-sm">{{ stats.lastClctDt || '-' }}</div>
            <div class="stat-label">마지막 수집일시</div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- 변경 요약 칩 -->
    <v-sheet v-if="hasResult" class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span class="filterLabel">변경 요약</span>
      <v-chip small color="green" text-color="white">
        <v-icon x-small left>mdi-plus-circle</v-icon>
        추가(ADDED): 테이블 {{ summary.addedTable }}건, 컬럼 {{ summary.addedColumn }}건
      </v-chip>
      <v-chip small color="orange" text-color="white">
        <v-icon x-small left>mdi-pencil-circle</v-icon>
        변경(MODIFIED): 컬럼 {{ summary.modifiedColumn }}건
      </v-chip>
      <v-chip small color="red" text-color="white">
        <v-icon x-small left>mdi-minus-circle</v-icon>
        삭제(DELETED): 테이블 {{ summary.deletedTable }}건, 컬럼 {{ summary.deletedColumn }}건
      </v-chip>
    </v-sheet>

    <!-- 변경사항 테이블 -->
    <v-sheet v-if="hasResult" style="flex:1; overflow:auto; border:1px solid #e0e0e0; border-radius:4px;">
      <!-- 변경유형 필터 칩 -->
      <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #e0e0e0;">
        <span class="filterLabel">변경유형</span>
        <v-chip class="mr-1" :color="changeTypeFilter === 'ALL' ? 'primary' : ''" :outlined="changeTypeFilter !== 'ALL'" small @click="changeTypeFilter = 'ALL'">전체</v-chip>
        <v-chip class="mr-1" :color="changeTypeFilter === 'ADDED' ? 'green' : ''" :outlined="changeTypeFilter !== 'ADDED'" :text-color="changeTypeFilter === 'ADDED' ? 'white' : ''" small @click="changeTypeFilter = 'ADDED'">ADDED</v-chip>
        <v-chip class="mr-1" :color="changeTypeFilter === 'MODIFIED' ? 'orange' : ''" :outlined="changeTypeFilter !== 'MODIFIED'" :text-color="changeTypeFilter === 'MODIFIED' ? 'white' : ''" small @click="changeTypeFilter = 'MODIFIED'">MODIFIED</v-chip>
        <v-chip class="mr-1" :color="changeTypeFilter === 'DELETED' ? 'red' : ''" :outlined="changeTypeFilter !== 'DELETED'" :text-color="changeTypeFilter === 'DELETED' ? 'white' : ''" small @click="changeTypeFilter = 'DELETED'">DELETED</v-chip>
        <v-spacer />
        <span class="filterLabel">{{ filteredChanges.length }}건</span>
      </div>

      <v-data-table
        :headers="changeHeaders"
        :items="filteredChanges"
        :page.sync="page"
        :items-per-page="itemsPerPage"
        hide-default-footer
        dense
        class="elevation-0"
      >
        <template v-slot:item.changeType="{ item }">
          <v-chip x-small :color="changeTypeColor(item.changeType)" text-color="white">
            {{ item.changeType }}
          </v-chip>
        </template>
        <template v-slot:item.prevType="{ item }">
          <span v-if="item.prevDataType">{{ item.prevDataType }}<span v-if="item.prevDataLen">({{ item.prevDataLen }})</span></span>
          <span v-else class="grey--text">-</span>
        </template>
        <template v-slot:item.currType="{ item }">
          <span v-if="item.currDataType">{{ item.currDataType }}<span v-if="item.currDataLen">({{ item.currDataLen }})</span></span>
          <span v-else class="grey--text">-</span>
        </template>
      </v-data-table>
      <div class="d-flex align-center px-4 pt-2 pb-2">
        <v-spacer />
        <v-pagination v-if="pageCount > 1" v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10" />
        <v-spacer />
        <v-select v-model="itemsPerPage" :items="[10,20,30,50]" dense outlined hide-details
          style="max-width:100px; font-size:.75rem;" suffix="건" />
      </div>
    </v-sheet>

    <!-- 결과 없을 때 안내 -->
    <v-sheet v-if="!hasResult && !executing" style="flex:1; display:flex; align-items:center; justify-content:center; border:1px solid #e0e0e0; border-radius:4px;">
      <div class="text-center grey--text">
        <v-icon large color="grey lighten-1" class="mb-2">mdi-compare-horizontal</v-icon>
        <div>데이터모델을 선택하고 이전/현재 수집을 지정한 후 [구조 비교 실행] 버튼을 클릭하세요.</div>
      </div>
    </v-sheet>

    <!-- 이전 진단 이력 -->
    <v-sheet class="mt-2 pa-0" style="border:1px solid #e0e0e0; border-radius:4px;">
      <div class="d-flex align-center pa-2" style="border-bottom:1px solid #e0e0e0;">
        <span class="filterLabel" style="font-size:.85rem; font-weight:600;">이전 진단 이력</span>
        <v-spacer />
        <v-btn x-small text color="primary" @click="loadHistory">
          <v-icon x-small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </div>
      <v-data-table
        :headers="historyHeaders"
        :items="historyList"
        :items-per-page="5"
        dense
        class="elevation-0"
        @click:row="onHistoryClick"
        style="cursor:pointer;"
      >
        <template v-slot:item.changeCnt="{ item }">
          <v-chip x-small color="indigo" text-color="white">{{ item.changeCnt }}건</v-chip>
        </template>
        <template v-slot:no-data>
          <span class="grey--text text--lighten-1">이전 진단 이력이 없습니다.</span>
        </template>
      </v-data-table>
    </v-sheet>

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">
      {{ snackbarMsg }}
    </v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSStructDiag',
  data() {
    return {
      // 필터
      dataModelList: [],
      selectedModel: null,
      clctList: [],
      prevClctId: null,
      currClctId: null,
      executing: false,

      // 결과
      hasResult: false,
      stats: {
        totalTableCnt: 0,
        totalColumnCnt: 0,
        changeCnt: 0,
        lastClctDt: '',
      },
      summary: {
        addedTable: 0,
        addedColumn: 0,
        modifiedColumn: 0,
        deletedTable: 0,
        deletedColumn: 0,
      },
      changeList: [],
      changeTypeFilter: 'ALL',

      // 변경사항 테이블 페이지네이션
      page: 1,
      itemsPerPage: 20,

      // 변경사항 테이블 헤더
      changeHeaders: [
        { text: '테이블명',       value: 'tableNm',     width: '180px' },
        { text: '컬럼명',         value: 'columnNm',    width: '180px' },
        { text: '변경유형',       value: 'changeType',  width: '110px' },
        { text: '이전 타입/길이', value: 'prevType',    width: '150px' },
        { text: '현재 타입/길이', value: 'currType',    width: '150px' },
      ],

      // 이전 진단 이력
      historyList: [],
      historyHeaders: [
        { text: '진단일시',   value: 'diagDt',     width: '180px' },
        { text: '데이터모델', value: 'dataModelNm', width: '180px' },
        { text: '변경건수',   value: 'changeCnt',  width: '100px' },
        { text: '실행자',     value: 'cretUserId',  width: '120px' },
      ],

      // 스낵바
      snackbar: false,
      snackbarMsg: '',
      snackbarColor: 'info',
    };
  },
  computed: {
    canExecute() {
      return this.selectedModel && this.prevClctId && this.currClctId && this.prevClctId !== this.currClctId;
    },
    filteredChanges() {
      if (this.changeTypeFilter === 'ALL') return this.changeList;
      return this.changeList.filter(c => c.changeType === this.changeTypeFilter);
    },
    pageCount() {
      return Math.ceil(this.filteredChanges.length / this.itemsPerPage);
    },
  },
  mounted() {
    this.loadDataModelList();
    this.loadHistory();
  },
  methods: {
    /* ── 데이터모델 목록 ── */
    loadDataModelList() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(res => {
        this.dataModelList = (res.data || []).map(item => ({
          dataModelId: item.dataModelId,
          dataModelNm: item.dataModelNm,
          schemaNm: item.schemaNm,
        }));
      });
    },

    /* ── 모델 변경 시 수집이력 로드 ── */
    onModelChange(dmId) {
      this.clctList = [];
      this.prevClctId = null;
      this.currClctId = null;
      this.resetResult();
      if (!dmId) return;

      const _to   = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      const _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';

      axios.post(this.$APIURL.base + 'api/dm/getDataModelClctList', { schId: dmId, from: _from, to: _to }).then(res => {
        const sorted = (res.data || []).slice().sort((a, b) => b.clctEndDt.localeCompare(a.clctEndDt));
        this.clctList = sorted.map((item, idx) => ({
          ...item,
          clctDisplayDt: item.clctEndDt + (idx === 0 ? ' (최신)' : ''),
        }));
        // 자동 선택: 최신 2개가 있으면 이전=두번째, 현재=최신
        if (this.clctList.length >= 2) {
          this.currClctId = this.clctList[0].clctId;
          this.prevClctId = this.clctList[1].clctId;
        } else if (this.clctList.length === 1) {
          this.currClctId = this.clctList[0].clctId;
        }
      });
    },

    /* ── 구조 비교 실행 ── */
    executeCompare() {
      if (!this.canExecute) return;

      // 선택한 모델의 schemaNm 찾기
      const model = this.dataModelList.find(m => m.dataModelId === this.selectedModel);
      const schemaNm = model ? model.schemaNm : '';

      this.executing = true;
      this.resetResult();

      const body = {
        dataModelId: this.selectedModel,
        prevClctId: this.prevClctId,
        currClctId: this.currClctId,
        schemaNm: schemaNm,
      };

      axios.post(this.$APIURL.base + 'api/std/structDiag/execute', body).then(res => {
        this.executing = false;
        const data = res.data;

        if (!data || data.resultCode !== 200) {
          this.showSnackbar('구조 비교 실패: ' + ((data && data.resultMessage) || '알 수 없는 오류'), 'error');
          return;
        }

        const result = data.contents || data;
        this.applyResult(result);
        this.showSnackbar('구조 비교가 완료되었습니다.', 'success');
        this.loadHistory();
      }).catch(() => {
        this.executing = false;
        this.showSnackbar('서버 오류가 발생했습니다.', 'error');
      });
    },

    /* ── 결과 적용 ── */
    applyResult(result) {
      // stats
      this.stats.totalTableCnt  = result.totalTableCnt  || 0;
      this.stats.totalColumnCnt = result.totalColumnCnt || 0;
      this.stats.lastClctDt     = result.lastClctDt     || '';

      // changeList
      this.changeList = result.changes || [];
      this.stats.changeCnt = this.changeList.length;

      // summary 계산
      this.summary.addedTable      = this.changeList.filter(c => c.changeType === 'ADDED'    && !c.columnNm).length;
      this.summary.addedColumn     = this.changeList.filter(c => c.changeType === 'ADDED'    && c.columnNm).length;
      this.summary.modifiedColumn  = this.changeList.filter(c => c.changeType === 'MODIFIED').length;
      this.summary.deletedTable    = this.changeList.filter(c => c.changeType === 'DELETED'  && !c.columnNm).length;
      this.summary.deletedColumn   = this.changeList.filter(c => c.changeType === 'DELETED'  && c.columnNm).length;

      this.hasResult = true;
      this.changeTypeFilter = 'ALL';
      this.page = 1;
    },

    /* ── 결과 초기화 ── */
    resetResult() {
      this.hasResult = false;
      this.changeList = [];
      this.stats = { totalTableCnt: 0, totalColumnCnt: 0, changeCnt: 0, lastClctDt: '' };
      this.summary = { addedTable: 0, addedColumn: 0, modifiedColumn: 0, deletedTable: 0, deletedColumn: 0 };
      this.changeTypeFilter = 'ALL';
      this.page = 1;
    },

    /* ── 이전 진단 이력 ── */
    loadHistory() {
      axios.get(this.$APIURL.base + 'api/std/structDiag/history').then(res => {
        this.historyList = res.data || [];
      }).catch(() => {
        // 이력 로드 실패 시 무시
      });
    },

    /* ── 이력 행 클릭 → 해당 결과 로드 ── */
    onHistoryClick(item) {
      if (!item.diagId) return;

      axios.get(this.$APIURL.base + 'api/std/structDiag/result/' + item.diagId).then(res => {
        const data = res.data;
        const result = (data && data.contents) ? data.contents : data;
        if (result) {
          this.applyResult(result);
          this.showSnackbar(item.diagDt + ' 진단 결과를 불러왔습니다.', 'info');
        }
      }).catch(() => {
        this.showSnackbar('진단 결과 조회에 실패했습니다.', 'error');
      });
    },

    /* ── 유틸 ── */
    changeTypeColor(type) {
      const map = { ADDED: 'green', MODIFIED: 'orange', DELETED: 'red' };
      return map[type] || 'grey';
    },
    showSnackbar(msg, color = 'info') {
      this.snackbarMsg = msg;
      this.snackbarColor = color;
      this.snackbar = true;
    },
  },
};
</script>

<style scoped>
.filterLabel {
  font-size: .8rem;
  white-space: nowrap;
  color: #455A64;
  font-weight: 500;
}

/* 스탯 카드 */
.stat-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px !important;
}
.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14px;
  flex-shrink: 0;
}
.stat-content {
  display: flex;
  flex-direction: column;
}
.stat-value {
  font-size: 1.3rem;
  font-weight: 700;
  color: #263238;
  line-height: 1.2;
}
.stat-value-sm {
  font-size: .9rem;
}
.stat-label {
  font-size: .75rem;
  color: #78909C;
  margin-top: 2px;
}

.btn-sm.v-btn {
  height: 22px !important;
  width: auto !important;
  min-width: 0 !important;
  padding: 0 6px !important;
  font-size: .65rem;
  letter-spacing: 0;
}
</style>
