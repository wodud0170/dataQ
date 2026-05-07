<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <!-- 상단 헤더 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:8px;">
        <span style="font-size:1.1rem; font-weight:600; color:#1A237E;">도메인 룰 관리</span>
        <span style="font-size:.8rem; color:#9E9E9E;">— 분류 단위 룰 정의 + 카탈로그 가져오기</span>
        <v-spacer></v-spacer>
        <v-text-field v-model="treeSearch" label="도메인 검색" dense hide-details clearable
          prepend-inner-icon="mdi-magnify" style="max-width:240px"
          @input="loadTree"></v-text-field>
        <v-btn small outlined @click="openCatalogDialog" id="btn-catalog-open">
          <v-icon small left>mdi-book-open-variant</v-icon>카탈로그
        </v-btn>
      </v-sheet>

      <v-row no-gutters style="flex:1; min-height:0;">
        <!-- 좌측 트리: 분류 → 도메인 -->
        <v-col cols="3" style="border-right:1px solid #ECEFF1; overflow:auto;">
          <v-treeview
            :items="treeItems"
            :open.sync="treeOpen"
            item-key="key"
            item-children="children"
            item-text="label"
            activatable
            :active.sync="treeActive"
            @update:active="onSelectDomain"
            dense hoverable open-on-click>
            <template v-slot:prepend="{ item }">
              <v-icon small :color="item.isDomain ? 'indigo' : 'grey'">
                {{ item.isDomain ? 'mdi-tag' : 'mdi-folder-outline' }}
              </v-icon>
            </template>
            <template v-slot:append="{ item }">
              <span v-if="item.isDomain && item.ruleCnt > 0"
                    style="font-size:.75rem; color:#3F51B5; font-weight:600;">
                {{ item.ruleCnt }}
              </span>
            </template>
          </v-treeview>
        </v-col>

        <!-- 우측 룰 패널 -->
        <v-col cols="9" style="display:flex; flex-direction:column; min-height:0;">
          <v-sheet v-if="!selectedDomain" class="pa-6 text-center" style="color:#9E9E9E;">
            좌측에서 도메인을 선택하세요
          </v-sheet>
          <template v-else>
            <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #ECEFF1; gap:8px;">
              <span style="font-weight:600; color:#1A237E;">{{ selectedDomain.domainNm }}</span>
              <v-chip x-small color="indigo" text-color="white">{{ selectedDomain.domainClsfNm || '미분류' }}</v-chip>
              <span style="font-size:.8rem; color:#9E9E9E;">
                {{ selectedDomain.dataType }}{{ selectedDomain.dataLen ? '(' + selectedDomain.dataLen + ')' : '' }}
              </span>
              <v-spacer></v-spacer>
              <v-btn small outlined @click="openImportDialog" id="btn-import-catalog">
                <v-icon small left>mdi-download</v-icon>카탈로그에서 가져오기
              </v-btn>
              <v-btn small color="indigo" dark @click="openAddDialog" id="btn-rule-add">
                <v-icon small left>mdi-plus</v-icon>룰 추가
              </v-btn>
            </v-sheet>

            <!-- 룰 그리드 -->
            <v-data-table :headers="ruleHeaders" :items="rules" dense hide-default-footer
              :items-per-page="50" :loading="loadingRules"
              class="elevation-0" style="flex:1;">
              <template v-slot:item.ruleType="{ item }">
                <v-chip x-small :color="typeColor(item.ruleType)" text-color="white">{{ item.ruleType }}</v-chip>
              </template>
              <template v-slot:item.ruleParams="{ item }">
                <span style="font-family:Consolas,monospace; font-size:.8rem;">
                  {{ summaryParams(item.ruleParams) }}
                </span>
              </template>
              <template v-slot:item.useYn="{ item }">
                <v-icon small :color="item.useYn === 'Y' ? 'green' : 'grey'">
                  {{ item.useYn === 'Y' ? 'mdi-check-circle' : 'mdi-close-circle' }}
                </v-icon>
              </template>
              <template v-slot:item.actions="{ item }">
                <v-btn icon small @click="openEditDialog(item)" :disabled="!isAdmin"
                       title="수정" id="btn-rule-edit">
                  <v-icon small>mdi-pencil</v-icon>
                </v-btn>
                <v-btn icon small @click="deleteRule(item)" :disabled="!isAdmin"
                       title="삭제" id="btn-rule-del">
                  <v-icon small>mdi-delete</v-icon>
                </v-btn>
              </template>
              <template v-slot:no-data>
                <v-alert dense type="info" outlined class="ma-2">
                  적용된 룰이 없습니다. [룰 추가] 또는 [카탈로그에서 가져오기] 로 시작하세요.
                </v-alert>
              </template>
            </v-data-table>
          </template>
        </v-col>
      </v-row>
    </v-sheet>

    <!-- 룰 추가/수정 다이얼로그 — 타입별 위젯 -->
    <v-dialog v-model="ruleDialog" max-width="640" persistent>
      <v-card>
        <v-card-title class="indigo white--text">{{ form.domainRuleId ? '룰 수정' : '룰 추가' }}</v-card-title>
        <v-card-text class="pt-4">
          <v-row dense>
            <v-col cols="6">
              <v-text-field v-model="form.ruleNm" label="룰명 *" dense
                            :rules="[v => !!v || '필수']"></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-select v-model="form.ruleType" :items="ruleTypes" label="유형 *" dense
                        @change="onTypeChange"></v-select>
            </v-col>
          </v-row>

          <!-- NOT_NULL — 파라미터 없음 -->
          <v-alert v-if="form.ruleType === 'NOT_NULL'" type="info" dense outlined class="mt-2">
            NULL 금지. 추가 파라미터 없음.
          </v-alert>

          <!-- RANGE -->
          <v-row v-else-if="form.ruleType === 'RANGE'" dense class="mt-2">
            <v-col cols="4">
              <v-text-field v-model="rangeMin" label="최소값" type="number" dense
                            @input="syncRangeParams"></v-text-field>
            </v-col>
            <v-col cols="4">
              <v-text-field v-model="rangeMax" label="최대값" type="number" dense
                            @input="syncRangeParams"></v-text-field>
            </v-col>
            <v-col cols="4" class="d-flex align-center">
              <v-checkbox v-model="rangeInteger" label="정수만" hide-details dense
                          @change="syncRangeParams"></v-checkbox>
            </v-col>
          </v-row>

          <!-- LENGTH -->
          <v-row v-else-if="form.ruleType === 'LENGTH'" dense class="mt-2">
            <v-col cols="6">
              <v-text-field v-model="lenMin" label="최소 길이" type="number" dense min="0"
                            @input="syncLenParams"></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-text-field v-model="lenMax" label="최대 길이" type="number" dense min="0"
                            @input="syncLenParams"></v-text-field>
            </v-col>
          </v-row>

          <!-- REGEX — 패턴 + 실시간 테스트 -->
          <div v-else-if="form.ruleType === 'REGEX'" class="mt-2">
            <v-textarea v-model="regexPattern" label="정규식 (pattern)" dense rows="2"
                        hint="예: ^0\d{1,2}-?\d{3,4}-?\d{4}$ (Java/PCRE 호환)"
                        persistent-hint @input="syncRegexParams"></v-textarea>
            <v-text-field v-model="regexTestInput" label="테스트 입력" dense
                          hint="값 입력 시 실시간 매칭" persistent-hint class="mt-2"></v-text-field>
            <v-alert v-if="regexTestInput" :type="regexTestResult ? 'success' : 'error'"
                     dense outlined class="mt-2">
              {{ regexTestResult ? '매칭 OK' : '매칭 실패' }}
            </v-alert>
          </div>

          <!-- ENUM — 칩 입력 -->
          <div v-else-if="form.ruleType === 'ENUM'" class="mt-2">
            <v-combobox v-model="enumValues" label="허용값 (Tab/Enter 로 추가)"
                        chips deletable-chips multiple dense small-chips
                        @input="syncEnumParams"></v-combobox>
          </div>

          <!-- 다른 타입 (UNIQUE, REFERENCE, COMPARE) — 일단 raw JSON -->
          <v-textarea v-else v-model="form.ruleParams" label="파라미터 JSON" dense rows="3"
                      placeholder='{"key":"value"}'></v-textarea>

          <v-row dense class="mt-2">
            <v-col cols="3">
              <v-text-field v-model.number="form.sortOrd" label="우선순위" type="number" dense min="1"></v-text-field>
            </v-col>
            <v-col cols="3">
              <v-checkbox v-model="form.useYnBool" label="사용" hide-details dense></v-checkbox>
            </v-col>
            <v-col cols="6">
              <v-text-field v-model="form.descr" label="설명" dense></v-text-field>
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="ruleDialog = false">취소</v-btn>
          <v-btn color="indigo" dark @click="saveRule" id="btn-rule-save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 카탈로그 다이얼로그 -->
    <v-dialog v-model="catalogDialog" max-width="900" persistent>
      <v-card>
        <v-card-title class="indigo white--text">
          카탈로그
          <v-spacer></v-spacer>
          <v-text-field v-model="catSearch" placeholder="검색" dark dense hide-details
            prepend-inner-icon="mdi-magnify" style="max-width:280px"
            @input="loadCatalog"></v-text-field>
        </v-card-title>
        <v-tabs v-model="catTab" background-color="indigo lighten-5">
          <v-tab>시스템 기본 ({{ catSystemCnt }})</v-tab>
          <v-tab>사용자 정의 ({{ catUserCnt }})</v-tab>
        </v-tabs>
        <v-card-text class="pa-0" style="max-height:60vh; overflow:auto;">
          <v-data-table :headers="catalogHeaders" :items="catRowsFiltered" dense hide-default-footer
            :items-per-page="100" :loading="loadingCatalog">
            <template v-slot:item.ruleType="{ item }">
              <v-chip x-small :color="typeColor(item.ruleType)" text-color="white">{{ item.ruleType }}</v-chip>
            </template>
            <template v-slot:item.actions="{ item }">
              <v-btn x-small color="green" dark @click="mapToDomain(item)" :disabled="!selectedDomain"
                     title="현재 도메인에 매핑">
                <v-icon x-small left>mdi-link-variant</v-icon>매핑
              </v-btn>
              <v-btn x-small outlined color="indigo" @click="forkCatalog(item)" class="ml-1"
                     :disabled="!isAdmin" title="복사하여 사용자 정의로">
                <v-icon x-small left>mdi-content-copy</v-icon>복사
              </v-btn>
              <v-btn v-if="item.isBuiltIn === 'N' && isAdmin" x-small icon @click="deleteUserCatalog(item)"
                     class="ml-1" title="삭제 (사용자 정의만)">
                <v-icon x-small>mdi-delete</v-icon>
              </v-btn>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="catalogDialog = false">닫기</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSQualDomainRule',
  props: ['isMobile'],
  data: () => ({
    isAdmin: false,
    treeSearch: '',
    treeItems: [],
    treeOpen: [],
    treeActive: [],
    selectedDomain: null,
    rules: [],
    loadingRules: false,
    ruleHeaders: [
      { text: '룰명', value: 'ruleNm' },
      { text: '유형', value: 'ruleType', width: 100 },
      { text: '파라미터', value: 'ruleParams' },
      { text: '우선순위', value: 'sortOrd', width: 80, align: 'center' },
      { text: '사용', value: 'useYn', width: 60, align: 'center' },
      { text: '', value: 'actions', sortable: false, width: 100 },
    ],
    ruleDialog: false,
    form: {},
    ruleTypes: ['NOT_NULL', 'RANGE', 'LENGTH', 'REGEX', 'ENUM', 'UNIQUE', 'REFERENCE', 'COMPARE'],
    // 타입별 위젯 상태
    rangeMin: null, rangeMax: null, rangeInteger: false,
    lenMin: null, lenMax: null,
    regexPattern: '', regexTestInput: '',
    enumValues: [],
    // 카탈로그
    catalogDialog: false,
    catTab: 0,
    catSearch: '',
    catRows: [],
    loadingCatalog: false,
    catalogHeaders: [
      { text: '카탈로그명', value: 'catalogNm' },
      { text: '유형', value: 'ruleType', width: 100 },
      { text: '도메인 분류', value: 'domainClsfNm', width: 130 },
      { text: '카테고리', value: 'category', width: 120 },
      { text: '', value: 'actions', sortable: false, width: 220 },
    ],
  }),
  computed: {
    catRowsFiltered() {
      const want = this.catTab === 0 ? 'Y' : 'N';
      return this.catRows.filter(r => r.isBuiltIn === want);
    },
    catSystemCnt() { return this.catRows.filter(r => r.isBuiltIn === 'Y').length; },
    catUserCnt()   { return this.catRows.filter(r => r.isBuiltIn === 'N').length; },
    regexTestResult() {
      if (!this.regexPattern || !this.regexTestInput) return false;
      try {
        return new RegExp(this.regexPattern).test(this.regexTestInput);
      } catch (_) { return false; }
    }
  },
  mounted() {
    var self = this;
    axios.get(this.$APIURL.base + 'api/login/isAdmin',
      { params: { user: this.$loginStatusData && this.$loginStatusData.id } })
      .then(r => { self.isAdmin = r.data === true; })
      .catch(() => { self.isAdmin = false; });
    this.loadTree();
  },
  methods: {
    loadTree() {
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/domain/tree',
                { params: { schNm: this.treeSearch || null } })
        .then(r => {
          // 분류별 그룹화 → 트리 데이터
          const groups = {};
          (r.data || []).forEach(d => {
            const c = d.domainClsfNm || '미분류';
            if (!groups[c]) groups[c] = [];
            groups[c].push({
              key: 'd_' + d.domainId,
              label: d.domainNm + (d.dataType ? ' (' + d.dataType +
                     (d.dataLen ? '/' + d.dataLen : '') + ')' : ''),
              isDomain: true,
              domainId: d.domainId,
              domainNm: d.domainNm,
              domainClsfNm: d.domainClsfNm,
              dataType: d.dataType,
              dataLen: d.dataLen,
              ruleCnt: d.ruleCnt,
            });
          });
          self.treeItems = Object.keys(groups).sort().map(c => ({
            key: 'g_' + c,
            label: c + ' (' + groups[c].length + ')',
            isDomain: false,
            children: groups[c],
          }));
        });
    },
    onSelectDomain(activeKeys) {
      // v-treeview activatable=true 일 때 active 는 [key]
      const k = activeKeys[0];
      if (!k || !k.startsWith('d_')) {
        this.selectedDomain = null;
        this.rules = [];
        return;
      }
      // 트리에서 해당 노드 찾기
      const node = this.findTreeNode(k);
      if (!node) return;
      this.selectedDomain = node;
      this.loadRules();
    },
    findTreeNode(key) {
      for (const g of this.treeItems) {
        for (const d of (g.children || [])) {
          if (d.key === key) return d;
        }
      }
      return null;
    },
    loadRules() {
      if (!this.selectedDomain) return;
      this.loadingRules = true;
      var self = this;
      axios.get(this.$APIURL.base + 'api/qual/domain/rules',
                { params: { domainId: this.selectedDomain.domainId } })
        .then(r => { self.rules = r.data || []; })
        .finally(() => { self.loadingRules = false; });
    },

    // ── 룰 추가/수정 ──
    openAddDialog() {
      this.form = {
        domainRuleId: null,
        domainId: this.selectedDomain.domainId,
        ruleNm: '',
        ruleType: 'NOT_NULL',
        ruleParams: '{}',
        sortOrd: 1,
        useYn: 'Y',
        useYnBool: true,
        descr: '',
      };
      this.resetTypeWidgets();
      this.ruleDialog = true;
    },
    openEditDialog(item) {
      this.form = Object.assign({}, item);
      this.form.useYnBool = item.useYn === 'Y';
      this.parseParamsToWidgets(item);
      this.ruleDialog = true;
    },
    resetTypeWidgets() {
      this.rangeMin = null; this.rangeMax = null; this.rangeInteger = false;
      this.lenMin = null; this.lenMax = null;
      this.regexPattern = ''; this.regexTestInput = '';
      this.enumValues = [];
    },
    parseParamsToWidgets(item) {
      this.resetTypeWidgets();
      if (!item.ruleParams) return;
      try {
        const p = JSON.parse(item.ruleParams);
        if (item.ruleType === 'RANGE') {
          this.rangeMin = p.min; this.rangeMax = p.max; this.rangeInteger = !!p.integer;
        } else if (item.ruleType === 'LENGTH') {
          this.lenMin = p.min; this.lenMax = p.max;
        } else if (item.ruleType === 'REGEX') {
          this.regexPattern = p.pattern || '';
        } else if (item.ruleType === 'ENUM') {
          this.enumValues = p.values || [];
        }
      } catch (_) { /* 무시 — 사용자 직접 편집 */ }
    },
    onTypeChange() {
      this.resetTypeWidgets();
      // 타입 변경 시 ruleParams 초기화
      if (this.form.ruleType === 'NOT_NULL') this.form.ruleParams = '{}';
    },
    syncRangeParams() {
      const p = {};
      if (this.rangeMin !== null && this.rangeMin !== '') p.min = Number(this.rangeMin);
      if (this.rangeMax !== null && this.rangeMax !== '') p.max = Number(this.rangeMax);
      if (this.rangeInteger) p.integer = true;
      this.form.ruleParams = JSON.stringify(p);
    },
    syncLenParams() {
      const p = {};
      if (this.lenMin !== null && this.lenMin !== '') p.min = Number(this.lenMin);
      if (this.lenMax !== null && this.lenMax !== '') p.max = Number(this.lenMax);
      this.form.ruleParams = JSON.stringify(p);
    },
    syncRegexParams() {
      this.form.ruleParams = JSON.stringify({ pattern: this.regexPattern });
    },
    syncEnumParams() {
      this.form.ruleParams = JSON.stringify({ values: this.enumValues });
    },
    saveRule() {
      if (!this.form.ruleNm) {
        this.$swal.fire({ icon: 'warning', title: '룰명 필수' }); return;
      }
      this.form.useYn = this.form.useYnBool ? 'Y' : 'N';
      var self = this;
      axios.post(this.$APIURL.base + 'api/qual/domain/rule/save', this.form)
        .then(r => {
          if (r.data.resultCode === 200) {
            self.$swal.fire({ icon: 'success', title: '저장 완료', timer: 1200, showConfirmButton: false });
            self.ruleDialog = false;
            self.loadRules();
            self.loadTree();   // 룰 카운트 갱신
          } else {
            self.$swal.fire({ icon: 'error', title: '실패', text: r.data.message });
          }
        });
    },
    deleteRule(item) {
      var self = this;
      this.$swal.fire({
        title: '룰 삭제?',
        text: item.ruleNm,
        showCancelButton: true,
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(self.$APIURL.base + 'api/qual/domain/rule/delete',
                   { domainRuleId: item.domainRuleId }).then(() => {
          self.loadRules();
          self.loadTree();
        });
      });
    },

    // ── 카탈로그 ──
    openCatalogDialog() {
      this.catalogDialog = true;
      this.catTab = 0;
      this.catSearch = '';
      this.loadCatalog();
    },
    openImportDialog() {
      // 도메인 선택 후 카탈로그 → 매핑 흐름
      this.openCatalogDialog();
    },
    loadCatalog() {
      var self = this;
      this.loadingCatalog = true;
      axios.get(this.$APIURL.base + 'api/qual/rule/catalog',
                { params: { schNm: this.catSearch || null } })
        .then(r => { self.catRows = r.data || []; })
        .finally(() => { self.loadingCatalog = false; });
    },
    mapToDomain(item) {
      if (!this.selectedDomain) {
        this.$swal.fire({ icon: 'warning', title: '먼저 좌측에서 도메인을 선택' });
        return;
      }
      var self = this;
      axios.post(this.$APIURL.base + 'api/qual/domain/rule/importFromCatalog', {
        domainId: this.selectedDomain.domainId,
        catalogId: item.catalogId,
      }).then(r => {
        if (r.data.resultCode === 200) {
          self.$swal.fire({ icon: 'success', title: '매핑 완료', timer: 1200, showConfirmButton: false });
          self.catalogDialog = false;
          self.loadRules();
          self.loadTree();
        } else {
          self.$swal.fire({ icon: 'error', title: '실패', text: r.data.message });
        }
      });
    },
    forkCatalog(item) {
      var self = this;
      axios.post(this.$APIURL.base + 'api/qual/rule/catalog/fork',
                 { srcCatalogId: item.catalogId }).then(r => {
        if (r.data.resultCode === 200) {
          self.$swal.fire({ icon: 'success', title: '복사 완료 — 사용자 정의 탭에서 편집',
                            timer: 1500, showConfirmButton: false });
          self.catTab = 1;
          self.loadCatalog();
        } else {
          self.$swal.fire({ icon: 'error', title: '실패', text: r.data.message });
        }
      });
    },
    deleteUserCatalog(item) {
      var self = this;
      this.$swal.fire({
        title: '사용자 정의 룰 삭제?',
        text: item.catalogNm,
        showCancelButton: true,
        confirmButtonText: '삭제',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(self.$APIURL.base + 'api/qual/rule/catalog/delete',
                   { catalogId: item.catalogId }).then(() => self.loadCatalog());
      });
    },

    // ── 헬퍼 ──
    typeColor(t) {
      switch (t) {
        case 'NOT_NULL': return 'red';
        case 'RANGE':    return 'blue';
        case 'LENGTH':   return 'cyan';
        case 'REGEX':    return 'purple';
        case 'ENUM':     return 'teal';
        case 'UNIQUE':   return 'orange';
        case 'REFERENCE':return 'brown';
        default:         return 'grey';
      }
    },
    summaryParams(p) {
      if (!p) return '';
      try {
        const o = JSON.parse(p);
        return Object.entries(o).map(([k, v]) => k + '=' + JSON.stringify(v)).join(', ');
      } catch (_) { return p.substring(0, 50); }
    }
  }
};
</script>

<style scoped>
:deep(.v-treeview-node__content) {
  cursor: pointer;
}
</style>
