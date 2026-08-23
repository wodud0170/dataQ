<template>
  <v-main>
    <!-- 컨트롤 바 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '8px' }">
        <span class="filterLabel">데이터모델명</span>
        <v-autocomplete v-model="selectedModelId" :items="modelList"
          item-text="dataModelNm" item-value="dataModelId"
          @change="onModelChange" clearable dense outlined hide-details
          class="filterInput" :style="{ width: '260px' }" color="ndColor" placeholder="모델 선택">
        </v-autocomplete>

        <span class="filterLabel">레이아웃</span>
        <v-btn-toggle v-model="layoutMode" mandatory dense @change="onLayoutChange">
          <v-btn small value="hierarchical" title="계층 (LR)">
            <v-icon small left>mdi-file-tree</v-icon>계층
          </v-btn>
          <v-btn small value="physics" title="자율 배치 (드래그 정렬)">
            <v-icon small left>mdi-graph</v-icon>자율 배치
          </v-btn>
        </v-btn-toggle>

        <span class="filterLabel">표시</span>
        <v-btn-toggle v-model="nameMode" mandatory dense @change="onNameModeChange">
          <v-btn small value="both" title="영문 + 한글">전체</v-btn>
          <v-btn small value="en" title="영문명만">영문</v-btn>
          <v-btn small value="ko" title="한글명만">한글</v-btn>
        </v-btn-toggle>

        <span class="filterLabel">테이블 검색</span>
        <v-text-field v-model="searchTable" clearable clear-icon="mdi-close-circle"
          color="ndColor" single-line dense outlined hide-details
          class="filterInput" :style="{ width: '160px' }"
          placeholder="물리/논리명" @input="onSearchInput" :disabled="!selectedModelId" />

        <v-btn small class="gradient" @click="fitView" :disabled="!selectedModelId" title="전체 보기">
          <v-icon small left>mdi-fit-to-page-outline</v-icon>맞춤
        </v-btn>
        <v-btn small class="gradient" @click="exportPng" :disabled="!selectedModelId">
          <v-icon small left>mdi-image-outline</v-icon>PNG
        </v-btn>
        <v-btn small class="gradient" @click="openPdfDialog" :disabled="!selectedModelId" :loading="exportingPdf">
          <v-icon small left>mdi-file-pdf-box</v-icon>PDF
        </v-btn>

        <v-spacer />
        <span v-if="stats" :style="{ fontSize: '.85rem', color: '#455A64' }">
          테이블 <b>{{ stats.tableCnt }}</b> · 컬럼 <b>{{ stats.attrCnt }}</b> · 관계(FK) <b>{{ stats.fkCnt }}</b>
        </span>
      </v-row>
    </v-sheet>

    <!-- 캔버스 -->
    <div class="erd-wrap">
      <div ref="networkContainer" class="erd-canvas"></div>

      <!-- 빈 상태 -->
      <div v-if="!selectedModelId && !loading" class="erd-empty">
        <v-icon size="72" color="grey lighten-1">mdi-graph-outline</v-icon>
        <p class="mt-2">데이터 모델을 선택하면 ERD가 자동 생성됩니다.</p>
      </div>
      <!-- 모델은 선택했지만 데이터 없음 -->
      <div v-if="selectedModelId && !loading && stats && stats.tableCnt === 0" class="erd-empty">
        <v-icon size="72" color="grey lighten-1">mdi-table-off</v-icon>
        <p class="mt-2">선택한 모델에 테이블이 없습니다.</p>
      </div>
      <!-- 로딩 -->
      <div v-if="loading" class="erd-loading">
        <v-progress-circular indeterminate color="indigo darken-2" size="48" />
        <p class="mt-3">ERD 생성 중...</p>
      </div>

      <!-- 범례 -->
      <div v-if="selectedModelId && !loading" class="erd-legend">
        <div class="legend-row"><span class="legend-marker pk">[PK]</span>기본키</div>
        <div class="legend-row"><span class="legend-marker fk">[FK]</span>외래키</div>
        <div class="legend-row"><span class="legend-arrow">→</span>참조관계</div>
      </div>
    </div>

    <!-- PDF 옵션 다이얼로그 -->
    <v-dialog v-model="pdfDialog" max-width="420" persistent>
      <v-card>
        <v-card-title>PDF 내보내기 옵션</v-card-title>
        <v-card-text>
          <v-radio-group v-model="pdfFormat" hide-details class="mt-0">
            <v-radio label="A3 가로 (권장)" value="a3"></v-radio>
            <v-radio label="A4 가로" value="a4"></v-radio>
          </v-radio-group>
          <v-checkbox v-model="pdfHighRes" label="고해상도 (2배 스케일) — 파일 크기 증가" hide-details class="mt-3"></v-checkbox>
          <p class="caption grey--text mt-3 mb-0">
            현재 보이는 그대로 한 페이지에 맞춰 저장됩니다.
            노드가 잘리지 않게 먼저 [맞춤] 버튼을 눌러주세요.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="pdfDialog = false">취소</v-btn>
          <v-btn color="primary" :loading="exportingPdf" @click="exportPdf">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
