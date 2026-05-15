<template>
  <v-main>
    <v-sheet class="splitTopWrapper pt-4 pb-4"
      v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
      <!-- 검색 (2줄 분리) -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
        <!-- 1줄: 한글 검색 3개 -->
        <v-row :style="{ alignItems: 'center', margin: '0px 0px 8px 0px' }">
          <span :style="{ fontSize: '.875rem' }">원래 입력 한글 (A)</span>
          <v-text-field class="pr-4 pl-2" v-model="searchInput" v-on:keyup.enter="load"
            clearable clear-icon="mdi-close-circle" color="ndColor"
            single-line dense outlined hide-details :style="{ width: '200px' }" />

          <span :style="{ fontSize: '.875rem' }">표준 매핑 한글 (B)</span>
          <v-text-field class="pr-4 pl-2" v-model="searchResolvedKr" v-on:keyup.enter="load"
            clearable clear-icon="mdi-close-circle" color="ndColor"
            single-line dense outlined hide-details :style="{ width: '200px' }" />

          <span :style="{ fontSize: '.875rem' }">표준 매핑 영문 (B)</span>
          <v-text-field class="pr-4 pl-2" v-model="searchResolvedEn" v-on:keyup.enter="load"
            @input="searchResolvedEn = (searchResolvedEn || '').toUpperCase()"
            clearable clear-icon="mdi-close-circle" color="ndColor"
            single-line dense outlined hide-details :style="{ width: '200px' }" />
        </v-row>

        <!-- 2줄: 변경여부 / 사용자 / 변환일자 + 검색·초기화 -->
        <v-row :style="{ alignItems: 'center', margin: '0px' }">
          <span :style="{ fontSize: '.875rem' }">한글명 변경</span>
          <v-select class="pr-4 pl-2" v-model="searchChanged" :items="changedOptions"
            item-text="text" item-value="value"
            dense outlined hide-details single-line :style="{ width: '110px' }" />

          <template v-if="isAdmin">
            <span :style="{ fontSize: '.875rem' }">사용자</span>
            <v-text-field class="pr-4 pl-2" v-model="searchUser" v-on:keyup.enter="load"
              clearable clear-icon="mdi-close-circle" color="ndColor"
              single-line dense outlined hide-details :style="{ width: '140px' }" />
          </template>

          <span :style="{ fontSize: '.875rem' }">변환일자</span>
          <v-text-field class="pl-2" v-model="searchFromDt" type="date" dense outlined hide-details
            color="ndColor" :style="{ width: '160px' }" />
          <span :style="{ fontSize: '.875rem' }" class="px-1">~</span>
          <v-text-field class="pr-4" v-model="searchToDt" type="date" dense outlined hide-details
            color="ndColor" :style="{ width: '160px' }" />

          <v-btn class="gradient" title="검색" v-on:click="load"
            :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '8px' }">
            <v-icon>search</v-icon>
          </v-btn>
          <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
            :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }">
            <v-icon>restart_alt</v-icon>
          </v-btn>
        </v-row>
      </v-sheet>

      <!-- 액션 영역 (다른 화면과 동일 패턴 — DSWord/DSTerm) -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]"
        class="d-flex flex-nowrap align-center" style="gap: 6px;">
        <v-btn class="gradient" v-on:click="excelDownload" title="엑셀 다운로드"
          :style="{ minWidth: '140px' }">
          <v-icon small left>mdi-file-excel</v-icon>엑셀 다운로드
        </v-btn>
      </v-sheet>
    </v-sheet>

    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ items.length }}건</span>
        <span class="ml-3" style="color:#FB8C00; font-size:.85rem;" v-if="changedCount > 0">
          (한글명 변경 {{ changedCount }}건)
        </span>
      </v-sheet>
      <v-sheet>
        <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense />
      </v-sheet>
    </v-sheet>
    <v-divider />

    <v-data-table id="term_resolve_table" :headers="headers" :items="items" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="historySeq"
      class="px-4 pb-3" :loading="loading" loading-text="잠시만 기다려주세요.">
      <template #[`item.beforeNm`]="{ item }">
        <span style="font-weight:500; color:#1A237E;">{{ item.inputKrNm }}</span>
      </template>
      <template #[`item.afterNm`]="{ item }">
        <span :style="{ color: isChanged(item) ? '#2E7D32' : '#546E7A', fontWeight: isChanged(item) ? 500 : 400 }">
          {{ item.resolvedKrNm || '-' }}
        </span>
      </template>
      <template #[`item.resolvedEnNm`]="{ item }">
        <v-chip v-if="item.resolvedEnNm" x-small outlined>{{ item.resolvedEnNm }}</v-chip>
        <span v-else class="grey--text text--lighten-1">-</span>
      </template>
      <template #[`item.changed`]="{ item }">
        <v-chip v-if="isChanged(item)" x-small color="orange" text-color="white">변경</v-chip>
        <v-chip v-else x-small outlined color="grey">유지</v-chip>
      </template>
      <template #[`item.target`]="{ item }">
        <span v-if="item.objNm" style="font-family:monospace; font-size:.78rem;">
          {{ (item.objOwner ? item.objOwner + '.' : '') + item.objNm + (item.attrNm ? '.' + item.attrNm : '') }}
        </span>
        <span v-else class="grey--text text--lighten-1">-</span>
      </template>
      <template #[`item.changeDt`]="{ item }">{{ formatDt(item.changeDt) }}</template>
      <template #top>
        <v-progress-linear v-show="loading" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loading" color="grey lighten-3">변환 이력이 없습니다.</v-alert>
      </template>
    </v-data-table>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSTermResolveHistory',
  props: ['isMobile'],
  data: () => ({
    items: [],
    isAdmin: false,
    loading: false,
    page: 1,
    itemsPerPage: 20,
    tableViewLengthList: [10, 20, 50, 100, 200],
    searchInput: '',
    searchResolvedKr: '',
    searchResolvedEn: '',
    searchUser: '',
    searchChanged: '',
    searchFromDt: '',
    searchToDt: '',
    changedOptions: [
      { text: '전체', value: '' },
      { text: '변경', value: 'Y' },
      { text: '유지', value: 'N' },
    ],
    headers: [
      { text: '#', value: 'historySeq', width: '70px', align: 'center' },
      { text: '원래 입력 한글 (A)', value: 'beforeNm', sortable: false },
      { text: '표준 매핑 한글 (B)', value: 'afterNm', sortable: false },
      { text: '표준 매핑 영문 (B)', value: 'resolvedEnNm', sortable: false, width: '160px' },
      { text: '한글명 변경', value: 'changed', sortable: false, align: 'center', width: '90px' },
      { text: '데이터 타입', value: 'resolvedDataType', width: '120px', align: 'center' },
      { text: '길이', value: 'resolvedDataLen', width: '80px', align: 'right' },
      { text: '사용자', value: 'changeUserId', width: '110px', align: 'center' },
      { text: '변환 시각', value: 'changeDt', width: '140px', align: 'center' },
      { text: '변환 사유', value: 'resolveReason', sortable: false },
    ],
  }),
  computed: {
    changedCount() {
      return this.items.filter(i => this.isChanged(i)).length;
    },
  },
  mounted() {
    axios.get(this.$APIURL.base + 'api/search/getUserInfo').then(r => {
      this.isAdmin = !!(r.data && (r.data.admin || r.data.role === 'A'));
    }).catch(() => {});
    this.load();
  },
  activated() { this.load(); },
  methods: {
    isChanged(item) {
      const a = (item.inputKrNm || '').trim();
      const b = (item.resolvedKrNm || '').trim();
      return a !== b && b !== '';
    },
    buildFilter() {
      return {
        inputKrNm: this.searchInput,
        resolvedKrNm: this.searchResolvedKr,
        resolvedEnNm: this.searchResolvedEn,
        changeUserId: this.searchUser,
        nmChangedYn: this.searchChanged,
        fromDt: this.searchFromDt ? this.searchFromDt.replace(/-/g, '') + '000000' : '',
        toDt: this.searchToDt ? this.searchToDt.replace(/-/g, '') + '235959' : '',
      };
    },
    load() {
      this.loading = true;
      axios.post(this.$APIURL.base + 'api/termResolve/list', this.buildFilter()).then(r => {
        this.items = r.data || [];
      }).catch(() => { this.items = []; }).finally(() => { this.loading = false; });
    },
    resetSearch() {
      this.searchInput = ''; this.searchResolvedKr = ''; this.searchResolvedEn = '';
      this.searchUser = ''; this.searchChanged = '';
      this.searchFromDt = ''; this.searchToDt = '';
      this.load();
    },
    formatDt(s) {
      if (!s || s.length < 14) return s;
      return s.substr(0,4) + '-' + s.substr(4,2) + '-' + s.substr(6,2) + ' ' + s.substr(8,2) + ':' + s.substr(10,2);
    },
    excelDownload() {
      const f = this.buildFilter();
      const qs = Object.keys(f).filter(k => f[k]).map(k => k + '=' + encodeURIComponent(f[k])).join('&');
      const url = this.$APIURL.base + 'api/termResolve/excel' + (qs ? '?' + qs : '');
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', '한글변환이력_' + this.$getToday() + '.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
  },
};
</script>

<style scoped>
/* DSWord 패턴 동일 — scoped 라서 컴포넌트별로 재정의 필요 */
.splitTopWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}
.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}
</style>
