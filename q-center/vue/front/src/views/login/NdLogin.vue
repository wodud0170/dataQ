<template>
    <v-main class="login-main">
        <div class="login-wrapper">
            <!-- 좌측: 비주얼 패널 (데스크톱만) -->
            <div v-if="!isMobile" class="login-visual">
                <div class="orb orb-1"></div>
                <div class="orb orb-2"></div>
                <div class="orb orb-3"></div>
                <div class="orb orb-4"></div>
                <div class="grid-overlay"></div>

                <!-- 일러스트 + 문구 -->
                <div class="illust-wrap">
                    <svg viewBox="0 0 400 340" fill="none" xmlns="http://www.w3.org/2000/svg" class="illust-svg">
                        <!-- 메인 모니터 -->
                        <rect x="60" y="40" width="280" height="190" rx="14" fill="rgba(255,255,255,0.08)" stroke="rgba(255,255,255,0.15)" stroke-width="1.5"/>
                        <rect x="60" y="40" width="280" height="32" rx="14" fill="rgba(255,255,255,0.05)"/>
                        <rect x="60" y="60" width="280" height="12" fill="rgba(255,255,255,0.05)"/>
                        <!-- 모니터 받침 -->
                        <rect x="170" y="230" width="60" height="16" rx="2" fill="rgba(255,255,255,0.06)"/>
                        <rect x="140" y="244" width="120" height="6" rx="3" fill="rgba(255,255,255,0.08)"/>

                        <!-- 윈도우 닫기 버튼 -->
                        <circle cx="82" cy="56" r="4" fill="#EF5350" opacity="0.7"/>
                        <circle cx="96" cy="56" r="4" fill="#FFC107" opacity="0.7"/>
                        <circle cx="110" cy="56" r="4" fill="#66BB6A" opacity="0.7"/>

                        <!-- 차트: 바 그래프 -->
                        <rect x="85" y="160" width="24" height="50" rx="4" fill="#5C6BC0" opacity="0.6"/>
                        <rect x="118" y="130" width="24" height="80" rx="4" fill="#7986CB" opacity="0.7"/>
                        <rect x="151" y="145" width="24" height="65" rx="4" fill="#9FA8DA" opacity="0.6"/>
                        <rect x="184" y="110" width="24" height="100" rx="4" fill="#81D4FA" opacity="0.8"/>
                        <rect x="217" y="125" width="24" height="85" rx="4" fill="#7986CB" opacity="0.7"/>
                        <rect x="250" y="98" width="24" height="112" rx="4" fill="#5C6BC0" opacity="0.8"/>
                        <rect x="283" y="140" width="24" height="70" rx="4" fill="#9FA8DA" opacity="0.6"/>

                        <!-- 체크 배지 -->
                        <circle cx="310" cy="88" r="20" fill="#66BB6A" opacity="0.9"/>
                        <polyline points="300,88 307,96 322,80" fill="none" stroke="white" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>

                        <!-- 플로팅 카드 (왼쪽) -->
                        <g class="float-card-1">
                            <rect x="16" y="130" width="70" height="55" rx="10" fill="rgba(129,212,250,0.12)" stroke="rgba(129,212,250,0.25)" stroke-width="1"/>
                            <text x="51" y="155" font-size="9" fill="rgba(255,255,255,0.5)" font-family="sans-serif" text-anchor="middle">적합률</text>
                            <text x="51" y="174" font-size="16" fill="#81D4FA" font-family="sans-serif" font-weight="700" text-anchor="middle">94%</text>
                        </g>

                        <!-- 플로팅 카드 (오른쪽) -->
                        <g class="float-card-2">
                            <rect x="318" y="160" width="70" height="55" rx="10" fill="rgba(165,214,167,0.12)" stroke="rgba(165,214,167,0.25)" stroke-width="1"/>
                            <text x="353" y="185" font-size="9" fill="rgba(255,255,255,0.5)" font-family="sans-serif" text-anchor="middle">진단완료</text>
                            <text x="353" y="204" font-size="16" fill="#A5D6A7" font-family="sans-serif" font-weight="700" text-anchor="middle">37건</text>
                        </g>

                        <!-- 사람 (간략한 실루엣) -->
                        <!-- 머리 -->
                        <circle cx="200" cy="278" r="14" fill="rgba(255,255,255,0.12)"/>
                        <!-- 몸 -->
                        <path d="M175 310 Q177 294, 200 292 Q223 294, 225 310 L225 340 L175 340 Z" fill="rgba(255,255,255,0.08)" rx="8"/>
                        <!-- 노트북 -->
                        <rect x="165" y="314" width="70" height="6" rx="2" fill="rgba(92,107,192,0.3)"/>
                        <rect x="162" y="320" width="76" height="4" rx="2" fill="rgba(92,107,192,0.15)"/>
                    </svg>

                    <div class="visual-text">
                        <h2 class="visual-headline">데이터 품질,<br/>자동으로 관리하세요</h2>
                        <div class="visual-points">
                            <div class="visual-point">
                                <span class="point-dot" style="background:#81D4FA;"></span>
                                표준 용어·단어·도메인 통합 관리
                            </div>
                            <div class="visual-point">
                                <span class="point-dot" style="background:#A5D6A7;"></span>
                                자동 표준화 추천 및 등록
                            </div>
                            <div class="visual-point">
                                <span class="point-dot" style="background:#CE93D8;"></span>
                                데이터 모델 품질 진단 및 리포트
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 우측: 로그인 폼 -->
            <div class="login-form-area" :class="{ 'login-form-mobile': isMobile }">
                <div class="login-form-inner">
                    <img src="./../../../static/images/logo_font_200x36.svg"
                        :style="isMobile ? 'height:40px' : 'height:48px'" class="login-logo" />

                    <p class="login-sub">데이터 품질 관리 플랫폼</p>

                    <v-form class="login-form">
                        <v-text-field v-model="user_id" color="#3F51B5"
                            placeholder="아이디" required
                            autocomplete="off" solo flat
                            :rules="[() => !!user_id || 'ID를 입력해주세요.']"
                            :error-messages="errorMessages"
                            prepend-inner-icon="mdi-account-outline"
                            background-color="#F0F1F5"
                            class="login-input"
                            hide-details="auto">
                        </v-text-field>

                        <v-text-field v-model="user_pw" type="password" color="#3F51B5"
                            placeholder="비밀번호" required
                            autocomplete="off" solo flat
                            :rules="[() => !!user_pw || '비밀번호를 입력해주세요.']"
                            :error-messages="errorMessages"
                            prepend-inner-icon="mdi-lock-outline"
                            background-color="#F0F1F5"
                            v-on:keyup.enter="tryLogin()"
                            class="login-input"
                            hide-details="auto">
                        </v-text-field>

                        <v-btn block elevation="0" color="#3F51B5"
                            class="login-btn white--text"
                            v-on:click="tryLogin()">
                            로그인
                        </v-btn>
                    </v-form>

                    <p class="login-footer">
                        © 2025 <a href="http://naraedata.com" target="_blank">Narae Data</a>
                    </p>
                </div>
            </div>
        </div>
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
.login-main {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    width: 100%; height: 100vh !important;
}

