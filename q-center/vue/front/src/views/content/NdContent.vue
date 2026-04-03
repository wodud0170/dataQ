<template>
    <v-sheet id="tabList" v-bind:style="[isMobile ? { 'height': 'calc(100vh - 56px)', 'top': '0px' } : {}]">
        <v-sheet class="tabsWrapper" v-show="!isMobile">
            <v-tabs :value="this.tab" class="tabsStyle" background-color="transparent">
                <draggable v-model="addTabs" :list="addTabs" @start="(event) => onStart(event)"
                    @end="(event) => onEnd(event)" class="draggableWrap">
                    <v-tab v-for="item in tabs" :tabindex="item.index" :key="item.index" class="modern-tab"
                        active-class="modern-tab-active"
                        v-on:click.stop="addActiveContent(item.name, item.index); navAndTabSync(item.name)">
                        {{ item.title }}
                        <v-icon small class="tabCloseIcon" title="삭제"
                            v-on:click.stop="removeTabItem(item.index, item.title, item.name)">close</v-icon>
                    </v-tab>
                </draggable>
            </v-tabs>
            <!-- <v-icon v-show="this.tabs.length !== 0" class="tabListViewIcon">arrow_drop_down</v-icon> -->
            <!-- 탭 리스트 아이콘과 리스트 목록 : 탭이 있는 경우 보여주고 없으면 사라짐 -->
            <v-menu bottom offset-y :max-width="isMobile ? '100px' : '200px'" :min-width="'200px'" :value="menuShow">
                <template v-slot:activator="{ on }">
                    <v-avatar v-show="tabListShow" v-on="on" class="tabListViewIcon" v-on:click="tabListClick()">
                        <v-icon>arrow_drop_down</v-icon>
                    </v-avatar>
                </template>
                <!-- 탭 리스트 화살표 모양 버튼을 클릭했을 때 노출되는 card -->
                <v-card>
                    <v-list-item-content class="justify-center">
                        <v-col class="text-left tabListBtnWrapper">
                            <v-btn class="tabListBtn" v-for="item in tabs" depressed text :key="item.name"
                                v-on:click.stop="addActiveContent(item.name, item.index); tabListClick(); navAndTabSync(item.name) ">
                                <v-icon small color="ndColor"
                                    :style="{ marginRight: '10px', visibility: item.name === activeContent ? 'visible' : 'hidden' }">done</v-icon>
                                {{ item.title }}
                            </v-btn>
                        </v-col>
                    </v-list-item-content>
                </v-card>
            </v-menu>
        </v-sheet>
        <div class="contents_wrap" :class="isMobile ? 'mobileContent' : ''" v-show="!drawer">
            <div v-if="this.activeContent === 'dashboard'" id="tab_dashboard" class="tab_contents"
                :class="{ active: this.activeContent === 'dashboard' }">
                <keep-alive>
                    <dashboard :key="dashboardKey" :isMobile="isMobile" :addTabs="addTabs" @addTabItem="addTabItem"
                        @addActiveContent="addActiveContent" @navAndTabSync="navAndTabSync" @sendApprovalStatus="sendApprovalStatus"/>
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'term'" id="tab_term" class="tab_contents"
                :class="{ active: this.activeContent === 'term' }">
                <keep-alive>
                    <term :key="termKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dsCode'" id="tab_dsCode" class="tab_contents"
                :class="{ active: this.activeContent === 'dsCode' }">
                <keep-alive>
                    <dsCode :key="codeKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'word'" id="tab_word" class="tab_contents"
                :class="{ active: this.activeContent === 'word' }">
                <keep-alive>
                    <word :key="wordKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'domain'" id="tab_domain" class="tab_contents"
                :class="{ active: this.activeContent === 'domain' }">
                <keep-alive>
                    <domain :key="domainKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'domainGroup'" id="tab_domainGroup" class="tab_contents"
                :class="{ active: this.activeContent === 'domainGroup' }">
                <keep-alive>
                    <domainGroup :key="domainGroupKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'domainClassification'" id="tab_domainClassification" class="tab_contents"
                :class="{ active: this.activeContent === 'domainClassification' }">
                <keep-alive>
                    <domainClassification :key="domainClassificationKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datamodelStatus'" id="tab_datamodelStatus" class="tab_contents"
                :class="{ active: this.activeContent === 'datamodelStatus' }">
                <keep-alive>
                    <datamodelStatus :key="datamodelStatusKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datamodelStatusTable'" id="tab_datamodelStatusTable" class="tab_contents"
                :class="{ active: this.activeContent === 'datamodelStatusTable' }">
                <keep-alive>
                    <datamodelStatusTable :key="datamodelStatusTableKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datamodelStatusColumn'" id="tab_datamodelStatusColumn" class="tab_contents"
                :class="{ active: this.activeContent === 'datamodelStatusColumn' }">
                <keep-alive>
                    <datamodelStatusColumn :key="datamodelStatusColumnKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datamodelCollection'" id="tab_datamodelCollection" class="tab_contents"
                :class="{ active: this.activeContent === 'datamodelCollection' }">
                <keep-alive>
                    <datamodelCollection :key="datamodelCollectionKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datamodelHistory'" id="tab_datamodelHistory" class="tab_contents"
                :class="{ active: this.activeContent === 'datamodelHistory' }">
                <keep-alive>
                    <datamodelHistory :key="datamodelHistoryKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'erwinImport'" id="tab_erwinImport" class="tab_contents"
                :class="{ active: this.activeContent === 'erwinImport' }">
                <keep-alive>
                    <erwinImport :key="erwinImportKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dataDiag'" id="tab_dataDiag" class="tab_contents"
                :class="{ active: this.activeContent === 'dataDiag' }">
                <keep-alive>
                    <dataDiag :key="dataDiagKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dataDiagResult'" id="tab_dataDiagResult" class="tab_contents"
                :class="{ active: this.activeContent === 'dataDiagResult' }">
                <keep-alive>
                    <dataDiagResult :key="dataDiagResultKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'schemaCompare'" id="tab_schemaCompare" class="tab_contents"
                :class="{ active: this.activeContent === 'schemaCompare' }">
                <keep-alive>
                    <schemaCompare :key="schemaCompareKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'structDiag'" id="tab_structDiag" class="tab_contents"
                :class="{ active: this.activeContent === 'structDiag' }">
                <keep-alive>
                    <structDiag :key="structDiagKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'termRecommend'" id="tab_termRecommend" class="tab_contents"
                :class="{ active: this.activeContent === 'termRecommend' }">
                <keep-alive>
                    <termRecommend :key="termRecommendKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dqi'" id="tab_dqi" class="tab_contents"
                :class="{ active: this.activeContent === 'dqi' }">
                <keep-alive>
                    <dqi :key="dqiKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'ctq'" id="tab_ctq" class="tab_contents"
                :class="{ active: this.activeContent === 'ctq' }">
                <keep-alive>
                    <ctq :key="ctqKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dqbr'" id="tab_dqbr" class="tab_contents"
                :class="{ active: this.activeContent === 'dqbr' }">
                <keep-alive>
                    <dqbr :key="dqbrKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'target'" id="tab_target" class="tab_contents"
                :class="{ active: this.activeContent === 'target' }">
                <keep-alive>
                    <target :key="targetKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'qv'" id="tab_qv" class="tab_contents"
                :class="{ active: this.activeContent === 'qv' }">
                <keep-alive>
                    <qv :key="qvKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'rvi'" id="tab_rvi" class="tab_contents"
                :class="{ active: this.activeContent === 'rvi' }">
                <keep-alive>
                    <rvi :key="rviKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'dqqvrt'" id="tab_dqqvrt" class="tab_contents"
                :class="{ active: this.activeContent === 'dqqvrt' }">
                <keep-alive>
                    <dqqvrt :key="dqqvrtKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'user'" id="tab_user" class="tab_contents"
                :class="{ active: this.activeContent === 'user' }">
                <keep-alive>
                    <user :key="userKey" :isMobile="isMobile"  />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'roles'" id="tab_roles" class="tab_contents"
                :class="{ active: this.activeContent === 'roles' }">
                <keep-alive>
                    <roles :key="rolesKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'approval'" id="tab_approval" class="tab_contents"
                :class="{ active: this.activeContent === 'approval' }">
                <keep-alive>
                    <approval :key="approvalKey" :isMobile="isMobile" :approvalStatus="approvalStatus"/>
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'datasource'" id="tab_datasource" class="tab_contents"
                :class="{ active: this.activeContent === 'datasource' }">
                <keep-alive>
                    <datasource :key="datasourceKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'system'" id="tab_system" class="tab_contents"
                :class="{ active: this.activeContent === 'system' }">
                <keep-alive>
                    <system :key="systemKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'scurrent'" id="tab_scurrent" class="tab_contents"
                :class="{ active: this.activeContent === 'scurrent' }">
                <keep-alive>
                    <scurrent :key="scurrentKey" :isMobile="isMobile"  />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'qcurrent'" id="tab_qcurrent" class="tab_contents"
                :class="{ active: this.activeContent === 'qcurrent' }">
                <keep-alive>
                    <qcurrent :key="qcurrentKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'changeHistory'" id="tab_changeHistory" class="tab_contents"
                :class="{ active: this.activeContent === 'changeHistory' }">
                <keep-alive>
                    <changeHistory :key="changeHistoryKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'board'" id="tab_board" class="tab_contents"
                :class="{ active: this.activeContent === 'board' }">
                <keep-alive>
                    <board :key="boardKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'myRequest'" id="tab_myRequest" class="tab_contents"
                :class="{ active: this.activeContent === 'myRequest' }">
                <keep-alive>
                    <myRequest :key="myRequestKey" :isMobile="isMobile" />
                </keep-alive>
            </div>
            <div v-if="this.activeContent === 'globalSearch'" id="tab_globalSearch" class="tab_contents"
                :class="{ active: this.activeContent === 'globalSearch' }">
                <keep-alive>
                    <globalSearch :key="globalSearchKey" :isMobile="isMobile" @addTabItem="addTabItem" />
                </keep-alive>
            </div>
        </div>
        <v-snackbar v-model="removeSnackbar" :timeout="removeSnackbarTimeout">
            {{ removeSnackbarText }}

            <template v-slot:action="{ attrs }">
                <v-btn color="white" text v-bind="attrs" v-on:click="removeSnackbar = false">
                    닫기
                </v-btn>
            </template>
        </v-snackbar>
    </v-sheet>
