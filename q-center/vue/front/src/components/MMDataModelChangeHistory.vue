<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-history</v-icon>&nbsp;&nbsp;데이터 모델 변경 이력</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="load">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <!-- 필터 -->
      <v-row no-gutters style="gap:8px; margin-bottom:8px;">
        <v-text-field v-model="filter.dmId" label="모델 ID" dense outlined hide-details style="max-width:240px;" />
        <v-text-field v-model="filter.changeUserId" label="사용자 ID" dense outlined hide-details style="max-width:160px;" />
        <v-select v-model="filter.changeTier" :items="tierOptions" label="Tier" dense outlined hide-details style="max-width:140px;" />
        <v-select v-model="filter.aprvStatus" :items="statusOptions" label="상태" dense outlined hide-details style="max-width:160px;" />
        <v-btn small color="primary" outlined v-on:click="load">조회</v-btn>
      </v-row>

      <v-data-table id="dmHistTable" :headers="headers" :items="items" item-key="changeSeq"
        :items-per-page="30" :footer-props="{ 'items-per-page-options': [30, 50, 100, -1] }" dense fixed-header :height="600">
        <template #item.changeType="{ item }">
          <v-chip x-small outlined :title="item.changeType">{{ changeTypeLabel(item.changeType) }}</v-chip>
        </template>
        <template #item.changeTier="{ item }">
          <span :class="'tier-' + (item.changeTier || '').toLowerCase()">{{ item.changeTier }}</span>
        </template>
        <template #item.path="{ item }">
          <span style="font-family:monospace;">{{ pathOf(item) }}</span>
        </template>
        <template #item.aprvStatus="{ item }">
          <v-chip x-small :color="statusColor(item.aprvStatus)" dark>{{ item.aprvStatus }}</v-chip>
        </template>
        <template #item.changeDt="{ item }">
          <span class="nowrap">{{ formatDt(item.changeDt) }}</span>
        </template>
        <template #item.actions="{ item }">
          <div class="nowrap">
          <v-btn x-small text class="btn-auto act-btn" title="상세" v-on:click="showDetail(item)">
            <v-icon x-small>mdi-information-outline</v-icon>
          </v-btn>
          <v-btn v-if="item.ddlSnippet" x-small text class="btn-auto act-btn" v-on:click="copyDdl(item)">
            <v-icon x-small>mdi-content-copy</v-icon>DDL
          </v-btn>
          <v-btn v-if="canExecDdl && item.aprvStatus==='APPROVED' && item.ddlSnippet && !item.ddlExecDt"
            x-small text class="btn-auto act-btn" color="success" v-on:click="execDdl(item)">
            <v-icon x-small>mdi-database-arrow-up</v-icon>DB 반영
          </v-btn>
          <v-chip v-if="item.ddlExecDt" x-small outlined :color="item.ddlExecResult==='SUCCESS' ? 'success' : 'error'"
            :title="item.ddlExecMessage">
            {{ item.ddlExecResult }}
          </v-chip>
          </div>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 상세 모달 -->
    <v-dialog v-model="detailDialog" max-width="900" scrollable>
      <v-card>
        <v-card-title>변경 이력 상세 — #{{ detail.changeSeq }}</v-card-title>
        <v-card-text class="pt-2">
          <!-- 104번 WP9-55 — 항목별 전후 비교 (원문 JSON 은 접어 둔다) -->
          <div class="diff-title">변경 전후</div>
          <ChangeDiffTable id="dmHistDiff" :before="detail.beforeJson" :after="detail.afterJson" kind="DM" />
          <div class="mt-2">
            <a id="dmHistRawToggle" class="raw-link" v-on:click="showRaw = !showRaw">원문 JSON {{ showRaw ? '접기' : '보기' }}</a>
          </div>
          <div v-if="showRaw" class="d-flex mt-2" style="gap:16px;">
            <div style="flex:1;">
              <div class="diff-title">변경 전 (BEFORE)</div>
              <pre class="diff-box">{{ formatJson(detail.beforeJson) }}</pre>
            </div>
            <div style="flex:1;">
              <div class="diff-title">변경 후 (AFTER)</div>
              <pre class="diff-box">{{ formatJson(detail.afterJson) }}</pre>
            </div>
          </div>
          <div v-if="detail.ddlSnippet" class="mt-3">
            <div class="diff-title">DDL Snippet{{ detail.ddlDbmsTp ? ' (' + detail.ddlDbmsTp + ')' : '' }}</div>
            <pre class="ddl-box">{{ detail.ddlSnippet }}</pre>
          </div>
          <div v-if="detail.ddlExecDt" class="mt-2" style="font-size:.85rem;">
            DB 반영: {{ detail.ddlExecResult }} — {{ detail.ddlExecMessage }} ({{ formatDt(detail.ddlExecDt) }}, {{ detail.ddlExecUserId }})
          </div>
          <div v-if="detail.aprvComment" class="mt-2" style="color:#C62828;">
            반려 사유: {{ detail.aprvComment }}
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="detailDialog = false">닫기</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
import { dmChangeTypeLabel } from './dmChangeType';
import ChangeDiffTable from './ChangeDiffTable.vue';

