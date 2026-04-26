<template>
  <v-main>
    <!-- 조회 조건 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <!-- Row 1: 모델 선택 -->
      <v-row :style="{ alignItems: 'center', margin: '0 0 6px 0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">데이터모델명</span>
        <v-autocomplete v-model="selectedModelId" :items="modelList"
          item-text="dataModelNm" item-value="dataModelId"
          @change="onModelChange" clearable dense outlined hide-details
          class="filterInput" :style="{ width: '200px' }" color="ndColor" placeholder="모델 선택">
        </v-autocomplete>
      </v-row>
      <!-- Row 2: 상세 검색 조건 -->
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">테이블명</span>
        <v-text-field v-model="searchTable" @click:clear="searchTable=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '100px' }">
        </v-text-field>
        <span class="filterLabel">컬럼명</span>
        <v-text-field v-model="searchColumn" @click:clear="searchColumn=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '100px' }">
        </v-text-field>
        <span class="filterLabel">컬럼 한글명</span>
        <v-text-field v-model="searchColumnKr" @click:clear="searchColumnKr=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '100px' }">
        </v-text-field>
        <v-checkbox class="checkboxStyle" hide-details v-model="showNonStandardOnly" label="비표준만 보기" color="error" dense></v-checkbox>
        <v-btn class="gradient" v-on:click="load" :style="{ padding: '0 12px' }">조회</v-btn>
        <v-btn class="gradient" v-on:click="columnDataDownload" :disabled="dmColumnAllItems.length === 0">다운로드</v-btn>
        <v-btn id="btn-upload-attrs" color="deep-purple" outlined :disabled="!selectedModelId" v-on:click="triggerUploadAttrs" :style="{ padding: '0 12px' }">엑셀 업로드</v-btn>
        <v-btn id="btn-download-attrs-template" color="deep-purple" text v-on:click="downloadAttrsTemplate" :style="{ padding: '0 8px' }">양식 다운로드</v-btn>
        <input ref="uploadAttrsInput" type="file" accept=".xlsx" style="display:none" @change="onAttrFileSelected" />
      </v-row>
      <!-- Row 3: 그리드 편집 툴바 -->
      <v-row :style="{ alignItems: 'center', margin: '6px 0 0 0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">추가 대상 테이블</span>
        <v-autocomplete v-model="addTargetObjNm" :items="objOptions" item-text="label" item-value="objNm"
          :disabled="!selectedModelId" dense outlined hide-details
          class="filterInput" :style="{ width: '220px' }" color="ndColor" placeholder="테이블 선택">
        </v-autocomplete>
        <v-btn id="btn-add-col-row" color="primary" :disabled="!selectedModelId || !addTargetObjNm || newRows.length >= 100"
          v-on:click="addEmptyRow" :style="{ padding: '0 12px' }">+ 컬럼 추가</v-btn>
        <v-btn id="btn-add-col-rows-10" color="primary" outlined :disabled="!selectedModelId || !addTargetObjNm || newRows.length >= 100"
          v-on:click="addEmptyRows(10)" :style="{ padding: '0 12px' }">+ 빈 행 10개</v-btn>
        <v-btn id="btn-save-attrs" color="success" :disabled="!selectedModelId || (newRows.length === 0 && pendingDeletes.length === 0)"
          v-on:click="saveAll" :style="{ padding: '0 12px' }">저장 ({{ newRows.length + pendingDeletes.length }})</v-btn>
        <v-btn id="btn-resolve-selected" color="indigo" outlined :disabled="selectedRows.length === 0"
          v-on:click="resolveSelected" :loading="resolving" :style="{ padding: '0 12px' }">선택 컬럼 물리모델 변환</v-btn>
        <v-btn color="error" outlined :disabled="selectedRows.length === 0"
          v-on:click="deleteSelected" :style="{ padding: '0 12px' }">선택 행 삭제</v-btn>
        <span v-if="newRows.length >= 100" class="ml-2" style="color:#D32F2F;font-size:.8rem;">
          100행 도달 — 대량 입력은 엑셀 업로드 사용
        </span>
        <span v-else-if="selectedModelId && addTargetObjNm" class="ml-2" style="color:#546E7A;font-size:.75rem;">
          엑셀에서 Ctrl+C 후 여기서 Ctrl+V — 여러 행 한 번에 추가 (열 순서: 한글명·NULL·PK·FK·기본값)
        </span>
      </v-row>
    </v-sheet>

    <!-- 목록 카운트 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ mergedItems.length }}건</span>
        <span v-if="newRows.length > 0" class="ml-2" style="color:#F57C00;">(미저장 {{ newRows.length }}건)</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 컬럼 목록 (인라인 편집) -->
    <v-data-table id="clTable_table" :headers="dmColumnDetaileHeaders" :items="mergedItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="_rowKey" show-select v-model="selectedRows"
      class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">

      <template #item.objNmKr="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item.objNmKr }}</span>
      </template>
      <template #item.objNm="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item.objNm }}</span>
      </template>

      <template #item.attrNmKr="{ item }">
        <v-text-field v-if="item._mode === 'add'" v-model="item.attrNmKr"
          :class="'inline-edit ' + (item._error ? 'inline-error' : '')"
          dense hide-details outlined flat solo single-line autofocus
          placeholder="컬럼 한글명" />
        <span v-else class="ndColor--text" :style="{ cursor: 'pointer', margin: '0px 8px' }" @click="showTermData(item)">
          <v-icon v-if="item.termsStndYn === 'N'" small color="error" class="mr-1"
            title="비표준 용어">mdi-alert-circle</v-icon>{{ item.attrNmKr }}
        </span>
      </template>

      <template #item.attrNm="{ item }">
        <span :style="{ margin: '0px 8px', color: (item._mode === 'add' || item.termsStndYn === 'N') ? '#9E9E9E' : 'inherit' }">
          {{ formatAttrNm(item) }}
        </span>
      </template>
      <template #item.dataType="{ item }">
        <span :style="{ margin: '0px 8px', color: item._mode === 'add' ? '#9E9E9E' : 'inherit' }">
          {{ item._mode === 'add' ? '-' : item.dataType }}
        </span>
      </template>
      <template #item.dataLen="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item._mode === 'add' ? '-' : item.dataLen }}</span>
      </template>
      <template #item.dataDecimalLen="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item._mode === 'add' ? '-' : item.dataDecimalLen }}</span>
      </template>

      <template #item.nullableYn="{ item }">
        <v-checkbox v-if="item._mode === 'add'" v-model="item.nullableYn"
          true-value="Y" false-value="N" dense hide-details class="ma-0 pa-0" />
        <span v-else :style="{ margin: '0px 8px' }">{{ item.nullableYn }}</span>
      </template>
      <template #item.pkYn="{ item }">
        <v-checkbox v-if="item._mode === 'add'" v-model="item.pkYn"
          true-value="Y" false-value="N" dense hide-details class="ma-0 pa-0" />
        <span v-else :style="{ margin: '0px 8px' }">{{ item.pkYn }}</span>
      </template>
      <template #item.fkYn="{ item }">
        <v-checkbox v-if="item._mode === 'add'" v-model="item.fkYn"
          true-value="Y" false-value="N" dense hide-details class="ma-0 pa-0" />
        <span v-else :style="{ margin: '0px 8px' }">{{ item.fkYn }}</span>
      </template>
      <template #item.defaultVal="{ item }">
        <v-text-field v-if="item._mode === 'add'" v-model="item.defaultVal"
          dense hide-details outlined flat solo single-line placeholder="-" />
        <span v-else :style="{ margin: '0px 8px' }">{{ item.defaultVal }}</span>
      </template>

      <template #item.termsStndYn="{ item }">
        <v-icon v-if="item._mode === 'add'" small color="grey">mdi-minus-circle-outline</v-icon>
        <v-icon v-else small :color="item.termsStndYn === 'Y' ? 'success' : 'error'">
          {{ item.termsStndYn === 'Y' ? 'mdi-check-circle' : 'mdi-close-circle' }}
        </v-icon>
      </template>

      <template #item.resolveReason="{ item }">
        <span v-if="item._resolveReason" style="color:#D32F2F;font-size:.75rem;">{{ item._resolveReason }}</span>
        <span v-else style="color:#9E9E9E;">-</span>
      </template>

      <template #item.actions="{ item }">
        <v-icon v-if="item._mode === 'add'" small color="error" @click="removeNewRow(item)">mdi-close</v-icon>
        <v-icon v-else small :disabled="!selectedModelId" @click="deleteOne(item)">mdi-delete</v-icon>
      </template>

      <template #top>
        <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loadTable">데이터가 존재하지 않습니다.</v-alert>
        <span v-show="loadTable">잠시만 기다려주세요.</span>
      </template>
    </v-data-table>

    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-2 pb-2 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
          next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>

    <!-- 엑셀 업로드 미리보기 다이얼로그 -->
    <v-dialog v-model="uploadDialog" max-width="1200" persistent>
      <v-card>
        <v-card-title>
          컬럼 엑셀 업로드 미리보기
          <v-spacer />
          <span v-if="uploadSummary" style="font-size:.85rem;color:#455A64;">
            총 {{ uploadSummary.total }} / 등록 예정 {{ uploadSummary.toInsertAttrs }} (FK {{ uploadSummary.toInsertFks }}) / 오류 {{ (uploadErrors || []).length }}
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
          </v-alert>
          <v-data-table :items="uploadRows" :headers="uploadAttrHeaders" dense
            :items-per-page="20" class="preview-grid" :item-class="rowClass">
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
          <v-btn id="btn-upload-attrs-commit" color="primary"
            :disabled="!uploadSummary || (uploadErrors && uploadErrors.length > 0) || uploadSummary.toInsertAttrs === 0"
            :loading="uploadCommitting" @click="commitAttrsUpload">
            {{ uploadSummary ? uploadSummary.toInsertAttrs : 0 }}건 등록 실행
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 용어 상세 정보 Modal -->
    <v-dialog max-width="800" v-model="termDataModalShow">
      <NdModal @hide="termDataModalShow = false" :footer-submit="false" header-title="용어 상세 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="term_detail_table" :items="termDetailItem" :loading="termLoading"
              hide-default-footer class="px-4 pb-3">
              <template v-slot:body="{ items }" v-if="termDetailItem.length !== 0">
                <tbody>
                  <tr v-for="header in termDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">{{ header.text }}</td>
                    <td v-for="item in items" :key="item.termNm">
                      <div v-if="Array.isArray(item[header.value])">
                        <div v-for="item2 in item[header.value]" :key="item2">{{ item2 }}</div>
                      </div>
                      <div v-else>{{ item[header.value] }}</div>
                    </td>
                  </tr>
                </tbody>
              </template>
              <template #top>
                <v-progress-linear v-show="termLoading" color="indigo darken-2" indeterminate />
              </template>
              <template #no-data>
                <v-alert v-show="!termLoading">데이터가 존재하지 않습니다.</v-alert>
              </template>
            </v-data-table>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
