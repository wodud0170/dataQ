<template>
  <v-main>
    <!-- 110 6.10-33 — 50건씩 보이면 목록이 화면보다 길어진다. height 로 고정하면 흰 바탕이 목록 중간에서 끊겨 min-height 로 -->
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; min-height:100%;">
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

      <!-- 110 6.10-33 — 서버는 최근 logLimit 건까지만 준다. 다 찼으면 조건으로 좁히라고 알린다 -->
      <div v-if="items.length >= logLimit" id="schLogLimitNote" class="sch-limit-note">
        최근 {{ logLimit }}건까지 보입니다 — 스케줄·기간으로 좁혀 조회하세요
      </div>
      <v-data-table :headers="headers" :items="items" dense hide-default-footer
        :page.sync="page" :items-per-page="itemsPerPage" class="elevation-0" :loading="loading"
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
      <div id="schLogPager" class="d-flex align-center px-4 pt-2 pb-2">
        <v-spacer />
        <v-pagination v-if="pageCount > 1" v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10" />
        <v-spacer />
        <v-select v-model="itemsPerPage" :items="[20,50,100]" dense outlined hide-details
          style="max-width:100px; font-size:.75rem;" suffix="건" />
      </div>
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
        <!-- 104번 WP9-67 — 준수 현황 스냅샷·미사용 표준 결과 -->
        <v-card v-if="detail.batchRunId" id="schLogBatch" outlined class="pa-3 mt-3">
          <div style="font-size:.75rem; color:#546E7A;">배치 결과</div>
          <template v-if="batchRun">
            <div style="font-size:.85rem;">
              <v-chip x-small :color="statusColor(batchRun.status)" text-color="white" class="mr-1">{{ batchRun.status }}</v-chip>
              {{ batchRun.summary || '' }}
            </div>
            <div style="font-size:.75rem; color:#78909C;" class="mt-1">
              {{ batchRun.runTp === 'UNUSED_STD' ? '사전: ' + (batchRun.dictNm || batchRun.dictId || '-') + ' — 표준 사전 > 미사용 표준의 "예약 실행 결과" 에서 목록을 봅니다'
                : '모델: ' + (batchRun.dmNm || '사용 중 모델 전부') + ' — 데이터 모델 > 시스템별 현황의 추이에 쓰입니다' }}
            </div>
          </template>
          <code v-else style="font-size:.7rem;">{{ detail.batchRunId }}</code>
        </v-card>
        <v-card v-if="detail.govMetaSendId" outlined class="pa-3 mt-3">
          <div style="font-size:.75rem; color:#546E7A;">연결된 범정부 메타 생성 이력 ([범정부 메타 > 생성·전송] 이력에서 검증 결과를 봅니다)</div>
          <code style="font-size:.7rem;">{{ detail.govMetaSendId }}</code>
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
      logLimit: 200,       // 110 6.10-33 — 서버에 넘기는 조회 상한
      page: 1,
      itemsPerPage: 50,
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
      batchRun: null,   // 104번 WP9-67 — 선택한 실행의 배치 결과
    };
  },
  computed: {
    pageCount() {
      return Math.ceil(this.items.length / this.itemsPerPage);
    },
  },
  watch: {
    // 건수 변경으로 페이지가 줄면 마지막 페이지로
    pageCount(n) { if (this.page > n) this.page = Math.max(1, n); },
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
      const p = { limit: this.logLimit };
      if (this.filter.scheduleId) p.scheduleId = this.filter.scheduleId;
      if (this.filter.execStatus) p.execStatus = this.filter.execStatus;
      if (this.filter.fromDt)     p.fromDt     = this.filter.fromDt + ' 00:00:00';
      if (this.filter.toDt)       p.toDt       = this.filter.toDt   + ' 23:59:59';
      axios.get(this.$APIURL.base + 'api/diag/schedule/logs', { params: p })
        .then(res => { this.items = res.data || []; this.page = 1; })
        .finally(() => { this.loading = false; });
    },
    resetFilter() {
      this.filter = { scheduleId: null, execStatus: null, fromDt: '', toDt: '' };
      this.loadList();
    },
    openDetail(row) {
      this.detail = row;
      this.drawerOpen = true;
      // 104번 WP9-67 — 배치 결과 (상태·요약)
      this.batchRun = null;
      if (row.batchRunId) {
        axios.post(this.$APIURL.base + "api/batchRun/get", { runId: row.batchRunId }).then(r => { this.batchRun = r.data || null; }).catch(() => {});
      }
    },
    diagTypeLabel(t) {
      return { STANDARD: '표준화', STRUCT: '구조변경', BOTH: '전체', GOV_META: '범정부 메타', COMPLIANCE_SNAPSHOT: '준수 현황 스냅샷', UNUSED_STD: '미사용 표준' }[t] || t || '-';
    },
    diagTypeColor(t) {
      return { STANDARD: 'blue', STRUCT: 'deep-purple', BOTH: 'teal', GOV_META: 'brown', COMPLIANCE_SNAPSHOT: 'indigo', UNUSED_STD: 'blue-grey' }[t] || 'grey';
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

<style scoped>
/* 110 6.10-33 — DSChangeHistory 의 상한 안내와 같은 모양 */
.sch-limit-note {
  font-size: .8rem;
  color: #E65100;
  background: #FFF3E0;
  padding: 6px 12px;
}
</style>
