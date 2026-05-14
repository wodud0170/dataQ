<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-clipboard-edit-outline</v-icon>&nbsp;&nbsp;내 변경 신청</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="loadAll">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <!-- DRAFT 영역 — 신청 전 -->
      <v-sheet class="section">
        <div class="section-title">
          <v-icon small color="grey darken-1">mdi-pencil-outline</v-icon>
          DRAFT (저장만 됨, 신청 전) — {{ drafts.length }}건
          <v-spacer />
          <v-btn small color="primary" depressed :disabled="checkedDrafts.length === 0"
            v-on:click="submitSelected">
            <v-icon small left>mdi-send</v-icon>선택 신청 ({{ checkedDrafts.length }})
          </v-btn>
          <v-btn small color="error" outlined :disabled="checkedDrafts.length === 0"
            v-on:click="rollbackSelected" class="ml-2">
            <v-icon small left>mdi-undo</v-icon>선택 롤백
          </v-btn>
        </div>
        <v-data-table v-if="drafts.length > 0" :headers="headers" :items="drafts"
          item-key="changeSeq" show-select v-model="checkedDrafts"
          :items-per-page="20" hide-default-footer dense>
          <template #item.changeType="{ item }">
            <v-chip x-small outlined>{{ item.changeType }}</v-chip>
          </template>
          <template #item.path="{ item }">
            <span style="font-family:monospace;">{{ pathOf(item) }}</span>
          </template>
        </v-data-table>
        <div v-else style="color:#90A4AE; padding:16px; text-align:center;">저장된 DRAFT 가 없습니다.</div>
      </v-sheet>

      <!-- SUBMITTED 영역 — 관리자 대기 -->
      <v-sheet class="section">
        <div class="section-title">
          <v-icon small color="orange">mdi-clock-outline</v-icon>
          SUBMITTED (관리자 승인 대기) — {{ submitted.length }}건
        </div>
        <v-data-table v-if="submitted.length > 0" :headers="headers" :items="submitted"
          item-key="changeSeq" :items-per-page="20" hide-default-footer dense>
          <template #item.changeType="{ item }">
            <v-chip x-small outlined>{{ item.changeType }}</v-chip>
          </template>
          <template #item.path="{ item }">
            <span style="font-family:monospace;">{{ pathOf(item) }}</span>
          </template>
        </v-data-table>
        <div v-else style="color:#90A4AE; padding:16px; text-align:center;">신청 중인 항목이 없습니다.</div>
      </v-sheet>

      <!-- REJECTED 영역 — 반려됨 -->
      <v-sheet class="section">
        <div class="section-title">
          <v-icon small color="error">mdi-close-circle-outline</v-icon>
          REJECTED (반려) — {{ rejected.length }}건
        </div>
        <v-data-table v-if="rejected.length > 0" :headers="headersRej" :items="rejected"
          item-key="changeSeq" :items-per-page="20" hide-default-footer dense>
          <template #item.changeType="{ item }">
            <v-chip x-small outlined>{{ item.changeType }}</v-chip>
          </template>
          <template #item.path="{ item }">
            <span style="font-family:monospace;">{{ pathOf(item) }}</span>
          </template>
          <template #item.aprvComment="{ item }">
            <span style="color:#C62828; font-size:.8rem;">{{ item.aprvComment || '-' }}</span>
          </template>
        </v-data-table>
        <div v-else style="color:#90A4AE; padding:16px; text-align:center;">반려된 항목이 없습니다.</div>
      </v-sheet>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMMyDataModelChanges',
  props: ['isMobile'],
  data: () => ({
    drafts: [],
    submitted: [],
    rejected: [],
    checkedDrafts: [],
    headers: [
      { text: '유형', value: 'changeType', width: '140px' },
      { text: '대상', value: 'path' },
      { text: '신청 시각', value: 'changeDt', width: '160px' },
    ],
    headersRej: [
      { text: '유형', value: 'changeType', width: '140px' },
      { text: '대상', value: 'path' },
      { text: '신청 시각', value: 'changeDt', width: '160px' },
      { text: '반려 사유', value: 'aprvComment' },
    ],
  }),
  mounted() { this.loadAll(); },
  activated() { this.loadAll(); },
  methods: {
    loadAll() {
      // 본인 DRAFT
      axios.post(this.$APIURL.base + 'api/dmApproval/myDrafts', {}).then(res => {
        this.drafts = res.data || [];
      }).catch(() => { this.drafts = []; });
      // 본인의 SUBMITTED / REJECTED — history endpoint 호출 후 클라이언트에서 분류
      axios.post(this.$APIURL.base + 'api/dmApproval/history', { dmId: '' }).then(res => {
        const all = res.data || [];
        const myUser = window.__myUserId || '';
        // 본인 신청 + status filter
        this.submitted = all.filter(r => r.aprvStatus === 'SUBMITTED');
        this.rejected  = all.filter(r => r.aprvStatus === 'REJECTED');
      }).catch(() => { this.submitted = []; this.rejected = []; });
    },
    pathOf(item) {
      return (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '');
    },
    submitSelected() {
      const seqList = this.checkedDrafts.map(d => d.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 신청`,
        text: '관리자 승인 대기 상태로 전환됩니다.',
        icon: 'question', showCancelButton: true,
        confirmButtonText: '신청', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dmApproval/submit', {
          changeSeqList: seqList,
        }).then(() => {
          this.$swal.fire({ title: '신청 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadAll();
        }).catch((e) => this.$swal.fire({ title: '신청 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
      });
    },
    rollbackSelected() {
      const seqList = this.checkedDrafts.map(d => d.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 롤백`,
        text: '선택한 DRAFT 가 삭제됩니다 (모델 변경은 그대로). 진행?',
        icon: 'warning', showCancelButton: true,
        confirmButtonText: '롤백', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        Promise.all(seqList.map(seq =>
          axios.post(this.$APIURL.base + 'api/dmApproval/rollbackDraft', { changeSeq: seq })
        )).then(() => {
          this.$swal.fire({ title: '롤백 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadAll();
        }).catch(() => this.$swal.fire({ title: '롤백 실패', icon: 'error' }));
      });
    },
  },
};
</script>

<style scoped>
.section {
  border: 1px solid #E0E0E0;
  border-radius: 6px;
  margin-bottom: 16px;
  padding: 8px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: .9rem;
  font-weight: 600;
  color: #1A237E;
  padding: 4px 4px 8px 4px;
  border-bottom: 1px solid #ECEFF1;
  margin-bottom: 8px;
}
</style>
