<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-shield-check-outline</v-icon>&nbsp;&nbsp;데이터 모델 변경 승인</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="loadSubmissions">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <v-sheet style="display:flex; gap:16px;">
        <!-- 좌측: 신청 묶음 목록 -->
        <v-sheet style="width:380px; border:1px solid #E0E0E0; border-radius:6px; padding:8px;">
          <div style="font-size:.85rem; font-weight:600; color:#1A237E; margin-bottom:6px;">
            신청 묶음 ({{ submissions.length }})
          </div>
          <div v-if="submissions.length === 0" style="color:#90A4AE; font-size:.82rem; padding:16px; text-align:center;">
            대기 중인 변경 신청이 없습니다.
          </div>
          <div v-for="s in submissions" :key="s.submissionId"
            class="submission-row"
            :class="{ active: selectedSubmissionId === s.submissionId }"
            v-on:click="selectSubmission(s)">
            <div style="font-size:.82rem; font-weight:600; color:#283593;">{{ s.changeUserId }}</div>
            <div style="font-size:.72rem; color:#546E7A;">{{ formatDt(s.firstDt) }}</div>
            <div style="font-size:.78rem;">변경 {{ s.itemCnt }}건 · 모델 {{ s.dmId }}</div>
          </div>
        </v-sheet>

        <!-- 우측: 선택 묶음의 항목들 -->
        <v-sheet style="flex:1; border:1px solid #E0E0E0; border-radius:6px; padding:8px;">
          <div v-if="!selectedSubmissionId" style="color:#90A4AE; padding:16px; text-align:center;">
            좌측에서 신청 묶음을 선택하세요.
          </div>
          <template v-else>
            <div style="display:flex; gap:8px; align-items:center; margin-bottom:8px;">
              <v-checkbox v-model="selectAll" dense hide-details class="ma-0" v-on:change="toggleAll" />
              <span style="font-size:.85rem;">전체 선택</span>
              <v-spacer />
              <v-btn small color="success" depressed :disabled="checkedItems.length === 0" v-on:click="approveSelected">
                <v-icon small left>mdi-check</v-icon>선택 승인 ({{ checkedItems.length }})
              </v-btn>
              <v-btn small color="error" outlined :disabled="checkedItems.length === 0" v-on:click="rejectSelected">
                <v-icon small left>mdi-close</v-icon>선택 반려
              </v-btn>
            </div>
            <v-data-table :headers="headers" :items="items" item-key="changeSeq" show-select
              v-model="checkedItems" :items-per-page="50" hide-default-footer dense>
              <template #item.changeType="{ item }">
                <v-chip x-small outlined>{{ item.changeType }}</v-chip>
              </template>
              <template #item.changeTier="{ item }">
                <span :class="'tier-' + item.changeTier.toLowerCase()">{{ item.changeTier }}</span>
              </template>
              <template #item.path="{ item }">
                <span style="font-family:monospace;">
                  {{ (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '') }}
                </span>
              </template>
              <template #item.aprvStatus="{ item }">
                <v-chip x-small :color="statusColor(item.aprvStatus)" dark>{{ item.aprvStatus }}</v-chip>
              </template>
            </v-data-table>
          </template>
        </v-sheet>
      </v-sheet>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMDataModelApproval',
  props: ['isMobile'],
  data: () => ({
    submissions: [],
    selectedSubmissionId: null,
    items: [],
    checkedItems: [],
    selectAll: false,
    headers: [
      { text: '유형', value: 'changeType', width: '130px' },
      { text: 'Tier', value: 'changeTier', width: '80px' },
      { text: '대상', value: 'path' },
      { text: '신청 시각', value: 'changeDt', width: '140px' },
      { text: '상태', value: 'aprvStatus', width: '100px' },
    ],
  }),
  mounted() { this.loadSubmissions(); },
  activated() { this.loadSubmissions(); },
  methods: {
    loadSubmissions() {
      axios.post(this.$APIURL.base + 'api/dmApproval/submissions', {}).then(res => {
        this.submissions = res.data || [];
      }).catch(() => { this.submissions = []; });
    },
    selectSubmission(s) {
      this.selectedSubmissionId = s.submissionId;
      this.checkedItems = [];
      axios.post(this.$APIURL.base + 'api/dmApproval/submissionItems', {
        submissionId: s.submissionId,
      }).then(res => {
        this.items = res.data || [];
      }).catch(() => { this.items = []; });
    },
    toggleAll(v) {
      this.checkedItems = v ? [...this.items] : [];
    },
    approveSelected() {
      const seqList = this.checkedItems.map(i => i.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 승인하시겠습니까?`,
        icon: 'question', showCancelButton: true,
        confirmButtonText: '승인', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dmApproval/approve', {
          changeSeqList: seqList,
        }).then(() => {
          this.$swal.fire({ title: '승인 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadSubmissions();
          this.selectedSubmissionId = null;
          this.items = [];
          this.checkedItems = [];
        }).catch((e) => this.$swal.fire({ title: '승인 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
      });
    },
    rejectSelected() {
      const seqList = this.checkedItems.map(i => i.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 반려`,
        input: 'textarea',
        inputLabel: '반려 사유 (선택)',
        inputPlaceholder: '사유를 입력하세요',
        showCancelButton: true,
        confirmButtonText: '반려', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dmApproval/reject', {
          changeSeqList: seqList,
          aprvComment: r.value || null,
        }).then(() => {
          this.$swal.fire({ title: '반려 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadSubmissions();
          this.selectedSubmissionId = null;
          this.items = [];
          this.checkedItems = [];
        }).catch((e) => this.$swal.fire({ title: '반려 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
      });
    },
    statusColor(s) {
      if (s === 'APPROVED') return 'success';
      if (s === 'REJECTED') return 'error';
      if (s === 'SUBMITTED') return 'orange';
      return 'grey';
    },
    formatDt(s) {
      if (!s || s.length < 14) return s;
      return s.substr(0,4) + '-' + s.substr(4,2) + '-' + s.substr(6,2) + ' ' + s.substr(8,2) + ':' + s.substr(10,2);
    },
  },
};
</script>

<style scoped>
.submission-row {
  padding: 8px;
  border: 1px solid #E0E0E0;
  border-radius: 4px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.submission-row:hover { background: #F5F7FF; }
.submission-row.active { background: #E8EAF6; border-color: #3F51B5; }
.tier-tier1 { color: #C62828; font-weight: 600; }
.tier-tier1_5 { color: #FB8C00; font-weight: 600; }
.tier-tier2 { color: #2E7D32; }
</style>
