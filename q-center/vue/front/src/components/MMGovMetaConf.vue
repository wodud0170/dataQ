<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-cog-transfer-outline</v-icon>&nbsp;&nbsp;범정부 메타 설정</h2>
      </v-row>

      <v-tabs v-model="tab" color="ndColor" class="mb-3">
        <v-tab id="govConfTabInst">기관·연계</v-tab>
        <v-tab id="govConfTabBrm">업무분류체계(BRM)</v-tab>
        <v-tab id="govConfTabCode">코드 매핑</v-tab>
      </v-tabs>

      <v-tabs-items v-model="tab">
        <!-- 기관·연계 설정 -->
        <v-tab-item>
          <div class="gm-guide">
            기관 1곳 기준입니다. 연계 방식과 파일 경로는 저장만 하고, 파일 생성·전송은 연계 규격을 받은 뒤 붙습니다.
          </div>
          <v-row dense>
            <v-col cols="12" md="4"><v-text-field id="govConfInstNm" v-model="conf.instNm" label="기관명 *" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="4"><v-text-field id="govConfInstCd" v-model="conf.instCd" label="기관코드 (7자리)" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="4"><v-text-field v-model="conf.sendSysCd" label="송신시스템코드" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="4">
              <v-select v-model="conf.linkMthd" :items="linkMthdOptions" label="연계 방식" dense outlined hide-details clearable :disabled="!canManage" />
            </v-col>
            <v-col cols="12" md="4"><v-text-field v-model="conf.instMetaUserId" label="기관메타 사용자 ID" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="4">
              <v-autocomplete v-model="conf.dfltBrmId" :items="brmOptions" item-text="brmPath" item-value="brmId"
                label="기관 기본 업무분류체계" dense outlined hide-details clearable :disabled="!canManage" />
            </v-col>
          </v-row>
          <div class="gm-sub">SFTP 접속 (연계 방식이 SFTP 일 때)</div>
          <v-row dense>
            <v-col cols="12" md="4"><v-text-field v-model="conf.sftpHost" label="호스트" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="2"><v-text-field v-model="conf.sftpPort" label="포트" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="3"><v-text-field v-model="conf.sftpUserId" label="사용자" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="3">
              <v-text-field v-model="sftpPwd" type="password" :label="conf.sftpPwdSetYn === 'Y' ? '비밀번호 (저장됨 — 바꿀 때만 입력)' : '비밀번호'"
                dense outlined hide-details autocomplete="new-password" :disabled="!canManage" />
            </v-col>
            <v-col cols="12" md="6"><v-text-field v-model="conf.sftpDir" label="원격 경로" dense outlined hide-details :disabled="!canManage" /></v-col>
          </v-row>
          <div class="gm-sub">파일</div>
          <v-row dense>
            <v-col cols="12" md="6"><v-text-field v-model="conf.tmpFileDir" label="임시 파일 경로" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="6"><v-text-field v-model="conf.metaFileDir" label="메타 파일 경로" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="2"><v-text-field v-model="conf.fileKeepCnt" label="보관 개수" dense outlined hide-details :disabled="!canManage" /></v-col>
            <v-col cols="12" md="2"><v-select v-model="conf.fileFmt" :items="['XLSX', 'CSV']" label="형식" dense outlined hide-details clearable :disabled="!canManage" /></v-col>
            <v-col cols="12" md="2"><v-select v-model="conf.fileEncoding" :items="['UTF-8', 'EUC-KR']" label="인코딩" dense outlined hide-details clearable :disabled="!canManage" /></v-col>
          </v-row>
          <v-row v-if="canManage" no-gutters class="mt-3">
            <v-btn id="govConfSave" color="primary" v-on:click="saveConf">저장</v-btn>
            <span v-if="conf.updtDt" class="gm-note ml-3">마지막 저장 {{ fmtDt(conf.updtDt) }} {{ conf.updtUserId }}</span>
          </v-row>
        </v-tab-item>

        <!-- 업무분류체계 -->
        <v-tab-item>
          <div class="gm-guide">
            정부기능분류체계는 공공데이터포털 "행정안전부_정부기능별분류체계" CSV 를 반입합니다(분류체계ID·분류체계명·상위과제ID·분류체계단계·기관코드·분류체계경로 열).
            공공기관은 기관 자체 BRM 을 4단계까지 추가할 수 있습니다.
          </div>
          <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
            <v-text-field id="govBrmKeyword" v-model="brmKeyword" label="분류체계명·경로" dense outlined hide-details clearable style="max-width:240px;" v-on:keyup.enter="loadBrm" />
            <v-select v-model="brmSrc" :items="brmSrcOptions" label="출처" dense outlined hide-details style="max-width:160px;" />
            <v-btn small outlined color="primary" v-on:click="loadBrm">조회</v-btn>
            <v-spacer />
            <template v-if="canManage">
              <v-btn id="govBrmImport" small outlined v-on:click="$refs.brmFile.click()"><v-icon small left>mdi-file-upload</v-icon>CSV 반입</v-btn>
              <input ref="brmFile" type="file" accept=".csv" style="display:none" v-on:change="importBrm" />
              <v-btn id="govBrmAdd" small color="primary" v-on:click="openBrm(null)">기관 BRM 추가</v-btn>
            </template>
          </v-row>
          <v-data-table id="govBrmTable" :headers="brmHeaders" :items="brmItems" item-key="brmId" :items-per-page="30" dense
            fixed-header :height="460" :loading="brmLoading">
            <template #item.brmSrc="{ item }">
              <v-chip x-small :color="item.brmSrc === 'GOV' ? 'blue lighten-4' : 'green lighten-4'">{{ item.brmSrc === 'GOV' ? '정부' : '기관' }}</v-chip>
            </template>
            <template #item.actions="{ item }">
              <template v-if="canManage">
                <v-btn v-if="item.brmSrc === 'INST' && item.brmLvl < 4" x-small text v-on:click="openBrm(null, item)">하위 추가</v-btn>
                <v-btn v-if="item.brmSrc === 'INST'" x-small text v-on:click="openBrm(item)">수정</v-btn>
                <v-btn x-small text color="error" :disabled="item.childCnt > 0" v-on:click="deleteBrm(item)">삭제</v-btn>
              </template>
            </template>
          </v-data-table>
        </v-tab-item>

        <!-- 코드 매핑 -->
        <v-tab-item>
          <div class="gm-guide">
            우리 값(예: DBMS "Oracle", 공개/비공개여부 "공개")을 범정부 코드로 바꾸는 표입니다. 범정부 코드표를 받으면 여기에 넣습니다.
            생성 전 검증은 매핑이 없는 값을 오류로 알려 줍니다.
          </div>
          <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
            <v-select id="govCodeGrp" v-model="codeGrp" :items="codeGrpOptions" label="코드 그룹" dense outlined hide-details clearable
              style="max-width:220px;" v-on:change="loadCode" />
            <v-spacer />
            <v-btn v-if="canManage" id="govCodeAdd" small color="primary" v-on:click="openCode(null)">매핑 추가</v-btn>
          </v-row>
          <v-data-table id="govCodeTable" :headers="codeHeaders" :items="codeItems" item-key="rowKey" :items-per-page="30" dense
            fixed-header :height="460">
            <template #item.codeGrp="{ item }">{{ codeGrpNm(item.codeGrp) }}</template>
            <template #item.actions="{ item }">
              <template v-if="canManage">
                <v-btn x-small text v-on:click="openCode(item)">수정</v-btn>
                <v-btn x-small text color="error" v-on:click="deleteCode(item)">삭제</v-btn>
              </template>
            </template>
            <template #no-data>매핑이 없습니다. 범정부 코드표를 받으면 넣어 주세요.</template>
          </v-data-table>
        </v-tab-item>
      </v-tabs-items>
    </v-sheet>

    <!-- BRM 편집 -->
    <v-dialog v-model="brmDialog" max-width="520">
      <v-card>
        <v-card-title>{{ brmForm.brmId ? '기관 BRM 수정' : '기관 BRM 추가' }}</v-card-title>
        <v-card-text>
          <div v-if="brmForm.parentPath" class="gm-note mb-2">상위: {{ brmForm.parentPath }}</div>
          <v-text-field id="govBrmNm" v-model="brmForm.brmNm" label="분류체계명 *" dense outlined />
          <v-text-field v-model="brmForm.instCd" label="기관코드" dense outlined />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="brmDialog = false">취소</v-btn>
          <v-btn id="govBrmSave" color="primary" v-on:click="saveBrm">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 코드 매핑 편집 -->
    <v-dialog v-model="codeDialog" max-width="520">
      <v-card>
        <v-card-title>코드 매핑</v-card-title>
        <v-card-text>
          <v-select id="govCodeFormGrp" v-model="codeForm.codeGrp" :items="codeGrpOptions" label="코드 그룹 *" dense outlined :disabled="codeForm.isEdit" />
          <v-text-field id="govCodeFormLocal" v-model="codeForm.localVal" label="우리 값 *" dense outlined :disabled="codeForm.isEdit" />
          <v-text-field id="govCodeFormGov" v-model="codeForm.govCode" label="범정부 코드 *" dense outlined />
          <v-text-field v-model="codeForm.govCodeNm" label="범정부 코드명" dense outlined />
          <v-text-field v-model="codeForm.sortOrder" label="순서" dense outlined />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="codeDialog = false">취소</v-btn>
          <v-btn id="govCodeSave" color="primary" v-on:click="saveCode">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>
