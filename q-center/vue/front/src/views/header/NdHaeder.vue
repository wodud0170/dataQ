<template>
    <v-app-bar app clipped-left elevation="2" color="white" class="modern-header">
        <!-- 모바일 화면에서 네비게이션 바를 보일 수 있도록 합니다. 모바일 외의 화면에서는 네비게이션 바가 늘 열려있습니다. -->
        <v-app-bar-nav-icon v-cloak v-show="isMobile" v-on:click="navIconClick" color="ndColor"></v-app-bar-nav-icon>

        <!-- header의 logo 이미지 입니다. 모바일 이외의 화면에서 최대 높이와 최대 넓이를 지정합니다. -->
        <v-img v-cloak v-show="!isMobile" lazy-src="" max-height="36" max-width="180"
            src="./../../../static/images/logo_font_200x36.svg" class="header_logo" v-on:click="goToMain"></v-img>

        <!-- header의 logo 이미지 입니다. 모바일 화면에서 최대 높이와 최대 넓이를 지정합니다. -->
        <v-img v-cloak v-show="isMobile" lazy-src="" max-height="26" max-width="100"
            src="./../../../static/images/logo_font_200x36.svg" class="header_logo" v-on:click="goToMain"></v-img>

        <v-spacer />

        <!-- 통합 검색 -->
        <v-text-field
            v-model="searchKeyword"
            placeholder="통합 검색"
            prepend-inner-icon="mdi-magnify"
            dense hide-details outlined single-line
            class="search-field mr-3"
            style="max-width: 260px;"
            @keyup.enter="executeSearch"
            @click:prepend-inner="executeSearch"
            clearable
        ></v-text-field>

        <span v-show="!isMobile" class="mr-3" style="font-size:.8rem; color:#455A64;">
            로그인 사용자 : <strong>{{ loginUserId }}</strong> 님
            <v-chip x-small :color="loginIsAdmin ? 'indigo' : 'grey'" text-color="white" class="ml-1">{{ loginIsAdmin ? '관리자' : '일반' }}</v-chip>
        </span>

        <!-- 104번 WP1 — 알림 (결재 요청·승인·반려) -->
        <v-menu bottom left offset-y :close-on-content-click="false" v-model="notiMenu" max-width="380" transition="slide-y-transition">
            <template v-slot:activator="{ on }">
                <v-btn icon v-on="on" class="mr-2" title="알림" id="headerNotiBtn" @click="loadNotis">
                    <v-badge :value="unreadCnt > 0" :content="unreadCnt > 99 ? '99+' : unreadCnt" color="red" overlap>
                        <v-icon color="ndColor">mdi-bell-outline</v-icon>
                    </v-badge>
                </v-btn>
            </template>
            <v-card min-width="320" class="noti-card">
                <div class="d-flex align-center px-3 py-2" style="border-bottom:1px solid #eee;">
                    <strong style="font-size:.85rem;">알림</strong>
                    <v-spacer />
                    <v-btn x-small text color="primary" :disabled="unreadCnt === 0" @click="readAll">모두 읽음</v-btn>
                </div>
                <div v-if="notis.length === 0" class="pa-4 text-center grey--text" style="font-size:.8rem;">알림이 없습니다.</div>
                <v-list dense class="py-0" style="max-height:420px; overflow:auto;">
                    <v-list-item v-for="n in notis" :key="n.notiId" @click="openNoti(n)" :class="{ 'noti-unread': n.readYn === 'N' }">
                        <v-list-item-content>
                            <v-list-item-title style="font-size:.8rem;">{{ n.title }}</v-list-item-title>
                            <v-list-item-subtitle style="font-size:.72rem; white-space:normal;">{{ n.msg }}</v-list-item-subtitle>
                            <v-list-item-subtitle style="font-size:.68rem;">{{ fmtDt(n.cretDt) }}</v-list-item-subtitle>
                        </v-list-item-content>
                    </v-list-item>
                </v-list>
            </v-card>
        </v-menu>

        <v-menu bottom rounded offset-y :max-width="isMobile ? '120px' : '150px'" transition="slide-y-transition">
            <template v-slot:activator="{ on }">
                <!-- header의 오른쪽 아바타입니다. 모바일 이외의 화면에서 사이즈를 지정합니다. -->
                <v-avatar v-show="!isMobile" color="ndColor" size="38" v-on="on" class="header-avatar">
                    <v-icon v-cloak dark>mdi-account-circle</v-icon>
                </v-avatar>
                <!-- header의 오른쪽 아바타입니다. 모바일 화면에서 사이즈를 지정합니다. -->
                <v-avatar v-show="isMobile" color="ndColor" size="30" v-on="on" class="header-avatar">
                    <v-icon v-cloak dark>mdi-account-circle</v-icon>
                </v-avatar>
            </template>
            <!-- 아바타 버튼을 클릭했을 때 노출되는 card -->
            <v-card class="user-menu-card pa-1">
                <v-list-item-content class="justify-center">
                    <!-- 버튼 영역 -->
                    <v-btn depressed rounded text v-on:click="goToUserPage" class="user-menu-btn">
                        <v-icon small class="mr-2">mdi-account</v-icon>
                        User Info
                    </v-btn>
                    <!-- 가름선 -->
                    <v-divider class="my-1"></v-divider>
                    <!-- 버튼 -->
                    <v-btn depressed rounded text v-on:click="showAlert" class="user-menu-btn">
                        <v-icon small class="mr-2">mdi-logout</v-icon>
                        Logout
                    </v-btn>
                </v-list-item-content>
            </v-card>
        </v-menu>


    </v-app-bar>
