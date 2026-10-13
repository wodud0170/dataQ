<!--
  104번 WP1 — 조직 (관리 > 조직, 사용자·권한 관리 권한)

  부서 트리와 부서장, 부서원을 관리한다. 결재선의 "요청자 부서장" 이 이 정보를 쓴다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        결재선의 "요청자 부서장" 은 요청자 부서의 부서장입니다. 요청자가 부서장이면 상위 부서의 부서장에게 갑니다.
      </span>
    </v-sheet>

    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <v-btn small color="primary" @click="openNew(null)">최상위 부서 추가</v-btn>
      <v-btn small outlined color="primary" :disabled="!sel" @click="openNew(sel.orgId)">하위 부서 추가</v-btn>
      <v-spacer />
      <v-btn small outlined @click="load"><v-icon small left>mdi-refresh</v-icon>새로고침</v-btn>
    </v-sheet>

    <v-row no-gutters style="flex:1; min-height:0;">
      <v-col cols="12" md="4" class="pr-md-2" style="min-height:0; overflow:auto; border:1px solid #e0e0e0; border-radius:4px;">
        <v-treeview :items="tree" item-key="orgId" item-text="orgNm" activatable open-all dense
          :active.sync="active" return-object>
          <template v-slot:label="{ item }">
            {{ item.orgNm }} <span class="grey--text" style="font-size:.75rem;">({{ item.userCnt }}명<span v-if="item.headUserNm"> · 부서장 {{ item.headUserNm }}</span>)</span>
          </template>
        </v-treeview>
        <div v-if="tree.length === 0" class="pa-4 grey--text text-center" style="font-size:.8rem;">부서가 없습니다.</div>
      </v-col>

      <v-col cols="12" md="8" class="pl-md-2" style="min-height:0; overflow:auto;">
        <v-card v-if="form" outlined class="pa-3">
          <h3 style="font-size:1rem;" class="mb-2">{{ form.orgId ? '부서 수정' : '부서 추가' }}</h3>
          <v-text-field v-model="form.orgNm" label="부서 이름" dense counter="100" />
          <v-select v-model="form.pOrgId" :items="parentItems" label="상위 부서" dense clearable placeholder="최상위" />
          <v-autocomplete v-model="form.headUserId" :items="userItems" label="부서장" dense clearable
            hint="부서장은 이 부서 소속으로 바뀝니다" persistent-hint />
          <v-text-field v-model="form.sortOrd" label="정렬 순서" type="number" dense class="mt-2" />
          <div class="d-flex mt-2" style="gap:8px;">
            <v-btn v-if="form.orgId" small color="error" outlined @click="confirmDelete">삭제</v-btn>
            <v-spacer />
            <v-btn small color="primary" :disabled="!form.orgNm" @click="save">저장</v-btn>
          </div>

          <template v-if="form.orgId">
            <v-divider class="my-3" />
            <div class="d-flex align-center mb-1" style="gap:8px;">
              <strong style="font-size:.85rem;">부서원 ({{ members.length }})</strong>
              <v-spacer />
              <v-autocomplete v-model="addUserId" :items="userItems" dense hide-details clearable placeholder="사용자 추가" style="max-width:260px;" />
              <v-btn small outlined color="primary" :disabled="!addUserId" @click="assign(addUserId, form.orgId)">추가</v-btn>
            </div>
            <v-simple-table dense>
              <thead><tr><th>계정</th><th>이름</th><th>이메일</th><th style="width:60px;"></th></tr></thead>
              <tbody>
                <tr v-for="m in members" :key="m.userId">
                  <td>{{ m.userId }}<v-chip v-if="m.userId === form.headUserId" x-small class="ml-1" color="indigo" dark>부서장</v-chip></td>
                  <td>{{ m.userNm }}</td>
                  <td>{{ m.email }}</td>
                  <td><v-btn icon x-small color="error" title="부서에서 빼기" @click="assign(m.userId, null)"><v-icon small>mdi-close</v-icon></v-btn></td>
                </tr>
                <tr v-if="members.length === 0"><td colspan="4" class="grey--text text-center" style="font-size:.8rem;">부서원이 없습니다.</td></tr>
              </tbody>
            </v-simple-table>
          </template>
        </v-card>
        <div v-else class="pa-6 text-center grey--text" style="font-size:.85rem;">왼쪽에서 부서를 고르거나 부서를 추가하세요.</div>
      </v-col>
    </v-row>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3500">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMOrg',
  props: ['isMobile'],
  data() {
    return {
      orgs: [],
      active: [],
      form: null,
      members: [],
      users: [],
      addUserId: null,
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
    };
  },
  computed: {
    sel() { return this.active.length ? this.active[0] : null; },
    tree() {
      const byId = {};
      this.orgs.forEach(o => { byId[o.orgId] = Object.assign({}, o, { children: [] }); });
      const roots = [];
      Object.values(byId).forEach(o => {
        if (o.pOrgId && byId[o.pOrgId]) byId[o.pOrgId].children.push(o);
        else roots.push(o);
      });
      return roots;
    },
    parentItems() {
      return this.orgs.filter(o => !this.form || o.orgId !== this.form.orgId).map(o => ({ text: o.orgNm, value: o.orgId }));
    },
    userItems() { return this.users.map(u => ({ text: u.id + (u.name ? ' (' + u.name + ')' : ''), value: u.id })); },
  },
  watch: {
    sel(o) {
      if (!o) return;
      this.form = { orgId: o.orgId, orgNm: o.orgNm, pOrgId: o.pOrgId, headUserId: o.headUserId, sortOrd: o.sortOrd };
      this.loadMembers();
    },
  },
  mounted() {
    this.load();
    axios.get(this.$APIURL.base + 'api/login/getUserList').then(res => { this.users = Array.isArray(res.data) ? res.data : []; });
  },
  methods: {
    load() {
      axios.get(this.$APIURL.base + 'api/org/list').then(res => {
        this.orgs = Array.isArray(res.data) ? res.data : [];
      }).catch(() => this.notify('부서 조회 실패', 'error'));
    },
    loadMembers() {
      if (!this.form || !this.form.orgId) { this.members = []; return; }
      axios.get(this.$APIURL.base + 'api/org/users', { params: { orgId: this.form.orgId } }).then(res => {
        this.members = Array.isArray(res.data) ? res.data : [];
      });
    },
    openNew(pOrgId) {
      this.active = [];
      this.form = { orgId: null, orgNm: '', pOrgId: pOrgId, headUserId: null, sortOrd: 0 };
      this.members = [];
    },
    save() {
      axios.post(this.$APIURL.base + 'api/org/save', this.form).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.form.orgId = res.data.contents;
          this.load();
          this.loadMembers();
        } else {
          this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    confirmDelete() {
      this.$swal.fire({
        title: "'" + this.form.orgNm + "' 부서를 삭제할까요?", text: '부서원은 부서 없음으로 바뀝니다.',
        icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/org/delete', { orgId: this.form.orgId }).then(res => {
          if (res.data && res.data.resultCode === 200) {
            this.notify('삭제했습니다.', 'success');
            this.form = null;
            this.active = [];
            this.load();
          } else {
            this.notify('삭제 실패: ' + (res.data && res.data.resultMessage), 'error');
          }
        });
      });
    },
    assign(userId, orgId) {
      axios.post(this.$APIURL.base + 'api/org/assignUser', { userId, orgId }).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.addUserId = null;
          this.loadMembers();
          this.load();
        } else {
          this.notify('실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      });
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>
