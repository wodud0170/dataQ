import Vue from "vue";
import Vuetify from "vuetify";
import "vuetify/dist/vuetify.min.css";

Vue.use(Vuetify);

// 한국어 locale — v-data-table 등 default 텍스트
const koLocale = {
  badge: "배지",
  close: "닫기",
  dataIterator: {
    noResultsText: "검색 결과가 없습니다.",
    loadingText: "로딩 중..."
  },
  dataTable: {
    itemsPerPageText: "페이지 당 행 수:",
    ariaLabel: {
      sortDescending: "내림차순 정렬.",
      sortAscending: "오름차순 정렬.",
      sortNone: "정렬 안 함.",
      activateNone: "정렬 제거",
      activateDescending: "내림차순 정렬",
      activateAscending: "오름차순 정렬"
    },
    sortBy: "정렬 기준"
  },
  dataFooter: {
    itemsPerPageText: "페이지 당 행 수:",
    itemsPerPageAll: "전체",
    nextPage: "다음",
    prevPage: "이전",
    firstPage: "처음",
    lastPage: "마지막",
    pageText: "{0}-{1} / {2}"
  },
  noDataText: "데이터가 존재하지 않습니다."
};

const opts = {
  lang: {
    locales: { ko: koLocale },
    current: "ko"
  },
  breakpoint: {
    mobileBreakpoint: "xs"
  },
  theme: {
    themes: {
      light: {
        primary: "#3F51B5",
        secondary: "#546E7A",
        accent: "#536DFE",
        error: "#EF5350",
        info: "#42A5F5",
        success: "#66BB6A",
        warning: "#FFA726",
        lightblue: "#5C6BC0",
        yellow: "#FFB300",
        pink: "#EC407A",
        orange: "#FF7043",
        magenta: "#AB47BC",
        darkblue: "#283593",
        gray: "#78909C",
        neutralgray: "#B0BEC5",
        green: "#66BB6A",
        red: "#EF5350",
        darkblueshade: "#3949AB",
        lightgray: "#CFD8DC",
        lightpink: "#F8BBD0",
        white: "#FFFFFF",
        ndColor: "#3F51B5",
        activeNdColor: "#1A237E",
        tabBgColor: "#E8EAF6",
        activeTabBgColor: "#C5CAE9",
      }
    }
  }
};

export default new Vuetify(opts);
