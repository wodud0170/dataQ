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
              <span :style="{ fontSize: '.875rem' }">코드명</span>
              <!-- 코드명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchCode" v-on:keyup.enter="getCodeData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 승인 여부 추가 -->
              <v-checkbox class="codeSearchApv" v-model="searchApproval" label="승인 여부" color="ndColor"
                hide-details></v-checkbox>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getCodeData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>
          </v-sheet>
          <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-btn :style="{ width: '150px' }" class="gradient" v-on:click="showModal('managingCodeval')"
              title="코드 항목 관리">코드 항목 관리</v-btn>
            <v-btn class="gradient" v-on:click="showModal('codeAdd')" title="등록">등록</v-btn>
            <v-btn class="gradient" v-on:click="excelFileUpload" title="일괄 등록">일괄 등록</v-btn>
            <v-btn class="gradient" v-on:click="codeListDownload()" title="다운로드">다운로드</v-btn>
            <v-btn class="gradient" v-on:click="codeRemoveItem()" title="삭제">삭제</v-btn>
            <input type="file" @change="readExcelFile" ref="file" id="inputCodeUpload" :style="{ display: 'none' }"
              accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ codeItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
        <!-- 코드 목록 -->
        <v-data-table id="code_table" :headers="codeHeaders" :items="codeItems" :page.sync="page"
          :items-per-page="itemsPerPage" hide-default-footer item-key="id" show-select class="px-4 pb-3"
          v-model="removeItems" @input="enterSelect()" :loading="loadTable" loading-text="잠시만 기다려주세요.">
          <!-- 클릭 가능한 아이템 설정 : 코드명  -->
          <template v-slot:[`item.codeNm`]="{ item }">
            <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="showDetail(item)">{{
              item.codeNm
            }}</span>
          </template>
          <!-- 데이터 없음 -->
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
          <!-- 코드 상세보기 콘텐츠 -->
          <div class="split_bottom" v-show="selectedItem.length != 0">
            <v-sheet class="splitBottomWrapper">
              <!-- 타이틀 -->
              <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
                <span class="splitBottomSpan"
                  :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                    detailCode
                  }}'</span>
                <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;상세 보기</span>
              </v-sheet>
              <!-- 수정 / 삭제 버튼 -->
              <v-sheet class="pr-4 pl-4">
                <v-btn class="gradient" v-on:click="showModal('codeUpdate')">수정</v-btn>
              </v-sheet>
            </v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-data-table id="code_detail_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
                <template v-slot:body="{ items }">
                  <tbody>
                    <!-- 상세 테이블 왼쪽  -->
                    <tr v-for="header in detaileHeaders" :key="header.value">
                      <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '15%' }">
                        {{ header.text }}
                      </td>
                      <!-- 상세 테이블 오른쪽  -->
                      <td v-for="item in items" :key="item.codeNm">
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
              </v-data-table>
            </v-sheet>
          </div>
        </v-sheet>
        <v-sheet v-else class="tabContentsWrapper">
          <!-- 코드 항목 목록 콘텐츠 -->
          <v-sheet class="tabContents" v-show="selectedItem.length != 0">
            <v-sheet class="splitTopWrapper pt-4 pb-4"
              v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
              <!-- 검색 영역 -->
              <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]"></v-sheet>
              <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
              <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
                <!-- <v-btn class="gradient" v-on:click="showModal('codeValAdd')" title="등록">등록</v-btn>
                <v-btn class="gradient" v-on:click="excelCodeValFileUpload()" title="일괄 등록">일괄 등록</v-btn>
                <v-btn class="gradient" v-on:click="codeValListDownload()" title="다운로드">다운로드</v-btn> -->
                <v-btn class="gradient" v-on:click="codeValRemoveItem()" title="삭제">삭제</v-btn>
                <input type="file" @change="readCodeValExcelFile" ref="file" id="inputCodeValUpload"
                  :style="{ display: 'none' }" accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
              </v-sheet>

            </v-sheet>

            <!-- 코드 항목 목록 테이블-->
            <v-data-table id="codeVal_table" :headers="codeValHeader" :items="codeValItems" :page.sync="codeValPage"
              :items-per-page="codeValitemsPerPage" hide-default-footer item-key="id" show-select class="px-4 pb-3"
              v-model="removeCodeValItems" @input="enterSelectCodeVal()">
              <!-- 클릭 가능한 아이템 설정 : 코드 항목(값)명  -->
              <template v-slot:[`item.codeNm`]="{ item }">
                <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="selectedCodeVal(item)">{{
                  item.codeNm
                }}</span>
              </template>
              <!-- 데이터 없음 -->
              <template v-slot:no-data>
                <v-alert>
                  데이터가 존재하지 않습니다.
                </v-alert>
              </template>
            </v-data-table>

            <v-sheet class="codeValTable_wrap">
              <!-- 페이지네이션 -->
              <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="codeValPageCount > 1">
                <v-pagination v-model="page" :length="codeValPageCount" prev-icon="mdi-menu-left"
                  next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
              </div>
            </v-sheet>
          </v-sheet>
        </v-sheet>
      </SplitArea>
    </Split>
    <!-- Add code Modal -->
    <v-dialog max-width="950px" v-model="addCodeModalShow">
      <NdModal @hide="hideModal('codeAdd')" @submit="submitDialog('codeAdd')" :footer-submit="true"
        :header-title="'코드 등록 ' + (addTerm_user_selected_word.length > 0 ? '- ' + addTerm_user_selected_word : '')"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <v-form ref="form">
            <v-stepper v-model="addCodeModalStep" vertical :style="{ boxShadow: 'none !important' }">
              <v-stepper-step :complete="addCodeModalStep > 1" step="1" color="ndColor" v-on:click="addCodeModalStep = 1">
                코드명 입력
                <!-- <small>Summarize if needed</small> -->
              </v-stepper-step>

              <v-stepper-content step="1">
                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">코드명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_termNm" ref="addTerm_termNm"
                      :rules="[() => !!addTerm_termNm || '코드명은 필수 입력값입니다.']" clearable required dense placeholder="가동개시일자"
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

              <v-stepper-step :complete="addCodeModalStep > 2" step="2" color="ndColor" v-on:click="addNextStep(1)">
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
                  <v-btn text class="gray white--text" @click="addCodeModalStep = 1"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    이전
                  </v-btn>
                  <v-btn class="white--text" color="ndColor" v-on:click="addNextStep(2)"
                    :style="{ width: '80px !important', height: '30px !important' }">
                    다음
                  </v-btn>
                </v-col>
              </v-stepper-content>

              <v-stepper-step :complete="addCodeModalStep > 3" step="3" color="ndColor">
                코드 정보 입력
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
                    <v-subheader class="reqText">코드 영문 약어명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_termEngAbrvNm" required dense color="ndColor" readonly
                      filled></v-text-field>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">코드 설명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-textarea clearable dense color="ndColor" rows="1" v-model="addTerm_termDesc" ref="addTerm_termDesc"
                      placeholder="사람이나 기계 등이 움직이거나 행동을 시작한 날짜"
                      :rules="[() => !!addTerm_termDesc || '코드 설명은 필수 입력값입니다.']"></v-textarea>
                  </v-col>
                </v-row>

                <v-row>
                  <v-col cols="4">
                    <v-subheader class="reqText">도메인명</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-autocomplete dense required color="ndColor" v-model="addTerm_domainNm" ref="addTerm_domainNm"
                      :placeholder="'선택'" :items="addTerm_domainNmItems" :rules="[v => !!v || '도메인명은 필수 입력값입니다.']"
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
                    <v-subheader class="reqText">코드그룹</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <v-text-field v-model="addTerm_codeGrp" ref="addTerm_codeGrp"
                      :rules="[() => !!addTerm_codeGrp || '코드 그룹은 필수 입력값입니다.']" clearable required dense
                      color="ndColor"></v-text-field>
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
                  <v-btn text class="gray white--text" @click="addCodeModalStep = 2"
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
    <!-- update code Modal -->
    <v-dialog max-width="950px" v-model="updateCodeModalShow">
      <NdModal @hide="hideModal('codeUpdate')" @submit="submitDialog('codeUpdate')" :footer-submit="true"
        :header-title="'코드 수정 ' + (updateTerm_user_selected_word.length > 0 ? '- ' + updateTerm_user_selected_word : '')"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">
              <v-stepper v-model="updateCodeModalStep" vertical :style="{ boxShadow: 'none !important' }">
                <v-stepper-step :complete="updateCodeModalStep > 1" step="1" color="ndColor"
                  v-on:click="updateCodeModalStep = 1">
                  코드명 입력
                  <!-- <small>Summarize if needed</small> -->
                </v-stepper-step>

                <v-stepper-content step="1">
                  <v-row>
                    <v-col cols="4">
                      <v-subheader class="reqText">코드명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_termNm" ref="updateTerm_termNm"
                        :rules="[() => !!updateTerm_termNm || '코드명은 필수 입력값입니다.']" clearable required dense
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

                <v-stepper-step :complete="updateCodeModalStep > 2" step="2" color="ndColor"
                  v-on:click="updateNextStep(1)">
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
                    <v-btn text class="gray white--text" @click="updateCodeModalStep = 1"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      이전
                    </v-btn>
                    <v-btn class="white--text" color="ndColor" v-on:click="updateNextStep(2)"
                      :style="{ width: '80px !important', height: '30px !important' }">
                      다음
                    </v-btn>
                  </v-col>
                </v-stepper-content>

                <v-stepper-step :complete="updateCodeModalStep > 3" step="3" color="ndColor">
                  코드 정보 입력
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
                      <v-subheader class="reqText">코드 영문 약어명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_termEngAbrvNm" required dense color="ndColor" readonly
                        filled></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader class="reqText">코드 설명</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-textarea clearable dense color="ndColor" rows="1" v-model="updateTerm_termDesc"
                        ref="updateTerm_termDesc" placeholder="사람이나 기계 등이 움직이거나 행동을 시작한 날짜"
                        :rules="[() => !!updateTerm_termDesc || '코드 설명은 필수 입력값입니다.']"></v-textarea>
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
                      <v-subheader class="reqText">코드그룹</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <v-text-field v-model="updateTerm_codeGrp" ref="updateTerm_codeGrp"
                        :rules="[() => !!updateTerm_codeGrp || '코드 그룹은 필수 입력값입니다.']" clearable required dense
                        color="ndColor"></v-text-field>
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="4">
                      <v-subheader>요청 시스템</v-subheader>
                    </v-col>
                    <v-col cols="8">
                      <!-- <v-text-field v-model="updateTerm_reqSysCd" dense color="ndColor"></v-text-field> -->
                      <treeselect v-model="updateTerm_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="updateTerm_reqSysCd" />
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
                    <v-btn text class="gray white--text" @click="updateCodeModalStep = 2"
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

    <!-- add codeVal Modal -->
    <v-dialog max-width="600" v-model="addCodeValModalShow">
      <NdModal @hide="hideModal('codeValAdd')" @submit="submitDialog('codeValAdd')" :footer-submit="true"
        header-title="코드 항목(값) 등록" footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <v-container fluid>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드 그룹명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="addCodeValCodeGrp" ref="addCodeValCodeGrp" 
                :rules="[() => !!addCodeValCodeGrp || '코드 그룹명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="addCodeValCodeNm" ref="addCodeValCodeNm"  
                :rules="[() => !!addCodeValCodeNm || '코드명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드영문명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="addCodeValCodeEngNm" ref="addCodeValCodeEngNm"  
                :rules="[() => !!addCodeValCodeEngNm || '코드 영문명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드값</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="addCodeValCodeVal" ref="addCodeValCodeVal"
                  :rules="[() => !!addCodeValCodeVal || '코드값은 필수 입력값입니다.']" clearable required dense placeholder=""
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>코드값 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="addCodeValCodeValDesc" clearable required dense placeholder=""
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>

          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- update codeVal Modal -->
    <v-dialog max-width="600" v-model="updateCodeValModalShow">
      <NdModal @hide="hideModal('codeValUpdate')" @submit="submitDialog('codeValUpdate')" :footer-submit="true"
        header-title="코드 항목(값) 수정" footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <v-container fluid>
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드 그룹명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateCodeValCodeGrp" ref="updateCodeValCodeGrp" 
                :rules="[() => !!updateCodeValCodeGrp || '코드 그룹명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateCodeValCodeNm" ref="updateCodeValCodeNm"  
                :rules="[() => !!updateCodeValCodeNm || '코드명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드영문명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateCodeValCodeEngNm" ref="updateCodeValCodeEngNm"  
                :rules="[() => !!updateCodeValCodeEngNm || '코드 영문명은 필수 입력값입니다.']" clearable required dense color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">코드값</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateCodeValCodeVal" ref="updateCodeValCodeVal"
                  :rules="[() => !!updateCodeValCodeVal || '코드값은 필수 입력값입니다.']" clearable required dense placeholder="001"
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>코드값 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateCodeValCodeValDesc" clearable required dense placeholder=""
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- manage codeVal Modal -->
    <v-dialog max-width="1200px" v-model="managingCodevalModalShow">
      <NdModal @hide="hideModal('managingCodeval')" :footer-submit="false" header-title="코드 항목(값) 관리"
        footer-hide-title="닫기">
        <template v-slot:body>
          <v-container fluid>
            <v-sheet class="modalContents">
              <v-sheet class="splitTopWrapper pt-4 pb-4"
                v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
                <!-- 검색 -->
                <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
                  <v-row :style="{ alignItems: 'center', margin: '0px' }">
                    <span :style="{ fontSize: '.875rem' }">코드 항목(값)명</span>
                    <v-text-field class="pr-4 pl-4" v-model="searchCodeData" v-on:keyup.enter="executeSearchCodeData"
                      @click:clear="clearMessageCodeVal" clearable prepend-icon="" clear-icon="mdi-close-circle"
                      type="text" color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
                    </v-text-field>
                    <v-btn class="gradient" title="검색" v-on:click="executeSearchCodeData"
                      :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
                  </v-row>
                </v-sheet>
                <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
                <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
                  <v-btn class="gradient" v-on:click="showModal('codeValAdd')" title="등록">등록</v-btn>
                  <v-btn class="gradient" v-on:click="excelCodeValFileUpload()" title="일괄 등록">일괄 등록</v-btn>
                  <v-btn class="gradient" v-on:click="codeValListDownload()" title="다운로드">다운로드</v-btn>
                  <v-btn class="gradient" v-on:click="codeDataRemoveItem()" title="삭제">삭제</v-btn>
                  <input type="file" @change="readCodeValExcelFile" ref="file" id="inputCodeValUpload"
                    :style="{ display: 'none' }"
                    accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
                </v-sheet>

              </v-sheet>

              <!-- 모든 코드 항목 목록 테이블 !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!-->
              <v-data-table id="manageCodeVal_table" :headers="codeValHeader" :items="codeDataItems"
                :page.sync="codeDataPage" :items-per-page="codeDataitemsPerPage" hide-default-footer item-key="id"
                show-select class="px-4 pb-3" v-model="removeCodeDataItems" @input="enterSelectCodeData()">
                <!-- 클릭 가능한 아이템 설정 : 코드 항목(값)명  -->
                <template v-slot:[`item.codeNm`]="{ item }">
                  <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="selectedCodeData(item)">{{
                    item.codeNm
                  }}</span>
                </template>
                <!-- 데이터 없음 -->
                <template v-slot:no-data>
                  <v-alert>
                    데이터가 존재하지 않습니다.
                  </v-alert>
                </template>
              </v-data-table>
              <!-- 페이지네이션 -->
                <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="codeDataPageCount > 1">
                <v-pagination v-model="codeDataPage" :length="codeDataPageCount" prev-icon="mdi-menu-left"
                  next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
              </div>
            </v-sheet>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- 일괄등록 Modal -->
    <v-dialog max-width="520" v-model="collectiveCodeModalShow" persistent>
      <v-card>
        <v-card-title class="pb-2" :style="{ fontSize: '1rem', fontWeight: 'bold' }">
          <v-icon left color="ndColor">mdi-upload</v-icon>
          {{ uploadDialogTitle }} 일괄등록 진행
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
          <v-btn v-if="!isUploading" color="ndColor" text @click="collectiveCodeModalShow = false">닫기</v-btn>
          <span v-else :style="{ fontSize: '0.8rem', color: '#999', paddingRight: '12px' }">완료될 때까지 기다려주세요...</span>
        </v-card-actions>
      </v-card>
    </v-dialog>

  </v-main>
