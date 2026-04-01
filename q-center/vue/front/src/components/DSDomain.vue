<template>
  <v-main>
    <Split direction="vertical" :style="{ overflow: 'hidden' }">
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <!-- 검색과 버튼 영역 -->
        <v-sheet class="splitTopWrapper pt-4 pb-4"
          v-bind:style="[isMobile ? { 'flex-direction': 'column' } : { 'flex-direction': 'row' }]">
          <!-- 검색 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-row :style="{ alignItems: 'center', margin: '0px 0px 6px 0px' }">
              <span :style="{ fontSize: '.875rem' }">도메인명</span>
              <v-text-field class="pr-4 pl-4" v-model="searchDomain" v-on:keyup.enter="getDomainData"
                @click:clear="clearMessage" clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <span :style="{ fontSize: '.875rem' }">도메인 그룹명</span>
              <v-text-field class="pr-4 pl-4" v-model="searchDomainGrpNm" v-on:keyup.enter="getDomainData"
                clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '200px' }">
              </v-text-field>
              <span :style="{ fontSize: '.875rem' }">데이터 타입</span>
              <v-text-field class="pr-4 pl-4" v-model="searchDataType" v-on:keyup.enter="getDomainData"
                clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '130px' }">
              </v-text-field>
              <span :style="{ fontSize: '.875rem' }">데이터 길이</span>
              <v-text-field class="pr-4 pl-4" v-model="searchDataLen" v-on:keyup.enter="getDomainData"
                clearable prepend-icon="" clear-icon="mdi-close-circle" type="text"
                color="ndColor" single-line dense outlined hide-details :style="{ width: '100px' }">
              </v-text-field>
              <!-- 승인 여부 추가 -->
              <v-checkbox class="domainSearchApv" v-model="searchApproval" label="승인 여부" color="ndColor"
                hide-details></v-checkbox>
              <v-btn class="gradient" title="검색" v-on:click="getDomainData"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>search</v-icon></v-btn>
              <!-- 초기화 버튼 -->
              <v-btn class="gradient" title="초기화" v-on:click="resetSearch"
                :style="{ width: '40px', padding: '0 5px', minWidth: '45px', marginRight: '16px' }"><v-icon>restart_alt</v-icon></v-btn>
            </v-row>
          </v-sheet>
          <!-- 등록 / 일괄 등록 / 삭제 버튼 -->
          <v-sheet v-bind:style="[isMobile ? { 'padding': '12px 0px' } : { 'padding': '0px 12px' }]">
            <v-btn class="gradient" v-on:click="showModal('add')" title="등록">등록</v-btn>
            <v-btn class="gradient" v-on:click="domainExcelFileUpload()" title="일괄 등록">일괄 등록</v-btn>
            <v-btn class="gradient" v-on:click="domainListDownload()" title="다운로드">다운로드</v-btn>
            <v-btn class="gradient" v-on:click="domainRemoveItem()" title="삭제">삭제</v-btn>
            <v-btn class="gradient" color="red lighten-4" v-on:click="domainBulkRemove()" title="일괄 삭제">일괄 삭제</v-btn>
            <input type="file" @change="readExcelFile" ref="file" id="inputDomainUpload" :style="{ display: 'none' }"
              accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet">
          </v-sheet>
        </v-sheet>
        <v-sheet class="tableSpt">
          <!-- 총 개수와 테이블 표시 개수 변경 영역 -->
          <v-sheet>
            <span class="ndColor--text">총 {{ domainItems.length }}건</span>
          </v-sheet>
          <v-sheet>
            <v-select :style="{ width: '90px' }" v-model.lazy="itemsPerPage" :items="tableViewLengthList" color="ndColor"
              hide-details outlined dense></v-select>
          </v-sheet>
        </v-sheet>
        <v-divider></v-divider>
        <!-- 도메인 목록 -->
        <v-data-table id="domain_table" :headers="domainHeaders" :items="domainItems" :page.sync="page"
          :items-per-page="itemsPerPage" hide-default-footer item-key="domainNm" show-select class="px-4 pb-3"
          v-model="removeItems" @input="enterSelect()" :loading="loadTable" loading-text="잠시만 기다려주세요.">
          <!-- 클릭 가능한 아이템 설정 : 도메인명  -->
          <template v-slot:[`item.domainNm`]="{ item }">
            <span class="ndColor--text" :style="{ cursor: 'pointer' }" @click="showDetail(item)">{{
              item.domainNm
            }}</span>
          </template>
          <!-- 데이터 없음 -->
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
          <div class="text-center px-4 pt-4 pb-4 pagination_wrap" v-show="pageCount > 1">
            <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
              color="ndColor" :total-visible="10"></v-pagination>
          </div>
        </v-sheet>

      </SplitArea>
      <SplitArea :size="50" :style="{ overflow: 'hidden', position: 'relative' }">
        <div class="split_bottom" v-show="selectedItem.length != 0">
          <v-sheet class="splitBottomWrapper">
            <!-- 타이틀 -->
            <v-sheet class="splitBottomSpanWrapper px-4 pt-4 pb-4 font-weight-bold">
              <span class="splitBottomSpan"
                :style="{ maxWidth: '88%', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }">'{{
                  detailDomain
                }}'</span>
              <span class="splitBottomSpan" :style="{ minWidth: '20%' }"> &nbsp;상세 보기</span>
            </v-sheet>
            <!-- 수정 / 삭제 버튼 -->
            <v-sheet class="pr-4 pl-4">
              <v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>
              <v-btn class="gradient" v-on:click="openImpactDialog()">영향도 분석</v-btn>
              <!-- <v-btn class="gradient" v-on:click="domainRemoveItem()">삭제</v-btn> -->
            </v-sheet>
          </v-sheet>
          <!-- 테이블 -->
          <v-data-table id="domain_detail_table" :items="selectedItem" hide-default-footer class="px-4 pb-3">
            <template v-slot:body="{ items }">
              <tbody>
                <!-- 상세 테이블 왼쪽  -->
                <tr v-for="header in detaileHeaders" :key="header.value">
                  <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '15%' }">
                    {{ header.text }}
                  </td>
                  <td v-for="item in items" :key="item.domainNm">
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
          </v-data-table>
        </div>
      </SplitArea>
    </Split>
    <!-- Add Domain Modal -->
    <v-dialog max-width="600" v-model="addDomainModalShow">
      <NdModal @hide="hideModal('add')" @submit="submitDialog('add')" :footer-submit="true" header-title="도메인 등록"
        footer-hide-title="취소" footer-submit-title="등록">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-form ref="form">
              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_domainNm" ref="addDomain_domainNm"
                    :rules="[() => !!addDomain_domainNm || '도메인명은 필수 입력값입니다.']" clearable required dense
                    placeholder="가격N10" color="ndColor"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 그룹명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDomain_domainGrpNm" :items="domainGroupNameItems"
                    :placeholder="'선택'" :rules="[v => !!v || '도메인 그룹명은 필수 입력값입니다.']"
                    :menu-props="{ top: false, offsetY: true }" v-on:change="resetDomainClassificationItems()"></v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 분류명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-select dense required color="ndColor" v-model="addDomain_domainClsfNm" :placeholder="'선택'"
                    :items="domainClassificationNameItems" :rules="[v => !!v || '도메인 분류명은 필수 입력값입니다.']"
                    :menu-props="{ top: false, offsetY: true }" v-on:click="getDomainClassification()">

                    <template v-slot:no-data>
                      <div class="px-4">도메인 그룹을 먼저 선택해주세요.</div>
                    </template>

                  </v-select>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">도메인 설명</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-textarea clearable dense color="ndColor" rows="1" v-model="addDomain_domainDesc"
                    ref="addDomain_domainDesc" placeholder="물건이 지니고 있는 가치를 돈으로 나타낸 것"
                    :rules="[() => !!addDomain_domainDesc || '도메인 설명은 필수 입력값입니다.']"></v-textarea>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader class="reqText">데이터 타입</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-autocomplete dense required color="ndColor" v-model="addDomain_dataType" :items="dataTypeItems"
                    :placeholder="'선택'" :rules="[v => !!v || '데이터 타입은 필수 입력값입니다.']"
                    :menu-props="{ top: false, offsetY: true }"></v-autocomplete>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>데이터 길이</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_dataLen" dense color="ndColor" placeholder="10"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>데이터 소숫점 길이</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_dataDecimalLen" dense color="ndColor" placeholder="1"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>데이터 단위</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_dataUnit" dense color="ndColor" placeholder="원"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>요청 시스템</v-subheader>
                </v-col>
                <v-col cols="8">
                  <treeselect v-model="addDomain_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                    ref="addDomain_reqSysCd" />
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>저장 형식</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_storFmt" dense color="ndColor" placeholder="9999999999"></v-text-field>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>표현 형식</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-col class="colInBtnWrap" v-for="addDomain_exprFmtLst in addDomain_exprFmtLst_arr"
                    :key="addDomain_exprFmtLst.id">
                    <v-text-field :for="addDomain_exprFmtLst.value" v-model="addDomain_exprFmtLst.value" dense
                      color="ndColor" ref="addDomain_exprFmtLst_arr" placeholder="" hide-details></v-text-field>
                    <v-btn class="gradient colInBtn" v-show="addDomain_exprFmtLst.addBtnView" v-on:click="addExprFmtLst()"
                      title="추가">추가</v-btn>
                    <v-btn class="colInBtn white--text" color="gray" v-show="addDomain_exprFmtLst.removeBtnView"
                      v-on:click="removeExprFmtLst(addDomain_exprFmtLst.id)" title="삭제">삭제</v-btn>
                  </v-col>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>허용값</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-col class="colInBtnWrap" v-for="addDomain_allowValLst in addDomain_allowValLst_arr"
                    :key="addDomain_allowValLst.id">
                    <v-text-field :for="addDomain_allowValLst.value" v-model="addDomain_allowValLst.value" dense
                      color="ndColor" ref="addDomain_allowValLst_arr" placeholder="" hide-details></v-text-field>
                    <v-btn class="gradient colInBtn" v-show="addDomain_allowValLst.addBtnView"
                      v-on:click="addAllowValLst()" title="추가">추가</v-btn>
                    <v-btn class="colInBtn white--text" color="gray" v-show="addDomain_allowValLst.removeBtnView"
                      v-on:click="removeAllowValLst(addDomain_allowValLst.id)" title="삭제">삭제</v-btn>
                  </v-col>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>공통표준여부</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-radio-group v-model="addDomain_commStndYn" row mandatory dense hide-details>
                    <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                    <v-radio color="ndColor" label="N" value="N"></v-radio>
                  </v-radio-group>
                </v-col>
              </v-row>

              <v-row>
                <v-col cols="4">
                  <v-subheader>제정차수</v-subheader>
                </v-col>
                <v-col cols="8">
                  <v-text-field v-model="addDomain_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
                </v-col>
              </v-row>

            </v-form>
          </v-container>
        </template>
      </NdModal>
    </v-dialog>
    <!-- update Domain Modal -->
    <v-dialog max-width="600" v-model="updateDomainModalShow">
      <NdModal @hide="hideModal('update')" @submit="submitDialog('update')" :footer-submit="true" header-title="도메인 수정"
        footer-hide-title="취소" footer-submit-title="수정">
        <template v-slot:body>
          <!--  -->
          <v-container fluid>
            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">도메인명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_domainNm" ref="updateDomain_domainNm"
                  :rules="[() => !!updateDomain_domainNm || '도메인명은 필수 입력값입니다.']" clearable required dense
                  placeholder="가격N10" color="ndColor"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">도메인 그룹명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="updateDomain_domainGrpNm" :items="domainGroupNameItems"
                  :placeholder="'선택'" :rules="[v => !!v || '도메인 그룹명은 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:change="resetDomainClassificationItems()"></v-select>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">도메인 분류명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-select dense required color="ndColor" v-model="updateDomain_domainClsfNm" :placeholder="'선택'"
                  :items="domainClassificationNameItems" :rules="[v => !!v || '도메인 분류명은 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }" v-on:click="getDomainClassification()">
                  <template v-slot:no-data>
                    <div class="px-4">도메인 그룹을 먼저 선택해주세요.</div>
                  </template></v-select>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">도메인 설명</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-textarea clearable dense color="ndColor" rows="1" v-model="updateDomain_domainDesc"
                  ref="updateDomain_domainDesc" placeholder="물건이 지니고 있는 가치를 돈으로 나타낸 것"
                  :rules="[() => !!updateDomain_domainDesc || '도메인 설명은 필수 입력값입니다.']"></v-textarea>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader class="reqText">데이터 타입</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-autocomplete dense required color="ndColor" v-model="updateDomain_dataType" :items="dataTypeItems"
                  :placeholder="'선택'" :rules="[v => !!v || '데이터 타입은 필수 입력값입니다.']"
                  :menu-props="{ top: false, offsetY: true }"></v-autocomplete>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>데이터 길이</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_dataLen" dense color="ndColor" placeholder="10"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>데이터 소숫점 길이</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_dataDecimalLen" dense color="ndColor" placeholder="1"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>데이터 단위</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_dataUnit" dense color="ndColor" placeholder="원"></v-text-field>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>요청 시스템</v-subheader>
              </v-col>
              <v-col cols="8">
                <treeselect v-model="updateDomain_reqSysCd" :multiple="false" :options="systemNameList" placeholder="선택"
                  ref="updateDomain_reqSysCd" />
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>저장 형식</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_storFmt" dense color="ndColor"
                  placeholder="9999999999"></v-text-field>
              </v-col>
            </v-row>

            <!-- <v-row>
              <v-col cols="4">
                <v-subheader>표현 형식</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_exprFmtLst" dense color="ndColor"
                  placeholder="9,999,999,999, 0,000,000,000"></v-text-field>
              </v-col>
            </v-row> -->

            <v-row>
              <v-col cols="4">
                <v-subheader>표현 형식</v-subheader>
              </v-col>

              <v-col cols="8">
                <v-col class="colInBtnWrap" v-for="updateDomain_exprFmt in updateDomain_exprFmtLst_arr"
                  :key="updateDomain_exprFmt.id">
                  <v-text-field :for="updateDomain_exprFmt.value" v-model="updateDomain_exprFmt.value" dense
                    color="ndColor" ref="updateDomain_exprFmtLst_arr" placeholder="" hide-details></v-text-field>
                  <v-btn class="gradient colInBtn" v-show="updateDomain_exprFmt.addBtnView" v-on:click="addExprFmtLst()"
                    title="추가">추가</v-btn>
                  <v-btn class="colInBtn white--text" color="gray" v-show="updateDomain_exprFmt.removeBtnView"
                    v-on:click="removeExprFmtLst(updateDomain_exprFmt.id)" title="삭제">삭제</v-btn>
                </v-col>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>허용값</v-subheader>
              </v-col>
              <!-- <v-col cols="8">
                <v-text-field v-model="updateDomain_allowValLst" dense color="ndColor"
                  placeholder="9,999,999,999, 0,000,000,000"></v-text-field>
              </v-col> -->
              <v-col cols="8">
                <v-col class="colInBtnWrap" v-for="updateDomain_allowValLst in updateDomain_allowValLst_arr"
                  :key="updateDomain_allowValLst.id">
                  <v-text-field :for="updateDomain_allowValLst.value" v-model="updateDomain_allowValLst.value" dense
                    color="ndColor" ref="updateDomain_allowValLst_arr" placeholder="" hide-details></v-text-field>
                  <v-btn class="gradient colInBtn" v-show="updateDomain_allowValLst.addBtnView"
                    v-on:click="addAllowValLst()" title="추가">추가</v-btn>
                  <v-btn class="colInBtn white--text" color="gray" v-show="updateDomain_allowValLst.removeBtnView"
                    v-on:click="removeAllowValLst(updateDomain_allowValLst.id)" title="삭제">삭제</v-btn>
                </v-col>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>공통표준여부</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-radio-group v-model="updateDomain_commStndYn" row mandatory dense hide-details>
                  <v-radio color="ndColor" label="Y" value="Y" checked></v-radio>
                  <v-radio color="ndColor" label="N" value="N"></v-radio>
                </v-radio-group>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="4">
                <v-subheader>제정차수</v-subheader>
              </v-col>
              <v-col cols="8">
                <v-text-field v-model="updateDomain_magntdOrd" dense color="ndColor" placeholder="1차"></v-text-field>
              </v-col>
            </v-row>

          </v-container>
        </template>
      </NdModal>
    </v-dialog>

    <!-- 일괄등록 Modal -->
    <v-dialog max-width="520" v-model="collectiveDomainModalShow" persistent>
      <v-card>
        <v-card-title class="pb-2" :style="{ fontSize: '1rem', fontWeight: 'bold' }">
          <v-icon left color="ndColor">mdi-upload</v-icon>
          도메인 일괄등록 진행
        </v-card-title>
        <v-progress-linear v-if="isUploading" indeterminate color="ndColor" height="3"></v-progress-linear>
        <v-card-text class="pt-3 pb-2">
          <div ref="uploadLogBox"
            :style="{ maxHeight: '280px', overflowY: 'auto', fontFamily: 'monospace', fontSize: '0.82rem', background: '#f8f8f8', border: '1px solid #e0e0e0', borderRadius: '4px', padding: '10px 12px' }">
            <div v-for="(log, i) in uploadLogs" :key="i"
              :style="{ color: log.level === 'ERROR' ? '#d32f2f' : log.level === 'DONE' ? '#1976d2' : '#333', fontWeight: log.level === 'DONE' ? 'bold' : 'normal', lineHeight: '1.7' }">
              <span :style="{ color: '#999', marginRight: '8px' }">{{ log.time }}</span>{{ log.msg }}
            </div>
            <div v-if="uploadLogs.length === 0" :style="{ color: '#999' }">대기 중...</div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn v-if="!isUploading" color="ndColor" text @click="collectiveDomainModalShow = false">닫기</v-btn>
          <span v-else :style="{ fontSize: '0.8rem', color: '#999', paddingRight: '12px' }">완료될 때까지 기다려주세요...</span>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <!-- 영향도 분석 다이얼로그 -->
    <v-dialog max-width="700" v-model="impactDialogShow" scrollable>
      <v-card>
        <v-card-title class="font-weight-bold">영향도 분석 - '{{ detailDomain }}'</v-card-title>
        <v-card-text>
          <v-progress-linear v-if="impactLoading" indeterminate color="indigo darken-2" />
          <template v-if="!impactLoading">
            <div class="font-weight-bold mb-2">연관 용어 ({{ impactTerms.length }}건)</div>
            <v-simple-table dense v-if="impactTerms.length > 0">
              <template v-slot:default>
                <thead>
                  <tr>
                    <th>용어명</th>
                    <th>영문약어명</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(t, i) in impactTerms" :key="'term_'+i">
                    <td>{{ t.termsNm }}</td>
                    <td>{{ t.termsEngAbrvNm }}</td>
                  </tr>
                </tbody>
              </template>
            </v-simple-table>
            <v-alert v-else type="info" dense text class="mt-1">연관 용어가 없습니다.</v-alert>
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="ndColor" text @click="impactDialogShow = false">닫기</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from "axios";
import NdModal from "./../views/modal/NdModal.vue"
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import { eventBus } from './../eventBus.js'

