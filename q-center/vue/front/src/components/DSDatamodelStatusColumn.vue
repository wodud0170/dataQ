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
        <span class="filterLabel">수집일시</span>
        <v-select v-model="selectedClctId" :items="clctList"
          item-text="clctDisplayDt" item-value="clctId"
          clearable dense outlined hide-details
          class="filterInput" :style="{ width: '300px' }" color="ndColor"
          placeholder="수집일시 선택" :disabled="clctList.length === 0">
        </v-select>
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
      </v-row>
    </v-sheet>

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
              :style="{ borderTop: '0px', borderLeft: '0px', backgroundColor: 'rgba(24, 127, 196, 0.1)' }">
              <pre>{{ h1.text }}</pre>
            </th>
          </tr>
        </thead>
      </template>
      <template #item="props">
        <tr>
          <td v-for="(c, ci) in getRows(props.item)" :key="ci"
            :style="{ padding: '0px', backgroundColor: '#ffffff' }">
            <span v-if="ci === 'objNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
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
                    <td :style="{ backgroundColor: 'rgba(24, 127, 196, 0.1)', width: '25%' }">{{ header.text }}</td>
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
    clctList: [],
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
      { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysNm' },
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
      this.clctList = [];
      this.selectedClctId = null;
      this.dmColumnAllItems = [];
      if (!modelId) return;
      const _to = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      const _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';
      axios.post(this.$APIURL.base + "api/dm/getDataModelClctList", {
        'schId': modelId, 'from': _from, 'to': _to
      }).then((res) => {
        const sorted = res.data.slice().sort((a, b) => b.clctStartDt.localeCompare(a.clctStartDt));
        this.clctList = sorted.map((item, index) => ({
          ...item,
          clctDisplayDt: index === 0 ? item.clctStartDt + ' (최신)' : item.clctStartDt,
        }));
        if (this.clctList.length > 0) {
          this.selectedClctId = this.clctList[0].clctId;
        }
      }).catch(() => {
        this.$swal.fire({ title: '수집 목록 로드 실패', confirmButtonText: '확인', icon: 'error' });
      });
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      if (!this.selectedClctId) {
        this.$swal.fire({ title: '수집일시를 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loadTable = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelAttrListByClctId", {
        params: { 'clctId': this.selectedClctId }
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
          objNm: item.objNm, objNmKr: item.objNmKr, attrNm: item.attrNm, attrNmKr: item.attrNmKr,
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
        params: { 'clctId': this.selectedClctId },
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
    getSubHeader(headers) {
      let result = [];
      headers.filter(i => i.children).forEach(v => { result = result.concat(v.children); });
      return result;
    },
    getRows(rows) {
      const result = {};
      _.forEach(rows, (i, key) => {
        if (i === null) i = '';
        if (['objNm','objNmKr','attrNm','attrNmKr','dataType','dataLen','dataDecimalLen',
             'nullableYn',/* 'termsStndYn','domainStndYn','wordLst', */ 'pkYn','fkYn','defaultVal'].includes(key)) {
          result[key] = i;
        }
      });
      return result;
    },
  },
  created() {
    this.getModelList();
  },
  mounted() {
    this.$resizableGrid();
  },
}
</script>

<style scoped>
.filterWrapper { border-bottom: 1px solid rgba(0,0,0,0.1); }
.filterLabel { font-size: .8rem; white-space: nowrap; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }
.tableSpt { display: flex; justify-content: space-between; align-items: center; padding: 6px 20px; }
.split_bottom_wrap { position: absolute; width: 100%; max-height: 60px; bottom: 0px; border-top: 1px solid rgba(0,0,0,0.1); }
.pagination_wrap { position: relative; width: 100%; }
pre { font-family: 'Roboto'; }
.checkboxStyle { margin-top: 0; padding-top: 0; }
#clTable_table { height: calc(100vh - 64px - 48px - 104px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
</style>
