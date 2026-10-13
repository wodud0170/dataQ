<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-shield-account-outline</v-icon>&nbsp;&nbsp;개인정보 컬럼</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="download">
          <v-icon small left>mdi-file-excel</v-icon>엑셀 내려받기
        </v-btn>
      </v-row>

      <!-- 104번 WP5-16 — 실제 값 = 컬럼 값 → 컬럼에 맞는 승인 용어 값 → 그 용어 도메인 값 → N -->
      <div class="priv-guide">
        컬럼의 개인정보여부·암호화여부는 컬럼에 지정한 값이 없으면 컬럼에 맞는 승인 용어(영문약어명 = 컬럼명)의 값을, 그것도 없으면 그 용어 도메인의 값을 따릅니다.
        개인정보 판단 기준은 「개인정보 비식별 조치 가이드라인」의 식별자 기준입니다.
      </div>

      <!-- 필터 -->
      <v-row no-gutters style="gap:8px; margin-bottom:8px;" align="center">
        <v-select id="privColSys" v-model="filter.sysCd" :items="sysOptions" item-text="text" item-value="value"
          label="시스템 (하위 포함)" dense outlined hide-details clearable style="max-width:220px;" />
        <v-autocomplete id="privColModel" v-model="filter.dmId" :items="modelOptions" item-text="dmNm" item-value="dmId"
          label="데이터 모델" dense outlined hide-details clearable style="max-width:260px;" v-on:change="loadOwners" />
        <v-select id="privColOwner" v-model="filter.objOwner" :items="ownerOptions" label="소유자"
          dense outlined hide-details clearable style="max-width:160px;" :disabled="!filter.dmId" />
        <v-text-field id="privColObj" v-model="filter.objNm" label="테이블" dense outlined hide-details clearable
          style="max-width:180px;" v-on:keyup.enter="load" />
        <v-text-field id="privColAttr" v-model="filter.attrNm" label="컬럼" dense outlined hide-details clearable
          style="max-width:180px;" v-on:keyup.enter="load" />
        <v-checkbox id="privColPrivOnly" v-model="privOnly" label="개인정보만" dense hide-details class="mt-0" />
        <v-checkbox id="privColUnencOnly" v-model="unencOnly" label="암호화 안 된 개인정보만" dense hide-details class="mt-0" />
        <v-btn id="privColSearch" small color="primary" outlined v-on:click="load">조회</v-btn>
      </v-row>

      <!-- 선택 일괄 지정 -->
      <v-row v-if="canManage" no-gutters align="center" style="gap:6px; margin-bottom:8px;">
        <span class="sel-cnt">선택 {{ selected.length }}건</span>
        <v-btn id="privColSetPrivY" x-small outlined color="error" :disabled="!selected.length" v-on:click="setVal('PRIV', 'Y')">개인정보 Y</v-btn>
        <v-btn id="privColSetPrivN" x-small outlined :disabled="!selected.length" v-on:click="setVal('PRIV', 'N')">개인정보 N</v-btn>
        <v-btn id="privColSetPrivInherit" x-small outlined :disabled="!selected.length" v-on:click="setVal('PRIV', '')">개인정보 상속으로</v-btn>
        <v-divider vertical class="mx-2" />
        <v-btn id="privColSetEncrY" x-small outlined color="success" :disabled="!selected.length" v-on:click="setVal('ENCR', 'Y')">암호화 Y</v-btn>
        <v-btn id="privColSetEncrN" x-small outlined :disabled="!selected.length" v-on:click="setVal('ENCR', 'N')">암호화 N</v-btn>
        <v-btn id="privColSetEncrInherit" x-small outlined :disabled="!selected.length" v-on:click="setVal('ENCR', '')">암호화 상속으로</v-btn>
      </v-row>

      <div class="sel-cnt mb-1">총 {{ items.length }}건</div>
      <v-data-table id="privColTable" v-model="selected" :headers="headers" :items="items" item-key="rowKey"
        :show-select="canManage" :loading="loading" :items-per-page="50" dense fixed-header :height="560">
        <template #item.objNm="{ item }">
          <span>{{ item.objOwner ? item.objOwner + '.' : '' }}{{ item.objNm }}</span>
          <div v-if="item.objNmKr" class="sub">{{ item.objNmKr }}</div>
        </template>
        <template #item.attrNm="{ item }">
          <span>{{ item.attrNm }}</span>
          <div v-if="item.attrNmKr" class="sub">{{ item.attrNmKr }}</div>
        </template>
        <template #item.privYn="{ item }">
          <v-chip x-small :color="item.privYn === 'Y' ? 'error' : 'grey lighten-2'" :dark="item.privYn === 'Y'">{{ item.privYn }}</v-chip>
          <span class="src">{{ srcNm(item.privSrc) }}</span>
        </template>
        <template #item.encrYn="{ item }">
          <v-chip x-small :color="item.encrYn === 'Y' ? 'success' : 'grey lighten-2'" :dark="item.encrYn === 'Y'">{{ item.encrYn }}</v-chip>
          <span class="src">{{ srcNm(item.encrSrc) }}</span>
          <v-icon v-if="item.privYn === 'Y' && item.encrYn !== 'Y'" x-small color="warning" title="암호화 안 된 개인정보">mdi-alert</v-icon>
        </template>
      </v-data-table>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