</template>

<script>
import QDashboard from "./../../components/QDashboard.vue"
import DSTerm from "./../../components/DSTerm.vue"
import DSCode from "./../../components/DSCode.vue"
import DSWord from "./../../components/DSWord.vue"
import DSDomain from "./../../components/DSDomain.vue"
import DSDomainGroup from "./../../components/DSDomainGroup.vue"
import DSDomainClassification from "./../../components/DSDomainClassification.vue"
import DSDatamodelStatus from "../../components/DSDatamodelStatus.vue"
import DSDatamodelStatusTable from "../../components/DSDatamodelStatusTable.vue"
import DSDatamodelStatusColumn from "../../components/DSDatamodelStatusColumn.vue"
import DSDatamodelCollection from "../../components/DSDatamodelCollection.vue"
import DSDatamodelHistory from "../../components/DSDatamodelHistory.vue"
import DSDataDiag from "../../components/DSDataDiag.vue"
import DSDataDiagResult from "../../components/DSDataDiagResult.vue"
import DSTermRecommend from "../../components/DSTermRecommend.vue"
import DSSchemaCompare from "../../components/DSSchemaCompare.vue"
import DSStructDiag from "../../components/DSStructDiag.vue"
import DSErwinImport from "../../components/DSErwinImport.vue"
import DSChangeHistory from "../../components/DSChangeHistory.vue"
import DSGlobalSearch from "../../components/DSGlobalSearch.vue"
import DSMyRequest from "../../components/DSMyRequest.vue"
import DSBoard from "../../components/DSBoard.vue"
import DQDQI from "./../../components/DQDQI.vue"
import DQCTQ from "./../../components/DQCTQ.vue"
import DQBR from "./../../components/DQBR.vue"
import DQQVTarget from "./../../components/DQQVTarget.vue"
import DQQVQV from "./../../components/DQQVQV.vue"
import DQQVRVI from "./../../components/DQQVRVI.vue"
import DQQVRT from "./../../components/DQQVRT.vue"
import MMUser from "./../../components/MMUser.vue"
import MMRoles from "./../../components/MMRoles.vue"
import MMApproval from "./../../components/MMApproval.vue"
import MMDatasource from "./../../components/MMDatasource.vue"
import MMSystem from "./../../components/MMSystem.vue"
import ANDscurrent from "./../../components/ANDscurrent.vue"
import ANDqcurrent from "./../../components/ANDqcurrent.vue"
import draggable from "vuedraggable"

