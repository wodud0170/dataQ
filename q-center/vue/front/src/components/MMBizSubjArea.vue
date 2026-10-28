<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <!-- 헤더 -->
      <v-row no-gutters align="center" class="mb-2">
        <h2><v-icon>mdi-folder-multiple-outline</v-icon>&nbsp;&nbsp;영역 관리</h2>
        <v-spacer />
        <v-btn small color="primary" outlined v-on:click="loadAll">
          <v-icon small left>mdi-refresh</v-icon>새로고침
        </v-btn>
      </v-row>

      <!-- 104번 WP8-38 — 업무영역 / 주제영역 탭 -->
      <v-tabs v-model="tab" height="36" color="ndColor" class="mb-3">
        <v-tab id="tabBiz">업무영역</v-tab>
        <v-tab id="tabSubj">주제영역</v-tab>
      </v-tabs>

      <!-- 업무영역 (논리) -->
      <div v-show="tab === 0">
        <v-row no-gutters align="center" class="mb-2">
          <span class="text-subtitle-1 font-weight-medium">업무영역 (논리)</span>
          <span class="text-caption grey--text ml-2">총 {{ bizList.length }}건</span>
          <v-spacer />
          <v-btn small class="btn-auto" color="success" depressed v-on:click="openBiz(null)">
            <v-icon small left>mdi-plus</v-icon>업무영역 추가
          </v-btn>
        </v-row>

        <v-data-table :headers="bizHeaders" :items="bizList" item-key="bizAreaId"
          :items-per-page="20" hide-default-footer dense class="elevation-1"
          no-data-text="등록된 업무영역이 없습니다.">
          <template #item.bizAreaDesc="{ item }">
            <span :class="{ 'grey--text': !item.bizAreaDesc }">{{ item.bizAreaDesc || '-' }}</span>
          </template>
          <template #item.actions="{ item }">
            <div class="d-flex justify-center" :style="{ gap: '4px' }">
              <v-btn icon small v-on:click="openBiz(item)" title="수정">
                <v-icon small color="ndColor">mdi-pencil</v-icon>
              </v-btn>
              <v-btn icon small v-on:click="delBiz(item)" title="삭제">
                <v-icon small color="error">mdi-delete</v-icon>
              </v-btn>
            </div>
          </template>
        </v-data-table>
      </div>

      <!-- 주제영역 — 3단계 트리 + 연결 테이블 -->
      <div v-show="tab === 1">
        <v-row no-gutters>
          <v-col cols="12" md="6" class="pr-md-3 mb-3">
            <v-row no-gutters align="center" class="mb-2">
              <span class="text-subtitle-1 font-weight-medium">주제영역</span>
              <span class="text-caption grey--text ml-2">총 {{ subjList.length }}건 · 3단계까지</span>
              <v-spacer />
              <v-btn id="subjAdd" small class="btn-auto" color="success" depressed v-on:click="openSubj(null)">
                <v-icon small left>mdi-plus</v-icon>주제영역 추가
              </v-btn>
            </v-row>
            <v-data-table id="subjTable" :headers="subjHeaders" :items="subjList" item-key="subjAreaId"
              :items-per-page="-1" hide-default-footer dense class="elevation-1" disable-sort
              :item-class="s => (selSubj && s.subjAreaId === selSubj.subjAreaId) ? 'row-sel' : ''"
              no-data-text="등록된 주제영역이 없습니다." @click:row="pickSubj">
              <template #item.subjAreaNm="{ item }">
                <div class="subj-nm" :style="{ paddingLeft: ((item.level - 1) * 18) + 'px' }">
                  <v-icon x-small v-if="item.level > 1" class="subj-arrow">mdi-subdirectory-arrow-right</v-icon>{{ item.subjAreaNm }}
                </div>
              </template>
              <template #item.actions="{ item }">
                <div class="d-flex justify-center" :style="{ gap: '4px' }">
                  <v-btn icon small v-on:click.stop="openSubj(item)" title="수정">
                    <v-icon small color="ndColor">mdi-pencil</v-icon>
                  </v-btn>
                  <v-btn icon small v-on:click.stop="delSubj(item)" title="삭제">
                    <v-icon small color="error">mdi-delete</v-icon>
                  </v-btn>
                </div>
              </template>
            </v-data-table>
          </v-col>
          <v-col cols="12" md="6">
            <v-row no-gutters align="center" class="mb-2">
              <span class="text-subtitle-1 font-weight-medium">연결된 테이블</span>
              <span v-if="selSubj" id="subjTablesTitle" class="text-caption grey--text ml-2">{{ selSubj.path }} · {{ subjTables.length }}건</span>
              <span v-else class="text-caption grey--text ml-2">왼쪽에서 주제영역을 고르세요</span>
              <v-spacer />
              <v-btn id="subjLinkOpen" small class="btn-auto" color="primary" depressed :disabled="!selSubj" v-on:click="openLink">
                <v-icon small left>mdi-link-plus</v-icon>테이블 연결
              </v-btn>
              <v-btn id="subjUnlink" small color="error" outlined class="ml-2 btn-auto" :disabled="!subjTableSel.length" v-on:click="unlink">
                <v-icon small left>mdi-link-off</v-icon>연결 해제 ({{ subjTableSel.length }})
              </v-btn>
            </v-row>
            <v-data-table id="subjTablesGrid" :headers="subjTableHeaders" :items="subjTables" :item-key="'_key'"
              v-model="subjTableSel" show-select :items-per-page="15" dense class="elevation-1"
              no-data-text="연결된 테이블이 없습니다." />
          </v-col>
        </v-row>
      </div>
    </v-sheet>

    <!-- 업무영역 다이얼로그 -->
    <v-dialog v-model="bizDialog" max-width="480" persistent>
      <v-card>
        <v-card-title class="text-subtitle-1">
          {{ bizForm.bizAreaId ? '업무영역 수정' : '업무영역 추가' }}
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <v-text-field v-model="bizForm.bizAreaNm" label="업무영역명 *" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-text-field v-model="bizForm.bizAreaDesc" label="설명" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-text-field v-model.number="bizForm.sortOrder" label="정렬 순서" type="number" dense outlined
            color="ndColor" hide-details="auto" />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="bizDialog = false">취소</v-btn>
          <v-btn color="ndColor" dark v-on:click="saveBiz">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 104번 WP8-38 — 주제영역 다이얼로그 -->
    <v-dialog v-model="subjDialog" max-width="520" persistent>
      <v-card>
        <v-card-title class="text-subtitle-1">
          {{ subjForm.subjAreaId ? '주제영역 수정' : '주제영역 추가' }}
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <v-text-field id="subjNm" v-model="subjForm.subjAreaNm" label="주제영역명 *" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-select id="subjParent" v-model="subjForm.parentId" :items="parentOptions" item-text="path" item-value="subjAreaId"
            label="상위 주제영역 (비우면 최상위)" dense outlined clearable color="ndColor"
            hint="옮긴 뒤 3단계를 넘지 않는 영역만 보입니다 (자기 자신·하위 영역 제외)." persistent-hint class="mb-3" />
          <v-text-field v-model="subjForm.subjAreaDesc" label="설명" dense outlined
            color="ndColor" hide-details="auto" class="mb-3" />
          <v-text-field v-model.number="subjForm.sortOrder" label="정렬 순서" type="number" dense outlined
            color="ndColor" hide-details="auto" />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="subjDialog = false">취소</v-btn>
          <v-btn id="subjSave" color="ndColor" dark v-on:click="saveSubj">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 104번 WP8-38 — 테이블 연결 다이얼로그 -->
    <v-dialog v-model="linkDialog" max-width="900" persistent scrollable>
      <v-card>
        <v-card-title class="text-subtitle-1">테이블 연결 — {{ selSubj ? selSubj.path : '' }}</v-card-title>
        <v-divider />
        <v-card-text class="pt-4" style="max-height:65vh;">
          <v-row no-gutters align="center" :style="{ gap: '8px' }" class="mb-2">
            <v-autocomplete id="linkModel" v-model="linkModelId" :items="modelList" item-text="dataModelNm" item-value="dataModelId"
              label="데이터 모델" dense outlined hide-details color="ndColor" :style="{ maxWidth: '280px' }" @change="loadModelTables" />
            <v-text-field id="linkKeyword" v-model="linkKeyword" label="테이블명 (영문·한글)" dense outlined hide-details clearable
              color="ndColor" :style="{ maxWidth: '240px' }" />
          </v-row>
          <v-data-table id="linkTablesGrid" :headers="linkHeaders" :items="linkTablesFiltered" item-key="_key"
            v-model="linkSel" show-select selectable-key="selectable" :items-per-page="10" dense class="elevation-1"
            :loading="linkLoading" no-data-text="데이터 모델을 고르세요.">
            <template #item.subjPath="{ item }">
              <v-chip v-if="!item.selectable" x-small color="indigo" text-color="white">이 영역</v-chip>
              <span v-else class="path-txt">{{ item.subjPath }}</span>
            </template>
          </v-data-table>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <span class="text-caption grey--text">이미 다른 주제영역에 연결된 테이블은 이 영역으로 옮겨집니다.</span>
          <v-spacer />
          <v-btn text v-on:click="linkDialog = false">취소</v-btn>
          <v-btn id="linkRun" class="btn-auto" color="ndColor" :dark="linkSel.length > 0" :disabled="!linkSel.length" v-on:click="runLink">{{ linkSel.length }}개 연결</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMBizSubjArea',
  props: ['isMobile'],
  data: () => ({
    tab: 0,
    bizList: [],
    bizHeaders: [
      { text: '업무영역명', value: 'bizAreaNm', align: 'start' },
      { text: '설명', value: 'bizAreaDesc', align: 'start' },
      { text: '정렬 순서', value: 'sortOrder', width: '120px', align: 'center' },
      { text: '관리', value: 'actions', width: '120px', sortable: false, align: 'center' },
    ],
    bizDialog: false,
    bizForm: { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 },
    // 104번 WP8-38 — 주제영역
    subjList: [],
    subjHeaders: [
      { text: '주제영역명', value: 'subjAreaNm', align: 'start' },
      { text: '하위', value: 'childCnt', width: '64px', align: 'center' },
      { text: '테이블', value: 'tableCnt', width: '72px', align: 'center' },
      { text: '순서', value: 'sortOrder', width: '64px', align: 'center' },
      { text: '관리', value: 'actions', width: '96px', align: 'center' },
    ],
    selSubj: null,
    subjTables: [],
    subjTableSel: [],
    subjTableHeaders: [
      { text: '데이터 모델', value: 'dmNm', align: 'start' },
      { text: '소유자', value: 'objOwner', align: 'start', width: '110px' },
      { text: '테이블 영문명', value: 'objNm', align: 'start' },
      { text: '테이블 한글명', value: 'objNmKr', align: 'start' },
    ],
    subjDialog: false,
    subjForm: { subjAreaId: null, subjAreaNm: '', subjAreaDesc: '', parentId: null, sortOrder: 0 },
    linkDialog: false,
    modelList: [],
    linkModelId: null,
    linkKeyword: '',
    linkTables: [],
    linkSel: [],
    linkLoading: false,
    linkHeaders: [
      { text: '소유자', value: 'objOwner', align: 'start', width: '110px' },
      { text: '테이블 영문명', value: 'objNm', align: 'start' },
      { text: '테이블 한글명', value: 'objNmKr', align: 'start' },
      { text: '지금 주제영역', value: 'subjPath', align: 'start' },
    ],
  }),
  computed: {
    // 상위 후보: 자기 자신·자기 하위를 빼고, 옮긴 뒤 자기 아래까지 3단계를 넘지 않는 영역 (서버와 같은 규칙)
    parentOptions() {
      const self = this.subjForm.subjAreaId;
      const desc = new Set();
      let height = 1;
      if (self) {
        const walk = (id, depth) => {
          height = Math.max(height, depth);
          this.subjList.filter(s => s.parentId === id).forEach(s => { desc.add(s.subjAreaId); walk(s.subjAreaId, depth + 1); });
        };
        walk(self, 1);
      }
      return this.subjList.filter(s => s.subjAreaId !== self && !desc.has(s.subjAreaId) && s.level + height <= 3);
    },
    linkTablesFiltered() {
      const k = (this.linkKeyword || '').toLowerCase();
      if (!k) return this.linkTables;
      return this.linkTables.filter(t => (t.objNm || '').toLowerCase().includes(k) || (t.objNmKr || '').toLowerCase().includes(k));
    },
  },
  mounted() { this.loadAll(); },
  activated() { this.loadAll(); },
  methods: {
    _msg(res, e) {
      return (res && res.data && res.data.resultMessage) || (e && e.response && e.response.data && e.response.data.resultMessage) || '';
    },
    loadAll() {
      axios.post(this.$APIURL.base + 'api/area/biz/list', {}).then(r => {
        // 응답이 배열일 때만 사용 — API 오류 시 fallback HTML 등 비배열이 들어오면 빈 목록 처리
        this.bizList = Array.isArray(r.data) ? r.data : [];
      }).catch(() => { this.bizList = []; });
      this.loadSubj();
    },
    openBiz(item) {
      this.bizForm = item
        ? { bizAreaId: item.bizAreaId, bizAreaNm: item.bizAreaNm, bizAreaDesc: item.bizAreaDesc || '', sortOrder: item.sortOrder || 0 }
        : { bizAreaId: null, bizAreaNm: '', bizAreaDesc: '', sortOrder: 0 };
      this.bizDialog = true;
    },
    saveBiz() {
      if (!this.bizForm.bizAreaNm) { this.$swal.fire({ title: '업무영역명 필수', icon: 'warning' }); return; }
      axios.post(this.$APIURL.base + 'api/area/biz/save', this.bizForm).then((res) => {
        if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '저장 실패', text: this._msg(res), icon: 'error' }); return; }
        this.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1000, showConfirmButton: false });
        this.bizDialog = false; this.loadAll();
      }).catch((e) => this.$swal.fire({ title: '저장 실패', text: this._msg(null, e), icon: 'error' }));
    },
    delBiz(item) {
      // 110 5.8-31-c — 취소 버튼 라벨, 5.8-11-c — 실패(resultCode) 안내 (예전에는 권한 없음도 조용히 넘어갔다)
      this.$swal.fire({ title: `'${item.bizAreaNm}' 삭제?`, icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/area/biz/delete', { bizAreaId: item.bizAreaId }).then((res) => {
          if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '삭제 실패', text: this._msg(res), icon: 'error' }); return; }
          this.loadAll();
        }).catch((e) => this.$swal.fire({ title: '삭제 실패', text: this._msg(null, e), icon: 'error' }));
      });
    },
    // ===== 104번 WP8-38 — 주제영역 =====
    loadSubj() {
      axios.post(this.$APIURL.base + 'api/area/subj/list', {}).then(r => {
        this.subjList = Array.isArray(r.data) ? r.data : [];
        if (this.selSubj) {
          this.selSubj = this.subjList.find(s => s.subjAreaId === this.selSubj.subjAreaId) || null;
          this.loadSubjTables();
        }
      }).catch(() => { this.subjList = []; });
    },
    pickSubj(item) {
      this.selSubj = item;
      this.loadSubjTables();
    },
    loadSubjTables() {
      this.subjTableSel = [];
      if (!this.selSubj) { this.subjTables = []; return; }
      axios.post(this.$APIURL.base + 'api/area/subj/tables', { subjAreaId: this.selSubj.subjAreaId }).then(r => {
        this.subjTables = (r.data || []).map(t => ({ ...t, _key: t.dmId + '|' + (t.objOwner || '') + '|' + t.objNm }));
      }).catch(() => { this.subjTables = []; });
    },
    openSubj(item) {
      this.subjForm = item
        ? { subjAreaId: item.subjAreaId, subjAreaNm: item.subjAreaNm, subjAreaDesc: item.subjAreaDesc || '', parentId: item.parentId || null, sortOrder: item.sortOrder || 0 }
        : { subjAreaId: null, subjAreaNm: '', subjAreaDesc: '', parentId: this.selSubj && this.selSubj.level < 3 ? this.selSubj.subjAreaId : null, sortOrder: 0 };
      this.subjDialog = true;
    },
    saveSubj() {
      if (!this.subjForm.subjAreaNm) { this.$swal.fire({ title: '주제영역명을 넣어 주세요.', icon: 'warning' }); return; }
      axios.post(this.$APIURL.base + 'api/area/subj/save', this.subjForm).then((res) => {
        if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '저장 실패', text: this._msg(res), icon: 'error' }); return; }
        this.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1000, showConfirmButton: false });
        this.subjDialog = false; this.loadSubj();
      }).catch((e) => this.$swal.fire({ title: '저장 실패', text: this._msg(null, e), icon: 'error' }));
    },
    delSubj(item) {
      this.$swal.fire({ title: `'${item.subjAreaNm}' 삭제?`, icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/area/subj/delete', { subjAreaId: item.subjAreaId }).then((res) => {
          if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '삭제 실패', text: this._msg(res), icon: 'error' }); return; }
          if (this.selSubj && this.selSubj.subjAreaId === item.subjAreaId) this.selSubj = null;
          this.loadSubj();
        });
      });
    },
    openLink() {
      this.linkSel = [];
      this.linkKeyword = '';
      if (!this.modelList.length) {
        axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', { schNm: null, schSysNm: null }).then(res => {
          this.modelList = (res.data || []).map(m => ({ dataModelId: m.dataModelId, dataModelNm: m.dataModelNm }));
        });
      }
      this.linkDialog = true;
    },
    loadModelTables() {
      this.linkSel = [];
      this.linkTables = [];
      if (!this.linkModelId) return;
      this.linkLoading = true;
      const pathOf = {};
      this.subjList.forEach(s => { pathOf[s.subjAreaId] = s.path; });
      axios.get(this.$APIURL.base + 'api/dm/getDataModelObjListByClctId', { params: { clctId: this.linkModelId } }).then(res => {
        this.linkTables = (res.data || []).map(t => ({
          dmId: this.linkModelId, objOwner: t.objOwner || '', objNm: t.objNm, objNmKr: t.objNmKr,
          subjPath: pathOf[t.subjAreaId] || '', _key: (t.objOwner || '') + '|' + t.objNm,
          selectable: t.subjAreaId !== this.selSubj.subjAreaId,   // 이미 이 영역에 연결된 테이블은 고를 수 없다
        }));
        this.linkLoading = false;
      }).catch(() => { this.linkLoading = false; });
    },
    _link(subjAreaId, tables, done) {
      axios.post(this.$APIURL.base + 'api/area/subj/link', {
        subjAreaId: subjAreaId,
        tables: tables.map(t => ({ dmId: t.dmId, objOwner: t.objOwner || '', objNm: t.objNm })),
      }).then(res => {
        if (!(res.data && res.data.resultCode === 200)) { this.$swal.fire({ title: '실패', text: this._msg(res), icon: 'error' }); return; }
        done(res.data.contents);
        this.loadSubj();
      }).catch((e) => this.$swal.fire({ title: '실패', text: this._msg(null, e), icon: 'error' }));
    },
    runLink() {
      this._link(this.selSubj.subjAreaId, this.linkSel, (n) => {
        this.linkDialog = false;
        this.$swal.fire({ title: `테이블 ${n}개를 연결했습니다.`, icon: 'success', timer: 1200, showConfirmButton: false });
      });
    },
    unlink() {
      const n = this.subjTableSel.length;
      this.$swal.fire({ title: `테이블 ${n}개의 연결을 해제할까요?`, icon: 'warning', showCancelButton: true, confirmButtonText: '해제', cancelButtonText: '취소' }).then(r => {
        if (!r.isConfirmed) return;
        this._link(null, this.subjTableSel, (cnt) => {
          this.$swal.fire({ title: `테이블 ${cnt}개의 연결을 해제했습니다.`, icon: 'success', timer: 1200, showConfirmButton: false });
        });
      });
    },
  },
};
</script>

<style>
/* 104번 WP8-38 — 표 안쪽 행은 자식 컴포넌트라 scoped 가 닿지 않는다 */
#subjTable .row-sel > td { background-color: #E8EAF6 !important; }
#subjTable tbody tr { cursor: pointer; }
#subjTable th, #linkTablesGrid th, #subjTablesGrid th { white-space: nowrap; }
#subjTable .subj-nm { word-break: keep-all; overflow-wrap: anywhere; }
#subjTable .subj-arrow { margin-right: 2px; vertical-align: baseline; }
#linkTablesGrid td, #subjTablesGrid td { word-break: keep-all; overflow-wrap: anywhere; }
</style>
