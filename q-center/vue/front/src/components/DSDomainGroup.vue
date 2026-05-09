<template>
  <v-main>
    <!-- 검색 + 버튼 영역 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '6px' }">
        <span class="filterLabel">도메인 그룹명</span>
        <v-text-field v-model="searchGrpNm" @click:clear="searchGrpNm=''" clearable
          clear-icon="mdi-close-circle" type="text" color="ndColor"
          single-line dense outlined hide-details class="filterInput" :style="{ width: '200px' }">
        </v-text-field>
        <v-spacer></v-spacer>
        <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
        <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn>
        <v-btn class="gradient" v-on:click="domainGroupRemoveItem()" title="삭제">삭제</v-btn>
        <!-- 86번 #11 — 엑셀 드롭다운 (업로드/양식/다운로드) -->
        <v-menu offset-y>
          <template v-slot:activator="{ on, attrs }">
            <v-btn class="gradient" v-bind="attrs" v-on="on">
              <v-icon small left>mdi-file-excel</v-icon>엑셀
              <v-icon small right>mdi-menu-down</v-icon>
            </v-btn>
          </template>
          <v-list dense>
            <v-list-item @click="domainGroupExcelFileUpload()">
              <v-list-item-icon><v-icon small>mdi-upload</v-icon></v-list-item-icon>
              <v-list-item-title>엑셀 업로드</v-list-item-title>
            </v-list-item>
            <v-list-item @click="domainGroupTemplateDownload()">
              <v-list-item-icon><v-icon small>mdi-file-download-outline</v-icon></v-list-item-icon>
              <v-list-item-title>양식 다운로드</v-list-item-title>
            </v-list-item>
            <v-list-item @click="domainGroupListDownload()">
              <v-list-item-icon><v-icon small>mdi-download</v-icon></v-list-item-icon>
              <v-list-item-title>데이터 다운로드</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <input type="file" id="inputDomainGroupUpload" ref="file" accept=".xlsx,.xls"
          @change="readExcelFile($event)" :style="{ display: 'none' }" />
      </v-row>
    </v-sheet>

    <!-- 일괄등록 Modal -->
    <v-dialog max-width="520" v-model="collectiveModalShow" persistent>
      <v-card>
        <v-card-title class="pb-2" :style="{ fontSize: '1rem', fontWeight: 'bold' }">
          <v-icon left color="ndColor">mdi-upload</v-icon>
          도메인 그룹 일괄등록 진행
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
          <v-btn v-if="!isUploading" color="ndColor" text @click="collectiveModalShow = false">닫기</v-btn>
          <span v-else :style="{ fontSize: '0.8rem', color: '#999', paddingRight: '12px' }">완료될 때까지 기다려주세요...</span>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-sheet class="tableSpt">
      <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
      <v-sheet>
        <span class="ndColor--text">총 {{ domainGroupItems.length }}건</span>
      </v-sheet>
      <v-sheet>
        <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
          hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>
    <v-divider></v-divider>
    <!-- 도메인 그룹 목록 -->
    <v-data-table id="domainGroup_table" :headers="domainGroupHeaders" :items="domainGroupItems" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="domainGrpNm" show-select class="px-4 pb-3"
      v-model="selectedItem">
      <!-- 클릭 가능한 아이템 설정 : 도메인그룹명  -->
      <template v-slot:[`item.domainGrpNm`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="bindDetail(item)">{{
          item.domainGrpNm
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

    <!-- add domainGroup Modal -->
    <v-dialog max-width="600" v-model="addDomainGroupModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="도메인 그룹 등록"
        footer-hide-title="취소" footer-submit-title="등록" :style="{ height: '40vh' }">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">
              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 그룹명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomainGroup_domainGrpNm" ref="addDomainGroup_domainGrpNm"
                    :rules="[() => !!addDomainGroup_domainGrpNm || '도메인 그룹명은 필수 입력값입니다.']" clearable required dense
                    placeholder="금액" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>공통표준여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addDomainGroup_commStndYn" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="Y" value="Y"></v-radio>
                    <v-radio color="ndColor" label="N" value="N" checked></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>
            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- update domainGroup Modal -->
    <v-dialog max-width="600" v-model="updateDomainGroupModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="도메인 그룹 수정"
        footer-hide-title="취소" footer-submit-title="수정" :style="{ height: '40vh' }">
        <template v-slot:body>
          <v-container fluid>
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">도메인 그룹명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomainGroup_domainGrpNm" ref="updateDomainGroup_domainGrpNm"
                  :rules="[() => !!updateDomainGroup_domainGrpNm || '도메인 그룹명은 필수 입력값입니다.']" clearable required dense
                  placeholder="금액" color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>공통표준여부</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-radio-group v-model="updateDomainGroup_commStndYn" row mandatory dense hide-details>
                  <v-radio color="ndColor" label="Y" value="Y"></v-radio>
                  <v-radio color="ndColor" label="N" value="N" checked></v-radio>
                </v-radio-group>
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
import { eventBus } from '../eventBus';

export default {
  name: 'DSDomainGroup',
  components: {
    NdModal
  },
  props: ['isMobile'],
  data: () => ({
    // 도메인 그룹 목록 리스트
    domainGroupAllItems: [],
    searchGrpNm: '',
    // 삭제 관련
    removeItems: [],
    // 선택한 도메인 그룹의 정보들
    selectedItem: [],
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 도메인 그룹의 수
    itemsPerPage: 20,
    // 도메인 그룹 등록 모달 보이기
    addDomainGroupModalShow: false,
    // 도메인 그룹 수정 모달 보이기
    updateDomainGroupModalShow: false,
    // 등록 관련
    addDomainGroup_domainGrpNm: null,
    addDomainGroup_commStndYn: "N",
    // 수정 관련
    updateDomainGroup_id: null,
    updateDomainGroup_domainGrpNm: null,
    updateDomainGroup_commStndYn: "N",
    // 상단 테이블 헤더
    domainGroupHeaders: [
      { text: '도메인 그룹명', align: 'center', sortable: false, value: 'domainGrpNm' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
    // 일괄등록 관련
    collectiveModalShow: false,
    isUploading: false,
    uploadLogs: [],
    excelFile: null,
    _uploadTimer: null,
  }),
  computed: {
    domainGroupItems() {
      if (!this.searchGrpNm) return this.domainGroupAllItems;
      return this.domainGroupAllItems.filter(item =>
        (item.domainGrpNm || '').includes(this.searchGrpNm)
      );
    },
  },
  watch: {
    addDomainGroupModalShow(val) {
      val || this.hideModal('add')
    },
    updateDomainGroupModalShow(val) {
      val || this.hideModal('update')
    },
    domainGroupAllItems() {
      this.setListPage();
    },
    domainGroupItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    }
  },
  created() {
    // 데이터 표준 메뉴의 도메인 - 도메인 그룹 선택 시 domainGroup data를 불러온다.
    this.getDomainGroupData();
    eventBus.$on('NOTICE', this.onUploadNotice);
  },
  beforeDestroy() {
    eventBus.$off('NOTICE', this.onUploadNotice);
  },
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.domainGroupItems.length / this.itemsPerPage);
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addDomainGroupModalShow = true;
      } else if (value === 'update') {
        if (this.checkedSelected()) {
          this.updateModalInit();
          this.updateDomainGroupModalShow = true;
        }
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addDomainGroupModalShow = false;
        this.$refs.form.reset();
      } else if (value === 'update') {
        this.updateDomainGroupModalShow = false;
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          // this.createWord();
          this.createdDomainGroup()
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateDomainGroup();
        }
      }
    },
    fieldcheck(status) {
      if (status === 'add') {
        if (this.addDomainGroup_domainGrpNm === null) {
          this.$swal.fire({
            title: '도메인 그룹명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.addDomainGroup_domainGrpNm.focus()
          return false;
        }
      } else if (status === 'update') {
        if (this.updateDomainGroup_domainGrpNm === null) {
          this.$swal.fire({
            title: '도메인 그룹명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.updateDomainGroup_domainGrpNm.focus()
          return false;
        }
      }

      return true;
    },
    // 도메인 그룹 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      this.updateDomainGroup_id = this.selectedItem[0].id;
      this.updateDomainGroup_domainGrpNm = this.selectedItem[0].domainGrpNm;
      this.updateDomainGroup_commStndYn = this.selectedItem[0].commStndYn;
    },
    getDomainGroupData() {
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainGroupList").then(result => {
          let _data = result.data;

          // console 표시
          console.log("📃 Domain Group LIST ↓↓↓")
          console.log(_data);

          this.domainGroupAllItems = _data;
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 그룹 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }

      this.resetDetail();
    },
    checkedSelected() {
      if (this.selectedItem === null || this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '수정할 도메인 그룹을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      } else if (this.selectedItem.length > 1) {
        this.$swal.fire({
          title: '수정할 도메인 그룹을 한 개만 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      }
      return true;
    },
    createdDomainGroup() {
      try {
        let domainGroupData = {
          'domainGrpNm': this.addDomainGroup_domainGrpNm,
          'commStndYn': this.addDomainGroup_commStndYn,
        };

        axios.post(this.$APIURL.base + "api/std/createDomainGroup", domainGroupData).then(result => {
          if (result.data.resultCode === 200) {
            this.hideModal('add');

            this.$swal.fire({
              title: '새로운 도메인 그룹이 등록되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            });

            this.getDomainGroupData();

            // 초기화
            this.addDomainGroup_domainGrpNm = null;
            this.addDomainGroup_commStndYn = 'N';

          } else {
            this.$swal.fire({
              title: '도메인 그룹 등록 실패',
              text: result.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 그룹 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        this.$swal.fire({
          title: '도메인 그룹 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateDomainGroup() {
      try {

        let domainGroupData = {
          'id': this.updateDomainGroup_id,
          'domainGrpNm': this.updateDomainGroup_domainGrpNm,
          'commStndYn': this.updateDomainGroup_commStndYn,
        };

        axios.post(this.$APIURL.base + "api/std/updateDomainGroup", domainGroupData).then(result => {

          if (result.data.resultCode === 200) {
            this.hideModal('update');

            this.$swal.fire({
              title: '도메인 그룹이 수정되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            });

            this.getDomainGroupData();
          } else {
            this.$swal.fire({
              title: '도메인 그룹 수정 실패',
              text: result.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 그룹 수정 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        this.$swal.fire({
          title: '도메인 그룹 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    domainGroupRemoveItem() {
      if (this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '삭제할 도메인 그룹을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      };

      let removeName = '';

      for (let i = 0; i < this.selectedItem.length; i++) {
        if (i === 0) {
          removeName += this.selectedItem[i].domainGrpNm;
        } else {
          removeName += ', ' + this.selectedItem[i].domainGrpNm;
        }
      }

      this.$swal.fire({
        title: '정말로 도메인 그룹을 삭제할까요?',
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

          for (let i = 0; i < this.selectedItem.length; i++) {
            let removeObj = {
              id: this.selectedItem[i].id
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/std/deleteDomainGroups", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '도메인 그룹이 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getDomainGroupData();
                } else {
                  this.$swal.fire({
                    title: '도메인 그룹 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }

              }).catch(error => {
                this.$swal.fire({
                  title: '도메인 그룹 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '도메인 그룹 삭제 실패 - params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })

    },
    bindDetail(item) {
      // 도메인 그룹명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // remove item에 단독으로 넣어주기
      // this.removeItems = [item];
      // 수정 모달 열기
      this.showModal('update');
    },
    resetDetail() {
      this.selectedItem = [];
    },
    domainGroupExcelFileUpload() {
      let fileUpload = document.getElementById('inputDomainGroupUpload');
      if (fileUpload != null) fileUpload.click();
    },
    readExcelFile(event) {
      const file = event.target.files[0];
      if (file === undefined) return;
      this.excelFile = this.$refs.file.files[0];

      this.uploadLogs = [];
      this.isUploading = true;
      this.collectiveModalShow = true;

      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getDomainGroupData();
        }
      }, 60000);

      const _url = this.$APIURL.base + "api/std/uploadDomainGroups";
      const formData = new FormData();
      formData.append('file', this.excelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      document.getElementById('inputDomainGroupUpload').value = '';
    },
    onUploadNotice(msg) {
      if (!this.collectiveModalShow) return;
      if (!msg.data || !msg.data.startsWith('[도메인그룹]')) return;
      const level = msg.noticeType === 'ERROR' ? 'ERROR' : 'INFO';
      this._addUploadLog(level, msg.data);
      if (msg.data.includes('완료 -')) {
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
        this.getDomainGroupData();
        const summary = msg.data.replace('[도메인그룹] ', '');
        const failMatch = summary.match(/실패:\s*(\d+)건/);
        const failCount = failMatch ? parseInt(failMatch[1]) : 0;
        this.$swal.fire({
          title: '도메인 그룹 일괄등록 완료',
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
    /** 86번 #11 — 도메인 그룹 업로드 양식 다운로드 (헤더만 있는 빈 xlsx) */
    domainGroupTemplateDownload() {
      const url = this.$APIURL.base + 'api/std/downloadDomainGroupTemplate';
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', '도메인그룹_일괄등록_템플릿.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    domainGroupListDownload() {
      let _keyWord = this.searchGrpNm && this.searchGrpNm.length !== 0 ? this.searchGrpNm : null;
      try {
        axios.get(this.$APIURL.base + "api/std/downloadDomainGroups", {
          params: { 'searchKey': _keyWord },
          responseType: 'blob',
          headers: { "Accept": "application/vnd.ms-excel" }
        }).then(response => {
          const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/csv" }));
          const link = document.createElement("a");
          link.href = url;
          let _today = this.$getToday();
          link.setAttribute("download", `도메인그룹_${_today}.xlsx`);
          document.body.appendChild(link);
          link.click();
          window.URL.revokeObjectURL(url);
          link.remove();
        }).catch(() => {
          this.$swal.fire({ title: '도메인 그룹 다운로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        });
      } catch (error) {
        console.log('도메인 그룹 다운로드 실패 :', error);
      }
    }
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
  }
}
</script>
  
<style scoped>
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }

#domainGroup_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#domainGroup_table thead th:nth-child(1) {
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

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}
</style>