export default {
    name: 'NdContent',
    props: ['isMobile', 'drawer', 'activeContent', 'tab', 'tabs', 'navApprovalStatus'],
    watch: {
        tab() {
            if (this.tab !== null) {
                this.tabListShow = true;
            } else {
                this.tabListShow = false;
            }
        },
        tabs() {
            this.addTabs = this.tabs;
        },
        navApprovalStatus() {
            this.approvalStatus = this.navApprovalStatus;
        }
    },
    created() {
        // 맨 처음 생성할 때 대시보드 탭을 활성화 시킨다.
        this.addTabItem('대시보드', 'dashboard', 0);
    },
    data() {
        return {
            addTabs: null,
            tabListShow: false,
            menuShow: false,
            removeSnackbar: false,
            removeSnackbarTimeout: 2000,
            removeSnackbarText: null,
            activeNames: null,
            approvalStatus: [],// 대시보드에서 승인 페이지 호출 시 승인 상태를 저장하는 변수
            dashboardKey: this.createUUID(),
            termKey: this.createUUID(),
            codeKey: this.createUUID(),
            wordKey: this.createUUID(),
            domainKey: this.createUUID(),
            domainGroupKey: this.createUUID(),
            domainClassificationKey: this.createUUID(),
            datamodelStatusKey: this.createUUID(),
            datamodelStatusTableKey: this.createUUID(),
            datamodelStatusColumnKey: this.createUUID(),
            datamodelCollectionKey: this.createUUID(),
            datamodelHistoryKey: this.createUUID(),
            erwinImportKey: this.createUUID(),
            dataDiagKey: this.createUUID(),
            dataDiagResultKey: this.createUUID(),
            termRecommendKey: this.createUUID(),
            schemaCompareKey: this.createUUID(),
            structDiagKey: this.createUUID(),
            dqiKey: this.createUUID(),
            ctqKey: this.createUUID(),
            dqbrKey: this.createUUID(),
            targetKey: this.createUUID(),
            qvKey: this.createUUID(),
            rviKey: this.createUUID(),
            dqqvrtKey: this.createUUID(),
            userKey: this.createUUID(),
            rolesKey: this.createUUID(),
            approvalKey: this.createUUID(),
            datasourceKey: this.createUUID(),
            systemKey: this.createUUID(),
            scurrentKey: this.createUUID(),
            qcurrentKey: this.createUUID(),
            changeHistoryKey: this.createUUID(),
            boardKey: this.createUUID(),
            myRequestKey: this.createUUID(),
            globalSearchKey: this.createUUID(),
        }
    },
    components: {
        "draggable": draggable,
        "dashboard": QDashboard,
        "term": DSTerm,
        "dsCode": DSCode,
        "word": DSWord,
        "domain": DSDomain,
        "domainGroup": DSDomainGroup,
        "domainClassification": DSDomainClassification,
        "datamodelStatus": DSDatamodelStatus,
        "datamodelStatusTable": DSDatamodelStatusTable,
        "datamodelStatusColumn": DSDatamodelStatusColumn,
        "datamodelCollection": DSDatamodelCollection,
        "datamodelHistory": DSDatamodelHistory,
        "dataDiag": DSDataDiag,
        "dataDiagResult": DSDataDiagResult,
        "termRecommend": DSTermRecommend,
        "schemaCompare": DSSchemaCompare,
        "structDiag": DSStructDiag,
        "erwinImport": DSErwinImport,
        "dqi": DQDQI,
        "ctq": DQCTQ,
        "dqbr": DQBR,
        "target": DQQVTarget,
        "qv": DQQVQV,
        "rvi": DQQVRVI,
        "dqqvrt": DQQVRT,
        "user": MMUser,
        "roles": MMRoles,
        "approval": MMApproval,
        "datasource": MMDatasource,
        "system": MMSystem,
        "scurrent": ANDscurrent,
        "qcurrent": ANDqcurrent,
        "changeHistory": DSChangeHistory,
        "board": DSBoard,
        "myRequest": DSMyRequest,
        "globalSearch": DSGlobalSearch
    },
    methods: {
        addActiveContent(itemName, itemIdenx) {
            this.$emit('addActiveContent', itemName, itemIdenx);
        },
        removeTabItem(itemIdenx, itemTitle, itemName) {
            this.showRemoveSnackbar(itemTitle);
            this.$emit('removeTabItem', itemIdenx, itemName);

            // 탭 삭제 시 key값 변경하여 add 시 새 화면 보이도록 하기
            switch (itemName) {
                case "dashboard":
                    this.dashboardKey = this.createUUID();
                    break;
                case "term":
                    this.termKey = this.createUUID();
                    break;
                case "dsCode":
                    this.codeKey = this.createUUID();
                    break;
                case "word":
                    this.wordKey = this.createUUID();
                    break;
                case "domain":
                    this.domainKey = this.createUUID();
                    break;
                case "domainGroup":
                    this.domainGroupKey = this.createUUID();
                    break;
                case "domainClassification":
                    this.domainClassificationKey = this.createUUID();
                    break;
                case "datamodelStatus":
                    this.datamodelStatusKey = this.createUUID();
                    break;
                case "datamodelStatusTable":
                    this.datamodelStatusTableKey = this.createUUID();
                    break;
                case "datamodelStatusColumn":
                    this.datamodelStatusColumnKey = this.createUUID();
                    break;
                case "datamodelCollection":
                    this.datamodelCollectionKey = this.createUUID();
                    break;
                case "datamodelHistory":
                    this.datamodelHistoryKey = this.createUUID();
                    break;
                case "erwinImport":
                    this.erwinImportKey = this.createUUID();
                    break;
                case "dataDiag":
                    this.dataDiagKey = this.createUUID();
                    break;
                case "dataDiagResult":
                    this.dataDiagResultKey = this.createUUID();
                    break;
                case "termRecommend":
                    this.termRecommendKey = this.createUUID();
                    break;
                case "schemaCompare":
                    this.schemaCompareKey = this.createUUID();
                    break;
                case "structDiag":
                    this.structDiagKey = this.createUUID();
                    break;
                case "dqi":
                    this.dqiKey = this.createUUID();
                    break;
                case "ctq":
                    this.ctqKey = this.createUUID();
                    break;
                case "dqbr":
                    this.dqbrKey = this.createUUID();
                    break;
                case "target":
                    this.targetKey = this.createUUID();
                    break;
                case "qv":
                    this.qvKey = this.createUUID();
                    break;
                case "rvi":
                    this.rviKey = this.createUUID();
                    break;
                case "dqqvrt":
                    this.dqqvrtKey = this.createUUID();
                    break;
                case "user":
                    this.userKey = this.createUUID();
                    break;
                case "roles":
                    this.rolesKey = this.createUUID();
                    break;
                case "approval":
                    this.approvalKey = this.createUUID();
                    break;
                case "datasource":
                    this.datasourceKey = this.createUUID();
                    break;
                case "system":
                    this.systemKey = this.createUUID();
                    break;
                case "scurrent":
                    this.scurrentKey = this.createUUID();
                    break;
                case "qcurrent":
                    this.qcurrentKey = this.createUUID();
                    break;
                case "changeHistory":
                    this.changeHistoryKey = this.createUUID();
                    break;
                case "board":
                    this.boardKey = this.createUUID();
                    break;
                case "myRequest":
                    this.myRequestKey = this.createUUID();
                    break;
                case "globalSearch":
                    this.globalSearchKey = this.createUUID();
                    break;
            }

        },
        navAndTabSync(itemName) {
            this.$emit('navAndTabSync', itemName)
        },
        tabListClick() {
            this.menuShow = !this.menuShow;
        },
        showRemoveSnackbar(itemTitle) {
            this.removeSnackbarText = itemTitle + " 탭을 삭제했습니다.";
            this.removeSnackbar = true;
        },
        onStart(event) {
            this.$emit('changeTabStatu');
        },
        onEnd(event) {
            this.$emit('changeTabItem', this.addTabs);
        },
        createUUID() {
            return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
                var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
                return v.toString(16);
            });
        },
        addTabItem(title, name, index) {
            this.$emit('addTabItem', title, name, index);
        },
        sendApprovalStatus(status) {
            this.approvalStatus = [status];
        }
    }
}


