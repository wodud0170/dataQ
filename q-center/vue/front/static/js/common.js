import axios from "axios";

const DEVELOP_MODE = process.env.NODE_ENV === "production" ? false : true;
const APIURLBASE =
  process.env.NODE_ENV === "production" ? "/" : "http://127.0.0.1:28090/";
const LOGINSTATUSDATA = JSON.parse(sessionStorage.getItem("loginStatus"));

const methods = {
  checkSession: () => {
    let _data = null;
    axios
      .get(APIURLBASE + "api/login/checkSession")
      .then(result => {
        if (_data) {
          _data = null;
        }
        _data = result.data;
      })
      .catch(Error => {
        console.log(Error);
      });
    return _data;
  },
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
  resizableGrid() {
    let thElm;
    let startOffset;

    Array.prototype.forEach.call(
      document.querySelectorAll("table th"),
      function(th) {
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
          thElm = th;
          startOffset = th.offsetWidth - e.pageX;
        });

        th.appendChild(grip);
      }
    );

    document.addEventListener("mousemove", function(e) {
      if (thElm) {
        thElm.style.width = startOffset + e.pageX + "px";
      }
    });

    document.addEventListener("mouseup", function() {
      thElm = undefined;
    });
  }
};

export default {
  install(Vue) {
    Vue.prototype.$APIURL = {
      base: APIURLBASE
    };
    Vue.prototype.$checkSession = methods.checkSession;
    Vue.prototype.$devMode = DEVELOP_MODE;
    Vue.prototype.$getToday = methods.getToday;
    Vue.prototype.$getMonthAgo = methods.getMonthAgo;
    Vue.prototype.$formattedDate = methods.formattedDate;
    Vue.prototype.$dateToString = methods.dateToString;
    Vue.prototype.$resizableGrid = methods.resizableGrid;
    Vue.prototype.$loginStatusData = LOGINSTATUSDATA;
  }
};
