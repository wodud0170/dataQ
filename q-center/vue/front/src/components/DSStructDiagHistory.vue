<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 이력 목록 -->
    <v-sheet class="pa-0" style="border:1px solid #E8EAF6; border-radius:4px;">
      <div class="d-flex align-center pa-3" style="border-bottom:1px solid #E8EAF6;">
        <v-icon small color="primary" class="mr-2">mdi-history</v-icon>
        <span style="font-size:.9rem; font-weight:600;">구조 진단 이력</span>
        <v-spacer />
        <v-btn x-small text color="primary" @click="loadHistory">
          <v-icon x-small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </div>
      <v-data-table
        :headers="historyHeaders"
        :items="historyList"
        :items-per-page="15"
        dense class="elevation-0"
        @click:row="onHistoryClick"
        style="cursor:pointer;"
        :loading="loading"
      >
        <template v-slot:item.status="{ item }">
          <v-chip x-small :color="statusColor(item.status)" text-color="white">{{ statusLabel(item.status) }}</v-chip>
        </template>
        <template v-slot:item.changeCnt="{ item }">
          <v-chip x-small :color="item.changeCnt > 0 ? 'orange' : 'green'" text-color="white">{{ item.changeCnt }}건</v-chip>
        </template>
        <template v-slot:no-data>
          <span class="grey--text">진단 이력이 없습니다. [구조 진단] 메뉴에서 진단을 실행해주세요.</span>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 선택한 진단의 상세 결과 -->
    <template v-if="hasResult">
      <!-- 스탯 카드 -->
      <v-row dense class="mt-3 mb-2">
        <v-col cols="3">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#E8EAF6;"><v-icon color="#3F51B5">mdi-table</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalTables }}</div><div class="stat-label">전체 테이블</div></div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#E3F2FD;"><v-icon color="#1E88E5">mdi-view-column</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalColumns }}</div><div class="stat-label">전체 컬럼</div></div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" :style="{ background: stats.totalChanges > 0 ? '#FFF3E0' : '#E8F5E9' }">
              <v-icon :color="stats.totalChanges > 0 ? '#FF9800' : '#4CAF50'">{{ stats.totalChanges > 0 ? 'mdi-alert-circle' : 'mdi-check-circle' }}</v-icon>
            </div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalChanges }}</div><div class="stat-label">변경 항목</div></div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#F3E5F5;"><v-icon color="#7B1FA2">mdi-calendar</v-icon></div>
            <div class="stat-content"><div class="stat-value" style="font-size:.75rem;">{{ selectedDiagDt }}</div><div class="stat-label">진단일시</div></div>
          </v-card>
        </v-col>
      </v-row>

      <!-- 변경 요약 칩 -->
      <div v-if="stats.totalChanges > 0" class="d-flex align-center mb-2" style="gap:12px;">
        <v-chip small color="green" text-color="white"><v-icon x-small left>mdi-plus</v-icon>추가: 테이블 {{ summary.addedTables }}, 컬럼 {{ summary.addedColumns }}</v-chip>
        <v-chip small color="orange" text-color="white"><v-icon x-small left>mdi-pencil</v-icon>변경: 컬럼 {{ summary.modifiedColumns }}</v-chip>
        <v-chip small color="red" text-color="white"><v-icon x-small left>mdi-minus</v-icon>삭제: 테이블 {{ summary.deletedTables }}, 컬럼 {{ summary.deletedColumns }}</v-chip>
      </div>

      <v-alert v-if="stats.totalChanges === 0" type="success" dense outlined class="mb-2">
        변경사항이 없습니다.
      </v-alert>

      <!-- 변경사항 테이블 -->
      <v-sheet v-if="stats.totalChanges > 0" style="flex:1; border:1px solid #E8EAF6; border-radius:4px; overflow:auto;">
        <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #E8EAF6;">
          <v-chip small :color="changeTypeFilter === 'ALL' ? 'indigo' : ''" :outlined="changeTypeFilter !== 'ALL'" @click="changeTypeFilter = 'ALL'">전체 ({{ changeList.length }})</v-chip>
          <v-chip small :color="changeTypeFilter === 'ADDED' ? 'green' : ''" :outlined="changeTypeFilter !== 'ADDED'" @click="changeTypeFilter = 'ADDED'">추가 ({{ countByType('ADDED') }})</v-chip>
          <v-chip small :color="changeTypeFilter === 'MODIFIED' ? 'orange' : ''" :outlined="changeTypeFilter !== 'MODIFIED'" @click="changeTypeFilter = 'MODIFIED'">변경 ({{ countByType('MODIFIED') }})</v-chip>
          <v-chip small :color="changeTypeFilter === 'DELETED' ? 'red' : ''" :outlined="changeTypeFilter !== 'DELETED'" @click="changeTypeFilter = 'DELETED'">삭제 ({{ countByType('DELETED') }})</v-chip>
        </div>
        <v-data-table :headers="changeHeaders" :items="filteredChanges" :items-per-page="20" dense class="elevation-0">
          <template v-slot:item.changeType="{ item }">
            <v-chip x-small :color="changeTypeColor(item.changeType)" text-color="white">{{ item.changeType }}</v-chip>
          </template>
          <template v-slot:item.prevType="{ item }">
            {{ item.prevDataType ? item.prevDataType + (item.prevDataLen ? '(' + item.prevDataLen + ')' : '') : '-' }}
          </template>
          <template v-slot:item.currType="{ item }">
            {{ item.currDataType ? item.currDataType + (item.currDataLen ? '(' + item.currDataLen + ')' : '') : '-' }}
          </template>
        </v-data-table>
      </v-sheet>
    </template>

    <!-- 결과 미선택 안내 -->
    <v-sheet v-if="!hasResult" class="mt-3" style="flex:1; display:flex; align-items:center; justify-content:center; border:1px solid #E8EAF6; border-radius:4px;">
      <div class="text-center grey--text">
        <v-icon large color="grey lighten-1" class="mb-2">mdi-history</v-icon>
        <div>위 목록에서 진단 이력을 클릭하면 상세 결과를 확인할 수 있습니다.</div>
      </div>
    </v-sheet>

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSStructDiagHistory',
  props: ['isMobile'],
  data() {
    return {
      loading: false,
      historyList: [],
      historyHeaders: [
        { text: '진단일시', value: 'diagDt', width: '180px' },
        { text: '데이터모델', value: 'dataModelNm', width: '200px' },
        { text: '상태', value: 'status', width: '90px' },
        { text: '변경건수', value: 'changeCnt', width: '100px' },
        { text: '전체테이블', value: 'totalTables', width: '100px' },
        { text: '전체컬럼', value: 'totalColumns', width: '100px' },
        { text: '실행자', value: 'cretUserId', width: '120px' },
      ],
      hasResult: false,
      selectedDiagDt: '',
      stats: { totalTables: 0, totalColumns: 0, totalChanges: 0 },
      summary: { addedTables: 0, addedColumns: 0, modifiedColumns: 0, deletedTables: 0, deletedColumns: 0 },
      changeList: [],
      changeTypeFilter: 'ALL',
      changeHeaders: [
        { text: '테이블명', value: 'tableNm', width: '180px' },
        { text: '컬럼명', value: 'columnNm', width: '180px' },
        { text: '변경유형', value: 'changeType', width: '110px' },
        { text: '이전 타입/길이', value: 'prevType', width: '150px', sortable: false },
        { text: '현재 타입/길이', value: 'currType', width: '150px', sortable: false },
      ],
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
    };
  },
  computed: {
    filteredChanges() {
      if (this.changeTypeFilter === 'ALL') return this.changeList;
      return this.changeList.filter(function(c) { return c.changeType === this.changeTypeFilter; }.bind(this));
    },
  },
  mounted() {
    this.loadHistory();
  },
  methods: {
    loadHistory() {
      var self = this;
      self.loading = true;
      axios.get(self.$APIURL.base + 'api/std/structDiag/history').then(function(res) {
        self.historyList = (res.data || []).map(function(h) {
          return {
            diagId: h.diagId, diagDt: h.diagDt,
            dataModelNm: h.dataModelNm || '-',
            status: h.status || 'DONE',
            totalTables: h.totalTables || 0,
            totalColumns: h.totalColumns || 0,
            changeCnt: (h.addedTables || 0) + (h.addedColumns || 0) + (h.modifiedColumns || 0) + (h.deletedTables || 0) + (h.deletedColumns || 0),
            cretUserId: h.cretUserId,
          };
        });
      }).catch(function() {}).finally(function() { self.loading = false; });
    },
    onHistoryClick(item) {
      if (!item.diagId || item.status !== 'DONE') {
        this.showSnackbar('완료된 진단만 결과를 볼 수 있습니다.', 'warning');
        return;
      }
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/result/' + item.diagId).then(function(res) {
        var data = res.data;
        if (data && data.history) {
          var h = data.history;
          self.stats.totalTables = h.totalTables || 0;
          self.stats.totalColumns = h.totalColumns || 0;
          self.stats.totalChanges = (h.addedTables || 0) + (h.addedColumns || 0) + (h.modifiedColumns || 0) + (h.deletedTables || 0) + (h.deletedColumns || 0);
          self.summary = { addedTables: h.addedTables || 0, addedColumns: h.addedColumns || 0, modifiedColumns: h.modifiedColumns || 0, deletedTables: h.deletedTables || 0, deletedColumns: h.deletedColumns || 0 };
          self.selectedDiagDt = item.diagDt;
          self.changeList = data.details || [];
          self.hasResult = true;
          self.changeTypeFilter = 'ALL';
          self.showSnackbar(item.diagDt + ' 진단 결과', 'info');
        }
      }).catch(function() { self.showSnackbar('결과 조회 실패', 'error'); });
    },
    countByType(type) { return this.changeList.filter(function(c) { return c.changeType === type; }).length; },
    changeTypeColor(type) { return { ADDED: 'green', MODIFIED: 'orange', DELETED: 'red' }[type] || 'grey'; },
    statusColor(status) { return { READY: 'grey', RUNNING: 'blue', DONE: 'green', ERROR: 'red' }[status] || 'grey'; },
    statusLabel(status) { return { READY: '대기', RUNNING: '진행중', DONE: '완료', ERROR: '오류' }[status] || status; },
    showSnackbar(msg, color) { this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true; },
  },
};
</script>

<style scoped>
.stat-card { display: flex; align-items: center; padding: 12px 16px; border-radius: 8px !important; }
.stat-icon-wrap { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; margin-right: 14px; }
.stat-content { display: flex; flex-direction: column; }
.stat-value { font-size: 1.3rem; font-weight: 700; color: #263238; line-height: 1.2; }
.stat-label { font-size: .7rem; color: #78909C; margin-top: 2px; }
</style>
