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
      </v-stepper-header>

      <!-- STEP 1: Input -->
      <v-stepper-content step="1">
        <v-card flat class="pa-4">
          <div class="d-flex align-center px-0">
            <div>
              <v-card-title class="px-0 pt-0 pb-1">한글 컬럼명 입력</v-card-title>
              <v-card-subtitle class="px-0 pt-0">엑셀 업로드, 붙여넣기, 또는 직접 입력</v-card-subtitle>
            </div>
            <v-spacer></v-spacer>
            <v-btn small outlined color="ndColor" class="btn-wide" @click="downloadTemplate">
              <v-icon small left>mdi-download</v-icon>양식 다운로드
            </v-btn>
          </div>

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
            placeholder="한글명을 줄바꿈으로 구분하여 입력하세요, 공백은 무시됩니다"
            label="직접 입력 (줄바꿈 구분)" color="ndColor"
            @paste="onPaste">
          </v-textarea>

          <div class="d-flex justify-space-between align-center mt-2">
            <span class="grey--text">입력: {{ parsedNames.length }}건</span>
            <v-btn color="ndColor" class="white--text btn-wide-lg" :disabled="parsedNames.length === 0 || isAnalyzing" :loading="isAnalyzing" @click="startAnalysis">
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
          <v-card-title class="px-4 pt-2 pb-2 d-flex align-center flex-wrap" style="gap:4px;">
            <span>분석 결과</span>
            <v-spacer></v-spacer>
            <v-chip small color="blue" text-color="white" class="mr-1">전체 {{ analysisResults.length }}</v-chip>
            <v-chip small color="grey" text-color="white" class="mr-1" v-if="countByStatus('REGISTERED') > 0">기등록 {{ countByStatus('REGISTERED') }}</v-chip>
            <v-chip small color="green" text-color="white" class="mr-1" v-if="countByStatus('AUTO') > 0">자동완성 {{ countByStatus('AUTO') }}</v-chip>
            <v-chip small color="orange" text-color="white" class="mr-1" v-if="countByStatus('PARTIAL') > 0">부분매칭 {{ countByStatus('PARTIAL') }}</v-chip>
            <v-chip small color="red" text-color="white" class="mr-1" v-if="countByStatus('FAILED') > 0">미매칭 {{ countByStatus('FAILED') }}</v-chip>
            <v-divider vertical class="mx-2" v-if="countPost('SUCCESS') + countPost('FAIL') > 0"></v-divider>
            <v-chip small :color="isAdmin ? 'green' : 'blue'" text-color="white" class="mr-1" v-if="countPost('SUCCESS') > 0">
              {{ isAdmin ? '등록완료' : '등록신청' }} {{ countPost('SUCCESS') }}
            </v-chip>
            <v-chip small color="red" text-color="white" class="mr-1" v-if="countPost('FAIL') > 0">실패 {{ countPost('FAIL') }}</v-chip>
          </v-card-title>

          <!-- Filter + Column toggle + Action buttons -->
          <v-card-actions class="px-4 pt-0 flex-wrap" style="gap:4px;">
            <v-chip class="mr-1" :color="filterStatus === 'all' ? 'primary' : ''" :outlined="filterStatus !== 'all'" small @click="filterStatus = 'all'">전체</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'AUTO' ? 'green' : ''" :outlined="filterStatus !== 'AUTO'" :text-color="filterStatus === 'AUTO' ? 'white' : ''" small @click="filterStatus = 'AUTO'">자동완성</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'PARTIAL' ? 'orange' : ''" :outlined="filterStatus !== 'PARTIAL'" :text-color="filterStatus === 'PARTIAL' ? 'white' : ''" small @click="filterStatus = 'PARTIAL'">부분매칭</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'FAILED' ? 'red' : ''" :outlined="filterStatus !== 'FAILED'" :text-color="filterStatus === 'FAILED' ? 'white' : ''" small @click="filterStatus = 'FAILED'">미매칭</v-chip>
            <v-chip class="mr-1" :color="filterStatus === 'REGISTERED' ? 'grey' : ''" :outlined="filterStatus !== 'REGISTERED'" :text-color="filterStatus === 'REGISTERED' ? 'white' : ''" small @click="filterStatus = 'REGISTERED'">기등록</v-chip>
            <v-chip v-if="countPost('FAIL') > 0" class="mr-1" :color="filterStatus === 'POST_FAIL' ? 'red' : ''" :outlined="filterStatus !== 'POST_FAIL'" :text-color="filterStatus === 'POST_FAIL' ? 'white' : ''" small @click="filterStatus = 'POST_FAIL'">등록실패</v-chip>

            <v-spacer></v-spacer>

            <!-- 표시 컬럼 토글 -->
            <v-menu offset-y :close-on-content-click="false">
              <template v-slot:activator="{ on, attrs }">
                <v-btn small text v-bind="attrs" v-on="on" class="mr-1 btn-wide">
                  <v-icon small left>mdi-table-eye</v-icon>표시 컬럼
                </v-btn>
              </template>
              <v-list dense>
                <v-list-item v-for="col in toggleableCols" :key="col.value">
                  <v-list-item-action class="mr-2">
                    <v-checkbox v-model="visibleCols" :value="col.value" hide-details></v-checkbox>
                  </v-list-item-action>
                  <v-list-item-title>{{ col.text }}</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>

            <v-btn small color="primary" class="white--text btn-wide-lg" @click="registerAll"
              :disabled="registerableCount === 0 || isRegistering" :loading="isRegistering">
              <v-icon left small>mdi-database-plus</v-icon>용어 등록 ({{ registerableCount }}건)
            </v-btn>
            <v-btn small outlined @click="currentStep = 1" class="ml-2 btn-wide">
              <v-icon left small>mdi-arrow-left</v-icon>다시 입력
            </v-btn>
          </v-card-actions>

          <!-- Review table -->
          <v-data-table
            :headers="visibleHeaders"
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

            <template v-slot:[`header.words`]>
              <span>구성단어</span>
              <v-chip x-small color="green" text-color="white" class="ml-2" style="height:16px; font-size:.6rem;">등록</v-chip>
              <v-chip x-small color="orange" text-color="white" style="height:16px; font-size:.6rem;">사전</v-chip>
              <v-chip x-small color="red" text-color="white" style="height:16px; font-size:.6rem;">미인식</v-chip>
            </template>
            <!-- Words display -->
            <template v-slot:[`item.words`]="{ item }">
              <span v-if="item.status === 'REGISTERED'">-</span>
              <span v-else>
                <v-chip v-for="(w, wi) in item.words" :key="wi" x-small
                  :color="w.status === 'MATCHED' ? 'green' : w.status === 'NEW' ? 'orange' : 'red'" text-color="white" class="mr-1">
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
                dense hide-details solo flat style="max-width:180px;font-size:0.8rem;"
                @change="onGridDomainChange(item)">
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
              <template v-else-if="item._postStatus === 'SUCCESS'">
                <v-chip small :color="isAdmin ? 'green' : 'blue'" text-color="white" class="chip-status">
                  <v-icon small left>mdi-check-circle</v-icon>{{ isAdmin ? '등록완료' : '등록신청 완료' }}
                </v-chip>
              </template>
              <template v-else-if="item._postStatus === 'SKIPPED'">
                <v-chip small color="grey" text-color="white" class="chip-status-sm">
                  <v-icon small left>mdi-information-outline</v-icon>이미 등록됨
                </v-chip>
              </template>
              <template v-else-if="item._postStatus === 'FAIL'">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-chip small color="red" text-color="white" v-bind="attrs" v-on="on" class="mr-1 chip-status-sm">
                      <v-icon small left>mdi-alert-circle</v-icon>등록실패
                    </v-chip>
                  </template>
                  <span style="font-size:.8rem;">{{ item._postMessage || '등록 중 오류' }}</span>
                </v-tooltip>
                <v-btn small color="orange" dark class="btn-cell" @click="editItem(item)">
                  <v-icon small left>mdi-pencil</v-icon>재수정
                </v-btn>
              </template>
              <template v-else-if="!isRowRegisterable(item)">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-chip small color="amber darken-2" text-color="white" v-bind="attrs" v-on="on" class="mr-1 chip-status-sm">
                      <v-icon small left>mdi-alert-outline</v-icon>{{ getBlockReason(item) }}
                    </v-chip>
                  </template>
                  <span style="font-size:.8rem;">{{ getBlockReasonDetail(item) }}</span>
                </v-tooltip>
                <v-btn small color="orange" dark class="btn-cell" @click="editItem(item)">
                  <v-icon small left>mdi-pencil</v-icon>수정
                </v-btn>
              </template>
              <template v-else>
                <v-btn small color="grey" outlined class="mr-1 btn-cell" @click="editItem(item)">
                  <v-icon small left>mdi-pencil</v-icon>수정
                </v-btn>
                <v-btn small color="primary" dark class="btn-cell"
                  :loading="item._registering" @click="registerOne(item)">
                  <v-icon small left>mdi-database-plus</v-icon>용어 등록
                </v-btn>
              </template>
            </template>
          </v-data-table>
        </v-card>
      </v-stepper-content>

    </v-stepper>

    <!-- Edit dialog for PARTIAL items -->
    <v-dialog v-model="editDialog" max-width="1000">
      <v-card v-if="editingItem">
        <v-card-title>단어 정보 수정</v-card-title>
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
                <th>한글명</th><th>영문약어</th><th>영문명</th><th>상태</th><th>등록</th><th style="width:40px;"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(w, wi) in currentEditWords" :key="wi">
                <td>
                  <v-text-field v-if="w.status === 'NEW' || w.status === 'UNRECOGNIZED'" v-model="w.wordNm" dense hide-details
                    style="max-width:100px" @input="onWordNmInput(w)" @change="onWordNmInput(w)"
                    @compositionend="onWordNmInput(w)"></v-text-field>
                  <span v-else>{{ w.wordNm }}
                    <v-btn icon x-small @click="unlockWord(w)" title="수정" class="ml-1">
                      <v-icon x-small>mdi-pencil</v-icon>
                    </v-btn>
                  </span>
                </td>
                <td>
                  <v-text-field v-if="w.status === 'NEW' || w.status === 'UNRECOGNIZED'" v-model="w.newWord.wordEngAbrvNm" dense
                    :hide-details="!w._abrvDupMsg && !w._dictHint"
                    :error-messages="w._abrvDupMsg || ''"
                    :messages="(!w._abrvDupMsg && w._dictHint) ? w._dictHint : ''"
                    @input="w.newWord.wordEngAbrvNm = (w.newWord.wordEngAbrvNm || '').toUpperCase()"
                    style="max-width:120px"></v-text-field>
                  <span v-else>{{ w.selected && w.selected.wordEngAbrvNm || '-' }}</span>
                </td>
                <td>
                  <v-text-field v-if="w.status === 'NEW' || w.status === 'UNRECOGNIZED'" v-model="w.newWord.wordEngNm" dense hide-details
                    style="max-width:150px"></v-text-field>
                  <span v-else>{{ w.selected && w.selected.wordEngNm || '-' }}</span>
                </td>
                <td>
                  <v-chip v-if="(w.status === 'UNRECOGNIZED' || w.status === 'NEW') && !w._registered" x-small color="orange" text-color="white">신규</v-chip>
                  <v-chip v-else x-small color="green" text-color="white">등록됨</v-chip>
                </td>
                <td>
                  <v-btn v-if="(w.status === 'NEW' || w.status === 'UNRECOGNIZED') && !w._registered" small color="primary"
                    class="btn-cell" :loading="w._registering"
                    @click="registerSingleWord(w)">단어등록</v-btn>
                  <v-icon v-if="w._registered" small color="green">mdi-check-circle</v-icon>
                </td>
                <td>
                  <v-btn icon x-small color="red" @click="removeEditWord(wi)" title="삭제">
                    <v-icon x-small>mdi-close</v-icon>
                  </v-btn>
                </td>
              </tr>
            </tbody>
          </v-simple-table>
          <div class="d-flex align-center flex-wrap mt-2" style="gap:8px;">
            <v-text-field v-model="newWordInput" dense outlined hide-details placeholder="추가할 한글 단어명"
              style="max-width:200px;" @keyup.enter="addEditWord"></v-text-field>
            <v-btn small color="primary" outlined class="btn-cell" @click="addEditWord">
              <v-icon small left>mdi-plus</v-icon>단어 추가
            </v-btn>
            <v-divider vertical class="mx-1"></v-divider>
            <v-autocomplete v-model="selectedClsfWord" :items="classWords" :item-text="clsfItemText"
              return-object dense outlined hide-details clearable placeholder="형식단어 검색/선택"
              :loading="loadingClsfWords" :menu-props="{ maxHeight: 320 }"
              no-data-text="일치하는 형식단어 없음"
              style="max-width:260px;">
              <template v-slot:selection="{ item }">
                <span>{{ item.wordNm }}</span>
                <span v-if="item.domainClsfNm" style="font-size:.75rem; color:#9E9E9E; margin-left:4px;">({{ item.domainClsfNm }})</span>
              </template>
              <template v-slot:item="{ item }">
                <span>{{ item.wordNm }}</span>
                <span v-if="item.domainClsfNm" style="font-size:.75rem; color:#9E9E9E; margin-left:6px;">[{{ item.domainClsfNm }}]</span>
              </template>
            </v-autocomplete>
            <v-btn small color="indigo" dark class="btn-wide" :disabled="!selectedClsfWord" @click="addClassificationWord">
              <v-icon small left>mdi-plus</v-icon>형식단어 추가
            </v-btn>
          </div>

          <!-- 용어 도메인 (마지막 단어 기준 독립 편집) -->
          <div class="d-flex align-center flex-wrap mt-3" style="gap:8px;">
            <span style="font-size:.85rem; color:#546E7A; font-weight:600; min-width:140px;">
              용어 도메인 (마지막 단어 기준):
            </span>
            <v-select v-model="selectedDomain" :items="domainsInClsf" item-text="domainNm"
              return-object dense outlined hide-details placeholder="도메인 선택"
              :disabled="!lastWordDomainClsfNm" :loading="loadingDomains" :menu-props="{ maxHeight: 320 }"
              style="max-width:280px;" @change="onDomainChange">
              <template v-slot:selection="{ item }">
                <span style="font-size:.85rem;">{{ item.domainNm }}</span>
                <span v-if="item.dataType" style="font-size:.75rem; color:#9E9E9E; margin-left:4px;">({{ domainTypeLabel(item) }})</span>
              </template>
              <template v-slot:item="{ item }">
                <span>{{ item.domainNm }}</span>
                <span v-if="item.dataType" style="font-size:.75rem; color:#9E9E9E; margin-left:6px;">{{ domainTypeLabel(item) }}</span>
              </template>
            </v-select>
            <span v-if="!lastWordDomainClsfNm" style="font-size:.75rem; color:#B71C1C;">
              마지막 단어가 형식단어가 아니거나 도메인 분류가 지정되지 않았습니다.
            </span>
          </div>
          <!-- 실시간 조합 미리보기 -->
          <v-sheet class="mt-3 pa-3" style="background:#F5F7FA; border-radius:8px;">
            <div class="d-flex align-center" style="gap:8px;">
              <span style="font-size:.8rem; color:#546E7A; font-weight:600;">용어 미리보기:</span>
              <span style="font-size:.95rem; font-weight:700; color:#1A237E;">{{ editPreviewKor }}</span>
              <span style="font-size:.8rem; color:#9E9E9E;">→</span>
              <span style="font-size:.95rem; font-weight:700; color:#3F51B5;">{{ editPreviewEng }}</span>
            </div>
          </v-sheet>
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
      isAnalyzing: false,
      isRegistering: false,

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
      newWordInput: '',  // 단어 추가 입력

      // 분류어/도메인 선택 (수정 모달 cascade)
      classWords: [],           // [{wordId, wordNm, wordEngAbrvNm, wordEngNm, domainClsfNm}]
      domainsInClsf: [],        // 선택된 분류어의 도메인 리스트
      selectedClsfWord: null,   // 현재 선택된 분류어 (object)
      selectedDomain: null,     // 현재 선택된 도메인 (object)
      loadingClsfWords: false,
      loadingDomains: false,

      // 등록 결과 (요약용 — STEP 4 제거됨)
      registerResult: null,

      // 권한 (등록완료/등록신청 라벨 분기)
      isAdmin: false,

      // 표시 컬럼 토글
      visibleCols: ['words', 'recommendedEngAbrvNm', 'recommendedDomainNm', 'typeLen'],
      toggleableCols: [
        { text: '구성단어', value: 'words' },
        { text: '영문약어', value: 'recommendedEngAbrvNm' },
        { text: '도메인', value: 'recommendedDomainNm' },
        { text: '타입/길이', value: 'typeLen' },
      ],

      reviewHeaders: [
        { text: '한글명', value: 'inputNm', width: '14%' },
        { text: '상태', value: 'status', width: '8%' },
        { text: '구성단어', value: 'words', width: '17%', sortable: false, _toggle: true },
        { text: '영문약어', value: 'recommendedEngAbrvNm', width: '14%', _toggle: true },
        { text: '도메인', value: 'recommendedDomainNm', width: '13%', _toggle: true },
        { text: '타입/길이', value: 'typeLen', width: '10%', sortable: false, _toggle: true },
        { text: '액션', value: 'action', width: '24%', sortable: false, align: 'center' },
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
    editPreviewKor: function() {
      return this.currentEditWords.map(function(w) { return w.wordNm || ''; }).join('');
    },
    editPreviewEng: function() {
      var parts = [];
      for (var i = 0; i < this.currentEditWords.length; i++) {
        var w = this.currentEditWords[i];
        if (w.status === 'MATCHED' && w.selected) {
          parts.push(w.selected.wordEngAbrvNm || '?');
        } else if (w.newWord && w.newWord.wordEngAbrvNm) {
          parts.push(w.newWord.wordEngAbrvNm);
        } else {
          parts.push('?');
        }
      }
      return parts.join('_');
    },
    filteredResults: function() {
      var self = this;
      if (self.filterStatus === 'all') return self.analysisResults;
      if (self.filterStatus === 'POST_FAIL') {
        return self.analysisResults.filter(function(r) { return r._postStatus === 'FAIL'; });
      }
      return self.analysisResults.filter(function(r) { return r.status === self.filterStatus; });
    },
    visibleHeaders: function() {
      var self = this;
      return self.reviewHeaders.filter(function(h) {
        return !h._toggle || self.visibleCols.indexOf(h.value) !== -1;
      });
    },
    registerableCount: function() {
      var self = this;
      return this.analysisResults.filter(function(r) { return self.isRowRegisterable(r); }).length;
    },
    // 마지막 단어의 domainClsfNm (도메인 드롭다운 기준)
    lastWordDomainClsfNm: function() {
      var ws = this.currentEditWords;
      if (!ws || ws.length === 0) return '';
      var last = ws[ws.length - 1];
      if (!last) return '';
      if (last.selected && last.selected.domainClsfNm) return last.selected.domainClsfNm;
      if (last.newWord && last.newWord.domainClsfNm) return last.newWord.domainClsfNm;
      return '';
    },
  },
  watch: {
    // 마지막 단어가 바뀔 때마다 도메인 목록 재로드
    lastWordDomainClsfNm: function(newVal) {
      if (!this.editDialog) return;
      if (!newVal) {
        this.domainsInClsf = [];
        this.selectedDomain = null;
        return;
      }
      this.loadDomainsForLastWord(newVal);
    },
  },
  methods: {
    /** 86번 #11 — 백엔드 raw exception 차단, 친화적 메세지 변환 */
    _friendlyErrText: function(err, fallback) {
      var status = (err && err.response && err.response.status) || 0;
      var data   = (err && err.response && err.response.data) || {};
      var our    = data.resultMessage;
      var raw    = data.message;
      var rawIsTechnical = raw && /JSON|deserialize|parse|MismatchedInput|HttpMessageNotReadable|Exception|NullPointer|invalid|cannot/i.test(raw);
      if (our) return our;
      if (raw && !rawIsTechnical) return raw;
      if (status >= 500) return (fallback || '서버 처리 중 오류가 발생했습니다.') + ' (관리자에게 문의해 주세요)';
      if (status === 400) return (fallback || '입력값이 올바르지 않습니다.');
      if (status === 401 || status === 403) return '권한이 없습니다.';
      if (status === 404) return '요청한 자원을 찾을 수 없습니다.';
      return fallback || (err && err.message) || '알 수 없는 오류가 발생했습니다.';
    },
    downloadTemplate: function() {
      var self = this;
      axios.get(self.$APIURL.base + 'api/std/downloadTermRecommendTemplate', {
        responseType: 'blob'
      }).then(function(res) {
        var blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
        var url = window.URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = '표준화추천_양식.xlsx';
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
      }).catch(function() {
        self.$swal.fire({ title: '양식 다운로드 실패', icon: 'error', confirmButtonText: '확인' });
      });
    },
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
      if (self.isAnalyzing) return;
      self.isAnalyzing = true;
      self.currentStep = 2;
      self.analysisResults = [];
      self.selectedItems = [];

      axios.post(self.$APIURL.base + 'api/std/analyzeTermsBatch', {
        termNames: self.parsedNames
      }).then(function(res) {
        if (res.data) {
          self.analysisResults = res.data;
          // 88번 §16 — 사용자가 처음 입력한 한글을 _originalInput 에 보존 (이후 단어 교체로 inputNm 갱신돼도 원본 유지)
          self.analysisResults.forEach(function(r, idx) {
            r._originalInput = self.parsedNames[idx] || r.inputNm;
          });
        }
        self.currentStep = 3;
      }).catch(function(err) {
        self.$swal.fire({ title: '분석 실패', text: self._friendlyErrText(err, '분석 중 오류가 발생했습니다.'), icon: 'error', confirmButtonText: '확인' });
        self.currentStep = 1;
      }).finally(function() {
        self.isAnalyzing = false;
      });
    },
    countByStatus: function(status) {
      return this.analysisResults.filter(function(r) { return r.status === status; }).length;
    },
    countPost: function(status) {
      return this.analysisResults.filter(function(r) { return r._postStatus === status; }).length;
    },
    statusColor: function(status) {
      var map = { 'REGISTERED': 'grey', 'AUTO': 'green', 'PARTIAL': 'orange', 'FAILED': 'red' };
      return map[status] || 'grey';
    },
    statusText: function(status) {
      var map = { 'REGISTERED': '기등록', 'AUTO': '자동완성', 'PARTIAL': '부분매칭', 'FAILED': '미매칭' };
      return map[status] || status;
    },
    /**
     * 그리드 인라인 도메인 v-select 변경 시 type/len 도 같이 갱신
     */
    onGridDomainChange: function(item) {
      if (!item || !item.domainCandidates) return;
      var nm = item.recommendedDomainNm;
      var cand = item.domainCandidates.find(function(d) { return d.domainNm === nm; });
      if (cand) {
        this.$set(item, 'recommendedDomainId', cand.domainId);
        this.$set(item, 'recommendedDataType', cand.dataType);
        this.$set(item, 'recommendedDataLen', cand.dataLen);
        this.$set(item, 'recommendedDataDecimalLen', cand.dataDecimalLen);
      }
    },
    /**
     * 등록 가능 판정 — 모든 단어가 MATCHED + 도메인 지정 + 미등록/미실패 상태
     * (수정 모달 거치지 않아도 데이터가 충족되면 즉시 등록 가능)
     */
    isRowRegisterable: function(r) {
      if (!r) return false;
      if (r.status === 'REGISTERED') return false;
      if (r._postStatus === 'SUCCESS' || r._postStatus === 'SKIPPED') return false;
      if (!r.words || !r.words.length) return false;
      for (var i = 0; i < r.words.length; i++) {
        if (r.words[i].status !== 'MATCHED') return false;
      }
      if (!r.recommendedDomainNm || !String(r.recommendedDomainNm).trim()) return false;
      return true;
    },
    /**
     * 등록 불가 사유 — 칩에 짧게 표시
     */
    getBlockReason: function(r) {
      if (!r || !r.words || !r.words.length) return '단어 미분석';
      var hasNew = false, hasUnrec = false;
      for (var i = 0; i < r.words.length; i++) {
        if (r.words[i].status === 'NEW') hasNew = true;
        else if (r.words[i].status === 'UNRECOGNIZED') hasUnrec = true;
      }
      if (hasUnrec) return '단어 미인식';
      if (hasNew) return '단어 미등록';
      if (!r.recommendedDomainNm || !String(r.recommendedDomainNm).trim()) return '도메인 미지정';
      return '수정 필요';
    },
    /**
     * 등록 불가 사유 상세 — 툴팁용
     */
    getBlockReasonDetail: function(r) {
      if (!r) return '';
      var msgs = [];
      var newWords = [], unrecWords = [];
      if (r.words) {
        for (var i = 0; i < r.words.length; i++) {
          var w = r.words[i];
          if (w.status === 'NEW') newWords.push(w.wordNm);
          else if (w.status === 'UNRECOGNIZED') unrecWords.push(w.wordNm);
        }
      }
      if (unrecWords.length) msgs.push('미인식 단어: ' + unrecWords.join(', '));
      if (newWords.length) msgs.push('미등록 단어 (등록 필요): ' + newWords.join(', '));
      if (!r.recommendedDomainNm || !String(r.recommendedDomainNm).trim()) msgs.push('도메인이 지정되지 않았습니다.');
      if (!msgs.length) msgs.push('[수정] 으로 보완해주세요.');
      return msgs.join('\n');
    },
    summarizeSplit: function(words) {
      if (!words) return '';
      return words.map(function(w) { return w.wordNm; }).join('+');
    },
    onWordNmInput: function(w) {
      var self = this;
      var nm = (w.wordNm || '').trim();
      if (!nm || nm.length < 1) return;
      // 같은 값 재요청 방지
      if (w._lastLookup === nm) return;
      // 디바운스: 이전 타이머 취소
      if (w._dictTimer) clearTimeout(w._dictTimer);
      w._dictTimer = setTimeout(function() {
        w._lastLookup = nm;
        // 경량 API: TB_WORD + TB_WORD_DICT 동시 조회
        axios.get(self.$APIURL.base + 'api/std/lookupWord', { params: { wordNm: nm } }).then(function(res) {
          var data = res.data;
          if (data && data.found) {
            if (data.source === 'WORD') {
              // TB_WORD에 등록된 단어 → MATCHED로 전환
              self.$set(w, 'status', 'MATCHED');
              self.$set(w, 'selected', {
                wordId: data.wordId,
                wordNm: data.wordNm,
                wordEngAbrvNm: data.wordEngAbrvNm,
                wordEngNm: data.wordEngNm,
                domainClsfNm: data.domainClsfNm || '',
                wordClsfYn: data.wordClsfYn || 'N'
              });
              self.$set(w, 'candidates', [w.selected]);
              self.$set(w, '_registered', true);
              self.$set(w, '_dictHint', '');
              self.$set(w, '_abrvDupMsg', '');
              self.recalcItemStatus(self.editingItem);
            } else {
              // DICT에서 발견 → 영문약어/영문명 자동입력
              if (!w.newWord) self.$set(w, 'newWord', {});
              self.$set(w.newWord, 'wordEngAbrvNm', data.wordEngAbrvNm || '');
              self.$set(w.newWord, 'wordEngNm', data.wordEngNm || '');
              self.$set(w, '_dictHint', "'" + nm + "' 사전 추천: " + (data.wordEngAbrvNm || ''));
              // 영문약어 중복 경고
              if (data.abrvDuplicate) {
                self.$set(w, '_abrvDupMsg', data.abrvDuplicateMsg);
                self.$set(w, '_dictHint', '');
              } else {
                self.$set(w, '_abrvDupMsg', '');
              }
            }
          } else {
            // 미발견 → 영문 필드 초기화
            if (!w.newWord) self.$set(w, 'newWord', {});
            self.$set(w.newWord, 'wordEngAbrvNm', '');
            self.$set(w.newWord, 'wordEngNm', '');
            self.$set(w, '_dictHint', '');
            self.$set(w, '_abrvDupMsg', '');
          }
        }).catch(function(err) {
          console.error('lookupWord error:', err);
        });
      }, 400);
    },
    unlockWord: function(w) {
      // MATCHED 상태를 NEW로 전환하여 다시 편집 가능하게
      var engAbrv = (w.selected && w.selected.wordEngAbrvNm) || '';
      var engNm = (w.selected && w.selected.wordEngNm) || '';
      this.$set(w, 'status', 'NEW');
      this.$set(w, 'selected', null);
      this.$set(w, 'candidates', []);
      this.$set(w, '_registered', false);
      this.$set(w, '_lastLookup', '');
      if (!w.newWord) this.$set(w, 'newWord', {});
      this.$set(w.newWord, 'wordEngAbrvNm', engAbrv);
      this.$set(w.newWord, 'wordEngNm', engNm);
      this.recalcItemStatus(this.editingItem);
    },
    removeEditWord: function(index) {
      var words = this.currentEditWords;
      if (words.length <= 1) {
        this.$swal.fire({ title: '최소 1개 단어는 필요합니다.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      words.splice(index, 1);
      this.recalcItemStatus(this.editingItem);
    },
    addEditWord: function() {
      var nm = (this.newWordInput || '').trim();
      if (!nm) {
        this.$swal.fire({ title: '추가할 한글 단어명을 입력해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      var self = this;
      var newWord = {
        wordNm: nm,
        status: 'UNRECOGNIZED',
        candidates: [],
        selected: null,
        newWord: { wordEngAbrvNm: '', wordEngNm: '', domainClsfNm: '' }
      };
      // TB_WORD에서 먼저 조회
      axios.get(self.$APIURL.base + 'api/std/getWordInfoByNm', { params: { wordNm: nm } }).then(function(res) {
        var info = res.data;
        if (info && info.length > 0) {
          // 등록된 단어
          newWord.status = 'MATCHED';
          newWord.selected = {
            wordId: info[0].id,
            wordNm: info[0].wordNm,
            wordEngAbrvNm: info[0].wordEngAbrvNm,
            wordEngNm: info[0].wordEngNm,
            domainClsfNm: info[0].domainClsfNm || ''
          };
          newWord.candidates = [newWord.selected];
          self.currentEditWords.push(newWord);
          self.newWordInput = '';
          self.recalcItemStatus(self.editingItem);
          self.$swal.fire({ title: '등록된 단어입니다.', text: nm + ' → ' + info[0].wordEngAbrvNm, icon: 'success', timer: 1500, confirmButtonText: '확인' });
        } else {
          // 미등록 → DICT에서 추천 조회
          axios.post(self.$APIURL.base + 'api/std/analyzeTermsBatch', { termNames: [nm] }).then(function(res2) {
            var data = res2.data;
            if (data && data.length > 0 && data[0].words && data[0].words.length > 0) {
              var w = data[0].words[0];
              if (w.newWord) {
                newWord.newWord.wordEngAbrvNm = w.newWord.wordEngAbrvNm || '';
                newWord.newWord.wordEngNm = w.newWord.wordEngNm || '';
              }
            }
            self.currentEditWords.push(newWord);
            self.newWordInput = '';
            self.recalcItemStatus(self.editingItem);
            if (newWord.newWord.wordEngAbrvNm) {
              self.$swal.fire({ title: '사전 기반 자동 추천', text: nm + ' → ' + newWord.newWord.wordEngAbrvNm + ' (수정 가능)', icon: 'info', timer: 2000, confirmButtonText: '확인' });
            }
          }).catch(function() {
            self.currentEditWords.push(newWord);
            self.newWordInput = '';
            self.recalcItemStatus(self.editingItem);
          });
        }
      }).catch(function() {
        self.currentEditWords.push(newWord);
        self.newWordInput = '';
        self.recalcItemStatus(self.editingItem);
      });
    },
    editItem: function(item) {
      this.editingItem = item;
      this.editSplitMode = 0;
      this.newWordInput = '';
      this.selectedClsfWord = null;
      this.selectedDomain = null;
      this.domainsInClsf = [];
      this.editDialog = true;
      this.loadClassificationWords();
      // 기존 last word 기준 도메인 드롭다운 초기 로드
      var clsfNm = this.lastWordDomainClsfNm;
      if (clsfNm) this.loadDomainsForLastWord(clsfNm);
    },
    clsfItemText: function(item) {
      // autocomplete 검색 대상: 한글명 + 분류명 둘 다 매칭되도록
      return (item.wordNm || '') + ' ' + (item.domainClsfNm || '');
    },
    domainTypeLabel: function(d) {
      if (!d || !d.dataType) return '';
      var s = d.dataType;
      if (d.dataLen) {
        s += '(' + d.dataLen;
        if (d.dataDecimalLen && Number(d.dataDecimalLen) > 0) s += ',' + d.dataDecimalLen;
        s += ')';
      }
      return s;
    },
    loadClassificationWords: function() {
      var self = this;
      if (self.classWords.length > 0) return; // 세션 캐시
      self.loadingClsfWords = true;
      axios.get(self.$APIURL.base + 'api/std/getClassificationWords').then(function(res) {
        self.classWords = res.data || [];
      }).catch(function() {
        self.$swal.fire({ title: '형식단어 조회 실패', icon: 'error', confirmButtonText: '확인' });
      }).finally(function() {
        self.loadingClsfWords = false;
      });
    },
    loadDomainsForLastWord: function(clsfNm) {
      var self = this;
      if (!clsfNm) { self.domainsInClsf = []; self.selectedDomain = null; return; }
      self.loadingDomains = true;
      axios.get(self.$APIURL.base + 'api/std/getDomainsByClsf', {
        params: { domainClsfNm: clsfNm }
      }).then(function(res) {
        self.domainsInClsf = res.data || [];
        // 기존 editingItem 의 추천 도메인이 현재 목록에 있으면 preselect, 없으면 첫번째
        var match = null;
        if (self.editingItem && self.editingItem.recommendedDomainNm) {
          match = self.domainsInClsf.find(function(d) { return d.domainNm === self.editingItem.recommendedDomainNm; });
        }
        self.selectedDomain = match || (self.domainsInClsf.length > 0 ? self.domainsInClsf[0] : null);
      }).catch(function() {
        self.$swal.fire({ title: '도메인 조회 실패', icon: 'error', confirmButtonText: '확인' });
      }).finally(function() {
        self.loadingDomains = false;
      });
    },
    onDomainChange: function() {
      if (!this.selectedDomain || !this.editingItem) return;
      this.editingItem.recommendedDomainId = this.selectedDomain.domainId;
      this.editingItem.recommendedDomainNm = this.selectedDomain.domainNm;
      this.editingItem.recommendedDataType = this.selectedDomain.dataType;
      this.editingItem.recommendedDataLen = this.selectedDomain.dataLen;
      this.editingItem.recommendedDataDecimalLen = this.selectedDomain.dataDecimalLen;
      this.recalcItemStatus(this.editingItem);
    },
    addClassificationWord: function() {
      var self = this;
      if (!self.selectedClsfWord) {
        self.$swal.fire({ title: '형식단어를 선택해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      var c = self.selectedClsfWord;
      // addEditWord 의 MATCHED 분기와 동일 구조로 push. 도메인은 watcher 가 자동 연결
      var newWord = {
        wordNm: c.wordNm,
        status: 'MATCHED',
        selected: {
          wordId: c.wordId,
          wordNm: c.wordNm,
          wordEngAbrvNm: c.wordEngAbrvNm,
          wordEngNm: c.wordEngNm,
          domainClsfNm: c.domainClsfNm || ''
        },
        candidates: [{
          wordId: c.wordId,
          wordNm: c.wordNm,
          wordEngAbrvNm: c.wordEngAbrvNm,
          wordEngNm: c.wordEngNm,
          domainClsfNm: c.domainClsfNm || ''
        }],
        newWord: { wordEngAbrvNm: '', wordEngNm: '', domainClsfNm: '' }
      };
      self.currentEditWords.push(newWord);
      self.recalcItemStatus(self.editingItem);
      // 분류어 입력 드롭다운 초기화 (도메인은 watcher 가 lastWord 변경 감지해서 자동 반영)
      self.selectedClsfWord = null;
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
            domainClsfNm: res.data.domainClsfNm || '',
            wordClsfYn: res.data.wordClsfYn || 'N'
          };
          w.candidates = [w.selected];
          // 전체 상태 재판정
          self.recalcItemStatus(self.editingItem);
          var msg = res.data.alreadyExists ? '이미 등록된 단어입니다. 기존 정보를 사용합니다.' : '단어가 등록되었습니다.';
          if (res.data.warning) {
            self.$swal.fire({ title: '완료', text: msg + '\n\n' + res.data.warning, icon: 'warning', confirmButtonText: '확인' });
          } else {
            self.$swal.fire({ title: '완료', text: msg, icon: 'success', confirmButtonText: '확인', timer: 1500 });
          }
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

      // 2순위 선택 시 item.words를 alternativeWords로 교체
      if (this.editSplitMode === 1 && item.alternativeWords && item.alternativeWords.length) {
        item.words = item.alternativeWords;
        item.alternativeWords = null;
      }

      var words = this.currentEditWords;
      if (!words || words.length === 0) return;

      // 검증: 모든 단어가 영문약어를 가지고 있는지 체크
      var missingAbrv = [];
      var unregistered = [];
      for (var j = 0; j < words.length; j++) {
        var w = words[j];
        var abrv = '';
        if (w.status === 'MATCHED' && w.selected) {
          abrv = w.selected.wordEngAbrvNm || '';
        } else {
          abrv = (w.newWord && w.newWord.wordEngAbrvNm) || '';
          if (!w._registered) {
            unregistered.push(w.wordNm);
          }
        }
        if (!abrv.trim()) {
          missingAbrv.push(w.wordNm);
        }
      }

      if (missingAbrv.length > 0) {
        this.$swal.fire({
          title: '영문약어 누락',
          text: '다음 단어의 영문약어를 입력해주세요: ' + missingAbrv.join(', '),
          icon: 'warning', confirmButtonText: '확인'
        });
        return;
      }

      if (unregistered.length > 0) {
        this.$swal.fire({
          title: '미등록 단어',
          text: '다음 단어를 먼저 [단어등록] 해주세요: ' + unregistered.join(', '),
          icon: 'warning', confirmButtonText: '확인'
        });
        return;
      }

      // 형식단어 검증: 마지막 단어가 형식단어(WORD_CLSF_YN='Y')인지 서버에서 확인
      if (words.length >= 2) {
        var lastW = words[words.length - 1];
        var lastWordId = null;
        if (lastW.status === 'MATCHED' && lastW.selected) {
          lastWordId = lastW.selected.wordId;
        }
        if (lastWordId) {
          var self = this;
          axios.get(self.$APIURL.base + 'api/std/getWordInfoById', { params: { wordId: lastWordId } }).then(function(res) {
            var info = res.data;
            if (info && info.length > 0 && info[0].wordClsfYn === 'Y') {
              self.doConfirmEditFinish(item, words);
            } else {
              self.$swal.fire({
                title: '형식단어 필요',
                text: '용어의 마지막 단어는 형식단어여야 합니다. (현재: ' + lastW.wordNm + ')\n명, 코드, 번호, 일자 등 형식단어를 마지막에 배치해주세요.',
                icon: 'warning', confirmButtonText: '확인'
              });
            }
          });
          return; // 비동기이므로 여기서 중단, 콜백에서 계속
        } else {
          // 마지막 단어가 미등록이면 형식단어 아님
          this.$swal.fire({
            title: '형식단어 필요',
            text: '용어의 마지막 단어는 형식단어여야 합니다. (현재: ' + lastW.wordNm + ')\n명, 코드, 번호, 일자 등 형식단어를 마지막에 배치해주세요.',
            icon: 'warning', confirmButtonText: '확인'
          });
          return;
        }
      }

      this.doConfirmEditFinish(item, words);
    },
    doConfirmEditFinish: function(item, words) {
      // 한글명 재조합 (구성단어 이어붙이기)
      var korParts = [];
      var engParts = [];
      for (var i = 0; i < words.length; i++) {
        var wd = words[i];
        korParts.push(wd.wordNm || '');
        if (wd.status === 'MATCHED' && wd.selected) {
          engParts.push(wd.selected.wordEngAbrvNm);
        } else if (wd.newWord && wd.newWord.wordEngAbrvNm) {
          engParts.push(wd.newWord.wordEngAbrvNm);
        }
      }
      item.inputNm = korParts.join('');
      item.termName = korParts.join('');
      item.recommendedEngAbrvNm = engParts.join('_');

      // [버그 fix] selectedDomain 이 자동 set 된 경우 @change 가 발화 안 해서
      // editingItem.recommendedDomain* 가 옛 분석값으로 남아있는 문제 — 확인 시 강제 동기화.
      if (this.selectedDomain) {
        item.recommendedDomainId = this.selectedDomain.domainId;
        item.recommendedDomainNm = this.selectedDomain.domainNm;
        item.recommendedDataType = this.selectedDomain.dataType;
        item.recommendedDataLen = this.selectedDomain.dataLen;
        item.recommendedDataDecimalLen = this.selectedDomain.dataDecimalLen;
      }
      // grid 의 도메인 인라인 v-select 가 모달 수정 후에도 작동하도록
      // domainCandidates 를 새 분류어 기준 도메인 목록으로 갱신
      this.$set(item, 'domainCandidates', this.domainsInClsf ? this.domainsInClsf.slice() : []);

      // 등록 결과 잔존 상태 초기화 (재수정 시)
      this.$set(item, '_postStatus', null);
      this.$set(item, '_postMessage', '');
      this.editDialog = false;
    },
    /**
     * 한 row 의 등록 페이로드 빌드 (registerAll/registerOne 공용)
     */
    buildItemPayload: function(r) {
      var wordIds = [];
      var newWords = [];
      if (r.words) {
        for (var j = 0; j < r.words.length; j++) {
          var w = r.words[j];
          if (w.status === 'MATCHED' && w.selected) {
            wordIds.push({ wordId: w.selected.wordId, wordOrd: j });
          } else if ((w.status === 'NEW' || w.status === 'UNRECOGNIZED') && w._registered && w.selected) {
            wordIds.push({ wordId: w.selected.wordId, wordOrd: j });
          } else if ((w.status === 'NEW' || w.status === 'UNRECOGNIZED') && w.newWord) {
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
      return {
        termsNm: r.inputNm,
        termsEngAbrvNm: r.recommendedEngAbrvNm,
        termsDesc: r.inputNm,
        domainNm: r.recommendedDomainNm || '',
        words: wordIds,
        newWords: newWords,
        // 88번 §16 — 변환 이력용: 사용자가 처음 입력한 원본 한글 (단어 교체 전)
        originalInput: r._originalInput || r.inputNm
      };
    },
    /**
     * details[] 응답을 analysisResults 의 row 에 매핑하여 _postStatus/_postMessage 갱신
     */
    applyDetailsToRows: function(details, targetRows) {
      var self = this;
      if (!details || !details.length) return;
      for (var i = 0; i < details.length; i++) {
        var d = details[i];
        var row = targetRows.find(function(r) { return r.inputNm === d.termsNm; });
        if (!row) continue;
        self.$set(row, '_postStatus', d.status);
        self.$set(row, '_postMessage', d.message || '');
        // 등록(또는 이미 존재)된 row 는 isRowRegisterable 이 false 가 되어 더 이상 등록 불가
      }
    },
    registerAll: function() {
      var self = this;
      var targetRows = self.analysisResults.filter(function(r) { return self.isRowRegisterable(r); });
      var items = targetRows.map(function(r) { return self.buildItemPayload(r); });

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

        self.isRegistering = true;
        axios.post(self.$APIURL.base + 'api/std/registerTermsBatch', { items: items })
          .then(function(res) {
            self.registerResult = res.data;
            self.applyDetailsToRows(res.data && res.data.details, targetRows);
            var rt = (res.data && res.data.registeredTerms) || 0;
            var sk = (res.data && res.data.skipped) || 0;
            var fl = (res.data && res.data.failed) || 0;
            var lbl = self.isAdmin ? '등록' : '등록 신청';
            self.$swal.fire({
              title: '처리 완료',
              html: lbl + ' ' + rt + '건 / 기등록 ' + sk + '건 / 실패 ' + fl + '건',
              icon: fl > 0 ? 'warning' : 'success',
              confirmButtonText: '확인'
            });
          }).catch(function(err) {
            self.$swal.fire({ title: '등록 실패', text: self._friendlyErrText(err, '등록 중 오류가 발생했습니다.'), icon: 'error', confirmButtonText: '확인' });
          }).finally(function() {
            self.isRegistering = false;
          });
      });
    },
    /**
     * row 1 건만 등록
     */
    registerOne: function(item) {
      var self = this;
      if (!self.isRowRegisterable(item)) {
        self.$swal.fire({ title: '등록 불가', text: self.getBlockReasonDetail(item), icon: 'info', confirmButtonText: '확인' });
        return;
      }
      var payload = self.buildItemPayload(item);
      self.$set(item, '_registering', true);
      axios.post(self.$APIURL.base + 'api/std/registerTermsBatch', { items: [payload] })
        .then(function(res) {
          self.applyDetailsToRows(res.data && res.data.details, [item]);
          var d = (res.data && res.data.details && res.data.details[0]) || {};
          if (d.status === 'SUCCESS') {
            self.$swal.fire({
              title: self.isAdmin ? '등록 완료' : '등록 신청 완료',
              icon: 'success', confirmButtonText: '확인', timer: 1500
            });
          } else if (d.status === 'SKIPPED') {
            self.$swal.fire({ title: '이미 등록된 용어입니다.', icon: 'info', confirmButtonText: '확인' });
          } else {
            self.$swal.fire({ title: '등록 실패', text: d.message || '오류', icon: 'error', confirmButtonText: '확인' });
          }
        }).catch(function(err) {
          self.$swal.fire({ title: '등록 실패', text: self._friendlyErrText(err, '등록 중 오류가 발생했습니다.'), icon: 'error', confirmButtonText: '확인' });
        }).finally(function() {
          self.$set(item, '_registering', false);
        });
    },
    resetAll: function() {
      this.currentStep = 1;
      this.inputText = '';
      this.fileName = '';
      this.analysisResults = [];
      this.selectedItems = [];
      this.filterStatus = 'all';
      this.registerResult = null;
    },
  },
  mounted: function() {
    var self = this;
    var uid = self.$loginStatusData && self.$loginStatusData.id;
    axios.get(self.$APIURL.base + 'api/login/isAdmin', { params: { user: uid } })
      .then(function(res) { self.isAdmin = res.data === true; })
      .catch(function() { self.isAdmin = false; });
  },
};
</script>

<style scoped>
/* 버튼 글씨 잘림 방지 — 한글 라벨이 길어도 펼쳐지게 */
::v-deep .v-btn { letter-spacing: 0 !important; }
::v-deep .v-btn__content { white-space: nowrap; }
.btn-wide { min-width: 110px !important; padding: 0 14px !important; }
.btn-wide-lg { min-width: 140px !important; padding: 0 16px !important; }
.btn-cell { min-width: 64px !important; padding: 0 8px !important; }
/* 액션 컬럼 칩: 한글이 길어도 안 잘리게 */
.chip-status { min-width: 110px; justify-content: center; padding: 0 10px !important; }
.chip-status-sm { min-width: 90px; justify-content: center; padding: 0 8px !important; }
::v-deep .chip-status .v-chip__content,
::v-deep .chip-status-sm .v-chip__content { white-space: nowrap; }
</style>
