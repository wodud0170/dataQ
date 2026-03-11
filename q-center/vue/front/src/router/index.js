import Vue from "vue";
import VueRouter from "vue-router";
import axios from "axios";
import NdLogin from "../views/login/NdLogin.vue";
import NdContent from "../views/content/NdContent.vue";
import NdHaederVue from "../views/header/NdHaeder.vue";
import NdNavVue from "../views/nav/NdNav.vue";

const routes = [
  // {
  //   path: "/signout",
  //   components: {
  //     content: LogoutView
  //   },
  //   name: "LogoutView"
  // },
  {
    path: "/",
    redirect: "/signin",
    name: "Mainview"
  },
  {
    path: "/login",
    redirect: "/signin",
    components: {
      content: NdLogin
    }
  },
  {
    path: "/signin",
    components: {
      content: NdLogin
    },
    name: "LoginView"
  },
  {
    path: "/app/main",
    name: "main",
    props: true,
    components: {
      header: NdHaederVue,
      aside: NdNavVue,
      content: NdContent
    },
    beforeEnter: function(to, from, next) {
      router_beforeEnter_setting(next);
    }
  }
];

const router = new VueRouter({
  routes,
  mode: "history",
  linkExactActiveClass: "ndColor--text"
});

Vue.use(VueRouter);

// router의 beforeEnter 기능을 개발용, 운영용으로 분리하고 코드를 줄이기 위해 callback 함수로 구현
async function router_beforeEnter_setting(next) {
  try {
    const result = await axios.get(
      Vue.prototype.$APIURL.base + "api/login/checkSession"
    );
    if (!result.data) {
      Vue.swal
        .fire({
          title: "로그인이 필요합니다.",
          confirmButtonText: "확인",
          icon: "info"
        })
        .then(result => {
          if (result.isConfirmed) {
            next("/signin");
          }
        });
    } else {
      next();
    }
  } catch (Error) {
    console.log(Error);
  }
}

export default router;
