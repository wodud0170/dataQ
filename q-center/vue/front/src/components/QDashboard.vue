<template>
  <v-main>
    <v-sheet class="containerWrapper">
      <v-card class="itemsWrapper">
        <h2><v-icon>grid_view</v-icon>&nbsp;&nbsp;표준 현황</h2>
        <v-sheet class="chartWrapper">
          <!-- 용어 -->
          <v-card class="top_chart" v-on:click.stop="addTabItem('용어', 'term')">

            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 mt-2 mb-2 fontSize08">
                용어
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ termsCnt }}
              </div>
            </v-card-text>
          </v-card>

          <!-- 단어 -->
          <v-card class="top_chart" v-on:click.stop="addTabItem('단어', 'word')">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6  mt-2 mb-2 fontSize08">
                단어
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ wordCnt }}
              </div>
            </v-card-text>
          </v-card>
          <!-- 도메인 -->
          <v-card class="top_chart" v-on:click.stop="addTabItem('도메인', 'domain')">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6  mt-2 mb-2 fontSize08">
                도메인
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ domainCnt }}
              </div>
            </v-card-text>
          </v-card>
        </v-sheet>
      </v-card>
      <v-card class="itemsWrapper">
        <h2><v-icon>grading</v-icon>&nbsp;&nbsp;데이터 모델 현황</h2>
        <v-sheet class="chartWrapper_2">
          <!-- 모델 -->
          <v-card class="topDmTextChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="titleFont fontSize08">
                모델 갯수
              </div>
              <div class="contentFont fontSize225">
                {{ dataModelCnt }}
              </div>
            </v-card-text>
          </v-card>
          <!-- 테이블 -->
          <v-card class="topDmTextChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="titleFont fontSize08">
                테이블 갯수
              </div>
              <div class="contentFont fontSize225">
                {{ objCnt }}
              </div>
            </v-card-text>
          </v-card>
          <!-- 컬럼 -->
          <v-card class="topDmTextChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="titleFont fontSize08">
                컬럼 갯수
              </div>
              <div class="contentFont fontSize225">
                {{ attrCnt }}
              </div>
            </v-card-text>
          </v-card>
        </v-sheet>

        <v-sheet class="chartWrapper_3">
          <!-- 용어 표준 준수율 -->
          <div class="topDmPieChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 mt-2 mb-2 fontSize08">
                용어 표준 준수율
              </div>
              <apexchart type="pie" class="chart_full" :options="term_pie_chartOptions" :series="term_series"
                :key="term_pie_Key"></apexchart>
            </v-card-text>
          </div>

          <!-- 단어 표준 준수율 -->
          <div class="topDmPieChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6  mt-2 mb-2 fontSize08">
                단어 표준 준수율
              </div>
              <apexchart type="pie" class="chart_full" :options="word_pie_chartOptions" :series="word_series"
                :key="word_pie_Key"></apexchart>

            </v-card-text>
          </div>

          <!-- 도메인 표준 준수율 -->
          <div class="topDmPieChart">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6  mt-2 mb-2 fontSize08">
                도메인 표준 준수율
              </div>
              <apexchart type="pie" class="chart_full" :options="domain_pie_chartOptions" :series="domain_series"
                :key="domain_pie_Key"></apexchart>
            </v-card-text>
          </div>
        </v-sheet>
      </v-card>

      <v-card class="itemsWrapper">
        <h2><v-icon>dashboard_customize</v-icon>&nbsp;&nbsp;승인 현황</h2>
        <v-sheet class="chartWrapper">
          <!-- 승인요청 -->
          <v-card class="bottom_chart" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('REQUESTED');">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 fontSize08 mt-2 mb-2">
                승인요청
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ aprvStatRequestedCnt }}
              </div>
            </v-card-text>
          </v-card>
          <!-- 검토 -->
          <v-card class="bottom_chart" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('CHECKING');">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 fontSize08 mt-2 mb-2">
                검토
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ aprvStatCheckingCnt }}
              </div>
            </v-card-text></v-card>
          <!-- 승인 완료 -->
          <v-card class="bottom_chart" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('APPROVED');">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 fontSize08 mt-2 mb-2">
                승인완료
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ aprvStatApprovedCnt }}
              </div>
            </v-card-text></v-card>
          <!-- 반려 -->
          <v-card class="bottom_chart" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('REJECTED');">
            <v-card-text class="pt-0 cardTextWrapper">
              <div class="text-h6 fontSize08 mt-2 mb-2">
                반려
              </div>
              <div class="text-h2 fontSize225" :style="{ fontWeight: '700', textAlign: 'center' }">
                {{ aprvStatRejectedCnt }}
              </div>
            </v-card-text></v-card>
        </v-sheet>
      </v-card>
      <v-card class="itemsWrapper" :style="{ overflow: 'hidden' }">
        <h2><v-icon>trending_up</v-icon>&nbsp;&nbsp;Top 10 데이터 모델(표준준수율 기준)</h2>
        <!-- 차트 영역 -->
        <v-sheet class="barchartWrapper">
          <apexchart class="bar_full" type="bar" height="95%" ref="barchart" :key="barchartKey"
            :options="bar_chartOptions" :series="bar_series"></apexchart>
        </v-sheet>
        <!-- 테이블 영역 -->
        <v-sheet class="barchartWrapper_bottom">
          <v-data-table id="topTen_table" :headers="topTenDataModelHeaders" :items="topTenDataModelList"
            hide-default-footer item-key="rank" class="px-4 pb-3" v-model="topTenDataModelList" :loading="loadTable"
            loading-text="잠시만 기다려주세요.">
            <template #top>
              <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
            </template>
            <template #no-data>
              <v-alert v-show="!loadTable">
                데이터가 존재하지 않습니다.
              </v-alert>
              <span v-show="loadTable">잠시만 기다려주세요.</span>
            </template>
          </v-data-table>
        </v-sheet>
      </v-card>
    </v-sheet>
  </v-main>
