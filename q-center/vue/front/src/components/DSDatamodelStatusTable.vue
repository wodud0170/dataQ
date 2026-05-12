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
        <!-- 86번 #11 — 소유자 (테이블명 앞) -->
        <span class="filterLabel">소유자</span>
        <v-select v-model="searchOwnerMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchOwner" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }"
          placeholder="소유자(스키마)" />
        <span class="filterLabel">테이블 영문명 (물리)</span>
        <v-select v-model="searchTableMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchTable" @click:clear="searchTable=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <span class="filterLabel">테이블 한글명 (논리)</span>
        <v-select v-model="searchTableKrMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchTableKr" @click:clear="searchTableKr=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <v-btn class="gradient" v-on:click="load" :style="{ padding: '0 12px' }">조회</v-btn>
        <v-btn color="primary" :disabled="!selectedModelId" v-on:click="openAddObjDialog" :style="{ padding: '0 12px', marginLeft: '8px' }">테이블 추가</v-btn>
        <!-- 86번 #11 — 엑셀 드롭다운 (업로드/양식/다운로드) -->
        <v-menu offset-y>
          <template v-slot:activator="{ on, attrs }">
            <v-btn class="gradient" v-bind="attrs" v-on="on" :disabled="!selectedModelId">
              <v-icon small left>mdi-file-excel</v-icon>엑셀
              <v-icon small right>mdi-menu-down</v-icon>
            </v-btn>
          </template>
          <v-list dense>
            <v-list-item id="btn-upload-tables" @click="triggerUploadTables">
              <v-list-item-icon><v-icon small>mdi-upload</v-icon></v-list-item-icon>
              <v-list-item-title>엑셀 업로드</v-list-item-title>
            </v-list-item>
            <v-list-item id="btn-download-tables-template" @click="downloadTablesTemplate">
              <v-list-item-icon><v-icon small>mdi-file-download-outline</v-icon></v-list-item-icon>
              <v-list-item-title>양식 다운로드</v-list-item-title>
            </v-list-item>
            <v-list-item :disabled="dmTableAllItems.length === 0" @click="tableDataDownload">
              <v-list-item-icon><v-icon small>mdi-download</v-icon></v-list-item-icon>
              <v-list-item-title>데이터 다운로드</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
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
          <v-text-field v-model="objForm.objNm" label="테이블 영문명 (물리, 예: TB_USER)"
            :hint="objDialogMode === 'edit' && objForm.objNm !== objForm.origObjNm
              ? '물리명 변경 시 하위 컬럼·인덱스·제약조건의 참조까지 함께 갱신됩니다 (확인 단계 표시)'
              : '영문/숫자/언더바'"
            persistent-hint outlined dense />
          <v-text-field v-model="objForm.objNmKr" label="테이블 한글명 (논리)" outlined dense />
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
    <!-- 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → item-key 에 OWNER 포함, 기본 정렬도 OWNER 우선 -->
    <v-data-table id="dmTable_table" :headers="dmTabledetaileHeaders" :items="dmTableItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="_rowKey" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요."
      multi-sort :sort-by="['objOwner', 'objNm']" :sort-desc="[false, false]">
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
    // 86번 #11 — 검색 필드 (소유자 추가, 한·영 모두 모드 셀렉트)
    searchOwner: '',
    searchOwnerMode: 'contains',
    searchTable: '',
    searchTableMode: 'contains',
    searchTableKr: '',
    searchTableKrMode: 'contains',
    searchModeOptions: [
      { value: 'contains', label: '포함' },
      { value: 'exact',    label: '완전 일치' },
      { value: 'start',    label: '앞' },
      { value: 'end',      label: '뒤' },
    ],
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    dmTabledetaileHeaders: [
      { text: '소유자', sortable: true, align: 'center', value: 'objOwner', width: '110px' },
      { text: '테이블 영문명 (물리)', align: 'center', sortable: true, value: 'objNm' },
      { text: '테이블 한글명 (논리)', sortable: true, align: 'center', value: 'objNmKr' },
      { text: '컬럼개수', sortable: true, align: 'center', value: 'objAttrCnt' },
      { text: '테이블 설명', sortable: false, align: 'center', value: 'objDesc' },
      { text: '편집', align: 'center', sortable: false, value: 'actions', width: '100px' },
    ],
    objDialog: false,
    objDialogMode: 'add',
    objForm: { objNm: '', objNmKr: '', objOwner: '', objDesc: '', origObjNm: '' },
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
      { text: '테이블명(영문)', value: 'objNm', align: 'center', sortable: false, width: '180px' },
      { text: '테이블명(한글)', value: 'objNmKr', align: 'center', sortable: false },
      { text: '설명', value: 'objDesc', sortable: false },
      { text: '메시지', value: '_msg', sortable: false },
    ],
  }),
  computed: {
    dmTableItems() {
      // 86번 #11 — 소유자/영문/한글 매칭 + _rowKey (owner||objNm) 부여 (같은 obj_nm 다른 owner 동시 표시 가능)
      return this.dmTableAllItems
        .filter(item =>
          this._matchName(item.objOwner, this.searchOwner,   this.searchOwnerMode)
          && this._matchName(item.objNm,    this.searchTable,   this.searchTableMode)
          && this._matchName(item.objNmKr,  this.searchTableKr, this.searchTableKrMode)
        )
        .map(item => ({ ...item, _rowKey: (item.objOwner || '') + '' + item.objNm }));
    },
  },
  methods: {
    /** 86번 #11 — 검색 모드 매칭: contains/exact/start/end */
    _matchName(value, keyword, mode) {
      if (!keyword) return true;
      const v = (value || '').toLowerCase();
      const k = keyword.toLowerCase();
      if (mode === 'exact') return v === k;
      if (mode === 'start') return v.startsWith(k);
      if (mode === 'end')   return v.endsWith(k);
      return v.includes(k);
    },
    /** 86번 #11 — 백엔드 raw exception 차단, 친화적 메세지 변환 */
    _friendlyErrText(err, fallback) {
      const status = (err && err.response && err.response.status) || 0;
      const data   = (err && err.response && err.response.data) || {};
      const our    = data.resultMessage;
      const raw    = data.message;
      const rawIsTechnical = raw && /JSON|deserialize|parse|MismatchedInput|HttpMessageNotReadable|Exception|NullPointer|invalid|cannot/i.test(raw);
      if (our) return our;
      if (raw && !rawIsTechnical) return raw;
      if (status >= 500) return (fallback || '서버 처리 중 오류가 발생했습니다.') + ' (관리자에게 문의해 주세요)';
      if (status === 400) return (fallback || '입력값이 올바르지 않습니다.');
      if (status === 401 || status === 403) return '권한이 없습니다.';
      if (status === 404) return '요청한 자원을 찾을 수 없습니다.';
      return fallback || (err && err.message) || '알 수 없는 오류가 발생했습니다.';
    },
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
      // 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → tableOwner 도 같이 전달
      eventBus.pendingColumnView = {
        modelId: this.selectedModelId,
        clctId: this.selectedClctId,
        tableOwner: item.objOwner || '',
        tableNm: item.objNm,
      };
      eventBus.$emit('openColumnView');
    },
    tableDataDownload() {
      // 86번 #11 — `selectedClctId` 가 이 컴포넌트에 없어서 항상 undefined 였던 버그.
      // CLCT 폐기 이후 매퍼는 DM_ID 기준이라 `selectedModelId` 로 변경.
      axios.get(this.$APIURL.base + "api/dm/downloadDataModelObjs", {
        params: { 'clctId': this.selectedModelId },
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
      this.objForm = { objNm: '', objNmKr: '', objOwner: '', objDesc: '', origObjNm: '' };
      this.objDialog = true;
    },
    openEditObjDialog(item) {
      this.objDialogMode = 'edit';
      this.objForm = {
        objNm: item.objNm, objNmKr: item.objNmKr || '',
        objOwner: item.objOwner || '', objDesc: item.objDesc || '',
        origObjNm: item.objNm, origObjOwner: item.objOwner || '',
      };
      this.objDialog = true;
    },
    async submitObj() {
      if ((!this.objForm.objNm || !this.objForm.objNm.trim()) && (!this.objForm.objNmKr || !this.objForm.objNmKr.trim())) {
        this.$swal.fire({ title: '테이블명(물리명) 또는 한글명(논리명) 중 하나는 입력해야 합니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      // 86번 #11 — 영문명(물리) 정규식 검증. 영문/숫자/언더바만 허용 + 영문/언더바로 시작.
      // (예전엔 백틱·공백·특수문자 통과 → DDL 생성 시 깨짐. SQL 예약어와 충돌도 잠재.)
      if (this.objForm.objNm && this.objForm.objNm.trim()) {
        const en = this.objForm.objNm.trim();
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(en)) {
          this.$swal.fire({
            title: '테이블 영문명(물리) 형식 오류',
            html: `<div style="text-align:left">
              영문(A-Z,a-z) / 숫자(0-9) / 언더바(_) 만 허용됩니다.<br>
              첫 글자는 영문 또는 언더바여야 합니다.<br><br>
              <span style="color:#D32F2F">입력값: <code>${en}</code></span>
            </div>`,
            icon: 'error',
            confirmButtonText: '확인',
          });
          return;
        }
        if (en.length > 128) {
          this.$swal.fire({ title: '테이블 영문명이 너무 깁니다 (최대 128자).', icon: 'error', confirmButtonText: '확인' });
          return;
        }
      }
      // 편집 모드 + 물리명 변경됨 → 영향 범위 사전 알림
      if (this.objDialogMode === 'edit' &&
          this.objForm.origObjNm &&
          this.objForm.objNm.trim() !== this.objForm.origObjNm) {
        const newNm = this.objForm.objNm.trim();
        try {
          const pv = await axios.get(this.$APIURL.base + 'api/dm/previewObjRename', {
            params: {
              dataModelId: this.selectedModelId,
              origObjNm:   this.objForm.origObjNm,
              newObjNm:    newNm,
            }
          });
          const d = pv.data || {};
          if (d.conflict) {
            this.$swal.fire({
              title: '이미 같은 이름의 테이블이 있습니다',
              text: `'${newNm}' 은(는) 이 모델 안에 이미 존재합니다. 다른 이름을 사용해주세요.`,
              icon: 'error', confirmButtonText: '확인',
            });
            return;
          }
          const html =
            `<div style="text-align:left; font-size:.9rem;">` +
            `<b>${this.objForm.origObjNm}</b> → <b>${newNm}</b> 로 변경합니다.<br><br>` +
            `함께 갱신되는 항목:<br>` +
            `· 컬럼 <b>${d.attrCnt || 0}</b>건<br>` +
            `· 인덱스 <b>${d.indexCnt || 0}</b>건<br>` +
            `· 제약조건 (이 테이블) <b>${d.constraintCnt || 0}</b>건<br>` +
            `· 외래키 참조 (다른 테이블 → 이 테이블) <b>${d.refConstraintCnt || 0}</b>건<br><br>` +
            `진행하시겠습니까?` +
            `</div>`;
          const confirm = await this.$swal.fire({
            title: '물리 테이블명 변경 확인',
            html, icon: 'warning',
            showCancelButton: true, confirmButtonText: '변경', cancelButtonText: '취소',
          });
          if (!confirm.isConfirmed) return;
        } catch (err) {
          this.$swal.fire({ title: '영향 범위 조회 실패', text: this._friendlyErrText(err, '영향 범위 조회 중 오류가 발생했습니다.'), icon: 'error', confirmButtonText: '확인' });
          return;
        }
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
          const s = this.uploadSummary || {};
          const inserted = s.toInsert || 0;
          const skipped = s.skipped || 0;
          const errs = (this.uploadErrors || []).length;
          const html = `<div style="text-align:left">
            등록: <b style="color:#2E7D32">${inserted}건</b><br>
            중복 스킵: <b style="color:#F57C00">${skipped}건</b><br>
            오류: <b style="color:#D32F2F">${errs}건</b>
          </div>`;
          this.$swal.fire({ title: '엑셀 업로드 완료', html: html, icon: 'success' });
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