export default {
  name: 'MMDataModelChangeHistory',
  components: { ChangeDiffTable },
  props: ['isMobile'],
  computed: {
    // 104번 WP2 — DB 반영 권한(관리자 또는 DDL_EXEC, 시스템 단위). 건마다 시스템 범위는 서버가 다시 본다
    canExecDdl() { return this.$can('DDL_EXEC'); },
  },
  data: () => ({
    items: [],
    filter: { dmId: '', changeUserId: '', changeTier: '', aprvStatus: '' },
    tierOptions: [
      { text: '전체', value: '' },
      { text: 'TIER1', value: 'TIER1' },
      { text: 'TIER1.5', value: 'TIER1_5' },
      { text: 'TIER2', value: 'TIER2' },
    ],
    statusOptions: [
      { text: '전체', value: '' },
      { text: 'DRAFT', value: 'DRAFT' },
      { text: 'SUBMITTED', value: 'SUBMITTED' },
      { text: 'APPROVED', value: 'APPROVED' },
      { text: 'REJECTED', value: 'REJECTED' },
    ],
    headers: [
      { text: '#', value: 'changeSeq', width: '80px' },
      { text: '유형', value: 'changeType', width: '140px' },
      { text: 'Tier', value: 'changeTier', width: '80px' },
      { text: '대상', value: 'path' },
      { text: '신청자', value: 'changeUserId', width: '120px' },
      { text: '신청 시각', value: 'changeDt', width: '150px' },
      { text: '상태', value: 'aprvStatus', width: '100px' },
      { text: '', value: 'actions', width: '200px', sortable: false },
    ],
    detailDialog: false,
    detail: {},
    showRaw: false,   // 104번 WP9-55 — 원문 JSON 펼침
  }),
  mounted() { this.load(); },
  activated() { this.load(); },
  methods: {
    load() {
      axios.post(this.$APIURL.base + 'api/dmApproval/history', this.filter).then(res => {
        this.items = res.data || [];
      }).catch(() => { this.items = []; });
    },
    pathOf(item) {
      // PK/FK 변경은 컬럼 대신 제약조건 이름을 붙인다
      return (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '')
        + (item.constraintNm ? ' [' + item.constraintNm + ']' : '');
    },
    changeTypeLabel(code) {
      return dmChangeTypeLabel(code);
    },
    statusColor(s) {
      if (s === 'APPROVED') return 'success';
      if (s === 'REJECTED') return 'error';
      if (s === 'SUBMITTED') return 'orange';
      if (s === 'DRAFT') return 'grey';
      return 'grey';
    },
    formatDt(s) {
      if (!s || s.length < 14) return s;
      return s.substr(0,4) + '-' + s.substr(4,2) + '-' + s.substr(6,2) + ' ' + s.substr(8,2) + ':' + s.substr(10,2);
    },
    formatJson(s) {
      if (!s) return '(없음)';
      try { return JSON.stringify(JSON.parse(s), null, 2); }
      catch (e) { return s; }
    },
    showDetail(item) {
      this.detail = item;
      this.showRaw = false;
      this.detailDialog = true;
    },
    copyDdl(item) {
      navigator.clipboard.writeText(item.ddlSnippet || '').then(() => {
        this.$swal.fire({ title: 'DDL 복사됨', icon: 'success', timer: 1000, showConfirmButton: false });
      });
    },
    // 104번 WP3 — 실행 대상은 모델의 데이터소스로 고정 (서버가 정한다). 테이블 추가는 승인된 컬럼으로 CREATE TABLE 을 만든다
    execDdl(item) {
      const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const target = item.dsNm
        ? `${esc(item.dsNm)} (${esc(item.dsDbmsTp)})`
        : '<span style="color:#C62828;">모델에 데이터소스가 없습니다 — 반영할 수 없습니다</span>';
      const ddlNote = item.changeType === 'ADD_OBJ'
        ? '<div style="color:#546E7A;">테이블 추가는 실행 때 이 테이블의 승인된 컬럼·제약조건으로 CREATE TABLE 을 만듭니다.</div>' : '';
      this.$swal.fire({
        title: 'DB 직접 반영',
        html: `<div style="text-align:left;font-size:.85rem;"><b>대상 데이터소스:</b> ${target}<br><br><b>실행될 DDL</b>${item.ddlDbmsTp ? ' (' + esc(item.ddlDbmsTp) + ')' : ''}:`
          + `<pre style="background:#263238;color:#B0BEC5;padding:6px;border-radius:4px;font-size:.78rem;white-space:pre-wrap;">${esc(item.ddlSnippet)}</pre>${ddlNote}</div>`,
        showCancelButton: true,
        confirmButtonText: '실행', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dmApproval/execDdl', {
          changeSeqList: [item.changeSeq],
        }).then(res => {
          if (res.data.resultCode !== 200) {
            this.$swal.fire({ title: 'DB 반영 실패', text: res.data.resultMessage || '', icon: 'error' });
            return;
          }
          const c = res.data && res.data.contents ? JSON.parse(res.data.contents) : {};
          this.$swal.fire({
            title: `DB 반영: 성공 ${c.success || 0} / 실패 ${c.failed || 0} / 권한 없음 ${c.noPriv || 0} / 건너뜀 ${c.skipped || 0}`,
            html: (c.messages || []).map(m => `<div style="text-align:left;font-size:.8rem;">${esc(m)}</div>`).join(''),
            icon: (c.success || 0) > 0 && !(c.failed || c.noPriv || c.skipped) ? 'success' : 'info',
          });
          this.load();
        }).catch(e => this.$swal.fire({ title: 'DB 반영 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
      });
    },
  },
};
</script>

<style scoped>
.tier-tier1 { color: #C62828; font-weight: 600; }
.tier-tier1_5 { color: #FB8C00; font-weight: 600; }
.tier-tier2 { color: #2E7D32; }
.diff-title { font-size: .85rem; font-weight: 600; color: #1A237E; margin-bottom: 4px; }
.raw-link { font-size: .78rem; cursor: pointer; text-decoration: underline; }
.nowrap { white-space: nowrap; }
.act-btn.v-btn { height: 26px !important; padding: 0 6px !important; }
.diff-box { background: #F5F5F5; padding: 8px; border-radius: 4px; font-size: .78rem; max-height: 300px; overflow: auto; }
.ddl-box { background: #263238; color: #B0BEC5; padding: 8px; border-radius: 4px; font-size: .8rem; font-family: monospace; max-height: 200px; overflow: auto; }
</style>