import { Network, DataSet } from 'vis-network/standalone';
import { jsPDF } from 'jspdf';

export default {
  name: 'DSDatamodelVisualization',
  props: { isMobile: Boolean },
  data() {
    return {
      modelList: [],
      selectedModelId: null,
      selectedModelNm: '',
      loading: false,

      layoutMode: 'hierarchical',
      nameMode: 'both',
      searchTable: '',

      // raw
      objs: [],
      attrsByTable: {},
      fkEdges: [],
      stats: null,

      // vis-network
      network: null,
      nodesDataSet: null,
      edgesDataSet: null,

      // 줌 아웃 자동 단순화 (테이블명만 표시)
      simplifiedMode: false,
      simplifyThreshold: 0.55,

      // export
      pdfDialog: false,
      pdfFormat: 'a3',
      pdfHighRes: false,
      exportingPdf: false,

      // 검색 디바운스
      searchTimer: null,
    };
  },
  mounted() {
    this.loadModelList();
  },
  activated() {
    if (this.selectedModelId && this.network) {
      this.$nextTick(() => { try { this.network.redraw(); this.network.fit(); } catch (e) {} });
    }
  },
  beforeDestroy() {
    if (this.network) { try { this.network.destroy(); } catch (e) {} this.network = null; }
  },
  methods: {
    loadModelList() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {})
        .then(r => {
          const list = (r.data || []).filter(m => (m.objCnt || 0) > 0);
          this.modelList = list;
        })
        .catch(e => { console.error('모델 목록 조회 실패', e); this.$swal('모델 목록 조회 실패', '', 'error'); });
    },
    onModelChange(modelId) {
      if (!modelId) {
        this.clearNetwork();
        this.selectedModelNm = '';
        this.stats = null;
        return;
      }
      const m = this.modelList.find(x => x.dataModelId === modelId);
      this.selectedModelNm = m ? m.dataModelNm : modelId;
      this.loadModelData(modelId);
    },
    async loadModelData(modelId) {
      this.loading = true;
      try {
        const [objR, attrR, consR] = await Promise.all([
          axios.get(this.$APIURL.base + 'api/dm/getDataModelObjListByClctId', { params: { clctId: modelId } }),
          axios.get(this.$APIURL.base + 'api/dm/getDataModelAttrListByClctId', { params: { clctId: modelId } }),
          axios.get(this.$APIURL.base + 'api/dm/getDataModelConstraintListByDmId', { params: { dataModelId: modelId } }).catch(() => ({ data: [] })),
        ]);
        const objs = objR.data || [];
        const attrs = attrR.data || [];
        const cons = consR.data || [];

        // 컬럼을 테이블 단위로 그룹핑 (key = owner|objNm)
        const attrsByTable = {};
        for (const a of attrs) {
          const k = (a.objOwner || '') + '|' + a.objNm;
          if (!attrsByTable[k]) attrsByTable[k] = [];
          attrsByTable[k].push(a);
        }
        for (const k in attrsByTable) {
          attrsByTable[k].sort((x, y) => (x.attrOrder || 0) - (y.attrOrder || 0));
        }

        // FK 엣지 추출 (CONSTRAINT 우선, 없으면 ATTR.FK_PARENT_*)
        const objKeySet = new Set(objs.map(o => (o.objOwner || '') + '|' + o.objNm));
        const fkEdges = [];
        const seen = new Set();

        // 1) 정식 CONSTRAINT (type='R' 또는 'F')
        const fkCons = cons.filter(c => {
          const t = (c.constraintType || '').toUpperCase();
          return t === 'R' || t === 'F' || t === 'FOREIGN KEY';
        });
        // 같은 constraintNm 으로 그룹핑하여 컬럼셋 합치기 (복합키 표시용)
        const consGroups = {};
        for (const c of fkCons) {
          const k = c.constraintNm + '|' + c.objOwner + '|' + c.tableNm;
          if (!consGroups[k]) consGroups[k] = { from: null, to: null, cols: [], refCols: [] };
          consGroups[k].to = (c.objOwner || '') + '|' + c.tableNm;
          consGroups[k].from = (c.refOwner || '') + '|' + c.refTableNm;
          consGroups[k].cols.push(c.columnNm);
          consGroups[k].refCols.push(c.refColumnNm);
        }
        for (const k in consGroups) {
          const g = consGroups[k];
          if (!g.from || !g.to) continue;
          if (!objKeySet.has(g.from) || !objKeySet.has(g.to)) continue;
          const eKey = g.from + '->' + g.to + ':' + g.cols.join(',');
          if (seen.has(eKey)) continue;
          seen.add(eKey);
          fkEdges.push({ from: g.from, to: g.to, label: g.cols.join(','), source: 'constraint' });
        }

        // 2) ATTR.FK_PARENT_* 폴백 (CONSTRAINT 로 이미 잡힌건 skip)
        for (const a of attrs) {
          if (a.fkYn !== 'Y' || !a.fkParentObjNm) continue;
          const toKey = (a.objOwner || '') + '|' + a.objNm;
          // FK_PARENT_OBJ_NM 에는 owner 가 없다. 같은 이름이 여러 스키마에 있으면
          // 자식과 같은 스키마를 먼저 고른다 — 아무거나 집으면 다른 스키마로 선이 그어진다.
          const parent = objs.find(o => o.objNm === a.fkParentObjNm
                                     && (o.objOwner || '') === (a.objOwner || ''))
                      || objs.find(o => o.objNm === a.fkParentObjNm);
          if (!parent) continue;
          const fromKey = (parent.objOwner || '') + '|' + parent.objNm;
          if (!objKeySet.has(fromKey) || !objKeySet.has(toKey)) continue;
          const eKey = fromKey + '->' + toKey + ':' + a.attrNm;
          if (seen.has(eKey)) continue;
          // 같은 from-to 쌍이 CONSTRAINT 로 이미 잡혔으면 중복 방지 (컬럼명만 다른 케이스)
          const dupPair = Array.from(seen).some(s => s.startsWith(fromKey + '->' + toKey + ':') && s.includes(a.attrNm));
          if (dupPair) continue;
          seen.add(eKey);
          fkEdges.push({ from: fromKey, to: toKey, label: a.attrNm, source: 'attr' });
        }

        this.objs = objs;
        this.attrsByTable = attrsByTable;
        this.fkEdges = fkEdges;
        this.stats = { tableCnt: objs.length, attrCnt: attrs.length, fkCnt: fkEdges.length };

        this.$nextTick(() => this.buildNetwork());
      } catch (e) {
        console.error('모델 데이터 로드 실패', e);
        this.$swal('모델 데이터 로드 실패', e.message || '', 'error');
      } finally {
        this.loading = false;
      }
    },
    clearNetwork() {
      if (this.network) { try { this.network.destroy(); } catch (e) {} this.network = null; }
      this.nodesDataSet = null;
      this.edgesDataSet = null;
      this.simplifiedMode = false;
      this.objs = []; this.attrsByTable = {}; this.fkEdges = [];
    },
    buildNetwork() {
      const container = this.$refs.networkContainer;
      if (!container) return;
      if (this.network) { try { this.network.destroy(); } catch (e) {} this.network = null; }
      this.simplifiedMode = false;

      const nodes = this.objs.map(o => this.buildNodeOptions(o, false));
      const edges = this.fkEdges.map((e, i) => ({
        id: 'e' + i,
        from: e.from,
        to: e.to,
        label: e.label,
        arrows: { to: { enabled: true, scaleFactor: 0.8 } },
        color: { color: '#5C6BC0', highlight: '#1A237E' },
        smooth: { type: 'cubicBezier', forceDirection: this.layoutMode === 'hierarchical' ? 'horizontal' : 'none', roundness: 0.4 },
        font: { size: 10, color: '#37474F', background: 'rgba(255,255,255,0.85)', strokeWidth: 0, align: 'middle' },
        width: 1.2,
        dashes: e.source === 'attr',
      }));

      this.nodesDataSet = new DataSet(nodes);
      this.edgesDataSet = new DataSet(edges);

      const options = this.networkOptions();
      this.network = new Network(container, { nodes: this.nodesDataSet, edges: this.edgesDataSet }, options);

      this.network.once('stabilizationIterationsDone', () => {
        try { this.network.setOptions({ physics: false }); } catch (e) {}
      });
      this.network.once('afterDrawing', () => {
        try {
          this.network.fit({ animation: { duration: 500, easingFunction: 'easeInOutQuad' } });
          setTimeout(() => this.checkSimplifyMode(), 600);
        } catch (e) {}
      });
      this.network.on('zoom', () => this.checkSimplifyMode());
    },
    buildNodeOptions(o, simplified) {
      const key = (o.objOwner || '') + '|' + o.objNm;
      const attrs = this.attrsByTable[key] || [];
      const widthByMode = simplified
        ? { minimum: 110, maximum: 220 }
        : (this.nameMode === 'both' ? { minimum: 280, maximum: 420 } : { minimum: 220, maximum: 320 });
      return {
        id: key,
        label: simplified ? this.buildSimplifiedLabel(o) : this.buildLabel(o, attrs),
        title: this.buildTooltip(o, attrs),
        shape: 'box',
        color: this.colorByArea(o),
        font: {
          multi: 'html',
          face: 'Consolas, "Courier New", monospace',
          size: simplified ? 24 : 12,
          align: simplified ? 'center' : 'left',
        },
        margin: simplified ? 12 : 10,
        widthConstraint: widthByMode,
        shadow: { enabled: true, size: 6, x: 2, y: 2 },
      };
    },
    checkSimplifyMode() {
      if (!this.network || !this.nodesDataSet) return;
      let scale;
      try { scale = this.network.getScale(); } catch (e) { return; }
      const shouldSimplify = scale < this.simplifyThreshold;
      if (shouldSimplify === this.simplifiedMode) return;
      this.simplifiedMode = shouldSimplify;
      const updates = this.objs.map(o => this.buildNodeOptions(o, shouldSimplify));
      this.nodesDataSet.update(updates);
    },
    buildSimplifiedLabel(obj) {
      const mode = this.nameMode;
      // vis-network multi='html' 은 줄(\n) 별로 태그 매칭 → 줄별로 따로 감싸야 닫는 태그가 텍스트로 노출되지 않음
      const lines = [];
      if (mode === 'en') {
        lines.push(obj.objNm);
      } else if (mode === 'ko') {
        lines.push(obj.objNmKr || obj.objNm);
      } else {
        lines.push(obj.objNm);
        if (obj.objNmKr) lines.push('(' + obj.objNmKr + ')');
      }
      return lines.map(l => '<b>' + this.escapeHtml(l) + '</b>').join('\n');
    },
    networkOptions() {
      const hier = this.layoutMode === 'hierarchical';
      return {
        layout: hier ? {
          hierarchical: {
            enabled: true,
            direction: 'LR',
            sortMethod: 'directed',
            nodeSpacing: 220,
            levelSeparation: 360,
            treeSpacing: 240,
            blockShifting: true,
            edgeMinimization: true,
            parentCentralization: true,
          },
        } : { hierarchical: { enabled: false } },
        physics: hier ? {
          enabled: false,
        } : {
          enabled: true,
          solver: 'barnesHut',
          barnesHut: { gravitationalConstant: -8000, springLength: 220, springConstant: 0.03, damping: 0.5, avoidOverlap: 0.8 },
          stabilization: { iterations: 250, fit: true },
        },
        interaction: {
          dragNodes: true,
          dragView: true,
          zoomView: true,
          hover: true,
          tooltipDelay: 200,
          navigationButtons: false,
        },
        nodes: { borderWidth: 1.5 },
        edges: { selectionWidth: 2 },
      };
    },
    buildLabel(obj, attrs) {
      const mode = this.nameMode;
      let header;
      if (mode === 'en') {
        header = '<b>' + this.escapeHtml(obj.objNm) + '</b>';
      } else if (mode === 'ko') {
        const ko = obj.objNmKr || obj.objNm;
        header = '<b>' + this.escapeHtml(ko) + '</b>';
      } else {
        const ko = obj.objNmKr ? '  <i>(' + this.escapeHtml(obj.objNmKr) + ')</i>' : '';
        header = '<b>' + this.escapeHtml(obj.objNm) + '</b>' + ko;
      }
      const sepLen = mode === 'both' ? 38 : 28;
      const nmWidth = mode === 'both' ? 30 : (mode === 'ko' ? 18 : 22);
      const sep = '─'.repeat(sepLen);
      const MAX = 25;
      const shown = attrs.slice(0, MAX);
      const rows = shown.map(a => {
        const prefix = a.pkYn === 'Y' ? '[PK]' : (a.fkYn === 'Y' ? '[FK]' : '    ');
        let nmRaw;
        if (mode === 'en') {
          nmRaw = a.attrNm || '';
        } else if (mode === 'ko') {
          nmRaw = a.attrNmKr || a.attrNm || '';
        } else {
          nmRaw = (a.attrNm || '') + (a.attrNmKr ? '(' + a.attrNmKr + ')' : '');
        }
        const nm = nmRaw.length > nmWidth ? nmRaw.slice(0, nmWidth - 1) + '…' : nmRaw.padEnd(nmWidth, ' ');
        const dt = this.formatDataType(a);
        const line = this.escapeHtml(prefix + ' ' + nm + ' ' + dt);
        if (a.pkYn === 'Y') return '<b>' + line + '</b>';
        if (a.fkYn === 'Y') return '<i>' + line + '</i>';
        return line;
      });
      if (attrs.length > MAX) rows.push('     … +' + (attrs.length - MAX) + ' columns');
      return [header, sep, ...rows].join('\n');
    },
    buildTooltip(obj, attrs) {
      const lines = [
        '테이블: ' + obj.objNm + (obj.objNmKr ? ' (' + obj.objNmKr + ')' : ''),
        '소유자: ' + (obj.objOwner || '-'),
        '컬럼 수: ' + attrs.length,
        obj.objComment ? '코멘트: ' + obj.objComment : '',
      ].filter(Boolean);
      return lines.join('\n');
    },
    formatDataType(a) {
      const t = (a.dataType || '').toUpperCase();
      if (!t) return '';
      if (a.dataLen && a.dataDecimalLen) return t + '(' + a.dataLen + ',' + a.dataDecimalLen + ')';
      if (a.dataLen) return t + '(' + a.dataLen + ')';
      return t;
    },
    escapeHtml(s) {
      if (s == null) return '';
      return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    },
    colorByArea(o) {
      const palette = ['#E3F2FD', '#F3E5F5', '#E8F5E9', '#FFF3E0', '#FFEBEE', '#E0F7FA', '#F1F8E9', '#FCE4EC', '#FFFDE7', '#EDE7F6'];
      const borderPalette = ['#1976D2', '#7B1FA2', '#388E3C', '#F57C00', '#C62828', '#00838F', '#689F38', '#C2185B', '#F9A825', '#512DA8'];
      const id = o.bizAreaId || o.subjAreaId || o.objOwner || '_default';
      let h = 0;
      for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
      const idx = h % palette.length;
      return { background: palette[idx], border: borderPalette[idx], highlight: { background: palette[idx], border: '#1A237E' } };
    },
    onLayoutChange() {
      if (this.selectedModelId && this.objs.length) this.buildNetwork();
    },
    onNameModeChange() {
      if (!this.selectedModelId || !this.objs.length) return;
      // 자율 배치 모드: 사용자가 정리해둔 노드 위치를 유지하기 위해 라벨/폰트/width 만 update
      // 계층 모드: 너비 변화가 정렬에 영향을 주므로 재빌드
      if (this.layoutMode === 'physics' && this.nodesDataSet) {
        const updates = this.objs.map(o => this.buildNodeOptions(o, this.simplifiedMode));
        this.nodesDataSet.update(updates);
      } else {
        this.buildNetwork();
      }
    },
    fitView() {
      if (this.network) this.network.fit({ animation: { duration: 400, easingFunction: 'easeInOutQuad' } });
    },
    onSearchInput() {
      if (this.searchTimer) clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => this.applySearchHighlight(), 200);
    },
    applySearchHighlight() {
      if (!this.network) return;
      const q = (this.searchTable || '').trim().toLowerCase();
      if (!q) { this.network.unselectAll(); return; }
      const hits = this.objs.filter(o =>
        (o.objNm || '').toLowerCase().includes(q) || (o.objNmKr || '').toLowerCase().includes(q)
      ).map(o => (o.objOwner || '') + '|' + o.objNm);
      if (hits.length === 0) { this.$swal('일치하는 테이블 없음', q, 'info'); return; }
      this.network.selectNodes(hits);
      this.network.focus(hits[0], { scale: 1.0, animation: { duration: 500, easingFunction: 'easeInOutQuad' } });
    },
    safeFileName(s) {
      return (s || 'erd').replace(/[\\/:*?"<>|]/g, '_');
    },
    composeExportCanvas(includeHeader) {
      const visCanvas = this.$refs.networkContainer.querySelector('canvas');
      if (!visCanvas) return null;
      const headerH = includeHeader ? 60 : 0;
      const w = visCanvas.width;
      const h = visCanvas.height + headerH;
      const out = document.createElement('canvas');
      out.width = w;
      out.height = h;
      const ctx = out.getContext('2d');
      // 흰 배경 (vis-network canvas 는 투명 → PDF/일부 뷰어에서 검정으로 보이는 문제 회피)
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, w, h);
      if (includeHeader) {
        ctx.textBaseline = 'top';
        ctx.fillStyle = '#263238';
        ctx.font = 'bold 22px "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans KR", sans-serif';
        ctx.fillText('ERD - ' + (this.selectedModelNm || ''), 24, 14);
        ctx.fillStyle = '#607D8B';
        ctx.font = '14px "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans KR", sans-serif';
        const sub = '테이블 ' + this.stats.tableCnt
          + ' · 컬럼 ' + this.stats.attrCnt
          + ' · 관계(FK) ' + this.stats.fkCnt
          + '   |   ' + this.humanTimestamp();
        ctx.fillText(sub, 24, 40);
        // 헤더 / 본문 구분선
        ctx.strokeStyle = '#ECEFF1';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, headerH - 0.5);
        ctx.lineTo(w, headerH - 0.5);
        ctx.stroke();
      }
      ctx.drawImage(visCanvas, 0, headerH);
      return out;
    },
    exportPng() {
      if (!this.network) return;
      this.fitView();
      setTimeout(() => {
        const out = this.composeExportCanvas(false);
        if (!out) { this.$swal('내보낼 캔버스를 찾을 수 없습니다.', '', 'error'); return; }
        out.toBlob((blob) => {
          if (!blob) { this.$swal('PNG 생성 실패', '', 'error'); return; }
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'ERD_' + this.safeFileName(this.selectedModelNm) + '_' + this.timestamp() + '.png';
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        }, 'image/png');
      }, 500);
    },
    openPdfDialog() { this.pdfDialog = true; },
    exportPdf() {
      if (!this.network) return;
      this.exportingPdf = true;
      this.fitView();
      setTimeout(() => {
        try {
          const out = this.composeExportCanvas(true);
          if (!out) throw new Error('canvas not found');
          const scale = this.pdfHighRes ? 2 : 1;
          const imgData = out.toDataURL('image/png', 1.0);

          const pdf = new jsPDF({ orientation: 'landscape', unit: 'mm', format: this.pdfFormat });
          const pageW = pdf.internal.pageSize.getWidth();
          const pageH = pdf.internal.pageSize.getHeight();
          const margin = 8;
          const availW = pageW - margin * 2;
          const availH = pageH - margin * 2;

          const imgW = out.width;
          const imgH = out.height;
          const ratio = Math.min(availW / imgW, availH / imgH);
          const drawW = Math.min(imgW * ratio * scale, availW);
          const drawH = Math.min(imgH * ratio * scale, availH);
          const offsetX = margin + (availW - drawW) / 2;
          const offsetY = margin + (availH - drawH) / 2;

          // jspdf 기본 폰트(helvetica)는 한글 미지원 → pdf.text 호출하지 않음.
          // 헤더 텍스트는 위 composeExportCanvas 에서 시스템 한글 폰트로 캔버스에 미리 렌더링됨.
          pdf.addImage(imgData, 'PNG', offsetX, offsetY, drawW, drawH);
          pdf.save('ERD_' + this.safeFileName(this.selectedModelNm) + '_' + this.timestamp() + '.pdf');
          this.pdfDialog = false;
        } catch (e) {
          console.error('PDF export 실패', e);
          this.$swal('PDF 생성 실패', e.message || '', 'error');
        } finally {
          this.exportingPdf = false;
        }
      }, 500);
    },
    timestamp() {
      const d = new Date();
      const pad = n => String(n).padStart(2, '0');
      return d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + '_' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
    },
    humanTimestamp() {
      const d = new Date();
      const pad = n => String(n).padStart(2, '0');
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    },
  },
};
</script>

