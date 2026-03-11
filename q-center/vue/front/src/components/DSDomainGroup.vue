<template>
  <v-main>
    <!-- 버튼 영역 -->
    <v-sheet class="btnWrapper pt-4 pb-4">
      <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
      <v-sheet class="pr-4 pl-4"
        v-bind:style="[isMobile ? { width: '100%', display: 'flex', justifyContent: 'space-between' } : {}]">
        <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
        <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn>
        <v-btn class="gradient" v-on:click="domainGroupRemoveItem()" title="삭제">삭제</v-btn>
      </v-sheet>
    </v-sheet>
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

export default {
  name: 'DSDomainGroup',
  components: {
    NdModal
  },
  props: ['isMobile'],
  data: () => ({
    // 도메인 그룹 목록 리스트
    domainGroupItems: [],
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
  }),
  watch: {
    addDomainGroupModalShow(val) {
      val || this.hideModal('add')
    },
    updateDomainGroupModalShow(val) {
      val || this.hideModal('update')
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
            confirmButtonColor: '#187fc4',
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
            confirmButtonColor: '#187fc4',
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

          this.domainGroupItems = _data;
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
    }
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
  }
}
</script>
  
<style scoped>
.btnWrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

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
  border-top: 1px solid rgba(0, 0, 0, 0.1);
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
}
</style>