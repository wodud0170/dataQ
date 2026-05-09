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
            <v-btn small outlined color="red" @click="bulkObjToggle('STND','N')" title="선택 행 표준진단 OFF">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkObjToggle('STND','Y')" title="선택 행 표준진단 ON">대상</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">구조</span>
            <v-btn small outlined color="red" @click="bulkObjToggle('STRUCT','N')" title="선택 행 구조진단 OFF">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkObjToggle('STRUCT','Y')" title="선택 행 구조진단 ON">대상</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">품질</span>
            <v-btn small outlined color="red" @click="bulkObjToggle('QUAL','N')" title="선택 행 품질진단 OFF">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkObjToggle('QUAL','Y')" title="선택 행 품질진단 ON">대상</v-btn>
          </v-sheet>
          <v-data-table class="px-3 pb-3" :headers="objHeaders" :items="filteredObjs" item-key="_rowKey"
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
            <v-btn small outlined color="red" @click="bulkAttrToggle('STND','N')" :disabled="!selObj">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkAttrToggle('STND','Y')" :disabled="!selObj">대상</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">구조</span>
            <v-btn small outlined color="red" @click="bulkAttrToggle('STRUCT','N')" :disabled="!selObj">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkAttrToggle('STRUCT','Y')" :disabled="!selObj">대상</v-btn>
            <v-divider vertical class="mx-1" />
            <span class="text-caption grey--text mr-1">품질</span>
            <v-btn small outlined color="red" @click="bulkAttrToggle('QUAL','N')" :disabled="!selObj">제외</v-btn>
            <v-btn small outlined color="green" @click="bulkAttrToggle('QUAL','Y')" :disabled="!selObj">대상</v-btn>
          </v-sheet>
          <v-data-table class="px-3 pb-3" :headers="attrHeaders" :items="filteredAttrs" item-key="_rowKey"
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
      // 86번 #11 — 소유자 추가 (같은 OBJ_NM 다른 OWNER 분리 표시)
      { text: '소유자', value: 'objOwner', width: '12%' },
      { text: '테이블명', value: 'objNm', width: '18%' },
      { text: '한글명', value: 'objNmKr', width: '15%' },
      { text: '컬럼수', value: 'objAttrCnt', width: '8%' },
      { text: '표준대상', value: 'stndDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '구조대상', value: 'structDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '품질대상', value: 'qualDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '상세', value: 'detail', width: '7%', sortable: false, align: 'center' },
    ],
    attrHeaders: [
      { text: '소유자', value: 'objOwner', width: '10%' },
      { text: '테이블', value: 'objNm', width: '14%' },
      { text: '컬럼명', value: 'attrNm', width: '16%' },
      { text: '한글명', value: 'attrNmKr', width: '16%' },
      { text: '타입', value: 'dataType', width: '10%' },
      { text: '표준대상', value: 'stndDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '구조대상', value: 'structDiagTargetYn', width: '10%', sortable: false, align: 'center' },
      { text: '품질대상', value: 'qualDiagTargetYn', width: '10%', sortable: false, align: 'center' },
    ],
    reasonDialog: false, reasonTitle: '', reasonText: '', _pendingApply: null,
    detailDialog: false, detailData: null,
  }),
  computed: {
    filteredObjs() {
      // 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → unique _rowKey 부여
      const q = (this.objSearch || '').toLowerCase();
      const list = q
        ? this.objs.filter(o => (o.objNm || '').toLowerCase().includes(q)
                             || (o.objNmKr || '').toLowerCase().includes(q))
        : this.objs;
      return list.map(o => ({ ...o, _rowKey: (o.objOwner || '') + '' + o.objNm }));
    },
    filteredAttrs() {
      const q = (this.attrSearch || '').toLowerCase();
      const list = q
        ? this.attrs.filter(a => (a.attrNm || '').toLowerCase().includes(q)
                             || (a.attrNmKr || '').toLowerCase().includes(q))
        : this.attrs;
      return list.map(a => ({ ...a, _rowKey: (a.objOwner || '') + '' + a.objNm + '' + a.attrNm }));
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
      this.selObj = null;
      this.loadAttrs();  // 86번 #11 — 컬럼 탭 자동 전체 로드 (사용자 검색 안 해도 보이게)
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
      // 86번 #11 — selObj 없으면 모델 전체 컬럼, 있으면 선택 테이블 컬럼만
      if (!this.dmId) { this.attrs = []; return; }
      axios.get(this.$APIURL.base + 'api/dm/getDataModelAttrListByClctId', { params: { clctId: this.dmId } })
        .then(r => {
          const all = r.data || [];
          this.attrs = this.selObj ? all.filter(a => a.objNm === this.selObj) : all;
        });
    },
    toggleObj(item, diagType) {
      // 86번 #11 — item 단위로 owner 명시 ((owner, objNm) tuple)
      const cur = this._getYn(item, diagType);
      const newYn = cur === 'Y' ? 'N' : 'Y';
      const target = { objOwner: item.objOwner || '', objNm: item.objNm };
      if (newYn === 'N') {
        this._openReason(`${item.objNm} 의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyObj([target], diagType, 'N', reason));
      } else {
        this._applyObj([target], diagType, 'Y', null);
      }
    },
    toggleAttr(item, diagType) {
      const cur = this._getYn(item, diagType);
      const newYn = cur === 'Y' ? 'N' : 'Y';
      // ATTR 은 selObj 가 항상 있을 때만 (UI 가 보장). owner 도 item 에서 가져옴.
      const target = { objOwner: item.objOwner || '', objNm: item.objNm, attrNm: item.attrNm };
      if (newYn === 'N') {
        this._openReason(`${item.attrNm} 의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyAttrTargets([target], diagType, 'N', reason));
      } else {
        this._applyAttrTargets([target], diagType, 'Y', null);
      }
    },
    bulkObjToggle(diagType, targetYn) {
      // 86번 #11 — selectedObjs 의 행에서 (owner, objNm) 추출
      const targets = (this.selectedObjs || []).map(o => ({ objOwner: o.objOwner || '', objNm: o.objNm }));
      if (!targets.length) { this.$swal.fire({ title: '행을 선택해주세요.', icon: 'info' }); return; }
      if (targetYn === 'N') {
        this._openReason(`${targets.length} 개 행의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyObj(targets, diagType, 'N', reason));
      } else {
        this._applyObj(targets, diagType, 'Y', null);
      }
    },
    bulkAttrToggle(diagType, targetYn) {
      // ATTR 일괄: 같은 (owner, objNm) 안의 attrNm 들만 묶음 처리. 다른 owner/obj 면 따로 호출.
      const sel = this.selectedAttrs || [];
      if (!sel.length) { this.$swal.fire({ title: '행을 선택해주세요.', icon: 'info' }); return; }
      const targets = sel.map(a => ({ objOwner: a.objOwner || '', objNm: a.objNm, attrNm: a.attrNm }));
      if (targetYn === 'N') {
        this._openReason(`${targets.length} 개 컬럼의 ${this._typeLabel(diagType)} 진단 OFF`,
          (reason) => this._applyAttrTargets(targets, diagType, 'N', reason));
      } else {
        this._applyAttrTargets(targets, diagType, 'Y', null);
      }
    },
    /** 86번 #11 — OBJ 단/일괄. targets = [{objOwner, objNm}, ...] */
    _applyObj(targets, diagType, targetYn, reason) {
      const url = this.$APIURL.base + (targets.length === 1 ? 'api/dm/setObjDiagTarget' : 'api/dm/setObjDiagTargetBatch');
      const body = targets.length === 1
        ? { dmId: this.dmId, objOwner: targets[0].objOwner, objNm: targets[0].objNm, diagType, targetYn, reason }
        : { dmId: this.dmId, targets, diagType, targetYn, reason };
      // 86번 #11 — 응답 검사 + 에러 노출 (이전엔 .then 만 — 백엔드 throw 면 silent fail → 사용자는 "안 바뀐다" 만 봄)
      axios.post(url, body).then(res => {
        const ok = res && res.data && (res.data.success === true || res.data.count > 0);
        if (!ok) {
          this.$swal.fire({ icon: 'warning', title: '변경 반영 0건',
            text: '서버 응답: ' + JSON.stringify(res.data), confirmButtonText: '확인' });
          return;
        }
        this.loadAll(); this.selectedObjs = [];
      }).catch(err => {
        const status = err.response && err.response.status;
        const data = err.response && err.response.data;
        const msg = (data && (data.message || data.resultMessage)) || err.message || '서버 오류';
        this.$swal.fire({
          icon: 'error',
          title: '진단 대상 변경 실패' + (status ? ` (${status})` : ''),
          text: msg,
          confirmButtonText: '확인'
        });
      });
    },
    /** 86번 #11 — ATTR 단/일괄. targets = [{objOwner, objNm, attrNm}, ...]. 같은 (owner, objNm) 끼리 그룹핑해서 batch 호출. */
    _applyAttrTargets(targets, diagType, targetYn, reason) {
      // (owner|objNm) 별로 그룹
      const groups = {};
      targets.forEach(t => {
        const k = (t.objOwner || '') + '' + t.objNm;
        if (!groups[k]) groups[k] = { objOwner: t.objOwner || '', objNm: t.objNm, attrNms: [] };
        groups[k].attrNms.push(t.attrNm);
      });
      const reqs = Object.values(groups).map(g => {
        const isSingle = g.attrNms.length === 1;
        const url = this.$APIURL.base + (isSingle ? 'api/dm/setAttrDiagTarget' : 'api/dm/setAttrDiagTargetBatch');
        const body = isSingle
          ? { dmId: this.dmId, objOwner: g.objOwner, objNm: g.objNm, attrNm: g.attrNms[0], diagType, targetYn, reason }
          : { dmId: this.dmId, objOwner: g.objOwner, objNm: g.objNm, attrNms: g.attrNms, diagType, targetYn, reason };
        return axios.post(url, body);
      });
      Promise.all(reqs).then(responses => {
        const allOk = responses.every(r => r && r.data && (r.data.success === true || r.data.count > 0));
        if (!allOk) {
          this.$swal.fire({ icon: 'warning', title: '일부 변경 반영 안 됨',
            text: '응답: ' + JSON.stringify(responses.map(r => r.data)), confirmButtonText: '확인' });
        }
        this.loadStats(); this.loadAttrs(); this.selectedAttrs = [];
      }).catch(err => {
        const status = err.response && err.response.status;
        const data = err.response && err.response.data;
        const msg = (data && (data.message || data.resultMessage)) || err.message || '서버 오류';
        this.$swal.fire({
          icon: 'error',
          title: '진단 대상 변경 실패' + (status ? ` (${status})` : ''),
          text: msg,
          confirmButtonText: '확인'
        });
      });
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
        { params: { dmId: this.dmId, objOwner: item.objOwner || '', objNm: item.objNm } })
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
