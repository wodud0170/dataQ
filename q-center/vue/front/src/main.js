import Vue from "vue"
import axios from "axios"
import App from "./../src/App.vue"
import SockJS from "sockjs-client"
import Stomp from "webstomp-client"
import router from "./router"
import vuetify from "./plugins/vuetify"
import common from "./../static/js/common"
import perm from "./perm"
import VueSplit from "vue-split-panel"
import VueSweetalert2 from "vue-sweetalert2"
import VueApexCharts from 'vue-apexcharts'

import "@mdi/font/css/materialdesignicons.min.css"
import "material-design-icons-iconfont/dist/material-design-icons.css"
import "sweetalert2/dist/sweetalert2.min.css"

Vue.config.productionTip = false;

Vue.use(common);
Vue.use(perm);
Vue.use(VueSplit);
// 104번 WP2 — 권한은 메인 화면 진입 때(router 세션 확인 뒤) 읽는다
Vue.use(VueSweetalert2);
Vue.use(VueApexCharts)

// WP0 — 로그인 세션이 없거나 끊기면 서버는 /api/** 에 401 을 돌려준다(로그인 화면 리다이렉트 아님).
// 어느 화면에서 받든 한 번만 안내하고 로그인 화면으로 보낸다. 로그인 화면 자체에서는 하지 않는다.
// 401 은 화면의 catch 로 넘기지 않는다 — 넘기면 화면마다 띄우는 "서버 오류" 창이 이 안내를 덮어
// 로그인 화면으로 가지 못한다. 곧 페이지를 떠나므로 그 요청은 끝나지 않은 채로 둔다.
let sessionExpiredNotified = false;
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 401 && window.location.pathname !== "/signin") {
      if (!sessionExpiredNotified) {
        sessionExpiredNotified = true;
        sessionStorage.removeItem("loginStatus");
        Vue.swal
          .fire({ title: "로그인이 필요합니다.", confirmButtonText: "확인", icon: "info" })
          .then(() => { window.location.href = "/signin"; });
      }
      return new Promise(() => {});
    }
    return Promise.reject(error);
  }
);

new Vue({
  el: "#app",
  router: router,
  components: { App },
  vuetify,
  template: "<App/>"
});

export default {
  DEVELOP_MODE: Vue.prototype.$devMode,
  APIURLBASE: Vue.prototype.$APIURL.base
};

// eventBus
export { eventBus } from './eventBus.js';

import { eventBus } from './eventBus.js'

