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
        <span class="filterLabel">데이터 타입</span>
        <v-text-field v-model="searchDataType" @click:clear="searchDataType=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '90px' }">
        </v-text-field>
        <span class="filterLabel">데이터 길이</span>
        <v-text-field v-model="searchDataLen" @click:clear="searchDataLen=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '80px' }">
        </v-text-field>
        <!-- [숨김] 수집 시 표준검사 제거에 따라 표준여부 필터 비활성화 — 원복 시 주석 해제 -->
        <!-- <span class="filterLabel" :style="{ paddingLeft: '4px' }">표준여부</span>
        <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="표준" color="ndColor" value="Y"></v-checkbox>
        <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="비표준" color="ndColor" value="N"></v-checkbox> -->
        <v-btn class="gradient" v-on:click="load" :style="{ padding: '0 12px' }">조회</v-btn>
        <v-btn class="gradient" v-on:click="columnDataDownload" :disabled="dmColumnAllItems.length === 0">다운로드</v-btn>
        <v-btn color="primary" :disabled="!selectedModelId" v-on:click="openAddAttrDialog" :style="{ padding: '0 12px', marginLeft: '8px' }">컬럼 추가</v-btn>
      </v-row>
    </v-sheet>

    <!-- 컬럼 추가/수정 다이얼로그 (한글명 단일 입력 · 표준 변환) -->
    <v-dialog v-model="attrDialog" max-width="720" persistent>
      <v-card>
        <v-card-title>
          {{ attrDialogMode === 'add' ? '컬럼 추가' : '컬럼 수정' }}
          <v-spacer />
          <v-chip v-if="resolveState === 'matched'" color="success" small>표준</v-chip>
          <v-chip v-else-if="resolveState === 'unmatched'" color="error" small>비표준</v-chip>
        </v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12">
              <v-autocomplete v-model="attrForm.objNm" :items="objOptions" label="소속 테이블 *"
                :disabled="attrDialogMode === 'edit'" outlined dense hide-details />
            </v-col>
          </v-row>
          <v-row dense>
            <v-col cols="9">
              <v-text-field v-model="attrForm.attrNmKr" label="컬럼 한글명 *"
                hint="입력 후 [표준 변환] 클릭 또는 엔터로 자동 변환됩니다"
                persistent-hint outlined dense
                @blur="resolveStandardNow" @keyup.enter="resolveStandardNow" />
            </v-col>
            <v-col cols="3" class="d-flex align-center">
              <v-btn color="indigo" dark @click="resolveStandardNow" :loading="standardLoading"
                :disabled="!attrForm.attrNmKr">
                <v-icon small left>mdi-auto-fix</v-icon>표준 변환
              </v-btn>
            </v-col>
          </v-row>

          <!-- 변환 결과 패널 -->
          <v-sheet v-if="resolveState !== 'idle'" outlined rounded class="pa-3 mt-2"
            :class="resolveState === 'matched' ? 'matched-panel' : 'unmatched-panel'">
            <div v-if="resolveState === 'matched'">
              <v-icon small color="success" class="mr-1">mdi-check-circle</v-icon>
              <strong>표준 매칭</strong> — {{ resolveResult.termsNm }} → {{ resolveResult.termsEngAbrvNm }}
              <span v-if="resolveResult.domainNm"> · {{ resolveResult.domainNm }}</span>
            </div>
            <div v-else>
              <v-icon small color="error" class="mr-1">mdi-alert-circle</v-icon>
              <strong>표준 미매칭</strong> — '{{ attrForm.attrNmKr }}'에 해당하는 표준 용어가 없습니다.
              <div class="mt-1 mb-2" style="font-size:.8rem; color:#666;">
                비표준으로 저장 시: 물리명은 임시로 자동 생성되고, 타입은 VARCHAR(255)로 설정됩니다. 그리드에서 빨간색으로 표시됩니다.
              </div>
              <v-btn small outlined color="indigo" @click="goRegisterTerm">
                <v-icon small left>mdi-book-plus</v-icon>용어 등록하기
              </v-btn>
            </div>
          </v-sheet>

          <!-- 변환 결과 필드 (readonly) -->
          <v-row dense class="mt-2" v-if="resolveState === 'matched'">
            <v-col cols="6">
              <v-text-field v-model="attrForm.attrNm" label="컬럼 물리명 (자동)" readonly outlined dense />
            </v-col>
            <v-col cols="3">
              <v-text-field v-model="attrForm.dataType" label="데이터 타입 (자동)" readonly outlined dense />
            </v-col>
            <v-col cols="2">
              <v-text-field v-model.number="attrForm.dataLen" label="길이" type="number" readonly outlined dense />
            </v-col>
            <v-col cols="1">
              <v-text-field v-model.number="attrForm.dataDecimalLen" label="소수" type="number" readonly outlined dense />
            </v-col>
          </v-row>

          <!-- 속성 (사용자 편집) -->
          <v-row dense align="center" class="mt-2">
            <v-col cols="3"><v-checkbox v-model="attrForm.pkYn" label="PK" true-value="Y" false-value="N" hide-details /></v-col>
            <v-col cols="3"><v-checkbox v-model="attrForm.fkYn" label="FK" true-value="Y" false-value="N" hide-details /></v-col>
            <v-col cols="3"><v-checkbox v-model="attrForm.nullableYn" label="NULL 허용" true-value="Y" false-value="N" hide-details /></v-col>
            <v-col cols="3">
              <v-text-field v-model="attrForm.defaultVal" label="기본값" outlined dense hide-details />
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="attrDialog = false">취소</v-btn>
          <v-btn color="primary" @click="submitAttr"
            :disabled="!attrForm.objNm || !attrForm.attrNmKr || resolveState === 'idle' || resolveState === 'loading'">
            {{ attrDialogMode === 'add' ? (resolveState === 'unmatched' ? '비표준으로 추가' : '추가') : '수정' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 목록 카운트 + 페이지 크기 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ dmColumnItems.length }}건</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 컬럼 목록 -->
    <v-data-table id="clTable_table" :headers="dmColumnDetaileHeaders" :items="dmColumnItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer hide-default-header
      item-key="attrNm" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">
      <template #header="">
        <thead class="v-data-table-header">
          <tr>
            <th v-for="(h, i) in dmColumnDetaileHeaders" :key="i"
              class="text-center parent-header td-border-style"
              :rowspan="h.children ? 1 : 2" :colspan="h.children ? h.children.length : 1">
              <pre>{{ h.text }}</pre>
            </th>
          </tr>
          <tr>
            <th v-for="(h1, i1) in getSubHeader(dmColumnDetaileHeaders)" :key="i1"
              class="text-center child-header td-border-style"
              :style="{ borderTop: '0px', borderLeft: '0px', backgroundColor: 'rgba(63, 81, 181, 0.08)' }">
              <pre>{{ h1.text }}</pre>
            </th>
          </tr>
        </thead>
      </template>
      <template #item="props">
        <tr :class="{ 'row-nonstandard': props.item.termsStndYn === 'N' }">
          <td v-for="(c, ci) in getRows(props.item)" :key="ci" :style="{ padding: '0px' }">
            <span v-if="ci === 'objOwner'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'objNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'objNmKr'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'attrNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'attrNmKr'" class="ndColor--text"
              :style="{ cursor: 'pointer', margin: '0px 16px' }" @click="showTermData(props.item)">
              <v-icon v-if="props.item.termsStndYn === 'N'" small color="error" class="mr-1"
                title="비표준 용어">mdi-alert-circle</v-icon>{{ c }}
            </span>
            <span v-else-if="ci === 'dataType'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'dataLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'dataDecimalLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <p v-else-if="ci === 'nullableYn'" :style="{ textAlign: 'center', margin: '0px 16px' }">{{ c }}</p>
            <p v-else-if="ci === 'pkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
            <p v-else-if="ci === 'fkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
            <span v-else-if="ci === 'defaultVal'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'termsStndYn'" :style="{ display: 'block', textAlign: 'center' }">
              <v-icon small :color="c === 'Y' ? 'success' : 'error'">
                {{ c === 'Y' ? 'mdi-check-circle' : 'mdi-close-circle' }}
              </v-icon>
            </span>
            <span v-else-if="ci === 'actions'" :style="{ textAlign: 'center', display: 'block' }">
              <v-icon small :disabled="!selectedModelId" @click="openEditAttrDialog(props.item)" class="mr-2">mdi-pencil</v-icon>
              <v-icon small :disabled="!selectedModelId" @click="deleteAttr(props.item)">mdi-delete</v-icon>
            </span>
          </td>
        </tr>
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
import _ from "lodash"

