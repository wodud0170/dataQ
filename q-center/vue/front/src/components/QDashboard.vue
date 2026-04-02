<template>
  <v-main>
    <v-sheet class="containerWrapper">
      <!-- ===== 1. 표준 현황 ===== -->
      <v-card class="itemsWrapper">
        <h2><v-icon>grid_view</v-icon>&nbsp;&nbsp;표준 현황</h2>
        <v-sheet class="chartWrapper">
          <!-- 용어 -->
          <v-card class="std-card" v-on:click.stop="addTabItem('용어', 'term')">
            <div class="std-card__icon" style="background: linear-gradient(135deg, #3F51B5, #5C6BC0);">
              <v-icon dark large>mdi-book-open-variant</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">용어</div>
              <div class="std-card__value">{{ termsCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>

          <!-- 단어 -->
          <v-card class="std-card" v-on:click.stop="addTabItem('단어', 'word')">
            <div class="std-card__icon" style="background: linear-gradient(135deg, #303F9F, #5C6BC0);">
              <v-icon dark large>mdi-alphabetical</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">단어</div>
              <div class="std-card__value">{{ wordCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>

          <!-- 도메인 -->
          <v-card class="std-card" v-on:click.stop="addTabItem('도메인', 'domain')">
            <div class="std-card__icon" style="background: linear-gradient(135deg, #283593, #5C6BC0);">
              <v-icon dark large>mdi-domain</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">도메인</div>
              <div class="std-card__value">{{ domainCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>
        </v-sheet>
      </v-card>

      <!-- ===== 2. 데이터 모델 현황 ===== -->
      <v-card class="itemsWrapper">
        <div class="d-flex align-center">
          <h2><v-icon>grading</v-icon>&nbsp;&nbsp;데이터 모델 현황</h2>
          <v-spacer />
          <v-select
            v-model="selectedModelId"
            :items="modelList"
            item-text="dataModelNm"
            item-value="dataModelId"
            dense outlined hide-details
            placeholder="전체"
            clearable
            style="max-width:200px; font-size:.8rem;"
            @change="onModelFilterChange"
          />
        </div>
        <v-sheet class="chartWrapper_2">
          <!-- 모델 -->
          <v-card class="std-card std-card--sm">
            <div class="std-card__icon std-card__icon--sm" style="background: linear-gradient(135deg, #3F51B5, #7986CB);">
              <v-icon dark>mdi-database</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">모델</div>
              <div class="std-card__value std-card__value--sm">{{ dataModelCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>
          <!-- 테이블 -->
          <v-card class="std-card std-card--sm">
            <div class="std-card__icon std-card__icon--sm" style="background: linear-gradient(135deg, #303F9F, #7986CB);">
              <v-icon dark>mdi-table</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">테이블</div>
              <div class="std-card__value std-card__value--sm">{{ objCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>
          <!-- 컬럼 -->
          <v-card class="std-card std-card--sm">
            <div class="std-card__icon std-card__icon--sm" style="background: linear-gradient(135deg, #283593, #7986CB);">
              <v-icon dark>mdi-view-column</v-icon>
            </div>
            <div class="std-card__content">
              <div class="std-card__label">컬럼</div>
              <div class="std-card__value std-card__value--sm">{{ attrCnt }}<span class="std-card__unit">건</span></div>
            </div>
          </v-card>
        </v-sheet>

        <v-sheet class="chartWrapper_3">
          <!-- 표준 준수율 -->
          <div class="donut-chart-wrapper" style="flex:1;">
            <div class="donut-chart-title">표준 준수율</div>
            <apexchart type="donut" class="chart_full" :options="term_pie_chartOptions" :series="term_series"
              :key="term_pie_Key"></apexchart>
          </div>

          <!-- 구조 일치율 -->
          <div class="donut-chart-wrapper" style="flex:1;">
            <div class="donut-chart-title">구조 일치율</div>
            <apexchart type="donut" class="chart_full" :options="struct_pie_chartOptions" :series="struct_series"
              :key="struct_pie_Key"></apexchart>
          </div>

          <!-- 진단 추이 -->
          <div class="donut-chart-wrapper" style="flex:1.3;">
            <div class="donut-chart-title">진단 준수율 추이</div>
            <apexchart type="line" height="170" :options="diagTrendOptions" :series="diagTrendSeries"
              :key="diagTrendKey"></apexchart>
          </div>
        </v-sheet>
      </v-card>

      <!-- ===== 3. 승인 현황 ===== -->
      <v-card class="itemsWrapper">
        <h2><v-icon>dashboard_customize</v-icon>&nbsp;&nbsp;승인 현황</h2>
        <v-sheet class="chartWrapper">
          <!-- 승인요청 -->
          <v-card class="aprv-card" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('REQUESTED');">
            <div class="aprv-card__bar" style="background: #1976D2;"></div>
            <div class="aprv-card__icon-area" style="background: rgba(25,118,210,0.1);">
              <v-icon color="#1976D2" large>mdi-send</v-icon>
            </div>
            <div class="aprv-card__content">
              <div class="aprv-card__label">승인요청</div>
              <div class="aprv-card__value">{{ aprvStatRequestedCnt }}<span class="aprv-card__unit">건</span></div>
            </div>
          </v-card>
          <!-- 검토 -->
          <v-card class="aprv-card" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('CHECKING');">
            <div class="aprv-card__bar" style="background: #FB8C00;"></div>
            <div class="aprv-card__icon-area" style="background: rgba(251,140,0,0.1);">
              <v-icon color="#FB8C00" large>mdi-eye</v-icon>
            </div>
            <div class="aprv-card__content">
              <div class="aprv-card__label">검토</div>
              <div class="aprv-card__value">{{ aprvStatCheckingCnt }}<span class="aprv-card__unit">건</span></div>
            </div>
          </v-card>
          <!-- 승인 완료 -->
          <v-card class="aprv-card" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('APPROVED');">
            <div class="aprv-card__bar" style="background: #43A047;"></div>
            <div class="aprv-card__icon-area" style="background: rgba(67,160,71,0.1);">
              <v-icon color="#43A047" large>mdi-check-circle</v-icon>
            </div>
            <div class="aprv-card__content">
              <div class="aprv-card__label">승인완료</div>
              <div class="aprv-card__value">{{ aprvStatApprovedCnt }}<span class="aprv-card__unit">건</span></div>
            </div>
          </v-card>
          <!-- 반려 -->
          <v-card class="aprv-card" v-on:click.stop="addTabItem('승인', 'approval'); sendApprovalStatus('REJECTED');">
            <div class="aprv-card__bar" style="background: #E53935;"></div>
            <div class="aprv-card__icon-area" style="background: rgba(229,57,53,0.1);">
              <v-icon color="#E53935" large>mdi-close-circle</v-icon>
            </div>
            <div class="aprv-card__content">
              <div class="aprv-card__label">반려</div>
              <div class="aprv-card__value">{{ aprvStatRejectedCnt }}<span class="aprv-card__unit">건</span></div>
            </div>
          </v-card>
        </v-sheet>
      </v-card>

      <!-- ===== 4. 최근 활동 / 빠른 액션 ===== -->
      <v-card class="itemsWrapper" :style="{ overflow: 'hidden' }">
        <h2><v-icon>mdi-lightning-bolt</v-icon>&nbsp;&nbsp;최근 활동 / 빠른 액션</h2>
        <v-sheet class="bottom-split">
          <!-- 좌측: 최근 변경 이력 -->
          <div class="recent-history">
            <div class="section-subtitle">최근 변경 이력</div>
            <div v-if="recentHistoryLoading" class="recent-history__loading">
              <v-progress-circular indeterminate color="indigo" size="24" />
            </div>
            <div v-else-if="recentHistoryList.length === 0" class="recent-history__empty">
              변경 이력이 없습니다.
            </div>
            <div v-else class="recent-history__list">
              <div v-for="(item, idx) in recentHistoryList" :key="idx" class="recent-history__item">
                <div class="recent-history__dt">{{ item.changeDt }}</div>
                <v-chip :color="getChangeTypeColor(item.changeType)" x-small dark class="mr-1">{{ getChangeTypeLabel(item.changeType) }}</v-chip>
                <span class="recent-history__target">{{ getTargetTypeLabel(item.targetType) }}</span>
                <span class="recent-history__sep">|</span>
                <span class="recent-history__nm">{{ item.targetNm }}</span>
                <span v-if="item.summary" class="recent-history__summary">- {{ item.summary }}</span>
              </div>
            </div>
            <div class="recent-history__more">
              <v-btn text small color="indigo" @click="addTabItem('변경 이력', 'changeHistory')">더보기 &rarr;</v-btn>
            </div>
          </div>

          <!-- 우측: 빠른 액션 -->
          <div class="quick-actions">
            <div class="section-subtitle">빠른 액션</div>
            <div class="quick-actions__grid">
              <v-card class="quick-action-btn" @click="addTabItem('표준화 추천', 'termRecommend')">
                <v-icon color="#3F51B5" x-large>mdi-auto-fix</v-icon>
                <div class="quick-action-btn__label">표준화 추천</div>
                <div class="quick-action-btn__desc">표준 용어 자동 추천</div>
              </v-card>
              <v-card class="quick-action-btn" @click="addTabItem('진단 실행', 'dataDiag')">
                <v-icon color="#303F9F" x-large>mdi-stethoscope</v-icon>
                <div class="quick-action-btn__label">진단 실행</div>
                <div class="quick-action-btn__desc">표준화 진단 수행</div>
              </v-card>
              <v-card class="quick-action-btn" @click="addTabItem('구조 진단', 'schemaCompare')">
                <v-icon color="#283593" x-large>mdi-file-compare</v-icon>
                <div class="quick-action-btn__label">구조 진단</div>
                <div class="quick-action-btn__desc">모델-DB 구조 비교</div>
              </v-card>
            </div>
          </div>
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
    modelList: [],
    selectedModelId: null,
    //
    term_series: [],
    word_series: [],
    domain_series: [],
    // 도넛 차트 옵션 (공통 생성 함수로 대체)
    term_pie_chartOptions: {
      colors: ['#43A047', '#E53935'],
      labels: ['준수', '미준수'],
      chart: {
        type: 'donut',
      },
      plotOptions: {
        pie: {
          donut: {
            size: '65%',
            labels: {
              show: true,
              name: { show: true, fontSize: '13px', color: '#546E7A' },
              value: { show: true, fontSize: '20px', fontWeight: 700, color: '#283593' },
              total: {
                show: true,
                label: '준수율',
                fontSize: '12px',
                color: '#546E7A',
                formatter: function (w) {
                  var s = w.globals.seriesTotals;
                  if (s.length > 0 && (s[0] + s[1]) > 0) return s[0] + '%';
                  return '0%';
                }
              }
            }
          }
        }
      },
      dataLabels: { enabled: false },
      legend: { show: false },
      stroke: { width: 2, colors: ['#fff'] },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          }
        }
      }
    },
    term_pie_Key: 0,
    word_pie_chartOptions: {
      colors: ['#43A047', '#FB8C00'],
      labels: ['준수', '미준수'],
      chart: {
        type: 'donut',
      },
      plotOptions: {
        pie: {
          donut: {
            size: '65%',
            labels: {
              show: true,
              name: { show: true, fontSize: '13px', color: '#546E7A' },
              value: { show: true, fontSize: '20px', fontWeight: 700, color: '#283593' },
              total: {
                show: true,
                label: '준수율',
                fontSize: '12px',
                color: '#546E7A',
                formatter: function (w) {
                  var s = w.globals.seriesTotals;
                  if (s.length > 0 && (s[0] + s[1]) > 0) return s[0] + '%';
                  return '0%';
                }
              }
            }
          }
        }
      },
      dataLabels: { enabled: false },
      legend: { show: false },
      stroke: { width: 2, colors: ['#fff'] },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          }
        }
      }
    },
    word_pie_Key: 0,
    domain_pie_chartOptions: {
      colors: ['#43A047', '#E53935'],
      labels: ['준수', '미준수'],
      chart: {
        type: 'donut',
      },
      plotOptions: {
        pie: {
          donut: {
            size: '65%',
            labels: {
              show: true,
              name: { show: true, fontSize: '13px', color: '#546E7A' },
              value: { show: true, fontSize: '20px', fontWeight: 700, color: '#283593' },
              total: {
                show: true,
                label: '준수율',
                fontSize: '12px',
                color: '#546E7A',
                formatter: function (w) {
                  var s = w.globals.seriesTotals;
                  if (s.length > 0 && (s[0] + s[1]) > 0) return s[0] + '%';
                  return '0%';
                }
              }
            }
          }
        }
      },
      dataLabels: { enabled: false },
      legend: { show: false },
      stroke: { width: 2, colors: ['#fff'] },
      tooltip: {
        y: {
          formatter: function (val) {
            return val + "%"
          },
        }
      }
    },
    domain_pie_Key: 0,
    // 구조 일치율
    struct_series: [0, 100],
    struct_pie_chartOptions: {
      colors: ['#43A047', '#FF9800'],
      labels: ['일치', '불일치'],
      chart: { type: 'donut' },
      plotOptions: {
        pie: {
          donut: {
            size: '65%',
            labels: {
              show: true,
              name: { show: true, fontSize: '13px', color: '#546E7A' },
              value: { show: true, fontSize: '20px', fontWeight: 700, color: '#283593' },
              total: {
                show: true,
                label: '일치율',
                fontSize: '12px',
                color: '#546E7A',
                formatter: function (w) {
                  var s = w.globals.seriesTotals;
                  if (s.length > 0 && (s[0] + s[1]) > 0) return s[0] + '%';
                  return '미진단';
                }
              }
            }
          }
        }
      },
      dataLabels: { enabled: false },
      legend: { show: false },
      stroke: { width: 2, colors: ['#fff'] },
      tooltip: { y: { formatter: function (val) { return val + "%"; } } }
    },
    struct_pie_Key: 0,
    // 진단 추이 라인차트
    diagTrendSeries: [{ name: '준수율', data: [] }],
    diagTrendOptions: {
      chart: { type: 'line', toolbar: { show: false }, sparkline: { enabled: false } },
      colors: ['#3F51B5'],
      stroke: { width: 3, curve: 'smooth' },
      markers: { size: 5, colors: ['#3F51B5'], strokeColors: '#fff', strokeWidth: 2 },
      xaxis: { categories: [], labels: { style: { fontSize: '10px', colors: '#9E9E9E' } } },
      yaxis: { min: 0, max: 100, labels: { style: { fontSize: '10px' }, formatter: function(v) { return v + '%'; } } },
      grid: { borderColor: '#F0F0F0', strokeDashArray: 4 },
      tooltip: { y: { formatter: function(v) { return v + '%'; } } },
      dataLabels: { enabled: true, style: { fontSize: '11px', fontWeight: 600 }, formatter: function(v) { return v + '%'; }, offsetY: -8 },
    },
    diagTrendKey: 0,
    // 최근 변경 이력
    recentHistoryList: [],
    recentHistoryLoading: false,
  }),
  watch: {

  },
  created() {
    // 대시보드 메뉴 선택 시 호출
    this.getDashboardInfo();
    this.loadModelList();
    this.getDataModelStat();
    this.getDiagTrend();
    this.getRecentChangeHistory();
  },
  mounted() {
  },
  methods: {
    loadModelList() {
      try {
        axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", { schNm: null, schSysNm: null }).then(result => {
          this.modelList = (result.data || []).map(function(item) {
            return { dataModelId: item.dataModelId, dataModelNm: item.dataModelNm };
          });
        });
      } catch (e) { console.error(e); }
    },
    onModelFilterChange() {
      this.getDataModelStat();
      this.getStructDiagRate();
      this.getDiagTrend();
    },
    getDashboardInfo() {
      try {
        axios.get(this.$APIURL.base + "api/search/getDashboardInfo").then(result => {
          let _data = result.data;

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
      var self = this;
      try {
        axios.post(self.$APIURL.base + "api/dm/getDataModelStatsList", { schNm: null, schSysNm: null }).then(function(result) {
          var allModels = result.data || [];
          var filtered = allModels;
          if (self.selectedModelId) {
            filtered = allModels.filter(function(m) { return m.dataModelId === self.selectedModelId; });
          }
          // 합산
          var totalObj = 0, totalAttr = 0, totalStndRate = 0, stndRateCnt = 0;
          for (var i = 0; i < filtered.length; i++) {
            var m = filtered[i];
            if (m.dataModelStats) {
              totalObj += m.dataModelStats.objCnt || 0;
              totalAttr += m.dataModelStats.attrCnt || 0;
            }
            if (m.diagStndRate > 0) {
              totalStndRate += m.diagStndRate;
              stndRateCnt++;
            }
          }
          self.dataModelCnt = filtered.length;
          self.objCnt = totalObj;
          self.attrCnt = totalAttr;
          var avgRate = stndRateCnt > 0 ? Math.round(totalStndRate / stndRateCnt * 10) / 10 : 0;
          self.term_series = [avgRate, (100 - avgRate)];
          self.term_pie_Key++;
          self.getStructDiagRate();
        }).catch(function() {});
      } catch (error) {
        console.error(error);
      }
    },
    getStructDiagRate() {
      try {
        axios.get(this.$APIURL.base + "api/std/structDiag/history").then(result => {
          var data = result.data || [];
          if (data.length > 0) {
            // 최신 진단 결과에서 일치율 계산
            var latest = data[0];
            var totalChanges = (latest.addedTables || 0) + (latest.addedColumns || 0) +
              (latest.modifiedColumns || 0) + (latest.deletedTables || 0) + (latest.deletedColumns || 0);
            var totalItems = (latest.totalTables || 0) + (latest.totalColumns || 0);
            var matchRate = totalItems > 0 ? Math.round((1 - totalChanges / totalItems) * 100) : 0;
            if (matchRate < 0) matchRate = 0;
            this.struct_series = [matchRate, (100 - matchRate)];
          } else {
            this.struct_series = [0, 0]; // 미진단
          }
          this.struct_pie_Key++;
        }).catch(function() {});
      } catch (e) { console.error(e); }
    },
    getDiagTrend() {
      try {
        var self = this;
        axios.post(self.$APIURL.base + "api/diag/getDiagJobList", {}).then(function(result) {
          var jobs = (result.data || [])
            .filter(function(j) {
              if (j.status !== 'DONE' || j.totalCnt <= 0) return false;
              if (self.selectedModelId && j.dataModelId !== self.selectedModelId) return false;
              return true;
            })
            .slice(0, 5)
            .reverse();
          if (jobs.length > 0) {
            var categories = [];
            var data = [];
            for (var i = 0; i < jobs.length; i++) {
              var j = jobs[i];
              var rate = Math.round((1 - j.resultCnt / j.totalCnt) * 100 * 10) / 10;
              var dt = j.endDt || j.startDt || '';
              categories.push(dt.substring(5, 10) || ('#' + (i + 1)));
              data.push(rate);
            }
            self.diagTrendSeries = [{ name: '준수율', data: data }];
            self.diagTrendOptions = Object.assign({}, self.diagTrendOptions, {
              xaxis: Object.assign({}, self.diagTrendOptions.xaxis, { categories: categories })
            });
            self.diagTrendKey++;
          }
        }).catch(function() {});
      } catch (e) { console.error(e); }
    },
    getRecentChangeHistory() {
      this.recentHistoryLoading = true;
      try {
        axios.post(this.$APIURL.base + "api/std/getChangeHistoryList", {
          targetType: "",
          changeType: "",
          fromDt: "",
          toDt: ""
        }).then(result => {
          let _data = result.data || [];
          this.recentHistoryList = _data.slice(0, 5);
          this.recentHistoryLoading = false;
        }).catch(error => {
          console.error("최근 변경 이력 조회 실패:", error);
          this.recentHistoryList = [];
          this.recentHistoryLoading = false;
        });
      } catch (error) {
        console.error(error);
        this.recentHistoryLoading = false;
      }
    },
    getChangeTypeColor(type) {
      var map = { 'INSERT': 'green', 'UPDATE': 'blue', 'DELETE': 'red', 'BULK_INSERT': 'purple' };
      return map[type] || 'grey';
    },
    getChangeTypeLabel(type) {
      var map = { 'INSERT': '등록', 'UPDATE': '수정', 'DELETE': '삭제', 'BULK_INSERT': '일괄등록' };
      return map[type] || type;
    },
    getTargetTypeLabel(type) {
      var map = { 'WORD': '단어', 'TERM': '용어', 'DOMAIN': '도메인', 'CODE': '코드', 'CODE_DATA': '코드데이터' };
      return map[type] || type;
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

.itemsWrapper h2 i {
  color: #3F51B5;
  font-size: 1.3rem;
}

.chartWrapper {
  display: flex;
  height: calc(100% - 36px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
  position: relative;
}

/* ===== 1. 표준 현황 카드 ===== */
.std-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 30%;
  height: calc(70% - 20px);
  margin: 8px;
  cursor: pointer;
  background: #fff !important;
  border: 1px solid #E8EAF6;
  border-radius: 12px !important;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  overflow: hidden;
}

.std-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(63, 81, 181, 0.18) !important;
}

.std-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  min-width: 72px;
  height: 100%;
}

.std-card__icon--sm {
  width: 56px;
  min-width: 56px;
}

.std-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px 16px;
}

.std-card__label {
  font-size: 0.8rem;
  font-weight: 500;
  color: #546E7A;
  margin-bottom: 4px;
}

.std-card__value {
  font-size: 2.2vw;
  font-weight: 700;
  color: #283593;
  line-height: 1.1;
}

.std-card__value--sm {
  font-size: 1.6vw;
}

.std-card__unit {
  font-size: 0.8rem;
  font-weight: 400;
  color: #9E9E9E;
  margin-left: 4px;
}

.std-card--sm {
  height: calc(80% - 16px);
}

/* ===== 2. 데이터 모델 현황 ===== */
.chartWrapper_2 {
  display: flex;
  height: calc(35% - 36px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
}

.chartWrapper_3 {
  display: flex;
  height: calc(65% - 20px);
  justify-content: space-around;
  align-items: center;
  bottom: 0px;
  overflow: hidden;
  background-color: transparent;
}

.donut-chart-wrapper {
  position: relative;
  width: 30%;
  margin: 8px;
  height: 95%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.donut-chart-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #546E7A;
  margin-bottom: 4px;
  text-align: center;
}

.chart_full {
  width: 100%;
  max-width: 200px;
  max-height: 200px;
  margin: 0 auto;
}

/* ===== 3. 승인 현황 카드 ===== */
.aprv-card {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 22%;
  height: calc(65% - 20px);
  margin: 8px;
  cursor: pointer;
  background: #fff !important;
  border: 1px solid #ECEFF1;
  border-radius: 12px !important;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.aprv-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12) !important;
}

.aprv-card__bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
}

.aprv-card__icon-area {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  min-width: 60px;
  height: 100%;
}

.aprv-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px 12px;
}

