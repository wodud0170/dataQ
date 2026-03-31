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
        style="width:280px; flex-grow:0;"
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

      <v-btn small color="primary" :disabled="!selectedModel || !selectedClctId" :loading="executing" @click="executeStructDiag">
        <v-icon small left>mdi-database-sync</v-icon>
        구조 진단 실행
      </v-btn>
    </v-sheet>

    <!-- 진행 표시 -->
    <v-sheet v-if="executing" class="pa-4 mb-2 text-center" style="border:1px solid #E8EAF6; border-radius:4px;">
      <v-progress-circular indeterminate color="primary" size="32" class="mr-3" />
      <span>{{ stepMessage }}</span>
    </v-sheet>

    <!-- 스탯 카드 4개 -->
    <v-row v-if="hasResult" dense class="mb-2">
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8EAF6;">
            <v-icon color="#3F51B5">mdi-table</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalTables }}</div>
            <div class="stat-label">전체 테이블</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E3F2FD;">
            <v-icon color="#1E88E5">mdi-view-column</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalColumns }}</div>
            <div class="stat-label">전체 컬럼</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" :style="{ background: stats.totalChanges > 0 ? '#FFF3E0' : '#E8F5E9' }">
            <v-icon :color="stats.totalChanges > 0 ? '#FF9800' : '#4CAF50'">
              {{ stats.totalChanges > 0 ? 'mdi-alert-circle' : 'mdi-check-circle' }}
            </v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalChanges }}</div>
            <div class="stat-label">변경 항목</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#F3E5F5;">
            <v-icon color="#7B1FA2">mdi-information</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value" style="font-size:.85rem;">{{ isFirstDiag ? '최초 진단' : '이전 수집 대비' }}</div>
            <div class="stat-label">비교 기준</div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- 변경 요약 칩 -->
    <div v-if="hasResult && stats.totalChanges > 0" class="d-flex align-center mb-2" style="gap:12px;">
      <v-chip small color="green" text-color="white">
        <v-icon x-small left>mdi-plus</v-icon>추가: 테이블 {{ summary.addedTables }}, 컬럼 {{ summary.addedColumns }}
      </v-chip>
      <v-chip small color="orange" text-color="white">
        <v-icon x-small left>mdi-pencil</v-icon>변경: 컬럼 {{ summary.modifiedColumns }}
      </v-chip>
      <v-chip small color="red" text-color="white">
        <v-icon x-small left>mdi-minus</v-icon>삭제: 테이블 {{ summary.deletedTables }}, 컬럼 {{ summary.deletedColumns }}
      </v-chip>
    </div>

    <!-- 변경 0건 -->
    <v-alert v-if="hasResult && stats.totalChanges === 0" type="success" dense outlined class="mb-2">
      이전 수집 대비 변경사항이 없습니다. 현재 스키마와 동일합니다.
    </v-alert>

    <!-- 변경사항 테이블 -->
    <v-sheet v-if="hasResult && stats.totalChanges > 0" style="flex:1; border:1px solid #E8EAF6; border-radius:4px; overflow:auto;">
      <!-- 필터 칩 -->
      <div class="d-flex align-center pa-2" style="gap:6px; border-bottom:1px solid #E8EAF6;">
        <v-chip small :color="changeTypeFilter === 'ALL' ? 'indigo' : ''" :outlined="changeTypeFilter !== 'ALL'"
          @click="changeTypeFilter = 'ALL'">전체 ({{ changeList.length }})</v-chip>
        <v-chip small :color="changeTypeFilter === 'ADDED' ? 'green' : ''" :outlined="changeTypeFilter !== 'ADDED'"
          @click="changeTypeFilter = 'ADDED'">추가 ({{ countByType('ADDED') }})</v-chip>
        <v-chip small :color="changeTypeFilter === 'MODIFIED' ? 'orange' : ''" :outlined="changeTypeFilter !== 'MODIFIED'"
          @click="changeTypeFilter = 'MODIFIED'">변경 ({{ countByType('MODIFIED') }})</v-chip>
        <v-chip small :color="changeTypeFilter === 'DELETED' ? 'red' : ''" :outlined="changeTypeFilter !== 'DELETED'"
          @click="changeTypeFilter = 'DELETED'">삭제 ({{ countByType('DELETED') }})</v-chip>
      </div>
      <v-data-table
        :headers="changeHeaders"
        :items="filteredChanges"
        :items-per-page="itemsPerPage"
        :page.sync="page"
        hide-default-footer dense class="elevation-0"
      >
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
    <v-sheet v-if="!hasResult && !executing" style="flex:1; display:flex; align-items:center; justify-content:center; border:1px solid #E8EAF6; border-radius:4px;">
      <div class="text-center grey--text">
        <v-icon large color="grey lighten-1" class="mb-2">mdi-database-sync</v-icon>
        <div>데이터모델을 선택한 후 [구조 진단 실행] 버튼을 클릭하세요.</div>
        <div class="mt-1 text-caption">DBMS를 재수집하여 이전 수집 결과와 비교합니다.</div>
      </div>
    </v-sheet>

    <!-- 이전 진단 이력 -->
    <v-sheet class="mt-2 pa-0" style="border:1px solid #E8EAF6; border-radius:4px;">
      <div class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6;">
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
        dense class="elevation-0"
        @click:row="onHistoryClick"
        style="cursor:pointer;"
      >
        <template v-slot:item.status="{ item }">
          <v-chip x-small :color="statusColor(item.status)" text-color="white">{{ statusLabel(item.status) }}</v-chip>
        </template>
        <template v-slot:item.changeCnt="{ item }">
          <v-chip x-small :color="item.changeCnt > 0 ? 'orange' : 'green'" text-color="white">{{ item.changeCnt }}건</v-chip>
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
  props: ['isMobile'],
  data() {
    return {
      dataModelList: [],
      selectedModel: null,
      selectedModelData: null,
      clctList: [],
      selectedClctId: null,
      lastClctDt: '',
      executing: false,
      stepMessage: '',
      currentDiagId: null,

      hasResult: false,
      isFirstDiag: false,
      stats: { totalTables: 0, totalColumns: 0, totalChanges: 0 },
      summary: { addedTables: 0, addedColumns: 0, modifiedColumns: 0, deletedTables: 0, deletedColumns: 0 },
      changeList: [],
      changeTypeFilter: 'ALL',

      page: 1,
      itemsPerPage: 20,
      changeHeaders: [
        { text: '테이블명',       value: 'tableNm',     width: '180px' },
        { text: '컬럼명',         value: 'columnNm',    width: '180px' },
        { text: '변경유형',       value: 'changeType',  width: '110px' },
        { text: '이전 타입/길이', value: 'prevType',    width: '150px', sortable: false },
        { text: '현재 타입/길이', value: 'currType',    width: '150px', sortable: false },
      ],

      historyList: [],
      historyHeaders: [
        { text: '진단일시',   value: 'diagDt',      width: '180px' },
        { text: '데이터모델', value: 'dataModelNm',  width: '180px' },
        { text: '상태',       value: 'status',      width: '90px' },
        { text: '변경건수',   value: 'changeCnt',   width: '100px' },
        { text: '실행자',     value: 'cretUserId',   width: '120px' },
      ],

      snackbar: false,
      snackbarMsg: '',
      snackbarColor: 'info',
    };
  },
  computed: {
    filteredChanges() {
      if (this.changeTypeFilter === 'ALL') return this.changeList;
      return this.changeList.filter(function(c) { return c.changeType === this.changeTypeFilter; }.bind(this));
    },
    pageCount() {
      return Math.ceil(this.filteredChanges.length / this.itemsPerPage);
    },
  },
  mounted() {
    this.loadDataModelList();
    this.loadHistory();
  },
  beforeDestroy() {
    this.clearTimers();
  },
  methods: {
    loadDataModelList: function() {
      var self = this;
      axios.post(self.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(function(res) {
        self.dataModelList = (res.data || []).map(function(item) {
          return {
            dataModelId: item.dataModelId,
            dataModelNm: item.dataModelNm,
            dataModelDsId: item.dataModelDsId,
            dataModelDsNm: item.dataModelDsNm,
            dataModelSysCd: item.dataModelSysCd,
            dataModelSysNm: item.dataModelSysNm,
            ver: item.ver,
          };
        });
      });
    },

    onModelChange: function(dmId) {
      this.lastClctDt = '';
      this.selectedModelData = null;
      this.clctList = [];
      this.selectedClctId = null;
      this.resetResult();
      if (!dmId) return;

      // 선택한 모델 데이터 찾기
      var self = this;
      for (var i = 0; i < self.dataModelList.length; i++) {
        if (self.dataModelList[i].dataModelId === dmId) {
          self.selectedModelData = self.dataModelList[i];
          break;
        }
      }

      // 수집 이력 조회
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
          self.lastClctDt = sorted[0].clctEndDt;
        }
      });
    },

    /**
     * 구조 진단 실행:
     * 1. /api/std/structDiag/execute 호출 → q-executor가 실제 DBMS 접속 + 수집 스냅샷과 diff
     * 2. 완료 대기 (폴링: /api/std/structDiag/status/{diagId})
     * 3. 결과 표시
     */
    executeStructDiag: function() {
      if (!this.selectedModel) return;
      var self = this;

      // 수집 이력이 없으면 수집 화면으로 안내
      if (!self.lastClctDt) {
        self.$swal.fire({
          title: '수집 이력 없음',
          text: '이 데이터모델은 아직 수집된 적이 없습니다. 먼저 데이터 모델 수집을 진행해주세요.',
          icon: 'warning',
          confirmButtonText: '수집 화면으로 이동',
          showCancelButton: true,
          cancelButtonText: '취소'
        }).then(function(result) {
          if (result.value) {
            self.$emit('addTabItem', '데이터 모델 현황', 'datamodelStatus');
          }
        });
        return;
      }

      self.executing = true;
      self.resetResult();
      self.stepMessage = '실제 DBMS에 접속하여 스키마를 비교하고 있습니다...';

      // 바로 구조 진단 실행 (executor가 DBMS 접속 + diff 수행)
      var execBody = { dataModelId: self.selectedModel };
      if (self.selectedClctId) {
        execBody.clctId = self.selectedClctId;
      }
      axios.post(self.$APIURL.base + 'api/std/structDiag/execute', execBody).then(function(res) {
        var data = res.data;
        if (data && data.resultCode === 200 && data.contents) {
          self._diagId = data.contents;
          self.stepMessage = '분석 중...';
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

    pollDiag: function() {
      var self = this;
      var diagId = self._diagId;
      var self = this;
      var pollCount = 0;
      var maxPoll = 120; // 최대 120회 (2초 간격 = 4분)

      var poll = function() {
        pollCount++;
        if (pollCount > maxPoll) {
          self.executing = false;
          self.showSnackbar('분석 시간 초과. 이력에서 결과를 확인해주세요.', 'warning');
          self.loadHistory();
          return;
        }

        axios.get(self.$APIURL.base + 'api/std/structDiag/status/' + diagId).then(function(res) {
          var data = res.data;
          if (!data || data.status === 'NONE') {
            self._diagTimer = setTimeout(poll, 2000);
            return;
          }

          if (data.status === 'DONE') {
            self.executing = false;
            self.applyResultFromHistory(data);
            // 상세 목록 로드
            axios.get(self.$APIURL.base + 'api/std/structDiag/result/' + diagId).then(function(detailRes) {
              self.changeList = (detailRes.data && detailRes.data.details) || [];
              self.hasResult = true;
              self.changeTypeFilter = 'ALL';
              self.page = 1;
            });
            var totalChanges = (data.addedTables || 0) + (data.addedColumns || 0) + (data.modifiedColumns || 0) + (data.deletedTables || 0) + (data.deletedColumns || 0);
            var msg = totalChanges > 0
              ? '구조 진단 완료: ' + totalChanges + '건 변경사항 발견'
              : '구조 진단 완료: 변경사항 없음';
            self.showSnackbar(msg, totalChanges > 0 ? 'warning' : 'success');
            self.loadHistory();
          } else if (data.status === 'ERROR') {
            self.executing = false;
            self.showSnackbar('구조 진단 중 오류가 발생했습니다.', 'error');
            self.loadHistory();
          } else if (data.status === 'RUNNING' || data.status === 'READY') {
            self._diagTimer = setTimeout(poll, 2000);
          } else {
            self._diagTimer = setTimeout(poll, 2000);
          }
        }).catch(function() {
          self._diagTimer = setTimeout(poll, 2000);
        });
      };

      self._diagTimer = setTimeout(poll, 1000);
    },

    applyResultFromHistory: function(h) {
      this.stats.totalTables = h.totalTables || 0;
      this.stats.totalColumns = h.totalColumns || 0;
      this.stats.totalChanges = (h.addedTables || 0) + (h.addedColumns || 0) + (h.modifiedColumns || 0) + (h.deletedTables || 0) + (h.deletedColumns || 0);
      this.isFirstDiag = false;

      this.summary.addedTables = h.addedTables || 0;
      this.summary.addedColumns = h.addedColumns || 0;
      this.summary.modifiedColumns = h.modifiedColumns || 0;
      this.summary.deletedTables = h.deletedTables || 0;
      this.summary.deletedColumns = h.deletedColumns || 0;
    },

    resetResult: function() {
      this.hasResult = false;
      this.isFirstDiag = false;
      this.changeList = [];
      this.stats = { totalTables: 0, totalColumns: 0, totalChanges: 0 };
      this.summary = { addedTables: 0, addedColumns: 0, modifiedColumns: 0, deletedTables: 0, deletedColumns: 0 };
      this.changeTypeFilter = 'ALL';
      this.page = 1;
      this.currentDiagId = null;
    },

    clearTimers: function() {
      if (this._diagTimer) { clearTimeout(this._diagTimer); this._diagTimer = null; }
    },

    loadHistory: function() {
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/history').then(function(res) {
        self.historyList = (res.data || []).map(function(h) {
          return {
            diagId: h.diagId,
            diagDt: h.diagDt,
            dataModelNm: h.dataModelNm || '-',
            status: h.status || 'DONE',
            changeCnt: (h.addedTables || 0) + (h.addedColumns || 0) + (h.modifiedColumns || 0) + (h.deletedTables || 0) + (h.deletedColumns || 0),
            cretUserId: h.cretUserId,
          };
        });
      }).catch(function() {});
    },

    onHistoryClick: function(item) {
      if (!item.diagId) return;
      if (item.status !== 'DONE') {
        this.showSnackbar('완료된 진단만 결과를 볼 수 있습니다.', 'warning');
        return;
      }
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/structDiag/result/' + item.diagId).then(function(res) {
        var data = res.data;
        if (data && data.history) {
          self.applyResultFromHistory(data.history);
          self.changeList = data.details || [];
          self.hasResult = true;
          self.changeTypeFilter = 'ALL';
          self.page = 1;
          self.showSnackbar(item.diagDt + ' 진단 결과를 불러왔습니다.', 'info');
        }
      }).catch(function() {
        self.showSnackbar('결과 조회 실패', 'error');
      });
    },

    countByType: function(type) {
      return this.changeList.filter(function(c) { return c.changeType === type; }).length;
    },
    changeTypeColor: function(type) {
      var map = { ADDED: 'green', MODIFIED: 'orange', DELETED: 'red' };
      return map[type] || 'grey';
    },
    statusColor: function(status) {
      var map = { READY: 'grey', RUNNING: 'blue', DONE: 'green', ERROR: 'red' };
      return map[status] || 'grey';
    },
    statusLabel: function(status) {
      var map = { READY: '대기', RUNNING: '진행중', DONE: '완료', ERROR: '오류' };
      return map[status] || status;
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
  padding: 12px 16px;
  border-radius: 8px !important;
}
.stat-icon-wrap {
  width: 44px; height: 44px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  margin-right: 14px;
  flex-shrink: 0;
}
.stat-content { flex: 1; }
.stat-value { font-size: 1.3rem; font-weight: 700; color: #263238; }
.stat-label { font-size: .75rem; color: #90A4AE; }
</style>
