<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-server-network</v-icon>&nbsp;&nbsp;정보시스템·DB</h2>
        <v-spacer />
        <v-btn class="tb-btn" color="primary" outlined v-on:click="loadSys"><v-icon small left>mdi-refresh</v-icon>새로고침</v-btn>
      </v-row>

      <div class="gm-guide">
        시스템 트리는 [관리 > 시스템] 의 시스템입니다. 정보시스템명·유형은 IRM 등록 내용과 같게 넣습니다.
        DB 는 시스템에 묶인 데이터소스이며, DBMS 이름·버전은 데이터 모델을 수집할 때 채워집니다.
      </div>

      <v-row no-gutters style="gap:16px; flex-wrap:nowrap;">
        <!-- 시스템 목록 -->
        <div style="width:420px; min-width:320px;">
          <v-data-table id="govSysTable" :headers="sysHeaders" :items="sysItems" item-key="sysCd" dense
            :items-per-page="-1" hide-default-footer fixed-header :height="620" single-select
            v-on:click:row="selectSys" :item-class="r => (cur && r.sysCd === cur.sysCd) ? 'gm-sel' : ''">
            <template #item.sysNm="{ item }">
              <span :style="{ paddingLeft: ((item.level || 1) - 1) * 14 + 'px' }">
                <v-icon x-small>{{ item.sysTp === 0 ? 'mdi-folder-outline' : 'mdi-monitor' }}</v-icon> {{ item.sysNm }}
              </span>
            </template>
            <template #item.sendYn="{ item }">
              <v-chip v-if="item.sendYn === 'Y'" x-small color="primary" dark>전송</v-chip>
            </template>
            <template #no-data>시스템이 없습니다. [관리 > 시스템] 에서 먼저 등록해 주세요.</template>
          </v-data-table>
        </div>

        <!-- 시스템 항목 + DB -->
        <div style="flex:1; min-width:0;">
          <div v-if="!cur" class="gm-note mt-6">왼쪽에서 시스템을 고르세요.</div>
          <template v-else>
            <div class="gm-sub">정보시스템 — {{ cur.sysNm }}
              <span v-if="cur.sysTp === 0" class="gm-note">(시스템 그룹 — 생성 대상은 시스템 노드만입니다)</span>
            </div>
            <v-row dense>
              <v-col cols="12" md="6"><v-text-field id="govSysNm" v-model="form.govSysNm" label="정보시스템명 (IRM 등록명) *" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="6"><v-text-field id="govSysCd" v-model="form.govSysCd" label="정보시스템코드 (기관코드 7자리_일련번호 5자리)" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="4"><v-select v-model="form.govSysTp" :items="['개별', '단일', '표준']" label="정보시스템유형" dense outlined hide-details clearable :disabled="!canManage" /></v-col>
              <v-col cols="12" md="4"><v-select v-model="form.stdDstbTp" :items="['주관기관', '활용기관']" label="표준 구분" dense outlined hide-details clearable :disabled="!canManage || form.govSysTp !== '표준'" /></v-col>
              <v-col cols="12" md="4"><v-text-field id="govSysDept" v-model="form.operDeptNm" label="운영부서명 *" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="4"><v-text-field v-model="form.chrgUserNm" label="담당자명 (직급 포함)" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="4"><v-text-field v-model="form.chrgTelno" label="전화번호" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="4"><v-text-field v-model="form.chrgEmail" label="이메일" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12"><v-text-field v-model="form.rltLawCn" label="관련법령(보유목적)" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="3"><v-text-field v-model="form.bldYm" label="구축연월 (YYYYMM)" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="3"><v-text-field v-model="form.lastUpgrdYm" label="최근업데이트연월 (YYYYMM)" dense outlined hide-details :disabled="!canManage" /></v-col>
              <v-col cols="12" md="3"><v-checkbox id="govSysSendYn" v-model="sendYn" label="전송대상" dense hide-details class="mt-1" :disabled="!canManage" /></v-col>
              <v-col cols="12" md="3" class="d-flex align-center">
                <v-btn v-if="canManage" id="govSysSave" color="primary" small v-on:click="saveSys">저장</v-btn>
              </v-col>
            </v-row>
            <div class="gm-note mt-1">테이블 {{ cur.tblCnt }}개 · 전송대상 {{ cur.sendTblCnt }}개</div>

            <div class="gm-sub mt-4">데이터베이스</div>
            <div v-if="!dbs.length" class="gm-note">이 시스템에 묶인 데이터소스가 없습니다.</div>
            <v-expansion-panels v-else v-model="dbPanel" accordion>
              <v-expansion-panel v-for="d in dbs" :key="d.dsId">
                <v-expansion-panel-header>
                  <span><b>{{ d.dsn }}</b> <span class="gm-note">{{ d.dbmsTp }} · {{ d.dbName }}</span></span>
                </v-expansion-panel-header>
                <v-expansion-panel-content>
                  <v-row dense>
                    <v-col cols="12" md="6"><v-text-field v-model="d.dbNmKr" label="한글 DB명 *" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12" md="6"><v-text-field v-model="d.dbNmEn" :label="'영문 DB명 * (비우면 ' + (d.dbName || '-') + ')'" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12"><v-text-field v-model="d.dbDesc" label="DB 설명" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12" md="4"><v-combobox v-model="d.dbFormCd" :items="['정형', '비정형']" label="DB 형태 *" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12" md="8">
                      <v-autocomplete v-model="d.brmId" :items="brmOptions" item-text="brmPath" item-value="brmId"
                        label="업무분류체계 *" dense outlined hide-details clearable :disabled="!canManage" />
                    </v-col>
                    <v-col cols="12" md="6"><v-text-field :value="d.dbmsNm" label="DBMS 이름 (수집)" dense outlined hide-details readonly /></v-col>
                    <v-col cols="12" md="6"><v-text-field :value="d.dbmsVer" label="DBMS 버전 (수집)" dense outlined hide-details readonly /></v-col>
                    <v-col cols="12" md="6"><v-text-field v-model="d.osNm" label="운영체제 이름 *" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12" md="6"><v-text-field v-model="d.osVer" label="운영체제 버전 *" dense outlined hide-details :disabled="!canManage" /></v-col>
                    <v-col cols="12" md="8">
                      <v-select v-model="d.repDmId" :items="modelsOf(d.dsId)" item-text="dmNm" item-value="dmId" clearable
                        label="대표 모델 (이 DB 를 여러 모델이 쓸 때 테이블·컬럼을 가져올 모델)" dense outlined hide-details :disabled="!canManage" />
                    </v-col>
                    <v-col cols="12" md="4" class="d-flex align-center">
                      <v-btn v-if="canManage" color="primary" small v-on:click="saveDb(d)">DB 저장</v-btn>
                    </v-col>
                  </v-row>
                  <div class="gm-note mt-1">이 DB 를 쓰는 모델: {{ modelsOf(d.dsId).map(m => m.dmNm).join(', ') || '없음' }}
                    <span v-if="d.dbmsInfoClctDt"> · DBMS 정보 수집 {{ fmtDt(d.dbmsInfoClctDt) }}</span></div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
          </template>
        </div>
      </v-row>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
