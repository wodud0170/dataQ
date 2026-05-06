<template>
  <v-main>
    <v-card flat>
      <!-- 상단: 모델 선택 + 통계 (다른 화면 splitTopWrapper 패턴) -->
      <v-sheet class="splitTopWrapper pt-4 pb-4 px-3" :style="{ display:'flex', alignItems:'center', flexWrap:'wrap', gap:'12px' }">
        <span :style="{ fontSize: '.875rem' }">모델</span>
        <v-autocomplete v-model="dmId" :items="dataModels" item-text="dataModelNm" item-value="dataModelId"
          placeholder="진단 대상 모델 선택" color="ndColor" outlined dense hide-details
          :style="{ maxWidth: '320px' }" @change="loadAll"></v-autocomplete>
        <template v-if="stats">
          <v-divider vertical />
          <v-chip small label outlined>테이블 {{ stats.totalobj }}</v-chip>
          <v-chip small label color="error" outlined>표준 OFF {{ stats.objstndoff }}</v-chip>
          <v-chip small label color="error" outlined>구조 OFF {{ stats.objstructoff }}</v-chip>
          <v-chip small label color="error" outlined>품질 OFF {{ stats.objqualoff }}</v-chip>
          <v-divider vertical />
          <v-chip small label outlined>컬럼 {{ stats.totalattr }}</v-chip>
          <v-chip small label color="error" outlined>표준 OFF {{ stats.attrstndoff }}</v-chip>
          <v-chip small label color="error" outlined>구조 OFF {{ stats.attrstructoff }}</v-chip>
          <v-chip small label color="error" outlined>품질 OFF {{ stats.attrqualoff }}</v-chip>
        </template>
      </v-sheet>

      <!-- 탭 -->
      <v-tabs v-model="tab" class="px-3">
        <v-tab>테이블 단위</v-tab>
        <v-tab>컬럼 단위</v-tab>
      </v-tabs>

      <v-tabs-items v-model="tab">
        <!-- 테이블 단위 -->
        <v-tab-item>
          <v-sheet class="px-3 py-2" :style="{ display:'flex', alignItems:'center', flexWrap:'wrap', gap:'8px' }">
            <v-text-field v-model="objSearch" placeholder="테이블명 검색" dense hide-details outlined
              color="ndColor" prepend-inner-icon="search" :style="{ maxWidth: '240px' }"></v-text-field>
            <v-spacer></v-spacer>
            <span class="text-caption grey--text mr-1">표준</span>
            <v-btn small class="gradient" @click="bulkObjToggle('STND','N')">OFF</v-btn>
            <v-btn small outlined @click="bulkObjToggle('STND','Y')">ON</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">구조</span>
            <v-btn small class="gradient" @click="bulkObjToggle('STRUCT','N')">OFF</v-btn>
            <v-btn small outlined @click="bulkObjToggle('STRUCT','Y')">ON</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">품질</span>
            <v-btn small class="gradient" @click="bulkObjToggle('QUAL','N')">OFF</v-btn>
            <v-btn small outlined @click="bulkObjToggle('QUAL','Y')">ON</v-btn>
          </v-sheet>
          <v-data-table class="px-3 pb-3" :headers="objHeaders" :items="filteredObjs" item-key="objNm"
            v-model="selectedObjs" show-select dense hide-default-footer :items-per-page="-1">
            <template v-slot:[`item.stndDiagTargetYn`]="{ item }">
              <v-icon small :color="item.stndDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleObj(item, 'STND')" style="cursor:pointer">
                {{ item.stndDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
            <template v-slot:[`item.structDiagTargetYn`]="{ item }">
              <v-icon small :color="item.structDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleObj(item, 'STRUCT')" style="cursor:pointer">
                {{ item.structDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
            <template v-slot:[`item.qualDiagTargetYn`]="{ item }">
              <v-icon small :color="item.qualDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleObj(item, 'QUAL')" style="cursor:pointer">
                {{ item.qualDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
            <template v-slot:[`item.detail`]="{ item }">
              <v-btn icon small @click="openDetail(item)"><v-icon small>info_outline</v-icon></v-btn>
            </template>
          </v-data-table>
        </v-tab-item>

        <!-- 컬럼 단위 -->
        <v-tab-item>
          <v-sheet class="px-3 py-2" :style="{ display:'flex', alignItems:'center', flexWrap:'wrap', gap:'8px' }">
            <v-autocomplete v-model="selObj" :items="objs" item-text="objNm" item-value="objNm"
              placeholder="테이블 선택" color="ndColor" outlined dense hide-details
              :style="{ maxWidth: '260px' }" @change="loadAttrs"></v-autocomplete>
            <v-text-field v-model="attrSearch" placeholder="컬럼명 검색" dense hide-details outlined
              color="ndColor" prepend-inner-icon="search" :style="{ maxWidth: '240px' }"></v-text-field>
            <v-spacer></v-spacer>
            <span class="text-caption grey--text mr-1">표준</span>
            <v-btn small class="gradient" @click="bulkAttrToggle('STND','N')" :disabled="!selObj">OFF</v-btn>
            <v-btn small outlined @click="bulkAttrToggle('STND','Y')" :disabled="!selObj">ON</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">구조</span>
            <v-btn small class="gradient" @click="bulkAttrToggle('STRUCT','N')" :disabled="!selObj">OFF</v-btn>
            <v-btn small outlined @click="bulkAttrToggle('STRUCT','Y')" :disabled="!selObj">ON</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">품질</span>
            <v-btn small class="gradient" @click="bulkAttrToggle('QUAL','N')" :disabled="!selObj">OFF</v-btn>
            <v-btn small outlined @click="bulkAttrToggle('QUAL','Y')" :disabled="!selObj">ON</v-btn>
          </v-sheet>
          <v-data-table class="px-3 pb-3" :headers="attrHeaders" :items="filteredAttrs" item-key="attrNm"
            v-model="selectedAttrs" show-select dense hide-default-footer :items-per-page="-1">
            <template v-slot:[`item.stndDiagTargetYn`]="{ item }">
              <v-icon small :color="item.stndDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleAttr(item, 'STND')" style="cursor:pointer">
                {{ item.stndDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
            <template v-slot:[`item.structDiagTargetYn`]="{ item }">
              <v-icon small :color="item.structDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleAttr(item, 'STRUCT')" style="cursor:pointer">
                {{ item.structDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
            <template v-slot:[`item.qualDiagTargetYn`]="{ item }">
              <v-icon small :color="item.qualDiagTargetYn==='Y' ? 'green' : 'red'"
                @click="toggleAttr(item, 'QUAL')" style="cursor:pointer">
                {{ item.qualDiagTargetYn==='Y' ? 'check_box' : 'check_box_outline_blank' }}
              </v-icon>
            </template>
          </v-data-table>
        </v-tab-item>
      </v-tabs-items>
    </v-card>

    <!-- 사유 입력 모달 (OFF 시) -->
    <v-dialog v-model="reasonDialog" max-width="450">
      <v-card>
        <v-card-title class="text-subtitle-1">{{ reasonTitle }}</v-card-title>
        <v-card-text>
          <v-textarea v-model="reasonText" label="사유 (선택)" placeholder="빈칸 가능" rows="3" outlined hide-details />
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="reasonDialog=false">취소</v-btn>
          <v-btn color="primary" @click="confirmReason">적용</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 상세 Drawer -->
    <v-dialog v-model="detailDialog" max-width="500">
      <v-card v-if="detailData">
        <v-card-title>{{ detailData.objnm }} ({{ detailData.objnmkr }})</v-card-title>
        <v-card-text>
          <div class="mb-2"><strong>표준 진단 대상:</strong>
            <v-chip x-small :color="detailData.stnddiagtargetyn==='Y'?'green':'red'" dark>
              {{ detailData.stnddiagtargetyn==='Y' ? 'Y (대상)' : 'N (제외)' }}
            </v-chip>
            <div v-if="detailData.stnddiagtargetyn==='N'" class="text-caption">
              사유: {{ detailData.stnddiagtargetreason || '(없음)' }}
            </div>
          </div>
          <div class="mb-2"><strong>구조 변경 진단 대상:</strong>
            <v-chip x-small :color="detailData.structdiagtargetyn==='Y'?'green':'red'" dark>
              {{ detailData.structdiagtargetyn==='Y' ? 'Y (대상)' : 'N (제외)' }}
            </v-chip>
            <div v-if="detailData.structdiagtargetyn==='N'" class="text-caption">
              사유: {{ detailData.structdiagtargetreason || '(없음)' }}
            </div>
          </div>
          <div class="mb-2"><strong>품질 진단 대상:</strong>
            <v-chip x-small :color="detailData.qualdiagtargetyn==='Y'?'green':'red'" dark>
              {{ detailData.qualdiagtargetyn==='Y' ? 'Y (대상)' : 'N (제외)' }}
            </v-chip>
            <div v-if="detailData.qualdiagtargetyn==='N'" class="text-caption">
              사유: {{ detailData.qualdiagtargetreason || '(없음)' }}
            </div>
          </div>
          <v-divider class="my-2"></v-divider>
          <div class="text-caption">
            마지막 변경: {{ detailData.diagtargetupdtdt || '-' }} / {{ detailData.diagtargetupdtuserid || '-' }}
          </div>
        </v-card-text>
        <v-card-actions><v-spacer></v-spacer><v-btn text @click="detailDialog=false">닫기</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
export default {
  name: 'DSDiagTargetMgmt',
  data: () => ({
    dmId: null, dataModels: [], stats: null,
    tab: 0,
    objs: [], selectedObjs: [], objSearch: '',
    selObj: null, attrs: [], selectedAttrs: [], attrSearch: '',
    objHeaders: [
      { text: '테이블명', value: 'objNm', width: '20%' },
      { text: '한글명', value: 'objNmKr', width: '15%' },
      { text: '컬럼수', value: 'objAttrCnt', width: '8%' },
      { text: '표준대상', value: 'stndDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '구조대상', value: 'structDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '품질대상', value: 'qualDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '상세', value: 'detail', width: '7%', sortable: false, align: 'center' },
    ],
    attrHeaders: [
      { text: '컬럼명', value: 'attrNm', width: '20%' },
      { text: '한글명', value: 'attrNmKr', width: '20%' },
      { text: '타입', value: 'dataType', width: '12%' },
      { text: '표준대상', value: 'stndDiagTargetYn', width: '12%', sortable: false, align: 'center' },
      { text: '구조대상', value: 'structDiagTargetYn', width: '12%', sortable: false, align: 'center' },
      { text: '품질대상', value: 'qualDiagTargetYn', width: '12%', sortable: false, align: 'center' },
    ],
    reasonDialog: false, reasonTitle: '', reasonText: '', _pendingApply: null,
    detailDialog: false, detailData: null,
  }),
  computed: {
    filteredObjs() {
      const q = (this.objSearch || '').toLowerCase();
      if (!q) return this.objs;
      return this.objs.filter(o => (o.objNm || '').toLowerCase().includes(q)
                                || (o.objNmKr || '').toLowerCase().includes(q));
    },
    filteredAttrs() {
      const q = (this.attrSearch || '').toLowerCase();
      if (!q) return this.attrs;
      return this.attrs.filter(a => (a.attrNm || '').toLowerCase().includes(q)
                                || (a.attrNmKr || '').toLowerCase().includes(q));
    }
  },
  mounted() {
    // 진단 제외 관리는 모델 메타데이터 설정이라 DB 연결 여부 무관 — connectedOnly 제거.
    // 진단 대상은 물리 변환된 모델만 (LOGICAL 은 OBJ/ATTR 메타가 다른 의미라 제외)
    axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {})
      .then(r => { this.dataModels = (r.data || []).filter(m => m.modelType === 'PHYSICAL'); });
  },
  methods: {
    loadAll() {
      if (!this.dmId) return;
      this.loadStats();
      this.loadObjs();
      this.selObj = null; this.attrs = [];
    },
    loadStats() {
      axios.get(this.$APIURL.base + 'api/dm/diagTargetStats', { params: { dmId: this.dmId } })
        .then(r => { this.stats = r.data; });
    },
    loadObjs() {
      axios.get(this.$APIURL.base + 'api/dm/getDataModelObjListByClctId', { params: { clctId: this.dmId } })
        .then(r => { this.objs = r.data || []; });
    },
    loadAttrs() {
      if (!this.selObj) { this.attrs = []; return; }
      axios.get(this.$APIURL.base + 'api/dm/getDataModelAttrListByClctId', { params: { clctId: this.dmId } })
        .then(r => { this.attrs = (r.data || []).filter(a => a.objNm === this.selObj); });
    },
    toggleObj(item, diagType) {
      const cur = this._getYn(item, diagType);
      const newYn = cur === 'Y' ? 'N' : 'Y';
      if (newYn === 'N') {
        this._openReason(`${item.objNm} 의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyObj([item.objNm], diagType, 'N', reason));
      } else {
        this._applyObj([item.objNm], diagType, 'Y', null);
      }
    },
    toggleAttr(item, diagType) {
      const cur = this._getYn(item, diagType);
      const newYn = cur === 'Y' ? 'N' : 'Y';
      if (newYn === 'N') {
        this._openReason(`${item.attrNm} 의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyAttr([item.attrNm], diagType, 'N', reason));
      } else {
        this._applyAttr([item.attrNm], diagType, 'Y', null);
      }
    },
    bulkObjToggle(diagType, targetYn) {
      const objNms = (this.selectedObjs || []).map(o => o.objNm);
      if (!objNms.length) { this.$swal.fire({ title: '행을 선택해주세요.', icon: 'info' }); return; }
      if (targetYn === 'N') {
        this._openReason(`${objNms.length} 개 행의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyObj(objNms, diagType, 'N', reason));
      } else {
        this._applyObj(objNms, diagType, 'Y', null);
      }
    },
    bulkAttrToggle(diagType, targetYn) {
      const attrNms = (this.selectedAttrs || []).map(a => a.attrNm);
      if (!attrNms.length) { this.$swal.fire({ title: '행을 선택해주세요.', icon: 'info' }); return; }
      if (targetYn === 'N') {
        this._openReason(`${attrNms.length} 개 컬럼의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyAttr(attrNms, diagType, 'N', reason));
      } else {
        this._applyAttr(attrNms, diagType, 'Y', null);
      }
    },
    _applyObj(objNms, diagType, targetYn, reason) {
      const url = this.$APIURL.base + (objNms.length === 1 ? 'api/dm/setObjDiagTarget' : 'api/dm/setObjDiagTargetBatch');
      const body = objNms.length === 1
        ? { dmId: this.dmId, objNm: objNms[0], diagType, targetYn, reason }
        : { dmId: this.dmId, objNms, diagType, targetYn, reason };
      axios.post(url, body).then(() => { this.loadAll(); this.selectedObjs = []; });
    },
    _applyAttr(attrNms, diagType, targetYn, reason) {
      const url = this.$APIURL.base + (attrNms.length === 1 ? 'api/dm/setAttrDiagTarget' : 'api/dm/setAttrDiagTargetBatch');
      const body = attrNms.length === 1
        ? { dmId: this.dmId, objNm: this.selObj, attrNm: attrNms[0], diagType, targetYn, reason }
        : { dmId: this.dmId, objNm: this.selObj, attrNms, diagType, targetYn, reason };
      axios.post(url, body).then(() => { this.loadStats(); this.loadAttrs(); this.selectedAttrs = []; });
    },
    _openReason(title, callback) {
      this.reasonTitle = title;
      this.reasonText = '';
      this._pendingApply = callback;
      this.reasonDialog = true;
    },
    confirmReason() {
      this.reasonDialog = false;
      if (this._pendingApply) {
        const r = this.reasonText && this.reasonText.trim() ? this.reasonText.trim() : null;
        this._pendingApply(r);
        this._pendingApply = null;
      }
    },
    openDetail(item) {
      axios.get(this.$APIURL.base + 'api/dm/objDiagTargetDetail',
        { params: { dmId: this.dmId, objNm: item.objNm } })
        .then(r => { this.detailData = r.data || {}; this.detailDialog = true; });
    },
    _getYn(item, diagType) {
      if (diagType === 'STND') return item.stndDiagTargetYn;
      if (diagType === 'STRUCT') return item.structDiagTargetYn;
      return item.qualDiagTargetYn;
    },
    _typeLabel(t) { return { STND:'표준', STRUCT:'구조', QUAL:'품질' }[t] || t; }
  }
};
</script>
