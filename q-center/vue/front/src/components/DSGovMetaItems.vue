<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-table-cog</v-icon>&nbsp;&nbsp;테이블·컬럼</h2>
        <v-spacer />
        <v-menu offset-y>
          <template v-slot:activator="{ on, attrs }">
            <v-btn class="tb-btn" outlined color="primary" v-bind="attrs" v-on="on"><v-icon small left>mdi-file-excel</v-icon>엑셀</v-btn>
          </template>
          <v-list dense>
            <v-list-item v-on:click="download('tbl')"><v-list-item-title>테이블 항목 내려받기</v-list-item-title></v-list-item>
            <v-list-item :disabled="!filter.dmId" v-on:click="download('col')"><v-list-item-title>컬럼 항목 내려받기 (모델 선택)</v-list-item-title></v-list-item>
            <template v-if="canManage">
              <v-divider />
              <v-list-item v-on:click="pickUpload('tbl')"><v-list-item-title>테이블 항목 올리기</v-list-item-title></v-list-item>
              <v-list-item v-on:click="pickUpload('col')"><v-list-item-title>컬럼 항목 올리기</v-list-item-title></v-list-item>
            </template>
          </v-list>
        </v-menu>
        <input ref="upFile" type="file" accept=".xlsx" style="display:none" v-on:change="upload" />
      </v-row>

      <div class="gm-guide">
        별표 4 테이블·컬럼 항목입니다. 엑셀은 이 화면의 양식(내려받은 파일)으로 올립니다 — 기관메타 업로드 양식은 연계 규격을 받은 뒤 맞춥니다.
        개인정보여부·암호화여부는 [데이터 모델 > 개인정보 컬럼] 과 같은 값입니다.
      </div>

      <!-- 필터 -->
      <v-row no-gutters style="gap:8px; margin-bottom:8px;" align="center">
        <v-select v-model="filter.sysCd" :items="sysOptions" item-text="text" item-value="value" label="시스템 (하위 포함)"
          dense outlined hide-details clearable style="max-width:200px;" />
        <v-autocomplete id="govItemsModel" v-model="filter.dmId" :items="modelOptions" item-text="dmNm" item-value="dmId" label="데이터 모델"
          dense outlined hide-details clearable style="max-width:240px;" v-on:change="loadOwners" />
        <v-select v-model="filter.objOwner" :items="ownerOptions" label="소유자" dense outlined hide-details clearable
          style="max-width:140px;" :disabled="!filter.dmId" />
        <v-text-field id="govItemsObj" v-model="filter.objNm" label="테이블명" dense outlined hide-details clearable style="max-width:160px;" v-on:keyup.enter="load" />
        <v-select v-model="filter.govSendYn" :items="ynAll" label="전송대상" dense outlined hide-details style="max-width:110px;" />
        <v-select v-model="filter.openTp" :items="openTpFilter" label="공개/비공개여부" dense outlined hide-details style="max-width:150px;" />
        <v-select v-model="filter.tblTp" :items="['', ...tblTpOptions]" label="테이블유형" dense outlined hide-details style="max-width:120px;" />
        <v-select v-model="filter.privYn" :items="privFilter" label="개인정보" dense outlined hide-details style="max-width:150px;" />
        <v-checkbox v-model="unencOnly" label="암호화 안 된 개인정보" dense hide-details class="mt-0" />
        <v-btn id="govItemsSearch" small color="primary" outlined v-on:click="load">조회</v-btn>
      </v-row>

      <v-row v-if="canManage" no-gutters align="center" style="gap:6px; margin-bottom:8px;">
        <span class="gm-note">선택 {{ selected.length }}건</span>
        <v-btn id="govItemsSendY" x-small outlined color="primary" :disabled="!selected.length" v-on:click="applyTbl({ govSendYn: 'Y' })">전송대상 추가</v-btn>
        <v-btn id="govItemsSendN" x-small outlined :disabled="!selected.length" v-on:click="applyTbl({ govSendYn: 'N' })">전송대상 제외</v-btn>
        <v-btn id="govItemsBulk" x-small outlined :disabled="!selected.length" v-on:click="openBulk">일괄 적용</v-btn>
      </v-row>

      <div class="gm-note mb-1">총 {{ items.length }}건 · 테이블을 누르면 컬럼 항목이 열립니다</div>
      <v-data-table id="govItemsTable" v-model="selected" :headers="headers" :items="items" item-key="rowKey" :show-select="canManage"
        :loading="loading" :items-per-page="50" dense fixed-header :height="520" v-on:click:row="openCols">
        <template #item.objNm="{ item }">
          <span>{{ item.objOwner ? item.objOwner + '.' : '' }}{{ item.objNm }}</span>
          <div v-if="item.objNmKr" class="gm-sub2">{{ item.objNmKr }}</div>
        </template>
        <template #item.govSendYn="{ item }">
          <v-chip x-small :color="item.govSendYn === 'Y' ? 'primary' : 'grey lighten-2'" :dark="item.govSendYn === 'Y'">{{ item.govSendYn }}</v-chip>
        </template>
        <template #item.openTp="{ item }">
          <span>{{ item.openTp || '-' }}</span>
          <v-icon v-if="item.openTp && item.openTp !== '공개' && (!item.openLimitReason || !item.openLimitBasis)" x-small color="error"
            title="사유·관련근거가 없습니다">mdi-alert-circle</v-icon>
        </template>
        <template #item.privColCnt="{ item }">
          <span>{{ item.privColCnt }}/{{ item.colCnt }}</span>
          <v-icon v-if="item.unencPrivColCnt > 0" x-small color="warning" :title="'암호화 안 된 개인정보 ' + item.unencPrivColCnt + '개'">mdi-alert</v-icon>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 테이블 일괄 적용 -->
    <v-dialog v-model="bulkDialog" max-width="560">
      <v-card>
        <v-card-title>선택 {{ selected.length }}개 테이블에 적용</v-card-title>
        <v-card-text>
          <div class="gm-note mb-2">체크한 항목만 바꿉니다. 비워 두고 체크하면 그 항목을 지웁니다.</div>
          <div v-for="f in bulkFields" :key="f.key" class="d-flex align-center" style="gap:8px;">
            <v-checkbox v-model="bulkOn[f.key]" dense hide-details class="mt-0" />
            <v-combobox v-if="f.items" v-model="bulk[f.key]" :items="f.items" :label="f.label" dense outlined hide-details class="my-1" />
            <v-text-field v-else v-model="bulk[f.key]" :label="f.label" dense outlined hide-details class="my-1" />
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="bulkDialog = false">취소</v-btn>
          <v-btn id="govItemsBulkApply" color="primary" v-on:click="applyBulk">적용</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 컬럼 항목 -->
    <v-dialog v-model="colDialog" max-width="1280">
      <v-card>
        <v-card-title>컬럼 항목 — {{ colTbl.objOwner ? colTbl.objOwner + '.' : '' }}{{ colTbl.objNm }}
          <span class="gm-note ml-2">{{ colTbl.objNmKr }}</span></v-card-title>
        <v-card-text>
          <v-row v-if="canManage" no-gutters align="center" style="gap:6px;" class="mb-2">
            <span class="gm-note">선택 {{ colSelected.length }}건</span>
            <v-select v-model="colForm.openTp" :items="['공개', '비공개']" label="공개/비공개여부" dense outlined hide-details clearable style="max-width:150px;" />
            <v-text-field v-model="colForm.openLimitReason" label="비공개 사유" dense outlined hide-details style="max-width:220px;" />
            <v-text-field v-model="colForm.cnstrDesc" label="제약조건" dense outlined hide-details style="max-width:220px;" />
            <v-btn id="govColApply" x-small color="primary" :disabled="!colSelected.length" v-on:click="applyCol">적용</v-btn>
            <v-divider vertical class="mx-1" />
            <v-btn x-small outlined color="error" :disabled="!colSelected.length" v-on:click="setPriv('PRIV', 'Y')">개인정보 Y</v-btn>
            <v-btn x-small outlined :disabled="!colSelected.length" v-on:click="setPriv('PRIV', 'N')">개인정보 N</v-btn>
            <v-btn x-small outlined color="success" :disabled="!colSelected.length" v-on:click="setPriv('ENCR', 'Y')">암호화 Y</v-btn>
            <v-btn x-small outlined :disabled="!colSelected.length" v-on:click="setPriv('ENCR', 'N')">암호화 N</v-btn>
          </v-row>
          <v-data-table id="govColTable" v-model="colSelected" :headers="colHeaders" :items="cols" item-key="attrNm" :show-select="canManage"
            dense fixed-header :height="460" :items-per-page="-1" hide-default-footer :loading="colLoading">
            <template #item.attrNm="{ item }">
              <span>{{ item.attrNm }}</span>
              <div v-if="item.attrNmKr" class="gm-sub2">{{ item.attrNmKr }}</div>
            </template>
            <template #item.dataType="{ item }">
              <span>{{ item.dbmsDataType || item.dataType }}</span>
              <span v-if="item.dataLen" class="gm-sub2">({{ item.dataLen }}{{ item.dataDecimalLen ? ',' + item.dataDecimalLen : '' }})</span>
            </template>
            <template #item.notNull="{ item }">{{ item.nullableYn === 'N' ? 'Y' : 'N' }}</template>
            <template #item.pk="{ item }">{{ item.pkPos ? 'PK' + item.pkPos : (item.pkYn && item.pkYn !== 'N' ? 'PK' : '') }}</template>
            <template #item.privYn="{ item }">
              <v-chip x-small :color="item.privYn === 'Y' ? 'error' : 'grey lighten-2'" :dark="item.privYn === 'Y'">{{ item.privYn }}</v-chip>
              <span class="gm-sub2">{{ srcNm(item.privSrc) }}</span>
            </template>
            <template #item.encrYn="{ item }">
              <v-chip x-small :color="item.encrYn === 'Y' ? 'success' : 'grey lighten-2'" :dark="item.encrYn === 'Y'">{{ item.encrYn }}</v-chip>
              <span class="gm-sub2">{{ srcNm(item.encrSrc) }}</span>
            </template>
            <template #item.cnstrDesc="{ item }">
              <span>{{ item.cnstrDesc || '' }}</span>
              <div v-if="!item.cnstrDesc && (item.chkCond || item.defaultVal)" class="gm-sub2" title="수집값 — 제약조건 초안">
                {{ [item.chkCond, item.defaultVal ? '기본값 ' + item.defaultVal : null].filter(Boolean).join(' / ') }}</div>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="colDialog = false">닫기</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>
