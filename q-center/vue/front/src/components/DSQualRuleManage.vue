<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:6px;">
        <v-autocomplete v-model="filterDmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          label="모델" dense hide-details style="max-width:280px" clearable @change="loadRules"></v-autocomplete>
        <v-spacer></v-spacer>
        <v-btn v-if="isAdmin" small class="gradient" @click="openAdd"
          :disabled="!filterDmId" id="btn-rule-add">
          <v-icon small left>mdi-plus</v-icon>룰 추가
        </v-btn>
        <v-btn v-if="isAdmin" small outlined @click="openCatalog"
          :disabled="!filterDmId" class="ml-2" id="btn-rule-catalog">
          <v-icon small left>mdi-book-open-variant</v-icon>카탈로그
        </v-btn>
      </v-sheet>

      <v-data-table :headers="headers" :items="rules" dense hide-default-footer
        :items-per-page="50" class="elevation-0" :loading="loading">
        <template v-slot:item.estCost="{ item }">
          <v-chip x-small :color="costColor(item.estCost)" text-color="white">{{ item.estCost || 'MID' }}</v-chip>
        </template>
        <template v-slot:item.severity="{ item }">
          <v-chip x-small :color="sevColor(item.severity)" text-color="white">{{ item.severity }}</v-chip>
        </template>
        <template v-slot:item.useYn="{ item }">
          <v-icon small :color="item.useYn === 'Y' ? 'green' : 'grey'">
            {{ item.useYn === 'Y' ? 'mdi-check-circle' : 'mdi-close-circle' }}
          </v-icon>
        </template>
        <template v-slot:item.actions="{ item }">
          <v-btn v-if="isAdmin" icon small @click="openEdit(item)"><v-icon small>mdi-pencil</v-icon></v-btn>
          <v-btn v-if="isAdmin" icon small @click="del(item)"><v-icon small>mdi-delete</v-icon></v-btn>
        </template>
      </v-data-table>

      <v-sheet class="pa-2 d-flex align-center" style="border-top:1px solid #E8EAF6">
        <v-spacer></v-spacer>
        <span class="mr-2" style="font-size:.8rem">샘플링</span>
        <v-select v-model="runSampleRate" :items="sampleOpts" item-text="text" item-value="value"
          dense hide-details style="max-width:130px"></v-select>
        <v-checkbox v-model="runIncremental" label="증분" hide-details dense class="ml-3 mt-0"></v-checkbox>
        <v-btn small class="ml-3 gradient" @click="runDiag" :disabled="!filterDmId || rules.length===0"
          id="btn-rule-run">
          <v-icon small left>mdi-play</v-icon>진단 실행
        </v-btn>
      </v-sheet>
    </v-sheet>

    <!-- 룰 추가/수정 다이얼로그 -->
    <v-dialog v-model="dialog" max-width="600" persistent>
      <v-card>
        <v-card-title>{{ form.ruleId ? '룰 수정' : '룰 추가' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.ruleNm" label="룰명 *" dense></v-text-field>
          <v-text-field v-model="form.objNm" label="테이블명" dense></v-text-field>
          <v-text-field v-model="form.attrNm" label="컬럼명" dense></v-text-field>
          <v-select v-model="form.ruleType" :items="ruleTypes" label="유형 *" dense @change="syncCost"></v-select>
          <v-textarea v-model="form.ruleParams" label='파라미터 JSON (예: {"min":0,"max":100})' dense rows="3"></v-textarea>
          <v-select v-model="form.severity" :items="['ERROR','WARN','INFO']" label="심각도" dense></v-select>
          <v-text-field v-model="form.incrementalCol" label="증분 트리거 컬럼 (UPDT_DT 등, 옵션)" dense></v-text-field>
          <v-text-field v-model="form.descr" label="설명" dense></v-text-field>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="dialog=false">취소</v-btn>
          <v-btn class="gradient" @click="save" id="btn-rule-save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 카탈로그 -->
    <v-dialog v-model="catalogDialog" max-width="700">
      <v-card>
        <v-card-title>룰 카탈로그</v-card-title>
        <v-card-text>
          <v-data-table :headers="catalogHeaders" :items="catalog" dense hide-default-footer
            :items-per-page="20">
            <template v-slot:item.actions="{ item }">
              <v-btn x-small @click="importCat(item)">가져오기</v-btn>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="catalogDialog=false">닫기</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualRuleManage',
  data() {
    return {
      loading: false,
      dataModels: [],
      filterDmId: null,
      rules: [],
      headers: [
        { text: '룰명', value: 'ruleNm' },
        { text: '대상', value: 'objNm' },
        { text: '컬럼', value: 'attrNm' },
        { text: '유형', value: 'ruleType' },
        { text: '비용', value: 'estCost' },
        { text: '심각도', value: 'severity' },
        { text: '사용', value: 'useYn' },
        { text: '', value: 'actions', sortable: false }
      ],
      ruleTypes: ['NOT_NULL','RANGE','LENGTH','REGEX','ENUM','UNIQUE','REFERENCE','COMPARE'],
      sampleOpts: [
        { text: '1만건', value: 1 },
        { text: '10%',   value: 10 },
        { text: '100%',  value: 100 }
      ],
      runSampleRate: 100,
      runIncremental: false,
      dialog: false,
      form: {},
      catalogDialog: false,
      catalog: [],
      catalogHeaders: [
        { text: '카탈로그명', value: 'catalogNm' },
        { text: '유형', value: 'ruleType' },
        { text: '카테고리', value: 'category' },
        { text: '', value: 'actions', sortable: false }
      ],
      isAdmin: false
    };
  },
  mounted() {
    var self = this;
    axios.get(this.$APIURL.base + 'api/login/isAdmin',
      { params: { user: this.$loginStatusData && this.$loginStatusData.id } })
      .then(function(res) { self.isAdmin = res.data === true; })
      .catch(function() { self.isAdmin = false; });
    this.loadModels();
  },
  methods: {
    loadModels() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {}).then(r => {
        this.dataModels = (r.data || []).filter(m => m.modelType === 'PHYSICAL');
      });
    },
    loadRules() {
      if (!this.filterDmId) { this.rules = []; return; }
      this.loading = true;
      axios.post(this.$APIURL.base + 'api/qual/rule/list', { dmId: this.filterDmId, useYn: 'Y' })
        .then(r => { this.rules = r.data || []; })
        .finally(() => { this.loading = false; });
    },
    openAdd() {
      this.form = { dmId: this.filterDmId, severity: 'WARN', ruleType: 'NOT_NULL', useYn: 'Y' };
      this.dialog = true;
    },
    openEdit(item) {
      this.form = Object.assign({}, item);
      this.dialog = true;
    },
    save() {
      if (!this.form.ruleNm || !this.form.ruleType) {
        this.$swal.fire({ icon: 'warning', title: '룰명/유형 필수' }); return;
      }
      axios.post(this.$APIURL.base + 'api/qual/rule/save', this.form).then(r => {
        if (r.data.resultCode === 200) {
          this.$swal.fire({ icon: 'success', title: '저장 완료' });
          this.dialog = false;
          this.loadRules();
        } else {
          this.$swal.fire({ icon: 'error', title: '실패', text: r.data.message });
        }
      });
    },
    del(item) {
      this.$swal.fire({ title: '룰 삭제?', showCancelButton: true }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/qual/rule/delete', { ruleId: item.ruleId }).then(() => this.loadRules());
      });
    },
    runDiag() {
      const body = {
        dataModelId: this.filterDmId,
        sampleRate: this.runSampleRate,
        incrementalYn: this.runIncremental ? 'Y' : 'N'
      };
      axios.post(this.$APIURL.base + 'api/qual/rule/run', body).then(r => {
        if (r.data.resultCode === 200) {
          this.$swal.fire({
            icon: 'success', title: '진단 시작',
            html: 'diagId: ' + r.data.contents + '<br>결과 화면에서 확인하세요'
          });
        } else {
          this.$swal.fire({ icon: 'error', title: '실패', text: r.data.message });
        }
      });
    },
    openCatalog() {
      axios.get(this.$APIURL.base + 'api/qual/rule/catalog').then(r => {
        this.catalog = r.data || [];
        this.catalogDialog = true;
      });
    },
    importCat(c) {
      this.$swal.fire({
        title: '카탈로그 가져오기',
        input: 'text',
        inputLabel: '대상 테이블.컬럼 (예: TB_MEMBER.EMAIL)',
        showCancelButton: true
      }).then(r => {
        if (!r.isConfirmed || !r.value) return;
        const parts = r.value.split('.');
        if (parts.length !== 2) { this.$swal.fire({ icon: 'warning', title: '형식 오류' }); return; }
        axios.post(this.$APIURL.base + 'api/qual/rule/importFromCatalog', {
          dataModelId: this.filterDmId, catalogId: c.catalogId, objNm: parts[0], attrNm: parts[1]
        }).then(rr => {
          if (rr.data.resultCode === 200) {
            this.$swal.fire({ icon: 'success', title: '등록 완료' });
            this.catalogDialog = false;
            this.loadRules();
          }
        });
      });
    },
    syncCost() {
      const high = ['REGEX','EXPRESSION'];
      const low = ['NOT_NULL','ENUM'];
      this.form.estCost = high.includes(this.form.ruleType) ? 'HIGH'
                         : low.includes(this.form.ruleType) ? 'LOW' : 'MID';
    },
    costColor(c) { return c === 'HIGH' ? 'red' : c === 'LOW' ? 'green' : 'orange'; },
    sevColor(s)  { return s === 'ERROR' ? 'red' : s === 'WARN' ? 'orange' : 'blue'; }
  }
};
</script>
