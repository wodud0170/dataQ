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
        <span class="filterLabel">수집일시</span>
        <v-select v-model="selectedClctId" :items="clctList"
          item-text="clctDisplayDt" item-value="clctId"
          clearable dense outlined hide-details
          class="filterInput" :style="{ width: '300px' }" color="ndColor"
          placeholder="수집일시 선택" :disabled="clctList.length === 0">
        </v-select>
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
      </v-row>
    </v-sheet>

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
  </v-main>
</template>

<script>
import axios from 'axios';

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
    clctList: [],
    dmTableAllItems: [],
    selectedModelId: null,
    selectedClctId: null,
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
      this.clctList = [];
      this.selectedClctId = null;
      this.dmTableAllItems = [];
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
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedClctId }
      }).then((res) => {
        this.dmTableAllItems = res.data;
        this.loadTable = false;
      }).catch(() => {
        this.$swal.fire({ title: '테이블 정보 로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
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
#dmTable_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
</style>
