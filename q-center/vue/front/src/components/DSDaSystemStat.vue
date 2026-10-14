<!--
  104번 WP9-62·66 — 시스템별 현황 (데이터 모델 > 시스템별 현황)

  시스템 트리마다 그 시스템과 하위 시스템에 속한 데이터 모델을 묶어 표준 준수율·구조 일치율·구조 차이를 보이고,
  DA 목표(시스템·모델)와 달성 여부를 함께 보인다. 시스템을 고르면 준수율 추이(준수 현황 스냅샷)와 모델별 표.
  목표 설정은 시스템 관리 권한(SYS_ADMIN).
-->
<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-2" style="gap:8px;">
        <h2><v-icon>mdi-sitemap-outline</v-icon>&nbsp;&nbsp;시스템별 현황</h2>
        <v-spacer />
        <v-btn small outlined color="primary" class="btn-auto" v-on:click="loadAll">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
        <v-btn id="daExcel" small outlined color="deep-purple" class="btn-auto" :disabled="!systems.length" v-on:click="download">
          <v-icon small left>mdi-file-excel</v-icon>엑셀
        </v-btn>
      </v-row>

      <div class="da-guide">
        시스템마다 그 시스템과 하위 시스템에 속한 데이터 모델을 묶어 봅니다.
        표준 준수율 = (전체 컬럼 − 이슈 컬럼) ÷ 전체 컬럼 (모델마다 최신 표준 진단을 합쳐서), 구조 일치율 = 1 − 차이 ÷ 전체 (모델마다 최신 구조 진단을 합쳐서).
        목표는 모델 → 속한 시스템 → 상위 시스템 순으로 가까운 것을 씁니다.
      </div>

      <v-data-table id="daSysTable" :headers="sysHeadersView" :items="systems" item-key="sysCd" dense hide-default-footer
        :items-per-page="-1" disable-sort :loading="loading" loading-text="집계하는 중입니다."
        :item-class="r => (sel && r.sysCd === sel.sysCd) ? 'da-row-sel' : ''" @click:row="pick"
        no-data-text="시스템이 없습니다. [관리 > 시스템] 에서 등록하세요.">
        <template #item.sysNm="{ item }">
          <div class="da-sys" :style="{ paddingLeft: ((item.level - 1) * 18) + 'px' }" :title="item.path">
            <v-icon v-if="item.level > 1" x-small class="da-arrow">mdi-subdirectory-arrow-right</v-icon><span class="da-sys-nm">{{ item.sysNm }}</span>
          </div>
        </template>
        <template #item.modelCnt="{ item }">{{ n(item.modelCnt) }}</template>
        <template #item.objCnt="{ item }">{{ n(item.objCnt) }}</template>
        <template #item.attrCnt="{ item }">{{ n(item.attrCnt) }}</template>
        <template #item.stndRate="{ item }">
          <div class="da-rate">
            <div class="da-rate-row">
              <span v-if="!item.modelCnt" class="da-na">모델 없음</span>
              <span v-else class="da-rate-v">{{ pct(item.stndRate) }}</span>
              <span v-if="item.stndGoal != null" class="da-goal">목표 {{ item.stndGoal }}%
                <span :class="'da-achv-' + item.stndAchv">{{ achvNm(item.stndAchv) }}</span></span>
            </div>
            <v-progress-linear :value="item.stndRate || 0" height="5" rounded :color="barColor(item.stndRate, item.stndAchv)" background-color="#ECEFF1" />
          </div>
        </template>
        <template #item.structRate="{ item }">
          <div class="da-rate">
            <div class="da-rate-row">
              <span v-if="!item.modelCnt" class="da-na">모델 없음</span>
              <span v-else-if="item.structRate == null && !item.structTargetCnt" class="da-na"
                title="데이터소스가 연결된 모델이 없어 구조 진단 대상이 아닙니다">대상 아님</span>
              <span v-else class="da-rate-v">{{ pct(item.structRate) }}</span>
              <span v-if="item.structGoal != null" class="da-goal">목표 {{ item.structGoal }}%
                <span :class="'da-achv-' + item.structAchv">{{ achvNm(item.structAchv) }}</span></span>
            </div>
            <v-progress-linear :value="item.structRate || 0" height="5" rounded :color="barColor(item.structRate, item.structAchv)" background-color="#ECEFF1" />
          </div>
        </template>
        <template #item.diff="{ item }">
          <span class="da-small">추가 {{ n(item.addedColumns) }} · 변경 {{ n(item.modifiedColumns) }} · 삭제 {{ n(item.deletedColumns) }}</span>
        </template>
        <template #item.undiag="{ item }">
          <span class="da-small" :class="{ 'da-warn': item.undiagStndCnt + item.undiagStructCnt > 0 }">표준 {{ item.undiagStndCnt }} · 구조 {{ item.undiagStructCnt }}</span>
        </template>
        <template #item.actions="{ item }">
          <v-btn v-if="canTarget && item.sysCd" icon small :id="'daTarget_' + item.sysCd" v-on:click.stop="openTarget('SYS', item)"
            :title="item.ownTarget === 'Y' ? '목표 수정' : '목표 설정'">
            <v-icon small :color="item.ownTarget === 'Y' ? 'indigo' : 'grey'">mdi-bullseye-arrow</v-icon>
          </v-btn>
        </template>
      </v-data-table>

      <!-- 선택한 시스템 — 추이 + 모델별 -->
      <div v-if="sel" class="mt-5">
        <v-row no-gutters align="center" class="mb-2">
          <span id="daSelTitle" class="text-subtitle-1 font-weight-medium">{{ sel.path }}</span>
          <span class="text-caption grey--text ml-2">모델 {{ models.length }}개{{ sel.sysCd ? ' · 하위 시스템 포함' : '' }}</span>
        </v-row>
        <!-- 모델별 표는 전체 폭 (열이 많다), 추이는 그 아래 -->
        <v-row>
          <v-col cols="12">
            <v-data-table id="daModelTable" :headers="modelHeadersView" :items="models" item-key="dmId" dense :items-per-page="10"
              :footer-props="{ 'items-per-page-options': [10, 20, 50, -1] }" no-data-text="이 시스템에 속한 데이터 모델이 없습니다.">
              <template #item.dmNm="{ item }">
                <a class="ndColor--text da-link" v-on:click="goModel(item)">{{ item.dmNm }}</a>
              </template>
              <template #item.sysNm="{ item }"><span class="da-ell" :title="item.sysNm">{{ item.sysNm || '-' }}</span></template>
              <template #item.objCnt="{ item }">{{ n(item.objCnt) }}</template>
              <template #item.attrCnt="{ item }">{{ n(item.attrCnt) }}</template>
              <template #item.stndRate="{ item }">
                <span class="da-rate-v">{{ pct(item.stndRate) }}</span>
                <span v-if="item.stndGoal != null" class="da-goal ml-1">/ {{ item.stndGoal }}%
                  <span :class="'da-achv-' + item.stndAchv">{{ achvNm(item.stndAchv) }}</span></span>
              </template>
              <template #item.structRate="{ item }">
                <span v-if="item.structRate == null && item.structTargetYn !== 'Y'" class="da-na" title="데이터소스가 연결되지 않은 모델은 구조 진단 대상이 아닙니다">대상 아님</span>
                <span v-else class="da-rate-v">{{ pct(item.structRate) }}</span>
                <span v-if="item.structGoal != null && !(item.structRate == null && item.structTargetYn !== 'Y')" class="da-goal ml-1">/ {{ item.structGoal }}%
                  <span :class="'da-achv-' + item.structAchv">{{ achvNm(item.structAchv) }}</span></span>
              </template>
              <template #item.goalSrc="{ item }">
                <span class="da-small">{{ item.goalSrc || '-' }}</span>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="canTarget" icon small :id="'daTargetDm_' + item.dmId" v-on:click.stop="openTarget('DM', item)"
                  :title="item.ownTarget === 'Y' ? '모델 목표 수정' : '모델 목표 설정'">
                  <v-icon small :color="item.ownTarget === 'Y' ? 'indigo' : 'grey'">mdi-bullseye-arrow</v-icon>
                </v-btn>
              </template>
            </v-data-table>
          </v-col>
          <v-col cols="12" lg="6">
            <v-card outlined class="pa-3">
              <div class="da-sub">준수율 추이 (준수 현황 스냅샷)</div>
              <apexchart v-if="trend.length" type="line" height="230" :options="trendOptions" :series="trendSeries" :key="trendKey" />
              <div v-else id="daTrendEmpty" class="da-empty">스냅샷이 없습니다. [스케줄 관리] 에서 진단 유형 "준수 현황 스냅샷" 을 예약하면 실행할 때마다 쌓입니다.</div>
            </v-card>
          </v-col>
        </v-row>
      </div>
      <div v-else-if="systems.length" class="da-empty mt-4">위 표에서 시스템을 고르면 준수율 추이와 모델별 현황을 봅니다.</div>
    </v-sheet>

    <!-- DA 목표 -->
    <v-dialog v-model="targetDialog" max-width="480" persistent>
      <v-card>
        <v-card-title class="text-subtitle-1">DA 목표 — {{ tForm.nm }}</v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <div class="da-hint mb-4">
            {{ tForm.targetTp === 'SYS'
              ? '이 시스템과, 목표를 따로 정하지 않은 하위 시스템·모델에 적용됩니다.'
              : '이 모델에만 적용됩니다 (시스템 목표보다 먼저).' }}
            <span v-if="tForm.inherited">지금은 {{ tForm.inherited }} 의 목표를 따르고 있습니다.</span>
            비워 둔 항목은 목표 없음이 됩니다 (상위 목표를 따르지 않습니다).
          </div>
          <v-text-field id="daStndGoal" v-model="tForm.stndRateGoal" label="표준 준수율 목표 (%)" type="number" min="0" max="100"
            dense outlined hide-details class="mb-4" />
          <v-text-field id="daStructGoal" v-model="tForm.structRateGoal" label="구조 일치율 목표 (%)" type="number" min="0" max="100"
            dense outlined hide-details class="mb-4" />
          <v-text-field v-model="tForm.targetDesc" label="설명 (선택)" dense outlined hide-details />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-btn v-if="tForm.exists" id="daTargetDelete" text color="error" class="btn-auto" v-on:click="deleteTarget">목표 삭제</v-btn>
          <v-spacer />
          <v-btn text v-on:click="targetDialog = false">취소</v-btn>
          <v-btn id="daTargetSave" color="ndColor" dark class="btn-auto" v-on:click="saveTarget">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
