import Vue from "vue"
import App from "./../src/App.vue"
import SockJS from "sockjs-client"
import Stomp from "webstomp-client"
import router from "./router"
import vuetify from "./plugins/vuetify"
import common from "./../static/js/common"
import VueSplit from "vue-split-panel"
import VueSweetalert2 from "vue-sweetalert2"
import VueApexCharts from 'vue-apexcharts'

import "@mdi/font/css/materialdesignicons.min.css"
import "material-design-icons-iconfont/dist/material-design-icons.css"
import "sweetalert2/dist/sweetalert2.min.css"

Vue.config.productionTip = false;

Vue.use(common);
Vue.use(VueSplit);
Vue.use(VueSweetalert2);
Vue.use(VueApexCharts)

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

      if (
        error &&
        Object.prototype.toString.call(error) === "[object String]" &&
        error.lastIndexOf("Whoops! Lost connection to", 0) === 0
      ) {
        //when stomp session disconnected, try reconnect
        this.wsUnsubscribe();
        setTimeout(this.connect(), 2000);
        console.log("STOMP: Reconecting in 2 seconds");
      } else {
        console.log("STOMP: connection failed or nomally closed, redirect to login page");
        router.push({ path: '/signin' });
      }
    },
    wsSubcribe: function() {
      this.stompClient.subscribe("/topic/onMessage", this.onMessage);
      this.stompClient.subscribe(
        "/user/exchange/amq.direct/message.window",
        this.onMessage
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