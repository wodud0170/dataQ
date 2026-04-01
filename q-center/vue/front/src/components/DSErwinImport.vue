<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <!-- 필터 바 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #E8EAF6; border-radius:4px;">
      <span class="filterLabel">ERwin XML 파일</span>
      <v-file-input
        v-model="selectedFile"
        accept=".xml"
        dense outlined hide-details clearable
        placeholder="ERwin XML 파일 선택"
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
      <v-btn small color="success" :disabled="!canImport" :loading="importing" @click="doImport">
        <v-icon small left>mdi-database-import</v-icon>
        임포트 실행
      </v-btn>
    </v-sheet>

    <!-- 진행 표시 -->
    <v-sheet v-if="parsing || importing" class="pa-4 mb-2 text-center" style="border:1px solid #E8EAF6; border-radius:4px;">
      <v-progress-circular indeterminate color="primary" size="32" class="mr-3" />
      <span v-if="parsing">ERwin XML 파일을 분석하고 있습니다...</span>
      <span v-if="importing">데이터를 임포트하고 있습니다...</span>
    </v-sheet>

    <!-- 요약 카드 -->
    <v-row v-if="previewResult" dense class="mb-2">
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
          class="elevation-0"
          style="flex:1; overflow-y:auto; border:1px solid #E8EAF6;"
          @click:row="onTableClick"
          :item-class="tableRowClass"
        >
          <template v-slot:item.objNm="{ item }">
            <span style="font-weight:500;">{{ item.objNm }}</span>
          </template>
        </v-data-table>
      </v-col>
      <v-col cols="8" style="display:flex; flex-direction:column; height:100%;">
        <v-sheet class="pa-2 mb-1" style="border:1px solid #E8EAF6; border-radius:4px 4px 0 0; background:#F5F7FA;">
          <strong>컬럼 목록</strong>
          <span v-if="selectedTable" class="ml-2" style="color:#666;">- {{ selectedTable }}</span>
        </v-sheet>
        <v-data-table
          :headers="columnHeaders"
          :items="filteredColumns"
          :items-per-page="-1"
          dense
          hide-default-footer
          class="elevation-0"
          style="flex:1; overflow-y:auto; border:1px solid #E8EAF6;"
        >
          <template v-slot:item.pkYn="{ item }">
            <v-icon v-if="item.pkYn === 'Y'" small color="amber darken-2">mdi-key</v-icon>
          </template>
          <template v-slot:item.nullableYn="{ item }">
            <span :style="{ color: item.nullableYn === 'N' ? '#E53935' : '#999' }">{{ item.nullableYn }}</span>
          </template>
        </v-data-table>
      </v-col>
    </v-row>

    <!-- 임포트 결과 -->
    <v-snackbar v-model="snackbar" :color="snackbarColor" :timeout="4000" top>
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
      selectedFile: null,
      selectedModelId: null,
      dataModelList: [],
      previewResult: null,
      selectedTable: null,
      parsing: false,
      importing: false,
      snackbar: false,
      snackbarText: '',
      snackbarColor: 'success',
      tableHeaders: [
        { text: '물리명', value: 'objNm', width: '50%' },
        { text: '논리명', value: 'objNmKr', width: '35%' },
        { text: '컬럼수', value: 'objAttrCnt', width: '15%', align: 'center' }
      ],
      columnHeaders: [
        { text: 'PK', value: 'pkYn', width: '5%', align: 'center' },
        { text: '물리명', value: 'attrNm', width: '22%' },
        { text: '논리명', value: 'attrNmKr', width: '22%' },
        { text: '데이터타입', value: 'dataType', width: '15%' },
        { text: '길이', value: 'dataLen', width: '10%', align: 'center' },
        { text: 'Nullable', value: 'nullableYn', width: '10%', align: 'center' },
        { text: '순서', value: 'attrOrder', width: '8%', align: 'center' }
      ]
    };
  },
  computed: {
    canImport() {
      return this.previewResult && this.selectedModelId && !this.importing;
    },
    filteredColumns() {
      if (!this.previewResult) return [];
      if (!this.selectedTable) return this.previewResult.columns;
      return this.previewResult.columns.filter(c => c.objNm === this.selectedTable);
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
    async previewXml() {
      if (!this.selectedFile) return;
      this.parsing = true;
      this.previewResult = null;
      this.selectedTable = null;
      try {
        const formData = new FormData();
        formData.append('file', this.selectedFile);
        const res = await axios.post('/api/dm/parseErwinXml', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        if (res.data.success) {
          this.previewResult = res.data;
        } else {
          this.showSnackbar(res.data.message || '파싱 실패', 'error');
        }
      } catch (e) {
        this.showSnackbar('ERwin XML 파싱 중 오류가 발생했습니다.', 'error');
      } finally {
        this.parsing = false;
      }
    },
    async doImport() {
      if (!this.canImport) return;
      this.importing = true;
      try {
        const res = await axios.post('/api/dm/importErwinModel', {
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
    onTableClick(item) {
      this.selectedTable = (this.selectedTable === item.objNm) ? null : item.objNm;
    },
    tableRowClass(item) {
      return item.objNm === this.selectedTable ? 'selected-row' : '';
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

.selected-row {
  background-color: #E3F2FD !important;
}

>>> .v-data-table tbody tr:hover {
  cursor: pointer;
}
</style>
