<template>
    <v-container fluid class="pa-4">
        <v-card class="mb-4" outlined>
            <v-card-title class="subtitle-1 font-weight-bold py-2">
                변경 이력 조회
            </v-card-title>
            <v-card-text>
                <!-- 86번 #38 에서 등록 경로 필터를 뺐다(그때는 DB 가 항상 NULL). 2026-08-23 부터 서버가 값을 채워 110번 3.12-14-c 에서 되살린다 -->
                <v-row dense align="center">
                    <v-col cols="2">
                        <v-select v-model="filter.targetType" :items="targetTypes" item-text="text" item-value="value"
                            label="대상" dense outlined hide-details />
                    </v-col>
                    <v-col cols="2">
                        <v-select v-model="filter.changeType" :items="changeTypes" item-text="text" item-value="value"
                            label="유형" dense outlined hide-details />
                    </v-col>
                    <v-col cols="2">
                        <v-select id="chgSourceFilter" v-model="filter.changeSource" :items="changeSources" item-text="text" item-value="value"
                            label="등록 경로" dense outlined hide-details />
                    </v-col>
                    <v-col cols="2">
                        <v-text-field v-model="filter.fromDt" label="시작일" type="date" dense outlined hide-details />
                    </v-col>
                    <v-col cols="2">
                        <v-text-field v-model="filter.toDt" label="종료일" type="date" dense outlined hide-details />
                    </v-col>
                    <v-col cols="2">
                        <v-btn color="primary" @click="search" small depressed>조회</v-btn>
                        <v-btn class="ml-2" @click="resetFilter" small depressed>초기화</v-btn>
                    </v-col>
                </v-row>
            </v-card-text>
        </v-card>

        <v-card outlined>
            <!-- 110번 3.12-15-f — 목록은 최근 200건까지만 온다. 다 찼으면 조건으로 좁히라고 알린다 -->
            <div v-if="historyList.length >= 200" id="chgLimitNote" class="chg-limit-note">
                최근 200건까지만 보입니다. 대상·유형·등록 경로·기간으로 좁혀 조회하세요.
            </div>
            <v-data-table :headers="headers" :items="historyList" :loading="loading" dense
                @click:row="onRowClick" item-key="changeId" single-select
                :items-per-page="20" :footer-props="{ 'items-per-page-options': [10, 20, 50, 100] }"
                no-data-text="조회된 이력이 없습니다." class="history-table">
                <template v-slot:item.changeType="{ item }">
                    <v-chip :color="getChangeTypeColor(item.changeType)" small dark>
                        {{ getChangeTypeLabel(item.changeType) }}
                    </v-chip>
                </template>
                <template v-slot:item.targetType="{ item }">
                    {{ getTargetTypeLabel(item.targetType) }}
                </template>
            </v-data-table>
        </v-card>

        <!-- 상세 표시 모달 — 86번 #34: VO toString 파싱 + INSERT/UPDATE/DELETE 분기 + 모달 크기 확장 -->
        <v-dialog v-model="detailDialog" max-width="1300" scrollable>
            <v-card v-if="selectedHistory">
                <v-card-title class="subtitle-1 font-weight-bold py-2">
                    변경 상세
                    <v-spacer />
                    <v-btn icon small @click="closeDetail"><v-icon>close</v-icon></v-btn>
                </v-card-title>
                <v-divider />
                <v-card-text style="max-height: 75vh;">
                    <v-row dense class="mt-2">
                        <v-col cols="3"><strong>변경유형:</strong> {{ getChangeTypeLabel(selectedHistory.changeType) }}</v-col>
                        <v-col cols="3"><strong>대상:</strong> {{ getTargetTypeLabel(selectedHistory.targetType) }}</v-col>
                        <v-col cols="3"><strong>대상명:</strong> {{ selectedHistory.targetNm }}</v-col>
                        <v-col cols="3"><strong>변경일시:</strong> {{ selectedHistory.changeDt }}</v-col>
                    </v-row>
                    <v-row dense>
                        <v-col cols="6"><strong>변경자:</strong> {{ selectedHistory.changeUserId }}</v-col>
                        <v-col cols="6"><strong>요약:</strong> {{ selectedHistory.summary }}</v-col>
                    </v-row>

                    <!-- INSERT (등록) — 등록된 정보만 보여줌 -->
                    <div v-if="selectedHistory.changeType === 'INSERT'" class="mt-4">
                        <div class="subtitle-2 mb-2">등록된 정보</div>
                        <v-simple-table dense v-if="parsedCurr.length > 0">
                            <thead>
                                <tr><th width="30%">항목</th><th width="70%">값</th></tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in parsedCurr" :key="row.key">
                                    <td>{{ getFieldLabel(row.key) }}</td>
                                    <td class="text-pre-wrap">{{ row.value || '(없음)' }}</td>
                                </tr>
                            </tbody>
                        </v-simple-table>
                        <div v-else class="grey--text">상세 정보가 없습니다.</div>
                    </div>

                    <!-- DELETE (삭제) — 삭제된 정보 -->
                    <div v-else-if="selectedHistory.changeType === 'DELETE'" class="mt-4">
                        <div class="subtitle-2 mb-2">삭제된 정보</div>
                        <v-simple-table dense v-if="parsedPrev.length > 0">
                            <thead>
                                <tr><th width="30%">항목</th><th width="70%">값</th></tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in parsedPrev" :key="row.key">
                                    <td>{{ getFieldLabel(row.key) }}</td>
                                    <td class="text-pre-wrap">{{ row.value || '(없음)' }}</td>
                                </tr>
                            </tbody>
                        </v-simple-table>
                        <div v-else class="grey--text">상세 정보가 없습니다.</div>
                    </div>

                    <!-- UPDATE (수정) — 변경 항목만 비교 (변경 안 된 필드 숨김) -->
                    <div v-else-if="selectedHistory.changeType === 'UPDATE'" class="mt-4">
                        <div class="subtitle-2 mb-2">변경된 항목</div>
                        <v-simple-table dense v-if="diffRows.length > 0">
                            <thead>
                                <tr>
                                    <th width="20%">항목</th>
                                    <th width="40%">이전값</th>
                                    <th width="40%">변경값</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in diffRows" :key="row.key">
                                    <td>{{ getFieldLabel(row.key) }}</td>
                                    <td class="text-pre-wrap"><span class="red--text">{{ row.prev || '(없음)' }}</span></td>
                                    <td class="text-pre-wrap"><span class="green--text">{{ row.curr || '(없음)' }}</span></td>
                                </tr>
                            </tbody>
                        </v-simple-table>
                        <div v-else class="grey--text">변경된 항목이 없습니다.</div>
                    </div>

                    <!-- 일괄 등록: 상세 건 목록 -->
                    <div v-else class="mt-4">
                        <v-data-table v-if="detailList.length > 0" :headers="detailHeaders" :items="detailList"
                            dense :items-per-page="10" no-data-text="상세 내역이 없습니다.">
                            <template v-slot:item.detailType="{ item }">
                                <v-chip :color="item.detailType === 'SUCCESS' ? 'success' : item.detailType === 'SKIPPED' ? 'warning' : 'error'"
                                    small dark>
                                    {{ item.detailType }}
                                </v-chip>
                            </template>
                        </v-data-table>
                        <div v-else class="grey--text">상세 건 목록이 없습니다.</div>
                    </div>
                </v-card-text>
                <v-divider />
                <v-card-actions>
                    <v-spacer />
                    <v-btn text @click="closeDetail">닫기</v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>
    </v-container>
