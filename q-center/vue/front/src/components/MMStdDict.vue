<!--
  98번 표준사전 세트 — 사전 관리 (관리자 전용)

  사전을 만들고, 사전마다 다른 표준화 관례를 켜고 끈다.
  사전은 지우지 않는다 — 사전이 사라지면 그 사전으로 잰 진단 이력을 해석할 수 없다.
  대신 '사용 중지' 로 목록에서 내린다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        표준사전은 단어·용어·도메인·코드의 이름공간입니다.
        기관마다 표준화 관례가 다르면 사전을 나누고, 데이터 모델마다 어느 사전을 쓸지 지정합니다.
      </span>
    </v-sheet>

    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <v-spacer />
      <v-btn small color="primary" @click="openAdd">사전 추가</v-btn>
      <v-btn small :disabled="!selected" @click="openEdit">수정</v-btn>
      <v-btn small color="error" :disabled="!selected || selected.defaultYn === 'Y'" @click="confirmDisable">사용 중지</v-btn>
    </v-sheet>

    <v-data-table :headers="headers" :items="dicts" dense hide-default-footer
      :items-per-page="-1" item-key="dictId" single-select show-select
      v-model="selectedRows" class="elevation-0" @item-selected="onSelect">
      <template v-slot:item.defaultYn="{ item }">
        <v-chip v-if="item.defaultYn === 'Y'" x-small color="primary" text-color="white">기본</v-chip>
      </template>
      <!-- 104번 WP5-14·11 -->
      <template v-slot:item.dictTp="{ item }">
        <v-chip v-if="item.dictTp === 'REF'" x-small color="indigo" text-color="white">참조</v-chip>
        <span v-else>기관</span>
        <span v-if="item.parentDictId" class="dict-opt-desc ml-1">· 상위 {{ dictNm(item.parentDictId) }}</span>
      </template>
      <template v-slot:item.termLastWordClsfYn="{ item }">
        <span>{{ item.termLastWordClsfYn === 'Y' ? '필수' : '미적용' }}</span>
      </template>
      <template v-slot:item.opts="{ item }">
        <span class="dict-opt-desc">
          대문자 {{ item.upperYn === 'N' ? '거부' : '자동' }}
          · 단어 영문명 중복 {{ item.wordEngNmDupYn === 'N' ? '불가' : '허용' }}
          · 용어 영문약어 {{ item.termEngMaxLen ? item.termEngMaxLen + ' Byte 이하' : '길이 제한 없음' }}
          · 구표준 {{ item.oldStdYn === 'Y' ? '사용' : '안 씀' }}
        </span>
      </template>
      <template v-slot:item.counts="{ item }">
        <span v-if="stats[item.dictId]">
          단어 {{ stats[item.dictId].wordCnt }} / 용어 {{ stats[item.dictId].termsCnt }}
          / 도메인 {{ stats[item.dictId].domainCnt }} / 모델 {{ stats[item.dictId].modelCnt }}
        </span>
        <span v-else class="grey--text">…</span>
      </template>
    </v-data-table>

    <!-- 등록 / 수정 -->
    <v-dialog max-width="600" v-model="modal" scrollable>
      <v-card>
        <v-card-title>{{ editing ? '표준사전 수정' : '표준사전 추가' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.dictNm" label="사전명" dense
            :rules="[() => !!form.dictNm || '사전명은 필수입니다.']" />
          <v-text-field v-model="form.dictDesc" label="설명" dense />
          <v-switch v-model="form.termLastWordClsfYn" true-value="Y" false-value="N" dense
            label="용어의 마지막 단어를 형식단어로 강제" />
          <div class="ml-8 mb-2" style="font-size:.75rem; color:#78909C;">
            켜면 '고객명' 처럼 형식단어(명·코드·번호·일자 등)로 끝나야 용어를 등록할 수 있습니다.
            행안부 공통표준의 관례이며, 기관 자체 표준에서는 대개 끕니다.
          </div>
          <!-- 104번 WP6-13 표준 운영 옵션 -->
          <div class="dict-opt-title">표준 운영 옵션</div>
          <v-switch id="dictUpperYn" v-model="form.upperYn" true-value="Y" false-value="N" dense hide-details
            label="표준 대문자 — 영문약어 소문자 입력을 대문자로 바꿔 받음" />
          <div class="ml-8 mb-2 dict-opt-desc">끄면 단어·용어 영문약어에 소문자가 있을 때 등록하지 않습니다.</div>
          <v-switch id="dictWordEngNmDupYn" v-model="form.wordEngNmDupYn" true-value="Y" false-value="N" dense hide-details
            label="단어 영문명 중복 허용" />
          <div class="ml-8 mb-2 dict-opt-desc">끄면 같은 영문명(대소문자 무시)을 쓰는 단어가 이 사전에 있을 때 등록·수정하지 않습니다.</div>
          <v-text-field id="dictTermEngMaxLen" v-model="form.termEngMaxLen" label="용어 영문약어명 최대 길이 (Byte, 비우면 제한 없음)"
            dense type="number" min="1" class="mt-2" />
          <v-switch id="dictOldStdYn" v-model="form.oldStdYn" true-value="Y" false-value="N" dense hide-details
            label="구표준 사용 — 옛 표준 → 새 표준 매핑" />
          <div class="ml-8 mb-2 dict-opt-desc">켜면 표준 사전 > 표준 매핑에 옛 표준을 등록하고, 표준 진단 결과가 옛 표준을 쓰는 컬럼에 새 표준을 안내합니다.</div>
          <!-- 104번 WP5-14·11 참조 사전·상위 사전 -->
          <div class="dict-opt-title">참조·상속</div>
          <v-switch id="dictRefTp" v-model="form.dictTp" true-value="REF" false-value="ORG" dense hide-details
            :disabled="form.defaultYn === 'Y'" label="참조 사전 (읽기 전용)" />
          <div class="ml-8 mb-2 dict-opt-desc">켜면 이 사전의 단어·용어·도메인·코드를 고칠 수 없고, 표준 사전 > 공통표준 가져오기의 원본으로 씁니다. 기본 사전은 참조 사전으로 둘 수 없습니다.</div>
          <v-select id="dictParent" v-model="form.parentDictId" :items="parentOptions" item-text="dictNm" item-value="dictId"
            label="상위 사전 (비우면 없음)" dense clearable class="mt-2" />
          <div class="ml-8 mb-2 dict-opt-desc">상위 사전을 두면 표준 진단·컬럼 표준 검사·자동완성이 상위 사전 용어도 쓰고(같은 영문약어는 이 사전이 우선), 단어·용어·도메인 목록에서 "상위 사전 포함" 으로 함께 볼 수 있습니다.</div>
          <v-switch v-model="form.defaultYn" true-value="Y" false-value="N" dense
            label="기본 사전으로 지정 (신규 모델에 미리 선택됨)" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="modal = false">취소</v-btn>
          <v-btn color="primary" :disabled="!form.dictNm" @click="save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'MMStdDict',
  props: ['isMobile'],
  data() {
    return {
      dicts: [],
      stats: {},
      selectedRows: [],
      modal: false,
      editing: false,
      form: { dictId: null, dictNm: '', dictDesc: '', termLastWordClsfYn: 'N', upperYn: 'Y', wordEngNmDupYn: 'Y', termEngMaxLen: null, oldStdYn: 'N', dictTp: 'ORG', parentDictId: null, defaultYn: 'N' },
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
      headers: [
        { text: '사전명', value: 'dictNm', width: '180px' },
        { text: '설명', value: 'dictDesc' },
        { text: '기본', value: 'defaultYn', width: '70px', align: 'center' },
        { text: '유형·상위 사전', value: 'dictTp', width: '170px', sortable: false },
        { text: '형식단어 강제', value: 'termLastWordClsfYn', width: '110px', align: 'center' },
        { text: '운영 옵션', value: 'opts', sortable: false },
        { text: '보유 건수', value: 'counts', width: '320px', sortable: false },
      ],
    };
  },
  computed: {
    selected() { return this.selectedRows.length ? this.selectedRows[0] : null; },
    // 104번 WP5-11 — 상위 사전 후보: 자기 자신 빼고
    parentOptions() { return this.dicts.filter(d => d.dictId !== this.form.dictId); },
  },
  mounted() { this.load(); },
  methods: {
    dictNm(id) {
      const d = this.dicts.find(x => x.dictId === id);
      return d ? d.dictNm : id;
    },
    load() {
      axios.post(this.$APIURL.base + 'api/dict/list', {}).then(res => {
        this.dicts = res.data || [];
        this.dicts.forEach(d => this.loadStats(d.dictId));
      }).catch(() => this.notify('사전 목록 조회 실패', 'error'));
    },
    loadStats(dictId) {
      axios.post(this.$APIURL.base + 'api/dict/stats', { dictId: dictId }).then(res => {
        this.$set(this.stats, dictId, res.data || {});
      }).catch(() => { /* 건수는 보조 정보라 실패해도 목록은 보여준다 */ });
    },
    onSelect() { /* v-model 로 처리 */ },
    openAdd() {
      this.editing = false;
      this.form = { dictId: null, dictNm: '', dictDesc: '', termLastWordClsfYn: 'N', upperYn: 'Y', wordEngNmDupYn: 'Y', termEngMaxLen: null, oldStdYn: 'N', dictTp: 'ORG', parentDictId: null, defaultYn: 'N' };
      this.modal = true;
    },
    openEdit() {
      if (!this.selected) return;
      this.editing = true;
      this.form = Object.assign({}, this.selected);
      this.modal = true;
    },
    save() {
      axios.post(this.$APIURL.base + 'api/dict/save', this.form).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.modal = false;
          this.selectedRows = [];
          this.load();
        } else {
          this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    confirmDisable() {
      if (!this.selected) return;
      const s = this.stats[this.selected.dictId] || {};
      const used = (s.modelCnt || 0);
      const msg = used > 0
        ? '이 사전을 쓰는 데이터 모델이 ' + used + '개 있습니다. 중지하면 그 모델들의 예약 진단이 막힙니다. 계속할까요?'
        : '사전을 사용 중지합니다. 데이터는 지워지지 않고 목록에서만 내려갑니다. 계속할까요?';
      this.$swal.fire({
        title: msg, icon: 'warning', showCancelButton: true,
        confirmButtonText: '중지', cancelButtonText: '취소',
      }).then(r => { if (r.isConfirmed) this.disable(); });
    },
    disable() {
      axios.post(this.$APIURL.base + 'api/dict/disable', { dictId: this.selected.dictId }).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('사용 중지했습니다.', 'success');
          this.selectedRows = [];
          this.load();
        } else {
          this.notify('중지 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>
<style scoped>
.dict-opt-title { font-size: .85rem; font-weight: 600; color: #37474F; margin: 12px 0 2px; }
.dict-opt-desc { font-size: .75rem; color: #78909C; }
</style>