<script>
import axios from 'axios';
const CODE_GRP_NM = {
  DB_FORM: 'DB 형태', DBMS: 'DBMS 종류', DBMS_VER: 'DBMS 버전', OS: '운영체제 종류', OS_VER: '운영체제 버전',
  TBL_TP: '테이블유형', OCCR_CYCLE: '발생주기', OPEN_TP: '공개/비공개여부',
};
export default {
  name: 'MMGovMetaConf',
  props: ['isMobile'],
  computed: {
    canManage() { return this.$can('GOV_META_ADMIN'); },
    codeGrpOptions() { return Object.keys(CODE_GRP_NM).map(k => ({ text: CODE_GRP_NM[k], value: k })); },
  },
  data: () => ({
    tab: 0,
    conf: {},
    sftpPwd: '',
    linkMthdOptions: [
      { text: '기관메타 업로드', value: 'INST_META' },
      { text: '중앙메타', value: 'CENTRAL' },
      { text: 'SFTP', value: 'SFTP' },
    ],
    brmOptions: [],
    brmItems: [],
    brmLoading: false,
    brmKeyword: '',
    brmSrc: '',
    brmSrcOptions: [
      { text: '전체', value: '' },
      { text: '정부기능분류체계', value: 'GOV' },
      { text: '기관 자체', value: 'INST' },
    ],
    brmHeaders: [
      { text: '분류체계 ID', value: 'brmId', width: '170px' },
      { text: '분류체계명', value: 'brmNm' },
      { text: '단계', value: 'brmLvl', width: '70px' },
      { text: '경로', value: 'brmPath' },
      { text: '출처', value: 'brmSrc', width: '80px' },
      { text: '', value: 'actions', width: '190px', sortable: false },
    ],
    brmDialog: false,
    brmForm: {},
    codeGrp: null,
    codeItems: [],
    codeHeaders: [
      { text: '코드 그룹', value: 'codeGrp', width: '160px' },
      { text: '우리 값', value: 'localVal' },
      { text: '범정부 코드', value: 'govCode', width: '140px' },
      { text: '범정부 코드명', value: 'govCodeNm' },
      { text: '순서', value: 'sortOrder', width: '70px' },
      { text: '', value: 'actions', width: '120px', sortable: false },
    ],
    codeDialog: false,
    codeForm: {},
  }),
  mounted() {
    this.loadConf();
    this.loadBrm();
    this.loadCode();
  },
  methods: {
    parse(res) { return res.data && res.data.resultCode === 200 ? JSON.parse(res.data.contents) : null; },
    notify(res, okMsg) {
      if (!res.data || res.data.resultCode !== 200) {
        this.$swal.fire({ title: (res.data && res.data.resultMessage) || '실패했습니다', icon: 'error' });
        return false;
      }
      if (okMsg) this.$swal.fire({ title: okMsg, icon: 'success', timer: 1000, showConfirmButton: false });
      return true;
    },
    fmtDt(s) { return s && s.length >= 12 ? `${s.substr(0, 4)}-${s.substr(4, 2)}-${s.substr(6, 2)} ${s.substr(8, 2)}:${s.substr(10, 2)}` : s; },
    codeGrpNm(g) { return CODE_GRP_NM[g] || g; },
    loadConf() {
      axios.get(this.$APIURL.base + 'api/govMeta/conf').then(res => {
        const d = this.parse(res);
        this.conf = (d && d.conf) || {};
        this.sftpPwd = '';
      });
      axios.post(this.$APIURL.base + 'api/govMeta/brm/list', { maxLvl: 4 }).then(res => {
        this.brmOptions = this.parse(res) || [];
      });
    },
    saveConf() {
      const body = Object.assign({}, this.conf, { sftpPwd: this.sftpPwd || null });
      axios.post(this.$APIURL.base + 'api/govMeta/conf/save', body).then(res => {
        if (this.notify(res, '저장했습니다')) this.loadConf();
      });
    },
    loadBrm() {
      this.brmLoading = true;
      axios.post(this.$APIURL.base + 'api/govMeta/brm/list', { keyword: this.brmKeyword || null, brmSrc: this.brmSrc || null })
        .then(res => { this.brmItems = this.parse(res) || []; })
        .finally(() => { this.brmLoading = false; });
    },
    openBrm(item, parent) {
      this.brmForm = item
        ? { brmId: item.brmId, brmNm: item.brmNm, instCd: item.instCd, parentBrmId: item.parentBrmId, parentPath: null }
        : { brmId: null, brmNm: '', instCd: this.conf.instCd || '', parentBrmId: parent ? parent.brmId : null, parentPath: parent ? (parent.brmPath || parent.brmNm) : null };
      this.brmDialog = true;
    },
    saveBrm() {
      axios.post(this.$APIURL.base + 'api/govMeta/brm/save', this.brmForm).then(res => {
        if (this.notify(res, '저장했습니다')) { this.brmDialog = false; this.loadBrm(); this.loadConf(); }
      });
    },
    deleteBrm(item) {
      this.$swal.fire({ title: `'${item.brmNm}' 을 지울까요?`, showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/govMeta/brm/delete', { brmId: item.brmId }).then(res => {
          if (this.notify(res, '지웠습니다')) this.loadBrm();
        });
      });
    },
    importBrm(e) {
      const file = e.target.files[0];
      e.target.value = '';
      if (!file) return;
      const fd = new FormData();
      fd.append('file', file);
      this.brmLoading = true;
      axios.post(this.$APIURL.base + 'api/govMeta/brm/importCsv', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(res => {
          if (!this.notify(res)) return;
          const r = JSON.parse(res.data.contents);
          this.$swal.fire({ title: `${r.ok}건 반입했습니다`, text: r.skip ? `ID·이름이 빈 행 ${r.skip}건은 건너뛰었습니다.` : '', icon: 'success' });
          this.loadBrm();
          this.loadConf();
        })
        .finally(() => { this.brmLoading = false; });
    },
    loadCode() {
      axios.post(this.$APIURL.base + 'api/govMeta/codeMap/list', { codeGrp: this.codeGrp || null }).then(res => {
        this.codeItems = (this.parse(res) || []).map(r => Object.assign(r, { rowKey: r.codeGrp + '|' + r.localVal }));
      });
    },
    openCode(item) {
      this.codeForm = item ? Object.assign({ isEdit: true }, item) : { isEdit: false, codeGrp: this.codeGrp, localVal: '', govCode: '', govCodeNm: '', sortOrder: '' };
      this.codeDialog = true;
    },
    saveCode() {
      axios.post(this.$APIURL.base + 'api/govMeta/codeMap/save', this.codeForm).then(res => {
        if (this.notify(res, '저장했습니다')) { this.codeDialog = false; this.loadCode(); }
      });
    },
    deleteCode(item) {
      axios.post(this.$APIURL.base + 'api/govMeta/codeMap/delete', { codeGrp: item.codeGrp, localVal: item.localVal }).then(res => {
        if (this.notify(res, '지웠습니다')) this.loadCode();
      });
    },
  },
};
</script>
<style scoped>
.gm-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.gm-sub { font-size: .85rem; font-weight: 600; color: #37474F; margin: 14px 0 6px; }
.gm-note { font-size: .78rem; color: #78909C; }
</style>
