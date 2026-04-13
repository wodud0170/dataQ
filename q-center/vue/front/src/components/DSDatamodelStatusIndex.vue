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
        <span class="filterLabel">컬럼명</span>
        <v-text-field v-model="searchColumn" @click:clear="searchColumn=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <span class="filterLabel">인덱스명</span>
        <v-text-field v-model="searchIndex" @click:clear="searchIndex=''" clearable
          prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
        </v-text-field>
        <v-btn class="gradient" v-on:click="load" :style="{ padding: '0 12px' }">조회</v-btn>
      </v-row>
    </v-sheet>

    <!-- 목록 카운트 + 페이지 크기 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ filteredItems.length }}건</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 인덱스 목록 -->
    <v-data-table id="dmIndex_table" :headers="headers" :items="filteredItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      class="px-4 pb-3" :loading="loading" loading-text="잠시만 기다려주세요.">
      <template #top>
        <v-progress-linear v-show="loading" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loading" class="text-center">데이터가 존재하지 않습니다.</v-alert>
        <span v-show="loading">잠시만 기다려주세요.</span>
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
  name: 'DSDatamodelStatusIndex',
  props: ['isMobile'],
  watch: {
    filteredItems() {
      this.pageCount = Math.ceil(this.filteredItems.length / this.itemsPerPage);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.filteredItems.length / this.itemsPerPage);
    },
  },
  data: () => ({
    modelList: [],
    clctList: [],
    allItems: [],
    selectedModelId: null,
    selectedClctId: null,
    searchTable: '',
    searchColumn: '',
    searchIndex: '',
    loading: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    headers: [
      { text: '소유자', sortable: true, align: 'center', value: 'objOwner' },
      { text: '테이블명', sortable: true, align: 'center', value: 'tableNm' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'columnNm' },
      { text: '컬럼 순서', sortable: true, align: 'center', value: 'columnPos' },
      { text: '인덱스명', sortable: true, align: 'center', value: 'indexNm' },
      { text: '인덱스 타입', sortable: true, align: 'center', value: 'indexType' },
      { text: '유니크', sortable: true, align: 'center', value: 'uniqueness' },
      { text: '정렬', sortable: false, align: 'center', value: 'sortOrder' },
    ],
  }),
  computed: {
    filteredItems() {
      return this.allItems.filter(item => {
        var tbl = !this.searchTable || (item.tableNm || '').toUpperCase().includes(this.searchTable.toUpperCase());
        var col = !this.searchColumn || (item.columnNm || '').toUpperCase().includes(this.searchColumn.toUpperCase());
        var idx = !this.searchIndex || (item.indexNm || '').toUpperCase().includes(this.searchIndex.toUpperCase());
        return tbl && col && idx;
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
      });
    },
    onModelChange(modelId) {
      this.clctList = [];
      this.selectedClctId = null;
      this.allItems = [];
      if (!modelId) return;
      var _to = new Date().toISOString().substr(0, 10).replace(/-/g, '') + '235959';
      var _from = new Date(new Date() - 365 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10).replace(/-/g, '') + '000000';
      axios.post(this.$APIURL.base + "api/dm/getDataModelClctList", {
        'schId': modelId, 'from': _from, 'to': _to
      }).then((res) => {
        var sorted = res.data.slice().sort(function(a, b) { return b.clctStartDt.localeCompare(a.clctStartDt); });
        this.clctList = sorted.map(function(item, index) {
          return Object.assign({}, item, {
            clctDisplayDt: index === 0 ? item.clctStartDt + ' (최신)' : item.clctStartDt,
          });
        });
        if (this.clctList.length > 0) {
          this.selectedClctId = this.clctList[0].clctId;
        }
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
      this.loading = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelIndexListByClctId", {
        params: { 'clctId': this.selectedClctId }
      }).then((res) => {
        this.allItems = res.data;
        this.loading = false;
      }).catch(() => {
        this.$swal.fire({ title: '인덱스 정보 로드 실패', confirmButtonText: '확인', icon: 'error' });
        this.loading = false;
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
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }
.tableSpt { display: flex; justify-content: space-between; align-items: center; padding: 6px 20px; background: #FAFBFF; }
.split_bottom_wrap { position: absolute; width: 100%; max-height: 60px; bottom: 0px; border-top: 1px solid #E8EAF6; background: #FAFBFF; }
.pagination_wrap { position: relative; width: 100%; }
#dmIndex_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
</style>
