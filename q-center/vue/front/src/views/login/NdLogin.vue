<template>
    <v-main class="main">
        <v-container d-flex justify-center align-center align-self-center mb-6 class="wrapper">
            <v-card class="login_card" :class="{ 'login_card_mobile': isMobile, 'login_card_not_mobile': !isMobile }">
                <!-- logo container -->
                <v-container d-flex justify-center align-center
                    :class="{ 'pa-5': $vuetify.breakpoint.xs, 'pa-5': !$vuetify.breakpoint.sm, 'pa-10': $vuetify.breakpoint.xl }">
                    <!-- header의 logo 이미지 입니다. 모바일 이외의 화면에서 최대 높이와 최대 넓이를 지정합니다. -->
                    <v-img v-show="!isMobile" lazy-src="" max-height="36" max-width="200"
                        src="./../../../static/images/logo_font_200x36.png"></v-img>

                    <!-- header의 logo 이미지 입니다. 모바일 화면에서 최대 높이와 최대 넓이를 지정합니다. -->
                    <v-img v-show="isMobile" lazy-src="" max-height="36" max-width="200"
                        src="./../../../static/images/logo_font_200x36.png"></v-img>
                </v-container>

                <!-- form container -->
                <v-form>
                    <v-container>
                        <!-- <v-row class="pa-10"> -->
                        <v-row
                            :class="{ 'pa-5': $vuetify.breakpoint.xs, 'pa-5': !$vuetify.breakpoint.sm, 'pa-10': $vuetify.breakpoint.xl }">
                            <v-col cols="12">
                                <v-text-field v-model="user_id" color="ndColor" label="ID" placeholder="" required=""
                                    autocomplete="off" outlined :rules="[() => !!user_id || 'ID를 입력해주세요.']"
                                    :error-messages="errorMessages" :style="{fontSize:'1.25rem'}">
                                </v-text-field>
                            </v-col>
                            <v-col cols="12">
                                <v-text-field v-model="user_pw" type="password" color="ndColor" label="PASSWORD"
                                    placeholder="" required="" autocomplete="off" outlined :style="{fontSize:'1.25rem'}"
                                    :rules="[() => !!user_pw || '비밀번호를 입력해주세요.']" :error-messages="errorMessages" v-on:keyup.enter="tryLogin()">
                                </v-text-field>
                            </v-col>
                            <v-col cols="12">
                                <v-btn block x-large elevation="2" color="ndColor" class="login_btn white--text"
                                    :style="{ height: '60px !important' }" v-on:click="tryLogin()">LOGIN</v-btn>
                            </v-col>
                        </v-row>
                    </v-container>
                </v-form>
                <v-card-text class="text-center narae_link card_text_custom"><a href="http://naraedata.com"
                        target="_blank">http://naraedata.com</a></v-card-text>
                <v-card-text class="text-center card_text_custom">Copyright © 2021 NARAE DATA All Rights
                    Reserved.</v-card-text>
            </v-card>

        </v-container>
    </v-main>
</template>
  
<script>
import axios from "axios";

export default {
    name: "NdLogin",
    props: ['isMobile'],
    data: () => ({
        user_id: null,
        user_pw: null,
        errorMessages: ''
    }),
    watch: {
        user_id() {
            this.errorMessages = ''
        }
    },
    methods: {
        tryLogin: function () {
            const data = {
                id: this.user_id,
                password: this.user_pw
            };

            let href = location.href;
            let isLogpage = href.indexOf("/signin") > -1;

            // logout
            if (!data) {
                sessionStorage.setItem(
                    "loginStatus",
                    JSON.stringify({ id: null, authed: false })
                );

                axios
                    .get(this.$APIURL.base + "logout")
                    .then(Response => {
                        location.href = "/signin";
                    })
                    .catch(Error => {
                        console.log(Error);
                    });
            }

            if (!data.id || !data.password) {
                this.$swal
                    .fire({
                        title: "아이디와 패스워드를 입력해주세요.",
                        confirmButtonText: "확인",
                        icon: "error"
                    })
                    .then(result => {
                        if (result.isConfirmed) {
                            return false;
                        }
                    });
                return false;
            }

            const loginData = new FormData();
            loginData.append("id", data.id);
            loginData.append("password", btoa(data.password));

            let _url = this.$APIURL.base + "login";

            let axiosHeader = {
                headers: {
                    "Content-Type": "multipart/form-data"
                }
            };

            axios
                .post(_url, loginData, axiosHeader)
                .then(result => {
                    console.log(result)

                    if (result.data.success) {
                        sessionStorage.setItem(
                            "loginStatus",
                            JSON.stringify({ id: data.id, authed: true })
                        );

                        if (isLogpage) {
                        location.href = "/app/main";
                        }

                    } else {
                        // 경고 메세지
                        let msg = result.data.message;

                        this.$swal
                            .fire({
                                title: msg,
                                confirmButtonText: "확인",
                                icon: "error"
                            })
                            .then(result => {
                                if (result.isConfirmed) {
                                    this.user_pw = "";
                                }
                            });
                    }
                })
                .catch(() => {
                    console.error("LoginView : rest error");
                });
        },
    }
};
</script>
  
<style scoped>
.main {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    height: 100vh !important;
    background-image: url('./../../../static/images/bg.jpg');
    background-position: center;
    background-repeat: no-repeat;
    background-size: cover;
}

.wrapper {
    height: 100vh;
}

.login_card {
    background-color: rgba(255, 255, 255, 0.9);
    height: 90%;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    flex-wrap: nowrap;
    align-content: center;
    justify-content: center;
    align-items: center;
}

.login_card_mobile {
    width: 90%;
    transition: 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.login_card_not_mobile {
    width: 50%;
    transition: 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.narae_link a {
    color: rgba(0, 0, 0, 0.6);
}

.card_text_custom {
    letter-spacing: 0.0892857143em;
    font-size: medium;
}

/* 모바일 가로 CSS */
@media screen and (orientation: landscape) and (max-width: 916px) {
    .login_card {
        flex-direction: row;
        align-content: center;
        justify-content: center;
        align-items: center;
        padding: 4vw 4vw 4vw 2vw;
        transition: 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        width: 80%;
    }

    .text-center {
        display: none;
    }
}
</style>
  