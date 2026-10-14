<!--
  104번 WP1 — 결재함 (마이페이지 > 결재함, 로그인 사용자 모두)

  결재선이 있는 요청(표준사전 단어·용어·코드·도메인, 데이터 모델 변경 신청)의 결재 문서를 본다.
  내 차례인 문서는 승인·반려(사유 필수)하고, 표준사전 항목은 설명·영문명을 고친 뒤 승인할 수 있다.
  처리는 대상별 기존 승인 API 로 가서, 마지막 단계에서만 실제 승인·반려가 일어난다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        결재선이 정해진 요청은 단계마다 결재자가 승인해야 반영됩니다. 어느 단계에서든 반려하면 바로 반려됩니다.
      </span>
    </v-sheet>

    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <v-btn-toggle v-model="box" mandatory dense color="primary">
        <v-btn small value="TURN">내 차례<span v-if="turnCnt > 0">&nbsp;({{ turnCnt }})</span></v-btn>
        <v-btn small value="MINE">내가 올린 결재</v-btn>
        <v-btn small value="DONE">처리한 결재</v-btn>
        <v-btn v-if="$perm.admin" small value="ALL">진행 중 전체</v-btn>
      </v-btn-toggle>
      <v-select v-model="reqTp" :items="reqTpItems" dense outlined hide-details clearable placeholder="요청 유형 전체"
        style="max-width:200px;" />
      <v-spacer />
      <v-btn small outlined color="primary" @click="load"><v-icon small left>mdi-refresh</v-icon>새로고침</v-btn>
    </v-sheet>

    <v-row no-gutters style="flex:1; min-height:0;">
      <v-col cols="12" md="6" class="pr-md-2" style="min-height:0;">
        <v-data-table :headers="headers" :items="docs" dense item-key="docId" single-select
          :items-per-page="20" class="elevation-0" @click:row="select" :item-class="rowClass">
          <template v-slot:item.docStat="{ item }">
            <v-chip x-small :color="statColor(item.docStat)" dark>{{ statLabel(item) }}</v-chip>
          </template>
          <template v-slot:item.reqDt="{ item }">{{ fmtDt(item.reqDt) }}</template>
        </v-data-table>
      </v-col>

      <v-col cols="12" md="6" class="pl-md-2" style="min-height:0; overflow:auto;">
        <v-card v-if="doc" outlined class="pa-3">
          <div class="d-flex align-center mb-2">
            <h3 style="font-size:1rem;">{{ doc.reqTpNm }} — {{ doc.targetNm }}</h3>
            <v-spacer />
            <v-chip small :color="statColor(doc.docStat)" dark>{{ statLabel(doc) }}</v-chip>
          </div>
          <div style="font-size:.8rem; color:#546E7A;" class="mb-3">
            요청자 {{ doc.reqUserNm || doc.reqUserId }} · 요청 {{ fmtDt(doc.reqDt) }}<span v-if="doc.endDt"> · 종료 {{ fmtDt(doc.endDt) }}</span>
          </div>

          <div class="sec-title">결재 진행</div>
          <v-simple-table dense class="mb-3">
            <thead>
              <tr><th>단계</th><th>결재자</th><th>결과</th><th>의견</th><th>처리</th></tr>
            </thead>
            <tbody>
              <tr v-for="s in doc.steps" :key="s.stepNo + '-' + s.aprvrUserId" :class="{ 'cur-step': s.stepNo === doc.curStepNo && doc.docStat === 'PROGRESS' }">
                <td>{{ s.stepNo }}<span v-if="s.stepNm"> {{ s.stepNm }}</span><span v-if="s.agreeYn === 'Y'" class="agree"> (합의)</span></td>
                <td>{{ s.aprvrUserNm || s.aprvrUserId }}</td>
                <td><span :class="'act-' + s.actStat">{{ actLabel(s.actStat) }}</span></td>
                <td style="max-width:200px; white-space:normal;">{{ s.actCmnt }}</td>
                <td style="font-size:.72rem;">
                  <span v-if="s.actDt">{{ fmtDt(s.actDt) }}</span>
                  <span v-if="s.actUserId && s.actUserId !== s.aprvrUserId"> (대결 {{ s.actUserId }})</span>
                </td>
              </tr>
            </tbody>
          </v-simple-table>

          <div class="sec-title">요청 내용</div>
          <v-simple-table v-if="doc.target" dense class="mb-3">
            <tbody>
              <tr v-for="f in targetFields" :key="f.key"><th style="width:130px;">{{ f.label }}</th><td>{{ doc.target[f.key] }}</td></tr>
            </tbody>
          </v-simple-table>
          <div v-else-if="doc.reqTp !== 'DM_CHANGE'" class="mb-3 grey--text" style="font-size:.8rem;">대상 항목이 없습니다 (다른 경로로 삭제됨).</div>
          <v-simple-table v-if="doc.items" dense class="mb-3">
            <thead><tr><th>#</th><th>유형</th><th>대상</th><th>상태</th></tr></thead>
            <tbody>
              <tr v-for="i in doc.items" :key="i.changeSeq">
                <td>{{ i.changeSeq }}</td>
                <td>{{ changeTypeLabel(i.changeType) }}</td>
                <td style="font-family:monospace; font-size:.75rem;">{{ pathOf(i) }}</td>
                <td>{{ i.aprvStatus }}</td>
              </tr>
            </tbody>
          </v-simple-table>

          <div v-if="doc.canAct" class="d-flex" style="gap:8px;">
            <v-spacer />
            <v-btn v-if="doc.reqTp !== 'DM_CHANGE' && doc.target" small outlined color="primary" @click="openEdit">수정 후 승인</v-btn>
            <v-btn small color="error" @click="rejectDialog = true; rejectReason = ''">반려</v-btn>
            <v-btn small color="primary" @click="act(true, '')">승인</v-btn>
          </div>
        </v-card>
        <div v-else class="pa-6 text-center grey--text" style="font-size:.85rem;">왼쪽 목록에서 결재를 고르세요.</div>
      </v-col>
    </v-row>

    <!-- 반려 -->
    <v-dialog v-model="rejectDialog" max-width="480">
      <v-card>
        <v-card-title>반려</v-card-title>
        <v-card-text>
          <v-textarea v-model="rejectReason" label="반려 사유" rows="3" outlined dense counter="500"
            :rules="[v => !!(v && v.trim()) || '반려 사유는 필수입니다.']" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="rejectDialog = false">취소</v-btn>
          <v-btn color="error" :disabled="!rejectReason || !rejectReason.trim()" @click="act(false, rejectReason)">반려</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 수정 후 승인 -->
    <v-dialog v-model="editDialog" max-width="560">
      <v-card>
        <v-card-title>수정 후 승인 — {{ doc ? doc.targetNm : '' }}</v-card-title>
        <v-card-text>
          <template v-if="doc && doc.reqTp === 'WORD'">
            <v-text-field v-model="edit.engNm" label="영문명" dense />
            <v-text-field v-model="edit.engAbrvNm" label="영문약어" dense hint="대문자 영문·숫자" persistent-hint />
          </template>
          <v-textarea v-model="edit.desc" label="설명" rows="3" dense class="mt-2" counter="1000" />
          <v-text-field v-model="edit.comment" label="승인 의견 (선택)" dense />
          <div style="font-size:.75rem; color:#78909C;">고친 내용은 변경 이력에 "결재자 수정" 으로 남습니다.</div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="editDialog = false">취소</v-btn>
          <v-btn color="primary" @click="editAndApprove">수정 후 승인</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="4000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
