<template>
  <v-main>
    <!-- 검색과 버튼 영역 -->
    <v-sheet class="splitTopWrapper pt-4 pb-4" id="approvalWrap"
      v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
      <!-- 검색 -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]" class="searchWrapper">
        <v-row :style="{ alignItems: 'center', margin: '0px' }">
          <span :style="{ fontSize: '.875rem' }">검색 조건 &nbsp;&nbsp;</span>
          <v-checkbox class="checkboxStyle" hide-details v-model="statusListAllCheck" label="전체" color="ndColor"
            v-on:change="allSelectedBtnAction"></v-checkbox>
          <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="요청" color="ndColor"
            value="REQUESTED"></v-checkbox>
          <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="검토" color="ndColor"
            value="CHECKING"></v-checkbox>
          <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="승인" color="ndColor"
            value="APPROVED"></v-checkbox>
          <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="반려" color="ndColor"
            value="REJECTED"></v-checkbox>
        </v-row>
        <date-picker v-model="datetimeitem" type="datetime" valueType="format" range></date-picker>
        <!-- 검색 버튼 -->
        <v-btn class="gradient" title="검색" v-on:click="getApprovalData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', margin: '0 16px' }"><v-icon>search</v-icon></v-btn>
      </v-sheet>
      <!-- 수정 버튼 -->
      <v-sheet class="pr-4 pl-4"
        v-bind:style="[isMobile ? { width: '100%', display: 'flex', justifyContent: 'space-between' } : {}]">
        <v-btn :style="{ width: '200px' }" class="gradient" v-on:click="showModal('update')" title="승인 상태 변경"
          v-if=isAdmin>승인
          상태
          변경</v-btn>
      </v-sheet>
    </v-sheet>

    <!-- 승인 목록 -->
    <v-data-table id="approval_table" :headers="approvalHeaders" :items="approvalItems" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="key" show-select class="px-4 pb-3"
      v-model="selectedItem" :loading="loadTable" loading-text="Loading">
      <!-- 클릭 가능한 아이템 설정 : 요청항목명  -->
      <template v-slot:[`item.reqItemNm`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="bindDetail(item)">{{
          item.reqItemNm
        }}</span>
      </template>
      <!-- 데이터 없음 -->
      <template v-slot:no-data>
        <v-alert>
          데이터가 존재하지 않습니다.
        </v-alert>
      </template>
    </v-data-table>

    <v-sheet class="split_bottom_wrap">
      <!-- 페이지네이션 -->
      <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>

    <!-- term approval Modal -->
    <v-dialog max-width="800" v-model="termApprovalModalShow">
      <NdModal @hide="hideModal('termView')" :footer-submit="false" header-title="승인 요청 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="term_detail_table" :items="getDetailItem" hide-default-footer class="px-4 pb-3">
              <template v-slot:body="{ items }">
                <tbody>
                  <!-- 상세 테이블 왼쪽  -->
                  <tr v-for="header in termDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">
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
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- word approval Modal -->
    <v-dialog max-width="800" v-model="wordApprovalModalShow">
      <NdModal @hide="hideModal('wordView')" :footer-submit="false" header-title="승인 요청 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="word_detail_table" :items="getDetailItem" hide-default-footer class="px-4 pb-3">
              <template v-slot:body="{ items }">
                <tbody>
                  <!-- 상세 테이블 왼쪽  -->
                  <tr v-for="header in wordDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">
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
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- domain approval Modal -->
    <v-dialog max-width="800" v-model="domainApprovalModalShow">
      <NdModal @hide="hideModal('domainView')" :footer-submit="false" header-title="승인 요청 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="domain_detail_table" :items="getDetailItem" hide-default-footer class="px-4 pb-3">
              <template v-slot:body="{ items }">
                <tbody>
                  <!-- 상세 테이블 왼쪽  -->
                  <tr v-for="header in domainDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">
                      {{ header.text }}
                    </td>
                    <td v-for="item in items" :key="item.domainNm">
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
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- update approval Modal -->
    <v-dialog max-width="600" v-model="updateApprovalModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="승인 상태 변경"
        footer-hide-title="취소" footer-submit-title="수정" :style="{ height: '40vh' }">
        <template v-slot:body>
          <v-container fluid>
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">승인 상태</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="updateAprvStat" :items="aprvStatItems"
                  ref="updateAprvStat" :placeholder="'선택'" :rules="[v => !!v || '승인 상태는 필수입력 값입니다.']"
                  :menu-props="{ top: false, offsetY: true }"></v-select>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>승인 상태 변경 사유</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateAprvStatUpdtRsn" dense clearable color="ndColor"
                  v-on:keyup.enter="submitDialog('update')"></v-text-field>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
  </v-main>
</template>
  
<script>
import axios from "axios";
import NdModal from "./../views/modal/NdModal.vue"
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';

export default {
  name: 'MMApproval',
  components: {
    NdModal,
    DatePicker
  },
  props: ['isMobile', 'approvalStatus'],
  data: () => ({
    // 승인 상태 목록
    approvalItems: [],
    // 선택한 승인 정보
    selectedItem: [],
    // 승인 요청 아이템의 상세 항목
    getDetailItem: [],
    // 전체 선택 여부
    statusListAllCheck: true,
    // 대시보드에서 받아온 승인 상태
    addApprovalStatus: [],
    // 검색 조건 리스트
    // statusListArray: ['REQUESTED', 'CHECKING', 'APPROVED', 'REJECTED'],
    statusListArray: [],
    // 검색 조건 날짜/시간
    datetimeitem: [],
    // 테이블 로딩
    loadTable: true,
    // 승인 요청 - 용어 상세 정보 모달
    termApprovalModalShow: false,
    // 승인 요청 - 단어 상세 정보 모달
    wordApprovalModalShow: false,
    // 승인 요청 - 단어 상세 정보 모달
    domainApprovalModalShow: false,
    // 승인 상태 변경 모달
    updateApprovalModalShow: false,
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 승인 데이터의 수
    itemsPerPage: 20,
    // 테이블 상단 헤더
    approvalHeaders: [
      { text: '요청 항목명', align: 'center', sortable: false, value: 'reqItemNm' },
      { text: '영문명', align: 'center', sortable: false, value: 'reqItemEngNm' },
      { text: '요청 유형', align: 'center', sortable: false, value: 'reqTp' },
      { text: '승인 상태', align: 'center', sortable: false, value: 'aprvStat' },
      { text: '요청자ID', align: 'center', sortable: false, value: 'reqUserId' },
      { text: '요청생성일시', align: 'center', sortable: false, value: 'reqCretDt' },
      { text: '요청수정일시', align: 'center', sortable: false, value: 'reqUpdtDt' },
      { text: '승인자ID', align: 'center', sortable: false, value: 'aprvUserId' },
      { text: '승인상태수정일시', align: 'center', sortable: false, value: 'aprvStatUpdtDt' },
      { text: '승인상태수정사유', align: 'center', sortable: false, value: 'aprvStatUpdtRsn' },
    ],
    // 관리자 여부
    isAdmin: false,
    // 승인상태 변경 
    updateAprvStat: null,
    // 승인상태 리스트
    aprvStatItems: [
      { text: '요청', value: 'REQUESTED' },
      { text: '검토', value: 'CHECKING' },
      { text: '승인', value: 'APPROVED' },
      { text: '반려', value: 'REJECTED' },
    ],
    // 승인상태변경사유
    updateAprvStatUpdtRsn: null,
    // 용어 상세보기 헤더
    termDetaileHeaders: [
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
    // 단어 상세보기 헤더
    wordDetaileHeaders: [
      { text: '단어명', align: 'center', sortable: false, value: 'wordNm' },
      { text: '단어영문약어명', sortable: false, align: 'center', value: 'wordEngAbrvNm' },
      { text: '단어영문명', sortable: false, align: 'center', value: 'wordEngNm' },
      { text: '단어설명', sortable: false, align: 'center', value: 'wordDesc' },
      { text: '형식단어여부', sortable: false, align: 'center', value: 'wordClsfYn' },
      { text: '도메인분류명', sortable: false, align: 'center', value: 'domainClsfNm' },
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
    // 도메인 상세보기 헤더
    domainDetaileHeaders: [
      { text: '도메인명', align: 'center', sortable: false, value: 'domainNm' },
      { text: '도메인 그룹명', sortable: false, align: 'center', value: 'domainGrpNm' },
      { text: '도메인 분류명', sortable: false, align: 'center', value: 'domainClsfNm' },
      { text: '도메인 설명', sortable: false, align: 'center', value: 'domainDesc' },
      { text: '데이터 타입', sortable: false, align: 'center', value: 'dataType', width: '15%' },
      { text: '데이터 길이', sortable: false, align: 'center', value: 'dataLen' },
      { text: '데이터 소숫점 길이', sortable: false, align: 'center', value: 'dataDecimalLen', width: '15%' },
      { text: '데이터 단위', sortable: false, align: 'center', value: 'dataUnit', width: '15%' },
      { text: '저장형식', sortable: false, align: 'center', value: 'storFmt', width: '15%' },
      { text: '표현형식', sortable: false, align: 'center', value: 'exprFmtLst' },
      { text: '허용값', sortable: false, align: 'center', value: 'allowValLst' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
  }),
  watch: {
    approvalItems() {
      this.setListPage();
    },
    statusListArray() {
      // this.getApprovalData();
    },
    datetimeitem() {
      this.getApprovalData();
    },
    approvalStatus() {
      this.addApprovalStatus = this.approvalStatus;
    },
    addApprovalStatus() {
      this.statusListArray = this.addApprovalStatus;

      if (this.addApprovalStatus.length !== 0) {
        this.statusListAllCheck = false;
      } else {
        this.statusListAllCheck = true;
        this.statusListArray = ['REQUESTED', 'CHECKING', 'APPROVED', 'REJECTED'];
      }
    },
  },
  created() {
    // 데이터 피커 설정을 먼저 해야 검색 조건으로 테이블을 불러와서 바인드 함
    this.setDatePicker();
    // 접속자가 일반 사용자라면 버튼을 숨기고 관리자라면 보여야 함
    this.adminCheck();
    // 관리 - 승인 선택 시 승인 목록 data를 불러온다.
    this.getApprovalData();
    // 테이블 초기화
    this.resetTable();
    // 대시보드를 통해 들어온 데이터를 받아온다.
    this.addApprovalStatus = this.approvalStatus;
  },
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.approvalItems.length / this.itemsPerPage);
    },
    bindDetail(item) {
      if (item.reqItemNm === '-') {
        return;
      }

      this.selectedItem = [item];

      // console.log(item);

      // 용어, 도메인은 reqItemNm을 보내주고 단어는 중복 가능성이 있어 reqItemId 보내준다.
      let reqItemNm = item.reqItemNm;
      let reqItemId = item.reqItemId;
      
      switch (item.reqTp) {
        case '용어':
          this.getTermDetail(reqItemNm);
          break;
        case '단어':
          this.getWordDetail(reqItemId);
          break;
        case '도메인':
          this.getDomainDetail(reqItemNm);
          break;
      }

      // 모달 열기
      // this.showModal('view');
    },
    getTermDetail(item) {
      let _url = this.$APIURL.base + "api/std/getTermsInfoByNm";

      axios.get(_url, {
        params: {
          'termsNm': item,
        }
      }).then((res) => {
        // console.log(res.data)
        this.getDetailItem = res.data;

        this.showModal('termView');
      })
    },
    getWordDetail(item) {
      let _url = this.$APIURL.base + "api/std/getWordInfoById";

      axios.get(_url, {
        params: {
          'wordId': item,
        }
      }).then((res) => {
        // console.log(res.data)
        this.getDetailItem = res.data;

        this.showModal('wordView');
      })
    },
    getDomainDetail(item) {
      let _url = this.$APIURL.base + "api/std/getDomainInfoByNm";

      axios.get(_url, {
        params: {
          'domainNm': item,
        }
      }).then((res) => {
        // console.log(res.data)
        this.getDetailItem = res.data;

        this.showModal('domainView');
      })
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'termView') {
        this.termApprovalModalShow = true;
      } else if (value === 'wordView') {
        this.wordApprovalModalShow = true;
      } else if (value === 'domainView') {
        this.domainApprovalModalShow = true;
      } else if (value === 'update') {
        // 선택한 항목이 없을 경우
        if (this.selectedItem.length === 0) {
          this.$swal.fire({
            title: '승인 상태를 변경할 항목을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });

          return;
        }

        // 선택한 항목 중 삭제된 항목(reqItemNm값이 '-'인 항목)이 있을 경우 return
        for (let i = 0; i < this.selectedItem.length; i++) {
          if (this.selectedItem[i].reqItemNm === '-') {
            this.$swal.fire({
              title: '삭제된 항목은 승인 상태를 변경할 수 없습니다.',
              confirmButtonText: '확인',
              icon: 'error',
            });

            return;
          }
        }

        this.updateApprovalModalShow = true;
      }
    },
    hideModal(value) {
      if (value === 'termView') {
        this.termApprovalModalShow = false;
      } else if (value === 'wordView') {
        this.wordApprovalModalShow = false;
      } else if (value === 'domainView') {
        this.domainApprovalModalShow = false;
      } else if (value === 'update') {
        this.resetModal();
        this.updateApprovalModalShow = false;
      }
    },
    submitDialog(value) {
      if (value === 'termView') {
      } else if (value === 'wordView') {
      } else if (value === 'domainView') {
      } else {
        if (this.fieldcheck('update')) {
          this.updateApproval(this.updateAprvStat, this.updateAprvStatUpdtRsn);
        }
      }
    },
    resetModal() {
      this.updateAprvStat = null;
      this.updateAprvStatUpdtRsn = null;
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'view') {

      } else if (status === 'update') {
        if (this.updateAprvStat === null) {
          _attr = '승인상태는';
          this.$refs.updateAprvStat.focus()
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
    // updateModalInit() {
    //   this.updateAprvStat = this.selectedItem[0].aprvStat;
    //   this.updateAprvStatUpdtRsn = this.selectedItem[0].aprvStatUpdtRsn;
    // },
    resetTable() {
      this.statusListAllCheck = true;
      this.selectedItem = [];
      this.addApprovalStatus = [];
      this.statusListArray = [];
    },
    setDatePicker() {
      // 한달전 날짜부터 오늘 날짜까지
      // this.datetimeitem = [this.$formattedDate(this.$getMonthAgo()), this.$formattedDate(this.$getToday())];
      // 날짜가 없으면 모든 승인 리스트가 출력됨
      this.datetimeitem = [null, null];
    },
    getApprovalData() {
      try {
        let _fromDt = this.$dateToString(this.datetimeitem[0]);
        let _toDt = this.$dateToString(this.datetimeitem[1]);

        // 입력 데이터가 없으면 null
        if (_fromDt === '19700101090000') {
          _fromDt = null;
        }

        if (_toDt === '19700101090000') {
          _toDt = null;
        }

        let _getListData = {
          "from": _fromDt,
          "to": _toDt,
          "aprvUserId": null,
          "statusList": this.statusListArray,
          "limit": 0
        }
        // console.log(_getListData);

        axios.post(this.$APIURL.base + "api/std/getStdAprvStatList", _getListData)
          .then(res => {
            // console.log(res)

            if (res.status === 200) {
              // 승인상태 수정하여 바인드 필요
              for (let i = 0; i < res.data.length; i++) {
                // 승인 상태 값 변경
                let _aprvStat = res.data[i].aprvStat;

                if (_aprvStat === 0) {
                  res.data[i].aprvStat = "요청";
                } else if (_aprvStat === 1) {
                  res.data[i].aprvStat = "검토";
                } else if (_aprvStat === 2) {
                  res.data[i].aprvStat = "승인";
                } else if (_aprvStat === 3) {
                  res.data[i].aprvStat = "반려";
                }

                // 요청유형 값 변경
                let _reqTp = res.data[i].reqTp;

                if (_reqTp === 'DOMAIN') {
                  res.data[i].reqTp = "도메인";
                } else if (_reqTp === 'TERMS') {
                  res.data[i].reqTp = "용어";
                } else if (_reqTp === 'WORD') {
                  res.data[i].reqTp = "단어";
                }

                // 요청 항목명이 null이면 '-'로 변경하고 승인상태수정사유에 '삭제됨' 추가
                if (res.data[i].reqItemNm === null) {
                  res.data[i].reqItemNm = "-";
                  res.data[i].aprvStatUpdtRsn = "삭제됨";
                }
              }

              // 승인 테이블에는 유일하게 식별 가능한 key가 없으므로 클라이언트에서만 사용할 key를 생성하여 사용한다.
              for (let i = 0; i < res.data.length; i++) {
                res.data[i].key = i;
              }

              this.approvalItems = res.data;
              this.selectedItem = [];

              // console 표시
              // console.log("📃 Approval LIST ↓↓↓")
              // console.log(this.approvalItems);
            }

          }).catch(error => {
            this.$swal.fire({
              title: '승인 등록 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        // this.$swal.fire({
        //   title: '승인 목록 바인드 실패 - params 확인 필요',
        //   confirmButtonText: '확인',
        //   icon: 'error',
        // });
        console.log(error);
      }

      this.loadTable = false;
    },
    checkedSelected() {
      if (this.selectedItem === null || this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '수정할 항목을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      }
      return true;
    },
    updateApproval(aprvStat, aprvStatUpdtRsn) {
      try {
        let approvalDataArray = [];

        for (let i = 0; i < this.selectedItem.length; i++) {

          let _reqTp = '';

          if (this.selectedItem[i].reqTp === '도메인') {
            _reqTp = 'DOMAIN';
          }
          else if (this.selectedItem[i].reqTp === '용어') {
            _reqTp = 'TERMS';
          }
          else if (this.selectedItem[i].reqTp === '단어') {
            _reqTp = 'WORD';
          }

          // 승인상태 값 변경
          let _aprvStat = '';

          if (aprvStat === 'REQUESTED') {
            _aprvStat = 0;
          }
          else if (aprvStat === 'CHECKING') {
            _aprvStat = 1;
          }
          else if (aprvStat === 'APPROVED') {
            _aprvStat = 2;
          }
          else if (aprvStat === 'REJECTED') {
            _aprvStat = 3;
          }

          let _approvalData = {
            "reqTp": _reqTp,
            "reqItemId": this.selectedItem[i].reqItemId,
            "reqItemNm": this.selectedItem[i].reqItemNm,
            "aprvStat": _aprvStat,
            "reqSysCd": this.selectedItem[i].reqSysCd,
            "reqUserId": this.selectedItem[i].reqUserId,
            "aprvUserId": this.selectedItem[i].aprvUserId,
            "aprvdDt": this.selectedItem[i].aprvdDt,
            "aprvStatUpdtRsn": aprvStatUpdtRsn
          };
          approvalDataArray.push(_approvalData);
        }

        console.log(approvalDataArray);
        axios.post(this.$APIURL.base + "api/std/putStdAprvStat", approvalDataArray).then(result => {
          // console.log(result)

          if (result.data.resultCode === 200) {
            this.hideModal('update');

            this.$swal.fire({
              title: '승인 상태 변경이 완료되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            });

            this.getApprovalData();
          } else {
            this.$swal.fire({
              title: '승인 상태 변경 실패',
              text: result.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '승인 상태 변경 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        this.$swal.fire({
          title: '승인 상태 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    adminCheck() {
      const _userStatus = this.$loginStatusData.id;

      axios.get(this.$APIURL.base + "api/login/isAdmin", {
        params: { user: _userStatus }
      })
        .then(result => {

          this.isAdmin = result.data;
        })
        .catch(Error => {
          console.log(Error);
        });
    },
    allSelectedBtnAction() {
      // console.log(this.statusListAllCheck);

      if (this.statusListAllCheck) {
        this.statusListArray = ['REQUESTED', 'CHECKING', 'APPROVED', 'REJECTED']
      } else {
        this.statusListArray = [];
      }
    },
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
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

.searchWrapper {
  display: flex;
  align-items: center;
  align-content: center;
  justify-content: flex-start;
}

.btnWrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

#approval_table {
  height: calc(100% - 150px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#approval_table thead th:nth-child(1) {
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

.checkboxStyle {
  margin-top: 0px;
  padding-top: 0px;
  margin-right: 15px;
}

#term_detail_table tbody tr:nth-child(1) td,
#domain_detail_table tbody tr:nth-child(1) td,
#word_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}
</style>