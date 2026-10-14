<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-file-send-outline</v-icon>&nbsp;&nbsp;생성·전송</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="loadHist"><v-icon small left>mdi-refresh</v-icon>새로고침</v-btn>
      </v-row>

      <div class="gm-guide">
        지금은 [검증] 만 합니다 — 필수 항목, 코드 매핑, 한글 컬럼명 규칙, 공개 사유·근거, 개인정보↔암호화(경고), FK 참조 대상(경고).
        파일 생성·전송은 연계 규격을 받은 뒤 붙습니다. 예약 실행은 [스케줄 관리] 에서 유형 "범정부 메타" 로 모델마다 등록합니다.
      </div>

      <v-row no-gutters align="center" style="gap:8px;" class="mb-3">
        <v-radio-group v-model="mode" row dense hide-details class="mt-0">
          <v-radio label="전송대상 정보시스템 전체" value="ALL" />
          <v-radio label="시스템 (하위 포함)" value="SYS" />
          <v-radio label="데이터 모델" value="DM" />
        </v-radio-group>
        <v-select v-if="mode === 'SYS'" id="govSendSys" v-model="sysCd" :items="sysOptions" item-text="text" item-value="value"
          label="시스템" dense outlined hide-details style="max-width:240px;" />
        <v-autocomplete v-if="mode === 'DM'" id="govSendModel" v-model="dmId" :items="modelOptions" item-text="dmNm" item-value="dmId"
          label="데이터 모델" dense outlined hide-details style="max-width:280px;" />
        <v-btn v-if="canManage" id="govSendValidate" color="primary" :loading="running" v-on:click="validate">검증</v-btn>
        <v-btn disabled title="연계 규격을 받은 뒤 붙습니다">생성</v-btn>
        <v-btn disabled title="연계 규격을 받은 뒤 붙습니다">전송</v-btn>
      </v-row>

      <div class="gm-sub">이력</div>
      <v-data-table id="govSendHist" :headers="histHeaders" :items="hist" item-key="sendId" dense :items-per-page="10"
        v-on:click:row="openErrs" :item-class="r => (cur && r.sendId === cur.sendId) ? 'gm-sel' : ''">
        <template #item.startDt="{ item }">{{ fmtDt(item.startDt) }}</template>
        <template #item.target="{ item }">{{ item.dmNm || item.sysNm || '전체' }}</template>
        <template #item.triggerType="{ item }">{{ item.triggerType === 'SCHEDULE' ? '예약' : '화면' }}</template>
        <template #item.status="{ item }">
          <v-chip x-small :color="statusColor(item.status)" dark>{{ statusNm(item.status) }}</v-chip>
        </template>
        <template #item.cnt="{ item }">시스템 {{ item.sysCnt }} · DB {{ item.dbCnt }} · 테이블 {{ item.tblCnt }} · 컬럼 {{ item.colCnt }}</template>
        <template #item.errCnt="{ item }">
          <span :class="item.errCnt ? 'error--text' : ''">{{ item.errCnt }}</span> / <span :class="item.warnCnt ? 'warning--text' : ''">{{ item.warnCnt }}</span>
        </template>
      </v-data-table>

      <template v-if="cur">
        <div class="gm-sub mt-3">검증 결과 — {{ fmtDt(cur.startDt) }} <span class="gm-note">{{ cur.sendMsg }}</span></div>
        <v-row no-gutters style="gap:8px;" class="mb-2">
          <v-select v-model="errSev" :items="sevOptions" label="구분" dense outlined hide-details style="max-width:140px;" />
          <v-select v-model="errLvl" :items="lvlOptions" label="수준" dense outlined hide-details style="max-width:140px;" />
        </v-row>
        <v-data-table id="govSendErrs" :headers="errHeaders" :items="filteredErrs" item-key="errSeq" dense :items-per-page="20"
          v-on:click:row="goItems">
          <template #item.sev="{ item }">
            <v-chip x-small :color="item.sev === 'ERROR' ? 'error' : 'warning'" dark>{{ item.sev === 'ERROR' ? '오류' : '경고' }}</v-chip>
          </template>
          <template #item.errLvl="{ item }">{{ lvlNm(item.errLvl) }}</template>
          <template #item.where="{ item }">
            <span>{{ [item.sysNm, item.dsn, item.objNm ? (item.objOwner ? item.objOwner + '.' : '') + item.objNm : null, item.attrNm].filter(Boolean).join(' › ') }}</span>
          </template>
          <template #no-data>오류·경고가 없습니다.</template>
        </v-data-table>
        <div class="gm-note">테이블·컬럼 행을 누르면 [테이블·컬럼] 화면이 그 테이블로 열립니다.</div>
      </template>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