</script>

<style scoped>
.contents_wrap {
    position: absolute;
    width: 100%;
    height: calc(100vh - 64px - 48px);
    top: 48px;
    background-color: #F5F7FA;
}

.contents_wrap.mobileContent {
    height: calc(100vh - 56px) !important;
    top: 0px !important;
}

.contents_wrap > div {
    position: relative;
    width: 100%;
    height: calc(100vh - 64px - 48px);
}

.tab_contents {
    display: none;
}

.tab_contents.active {
    display: inline-block;
}

.tabsWrapper {
    width: 100%;
    height: 48px;
    background-color: #E8EAF6 !important;
    display: flex;
    justify-content: space-between;
    border-bottom: 1px solid #C5CAE9;
}

.modern-tab {
    color: #546E7A !important;
    background-color: transparent !important;
    border-right: 1px solid #C5CAE9 !important;
    text-transform: none;
    font-weight: 500;
    font-size: 0.85rem;
    letter-spacing: 0.02em;
    transition: all 0.2s ease;
    min-width: 120px;
}

.modern-tab:hover {
    background-color: #C5CAE9 !important;
}

.modern-tab-active {
    color: #1A237E !important;
    background-color: #ffffff !important;
    font-weight: 600;
    border-bottom: 2px solid #3F51B5 !important;
}

.tabCloseIcon {
    margin: 10px 0px 10px 12px;
    color: #90A4AE !important;
    transition: color 0.15s ease;
}

.tabCloseIcon:hover {
    color: #EF5350 !important;
}

.tabListViewIcon {
    position: relative;
    margin-left: auto;
    max-width: 3% !important;
    width: 3% !important;
    min-width: 3% !important;
}

.tabListBtnWrapper {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 0px;
}

.tabListBtnWrapper > button {
    width: 100%;
    display: flex;
    justify-content: flex-start;
}

.tabsStyle {
    position: relative;
    min-width: 97% !important;
    max-width: 97% !important;
    width: 97% !important;
}

.tabListBtn {
    height: 30px !important;
}
</style>
