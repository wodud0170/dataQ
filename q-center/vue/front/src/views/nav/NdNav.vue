<template>
    <v-navigation-drawer app left clipped :permanent="!isMobile" :mini-variant.sync=this.navSize v-bind="$attrs"
        :class="isMobile ? 'mobileNav' : ''">
        <v-list-item-group v-model="selectedList" active-class="ndColor--text" focusable :mandatory="selectedList !== null">
            <v-list nav dense>
                <!-- 대시보드 -->
                <v-list-item link id="nav_dashboard"
                    v-on:click.stop="resetSplit(); addTabItem('대시보드', 'dashboard'); navAllGroupClose();"
                    active-class="ndColor--text" href="#tab_dashboard" title="대시보드">
                    <v-list-item-icon>
                        <v-icon v-cloak>dashboard</v-icon>
                    </v-list-item-icon>
                    <v-list-item-title>대시보드</v-list-item-title>
                </v-list-item>

                <!-- 데이터 표준 사전 -->
                <v-list-group link v-cloak :value=" navDsGroup " prepend-icon="task" active-class="ndColor--text"
                    id="dsGroup" title="데이터 표준 사전" v-on:click.stop=" addNavGroupData('dsGroup'); ">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple=" false ">데이터 표준 사전</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_term" href="#tab_term" active-class="ndColor--text" title="용어"
                        v-on:click.stop=" addTabItem('용어', 'term'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>용어</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_word" href="#tab_word" active-class="ndColor--text" title="단어"
                        v-on:click.stop=" addTabItem('단어', 'word'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>단어</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_dsCode" href="#tab_dsCode" active-class="ndColor--text" title="코드"
                        v-on:click.stop=" addTabItem('코드', 'dsCode'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>코드</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_domain" href="#tab_domain" active-class="ndColor--text" title="도메인"
                        v-on:click.stop=" addTabItem('도메인', 'domain'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>도메인</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_domainGroup" href="#tab_domainGroup" active-class="ndColor--text"
                        title="도메인 그룹" v-on:click.stop=" addTabItem('도메인 그룹', 'domainGroup'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>도메인 그룹</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_domainClassification" href="#tab_domainClassification"
                        active-class="ndColor--text" title="도메인 분류"
                        v-on:click.stop=" addTabItem('도메인 분류', 'domainClassification'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>도메인 분류</v-list-item-title>
                    </v-list-item>

                    <!-- <v-list-item link id="nav_datamodel" href="#tab_datamodel" active-class="ndColor--text" title="데이터 모델"
                        v-on:click.stop="addTabItem('데이터 모델', 'datamodel');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델</v-list-item-title>
                    </v-list-item> -->

                </v-list-group>

                <!-- 데이터 모델 -->
                <v-list-group link v-cloak :value="navDmGroup" prepend-icon="storage" active-class="ndColor--text"
                    id="dmGroup" title="데이터 모델" v-on:click.stop="addNavGroupData('dmGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">데이터 모델</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_datamodelStatusTable" href="#tab_datamodelStatusTable"
                        active-class="ndColor--text" title="테이블"
                        v-on:click.stop="addTabItem('테이블', 'datamodelStatusTable');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>테이블</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelStatusColumn" href="#tab_datamodelStatusColumn"
                        active-class="ndColor--text" title="컬럼"
                        v-on:click.stop="addTabItem('컬럼', 'datamodelStatusColumn');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>컬럼</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelCollection" href="#tab_datamodelCollection"
                        active-class="ndColor--text" title="데이터 모델 수집"
                        v-on:click.stop="addTabItem('데이터 모델 수집', 'datamodelCollection');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 수집</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelHistory" href="#tab_datamodelHistory"
                        active-class="ndColor--text" title="데이터 모델 수집이력"
                        v-on:click.stop="addTabItem('데이터 모델 수집이력', 'datamodelHistory');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 수집이력</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelStatus" href="#tab_datamodelStatus"
                        active-class="ndColor--text" title="데이터 모델 현황"
                        v-on:click.stop="addTabItem('데이터 모델 현황', 'datamodelStatus');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 현황</v-list-item-title>
                    </v-list-item>

                </v-list-group>

                <!-- 데이터 표준화 진단 -->
                <v-list-group link v-cloak :value="navDiagGroup" prepend-icon="search" active-class="ndColor--text"
                    id="diagGroup" title="데이터 표준화 진단" v-on:click.stop="addNavGroupData('diagGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">데이터 표준화 진단</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_dataDiag" href="#tab_dataDiag"
                        active-class="ndColor--text" title="진단 실행"
                        v-on:click.stop="addTabItem('진단 실행', 'dataDiag');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>진단 실행</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_dataDiagResult" href="#tab_dataDiagResult"
                        active-class="ndColor--text" title="진단 결과"
                        v-on:click.stop="addTabItem('진단 결과', 'dataDiagResult');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>진단 결과</v-list-item-title>
                    </v-list-item>

                </v-list-group>

                <!-- 데이터 구조 진단 -->
                <v-list-group link v-cloak :value="navStructDiagGroup" prepend-icon="mdi-database-search" active-class="ndColor--text"
                    id="structDiagGroup" title="데이터 구조 진단" v-on:click.stop="addNavGroupData('structDiagGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">데이터 구조 진단</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_structDiag" href="#tab_structDiag"
                        active-class="ndColor--text" title="구조 진단"
                        v-on:click.stop="addTabItem('구조 진단', 'structDiag');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>구조 진단</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_schemaCompare" href="#tab_schemaCompare"
                        active-class="ndColor--text" title="스키마 비교"
                        v-on:click.stop="addTabItem('스키마 비교', 'schemaCompare');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>스키마 비교</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 자동 표준화 -->
                <v-list-group link v-cloak :value="navAutoStdGroup" prepend-icon="auto_fix_high" active-class="ndColor--text"
                    id="autoStdGroup" title="자동 표준화" v-on:click.stop="addNavGroupData('autoStdGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">자동 표준화</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_termRecommend" href="#tab_termRecommend"
                        active-class="ndColor--text" title="표준화 추천"
                        v-on:click.stop="addTabItem('표준화 추천', 'termRecommend');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>표준화 추천</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 데이터 품질 -->
                <!-- <v-list-group v-cloak link :value=" navDqGroup " prepend-icon="high_quality" active-class="ndColor--text"
                    id="dqGroup" title="데이터 품질" v-on:click.stop=" addNavGroupData('dqGroup'); ">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple=" false ">데이터 품질</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-group id="navDqSub1" link sub-group :value=" navDqSub1 " active-class="ndColor--text"
                        v-on:click.stop=" addNavSubGroupData('navDqSub1'); ">
                        <template v-slot:activator>
                            <v-list-item-title :ripple=" false " title="검증항목">검증항목</v-list-item-title>
                        </template>

                        <v-list-item link id="nav_dqi" href="#tab_dqi" active-class="ndColor--text" title="품질지표(DQI)"
                            v-on:click.stop=" addTabItem('품질지표(DQI)', 'dqi'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>품질지표(DQI)</v-list-item-title>
                        </v-list-item>

                        <v-list-item link id="nav_ctq" href="#tab_ctq" active-class="ndColor--text" title="핵심관리항목(CTQ)"
                            v-on:click.stop=" addTabItem('핵심관리항목(CTQ)', 'ctq'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>핵심관리항목(CTQ)</v-list-item-title>
                        </v-list-item>

                        <v-list-item link id="nav_dqbr" href="#tab_dqbr" active-class="ndColor--text" title="업무규칙(BR)"
                            v-on:click.stop=" addTabItem('업무규칙(BR)', 'dqbr'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>업무규칙(BR)</v-list-item-title>
                        </v-list-item>
                    </v-list-group>

                    <v-list-group id="navDqSub2" link sub-group :value=" navDqSub2 " active-class="ndColor--text"
                        v-on:click.stop=" addNavSubGroupData('navDqSub2'); ">
                        <template v-slot:activator>
                            <v-list-item-title :ripple=" false " title="품질검증">품질검증</v-list-item-title>
                        </template>

                        <v-list-item link id="nav_target" href="#tab_target" active-class="ndColor--text" title="검증대상"
                            v-on:click.stop=" addTabItem('검증대상', 'target'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>검증대상</v-list-item-title>
                        </v-list-item>

                        <v-list-item link id="nav_qv" href="#tab_qv" active-class="ndColor--text" title="품질검증"
                            v-on:click.stop=" addTabItem('품질검증', 'qv'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>품질검증</v-list-item-title>
                        </v-list-item>
                    </v-list-group>

                    <v-list-group id="navDqSub3" link sub-group :value=" navDqSub3 " active-class="ndColor--text"
                        v-on:click.stop=" addNavSubGroupData('navDqSub3'); ">
                        <template v-slot:activator>
                            <v-list-item-title :ripple=" false " title="품질검증 결과">품질검증 결과</v-list-item-title>
                        </template>

                        <v-list-item link id="nav_rvi" href="#tab_rvi" active-class="ndColor--text" title="검증항목별 결과"
                            v-on:click.stop=" addTabItem('검증항목별 결과', 'rvi'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>검증항목별 결과</v-list-item-title>
                        </v-list-item>

                        <v-list-item link id="nav_dqqvrt" href="#tab_dqqvrt" active-class="ndColor--text" title="테이블별 결과"
                            v-on:click.stop=" addTabItem('테이블별 결과', 'dqqvrt'); ">
                            <v-list-item-icon>
                                <v-icon></v-icon>
                            </v-list-item-icon>
                            <v-list-item-title>테이블별 결과</v-list-item-title>
                        </v-list-item>
                    </v-list-group>
                </v-list-group> -->


                <!-- 진단 -->
                <!-- <v-list-group link v-cloak :value=" navAndGroup " prepend-icon="assessment" active-class="ndColor--text"
                    id="andGroup" title="진단" v-on:click.stop=" addNavGroupData('andGroup'); ">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple=" false ">진단</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_scurrent" href="#tab_scurrent" active-class="ndColor--text" title="데이터 표준화 진단"
                        v-on:click.stop=" addTabItem('데이터 표준화 진단', 'scurrent'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 표준화 진단</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_qcurrent" href="#tab_qcurrent" active-class="ndColor--text" title="데이터 품질 현황"
                        v-on:click.stop=" addTabItem('데이터 품질 현황', 'qcurrent'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 품질 현황</v-list-item-title>
                    </v-list-item>

                </v-list-group> -->

                <!-- 관리 -->
                <v-list-group link v-cloak :value=" navMmGroup " prepend-icon="app_registration"
                    active-class="ndColor--text" id="mmGroup" title="관리" v-on:click.stop=" addNavGroupData('mmGroup'); ">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple=" false ">관리</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_user" href="#tab_user" active-class="ndColor--text" title="사용자"
                        v-on:click.stop=" addTabItem('사용자', 'user'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>사용자</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_roles" href="#tab_roles" active-class="ndColor--text" title="역할 및 권한"
                        :style=" { 'display': 'none' } " v-on:click.stop=" addTabItem('역할 및 권한', 'roles'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>역할 및 권한</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_approval" href="#tab_approval" active-class="ndColor--text" title="승인"
                        v-on:click.stop=" sendApprovalStatus(); addTabItem('승인', 'approval'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>승인</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datasource" href="#tab_datasource" active-class="ndColor--text" title="데이터 소스"
                        v-on:click.stop=" addTabItem('데이터 소스', 'datasource'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 소스</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_system" href="#tab_system" active-class="ndColor--text" title="시스템 정보"
                        v-on:click.stop=" addTabItem('시스템 정보', 'system'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>시스템 정보</v-list-item-title>
                    </v-list-item>
                </v-list-group>
            </v-list>
        </v-list-item-group>
        <v-sheet class="wsLogViewer" v-model=" wsLogShow " :class=" { 'active': this.wsLogShow } ">
            <h4 v-if=" wsLogShow ">알림 로그 <v-icon v-on:click=" wsLogShowClick() ">expand_more</v-icon> </h4>
            <h4 v-else>알림 로그<v-icon v-on:click=" wsLogShowClick() ">expand_less</v-icon> </h4>
            <v-sheet class="logTextWrap" id="logTextWrap">
            </v-sheet>
        </v-sheet>
    </v-navigation-drawer>
