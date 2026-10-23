<template>
  <v-main>
    <!-- 상태별 칩 필터 -->
    <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
      <v-row align="center" no-gutters>
        <v-btn-toggle v-model="activeStatusFilter" mandatory dense class="mr-2">
          <v-btn small value="REQUESTED" :color="activeStatusFilter === 'REQUESTED' ? 'indigo' : ''" :dark="activeStatusFilter === 'REQUESTED'">승인대기 ({{ statusCounts.requested }})</v-btn>
          <v-btn small value="APPROVED" :color="activeStatusFilter === 'APPROVED' ? 'green' : ''" :dark="activeStatusFilter === 'APPROVED'">승인완료 ({{ statusCounts.approved }})</v-btn>
          <v-btn small value="REJECTED" :color="activeStatusFilter === 'REJECTED' ? 'red' : ''" :dark="activeStatusFilter === 'REJECTED'">반려 ({{ statusCounts.rejected }})</v-btn>
          <v-btn small value="ALL" :color="activeStatusFilter === 'ALL' ? 'grey darken-1' : ''" :dark="activeStatusFilter === 'ALL'">전체 ({{ approvalAllItems.length }})</v-btn>
        </v-btn-toggle>
      </v-row>
    </v-sheet>

    <!-- 검색과 버튼 영역 — 우측 padding 확보로 [일괄 반려] 잘림 방지 -->
    <v-sheet class="splitTopWrapper pt-4 pb-4" id="approvalWrap"
      style="padding-right:24px !important;"
      v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
      <!-- 검색 -->
      <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]" class="searchWrapper">
        <v-select dense hide-details v-model="typeFilter" :items="typeItems" label="유형" color="ndColor"
          style="max-width: 120px;" :menu-props="{ top: false, offsetY: true }"></v-select>
        <date-picker v-model="datetimeitem" type="datetime" valueType="format" range class="ml-3"></date-picker>
        <v-btn class="gradient ml-3" title="검색" v-on:click="getApprovalData"
          :style="{ width: '40px', padding: '0 5px', minWidth: '45px' }"><v-icon>search</v-icon></v-btn>
      </v-sheet>
      <!-- 일괄 버튼 (화면 우측 padding 확보) -->
      <v-sheet class="pl-4" v-if="canApprove"
        style="padding-right:32px !important; flex-shrink:0;"
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
      :items-per-page="itemsPerPage" hide-default-footer item-key="key" show-select selectable-key="selectable" class="px-4 pb-3"
      v-model="selectedItem" :loading="loadTable" loading-text="Loading" @click:row="bindDetail">
      <template v-slot:[`item.reqItemNm`]="{ item }">
        <span class="ndColor--text" :style="{ cursor: 'pointer' }">{{ item.reqItemNm }}</span>
      </template>
      <template v-slot:[`item.aprvStat`]="{ item }">
        <v-chip small :color="getStatusChipColor(item.aprvStatRaw)" :text-color="getStatusTextColor(item.aprvStatRaw)">
          {{ item.aprvStat }}
        </v-chip>
        <div v-if="item.aprvStatRaw === 0 && aprvDocs[item.reqItemId]" style="font-size:.7rem; color:#1565C0;"
          :title="'현재 결재자: ' + (aprvDocs[item.reqItemId].curAprvrNms || '')">
          결재 {{ aprvDocs[item.reqItemId].curStepNo }}/{{ aprvDocs[item.reqItemId].stepCnt }} · {{ aprvDocs[item.reqItemId].curAprvrNms }}
        </div>
      </template>
      <!-- 행별 처리 — 그리드 가장 오른쪽 -->
      <template v-slot:[`item.rowActions`]="{ item }">
        <template v-if="!canApprove">
          <span style="font-size:.75rem; color:#9E9E9E;">-</span>
        </template>
        <template v-else-if="rowRejectId === item.key">
          <!-- 반려 사유 인라인 입력 모드 -->
          <div class="d-flex align-center" style="gap:4px;" @click.stop>
            <!-- 110번 8.5-6 — 처리 사유는 50자까지 저장된다 -->
            <v-text-field v-model="rowRejectReason" dense hide-details outlined single-line maxlength="50"
              placeholder="사유" style="max-width:120px; font-size:.8rem;" autofocus></v-text-field>
            <v-btn icon x-small color="red" @click.stop="confirmRowReject(item)" title="반려 확정">
              <v-icon small>mdi-check</v-icon>
            </v-btn>
            <v-btn icon x-small @click.stop="cancelRowReject" title="취소">
              <v-icon small>mdi-close</v-icon>
            </v-btn>
          </div>
        </template>
        <template v-else>
          <!-- 110번 8.5-3a — 처리된 행(승인완료·반려)은 승인·반려 둘 다 막는다 (서버도 승인 대기 항목만 처리) -->
          <div class="d-flex align-center justify-center" style="gap:4px; white-space:nowrap;">
            <v-btn x-small color="success" :disabled="item.aprvStatRaw !== 0"
              @click.stop="approveItem(item)" title="승인">
              <v-icon x-small left>mdi-check</v-icon>승인
            </v-btn>
            <v-btn x-small color="red" :dark="item.aprvStatRaw === 0" :disabled="item.aprvStatRaw !== 0"
              @click.stop="startRowReject(item)" title="반려">
              <v-icon x-small left>mdi-close</v-icon>반려
            </v-btn>
          </div>
        </template>
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
        <v-col>
          <div class="text-subtitle-1 font-weight-bold mb-2">
            <v-chip small class="mr-2" :color="getTypeColor(detailItem._reqTpRaw)">{{ detailItem._reqTpLabel }}</v-chip>
            {{ detailItem._itemNm || '-' }}
          </div>
        </v-col>
        <v-col cols="auto">
          <v-btn icon small @click="detailItem = null"><v-icon>mdi-close</v-icon></v-btn>
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
            <div class="detail-row"><span class="detail-label">형식단어:</span> {{ detailData.wordClsfYn }}</div>
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
      <v-divider class="my-3" v-if="canApprove"></v-divider>
      <v-row v-if="canApprove" align="center">
        <v-col cols="auto">
          <v-btn color="success" @click="approveItem(detailItem)" :disabled="detailItem.aprvStatRaw !== 0">
            <v-icon small class="mr-1">mdi-check</v-icon> 승인
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn color="red" :dark="detailItem.aprvStatRaw === 0" @click="showRejectDialog(detailItem)" :disabled="detailItem.aprvStatRaw !== 0">
            <v-icon small class="mr-1">mdi-close</v-icon> 반려
          </v-btn>
        </v-col>
        <v-col>
          <v-text-field v-model="rejectReason" dense hide-details clearable color="ndColor" maxlength="50"
            placeholder="반려 사유 입력" v-if="showRejectInput"></v-text-field>
        </v-col>
        <v-col cols="auto" v-if="showRejectInput">
          <v-btn color="red" dark @click="rejectItem(detailItem)">반려 확인</v-btn>
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

      <!-- 104번 WP9-55 — 이 항목의 변경 이력 (등록·수정·결재자 수정 후 승인) — 행을 펼치면 전후 비교 -->
      <v-divider class="my-3" v-if="changeHistory.length > 0"></v-divider>
      <div v-if="changeHistory.length > 0" id="aprvChangeHistory">
        <div class="text-caption font-weight-bold mb-1">변경 이력</div>
        <v-data-table :headers="changeHeaders" :items="changeHistory" item-key="changeId" show-expand single-expand
          :expanded.sync="changeExpanded" dense hide-default-footer :items-per-page="-1" class="chg-table">
          <template #item.changeType="{ item }">
            <v-chip x-small outlined>{{ changeTypeNm(item.changeType) }}</v-chip>
          </template>
          <template #item.changeDt="{ item }"><span class="nowrap">{{ item.changeDt }}</span></template>
          <template #expanded-item="{ headers: hs, item }">
            <td :colspan="hs.length" class="chg-diff-cell">
              <ChangeDiffTable :before="item.prevValue" :after="item.currValue" kind="STD" />
            </td>
          </template>
        </v-data-table>
      </div>

      <!-- 단어 반려 시 연관 용어 경고 -->
      <v-alert v-if="relatedTermsWarning.length > 0" type="warning" dense outlined class="mt-3">
        이 단어를 반려하면 다음 미승인 용어도 함께 삭제됩니다:
        <strong>{{ relatedTermsWarning.map(t => t.termsNm).join(', ') }}</strong>
      </v-alert>
    </v-sheet>

    <!-- 일괄 반려 사유 다이얼로그 -->
    <v-dialog max-width="500" v-model="batchRejectDialogShow">
      <v-card>
        <v-card-title>일괄 반려</v-card-title>
        <v-card-text>
          <p>선택한 {{ selectedItem.length }}건을 반려합니다.</p>
          <v-text-field v-model="batchRejectReason" label="반려 사유" color="ndColor" counter="50" maxlength="50"></v-text-field>
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
import ChangeDiffTable from './ChangeDiffTable.vue';

