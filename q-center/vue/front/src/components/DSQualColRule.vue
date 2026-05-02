<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" @change="loadCols"></v-autocomplete>
        <v-text-field v-model="filterObj" label="테이블 (선택)" dense hide-details style="max-width:200px"
          @change="loadCols"></v-text-field>
        <v-spacer></v-spacer>
        <v-btn small @click="loadCols" :disabled="!dmId" id="btn-colrule-reload">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-sheet>

      <v-data-table :headers="headers" :items="rows" dense hide-default-footer
        :items-per-page="200" class="elevation-0" :loading="loading">
        <template v-slot:item.effectiveSource="{ item }">
          <v-chip x-small :color="srcColor(item.effectiveSource)" text-color="white">
            {{ item.effectiveSource }}
          </v-chip>
        </template>
        <template v-slot:item.actions="{ item }">
          <v-btn icon small @click="openChange(item)" title="규칙 변경"><v-icon small>mdi-pencil</v-icon></v-btn>
          <v-btn icon small @click="reDiag(item)" title="이 컬럼만 재진단" id="btn-colrule-rediag">
            <v-icon small>mdi-play</v-icon>
          </v-btn>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 규칙 변경 다이얼로그 -->
    <v-dialog v-model="dialog" max-width="600" persistent>
      <v-card>
        <v-card-title>{{ form.objNm }}.{{ form.attrNm }} — 적용 규칙 변경</v-card-title>
        <v-card-text>
          <p style="font-size:.85rem; color:#546E7A; margin-bottom:8px">
            현재 적용: <b>{{ form.effectiveRuleNm || '없음' }}</b>
            (<v-chip x-small :color="srcColor(form.effectiveSource)" text-color="white">{{ form.effectiveSource }}</v-chip>)
          </p>
          <v-radio-group v-model="form.choice" dense>
            <v-radio label="도메인 default 적용 (매핑 해제)" value="DEFAULT"></v-radio>
            <v-radio label="도메인 룰 직접 선택" value="DOMAIN"></v-radio>
            <v-radio label="커스텀 룰 등록 후 매핑" value="CUSTOM"></v-radio>
            <v-radio label="진단 제외" value="EXCLUDE"></v-radio>
          </v-radio-group>

          <v-select v-if="form.choice==='DOMAIN'" v-model="form.domainRuleId"
            :items="domainRules" item-text="ruleNm" item-value="domainRuleId"
            label="도메인 룰 선택" dense hide-details></v-select>

          <div v-if="form.choice==='CUSTOM'" style="border:1px solid #E8EAF6; padding:8px; margin-top:8px;">
            <v-text-field v-model="form.customNm" label="커스텀 룰명" dense></v-text-field>
            <v-select v-model="form.customType" :items="ruleTypes" label="유형 *" dense></v-select>
            <v-textarea v-model="form.customParams" label="파라미터 JSON" dense rows="2"
              placeholder='{"pattern":"^\\d+$"}'></v-textarea>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="dialog=false">취소</v-btn>
          <v-btn class="gradient" @click="save" id="btn-colrule-save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualColRule',
  data() {
    return {
      dataModels: [], dmId: null, filterObj: '',
      rows: [], loading: false,
      headers: [
        { text: '테이블', value: 'objNm' },
        { text: '컬럼', value: 'attrNm' },
        { text: '적용 규칙', value: 'effectiveRuleNm' },
        { text: '유형', value: 'effectiveRuleType' },
        { text: 'SORT', value: 'effectiveSortOrd' },
        { text: '소스', value: 'effectiveSource' },
        { text: '', value: 'actions', sortable: false }
      ],
      dialog: false, form: {},
      domainRules: [],
      ruleTypes: ['NOT_NULL','RANGE','LENGTH','REGEX','ENUM','UNIQUE','REFERENCE','COMPARE']
    };
  },
  mounted() {
    var self = this;
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {})
      .then(function(r) {
        self.dataModels = (r.data || []).filter(function(m) { return m.modelType === 'PHYSICAL'; });
      });
  },
  methods: {
    loadCols() {
      if (!this.dmId) { this.rows = []; return; }
      this.loading = true;
      var params = { dmId: this.dmId };
      if (this.filterObj) params.objNm = this.filterObj;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/colrule/list', { params: params })
        .then(function(r) { self.rows = r.data || []; })
        .finally(function() { self.loading = false; });
    },
    openChange(item) {
      this.form = Object.assign({ choice: 'DEFAULT' }, item);
      this.domainRules = [];
      // 도메인 정보 미상이라 전체 도메인 룰 목록 가져오기 — 1차 단순화: 컬럼의 effective domain 만 query
      // 대안: 전체 룰 목록을 받아 사용자가 보고 선택
      var self = this;
      // TB_DATA_MODEL_ATTR 의 도메인 ID 알 길이 없어서, 컬럼명 한글 기반 추정 — 1차에선 도메인룰 전체 표시
      axios.get(this.$APIURL.base + 'api/qual/domain/rules', { params: { domainId: '*' } })
        .then(function() { /* placeholder */ })
        .catch(function() {});
      this.dialog = true;
    },
    save() {
      var self = this;
      var f = this.form;
      var body = { dmId: f.dmId, objNm: f.objNm, attrNm: f.attrNm,
                   domainRuleId: null, customRuleId: null, excludeYn: 'N' };

      if (f.choice === 'EXCLUDE')      body.excludeYn = 'Y';
      else if (f.choice === 'DOMAIN')  body.domainRuleId = f.domainRuleId;
      else if (f.choice === 'CUSTOM') {
        // 1차: 커스텀 룰 신규 등록 후 그 ID 매핑
        axios.post(this.$APIURL.base + 'api/qual/rule/save', {
          dmId: f.dmId, objNm: f.objNm, attrNm: f.attrNm,
          ruleNm: f.customNm, ruleType: f.customType, ruleParams: f.customParams,
          severity: 'WARN'
        }).then(function(rr) {
          if (rr.data.resultCode === 200) {
            body.customRuleId = rr.data.contents;
            self._upsertColRule(body);
          } else { self.$swal.fire({ icon: 'error', title: '커스텀 룰 등록 실패' }); }
        });
        return;
      }
      this._upsertColRule(body);
    },
    _upsertColRule(body) {
      var self = this;
      axios.post(this.$APIURL.base + 'api/qual/colrule/save', body)
        .then(function(r) {
          if (r.data.resultCode === 200) {
            self.$swal.fire({ icon: 'success', title: '저장 완료', timer: 1200, showConfirmButton: false });
            self.dialog = false;
            self.loadCols();
          } else {
            self.$swal.fire({ icon: 'error', title: '실패', text: r.data.resultMessage });
          }
        });
    },
    reDiag(item) {
      var self = this;
      axios.post(this.$APIURL.base + 'api/qual/value/runColumn', {
        dataModelId: item.dmId, objNm: item.objNm, attrNm: item.attrNm, sampleRate: 100
      }).then(function(rv) {
        axios.post(self.$APIURL.base + 'api/qual/rule/runColumn', {
          dataModelId: item.dmId, objNm: item.objNm, attrNm: item.attrNm,
          sampleRate: 100, incrementalYn: 'N'
        }).then(function(rr) {
          self.$swal.fire({
            icon: 'success', title: item.objNm + '.' + item.attrNm + ' 재진단 시작',
            html: '값 진단 ' + (rv.data.contents || '') + '<br>룰 진단 ' + (rr.data.contents || '')
          });
        });
      });
    },
    srcColor(s) {
      switch (s) {
        case 'DOMAIN':  return 'blue';
        case 'CUSTOM':  return 'purple';
        case 'DEFAULT': return 'green';
        case 'EXCLUDED':return 'grey';
        default:        return 'orange';
      }
    }
  }
};
</script>