const LVL_NM = { SYS: '정보시스템', DB: 'DB', TBL: '테이블', COL: '컬럼' };
export default {
  name: 'DSGovMetaSend',
  props: ['isMobile'],
  computed: {
    canManage() { return this.$can('GOV_META_ADMIN'); },
    filteredErrs() {
      return this.errs.filter(e => (!this.errSev || e.sev === this.errSev) && (!this.errLvl || e.errLvl === this.errLvl));
    },
  },
  data: () => ({
    mode: 'ALL',
    sysCd: null,
    dmId: null,
    sysOptions: [],
    modelOptions: [],
    running: false,
    hist: [],
    cur: null,
    errs: [],
    errSev: '',
    errLvl: '',
    sevOptions: [{ text: '전체', value: '' }, { text: '오류', value: 'ERROR' }, { text: '경고', value: 'WARN' }],
    lvlOptions: [{ text: '전체', value: '' }, { text: '정보시스템', value: 'SYS' }, { text: 'DB', value: 'DB' }, { text: '테이블', value: 'TBL' }, { text: '컬럼', value: 'COL' }],
    histHeaders: [
      { text: '시작', value: 'startDt', width: '140px' },
      { text: '대상', value: 'target' },
      { text: '실행', value: 'triggerType', width: '70px' },
      { text: '수행자', value: 'cretUserId', width: '100px' },
      { text: '상태', value: 'status', width: '90px' },
      { text: '건수', value: 'cnt', sortable: false },
      { text: '오류 / 경고', value: 'errCnt', width: '100px' },
    ],
    errHeaders: [
      { text: '구분', value: 'sev', width: '70px' },
      { text: '수준', value: 'errLvl', width: '90px' },
      { text: '위치', value: 'where', sortable: false },
      { text: '항목', value: 'itemNm', width: '150px' },
      { text: '내용', value: 'errMsg' },
    ],
  }),
  mounted() {
    axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList').then(res => {
      this.sysOptions = (res.data || []).map(s => ({ text: '  '.repeat(Math.max(0, (s.level || 1) - 1)) + s.sysNm, value: s.sysCd }));
    });
    axios.get(this.$APIURL.base + 'api/privCol/models').then(res => {
      this.modelOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
    });
    this.loadHist();
  },
  activated() { this.loadHist(); },
  methods: {
    fmtDt(s) { return s && s.length >= 12 ? `${s.substr(0, 4)}-${s.substr(4, 2)}-${s.substr(6, 2)} ${s.substr(8, 2)}:${s.substr(10, 2)}` : s; },
    lvlNm(l) { return LVL_NM[l] || l; },
    statusNm(s) { return { RUNNING: '실행 중', DONE: '통과', ERROR: '오류 있음', READY: '대기' }[s] || s; },
    statusColor(s) { return s === 'DONE' ? 'success' : s === 'ERROR' ? 'error' : 'grey'; },
    loadHist() {
      axios.post(this.$APIURL.base + 'api/govMeta/send/list', {}).then(res => {
        this.hist = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
        if (this.cur) {
          const again = this.hist.find(h => h.sendId === this.cur.sendId);
          if (again) this.cur = again;
        }
      });
    },
    validate() {
      if (this.mode === 'SYS' && !this.sysCd) { this.$swal.fire({ title: '시스템을 골라 주세요', icon: 'warning' }); return; }
      if (this.mode === 'DM' && !this.dmId) { this.$swal.fire({ title: '데이터 모델을 골라 주세요', icon: 'warning' }); return; }
      const body = { sysCd: this.mode === 'SYS' ? this.sysCd : null, dmId: this.mode === 'DM' ? this.dmId : null };
      this.running = true;
      axios.post(this.$APIURL.base + 'api/govMeta/send/validate', body).then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.$swal.fire({ title: (res.data && res.data.resultMessage) || '검증 실패', icon: 'error' });
          return;
        }
        const r = JSON.parse(res.data.contents);
        this.$swal.fire({
          title: r.status === 'DONE' ? '검증을 통과했습니다' : `오류 ${r.errCnt}건`,
          text: `경고 ${r.warnCnt}건 · 테이블 ${r.tblCnt} · 컬럼 ${r.colCnt}`,
          icon: r.status === 'DONE' ? 'success' : 'warning',
        });
        this.loadHist();
        this.openErrs({ sendId: r.sendId });
      }).finally(() => { this.running = false; });
    },
    openErrs(row) {
      this.cur = this.hist.find(h => h.sendId === row.sendId) || row;
      this.errSev = '';
      this.errLvl = '';
      axios.get(this.$APIURL.base + 'api/govMeta/send/errors', { params: { sendId: row.sendId } }).then(res => {
        this.errs = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
      });
    },
    // 테이블·컬럼 오류 → 테이블·컬럼 화면 (NdNav 가 openGovMetaItems 를 받아 탭을 연다)
    goItems(e) {
      if (!e.objNm) return;
      eventBus.pendingGovMetaTarget = { dmId: e.dmId || null, objNm: e.objNm };
      eventBus.$emit('openGovMetaItems');
    },
  },
};
</script>
<style scoped>
.gm-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.gm-sub { font-size: .9rem; font-weight: 600; color: #37474F; margin: 4px 0 8px; }
.gm-note { font-size: .78rem; color: #78909C; font-weight: normal; }
.v-data-table >>> .gm-sel { background: #E3F2FD; }
</style>
