<template>
  <v-main>
    <!-- 일반 사용자인 경우 비밀번호 변경 탭을 보여주고 관리자인 경우 비밀번호 변경 탭과 사용자 관리 탭을 보여준다. -->
    <v-sheet>
      <v-tabs :value="this.tabName" class="tabsStyle" background-color="rgba(0,0,0,0.1)">
        <v-tab v-for="item in tabName" :tabindex="item.index" :key="item.index" class="tabBgColor"
          active-class="activeTabBgColor" v-on:click.stop="activeTabHandler(item.name)"
          :style="{ borderRight: '1px solid rgba(255,255,255, 0.4) !important' }">
          {{ item.title }}
        </v-tab>
      </v-tabs>
    </v-sheet>
    <v-sheet v-if="activeTab === 'tab1'" class="tabContentsWrapper">
      <!-- 비밀번호 변경 -->
      <v-sheet class="passwordChangeWrapper">
        <v-row>
          <v-subheader class="reqText">현재 비밀번호</v-subheader>
        </v-row>
        <v-row>
          <v-text-field type="password" color="ndColor" autocomplete="off" ref="currentPassword"
            :rules="[() => !!currentPassword || '현재 비밀번호를 입력해주세요.']" v-model="currentPassword" required outlined
            dense></v-text-field>
        </v-row>
        <v-row>
          <v-subheader class="reqText">새 비밀번호</v-subheader>
        </v-row>
        <v-row>
          <v-text-field type="password" color="ndColor" autocomplete="off" ref="newPassword"
            :rules="[() => !!newPassword || '새 비밀번호를 입력해주세요.']" v-model="newPassword" required outlined
            dense></v-text-field>
        </v-row>
        <v-row>
          <v-subheader class="reqText">새 비밀번호 확인</v-subheader>
        </v-row>
        <v-row>
          <v-text-field type="password" color="ndColor" autocomplete="off" ref="chekcNewPassword"
            :rules="[() => !!chekcNewPassword || '새 비밀번호 확인을 입력해주세요.']" v-model="chekcNewPassword" required outlined
            dense></v-text-field>
        </v-row>
        <v-btn class="gradient" title="수정" v-on:click="tryPasswordChange()"
          :style="{ width: '200px', padding: '0 5px', margin: '5%' }">수정</v-btn>
      </v-sheet>

    </v-sheet>
    <v-sheet v-else class="tabContentsWrapper">
      <!-- 사용자 관리 -->
      <!-- 버튼 영역 -->
      <v-sheet class="splitTopWrapper pt-4 pb-4"
        v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
        <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
        <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
          <v-btn class="gradient" v-on:click="showModal('add')" title="추가">추가</v-btn>
          <v-btn class="gradient" v-on:click="removeUserData()" title="삭제">삭제</v-btn>
        </v-sheet>
      </v-sheet>
      <v-sheet class="tableSpt">
        <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
        <v-sheet>
          <span class="ndColor--text">총 {{ userItems.length }}건</span>
        </v-sheet>
        <v-sheet>
          <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
            hide-details outlined dense></v-select>
        </v-sheet>
      </v-sheet>
      <v-divider></v-divider>
      <!-- 사용자 목록 -->
      <v-data-table id="user_table" :headers="userHeaders" :items="userItems" :single-select='true' :page.sync="page"
        :items-per-page="itemsPerPage" hide-default-footer item-key="id" show-select class="px-4 pb-3"
        v-model="removeItems" :loading="isLoading" loading-text="Loading... Please wait">
        <!-- 클릭 가능한 아이템 설정 : 계정명  -->
        <template v-slot:[`item.id`]="{ item }">
          <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="selectedUser(item)">{{
            item.id
          }}</span>
        </template>

        <template #top>
          <v-progress-linear v-show="isLoading" color="indigo darken-2" indeterminate />
        </template>
        <template #no-data>
          <v-alert v-show="!isLoading">
            데이터가 존재하지 않습니다.
          </v-alert>
          <span v-show="isLoading">Loading...</span>
        </template>
      </v-data-table>
    </v-sheet>
    <!-- Add user Modal -->
    <v-dialog max-width="600" v-model="addUserModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="사용자 등록"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">계정</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addUser_id" ref="addUser_id" :rules="[() => !!addUser_id || '계정은 필수 입력값입니다.']"
                    clearable required dense placeholder="ID" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">이름</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addUser_name" ref="addUser_name"
                    :rules="[() => !!addUser_name || '이름은 필수 입력값입니다.']" clearable required dense placeholder="홍길동"
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">이메일</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addUser_email" ref="addUser_email" type="email"
                    :rules="[() => !!addUser_email || '이메일은 필수 입력값입니다.']" clearable required dense
                    placeholder="abc@abc.com" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addUser_password" ref="addUser_password" type="password"
                    :rules="[() => !!addUser_password || '비밀번호는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호 확인</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addUser_checkpassword" ref="addUser_checkpassword" type="password"
                    :rules="[() => !!addUser_checkpassword || '비밀번호 확인은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>


              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">관리자 권한</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-switch v-model="addUser_admin" inset :label="addUser_adminText"></v-switch>
                </v-col>
              </v-row>

            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- update user Modal -->
    <v-dialog max-width="600" v-model="updateUserModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="사용자 수정"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-form ref="form">

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">계정</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateUser_id" ref="updateUser_id"
                    :rules="[() => !!updateUser_id || '계정은 필수 입력값입니다.']" clearable required dense placeholder="ID"
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">이름</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateUser_name" ref="updateUser_name"
                    :rules="[() => !!updateUser_name || '이름은 필수 입력값입니다.']" clearable required dense placeholder="홍길동"
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">이메일</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateUser_email" ref="updateUser_email" type="email"
                    :rules="[() => !!updateUser_email || '이메일은 필수 입력값입니다.']" clearable required dense
                    placeholder="abc@abc.com" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateUser_password" ref="updateUser_password" type="password"
                    :rules="[() => !!updateUser_password || '비밀번호는 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">비밀번호 확인</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="updateUser_checkpassword" ref="updateUser_checkpassword" type="password"
                    :rules="[() => !!updateUser_checkpassword || '비밀번호 확인은 필수 입력값입니다.']" clearable required dense
                    color="ndColor"></v-text-field>
                </v-col>
              </v-row>


              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">관리자 권한</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-switch v-model="updateUser_admin" inset :label="updateUser_adminText"></v-switch>
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
  name: 'MMUser',
  components: {
    NdModal
  },
  props: ['isMobile'],
  data() {
    return {
      // 메뉴 탭
      tabName: [
        { title: '비밀번호 변경', name: 'tab1', index: 0 },
        { title: '사용자 관리', name: 'tab2', index: 1 }
      ],
      // 탭 토글 컨트롤
      activeTab: 'tab1',
      // 유저 등록 모달
      addUserModalShow: false,
      // 유저 수정 모달
      updateUserModalShow: false,
      // 비밀번호 변경 관련
      // 현재 비밀번호
      currentPassword: null,
      // 새 비밀번호
      newPassword: null,
      // 새 비밀번호 확인
      chekcNewPassword: null,
      // 테이블 로딩
      isLoading: true,
      // 사용자 테이블 헤더 
      userHeaders: [
        { text: '계정', align: 'center', sortable: false, value: 'id' },
        { text: '이름', sortable: false, align: 'center', value: 'name' },
        { text: '이메일', sortable: false, align: 'center', value: 'email' },
        { text: '권한', sortable: false, align: 'center', value: 'adminString' },
      ],
      // 사용자 데이터
      userItems: [],
      // 사용자 추가 관련
      addUser_id: null, // 계정
      addUser_name: null, // 이름
      addUser_email: null, // 이메일
      addUser_password: null, // 비밀번호
      addUser_checkpassword: null, // 비밀번호 확인
      addUser_admin: false, // 관리자 권한
      addUser_adminText: '일반 사용자', // 관리자 권한 텍스트
      // 사용자 수정 관련
      updateUser_id: null, // 계정
      updateUser_name: null, // 이름
      updateUser_email: null, // 이메일
      updateUser_password: null, // 비밀번호
      updateUser_checkpassword: null, // 비밀번호 확인
      updateUser_admin: false, // 관리자 권한
      updateUser_adminText: '일반 사용자', // 관리자 권한 텍스트
      // 페이지네이션 시작 지정
      page: 1,
      // 총 페이지 수
      pageCount: null,
      // 한 페이지에 보여지는 단어의 수
      itemsPerPage: 10,
      // 삭제 관련
      removeItems: [],
      // 선택한 유저 데이터
      selectedUserItems: [],
      // API에서 사용하는 공통 header
      axiosHeader: { "headers": { "Content-Type": "multipart/form-data" } },
      // 테이블 편의성 관련
      tableViewLengthList: [10, 20, 30, 40, 50],
    }
  },
  watch: {
    userItems() {
      this.setListPage();
    },
    addUser_admin() {
      if (this.addUser_admin) {
        this.addUser_adminText = '관리자';
      } else {
        this.addUser_adminText = '일반 사용자';
      }
    },
    updateUser_admin() {
      if (this.updateUser_admin) {
        this.updateUser_adminText = '관리자';
      } else {
        this.updateUser_adminText = '일반 사용자';
      }
    },
    itemsPerPage() {
      this.setListPage();
    }
  },
  created() {
    // 관리 메뉴의 사용자 선택 시 user data를 불러온다.
    this.getUserData();
    // 사용자가 관리자라면 탭을 전부 보여주고 아니라면 숨김
    this.adminCheck();
  },
  mounted() {

  },
  computed: {

  },
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.userItems.length / this.itemsPerPage);
    },
    activeTabHandler(name) {
      this.activeTab = name
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addUserModalShow = true;
      } else if (value === 'update') {
        this.updateUserModalInit();
        this.updateUserModalShow = true;
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.resetUserCreateModal();
        this.addUserModalShow = false;
      } else if (value === 'update') {
        this.resetUserUpdateModal();
        this.updateUserModalShow = false;
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createUser();
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateUser()
        }
      }
    },
    checkInputPassword(password) {
      let passwordPattern = new RegExp(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[ -/:-@[-`{-~]).{9,16}$"
      );

      return passwordPattern.test(password);
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'add') {
        if (this.addUser_id === null) {
          _attr = '계정은';
          this.$refs.addUser_id.focus()
        } else if (this.addUser_name === null) {
          _attr = '이름은';
          this.$refs.addUser_name.focus()
        } else if (this.addUser_email === null) {
          _attr = '이메일은'
          this.$refs.addUser_email.focus()
        } else if (this.addUser_password === null) {
          _attr = '비밀번호는'
          this.$refs.addUser_password.focus()
        } else if (this.addUser_checkpassword === null) {
          _attr = '비밀번호 확인은'
          this.$refs.addUser_checkpassword.focus()
        } else if (this.addUser_password !== this.addUser_checkpassword) {
          this.$swal.fire({
            title: '비밀번호가 일치하지 않습니다.',
            text: '비밀번호를 다시 확인해주세요.',
            confirmButtonText: '확인',
            icon: 'warning',
            showConfirmButton: true,
          })
          this.$refs.addUser_checkpassword.focus()
          return false;
        } else if (!this.checkInputPassword(this.addUser_password)) {
          this.$swal.fire({
            title: `비밀번호는 대소문자 및 숫자, 특수문자를 포함한 9~16자만 가능합니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
            icon: 'error',
          });

          this.$refs.addUser_password.focus()
          return false
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
            icon: 'error',
          });

          return false;
        } else {
          return true;
        }
      } else if (status === 'update') {
        if (this.updateUser_id === null) {
          _attr = '계정은';
          this.$refs.updateUser_id.focus()
        } else if (this.updateUser_name === null) {
          _attr = '이름은';
          this.$refs.updateUser_name.focus()
        } else if (this.updateUser_email === null) {
          _attr = '이메일은'
          this.$refs.updateUser_email.focus()
        } else if (this.updateUser_password === null) {
          _attr = '비밀번호는'
          this.$refs.updateUser_password.focus()
        } else if (this.updateUser_checkpassword === null) {
          _attr = '비밀번호 확인은'
          this.$refs.updateUser_checkpassword.focus()
        } else if (this.updateUser_password !== this.updateUser_checkpassword) {
          this.$swal.fire({
            title: '비밀번호가 일치하지 않습니다.',
            text: '비밀번호를 다시 확인해주세요.',
            confirmButtonText: '확인',
            icon: 'warning',
            showConfirmButton: true,
          })
          this.$refs.updateUser_checkpassword.focus()
          return false;
        } else if (!this.checkInputPassword(this.updateUser_password)) {
          this.$swal.fire({
            title: `비밀번호는 대소문자 및 숫자, 특수문자를 포함한 9~16자만 가능합니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
            icon: 'error',
          });

          this.$refs.updateUser_password.focus()
          return false
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#187fc4',
            icon: 'error',
          });

          return false;
        } else {
          return true;
        }
      }
    },
    tryPasswordChange() {
      if (this.passwordCheck()) {

        let _params = {
          'user': this.$loginStatusData.id,
          'oldPassword': btoa(this.currentPassword),
          'newPassword': btoa(this.newPassword),
        }
        // console.log(_params)
        try {

          axios.get(this.$APIURL.base + "api/login/changePassword", {
            params: _params
          }).then(res => {
            console.log(res)
            if (res.data.resultMessage === 'OK') {
              this.$swal.fire({
                title: '비밀번호가 변경되었습니다.',
                text: '다시 로그인해주세요.',
                confirmButtonText: '확인',
                icon: 'success',
                showConfirmButton: true,
              }).then((result) => {
                if (result.isConfirmed) {
                  this.$router.push('/login')
                }
              })
            } else {
              this.$swal.fire({
                title: '비밀번호 변경 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(err => {
            this.$swal.fire({
              title: '비밀번호 변경 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

        } catch (error) {
          this.$swal.fire({
            title: '비밀번호 변경 실패 - params 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        }
      }
    },
    passwordCheck() {
      if (this.currentPassword === '' || this.currentPassword === null) {

        this.$swal.fire({
          title: `현재 비밀번호는 필수 입력값입니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        this.$refs.currentPassword.focus();
        return false
      } else if (this.newPassword === '' || this.newPassword === null) {
        this.$swal.fire({
          title: `새 비밀번호는 필수 입력값입니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        this.$refs.newPassword.focus()
        return false
      } else if (this.chekcNewPassword === '' || this.chekcNewPassword === null) {
        this.$swal.fire({
          title: `새 비밀번호 확인은 필수 입력값입니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        this.$refs.chekcNewPassword.focus()
        return false
      } else if (this.newPassword !== this.chekcNewPassword) {
        this.$swal.fire({
          title: `새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        this.$refs.chekcNewPassword.focus()
        return false
      } else if (!this.checkInputPassword(this.newPassword)) {
        this.$swal.fire({
          title: `비밀번호는 대소문자 및 숫자, 특수문자를 포함한 9~16자만 가능합니다.`,
          confirmButtonText: '확인',
          confirmButtonColor: '#187fc4',
          icon: 'error',
        });

        this.$refs.newPassword.focus()
        return false
      } else {
        return true
      }
    },
    getUserData() {
      // 사용자 데이터 가져오기
      try {
        axios
          .get(this.$APIURL.base + "api/login/getUserList")
          .then(result => {
            // console.log(result);

            let _data = result.data;

            // 권한 데이터가 true면 Admin, false면 User로 변경
            for (let i = 0; i < _data.length; i++) {
              if (_data[i].admin === true) {
                _data[i].adminString = 'Admin';
              } else {
                _data[i].adminString = 'User';
              }
            }
            // 테이블 생성하는 목록 Data에 전달
            this.userItems = _data;

            // 로딩바 닫기
            this.isLoading = false;
          })
          .catch(error => {
            this.$swal.fire({
              title: '유저 목록 바인드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
            // 로딩바 닫기
            this.isLoading = false;
          });
      } catch (error) {
        console.error(error);
        // 로딩바 닫기
        this.isLoading = false;
      }
    },
    createUser() {
      let userData = {
        'id': this.addUser_id,
        'password': btoa(this.addUser_password),
        'name': this.addUser_name,
        'email': this.addUser_email,
        'admin': this.addUser_admin,
      };

      try {
        axios.post(this.$APIURL.base + "api/login/createUser", userData, this.axiosHeader)
          .then(res => {
            console.log(res)
            if (res.data.resultCode === 200) {
              this.hideModal('add');

              this.$swal.fire({
                title: '새로운 사용자가 추가되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              })

              this.getUserData();
            } else {
              this.$swal.fire({
                title: '사용자 추가 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '사용자 추가 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

      } catch (error) {
        this.$swal.fire({
          title: '사용자 추가 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }


    },
    updateUser() {
      let userData = {
        'id': this.updateUser_id,
        'password': btoa(this.updateUser_password),
        'name': this.updateUser_name,
        'email': this.updateUser_email,
        'admin': this.updateUser_admin,
      };

      try {
        axios.post(this.$APIURL.base + "api/login/updateUser", userData, this.axiosHeader)
          .then(res => {
            if (res.data) {
              this.hideModal('update');

              this.$swal.fire({
                title: '사용자 정보 수정이 완료되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              })

              this.getUserData();
              this.resetSelectedUser();
            } else {
              this.$swal.fire({
                title: '사용자 수정 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '사용자 수정 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

      } catch (error) {
        this.$swal.fire({
          title: '사용자 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateUserModalInit() {
      this.updateUser_id = this.selectedUserItems[0].id;
      this.updateUser_name = this.selectedUserItems[0].name;
      this.updateUser_email = this.selectedUserItems[0].email;
      this.updateUser_admin = this.selectedUserItems[0].admin;
    },
    removeUserData() {
      // 사용자 삭제
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 사용자를 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      }

      // 사용자 삭제는 다른 삭제와 다르게 한 개체씩 삭제가능함
      this.$swal.fire({
        title: '정말로 사용자를 삭제할까요?',
        icon: 'warning',
        showCancelButton: true,
        text: this.removeItems[0].name,
        confirmButtonColor: '#3678a7',
        cancelButtonColor: '#909090',
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {

          let remoceUserForm = new FormData();
          remoceUserForm.append("id", this.removeItems[0].id);

          try {
            axios.post(this.$APIURL.base + "api/login/removeUser", remoceUserForm, this.axiosHeader)
              .then(res => {
                console.log(res)

                if (res.data) {
                  this.$swal.fire({
                    title: '사용자가 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getUserData();
                  this.resetSelectedUser();
                } else {
                  this.$swal.fire({
                    title: '사용자 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }
              }).catch(error => {
                this.$swal.fire({
                  title: '사용자 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '사용자 삭제 실패 -  params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    selectedUser(item) {
      this.selectedUserItems = [item]
      this.removeItems = [item]
      this.showModal('update')
    },
    resetSelectedUser() {
      this.selectedUserItems = []
      this.removeItems = []
    },
    resetUserCreateModal() {
      this.addUser_id = null;
      this.addUser_password = null;
      this.addUser_name = null;
      this.addUser_email = null;
      this.addUser_admin = null;
    },
    resetUserUpdateModal() {
      this.updateUser_id = null;
      this.updateUser_password = null;
      this.updateUser_name = null;
      this.updateUser_email = null;
      this.updateUser_admin = null;
    },
    adminCheck() {
      const _userStatus = this.$loginStatusData.id;

      axios.get(this.$APIURL.base + "api/login/isAdmin", {
        params: { user: _userStatus }
      })
        .then(result => {

          if (result.data) {
            this.tabName = [
              { title: '비밀번호 변경', name: 'tab1', index: 0 },
              { title: '사용자 관리', name: 'tab2', index: 1 }
            ]
          } else if (!result.data) {
            this.tabName = [
              { title: '사용자 관리', name: 'tab2', index: 1 }
            ]
          }
        })
        .catch(Error => {
          console.log(Error);
        });
    }
  },
}


</script>

<style scoped>
.tabsStyle {
  position: relative;
  width: 100% !important;
}

.tabsStyle .v-tab {
  /* border-top-right-radius: 10px !important;
  border-top-left-radius: 10px !important; */
  border-top-right-radius: 0px !important;
  color: rgba(0, 0, 0, 0.8);
  font-weight: 600;
}

.tabContentsWrapper {
  position: relative;
  width: 100%;
  height: calc(100% - 48px);
  overflow: hidden;
}

.splitTopWrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.passwordChangeWrapper {
  position: relative;
  width: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 5% auto 0;
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
}
</style>

