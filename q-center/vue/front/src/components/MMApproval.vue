<template>
  <v-main>
    <!-- 상태별 칩 필터 -->
    <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
      <v-row align="center" no-gutters>
        <v-chip-group v-model="activeStatusFilter" mandatory active-class="ndColor white--text">
          <v-chip filter outlined value="REQUESTED">승인대기 ({{ statusCounts.requested }})</v-chip>
          <v-chip filter outlined value="CHECKING">검토 ({{ statusCounts.checking }})</v-chip>
          <v-chip filter outlined value="APPROVED">승인완료 ({{ statusCounts.approved }})</v-chip>
          <v-chip filter outlined value="REJECTED">반려 ({{ statusCounts.rejected }})</v-chip>
          <v-chip filter outlined value="ALL">전체 ({{ approvalAllItems.length }})</v-chip>
        </v-chip-group>
      </v-row>
    </v-sheet>

    <!-- 검색과 버튼 영역 -->
    <v-sheet class="splitTopWrapper pt-4 pb-4" id="approvalWrap"
      v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
      <!-- 검색 -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]" class="searchWrapper">
        <v-select dense hide-details v-model="typeFilter" :items="typeItems" label="유형" color="ndColor"
          style="max-width: 120px;" :menu-props="{ top: false, offsetY: true }"></v-select>
        <date-picker v-model="datetimeitem" type="datetime" valueType="format" range class="ml-3"></date-picker>
        <v-btn class="gradient ml-3" title="검색" v-on:click="getApprovalData"
          :style="{ width: '40px', padding: '0 5px', minWidth: '45px' }"><v-icon>search</v-icon></v-btn>
      </v-sheet>
      <!-- 일괄 버튼 -->
      <v-sheet class="pr-4 pl-4" v-if="isAdmin"
        v-bind:style="[isMobile ? { width: '100%', display: 'flex', justifyContent: 'space-between' } : {}]">
        <v-btn class="gradient mr-2" @click="batchApprove" title="일괄 승인"
          :disabled="selectedItem.length === 0">
          <v-icon small class="mr-1">mdi-check</v-icon> 일괄 승인
        </v-btn>
        <v-btn color="red" dark @click="showBatchReject" title="일괄 반려"
          :disabled="selectedItem.length === 0">
          <v-icon small class="mr-1">mdi-close</v-icon> 일괄 반려
        </v-btn>
      </v-sheet>
    </v-sheet>

    <!-- 승인 목록 -->
    <v-data-table id="approval_table" :headers="approvalHeaders" :items="filteredItems" :page.sync="page"
      :items-per-page="itemsPerPage" hide-default-footer item-key="key" show-select class="px-4 pb-3"
      v-model="selectedItem" :loading="loadTable" loading-text="Loading" @click:row="bindDetail">
      <template v-slot:[`item.reqItemNm`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }">{{ item.reqItemNm }}</span>
      </template>
      <template v-slot:[`item.aprvStat`]="{ item }">
        <v-chip small :color="getStatusChipColor(item.aprvStatRaw)" :text-color="getStatusTextColor(item.aprvStatRaw)">
          {{ item.aprvStat }}
        </v-chip>
      </template>
      <template v-slot:no-data>
        <v-alert>데이터가 존재하지 않습니다.</v-alert>
      </template>
    </v-data-table>

    <!-- 페이지네이션 -->
    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>

    <!-- 상세 패널 (인라인) -->
    <v-sheet v-if="detailItem" class="mx-4 mb-4 pa-4" outlined style="border-radius: 8px;">
      <v-row>
        <v-col cols="12">
          <div class="text-subtitle-1 font-weight-bold mb-2">
            <v-chip small class="mr-2" :color="getTypeColor(detailItem._reqTpRaw)">{{ detailItem._reqTpLabel }}</v-chip>
            {{ detailItem._itemNm || '-' }}
          </div>
        </v-col>
      </v-row>
      <v-divider class="mb-3"></v-divider>

      <!-- 단어 상세 -->
      <template v-if="detailItem._reqTpRaw === 'WORD'">
        <v-row>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">단어명:</span> {{ detailData.wordNm }}</div>
            <div class="detail-row"><span class="detail-label">영문약어:</span> {{ detailData.wordEngAbrvNm }}</div>
            <div class="detail-row"><span class="detail-label">영문명:</span> {{ detailData.wordEngNm }}</div>
          </v-col>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">설명:</span> {{ detailData.wordDesc }}</div>
            <div class="detail-row"><span class="detail-label">분류어:</span> {{ detailData.wordClsfYn }}</div>
            <div class="detail-row"><span class="detail-label">도메인분류:</span> {{ detailData.domainClsfNm }}</div>
          </v-col>
        </v-row>
      </template>

      <!-- 용어 상세 -->
      <template v-if="detailItem._reqTpRaw === 'TERMS'">
        <v-row>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">용어명:</span> {{ detailData.termsNm }}</div>
            <div class="detail-row"><span class="detail-label">영문약어:</span> {{ detailData.termsEngAbrvNm }}</div>
            <div class="detail-row"><span class="detail-label">도메인:</span> {{ detailData.domainNm }}</div>
          </v-col>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">설명:</span> {{ detailData.termsDesc }}</div>
            <div class="detail-row"><span class="detail-label">승인여부:</span> {{ detailData.aprvYn }}</div>
          </v-col>
        </v-row>
      </template>

      <!-- 도메인 상세 -->
      <template v-if="detailItem._reqTpRaw === 'DOMAIN'">
        <v-row>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">도메인명:</span> {{ detailData.domainNm }}</div>
            <div class="detail-row"><span class="detail-label">그룹:</span> {{ detailData.domainGrpNm }}</div>
            <div class="detail-row"><span class="detail-label">분류:</span> {{ detailData.domainClsfNm }}</div>
          </v-col>
          <v-col cols="6">
            <div class="detail-row"><span class="detail-label">데이터타입:</span> {{ detailData.dataType }}</div>
            <div class="detail-row"><span class="detail-label">길이:</span> {{ detailData.dataLen }}</div>
            <div class="detail-row"><span class="detail-label">설명:</span> {{ detailData.domainDesc }}</div>
          </v-col>
        </v-row>
      </template>

      <!-- 관리자 승인/반려 액션 -->
      <v-divider class="my-3" v-if="isAdmin"></v-divider>
      <v-row v-if="isAdmin" align="center">
        <v-col cols="auto">
          <v-btn color="success" @click="approveItem(detailItem)" :disabled="detailItem.aprvStatRaw === 2">
            <v-icon small class="mr-1">mdi-check</v-icon> 승인
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn color="red" dark @click="showRejectDialog(detailItem)" :disabled="detailItem.aprvStatRaw === 3">
            <v-icon small class="mr-1">mdi-close</v-icon> 반려
          </v-btn>
        </v-col>
        <v-col>
          <v-text-field v-model="rejectReason" dense hide-details clearable color="ndColor"
            placeholder="반려 사유 입력" v-if="showRejectInput"></v-text-field>
        </v-col>
        <v-col cols="auto" v-if="showRejectInput">
          <v-btn color="red" dark @click="rejectItem(detailItem)">반려 확인</v-btn>
        </v-col>
      </v-row>

      <!-- 반려 상태: 재요청 버튼 -->
      <v-row v-if="detailItem.aprvStatRaw === 3" class="mt-2">
        <v-col>
          <v-btn color="primary" @click="reRequestApproval(detailItem)">
            <v-icon small class="mr-1">mdi-refresh</v-icon> 재요청 (승인대기로 변경)
          </v-btn>
        </v-col>
      </v-row>

      <!-- 승인 이력 -->
      <v-divider class="my-3" v-if="approvalHistory.length > 0"></v-divider>
      <div v-if="approvalHistory.length > 0">
        <div class="text-caption font-weight-bold mb-1">승인 이력</div>
        <v-simple-table dense>
          <thead><tr><th>상태</th><th>처리자</th><th>처리일</th><th>사유</th></tr></thead>
          <tbody>
            <tr v-for="(h, hi) in approvalHistory" :key="hi">
              <td><v-chip x-small :color="h.aprvStat == 3 ? 'red' : h.aprvStat == 2 ? 'green' : 'orange'" text-color="white">{{ h.aprvStatNm }}</v-chip></td>
              <td>{{ h.aprvUserId || h.reqUserId || '-' }}</td>
              <td>{{ h.aprvStatUpdtDt || h.reqCretDt || '-' }}</td>
              <td>{{ h.aprvStatUpdtRsn || '-' }}</td>
            </tr>
          </tbody>
        </v-simple-table>
      </div>

      <!-- 단어 반려 시 연관 용어 경고 -->
      <v-alert v-if="relatedTermsWarning.length > 0" type="warning" dense outlined class="mt-3">
        이 단어를 사용하는 미승인 용어가 있습니다:
        <strong>{{ relatedTermsWarning.map(t => t.termsNm).join(', ') }}</strong>
        <br/>해당 용어도 함께 반려하시겠습니까?
        <v-btn small text color="red" class="ml-2" @click="batchRejectRelatedTerms">함께 반려</v-btn>
      </v-alert>
    </v-sheet>

    <!-- 일괄 반려 사유 다이얼로그 -->
    <v-dialog max-width="500" v-model="batchRejectDialogShow">
      <v-card>
        <v-card-title>일괄 반려</v-card-title>
        <v-card-text>
          <p>선택한 {{ selectedItem.length }}건을 반려합니다.</p>
          <v-text-field v-model="batchRejectReason" label="반려 사유" color="ndColor"></v-text-field>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="batchRejectDialogShow = false">취소</v-btn>
          <v-btn color="red" dark @click="executeBatchReject">반려 확인</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from "axios";
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';