export default {
  name: 'DSDomain',
  components: {
    NdModal,
    Treeselect
  },
  watch: {
    addDomainModalShow(val) {
      val || this.hideModal('add')
    },
    updateDomainModalShow(val) {
      val || this.hideModal('update')
    },
    domainItems() {
      this.setListPage();
    },
    itemsPerPage(val) {
      this.setListPage();
      localStorage.setItem('dsdomain_itemsPerPage', val);
    }
  },
  props: ['isMobile'],
  data: () => ({
    // 도메인 목록 리스트
    domainItems: [],
    // 검색 도메인
    searchDomain: '',
    searchDomainGrpNm: '',
    searchDataType: '',
    searchDataLen: '',
    // 검색 승인 여부
    searchApproval: true,
    // 등록 모달 보이기
    addDomainModalShow: false,
    // 수정 모달 보이기
    updateDomainModalShow: false,
    // 일괄 등록 진행 다이얼로그
    collectiveDomainModalShow: false,
    // 일괄 등록 진행 상태
    isUploading: false,
    // 일괄 등록 로그
    uploadLogs: [],
    // 페이지네이션 시작 지정
    page: 1,
    // 총 페이지 수
    pageCount: null,
    // 한 페이지에 보여지는 도메인의 수
    itemsPerPage: parseInt(localStorage.getItem('dsdomain_itemsPerPage')) || 10,
    // 테이블 로딩
    loadTable: true,
    // 검색 이후 도메인 리스트 다시보기 버튼 보이기
    // resetBtnShow: false,
    // 선택한 도메인의 정보들
    selectedItem: [],
    // 선택한 도메인 이름
    detailDomain: null,
    // 도메인 그룹명 선택 리스트
    domainGroupNameItems: [],
    // 도메인 분류명 선택 리스트
    domainClassificationNameItems: [],
    // 데이터 타입 선택 리스트
    dataTypeItems: ['VARCHAR', 'VARCHAR2', 'CHAR', 'NUMBER', 'NUMERIC', 'DECIMAL', 'DATE', 'DATETIME', 'TIMESTAMP', 'CLOB', 'BLOB', 'TEXT', 'INTEGER', 'BIGINT', 'FLOAT', 'DOUBLE', 'TIME', 'BOOLEAN'],
    // 등록 관련
    addDomain_domainNm: null,
    addDomain_domainGrpNm: null,
    addDomain_domainClsfNm: null,
    addDomain_domainDesc: null,
    addDomain_dataType: null,
    addDomain_dataLen: null,
    addDomain_dataDecimalLen: null,
    addDomain_dataUnit: null,
    addDomain_storFmt: null,
    // addDomain_exprFmtLst: null,
    // addDomain_allowValLst: null,
    addDomain_exprFmtLst_arr: [{ id: 'exprFmt_0', value: '', addBtnView: true, removeBtnView: false }],
    addDomain_exprFmtLst_count: 0,
    addDomain_allowValLst_arr: [{ id: 'allowVal_0', value: '', addBtnView: true, removeBtnView: false }],
    addDomain_allowValLst_count: 0,
    addDomain_reqSysCd: null,
    addDomain_commStndYn: null,
    addDomain_magntdOrd: null,
    addDomain_aprvYn: "N",
    addDomain_aprvUserId: null,
    addDomain_cretDt: null,
    addDomain_cretUserId: null,
    addDomain_updtDt: null,
    addDomain_updtUserId: null,
    // 수정 관련
    updateDomain_id: null,
    updateDomain_domainNm: null,
    updateDomain_domainGrpNm: null,
    updateDomain_domainClsfNm: null,
    updateDomain_domainDesc: null,
    updateDomain_dataType: null,
    updateDomain_dataLen: null,
    updateDomain_dataDecimalLen: null,
    updateDomain_dataUnit: null,
    updateDomain_storFmt: null,
    // updateDomain_exprFmtLst: null,
    // updateDomain_allowValLst: null,
    updateDomain_exprFmtLst_arr: [{ id: 'exprFmt_0', value: '', addBtnView: true, removeBtnView: false }],
    updateDomain_exprFmtLst_count: 0,
    updateDomain_allowValLst_arr: [{ id: 'allowVal_0', value: '', addBtnView: true, removeBtnView: false }],
    updateDomain_allowValLst_count: 0,
    updateDomain_reqSysCd: null,
    updateDomain_commStndYn: null,
    updateDomain_magntdOrd: null,
    updateDomain_aprvYn: "N",
    updateDomain_aprvUserId: null,
    updateDomain_cretDt: null,
    updateDomain_cretUserId: null,
    updateDomain_updtDt: null,
    updateDomain_updtUserId: null,
    // 영향도 분석
    impactDialogShow: false,
    impactLoading: false,
    impactTerms: [],
    // 삭제 관련
    removeItems: [],
    // 상단 테이블 헤더
    domainHeaders: [
      { text: '도메인명', align: 'center', sortable: false, value: 'domainNm', width: '10%' },
      { text: '도메인 그룹명', sortable: false, align: 'center', value: 'domainGrpNm', width: '10%' },
      { text: '도메인 분류명', sortable: false, align: 'center', value: 'domainClsfNm', width: '10%' },
      { text: '도메인 설명', sortable: false, align: 'center', value: 'domainDesc' },
      { text: '데이터 타입', sortable: false, align: 'center', value: 'dataType', width: '10%' },
      { text: '데이터 길이', sortable: false, align: 'center', value: 'dataLenDisplay', width: '10%' },
    ],
    // 하단 테이블 헤더
    detaileHeaders: [
      { text: '도메인명', align: 'center', sortable: false, value: 'domainNm' },
      { text: '도메인 그룹명', sortable: false, align: 'center', value: 'domainGrpNm' },
      { text: '도메인 분류명', sortable: false, align: 'center', value: 'domainClsfNm' },
      { text: '도메인 설명', sortable: false, align: 'center', value: 'domainDesc' },
      { text: '데이터 타입', sortable: false, align: 'center', value: 'dataType', width: '15%' },
      { text: '데이터 길이', sortable: false, align: 'center', value: 'dataLenDisplay' },
      { text: '데이터 단위', sortable: false, align: 'center', value: 'dataUnit', width: '15%' },
      { text: '요청시스템', sortable: false, align: 'center', value: 'reqSysNm', width: '15%' },
      { text: '저장형식', sortable: false, align: 'center', value: 'storFmt', width: '15%' },
      { text: '표현형식', sortable: false, align: 'center', value: 'exprFmtLst' },
      { text: '허용값', sortable: false, align: 'center', value: 'allowValLst' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
    // 테이블 편의성 관련
    tableViewLengthList: [10, 20, 30, 40, 50],
    // 승인 시스템에서 사용할 시스템 네임 리스트
    systemNameList: [],
  }),
  methods: {
    resetSearch() {
      this.searchDomain = '';
      this.searchDomainGrpNm = '';
      this.searchDataType = '';
      this.searchDataLen = '';
      this.searchApproval = true;
    },
    getSystemList() {
      // 시스템 리스트 가지고 오기
      try {
        axios.get(this.$APIURL.base + 'api/sysinfo/getSysInfoList')
          .then((res) => {
            // console 표시
            console.log("📃 SYSTEM INFO LIST ↓↓↓")
            console.log(res.data);

            let _list = res.data;

            const treeData = []
            const idMap = {}

            // 노드 생성 및 idMap 구성
            _list.forEach(node => {
              idMap[node.sysCd] = { ...node, children: [] }
            })

            // children 속성 채워넣기
            Object.values(idMap).forEach(node => {
              if (node.parentSysCd) {
                idMap[node.parentSysCd].children.push(node)
              } else {
                treeData.push(node)
              }
            })

            function removeEmptyChildren(node) {
              // 노드 및 하위 노드에 대한 속성 채우기
              node.id = node.sysCd
              node.label = node.sysNm

              // 자식 요소가 없으면 지우기
              if (node.children.length === 0) {
                delete node.children;
              } else {
                node.children.forEach(childNode => removeEmptyChildren(childNode));
              }
            }

            treeData.forEach(node => removeEmptyChildren(node));

            this.systemNameList = treeData;

          })
          .catch((err) => {
            console.log(err);
          })
      } catch (error) {
        console.log(err);
      }
    },
    showDetail(item) {
      // 도메인명 클릭 시 보여지는 하단 리스트
      this.selectedItem = [item];
      // 선택한 도메인 이름을 타이틀에 보이기 위해 추가함
      this.detailDomain = item.domainNm;
      // remove item에 단독으로 넣어주기
      this.removeItems = [item];
    },
    openImpactDialog() {
      if (!this.selectedItem || this.selectedItem.length === 0) return;
      const domainNm = this.selectedItem[0].domainNm;
      this.impactDialogShow = true;
      this.impactLoading = true;
      this.impactTerms = [];
      axios.get(this.$APIURL.base + 'api/std/impact/domain', { params: { domainNm: domainNm } })
        .then(res => {
          this.impactTerms = res.data.terms || [];
        })
        .catch(err => {
          console.error('영향도 분석 조회 실패', err);
        })
        .finally(() => {
          this.impactLoading = false;
        });
    },
    resetDetail() {
      // 선택한 도메인 정보를 리셋
      this.selectedItem = [];
      this.removeItems = [];
      this.detailDomain = null;
    },
    clearMessage() {
      // 검색어 지워주기
      this.searchDomain = ''
    },
    setListPage() {
      // 페이지네이션 버튼 개수
      this.pageCount = Math.ceil(this.domainItems.length / this.itemsPerPage);
    },
    enterSelect() {
      // 도메인명 클릭 시 하단에 보여지는 상세 보기를 체크 해제 시 초기화 해주기
      if (this.removeItems.length === 0) {
        this.selectedItem = [];
      }
    },
    getDomainData() {
      this.loadTable = true;
      // 도메인 리스트 불러오기
      try {

        let schNm = null;

        if (this.searchDomain !== '') {
          schNm = this.searchDomain
        }
        let schAprvYn = ''
        if (this.searchApproval === true) {
          schAprvYn = 'Y'
        } else {
          schAprvYn = 'N'
        }

        let _url = this.$APIURL.base + "api/std/getDomainList";


        axios.post(_url, {
          'schNm': schNm,
          'schAprvYn': schAprvYn,
          'schDomainGrpNm': this.searchDomainGrpNm !== '' ? this.searchDomainGrpNm : null,
          'schDataType': this.searchDataType !== '' ? this.searchDataType : null,
          'schDataLen': this.searchDataLen !== '' ? this.searchDataLen : null
        }).then(result => {
          let _data = result.data;
          // console.log(_data);

          let _new_arr = [];

          for (let i = 0; i < _data.length; i++) {
            let _new_obj = {
              'id': _data[i].id,
              'domainNm': _data[i].domainNm,
              'domainGrpNm': _data[i].domainGrpNm,
              'domainClsfNm': _data[i].domainClsfNm,
              'domainDesc': _data[i].domainDesc,
              'dataType': _data[i].dataType,
              'dataLen': _data[i].dataLen,
              'dataDecimalLen': _data[i].dataDecimalLen,
              'dataLenDisplay': _data[i].dataDecimalLen > 0 ? _data[i].dataLen + ',' + _data[i].dataDecimalLen : (_data[i].dataLen != null ? String(_data[i].dataLen) : ''),
              'dataUnit': _data[i].dataUnit,
              'storFmt': _data[i].storFmt,
              'reqSysCd': _data[i].reqSysCd,
              'reqSysNm': _data[i].reqSysNm,
              // 'exprFmtLst': _data[i].exprFmtLst,
              // 'allowValLst': _data[i].allowValLst,
              'commStndYn': _data[i].commStndYn,
              'magntdOrd': _data[i].magntdOrd,
              'aprvYn': _data[i].aprvYn,
              'aprvUserId': _data[i].aprvUserId,
              'cretDt': _data[i].cretDt,
              'cretUserId': _data[i].cretUserId,
              'updtDt': _data[i].updtDt,
              'updtUserId': _data[i].updtUserId,
            }

            // 표현형식 목록의 array를 풀어서 단순 문자열로 변환
            if (_data[i].exprFmtLst !== null && _data[i].exprFmtLst.length !== 0) {
              let _arr = new Array();

              for (let n = 0; n < _data[i].exprFmtLst.length; n++) {
                _arr.push(_data[i].exprFmtLst[n])
              }

              _new_obj.exprFmtLst = _arr;
            }

            // 허용값 목록의 array를 풀어서 단순 문자열로 변환
            if (_data[i].allowValLst !== null && _data[i].allowValLst.length !== 0) {
              let _arr = new Array();

              for (let n = 0; n < _data[i].allowValLst.length; n++) {
                _arr.push(_data[i].allowValLst[n])
              }

              _new_obj.allowValLst = _arr;
            }

            _new_arr.push(_new_obj);
          }
          // 테이블 생성하는 목록 Data에 전달
          this.domainItems = _new_arr;

          this.loadTable = false;

          // console 표시
          console.log("📃 DOMAIN LIST ↓↓↓")
          console.log(_new_arr);

        }).catch(error => {
          this.$swal.fire({
            title: '도메인 목록 바인드 실패 - API 확인 필요',
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
    getDomainGroupName() {
      // 도메인 그룹명을 도메인 등록, 도메인 수정에 바인드
      try {
        axios.get(this.$APIURL.base + "api/std/getDomainGroupList").then(result => {
          let _data = result.data;

          let _new_arr = [];

          for (let i = 0; i < _data.length; i++) {
            _new_arr.push(_data[i].domainGrpNm)
          }

          this.domainGroupNameItems = _new_arr;

        }).catch(error => {
          this.$swal.fire({
            title: '도메인 그룹명 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }
    },
    resetDomainClassificationItems() {
      this.domainClassificationNameItems = [];
    },
    getDomainClassification() {
      // 도메인 분류명을 도메인 등록, 도메인 수정에 바인드
      let _domainGroupName = null;

      if (this.addDomainModalShow) {
        _domainGroupName = this.addDomain_domainGrpNm

        if (_domainGroupName === null) {
          this.$swal.fire({
            title: '먼저 도메인 그룹명을 선택해주세요.',
            confirmButtonText: '확인',
            icon: 'error',
          });

          return;
        }
      } else if (this.updateDomainModalShow) {
        _domainGroupName = this.updateDomain_domainGrpNm
      }

      try {
        axios.get(this.$APIURL.base + "api/std/getDomainClassificationListByDomainGrpNm", {
          params: {
            'domainGrpNm': _domainGroupName
          }
        }).then(result => {

          let _data = result.data;
          console.log(_data);

          if (result.status === 200) {
            let _new_arr = [];

            for (let i = 0; i < _data.length; i++) {
              _new_arr.push(_data[i].domainClsfNm)
            }


            this.domainClassificationNameItems = _new_arr;

            if (this.updateDomainModalShow) {
              this.updateDomain_domainClsfNm = this.selectedItem[0].domainClsfNm;
            }
          }

        }).catch(error => {
          this.$swal.fire({
            title: '도메인 분류명 바인드 실패 - API 확인 필요',
            confirmButtonText: '확인',
            icon: 'error',
          });
        })
      } catch (error) {
        console.error(error);
      }
    },
    showModal(value) {
      // 모달 보여주기
      if (value === 'add') {
        this.addDomainModalShow = true;
        // 도메인 그룹명 가지고 와서 바인드
        // 도메인 분류명은 도메인 그룹명 선택시 바인드
        this.getDomainGroupName();
        this.addModalOpenSetDomainNm();
      } else if (value === 'update') {
        // 선택한 도메인 데이터 바인드
        this.updateModalInit();
        // 도메인 그룹명 가지고 와서 바인드
        this.getDomainGroupName();
        // 모달 오픈
        this.updateDomainModalShow = true;
        // 도메인 분류명 가지고 와서 바인드
        this.getDomainClassification();
      }
    },
    hideModal(value) {
      if (value === 'add') {
        this.addDomainModalShow = false;
        this.domainClassificationNameItems = [];
        this.$refs.form.reset();
        this.resetAddDomainTextfield();
      } else if (value === 'update') {
        this.updateDomainModalShow = false;
        this.domainClassificationNameItems = [];
        this.resetUpdateDomainTextfield();
      }
    },
    addModalOpenSetDomainNm() {
      // 모달 오픈 시 검색어에 문자열이 있을 경우 도메인명에 자동으로 입력
      if (this.searchDomain !== '') {
        this.addDomain_domainNm = this.searchDomain;
      }
    },
    submitDialog(value) {
      if (value === 'add') {
        if (this.fieldcheck('add')) {
          this.createDomain();
        }

      } else if (value === 'update') {
        if (this.fieldcheck('update')) {
          this.updateDomain();
        }
      }
    },
    fieldcheck(status) {
      let _attr = null;

      if (status === 'add') {
        if (this.addDomain_domainNm === null) {
          _attr = '도메인명은';
          this.$refs.addDomain_domainNm.focus()
        } else if (this.addDomain_domainGrpNm === null) {
          _attr = '도메인 그룹명은';
          this.$refs.addDomain_domainGrpNm.focus()
        } else if (this.addDomain_domainClsfNm === null) {
          _attr = '도메인 분류명은'
          this.$refs.addDomain_domainClsfNm.focus()
        } else if (this.addDomain_domainDesc === null) {
          _attr = '도메인 설명은'
          this.$refs.addDomain_domainDesc.focus()
        } else if (this.addDomain_dataType === null) {
          _attr = '데이터 타입은'
          this.$refs.addDomain_dataType.focus()
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });

          return false;
        }
      } else if (status === 'update') {
        if (this.updateDomain_domainNm === null) {
          _attr = '도메인명은';
          this.$refs.updateDomain_domainNm.focus()
        } else if (this.updateDomain_domainGrpNm === null) {
          _attr = '도메인 그룹명은';
          this.$refs.updateDomain_domainGrpNm.focus()
        } else if (this.updateDomain_domainClsfNm === null) {
          _attr = '도메인 분류명은'
          this.$refs.updateDomain_domainClsfNm.focus()
        } else if (this.updateDomain_domainDesc === null) {
          _attr = '도메인 설명은'
          this.$refs.updateDomain_domainDesc.focus()
        } else if (this.updateDomain_dataType === null) {
          _attr = '데이터 타입은'
          this.$refs.updateDomain_dataType.focus()
        }

        if (_attr !== null) {
          this.$swal.fire({
            title: `${_attr} 필수 입력값입니다.`,
            confirmButtonText: '확인',
            confirmButtonColor: '#3F51B5',
            icon: 'error',
          });

          return false;
        }
      }
      return true;
    },
    createDomain() {
      // 표현형식 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
      let arr_exprFmtLst = this.addDomain_exprFmtLst_arr.map(obj => obj.value).filter(val => val !== '');
      // 허용값 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
      let arr_allowValLst = this.addDomain_allowValLst_arr.map(obj => obj.value).filter(val => val !== '');

      try {
        let domainData = {
          'domainNm': this.addDomain_domainNm,
          'domainGrpNm': this.addDomain_domainGrpNm,
          'domainClsfNm': this.addDomain_domainClsfNm,
          'domainDesc': this.addDomain_domainDesc,
          'dataType': this.addDomain_dataType,
          'dataLen': this.addDomain_dataLen,
          'dataDecimalLen': this.addDomain_dataDecimalLen,
          'dataUnit': this.addDomain_dataUnit,
          'storFmt': this.addDomain_storFmt,
          "exprFmtLst": arr_exprFmtLst,
          "allowValLst": arr_allowValLst,
          'commStndYn': this.addDomain_commStndYn,
          'magntdOrd': this.addDomain_magntdOrd,
          'reqSysCd': this.addDomain_reqSysCd,
        };

        axios.post(this.$APIURL.base + "api/std/createDomain", domainData)
          .then(res => {
            // console.log(res)

            if (res.data.resultCode === 200) {

              this.$swal.fire({
                title: '새로운 도메인이 등록되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              })

              this.getDomainData();

              this.hideModal('add');
            } else {
              this.$swal.fire({
                title: '도메인 등록 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }

          }).catch(error => {
            this.$swal.fire({
              title: '도메인 등록 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })

      } catch (error) {
        this.$swal.fire({
          title: '도메인 등록 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    updateDomain() {
      // 표현형식 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
      let arr_exprFmtLst = this.updateDomain_exprFmtLst_arr.map(obj => obj.value).filter(val => val !== '');
      // 허용값 배열을 가지고 온 다음 빈 값을 제외한 value로 새로운 배열을 생성한다.
      let arr_allowValLst = this.updateDomain_allowValLst_arr.map(obj => obj.value).filter(val => val !== '');

      try {
        let domainData = {
          'id': this.updateDomain_id,
          'domainNm': this.updateDomain_domainNm,
          'domainGrpNm': this.updateDomain_domainGrpNm,
          'domainClsfNm': this.updateDomain_domainClsfNm,
          'domainDesc': this.updateDomain_domainDesc,
          'dataType': this.updateDomain_dataType,
          'dataLen': this.updateDomain_dataLen,
          'dataDecimalLen': this.updateDomain_dataDecimalLen,
          'dataUnit': this.updateDomain_dataUnit,
          'storFmt': this.updateDomain_storFmt,
          "exprFmtLst": arr_exprFmtLst,
          "allowValLst": arr_allowValLst,
          'commStndYn': this.updateDomain_commStndYn,
          'magntdOrd': this.updateDomain_magntdOrd,
          'reqSysCd': this.updateDomain_reqSysCd,
        };

        axios.post(this.$APIURL.base + "api/std/updateDomain", domainData)
          .then(res => {
            console.log(res)

            if (res.data.resultCode === 200) {
              this.hideModal('update');

              this.$swal.fire({
                title: '도메인이 수정되었습니다.',
                icon: 'success',
                showConfirmButton: false,
                timer: 1500
              });
              this.resetDomainList();
            } else {
              this.$swal.fire({
                title: '도메인 수정 실패',
                text: res.data.resultMessage,
                confirmButtonText: '확인',
                icon: 'error',
              });
            }
          }).catch(error => {
            this.$swal.fire({
              title: '도메인 수정 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        this.$swal.fire({
          title: '도메인 수정 실패 - params 확인 필요',
          confirmButtonText: '확인',
          icon: 'error',
        });
      }
    },
    resetDomainList() {
      // 도메인 목록 다시 불러오기
      this.getDomainData();
      this.searchDomain = '';
      // this.resetBtnShow = false;
      this.resetDetail();
    },
    updateModalInit() {
      let exprFmtLst_data = [];
      let allowValLst_data = [];

      if (this.selectedItem[0].exprFmtLst !== undefined) {
        // for문으로 돌려서 value값만 넣어준다.
        for (let i = 0; i < this.selectedItem[0].exprFmtLst.length; i++) {
          // 마지막 배열은 add버튼을 보여주고 나머지는 remove버튼을 보여준다.
          if (i === this.selectedItem[0].exprFmtLst.length - 1) {
            exprFmtLst_data.push({ id: 'exprFmt_' + i, value: this.selectedItem[0].exprFmtLst[i].trim(), addBtnView: true, removeBtnView: false });
          } else {
            exprFmtLst_data.push({ id: 'exprFmt_' + i, value: this.selectedItem[0].exprFmtLst[i].trim(), addBtnView: false, removeBtnView: true });
          }
        }
        this.updateDomain_exprFmtLst_count = this.selectedItem[0].exprFmtLst.length - 1;
      } else {
        exprFmtLst_data = [{ id: 'exprFmt_0', value: '', addBtnView: true, removeBtnView: false }];
        this.updateDomain_exprFmtLst_count = 0;
      }

      if (this.selectedItem[0].allowValLst !== undefined) {
        for (let i = 0; i < this.selectedItem[0].allowValLst.length; i++) {
          // 마지막 배열은 add버튼을 보여주고 나머지는 remove버튼을 보여준다.
          if (i === this.selectedItem[0].allowValLst.length - 1) {
            allowValLst_data.push({ id: 'allowVal_' + i, value: this.selectedItem[0].allowValLst[i].trim(), addBtnView: true, removeBtnView: false });
          } else {
            allowValLst_data.push({ id: 'allowVal_' + i, value: this.selectedItem[0].allowValLst[i].trim(), addBtnView: false, removeBtnView: true });
          }
        }
        this.updateDomain_allowValLst_count = this.selectedItem[0].allowValLst.length - 1;
      } else {
        allowValLst_data = [{ id: 'allowVal_0', value: '', addBtnView: true, removeBtnView: false }];
        this.updateDomain_allowValLst_count = 0;
      }

      this.updateDomain_id = this.selectedItem[0].id;
      this.updateDomain_domainNm = this.selectedItem[0].domainNm;
      this.updateDomain_domainGrpNm = this.selectedItem[0].domainGrpNm;
      this.updateDomain_domainClsfNm = this.selectedItem[0].domainClsfNm;
      this.updateDomain_domainDesc = this.selectedItem[0].domainDesc;
      this.updateDomain_dataType = this.selectedItem[0].dataType;
      this.updateDomain_dataLen = this.selectedItem[0].dataLen;
      this.updateDomain_dataDecimalLen = this.selectedItem[0].dataDecimalLen;
      this.updateDomain_dataUnit = this.selectedItem[0].dataUnit;
      this.updateDomain_storFmt = this.selectedItem[0].storFmt;
      this.updateDomain_exprFmtLst_arr = exprFmtLst_data;
      this.updateDomain_allowValLst_arr = allowValLst_data;
      this.updateDomain_commStndYn = this.selectedItem[0].commStndYn;
      this.updateDomain_magntdOrd = this.selectedItem[0].magntdOrd;
      this.updateDomain_aprvYn = this.selectedItem[0].aprvYn;
      this.updateDomain_aprvUserId = this.selectedItem[0].aprvUserId;
      this.updateDomain_cretDt = this.selectedItem[0].cretDt;
      this.updateDomain_cretUserId = this.selectedItem[0].cretUserId;
      this.updateDomain_updtDt = this.selectedItem[0].updtDt;
      this.updateDomain_updtUserId = this.selectedItem[0].updtUserId;
      this.updateDomain_reqSysCd = this.selectedItem[0].reqSysCd;
    },
    domainRemoveItem() {
      if (this.removeItems.length === 0) {
        this.$swal.fire({
          title: '삭제할 도메인을 선택해주세요.',
          confirmButtonText: '확인',
          icon: 'error',
        });
        return;
      };

      let removeName = '';

      for (let i = 0; i < this.removeItems.length; i++) {
        if (i === 0) {
          removeName += this.removeItems[i].domainNm;
        } else {
          removeName += ', ' + this.removeItems[i].domainNm;
        }
      }

      this.$swal.fire({
        title: '정말로 도메인을 삭제할까요?',
        icon: 'warning',
        showCancelButton: true,
        text: removeName,
        confirmButtonColor: '#3678a7',
        cancelButtonColor: '#909090',
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {

          let removeItemArr = [];

          for (let i = 0; i < this.removeItems.length; i++) {
            let removeObj = {
              id: this.removeItems[i].id
            }
            removeItemArr.push(removeObj)
          }

          try {
            axios.post(this.$APIURL.base + "api/std/deleteDomains", removeItemArr)
              .then(res => {
                console.log(res)

                if (res.data.resultCode === 200) {

                  this.$swal.fire({
                    title: '도메인이 삭제되었습니다.',
                    icon: 'success',
                    showConfirmButton: false,
                    timer: 1500
                  });

                  this.getDomainData();
                  this.resetDetail();
                } else {
                  this.$swal.fire({
                    title: '도메인 삭제 실패',
                    text: res.data.resultMessage,
                    confirmButtonText: '확인',
                    icon: 'error',
                  });
                }

              }).catch(error => {
                this.$swal.fire({
                  title: '도메인 삭제 실패 - API 확인 필요',
                  confirmButtonText: '확인',
                  icon: 'error',
                });
              });
          } catch (error) {
            this.$swal.fire({
              title: '도메인 삭제 실패 - params 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          }
        }
      })
    },
    domainBulkRemove() {
      if (this.domainItems.length === 0) {
        this.$swal.fire({ title: '삭제할 도메인이 없습니다.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.$swal.fire({
        title: `조회된 도메인 ${this.domainItems.length}건을 모두 삭제할까요?`,
        text: '이 작업은 되돌릴 수 없습니다.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#d32f2f',
        cancelButtonColor: '#909090',
        confirmButtonText: '일괄 삭제',
        cancelButtonText: '취소',
      }).then((result) => {
        if (result.isConfirmed) {
          const removeItemArr = this.domainItems.map(item => ({ id: item.id }));
          axios.post(this.$APIURL.base + 'api/std/deleteDomains', removeItemArr)
            .then(res => {
              if (res.data.resultCode === 200) {
                this.$swal.fire({ title: `도메인 ${removeItemArr.length}건이 삭제되었습니다.`, icon: 'success', showConfirmButton: false, timer: 1500 });
                this.getDomainData();
                this.resetDetail();
              } else {
                this.$swal.fire({ title: '도메인 일괄 삭제 실패', text: res.data.resultMessage, confirmButtonText: '확인', icon: 'error' });
              }
            }).catch(() => {
              this.$swal.fire({ title: '도메인 일괄 삭제 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
            });
        }
      });
    },
    domainExcelFileUpload() {
      // 일괄 등록 버튼 클릭
      let fileUpload = document.getElementById('inputDomainUpload')
      if (fileUpload != null) {
        fileUpload.click()
      }
    },
    readExcelFile(event) {
      // 도메인 일괄 등록에서 사용하는 function
      const file = event.target.files[0];

      // 취소일 때 return
      if (file === undefined) {
        return;
      }

      this.excelFile = this.$refs.file.files[0];

      // 진행 다이얼로그 열기
      this.uploadLogs = [];
      this.isUploading = true;
      this.collectiveDomainModalShow = true;

      if (this._uploadTimer) clearTimeout(this._uploadTimer);
      this._uploadTimer = setTimeout(() => {
        if (this.isUploading) {
          this._addUploadLog('ERROR', 'WebSocket 응답 없음 - 결과를 직접 확인해주세요.');
          this.isUploading = false;
          this.getDomainData();
        }
      }, 60000);

      const _url = this.$APIURL.base + "api/std/uploadDomains";
      const formData = new FormData();
      formData.append('file', this.excelFile);
      const headers = { 'Content-Type': 'multipart/form-data' };

      axios.post(_url, formData, { headers }).catch(() => {
        this._addUploadLog('ERROR', '서버 연결 오류 - API 확인 필요');
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
      });

      // input 초기화
      document.getElementById('inputDomainUpload').value = '';
    },
    onUploadNotice(msg) {
      if (!this.collectiveDomainModalShow) return;
      if (!msg.data || !msg.data.startsWith('[도메인]')) return;
      const level = msg.noticeType === 'ERROR' ? 'ERROR' : 'INFO';
      this._addUploadLog(level, msg.data);
      if (msg.data.includes('완료 -')) {
        this.isUploading = false;
        clearTimeout(this._uploadTimer);
        this.getDomainData();
        const summary = msg.data.replace('[도메인] ', '');
        const failMatch = summary.match(/실패:\s*(\d+)건/);
        const failCount = failMatch ? parseInt(failMatch[1]) : 0;
        this.$swal.fire({
          title: '도메인 일괄등록 완료',
          text: summary,
          icon: failCount > 0 ? 'warning' : 'success',
          showConfirmButton: false,
          timer: 3000
        });
      }
    },
    _addUploadLog(level, msg) {
      const now = new Date();
      const time = now.toTimeString().slice(0, 8);
      this.uploadLogs.push({ level, msg, time });
      this.$nextTick(() => {
        const box = this.$refs.uploadLogBox;
        if (box) box.scrollTop = box.scrollHeight;
      });
    },

    domainListDownload() {
      let _keyWord = this.searchDomain.length !== 0 ? this.searchDomain : null;

      try {
        axios.get(this.$APIURL.base + "api/std/downloadDomains",
          {
            params: { 'searchKey': _keyWord },
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

            link.setAttribute("download", `도메인_${_today}.xlsx`);

            document.body.appendChild(link);
            link.click();
            window.URL.revokeObjectURL(url);
            link.remove();
          }).catch(error => {
            this.$swal.fire({
              title: '도메인 다운로드 실패 - API 확인 필요',
              confirmButtonText: '확인',
              icon: 'error',
            });
          })
      } catch (error) {
        console.log('도메인 다운로드 실패 :', error);
      }
    },
    addExprFmtLst() {
      if (this.addDomainModalShow) {
        // 도메인 등록 - 표현형식목록 
        let _dataLength = this.addDomain_exprFmtLst_arr.length;

        // 표현형식 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.addDomain_exprFmtLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '표현형식을 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.addDomain_exprFmtLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        // 표현형식 목록 배열 생성
        this.addDomain_exprFmtLst_arr.push({
          id: `exprFmt_${++this.addDomain_exprFmtLst_count}`,
          value: ''
        })

        // 표현형식 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.addDomain_exprFmtLst_arr.forEach((item, index) => {
          if (index === this.addDomain_exprFmtLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      } else if (this.updateDomainModalShow) {
        // 도메인 수정 - 표현형식목록
        let _dataLength = this.updateDomain_exprFmtLst_arr.length;

        // 표현형식 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.updateDomain_exprFmtLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '표현형식을 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.updateDomain_exprFmtLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        this.updateDomain_exprFmtLst_arr.push({
          id: `exprFmt_${++this.updateDomain_exprFmtLst_count}`,
          value: ''
        })

        this.updateDomain_exprFmtLst_arr.forEach((item, index) => {
          if (index === this.updateDomain_exprFmtLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      }
    },
    removeExprFmtLst(id) {
      if (this.addDomainModalShow) {
        this.addDomain_exprFmtLst_arr = this.addDomain_exprFmtLst_arr.filter(item => item.id !== id);
      } else if (this.updateDomainModalShow) {
        this.updateDomain_exprFmtLst_arr = this.updateDomain_exprFmtLst_arr.filter(item => item.id !== id);
      }
    },
    addAllowValLst() {
      if (this.addDomainModalShow) {
        // 도메인 등록 - 허용값 목록 
        let _dataLength = this.addDomain_allowValLst_arr.length;

        // 허용값 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.addDomain_allowValLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '허용값을 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.addDomain_allowValLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        // 허용값 목록 배열 생성
        this.addDomain_allowValLst_arr.push({
          id: `allowVal_${++this.addDomain_allowValLst_count}`,
          value: ''
        })

        // 허용값 목록 배열의 마지막 데이터의 addBtnView, removeBtnView를 true, false로 변경
        this.addDomain_allowValLst_arr.forEach((item, index) => {
          if (index === this.addDomain_allowValLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      } else if (this.updateDomainModalShow) {
        // 도메인 수정 - 허용값 목록
        let _dataLength = this.updateDomain_allowValLst_arr.length;

        // 허용값 입력하지 않고 추가 버튼 눌렀을 때 경고창(추가 버튼 무한 클릭 방지)
        if (_dataLength > 0) {
          let _lastData = this.updateDomain_allowValLst_arr[_dataLength - 1];
          if (_lastData.value === '') {

            this.$swal.fire({
              title: '허용값을 입력해주세요',
              showConfirmButton: false,
              timer: 1500,
              icon: 'error',
            })

            this.$refs.updateDomain_allowValLst_arr[_dataLength - 1].focus();
            return;
          }
        }

        this.updateDomain_allowValLst_arr.push({
          id: `allowVal_${++this.updateDomain_allowValLst_count}`,
          value: ''
        })

        this.updateDomain_allowValLst_arr.forEach((item, index) => {
          if (index === this.updateDomain_allowValLst_arr.length - 1) {
            item.addBtnView = true;
            item.removeBtnView = false;
          } else {
            item.addBtnView = false;
            item.removeBtnView = true;
          }
        })
      }
    },
    removeAllowValLst(id) {
      if (this.addDomainModalShow) {
        this.addDomain_allowValLst_arr = this.addDomain_allowValLst_arr.filter(item => item.id !== id);
      } else if (this.updateDomainModalShow) {
        this.updateDomain_allowValLst_arr = this.updateDomain_allowValLst_arr.filter(item => item.id !== id);
      }
    },
    resetAddDomainTextfield() {
      this.addDomain_exprFmtLst_arr = [{ id: 'exprFmt_0', value: '', addBtnView: true, removeBtnView: false }];
      this.addDomain_exprFmtLst_count = 0;
      this.addDomain_allowValLst_arr = [{ id: 'allowVal_0', value: '', addBtnView: true, removeBtnView: false }];
      this.addDomain_allowValLst_count = 0;
      this.addDomain_reqSysCd = null;

    },
    resetUpdateDomainTextfield() {
      this.updatDomain_exprFmtLst_arr = [{ id: 'exprFmt_0', value: '', addBtnView: true, removeBtnView: false }];
      this.updatDomain_exprFmtLst_count = 0;
      this.updatDomain_allowValLst_arr = [{ id: 'allowVal_0', value: '', addBtnView: true, removeBtnView: false }];
      this.updatDomain_allowValLst_count = 0;
      this.updateDomain_reqSysCd = null;
    },
  },
  created() {
    this.getDomainData();
    this.getSystemList();
    eventBus.$on('NOTICE', this.onUploadNotice);
  },
  beforeDestroy() {
    eventBus.$off('NOTICE', this.onUploadNotice);
  },
  mounted() {
    // 테이블 셀 가로길이 조절
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

.splitBottomWrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
}

#domain_table {
  height: calc(100% - 210px);
  overflow-y: overlay;
  overflow-x: hidden;
}

.split_bottom {
  overflow: hidden;
  position: relative;
  height: 100%;
  background: #ffffff;
}

#domain_detail_table {
  height: calc(100% - 64px);
  overflow-y: overlay;
  overflow-x: hidden;
}

#domain_detail_table tbody tr:nth-child(1) td {
  border-top: thin solid rgba(0, 0, 0, 0.08);
}

#domain_table thead th:nth-child(1) {
  width: 58px !important;
  min-width: 58px !important;
  max-width: 58px !important;
}

.v-data-table__wrapper {
  max-height: calc(100% - 73.59px);
  overflow-y: auto !important;
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

.select_add_title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: nowrap;
  flex-direction: row;
}

.select_add_title > .material-icons {
  cursor: pointer;
}

.splitBottomSpanWrapper {
  width: 60%;
  display: flex;
  font-size: 1.2rem;
}

.splitBottomSpan {
  display: inline-block;
}

.domainSearchApv {
  margin-top: 0px !important;
  padding-top: 0px !important;
  margin: 0 30px 0 0;
}

.colInBtnWrap {
  display: flex;
  align-items: center !important;
}

.colInBtn {
  width: 50px;
  margin-left: 20px;
  height: 30px !important;
}

.tableSpt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #FAFBFF;
}
</style>