.login-wrapper {
    display: flex;
    width: 100%; height: 100vh;
}

/* ── 좌측 비주얼 ── */
.login-visual {
    flex: 0 0 50%;
    background: #080d20;
    position: relative;
    overflow: hidden;
}

.grid-overlay {
    position: absolute;
    inset: 0;
    background-image:
        linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
    background-size: 60px 60px;
}

.orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    animation: float 20s ease-in-out infinite;
}

.orb-1 {
    width: 450px; height: 450px;
    top: -15%; left: -10%;
    background: rgba(63,81,181,0.35);
    animation-delay: 0s;
}

.orb-2 {
    width: 350px; height: 350px;
    bottom: -10%; right: -5%;
    background: rgba(92,107,192,0.25);
    animation-delay: -5s;
    animation-duration: 25s;
}

.orb-3 {
    width: 200px; height: 200px;
    top: 50%; left: 50%;
    transform: translate(-50%, -50%);
    background: rgba(121,134,203,0.2);
    animation-delay: -10s;
    animation-duration: 18s;
}

.orb-4 {
    width: 280px; height: 280px;
    top: 20%; right: 15%;
    background: rgba(159,168,218,0.12);
    animation-delay: -15s;
    animation-duration: 22s;
}

@keyframes float {
    0%, 100% { transform: translate(0, 0) scale(1); }
    25% { transform: translate(30px, -40px) scale(1.05); }
    50% { transform: translate(-20px, 20px) scale(0.95); }
    75% { transform: translate(15px, 35px) scale(1.02); }
}