import VueApexCharts from 'vue-apexcharts';
import { eventBus } from '../eventBus';

export default {
  name: 'DSDaSystemStat',
  components: { apexchart: VueApexCharts },
  props: ['isMobile'],
  data: () => ({
    loading: false,
    systems: [],
    sel: null,
    models: [],
    trend: [],
    trendKey: 0,
    sysHeaders: [
      { text: '시스템', value: 'sysNm', align: 'start', width: '200px' },
      { text: '모델', value: 'modelCnt', width: '60px', align: 'end' },
      { text: '테이블', value: 'objCnt', width: '72px', align: 'end' },
      { text: '컬럼', value: 'attrCnt', width: '84px', align: 'end' },
      { text: '표준 준수율', value: 'stndRate', width: '150px' },
      { text: '구조 일치율', value: 'structRate', width: '150px' },
      { text: '구조 차이 (컬럼)', value: 'diff', width: '165px' },
      { text: '진단 안 한 모델', value: 'undiag', width: '110px' },
      { text: '목표', value: 'actions', width: '56px', align: 'center' },
    ],
    modelHeaders: [
      { text: '데이터 모델', value: 'dmNm' },
      { text: '시스템', value: 'sysNm', width: '180px' },
      { text: '테이블', value: 'objCnt', width: '76px', align: 'end' },
      { text: '컬럼', value: 'attrCnt', width: '84px', align: 'end' },
      { text: '표준 준수율 / 목표', value: 'stndRate', width: '190px' },
      { text: '구조 일치율 / 목표', value: 'structRate', width: '190px' },
      { text: '목표 출처', value: 'goalSrc', width: '110px' },
      { text: '목표', value: 'actions', width: '64px', align: 'center', sortable: false },
    ],
    targetDialog: false,
    tForm: {},
  }),
  computed: {
    canTarget() { return this.$can('SYS_ADMIN'); },
    // 목표 설정 권한이 없으면 [목표] 열을 뺀다
    sysHeadersView() { return this.canTarget ? this.sysHeaders : this.sysHeaders.filter(h => h.value !== 'actions'); },
    modelHeadersView() { return this.canTarget ? this.modelHeaders : this.modelHeaders.filter(h => h.value !== 'actions'); },
    trendSeries() {
      return [
        { name: '표준 준수율', data: this.trend.map(t => t.stndRate) },
        { name: '구조 일치율', data: this.trend.map(t => t.structRate) },
      ];
    },
    trendOptions() {
      return {
        // 숨은 탭에서는 창 크기 변화로 다시 그리지 않는다 (크기 0 → NaN 콘솔 에러)
        chart: { type: 'line', toolbar: { show: false }, redrawOnParentResize: false,
          redrawOnWindowResize: () => !this._inactive && !!this.$el && this.$el.offsetParent !== null },
        colors: ['#3F51B5', '#00897B'],
        stroke: { width: 3, curve: 'straight' },
        markers: { size: 4, strokeColors: '#fff', strokeWidth: 2 },
        xaxis: { categories: this.trend.map(t => this.shortDt(t.snapDt)), labels: { style: { fontSize: '10px', colors: '#9E9E9E' } } },
        yaxis: { min: 0, max: 100, labels: { style: { fontSize: '10px' }, formatter: v => v + '%' } },
        legend: { position: 'top', fontSize: '11px' },
        grid: { borderColor: '#F0F0F0', strokeDashArray: 4 },
        tooltip: { y: { formatter: v => (v == null ? '-' : v + '%') } },
      };
    },
  },
  mounted() { this.loadAll(); },
  activated() { this.loadAll(); },
  methods: {
    n(v) { return v == null ? '-' : Number(v).toLocaleString(); },
    pct(v) { return v == null ? '미진단' : v + '%'; },
    achvNm(a) { return a === 'Y' ? '달성' : a === 'N' ? '미달' : ''; },
    barColor(rate, achv) {
      if (rate == null) return '#CFD8DC';
      if (achv === 'Y') return '#43A047';
      if (achv === 'N') return '#E53935';
      return '#5C6BC0';
    },
    shortDt(s) { return s && s.length >= 12 ? s.substr(4, 2) + '-' + s.substr(6, 2) + ' ' + s.substr(8, 2) + ':' + s.substr(10, 2) : s; },
    loadAll() {
      this.loading = true;
      axios.post(this.$APIURL.base + 'api/daStat/systems', {}).then(r => {
        this.systems = Array.isArray(r.data) ? r.data : [];
        if (this.sel) {
          this.sel = this.systems.find(s => s.sysCd === this.sel.sysCd) || null;
          if (this.sel) this.loadDetail();
        }
      }).catch(() => { this.systems = []; }).finally(() => { this.loading = false; });
    },
    pick(item) {
      this.sel = item;
      this.loadDetail();
    },
    loadDetail() {
      const body = { sysCd: this.sel.sysCd };
      axios.post(this.$APIURL.base + 'api/daStat/models', body).then(r => { this.models = r.data || []; }).catch(() => { this.models = []; });
      axios.post(this.$APIURL.base + 'api/daStat/trend', body).then(r => { this.trend = r.data || []; this.trendKey++; }).catch(() => { this.trend = []; });
    },
    goModel(item) {
      eventBus.pendingTableView = { modelId: item.dmId };
      eventBus.$emit('openTableView');
    },
    openTarget(tp, item) {
      const own = item.ownTarget === 'Y';
      this.tForm = {
        targetTp: tp,
        targetId: tp === 'SYS' ? item.sysCd : item.dmId,
        nm: tp === 'SYS' ? item.path : item.dmNm,
        stndRateGoal: own ? item.stndGoal : null,
        structRateGoal: own ? item.structGoal : null,
        targetDesc: '',
        exists: own,
        inherited: !own && item.goalSrc ? item.goalSrc + ' (표준 ' + (item.stndGoal == null ? '-' : item.stndGoal + '%') + ' · 구조 ' + (item.structGoal == null ? '-' : item.structGoal + '%') + ')' : null,
      };
      this.targetDialog = true;
    },
    _msg(res) { return (res && res.data && res.data.resultMessage) || ''; },
    saveTarget() {
      const f = this.tForm;
      axios.post(this.$APIURL.base + 'api/daStat/target/save', {
        targetTp: f.targetTp, targetId: f.targetId, stndRateGoal: f.stndRateGoal, structRateGoal: f.structRateGoal, targetDesc: f.targetDesc,
      }).then(res => {
        if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '저장 실패', text: this._msg(res), icon: 'error', confirmButtonText: '확인' }); return; }
        this.targetDialog = false;
        this.$swal.fire({ title: '목표를 저장했습니다.', icon: 'success', timer: 1000, showConfirmButton: false });
        this.loadAll();
      }).catch(() => this.$swal.fire({ title: '저장 실패', icon: 'error', confirmButtonText: '확인' }));
    },
    deleteTarget() {
      const f = this.tForm;
      axios.post(this.$APIURL.base + 'api/daStat/target/delete', { targetTp: f.targetTp, targetId: f.targetId }).then(res => {
        if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '삭제 실패', text: this._msg(res), icon: 'error', confirmButtonText: '확인' }); return; }
        this.targetDialog = false;
        this.loadAll();
      });
    },
    download() {
      axios.get(this.$APIURL.base + 'api/daStat/download', { responseType: 'blob' }).then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data]));
        const a = document.createElement('a');
        a.href = url;
        a.download = '시스템별현황_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      }).catch(() => this.$swal.fire({ title: '엑셀 다운로드 실패', icon: 'error', confirmButtonText: '확인' }));
    },
  },
};
</script>

