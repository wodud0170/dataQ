<template>
  <v-main>
    <Split direction="vertical" :style="{ overflow: 'hidden' }">
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <!-- 검색과 버튼 영역 -->
        <v-sheet class="splitTopWrapper pt-4 pb-4"
          v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
          <!-- 검색 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-row :style="{ alignItems: 'center', margin: '0px' }">
              <span :style="{ fontSize: '.875rem' }">시스템명</span>
              <!-- 시스템명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchSystem" v-on:keyup.enter="getDataModelStatsList"
                @click:clear="clearSystemMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <span :style="{ fontSize: '.875rem' }">모델명</span>
              <!-- 모델명 입력 필드 -->
              <v-text-field class="pr-4 pl-4" v-model="searchModel" v-on:keyup.enter="getDataModelStatsList"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <!-- 검색 버튼 -->
              <v-btn class="gradient" title="검색" v-on:click="getDataModelStatsList"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ dmStatusItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <!-- 데이터 모델 현황 목록 -->
        <v-data-table id="dmStatus_table" :headers="dataModelHeaders" :items="dmStatusItems" :page.sync="page"
          v-model="dmStatusItems" :items-per-page="itemsPerPage" hide-default-header hide-default-footer
          item-key="dataModelId" class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">

          <!-- thead -->
          <template #header="{}">
            <thead class="v-data-table-header">
              <tr>
                <!-- thead -->
                <th v-for="(h, i) in dataModelHeaders" :key="i" class="text-center parent-header td-border-style"
                  :rowspan="h.children ? 1 : 2" :colspan="h.children ? h.children.length : 1">
                  <pre>{{ h.text }}</pre>
                </th>
              </tr>
              <tr>
                <!-- sub thead -->
                <th v-for="(h1, i1) in getSubHeader(dataModelHeaders)" :key="i1"
                  class="text-center child-header td-border-style"
                  :style="{ borderTop: '0px', borderLeft: '0px', backgroundColor: 'rgba(63, 81, 181, 0.08)' }">
                  <pre>{{ h1.text }}</pre>
                </th>
              </tr>
            </thead>
          </template>
          <template #item="props">
            <tr>
              <td v-for="(c, ci) in getRows(props.item)" :key="ci"
                :style="{ padding: '0px', backgroundColor: '#ffffff' }">
                <!-- 클릭 가능한 요소 생성 -->
                <span v-if="ci === 'dataModelNm'" class="ndColor--text" :style="{ cursor: 'pointer', margin: '0px 16px' }"
                  @click="showDetail(props.item)">{{ c }}</span>
                <!-- 시스템명 -->
                <span v-else-if="ci === 'dataModelSysNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 데이터 소스 -->
                <span v-else-if="ci === 'dataModelDsNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 테이블 개수 -->
                <span v-else-if="ci === 'objCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 컬럼 개수 -->
                <span v-else-if="ci === 'attrCnt'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 수집일시 셀 - 버튼 추가 -->
                <template v-else-if="ci === 'clctDt'">
                  <div :style="{ display: 'flex', alignItems: 'center', justifyContent: 'space-around' }">
                    <span :style="{ margin: '0px 16px', width: 'calc(100% - 40px)' }">{{ c }}</span>
                    <!-- 수집일시 데이터가 없으면 선택 버튼 보이지 않기 -->
                    <v-btn v-if="c.length > 0" class="gradient" title="수집일시" v-on:click="showCltList(props.item)"
                      :style="{ width: '40px', height: '25px !important', padding: '0 5px', minWidth: '45px', marginRight: '16px !important' }">선택</v-btn>
                  </div>
                </template>
                <!-- 구조 진단 -->
                <template v-else-if="ci === 'structDiag'">
                  <div :style="{ textAlign: 'center', padding: '4px 8px' }">
                    <v-chip v-if="props.item.structDiagYn === 'Y'" x-small color="green" text-color="white" :title="'구조진단 ' + props.item.structDiagDt">
                      <v-icon x-small left>mdi-check</v-icon>일치
                    </v-chip>
                    <v-chip v-else-if="props.item.structDiagDt" x-small color="orange" text-color="white" :title="'구조진단 ' + props.item.structDiagDt">
                      <v-icon x-small left>mdi-alert</v-icon>불일치
                    </v-chip>
                    <span v-else class="grey--text text-caption">미진단</span>
                    <div v-if="props.item.structDiagDt" class="text-caption grey--text" style="font-size:.65rem;">{{ props.item.structDiagDt }}</div>
                  </div>
                </template>
                <!-- 표준 준수율 -->
                <template v-else-if="ci === 'diagStndRate'">
                  <div :style="{ textAlign: 'center', padding: '4px 8px' }">
                    <template v-if="props.item.diagDt">
                      <v-chip x-small :color="props.item.diagStndRate >= 90 ? 'green' : props.item.diagStndRate >= 70 ? 'orange' : 'red'" text-color="white">
                        {{ props.item.diagStndRate }}%
                      </v-chip>
                      <div class="text-caption grey--text" style="font-size:.65rem;">{{ props.item.diagDt }}</div>
                    </template>
                    <span v-else class="grey--text text-caption">미진단</span>
                  </div>
                </template>
                <!-- 버전 -->
                <span v-else-if="ci === 'ver'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                <!-- 일반 아이템 -->
                <!-- <span v-else :style="{ margin: '0px 16px' }">{{ c }}</span> -->
              </td>
            </tr>
          </template>
          <template #top>
            <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
          </template>
          <template #no-data>
            <v-alert v-show="!loadTable">
              데이터가 존재하지 않습니다.
            </v-alert>
            <span v-show="loadTable">잠시만 기다려주세요.</span>
          </template>
        </v-data-table>

        <v-sheet class="split_bottom_wrap">
          <!-- 페이지네이션 -->
          <div class="text-center px-4 pt-4 pb-4 pagination_wrap">
            <!-- <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1"> -->
            <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
              color="ndColor" :total-visible="10"></v-pagination>
          </div>
        </v-sheet>
      </SplitArea>
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <v-sheet>
          <!-- 탭 -->
          <v-tabs :value="this.detailTab" class="tabsStyle" background-color="rgba(0,0,0,0.1)">
            <v-tab v-for="item in detailTab" :tabindex="item.index" :key="item.index" class="tabBgColor"
              active-class="activeTabBgColor" v-on:click.stop="addActiveDetail(item.name, item.index)"
              :style="{ borderRight: '1px solid rgba(255,255,255, 0.4) !important' }">
              {{ item.title }}
            </v-tab>
          </v-tabs>
        </v-sheet>
        <!-- 탭별 콘텐츠 -->
        <v-sheet v-if="activeDetailTab === 'tab1'" class="tabContentsWrapper">
          <!-- 테이블 탭 콘텐츠 -->
          <div class="split_bottom" v-if="selectedItem.length != 0">
            <v-sheet class="splitBottomWrapper"></v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-sheet class="splitTopWrapper"
                v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]"
                :style="{ justifyContent: 'flex-start' }">
                <!-- 타이틀 -->
                <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
                  <span class="splitBottomSpan"
                    :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                      detailDataModelName
                    }}'</span>
                  <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;테이블 정보</span>
                </v-sheet>

                <!-- 검색 -->
                <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
                  <v-row :style="{ alignItems: 'center', margin: '0px' }">
                    <span :style="{ fontSize: '.875rem' }">테이블명</span>
                    <!-- 테이블명 입력 필드 -->
                    <v-text-field class="pr-4 pl-4" v-model="searchTable" v-on:keyup.enter="getDataModelObjListByObjNm"
                      @click:clear="clearScTableMessage" clearable prepend-icon="" clear-icon="mdi-close-circle"
                      type="text" color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
                    </v-text-field>
                    <!-- 검색 버튼 -->
                    <v-btn class="gradient" title="검색" v-on:click="getDataModelObjListByObjNm"
                      :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
                  </v-row>
                </v-sheet>

                <!-- 다운로드 버튼 -->
                <v-sheet class="pr-4 pl-4" :style="{ marginLeft: 'auto' }">
                  <v-btn class="gradient" v-on:click="tableDataDownload()">다운로드</v-btn>
                </v-sheet>
              </v-sheet>
              <!-- 테이블 데이터 목록 -->
              <v-data-table id="dmTable_table" :headers="dmTabledetaileHeaders" :items="dmTableItems" :page.sync="tb_page"
                :items-per-page="tb_itemsPerPage" hide-default-footer item-key="tableid" class="px-4 pb-3"
                :loading="tb_loadTable" loading-text="잠시만 기다려주세요.">
                <!-- 클릭 가능한 아이템 설정 : 테이블 한글명  -->
                <!-- <template v-slot:[`item.objNmKr`]="{ item }">
                  <span class="ndColor--text" :style="{ cursor: 'pointer' }">{{
                    item.objNmKr
                  }}</span>
                </template> -->
                <!-- 데이터 없음 -->
                <template #top>
                  <v-progress-linear v-show="tb_loadTable" color="indigo darken-2" indeterminate />
                </template>
                <template #no-data>
                  <v-alert v-show="!tb_loadTable">
                    데이터가 존재하지 않습니다.
                  </v-alert>
                  <span v-show="tb_loadTable">잠시만 기다려주세요.</span>
                </template>
              </v-data-table>

              <v-sheet class="dmTable_table_wrap">
                <!-- 페이지네이션 -->
                <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="tb_pageCount > 1">
                  <v-pagination v-model="tb_page" :length="tb_pageCount" prev-icon="mdi-menu-left"
                    next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
                </div>
              </v-sheet>
            </v-sheet>
          </div>
        </v-sheet>
        <v-sheet v-else class="tabContentsWrapper">
          <!-- 컬럼 탭 콘텐츠 -->
          <div class="split_bottom" v-if="selectedItem.length != 0">
            <v-sheet class="splitBottomWrapper"></v-sheet>
            <!-- 테이블 -->
            <v-sheet class="tabContents">
              <v-sheet class="splitTopWrapper" :style="{ justifyContent: 'flex-start' }"
                v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
                <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
                  <span class="splitBottomSpan"
                    :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                      detailDataModelName
                    }}'</span>
                  <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;컬럼 정보</span>
                </v-sheet>
                <!-- 검색 -->
                <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
                  <v-row :style="{ alignItems: 'center', margin: '0px' }">
                    <!-- 테이블명 입력 필드 -->
                    <span :style="{ fontSize: '.875rem' }">테이블명</span>
                    <v-text-field class="pr-4 pl-4" v-model="searchTable"
                      v-on:keyup.enter="getDataModelAttrListByRetreiveCond()" @click:clear="clearScTableMessage" clearable
                      prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor" single-line dense outlined
                      hide-details :style="{ width: '200px' }">
                    </v-text-field>
                    <!-- 컬럼명 입력 필드 -->
                    <span :style="{ fontSize: '.875rem' }">컬럼명</span>
                    <v-text-field class="pr-4 pl-4" v-model="searchColumn"
                      v-on:keyup.enter="getDataModelAttrListByRetreiveCond()" @click:clear="clearScColumnMessage"
                      clearable prepend-icon="" clear-icon="mdi-close-circle" type="text" color="ndColor" single-line
                      dense outlined hide-details :style="{ width: '200px' }">
                    </v-text-field>
                    <!-- 체크박스 -->
                    <span :style="{ fontSize: '.875rem', paddingRight:'15px' }">표준여부</span>
                    <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="표준" color="ndColor" value="Y"></v-checkbox>
                    <v-checkbox class="checkboxStyle" hide-details v-model="statusListArray" label="비표준" color="ndColor" value="N"></v-checkbox>
                    <!-- 검색 버튼 -->
                    <v-btn class="gradient" title="검색" v-on:click="getDataModelAttrListByRetreiveCond()"
                      :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
                  </v-row>
                </v-sheet>
                <!-- 다운로드 버튼 -->
                <v-sheet class="pr-4 pl-4" :style="{ marginLeft: 'auto' }">
                  <v-btn class="gradient" v-on:click="columnDataDownload()">다운로드</v-btn>
                </v-sheet>

              </v-sheet>
              <!-- 컬럼 목록 -->
              <v-data-table id="clTable_table" :headers="dmColumnDetaileHeaders" :items="dmColumnItems"
                :page.sync="cl_page" :items-per-page="cl_itemsPerPage" hide-default-footer hide-default-header
                item-key="tableid" class="px-4 pb-3" :loading="cl_loadTable" loading-text="잠시만 기다려주세요.">

                <!-- thead -->
                <template #header="">
                  <!-- <template v-slot:header="{props}"> -->
                  <thead class="v-data-table-header">
                    <tr>
                      <th v-for="(h, i) in dmColumnDetaileHeaders" :key="i"
                        class="text-center parent-header td-border-style" :rowspan="h.children ? 1 : 2"
                        :colspan="h.children ? h.children.length : 1">
                        <pre>{{ h.text }}</pre>
                      </th>
                    </tr>
                    <tr>
                      <!-- sub thead -->
                      <th v-for="(h1, i1) in clTb_getSubHeader(dmColumnDetaileHeaders)" :key="i1"
                        class="text-center child-header td-border-style"
                        :style="{ borderTop: '0px', borderLeft: '0px', backgroundColor: 'rgba(63, 81, 181, 0.08)' }">
                        <pre>{{ h1.text }}</pre>
                      </th>
                    </tr>
                  </thead>
                </template>
                <template #item="props">
                  <tr>
                    <td v-for="(c, ci) in clTb_getRows(props.item)" :key="ci"
                      :style="{ padding: '0px', backgroundColor: '#ffffff' }">
                      <!-- 테이블명 -->
                      <span v-if="ci === 'objNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- 테이블 한글명 -->
                      <span v-else-if="ci === 'objNmKr'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- 컬럼명 -->
                      <span v-else-if="ci === 'attrNm'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- 컬럼 한글명 -->
                      <span v-else-if="ci === 'attrNmKr'" class="ndColor--text"
                        :style="{ cursor: 'pointer', margin: '0px 16px' }" @click="showTermData(props.item)">{{ c
                        }}</span>
                      <!-- 데이터 타입 -->
                      <span v-else-if="ci === 'dataType'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- 데이터 길이 -->
                      <span v-else-if="ci === 'dataLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- 데이터 소수점 길이 -->
                      <span v-else-if="ci === 'dataDecimalLen'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                      <!-- Null 여부 -->
                      <p v-else-if="ci === 'nullableYn'" :style="{ textAlign: 'center', margin: '0px 16px' }">{{ c }}</p>

                      <!-- [숨김] 수집 시 표준검사 제거에 따라 표준여부 셀 비활성화 — 원복 시 주석 해제 -->
                      <!-- <template v-else-if="ci === 'termsStndYn'">
                        <p :style="{ textAlign: 'center', margin: '0px 5px' }">{{ c }}</p>
                      </template>
                      <template v-else-if="ci === 'domainStndYn'">
                        <p :style="{ textAlign: 'center', margin: '0px 5px' }">{{ c }}</p>
                      </template>
                      <template v-else-if="ci === 'wordLst'">
                        <p v-for="(line, index) in c" :key="index" :style="{ textAlign: 'center', margin: '0px 5px' }">
                          {{ line }}
                        </p>
                      </template> -->

                      <!-- PK 여부 -->
                      <p v-else-if="ci === 'pkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
                      <!-- FK 여부 -->
                      <p v-else-if="ci === 'fkYn'" :style="{ margin: '0px 16px' }">{{ c }}</p>
                      <!-- 디폴트 값 -->
                      <span v-else-if="ci === 'defaultVal'" :style="{ margin: '0px 16px' }">{{ c }}</span>
                    </td>
                  </tr>
                </template>

                <!-- 클릭 가능한 아이템 설정 : 테이블명  -->
                <template v-slot:[`item.attrNmKr`]="{ item }">
                  <span class="ndColor--text" :style="{ cursor: 'pointer' }">{{
                    item.attrNmKr
                  }}</span>
                </template>

                <!-- 데이터 없음 -->
                <template #top>
                  <v-progress-linear v-show="cl_loadTable" color="indigo darken-2" indeterminate />
                </template>
                <template #no-data>
                  <v-alert v-show="!cl_loadTable">
                    데이터가 존재하지 않습니다.
                  </v-alert>
                  <span v-show="cl_loadTable">잠시만 기다려주세요.</span>
                </template>
              </v-data-table>

              <v-sheet class="dmTable_table_wrap">
                <!-- 페이지네이션 -->
                <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="cl_pageCount > 1">
                  <v-pagination v-model="cl_page" :length="cl_pageCount" prev-icon="mdi-menu-left"
                    next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
                </div>
              </v-sheet>
            </v-sheet>
          </div>
        </v-sheet>

      </SplitArea>
    </Split>

    <!-- show DataModel Collection Date List Modal -->
    <v-dialog max-width="800" v-model="showDmCltDateModal">
      <NdModal id="dmCltDateModal" @hide="hideModal('dmCdl')" @submit="submitDialog('dmCdl')" :footer-submit="true"
        header-title="데이터모델 수집 목록 선택" footer-hide-title="취소" footer-submit-title="확인">
        <template v-slot:body>
          <v-container fluid :style="{ padding: '0', height: '100%' }">
            <v-row :style="{ alignItems: 'center', margin: '0px' }">
              <v-col cols="2">
                <v-subheader>조회 기간</v-subheader>
              </v-col>
              <v-col cols="6">
                <v-menu ref="dmCdlPicker" v-model="dmCdlPicker" :close-on-content-click="false"
                  :return-value.sync="dmCdlDate" transition="scale-transition" offset-y min-width="auto" color="ndColor">
                  <template v-slot:activator="{ on, attrs }">
                    <v-text-field hide-details v-model="dateRangeText" color="ndColor" prepend-icon="mdi-calendar"
                      readonly dense v-bind="attrs" v-on="on"></v-text-field>
                  </template>
                  <v-date-picker id="dmcltPicker" no-title range show-current color="ndColor" v-model="dmCdlDate"
                    scrollable>
                    <v-spacer></v-spacer>
                    <v-btn text color="ndColor" @click="dmCdlPicker = false">
                      취소
                    </v-btn>
                    <v-btn text color="ndColor" @click="$refs.dmCdlPicker.save(dmCdlDate)">
                      저장
                    </v-btn></v-date-picker>
                </v-menu>

              </v-col>
              <!-- 검색 버튼 -->
              <v-col cols="2" :style="{ marginBottom: '15px' }">
                <v-btn class="gradient" title="검색" v-on:click="searchDataModelClctList()"
                  :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              </v-col>
            </v-row>
            <!-- 테이블 영역 -->
            <v-sheet class="dmCdlList_table_wrap">
              <v-data-table id="dmCdlList_table" :headers="dmCdlListHeaders" :items="dmCdlListItems" :single-select="true"
                v-model="dmCdlListSelected" :page.sync="dmCdl_page" :items-per-page="dmCdl_itemsPerPage"
                hide-default-footer show-select item-key="index" class="px-4 pb-3">
                <!-- 데이터 없음 -->
                <template v-slot:no-data>
                  <v-alert>
                    데이터가 존재하지 않습니다.
                  </v-alert>
                </template>
              </v-data-table>
            </v-sheet>
            <v-sheet class="pagination_wrapper">
              <!-- 페이지네이션 -->
              <div class="text-center px-4 pt-4 pb-4 pagination_wrap">
                <!-- <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="dmCdl_pageCount > 1"> -->
                <v-pagination v-model="dmCdl_page" :length="dmCdl_pageCount" prev-icon="mdi-menu-left"
                  next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
              </div>
            </v-sheet>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- term data Modal -->
    <v-dialog max-width="800" v-model="termDataModalShow">
      <NdModal @hide="hideModal('termData')" :footer-submit="false" header-title="용어 상세 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="term_detail_table" :items="getTermDetailItem" :loading="cl_td_loadTable" hide-default-footer
              class="px-4 pb-3">
              <template v-slot:body="{ items }" v-if="getTermDetailItem.length !== 0">
                <tbody>
                  <!-- 상세 테이블 왼쪽  -->
                  <tr v-for="header in termDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">
                      {{ header.text }}
                    </td>
                    <!-- 상세 테이블 오른쪽  -->
                    <td v-for="item in items" :key="item.termNm">
                      <div v-if="Array.isArray(item[header.value])">
                        <!-- 값이 배열이라면 줄바꿈으로 표시 -->
                        <div v-for="item2 in item[header.value]" :key="item2">
                          {{ item2 }}
                        </div>
                      </div>
                      <div v-else>
                        <!-- 값이 배열이 아닌 문자열이라면 한 줄로 표시 -->
                        {{ item[header.value] }}
                      </div>
                    </td>
                  </tr>
                </tbody>
              </template>
              <!-- 데이터 없음 -->
              <template #top>
                <v-progress-linear v-show="cl_td_loadTable" color="indigo darken-2" indeterminate />
              </template>
              <template #no-data>
                <v-alert v-show="!cl_td_loadTable">
                  데이터가 존재하지 않습니다.
                </v-alert>
                <span v-show="cl_td_loadTable">잠시만 기다려주세요.</span>
              </template>
            </v-data-table>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
  </v-main>
