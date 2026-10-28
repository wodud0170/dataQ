<template>
  <v-main>
    <!-- 조회 조건 -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '6px' }">
        <!-- 104번 WP8 — 이름표와 입력을 묶음으로 줄바꿈 (이름표만 윗줄에 남지 않게) -->
        <div class="fgrp">
          <span class="filterLabel">데이터모델명</span>
          <v-autocomplete v-model="selectedModelId" :items="modelList"
            item-text="dataModelNm" item-value="dataModelId"
            @change="onModelChange" clearable dense outlined hide-details
            class="filterInput" :style="{ width: '200px' }" color="ndColor" placeholder="모델 선택">
          </v-autocomplete>
        </div>
        <!-- 86번 #11 — 소유자 (테이블명 앞) -->
        <div class="fgrp">
          <span class="filterLabel">소유자</span>
          <v-select v-model="searchOwnerMode" :items="searchModeOptions" item-text="label" item-value="value"
            dense outlined hide-details :style="{ width: '90px' }" />
          <v-text-field v-model="searchOwner" clearable clear-icon="mdi-close-circle" color="ndColor"
            single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }"
            placeholder="소유자(스키마)" />
        </div>
        <div class="fgrp">
          <span class="filterLabel">테이블 영문명 (물리)</span>
          <v-select v-model="searchTableMode" :items="searchModeOptions" item-text="label" item-value="value"
            dense outlined hide-details :style="{ width: '90px' }" />
          <v-text-field v-model="searchTable" @click:clear="searchTable=''" clearable
            prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
            single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
          </v-text-field>
        </div>
        <div class="fgrp">
          <span class="filterLabel">테이블 한글명 (논리)</span>
          <v-select v-model="searchTableKrMode" :items="searchModeOptions" item-text="label" item-value="value"
            dense outlined hide-details :style="{ width: '90px' }" />
          <v-text-field v-model="searchTableKr" @click:clear="searchTableKr=''" clearable
            prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor"
            single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }">
          </v-text-field>
        </div>
        <!-- 88번 §15 — 테이블스페이스(물리) / 업무영역(논리) 검색 -->
        <div class="fgrp">
          <span class="filterLabel">테이블스페이스</span>
          <v-select v-model="searchTablespaceMode" :items="searchModeOptions" item-text="label" item-value="value"
            dense outlined hide-details :style="{ width: '90px' }" />
          <v-text-field v-model="searchTablespace" clearable clear-icon="mdi-close-circle" color="ndColor"
            single-line dense outlined hide-details class="filterInput" :style="{ width: '120px' }" />
        </div>
        <div class="fgrp">
          <span class="filterLabel">업무영역</span>
          <v-select v-model="searchBizAreaId" :items="bizAreaOptions" item-text="bizAreaNm" item-value="bizAreaId"
            clearable dense outlined hide-details class="filterInput" :style="{ width: '140px' }" placeholder="전체" />
        </div>
        <!-- 104번 WP8-38 — 주제영역 (상위 영역을 고르면 하위 영역 테이블도) -->
        <div class="fgrp">
          <span class="filterLabel">주제영역</span>
          <v-select id="searchSubjArea" v-model="searchSubjAreaId" :items="subjAreaOptions" item-text="path" item-value="subjAreaId"
            clearable dense outlined hide-details class="filterInput" :style="{ width: '200px' }" placeholder="전체" />
        </div>
        <div class="fgrp">
          <v-btn class="gradient btn-auto" v-on:click="load" :style="{ padding: '0 16px' }">조회</v-btn>
          <v-btn class="btn-auto" color="primary" :disabled="!selectedModelId" v-on:click="openAddObjDialog" :style="{ padding: '0 14px' }">테이블 추가</v-btn>
          <!-- 86번 #11 — 엑셀 드롭다운 (업로드/양식/다운로드) -->
          <v-menu offset-y>
            <template v-slot:activator="{ on, attrs }">
              <v-btn class="gradient btn-auto" v-bind="attrs" v-on="on" :disabled="!selectedModelId">
                <v-icon small left>mdi-file-excel</v-icon>엑셀
                <v-icon small right>mdi-menu-down</v-icon>
              </v-btn>
            </template>
            <v-list dense>
              <v-list-item id="btn-upload-tables" @click="triggerUploadTables">
                <v-list-item-icon><v-icon small>mdi-upload</v-icon></v-list-item-icon>
                <v-list-item-title>엑셀 업로드</v-list-item-title>
              </v-list-item>
              <v-list-item id="btn-download-tables-template" @click="downloadTablesTemplate">
                <v-list-item-icon><v-icon small>mdi-file-download-outline</v-icon></v-list-item-icon>
                <v-list-item-title>양식 다운로드</v-list-item-title>
              </v-list-item>
              <v-list-item :disabled="dmTableAllItems.length === 0" @click="tableDataDownload">
                <v-list-item-icon><v-icon small>mdi-download</v-icon></v-list-item-icon>
                <v-list-item-title>데이터 다운로드</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
          <v-btn class="gradient btn-auto" v-on:click="submitModalShow = true"
            title="저장한 변경(DRAFT)을 관리자에게 신청">
            <v-icon small left>mdi-send-clock-outline</v-icon>내 변경 신청
          </v-btn>
        </div>
        <input ref="uploadTablesInput" type="file" accept=".xlsx" style="display:none" @change="onTableFileSelected" />
      </v-row>
    </v-sheet>

    <!-- 엑셀 업로드 미리보기 다이얼로그 -->
    <!-- 모달 콘텐츠가 길어져도 footer(버튼)·pagination 이 잘리지 않도록 v-card-text 내부 스크롤 -->
    <v-dialog v-model="uploadDialog" max-width="1100" persistent scrollable>
      <v-card>
        <v-card-title>
          테이블 엑셀 업로드 미리보기
          <v-spacer />
          <span v-if="uploadSummary" style="font-size:.85rem;color:#455A64;">
            총 {{ uploadSummary.total }} / 등록 예정 {{ uploadSummary.toInsert }} / 스킵 {{ uploadSummary.skipped }} / 오류 {{ (uploadErrors || []).length }}
          </span>
        </v-card-title>
        <v-card-text style="max-height: 70vh; overflow-y: auto;">
          <v-alert v-if="uploadErrors && uploadErrors.length > 0" type="error" dense text>
            오류 {{ uploadErrors.length }}건 — 수정 후 다시 업로드하세요.
            <div v-for="(e, i) in uploadErrors.slice(0, 5)" :key="'e' + i" style="font-size:.8rem;">
              · {{ e.row }}행: {{ e.message }}
            </div>
            <div v-if="uploadErrors.length > 5" style="font-size:.8rem;">외 {{ uploadErrors.length - 5 }}건</div>
          </v-alert>
          <v-alert v-if="uploadWarnings && uploadWarnings.length > 0" type="warning" dense text>
            경고 {{ uploadWarnings.length }}건
            <div v-for="(w, i) in uploadWarnings.slice(0, 5)" :key="'w' + i" style="font-size:.8rem;">
              · {{ w.row }}행: {{ w.message }}
            </div>
            <div v-if="uploadWarnings.length > 5" style="font-size:.8rem;">외 {{ uploadWarnings.length - 5 }}건</div>
          </v-alert>
          <v-data-table :items="uploadRows" :headers="uploadRowHeaders" dense
            :items-per-page="20" class="preview-grid"
            :item-class="rowClass">
            <template #[`item._action`]="{ item }">
              <span v-if="item._action === 'INSERT'" style="color:#2E7D32;font-weight:600;">등록</span>
              <span v-else-if="item._action === 'SKIP'" style="color:#F57C00;">스킵</span>
              <span v-else-if="item._action === 'ERROR'" style="color:#D32F2F;font-weight:600;">오류</span>
              <span v-else>{{ item._action }}</span>
            </template>
          </v-data-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="uploadDialog = false">취소</v-btn>
          <v-btn id="btn-upload-tables-commit" color="primary"
            :disabled="!uploadSummary || (uploadErrors && uploadErrors.length > 0) || uploadSummary.toInsert === 0"
            :loading="uploadCommitting" @click="commitTablesUpload">
            {{ uploadSummary ? uploadSummary.toInsert : 0 }}건 등록 실행
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 테이블 추가/수정 다이얼로그 -->
    <v-dialog v-model="objDialog" max-width="600" persistent scrollable>
      <v-card>
        <v-card-title>{{ objDialogMode === 'add' ? '테이블 추가' : '테이블 수정' }}</v-card-title>
        <v-card-text class="pt-3">
          <v-text-field v-model="objForm.objNm" label="테이블 영문명 (물리, 예: TB_USER)"
            :hint="objDialogMode === 'edit' && objForm.objNm !== objForm.origObjNm
              ? '물리명 변경 시 하위 컬럼·인덱스·제약조건의 참조까지 함께 갱신됩니다 (확인 단계 표시)'
              : '영문/숫자/언더바'"
            persistent-hint outlined dense class="mb-3" />
          <v-text-field v-model="objForm.objNmKr" label="테이블 한글명 (논리)" outlined dense />
          <v-text-field v-model="objForm.objOwner" label="소유자 (스키마)" outlined dense />
          <v-text-field v-model="objForm.objDesc" label="테이블 설명" outlined dense />
          <!-- 88번 §15 — 테이블스페이스 (물리) + 업무영역 (논리) -->
          <v-text-field v-model="objForm.tablespaceNm" label="테이블스페이스 (선택, 물리)" outlined dense
            class="hdr-physical-bg mb-3" persistent-hint hint="DDL 출력 시 TABLESPACE 절 자동 포함" />
          <v-select v-model="objForm.bizAreaId" :items="bizAreaOptions" item-text="bizAreaNm" item-value="bizAreaId"
            label="업무영역 (논리, 선택)" outlined dense clearable
            class="hdr-logical-bg" />
          <!-- 104번 WP8-38 -->
          <v-select id="objSubjArea" v-model="objForm.subjAreaId" :items="subjAreaOptions" item-text="path" item-value="subjAreaId"
            label="주제영역 (선택)" outlined dense clearable />
          <!-- 104번 WP8-31·75 — 사용자 정의 속성 (관리 > 사용자 정의 속성에서 정의) -->
          <div v-if="udpDefs.length" class="udp-title">관리 항목</div>
          <template v-for="d in udpDefs">
            <v-select v-if="d.dataTp === 'SELECT'" :key="d.udpId" :id="'udp_' + d.udpId" v-model="objForm.udpVals[d.udpId]"
              :items="(d.selectVals || '').split('\n')" :label="d.udpNm + (d.requiredYn === 'Y' ? ' *' : '')"
              :hint="d.udpDesc" persistent-hint outlined dense clearable class="mb-3" />
            <v-text-field v-else :key="d.udpId" :id="'udp_' + d.udpId" v-model="objForm.udpVals[d.udpId]"
              :type="d.dataTp === 'NUMBER' ? 'number' : (d.dataTp === 'DATE' ? 'date' : 'text')"
              :label="d.udpNm + (d.requiredYn === 'Y' ? ' *' : '')"
              :hint="d.udpDesc" persistent-hint outlined dense class="mb-3" />
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="objDialog = false">취소</v-btn>
          <v-btn color="primary" @click="submitObj">{{ objDialogMode === 'add' ? '추가' : '수정' }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 목록 카운트 + 페이지 크기 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ dmTableItems.length }}건</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 일괄 삭제 버튼 (선택 시 표시) — 검색결과 전체 삭제 옵션 별도 -->
    <v-sheet class="px-4 pb-2" v-show="selectedModelId">
      <v-btn small class="btn-auto" color="error" outlined :disabled="selectedRows.length === 0" @click="deleteSelected"
        :style="{ marginRight: '8px' }">
        <v-icon small left>mdi-delete-sweep</v-icon>선택 삭제 ({{ selectedRows.length }})
      </v-btn>
      <v-btn small class="btn-auto" color="error" :disabled="dmTableItems.length === 0" @click="deleteAllVisible">
        <v-icon small left>mdi-delete-empty</v-icon>검색결과 전체 삭제 ({{ dmTableItems.length }})
      </v-btn>
      <span style="color:#888; font-size:.8rem; margin-left:12px;">선택 삭제 = 체크박스 / 전체 삭제 = 현재 검색·필터 조건의 모든 행</span>
    </v-sheet>

    <!-- 테이블 목록 -->
    <!-- 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → item-key 에 OWNER 포함, 기본 정렬도 OWNER 우선 -->
    <v-data-table id="dmTable_table" :headers="dmTabledetaileHeaders" :items="dmTableItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="_rowKey" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요."
      multi-sort :sort-by="['objOwner', 'objNm']" :sort-desc="[false, false]"
      show-select v-model="selectedRows"
      :item-class="row => row.aprvStatus === 'REJECTED' ? 'row-rejected' : ((row.aprvStatus === 'DRAFT' || row.aprvStatus === 'SUBMITTED') ? 'row-draft' : '')">
      <template #[`item.objNm`]="{ item }">
        <a class="ndColor--text" style="cursor:pointer; text-decoration:underline;" @click="goToColumn(item)">{{ item.objNm }}</a>
        <v-chip v-if="govLabel(item)" x-small label class="ml-1" :color="govColor(item)" text-color="white">
          {{ govLabel(item) }}
        </v-chip>
      </template>
      <!-- 104번 WP8 — 주제영역은 마지막 이름만 (경로는 마우스를 올리면), 관리 항목은 한 줄에 하나 -->
      <template #[`item.subjAreaPath`]="{ item }">
        <span class="subj-path" :title="item.subjAreaPath">{{ item.subjAreaPath ? item.subjAreaPath.split(' > ').pop() : '' }}</span>
      </template>
      <template #[`item.udpSummary`]="{ item }">
        <div v-for="(s, i) in (item.udpSummary ? item.udpSummary.split(' · ') : [])" :key="i" class="udp-summary" :title="s">{{ s }}</div>
      </template>
      <template #[`item.actions`]="{ item }">
        <v-btn icon small :disabled="!selectedModelId" @click="openEditObjDialog(item)" title="수정">
          <v-icon small>mdi-pencil</v-icon>
        </v-btn>
        <v-btn icon small :disabled="!selectedModelId" @click="deleteObj(item)" title="삭제">
          <v-icon small color="error">mdi-delete</v-icon>
        </v-btn>
      </template>
      <template #top>
        <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loadTable" class="text-center">데이터가 존재하지 않습니다.</v-alert>
        <span v-show="loadTable">잠시만 기다려주세요.</span>
      </template>
    </v-data-table>

    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-2 pb-2 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
          next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>

    <!-- 88번 거버넌스 — 내 변경 신청 모달 (DRAFT 일괄 → SUBMITTED) -->
    <DMSubmitModal v-model="submitModalShow" @submitted="load" />
  </v-main>
