<template>
  <v-main>
    <Split direction="vertical" :style="{ overflow: 'hidden' }">
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <!-- 검색과 버튼 영역 -->
        <v-sheet class="splitTopWrapper pt-4 pb-4"
          v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
          <!-- 검색 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-row :style="{ alignItems: 'center', margin: '0px' }">
              <!-- 용어명 검색 -->
              <span :style="{ fontSize: '.875rem' }">용어명</span>
              <v-select v-model="searchTermMode" :items="searchModeOptions" item-text="label" item-value="value"
                dense outlined hide-details :style="{ width: '100px', flexGrow: 0 }" class="ml-2" />
              <v-text-field class="pr-4 pl-2" v-model="searchTerm" v-on:keyup.enter="getTermData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>

              <!-- 용어영문약어명 검색 -->
              <span :style="{ fontSize: '.875rem' }">용어영문약어명</span>
              <v-select v-model="searchEngTermMode" :items="searchModeOptions" item-text="label" item-value="value"
                dense outlined hide-details :style="{ width: '100px', flexGrow: 0 }" class="ml-2" />
              <v-text-field class="pr-4 pl-2" v-model="searchEngTerm" v-on:keyup.enter="getTermData"
                @input="searchEngTerm = (searchEngTerm || '').toUpperCase()"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>

               <!-- 도메인명 검색 타이틀 -->
              <span :style="{ fontSize: '.875rem' }">도메인명</span>
              <!-- 도메인명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchDomain" v-on:keyup.enter="getTermData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>

              <!-- 등록일자 (범위) -->
              <span :style="{ fontSize: '.875rem' }">등록일자</span>
              <v-text-field class="pl-2" v-model="searchFromDt" type="date" dense outlined hide-details
                color="ndColor" :style="{ width: '160px' }" />
              <span :style="{ fontSize: '.875rem' }" class="px-1">~</span>
              <v-text-field class="pr-4" v-model="searchToDt" type="date" dense outlined hide-details
                color="ndColor" :style="{ width: '160px' }" />

              <!-- 승인 여부 추가 -->
              <v-checkbox class="tarmSearchApv" v-model="searchApproval" label="승인 여부" color="ndColor"
                hide-details></v-checkbox>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getTermData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>

          </v-sheet>
          <!-- 86번 #35 — DSDomain 패턴 따라 엑셀 관련은 드롭다운으로 묶음 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]"
            class="d-flex flex-nowrap align-center" style="gap: 6px;">
            <v-btn class="gradient" v-on:click="showModal('add')" title="등록">{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>
            <v-btn v-if="isAdmin" class="gradient" v-on:click="termRemoveItem()" title="선택 삭제" :disabled="removeItems.length === 0">선택 삭제</v-btn>
            <v-btn v-if="isAdmin" class="gradient" color="red lighten-4" v-on:click="termBulkRemove()" title="전체 삭제">전체 삭제</v-btn>
            <v-menu offset-y>
              <template v-slot:activator="{ on, attrs }">
                <v-btn class="gradient" v-bind="attrs" v-on="on">
                  <v-icon small left>mdi-file-excel</v-icon>엑셀
                  <v-icon small right>mdi-menu-down</v-icon>
                </v-btn>
              </template>
              <v-list dense>
                <v-list-item v-if="isAdmin" @click="excelFileUpload">
                  <v-list-item-icon><v-icon small>mdi-upload</v-icon></v-list-item-icon>
                  <v-list-item-title>엑셀 업로드</v-list-item-title>
                </v-list-item>
                <v-list-item @click="downloadTermTemplate()">
                  <v-list-item-icon><v-icon small>mdi-file-download-outline</v-icon></v-list-item-icon>
                  <v-list-item-title>양식 다운로드</v-list-item-title>
                </v-list-item>
                <v-list-item @click="termListDownload()">
                  <v-list-item-icon><v-icon small>mdi-download</v-icon></v-list-item-icon>
                  <v-list-item-title>데이터 다운로드</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
            <input type="file" @change="readExcelFile" ref="file" id="inputTermUpload" :style="{ display: 'none' }"
              accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ termItems.length }}건</span>
            <span v-if="removeItems.length > 0" class="ml-3" :style="{ color: '#d32f2f', fontWeight: 'bold' }">{{ removeItems.length }}건 선택됨</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
        <!-- 용어 목록 -->
        <v-data-table id="term_table" :headers="termHeaders" :items="termItems" :page.sync="page"
          :items-per-page="itemsPerPage" hide-default-footer item-key="termsNm" show-select class="px-4 pb-3"
          v-model="removeItems" @input="enterSelect()" :loading="loadTable" loading-text="잠시만 기다려주세요.">
          <!-- 클릭 가능한 아이템 설정 : 용어명  -->
          <template v-slot:[`item.termsNm`]="{ item }">
            <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="showDetail(item)">{{
              item.termsNm
            }}</span>
          </template>

          <template #top>
            <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
          </template>
          <template #no-data>
            <v-alert v-show="!loadTable">
              데이터가 존재하지 않습니다.
            </v-alert>
            <span v-show="loadTable">잠시만 기다려주세요.</span>
          </template>
        </v-data-table>

        <v-sheet class="split_bottom_wrap">
          <!-- 페이지네이션 -->
          <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
            <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
              color="ndColor" :total-visible="10"></v-pagination>
          </div>
        </v-sheet>

      </SplitArea>
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <v-sheet>
          <!-- 디테일 탭 -->
          <v-tabs :value="this.detailTab" class="tabsStyle" background-color="rgba(0,0,0,0.1)">
            <v-tab v-for="item in detailTab" :tabindex="item.index" :key="item.index" class="tabBgColor"
              active-class="activeTabBgColor" v-on:click.stop="addActiveDetail(item.name, item.index)"
              :style="{ borderRight: '1px solid rgba(255,255,255, 0.4) !important' }">
              {{ item.title }}
            </v-tab>
          </v-tabs>
        </v-sheet>
        <v-sheet v-if="activeDetailTab === 'tab1'" class="tabContentsWrapper">
          <!-- 용어 상세보기 콘텐츠 -->
          <div class="split_bottom" v-show="selectedItem.length != 0">
            <v-sheet class="splitBottomWrapper">
              <!-- 타이틀 -->
              <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
                <span class="splitBottomSpan"
                  :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                    detailTerm
                  }}'</span>
                <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;상세 보기</span>
              </v-sheet>
              <!-- 수정 / 삭제 버튼 -->
              <v-sheet class="pr-4 pl-4">
                <v-btn v-if="isAdmin" class="gradient" v-on:click="showModal('update')">수정</v-btn>
                <!-- <v-btn class="gradient" v-on:click="wordRemoveItem()">삭제</v-btn> -->
              </v-sheet>
            </v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-data-table id="term_detail_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
                <template v-slot:body="{ items }">
                  <tbody>
                    <!-- 상세 테이블 왼쪽  -->
                    <tr v-for="header in detaileHeaders" :key="header.value">
                      <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '15%' }">
                        {{ header.text }}
                      </td>
                      <!-- 상세 테이블 오른쪽  -->
                      <td v-for="item in items" :key="item.termNm">
                        <div v-if="Array.isArray(item[header.value])">
                          <!-- 값이 배열이라면 줄바꿈으로 표시 -->
                          <div v-for="item2 in item[header.value]" :key="item2">
                            {{ item2 }}
                          </div>
                        </div>
                        <div v-else>
                          <!-- 값이 배열이 아닌 문자열이라면 한 줄로 표시 -->
                          {{ item[header.value] }}
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </template>
                <!-- 데이터 없음 -->
                <template v-slot:no-data>
                  <v-alert>
                    데이터가 존재하지 않습니다.
                  </v-alert>
                </template>
              </v-data-table>
            </v-sheet>
          </div>
        </v-sheet>
        <v-sheet v-else class="tabContentsWrapper">
          <!-- 용어 단어 구성 목록 콘텐츠 -->
          <div class="split_bottom" v-show="selectedItem.length != 0">
            <v-sheet class="splitBottomWrapper">
              <!-- 타이틀 -->
              <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
                <span class="splitBottomSpan"
                  :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                    detailTerm
                  }}'</span>
                <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;단어 구성 목록</span>
              </v-sheet>
              <!-- 수정 / 삭제 버튼 -->
              <v-sheet class="pr-4 pl-4">
                <v-btn v-if="isAdmin" class="gradient" v-on:click="showModal('update')">수정</v-btn>
                <!-- <v-btn class="gradient" v-on:click="wordRemoveItem()">삭제</v-btn> -->
              </v-sheet>
            </v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-data-table id="term_wordItemsList_table" :headers="wordItemsListHeaders" :items="wordItemsList"
                hide-default-footer class="px-4 pb-3">
                <!-- 데이터 없음 -->
                <template v-slot:no-data>
                  <v-alert>
                    데이터가 존재하지 않습니다.
                  </v-alert>
                </template>
              </v-data-table>
            </v-sheet>
          </div>
        </v-sheet>
      </SplitArea>
    </Split>
    <!-- Add term Modal — 81번 단일 폼 + 디바운스 자동 분석 + 코드 picker -->
    <v-dialog max-width="950px" v-model="addTermModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true"
        :header-title="'용어 등록 ' + (addTerm_user_selected_word.length > 0 ? '- ' + addTerm_user_selected_word : '')"
        footer-hide-title="취소" footer-submit-title="등록">
        <!-- 86번 #41 — 최종 용어명 + 영문약어 + 도메인 V타입 미리보기 (footer 왼쪽 큼지막하게) -->
        <template v-slot:footer-left>
          <div v-if="addTerm_user_selected_word" class="d-flex align-center"
            style="gap: 10px; padding: 4px 8px; background: #E8EAF6; border-radius: 6px;">
            <span style="font-size: 1.1rem; font-weight: 700; color: #283593;">{{ addTerm_user_selected_word }}</span>
            <span v-if="addTerm_termEngAbrvNm" style="font-size: .9rem; font-weight: 600; color: #5C6BC0;">{{ addTerm_termEngAbrvNm }}</span>
            <span v-if="addTerm_finalDomainLabel" style="font-size: .85rem; color: #00695C; font-weight: 500;">{{ addTerm_finalDomainLabel }}</span>
          </div>
        </template>
        <template v-slot:body>
          <v-form ref="form">
            <!-- ① 용어명 + 영문약어 -->
            <v-row dense align="center">
              <v-col cols="4"><v-subheader class="reqText">용어명</v-subheader></v-col>
              <v-col cols="8">
                <v-text-field v-model="addTerm_termNm" ref="addTerm_termNm"
                  :rules="[() => !!addTerm_termNm || '용어명은 필수 입력값입니다.']"
                  :loading="addTerm_analyzing" clearable required dense placeholder="가동개시일자"
                  color="ndColor" hide-details="auto"></v-text-field>
                <div v-if="addTerm_analyzing" class="caption grey--text mt-1">자동 분석 중…</div>
                <div v-else-if="addTerm_wordListArr.length > 0" class="caption grey--text mt-1">
                  자동 분석 완료 — 단어 {{ addTerm_wordListArr.length }}개 ({{ addTerm_wordListArr.filter(w => w.wordLst && w.wordLst.length > 0).length }} 매칭 / {{ addTerm_wordListArr.filter(w => !w.wordLst || w.wordLst.length === 0).length }} 신규)
                </div>
              </v-col>
            </v-row>
            <v-row dense align="center" v-if="addTerm_termEngAbrvNm">
              <v-col cols="4"><v-subheader class="reqText">용어 영문 약어명</v-subheader></v-col>
              <v-col cols="8">
                <v-text-field v-model="addTerm_termEngAbrvNm" required dense color="ndColor" readonly
                  filled hide-details></v-text-field>
              </v-col>
            </v-row>

            <v-divider class="my-3" v-if="addTerm_wordListArr.length > 0"></v-divider>

            <!-- ② 구성 단어 (자동 분석 결과 — 매칭 단어 + 사용자가 입력한 용어명에 해당하는 신규 단어만 표시.
                 API 가 모든 부분문자열을 분류로 주지만 매칭된 것만 사용자에게 노출) -->
            <div v-if="addTerm_wordListArr.length > 0">
              <div class="d-flex align-center px-0">
                <v-subheader class="px-0 flex-grow-1">구성 단어</v-subheader>
                <!-- 86번 #23 — 추천 1순위 / 2순위 토글 (alternativeWords 가 있을 때만) -->
                <v-btn-toggle v-if="addTerm_lastAnalysis && addTerm_lastAnalysis.alternativeWords && addTerm_lastAnalysis.alternativeWords.length > 0"
                  :value="addTerm_splitMode" @change="applySplitMode" mandatory dense color="primary">
                  <v-btn x-small :value="0">추천 1</v-btn>
                  <v-btn x-small :value="1">추천 2</v-btn>
                </v-btn-toggle>
              </div>
              <!-- 86번 #31 — 단일 v-for + 내부 v-if/v-else 로 매칭/미매칭 분기.
                   기존: 두 개 v-for + 각자 v-if 였는데 Vue 2 의 v-for+v-if 조합에서
                   item.wordLst 변경 시 DOM 이 안 바뀌는 문제 발견 (state 는 업데이트되는데 화면 미반영). -->
              <v-row v-for="(item, index) in addTerm_wordListArr" :key="'add-word-row-' + index"
                :style="{ margin: '0 0 12px 0' }">
                <v-col cols="12" :style="{ padding: '0' }">
                  <template v-if="item.wordLst && item.wordLst.length > 0 && item.wordLst[0] && item.wordLst[0].wordEngAbrvNm">
                    <!-- 매칭됨: 등록됨 칩 + 단어 정보 테이블 -->
                    <div class="d-flex align-center" :style="{ margin: '6px 0' }">
                      <h4 class="flex-grow-1">{{ item.wordNm }}
                        <v-chip x-small color="green" text-color="white" class="ml-2">등록됨</v-chip>
                      </h4>
                      <v-btn icon x-small color="red" @click="removeWordAt(index)" title="단어 삭제">
                        <v-icon x-small>mdi-close</v-icon>
                      </v-btn>
                    </div>
                    <v-data-table class="px-2 pb-2" :headers="wordListHeader"
                      :items="item.wordLst" item-key="index" v-model="addTerm_selected_word_list[index]"
                      :value="addTerm_selected_word_list[index]" hide-default-footer show-select dense>
                    </v-data-table>
                  </template>
                  <template v-else>
                    <!-- 미매칭: 인라인 등록 폼 -->
                    <div class="d-flex align-center" :style="{ margin: '6px 0' }">
                      <h4 class="flex-grow-1">{{ item.wordNm }}
                        <v-chip x-small color="orange" text-color="white" class="ml-2">미등록 — 인라인 등록 가능</v-chip>
                      </h4>
                      <v-btn icon x-small color="red" @click="removeWordAt(index)" title="단어 삭제">
                        <v-icon x-small>mdi-close</v-icon>
                      </v-btn>
                    </div>
                    <v-sheet outlined rounded class="pa-3">
                      <v-row dense>
                        <v-col cols="3">
                          <v-text-field v-model="item.inlineWordNm" dense outlined hide-details
                            label="단어 한글명" :placeholder="item.wordNm"
                            @input="onInlineWordNmInput(index)"
                            @change="onInlineWordNmInput(index)"
                            @compositionend="onInlineWordNmInput(index)" />
                        </v-col>
                        <v-col cols="3">
                          <v-text-field v-model="item.inlineWordEngAbrvNm" dense outlined hide-details
                            label="영문약어" @input="item.inlineWordEngAbrvNm = (item.inlineWordEngAbrvNm || '').toUpperCase()" />
                        </v-col>
                        <v-col cols="3">
                          <v-text-field v-model="item.inlineWordEngNm" dense outlined hide-details
                            label="영문명" @input="item.inlineWordEngNm = (item.inlineWordEngNm || '').toUpperCase()" />
                        </v-col>
                        <v-col cols="3" class="d-flex align-center">
                          <v-btn small color="primary" :loading="item.inlineSaving"
                            @click="inlineRegisterWord(index)">단어 등록</v-btn>
                        </v-col>
                      </v-row>
                    </v-sheet>
                  </template>
                </v-col>
              </v-row>

              <!-- 86번 #41 — 단어 추가 / 형식단어 추가를 카드로 묶어 시각적 구분 -->
              <v-sheet outlined rounded class="pa-3 mt-2"
                :style="{ background: '#F5F7FB', borderColor: '#C5CAE9 !important' }">
                <div class="caption font-weight-bold ndColor--text mb-2">
                  <v-icon small color="indigo darken-2">mdi-plus-circle-outline</v-icon>
                  단어 추가
                </div>
                <!-- 단어 직접 추가 -->
                <v-row dense align="center">
                  <v-col cols="9">
                    <v-text-field v-model="addTerm_manualWordInput" dense outlined hide-details
                      label="단어 직접 추가" placeholder="자동 분석이 잘못 쪼갠 경우 — 한글 단어명 입력 후 추가"
                      background-color="white"
                      @keyup.enter="addManualWord" />
                  </v-col>
                  <v-col cols="3">
                    <v-btn small color="primary" :disabled="!addTerm_manualWordInput || !addTerm_manualWordInput.trim()"
                      @click="addManualWord">
                      <v-icon small left>mdi-plus</v-icon>단어 추가
                    </v-btn>
                  </v-col>
                </v-row>
                <!-- 형식단어 검색/선택 추가 -->
                <v-row dense align="center" class="mt-2">
                  <v-col cols="9">
                    <v-autocomplete v-model="addTerm_selectedClsfWord" :items="addTerm_classWords"
                      :item-text="clsfItemText" return-object dense outlined hide-details clearable
                      label="형식단어 검색/선택"
                      placeholder="예: 명, 일자, 코드 등"
                      background-color="white"
                      :loading="addTerm_loadingClsfWords" :menu-props="{ maxHeight: 320 }"
                      no-data-text="일치하는 형식단어 없음">
                      <template v-slot:selection="{ item }">
                        <span>{{ item.wordNm }}</span>
                        <span v-if="item.domainClsfNm" style="font-size:.75rem; color:#9E9E9E; margin-left:4px;">({{ item.domainClsfNm }})</span>
                      </template>
                      <template v-slot:item="{ item }">
                        <span>{{ item.wordNm }}</span>
                        <span v-if="item.domainClsfNm" style="font-size:.75rem; color:#9E9E9E; margin-left:6px;">[{{ item.domainClsfNm }}]</span>
                      </template>
                    </v-autocomplete>
                  </v-col>
                  <v-col cols="3">
                    <v-btn small color="indigo" dark :disabled="!addTerm_selectedClsfWord"
                      @click="addClassificationWord">
                      <v-icon small left>mdi-plus</v-icon>형식단어 추가
                    </v-btn>
                  </v-col>
                </v-row>
              </v-sheet>

              <!-- 단어 순서 변경 (선택된 단어가 있을 때만) -->
              <v-row v-if="addTerm_wordList.length > 0" align="center">
                <v-col cols="4"><v-subheader>단어 순서</v-subheader></v-col>
                <v-col cols="8">
                  <v-list dense>
                    <v-list-item v-for="(item, index) in addTerm_wordList" :key="'add-ord-' + index" class="liStyle">
                      <span class="indexStyle">{{ index + 1 }}</span>
                      <span :style="{ width: 'calc(100% - 60px)' }">{{ item.wordNm }}</span>
                      <div :style="{ width: '60px' }">
                        <v-icon :class="{ 'iconShow': index !== 0, 'iconHide': index === 0 }" title="위로 이동"
                          :style="{ transform: 'rotate(180deg)' }"
                          @click="moveItemUp(index, 'add')">arrow_drop_down_circle</v-icon>
                        <v-icon
                          :class="{ 'iconShow': addTerm_wordList.length - 1 !== index, 'iconHide': addTerm_wordList.length - 1 === index }"
                          title="아래로 이동" @click="moveItemDown(index, 'add')">arrow_drop_down_circle</v-icon>
                      </div>
                    </v-list-item>
                  </v-list>
                </v-col>
              </v-row>
            </div>

            <v-divider class="my-3" v-if="addTerm_wordList.length > 0"></v-divider>

            <!-- 86번 #40/41 — 최종 용어명 미리보기는 footer 왼쪽으로 이동 (NdModal subbtn slot) -->

            <!-- ③ 도메인 (분석 결과 + 단어 선택이 있을 때만 노출 — 86번 #25 progressive disclosure) -->
            <v-row v-if="addTerm_lastWordIsCode && addTerm_wordList.length > 0" align="center">
              <v-col cols="4"><v-subheader class="reqText">도메인 유형</v-subheader></v-col>
              <v-col cols="8">
                <v-radio-group v-model="addTerm_domainType" row dense hide-details class="mt-0"
                  @change="onAddDomainTypeChange">
                  <v-radio color="ndColor" label="일반 도메인" value="domain"></v-radio>
                  <v-radio color="ndColor" label="코드" value="code"></v-radio>
                </v-radio-group>
              </v-col>
            </v-row>

            <v-row v-if="addTerm_domainType === 'domain' && addTerm_wordList.length > 0" align="center">
              <v-col cols="4"><v-subheader class="reqText">도메인명</v-subheader></v-col>
              <v-col cols="8">
                <v-autocomplete dense required color="ndColor" v-model="addTerm_domainNm" ref="addTerm_domainNm"
                  :items="addTerm_domainNmItems" :rules="[v => !!v || '도메인명은 필수 입력값입니다.']" placeholder="선택"
                  hide-details="auto" :menu-props="{ top: false, offsetY: true }">
                  <template v-slot:no-data><v-list-item><v-list-item-title></v-list-item-title></v-list-item></template>
                </v-autocomplete>
              </v-col>
            </v-row>

            <v-row v-if="addTerm_domainType === 'code' && addTerm_lastWordIsCode && addTerm_wordList.length > 0" align="center">
              <v-col cols="4"><v-subheader class="reqText">코드 선택</v-subheader></v-col>
              <v-col cols="8">
                <div class="d-flex align-center" :style="{ gap: '8px' }">
                  <v-text-field v-model="addTerm_selectedCodeLabel" placeholder="코드를 검색하여 선택" dense outlined
                    hide-details="auto" readonly @click="openCodePicker"></v-text-field>
                  <v-btn class="gradient white--text" small @click="openCodePicker"><v-icon small left>search</v-icon>검색</v-btn>
                </div>
                <div v-if="addTerm_selectedCode && addTerm_selectedCode.domainNm" class="caption grey--text mt-1">
                  도메인: {{ addTerm_selectedCode.domainNm }} / 타입: {{ addTerm_selectedCode.dataType || '-' }} / 길이: {{ addTerm_selectedCode.dataLen || '-' }}
                </div>
              </v-col>
            </v-row>

            <v-row v-if="addTerm_wordList.length > 0" align="center">
              <v-col cols="4"><v-subheader class="reqText">용어 설명</v-subheader></v-col>
              <v-col cols="8">
                <v-textarea clearable dense color="ndColor" rows="1" v-model="addTerm_termDesc" ref="addTerm_termDesc"
                  placeholder="사람이나 기계 등이 움직이거나 행동을 시작한 날짜"
                  :rules="[() => !!addTerm_termDesc || '용어 설명은 필수 입력값입니다.']"></v-textarea>
              </v-col>
            </v-row>

            <!-- ④ 메타 (접기 가능, default 접힘) — 86번 #25 분석 결과 있을 때만 -->
            <v-expansion-panels v-if="addTerm_wordList.length > 0" flat class="mt-2">
              <v-expansion-panel>
                <v-expansion-panel-header class="px-2 py-1 grey--text text--darken-1">
                  추가 메타 (이음동의어 / 코드그룹 / 담당기관 / 공통표준여부 / 제정차수 / 시스템CD)
                </v-expansion-panel-header>
                <v-expansion-panel-content>
                  <v-row align="center">
                    <v-col cols="4"><v-subheader>이음동의어 목록</v-subheader></v-col>
                    <v-col cols="8">
                      <v-col class="colInBtnWrap" v-for="addTerm_allophSynm in addTerm_allophSynmLst_arr"
                        :key="addTerm_allophSynm.id">
                        <v-text-field :for="addTerm_allophSynm.value" v-model="addTerm_allophSynm.value" dense
                          color="ndColor" ref="addTerm_allophSynmLst_arr" placeholder="" hide-details></v-text-field>
                        <v-btn class="gradient colInBtn" v-show="addTerm_allophSynm.addBtnView"
                          v-on:click="addAllophSynmLst()" title="추가">추가</v-btn>
                        <v-btn class="colInBtn white--text" color="gray" v-show="addTerm_allophSynm.removeBtnView"
                          v-on:click="removeAllophSynmLst(addTerm_allophSynm.id)" title="삭제">삭제</v-btn>
                      </v-col>
                    </v-col>
                  </v-row>
                  <v-row v-if="addTerm_domainType !== 'code'" align="center">
                    <v-col cols="4"><v-subheader>코드그룹</v-subheader></v-col>
                    <v-col cols="8">
                      <v-text-field v-model="addTerm_codeGrp" dense color="ndColor" placeholder=""
                        hide-details @input="addTerm_codeGrp = (addTerm_codeGrp || '').toUpperCase()"></v-text-field>
                    </v-col>
                  </v-row>
                  <v-row align="center">
                    <v-col cols="4"><v-subheader>담당기관명</v-subheader></v-col>
                    <v-col cols="8">
                      <v-text-field v-model="addTerm_chrgOrg" dense color="ndColor" placeholder="" hide-details></v-text-field>
                    </v-col>
                  </v-row>
                  <v-row align="center">
                    <v-col cols="4"><v-subheader>공통표준여부</v-subheader></v-col>
                    <v-col cols="8">
                      <v-radio-group v-model="addTerm_commStndYn" row mandatory dense hide-details>
                        <v-radio color="ndColor" label="Y" value="Y"></v-radio>
                        <v-radio color="ndColor" label="N" value="N"></v-radio>
                      </v-radio-group>
                    </v-col>
                  </v-row>
                  <v-row align="center">
                    <v-col cols="4"><v-subheader>제정차수</v-subheader></v-col>
                    <v-col cols="8">
                      <v-text-field v-model="addTerm_magntdOrd" dense color="ndColor" placeholder="1차" hide-details></v-text-field>
                    </v-col>
                  </v-row>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
          </v-form>
        </template>
      </NdModal>
    </v-dialog>

    <!-- 코드 picker 다이얼로그 (모달 안의 모달) -->
    <v-dialog v-model="codePickerShow" max-width="720" persistent>
      <v-card>
        <v-card-title class="text-subtitle-1">코드 선택</v-card-title>
        <v-card-text class="pb-0">
          <v-text-field v-model="codePickerSearch" placeholder="코드명 / 코드그룹 / 도메인 검색" dense outlined
            prepend-inner-icon="search" hide-details clearable></v-text-field>
          <v-data-table class="mt-3" :headers="codePickerHeaders" :items="codePickerFilteredItems"
            item-key="codeNm" :items-per-page="10" dense fixed-header height="320"
            @click:row="pickCode" :footer-props="{ 'items-per-page-options': [10, 25, 50] }">
            <template v-slot:item.actions="{ item }">
              <v-btn small color="ndColor" dark @click.stop="pickCode(item)">선택</v-btn>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="codePickerShow = false">취소</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <!-- update term Modal -->
    <v-dialog max-width="950px" v-model="updateTermModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true"
        :header-title="'용어 수정 ' + (updateTerm_user_selected_word.length > 0 ? '- ' + updateTerm_user_selected_word : '')"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-form ref="form">
              <v-stepper v-model="updateModalStep" vertical :style="{ boxShadow: 'none !important' }">
                <v-stepper-step :complete="updateModalStep > 1" step="1" color="ndColor" v-on:click="updateModalStep = 1">
                  용어명 입력
                  <!-- <small>Summarize if needed</small> -->
                </v-stepper-step>

                <v-stepper-content step="1">
                  <v-row>
                    <v-col cols="4">
                      <v-subheader class="reqText">용어명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_termNm" ref="updateTerm_termNm"
                        :rules="[() => !!updateTerm_termNm || '용어명은 필수 입력값입니다.']" clearable required dense
                        placeholder="가동개시일자" color="ndColor" v-on:keyup.enter="updateNextStep(1)"></v-text-field>
                    </v-col>
                  </v-row>

                  <v-col class="text-right">
                    <v-btn class="white--text" color="ndColor" @click="updateNextStep(1)"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      다음
                    </v-btn>
                  </v-col>
                </v-stepper-content>

                <v-stepper-step :complete="updateModalStep > 2" step="2" color="ndColor" v-on:click="updateNextStep(1)">
                  단어 목록 선택
                </v-stepper-step>

                <v-stepper-content step="2">
                  <v-row v-for="(item, index) in updateTerm_wordListArr" :key="index"
                    :style="{ margin: '0px 0px 20px 0px' }">
                    <h3 :style="{ margin: '10px 0px' }">{{ item.wordNm }}
                      <v-chip v-if="item.wordLst && item.wordLst.length > 0" x-small color="green" text-color="white" class="ml-2">등록됨</v-chip>
                      <v-chip v-else x-small color="red" text-color="white" class="ml-2">미등록</v-chip>
                    </h3>
                    <v-col cols="12" :style="{ padding: '0px' }">
                      <v-data-table v-if="item.wordLst && item.wordLst.length > 0"
                        id="updateTerm_wordList_table" class="px-4 pb-3" :headers="wordListHeader"
                        :items="item.wordLst" item-key="index" v-model="updateTerm_selected_word_list[index]"
                        :value="updateTerm_selected_word_list[index]" hide-default-footer show-select>
                      </v-data-table>
                      <v-sheet v-else outlined rounded class="pa-3 mx-4">
                        <v-row dense>
                          <v-col cols="3">
                            <v-text-field v-model="item.inlineWordNm" dense outlined hide-details
                              label="단어 한글명" :placeholder="item.wordNm" />
                          </v-col>
                          <v-col cols="3">
                            <v-text-field v-model="item.inlineWordEngAbrvNm" dense outlined hide-details
                              label="영문약어" @input="item.inlineWordEngAbrvNm = (item.inlineWordEngAbrvNm || '').toUpperCase()" />
                          </v-col>
                          <v-col cols="3">
                            <v-text-field v-model="item.inlineWordEngNm" dense outlined hide-details
                              label="영문명" @input="item.inlineWordEngNm = (item.inlineWordEngNm || '').toUpperCase()" />
                          </v-col>
                          <v-col cols="3" class="d-flex align-center">
                            <v-btn small color="primary" :loading="item.inlineSaving"
                              @click="inlineRegisterWord(index)">단어 등록</v-btn>
                          </v-col>
                        </v-row>
                      </v-sheet>
                    </v-col>
                  </v-row>

                  <!-- 버튼 -->
                  <v-col class="text-right">
                    <v-btn text class="gray white--text" @click="updateModalStep = 1"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      이전
                    </v-btn>
                    <v-btn class="white--text" color="ndColor" v-on:click="updateNextStep(2)"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      다음
                    </v-btn>
                  </v-col>
                </v-stepper-content>

                <v-stepper-step :complete="updateModalStep > 3" step="3" color="ndColor">
                  용어 정보 입력
                </v-stepper-step>

                <v-stepper-content step="3" color="ndColor">

                  <!-- 단어 순서 변경 -->
                  <v-row>
                    <v-col cols="4">
                      <v-subheader>단어 순서 변경</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-list>
                        <v-list-item v-for="(item, index) in updateTerm_wordList" :key="index" class="liStyle">
                          <span class="indexStyle">{{ index + 1 }}</span>
                          <span :style="{ width: 'calc(100% - 60px)' }">
                            {{ item.wordNm }}
                          </span>
                          <div :style="{ width: '60px' }">
                            <v-icon :class="{ 'iconShow': index !== 0, 'iconHide': index === 0 }" title="위로 이동"
                              :style="{ transform: 'rotate(180deg)' }"
                              @click="moveItemUp(index, 'update')">arrow_drop_down_circle</v-icon>
                            <v-icon
                              :class="{ 'iconShow': updateTerm_wordList.length - 1 !== index, 'iconHide': updateTerm_wordList.length - 1 === index }"
                              title="아래로 이동" @click="moveItemDown(index, 'update')">arrow_drop_down_circle</v-icon>
                          </div>
                        </v-list-item>
                      </v-list>
                    </v-col>
                  </v-row>
                  <!--  -->
                  <v-divider :style="{ margin: '25px 0' }"></v-divider>
                  <!--  -->

                  <v-row>
                    <v-col cols="4">
                      <v-subheader class="reqText">용어 영문 약어명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_termEngAbrvNm" required dense color="ndColor" readonly
                        filled></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row v-if="updateTerm_lastWordIsCode">
                    <v-col cols="4">
                      <v-subheader class="reqText">도메인 유형</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-radio-group v-model="updateTerm_domainType" row dense hide-details class="mt-0"
                        @change="onUpdateDomainTypeChange">
                        <v-radio color="ndColor" label="일반 도메인" value="domain"></v-radio>
                        <v-radio color="ndColor" label="코드" value="code"></v-radio>
                      </v-radio-group>
                    </v-col>
                  </v-row>

                  <v-row v-if="updateTerm_domainType === 'domain'">
                    <v-col cols="4">
                      <v-subheader class="reqText">도메인명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-autocomplete dense required color="ndColor" v-model="updateTerm_domainNm" :placeholder="'선택'"
                        ref="updateTerm_domainNm" :items="updateTerm_domainNmItems"
                        :rules="[v => !!v || '도메인명은 필수 입력값입니다.']" :menu-props="{ top: false, offsetY: true }">
                        <template v-slot:no-data>
                          <v-list-item>
                            <v-list-item-title>
                            </v-list-item-title>
                          </v-list-item>
                        </template>
                      </v-autocomplete>
                    </v-col>
                  </v-row>

                  <v-row v-if="updateTerm_domainType === 'code' && updateTerm_lastWordIsCode">
                    <v-col cols="4">
                      <v-subheader class="reqText">코드 선택</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-autocomplete dense required color="ndColor" v-model="updateTerm_selectedCode"
                        :items="updateTerm_codeInfoList" item-text="codeNm" return-object
                        :placeholder="'코드 검색'" :menu-props="{ top: false, offsetY: true }"
                        @change="onUpdateCodeSelected">
                        <template v-slot:item="{ item }">
                          <span>{{ item.codeNm }} <span class="caption grey--text">[{{ item.codeGrp }}] ({{ item.domainNm || '-' }})</span></span>
                        </template>
                        <template v-slot:selection="{ item }">
                          {{ item.codeNm }} [{{ item.codeGrp }}]
                        </template>
                      </v-autocomplete>
                      <div v-if="updateTerm_selectedCode && updateTerm_selectedCode.domainNm" class="caption grey--text mt-1">
                        도메인: {{ updateTerm_selectedCode.domainNm }} / 타입: {{ updateTerm_selectedCode.dataType || '-' }} / 길이: {{ updateTerm_selectedCode.dataLen || '-' }}
                      </div>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader class="reqText">용어 설명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-textarea clearable dense color="ndColor" rows="1" v-model="updateTerm_termDesc"
                        ref="updateTerm_termDesc" placeholder="사람이나 기계 등이 움직이거나 행동을 시작한 날짜"
                        :rules="[() => !!updateTerm_termDesc || '용어 설명은 필수 입력값입니다.']"></v-textarea>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>이음동의어 목록</v-subheader>
                    </v-col>

                    <v-col cols="8">
                      <v-col class="colInBtnWrap" v-for="updateTerm_allophSynm in updateTerm_allophSynmLst_arr"
                        :key="updateTerm_allophSynm.id">
                        <v-text-field :for="updateTerm_allophSynmLst_arr.value" v-model="updateTerm_allophSynm.value"
                          dense color="ndColor" ref="updateTerm_allophSynmLst_arr" placeholder=""
                          hide-details></v-text-field>
                        <v-btn class="gradient colInBtn" v-show="updateTerm_allophSynm.addBtnView"
                          v-on:click="addAllophSynmLst()" title="추가">추가</v-btn>
                        <v-btn class="colInBtn white--text" color="gray" v-show="updateTerm_allophSynm.removeBtnView"
                          v-on:click="removeAllophSynmLst(updateTerm_allophSynm.id)" title="삭제">삭제</v-btn>
                      </v-col>
                    </v-col>
                  </v-row>

                  <v-row v-if="updateTerm_domainType !== 'code'">
                    <v-col cols="4">
                      <v-subheader>코드그룹</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_codeGrp" dense color="ndColor" placeholder=""
                        @input="updateTerm_codeGrp = (updateTerm_codeGrp || '').toUpperCase()"></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>담당기관명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_chrgOrg" dense color="ndColor" placeholder=""></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>공통표준여부</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-radio-group v-model="updateTerm_commStndYn" row mandatory dense hide-details>
                        <v-radio color="ndColor" label="Y" value="Y"></v-radio>
                        <v-radio color="ndColor" label="N" value="N"></v-radio>
                      </v-radio-group>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>제정차수</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
                    </v-col>
                  </v-row>

                  
                  <v-col class="text-right">
                    <v-btn text class="gray white--text" @click="updateModalStep = 2"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      이전
                    </v-btn>
                  </v-col>
                </v-stepper-content>
              </v-stepper>
            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- 일괄등록 Modal -->
    <v-dialog max-width="520" v-model="collectiveTermModalShow" persistent>
      <v-card>
        <v-card-title class="pb-2" :style="{ fontSize: '1rem', fontWeight: 'bold' }">
          <v-icon left color="ndColor">mdi-upload</v-icon>
          용어 일괄등록 진행
        </v-card-title>
        <v-progress-linear v-if="isUploading" indeterminate color="ndColor" height="3"></v-progress-linear>
        <v-card-text class="pt-3 pb-2">
          <div ref="uploadLogBox"
            :style="{ maxHeight: '280px', overflowY: 'auto', fontFamily: 'monospace', fontSize: '0.82rem', background: '#f8f8f8', border: '1px solid #e0e0e0', borderRadius: '4px', padding: '10px 12px' }">
            <div v-for="(log, i) in uploadLogs" :key="i"
              :style="{ color: log.level === 'ERROR' ? '#d32f2f' : log.level === 'DONE' ? '#1976d2' : '#333', fontWeight: log.level === 'DONE' ? 'bold' : 'normal', lineHeight: '1.7' }">
              <span :style="{ color: '#999', marginRight: '8px' }">{{ log.time }}</span>{{ log.msg }}
            </div>
            <div v-if="uploadLogs.length === 0" :style="{ color: '#999' }">대기 중...</div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn v-if="!isUploading && uploadFailList.length > 0" color="red" text @click="downloadFailList">
            <v-icon left small>mdi-download</v-icon>실패 목록 다운로드 ({{ uploadFailList.length }}건)
          </v-btn>
          <v-btn v-if="!isUploading" color="ndColor" text @click="collectiveTermModalShow = false">닫기</v-btn>
          <template v-else>
            <span :style="{ fontSize: '0.8rem', color: '#999', paddingRight: '8px' }">처리 중...</span>
            <v-btn color="grey" text @click="forceCloseUploadModal">강제 닫기</v-btn>
          </template>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';
import NdModal from "./../views/modal/NdModal.vue"
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import { eventBus } from './../eventBus.js'

export default {
  name: 'DSTerm',
  props: ['isMobile'],
  components: {
    NdModal,
    Treeselect
  },
  watch: {
    termItems() {
      this.setListPage();
    },
    itemsPerPage(val) {
      this.setListPage();
      localStorage.setItem('DSTerm_itemsPerPage', val);
    },
    addModalStep() {
      if (this.addModalStep === 1) {
        this.addTerm_selected_word = {};
        this.addTerm_selected_word_list = [];
      }
    },
    updateModalStep() {
      if (this.updateModalStep === 1) {
        this.updateTerm_selected_word_list = [];
      }
    },
    addTermModalShow() {
      if (this.addTermModalShow === false) {
        this.addFormReset();
      }
    },
    updateTermModalShow() {
      if (this.updateTermModalShow === false) {
        this.updateFormReset();
      }
    },
    addTerm_selected_word_list: {
      handler(val) {
        // 86번 #27 — checkSelectedWordStatus 호출 제거.
        //   옛 stepper UX 잔재로 partOfSpeech 누락 시 "명사가 아닙니다" false positive.
        //   새 UX 는 _applyAnalyzedWords / _postValidateNewTokens / inlineRegisterWord 가
        //   addTerm_wordList 를 collectSelectedItems 로 동기화 — selected 변경 시 그것만 호출.
        this.collectSelectedItems();
      },
      deep: true
    },
    updateTerm_selected_word_list: {
      handler(val) {
        // 86번 #27 — addTerm_selected_word_list 와 동일 처리 (옛 NN 검사 제거)
        this.collectSelectedItems();
      },
      deep: true
    },
    addTerm_wordList() {
      // 용어 등록 title 옆에 사용자가 선택한 단어 보여주기
      this.createWordToTerm(this.addTerm_wordList)
      this.createTermEngAbrvNm(this.addTerm_wordList)
      // 86번 #32 — 마지막 단어가 코드가 아니게 되면 (순서 변경 등) 코드 도메인 상태 클리어
      //   "도메인 유형" 라디오는 사라지지만 domainType='code' 가 유지돼서 코드 선택 영역이 잔존하던 버그.
      if (!this.addTerm_lastWordIsCode && this.addTerm_domainType === 'code') {
        this.addTerm_domainType = 'domain';
        this.addTerm_selectedCode = null;
        this.addTerm_selectedCodeLabel = '';
        this.addTerm_codeGrp = null;
      }
    },
    /** 81번 — 한글 용어명 입력 1초 디바운스 후 자동 분석 (단어 분리 + 매칭 결과) */
    addTerm_termNm(val) {
      if (!this.addTermModalShow) return;
      if (this._addTerm_analyzeTimer) {
        clearTimeout(this._addTerm_analyzeTimer);
        this._addTerm_analyzeTimer = null;
      }
      // 빈 값이면 결과만 비우고 종료 (의도적 clear 인 경우 분석 호출 X)
      if (!val || !val.trim()) {
        this.addTerm_wordListArr = [];
        this.addTerm_selected_word_list = [];
        this.addTerm_wordList = [];
        this.addTerm_termEngAbrvNm = null;
        return;
      }
      var self = this;
      this._addTerm_analyzeTimer = setTimeout(function() {
        self.runAutoAnalyze();
      }, 1000);
    },
    updateTerm_wordList() {
      // 용어 수정 title 옆에 사용자가 선택한 단어 보여주기
      this.createWordToTerm(this.updateTerm_wordList)
      this.createTermEngAbrvNm(this.updateTerm_wordList)
      // 86번 #32 — 수정 모달도 동일 처리
      if (!this.updateTerm_lastWordIsCode && this.updateTerm_domainType === 'code') {
        this.updateTerm_domainType = 'domain';
        this.updateTerm_selectedCode = null;
        this.updateTerm_selectedCodeLabel = '';
        this.updateTerm_codeGrp = null;
      }
    }
  },
  data: () => ({
    // 용어 목록
    termItems: [],

    // 검색 조건 시작

    // 86번 #11 — 검색 모드 옵션 (포함/완전일치/앞/뒤)
    searchModeOptions: [
      { value: 'contains', label: '포함' },
      { value: 'exact',   label: '완전 일치' },
      { value: 'start',   label: '앞' },
      { value: 'end',     label: '뒤' },
    ],

    //검색 용어명
    searchTerm: '',
    searchTermMode: 'contains',

    //검색 등록일자 (범위, YYYY-MM-DD)
    searchFromDt: '',
    searchToDt: '',

    //검색 용어영문약어명
    searchEngTerm: '',
    searchEngTermMode: 'contains',
    //검색 도메인명
    searchDomain: '',

    
    // 관리자 여부
    isAdmin: false,
    // 검색 승인 여부
    searchApproval: true,
    // 검색 이후 용어 리스트 다시보기 버튼 보이기
    // resetBtnShow: false,
    // 등록 모달 보이기
    addTermModalShow: false,
    // 수정 모달 보이기
    updateTermModalShow: false,
    // 선택한 용어의 정보들
    selectedItem: [],
    // 선택한 용어 이름
    detailTerm: null,
    // 일괄 등록 파일
    excelFile: null,
    // 일괄 등록 진행 다이얼로그
    collectiveTermModalShow: false,
    // 일괄 등록 진행 상태
    isUploading: false,
    // 일괄 등록 로그
    uploadLogs: [],
    // 일괄 등록 실패 목록
    uploadFailList: [],
    // 디테일 메뉴 탭
    detailTab: [
      { title: '용어 상세 보기', name: 'tab1', index: 0 },
      { title: '단어 구성 목록', name: 'tab2', index: 1 }
    ],
    // 테이블 로딩
    loadTable: true,
    activeDetailTab: 'tab1',
    // 삭제 관련
    removeItems: [],
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 용어의 수
    itemsPerPage: parseInt(localStorage.getItem('DSTerm_itemsPerPage')) || 10,
    // 등록 관련
    addModalStep: 1, // 용어 등록 스테퍼 카운트
    addTerm_termNm: null, // 용어 등록 용어명
    addTerm_wordListArr: [], // 용어 등록 단어 목록 배열
    addTerm_termEngAbrvNm: null, // 용어 등록 용어영문명 (자동 생성하여 보여줌)
    addTerm_termDesc: null,
    addTerm_domainType: 'domain', // 'domain' | 'code'
    addTerm_domainNm: null,
    addTerm_domainNmItems: [],
    addTerm_selectedCode: null,   // 코드 선택 시 선택된 코드 객체
    addTerm_codeInfoList: [],     // 코드 목록
    addTerm_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    addTerm_allophSynmLst_count: 0,
    addTerm_codeGrp: null,
    addTerm_chrgOrg: null,
    addTerm_commStndYn: 'N',
    addTerm_magntdOrd: null,
    addTerm_reqSysCd: null,
    addTerm_selected_word_list: [],
    // addTerm_word_length: 0,
    addTerm_wordList: [],
    addTerm_user_selected_word: '',
    addTerm_lastCheckedNm: null,
    // 86번 #23 — 자동 분석 결과 1순위/2순위 토글 + 수동 단어 추가
    addTerm_lastAnalysis: null,        // 마지막 analyzeTermsBatch 응답 원본 (alternativeWords 포함)
    addTerm_splitMode: 0,              // 0=추천1, 1=추천2
    addTerm_manualWordInput: '',       // 수동 추가 입력
    // 86번 #39 — 형식단어 검색/추가
    addTerm_classWords: [],            // [{wordId, wordNm, wordEngAbrvNm, wordEngNm, domainClsfNm}]
    addTerm_selectedClsfWord: null,
    addTerm_loadingClsfWords: false,
    // 수정 관련
    updateModalStep: 1, // 용어 수정 스테퍼 카운트
    updateTerm_id: null,
    updateTerm_wordListObj: {}, // 용어 등록 단어 목록 오브젝트
    updateTerm_wordListArr: [], // 용어 등록 단어 목록 배열
    updateTerm_termNm: null,
    updateTerm_termEngAbrvNm: null,
    updateTerm_termDesc: null,
    updateTerm_domainType: 'domain', // 'domain' | 'code'
    updateTerm_domainNm: null,
    updateTerm_domainNmItems: [],
    updateTerm_selectedCode: null,
    updateTerm_codeInfoList: [],
    updateTerm_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    updateTerm_allophSynmLst_count: 0,
    updateTerm_codeGrp: null,
    updateTerm_chrgOrg: null,
    updateTerm_commStndYn: null,
    updateTerm_magntdOrd: null,
    updateTerm_reqSysCd: null,
    updateTerm_selected_word_list: [],
    updateTerm_word_length: 0,
    updateTerm_wordList: [],
    updateTerm_user_selected_word: '',

    // 81번 — 자동 분석 디바운스 + 코드 picker
    addTerm_analyzing: false,
    _addTerm_analyzeTimer: null,
    codePickerShow: false,
    codePickerSearch: '',
    addTerm_selectedCodeLabel: '',
    codePickerHeaders: [
      { text: '코드명',   value: 'codeNm',   sortable: false, align: 'left',   width: '32%' },
      { text: '코드그룹', value: 'codeGrp',  sortable: false, align: 'left',   width: '20%' },
      { text: '도메인',   value: 'domainNm', sortable: false, align: 'left',   width: '20%' },
      { text: '타입',     value: 'dataType', sortable: false, align: 'center', width: '14%' },
      { text: '',         value: 'actions',  sortable: false, align: 'center', width: '14%' },
    ],

    // 상단 테이블 헤더
    termHeaders: [
      { text: '용어명', align: 'center', sortable: false, value: 'termsNm', width: '10%' },
      { text: '용어영문약어명', sortable: false, align: 'center', value: 'termsEngAbrvNm', width: '10%' },
      { text: '용어설명', sortable: false, align: 'center', value: 'termsDesc' },
      { text: '도메인명', sortable: false, align: 'center', value: 'domainNm', width: '8%' },
      { text: '코드그룹', sortable: false, align: 'center', value: 'codeGrp', width: '8%' },
      { text: '담당기관명', sortable: false, align: 'center', value: 'chrgOrg', width: '8%' },
    ],
    // 하단 테이블 헤더
    detaileHeaders: [
      { text: '용어명', align: 'center', sortable: false, value: 'termsNm', width: '15%' },
      { text: '용어영문약어명', sortable: false, align: 'center', value: 'termsEngAbrvNm', width: '15%' },
      { text: '용어설명', sortable: false, align: 'center', value: 'termsDesc' },
      { text: '도메인명', sortable: false, align: 'center', value: 'domainNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '코드그룹', sortable: false, align: 'center', value: 'codeGrp', width: '15%' },
      { text: '담당기관명', sortable: false, align: 'center', value: 'chrgOrg', width: '15%' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn', width: '15%' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd', width: '15%' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn', width: '15%' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt', width: '15%' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt', width: '15%' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId', width: '15%' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt', width: '15%' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId', width: '15%' },
    ],
    // 단어 구성 목록
    wordItemsList: [],
    // 단어 구성 목록 헤더
    wordItemsListHeaders: [
      { text: '단어명', align: 'center', sortable: false, value: 'wordNm', width: '5%' },
      { text: '단어영문약어명', align: 'center', sortable: false, value: 'wordEngAbrvNm', width: '8%' },
      { text: '단어영문명', align: 'center', sortable: false, value: 'wordEngNm', width: '15%' },
      { text: '단어설명', align: 'center', sortable: false, value: 'wordDesc' },
      { text: '형식단어여부', align: 'center', sortable: false, value: 'wordClsfYn', width: '7%' },
      { text: '도메인분류명', sortable: false, align: 'center', value: 'domainClsfNm', width: '7%' },
    ],
    // 용어 등록/수정 단어 목록 테이블 헤더
    wordListHeader: [
      { text: '단어명', align: 'center', sortable: false, value: 'wordNm', width: '12%' },
      { text: '단어영문명', align: 'center', sortable: false, value: 'wordEngNm', width: '18%' },
      { text: '단어영문약어명', align: 'center', sortable: false, value: 'wordEngAbrvNm', width: '20%' },
      { text: '단어설명', align: 'center', sortable: false, value: 'wordDesc' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
    // 승인 시스템에서 사용할 시스템 네임 리스트
    systemNameList: [],
  }),
  computed: {
    /** 86번 #41 — 최종 용어명 footer 의 도메인+타입 라벨. 코드면 코드명/그룹, 일반이면 도메인V타입(길이) */
    addTerm_finalDomainLabel() {
      if (this.addTerm_domainType === 'code' && this.addTerm_selectedCode) {
        var c = this.addTerm_selectedCode;
        var label = c.codeNm || c.codeGrp || '';
        if (c.dataType) {
          label += ' ' + c.dataType;
          if (c.dataLen) label += '(' + c.dataLen + ')';
        }
        return label;
      }
      if (this.addTerm_domainNm) {
        // 도메인 리스트에서 dataType/dataLen 찾기 (addTerm_domainNmItems 는 이름만 — 별도 캐시 없음)
        // 간단하게 도메인명만 표시. (타입까지 표시하려면 추가 로드 필요 — domainsByNm 캐시 만드는 건 추후)
        return this.addTerm_domainNm;
      }
      return '';
    },
    /** 마지막 단어가 CD(코드)인지 여부 - 등록 */
    addTerm_lastWordIsCode() {
      var list = this.addTerm_wordList;
      if (!list || list.length === 0) return false;
      var last = list[list.length - 1];
      var abrv = (last.wordEngAbrvNm || last.wordNm || '').toUpperCase();
      return abrv === 'CD';
    },
    /** 마지막 단어가 CD(코드)인지 여부 - 수정 */
    updateTerm_lastWordIsCode() {
      var list = this.updateTerm_wordList;
      if (!list || list.length === 0) return false;
      var last = list[list.length - 1];
      var abrv = (last.wordEngAbrvNm || last.wordNm || '').toUpperCase();
      return abrv === 'CD';
    },
    /** 81번 — 코드 picker 검색 필터 */
    codePickerFilteredItems() {
      var q = (this.codePickerSearch || '').trim().toLowerCase();
      var list = this.addTerm_codeInfoList || [];
      if (!q) return list;
      return list.filter(c =>
        (c.codeNm   || '').toLowerCase().includes(q) ||
        (c.codeGrp  || '').toLowerCase().includes(q) ||
        (c.domainNm || '').toLowerCase().includes(q));
    },
  },
  methods: {
    /** 86번 #11 — 백엔드 raw exception 차단, 친화적 메세지로 정리 */
    _friendlyErrText(err, fallback) {
      const status = (err && err.response && err.response.status) || 0;
      const data   = (err && err.response && err.response.data) || {};
      const our    = data.resultMessage;
      const raw    = data.message;
      const rawIsTechnical = raw && /JSON|deserialize|parse|MismatchedInput|HttpMessageNotReadable|Exception|NullPointer|invalid|cannot/i.test(raw);
      if (our) return our;
      if (raw && !rawIsTechnical) return raw;
      if (status >= 500) return (fallback || '서버 처리 중 오류가 발생했습니다.') + ' (관리자에게 문의해 주세요)';
      if (status === 400) return (fallback || '입력값이 올바르지 않습니다.');
      if (status === 401 || status === 403) return '권한이 없습니다.';
      if (status === 404) return '요청한 자원을 찾을 수 없습니다.';
      return fallback || (err && err.message) || '알 수 없는 오류가 발생했습니다.';
    },
    resetSearch() {
      this.searchTerm = '';
      this.searchTermMode = 'contains';
      this.searchEngTerm = '';
      this.searchEngTermMode = 'contains';
      this.searchDomain = '';
      this.searchFromDt = '';
      this.searchToDt = '';
      this.searchApproval = true;
    },
    getSystemList() {
      // 시스템 리스트 가지고 오기
      try {
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList')
          .then((res) => {
            let _list = res.data;

            const treeData = []
            const idMap = {}

            // 노드 생성 및 idMap 구성
            _list.forEach(node => {
              idMap[node.sysCd] = { ...node, children: [] }
            })

            // children 속성 채워넣기
            Object.values(idMap).forEach(node => {
              if (node.parentSysCd) {
                idMap[node.parentSysCd].children.push(node)
              } else {
                treeData.push(node)
              }
            })

            function removeEmptyChildren(node) {
              // 노드 및 하위 노드에 대한 속성 채우기
              node.id = node.sysCd
              node.label = node.sysNm

              // 자식 요소가 없으면 지우기
              if (node.children.length === 0) {
                delete node.children;
              } else {
                node.children.forEach(childNode => removeEmptyChildren(childNode));
              }
            }

            treeData.forEach(node => removeEmptyChildren(node));

            this.systemNameList = treeData;

          })
          .catch((err) => {
            console.log(err);
            this.$swal.fire({
              title: '시스템 목록 조회 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log(error);
        this.$swal.fire({
          title: '시스템 목록 조회 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.termItems.length / this.itemsPerPage);
    },
    getTermData() {
      this.loadTable = true;
      // 용어 리스트 불러오기
      try {

        //검색 조건 세팅
        let schNm = null;
        let searchEngTerm = null;
        let searchDomain = null;
        let schAprvYn = ''
        if (this.searchTerm !== '') {
          schNm = this.searchTerm
        }

            if (this.searchEngTerm !== '') {
          searchEngTerm = this.searchEngTerm
        }
    if (this.searchDomain !== '') {
          searchDomain = this.searchDomain
        }
        if (this.searchApproval === true) {
          schAprvYn = 'Y'
        } else {
          schAprvYn = 'N'
        }

        let _url = this.$APIURL.base + "api/std/getTermsList";

        axios.post(_url, {
          'schNm': schNm,
          'schNmMode': this.searchTermMode,
          'searchEngTerm': searchEngTerm,
          'searchEngTermMode': this.searchEngTermMode,
          'searchDomain': searchDomain,
          'schAprvYn': schAprvYn,
          'from': this.searchFromDt ? this.searchFromDt.replace(/-/g, '') + '000000' : null,
          'to': this.searchToDt ? this.searchToDt.replace(/-/g, '') + '235959' : null
        }).then((res) => {
          // console.log(res.data)
          this.termItems = res.data;

          // console 표시
          console.log("📃 Term LIST ↓↓↓")
          console.log(this.termItems);

          // 하단 상세보기 초기화
          this.resetDetail();

          this.loadTable = false;

        }).catch((err) => {
          this.$swal.fire({
            title: '용어 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }
    },
    readExcelFile(event) {
      // 용어 일괄 등록에서 사용하는 function
      const file = event.target.files[0];

      // 취소일 때 return
      if (file === undefined) {
        return;
      }

      this.excelFile = this.$refs.file.files[0];

      // 진행 다이얼로그 열기
      this.uploadLogs = [];
      this.uploadFailList = [];
      this.isUploading = true;
      this.collectiveTermModalShow = true;

      // 180초 하트비트 타임아웃: 메시지 받을 때마다 리셋됨 (_resetUploadTimer)
      this._resetUploadTimer();

      const _url = this.$APIURL.base + "api/std/uploadTermsList";
      const formData = new FormData();
      formData.append('file', this.excelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).then((res) => {
        if (res.data && res.data.resultCode === 200) {
          this._addUploadLog('INFO', '처리 중... WebSocket 결과를 기다립니다.');
        } else {
          this._addUploadLog('ERROR', '요청 실패: ' + (res.data && res.data.resultMessage));
          this.isUploading = false;
          clearTimeout(this._uploadTimer);
        }
      }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      // input 초기화
      document.getElementById('inputTermUpload').value = '';
    },
    onUploadNotice(msg) {
      if (!this.collectiveTermModalShow) return;
      if (!msg.data || !msg.data.startsWith('[용어]')) return;
      const level = msg.noticeType === 'ERROR' ? 'ERROR' : 'INFO';
      // 하트비트: 메시지 수신 시 타임아웃 리셋 (완료 메시지 전까지)
      if (this.isUploading && !msg.data.includes('완료 -')) {
        this._resetUploadTimer();
      }
      this._addUploadLog(level, msg.data);
      // 실패 항목 수집: "[용어] 실패: 용어(기준일자): 구성단어(BASS) 미등록" 형태
      if (level === 'ERROR' && msg.data.includes('실패:')) {
        var failText = msg.data.replace('[용어] 실패: ', '');
        var termMatch = failText.match(/^용어\(([^)]*)\):\s*(.*)/);
        // 행번호 추출: "[용어] 실패: [N행] 용어(...)" 또는 순차 번호
        var rowIdx = this.uploadFailList.length + 1;
        var rowMatch = failText.match(/^\[(\d+)행\]\s*/);
        if (rowMatch) {
          rowIdx = parseInt(rowMatch[1]);
          failText = failText.replace(rowMatch[0], '');
          termMatch = failText.match(/^용어\(([^)]*)\):\s*(.*)/);
        }
        if (termMatch) {
          this.uploadFailList.push({ rowNo: rowIdx, termsNm: termMatch[1], reason: termMatch[2] });
        } else {
          this.uploadFailList.push({ rowNo: rowIdx, termsNm: '-', reason: failText });
        }
      }
      if (msg.data.includes('완료 -')) {
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
        this.getTermData();
        // 완료 메시지에서 건수 파싱 후 팝업
        const summary = msg.data.replace('[용어] ', '');
        const failMatch = summary.match(/실패:\s*(\d+)건/);
        const failCount = failMatch ? parseInt(failMatch[1]) : 0;
        this.$swal.fire({
          title: '용어 일괄등록 완료',
          text: summary,
          icon: failCount > 0 ? 'warning' : 'success',
          showConfirmButton: false,
          timer: 3000
        });
      }
    },
    _resetUploadTimer() {
      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getTermData();
        }
      }, 180000);
    },
    forceCloseUploadModal() {
      this.isUploading = false;
      clearTimeout(this._uploadTimer);
      this.collectiveTermModalShow = false;
      this.getTermData();
    },
    downloadFailList() {
      if (this.uploadFailList.length === 0) return;
      // BOM + CSV 생성
      var csvContent = '\uFEFFNo,용어명,실패 사유\n';
      for (var i = 0; i < this.uploadFailList.length; i++) {
        var row = this.uploadFailList[i];
        var rowNo = row.rowNo || (i + 1);
        var termsNm = (row.termsNm || '').replace(/"/g, '""');
        var reason = (row.reason || '').replace(/"/g, '""');
        csvContent += rowNo + ',"' + termsNm + '","' + reason + '"\n';
      }
      var blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      var url = window.URL.createObjectURL(blob);
      var link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', '용어_일괄등록_실패목록_' + this.$getToday() + '.csv');
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(url);
      link.remove();
    },
    _addUploadLog(level, msg) {
      const now = new Date();
      const time = now.toTimeString().slice(0, 8);
      this.uploadLogs.push({ level, msg, time });
      this.$nextTick(() => {
        const box = this.$refs.uploadLogBox;
        if (box) box.scrollTop = box.scrollHeight;
      });
    },
    downloadTermTemplate() {
      var link = document.createElement('a');
      link.href = this.$APIURL.base + 'api/std/downloadTermTemplate';
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    resetDetail() {
      // 선택한 용어 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.detailTerm = null;
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchTerm = ''
      this.searchEngTerm = ''
      this.searchDomain = ''
    },
    enterSelect() {
      // 용어명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeItems.length === 0) {
        this.selectedItem = [];
      }
    },
    resetTermList() {
      // 용어 목록 다시 불러오기
      this.getTermData();
      this.searchTerm = '';
      this.resetDetail();
    },
    // 용어 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      // 이음동의어
      let allophSynmLst_data = [];

      if (this.selectedItem[0].allophSynmLst != null && this.selectedItem[0].allophSynmLst.length > 0) {
        // for문으로 돌려서 value값만 넣어준다.
        // allophSynmLst_data = this.selectedItem[0].allophSynmLst;

        for (let i = 0; i < this.selectedItem[0].allophSynmLst.length; i++) {
          // 마지막 배열은 add버튼을 보여주고 나머지는 remove버튼을 보여준다.
          if (i === this.selectedItem[0].allophSynmLst.length - 1) {
            allophSynmLst_data.push({ id: 'alloph_' + i, value: this.selectedItem[0].allophSynmLst[i].trim(), addBtnView: true, removeBtnView: false });
          } else {
            allophSynmLst_data.push({ id: 'alloph_' + i, value: this.selectedItem[0].allophSynmLst[i].trim(), addBtnView: false, removeBtnView: true });
          }
        }
        this.updateTerm_allophSynmLst_count = this.selectedItem[0].allophSynmLst.length - 1;
      } else {
        allophSynmLst_data = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
        this.updateTerm_allophSynmLst_count = 0;
      }

      this.updateTerm_id = this.selectedItem[0].id;
      this.updateTerm_termNm = this.selectedItem[0].termsNm;
      this.updateTerm_termEngAbrvNm = this.selectedItem[0].termsEngAbrvNm;
      this.updateTerm_termDesc = this.selectedItem[0].termsDesc;
      this.updateTerm_domainNm = this.selectedItem[0].domainNm;
      this.updateTerm_codeGrp = this.selectedItem[0].codeGrp;
      this.updateTerm_chrgOrg = this.selectedItem[0].chrgOrg;

      // 기존 codeGrp가 있으면 코드 도메인 모드로 설정
      if (this.selectedItem[0].codeGrp) {
        this.updateTerm_domainType = 'code';
        this.loadUpdateCodeInfoList(this.selectedItem[0].codeGrp);
      } else {
        this.updateTerm_domainType = 'domain';
        this.updateTerm_selectedCode = null;
      }
      this.updateTerm_commStndYn = this.selectedItem[0].commStndYn;
      this.updateTerm_magntdOrd = this.selectedItem[0].magntdOrd;
      this.updateTerm_reqSysCd = this.selectedItem[0].reqSysCd;

      // 타이틀에 용어명 넣어주기
      this.updateTerm_user_selected_word = this.selectedItem[0].termsNm;
      // 이음동의어
      this.updateTerm_allophSynmLst_arr = allophSynmLst_data;
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addTermModalShow = true;
        this.addModalOpenSetTermNm();
        // 86번 #39 — 형식단어 목록 비동기 로드 (선택 input 에 사용)
        this.loadClassificationWords();
        // 용어 등록 도메인명 리스트 바인드
        // this.getDomainData();
      } else if (value === 'update') {
        // this.getDomainData();
        this.updateModalInit();
        this.updateTermModalShow = true;
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addTermModalShow = false;
        this.addFormReset();
        this.resetAddTermTextfield();
      } else if (value === 'update') {
        this.updateTermModalShow = false;
        this.updateFormReset();
        this.resetUpdateTermTextfield();
      }
    },
    addModalOpenSetTermNm() {
      // 모달 오픈 시 검색어에 문자열이 있을 경우 용어명에 자동으로 입력
      if (this.searchTerm !== '') {
        this.addTerm_termNm = this.searchTerm;
      }
    },
    addFormReset() {
      // 용어 등록 모달 초기화
      this.$refs.form.reset();
      this.addModalStep = 1;
      this.addTerm_termNm = null;
      this.addTerm_wordListArr = [];
      this.addTerm_termEngAbrvNm = null;
      this.addTerm_termDesc = null;
      this.addTerm_domainType = 'domain';
      this.addTerm_domainNm = null;
      this.addTerm_domainNmItems = [];
      this.addTerm_selectedCode = null;
      this.addTerm_codeInfoList = [];
      this.addTerm_codeGrp = null;
      this.addTerm_chrgOrg = null;
      this.addTerm_commStndYn = 'N';
      this.addTerm_magntdOrd = null;
      this.addTerm_reqSysCd = null;
      this.addTerm_selected_word_list = [];
      this.addTerm_wordList = [];
      this.addTerm_lastCheckedNm = null;
      // 86번 #23 — 분석 응답 / 분리 모드 / 수동 추가 입력 초기화
      this.addTerm_lastAnalysis = null;
      this.addTerm_splitMode = 0;
      this.addTerm_manualWordInput = '';
      this.addTerm_selectedClsfWord = null;  // 86번 #39
      // 81번 — 자동 분석 + 코드 picker 상태 초기화
      this.addTerm_analyzing = false;
      this.addTerm_selectedCodeLabel = '';
      this.codePickerShow = false;
      this.codePickerSearch = '';
      if (this._addTerm_analyzeTimer) {
        clearTimeout(this._addTerm_analyzeTimer);
        this._addTerm_analyzeTimer = null;
      }
    },
    updateFormReset() {
      // 용어 수정 모달 초기화
      this.updateModalStep = 1;
      this.updateTerm_wordListArr = [];
      this.updateTerm_domainType = 'domain';
      this.updateTerm_domainNmItems = [];
      this.updateTerm_selectedCode = null;
      this.updateTerm_codeInfoList = [];
      this.updateTerm_selected_word_list = [];
      this.updateTerm_wordList = [];
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'add') {
        if (this.addTerm_termNm === null) {
          _attr = '용어명은';
          this.$refs.addTerm_termNm.focus()
        } else if (this.addTerm_termDesc === null) {
          _attr = '용어 설명은'
          this.$refs.addTerm_termDesc.focus()
        } else if (this.addTerm_domainType === 'domain' && (this.addTerm_domainNm === null || this.addTerm_domainNm === '')) {
          _attr = '도메인명은'
          this.$refs.addTerm_domainNm.focus()
        } else if (this.addTerm_domainType === 'code' && !this.addTerm_selectedCode) {
          _attr = '코드는'
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });

          return false;
        }

      } else if (status === 'update') {
        if (this.updateTerm_termNm === null) {
          _attr = '용어명은';
          this.$refs.updateTerm_termNm.focus()
        } else if (this.updateTerm_termDesc === null) {
          _attr = '용어 설명은'
          this.$refs.updateTerm_termDesc.focus()
        } else if (this.updateTerm_domainType === 'domain' && (this.updateTerm_domainNm === null || this.updateTerm_domainNm === '')) {
          _attr = '도메인명은'
          this.$refs.updateTerm_domainNm.focus()
        } else if (this.updateTerm_domainType === 'code' && !this.updateTerm_selectedCode) {
          _attr = '코드는'
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });

          return false;
        }
      }

      return true;
    },
    createTerm() {
      try {
        // 이음동의어 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_allophSynmLst = this.addTerm_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');
        // 86번 #40 — 사용자가 단어 구성 (× 삭제 / 단어 추가 / 형식단어 추가) 으로 편집한 결과를 termsNm 으로 사용.
        //   기존엔 사용자가 처음 입력한 한글명 그대로 등록 → 편집 무시되고 승인 화면에서 원본명으로 노출되는 버그.
        //   addTerm_user_selected_word 가 빈 문자열이면 fallback 으로 입력값 사용.
        let _term_name = (this.addTerm_user_selected_word && this.addTerm_user_selected_word.length > 0)
          ? this.addTerm_user_selected_word
          : this.addTerm_termNm;

        var _domainNm = this.addTerm_domainNm;
        var _codeGrp = this.addTerm_codeGrp;
        if (this.addTerm_domainType === 'code' && this.addTerm_selectedCode) {
          _domainNm = this.addTerm_selectedCode.domainNm || '';
          _codeGrp = this.addTerm_selectedCode.codeGrp || '';
        }
        let termData = {
          'termsNm': _term_name,
          'termsEngAbrvNm': this.addTerm_termEngAbrvNm,
          'termsDesc': this.addTerm_termDesc,
          'domainNm': _domainNm,
          'codeGrp': _codeGrp,
          'chrgOrg': this.addTerm_chrgOrg,
          'commStndYn': this.addTerm_commStndYn,
          'magntdOrd': this.addTerm_magntdOrd,
          'reqSysCd': this.addTerm_reqSysCd,
          'wordList': this.addTerm_wordList,
          "allophSynmLst": arr_allophSynmLst,
        }

        axios.post(this.$APIURL.base + 'api/std/createTerms', termData).then(res => {
          // console.log(res)
          if (res.data.resultCode === 200) {
            this.hideModal('add');

            if (res.data.resultMessage) {
              this.$swal.fire({
                title: '용어가 등록되었습니다.',
                text: res.data.resultMessage,
                icon: 'info',
                confirmButtonText: '확인',
              });
            } else {
              this.$swal.fire({
                title: '새로운 용어가 등록되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });
            }

            this.getTermData()
          } else {
            this.$swal.fire({
              title: '용어 등록 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '용어 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        this.$swal.fire({
          title: '용어 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateTerm() {
      try {
        // 이음동의어 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_allophSynmLst = this.updateTerm_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');
        // 86번 #40 — 단어 구성 편집 결과를 termsNm 으로 사용 (수정 모달도 동일)
        let _term_name = (this.updateTerm_user_selected_word && this.updateTerm_user_selected_word.length > 0)
          ? this.updateTerm_user_selected_word
          : this.updateTerm_termNm;

        var _domainNm = this.updateTerm_domainNm;
        var _codeGrp = this.updateTerm_codeGrp;
        if (this.updateTerm_domainType === 'code' && this.updateTerm_selectedCode) {
          _domainNm = this.updateTerm_selectedCode.domainNm || '';
          _codeGrp = this.updateTerm_selectedCode.codeGrp || '';
        }

        let termData = {
          'id': this.updateTerm_id,
          'termsNm': _term_name,
          'termsEngAbrvNm': this.updateTerm_termEngAbrvNm,
          'termsDesc': this.updateTerm_termDesc,
          'domainNm': _domainNm,
          'codeGrp': _codeGrp,
          'chrgOrg': this.updateTerm_chrgOrg,
          'commStndYn': this.updateTerm_commStndYn,
          'magntdOrd': this.updateTerm_magntdOrd,
          'reqSysCd': this.updateTerm_reqSysCd,
          'wordList': this.updateTerm_wordList,
          "allophSynmLst": arr_allophSynmLst,
        }

        axios.post(this.$APIURL.base + 'api/std/updateTerms', termData).then(res => {
          // console.log(res)

          if (res.data.resultCode === 200) {
            this.hideModal('update');

            this.$swal.fire({
              title: '용어가 수정되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            // 리셋
            this.resetTermList();
          } else {
            this.$swal.fire({
              title: '용어 수정 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '용어 수정 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        this.$swal.fire({
          title: '용어 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    excelFileUpload() {
      // 일괄 등록 버튼 클릭
      let fileUpload = document.getElementById('inputTermUpload')
      if (fileUpload != null) {
        fileUpload.click()
      }
    },
    termListDownload() {
      let _keyWord = this.searchTerm.length !== 0 ? this.searchTerm : null;

      try {
        axios.get(this.$APIURL.base + "api/std/downloadTermsList",
          {
            params: { 'searchKey': _keyWord },
            responseType: 'blob',
            headers: { "Accept": "application/vnd.ms-excel" }
          }).then(response => {
            // console.log(response)
            const url = window.URL.createObjectURL(
              new Blob([response.data], { type: "application/csv" })
            );
            const link = document.createElement("a");
            link.href = url;

            let _today = this.$getToday();

            link.setAttribute("download", `용어사전_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '용어 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('용어 다운로드 실패 :', error);
      }
    },
    termRemoveItem() {
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 용어를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      let removeName = '';

      for (let i = 0; i < this.removeItems.length; i++) {
        if (i === 0) {
          removeName += this.removeItems[i].termsNm;
        } else {
          removeName += ', ' + this.removeItems[i].termsNm;
        }
      }

      this.$swal.fire({
        title: '정말로 용어를 삭제할까요?',
        icon: 'warning',
        showCancelButton: true,
        text: removeName,
        confirmButtonColor: '#3678a7',
        cancelButtonColor: '#909090',
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {

          let removeItemArr = [];
          for (let i = 0; i < this.removeItems.length; i++) {
            let removeObj = {
              id: this.removeItems[i].id
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/std/deleteTermsList", removeItemArr)
              .then(res => {
                // console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '용어가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getTermData();
                  this.resetDetail();
                } else {
                  this.$swal.fire({
                    title: '용어 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '용어 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '용어 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    termBulkRemove() {
      if (this.termItems.length === 0) {
        this.$swal.fire({ title: '삭제할 용어가 없습니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.$swal.fire({
        title: `조회된 용어 ${this.termItems.length}건을 모두 삭제할까요?`,
        text: '이 작업은 되돌릴 수 없습니다.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#d32f2f',
        cancelButtonColor: '#909090',
        confirmButtonText: '일괄 삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {
          const removeItemArr = this.termItems.map(item => ({ id: item.id }));
          axios.post(this.$APIURL.base + 'api/std/deleteTermsList', removeItemArr)
            .then(res => {
              if (res.data.resultCode === 200) {
                this.$swal.fire({ title: `용어 ${removeItemArr.length}건이 삭제되었습니다.`, icon: 'success', showConfirmButton: false, timer: 1500 });
                this.getTermData();
                this.resetDetail();
              } else {
                this.$swal.fire({ title: '용어 일괄 삭제 실패', text: res.data.resultMessage, confirmButtonText: '확인', icon: 'error' });
              }
            }).catch(() => {
              this.$swal.fire({ title: '용어 일괄 삭제 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
            });
        }
      });
    },
    showDetail(item) {
      // 용어명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 선택한 용어 이름을 타이틀에 보이기 위해 추가함
      this.detailTerm = item.termsNm;
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
      // 단어구성목록 조회하여 가지고 오기
      // console.log(item)
      this.getWordItemsListByTermsId(item.id);
    },
    addActiveDetail(name, index) {
      this.activeDetailTab = name;
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createTerm();
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateTerm();
        }
      }
    },

    selectedWordName() {
      if (this.addTermModalShow) {
        // 단어 구성을 아무것도 선택하지 않았을 때 경고창
        if (this.addTerm_selected_word_list.length === 0) {
          this.$swal.fire({
            title: '단어구성을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return false;
        } else {
          return true;
        }

      } else if (this.updateTermModalShow) {
        // 단어 구성을 아무것도 선택하지 않았을 때 경고창
        if (this.updateTerm_selected_word_list.length === 0) {
          this.$swal.fire({
            title: '단어구성을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return false;
        } else {
          return true;
        }

      }
    },
    // 86번 #27 — checkSelectedWordStatus 제거.
    //   옛 stepper UX (사용자가 OKT raw 토큰 체크박스 선택) 의 검증 로직.
    //   새 UX 는 analyzeTermsBatch 가 NN 만 반환 + _applyAnalyzedWords 가 partOfSpeech='NN' 강제 +
    //   인라인 단어 등록 폼이 미등록 단어 처리. 비명사가 selected_word_list 에 들어올 경로 자체 없음.
    //   잔존 시 partOfSpeech 누락(getWordInfoByNm 응답)으로 false positive "명사가 아닙니다" swal 발생.
    createAddWordList(arr) {
      // 새로운 용어 생성 시 필요한 this.addTerm_wordList의 배열을 생성한다.
      // 필요 데이터 termsId=null, wordId, wordNm, wordOrd(index)
      let sortedArr = arr;
      let createWordList = [];

      for (let i = 0; i < sortedArr.length; i++) {
        if (this.addTermModalShow) {
          createWordList.push({
            termsId: null,
            wordId: sortedArr[i].id,
            wordNm: sortedArr[i].wordNm,
            wordEngAbrvNm: sortedArr[i].wordEngAbrvNm,
            domainClsfNm: sortedArr[i].domainClsfNm,
            wordOrd: i,
          })

        } else if (this.updateTermModalShow) {
          // 수정할 때는 termsId가 필요하다.
          createWordList.push({
            termsId: this.updateTerm_id,
            wordId: sortedArr[i].id,
            wordNm: sortedArr[i].wordNm,
            wordEngAbrvNm: sortedArr[i].wordEngAbrvNm,
            domainClsfNm: sortedArr[i].domainClsfNm,
            wordOrd: i,
          })
        }
      }

      return createWordList;
    },
    createTermEngAbrvNm(sortedArr) {
      // console.log(sortedArr)
      let _createEngAbrvNm = '';

      for (let i = 0; i < sortedArr.length; i++) {
        if (i === sortedArr.length - 1) {
          _createEngAbrvNm += sortedArr[i].wordEngAbrvNm;

          // 마지막 단어의 도메인 분류명 확인하여 null이면 모든 도메인을 보여주고 아니면 해당 도메인만 보여준다.
          // 86번 #22 — 마지막 단어가 코드(CD) + 사용자가 '일반 도메인' 모드 선택했으면 코드분류 제한 풀고 전체 노출.
          //   (이름만 코드, 실제 free-text 케이스 대응)
          let _domainClsfNm = sortedArr[i].domainClsfNm;
          let _isCodeWord   = (sortedArr[i].wordEngAbrvNm || '').toUpperCase() === 'CD';

          if (_isCodeWord && this.addTermModalShow && this.addTerm_domainType === 'domain') {
            this.getDomainData();
          } else if (_isCodeWord && this.updateTermModalShow && this.updateTerm_domainType === 'domain') {
            this.getDomainData();
          } else if (_domainClsfNm !== null) {
            this.getDomainInfoByClsfNm(_domainClsfNm);
          } else {
            this.getDomainData();
          }

        } else {
          _createEngAbrvNm += sortedArr[i].wordEngAbrvNm + '_';
        }
      }

      // 용어 영문 약어명을 생성하여 바인드
      if (this.addTermModalShow) {
        this.addTerm_termEngAbrvNm = _createEngAbrvNm;
      } else if (this.updateTermModalShow) {
        this.updateTerm_termEngAbrvNm = _createEngAbrvNm;
      }
    },
    collectSelectedItems() {
      let wordItems = [];

      if (this.addTermModalShow) {
        wordItems = this.addTerm_selected_word_list;

      } else if (this.updateTermModalShow) {
        wordItems = this.updateTerm_selected_word_list
      }

      // 빈 배열 삭제
      const validWords = wordItems.filter(word => word.length !== 0);

      let _length = validWords.length;

      // 용어의 용어명을 순서대로 정렬하기 위한 배열
      let sortedArr = [];
      for (let i = 0; i < _length; i++) {
        sortedArr.push(validWords[i][0]);
      }

      if (this.addTermModalShow) {
        // 용어 등록 시에 필요한 단어 구성 배열
        this.addTerm_wordList = this.createAddWordList(sortedArr);

      } else if (this.updateTermModalShow) {
        // 용어 수정 시에 필요한 단어 구성 배열
        this.updateTerm_wordList = this.createAddWordList(sortedArr);
      }
    },
    getWordItemsListByTermsId(id) {
      let _termId = id;
      try {
        axios.get(this.$APIURL.base + "api/std/getTermsWordInfoList", {
          params: {
            'termsId': _termId
          }
        }).then((res) => {
          // console.log(res)
          this.wordItemsList = res.data;

          // console 표시
          console.log("📃 WORD ITEM LIST ↓↓↓")
          console.log(this.wordItemsList);
        }).catch((err) => {
          this.$swal.fire({
            title: '용어 단어 구성 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error)
      }
    },
    /**
     * 81번/82번 — 자동 분석.
     * v1 (858f091) 은 getTermsTokenListByNm 사용했으나 부분문자열 모든 매칭이 반환돼
     * '가로세로일시' → 27개 분류 같은 잡음 발생.
     * v2 (82번 §3) — DSTermRecommend 의 analyzeTermsBatch 로 교체.
     * 응답이 단어 1개당 분류 1개 (가장 긴 매칭 우선 + 미매칭 NEW) 라 잡음 없음.
     * MATCHED 단어는 자동 선택, 추천 도메인 자동 채움.
     */
    runAutoAnalyze() {
      if (!this.addTermModalShow) return;
      var nm = (this.addTerm_termNm || '').trim();
      if (!nm) return;
      if (this.addTerm_lastCheckedNm === nm && this.addTerm_wordListArr.length > 0) return;
      var self = this;
      self.addTerm_analyzing = true;
      self.addTerm_lastCheckedNm = nm;

      axios.post(this.$APIURL.base + 'api/std/analyzeTermsBatch', { termNames: [nm] })
        .then(function(res) {
          var arr = res.data || [];
          var result = arr[0];
          if (!result) {
            self.addTerm_wordListArr = [];
            self.addTerm_lastAnalysis = null;
            return;
          }
          // 기등록 용어: 알림 + 분석 결과는 그대로 표시
          if (result.status === 'REGISTERED') {
            self.$swal.fire({
              title: '이미 등록된 용어입니다.',
              text: '"' + nm + '" 용어가 이미 존재합니다.',
              confirmButtonText: '확인', icon: 'warning'
            });
          }
          // 분석 응답 저장 → 추천1/2 토글에서 재사용
          self.addTerm_lastAnalysis = result;
          self.addTerm_splitMode = 0;
          self._applyAnalyzedWords(result.words || [], result.recommendedDomainNm);
        })
        .catch(function(err) {
          console.error('analyzeTermsBatch 실패', err);
          self.addTerm_wordListArr = [];
          self.addTerm_lastAnalysis = null;
        })
        .finally(function() {
          self.addTerm_analyzing = false;
        });
    },
    /**
     * 86번 #23 — 분석 응답을 wordListArr / selected_word_list 에 적용 (1순위 또는 2순위)
     * runAutoAnalyze + applySplitMode 둘 다에서 재사용.
     * 86번 #24 — 적용 후 post-validation: NEW/UNRECOGNIZED 토큰을 lookupWord 로 재검증.
     *   알고리즘이 1글자 단어 ('명' 등) 미스해도 TB_WORD 직접 조회로 MATCHED 승격.
     *   자동표준화 (DSTermRecommend.onWordNmInput) 패턴 답습.
     */
    _applyAnalyzedWords(words, recommendedDomainNm) {
      var wordListArr = [];
      var newSelectedList = [];
      for (var i = 0; i < words.length; i++) {
        var w = words[i];
        if (w.status === 'MATCHED' && w.selected) {
          var s = w.selected;
          var item = {
            id: s.wordId, wordNm: s.wordNm,
            wordEngAbrvNm: s.wordEngAbrvNm, wordEngNm: s.wordEngNm,
            domainClsfNm: s.domainClsfNm, partOfSpeech: 'NN', index: i
          };
          wordListArr.push({
            wordNm: w.wordNm, wordLst: [item],
            inlineWordNm: '', inlineWordEngAbrvNm: '', inlineWordEngNm: '',
            inlineSaving: false
          });
          newSelectedList[i] = [item];
        } else {
          var nw = w.newWord || {};
          wordListArr.push({
            wordNm: w.wordNm, wordLst: [],
            inlineWordNm: w.wordNm || '',
            inlineWordEngAbrvNm: nw.wordEngAbrvNm || '',
            inlineWordEngNm: nw.wordEngNm || '',
            inlineSaving: false
          });
          newSelectedList[i] = [];
        }
      }
      this.addTerm_wordListArr = wordListArr;
      this.addTerm_selected_word_list = newSelectedList;
      if (recommendedDomainNm && !this.addTerm_domainNm && this.addTerm_domainType !== 'code') {
        this.addTerm_domainNm = recommendedDomainNm;
      }
      // post-validation — NEW/UNRECOGNIZED 행을 lookupWord 로 재검증
      this._postValidateNewTokens();
    },
    /**
     * 86번 #24 — wordListArr 의 미등록 행에 대해 lookupWord 호출 → TB_WORD 발견 시 MATCHED 로 승격.
     * 자동표준화의 onWordNmInput 패턴.
     */
    _postValidateNewTokens() {
      var self = this;
      var arr = this.addTerm_wordListArr;
      for (var i = 0; i < arr.length; i++) {
        var item = arr[i];
        // 이미 MATCHED 행 (wordLst 채워짐) 은 건너뜀
        if (item.wordLst && item.wordLst.length > 0) continue;
        var nm = (item.inlineWordNm || item.wordNm || '').trim();
        if (!nm) continue;
        (function(idx, wordNm) {
          axios.get(self.$APIURL.base + 'api/std/lookupWord', { params: { wordNm: wordNm } })
            .then(function(res) {
              var data = res.data;
              if (!data || !data.found) return;
              var cur = self.addTerm_wordListArr[idx];
              if (!cur || (cur.wordLst && cur.wordLst.length > 0)) return;  // 그새 변경됐으면 skip
              if (data.source === 'WORD') {
                // TB_WORD 발견 → MATCHED 승격
                var matched = {
                  id: data.wordId, wordNm: data.wordNm,
                  wordEngAbrvNm: data.wordEngAbrvNm, wordEngNm: data.wordEngNm,
                  domainClsfNm: data.domainClsfNm || '', partOfSpeech: 'NN', index: idx
                };
                self.$set(self.addTerm_wordListArr, idx, {
                  wordNm: data.wordNm, wordLst: [matched],
                  inlineWordNm: '', inlineWordEngAbrvNm: '', inlineWordEngNm: '',
                  inlineSaving: false
                });
                self.$set(self.addTerm_selected_word_list, idx, [matched]);
              } else if (data.source === 'DICT') {
                // DICT 추천 → 영문약어/영문명 자동 채움
                var c = self.addTerm_wordListArr[idx];
                if (!c) return;
                if (!c.inlineWordEngAbrvNm) c.inlineWordEngAbrvNm = data.wordEngAbrvNm || '';
                if (!c.inlineWordEngNm)     c.inlineWordEngNm     = data.wordEngNm || '';
                self.$set(self.addTerm_wordListArr, idx, Object.assign({}, c));
              }
            })
            .catch(function() { /* lookup 실패는 무시 — UI 그대로 */ });
        })(i, nm);
      }
    },
    /**
     * 86번 #24 — 인라인 한글명 input 디바운스 핸들러.
     * 사용자가 미등록 행의 한글명을 바꾸면 lookupWord 로 재검증.
     */
    onInlineWordNmInput(index) {
      var self = this;
      if (!this._inlineLookupTimers) this._inlineLookupTimers = {};
      if (this._inlineLookupTimers[index]) clearTimeout(this._inlineLookupTimers[index]);
      this._inlineLookupTimers[index] = setTimeout(function() {
        var item = self.addTerm_wordListArr[index];
        if (!item) return;
        if (item.wordLst && item.wordLst.length > 0) return;
        var nm = (item.inlineWordNm || '').trim();
        if (!nm) return;
        if (item._lastLookup === nm) return;
        item._lastLookup = nm;
        axios.get(self.$APIURL.base + 'api/std/lookupWord', { params: { wordNm: nm } })
          .then(function(res) {
            var data = res.data;
            var cur = self.addTerm_wordListArr[index];
            if (!cur || (cur.wordLst && cur.wordLst.length > 0)) return;
            if (data && data.found) {
              if (data.source === 'WORD') {
                var matched = {
                  id: data.wordId, wordNm: data.wordNm,
                  wordEngAbrvNm: data.wordEngAbrvNm, wordEngNm: data.wordEngNm,
                  domainClsfNm: data.domainClsfNm || '', partOfSpeech: 'NN', index: index
                };
                self.$set(self.addTerm_wordListArr, index, {
                  wordNm: data.wordNm, wordLst: [matched],
                  inlineWordNm: '', inlineWordEngAbrvNm: '', inlineWordEngNm: '',
                  inlineSaving: false
                });
                self.$set(self.addTerm_selected_word_list, index, [matched]);
              } else if (data.source === 'DICT') {
                if (!cur.inlineWordEngAbrvNm) cur.inlineWordEngAbrvNm = data.wordEngAbrvNm || '';
                if (!cur.inlineWordEngNm)     cur.inlineWordEngNm     = data.wordEngNm || '';
                self.$set(self.addTerm_wordListArr, index, Object.assign({}, cur));
              }
            }
          })
          .catch(function() {});
      }, 400);
    },
    /** 86번 #23 — 추천 1순위/2순위 토글 */
    applySplitMode(mode) {
      this.addTerm_splitMode = mode;
      var src = this.addTerm_lastAnalysis;
      if (!src) return;
      var words = mode === 1 ? (src.alternativeWords || []) : (src.words || []);
      this._applyAnalyzedWords(words, src.recommendedDomainNm);
    },
    /** 86번 #39 — 형식단어 목록 로드 (모달 진입 시 1회) */
    loadClassificationWords() {
      var self = this;
      if (self.addTerm_classWords.length > 0) return;
      self.addTerm_loadingClsfWords = true;
      axios.get(self.$APIURL.base + 'api/std/getClassificationWords').then(function(res) {
        self.addTerm_classWords = res.data || [];
      }).catch(function() { /* 조용히 실패 */ }).finally(function() {
        self.addTerm_loadingClsfWords = false;
      });
    },
    /** 86번 #39 — autocomplete 검색 텍스트 — 한글명 + 분류명 둘 다 매칭 */
    clsfItemText(item) {
      return (item.wordNm || '') + ' ' + (item.domainClsfNm || '');
    },
    /** 86번 #39 — 형식단어 1개 추가 (MATCHED 상태로 push) */
    addClassificationWord() {
      var c = this.addTerm_selectedClsfWord;
      if (!c) return;
      var idx = this.addTerm_wordListArr.length;
      var item = {
        id: c.wordId, wordNm: c.wordNm,
        wordEngAbrvNm: c.wordEngAbrvNm, wordEngNm: c.wordEngNm,
        domainClsfNm: c.domainClsfNm, partOfSpeech: 'NN', index: idx
      };
      this.addTerm_wordListArr.push({
        wordNm: c.wordNm, wordLst: [item],
        inlineWordNm: '', inlineWordEngAbrvNm: '', inlineWordEngNm: '',
        inlineSaving: false
      });
      this.addTerm_selected_word_list.push([item]);
      this.addTerm_selectedClsfWord = null;
    },
    /**
     * 86번 #23 — 사용자가 수동으로 단어 한 개 추가.
     * 86번 #24 — _postValidateNewTokens 가 lookupWord 로 검증해서 MATCHED 승격 / DICT 영문 자동입력.
     */
    addManualWord() {
      var nm = (this.addTerm_manualWordInput || '').trim();
      if (!nm) return;
      // NEW placeholder 로 push → _postValidateNewTokens 가 lookup 후 처리
      this.addTerm_wordListArr.push({
        wordNm: nm, wordLst: [],
        inlineWordNm: nm, inlineWordEngAbrvNm: '', inlineWordEngNm: '',
        inlineSaving: false
      });
      this.addTerm_selected_word_list.push([]);
      this.addTerm_manualWordInput = '';
      this._postValidateNewTokens();
    },
    /** 86번 #23 — 단어 행 삭제 */
    removeWordAt(index) {
      this.addTerm_wordListArr.splice(index, 1);
      this.addTerm_selected_word_list.splice(index, 1);
    },
    /** 81번 — 코드 picker 열기. 코드 목록이 비어있으면 onAddDomainTypeChange 가 채워줌 */
    openCodePicker() {
      var self = this;
      self.codePickerShow = true;
      self.codePickerSearch = '';
      if (!self.addTerm_codeInfoList || self.addTerm_codeInfoList.length === 0) {
        // domainType 을 'code' 로 두지 않은 상태에서도 목록 prefetch
        if (typeof self.onAddDomainTypeChange === 'function') self.onAddDomainTypeChange();
      }
    },
    /** 81번 — 코드 picker 에서 선택. 기존 onAddCodeSelected 흐름 재사용 (codeGrp/domainNm/dataType 자동 매핑) */
    pickCode(item) {
      this.addTerm_selectedCode = item;
      this.addTerm_selectedCodeLabel = item.codeNm + ' [' + (item.codeGrp || '') + ']';
      this.codePickerShow = false;
      if (typeof this.onAddCodeSelected === 'function') this.onAddCodeSelected();
    },
    addNextStep(step) {
      if (this.addTerm_termNm === null || this.addTerm_termNm === '') {
        this.$swal.fire({
          title: '용어명을 입력해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        this.$refs.addTerm_termNm.focus();
        return;
      }

      if (step === 1) {
        // 용어명이 이전 체크와 동일하면 API 호출 생략
        if (this.addTerm_lastCheckedNm === this.addTerm_termNm) {
          this.getWordListByNm();
          this.addModalStep = 2;
          return;
        }
        // 용어명 중복 체크 후 다음 단계
        var self = this;
        axios.get(this.$APIURL.base + 'api/std/getTermsInfoByNm', {
          params: { termsNm: this.addTerm_termNm }
        }).then(function(res) {
          if (res.data && res.data.length > 0) {
            self.$swal.fire({
              title: '이미 등록된 용어입니다.',
              text: '"' + self.addTerm_termNm + '" 용어가 이미 존재합니다.',
              confirmButtonText: '확인',
              icon: 'warning',
            });
            return;
          }
          self.addTerm_lastCheckedNm = self.addTerm_termNm;
          self.getWordListByNm();
          self.addModalStep = 2;
        }).catch(function() {
          // 중복 체크 실패해도 진행 허용
          self.addTerm_lastCheckedNm = self.addTerm_termNm;
          self.getWordListByNm();
          self.addModalStep = 2;
        });
      } else if (step === 2) {
        if (this.selectedWordName()) {
          this.addModalStep = 3;
        }
      }
    },
    updateNextStep(step) {
      if (this.updateTerm_termNm === null || this.updateTerm_termNm === '') {
        this.$swal.fire({
          title: '용어명을 입력해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        this.$refs.updateTerm_termNm.focus();
        return;
      }

      if (step === 1) {
        this.getWordListByNm();
        this.updateModalStep = 2;
      } else if (step === 2) {
        if (this.selectedWordName()) {
          this.updateModalStep = 3;
        }
      }
    },
    getWordListByNm() {
      let _term_name = '';

      if (this.addTermModalShow) {
        _term_name = this.addTerm_termNm;
      } else if (this.updateTermModalShow) {
        _term_name = this.updateTerm_termNm;
      }

      axios.get(this.$APIURL.base + "api/std/getTermsTokenListByNm", {
        params: {
          'termsNm': _term_name
        }
      }).then((res) => {
        // console.log(res.data)

        // index 추가 + 인라인 등록 필드 초기화
        for (let i = 0; i < res.data.length; i++) {
          if (res.data[i].wordLst && res.data[i].wordLst.length > 0) {
            res.data[i].wordLst[0].index = i;
          } else {
            // 미등록 단어: 인라인 등록용 초기값
            res.data[i].wordLst = [];
            res.data[i].inlineWordNm = res.data[i].wordNm || '';
            res.data[i].inlineWordEngAbrvNm = '';
            res.data[i].inlineWordEngNm = '';
            res.data[i].inlineSaving = false;
          }
        }

        if (this.addTermModalShow) {
          this.addTerm_wordListArr = res.data;
        } else if (this.updateTermModalShow) {
          this.updateTerm_wordListArr = res.data;
        }

        // console.log(this.addTerm_wordListArr)

      }).catch((err) => {
        this.$swal.fire({
          title: '용어 단어 구성 목록 바인드 실패 - API 확인 필요',
          text: err,
          confirmButtonText: '확인',
          icon: 'error',
        });
      });
    },
    getDomainData() {
      // 도메인 리스트에서 도메인명을 가지고 와 도메인명을 추출하여 배열 생성
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainList").then(result => {
          let _data = result.data;
          // console.log(_data);

          let _new_arr = [];

          for (let i = 0; i < _data.length; i++) {
            _new_arr.push(_data[i].domainNm);
          }
          // 테이블 생성하는 목록 Data에 전달
          this.addTerm_domainNmItems = _new_arr;
          this.updateTerm_domainNmItems = _new_arr;
          // 도메인이 1개뿐이면 자동 선택
          if (_new_arr.length === 1) {
            if (this.addTermModalShow) this.addTerm_domainNm = _new_arr[0];
            if (this.updateTermModalShow) this.updateTerm_domainNm = _new_arr[0];
          }

        }).catch(error => {
          console.error(error);
        })
      } catch (error) {
        console.error(error);
      }
    },
    getDomainInfoByClsfNm(_domainClsfNm) {
      // 도메인 리스트에서 도메인명을 가지고 와 도메인명을 추출하여 배열 생성
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainInfoByClsfNm", {
          params: {
            'clsfNm': _domainClsfNm
          }
        }).then(result => {
          let _data = result.data;
          // console.log(_data);

          let _new_arr = [];

          for (let i = 0; i < _data.length; i++) {
            _new_arr.push(_data[i].domainNm);
          }
          // 테이블 생성하는 목록 Data에 전달
          this.addTerm_domainNmItems = _new_arr;
          this.updateTerm_domainNmItems = _new_arr;
          // 도메인이 1개뿐이면 자동 선택
          if (_new_arr.length === 1) {
            if (this.addTermModalShow) this.addTerm_domainNm = _new_arr[0];
            if (this.updateTermModalShow) this.updateTerm_domainNm = _new_arr[0];
          }

        }).catch(error => {
          console.error(error);
        })
      } catch (error) {
        console.error(error);
      }
    },
    /** Step 2: 미등록 단어 인라인 등록 */
    inlineRegisterWord(index) {
      var arr = this.addTermModalShow ? this.addTerm_wordListArr : this.updateTerm_wordListArr;
      var selList = this.addTermModalShow ? this.addTerm_selected_word_list : this.updateTerm_selected_word_list;
      var item = arr[index];
      if (!item) return;
      if (!item.inlineWordNm) {
        this.$swal.fire({ title: '단어 한글명을 입력해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      if (!item.inlineWordEngAbrvNm) {
        this.$swal.fire({ title: '영문약어를 입력해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      var self = this;
      // 86번 #31 — arr[index] 를 사본으로 교체하지 않고 item 의 reactive prop 만 직접 set.
      //   이전 fix(#26) 가 시작 시 arr[index]=copy(item) 으로 교체해서 'item' 변수가 detached 됐고,
      //   성공 콜백에서 item.wordLst=words 가 화면 안 들어가서 행이 안 사라지는 버그.
      this.$set(item, 'inlineSaving', true);
      var setLoading = function(val) {
        var cur = arr[index];
        if (cur) self.$set(cur, 'inlineSaving', val);
      };
      axios.post(this.$APIURL.base + 'api/std/createWord', {
        wordNm: item.inlineWordNm,
        wordEngAbrvNm: item.inlineWordEngAbrvNm,
        wordEngNm: item.inlineWordEngNm || '',
        wordDesc: item.inlineWordNm,
        wordClsfYn: 'N',
        domainClsfNm: '',
        allophSynmLst: [],
        forbdnWordLst: [],
        commStndYn: 'N',
        magntdOrd: '',
        reqSysCd: '',
      }).then(function(res) {
        // resultCode 검사
        var rc = res && res.data && res.data.resultCode;
        if (rc != null && rc !== 200) {
          setLoading(false);
          self.$swal.fire({
            title: '단어 등록 실패',
            text: (res.data && res.data.resultMessage) || '단어 등록 중 오류가 발생했습니다.',
            confirmButtonText: '확인', icon: 'error'
          });
          return;
        }
        // 등록 완료 toast (admin/일반 모두)
        var msg = res.data && res.data.resultMessage;
        var isPending = msg && /승인/.test(msg);
        self.$swal.fire({
          title: isPending ? '단어 등록 — 승인 대기' : '단어 등록 완료',
          text: isPending ? msg : (item.inlineWordNm || ''),
          icon: isPending ? 'info' : 'success',
          toast: true, position: 'top-end',
          showConfirmButton: false, timer: 1800
        });
        // 등록 성공 → 단어 목록 조회하여 갱신 (current arr[index] 에 직접 set, item 변수 의존 X)
        axios.get(self.$APIURL.base + 'api/std/getWordInfoByNm', {
          params: { wordNm: item.inlineWordNm }
        }).then(function(res2) {
          var words = res2.data || [];
          var cur = arr[index];
          if (!cur) return;
          if (words.length > 0) {
            for (var w = 0; w < words.length; w++) {
              words[w].index = index;
              if (!words[w].partOfSpeech) words[w].partOfSpeech = 'NN';
            }
            self.$set(cur, 'wordLst', words);              // ← 핵심: 현재 arr[index] 에 set
            self.$set(cur, 'inlineWordNm', '');
            self.$set(cur, 'inlineWordEngAbrvNm', '');
            self.$set(cur, 'inlineWordEngNm', '');
            self.$set(selList, index, [words[0]]);
          }
          self.$set(cur, 'inlineSaving', false);
        }).catch(function() {
          setLoading(false);
        });
      }).catch(function(err) {
        setLoading(false);
        self.$swal.fire({ title: '단어 등록 실패', text: self._friendlyErrText(err, '단어 등록 중 오류가 발생했습니다.'), confirmButtonText: '확인', icon: 'error' });
      });
    },
    /** 도메인 유형 변경 시 (등록) */
    onAddDomainTypeChange(val) {
      if (val === 'code' && this.addTerm_codeInfoList.length === 0) {
        this.loadCodeInfoList();
      }
      // 86번 #22 — 마지막 단어가 코드인데 '일반 도메인' 으로 토글 시 전체 도메인 다시 로드
      if (val === 'domain' && this.addTerm_lastWordIsCode) {
        this.addTerm_domainNm = null;
        this.getDomainData();
      } else if (val === 'domain' && !this.addTerm_lastWordIsCode) {
        // 일반 도메인으로 돌아왔을 때 — 마지막 단어 분류 기반 다시 필터
        var list = this.addTerm_wordList;
        if (list && list.length > 0) {
          var last = list[list.length - 1];
          var _clsf = last && last.domainClsfNm;
          if (_clsf) this.getDomainInfoByClsfNm(_clsf);
          else this.getDomainData();
        }
      }
    },
    /** 코드 목록 로드 (등록) */
    loadCodeInfoList() {
      var self = this;
      axios.post(this.$APIURL.base + 'api/std/getCodeInfoList', {}).then(function(res) {
        self.addTerm_codeInfoList = res.data || [];
      });
    },
    /** 코드 선택 시 (등록) */
    onAddCodeSelected(code) {
      this.addTerm_selectedCode = code;
    },
    /** 도메인 유형 변경 시 (수정) */
    onUpdateDomainTypeChange(val) {
      if (val === 'code' && this.updateTerm_codeInfoList.length === 0) {
        this.loadUpdateCodeInfoList();
      }
      // 86번 #22 — 마지막 단어가 코드인데 '일반 도메인' 으로 토글 시 전체 도메인 다시 로드
      if (val === 'domain' && this.updateTerm_lastWordIsCode) {
        this.updateTerm_domainNm = null;
        this.getDomainData();
      } else if (val === 'domain' && !this.updateTerm_lastWordIsCode) {
        var list = this.updateTerm_wordList;
        if (list && list.length > 0) {
          var last = list[list.length - 1];
          var _clsf = last && last.domainClsfNm;
          if (_clsf) this.getDomainInfoByClsfNm(_clsf);
          else this.getDomainData();
        }
      }
    },
    /** 코드 목록 로드 (수정) - codeGrp가 있으면 기존 코드 자동 선택 */
    loadUpdateCodeInfoList(existingCodeGrp) {
      var self = this;
      axios.post(this.$APIURL.base + 'api/std/getCodeInfoList', {}).then(function(res) {
        self.updateTerm_codeInfoList = res.data || [];
        // 기존 codeGrp에 해당하는 코드 자동 선택
        if (existingCodeGrp) {
          for (var i = 0; i < self.updateTerm_codeInfoList.length; i++) {
            if (self.updateTerm_codeInfoList[i].codeGrp === existingCodeGrp) {
              self.updateTerm_selectedCode = self.updateTerm_codeInfoList[i];
              break;
            }
          }
        }
      });
    },
    /** 코드 선택 시 (수정) */
    onUpdateCodeSelected(code) {
      this.updateTerm_selectedCode = code;
    },
    moveItemUp(index, state) {
      if (state === 'add') {
        if (index === 0) return; // 최상단 요소는 위로 이동할 수 없음
        const temp = this.addTerm_wordList[index];
        this.addTerm_wordList.splice(index, 1);
        this.addTerm_wordList.splice(index - 1, 0, temp);
        // item.wordOrd 값 갱신
        this.addTerm_wordList.forEach((item, i) => {
          item.wordOrd = i;
        });
      } else if (state === 'update') {
        if (index === 0) return; // 최상단 요소는 위로 이동할 수 없음
        const temp = this.updateTerm_wordList[index];
        this.updateTerm_wordList.splice(index, 1);
        this.updateTerm_wordList.splice(index - 1, 0, temp);
        // item.wordOrd 값 갱신
        this.updateTerm_wordList.forEach((item, i) => {
          item.wordOrd = i;
        });
      }
    },
    moveItemDown(index, state) {
      if (state === 'add') {
        if (index === this.addTerm_wordList.length - 1) return; // 최하단 요소는 아래로 이동할 수 없음
        const temp = this.addTerm_wordList[index];
        this.addTerm_wordList.splice(index, 1);
        this.addTerm_wordList.splice(index + 1, 0, temp);
        // item.wordOrd 값 갱신
        this.addTerm_wordList.forEach((item, i) => {
          item.wordOrd = i;
        });

      } else if (state === 'update') {
        if (index === this.updateTerm_wordList.length - 1) return; // 최하단 요소는 아래로 이동할 수 없음
        const temp = this.updateTerm_wordList[index];
        this.updateTerm_wordList.splice(index, 1);
        this.updateTerm_wordList.splice(index + 1, 0, temp);
        // item.wordOrd 값 갱신
        this.updateTerm_wordList.forEach((item, i) => {
          item.wordOrd = i;
        });

      }
    },
    createWordToTerm(arr) {
      const validWords = arr.filter(word => word.length !== 0);

      let _str = '';
      for (let i = 0; i < validWords.length; i++) {
        _str += validWords[i].wordNm;
      }

      if (this.addTermModalShow) {
        this.addTerm_user_selected_word = _str;
      } else if (this.updateTermModalShow) {
        this.updateTerm_user_selected_word = _str;
      }
    },
    addAllophSynmLst() {
      if (this.addTermModalShow) {
        // 용어 등록 - 이음동의어목록 
        let _dataLength = this.addTerm_allophSynmLst_arr.length;

        // 이음동의어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.addTerm_allophSynmLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '이음동의어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.addTerm_allophSynmLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        // 이음동의어 목록 배열 생성
        this.addTerm_allophSynmLst_arr.push({
          id: `alloph_${++this.addTerm_allophSynmLst_count}`,
          value: ''
        })

        // 이음동의어 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.addTerm_allophSynmLst_arr.forEach((item, index) => {
          if (index === this.addTerm_allophSynmLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })

        console.log(this.adddTerm_allophSynmLst_arr)
      } else if (this.updateTermModalShow) {
        // 용어 수정 - 이음동의어목록
        let _dataLength = this.updateTerm_allophSynmLst_arr.length;

        // 이음동의어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.updateTerm_allophSynmLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '이음동의어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.updateTerm_allophSynmLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        this.updateTerm_allophSynmLst_arr.push({
          id: `alloph_${++this.updateTerm_allophSynmLst_count}`,
          value: ''
        })

        this.updateTerm_allophSynmLst_arr.forEach((item, index) => {
          if (index === this.updateTerm_allophSynmLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      }
    },
    removeAllophSynmLst(id) {
      if (this.addTermModalShow) {
        this.addTerm_allophSynmLst_arr = this.addTerm_allophSynmLst_arr.filter(item => item.id !== id);
      } else if (this.updateTermModalShow) {
        this.updateTerm_allophSynmLst_arr = this.updateTerm_allophSynmLst_arr.filter(item => item.id !== id);
      }
    },
    resetAddTermTextfield() {
      this.addTerm_allophSynmLst_arr = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
      this.addTerm_allophSynmLst_count = 0;
    },
    resetUpdateTermTextfield() {
      this.updateTerm_allophSynmLst_arr = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
      this.updateTerm_allophSynmLst_count = 0;
    },
  },
  created() {
    if (eventBus.pendingSearch && eventBus.pendingSearch.type === 'term') {
      var pending = eventBus.pendingSearch;
      eventBus.pendingSearch = null;
      if (pending.field === 'termsNm') {
        this.searchTerm = pending.value;
        this.searchTermMode = 'contains';
        this.searchEngTerm = '';
        this.searchDomain = '';
      } else if (pending.field === 'termsEngAbrvNm') {
        this.searchEngTerm = pending.value;
        this.searchEngTermMode = 'contains';
        this.searchTerm = '';
        this.searchDomain = '';
      }
    }
    this.getTermData();
    this.getSystemList();
    eventBus.$on('NOTICE', this.onUploadNotice);
    axios.get(this.$APIURL.base + 'api/login/isAdmin', { params: { user: this.$loginStatusData.id } })
      .then(res => { this.isAdmin = res.data === true; });
  },
  activated() {
    if (eventBus.pendingSearch && eventBus.pendingSearch.type === 'term') {
      var pending = eventBus.pendingSearch;
      eventBus.pendingSearch = null;
      if (pending.field === 'termsNm') {
        this.searchTerm = pending.value;
        this.searchTermMode = 'contains';
        this.searchEngTerm = '';
        this.searchDomain = '';
      } else if (pending.field === 'termsEngAbrvNm') {
        this.searchEngTerm = pending.value;
        this.searchEngTermMode = 'contains';
        this.searchTerm = '';
        this.searchDomain = '';
      }
      this.getTermData();
    }
  },
  beforeDestroy() {
    eventBus.$off('NOTICE', this.onUploadNotice);
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
  }
}
</script>


<style scoped>
.splitTopWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}

#term_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#term_table thead th:nth-child(1) {
  width: 58px !important;
  min-width: 58px !important;
  max-width: 58px !important;
}

.tabsStyle {
  position: relative;
  width: 100% !important;
}

.tabsStyle .v-tab {
  border-top-right-radius: 10px !important;
  border-top-left-radius: 10px !important;
  color: #455A64;
  font-weight: 600;
  transition: all 0.2s ease;
}

.tabsStyle .v-tab--active {
  color: #283593 !important;
}

.tabContentsWrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.tabContents {
  height: calc(100% - 130px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#term_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

#term_wordItemsList_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

.splitBottomWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
}

.splitBottomSpanWrapper {
  width: 60%;
  display: flex;
  font-size: 1.2rem;
}

.splitBottomSpan {
  display: inline-block;
}

.split_bottom {
  overflow: hidden;
  position: relative;
  height: 100%;
  background: #ffffff;
}

.split_bottom_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 0px;
  border-top: 1px solid #E8EAF6;
  background: #FAFBFF;
}

.pagination_wrap {
  position: relative;
  width: 100%;
}

.tarmSearchApv {
  margin-top: 0px !important;
  padding-top: 0px !important;
  margin: 0 30px 0 0;
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}

.liStyle {
  border: 1px solid #C5CAE9;
  margin: 5px 0px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 6px;
}

.indexStyle {
  position: absolute;
  left: -30px;
  font-weight: 900;
}

.iconShow {
  visibility: visible;
}

.iconHide {
  visibility: hidden;
}

.colInBtnWrap {
  display: flex;
  align-items: center !important;
}

.colInBtn {
  width: 50px;
  margin-left: 20px;
  height: 30px !important;
}

.wordSearchApv {
  margin-top: 0px !important;
  padding-top: 0px !important;
  margin: 0 30px 0 0;
}
</style>