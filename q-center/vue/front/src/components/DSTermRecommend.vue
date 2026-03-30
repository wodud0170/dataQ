<template>
  <v-main>
    <!-- Step indicator at top -->
    <v-stepper v-model="currentStep" class="elevation-0" style="overflow: auto; height: 100%;">
      <v-stepper-header>
        <v-stepper-step :complete="currentStep > 1" step="1" color="ndColor">입력</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 2" step="2" color="ndColor">분석</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 3" step="3" color="ndColor">리뷰</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 4" step="4" color="ndColor">완료</v-stepper-step>
      </v-stepper-header>

      <!-- STEP 1: Input -->
      <v-stepper-content step="1">
        <v-card flat class="pa-4">
          <v-card-title class="px-0 pt-0">한글 컬럼명 입력</v-card-title>
          <v-card-subtitle class="px-0">엑셀 업로드, 붙여넣기, 또는 직접 입력</v-card-subtitle>

          <!-- File upload area -->
          <v-sheet outlined rounded class="pa-6 text-center mb-4"
            style="border-style: dashed; border-width: 2px; cursor: pointer;"
            @click="$refs.fileInput.click()"
            @dragover.prevent
            @drop.prevent="onFileDrop">
            <v-icon large color="grey">mdi-file-excel-outline</v-icon>
            <div class="mt-2 grey--text">엑셀 파일을 드래그하거나 클릭하여 선택</div>
            <div v-if="fileName" class="mt-1 ndColor--text font-weight-bold">{{ fileName }}</div>
            <input type="file" ref="fileInput" style="display:none" accept=".xlsx,.xls,.csv" @change="onFileSelect">
          </v-sheet>

          <!-- Direct text input -->
          <v-textarea v-model="inputText" outlined auto-grow rows="6"
            placeholder="한글명을 줄바꿈으로 구분하여 입력하세요&#10;예:&#10;기준일자&#10;사용자코드명&#10;월별정산금액"
            label="직접 입력 (줄바꿈 구분)" color="ndColor"
            @paste="onPaste">
          </v-textarea>

          <div class="d-flex justify-space-between align-center mt-2">
            <span class="grey--text">입력: {{ parsedNames.length }}건</span>
            <v-btn color="ndColor" class="white--text" :disabled="parsedNames.length === 0" @click="startAnalysis">
              <v-icon left>mdi-magnify</v-icon>분석 시작
            </v-btn>
          </div>
        </v-card>
      </v-stepper-content>

      <!-- STEP 2: Analyzing (loading) -->
      <v-stepper-content step="2">
        <v-card flat class="pa-8 text-center">
          <v-progress-circular indeterminate color="ndColor" size="64" width="5"></v-progress-circular>
          <div class="mt-4 text-h6">분석 중...</div>
          <div class="mt-2 grey--text">{{ parsedNames.length }}건의 한글명을 분석하고 있습니다</div>
        </v-card>
      </v-stepper-content>

      <!-- STEP 3: Review -->
      <v-stepper-content step="3">
        <v-card flat>
          <!-- Summary bar -->
          <v-card-title class="px-4 pt-2 pb-2 d-flex align-center">
            <span>분석 결과</span>
            <v-spacer></v-spacer>
            <v-chip small color="blue" text-color="white" class="mr-1">전체 {{ analysisResults.length }}</v-chip>
            <v-chip small color="grey" text-color="white" class="mr-1" v-if="countByStatus('REGISTERED') > 0">기등록 {{ countByStatus('REGISTERED') }}</v-chip>
            <v-chip small color="green" text-color="white" class="mr-1" v-if="countByStatus('AUTO') > 0">자동완성 {{ countByStatus('AUTO') }}</v-chip>
            <v-chip small color="orange" text-color="white" class="mr-1" v-if="countByStatus('PARTIAL') > 0">부분매칭 {{ countByStatus('PARTIAL') }}</v-chip>
            <v-chip small color="red" text-color="white" class="mr-1" v-if="countByStatus('FAILED') > 0">미매칭 {{ countByStatus('FAILED') }}</v-chip>
          </v-card-title>

          <!-- Filter + Action buttons -->
          <v-card-actions class="px-4 pt-0">
            <v-chip class="mr-1" :color="filterStatus === 'all' ? 'primary' : ''" :outlined="filterStatus !== 'all'" small @click="filterStatus = 'all'">전체</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'AUTO' ? 'green' : ''" :outlined="filterStatus !== 'AUTO'" :text-color="filterStatus === 'AUTO' ? 'white' : ''" small @click="filterStatus = 'AUTO'">자동완성</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'PARTIAL' ? 'orange' : ''" :outlined="filterStatus !== 'PARTIAL'" :text-color="filterStatus === 'PARTIAL' ? 'white' : ''" small @click="filterStatus = 'PARTIAL'">이슈</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'REGISTERED' ? 'grey' : ''" :outlined="filterStatus !== 'REGISTERED'" :text-color="filterStatus === 'REGISTERED' ? 'white' : ''" small @click="filterStatus = 'REGISTERED'">기등록</v-chip>
            <v-spacer></v-spacer>
            <v-btn small color="ndColor" class="white--text" @click="approveAll" :disabled="approvableCount === 0">
              <v-icon left small>mdi-check-all</v-icon>전체 승인 ({{ approvableCount }}건)
            </v-btn>
            <v-btn small color="primary" class="white--text ml-2" @click="registerAll"
              :disabled="approvedCount === 0">
              <v-icon left small>mdi-database-plus</v-icon>등록 실행
            </v-btn>
            <v-btn small outlined @click="currentStep = 1" class="ml-2">
              <v-icon left small>mdi-arrow-left</v-icon>다시 입력
            </v-btn>
          </v-card-actions>

          <!-- Review table -->
          <v-data-table
            :headers="reviewHeaders"
            :items="filteredResults"
            item-key="inputNm"
            show-select
            v-model="selectedItems"
            hide-default-footer
            :items-per-page="-1"
            class="px-2"
            dense>

            <!-- Status chip -->
            <template v-slot:[`item.status`]="{ item }">
              <v-chip :color="statusColor(item.status)" x-small dark>{{ statusText(item.status) }}</v-chip>
            </template>

            <!-- Words display -->
            <template v-slot:[`item.words`]="{ item }">
              <span v-if="item.status === 'REGISTERED'">-</span>
              <span v-else>
                <v-chip v-for="(w, wi) in item.words" :key="wi" x-small
                  :color="w.status === 'NEW' ? 'orange' : 'green'" text-color="white" class="mr-1">
                  {{ w.wordNm }}
                </v-chip>
              </span>
            </template>

            <!-- Recommended English abbreviation -->
            <template v-slot:[`item.recommendedEngAbrvNm`]="{ item }">
              <span v-if="item.status === 'REGISTERED'">{{ item.existingTerm && item.existingTerm.termsEngAbrvNm || '-' }}</span>
              <span v-else>{{ item.recommendedEngAbrvNm }}</span>
            </template>

            <!-- Domain -->
            <template v-slot:[`item.recommendedDomainNm`]="{ item }">
              <span v-if="item.status === 'REGISTERED'">{{ item.existingTerm && item.existingTerm.domainNm || '-' }}</span>
              <v-select v-else-if="item.domainCandidates && item.domainCandidates.length > 1"
                v-model="item.recommendedDomainNm"
                :items="item.domainCandidates" item-text="domainNm" item-value="domainNm"
                dense hide-details solo flat style="max-width:180px;font-size:0.8rem;">
              </v-select>
              <span v-else>{{ item.recommendedDomainNm || '-' }}</span>
            </template>

            <!-- Type/Length -->
            <template v-slot:[`item.typeLen`]="{ item }">
              <span v-if="item.status === 'REGISTERED'">
                {{ (item.existingTerm && item.existingTerm.dataType || '') }}
                {{ (item.existingTerm && item.existingTerm.dataLen) ? '(' + item.existingTerm.dataLen + ')' : '' }}
              </span>
              <span v-else>
                {{ item.recommendedDataType || '' }}
                {{ item.recommendedDataLen ? '(' + item.recommendedDataLen + ')' : '' }}
              </span>
            </template>

            <!-- Action column -->
            <template v-slot:[`item.action`]="{ item }">
              <span v-if="item.status === 'REGISTERED'" class="grey--text text-caption">기등록</span>
              <v-btn v-else-if="item.status === 'PARTIAL' || item.status === 'FAILED'" x-small color="orange" dark @click="editItem(item)">
                <v-icon x-small left>mdi-pencil</v-icon>수정
              </v-btn>
              <v-icon v-else-if="item._approved" color="green" small>mdi-check-circle</v-icon>
              <v-btn v-else x-small color="green" dark @click="approveItem(item)">승인</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-stepper-content>

      <!-- STEP 4: Complete -->
      <v-stepper-content step="4">
        <v-card flat class="pa-6 text-center">
          <v-icon color="green" size="64">mdi-check-circle-outline</v-icon>
          <div class="text-h5 mt-4">표준화 등록 완료</div>
          <div class="mt-3" v-if="registerResult">
            <v-chip color="green" text-color="white" class="mx-1">단어 등록: {{ registerResult.registeredWords }}건</v-chip>
            <v-chip color="blue" text-color="white" class="mx-1">용어 등록: {{ registerResult.registeredTerms }}건</v-chip>
            <v-chip color="grey" text-color="white" class="mx-1" v-if="registerResult.skipped > 0">스킵: {{ registerResult.skipped }}건</v-chip>
            <v-chip color="red" text-color="white" class="mx-1" v-if="registerResult.failed > 0">실패: {{ registerResult.failed }}건</v-chip>
          </div>

          <!-- DDL Generation -->
          <v-divider class="my-6"></v-divider>
          <div class="text-h6 mb-4">DDL 생성</div>
          <v-row justify="center" class="mb-4">
            <v-col cols="3">
              <v-select v-model="ddlDbmsType" :items="['ORACLE','POSTGRESQL','MYSQL']" label="DBMS" dense outlined hide-details></v-select>
            </v-col>
            <v-col cols="3">
              <v-text-field v-model="ddlTableName" label="테이블명" dense outlined hide-details placeholder="TB_EXAMPLE"
                @input="ddlTableName = (ddlTableName || '').toUpperCase()"></v-text-field>
            </v-col>
            <v-col cols="2">
              <v-btn color="ndColor" class="white--text" @click="generateDDL" :disabled="!ddlTableName" style="height:40px">
                DDL 생성
              </v-btn>
            </v-col>
          </v-row>

          <v-sheet v-if="ddlScript" outlined rounded class="pa-4 text-left mx-auto" style="max-width:800px; background:#f5f5f5;">
            <pre style="white-space:pre-wrap; font-size:0.85rem; margin:0;">{{ ddlScript }}</pre>
            <div class="text-right mt-2">
              <v-btn small color="ndColor" class="white--text" @click="copyDDL">
                <v-icon small left>mdi-content-copy</v-icon>복사
              </v-btn>
            </div>
          </v-sheet>

          <v-divider class="my-6"></v-divider>
          <v-btn color="ndColor" class="white--text" @click="resetAll">
            <v-icon left>mdi-restart</v-icon>새로운 분석 시작
          </v-btn>
        </v-card>
      </v-stepper-content>
    </v-stepper>

    <!-- Edit dialog for PARTIAL items -->
    <v-dialog v-model="editDialog" max-width="750">
      <v-card v-if="editingItem">
        <v-card-title>단어 정보 수정 - {{ editingItem.inputNm }}</v-card-title>
        <v-card-text>
          <!-- 분리 방법 선택 (2순위가 있을 때만 표시) -->
          <div v-if="editingItem.alternativeWords && editingItem.alternativeWords.length" class="mb-3">
            <v-btn-toggle v-model="editSplitMode" mandatory dense color="primary">
              <v-btn x-small :value="0">추천 1: {{ summarizeSplit(editingItem.words) }}</v-btn>
              <v-btn x-small :value="1">추천 2: {{ summarizeSplit(editingItem.alternativeWords) }}</v-btn>
            </v-btn-toggle>
          </div>
          <v-simple-table dense>
            <thead>
              <tr>
                <th>한글명</th><th>영문약어</th><th>영문명</th><th>도메인분류</th><th>상태</th><th>등록</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(w, wi) in currentEditWords" :key="wi">
                <td>{{ w.wordNm }}</td>
                <td>
                  <v-text-field v-if="w.status === 'NEW'" v-model="w.newWord.wordEngAbrvNm" dense hide-details
                    @input="w.newWord.wordEngAbrvNm = (w.newWord.wordEngAbrvNm || '').toUpperCase()"
                    style="max-width:120px"></v-text-field>
                  <span v-else-if="w.status === 'UNRECOGNIZED'" class="grey--text">-</span>
                  <span v-else>{{ w.selected && w.selected.wordEngAbrvNm || '-' }}</span>
                </td>
                <td>
                  <v-text-field v-if="w.status === 'NEW'" v-model="w.newWord.wordEngNm" dense hide-details
                    style="max-width:150px"></v-text-field>
                  <span v-else-if="w.status === 'UNRECOGNIZED'" class="grey--text">-</span>
                  <span v-else>{{ w.selected && w.selected.wordEngNm || '-' }}</span>
                </td>
                <td>
                  <v-text-field v-if="w.status === 'NEW'" v-model="w.newWord.domainClsfNm" dense hide-details
                    style="max-width:100px"></v-text-field>
                  <span v-else-if="w.status === 'UNRECOGNIZED'" class="grey--text">-</span>
                  <span v-else>{{ w.selected && w.selected.domainClsfNm || '-' }}</span>
                </td>
                <td>
                  <v-chip v-if="w.status === 'UNRECOGNIZED'" x-small color="red" text-color="white">매칭불가</v-chip>
                  <v-chip v-else-if="w.status === 'NEW' && !w._registered" x-small color="orange" text-color="white">신규</v-chip>
                  <v-chip v-else x-small color="green" text-color="white">등록됨</v-chip>
                </td>
                <td>
                  <v-btn v-if="w.status === 'NEW' && !w._registered" x-small color="primary"
                    :loading="w._registering"
                    @click="registerSingleWord(w)">단어등록</v-btn>
                  <v-icon v-if="w._registered" small color="green">mdi-check-circle</v-icon>
                </td>
              </tr>
            </tbody>
          </v-simple-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="editDialog = false">취소</v-btn>
          <v-btn color="ndColor" class="white--text" @click="confirmEdit">확인</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSTermRecommend',
  props: ['isMobile'],
  data: function() {
    return {
      currentStep: 1,

      // Step 1
      inputText: '',
      fileName: '',

      // Step 3
      analysisResults: [],
      selectedItems: [],
      filterStatus: 'all',

      // Edit dialog
      editDialog: false,
      editingItem: null,
      editSplitMode: 0,  // 0=1순위, 1=2순위

      // Step 4
      registerResult: null,
      ddlDbmsType: 'ORACLE',
      ddlTableName: '',
      ddlScript: '',

      reviewHeaders: [
        { text: '한글명', value: 'inputNm', width: '15%' },
        { text: '상태', value: 'status', width: '8%' },
        { text: '구성단어', value: 'words', width: '20%', sortable: false },
        { text: '영문약어', value: 'recommendedEngAbrvNm', width: '17%' },
        { text: '도메인', value: 'recommendedDomainNm', width: '15%' },
        { text: '타입/길이', value: 'typeLen', width: '10%', sortable: false },
        { text: '액션', value: 'action', width: '10%', sortable: false, align: 'center' },
      ],
    };
  },
  computed: {
    parsedNames: function() {
      if (!this.inputText) return [];
      return this.inputText.split('\n')
        .map(function(s) { return s.replace(/\s+/g, '').trim(); })
        .filter(function(s) { return s.length > 0; });
    },
    currentEditWords: function() {
      if (!this.editingItem) return [];
      if (this.editSplitMode === 1 && this.editingItem.alternativeWords && this.editingItem.alternativeWords.length) {
        return this.editingItem.alternativeWords;
      }
      return this.editingItem.words || [];
    },
    filteredResults: function() {
      var self = this;
      if (self.filterStatus === 'all') return self.analysisResults;
      if (self.filterStatus === 'PARTIAL') {
        return self.analysisResults.filter(function(r) { return r.status === 'PARTIAL' || r.status === 'FAILED'; });
      }
      return self.analysisResults.filter(function(r) { return r.status === self.filterStatus; });
    },
    approvableCount: function() {
      return this.analysisResults.filter(function(r) {
        return (r.status === 'AUTO' || r.status === 'PARTIAL') && !r._approved;
      }).length;
    },
    approvedCount: function() {
      return this.analysisResults.filter(function(r) { return r._approved; }).length;
    },
  },
  methods: {
    onFileSelect: function(event) {
      var file = event.target.files[0];
      if (!file) return;
      this.fileName = file.name;
      this.readExcelFile(file);
    },
    onFileDrop: function(event) {
      var file = event.dataTransfer.files[0];
      if (!file) return;
      this.fileName = file.name;
      this.readExcelFile(file);
    },
    onPaste: function() {
      // textarea handles paste naturally, parsedNames computed will update
    },
    readExcelFile: function(file) {
      var self = this;
      if (file.name.endsWith('.csv')) {
        var reader = new FileReader();
        reader.onload = function(e) {
          var text = e.target.result;
          var lines = text.split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l; });
          // Skip header if it looks like a header
          if (lines.length > 0 && (lines[0].indexOf('한글') >= 0 || lines[0].indexOf('컬럼') >= 0 || lines[0].indexOf('No') >= 0)) {
            lines.shift();
          }
          // Take first column only
          var names = lines.map(function(l) { return l.split(',')[0].replace(/"/g, '').trim(); }).filter(function(n) { return n; });
          self.inputText = names.join('\n');
        };
        reader.readAsText(file, 'UTF-8');
      } else {
        // For xlsx, upload to server for parsing
        var formData = new FormData();
        formData.append('file', file);
        axios.post(self.$APIURL.base + 'api/std/parseExcelColumnNames', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        }).then(function(res) {
          if (res.data && res.data.length > 0) {
            self.inputText = res.data.join('\n');
          }
        }).catch(function() {
          self.$swal.fire({
            title: '엑셀 파싱 실패',
            text: 'CSV 형식으로 다시 시도해주세요.',
            icon: 'warning',
            confirmButtonText: '확인'
          });
        });
      }
    },
    startAnalysis: function() {
      var self = this;
      self.currentStep = 2;
      self.analysisResults = [];
      self.selectedItems = [];

      axios.post(self.$APIURL.base + 'api/std/analyzeTermsBatch', {
        termNames: self.parsedNames
      }).then(function(res) {
        if (res.data) {
          for (var i = 0; i < res.data.length; i++) {
            res.data[i]._approved = false;
          }
          self.analysisResults = res.data;
        }
        self.currentStep = 3;
      }).catch(function(err) {
        var msg = (err.response && err.response.data && err.response.data.message) || '분석 중 오류가 발생했습니다.';
        self.$swal.fire({ title: '분석 실패', text: msg, icon: 'error', confirmButtonText: '확인' });
        self.currentStep = 1;
      });
    },
    countByStatus: function(status) {
      return this.analysisResults.filter(function(r) { return r.status === status; }).length;
    },
    statusColor: function(status) {
      var map = { 'REGISTERED': 'grey', 'AUTO': 'green', 'PARTIAL': 'orange', 'FAILED': 'red' };
      return map[status] || 'grey';
    },
    statusText: function(status) {
      var map = { 'REGISTERED': '기등록', 'AUTO': '자동완성', 'PARTIAL': '부분매칭', 'FAILED': '미매칭' };
      return map[status] || status;
    },
    approveItem: function(item) {
      this.$set(item, '_approved', true);
    },
    approveAll: function() {
      for (var i = 0; i < this.analysisResults.length; i++) {
        var r = this.analysisResults[i];
        if (r.status === 'AUTO' || r.status === 'PARTIAL') {
          this.$set(r, '_approved', true);
        }
      }
    },
    summarizeSplit: function(words) {
      if (!words) return '';
      return words.map(function(w) { return w.wordNm; }).join('+');
    },
    editItem: function(item) {
      this.editingItem = item;
      this.editSplitMode = 0;
      this.editDialog = true;
    },
    registerSingleWord: function(w) {
      var self = this;
      if (!w.newWord || !w.newWord.wordEngAbrvNm || !w.newWord.wordEngAbrvNm.trim()) {
        self.$swal.fire({ title: '입력 오류', text: '영문약어를 먼저 입력해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      if (!w.newWord.wordEngNm || !w.newWord.wordEngNm.trim()) {
        self.$swal.fire({ title: '입력 오류', text: '영문명을 먼저 입력해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      self.$set(w, '_registering', true);
      axios.post(self.$APIURL.base + 'api/std/registerWord', {
        wordNm: w.wordNm,
        wordEngAbrvNm: w.newWord.wordEngAbrvNm,
        wordEngNm: w.newWord.wordEngNm,
        wordDesc: w.wordNm,
        domainClsfNm: w.newWord.domainClsfNm || ''
      }).then(function(res) {
        self.$set(w, '_registering', false);
        if (res.data.success) {
          // 단어 등록 성공 → 상태를 MATCHED로 변경
          self.$set(w, '_registered', true);
          w.status = 'MATCHED';
          w.selected = {
            wordId: res.data.wordId,
            wordNm: res.data.wordNm,
            wordEngAbrvNm: res.data.wordEngAbrvNm,
            wordEngNm: res.data.wordEngNm,
            domainClsfNm: res.data.domainClsfNm || ''
          };
          w.candidates = [w.selected];
          // 전체 상태 재판정
          self.recalcItemStatus(self.editingItem);
          var msg = res.data.alreadyExists ? '이미 등록된 단어입니다. 기존 정보를 사용합니다.' : '단어가 등록되었습니다.';
          self.$swal.fire({ title: '완료', text: msg, icon: 'success', confirmButtonText: '확인', timer: 1500 });
        } else {
          self.$swal.fire({ title: '등록 실패', text: res.data.message, icon: 'error', confirmButtonText: '확인' });
        }
      }).catch(function(err) {
        self.$set(w, '_registering', false);
        self.$swal.fire({ title: '등록 실패', text: '서버 오류가 발생했습니다.', icon: 'error', confirmButtonText: '확인' });
      });
    },
    recalcItemStatus: function(item) {
      if (!item || !item.words) return;
      var hasMatched = false;
      var hasNew = false;
      for (var i = 0; i < item.words.length; i++) {
        if (item.words[i].status === 'MATCHED') hasMatched = true;
        if (item.words[i].status === 'NEW') hasNew = true;
      }
      if (!hasMatched) {
        item.status = 'FAILED';
      } else if (hasNew) {
        item.status = 'PARTIAL';
      } else {
        item.status = 'AUTO';
      }
      // 영문약어 재조합
      var parts = [];
      for (var j = 0; j < item.words.length; j++) {
        var w = item.words[j];
        if (w.status === 'MATCHED' && w.selected) {
          parts.push(w.selected.wordEngAbrvNm);
        } else if (w.status === 'NEW' && w.newWord) {
          parts.push(w.newWord.wordEngAbrvNm);
        }
      }
      item.recommendedEngAbrvNm = parts.join('_');
    },
    confirmEdit: function() {
      var item = this.editingItem;
      var activeWords = this.currentEditWords;

      // 2순위 선택 시 item.words를 alternativeWords로 교체
      if (this.editSplitMode === 1 && item.alternativeWords && item.alternativeWords.length) {
        item.words = item.alternativeWords;
        item.alternativeWords = null;
      }

      if (item && item.words) {
        // UNRECOGNIZED 단어가 있는지 체크
        var unrecognized = [];
        var unregistered = [];
        for (var j = 0; j < item.words.length; j++) {
          var nw = item.words[j];
          if (nw.status === 'UNRECOGNIZED') {
            unrecognized.push(nw.wordNm);
          } else if (nw.status === 'NEW' && !nw._registered) {
            unregistered.push(nw.wordNm);
          }
        }
        if (unregistered.length > 0) {
          this.$swal.fire({
            title: '미등록 단어 있음',
            text: '다음 단어를 먼저 등록해주세요: ' + unregistered.join(', '),
            icon: 'warning',
            confirmButtonText: '확인'
          });
          return;
        }
        var parts = [];
        for (var i = 0; i < item.words.length; i++) {
          var w = item.words[i];
          if (w.status === 'MATCHED' && w.selected) {
            parts.push(w.selected.wordEngAbrvNm);
          } else if (w.status === 'NEW' && w.newWord) {
            parts.push(w.newWord.wordEngAbrvNm);
          }
        }
        item.recommendedEngAbrvNm = parts.join('_');
      }
      this.$set(item, '_approved', true);
      this.editDialog = false;
    },
    registerAll: function() {
      var self = this;
      var items = [];

      for (var i = 0; i < self.analysisResults.length; i++) {
        var r = self.analysisResults[i];
        if (!r._approved || r.status === 'REGISTERED') continue;

        var wordIds = [];
        var newWords = [];
        if (r.words) {
          for (var j = 0; j < r.words.length; j++) {
            var w = r.words[j];
            if (w.status === 'MATCHED' && w.selected) {
              wordIds.push({ wordId: w.selected.wordId, wordOrd: j });
            } else if (w.status === 'NEW' && w.newWord) {
              wordIds.push({ wordId: null, wordOrd: j, newWordIndex: newWords.length });
              newWords.push({
                wordNm: w.wordNm,
                wordEngAbrvNm: w.newWord.wordEngAbrvNm,
                wordEngNm: w.newWord.wordEngNm,
                wordDesc: w.wordNm,
                domainClsfNm: w.newWord.domainClsfNm || ''
              });
            }
          }
        }

        items.push({
          termsNm: r.inputNm,
          termsEngAbrvNm: r.recommendedEngAbrvNm,
          termsDesc: r.inputNm,
          domainNm: r.recommendedDomainNm || '',
          words: wordIds,
          newWords: newWords
        });
      }

      if (items.length === 0) {
        self.$swal.fire({ title: '등록할 항목이 없습니다.', icon: 'info', confirmButtonText: '확인' });
        return;
      }

      self.$swal.fire({
        title: '표준화 등록',
        text: items.length + '건의 용어를 등록합니다. 계속하시겠습니까?',
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '등록',
        cancelButtonText: '취소'
      }).then(function(result) {
        if (!result.value) return;

        axios.post(self.$APIURL.base + 'api/std/registerTermsBatch', { items: items })
          .then(function(res) {
            self.registerResult = res.data;
            self.currentStep = 4;
          }).catch(function(err) {
            var msg = (err.response && err.response.data && err.response.data.message) || '등록 중 오류가 발생했습니다.';
            self.$swal.fire({ title: '등록 실패', text: msg, icon: 'error', confirmButtonText: '확인' });
          });
      });
    },
    generateDDL: function() {
      var self = this;
      if (!self.ddlTableName) return;

      var terms = [];
      for (var i = 0; i < self.analysisResults.length; i++) {
        var r = self.analysisResults[i];
        if (r.status === 'REGISTERED') {
          terms.push({
            engAbrv: r.existingTerm && r.existingTerm.termsEngAbrvNm || '',
            korNm: r.inputNm,
            dataType: r.existingTerm && r.existingTerm.dataType || '',
            dataLen: r.existingTerm && r.existingTerm.dataLen || 0
          });
        } else if (r._approved) {
          terms.push({
            engAbrv: r.recommendedEngAbrvNm,
            korNm: r.inputNm,
            dataType: r.recommendedDataType || '',
            dataLen: r.recommendedDataLen || 0
          });
        }
      }

      var dbms = self.ddlDbmsType;
      var tbl = self.ddlTableName;
      var ddl = '';
      var comments = '';

      if (dbms === 'ORACLE') {
        ddl = 'ALTER TABLE ' + tbl + ' ADD (\n';
        for (var j = 0; j < terms.length; j++) {
          var t = terms[j];
          var typeStr = self.oracleType(t.dataType, t.dataLen);
          ddl += '    ' + self.pad(t.engAbrv, 25) + typeStr;
          ddl += (j < terms.length - 1 ? ',' : '') + '  -- ' + t.korNm + '\n';
          comments += 'COMMENT ON COLUMN ' + tbl + '.' + t.engAbrv + " IS '" + t.korNm + "';\n";
        }
        ddl += ');\n\n' + comments;
      } else if (dbms === 'POSTGRESQL') {
        for (var k = 0; k < terms.length; k++) {
          var p = terms[k];
          var pType = self.pgType(p.dataType, p.dataLen);
          ddl += 'ALTER TABLE ' + tbl + ' ADD COLUMN ' + p.engAbrv + ' ' + pType + ';  -- ' + p.korNm + '\n';
          comments += 'COMMENT ON COLUMN ' + tbl + '.' + p.engAbrv + " IS '" + p.korNm + "';\n";
        }
        ddl += '\n' + comments;
      } else if (dbms === 'MYSQL') {
        for (var m = 0; m < terms.length; m++) {
          var q = terms[m];
          var mType = self.mysqlType(q.dataType, q.dataLen);
          ddl += "ALTER TABLE " + tbl + " ADD COLUMN " + q.engAbrv + " " + mType + " COMMENT '" + q.korNm + "';\n";
        }
      }

      self.ddlScript = ddl;
    },
    oracleType: function(type, len) {
      if (!type) return 'VARCHAR2(100)';
      var t = type.toUpperCase();
      if (t === 'DATE' || t === 'DATETIME') return 'DATE';
      if (t === 'VARCHAR' || t === 'VARCHAR2' || t === 'CHAR') return 'VARCHAR2(' + (len || 100) + ')';
      if (t === 'NUMBER' || t === 'NUMERIC' || t === 'DECIMAL') return 'NUMBER(' + (len || 22) + ')';
      if (t === 'CLOB') return 'CLOB';
      return t + (len ? '(' + len + ')' : '');
    },
    pgType: function(type, len) {
      if (!type) return 'VARCHAR(100)';
      var t = type.toUpperCase();
      if (t === 'DATE') return 'DATE';
      if (t === 'DATETIME') return 'TIMESTAMP';
      if (t === 'VARCHAR' || t === 'VARCHAR2' || t === 'CHAR') return 'VARCHAR(' + (len || 100) + ')';
      if (t === 'NUMBER' || t === 'NUMERIC' || t === 'DECIMAL') return 'NUMERIC(' + (len || 22) + ')';
      return t + (len ? '(' + len + ')' : '');
    },
    mysqlType: function(type, len) {
      if (!type) return 'VARCHAR(100)';
      var t = type.toUpperCase();
      if (t === 'DATE') return 'DATE';
      if (t === 'DATETIME') return 'DATETIME';
      if (t === 'VARCHAR' || t === 'VARCHAR2' || t === 'CHAR') return 'VARCHAR(' + (len || 100) + ')';
      if (t === 'NUMBER' || t === 'NUMERIC' || t === 'DECIMAL') return 'DECIMAL(' + (len || 22) + ')';
      return t + (len ? '(' + len + ')' : '');
    },
    pad: function(str, len) {
      if (!str) str = '';
      while (str.length < len) str += ' ';
      return str;
    },
    copyDDL: function() {
      var self = this;
      if (navigator.clipboard) {
        navigator.clipboard.writeText(self.ddlScript);
      } else {
        var ta = document.createElement('textarea');
        ta.value = self.ddlScript;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      self.$swal.fire({ title: '복사되었습니다.', icon: 'success', showConfirmButton: false, timer: 1500 });
    },
    resetAll: function() {
      this.currentStep = 1;
      this.inputText = '';
      this.fileName = '';
      this.analysisResults = [];
      this.selectedItems = [];
      this.filterStatus = 'all';
      this.registerResult = null;
      this.ddlScript = '';
      this.ddlTableName = '';
    },
  },
};
</script>
