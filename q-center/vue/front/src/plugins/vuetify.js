import Vue from "vue";
import Vuetify from "vuetify";
import "vuetify/dist/vuetify.min.css";

Vue.use(Vuetify);

const opts = {
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