</template>

<script>
import { eventBus } from '../../eventBus';
export default {
    name: 'NdNav',
    props: ['isMobile',
        'navSize',
        'activeContent',
        'tabs',
        'navDsGroup',
        'navDqGroup',
        'navMmGroup',
        'navAndGroup',
        'navDqSub1',
        'navDqSub2',
        'navDqSub3',
        'navDsSub1',
        'navDsSub2',
        'navDsSub3',
        'navDmGroup',
        'navDiagGroup',
        'navStructDiagGroup',
        'navAutoStdGroup'],
    data: () => ({
        selectedList: null,
        wsLogShow: false,
    }),
    watch: {

    },
    methods: {
        addTabItem(title, name) {
            // 각 탭의 활성화를 위한 index가 필요함. vuetify에서 지원하는 tab 메뉴는 index로 tab data를 관리함.
            if (this.tabs.length === 0) {

                this.$emit('addTabItem', title, name, 0);
            } else {
                let tab = this.tabs.find(item => item.name === name);

                if (!tab) {
                    let _index = this.tabs.length;
                    this.$emit('addTabItem', title, name, _index);
                } else {
                    this.addActiveContent(name);
                }
            }
        },
        addActiveContent(name) {
            let _tab = this.tabs.find(item => item.name === name);
            this.$emit('addActiveContent', name, _tab.index);
        },
        navAllGroupClose() {
            this.$emit('navAllGroupClose');
        },
        addNavGroupData(target) {
            this.$emit('addNavGroupData', target);
        },
        addNavSubGroupData(target) {
            this.$emit('addNavSubGroupData', target);
        },
        resetSplit() {
            if (this.navSize) {
                this.$parent.$parent.$parent.resetSplit();
            }
        },
        wsLogShowClick() {
            this.wsLogShow = !this.wsLogShow;
        },
        sendApprovalStatus() {
            // 승인 메뉴 선택 시 대시보드에서 보내주는 상태값과 다르게 보내줘야 함.
            // 대시보드에서 직접 승인 메뉴 접근하지 않고 네비게이션으로 접근할 경우 초기화 필요함
            this.$emit('sendApprovalStatus');
        },
    },
    mounted() {
        /**
         * DSDataDiag '결과 보기' 버튼 클릭 시 진단 결과 탭을 열기 위한 이벤트 수신
         * - eventBus 'openDiagResult' 이벤트를 받으면 진단 결과 탭을 추가/활성화
         * - diagJobId는 eventBus.pendingDiagJobId에 저장되어 DSDataDiagResult에서 참조
         */
        eventBus.$on('openDiagResult', () => {
            this.addTabItem('진단 결과', 'dataDiagResult');
        });
    },
    beforeDestroy() {
        eventBus.$off('openDiagResult');
    }
}
</script>

