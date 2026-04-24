<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <!-- 상단 필터 -->
      <v-sheet class="d-flex align-center pa-2 flex-wrap" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <v-autocomplete v-model="filter.scheduleId" :items="scheduleOptions"
          item-text="scheduleNm" item-value="scheduleId" label="스케줄"
          dense outlined hide-details clearable style="max-width:240px;"></v-autocomplete>
        <v-select v-model="filter.execStatus" :items="statusOptions"
          label="상태" dense outlined hide-details clearable
          style="max-width:160px;"></v-select>
        <v-text-field v-model="filter.fromDt" type="date" label="시작일"
          dense outlined hide-details style="max-width:160px;"></v-text-field>
        <v-text-field v-model="filter.toDt" type="date" label="종료일"
          dense outlined hide-details style="max-width:160px;"></v-text-field>
        <v-btn small class="gradient" @click="loadList">
          <v-icon small left>mdi-magnify</v-icon>조회
        </v-btn>
        <v-btn small text @click="resetFilter">초기화</v-btn>
      </v-sheet>

      <v-data-table :headers="headers" :items="items" dense hide-default-footer
        :items-per-page="100" class="elevation-0" :loading="loading"
        @click:row="openDetail" style="cursor:pointer;">
        <template v-slot:item.execDt="{ item }">
          <span style="font-size:.8rem;">{{ formatDt(item.execDt) }}</span>
        </template>
        <template v-slot:item.diagType="{ item }">
          <v-chip x-small :color="diagTypeColor(item.diagType)" text-color="white">
            {{ diagTypeLabel(item.diagType) }}
          </v-chip>
        </template>
        <template v-slot:item.triggerType="{ item }">
          <v-chip x-small outlined :color="item.triggerType === 'AUTO' ? 'blue' : 'orange'">
            {{ item.triggerType }}
          </v-chip>
        </template>
        <template v-slot:item.execStatus="{ item }">
          <v-chip x-small :color="statusColor(item.execStatus)" text-color="white">
            {{ item.execStatus }}
          </v-chip>
        </template>
        <template v-slot:item.execDurationSec="{ item }">
          <span v-if="item.execDurationSec != null" style="font-size:.8rem;">
            {{ item.execDurationSec }}s
          </span>
          <span v-else style="color:#9E9E9E;">-</span>
        </template>
        <template v-slot:item.errorMsg="{ item }">
          <span v-if="item.execStatus === 'ERROR' || item.execStatus === 'SKIPPED'"
            style="font-size:.75rem; color:#B71C1C;">
            {{ shortErr(item.errorMsg) }}
          </span>
          <span v-else style="color:#9E9E9E;">-</span>
        </template>
        <template #no-data>
          <span class="grey--text">이력이 없습니다.</span>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 상세 드로어 -->
    <v-navigation-drawer v-model="drawerOpen" right fixed width="480" temporary>
      <v-sheet v-if="detail" class="pa-4">
        <div class="d-flex align-center mb-3">
          <v-icon class="mr-1">mdi-history</v-icon>
          <span class="text-subtitle-1 font-weight-bold">실행 이력 상세</span>
          <v-spacer></v-spacer>
          <v-btn icon small @click="drawerOpen = false"><v-icon small>mdi-close</v-icon></v-btn>
        </div>

        <v-card outlined class="pa-3 mb-3">
          <div style="font-size:.8rem; color:#546E7A;">스케줄명 (실행 당시)</div>
          <div class="font-weight-bold">{{ detail.scheduleNmSnapshot || '(삭제됨)' }}</div>
        </v-card>

        <v-row dense>
          <v-col cols="6">
            <div style="font-size:.75rem; color:#546E7A;">진단 유형</div>
            <v-chip x-small :color="diagTypeColor(detail.diagType)" text-color="white">
              {{ diagTypeLabel(detail.diagType) }}
            </v-chip>
          </v-col>
          <v-col cols="6">
            <div style="font-size:.75rem; color:#546E7A;">트리거</div>
            <v-chip x-small outlined :color="detail.triggerType === 'AUTO' ? 'blue' : 'orange'">
              {{ detail.triggerType }}
            </v-chip>
          </v-col>
          <v-col cols="6" class="mt-2">
            <div style="font-size:.75rem; color:#546E7A;">상태</div>
            <v-chip x-small :color="statusColor(detail.execStatus)" text-color="white">
              {{ detail.execStatus }}
            </v-chip>
          </v-col>
          <v-col cols="6" class="mt-2">
            <div style="font-size:.75rem; color:#546E7A;">소요시간</div>
            <span>{{ detail.execDurationSec != null ? detail.execDurationSec + 's' : '-' }}</span>
          </v-col>
          <v-col cols="12" class="mt-2">
            <div style="font-size:.75rem; color:#546E7A;">시작</div>
            <span style="font-size:.85rem;">{{ formatDt(detail.execDt) }}</span>
          </v-col>
          <v-col cols="12" class="mt-2">
            <div style="font-size:.75rem; color:#546E7A;">완료</div>
            <span style="font-size:.85rem;">{{ formatDt(detail.execEndDt) || '-' }}</span>
          </v-col>
        </v-row>

        <v-card v-if="detail.diagJobId" outlined class="pa-3 mt-3">
          <div style="font-size:.75rem; color:#546E7A;">연결된 표준화 진단 Job</div>
          <code style="font-size:.7rem;">{{ detail.diagJobId }}</code>
        </v-card>
        <v-card v-if="detail.structDiagId" outlined class="pa-3 mt-3">
          <div style="font-size:.75rem; color:#546E7A;">연결된 구조변경 진단 ID</div>
          <code style="font-size:.7rem;">{{ detail.structDiagId }}</code>
        </v-card>

        <v-card v-if="detail.errorMsg" outlined class="pa-3 mt-3" color="#FFEBEE">
          <div style="font-size:.75rem; color:#B71C1C; font-weight:bold;">실패 사유</div>
          <div style="font-size:.8rem; color:#B71C1C; white-space:pre-wrap; word-break:break-all;">
            {{ detail.errorMsg }}
          </div>
        </v-card>
      </v-sheet>
    </v-navigation-drawer>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSScheduleLog',
  data() {
    return {
      loading: false,
      items: [],
      scheduleOptions: [],
      filter: { scheduleId: null, execStatus: null, fromDt: '', toDt: '' },
      statusOptions: ['RUNNING', 'DONE', 'ERROR', 'SKIPPED'],
      headers: [
        { text: '실행일시',   value: 'execDt',           sortable: false },
        { text: '스케줄명',   value: 'scheduleNmSnapshot', sortable: false },
        { text: '진단유형',   value: 'diagType',         sortable: false, align: 'center', width: 90 },
        { text: '트리거',     value: 'triggerType',      sortable: false, align: 'center', width: 90 },
        { text: '상태',       value: 'execStatus',       sortable: false, align: 'center', width: 90 },
        { text: '소요(초)',   value: 'execDurationSec',  sortable: false, align: 'right',  width: 80 },
        { text: '에러요약',   value: 'errorMsg',         sortable: false },
      ],
      drawerOpen: false,
      detail: null,
    };
  },
  mounted() {
    this.loadScheduleOptions();
    this.loadList();
  },
  methods: {
    loadScheduleOptions() {
      axios.get(this.$APIURL.base + 'api/diag/schedule/list')
        .then(res => { this.scheduleOptions = res.data || []; });
    },
    loadList() {
      this.loading = true;
      const p = { limit: 200 };
      if (this.filter.scheduleId) p.scheduleId = this.filter.scheduleId;
      if (this.filter.execStatus) p.execStatus = this.filter.execStatus;
      if (this.filter.fromDt)     p.fromDt     = this.filter.fromDt + ' 00:00:00';
      if (this.filter.toDt)       p.toDt       = this.filter.toDt   + ' 23:59:59';
      axios.get(this.$APIURL.base + 'api/diag/schedule/logs', { params: p })
        .then(res => { this.items = res.data || []; })
        .finally(() => { this.loading = false; });
    },
    resetFilter() {
      this.filter = { scheduleId: null, execStatus: null, fromDt: '', toDt: '' };
      this.loadList();
    },
    openDetail(row) {
      this.detail = row;
      this.drawerOpen = true;
    },
    diagTypeLabel(t) {
      return { STANDARD: '표준화', STRUCT: '구조변경', BOTH: '전체' }[t] || t || '-';
    },
    diagTypeColor(t) {
      return { STANDARD: 'blue', STRUCT: 'deep-purple', BOTH: 'teal' }[t] || 'grey';
    },
    statusColor(s) {
      return { DONE: 'green', ERROR: 'red', SKIPPED: 'grey', RUNNING: 'blue' }[s] || 'grey';
    },
    formatDt(s) {
      if (!s) return '';
      return String(s).replace('T', ' ').substring(0, 19);
    },
    shortErr(s) {
      if (!s) return '';
      const one = s.split('\n')[0];
      return one.length > 80 ? one.substring(0, 80) + '...' : one;
    },
  },
};
</script>
