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

      <v-data-table :headers="headers" :items="items" item-key="changeSeq"
        :items-per-page="30" dense fixed-header :height="600">
        <template #item.changeType="{ item }">
          <v-chip x-small outlined>{{ item.changeType }}</v-chip>
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
          {{ formatDt(item.changeDt) }}
        </template>
        <template #item.actions="{ item }">
          <v-btn x-small text v-on:click="showDetail(item)">
            <v-icon x-small>mdi-information-outline</v-icon>
          </v-btn>
          <v-btn v-if="item.ddlSnippet" x-small text v-on:click="copyDdl(item)">
            <v-icon x-small>mdi-content-copy</v-icon>DDL
          </v-btn>
          <v-btn v-if="isAdmin && item.aprvStatus==='APPROVED' && item.ddlSnippet && !item.ddlExecDt"
            x-small text color="success" v-on:click="execDdl(item)">
            <v-icon x-small>mdi-database-arrow-up</v-icon>DB 반영
          </v-btn>
          <v-chip v-if="item.ddlExecDt" x-small outlined :color="item.ddlExecResult==='SUCCESS' ? 'success' : 'error'">
            {{ item.ddlExecResult }}
          </v-chip>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 상세 모달 -->
    <v-dialog v-model="detailDialog" max-width="900">
      <v-card>
        <v-card-title>변경 이력 상세 — #{{ detail.changeSeq }}</v-card-title>
        <v-card-text>
          <div class="d-flex" style="gap:16px;">
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
            <div class="diff-title">DDL Snippet</div>
            <pre class="ddl-box">{{ detail.ddlSnippet }}</pre>
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

export default {
  name: 'MMDataModelChangeHistory',
  props: ['isMobile'],
  data: () => ({
    items: [],
    isAdmin: false,
    dsId: '',
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
      { text: '신청 시각', value: 'changeDt', width: '140px' },
      { text: '상태', value: 'aprvStatus', width: '100px' },
      { text: '', value: 'actions', width: '120px', sortable: false },
    ],
    detailDialog: false,
    detail: {},
  }),
  mounted() { this.load(); this.loadIsAdmin(); },
  activated() { this.load(); this.loadIsAdmin(); },
  methods: {
    loadIsAdmin() {
      axios.get(this.$APIURL.base + 'api/search/getUserInfo').then(r => {
        this.isAdmin = !!(r.data && (r.data.admin || r.data.role === 'A'));
      }).catch(() => { this.isAdmin = false; });
    },
    load() {
      axios.post(this.$APIURL.base + 'api/dmApproval/history', this.filter).then(res => {
        this.items = res.data || [];
      }).catch(() => { this.items = []; });
    },
    pathOf(item) {
      return (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '');
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
      this.detailDialog = true;
    },
    copyDdl(item) {
      navigator.clipboard.writeText(item.ddlSnippet || '').then(() => {
        this.$swal.fire({ title: 'DDL 복사됨', icon: 'success', timer: 1000, showConfirmButton: false });
      });
    },
    execDdl(item) {
      this.$swal.fire({
        title: 'DB 직접 반영',
        html: `<div style="text-align:left;font-size:.85rem;"><b>실행될 DDL:</b><pre style="background:#263238;color:#B0BEC5;padding:6px;border-radius:4px;font-size:.78rem;">${item.ddlSnippet}</pre><br>대상 데이터소스 ID:</div>`,
        input: 'text',
        inputValue: this.dsId,
        showCancelButton: true,
        confirmButtonText: '실행', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        this.dsId = r.value || '';
        axios.post(this.$APIURL.base + 'api/dmApproval/execDdl', {
          changeSeqList: [item.changeSeq], dsId: r.value,
        }).then(res => {
          const c = res.data && res.data.contents ? JSON.parse(res.data.contents) : {};
          this.$swal.fire({ title: `DB 반영: success ${c.success||0} / failed ${c.failed||0} / noPriv ${c.noPriv||0}`, icon: 'info' });
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
.diff-box { background: #F5F5F5; padding: 8px; border-radius: 4px; font-size: .78rem; max-height: 300px; overflow: auto; }
.ddl-box { background: #263238; color: #B0BEC5; padding: 8px; border-radius: 4px; font-size: .8rem; font-family: monospace; max-height: 200px; overflow: auto; }
</style>
