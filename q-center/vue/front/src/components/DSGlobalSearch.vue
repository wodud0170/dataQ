<template>
  <v-main>
    <v-sheet class="pa-4" style="height: 100%; overflow-y: auto; background-color: #F5F7FA;">
      <!-- 검색 입력 -->
      <v-sheet class="pa-4 mb-4" elevation="1" rounded>
        <v-row align="center" no-gutters>
          <v-col cols="auto" class="mr-2">
            <v-icon color="indigo">mdi-magnify</v-icon>
          </v-col>
          <v-col>
            <v-text-field
              v-model="keyword"
              placeholder="검색어를 입력하세요"
              dense hide-details outlined single-line
              @keyup.enter="doSearch"
              clearable
            ></v-text-field>
          </v-col>
          <v-col cols="auto" class="ml-2">
            <v-btn color="indigo" dark @click="doSearch">검색</v-btn>
          </v-col>
        </v-row>
      </v-sheet>

      <!-- 로딩 -->
      <v-progress-linear v-if="loading" indeterminate color="indigo" class="mb-4"></v-progress-linear>

      <!-- 검색 결과 없음 -->
      <v-alert v-if="searched && !loading && totalCount === 0" type="info" outlined>
        검색 결과가 없습니다.
      </v-alert>

      <!-- 검색 결과 섹션 -->
      <template v-if="searched && !loading">
        <!-- 단어 -->
        <v-expansion-panels v-model="panels" multiple class="mb-3">
          <v-expansion-panel v-if="results.words && results.words.length > 0">
            <v-expansion-panel-header>
              <span><v-icon small class="mr-1">mdi-alphabetical</v-icon> 단어 ({{ results.words.length }}건)</span>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-data-table
                :headers="wordHeaders"
                :items="results.words"
                dense hide-default-footer
                :items-per-page="-1"
                class="search-result-table"
                @click:row="(item) => navigateTo('word')"
              ></v-data-table>
            </v-expansion-panel-content>
          </v-expansion-panel>

          <!-- 용어 -->
          <v-expansion-panel v-if="results.terms && results.terms.length > 0">
            <v-expansion-panel-header>
              <span><v-icon small class="mr-1">mdi-file-document-outline</v-icon> 용어 ({{ results.terms.length }}건)</span>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-data-table
                :headers="termHeaders"
                :items="results.terms"
                dense hide-default-footer
                :items-per-page="-1"
                class="search-result-table"
                @click:row="(item) => navigateTo('term')"
              ></v-data-table>
            </v-expansion-panel-content>
          </v-expansion-panel>

          <!-- 도메인 -->
          <v-expansion-panel v-if="results.domains && results.domains.length > 0">
            <v-expansion-panel-header>
              <span><v-icon small class="mr-1">mdi-domain</v-icon> 도메인 ({{ results.domains.length }}건)</span>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-data-table
                :headers="domainHeaders"
                :items="results.domains"
                dense hide-default-footer
                :items-per-page="-1"
                class="search-result-table"
                @click:row="(item) => navigateTo('domain')"
              ></v-data-table>
            </v-expansion-panel-content>
          </v-expansion-panel>

          <!-- 컬럼 -->
          <v-expansion-panel v-if="results.columns && results.columns.length > 0">
            <v-expansion-panel-header>
              <span><v-icon small class="mr-1">mdi-table-column</v-icon> 테이블/컬럼 ({{ results.columns.length }}건)</span>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-data-table
                :headers="columnHeaders"
                :items="results.columns"
                dense hide-default-footer
                :items-per-page="-1"
                class="search-result-table"
                @click:row="(item) => navigateTo('datamodelStatus')"
              ></v-data-table>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>
      </template>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios'
import { eventBus } from './../eventBus.js'

export default {
  name: 'DSGlobalSearch',
  props: ['isMobile'],
  data: () => ({
    keyword: '',
    loading: false,
    searched: false,
    results: {
      words: [],
      terms: [],
      domains: [],
      columns: [],
    },
    panels: [0, 1, 2, 3],
    wordHeaders: [
      { text: '단어명', value: 'wordNm', sortable: false },
      { text: '영문약어명', value: 'wordEngAbrvNm', sortable: false },
      { text: '영문명', value: 'wordEngNm', sortable: false },
      { text: '설명', value: 'wordDesc', sortable: false },
    ],
    termHeaders: [
      { text: '용어명', value: 'termsNm', sortable: false },
      { text: '영문약어명', value: 'termsEngAbrvNm', sortable: false },
      { text: '설명', value: 'termsDesc', sortable: false },
      { text: '도메인명', value: 'domainNm', sortable: false },
    ],
    domainHeaders: [
      { text: '도메인명', value: 'domainNm', sortable: false },
      { text: '도메인그룹명', value: 'domainGroupNm', sortable: false },
      { text: '데이터타입', value: 'dataType', sortable: false },
      { text: '데이터길이', value: 'dataLen', sortable: false },
    ],
    columnHeaders: [
      { text: '테이블명', value: 'objNm', sortable: false },
      { text: '테이블한글명', value: 'objNmKr', sortable: false },
      { text: '컬럼명', value: 'attrNm', sortable: false },
      { text: '컬럼한글명', value: 'attrNmKr', sortable: false },
      { text: '데이터타입', value: 'dataType', sortable: false },
    ],
  }),
  computed: {
    totalCount() {
      return (this.results.words ? this.results.words.length : 0)
        + (this.results.terms ? this.results.terms.length : 0)
        + (this.results.domains ? this.results.domains.length : 0)
        + (this.results.columns ? this.results.columns.length : 0);
    },
  },
  created() {
    eventBus.$on('globalSearch', this.onGlobalSearch);
  },
  beforeDestroy() {
    eventBus.$off('globalSearch', this.onGlobalSearch);
  },
  methods: {
    onGlobalSearch(keyword) {
      this.keyword = keyword;
      this.doSearch();
    },
    async doSearch() {
      if (!this.keyword || !this.keyword.trim()) return;
      this.loading = true;
      this.searched = false;
      try {
        const url = this.$APIURL.base + 'api/std/search';
        const res = await axios.get(url, { params: { keyword: this.keyword.trim() } });
        this.results = res.data;
        this.panels = [0, 1, 2, 3];
      } catch (e) {
        console.error('통합 검색 오류:', e);
        this.results = { words: [], terms: [], domains: [], columns: [] };
      } finally {
        this.loading = false;
        this.searched = true;
      }
    },
    navigateTo(tabName) {
      this.$emit('addTabItem', this.getTabTitle(tabName), tabName);
    },
    getTabTitle(name) {
      const map = {
        word: '단어',
        term: '용어',
        domain: '도메인',
        datamodelStatus: '데이터 모델 현황',
      };
      return map[name] || name;
    },
  },
}
</script>

<style scoped>
.search-result-table >>> tbody tr {
  cursor: pointer;
}
.search-result-table >>> tbody tr:hover {
  background-color: #E8EAF6 !important;
}
</style>
