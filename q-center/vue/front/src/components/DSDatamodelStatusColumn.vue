<template>
  <v-main>
    <!-- 조회 조건 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <!-- Row 1: 모델/수집일시 선택 -->
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

    <!-- 컬럼 추가/수정 다이얼로그 (표준사전 드롭다운 강제형) -->
    <v-dialog v-model="attrDialog" max-width="720" persistent>
      <v-card>
        <v-card-title>{{ attrDialogMode === 'add' ? '컬럼 추가' : '컬럼 수정' }}</v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="6">
              <v-autocomplete v-model="attrForm.objNm" :items="objOptions" label="소속 테이블 *"
                :disabled="attrDialogMode === 'edit'" outlined dense hide-details />
            </v-col>
            <v-col cols="6">
              <v-autocomplete v-model="selectedTerm" :items="termOptions" :loading="termsLoading"
                :search-input.sync="termSearch" item-text="termsNm" item-value="termsId" return-object
                label="용어 사전에서 선택 *" hint="선택 시 컬럼명·한글명이 자동 설정됩니다" persistent-hint
                outlined dense @change="onTermSelected" :disabled="attrDialogMode === 'edit'" />
            </v-col>
          </v-row>
          <v-row dense>
            <v-col cols="6">
              <v-text-field v-model="attrForm.attrNm" label="컬럼명 (물리명) *"
                :disabled="attrDialogMode === 'edit'" hint="용어 선택 시 자동 · 수동 입력 시 표준 검증" persistent-hint outlined dense />
            </v-col>
            <v-col cols="4">
              <v-text-field v-model="attrForm.attrNmKr" label="컬럼 한글명 (논리명) *" outlined dense />
            </v-col>
            <v-col cols="2" class="d-flex align-center">
              <v-btn small color="indigo" dark @click="applyStandard" :loading="standardLoading" :disabled="!attrForm.attrNmKr">
                <v-icon small left>mdi-auto-fix</v-icon>표준 적용
              </v-btn>
            </v-col>
          </v-row>
          <v-row dense>
            <v-col cols="12">
              <v-autocomplete v-model="selectedDomain" :items="domainOptions" :loading="domainsLoading"
                item-text="domainDisplayNm" item-value="domainId" return-object
                label="도메인 사전에서 선택 *" hint="선택 시 데이터 타입·길이가 자동 설정됩니다" persistent-hint
                outlined dense @change="onDomainSelected" />
            </v-col>
          </v-row>
          <v-row dense>
            <v-col cols="4">
              <v-text-field v-model="attrForm.dataType" label="데이터 타입 *" readonly outlined dense />
            </v-col>
            <v-col cols="4">
              <v-text-field v-model.number="attrForm.dataLen" label="길이" type="number" readonly outlined dense />
            </v-col>
            <v-col cols="4">
              <v-text-field v-model.number="attrForm.dataDecimalLen" label="소수점 길이" type="number" readonly outlined dense />
            </v-col>
          </v-row>
          <v-row dense align="center">
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
          <v-btn color="primary" @click="submitAttr">{{ attrDialogMode === 'add' ? '추가' : '수정' }}</v-btn>
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
        <tr>
          <td v-for="(c, ci) in getRows(props.item)" :key="ci"
            :style="{ padding: '0px', backgroundColor: '#ffffff' }">
            <span v-if="ci === 'objOwner'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'objNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'objNmKr'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'attrNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'attrNmKr'" class="ndColor--text"
              :style="{ cursor: 'pointer', margin: '0px 16px' }" @click="showTermData(props.item)">{{ c }}</span>
            <span v-else-if="ci === 'dataType'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'dataLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <span v-else-if="ci === 'dataDecimalLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <p v-else-if="ci === 'nullableYn'" :style="{ textAlign: 'center', margin: '0px 16px' }">{{ c }}</p>
            <!-- [숨김] 수집 시 표준검사 제거에 따라 표준여부 셀 비활성화 — 원복 시 주석 해제 -->
            <!-- <template v-else-if="ci === 'termsStndYn'">
              <p :style="{ textAlign: 'center', margin: '0px 5px' }">{{ c }}</p>
            </template>
            <template v-else-if="ci === 'domainStndYn'">
              <p :style="{ textAlign: 'center', margin: '0px 5px' }">{{ c }}</p>
            </template>
            <template v-else-if="ci === 'wordLst'">
              <p v-for="(line, index) in c" :key="index" :style="{ textAlign: 'center', margin: '0px 5px' }">{{ line }}</p>
            </template> -->
            <p v-else-if="ci === 'pkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
            <p v-else-if="ci === 'fkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
            <span v-else-if="ci === 'defaultVal'" :style="{ margin: '0px 16px' }">{{ c }}</span>
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
    termSearch(val) {
      if (this._termSearchTimer) clearTimeout(this._termSearchTimer);
      this._termSearchTimer = setTimeout(() => { this.loadTermOptions(val); }, 300);
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
      { text: '소유자', align: 'center', sortable: false, value: 'objOwner', width: '100px' },
      { text: '테이블명', align: 'center', sortable: false, value: 'objNm' },
      { text: '테이블 한글명', sortable: false, align: 'center', value: 'objNmKr' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'attrNm' },
      { text: '컬럼\n한글명', sortable: false, align: 'center', value: 'attrNmKr' },
      { text: '데이터\n타입', sortable: false, align: 'center', value: 'dataType' },
      { text: '데이터\n길이', sortable: false, align: 'center', value: 'dataLen' },
      { text: '데이터\n소수점\n길이', sortable: false, align: 'center', value: 'dataDecimalLen' },
      { text: 'NULL\n여부', sortable: false, align: 'center', value: 'nullableYn' },
      // [숨김] 수집 시 표준검사 제거에 따라 표준여부 헤더 비활성화 — 원복 시 주석 해제
      // {
      //   text: '표준 여부', sortable: false, align: 'center', value: 'termsStndYn', divider: true,
      //   children: [
      //     { text: '용어', align: 'center', value: 'termsStndYn', sortable: false },
      //     { text: '도메인', align: 'center', value: 'domainStndYn', sortable: false },
      //     { text: '단어', align: 'center', value: 'wordLst', sortable: false }
      //   ]
      // },
      { text: 'PK 여부', sortable: false, align: 'center', value: 'pkYn' },
      { text: 'FK 여부', sortable: false, align: 'center', value: 'fkYn' },
      { text: '디폴트 값', sortable: false, align: 'center', value: 'defaultVal' },
      { text: '편집', sortable: false, align: 'center', value: 'actions', width: '100px' },
    ],
    attrDialog: false,
    attrDialogMode: 'add',
    attrForm: {
      attrId: null, dataModelId: null, clctId: null,
      objNm: null, attrNm: '', attrNmKr: '',
      dataType: '', dataLen: null, dataDecimalLen: null,
      pkYn: 'N', fkYn: 'N', nullableYn: 'Y', defaultVal: '',
      termsId: null, domainId: null,
    },
    selectedTerm: null,
    termOptions: [],
    termsLoading: false,
    standardLoading: false,
    termSearch: '',
    _termSearchTimer: null,
    selectedDomain: null,
    domainOptions: [],
    domainsLoading: false,
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
      const keys = ['objOwner','objNm','objNmKr','attrNm','attrNmKr','dataType','dataLen','dataDecimalLen',
                     'nullableYn','pkYn','fkYn','defaultVal','actions'];
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
    loadTermOptions(keyword) {
      const kw = (keyword || '').trim();
      if (kw.length < 1) { this.termOptions = []; return; }
      this.termsLoading = true;
      axios.post(this.$APIURL.base + "api/std/getTerms", { 'schNm': kw, 'aprvYn': 'Y' })
        .then((res) => { this.termOptions = res.data || []; })
        .catch(() => { this.termOptions = []; })
        .finally(() => { this.termsLoading = false; });
    },
    loadDomainOptions() {
      this.domainsLoading = true;
      axios.post(this.$APIURL.base + "api/std/getDomainList", { 'schNm': null, 'aprvYn': 'Y' })
        .then((res) => {
          this.domainOptions = (res.data || []).map(d => ({
            ...d,
            domainDisplayNm: `${d.domainNm} (${d.dataType}${d.dataLen ? '(' + d.dataLen + (d.dataDecimalLen ? ',' + d.dataDecimalLen : '') + ')' : ''})`,
          }));
        })
        .catch(() => { this.domainOptions = []; })
        .finally(() => { this.domainsLoading = false; });
    },
    resetAttrForm() {
      this.attrForm = {
        attrId: null,
        dataModelId: this.selectedModelId,
        clctId: this.selectedModelId,
        objNm: null, attrNm: '', attrNmKr: '',
        dataType: '', dataLen: null, dataDecimalLen: null,
        pkYn: 'N', fkYn: 'N', nullableYn: 'Y', defaultVal: '',
        termsId: null, domainId: null,
      };
      this.selectedTerm = null;
      this.selectedDomain = null;
      this.termOptions = [];
      this.termSearch = '';
    },
    openAddAttrDialog() {
      if (!this.isLatestClct) return;
      this.attrDialogMode = 'add';
      this.resetAttrForm();
      this.loadObjOptions();
      if (this.domainOptions.length === 0) this.loadDomainOptions();
      this.attrDialog = true;
    },
    openEditAttrDialog(item) {
      if (!this.isLatestClct) return;
      this.attrDialogMode = 'edit';
      this.resetAttrForm();
      this.attrForm = {
        attrId: item.attrId,
        dataModelId: item.dataModelId || this.selectedModelId,
        clctId: item.clctId || this.selectedModelId,
        objNm: item.objNm, attrNm: item.attrNm, attrNmKr: item.attrNmKr,
        dataType: item.dataType, dataLen: item.dataLen, dataDecimalLen: item.dataDecimalLen,
        pkYn: item.pkYn || 'N', fkYn: item.fkYn || 'N',
        nullableYn: item.nullableYn || 'Y', defaultVal: item.defaultVal || '',
        termsId: null, domainId: null,
      };
      this.loadObjOptions();
      if (this.domainOptions.length === 0) this.loadDomainOptions();
      this.attrDialog = true;
    },
    onTermSelected(term) {
      if (!term) return;
      this.attrForm.attrNm = term.termsEngAbrvNm || '';
      this.attrForm.attrNmKr = term.termsNm || '';
      this.attrForm.termsId = term.termsId;
      if (term.domainId) {
        const d = this.domainOptions.find(x => x.domainId === term.domainId);
        if (d) { this.selectedDomain = d; this.onDomainSelected(d); }
      }
    },
    applyStandard() {
      if (!this.attrForm.attrNmKr) return;
      var self = this;
      self.standardLoading = true;
      axios.get(self.$APIURL.base + 'api/dm/resolveStandard', {
        params: { termsNm: self.attrForm.attrNmKr.trim() }
      }).then(function(res) {
        var data = res.data;
        if (data.found) {
          self.attrForm.attrNm = data.termsEngAbrvNm || '';
          if (data.dataType) {
            self.attrForm.dataType = data.dataType;
            self.attrForm.dataLen = data.dataLen || null;
            self.attrForm.dataDecimalLen = data.dataDecimalLen || null;
          }
          self.$swal.fire({ title: '표준 적용 완료', text: data.termsEngAbrvNm + ' (' + (data.dataType || '') + (data.dataLen ? '(' + data.dataLen + ')' : '') + ')', icon: 'success', timer: 2000, showConfirmButton: false });
        } else {
          self.$swal.fire({ title: '표준 용어 없음', text: data.message, icon: 'warning', confirmButtonText: '확인' });
        }
      }).catch(function() {
        self.$swal.fire({ title: '조회 실패', icon: 'error', confirmButtonText: '확인' });
      }).finally(function() { self.standardLoading = false; });
    },
    onDomainSelected(domain) {
      if (!domain) return;
      this.attrForm.dataType = domain.dataType || '';
      this.attrForm.dataLen = domain.dataLen != null ? domain.dataLen : null;
      this.attrForm.dataDecimalLen = domain.dataDecimalLen != null ? domain.dataDecimalLen : null;
      this.attrForm.domainId = domain.domainId;
    },
    submitAttr() {
      if (!this.attrForm.objNm) { this.$swal.fire({ title: '소속 테이블을 선택하세요.', icon: 'warning' }); return; }
      if (!this.attrForm.attrNm) { this.$swal.fire({ title: '컬럼 물리명이 필요합니다.', icon: 'warning' }); return; }
      if (!this.attrForm.attrNmKr) { this.$swal.fire({ title: '컬럼 한글명이 필요합니다.', icon: 'warning' }); return; }
      if (!this.attrForm.dataType) { this.$swal.fire({ title: '도메인 사전에서 선택해야 합니다.', icon: 'warning' }); return; }
      const url = this.attrDialogMode === 'add' ? 'api/dm/addAttr' : 'api/dm/updateAttr';
      axios.post(this.$APIURL.base + url, this.attrForm).then((res) => {
        if (res.data && res.data.code === 200) {
          this.$swal.fire({ title: this.attrDialogMode === 'add' ? '추가되었습니다.' : '수정되었습니다.', icon: 'success', timer: 1000, showConfirmButton: false });
          this.attrDialog = false;
          this.load();
        } else {
          this.$swal.fire({ title: '저장 실패', text: (res.data && res.data.message) || '표준 검증 실패', icon: 'error' });
        }
      }).catch((err) => {
        const msg = (err.response && err.response.data && err.response.data.message) || '저장 실패';
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
          if (res.data && res.data.code === 200) {
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
</style>
