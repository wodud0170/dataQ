<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <!-- 헤더 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <span style="font-size:1.05rem; font-weight:600; color:#1A237E;">검증 대상</span>
        <span style="font-size:.8rem; color:#9E9E9E;">— 컬럼 ↔ 규칙 매핑 + 유효 규칙 + 직전 진단 적합률</span>
      </v-sheet>

      <!-- 검색 필터 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #ECEFF1; gap:8px; flex-wrap:wrap; background:#FAFBFC;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:240px" @change="loadCols"></v-autocomplete>
        <v-autocomplete v-model="domainClsfNm" :items="domainClsfList" label="도메인 분류" dense hide-details clearable
          style="max-width:160px" @change="loadCols"></v-autocomplete>
        <v-text-field v-model="filterObj"  label="테이블 검색" dense hide-details clearable style="max-width:160px"
          @change="loadCols"></v-text-field>
        <v-text-field v-model="filterAttr" label="컬럼 검색" dense hide-details clearable style="max-width:160px"
          @change="loadCols"></v-text-field>
        <v-text-field v-model.number="rateMin" label="적합률 최소" type="number" dense hide-details clearable
          style="max-width:120px" @change="loadCols"></v-text-field>
        <v-text-field v-model.number="rateMax" label="적합률 최대" type="number" dense hide-details clearable
          style="max-width:120px" @change="loadCols"></v-text-field>
        <v-btn small @click="loadCols" :disabled="!dmId" id="btn-colrule-reload">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
        <v-spacer></v-spacer>
        <span style="font-size:.85rem; color:#546E7A;">총 {{ rows.length }}건</span>
      </v-sheet>

      <!-- 그리드 -->
      <v-data-table :headers="headers" :items="rows" item-key="rowKey" dense hide-default-footer
        :items-per-page="200" :loading="loading" class="elevation-0" style="flex:1;">
        <template v-slot:item.effectiveSource="{ item }">
          <v-chip x-small :color="srcColor(item.effectiveSource)" text-color="white">
            {{ item.effectiveSource || '-' }}
          </v-chip>
        </template>
        <template v-slot:item.effectiveRuleNm="{ item }">
          <span v-if="item.effectiveRuleNm">{{ item.effectiveRuleNm }}</span>
          <span v-else style="color:#9E9E9E; font-size:.8rem;">(룰 없음)</span>
        </template>
        <template v-slot:item.profNullPct="{ item }">
          <span v-if="item.profTotal">{{ pct(item.profNull, item.profTotal) }}</span>
          <span v-else style="color:#9E9E9E; font-size:.8rem">-</span>
        </template>
        <template v-slot:item.ruleConformRate="{ item }">
          <span v-if="item.ruleConformRate != null"
            :style="{color: rateColor(item.ruleConformRate), 'font-weight': 600}">
            {{ Number(item.ruleConformRate).toFixed(1) }}%
          </span>
          <span v-else style="color:#9E9E9E; font-size:.8rem">-</span>
        </template>
        <template v-slot:item.actions="{ item }">
          <v-btn icon small @click="openDetail(item)" title="상세" id="btn-row-detail">
            <v-icon small>mdi-information-outline</v-icon>
          </v-btn>
          <v-btn icon small @click="rerun(item)" :disabled="!isAdmin" title="단위 재진단" id="btn-row-rerun">
            <v-icon small>mdi-play-circle-outline</v-icon>
          </v-btn>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 상세 drawer -->
    <v-navigation-drawer v-model="drawer" right temporary fixed width="600" style="z-index:99">
      <v-sheet v-if="detail" class="pa-4">
        <h3>{{ detail.objNm }}.{{ detail.attrNm }}
          <span style="font-size:.7rem; color:#90A4AE; margin-left:6px;">({{ detail.attrNmKr || '-' }})</span>
        </h3>
        <div class="mt-1 mb-2" style="font-size:.85rem; color:#546E7A;">
          <v-chip x-small color="indigo" text-color="white" v-if="detail.domainClsfNm">{{ detail.domainClsfNm }}</v-chip>
          <span v-if="detail.domainNm" class="ml-1">{{ detail.domainNm }}</span>
        </div>
        <v-divider class="my-3"></v-divider>

        <p class="font-weight-bold mb-1">적용 룰 (effective)</p>
        <div style="font-size:.9rem">
          <v-chip x-small :color="srcColor(detail.effectiveSource)" text-color="white">{{ detail.effectiveSource }}</v-chip>
          <span class="ml-1">{{ detail.effectiveRuleNm || '없음' }}</span>
          <span v-if="detail.effectiveRuleType" class="ml-1" style="color:#9E9E9E">({{ detail.effectiveRuleType }})</span>
        </div>

        <v-divider class="my-3"></v-divider>
        <p class="font-weight-bold mb-1">값 프로파일 (직전)</p>
        <table class="detail-tbl" v-if="detail.profTotal">
          <tr><td>총 행수</td>     <td>{{ detail.profTotal }}</td></tr>
          <tr><td>NULL</td>       <td>{{ detail.profNull }} ({{ pct(detail.profNull, detail.profTotal) }})</td></tr>
          <tr><td>유일값</td>     <td>{{ detail.profDistinct }}</td></tr>
          <tr><td>최소값</td>     <td>{{ detail.profMin || '-' }}</td></tr>
          <tr><td>최대값</td>     <td>{{ detail.profMax || '-' }}</td></tr>
          <tr><td>최소 길이</td>  <td>{{ detail.profMinLen != null ? detail.profMinLen : '-' }}</td></tr>
          <tr><td>최대 길이</td>  <td>{{ detail.profMaxLen != null ? detail.profMaxLen : '-' }}</td></tr>
        </table>
        <p v-else style="font-size:.8rem; color:#9E9E9E">아직 프로파일링 결과 없음</p>

        <v-divider class="my-3"></v-divider>
        <p class="font-weight-bold mb-1">룰별 적합률 (최근 N회)</p>
        <v-data-table v-if="ruleResults.length > 0"
          :headers="ruleResultHeaders" :items="ruleResults" dense hide-default-footer
          :items-per-page="20" class="elevation-0">
          <template v-slot:item.conformRate="{ item }">
            <span :style="{color: rateColor(item.conformRate), 'font-weight': 600}">
              {{ Number(item.conformRate).toFixed(2) }}%
            </span>
          </template>
        </v-data-table>
        <p v-else style="font-size:.8rem; color:#9E9E9E">룰 진단 이력 없음</p>

        <v-divider class="my-3"></v-divider>
        <p class="font-weight-bold mb-1">위반 샘플 (직전 5건)</p>
        <v-data-table v-if="violationSamples.length > 0"
          :headers="violationHeaders" :items="violationSamples" dense hide-default-footer
          :items-per-page="20" class="elevation-0">
          <template v-slot:item.violationValue="{ item }">
            <span style="font-family:Consolas,monospace; font-size:.8rem; color:#C62828;">
              {{ truncate(item.violationValue, 50) }}
            </span>
          </template>
        </v-data-table>
        <p v-else style="font-size:.8rem; color:#9E9E9E">위반 샘플 없음 (적합률 100% 또는 미진단)</p>
      </v-sheet>
    </v-navigation-drawer>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualColRule',
  props: ['isMobile'],
  data: () => ({
    isAdmin: false,
    dataModels: [],
    dmId: null,
    domainClsfList: [
      '전화번호','휴대전화번호','팩스번호','우편번호','구우편번호',
      '사업자등록번호','법인등록번호','주민등록번호','외국인등록번호',
      '운전면허번호','여권번호','신용카드번호','차대번호','자동차등록번호',
      '계좌번호','아이핀번호',
      '연도','연월','연월일','연월일시분','연월일시분초','시분','시분초','월',
      '위도','경도','좌표','면적','금액','가격','비용','요금','율',
      '본번','부번','건물본번','건물부번','건물번호','일련번호','순서','여부','유무',
    ],
    domainClsfNm: null,
    filterObj: '',
    filterAttr: '',
    rateMin: null,
    rateMax: null,
    rows: [],
    loading: false,
    headers: [
      { text: '테이블', value: 'objNm' },
      { text: '컬럼', value: 'attrNm' },
      { text: '한글명', value: 'attrNmKr' },
      { text: '도메인 분류', value: 'domainClsfNm', width: 130 },
      { text: '적용 룰', value: 'effectiveRuleNm' },
      { text: '소스', value: 'effectiveSource', width: 100 },
      { text: 'NULL%', value: 'profNullPct', width: 90 },
      { text: '적합률', value: 'ruleConformRate', width: 90 },
      { text: '', value: 'actions', sortable: false, width: 90 },
    ],
    drawer: false,
    detail: null,
    violationSamples: [],
    ruleResults: [],
    violationHeaders: [
      { text: 'PK', value: 'pkVal', width: 120 },
      { text: '위반값', value: 'violationValue' },
      { text: '룰', value: 'ruleNm' },
    ],
    ruleResultHeaders: [
      { text: '룰', value: 'ruleNm' },
      { text: '유형', value: 'ruleType', width: 90 },
      { text: '총건', value: 'totalCnt', width: 90 },
      { text: '위반', value: 'violationCnt', width: 90 },
      { text: '적합률', value: 'conformRate', width: 90 },
    ],
  }),
  mounted() {
    var self = this;
    axios.get(this.$APIURL.base + 'api/login/isAdmin',
      { params: { user: this.$loginStatusData && this.$loginStatusData.id } })
      .then(r => { self.isAdmin = r.data === true; })
      .catch(() => { self.isAdmin = false; });
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { connectedOnly: 'Y' })
      .then(r => {
        self.dataModels = (r.data || []).filter(m => m.modelType === 'PHYSICAL');
      });
  },
  methods: {
    loadCols() {
      if (!this.dmId) { this.rows = []; return; }
      this.loading = true;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/colrule/listWithLatest', { params: {
        dmId: this.dmId,
        objNm: this.filterObj || null,
        attrNm: this.filterAttr || null,
        domainClsfNm: this.domainClsfNm || null,
        rateMin: this.rateMin != null ? this.rateMin : null,
        rateMax: this.rateMax != null ? this.rateMax : null,
      } }).then(r => {
        self.rows = (r.data || []).map(x => {
          x.rowKey = x.objNm + '.' + x.attrNm;
          return x;
        }).filter(x => x.effectiveSource !== 'EXCLUDED');
      }).finally(() => { self.loading = false; });
    },
    openDetail(item) {
      this.detail = item;
      this.violationSamples = [];
      this.ruleResults = [];
      this.drawer = true;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/colrule/detail', {
        params: { dmId: this.dmId, objNm: item.objNm, attrNm: item.attrNm }
      }).then(r => {
        self.violationSamples = r.data.violationSamples || [];
        self.ruleResults = r.data.ruleResults || [];
      });
    },
    rerun(item) {
      var self = this;
      this.$swal.fire({
        title: '단위 재진단?',
        text: item.objNm + '.' + item.attrNm,
        showCancelButton: true,
        confirmButtonText: '실행',
      }).then(r => {
        if (!r.isConfirmed) return;
        const targets = [{ objNm: item.objNm, attrNm: item.attrNm }];
        const body = { dataModelId: this.dmId, sampleRate: 100, targets: targets };
        axios.post(self.$APIURL.base + 'api/qual/value/runColumns', body).then(() => {
          axios.post(self.$APIURL.base + 'api/qual/rule/runColumns', body).then(() => {
            self.$swal.fire({
              icon: 'success', title: '진단 시작',
              html: '약 30초 후 [새로고침] 클릭하여 결과 확인',
              timer: 2000, showConfirmButton: false
            });
          });
        });
      });
    },
    pct(n, t) { if (!t) return '0.0%'; return ((n / t) * 100).toFixed(1) + '%'; },
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
    },
    truncate(s, n) {
      s = s == null ? '' : String(s);
      return s.length > n ? s.substring(0, n) + '…' : s;
    }
  }
};
</script>

<style scoped>
.detail-tbl { width: 100%; font-size: .85rem; }
.detail-tbl td { padding: 4px 8px; border-bottom: 1px solid #ECEFF1; }
.detail-tbl td:first-child { color: #607D8B; width: 35%; }
</style>
