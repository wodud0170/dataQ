<template>
  <v-main>
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3" style="gap:8px;">
        <h2><v-icon>mdi-swap-horizontal</v-icon>&nbsp;&nbsp;표준 매핑</h2>
        <v-spacer />
        <DictSelector v-model="dictId" @change="load" />
      </v-row>

      <div class="sm-guide">
        옛 표준(AS-IS)의 단어·용어를 새 표준(TO-BE)에 잇습니다. 용어 매핑에 옛 영문약어명을 넣으면, 표준 진단 결과에서 그 영문명을 쓰는 컬럼에 새 용어를 안내합니다.
      </div>
      <v-alert v-if="oldStdYn !== 'Y'" id="stdMapOff" dense text type="warning" class="mb-2">
        이 사전은 구표준을 쓰지 않습니다. [관리 > 표준사전 관리] 에서 '구표준 사용' 을 켜면 매핑을 등록할 수 있습니다.
      </v-alert>

      <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
        <v-select v-model="mapTp" :items="mapTpFilter" label="유형" dense outlined hide-details style="max-width:130px;" />
        <v-text-field v-model="keyword" label="옛 이름·영문약어·새 표준" dense outlined hide-details clearable style="max-width:260px;" v-on:keyup.enter="load" />
        <v-btn small outlined color="primary" v-on:click="load">조회</v-btn>
        <v-spacer />
        <template v-if="canManage && oldStdYn === 'Y'">
          <v-btn id="stdMapAdd" small color="primary" v-on:click="openForm(null)">매핑 추가</v-btn>
          <v-btn small outlined color="error" :disabled="!selected.length" v-on:click="remove">삭제</v-btn>
        </template>
      </v-row>

      <v-data-table id="stdMapTable" v-model="selected" :headers="headers" :items="items" item-key="mapId" :show-select="canManage"
        dense :items-per-page="30" :loading="loading">
        <template #item.mapTp="{ item }">
          <v-chip x-small :color="item.mapTp === 'WORD' ? 'blue lighten-4' : 'green lighten-4'">{{ item.mapTp === 'WORD' ? '단어' : '용어' }}</v-chip>
        </template>
        <template #item.old="{ item }">
          <span>{{ item.oldNm }}</span><span v-if="item.oldEngAbrvNm" class="sm-sub"> ({{ item.oldEngAbrvNm }})</span>
        </template>
        <template #item.arrow><v-icon small>mdi-arrow-right</v-icon></template>
        <template #item.new="{ item }">
          <span>{{ item.newNm || '(지워진 표준)' }}</span><span v-if="item.newEngAbrvNm" class="sm-sub"> ({{ item.newEngAbrvNm }})</span>
          <v-chip v-if="item.newAprvYn === 'N'" x-small outlined color="orange" class="ml-1">승인 대기</v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn v-if="canManage && oldStdYn === 'Y'" x-small text v-on:click="openForm(item)">수정</v-btn>
        </template>
        <template #no-data>매핑이 없습니다.</template>
      </v-data-table>
    </v-sheet>

    <v-dialog v-model="formDialog" max-width="560">
      <v-card>
        <v-card-title>{{ form.mapId ? '표준 매핑 수정' : '표준 매핑 추가' }}</v-card-title>
        <v-card-text>
          <v-radio-group v-model="form.mapTp" row dense hide-details class="mt-0 mb-2" v-on:change="newOptions = []; form.newId = null">
            <v-radio label="용어" value="TERM" />
            <v-radio label="단어" value="WORD" />
          </v-radio-group>
          <v-text-field id="stdMapOldNm" v-model="form.oldNm" label="옛 표준 이름 *" dense outlined />
          <v-text-field id="stdMapOldEng" v-model="form.oldEngAbrvNm" label="옛 영문약어명 (용어면 진단 안내에 씀)" dense outlined />
          <v-autocomplete id="stdMapNew" v-model="form.newId" :items="newOptions" item-text="text" item-value="value" :search-input.sync="newSearch"
            :label="'새 표준 ' + (form.mapTp === 'WORD' ? '단어' : '용어') + ' * (이름으로 찾기)'" dense outlined no-filter hide-no-data />
          <v-text-field v-model="form.mapReason" label="바꾼 이유" dense outlined />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text v-on:click="formDialog = false">취소</v-btn>
          <v-btn id="stdMapSave" color="primary" v-on:click="save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>
