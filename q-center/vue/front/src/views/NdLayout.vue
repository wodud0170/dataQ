<template>
    <div>
        <router-view name="header" :drawer='drawer' :isMobile='isMobile' :tabs="tabs" @navIconClick="navIconClick"
            @addTabItem="addTabItem" @addActiveContent="addActiveContent" @navAndTabSync="navAndTabSync" />
        <!-- PC, tablet -->
        <div v-if="!isMobile">
            <Split ref="mySplit" style="height: 100vh" @onDrag="onDrag" @onDragEnd="onDragEnd">
                <SplitArea :size="15" :minSize="60">
                    <router-view name="aside" id="splistNav" v-model="drawer" :isMobile='isMobile' :navSize="mini"
                        :tab="tab" :activeContent="activeContent" :tabs="tabs" :navDsGroup="navDsGroup"
                        :navDqGroup="navDqGroup" :navMmGroup="navMmGroup" :navAndGroup="navAndGroup" :navDqSub1="navDqSub1"
                        :navDqSub2="navDqSub2" :navDqSub3="navDqSub3" :navDsSub1="navDsSub1" :navDsSub2="navDsSub2" :navDsSub3="navDsSub3"
                        :navDmGroup="navDmGroup" :navDsStatusGroup="navDsStatusGroup" :navDiagGroup="navDiagGroup"
                        :navStructDiagGroup="navStructDiagGroup" :navAutoStdGroup="navAutoStdGroup"
                        :navMyPageGroup="navMyPageGroup"
                        :navCommunityGroup="navCommunityGroup" :navScheduleGroup="navScheduleGroup" :navQualGroup="navQualGroup"
                        @addTabItem="addTabItem" @addActiveContent="addActiveContent" @navAllGroupClose="navAllGroupClose"
                        @navSubGropClose="navSubGropClose" @addNavGroupData="addNavGroupData"
                        @addNavSubGroupData="addNavSubGroupData" @resetSplit="resetSplit"
                        @sendApprovalStatus="sendApprovalStatus" />
                </SplitArea>
                <SplitArea :size="85" id="splistMain">
                    <router-view name="content" :isMobile="isMobile" :tab="tab" :activeContent="activeContent" :tabs="tabs"
                        :navApprovalStatus="navApprovalStatus" @addActiveContent="addActiveContent"
                        @removeTabItem="removeTabItem" @navAndTabSync="navAndTabSync" @changeTabStatu="changeTabStatu"
                        @addTabItem="addTabItem" @changeTabItem="changeTabItem" />
                </SplitArea>
            </Split>
        </div>
        <!-- mobile -->
        <div v-else :style="{ width: '100vw', height: 'calc(100vh - 56px)', top: '56px', position: 'relative' }">
            <router-view name="aside" v-model="drawer" :isMobile='isMobile' :tab="tab" :activeContent="activeContent"
                :tabs="tabs" :navDsGroup="navDsGroup" :navDqGroup="navDqGroup" :navMmGroup="navMmGroup"
                :navAndGroup="navAndGroup" :navDqSub1="navDqSub1" :navDqSub2="navDqSub2" :navDqSub3="navDqSub3"
                :navDsSub1="navDsSub1" :navDsSub2="navDsSub2" :navDsSub3="navDsSub3"
                :navDmGroup="navDmGroup" :navDsStatusGroup="navDsStatusGroup" :navDiagGroup="navDiagGroup"
                :navStructDiagGroup="navStructDiagGroup" :navAutoStdGroup="navAutoStdGroup"
                        :navMyPageGroup="navMyPageGroup"
                        :navCommunityGroup="navCommunityGroup" :navScheduleGroup="navScheduleGroup" :navQualGroup="navQualGroup" @addTabItem="addTabItem" @addActiveContent="addActiveContent"
                @navAllGroupClose="navAllGroupClose" @navSubGropClose="navSubGropClose" @addNavGroupData="addNavGroupData"
                @addNavSubGroupData="addNavSubGroupData" />
            <router-view name="content" :isMobile="isMobile" :tab="tab" :drawer="drawer" :activeContent="activeContent"
                :tabs="tabs" @addActiveContent="addActiveContent" @removeTabItem="removeTabItem"
                @navAndTabSync="navAndTabSync" @changeTabStatu="changeTabStatu" @changeTabItem="changeTabItem" />
        </div>
    </div>
