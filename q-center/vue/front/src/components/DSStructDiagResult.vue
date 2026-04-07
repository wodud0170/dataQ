<template>
  <v-container fluid class="pa-2" style="overflow:auto;">
    <!-- 설명 -->
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">수집된 스키마 스냅샷과 현재 DBMS의 구조를 비교하여 변경점(테이블/컬럼 추가·변경·삭제)을 검사합니다.</span>
    </v-sheet>

    <!-- 검색조건 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #E8EAF6; border-radius:4px;">
      <span class="filterLabel">데이터모델</span>
      <v-select v-model="selectedModelNm" :items="modelList" dense outlined hide-details clearable
        placeholder="데이터모델 선택" style="width:250px; flex-grow:0;" @change="onModelChange" />

      <span class="filterLabel">진단일시</span>
      <v-select v-model="selectedDiagId" :items="diagList" item-text="displayText" item-value="diagId"
        dense outlined hide-details clearable placeholder="진단 이력 선택" style="width:350px; flex-grow:0;"
        @change="loadResult" />
    </v-sheet>

    <!-- 결과 영역 -->
    <template v-if="hasResult">
      <!-- 스탯 카드 -->
      <v-row dense class="mb-2">
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
            <div class="stat-content"><div class="stat-value" style="font-size:.8rem;">{{ selectedDiagDt }}</div><div class="stat-label">진단일시</div></div>
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
        이전 수집 대비 변경사항이 없습니다. 현재 스키마와 동일합니다.
      </v-alert>

      <!-- 변경사항 테이블 -->
      <v-sheet v-if="stats.totalChanges > 0" style="border:1px solid #E8EAF6; border-radius:4px;">
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
          <template v-slot:item.prevType="{ item }">{{ item.prevDataType ? item.prevDataType + (item.prevDataLen ? '(' + item.prevDataLen + ')' : '') : '-' }}</template>
          <template v-slot:item.currType="{ item }">{{ item.currDataType ? item.currDataType + (item.currDataLen ? '(' + item.currDataLen + ')' : '') : '-' }}</template>
        </v-data-table>
      </v-sheet>
    </template>

    <!-- 미선택 안내 -->
    <v-sheet v-if="!hasResult" style="display:flex; align-items:center; justify-content:center; border:1px solid #E8EAF6; border-radius:4px; min-height:200px;">
      <div class="text-center grey--text">
        <v-icon large color="grey lighten-1" class="mb-2">mdi-file-search</v-icon>
        <div>데이터모델과 진단일시를 선택하면 결과를 확인할 수 있습니다.</div>
      </div>
    </v-sheet>

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSStructDiagResult',
  props: ['isMobile'],
  data() {
    return {
      // 검색조건
      modelList: [],
      selectedModelNm: null,
      diagList: [],
      selectedDiagId: null,
      // 결과
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
      var f = this.changeTypeFilter;
      return this.changeList.filter(function(c) { return c.changeType === f; });
    },
  },
  mounted() {
    this.checkPendingAndLoad();
  },
  activated() {
    // keep-alive에서 다시 활성화될 때
    this.checkPendingAndLoad();
  },
  methods: {
    checkPendingAndLoad() {
      var pendingId = null;
      if (eventBus.pendingStructDiagId) {
        pendingId = eventBus.pendingStructDiagId;
        eventBus.pendingStructDiagId = null;
      }
      this.loadAllHistory(pendingId);
    },
    loadAllHistory(pendingDiagId) {
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/history').then(function(res) {
        var allHistory = res.data || [];
        // 모델 목록 추출 (중복 제거)
        var modelSet = {};
        allHistory.forEach(function(h) {
          if (h.dataModelNm) modelSet[h.dataModelNm] = true;
        });
        self.modelList = Object.keys(modelSet).sort();

        // 전체 이력 저장
        self._allHistory = allHistory.map(function(h) {
          var cnt = (h.addedTables||0)+(h.addedColumns||0)+(h.modifiedColumns||0)+(h.deletedTables||0)+(h.deletedColumns||0);
          return {
            diagId: h.diagId, diagDt: h.diagDt, dataModelNm: h.dataModelNm || '-',
            status: h.status || 'DONE', changeCnt: cnt,
            totalTables: h.totalTables || 0, totalColumns: h.totalColumns || 0,
            displayText: h.diagDt + ' (' + cnt + '건 변경)',
          };
        });

        // 진단 실행에서 [결과보기]로 넘어온 경우 자동 선택
        if (pendingDiagId) {
          var target = self._allHistory.find(function(h) { return h.diagId === pendingDiagId; });
          if (target) {
            self.selectedModelNm = target.dataModelNm;
            self.diagList = self._allHistory.filter(function(h) { return h.dataModelNm === target.dataModelNm && h.status === 'DONE'; });
            self.selectedDiagId = target.diagId;
            self.loadResult();
          }
        }
      });
    },
    onModelChange() {
      this.selectedDiagId = null;
      this.hasResult = false;
      if (!this.selectedModelNm || !this._allHistory) {
        this.diagList = [];
        return;
      }
      var nm = this.selectedModelNm;
      this.diagList = this._allHistory.filter(function(h) { return h.dataModelNm === nm && h.status === 'DONE'; });
    },
    loadResult() {
      if (!this.selectedDiagId) return;
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/result/' + self.selectedDiagId).then(function(res) {
        var data = res.data;
        if (data && data.history) {
          var h = data.history;
          self.stats = {
            totalTables: h.totalTables || 0, totalColumns: h.totalColumns || 0,
            totalChanges: (h.addedTables||0)+(h.addedColumns||0)+(h.modifiedColumns||0)+(h.deletedTables||0)+(h.deletedColumns||0)
          };
          self.summary = { addedTables: h.addedTables||0, addedColumns: h.addedColumns||0, modifiedColumns: h.modifiedColumns||0, deletedTables: h.deletedTables||0, deletedColumns: h.deletedColumns||0 };
          self.selectedDiagDt = h.diagDt || '';
          self.changeList = data.details || [];
          self.hasResult = true;
          self.changeTypeFilter = 'ALL';
        }
      }).catch(function() { self.showSnackbar('결과 조회 실패', 'error'); });
    },
    countByType(type) { return this.changeList.filter(function(c) { return c.changeType === type; }).length; },
    changeTypeColor(type) { return { ADDED: 'green', MODIFIED: 'orange', DELETED: 'red' }[type] || 'grey'; },
    showSnackbar(msg, color) { this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true; },
  },
};
</script>

<style scoped>
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.stat-card { display: flex; align-items: center; padding: 10px 14px; border-radius: 8px !important; }
.stat-icon-wrap { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; margin-right: 12px; flex-shrink: 0; }
.stat-content { flex: 1; }
.stat-value { font-size: 1.2rem; font-weight: 700; color: #263238; }
.stat-label { font-size: .7rem; color: #90A4AE; }
</style>
