<template>
  <v-main>
    <!-- 상태별 카운트 카드 -->
    <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
      <v-row>
        <v-col cols="3">
          <v-card outlined class="text-center pa-3" :class="{ 'selected-card': activeFilter === 'ALL' }"
            @click="setFilter('ALL')" style="cursor: pointer;">
            <div class="text-subtitle-2 grey--text">전체</div>
            <div class="text-h5 font-weight-bold">{{ totalCount }}</div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card outlined class="text-center pa-3" :class="{ 'selected-card': activeFilter === 'PENDING' }"
            @click="setFilter('PENDING')" style="cursor: pointer;">
            <div class="text-subtitle-2 orange--text">승인대기</div>
            <div class="text-h5 font-weight-bold orange--text">{{ pendingCount }}</div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card outlined class="text-center pa-3" :class="{ 'selected-card': activeFilter === 'APPROVED' }"
            @click="setFilter('APPROVED')" style="cursor: pointer;">
            <div class="text-subtitle-2 green--text">승인완료</div>
            <div class="text-h5 font-weight-bold green--text">{{ approvedCount }}</div>
          </v-card>
        </v-col>
        <v-col cols="3">
          <v-card outlined class="text-center pa-3" :class="{ 'selected-card': activeFilter === 'REJECTED' }"
            @click="setFilter('REJECTED')" style="cursor: pointer;">
            <div class="text-subtitle-2 red--text">반려</div>
            <div class="text-h5 font-weight-bold red--text">{{ rejectedCount }}</div>
          </v-card>
        </v-col>
      </v-row>
    </v-sheet>

    <!-- 필터/검색 -->
    <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
      <v-row align="center" no-gutters>
        <v-col cols="2">
          <v-select dense hide-details v-model="typeFilter" :items="typeItems" label="유형" color="ndColor"
            :menu-props="{ top: false, offsetY: true }"></v-select>
        </v-col>
        <v-col cols="4" class="pl-4">
          <date-picker v-model="datetimeitem" type="datetime" valueType="format" range></date-picker>
        </v-col>
        <v-col cols="1" class="pl-2">
          <v-btn class="gradient" title="검색" @click="loadData" style="min-width: 45px;">
            <v-icon>search</v-icon>
          </v-btn>
        </v-col>
      </v-row>
    </v-sheet>

    <!-- 목록 테이블 -->
    <v-data-table :headers="tableHeaders" :items="filteredItems" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="key" class="px-4 pb-3"
      :loading="loading" loading-text="Loading" @click:row="selectRow">
      <template v-slot:[`item.aprvStat`]="{ item }">
        <v-chip small :color="getStatusColor(item.aprvStatRaw)" :text-color="getStatusTextColor(item.aprvStatRaw)">
          {{ item.aprvStat }}
        </v-chip>
      </template>
      <template v-slot:no-data>
        <v-alert>데이터가 존재하지 않습니다.</v-alert>
      </template>
    </v-data-table>

    <!-- 페이지네이션 -->
    <div class="text-center px-4 pt-4 pb-4" v-show="pageCount > 1">
      <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
        color="ndColor" :total-visible="10"></v-pagination>
    </div>

    <!-- 상세 패널 -->
    <v-sheet v-if="selectedItem" class="mx-4 mb-4 pa-4" outlined style="border-radius: 8px;">
      <v-row>
        <v-col cols="12">
          <div class="text-subtitle-1 font-weight-bold mb-2">
            <v-chip small :color="getStatusColor(selectedItem.aprvStatRaw)"
              :text-color="getStatusTextColor(selectedItem.aprvStatRaw)" class="mr-2">
              {{ selectedItem.aprvStat }}
            </v-chip>
            {{ selectedItem.reqItemNm }}
            <span class="grey--text text-body-2 ml-2">({{ selectedItem.reqTp }})</span>
          </div>
        </v-col>
      </v-row>
      <v-divider class="mb-3"></v-divider>
      <v-row>
        <v-col cols="6">
          <div class="detail-row"><span class="detail-label">항목명:</span> {{ selectedItem.reqItemNm }}</div>
          <div class="detail-row"><span class="detail-label">영문명:</span> {{ selectedItem.reqItemEngNm }}</div>
          <div class="detail-row"><span class="detail-label">유형:</span> {{ selectedItem.reqTp }}</div>
        </v-col>
        <v-col cols="6">
          <div class="detail-row"><span class="detail-label">요청일:</span> {{ selectedItem.reqCretDt }}</div>
          <div class="detail-row"><span class="detail-label">처리일:</span> {{ selectedItem.aprvStatUpdtDt || '-' }}</div>
          <div class="detail-row"><span class="detail-label">승인자:</span> {{ selectedItem.aprvUserId || '-' }}</div>
        </v-col>
      </v-row>
      <v-row v-if="selectedItem.aprvStatUpdtRsn">
        <v-col cols="12">
          <v-alert type="error" dense outlined class="mt-2">
            <div class="text-subtitle-2">반려 사유</div>
            {{ selectedItem.aprvStatUpdtRsn }}
          </v-alert>
        </v-col>
      </v-row>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from "axios";
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';

