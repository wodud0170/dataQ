<template>
  <v-container fluid class="pa-2" style="height: 100%; overflow: auto;">
    <!-- 필터 바 1행: 진단 이력, 이슈 여부, 진단유형 -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-1" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span class="filterLabel">진단 이력</span>
      <v-select
        v-model="selectedJobId"
        :items="jobList"
        item-text="jobDisplayText"
        item-value="diagJobId"
        dense outlined hide-details clearable
        placeholder="진단 이력 선택"
        style="width:680px; flex-grow:0;"
        @change="loadResults"
      />



      <span class="filterLabel">진단유형</span>
      <v-select v-model="selectedDiagTypes" :items="diagTypeOptions" item-text="label" item-value="value"
        dense outlined hide-details multiple chips small-chips deletable-chips
        style="width:260px; flex-grow:0;" />
      <span class="filterLabel">이슈 여부</span>
      <v-select v-model="issueFilter" :items="issueFilterOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:130px; flex-grow:0;" />
    </v-sheet>

    <!-- 필터 바 2행: 명칭 검색 (각각 모드 셀렉트 + 입력) -->
    <v-sheet class="d-flex align-center flex-wrap pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <!-- 테이블명 -->
      <span class="filterLabel">테이블명</span>
      <v-select v-model="searchTableMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchTable" dense outlined hide-details clearable
        placeholder="테이블명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 테이블 한글명 -->
      <span class="filterLabel">테이블 한글명</span>
      <v-select v-model="searchTableKrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchTableKr" dense outlined hide-details clearable
        placeholder="테이블 한글명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼명 -->
      <span class="filterLabel">컬럼명</span>
      <v-select v-model="searchAttrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchAttr" dense outlined hide-details clearable
        placeholder="컬럼명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 한글명 -->
      <span class="filterLabel">컬럼 한글명</span>
      <v-select v-model="searchAttrKrMode" :items="searchModeOptions" item-text="label" item-value="value"
        dense outlined hide-details style="width:100px; flex-grow:0;" />
      <v-text-field v-model="searchAttrKr" dense outlined hide-details clearable
        placeholder="컬럼 한글명" style="width:200px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 타입 -->
      <span class="filterLabel">컬럼 타입</span>
      <v-text-field v-model="searchDataType" dense outlined hide-details clearable
        placeholder="ex) VARCHAR" style="width:140px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <!-- 컬럼 길이 -->
      <span class="filterLabel">컬럼 길이</span>
      <v-text-field v-model="searchDataLen" dense outlined hide-details clearable
        placeholder="ex) 10" style="width:100px; flex-grow:0;" />

      <v-divider vertical class="mx-1" />

      <v-btn small color="grey" outlined @click="resetSearch">
        <v-icon small left>mdi-refresh</v-icon>초기화
      </v-btn>
    </v-sheet>

    <!-- 요약 + 상세 탭 -->
    <v-tabs v-model="activeTab" dense class="mb-0">
      <v-tab>진단 결과</v-tab>
      <v-tab>테이블 상세</v-tab>
      <v-tab>컬럼 상세</v-tab>
    </v-tabs>

    <v-tabs-items v-model="activeTab">
      <!-- 진단 결과 요약 -->
      <v-tab-item>
        <v-sheet v-if="!selectedJobId" class="pa-8 text-center grey--text">
          진단 이력을 선택해주세요.
        </v-sheet>
        <v-sheet v-else class="pa-4">
          <!-- 상단: 진단 정보 + 표준 준수율 -->
          <v-row>
            <!-- 진단 정보 카드 -->
            <v-col cols="4">
              <v-card outlined class="pa-4" style="height:100%;">
                <div class="subtitle-2 font-weight-bold mb-3" style="border-bottom:2px solid #1976D2; padding-bottom:6px;">
                  진단 정보
                </div>
                <v-simple-table dense>
                  <tbody>
                    <tr>
                      <td class="grey--text text--darken-1" style="width:120px;">데이터모델</td>
                      <td class="font-weight-medium">{{ selectedJobInfo.dataModelNm || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">수집일시</td>
                      <td>{{ selectedJobInfo.clctDt || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">진단일시</td>
                      <td>{{ selectedJobInfo.startDt || '-' }} ~ {{ selectedJobInfo.endDt || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">실행자</td>
                      <td>{{ selectedJobInfo.cretUserId || '-' }}</td>
                    </tr>
                  </tbody>
                </v-simple-table>
              </v-card>
            </v-col>

            <!-- 표준 준수율 카드 -->
            <v-col cols="4">
              <v-card outlined class="pa-4 text-center" style="height:100%;">
                <div class="subtitle-2 font-weight-bold mb-3" style="border-bottom:2px solid #4CAF50; padding-bottom:6px;">
                  표준 준수율
                </div>
                <v-progress-circular
                  :value="overviewStats.complianceRate"
                  :size="120"
                  :width="12"
                  :color="complianceColor"
                  rotate="-90"
                >
                  <span class="text-h5 font-weight-bold">{{ overviewStats.complianceRate }}%</span>
                </v-progress-circular>
                <div class="mt-2 text-caption grey--text">
                  전체 {{ overviewStats.totalColCnt }}개 컬럼 중 {{ overviewStats.okColCnt }}개 준수
                </div>
              </v-card>
            </v-col>

            <!-- 전체 현황 카드 -->
            <v-col cols="4">
              <v-card outlined class="pa-4" style="height:100%;">
                <div class="subtitle-2 font-weight-bold mb-3" style="border-bottom:2px solid #FF9800; padding-bottom:6px;">
                  전체 현황
                </div>
                <v-simple-table dense>
                  <tbody>
                    <tr>
                      <td class="grey--text text--darken-1" style="width:120px;">전체 테이블</td>
                      <td class="font-weight-medium text-right">{{ overviewStats.totalTableCnt }}개</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">이슈 테이블</td>
                      <td class="font-weight-medium text-right red--text">{{ overviewStats.issueTableCnt }}개</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">전체 컬럼</td>
                      <td class="font-weight-medium text-right">{{ overviewStats.totalColCnt }}개</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">이슈 컬럼</td>
                      <td class="font-weight-medium text-right red--text">{{ overviewStats.issueColCnt }}개</td>
                    </tr>
                    <tr>
                      <td class="grey--text text--darken-1">총 이슈 건수</td>
                      <td class="font-weight-medium text-right red--text">{{ overviewStats.totalIssueCnt }}건</td>
                    </tr>
                  </tbody>
                </v-simple-table>
              </v-card>
            </v-col>
          </v-row>

          <!-- 하단: 이슈 유형별 분석 + 이슈 TOP 5 테이블 -->
          <v-row class="mt-2">
            <!-- 이슈 유형별 분석 -->
            <v-col cols="6">
              <v-card outlined class="pa-4" style="height:100%;">
                <div class="subtitle-2 font-weight-bold mb-3" style="border-bottom:2px solid #F44336; padding-bottom:6px;">
                  이슈 유형별 분석
                </div>
                <v-simple-table dense>
                  <thead>
                    <tr>
                      <th class="text-center">이슈 유형</th>
                      <th class="text-center">건수</th>
                      <th class="text-center">비율</th>
                      <th class="text-center" style="width:40%;">분포</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="t in issueTypeStats" :key="t.type" style="cursor:pointer;" @click="drillToIssueType(t.type)">
                      <td class="text-center">
                        <v-chip x-small :color="diagTypeColor(t.type)" text-color="white">{{ t.label }} ({{ t.count }})</v-chip>
                      </td>
                      <td class="text-center font-weight-medium">{{ t.count }}건</td>
                      <td class="text-center">{{ t.percent }}%</td>
                      <td>
                        <v-progress-linear :value="t.percent" :color="diagTypeColor(t.type)" height="16" rounded>
                          <template v-slot:default>
                            <span style="font-size:.65rem; color:#fff;">{{ t.percent }}%</span>
                          </template>
                        </v-progress-linear>
                      </td>
                    </tr>
                  </tbody>
                </v-simple-table>
              </v-card>
            </v-col>

            <!-- 이슈 TOP 5 테이블 -->
            <v-col cols="6">
              <v-card outlined class="pa-4" style="height:100%;">
                <div class="subtitle-2 font-weight-bold mb-3" style="border-bottom:2px solid #9C27B0; padding-bottom:6px;">
                  이슈 TOP 5 테이블
                </div>
                <v-simple-table dense>
                  <thead>
                    <tr>
                      <th style="width:30px;">순위</th>
                      <th>테이블명</th>
                      <th class="text-right">이슈 컬럼</th>
                      <th class="text-right">총 이슈</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(t, idx) in top5Tables" :key="t.objNm">
                      <td class="text-center font-weight-bold">{{ idx + 1 }}</td>
                      <td>
                        <span class="ndColor--text" style="cursor:pointer;" @click="drillToDetail(t.objNm)">{{ t.objNm }}</span>
                      </td>
                      <td class="text-right">{{ t.issueColCnt }}</td>
                      <td class="text-right">
                        <v-chip x-small color="red" text-color="white">{{ t.issueCnt }}</v-chip>
                      </td>
                    </tr>
                    <tr v-if="top5Tables.length === 0">
                      <td colspan="4" class="text-center grey--text">이슈 없음</td>
                    </tr>
                  </tbody>
                </v-simple-table>
              </v-card>
            </v-col>
          </v-row>
        </v-sheet>
      </v-tab-item>

      <!-- 테이블 집계 -->
      <v-tab-item>
        <v-data-table
          :headers="summaryHeaders"
          :items="filteredSummary"
          :page.sync="summaryPage"
          :items-per-page="summaryItemsPerPage"
          hide-default-footer
          dense
          class="elevation-1"
        >
          <template v-slot:item.objNm="{ item }">
            <span class="text-caption font-weight-bold ndColor--text" style="cursor:pointer;"
              @click="drillToDetail(item.objNm)">{{ item.objNm }}</span>
          </template>
          <template v-slot:item.issueCnt="{ item }">
            <v-chip x-small color="red" text-color="white">{{ item.issueCnt }}</v-chip>
          </template>
        </v-data-table>
          <div class="d-flex align-center px-4 pt-2 pb-2">
          <v-spacer />
          <v-pagination v-if="summaryPageCount > 1" v-model="summaryPage" :length="summaryPageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
            color="ndColor" :total-visible="10" />
          <v-spacer />
          <v-select v-model="summaryItemsPerPage" :items="[10,20,30,50]" dense outlined hide-details
            style="max-width:100px; font-size:.75rem;" suffix="건" />
        </div>
      </v-tab-item>

      <!-- 컬럼 상세: 전체 컬럼 1건=1행, 실제값/표준값 병렬 표시 -->
      <v-tab-item>
        <!-- 일괄 액션 버튼 -->
        <div class="d-flex align-center pa-2" style="gap:8px;">
          <v-btn small color="primary" outlined
            :disabled="selectedDetailItems.length === 0"
            @click="bulkTermRegister"
            style="min-width:180px; white-space:nowrap;">
            <v-icon small left>mdi-book-plus</v-icon>일괄 용어등록 ({{ selectedDetailItems.length }})
          </v-btn>
          <v-btn small color="orange darken-2" outlined
            :disabled="selectedDetailItems.length === 0"
            @click="bulkCommentGenerate"
            style="min-width:200px; white-space:nowrap;">
            <v-icon small left>mdi-comment-text-multiple</v-icon>일괄 코멘트 생성 ({{ selectedDetailItems.length }})
          </v-btn>
        </div>
        <v-data-table
          :key="`detail-${filterKey}`"
          v-model="selectedDetailItems"
          :headers="detailHeaders"
          :items="filteredDetail"
          :page.sync="detailPage"
          :items-per-page="detailItemsPerPage"
          hide-default-footer
          dense
          show-select
          item-key="objNm_attrNm"
          class="elevation-1"
        >
          <!-- 테이블 한글명 -->
          <template v-slot:item.objNmKr="{ item }">
            <span v-if="item.objNmKr" class="text-caption">{{ item.objNmKr }}</span>
            <span v-else class="text-caption grey--text empty-val">-</span>
          </template>
          <!-- 컬럼 영문명 -->
          <template v-slot:item.attrNm="{ item }">
            <span class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'attrNm') }">{{ item.attrNm }}</span>
          </template>
          <!-- 컬럼 한글명 -->
          <template v-slot:item.attrNmKr="{ item }">
            <span v-if="item.attrNmKr" :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'attrNmKr') }]">
              {{ item.attrNmKr }}
            </span>
            <span v-else :class="['text-caption grey--text empty-val', { 'diag-highlight': isHighlighted(item, 'attrNmKr') }]">-</span>
          </template>
          <!-- 컬럼 데이터 타입 -->
          <template v-slot:item.actualDataType="{ item }">
            <span class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'actualDataType') }">{{ item.actualDataType }}</span>
          </template>
          <!-- 컬럼 길이 (소수점 있으면 10,2 형태) -->
          <template v-slot:item.actualDataLen="{ item }">
            <span v-if="item.actualDataLen != null" class="text-caption" :class="{ 'diag-highlight': isHighlighted(item, 'actualDataLen') }">{{ formatLen(item.actualDataLen, item.actualDataDecimalLen) }}</span>
            <span v-else class="text-caption grey--text empty-val" :class="{ 'diag-highlight': isHighlighted(item, 'actualDataLen') }">-</span>
          </template>
          <!-- 표준 한글명 -->
          <template v-slot:item.stdTermsNm="{ item }">
            <span v-if="item.stdTermsNm" :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdTermsNm') }]">
              {{ item.stdTermsNm }}
            </span>
            <span v-else :class="['text-caption grey--text empty-val', { 'diag-highlight': isHighlighted(item, 'stdTermsNm') }]">-</span>
          </template>
          <!-- 표준 타입 -->
          <template v-slot:item.stdDataType="{ item }">
            <span v-if="item.stdDataType" :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdDataType') }]">
              {{ item.stdDataType }}
            </span>
            <span v-else :class="['text-caption grey--text empty-val', { 'diag-highlight': isHighlighted(item, 'stdDataType') }]">-</span>
          </template>
          <!-- 표준 길이 (소수점 있으면 10,2 형태) -->
          <template v-slot:item.stdDataLen="{ item }">
            <span v-if="item.stdDataLen != null" :class="['text-caption', { 'diag-highlight': isHighlighted(item, 'stdDataLen') }]">
              {{ formatLen(item.stdDataLen, item.stdDataDecimalLen) }}
            </span>
            <span v-else :class="['text-caption grey--text empty-val', { 'diag-highlight': isHighlighted(item, 'stdDataLen') }]">-</span>
          </template>
          <!-- 진단결과: 이슈 유형을 칩으로 복수 표시, mouseover 시 관련 셀 하이라이트 -->
          <template v-slot:item.diagTypes="{ item }">
            <template v-if="item.diagTypeList && item.diagTypeList.length > 0">
              <v-tooltip bottom v-for="dt in item.diagTypeList" :key="dt">
                <template v-slot:activator="{ on }">
                  <v-chip v-on="on" x-small :color="diagTypeColor(dt)" text-color="white" class="mr-1"
                    @mouseenter.native="onChipEnter(item, dt)"
                    @mouseleave.native="onChipLeave()">
                    {{ diagTypeLabel(dt) }}
                  </v-chip>
                </template>
                <span>{{ diagTypeDesc(dt) }}</span>
              </v-tooltip>
            </template>
            <span v-else class="text-caption green--text">OK</span>
          </template>
          <!-- 액션: 이슈 유형별 버튼 -->
          <template v-slot:item.actions="{ item }">
            <div class="d-flex flex-column align-center" style="gap:2px;">
              <v-btn v-if="item.diagTypeList && item.diagTypeList.includes('TERM_NOT_EXIST')"
                x-small color="primary" outlined @click="openTermRegDialog(item)">
                용어 등록
              </v-btn>
              <v-btn v-if="item.diagTypeList && item.diagTypeList.includes('TERM_KR_NM_MISMATCH')"
                x-small color="orange darken-2" outlined @click="openCommentDialog(item)">
                코맨트 변경
              </v-btn>
              <v-btn v-if="item.diagTypeList && (item.diagTypeList.includes('DATA_TYPE_MISMATCH') || item.diagTypeList.includes('DATA_LEN_MISMATCH'))"
                x-small color="purple darken-1" outlined @click="openModifyDialog(item)">
                컬럼 변경
              </v-btn>
            </div>
          </template>
        </v-data-table>
        <div class="d-flex align-center px-4 pt-2 pb-2">
          <v-spacer />
          <v-pagination v-if="detailPageCount > 1" v-model="detailPage" :length="detailPageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
            color="ndColor" :total-visible="10" />
          <v-spacer />
          <v-select v-model="detailItemsPerPage" :items="[20,50,100]" dense outlined hide-details
            style="max-width:100px; font-size:.75rem;" suffix="건" />
        </div>
      </v-tab-item>
    </v-tabs-items>

    <!-- ===== 용어 빠른 등록 모달 ===== -->
    <v-dialog v-model="termRegDialog" max-width="720" persistent>
      <v-card>
        <v-card-title class="subtitle-1 font-weight-bold pb-1">
          용어 빠른 등록
          <v-spacer />
          <v-btn icon small @click="termRegDialog = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-3" v-if="termRegItem">
          <!-- 컬럼 정보 -->
          <v-sheet outlined rounded class="pa-3 mb-3" style="background:#FAFAFA;">
            <div class="d-flex" style="gap:24px;">
              <div><span class="caption grey--text">테이블</span><br/><b>{{ termRegItem.objNm }}</b></div>
              <div><span class="caption grey--text">컬럼</span><br/><b>{{ termRegItem.attrNm }}</b></div>
              <div><span class="caption grey--text">한글명</span><br/><b>{{ termRegItem.attrNmKr || '-' }}</b></div>
              <div><span class="caption grey--text">타입</span><br/><b>{{ termRegItem.actualDataType }}({{ termRegItem.actualDataLen }})</b></div>
            </div>
          </v-sheet>

          <!-- Step 1: 단어 분석 -->
          <div class="subtitle-2 font-weight-bold mb-2">1. 단어 분석</div>
          <v-simple-table dense class="mb-3">
            <thead>
              <tr>
                <th style="width:80px;">영문약어</th>
                <th style="width:100px;">한글명</th>
                <th style="width:100px;">영문명</th>
                <th style="width:80px;">상태</th>
                <th style="width:120px;">액션</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(w, idx) in termRegWords" :key="idx">
                <td><code>{{ w.abrv }}</code></td>
                <td>
                  <span v-if="w.exists">{{ w.wordNm }}</span>
                  <v-text-field v-else v-model="w.wordNm" dense hide-details outlined
                    style="max-width:100px; font-size:.8rem;" placeholder="한글명" />
                </td>
                <td>
                  <span v-if="w.exists">{{ w.wordEngNm }}</span>
                  <v-text-field v-else v-model="w.wordEngNm" dense hide-details outlined
                    style="max-width:100px; font-size:.8rem;" placeholder="영문명"
                    @input="w.wordEngNm = (w.wordEngNm || '').toUpperCase()" />
                </td>
                <td>
                  <v-chip v-if="w.exists" x-small color="green" text-color="white">등록됨</v-chip>
                  <v-chip v-else x-small color="red" text-color="white">미등록</v-chip>
                </td>
                <td>
                  <v-btn v-if="!w.exists" x-small color="primary" :loading="w.saving"
                    @click="registerWord(idx)">단어 등록</v-btn>
                  <span v-else class="caption green--text">OK</span>
                </td>
              </tr>
            </tbody>
          </v-simple-table>

          <!-- Step 2: 도메인 유형 선택 -->
          <div class="subtitle-2 font-weight-bold mb-2">2. 도메인 선택</div>
          <v-radio-group v-if="termRegLastWordIsCode" v-model="termRegDomainType" row dense hide-details class="mt-0 mb-2">
            <v-radio label="일반 도메인" value="domain" />
            <v-radio label="코드" value="code" />
          </v-radio-group>

          <!-- 일반 도메인 선택 -->
          <template v-if="termRegDomainType === 'domain'">
            <v-autocomplete
              v-model="termRegDomain"
              :items="termRegDomainList"
              item-text="domainNm"
              return-object
              dense outlined hide-details
              placeholder="도메인 검색 (타입/길이 기반 추천)"
              class="mb-1"
            >
              <template v-slot:item="{ item }">
                <span>{{ item.domainNm }} <span class="caption grey--text">({{ item.dataType }}, {{ item.dataLen }})</span></span>
              </template>
              <template v-slot:selection="{ item }">
                {{ item.domainNm }} ({{ item.dataType }}, {{ item.dataLen }})
              </template>
            </v-autocomplete>
            <div v-if="termRegDomain" class="caption grey--text mb-3">
              표준 타입: {{ termRegDomain.dataType }} / 길이: {{ termRegDomain.dataLen }}
            </div>
          </template>

          <!-- 코드 선택 -->
          <template v-if="termRegDomainType === 'code'">
            <v-autocomplete
              v-model="termRegCode"
              :items="termRegCodeList"
              :item-text="codeDisplayText"
              return-object
              dense outlined hide-details
              placeholder="코드 검색"
              class="mb-1"
              @change="onCodeSelected"
            >
              <template v-slot:item="{ item }">
                <span>{{ item.codeNm }} <span class="caption grey--text">[{{ item.codeGrp }}] ({{ item.domainNm || '-' }})</span></span>
              </template>
              <template v-slot:selection="{ item }">
                {{ item.codeNm }} [{{ item.codeGrp }}]
              </template>
            </v-autocomplete>
            <div v-if="termRegCode && termRegCode.domainNm" class="caption grey--text mb-3">
              코드 도메인: {{ termRegCode.domainNm }} / 타입: {{ termRegCode.dataType || '-' }} / 길이: {{ termRegCode.dataLen || '-' }}
            </div>
          </template>

          <!-- Step 3: 용어 정보 -->
          <div class="subtitle-2 font-weight-bold mb-2">3. 용어 정보</div>
          <v-row dense>
            <v-col cols="6">
              <v-text-field v-model="termRegTermsNm" label="용어 한글명" dense outlined hide-details />
            </v-col>
            <v-col cols="6">
              <v-text-field :value="termRegItem.attrNm" label="영문약어 (자동)" dense outlined hide-details readonly disabled />
            </v-col>
          </v-row>
          <v-row dense class="mt-1">
            <v-col cols="12">
              <v-textarea v-model="termRegTermsDesc" label="용어 설명" dense outlined hide-details rows="2" />
            </v-col>
          </v-row>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="termRegDialog = false">취소</v-btn>
          <v-btn color="primary" :loading="termRegLoading" :disabled="!canRegisterTerm"
            @click="registerTerm()">용어 등록</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <!-- ===== 컬럼 변경(MODIFY) 스크립트 모달 ===== -->
    <v-dialog v-model="modifyDialog" max-width="600">
      <v-card>
        <v-card-title class="subtitle-1 font-weight-bold pb-1">
          컬럼 변경 스크립트
          <v-spacer />
          <v-btn icon small @click="modifyDialog = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-3">
          <pre style="background:#263238; color:#EEFFFF; padding:16px; border-radius:4px; font-size:.85rem; overflow-x:auto; white-space:pre-wrap;">{{ modifyScript }}</pre>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-chip v-if="modifyCopied" small color="green" text-color="white" class="mr-2">
            <v-icon small left>mdi-check</v-icon>복사되었습니다
          </v-chip>
          <v-btn color="primary" @click="copyModify()">
            <v-icon left small>mdi-content-copy</v-icon>복사
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ===== 코맨트 변경 스크립트 모달 ===== -->
    <v-dialog v-model="commentDialog" max-width="600">
      <v-card>
        <v-card-title class="subtitle-1 font-weight-bold pb-1">
          COMMENT 변경 스크립트
          <v-spacer />
          <v-btn icon small @click="commentDialog = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-3">
          <pre style="background:#263238; color:#EEFFFF; padding:16px; border-radius:4px; font-size:.85rem; overflow-x:auto; white-space:pre-wrap;">{{ commentScript }}</pre>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-chip v-if="commentCopied" small color="green" text-color="white" class="mr-2">
            <v-icon small left>mdi-check</v-icon>복사되었습니다
          </v-chip>
          <v-btn color="primary" @click="copyComment()">
            <v-icon left small>mdi-content-copy</v-icon>복사
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <!-- ===== 일괄 코멘트 스크립트 모달 ===== -->
    <v-dialog v-model="bulkCommentDialog" max-width="700">
      <v-card>
        <v-card-title class="subtitle-1 font-weight-bold pb-1">
          일괄 COMMENT 변경 스크립트
          <v-spacer />
          <v-btn icon small @click="bulkCommentDialog = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pt-3">
          <pre style="background:#263238; color:#EEFFFF; padding:16px; border-radius:4px; font-size:.85rem; overflow-x:auto; white-space:pre-wrap; max-height:400px; overflow-y:auto;">{{ bulkCommentScript }}</pre>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-chip v-if="bulkCommentCopied" small color="green" text-color="white" class="mr-2">
            <v-icon small left>mdi-check</v-icon>복사되었습니다
          </v-chip>
          <v-btn color="primary" @click="copyBulkComment()">
            <v-icon left small>mdi-content-copy</v-icon>복사
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';

