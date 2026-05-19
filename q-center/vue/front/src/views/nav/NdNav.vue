<template>
    <v-navigation-drawer app left clipped :permanent="!isMobile" :mini-variant.sync=this.navSize v-bind="$attrs"
        :class="isMobile ? 'mobileNav' : ''" :width="240">
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
                <v-list-group link v-cloak :value=" navDsGroup " prepend-icon="mdi-book-open-page-variant" active-class="ndColor--text"
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

                    <v-list-item link id="nav_changeHistory" href="#tab_changeHistory"
                        active-class="ndColor--text" title="변경 이력"
                        v-on:click.stop=" addTabItem('변경 이력', 'changeHistory'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>변경 이력</v-list-item-title>
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

                    <v-list-item link id="nav_datamodelStatusIndex" href="#tab_datamodelStatusIndex"
                        active-class="ndColor--text" title="인덱스"
                        v-on:click.stop="addTabItem('인덱스', 'datamodelStatusIndex');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>인덱스</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelStatusConstraint" href="#tab_datamodelStatusConstraint"
                        active-class="ndColor--text" title="제약조건"
                        v-on:click.stop="addTabItem('제약조건', 'datamodelStatusConstraint');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>제약조건</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datamodelCollection" href="#tab_datamodelCollection"
                        active-class="ndColor--text" title="데이터 모델 관리"
                        v-on:click.stop="addTabItem('데이터 모델 관리', 'datamodelCollection');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 관리</v-list-item-title>
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

                    <!-- 85번 — 모델링 도구 임포트 (ERwin native XML / XMI 2.1) -->
                    <v-list-item link id="nav_erwinImport" href="#tab_erwinImport"
                        active-class="ndColor--text" title="모델링 도구 임포트"
                        v-on:click.stop="addTabItem('모델링 도구 임포트', 'erwinImport');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>모델링 도구 임포트</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_diagTargetMgmt" href="#tab_diagTargetMgmt"
                        active-class="ndColor--text" title="진단 제외 관리"
                        v-on:click.stop="addTabItem('진단 제외 관리', 'diagTargetMgmt');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>진단 제외 관리</v-list-item-title>
                    </v-list-item>

                    <!-- 88번 거버넌스 — 내 변경 신청 (사용자 + 관리자 모두 사용) -->
                    <v-list-item link id="nav_my_dm_changes" href="#tab_my_dm_changes"
                        active-class="ndColor--text" title="내 변경 신청"
                        v-on:click.stop="addTabItem('내 변경 신청', 'myDmChanges');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>내 변경 신청</v-list-item-title>
                    </v-list-item>

                    <!-- 88번 거버넌스 — 데이터 모델 변경 이력 (양쪽 모두 사용) -->
                    <v-list-item link id="nav_dm_history" href="#tab_dm_history"
                        active-class="ndColor--text" title="데이터 모델 변경 이력"
                        v-on:click.stop="addTabItem('데이터 모델 변경 이력', 'dmHistory');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 변경 이력</v-list-item-title>
                    </v-list-item>

                </v-list-group>

                <!-- 표준 진단 -->
                <v-list-group link v-cloak :value="navDiagGroup" prepend-icon="search" active-class="ndColor--text"
                    id="diagGroup" title="표준 진단" v-on:click.stop="addNavGroupData('diagGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">표준 진단</v-list-item-title>
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

                <!-- 구조 진단 -->
                <v-list-group link v-cloak :value="navStructDiagGroup" prepend-icon="mdi-database-search" active-class="ndColor--text"
                    id="structDiagGroup" title="구조 변경 진단" v-on:click.stop="addNavGroupData('structDiagGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">구조 변경 진단</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_structDiag" href="#tab_structDiag"
                        active-class="ndColor--text" title="진단 실행"
                        v-on:click.stop="addTabItem('진단 실행', 'structDiag');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>진단 실행</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_structDiagResult" href="#tab_structDiagResult"
                        active-class="ndColor--text" title="진단 결과"
                        v-on:click.stop="addTabItem('진단 결과', 'structDiagResult');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>진단 결과</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 데이터 품질 진단 (값 진단 + 업무 규칙) — 67번 / 위치: 구조 변경 진단 직후
                     2026-05-13: 영업 라인업 분리 (DataQ ↔ DQ 별도) 결정에 따라 메뉴 전체 주석 처리.
                     표준화 진단은 메타 필수라 유지. 복원 시 아래 블록 주석 해제만 하면 됨.
                <v-list-group link v-cloak :value="navQualGroup" prepend-icon="mdi-database-check"
                    active-class="ndColor--text" id="qualGroup" title="데이터 품질 진단" v-on:click.stop="addNavGroupData('qualGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">데이터 품질 진단</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    83번 신규: 도메인 룰 관리 (분류 트리 + 룰 정의 + 카탈로그 가져오기)
                    <v-list-item link id="nav_qualDomainRule" href="#tab_qualDomainRule" active-class="ndColor--text" title="도메인 룰 관리"
                        v-on:click.stop="addTabItem('도메인 룰 관리', 'qualDomainRule');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>도메인 룰 관리</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_valueProfile" href="#tab_valueProfile" active-class="ndColor--text" title="값 프로파일링"
                        v-on:click.stop="addTabItem('값 프로파일링', 'qualValueProfile');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>값 프로파일링</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_ruleManage" href="#tab_ruleManage" active-class="ndColor--text" title="업무 규칙 관리"
                        v-on:click.stop="addTabItem('업무 규칙 관리', 'qualRuleManage');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>업무 규칙 관리</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_ruleResult" href="#tab_ruleResult" active-class="ndColor--text" title="업무 규칙 진단 결과"
                        v-on:click.stop="addTabItem('업무 규칙 진단 결과', 'qualRuleResult');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>업무 규칙 진단 결과</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_qualColRule" href="#tab_qualColRule" active-class="ndColor--text" title="컬럼 규칙 매핑"
                        v-on:click.stop="addTabItem('컬럼 규칙 매핑', 'qualColRule');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>컬럼 규칙 매핑</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_qualStats" href="#tab_qualStats" active-class="ndColor--text" title="진단 통계"
                        v-on:click.stop="addTabItem('진단 통계', 'qualStats');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>진단 통계</v-list-item-title>
                    </v-list-item>
                </v-list-group>
                -->
                <!-- /데이터 품질 진단 메뉴 주석 처리 끝 -->

                <!-- 86번 #44 — "자동 표준화 지원" → "표준화 도구" / "컬럼 표준화" → "한글컬럼 일괄 표준화" -->
                <v-list-group link v-cloak :value="navAutoStdGroup" prepend-icon="auto_fix_high" active-class="ndColor--text"
                    id="autoStdGroup" title="표준화 도구" v-on:click.stop="addNavGroupData('autoStdGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">표준화 도구</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_termRecommend" href="#tab_termRecommend"
                        active-class="ndColor--text" title="한글컬럼 일괄 표준화"
                        v-on:click.stop="addTabItem('한글컬럼 일괄 표준화', 'termRecommend');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>한글컬럼 일괄 표준화</v-list-item-title>
                    </v-list-item>

                    <!-- 88번 §16 — 한글 변환 이력 (매핑 정의서 작성용) -->
                    <v-list-item link id="nav_termResolveHistory" href="#tab_term_resolve_history"
                        active-class="ndColor--text" title="한글 변환 이력"
                        v-on:click.stop="addTabItem('한글 변환 이력', 'termResolveHistory');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>한글 변환 이력</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 커뮤니티 -->
                <v-list-group link v-cloak :value="navCommunityGroup" prepend-icon="mdi-forum"
                    active-class="ndColor--text" id="communityGroup" title="커뮤니티"
                    v-on:click.stop="addNavGroupData('communityGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">커뮤니티</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_boardNotice" href="#tab_boardNotice" active-class="ndColor--text"
                        title="공지사항"
                        v-on:click.stop="addTabItem('공지사항', 'boardNotice');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>공지사항</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_boardQna" href="#tab_boardQna" active-class="ndColor--text"
                        title="Q&amp;A"
                        v-on:click.stop="addTabItem('Q&amp;A', 'boardQna');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>Q&amp;A</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 86번 #47 — "진단 스케줄" → "스케줄 관리" -->
                <v-list-group link v-cloak :value="navScheduleGroup" prepend-icon="mdi-calendar-clock"
                    active-class="ndColor--text" id="scheduleGroup" title="스케줄 관리" v-on:click.stop="addNavGroupData('scheduleGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">스케줄 관리</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_scheduleManage" href="#tab_scheduleManage" active-class="ndColor--text" title="스케줄 관리"
                        v-on:click.stop="addTabItem('스케줄 관리', 'scheduleManage');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>스케줄 관리</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_scheduleLog" href="#tab_scheduleLog" active-class="ndColor--text" title="실행 이력"
                        v-on:click.stop="addTabItem('실행 이력', 'scheduleLog');">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>실행 이력</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 마이페이지 -->
                <v-list-group link v-cloak :value="navMyPageGroup" prepend-icon="mdi-account-circle"
                    active-class="ndColor--text" id="myPageGroup" title="마이페이지"
                    v-on:click.stop="addNavGroupData('myPageGroup');">
                    <template v-slot:activator>
                        <v-list-item-content>
                            <v-list-item-title :ripple="false">마이페이지</v-list-item-title>
                        </v-list-item-content>
                    </template>

                    <v-list-item link id="nav_myProfile" href="#tab_myProfile" active-class="ndColor--text"
                        title="내 정보"
                        v-on:click.stop="addTabItem('내 정보', 'myProfile');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>내 정보</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_myRequest" href="#tab_myRequest" active-class="ndColor--text"
                        title="요청 현황"
                        v-on:click.stop="addTabItem('요청 현황', 'myRequest');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>요청 현황</v-list-item-title>
                    </v-list-item>

                    <!-- 88번 거버넌스 — 일반: 내 변경 신청 / 관리자: 데이터 모델 변경 승인 -->
                    <v-list-item v-if="!isAdmin" link id="nav_mypage_dm_changes" href="#tab_my_dm_changes"
                        active-class="ndColor--text" title="내 변경 신청"
                        v-on:click.stop="addTabItem('내 변경 신청', 'myDmChanges');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>내 변경 신청</v-list-item-title>
                    </v-list-item>

                    <v-list-item v-if="isAdmin" link id="nav_mypage_dm_approval" href="#tab_dm_approval"
                        active-class="ndColor--text" title="데이터 모델 변경 승인"
                        v-on:click.stop="addTabItem('데이터 모델 변경 승인', 'dmApproval');">
                        <v-list-item-icon><v-icon></v-icon></v-list-item-icon>
                        <v-list-item-title>데이터 모델 변경 승인</v-list-item-title>
                    </v-list-item>
                </v-list-group>

                <!-- 관리 (관리자만) -->
                <v-list-group v-if="isAdmin" link v-cloak :value=" navMmGroup " prepend-icon="app_registration"
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

                    <!-- 88번 거버넌스 — 데이터 모델 변경 승인 (관리자 전용) -->
                    <v-list-item link id="nav_dm_approval" href="#tab_dm_approval" active-class="ndColor--text" title="데이터 모델 변경 승인"
                        v-on:click.stop=" addTabItem('데이터 모델 변경 승인', 'dmApproval'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 모델 변경 승인</v-list-item-title>
                    </v-list-item>

                    <!-- 88번 §15 — 영역 관리 (관리자 전용) -->
                    <v-list-item link id="nav_area_mgmt" href="#tab_area_mgmt" active-class="ndColor--text" title="영역 관리 (업무/주제)"
                        v-on:click.stop=" addTabItem('영역 관리', 'areaMgmt'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>영역 관리 (업무/주제)</v-list-item-title>
                    </v-list-item>

                    <v-list-item link id="nav_datasource" href="#tab_datasource" active-class="ndColor--text" title="데이터 소스"
                        v-on:click.stop=" addTabItem('데이터 소스', 'datasource'); ">
                        <v-list-item-icon>
                            <v-icon></v-icon>
                        </v-list-item-icon>
                        <v-list-item-title>데이터 소스</v-list-item-title>
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
import axios from 'axios';
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
        'navAutoStdGroup',
        'navCommunityGroup',
        'navScheduleGroup',
        'navQualGroup',
        'navMyPageGroup'],
    data: () => ({
        selectedList: null,
        wsLogShow: false,
        isAdmin: false,
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
        var self = this;
        axios.get(this.$APIURL.base + 'api/login/isAdmin', { params: { user: this.$loginStatusData.id } })
            .then(function(res) { self.isAdmin = res.data === true; });
        /**
         * DSDataDiag '결과 보기' 버튼 클릭 시 진단 결과 탭을 열기 위한 이벤트 수신
         * - eventBus 'openDiagResult' 이벤트를 받으면 진단 결과 탭을 추가/활성화
         * - diagJobId는 eventBus.pendingDiagJobId에 저장되어 DSDataDiagResult에서 참조
         */
        eventBus.$on('openDiagResult', () => {
            this.addTabItem('진단 결과', 'dataDiagResult');
        });
        eventBus.$on('openStructDiagResult', () => {
            this.addTabItem('진단 결과', 'structDiagResult');
        });
        eventBus.$on('openColumnView', () => {
            this.addTabItem('컬럼', 'datamodelStatusColumn');
        });
    },
    beforeDestroy() {
        eventBus.$off('openDiagResult');
        eventBus.$off('openStructDiagResult');
        eventBus.$off('openColumnView');
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
    /* 2026-05-17 — 알림 로그 패널 임시 숨김. 미완성 상태(TEST 로그/오타)라 정리 전까지 가림.
       복원하려면 아래 display:none 한 줄만 제거. logTextWrap DOM 은 유지되어 main.js 참조 안전. */
    display: none;
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