</template>

<script>
import axios from "axios";
import VueApexCharts from 'vue-apexcharts'

export default {
  name: 'QDashboard',
  props: ['isMobile', 'addTabs'],
  components: {
    'apexchart': VueApexCharts,
  },
  data: () => ({
    // 표준현황
    domainCnt: 0,
    termsCnt: 0,
    wordCnt: 0,
    // 승인현황
    aprvStatRequestedCnt: 0,
    aprvStatApprovedCnt: 0,
    aprvStatCheckingCnt: 0,
    aprvStatRejectedCnt: 0,
    // 데이터 모델 현황
    dataModelCnt: 0,
    objCnt: 0,
    attrCnt: 0,
    //
    term_series: [],
    word_series: [],
    domain_series: [],
    // 파이차트 옵션
    term_pie_chartOptions: {
      colors: ['#008FFB', '#00E396'],
      labels: ['준수', '미준수'],
      chart: {
        width: 200,
        height: 200,
        type: 'pie',
      },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          }
        }
      }
    },
    term_pie_Key: 0, // 용어  파이차트 key 값 초기화
    word_pie_chartOptions: {
      colors: ['#FF4560', '#FEB019'],
      labels: ['준수', '미준수'],
      chart: {
        width: 200,
        height: 200,
        type: 'pie',
      },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          }
        }
      }
    },
    word_pie_Key: 0, // 단어  파이차트 key 값 초기화
    domain_pie_chartOptions: {
      colors: ['#775DD0', '#43BCCD'],
      labels: ['준수', '미준수'],
      chart: {
        width: 200,
        height: 200,
        type: 'pie',
      },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          },
        }
      }
    },
    domain_pie_Key: 0, // 단어  파이차트 key 값 초기화
    // 바 차트 옵션
    bar_series: [{
      name: '전체',
      data: []
    }, {
      name: '용어',
      data: []
    }, {
      name: '단어',
      data: []
    }, {
      name: '도메인',
      data: []
    }],
    bar_chartOptions: {
      chart: {
        type: 'bar',
        height: 200,
        toolbar: {
          show: false,
        },
      },
      plotOptions: {
        bar: {
          horizontal: false,
          columnWidth: '55%',
          endingShape: 'rounded'
        },
      },
      dataLabels: {
        enabled: false
      },
      stroke: {
        show: true,
        width: 2,
        colors: ['transparent']
      },
      xaxis: {
        categories: [],
        labels: {
          rotate: 0,
          hideOverlappingLabels: false,
        },
      },
      yaxis: {
        show: false,
        title: {
          text: '%'
        }
      },
      fill: {
        opacity: 1
      },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          }
        }
      }
    },
    barchartKey: 0, // 바 차트 key 값 초기화
    topTenDataModelList: [],
    topTenDataModelHeaders: [
      { text: '순위', align: 'center', sortable: false, value: 'rank' },
      { text: '데이터모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '전체', align: 'center', sortable: false, value: 'totalStndRate' },
      { text: '용어', align: 'center', sortable: false, value: 'termsStndRate' },
      { text: '단어', align: 'center', sortable: false, value: 'wordStndRate' },
      { text: '도메인', align: 'center', sortable: false, value: 'domainStndRate' },

    ],
    loadTable: true,
  }),
  watch: {

  },
  created() {
    // 대시보드 메뉴 선택 시 호출
    this.getDashboardInfo();
    this.getDataModelStat();
    this.getTopDataModelList();
  },
  mounted() {
  },
  methods: {
    getDashboardInfo() {
      try {
        axios.get(this.$APIURL.base + "api/search/getDashboardInfo").then(result => {
          let _data = result.data;

          // console.log(_data);

          this.domainCnt = _data.domainCnt;
          this.aprvStatRequestedCnt = _data.aprvStatRequestedCnt;
          this.termsCnt = _data.termsCnt;
          this.aprvStatApprovedCnt = _data.aprvStatApprovedCnt;
          this.wordCnt = _data.wordCnt;
          this.aprvStatCheckingCnt = _data.aprvStatCheckingCnt;
          this.aprvStatRejectedCnt = _data.aprvStatRejectedCnt;

        }).catch(error => {
          this.$swal.fire({
            title: '대시보드 데이터 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });
      } catch (error) {
        console.error(error);

      }
    },
    getDataModelStat() {
      try {
        axios.get(this.$APIURL.base + "api/search/getDataModelStat").then(result => {
          let _data = result.data;

          // console.log(_data);

          this.dataModelCnt = _data.dataModelCnt;
          this.objCnt = _data.objCnt;
          this.attrCnt = _data.attrCnt;
          this.term_series = [_data.termsStndRate, (100 - _data.termsStndRate)];
          this.word_series = [_data.wordStndRate, (100 - _data.wordStndRate)];
          this.domain_series = [_data.domainStndRate, (100 - _data.domainStndRate)];

          this.term_pie_Key++;
          this.word_pie_Key++;
          this.domain_pie_Key++;

        }).catch(error => {
          this.$swal.fire({
            title: '대시보드 데이터 모델 현황 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        });
      } catch (error) {
        console.error(error);
      }
    },
    getTopDataModelList() {
      this.loadTable = true;
      try {
        axios.get(this.$APIURL.base + "api/search/getTopDataModelList").then(result => {
          let _data = result.data;

          // console.log(_data);

          let xaxis_categories = [];
          let bar_series_total = [];
          let bar_series_term = [];
          let bar_series_word = [];
          let bar_series_domain = [];

          for (let i = 0; i < _data.length; i++) {
            xaxis_categories.push('#' + (i + 1));
            bar_series_total.push(_data[i].totalStndRate);
            bar_series_term.push(_data[i].termsStndRate);
            bar_series_word.push(_data[i].wordStndRate);
            bar_series_domain.push(_data[i].domainStndRate);
            // 데이터에 rank 추가
            _data[i].rank = '#' + (i + 1);
            // 데이터에 % 추가 
            _data[i].totalStndRate = _data[i].totalStndRate + "%";
            _data[i].termsStndRate = _data[i].termsStndRate + "%";
            _data[i].wordStndRate = _data[i].wordStndRate + "%";
            _data[i].domainStndRate = _data[i].domainStndRate + "%";
          }

          this.bar_chartOptions.xaxis.categories = xaxis_categories;
          this.bar_series[0].data = bar_series_total;
          this.bar_series[1].data = bar_series_term;
          this.bar_series[2].data = bar_series_word;
          this.bar_series[3].data = bar_series_domain;

          // key 값을 변경하여 그래프 다시 렌더링
          this.barchartKey++;

          // 테이블 생성을 위해 바인드
          this.topTenDataModelList = _data;
          this.loadTable = false;
        }).catch(error => {
          this.$swal.fire({
            title: '대시보드 Top 10 데이터 모델 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        });
      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }
    },
    addTabItem(title, name) {
      // 1. 탭과 네비게이션을 동기화
      this.$emit('navAndTabSync', name);

      // 2. 탭에 현재 클릭한 메뉴가 있는지 확인하여 있으면 해당 탭으로 이동, 없으면 탭 추가
      // DOM이 동작을 끝내길 기다렸다가 진행하기 위해 SetTimeout 사용
      setTimeout(() => {
        let tab = this.addTabs.find(item => item.name === name);

        if (!tab) {
          let _index = this.addTabs.length;
          this.$emit('addTabItem', title, name, _index);
        } else {
          let _tab = this.addTabs.find(item => item.name === name);
          this.$emit('addActiveContent', name, _tab.index);
        }
      }, 500);

    },
    sendApprovalStatus(status) {
      // 승인일 경우 승인상태를 넘겨주어서 해당 목록만 보일 수 있도록 함
      this.$emit('sendApprovalStatus', status);
    }
  },
}
</script>

<style scoped>
.containerWrapper {
  position: relative;
  padding: 12px;
  background-color: #F5F7FA;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 12px;
}

.itemsWrapper {
  position: relative;
  width: calc(50% - 12px);
  height: calc((100vh - 64px - 48px - 50px) / 2);
  padding: 16px 20px;
  border-radius: 16px !important;
  background: #ffffff;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05), 0 2px 12px rgba(0, 0, 0, 0.03) !important;
}

.itemsWrapper h2 {
  display: flex;
  align-items: center;
  color: #283593;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.cardTextWrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-evenly;
}

.cardTextWrapper .text-h6 {
  position: absolute;
  top: 0;
}

.itemsWrapper h2 i {
  color: #3F51B5;
  font-size: 1.3rem;
}

.top_chart {
  position: relative;
  width: 30%;
  height: calc(80% - 20px);
  margin: 8px;
  cursor: pointer;
  background: linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 100%) !important;
  color: #283593 !important;
  border-radius: 14px !important;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.top_chart:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(63, 81, 181, 0.2) !important;
}

.bottom_chart {
  position: relative;
  width: 22%;
  height: calc(70% - 20px);
  margin: 8px;
  cursor: pointer;
  background: linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 100%) !important;
  color: #283593 !important;
  border-radius: 14px !important;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.bottom_chart:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(63, 81, 181, 0.2) !important;
}

.chartWrapper {
  display: flex;
  height: calc(100% - 36px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
  position: relative;
}

.barchartWrapper {
  display: flex;
  height: calc(60% - 36px);
  justify-content: space-around;
  bottom: 0px;
  position: relative;
  overflow: hidden;
  align-items: flex-start;
}

.chartWrapper_2 {
  display: flex;
  height: calc(40% - 36px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
}

.chartWrapper_3 {
  display: flex;
  height: calc(68% - 36px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
  overflow: hidden;
  background-color: transparent;
}

.topChartStyle {
  position: absolute;
  width: 100%;
  height: calc(50% - 1px);
  left: 0;
  bottom: -22px;
  margin: 0;
  padding: 0;
  opacity: 0.5;
}

.bottomChartStyle {
  position: absolute;
  width: 100%;
  height: calc(50% - 1px);
  left: 0;
  bottom: -38px;
  margin: 0;
  padding: 0;
  opacity: 0.5;
}

.dividerStyle {
  width: 100%;
  position: absolute;
  left: 0;
  top: 50%;
  margin: 0 !important;
}

.topDmTextChart {
  position: relative;
  width: 30%;
  margin: 8px;
  height: calc(50% - 20px);
  background: linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 100%) !important;
  color: #283593 !important;
  border-radius: 14px !important;
}

.chartWrapper_2 .topDmTextChart {
  position: relative;
  width: 30%;
  margin: 8px;
  height: 90%;
  background: linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 100%) !important;
  color: #283593 !important;
  border-radius: 14px !important;
}

.chartWrapper_2 .topDmPieChart {
  position: relative;
  width: 30%;
  margin: 8px;
  height: 95%;
  cursor: pointer;
  background-color: transparent !important;
  color: #455A64 !important;
}

.chartWrapper_3 .topDmTextChart {
  position: relative;
  width: 30%;
  margin: 8px;
  height: 60%;
  cursor: pointer;
  background: linear-gradient(135deg, #E8EAF6 0%, #C5CAE9 100%) !important;
  color: #283593 !important;
  border-radius: 14px !important;
}

.chartWrapper_3 .topDmPieChart {
  position: relative;
  width: 30%;
  margin: 8px;
  height: 95%;
  cursor: pointer;
  background-color: transparent !important;
  color: #455A64 !important;
}

.barchartWrapper_bottom {
  height: calc(48% - 36px);
  overflow: auto;
}

.titleFont {
  height: 30%;
  display: flex;
  align-items: center;
  font-weight: 500;
  color: #546E7A;
}

.contentFont {
  height: 70%;
  display: flex;
  align-items: center;
  font-weight: 700;
  text-align: center;
  justify-content: center;
  color: #283593;
}

.fontSize08 {
  font-size: 0.8vw !important;
}

.fontSize225 {
  font-size: 2.25vw !important;
}
</style>