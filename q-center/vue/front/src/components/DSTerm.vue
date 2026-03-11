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
              <span :style="{ fontSize: '.875rem' }">용어명</span>
              <!-- 용어명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchTerm" v-on:keyup.enter="getTermData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 승인 여부 추가 -->
              <v-checkbox class="tarmSearchApv" v-model="searchApproval" label="승인 여부" color="ndColor"
                hide-details></v-checkbox>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getTermData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- <v-btn class="gradient" title="용어 목록 다시 불러오기" v-on:click="resetTermList" v-show="resetBtnShow"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px' }"><v-icon>restart_alt</v-icon></v-btn> -->
            </v-row>

          </v-sheet>
          <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
            <v-btn class="gradient" v-on:click="excelFileUpload" title="일괄 등록">일괄 등록</v-btn>
            <v-btn class="gradient" v-on:click="termListDownload()" title="다운로드">다운로드</v-btn>
            <v-btn class="gradient" v-on:click="termRemoveItem()" title="삭제">삭제</v-btn>
            <input type="file" @change="readExcelFile" ref="file" id="inputTermUpload" :style="{ display: 'none' }"
              accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ termItems.length }}건</span>
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
                <v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>
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
                      <td :style="{ backgroundColor: 'rgba(24, 127, 196, 0.1)', width: '15%' }">
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
                <v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>
                <!-- <v-btn class="gradient" v-on:click="wordRemoveItem()">삭제</v-btn> -->
              </v-sheet>
            </v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-data-table id="term_wordItemsList_table" :items="wordItemsList" hide-default-footer class="px-4 pb-3">
                <template v-slot:body="{ items }">
                  <tbody>
                    <!-- 상세 테이블 왼쪽  -->
                    <tr v-for="header in items.wordNm" :key="header.value">
                      <td :style="{ backgroundColor: 'rgba(24, 127, 196, 0.1)', width: '15%' }">
                        {{ header.text }}
                      </td>
                      <!-- 상세 테이블 오른쪽  -->
                      <td v-for="item in items" :key="item.wordNm">
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

              <v-data-table id="term_wordItemsList_table" :headers="wordItemsListHeaders" :items="wordItemsList"
                hide-default-footer item-key="id" class="px-4 pb-3" v-model="wordItemsList">
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
    <!-- Add term Modal -->
    <v-dialog max-width="950px" v-model="addTermModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true"
        :header-title="'용어 등록 ' + (addTerm_user_selected_word.length > 0 ? '- ' + addTerm_user_selected_word : '')"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <v-form ref="form">
            <v-stepper v-model="addModalStep" vertical :style="{ boxShadow: 'none !important' }">
              <v-stepper-step :complete="addModalStep > 1" step="1" color="ndColor" v-on:click="addModalStep = 1">
                용어명 입력
                <!-- <small>Summarize if needed</small> -->
              </v-stepper-step>

              <v-stepper-content step="1">
                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">용어명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_termNm" ref="addTerm_termNm"
                      :rules="[() => !!addTerm_termNm || '용어명은 필수 입력값입니다.']" clearable required dense placeholder="가동개시일자"
                      color="ndColor" v-on:keyup.enter="addNextStep(1)"></v-text-field>
                  </v-col>
                </v-row>

                <v-col class="text-right">
                  <v-btn class="white--text" color="ndColor" @click="addNextStep(1)"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    다음
                  </v-btn>
                </v-col>
              </v-stepper-content>

              <v-stepper-step :complete="addModalStep > 2" step="2" color="ndColor" v-on:click="addNextStep(1)">
                단어 목록 선택
              </v-stepper-step>

              <v-stepper-content step="2">

                <v-row v-for="(item, index) in addTerm_wordListArr" :key="index" :style="{ margin: '0px 0px 20px 0px' }">
                  <h3 :style="{ margin: '10px 0px' }">{{ item.wordNm }}</h3>
                  <v-col cols="12" :style="{ padding: '0px' }">
                    <v-data-table id="addTerm_wordList_table" class="px-4 pb-3" :headers="wordListHeader"
                      :items="item.wordLst" item-key="index" v-model="addTerm_selected_word_list[index]"
                      :value="addTerm_selected_word_list[index]" hide-default-footer show-select>
                    </v-data-table>
                  </v-col>
                </v-row>

                <!-- 버튼 -->
                <v-col class="text-right">
                  <v-btn text class="gray white--text" @click="addModalStep = 1"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    이전
                  </v-btn>
                  <v-btn class="white--text" color="ndColor" v-on:click="addNextStep(2)"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    다음
                  </v-btn>
                </v-col>
              </v-stepper-content>

              <v-stepper-step :complete="addModalStep > 3" step="3" color="ndColor">
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
                      <v-list-item v-for="(item, index) in addTerm_wordList" :key="index" class="liStyle">
                        <span class="indexStyle">{{ index + 1 }}</span>
                        <span :style="{ width: 'calc(100% - 60px)' }">
                          {{ item.wordNm }}
                        </span>
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
                <!--  -->
                <v-divider :style="{ margin: '25px 0' }"></v-divider>
                <!--  -->
                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">용어 영문 약어명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_termEngAbrvNm" required dense color="ndColor" readonly
                      filled></v-text-field>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">도메인명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-autocomplete dense required color="ndColor" v-model="addTerm_domainNm" ref="addTerm_domainNm"
                      :items="addTerm_domainNmItems" :rules="[v => !!v || '도메인명은 필수 입력값입니다.']" :placeholder="'선택'"
                      :menu-props="{ top: false, offsetY: true }">
                      <template v-slot:no-data>
                        <v-list-item>
                          <v-list-item-title>

                          </v-list-item-title>
                        </v-list-item>
                      </template>
                    </v-autocomplete>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">용어 설명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-textarea clearable dense color="ndColor" rows="1" v-model="addTerm_termDesc" ref="addTerm_termDesc"
                      placeholder="사람이나 기계 등이 움직이거나 행동을 시작한 날짜"
                      :rules="[() => !!addTerm_termDesc || '용어 설명은 필수 입력값입니다.']"></v-textarea>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader>요청 시스템</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <!-- <v-text-field v-model="addTerm_reqSysCd" dense color="ndColor"></v-text-field> -->
                    <treeselect v-model="addTerm_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="addTerm_reqSysCd" />
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader>이음동의어 목록</v-subheader>
                  </v-col>

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

                <v-row>
                  <v-col cols="4">
                    <v-subheader>코드그룹</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_codeGrp" dense color="ndColor" placeholder=""></v-text-field>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader>담당기관명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_chrgOrg" dense color="ndColor" placeholder=""></v-text-field>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader>공통표준여부</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-radio-group v-model="addTerm_commStndYn" row mandatory dense hide-details>
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
                    <v-text-field v-model="addTerm_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
                  </v-col>
                </v-row>

                <v-col class="text-right">
                  <v-btn text class="gray white--text" @click="addModalStep = 2"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    이전
                  </v-btn>
                </v-col>
              </v-stepper-content>
            </v-stepper>
          </v-form>
        </template>
      </NdModal>
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
                    <h3 :style="{ margin: '10px 0px' }">{{ item.wordNm }}</h3>
                    <v-col cols="12" :style="{ padding: '0px' }">
                      <v-data-table id="updateTerm_wordList_table" class="px-4 pb-3" :headers="wordListHeader"
                        :items="item.wordLst" item-key="index" v-model="updateTerm_selected_word_list[index]"
                        :value="updateTerm_selected_word_list[index]" hide-default-footer show-select>
                      </v-data-table>
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

                  <v-row>
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
                      <v-subheader>요청 시스템</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <treeselect v-model="updateTerm_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="updateTerm_reqSysCd" />
                      <!-- <v-text-field v-model="updateTerm_reqSysCd" dense color="ndColor"></v-text-field> -->
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

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>코드그룹</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_codeGrp" dense color="ndColor" placeholder=""></v-text-field>
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
  </v-main>
