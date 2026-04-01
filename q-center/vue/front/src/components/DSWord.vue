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
              <span :style="{ fontSize: '.875rem' }">단어명</span>
              <!-- 단어명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchWord" v-on:keyup.enter="getWordData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 단어영문약어명 입력 필드 -->
              <span :style="{ fontSize: '.875rem' }">단어영문약어명</span>
              <v-text-field class="pr-4 pl-4" v-model="searchEngWord" v-on:keyup.enter="getWordData"
                @input="searchEngWord = (searchEngWord || '').toUpperCase()"
                clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 승인 여부 추가 -->
              <v-checkbox class="wordSearchApv" v-model="searchApproval" label="승인 여부" color="ndColor"
                hide-details></v-checkbox>
              <v-btn class="gradient" title="검색" v-on:click="getWordData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>
          </v-sheet>
          <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-btn class="gradient" id="addWordBtn" v-on:click="showModal('add')" title="등록">등록</v-btn>
            <v-btn class="gradient" v-on:click="excelFileUpload" title="일괄 등록">일괄 등록</v-btn>
            <v-btn class="gradient" v-on:click="wordListDownload()" title="다운로드">다운로드</v-btn>
            <v-btn class="gradient" v-on:click="wordRemoveItem()" title="삭제">삭제</v-btn>
            <v-btn class="gradient" color="red lighten-4" v-on:click="wordBulkRemove()" title="일괄 삭제">일괄 삭제</v-btn>
            <input type="file" @change="readExcelFile" ref="file" id="inputUpload" :style="{ display: 'none' }"
              accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ wordItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
        <!-- 단어 목록 -->
        <v-data-table id="word_table" :headers="wordHeaders" :items="wordItems" :page.sync="page"
          :items-per-page="itemsPerPage" hide-default-footer item-key="wordEngAbrvNm" show-select class="px-4 pb-3"
          v-model="removeItems" :loading="loadTable" loading-text="잠시만 기다려주세요." @input="enterSelect()">
          <!-- 클릭 가능한 아이템 설정 : 단어명  -->
          <template v-slot:[`item.wordNm`]="{ item }">
            <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="showDetail(item)">{{
              item.wordNm
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
        <div class="split_bottom" v-show="selectedItem.length != 0">
          <v-sheet class="splitBottomWrapper">
            <!-- 타이틀 -->
            <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
              <span class="splitBottomSpan"
                :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                  detailWord
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
          <v-data-table id="word_detail_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
            <template v-slot:body="{ items }">
              <tbody>
                <!-- 상세 테이블 왼쪽  -->
                <tr v-for="header in detaileHeaders" :key="header.value">
                  <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '15%' }">
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
          </v-data-table>
        </div>
      </SplitArea>
    </Split>
    <!-- Add word Modal -->
    <v-dialog max-width="600" v-model="addWordModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="단어 등록"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-form ref="form">
              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">단어명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addWord_wordNm" ref="addWord_wordNm"
                    :rules="[() => !!addWord_wordNm || '단어명은 필수 입력값입니다.']" clearable required dense placeholder="가능"
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">단어 영문 약어명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addWord_wordEngAbrvNm" ref="addWord_wordEngAbrvNm"
                    :rules="[() => !!addWord_wordEngAbrvNm || '단어 영문 약어명은 필수 입력값입니다.']" clearable required dense
                    placeholder="PSBLTY" color="ndColor"
                    @input="addWord_wordEngAbrvNm = (addWord_wordEngAbrvNm || '').toUpperCase()"
                    @blur="checkEngAbrvDuplicate"></v-text-field>
                  <div v-if="addWord_engAbrvDuplicate" :style="{ color: '#d32f2f', fontSize: '0.75rem', marginTop: '-8px' }">
                    {{ addWord_engAbrvDuplicate }}
                  </div>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">단어 영문명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field ref="addWord_wordEngNm" v-model="addWord_wordEngNm"
                    :rules="[() => !!addWord_wordEngNm || '단어 영문명은 필수 입력값입니다.']" clearable required dense
                    placeholder="POSSIBILITY" color="ndColor"
                    @input="addWord_wordEngNm = (addWord_wordEngNm || '').toUpperCase()"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">단어 설명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-textarea clearable dense color="ndColor" rows="1" v-model="addWord_wordDesc" ref="addWord_wordDesc"
                    placeholder="可能. 할 수 있거나 될 수 있음"
                    :rules="[() => !!addWord_wordDesc || '단어 설명은 필수 입력값입니다.']"></v-textarea>
                </v-col>
              </v-row>

              <v-row>
                  <v-col cols="4">
                    <v-subheader>요청 시스템</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <treeselect v-model="addWord_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="addWord_reqSysCd" />
                  </v-col>
                </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>도메인 분류명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-autocomplete dense required color="ndColor" v-model="addWord_domainClsfNm" :placeholder="'선택'"
                    :items="domainClassificationItems" :menu-props="{ top: false, offsetY: true }">
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
                  <v-subheader>형식 단어 여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addWord_wordClsfYn" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                    <v-radio color="ndColor" label="N" value="N"></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>
              <!-- <hr> -->
              <v-row>
                <v-col cols="4">
                  <v-subheader>이음동의어 목록</v-subheader>
                </v-col>

                <v-col cols="8">
                  <v-col class="colInBtnWrap" v-for="addWord_allophSynm in addWord_allophSynmLst_arr"
                    :key="addWord_allophSynm.id">
                    <v-text-field :for="addWord_allophSynm.value" v-model="addWord_allophSynm.value" dense color="ndColor"
                      ref="addWord_allophSynmLst_arr" placeholder="" hide-details></v-text-field>
                    <v-btn class="gradient colInBtn" v-show="addWord_allophSynm.addBtnView"
                      v-on:click="addAllophSynmLst()" title="추가">추가</v-btn>
                    <v-btn class="colInBtn white--text" color="gray" v-show="addWord_allophSynm.removeBtnView"
                      v-on:click="removeAllophSynmLst(addWord_allophSynm.id)" title="삭제">삭제</v-btn>
                  </v-col>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>금칙어 목록</v-subheader>
                </v-col>

                <v-col cols="8">
                  <v-col class="colInBtnWrap" v-for="addWord_forbdnWord in addWord_forbdnWordLst_arr"
                    :key="addWord_forbdnWord.id">
                    <v-text-field :for="addWord_forbdnWord.value" v-model="addWord_forbdnWord.value" dense color="ndColor"
                      ref="addWord_forbdnWordLst_arr" placeholder="" hide-details></v-text-field>
                    <v-btn class="gradient colInBtn" v-show="addWord_forbdnWord.addBtnView"
                      v-on:click="addForbdnWordLst()" title="추가">추가</v-btn>
                    <v-btn class="colInBtn white--text" color="gray" v-show="addWord_forbdnWord.removeBtnView"
                      v-on:click="removeForbdnWordLst(addWord_forbdnWord.id)" title="삭제">삭제</v-btn>
                  </v-col>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>공통표준여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addWord_commStndYn" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                    <v-radio color="ndColor" label="N" value="N"></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>제정차수</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addWord_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
                </v-col>
              </v-row>

            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- update word Modal -->
    <v-dialog max-width="600" v-model="updateWordModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="단어 수정"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <v-container fluid>
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">단어명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateWord_wordNm" ref="updateWord_wordNm"
                  :rules="[() => !!updateWord_wordNm || '단어명은 필수 입력값입니다.']" clearable required dense placeholder="가능"
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">단어 영문 약어명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateWord_wordEngAbrvNm" ref="updateWord_wordEngAbrvNm"
                  :rules="[() => !!updateWord_wordEngAbrvNm || '단어 영문 약어명은 필수 입력값입니다.']" clearable required dense
                  placeholder="PSBLTY" color="ndColor"
                  @input="updateWord_wordEngAbrvNm = (updateWord_wordEngAbrvNm || '').toUpperCase()"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">단어 영문명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field ref="updateWord_wordEngNm" v-model="updateWord_wordEngNm"
                  :rules="[() => !!updateWord_wordEngNm || '단어 영문명은 필수 입력값입니다.']" clearable required dense
                  placeholder="POSSIBILITY" color="ndColor"
                  @input="updateWord_wordEngNm = (updateWord_wordEngNm || '').toUpperCase()"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">단어 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-textarea clearable dense color="ndColor" rows="1" v-model="updateWord_wordDesc"
                  ref="updateWord_wordDesc" placeholder="可能. 할 수 있거나 될 수 있음"
                  :rules="[() => !!updateWord_wordDesc || '단어 설명은 필수 입력값입니다.']"></v-textarea>
              </v-col>
            </v-row>

            <v-row>
                  <v-col cols="4">
                    <v-subheader>요청 시스템</v-subheader>
                  </v-col>
                  <v-col cols="8">
                    <treeselect v-model="updateWord_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="updateWord_reqSysCd" />
                  </v-col>
                </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>도메인 분류명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-autocomplete dense required color="ndColor" v-model="updateWord_domainClsfNm" :placeholder="'선택'"
                  :items="domainClassificationItems" :menu-props="{ top: false, offsetY: true }">
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
                <v-subheader>형식 단어 여부</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-radio-group v-model="updateWord_wordClsfYn" row mandatory dense hide-details>
                  <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                  <v-radio color="ndColor" label="N" value="N"></v-radio>
                </v-radio-group>
              </v-col>
            </v-row>
            <!-- <hr> -->
            <v-row>
              <v-col cols="4">
                <v-subheader>이음동의어 목록</v-subheader>
              </v-col>

              <v-col cols="8">
                <v-col class="colInBtnWrap" v-for="updateWord_allophSynm in updateWord_allophSynmLst_arr"
                  :key="updateWord_allophSynm.id">
                  <v-text-field :for="updateWord_allophSynm.value" v-model="updateWord_allophSynm.value" dense
                    color="ndColor" ref="updateWord_allophSynmLst_arr" placeholder="" hide-details></v-text-field>
                  <v-btn class="gradient colInBtn" v-show="updateWord_allophSynm.addBtnView"
                    v-on:click="addAllophSynmLst()" title="추가">추가</v-btn>
                  <v-btn class="colInBtn white--text" color="gray" v-show="updateWord_allophSynm.removeBtnView"
                    v-on:click="removeAllophSynmLst(updateWord_allophSynm.id)" title="삭제">삭제</v-btn>
                </v-col>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>금칙어 목록</v-subheader>
              </v-col>

              <v-col cols="8">
                <v-col class="colInBtnWrap" v-for="updateWord_forbdnWord in updateWord_forbdnWordLst_arr"
                  :key="updateWord_forbdnWord.id">
                  <v-text-field :for="updateWord_forbdnWord.value" v-model="updateWord_forbdnWord.value" dense
                    color="ndColor" ref="updateWord_forbdnWordLst_arr" placeholder="" hide-details></v-text-field>
                  <v-btn class="gradient colInBtn" v-show="updateWord_forbdnWord.addBtnView"
                    v-on:click="addForbdnWordLst()" title="추가">추가</v-btn>
                  <v-btn class="colInBtn white--text" color="gray" v-show="updateWord_forbdnWord.removeBtnView"
                    v-on:click="removeForbdnWordLst(updateWord_forbdnWord.id)" title="삭제">삭제</v-btn>
                </v-col>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>공통표준여부</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-radio-group v-model="updateWord_commStndYn" row mandatory dense hide-details>
                  <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                  <v-radio color="ndColor" label="N" value="N"></v-radio>
                </v-radio-group>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>제정차수</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateWord_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
              </v-col>
            </v-row>

          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- 일괄등록 Modal -->
    <v-dialog max-width="520" v-model="collectiveWordModalShow" persistent>
      <v-card>
        <v-card-title class="pb-2" :style="{ fontSize: '1rem', fontWeight: 'bold' }">
          <v-icon left color="ndColor">mdi-upload</v-icon>
          단어 일괄등록 진행
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
          <v-btn v-if="!isUploading" color="ndColor" text @click="collectiveWordModalShow = false">닫기</v-btn>
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
  name: 'DSWord',
  components: {
    NdModal,
    Treeselect
  },
  watch: {
    addWordModalShow(val) {
      val || this.hideModal('add')
    },
    updateWordModalShow(val) {
      val || this.hideModal('update')
    },
    wordItems() {
      this.setListPage();
    },
    itemsPerPage(val) {
      this.setListPage();
      localStorage.setItem('dsword_itemsPerPage', val);
    }
  },
  props: ['isMobile'],
  data: () => ({
    // 단어 목록 리스트
    wordItems: [],
    // 도메인 분류명 리스트
    domainClassificationItems: [],
    // 검색 단어
    searchWord: '',
    // 검색 단어영문약어명
    searchEngWord: '',
    // 검색 승인 여부
    searchApproval: true,
    // 등록 모달 보이기
    addWordModalShow: false,
    // 수정 모달 보이기
    updateWordModalShow: false,
    // 일괄 등록 진행 보여주기
    collectiveWordModalShow: false,
    // 일괄 등록 진행 상태
    isUploading: false,
    // 일괄 등록 로그
    uploadLogs: [],
    // 일괄 등록 실패 목록
    uploadFailList: [],
    // 일괄 등록 파일
    excelFile: null,
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 단어의 수
    itemsPerPage: parseInt(localStorage.getItem('dsword_itemsPerPage')) || 10,
    // 테이블 로딩
    loadTable: true,
    // 검색 이후 단어 리스트 다시보기 버튼 보이기
    // resetBtnShow: false,
    // 선택한 단어의 정보들
    selectedItem: [],
    // 선택한 단어 이름
    detailWord: null,
    // 등록 관련
    addWord_engAbrvDuplicate: '',
    addWord_wordNm: null,
    addWord_wordEngAbrvNm: null,
    addWord_wordEngNm: null,
    addWord_wordDesc: null,
    addWord_domainClsfNm: null,
    addWord_wordClsfYn: "N",
    addWord_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    addWord_allophSynmLst_count: 0,
    addWord_forbdnWordLst_arr: [{ id: 'forbdnWord_0', value: '', addBtnView: true, removeBtnView: false }],
    addWord_forbdnWordLst_count: 0,
    addWord_commStndYn: "N",
    addWord_magntdOrd: null,
    addWord_aprvYn: "N",
    addWord_reqSysCd: null,
    // 수정 관련
    updateWord_id: null,
    updateWord_wordNm: null,
    updateWord_wordEngAbrvNm: null,
    updateWord_wordEngNm: null,
    updateWord_wordDesc: null,
    updateWord_domainClsfNm: null,
    updateWord_wordClsfYn: "N",
    updateWord_allophSynmLst_arr: [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }],
    updateWord_allophSynmLst_count: 0,
    updateWord_forbdnWordLst_arr: [{ id: 'forbdnWord_0', value: '', addBtnView: true, removeBtnView: false }],
    updateWord_forbdnWordLst_count: 0,
    updateWord_commStndYn: "N",
    updateWord_magntdOrd: null,
    updateWord_aprvYn: "N",
    updateWord_aprvStatUpdtDt: null,
    updateWord_cretDt: null,
    updateWord_cretUserId: null,
    updateWord_updtDt: null,
    updateWord_updtUserId: null,
    updateWord_reqSysCd: null,
    // 삭제 관련
    removeItems: [],
    // 상단 테이블 헤더
    wordHeaders: [
      { text: '단어명', align: 'center', sortable: false, value: 'wordNm', width: '5%' },
      { text: '단어영문약어명', sortable: false, align: 'center', value: 'wordEngAbrvNm', width: '8%' },
      { text: '단어영문명', sortable: false, align: 'center', value: 'wordEngNm', width: '15%' },
      { text: '단어설명', sortable: false, align: 'center', value: 'wordDesc' },
      { text: '형식단어여부', sortable: false, align: 'center', value: 'wordClsfYn', width: '7%' },
      { text: '도메인분류명', sortable: false, align: 'center', value: 'domainClsfNm', width: '7%' },
    ],
    // 하단 테이블 헤더
    detaileHeaders: [
      { text: '단어명', align: 'center', sortable: false, value: 'wordNm' },
      { text: '단어영문약어명', sortable: false, align: 'center', value: 'wordEngAbrvNm' },
      { text: '단어영문명', sortable: false, align: 'center', value: 'wordEngNm' },
      { text: '단어설명', sortable: false, align: 'center', value: 'wordDesc' },
      { text: '형식단어여부', sortable: false, align: 'center', value: 'wordClsfYn' },
      { text: '도메인분류명', sortable: false, align: 'center', value: 'domainClsfNm' },
      { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '금칙어목록', sortable: false, align: 'center', value: 'forbdnWordLst' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
    // 승인 시스템에서 사용할 시스템 네임 리스트
    systemNameList: [],
  }),
  methods: {
    resetSearch() {
      this.searchWord = '';
      this.searchEngWord = '';
      this.searchApproval = true;
    },
    checkEngAbrvDuplicate() {
      var vm = this;
      var abrvNm = vm.addWord_wordEngAbrvNm;
      if (!abrvNm || abrvNm.trim() === '') {
        vm.addWord_engAbrvDuplicate = '';
        return;
      }
      axios.post(vm.$APIURL.base + 'api/std/getWordList', {
        'searchEngWord': abrvNm.trim(),
        'schAprvYn': ''
      }).then(function (res) {
        var found = false;
        if (res.data && res.data.length > 0) {
          for (var i = 0; i < res.data.length; i++) {
            if (res.data[i].wordEngAbrvNm === abrvNm.trim()) {
              found = true;
              break;
            }
          }
        }
        if (found) {
          vm.addWord_engAbrvDuplicate = '이미 동일한 영문약어명(' + abrvNm.trim() + ')이 등록되어 있습니다.';
        } else {
          vm.addWord_engAbrvDuplicate = '';
        }
      }).catch(function () {
        vm.addWord_engAbrvDuplicate = '';
      });
    },
    downloadFailList() {
      if (this.uploadFailList.length === 0) return;
      var csvContent = '\uFEFF단어명,실패 사유\n';
      for (var i = 0; i < this.uploadFailList.length; i++) {
        var row = this.uploadFailList[i];
        var wordNm = (row.wordNm || '').replace(/"/g, '""');
        var reason = (row.reason || '').replace(/"/g, '""');
        csvContent += '"' + wordNm + '","' + reason + '"\n';
      }
      var blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      var url = window.URL.createObjectURL(blob);
      var link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', '단어_일괄등록_실패목록_' + this.$getToday() + '.csv');
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(url);
      link.remove();
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
    addAllophSynmLst() {
      if (this.addWordModalShow) {
        // 단어 등록 - 이음동의어목록 
        let _dataLength = this.addWord_allophSynmLst_arr.length;

        // 이음동의어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.addWord_allophSynmLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '이음동의어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.addWord_allophSynmLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        // 이음동의어 목록 배열 생성
        this.addWord_allophSynmLst_arr.push({
          id: `alloph_${++this.addWord_allophSynmLst_count}`,
          value: ''
        })

        // 이음동의어 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.addWord_allophSynmLst_arr.forEach((item, index) => {
          if (index === this.addWord_allophSynmLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      } else if (this.updateWordModalShow) {
        // 단어 수정 - 이음동의어목록
        let _dataLength = this.updateWord_allophSynmLst_arr.length;

        // 이음동의어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.updateWord_allophSynmLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '이음동의어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.updateWord_allophSynmLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        this.updateWord_allophSynmLst_arr.push({
          id: `alloph_${++this.updateWord_allophSynmLst_count}`,
          value: ''
        })

        this.updateWord_allophSynmLst_arr.forEach((item, index) => {
          if (index === this.updateWord_allophSynmLst_arr.length - 1) {
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
      if (this.addWordModalShow) {
        this.addWord_allophSynmLst_arr = this.addWord_allophSynmLst_arr.filter(item => item.id !== id);
      } else if (this.updateWordModalShow) {
        this.updateWord_allophSynmLst_arr = this.updateWord_allophSynmLst_arr.filter(item => item.id !== id);
      }
    },
    resetAddWordTextfield() {
      this.addWord_allophSynmLst_arr = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
      this.addWord_allophSynmLst_count = 0;
      this.addWord_forbdnWordLst_arr = [{ id: 'forbdnWord_0', value: '', addBtnView: true, removeBtnView: false }];
      this.addWord_forbdnWordLst_count = 0;
      this.addWord_reqSysCd = null;
      this.addWord_engAbrvDuplicate = '';
    },
    addForbdnWordLst() {
      if (this.addWordModalShow) {
        // 단어 등록 - 금칙어목록
        let _dataLength = this.addWord_forbdnWordLst_arr.length;

        // 금칙어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.addWord_forbdnWordLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '금칙어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.addWord_forbdnWordLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        // 금칙어 목록 배열 생성
        this.addWord_forbdnWordLst_arr.push({
          id: `alloph_${++this.addWord_forbdnWordLst_count}`,
          value: ''
        })

        // 금칙어 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.addWord_forbdnWordLst_arr.forEach((item, index) => {
          if (index === this.addWord_forbdnWordLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      } else if (this.updateWordModalShow) {
        // 단어 수정 - 금칙어목록

        let _dataLength = this.updateWord_forbdnWordLst_arr.length;

        // 금칙어 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.updateWord_forbdnWordLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '금칙어를 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.updateWord_forbdnWordLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        this.updateWord_forbdnWordLst_arr.push({
          id: `alloph_${++this.updateWord_forbdnWordLst_count}`,
          value: ''
        })

        // 금칙어 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.updateWord_forbdnWordLst_arr.forEach((item, index) => {
          if (index === this.updateWord_forbdnWordLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      }
    },
    removeForbdnWordLst(id) {
      if (this.addWordModalShow) {
        this.addWord_forbdnWordLst_arr = this.addWord_forbdnWordLst_arr.filter(item => item.id !== id);
      } else if (this.updateWordModalShow) {
        this.updateWord_forbdnWordLst_arr = this.updateWord_forbdnWordLst_arr.filter(item => item.id !== id);
      }
    },
    resetUpdateWordTextfield() {
      this.updateWord_allophSynmLst_arr = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
      this.updateWord_allophSynmLst_count = 0;
      this.updateWord_forbdnWordLst_arr = [{ id: 'forbdnWord_0', value: '', addBtnView: true, removeBtnView: false }];
      this.updateWord_forbdnWordLst_count = 0;
      this.updateWord_reqSysCd = null;
    },
    readExcelFile(event) {
      // 단어 일괄 등록에서 사용하는 function
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
      this.collectiveWordModalShow = true;

      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getWordData();
        }
      }, 60000);

      const _url = this.$APIURL.base + "api/std/uploadWords";
      const formData = new FormData();
      formData.append('file', this.excelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      // input 초기화
      document.getElementById('inputUpload').value = '';
    },
    onUploadNotice(msg) {
      if (!this.collectiveWordModalShow) return;
      if (!msg.data || !msg.data.startsWith('[단어]')) return;
      const level = msg.noticeType === 'ERROR' ? 'ERROR' : 'INFO';
      this._addUploadLog(level, msg.data);
      // 실패 항목 수집
      if (level === 'ERROR' && msg.data.includes('실패:')) {
        var failText = msg.data.replace('[단어] 실패: ', '');
        var wordMatch = failText.match(/^단어\(([^)]*)\):\s*(.*)/);
        if (wordMatch) {
          this.uploadFailList.push({ wordNm: wordMatch[1], reason: wordMatch[2] });
        } else {
          this.uploadFailList.push({ wordNm: '-', reason: failText });
        }
      }
      if (msg.data.includes('완료 -')) {
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
        this.getWordData();
        const summary = msg.data.replace('[단어] ', '');
        const failMatch = summary.match(/실패:\s*(\d+)건/);
        const failCount = failMatch ? parseInt(failMatch[1]) : 0;
        this.$swal.fire({
          title: '단어 일괄등록 완료',
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
    showDetail(item) {
      // 단어명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 선택한 단어 이름을 타이틀에 보이기 위해 추가함
      this.detailWord = item.wordNm;
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
    },
    addModalOpenSetWordNm() {
      // 모달 오픈 시 검색어에 문자열이 있을 경우 단어명에 자동으로 입력
      if (this.searchWord !== '') {
        this.addWord_wordNm = this.searchWord;
      }
    },
    resetDetail() {
      // 선택한 단어 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.detailWord = null;
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchWord = ''
    },
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.wordItems.length / this.itemsPerPage);
    },
    enterSelect() {
      // 단어명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeItems.length === 0) {
        this.selectedItem = [];
      }
    },
    getWordData() {
      this.loadTable = true;
      // 단어 리스트 불러오기
      try {
        let schNm = null;

        if (this.searchWord !== '') {
          schNm = this.searchWord
        }

        let searchEngWord = null;

        if (this.searchEngWord !== '') {
          searchEngWord = this.searchEngWord
        }

        let schAprvYn = ''
        if (this.searchApproval === true) {
          schAprvYn = 'Y'
        } else {
          schAprvYn = 'N'
        }

        let _url = this.$APIURL.base + "api/std/getWordList";

        axios
          .post(
            _url,
            {
              'schNm': schNm,
              'searchEngWord': searchEngWord,
              'schAprvYn': schAprvYn
            }).then(result => {
              let _data = result.data;

              // console.log(_data);

              let _new_arr = [];

              // 배열로 이루어진 data를 변환
              for (let i = 0; i < _data.length; i++) {

                let _new_obj = {
                  'id': _data[i].id,
                  'wordNm': _data[i].wordNm,
                  'wordEngAbrvNm': _data[i].wordEngAbrvNm,
                  'wordEngNm': _data[i].wordEngNm,
                  'wordDesc': _data[i].wordDesc,
                  'wordClsfYn': _data[i].wordClsfYn,
                  'domainClsfNm': _data[i].domainClsfNm,
                  'commStndYn': _data[i].commStndYn,
                  'magntdOrd': _data[i].magntdOrd,
                  'aprvYn': _data[i].aprvYn,
                  'aprvStatUpdtDt': _data[i].aprvStatUpdtDt,
                  'cretDt': _data[i].cretDt,
                  'cretUserId': _data[i].cretUserId,
                  'updtDt': _data[i].updtDt,
                  'updtUserId': _data[i].updtUserId,
                  'reqSysCd':_data[i].reqSysCd,
                  'reqSysNm':_data[i].reqSysNm
                }

                if (_data[i].allophSynmLst !== null && _data[i].allophSynmLst.length !== 0) {
                  let _arr = new Array();

                  for (let n = 0; n < _data[i].allophSynmLst.length; n++) {
                    _arr.push(_data[i].allophSynmLst[n])
                  }

                  _new_obj.allophSynmLst = _arr;
                }

                if (_data[i].forbdnWordLst !== null && _data[i].forbdnWordLst.length !== 0) {
                  let _arr = new Array();

                  for (let n = 0; n < _data[i].forbdnWordLst.length; n++) {
                    _arr.push(_data[i].forbdnWordLst[n])
                  }

                  _new_obj.forbdnWordLst = _arr;
                }
                _new_arr.push(_new_obj);

              }

              // 테이블 생성하는 목록 Data에 전달
              this.wordItems = _new_arr;

              // console 표시
              console.log("📃 WORD LIST ↓↓↓")
              console.log(_new_arr);

              this.loadTable = false;
            })
          .catch(error => {
            this.$swal.fire({
              title: '단어 목록 바인드 실패 - API 확인 필요',
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
    excelFileUpload() {
      // 일괄 등록 버튼 클릭
      let fileUpload = document.getElementById('inputUpload')
      if (fileUpload != null) {
        fileUpload.click()
      }
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        // 도메인 분류 목록 가져오기
        this.getDomainClassificationData();
        this.addWordModalShow = true;
        this.addModalOpenSetWordNm();
      } else if (value === 'update') {
        this.getDomainClassificationData();
        this.updateModalInit();
        this.updateWordModalShow = true;
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addWordModalShow = false;
        this.$refs.form.reset();
        this.resetAddWordTextfield();
      } else if (value === 'update') {
        this.updateWordModalShow = false;
        this.resetUpdateWordTextfield();
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createWord();
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateWord();
        }
      }
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'add') {
        if (this.addWord_wordNm === null) {
          _attr = '단어명은';
          this.$refs.addWord_wordNm.focus()
        } else if (this.addWord_wordEngAbrvNm === null) {
          _attr = '단어 영문 약어명은';
          this.$refs.addWord_wordEngAbrvNm.focus()
        } else if (this.addWord_wordEngNm === null) {
          _attr = '단어 영문명은'
          this.$refs.addWord_wordEngNm.focus()
        } else if (this.addWord_wordDesc === null) {
          _attr = '단어 설명은'
          this.$refs.addWord_wordDesc.focus()
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
        if (this.updateWord_wordNm === null) {
          _attr = '단어명은';
          this.$refs.upateWord_wordNm.focus()
        } else if (this.updateWord_wordEngAbrvNm === null) {
          _attr = '단어 영문 약어명은';
          this.$refs.updateWord_wordEngAbrvNm.focus()
        } else if (this.updateWord_wordEngNm === null) {
          _attr = '단어 영문명은'
          this.$refs.updateWord_wordEngNm.focus()
        } else if (this.updateWord_wordDesc === null) {
          _attr = '단어 설명은'
          this.$refs.updateWord_wordDesc.focus()
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
    createWord() {
      try {
        // 이음동의어 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_allophSynmLst = this.addWord_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');
        // 금칙어목록 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_forbdnWordLst = this.addWord_forbdnWordLst_arr.map(obj => obj.value).filter(val => val !== '');

        let wordData = {
          'wordNm': this.addWord_wordNm,
          'wordEngAbrvNm': this.addWord_wordEngAbrvNm,
          'wordEngNm': this.addWord_wordEngNm,
          'wordDesc': this.addWord_wordDesc,
          'wordClsfYn': this.addWord_wordClsfYn,
          'domainClsfNm': this.addWord_domainClsfNm,
          "allophSynmLst": arr_allophSynmLst,
          "forbdnWordLst": arr_forbdnWordLst,
          'commStndYn': this.addWord_commStndYn,
          'magntdOrd': this.addWord_magntdOrd,
          'reqSysCd': this.addWord_reqSysCd,
        };

        // console.log(wordData);

        axios.post(this.$APIURL.base + "api/std/createWord", wordData)
          .then(res => {
            console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('add');

              if (res.data.resultMessage) {
                // 유사어 경고가 있는 경우
                this.$swal.fire({
                  title: '단어가 등록되었습니다.',
                  text: res.data.resultMessage,
                  icon: 'warning',
                  confirmButtonText: '확인',
                });
              } else {
                this.$swal.fire({
                  title: '새로운 단어가 등록되었습니다.',
                  icon: 'success',
                  showConfirmButton: false,
                  timer: 1500
                });
              }

              this.getWordData();
            } else {
              this.$swal.fire({
                title: '단어 등록 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '단어 등록 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        this.$swal.fire({
          title: '단어 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateWord() {
      try {
        // 이음동의어 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_allophSynmLst = this.updateWord_allophSynmLst_arr.map(obj => obj.value).filter(val => val !== '');
        // 금칙어목록 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
        let arr_forbdnWordLst = this.updateWord_forbdnWordLst_arr.map(obj => obj.value).filter(val => val !== '');

        let wordData = {
          'id': this.updateWord_id,
          'wordNm': this.updateWord_wordNm,
          'wordEngAbrvNm': this.updateWord_wordEngAbrvNm,
          'wordEngNm': this.updateWord_wordEngNm,
          'wordDesc': this.updateWord_wordDesc,
          'wordClsfYn': this.updateWord_wordClsfYn,
          'domainClsfNm': this.updateWord_domainClsfNm,
          "allophSynmLst": arr_allophSynmLst,
          "forbdnWordLst": arr_forbdnWordLst,
          'commStndYn': this.updateWord_commStndYn,
          'magntdOrd': this.updateWord_magntdOrd,
          'reqSysCd': this.updateWord_reqSysCd,
        };

        // console.log('updateWord');
        // console.log(wordData);

        axios.post(this.$APIURL.base + "api/std/updateWord", wordData)
          .then(res => {
            // console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('update');

              this.$swal.fire({
                title: '단어가 수정되었습니다.',
                // confirmButtonText: '확인',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });

              this.resetWordList();
            } else {
              this.$swal.fire({
                title: '단어 수정 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '단어 수정 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        this.$swal.fire({
          title: '단어 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    wordListDownload() {
      let _keyWord = this.searchWord.length !== 0 ? this.searchWord : null;

      try {
        axios.get(this.$APIURL.base + "api/std/downloadWords",
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

            link.setAttribute("download", `단어사전_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '단어 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('단어 다운로드 실패 :', error);
      }
    },
    resetWordList() {
      // 단어 목록 다시 불러오기
      this.getWordData();
      this.searchWord = '';
      // this.resetBtnShow = false;
      this.resetDetail();
    },
    // 단어 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      let allophSynmLst_data = [];
      let forbdnWordLst_data = [];

      if (this.selectedItem[0].allophSynmLst !== undefined) {
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
        this.updateWord_allophSynmLst_count = this.selectedItem[0].allophSynmLst.length - 1;
      } else {
        allophSynmLst_data = [{ id: 'alloph_0', value: '', addBtnView: true, removeBtnView: false }];
        this.updateWord_allophSynmLst_count = 0;
      }

      if (this.selectedItem[0].forbdnWordLst !== undefined) {
        for (let i = 0; i < this.selectedItem[0].forbdnWordLst.length; i++) {
          // 마지막 배열은 add버튼을 보여주고 나머지는 remove버튼을 보여준다.
          if (i === this.selectedItem[0].forbdnWordLst.length - 1) {
            forbdnWordLst_data.push({ id: 'forbdn_' + i, value: this.selectedItem[0].forbdnWordLst[i].trim(), addBtnView: true, removeBtnView: false });
          } else {
            forbdnWordLst_data.push({ id: 'forbdn_' + i, value: this.selectedItem[0].forbdnWordLst[i].trim(), addBtnView: false, removeBtnView: true });
          }
        }
        this.updateWord_forbdnWordLst_count = this.selectedItem[0].forbdnWordLst.length - 1;
      } else {
        forbdnWordLst_data = [{ id: 'forbdn_0', value: '', addBtnView: true, removeBtnView: false }];
        this.updateWord_forbdnWordLst_count = 0;
      }

      this.updateWord_id = this.selectedItem[0].id;
      this.updateWord_wordNm = this.selectedItem[0].wordNm;
      this.updateWord_wordEngAbrvNm = this.selectedItem[0].wordEngAbrvNm;
      this.updateWord_wordEngNm = this.selectedItem[0].wordEngNm;
      this.updateWord_wordDesc = this.selectedItem[0].wordDesc;
      this.updateWord_reqSysCd = this.selectedItem[0].reqSysCd;
      this.updateWord_domainClsfNm = this.selectedItem[0].domainClsfNm;
      this.updateWord_wordClsfYn = this.selectedItem[0].wordClsfYn;
      this.updateWord_allophSynmLst_arr = allophSynmLst_data;
      this.updateWord_forbdnWordLst_arr = forbdnWordLst_data;
      this.updateWord_commStndYn = this.selectedItem[0].commStndYn;
      this.updateWord_magntdOrd = this.selectedItem[0].magntdOrd;
      this.updateWord_aprvYn = this.selectedItem[0].aprvYn;
      this.updateWord_aprvStatUpdtDt = this.selectedItem[0].aprvStatUpdtDt;
      this.updateWord_cretDt = this.selectedItem[0].cretDt;
      this.updateWord_cretUserId = this.selectedItem[0].cretUserId;
      this.updateWord_updtDt = this.selectedItem[0].updtDt;
      this.updateWord_updtUserId = this.selectedItem[0].updtUserId;
    },
    wordRemoveItem() {
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 단어를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      let removeName = '';

      for (let i = 0; i < this.removeItems.length; i++) {
        if (i === 0) {
          removeName += this.removeItems[i].wordNm;
        } else {
          removeName += ', ' + this.removeItems[i].wordNm;
        }
      }

      this.$swal.fire({
        title: '정말로 단어를 삭제할까요?',
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
            axios.post(this.$APIURL.base + "api/std/deleteWords", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '단어가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getWordData();
                  this.resetDetail();
                } else {
                  this.$swal.fire({
                    title: '단어 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '단어 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '단어 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    wordBulkRemove() {
      if (this.wordItems.length === 0) {
        this.$swal.fire({ title: '삭제할 단어가 없습니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.$swal.fire({
        title: `조회된 단어 ${this.wordItems.length}건을 모두 삭제할까요?`,
        text: '이 작업은 되돌릴 수 없습니다.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#d32f2f',
        cancelButtonColor: '#909090',
        confirmButtonText: '일괄 삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {
          const removeItemArr = this.wordItems.map(item => ({ id: item.id }));
          axios.post(this.$APIURL.base + 'api/std/deleteWords', removeItemArr)
            .then(res => {
              if (res.data.resultCode === 200) {
                this.$swal.fire({ title: `단어 ${removeItemArr.length}건이 삭제되었습니다.`, icon: 'success', showConfirmButton: false, timer: 1500 });
                this.getWordData();
                this.resetDetail();
              } else {
                this.$swal.fire({ title: '단어 일괄 삭제 실패', text: res.data.resultMessage, confirmButtonText: '확인', icon: 'error' });
              }
            }).catch(() => {
              this.$swal.fire({ title: '단어 일괄 삭제 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
            });
        }
      });
    },
    getDomainClassificationData() {
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainClassificationList").then(result => {
          let _data = result.data;

          // console 표시
          console.log("📃 Domain Classification LIST ↓↓↓")
          console.log(_data);

          // 도메인 분류명 배열에 담기
          for (let i = 0; i < _data.length; i++) {
            this.domainClassificationItems.push(_data[i].domainClsfNm);
          }

        }).catch(error => {
          console.error(error);
        })
      } catch (error) {
        console.error(error);
      }
    },
  },
  created() {
    this.getWordData();
    this.getSystemList();
    eventBus.$on('NOTICE', this.onUploadNotice);
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

.splitBottomWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
}

#word_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

.split_bottom {
  overflow: hidden;
  position: relative;
  height: 100%;
  background: #ffffff;
}

#word_detail_table {
  height: calc(100% - 64px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#word_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

#word_table thead th:nth-child(1) {
  width: 58px !important;
  min-width: 58px !important;
  max-width: 58px !important;
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

.splitBottomSpanWrapper {
  width: 60%;
  display: flex;
  font-size: 1.2rem;
}

.splitBottomSpan {
  display: inline-block;
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

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}
</style>