<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
export default {
  name: 'DSGovMetaItems',
  props: ['isMobile'],
  computed: {
    canManage() { return this.$can('GOV_META_ADMIN'); },
    bulkFields() {
      return [
        { key: 'tblTp', label: '테이블유형', items: this.tblTpOptions },
        { key: 'occrCycle', label: '발생주기', items: this.occrCycleOptions },
        { key: 'openTp', label: '공개/비공개여부', items: ['공개', '부분공개', '비공개'] },
        { key: 'openLimitReason', label: '부분공개·비공개 사유' },
        { key: 'openLimitBasis', label: '부분공개·비공개 관련근거' },
        { key: 'openDataLstNm', label: '개방데이터목록' },
      ];
    },
  },
  data: () => ({
    items: [],
    selected: [],
    loading: false,
    filter: { sysCd: null, dmId: null, objOwner: null, objNm: '', govSendYn: '', openTp: '', tblTp: '', privYn: '' },
    unencOnly: false,
    sysOptions: [],
    modelOptions: [],
    ownerOptions: [],
    ynAll: [{ text: '전체', value: '' }, { text: 'Y', value: 'Y' }, { text: 'N', value: 'N' }],
    openTpFilter: [{ text: '전체', value: '' }, '공개', '부분공개', '비공개', { text: '(비어 있음)', value: '(없음)' }],
    privFilter: [{ text: '전체', value: '' }, { text: '개인정보 있음', value: 'Y' }, { text: '개인정보 없음', value: 'N' }],
    tblTpOptions: ['일반', '통계', '이력', '코드'],
    occrCycleOptions: ['수시', '일', '주', '월', '분기', '반기', '년'],
    headers: [
      { text: '시스템', value: 'sysNm', width: '110px' },
      { text: '데이터 모델', value: 'dmNm', width: '150px' },
      { text: '테이블', value: 'objNm' },
      { text: '전송대상', value: 'govSendYn', width: '80px' },
      { text: '테이블유형', value: 'tblTp', width: '90px' },
      { text: '발생주기', value: 'occrCycle', width: '80px' },
      { text: '공개/비공개여부', value: 'openTp', width: '120px' },
      { text: '부분공개·비공개 사유', value: 'openLimitReason' },
      { text: '관련근거', value: 'openLimitBasis' },
      { text: '개방데이터목록', value: 'openDataLstNm' },
      { text: '개인정보 컬럼', value: 'privColCnt', width: '100px' },
    ],
    bulkDialog: false,
    bulk: {},
    bulkOn: {},
    colDialog: false,
    colTbl: {},
    cols: [],
    colSelected: [],
    colLoading: false,
    colForm: { openTp: null, openLimitReason: '', cnstrDesc: '' },
    colHeaders: [
      { text: '컬럼', value: 'attrNm' },
      { text: '데이터타입', value: 'dataType', width: '130px' },
      { text: 'Not Null', value: 'notNull', width: '70px' },
      { text: 'PK정보', value: 'pk', width: '70px' },
      { text: 'FK정보', value: 'fkRef' },
      { text: '개인정보여부', value: 'privYn', width: '110px' },
      { text: '암호화여부', value: 'encrYn', width: '110px' },
      { text: '공개/비공개여부', value: 'openTp', width: '110px' },
      { text: '비공개 사유', value: 'openLimitReason' },
      { text: '제약조건', value: 'cnstrDesc' },
    ],
    upKind: null,
  }),
  mounted() {
    axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList').then(res => {
      this.sysOptions = (res.data || []).map(s => ({ text: '  '.repeat(Math.max(0, (s.level || 1) - 1)) + s.sysNm, value: s.sysCd }));
    });
    axios.get(this.$APIURL.base + 'api/privCol/models').then(res => {
      this.modelOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
    });
  },
  activated() {
    // 생성·전송 화면의 검증 오류에서 넘어온 대상
    if (eventBus.pendingGovMetaTarget) {
      const t = eventBus.pendingGovMetaTarget;
      eventBus.pendingGovMetaTarget = null;
      this.filter = { sysCd: null, dmId: t.dmId || null, objOwner: null, objNm: t.objNm || '', govSendYn: '', openTp: '', tblTp: '', privYn: '' };
      this.loadOwners(true);
      this.load();
    }
  },
  methods: {
    srcNm(src) {
      if (src === 'COLUMN') return '컬럼';
      if (src === 'TERMS') return '용어';
      if (src === 'DOMAIN') return '도메인';
      return '기본값';
    },
    loadOwners(keep) {
      if (keep !== true) this.filter.objOwner = null;
      this.ownerOptions = [];
      if (!this.filter.dmId) return;
      axios.get(this.$APIURL.base + 'api/privCol/owners', { params: { dmId: this.filter.dmId } }).then(res => {
        this.ownerOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
      });
    },
    params() {
      const p = Object.assign({}, this.filter, { unencOnly: this.unencOnly ? 'Y' : null });
      Object.keys(p).forEach(k => { if (p[k] === '' || p[k] === null) delete p[k]; });
      return p;
    },
    load() {
      this.loading = true;
      this.selected = [];
      axios.post(this.$APIURL.base + 'api/govMeta/tbl/list', this.params()).then(res => {
        const list = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
        this.items = list.map(r => Object.assign(r, { rowKey: [r.dmId, r.objOwner, r.objNm].join('|') }));
      }).finally(() => { this.loading = false; });
    },
    result(res, msg) {
      if (!res.data || res.data.resultCode !== 200) {
        this.$swal.fire({ title: (res.data && res.data.resultMessage) || '실패했습니다', icon: 'error' });
        return false;
      }
      this.$swal.fire({ title: msg || `${res.data.contents}건 적용했습니다`, icon: 'success', timer: 1000, showConfirmButton: false });
      return true;
    },
    keysOf(rows) { return rows.map(r => ({ dmId: r.dmId, objOwner: r.objOwner, objNm: r.objNm })); },
    applyTbl(fields) {
      axios.post(this.$APIURL.base + 'api/govMeta/tbl/apply', Object.assign({ keys: this.keysOf(this.selected) }, fields)).then(res => {
        if (this.result(res)) this.load();
      });
    },
    openBulk() {
      this.bulk = {};
      this.bulkOn = {};
      this.bulkDialog = true;
    },
    applyBulk() {
      const fields = {};
      Object.keys(this.bulkOn).forEach(k => { if (this.bulkOn[k]) fields[k] = this.bulk[k] || ''; });
      if (!Object.keys(fields).length) {
        this.$swal.fire({ title: '적용할 항목을 체크해 주세요', icon: 'warning' });
        return;
      }
      this.bulkDialog = false;
      this.applyTbl(fields);
    },
    openCols(row) {
      this.colTbl = row;
      this.colSelected = [];
      this.colForm = { openTp: null, openLimitReason: '', cnstrDesc: '' };
      this.colDialog = true;
      this.loadCols();
    },
    loadCols() {
      this.colLoading = true;
      axios.post(this.$APIURL.base + 'api/govMeta/col/list', { dmId: this.colTbl.dmId, objOwner: this.colTbl.objOwner, objNm: this.colTbl.objNm })
        .then(res => { this.cols = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : []; })
        .finally(() => { this.colLoading = false; });
    },
    colKeys() { return this.colSelected.map(c => ({ dmId: c.dmId, objOwner: c.objOwner, objNm: c.objNm, attrNm: c.attrNm })); },
    applyCol() {
      const body = { keys: this.colKeys() };
      if (this.colForm.openTp) body.openTp = this.colForm.openTp;
      if (this.colForm.openLimitReason) body.openLimitReason = this.colForm.openLimitReason;
      if (this.colForm.cnstrDesc) body.cnstrDesc = this.colForm.cnstrDesc;
      axios.post(this.$APIURL.base + 'api/govMeta/col/apply', body).then(res => {
        if (this.result(res)) { this.colSelected = []; this.loadCols(); }
      });
    },
    setPriv(field, val) {
      axios.post(this.$APIURL.base + 'api/privCol/set', { field, val, keys: this.colKeys() }).then(res => {
        if (this.result(res)) { this.colSelected = []; this.loadCols(); this.load(); }
      });
    },
    download(kind) {
      const p = this.params();
      axios.get(this.$APIURL.base + `api/govMeta/${kind}/download`, { params: p, responseType: 'blob' }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `범정부메타_${kind === 'tbl' ? '테이블' : '컬럼'}_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        link.remove();
      });
    },
    pickUpload(kind) {
      this.upKind = kind;
      this.$refs.upFile.click();
    },
    upload(e) {
      const file = e.target.files[0];
      e.target.value = '';
      if (!file) return;
      const fd = new FormData();
      fd.append('file', file);
      axios.post(this.$APIURL.base + `api/govMeta/${this.upKind}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.$swal.fire({ title: (res.data && res.data.resultMessage) || '올리기 실패', icon: 'error' });
          return;
        }
        const r = JSON.parse(res.data.contents);
        const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
        this.$swal.fire({
          title: `${r.updated}건 바꿨습니다`,
          html: r.errorCnt ? `<div style="text-align:left;font-size:.8rem;">건너뛴 행 ${r.errorCnt}건<br>${r.errors.map(esc).join('<br>')}</div>` : '',
          icon: r.errorCnt ? 'warning' : 'success',
        });
        this.load();
      });
    },
  },
};
</script>
<style scoped>
.gm-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.gm-note { font-size: .78rem; color: #78909C; }
.gm-sub2 { font-size: .72rem; color: #78909C; }
</style>