<style scoped>
.da-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
/* 긴 시스템 이름은 한 줄로 줄이고 전체 경로는 툴팁 */
.da-sys { display: flex; align-items: center; max-width: 240px; }
.da-sys-nm { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.da-arrow { margin-right: 2px; flex: none; }
.da-rate { min-width: 130px; padding: 4px 0; }
.da-rate-row { display: flex; align-items: baseline; gap: 6px; margin-bottom: 3px; }
.da-rate-v { font-weight: 600; font-size: .85rem; color: #263238; font-variant-numeric: tabular-nums; }
.da-goal { font-size: .72rem; color: #78909C; white-space: nowrap; }
.da-na { font-size: .8rem; color: #90A4AE; }
.da-ell { display: inline-block; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.da-achv-Y { color: #2E7D32; font-weight: 600; }
.da-achv-N { color: #C62828; font-weight: 600; }
.da-small { font-size: .78rem; color: #546E7A; white-space: nowrap; }
.da-warn { color: #EF6C00; }
.da-sub { font-size: .85rem; font-weight: 600; color: #37474F; margin-bottom: 4px; }
.da-empty { font-size: .8rem; color: #90A4AE; padding: 12px 4px; }
.da-hint { font-size: .8rem; color: #546E7A; }
.da-link { cursor: pointer; text-decoration: underline; }
</style>
<style>
/* 표 안쪽 행은 자식 컴포넌트라 scoped 가 닿지 않는다 */
#daSysTable tbody tr { cursor: pointer; }
#daSysTable .da-row-sel > td { background-color: #E8EAF6 !important; }
#daSysTable th, #daModelTable th { white-space: nowrap; }
/* 1366 폭에서도 가로 스크롤 없이 — 열 안쪽 여백을 줄인다 */
#daSysTable th, #daSysTable td { padding: 0 10px !important; }
#daModelTable td { word-break: keep-all; overflow-wrap: break-word; }
</style>
