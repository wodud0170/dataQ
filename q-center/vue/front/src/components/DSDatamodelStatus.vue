<template>
  <v-main>
    <Split direction="vertical" :style="{ overflow: 'hidden' }">
      <SplitArea :size="100" :style="{ overflow: 'hidden', position: 'relative' }">
        <!-- 검색과 버튼 영역 -->
        <v-sheet class="splitTopWrapper pt-4 pb-4"
          v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
          <!-- 검색 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-row :style="{ alignItems: 'center', margin: '0px' }">
              <span :style="{ fontSize: '.875rem' }">모델명</span>
              <!-- 모델명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchModel" v-on:keyup.enter="getDataModelStatsList"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getDataModelStatsList"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ dmStatusItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <!-- 데이터 모델 현황 목록 -->
        <v-data-table id="dmStatus_table" :headers="dataModelHeaders" :items="dmStatusItems" :page.sync="page"
          v-model="dmStatusItems" :items-per-page="itemsPerPage" hide-default-header hide-default-footer
          item-key="dataModelId" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">

          <!-- thead -->
          <template #header="{}">
            <thead class="v-data-table-header">
              <tr>
                <!-- thead -->
                <th v-for="(h, i) in dataModelHeaders" :key="i" class="text-center parent-header td-border-style"
                  :rowspan="h.children ? 1 : 2" :colspan="h.children ? h.children.length : 1">
                  <pre>{{ h.text }}</pre>
                </th>
              </tr>
              <tr>
                <!-- sub thead -->
                <th v-for="(h1, i1) in getSubHeader(dataModelHeaders)" :key="i1"
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
                <span v-if="ci === 'dataModelNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 데이터 소스 -->
                <span v-else-if="ci === 'dataModelDsNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 테이블 개수 -->
                <span v-else-if="ci === 'objCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 컬럼 개수 -->
                <span v-else-if="ci === 'attrCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 수집일시 -->
                <span v-else-if="ci === 'clctDt'" :style="{ margin: '0px 16px' }">{{ c || '-' }}</span>
                <!-- 최신 표준화 진단일시 -->
                <span v-else-if="ci === 'diagDt'" :style="{ margin: '0px 16px' }">{{ c || '-' }}</span>
                <!-- 최신 구조변경 진단일시 -->
                <span v-else-if="ci === 'structDiagDt'" :style="{ margin: '0px 16px' }">{{ c || '-' }}</span>
                <!-- 표준화 준수율 -->
                <template v-else-if="ci === 'diagStndRate'">
                  <div :style="{ textAlign: 'center', padding: '4px 8px' }">
                    <template v-if="props.item.diagDt">
                      <v-chip x-small :color="props.item.diagStndRate >= 90 ? 'green' : props.item.diagStndRate >= 70 ? 'orange' : 'red'" text-color="white">
                        {{ props.item.diagStndRate }}%
                      </v-chip>
                    </template>
                    <span v-else class="grey--text text-caption">미진단</span>
                  </div>
                </template>
                <!-- 구조진단 일치율 -->
                <template v-else-if="ci === 'structDiagRate'">
                  <div :style="{ textAlign: 'center', padding: '4px 8px' }">
                    <template v-if="props.item.structDiagRate != null">
                      <v-chip x-small :color="props.item.structDiagRate >= 90 ? 'green' : props.item.structDiagRate >= 70 ? 'orange' : 'red'" text-color="white">
                        {{ props.item.structDiagRate }}%
                      </v-chip>
                    </template>
                    <span v-else class="grey--text text-caption">미진단</span>
                  </div>
                </template>
                <!-- DDL 다운로드 (DBMS 선택 드롭다운) -->
                <template v-else-if="ci === 'ddl'">
                  <div :style="{ textAlign: 'center' }">
                    <v-menu offset-y>
                      <template #activator="{ on, attrs }">
                        <v-btn icon small color="ndColor" title="DDL 다운로드 (DBMS 선택)" v-bind="attrs" v-on="on" @click.stop>
                          <v-icon small>mdi-file-download</v-icon>
                        </v-btn>
                      </template>
                      <v-list dense>
                        <v-list-item @click="downloadDdl(props.item, '')">
                          <v-list-item-icon class="mr-2"><v-icon small>mdi-auto-fix</v-icon></v-list-item-icon>
                          <v-list-item-title>자동 (연결된 DBMS, 미연결·규칙 없는 DBMS 는 Oracle)</v-list-item-title>
                        </v-list-item>
                        <v-divider />
                        <v-list-item v-for="d in ddlDbmsList" :key="d.value" @click="downloadDdl(props.item, d.value)">
                          <v-list-item-icon class="mr-2"><v-icon small>{{ d.icon }}</v-icon></v-list-item-icon>
                          <v-list-item-title>{{ d.text }}</v-list-item-title>
                        </v-list-item>
                      </v-list>
                    </v-menu>
                  </div>
                </template>
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
          <!-- 페이지네이션 -->
          <div class="text-center px-4 pt-4 pb-4 pagination_wrap">
            <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
              color="ndColor" :total-visible="10"></v-pagination>
          </div>
        </v-sheet>
      </SplitArea>
    </Split>
    <!-- 110 4.12-2·6-b — 하단 테이블·컬럼 상세, 수집이력 선택, 용어 상세 정보 모달은 진입이 없어 지웠다
         (테이블·컬럼은 데이터 모델 > 테이블·컬럼 메뉴, 수집이력은 데이터 모델 수집이력 메뉴) -->
  </v-main>
