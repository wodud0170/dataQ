<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">

      <!-- 상단: 모델 + 샘플링 + 시작 (오른쪽 끝 padding 추가로 버튼 잘림 방지) -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px; padding-right:32px !important;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델 선택" dense hide-details style="max-width:280px" @change="loadCols" id="cmb-model"></v-autocomplete>
        <v-select v-model="sampleRate" :items="sampleOpts" item-text="text" item-value="value"
          label="샘플링" dense hide-details style="max-width:130px" id="cmb-sample"></v-select>
        <v-spacer></v-spacer>
        <span style="font-size:.8rem; color:#546E7A; white-space:nowrap;">선택 {{ selected.length }}건 / 표시 {{ filtered.length }}건</span>
        <v-btn small class="ml-2 gradient" @click="runSelected"
          :disabled="!dmId || selected.length===0 || running" id="btn-run-selected"
          style="flex-shrink:0; min-width:140px;">
          <v-icon small left>mdi-play</v-icon>선택 컬럼 진단
        </v-btn>
      </v-sheet>

      <!-- 검색 필터: 테이블 + 컬럼 + 도메인 분류 multi -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #ECEFF1; gap:8px; background:#FAFBFC; flex-wrap:wrap;">
        <v-text-field v-model="filterObj"  label="테이블 필터" dense hide-details clearable style="max-width:200px" id="txt-obj"></v-text-field>
        <v-text-field v-model="filterAttr" label="컬럼 필터"  dense hide-details clearable style="max-width:200px" id="txt-attr"></v-text-field>
        <v-autocomplete v-model="selectedClsfs" :items="clsfOptions" label="도메인 분류 (다중)"
          dense hide-details multiple chips small-chips clearable
          style="min-width:280px; max-width:480px;" id="cmb-clsf"></v-autocomplete>
        <v-spacer></v-spacer>
        <v-btn small outlined color="indigo" @click="selectAll" id="btn-select-all">전체 선택</v-btn>
        <v-btn small outlined color="grey darken-1" @click="selectNone" id="btn-select-none">선택 해제</v-btn>
      </v-sheet>

      <!-- 진행률 표시 -->
      <v-sheet v-if="running || progress.total > 0" class="pa-2" style="background:#F1F8E9; border-bottom:1px solid #DCEDC8;">
        <div class="d-flex align-center" style="gap:12px;">
          <v-icon small color="green">mdi-progress-clock</v-icon>
          <span style="font-size:.85rem; min-width:140px;">
            {{ running ? '진단 진행 중' : '진단 완료' }} ({{ progress.done }}/{{ progress.total }})
          </span>
          <v-progress-linear :value="progress.pct" height="14" color="green" rounded striped
            style="flex:1;" id="bar-progress">
            <template v-slot:default>
              <span style="font-size:.7rem; color:white; font-weight:600;">{{ progress.pct }}%</span>
            </template>
          </v-progress-linear>
          <span style="font-size:.7rem; color:#546E7A;" v-if="progress.statusV || progress.statusR">
            값:{{ progress.statusV || '-' }} 룰:{{ progress.statusR || '-' }}
          </span>
        </div>
      </v-sheet>

      <!-- 컬럼 그리드 -->
      <v-data-table
        v-model="selected"
        :headers="headers"
        :items="filtered"
        item-key="rowKey"
        show-select
        dense hide-default-footer
        :items-per-page="500"
        class="elevation-0" :loading="loading"
        id="grid-cols">
        <template v-slot:item.domainClsfNm="{ item }">
          <v-chip v-if="item.domainClsfNm" x-small color="indigo lighten-4">{{ item.domainClsfNm }}</v-chip>
          <span v-else style="color:#BDBDBD; font-size:.75rem">-</span>
        </template>
        <template v-slot:item.effectiveSource="{ item }">
          <v-chip x-small :color="srcColor(item.effectiveSource)" text-color="white">
            {{ item.effectiveSource || '-' }}
          </v-chip>
        </template>
        <template v-slot:item.ruleConformRate="{ item }">
          <span v-if="item.ruleConformRate != null"
            :style="{color: rateColor(item.ruleConformRate), 'font-weight': 600}">
            {{ Number(item.ruleConformRate).toFixed(1) }}%
          </span>
          <span v-else style="color:#9E9E9E; font-size:.8rem">-</span>
        </template>
        <template v-slot:item.profNullPct="{ item }">
          <span v-if="item.profTotal">
            {{ ((item.profNull / item.profTotal) * 100).toFixed(1) }}%
          </span>
          <span v-else style="color:#9E9E9E; font-size:.8rem">-</span>
        </template>
        <template v-slot:item.actions="{ item }">
          <v-btn icon small @click="openDetail(item)" title="상세" id="btn-row-detail">
            <v-icon small>mdi-information-outline</v-icon>
          </v-btn>
        </template>
      </v-data-table>

    </v-sheet>

    <!-- 상세 drawer -->
    <v-navigation-drawer v-model="drawer" right temporary fixed width="500" style="z-index:99">
      <v-sheet v-if="detail" class="pa-4">
        <h3>{{ detail.objNm }}.{{ detail.attrNm }}
          <span style="font-size:.7rem; color:#90A4AE; margin-left:6px;">({{ detail.attrNmKr || '-' }})</span>
        </h3>
        <v-divider class="my-3"></v-divider>

        <p><b>도메인</b></p>
        <p style="font-size:.85rem">
          {{ detail.domainNm || '-' }}
          <v-chip v-if="detail.domainClsfNm" x-small class="ml-2" color="indigo lighten-4">{{ detail.domainClsfNm }}</v-chip>
        </p>

        <p><b>적용 규칙</b></p>
        <p style="font-size:.85rem">{{ detail.effectiveRuleNm || '없음' }}
          (<v-chip x-small :color="srcColor(detail.effectiveSource)" text-color="white">{{ detail.effectiveSource }}</v-chip>
          {{ detail.effectiveRuleType || '' }})
        </p>

        <v-divider class="my-3"></v-divider>
        <p><b>값 프로파일 (직전)</b></p>
        <table class="detail-tbl" v-if="detail.profTotal">
          <tr><td>총 행수</td>     <td>{{ detail.profTotal }}</td></tr>
          <tr><td>NULL</td>       <td>{{ detail.profNull }} ({{ pct(detail.profNull, detail.profTotal) }})</td></tr>
          <tr><td>유일값</td>     <td>{{ detail.profDistinct }}</td></tr>
          <tr><td>최소값</td>     <td>{{ detail.profMin || '-' }}</td></tr>
          <tr><td>최대값</td>     <td>{{ detail.profMax || '-' }}</td></tr>
          <tr><td>최소 길이</td>  <td>{{ detail.profMinLen || '-' }}</td></tr>
          <tr><td>최대 길이</td>  <td>{{ detail.profMaxLen || '-' }}</td></tr>
        </table>
        <p v-else style="font-size:.8rem; color:#9E9E9E">아직 프로파일링 결과 없음</p>

        <v-divider class="my-3"></v-divider>
        <p><b>룰 진단 (직전)</b></p>
        <table class="detail-tbl" v-if="detail.ruleTotal != null">
          <tr><td>위반</td>       <td>{{ detail.ruleViolation }} / {{ detail.ruleTotal }}</td></tr>
          <tr><td>위반률</td>     <td>{{ Number(detail.ruleViolationRate || 0).toFixed(2) }}%</td></tr>
          <tr><td>적합률</td>     <td :style="{color: rateColor(detail.ruleConformRate)}"><b>{{ Number(detail.ruleConformRate || 0).toFixed(2) }}%</b></td></tr>
        </table>
        <p v-else style="font-size:.8rem; color:#9E9E9E">아직 룰 진단 결과 없음</p>

        <v-divider class="my-3"></v-divider>
        <p style="font-size:.75rem; color:#90A4AE">시계열 추이는 [진단 통계] 메뉴에서 확인</p>
      </v-sheet>
    </v-navigation-drawer>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualValueProfile',
  data() {
    return {
      dataModels: [],
      dmId: null,
      sampleRate: 100,
      sampleOpts: [
        { text: '1만건', value: 1 },
        { text: '10%',  value: 10 },
        { text: '100%', value: 100 }
      ],
      rows: [],
      selected: [],
      filterObj: '',
      filterAttr: '',
      selectedClsfs: [],
      loading: false,
      drawer: false,
      detail: null,
      // 진행률 폴링
      running: false,
      pollTimer: null,
      diagIdValue: null,
      diagIdRule:  null,
      progress: { done: 0, total: 0, pct: 0, statusV: null, statusR: null },
      headers: [
        { text: '테이블',     value: 'objNm', width: 140 },
        { text: '컬럼',       value: 'attrNm', width: 140 },
        { text: '한글명',     value: 'attrNmKr', width: 140 },
        { text: '도메인 분류', value: 'domainClsfNm', width: 120 },
        { text: '적용 규칙',  value: 'effectiveRuleNm' },
        { text: '소스',       value: 'effectiveSource', width: 90 },
        { text: 'NULL%',      value: 'profNullPct', width: 80 },
        { text: '적합률',     value: 'ruleConformRate', width: 90 },
        { text: '',           value: 'actions', sortable: false, width: 50 }
      ]
    };
  },
  computed: {
    clsfOptions() {
      var set = {};
      this.rows.forEach(function(r) { if (r.domainClsfNm) set[r.domainClsfNm] = true; });
      return Object.keys(set).sort();
    },
    filtered() {
      var fo = (this.filterObj || '').toLowerCase();
      var fa = (this.filterAttr || '').toLowerCase();
      var cls = this.selectedClsfs;
      return this.rows.filter(function(r) {
        if (fo && (r.objNm || '').toLowerCase().indexOf(fo) === -1) return false;
        if (fa && (r.attrNm || '').toLowerCase().indexOf(fa) === -1) return false;
        if (cls && cls.length > 0) {
          if (!r.domainClsfNm || cls.indexOf(r.domainClsfNm) === -1) return false;
        }
        return true;
      });
    }
  },
  mounted() {
    var self = this;
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { connectedOnly: 'Y' })
      .then(function(r) {
        self.dataModels = (r.data || []).filter(function(m) { return m.modelType === 'PHYSICAL'; });
      });
  },
  beforeDestroy() {
    if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; }
  },
  methods: {
    /** 86번 #11 — 백엔드 raw exception 차단, 친화적 메세지 변환 */
    _friendlyErrText: function(err, fallback) {
      var status = (err && err.response && err.response.status) || 0;
      var data   = (err && err.response && err.response.data) || {};
      var our    = data.resultMessage;
      var raw    = data.message;
      var rawIsTechnical = raw && /JSON|deserialize|parse|MismatchedInput|HttpMessageNotReadable|Exception|NullPointer|invalid|cannot/i.test(raw);
      if (our) return our;
      if (raw && !rawIsTechnical) return raw;
      if (status >= 500) return (fallback || '서버 처리 중 오류가 발생했습니다.') + ' (관리자에게 문의해 주세요)';
      if (status === 400) return (fallback || '입력값이 올바르지 않습니다.');
      if (status === 401 || status === 403) return '권한이 없습니다.';
      if (status === 404) return '요청한 자원을 찾을 수 없습니다.';
      return fallback || (err && err.message) || '알 수 없는 오류가 발생했습니다.';
    },
    loadCols() {
      if (!this.dmId) { this.rows = []; this.selected = []; return; }
      this.loading = true;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/colrule/listWithLatest',
                { params: { dmId: this.dmId } })
        .then(function(r) {
          self.rows = (r.data || []).map(function(x) {
            x.rowKey = x.objNm + '.' + x.attrNm;
            return x;
          }).filter(function(x) {
            return x.effectiveSource !== 'EXCLUDED';
          });
          self.selected = [];
        })
        .finally(function() { self.loading = false; });
    },
    selectAll()  { this.selected = this.filtered.slice(); },
    selectNone() { this.selected = []; },
    runSelected() {
      var self = this;
      var targets = this.selected.map(function(r) {
        return { objNm: r.objNm, attrNm: r.attrNm };
      });
      var body = { dataModelId: this.dmId, sampleRate: this.sampleRate, targets: targets };

      this.running = true;
      this.progress = { done: 0, total: targets.length * 2, pct: 0, statusV: 'READY', statusR: 'READY' };

      // 값 진단 + 룰 진단 동시 실행
      Promise.all([
        axios.post(this.$APIURL.base + 'api/qual/value/runColumns', body),
        axios.post(this.$APIURL.base + 'api/qual/rule/runColumns',  body)
      ]).then(function(arr) {
        self.diagIdValue = arr[0].data.contents;
        self.diagIdRule  = arr[1].data.contents;
        self.$swal.fire({
          icon: 'success',
          title: targets.length + '개 컬럼 진단 시작',
          html: '진행률은 화면 상단에서 확인',
          timer: 1500, showConfirmButton: false
        });
        self.startPoll();
      }).catch(function(err) {
        self.running = false;
        self.$swal.fire({ icon: 'error', title: '진단 시작 실패', text: self._friendlyErrText(err, '진단 시작 중 오류가 발생했습니다.') });
      });
    },
    startPoll() {
      var self = this;
      if (this.pollTimer) clearInterval(this.pollTimer);
      this.pollTimer = setInterval(function() { self.poll(); }, 3000);
      this.poll();
    },
    poll() {
      var self = this;
      var promises = [];
      if (this.diagIdValue) promises.push(axios.get(this.$APIURL.base + 'api/qual/value/history/' + this.diagIdValue));
      if (this.diagIdRule)  promises.push(axios.get(this.$APIURL.base + 'api/qual/rule/history/'  + this.diagIdRule));
      if (promises.length === 0) return;
      Promise.all(promises).then(function(arr) {
        var hv = arr[0] ? arr[0].data : null;
        var hr = arr[1] ? arr[1].data : null;
        var doneV  = hv ? (hv.progressDone  || 0) : 0;
        var totalV = hv ? (hv.progressTotal || 0) : 0;
        var doneR  = hr ? (hr.progressDone  || 0) : 0;
        var totalR = hr ? (hr.progressTotal || 0) : 0;
        var done  = doneV + doneR;
        var total = Math.max(totalV + totalR, 1);
        self.progress.done    = done;
        self.progress.total   = total;
        self.progress.pct     = Math.min(100, Math.round(done / total * 100));
        self.progress.statusV = hv ? hv.status : null;
        self.progress.statusR = hr ? hr.status : null;
        var vDone = !hv || hv.status === 'DONE' || hv.status === 'ERROR' || hv.status === 'SKIPPED';
        var rDone = !hr || hr.status === 'DONE' || hr.status === 'ERROR' || hr.status === 'SKIPPED';
        if (vDone && rDone) {
          self.running = false;
          if (self.pollTimer) { clearInterval(self.pollTimer); self.pollTimer = null; }
          self.loadCols();
        }
      }).catch(function() { /* 일시 오류 무시, 다음 폴링 */ });
    },
    openDetail(item) {
      this.detail = item;
      this.drawer = true;
    },
    pct(n, t) {
      if (!t || t === 0) return '0.0%';
      return ((n / t) * 100).toFixed(1) + '%';
    },
    srcColor(s) {
      switch (s) {
        case 'DOMAIN':  return 'blue';
        case 'CUSTOM':  return 'purple';
        case 'DEFAULT': return 'green';
        case 'EXCLUDED':return 'grey';
        default:        return 'orange';
      }
    },
    rateColor(r) {
      if (r == null) return 'inherit';
      if (r >= 95)  return '#2E7D32';
      if (r >= 80)  return '#F57F17';
      return '#C62828';
    }
  }
};
</script>

<style scoped>
.detail-tbl { width: 100%; font-size: .85rem; }
.detail-tbl td { padding: 4px 8px; border-bottom: 1px solid #ECEFF1; }
.detail-tbl td:first-child { color: #607D8B; width: 35%; }
</style>