// websocket vue
export const wsVue = new Vue({
  data: {
    url: Vue.prototype.$APIURL.base + "sockjs",
    stompClient: null,
    sessionId: null,
    userId: null
  },
  created: function() {
    if ("/signin" !== window.location.pathname) {
      this.connect();
    }
    eventBus.$on("NOTICE", this.onNotice);
    eventBus.$on("RELOAD", this.onReload);
  },
  //mounted: function () {
  //  //this.connect();
  //  eventBus.$on('NOTICE', this.onNotice);
  //  eventBus.$on('RELOAD', this.onReload);
  //},
  methods: {
    connect: function() {
      this.$checkSession(); //세션을 확인해서 세션이 없으면 로그인페이지로 이동

      var socketSessionId = sessionStorage.getItem("socketSessionId");

      if (socketSessionId == null) {
        socketSessionId = (function() {
          var length = 8;
          var _randomStringChars = "abcdefghijklmnopqrstuvwxyz012345";
          var max = _randomStringChars.length;
          var bytes = new Uint8Array(length);
          window.crypto.getRandomValues(bytes);
          var ret = [];
          for (var i = 0; i < length; i++) {
            ret.push(_randomStringChars.substr(bytes[i] % max, 1));
          }
          return ret.join("");
        })();
      }

      // set sessionId, userId
      const loginStatusData = JSON.parse(sessionStorage.getItem("loginStatus"));

      // 로그인 정보가 없을 때 로그인 페이지로 이동
      if (loginStatusData === null) {
        Vue.swal
        .fire({
          title: "로그인이 필요합니다.",
          confirmButtonText: "확인",
          icon: "info"
        })
        .then(result => {
          if (result.isConfirmed) {
            location.href = "/";
          }
        });

        return
      }

      this.sessionId = socketSessionId;
      this.userId = loginStatusData.id;
      // set socketWindow
      var socketWindow = new SockJS(this.url, [], {
        sessionId: function() {
          return socketSessionId;
        },
        heartbeat: false
      });

      // a separate stomp connection with "useSocketSession" header set on connect
      this.stompClient = Stomp.over(socketWindow);
      this.stompClient.debug = () => {};
      this.stompClient.connect(
        { useSocketSession: "true", socketUsername: loginStatusData.id },
        this.stompSuccessCallback,
        this.stompFailureCallback
      );
    },
    stompSuccessCallback(frame) {
      // console.log("success", this.stompClient);
      console.log("STOMP connection established, window session: " + frame);
      //sessionStorage에 socketSessionId를 저장한다.
      sessionStorage.setItem("socketSessionId", this.sessionId);
      this.wsSubcribe();
    },
    stompFailureCallback(error) {
      console.log("Websocket Connected : ", this.stompClient.connected);
      console.log("STOMP close: ", error);

      // -- 2023.03.24 추가
      document.getElementById("logTextWrap").innerHTML += "Websocket Connected : " + this.stompClient.connected + '<br /><br />';
      // -- 2023.03.24 추가
      document.getElementById("logTextWrap").innerHTML +=
        "STOMP close: " + JSON.stringify(error) + '<br /><br />';

      // 연결 끊김/실패 시 재연결 시도 (로그인 상태면 로그아웃하지 않음)
      var loginStatus = sessionStorage.getItem("loginStatus");
      if (loginStatus) {
        this.wsUnsubscribe();
        setTimeout(() => this.connect(), 3000);
        console.log("STOMP: Reconnecting in 3 seconds (session alive)");
      } else {
        console.log("STOMP: no login session, redirect to login page");
        router.push({ path: '/signin' });
      }
    },
    wsSubcribe: function() {
      this.stompClient.subscribe("/topic/onMessage", this.onMessage);
      this.stompClient.subscribe(
        "/user/exchange/amq.direct/message.window",
        this.onMessage
      );
      // 104번 WP1 — 로그인 사용자별 알림 (결재 요청·승인·반려). 헤더 알림이 받는다
      this.stompClient.subscribe(
        "/user/exchange/amq.direct/message.user",
        response => eventBus.$emit("USER_NOTI", JSON.parse(JSON.parse(response.body)))
      );
      //heartbeat 설정
      this.stompClient.heartbeat.outgoing = 0;
      this.stompClient.heartbeat.incoming = 0;
    },
    wsUnsubscribe: function() {
      this.subscription = {};
      if (this.stompClient != null) {
        this.stompClient.disconnect();
        this.stompClient = null;
      }
    },
    onMessage: function(response) {
      var msg = JSON.parse(JSON.parse(response.body));
      //console.log("ws onMessage", msg);
      // if(msg.receiver === 'ALL'){
      //     // TODO 수신자가 없을경우 전체 전송할지 처리 필요.
      // } else if(msg.receiver !== this.userId){
      // 	 // 수신자와 사용자ID가 다를 경우 전송 하지 않음.
      //     return;
      // }
      if (msg.type === "NOTICE") {
        eventBus.$emit("NOTICE", msg);
      } else if (msg.type === "RELOAD") {
        eventBus.$emit("RELOAD", msg);
        // reload 에 대해 알림 사용하는곳 주석
        // if(msg.data != ''){
        // 	var data = {
        // 		noticeType: "INFO",
        // 		data : msg.data
        // 	};
        // 	eventBus.$emit('NOTICE', data);
        // }
      }
    },
    onNotice(msg) {
      console.log("TEST NOTICE [" + msg.noticeType + "] : " + msg.data);
      // -- 2023.03.24 추가
      document.getElementById("logTextWrap").innerHTML +=
        "TEST NOTICE [" + msg.noticeType + "] : " + msg.data + + '<br /><br />';
    },
    onReload(msg) {
      console.log("TEST RELOAD [" + msg.dest + "] : " + msg.data);
      // -- 2023.03.24 추가
      document.getElementById("logTextWrap").innerHTML +=
        "TEST RELOAD [" + msg.dest + "] : " + msg.data + + '<br /><br />';
    }
  }
});

(function() {
  if (Vue.prototype.$devMode) {
    console.log("📍Operating in development mode!! 🙈");
  } else {
    console.log("📍Operating in operational mode!! 🙉");
  }
})();