<template>
  <v-main>
    <v-sheet class="containerWrapper">
      <v-sheet class="treeWrapper">
        <v-card class="treeCard">
          <v-jstree id="systemTree" ref="tree" :data="treeItems" multiple allow-batch whole-row
            @item-click="itemClick"></v-jstree>
          <!-- <v-jstree :data="data" ref="tree" multiple allow-batch :whole-row="true" @item-click="itemClick"></v-jstree> -->
        </v-card>

      </v-sheet>
      <v-sheet class="infoWrapper">
        <!-- 버튼 영역 -->
        <v-sheet v-show="selectedItem[0].sysCd !== ''"
          v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 15px', 'margin': '0 0 15px 0', 'display': 'flex', 'align-items': 'center', 'justify-content': 'flex-end' }]">
          <v-btn class="gradient" v-on:click="showModal('add')" title="등록" :style="{ marginRight: '20px' }">등록</v-btn>
          <v-btn class="gradient" v-on:click="showModal('update')" title="수정"
            :style="{ marginRight: '20px' }">수정</v-btn>
          <v-btn class="gradient" v-on:click="removeSystemData()" title="삭제">삭제</v-btn>
        </v-sheet>

        <!-- 테이블 -->
        <v-data-table id="systemInfo_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
          <template v-slot:body="{ items }">
            <tbody>
              <!-- 상세 테이블 왼쪽  -->
              <tr v-for="header in infoHeaders" :key="header.value">
                <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '20%' }">
                  {{ header.text }}
                </td>
                <!-- 상세 테이블 오른쪽  -->
                <td v-for="item in items" :key="item.sysNm">
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
    </v-sheet>
    <!-- Add System Modal -->
    <v-dialog max-width="950px" v-model="addSystemModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="시스템 등록"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <v-form ref="form">

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템 타입</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="addSystem_sysTp" :placeholder="'선택'"
                  ref="addSystem_sysTp" :items="sysTpItems" :menu-props="{ top: false, offsetY: true }"
                  :rules="[v => !!v || '시스템 타입은 필수 입력값입니다.']">
                  <template v-slot:no-data>
                    <v-list-item>
                      <v-list-item-title></v-list-item-title>
                    </v-list-item>
                  </template>
                </v-select>
              </v-col>
            </v-row>

            <!-- <v-row v-if="addSystem_sysTp_value === 1"> -->
            <v-row>
              <v-col cols="4">
                <v-subheader>시스템 그룹</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense color="ndColor" v-model="addSystem_parentSysCd" :placeholder="'선택'"
                  :items="system_parentSysCdItems" item-value="sysCd" item-text="sysNm" ref="addSystem_parentSysCd"
                  :menu-props="{ top: false, offsetY: true }">
                  <template v-slot:no-data>
                    <v-list-item>
                      <v-list-item-title></v-list-item-title>
                    </v-list-item>
                  </template>
                </v-select>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field ref="addSystem_sysNm" v-model="addSystem_sysNm"
                  :rules="[() => !!addSystem_sysNm || '시스템명은 필수 입력값입니다.']" clearable required dense
                  placeholder="시스템 이름 입력" color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>시스템 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-textarea clearable dense color="ndColor" rows="1" v-model="addSystem_sysDesc" ref="addSystem_sysDesc"
                  placeholder="시스템 설명 입력"></v-textarea>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="12">
                <v-subheader>데이터 소스</v-subheader>
              </v-col>
              <v-col cols="12">
                <v-row class="dsWrapper">
                  <v-sheet class="dsListWrapperStyle">
                    <h3 :style="{ margin: '15px' }">선택 데이터 소스 리스트</h3>

                    <h3 v-if="addSystem_sysDsLst.length === 0" :style="{ margin: '100px 0px', textAlign: 'center' }">지정된
                      데이터 소스가 없습니다.</h3>
                    <v-list-item v-for="(item, index) in addSystem_sysDsLst" :key="index" class="liStyle">
                      <span :style="{ width: 'calc(100% - 80px)' }">
                        {{ item.dsn }}
                      </span>
                      <div :style="{ width: '80px' }">
                        <v-btn class="white--text" color="error" @click="removeSelectedDs(index, item.id)"
                          :style="{ width: '80px !important', height: '30px !important' }">
                          삭제
                        </v-btn>
                      </div>

                    </v-list-item>

                  </v-sheet>

                  <v-sheet class="dsListWrapperStyle">
                    <h3 :style="{ margin: '15px' }">데이터 소스 리스트</h3>

                    <v-data-table id="all_bs_table" :headers="dsHeaders" :items="datasourceItems" hide-default-footer
                      :page.sync="page" :items-per-page="itemsPerPage" item-key="id" show-select class="px-4 pb-3"
                      v-model="addSystem_sysDsLst">
                    </v-data-table>
                    <v-sheet class="split_bottom_wrap">
                      <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
                        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
                          next-icon="mdi-menu-right" color="ndColor" :total-visible="7"></v-pagination>
                      </div>
                    </v-sheet>
                  </v-sheet>
                </v-row>
              </v-col>
            </v-row>

          </v-form>
        </template>
      </NdModal>
    </v-dialog>

    <!-- update System Modal -->
    <v-dialog max-width="950px" v-model="updateSystemModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="시스템 수정"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <v-form ref="form">

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템 타입</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="updateSystem_sysTp" :placeholder="'선택'"
                  :items="sysTpItems" :menu-props="{ top: false, offsetY: true }"
                  :rules="[v => !!v || '시스템 타입은 필수 입력값입니다.']">
                  <template v-slot:no-data>
                    <v-list-item>
                      <v-list-item-title>

                      </v-list-item-title>
                    </v-list-item>
                  </template>
                </v-select>
              </v-col>
            </v-row>

            <v-row>
              <!-- <v-row v-if="updataSystem_sysTp_value === 1"> -->
              <v-col cols="4">
                <v-subheader>시스템 그룹</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense color="ndColor" v-model="updateSystem_parentSysCd" :placeholder="'선택'"
                  :items="system_parentSysCdItems" item-value="sysCd" item-text="sysNm"
                  :menu-props="{ top: false, offsetY: true }">
                  <template v-slot:no-data>
                    <v-list-item>
                      <v-list-item-title>

                      </v-list-item-title>
                    </v-list-item>
                  </template>
                </v-select>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">시스템명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field ref="updateSystem_sysNm" v-model="updateSystem_sysNm"
                  :rules="[() => !!updateSystem_sysNm || '시스템명은 필수 입력값입니다.']" clearable required dense
                  placeholder="시스템 이름 입력" color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>시스템 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-textarea clearable dense color="ndColor" rows="1" v-model="updateSystem_sysDesc"
                  ref="updateSystem_sysDesc" placeholder="시스템 설명 입력"></v-textarea>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="12">
                <v-subheader>데이터 소스</v-subheader>
              </v-col>
              <v-col cols="12">
                <v-row class="dsWrapper">
                  <v-sheet class="dsListWrapperStyle">
                    <h3 :style="{ margin: '15px' }">선택 데이터 소스 리스트</h3>

                    <h3 v-if="updateSystem_sysDsLst.length === 0" :style="{ margin: '100px 0px', textAlign: 'center' }">
                      지정된
                      데이터 소스가 없습니다.</h3>
                    <v-list-item v-for="(item, index) in updateSystem_sysDsLst" :key="index" class="liStyle">
                      <span :style="{ width: 'calc(100% - 80px)' }">
                        {{ item.dsn }}
                      </span>
                      <div :style="{ width: '80px' }">
                        <v-btn class="white--text" color="error" @click="removeSelectedDs(index, item.id)"
                          :style="{ width: '80px !important', height: '30px !important' }">
                          삭제
                        </v-btn>
                      </div>

                    </v-list-item>

                  </v-sheet>
                  <v-sheet class="dsListWrapperStyle">
                    <h3 :style="{ margin: '15px' }">데이터 소스 리스트</h3>

                    <v-data-table id="all_bs_table" :headers="dsHeaders" :items="datasourceItems" hide-default-footer
                      :page.sync="page" :items-per-page="itemsPerPage" item-key="id" show-select class="px-4 pb-3"
                      v-model="updateSystem_sysDsLst">

                    </v-data-table>
                    <v-sheet class="split_bottom_wrap">
                      <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
                        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
                          next-icon="mdi-menu-right" color="ndColor" :total-visible="7"></v-pagination>
                      </div>
                    </v-sheet>
                  </v-sheet>
                </v-row>
              </v-col>
            </v-row>

          </v-form>
        </template>
      </NdModal>
    </v-dialog>

  </v-main>