<style scoped>
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.filterInput >>> .v-input__slot { min-height: 32px !important; }

.erd-wrap {
  position: relative;
  width: 100%;
  height: calc(100vh - 160px);
  background: #FAFAFA;
}
.erd-canvas {
  width: 100%;
  height: 100%;
  background: #FAFAFA;
  background-image:
    linear-gradient(rgba(0,0,0,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.04) 1px, transparent 1px);
  background-size: 24px 24px;
}
.erd-empty {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  text-align: center; color: #90A4AE; pointer-events: none;
}
.erd-empty p { font-size: .95rem; }
.erd-loading {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  text-align: center; color: #455A64;
  background: rgba(255,255,255,0.9); padding: 20px 28px; border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.erd-legend {
  position: absolute; right: 16px; bottom: 16px;
  background: rgba(255,255,255,0.96); border: 1px solid #CFD8DC; border-radius: 6px;
  padding: 8px 12px; font-size: .78rem; color: #37474F;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
}
.legend-row { display: flex; align-items: center; gap: 6px; line-height: 1.6; }
.legend-marker { font-family: Consolas, "Courier New", monospace; font-weight: 700; font-size: .72rem; padding: 1px 4px; border-radius: 3px; }
.legend-marker.pk { background: #FFF3E0; color: #E65100; }
.legend-marker.fk { background: #E3F2FD; color: #1565C0; }
.legend-arrow { color: #5C6BC0; font-weight: 700; }
</style>
