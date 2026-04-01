<template>
    <v-container fluid class="pa-4">
        <v-card class="mb-4" outlined>
            <v-card-title class="subtitle-1 font-weight-bold py-2">
                변경 이력 조회
            </v-card-title>
            <v-card-text>
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

        <!-- 상세 표시 영역 -->
        <v-card v-if="selectedHistory" outlined class="mt-4">
            <v-card-title class="subtitle-1 font-weight-bold py-2">
                변경 상세
                <v-spacer />
                <v-btn icon small @click="selectedHistory = null"><v-icon>close</v-icon></v-btn>
            </v-card-title>
            <v-card-text>
                <v-row dense>
                    <v-col cols="3"><strong>변경유형:</strong> {{ getChangeTypeLabel(selectedHistory.changeType) }}</v-col>
                    <v-col cols="3"><strong>대상:</strong> {{ getTargetTypeLabel(selectedHistory.targetType) }}</v-col>
                    <v-col cols="3"><strong>대상명:</strong> {{ selectedHistory.targetNm }}</v-col>
                    <v-col cols="3"><strong>변경일시:</strong> {{ selectedHistory.changeDt }}</v-col>
                </v-row>
                <v-row dense>
                    <v-col cols="6"><strong>변경자:</strong> {{ selectedHistory.changeUserId }}</v-col>
                    <v-col cols="6"><strong>요약:</strong> {{ selectedHistory.summary }}</v-col>
                </v-row>

                <!-- 개별 변경: 이전값/변경값 비교 -->
                <div v-if="selectedHistory.changeType !== 'BULK_INSERT'" class="mt-4">
                    <v-simple-table dense v-if="selectedHistory.prevValue || selectedHistory.currValue">
                        <thead>
                            <tr>
                                <th width="50%">이전값</th>
                                <th width="50%">변경값</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td class="text-pre-wrap">{{ selectedHistory.prevValue || '(없음)' }}</td>
                                <td class="text-pre-wrap">{{ selectedHistory.currValue || '(없음)' }}</td>
                            </tr>
                        </tbody>
                    </v-simple-table>
                    <div v-else class="grey--text">상세 변경 내역이 없습니다.</div>
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
        </v-card>
    </v-container>
</template>

<script>
import axios from "axios";

export default {
    name: "DSChangeHistory",
    props: ["isMobile"],
    data() {
        return {
            loading: false,
            filter: {
                targetType: "",
                changeType: "",
                fromDt: "",
                toDt: ""
            },
            targetTypes: [
                { text: "전체", value: "" },
                { text: "단어", value: "WORD" },
                { text: "용어", value: "TERM" },
                { text: "도메인", value: "DOMAIN" },
                { text: "코드", value: "CODE" },
                { text: "코드데이터", value: "CODE_DATA" }
            ],
            changeTypes: [
                { text: "전체", value: "" },
                { text: "등록", value: "INSERT" },
                { text: "수정", value: "UPDATE" },
                { text: "삭제", value: "DELETE" },
                { text: "일괄등록", value: "BULK_INSERT" }
            ],
            headers: [
                { text: "변경일시", value: "changeDt", width: "160px" },
                { text: "유형", value: "changeType", width: "100px" },
                { text: "대상", value: "targetType", width: "100px" },
                { text: "대상명", value: "targetNm", width: "200px" },
                { text: "요약", value: "summary" },
                { text: "건수", value: "changeCnt", width: "80px", align: "center" },
                { text: "변경자", value: "changeUserId", width: "120px" }
            ],
            detailHeaders: [
                { text: "순번", value: "seq", width: "80px" },
                { text: "대상명", value: "targetNm", width: "200px" },
                { text: "상태", value: "detailType", width: "100px" },
                { text: "비고", value: "remark" }
            ],
            historyList: [],
            selectedHistory: null,
            detailList: []
        };
    },
    mounted() {
        this.search();
    },
    methods: {
        search() {
            this.loading = true;
            this.selectedHistory = null;
            this.detailList = [];
            axios
                .post(this.$APIURL.base + "api/std/getChangeHistoryList", this.filter)
                .then(res => {
                    this.historyList = res.data || [];
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
            this.filter = { targetType: "", changeType: "", fromDt: "", toDt: "" };
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
                })
                .catch(err => {
                    console.error("이력 상세 조회 실패:", err);
                    this.selectedHistory = item;
                });
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
            const map = { WORD: "단어", TERM: "용어", DOMAIN: "도메인", CODE: "코드", CODE_DATA: "코드데이터" };
            return map[type] || type;
        }
    }
};
</script>

<style scoped>
.history-table >>> tbody tr {
    cursor: pointer;
}
.history-table >>> tbody tr:hover {
    background-color: #E8EAF6 !important;
}
.text-pre-wrap {
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 0.85rem;
    max-height: 300px;
    overflow-y: auto;
}
</style>
