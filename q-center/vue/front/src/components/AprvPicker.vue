<template>
  <!-- 108 §4.33 — 결재선 단계 방식 "요청자 지정" 의 결재자를 등록 신청 때 고른다. 그런 단계가 없으면 아무것도 안 보인다 -->
  <div v-if="groups.length > 0" class="aprv-picker">
    <div class="aprv-picker-title"><v-icon small color="#455A64">mdi-account-check-outline</v-icon> 결재자 지정</div>
    <!-- 넓은 화면(한글컬럼 일괄 표준화·변경 신청)에서는 묶음·단계를 나란히, 좁은 등록 창에서는 한 줄씩 -->
    <div class="aprv-picker-groups">
      <div v-for="g in groups" :key="g.reqTp" class="aprv-picker-group" :style="{ flexGrow: g.steps.length, flexBasis: (g.steps.length * 260) + 'px' }">
        <div v-if="groups.length > 1" class="aprv-picker-head">{{ headOf(g.reqTp) }}</div>
        <div class="aprv-picker-steps">
          <div v-for="st in g.steps" :key="st.stepNo" class="aprv-picker-row">
            <!-- 단계 이름은 길 수 있어 칸 라벨이 아니라 위 줄에 (줄바꿈) -->
            <div class="aprv-picker-step">{{ st.stepNo }}단계{{ st.stepNm ? ' · ' + st.stepNm : '' }}{{ st.agreeYn === 'Y' ? ' (합의)' : '' }}</div>
            <v-autocomplete :id="'aprvPick_' + g.reqTp + '_' + st.stepNo"
              :value="(picks[g.reqTp] || {})[st.stepNo]" @change="v => setPick(g.reqTp, st.stepNo, v)"
              :items="users" item-value="userId" :item-text="userText"
              label="결재자 *"
              no-data-text="고를 수 있는 사용자가 없습니다"
              dense outlined hide-details clearable />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

const REQ_TP_NM = { WORD: '단어', TERMS: '용어', CODE: '코드', DOMAIN: '도메인', DM_CHANGE: '데이터 모델 변경' };

export default {
  name: 'AprvPicker',
  props: {
    // 요청 유형 목록 — 이 요청으로 결재 문서가 생길 수 있는 유형 (예: 용어 등록 창은 TERMS 와 창 안 단어 등록의 WORD)
    reqTps: { type: Array, required: true },
    // true 가 될 때마다 결재선을 다시 읽고 고른 것을 비운다 (창을 열 때, 등록 신청일 때만)
    active: { type: Boolean, default: false },
    // 요청 유형이 둘 이상일 때 묶음 머리 (없으면 유형 이름)
    heads: { type: Object, default: () => ({}) },
  },
  data: () => ({
    groups: [],
    users: [],
    picks: {},
  }),
  watch: {
    active: { immediate: true, handler(v) { if (v) this.reload(); else this.groups = []; } },
    // 용어 등록 창처럼 고른 값에 따라 요청 유형이 바뀌면(TERMS ↔ CODE) 다시 읽는다
    reqTps(n, o) { if (this.active && n.join() !== o.join()) this.reload(); },
  },
  methods: {
    reload() {
      this.picks = {};
      const tps = this.reqTps.slice();
      Promise.all(tps.map(tp => axios.get(this.$APIURL.base + 'api/aprv/pickSteps', { params: { reqTp: tp } })
        .then(res => ({ reqTp: tp, steps: res.data.resultCode === 200 ? JSON.parse(res.data.contents || '[]') : [] }))
        .catch(() => ({ reqTp: tp, steps: [] }))))
        .then(gs => {
          this.groups = gs.filter(g => g.steps.length > 0);
          if (this.groups.length === 0 || this.users.length > 0) return;
          axios.get(this.$APIURL.base + 'api/aprv/pickUsers').then(res => {
            this.users = res.data.resultCode === 200 ? JSON.parse(res.data.contents || '[]') : [];
          });
        });
    },
    userText(u) {
      return (u.userNm || u.userId) + ' (' + u.userId + ')' + (u.orgNm ? ' · ' + u.orgNm : '');
    },
    headOf(reqTp) {
      return this.heads[reqTp] || REQ_TP_NM[reqTp] || reqTp;
    },
    setPick(reqTp, stepNo, userId) {
      this.$set(this.picks, reqTp, Object.assign({}, this.picks[reqTp], { [stepNo]: userId || undefined }));
    },
    /** 그 요청 유형의 고른 결재자 {stepNo: userId} — 그 유형에 "요청자 지정" 단계가 없으면 null */
    picksFor(reqTp) {
      if (!this.groups.some(g => g.reqTp === reqTp)) return null;
      return Object.assign({}, this.picks[reqTp]);
    },
    /** 고르지 않은 단계가 있으면 안내 문구, 아니면 null. only 를 주면 그 유형만 본다 */
    validate(only) {
      for (const g of this.groups) {
        if (only && only.indexOf(g.reqTp) < 0) continue;
        for (const st of g.steps) {
          if (!(this.picks[g.reqTp] || {})[st.stepNo]) {
            // 긴 등록 창 아래에 있어 안 보일 수 있다 — 안내를 닫으면 바로 보이게
            if (this.$el && this.$el.scrollIntoView) this.$el.scrollIntoView({ block: 'center' });
            return '결재자를 지정하세요: ' + (REQ_TP_NM[g.reqTp] || g.reqTp) + ' ' + st.stepNo + '단계' + (st.stepNm ? ' (' + st.stepNm + ')' : '');
          }
        }
      }
      return null;
    },
  },
};
</script>

<style scoped>
.aprv-picker { border: 1px solid #CFD8DC; border-radius: 4px; padding: 8px 12px 10px; margin: 8px 0; background: #FAFBFC; }
.aprv-picker-title { font-size: .85rem; font-weight: 600; color: #37474F; margin-bottom: 6px; }
.aprv-picker-head { font-size: .8rem; font-weight: 600; color: #37474F; margin: 4px 0 0; word-break: keep-all; }
/* 넓은 화면(한글컬럼 일괄 표준화·변경 신청)에서는 묶음·단계를 나란히(묶음 폭 = 단계 수 × 260px 기준), 좁은 등록 창에서는 한 줄씩.
   단계 이름 줄 수가 달라도 칸 아래를 맞춘다 */
.aprv-picker-groups { display: flex; flex-wrap: wrap; gap: 4px 24px; align-items: flex-end; }
.aprv-picker-group { flex-shrink: 1; min-width: 0; }
.aprv-picker-steps { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 6px 12px; align-items: end; }
.aprv-picker-row { margin-top: 6px; min-width: 0; }
.aprv-picker-step { font-size: .78rem; color: #455A64; margin-bottom: 4px; word-break: keep-all; overflow-wrap: anywhere; }
</style>
