<!--
  104번 WP1 — 결재선 (관리 > 결재선, 사용자·권한 관리 권한)

  요청 유형(단어·용어·코드·도메인·데이터 모델 변경)마다 결재 단계를 정한다. 결재선을 쓰지 않는 유형은
  지금처럼 승인 권한자(표준 승인·모델 승인)가 한 번에 승인한다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        결재선을 쓰는 유형은 요청 때 결재 문서가 만들어지고, 단계마다 결재자가 승인해야 반영됩니다.
        결재자는 요청 때 정해집니다(요청자 본인은 빠지고, 찾지 못하면 관리자에게 갑니다).
      </span>
    </v-sheet>

    <v-row no-gutters style="flex:1; min-height:0;">
      <v-col cols="12" md="4" class="pr-md-2">
        <v-list dense class="elevation-0" style="border:1px solid #e0e0e0; border-radius:4px;">
          <v-list-item-group v-model="selIdx" mandatory color="primary">
            <v-list-item v-for="l in lines" :key="l.reqTp">
              <v-list-item-content>
                <v-list-item-title>{{ l.reqTpNm }}</v-list-item-title>
                <v-list-item-subtitle>
                  <span v-if="l.useYn === 'Y'">{{ l.steps.length }}단계 — {{ l.steps.map(stepSummary).join(' → ') }}</span>
                  <span v-else class="grey--text">결재선 없음 (승인 권한자 1단계)</span>
                </v-list-item-subtitle>
              </v-list-item-content>
              <v-list-item-action>
                <v-chip x-small :color="l.useYn === 'Y' ? 'primary' : 'grey'" dark>{{ l.useYn === 'Y' ? '사용' : '미사용' }}</v-chip>
              </v-list-item-action>
            </v-list-item>
          </v-list-item-group>
        </v-list>
      </v-col>

      <v-col cols="12" md="8" class="pl-md-2" style="min-height:0; overflow:auto;">
        <v-card v-if="form" outlined class="pa-3">
          <div class="d-flex align-center mb-2" style="gap:12px;">
            <h3 style="font-size:1rem;">{{ form.reqTpNm }} 결재선</h3>
            <v-spacer />
            <v-switch v-model="form.useYn" true-value="Y" false-value="N" dense hide-details label="결재선 사용" />
          </div>
          <v-text-field v-model="form.lineNm" label="결재선 이름 (선택)" dense counter="100" />

          <v-simple-table dense class="mt-2">
            <thead>
              <tr><th style="width:50px;">단계</th><th>단계 이름</th><th style="width:160px;">결재자 방식</th><th>결재자</th><th style="width:70px;">합의</th><th style="width:110px;"></th></tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in form.steps" :key="i">
                <td>{{ i + 1 }}</td>
                <td><v-text-field v-model="s.stepNm" dense hide-details placeholder="예) 팀장" /></td>
                <td>
                  <v-select v-model="s.aprvrTp" :items="aprvrTpItems" dense hide-details @change="s.aprvrId = null" />
                </td>
                <td>
                  <v-autocomplete v-if="s.aprvrTp === 'USER'" v-model="s.aprvrId" :items="userItems" dense hide-details placeholder="사용자 선택" />
                  <v-select v-else-if="s.aprvrTp === 'ROLE'" v-model="s.aprvrId" :items="roleItems" dense hide-details placeholder="역할 선택" />
                  <span v-else style="font-size:.8rem; color:#546E7A;">{{ s.aprvrTp === 'ADMIN' ? '관리자 전원' : '요청자 부서의 부서장' }}</span>
                </td>
                <td><v-checkbox v-model="s.agreeYn" true-value="Y" false-value="N" dense hide-details
                  :title="'합의: 이 단계 결재자 전원이 승인해야 다음 단계'" /></td>
                <td>
                  <v-btn icon x-small :disabled="i === 0" @click="move(i, -1)"><v-icon small>mdi-arrow-up</v-icon></v-btn>
                  <v-btn icon x-small :disabled="i === form.steps.length - 1" @click="move(i, 1)"><v-icon small>mdi-arrow-down</v-icon></v-btn>
                  <v-btn icon x-small color="error" @click="form.steps.splice(i, 1)"><v-icon small>mdi-close</v-icon></v-btn>
                </td>
              </tr>
              <tr v-if="form.steps.length === 0"><td colspan="6" class="grey--text text-center" style="font-size:.8rem;">단계가 없습니다.</td></tr>
            </tbody>
          </v-simple-table>
          <div style="font-size:.75rem; color:#78909C;" class="mt-1">
            합의에 체크하면 그 단계 결재자 전원이 승인해야 다음 단계로 넘어갑니다. 체크하지 않으면 한 명이 승인하면 넘어갑니다.
          </div>
          <div class="d-flex mt-3" style="gap:8px;">
            <v-btn small outlined color="primary" :disabled="form.steps.length >= 10" @click="addStep">단계 추가</v-btn>
            <v-spacer />
            <v-btn small text @click="reset">되돌리기</v-btn>
            <v-btn small color="primary" @click="save">저장</v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3500">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMAprvLine',
  props: ['isMobile'],
  data() {
    return {
      lines: [],
      selIdx: 0,
      form: null,
      users: [],
      roles: [],
      aprvrTpItems: [
        { text: '지정 사용자', value: 'USER' },
        { text: '역할', value: 'ROLE' },
        { text: '요청자 부서장', value: 'ORG_HEAD' },
        { text: '관리자 전원', value: 'ADMIN' },
      ],
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
    };
  },
  computed: {
    userItems() { return this.users.map(u => ({ text: u.id + (u.name ? ' (' + u.name + ')' : ''), value: u.id })); },
    roleItems() { return this.roles.map(r => ({ text: r.roleNm, value: r.roleId })); },
  },
  watch: {
    selIdx() { this.reset(); },
  },
  mounted() {
    this.load();
    axios.get(this.$APIURL.base + 'api/login/getUserList').then(res => { this.users = Array.isArray(res.data) ? res.data : []; });
    axios.post(this.$APIURL.base + 'api/role/list', {}).then(res => {
      if (res.data && res.data.resultCode === 200) this.roles = JSON.parse(res.data.contents);
    });
  },
  methods: {
    load(keepIdx) {
      axios.get(this.$APIURL.base + 'api/aprv/lines').then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.notify('조회 실패: ' + (res.data && res.data.resultMessage), 'error');
          return;
        }
        this.lines = JSON.parse(res.data.contents);
        if (!keepIdx) this.selIdx = 0;
        this.reset();
      }).catch(() => this.notify('결재선 조회 실패', 'error'));
    },
    reset() {
      const l = this.lines[this.selIdx];
      if (!l) { this.form = null; return; }
      this.form = {
        reqTp: l.reqTp, reqTpNm: l.reqTpNm, lineNm: l.lineNm || '', useYn: l.useYn,
        steps: l.steps.map(s => ({ stepNm: s.stepNm || '', aprvrTp: s.aprvrTp, aprvrId: s.aprvrId, agreeYn: s.agreeYn })),
      };
    },
    stepSummary(s) {
      const who = s.aprvrTp === 'ORG_HEAD' ? '부서장' : s.aprvrTp === 'ADMIN' ? '관리자' : (s.aprvrNm || s.aprvrId);
      return (s.stepNm ? s.stepNm + ':' : '') + who;
    },
    addStep() {
      this.form.steps.push({ stepNm: '', aprvrTp: 'ORG_HEAD', aprvrId: null, agreeYn: 'N' });
    },
    move(i, d) {
      const s = this.form.steps.splice(i, 1)[0];
      this.form.steps.splice(i + d, 0, s);
    },
    save() {
      axios.post(this.$APIURL.base + 'api/aprv/line/save', this.form).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.load(true);
        } else {
          this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>
