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
              <!-- 시스템명 입력 필드 -->
              <span :style="{ fontSize: '.875rem' }">시스템명</span>
              <v-text-field class="pr-4 pl-4" v-model="searchSystem" v-on:keyup.enter="getDataModel"
                @click:clear="searchSystem = ''" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 시스템명 입력 필드 -->
              <span :style="{ fontSize: '.875rem' }">모델명</span>
              <v-text-field class="pr-4 pl-4" v-model="searchModel" v-on:keyup.enter="getDataModel"
                @click:clear="searchModel = ''" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getDataModel"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
            </v-row>
          </v-sheet>
          <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
            <!-- <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn> -->
            <v-btn class="gradient" v-on:click="removeItemAction()" title="삭제">삭제</v-btn>
            <v-btn class="gradient" v-on:click="collectionAction()" title="수집">수집</v-btn>
          </v-sheet>
        </v-sheet>
        <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
        <v-sheet class="tableSpt">
          <v-sheet>
            <span class="ndColor--text">총 {{ dataModelItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
        <!-- 데이터모델 목록 -->
        <v-data-table id="dataModel_table" :headers="dataModelHeaders" :items="dataModelItems" :page.sync="page"
          :items-per-page="itemsPerPage" hide-default-footer item-key="dataModelId" show-select class="px-4 pb-3"
          v-model="removeItems" :loading="loadTable" loading-text="잠시만 기다려주세요.">
          <!-- 클릭 가능한 아이템 설정 : 데이터모델명  -->
          <template v-slot:[`item.dataModelNm`]="{ item }">
            <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="showDetail(item)">{{
              item.dataModelNm
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
        <!-- 페이지네이션 -->
        <v-sheet class="split_bottom_wrap">
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
                  detailDataModelNm
                }}'</span>
              <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;상세 보기</span>
            </v-sheet>
            <!-- 수정 / 삭제 버튼 -->
            <v-sheet class="pr-4 pl-4">
              <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn>
              </v-sheet>
          </v-sheet>
          <!-- 테이블 -->
          <v-data-table id="dataModel_detail_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
            <template v-slot:body="{ items }">
              <tbody>
                <!-- 상세 테이블 왼쪽  -->
                <tr v-for="header in detaileHeaders" :key="header.value">
                  <td :style="{ backgroundColor: 'rgba(24, 127, 196, 0.1)', width: '15%' }">
                    {{ header.text }}
                  </td>
                  <td v-for="item in items" :key="item.dataModelId">
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

    <!-- Add dataModel Modal -->
    <v-dialog max-width="600" v-model="addDataModelModal" id="addDataModelModal">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="데이터 모델 등록"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <!-- 데이터 모델명 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">데이터모델명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="add_dataModelNm" ref="add_dataModelNm" name="add_dataModelNm"
                  :rules="[() => !!add_dataModelNm || '데이터모델명은 필수 입력값입니다.']" clearable required dense placeholder="데이터모델명"
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>
            <!-- 시스템명 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템명</v-subheader>
              </v-col>
              <v-col cols="8">

                <treeselect v-model="add_dataModelSysCd" :multiple="false" :options="dataModelSysList" placeholder="선택" />

                <!-- <v-select dense required color="ndColor" v-model="add_dataModelSysCd" :items="dataModelSysList"
                  item-text="sysNm" item-value="sysCd" ref="add_dataModelSysCd" :placeholder="'선택'"
                  name="add_dataModelSysCd" :rules="[v => !!v || '시스템명은 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:change="resetDataSourceItems()"></v-select> -->
              </v-col>
            </v-row>
            <!-- 데이터 소스 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">데이터 소스</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="add_dataSource" :items="dataSourceList" item-text="dsn"
                  name="add_dataSource" item-value="id" ref="add_dataSource" :placeholder="'선택'"
                  no-data-text="데이터 소스가 없습니다." :rules="[v => !!v || '데이터 소스는 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:click="getDataSourceList()"></v-select>
              </v-col>
            </v-row>
            <!-- 버전 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">버전</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="add_ver" name="add_ver" ref="add_ver"
                  :rules="[() => !!add_ver || '버전은 필수 입력값입니다.']" clearable required dense placeholder="버전"
                  color="ndColor"></v-text-field>
              </v-col>
            </v-row>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- update dataModel Modal -->
    <v-dialog max-width="600" v-model="updateDataModelModal" id="updateDataModelModal">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="데이터 모델 수정"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <v-container fluid>
            <!-- 데이터 모델명 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">데이터모델명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="update_dataModelNm" ref="update_dataModelNm" name="update_dataModelNm"
                  :rules="[() => !!update_dataModelNm || '데이터모델명은 필수 입력값입니다.']" clearable required dense
                  placeholder="데이터모델명" color="ndColor"></v-text-field>
              </v-col>
            </v-row>
            <!-- 시스템명 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템명</v-subheader>
              </v-col>
              <v-col cols="8">
                <treeselect v-model="update_dataModelSysCd" :multiple="false" :options="dataModelSysList" placeholder="선택"
                  ref="update_dataModelSysCd" />

                <!-- <v-select dense required color="ndColor" v-model="update_dataModelSysCd" :items="dataModelSysList"
                  item-text="sysNm" item-value="sysCd" ref="update_dataModelSysCd" :placeholder="'선택'"
                  name="update_dataModelSysCd" :rules="[v => !!v || '시스템명은 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:change="resetDataSourceItems()"></v-select> -->
              </v-col>
            </v-row>
            <!-- 데이터 소스 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">데이터 소스</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="update_dataSource" :items="dataSourceList"
                  item-text="dsn" name="update_dataSource" item-value="id" ref="update_dataSource" :placeholder="'선택'"
                  no-data-text="데이터 소스가 없습니다." :rules="[v => !!v || '데이터 소스는 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:click="getDataSourceList()"></v-select>
              </v-col>
            </v-row>
            <!-- 버전 -->
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">버전</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="update_ver" ref="update_ver" :rules="[() => !!update_ver || '버전은 필수 입력값입니다.']"
                  clearable required dense placeholder="버전" color="ndColor" name="update_ver"></v-text-field>
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
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'

export default {
  name: 'DSDatamodelCollection',
  components: {
    NdModal,
    Treeselect
  },
  watch: {
    add_dataModelSysCd() {
      this.resetDataSourceItems();
    },
    update_dataModelSysCd() {
      this.resetDataSourceItems();
    },
  },
  props: ['isMobile'],
  data: () => ({
    // 데이터 모델 정보
    dataModelItems: [],
    // 선택한 아이템
    selectedItem: [],
    // 삭제할 데이터 모델 정보
    removeItems: [],
    // 선택한 아이템의 데이터 모델 이름
    detailDataModelNm: '',
    // 데이터 모델에서 사용하는 시스템 리스트
    dataModelSysList: [],
    // 시스템 리스트에서 가져온 모든 데이터 정보보
    dataModelSysDataList: [],
    // 데이터 모델에서 시스템 리스트 선택 시 사용되면 데이터 소스 리스트
    dataSourceList: [],
    //  검색어 - 시스템명
    searchSystem: '',
    //  검색어 - 모델명
    searchModel: '',
    // 등록 모달
    addDataModelModal: false,
    // 수정 모달
    updateDataModelModal: false,
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 도메인의 수
    itemsPerPage: 10,
    // 테이블 로딩
    loadTable: true,
    // 등록 관련
    add_dataModelNm: null,
    add_ver: null,
    add_dataModelSysCd: null,
    add_dataSource: null,
    // 수정 관련
    update_dataModelId: null,
    update_dataModelNm: null,
    update_ver: null,
    update_dataModelSysCd: null,
    update_dataSource: null,
    // 데이터모델 테이블 헤더
    dataModelHeaders: [
      { text: '데이터 모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '시스템명', sortable: false, align: 'center', value: 'dataModelSysNm' },
      { text: '데이터소스', sortable: false, align: 'center', value: 'dataModelDsNm' },
      { text: '버전', sortable: false, align: 'center', value: 'dataModelVer' },
      { text: '수집 시작일시', align: 'center', sortable: false, value: 'clctStartDt' },
      { text: '수집 완료일시', align: 'center', sortable: false, value: 'clctEndDt' },
      { text: '완료 여부', align: 'center', sortable: false, value: 'clctCmptnYn' },
    ],
    // 하단 테이블 헤더
    detaileHeaders: [
      { text: '데이터 모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '시스템명', sortable: false, align: 'center', value: 'dataModelSysNm' },
      { text: '데이터소스', sortable: false, align: 'center', value: 'dataModelDsNm' },
      { text: '버전', sortable: false, align: 'center', value: 'dataModelVer' },
      { text: '수집 시작일시', align: 'center', sortable: false, value: 'clctStartDt' },
      { text: '수집 완료일시', align: 'center', sortable: false, value: 'clctEndDt' },
      { text: '완료 여부', align: 'center', sortable: false, value: 'clctCmptnYn' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
  }),
  methods: {
    async showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addDataModelModal = true;
      } else if (value === 'update') {
        if (this.selectedItem.length === 0) {
          this.$swal.fire({
            title: '수정할 데이터 모델을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return;
        }

        // 선택한 데이터 모델 정보 바인드
        this.updateDataModelInit();
        // system name Tree 생성
        this.createTreeItem(this.dataModelSysDataList)
        // 데이터 소스 바인드
        await this.updateModalSetData();
        this.updateDataModelModal = true;
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addModalReset();
        this.addDataModelModal = false;
      } else if (value === 'update') {
        this.updateModalReset();
        this.updateDataModelModal = false;
      }
    },
    showDetail(item) {
      // 도메인명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
      // 선택한 데이터 모델명 타이틀에 보이기 위해 추가가 
      this.detailDataModelNm = item.dataModelNm;
    },
    resetDetail() {
      // 선택한 데이터 모델 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.detailDataModelNm = null;
    },
    removeItemAction() {
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 데이터 모델을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      };

      let removeName = '';

      for (let i = 0; i < this.removeItems.length; i++) {
        if (i === 0) {
          removeName += this.removeItems[i].dataModelNm;
        } else {
          removeName += ', ' + this.removeItems[i].dataModelNm;
        }
      }

      this.$swal.fire({
        title: '정말로 데이터 모델을 삭제할까요?',
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
              dataModelId: this.removeItems[i].dataModelId
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/dm/deleteDataModels", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '데이터 모델이 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getDataModel();
                  this.resetDetail();
                } else {
                  this.$swal.fire({
                    title: '데이터 모델 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }

              }).catch(error => {
                this.$swal.fire({
                  title: '데이터 모델 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '데이터 모델 삭제 실패 - params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    collectionAction() {
      // console.log(this.removeItems)
      // 수집
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '수집을 진행할 데이터를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      } else if (this.removeItems.length > 1) {
        this.$swal.fire({
          title: '수집을 진행할 데이터를 하나만 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      try {
        let _params = {
          "dataModelId": this.removeItems[0].dataModelId,
          "dataModelNm": this.removeItems[0].dataModelNm,
          "dataModelSysCd": this.removeItems[0].dataModelSysCd,
          "dataModelSysNm": this.removeItems[0].dataModelSysNm,
          "dataModelDsId": this.removeItems[0].dataModelDsId,
          "dataModelDsNm": this.removeItems[0].dataModelDsNm,
          "dataModelVer": this.removeItems[0].dataModelVer,
        }

        // console.log(_params)

        axios.post(this.$APIURL.base + "api/dm/collectDataModel", _params)
          .then(res => {
            console.log(res)

            if (res.data.resultCode === 200) {

              this.$swal.fire({
                title: '데이터 모델 수집이 시작되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });

              this.getDataModel();
              this.resetDetail();
            } else {
              this.$swal.fire({
                title: '데이터 모델 수집 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }

          }).catch(error => {
            this.$swal.fire({
              title: '데이터 모델 수집 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          });

      } catch (error) {
        this.$swal.fire({
          title: '데이터 모델 수집 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    resetDataModelList() {
      // 데이터 모델 목록 다시 불러오기
      this.getDataModel();
      this.searchModel = '';
      this.searchSystem = '';
    },
    getDataModel() {
      this.loadTable = true;
      // 데이터 모델 가지고 오기
      try {
        // 검색어 중 모델명이 있으면 사용
        let schNm = null;
        if (this.searchModel !== '') {
          schNm = this.searchModel
        }

        // 검색어 중 시스템명이 있으면 사용
        let schSysNm = null
        if (this.searchSystem !== '') {
          schSysNm = this.searchSystem;
        }

        let _url = this.$APIURL.base + "api/dm/getDataModelList";

        axios.post(_url, {
          'schNm': schNm,
          'schSysNm': schSysNm,
        })
          .then((res) => {
            // console.log(res)
            this.dataModelItems = res.data;

            // console 표시
            console.log("📃 DATA MODEL LIST ↓↓↓")
            console.log(res.data);

            // 하단 상세보기 초기화
            this.resetDetail();

          })
          .catch((err) => {
            console.log(err);
          })
      } catch (error) {
        console.log(err);
      }

      this.loadTable = false;
    },
    getSystemList() {
      // 시스템 리스트 가지고 오기
      try {
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList')
          .then((res) => {
            // console 표시
            console.log("📃 SYSTEM INFO LIST ↓↓↓")
            console.log(res.data);

            this.dataModelSysDataList = res.data;

            // 시스템명만 추출하여 시스템명 리스트에 넣기 - 모달에서 selecte에 사용
            // let systemList = [];
            // for (let i = 0; i < res.data.length; i++) {
            //   let _obj = {
            //     sysCd: res.data[i].sysCd,
            //     sysNm: res.data[i].sysNm,
            //   }
            //   systemList.push(_obj);
            // }
            // // console.log(systemList);
            // this.dataModelSysList = systemList;

            this.createTreeItem(res.data);
          })
          .catch((err) => {
            console.log(err);
          })
      } catch (error) {
        console.log(err);
      }
    },
    // getSystemList method를 활용하여 tree Item 생성
    createTreeItem(_list) {
      // console.log(_list)
      const treeData = []
      const idMap = {}

      // 노드 생성 및 idMap 구성
      _list.forEach(node => {
        idMap[node.sysCd] = { ...node, children: []}
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

      this.dataModelSysList = treeData;
    },
    async updateModalSetData() {
      const result = await axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceListBySysCd", {
        params: {
          'sysCd': this.selectedItem[0].dataModelSysCd
        }
      });
      let _data = result.data;
      let _dataSourceList = [];

      for (let i = 0; i < _data.length; i++) {
        let _obj = {};
        _obj = {
          'id': _data[i].id,
          'dsn': _data[i].dsn,
        }
        _dataSourceList.push(_obj);
      }

      this.dataSourceList = _dataSourceList;

      setTimeout(() => {
        this.update_dataSource = this.selectedItem[0].dataModelDsId;
      }, 100);
    },

    resetDataSourceItems() {
      // 데이터 소스 리스트 리셋
      this.dataSourceList = [];
      this.add_dataSource = null;
      this.update_dataSource = null;
    },
    getDataSourceList() {
      let _sysCd = null;

      if (this.addDataModelModal) {
        _sysCd = this.add_dataModelSysCd;

        // 시스템을 먼저 선택하여 비교 대상이 되는 데이터 소스 리스트 가지고 오기
        if (!_sysCd) {
          this.$swal.fire({
            title: '먼저 시스템을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return;
        }
      } else if (this.updateDataModelModal) {
        _sysCd = this.update_dataModelSysCd;
      }

      axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceListBySysCd", {
        params: {
          'sysCd': _sysCd
        }
      }).then((res) => {
        // console.log(res)
        let _data = res.data;
        let _dataSourceList = [];

        for (let i = 0; i < _data.length; i++) {
          let _obj = {};
          _obj = {
            'id': _data[i].id,
            'dsn': _data[i].dsn,
          }
          _dataSourceList.push(_obj);
        }

        this.dataSourceList = _dataSourceList;

      }).catch((err) => {
        console.log(err);
      })
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createDataModel();
        }
      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateDataModel();
        }
      }
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'add') {
        if (this.add_dataModelNm === null) {
          _attr = '데이터 모델명은';
          this.$refs.add_dataModelNm.focus()
        } else if (this.add_dataModelSysCd === null) {
          _attr = '시스템명은';
        } else if (this.add_dataSource === null) {
          _attr = '데이터 소스는'
          this.$refs.add_dataSource.focus()
        } else if (this.add_ver === null) {
          _attr = '버전은'
          this.$refs.add_ver.focus()
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
        if (this.update_dataModelNm === null) {
          _attr = '데이터 모델명은';
          this.$refs.update_dataModelNm.focus()
        } else if (this.update_dataModelSysCd === null) {
          _attr = '시스템명은';
        } else if (this.update_dataSource === null || this.update_dataSource === '') {
          _attr = '데이터 소스는'
          this.$refs.update_dataSource.focus()
        } else if (this.update_ver === null) {
          _attr = '버전은'
          this.$refs.update_ver.focus()
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
    createDataModel() {
      try {
        let dataModleData = {
          'dataModelNm': this.add_dataModelNm,
          'dataModelSysCd': this.add_dataModelSysCd,
          'dataModelDsId': this.add_dataSource,
          'ver': this.add_ver,
        };

        // console.log(dataModleData);

        axios.post(this.$APIURL.base + "api/dm/createDataModel", dataModleData)
          .then(res => {
            // console.log(res)

            if (res.data.resultCode === 200) {

              this.$swal.fire({
                title: '새로운 데이터 모델이 등록되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              })

              this.getDataModel();

              this.hideModal('add');
            } else {
              this.$swal.fire({
                title: '데이터 모델 등록 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }

          }).catch(error => {
            this.$swal.fire({
              title: '데이터 모델 등록 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

      } catch (error) {
        this.$swal.fire({
          title: '데이터 모델 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateDataModelInit() {
      // 초기화
      this.updateModalReset();

      console.log(this.selectedItem[0])

      this.update_dataModelId = this.selectedItem[0].dataModelId;
      this.update_dataModelNm = this.selectedItem[0].dataModelNm;
      this.update_dataModelSysCd = this.selectedItem[0].dataModelSysCd;
      this.update_ver = this.selectedItem[0].dataModelVer;
      this.update_dataSource = this.selectedItem[0].dataModelDsId;
    },
    updateDataModel() {
      try {
        let dataModleData = {
          'dataModelId': this.update_dataModelId,
          'dataModelNm': this.update_dataModelNm,
          'dataModelSysCd': this.update_dataModelSysCd,
          'dataModelDsId': this.update_dataSource,
          'ver': this.update_ver,
        };

        // console.log(dataModleData);

        axios.post(this.$APIURL.base + "api/dm/updateDataModel", dataModleData)
          .then(res => {
            console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('update');

              this.$swal.fire({
                title: '데이터 모델이 수정되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });

              this.resetDataModelList();

            } else {
              this.$swal.fire({
                title: '데이터 모델 수정 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '데이터 모델 수정 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

      } catch (error) {
        this.$swal.fire({
          title: '데이터 모델 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    addModalReset() {
      this.add_dataModelNm = null;
      this.add_ver = null;
      this.add_dataModelSysCd = null;
      this.add_dataSource = null;
    },
    updateModalReset() {
      this.update_dataModelId = null;
      this.update_dataModelNm = null;
      this.update_ver = null;
      this.update_dataModelSysCd = null;
      this.update_dataSource = null;
    },
  },
  created() {
    // 데이터 표준 - 데이터 모델 - 데이터 모델 수집 메뉴 클릭 시 데이터 모델 목록 조회
    this.getDataModel();
    this.getSystemList();
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

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
}

.split_bottom {
  overflow: hidden;
  position: relative;
  height: 100%;
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

#dataModel_detail_table {
  height: calc(100% - 64px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#dataModel_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.12);
}
</style>