import NdModal from "./../views/modal/NdModal.vue"

export default {
  name: 'DSDatamodelStatusColumn',
  props: ['isMobile'],
  components: { NdModal },
  watch: {
    mergedItems() {
      this.pageCount = Math.ceil(this.mergedItems.length / this.itemsPerPage);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.mergedItems.length / this.itemsPerPage);
    },
  },
  data: () => ({
    modelList: [],
    dmColumnAllItems: [],
    selectedModelId: null,
    searchTable: '',
    searchColumn: '',
    searchColumnKr: '',
    showNonStandardOnly: false,
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 20,
    tableViewLengthList: [10, 20, 30, 50, 100],
    termDataModalShow: false,
    termDetailItem: [],
    termLoading: false,
    // 그리드 편집 상태
    objOptions: [],
    addTargetObjNm: null,
    newRows: [],            // 미저장 ADD 행들
    pendingDeletes: [],     // 미저장 DELETE 행들
    selectedRows: [],       // show-select 체크된 행
    resolving: false,
    dmColumnDetaileHeaders: [
      { text: '소유자', align: 'center', sortable: false, value: 'objOwner', width: '80px' },
      { text: '테이블 한글명', sortable: false, align: 'center', value: 'objNmKr' },
      { text: '테이블명', align: 'center', sortable: false, value: 'objNm' },
      { text: '컬럼 한글명', sortable: false, align: 'center', value: 'attrNmKr' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'attrNm' },
      { text: '데이터 타입', sortable: false, align: 'center', value: 'dataType' },
      { text: '길이', sortable: false, align: 'center', value: 'dataLen', width: '60px' },
      { text: '소수점', sortable: false, align: 'center', value: 'dataDecimalLen', width: '60px' },
      { text: 'NULL', sortable: false, align: 'center', value: 'nullableYn', width: '60px' },
      { text: 'PK', sortable: false, align: 'center', value: 'pkYn', width: '50px' },
      { text: 'FK', sortable: false, align: 'center', value: 'fkYn', width: '50px' },
      { text: '디폴트', sortable: false, align: 'center', value: 'defaultVal' },
      { text: '표준', sortable: false, align: 'center', value: 'termsStndYn', width: '60px' },
      { text: '변환 불가 사유', sortable: false, align: 'center', value: 'resolveReason', width: '160px' },
      { text: '', sortable: false, align: 'center', value: 'actions', width: '60px' },
    ],
    // 엑셀 업로드
    uploadDialog: false,
    uploadFile: null,
    uploadRows: [],
    uploadErrors: [],
    uploadWarnings: [],
    uploadSummary: null,
    uploadCommitting: false,
    uploadAttrHeaders: [
      { text: '행', value: 'row', align: 'center', sortable: false, width: '60px' },
      { text: '상태', value: '_action', align: 'center', sortable: false, width: '70px' },
      { text: '소유자', value: 'objOwner', align: 'center', sortable: false, width: '100px' },
      { text: '테이블(한글)', value: 'objNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '컬럼(한글)', value: 'attrNmKr', align: 'center', sortable: false },
      { text: 'PK', value: 'pkYn', align: 'center', sortable: false, width: '50px' },
      { text: 'FK', value: 'fkYn', align: 'center', sortable: false, width: '50px' },
      { text: '참조 테이블', value: 'refObjNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '참조 컬럼', value: 'refAttrNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '삭제 규칙', value: 'deleteRule', align: 'center', sortable: false, width: '90px' },
      { text: '메시지', value: '_msg', sortable: false },
    ],
    termDetaileHeaders: [
      { text: '용어명', align: 'center', sortable: false, value: 'termsNm' },
      { text: '용어영문약어명', sortable: false, align: 'center', value: 'termsEngAbrvNm' },
      { text: '용어설명', sortable: false, align: 'center', value: 'termsDesc' },
      { text: '도메인명', sortable: false, align: 'center', value: 'domainNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '코드그룹', sortable: false, align: 'center', value: 'codeGrp' },
      { text: '담당기관명', sortable: false, align: 'center', value: 'chrgOrg' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
  }),
  computed: {
    dmColumnItems() {
      return this.dmColumnAllItems.filter(item => {
        const t   = !this.searchTable    || (item.objNm     || '').includes(this.searchTable);
        const c   = !this.searchColumn   || (item.attrNm    || '').includes(this.searchColumn);
        const cKr = !this.searchColumnKr || (item.attrNmKr  || '').includes(this.searchColumnKr);
        const nonStd = !this.showNonStandardOnly || item.termsStndYn === 'N';
        return t && c && cKr && nonStd;
      });
    },
    mergedItems() {
      // 저장된 행 + 미저장 신규 행 (신규 행은 뒤에 붙임)
      return [...this.dmColumnItems, ...this.newRows];
    },
  },
  methods: {
    /**
     * 물리명(attrNm) 표시 포맷.
     *  - 미저장 신규 행 (_mode='add'): '(저장 후 자동)' placeholder
     *  - 저장된 행 중 비표준 (termsStndYn='N', 즉 자동 생성된 TMP_COL_N): 빈값
     *  - 그 외: attrNm 그대로
     */
    formatAttrNm(item) {
      if (item._mode === 'add') return '(저장 후 자동)';
      if (item.termsStndYn === 'N') return '';
      return item.attrNm || '';
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
      this.dmColumnAllItems = [];
      this.newRows = [];
      this.pendingDeletes = [];
      this.selectedRows = [];
      this.addTargetObjNm = null;
      if (!modelId) return;
      this.loadObjOptions();
      this.load();
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loadTable = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelAttrListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.dmColumnAllItems = this._mapColumnData(res.data);
        this.selectedRows = [];
        this.loadTable = false;
      }).catch(() => {
        this.$swal.fire({ title: '컬럼 정보 로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
    },
    _mapColumnData(data) {
      return data.map(item => ({
        attrId: item.attrId,
        objOwner: item.objOwner, objNm: item.objNm, objNmKr: item.objNmKr,
        attrNm: item.attrNm, attrNmKr: item.attrNmKr,
        dataType: item.dataType, dataLen: item.dataLen, dataDecimalLen: item.dataDecimalLen,
        nullableYn: item.nullableYn, termsStndYn: item.termsStndYn, domainStndYn: item.domainStndYn,
        pkYn: item.pkYn, fkYn: item.fkYn, defaultVal: item.defaultVal,
        clctId: item.clctId, dataModelId: item.dataModelId,
        _rowKey: 's_' + item.objNm + '_' + item.attrNm,
        _mode: 'saved',
        _resolveReason: null,
      }));
    },
    loadObjOptions() {
      if (!this.selectedModelId) { this.objOptions = []; return; }
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.objOptions = (res.data || []).map(o => ({
          objNm: o.objNm,
          label: (o.objNmKr ? o.objNmKr + ' (' + o.objNm + ')' : o.objNm),
        }));
      }).catch(() => { this.objOptions = []; });
    },
    _makeBlankRow(targetObj, seq) {
      const ts = Date.now() + '_' + seq;
      return {
        _rowKey: 'n_' + ts,
        _mode: 'add',
        _error: false,
        _resolveReason: null,
        objOwner: '',
        objNm: targetObj.objNm,
        objNmKr: (targetObj.label || '').replace(/ \(.+\)$/, ''),
        attrNm: '',
        attrNmKr: '',
        dataType: '',
        dataLen: null,
        dataDecimalLen: null,
        nullableYn: 'Y',
        pkYn: 'N',
        fkYn: 'N',
        defaultVal: '',
        termsStndYn: 'N',
      };
    },
    addEmptyRow() {
      if (!this.addTargetObjNm) {
        this.$swal.fire({ title: '추가 대상 테이블을 선택하세요.', icon: 'warning' });
        return;
      }
      if (this.newRows.length >= 100) return;
      const targetObj = this.objOptions.find(o => o.objNm === this.addTargetObjNm) || { objNm: this.addTargetObjNm, label: '' };
      this.newRows.push(this._makeBlankRow(targetObj, this.newRows.length));
    },
    addEmptyRows(n) {
      if (!this.addTargetObjNm) {
        this.$swal.fire({ title: '추가 대상 테이블을 선택하세요.', icon: 'warning' });
        return;
      }
      const targetObj = this.objOptions.find(o => o.objNm === this.addTargetObjNm) || { objNm: this.addTargetObjNm, label: '' };
      const remain = 100 - this.newRows.length;
      const add = Math.min(n, remain);
      for (let i = 0; i < add; i++) {
        this.newRows.push(this._makeBlankRow(targetObj, this.newRows.length));
      }
    },
    _parseBool(v) {
      if (v === undefined || v === null) return null;
      const s = String(v).trim().toUpperCase();
      if (s === '' ) return null;
      if (['Y', 'YES', 'TRUE', 'T', '1', '✓', 'O'].indexOf(s) >= 0) return 'Y';
      if (['N', 'NO', 'FALSE', 'F', '0', 'X'].indexOf(s) >= 0) return 'N';
      return null;
    },
    onPaste(e) {
      // 그리드 편집 컨텍스트가 아닐 때는 무시 (모델·타겟 테이블 없음)
      if (!this.selectedModelId || !this.addTargetObjNm) return;
      // 입력 중인 일반 input/textarea 포커스에선 기본 동작 유지
      const tag = (e.target && e.target.tagName) || '';
      const isInput = tag === 'INPUT' || tag === 'TEXTAREA';
      if (isInput) return;
      const cd = e.clipboardData || window.clipboardData;
      if (!cd) return;
      const text = cd.getData('text');
      if (!text) return;
      // TSV 파싱: 개행 → 행, \t → 열
      const rows = text.replace(/\r\n?/g, '\n').split('\n').filter(r => r.length > 0);
      if (rows.length === 0) return;
      e.preventDefault();
      const targetObj = this.objOptions.find(o => o.objNm === this.addTargetObjNm) || { objNm: this.addTargetObjNm, label: '' };
      const remain = 100 - this.newRows.length;
      const toAdd = Math.min(rows.length, remain);
      let added = 0;
      for (let i = 0; i < toAdd; i++) {
        const cols = rows[i].split('\t');
        const attrNmKr = (cols[0] || '').trim();
        if (!attrNmKr) continue;
        const row = this._makeBlankRow(targetObj, this.newRows.length + added);
        row.attrNmKr = attrNmKr;
        // 열 순서: 한글명, NULL, PK, FK, 기본값
        const nullable = this._parseBool(cols[1]);
        const pk = this._parseBool(cols[2]);
        const fk = this._parseBool(cols[3]);
        if (nullable) row.nullableYn = nullable;
        if (pk) row.pkYn = pk;
        if (fk) row.fkYn = fk;
        if (pk === 'Y') row.nullableYn = 'N';
        if (cols[4] !== undefined) row.defaultVal = cols[4].trim();
        this.newRows.push(row);
        added++;
      }
      if (added > 0) {
        this.$swal.fire({
          title: `${added}행 붙여넣기 완료`, icon: 'success', timer: 1200, showConfirmButton: false,
        });
      }
      if (rows.length > remain) {
        this.$swal.fire({
          title: '최대 100행 제한 초과', text: `${rows.length - remain}행은 잘림. 대량은 엑셀 업로드 사용.`, icon: 'warning',
        });
      }
    },
    removeNewRow(item) {
      this.newRows = this.newRows.filter(r => r._rowKey !== item._rowKey);
      this.selectedRows = this.selectedRows.filter(r => r._rowKey !== item._rowKey);
    },
    deleteOne(item) {
      this.$swal.fire({
        title: '컬럼을 삭제하시겠습니까?', text: `${item.objNm}.${item.attrNm}`, icon: 'warning',
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
      }).then((r) => {
        if (!r.isConfirmed) return;
        this.pendingDeletes.push({ objNm: item.objNm, attrNm: item.attrNm });
        this.dmColumnAllItems = this.dmColumnAllItems.filter(x => x._rowKey !== item._rowKey);
      });
    },
    deleteSelected() {
      const savedSel = this.selectedRows.filter(r => r._mode === 'saved');
      const newSel = this.selectedRows.filter(r => r._mode === 'add');
      if (savedSel.length + newSel.length === 0) return;
      this.$swal.fire({
        title: `${savedSel.length + newSel.length}건을 삭제하시겠습니까?`,
        text: '저장 버튼을 눌러야 최종 반영됩니다.',
        icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
      }).then(r => {
        if (!r.isConfirmed) return;
        newSel.forEach(n => this.removeNewRow(n));
        savedSel.forEach(s => {
          this.pendingDeletes.push({ objNm: s.objNm, attrNm: s.attrNm });
          this.dmColumnAllItems = this.dmColumnAllItems.filter(x => x._rowKey !== s._rowKey);
        });
        this.selectedRows = [];
      });
    },
    _validateNewRows() {
      let ok = true;
      this.newRows.forEach(r => {
        r._error = !r.attrNmKr || !r.attrNmKr.trim() || !r.objNm;
        if (r._error) ok = false;
      });
      return ok;
    },
    _groupByObj(rows) {
      const groups = {};
      rows.forEach(r => {
        const k = r.objNm;
        if (!groups[k]) groups[k] = [];
        groups[k].push(r);
      });
      return groups;
    },
    saveAll() {
      if (!this._validateNewRows()) {
        this.$swal.fire({ title: '입력 오류', text: '빨간 행의 한글명을 채워주세요.', icon: 'warning' });
        return;
      }
      const addGroups = this._groupByObj(this.newRows);
      const delGroups = this._groupByObj(this.pendingDeletes);
      const objNms = new Set([...Object.keys(addGroups), ...Object.keys(delGroups)]);
      if (objNms.size === 0) return;

      const requests = [];
      objNms.forEach(objNm => {
        const attrs = [];
        (addGroups[objNm] || []).forEach(r => attrs.push({
          mode: 'ADD', attrNmKr: r.attrNmKr, pkYn: r.pkYn, fkYn: r.fkYn,
          nullableYn: r.nullableYn, defaultVal: r.defaultVal,
        }));
        (delGroups[objNm] || []).forEach(r => attrs.push({
          mode: 'DELETE', attrNm: r.attrNm,
        }));
        requests.push(axios.post(this.$APIURL.base + 'api/dm/saveAttrs', {
          dataModelId: this.selectedModelId, objNm, attrs,
        }));
      });

      Promise.all(requests).then(responses => {
        const fail = responses.find(res => !(res.data && res.data.resultCode === 200));
        if (fail) {
          this.$swal.fire({
            title: '일부 저장 실패',
            text: (fail.data && fail.data.resultMessage) || '서버 오류',
            icon: 'error',
          });
          return;
        }
        const totalSaved = responses.reduce((sum, res) => {
          try {
            const c = res.data && res.data.contents;
            const parsed = typeof c === 'string' ? JSON.parse(c) : (c || {});
            return sum + (parsed.saved || 0);
          } catch (e) { return sum; }
        }, 0);
        this.$swal.fire({
          title: `${totalSaved}건 저장 완료`, icon: 'success', timer: 1500, showConfirmButton: false,
        });
        this.newRows = [];
        this.pendingDeletes = [];
        this.load();
      }).catch((err) => {
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '저장 실패';
        this.$swal.fire({ title: '저장 실패', text: msg, icon: 'error' });
      });
    },
    resolveSelected() {
      const saved = this.selectedRows.filter(r => r._mode === 'saved');
      if (saved.length === 0) {
        this.$swal.fire({ title: '변환할 저장된 컬럼이 없습니다.', text: '신규 행은 먼저 저장 후 변환하세요.', icon: 'info' });
        return;
      }
      this.resolving = true;
      const payload = {
        dataModelId: this.selectedModelId,
        attrs: saved.map(r => ({ objNm: r.objNm, attrNm: r.attrNm })),
      };
      axios.post(this.$APIURL.base + 'api/dm/resolveAttrs', payload).then((res) => {
        const data = res.data || {};
        const failedKeySet = new Set((data.failedList || []).map(f => f.objNm + '::' + f.attrNm));
        const reasonMap = {};
        (data.failedList || []).forEach(f => { reasonMap[f.objNm + '::' + f.attrNm] = f.reason; });
        // 변환 결과 메시지
        this.$swal.fire({
          title: `변환 결과: ${data.succeeded || 0} / ${data.tried || 0}`,
          text: (data.failed || 0) > 0 ? `실패 ${data.failed}건 — 그리드 "변환 불가 사유" 컬럼에 표시됩니다.` : '전체 성공',
          icon: (data.failed || 0) > 0 ? 'warning' : 'success',
          timer: 2000, showConfirmButton: false,
        });
        this.resolving = false;
        // 목록 새로고침 후 실패 사유 주입
        this.load();
        this.$nextTick(() => {
          setTimeout(() => {
            this.dmColumnAllItems.forEach(r => {
              const k = r.objNm + '::' + r.attrNm;
              if (reasonMap[k]) r._resolveReason = reasonMap[k];
            });
          }, 300);
        });
      }).catch((err) => {
        this.resolving = false;
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '변환 실패';
        this.$swal.fire({ title: '변환 실패', text: msg, icon: 'error' });
      });
    },
    showTermData(item) {
      this.termLoading = true;
      this.termDataModalShow = true;
      axios.get(this.$APIURL.base + "api/std/getTermsInfoByNm", {
        params: { 'termsNm': item.attrNmKr }
      }).then((res) => {
        this.termDetailItem = res.data;
        this.termLoading = false;
      }).catch(() => {
        this.$swal.fire({ title: '용어 검색 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.termLoading = false;
      });
    },
    columnDataDownload() {
      axios.get(this.$APIURL.base + "api/dm/downloadDataModelAttrs", {
        params: { 'clctId': this.selectedModelId },
        responseType: 'blob',
        headers: { "Accept": "application/vnd.ms-excel" }
      }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/csv" }));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", `데이터모델_컬럼정보_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(url);
        link.remove();
      }).catch(() => {
        this.$swal.fire({ title: '컬럼 정보 다운로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
      });
    },
    // ===== 엑셀 업로드 =====
    downloadAttrsTemplate() {
      const url = this.$APIURL.base + 'api/dm/uploadTemplate?scope=attrs';
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'dataq_attrs_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    triggerUploadAttrs() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델을 먼저 선택하세요.', icon: 'warning' });
        return;
      }
      this.$refs.uploadAttrsInput.value = '';
      this.$refs.uploadAttrsInput.click();
    },
    onAttrFileSelected(e) {
      const f = e.target.files && e.target.files[0];
      if (!f) return;
      this.uploadFile = f;
      this._runAttrsUpload('preview');
    },
    _runAttrsUpload(mode) {
      if (!this.uploadFile) return;
      if (mode === 'commit') this.uploadCommitting = true;
      const fd = new FormData();
      fd.append('file', this.uploadFile);
      fd.append('dataModelId', this.selectedModelId);
      fd.append('mode', mode);
      axios.post(this.$APIURL.base + 'api/dm/uploadAttrs', fd, {
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
        this.uploadRows = payload.attrs || [];
        this.uploadErrors = payload.errors || [];
        this.uploadWarnings = payload.warnings || [];
        this.uploadSummary = payload.summary || null;
        if (mode === 'commit') {
          this.uploadCommitting = false;
          this.uploadDialog = false;
          const inserted = (this.uploadSummary && this.uploadSummary.toInsertAttrs) || 0;
          this.$swal.fire({ title: inserted + '컬럼 등록 완료', icon: 'success', timer: 1500, showConfirmButton: false });
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
    commitAttrsUpload() {
      this._runAttrsUpload('commit');
    },
    rowClass(item) {
      if (!item) return '';
      if (item._action === 'ERROR') return 'row-upload-error';
      if (item._action === 'SKIP') return 'row-upload-skip';
      return '';
    },
    _applyPendingView(pending) {
      const apply = () => {
        this.selectedModelId = pending.modelId;
        this.searchTable = pending.tableNm || '';
        this.$nextTick(() => {
          this.loadObjOptions();
          this.load();
        });
      };
      if (this.modelList.length > 0) {
        apply();
      } else {
        axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
          'schNm': null
        }).then((res) => {
          this.modelList = res.data.map(item => ({
            dataModelId: item.dataModelId,
            dataModelNm: item.dataModelNm,
          }));
          apply();
        });
      }
    },
  },
  created() {
    this.getModelList();
  },
  mounted() {
    this.$resizableGrid && this.$resizableGrid();
    this._pasteHandler = this.onPaste.bind(this);
    document.addEventListener('paste', this._pasteHandler);
  },
  beforeDestroy() {
    if (this._pasteHandler) document.removeEventListener('paste', this._pasteHandler);
  },
  activated() {
    if (eventBus.pendingColumnView) {
      const pending = eventBus.pendingColumnView;
      eventBus.pendingColumnView = null;
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
pre { font-family: 'Roboto'; }
.checkboxStyle { margin-top: 0; padding-top: 0; }
#clTable_table { height: calc(100vh - 64px - 48px - 144px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
.row-nonstandard > td { background-color: #FFEBEE !important; }
.inline-edit { min-width: 100px; }
.inline-edit >>> input { padding: 2px 6px; }
.inline-error >>> .v-input__slot { background-color: #FFEBEE !important; }
.row-upload-error > td { background-color: #FFEBEE !important; }
.row-upload-skip > td { background-color: #FFF8E1 !important; }
.preview-grid { border: 1px solid #E0E0E0; }
</style>