</template>

<script>

export default {
    name: 'NdLayout',
    data: () => ({
        isMobile: false,
        drawer: null,
        mini: false,
        activeContent: '',
        tabStatu: null, // 'add', 'remove', 'change'
        tab: null,
        tabs: [],
        removeSelectedContent: null,
        navDsGroup: null,
        navDqGroup: null,
        navMmGroup: null,
        navAndGroup: null,
        navDmGroup: null,
        navDqSub1: null,
        navDqSub2: null,
        navDqSub3: null,
        navDsSub1: null,
        navDsSub2: null,
        navDsSub3: null,
        navDsStatusGroup: null,
        navDiagGroup: null,
        navStructDiagGroup: null,
        navAutoStdGroup: null,
        navCommunityGroup: null,
        navScheduleGroup: null,
        navQualGroup: null,
        navMyPageGroup: null,
        navApprovalStatus: []// 대시보드에서 승인 페이지 호출 시 승인 상태를 저장하는 변수
    }),
    beforeDestroy() {
        if (typeof window === 'undefined') return

        window.removeEventListener('resize', this.onResize, { passive: true })
        window.removeEventListener('beforeunload', this.onBeforeUnload)
    },
    watch: {
        $route() {
            this.drawer = false;
        },
        tabs() {
            this.matchContent();
        },
        approvalStatus() {
            this.navApprovalStatus = [];
        }
    },
    mounted() {
        this.onResize()

        window.addEventListener('resize', this.onResize, { passive: true })

        // F5/새로고침/탭 닫기 시 경고
        window.addEventListener('beforeunload', this.onBeforeUnload)

        // 86번 — 다운로드/API anchor 클릭 시 beforeunload 경고 자동 skip
        // (download 속성 또는 /api/ href 는 페이지 이동 의도 X)
        document.addEventListener('click', (e) => {
            const a = e.target.closest && e.target.closest('a[href]');
            if (a && (a.hasAttribute('download') || /\/api\//.test(a.href))) {
                window._skipBeforeUnload = true;
            }
        }, true)

        if (this.isMobile) {
            this.drawer = false;
        }
    },
    methods: {
        onResize() {
            // 모바일의 기준은 디바이스의 가로 길이 916px로 함
            this.isMobile = window.innerWidth < 916
        },
        onBeforeUnload(e) {
            if (window._skipBeforeUnload) {
                window._skipBeforeUnload = false;
                return;
            }
            // 메인 화면에서만 경고 (로그인 화면 제외)
            if (!window.location.pathname.startsWith('/app/main')) return;
            e.preventDefault();
            e.returnValue = '';
        },
        navIconClick(drawer) {
            this.drawer = drawer;
            this.mini = false;
        },
        onDrag(size) {
            if (size[0] < 5) {
                this.mini = true;
                this.miniSizeActiveStyle();
                return;
            } else if (size[0] > 5 && size[0] < 15) {
                this.mini = false;
                return;
            } else if (size[0] > 15) {
                // nav를 크게 볼 수 없도록 값을 고정함. (작게 보기는 가능)
                this.resetSplit();
                return;
            }
        },
        onDragEnd() {
            if (this.mini === false) {
                this.navAndTabSync(this.activeContent);
                return;
            }
        },
        resetSplit() {
            this.mini = false;
            this.$refs.mySplit.instance.setSizes([15, 85]);
        },

        miniSizeActiveStyle() {
            this.navAllGroupClose();

            switch (this.activeContent) {
                case "dashboard": {
                    const dashEl = document.getElementById("nav_dashboard");
                    if (dashEl) dashEl.classList.add("v-list-item--active", "ndColor--text");
                    break;
                }
                case "term":
                case "dsCode":
                case "word":
                case "domain":
                case "domainGroup":
                case "domainClassification":
                case "changeHistory":
                case "datamodelStatus":
                case "datamodelCollection":
                case "datamodelHistory":
                case "datamodelStatusTable":
                case "datamodelStatusColumn":
                case "erwinImport":
                    this._activateGroup("dsGroup");
                    break;
                case "boardNotice":
                case "boardQna":
                    this._activateGroup("communityGroup");
                    break;
                case "myRequest":
                case "myProfile":
                    this._activateGroup("myPageGroup");
                    break;
                case "dataDiag":
                case "dataDiagResult":
                    this._activateGroup("diagGroup");
                    break;
                case "structDiag":
                case "structDiagResult":
                    this._activateGroup("structDiagGroup");
                    break;
                case "dqi":
                case "ctq":
                case "dqbr":
                case "target":
                case "qv":
                case "rvi":
                case "dqqvrt":
                    this._activateGroup("dqGroup");
                    break;
                case "user":
                case "roles":
                case "approval":
                case "system":
                case "datasource":
                    this._activateGroup("mmGroup");
                    break;
                case "scurrent":
                case "qcurrent":
                    this._activateGroup("andGroup");
                    break;
                default:

                    break;
            }
        },
        matchContent() {
            // console.log(this.tabs)
            // console.log(this.tabStatu)

            // 생성된 탭의 정보를 이용하여 content의 정보를 지정한다.
            let tabsLength = this.tabs.length;

            if (tabsLength !== 0) {
                let selectedItem = this.tabs[tabsLength - 1];

                // add, remove, change별 content 지정 방법이 상이함.
                switch (this.tabStatu) {
                    case 'add':
                        // console.log('add');
                        // 새로운 탭 생성 시 인덱스는 가장 뒤에 것으로.
                        this.addActiveContent(selectedItem.name, selectedItem.index);
                        break;
                    case 'remove':
                        // console.log('remove');
                        // 탭 삭제 시 삭제하려는 콘텐트와 현재 보여지는 콘텐트가 같다면 새로운 탭처럼 맨 뒤로 이동한다.
                        if (this.removeSelectedContent === this.activeContent) {
                            this.addActiveContent(selectedItem.name, selectedItem.index);
                            this.navAndTabSync(selectedItem.name);
                        } else {
                            for (let i = 0; i < tabsLength - 1; i++) {
                                if (this.tabs[i].name === this.activeContent) {
                                    this.addActiveContent(this.tabs[i].name, this.tabs[i].index);
                                    this.navAndTabSync(this.tabs[i].name);
                                }
                            }
                        }


                        break;
                    case 'change':
                        // console.log('change');

                        for (let i = 0; i < tabsLength; i++) {
                            if (this.tabs[i].name === this.activeContent) {
                                this.addActiveContent(this.tabs[i].name, this.tabs[i].index);

                                this.navAndTabSync(this.tabs[i].name);
                            }
                        }
                        break;
                }

            } else {
                // 생성된 탭이 없을 때의 화면을 지정
                this.addActiveContent('', null)
            }
        },
        addActiveContent(itemName, itemIndex) {
            if (this.mini === true) {
                this.navSubGropClose();
            }

            this.activeContent = itemName;
            this.tab = itemIndex;

            this.changeNavItem(itemName);
        },
        addTabItem(title, name, index) {
            if (this.mini === true) {
                this.resetSplit();
                return;
            }

            this.tabStatu = 'add';
            let tabItem = {
                title: title,
                name: name,
                index: index
            };

            this.tabs.push(tabItem);
        },
        removeTabItem(itemIdenx, itemName) {
            this.tabStatu = 'remove';

            let items = this.tabs.filter(function (item) { return item.index != itemIdenx; });

            let resetItems = [];
            // 삭제 시 어떤 것부터 삭제될지 알 수 없으므로 index를 다시 지정해주어야 함.
            for (let i = 0; i < items.length; i++) {
                let resetObj = {
                    title: items[i].title,
                    name: items[i].name,
                    index: i
                };

                resetItems.push(resetObj);
            }

            this.removeSelectedContent = itemName;
            this.tabs = resetItems;
        },
        changeTabItem(items) {
            let oldTabs = items;
            let oldTabsLength = oldTabs.length;
            let resetTabItems = [];

            for (let i = 0; i < oldTabsLength; i++) {
                let resetObj = {
                    title: oldTabs[i].title,
                    name: oldTabs[i].name,
                    index: i
                };

                resetTabItems.push(resetObj);
            }
            this.tabs = resetTabItems;
        },
        // 그룹 헤더 활성화 — 대상 그룹이 없으면(주석 처리·삭제된 메뉴) 조용히 넘어간다.
        // 2026-08-23: dqGroup / andGroup 은 이미 NdNav 에 존재하지 않아 null 이 된다.
        _activateGroup(groupId) {
            const el = document.getElementById(groupId);
            if (!el || !el.childNodes || !el.childNodes[0] || !el.childNodes[0].classList) return;
            el.childNodes[0].classList.add('v-list-item--active', 'ndColor--text');
        },
        // nav 항목 id 는 'nav_' + 컴포넌트명 규칙을 따르지 않는 경우가 있다.
        // 예) nav_dm_history ↔ 컴포넌트 dmHistory, nav_area_mgmt ↔ areaMgmt
        // 규칙 위반분을 여기서 흡수한다. 신규 메뉴 추가 시 id 를 규칙에 맞추면 등록 불필요.
        _navIdOf(tabName) {
            const alias = {
                dmHistory:    'nav_dm_history',
                myDmChanges:  'nav_my_dm_changes',
                dmApproval:   'nav_dm_approval',
                areaMgmt:     'nav_area_mgmt',
                myDmApproval: 'nav_mypage_dm_approval',
                myDmChangeList: 'nav_mypage_dm_changes',
            };
            return alias[tabName] || ('nav_' + tabName);
        },
        changeNavItem(tabName) {
            const active = document.querySelectorAll('.v-list-item--active');

            active.forEach(active => {
                active.classList.remove('v-list-item--active', 'ndColor--text');
            });

            if (this.tabs.length === 0) {
                this.navAllGroupClose();
                return;
            } else {
                // group Active
                if (tabName === 'boardNotice' || tabName === 'boardQna') {
                    this._activateGroup("communityGroup");
                } else if (tabName === 'myRequest' || tabName === 'myProfile') {
                    this._activateGroup("myPageGroup");
                } else if (tabName === 'term' || tabName === 'dsCode' || tabName === 'word' || tabName === 'domain' || tabName === 'domainGroup' || tabName === 'domainClassification' || tabName === 'changeHistory') {
                    this._activateGroup("dsGroup");

                } else if (tabName === 'dataDiag' || tabName === 'dataDiagResult') {
                    this._activateGroup("diagGroup");
                } else if (tabName === 'structDiag' || tabName === 'structDiagResult') {
                    this._activateGroup("structDiagGroup");
                } else if (tabName === 'datamodelStatus' || tabName === 'datamodelCollection' || tabName === 'datamodelHistory' || tabName === 'datamodelStatusTable' || tabName === 'datamodelStatusColumn' || tabName === 'erwinImport') {
                    this._activateGroup("dmGroup");

                } else if (tabName === 'dqi' || tabName === 'ctq' || tabName === 'dqbr' || tabName === 'target' || tabName === 'qv' || tabName === 'rvi' || tabName === 'dqqvrt') {
                    this._activateGroup("dqGroup");

                    if (tabName === 'dqi' || tabName === 'ctq' || tabName === 'dqbr') {
                        this._activateGroup("navDqSub1");
                    } else if (tabName === 'target' || tabName === 'qv') {
                        this._activateGroup("navDqSub2");
                    } else if (tabName === 'rvi' || tabName === 'dqqvrt') {
                        this._activateGroup("navDqSub3");
                    }

                } else if (tabName === 'user' || tabName === 'roles' || tabName === 'approval' || tabName === 'system' || tabName ==='datasource') {
                    this._activateGroup("mmGroup");
                } else if (tabName === 'scurrent' || tabName === 'qcurrent') {
                    this._activateGroup("andGroup");
                }

                // item Active
                const navEl = document.getElementById(this._navIdOf(tabName));
                if (navEl) navEl.classList.add("v-list-item--active");
            }
        },
        navAllGroupClose() {
            this.navDsGroup = null;
            this.navDqGroup = null;
            this.navMmGroup = null;
            this.navAndGroup = null;
            this.navDmGroup = null;
            this.navDiagGroup = null;
            this.navStructDiagGroup = null;
            this.navAutoStdGroup = null;
            this.navMyPageGroup = null;
            this.navCommunityGroup = null;
            this.navScheduleGroup = null;
            this.navQualGroup = null;
            this.navSubGropClose();
        },
        navSubGropClose() {
            this.navDsSub1 = null;
            this.navDsSub2 = null;
            this.navDsSub3 = null;
            this.navDsStatusGroup = null;
            this.navDqSub1 = null;
            this.navDqSub2 = null;
            this.navDqSub3 = null;
        },
        addNavGroupData(target) {
            if (this.mini === true) {
                this.resetSplit();
                return;
            };

            switch (target) {
                case "dsGroup":

                    if (this.navDsGroup === null) {
                        this.navDsGroup = false;
                    } else {
                        this.navDsGroup = !this.navDsGroup;
                    }
                    break;

                case "dqGroup":

                    if (this.navDqGroup === null) {
                        this.navDqGroup = false;
                    } else {
                        this.navDqGroup = !this.navDqGroup;
                    }
                    break;

                case "mmGroup":

                    if (this.navMmGroup === null) {
                        this.navMmGroup = false;
                    } else {
                        this.navMmGroup = !this.navMmGroup;
                    }
                    break;

                case "andGroup":

                    if (this.navAndGroup === null) {
                        this.navAndGroup = false;
                    } else {
                        this.navAndGroup = !this.navAndGroup;
                    }
                    break;

                case "dmGroup":

                    if (this.navDmGroup === null) {
                        this.navDmGroup = false;
                    } else {
                        this.navDmGroup = !this.navDmGroup;
                    }
                    break;

                case "diagGroup":

                    if (this.navDiagGroup === null) {
                        this.navDiagGroup = false;
                    } else {
                        this.navDiagGroup = !this.navDiagGroup;
                    }
                    break;

                case "structDiagGroup":

                    if (this.navStructDiagGroup === null) {
                        this.navStructDiagGroup = false;
                    } else {
                        this.navStructDiagGroup = !this.navStructDiagGroup;
                    }
                    break;

                case "autoStdGroup":

                    if (this.navAutoStdGroup === null) {
                        this.navAutoStdGroup = false;
                    } else {
                        this.navAutoStdGroup = !this.navAutoStdGroup;
                    }
                    break;

                case "myPageGroup":

                    if (this.navMyPageGroup === null) {
                        this.navMyPageGroup = false;
                    } else {
                        this.navMyPageGroup = !this.navMyPageGroup;
                    }
                    break;

                case "communityGroup":

                    if (this.navCommunityGroup === null) {
                        this.navCommunityGroup = false;
                    } else {
                        this.navCommunityGroup = !this.navCommunityGroup;
                    }
                    break;

                case "scheduleGroup":

                    if (this.navScheduleGroup === null) {
                        this.navScheduleGroup = false;
                    } else {
                        this.navScheduleGroup = !this.navScheduleGroup;
                    }
                    break;

                case "qualGroup":

                    if (this.navQualGroup === null) {
                        this.navQualGroup = false;
                    } else {
                        this.navQualGroup = !this.navQualGroup;
                    }
                    break;
            }
        },
        addNavSubGroupData(target) {
            this.navSubGropClose();

            switch (target) {
                case "navDsSub1":
                    this.navDsSub1 = false;
                    break;
                case "navDsSub2":
                    this.navDsSub2 = false;
                    break;
                case "navDsSub3":
                    this.navDsSub3 = false;
                    break;
                case "navDqSub1":
                    this.navDqSub1 = false;
                    break;
                case "navDqSub2":
                    this.navDqSub2 = false;
                    break;
                case "navDqSub3":
                    this.navDqSub3 = false;
                    break;
            }
        },
        navAndTabSync(tabitem) {

            if (this.mini === true) {
                this.miniSizeActiveStyle();
                return;
            }

            this.navAllGroupClose();

            if (tabitem === 'boardNotice' || tabitem === 'boardQna') {
                this.navCommunityGroup = true;
                return;
            } else if (tabitem === 'myRequest' || tabitem === 'myProfile') {
                this.navMyPageGroup = true;
                return;
            } else if (tabitem === 'termRecommend') {
                this.navAutoStdGroup = true;
                return;
            } else if (tabitem === 'dataDiag' || tabitem === 'dataDiagResult') {
                this.navDiagGroup = true;
                return;
            } else if (tabitem === 'structDiag' || tabitem === 'structDiagResult') {
                this.navStructDiagGroup = true;
                return;
            } else if (tabitem === 'datamodelStatus' || tabitem === 'datamodelCollection' || tabitem === 'datamodelHistory' || tabitem === 'datamodelStatusTable' || tabitem === 'datamodelStatusColumn' || tabitem === 'erwinImport') {
                this.navDmGroup = true;
                return;
            } else if (tabitem === 'word' || tabitem === 'term' || tabitem === 'dsCode' || tabitem === 'domain' || tabitem === 'domainGroup' || tabitem === 'domainClassification' || tabitem === 'changeHistory') {
                this.navDsGroup = true;
                return;
            } else if (tabitem === 'dqi' || tabitem === 'ctq' || tabitem === 'dqbr') {
                this.navDqGroup = true;

                setTimeout(() => {
                    this.navDqSub1 = true;
                    this.navDqSub2 = null;
                    this.navDqSub3 = null;
                }, 500);

                return;
            } else if (tabitem === 'target' || tabitem === 'qv') {
                this.navDqGroup = true;

                setTimeout(() => {
                    this.navDqSub1 = null;
                    this.navDqSub2 = true;
                    this.navDqSub3 = null;
                }, 500);

                return;
            } else if (tabitem === 'rvi' || tabitem === 'dqqvrt') {
                this.navDqGroup = true;

                setTimeout(() => {
                    this.navDqSub1 = null;
                    this.navDqSub2 = null;
                    this.navDqSub3 = true;
                }, 500);

                return;
            } else if (tabitem === 'user' || tabitem === 'roles' || tabitem === 'approval' || tabitem === 'system' || tabitem === 'datasource') {
                this.navMmGroup = true;
                this.navSubGropClose();

                return;
            } else if (tabitem === 'scurrent' || tabitem === 'qcurrent') {
                this.navAndGroup = true;
                this.navSubGropClose();

                return;
            }
        },
        changeTabStatu() {
            this.tabStatu = 'change';
        },
        sendApprovalStatus() {
            // 승인 메뉴 선택 시 대시보드에서 보내주는 상태값과 다르게 보내줘야 함.
            // 대시보드에서 직접 승인 메뉴 접근하지 않고 네비게이션으로 접근할 경우 초기화 필요함
            // 탭에 이미 승인이 있을 때는 return하고 없으면 초기화
            if (this.tabs.filter(tab => tab.title === '승인').length > 0) {
                return;
            } else {
                this.navApprovalStatus = [];
            }
        }
    },
}
</script>
<style scoped>
#splistMain {
    overflow-x: hidden !important;
    overflow-y: hidden !important;
}
</style>