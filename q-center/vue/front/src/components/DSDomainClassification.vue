<template>
  <v-main>
    <!-- 검색과 버튼 영역 -->
    <v-sheet class="splitTopWrapper pt-4 pb-4"
      v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
      <!-- 검색 -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
        <v-row :style="{ alignItems: 'center', margin: '0px' }">
          <span :style="{ fontSize: '.875rem' }">도메인 분류명</span>
          <!-- 도매인 분류명 입력 필드 -->
          <v-text-field class="pr-4 pl-4" v-model="searchDomainClassification"
            v-on:keyup.enter="executeSearchDomainClassifications" @click:clear="clearMessage" clearable prepend-icon=""
            clear-icon="mdi-close-circle" type="text" color="ndColor" single-line dense outlined hide-details
            :style="{ width: '200px' }">
          </v-text-field>
          <!-- 검색 버튼 -->
          <v-btn class="gradient" title="검색" v-on:click="executeSearchDomainClassifications"
            :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
          <!-- 초기화 버튼 -->
          <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
            :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
        </v-row>

      </v-sheet>
      <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
      <v-sheet class="pr-4 pl-4"
        v-bind:style="[isMobile ? { width: '100%', display: 'flex', justifyContent: 'space-between' } : {}]">
        <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
        <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn>
        <v-btn class="gradient" v-on:click="domainClassificationRemoveItem()" title="삭제">삭제</v-btn>
      </v-sheet>
    </v-sheet>
    <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ domainClassificationItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{width:'90px'}" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor" hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
    <!-- 도메인 분류 목록 -->
    <v-data-table id="domainClassification_table" :headers="domainClassificationHeaders"
      :items="domainClassificationItems" :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="domainClsfNm" show-select class="px-4 pb-3" v-model="selectedItem">
      <!-- 클릭 가능한 아이템 설정 : 도메인분류명  -->
      <template v-slot:[`item.domainClsfNm`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="bindDetail(item)">{{
          item.domainClsfNm
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

    <!-- add domainClassification Modal -->
    <v-dialog max-width="600" v-model="addDomainClassificationModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="도메인 분류 등록"
        footer-hide-title="취소" footer-submit-title="등록" :style="{ height: '40vh' }">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 그룹명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDomainClassification_domainGrpNm" :placeholder="'선택'"
                    :items="domainGroupNameItems" :rules="[v => !!v || '도메인 그룹명은 필수 입력값입니다.']"
                    :menu-props="{ top: false, offsetY: true }"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 분류명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomainClassification_domainClsfNm" ref="addDomainClassification_domainClsfNm"
                    :rules="[() => !!addDomainClassification_domainClsfNm || '도메인 분류명은 필수 입력값입니다.']" clearable required
                    dense placeholder="가격" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>공통표준여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addDomainClassification_commStndYn" row mandatory dense hide-details>
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

    <!-- update domainClassification Modal -->
    <v-dialog max-width="600" v-model="updateDomainClassificationModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="도메인 분류 수정"
        footer-hide-title="취소" footer-submit-title="수정" :style="{ height: '40vh' }">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 그룹명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="updateDomainClassification_domainGrpNm" :placeholder="'선택'"
                    :items="domainGroupNameItems" :rules="[v => !!v || '도메인 그룹명은 필수 입력값입니다.']"
                    :menu-props="{ top: false, offsetY: true }"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 분류명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDomainClassification_domainClsfNm"
                    ref="updateDomainClassification_domainClsfNm"
                    :rules="[() => !!updateDomainClassification_domainClsfNm || '도메인 분류명은 필수 입력값입니다.']" clearable required
                    dense placeholder="가격" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>공통표준여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="updateDomainClassification_commStndYn" row mandatory dense hide-details>
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
  </v-main>
</template>
  
<script>
import axios from "axios";
import NdModal from "./../views/modal/NdModal.vue"

export default {
  name: 'DSDomainClassification',
  components: {
    NdModal
  },
  props: ['isMobile'],
  data: () => ({
    // 도메인 분류 목록 리스트
    domainClassificationItems: [],
    // 검색 도메인 분류
    searchDomainClassification: null,
    // 삭제 관련
    removeItems: [],
    // 선택한 도메인 분류의 정보들
    selectedItem: [],
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 도메인 분류의 수
    itemsPerPage: 20,
    // 도메인 분류 등록 모달 보이기
    addDomainClassificationModalShow: false,
    // 도메인 분류 수정 모달 보이기
    updateDomainClassificationModalShow: false,
    // 도메인 그룹명 선택 리스트
    domainGroupNameItems: [],
    // 등록 관련
    addDomainClassification_domainClsfNm: null,
    addDomainClassification_domainGrpNm: null,
    addDomainClassification_commStndYn: "N",
    // 수정 관련
    updateDomainClassification_id: null,
    updateDomainClassification_domainClsfNm: null,
    updateDomainClassification_domainGrpNm: null,
    updateDomainClassification_commStndYn: "N",
    // 상단 테이블 헤더
    domainClassificationHeaders: [
      { text: '도메인 분류명', align: 'center', sortable: false, value: 'domainClsfNm' },
      { text: '도메인 그룹명', align: 'center', sortable: false, value: 'domainGrpNm' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
        // 테이블 편의성 관련
        tableViewLengthList: [10, 20, 30, 40, 50],
  }),
  watch: {
    addDomainClassificationModalShow(val) {
      val || this.hideModal('add')
    },
    updateDomainClassificationModalShow(val) {
      val || this.hideModal('update')
    },
    domainClassificationItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    }
  },
  created() {
    // 데이터 표준 메뉴의 도메인 - 도메인 그룹 선택 시 domainClassification data를 불러온다.
    this.getDomainClassificationData();
  },
  methods: {
    resetSearch() {
      this.searchDomainClassification = '';
    },
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.domainClassificationItems.length / this.itemsPerPage);
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        // 도메인 그룹명 가지고 와서 바인드
        this.getDomainGroupName();
        this.addDomainClassificationModalShow = true;
      } else if (value === 'update') {
        if (this.checkedSelected()) {
          // 도메인 그룹명 가지고 와서 바인드
          this.getDomainGroupName();
          // 선택한 도메인 분류 데이터 바인드
          this.updateModalInit();
          this.updateDomainClassificationModalShow = true;
        }
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addDomainClassificationModalShow = false;
        this.$refs.form.reset();
      } else if (value === 'update') {
        this.updateDomainClassificationModalShow = false;
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          // this.createWord();
          this.createdDomainClassification()
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateDomainClassification();
        }
      }
    },
    fieldcheck(status) {
      if (status === 'add') {
        if (this.addDomainClassification_domainClsfNm === null) {
          this.$swal.fire({
            title: '도메인 분류명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.addDomainClassification_domainClsfNm.focus()
          return false;
        } else if (this.addDomainClassification_domainGrpNm === null) {
          this.$swal.fire({
            title: '도메인 그룹명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.addDomainClassification_domainGrpNm.focus()
          return false;
        }
      } else if (status === 'update') {
        if (this.updateDomainClassification_domainClsfNm === null) {
          this.$swal.fire({
            title: '도메인 분류명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.updateDomainClassification_domainClsfNm.focus()
          return false;
        } else if (this.updateDomainClassification_domainGrpNm === null) {
          this.$swal.fire({
            title: '도메인 그룹명은 필수 입력값입니다.',
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });
          this.$refs.updateDomainClassification_domainGrpNm.focus()
          return false;
        }
      }

      return true;
    },
    // 도메인 분류 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      this.updateDomainClassification_id = this.selectedItem[0].id;
      this.updateDomainClassification_domainClsfNm = this.selectedItem[0].domainClsfNm;
      this.updateDomainClassification_domainGrpNm = this.selectedItem[0].domainGrpNm;
      this.updateDomainClassification_commStndYn = this.selectedItem[0].commStndYn;
    },
    getDomainClassificationData() {
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainClassificationList").then(result => {
          let _data = result.data;

          // console 표시
          console.log("📃 Domain Classification LIST ↓↓↓")
          console.log(_data);

          this.domainClassificationItems = _data;
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 분류 목록 바인드 실패 - API 확인 필요',
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
          title: '수정할 도메인 분류를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      } else if (this.selectedItem.length > 1) {
        this.$swal.fire({
          title: '수정할 도메인 분류를 한 개만 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      }
      return true;
    },
    createdDomainClassification() {

      try {
        let domainClassificationData = {
          'domainClsfNm': this.addDomainClassification_domainClsfNm,
          'domainGrpNm': this.addDomainClassification_domainGrpNm,
          'commStndYn': this.addDomainClassification_commStndYn,
        };

        axios.post(this.$APIURL.base + "api/std/createDomainClassification", domainClassificationData).then(result => {
          if (result.data.resultCode === 200) {
            this.hideModal('add');

            this.$swal.fire({
              title: '새로운 도메인 분류가 등록되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            });

            this.getDomainClassificationData();

            // 초기화
            this.addDomainClassification_domainClsfNm = null;
            this.addDomainClassification_domainGrpNm = null;
            this.addDomainClassification_commStndYn = 'N';

          } else {
            this.$swal.fire({
              title: '도메인 분류 등록 실패',
              text: result.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 분류 등록 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        this.$swal.fire({
          title: '도메인 분류 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateDomainClassification() {
      try {

        let domainClassificationData = {
          'id': this.updateDomainClassification_id,
          'domainClsfNm': this.updateDomainClassification_domainClsfNm,
          'domainGrpNm': this.updateDomainClassification_domainGrpNm,
          'commStndYn': this.updateDomainClassification_commStndYn,
        };

        axios.post(this.$APIURL.base + "api/std/updateDomainClassification", domainClassificationData).then(result => {

          if (result.data.resultCode === 200) {
            this.hideModal('update');

            this.$swal.fire({
              title: '도메인 분류가 수정되었습니다.',
              icon: 'success',
              showConfirmButton: false,
              timer: 1500
            });

            this.getDomainClassificationData();
          } else {
            this.$swal.fire({
              title: '도메인 분류 수정 실패',
              text: result.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 분류 수정 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });

      } catch (error) {
        this.$swal.fire({
          title: '도메인 분류 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    domainClassificationRemoveItem() {
      if (this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '삭제할 도메인 분류를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      };

      let removeName = '';

      for (let i = 0; i < this.selectedItem.length; i++) {
        if (i === 0) {
          removeName += this.selectedItem[i].domainClsfNm;
        } else {
          removeName += ', ' + this.selectedItem[i].domainClsfNm;
        }
      }

      this.$swal.fire({
        title: '정말로 도메인 분류를 삭제할까요?',
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
            axios.post(this.$APIURL.base + "api/std/deleteDomainClassifications", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '도메인 분류가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getDomainClassificationData();
                } else {
                  this.$swal.fire({
                    title: '도메인 분류 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }

              }).catch(error => {
                this.$swal.fire({
                  title: '도메인 분류 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '도메인 분류 삭제 실패 - params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })

    },
    bindDetail(item) {
      // 도메인 분류명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 수정 모달 열기
      this.showModal('update');
    },
    resetDetail() {
      this.selectedItem = [];
    },
    getDomainGroupName() {
      // 도메인 그룹명을 도메인 등록, 도메인 수정에 바인드
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainGroupList").then(result => {
          let _data = result.data;

          let _new_arr = [];

          for (let i = 0; i < _data.length; i++) {
            _new_arr.push(_data[i].domainGrpNm)
          }

          this.domainGroupNameItems = _new_arr;
        }).catch(error => {
          this.$swal.fire({
            title: '도메인 그룹명 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }
    },
    resetDomainClassificationList() {
      // 용어 목록 다시 불러오기
      this.getDomainClassificationData();
      this.searchDomainClassification = '';
      this.resetDetail();
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchDomainClassification = ''
    },
    executeSearchDomainClassifications() {
      if (this.searchDomainClassification === null || this.searchDomainClassification === "") {
        // 빈 값 입력 시 도메인 분류 목록 다시 불러오기 
        this.resetDomainClassificationList();
        return false;
      };

      try {
        axios.get(this.$APIURL.base + "api/std/getDomainClassificationListByNm", {
          params: {
            'domainClsfNm': this.searchDomainClassification,
          }
        }).then((res) => {
          this.domainClassificationItems = res.data;

          // 하단 상세보기 초기화
          this.resetDetail();
          // console 표시
          console.log("🔎 SEARCH DomainClassification LIST ↓↓↓")
          console.log(this.domainClassificationItems);
        }).catch((err) => {
          this.$swal.fire({
            title: '도메인 검색 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }
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
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}

.btnWrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

#domainClassification_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#domainClassification_table thead th:nth-child(1) {
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