export default {
  name: 'MMApproval',
  components: { DatePicker },
  props: ['isMobile', 'approvalStatus'],
  data: () => ({
    approvalAllItems: [],
    selectedItem: [],
    loadTable: true,
    page: 1,
    pageCount: null,
    itemsPerPage: 20,
    activeStatusFilter: 'REQUESTED',
    typeFilter: '전체',
    typeItems: ['전체', '용어', '단어', '도메인'],
    datetimeitem: [null, null],
    isAdmin: false,
    // 상세 패널
    detailItem: null,
    detailData: {},
    showRejectInput: false,
    rejectReason: '',
    relatedTermsWarning: [],
    // 일괄 반려
    batchRejectDialogShow: false,
    batchRejectReason: '',
    // 승인 이력
    approvalHistory: [],
    approvalHeaders: [
      { text: '항목명', align: 'center', sortable: false, value: 'reqItemNm' },
      { text: '영문명', align: 'center', sortable: false, value: 'reqItemEngNm' },
      { text: '유형', align: 'center', sortable: false, value: 'reqTp' },
      { text: '상태', align: 'center', sortable: false, value: 'aprvStat' },
      { text: '요청자', align: 'center', sortable: false, value: 'reqUserId' },
      { text: '요청일', align: 'center', sortable: false, value: 'reqCretDt' },
      { text: '처리일', align: 'center', sortable: false, value: 'aprvStatUpdtDt' },
      { text: '사유', align: 'center', sortable: false, value: 'aprvStatUpdtRsn' },
    ],
  }),
  computed: {
    statusCounts() {
      let items = this.approvalAllItems;
      return {
        requested: items.filter(i => i.aprvStatRaw === 0).length,
        checking: items.filter(i => i.aprvStatRaw === 1).length,
        approved: items.filter(i => i.aprvStatRaw === 2).length,
        rejected: items.filter(i => i.aprvStatRaw === 3).length,
      };
    },
    filteredItems() {
      let items = this.approvalAllItems;
      if (this.activeStatusFilter === 'REQUESTED') items = items.filter(i => i.aprvStatRaw === 0);
      else if (this.activeStatusFilter === 'CHECKING') items = items.filter(i => i.aprvStatRaw === 1);
      else if (this.activeStatusFilter === 'APPROVED') items = items.filter(i => i.aprvStatRaw === 2);
      else if (this.activeStatusFilter === 'REJECTED') items = items.filter(i => i.aprvStatRaw === 3);
      if (this.typeFilter !== '전체') items = items.filter(i => i.reqTp === this.typeFilter);
      this.pageCount = Math.ceil(items.length / this.itemsPerPage);
      return items;
    }
  },
  watch: {
    approvalStatus() {
      if (this.approvalStatus && this.approvalStatus.length > 0) {
        this.activeStatusFilter = this.approvalStatus[0];
      }
    },
    datetimeitem() {
      this.getApprovalData();
    }
  },
  created() {
    this.adminCheck();
    this.getApprovalData();
  },
  methods: {
    getStatusChipColor(stat) {
      if (stat === 0) return 'orange lighten-4';
      if (stat === 1) return 'blue lighten-4';
      if (stat === 2) return 'green lighten-4';
      if (stat === 3) return 'red lighten-4';
      return 'grey lighten-3';
    },
    getStatusTextColor(stat) {
      if (stat === 0) return 'orange darken-3';
      if (stat === 1) return 'blue darken-3';
      if (stat === 2) return 'green darken-3';
      if (stat === 3) return 'red darken-3';
      return 'grey darken-1';
    },
    getTypeColor(tp) {
      if (tp === 'WORD') return 'blue lighten-4';
      if (tp === 'TERMS') return 'purple lighten-4';
      if (tp === 'DOMAIN') return 'teal lighten-4';
      return 'grey lighten-3';
    },
    bindDetail(item) {
      if (item.reqItemNm === '-') return;
      this.detailItem = item;
      this.showRejectInput = false;
      this.rejectReason = '';
      this.relatedTermsWarning = [];
      this.approvalHistory = [];
      this.selectedItem = [item];

      let reqTpRaw = item._reqTpRaw;
      if (reqTpRaw === 'TERMS') {
        this.getTermDetail(item.reqItemNm);
      } else if (reqTpRaw === 'WORD') {
        this.getWordDetail(item.reqItemId);
      } else if (reqTpRaw === 'DOMAIN') {
        this.getDomainDetail(item.reqItemNm);
      }
      // 승인 이력 조회
      if (item.reqItemId) {
        axios.get(this.$APIURL.base + "api/std/getApprovalHistory", { params: { reqItemId: item.reqItemId, reqTp: reqTpRaw } })
          .then(res => { this.approvalHistory = res.data || []; })
          .catch(() => {});
      }
    },
    reRequestApproval(item) {
      let tpMap = { '도메인': 'DOMAIN', '용어': 'TERMS', '단어': 'WORD' };
      let reqTp = tpMap[item.reqTp] || item._reqTpRaw || item.reqTp;
      this.$swal.fire({
        title: '재요청',
        text: "'" + item.reqItemNm + "' 항목을 승인대기 상태로 재요청합니다.",
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '재요청',
        cancelButtonText: '취소'
      }).then(result => {
        if (!result.isConfirmed) return;
        axios.post(this.$APIURL.base + "api/std/reRequestApproval", {
          reqTp: reqTp,
          reqItemId: item.reqItemId
        }).then(res => {
          if (res.data.success) {
            this.$swal.fire({ title: '재요청 완료', icon: 'success', timer: 1500, showConfirmButton: false });
            this.detailItem = null;
            this.getApprovalData();
          } else {
            this.$swal.fire({ title: '실패', text: res.data.message, icon: 'error', confirmButtonText: '확인' });
          }
        }).catch(() => {
          this.$swal.fire({ title: '서버 오류', icon: 'error', confirmButtonText: '확인' });
        });
      });
    },
    getTermDetail(nm) {
      axios.get(this.$APIURL.base + "api/std/getTermsInfoByNm", { params: { termsNm: nm } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    getWordDetail(id) {
      axios.get(this.$APIURL.base + "api/std/getWordInfoById", { params: { wordId: id } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    getDomainDetail(nm) {
      axios.get(this.$APIURL.base + "api/std/getDomainInfoByNm", { params: { domainNm: nm } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    approveItem(item) {
      this.updateApproval([item], 'APPROVED', '');
    },
    showRejectDialog(item) {
      this.showRejectInput = true;
      // 단어 반려 시 연관 미승인 용어 조회
      if (item._reqTpRaw === 'WORD') {
        axios.get(this.$APIURL.base + "api/std/getRelatedUnapprovedTerms", { params: { wordId: item.reqItemId } })
          .then(res => {
            this.relatedTermsWarning = res.data || [];
          });
      }
    },
    rejectItem(item) {
      if (!this.rejectReason) {
        this.$swal.fire({ title: '반려 사유를 입력해주세요.', icon: 'error', confirmButtonText: '확인' });
        return;
      }
      this.updateApproval([item], 'REJECTED', this.rejectReason);
    },
    batchApprove() {
      if (this.selectedItem.length === 0) return;
      this.$swal.fire({
        title: '선택한 ' + this.selectedItem.length + '건을 승인하시겠습니까?',
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '승인',
        cancelButtonText: '취소'
      }).then(result => {
        if (result.isConfirmed) {
          this.updateApproval(this.selectedItem, 'APPROVED', '');
        }
      });
    },
    showBatchReject() {
      this.batchRejectReason = '';
      this.batchRejectDialogShow = true;
    },
    executeBatchReject() {
      if (!this.batchRejectReason) {
        this.$swal.fire({ title: '반려 사유를 입력해주세요.', icon: 'error', confirmButtonText: '확인' });
        return;
      }
      this.batchRejectDialogShow = false;
      this.updateApproval(this.selectedItem, 'REJECTED', this.batchRejectReason);
    },
    batchRejectRelatedTerms() {
      if (this.relatedTermsWarning.length === 0) return;
      let items = this.relatedTermsWarning.map(t => ({
        reqItemId: t.termsId,
        reqItemNm: t.termsNm,
        _reqTpRaw: 'TERMS',
        reqTp: '용어'
      }));
      this.updateApproval(items, 'REJECTED', '연관 단어 반려로 인한 자동 반려');
    },
    updateApproval(items, aprvStat, reason) {
      let statMap = { 'REQUESTED': 0, 'CHECKING': 1, 'APPROVED': 2, 'REJECTED': 3 };
      let tpMap = { '도메인': 'DOMAIN', '용어': 'TERMS', '단어': 'WORD' };

      let arr = items.map(item => ({
        reqTp: tpMap[item.reqTp] || item._reqTpRaw || item.reqTp,
        reqItemId: item.reqItemId,
        reqItemNm: item.reqItemNm,
        aprvStat: statMap[aprvStat],
        reqSysCd: item.reqSysCd,
        reqUserId: item.reqUserId,
        aprvUserId: item.aprvUserId,
        aprvdDt: item.aprvdDt,
        aprvStatUpdtRsn: reason
      }));

      axios.post(this.$APIURL.base + "api/std/putStdAprvStat", arr)
        .then(result => {
          if (result.data.resultCode === 200) {
            let msg = aprvStat === 'APPROVED' ? '승인이 완료되었습니다.' : '반려가 완료되었습니다.';
            // 경고 메시지가 있으면 표시
            if (result.data.resultMessage) {
              this.$swal.fire({ title: msg, text: result.data.resultMessage, icon: 'warning', confirmButtonText: '확인' });
            } else {
              this.$swal.fire({ title: msg, icon: 'success', showConfirmButton: false, timer: 1500 });
            }
            this.detailItem = null;
            this.showRejectInput = false;
            this.relatedTermsWarning = [];
            this.getApprovalData();
          } else {
            this.$swal.fire({ title: '처리 실패', text: result.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        })
        .catch(error => {
          this.$swal.fire({ title: '처리 실패 - API 확인 필요', icon: 'error', confirmButtonText: '확인' });
        });
    },
    getApprovalData() {
      this.loadTable = true;
      let _fromDt = this.datetimeitem[0] ? this.$dateToString(this.datetimeitem[0]) : null;
      let _toDt = this.datetimeitem[1] ? this.$dateToString(this.datetimeitem[1]) : null;
      if (_fromDt === '19700101090000') _fromDt = null;
      if (_toDt === '19700101090000') _toDt = null;

      let params = {
        from: _fromDt,
        to: _toDt,
        aprvUserId: null,
        statusList: null,
        limit: 0
      };

      axios.post(this.$APIURL.base + "api/std/getStdAprvStatList", params)
        .then(res => {
          if (res.status === 200) {
            for (let i = 0; i < res.data.length; i++) {
              let raw = res.data[i].aprvStat;
              res.data[i].aprvStatRaw = raw;
              if (raw === 0) res.data[i].aprvStat = "요청";
              else if (raw === 1) res.data[i].aprvStat = "검토";
              else if (raw === 2) res.data[i].aprvStat = "승인";
              else if (raw === 3) res.data[i].aprvStat = "반려";

              let tp = res.data[i].reqTp;
              res.data[i]._reqTpRaw = tp;
              res.data[i]._reqTpLabel = tp === 'DOMAIN' ? '도메인' : tp === 'TERMS' ? '용어' : tp === 'WORD' ? '단어' : tp;
              if (tp === 'DOMAIN') res.data[i].reqTp = "도메인";
              else if (tp === 'TERMS') res.data[i].reqTp = "용어";
              else if (tp === 'WORD') res.data[i].reqTp = "단어";

              res.data[i]._itemNm = res.data[i].reqItemNm;
              if (res.data[i].reqItemNm === null) {
                res.data[i].reqItemNm = "-";
                res.data[i].aprvStatUpdtRsn = "삭제됨";
              }
              res.data[i].key = i;
            }
            this.approvalAllItems = res.data;
            this.selectedItem = [];
          }
        })
        .catch(error => {
          console.error(error);
        })
        .finally(() => {
          this.loadTable = false;
        });
    },
    adminCheck() {
      const _userStatus = this.$loginStatusData.id;
      axios.get(this.$APIURL.base + "api/login/isAdmin", { params: { user: _userStatus } })
        .then(result => { this.isAdmin = result.data; })
        .catch(err => { console.log(err); });
    }
  },
  mounted() {
    this.$resizableGrid();
  }
}
</script>

<style scoped>
.splitTopWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}
.searchWrapper {
  display: flex;
  align-items: center;
  align-content: center;
  justify-content: flex-start;
}
#approval_table {
  height: calc(100% - 300px);
  overflow-y: overlay;
  overflow-x: hidden;
}
.detail-row {
  margin-bottom: 4px;
  font-size: 0.875rem;
}
.detail-label {
  font-weight: 600;
  color: #546E7A;
  display: inline-block;
  width: 100px;
}
</style>
