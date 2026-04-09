<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 설명 -->
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">수집된 데이터 모델과 현재 DBMS의 구조를 비교하여 변경점(테이블/컬럼 추가·변경·삭제)을 검사합니다.</span>
    </v-sheet>

    <!-- 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #E8EAF6; border-radius:4px;">
      <span class="filterLabel">데이터모델명</span>
      <v-autocomplete v-model="selectedModel" :items="dataModelList" item-text="dataModelNm" item-value="dataModelId"
        dense outlined hide-details clearable placeholder="데이터 모델 선택" style="width:280px; flex-grow:0;" @change="onModelChange" />

      <span class="filterLabel">수집일시</span>
      <v-select v-model="selectedClctId" :items="clctList" item-text="clctDisplayDt" item-value="clctId"
        dense outlined hide-details placeholder="수집일시 선택" style="width:280px; flex-grow:0;" :disabled="!selectedModel" />

      <v-spacer />

      <v-chip v-if="executing" small color="blue" text-color="white" class="mr-2">
        <v-progress-circular indeterminate size="14" width="2" class="mr-2" />
        {{ stepMessage }}
      </v-chip>

      <v-btn small color="primary" :disabled="!selectedModel || !selectedClctId || executing" @click="executeStructDiag">
        <v-icon small left>mdi-database-sync</v-icon> 진단 실행
      </v-btn>
    </v-sheet>

    <!-- 진단 이력 -->
    <v-sheet style="flex:1; overflow:auto; border:1px solid #E8EAF6; border-radius:4px;">
      <v-data-table :headers="historyHeaders" :items="historyList" :items-per-page="15"
        dense class="elevation-0" :loading="historyLoading">
        <template v-slot:item.status="{ item }">
          <v-chip x-small :color="statusColor(item.status)" text-color="white">{{ statusLabel(item.status) }}</v-chip>
        </template>
        <template v-slot:item.changeCnt="{ item }">
          <v-chip x-small :color="item.changeCnt > 0 ? 'orange' : 'green'" text-color="white">{{ item.changeCnt }}건</v-chip>
        </template>
        <template v-slot:item.actions="{ item }">
          <v-btn v-if="item.status === 'DONE'" x-small color="teal" dark @click="goToResult(item)"
            class="btn-sm">결과보기</v-btn>
        </template>
        <template v-slot:no-data>
          <span class="grey--text">진단 이력이 없습니다.</span>
        </template>
      </v-data-table>
    </v-sheet>

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSStructDiag',
  props: ['isMobile'],
  data() {
    return {
      dataModelList: [],
      selectedModel: null,
      clctList: [],
      selectedClctId: null,
      lastClctDt: '',
      executing: false,
      stepMessage: '',
      historyLoading: false,
      historyList: [],
      historyHeaders: [
        { text: '진단일시',       value: 'diagDt',         width: '150px' },
        { text: '데이터모델',     value: 'dataModelNm',    width: '180px' },
        { text: '데이터 모델\n수집일시', value: 'prevCollectDt',  width: '160px' },
        { text: '상태',           value: 'status',         width: '80px' },
        { text: '변경건수',       value: 'changeCnt',      width: '90px' },
        { text: '테이블 개수',    value: 'totalTables',    width: '90px' },
        { text: '컬럼 개수',      value: 'totalColumns',   width: '90px' },
        { text: '실행자',         value: 'cretUserId',     width: '100px' },
        { text: '결과',           value: 'actions',        width: '55px', sortable: false, align: 'center' },
      ],
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
    };
  },
  mounted() {
    this.loadDataModelList();
    this.loadHistory();
  },
  beforeDestroy() {
    if (this._diagTimer) clearTimeout(this._diagTimer);
  },
  methods: {
    loadDataModelList() {
      var self = this;
      axios.post(self.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(function(res) {
        self.dataModelList = (res.data || []).map(function(item) {
          return { dataModelId: item.dataModelId, dataModelNm: item.dataModelNm };
        });
      });
    },
    onModelChange(dmId) {
      this.clctList = [];
      this.selectedClctId = null;
      this.lastClctDt = '';
      if (!dmId) return;
      var self = this;
      var _to = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      var _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';
      axios.post(self.$APIURL.base + 'api/dm/getDataModelClctList', { schId: dmId, from: _from, to: _to }).then(function(res) {
        var sorted = (res.data || []).slice().sort(function(a, b) { return b.clctEndDt.localeCompare(a.clctEndDt); });
        self.clctList = sorted.map(function(item, idx) {
          return Object.assign({}, item, { clctDisplayDt: item.clctEndDt + (idx === 0 ? ' (최신)' : '') });
        });
        if (self.clctList.length > 0) {
          self.selectedClctId = self.clctList[0].clctId;
          self.lastClctDt = sorted[0].clctEndDt;
        }
      });
    },
    executeStructDiag() {
      if (!this.selectedModel) return;
      var self = this;
      if (!self.lastClctDt) {
        self.$swal.fire({ title: '수집 이력 없음', text: '먼저 데이터 모델 수집을 진행해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      self.executing = true;
      self.stepMessage = '분석 중...';
      var body = { dataModelId: self.selectedModel };
      if (self.selectedClctId) body.clctId = self.selectedClctId;
      axios.post(self.$APIURL.base + 'api/std/structDiag/execute', body).then(function(res) {
        var data = res.data;
        if (data && data.resultCode === 200 && data.contents) {
          self._diagId = data.contents;
          self.pollDiag();
        } else {
          self.executing = false;
          self.showSnackbar('진단 요청 실패', 'error');
        }
      }).catch(function() {
        self.executing = false;
        self.showSnackbar('서버 오류가 발생했습니다.', 'error');
      });
    },
    pollDiag() {
      var self = this;
      var diagId = self._diagId;
      var pollCount = 0;
      var poll = function() {
        pollCount++;
        if (pollCount > 120) {
          self.executing = false;
          self.showSnackbar('분석 시간 초과', 'warning');
          self.loadHistory();
          return;
        }
        axios.get(self.$APIURL.base + 'api/std/structDiag/status/' + diagId).then(function(res) {
          var data = res.data;
          if (data && data.status === 'DONE') {
            self.executing = false;
            var cnt = (data.addedTables||0)+(data.addedColumns||0)+(data.modifiedColumns||0)+(data.deletedTables||0)+(data.deletedColumns||0);
            self.showSnackbar('진단 완료: ' + (cnt > 0 ? cnt + '건 변경' : '변경 없음'), cnt > 0 ? 'warning' : 'success');
            self.loadHistory();
          } else if (data && data.status === 'ERROR') {
            self.executing = false;
            self.showSnackbar('진단 오류 발생', 'error');
            self.loadHistory();
          } else {
            self._diagTimer = setTimeout(poll, 2000);
          }
        }).catch(function() { self._diagTimer = setTimeout(poll, 2000); });
      };
      self._diagTimer = setTimeout(poll, 1000);
    },
    loadHistory() {
      var self = this;
      self.historyLoading = true;
      axios.get(self.$APIURL.base + 'api/std/structDiag/history').then(function(res) {
        self.historyList = (res.data || []).map(function(h) {
          return {
            diagId: h.diagId, diagDt: h.diagDt, dataModelId: h.dataModelId,
            dataModelNm: h.dataModelNm || '-', prevCollectDt: h.prevCollectDt || '-',
            status: h.status || 'DONE',
            totalTables: h.totalTables || 0, totalColumns: h.totalColumns || 0,
            changeCnt: (h.addedTables||0)+(h.addedColumns||0)+(h.modifiedColumns||0)+(h.deletedTables||0)+(h.deletedColumns||0),
            cretUserId: h.cretUserId,
          };
        });
      }).catch(function(){}).finally(function(){ self.historyLoading = false; });
    },
    goToResult(item) {
      eventBus.pendingStructDiagId = item.diagId;
      eventBus.$emit('openStructDiagResult');
    },
    statusColor(s) { return { READY: 'grey', RUNNING: 'blue', DONE: 'green', ERROR: 'red' }[s] || 'grey'; },
    statusLabel(s) { return { READY: '대기', RUNNING: '진행중', DONE: '완료', ERROR: '오류' }[s] || s; },
    showSnackbar(msg, color) { this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true; },
  },
};
</script>

<style scoped>
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
</style>
