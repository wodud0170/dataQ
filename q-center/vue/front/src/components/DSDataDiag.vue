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

      <!-- Job 상태 표시 -->
      <v-chip v-if="currentJob" small :color="statusColor(currentJob.status)" text-color="white" class="mr-2">
        {{ statusLabel(currentJob.status) }}
        <span v-if="currentJob.status === 'RUNNING'" class="ml-1">
          ({{ currentJob.processCnt }}/{{ currentJob.totalCnt }})
        </span>
      </v-chip>

      <v-btn small color="primary" :disabled="!selectedClctId || isRunning" @click="startDiag">
        진단 시작
      </v-btn>
      <v-btn small color="error" :disabled="!isRunning" @click="stopDiag">
        진단 중지
      </v-btn>
    </v-sheet>

    <!-- 진행 상황 -->
    <v-sheet v-if="currentJob && currentJob.status === 'RUNNING'" class="mb-2 pa-2" style="border:1px solid #e0e0e0; border-radius:4px;">
      <div class="d-flex align-center" style="gap:8px;">
        <span class="filterLabel">진행률</span>
        <v-progress-linear
          :value="progressValue"
          color="primary"
          height="20"
          rounded
          style="flex:1;"
        >
          <template v-slot:default>
            <span style="font-size:.75rem;">{{ currentJob.processCnt }} / {{ currentJob.totalCnt }} 컬럼</span>
          </template>
        </v-progress-linear>
        <span class="filterLabel">이슈 {{ currentJob.resultCnt }}건</span>
      </div>
    </v-sheet>

    <!-- 최근 진단 이력 -->
    <v-sheet style="flex:1; overflow:auto; border:1px solid #e0e0e0; border-radius:4px;">
      <v-data-table
        :headers="jobHeaders"
        :items="jobList"
        :page.sync="page"
        :items-per-page="itemsPerPage"
        hide-default-footer
        dense
        class="elevation-0"
      >
        <template v-slot:item.status="{ item }">
          <v-chip x-small :color="statusColor(item.status)" text-color="white">
            {{ statusLabel(item.status) }}
          </v-chip>
        </template>
        <template v-slot:item.progress="{ item }">
          <span v-if="item.totalCnt > 0">{{ item.processCnt }} / {{ item.totalCnt }}</span>
          <span v-else>-</span>
        </template>
        <!-- 결과 보기 버튼: 완료(DONE) 상태인 Job만 표시, 클릭 시 진단 결과 탭으로 이동 -->
        <template v-slot:item.actions="{ item }">
          <v-btn v-if="item.status === 'DONE'" x-small color="teal" dark @click="goToResult(item)"
            class="btn-sm">
            결과보기
          </v-btn>
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

    <v-snackbar v-model="snackbar" :color="snackbarColor" top right :timeout="3000">
      {{ snackbarMsg }}
    </v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSDataDiag',
  data() {
    return {
      dataModelList: [],
      selectedModel: null,
      clctList: [],
      selectedClctId: null,
      jobList: [],
      currentJob: null,
      pollTimer: null,
      page: 1,
      itemsPerPage: 20,
      snackbar: false,
      snackbarMsg: '',
      snackbarColor: 'info',
      jobHeaders: [
        { text: '데이터모델명',  value: 'dataModelNm',  width: '180px' },
        { text: '수집일시',      value: 'clctDt',       width: '160px' },
        { text: '상태',          value: 'status',       width: '90px'  },
        { text: '진행(컬럼)',    value: 'progress',     width: '110px' },
        { text: '이슈 건수',     value: 'resultCnt',    width: '90px'  },
        { text: '시작일시',      value: 'startDt',      width: '160px' },
        { text: '완료일시',      value: 'endDt',        width: '160px' },
        { text: '실행자',        value: 'cretUserId',   width: '100px' },
        { text: '결과 보기',             value: 'actions',      width: '55px', sortable: false, align: 'center' },
      ],
    };
  },
  computed: {
    isRunning() {
      return this.currentJob && this.currentJob.status === 'RUNNING';
    },
    progressValue() {
      if (!this.currentJob || !this.currentJob.totalCnt) return 0;
      return Math.round((this.currentJob.processCnt / this.currentJob.totalCnt) * 100);
    },
    pageCount() {
      return Math.ceil(this.jobList.length / this.itemsPerPage);
    },
  },
  mounted() {
    this.loadDataModelList();
    this.loadJobList();
  },
  beforeDestroy() {
    this.stopPoll();
  },
  methods: {
    loadDataModelList() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(res => {
        this.dataModelList = (res.data || []).map(item => ({
          dataModelId: item.dataModelId,
          dataModelNm: item.dataModelNm,
        }));
      });
    },
    onModelChange(dmId) {
      this.clctList = [];
      this.selectedClctId = null;
      if (!dmId) return;
      const _to   = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      const _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';
      axios.post(this.$APIURL.base + 'api/dm/getDataModelClctList', { schId: dmId, from: _from, to: _to }).then(res => {
        const sorted = (res.data || []).slice().sort((a, b) => b.clctEndDt.localeCompare(a.clctEndDt));
        this.clctList = sorted.map((item, idx) => ({
          ...item,
          clctDisplayDt: item.clctEndDt + (idx === 0 ? ' (최신)' : ''),
        }));
        if (this.clctList.length > 0) {
          this.selectedClctId = this.clctList[0].clctId;
        }
      });
    },
    loadJobList() {
      axios.post(this.$APIURL.base + 'api/diag/getDiagJobList').then(res => {
        this.jobList = res.data || [];
        // 실행 중인 Job이 있으면 폴링 시작
        const running = this.jobList.find(j => j.status === 'RUNNING');
        if (running) {
          this.currentJob = running;
          this.startPoll(running.diagJobId);
        }
      });
    },
    startDiag() {
      if (!this.selectedModel || !this.selectedClctId) return;
      const body = {
        clctId: this.selectedClctId,
        dataModelId: this.selectedModel,
      };
      axios.post(this.$APIURL.base + 'api/diag/startDiag', body).then(res => {
        if (res.data && res.data.resultCode === 200) {
          const diagJobId = res.data.contents;
          this.showSnackbar('진단을 시작했습니다.', 'success');
          this.loadJobList();
          this.pollJob(diagJobId);
          this.startPoll(diagJobId);
        } else {
          this.showSnackbar('진단 시작 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.showSnackbar('서버 오류', 'error'));
    },
    stopDiag() {
      if (!this.currentJob) return;
      const body = { diagJobId: this.currentJob.diagJobId };
      axios.post(this.$APIURL.base + 'api/diag/stopDiag', body).then(() => {
        this.showSnackbar('진단 중지를 요청했습니다.', 'warning');
      });
    },
    startPoll(diagJobId) {
      this.stopPoll();
      this.pollTimer = setInterval(() => this.pollJob(diagJobId), 3000);
    },
    stopPoll() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    pollJob(diagJobId) {
      axios.get(this.$APIURL.base + 'api/diag/getDiagJobById', { params: { diagJobId } }).then(res => {
        const job = res.data;
        this.currentJob = job;
        // 목록에서 해당 Job 업데이트
        const idx = this.jobList.findIndex(j => j.diagJobId === diagJobId);
        if (idx >= 0) this.$set(this.jobList, idx, job);
        else this.jobList.unshift(job);

        if (job.status !== 'RUNNING') {
          this.stopPoll();
          if (job.status === 'DONE') {
            this.showSnackbar(`진단 완료 - 이슈 ${job.resultCnt}건 발견`, 'success');
          } else if (job.status === 'STOPPED') {
            this.showSnackbar('진단이 중지되었습니다.', 'warning');
            this.loadJobList();
          }
        }
      });
    },
    statusColor(status) {
      const map = { READY: 'grey', RUNNING: 'blue', DONE: 'green', STOPPED: 'orange', ERROR: 'red' };
      return map[status] || 'grey';
    },
    statusLabel(status) {
      const map = { READY: '대기', RUNNING: '진행중', DONE: '완료', STOPPED: '중지', ERROR: '오류' };
      return map[status] || status;
    },
    /**
     * 결과 보기 버튼 클릭 핸들러
     * - eventBus에 diagJobId를 저장하여 진단 결과 화면에서 참조할 수 있게 함
     * - 'openDiagResult' 이벤트를 발행하여 NdNav에서 진단 결과 탭을 열도록 함
     */
    goToResult(item) {
      eventBus.pendingDiagJobId = item.diagJobId;
      eventBus.$emit('openDiagResult');
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
