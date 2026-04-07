<template>
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
        <v-col cols="8"><v-text-field v-model="userPhone" dense outlined hide-details color="ndColor" placeholder="010-0000-0000"></v-text-field></v-col>
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
        <v-col cols="8"><v-text-field v-model="oldPassword" dense outlined hide-details type="password" color="ndColor"></v-text-field></v-col>
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
          <v-btn color="red" dark @click="changePassword" :loading="changingPw"
            :disabled="!oldPassword || !newPassword || newPassword !== confirmPassword">
            <v-icon small class="mr-1">mdi-lock-check</v-icon> 비밀번호 변경
          </v-btn>
        </v-col>
      </v-row>
    </v-card>
  </v-container>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSMyProfile',
  props: ['isMobile'],
  data() {
    return {
      userId: '',
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
      axios.get(this.$APIURL.base + 'api/login/getUser').then(res => {
        if (res.data) {
          this.userId = res.data.id || '';
          this.userEmail = res.data.email || '';
          this.userPhone = res.data.phone || '';
          this.isAdmin = res.data.admin || false;
        }
      });
    },
    saveProfile() {
      this.saving = true;
      let formData = new FormData();
      formData.append('id', this.userId);
      formData.append('email', this.userEmail || '');
      formData.append('phone', this.userPhone || '');
      axios.post(this.$APIURL.base + 'api/login/updateUser', formData)
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
      let encoded_old = btoa(this.oldPassword);
      let encoded_new = btoa(this.newPassword);
      axios.get(this.$APIURL.base + 'api/login/changePassword', {
        params: { user: this.userId, oldPassword: encoded_old, newPassword: encoded_new }
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
