<!--
  98번 표준사전 세트 — 사전 선택기.

  표준사전 화면(단어·용어·도메인·코드·도메인그룹·도메인분류)과 통합검색 상단에 붙는다.
  고른 사전은 세션스토리지에 남아 탭을 옮겨도 유지된다.

  사용:
    <DictSelector v-model="dictId" @change="조회함수" @meta="고른 사전 속성(dictTp·parentDictId)" />
-->
<template>
  <v-sheet class="d-flex align-center" :style="{ background: 'transparent' }">
    <v-icon small class="mr-1" color="ndColor">mdi-book-open-variant</v-icon>
    <span :style="{ fontSize: '.875rem', whiteSpace: 'nowrap' }">표준사전</span>
    <v-select class="pr-4 pl-2" v-model="selected" :items="dicts" item-text="dictNm" item-value="dictId"
      color="ndColor" single-line dense outlined hide-details :loading="loading"
      :style="{ width: '200px' }" @change="onChange">
      <template v-slot:item="{ item }">
        <span>{{ item.dictNm }}</span>
        <v-chip v-if="item.defaultYn === 'Y'" x-small class="ml-2">기본</v-chip>
        <v-chip v-if="item.dictTp === 'REF'" x-small class="ml-2" color="indigo" text-color="white">참조</v-chip>
      </template>
    </v-select>
  </v-sheet>
</template>

<script>
import axios from 'axios';

const STORAGE_KEY = 'navidmeta.dictId';

export default {
  name: 'DictSelector',
  props: {
    value: { type: String, default: null },
  },
  data() {
    return {
      dicts: [],
      selected: null,
      loading: false,
    };
  },
  created() {
    this.load();
  },
  methods: {
    load() {
      const self = this;
      self.loading = true;
      axios.post(self.$APIURL.base + 'api/dict/list', {})
        .then(function (res) {
          self.dicts = res.data || [];
          // 우선순위: 부모가 준 값 → 지난번에 고른 값 → 기본 사전 → 첫 번째
          let pick = self.value || self.readStored();
          if (!pick || !self.dicts.some(function (d) { return d.dictId === pick; })) {
            const def = self.dicts.find(function (d) { return d.defaultYn === 'Y'; });
            pick = def ? def.dictId : (self.dicts.length ? self.dicts[0].dictId : null);
          }
          self.selected = pick;
          self.emit(pick, false);
        })
        .catch(function (e) {
          // 사전 목록을 못 가져오면 선택기를 비워둔다. 화면은 기본 사전으로 동작한다.
          console.error('사전 목록 조회 실패', e);
          self.dicts = [];
        })
        .finally(function () { self.loading = false; });
    },
    onChange(v) {
      this.emit(v, true);
    },
    emit(v, fromUser) {
      this.writeStored(v);
      this.$emit('input', v);
      // 최초 로드에서도 부모가 조회를 걸 수 있게 change 를 쏜다.
      this.$emit('change', v, fromUser);
      // 104번 WP5-14·11 — 고른 사전의 속성 (참조 사전 여부·상위 사전)
      this.$emit('meta', this.dicts.find(function (d) { return d.dictId === v; }) || null);
    },
    readStored() {
      try { return window.sessionStorage.getItem(STORAGE_KEY); } catch (e) { return null; }
    },
    writeStored(v) {
      try {
        if (v) window.sessionStorage.setItem(STORAGE_KEY, v);
      } catch (e) { /* 사생활 보호 모드 등 — 저장 못 해도 동작에 지장 없음 */ }
    },
  },
};
</script>