export default {
  name: 'MMApproval',
  components: { DatePicker, ChangeDiffTable },
  props: ['isMobile'],
  data: () => ({
    approvalAllItems: [],
    aprvDocs: {},   // 104번 WP1 — 항목 ID → 진행 중 결재 문서
    selectedItem: [],
    loadTable: true,
    page: 1,
    pageCount: null,
    itemsPerPage: 20,
    activeStatusFilter: 'REQUESTED',
    typeFilter: '전체',
    typeItems: ['전체', '용어', '단어', '도메인'],
    datetimeitem: [null, null],
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
    // 104번 WP9-55 — 이 항목의 변경 이력 (전후 비교)
    changeHistory: [],
    changeExpanded: [],
    changeHeaders: [
      { text: "유형", value: "changeType", width: "90px" },
      { text: "변경일시", value: "changeDt", width: "180px" },
      { text: "변경자", value: "changeUserId", width: "110px" },
      { text: "요약", value: "summary" },
      { text: "전후", value: "data-table-expand", width: "64px", sortable: false },
    ],
    approvalHeaders: [
      { text: '항목명', align: 'center', sortable: false, value: 'reqItemNm' },
      { text: '영문명', align: 'center', sortable: false, value: 'reqItemEngNm' },
      { text: '유형', align: 'center', sortable: false, value: 'reqTp' },
      { text: '상태', align: 'center', sortable: false, value: 'aprvStat' },
      { text: '요청자', align: 'center', sortable: false, value: 'reqUserId' },
      { text: '요청일', align: 'center', sortable: false, value: 'reqCretDt' },
      { text: '처리일', align: 'center', sortable: false, value: 'aprvStatUpdtDt' },
      { text: '사유', align: 'center', sortable: false, value: 'aprvStatUpdtRsn' },
      { text: '처리', align: 'center', sortable: false, value: 'rowActions', width: '180px' },
    ],
    rowRejectId: null,        // 인라인 반려 사유 입력 중인 행 key
    rowRejectReason: '',      // 인라인 반려 사유 텍스트
  }),
  computed: {
    // 104번 WP2 — 표준 승인 권한(관리자 또는 STD_APPROVE)이면 승인·반려 처리 영역을 보인다
    canApprove() { return this.$can('STD_APPROVE'); },
    statusCounts() {
      let items = this.approvalAllItems;
      return {
        requested: items.filter(i => i.aprvStatRaw === 0).length,
        approved: items.filter(i => i.aprvStatRaw === 2).length,
        rejected: items.filter(i => i.aprvStatRaw === 3).length,
      };
    },
    filteredItems() {
      let items = this.approvalAllItems;
      if (this.activeStatusFilter === 'REQUESTED') items = items.filter(i => i.aprvStatRaw === 0);
      else if (this.activeStatusFilter === 'APPROVED') items = items.filter(i => i.aprvStatRaw === 2);
      else if (this.activeStatusFilter === 'REJECTED') items = items.filter(i => i.aprvStatRaw === 3);
      if (this.typeFilter !== '전체') items = items.filter(i => i.reqTp === this.typeFilter);
      this.pageCount = Math.ceil(items.length / this.itemsPerPage);
      if (this.page > this.pageCount) this.page = Math.max(1, this.pageCount);
      return items;
    }
  },
  watch: {
    datetimeitem() {
      this.getApprovalData();
    }
  },
  created() {
    this.getApprovalData();
  },
  methods: {
    getStatusChipColor(stat) {
      if (stat === 0) return 'orange lighten-4';
      if (stat === 2) return 'green lighten-4';
      if (stat === 3) return 'red lighten-4';
      return 'grey lighten-3';
    },
    getStatusTextColor(stat) {
      if (stat === 0) return 'orange darken-3';
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

      let reqTpRaw = item._reqTpRaw;
      // 98번 — 용어·도메인은 이름으로 찾으므로 신청 항목이 속한 사전(item.dictId)으로 찾는다
      if (reqTpRaw === 'TERMS') {
        this.getTermDetail(item.reqItemNm, item.dictId);
      } else if (reqTpRaw === 'WORD') {
        this.getWordDetail(item.reqItemId);
      } else if (reqTpRaw === 'DOMAIN') {
        this.getDomainDetail(item.reqItemNm, item.dictId);
      }
      // 승인 이력 조회
      if (item.reqItemId) {
        axios.get(this.$APIURL.base + "api/std/getApprovalHistory", { params: { reqItemId: item.reqItemId, reqTp: reqTpRaw } })
          .then(res => { this.approvalHistory = res.data || []; })
          .catch(() => {});
      }
      // 104번 WP9-55 — 이 항목의 변경 이력 (변경 이력 대상 유형: 용어는 TERM)
      this.changeHistory = [];
      this.changeExpanded = [];
      if (item.reqItemId) {
        axios.post(this.$APIURL.base + "api/std/getChangeHistoryByTarget", { targetType: reqTpRaw === 'TERMS' ? 'TERM' : reqTpRaw, targetId: item.reqItemId })
          .then(res => { this.changeHistory = res.data || []; })
          .catch(() => {});
      }
    },
    changeTypeNm(t) {
      return { INSERT: '등록', UPDATE: '수정', DELETE: '삭제', BULK_INSERT: '일괄등록' }[t] || t;
    },
    getTermDetail(nm, dictId) {
      axios.get(this.$APIURL.base + "api/std/getTermsInfoByNm", { params: { termsNm: nm, dictId: dictId } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    getWordDetail(id) {
      axios.get(this.$APIURL.base + "api/std/getWordInfoById", { params: { wordId: id } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    getDomainDetail(nm, dictId) {
      axios.get(this.$APIURL.base + "api/std/getDomainInfoByNm", { params: { domainNm: nm, dictId: dictId } })
        .then(res => { this.detailData = res.data && res.data.length > 0 ? res.data[0] : {}; });
    },
    approveItem(item) {
      // 110번 8.5-14 — 한 건 승인도 일괄 승인처럼 확인을 받는다
      this.$swal.fire({
        title: "'" + item.reqItemNm + "' 을(를) 승인하시겠습니까?",
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '승인',
        cancelButtonText: '취소'
      }).then(result => {
        if (result.isConfirmed) this.updateApproval([item], 'APPROVED', '');
      });
    },
    /** 행별 반려 — 사유 입력 인라인 모드로 전환 */
    startRowReject(item) {
      this.rowRejectId = item.key;
      this.rowRejectReason = '';
    },
    cancelRowReject() {
      this.rowRejectId = null;
      this.rowRejectReason = '';
    },
    confirmRowReject(item) {
      var reason = (this.rowRejectReason || '').trim();
      if (!reason) {
        this.$swal.fire({ title: '반려 사유를 입력해주세요.', icon: 'warning', confirmButtonText: '확인' });
        return;
      }
      this.cancelRowReject();
      // 110번 8.5-13 — 단어를 반려하면 그 단어를 쓰는 승인 대기 용어도 함께 지워진다. 상세 패널처럼 미리 알리고 확인을 받는다
      if (item._reqTpRaw !== 'WORD') {
        this.updateApproval([item], 'REJECTED', reason);
        return;
      }
      axios.get(this.$APIURL.base + "api/std/getRelatedUnapprovedTerms", { params: { wordId: item.reqItemId } })
        .then(res => {
          var terms = res.data || [];
          if (terms.length === 0) {
            this.updateApproval([item], 'REJECTED', reason);
            return;
          }
          this.$swal.fire({
            title: '연관 미승인 용어도 함께 삭제됩니다',
            text: "단어 '" + item.reqItemNm + "' 을(를) 반려하면 다음 미승인 용어도 함께 삭제됩니다: " + terms.map(t => t.termsNm).join(', '),
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: '반려',
            cancelButtonText: '취소'
          }).then(result => {
            if (result.isConfirmed) this.updateApproval([item], 'REJECTED', reason);
          });
        })
        .catch(() => {
          this.$swal.fire({ title: '연관 미승인 용어 조회 실패 - API 확인 필요', icon: 'error', confirmButtonText: '확인' });
        });
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
    updateApproval(items, aprvStat, reason) {
      let statMap = { 'REQUESTED': 0, 'APPROVED': 2, 'REJECTED': 3 };
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
            // 104번 WP1 — 결재선이 걸린 항목은 단계만 넘어갈 수 있다
            if (aprvStat === 'APPROVED' && (result.data.resultMessage || '').indexOf('다음 ') >= 0) msg = '결재 단계를 승인했습니다.';
            // 경고 메시지가 있으면 표시 — 110번 8.5-5: 정상 응답의 기본 문구 "OK" 는 안내가 아니다
            if (result.data.resultMessage && result.data.resultMessage !== 'OK') {
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
              // 110번 8.5-3a — 일괄 승인·반려도 승인 대기 행만 고를 수 있다
              res.data[i].selectable = raw === 0;
              if (raw === 0) res.data[i].aprvStat = "승인대기";
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
            // 104번 WP1 — 승인 대기 항목 중 결재선이 걸린 것은 결재 단계·현재 결재자를 보인다
            const pendingIds = res.data.filter(r => r.aprvStatRaw === 0).map(r => r.reqItemId);
            if (pendingIds.length) {
              axios.post(this.$APIURL.base + "api/aprv/openDocs", { targetIds: pendingIds })
                .then(r2 => { this.aprvDocs = r2.data || {}; });
            } else {
              this.aprvDocs = {};
            }
          }
        })
        .catch(error => {
          console.error(error);
        })
        .finally(() => {
          this.loadTable = false;
        });
    }
  },
  mounted() {
    this.$resizableGrid();
  }
}
</script>

<style scoped>
/* 104번 WP9-55 */
.chg-table { border: 1px solid #E0E0E0; }
.chg-diff-cell { background: #FAFBFF; padding: 8px 16px 12px !important; }
.chg-table >>> th { white-space: nowrap; }
.nowrap { white-space: nowrap; }
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
