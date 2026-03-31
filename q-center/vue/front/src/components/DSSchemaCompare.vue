<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #E8EAF6; border-radius:4px;">
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

      <span class="filterLabel">수집일시</span>
      <v-select
        v-model="selectedClctId"
        :items="clctList"
        item-text="clctDisplayDt"
        item-value="clctId"
        dense outlined hide-details
        placeholder="수집일시 선택"
        style="width:280px; flex-grow:0;"
        :disabled="!selectedModel"
      />

      <v-spacer />

      <v-btn small color="primary" :disabled="!selectedClctId" :loading="comparing" @click="runCompare">
        <v-icon small left>mdi-compare</v-icon>
        비교 실행
      </v-btn>
    </v-sheet>

    <!-- 진행 표시 -->
    <v-sheet v-if="comparing" class="pa-4 mb-2 text-center" style="border:1px solid #E8EAF6; border-radius:4px;">
      <v-progress-circular indeterminate color="primary" size="32" class="mr-3" />
      <span>DBMS에 접속하여 스키마를 비교하고 있습니다...</span>
    </v-sheet>

    <!-- 요약 카드 -->
    <v-row v-if="hasResult" dense class="mb-2">
      <v-col cols="2">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8EAF6;">
            <v-icon color="#3F51B5">mdi-table</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ summary.totalTables }}</div>
            <div class="stat-label">전체</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="2">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8F5E9;">
            <v-icon color="#4CAF50">mdi-check-circle</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ summary.sameTables }}</div>
            <div class="stat-label">동일</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="2">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#FFF3E0;">
            <v-icon color="#FF9800">mdi-pencil-circle</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ summary.modifiedTables }}</div>
            <div class="stat-label">변경</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="2">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8F5E9;">
            <v-icon color="#4CAF50">mdi-plus-circle</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ summary.addedTables }}</div>
            <div class="stat-label">추가</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="2">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#FFEBEE;">
            <v-icon color="#F44336">mdi-minus-circle</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ summary.deletedTables }}</div>
            <div class="stat-label">삭제</div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- 좌우 분할 -->
    <v-sheet v-if="hasResult" style="flex:1; display:flex; gap:8px; overflow:hidden;">
      <!-- 좌측: 테이블 목록 -->
      <v-card outlined style="width:280px; min-width:280px; display:flex; flex-direction:column;">
        <div class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:6px;">
          <span class="filterLabel" style="font-weight:600;">테이블 목록</span>
          <v-spacer />
          <v-btn-toggle v-model="tableFilter" dense mandatory>
            <v-btn x-small value="ALL">전체</v-btn>
            <v-btn x-small value="CHANGED">변경만</v-btn>
          </v-btn-toggle>
        </div>
        <v-list dense style="flex:1; overflow-y:auto;" class="pa-0">
          <v-list-item
            v-for="tbl in filteredTables"
            :key="tbl.tableNm"
            :class="{ 'v-list-item--active': selectedTable && selectedTable.tableNm === tbl.tableNm }"
            @click="selectTable(tbl)"
            style="min-height:36px; border-bottom:1px solid #f5f5f5;"
          >
            <v-list-item-icon class="mr-2 my-auto">
              <v-icon small :color="tableStatusColor(tbl.status)">{{ tableStatusIcon(tbl.status) }}</v-icon>
            </v-list-item-icon>
            <v-list-item-content class="py-1">
              <v-list-item-title style="font-size:.8rem;">{{ tbl.tableNm }}</v-list-item-title>
            </v-list-item-content>
            <v-chip x-small :color="tableStatusColor(tbl.status)" text-color="white" class="ml-1">
              {{ tableStatusLabel(tbl.status) }}
            </v-chip>
          </v-list-item>
          <v-list-item v-if="filteredTables.length === 0">
            <v-list-item-content>
              <span class="grey--text text-caption">해당 조건의 테이블이 없습니다.</span>
            </v-list-item-content>
          </v-list-item>
        </v-list>
      </v-card>

      <!-- 우측: 컬럼 비교 그리드 -->
      <v-card outlined style="flex:1; display:flex; flex-direction:column; overflow:hidden;">
        <div class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
          <span class="filterLabel" style="font-weight:600;">
            {{ selectedTable ? selectedTable.tableNm : '테이블을 선택하세요' }}
          </span>
          <v-chip v-if="selectedTable" x-small :color="tableStatusColor(selectedTable.status)" text-color="white">
            {{ tableStatusLabel(selectedTable.status) }}
          </v-chip>
          <v-spacer />
          <v-checkbox
            v-if="selectedTable"
            v-model="changedOnly"
            label="변경만 보기"
            hide-details dense
            class="mt-0 pt-0"
            style="font-size:.75rem;"
          />
        </div>
        <v-data-table
          v-if="selectedTable"
          :headers="columnHeaders"
          :items="filteredColumns"
          :items-per-page="50"
          dense
          hide-default-footer
          class="elevation-0 schema-compare-table"
          style="flex:1; overflow-y:auto;"
          :item-class="columnRowClass"
        >
          <template v-slot:item.status="{ item }">
            <v-chip x-small :color="tableStatusColor(item.status)" text-color="white">{{ tableStatusLabel(item.status) }}</v-chip>
          </template>
          <template v-slot:item.snapshotLen="{ item }">
            {{ item.snapshotLen != null ? item.snapshotLen : '-' }}
          </template>
          <template v-slot:item.currentLen="{ item }">
            {{ item.currentLen != null ? item.currentLen : '-' }}
          </template>
          <template v-slot:item.snapshotType="{ item }">
            {{ item.snapshotType || '-' }}
          </template>
          <template v-slot:item.currentType="{ item }">
            {{ item.currentType || '-' }}
          </template>
          <template v-slot:item.snapshotNullable="{ item }">
            {{ item.snapshotNullable || '-' }}
          </template>
          <template v-slot:item.currentNullable="{ item }">
            {{ item.currentNullable || '-' }}
          </template>
        </v-data-table>
        <div v-else style="flex:1; display:flex; align-items:center; justify-content:center;">
          <span class="grey--text">좌측에서 테이블을 선택하세요.</span>
        </div>
      </v-card>
    </v-sheet>

    <!-- 결과 없을 때 -->
    <v-sheet v-if="!hasResult && !comparing" style="flex:1; display:flex; align-items:center; justify-content:center; border:1px solid #E8EAF6; border-radius:4px;">
      <div class="text-center grey--text">
        <v-icon large color="grey lighten-1" class="mb-2">mdi-compare</v-icon>
        <div>데이터모델과 수집일시를 선택한 후 [비교 실행] 버튼을 클릭하세요.</div>
        <div class="mt-1 text-caption">수집 스냅샷과 현재 DBMS 스키마를 비교합니다.</div>
      </div>
    </v-sheet>

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">
      {{ snackbarMsg }}
    </v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSSchemaCompare',
  props: ['isMobile'],
  data: function() {
    return {
      dataModelList: [],
      selectedModel: null,
      clctList: [],
      selectedClctId: null,
      comparing: false,
      hasResult: false,
      tables: [],
      summary: { totalTables: 0, sameTables: 0, modifiedTables: 0, addedTables: 0, deletedTables: 0 },
      tableFilter: 'ALL',
      selectedTable: null,
      changedOnly: false,
      columnHeaders: [
        { text: '컬럼명',         value: 'columnNm',         width: '150px' },
        { text: '수집 타입',      value: 'snapshotType',     width: '100px' },
        { text: '수집 길이',      value: 'snapshotLen',      width: '80px' },
        { text: '수집 Nullable',  value: 'snapshotNullable', width: '100px' },
        { text: '현재 타입',      value: 'currentType',      width: '100px' },
        { text: '현재 길이',      value: 'currentLen',       width: '80px' },
        { text: '현재 Nullable',  value: 'currentNullable',  width: '100px' },
        { text: '상태',           value: 'status',           width: '80px' },
      ],
      snackbar: false,
      snackbarMsg: '',
      snackbarColor: 'info',
    };
  },
  computed: {
    filteredTables: function() {
      if (this.tableFilter === 'CHANGED') {
        return this.tables.filter(function(t) { return t.status !== 'SAME'; });
      }
      return this.tables;
    },
    filteredColumns: function() {
      if (!this.selectedTable || !this.selectedTable.columns) return [];
      if (this.changedOnly) {
        return this.selectedTable.columns.filter(function(c) { return c.status !== 'SAME'; });
      }
      return this.selectedTable.columns;
    },
  },
  mounted: function() {
    this.loadDataModelList();
  },
  methods: {
    loadDataModelList: function() {
      var self = this;
      axios.post(self.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(function(res) {
        self.dataModelList = (res.data || []).map(function(item) {
          return {
            dataModelId: item.dataModelId,
            dataModelNm: item.dataModelNm,
          };
        });
      });
    },
    onModelChange: function(dmId) {
      this.clctList = [];
      this.selectedClctId = null;
      this.resetResult();
      if (!dmId) return;

      var self = this;
      var _to   = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      var _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';

      axios.post(self.$APIURL.base + 'api/dm/getDataModelClctList', { schId: dmId, from: _from, to: _to }).then(function(res) {
        var sorted = (res.data || []).slice().sort(function(a, b) { return b.clctEndDt.localeCompare(a.clctEndDt); });
        self.clctList = sorted.map(function(item, idx) {
          return Object.assign({}, item, {
            clctDisplayDt: item.clctEndDt + (idx === 0 ? ' (최신)' : '')
          });
        });
        if (self.clctList.length > 0) {
          self.selectedClctId = self.clctList[0].clctId;
        }
      });
    },
    runCompare: function() {
      if (!this.selectedModel || !this.selectedClctId) return;
      var self = this;
      self.comparing = true;
      self.resetResult();

      var body = { dataModelId: self.selectedModel, clctId: self.selectedClctId };
      axios.post(self.$APIURL.base + 'api/std/structDiag/compareSchema', body).then(function(res) {
        self.comparing = false;
        var data = res.data;
        if (data && data.resultCode === 200 && data.contents) {
          var parsed = typeof data.contents === 'string' ? JSON.parse(data.contents) : data.contents;
          self.tables = parsed.tables || [];
          self.summary = parsed.summary || { totalTables: 0, sameTables: 0, modifiedTables: 0, addedTables: 0, deletedTables: 0 };
          self.hasResult = true;
          // 첫 번째 변경된 테이블 자동 선택
          var changed = self.tables.filter(function(t) { return t.status !== 'SAME'; });
          if (changed.length > 0) {
            self.selectedTable = changed[0];
          } else if (self.tables.length > 0) {
            self.selectedTable = self.tables[0];
          }
          var totalChanges = (self.summary.modifiedTables || 0) + (self.summary.addedTables || 0) + (self.summary.deletedTables || 0);
          self.showSnackbar(
            totalChanges > 0 ? '비교 완료: ' + totalChanges + '개 테이블 변경' : '비교 완료: 변경사항 없음',
            totalChanges > 0 ? 'warning' : 'success'
          );
        } else {
          self.showSnackbar('비교 실행 실패: ' + (data && data.resultMessage ? data.resultMessage : ''), 'error');
        }
      }).catch(function() {
        self.comparing = false;
        self.showSnackbar('서버 오류가 발생했습니다.', 'error');
      });
    },
    resetResult: function() {
      this.hasResult = false;
      this.tables = [];
      this.summary = { totalTables: 0, sameTables: 0, modifiedTables: 0, addedTables: 0, deletedTables: 0 };
      this.selectedTable = null;
      this.changedOnly = false;
      this.tableFilter = 'ALL';
    },
    selectTable: function(tbl) {
      this.selectedTable = tbl;
      this.changedOnly = false;
    },
    tableStatusColor: function(status) {
      var map = { SAME: 'green', MODIFIED: 'orange', ADDED: '#4CAF50', DELETED: 'red' };
      return map[status] || 'grey';
    },
    tableStatusIcon: function(status) {
      var map = { SAME: 'mdi-check-circle', MODIFIED: 'mdi-pencil-circle', ADDED: 'mdi-plus-circle', DELETED: 'mdi-minus-circle' };
      return map[status] || 'mdi-help-circle';
    },
    tableStatusLabel: function(status) {
      var map = { SAME: '동일', MODIFIED: '변경', ADDED: '추가', DELETED: '삭제' };
      return map[status] || status;
    },
    columnRowClass: function(item) {
      var map = { MODIFIED: 'row-modified', ADDED: 'row-added', DELETED: 'row-deleted' };
      return map[item.status] || '';
    },
    showSnackbar: function(msg, color) {
      this.snackbarMsg = msg;
      this.snackbarColor = color || 'info';
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
.stat-card {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px !important;
}
.stat-icon-wrap {
  width: 40px; height: 40px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
}
.stat-content { flex: 1; }
.stat-value { font-size: 1.2rem; font-weight: 700; color: #263238; }
.stat-label { font-size: .7rem; color: #90A4AE; }
</style>

<style>
.schema-compare-table .row-modified {
  background-color: #FFF8E1 !important;
}
.schema-compare-table .row-added {
  background-color: #E8F5E9 !important;
}
.schema-compare-table .row-deleted {
  background-color: #FFEBEE !important;
  text-decoration: line-through;
  color: #999;
}
</style>