<script>
import axios from 'axios';
import DictSelector from './DictSelector.vue';
export default {
  name: 'DSStdMap',
  components: { DictSelector },
  props: ['isMobile'],
  computed: {
    canManage() { return this.$can('STD_MANAGE'); },
  },
  data: () => ({
    dictId: null,
    oldStdYn: 'N',
    items: [],
    selected: [],
    loading: false,
    mapTp: '',
    keyword: '',
    mapTpFilter: [{ text: '전체', value: '' }, { text: '용어', value: 'TERM' }, { text: '단어', value: 'WORD' }],
    headers: [
      { text: '유형', value: 'mapTp', width: '70px' },
      { text: '옛 표준', value: 'old' },
      { text: '', value: 'arrow', width: '40px', sortable: false },
      { text: '새 표준', value: 'new' },
      { text: '바꾼 이유', value: 'mapReason' },
      { text: '등록', value: 'cretUserId', width: '100px' },
      { text: '', value: 'actions', width: '70px', sortable: false },
    ],
    formDialog: false,
    form: {},
    newOptions: [],
    newSearch: null,
    searchTimer: null,
  }),
  watch: {
    newSearch(v) {
      clearTimeout(this.searchTimer);
      if (!v || v.length < 1) return;
      this.searchTimer = setTimeout(() => this.searchNew(v), 300);
    },
  },
  methods: {
    load() {
      this.loading = true;
      this.selected = [];
      axios.post(this.$APIURL.base + 'api/stdMap/list', { dictId: this.dictId, mapTp: this.mapTp || null, keyword: this.keyword || null })
        .then(res => {
          const d = res.data.resultCode === 200 ? JSON.parse(res.data.contents) : { items: [], oldStdYn: 'N' };
          this.items = d.items;
          this.oldStdYn = d.oldStdYn;
        }).finally(() => { this.loading = false; });
    },
    // 새 표준 찾기 — 이름(이음동의어 포함)으로 사전에서 50건까지
    searchNew(v) {
      const isWord = this.form.mapTp === 'WORD';
      const url = this.$APIURL.base + (isWord ? 'api/std/getWordList' : 'api/std/getTermsListByCond');
      axios.post(url, { schNm: v, dictId: this.dictId }).then(res => {
        const seen = new Set();
        const list = (res.data || []).filter(x => { if (seen.has(x.id)) return false; seen.add(x.id); return true; }).slice(0, 50);
        const keep = this.newOptions.filter(o => o.value === this.form.newId);
        this.newOptions = keep.concat(list.filter(x => x.id !== this.form.newId).map(x => ({
          text: isWord ? `${x.wordNm} (${x.wordEngAbrvNm})` : `${x.termsNm} (${x.termsEngAbrvNm})`, value: x.id,
        })));
      });
    },
    openForm(item) {
      this.form = item
        ? { mapId: item.mapId, mapTp: item.mapTp, oldNm: item.oldNm, oldEngAbrvNm: item.oldEngAbrvNm, newId: item.newId, mapReason: item.mapReason }
        : { mapId: null, mapTp: 'TERM', oldNm: '', oldEngAbrvNm: '', newId: null, mapReason: '' };
      this.newOptions = item ? [{ text: `${item.newNm} (${item.newEngAbrvNm})`, value: item.newId }] : [];
      this.formDialog = true;
    },
    save() {
      axios.post(this.$APIURL.base + 'api/stdMap/save', Object.assign({ dictId: this.dictId }, this.form)).then(res => {
        if (res.data.resultCode !== 200) {
          this.$swal.fire({ title: res.data.resultMessage || '저장 실패', icon: 'error' });
          return;
        }
        this.$swal.fire({ title: '저장했습니다', icon: 'success', timer: 1000, showConfirmButton: false });
        this.formDialog = false;
        this.load();
      });
    },
    remove() {
      this.$swal.fire({ title: `${this.selected.length}건을 지울까요?`, showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/stdMap/delete', { mapIds: this.selected.map(x => x.mapId) }).then(res => {
          if (res.data.resultCode !== 200) {
            this.$swal.fire({ title: res.data.resultMessage || '삭제 실패', icon: 'error' });
            return;
          }
          this.load();
        });
      });
    },
  },
};
</script>
<style scoped>
.sm-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; }
.sm-sub { font-size: .75rem; color: #78909C; }
</style>
