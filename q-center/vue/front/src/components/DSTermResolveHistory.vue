<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-swap-horizontal-bold</v-icon>&nbsp;&nbsp;한글 변환 이력</h2>
        <v-chip x-small class="ml-2" outlined>매핑 정의서 작성용</v-chip>
        <v-spacer />
        <v-btn small color="primary" outlined v-on:click="load">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
        <v-btn small color="success" class="ml-2" v-on:click="exportCsv">
          <v-icon small left>mdi-file-excel-outline</v-icon>CSV 내보내기
        </v-btn>
      </v-row>

      <!-- 필터 -->
      <v-row no-gutters style="gap:8px;" class="mb-2">
        <v-text-field v-model="filter.inputKrNm" label="원래 입력 (A)" dense outlined hide-details style="max-width:200px;" />
        <v-text-field v-model="filter.resolvedKrNm" label="표준 매핑 (B)" dense outlined hide-details style="max-width:200px;" />
        <v-text-field v-model="filter.changeUserId" label="사용자" dense outlined hide-details style="max-width:140px;" v-if="isAdmin" />
        <v-text-field v-model="filter.dmId" label="모델 ID" dense outlined hide-details style="max-width:200px;" />
        <v-btn small color="primary" v-on:click="load">조회</v-btn>
      </v-row>

      <v-data-table :headers="headers" :items="items" item-key="historySeq"
        :items-per-page="30" dense fixed-header :height="600">
        <template #item.changeDt="{ item }">{{ formatDt(item.changeDt) }}</template>
        <template #item.mapping="{ item }">
          <div style="display:flex; align-items:center; gap:6px;">
            <span style="font-weight:500; color:#1A237E;">{{ item.inputKrNm }}</span>
            <v-icon small color="grey">mdi-arrow-right</v-icon>
            <span style="color:#2E7D32; font-weight:500;">{{ item.resolvedKrNm }}</span>
            <v-chip v-if="item.resolvedEnNm" x-small outlined>{{ item.resolvedEnNm }}</v-chip>
          </div>
        </template>
        <template #item.target="{ item }">
          <span style="font-family:monospace; font-size:.78rem;">
            {{ (item.objOwner ? item.objOwner + '.' : '') + (item.objNm || '') + (item.attrNm ? '.' + item.attrNm : '') }}
          </span>
        </template>
      </v-data-table>
    </v-sheet>
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
    filter: { inputKrNm: '', resolvedKrNm: '', changeUserId: '', dmId: '' },
    headers: [
      { text: '#', value: 'historySeq', width: '80px' },
      { text: '변환 매핑 (A → B)', value: 'mapping', sortable: false },
      { text: '데이터 타입', value: 'resolvedDataType', width: '120px' },
      { text: '길이', value: 'resolvedDataLen', width: '80px' },
      { text: '대상', value: 'target', sortable: false },
      { text: '사용자', value: 'changeUserId', width: '120px' },
      { text: '변환 시각', value: 'changeDt', width: '160px' },
      { text: '사유', value: 'resolveReason' },
    ],
  }),
  mounted() {
    axios.get(this.$APIURL.base + 'api/search/getUserInfo').then(r => {
      this.isAdmin = !!(r.data && (r.data.admin || r.data.role === 'A'));
    });
    this.load();
  },
  activated() { this.load(); },
  methods: {
    load() {
      axios.post(this.$APIURL.base + 'api/termResolve/list', this.filter).then(r => {
        this.items = r.data || [];
      }).catch(() => { this.items = []; });
    },
    formatDt(s) {
      if (!s || s.length < 14) return s;
      return s.substr(0,4) + '-' + s.substr(4,2) + '-' + s.substr(6,2) + ' ' + s.substr(8,2) + ':' + s.substr(10,2);
    },
    exportCsv() {
      const rows = [['#','입력 한글(A)','표준 한글(B)','표준 영문','데이터타입','길이','모델ID','오너','테이블','컬럼','사용자','변환시각','사유']];
      this.items.forEach(i => {
        rows.push([i.historySeq, i.inputKrNm, i.resolvedKrNm, i.resolvedEnNm,
          i.resolvedDataType, i.resolvedDataLen, i.dmId, i.objOwner, i.objNm, i.attrNm,
          i.changeUserId, this.formatDt(i.changeDt), i.resolveReason]);
      });
      const csv = rows.map(r => r.map(c => '"' + String(c == null ? '' : c).replace(/"/g, '""') + '"').join(',')).join('\n');
      const bom = '﻿';
      const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = '한글변환이력_' + this.$getToday() + '.csv';
      document.body.appendChild(a); a.click(); a.remove();
      window.URL.revokeObjectURL(url);
    },
  },
};
</script>
