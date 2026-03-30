<template>
  <v-main>
    <!-- 검색과 버튼 영역 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">시스템명</span>
        <v-autocomplete v-model="selectedSystem" :items="systemList"
          clearable dense outlined hide-details
          class="filterInput" :style="{ width: '180px' }" color="ndColor" placeholder="시스템 선택"
          @change="onSystemChange">
        </v-autocomplete>
        <span class="filterLabel">데이터 모델명</span>
        <v-autocomplete v-model="selectedModel" :items="filteredModelList"
          item-text="dataModelNm" item-value="dataModelNm"
          clearable dense outlined hide-details
          class="filterInput" :style="{ width: '220px' }" color="ndColor" placeholder="모델 선택"
          :disabled="filteredModelList.length === 0">
        </v-autocomplete>
        <span class="filterLabel">수집일(From)</span>
        <v-text-field v-model="searchFrom" @click:clear="searchFrom = ''" clearable
          clear-icon="mdi-close-circle" type="date" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '160px' }">
        </v-text-field>
        <span class="filterLabel">수집일(To)</span>
        <v-text-field v-model="searchTo" @click:clear="searchTo = ''" clearable
          clear-icon="mdi-close-circle" type="date" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '160px' }">
        </v-text-field>
        <v-btn class="gradient" v-on:click="getHistoryList" :style="{ padding: '0 12px' }">조회</v-btn>
        <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
          :style="{ padding: '0 5px', minWidth: '40px' }">
          <v-icon>restart_alt</v-icon>
        </v-btn>
      </v-row>
    </v-sheet>

    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ historyItems.length }}건</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 수집이력 목록 -->
    <v-data-table id="dmHistory_table" :headers="historyHeaders" :items="historyItems" :page.sync="page"
      v-model="historyItems" :items-per-page="itemsPerPage" hide-default-header hide-default-footer
      item-key="clctId" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">

      <!-- thead -->
      <template #header="{}">
        <thead class="v-data-table-header">
          <tr>
            <th v-for="(h, i) in historyHeaders" :key="i" class="text-center parent-header td-border-style"
              :rowspan="h.children ? 1 : 2" :colspan="h.children ? h.children.length : 1">
              <pre>{{ h.text }}</pre>
            </th>
          </tr>
          <tr>
            <th v-for="(h1, i1) in getSubHeader(historyHeaders)" :key="i1"
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
            <!-- 모델명 -->
            <span v-if="ci === 'dataModelNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 시스템명 -->
            <span v-else-if="ci === 'dataModelSysNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 데이터소스 -->
            <span v-else-if="ci === 'dataModelDsNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 수집시작일시 -->
            <span v-else-if="ci === 'clctStartDt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 수집완료일시 -->
            <span v-else-if="ci === 'clctEndDt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 완료여부 -->
            <template v-else-if="ci === 'clctCmptnYn'">
              <div :style="{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '48px' }">
                <v-chip x-small :color="c === 'Y' ? 'blue lighten-4' : 'red lighten-4'">
                  {{ c === 'Y' ? '완료' : '미완료' }}
                </v-chip>
              </div>
            </template>
            <!-- 테이블 개수 -->
            <span v-else-if="ci === 'objCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
            <!-- 컬럼 개수 -->
            <span v-else-if="ci === 'attrCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
          </td>
        </tr>
      </template>

      <template #top>
        <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loadTable">
          데이터가 존재하지 않습니다.
        </v-alert>
        <span v-show="loadTable">잠시만 기다려주세요.</span>
      </template>
    </v-data-table>

    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-2 pb-2 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from 'axios';
import _ from 'lodash';

export default {
  name: 'DSDatamodelHistory',
  props: ['isMobile'],
  watch: {
    historyItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    },
  },
  data: () => ({
    allModelList: [],
    selectedSystem: null,
    selectedModel: null,
    searchFrom: '',
    searchTo: '',
    historyItems: [],
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    historyHeaders: [
      { text: '데이터\n모델명',  align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '시스템명',       align: 'center', sortable: false, value: 'dataModelSysNm' },
      { text: '데이터소스',     align: 'center', sortable: false, value: 'dataModelDsNm' },
      { text: '수집\n시작일시', align: 'center', sortable: false, value: 'clctStartDt' },
      { text: '수집\n완료일시', align: 'center', sortable: false, value: 'clctEndDt' },
      { text: '완료\n여부',     align: 'center', sortable: false, value: 'clctCmptnYn' },
      { text: '테이블\n개수',   align: 'center', sortable: false, value: 'objCnt' },
      { text: '컬럼\n개수',    align: 'center', sortable: false, value: 'attrCnt' },
    ],
  }),
  computed: {
    systemList() {
      const systems = this.allModelList
        .map(m => m.dataModelSysNm)
        .filter(s => s);
      return [...new Set(systems)].sort();
    },
    filteredModelList() {
      if (!this.selectedSystem) return this.allModelList;
      return this.allModelList.filter(m => m.dataModelSysNm === this.selectedSystem);
    },
  },
  methods: {
    setListPage() {
      this.pageCount = Math.ceil(this.historyItems.length / this.itemsPerPage);
    },
    onSystemChange() {
      this.selectedModel = null;
    },
    resetSearch() {
      this.selectedSystem = null;
      this.selectedModel = null;
      this.searchFrom = '';
      this.searchTo = '';
    },
    loadModelList() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {
        schNm: null, schSysNm: null,
      }).then((res) => {
        this.allModelList = res.data;
      }).catch(() => {
        this.$swal.fire({ title: '모델 목록 로드 실패', confirmButtonText: '확인', icon: 'error' });
      });
    },
    getSubHeader(headers) {
      let result = [];
      headers
        .filter((i) => i.children)
        .forEach((v) => {
          result = result.concat(v.children);
        });
      return result;
    },
    getRows(rows) {
      const result = {};
      _.forEach(rows, (i, key) => {
        if (i === null) i = '';
        if (key === 'dataModelNm' || key === 'dataModelSysNm' || key === 'dataModelDsNm' ||
          key === 'clctStartDt' || key === 'clctEndDt' || key === 'clctCmptnYn' ||
          key === 'objCnt' || key === 'attrCnt') {
          result[key] = i;
        }
      });
      return result;
    },
    getHistoryList() {
      this.loadTable = true;

      let _from = null;
      let _to   = null;
      if (this.searchFrom) _from = this.searchFrom.replace(/-/g, '') + '000000';
      if (this.searchTo)   _to   = this.searchTo.replace(/-/g, '')   + '235959';

      axios.post(this.$APIURL.base + 'api/dm/getDataModelHistoryList', {
        schSysNm: this.selectedSystem || null,
        schNm:    this.selectedModel  || null,
        from:     _from,
        to:       _to,
      }).then((res) => {
        this.historyItems = res.data;
        this.loadTable = false;
      }).catch(() => {
        this.$swal.fire({
          title: '수집이력 조회에 실패하였습니다.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        this.loadTable = false;
      });
    },
  },
  created() {
    this.loadModelList();
    this.getHistoryList();
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
#dmHistory_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }

pre {
  font-family: 'Roboto';
}
</style>
