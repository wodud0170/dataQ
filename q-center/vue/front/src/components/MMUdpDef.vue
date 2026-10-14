<!--
  104번 WP8-31·75 — 사용자 정의 속성 (관리 > 사용자 정의 속성)

  데이터 모델 테이블에 기관이 더하는 관리 항목(보존기간·데이터 볼륨 등)을 정의한다.
  필수로 두면 테이블 추가·수정·엑셀 업로드가 그 값 없이 저장되지 않는다.
  값이 있는 속성은 지우지 않고 사용 중지한다 (값은 남고 화면·검사에서 빠진다).
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        데이터 모델 > 테이블의 추가·수정 창과 엑셀 양식에 들어가는 관리 항목입니다.
        필수 항목을 비우면 테이블을 저장하지 않습니다. 값이 있는 항목은 삭제 대신 사용 중지합니다.
      </span>
    </v-sheet>

    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <v-switch id="udpShowAll" v-model="showAll" dense hide-details class="mt-0" label="사용 중지 포함" @change="load" />
      <v-spacer />
      <v-btn id="udpAdd" small class="btn-auto" color="primary" :disabled="!canManage" @click="openAdd">속성 추가</v-btn>
      <v-btn id="udpEdit" small class="btn-auto" :disabled="!canManage || !selected" @click="openEdit">수정</v-btn>
      <v-btn id="udpDelete" small class="btn-auto" color="error" :disabled="!canManage || !selected" @click="confirmDelete">삭제</v-btn>
    </v-sheet>

    <v-data-table id="udpTable" :headers="headers" :items="defs" dense hide-default-footer
      :items-per-page="-1" item-key="udpId" single-select show-select
      v-model="selectedRows" class="elevation-0">
      <template v-slot:item.udpNm="{ item }">
        <span class="udp-nm">{{ item.udpNm }}</span>
        <v-chip v-if="item.dfltSetYn === 'Y'" x-small class="ml-1">기본</v-chip>
      </template>
      <template v-slot:item.dataTp="{ item }">{{ tpLabel(item.dataTp) }}</template>
      <template v-slot:item.selectVals="{ item }">
        <span class="udp-desc">{{ (item.selectVals || '').split('\n').filter(v => v).join(' · ') }}</span>
      </template>
      <template v-slot:item.requiredYn="{ item }">
        <v-chip v-if="item.requiredYn === 'Y'" x-small color="error" text-color="white">필수</v-chip>
        <span v-else class="udp-desc">-</span>
      </template>
      <template v-slot:item.useYn="{ item }">
        <span :class="item.useYn === 'N' ? 'grey--text' : ''">{{ item.useYn === 'N' ? '사용 중지' : '사용' }}</span>
      </template>
    </v-data-table>

    <v-dialog max-width="560" v-model="modal" scrollable>
      <v-card>
        <v-card-title>{{ form.udpId ? '사용자 정의 속성 수정' : '사용자 정의 속성 추가' }}</v-card-title>
        <v-card-text class="pt-3">
          <v-text-field id="udpNm" v-model="form.udpNm" label="속성 이름 *" dense hide-details class="mt-2" />
          <div class="udp-desc mt-1 mb-4">엑셀 업로드에서 같은 이름의 열을 이 속성 값으로 읽습니다.</div>
          <v-select id="udpDataTp" v-model="form.dataTp" :items="tpOptions" item-text="text" item-value="value" label="유형" dense hide-details class="mb-4" />
          <v-textarea v-if="form.dataTp === 'SELECT'" id="udpSelectVals" v-model="form.selectValsText" label="선택값 (한 줄에 하나, 2개 이상)"
            rows="4" dense outlined hide-details class="mb-2" />
          <v-switch id="udpRequired" v-model="form.requiredYn" true-value="Y" false-value="N" dense hide-details
            label="필수 — 비우면 테이블을 저장하지 않음" />
          <v-switch id="udpUse" v-model="form.useYn" true-value="Y" false-value="N" dense hide-details label="사용" />
          <v-text-field id="udpSortOrder" v-model="form.sortOrder" label="순서" type="number" dense hide-details class="mt-6 mb-4" />
          <v-text-field id="udpDesc" v-model="form.udpDesc" label="설명" dense hide-details />
          <div class="udp-desc mt-1">테이블 추가·수정 창에서 이 속성 칸 아래 안내로 보입니다.</div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="modal = false">취소</v-btn>
          <v-btn id="udpSave" color="primary" :disabled="!form.udpNm" @click="save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