export default {
  name: 'DSDatamodelStatusColumn',
  props: ['isMobile'],
  components: { NdModal },
  watch: {
    dmColumnItems() {
      this.pageCount = Math.ceil(this.dmColumnItems.length / this.itemsPerPage);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.dmColumnItems.length / this.itemsPerPage);
    },
  },
  data: () => ({
    modelList: [],
    dmColumnAllItems: [],
    selectedModelId: null,
    selectedClctId: null,
    searchTable: '',
    searchColumn: '',
    searchColumnKr: '',
    searchDataType: '',
    searchDataLen: '',
    statusListArray: ['Y', 'N'],
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    termDataModalShow: false,
    termDetailItem: [],
    termLoading: false,
    dmColumnDetaileHeaders: [
      { text: '소유자', align: 'center', sortable: false, value: 'objOwner', width: '80px' },
      { text: '테이블\n한글명', sortable: false, align: 'center', value: 'objNmKr' },
      { text: '테이블명', align: 'center', sortable: false, value: 'objNm' },
      { text: '컬럼\n한글명', sortable: false, align: 'center', value: 'attrNmKr' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'attrNm' },
      { text: '데이터\n타입', sortable: false, align: 'center', value: 'dataType' },
      { text: '길이', sortable: false, align: 'center', value: 'dataLen', width: '60px' },
      { text: '소수점', sortable: false, align: 'center', value: 'dataDecimalLen', width: '60px' },
      { text: 'NULL\n여부', sortable: false, align: 'center', value: 'nullableYn', width: '60px' },
      { text: 'PK', sortable: false, align: 'center', value: 'pkYn', width: '50px' },
      { text: 'FK', sortable: false, align: 'center', value: 'fkYn', width: '50px' },
      { text: '디폴트', sortable: false, align: 'center', value: 'defaultVal' },
      { text: '표준', sortable: false, align: 'center', value: 'termsStndYn', width: '60px' },
      { text: '편집', sortable: false, align: 'center', value: 'actions', width: '90px' },
    ],
    attrDialog: false,
    attrDialogMode: 'add',
    attrForm: {
      attrId: null, dataModelId: null,
      objNm: null, attrNm: '', attrNmKr: '',
      dataType: '', dataLen: null, dataDecimalLen: null,
      pkYn: 'N', fkYn: 'N', nullableYn: 'Y', defaultVal: '',
      termsId: null, domainId: null,
      termsStndYn: 'Y', domainStndYn: 'Y',
    },
    resolveState: 'idle',      // idle | loading | matched | unmatched
    resolveResult: {},         // resolveStandard 응답
    standardLoading: false,
    objOptions: [],
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
        const c   = !this.searchColumn  || (item.attrNm    || '').includes(this.searchColumn);
        const cKr = !this.searchColumnKr|| (item.attrNmKr  || '').includes(this.searchColumnKr);
        const dt  = !this.searchDataType|| (item.dataType  || '').toUpperCase().includes(this.searchDataType.toUpperCase());
        const dl  = !this.searchDataLen || String(item.dataLen || '').includes(this.searchDataLen);
        // [숨김] 표준여부 필터 비활성화
        // const stnd = this.statusListArray.length === 2 || this.statusListArray.length === 0 ||
        //              this.statusListArray.includes(item.termsStndYn);
        return t && c && cKr && dt && dl;
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
      this.dmColumnAllItems = [];
      if (!modelId) return;
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
        this.loadTable = false;
      }).catch(() => {
        this.$swal.fire({ title: '컬럼 정보 로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
    },
    _mapColumnData(data) {
      return data.map(item => {
        const _wordLst = (item.wordLst || []).map((w, i) => w + " : " + (item.wordStndLst || [])[i]);
        return {
          attrId: item.attrId,
          objOwner: item.objOwner, objNm: item.objNm, objNmKr: item.objNmKr, attrNm: item.attrNm, attrNmKr: item.attrNmKr,
          dataType: item.dataType, dataLen: item.dataLen, dataDecimalLen: item.dataDecimalLen,
          nullableYn: item.nullableYn, termsStndYn: item.termsStndYn, domainStndYn: item.domainStndYn,
          wordLst: _wordLst, pkYn: item.pkYn, fkYn: item.fkYn, defaultVal: item.defaultVal,
          clctId: item.clctId, dataModelId: item.dataModelId,
        };
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
    _applyPendingView(pending) {
      const apply = () => {
        this.selectedModelId = pending.modelId;
        this.searchTable = pending.tableNm || '';
        this.$nextTick(() => { this.load(); });
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
    getSubHeader(headers) {
      let result = [];
      headers.filter(i => i.children).forEach(v => { result = result.concat(v.children); });
      return result;
    },
    getRows(rows) {
      // 그리드 표시 순서: 한글명 중심 (objNmKr 먼저, objNm 뒤) + 표준 여부 컬럼 추가
      const keys = ['objOwner','objNmKr','objNm','attrNmKr','attrNm','dataType','dataLen','dataDecimalLen',
                     'nullableYn','pkYn','fkYn','defaultVal','termsStndYn','actions'];
      const result = {};
      keys.forEach(key => { result[key] = rows[key] != null ? rows[key] : ''; });
      return result;
    },
    loadObjOptions() {
      if (!this.selectedModelId) { this.objOptions = []; return; }
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.objOptions = (res.data || []).map(o => o.objNm).filter(Boolean);
      }).catch(() => { this.objOptions = []; });
    },
    resetAttrForm() {
      this.attrForm = {
        attrId: null,
        dataModelId: this.selectedModelId,
        objNm: null, attrNm: '', attrNmKr: '',
        dataType: '', dataLen: null, dataDecimalLen: null,
        pkYn: 'N', fkYn: 'N', nullableYn: 'Y', defaultVal: '',
        termsId: null, domainId: null,
        termsStndYn: 'Y', domainStndYn: 'Y',
      };
      this.resolveState = 'idle';
      this.resolveResult = {};
    },
    openAddAttrDialog() {
      if (!this.isLatestClct) return;
      this.attrDialogMode = 'add';
      this.resetAttrForm();
      this.loadObjOptions();
      this.attrDialog = true;
    },
    openEditAttrDialog(item) {
      if (!this.isLatestClct) return;
      this.attrDialogMode = 'edit';
      this.resetAttrForm();
      this.attrForm = {
        attrId: item.attrId,
        dataModelId: item.dataModelId || this.selectedModelId,
        objNm: item.objNm, attrNm: item.attrNm, attrNmKr: item.attrNmKr,
        dataType: item.dataType, dataLen: item.dataLen, dataDecimalLen: item.dataDecimalLen,
        pkYn: item.pkYn || 'N', fkYn: item.fkYn || 'N',
        nullableYn: item.nullableYn || 'Y', defaultVal: item.defaultVal || '',
        termsId: null, domainId: null,
        termsStndYn: item.termsStndYn || 'Y', domainStndYn: item.domainStndYn || 'Y',
      };
      this.resolveState = item.termsStndYn === 'N' ? 'unmatched' : 'matched';
      this.resolveResult = { termsNm: item.attrNmKr, termsEngAbrvNm: item.attrNm };
      this.loadObjOptions();
      this.attrDialog = true;
    },
    resolveStandardNow() {
      const kr = (this.attrForm.attrNmKr || '').trim();
      if (!kr) { this.resolveState = 'idle'; return; }
      // 이미 같은 한글명으로 변환된 상태면 재호출 안함
      if (this.resolveState === 'matched' && this.resolveResult.termsNm === kr) return;
      this.standardLoading = true;
      this.resolveState = 'loading';
      axios.get(this.$APIURL.base + 'api/dm/resolveStandard', { params: { termsNm: kr } })
        .then((res) => {
          const data = res.data || {};
          if (data.found) {
            this.resolveResult = data;
            this.attrForm.attrNm = data.termsEngAbrvNm || '';
            this.attrForm.termsId = data.termsId || null;
            this.attrForm.domainId = data.domainId || null;
            this.attrForm.dataType = data.dataType || '';
            this.attrForm.dataLen = data.dataLen || null;
            this.attrForm.dataDecimalLen = data.dataDecimalLen || null;
            this.attrForm.termsStndYn = 'Y';
            this.attrForm.domainStndYn = data.domainId ? 'Y' : 'N';
            this.resolveState = (data.domainId && data.dataType) ? 'matched' : 'unmatched';
          } else {
            this.resolveResult = { message: data.message };
            this.attrForm.attrNm = '';
            this.attrForm.dataType = '';
            this.attrForm.dataLen = null;
            this.attrForm.dataDecimalLen = null;
            this.attrForm.termsId = null;
            this.attrForm.domainId = null;
            this.attrForm.termsStndYn = 'N';
            this.attrForm.domainStndYn = 'N';
            this.resolveState = 'unmatched';
          }
        })
        .catch(() => {
          this.$swal.fire({ title: '변환 실패', text: '서버 오류', icon: 'error', confirmButtonText: '확인' });
          this.resolveState = 'idle';
        })
        .finally(() => { this.standardLoading = false; });
    },
    goRegisterTerm() {
      eventBus.pendingTermRegister = { termsNm: this.attrForm.attrNmKr };
      eventBus.$emit('openTermsRegister');
      this.attrDialog = false;
    },
    submitAttr() {
      if (!this.attrForm.objNm) { this.$swal.fire({ title: '소속 테이블을 선택하세요.', icon: 'warning' }); return; }
      if (!this.attrForm.attrNmKr) { this.$swal.fire({ title: '컬럼 한글명이 필요합니다.', icon: 'warning' }); return; }
      if (this.resolveState === 'idle' || this.resolveState === 'loading') {
        this.$swal.fire({ title: '표준 변환이 필요합니다.', text: '한글명 입력 후 [표준 변환]을 실행하세요.', icon: 'warning' });
        return;
      }
      const url = this.attrDialogMode === 'add' ? 'api/dm/addAttr' : 'api/dm/updateAttr';
      axios.post(this.$APIURL.base + url, this.attrForm).then((res) => {
        if (res.data && res.data.resultCode === 200) {
          this.$swal.fire({
            title: this.attrDialogMode === 'add' ? '추가되었습니다.' : '수정되었습니다.',
            text: this.attrForm.termsStndYn === 'N' ? '비표준으로 저장되었습니다 (빨간색 표시)' : '',
            icon: 'success', timer: 1500, showConfirmButton: false
          });
          this.attrDialog = false;
          this.load();
        } else {
          this.$swal.fire({ title: '저장 실패', text: (res.data && res.data.resultMessage) || '저장 중 오류', icon: 'error' });
        }
      }).catch((err) => {
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '저장 실패';
        this.$swal.fire({ title: '저장 실패', text: msg, icon: 'error' });
      });
    },
    deleteAttr(item) {
      if (!this.isLatestClct) return;
      this.$swal.fire({
        title: '컬럼을 삭제하시겠습니까?', text: `${item.objNm}.${item.attrNm}`, icon: 'warning',
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
      }).then((r) => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + "api/dm/deleteAttr", {
          attrId: item.attrId, clctId: item.clctId || this.selectedModelId,
          dataModelId: item.dataModelId || this.selectedModelId, objNm: item.objNm, attrNm: item.attrNm,
        }).then((res) => {
          if (res.data && res.data.resultCode === 200) {
            this.$swal.fire({ title: '삭제되었습니다.', icon: 'success', timer: 1000, showConfirmButton: false });
            this.load();
          } else {
            this.$swal.fire({ title: '삭제 실패', icon: 'error' });
          }
        }).catch(() => {
          this.$swal.fire({ title: '삭제 실패 - API 확인 필요', icon: 'error' });
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
#clTable_table { height: calc(100vh - 64px - 48px - 104px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
.row-nonstandard > td { background-color: #FFEBEE !important; }
.matched-panel { background-color: #E8F5E9; border-color: #66BB6A !important; }
.unmatched-panel { background-color: #FFEBEE; border-color: #EF5350 !important; }
</style>