export default {
  name: 'DSDataDiagResult',
  data() {
    return {
      jobList: [],
      selectedJobId: null,
      activeTab: 0,
      summaryList: [],
      detailList: [],
      pendingJobId: null,
      // 페이지네이션
      summaryPage: 1,
      summaryItemsPerPage: 20,
      detailPage: 1,
      detailItemsPerPage: 50,
      // 이슈 여부 필터 (체크 시 이슈 있는 컬럼만 표시)
      issueFilter: 'all',   // 'all' | 'issue' | 'noIssue'
      issueFilterOptions: [
        { value: 'all',     label: '전체' },
        { value: 'issue',   label: '이슈 있음' },
        { value: 'noIssue', label: '이슈 없음' },
      ],
      // 검색 모드 옵션 (포함/앞/뒤)
      searchModeOptions: [
        { value: 'contains', label: '포함' },
        { value: 'start',   label: '앞' },
        { value: 'end',     label: '뒤' },
      ],
      // 테이블명 검색
      searchTable: '',
      searchTableMode: 'contains',
      // 테이블 한글명 검색
      searchTableKr: '',
      searchTableKrMode: 'contains',
      // 컬럼명 검색
      searchAttr: '',
      searchAttrMode: 'contains',
      // 컬럼 한글명 검색
      searchAttrKr: '',
      searchAttrKrMode: 'contains',
      // 컬럼 타입 검색
      searchDataType: '',
      // 컬럼 길이 검색
      searchDataLen: '',
      // 칩 mouseover 시 관련 셀 하이라이트 상태
      highlightRow: { itemKey: null, diagType: null },
      // 진단유형 필터
      selectedDiagTypes: [],
      diagTypeOptions: [
        { value: 'TERM_NOT_EXIST',       label: '용어 미존재' },
        { value: 'TERM_KR_NM_MISMATCH',  label: '한글명 불일치' },
        { value: 'DATA_TYPE_MISMATCH',   label: '타입 불일치' },
        { value: 'DATA_LEN_MISMATCH',    label: '길이 불일치' },
      ],
      summaryHeaders: [
        { text: '테이블명',     value: 'objNm',                width: '180px' },
        { text: '테이블 한글명', value: 'objNmKr',             width: '180px' },
        { text: '이슈 컬럼',    value: 'issueColCnt',          width: '80px',  align: 'end' },
        { text: '총 이슈',      value: 'issueCnt',             width: '80px',  align: 'end' },
        { text: '용어 미존재',  value: 'termNotExistCnt',      width: '100px', align: 'end' },
        { text: '한글명 불일치',value: 'termKrNmMismatchCnt',  width: '100px', align: 'end' },
        { text: '타입 불일치',  value: 'dataTypeMismatchCnt',  width: '100px', align: 'end' },
        { text: '길이 불일치',  value: 'dataLenMismatchCnt',   width: '100px', align: 'end' },
      ],
      detailHeaders: [
        { text: '테이블명',       value: 'objNm',         width: '110px', align: 'center' },
        { text: '테이블 한글명',  value: 'objNmKr',       width: '120px' },
        { text: '컬럼 영문명',    value: 'attrNm',        width: '100px' },
        { text: '컬럼 한글명',    value: 'attrNmKr',      width: '110px' },
        { text: '컬럼 타입',      value: 'actualDataType',width: '80px', align: 'center' },
        { text: '컬럼 길이',      value: 'actualDataLen', width: '70px', align: 'end' },
        { text: '표준 한글명',    value: 'stdTermsNm',    width: '110px', class: 'std-header' },
        { text: '표준 타입',      value: 'stdDataType',   width: '80px',  class: 'std-header', align: 'center' },
        { text: '표준 길이',      value: 'stdDataLen',    width: '70px',  class: 'std-header', align: 'end' },
        { text: '진단결과',       value: 'diagTypes',     width: '200px', sortable: false, align: 'center' },
        { text: '액션',           value: 'actions',       width: '80px',  sortable: false, align: 'center' },
      ],
      // 용어 빠른 등록 모달
      termRegDialog: false,
      termRegLoading: false,
      termRegItem: null,         // 선택된 컬럼 정보
      termRegWords: [],          // 분석된 단어 목록 [{ abrv, wordNm, wordEngNm, wordDesc, exists, wordId, domainClsfNm }]
      termRegDomainType: 'domain', // 'domain' | 'code'
      termRegDomain: null,       // 추천 도메인 { domainNm, dataType, dataLen }
      termRegDomainList: [],     // 도메인 후보 목록
      termRegCode: null,         // 선택된 코드 { codeNm, codeGrp, domainNm, dataType, dataLen }
      termRegCodeList: [],       // 코드 목록
      termRegTermsNm: '',        // 용어 한글명
      termRegTermsDesc: '',      // 용어 설명
      // 단어 인라인 등록
      wordRegIdx: -1,            // 현재 편집중인 단어 인덱스 (-1이면 없음)
      // 코맨트 변경 모달
      commentDialog: false,
      commentScript: '',
      commentCopied: false,
      // 컬럼 변경(MODIFY) 모달
      modifyDialog: false,
      modifyScript: '',
      modifyCopied: false,
      // 컬럼 상세 일괄 선택
      selectedDetailItems: [],
      // 일괄 코멘트 모달
      bulkCommentDialog: false,
      bulkCommentScript: '',
      bulkCommentCopied: false,
    };
  },
  computed: {
    summaryPageCount() {
      return Math.ceil(this.filteredSummary.length / this.summaryItemsPerPage);
    },
    detailPageCount() {
      return Math.ceil(this.filteredDetail.length / this.detailItemsPerPage);
    },
    /** 필터 변경 시 테이블 리마운트용 key */
    filterKey() {
      return [
        this.searchTable, this.searchTableMode,
        this.searchTableKr, this.searchTableKrMode,
        this.searchAttr, this.searchAttrMode,
        this.searchAttrKr, this.searchAttrKrMode,
        this.searchDataType,
        this.searchDataLen,
        this.selectedDiagTypes.join(','),
        this.issueFilter,
      ].join('|');
    },
    /** 선택된 진단 Job 정보 */
    selectedJobInfo() {
      if (!this.selectedJobId) return {};
      return this.jobList.find(j => j.diagJobId === this.selectedJobId) || {};
    },
    /** 진단 결과 요약 통계 */
    overviewStats() {
      const detail = this.detailList;
      const summary = this.summaryList;
      const totalColCnt = detail.length;
      const issueColCnt = detail.filter(d => d.diagTypeList && d.diagTypeList.length > 0).length;
      const okColCnt = totalColCnt - issueColCnt;
      const complianceRate = totalColCnt > 0 ? Math.round((okColCnt / totalColCnt) * 100) : 0;
      const totalTableCnt = new Set(detail.map(d => d.objNm)).size;
      const issueTableCnt = summary.filter(s => s.issueCnt > 0).length;
      const totalIssueCnt = summary.reduce((sum, s) => sum + (s.issueCnt || 0), 0);
      return { totalColCnt, issueColCnt, okColCnt, complianceRate, totalTableCnt, issueTableCnt, totalIssueCnt };
    },
    /** 준수율 색상 */
    complianceColor() {
      const r = this.overviewStats.complianceRate;
      if (r >= 80) return 'green';
      if (r >= 50) return 'orange';
      return 'red';
    },
    /** 이슈 유형별 통계 */
    issueTypeStats() {
      const detail = this.detailList;
      const types = [
        { type: 'TERM_NOT_EXIST',      label: '용어 미존재' },
        { type: 'TERM_KR_NM_MISMATCH', label: '한글명 불일치' },
        { type: 'DATA_TYPE_MISMATCH',  label: '타입 불일치' },
        { type: 'DATA_LEN_MISMATCH',   label: '길이 불일치' },
      ];
      const total = this.overviewStats.totalIssueCnt;
      return types.map(t => {
        const count = detail.reduce((sum, d) => sum + ((d.diagTypeList || []).includes(t.type) ? 1 : 0), 0);
        return { ...t, count, percent: total > 0 ? Math.round((count / total) * 100) : 0 };
      });
    },
    /** 이슈 TOP 5 테이블 */
    top5Tables() {
      return this.summaryList
        .filter(s => s.issueCnt > 0)
        .sort((a, b) => b.issueCnt - a.issueCnt)
        .slice(0, 5);
    },
    /** 용어 등록 가능 여부: 모든 단어 등록됨 + 도메인 선택 + 한글명 입력 */
    /** 마지막 단어가 CD(코드)인지 여부 */
    termRegLastWordIsCode() {
      if (!this.termRegWords || this.termRegWords.length === 0) return false;
      var last = this.termRegWords[this.termRegWords.length - 1];
      return last.abrv && last.abrv.toUpperCase() === 'CD';
    },
    canRegisterTerm() {
      if (!this.termRegItem) return false;
      if (!this.termRegTermsNm) return false;
      if (this.termRegDomainType === 'domain' && !this.termRegDomain) return false;
      if (this.termRegDomainType === 'code' && !this.termRegCode) return false;
      if (this.termRegWords.some(w => !w.exists)) return false;
      return true;
    },
    filteredSummary() {
      // 이슈 없음 선택 시 테이블 집계는 이슈 기반이므로 빈 결과
      if (this.issueFilter === 'noIssue') return [];

      return this.summaryList.filter(item => {
        // 테이블명 검색
        if (!this.matchName(item.objNm, this.searchTable, this.searchTableMode)) return false;
        // 진단유형 필터: 선택된 유형의 건수가 1 이상인 테이블만
        if (this.selectedDiagTypes.length > 0) {
          const typeCountMap = {
            'TERM_NOT_EXIST':      item.termNotExistCnt,
            'TERM_KR_NM_MISMATCH': item.termKrNmMismatchCnt,
            'DATA_TYPE_MISMATCH':  item.dataTypeMismatchCnt,
            'DATA_LEN_MISMATCH':   item.dataLenMismatchCnt,
          };
          const hasSelected = this.selectedDiagTypes.some(dt => (typeCountMap[dt] || 0) > 0);
          if (!hasSelected) return false;
        }
        return true;
      });
    },
    filteredDetail() {
      return this.detailList.filter(item => {
        // 이슈 여부 필터
        const hasIssue = item.diagTypeList && item.diagTypeList.length > 0;
        if (this.issueFilter === 'issue' && !hasIssue) return false;
        if (this.issueFilter === 'noIssue' && hasIssue) return false;

        // 명칭 검색 (모드별)
        if (!this.matchName(item.objNm,    this.searchTable,   this.searchTableMode))   return false;
        if (!this.matchName(item.objNmKr,  this.searchTableKr, this.searchTableKrMode)) return false;
        if (!this.matchName(item.attrNm,   this.searchAttr,    this.searchAttrMode))    return false;
        if (!this.matchName(item.attrNmKr, this.searchAttrKr,  this.searchAttrKrMode))  return false;

        // 컬럼 타입 검색 (포함 매칭)
        if (this.searchDataType) {
          const v = (item.actualDataType || '').toLowerCase();
          if (!v.includes(this.searchDataType.toLowerCase())) return false;
        }

        // 컬럼 길이 검색
        if (this.searchDataLen) {
          const display = this.formatLen(item.actualDataLen, item.actualDataDecimalLen);
          if (!display.includes(this.searchDataLen)) return false;
        }

        // 진단유형 필터
        if (this.selectedDiagTypes.length > 0) {
          if (!hasIssue) return false;
          if (!item.diagTypeList.some(d => this.selectedDiagTypes.includes(d))) return false;
        }
        return true;
      });
    },
  },
  mounted() {
    this.checkPendingJob();
    this.loadJobList();
  },
  /**
   * keep-alive 캐시에서 복원될 때 호출 (두 번째 이후 진입)
   */
  activated() {
    this.checkPendingJob();
    if (this.pendingJobId) {
      this.loadJobList();
    }
  },
  methods: {
    /**
     * 명칭 검색 매칭 (포함/앞/뒤 모드)
     * @param {String} value  - 실제 값
     * @param {String} keyword - 검색어
     * @param {String} mode   - 'contains' | 'start' | 'end'
     */
    matchName(value, keyword, mode) {
      if (!keyword) return true;
      const v = (value || '').toLowerCase();
      const k = keyword.toLowerCase();
      if (mode === 'start') return v.startsWith(k);
      if (mode === 'end')   return v.endsWith(k);
      return v.includes(k);  // contains (default)
    },
    loadJobList() {
      axios.post(this.$APIURL.base + 'api/diag/getDiagJobList').then(res => {
        this.jobList = (res.data || []).map(job => ({
          ...job,
          jobDisplayText: `[${job.dataModelNm}] ${job.clctDt || ''} - ${this.statusLabel(job.status)} (이슈 ${job.resultCnt}건)`,
        }));
        if (this.pendingJobId) {
          this.selectJob(this.pendingJobId);
        }
      });
    },
    /**
     * eventBus에 저장된 pendingDiagJobId를 확인하여 pendingJobId에 세팅
     */
    checkPendingJob() {
      if (eventBus.pendingDiagJobId) {
        this.pendingJobId = eventBus.pendingDiagJobId;
        eventBus.pendingDiagJobId = null;
      }
    },
    selectJob(diagJobId) {
      this.selectedJobId = diagJobId;
      this.pendingJobId = null;
      this.loadResults(diagJobId);
    },
    /**
     * 진단 결과 조회
     * - 테이블 집계: getDiagResultSummary (GROUP BY OBJ_NM)
     * - 컬럼 상세: getDiagResultDetail (전체 컬럼 1행, 용어/도메인 JOIN)
     */
    loadResults(diagJobId) {
      if (!diagJobId) {
        this.summaryList = [];
        this.detailList = [];
        return;
      }
      Promise.all([
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultSummary', { params: { diagJobId } }),
        axios.get(this.$APIURL.base + 'api/diag/getDiagResultDetail',  { params: { diagJobId } }),
      ]).then(([summaryRes, detailRes]) => {
        this.summaryList = summaryRes.data || [];
        // diagTypes 문자열을 배열로 변환
        this.detailList = (detailRes.data || []).map(item => ({
          ...item,
          diagTypeList: item.diagTypes ? item.diagTypes.split(',') : [],
          objNm_attrNm: item.objNm + '|' + item.attrNm,
        }));
      });
    },
    /** 테이블 집계에서 테이블명 클릭 → 컬럼 상세 탭으로 이동 + 테이블명 검색조건 세팅 */
    drillToDetail(objNm) {
      this.searchTable = objNm;
      this.searchTableMode = 'contains';
      this.activeTab = 2;
    },
    /** 이슈 유형별 분석에서 클릭 → 진단유형 필터 세팅 + 컬럼 상세 탭 이동 */
    drillToIssueType(diagType) {
      this.selectedDiagTypes = [diagType];
      this.issueFilter = 'issue';
      this.activeTab = 2;
    },
    diagTypeColor(type) {
      const map = {
        TERM_NOT_EXIST:       'red darken-2',
        TERM_KR_NM_MISMATCH:  'orange darken-2',
        DATA_TYPE_MISMATCH:   'blue darken-2',
        DATA_LEN_MISMATCH:    'purple darken-1',
      };
      return map[type] || 'grey';
    },
    diagTypeDesc(type) {
      const map = {
        TERM_NOT_EXIST:       '컬럼 영문명에 해당하는 표준 용어가 등록되어 있지 않습니다.',
        TERM_KR_NM_MISMATCH:  '컬럼 한글명(COMMENTS)이 표준 용어의 한글명과 일치하지 않습니다.',
        DATA_TYPE_MISMATCH:   '컬럼의 데이터 타입이 표준 도메인의 타입과 일치하지 않습니다.',
        DATA_LEN_MISMATCH:    '컬럼의 데이터 길이가 표준 도메인의 길이와 일치하지 않습니다.',
      };
      return map[type] || type;
    },
    diagTypeLabel(type) {
      const map = {
        TERM_NOT_EXIST:       '용어 미존재',
        TERM_KR_NM_MISMATCH:  '한글명 불일치',
        DATA_TYPE_MISMATCH:   '타입 불일치',
        DATA_LEN_MISMATCH:    '길이 불일치',
      };
      return map[type] || type;
    },
    /**
     * 데이터 길이 표시: 소수점 길이가 있으면 "10,2" 형태, 없으면 "10"
     */
    formatLen(len, decimalLen) {
      if (len == null) return '-';
      if (decimalLen && decimalLen > 0) return len + ',' + decimalLen;
      return String(len);
    },
    statusLabel(status) {
      const map = { READY: '대기', RUNNING: '진행중', DONE: '완료', STOPPED: '중지', ERROR: '오류' };
      return map[status] || status;
    },
    /**
     * 진단 칩 mouseenter: 해당 row + 진단유형에 해당하는 셀 하이라이트 활성화
     */
    onChipEnter(item, diagType) {
      this.highlightRow = { itemKey: item.objNm + '|' + item.attrNm, diagType };
    },
    onChipLeave() {
      this.highlightRow = { itemKey: null, diagType: null };
    },
    /**
     * 진단유형별 하이라이트 대상 셀 매핑:
     * - TERM_NOT_EXIST       → attrNm (컬럼 영문명)
     * - TERM_KR_NM_MISMATCH  → attrNmKr, stdTermsNm
     * - DATA_TYPE_MISMATCH   → actualDataType, stdDataType
     * - DATA_LEN_MISMATCH    → actualDataLen, stdDataLen
     */
    /** 컬럼 변경(MODIFY) 스크립트 모달 열기 */
    openModifyDialog(item) {
      var dbmsTp = (this.selectedJobInfo.dbmsTp || '').toLowerCase();
      var table = item.objNm;
      var column = item.attrNm;
      var stdType = item.stdDataType || item.actualDataType || 'VARCHAR2';
      var stdLen = item.stdDataLen;
      var typeSpec = stdLen ? stdType + '(' + stdLen + ')' : stdType;

      var script = '';
      if (dbmsTp.indexOf('oracle') >= 0) {
        script = 'ALTER TABLE ' + table + ' MODIFY (' + column + ' ' + typeSpec + ');';
      } else if (dbmsTp.indexOf('mysql') >= 0 || dbmsTp.indexOf('maria') >= 0) {
        script = 'ALTER TABLE ' + table + ' MODIFY COLUMN ' + column + ' ' + typeSpec + ';';
      } else if (dbmsTp.indexOf('mssql') >= 0 || dbmsTp.indexOf('sqlserver') >= 0) {
        script = 'ALTER TABLE ' + table + ' ALTER COLUMN ' + column + ' ' + typeSpec + ';';
      } else {
        // PostgreSQL
        script = 'ALTER TABLE ' + table + ' ALTER COLUMN ' + column + ' TYPE ' + typeSpec + ';';
      }

      this.modifyScript = '-- [주의] 실행 전 반드시 데이터 백업을 권장합니다.\n' + script;
      this.modifyCopied = false;
      this.modifyDialog = true;
    },
    /** 컬럼 변경 스크립트 클립보드 복사 */
    copyModify() {
      var self = this;
      this.copyToClipboard(this.modifyScript, function() {
        self.modifyCopied = true;
        setTimeout(function() { self.modifyCopied = false; }, 2000);
      });
    },
    /** 코맨트 변경 스크립트 모달 열기 */
    openCommentDialog(item) {
      var dbmsTp = (this.selectedJobInfo.dbmsTp || '').toLowerCase();
      var schema = item.objSchema || '';
      var table = item.objNm;
      var column = item.attrNm;
      var stdNm = item.stdTermsNm || '';

      var script = '';
      if (dbmsTp.indexOf('oracle') >= 0) {
        var target = schema ? schema + '.' + table + '.' + column : table + '.' + column;
        script = 'COMMENT ON COLUMN ' + target + " IS '" + stdNm + "';";
      } else if (dbmsTp.indexOf('mysql') >= 0 || dbmsTp.indexOf('maria') >= 0) {
        var colType = (item.actualDataType || 'VARCHAR') + '(' + (item.actualDataLen || 255) + ')';
        script = 'ALTER TABLE ' + table + ' MODIFY COLUMN ' + column + ' ' + colType + " COMMENT '" + stdNm + "';";
      } else if (dbmsTp.indexOf('mssql') >= 0 || dbmsTp.indexOf('sqlserver') >= 0) {
        script = "EXEC sp_addextendedproperty\n  @name = N'MS_Description',\n  @value = N'" + stdNm + "',\n  @level0type = N'SCHEMA', @level0name = N'" + (schema || 'dbo') + "',\n  @level1type = N'TABLE',  @level1name = N'" + table + "',\n  @level2type = N'COLUMN', @level2name = N'" + column + "';";
      } else {
        // PostgreSQL (기본)
        var pgTarget = schema ? schema + '.' + table + '.' + column : table + '.' + column;
        script = 'COMMENT ON COLUMN ' + pgTarget + " IS '" + stdNm + "';";
      }

      this.commentScript = script;
      this.commentCopied = false;
      this.commentDialog = true;
    },
    /** 코맨트 스크립트 클립보드 복사 */
    copyComment() {
      var self = this;
      this.copyToClipboard(this.commentScript, function() {
        self.commentCopied = true;
        setTimeout(function() { self.commentCopied = false; }, 2000);
      });
    },
    /** 용어 빠른 등록 모달 열기 */
    openTermRegDialog(item) {
      this.termRegItem = item;
      this.termRegTermsNm = item.attrNmKr || '';
      this.termRegTermsDesc = '';
      this.termRegDomainType = 'domain';
      this.termRegDomain = null;
      this.termRegDomainList = [];
      this.termRegCode = null;
      this.termRegCodeList = [];
      this.termRegWords = [];
      this.termRegDialog = true;
      this.analyzeWords(item.attrNm);
      this.loadDomainCandidates(item.actualDataType, item.actualDataLen);
      this.loadCodeList();
    },
    /** 컬럼 영문명을 _ 로 분리하여 단어 존재 여부 일괄 확인 */
    analyzeWords(attrNm) {
      const parts = attrNm.split('_').filter(p => p);
      this.termRegWords = parts.map(p => ({
        abrv: p, wordNm: '', wordEngNm: '', wordDesc: '', exists: false, wordId: null, domainClsfNm: '', saving: false,
      }));
      // 일괄 조회
      axios.post(this.$APIURL.base + 'api/std/getWordsByEngAbrvNms', parts).then(res => {
        const wordMap = {};
        (res.data || []).forEach(w => { wordMap[w.wordEngAbrvNm] = w; });
        this.termRegWords.forEach(tw => {
          const found = wordMap[tw.abrv];
          if (found) {
            tw.exists = true;
            tw.wordId = found.wordId;
            tw.wordNm = found.wordNm;
            tw.wordEngNm = found.wordEngNm || '';
            tw.domainClsfNm = found.domainClsfNm || '';
          }
        });
        // 미등록 단어 한글명 자동 추출 시도 (한글명에서 등록된 단어 제거)
        this.guessWordNames();
      });
    },
    /** 한글명에서 등록된 단어를 제외하여 미등록 단어의 한글명을 추측 */
    guessWordNames() {
      let remaining = this.termRegTermsNm || '';
      // 등록된 단어의 한글명을 순서대로 제거
      this.termRegWords.forEach(w => {
        if (w.exists && w.wordNm && remaining.includes(w.wordNm)) {
          remaining = remaining.replace(w.wordNm, '');
        }
      });
      // 미등록 단어가 1개면 나머지 전체를 한글명으로 설정
      const unregistered = this.termRegWords.filter(w => !w.exists);
      if (unregistered.length === 1 && remaining.trim()) {
        unregistered[0].wordNm = remaining.trim();
      }
    },
    /** 타입+길이 기반으로 도메인 후보 조회 */
    loadDomainCandidates(dataType, dataLen) {
      axios.post(this.$APIURL.base + 'api/std/getDomainList', {
        schDataType: dataType || '',
      }).then(res => {
        this.termRegDomainList = res.data || [];
        // 타입+길이 정확 매치가 있으면 자동 선택
        const exact = this.termRegDomainList.find(d =>
          d.dataType && d.dataType.toUpperCase() === (dataType || '').toUpperCase()
          && d.dataLen === dataLen
        );
        if (exact) this.termRegDomain = exact;
      });
    },
    /** 등록된 코드 목록 조회 */
    loadCodeList() {
      var self = this;
      axios.post(this.$APIURL.base + 'api/std/getCodeInfoList', {}).then(function(res) {
        self.termRegCodeList = res.data || [];
      });
    },
    /** 코드 선택 시 도메인 정보 표시용 */
    codeDisplayText(item) {
      return item.codeNm + ' [' + item.codeGrp + ']';
    },
    /** 코드 선택 시 */
    onCodeSelected(code) {
      this.termRegCode = code;
    },
    /** 미등록 단어 인라인 등록 */
    registerWord(idx) {
      const w = this.termRegWords[idx];
      if (!w.wordNm) { this.$swal.fire({ icon: 'warning', title: '입력 필요', text: '한글명을 입력해주세요.', confirmButtonText: '확인' }); return; }
      w.saving = true;
      this.$set(this.termRegWords, idx, { ...w });
      axios.post(this.$APIURL.base + 'api/std/createWord', {
        wordNm: w.wordNm,
        wordEngAbrvNm: w.abrv,
        wordEngNm: w.wordEngNm || '',
        wordDesc: w.wordNm,
        wordClsfYn: 'N',
        domainClsfNm: '',
        allophSynmLst: [],
        forbdnWordLst: [],
        commStndYn: 'N',
        magntdOrd: '',
        reqSysCd: '',
      }).then(res => {
        // 등록 성공 → 단어 ID 받아서 상태 갱신
        w.exists = true;
        w.saving = false;
        w.wordId = res.data.wordId || res.data;
        this.$set(this.termRegWords, idx, { ...w });
        // wordId를 다시 조회 (createWord 응답에 ID가 없을 수 있으므로)
        axios.get(this.$APIURL.base + 'api/std/getWordByEngAbrvNm', { params: { wordEngAbrvNm: w.abrv } }).then(r2 => {
          if (r2.data) {
            w.wordId = r2.data.wordId;
            this.$set(this.termRegWords, idx, { ...w });
          }
        });
      }).catch(err => {
        w.saving = false;
        this.$set(this.termRegWords, idx, { ...w });
        this.$swal.fire({ icon: 'error', title: '단어 등록 실패', text: (err.response && err.response.data && err.response.data.message) || err.message, confirmButtonText: '확인' });
      });
    },
    /** 용어 등록 */
    registerTerm() {
      var self = this;
      this.termRegLoading = true;
      var wordList = this.termRegWords.map(function(w, i) {
        return {
          termsId: '',
          wordId: w.wordId,
          wordNm: w.wordNm,
          wordOrd: i + 1,
        };
      });
      var engAbrvNm = this.termRegWords.map(function(w) { return w.abrv; }).join('_');
      var domainNm = '';
      var codeGrp = '';
      if (this.termRegDomainType === 'code' && this.termRegCode) {
        domainNm = this.termRegCode.domainNm || '';
        codeGrp = this.termRegCode.codeGrp || '';
      } else if (this.termRegDomain) {
        domainNm = this.termRegDomain.domainNm;
      }
      axios.post(this.$APIURL.base + 'api/std/createTerms', {
        termsNm: this.termRegTermsNm,
        termsEngAbrvNm: engAbrvNm,
        termsDesc: this.termRegTermsDesc || this.termRegTermsNm,
        domainNm: domainNm,
        codeGrp: codeGrp,
        chrgOrg: '',
        commStndYn: 'N',
        magntdOrd: '',
        reqSysCd: '',
        wordList: wordList,
        allophSynmLst: [],
      }).then(function() {
        self.termRegLoading = false;
        self.termRegDialog = false;
        self.$swal.fire({
          icon: 'success',
          title: '용어 등록 완료',
          text: '용어가 등록되었습니다. 재진단 시 반영됩니다.',
          confirmButtonText: '확인',
        });
        // 진단 결과 데이터 새로고침
        self.loadResults(self.selectedJobId);
      }).catch(function(err) {
        self.termRegLoading = false;
        var detail = (err.response && err.response.data && err.response.data.message) || '';
        var errMsg = detail || err.message || '알 수 없는 오류가 발생했습니다.';
        self.$swal.fire({
          icon: 'error',
          title: '용어 등록 실패',
          text: errMsg,
          confirmButtonText: '확인',
        });
      });
    },
    /** 클립보드 복사 공통 (navigator.clipboard 우선, fallback으로 execCommand) */
    copyToClipboard(text, onSuccess) {
      var self = this;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          if (onSuccess) onSuccess();
        }).catch(function() {
          self.$swal.fire({ icon: 'error', title: '복사 실패', text: '클립보드 복사에 실패했습니다.', confirmButtonText: '확인' });
        });
      } else {
        var textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        textArea.style.opacity = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        try {
          document.execCommand('copy');
          if (onSuccess) onSuccess();
        } catch (e) {
          self.$swal.fire({ icon: 'error', title: '복사 실패', text: '클립보드 복사에 실패했습니다.', confirmButtonText: '확인' });
        }
        document.body.removeChild(textArea);
      }
    },
    /** 검색 조건 초기화 + 데이터 새로고침 */
    resetSearch() {
      this.searchTable = '';
      this.searchTableMode = 'contains';
      this.searchTableKr = '';
      this.searchTableKrMode = 'contains';
      this.searchAttr = '';
      this.searchAttrMode = 'contains';
      this.searchAttrKr = '';
      this.searchAttrKrMode = 'contains';
      this.searchDataType = '';
      this.searchDataLen = '';
      this.selectedDiagTypes = [];
      this.issueFilter = 'all';
      this.selectedDetailItems = [];
      // 데이터 새로고침
      this.loadResults(this.selectedJobId);
    },
    /** 일괄 용어 등록: 선택된 항목 중 TERM_NOT_EXIST 이슈가 있는 것만 순차 등록 */
    bulkTermRegister() {
      var targets = this.selectedDetailItems.filter(function(item) {
        return item.diagTypeList && item.diagTypeList.indexOf('TERM_NOT_EXIST') >= 0;
      });
      if (targets.length === 0) {
        this.$swal.fire({ icon: 'info', title: '대상 없음', text: '선택된 항목 중 용어 미존재 이슈가 있는 컬럼이 없습니다.', confirmButtonText: '확인' });
        return;
      }
      // 첫 번째 대상을 용어 등록 모달로 열기
      this.openTermRegDialog(targets[0]);
      if (targets.length > 1) {
        this.$swal.fire({ icon: 'info', title: '일괄 용어등록', text: '대상 ' + targets.length + '건 중 첫 번째 항목의 등록 모달을 열었습니다. 순차적으로 등록해주세요.', confirmButtonText: '확인' });
      }
    },
    /** 일괄 코멘트 생성: 선택된 모든 항목에 대해 COMMENT 스크립트 생성 */
    bulkCommentGenerate() {
      var self = this;
      if (this.selectedDetailItems.length === 0) return;
      var scripts = [];
      this.selectedDetailItems.forEach(function(item) {
        var script = self.generateCommentScript(item);
        if (script) scripts.push(script);
      });
      if (scripts.length === 0) {
        this.$swal.fire({ icon: 'info', title: '대상 없음', text: '선택된 항목에서 코멘트 스크립트를 생성할 수 없습니다.', confirmButtonText: '확인' });
        return;
      }
      this.bulkCommentScript = '-- 일괄 COMMENT 변경 스크립트 (' + scripts.length + '건)\n' + scripts.join('\n');
      this.bulkCommentCopied = false;
      this.bulkCommentDialog = true;
    },
    /** 단일 항목에 대한 COMMENT 스크립트 생성 (문자열 반환) */
    generateCommentScript(item) {
      var dbmsTp = (this.selectedJobInfo.dbmsTp || '').toLowerCase();
      var schema = item.objSchema || '';
      var table = item.objNm;
      var column = item.attrNm;
      var stdNm = item.stdTermsNm || item.attrNmKr || '';
      if (!stdNm) return '';

      if (dbmsTp.indexOf('oracle') >= 0) {
        var target = schema ? schema + '.' + table + '.' + column : table + '.' + column;
        return 'COMMENT ON COLUMN ' + target + " IS '" + stdNm + "';";
      } else if (dbmsTp.indexOf('mysql') >= 0 || dbmsTp.indexOf('maria') >= 0) {
        var colType = (item.actualDataType || 'VARCHAR') + '(' + (item.actualDataLen || 255) + ')';
        return 'ALTER TABLE ' + table + ' MODIFY COLUMN ' + column + ' ' + colType + " COMMENT '" + stdNm + "';";
      } else if (dbmsTp.indexOf('mssql') >= 0 || dbmsTp.indexOf('sqlserver') >= 0) {
        return "EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'" + stdNm + "', @level0type = N'SCHEMA', @level0name = N'" + (schema || 'dbo') + "', @level1type = N'TABLE', @level1name = N'" + table + "', @level2type = N'COLUMN', @level2name = N'" + column + "';";
      } else {
        var pgTarget = schema ? schema + '.' + table + '.' + column : table + '.' + column;
        return 'COMMENT ON COLUMN ' + pgTarget + " IS '" + stdNm + "';";
      }
    },
    /** 일괄 코멘트 스크립트 클립보드 복사 */
    copyBulkComment() {
      var self = this;
      this.copyToClipboard(this.bulkCommentScript, function() {
        self.bulkCommentCopied = true;
        setTimeout(function() { self.bulkCommentCopied = false; }, 2000);
      });
    },
    isHighlighted(item, field) {
      const h = this.highlightRow;
      if (!h.itemKey || !h.diagType) return false;
      if ((item.objNm + '|' + item.attrNm) !== h.itemKey) return false;
      const fieldMap = {
        TERM_NOT_EXIST:       ['attrNm'],
        TERM_KR_NM_MISMATCH:  ['attrNmKr', 'stdTermsNm'],
        DATA_TYPE_MISMATCH:   ['actualDataType', 'stdDataType'],
        DATA_LEN_MISMATCH:    ['actualDataLen', 'stdDataLen'],
      };
      const targets = fieldMap[h.diagType] || [];
      return targets.includes(field);
    },
  },
};
</script>

<style scoped>
.filterLabel {
  font-size: .8rem;
  white-space: nowrap;
  color: #455A64;
  font-weight: 500;
}
/* 빈 값(-) 가운데 정렬 */
.empty-val {
  display: block;
  text-align: center;
}
/* 진단 칩 mouseover 시 관련 셀 빨간 배경 하이라이트 */
.diag-highlight {
  background-color: #FFCDD2 !important;
  border-radius: 4px;
  padding: 1px 4px;
  transition: background-color 0.15s ease;
}
</style>

<style>
/* 표준 기준 컬럼 헤더 배경색 (연초록) - scoped 밖에서 정의해야 v-data-table 내부에 적용 */
.std-header {
  background-color: #E8F5E9 !important;
}
/* 모든 테이블 헤더 가운데 정렬 (Vuetify가 th에 text-start/text-end 클래스 적용하므로 강제 override) */
.v-data-table th.text-start,
.v-data-table th.text-end,
.v-data-table th.text-center {
  text-align: center !important;
  position: relative;
}
/* 정렬 화살표를 항상 오른쪽에 배치 (align:'end' 시 왼쪽에 생기는 것 방지) */
.v-data-table th .v-data-table-header__icon {
  position: absolute !important;
  right: 4px;
}
</style>