const TP = [
  { value: 'TEXT', text: '글' },
  { value: 'NUMBER', text: '숫자' },
  { value: 'DATE', text: '날짜' },
  { value: 'SELECT', text: '선택' },
];

export default {
  name: 'MMUdpDef',
  props: ['isMobile'],
  data() {
    return {
      defs: [],
      showAll: false,
      selectedRows: [],
      modal: false,
      form: {},
      tpOptions: TP,
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
      headers: [
        { text: '속성 이름', value: 'udpNm', width: '200px' },
        { text: '유형', value: 'dataTp', width: '80px', align: 'center' },
        { text: '선택값', value: 'selectVals', sortable: false },
        { text: '필수', value: 'requiredYn', width: '80px', align: 'center' },
        { text: '순서', value: 'sortOrder', width: '70px', align: 'center' },
        { text: '사용', value: 'useYn', width: '90px', align: 'center' },
        { text: '값 건수', value: 'valCnt', width: '90px', align: 'center' },
        { text: '설명', value: 'udpDesc', sortable: false },
      ],
    };
  },
  computed: {
    selected() { return this.selectedRows.length ? this.selectedRows[0] : null; },
    canManage() { return this.$can('UDP_ADMIN'); },
  },
  mounted() { this.load(); },
  activated() { this.load(); },
  methods: {
    tpLabel(v) {
      const t = TP.find(x => x.value === v);
      return t ? t.text : v;
    },
    load() {
      axios.post(this.$APIURL.base + 'api/udp/defs', { useOnly: this.showAll ? 'N' : 'Y' }).then(res => {
        this.defs = res.data || [];
        this.selectedRows = [];
      }).catch(() => this.notify('속성 목록 조회 실패', 'error'));
    },
    openAdd() {
      this.form = { udpId: null, udpNm: '', dataTp: 'TEXT', selectValsText: '', requiredYn: 'N', useYn: 'Y', sortOrder: (this.defs.length + 1), udpDesc: '' };
      this.modal = true;
    },
    openEdit() {
      if (!this.selected) return;
      const s = this.selected;
      this.form = {
        udpId: s.udpId, udpNm: s.udpNm, dataTp: s.dataTp, selectValsText: s.selectVals || '',
        requiredYn: s.requiredYn || 'N', useYn: s.useYn || 'Y', sortOrder: s.sortOrder, udpDesc: s.udpDesc || '',
      };
      this.modal = true;
    },
    save() {
      const body = {
        udpId: this.form.udpId, udpNm: this.form.udpNm, dataTp: this.form.dataTp,
        selectVals: (this.form.selectValsText || '').split('\n').map(v => v.trim()).filter(v => v),
        requiredYn: this.form.requiredYn, useYn: this.form.useYn, sortOrder: this.form.sortOrder, udpDesc: this.form.udpDesc,
      };
      axios.post(this.$APIURL.base + 'api/udp/save', body).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.modal = false;
          this.load();
        } else {
          this.$swal.fire({ title: '저장 실패', text: res.data && res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    confirmDelete() {
      if (!this.selected) return;
      const s = this.selected;
      this.$swal.fire({
        title: `'${s.udpNm}' 을 삭제할까요?`, icon: 'warning', showCancelButton: true,
        confirmButtonText: '삭제', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/udp/delete', { udpId: s.udpId }).then(res => {
          if (res.data && res.data.resultCode === 200) {
            this.notify('삭제했습니다.', 'success');
            this.load();
          } else {
            this.$swal.fire({ title: '삭제 실패', text: res.data && res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        }).catch(() => this.notify('서버 오류', 'error'));
      });
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>
<style scoped>
.udp-desc { font-size: .78rem; color: #78909C; }
.udp-nm { word-break: keep-all; }
</style>