</template>

<script>
import axios from 'axios';
import { eventBus } from '../eventBus';
import DMSubmitModal from './DMSubmitModal.vue';

export default {
  name: 'DSDatamodelStatusTable',
  components: { DMSubmitModal },
  props: ['isMobile'],
  watch: {
    dmTableItems() {
      this.pageCount = Math.ceil(this.dmTableItems.length / this.itemsPerPage);
      if (this.page > this.pageCount) this.page = Math.max(1, this.pageCount);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.dmTableItems.length / this.itemsPerPage);
      if (this.page > this.pageCount) this.page = Math.max(1, this.pageCount);
    },
  },
  data: () => ({
    modelList: [],
    dmTableAllItems: [],
    selectedModelId: null,
    selectedRows: [],   // 체크박스로 선택된 행들 (일괄 삭제용)
    submitModalShow: false,
    // 86번 #11 — 검색 필드 (소유자 추가, 한·영 모두 모드 셀렉트)
    searchOwner: '',
    searchOwnerMode: 'contains',
    searchTable: '',
    searchTableMode: 'contains',
    searchTableKr: '',
    searchTableKrMode: 'contains',
    searchTablespace: '',
    searchTablespaceMode: 'contains',
    searchBizAreaId: null,
    searchSubjAreaId: null,
    searchModeOptions: [
      { value: 'contains', label: '포함' },
      { value: 'exact',    label: '완전 일치' },
      { value: 'start',    label: '앞' },
      { value: 'end',      label: '뒤' },
    ],
    loadTable: false,
    page: 1,
    pageCount: null,
    itemsPerPage: 10,
    tableViewLengthList: [10, 20, 30, 40, 50],
    dmTabledetaileHeaders: [
      { text: '소유자', sortable: true, align: 'center', value: 'objOwner', width: '100px' },
      { text: '업무영역 (논리)', sortable: true, align: 'center', value: 'bizAreaNm', width: '110px' },
      { text: '주제영역', sortable: true, align: 'center', value: 'subjAreaPath', width: '110px' },
      { text: '테이블 한글명 (논리)', sortable: true, align: 'center', value: 'objNmKr' },
      { text: '테이블 영문명 (물리)', sortable: true, align: 'center', value: 'objNm' },
      { text: '컬럼개수', sortable: true, align: 'center', value: 'objAttrCnt', width: '96px' },
      { text: '테이블스페이스 (물리)', sortable: true, align: 'center', value: 'tablespaceNm', width: '140px' },
      { text: '관리 항목', sortable: false, align: 'start', value: 'udpSummary', width: '150px' },
      { text: '테이블 설명', sortable: false, align: 'center', value: 'objDesc' },
      { text: '편집', align: 'center', sortable: false, value: 'actions', width: '90px' },
    ],
    objDialog: false,
    objDialogMode: 'add',
    objForm: { objNm: '', objNmKr: '', objOwner: '', objDesc: '', origObjNm: '', tablespaceNm: '', bizAreaId: null, subjAreaId: null, udpVals: {} },
    // 88번 §15 — 업무영역 옵션
    bizAreaOptions: [],
    // 104번 WP8-38 — 주제영역 옵션 (path = "상위 > 하위")
    subjAreaOptions: [],
    // 104번 WP8-31·75 — 사용 중인 사용자 정의 속성 정의, 테이블별 값 요약 (owner|objNm → "이름: 값 · …")
    udpDefs: [],
    udpSummaryMap: {},
    // 엑셀 업로드
    uploadDialog: false,
    uploadFile: null,
    uploadRows: [],
    uploadErrors: [],
    uploadWarnings: [],
    uploadSummary: null,
    uploadCommitting: false,
    uploadRowHeaders: [
      { text: '행', value: 'row', align: 'center', sortable: false, width: '60px' },
      { text: '상태', value: '_action', align: 'center', sortable: false, width: '70px' },
      { text: '소유자', value: 'objOwner', align: 'center', sortable: false, width: '110px' },
      { text: '업무영역', value: 'bizAreaNm', align: 'center', sortable: false, width: '120px' },
      { text: '테이블명(한글)', value: 'objNmKr', align: 'center', sortable: false },
      { text: '테이블명(영문)', value: 'objNm', align: 'center', sortable: false, width: '180px' },
      { text: '테이블스페이스', value: 'tablespaceNm', align: 'center', sortable: false, width: '130px' },
      { text: '설명', value: 'objDesc', sortable: false },
      { text: '메시지', value: '_msg', sortable: false },
    ],
  }),
  computed: {
    dmTableItems() {
      // 86번 #11 — 소유자/영문/한글 매칭 + _rowKey (owner||objNm) 부여 (같은 obj_nm 다른 owner 동시 표시 가능)
      return this.dmTableAllItems
        .filter(item =>
          this._matchName(item.objOwner, this.searchOwner,   this.searchOwnerMode)
          && this._matchName(item.objNm,    this.searchTable,   this.searchTableMode)
          && this._matchName(item.objNmKr,  this.searchTableKr, this.searchTableKrMode)
          && this._matchName(item.tablespaceNm, this.searchTablespace, this.searchTablespaceMode)
          && (!this.searchBizAreaId || item.bizAreaId === this.searchBizAreaId)
          && this._inSubj(item.subjAreaId)
        )
        .map(item => ({
          ...item,
          _rowKey: (item.objOwner || '') + '' + item.objNm,
          subjAreaPath: this.subjPathMap[item.subjAreaId] || '',
          udpSummary: this.udpSummaryMap[(item.objOwner || '') + '|' + item.objNm] || '',
        }));
    },
    subjPathMap() {
      const m = {};
      this.subjAreaOptions.forEach(s => { m[s.subjAreaId] = s.path; });
      return m;
    },
  },
  methods: {
    // 88번 거버넌스 — 미승인 상태 칩 라벨/색 (DRAFT/SUBMITTED/REJECTED)
    govLabel(item) {
      const s = item && item.aprvStatus;
      if (s === 'DRAFT') return '미승인';
      if (s === 'SUBMITTED') return '승인대기';
      if (s === 'REJECTED') return '반려';
      return '';
    },
    govColor(item) {
      return (item && item.aprvStatus === 'REJECTED') ? 'error' : 'warning';
    },
    // 88번 §15 — 영역 옵션 로드
    loadAreaOptions() {
      axios.post(this.$APIURL.base + 'api/area/biz/list', {}).then(r => { this.bizAreaOptions = r.data || []; }).catch(() => {});
      axios.post(this.$APIURL.base + 'api/area/subj/list', {}).then(r => { this.subjAreaOptions = r.data || []; }).catch(() => {});
      axios.post(this.$APIURL.base + 'api/udp/defs', { useOnly: 'Y' }).then(r => { this.udpDefs = r.data || []; }).catch(() => {});
    },
    /** 104번 WP8-38 — 고른 주제영역이나 그 하위 영역에 연결된 테이블 */
    _inSubj(subjAreaId) {
      if (!this.searchSubjAreaId) return true;
      const sel = this.subjPathMap[this.searchSubjAreaId];
      const p = this.subjPathMap[subjAreaId];
      return !!p && (p === sel || p.startsWith(sel + ' > '));
    },
    /** 104번 WP8-31·75 — 테이블별 관리 항목 요약 */
    loadUdpSummary() {
      axios.post(this.$APIURL.base + 'api/udp/summary', { dmId: this.selectedModelId }).then(r => {
        const m = {};
        (r.data || []).forEach(s => { m[(s.objOwner || '') + '|' + s.objNm] = s.summary; });
        this.udpSummaryMap = m;
      }).catch(() => {});
    },
    /** 창을 열 때 속성 칸을 모두 만들어 둔다 (v-model 반응성) */
    _udpForm(vals) {
      const f = {};
      this.udpDefs.forEach(d => { f[d.udpId] = (vals && vals[d.udpId]) || ''; });
      return f;
    },
    /** 86번 #11 — 검색 모드 매칭: contains/exact/start/end */
    _matchName(value, keyword, mode) {
      if (!keyword) return true;
      const v = (value || '').toLowerCase();
      const k = keyword.toLowerCase();
      if (mode === 'exact') return v === k;
      if (mode === 'start') return v.startsWith(k);
      if (mode === 'end')   return v.endsWith(k);
      return v.includes(k);
    },
    /** 86번 #11 — 백엔드 raw exception 차단, 친화적 메세지 변환 */
    _friendlyErrText(err, fallback) {
      const status = (err && err.response && err.response.status) || 0;
      const data   = (err && err.response && err.response.data) || {};
      const our    = data.resultMessage;
      const raw    = data.message;
      const rawIsTechnical = raw && /JSON|deserialize|parse|MismatchedInput|HttpMessageNotReadable|Exception|NullPointer|invalid|cannot/i.test(raw);
      if (our) return our;
      if (raw && !rawIsTechnical) return raw;
      if (status >= 500) return (fallback || '서버 처리 중 오류가 발생했습니다.') + ' (관리자에게 문의해 주세요)';
      if (status === 400) return (fallback || '입력값이 올바르지 않습니다.');
      if (status === 401 || status === 403) return '권한이 없습니다.';
      if (status === 404) return '요청한 자원을 찾을 수 없습니다.';
      return fallback || (err && err.message) || '알 수 없는 오류가 발생했습니다.';
    },
    getModelList() {
      axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
        'schNm': null, 'schSysNm': null
      }).then((res) => {
        this.modelList = res.data.map(item => ({
          dataModelId: item.dataModelId,
          dataModelNm: item.dataModelNm,
        }));
      }).catch(() => {
        this.$swal.fire({ title: '데이터 모델 목록 로드 실패', confirmButtonText: '확인', icon: 'error' });
      });
    },
    onModelChange(modelId) {
      this.dmTableAllItems = [];
      if (!modelId) return;
      this.load();
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loadTable = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.dmTableAllItems = res.data;
        this.loadTable = false;
        this.loadUdpSummary();
      }).catch(() => {
        this.$swal.fire({ title: '테이블 정보 로드 실패', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
    },
    _applyPendingView(pending) {
      var self = this;
      var apply = function() {
        self.selectedModelId = pending.modelId;
        self.$nextTick(function() { self.load(); });
      };
      if (this.modelList.length > 0) {
        apply();
      } else {
        axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
          'schNm': null
        }).then(function(res) {
          self.modelList = res.data.map(function(item) {
            return { dataModelId: item.dataModelId, dataModelNm: item.dataModelNm };
          });
          apply();
        });
      }
    },
    goToColumn(item) {
      // 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → tableOwner 도 같이 전달
      eventBus.pendingColumnView = {
        modelId: this.selectedModelId,
        clctId: this.selectedClctId,
        tableOwner: item.objOwner || '',
        tableNm: item.objNm,
      };
      eventBus.$emit('openColumnView');
    },
    tableDataDownload() {
      // 86번 #11 — `selectedClctId` 가 이 컴포넌트에 없어서 항상 undefined 였던 버그.
      // CLCT 폐기 이후 매퍼는 DM_ID 기준이라 `selectedModelId` 로 변경.
      axios.get(this.$APIURL.base + "api/dm/downloadDataModelObjs", {
        params: { 'clctId': this.selectedModelId },
        responseType: 'blob',
        headers: { "Accept": "application/vnd.ms-excel" }
      }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/csv" }));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", `데이터모델_테이블정보_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(url);
        link.remove();
      }).catch(() => {
        this.$swal.fire({ title: '테이블 정보 다운로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
      });
    },
    openAddObjDialog() {
      this.objDialogMode = 'add';
      this.objForm = { objNm: '', objNmKr: '', objOwner: '', objDesc: '', origObjNm: '', tablespaceNm: '', bizAreaId: null, subjAreaId: null, udpVals: this._udpForm(null) };
      this.loadAreaOptions();
      this.objDialog = true;
    },
    openEditObjDialog(item) {
      this.objDialogMode = 'edit';
      this.objForm = {
        objNm: item.objNm, objNmKr: item.objNmKr || '',
        objOwner: item.objOwner || '', objDesc: item.objDesc || '',
        origObjNm: item.objNm, origObjOwner: item.objOwner || '',
        tablespaceNm: item.tablespaceNm || '',
        bizAreaId: item.bizAreaId || null,
        subjAreaId: item.subjAreaId || null,
        udpVals: this._udpForm(null),
      };
      this.loadAreaOptions();
      // 104번 WP8-31 — 저장된 속성 값
      axios.post(this.$APIURL.base + 'api/udp/vals', { dmId: this.selectedModelId, objOwner: item.objOwner || '', objNm: item.objNm }).then(r => {
        this.objForm.udpVals = this._udpForm(r.data || {});
      }).catch(() => {});
      this.objDialog = true;
    },
    async submitObj() {
      if ((!this.objForm.objNm || !this.objForm.objNm.trim()) && (!this.objForm.objNmKr || !this.objForm.objNmKr.trim())) {
        this.$swal.fire({ title: '테이블명(물리명) 또는 한글명(논리명) 중 하나는 입력해야 합니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      // 86번 #11 — 영문명(물리) 정규식 검증. 영문/숫자/언더바만 허용 + 영문/언더바로 시작.
      // (예전엔 백틱·공백·특수문자 통과 → DDL 생성 시 깨짐. SQL 예약어와 충돌도 잠재.)
      if (this.objForm.objNm && this.objForm.objNm.trim()) {
        const en = this.objForm.objNm.trim();
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(en)) {
          this.$swal.fire({
            title: '테이블 영문명(물리) 형식 오류',
            html: `<div style="text-align:left">
              영문(A-Z,a-z) / 숫자(0-9) / 언더바(_) 만 허용됩니다.<br>
              첫 글자는 영문 또는 언더바여야 합니다.<br><br>
              <span style="color:#D32F2F">입력값: <code>${en}</code></span>
            </div>`,
            icon: 'error',
            confirmButtonText: '확인',
          });
          return;
        }
        if (en.length > 128) {
          this.$swal.fire({ title: '테이블 영문명이 너무 깁니다 (최대 128자).', icon: 'error', confirmButtonText: '확인' });
          return;
        }
      }
      // 편집 모드 + 물리명 변경됨 → 영향 범위 사전 알림
      if (this.objDialogMode === 'edit' &&
          this.objForm.origObjNm &&
          this.objForm.objNm.trim() !== this.objForm.origObjNm) {
        const newNm = this.objForm.objNm.trim();
        try {
          const pv = await axios.get(this.$APIURL.base + 'api/dm/previewObjRename', {
            params: {
              dataModelId: this.selectedModelId,
              objOwner:    this.objForm.origObjOwner,   // 110 4.12-17 — 그 테이블의 소유자로 센다
              origObjNm:   this.objForm.origObjNm,
              newObjNm:    newNm,
            }
          });
          const d = pv.data || {};
          if (d.conflict) {
            this.$swal.fire({
              title: '이미 같은 이름의 테이블이 있습니다',
              text: `'${newNm}' 은(는) 이 모델 안에 이미 존재합니다. 다른 이름을 사용해주세요.`,
              icon: 'error', confirmButtonText: '확인',
            });
            return;
          }
          const html =
            `<div style="text-align:left; font-size:.9rem;">` +
            `<b>${this.objForm.origObjNm}</b> → <b>${newNm}</b> 로 변경합니다.<br><br>` +
            `함께 갱신되는 항목:<br>` +
            `· 컬럼 <b>${d.attrCnt || 0}</b>건<br>` +
            `· 인덱스 <b>${d.indexCnt || 0}</b>건<br>` +
            `· 제약조건 (이 테이블) <b>${d.constraintCnt || 0}</b>건<br>` +
            `· 외래키 참조 (다른 테이블 → 이 테이블) <b>${d.refConstraintCnt || 0}</b>건<br><br>` +
            `진행하시겠습니까?` +
            `</div>`;
          const confirm = await this.$swal.fire({
            title: '물리 테이블명 변경 확인',
            html, icon: 'warning',
            showCancelButton: true, confirmButtonText: '변경', cancelButtonText: '취소',
          });
          if (!confirm.isConfirmed) return;
        } catch (err) {
          this.$swal.fire({ title: '영향 범위 조회 실패', text: this._friendlyErrText(err, '영향 범위 조회 중 오류가 발생했습니다.'), icon: 'error', confirmButtonText: '확인' });
          return;
        }
      }
      const url = this.objDialogMode === 'add' ? 'api/dm/addObj' : 'api/dm/updateObj';
      const payload = { ...this.objForm, dataModelId: this.selectedModelId };
      axios.post(this.$APIURL.base + url, payload).then((res) => {
        if (res.data && res.data.resultCode === 200) {
          this.$swal.fire({ title: this.objDialogMode === 'add' ? '테이블이 추가되었습니다.' : '테이블이 수정되었습니다.', confirmButtonText: '확인', icon: 'success' });
          this.objDialog = false;
          this.load();
        } else {
          this.$swal.fire({ title: '저장 실패', text: (res.data && res.data.resultMessage) || '저장 중 오류', confirmButtonText: '확인', icon: 'error' });
        }
      }).catch((e) => {
        this.$swal.fire({ title: '저장 실패', text: e.message, confirmButtonText: '확인', icon: 'error' });
      });
    },
    // ===== 엑셀 업로드 =====
    downloadTablesTemplate() {
      const url = this.$APIURL.base + 'api/dm/uploadTemplate?scope=tables';
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'navidmeta_tables_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    triggerUploadTables() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델을 먼저 선택하세요.', icon: 'warning' });
        return;
      }
      this.$refs.uploadTablesInput.value = '';
      this.$refs.uploadTablesInput.click();
    },
    onTableFileSelected(e) {
      const f = e.target.files && e.target.files[0];
      if (!f) return;
      this.uploadFile = f;
      this._runTablesUpload('preview');
    },
    _runTablesUpload(mode) {
      if (!this.uploadFile) return;
      if (mode === 'commit') this.uploadCommitting = true;
      const fd = new FormData();
      fd.append('file', this.uploadFile);
      fd.append('dataModelId', this.selectedModelId);
      fd.append('mode', mode);
      axios.post(this.$APIURL.base + 'api/dm/uploadTables', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((res) => {
        if (!(res.data && res.data.resultCode === 200)) {
          this.$swal.fire({ title: '업로드 실패', text: (res.data && res.data.resultMessage) || '서버 오류', icon: 'error' });
          this.uploadCommitting = false;
          return;
        }
        let payload = {};
        try {
          const c = res.data.contents;
          payload = typeof c === 'string' ? JSON.parse(c) : (c || {});
        } catch (err) { payload = {}; }
        this.uploadRows = payload.tables || [];
        this.uploadErrors = payload.errors || [];
        this.uploadWarnings = payload.warnings || [];
        this.uploadSummary = payload.summary || null;
        if (mode === 'commit') {
          this.uploadCommitting = false;
          this.uploadDialog = false;
          const s = this.uploadSummary || {};
          const inserted = s.toInsert || 0;
          const skipped = s.skipped || 0;
          const errs = (this.uploadErrors || []).length;
          const html = `<div style="text-align:left">
            등록: <b style="color:#2E7D32">${inserted}건</b><br>
            중복 스킵: <b style="color:#F57C00">${skipped}건</b><br>
            오류: <b style="color:#D32F2F">${errs}건</b>
          </div>`;
          this.$swal.fire({ title: '엑셀 업로드 완료', html: html, icon: 'success' });
          this.load();
        } else {
          this.uploadDialog = true;
        }
      }).catch((err) => {
        this.uploadCommitting = false;
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || err.message || '업로드 실패';
        this.$swal.fire({ title: '업로드 실패', text: msg, icon: 'error' });
      });
    },
    commitTablesUpload() {
      this._runTablesUpload('commit');
    },
    rowClass(item) {
      if (!item) return '';
      if (item._action === 'ERROR') return 'row-upload-error';
      if (item._action === 'SKIP') return 'row-upload-skip';
      return '';
    },
    deleteObj(item) {
      this.$swal.fire({
        title: '테이블을 삭제할까요?',
        text: `${item.objNm} 및 하위 컬럼이 함께 삭제됩니다.`,
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소', icon: 'warning',
      }).then((r) => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + "api/dm/deleteObj", {
          dataModelId: this.selectedModelId,
          objOwner: item.objOwner || '',
          objNm: item.objNm,
        }).then((res) => {
          if (res.data && res.data.resultCode === 200) {
            this.$swal.fire({ title: '삭제되었습니다.', confirmButtonText: '확인', icon: 'success' });
            this.load();
          } else {
            this.$swal.fire({ title: '삭제 실패', text: (res.data && res.data.resultMessage) || '', confirmButtonText: '확인', icon: 'error' });
          }
        });
      });
    },
    // 선택 삭제 — 체크박스로 선택한 행들 일괄 삭제
    deleteSelected() {
      if (this.selectedRows.length === 0) return;
      this._bulkDelete(this.selectedRows, `선택한 테이블 ${this.selectedRows.length}건`);
    },
    // 검색결과 전체 삭제 — 현재 필터링된 모든 행 삭제
    deleteAllVisible() {
      if (this.dmTableItems.length === 0) return;
      this._bulkDelete(this.dmTableItems, `현재 검색결과 ${this.dmTableItems.length}건 전체`);
    },
    _bulkDelete(items, label) {
      const sampleNames = items.slice(0, 5).map(i => i.objNm).join(', ');
      const moreText = items.length > 5 ? ` 외 ${items.length - 5}건` : '';
      this.$swal.fire({
        title: `${label} 삭제`,
        html: `<div style="text-align:left;">${label} 및 <b>하위 컬럼·인덱스·제약조건</b>이 함께 삭제됩니다.<br><br>` +
              `<span style="color:#666;font-size:.85rem;">대상: ${sampleNames}${moreText}</span><br><br>` +
              `<b style="color:#D32F2F;">이 작업은 되돌릴 수 없습니다.</b></div>`,
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소', icon: 'warning',
        confirmButtonColor: '#D32F2F',
      }).then((r) => {
        if (!r.isConfirmed) return;
        const payload = {
          dataModelId: this.selectedModelId,
          items: items.map(i => ({ objOwner: i.objOwner || '', objNm: i.objNm })),
        };
        axios.post(this.$APIURL.base + "api/dm/deleteObjs", payload).then((res) => {
          if (res.data && res.data.resultCode === 200) {
            let deleted = items.length;
            try {
              const c = JSON.parse(res.data.contents || '{}');
              if (typeof c.deleted === 'number') deleted = c.deleted;
            } catch (e) { /* ignore */ }
            this.$swal.fire({ title: `${deleted}건 삭제됨`, confirmButtonText: '확인', icon: 'success' });
            this.selectedRows = [];
            this.load();
          } else {
            this.$swal.fire({ title: '삭제 실패', text: (res.data && res.data.resultMessage) || '', confirmButtonText: '확인', icon: 'error' });
          }
        }).catch((e) => {
          this.$swal.fire({ title: '삭제 실패', text: e.message, confirmButtonText: '확인', icon: 'error' });
        });
      });
    },
  },
  created() {
    this.getModelList();
    this.loadAreaOptions();
  },
  mounted() {
    this.$resizableGrid();
  },
  activated() {
    if (eventBus.pendingTableView) {
      var pending = eventBus.pendingTableView;
      eventBus.pendingTableView = null;
      this._applyPendingView(pending);
    }
  },
}
</script>

<style scoped>
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }
.tableSpt { display: flex; justify-content: space-between; align-items: center; padding: 6px 20px; background: #FAFBFF; }
.split_bottom_wrap { position: absolute; width: 100%; max-height: 60px; bottom: 0px; border-top: 1px solid #E8EAF6; background: #FAFBFF; }
.pagination_wrap { position: relative; width: 100%; }
#dmTable_table { height: calc(100vh - 64px - 48px - 68px - 44px - 60px); overflow-y: overlay; overflow-x: hidden; }
.preview-grid { border: 1px solid #E0E0E0; }
/* 104번 WP8 */
.udp-title { font-size: .85rem; font-weight: 600; color: #37474F; margin: 4px 0 8px; }
.subj-path, .udp-summary { font-size: .78rem; color: #455A64; }
.udp-summary { max-width: 150px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; line-height: 1.5; }
.fgrp { display: flex; align-items: center; gap: 6px; flex-wrap: nowrap; }
</style>
<style>
/* 표 안쪽 행·칸은 v-data-table(자식 컴포넌트)이 그려 scoped 가 닿지 않는다 — 표 id 로 범위를 좁혀 전역으로 둔다 */
/* 88번 거버넌스 — 미승인(DRAFT/SUBMITTED) 노란 배경, 반려 빨간 배경 (scoped 라 그동안 안 먹었다) */
#dmTable_table .row-draft > td { background-color: #FFF8E1 !important; }
#dmTable_table .row-rejected > td { background-color: #FFEBEE !important; }
.preview-grid .row-upload-error > td { background-color: #FFEBEE !important; }
.preview-grid .row-upload-skip > td { background-color: #FFF8E1 !important; }
/* 104번 WP8 — 열이 늘어도 머리글·값이 한 글자씩 줄바꿈되지 않게 낱말 단위로. 좁은 화면은 가로 스크롤 */
#dmTable_table th, #dmTable_table td { word-break: keep-all; overflow-wrap: break-word; }
#dmTable_table .subj-path { word-break: break-all; }
/* 편집 열은 오른쪽에 붙여 가로 스크롤 때도 보이게 */
#dmTable_table th:last-child { position: sticky !important; right: 0; z-index: 2; box-shadow: -3px 0 4px rgba(0, 0, 0, .06); }
#dmTable_table td:last-child { position: sticky; right: 0; z-index: 1; background: #fff; white-space: nowrap; box-shadow: -3px 0 4px rgba(0, 0, 0, .06); }
</style>