.aprv-card__label {
  font-size: 0.78rem;
  font-weight: 500;
  color: #546E7A;
  margin-bottom: 4px;
}

.aprv-card__value {
  font-size: 2vw;
  font-weight: 700;
  color: #283593;
  line-height: 1.1;
}

.aprv-card__unit {
  font-size: 0.75rem;
  font-weight: 400;
  color: #9E9E9E;
  margin-left: 3px;
}

/* ===== 4. 최근 활동 / 빠른 액션 ===== */
.bottom-split {
  display: flex;
  flex-direction: row;
  height: calc(100% - 36px);
  gap: 16px;
  background: transparent;
}

.recent-history {
  flex: 1.2;
  display: flex;
  flex-direction: column;
  background: #FAFBFD;
  border-radius: 10px;
  padding: 12px 16px;
  border: 1px solid #E8EAF6;
}

.quick-actions {
  flex: 0.8;
  display: flex;
  flex-direction: column;
}

.section-subtitle {
  font-size: 0.85rem;
  font-weight: 600;
  color: #3F51B5;
  margin-bottom: 10px;
}

.recent-history__loading,
.recent-history__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9E9E9E;
  font-size: 0.85rem;
}

.recent-history__list {
  flex: 1;
  overflow-y: auto;
}

.recent-history__item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  padding: 6px 0;
  border-bottom: 1px solid #ECEFF1;
  font-size: 0.78rem;
  line-height: 1.5;
}

.recent-history__item:last-child {
  border-bottom: none;
}

.recent-history__dt {
  color: #78909C;
  font-size: 0.72rem;
  margin-right: 8px;
  white-space: nowrap;
}

.recent-history__target {
  font-weight: 500;
  color: #455A64;
  margin-left: 4px;
}

.recent-history__sep {
  margin: 0 4px;
  color: #CFD8DC;
}

.recent-history__nm {
  font-weight: 600;
  color: #283593;
}

.recent-history__summary {
  color: #78909C;
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-history__more {
  text-align: right;
  margin-top: 4px;
}

.quick-actions__grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.quick-action-btn {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px !important;
  cursor: pointer;
  background: linear-gradient(135deg, #E8EAF6 0%, #f5f5ff 100%) !important;
  border: 1px solid #C5CAE9;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  gap: 14px;
  flex: 1;
}

.quick-action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(63, 81, 181, 0.15) !important;
  background: linear-gradient(135deg, #C5CAE9 0%, #E8EAF6 100%) !important;
}

.quick-action-btn__label {
  font-size: 0.9rem;
  font-weight: 700;
  color: #283593;
}

.quick-action-btn__desc {
  font-size: 0.72rem;
  color: #78909C;
  margin-top: 2px;
}
</style>
