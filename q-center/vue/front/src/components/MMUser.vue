<template>
  <v-main>
    <!-- 사용자 관리 (목록-상세) -->
    <v-sheet style="display:flex; flex-direction:column; height:100%;">
      <!-- 상단: 버튼 -->
      <v-sheet class="d-flex justify-end align-center pa-3" style="border-bottom:1px solid #E8EAF6;">
        <v-btn class="gradient mr-2" @click="showAddDialog">사용자 추가</v-btn>
        <v-btn color="red" dark @click="deleteUser" :disabled="!selectedUserId">삭제</v-btn>
      </v-sheet>

      <!-- 한 줄(nowrap)이어야 두 열이 이 영역 높이에 맞춰져 각자 세로 스크롤된다 — 1366x768 에서 15번째 행·페이지 버튼·저장 버튼이 잘리지 않게 -->
      <v-row no-gutters style="flex:1; overflow:hidden; flex-wrap:nowrap;">
        <!-- 좌측: 사용자 목록 -->
        <v-col cols="5" style="border-right:1px solid #E8EAF6; overflow-y:auto;">
          <v-data-table :headers="userHeaders" :items="userItems" :page.sync="page" :items-per-page="itemsPerPage"
            dense hide-default-footer item-key="id" class="elevation-0"
            :loading="isLoading" @click:row="selectUser">
            <template v-slot:item.admin="{ item }">
              <v-chip x-small :color="item.admin ? 'indigo' : 'grey'" text-color="white">{{ item.admin ? '관리자' : '일반' }}</v-chip>
            </template>
            <template #no-data>
              <span class="grey--text">사용자가 없습니다.</span>
            </template>
          </v-data-table>
          <!-- 페이지네이션 (110번 11.7-9) -->
          <div class="text-center px-4 pt-4 pb-4" v-show="pageCount > 1">
            <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
              color="ndColor" :total-visible="10"></v-pagination>
          </div>
        </v-col>

        <!-- 우측: 사용자 상세 -->
        <v-col cols="7" style="overflow-y:auto;">
          <v-sheet v-if="!selectedUserId" class="d-flex align-center justify-center" style="height:100%;">
            <div class="text-center grey--text">
              <v-icon large color="grey lighten-1" class="mb-2">mdi-account-circle</v-icon>
              <div>사용자를 선택하면 상세 정보를 확인할 수 있습니다.</div>
            </div>
          </v-sheet>

          <v-sheet v-else class="pa-4">
            <div class="text-subtitle-1 font-weight-bold mb-3">
              <v-icon class="mr-1">mdi-account</v-icon> {{ detailUser.id }}
            </div>

            <!-- 기본 정보 -->
            <v-card outlined class="pa-3 mb-3">
              <div class="text-caption font-weight-bold mb-2">기본 정보</div>
              <v-row dense>
                <v-col cols="6">
                  <v-text-field v-model="detailUser.name" label="이름" dense outlined hide-details class="mb-2"></v-text-field>
                </v-col>
                <v-col cols="6">
                  <v-text-field v-model="detailUser.email" label="이메일" dense outlined hide-details class="mb-2"></v-text-field>
                </v-col>
                <v-col cols="6">
                  <v-text-field v-model="detailUser.phone" label="전화번호" dense outlined hide-details></v-text-field>
                </v-col>
                <!-- 104번 WP1 — 부서 (결재선의 요청자 부서장) -->
                <v-col cols="6">
                  <v-select v-model="detailUser.orgId" :items="orgItems" label="부서" dense outlined hide-details clearable></v-select>
                </v-col>
              </v-row>
            </v-card>

            <!-- 권한 설정 -->
            <v-card outlined class="pa-3 mb-3">
              <div class="text-caption font-weight-bold mb-2">권한 설정</div>
              <v-switch v-model="detailUser.admin" inset :label="detailUser.admin ? '관리자' : '일반 사용자'" color="indigo" hide-details></v-switch>
            </v-card>

            <!-- 계정 관리 -->
            <v-card outlined class="pa-3 mb-3">
              <div class="text-caption font-weight-bold mb-2">계정 관리</div>
              <v-row dense>
                <v-col cols="6">
                  <v-text-field v-model="detailUser.newPassword" label="새 비밀번호 (변경 시만 입력)" type="password" dense outlined hide-details></v-text-field>
                </v-col>
              </v-row>
            </v-card>

            <div class="d-flex justify-end">
              <v-btn class="gradient" @click="saveUser">저장</v-btn>
            </div>
          </v-sheet>
        </v-col>
      </v-row>
    </v-sheet>

    <!-- 사용자 추가 다이얼로그 -->
    <v-dialog max-width="500" v-model="addDialogShow">
      <v-card>
        <v-card-title>사용자 추가</v-card-title>
        <v-card-text>
          <!-- 입력 검사는 등록 때 알림창으로 한다 — 칸 아래 안내 줄(hide-details)을 빼 1366x768 에서도 등록 버튼까지 한 화면에 -->
          <v-text-field v-model="addForm.id" label="계정 ID" dense outlined hide-details class="mb-4"></v-text-field>
          <v-text-field v-model="addForm.name" label="이름" dense outlined hide-details class="mb-4"></v-text-field>
          <v-text-field v-model="addForm.email" label="이메일" dense outlined hide-details class="mb-4"></v-text-field>
          <v-text-field v-model="addForm.phone" label="전화번호" dense outlined hide-details class="mb-4"></v-text-field>
          <v-text-field v-model="addForm.password" label="비밀번호" type="password" dense outlined hide-details class="mb-4"></v-text-field>
          <v-text-field v-model="addForm.confirmPassword" label="비밀번호 확인" type="password" dense outlined hide-details class="mb-4"></v-text-field>
          <v-switch v-model="addForm.admin" inset :label="addForm.admin ? '관리자' : '일반 사용자'" color="indigo" hide-details></v-switch>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="addDialogShow = false">취소</v-btn>
          <v-btn color="primary" @click="createUser">등록</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from "axios";

