<template>
  <!-- 88번 거버넌스 — 테이블/컬럼 화면에서 본인 DRAFT 일괄 신청 (별도 탭 인지성 문제 해소) -->
  <v-dialog v-model="dialog" max-width="920" scrollable>
    <v-card>
      <v-card-title>
        <v-icon left>mdi-send-clock-outline</v-icon>내 변경 신청
        <v-spacer />
        <span style="font-size:.85rem; color:#546E7A;">DRAFT {{ drafts.length }}건</span>
      </v-card-title>
      <v-divider />
      <v-card-text style="max-height:60vh;">
        <div style="font-size:.82rem; color:#78909C; margin:8px 0;">
          저장된(DRAFT) 변경을 선택해 관리자에게 일괄 신청합니다.
          신청하면 SUBMITTED 가 되어 관리자 승인 화면에 노출되며, 승인 전까지는 본인에게만 보입니다.
        </div>
        <v-data-table v-if="drafts.length > 0" :headers="headers" :items="drafts"
          item-key="changeSeq" show-select v-model="checked"
          :items-per-page="50" hide-default-footer dense>
          <template #[`item.changeType`]="{ item }">
            <v-chip x-small outlined>{{ item.changeType }}</v-chip>
          </template>
          <template #[`item.path`]="{ item }">
            <span style="font-family:monospace; font-size:.8rem;">{{ pathOf(item) }}</span>
          </template>
          <template #[`item.changeDt`]="{ item }">{{ formatDt(item.changeDt) }}</template>
        </v-data-table>
        <div v-else style="color:#90A4AE; padding:24px; text-align:center;">
          저장된 DRAFT 가 없습니다.
        </div>
      </v-card-text>
      <v-divider />
      <v-card-actions>
        <v-btn small color="error" outlined :disabled="checked.length === 0" @click="rollbackSelected">
          <v-icon small left>mdi-undo</v-icon>선택 롤백
        </v-btn>
        <v-spacer />
        <v-btn text @click="dialog = false">닫기</v-btn>
        <v-btn color="primary" depressed :disabled="checked.length === 0" @click="submitSelected">
          <v-icon small left>mdi-send</v-icon>선택 신청 ({{ checked.length }})
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DMSubmitModal',
  props: {
    value: { type: Boolean, default: false },
  },
  data: () => ({
    drafts: [],
    checked: [],
    headers: [
      { text: '유형', value: 'changeType', width: '140px' },
      { text: '대상', value: 'path' },
      { text: '저장 시각', value: 'changeDt', width: '160px' },
    ],
  }),
  computed: {
    dialog: {
      get() { return this.value; },
      set(v) { this.$emit('input', v); },
    },
  },
  watch: {
    value(v) { if (v) this.loadDrafts(); },
  },
  methods: {
    loadDrafts() {
      this.checked = [];
      axios.post(this.$APIURL.base + 'api/dmApproval/myDrafts', {}).then(res => {
        this.drafts = res.data || [];
      }).catch(() => { this.drafts = []; });
    },
    pathOf(item) {
      return (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '');
    },
    formatDt(s) {
      if (!s || s.length < 14) return s || '';
      return s.substr(0, 4) + '-' + s.substr(4, 2) + '-' + s.substr(6, 2) + ' ' + s.substr(8, 2) + ':' + s.substr(10, 2);
    },
    submitSelected() {
      const seqList = this.checked.map(d => d.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 신청`,
        text: '관리자 승인 대기(SUBMITTED) 상태로 전환됩니다.',
        icon: 'question', showCancelButton: true,
        confirmButtonText: '신청', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dmApproval/submit', { changeSeqList: seqList }).then(() => {
          this.$swal.fire({ title: '신청 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadDrafts();
          this.$emit('submitted');
        }).catch((e) => this.$swal.fire({
          title: '신청 실패',
          text: (e.response && e.response.data && e.response.data.resultMessage) || '',
          icon: 'error',
        }));
      });
    },
    rollbackSelected() {
      const seqList = this.checked.map(d => d.changeSeq);
      this.$swal.fire({
        title: `${seqList.length}건 롤백`,
        text: '선택한 DRAFT 가 삭제됩니다 (이미 적용된 모델 변경은 별도). 진행하시겠습니까?',
        icon: 'warning', showCancelButton: true,
        confirmButtonText: '롤백', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        Promise.all(seqList.map(seq =>
          axios.post(this.$APIURL.base + 'api/dmApproval/rollbackDraft', { changeSeq: seq })
        )).then(() => {
          this.$swal.fire({ title: '롤백 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.loadDrafts();
          this.$emit('submitted');
        }).catch(() => this.$swal.fire({ title: '롤백 실패', icon: 'error' }));
      });
    },
  },
};
</script>