export default {
  name: 'DSMyRequest',
  components: { DatePicker },
  props: ['isMobile'],
  data: () => ({
    allItems: [],
    loading: true,
    page: 1,
    pageCount: 0,
    itemsPerPage: 20,
    activeFilter: 'ALL',
    typeFilter: '전체',
    typeItems: ['전체', '용어', '단어', '도메인'],
    datetimeitem: [null, null],
    selectedItem: null,
    tableHeaders: [
      { text: '항목명', value: 'reqItemNm', align: 'center', sortable: false },
      { text: '영문명', value: 'reqItemEngNm', align: 'center', sortable: false },
      { text: '유형', value: 'reqTp', align: 'center', sortable: false },
      { text: '상태', value: 'aprvStat', align: 'center', sortable: false },
      { text: '요청일', value: 'reqCretDt', align: 'center', sortable: false },
      { text: '처리일', value: 'aprvStatUpdtDt', align: 'center', sortable: false },
      { text: '승인자', value: 'aprvUserId', align: 'center', sortable: false },
      { text: '사유', value: 'aprvStatUpdtRsn', align: 'center', sortable: false },
    ],
  }),
  computed: {
    totalCount() { return this.allItems.length; },
    pendingCount() { return this.allItems.filter(i => i.aprvStatRaw === 0).length; },
    approvedCount() { return this.allItems.filter(i => i.aprvStatRaw === 2).length; },
    rejectedCount() { return this.allItems.filter(i => i.aprvStatRaw === 3).length; },
    filteredItems() {
      let items = this.allItems;
      if (this.activeFilter === 'PENDING') items = items.filter(i => i.aprvStatRaw === 0);
      else if (this.activeFilter === 'APPROVED') items = items.filter(i => i.aprvStatRaw === 2);
      else if (this.activeFilter === 'REJECTED') items = items.filter(i => i.aprvStatRaw === 3);
      if (this.typeFilter !== '전체') items = items.filter(i => i.reqTp === this.typeFilter);
      this.pageCount = Math.ceil(items.length / this.itemsPerPage);
      return items;
    }
  },
  created() {
    this.loadData();
  },
  methods: {
    setFilter(filter) {
      this.activeFilter = filter;
      this.page = 1;
      this.selectedItem = null;
    },
    getStatusColor(stat) {
      if (stat === 0) return 'orange lighten-4';
      if (stat === 1) return 'blue lighten-4';
      if (stat === 2) return 'green lighten-4';
      if (stat === 3) return 'red lighten-4';
      return 'grey lighten-3';
    },
    getStatusTextColor(stat) {
      if (stat === 0) return 'orange darken-3';
      if (stat === 1) return 'blue darken-3';
      if (stat === 2) return 'green darken-3';
      if (stat === 3) return 'red darken-3';
      return 'grey darken-1';
    },
    selectRow(item) {
      this.selectedItem = item;
    },
    loadData() {
      this.loading = true;
      let _fromDt = this.datetimeitem[0] ? this.$dateToString(this.datetimeitem[0]) : null;
      let _toDt = this.datetimeitem[1] ? this.$dateToString(this.datetimeitem[1]) : null;
      if (_fromDt === '19700101090000') _fromDt = null;
      if (_toDt === '19700101090000') _toDt = null;

      let params = {
        from: _fromDt,
        to: _toDt,
        statusList: null,
        limit: 0
      };

      axios.post(this.$APIURL.base + "api/std/getMyRequestList", params)
        .then(res => {
          if (res.status === 200) {
            for (let i = 0; i < res.data.length; i++) {
              let raw = res.data[i].aprvStat;
              res.data[i].aprvStatRaw = raw;
              if (raw === 0) res.data[i].aprvStat = "승인대기";
              else if (raw === 1) res.data[i].aprvStat = "검토중";
              else if (raw === 2) res.data[i].aprvStat = "승인완료";
              else if (raw === 3) res.data[i].aprvStat = "반려";

              let tp = res.data[i].reqTp;
              if (tp === 'DOMAIN') res.data[i].reqTp = "도메인";
              else if (tp === 'TERMS') res.data[i].reqTp = "용어";
              else if (tp === 'WORD') res.data[i].reqTp = "단어";

              if (res.data[i].reqItemNm === null) {
                res.data[i].reqItemNm = "-";
                res.data[i].aprvStatUpdtRsn = "삭제됨";
              }
              res.data[i].key = i;
            }
            this.allItems = res.data;
            this.selectedItem = null;
          }
        })
        .catch(err => {
          console.error(err);
        })
        .finally(() => {
          this.loading = false;
        });
    }
  },
  mounted() {
    this.$resizableGrid();
  }
}
</script>

<style scoped>
.selected-card {
  border: 2px solid #3F51B5 !important;
  background-color: #E8EAF6 !important;
}
.detail-row {
  margin-bottom: 4px;
  font-size: 0.875rem;
}
.detail-label {
  font-weight: 600;
  color: #546E7A;
  display: inline-block;
  width: 80px;
}
</style>