</template>

<script>
import axios from "axios";
import { parseVoString, fieldLabel } from "./changeDiff";

export default {
    name: "DSChangeHistory",
    props: ["isMobile"],
    data() {
        return {
            loading: false,
            filter: {
                targetType: "",
                changeType: "",
                changeSource: "",
                fromDt: "",
                toDt: ""
            },
            targetTypes: [
                { text: "전체", value: "" },
                { text: "단어", value: "WORD" },
                { text: "용어", value: "TERM" },
                { text: "도메인", value: "DOMAIN" },
                { text: "코드", value: "CODE" },
                { text: "코드데이터", value: "CODE_DATA" },
                // 110번 3.12-7-f — 도메인 그룹·분류 일괄등록 이력
                { text: "도메인 그룹", value: "DOMAIN_GRP" },
                { text: "도메인 분류", value: "DOMAIN_CLSF" }
            ],
            changeTypes: [
                { text: "전체", value: "" },
                { text: "등록", value: "INSERT" },
                { text: "수정", value: "UPDATE" },
                { text: "삭제", value: "DELETE" },
                { text: "일괄등록", value: "BULK_INSERT" }
            ],
            changeSources: [
                { text: "전체", value: "" },
                { text: "관리자 직접", value: "ADMIN_DIRECT" },
                { text: "사용자 신청", value: "USER_REQUEST" },
                { text: "관리자 승인", value: "ADMIN_APPROVE" },
                { text: "관리자 반려", value: "ADMIN_REJECT" },
                { text: "일괄등록", value: "BULK_UPLOAD" },
                { text: "자동 추천", value: "AUTO_RECOMMEND" },
                { text: "공통표준 가져오기", value: "STD_IMPORT" }
            ],
            // 등록 경로 컬럼은 saveChangeHistory 가 changeSource 를 안 넘겨 항상 '-' 라
            // 한동안 빼두었다. 2026-08-23 서버에서 값을 채우도록 고쳐 되살린다.
            // 그 이전 이력은 값이 없어 '-' 로 보인다.
            headers: [
                { text: "유형", value: "changeType", width: "90px" },
                { text: "대상", value: "targetType", width: "90px" },
                { text: "대상명", value: "targetNm", width: "200px" },
                { text: "요약", value: "summary" },
                { text: "건수", value: "changeCnt", width: "70px", align: "center" },
                { text: "등록 경로", value: "changeSourceLabel", width: "110px" },
                { text: "변경자", value: "changeUserId", width: "100px" },
                { text: "변경일시", value: "changeDt", width: "180px" }
            ],
            detailHeaders: [
                { text: "순번", value: "seq", width: "80px" },
                { text: "대상명", value: "targetNm", width: "200px" },
                { text: "상태", value: "detailType", width: "100px" },
                { text: "비고", value: "remark" }
            ],
            historyList: [],
            selectedHistory: null,
            detailList: [],
            detailDialog: false
        };
    },
    mounted() {
        this.search();
    },
    computed: {
        parsedPrev() {
            return this._parseVoString(this.selectedHistory && this.selectedHistory.prevValue);
        },
        parsedCurr() {
            return this._parseVoString(this.selectedHistory && this.selectedHistory.currValue);
        },
        diffRows() {
            // UPDATE: 이전/변경값 모두 같은 키 기준 비교 → 다른 것만 노출
            const prev = {}; this.parsedPrev.forEach(r => { prev[r.key] = r.value; });
            const curr = {}; this.parsedCurr.forEach(r => { curr[r.key] = r.value; });
            const keys = new Set([...Object.keys(prev), ...Object.keys(curr)]);
            const diffs = [];
            for (const k of keys) {
                const a = prev[k] || ''; const b = curr[k] || '';
                if (a !== b) diffs.push({ key: k, prev: a, curr: b });
            }
            return diffs;
        }
    },
    methods: {
        // 86번 #34 — VO toString 파싱. 104번 WP9-55 — 다른 전후 비교 화면과 같은 코드(changeDiff.js)를 쓴다
        _parseVoString(s) {
            return parseVoString(s);
        },
        getFieldLabel(key) {
            return fieldLabel(key, 'STD');
        },
        search() {
            this.loading = true;
            this.detailDialog = false;
            this.selectedHistory = null;
            this.detailList = [];
            axios
                .post(this.$APIURL.base + "api/std/getChangeHistoryList", this.filter)
                .then(res => {
                    var sourceMap = { ADMIN_DIRECT: '관리자 직접', USER_REQUEST: '사용자 신청', ADMIN_APPROVE: '관리자 승인', ADMIN_REJECT: '관리자 반려', BULK_UPLOAD: '일괄등록', AUTO_RECOMMEND: '자동 추천', STD_IMPORT: '공통표준 가져오기' };
                    this.historyList = (res.data || []).map(function(h) {
                        h.changeSourceLabel = sourceMap[h.changeSource] || h.changeSource || '-';
                        return h;
                    });
                })
                .catch(err => {
                    console.error("이력 조회 실패:", err);
                    this.historyList = [];
                })
                .finally(() => {
                    this.loading = false;
                });
        },
        resetFilter() {
            this.filter = { targetType: "", changeType: "", changeSource: "", fromDt: "", toDt: "" };
            this.search();
        },
        onRowClick(item) {
            this.selectedHistory = null;
            this.detailList = [];
            axios
                .get(this.$APIURL.base + "api/std/getChangeHistoryDetail", {
                    params: { changeId: item.changeId }
                })
                .then(res => {
                    this.selectedHistory = res.data.history || item;
                    this.detailList = res.data.details || [];
                    this.detailDialog = true;
                })
                .catch(err => {
                    console.error("이력 상세 조회 실패:", err);
                    this.selectedHistory = item;
                    this.detailDialog = true;
                });
        },
        closeDetail() {
            this.detailDialog = false;
            this.selectedHistory = null;
            this.detailList = [];
        },
        getChangeTypeLabel(type) {
            const map = { INSERT: "등록", UPDATE: "수정", DELETE: "삭제", BULK_INSERT: "일괄등록" };
            return map[type] || type;
        },
        getChangeTypeColor(type) {
            const map = { INSERT: "success", UPDATE: "primary", DELETE: "error", BULK_INSERT: "indigo" };
            return map[type] || "grey";
        },
        getTargetTypeLabel(type) {
            const map = { WORD: "단어", TERM: "용어", DOMAIN: "도메인", CODE: "코드", CODE_DATA: "코드데이터", DOMAIN_GRP: "도메인 그룹", DOMAIN_CLSF: "도메인 분류" };
            return map[type] || type;
        }
    }
};
</script>

<style scoped>
.history-table >>> tbody tr {
    cursor: pointer;
}
.chg-limit-note {
    font-size: .8rem;
    color: #E65100;
    background: #FFF3E0;
    padding: 6px 12px;
}
.history-table >>> tbody tr:hover {
    background-color: #E8EAF6 !important;
}
/* 86번 #34 fix — 이전엔 display: block 이 td 에 적용돼서 셀이 가로로 안 펼쳐지고 세로로 쌓이는 버그.
   text-pre-wrap 은 td 자체가 아니라 td 안의 span 에 적용해야 함. 현재는 td.text-pre-wrap 로 사용 중이라
   display 만 빼고 vertical-align 조정. */
.text-pre-wrap {
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 0.85rem;
    max-height: 400px;
    overflow-y: auto;
    vertical-align: top !important;
}
</style>