export default {
  name: 'DSGovMetaSys',
  props: ['isMobile'],
  computed: {
    canManage() { return this.$can('GOV_META_ADMIN'); },
  },
  data: () => ({
    sysItems: [],
    cur: null,
    form: {},
    sendYn: false,
    dbs: [],
    models: [],
    dbPanel: null,
    brmOptions: [],
    sysHeaders: [
      { text: '시스템', value: 'sysNm' },
      { text: '테이블', value: 'tblCnt', width: '60px', align: 'end' },
      { text: '전송대상', value: 'sendTblCnt', width: '70px', align: 'end' },
      { text: '', value: 'sendYn', width: '56px' },
    ],
  }),
  mounted() {
    this.loadSys();
    axios.post(this.$APIURL.base + 'api/govMeta/brm/list', { maxLvl: 4 }).then(res => {
      this.brmOptions = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : [];
    });
  },
  methods: {
    fmtDt(s) { return s && s.length >= 12 ? `${s.substr(0, 4)}-${s.substr(4, 2)}-${s.substr(6, 2)} ${s.substr(8, 2)}:${s.substr(10, 2)}` : s; },
    // 시스템 트리 순서·깊이는 sysinfo 목록(경로 순)을 따르고, 범정부 항목·건수는 govMeta 목록에서 붙인다
    loadSys() {
      Promise.all([
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList'),
        axios.get(this.$APIURL.base + 'api/govMeta/sys/list'),
      ]).then(([tree, gov]) => {
        const g = {};
        (gov.data.resultCode === 200 ? JSON.parse(gov.data.contents) : []).forEach(r => { g[r.sysCd] = r; });
        this.sysItems = (tree.data || []).map(t => Object.assign({}, g[t.sysCd] || {}, { sysCd: t.sysCd, sysNm: t.sysNm, sysTp: t.sysTp, level: t.level }));
        if (this.cur) {
          const again = this.sysItems.find(s => s.sysCd === this.cur.sysCd);
          if (again) this.selectSys(again);
        }
      });
    },
    selectSys(item) {
      this.cur = item;
      this.form = Object.assign({}, item);
      this.sendYn = item.sendYn === 'Y';
      this.dbPanel = null;
      axios.get(this.$APIURL.base + 'api/govMeta/db/list', { params: { sysCd: item.sysCd } }).then(res => {
        const d = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : { dbs: [], models: [] };
        this.dbs = d.dbs;
        this.models = d.models;
      });
    },
    modelsOf(dsId) { return this.models.filter(m => m.dsId === dsId); },
    result(res) {
      if (!res.data || res.data.resultCode !== 200) {
        this.$swal.fire({ title: (res.data && res.data.resultMessage) || '저장 실패', icon: 'error' });
        return false;
      }
      this.$swal.fire({ title: '저장했습니다', icon: 'success', timer: 1000, showConfirmButton: false });
      return true;
    },
    saveSys() {
      const body = Object.assign({}, this.form, { sysCd: this.cur.sysCd, sendYn: this.sendYn ? 'Y' : 'N' });
      axios.post(this.$APIURL.base + 'api/govMeta/sys/save', body).then(res => {
        if (this.result(res)) this.loadSys();
      });
    },
    saveDb(d) {
      axios.post(this.$APIURL.base + 'api/govMeta/db/save', d).then(res => {
        if (this.result(res)) this.selectSys(this.cur);
      });
    },
  },
};
</script>
<style scoped>
.gm-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.gm-sub { font-size: .9rem; font-weight: 600; color: #37474F; margin: 4px 0 8px; }
.gm-note { font-size: .78rem; color: #78909C; }
::v-deep .gm-sel { background: #E3F2FD; }
</style>
