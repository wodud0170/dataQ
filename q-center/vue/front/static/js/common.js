const DEVELOP_MODE = process.env.NODE_ENV === "production" ? false : true;
// 운영(q-center 가 화면을 서빙)과 개발 서버 모두 같은 출처로 부른다.
// 개발 서버는 config/index.js 의 proxyTable 이 q-center(28090)로 넘긴다 — 세션 쿠키가 실려야 API 가 열린다.
const APIURLBASE = "/";
const LOGINSTATUSDATA = JSON.parse(sessionStorage.getItem("loginStatus"));
// resizableGrid — 끌고 있는 머리 칸과 시작 위치, 문서 이벤트를 달았는지
const gripDrag = { th: undefined, startOffset: 0, bound: false };

const methods = {
  getToday() {
    // 현재 날짜
    let today = new Date();
    let year = today.getFullYear();
    let month = (today.getMonth() + 1).toString().padStart(2, "0");
    let day = today
      .getDate()
      .toString()
      .padStart(2, "0");
    let hours = today
      .getHours()
      .toString()
      .padStart(2, "0");
    let minutes = today
      .getMinutes()
      .toString()
      .padStart(2, "0");
    let seconds = today
      .getSeconds()
      .toString()
      .padStart(2, "0");

    return `${year}${month}${day}${hours}${minutes}${seconds}`;
  },
  getMonthAgo() {
    // 한달 전 날짜 시간
    const today = new Date();
    const year = today.getFullYear();
    const month = today.getMonth() + 1;
    const day = today.getDate();
    const hours = today
      .getHours()
      .toString()
      .padStart(2, "0");
    const minutes = today
      .getMinutes()
      .toString()
      .padStart(2, "0");
    const seconds = today
      .getSeconds()
      .toString()
      .padStart(2, "0");

    let oneMonthAgoMonth = month - 1;
    let oneMonthAgoYear = year;

    if (oneMonthAgoMonth === 0) {
      oneMonthAgoMonth = 12;
      oneMonthAgoYear--;
    }

    let daysInOneMonthAgo = new Date(
      oneMonthAgoYear,
      oneMonthAgoMonth,
      0
    ).getDate();
    let oneMonthAgoDay = day;

    if (oneMonthAgoDay > daysInOneMonthAgo) {
      oneMonthAgoDay = daysInOneMonthAgo;
    }

    return `${oneMonthAgoYear}${oneMonthAgoMonth
      .toString()
      .padStart(2, "0")}${oneMonthAgoDay
      .toString()
      .padStart(2, "0")}${hours}${minutes}${seconds}`;
  },
  formattedDate(date) {
    // 날짜 시간 포멧 변경 (2023-02-28 13:33:16)
    const year = date.slice(0, 4);
    const month = date.slice(4, 6);
    const day = date.slice(6, 8);
    const hours = date.slice(8, 10);
    const minutes = date.slice(10, 12);
    const seconds = date.slice(12, 14);

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  },
  dateToString(dateString) {
    const date = new Date(dateString);
    const year = date.getFullYear().toString();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    const seconds = date.getSeconds().toString().padStart(2, '0');
  
    return year + month + day + hours + minutes + seconds;
  },
  // 표 머리 칸마다 폭 조절 손잡이를 붙인다. 여러 번 불러도 된다 — 손잡이가 이미 있는 칸은 건너뛰고 문서 이벤트는 한 번만 단다
  // (104번 WP8-75 — 컬럼 화면이 사용자 정의 속성 열이 바뀔 때마다 새로 생긴 머리 칸에 붙이려고 다시 부른다)
  resizableGrid() {
    Array.prototype.forEach.call(
      document.querySelectorAll("table th"),
      function(th) {
        if (th.dataset.resizeGrip) return;
        th.dataset.resizeGrip = "Y";
        th.style.position = "relative";

        let grip = document.createElement("div");
        grip.innerHTML = "&nbsp;";
        grip.style.top = 0;
        grip.style.right = 0;
        grip.style.bottom = 0;
        grip.style.width = "5px";
        grip.style.position = "absolute";
        grip.style.cursor = "col-resize";
        grip.addEventListener("mousedown", function(e) {
          gripDrag.th = th;
          gripDrag.startOffset = th.offsetWidth - e.pageX;
        });

        th.appendChild(grip);
      }
    );

    if (gripDrag.bound) return;
    gripDrag.bound = true;
    document.addEventListener("mousemove", function(e) {
      if (gripDrag.th) {
        gripDrag.th.style.width = gripDrag.startOffset + e.pageX + "px";
      }
    });

    document.addEventListener("mouseup", function() {
      gripDrag.th = undefined;
    });
  },
  encodePw(s) {
    // 비밀번호를 UTF-8 바이트로 Base64 (btoa 만 쓰면 한글 등 Latin1 밖 글자에서 예외). 서버는 UTF-8 로 되돌린다.
    return btoa(unescape(encodeURIComponent(s)));
  }
};

export default {
  install(Vue) {
    Vue.prototype.$APIURL = {
      base: APIURLBASE
    };
    Vue.prototype.$devMode = DEVELOP_MODE;
    Vue.prototype.$getToday = methods.getToday;
    Vue.prototype.$getMonthAgo = methods.getMonthAgo;
    Vue.prototype.$formattedDate = methods.formattedDate;
    Vue.prototype.$dateToString = methods.dateToString;
    Vue.prototype.$resizableGrid = methods.resizableGrid;
    Vue.prototype.$encodePw = methods.encodePw;
    Vue.prototype.$loginStatusData = LOGINSTATUSDATA;
  }
};