import { dmChangeTypeLabel } from './dmChangeType';

export default {
  name: 'DSAprvBox',
  props: ['isMobile'],
  data() {
    return {
      box: 'TURN',
      reqTp: null,
      reqTpItems: [
        { text: '단어', value: 'WORD' }, { text: '용어', value: 'TERMS' }, { text: '코드', value: 'CODE' },
        { text: '도메인', value: 'DOMAIN' }, { text: '데이터 모델 변경', value: 'DM_CHANGE' },
      ],
      docs: [],
      turnCnt: 0,
      doc: null,
      rejectDialog: false,
      rejectReason: '',
      editDialog: false,
      edit: { engNm: '', engAbrvNm: '', desc: '', comment: '' },
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
      headers: [
        { text: '유형', value: 'reqTpNm', width: '110px' },
        { text: '대상', value: 'targetNm' },
        { text: '요청자', value: 'reqUserNm', width: '90px' },
        { text: '요청 일시', value: 'reqDt', width: '130px' },
        { text: '상태', value: 'docStat', width: '110px' },
        { text: '현재 결재자', value: 'curAprvrNms', width: '120px' },
      ],
    };
  },
  computed: {
    targetFields() {
      if (!this.doc) return [];
      switch (this.doc.reqTp) {
        case 'WORD': return [
          { key: 'nm', label: '단어명' }, { key: 'engNm', label: '영문명' }, { key: 'engAbrvNm', label: '영문약어' },
          { key: 'desc', label: '설명' }, { key: 'wordClsfYn', label: '형식단어' }, { key: 'domainClsfNm', label: '도메인 분류' },
        ];
        case 'DOMAIN': return [
          { key: 'nm', label: '도메인명' }, { key: 'dataType', label: '데이터 타입' }, { key: 'dataLen', label: '길이' },
          { key: 'dataDecimalLen', label: '소수점' }, { key: 'desc', label: '설명' }, { key: 'domainGrpNm', label: '도메인 그룹' },
        ];
        default: return [
          { key: 'nm', label: this.doc.reqTp === 'CODE' ? '코드명' : '용어명' }, { key: 'engAbrvNm', label: '영문약어' },
          { key: 'words', label: '구성 단어' }, { key: 'domainNm', label: '도메인' }, { key: 'desc', label: '설명' },
        ];
      }
    },
  },
  watch: {
    box() { this.doc = null; this.load(); },
    reqTp() { this.load(); },
  },
  mounted() {
    this.load();
    eventBus.$on('openAprvDoc', this.applyPending);
    eventBus.$on('APRV_CHANGED', this.load);
  },
  activated() {
    this.applyPending();
  },
  beforeDestroy() {
    eventBus.$off('openAprvDoc', this.applyPending);
    eventBus.$off('APRV_CHANGED', this.load);
  },
  methods: {
    applyPending() {
      if (eventBus.pendingAprvDoc) {
        const docId = eventBus.pendingAprvDoc;
        eventBus.pendingAprvDoc = null;
        this.loadDoc(docId);
      }
    },
    load() {
      axios.get(this.$APIURL.base + 'api/aprv/box', { params: { box: this.box, reqTp: this.reqTp || '' } }).then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.notify('조회 실패: ' + (res.data && res.data.resultMessage), 'error');
          return;
        }
        this.docs = JSON.parse(res.data.contents);
      }).catch(() => this.notify('결재함 조회 실패', 'error'));
      axios.get(this.$APIURL.base + 'api/aprv/turnCount').then(res => {
        this.turnCnt = typeof res.data === 'number' ? res.data : 0;
      });
    },
    select(item) {
      this.loadDoc(item.docId);
    },
    loadDoc(docId) {
      axios.get(this.$APIURL.base + 'api/aprv/doc', { params: { docId } }).then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.notify('상세 조회 실패: ' + (res.data && res.data.resultMessage), 'error');
          return;
        }
        this.doc = JSON.parse(res.data.contents);
      }).catch(() => this.notify('상세 조회 실패', 'error'));
    },
    act(approve, comment) {
      axios.post(this.$APIURL.base + 'api/aprv/act', { docId: this.doc.docId, approve, comment }).then(res => this.afterAct(res, approve))
        .catch(e => this.notify('처리 실패: ' + ((e.response && e.response.data && e.response.data.resultMessage) || ''), 'error'));
    },
    afterAct(res, approve) {
      this.rejectDialog = false;
      this.editDialog = false;
      if (res.data && res.data.resultCode === 200) {
        const m = res.data.resultMessage;
        this.notify(m && m !== 'OK' ? m : (approve ? '승인했습니다.' : '반려했습니다.'), 'success');
      } else {
        this.notify('처리 실패: ' + (res.data && res.data.resultMessage), 'error');
      }
      const docId = this.doc.docId;
      this.load();
      this.loadDoc(docId);
    },
    openEdit() {
      const t = this.doc.target || {};
      this.edit = { engNm: t.engNm || '', engAbrvNm: t.engAbrvNm || '', desc: t.desc || '', comment: '' };
      this.editDialog = true;
    },
    editAndApprove() {
      axios.post(this.$APIURL.base + 'api/aprv/editAndApprove', Object.assign({ docId: this.doc.docId }, this.edit))
        .then(res => this.afterAct(res, true))
        .catch(e => this.notify('처리 실패: ' + ((e.response && e.response.data && e.response.data.resultMessage) || ''), 'error'));
    },
    rowClass(item) {
      return this.doc && this.doc.docId === item.docId ? 'row-selected' : '';
    },
    statColor(s) {
      return { PROGRESS: 'blue', APPROVED: 'green', REJECTED: 'red', CANCELED: 'grey' }[s] || 'grey';
    },
    statLabel(d) {
      if (d.docStat === 'PROGRESS') return '진행 ' + d.curStepNo + '/' + d.stepCnt;
      return { APPROVED: '승인', REJECTED: '반려', CANCELED: '취소' }[d.docStat] || d.docStat;
    },
    actLabel(s) {
      return { WAIT: '대기', APPROVED: '승인', REJECTED: '반려' }[s] || s;
    },
    changeTypeLabel(t) {
      return dmChangeTypeLabel(t);
    },
    pathOf(i) {
      return [i.objOwner, i.objNm, i.attrNm || i.constraintNm].filter(x => x).join('.');
    },
    fmtDt(dt) {
      if (!dt || dt.length < 12) return dt || '';
      return dt.substr(0, 4) + '-' + dt.substr(4, 2) + '-' + dt.substr(6, 2) + ' ' + dt.substr(8, 2) + ':' + dt.substr(10, 2);
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>

<style scoped>
.sec-title { font-size: .85rem; font-weight: 600; color: #1A237E; margin: 4px 0; }
.cur-step { background: #E3F2FD; }
.agree { color: #6A1B9A; font-size: .75rem; }
.act-APPROVED { color: #2E7D32; font-weight: 600; }
.act-REJECTED { color: #C62828; font-weight: 600; }
.act-WAIT { color: #78909C; }
.v-data-table >>> .row-selected { background: #E8EAF6 !important; }
</style>
