/**
 * 104번 WP2 — 로그인 사용자의 권한.
 *
 * 화면 기능(버튼·메뉴) 표시는 관리자 여부가 아니라 권한 코드로 정한다. 관리자는 모든 권한을 갖는다.
 * 권한 코드는 서버 q-common PermCode 와 같다(STD_MANAGE, STD_APPROVE, DICT_ADMIN, DM_MANAGE, DM_APPROVE,
 * DDL_EXEC, AREA_ADMIN, DIAG_TARGET_ADMIN, SCHEDULE_ADMIN, BOARD_ADMIN, USER_ADMIN, DS_ADMIN, SYS_ADMIN, QUAL_ADMIN).
 *
 * 사용: this.$can('STD_MANAGE'), this.$can('DDL_EXEC', sysCd)
 * 한 번 읽어 두고 화면이 함께 쓴다(반응형). 서버도 같은 기준으로 다시 막으므로 이 값은 표시용이다.
 */
import Vue from "vue";
import axios from "axios";

const state = Vue.observable({
  loaded: false,
  admin: false,
  perms: {}, // 코드 → ['*' | 시스템코드 ...]
  roles: []
});

let loading = null;

export function loadPerms(force) {
  if (loading && !force) return loading;
  loading = axios
    .get(Vue.prototype.$APIURL.base + "api/login/myPerms")
    .then(res => {
      const d = res.data || {};
      state.admin = d.admin === true;
      state.perms = d.perms || {};
      state.roles = d.roles || [];
      state.loaded = true;
    })
    .catch(() => {
      state.loaded = true;
    });
  return loading;
}

/** 그 권한이 있는지. sysCd 를 주면 그 시스템에 대해(모든 시스템 '*' 으로 받았거나 그 시스템에 받은 경우). */
export function can(code, sysCd) {
  const scopes = state.perms[code];
  if (!scopes || scopes.length === 0) return false;
  if (sysCd === undefined) return true;
  return scopes.indexOf("*") >= 0 || (sysCd != null && scopes.indexOf(sysCd) >= 0);
}

export default {
  install(V) {
    V.prototype.$perm = state;
    V.prototype.$can = can;
    V.prototype.$loadPerms = loadPerms;
  }
};
