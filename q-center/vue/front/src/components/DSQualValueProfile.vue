<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">

      <!-- 상단: 모델 + 샘플링 + 시작 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" @change="loadCols"></v-autocomplete>
        <v-select v-model="sampleRate" :items="sampleOpts" item-text="text" item-value="value"
          label="샘플링" dense hide-details style="max-width:130px"></v-select>
        <v-spacer></v-spacer>
        <span style="font-size:.8rem; color:#546E7A;">선택 {{ selected.length }}건</span>
        <v-btn small class="ml-2 gradient" @click="runSelected"
          :disabled="!dmId || selected.length===0" id="btn-run-selected">
          <v-icon small left>mdi-play</v-icon>선택 컬럼 프로파일링
        </v-btn>
      </v-sheet>

      <!-- 검색 필터 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #ECEFF1; gap:8px; background:#FAFBFC;">
        <v-text-field v-model="filterObj"  label="테이블 필터" dense hide-details clearable style="max-width:200px"></v-text-field>
        <v-text-field v-model="filterAttr" label="컬럼 필터"  dense hide-details clearable style="max-width:200px"></v-text-field>
        <v-spacer></v-spacer>
        <v-btn x-small text @click="selectAll">전체선택</v-btn>
        <v-btn x-small text @click="selectNone">선택해제</v-btn>
      </v-sheet>

      <!-- 컬럼 그리드: 체크박스 + 적용 규칙 + 적합률 + [상세] -->
      <v-data-table
        v-model="selected"
        :headers="headers"
        :items="filtered"
        item-key="rowKey"
        show-select
        dense hide-default-footer
        :items-per-page="200"
        class="elevation-0" :loading="loading">
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
      loading: false,
      drawer: false,
      detail: null,
      headers: [
        { text: '테이블',    value: 'objNm' },
        { text: '컬럼',      value: 'attrNm' },
        { text: '한글명',    value: 'attrNmKr' },
        { text: '적용 규칙', value: 'effectiveRuleNm' },
        { text: '소스',      value: 'effectiveSource' },
        { text: 'NULL%',     value: 'profNullPct' },
        { text: '적합률',    value: 'ruleConformRate' },
        { text: '',          value: 'actions', sortable: false, width: 50 }
      ]
    };
  },
  computed: {
    filtered() {
      var fo = (this.filterObj || '').toLowerCase();
      var fa = (this.filterAttr || '').toLowerCase();
      return this.rows.filter(function(r) {
        if (fo && (r.objNm || '').toLowerCase().indexOf(fo) === -1) return false;
        if (fa && (r.attrNm || '').toLowerCase().indexOf(fa) === -1) return false;
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
  methods: {
    loadCols() {
      if (!this.dmId) { this.rows = []; this.selected = []; return; }
      this.loading = true;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/colrule/listWithLatest',
                { params: { dmId: this.dmId } })
        .then(function(r) {
          // EXCLUDED/NONE 빼고 표시 (값 진단 가능한 컬럼만)
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

      // 값 진단 + 룰 진단 둘 다 실행 (적합률도 같이 갱신)
      axios.post(this.$APIURL.base + 'api/qual/value/runColumns', body).then(function(rv) {
        axios.post(self.$APIURL.base + 'api/qual/rule/runColumns', body).then(function(rr) {
          self.$swal.fire({
            icon: 'success',
            title: targets.length + '개 컬럼 진단 시작',
            html: '값 ' + (rv.data.contents || '') + '<br>룰 ' + (rr.data.contents || '') +
                  '<br><span style="font-size:.75rem;color:#9E9E9E">완료 후 [새로고침]</span>',
            timer: 2500, showConfirmButton: false
          });
          // 30초 후 자동 새로고침
          setTimeout(function() { self.loadCols(); }, 30000);
        });
      });
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