<style scoped>
.mobileNav {
    position: absolute;
    width: 70% !important;
    height: calc(100vh - 56px) !important;
    z-index: 4;
}

.wsLogViewer {
    position: absolute;
    bottom: calc((-100vh + 64px) / 3);
    width: 100%;
    height: calc((100vh - 64px) / 3);
    background-color: #F5F7FA;
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.wsLogViewer.active {
    bottom: 0px;
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.wsLogViewer h4 {
    position: absolute;
    top: -40px;
    width: 100%;
    height: 40px;
    line-height: 40px;
    padding: 0 14px;
    background-color: #E8EAF6;
    border-top-right-radius: 16px;
    color: #455A64;
    display: flex;
    justify-content: space-between;
    font-size: 0.85rem;
    font-weight: 600;
}

.wsLogViewer button:focus {
    background-color: transparent !important;
}

.logTextWrap {
    position: relative;
    overflow-x: hidden;
    overflow-y: auto;
    width: 100%;
    height: calc((100vh - 64px) / 3);
    padding-left: 14px;
    padding-top: 8px;
    background-color: #F5F7FA;
    word-wrap: break-word;
    font-size: .8125rem;
    font-weight: 500;
    color: #455A64;
}

::-webkit-scrollbar-track {
    background: transparent;
}
</style>
