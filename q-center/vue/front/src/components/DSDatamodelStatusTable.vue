<template>
  <v-main>
    <!-- 조회 조건 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">데이터모델명</span>
        <v-autocomplete v-model="selectedModelId" :items="modelList"
          item-text="dataModelNm" item-value="dataModelId"
          @change="onModelChange" clearable dense outlined hide-details
          class="filterInput" :style="{ width: '200px' }" color="ndColor" placeholder="모델 선택">
        </v-autocomplete>
        <span class="filterLabel">테이블명</span>
        <v-text-field v-model="searchTable" @click:clear="searchTable=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <span class="filterLabel">테이블 한글명</span>
        <v-text-field v-model="searchTableKr" @click:clear="searchTableKr=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <v-btn class="gradient" v-on:click="load" :style="{ padding: '0 12px' }">조회</v-btn>
        <v-btn class="gradient" v-on:click="tableDataDownload" :disabled="dmTableAllItems.length === 0">다운로드</v-btn>
        <v-btn color="primary" :disabled="!selectedModelId" v-on:click="openAddObjDialog" :style="{ padding: '0 12px', marginLeft: '8px' }">테이블 추가</v-btn>
        <v-btn id="btn-upload-tables" color="deep-purple" outlined :disabled="!selectedModelId" v-on:click="triggerUploadTables" :style="{ padding: '0 12px' }">엑셀 업로드</v-btn>
        <v-btn id="btn-download-tables-template" color="deep-purple" text v-on:click="downloadTablesTemplate" :style="{ padding: '0 8px' }">양식 다운로드</v-btn>
        <input ref="uploadTablesInput" type="file" accept=".xlsx" style="display:none" @change="onTableFileSelected" />
      </v-row>
    </v-sheet>

    <!-- 엑셀 업로드 미리보기 다이얼로그 -->
    <v-dialog v-model="uploadDialog" max-width="1100" persistent>
      <v-card>
        <v-card-title>
          테이블 엑셀 업로드 미리보기
          <v-spacer />
          <span v-if="uploadSummary" style="font-size:.85rem;color:#455A64;">
            총 {{ uploadSummary.total }} / 등록 예정 {{ uploadSummary.toInsert }} / 스킵 {{ uploadSummary.skipped }} / 오류 {{ (uploadErrors || []).length }}
          </span>
        </v-card-title>
        <v-card-text>
          <v-alert v-if="uploadErrors && uploadErrors.length > 0" type="error" dense text>
            오류 {{ uploadErrors.length }}건 — 수정 후 다시 업로드하세요.
            <div v-for="(e, i) in uploadErrors.slice(0, 5)" :key="'e' + i" style="font-size:.8rem;">
              · {{ e.row }}행: {{ e.msg || e.reason }}
            </div>
            <div v-if="uploadErrors.length > 5" style="font-size:.8rem;">외 {{ uploadErrors.length - 5 }}건</div>
          </v-alert>
          <v-alert v-if="uploadWarnings && uploadWarnings.length > 0" type="warning" dense text>
            경고 {{ uploadWarnings.length }}건
            <div v-for="(w, i) in uploadWarnings.slice(0, 5)" :key="'w' + i" style="font-size:.8rem;">
              · {{ w.row }}행: {{ w.msg || w.reason }}
            </div>
            <div v-if="uploadWarnings.length > 5" style="font-size:.8rem;">외 {{ uploadWarnings.length - 5 }}건</div>
          </v-alert>
          <v-data-table :items="uploadRows" :headers="uploadRowHeaders" dense
            :items-per-page="20" class="preview-grid"
            :item-class="rowClass">
            <template #[`item._action`]="{ item }">
              <span v-if="item._action === 'INSERT'" style="color:#2E7D32;font-weight:600;">등록</span>
              <span v-else-if="item._action === 'SKIP'" style="color:#F57C00;">스킵</span>
              <span v-else-if="item._action === 'ERROR'" style="color:#D32F2F;font-weight:600;">오류</span>
              <span v-else>{{ item._action }}</span>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="uploadDialog = false">취소</v-btn>
          <v-btn id="btn-upload-tables-commit" color="primary"
            :disabled="!uploadSummary || (uploadErrors && uploadErrors.length > 0) || uploadSummary.toInsert === 0"
            :loading="uploadCommitting" @click="commitTablesUpload">
            {{ uploadSummary ? uploadSummary.toInsert : 0 }}건 등록 실행
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 테이블 추가/수정 다이얼로그 -->
    <v-dialog v-model="objDialog" max-width="600" persistent>
      <v-card>
        <v-card-title>{{ objDialogMode === 'add' ? '테이블 추가' : '테이블 수정' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="objForm.objNm" label="테이블명 (물리명, 예: TB_USER)" :disabled="objDialogMode === 'edit'"
            hint="영문/숫자/언더바" persistent-hint outlined dense />
          <v-text-field v-model="objForm.objNmKr" label="테이블 한글명 (논리명)" outlined dense />
          <v-text-field v-model="objForm.objOwner" label="소유자 (스키마)" outlined dense />
          <v-text-field v-model="objForm.objDesc" label="테이블 설명" outlined dense />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="objDialog = false">취소</v-btn>
          <v-btn color="primary" @click="submitObj">{{ objDialogMode === 'add' ? '추가' : '수정' }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 목록 카운트 + 페이지 크기 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ dmTableItems.length }}건</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 테이블 목록 -->
    <v-data-table id="dmTable_table" :headers="dmTabledetaileHeaders" :items="dmTableItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="objNm" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">
      <template #[`item.objNm`]="{ item }">
        <a class="ndColor--text" style="cursor:pointer; text-decoration:underline;" @click="goToColumn(item)">{{ item.objNm }}</a>
      </template>
      <template #[`item.actions`]="{ item }">
        <v-btn icon small :disabled="!selectedModelId" @click="openEditObjDialog(item)" title="수정">
          <v-icon small>mdi-pencil</v-icon>
        </v-btn>
        <v-btn icon small :disabled="!selectedModelId" @click="deleteObj(item)" title="삭제">
          <v-icon small color="error">mdi-delete</v-icon>
        </v-btn>
      </template>
      <template #top>
        <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loadTable" class="text-center">데이터가 존재하지 않습니다.</v-alert>
        <span v-show="loadTable">잠시만 기다려주세요.</span>
      </template>
    </v-data-table>

    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-2 pb-2 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
          next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSDatamodelStatusTable',
  props: ['isMobile'],
  watch: {
    dmTableItems() {
      this.pageCount = Math.ceil(this.dmTableItems.length / this.itemsPerPage);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.dmTableItems.length / this.itemsPerPage);
    },
  },
  data: () => ({
    modelList: [],
    dmTableAllItems: [],
    selectedModelId: null,
    searchTable: '',
    searchTableKr: '',
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    dmTabledetaileHeaders: [
      { text: '테이블명', align: 'center', sortable: false, value: 'objNm' },
      { text: '테이블 한글명', sortable: false, align: 'center', value: 'objNmKr' },
      { text: '소유자', sortable: false, align: 'center', value: 'objOwner' },
      { text: '컬럼개수', sortable: false, align: 'center', value: 'objAttrCnt' },
      { text: '테이블 설명', sortable: false, align: 'center', value: 'objDesc' },
      { text: '편집', align: 'center', sortable: false, value: 'actions', width: '100px' },
    ],
    objDialog: false,
    objDialogMode: 'add',
    objForm: { objNm: '', objNmKr: '', objOwner: '', objDesc: '' },
    // 엑셀 업로드
    uploadDialog: false,
    uploadFile: null,
    uploadRows: [],
    uploadErrors: [],
    uploadWarnings: [],
    uploadSummary: null,
    uploadCommitting: false,
    uploadRowHeaders: [
      { text: '행', value: 'row', align: 'center', sortable: false, width: '60px' },
      { text: '상태', value: '_action', align: 'center', sortable: false, width: '70px' },
      { text: '소유자', value: 'objOwner', align: 'center', sortable: false, width: '110px' },
      { text: '테이블명(한글)', value: 'objNmKr', align: 'center', sortable: false },
      { text: '설명', value: 'objDesc', sortable: false },
      { text: '메시지', value: '_msg', sortable: false },
    ],
  }),
  computed: {
    dmTableItems() {
      return this.dmTableAllItems.filter(item => {
        const nm = !this.searchTable || (item.objNm || '').includes(this.searchTable);
        const nmKr = !this.searchTableKr || (item.objNmKr || '').includes(this.searchTableKr);
        return nm && nmKr;
      });
    },
  },
  methods: {
    getModelList() {
      axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
        'schNm': null, 'schSysNm': null
      }).then((res) => {
        this.modelList = res.data.map(item => ({
          dataModelId: item.dataModelId,
          dataModelNm: item.dataModelNm,
        }));
      }).catch(() => {
        this.$swal.fire({ title: '데이터 모델 목록 로드 실패', confirmButtonText: '확인', icon: 'error' });
      });
    },
    onModelChange(modelId) {
      this.dmTableAllItems = [];
      if (!modelId) return;
      this.load();
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loadTable = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.dmTableAllItems = res.data;
        this.loadTable = false;
      }).catch(() => {
        this.$swal.fire({ title: '테이블 정보 로드 실패', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
    },
    _applyPendingView(pending) {
      var self = this;
      var apply = function() {
        self.selectedModelId = pending.modelId;
        self.$nextTick(function() { self.load(); });
      };
      if (this.modelList.length > 0) {
        apply();
      } else {
        axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
          'schNm': null
        }).then(function(res) {
          self.modelList = res.data.map(function(item) {
            return { dataModelId: item.dataModelId, dataModelNm: item.dataModelNm };
          });
          apply();
        });
      }
    },
    goToColumn(item) {
      eventBus.pendingColumnView = {
        modelId: this.selectedModelId,
        clctId: this.selectedClctId,
        tableNm: item.objNm,
      };
      eventBus.$emit('openColumnView');
    },
    tableDataDownload() {
      axios.get(this.$APIURL.base + "api/dm/downloadDataModelObjs", {
        params: { 'clctId': this.selectedClctId },
        responseType: 'blob',
        headers: { "Accept": "application/vnd.ms-excel" }
      }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/csv" }));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", `데이터모델_테이블정보_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(url);
        link.remove();
      }).catch(() => {
        this.$swal.fire({ title: '테이블 정보 다운로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
      });
    },
    openAddObjDialog() {
      this.objDialogMode = 'add';
      this.objForm = { objNm: '', objNmKr: '', objOwner: '', objDesc: '' };
      this.objDialog = true;
    },
    openEditObjDialog(item) {
      this.objDialogMode = 'edit';
      this.objForm = {
        objNm: item.objNm, objNmKr: item.objNmKr || '',
        objOwner: item.objOwner || '', objDesc: item.objDesc || '',
      };
      this.objDialog = true;
    },
    submitObj() {
      if ((!this.objForm.objNm || !this.objForm.objNm.trim()) && (!this.objForm.objNmKr || !this.objForm.objNmKr.trim())) {
        this.$swal.fire({ title: '테이블명(물리명) 또는 한글명(논리명) 중 하나는 입력해야 합니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      const url = this.objDialogMode === 'add' ? 'api/dm/addObj' : 'api/dm/updateObj';
      const payload = { ...this.objForm, dataModelId: this.selectedModelId };
      axios.post(this.$APIURL.base + url, payload).then((res) => {
        if (res.data && res.data.resultCode === 200) {
          this.$swal.fire({ title: this.objDialogMode === 'add' ? '테이블이 추가되었습니다.' : '테이블이 수정되었습니다.', confirmButtonText: '확인', icon: 'success' });
          this.objDialog = false;
          this.load();
        } else {
          this.$swal.fire({ title: '저장 실패', text: (res.data && res.data.resultMessage) || '저장 중 오류', confirmButtonText: '확인', icon: 'error' });
        }
      }).catch((e) => {
        this.$swal.fire({ title: '저장 실패', text: e.message, confirmButtonText: '확인', icon: 'error' });
      });
    },
    // ===== 엑셀 업로드 =====
    downloadTablesTemplate() {
      const url = this.$APIURL.base + 'api/dm/uploadTemplate?scope=tables';
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'dataq_tables_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    triggerUploadTables() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델을 먼저 선택하세요.', icon: 'warning' });
        return;
      }
      this.$refs.uploadTablesInput.value = '';
      this.$refs.uploadTablesInput.click();
    },
    onTableFileSelected(e) {
      const f = e.target.files && e.target.files[0];
      if (!f) return;
      this.uploadFile = f;
      this._runTablesUpload('preview');
    },
    _runTablesUpload(mode) {
      if (!this.uploadFile) return;
      if (mode === 'commit') this.uploadCommitting = true;
      const fd = new FormData();
      fd.append('file', this.uploadFile);
      fd.append('dataModelId', this.selectedModelId);
      fd.append('mode', mode);
      axios.post(this.$APIURL.base + 'api/dm/uploadTables', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((res) => {
        if (!(res.data && res.data.resultCode === 200)) {
          this.$swal.fire({ title: '업로드 실패', text: (res.data && res.data.resultMessage) || '서버 오류', icon: 'error' });
          this.uploadCommitting = false;
          return;
        }
        let payload = {};
        try {
          const c = res.data.contents;
          payload = typeof c === 'string' ? JSON.parse(c) : (c || {});
        } catch (err) { payload = {}; }
        this.uploadRows = payload.tables || [];
        this.uploadErrors = payload.errors || [];
        this.uploadWarnings = payload.warnings || [];
        this.uploadSummary = payload.summary || null;
        if (mode === 'commit') {
          this.uploadCommitting = false;
          this.uploadDialog = false;
          const inserted = (this.uploadSummary && this.uploadSummary.toInsert) || 0;
          this.$swal.fire({ title: inserted + '건 등록 완료', icon: 'success', timer: 1500, showConfirmButton: false });
          this.load();
        } else {
          this.uploadDialog = true;
        }
      }).catch((err) => {
        this.uploadCommitting = false;
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || err.message || '업로드 실패';
        this.$swal.fire({ title: '업로드 실패', text: msg, icon: 'error' });
      });
    },
    commitTablesUpload() {
      this._runTablesUpload('commit');
    },
    rowClass(item) {
      if (!item) return '';
      if (item._action === 'ERROR') return 'row-upload-error';
      if (item._action === 'SKIP') return 'row-upload-skip';
      return '';
    },
    deleteObj(item) {
      this.$swal.fire({
        title: '테이블을 삭제할까요?',
        text: `${item.objNm} 및 하위 컬럼이 함께 삭제됩니다.`,
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소', icon: 'warning',
      }).then((r) => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + "api/dm/deleteObj", {
          dataModelId: this.selectedModelId, objNm: item.objNm,
        }).then((res) => {
          if (res.data && res.data.resultCode === 200) {
            this.$swal.fire({ title: '삭제되었습니다.', confirmButtonText: '확인', icon: 'success' });
            this.load();
          } else {
            this.$swal.fire({ title: '삭제 실패', text: (res.data && res.data.resultMessage) || '', confirmButtonText: '확인', icon: 'error' });
          }
        });
      });
    },
  },
  created() {
    this.getModelList();
  },
  mounted() {
    this.$resizableGrid();
  },
  activated() {
    if (eventBus.pendingTableView) {
      var pending = eventBus.pendingTableView;
      eventBus.pendingTableView = null;
      this._applyPendingView(pending);
    }
  },
}
</script>

<style scoped>
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }
.tableSpt { display: flex; justify-content: space-between; align-items: center; padding: 6px 20px; background: #FAFBFF; }
.split_bottom_wrap { position: absolute; width: 100%; max-height: 60px; bottom: 0px; border-top: 1px solid #E8EAF6; background: #FAFBFF; }
.pagination_wrap { position: relative; width: 100%; }
#dmTable_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
.row-upload-error > td { background-color: #FFEBEE !important; }
.row-upload-skip > td { background-color: #FFF8E1 !important; }
.preview-grid { border: 1px solid #E0E0E0; }
</style>