</template>
<script>
import axios from 'axios';
import NdModal from "./../views/modal/NdModal.vue"
import _ from "lodash"

export default {
  name: 'DSDatamodelStatus',
  props: ['isMobile'],
  components: {
    NdModal,
  },
  watch: {
    dmStatusItems() {
      this.setListPage();
    },
    dmTableItems() {
      this.tb_setListPage();
    },
    dmColumnItems() {
      this.cl_setListPage();
    },
    itemsPerPage() {
      this.setListPage();
    },
    tb_itemsPerPage() {
      this.tb_setListPage();
    },
    cl_itemsPerPage() {
      this.cl_setListPage();
    },
    dmCdl_itemsPerPage() {
      this.dmCdl_setListPage();
    },

  },
  data: () => ({
    // 
    dmStatusItems: [],
    // 데이터 모델 테이블 리스트
    dmTableItems: [],
    // 데이터 모델 컬럼 리스트
    dmColumnItems: [],
    // 데이터 모델 수집 조회 리스트
    dmCdlListItems: [],
    // 데이터 모델명 클릭했을 때 표시할 데이터 모델명
    detailDataModelName: '',
    // 승인 요청 아이템의 상세 항목
    getTermDetailItem: [],
    // 수집 조회날짜 메뉴 피커 열고 닫기
    dmCdlPicker: false,
    // 선택 날짜
    dmCdlDate: [],
    // 시스템명 검색어
    searchSystem: '',
    // 모델명 검색어
    searchModel: '',
    // 테이블명 검색어
    searchTable: '',
    // 컬럼명 검색어
    searchColumn: '',
    // 선택된 아이템
    selectedItem: [],
    // 테이블 로딩
    loadTable: true,
    // 테이블 로딩
    tb_loadTable: true,
    // 테이블 로딩
    cl_loadTable: true,
    // 테이블 로딩
    cl_td_loadTable: true,
    // 페이지 시작
    page: 1,
    // 테이블 탭 페이지 시작
    tb_page: 1,
    // 컬럼 탭 페이지 시작
    cl_page: 1,
    // 수집일시 모달 테이블 페이지 시작
    dmCdl_page: 1,
    // 총 페이지 수
    pageCount: null,
    // 테이블 탭 총 페이지 수
    tb_pageCount: null,
    // 컬럼 탭 총 페이지 수
    cl_pageCount: null,
    // 수집일시 모달 테이블 총 페이지 수
    dmCdl_pageCount: null,
    // 한 페이지에 보여지는 아이템의 수
    itemsPerPage: 10,
    // 테이블 탭 한 페이지에 보여지는 아이템의 수
    tb_itemsPerPage: 10,
    // 컬럼 탭 한 페이지에 보여지는 아이템의 수
    cl_itemsPerPage: 10,
    // 수집일수 모달 테이블 한 페이지에 보여지는 아이템의 수
    dmCdl_itemsPerPage: 10,
    // 데이터모델 수집일시 - 수집 목록 모달
    showDmCltDateModal: false,
    // 데이터모델 용어 정보 모달
    termDataModalShow: false,
    // 상단 테이블 헤더
    dataModelHeaders: [
      { text: '데이터\n모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '시스템명', sortable: false, align: 'center', value: 'dataModelSysNm' },
      { text: '데이터소스', sortable: false, align: 'center', value: 'dataModelDsNm' },
      { text: '테이블\n개수', sortable: false, align: 'center', value: 'objCnt' },
      { text: '컬럼\n개수', sortable: false, align: 'center', value: 'attrCnt' },
      { text: '수집일시', sortable: false, align: 'center', value: 'clctDt' },
      { text: '구조\n진단', sortable: false, align: 'center', value: 'structDiag' },
      { text: '표준\n준수율', sortable: false, align: 'center', value: 'diagStndRate' },
      { text: '버전', sortable: false, align: 'center', value: 'ver' },
    ],
    // 탭 활성화
    activeDetailTab: 'tab1',
    // 디테일 메뉴 탭
    detailTab: [
      { title: '테이블', name: 'tab1', index: 0 },
      { title: '컬럼', name: 'tab2', index: 1 }
    ],
    // 하단 테이블 탭 헤더
    dmTabledetaileHeaders: [
      { text: '테이블명', align: 'start', sortable: false, value: 'objNm' },
      { text: '테이블 한글명', sortable: false, align: 'start', value: 'objNmKr' },
      { text: '소유자', sortable: false, align: 'start', value: 'objOwner' },
      { text: '컬럼개수', sortable: false, align: 'center', value: 'objAttrCnt' },
      { text: '테이블 설명', sortable: false, align: 'start', value: 'objDesc' },
    ],
    // 하단 컬럼 탭 헤더
    dmColumnDetaileHeaders: [
      { text: '테이블명', align: 'center', sortable: false, value: 'objNm' },
      { text: '테이블 한글명', sortable: false, align: 'center', value: 'objNmKr' },
      { text: '컬럼명', sortable: false, align: 'center', value: 'attrNm' },
      { text: '컬럼\n한글명', sortable: false, align: 'center', value: 'attrNmKr' },
      { text: '데이터\n타입', sortable: false, align: 'center', value: 'dataType' },
      { text: '데이터\n길이', sortable: false, align: 'center', value: 'dataLen' },
      { text: '데이터\n소수점\n길이', sortable: false, align: 'center', value: 'dataDecimalLen' },
      { text: 'NULL\n여부', sortable: false, align: 'center', value: 'nullableYn' },
      // [숨김] 수집 시 표준검사 제거에 따라 표준여부 헤더 비활성화 — 원복 시 주석 해제
      // {
      //   text: '표준 여부', sortable: false, align: 'center', value: 'termsStndYn', divider: true,
      //   children: [
      //     { text: '용어', align: 'center', value: 'termsStndYn', sortable: false },
      //     { text: '도메인', align: 'center', value: 'domainStndYn', sortable: false },
      //     { text: '단어', align: 'center', value: 'wordLst', sortable: false }
      //   ]
      // },
      { text: 'PK 여부', sortable: false, align: 'center', value: 'pkYn' },
      { text: 'FK 여부', sortable: false, align: 'center', value: 'fkYn' },
      { text: '디폴트 값', sortable: false, align: 'center', value: 'defaultVal' },
    ],
    // 수집일시 선택 모달 리스트 내부 테이블 헤더
    dmCdlListHeaders: [
      { text: '데이터모델명', align: 'center', sortable: false, value: 'dataModelNm' },
      { text: '수집 시작 시간', align: 'center', sortable: false, value: 'clctStartDt' },
      { text: '수집 완료 시간', align: 'center', sortable: false, value: 'clctEndDt' },
      { text: '완료 여부', align: 'center', sortable: false, value: 'clctCmptnYn' },
    ],
    // 용어 상세보기 헤더
    termDetaileHeaders: [
      { text: '용어명', align: 'center', sortable: false, value: 'termsNm', width: '15%' },
      { text: '용어영문약어명', sortable: false, align: 'center', value: 'termsEngAbrvNm', width: '15%' },
      { text: '용어설명', sortable: false, align: 'center', value: 'termsDesc' },
      { text: '도메인명', sortable: false, align: 'center', value: 'domainNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '코드그룹', sortable: false, align: 'center', value: 'codeGrp', width: '15%' },
      { text: '담당기관명', sortable: false, align: 'center', value: 'chrgOrg', width: '15%' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn', width: '15%' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd', width: '15%' },
      { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysNm', width: '15%' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn', width: '15%' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt', width: '15%' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt', width: '15%' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId', width: '15%' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt', width: '15%' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId', width: '15%' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
    // 데이터모델 수집 목록 선택 시 사용
    selectedDmCdl: null,
    // 데이터모델 수집 목록 선택 시 아이템 저장
    dmCdlListSelected: [],
    // 컬럼 탭 체크 박스
    statusListArray: ['Y', 'N'],
  }),
  methods: {
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.dmStatusItems.length / this.itemsPerPage);
    },
    tb_setListPage() {
      // 페이지네이션 버튼 개수
      this.tb_pageCount = Math.ceil(this.dmTableItems.length / this.tb_itemsPerPage);
    },
    cl_setListPage() {
      // 페이지네이션 버튼 개수
      this.cl_pageCount = Math.ceil(this.dmColumnItems.length / this.cl_itemsPerPage);
    },
    dmCdl_setListPage() {
      // 페이지네이션 버튼 개수
      this.dmCdl_pageCount = Math.ceil(this.dmCdlListItems.length / this.dmCdl_itemsPerPage);
    },
    addActiveDetail(name, index) {
      this.activeDetailTab = name;
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchModel = ''
    },
    clearSystemMessage() {
      // 검색어 지워주기
      this.searchSystem = ''
    },
    resetSearch() {
      this.searchSystem = '';
      this.searchModel = '';
    },
    clearScTableMessage() {
      // 검색어 지워주기
      this.searchTable = ''
    },
    clearScColumnMessage() {
      // 검색어 지워주기
      this.searchColumn = ''
    },
    getDataTable(clctId) {
      this.tb_loadTable = false;

      // console.log(clctId)

      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelObjListByClctId";

        axios.get(_url, {
          params: {
            'clctId': clctId
          }
        }).then((res) => {
          // console.log(res.data)

          let _data = res.data;

          this.dmTableItems = _data;
          this.tb_loadTable = false;

          // console 표시
          console.log("📃 Data Model Table LIST ↓↓↓")
          console.log(res.data)
        })
      } catch (error) {
        console.error(error);
        this.tb_loadTable = false;
      }
    },
    getDataColumn(clctId) {
      this.cl_loadTable = false;

      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelAttrListByClctId";

        axios.get(_url, {
          params: {
            'clctId': clctId
          }
        }).then((res) => {
          // console.log(res.data)

          let _data = res.data;

          // 데이터를 테이블에 맞게 수정해야 함. 순서 꼭 지킬 것.
          let _newArr = [];

          for (let i = 0; i < _data.length; i++) {
            let _obj = {};

            _obj.objNm = _data[i].objNm;
            _obj.objNmKr = _data[i].objNmKr;
            _obj.attrNm = _data[i].attrNm;
            _obj.attrNmKr = _data[i].attrNmKr;
            _obj.dataType = _data[i].dataType;
            _obj.dataLen = _data[i].dataLen;
            _obj.dataDecimalLen = _data[i].dataDecimalLen;
            _obj.nullableYn = _data[i].nullableYn;
            _obj.termsStndYn = _data[i].termsStndYn;
            _obj.domainStndYn = _data[i].domainStndYn;
            // 단어는 리스트로 새로 생성해야 함. wordLst와 wordStndLst를 순서대로 받아서 생성.
            let _wordLst = [];
            for (let n = 0; n < _data[i].wordLst.length; n++) {
              _wordLst.push(_data[i].wordLst[n] + " : " + _data[i].wordStndLst[n]);
            }

            _obj.wordLst = _wordLst;
            _obj.pkYn = _data[i].pkYn;
            _obj.fkYn = _data[i].fkYn;
            _obj.defaultVal = _data[i].defaultVal;
            _obj.clctId = _data[i].clctId;
            _obj.dataModelId = _data[i].dataModelId;

            _newArr.push(_obj);
          }



          this.dmColumnItems = _newArr;
          this.cl_loadTable = false;

          // console 표시
          console.log("📃 Data Model Column LIST ↓↓↓")
          console.log(res.data)
        })
      } catch (error) {
        console.error(error);
        this.cl_loadTable = false;
      }
    },
    showDetail(item) {
      // console.log(item);

      // 아이템명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
      // 하단 탭 영역에 선택한 데이터 모델명 보여주기
      this.detailDataModelName = item.dataModelNm;
      // 테이블 데이터 테이블 불러오기
      this.getDataTable(item.clctId);
      // 컬럼 데이터 테이블 불러오기
      this.getDataColumn(item.clctId);

    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'dmCdl') {
        this.showDmCltDateModal = true;
        // !! - 나중에 바꿔야 함
        this.dmCdl_setListPage();
      } else if (value === 'termData') {
        this.termDataModalShow = true;
      }
    },
    hideModal(value) {
      // 모달 보여주기
      if (value === 'dmCdl') {
        this.showDmCltDateModal = false;
        this.setDmCdlDate();
      } else if (value === 'termData') {
        this.termDataModalShow = false;
      }
    },
    submitDialog(value) {
      if (value === 'dmCdl') {
        let dmCdlData = this.dmCdlListSelected;

        if (dmCdlData.length === 0) {
          this.$swal.fire({
            title: '데이터 모델을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return;
        }

        this.refreshRowItem(dmCdlData);
        this.showDmCltDateModal = false;
      } else if (value === 'termData') {
        this.termDataModalShow = false;
      }
    },
    resetDetail() {
      // 선택한 코드 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.dmTableItems = [];
      this.dmColumnItems = [];
      this.detailDataModelName = '';
      this.dmCdlListSelected = [];
    },
    getSubHeader(headers) {
      let result = [];
      headers
        .filter((i) => i.children)
        .forEach((v) => {
          result = result.concat(v.children);
        });
      return result;
    },
    clTb_getSubHeader(headers) {
      let result = [];
      headers
        .filter((i) => i.children)
        .forEach((v) => {
          result = result.concat(v.children);
        });

      return result;
    },
    getRows(rows) {
      const result = {};
      _.forEach(rows, (i, key) => {
        // value가 null인경우 오류 발생. ''로 변경
        if (i === null) {
          i = '';
        }
        if (i.children) {
          _.forEach(i.children, (i1, key1) => {
            result["c" + key1] = i1;
          });
        } else {
          // 테이블 헤더에 있는 값만 가져오기
          if (key === 'dataModelNm' || key === 'dataModelSysNm' || key === 'dataModelDsNm' || key === 'objCnt' || key === 'attrCnt' ||
            key === 'clctDt' || key === 'ver') {
            result[key] = i
          } else {
            return;
          }
        };
      });
      return result;
    },
    clTb_getRows(rows) {
      const result = {};
      _.forEach(rows, (i, key) => {
        // value가 null인경우 오류 발생. ''로 변경
        if (i === null) {
          i = '';
        }
        if (i.children) {
          _.forEach(i.children, (i1, key1) => {
            result["c" + key1] = i1;
          });
        } else {
          // 테이블 헤더에 있는 값만 가져오기
          if (key === 'objNm' || key === 'objNmKr' || key === 'attrNm' || key === 'attrNmKr' || key === 'dataType' || key === 'dataLen' ||
            key === 'dataDecimalLen' || key === 'nullableYn' || /* key === 'termsStndYn' || key === 'domainStndYn' || key === 'wordLst' || */ key === 'pkYn' || key === 'fkYn' || key === 'defaultVal') {
            result[key] = i
          } else {
            return;
          }
        };
      });
      return result;
    },
    showCltList(item) {
      // console.log(item.dataModelId);
      this.showModal('dmCdl');

      this.selectedDmCdl = item.dataModelId;

      let _from = this.dmCdlDate[0].replace(/-/gi, '') + '000000';
      let _to = this.dmCdlDate[1].replace(/-/gi, '') + '595959';

      try {

        let _url = this.$APIURL.base + "api/dm/getDataModelClctList";

        axios.post(_url, {
          'schId': item.dataModelId,
          'from': _from,
          'to': _to
        }).then((res) => {
          // console.log(res.data)

          // 테이블 key를 위해 index를 추가한다.
          res.data.forEach((item, index) => {
            item.index = index;
          });

          this.dmCdlListItems = res.data;

          // console 표시
          console.log("📃 Data Model Collection LIST ↓↓↓")
          console.log(res.data);

        }).catch((err) => {
          this.$swal.fire({
            title: '데이터모델 수집 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        console.error(error);
      }
    },
    searchDataModelClctList() {
      let _from = '';
      let _to = '';

      if (this.dmCdlDate.length === 2) {
        _from = this.dmCdlDate[0].replace(/-/gi, '') + '000000';
        _to = this.dmCdlDate[1].replace(/-/gi, '') + '595959';

        if (_from > _to) {
          this.$swal.fire({
            title: '시작일자가 종료일자보다 클 수 없습니다.',
            confirmButtonText: '확인',
            icon: 'error',
          });
          return;
        }

      } else if (this.dmCdlDate.length === 1) {
        _from = this.dmCdlDate[0].replace(/-/gi, '') + '000000';
        _to = this.dmCdlDate[0].replace(/-/gi, '') + '595959';
      } else {
        this.$swal.fire({
          title: '수집기간을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }

      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelClctList";

        axios.post(_url, {
          'schId': this.selectedDmCdl,
          'from': _from,
          'to': _to
        }).then((res) => {
          // console.log(res.data)

          // 테이블 key를 위해 index를 추가한다.
          res.data.forEach((item, index) => {
            item.index = index;
          });

          this.dmCdlListItems = res.data;

          // console 표시
          console.log("📃 Data Model Collection LIST ↓↓↓")
          console.log(res.data);

        }).catch((err) => {
          this.$swal.fire({
            title: '데이터모델 수집 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        console.error(error);
      }
    },
    showTermData(item) {
      this.cl_td_loadTable = true;
      try {
        // item에서 검색에 필요한 용어명을 가져와서 용어 정보를 모달로 보여준다.
        let _url = this.$APIURL.base + "api/std/getTermsInfoByNm";

        // console.log(item.attrNmKr)

        axios.get(_url, {
          params: {
            'termsNm': item.attrNmKr,
          }
        }).then((res) => {
          // console.log(res.data)
          this.getTermDetailItem = res.data;

          // console.log(this.getTermDetailItem);

          this.showModal('termData');
          this.cl_td_loadTable = false;
        }).catch((err) => {
          this.$swal.fire({
            title: '용어 검색 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.cl_td_loadTable = false;
        })

      } catch (error) {
        console.log(error);
      }
    },
    getDataModelStatsList() {
      this.loadTable = false;

      try {

        let schNm = null;
        if (this.searchModel !== '') {
          schNm = this.searchModel
        }

        let schSysNm = null;
        if (this.searchSystem !== '') {
          schSysNm = this.searchSystem
        }

        let _url = this.$APIURL.base + "api/dm/getDataModelStatsList";

        axios.post(_url, {
          'schNm': schNm,
          'schSysNm': schSysNm
        }).then((res) => {
          console.log(res.data)

          let _data = res.data;

          // 데이터를 테이블에 맞게 수정해야 함. 순서 꼭 지킬 것.
          let _newArr = [];

          for (let i = 0; i < _data.length; i++) {
            let _obj = {};

            _obj.dataModelNm = _data[i].dataModelNm;
            _obj.dataModelSysNm = _data[i].dataModelSysNm;
            _obj.dataModelDsNm = _data[i].dataModelDsNm;
            _obj.objCnt = _data[i].dataModelStats ? _data[i].dataModelStats.objCnt : 0;
            _obj.attrCnt = _data[i].dataModelStats ? _data[i].dataModelStats.attrCnt : 0;
            _obj.clctDt = _data[i].dataModelStats ? _data[i].dataModelStats.clctDt : '';
            _obj.structDiagYn = _data[i].structDiagYn || 'N';
            _obj.structDiagDt = _data[i].structDiagDt || '';
            _obj.diagStndRate = _data[i].diagStndRate || 0;
            _obj.diagDt = _data[i].diagDt || '';
            _obj.ver = _data[i].ver;
            _obj.dataModelId = _data[i].dataModelId;
            _obj.clctId = _data[i].dataModelStats ? _data[i].dataModelStats.clctId : '';

            _newArr.push(_obj);
          }

          this.dmStatusItems = _newArr;
          this.loadTable = false;

          // console 표시
          console.log("📃 DataModel Status LIST ↓↓↓")
          console.log(_data);
          console.log("📃 Create DataModel Status LIST ↓↓↓")
          console.log(_newArr);

          // 리셋
          this.resetDetail();

        }).catch((err) => {
          this.$swal.fire({
            title: '용어 목록 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.loadTable = false;
      }
    },
    isValidDateFormat(dateString) {
      // YYYY-MM-DD 형식의 유효성을 확인하는 함수를 작성합니다.
      // 실제로 유효한 날짜인지 검증하는 추가 로직이 필요할 수 있습니다.
      const pattern = /^\d{4}-\d{2}-\d{2}$/;
      return pattern.test(dateString);
    },
    setDmCdlDate() {
      // 수집일시 최근 일주일 날짜로 설정하기
      this.dmCdlDate = [
        new Date(new Date() - 7 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10),
        new Date().toISOString().substr(0, 10)
      ];
    },
    tableDataDownload() {
      const _clctId = this.selectedItem[0].clctId;
      // console.log(_clctId)

      try {
        axios.get(this.$APIURL.base + "api/dm/downloadDataModelObjs",
          {
            params: {
              'clctId': _clctId,
            },
            responseType: 'blob',
            headers: { "Accept": "application/vnd.ms-excel" }
          }).then(response => {
            // console.log(response)
            const url = window.URL.createObjectURL(
              new Blob([response.data], { type: "application/csv" })
            );
            const link = document.createElement("a");
            link.href = url;

            let _today = this.$getToday();

            link.setAttribute("download", `데이터모델_테이블정보_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '테이블 정보 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('테이블 정보 다운로드 실패 :', error);
      }
    },
    columnDataDownload() {
      const _clctId = this.selectedItem[0].clctId;
      // console.log(_clctId)
      try {
        axios.get(this.$APIURL.base + "api/dm/downloadDataModelAttrs",
          {
            params: {
              'clctId': _clctId
            },
            responseType: 'blob',
            headers: { "Accept": "application/vnd.ms-excel" }
          }).then(response => {
            // console.log(response)
            const url = window.URL.createObjectURL(
              new Blob([response.data], { type: "application/csv" })
            );
            const link = document.createElement("a");
            link.href = url;

            let _today = this.$getToday();

            link.setAttribute("download", `데이터모델_컬럼정보_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '컬럼 정보 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('컬럼 정보 다운로드 실패 :', error);
      }
    },
    refreshRowItem(item) {
      // console.log(item)
      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelStatsByClctId";

        // console.log(item[0].clctId)

        axios.get(_url, {
          params: {
            'clctId': item[0].clctId,
          }
        }).then((res) => {
          let _data = res.data;

          // _data의 dataModelId와 dmStatusItems.dataModelId가 같은 것을 찾아서 데이터를 교체
          // 해당 배열에 있는 모든 데이터를 교체해야하며 교체한 데이터를 dmStatusItems에 다시 넣어줘야함
          for (let i = 0; i < this.dmStatusItems.length; i++) {
            if (this.dmStatusItems[i].dataModelId === _data.dataModelStats.dataModelId) {
              this.dmStatusItems[i].dataModelNm = _data.dataModelNm;
              this.dmStatusItems[i].dataModelSysNm = _data.dataModelSysNm;
              this.dmStatusItems[i].dataModelDsNm = _data.dataModelDsNm;
              this.dmStatusItems[i].objCnt = _data.dataModelStats ? _data.dataModelStats.objCnt : 0;
              this.dmStatusItems[i].attrCnt = _data.dataModelStats ? _data.dataModelStats.attrCnt : 0;
              this.dmStatusItems[i].clctDt = _data.dataModelStats ? _data.dataModelStats.clctDt : '';
              this.dmStatusItems[i].ver = _data.ver;
              this.dmStatusItems[i].dataModelId = _data.dataModelId;
              this.dmStatusItems[i].clctId = _data.dataModelStats ? _data.dataModelStats.clctId : '';
            }
          }

          // 초기화
          this.dmCdlListSelected = [];
          // 수집일시 최근 일주일 날짜로 설정하기
          this.setDmCdlDate();

        }).catch((err) => {
          this.$swal.fire({
            title: '데이터모델 컬럼 현황 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })

      } catch (error) {
        console.log(error);
      }
    },
    getDataModelObjListByObjNm() {
      this.tb_loadTable = false;

      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelObjListByObjNm";

        axios.post(_url, {
          'schId': this.selectedItem[0].clctId,
          'schObjNm': this.searchTable
        }).then((res) => {
          console.log(res.data)

          let _data = res.data;

          this.dmTableItems = _data;
        }).catch((err) => {
          this.$swal.fire({
            title: '테이블 탭 테이블 정보 검색 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.tb_loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.tb_loadTable = false;
      }
    },
    getDataModelAttrListByRetreiveCond() {
      this.cl_loadTable = false;

      // 테이블명 검색어
      let _searchTable = this.searchTable;

      if(_searchTable === null || _searchTable === undefined || _searchTable === '') {
        _searchTable = null;
      }

      // 컬럼명 검색어
      let _searchColumn = this.searchColumn;

      if(_searchColumn === null || _searchColumn === undefined || _searchColumn === '') {
        _searchColumn = null;
      }

      // 체크 박스
      let _schStndYn = this.statusListArray;

      if(_schStndYn.length === 2 || _schStndYn.length === 0) {
        _schStndYn = null;
      } else if(_schStndYn[0] === 'Y') {
        _schStndYn = 'Y';
      } else if(_schStndYn[0] === 'N') {
        _schStndYn = 'N';
      }

      
      try {
        let _url = this.$APIURL.base + "api/dm/getDataModelAttrListByRetreiveCond";

        axios.post(_url, {
          'schId': this.selectedItem[0].clctId,
          'schObjNm': _searchTable,
          'schAttrNm': _searchColumn,
          'schStndYn': _schStndYn
        }).then((res) => {
          // console.log(res.data)

          let _data = res.data;

          this.dmColumnItems = _data;

          // console 표시
          console.log("📃 DataModel Column Tab Search LIST ↓↓↓")
          console.log(_data);

          this.cl_loadTable = false;
        }).catch((err) => {
          this.$swal.fire({
            title: '컬럼 탭 컬럼 정보 검색 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
          this.cl_loadTable = false;
        })
      } catch (error) {
        console.error(error);
        this.cl_loadTable = false;
      }
    },
  },
  created() {
    this.getDataModelStatsList();
  },
  mounted() {
    // 테이블 셀 가로길이 조절
    this.$resizableGrid();
    // 수집일시 최근 일주일 날짜로 설정하기
    this.setDmCdlDate();
  },
  computed: {
    dateRangeText() {
      return this.dmCdlDate.join(' ~ ')
    },
  },
}
</script>

<style>
/* unscoped: 테이블 탭 데이터 좌측정렬 */
#dmTable_table td {
  text-align: left !important;
}
#dmTable_table td:nth-child(4) {
  text-align: center !important;
}
</style>
<style scoped>
.splitTopWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #E8EAF6;
}

.splitBottomWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
}

.splitBottomSpanWrapper {
  display: flex;
  font-size: 1.2rem;
}

.split_bottom {
  overflow: hidden;
  position: relative;
  height: 100%;
  background: #ffffff;
}

.splitBottomSpan {
  display: inline-block;
}

.split_bottom_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 0px;
  border-top: 1px solid #E8EAF6;
  background: #FAFBFF;
}

.pagination_wrap {
  position: relative;
  width: 100%;
}

.tabsStyle {
  position: relative;
  width: 100% !important;
}

.tabsStyle .v-tab {
  border-top-right-radius: 10px !important;
  border-top-left-radius: 10px !important;
  color: #455A64;
  font-weight: 600;
  transition: all 0.2s ease;
}

.tabsStyle .v-tab--active {
  color: #283593 !important;
}

.tabContentsWrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.tabContents {
  height: calc(100% - 130px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#dmStatus_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

pre {
  font-family: 'Roboto';
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}

#dmcltPicker button {
  height: 32px !important;
}

#dmTable_table,
#clTable_table {
  height: calc(100% - 76px);
  overflow-y: overlay;
  overflow-x: hidden;
}

.dmCdlList_table_wrap {
  position: absolute;
  top: 72px;
  left: 0;
  width: 100%;
  height: calc(100% - 148px);
  overflow: auto;
}

#dmCdlList_table {
  overflow-y: overlay;
  overflow-x: hidden;
}

.dmTable_table_wrap {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 48px;
  border-top: 1px solid #E8EAF6;
}

.pagination_wrapper {
  position: absolute;
  width: 100%;
  max-height: 76px;
  bottom: 0px;
  min-height: 76px;
  border-top: 1px solid #E8EAF6;
  background: #FAFBFF;
}

#term_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

.checkboxStyle {
  margin-top: 0px;
  padding-top: 0px;
  margin-right: 15px;
}
</style>