export default {
  name: 'MMUser',
  props: ['isMobile'],
  data: () => ({
    // 사용자 관리
    isLoading: true,
    userHeaders: [
      { text: '계정', value: 'id', width: '100px' },
      { text: '이름', value: 'name', width: '80px' },
      { text: '이메일', value: 'email' },
      { text: '부서', value: 'orgNm', width: '100px' },
      { text: '권한', value: 'admin', width: '70px' },
    ],
    userItems: [],
    page: 1,
    pageCount: 0,
    itemsPerPage: 15,
    orgItems: [],
    selectedUserId: null,
    detailUser: {},
    // 추가 다이얼로그
    addDialogShow: false,
    addForm: { id: '', name: '', email: '', phone: '', password: '', confirmPassword: '', admin: false },
  }),
  created() {
    this.getUserData();
    axios.get(this.$APIURL.base + "api/org/list").then(res => {
      this.orgItems = (res.data || []).map(o => ({ text: o.orgNm, value: o.orgId }));
    });
  },
  methods: {
    getUserData() {
      var self = this;
      self.isLoading = true;
      axios.get(this.$APIURL.base + "api/login/getUserList")
        .then(function(res) {
          self.userItems = res.data || [];
          // 15건씩. 삭제 등으로 줄어 지금 페이지가 사라졌으면 마지막 페이지로
          self.pageCount = Math.ceil(self.userItems.length / self.itemsPerPage);
          if (self.page > self.pageCount) self.page = Math.max(1, self.pageCount);
        })
        .catch(function() {
          self.$swal.fire({ title: '사용자 목록 로드 실패', icon: 'error', confirmButtonText: '확인' });
        })
        .finally(function() { self.isLoading = false; });
    },
    selectUser(item) {
      this.selectedUserId = item.id;
      this.detailUser = Object.assign({}, item, { newPassword: '' });
    },
    saveUser() {
      var self = this;
      var data = {
        id: self.detailUser.id,
        name: self.detailUser.name,
        email: self.detailUser.email,
        phone: self.detailUser.phone,
        admin: self.detailUser.admin,
      };
      if (self.detailUser.newPassword) {
        data.password = self.$encodePw(self.detailUser.newPassword);
      }
      var orig = self.userItems.find(function(u) { return u.id === self.detailUser.id; }) || {};
      var orgChanged = (orig.orgId || null) !== (self.detailUser.orgId || null);
      axios.post(this.$APIURL.base + "api/login/updateUser", data)
        .then(function(res) {
          if (res.data && res.data.resultCode === 200) {
            // 104번 WP1 — 부서는 따로 저장
            var after = orgChanged
              ? axios.post(self.$APIURL.base + "api/org/assignUser", { userId: self.detailUser.id, orgId: self.detailUser.orgId || null })
              : Promise.resolve({ data: { resultCode: 200 } });
            return after.then(function(r2) {
              if (r2.data && r2.data.resultCode === 200) {
                self.$swal.fire({ title: '저장 완료', icon: 'success', timer: 1500, showConfirmButton: false });
              } else {
                self.$swal.fire({ title: '부서 저장 실패', text: r2.data && r2.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
              }
              self.getUserData();
            });
          } else {
            self.$swal.fire({ title: '저장 실패', text: res.data && res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        })
        .catch(function() {
          self.$swal.fire({ title: '저장 실패', icon: 'error', confirmButtonText: '확인' });
        });
    },
    showAddDialog() {
      this.addForm = { id: '', name: '', email: '', phone: '', password: '', confirmPassword: '', admin: false };
      this.addDialogShow = true;
    },
    createUser() {
      var self = this;
      if (!self.addForm.id || !self.addForm.name || !self.addForm.password) {
        self.$swal.fire({ title: '계정, 이름, 비밀번호는 필수입니다.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      if (self.addForm.password !== self.addForm.confirmPassword) {
        self.$swal.fire({ title: '비밀번호가 일치하지 않습니다.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      axios.post(this.$APIURL.base + "api/login/createUser", {
        id: self.addForm.id, password: self.$encodePw(self.addForm.password),
        name: self.addForm.name, email: self.addForm.email, phone: self.addForm.phone, admin: self.addForm.admin
      }).then(function(res) {
        if (res.data.resultCode === 200) {
          self.addDialogShow = false;
          self.$swal.fire({ title: '사용자 추가 완료', icon: 'success', timer: 1500, showConfirmButton: false });
          self.getUserData();
        } else {
          self.$swal.fire({ title: '추가 실패', text: res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
        }
      }).catch(function() {
        self.$swal.fire({ title: '추가 실패', icon: 'error', confirmButtonText: '확인' });
      });
    },
    deleteUser() {
      var self = this;
      if (!self.selectedUserId) return;
      self.$swal.fire({
        title: "'" + self.selectedUserId + "' 사용자를 삭제합니까?",
        icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
      }).then(function(result) {
        if (!result.isConfirmed) return;
        var form = new FormData();
        form.append("id", self.selectedUserId);
        axios.post(self.$APIURL.base + "api/login/removeUser", form)
          .then(function(res) {
            if (res.data) {
              self.$swal.fire({ title: '삭제 완료', icon: 'success', timer: 1500, showConfirmButton: false });
              self.selectedUserId = null;
              self.detailUser = {};
              self.getUserData();
            } else {
              self.$swal.fire({ title: '삭제 실패', icon: 'error', confirmButtonText: '확인' });
            }
          })
          .catch(function(error) {
            // 권한 없음·내장 관리자 등 서버가 거부한 사유 (110번 11.7-11b)
            var reason = error.response && error.response.data && error.response.data.message;
            self.$swal.fire({ title: '삭제 실패', text: reason, icon: 'error', confirmButtonText: '확인' });
          });
      });
    },
  },
}
</script>

<style scoped>
</style>