</template>

<script>
import axios from 'axios';
import { eventBus } from './../../eventBus.js'

export default {
    name: 'NdHeader',
    props: ['isMobile', 'drawer','tabs'],
    data: () => ({
        searchKeyword: '',
        loginUserId: '',
        loginIsAdmin: false,
        notiMenu: false,
        notis: [],
        unreadCnt: 0,
        notiTimer: null,
    }),
    mounted() {
        var self = this;
        var loginData = JSON.parse(sessionStorage.getItem('loginStatus'));
        if (loginData && loginData.id) {
            self.loginUserId = loginData.id;
            axios.get(self.$APIURL.base + 'api/login/isAdmin', { params: { user: loginData.id } })
                .then(function(res) { self.loginIsAdmin = res.data === true; });
        }
        // 104번 WP1 — 알림: 실시간(WebSocket) + 1분마다 개수 (q-executor 엑셀 일괄등록이 만든 알림은 실시간 전송이 없다.
        // 110 4.12-41-b 재수집 자동 반려 알림은 q-executor 가 커밋 뒤 같은 사용자 목적지로 보낸다)
        this.loadUnread();
        this.notiTimer = setInterval(this.loadUnread, 60000);
        eventBus.$on('USER_NOTI', this.onUserNoti);
    },
    beforeDestroy() {
        clearInterval(this.notiTimer);
        eventBus.$off('USER_NOTI', this.onUserNoti);
    },
    methods: {
        loadUnread() {
            axios.get(this.$APIURL.base + 'api/noti/unreadCount').then(res => {
                this.unreadCnt = typeof res.data === 'number' ? res.data : 0;
            }).catch(() => { /* 세션 만료 등은 공통 401 처리가 맡는다 */ });
        },
        loadNotis() {
            axios.get(this.$APIURL.base + 'api/noti/list').then(res => {
                this.notis = Array.isArray(res.data) ? res.data : [];
            });
        },
        onUserNoti(msg) {
            this.unreadCnt++;
            if (this.notiMenu) this.loadNotis();
            this.$swal.fire({ toast: true, position: 'top-end', timer: 4000, showConfirmButton: false,
                icon: msg.notiTp === 'APRV_REJECT' ? 'warning' : 'info', title: msg.title, text: msg.msg });
            eventBus.$emit('APRV_CHANGED');
        },
        readAll() {
            axios.post(this.$APIURL.base + 'api/noti/read', {}).then(() => {
                this.unreadCnt = 0;
                this.notis.forEach(n => { n.readYn = 'Y'; });
            });
        },
        openNoti(n) {
            if (n.readYn === 'N') {
                axios.post(this.$APIURL.base + 'api/noti/read', { notiIds: [n.notiId] }).then(() => {
                    n.readYn = 'Y';
                    this.unreadCnt = Math.max(0, this.unreadCnt - 1);
                });
            }
            this.notiMenu = false;
            if (n.linkTp === 'APRV_DOC') {
                eventBus.pendingAprvDoc = n.linkId;
                this.addTabItem('결재함', 'aprvBox');
                this.$nextTick(() => eventBus.$emit('openAprvDoc'));
            }
        },
        fmtDt(dt) {
            if (!dt || dt.length < 12) return dt || '';
            return dt.substr(0, 4) + '-' + dt.substr(4, 2) + '-' + dt.substr(6, 2) + ' ' + dt.substr(8, 2) + ':' + dt.substr(10, 2);
        },
        navIconClick() {
            if (this.isMobile) {
                // this.drawer의 초깃값은 null이므로 null이 들어오면 무조건 열어주는 true를 넘겨준다.
                if (this.drawer === null) {
                    this.$emit('navIconClick', true)
                } else {
                    this.$emit('navIconClick', !this.drawer)
                }
            }
        },
        showAlert() {
            window._skipBeforeUnload = true;
            // 서버 세션 종료 (Spring Security /logout) 후 로컬 세션 삭제
            axios.get(this.$APIURL.base + "logout").finally(() => {
                sessionStorage.removeItem("loginStatus");
                // 로그인 화면으로 이동
                location.href = "/";
            });
        },
        goToMain() {
            this.$swal.fire({
                title: '메인으로 이동',
                text: '열려있는 모든 탭이 닫히며, 작업중인 내용은 저장되지 않습니다.',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: '이동',
                cancelButtonText: '취소'
            }).then((result) => {
                if (result.isConfirmed) {
                    window._skipBeforeUnload = true;
                    window.location.href = '/app/main';
                }
            });
        },
        goToUserPage() {
            this.addTabItem('내 정보', 'myProfile');
        },
        executeSearch() {
            if (!this.searchKeyword || !this.searchKeyword.trim()) return;
            const keyword = this.searchKeyword.trim();
            this.addTabItem('통합 검색', 'globalSearch');
            this.$nextTick(() => {
                eventBus.$emit('globalSearch', keyword);
            });
        },
        addTabItem(title, name) {
            // 탭에 현재 클릭한 메뉴가 있는지 확인하여 있으면 해당 탭으로 이동, 없으면 탭 추가
            let tab = this.tabs.find(item => item.name === name);

            if (!tab) {
                let _index = this.tabs.length;
                this.$emit('addTabItem', title, name, _index);
            } else {
                let _tab = this.tabs.find(item => item.name === name);
                this.$emit('addActiveContent', name, _tab.index);
            }

            // 탭과 네비게이션을 동기화
            this.$emit('navAndTabSync', name);
        },
    },
}
</script>
<style scoped>
.modern-header {
    border-bottom: 1px solid #E8EAF6 !important;
}
.noti-unread {
    background: #E8EAF6;
}
.header-avatar {
    cursor: pointer;
    transition: opacity 0.2s ease;
}
.header-avatar:hover {
    opacity: 0.85;
}
.user-menu-card {
    border-radius: 12px !important;
    overflow: hidden;
}
.user-menu-btn {
    width: 100% !important;
    justify-content: flex-start !important;
    font-size: 0.85rem;
    text-transform: none;
    border-radius: 8px !important;
}
</style>