</template>

<script>
import axios from "axios";
import NdModal from "./../views/modal/NdModal.vue"
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import { eventBus } from './../eventBus.js'

export default {
  name: 'DSCode',
  props: ['isMobile'],
  components: {
    NdModal,
    Treeselect
  },
  watch: {
    codeItems() {
      this.setListPage();
    },
    codeValItems() {
      this.setCodeValListPage();
    },
    codeDataItems() {
      this.setCodeDataListPage();
    },
    itemsPerPage() {
      this.setListPage();
    },
    codeDataitemsPerPage() {
      this.setCodeDataListPage();
    },
    addCodeModalStep() {
      if (this.addCodeModalStep === 1) {
        this.addTerm_selected_word_list = [];
      }
    },
    updateCodeModalStep() {
      if (this.updateCodeModalStep === 1) {
        this.updateTerm_selected_word_list = [];
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
    // 코드 목록
    codeItems: [],
    // 코드 값 목록
    codeValItems: [],
    // 코드 항목 관리에서 사용하는 코드 항목 목록
    codeDataItems: [],
    // 검색 코드
    searchCode: '',
    // 검색 승인 여부
    searchApproval: true,
    // 테이블 로딩
    loadTable: true,
    // 검색 코드 항목(값)
    searchCodeData: '',
    // 코드 등록 모달 보이기
    addCodeModalShow: false,
    // 코드 수정 모달 보이기
    updateCodeModalShow: false,
    // 코드 항목(값) 등록 모달 보이기
    addCodeValModalShow: false,
    // 코드 항목(값) 수정 모달 보이기
    updateCodeValModalShow: false,
    // 코드 항목(값) 관리 모달 보이기
    managingCodevalModalShow: false,
    // 코드 등록 스탭 카운터
    addCodeModalStep: 1,
    // 코드 추가 관련
    addTerm_termNm: null, // 코드 등록 코드명
    addTerm_termEngAbrvNm: null, // 코드 등록 코드영문명 (자동 생성하여 보여줌)
    addTerm_termDesc: null,
    addTerm_domainNm: null,
    addTerm_domainNmItems: [],
    addTerm_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    addTerm_allophSynmLst_count: 0,
    addTerm_wordListArr: [],
    addTerm_selected_word_list: [],
    addTerm_codeGrp: null,
    addTerm_chrgOrg: null,
    addTerm_commStndYn: 'Y',
    addTerm_magntdOrd: null,
    addTerm_reqSysCd: null,
    addTerm_word_length: 0,
    addTerm_wordList: [],
    addTerm_user_selected_word: '',
    // 수정 관련
    updateCodeModalStep: 1, // 코드 수정 스테퍼 카운트
    updateTerm_id: null,
    updateTerm_termNm: null,
    updateTerm_termEngAbrvNm: null,
    updateTerm_termDesc: null,
    updateTerm_domainNm: null,
    updateTerm_domainNmItems: [],
    updateTerm_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    updateTerm_allophSynmLst_count: 0,
    updateTerm_wordListArr: [],
    updateTerm_selected_word_list: [],
    updateTerm_codeGrp: null,
    updateTerm_chrgOrg: null,
    updateTerm_commStndYn: null,
    updateTerm_magntdOrd: null,
    updateTerm_reqSysCd: null,
    updateTerm_word_length: 0,
    updateTerm_wordList: [],
    updateTerm_user_selected_word: '',
    // 코드 항목(값) 추가 관련
    addCodeValCodeGrp: null,
    addCodeValCodeNm: null,
    addCodeValCodeEngNm: null,
    addCodeValCodeVal: null,
    addCodeValCodeValDesc: null,
    // 코드 항목(값) 수정 관련
    updateCodeValId: null,
    updateCodeValCodeGrp: null,
    updateCodeValCodeNm: null,
    updateCodeValCodeEngNm: null,
    updateCodeValCodeVal: null,
    updateCodeValCodeValDesc: null,
    // 선택한 코드의 정보들
    selectedItem: [],
    // 선택한 코드 값의 정보들
    selectedCodeValItem: [],
    // 코드 항목 관리에서 사용하는 선택한 코드 값의 정보들
    selectedCodeDataItem: [],
    // 선택한 코드 이름
    detailCode: null,
    // 코드 일괄 등록 파일
    excelFile: null,
    // 코드 항목(값)일괄 등록 파일
    codeValExcelFile: null,
    // 일괄 등록 진행 다이얼로그
    collectiveCodeModalShow: false,
    // 일괄 등록 진행 다이얼로그 제목
    uploadDialogTitle: '',
    // 일괄 등록 진행 상태
    isUploading: false,
    // 일괄 등록 로그
    uploadLogs: [],
    // 디테일 메뉴 탭
    detailTab: [
      { title: '코드 상세 보기', name: 'tab1', index: 0 },
      { title: '코드 항목(값) 목록', name: 'tab2', index: 1 }
    ],
    // 선택된 세부 탭
    activeDetailTab: 'tab1',
    // 삭제 관련
    removeItems: [],
    // 코드 값 삭제 관련
    removeCodeValItems: [],
    // 코드 항목관리에서 사용하는 코드 값 삭제 관련
    removeCodeDataItems: [],
    // 페이지 시작 번호
    page: 1,
    // 코드 값 테이블 페이지 시작 번호
    codeValPage: 1,
    // 코드 항목 관리 테이블 페이지 시작 번호
    codeDataPage: 1,
    // 총 페이지 수
    pageCount: null,
    // 코드 항목(값) 테이블 총 페이지 수
    codeValPageCount: null,
    // 코드 항목 관리 테이블 총 페이지 수
    codeDataPageCount: null,
    // 한 페이지에 보여지는 코드의 수
    itemsPerPage: 10,
    // 코드 값 테이블 한 페이지에 보여지는 코드의 수
    codeValitemsPerPage: 10,
    // 코드 항목 관리 테이블 한 페이지에 보여지는 코드의 수
    codeDataitemsPerPage: 10,

    // 상단 테이블 헤더
    codeHeaders: [
      { text: '코드명', align: 'center', sortable: false, value: 'codeNm', width: '15%' },
      { text: '코드영문약어명', align: 'center', sortable: false, value: 'codeEngAbrvNm', width: '15%' },
      { text: '코드설명', align: 'center', sortable: false, value: 'codeDesc' },
      { text: '도메인명', align: 'center', sortable: false, value: 'domainNm', width: '12%' },
      { text: '코드 그룹', align: 'center', sortable: false, value: 'codeGrp', width: '12%' },
    ],
    // 하단 테이블 헤더
    detaileHeaders: [
      { text: '코드명', align: 'center', sortable: false, value: 'codeNm', width: '15%' },
      { text: '코드영문약어명', align: 'center', sortable: false, value: 'codeEngAbrvNm', width: '15%' },
      { text: '코드설명', align: 'center', sortable: false, value: 'codeDesc' },
      { text: '도메인명', align: 'center', sortable: false, value: 'domainNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '허용값', sortable: false, align: 'center', value: 'allowValLst', width: '15%' },
      { text: '저장형식', sortable: false, align: 'center', value: 'storFmt', width: '15%' },
      { text: '표현형식', sortable: false, align: 'center', value: 'exprFmtLst', width: '15%' },
      { text: '코드 그룹', align: 'center', sortable: false, value: 'codeGrp', width: '15%' },
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
    // 코드 항목(값) 테이블 헤더
    codeValHeader: [
      { text: '코드명', align: 'center', sortable: false, value: 'codeNm' },
      { text: '코드그룹', align: 'center', sortable: false, value: 'codeGrp' },
      { text: '코드영문약어명', align: 'center', sortable: false, value: 'codeEngNm' },
      { text: '코드값', align: 'center', sortable: false, value: 'codeVal' },
      { text: '코드값설명', align: 'center', sortable: false, value: 'codeValDesc' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
    // 코드 등록/수정 단어 목록 테이블 헤더
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
  methods: {
    resetSearch() {
      this.searchCode = '';
      this.searchApproval = true;
    },
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.codeItems.length / this.itemsPerPage);
    },
    setCodeValListPage() {
      // 페이지네이션 버튼 개수
      this.codeValPageCount = Math.ceil(this.codeValItems.length / this.codeValitemsPerPage);
    },
    setCodeDataListPage() {
      // 페이지네이션 버튼 개수
      this.codeDataPageCount = Math.ceil(this.codeDataItems.length / this.codeDataitemsPerPage);
    },
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
    getCodeData() {
      this.loadTable = true;
      try {

        let schNm = null;

        if (this.searchCode !== '') {
          schNm = this.searchCode
        }
        let schAprvYn = ''
        if (this.searchApproval === true) {
          schAprvYn = 'Y'
        } else {
          schAprvYn = 'N'
        }

        let _url = this.$APIURL.base + "api/std/getCodeInfoList";

        axios.post(_url, {
          'schNm': schNm,
          'schAprvYn': schAprvYn
        }).then(result => {
          this.codeItems = result.data;

          // console 표시
          console.log("📃 CODE LIST ↓↓↓")
          console.log(result.data);

          // 하단 상세보기 초기화
          this.resetDetail();

          this.loadTable = false;
        }).catch(error => {
          this.$swal.fire({
            title: '코드 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        });

      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }
    },
    getCodeValList(codeNm) {
      try {
        axios.get(this.$APIURL.base + "api/std/getCodeDataListByNm", {
          params: {
            codeNm: codeNm
          }
        }).then(result => {
          // console.log(result)
          this.codeValItems = result.data;

          // console 표시
          console.log("📃 CODE VAL LIST ↓↓↓")
          console.log(result.data);

        }).catch(error => {
          this.$swal.fire({
            title: '코드 항목(값) 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        console.error(error);
      }
    },
    readExcelFile(event) {
      // 코드 일괄 등록에서 사용하는 function
      const file = event.target.files[0];

      // 취소일 때 return
      if (file === undefined) {
        return;
      }

      this.excelFile = this.$refs.file.files[0];

      // 진행 다이얼로그 열기
      this.uploadLogs = [];
      this.isUploading = true;
      this.uploadDialogTitle = '코드';
      this.collectiveCodeModalShow = true;

      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getCodeData();
        }
      }, 60000);

      const _url = this.$APIURL.base + "api/std/uploadCodeInfoList";
      const formData = new FormData();
      formData.append('file', this.excelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      // input 초기화
      document.getElementById('inputCodeUpload').value = '';
    },
    readCodeValExcelFile(event) {
      // 코드 항목(값)일괄 등록에서 사용하는 function
      const file = event.target.files[0];

      // 취소일 때 return
      if (file === undefined) {
        return;
      }

      this.codeValExcelFile = this.$refs.file.files[0];

      // 진행 다이얼로그 열기
      this.uploadLogs = [];
      this.isUploading = true;
      this.uploadDialogTitle = '코드데이터';
      this.collectiveCodeModalShow = true;

      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getCodeData();
        }
      }, 60000);

      const _url = this.$APIURL.base + "api/std/uploadCodeDataList";
      const formData = new FormData();
      formData.append('file', this.codeValExcelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      // input 초기화
      document.getElementById('inputCodeValUpload').value = '';
    },
    onUploadNotice(msg) {
      if (!this.collectiveCodeModalShow) return;
      const tag = this.uploadDialogTitle === '코드데이터' ? '[코드데이터]' : '[코드]';
      if (!msg.data || !msg.data.startsWith(tag)) return;
      const level = msg.noticeType === 'ERROR' ? 'ERROR' : 'INFO';
      this._addUploadLog(level, msg.data);
      if (msg.data.includes('완료 -')) {
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
        this.getCodeData();
        const summary = msg.data.replace(tag + ' ', '');
        const failMatch = summary.match(/실패:\s*(\d+)건/);
        const failCount = failMatch ? parseInt(failMatch[1]) : 0;
        this.$swal.fire({
          title: `${this.uploadDialogTitle} 일괄등록 완료`,
          text: summary,
          icon: failCount > 0 ? 'warning' : 'success',
          showConfirmButton: false,
          timer: 3000
        });
      }
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
    executeSearchCodeData() {
      if (this.searchCodeData === null || this.searchCodeData === "") {
        // 빈 값 입력 시 코드 목록 다시 불러오기 
        this.resetCodeDataList()
        return false;
      };

      // console.log(this.searchCodeData)
      try {
        axios.get(this.$APIURL.base + "api/std/getCodeDataListByNm", {
          params: {
            'codeNm': this.searchCodeData
          }
        }).then((res) => {
          this.codeDataItems = res.data;

          // console 표시
          console.log("🔎 SEARCH CODE DATA LIST ↓↓↓")
          console.log(this.codeDataItems);

          // 선택 삭제
          this.selectedCodeDataItem = [];
          this.removeCodeDataItems = [];

        }).catch((err) => {
          this.$swal.fire({
            title: '코드 값 검색 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchCode = ''
    },
    clearMessageCodeVal() {
      // 검색어 지워주기
      this.searchCodeData = ''
    },
    enterSelect() {
      // 코드명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeItems.length === 0) {
        this.selectedItem = [];
      }
    },
    enterSelectCodeVal() {
      // 코드명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeCodeValItems.length === 0) {
        this.selectedCodeValItem = [];
      }
    },
    enterSelectCodeData() {
      // 코드명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeCodeDataItems.length === 0) {
        this.selectedCodeDataItem = [];
        this.removeCodeDataItems = [];
      }
    },
    resetDetail() {
      // 선택한 코드 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.codeValItems = [];
      this.detailCode = null;
      this.resetCodeValDetail();
    },
    resetCodeValDetail() {
      // 코드 정보 리셋될 때 코드 값 항목도 리셋 필요
      this.selectedCodeValItem = [];
      this.removeCodeValItems = [];
      this.searchCodeData = '';
    },
    resetCodeList() {
      // 코드 목록 다시 불러오기
      this.getCodeData();
      this.searchCode = '';
      // this.resetBtnShow = false;
      this.resetDetail();
    },
    resetCodeDataList() {
      // 코드 값 목록 다시 불러오기
      this.searchCodeData = '';
      this.selectedCodeDataItem = [];
      this.removeCodeDataItems = [];
      this.getCodeDataList();
    },
    excelFileUpload() {
      // 일괄 등록 버튼 클릭
      let fileUpload = document.getElementById('inputCodeUpload')
      if (fileUpload != null) {
        fileUpload.click()
      }
    },
    excelCodeValFileUpload() {
      // 코드 항목(값)일괄 등록 버튼 클릭
      let fileUpload = document.getElementById('inputCodeValUpload')
      if (fileUpload != null) {
        fileUpload.click()
      }
    },
    codeListDownload() {
      let _keyWord = this.searchCode.length !== 0 ? this.searchCode : null;

      try {
        axios.get(this.$APIURL.base + "api/std/downloadCodeInfoList",
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

            link.setAttribute("download", `코드정보_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '코드 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('코드 다운로드 실패 :', error);
      }
    },
    codeValListDownload() {
      let _keyWord = this.searchCodeData.length !== 0 ? this.searchCodeData : null;

      try {
        axios.get(this.$APIURL.base + "api/std/downloadCodeDataList",
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

            link.setAttribute("download", `코드데이터_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '코드 항목(값) 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('코드 항목(값) 다운로드 실패 :', error);
      }
    },
    codeRemoveItem() {
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 코드를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      let removeName = '';

      for (let i = 0; i < this.removeItems.length; i++) {
        if (i === 0) {
          removeName += this.removeItems[i].codeNm;
        } else {
          removeName += ', ' + this.removeItems[i].codeNm;
        }
      }

      this.$swal.fire({
        title: '정말로 코드를 삭제할까요?',
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

          // console.log(removeItemArr)
          try {
            axios.post(this.$APIURL.base + "api/std/deleteCodeList", removeItemArr)
              .then(res => {
                // console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '코드가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getCodeData();
                  this.resetDetail();
                } else {
                  this.$swal.fire({
                    title: '코드 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '코드 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '코드 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    codeValRemoveItem() {
      if (this.removeCodeValItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 코드값을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      let removeName = '';

      for (let i = 0; i < this.removeCodeValItems.length; i++) {
        if (i === 0) {
          removeName += this.removeCodeValItems[i].codeVal;
        } else {
          removeName += ', ' + this.removeCodeValItems[i].codeVal;
        }
      }

      this.$swal.fire({
        title: '정말로 코드값을 삭제할까요?',
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
          for (let i = 0; i < this.removeCodeValItems.length; i++) {
            let removeObj = {
              id: this.removeCodeValItems[i].id
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/std/deleteCodeDatas", removeItemArr)
              .then(res => {
                // console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '코드값이 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getCodeValList(this.selectedItem[0].codeNm);

                } else {
                  this.$swal.fire({
                    title: '코드 값 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '코드값 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '코드값 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    codeDataRemoveItem() {
      if (this.removeCodeDataItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 코드값을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      let removeName = '';

      for (let i = 0; i < this.removeCodeDataItems.length; i++) {
        if (i === 0) {
          removeName += this.removeCodeDataItems[i].codeVal;
        } else {
          removeName += ', ' + this.removeCodeDataItems[i].codeVal;
        }
      }

      this.$swal.fire({
        title: '정말로 코드값을 삭제할까요?',
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
          for (let i = 0; i < this.removeCodeDataItems.length; i++) {
            let removeObj = {
              id: this.removeCodeDataItems[i].id
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/std/deleteCodeDatas", removeItemArr)
              .then(res => {
                // console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '코드값이 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.resetCodeDataList();

                } else {
                  this.$swal.fire({
                    title: '코드 값 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '코드값 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '코드값 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    showDetail(item) {
      // 코드명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 선택한 코드 이름을 타이틀에 보이기 위해 추가함
      this.detailCode = item.codeNm;
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
      // 코드값 리스트 가져오기
      this.getCodeValList(item.codeNm);
    },
    selectedCodeVal(item) {
      // 코드값 리스트에서 선택한 아이템
      this.selectedCodeValItem = [item];
      // remove item에 단독으로 넣어주기
      this.removeCodeValItems = [item];
      this.showModal('codeValUpdate');
    },
    selectedCodeData(item) {
      // 코드값 리스트에서 선택한 아이템
      this.selectedCodeDataItem = [item];
      // remove item에 단독으로 넣어주기
      this.removeCodeDataItems = [item];
      this.showModal('codeValUpdate');
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'codeAdd') {
        // 코드 추가 모달
        this.addCodeModalShow = true;
        this.addModalOpenSetCodeNm();
      } else if (value === 'codeUpdate') {
        // 코드 수정 모달
        this.updateModalInit();
        this.updateCodeModalShow = true;
      } else if (value === 'codeValAdd') {
        // 코드값 추가 모달
        // this.addCodeValModalInit();
        this.addCodeValModalShow = true;
      } else if (value === 'codeValUpdate') {
        if(this.managingCodevalModalShow) {
          // 코드 항목관리 수정 모달
          this.updateCodeDataModalInit();
        } else {
          // 코드값 수정 모달
          this.updateCodeValModalInit();
        }

        this.updateCodeValModalShow = true;
      } else if (value === 'managingCodeval') {
        //  코드항목 관리 모달
        this.managingCodevalModalShow = true;
      }
    },
    hideModal(value) {
      if (value === 'codeAdd') {
        this.addCodeModalShow = false;
        this.addFormReset();
        this.resetAddTermTextfield();
      } else if (value === 'codeUpdate') {
        this.updateCodeModalShow = false;
        this.updateFormReset();
        this.resetUpdateTermTextfield();
      } else if (value === 'codeValAdd') {
        this.addCodeValReset();
        this.addCodeValModalShow = false;
      } else if (value === 'codeValUpdate') {
        this.updateCodeValReset();
        this.updateCodeValModalShow = false;
      } else if (value === 'managingCodeval') {
        //  코드항목 관리 모달
        this.managingCodevalModalShow = false;
      }
    },
    addModalOpenSetCodeNm() {
      // 모달 오픈 시 검색어에 문자열이 있을 경우 코드명에 자동으로 입력
      if (this.searchCode !== '') {
        this.addTerm_termNm = this.searchCode;
      }
    },
    submitDialog(value) {
      if (value === 'codeAdd') {
        if (this.fieldcheck('codeAdd')) {
          this.createCode();
        }
      } else if (value === 'codeUpdate') {
        if (this.fieldcheck('codeUpdate')) {
          this.updateCode();
        }
      } else if (value === 'codeValAdd') {
        if (this.fieldcheck('codeValAdd')) {
          this.createCodeVal();
        }
      } else if (value === 'codeValUpdate') {
        if (this.fieldcheck('codeValUpdate')) {
          this.updateCodeVal();
        }
      }
    },
    createCode() {
      try {
        let arr_allophSynmLst = this.addTerm_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');

        let codeData = {
          'termsNm': this.addTerm_termNm,
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

        axios.post(this.$APIURL.base + 'api/std/createCode', codeData).then(res => {
          // console.log(res)
          if (res.data.resultCode === 200) {
            this.hideModal('codeAdd');

            this.$swal.fire({
              title: '새로운 코드가 등록되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            this.getCodeData()
          } else {
            this.$swal.fire({
              title: '코드 등록 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '코드 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        this.$swal.fire({
          title: '코드 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateCode() {
      try {
        let arr_allophSynmLst = this.updateTerm_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');

        let codeData = {
          'id': this.updateTerm_id,
          'termsNm': this.updateTerm_termNm,
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

        axios.post(this.$APIURL.base + 'api/std/updateCode', codeData).then(res => {
          // console.log(res)

          if (res.data.resultCode === 200) {
            this.hideModal('codeUpdate');

            this.$swal.fire({
              title: '코드가 수정되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            // 리셋
            this.resetCodeList();
          } else {
            this.$swal.fire({
              title: '코드 수정 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '코드 수정 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        this.$swal.fire({
          title: '코드 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    createCodeVal() {
      try {
        let codeValData = {
          'id': null,
          'codeGrp': this.addCodeValCodeGrp,
          'codeNm': this.addCodeValCodeNm,
          'codeEngNm': this.addCodeValCodeEngNm,
          'codeVal': this.addCodeValCodeVal,
          'codeValDesc': this.addCodeValCodeValDesc
        }

        console.log(codeValData)

        axios.post(this.$APIURL.base + 'api/std/createCodeData', codeValData).then(res => {
          // console.log(res)
          if (res.data.resultCode === 200) {
            this.hideModal('codeValAdd');

            this.$swal.fire({
              title: '새로운 코드 값이 등록되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            if(this.managingCodevalModalShow) {
              this.resetCodeDataList();
            }
            
          } else {
            this.$swal.fire({
              title: '코드 값 등록 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '코드 값 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        this.$swal.fire({
          title: '코드 값 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateCodeVal() {
      try {
        let codeValData = {
          'id': this.updateCodeValId,
          'codeGrp': this.updateCodeValCodeGrp,
          'codeNm': this.updateCodeValCodeNm,
          'codeEngNm': this.updateCodeValCodeEngNm,
          'codeVal': this.updateCodeValCodeVal,
          'codeValDesc': this.updateCodeValCodeValDesc
        }

        // console.log(codeValData)
        axios.post(this.$APIURL.base + 'api/std/updateCodeData', codeValData).then(res => {
          // console.log(res)
          if (res.data.resultCode === 200) {
            // if (res.data.resultCode === 200) {
            this.hideModal('codeValUpdate');

            this.$swal.fire({
              title: '코드 값이 수정되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            })

            if(this.managingCodevalModalShow) {
              this.resetCodeDataList();
            } else {
              this.getCodeValList(this.selectedItem[0].codeNm);
            }
            
          } else {
            this.$swal.fire({
              title: '코드 값 수정 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '코드 값 수정 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        this.$swal.fire({
          title: '코드 값 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateModalInit() {
      // 이음동의어
      let allophSynmLst_data = [];

      if (this.selectedItem[0].allophSynmLst !== null) {
        // if (this.selectedItem[0].allophSynmLst.length > 0) {
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
      this.updateTerm_termNm = this.selectedItem[0].codeNm;
      this.updateTerm_termEngAbrvNm = this.selectedItem[0].codeEngAbrvNm;
      this.updateTerm_termDesc = this.selectedItem[0].codeDesc;
      this.updateTerm_domainNm = this.selectedItem[0].domainNm;
      this.updateTerm_codeGrp = this.selectedItem[0].codeGrp;
      this.updateTerm_chrgOrgcreateWordToTermCd = this.selectedItem[0].chrgOrg;
      this.updateTerm_commStndYn = this.selectedItem[0].commStndYn;
      this.updateTerm_magntdOrd = this.selectedItem[0].magntdOrd;
      this.updateTerm_reqSysCd = this.selectedItem[0].reqSysCd;
      // 타이틀에 용어명 넣어주기
      this.updateTerm_user_selected_word = this.selectedItem[0].codeNm;
      // 이음동의어
      this.updateTerm_allophSynmLst_arr = allophSynmLst_data;
    },
    updateCodeValModalInit() {
      this.updateCodeValId = this.selectedCodeValItem[0].id;
      this.updateCodeValCodeGrp = this.selectedCodeValItem[0].codeGrp;
      this.updateCodeValCodeNm = this.selectedCodeValItem[0].codeNm;
      this.updateCodeValCodeEngNm = this.selectedCodeValItem[0].codeEngNm;
      this.updateCodeValCodeVal = this.selectedCodeValItem[0].codeVal;
      this.updateCodeValCodeValDesc = this.selectedCodeValItem[0].codeValDesc;
    },
    updateCodeDataModalInit() {
      this.updateCodeValId = this.selectedCodeDataItem[0].id;
      this.updateCodeValCodeGrp = this.selectedCodeDataItem[0].codeGrp;
      this.updateCodeValCodeNm = this.selectedCodeDataItem[0].codeNm;
      this.updateCodeValCodeEngNm = this.selectedCodeDataItem[0].codeEngNm;
      this.updateCodeValCodeVal = this.selectedCodeDataItem[0].codeVal;
      this.updateCodeValCodeValDesc = this.selectedCodeDataItem[0].codeValDesc;
    },
    addCodeValModalInit() {
      this.addCodeValCodeGrp = this.selectedItem[0].codeGrp;
      this.addCodeValCodeNm = this.selectedItem[0].codeNm;
      this.addCodeValCodeEngNm = this.selectedItem[0].codeEngAbrvNm;
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'codeAdd') {
        if (this.addTerm_termNm === null || this.addTerm_termNm === '') {
          _attr = '코드명은';
          this.$refs.addTerm_termNm.focus()
        } else if (this.addTerm_termDesc === null || this.addTerm_termDesc === '') {
          _attr = '코드 설명은'
          this.$refs.addTerm_termDesc.focus()
        } else if (this.addTerm_domainNm === null || this.addTerm_domainNm === '') {
          _attr = '도메인명은'
          this.$refs.addTerm_domainNm.focus()
        } else if (this.addTerm_codeGrp === null || this.addTerm_codeGrp === '') {
          _attr = '코드 그룹은'
          this.$refs.addTerm_codeGrp.focus()
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
      } else if (status === 'codeUpdate') {
        if (this.updateTerm_termNm === null || this.updateTerm_termNm === '') {
          _attr = '코드명은';
          this.$refs.updateTerm_termNm.focus()
        } else if (this.updateTerm_termDesc === null || this.updateTerm_termDesc === '') {
          _attr = '코드 설명은'
          this.$refs.updateTerm_termDesc.focus()
        } else if (this.updateTerm_domainNm === null || this.updateTerm_domainNm === '') {
          _attr = '도메인명은'
          this.$refs.updateTerm_domainNm.focus()
        } else if (this.updateTerm_codeGrp === null || this.updateTerm_codeGrp === '') {
          _attr = '코드 그룹은'
          this.$refs.updateTerm_codeGrp.focus()
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
      } else if (status === 'codeValAdd') {
        if (this.addCodeValCodeGrp === null || this.addCodeValCodeGrp === '') {
          _attr = '코드 그룹명은'
          this.$refs.addCodeValCodeGrp.focus()
        }

        if (this.addCodeValCodeNm === null || this.addCodeValCodeNm === '') {
          _attr = '코드명은'
          this.$refs.addCodeValCodeNm.focus()
        }

        if (this.addCodeValCodeEngNm === null || this.addCodeValCodeEngNm === '') {
          _attr = '코드 영문명은'
          this.$refs.addCodeValCodeEngNm.focus()
        }

        if (this.addCodeValCodeVal === null || this.addCodeValCodeVal === '') {
          _attr = '코드 값은'
          this.$refs.addCodeValCodeVal.focus()
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
      } else if (status === 'codeValUpdate') {
        // console.log(this.updateCodeValCodeVal)
        if (this.updateCodeValCodeGrp === null || this.updateCodeValCodeGrp === '') {
          _attr = '코드 그룹명은'
          this.$refs.updateCodeValCodeGrp.focus()
        }

        if (this.updateCodeValCodeNm === null || this.updateCodeValCodeNm === '') {
          _attr = '코드명은'
          this.$refs.updateCodeValCodeNm.focus()
        }

        if (this.updateCodeValCodeEngNm === null || this.updateCodeValCodeEngNm === '') {
          _attr = '코드 영문명은'
          this.$refs.updateCodeValCodeEngNm.focus()
        }

        if (this.updateCodeValCodeVal === null || this.updateCodeValCodeVal === '') {
          _attr = '코드 값은'
          this.$refs.updateCodeValCodeVal.focus()
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

      if (_attr === null) {
        return true;
      }
    },
    addActiveDetail(name, index) {
      this.activeDetailTab = name;
    },
    addFormReset() {
      // 코드 등록 모달 초기화
      this.$refs.form.reset();
      this.addCodeModalStep = 1;
      this.addTerm_termNm = null;
      this.addTerm_termEngAbrvNm = null;
      this.addTerm_termDesc = null;
      this.addTerm_domainNm = null;
      this.addTerm_domainNmItems = [];
      this.addTerm_codeGrp = null;
      this.addTerm_chrgOrg = null;
      this.addTerm_commStndYn = 'Y';
      this.addTerm_magntdOrd = null;
      this.addTerm_reqSysCd = null;
      this.addTerm_wordList = [];
      this.addTerm_selected_word_list = [];
      this.addTerm_wordList = [];
    },
    addCodeValReset() {
      // 코드 값 등록 모달 초기화
      this.addCodeValCodeGrp = null,
        this.addCodeValCodeNm = null,
        this.addCodeValCodeEngNm = null,
        this.addCodeValCodeVal = null,
        this.addCodeValCodeValDesc = null
    },
    updateFormReset() {
      // 코드 수정 모달 초기화
      this.updateCodeModalStep = 1;
      this.updateTerm_id = null;
      this.updateTerm_termNm = null;
      this.updateTerm_termEngAbrvNm = null;
      this.updateTerm_termDesc = null;
      this.updateTerm_domainNm = null;
      this.updateTerm_domainNmItems = [];
      this.updateTerm_codeGrp = null;
      this.updateTerm_chrgOrg = null;
      this.updateTerm_commStndYn = null;
      this.updateTerm_magntdOrd = null;
      this.updateTerm_reqSysCd = null;
      this.updateTerm_wordList = [];
      this.updateTerm_selected_word_list = [];
    },
    updateCodeValReset() {
      // 코드 값 수정 모달 초기화
      this.updateCodeValId = null;
      this.updateCodeValCodeGrp = null;
      this.updateCodeValCodeNm = null;
      this.updateCodeValCodeEngNm = null;
      this.updateCodeValCodeVal = null;
      this.updateCodeValCodeValDesc = null;
    },
    checkSelectedWordStatus(partOfSpeech, word, id) {
      if ("NN" !== partOfSpeech &&
        "SL" !== partOfSpeech &&
        "SN" !== partOfSpeech &&
        "NF" !== partOfSpeech
      ) {
        // 명사가 아닐 때
        // console.log(_wordName, " 단어 아님")

        if (this.addCodeModalShow) {
          this.addTerm_wordListArr = [];
        } else if (this.updateCodeModalShow) {
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
            if (this.addCodeModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.addTerm_selected_word_list = [];
              this.addCodeModalStep = 1;
            } else if (this.updateCodeModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.updateTerm_selected_word_list = []
              this.updateCodeModalStep = 1;
            }
          }
        })

        return;
      } else if (("NN" === partOfSpeech || "SL" === partOfSpeech || "SN" === partOfSpeech || "NF" === partOfSpeech) && id === null) {
        // console.log(_wordName, " 단어 등록 해야 함")
        // 단어이지만 단어목록에 등록되지 않은 단어일 때
        if (this.addCodeModalShow) {
          this.addTerm_wordListArr = [];
        } else if (this.updateCodeModalShow) {
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

            if (this.addCodeModalShow) {
              // 용어 등록 모달 초기화
              this.addFormReset();
              // 확인 버튼 클릭 시 단어 페이지로 이동
              document.getElementById('nav_word').click();
              setTimeout(() => {
                document.getElementById('addWordBtn').click();
              }, 500);
            } else if (this.updateCodeModalShow) {
              // 확인 버튼 클릭 시 단어 페이지로 이동
              document.getElementById('nav_word').click();
              setTimeout(() => {
                document.getElementById('addWordBtn').click();
              }, 500);
            }
          } else {
            // 취소 버튼 클릭 시 이전 스텝으로 이동
            if (this.addCodeModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.addTerm_selected_word_list = [];
              this.addCodeModalStep = 1;
            } else if (this.updateCodeModalShow) {
              // 기존에 선택한 단어 목록 초기화
              this.updateTerm_selected_word_list = []
              this.updateCodeModalStep = 1;
            }
          }
        })
        return;
      } else {
        // 단어목록에 등록된 단어이며 명사일 때 단어
        // this.createTermEngAbrvNm();

        this.collectSelectedItems();
      }
    },
    collectSelectedItems() {
      let wordItems = [];

      if (this.addCodeModalShow) {
        wordItems = this.addTerm_selected_word_list;

      } else if (this.updateCodeModalShow) {
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

      if (this.addCodeModalShow) {
        // 용어 등록 시에 필요한 단어 구성 배열
        this.addTerm_wordList = this.createAddWordList(sortedArr);

      } else if (this.updateCodeModalShow) {
        // 용어 수정 시에 필요한 단어 구성 배열
        this.updateTerm_wordList = this.createAddWordList(sortedArr);
      }
    },
    getWordListByNm() {
      let _term_name = '';

      if (this.addCodeModalShow) {
        _term_name = this.addTerm_termNm;
      } else if (this.updateCodeModalShow) {
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

        if (this.addCodeModalShow) {
          this.addTerm_wordListArr = res.data;
        } else if (this.updateCodeModalShow) {
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
    addNextStep(step) {
      if (this.addTerm_termNm === null || this.addTerm_termNm === '') {
        this.$swal.fire({
          title: '코드명을 입력해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        this.$refs.addTerm_termNm.focus();
        return;
      }

      if (step === 1) {
        this.getWordListByNm();
        this.addCodeModalStep = 2;
      } else if (step === 2) {
        if (this.selectedWordName()) {
          this.addCodeModalStep = 3;
        }
      }
    },
    updateNextStep(step) {
      if (this.updateTerm_termNm === null || this.updateTerm_termNm === '') {
        this.$swal.fire({
          title: '코드명을 입력해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        this.$refs.updateTerm_termNm.focus();
        return;
      }

      if (step === 1) {
        this.getWordListByNm();
        this.updateCodeModalStep = 2;
      } else if (step === 2) {
        if (this.selectedWordName()) {
          this.updateCodeModalStep = 3;
        }
      }
    },
    selectedWordName() {
      if (this.addCodeModalShow) {
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

      } else if (this.updateCodeModalShow) {
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
    createAddWordList(arr) {
      // 새로운 용어 생성 시 필요한 this.addTerm_wordList의 배열을 생성한다.
      // 필요 데이터 termsId=null, wordId, wordNm, wordOrd(index)
      let sortedArr = arr;
      let createWordList = [];

      for (let i = 0; i < sortedArr.length; i++) {
        if (this.addCodeModalShow) {
          createWordList.push({
            termsId: null,
            wordId: sortedArr[i].id,
            wordNm: sortedArr[i].wordNm,
            wordEngAbrvNm: sortedArr[i].wordEngAbrvNm,
            domainClsfNm: sortedArr[i].domainClsfNm,
            wordOrd: i,
          })

        } else if (this.updateCodeModalShow) {
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
      // console.log(_domainClsfNm);
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
    addAllophSynmLst() {
      if (this.addCodeModalShow) {
        // 코드 등록 - 이음동의어목록 
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

        // console.log(this.adddTerm_allophSynmLst_arr)
      } else if (this.updateCodeModalShow) {
        // 코드드 수정 - 이음동의어목록
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
      if (this.addCodeModalShow) {
        this.addTerm_allophSynmLst_arr = this.addTerm_allophSynmLst_arr.filter(item => item.id !== id);
      } else if (this.updateCodeModalShow) {
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
    createWordToTerm(arr) {
      // console.log(arr)
      const validWords = arr.filter(word => word.length !== 0);

      let _str = '';
      for (let i = 0; i < validWords.length; i++) {
        _str += validWords[i].wordNm;
      }

      if (this.addCodeModalShow) {
        this.addTerm_user_selected_word = _str;
      } else if (this.updateCodeModalShow) {
        this.updateTerm_user_selected_word = _str;
      }
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
      // console.log(_createEngAbrvNm)

      // 용어 영문 약어명을 생성하여 바인드
      if (this.addCodeModalShow) {
        this.addTerm_termEngAbrvNm = _createEngAbrvNm;
      } else if (this.updateCodeModalShow) {
        this.updateTerm_termEngAbrvNm = _createEngAbrvNm;
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
    getCodeDataList() {
      // 코드 항목 관리에서 사용하는 데이터 리스트
      axios.get(this.$APIURL.base + "api/std/getCodeDataList").then(result => {
        let _data = result.data;

        this.codeDataItems = _data;

        // console 표시
        console.log("📃 CODE DATA LIST ↓↓↓")
        console.log(result.data);

      }).catch(error => {
        console.error(error);
      })
    }
  },
  created() {
    this.getCodeData();
    this.getCodeDataList();
    this.getSystemList();
    eventBus.$on('NOTICE', this.onUploadNotice);
  },
  beforeDestroy() {
    eventBus.$off('NOTICE', this.onUploadNotice);
  },
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

#code_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#code_table thead th:nth-child(1) {
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

#code_detail_table tbody tr:nth-child(1) td {
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

.codeValTable_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 48px;
  border-top: 1px solid #E8EAF6;
}

.pagination_wrap {
  position: relative;
  width: 100%;
}

#codeVal_table {
  height: calc(100% - 76px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#manageCodeVal_table {
  height: calc(100% - 115px);
  overflow-y: overlay;
  overflow-x: hidden;
}

.codeSearchApv {
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

.liStyle {
  border: 1px solid #C5CAE9;
  margin: 5px 0px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 6px;
}

.modalContents {
  height: 60vh;
  position: relative;
}

.modal_pagination_wrap {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
</style>