</template>
<script>
import axios from 'axios';

export default {
  name: 'DSDatamodelStatus',
  props: ['isMobile'],
  watch: {
    dmStatusItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    },
  },
  data: () => ({
    // 104번 WP3 — DDL 규칙이 있는 DBMS (값은 데이터소스 DBMS 유형)
    ddlDbmsList: [
      { value: 'Oracle', text: 'Oracle', icon: 'mdi-database' },
      { value: 'Tibero', text: 'Tibero', icon: 'mdi-database' },
      { value: 'PostgreSQL', text: 'PostgreSQL', icon: 'mdi-elephant' },
      { value: 'MariaDB', text: 'MariaDB', icon: 'mdi-database' },
      { value: 'SQLServer', text: 'SQL Server', icon: 'mdi-database' },
    ],
    dmStatusItems: [],
    // 모델명 검색어
    searchModel: '',
    // 테이블 로딩
    loadTable: true,
    // 페이지 시작
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 아이템의 수
    itemsPerPage: 10,
    // 상단 테이블 헤더
    dataModelHeaders: [
      { text: '데이터 모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '데이터소스', sortable: false, align: 'center', value: 'dataModelDsNm' },
      { text: '테이블\n개수', sortable: false, align: 'center', value: 'objCnt' },
      { text: '컬럼\n개수', sortable: false, align: 'center', value: 'attrCnt' },
      { text: '최신\n수집일시', sortable: false, align: 'center', value: 'clctDt' },
      { text: '최신\n표준화 진단일시', sortable: false, align: 'center', value: 'diagDt' },
      { text: '최신\n구조변경 진단일시', sortable: false, align: 'center', value: 'structDiagDt' },
      { text: '표준화\n준수율', sortable: false, align: 'center', value: 'diagStndRate' },
      { text: '구조진단\n일치율', sortable: false, align: 'center', value: 'structDiagRate' },
      { text: 'DDL', sortable: false, align: 'center', value: 'ddl' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
  }),
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.dmStatusItems.length / this.itemsPerPage);
      if (this.page > this.pageCount) this.page = Math.max(1, this.pageCount);
    },
    downloadDdl(item, dbType) {
      if (!item || !item.dataModelId) return;
      let url = this.$APIURL.base + 'api/dm/downloadDdl?dataModelId=' + encodeURIComponent(item.dataModelId);
      if (dbType) url += '&dbType=' + encodeURIComponent(dbType);
      const a = document.createElement('a');
      a.href = url;
      a.download = '';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchModel = ''
    },
    resetSearch() {
      this.searchModel = '';
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
      const keyOrder = ['dataModelNm', 'dataModelDsNm', 'objCnt', 'attrCnt', 'clctDt', 'diagDt', 'structDiagDt', 'diagStndRate', 'structDiagRate', 'ddl'];
      const result = {};
      keyOrder.forEach(key => {
        result[key] = rows[key] != null ? rows[key] : '';
      });
      return result;
    },
    getDataModelStatsList() {
      this.loadTable = false;

      try {

        let schNm = null;
        if (this.searchModel !== '') {
          schNm = this.searchModel
        }

        let _url = this.$APIURL.base + "api/dm/getDataModelStatsList";

        axios.post(_url, {
          'schNm': schNm
        }).then((res) => {
          let _data = res.data;

          // 데이터를 테이블에 맞게 수정해야 함. 순서 꼭 지킬 것.
          let _newArr = [];

          for (let i = 0; i < _data.length; i++) {
            let _obj = {};

            _obj.dataModelNm = _data[i].dataModelNm;
            _obj.dataModelDsNm = _data[i].dataModelDsNm;
            _obj.objCnt = _data[i].objCnt || 0;
            _obj.attrCnt = _data[i].attrCnt || 0;
            _obj.clctDt = _data[i].clctDt || '';
            _obj.diagDt = _data[i].diagDt || '';
            _obj.structDiagDt = _data[i].structDiagDt || '';
            _obj.diagStndRate = _data[i].diagStndRate || 0;
            _obj.structDiagRate = _data[i].structDiagRate >= 0 ? _data[i].structDiagRate : null;
            _obj.dataModelId = _data[i].dataModelId;

            _newArr.push(_obj);
          }

          this.dmStatusItems = _newArr;
          this.loadTable = false;
        }).catch((err) => {
          this.$swal.fire({
            title: '용어 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }
    },
  },
  created() {
    this.getDataModelStatsList();
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
  },
}
</script>

<style scoped>
.splitTopWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}

.split_bottom_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 0px;
  border-top: 1px solid #E8EAF6;
  background: #FAFBFF;
}

.pagination_wrap {
  position: relative;
  width: 100%;
}

#dmStatus_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

pre {
  font-family: 'Roboto';
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}
</style>
