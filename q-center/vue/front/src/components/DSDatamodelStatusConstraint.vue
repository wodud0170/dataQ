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
        <span class="filterLabel">제약조건 유형</span>
        <v-select v-model="searchType" :items="constraintTypes" clearable dense outlined hide-details
          class="filterInput" :style="{ width: '120px' }" color="ndColor" placeholder="전체">
        </v-select>
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

    <!-- 제약조건 목록 -->
    <v-data-table id="dmConstraint_table" :headers="headers" :items="filteredItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      class="px-4 pb-3" :loading="loading" loading-text="잠시만 기다려주세요.">
      <template #[`item.constraintType`]="{ item }">
        <v-chip x-small :color="typeColor(item.constraintType)" text-color="white">{{ typeLabel(item.constraintType) }}</v-chip>
      </template>
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
  name: 'DSDatamodelStatusConstraint',
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
    allItems: [],
    selectedModelId: null,
    searchTable: '',
    searchType: null,
    constraintTypes: [
      { text: 'PK (Primary Key)', value: 'P' },
      { text: 'FK (Foreign Key)', value: 'R' },
      { text: 'UK (Unique)', value: 'U' },
      { text: 'CHECK', value: 'C' },
    ],
    loading: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    headers: [
      { text: '소유자', sortable: true, align: 'center', value: 'objOwner' },
      { text: '테이블명', sortable: true, align: 'center', value: 'tableNm' },
      { text: '제약조건명', sortable: true, align: 'center', value: 'constraintNm' },
      { text: '유형', sortable: true, align: 'center', value: 'constraintType' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'columnNm' },
      { text: '컬럼 순서', sortable: true, align: 'center', value: 'columnPos' },
      { text: '참조 테이블', sortable: false, align: 'center', value: 'refTableNm' },
      { text: '참조 컬럼', sortable: false, align: 'center', value: 'refColumnNm' },
      { text: '삭제 규칙', sortable: false, align: 'center', value: 'deleteRule' },
      { text: '상태', sortable: true, align: 'center', value: 'status' },
      { text: '조건식', sortable: false, align: 'left', value: 'searchCondition' },
    ],
  }),
  computed: {
    filteredItems() {
      return this.allItems.filter(item => {
        var tbl = !this.searchTable || (item.tableNm || '').toUpperCase().includes(this.searchTable.toUpperCase());
        var tp = !this.searchType || item.constraintType === this.searchType;
        return tbl && tp;
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
      this.allItems = [];
      if (!modelId) return;
      this.load();
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loading = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelConstraintListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.allItems = res.data;
        this.loading = false;
      }).catch(() => {
        this.$swal.fire({ title: '제약조건 정보 로드 실패', confirmButtonText: '확인', icon: 'error' });
        this.loading = false;
      });
    },
    typeColor(type) {
      return { P: 'indigo', R: 'teal', U: 'orange', C: 'grey' }[type] || 'grey';
    },
    typeLabel(type) {
      return { P: 'PK', R: 'FK', U: 'UK', C: 'CHECK' }[type] || type;
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
#dmConstraint_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
</style>
