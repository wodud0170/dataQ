<!--
  104번 WP2 — 역할 및 권한 (사용자·권한 관리 권한)

  역할은 권한 코드의 묶음이다. 관리자(사용자 화면의 '관리자')는 모든 권한을 갖고,
  관리자가 아닌 사용자는 받은 역할의 권한만 더 갖는다. 일반 사용자가 원래 할 수 있던 일은 역할과 상관없다.
  'DB 반영' 처럼 시스템 단위 권한은 사용자에게 역할을 줄 때 시스템을 골라 그 시스템에만 줄 수 있다.
  내장 역할(표준 담당·모델 담당·DB 담당)은 고칠 수 있지만 지울 수 없다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        역할은 권한의 묶음입니다. 관리자는 모든 권한을 갖고, 관리자가 아닌 사용자는 받은 역할의 권한만 더 갖습니다.
        시스템별 권한(DB 반영)은 사용자에게 역할을 줄 때 시스템을 골라 그 시스템에만 줄 수 있습니다.
      </span>
    </v-sheet>

    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <v-spacer />
      <v-btn small color="primary" @click="openAdd">역할 추가</v-btn>
      <v-btn small :disabled="!selected" @click="openEdit">수정</v-btn>
      <v-btn small color="error" :disabled="!selected || selected.builtinYn === 'Y'" @click="confirmDelete">삭제</v-btn>
    </v-sheet>

    <v-row no-gutters style="flex:1; min-height:0;">
      <v-col cols="12" md="6" class="pr-md-2 mb-2">
        <v-data-table :headers="headers" :items="roles" dense hide-default-footer
          :items-per-page="-1" item-key="roleId" single-select show-select
          v-model="selectedRows" class="elevation-0" style="border:1px solid #e0e0e0; border-radius:4px;"
          no-data-text="역할이 없습니다.">
          <template v-slot:item.roleNm="{ item }">
            {{ item.roleNm }}
            <v-chip v-if="item.builtinYn === 'Y'" x-small class="ml-1" color="grey lighten-2">내장</v-chip>
          </template>
          <template v-slot:item.permCnt="{ item }">{{ (item.perms || []).length }}</template>
        </v-data-table>
      </v-col>

      <v-col cols="12" md="6" class="pl-md-2">
        <v-card v-if="selected" outlined class="pa-3">
          <div class="text-subtitle-2 font-weight-bold mb-1">{{ selected.roleNm }}</div>
          <div v-if="selected.roleDesc" class="text-caption grey--text mb-2">{{ selected.roleDesc }}</div>

          <div class="text-caption font-weight-bold mt-2 mb-1">권한</div>
          <div class="d-flex flex-wrap" style="gap:6px;">
            <span v-if="(selected.perms || []).length === 0" class="text-caption grey--text">권한이 없습니다.</span>
            <v-chip v-for="c in selected.perms" :key="c" small outlined color="indigo">{{ permLabel(c) }}</v-chip>
          </div>

          <div class="d-flex align-center mt-4 mb-1">
            <span class="text-caption font-weight-bold">이 역할을 받은 사용자 ({{ roleUsers.length }})</span>
            <v-spacer />
            <v-btn x-small color="primary" @click="openAssign">사용자 지정</v-btn>
          </div>
          <v-simple-table dense>
            <template v-slot:default>
              <thead>
                <tr><th>계정</th><th>이름</th><th>범위</th><th style="width:60px;"></th></tr>
              </thead>
              <tbody>
                <tr v-if="roleUsers.length === 0"><td colspan="4" class="grey--text text-center">지정된 사용자가 없습니다.</td></tr>
                <tr v-for="u in roleUsers" :key="u.userId + u.sysCd">
                  <td>{{ u.userId }}</td>
                  <td>{{ u.userNm }}</td>
                  <td>{{ u.sysCd === '*' ? '모든 시스템' : (u.sysNm || u.sysCd) }}</td>
                  <td><v-btn x-small text color="error" @click="unassign(u)">해제</v-btn></td>
                </tr>
              </tbody>
            </template>
          </v-simple-table>
        </v-card>
        <v-card v-else outlined class="pa-6 text-center grey--text">
          <v-icon large color="grey lighten-1">mdi-shield-account-outline</v-icon>
          <div class="mt-2">역할을 선택하면 권한과 사용자를 확인할 수 있습니다.</div>
        </v-card>
      </v-col>
    </v-row>

    <!-- 역할 추가 / 수정 -->
    <v-dialog max-width="640" v-model="modal" scrollable>
      <v-card>
        <v-card-title>{{ editing ? '역할 수정' : '역할 추가' }}</v-card-title>
        <v-card-text style="max-height:70vh;">
          <v-text-field v-model="form.roleNm" label="역할 이름" dense
            :rules="[() => !!form.roleNm || '역할 이름은 필수입니다.']" />
          <v-text-field v-model="form.roleDesc" label="설명" dense />
          <div class="text-caption font-weight-bold mt-2 mb-1">권한</div>
          <div v-for="p in permCatalog" :key="p.code" class="d-flex align-start mb-1">
            <v-checkbox v-model="form.perms" :value="p.code" dense hide-details class="mt-0 pt-0" />
            <div>
              <div style="font-size:.875rem;">
                {{ p.label }}
                <v-chip v-if="p.systemScoped" x-small class="ml-1" color="orange lighten-4">시스템별</v-chip>
              </div>
              <div class="text-caption grey--text">{{ p.desc }}</div>
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="modal = false">취소</v-btn>
          <v-btn color="primary" :disabled="!form.roleNm" @click="save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 사용자 지정 -->
    <v-dialog max-width="480" v-model="assignModal">
      <v-card>
        <v-card-title>사용자 지정 — {{ selected && selected.roleNm }}</v-card-title>
        <v-card-text>
          <v-autocomplete v-model="assignForm.userId" :items="userOptions" item-text="label" item-value="id"
            label="사용자" dense no-data-text="사용자가 없습니다." />
          <v-select v-model="assignForm.sysCd" :items="systemOptions" item-text="label" item-value="sysCd"
            label="범위" dense />
          <div class="text-caption grey--text">
            범위는 시스템별 권한(DB 반영)에만 적용됩니다. 그 밖의 권한은 범위와 상관없이 모든 시스템에 적용됩니다.
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="assignModal = false">취소</v-btn>
          <v-btn color="primary" :disabled="!assignForm.userId" @click="assign">지정</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMRoles',
  props: ['isMobile'],
  data() {
    return {
      roles: [],
      permCatalog: [],
      selectedRows: [],
      roleUsers: [],
      modal: false,
      editing: false,
      form: { roleId: null, roleNm: '', roleDesc: '', perms: [] },
      assignModal: false,
      assignForm: { userId: null, sysCd: '*' },
      users: [],
      systems: [],
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
      headers: [
        { text: '역할', value: 'roleNm' },
        { text: '권한', value: 'permCnt', width: '70px', align: 'center', sortable: false },
        { text: '사용자', value: 'userCnt', width: '90px', align: 'center' },
      ],
    };
  },
  computed: {
    selected() { return this.selectedRows.length ? this.selectedRows[0] : null; },
    userOptions() {
      return this.users.map(u => ({ id: u.id, label: u.id + (u.name ? ' (' + u.name + ')' : '') }));
    },
    systemOptions() {
      return [{ sysCd: '*', label: '모든 시스템' }]
        .concat(this.systems.map(s => ({ sysCd: s.sysCd, label: s.sysNm })));
    },
  },
  watch: {
    selected(v) {
      this.roleUsers = [];
      if (v) this.loadRoleUsers(v.roleId);
    },
  },
  mounted() {
    this.loadCatalog();
    this.load();
  },
  methods: {
    contentsOf(res) {
      const c = res.data && res.data.contents;
      return c ? JSON.parse(c) : [];
    },
    loadCatalog() {
      axios.get(this.$APIURL.base + 'api/role/perms').then(res => {
        if (res.data && res.data.resultCode === 200) this.permCatalog = this.contentsOf(res);
      }).catch(() => this.notify('권한 목록 조회 실패', 'error'));
    },
    load(keepRoleId) {
      axios.post(this.$APIURL.base + 'api/role/list', {}).then(res => {
        if (!(res.data && res.data.resultCode === 200)) {
          this.notify('역할 목록 조회 실패: ' + (res.data && res.data.resultMessage), 'error');
          return;
        }
        this.roles = this.contentsOf(res);
        const keep = keepRoleId && this.roles.find(r => r.roleId === keepRoleId);
        this.selectedRows = keep ? [keep] : [];
      }).catch(() => this.notify('역할 목록 조회 실패', 'error'));
    },
    loadRoleUsers(roleId) {
      axios.post(this.$APIURL.base + 'api/role/users', { roleId: roleId }).then(res => {
        if (res.data && res.data.resultCode === 200) this.roleUsers = this.contentsOf(res);
      }).catch(() => this.notify('사용자 목록 조회 실패', 'error'));
    },
    permLabel(code) {
      const p = this.permCatalog.find(x => x.code === code);
      return p ? p.label : code;
    },
    openAdd() {
      this.editing = false;
      this.form = { roleId: null, roleNm: '', roleDesc: '', perms: [] };
      this.modal = true;
    },
    openEdit() {
      if (!this.selected) return;
      this.editing = true;
      this.form = {
        roleId: this.selected.roleId,
        roleNm: this.selected.roleNm,
        roleDesc: this.selected.roleDesc,
        perms: (this.selected.perms || []).slice(),
      };
      this.modal = true;
    },
    save() {
      axios.post(this.$APIURL.base + 'api/role/save', this.form).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.modal = false;
          this.load(res.data.contents);
          this.$loadPerms(true);
        } else {
          this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    confirmDelete() {
      if (!this.selected) return;
      const n = this.selected.userCnt || 0;
      const msg = n > 0
        ? "'" + this.selected.roleNm + "' 역할을 받은 사용자가 " + n + '명 있습니다. 삭제하면 그 사용자들에게서도 이 역할이 빠집니다. 계속할까요?'
        : "'" + this.selected.roleNm + "' 역할을 삭제합니다. 계속할까요?";
      this.$swal.fire({
        title: msg, icon: 'warning', showCancelButton: true,
        confirmButtonText: '삭제', cancelButtonText: '취소',
      }).then(r => { if (r.isConfirmed) this.remove(); });
    },
    remove() {
      axios.post(this.$APIURL.base + 'api/role/delete', { roleId: this.selected.roleId }).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('삭제했습니다.', 'success');
          this.load();
          this.$loadPerms(true);
        } else {
          this.notify('삭제 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    openAssign() {
      this.assignForm = { userId: null, sysCd: '*' };
      this.assignModal = true;
      if (this.users.length === 0) {
        axios.get(this.$APIURL.base + 'api/login/getUserList').then(res => {
          this.users = res.data || [];
        }).catch(() => this.notify('사용자 목록 조회 실패', 'error'));
      }
      if (this.systems.length === 0) {
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList').then(res => {
          this.systems = res.data || [];
        }).catch(() => { /* 시스템이 없어도 '모든 시스템' 으로 지정할 수 있다 */ });
      }
    },
    assign() {
      const body = { userId: this.assignForm.userId, roleId: this.selected.roleId, sysCd: this.assignForm.sysCd };
      axios.post(this.$APIURL.base + 'api/role/assign', body).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('지정했습니다.', 'success');
          this.assignModal = false;
          this.load(this.selected.roleId);
          this.loadRoleUsers(this.selected.roleId);
        } else {
          this.notify('지정 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    unassign(u) {
      const body = { userId: u.userId, roleId: this.selected.roleId, sysCd: u.sysCd };
      axios.post(this.$APIURL.base + 'api/role/unassign', body).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('해제했습니다.', 'success');
          this.load(this.selected.roleId);
          this.loadRoleUsers(this.selected.roleId);
        } else {
          this.notify('해제 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>
