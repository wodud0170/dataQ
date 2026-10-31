<template>
  <v-main>
    <!-- 104번 WP7-23 (108 §4.29) — 시스템 단위 표준 후보 -->
    <v-sheet class="px-4 py-3">
      <v-row no-gutters align="center" class="mb-3">
        <h2><v-icon>mdi-format-list-checks</v-icon>&nbsp;&nbsp;시스템 단위 표준 후보</h2>
      </v-row>

      <div class="stc-guide">
        시스템(하위 시스템 포함)에 속한 데이터 모델의 비표준 컬럼(표준이 Y 가 아니고 한글명이 있는 컬럼, 표준 진단 제외 컬럼은 뺌)마다 그 모델의 표준사전(상위 사전 포함)에서 용어 후보를 찾습니다.
        컬럼마다 쓸 용어를 골라 [적용] 하면 컬럼 화면의 [저장]과 같이 변경 이력을 남기며, 영문명·타입·길이가 바뀌는 변경은 모델 직접 반영 권한이 없으면 데이터 모델 변경 승인을 거칩니다.
      </div>

      <!-- 조건 -->
      <v-row no-gutters align="center" style="gap:8px;" class="mb-2">
        <v-select id="sysTermCandSys" v-model="filter.sysCd" :items="sysOptions" item-text="text" item-value="value"
          label="시스템 (하위 포함)" dense outlined hide-details style="max-width:240px;" />
        <v-autocomplete id="sysTermCandModel" v-model="filter.dmIds" :items="modelOptions" item-text="dataModelNm" item-value="dataModelId"
          label="데이터 모델" multiple small-chips clearable dense outlined hide-details :disabled="!filter.sysCd"
          :menu-props="{ maxWidth: 520 }" class="stc-model" style="max-width:300px;">
          <!-- 칩은 하나만 — 여러 개면 칸이 두 줄로 커져 조건 줄이 어긋난다 -->
          <template #selection="{ item, index }">
            <v-chip v-if="index === 0" small class="stc-model-chip" :title="item.dataModelNm">{{ item.dataModelNm }}</v-chip>
            <span v-else-if="index === 1" class="stc-more">외 {{ filter.dmIds.length - 1 }}개</span>
          </template>
        </v-autocomplete>
        <v-text-field id="sysTermCandObj" v-model="filter.objNm" label="테이블" dense outlined hide-details clearable
          style="max-width:180px;" v-on:keyup.enter="onSearch" />
        <v-text-field id="sysTermCandAttr" v-model="filter.attrNm" label="컬럼" dense outlined hide-details clearable
          style="max-width:180px;" v-on:keyup.enter="onSearch" />
        <v-btn id="sysTermCandSearch" small outlined color="primary" class="btn-auto" :disabled="!filter.sysCd"
          :loading="loading" v-on:click="onSearch">조회</v-btn>
      </v-row>

      <!-- 모델 요약 -->
      <v-simple-table v-if="searched && models.length" id="sysTermCandModels" dense fixed-header
        :height="models.length > 3 ? 130 : undefined" class="stc-models mb-2">
        <thead>
          <tr>
            <th>시스템</th><th>데이터 모델</th><th>표준사전</th>
            <th class="text-center" style="width:130px;">표준 준수 필수</th>
            <th class="text-right" style="width:110px;">비표준 컬럼</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in models" :key="m.dmId">
            <td class="ko">{{ m.sysNm }}</td>
            <td class="ko">{{ m.dmNm }}</td>
            <td class="ko">{{ m.dictNm }}</td>
            <td class="text-center">
              <v-chip v-if="m.stndReqYn === 'Y'" x-small color="indigo" text-color="white">필수</v-chip>
              <span v-else>-</span>
            </td>
            <td class="text-right">{{ fmtNum(m.nonStdCnt) }}</td>
          </tr>
        </tbody>
      </v-simple-table>

      <!-- 건수·버튼 -->
      <v-row no-gutters align="center" style="gap:8px;" class="mb-1">
        <span v-if="searched" class="stc-count">비표준 컬럼 {{ fmtNum(total) }}개 · 후보 있음 {{ fmtNum(candCount) }}개 · 적용할 컬럼 {{ fmtNum(applyCount) }}개</span>
        <v-spacer />
        <v-btn id="btn-sysTermCand-pickFirst" small outlined color="primary" class="btn-auto" :disabled="!candCount" v-on:click="pickFirst">1순위로 모두 고르기</v-btn>
        <v-btn id="btn-sysTermCand-pickNone" small outlined color="primary" class="btn-auto" :disabled="!applyCount" v-on:click="pickNone">모두 적용 안 함</v-btn>
        <v-btn id="btn-sysTermCand-apply" small depressed color="primary" class="btn-auto" :disabled="!applyCount"
          :loading="applying" v-on:click="confirmApply">적용 ({{ fmtNum(applyCount) }})</v-btn>
      </v-row>
      <v-alert v-if="total > items.length" id="sysTermCandLimit" dense outlined type="warning" class="stc-limit mb-1">
        비표준 컬럼 {{ fmtNum(total) }}개 중 처음 {{ fmtNum(limit) }}개만 보입니다. 데이터 모델·테이블로 좁혀 조회하세요.
      </v-alert>

      <!-- 쪽당 50/100 행 — '전체'(2,000행)는 행마다 선택 칸이라 그리는 데 16초, 고를 때마다 6초 멈춰 뺐다 -->
      <div ref="tblWrap">
        <v-data-table id="sysTermCandTable" :headers="headers" :items="items" item-key="rowKey" :item-class="rowClass"
          :loading="loading" dense fixed-header :height="tableHeight" :items-per-page="50" :page.sync="page"
          :footer-props="{ 'items-per-page-options': [50, 100] }">
          <template #item.dmNm="{ item }">
            <div class="ko">{{ item.dmNm }}</div>
            <div class="sub ko">{{ item.dictNm }}</div>
          </template>
          <template #item.objNm="{ item }">
            <span class="stc-link en" title="컬럼 화면에서 보기" v-on:click="goColumn(item)">{{ item.objOwner ? item.objOwner + '.' : '' }}{{ item.objNm }}</span>
            <div v-if="item.objNmKr" class="sub ko">{{ item.objNmKr }}</div>
          </template>
          <template #item.attrNm="{ item }">
            <span class="en">{{ item.attrNm }}</span>
            <v-chip v-if="item.aprvStatus === 'DRAFT' || item.aprvStatus === 'SUBMITTED'" x-small outlined color="orange" class="ml-1">승인 대기</v-chip>
            <v-chip v-else-if="item.aprvStatus === 'REJECTED'" x-small outlined color="error" class="ml-1">반려</v-chip>
            <div class="sub ko">{{ item.attrNmKr }}</div>
          </template>
          <template #item.type="{ item }">
            <span class="stc-type">{{ fmtType(item.dataType, item.dataLen, item.dataDecimalLen) }}</span>
          </template>
          <template #item.pick="{ item }">
            <v-select v-if="item.candidates.length" v-model="item.pick" :items="candOptions(item)" item-text="text" item-value="value"
              dense outlined hide-details class="stc-cand my-1" :menu-props="{ maxWidth: 520 }">
              <template #selection="{ item: o }">
                <div class="v-select__selection v-select__selection--comma" :class="{ 'stc-none': o.value === null }" :title="o.text">{{ o.text }}</div>
              </template>
              <template #item="{ item: o }">
                <v-list-item-content>
                  <v-list-item-title :title="o.text" :class="{ 'stc-none': o.value === null }">{{ o.text }}</v-list-item-title>
                </v-list-item-content>
              </template>
            </v-select>
            <span v-else class="stc-none">후보 없음</span>
          </template>
          <template #item.after="{ item }">
            <template v-if="item.pick !== null">
              <div v-for="(a, i) in afterOf(item)" :key="i" class="stc-after">
                {{ a.label }} → <b v-if="a.bold" class="en">{{ a.val }}</b><span v-else :class="a.en ? 'en' : 'ko'">{{ a.val }}</span>
              </div>
              <span v-if="!afterOf(item).length" class="stc-none">그대로</span>
            </template>
          </template>
          <template #item.result="{ item }">
            <div v-if="overlaps[item.rowKey]" class="stc-err">같은 테이블에 영문명 '{{ overlaps[item.rowKey] }}' 가 겹칩니다</div>
            <div v-if="errMap[item.tblKey]" class="stc-err">{{ errMap[item.tblKey] }}</div>
          </template>
          <template #no-data>{{ emptyText }}</template>
        </v-data-table>
      </div>
    </v-sheet>
  </v-main>
