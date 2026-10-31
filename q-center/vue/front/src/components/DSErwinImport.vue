<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 85번 — 포맷 선택: ERwin native XML / XMI 2.1 (OMG 표준) -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:12px; border:1px solid #E8EAF6; border-radius:4px; background:#FAFBFC;">
      <span class="filterLabel">파일 포맷</span>
      <v-radio-group v-model="format" row dense hide-details mandatory id="rg-import-format" @change="onFormatChange">
        <v-radio label="ERwin native XML" value="erwin" id="rb-format-erwin"></v-radio>
        <v-radio label="XMI 2.1 (OMG 표준)" value="xmi" id="rb-format-xmi"></v-radio>
      </v-radio-group>
      <v-spacer />
      <span style="font-size:.75rem; color:#90A4AE;">
        <v-icon x-small>mdi-information-outline</v-icon>
        XMI 2.1 = ERwin 9.x+ / EA / VP / Modelio 호환 표준 포맷
      </span>
    </v-sheet>

    <!-- 필터 바 — 우측 padding 추가로 [XMI 추출] 버튼 잘림 방지 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2"
      style="gap:8px; border:1px solid #E8EAF6; border-radius:4px; padding-right:32px !important;">
      <span class="filterLabel">{{ formatLabel }} 파일</span>
      <v-file-input
        v-model="selectedFile"
        :accept="fileAccept"
        dense outlined hide-details clearable
        :placeholder="formatLabel + ' 파일 선택'"
        prepend-icon="mdi-file-xml-box"
        style="width:300px; flex-grow:0;"
        @change="onFileChange"
      />

      <span class="filterLabel">대상 데이터모델</span>
      <v-autocomplete
        v-model="selectedModelId"
        :items="dataModelList"
        item-text="dataModelNm"
        item-value="dataModelId"
        dense outlined hide-details clearable
        placeholder="데이터 모델 선택"
        style="width:250px; flex-grow:0;"
      />

      <v-spacer />

      <v-btn small color="primary" :disabled="!selectedFile" :loading="parsing" @click="previewXml" class="mr-2">
        <v-icon small left>mdi-eye</v-icon>
        미리보기
      </v-btn>
      <v-btn small color="success" :disabled="!canImport" :loading="importing" @click="doImport" class="mr-2">
        <v-icon small left>mdi-database-import</v-icon>
        임포트 실행
      </v-btn>
      <!-- 추출 버튼 둘은 한 묶음 — 폭이 모자라면 묶음째 다음 줄 오른쪽으로 (104번 WP8-40). mr-2 — 윗줄 [임포트 실행] 오른쪽 끝과 맞춘다 -->
      <div class="d-flex align-center ml-auto mr-2">
        <v-btn small outlined color="indigo" class="btn-auto mr-2" :disabled="!selectedModelId" @click="doExport"
          id="btn-export-xmi" title="현재 선택된 데이터 모델을 XMI 2.1 로 내려받기">
          <v-icon small left>mdi-file-export</v-icon>
          XMI 2.1 추출
        </v-btn>
        <!-- text-none: Vuetify 기본 대문자 변환이면 'ERWIN' 으로 보인다 -->
        <v-btn small outlined color="indigo" class="btn-auto text-none" :disabled="!selectedModelId" @click="doExportErwin"
          id="btn-export-erwin" title="현재 선택된 데이터 모델을 ERwin XML 로 내려받기">
          <v-icon small left>mdi-file-export</v-icon>
          ERwin XML 추출
        </v-btn>
      </div>
    </v-sheet>

    <!-- 진행 표시 -->
    <v-sheet v-if="parsing || importing" class="pa-4 mb-2 text-center" style="border:1px solid #E8EAF6; border-radius:4px;">
      <v-progress-circular indeterminate color="primary" size="32" class="mr-3" />
      <span v-if="parsing">{{ formatLabel }} 파일을 분석하고 있습니다...</span>
      <span v-if="importing">데이터를 임포트하고 있습니다...</span>
    </v-sheet>

    <!-- 요약 카드 -->
    <!-- flex:0 — .row 기본 flex:1 이면 카드 줄이 늘어나 아래 목록이 눌린다 -->
    <v-row v-if="previewResult" dense class="mb-2" style="flex:0 0 auto;">
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E8EAF6;">
            <v-icon color="#3F51B5">mdi-table</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ previewResult.tableCount }}</div>
            <div class="stat-label">테이블 수</div>
          </div>
        </v-card>
      </v-col>
      <v-col cols="3">
        <v-card class="stat-card" outlined>
          <div class="stat-icon-wrap" style="background:#E3F2FD;">
            <v-icon color="#1976D2">mdi-view-column</v-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ previewResult.columnCount }}</div>
            <div class="stat-label">컬럼 수</div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- 테이블 목록 -->
    <v-row v-if="previewResult" dense style="flex:1; min-height:0;">
      <v-col cols="4" style="display:flex; flex-direction:column; height:100%;">
        <v-sheet class="pa-2 mb-1" style="border:1px solid #E8EAF6; border-radius:4px 4px 0 0; background:#F5F7FA;">
          <strong>테이블 목록</strong>
        </v-sheet>
        <v-data-table
          :headers="tableHeaders"
          :items="previewResult.tables"
          :items-per-page="-1"
          dense
          hide-default-footer
          fixed-header height="100%"
          class="elevation-0 preview-table"
          style="flex:1; min-height:0; border:1px solid #E8EAF6;"
          @click:row="onTableClick"
          :item-class="tableRowClass"
        >
          <template v-slot:item.objNm="{ item }">
            <span style="font-weight:500;">{{ item.objNm }}</span>
          </template>
        </v-data-table>
      </v-col>
      <v-col cols="8" style="display:flex; flex-direction:column; height:100%;">
        <v-sheet class="pa-2 mb-1" style="border:1px solid #E8EAF6; border-radius:4px 4px 0 0; background:#F5F7FA; overflow-wrap:anywhere;">
          <strong>컬럼 목록</strong>
          <span v-if="selectedTable" class="ml-2" style="color:#666;">- {{ selectedTableLabel }}</span>
        </v-sheet>
        <v-data-table
          :headers="columnHeaders"
          :items="filteredColumns"
          :items-per-page="-1"
          dense
          hide-default-footer
          fixed-header height="100%"
          class="elevation-0 preview-table"
          style="flex:1; min-height:0; border:1px solid #E8EAF6;"
        >
          <template v-slot:item.pkYn="{ item }">
            <v-icon v-if="item.pkYn === 'Y'" small color="amber darken-2">mdi-key</v-icon>
          </template>
          <template v-slot:item.fkYn="{ item }">
            <v-icon v-if="item.fkYn === 'Y'" small color="indigo" :title="fkParentLabel(item)">mdi-key-link</v-icon>
          </template>
          <template v-slot:item.nullableYn="{ item }">
            <span :style="{ color: item.nullableYn === 'N' ? '#E53935' : '#999' }">{{ item.nullableYn }}</span>
          </template>
        </v-data-table>
      </v-col>
    </v-row>

    <!-- 임포트 결과 — 오류(덮어쓰기 거부처럼 테이블 이름이 여러 개 붙는 문구)는 [닫기]를 누를 때까지 둔다 -->
    <v-snackbar v-model="snackbar" :color="snackbarColor" :timeout="snackbarColor === 'error' ? -1 : 4000" top>
      {{ snackbarText }}
      <template v-slot:action="{ attrs }">
        <v-btn text v-bind="attrs" @click="snackbar = false">닫기</v-btn>
      </template>
    </v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSErwinImport',
  data() {
    return {
      format: 'xmi',           // 'erwin' (기존 native XML) | 'xmi' (OMG 표준 XMI 2.1)
      selectedFile: null,
      selectedModelId: null,
      dataModelList: [],
      previewResult: null,
      selectedTable: null,     // 테이블 키 (소유자|이름) — 두 소유자에 같은 이름 테이블이 있어도 컬럼이 섞이지 않게
      parsing: false,
      importing: false,
      snackbar: false,
      snackbarText: '',
      snackbarColor: 'success'
    };
  },
  computed: {
    canImport() {
      return this.previewResult && this.selectedModelId && !this.importing;
    },
    // 104번 WP8-40 — 파일에 소유자가 있을 때만 "소유자" 열, FK 컬럼이 있을 때만 "FK" 열
    hasOwner() {
      return !!this.previewResult && this.previewResult.tables.some(t => t.objOwner);
    },
    hasFk() {
      return !!this.previewResult && this.previewResult.columns.some(c => c.fkYn === 'Y');
    },
    tableHeaders() {
      const headers = [
        { text: '물리명', value: 'objNm', width: this.hasOwner ? '36%' : '50%' },
        { text: '논리명', value: 'objNmKr', width: this.hasOwner ? '27%' : '35%' },
        { text: '컬럼수', value: 'objAttrCnt', width: '15%', align: 'center', class: 'text-no-wrap' }
      ];
      if (this.hasOwner) headers.unshift({ text: '소유자', value: 'objOwner', width: '22%' });
      return headers;
    },
    columnHeaders() {
      const headers = [
        { text: 'PK', value: 'pkYn', width: '5%', align: 'center' },
        { text: '물리명', value: 'attrNm', width: this.hasFk ? '20%' : '22%' },
        { text: '논리명', value: 'attrNmKr', width: this.hasFk ? '20%' : '22%' },
        { text: '데이터타입', value: 'dataType', width: '15%' },
        { text: '길이', value: 'dataLen', width: '10%', align: 'center' },
        { text: 'Nullable', value: 'nullableYn', width: '10%', align: 'center' },
        { text: '순서', value: 'attrOrder', width: '8%', align: 'center' }
      ];
      if (this.hasFk) headers.splice(1, 0, { text: 'FK', value: 'fkYn', width: '5%', align: 'center' });
      return headers;
    },
    filteredColumns() {
      if (!this.previewResult) return [];
      if (!this.selectedTable) return this.previewResult.columns;
      return this.previewResult.columns.filter(c => this.tableKey(c) === this.selectedTable);
    },
    selectedTableLabel() {
      const t = this.previewResult && this.previewResult.tables.find(x => this.tableKey(x) === this.selectedTable);
      if (!t) return '';
      return t.objOwner ? t.objOwner + '.' + t.objNm : t.objNm;
    },
    formatLabel() {
      return this.format === 'xmi' ? 'XMI 2.1' : 'ERwin XML';
    },
    fileAccept() {
      return this.format === 'xmi' ? '.xmi,.xml' : '.xml';
    },
    parseEndpoint() {
      return this.format === 'xmi' ? '/api/dm/parseXmi' : '/api/dm/parseErwinXml';
    },
    importEndpoint() {
      return this.format === 'xmi' ? '/api/dm/importXmiModel' : '/api/dm/importErwinModel';
    }
  },
  created() {
    this.loadDataModelList();
  },
  methods: {
    async loadDataModelList() {
      try {
        const res = await axios.post('/api/dm/getDataModelList', {});
        this.dataModelList = res.data || [];
      } catch (e) {
        console.error('데이터모델 목록 조회 실패', e);
      }
    },
    onFileChange() {
      this.previewResult = null;
      this.selectedTable = null;
    },
    onFormatChange() {
      // 포맷 바꾸면 미리보기/파일 초기화 (혼동 방지)
      this.previewResult = null;
      this.selectedTable = null;
      this.selectedFile = null;
    },
    async previewXml() {
      if (!this.selectedFile) return;
      this.parsing = true;
      this.previewResult = null;
      this.selectedTable = null;
      try {
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        const res = await axios.post(this.parseEndpoint, formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        if (res.data.success) {
          this.previewResult = res.data;
        } else {
          this.showSnackbar(res.data.message || '파싱 실패', 'error');
        }
      } catch (e) {
        this.showSnackbar(this.formatLabel + ' 파싱 중 오류가 발생했습니다.', 'error');
      } finally {
        this.parsing = false;
      }
    },
    async doImport() {
      if (!this.canImport) return;
      this.importing = true;
      try {
        const res = await axios.post(this.importEndpoint, {
          dataModelId: this.selectedModelId,
          tables: this.previewResult.tables,
          columns: this.previewResult.columns
        });
        if (res.data.success) {
          this.showSnackbar(res.data.message, 'success');
        } else {
          this.showSnackbar(res.data.message || '임포트 실패', 'error');
        }
      } catch (e) {
        this.showSnackbar('임포트 중 오류가 발생했습니다.', 'error');
      } finally {
        this.importing = false;
      }
    },
    // 파일을 axios 로 받아 저장한다 — 링크 다운로드는 서버 오류(500 <error>…</error>)를 화면에서 알 수 없었다.
    // 401(세션 끊김)은 main.js 가로채기가 로그인 안내를 띄우고 이 요청은 끝나지 않는다
    async _download(url) {
      let res;
      try {
        res = await axios.get(url, { responseType: 'blob' });
      } catch (e) {
        const body = e.response ? await e.response.data.text() : '';
        const m = /<error>([\s\S]*)<\/error>/.exec(body);
        throw new Error(m ? m[1] : (e.response ? '서버 응답 ' + e.response.status : e.message));
      }
      const href = window.URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = href;
      a.download = /filename="([^"]+)"/.exec(res.headers['content-disposition'])[1];
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(href);
    },
    async doExport() {
      if (!this.selectedModelId) return;
      try {
        await this._download('/api/dm/exportXmi?dataModelId=' + encodeURIComponent(this.selectedModelId));
        this.showSnackbar('XMI 2.1 추출 요청 완료 (브라우저 다운로드 확인)', 'success');
      } catch (e) {
        this.showSnackbar('XMI 추출 중 오류: ' + (e.message || e), 'error');
      }
    },
    async doExportErwin() {
      if (!this.selectedModelId) return;
      try {
        await this._download('/api/dm/exportErwinXml?dataModelId=' + encodeURIComponent(this.selectedModelId));
        this.showSnackbar('ERwin XML 추출 요청 완료 (브라우저 다운로드 확인)', 'success');
      } catch (e) {
        this.showSnackbar('ERwin XML 추출 중 오류: ' + (e.message || e), 'error');
      }
    },
    tableKey(item) {
      return (item.objOwner || '') + '|' + item.objNm;
    },
    onTableClick(item) {
      const key = this.tableKey(item);
      this.selectedTable = (this.selectedTable === key) ? null : key;
    },
    tableRowClass(item) {
      return this.tableKey(item) === this.selectedTable ? 'selected-row' : '';
    },
    fkParentLabel(item) {
      return [item.fkParentObjOwner, item.fkParentObjNm, item.fkParentAttrNm].filter(v => v).join('.');
    },
    showSnackbar(text, color) {
      this.snackbarText = text;
      this.snackbarColor = color;
      this.snackbar = true;
    }
  }
};
</script>

<style scoped>
.filterLabel {
  font-weight: 600;
  font-size: 0.85rem;
  color: #455A64;
  white-space: nowrap;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px !important;
}

.stat-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: #1A237E;
}

.stat-label {
  font-size: 0.75rem;
  color: #90A4AE;
}

/* 행은 v-data-table 안에서 그려져 scoped 속성이 없다 → >>> 로 */
>>> .selected-row {
  background-color: #E3F2FD !important;
}

>>> .v-data-table tbody tr:hover {
  cursor: pointer;
}

/* 긴 소유자·테이블·컬럼 이름이 열을 밀지 않게 — 열 너비는 머리 % 그대로(fixed), 넘치는 이름만 줄바꿈(한글은 낱말 단위) */
>>> .preview-table table {
  table-layout: fixed;
}
>>> .preview-table td,
>>> .preview-table th {
  padding: 0 8px !important;
}
>>> .preview-table td {
  overflow-wrap: anywhere;
  word-break: keep-all;
}

/* 스낵바 — 긴 소유자.테이블 이름이 상자 밖으로 나가 [닫기]를 밀어내지 않게 */
>>> .v-snack__content {
  overflow-wrap: anywhere;
}
</style>
