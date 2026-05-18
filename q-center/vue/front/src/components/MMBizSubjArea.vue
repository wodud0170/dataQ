<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <!-- 헤더 -->
      <v-row no-gutters align="center" class="mb-4">
        <h2><v-icon>mdi-folder-multiple-outline</v-icon>&nbsp;&nbsp;영역 관리</h2>
        <v-spacer />
        <v-btn small color="primary" outlined v-on:click="loadAll">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <!-- 업무영역 (논리) -->
      <v-row no-gutters align="center" class="mb-2">
        <span class="text-subtitle-1 font-weight-medium">업무영역 (논리)</span>
        <span class="text-caption grey--text ml-2">총 {{ bizList.length }}건</span>
        <v-spacer />
        <v-btn small color="success" depressed v-on:click="openBiz(null)">
          <v-icon small left>mdi-plus</v-icon>업무영역 추가
        </v-btn>
      </v-row>

      <v-data-table :headers="bizHeaders" :items="bizList" item-key="bizAreaId"
        :items-per-page="20" hide-default-footer dense class="elevation-1"
        no-data-text="등록된 업무영역이 없습니다.">
        <template #item.bizAreaDesc="{ item }">
          <span :class="{ 'grey--text': !item.bizAreaDesc }">{{ item.bizAreaDesc || '-' }}</span>
        </template>
        <template #item.actions="{ item }">
          <div class="d-flex justify-center" :style="{ gap: '4px' }">
            <v-btn icon small v-on:click="openBiz(item)" title="수정">
              <v-icon small color="ndColor">mdi-pencil</v-icon>
            </v-btn>
            <v-btn icon small v-on:click="delBiz(item)" title="삭제">
              <v-icon small color="error">mdi-delete</v-icon>
            </v-btn>
          </div>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 업무영역 다이얼로그 -->
    <v-dialog v-model="bizDialog" max-width="480" persistent>
      <v-card>
        <v-card-title class="text-subtitle-1">
          {{ bizForm.bizAreaId ? '업무영역 수정' : '업무영역 추가' }}
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <v-text-field v-model="bizForm.bizAreaNm" label="업무영역명 *" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-text-field v-model="bizForm.bizAreaDesc" label="설명" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-text-field v-model.number="bizForm.sortOrder" label="정렬 순서" type="number" dense outlined
            color="ndColor" hide-details="auto" />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="bizDialog = false">취소</v-btn>
          <v-btn color="ndColor" dark v-on:click="saveBiz">저장</v-btn>
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
    bizList: [],
    bizHeaders: [
      { text: '업무영역명', value: 'bizAreaNm', align: 'start' },
      { text: '설명', value: 'bizAreaDesc', align: 'start' },
      { text: '정렬 순서', value: 'sortOrder', width: '120px', align: 'center' },
      { text: '관리', value: 'actions', width: '120px', sortable: false, align: 'center' },
    ],
    bizDialog: false,
    bizForm: { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 },
  }),
  mounted() { this.loadAll(); },
  activated() { this.loadAll(); },
  methods: {
    loadAll() {
      axios.post(this.$APIURL.base + 'api/area/biz/list', {}).then(r => {
        // 응답이 배열일 때만 사용 — API 오류 시 fallback HTML 등 비배열이 들어오면 빈 목록 처리
        this.bizList = Array.isArray(r.data) ? r.data : [];
      }).catch(() => { this.bizList = []; });
    },
    openBiz(item) {
      this.bizForm = item
        ? { bizAreaId: item.bizAreaId, bizAreaNm: item.bizAreaNm, bizAreaDesc: item.bizAreaDesc || '', sortOrder: item.sortOrder || 0 }
        : { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 };
      this.bizDialog = true;
    },
    saveBiz() {
      if (!this.bizForm.bizAreaNm) { this.$swal.fire({ title: '업무영역명 필수', icon: 'warning' }); return; }
      axios.post(this.$APIURL.base + 'api/area/biz/save', this.bizForm).then(() => {
        this.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1000, showConfirmButton: false });
        this.bizDialog = false; this.loadAll();
      }).catch((e) => this.$swal.fire({ title: '저장 실패', text: (e.response && e.response.data && e.response.data.resultMessage) || '', icon: 'error' }));
    },
    delBiz(item) {
      this.$swal.fire({ title: `'${item.bizAreaNm}' 삭제?`, icon: 'warning', showCancelButton: true, confirmButtonText: '삭제' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/area/biz/delete', { bizAreaId: item.bizAreaId }).then(() => { this.loadAll(); });
      });
    },
  },
};
</script>