</template>
<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
export default {
  name: 'DSSysTermCand',
  props: ['isMobile'],
  data: () => ({
    filter: { sysCd: null, dmIds: [], objNm: '', attrNm: '' },
    sysList: [],
    modelList: [],
    searched: false,
    lastCond: null,
    models: [],
    items: [],
    total: 0,
    limit: 2000,
    loading: false,
    applying: false,
    errMap: {},       // dmId|소유자|테이블 → 적용 실패 문구 (다음 [조회]까지)
    tableHeight: 400,
    page: 1,
    headers: [
      // 열 폭 고정(table-layout: fixed, 비율) — 용어 후보 칸이 남는 폭(약 25%)을 쓴다. 1366 폭에서 가로 스크롤 없이
      { text: '데이터 모델', value: 'dmNm', width: '12%', sortable: false },
      { text: '테이블', value: 'objNm', width: '14%', sortable: false },
      { text: '컬럼', value: 'attrNm', width: '14%', sortable: false },
      { text: '타입', value: 'type', width: '10%', sortable: false },
      { text: '용어 후보', value: 'pick', sortable: false },
      { text: '적용 뒤', value: 'after', width: '14%', sortable: false },
      { text: '결과', value: 'result', width: '11%', sortable: false },
    ],
  }),
  computed: {
    sysOptions() {
      return this.sysList.map(s => ({ text: '  '.repeat(Math.max(0, (s.level || 1) - 1)) + s.sysNm, value: s.sysCd }));
    },
    // 고른 시스템과 그 하위 시스템
    sysScope() {
      if (!this.filter.sysCd) return null;
      const set = new Set([this.filter.sysCd]);
      let grew = true;
      while (grew) {
        grew = false;
        this.sysList.forEach(s => {
          if (s.parentSysCd && set.has(s.parentSysCd) && !set.has(s.sysCd)) { set.add(s.sysCd); grew = true; }
        });
      }
      return set;
    },
    modelOptions() {
      const scope = this.sysScope;
      return scope ? this.modelList.filter(m => scope.has(m.dataModelSysCd)) : [];
    },
    modelById() {
      const m = {};
      this.models.forEach(x => { m[x.dmId] = x; });
      return m;
    },
    candCount() { return this.items.filter(i => i.candidates.length).length; },
    applyCount() { return this.items.filter(i => i.pick !== null).length; },
    // 영문명 겹침 — 같은 (모델, 소유자, 테이블)에서 적용 뒤 영문명(대소문자 무시)이 같은 행들. 고른 행이 하나라도 끼어야 겹침
    overlaps() {
      const byTbl = {};
      this.items.forEach(i => {
        const picked = i.pick !== null;
        const nm = (picked ? i.candidates[i.pick].termsEngAbrvNm : i.attrNm) || '';
        const t = byTbl[i.tblKey] || (byTbl[i.tblKey] = {});
        const g = t[nm.toUpperCase()] || (t[nm.toUpperCase()] = { nm, rows: [], picked: false });
        g.rows.push(i.rowKey);
        if (picked) g.picked = true;
      });
      const out = {};
      Object.keys(byTbl).forEach(k => Object.keys(byTbl[k]).forEach(u => {
        const g = byTbl[k][u];
        if (g.rows.length > 1 && g.picked) g.rows.forEach(r => { out[r] = g.nm; });
      }));
      return out;
    },
    emptyText() {
      if (!this.searched) return '시스템을 고르고 [조회] 하세요.';
      if (!this.models.length) return '이 시스템(하위 포함)에 데이터 모델이 없습니다.';
      return '비표준 컬럼이 없습니다.';
    },
  },
  watch: {
    'filter.sysCd'() { this.filter.dmIds = []; },
  },
  mounted() {
    axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList').then(res => { this.sysList = res.data || []; });
    axios.post(this.$APIURL.base + 'api/dm/getDataModelList', {}).then(res => { this.modelList = res.data || []; });
    this._resize = this.calcHeight.bind(this);
    window.addEventListener('resize', this._resize);
    this.$nextTick(this.calcHeight);
  },
  activated() {
    this.$nextTick(this.calcHeight);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this._resize);
  },
  methods: {
    // 목록이 창 아래 끝까지 (머리 고정 목록 + 아래 쪽수 줄 ~60px)
    calcHeight() {
      const el = this.$refs.tblWrap;
      if (!el || !el.offsetParent) return;
      // 화면이 세로로 스크롤된 채여도 같은 높이 — 스크롤 안 된 위치로 계산
      let top = el.getBoundingClientRect().top;
      for (let p = el.parentElement; p; p = p.parentElement) top += p.scrollTop;
      this.tableHeight = Math.max(200, Math.floor(window.innerHeight - top - 72));
    },
    fmtNum(n) { return Number(n || 0).toLocaleString(); },
    fmtType(t, len, dec) {
      if (!t) return '';
      if (len == null || Number(len) <= 0) return t;
      return t + '(' + len + (dec != null && Number(dec) > 0 ? ',' + dec : '') + ')';
    },
    candOptions(item) {
      return [{ text: '(적용 안 함)', value: null }].concat(item.candidates.map((t, i) => ({
        text: t.termsNm + ' (' + t.termsEngAbrvNm + ') — ' + t.reason + (t.inheritedDictNm ? ' · 상위 사전 ' + t.inheritedDictNm : ''),
        value: i,
      })));
    },
    // 고른 용어로 바뀌는 것 — 영문명(대소문자만 다르면 그대로)·타입(도메인 타입이 있을 때)·한글명
    afterOf(item) {
      const t = item.candidates[item.pick];
      const out = [];
      if ((t.termsEngAbrvNm || '').toUpperCase() !== (item.attrNm || '').toUpperCase()) out.push({ label: '영문명', val: t.termsEngAbrvNm, bold: true });
      if (t.dataType) {
        const to = this.fmtType(t.dataType, t.dataLen, t.dataDecimalLen);
        if (to !== this.fmtType(item.dataType, item.dataLen, item.dataDecimalLen)) out.push({ label: '타입', val: to, en: true });
      }
      if (t.termsNm !== item.attrNmKr) out.push({ label: '한글명', val: t.termsNm });
      return out;
    },
    rowClass(item) {
      return this.overlaps[item.rowKey] ? 'row-overlap' : '';
    },
    pickFirst() {
      this.items.forEach(i => { if (i.candidates.length) i.pick = 0; });
    },
    pickNone() {
      this.items.forEach(i => { i.pick = null; });
    },
    onSearch() {
      if (!this.filter.sysCd) return;
      this.errMap = {};
      this.lastCond = {
        sysCd: this.filter.sysCd, dmIds: this.filter.dmIds.slice(),
        objNm: (this.filter.objNm || '').trim() || null, attrNm: (this.filter.attrNm || '').trim() || null,
      };
      this.search();
    },
    search() {
      this.loading = true;
      axios.post(this.$APIURL.base + 'api/dm/suggestTermsBySys', this.lastCond).then(res => {
        if (!(res.data && res.data.resultCode === 200)) {
          this.clearResult();
          this.$swal.fire({ title: '조회 실패', text: res.data && res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          return;
        }
        const c = JSON.parse(res.data.contents);
        const dictNm = {};
        c.models.forEach(m => { dictNm[m.dmId] = m.dictNm; });
        this.models = c.models;
        this.total = c.total;
        this.limit = c.limit;
        this.items = c.items.map(i => Object.assign(i, {
          rowKey: [i.dmId, i.objOwner, i.objNm, i.attrNm].join('|'),
          tblKey: [i.dmId, i.objOwner, i.objNm].join('|'),
          dictNm: dictNm[i.dmId],
          pick: i.candidates.length ? 0 : null,
        }));
        this.searched = true;
        // 새 결과는 첫 쪽·맨 위부터
        this.page = 1;
        const w = this.$el.querySelector('#sysTermCandTable .v-data-table__wrapper');
        if (w) w.scrollTop = 0;
      }).catch(() => {
        this.clearResult();
        this.$swal.fire({ title: '조회 실패', icon: 'error', confirmButtonText: '확인' });
      }).finally(() => {
        this.loading = false;
        this.$nextTick(this.calcHeight);
      });
    },
    clearResult() {
      this.searched = false;
      this.models = [];
      this.items = [];
      this.total = 0;
    },
    tblLabel(dmNm, owner, objNm) {
      return dmNm + ' · ' + (owner ? owner + '.' : '') + objNm;
    },
    // 모델·테이블 이름과 서버 문구는 HTML 로 해석하지 않는다
    esc(s) {
      return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    },
    // 확인·결과 창 본문 — 왼쪽 정렬, 한글은 낱말 단위 줄바꿈
    box(html) {
      return '<div style="text-align:left;font-size:.9rem;word-break:keep-all;overflow-wrap:anywhere;">' + html + '</div>';
    },
    // 목록(20개까지 + 외 n개)은 창이 화면을 넘지 않게 세로 스크롤
    listBox(lines) {
      const shown = lines.slice(0, 20).map(s => '· ' + this.esc(s));
      if (lines.length > 20) shown.push('외 ' + this.fmtNum(lines.length - 20) + '개');
      return '<div style="max-height:40vh;overflow-y:auto;margin-top:6px;">' + shown.join('<br>') + '</div>';
    },
    confirmApply() {
      const picked = this.items.filter(i => i.pick !== null);
      // 영문명 겹침 — 보내지 않는다 (같은 테이블 안 겹침은 서버가 테이블째 거부한다)
      const ov = {};
      this.items.forEach(i => {
        const nm = this.overlaps[i.rowKey];
        if (nm) ov[i.tblKey + '|' + nm.toUpperCase()] = this.tblLabel(i.dmNm, i.objOwner, i.objNm) + ' — ' + nm;
      });
      const ovList = Object.keys(ov).map(k => ov[k]);
      if (ovList.length) {
        this.$swal.fire({
          title: '영문명 겹침', icon: 'warning', confirmButtonText: '확인', width: 600,
          html: this.box('같은 테이블에서 영문명이 겹치는 컬럼이 있습니다. 다른 용어를 고르거나 (적용 안 함)으로 바꾸세요.' + this.listBox(ovList)),
        });
        return;
      }
      const dms = new Set(picked.map(i => i.dmId));
      const tbls = new Set(picked.map(i => i.tblKey));
      const engCnt = picked.filter(i => (i.candidates[i.pick].termsEngAbrvNm || '').toUpperCase() !== (i.attrNm || '').toUpperCase()).length;
      const stndReq = [...dms].some(d => this.modelById[d] && this.modelById[d].stndReqYn === 'Y');
      const lines = ['모델 ' + dms.size + '개 · 테이블 ' + tbls.size + '개 · 컬럼 ' + this.fmtNum(picked.length) + '개에 적용합니다.'];
      if (engCnt) lines.push('영문명이 바뀌는 컬럼 ' + this.fmtNum(engCnt) + '개 — 같은 모델의 인덱스·제약조건·FK 참조 이름도 함께 바뀝니다.');
      if (!this.$can('DM_MANAGE')) lines.push('<b>영문명·타입·길이가 바뀌는 변경은 데이터 모델 변경 승인을 거쳐 확정됩니다 (내 변경 신청에서 볼 수 있습니다).</b>');
      if (stndReq) lines.push('표준 준수 필수 모델에서 표준 검사에 실패한 테이블은 적용되지 않습니다 (표준 예외는 컬럼 화면에서 신청).');
      this.$swal.fire({
        title: '시스템 단위 표준 후보 적용', icon: 'question', showCancelButton: true, confirmButtonText: '적용', cancelButtonText: '취소', width: 600,
        html: this.box(lines.map(l => '<div style="margin-bottom:6px;">' + l + '</div>').join('')),
      }).then(r => { if (r.isConfirmed) this.runApply(picked); });
    },
    runApply(picked) {
      const body = {
        items: picked.map(i => ({
          dmId: i.dmId, objOwner: i.objOwner, objNm: i.objNm, attrNm: i.attrNm, attrNmKr: i.attrNmKr, termsId: i.candidates[i.pick].termsId,
        })),
      };
      this.applying = true;
      axios.post(this.$APIURL.base + 'api/dm/applyTermsBySys', body).then(res => {
        if (!(res.data && res.data.resultCode === 200)) {
          this.$swal.fire({ title: '적용 실패', text: res.data && res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          return;
        }
        const c = JSON.parse(res.data.contents);
        const errs = c.errors || [];
        const map = {};
        errs.forEach(e => { map[[e.dmId, e.objOwner || '', e.objNm].join('|')] = e.message; });
        this.errMap = map;
        if (!errs.length) {
          this.$swal.fire({ title: this.fmtNum(c.applied) + '개 컬럼에 적용했습니다.', text: '테이블 ' + this.fmtNum(c.tables) + '개', icon: 'success', confirmButtonText: '확인' });
        } else {
          this.$swal.fire({
            title: '일부 적용 실패', icon: 'warning', confirmButtonText: '확인', width: 600,
            html: this.box('적용: <b>' + this.fmtNum(c.applied) + '개 컬럼</b> (테이블 ' + this.fmtNum(c.tables) + '개)<br><br>'
              + '<span style="color:#D32F2F">적용하지 못한 테이블 ' + this.fmtNum(errs.length) + '개</span>'
              + this.listBox(errs.map(e => this.tblLabel(e.dmNm, e.objOwner, e.objNm) + ' — ' + e.message))),
          });
        }
        this.search();
      }).catch(() => {
        this.$swal.fire({ title: '적용 실패', icon: 'error', confirmButtonText: '확인' });
      }).finally(() => { this.applying = false; });
    },
    // 테이블 이름 → 컬럼 화면에서 그 모델·테이블
    goColumn(item) {
      eventBus.pendingColumnView = { modelId: item.dmId, tableOwner: item.objOwner || '', tableNm: item.objNm };
      eventBus.$emit('openColumnView');
    },
  },
};
</script>
<style scoped>
.stc-guide { font-size: .8rem; color: #546E7A; background: #ECEFF1; border-radius: 4px; padding: 6px 10px; margin-bottom: 10px; word-break: keep-all; }
.stc-count { font-size: .85rem; color: #455A64; }
.stc-more { font-size: .8rem; color: #546E7A; margin-left: 4px; }
.stc-limit { font-size: .82rem; }
.stc-model-chip { max-width: 150px; }
/* 고른 모델 칩 뒤 입력 칸이 다음 줄로 내려가 칸이 두 줄로 커지지 않게 */
.stc-model >>> .v-select__selections { flex-wrap: nowrap; overflow: hidden; }
.stc-model >>> .v-select__selections input { min-width: 24px; }
.stc-model-chip >>> .v-chip__content { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
.stc-models { border: 1px solid #ECEFF1; }
.sub { font-size: .75rem; color: #78909C; }
/* 긴 이름 — 한글은 낱말 단위로, 낱말이 칸보다 길면 어디서나. 영문 이름은 어디서나 */
.ko { word-break: keep-all; overflow-wrap: anywhere; }
.en { word-break: break-all; }
.stc-link { color: #3949AB; cursor: pointer; }
.stc-link:hover { text-decoration: underline; }
.stc-none { color: #9E9E9E; }
.stc-type { font-size: .82rem; overflow-wrap: anywhere; }
.stc-after { font-size: .8rem; line-height: 1.35; }
.stc-err { font-size: .78rem; color: #D32F2F; word-break: keep-all; overflow-wrap: anywhere; }
.stc-cand >>> .v-select__selection--comma { font-size: .82rem; }
.stc-models >>> th { white-space: nowrap; }
#sysTermCandTable >>> table { table-layout: fixed; }
#sysTermCandTable >>> th, #sysTermCandTable >>> td { padding-left: 8px !important; padding-right: 8px !important; }
#sysTermCandTable >>> td { vertical-align: top; padding-top: 4px !important; padding-bottom: 4px !important; }
#sysTermCandTable >>> .row-overlap > td { background-color: #FFEBEE !important; }
</style>