</template>

<script>
import axios from 'axios';
import NdModal from "./../views/modal/NdModal.vue"
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'

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
    itemsPerPage() {
      this.setListPage();
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
        // console.log(val);

        // 빈 배열 삭제
        const validWords = val.filter(word => word.length !== 0);

        // 단어 체크
        for (let i = 0; i < validWords.length; i++) {
          let _partOfSpeech = validWords[i][0].partOfSpeech;
          let _word = validWords[i][0].wordNm;
          let _id = validWords[i][0].id;

          this.checkSelectedWordStatus(_partOfSpeech, _word, _id)
        }

        // console.log(val);
      },
      deep: true
    },
    updateTerm_selected_word_list: {
      handler(val) {
        // console.log(val);

        // 빈 배열 삭제
        const validWords = val.filter(word => word.length !== 0);

        // 단어 체크
        for (let i = 0; i < validWords.length; i++) {
          let _partOfSpeech = validWords[i][0].partOfSpeech;
          let _word = validWords[i][0].wordNm;
          let _id = validWords[i][0].id;

          this.checkSelectedWordStatus(_partOfSpeech, _word, _id)
        }

        // console.log(val);
      },
      deep: true
    },
    addTerm_wordList() {
      // console.log(this.addTerm_wordList);
      // 용어 등록 title 옆에 사용자가 선택한 단어 보여주기
      this.createWordToTerm(this.addTerm_wordList)
      this.createTermEngAbrvNm(this.addTerm_wordList)
    },
    updateTerm_wordList() {
      // console.log(this.updateTerm_wordList);
      // 용어 수정 title 옆에 사용자가 선택한 단어 보여주기
      this.createWordToTerm(this.updateTerm_wordList)
      this.createTermEngAbrvNm(this.updateTerm_wordList)
    }
  },
  data: () => ({
    // 용어 목록
    termItems: [],
    // 검색 용어
    searchTerm: '',
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
    itemsPerPage: 10,
    // 등록 관련
    addModalStep: 1, // 용어 등록 스테퍼 카운트
    addTerm_termNm: null, // 용어 등록 용어명
    addTerm_wordListArr: [], // 용어 등록 단어 목록 배열
    addTerm_termEngAbrvNm: null, // 용어 등록 용어영문명 (자동 생성하여 보여줌)
    addTerm_termDesc: null,
    addTerm_domainNm: null,
    addTerm_domainNmItems: [],
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
    // 수정 관련
    updateModalStep: 1, // 용어 수정 스테퍼 카운트
    updateTerm_id: null,
    updateTerm_wordListObj: {}, // 용어 등록 단어 목록 오브젝트
    updateTerm_wordListArr: [], // 용어 등록 단어 목록 배열
    updateTerm_termNm: null,
    updateTerm_termEngAbrvNm: null,
    updateTerm_termDesc: null,
    updateTerm_domainNm: null,
    updateTerm_domainNmItems: [],
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
      // { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysCd', width: '15%' },
      { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysNm', width: '15%' },
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
  created() {
    // 데이터 표준 메뉴의 용어 선택 시 term data를 불러온다.
    this.getTermData();
    this.getSystemList();
  },
  methods: {
    getSystemList() {
      // 시스템 리스트 가지고 오기
      try {
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList')
          .then((res) => {
            // console 표시
            console.log("📃 SYSTEM INFO LIST ↓↓↓")
            console.log(res.data);

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
          })
      } catch (error) {
        console.log(err);
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

        let schNm = null;

        if (this.searchTerm !== '') {
          schNm = this.searchTerm
        }
        let schAprvYn = ''
        if (this.searchApproval === true) {
          schAprvYn = 'Y'
        } else {
          schAprvYn = 'N'
        }

        let _url = this.$APIURL.base + "api/std/getTermsList";

        axios.post(_url, {
          'schNm': schNm,
          'schAprvYn': schAprvYn
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

      // API 주소
      const _url = this.$APIURL.base + "api/std/uploadTermsList";

      // form data
      const formData = new FormData();
      formData.append('file', this.excelFile);

      // form header
      const headers = { 'Content-Type': 'multipart/form-data' };

      // 일괄 등록 시 기존에 이미 등록된 용어라면 update를 하고 없으면 create를 하는 방식으로 처리
      try {
        axios.post(_url, formData, { headers }).then((res) => {
          // console.log(res)

          if (res.data.resultCode === 200) {
            this.$swal.fire({
              title: '용어 일괄 등록이 완료되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            // table update
            this.getTermData();

          } else {

            this.$swal.fire({
              title: '용어 일괄 등록 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }

        }).catch(error => {
          this.$swal.fire({
            title: '용어 일괄 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        this.$swal.fire({
          title: '용어 일괄 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }

      // input 초기화
      document.getElementById('inputTermUpload').value = '';
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
      this.addTerm_domainNm = null;
      this.addTerm_domainNmItems = [];
      this.addTerm_codeGrp = null;
      this.addTerm_chrgOrg = null;
      this.addTerm_commStndYn = 'N';
      this.addTerm_magntdOrd = null;
      this.addTerm_reqSysCd = null;
      this.addTerm_selected_word_list = [];
      this.addTerm_wordList = [];
    },
    updateFormReset() {
      // 용어 수정 모달 초기화
      this.updateModalStep = 1;
      this.updateTerm_wordListArr = [];
      this.updateTerm_domainNmItems = [];
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
        } else if (this.addTerm_domainNm === null) {
          _attr = '도메인명은'
          this.$refs.addTerm_domainNm.focus()
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
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
        } else if (this.updateTerm_domainNm === null) {
          _attr = '도메인명은'
          this.$refs.updateTerm_domainNm.focus()
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
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
        let _term_name = this.addTerm_termNm;

        // if (_term_name !== this.addTerm_user_selected_word) {
        //   _term_name = this.addTerm_user_selected_word;
        // }

        let termData = {
          'termsNm': _term_name,
          'termsEngAbrvNm': this.addTerm_termEngAbrvNm,
          'termsDesc': this.addTerm_termDesc,
          'domainNm': this.addTerm_domainNm,
          'codeGrp': this.addTerm_codeGrp,
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

            this.$swal.fire({
              title: '새로운 용어가 등록되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

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

        let _term_name = this.updateTerm_termNm;

        // 2023.04.26 수정 : 사용자가 직접 용어명에 입력한 값으로 사용하도록 함

        // if (_term_name !== this.updateTerm_user_selected_word) {
        //   _term_name = this.updateTerm_user_selected_word;
        // }

        let termData = {
          'id': this.updateTerm_id,
          'termsNm': _term_name,
          'termsEngAbrvNm': this.updateTerm_termEngAbrvNm,
          'termsDesc': this.updateTerm_termDesc,
          'domainNm': this.updateTerm_domainNm,
          'codeGrp': this.updateTerm_codeGrp,
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
    checkSelectedWordStatus(partOfSpeech, word, id) {
      if ("NN" !== partOfSpeech &&
        "SL" !== partOfSpeech &&
        "SN" !== partOfSpeech &&
        "NF" !== partOfSpeech
      ) {
        // 명사가 아닐 때
        // console.log(_wordName, " 단어 아님")

        if (this.addTermModalShow) {
          this.addTerm_wordListArr = [];
        } else if (this.updateTermModalShow) {
          this.updateTerm_wordListArr = [];
        }

        this.$swal.fire({
          title: word,
          html: '명사가 아닙니다.<br />명사로 등록된 단어를 이용하여<br />용어를 구성해야 합니다.',
          confirmButtonText: '확인',
          icon: 'error',
        }).then((result) => {
          if (result.isConfirmed) {
            // 확인 클릭 시 용어명 입력 스텝으로 이동
            if (this.addTermModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.addTerm_selected_word_list = [];
              this.addModalStep = 1;
            } else if (this.updateTermModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.updateTerm_selected_word_list = []
              this.updateModalStep = 1;
            }
          }
        })

        return;
      } else if (("NN" === partOfSpeech || "SL" === partOfSpeech || "SN" === partOfSpeech || "NF" === partOfSpeech) && id === null) {
        // console.log(_wordName, " 단어 등록 해야 함")
        // 단어이지만 단어목록에 등록되지 않은 단어일 때
        if (this.addTermModalShow) {
          this.addTerm_wordListArr = [];
        } else if (this.updateTermModalShow) {
          this.updateTerm_wordListArr = [];
        }

        this.$swal.fire({
          title: word,
          html: '등록되지 않은 단어입니다.<br />등록을 위해 단어 페이지로 이동할까요?',
          confirmButtonText: '이동',
          showCancelButton: true,
          cancelButtonText: '취소',
          icon: 'error',
        }).then((result) => {
          if (result.isConfirmed) {

            if (this.addTermModalShow) {
              // 용어 등록 모달 초기화
              this.addFormReset();
              // 확인 버튼 클릭 시 단어 페이지로 이동
              document.getElementById('nav_word').click();
              setTimeout(() => {
                document.getElementById('addWordBtn').click();
              }, 500);
            } else if (this.updateTermModalShow) {
              // 확인 버튼 클릭 시 단어 페이지로 이동
              document.getElementById('nav_word').click();
              setTimeout(() => {
                document.getElementById('addWordBtn').click();
              }, 500);
            }
          } else {
            // 취소 버튼 클릭 시 이전 스텝으로 이동
            if (this.addTermModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.addTerm_selected_word_list = [];
              this.addModalStep = 1;
            } else if (this.updateTermModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.updateTerm_selected_word_list = []
              this.updateModalStep = 1;
            }
          }
        })
        return;
      } else {
        // 단어목록에 등록된 단어이며 명사일 때 단어
        this.collectSelectedItems();
      }
    },
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
          let _domainClsfNm = sortedArr[i].domainClsfNm;

          if (_domainClsfNm !== null) {
            this.getDomainInfoByClsfNm(_domainClsfNm);
          } else if (_domainClsfNm === null) {
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
        this.getWordListByNm();
        this.addModalStep = 2;
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

        // index 추가
        for (let i = 0; i < res.data.length; i++) {
          res.data[i].wordLst[0].index = i;
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

        }).catch(error => {
          console.error(error);
        })
      } catch (error) {
        console.error(error);
      }
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
  /* padding: 10px 16px; */
}

.tabsStyle .v-tab {
  border-top-right-radius: 10px !important;
  border-top-left-radius: 10px !important;
  color: rgba(0, 0, 0, 0.8);
  font-weight: 600;
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
  border-top: thin solid rgba(0, 0, 0, 0.12);
}

#term_wordItemsList_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.12);
}

.splitBottomWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
}

.split_bottom_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 0px;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
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
}

.liStyle {
  border: 1px solid rgba(0, 0, 0, 0.25);
  margin: 5px 0px;
  display: flex;
  justify-content: space-between;
  align-items: center;
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