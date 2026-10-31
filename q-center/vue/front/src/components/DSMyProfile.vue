<template>
  <!-- 탭 영역 안에서 세로 스크롤 — 1366x768 에서 비밀번호 변경 칸·버튼이 화면 밖에 잘리지 않게 -->
  <div style="height:100%; overflow-y:auto;">
  <v-container fluid class="pa-6" style="max-width:700px;">
    <v-card outlined class="pa-6">
      <div class="d-flex align-center mb-4">
        <v-icon large color="primary" class="mr-3">mdi-account-circle</v-icon>
        <div>
          <div class="text-h6 font-weight-bold">내 정보</div>
          <div class="text-caption grey--text">계정 정보를 확인하고 수정할 수 있습니다.</div>
        </div>
        <v-spacer></v-spacer>
        <v-chip small :color="isAdmin ? 'indigo' : 'grey'" text-color="white">{{ isAdmin ? '관리자' : '일반 사용자' }}</v-chip>
      </div>
      <v-divider class="mb-4"></v-divider>

      <v-row>
        <v-col cols="4"><v-subheader class="font-weight-bold">아이디</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="userId" dense outlined hide-details disabled></v-text-field></v-col>
      </v-row>
      <v-row>
        <v-col cols="4"><v-subheader class="font-weight-bold">이메일</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="userEmail" dense outlined hide-details color="ndColor" placeholder="user@example.com"></v-text-field></v-col>
      </v-row>
      <v-row>
        <v-col cols="4"><v-subheader class="font-weight-bold">전화번호</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="userPhone" dense outlined hide-details color="ndColor" placeholder="010-0000-0000" autocomplete="off"></v-text-field></v-col>
      </v-row>
      <!-- 104번 WP2 — 받은 역할 (관리자는 모든 권한이라 따로 보이지 않는다) -->
      <v-row v-if="!isAdmin">
        <v-col cols="4"><v-subheader class="font-weight-bold">역할</v-subheader></v-col>
        <v-col cols="8" class="d-flex flex-wrap align-center" style="gap:6px;">
          <span v-if="$perm.roles.length === 0" class="text-caption grey--text">받은 역할이 없습니다.</span>
          <v-chip v-for="r in $perm.roles" :key="r.roleId + r.sysCd" small outlined color="indigo">
            {{ r.roleNm }}<span v-if="r.sysNm" class="ml-1 grey--text">· {{ r.sysNm }}</span>
          </v-chip>
        </v-col>
      </v-row>

      <v-row class="mt-2">
        <v-col class="text-right">
          <v-btn color="ndColor" class="white--text" @click="saveProfile" :loading="saving">
            <v-icon small class="mr-1">mdi-content-save</v-icon> 저장
          </v-btn>
        </v-col>
      </v-row>
    </v-card>

    <!-- 비밀번호 변경 -->
    <v-card outlined class="pa-6 mt-4">
      <div class="text-subtitle-1 font-weight-bold mb-3">
        <v-icon small class="mr-1">mdi-lock-reset</v-icon> 비밀번호 변경
      </div>
      <v-divider class="mb-4"></v-divider>
      <v-row>
        <v-col cols="4"><v-subheader>현재 비밀번호</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="oldPassword" dense outlined hide-details type="password" color="ndColor" autocomplete="new-password"></v-text-field></v-col>
      </v-row>
      <v-row>
        <v-col cols="4"><v-subheader>새 비밀번호</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="newPassword" dense outlined hide-details type="password" color="ndColor"></v-text-field></v-col>
      </v-row>
      <v-row>
        <v-col cols="4"><v-subheader>비밀번호 확인</v-subheader></v-col>
        <v-col cols="8"><v-text-field v-model="confirmPassword" dense outlined type="password" color="ndColor"
          :hide-details="!confirmPassword || confirmPassword === newPassword"
          :error-messages="confirmPassword && confirmPassword !== newPassword ? '비밀번호가 일치하지 않습니다.' : ''"></v-text-field></v-col>
      </v-row>
      <v-row class="mt-2">
        <v-col class="text-right">
          <!-- btn-auto: 전역 100px 고정이면 '비밀번호 변경' 글자가 잘린다. dark 를 빼야 비활성일 때 흰 카드 위에서 보인다 -->
          <v-btn color="red" class="white--text btn-auto" @click="changePassword" :loading="changingPw"
            :disabled="!oldPassword || !newPassword || newPassword !== confirmPassword">
            <v-icon small class="mr-1">mdi-lock-check</v-icon> 비밀번호 변경
          </v-btn>
        </v-col>
      </v-row>
    </v-card>
  </v-container>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSMyProfile',
  props: ['isMobile'],
  data() {
    return {
      userId: '',
      userName: null,
      userEmail: '',
      userPhone: '',
      isAdmin: false,
      saving: false,
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
      changingPw: false,
    };
  },
  created() {
    this.loadProfile();
  },
  methods: {
    loadProfile() {
      var self = this;
      axios.get(self.$APIURL.base + 'api/login/getUserDetail', { params: { userId: self.$loginStatusData.id } }).then(function(res) {
        if (res.data) {
          self.userId = res.data.id || '';
          self.userName = res.data.name;
          self.isAdmin = res.data.admin || false;
          self.userEmail = res.data.email || '';
          self.userPhone = res.data.phone || '';
        }
      });
    },
    saveProfile() {
      this.saving = true;
      // updateAdmin 은 NM/ADM_YN 까지 덮어쓰므로 관리자 여부는 지금 값을 보낸다. 탭을 연 뒤 관리자 지정·해제가 바뀌었을 수 있어
      // 저장 직전에 다시 읽는다 — 탭을 열 때 읽은 값을 보내면 관리자 지정이 말없이 풀린다 (110번 11.7-14)
      axios.get(this.$APIURL.base + 'api/login/getUserDetail', { params: { userId: this.userId } })
        .then(cur => {
          this.isAdmin = cur.data.admin || false;
          return axios.post(this.$APIURL.base + 'api/login/updateUser', {
            id: this.userId,
            name: this.userName,
            admin: this.isAdmin,
            email: this.userEmail || '',
            phone: this.userPhone || ''
          });
        })
        .then(res => {
          if (res.data.resultCode === 200) {
            this.$swal.fire({ title: '저장되었습니다.', icon: 'success', timer: 1500, showConfirmButton: false });
          } else {
            this.$swal.fire({ title: '저장 실패', text: res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        })
        .catch(() => {
          this.$swal.fire({ title: '서버 오류', icon: 'error', confirmButtonText: '확인' });
        })
        .finally(() => { this.saving = false; });
    },
    changePassword() {
      if (this.newPassword !== this.confirmPassword) return;
      this.changingPw = true;
      let encoded_old = this.$encodePw(this.oldPassword);
      let encoded_new = this.$encodePw(this.newPassword);
      axios.post(this.$APIURL.base + 'api/login/changePassword', {
        oldPassword: encoded_old, newPassword: encoded_new
      }).then(res => {
        if (res.data.resultCode === 200) {
          this.$swal.fire({ title: '비밀번호가 변경되었습니다.', icon: 'success', confirmButtonText: '확인' });
          this.oldPassword = '';
          this.newPassword = '';
          this.confirmPassword = '';
        } else {
          this.$swal.fire({ title: '변경 실패', text: res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
        }
      }).catch(() => {
        this.$swal.fire({ title: '서버 오류', icon: 'error', confirmButtonText: '확인' });
      }).finally(() => { this.changingPw = false; });
    }
  }
};
</script>