export default {
  name: 'DSPrivColumn',
  props: ['isMobile'],
  computed: {
    // 컬럼 단위 지정은 모델 직접 반영 권한 (관리자 또는 DM_MANAGE)
    canManage() { return this.$can('DM_MANAGE'); },
  },
  data: () => ({
    items: [],
    selected: [],
    loading: false,
    filter: { sysCd: null, dmId: null, objOwner: null, objNm: '', attrNm: '' },
    privOnly: false,
    unencOnly: false,
    sysOptions: [],
    modelOptions: [],
    ownerOptions: [],
    headers: [
      { text: '시스템', value: 'sysNm', width: '120px' },
      { text: '데이터 모델', value: 'dmNm', width: '160px' },
      { text: '테이블', value: 'objNm' },
      { text: '컬럼', value: 'attrNm' },
      { text: '데이터타입', value: 'dataType', width: '110px' },
      { text: '용어', value: 'termsNm', width: '140px' },
      { text: '도메인', value: 'domainNm', width: '140px' },
      { text: '개인정보여부', value: 'privYn', width: '120px' },
      { text: '암호화여부', value: 'encrYn', width: '130px' },
    ],
  }),
  mounted() {
    this.loadOptions();
  },
  methods: {
    loadOptions() {
      axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList').then(res => {
        this.sysOptions = (res.data || []).map(s => ({
          text: '  '.repeat(Math.max(0, (s.level || 1) - 1)) + s.sysNm, value: s.sysCd,
        }));
      });
      axios.get(this.$APIURL.base + 'api/privCol/models').then(res => {
        this.modelOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
      });
    },
    loadOwners() {
      this.filter.objOwner = null;
      this.ownerOptions = [];
      if (!this.filter.dmId) return;
      axios.get(this.$APIURL.base + 'api/privCol/owners', { params: { dmId: this.filter.dmId } }).then(res => {
        this.ownerOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
      });
    },
    params() {
      return Object.assign({}, this.filter, {
        privOnly: this.privOnly ? 'Y' : null,
        unencOnly: this.unencOnly ? 'Y' : null,
      });
    },
    load() {
      this.loading = true;
      this.selected = [];
      axios.post(this.$APIURL.base + 'api/privCol/list', this.params()).then(res => {
        const list = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
        this.items = list.map(r => Object.assign(r, { rowKey: [r.dmId, r.objOwner, r.objNm, r.attrNm].join('|') }));
      }).catch(() => { this.items = []; })
        .finally(() => { this.loading = false; });
    },
    srcNm(src) {
      if (src === 'COLUMN') return '컬럼';
      if (src === 'TERMS') return '용어';
      if (src === 'DOMAIN') return '도메인';
      return '기본값';
    },
    setVal(field, val) {
      const keys = this.selected.map(r => ({ dmId: r.dmId, objOwner: r.objOwner, objNm: r.objNm, attrNm: r.attrNm }));
      axios.post(this.$APIURL.base + 'api/privCol/set', { field, val, keys }).then(res => {
        if (res.data.resultCode !== 200) {
          this.$swal.fire({ title: res.data.resultMessage || '지정 실패', icon: 'error' });
          return;
        }
        this.$swal.fire({ title: `${res.data.contents}건 지정했습니다`, icon: 'success', timer: 1000, showConfirmButton: false });
        this.load();
      });
    },
    download() {
      const p = this.params();
      Object.keys(p).forEach(k => { if (p[k] === null || p[k] === '') delete p[k]; });
      axios.get(this.$APIURL.base + 'api/privCol/download', {
        params: p, responseType: 'blob', headers: { Accept: 'application/vnd.ms-excel' },
      }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `개인정보컬럼_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        link.remove();
      });
    },
  },
};
</script>
<style scoped>
.priv-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.sel-cnt { font-size: .85rem; color: #455A64; }
.sub { font-size: .75rem; color: #78909C; }
.src { font-size: .72rem; color: #78909C; margin-left: 4px; }
</style>
