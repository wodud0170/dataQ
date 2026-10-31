'use strict'
// Template version: 1.3.1
// see http://vuejs-templates.github.io/webpack for documentation.

const path = require('path')

module.exports = {
  dev: {

    // Paths
    assetsSubDirectory: 'static',
    assetsPublicPath: '/',
    // WP0 — API 는 로그인 세션(쿠키)이 있어야 열린다. 개발 서버(8080)가 q-center(28090)를 다른 출처로 부르면
    // 쿠키가 실리지 않으므로 같은 출처로 프록시한다. /login 은 화면 주소이기도 해서 POST(로그인 요청)만 넘긴다.
    proxyTable: {
      '/api': { target: 'http://127.0.0.1:28090' },
      '/logout': { target: 'http://127.0.0.1:28090' },
      '/sockjs': { target: 'http://127.0.0.1:28090', ws: true },
      '/login': {
        target: 'http://127.0.0.1:28090',
        bypass: function (req) {
          if (req.method !== 'POST') return '/index.html';
        }
      }
    },

    // Various Dev Server settings
    host: 'localhost', // can be overwritten by process.env.HOST
    port: 8080, // can be overwritten by process.env.PORT, if port is in use, a free one will be determined
    autoOpenBrowser: false,
    errorOverlay: true,
    notifyOnErrors: true,
    poll: false, // https://webpack.js.org/configuration/dev-server/#devserver-watchoptions-

    
    /**
     * Source Maps
     */

    // https://webpack.js.org/configuration/devtool/#development
    devtool: 'cheap-module-eval-source-map',

    // If you have problems debugging vue-files in devtools,
    // set this to false - it *may* help
    // https://vue-loader.vuejs.org/en/options.html#cachebusting
    cacheBusting: true,

    cssSourceMap: true
  },

  build: {
    // Template for index.html
    //index: path.resolve(__dirname, '../dist/index.html'),
    index: path.resolve(__dirname, '../../../src/main/resources/static/index.html'),

    // Paths
    //assetsRoot: path.resolve(__dirname, '../dist'),
    assetsRoot: path.resolve(__dirname, '../../../src/main/resources/static'),
    assetsSubDirectory: 'static/',
    assetsPublicPath: '/',

    /**
     * Source Maps
     */

    productionSourceMap: false,
    // https://webpack.js.org/configuration/devtool/#production
    devtool: '#source-map',

    // Gzip off by default as many popular static hosts such as
    // Surge or Netlify already gzip all static assets for you.
    // Before setting to `true`, make sure to:
    // npm install --save-dev compression-webpack-plugin
    productionGzip: false,
    productionGzipExtensions: ['js', 'css'],

    // Run the build command with an extra argument to
    // View the bundle analyzer report after build finishes:
    // `npm run build --report`
    // Set to `true` or `false` to always turn it on or off
    bundleAnalyzerReport: process.env.npm_config_report
  }
}
