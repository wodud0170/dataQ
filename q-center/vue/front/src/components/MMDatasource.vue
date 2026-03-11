<template>
  <v-main>
    <!-- 버튼 영역 -->
    <v-sheet class="btnWrapper pt-4 pb-4">
      <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
      <v-sheet class="pr-4 pl-4"
        v-bind:style="[isMobile ? { width: '100%', display: 'flex', justifyContent: 'space-between' } : {}]">
        <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
        <v-btn class="gradient" v-on:click="showModal('update')" title="수정">수정</v-btn>
        <v-btn class="gradient" v-on:click="datasourceRemoveItem()" title="삭제">삭제</v-btn>
      </v-sheet>
    </v-sheet>
    <v-sheet class="tableSpt">
      <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
      <v-sheet>
        <span class="ndColor--text">총 {{ datasourceItems.length }}건</span>
      </v-sheet>
      <v-sheet>
        <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
          hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>
    <v-divider></v-divider>
    <!-- 데이터 소스 목록 -->
    <v-data-table id="datasource_table" :headers="datasourceGroupHeaders" :items="datasourceItems" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="id" show-select class="px-4 pb-3"
      v-model="selectedItem" :loading="loadTable" loading-text="잠시만 기다려주세요.">
      <!-- 클릭 가능한 아이템 설정 : DNS  -->
      <template v-slot:[`item.dsn`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="bindDetail(item)">{{
          item.dsn
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

    <!-- add datasource Modal -->
    <v-dialog max-width="800px" v-model="addDatasourceModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="데이터 소스 등록"
        footer-hide-title="취소" footer-submit-title="등록" :footer-subbtn="true" footer-subbtn-title="연결 테스트"
        @subbtn="testDatasource()">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터 소스 타입</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addDatasource_dsTp" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="데이터 베이스" value="0" checked></v-radio>
                    <v-radio color="ndColor" label="호스트" value="1"></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">DSN</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDatasource_dsn" ref="addDatasource_dsn"
                    :rules="[() => !!addDatasource_dsn || 'DSN은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터 베이스 타입</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDatasource_dbmsTp" :value="datasourceDbmsTpItems"
                    :placeholder="'선택'" :items="datasourceDbmsTpItems" :rules="[v => !!v || '데이터 베이스는 필수 입력값입니다.']"
                    ref="addDatasource_dbmsTp" :menu-props="{ top: false, offsetY: true }"
                    v-on:change="resetDatasourceDriverNameItems()"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">드라이버</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDatasource_driverName"
                    :value="datasourceDriverNameItems" :items="datasourceDriverNameItems" :placeholder="'선택'"
                    :rules="[v => !!v || '드라이버는 필수 입력값입니다.']" :menu-props="{ top: false, offsetY: true }"
                    v-on:click="getDriverNameList()"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">서버주소</v-subheader>
                </v-col>
                <v-col cols="8" :style="{ display: 'flex', alignItems: 'center' }">
                  <vue-ip-input :ip="addDatasource_svrAddr" :on-change="onIpChange" :on-blur="onIpBlur"></vue-ip-input>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">포트</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDatasource_port" ref="addDatasource_port"
                    :rules="[() => !!addDatasource_port || '포트는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">사용자</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDatasource_userId" ref="addDatasource_userId"
                    :rules="[() => !!addDatasource_userId || '사용자는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDatasource_pwd" ref="addDatasource_pwd" type="password"
                    :rules="[() => !!addDatasource_pwd || '비밀번호는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터베이스명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDatasource_dbName" ref="addDatasource_dbName"
                    :rules="[() => !!addDatasource_dbName || '데이터베이스명은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>캐릭터셋</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDatasource_charSet" :value="datasourceCharSetItems"
                    :placeholder="'선택'" :items="datasourceCharSetItems"
                    :menu-props="{ top: false, offsetY: true }"></v-select>
                </v-col>
              </v-row>

            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- update domainGroup Modal -->
    <v-dialog max-width="800px" v-model="updateDatasourceModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="데이터 소스 수정"
        footer-hide-title="취소" footer-submit-title="수정" :footer-subbtn="true" footer-subbtn-title="연결 테스트"
        @subbtn="testDatasource()">
        <template v-slot:body>
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터 소스 타입</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="updateDatasource_dsTp" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="데이터 베이스" value="0"></v-radio>
                    <v-radio color="ndColor" label="호스트" value="1"></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">DSN</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDatasource_dsn" ref="updateDatasource_dsn"
                    :rules="[() => !!updateDatasource_dsn || 'DSN은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터 베이스 타입</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="updateDatasource_dbmsTp" :placeholder="'선택'"
                    :value="datasourceDbmsTpItems" :items="datasourceDbmsTpItems" ref="updateDatasource_dbmsTp"
                    :rules="[v => !!v || '데이터 베이스는 필수 입력값입니다.']" :menu-props="{ top: false, offsetY: true }"
                    v-on:change="resetDatasourceDriverNameItems()"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">드라이버</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="updateDatasource_driverName" :placeholder="'선택'"
                    :value="datasourceDriverNameItems" :items="datasourceDriverNameItems"
                    :rules="[v => !!v || '드라이버는 필수 입력값입니다.']" :menu-props="{ top: false, offsetY: true }"
                    v-on:click="getDriverNameList()"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">서버주소</v-subheader>
                </v-col>
                <v-col cols="8" :style="{ display: 'flex', alignItems: 'center' }">
                  <vue-ip-input :ip="updateDatasource_svrAddr" :on-change="onIpChange" :on-blur="onIpBlur"></vue-ip-input>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">포트</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDatasource_port" ref="updateDatasource_port"
                    :rules="[() => !!updateDatasource_port || '포트는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">사용자</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDatasource_userId" ref="updateDatasource_userId"
                    :rules="[() => !!updateDatasource_userId || '사용자는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDatasource_pwd" ref="updateDatasource_pwd" type="password"
                    :rules="[() => !!updateDatasource_pwd || '비밀번호는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터베이스명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateDatasource_dbName" ref="updateDatasource_dbName"
                    :rules="[() => !!updateDatasource_dbName || '데이터베이스명은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>캐릭터셋</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="updateDatasource_charSet" :placeholder="'선택'"
                    :value="datasourceCharSetItems" :items="datasourceCharSetItems"
                    :menu-props="{ top: false, offsetY: true }"></v-select>
                </v-col>
              </v-row>

            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- 연결 테스트 시 필요한 프로그래스 -->
    <v-dialog v-model="progress" fullscreen>
      <v-container fluid fill-height style="background-color: rgba(255, 255, 255, 0.5);" v-on:click="closeProgress()">
        <v-layout justify-center align-center :style="{ display: 'flex', flexDirection: 'column' }">
          <v-progress-circular :size="70" :width="10" color="ndColor" indeterminate></v-progress-circular>
          <h2>연결 테스트 진행 중...</h2>
        </v-layout>
      </v-container>
    </v-dialog>
  </v-main>
</template>
  
<script>
import axios from "axios";
import VueIpInput from 'vue-ip-input';
import NdModal from "./../views/modal/NdModal.vue"

export default {
  name: 'MMDatasource',
  components: {
    NdModal,
    VueIpInput,
  },
  props: ['isMobile'],
  data: () => ({
    // 데이터 소스 목록 리스트
    datasourceItems: [],
    // 삭제 관련
    removeItems: [],
    // 선택한 데이터 소스의 정보들
    selectedItem: [],
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 데이터 소스의 수
    itemsPerPage: 20,
    // 테이블 로딩
    loadTable: true,
    // 데이터 소스 등록 모달 보이기
    addDatasourceModalShow: false,
    // 데이터 소스 수정 모달 보이기
    updateDatasourceModalShow: false,
    // 등록 관련
    addDatasource_id: null,
    addDatasource_dsn: null,
    addDatasource_dsTp: null,
    addDatasource_dbmsTp: null,
    addDatasource_driverName: null,
    addDatasource_svrAddr: '0.0.0.0',
    addDatasource_port: null,
    addDatasource_userId: null,
    addDatasource_pwd: null,
    addDatasource_charSet: null,
    addDatasource_privateKey: null,
    addDatasource_dbName: null,
    // 수정 관련
    updateDatasource_id: null,
    updateDatasource_dsn: null,
    updateDatasource_dsTp: null,
    updateDatasource_dbmsTp: null,
    updateDatasource_driverName: null,
    updateDatasource_svrAddr: '0.0.0.0',
    updateDatasource_port: null,
    updateDatasource_userId: null,
    updateDatasource_pwd: null,
    updateDatasource_charSet: null,
    updateDatasource_privateKey: null,
    updateDatasource_dbName: null,
    updateDatasource_rmDir: null,
    // dbmsTp 아이템
    datasourceDbmsTpItems: ['Oracle', 'Cubrid', 'SQLServer', 'Altibase', 'Goldilocks', 'Tibero', 'MariaDB', 'PostgreSQL', 'Custom'],
    // driverName 아이템
    datasourceDriverNameItems: [],
    // 캐릭터셋 아이템
    datasourceCharSetItems: ['EUC-KR', 'UTF-8'],
    // 상단 테이블 헤더
    datasourceGroupHeaders: [
      { text: '구분', align: 'center', sortable: false, value: 'dsTpNm' },
      { text: 'DSN', sortable: false, align: 'center', value: 'dsn' },
      { text: '서버주소', sortable: false, align: 'center', value: 'svrAddr' },
      { text: '포트', sortable: false, align: 'center', value: 'port' },
      { text: '사용자', sortable: false, align: 'center', value: 'userId' },
    ],
    progress: false,
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
  }),
  watch: {
    addDatasourceModalShow(val) {
      val || this.hideModal('add')
    },
    updateDatasourceModalShow(val) {
      val || this.hideModal('update')
    },
    datasourceItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    }
  },
  created() {
    // 데이터 표준 메뉴의 도메인 - 데이터 소스 선택 시 domainGroup data를 불러온다.
    this.getDatasourceData();
  },
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.datasourceItems.length / this.itemsPerPage);
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addDatasourceModalShow = true;
      } else if (value === 'update') {
        if (this.checkedSelected()) {
          this.updateModalInit();
          // 드라이버 목록을 가지고 오기 위해 모달을 먼저 열어줌
          this.updateDatasourceModalShow = true;
          // 드라이버 목록을 가지고 오려면 우선 데이터 베이스 타입이 필요
          this.getDriverNameList();
        }
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addDatasourceModalShow = false;
        this.$refs.form.reset();
      } else if (value === 'update') {
        this.updateDatasourceModalShow = false;
      }
    },
    submitDialog(value) {
      // if (value === 'add') {
      //   if (this.fieldcheck('add')) {
      //     this.addDatasourceData()
      //   }
      // } else if (value === 'update') {
      //   if (this.fieldcheck('update')) {
      //     this.updateDatasourceData();
      //   }
      // }
      if (this.checkDatasourceTest()) {
        if (value === 'add') {
          this.addDatasourceData();
        } else if (value === 'update') {
          this.updateDatasourceData();
        }
      }
    },
    // fieldcheck(status) {
    //   if (status === 'add') {
    //     if (this.addDomainGroup_domainGrpNm === null) {
    //       this.$swal.fire({
    //         title: '데이터 소스명은 필수 입력값입니다.',
    //         confirmButtonText: '확인',
    //         confirmButtonColor: '#187fc4',
    //         icon: 'error',
    //       });
    //       this.$refs.addDomainGroup_domainGrpNm.focus()
    //       return false;
    //     }
    //   } else if (status === 'update') {
    //     if (this.updateDomainGroup_domainGrpNm === null) {
    //       this.$swal.fire({
    //         title: '데이터 소스명은 필수 입력값입니다.',
    //         confirmButtonText: '확인',
    //         confirmButtonColor: '#187fc4',
    //         icon: 'error',
    //       });
    //       this.$refs.updateDomainGroup_domainGrpNm.focus()
    //       return false;
    //     }
    //   }

    //   return true;
    // },
    addDatasourceData() {
      try {

        let _obj = {
          "id": null,
          "dsn": this.addDatasource_dsn,
          "dsTp": this.addDatasource_dsTp,
          "dbmsTp": this.addDatasource_dbmsTp,
          "driverName": this.addDatasource_driverName,
          "svrAddr": this.addDatasource_svrAddr,
          "port": this.addDatasource_port,
          "userId": this.addDatasource_userId,
          "pwd": this.addDatasource_pwd,
          "dbName": this.addDatasource_dbName,
          "charSet": this.addDatasource_charSet,
        }

        axios.post(this.$APIURL.base + "api/sysinfo/createDataSource", _obj)
          .then(res => {
            // console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('add');

              this.$swal.fire({
                title: '데이터 소스가 추가되었습니다.',
                // confirmButtonText: '확인',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });

              this.getDatasourceData();
            } else {
              this.$swal.fire({
                title: '데이터 소스 생성 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }

          }).catch(error => {
            this.$swal.fire({
              title: '데이터 소스 생성 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          });
      } catch (error) {
        this.$swal.fire({
          title: '데이터 소스 생성 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateDatasourceData() {
      try {

        let _obj = {
          "id": this.updateDatasource_id,
          "dsn": this.updateDatasource_dsn,
          "dsTp": this.updateDatasource_dsTp,
          "dbmsTp": this.updateDatasource_dbmsTp,
          "driverName": this.updateDatasource_driverName,
          "svrAddr": this.updateDatasource_svrAddr,
          "port": this.updateDatasource_port,
          "userId": this.updateDatasource_userId,
          "pwd": this.updateDatasource_pwd,
          "dbName": this.updateDatasource_dbName,
          "charSet": this.updateDatasource_charSet,
        }

        console.log(_obj)
        axios.post(this.$APIURL.base + "api/sysinfo/updateDataSource", _obj)
          .then(res => {
            console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('update');

              this.$swal.fire({
                title: '데이터 소스가 수정되었습니다.',
                // confirmButtonText: '확인',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });

              this.getDatasourceData();
            } else {
              this.$swal.fire({
                title: '데이터 소스 수정 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }

          }).catch(error => {
            this.$swal.fire({
              title: '데이터 소스 수정 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          });
      } catch (error) {
        this.$swal.fire({
          title: '데이터 소스 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    // 데이터 소스 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      this.updateDatasource_id = this.selectedItem[0].id;
      this.updateDatasource_dsn = this.selectedItem[0].dsn;
      this.updateDatasource_dsTp = this.selectedItem[0].dsTp;
      this.updateDatasource_dbmsTp = this.selectedItem[0].dbmsTp;
      this.updateDatasource_svrAddr = this.selectedItem[0].svrAddr;
      this.updateDatasource_port = this.selectedItem[0].port;
      this.updateDatasource_userId = this.selectedItem[0].userId;
      this.updateDatasource_pwd = this.selectedItem[0].pwd;
      this.updateDatasource_charSet = this.selectedItem[0].charSet;
      this.updateDatasource_privateKey = this.selectedItem[0].privateKey;
      this.updateDatasource_dbName = this.selectedItem[0].dbName;
      this.updateDatasource_rmDir = this.selectedItem[0].rmDir;
      this.updateDatasource_driverName = this.selectedItem[0].driverName;
    },
    getDatasourceData() {
      this.loadTable = true;
      try {
        axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceList").then(result => {
          let _data = result.data;

          // table에서 사용할 dsTp의 한글명 생성하여 dsTpNm에 저장
          for (let i = 0; i < _data.length; i++) {
            if (_data[i].dsTp === 0) {
              _data[i].dsTpNm = 'DB';
            }
          }

          // console 표시
          console.log("📃 Data Source LIST ↓↓↓")
          console.log(_data);

          this.datasourceItems = _data;
          this.loadTable = false;
        }).catch(error => {
          this.$swal.fire({
            title: '데이터 소스 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }

      this.resetDetail();
    },
    checkedSelected() {
      if (this.selectedItem === null || this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '수정할 데이터 소스를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      } else if (this.selectedItem.length > 1) {
        this.$swal.fire({
          title: '수정할 데이터 소스를 한 개만 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return false;
      }
      return true;
    },
    datasourceRemoveItem() {
      if (this.selectedItem.length === 0) {
        this.$swal.fire({
          title: '삭제할 데이터 소스를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      };

      let removeName = '';

      for (let i = 0; i < this.selectedItem.length; i++) {
        if (i === 0) {
          removeName += this.selectedItem[i].dsn;
        } else {
          removeName += ', ' + this.selectedItem[i].dsn;
        }
      }

      this.$swal.fire({
        title: '정말로 데이터 소스를 삭제할까요?',
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
            axios.post(this.$APIURL.base + "api/sysinfo/deleteDataSources", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '데이터 소스가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getDatasourceData();
                } else {
                  this.$swal.fire({
                    title: '데이터 소스 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }

              }).catch(error => {
                this.$swal.fire({
                  title: '데이터 소스 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '데이터 소스 삭제 실패 - params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })

    },
    bindDetail(item) {
      // 데이터 소스명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 수정 모달 열기
      this.showModal('update');
    },
    resetDetail() {
      this.selectedItem = [];
    },
    getDriverNameList() {
      // 1. dbmsTp가 있어야 선택 가능함.
      let _dbmsTp = null;

      if (this.addDatasourceModalShow) {
        _dbmsTp = this.addDatasource_dbmsTp;
        // 데이터 소스 추가 - 입력할 떄 dbmsTp가 없으면 경고창 띄우고 포커스
        if (_dbmsTp === null) {
          this.$swal.fire({
            title: '먼저 데이터 베이스 타입을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });

          this.$refs.addDatasource_dbmsTp.focus()

          return;
        }
      } else if (this.updateDatasourceModalShow) {
        _dbmsTp = this.updateDatasource_dbmsTp;
      }

      // 2. 드라이버 리스트를 가져온다.
      try {
        axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceDrivers")
          .then(res => {
            // console.log(res.data);

            if (res.status === 200) {
              // 전체 드라이버 정보를 가지고 온 후 선택된 dbmsTp에 맞는 드라이버만 필터링
              let _driverList = res.data.filter(item => item.driverType === _dbmsTp);
              let _new_arr = [];

              for (let i = 0; i < _driverList.length; i++) {
                if (_driverList[i].driverType === _dbmsTp) {
                  _new_arr.push(_driverList[i].driverName);
                }
              }

              // 필터링된 드라이버 리스트를 보여준다.
              this.datasourceDriverNameItems = _new_arr;

              if (this.updateDatasourceModalShow) {
                this.updateDatasource_driverName = this.selectedItem[0].driverName;
              }
            }
          }).catch(error => {
            this.$swal.fire({
              title: '드라이버 리스트 가져오기 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          });
      } catch (error) {
        this.$swal.fire({
          title: '드라이버 리스트 가져오기 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    resetDatasourceDriverNameItems() {
      this.datasourceDriverNameItems = [];
    },
    onIpChange(ip) {
      if (this.addDatasourceModalShow) {
        this.addDatasource_svrAddr = ip;
      } else if (this.updateDatasourceModalShow) {
        this.updateDatasource_svrAddr = ip;
      }
    },
    onIpBlur(ip) {
      if (this.addDatasourceModalShow) {
        this.addDatasource_svrAddr = ip;
      } else if (this.updateDatasourceModalShow) {
        this.updateDatasource_svrAddr = ip;
      }
    },
    testDatasource() {
      if (this.checkDatasourceTest()) {
        this.datasourceTestSubmit();
      }
    },
    checkDatasourceTest() {
      let _attr = null;

      if (this.addDatasourceModalShow) {
        if (this.addDatasource_dsn === null || this.addDatasource_dsn === '') {
          _attr = 'DSN';
        } else if (this.addDatasource_dsTp === null || this.addDatasource_dsTp === '') {
          _attr = '데이터 베이스 소스 타입';
        } else if (this.addDatasource_dbmsTp === null || this.addDatasource_dbmsTp === '') {
          _attr = '데이터베이스';
        } else if (this.addDatasource_driverName === null || this.addDatasource_driverName === '') {
          _attr = '드라이버';
        } else if (this.addDatasource_port === null || this.addDatasource_port === '') {
          _attr = '포트';
        } else if (this.addDatasource_userId === null || this.addDatasource_userId === '') {
          _attr = '사용자 아이디';
        } else if (this.addDatasource_pwd === null || this.addDatasource_pwd === '') {
          _attr = '사용자 비밀번호';
        } else if (this.addDatasource_dbName === null || this.addDatasource_dbName === '') {
          _attr = '데이터베이스명';
        }

        if (!this.checkIpAddress(this.addDatasource_svrAddr)) {
          _attr = '서버 주소(IP)';
        }

      } else if (this.updateDatasourceModalShow) {
        if (this.updateDatasource_dsn === null || this.updateDatasource_dsn === '') {
          _attr = 'DSN';
        } else if (this.updateDatasource_dsTp === null || this.updateDatasource_dsTp === '') {
          _attr = '데이터 베이스 소스 타입';
        } else if (this.updateDatasource_dbmsTp === null || this.updateDatasource_dbmsTp === '') {
          _attr = '데이터베이스';
        } else if (this.updateDatasource_driverName === null || this.updateDatasource_driverName === '') {
          _attr = '드라이버';
        } else if (this.updateDatasource_port === null || this.updateDatasource_port === '') {
          _attr = '포트';
        } else if (this.updateDatasource_userId === null || this.updateDatasource_userId === '') {
          _attr = '사용자 아이디';
        } else if (this.updateDatasource_pwd === null || this.updateDatasource_pwd === '') {
          _attr = '사용자 비밀번호';
        } else if (this.updateDatasource_dbName === null || this.updateDatasource_dbName === '') {
          _attr = '데이터베이스명';
        }

        if (!this.checkIpAddress(this.updateDatasource_svrAddr)) {
          _attr = '서버 주소(IP)';
        }
      }

      if (_attr !== null) {
        this.$swal.fire({
          title: `${_attr} 값이 유효하지 않습니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        return false;
      }

      return true;
    },
    datasourceTestSubmit() {
      this.progress = true;

      let _url = this.$APIURL.base + "api/sysinfo/testDataSource";

      let _obj = {};

      if (this.addDatasourceModalShow) {

        _obj = {
          "id": null,
          "dsn": this.addDatasource_dsn,
          "dsTp": this.addDatasource_dsTp,
          "dbmsTp": this.addDatasource_dbmsTp,
          "driverName": this.addDatasource_driverName,
          "svrAddr": this.addDatasource_svrAddr,
          "port": this.addDatasource_port,
          "userId": this.addDatasource_userId,
          "pwd": this.addDatasource_pwd,
          "dbName": this.addDatasource_dbName,
          "charSet": this.addDatasource_charSet,
        }

      } else if (this.updateDatasourceModalShow) {
        _obj = {
          "id": this.updateDatasource_id,
          "dsn": this.updateDatasource_dsn,
          "dsTp": this.updateDatasource_dsTp,
          "dbmsTp": this.updateDatasource_dbmsTp,
          "driverName": this.updateDatasource_driverName,
          "svrAddr": this.updateDatasource_svrAddr,
          "port": this.updateDatasource_port,
          "userId": this.updateDatasource_userId,
          "pwd": this.updateDatasource_pwd,
          "dbName": this.updateDatasource_dbName,
          "charSet": this.updateDatasource_charSet,
        }
      }

      try {
        axios.post(_url, _obj).then(res => {
          console.log(res)
          this.progress = false;
          if (res.data.resultCode === 200) {
            this.$swal.fire({
              title: '연결 테스트 성공',
              confirmButtonText: '확인',
              icon: 'success',
            });
          } else {
            this.$swal.fire({
              title: '연결 테스트 실패',
              text: res.data.resultMessage,
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }).catch(error => {
          this.$swal.fire({
            title: '연결 테스트 실패',
            text: error,
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        this.progress = false;
        this.$swal.fire({
          title: '연결 테스트 실패',
          text: error,
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    checkIpAddress: function (addr) {
      return /^(?!0)(?!.*\.$)((1?\d?\d|25[0-5]|2[0-4]\d)(\.|$)){4}$/.test(addr);
    },
    closeProgress() {
      // this.progress = false;
    },
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

#datasource_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#datasource_table thead th:nth-child(1) {
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