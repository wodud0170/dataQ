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
import { eventBus } from './../../eventBus.js'

export default {
    name: 'NdHeader',
    props: ['isMobile', 'drawer','tabs'],
    data: () => ({
        searchKeyword: '',
    }),
    methods: {
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
            this.$swal.fire({
                title: '로그아웃 되었습니다.',
                confirmButtonText: '확인',
                confirmButtonColor: '#3F51B5',
                icon: 'success',
            }).then((result) => {
                if (result.isConfirmed) {
                    // 세션 삭제
                    sessionStorage.removeItem("loginStatus");
                    // 확인 버튼 클릭 후 동작 지정
                    location.href = "/";
                }
            });
        },
        goToMain() {
            window.location.href = '/app/main'
        },
        goToUserPage() {
            this.addTabItem('사용자', 'user');
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