.illust-wrap {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    z-index: 2;
    gap: 0;
}

.visual-text {
    margin-top: 28px;
    text-align: center;
}

.visual-headline {
    font-size: 1.5rem;
    font-weight: 600;
    color: rgba(255,255,255,0.9);
    line-height: 1.5;
    letter-spacing: -0.01em;
    margin-bottom: 20px;
}

.visual-points {
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: center;
}

.visual-point {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 0.85rem;
    color: rgba(255,255,255,0.55);
    font-weight: 300;
    letter-spacing: 0.02em;
}

.point-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
}

.illust-svg {
    width: 65%;
    max-width: 380px;
    height: auto;
    filter: drop-shadow(0 4px 24px rgba(0,0,0,0.2));
}

.illust-svg .float-card-1 {
    animation: cardFloat 6s ease-in-out infinite;
}

.illust-svg .float-card-2 {
    animation: cardFloat 6s ease-in-out infinite;
    animation-delay: -3s;
}

@keyframes cardFloat {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-8px); }
}

/* ── 우측 폼 ── */
.login-form-area {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #FFFFFF;
}

.login-form-mobile {
    background: linear-gradient(180deg, #FAFBFD 0%, #F0F2F5 100%);
}

.login-form-inner {
    width: 100%;
    max-width: 360px;
    padding: 0 28px;
}

.login-logo {
    display: block;
    margin: 0 auto 12px auto;
}

.login-sub {
    text-align: center;
    font-size: 0.82rem;
    color: #9E9E9E;
    margin-bottom: 36px;
    letter-spacing: 0.04em;
}

.login-form {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.login-input {
    border-radius: 12px;
}

.login-input >>> .v-input__slot {
    border-radius: 12px !important;
    min-height: 50px !important;
    padding: 0 16px !important;
    border: 1.5px solid transparent !important;
    transition: border-color 0.2s, box-shadow 0.2s;
}

.login-input >>> .v-input__slot:hover {
    border-color: #C5CAE9 !important;
}

.login-input >>> input::placeholder {
    color: #BDBDBD !important;
    font-weight: 400;
}

.login-btn {
    border-radius: 12px !important;
    font-size: 0.95rem !important;
    font-weight: 600 !important;
    letter-spacing: 0.03em;
    height: 50px !important;
    margin-top: 4px;
    transition: all 0.2s ease;
}

.login-btn:hover {
    background-color: #283593 !important;
    box-shadow: 0 4px 20px rgba(26,35,126,0.25) !important;
    transform: translateY(-1px);
}

.login-footer {
    text-align: center;
    margin-top: 48px;
    font-size: 0.75rem;
    color: #BDBDBD;
}

.login-footer a {
    color: #9FA8DA;
    text-decoration: none;
}

.login-footer a:hover {
    color: #3F51B5;
}

/* 모바일 가로 */
@media screen and (orientation: landscape) and (max-width: 916px) {
    .login-wrapper { flex-direction: column; }
    .login-visual { display: none; }
}

/* 작은 데스크톱 */
@media screen and (max-width: 1100px) {
    .login-visual { flex: 0 0 40%; }
}
</style>
