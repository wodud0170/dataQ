<template>
  <v-container fluid class="pa-2" style="overflow:auto;">
    <!-- 설명 -->
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">수집된 데이터 모델과 현재 DBMS의 구조를 비교하여 변경점(테이블/컬럼/인덱스/제약조건)을 검사합니다.</span>
    </v-sheet>

    <!-- 검색조건 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #E8EAF6; border-radius:4px;">
      <span class="filterLabel">데이터모델</span>
      <v-select v-model="selectedModelNm" :items="modelList" dense outlined hide-details clearable
        placeholder="데이터모델 선택" style="width:220px; flex-grow:0;" @change="onModelChange" />
      <span class="filterLabel">진단이력</span>
      <v-select v-model="selectedDiagId" :items="diagList" item-text="displayText" item-value="diagId"
        dense outlined hide-details clearable placeholder="진단이력 선택" style="width:350px; flex-grow:0;"
        :disabled="!selectedModelNm" @change="loadResult" />
    </v-sheet>

    <!-- 결과 영역 -->
    <template v-if="hasResult">
      <!-- 스탯 카드 -->
      <v-row dense class="mb-2">
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#E8EAF6;"><v-icon color="#3F51B5" small>mdi-table</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalTables }}</div><div class="stat-label">전체 테이블</div></div>
          </v-card>
        </v-col>
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#E3F2FD;"><v-icon color="#1E88E5" small>mdi-view-column</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalColumns }}</div><div class="stat-label">전체 컬럼</div></div>
          </v-card>
        </v-col>
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#E0F2F1;"><v-icon color="#00897B" small>mdi-format-list-numbered</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalIndexes }}</div><div class="stat-label">전체 인덱스</div></div>
          </v-card>
        </v-col>
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#FFF3E0;"><v-icon color="#EF6C00" small>mdi-link-variant</v-icon></div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalConstraints }}</div><div class="stat-label">전체 제약조건</div></div>
          </v-card>
        </v-col>
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" :style="{ background: stats.totalChanges > 0 ? '#FFF3E0' : '#E8F5E9' }">
              <v-icon :color="stats.totalChanges > 0 ? '#FF9800' : '#4CAF50'" small>{{ stats.totalChanges > 0 ? 'mdi-alert-circle' : 'mdi-check-circle' }}</v-icon>
            </div>
            <div class="stat-content"><div class="stat-value">{{ stats.totalChanges }}</div><div class="stat-label">변경 항목</div></div>
          </v-card>
        </v-col>
        <v-col cols="2">
          <v-card class="stat-card" outlined>
            <div class="stat-icon-wrap" style="background:#F3E5F5;"><v-icon color="#7B1FA2" small>mdi-calendar</v-icon></div>
            <div class="stat-content"><div class="stat-value" style="font-size:.75rem;">{{ selectedDiagDt }}</div><div class="stat-label">진단일시</div></div>
          </v-card>
        </v-col>
      </v-row>

      <v-alert v-if="stats.totalChanges === 0" type="success" dense outlined class="mb-2">
        이전 수집 대비 변경사항이 없습니다. 현재 스키마와 동일합니다.
      </v-alert>

      <!-- 탭 분리: 컬럼 / 인덱스 / 제약조건 -->
      <v-tabs v-if="stats.totalChanges > 0" v-model="activeTab" dense class="mb-1" height="36" color="indigo">
        <v-tab>컬럼 변경 ({{ changeList.length }})</v-tab>
        <v-tab>인덱스 변경 ({{ indexChangeList.length }})</v-tab>
        <v-tab>제약조건 변경 ({{ constraintChangeList.length }})</v-tab>
      </v-tabs>

      <v-tabs-items v-if="stats.totalChanges > 0" v-model="activeTab">
        <!-- 컬럼 변경 탭 -->
        <v-tab-item>
          <v-sheet style="border:1px solid #E8EAF6; border-radius:0 0 4px 4px;">
            <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #E8EAF6;">
              <v-chip small :color="colFilter === 'ALL' ? 'indigo' : ''" :outlined="colFilter !== 'ALL'" @click="colFilter = 'ALL'">전체 ({{ changeList.length }})</v-chip>
              <v-chip small :color="colFilter === 'ADDED' ? 'green' : ''" :outlined="colFilter !== 'ADDED'" @click="colFilter = 'ADDED'">추가 ({{ countByType(changeList, 'ADDED') }})</v-chip>
              <v-chip small :color="colFilter === 'MODIFIED' ? 'orange' : ''" :outlined="colFilter !== 'MODIFIED'" @click="colFilter = 'MODIFIED'">변경 ({{ countByType(changeList, 'MODIFIED') }})</v-chip>
              <v-chip small :color="colFilter === 'DELETED' ? 'red' : ''" :outlined="colFilter !== 'DELETED'" @click="colFilter = 'DELETED'">삭제 ({{ countByType(changeList, 'DELETED') }})</v-chip>
              <v-spacer />
              <v-btn small outlined color="green darken-2" @click="downloadExcel"><v-icon small left>mdi-microsoft-excel</v-icon>엑셀 다운로드</v-btn>
            </div>
            <v-data-table :headers="colHeaders" :items="filteredCol" :items-per-page="20" dense class="elevation-0">
              <template v-slot:item.changeType="{ item }">
                <v-chip x-small :color="changeTypeColor(item.changeType)" text-color="white">{{ item.changeType }}</v-chip>
              </template>
              <template v-slot:item.prevType="{ item }">{{ item.prevDataType ? item.prevDataType + (item.prevDataLen ? '(' + item.prevDataLen + ')' : '') : '-' }}</template>
              <template v-slot:item.prevNullable="{ item }">{{ item.prevNullable || '-' }}</template>
              <template v-slot:item.currType="{ item }">{{ item.currDataType ? item.currDataType + (item.currDataLen ? '(' + item.currDataLen + ')' : '') : '-' }}</template>
              <template v-slot:item.currNullable="{ item }">{{ item.currNullable || '-' }}</template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>

        <!-- 인덱스 변경 탭 -->
        <v-tab-item>
          <v-sheet style="border:1px solid #E8EAF6; border-radius:0 0 4px 4px;">
            <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #E8EAF6;">
              <v-chip small :color="idxFilter === 'ALL' ? 'indigo' : ''" :outlined="idxFilter !== 'ALL'" @click="idxFilter = 'ALL'">전체 ({{ indexChangeList.length }})</v-chip>
              <v-chip small :color="idxFilter === 'ADDED' ? 'green' : ''" :outlined="idxFilter !== 'ADDED'" @click="idxFilter = 'ADDED'">추가 ({{ countByType(indexChangeList, 'ADDED') }})</v-chip>
              <v-chip small :color="idxFilter === 'MODIFIED' ? 'orange' : ''" :outlined="idxFilter !== 'MODIFIED'" @click="idxFilter = 'MODIFIED'">변경 ({{ countByType(indexChangeList, 'MODIFIED') }})</v-chip>
              <v-chip small :color="idxFilter === 'DELETED' ? 'red' : ''" :outlined="idxFilter !== 'DELETED'" @click="idxFilter = 'DELETED'">삭제 ({{ countByType(indexChangeList, 'DELETED') }})</v-chip>
              <v-spacer />
              <v-btn small outlined color="green darken-2" @click="downloadIndexExcel"><v-icon small left>mdi-microsoft-excel</v-icon>엑셀 다운로드</v-btn>
            </div>
            <v-data-table :headers="idxHeaders" :items="filteredIdx" :items-per-page="20" dense class="elevation-0">
              <template v-slot:item.changeType="{ item }">
                <v-chip x-small :color="changeTypeColor(item.changeType)" text-color="white">{{ item.changeType }}</v-chip>
              </template>
              <template v-slot:item.prevIndexType="{ item }">{{ item.prevIndexType || '-' }}</template>
              <template v-slot:item.currIndexType="{ item }">{{ item.currIndexType || '-' }}</template>
              <template v-slot:item.prevColumns="{ item }">{{ item.prevColumns || '-' }}</template>
              <template v-slot:item.currColumns="{ item }">{{ item.currColumns || '-' }}</template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>

        <!-- 제약조건 변경 탭 -->
        <v-tab-item>
          <v-sheet style="border:1px solid #E8EAF6; border-radius:0 0 4px 4px;">
            <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #E8EAF6;">
              <v-chip small :color="cstFilter === 'ALL' ? 'indigo' : ''" :outlined="cstFilter !== 'ALL'" @click="cstFilter = 'ALL'">전체 ({{ constraintChangeList.length }})</v-chip>
              <v-chip small :color="cstFilter === 'ADDED' ? 'green' : ''" :outlined="cstFilter !== 'ADDED'" @click="cstFilter = 'ADDED'">추가 ({{ countByType(constraintChangeList, 'ADDED') }})</v-chip>
              <v-chip small :color="cstFilter === 'MODIFIED' ? 'orange' : ''" :outlined="cstFilter !== 'MODIFIED'" @click="cstFilter = 'MODIFIED'">변경 ({{ countByType(constraintChangeList, 'MODIFIED') }})</v-chip>
              <v-chip small :color="cstFilter === 'DELETED' ? 'red' : ''" :outlined="cstFilter !== 'DELETED'" @click="cstFilter = 'DELETED'">삭제 ({{ countByType(constraintChangeList, 'DELETED') }})</v-chip>
              <v-spacer />
              <v-btn small outlined color="green darken-2" @click="downloadConstraintExcel"><v-icon small left>mdi-microsoft-excel</v-icon>엑셀 다운로드</v-btn>
            </div>
            <v-data-table :headers="cstHeaders" :items="filteredCst" :items-per-page="20" dense class="elevation-0">
              <template v-slot:item.changeType="{ item }">
                <v-chip x-small :color="changeTypeColor(item.changeType)" text-color="white">{{ item.changeType }}</v-chip>
              </template>
              <template v-slot:item.prevConstraintType="{ item }">{{ cstTypeLabel(item.prevConstraintType) }}</template>
              <template v-slot:item.currConstraintType="{ item }">{{ cstTypeLabel(item.currConstraintType) }}</template>
              <template v-slot:item.prevColumns="{ item }">{{ item.prevColumns || '-' }}</template>
              <template v-slot:item.currColumns="{ item }">{{ item.currColumns || '-' }}</template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>
      </v-tabs-items>
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
      modelList: [], selectedModelNm: null,
      collectDtList: [], selectedCollectDt: null,
      diagList: [], selectedDiagId: null,
      hasResult: false, selectedDiagDt: '',
      activeTab: 0,
      stats: { totalTables: 0, totalColumns: 0, totalIndexes: 0, totalConstraints: 0, totalChanges: 0 },
      // 컬럼
      changeList: [], colFilter: 'ALL',
      colHeaders: [
        { text: '오너', value: 'owner', width: '100px' },
        { text: '테이블명', value: 'tableNm', width: '160px' },
        { text: '컬럼명', value: 'columnNm', width: '160px' },
        { text: '변경유형', value: 'changeType', width: '90px' },
        { text: '이전 타입/길이', value: 'prevType', width: '130px', sortable: false, class: 'prev-header' },
        { text: '이전 Nullable', value: 'prevNullable', width: '100px', class: 'prev-header' },
        { text: '현재 타입/길이', value: 'currType', width: '130px', sortable: false, class: 'curr-header' },
        { text: '현재 Nullable', value: 'currNullable', width: '100px', class: 'curr-header' },
      ],
      // 인덱스
      indexChangeList: [], idxFilter: 'ALL',
      idxHeaders: [
        { text: '오너', value: 'owner', width: '100px' },
        { text: '테이블명', value: 'tableNm', width: '160px' },
        { text: '인덱스명', value: 'indexNm', width: '200px' },
        { text: '변경유형', value: 'changeType', width: '90px' },
        { text: '이전 타입', value: 'prevIndexType', width: '100px', class: 'prev-header' },
        { text: '이전 유니크', value: 'prevUniqueness', width: '100px', class: 'prev-header' },
        { text: '이전 구성컬럼', value: 'prevColumns', width: '200px', sortable: false, class: 'prev-header' },
        { text: '현재 타입', value: 'currIndexType', width: '100px', class: 'curr-header' },
        { text: '현재 유니크', value: 'currUniqueness', width: '100px', class: 'curr-header' },
        { text: '현재 구성컬럼', value: 'currColumns', width: '200px', sortable: false, class: 'curr-header' },
      ],
      // 제약조건
      constraintChangeList: [], cstFilter: 'ALL',
      cstHeaders: [
        { text: '오너', value: 'owner', width: '100px' },
        { text: '테이블명', value: 'tableNm', width: '150px' },
        { text: '제약조건명', value: 'constraintNm', width: '200px' },
        { text: '변경유형', value: 'changeType', width: '90px' },
        { text: '이전 유형', value: 'prevConstraintType', width: '80px', class: 'prev-header' },
        { text: '이전 구성컬럼', value: 'prevColumns', width: '150px', sortable: false, class: 'prev-header' },
        { text: '이전 참조', value: 'prevRefTable', width: '130px', class: 'prev-header' },
        { text: '현재 유형', value: 'currConstraintType', width: '80px', class: 'curr-header' },
        { text: '현재 구성컬럼', value: 'currColumns', width: '150px', sortable: false, class: 'curr-header' },
        { text: '현재 참조', value: 'currRefTable', width: '130px', class: 'curr-header' },
      ],
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
    };
  },
  computed: {
    filteredCol() {
      if (this.colFilter === 'ALL') return this.changeList;
      var f = this.colFilter;
      return this.changeList.filter(function(c) { return c.changeType === f; });
    },
    filteredIdx() {
      if (this.idxFilter === 'ALL') return this.indexChangeList;
      var f = this.idxFilter;
      return this.indexChangeList.filter(function(c) { return c.changeType === f; });
    },
    filteredCst() {
      if (this.cstFilter === 'ALL') return this.constraintChangeList;
      var f = this.cstFilter;
      return this.constraintChangeList.filter(function(c) { return c.changeType === f; });
    },
  },
  mounted() { this.checkPendingAndLoad(); },
  activated() { this.checkPendingAndLoad(); },
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
        var modelSet = {};
        allHistory.forEach(function(h) { if (h.dataModelNm) modelSet[h.dataModelNm] = true; });
        self.modelList = Object.keys(modelSet).sort();
        self._allHistory = allHistory.map(function(h) {
          var cnt = (h.addedTables||0)+(h.addedColumns||0)+(h.modifiedColumns||0)+(h.deletedTables||0)+(h.deletedColumns||0)
              +(h.addedIndexes||0)+(h.modifiedIndexes||0)+(h.deletedIndexes||0)
              +(h.addedConstraints||0)+(h.modifiedConstraints||0)+(h.deletedConstraints||0);
          return {
            diagId: h.diagId, diagDt: h.diagDt, dataModelNm: h.dataModelNm || '-',
            status: h.status || 'DONE', changeCnt: cnt,
            totalTables: h.totalTables || 0, totalColumns: h.totalColumns || 0,
            totalIndexes: h.totalIndexes || 0, totalConstraints: h.totalConstraints || 0,
            prevCollectDt: h.prevCollectDt || '',
            displayText: h.diagDt + ' (' + cnt + '건 변경)',
          };
        });
        if (pendingDiagId) {
          var target = self._allHistory.find(function(h) { return h.diagId === pendingDiagId; });
          if (target) {
            self.selectedModelNm = target.dataModelNm;
            self.onModelChange();
            self.selectedDiagId = target.diagId;
            self.loadResult();
          }
        }
      });
    },
    onModelChange() {
      this.selectedDiagId = null;
      this.diagList = []; this.hasResult = false;
      if (!this.selectedModelNm || !this._allHistory) return;
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
          var colChanges = (h.addedTables||0)+(h.addedColumns||0)+(h.modifiedColumns||0)+(h.deletedTables||0)+(h.deletedColumns||0);
          var idxChanges = (h.addedIndexes||0)+(h.modifiedIndexes||0)+(h.deletedIndexes||0);
          var cstChanges = (h.addedConstraints||0)+(h.modifiedConstraints||0)+(h.deletedConstraints||0);
          self.stats = {
            totalTables: h.totalTables || 0, totalColumns: h.totalColumns || 0,
            totalIndexes: h.totalIndexes || 0, totalConstraints: h.totalConstraints || 0,
            totalChanges: colChanges + idxChanges + cstChanges,
          };
          self.selectedDiagDt = h.diagDt || '';
          self.changeList = data.details || [];
          self.indexChangeList = data.indexDetails || [];
          self.constraintChangeList = data.constraintDetails || [];
          self.hasResult = true;
          self.colFilter = 'ALL'; self.idxFilter = 'ALL'; self.cstFilter = 'ALL';
          self.activeTab = 0;
        }
      }).catch(function() { self.showSnackbar('결과 조회 실패', 'error'); });
    },
    countByType(list, type) { return list.filter(function(c) { return c.changeType === type; }).length; },
    changeTypeColor(type) { return { ADDED: 'green', MODIFIED: 'orange', DELETED: 'red' }[type] || 'grey'; },
    cstTypeLabel(type) { return { P: 'PK', R: 'FK', U: 'UK', C: 'CHECK' }[type] || type || '-'; },
    showSnackbar(msg, color) { this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true; },
    downloadExcel() { this._downloadFile('downloadResult', '구조진단결과_컬럼_'); },
    downloadIndexExcel() { this._downloadFile('downloadIndexResult', '구조진단결과_인덱스_'); },
    downloadConstraintExcel() { this._downloadFile('downloadConstraintResult', '구조진단결과_제약조건_'); },
    _downloadFile(api, prefix) {
      if (!this.selectedDiagId) return;
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/' + api, {
        params: { diagId: self.selectedDiagId }, responseType: 'blob'
      }).then(function(response) {
        var url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
        var a = document.createElement('a');
        a.href = url;
        a.download = prefix + (self.selectedDiagDt || '').replace(/[:\s]/g, '') + '.xlsx';
        a.click(); window.URL.revokeObjectURL(url);
      }).catch(function() { self.showSnackbar('엑셀 다운로드 실패', 'error'); });
    },
  },
};
</script>

<style scoped>
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.stat-card { display: flex; align-items: center; padding: 8px 12px; border-radius: 8px !important; }
.stat-icon-wrap { width: 36px; height: 36px; border-radius: 8px; display: flex; align-items: center; justify-content: center; margin-right: 10px; flex-shrink: 0; }
.stat-content { flex: 1; }
.stat-value { font-size: 1.1rem; font-weight: 700; color: #263238; }
.stat-label { font-size: .65rem; color: #90A4AE; }
</style>
<style>
.prev-header { background: #FFF3E0 !important; }
.curr-header { background: #E3F2FD !important; }
</style>