</template>

<script>
import axios from "axios";
import VJstree from 'vue-jstree'
import NdModal from "./../views/modal/NdModal.vue"

export default {
  name: 'MMSystem',
  props: ['isMobile'],
  components: {
    VJstree,
    NdModal
  },
  data: () => ({
    systemInfoList: [], // 시스템 정보 리스트
    datasourceItems: [], // 데이터 베이스 소스
    system_parentSysCdItems: [], // 시스템일 때 상위 시스템 목록
    treeItems: [], // 트리 아이템
    // 등록 모달
    addSystemModalShow: false,
    // 수정 모달
    updateSystemModalShow: false,
    // 등록 관련
    addSystem_sysTp: null,
    addSystem_sysTp_value: null,
    addSystem_parentSysCd: null,
    addSystem_sysNm: null,
    addSystem_sysDesc: null,
    addSystem_sysDsLst: [],
    addSystem_sysDsLstNm: [],
    // 수정 관련
    updateSystem_sysCd: null,
    updateSystem_parentSysCd: null,
    updateSystem_sysNm: null,
    updateSystem_sysTp: null,
    updataSystem_sysTp_value: null,
    updateSystem_sysDesc: null,
    updateSystem_sysDsLst: [],
    // 시스템 타입
    sysTpItems: [
      '시스템 그룹', '시스템'
    ],
    // 선택 아이템 - table 빈값 생성
    selectedItem: [{
      sysCd: '',
      sysNm: '',
      sysDesc: '',
      parentSysCd: '',
      sysTp: '',
      level: '',
      path: '',
      cretDt: '',
      cretUserId: '',
      updtDt: '',
      updtUserId: '',
    }], // 사용자가 트리에서 항목 클릭 시 바인드
    infoHeaders: [
      { text: '시스템 이름', value: 'sysNm', sortable: false, },
      { text: '시스템 설명', value: 'sysDesc', sortable: false, },
      { text: '상위 시스템', value: 'parentSysNm', sortable: false, },
      { text: '시스템 타입', value: 'sysTpNm', sortable: false, },
      { text: '데이터 소스', value: 'sysDsLstNm', sortable: false, },
      // { text: '데이터 소스', value: 'sysDsLst', sortable: false, },
      { text: '시스템 경로', value: 'pathNm', sortable: false, },
      { text: '생성일시', value: 'cretDt', sortable: false, },
      { text: '생성사용자ID', value: 'cretUserId', sortable: false, },
      { text: '수정일시', value: 'updtDt', sortable: false, },
      { text: '수정사용자ID', value: 'updtUserId', sortable: false, },
    ],
    dsHeaders: [
      { text: '데이터소스명', value: 'dsn', sortable: false, },
      { text: '데이터베이스타입', value: 'dbmsTp', sortable: false, },
    ],

    page: 1,
    pageCount: 0,
    itemsPerPage: 10,

  }),
  created() {
    this.getDatasourceData();
    this.getSysInfoList();
  },
  mounted() {
  },
  watch: {
    addSystem_sysTp(value) {
      if (value === '시스템 그룹') {
        this.addSystem_sysTp_value = 0;
        // this.addSystem_parentSysCd = null;
      } else if (value === '시스템') {
        this.addSystem_sysTp_value = 1;
      }
    },
    updateSystem_sysTp(value) {
      if (value === '시스템 그룹') {
        this.updataSystem_sysTp_value = 0;
        // this.updateSystem_parentSysCd = null;
      } else if (value === '시스템') {
        this.updataSystem_sysTp_value = 1;
      }
    },
    treeItems() {
      this.$refs.tree.initializeData(this.treeItems);
    },
    //0311 pagination 추가 
    datasourceItems() {
      this.setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    }
  },
  methods: {
    setListPage() {
      this.pageCount = Math.ceil(
        this.datasourceItems.length / this.itemsPerPage
      );
    },
    async itemClick(data, event) {
      let _dsNm = [];
      for (let i = 0; i < data.model.sysDsLst.length; i++) {
        try {
          let dsNm = await this.getDataSourceById(data.model.sysDsLst[i]);
          _dsNm.push(dsNm);
        } catch (error) {
          console.error(error);
        }
      }
      // console.log(_dsNm);

      this.selectedItem = [data.model];

      // this.selectedItem에 데이터 소스명 추가
      this.selectedItem[0].sysDsLstNm = _dsNm;
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addSystemModalShow = true;
      } else if (value === 'update') {
        if (this.checkSelected()) {
          this.updateModalInit();
          this.updateSystemModalShow = true;
        }
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addSystemModalShow = false;
        this.resetAddModal();
      } else if (value === 'update') {
        this.updateSystemModalShow = false;
        this.resetUpdateModal();
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createSystemData();
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateSystemData();
        }
      }
    },
    // 수정 버튼 클릭 시 선택한 데이터가 있는지 확인
    checkSelected() {
      if (this.selectedItem[0].sysCd === '') {
        this.$swal.fire({
          title: '변경할 시스템을 선택해주세요.',
          showConfirmButton: false,
          timer: 1500,
          icon: 'error',
        })
        return false;
      } else {
        return true;
      }
    },
    // 등록 모달 리셋
    resetAddModal() {
      this.addSystem_sysTp = null;
      this.addSystem_sysTp_value = null;
      this.addSystem_parentSysCd = null;
      this.addSystem_sysNm = null;
      this.addSystem_sysDesc = null;
      this.addSystem_sysDsLst = [];
    },
    resetUpdateModal() {
      this.updateSystem_sysTp = null;
      this.updataSystem_sysTp_value = null;
      this.updateSystem_parentSysCd = null;
      this.updateSystem_sysNm = null;
      this.updateSystem_sysDesc = null;
      this.updateSystem_sysDsLst = [];
    },
    // 시스템 정보 수정 버튼 클릭 시 데이터 불러오기
    updateModalInit() {
      console.log(this.selectedItem[0]);
      this.updateSystem_sysCd = this.selectedItem[0].sysCd;
      this.updateSystem_parentSysCd = this.selectedItem[0].parentSysCd;
      this.updateSystem_sysNm = this.selectedItem[0].sysNm;
      this.updateSystem_sysDesc = this.selectedItem[0].sysDesc;

      if (this.selectedItem[0].sysTp === 0) {
        this.updateSystem_sysTp = '시스템 그룹';
      } else if (this.selectedItem[0].sysTp === 1) {
        this.updateSystem_sysTp = '시스템';
      }

      // console.log(this.datasourceItems)

      let _sysDsList = [];
      for (let i = 0; i < this.selectedItem[0].sysDsLst.length; i++) {
        for (let j = 0; j < this.datasourceItems.length; j++) {
          if (this.selectedItem[0].sysDsLst[i] === this.datasourceItems[j].id) {
            _sysDsList.push(this.datasourceItems[j]);
          }
        }
      }

      this.updateSystem_sysDsLst = _sysDsList;
      // console.log(_sysDsList);
    },
    getSysInfoList() {
      try {
        axios.get(this.$APIURL.base + "api/sysinfo/getSysInfoList").then(result => {
          let _data = result.data;

          // console 표시
          console.log(" SYSTEM INFO LIST ↓↓↓")
          console.log(_data);

          // parentSysCd가 null이 아니면 상위 시스템이 존재한다는 의미,하지만 parentSysCd는 id값이기 때문에 parentSysNM을 추가로 생성해야한다.
          _data.forEach(element => {
            if (element.parentSysCd !== null) {
              _data.forEach(element2 => {
                if (element.parentSysCd === element2.sysCd) {
                  element.parentSysNm = element2.sysNm;
                }
              });
            }
          });

          // 시스템 타입이 0이면 시스템 그룹, 1이면 시스템
          _data.forEach(element => {
            if (element.sysTp === 0) {
              element.sysTpNm = '시스템 그룹';
            } else if (element.sysTp === 1) {
              element.sysTpNm = '시스템';
            }
          });

          // path는 배열을 풀어서 문자열을 만든다. 배열과 배열 사이에는 /를 넣는다.
          _data.forEach(element => {
            let _path = '';
            for (let i = 0; i < element.path.length; i++) {
              if (i === 0) {
                _path += element.path[i];
              } else {
                _path += '/' + element.path[i];
              }
            }
            element.pathNm = _path;
          });

          // datasourceItems의 id값과 sysDsLst의 값이 같으면 datasourceItems의 dsn을 sysDsLstNm으로 새로 생성해준다.
          // _data.forEach(element => {
          //   let _sysDsLstNm = [];
          //   for (let i = 0; i < element.sysDsLst.length; i++) {
          //     for (let j = 0; j < this.datasourceItems.length; j++) {
          //       if (element.sysDsLst[i] === this.datasourceItems[j].id) {
          //         _sysDsLstNm.push(this.datasourceItems[j].dsn);
          //       }
          //     }
          //   }
          //   element.sysDsLst = _sysDsLstNm;
          // });


          this.systemInfoList = _data;

          // 시스템 추가, 변경 시 사용할 상위 시스템 목록을 생성해서 바인드해야한다. sysTp 0인 것을 찾아서 바인드한다.
          this.system_parentSysCdItems = []; // 초기화
          this.systemInfoList.forEach(element => {
            if (element.sysTp === 0) {
              this.system_parentSysCdItems.push({
                'sysNm': element.sysNm,
                'sysCd': element.sysCd,
              });
            }
          });

          // tree item 생성
          this.treeItems = this.convertToTreeData(this.systemInfoList);

        }).catch(error => {
          this.$swal.fire({
            title: '시스템 정보 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.log(error);
      }
    },
    getDataSourceById(id) {
      return axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceById", {
        params: {
          'dsId': id
        }
      }).then(result => {
        let _data = result.data;

        console.log(_data.dsn);

        return _data.dsn;

      }).catch(error => {
        console.error(error);
      })
    },
    getDatasourceData() {
      axios.get(this.$APIURL.base + "api/sysinfo/getDataSourceList").then(result => {
        let _data = result.data;


        // table에서 사용할 dsTp의 한글명 생성하여 dsTpNm에 저장 
        // 조회한 데이터소스 데이터의 dsTp =0 일경우 'DB' 아닌경우 HOST

        for (let i = 0; i < _data.length; i++) {
          if (_data[i].dsTp === 0) {
            _data[i].dsTpNm = 'DB';
          } else if (_data[i].dsTp === 1) {
            _data[i].dsTpNm = 'HOST';
          }
        }

        // console 표시
        console.log("📃 Data Source LIST ↓↓↓")
        console.log(_data);

        this.datasourceItems = _data;
      }).catch(error => {
        console.error(error);
      })
    },
    convertToTreeData(list) {
      const treeData = []
      const idMap = {}

      // 노드 생성 및 idMap 구성
      list.forEach(node => {
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

      // 노드 및 하위 노드에 대한 속성 채우기
      function fillNode(node) {
        // console.log(node)

        node.text = node.sysNm
        node.icon = node.sysTp === 0 ? 'mdi mdi-folder' : 'mdi mdi-file-document'
        node.state = { opened: true }
        node.children.forEach(childNode => fillNode(childNode))
      }
      treeData.forEach(node => fillNode(node))

      console.log(treeData)
      return treeData;
    },
    removeSelectedDs(index, id) {
      if (this.addSystemModalShow) {
        // 선택한 데이터 소스를 선택 데이터 소스 리스트에서 제거
        this.addSystem_sysDsLst.splice(index, 1);

        // 데이터 소스 리스트에서 해당 id와 일치하는 데이터 소스를 찾아 체크박스를 해제
        const ds = this.datasourceItems.find(ds => ds.id === id);
        ds.isSelected = false;
      } else if (this.addSystem_sysDsLst) {
        // 선택한 데이터 소스를 선택 데이터 소스 리스트에서 제거
        this.updateSystem_sysDsLst.splice(index, 1);

        // 데이터 소스 리스트에서 해당 id와 일치하는 데이터 소스를 찾아 체크박스를 해제
        const ds = this.datasourceItems.find(ds => ds.id === id);
        ds.isSelected = false;
      }
    },
    fieldcheck(status) {
      if (status === 'add') {
        if (this.addSystem_sysTp === '' || this.addSystem_sysTp === null) {
          this.$swal.fire({
            title: '시스템 타입을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.$refs.addSystem_sysTp.focus()

          return false;
        } else if (this.addSystem_sysTp === '시스템' && (this.addSystem_parentSysCd === '' || this.addSystem_parentSysCd === null)) {
          this.$swal.fire({
            title: '시스템 그룹을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.$refs.addSystem_parentSysCd.focus()

          return false;
        } else if (this.addSystem_sysNm === '' || this.addSystem_sysNm === null) {
          this.$swal.fire({
            title: '시스템명을 입력해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.$refs.addSystem_sysNm.focus()

          return false;
        }

      } else if (status === 'update') {
        if (this.updateSystem_sysNm === '' || this.updateSystem_sysNm === null) {
          this.$swal.fire({
            title: '시스템명을 입력해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.$refs.updateSystem_sysNm.focus()
          return false;
        }
      }

      return true;
    },
    createSystemData() {
      let _parentSysCd = null;

      if (this.addSystem_sysTp_value === 1) {
        _parentSysCd = this.addSystem_parentSysCd;
      }


      // addSystem_sysDsLst의 id값만 추출하여 배열로 저장
      let _sysDsLst = [];
      for (let i = 0; i < this.addSystem_sysDsLst.length; i++) {
        _sysDsLst.push(this.addSystem_sysDsLst[i].id);
      }

      let _systemData = {
        'sysCd': null,
        'parentSysCd': this.addSystem_parentSysCd,
        // 'parentSysCd': _parentSysCd,
        'sysNm': this.addSystem_sysNm,
        'sysTp': this.addSystem_sysTp_value,
        'sysDsLst': _sysDsLst,
        'sysDesc': this.addSystem_sysDesc,
      }

      axios.post(this.$APIURL.base + 'api/sysinfo/createSysInfo', _systemData).then(res => {
        // console.log(res);

        if (res.data.resultCode === 200) {
          this.hideModal('add');

          this.$swal.fire({
            title: '새로운 시스템 정보가 등록되었습니다.',
            icon: 'success',
            showConfirmButton: false,
            timer: 1500
          })

          // 시스템 정보 다시 불러오기
          this.getSysInfoList()
          // 선택된 시스템 초기화
          this.resetSelectedItem();
        } else {
          this.$swal.fire({
            title: '시스템 정보 등록 실패',
            text: res.data.resultMessage,
            confirmButtonText: '확인',
            icon: 'error',
          });
        }
      }).catch(error => {
        this.$swal.fire({
          title: '시스템 정보 등록 실패',
          text: error,
          confirmButtonText: '확인',
          icon: 'error',
        });
      })
    },
    updateSystemData() {
      // let _parentSysCd = null;

      // if (this.updataSystem_sysTp_value === 1) {
      //   _parentSysCd = this.updateSystem_parentSysCd;
      // }
      // updateSystem_sysDsLst id값만 추출하여 배열로 저장
      let _sysDsLst = [];
      for (let i = 0; i < this.updateSystem_sysDsLst.length; i++) {
        _sysDsLst.push(this.updateSystem_sysDsLst[i].id);
      }

      let _systemData = {
        'sysCd': this.updateSystem_sysCd,
        'parentSysCd': this.updateSystem_parentSysCd,
        // 'parentSysCd': _parentSysCd,
        'sysNm': this.updateSystem_sysNm,
        'sysTp': this.updataSystem_sysTp_value,
        'sysDsLst': _sysDsLst,
        'sysDesc': this.updateSystem_sysDesc,
      }

      console.log(_systemData);

      axios.post(this.$APIURL.base + 'api/sysinfo/updateSysInfo', _systemData).then(res => {
        // console.log(res);

        if (res.data.resultCode === 200) {
          this.hideModal('update');

          this.$swal.fire({
            title: '시스템 정보가 수정되었습니다.',
            icon: 'success',
            showConfirmButton: false,
            timer: 1500
          })

          // 시스템 정보 다시 불러오기
          this.getSysInfoList();
          // 선택된 시스템 초기화
          this.resetSelectedItem();
        } else {
          this.$swal.fire({
            title: '시스템 정보 수정 실패',
            text: res.data.resultMessage,
            confirmButtonText: '확인',
            icon: 'error',
          });
        }
      }).catch(error => {
        this.$swal.fire({
          title: '시스템 정보 수정 실패',
          text: error,
          confirmButtonText: '확인',
          icon: 'error',
        });
      })
    },
    removeSystemData() {
      // 삭제할 시스템 확인. 없으면 경고
      if (this.selectedItem[0].sysCd === '') {
        this.$swal.fire({
          title: '삭제할 시스템을 선택해주세요.',
          showConfirmButton: false,
          timer: 1500,
          icon: 'error',
        })
        return false;
      }

      let removeSystemData = [{
        'sysCd': this.selectedItem[0].sysCd,
      }]

      let removeName = this.selectedItem[0].sysNm;

      this.$swal.fire({
        title: '정말로 시스템 정보를 삭제할까요?',
        icon: 'warning',
        showCancelButton: true,
        text: removeName,
        confirmButtonColor: '#3678a7',
        cancelButtonColor: '#909090',
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {


          axios.post(this.$APIURL.base + "api/sysinfo/deleteSysInfos", removeSystemData)
            .then(res => {
              // console.log(res)

              if (res.data.resultCode === 200) {

                this.$swal.fire({
                  title: '시스템 정보가 삭제되었습니다.',
                  icon: 'success',
                  showConfirmButton: false,
                  timer: 1500
                });

                // 시스템 정보 다시 불러오기
                this.getSysInfoList();
                // 선택된 시스템 초기화
                this.resetSelectedItem();
              } else {
                this.$swal.fire({
                  title: '시스템 정보 삭제 실패',
                  text: res.data.resultMessage,
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              }
            }).catch(error => {
              this.$swal.fire({
                title: '시스템 정보 삭제 실패 - API 확인 필요',
                confirmButtonText: '확인',
                icon: 'error',
              });
            });

        }
      })

    },
    resetSelectedItem() {
      this.selectedItem = [{
        sysCd: '',
        sysNm: '',
        sysDesc: '',
        parentSysCd: '',
        sysTp: '',
        level: '',
        path: '',
        cretDt: '',
        cretUserId: '',
        updtDt: '',
        updtUserId: '',
      }]
    }
  }
}
</script>

<style scoped>
.containerWrapper {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: stretch;
  height: 100%;
  background: #F5F7FA;
}

.treeWrapper {
  width: 20%;
  margin: 20px;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06), 0 2px 12px rgba(0, 0, 0, 0.03);
}

.treeWrapper > .treeCard {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #E8EAF6;
  overflow: auto;
}

#systemTree {
  font-size: 1rem;
}

.infoWrapper {
  flex: 1;
  margin: 20px;
  overflow: auto;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06), 0 2px 12px rgba(0, 0, 0, 0.03);
  padding: 16px;
}

#systemInfo_table {
  overflow-y: overlay;
  overflow-x: hidden;
}

#systemInfo_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

#systemInfo_table thead th:nth-child(1) {
  width: 58px !important;
  min-width: 58px !important;
  max-width: 58px !important;
}

.dsWrapper {
  width: 100%;
  height: 280px;
  margin: 0 15px;
  position: relative;
  display: flex;
  justify-content: space-between;
}

.dsListWrapperStyle {
  width: 48%;
  border: 1px solid #C5CAE9;
  border-radius: 10px;
  max-height: 300px;
  overflow: auto;
}
</style>