<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-folder-multiple-outline</v-icon>&nbsp;&nbsp;영역 관리</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="loadAll">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <v-tabs v-model="tab" class="mb-2">
        <v-tab>업무영역 (논리)</v-tab>
        <v-tab>주제영역 (물리)</v-tab>
      </v-tabs>

      <v-tabs-items v-model="tab">
        <!-- 업무영역 -->
        <v-tab-item>
          <v-sheet class="pa-2 hdr-logical-bg">
            <v-row no-gutters style="gap:8px;" align="center" class="mb-2">
              <v-btn small color="success" depressed v-on:click="openBiz(null)">
                <v-icon small left>mdi-plus</v-icon>업무영역 추가
              </v-btn>
            </v-row>
            <v-data-table :headers="bizHeaders" :items="bizList" item-key="bizAreaId"
              :items-per-page="20" hide-default-footer dense>
              <template #item.actions="{ item }">
                <v-btn x-small text v-on:click="openBiz(item)"><v-icon x-small>mdi-pencil</v-icon></v-btn>
                <v-btn x-small text color="error" v-on:click="delBiz(item)"><v-icon x-small>mdi-delete</v-icon></v-btn>
              </template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>

        <!-- 주제영역 -->
        <v-tab-item>
          <v-sheet class="pa-2 hdr-physical-bg">
            <v-row no-gutters style="gap:8px;" align="center" class="mb-2">
              <v-btn small color="success" depressed v-on:click="openSubj(null)">
                <v-icon small left>mdi-plus</v-icon>주제영역 추가
              </v-btn>
            </v-row>
            <v-data-table :headers="subjHeaders" :items="subjList" item-key="subjAreaId"
              :items-per-page="20" hide-default-footer dense>
              <template #item.actions="{ item }">
                <v-btn x-small text v-on:click="openSubj(item)"><v-icon x-small>mdi-pencil</v-icon></v-btn>
                <v-btn x-small text color="error" v-on:click="delSubj(item)"><v-icon x-small>mdi-delete</v-icon></v-btn>
              </template>
            </v-data-table>
          </v-sheet>
        </v-tab-item>
      </v-tabs-items>
    </v-sheet>

    <!-- 업무영역 다이얼로그 -->
    <v-dialog v-model="bizDialog" max-width="500" persistent>
      <v-card>
        <v-card-title>{{ bizForm.bizAreaId ? '업무영역 수정' : '업무영역 추가' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="bizForm.bizAreaNm" label="업무영역명 *" dense outlined />
          <v-text-field v-model="bizForm.bizAreaDesc" label="설명" dense outlined />
          <v-text-field v-model.number="bizForm.sortOrder" label="정렬 순서" type="number" dense outlined />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="bizDialog = false">취소</v-btn>
          <v-btn color="primary" v-on:click="saveBiz">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 주제영역 다이얼로그 -->
    <v-dialog v-model="subjDialog" max-width="500" persistent>
      <v-card>
        <v-card-title>{{ subjForm.subjAreaId ? '주제영역 수정' : '주제영역 추가' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="subjForm.subjAreaNm" label="주제영역명 *" dense outlined />
          <v-text-field v-model="subjForm.subjAreaDesc" label="설명" dense outlined />
          <v-text-field v-model.number="subjForm.sortOrder" label="정렬 순서" type="number" dense outlined />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="subjDialog = false">취소</v-btn>
          <v-btn color="primary" v-on:click="saveSubj">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMBizSubjArea',
  props: ['isMobile'],
  data: () => ({
    tab: 0,
    bizList: [],
    subjList: [],
    bizHeaders: [
      { text: '업무영역명', value: 'bizAreaNm' },
      { text: '설명', value: 'bizAreaDesc' },
      { text: '정렬 순서', value: 'sortOrder', width: '100px' },
      { text: '', value: 'actions', width: '100px', sortable: false },
    ],
    subjHeaders: [
      { text: '주제영역명', value: 'subjAreaNm' },
      { text: '설명', value: 'subjAreaDesc' },
      { text: '정렬 순서', value: 'sortOrder', width: '100px' },
      { text: '', value: 'actions', width: '100px', sortable: false },
    ],
    bizDialog: false,
    subjDialog: false,
    bizForm: { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 },
    subjForm: { subjAreaId: null, subjAreaNm: '', subjAreaDesc: '', sortOrder: 0 },
  }),
  mounted() { this.loadAll(); },
  activated() { this.loadAll(); },
  methods: {
    loadAll() {
      axios.post(this.$APIURL.base + 'api/area/biz/list', {}).then(r => { this.bizList = r.data || []; });
      axios.post(this.$APIURL.base + 'api/area/subj/list', {}).then(r => { this.subjList = r.data || []; });
    },
    openBiz(item) {
      this.bizForm = item
        ? { bizAreaId: item.bizAreaId, bizAreaNm: item.bizAreaNm, bizAreaDesc: item.bizAreaDesc || '', sortOrder: item.sortOrder || 0 }
        : { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 };
      this.bizDialog = true;
    },
    openSubj(item) {
      this.subjForm = item
        ? { subjAreaId: item.subjAreaId, subjAreaNm: item.subjAreaNm, subjAreaDesc: item.subjAreaDesc || '', sortOrder: item.sortOrder || 0 }
        : { subjAreaId: null, subjAreaNm: '', subjAreaDesc: '', sortOrder: 0 };
      this.subjDialog = true;
    },
    saveBiz() {
      if (!this.bizForm.bizAreaNm) { this.$swal.fire({ title: '업무영역명 필수', icon: 'warning' }); return; }
      axios.post(this.$APIURL.base + 'api/area/biz/save', this.bizForm).then(() => {
        this.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1000, showConfirmButton: false });
        this.bizDialog = false; this.loadAll();
      }).catch((e) => this.$swal.fire({ title: '저장 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
    },
    saveSubj() {
      if (!this.subjForm.subjAreaNm) { this.$swal.fire({ title: '주제영역명 필수', icon: 'warning' }); return; }
      axios.post(this.$APIURL.base + 'api/area/subj/save', this.subjForm).then(() => {
        this.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1000, showConfirmButton: false });
        this.subjDialog = false; this.loadAll();
      }).catch((e) => this.$swal.fire({ title: '저장 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
    },
    delBiz(item) {
      this.$swal.fire({ title: `'${item.bizAreaNm}' 삭제?`, icon: 'warning', showCancelButton: true, confirmButtonText: '삭제' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/area/biz/delete', { bizAreaId: item.bizAreaId }).then(() => { this.loadAll(); });
      });
    },
    delSubj(item) {
      this.$swal.fire({ title: `'${item.subjAreaNm}' 삭제?`, icon: 'warning', showCancelButton: true, confirmButtonText: '삭제' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/area/subj/delete', { subjAreaId: item.subjAreaId }).then(() => { this.loadAll(); });
      });
    },
  },
};
</script>

<style scoped>
.hdr-logical-bg { background: #E8F5E9; }
.hdr-physical-bg { background: #E3F2FD; }
</style>
