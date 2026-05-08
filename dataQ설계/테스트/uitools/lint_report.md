# Vue UI 정적 분석 리포트

- 스캔 파일 수: **57**
- 총 v-btn: **122** / v-icon: **98**
- 이슈 파일: **20**

## 카테고리별 합계

- 6. icon+text 인데 left/right 누락: **23** 건
- 3. v-btn 사이즈 혼재 파일: **4** 건
- 4. v-icon 사이즈 혼재 파일: **5** 건
- 1. 반응 없는 버튼: **9** 건

## 파일별 상세

### `q-center\vue\front\src\components\DSBoard.vue` (v-btn 2 / v-icon 3)

**icon+text 정렬 누락** (2건)
  - L10: ``
  - L14: `small class="mr-1"`


### `q-center\vue\front\src\components\DSCode.vue` (v-btn 7 / v-icon 2)

**icon+text 정렬 누락** (2건)
  - L29: ``
  - L32: ``


### `q-center\vue\front\src\components\DSDatamodelCollection.vue` (v-btn 5 / v-icon 3)

**icon+text 정렬 누락** (2건)
  - L21: ``
  - L24: ``


### `q-center\vue\front\src\components\DSDatamodelHistory.vue` (v-btn 2 / v-icon 1)

**icon+text 정렬 누락** (1건)
  - L24: ``


### `q-center\vue\front\src\components\DSDatamodelStatus.vue` (v-btn 2 / v-icon 2)

**icon+text 정렬 누락** (2건)
  - L18: ``
  - L21: ``


### `q-center\vue\front\src\components\DSDatamodelStatusColumn.vue` (v-btn 9 / v-icon 0)

**반응 없는 버튼** (2건)
  - L45: "= 100"
          v-on:click="addEmptyRow">+ 컬럼 추가" — `class="btn-action" id="btn-add-col-row" color="primary" :disabled="!selectedMode`
  - L47: "= 100"
          v-on:click="addEmptyRows(10)">+ 빈 행 10개" — `class="btn-action" id="btn-add-col-rows-10" color="primary" outlined :disabled="`


### `q-center\vue\front\src\components\DSDomain.vue` (v-btn 7 / v-icon 2)

**icon+text 정렬 누락** (2건)
  - L34: ``
  - L37: ``


### `q-center\vue\front\src\components\DSDomainClassification.vue` (v-btn 8 / v-icon 3)

**icon+text 정렬 누락** (2건)
  - L17: ``
  - L20: ``


### `q-center\vue\front\src\components\DSErwinImport.vue` (v-btn 3 / v-icon 6)

**v-icon 사이즈 혼재** (1건)
  - {'summary': {'x-small': 1, 'small': 3, '(none)': 2}, 'icon_total': 6}


### `q-center\vue\front\src\components\DSGlobalSearch.vue` (v-btn 1 / v-icon 5)

**v-icon 사이즈 혼재** (1건)
  - {'summary': {'(none)': 1, 'small': 4}, 'icon_total': 5}


### `q-center\vue\front\src\components\DSMyProfile.vue` (v-btn 2 / v-icon 4)

**icon+text 정렬 누락** (2건)
  - L30: `small class="mr-1"`
  - L59: `small class="mr-1"`


### `q-center\vue\front\src\components\DSMyRequest.vue` (v-btn 1 / v-icon 1)

**icon+text 정렬 누락** (1건)
  - L48: ``


### `q-center\vue\front\src\components\DSQualValueProfile.vue` (v-btn 3 / v-icon 2)

**v-btn 사이즈 혼재** (1건)
  - {'summary': {'small': 1, 'x-small': 2}, 'btn_total': 3}


### `q-center\vue\front\src\components\DSSchemaCompare.vue` (v-btn 3 / v-icon 7)

**반응 없는 버튼** (2건)
  - L97: "전체" — `-toggle v-model="tableFilter" dense mandatory`
  - L99: "변경만" — `x-small value="CHANGED"`

**v-btn 사이즈 혼재** (1건)
  - {'summary': {'small': 1, '(none)': 1, 'x-small': 1}, 'btn_total': 3}

**v-icon 사이즈 혼재** (1건)
  - {'summary': {'small': 2, '(none)': 5}, 'icon_total': 7}


### `q-center\vue\front\src\components\DSStructDiagResult.vue` (v-btn 1 / v-icon 8)

**v-icon 사이즈 혼재** (1건)
  - {'summary': {'small': 7, '(none)': 1}, 'icon_total': 8}


### `q-center\vue\front\src\components\DSTerm.vue` (v-btn 8 / v-icon 2)

**icon+text 정렬 누락** (2건)
  - L50: ``
  - L53: ``


### `q-center\vue\front\src\components\DSTermRecommend.vue` (v-btn 3 / v-icon 4)

**반응 없는 버튼** (1건)
  - L97: "mdi-table-eye표시 컬럼" — `small text v-bind="attrs" v-on="on" class="mr-1 btn-wide"`

**v-btn 사이즈 혼재** (1건)
  - {'summary': {'small': 2, '(none)': 1}, 'btn_total': 3}


### `q-center\vue\front\src\components\DSWord.vue` (v-btn 8 / v-icon 2)

**icon+text 정렬 누락** (2건)
  - L42: ``
  - L45: ``


### `q-center\vue\front\src\components\MMApproval.vue` (v-btn 7 / v-icon 3)

**반응 없는 버튼** (4건)
  - L6: "승인대기 ({{ statusCounts.requested }})" — `-toggle v-model="activeStatusFilter" mandatory dense class="mr-2"`
  - L8: "승인완료 ({{ statusCounts.approved }})" — `small value="APPROVED" :color="activeStatusFilter === 'APPROVED' ? 'green' : ''"`
  - L9: "반려 ({{ statusCounts.rejected }})" — `small value="REJECTED" :color="activeStatusFilter === 'REJECTED' ? 'red' : ''" :`
  - L10: "전체 ({{ approvalAllItems.length }})" — `small value="ALL" :color="activeStatusFilter === 'ALL' ? 'grey darken-1' : ''" :`

**v-btn 사이즈 혼재** (1건)
  - {'summary': {'(none)': 4, 'small': 3}, 'btn_total': 7}

**icon+text 정렬 누락** (3건)
  - L23: ``
  - L29: `small class="mr-1"`
  - L33: `small class="mr-1"`


### `q-center\vue\front\src\components\QDashboard.vue` (v-btn 1 / v-icon 16)

**v-icon 사이즈 혼재** (1건)
  - {'summary': {'(none)': 7, 'large': 6, 'x-large': 3}, 'icon_total': 16}

