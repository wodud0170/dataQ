<template>
  <v-main>
    <!-- 조회 조건 — 86번 #11 3행 명확 분리 (Header / Filter / Edit) -->
    <v-sheet class="filterWrapper px-4 pt-3 pb-2">
      <!-- ============ Row 1: Header (모델 + 비표준 토글 + 엑셀 + 조회) ============ -->
      <v-row :style="{ alignItems: 'center', margin: '0 0 8px 0', flexWrap: 'wrap', gap: '8px' }">
        <span class="filterLabel">데이터모델명</span>
        <v-autocomplete v-model="selectedModelId" :items="modelList"
          item-text="dataModelNm" item-value="dataModelId"
          @change="onModelChange" clearable dense outlined hide-details
          class="filterInput" :style="{ width: '220px' }" color="ndColor" placeholder="모델 선택" />
        <v-checkbox class="checkboxStyle ma-0 pa-0" hide-details v-model="showNonStandardOnly"
          label="비표준만 보기" color="error" dense />
        <v-spacer />
        <!-- 엑셀 드롭다운 -->
        <v-menu offset-y>
          <template v-slot:activator="{ on, attrs }">
            <v-btn class="tb-btn" depressed color="deep-purple lighten-5" v-bind="attrs" v-on="on" :disabled="!selectedModelId">
              <v-icon small left color="deep-purple darken-2">mdi-file-excel</v-icon>
              <span style="color:#4527A0;font-weight:600;">엑셀</span>
              <v-icon small right color="deep-purple darken-2">mdi-menu-down</v-icon>
            </v-btn>
          </template>
          <v-list dense>
            <v-list-item id="btn-upload-attrs" @click="triggerUploadAttrs">
              <v-list-item-icon><v-icon small>mdi-upload</v-icon></v-list-item-icon>
              <v-list-item-title>엑셀 업로드</v-list-item-title>
            </v-list-item>
            <v-list-item id="btn-download-attrs-template" @click="downloadAttrsTemplate">
              <v-list-item-icon><v-icon small>mdi-file-download-outline</v-icon></v-list-item-icon>
              <v-list-item-title>양식 다운로드</v-list-item-title>
            </v-list-item>
            <v-list-item :disabled="dmColumnAllItems.length === 0" @click="columnDataDownload">
              <v-list-item-icon><v-icon small>mdi-download</v-icon></v-list-item-icon>
              <v-list-item-title>데이터 다운로드</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn class="tb-btn gradient" depressed v-on:click="load">
          <v-icon small left>mdi-magnify</v-icon>조회
        </v-btn>
        <input ref="uploadAttrsInput" type="file" accept=".xlsx" style="display:none" @change="onAttrFileSelected" />
      </v-row>

      <!-- ============ Row 2: Filter (검색 조건 — wrap) ============ -->
      <v-row :style="{ alignItems: 'center', margin: '0 0 8px 0', flexWrap: 'wrap', gap: '8px' }">
        <span class="filterLabel">소유자</span>
        <v-select v-model="searchOwnerMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchOwner" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details :style="{ width: '110px' }" placeholder="스키마" />
        <span class="filterLabel">테이블 영문</span>
        <v-select v-model="searchTableMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchTable" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details :style="{ width: '110px' }" />
        <span class="filterLabel">테이블 한글</span>
        <v-select v-model="searchTableKrMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchTableKr" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details :style="{ width: '110px' }" />
        <span class="filterLabel">컬럼 영문</span>
        <v-select v-model="searchColumnMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchColumn" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details :style="{ width: '110px' }" />
        <span class="filterLabel">컬럼 한글</span>
        <v-select v-model="searchColumnKrMode" :items="searchModeOptions" item-text="label" item-value="value"
          dense outlined hide-details :style="{ width: '90px' }" />
        <v-text-field v-model="searchColumnKr" clearable clear-icon="mdi-close-circle" color="ndColor"
          single-line dense outlined hide-details :style="{ width: '110px' }" />
      </v-row>

      <v-divider class="mt-1 mb-2" />

      <!-- ============ Row 3: Edit (그리드 편집 툴바) ============ -->
      <v-row :style="{ alignItems: 'center', margin: '0', flexWrap: 'wrap', gap: '8px' }">
        <span class="filterLabel">추가 대상</span>
        <v-autocomplete v-model="addTargetKey" :items="objOptions" item-text="label" item-value="key"
          :disabled="!selectedModelId || addTargetLocked" dense outlined hide-details
          :style="{ width: '300px' }" color="ndColor"
          :placeholder="addTargetLocked ? '저장/취소 후 변경 가능' : '테이블 선택 (소유자.테이블)'"
          :hint="addTargetLocked ? (newRows.length > 0 ? '미저장 신규 행이 있어 대상 변경 잠금' : '컬럼 순서 변경됨 — 저장 후 변경 가능') : ''" persistent-hint
          @change="onAddTargetChange" />

        <!-- 87-x — 추가 위치 선택 (맨위 default / 맨아래 / 체크된 컬럼 뒤) — 컬럼 추가 왼쪽 -->
        <div style="width:90px; min-width:90px; max-width:90px;">
          <v-select v-model="addPosition" :items="addPositionOptions"
            dense outlined hide-details color="ndColor"
            class="add-position-select"
            :disabled="!selectedModelId || !addTargetKey" />
        </div>
        <!-- 행 추가 -->
        <v-btn id="btn-add-col-row" class="tb-btn" color="primary" depressed
          :disabled="!selectedModelId || !addTargetKey || newRows.length >= 100"
          v-on:click="addEmptyRow">
          <v-icon small left>mdi-plus</v-icon>컬럼 추가
        </v-btn>
        <v-btn id="btn-add-col-rows-10" class="tb-btn" color="primary" outlined
          :disabled="!selectedModelId || !addTargetKey || newRows.length >= 100"
          v-on:click="addEmptyRows(10)">+10행</v-btn>

        <v-divider vertical class="mx-1" />

        <!-- 87-x — 현재 그리드 출력 순서로 1..N 재할당 (보류: mergedItems 정렬로 충분).
        <v-btn class="tb-btn" color="deep-orange" dark depressed
          :disabled="!selectedModelId || !addTargetKey"
          style="min-width:300px; height:40px; padding:0 22px; letter-spacing:0; font-size:.92rem; font-weight:600; flex-shrink:0; white-space:nowrap;"
          v-on:click="renumberByDisplayOrder">
          <span style="white-space:nowrap;">현재 출력 순서로 컬럼 순서 지정</span>
        </v-btn>
        -->

        <!-- 87-x — 미저장 변경 전부 버리고 DB 에서 새로 조회 -->
        <v-btn class="tb-btn" color="grey darken-1" outlined
          :disabled="!selectedModelId"
          style="min-width:220px !important; width:220px !important; height:40px; padding:0 18px !important; flex-shrink:0; white-space:nowrap; letter-spacing:0;"
          v-on:click="reloadDiscardChanges">
          <span style="white-space:nowrap;"><v-icon small left>mdi-refresh</v-icon>새로 조회 (변경 버림)</span>
        </v-btn>

        <v-divider vertical class="mx-1" />

        <v-btn class="tb-btn" color="error" outlined :disabled="selectedRows.length === 0"
          v-on:click="deleteSelected">
          <v-icon small left>mdi-delete-outline</v-icon>선택 삭제
        </v-btn>

        <v-spacer />

        <!-- 우측: 저장 (가장 중요한 액션은 우측 끝, 색상 강조) -->
        <v-btn id="btn-save-attrs" class="tb-btn" color="success" depressed
          :disabled="!selectedModelId || (newRows.length === 0 && pendingDeletes.length === 0 && dirtyCount === 0)"
          v-on:click="saveAll">
          <v-icon small left>mdi-content-save-outline</v-icon>
          저장 <span v-if="newRows.length + pendingDeletes.length + dirtyCount > 0" class="ml-1">({{ newRows.length + pendingDeletes.length + dirtyCount }})</span>
        </v-btn>
      </v-row>

      <!-- 표준화 액션 줄 — 한글명 기준 / 영문명 기준 (다음 줄로 분리) -->
      <v-row no-gutters align="center" class="px-2 py-2" style="background:#F5F7FF; border-bottom:1px solid #E8EAF6; gap:10px;">
        <span style="font-size:.85rem; font-weight:600; color:#1A237E; min-width:60px;">표준화</span>
        <span style="font-size:.78rem; color:#546E7A; margin-right:12px;">
          선택한 컬럼을 표준 용어 사전 기준으로 일괄 보정
        </span>

        <v-btn id="btn-resolve-selected" class="tb-btn tb-btn-magic" depressed
          :disabled="selectedRows.length === 0"
          v-on:click="resolveSelectedConfirm" :loading="resolving"
          style="min-width:220px !important; width:220px !important; padding:0 24px !important; flex-shrink:0; white-space:nowrap;">
          <span style="white-space:nowrap; display:inline-block;">한글명 기준 표준화</span>
        </v-btn>

        <v-btn id="btn-resolve-by-eng" class="tb-btn tb-btn-magic" depressed
          :disabled="selectedRows.length === 0"
          v-on:click="resolveByEngConfirm" :loading="resolvingByEng"
          style="min-width:220px !important; width:220px !important; padding:0 24px !important; flex-shrink:0; white-space:nowrap;">
          <span style="white-space:nowrap; display:inline-block;">영문명 기준 표준화</span>
        </v-btn>
      </v-row>

      <!-- 87-3 제약조건 액션 줄 — PK/FK 생성 (왼쪽 체크박스로 컬럼 선택 후 클릭) -->
      <v-row no-gutters align="center" class="px-2 py-2" style="background:#FAFAFE; border-bottom:1px solid #E8EAF6; gap:10px;">
        <span style="font-size:.85rem; font-weight:600; color:#1A237E; min-width:60px;">제약조건</span>
        <span style="font-size:.78rem; color:#546E7A; margin-right:12px;">
          선택한 컬럼들로 PK / FK 생성 — 다중 컬럼이면 복합키
        </span>
        <v-btn small class="tb-btn" color="indigo" dark depressed
          :disabled="selectedRows.length === 0"
          v-on:click="openPkDialogFromSelection"
          style="min-width:130px; flex-shrink:0; white-space:nowrap;">
          <v-icon small left>mdi-key-variant</v-icon>PK 생성
        </v-btn>
        <v-btn small class="tb-btn" color="deep-purple" dark depressed
          :disabled="selectedRows.length === 0"
          v-on:click="openFkDialogFromSelection"
          style="min-width:130px; flex-shrink:0; white-space:nowrap;">
          <v-icon small left>mdi-link-variant</v-icon>FK 생성
        </v-btn>
      </v-row>

      <!-- 안내 라인 (조건부) -->
      <div v-if="newRows.length >= 100" class="mt-1 px-1" style="color:#D32F2F;font-size:.78rem;">
        100행 도달 — 대량 입력은 엑셀 업로드 사용
      </div>
      <div v-else-if="selectedModelId && addTargetKey" class="mt-1 px-1" style="color:#546E7A;font-size:.72rem;">
        💡 엑셀에서 Ctrl+C 후 그리드에서 Ctrl+V — 여러 행 한 번에 추가 (열 순서: 한글명·NULL·PK·FK·기본값)
      </div>
    </v-sheet>

    <!-- 목록 카운트 -->
    <v-sheet class="tableSpt">
      <v-sheet>
        <span class="ndColor--text">총 {{ mergedItems.length }}건</span>
        <span v-if="newRows.length > 0" class="ml-2" style="color:#F57C00;">(미저장 신규 {{ newRows.length }}건)</span>
        <span v-if="dirtyCount > 0" class="ml-2" style="color:#F57C00;">(수정중 {{ dirtyCount }}건)</span>
        <span v-if="pendingDeletes.length > 0" class="ml-2" style="color:#D32F2F;">(삭제 대기 {{ pendingDeletes.length }}건)</span>
      </v-sheet>
      <v-sheet :style="{ width: '80px' }">
        <v-select v-model.lazy="itemsPerPage" :items="tableViewLengthList"
          color="ndColor" hide-details outlined dense></v-select>
      </v-sheet>
    </v-sheet>

    <!-- 컬럼 목록 (인라인 편집) -->
    <!-- 86번 #11 — fixed-header + 동적 height. 가로/세로 스크롤 모두 그리드 내부에서 동작 -->
    <v-data-table id="clTable_table" :headers="dmColumnDetaileHeaders" :items="mergedItems"
      :page.sync="page" :items-per-page="itemsPerPage" hide-default-footer
      item-key="_rowKey" show-select v-model="selectedRows"
      :sort-by.sync="userSortBy" :sort-desc.sync="userSortDesc"
      :must-sort="false"
      fixed-header :height="tableHeight"
      class="px-4 pb-3" :loading="loadTable" loading-text="잠시만 기다려주세요.">

      <template #item.objNmKr="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item.objNmKr }}</span>
      </template>
      <template #item.objNm="{ item }">
        <span :style="{ margin: '0px 8px' }">{{ item.objNm }}</span>
      </template>

      <template #item.attrNmKr="{ item }">
        <div style="display:flex; align-items:center;">
          <v-icon v-if="item.termsStndYn === 'N' && item._mode !== 'add'" small color="error" class="mr-1"
            title="비표준 용어">mdi-alert-circle</v-icon>
          <v-text-field v-model="item.attrNmKr"
            :class="'inline-edit ' + (item._error ? 'inline-error ' : '') + (isRowDirty(item) ? 'inline-dirty' : '')"
            dense hide-details outlined flat solo single-line
            :placeholder="item._mode === 'add' ? '컬럼 한글명 (논리)' : ''"
            :autofocus="item._mode === 'add'"
            @paste.native="onCellPaste(item, 'attrNmKr', $event)" />
        </div>
      </template>

      <template #item.attrNm="{ item }">
        <v-text-field v-model="item.attrNm"
          :class="'inline-edit ' + (item._error ? 'inline-error ' : '') + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="컬럼 영문명(물리)"
          @paste.native="onCellPaste(item, 'attrNm', $event)" />
      </template>
      <template #item.dataType="{ item }">
        <v-combobox v-model="item.dataType" :items="dataTypeOptions"
          :class="'inline-edit ' + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="VARCHAR"
          :menu-props="{ maxHeight: 280 }"
          @paste.native="onCellPaste(item, 'dataType', $event)" />
      </template>
      <template #item.dataLen="{ item }">
        <v-text-field v-model.number="item.dataLen"
          type="number"
          :class="'inline-edit ' + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="255"
          @paste.native="onCellPaste(item, 'dataLen', $event)" />
      </template>
      <template #item.dataDecimalLen="{ item }">
        <v-text-field v-model.number="item.dataDecimalLen"
          type="number"
          :class="'inline-edit ' + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="-"
          @paste.native="onCellPaste(item, 'dataDecimalLen', $event)" />
      </template>
      <template #item.attrOrder="{ item }">
        <v-text-field v-model.number="item.attrOrder"
          type="number" min="1"
          :class="'inline-edit ' + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="자동"
          @paste.native="onCellPaste(item, 'attrOrder', $event)" />
      </template>

      <template #item.nullableYn="{ item }">
        <v-checkbox v-model="item.nullableYn" true-value="Y" false-value="N"
          :class="'ma-0 pa-0 ' + (isRowDirty(item) ? 'inline-dirty-cb' : '')"
          dense hide-details />
      </template>
      <template #item.pkYn="{ item }">
        <v-icon v-if="getPkInfo(item)" small color="indigo" style="cursor:pointer;"
          :title="'PK 해제 — ' + getPkInfo(item).name"
          @click="confirmDeletePk(item)">mdi-check-circle</v-icon>
        <span v-else style="color:#CFD8DC;">-</span>
      </template>
      <template #item.fkYn="{ item }">
        <v-icon v-if="getFkInfo(item)" small color="deep-purple" style="cursor:pointer;"
          :title="'FK 해제 — ' + getFkInfo(item).name + ' → ' + getFkInfo(item).refTableNm + '.' + getFkInfo(item).refColumnNm"
          @click="confirmDeleteFk(item)">mdi-check-circle</v-icon>
        <span v-else style="color:#CFD8DC;">-</span>
      </template>
      <template #item.defaultVal="{ item }">
        <v-text-field v-model="item.defaultVal"
          :class="'inline-edit inline-edit-center ' + (isRowDirty(item) ? 'inline-dirty' : '')"
          dense hide-details outlined flat solo single-line placeholder="-"
          @paste.native="onCellPaste(item, 'defaultVal', $event)" />
      </template>

      <template #item.termsStndYn="{ item }">
        <v-icon v-if="item._mode === 'add'" small color="grey">mdi-minus-circle-outline</v-icon>
        <v-icon v-else small :color="item.termsStndYn === 'Y' ? 'success' : 'error'">
          {{ item.termsStndYn === 'Y' ? 'mdi-check-circle' : 'mdi-close-circle' }}
        </v-icon>
      </template>

      <template #item.resolveReason="{ item }">
        <span v-if="item._resolveReason" style="color:#D32F2F;font-size:.75rem;">{{ item._resolveReason }}</span>
        <span v-else style="color:#9E9E9E;">-</span>
      </template>

      <template #item.actions="{ item }">
        <v-icon v-if="item._mode === 'add'" small color="error" @click="removeNewRow(item)">mdi-close</v-icon>
        <v-icon v-else small :disabled="!selectedModelId" @click="deleteOne(item)">mdi-delete</v-icon>
      </template>

      <template #top>
        <v-progress-linear v-show="loadTable" color="indigo darken-2" indeterminate />
      </template>
      <template #no-data>
        <v-alert v-show="!loadTable">데이터가 존재하지 않습니다.</v-alert>
        <span v-show="loadTable">잠시만 기다려주세요.</span>
      </template>
    </v-data-table>

    <!-- ===== 87-3 PK 생성 모달 ===== -->
    <v-dialog v-model="pkDialog" max-width="640" persistent>
      <v-card>
        <v-card-title class="indigo white--text">PK 생성 — {{ pkForm.objNm }}</v-card-title>
        <v-card-text class="pt-4">
          <v-text-field v-model="pkForm.pkName" label="PK 이름" dense
            :placeholder="'PK_' + (pkForm.objNm || '').toUpperCase()" hint="비우면 자동 생성"
            persistent-hint />
          <div class="mt-3" style="font-size:.85rem; color:#37474F;">
            PK 구성 컬럼 (순서 중요)
          </div>
          <div v-for="(c, i) in pkForm.columns" :key="i" class="d-flex align-center mt-2" style="gap:8px;">
            <span style="width:28px; color:#90A4AE;">{{ i + 1 }}.</span>
            <v-select v-model="pkForm.columns[i]" :items="pkColumnOptions" dense hide-details
              outlined style="flex:1;" />
            <v-btn icon small color="error" @click="pkForm.columns.splice(i, 1)"
              v-if="pkForm.columns.length > 1"><v-icon small>mdi-close</v-icon></v-btn>
          </div>
          <v-btn small outlined color="indigo" class="mt-3" @click="pkForm.columns.push(null)">
            <v-icon small left>mdi-plus</v-icon>컬럼 추가
          </v-btn>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="pkDialog = false">취소</v-btn>
          <v-btn color="indigo" dark @click="submitPk">생성</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ===== 87-3 FK 생성 모달 ===== -->
    <v-dialog v-model="fkDialog" max-width="780" persistent>
      <v-card>
        <v-card-title class="deep-purple white--text">FK 생성 — {{ fkForm.objNm }}</v-card-title>
        <v-card-text class="pt-4">
          <v-row dense>
            <v-col cols="6">
              <v-text-field v-model="fkForm.fkName" label="FK 이름" dense
                :placeholder="'FK_' + (fkForm.objNm || '').toUpperCase() + '_' + (fkForm.refTableNm || '').toUpperCase()"
                hint="비우면 자동 생성" persistent-hint />
            </v-col>
            <v-col cols="6">
              <v-autocomplete v-model="fkForm.refTableKey" :items="refTableOptions"
                item-text="label" item-value="key" label="참조 테이블 *"
                dense outlined hide-details @change="onRefTableChange" />
            </v-col>
          </v-row>
          <div class="mt-3" style="font-size:.85rem; color:#37474F;">
            컬럼 매핑 (자기 → 참조)
          </div>
          <div v-for="(m, i) in fkForm.mappings" :key="i" class="d-flex align-center mt-2" style="gap:8px;">
            <span style="width:28px; color:#90A4AE;">{{ i + 1 }}.</span>
            <v-select v-model="fkForm.mappings[i].ownAttrNm" :items="fkOwnColumnOptions"
              dense hide-details outlined style="flex:1;" placeholder="자기 컬럼" />
            <v-icon small color="grey">mdi-arrow-right</v-icon>
            <v-select v-model="fkForm.mappings[i].refAttrNm" :items="fkRefColumnOptions"
              dense hide-details outlined style="flex:1;" placeholder="참조 컬럼"
              :disabled="!fkForm.refTableKey" />
            <v-btn icon small color="error" @click="fkForm.mappings.splice(i, 1)"
              v-if="fkForm.mappings.length > 1"><v-icon small>mdi-close</v-icon></v-btn>
          </div>
          <v-btn small outlined color="deep-purple" class="mt-3"
            @click="fkForm.mappings.push({ownAttrNm:null, refAttrNm:null})">
            <v-icon small left>mdi-plus</v-icon>매핑 추가
          </v-btn>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="fkDialog = false">취소</v-btn>
          <v-btn color="deep-purple" dark @click="submitFk">생성</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-sheet class="split_bottom_wrap">
      <div class="text-center px-4 pt-2 pb-2 pagination_wrap" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left"
          next-icon="mdi-menu-right" color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </v-sheet>

    <!-- 엑셀 업로드 미리보기 다이얼로그 -->
    <v-dialog v-model="uploadDialog" max-width="1200" persistent>
      <v-card>
        <v-card-title>
          컬럼 엑셀 업로드 미리보기
          <v-spacer />
          <span v-if="uploadSummary" style="font-size:.85rem;color:#455A64;">
            총 {{ uploadSummary.total }} / 등록 예정 {{ uploadSummary.toInsertAttrs }} (FK {{ uploadSummary.toInsertFks }}) / 중복 스킵 {{ uploadSummary.skipped || 0 }} / 오류 {{ (uploadErrors || []).length }}
          </span>
        </v-card-title>
        <v-card-text>
          <v-alert v-if="uploadErrors && uploadErrors.length > 0" type="error" dense text>
            오류 {{ uploadErrors.length }}건 — 수정 후 다시 업로드하세요.
            <div v-for="(e, i) in uploadErrors.slice(0, 5)" :key="'e' + i" style="font-size:.8rem;">
              · {{ e.row }}행: {{ e.msg || e.reason }}
            </div>
            <div v-if="uploadErrors.length > 5" style="font-size:.8rem;">외 {{ uploadErrors.length - 5 }}건</div>
          </v-alert>
          <v-alert v-if="uploadWarnings && uploadWarnings.length > 0" type="warning" dense text>
            경고 {{ uploadWarnings.length }}건
            <div v-for="(w, i) in uploadWarnings.slice(0, 5)" :key="'w' + i" style="font-size:.8rem;">
              · {{ w.row }}행: {{ w.msg || w.reason }}
            </div>
          </v-alert>
          <v-data-table :items="uploadRows" :headers="uploadAttrHeaders" dense
            :items-per-page="20" class="preview-grid" :item-class="rowClass">
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
          <v-btn id="btn-upload-attrs-commit" color="primary"
            :disabled="!uploadSummary || (uploadErrors && uploadErrors.length > 0) || uploadSummary.toInsertAttrs === 0"
            :loading="uploadCommitting" @click="commitAttrsUpload">
            {{ uploadSummary ? uploadSummary.toInsertAttrs : 0 }}건 등록 실행
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 용어 상세 정보 Modal -->
    <v-dialog max-width="800" v-model="termDataModalShow">
      <NdModal @hide="termDataModalShow = false" :footer-submit="false" header-title="용어 상세 정보" footer-hide-title="확인">
        <template v-slot:body>
          <v-container fluid>
            <v-data-table id="term_detail_table" :items="termDetailItem" :loading="termLoading"
              hide-default-footer class="px-4 pb-3">
              <template v-slot:body="{ items }" v-if="termDetailItem.length !== 0">
                <tbody>
                  <tr v-for="header in termDetaileHeaders" :key="header.value">
                    <td :style="{ backgroundColor: 'rgba(63, 81, 181, 0.08)', width: '25%' }">{{ header.text }}</td>
                    <td v-for="item in items" :key="item.termNm">
                      <div v-if="Array.isArray(item[header.value])">
                        <div v-for="item2 in item[header.value]" :key="item2">{{ item2 }}</div>
                      </div>
                      <div v-else>{{ item[header.value] }}</div>
                    </td>
                  </tr>
                </tbody>
              </template>
              <template #top>
                <v-progress-linear v-show="termLoading" color="indigo darken-2" indeterminate />
              </template>
              <template #no-data>
                <v-alert v-show="!termLoading">데이터가 존재하지 않습니다.</v-alert>
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
import { eventBus } from '../eventBus';
import NdModal from "./../views/modal/NdModal.vue"

export default {
  name: 'DSDatamodelStatusColumn',
  props: ['isMobile'],
  components: { NdModal },
  watch: {
    mergedItems() {
      this.pageCount = Math.ceil(this.mergedItems.length / this.itemsPerPage);
    },
    itemsPerPage() {
      this.pageCount = Math.ceil(this.mergedItems.length / this.itemsPerPage);
    },
    addTargetKey(newVal, oldVal) {
      if (this.addTargetLocked && newVal !== oldVal && oldVal != null) {
        this.$nextTick(() => {
          this.addTargetKey = oldVal;
          this.$swal.fire({
            title: '추가 대상 변경 불가',
            text: this.newRows.length > 0
              ? '미저장 신규 행이 있어 변경할 수 없습니다. 저장 또는 삭제 후 다시 시도하세요.'
              : '컬럼 순서가 변경되었습니다. 저장 또는 새로고침 후 다시 시도하세요.',
            icon: 'warning',
            confirmButtonText: '확인',
          });
        });
      }
    },
  },
  data: () => ({
    modelList: [],
    dmColumnAllItems: [],
    selectedModelId: null,
    // 86번 #11 — 검색 필드 (소유자, 테이블 한글명 추가, 모든 모드 셀렉트)
    searchOwner: '',
    searchOwnerMode: 'contains',
    searchTable: '',
    searchTableMode: 'contains',
    searchTableKr: '',
    searchTableKrMode: 'contains',
    searchColumn: '',
    searchColumnMode: 'contains',
    searchColumnKr: '',
    searchColumnKrMode: 'contains',
    searchModeOptions: [
      { value: 'contains', label: '포함' },
      { value: 'exact',    label: '완전 일치' },
      { value: 'start',    label: '앞' },
      { value: 'end',      label: '뒤' },
    ],
    showNonStandardOnly: false,
    loadTable: false,
    // 86번 #11 — 그리드 동적 높이 (viewport 변경 / 데이터 로드 시 재계산)
    tableHeight: 500,
    page: 1,
    pageCount: null,
    itemsPerPage: 20,
    tableViewLengthList: [10, 20, 30, 50, 100],
    termDataModalShow: false,
    termDetailItem: [],
    termLoading: false,
    // 그리드 편집 상태
    objOptions: [],
    addTargetKey: null,
    // 87-x — v-data-table 헤더 클릭 정렬 상태 (renumberByDisplayOrder 가 동일 정렬로 iterate)
    userSortBy: [],
    userSortDesc: [],
    // 87-x — 컬럼 추가 위치 (맨위 default / 맨아래 / 체크된 컬럼 뒤)
    addPosition: 'top',
    addPositionOptions: [
      { text: '맨 위 추가', value: 'top' },
      { text: '맨 아래 추가', value: 'bottom' },
      { text: '체크된 컬럼 뒤 추가', value: 'afterChecked' },
    ],
    newRows: [],            // 미저장 ADD 행들
    pendingDeletes: [],     // 미저장 DELETE 행들
    selectedRows: [],       // show-select 체크된 행
    resolving: false,
    resolvingByEng: false,
    // 87-3 PK/FK
    // key = ownerObjNm (예: USER1TB_MEMBER), value = { byCol: { ATTR_NM: { pk:{name,columns}, fk:{name,refTable,refCol} } }, pk: {...}, fkList: [...] }
    constraintsByKey: {},
    pkDialog: false,
    pkForm: { dataModelId: null, objOwner: '', objNm: '', pkName: '', columns: [] },
    fkDialog: false,
    fkForm: { dataModelId: null, objOwner: '', objNm: '', fkName: '',
              refOwner: '', refTableNm: '', refTableKey: null,
              mappings: [{ ownAttrNm: null, refAttrNm: null }] },
    fkRefColumnOptions: [],
    // 데이터 타입 드롭다운 옵션 — 일반적인 RDBMS 타입. v-combobox 라 비표준값도 직접 입력 가능
    dataTypeOptions: [
      'VARCHAR', 'CHAR', 'TEXT', 'CLOB', 'BLOB',
      'NUMBER', 'NUMERIC', 'DECIMAL', 'INTEGER', 'BIGINT', 'SMALLINT',
      'FLOAT', 'DOUBLE',
      'DATE', 'TIMESTAMP', 'TIME', 'BOOLEAN',
    ],
    dmColumnDetaileHeaders: [
      // [그룹 1] 테이블 식별 — 회색-블루 (#ECEFF1)
      { text: '소유자', align: 'center', sortable: true, value: 'objOwner', width: '80px', class: 'hdr-table' },
      { text: '테이블 한글명 (논리)', sortable: true, align: 'center', value: 'objNmKr', class: 'hdr-table' },
      { text: '테이블 영문명 (물리)', align: 'center', sortable: true, value: 'objNm', class: 'hdr-table' },
      // [그룹 2] 논리 / 사용자 편집 — 연한 그린 (#E8F5E9)
      { text: '컬럼 한글명 (논리)', sortable: false, align: 'center', value: 'attrNmKr', class: 'hdr-logical' },
      { text: 'NULL', sortable: false, align: 'center', value: 'nullableYn', width: '60px', class: 'hdr-logical' },
      { text: 'PK', sortable: false, align: 'center', value: 'pkYn', width: '50px', class: 'hdr-logical' },
      { text: 'FK', sortable: false, align: 'center', value: 'fkYn', width: '50px', class: 'hdr-logical' },
      { text: '디폴트', sortable: false, align: 'center', value: 'defaultVal', width: '90px', class: 'hdr-logical' },
      // [그룹 3] 자동 채움 / 물리 — 연한 인디고 (#E3F2FD)
      { text: '컬럼 영문명 (물리)', sortable: false, align: 'center', value: 'attrNm', width: '200px', class: 'hdr-physical' },
      { text: '데이터 타입', sortable: false, align: 'center', value: 'dataType', class: 'hdr-physical' },
      { text: '길이', sortable: false, align: 'center', value: 'dataLen', width: '90px', class: 'hdr-physical' },
      { text: '소수점', sortable: false, align: 'center', value: 'dataDecimalLen', width: '50px', class: 'hdr-physical' },
      { text: '순서', sortable: true, align: 'center', value: 'attrOrder', width: '70px', class: 'hdr-physical' },
      // 메타 (기본 색)
      { text: '표준', sortable: false, align: 'center', value: 'termsStndYn', width: '60px' },
      { text: '변환 불가 사유', sortable: false, align: 'center', value: 'resolveReason', width: '160px' },
      { text: '삽입/삭제', sortable: false, align: 'center', value: 'actions', width: '110px' },
    ],
    // 엑셀 업로드
    uploadDialog: false,
    uploadFile: null,
    uploadRows: [],
    uploadErrors: [],
    uploadWarnings: [],
    uploadSummary: null,
    uploadCommitting: false,
    uploadAttrHeaders: [
      { text: '행', value: 'row', align: 'center', sortable: false, width: '60px' },
      { text: '상태', value: '_action', align: 'center', sortable: false, width: '70px' },
      { text: '소유자', value: 'objOwner', align: 'center', sortable: false, width: '100px' },
      { text: '테이블(영문)', value: 'objNm', align: 'center', sortable: false, width: '140px' },
      { text: '테이블(한글)', value: 'objNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '컬럼(영문)', value: 'attrNm', align: 'center', sortable: false, width: '140px' },
      { text: '컬럼(한글)', value: 'attrNmKr', align: 'center', sortable: false },
      { text: '타입', value: 'dataType', align: 'center', sortable: false, width: '90px' },
      { text: '길이', value: 'dataLen', align: 'center', sortable: false, width: '60px' },
      { text: 'PK', value: 'pkYn', align: 'center', sortable: false, width: '50px' },
      { text: 'FK', value: 'fkYn', align: 'center', sortable: false, width: '50px' },
      { text: '참조 테이블', value: 'refObjNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '참조 컬럼', value: 'refAttrNmKr', align: 'center', sortable: false, width: '120px' },
      { text: '삭제 규칙', value: 'deleteRule', align: 'center', sortable: false, width: '90px' },
      { text: '메시지', value: '_msg', sortable: false },
    ],
    termDetaileHeaders: [
      { text: '용어명', align: 'center', sortable: false, value: 'termsNm' },
      { text: '용어영문약어명', sortable: false, align: 'center', value: 'termsEngAbrvNm' },
      { text: '용어설명', sortable: false, align: 'center', value: 'termsDesc' },
      { text: '도메인명', sortable: false, align: 'center', value: 'domainNm' },
      { text: '이음동의어목록', sortable: false, align: 'center', value: 'allophSynmLst' },
      { text: '코드그룹', sortable: false, align: 'center', value: 'codeGrp' },
      { text: '담당기관명', sortable: false, align: 'center', value: 'chrgOrg' },
      { text: '공통표준여부', sortable: false, align: 'center', value: 'commStndYn' },
      { text: '제정차수', sortable: false, align: 'center', value: 'magntdOrd' },
      { text: '승인여부', sortable: false, align: 'center', value: 'aprvYn' },
      { text: '승인상태수정일시', sortable: false, align: 'center', value: 'aprvStatUpdtDt' },
      { text: '생성일시', sortable: false, align: 'center', value: 'cretDt' },
      { text: '생성사용자ID', sortable: false, align: 'center', value: 'cretUserId' },
      { text: '수정일시', sortable: false, align: 'center', value: 'updtDt' },
      { text: '수정사용자ID', sortable: false, align: 'center', value: 'updtUserId' },
    ],
  }),
  computed: {
    // 87-3 모달용 옵션
    pkColumnOptions() {
      return (this.dmColumnAllItems || [])
        .filter(r => r.objNm === this.pkForm.objNm && (r.objOwner || '') === (this.pkForm.objOwner || '')
                  && r._mode === 'saved')
        .map(r => this._toColumnOption(r));
    },
    fkOwnColumnOptions() {
      return (this.dmColumnAllItems || [])
        .filter(r => r.objNm === this.fkForm.objNm && (r.objOwner || '') === (this.fkForm.objOwner || '')
                  && r._mode === 'saved')
        .map(r => this._toColumnOption(r));
    },
    refTableOptions() {
      // FK 참조 가능 후보 — 자기 자신 제외
      return (this.objOptions || []).filter(o =>
        !(o.objNm === this.fkForm.objNm && (o.objOwner || '') === (this.fkForm.objOwner || ''))
      );
    },
    dmColumnItems() {
      // 86번 #11 — 소유자/테이블 한글·영문/컬럼 한글·영문 (모드: contains/exact/start/end)
      return this.dmColumnAllItems.filter(item => {
        if (!this._matchName(item.objOwner, this.searchOwner,    this.searchOwnerMode))    return false;
        if (!this._matchName(item.objNm,    this.searchTable,    this.searchTableMode))    return false;
        if (!this._matchName(item.objNmKr,  this.searchTableKr,  this.searchTableKrMode))  return false;
        if (!this._matchName(item.attrNm,   this.searchColumn,   this.searchColumnMode))   return false;
        if (!this._matchName(item.attrNmKr, this.searchColumnKr, this.searchColumnKrMode)) return false;
        if (this.showNonStandardOnly && item.termsStndYn !== 'N') return false;
        return true;
      });
    },
    mergedItems() {
      // 87-x — 신규 + 저장 행을 같이 owner > obj_nm > attr_ord 정렬.
      // (addPosition 이 맨아래여도 신규 행이 attr_ord 기준 아래로 가도록)
      const sortOrd = it => Number(it.attrOrder) || 0;
      const combined = [...this.newRows, ...this.dmColumnItems];
      return combined.slice().sort((a, b) => {
        const oa = a.objOwner || '', ob = b.objOwner || '';
        if (oa !== ob) return oa.localeCompare(ob);
        const na = a.objNm || '',    nb = b.objNm || '';
        if (na !== nb) return na.localeCompare(nb);
        return sortOrd(a) - sortOrd(b);
      });
    },
    dirtyCount() {
      return (this.dmColumnAllItems || []).filter(it => this.isRowDirty(it)).length;
    },
    // 87-x — saved 행의 attr_ord 가 변경된 게 있는지 (추가 대상 변경 잠금 조건의 일부)
    hasOrderChanged() {
      return (this.dmColumnAllItems || []).some(it =>
        it._mode === 'saved' && it._orig
        && Number(it.attrOrder || 0) !== Number(it._orig.attrOrder || 0)
      );
    },
    // 87-x — 추가 대상 lock 여부 (미저장 신규 OR attr_ord 변경)
    addTargetLocked() {
      return this.newRows.length > 0 || this.hasOrderChanged;
    },
  },
  methods: {
    /** 86번 #11 — 그리드 높이 동적 계산. 화면 viewport 에서 toolbar / 카운트 / 페이지네이션 영역 차감 */
    _calcTableHeight() {
      // viewport 높이 - (탭 ~48 + 필터 wrapper ~150 + 카운트 행 ~46 + 페이지네이션 ~60 + 여유 24)
      const reserved = 48 + 150 + 46 + 60 + 24;
      this.tableHeight = Math.max(300, window.innerHeight - reserved);
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
    /**
     * 물리명(attrNm) 표시 포맷.
     *  - 미저장 신규 행 (_mode='add') 이면서 물리명이 안 들어온 경우만 placeholder
     *  - 저장된 행: 물리명 / 타입 / 길이 그대로 노출 (수집·엑셀로 들어온 물리값 가리지 않음)
     *  - 비표준 (termsStndYn='N') 은 가리지 않고 별도 알림 아이콘으로 표기
     */
    formatAttrNm(item) {
      if (item._mode === 'add' && !item.attrNm) return '(저장 후 자동)';
      return item.attrNm || '';
    },
    formatDataType(item) {
      if (item._mode === 'add' && !item.dataType) return '-';
      return item.dataType || '';
    },
    formatDataLen(item) {
      if (item._mode === 'add' && (item.dataLen == null || item.dataLen === '')) return '-';
      return item.dataLen != null ? item.dataLen : '';
    },
    formatDataDecimalLen(item) {
      if (item._mode === 'add' && (item.dataDecimalLen == null || item.dataDecimalLen === '')) return '-';
      return item.dataDecimalLen != null ? item.dataDecimalLen : '';
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
      this.dmColumnAllItems = [];
      this.newRows = [];
      this.pendingDeletes = [];
      this.selectedRows = [];
      this.addTargetKey = null;
      if (!modelId) return;
      this.loadObjOptions();
      this.load();
    },
    load() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델명을 선택해주세요.', confirmButtonText: '확인', icon: 'warning' });
        return;
      }
      this.loadTable = true;
      axios.get(this.$APIURL.base + "api/dm/getDataModelAttrListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        this.dmColumnAllItems = this._mapColumnData(res.data);
        this.selectedRows = [];
        this.loadTable = false;
        this.fetchConstraints();
      }).catch(() => {
        this.$swal.fire({ title: '컬럼 정보 로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.loadTable = false;
      });
    },
    // 87-3 — 모델 단위 PK/FK 일괄 조회 + 컬럼별 매핑
    fetchConstraints() {
      if (!this.selectedModelId) { this.constraintsByKey = {}; return; }
      axios.get(this.$APIURL.base + 'api/dm/getConstraintsByDmId', {
        params: { dataModelId: this.selectedModelId }
      }).then((res) => {
        const map = {};
        (res.data || []).forEach(c => {
          const key = (c.objOwner || '') + '|' + c.objNm;
          if (!map[key]) map[key] = { byCol: {}, pk: null, fkList: [] };
          if (!map[key].byCol[c.columnNm]) map[key].byCol[c.columnNm] = {};
          if (c.constraintType === 'P') {
            map[key].byCol[c.columnNm].pk = { name: c.constraintNm };
            if (!map[key].pk) map[key].pk = { name: c.constraintNm, columns: [] };
            if (map[key].pk.name === c.constraintNm) map[key].pk.columns.push(c.columnNm);
          } else if (c.constraintType === 'R') {
            map[key].byCol[c.columnNm].fk = {
              name: c.constraintNm,
              refOwner: c.refOwner || '',
              refTableNm: c.refTableNm || '',
              refColumnNm: c.refColumnNm || '',
            };
          }
        });
        this.constraintsByKey = map;
      }).catch(() => { this.constraintsByKey = {}; });
    },
    getPkInfo(item) {
      const k = (item.objOwner || '') + '|' + item.objNm;
      const entry = this.constraintsByKey[k];
      return entry && entry.byCol[item.attrNm] && entry.byCol[item.attrNm].pk || null;
    },
    getFkInfo(item) {
      const k = (item.objOwner || '') + '|' + item.objNm;
      const entry = this.constraintsByKey[k];
      return entry && entry.byCol[item.attrNm] && entry.byCol[item.attrNm].fk || null;
    },

    // 그리드 셀의 PK/FK 체크박스 클릭 — 설정된 행은 해제(삭제), 미설정 행은 안내
    onPkCheckClick(item, e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault();
      if (this.getPkInfo(item)) {
        this.confirmDeletePk(item);
      } else {
        this.$swal.fire({
          title: 'PK 생성은 위쪽 [PK 생성] 버튼 사용',
          text: '왼쪽 체크박스로 컬럼들을 선택한 뒤 상단 [PK 생성] 을 클릭하세요. (다중 컬럼 = 복합키)',
          icon: 'info',
        });
      }
    },
    onFkCheckClick(item, e) {
      if (e && typeof e.preventDefault === 'function') e.preventDefault();
      if (this.getFkInfo(item)) {
        this.confirmDeleteFk(item);
      } else {
        this.$swal.fire({
          title: 'FK 생성은 위쪽 [FK 생성] 버튼 사용',
          text: '왼쪽 체크박스로 자기 테이블의 컬럼들을 선택한 뒤 상단 [FK 생성] 을 클릭하세요.',
          icon: 'info',
        });
      }
    },

    // 선택된 행들에서 PK 모달 진입 — 같은 테이블 + saved 만 허용
    openPkDialogFromSelection() {
      const sel = this.selectedRows || [];
      if (sel.length === 0) {
        this.$swal.fire({ title: '왼쪽 체크박스로 PK 컬럼을 선택하세요.', icon: 'warning' }); return;
      }
      const owner = sel[0].objOwner || '';
      const objNm = sel[0].objNm;
      const same = sel.every(r => r.objNm === objNm && (r.objOwner || '') === owner);
      if (!same) {
        this.$swal.fire({ title: '같은 테이블의 컬럼만 선택해주세요.', icon: 'warning' }); return;
      }
      const allSaved = sel.every(r => r._mode === 'saved');
      if (!allSaved) {
        this.$swal.fire({ title: '미저장 신규 행은 PK 로 묶을 수 없습니다.', text: '먼저 저장 후 다시 시도', icon: 'warning' }); return;
      }
      this.pkForm = {
        dataModelId: this.selectedModelId,
        objOwner: owner,
        objNm: objNm,
        pkName: '',
        columns: sel.map(r => r.attrNm),
      };
      this.pkDialog = true;
    },
    openFkDialogFromSelection() {
      const sel = this.selectedRows || [];
      if (sel.length === 0) {
        this.$swal.fire({ title: '왼쪽 체크박스로 FK 컬럼을 선택하세요.', icon: 'warning' }); return;
      }
      const owner = sel[0].objOwner || '';
      const objNm = sel[0].objNm;
      const same = sel.every(r => r.objNm === objNm && (r.objOwner || '') === owner);
      if (!same) {
        this.$swal.fire({ title: '같은 테이블의 컬럼만 선택해주세요.', icon: 'warning' }); return;
      }
      const allSaved = sel.every(r => r._mode === 'saved');
      if (!allSaved) {
        this.$swal.fire({ title: '미저장 신규 행은 FK 로 묶을 수 없습니다.', text: '먼저 저장 후 다시 시도', icon: 'warning' }); return;
      }
      this.fkForm = {
        dataModelId: this.selectedModelId,
        objOwner: owner,
        objNm: objNm,
        fkName: '',
        refOwner: '',
        refTableNm: '',
        refTableKey: null,
        mappings: sel.map(r => ({ ownAttrNm: r.attrNm, refAttrNm: null })),
      };
      this.fkRefColumnOptions = [];
      this.fkDialog = true;
    },

    // ===== PK 모달 =====
    openPkDialog(item) {
      this.pkForm = {
        dataModelId: this.selectedModelId,
        objOwner: item.objOwner || '',
        objNm: item.objNm,
        pkName: '',
        columns: [item.attrNm],
      };
      this.pkDialog = true;
    },
    submitPk() {
      const cols = (this.pkForm.columns || []).filter(c => c);
      if (cols.length === 0) {
        this.$swal.fire({ title: '컬럼을 선택하세요.', icon: 'warning' }); return;
      }
      axios.post(this.$APIURL.base + 'api/dm/createPk', {
        dataModelId: this.pkForm.dataModelId,
        objOwner: this.pkForm.objOwner,
        tableNm: this.pkForm.objNm,
        pkName: this.pkForm.pkName,
        columns: cols,
      }).then((res) => {
        if (res.data.resultCode === 200) {
          this.$swal.fire({ title: 'PK 생성 완료', text: 'PK 이름: ' + res.data.contents,
            icon: 'success', timer: 1500, showConfirmButton: false });
          this.pkDialog = false;
          this.load();
        } else {
          this.$swal.fire({ title: 'PK 생성 실패', text: res.data.resultMessage || '', icon: 'error' });
        }
      }).catch((err) => {
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '서버 오류';
        this.$swal.fire({ title: 'PK 생성 실패', text: msg, icon: 'error' });
      });
    },
    confirmDeletePk(item) {
      const info = this.getPkInfo(item);
      if (!info) return;
      // 87-x — PK 해제 전 종속 FK 미리 조회 → cascade 안내
      axios.post(this.$APIURL.base + 'api/dm/getPkDependentFks', {
        dataModelId: this.selectedModelId,
        objOwner: item.objOwner || '',
        tableNm: item.objNm,
        pkName: info.name,
      }).then((res) => {
        const data = (res && res.data) || {};
        const deps = data.dependentFks || [];
        const baseHtml = '<b>' + info.name + '</b><br>이 PK 를 구성하는 모든 컬럼에서 해제됩니다.';
        let html = baseHtml;
        if (deps.length > 0) {
          const list = deps.map(d =>
            '· ' + (d.objOwner ? d.objOwner + '.' : '') + d.tableNm + ' → ' + d.constraintNm
          ).join('<br>');
          html += '<br><br><div style="text-align:left; padding:8px; background:#FFEBEE; border-left:3px solid #C62828; font-size:.86rem;">'
            + '❌ 아래 외부 FK 가 이 PK 를 참조 중입니다 (총 ' + deps.length + '건):<br><br>' + list
            + '<br><br>PK 해제 시 위 FK 도 함께 삭제됩니다.</div>';
        }
        this.$swal.fire({
          title: 'PK 삭제', html, icon: 'warning', showCancelButton: true,
          confirmButtonText: deps.length > 0 ? 'PK + FK 모두 삭제' : 'PK 삭제',
          cancelButtonText: '취소',
          confirmButtonColor: deps.length > 0 ? '#C62828' : undefined,
        }).then(r => {
          if (!r.isConfirmed) return;
          axios.post(this.$APIURL.base + 'api/dm/deletePk', {
            dataModelId: this.selectedModelId,
            objOwner: item.objOwner || '',
            tableNm: item.objNm,
            pkName: info.name,
            cascadeFk: deps.length > 0,
          }).then(() => {
            this.$swal.fire({
              title: deps.length > 0 ? `PK + FK ${deps.length}건 삭제 완료` : 'PK 삭제 완료',
              icon: 'success', timer: 1500, showConfirmButton: false,
            });
            this.load();
          }).catch(() => this.$swal.fire({ title: 'PK 삭제 실패', icon: 'error' }));
        });
      }).catch(() => this.$swal.fire({ title: 'PK 종속 FK 조회 실패', icon: 'error' }));
    },

    // ===== FK 모달 =====
    openFkDialog(item) {
      this.fkForm = {
        dataModelId: this.selectedModelId,
        objOwner: item.objOwner || '',
        objNm: item.objNm,
        fkName: '',
        refOwner: '',
        refTableNm: '',
        refTableKey: null,
        mappings: [{ ownAttrNm: item.attrNm, refAttrNm: null }],
      };
      this.fkRefColumnOptions = [];
      this.fkDialog = true;
    },
    onRefTableChange(key) {
      const tgt = (this.objOptions || []).find(o => o.key === key);
      if (!tgt) { this.fkForm.refOwner = ''; this.fkForm.refTableNm = ''; this.fkRefColumnOptions = []; return; }
      this.fkForm.refOwner = tgt.objOwner || '';
      this.fkForm.refTableNm = tgt.objNm;
      // 참조 테이블의 컬럼 옵션 — dmColumnAllItems 에서 같은 테이블 의 ATTR 추출
      this.fkRefColumnOptions = (this.dmColumnAllItems || [])
        .filter(r => r.objNm === tgt.objNm && (r.objOwner || '') === (tgt.objOwner || ''))
        .map(r => this._toColumnOption(r));
    },
    _toColumnOption(r) {
      const en = (r.attrNm || '').trim();
      const kr = (r.attrNmKr || '').trim();
      let text;
      if (kr && en) text = kr + ' (' + en + ')';
      else if (kr)  text = kr;
      else          text = en;
      return { text: text, value: en };
    },
    submitFk() {
      const maps = (this.fkForm.mappings || []).filter(m => m.ownAttrNm && m.refAttrNm);
      if (maps.length === 0) {
        this.$swal.fire({ title: '컬럼 매핑을 1개 이상 채워주세요.', icon: 'warning' }); return;
      }
      if (!this.fkForm.refTableNm) {
        this.$swal.fire({ title: '참조 테이블을 선택하세요.', icon: 'warning' }); return;
      }
      // 87-x — FK DBMS 호환성 사전 검증. 모든 위반을 한 번에 모달로 노출.
      const warnings = this._validateFkMappings(maps);
      const proceed = () => {
        axios.post(this.$APIURL.base + 'api/dm/createFk', {
          dataModelId: this.fkForm.dataModelId,
          objOwner: this.fkForm.objOwner,
          tableNm: this.fkForm.objNm,
          fkName: this.fkForm.fkName,
          refOwner: this.fkForm.refOwner,
          refTableNm: this.fkForm.refTableNm,
          mappings: maps,
        }).then((res) => {
          if (res.data.resultCode === 200) {
            this.$swal.fire({ title: 'FK 생성 완료', text: 'FK 이름: ' + res.data.contents,
              icon: 'success', timer: 1500, showConfirmButton: false });
            this.fkDialog = false;
            this.load();
          } else {
            this.$swal.fire({ title: 'FK 생성 실패', text: res.data.resultMessage || '', icon: 'error' });
          }
        }).catch((err) => {
          const msg = (err.response && err.response.data && err.response.data.resultMessage) || '서버 오류';
          this.$swal.fire({ title: 'FK 생성 실패', text: msg, icon: 'error' });
        });
      };
      if (warnings.length === 0) { proceed(); return; }
      const html = '<div style="text-align:left; font-size:.88rem; line-height:1.55;">'
        + '<div style="padding:8px; background:#FFEBEE; border-left:3px solid #C62828; margin-bottom:10px;">'
        + '❌ 아래 항목은 DBMS 에서 FK 생성이 실패하거나 데이터 잘림이 발생할 수 있어 진행할 수 없습니다.<br>'
        + '모델을 수정한 뒤 다시 시도하세요.'
        + '</div>'
        + warnings.map(w => '<div style="margin-bottom:4px;">· ' + w + '</div>').join('')
        + '</div>';
      this.$swal.fire({
        title: 'FK 생성 불가 (' + warnings.length + '건)',
        html, icon: 'error',
        confirmButtonText: '확인', confirmButtonColor: '#C62828',
      });
    },
    // 87-x — FK 매핑 DBMS 호환성 검증. 위반 메시지 배열 반환 (빈 배열 = 문제 없음).
    _validateFkMappings(maps) {
      const w = [];
      const ownerKey = this.fkForm.objOwner || '';
      const objNm = this.fkForm.objNm;
      const refOwnerKey = this.fkForm.refOwner || '';
      const refObjNm = this.fkForm.refTableNm;
      // 부모 PK 정보 — constraintsByKey 에서 조회. 키 형식: 'owner|objNm' (fetchConstraints 와 동일)
      const refKey = refOwnerKey + '|' + refObjNm;
      const refConstraints = this.constraintsByKey[refKey] || {};
      const refPk = refConstraints.pk;
      const pkCols = (refPk && refPk.columns) || [];
      // 부모 컬럼 사전 — attr_nm → row
      const refAttrByNm = {};
      (this.dmColumnAllItems || []).forEach(r => {
        if ((r.objOwner || '') === refOwnerKey && r.objNm === refObjNm) {
          refAttrByNm[r.attrNm] = r;
        }
      });
      const ownAttrByNm = {};
      (this.dmColumnAllItems || []).forEach(r => {
        if ((r.objOwner || '') === ownerKey && r.objNm === objNm) {
          ownAttrByNm[r.attrNm] = r;
        }
      });
      // 1) 복합 PK 컬럼 수 vs FK 매핑 수
      if (pkCols.length > 0 && pkCols.length !== maps.length) {
        w.push('부모 테이블 PK 컬럼 수(' + pkCols.length + ': ' + pkCols.join(', ')
          + ') 와 FK 매핑 수(' + maps.length + ') 불일치');
      }
      // 2) 부모 컬럼이 PK 의 일부인지 — PK 자체가 없으면 별도 경고
      if (pkCols.length === 0) {
        w.push('부모 테이블 (' + refObjNm + ') 에 PK 가 정의되어있지 않음 — FK 참조 컬럼은 PK 또는 UNIQUE 여야 함');
      } else {
        maps.forEach(m => {
          if (pkCols.indexOf(m.refAttrNm) < 0) {
            w.push('참조 컬럼 ' + refObjNm + '.' + m.refAttrNm + ' 가 부모 PK 의 일부가 아님');
          }
        });
      }
      // 3) 매핑별 타입 / 길이 비교
      maps.forEach(m => {
        const c = ownAttrByNm[m.ownAttrNm];
        const p = refAttrByNm[m.refAttrNm];
        if (!c) { w.push('자기 컬럼 ' + m.ownAttrNm + ' 정보 없음'); return; }
        if (!p) { w.push('참조 컬럼 ' + m.refAttrNm + ' 정보 없음'); return; }
        const ct = (c.dataType || '').toUpperCase();
        const pt = (p.dataType || '').toUpperCase();
        if (ct && pt && ct !== pt) {
          w.push(m.ownAttrNm + ' (' + ct + ') ↔ ' + refObjNm + '.' + m.refAttrNm + ' (' + pt + ') — 타입 불일치');
        }
        const cl = Number(c.dataLen || 0);
        const pl = Number(p.dataLen || 0);
        if (cl && pl && cl !== pl) {
          const severe = cl < pl ? ' (자식이 더 짧음 — 데이터 잘림 위험)' : '';
          w.push(m.ownAttrNm + ' 길이(' + cl + ') ↔ ' + refObjNm + '.' + m.refAttrNm + ' 길이(' + pl + ') — 길이 불일치' + severe);
        }
        const cd = Number(c.dataDecimalLen || 0);
        const pd = Number(p.dataDecimalLen || 0);
        if (cd !== pd) {
          w.push(m.ownAttrNm + ' 소수점(' + cd + ') ↔ ' + refObjNm + '.' + m.refAttrNm + ' 소수점(' + pd + ') — 소수점 불일치');
        }
      });
      return w;
    },
    confirmDeleteFk(item) {
      const info = this.getFkInfo(item);
      if (!info) return;
      this.$swal.fire({
        title: 'FK 삭제',
        html: '<b>' + info.name + '</b><br>' + info.refTableNm + '.' + info.refColumnNm + ' 참조 해제',
        icon: 'warning', showCancelButton: true,
        confirmButtonText: '삭제', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/dm/deleteFk', {
          dataModelId: this.selectedModelId,
          objOwner: item.objOwner || '',
          tableNm: item.objNm,
          fkName: info.name,
        }).then(() => {
          this.$swal.fire({ title: 'FK 삭제 완료', icon: 'success', timer: 1200, showConfirmButton: false });
          this.load();
        }).catch(() => this.$swal.fire({ title: 'FK 삭제 실패', icon: 'error' }));
      });
    },
    _mapColumnData(data) {
      return data.map(item => {
        // 86번 #11 — PK/FK 는 제약 없는 컬럼에서 NULL 로 들어옴.
        // 예전엔 _orig 만 default 채우고 item 본체는 raw 라
        // (item.pkYn || '') !== (item._orig.pkYn || 'N') → 항상 dirty 로 잡힘.
        // item·_orig 양쪽 동일 default 적용해서 baseline 일치.
        const nullableYn = item.nullableYn || 'Y';
        const pkYn       = item.pkYn       || 'N';
        const fkYn       = item.fkYn       || 'N';
        const defaultVal = item.defaultVal || '';
        const attrNmKr   = item.attrNmKr   || '';
        const attrNm     = item.attrNm     || '';
        const dataType   = item.dataType   || '';
        const dataLen    = item.dataLen    || 0;
        const dataDecimalLen = item.dataDecimalLen || 0;
        const attrOrder  = item.attrOrder  || 0;
        return {
          attrId: item.attrId,
          objOwner: item.objOwner, objNm: item.objNm, objNmKr: item.objNmKr,
          attrNm, attrNmKr,
          dataType, dataLen, dataDecimalLen,
          attrOrder,
          nullableYn, termsStndYn: item.termsStndYn, domainStndYn: item.domainStndYn,
          pkYn, fkYn, defaultVal,
          clctId: item.clctId, dataModelId: item.dataModelId,
          _rowKey: 's_' + item.objNm + '_' + item.attrNm,
          _mode: 'saved',
          _resolveReason: null,
          _orig: { attrNmKr, nullableYn, pkYn, fkYn, defaultVal,
                   attrNm, dataType, dataLen, dataDecimalLen, attrOrder },
        };
      });
    },
    // 추가 대상 변경 시 검색 필터(소유자/테이블 영문/한글)를 그 테이블로 자동 세팅 — 사용자가 즉시 그 테이블 컬럼만 보게
    onAddTargetChange(key) {
      if (!key) return;
      const tgt = (this.objOptions || []).find(o => o.key === key);
      if (!tgt) return;
      this.searchOwner = tgt.objOwner || '';
      this.searchOwnerMode = 'exact';
      this.searchTable = tgt.objNm || '';
      this.searchTableMode = 'exact';
      this.searchTableKr = tgt.objNmKr || '';
      this.searchTableKrMode = tgt.objNmKr ? 'exact' : 'contains';
    },
    isRowDirty(item) {
      if (!item || item._mode === 'add' || !item._orig) return false;
      const o = item._orig;
      return (item.attrNmKr || '')   !== (o.attrNmKr   || '')
          || (item.nullableYn || '') !== (o.nullableYn || '')
          || (item.pkYn || '')       !== (o.pkYn       || '')
          || (item.fkYn || '')       !== (o.fkYn       || '')
          || (item.defaultVal || '') !== (o.defaultVal || '')
          || (item.attrNm || '')     !== (o.attrNm     || '')
          || (item.dataType || '')   !== (o.dataType   || '')
          || Number(item.dataLen || 0)        !== Number(o.dataLen || 0)
          || Number(item.dataDecimalLen || 0) !== Number(o.dataDecimalLen || 0)
          || Number(item.attrOrder || 0)      !== Number(o.attrOrder || 0);
    },
    loadObjOptions() {
      if (!this.selectedModelId) { this.objOptions = []; return; }
      axios.get(this.$APIURL.base + "api/dm/getDataModelObjListByClctId", {
        params: { 'clctId': this.selectedModelId }
      }).then((res) => {
        // 86번 #11 — 같은 obj_nm 다른 owner 분리 위해 unique key + 표시 형식 'OWNER.OBJ_NM (한글명)'
        this.objOptions = (res.data || []).map(o => {
          const owner  = o.objOwner || '';
          const objNm  = o.objNm;
          const qualified = (owner ? owner + '.' : '') + objNm;
          const label  = o.objNmKr ? qualified + ' (' + o.objNmKr + ')' : qualified;
          return {
            key: owner + '' + objNm,   // unique 식별자 (v-model 값)
            objNm,
            objOwner: owner,
            objNmKr: o.objNmKr || '',
            label,
          };
        });
      }).catch(() => { this.objOptions = []; });
    },
    _makeBlankRow(targetObj, seq) {
      const ts = Date.now() + '_' + seq;
      return {
        _rowKey: 'n_' + ts,
        _mode: 'add',
        _error: false,
        _resolveReason: null,
        objOwner: targetObj.objOwner || '',  // 86번 #11 — 부모 OBJ 의 OWNER 자동 상속
        objNm: targetObj.objNm,
        objNmKr: targetObj.objNmKr || '',
        attrNm: '',
        attrNmKr: '',
        dataType: '',
        dataLen: null,
        dataDecimalLen: null,
        nullableYn: 'Y',
        pkYn: 'N',
        fkYn: 'N',
        defaultVal: '',
        termsStndYn: 'N',
      };
    },
    addEmptyRow() {
      if (!this.addTargetKey) {
        this.$swal.fire({ title: '추가 대상 테이블을 선택하세요.', icon: 'warning' });
        return;
      }
      if (this.newRows.length >= 100) return;
      const targetObj = this.objOptions.find(o => o.key === this.addTargetKey) || { objNm: this.addTargetKey, objOwner: '', label: '' };
      this._pushBlankRowAtPosition(targetObj, this.addPosition);
      this.page = 1;
    },
    // 87-x — 같은 테이블의 (saved + new) 최대 attrOrder + 1 (pendingDeletes 는 이미 dmColumnAllItems 에서 빠져있어 invisible)
    _nextOrderForTarget(targetObj) {
      const ownerKey = (targetObj.objOwner || '');
      const objNm = targetObj.objNm;
      let max = 0;
      (this.dmColumnAllItems || []).forEach(it => {
        if ((it.objOwner || '') !== ownerKey || it.objNm !== objNm) return;
        const v = Number(it.attrOrder) || 0;
        if (v > max) max = v;
      });
      this.newRows.forEach(r => {
        if ((r.objOwner || '') !== ownerKey || r.objNm !== objNm) return;
        const v = Number(r.attrOrder) || 0;
        if (v > max) max = v;
      });
      return max + 1;
    },
    // 87-x — addPosition (top / bottom / afterChecked) 에 따라 신규 행 attrOrder 결정 + push.
    // top / afterChecked 는 후행 saved+new 행을 +1 시프트 (saved 는 dirty 표시)
    _pushBlankRowAtPosition(targetObj, position) {
      const ownerKey = (targetObj.objOwner || '');
      const objNm = targetObj.objNm;
      let insertAt;
      if (position === 'bottom') {
        insertAt = this._nextOrderForTarget(targetObj);
      } else if (position === 'afterChecked') {
        const sameTableChecks = (this.selectedRows || []).filter(r =>
          (r.objOwner || '') === ownerKey && r.objNm === objNm);
        if (sameTableChecks.length === 0) {
          this.$swal.fire({ title: '컬럼을 한 개 체크하세요.', icon: 'warning' });
          return;
        }
        if (sameTableChecks.length > 1) {
          this.$swal.fire({
            title: '한 개만 체크하세요.',
            text: `현재 ${sameTableChecks.length}개 선택됨`,
            icon: 'warning',
          });
          return;
        }
        insertAt = (Number(sameTableChecks[0].attrOrder) || 0) + 1;
      } else { // top
        insertAt = 1;
      }
      // 후행 시프트 (bottom 은 시프트 불필요)
      if (position !== 'bottom') {
        (this.dmColumnAllItems || []).forEach(it => {
          if ((it.objOwner || '') !== ownerKey || it.objNm !== objNm) return;
          const cur = Number(it.attrOrder) || 0;
          if (cur >= insertAt) this.$set(it, 'attrOrder', cur + 1);
        });
        this.newRows.forEach(r => {
          if ((r.objOwner || '') !== ownerKey || r.objNm !== objNm) return;
          const cur = Number(r.attrOrder) || 0;
          if (cur >= insertAt) r.attrOrder = cur + 1;
        });
      }
      const row = this._makeBlankRow(targetObj, this.newRows.length);
      row.attrOrder = insertAt;
      this.newRows.push(row);
    },
    // 87-x — 미저장 변경 (newRows, pendingDeletes, dirty) 모두 버리고 DB 에서 새로 조회
    reloadDiscardChanges() {
      const dirty = this.dirtyCount;
      const total = this.newRows.length + this.pendingDeletes.length + dirty;
      if (total === 0) {
        this.load();
        return;
      }
      this.$swal.fire({
        title: '미저장 변경 버리고 새로 조회?',
        html: `<div style="text-align:left; font-size:.9rem; line-height:1.5;">`
          + (this.newRows.length > 0    ? `· 미저장 신규 ${this.newRows.length}건<br>`    : '')
          + (dirty > 0                  ? `· 수정중 ${dirty}건<br>`                      : '')
          + (this.pendingDeletes.length > 0 ? `· 삭제 대기 ${this.pendingDeletes.length}건<br>` : '')
          + `</div>`,
        icon: 'warning', showCancelButton: true,
        confirmButtonText: '버리고 새로 조회', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        this.newRows = [];
        this.pendingDeletes = [];
        this.selectedRows = [];
        this.load();
      });
    },
    // 87-x — 현재 그리드 표시 순서대로 (같은 추가 대상 테이블의 행만) attrOrder 1..N 재할당.
    // v-data-table 헤더 클릭 정렬 (userSortBy / userSortDesc) 도 반영.
    renumberByDisplayOrder() {
      if (!this.addTargetKey) return;
      const targetObj = this.objOptions.find(o => o.key === this.addTargetKey);
      if (!targetObj) return;
      const ownerKey = targetObj.objOwner || '';
      const objNm = targetObj.objNm;
      // 기본 정렬 결과 (mergedItems) 에서 추가 대상 행만 추출 후, 사용자가 헤더 정렬을 잡았으면 그 순서로 재정렬
      let targetRows = (this.mergedItems || []).filter(r =>
        (r.objOwner || '') === ownerKey && r.objNm === objNm
      );
      const sortKeys = (this.userSortBy && this.userSortBy.length) ? this.userSortBy : [];
      if (sortKeys.length > 0) {
        const descs = this.userSortDesc || [];
        targetRows = targetRows.slice().sort((a, b) => {
          for (let i = 0; i < sortKeys.length; i++) {
            const k = sortKeys[i];
            const desc = !!descs[i];
            const va = a[k], vb = b[k];
            let cmp = 0;
            if (va == null && vb == null) cmp = 0;
            else if (va == null) cmp = -1;
            else if (vb == null) cmp = 1;
            else if (typeof va === 'number' && typeof vb === 'number') cmp = va - vb;
            else cmp = String(va).localeCompare(String(vb));
            if (cmp !== 0) return desc ? -cmp : cmp;
          }
          return 0;
        });
      }
      if (targetRows.length === 0) {
        this.$swal.fire({ title: '대상 행이 없습니다.', icon: 'info' });
        return;
      }
      let ord = 1;
      targetRows.forEach(r => {
        if (r._mode === 'add') r.attrOrder = ord;
        else this.$set(r, 'attrOrder', ord);
        ord++;
      });
      this.$swal.fire({
        title: `${targetRows.length}건 순서 재할당`,
        text: '저장 버튼을 눌러야 최종 반영됩니다.',
        icon: 'success', timer: 1500, showConfirmButton: false,
      });
    },
    addEmptyRows(n) {
      if (!this.addTargetKey) {
        this.$swal.fire({ title: '추가 대상 테이블을 선택하세요.', icon: 'warning' });
        return;
      }
      const targetObj = this.objOptions.find(o => o.key === this.addTargetKey) || { objNm: this.addTargetKey, objOwner: '', label: '' };
      const remain = 100 - this.newRows.length;
      const add = Math.min(n, remain);
      if (add <= 0) { this.page = 1; return; }
      const ownerKey = targetObj.objOwner || '';
      const objNm = targetObj.objNm;
      // 87-x — addPosition 반영: insertAt 결정 후 후행 시프트 (add 만큼) → 연속 attrOrder 부여
      let insertAt;
      if (this.addPosition === 'bottom') {
        insertAt = this._nextOrderForTarget(targetObj);
      } else if (this.addPosition === 'afterChecked') {
        const checks = (this.selectedRows || []).filter(r =>
          (r.objOwner || '') === ownerKey && r.objNm === objNm);
        if (checks.length === 0) {
          this.$swal.fire({ title: '컬럼을 한 개 체크하세요.', icon: 'warning' }); return;
        }
        if (checks.length > 1) {
          this.$swal.fire({ title: '한 개만 체크하세요.', text: `현재 ${checks.length}개 선택됨`, icon: 'warning' }); return;
        }
        insertAt = (Number(checks[0].attrOrder) || 0) + 1;
      } else { // top
        insertAt = 1;
      }
      if (this.addPosition !== 'bottom') {
        (this.dmColumnAllItems || []).forEach(it => {
          if ((it.objOwner || '') !== ownerKey || it.objNm !== objNm) return;
          const cur = Number(it.attrOrder) || 0;
          if (cur >= insertAt) this.$set(it, 'attrOrder', cur + add);
        });
        this.newRows.forEach(r => {
          if ((r.objOwner || '') !== ownerKey || r.objNm !== objNm) return;
          const cur = Number(r.attrOrder) || 0;
          if (cur >= insertAt) r.attrOrder = cur + add;
        });
      }
      for (let i = 0; i < add; i++) {
        const row = this._makeBlankRow(targetObj, this.newRows.length);
        row.attrOrder = insertAt + i;
        this.newRows.push(row);
      }
      this.page = 1;
    },
    _parseBool(v) {
      if (v === undefined || v === null) return null;
      const s = String(v).trim().toUpperCase();
      if (s === '' ) return null;
      if (['Y', 'YES', 'TRUE', 'T', '1', '✓', 'O'].indexOf(s) >= 0) return 'Y';
      if (['N', 'NO', 'FALSE', 'F', '0', 'X'].indexOf(s) >= 0) return 'N';
      return null;
    },
    onPaste(e) {
      // 그리드 편집 컨텍스트가 아닐 때는 무시 (모델·타겟 테이블 없음)
      if (!this.selectedModelId || !this.addTargetKey) return;
      // 입력 중인 일반 input/textarea 포커스에선 기본 동작 유지
      const tag = (e.target && e.target.tagName) || '';
      const isInput = tag === 'INPUT' || tag === 'TEXTAREA';
      if (isInput) return;
      const cd = e.clipboardData || window.clipboardData;
      if (!cd) return;
      const text = cd.getData('text');
      if (!text) return;
      // TSV 파싱: 개행 → 행, \t → 열
      const rows = text.replace(/\r\n?/g, '\n').split('\n').filter(r => r.length > 0);
      if (rows.length === 0) return;
      e.preventDefault();
      const targetObj = this.objOptions.find(o => o.key === this.addTargetKey) || { objNm: this.addTargetKey, objOwner: '', label: '' };
      const remain = 100 - this.newRows.length;
      const toAdd = Math.min(rows.length, remain);
      let added = 0;
      for (let i = 0; i < toAdd; i++) {
        const cols = rows[i].split('\t');
        const attrNmKr = (cols[0] || '').trim();
        if (!attrNmKr) continue;
        const row = this._makeBlankRow(targetObj, this.newRows.length + added);
        row.attrNmKr = attrNmKr;
        // 열 순서: 한글명, NULL, PK, FK, 기본값
        const nullable = this._parseBool(cols[1]);
        const pk = this._parseBool(cols[2]);
        const fk = this._parseBool(cols[3]);
        if (nullable) row.nullableYn = nullable;
        if (pk) row.pkYn = pk;
        if (fk) row.fkYn = fk;
        if (pk === 'Y') row.nullableYn = 'N';
        if (cols[4] !== undefined) row.defaultVal = cols[4].trim();
        row.attrOrder = this._nextOrderForTarget(targetObj);
        this.newRows.push(row);
        added++;
      }
      if (added > 0) {
        this.$swal.fire({
          title: `${added}행 붙여넣기 완료`, icon: 'success', timer: 1200, showConfirmButton: false,
        });
      }
      if (rows.length > remain) {
        this.$swal.fire({
          title: '최대 100행 제한 초과', text: `${rows.length - remain}행은 잘림. 대량은 엑셀 업로드 사용.`, icon: 'warning',
        });
      }
    },
    onCellPaste(targetItem, field, e) {
      // 신규 행의 inline input paste — 여러 줄이면 같은 컬럼(field)의 행들에 분배
      // 호출 시 field 미지정이면 'attrNmKr' 로 fallback (이전 호환)
      if (typeof field !== 'string') { e = field; field = 'attrNmKr'; }
      const cd = e.clipboardData || window.clipboardData;
      if (!cd) return;
      const text = cd.getData('text') || '';
      if (!/\r|\n/.test(text)) return;  // 단일 행은 기본 paste
      e.preventDefault();
      const startIdx = this.newRows.findIndex(r => r._rowKey === targetItem._rowKey);
      if (startIdx < 0) {
        this.$swal.fire({ title: '미저장 행에서만 멀티 paste 가능', icon: 'info', timer: 1500, showConfirmButton: false });
        return;
      }
      const lines = text.replace(/\r\n?/g, '\n').split('\n').map(s => s.trim()).filter(s => s !== '');
      if (lines.length === 0) return;
      const numericFields = new Set(['dataLen', 'dataDecimalLen', 'attrOrder']);
      const upperFields   = new Set(['attrNm', 'dataType']);
      const totalCap = 100;
      let appliedCnt = 0;
      let truncated = 0;
      const targetObj = this.objOptions.find(o => o.key === this.addTargetKey)
        || { objNm: this.addTargetKey, objOwner: '', label: '' };
      for (let i = 0; i < lines.length; i++) {
        const idx = startIdx + i;
        if (this.newRows.length >= totalCap && idx >= this.newRows.length) {
          truncated = lines.length - i;
          break;
        }
        if (idx >= this.newRows.length) {
          this.newRows.push(this._makeBlankRow(targetObj, this.newRows.length));
        }
        let val = lines[i];
        if (numericFields.has(field)) {
          const n = parseInt(val, 10);
          val = isNaN(n) ? 0 : n;
        } else if (upperFields.has(field)) {
          val = val.toUpperCase();
        }
        this.$set(this.newRows[idx], field, val);
        appliedCnt++;
      }
      if (appliedCnt > 0) {
        this.$swal.fire({
          title: `${appliedCnt}행 분배 완료`, icon: 'success', timer: 1200, showConfirmButton: false,
        });
      }
      if (truncated > 0) {
        this.$swal.fire({
          title: '최대 100행 제한 초과',
          text: `${truncated}행은 잘림. 대량은 엑셀 업로드 사용.`,
          icon: 'warning',
        });
      }
    },
    removeNewRow(item) {
      this.newRows = this.newRows.filter(r => r._rowKey !== item._rowKey);
      this.selectedRows = this.selectedRows.filter(r => r._rowKey !== item._rowKey);
    },
    deleteOne(item) {
      this.$swal.fire({
        title: '컬럼을 삭제하시겠습니까?', text: `${item.objNm}.${item.attrNm}`, icon: 'warning',
        showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
      }).then((r) => {
        if (!r.isConfirmed) return;
        this.pendingDeletes.push({ objNm: item.objNm, attrNm: item.attrNm });
        this.dmColumnAllItems = this.dmColumnAllItems.filter(x => x._rowKey !== item._rowKey);
      });
    },
    deleteSelected() {
      const savedSel = this.selectedRows.filter(r => r._mode === 'saved');
      const newSel = this.selectedRows.filter(r => r._mode === 'add');
      if (savedSel.length + newSel.length === 0) return;

      const apply = (cascadeHtml) => {
        const title = `${savedSel.length + newSel.length}건을 삭제하시겠습니까?`;
        const baseText = '저장 버튼을 눌러야 최종 반영됩니다.';
        this.$swal.fire({
          title,
          html: cascadeHtml
            ? `<div style="text-align:left; font-size:.85rem; line-height:1.5;">${baseText}<br><br>${cascadeHtml}</div>`
            : baseText,
          icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소'
        }).then(r => {
          if (!r.isConfirmed) return;
          newSel.forEach(n => this.removeNewRow(n));
          savedSel.forEach(s => {
            this.pendingDeletes.push({ objNm: s.objNm, attrNm: s.attrNm });
            this.dmColumnAllItems = this.dmColumnAllItems.filter(x => x._rowKey !== s._rowKey);
          });
          this.selectedRows = [];
        });
      };

      // 저장된 컬럼만 ref 사전 조회 (신규 행은 DB 영향 없음)
      if (savedSel.length === 0) { apply(''); return; }
      Promise.all(savedSel.map(s =>
        axios.post(this.$APIURL.base + 'api/dm/getAttrReferences', {
          dataModelId: this.selectedModelId,
          objOwner: s.objOwner || '',
          objNm: s.objNm, attrNm: s.attrNm,
        }).then(res => ({ s, refs: res.data || {} })).catch(() => ({ s, refs: {} }))
      )).then(results => {
        const lines = [];
        results.forEach(({ s, refs }) => {
          const own = (refs.ownConstraints || []).map(c => `${c.constraintNm} (${c.constraintType})`);
          const idx = (refs.ownIndexes || []).map(c => c.indexNm);
          const inb = (refs.inboundFks || []).map(c => `${c.objOwner ? c.objOwner + '.' : ''}${c.tableNm}.${c.columnNm} → ${c.constraintNm}`);
          const fkp = (refs.fkParentAttrs || []).map(c => `${c.objOwner ? c.objOwner + '.' : ''}${c.objNm}.${c.attrNm}`);
          if (own.length + idx.length + inb.length + fkp.length === 0) return;
          const parts = [];
          if (own.length) parts.push(`자기 테이블 제약조건: <b>${own.join(', ')}</b>`);
          if (idx.length) parts.push(`자기 테이블 인덱스: <b>${idx.join(', ')}</b>`);
          if (inb.length) parts.push(`<span style="color:#D32F2F;">외부 FK 참조: <b>${inb.join(', ')}</b></span>`);
          if (fkp.length) parts.push(`<span style="color:#D32F2F;">FK_PARENT 가리키는 컬럼: <b>${fkp.join(', ')}</b></span>`);
          lines.push(`<div style="margin-bottom:6px;"><b>${s.objNm}.${s.attrNm}</b><br>&nbsp;&nbsp;${parts.join('<br>&nbsp;&nbsp;')}</div>`);
        });
        if (lines.length === 0) { apply(''); return; }
        const html = `<div style="padding:8px; background:#FFF3E0; border-left:3px solid #FB8C00;">⚠ 아래 참조도 함께 삭제됩니다:<br><br>${lines.join('')}</div>`;
        apply(html);
      });
    },
    _validateNewRows() {
      this._dupErrorList = [];
      let ok = true;
      // 1) 빈값 검사 — 86번 #11: 한글명 또는 영문명(물리) 둘 중 하나는 필수, objNm(테이블) 필수
      this.newRows.forEach(r => {
        const krEmpty = !r.attrNmKr || !r.attrNmKr.trim();
        const enEmpty = !r.attrNm || !r.attrNm.trim();
        r._error = (krEmpty && enEmpty) || !r.objNm;
        if (r._error) ok = false;
      });
      // dirty 수정 행: 영문명 또는 한글명 둘 다 빈값이면 오류
      const dirtyItems = (this.dmColumnAllItems || []).filter(it => this.isRowDirty(it));
      dirtyItems.forEach(r => {
        const krEmpty = !r.attrNmKr || !r.attrNmKr.trim();
        const enEmpty = !r.attrNm || !r.attrNm.trim();
        r._error = krEmpty && enEmpty;
        if (r._error) ok = false;
      });
      // 2) 한글명 중복 검사 — 같은 테이블(objNm) 내에서
      // 2-1) 변경되는 모든 행 (newRows + dirty) 내부 중복
      const seen = {}; // key: objNm|attrNmKr → first row 참조
      const changing = [
        ...this.newRows.filter(r => r.attrNmKr && r.objNm),
        ...dirtyItems.filter(r => r.attrNmKr && r.objNm),
      ];
      changing.forEach(r => {
        const key = r.objNm + '|' + r.attrNmKr.trim();
        if (seen[key]) {
          r._error = true;
          seen[key]._error = true;
          this._dupErrorList.push(`${r.objNmKr || r.objNm} 테이블 : '${r.attrNmKr.trim()}' 중복 (입력/수정 행)`);
          ok = false;
        } else {
          seen[key] = r;
        }
      });
      // 2-2) 변경되지 않는 기존 행과의 중복 (dirty 가 아닌 saved 와 충돌)
      const existing = {};
      (this.dmColumnAllItems || []).forEach(it => {
        if (it._mode === 'add' || !it.attrNmKr || !it.objNm) return;
        if (this.isRowDirty(it)) return; // dirty 는 위에서 changing 으로 검사 중
        existing[it.objNm + '|' + (it.attrNmKr || '').trim()] = it;
      });
      changing.forEach(r => {
        const key = r.objNm + '|' + r.attrNmKr.trim();
        // dirty 행 자기 자신이 existing 에 있는 케이스는 제외 (자기 자신 비교가 됨 — 위에서 dirty 는 existing 에 안 들어감)
        if (existing[key]) {
          r._error = true;
          this._dupErrorList.push(`${r.objNmKr || r.objNm} 테이블 : '${r.attrNmKr.trim()}' 이미 등록됨`);
          ok = false;
        }
      });

      // 3) 영문명(ATTR_NM) 중복 검사 — 같은 테이블(objNm) + owner 내, 대소문자 무시 (저장시 UPPER)
      const changingEn = [
        ...this.newRows.filter(r => r.attrNm && r.objNm),
        ...dirtyItems.filter(r => r.attrNm && r.objNm),
      ];
      // 3-1) 입력/수정 행 내부 중복
      const seenEn = {};
      changingEn.forEach(r => {
        const key = (r.objOwner || '') + '|' + r.objNm + '|' + r.attrNm.trim().toUpperCase();
        if (seenEn[key]) {
          r._error = true;
          seenEn[key]._error = true;
          this._dupErrorList.push(`${r.objNmKr || r.objNm} 테이블 : 영문명 '${r.attrNm.trim().toUpperCase()}' 중복 (입력/수정 행)`);
          ok = false;
        } else {
          seenEn[key] = r;
        }
      });
      // 3-2) 변경되지 않는 기존 행과의 영문명 중복
      const existingEn = {};
      (this.dmColumnAllItems || []).forEach(it => {
        if (it._mode === 'add' || !it.attrNm || !it.objNm) return;
        if (this.isRowDirty(it)) return;
        existingEn[(it.objOwner || '') + '|' + it.objNm + '|' + (it.attrNm || '').trim().toUpperCase()] = it;
      });
      changingEn.forEach(r => {
        const key = (r.objOwner || '') + '|' + r.objNm + '|' + r.attrNm.trim().toUpperCase();
        if (existingEn[key]) {
          r._error = true;
          this._dupErrorList.push(`${r.objNmKr || r.objNm} 테이블 : 영문명 '${r.attrNm.trim().toUpperCase()}' 이미 등록됨`);
          ok = false;
        }
      });
      return ok;
    },
    _groupByObj(rows) {
      // 86번 #11 — PK 가 (DM_ID, OBJ_OWNER, OBJ_NM) 이라 같은 OBJ_NM 다른 OWNER 가능 → key 에 owner 포함
      const groups = {};
      rows.forEach(r => {
        const owner = r.objOwner || '';
        const k = owner + '' + r.objNm;
        if (!groups[k]) groups[k] = { objOwner: owner, objNm: r.objNm, rows: [] };
        groups[k].rows.push(r);
      });
      return groups;
    },
    saveAll() {
      // 영문명(ATTR_NM) 변경된 UPDATE 행이 있으면 cascade 안내 confirm
      const dirtyForCheck = (this.dmColumnAllItems || []).filter(it => this.isRowDirty(it));
      const renamed = dirtyForCheck.filter(r => r._orig && (r.attrNm || '') !== (r._orig.attrNm || ''));
      if (renamed.length > 0) {
        const list = renamed.slice(0, 6).map(r =>
          '· ' + r.objNm + ': ' + (r._orig.attrNm || '') + ' → ' + (r.attrNm || '')).join('<br>');
        const more = renamed.length > 6 ? '<br>외 ' + (renamed.length - 6) + '건' : '';
        this.$swal.fire({
          title: '영문명 변경 cascade 확인',
          html: '<div style="text-align:left; font-size:.88rem; line-height:1.55;">'
              + '<b>' + renamed.length + '건</b>의 컬럼 영문명이 변경됩니다:<br><br>'
              + list + more
              + '<div style="margin-top:10px; padding:8px; background:#FFF3E0; border-left:3px solid #FB8C00; font-size:.82rem; color:#5D4037;">'
              + '⚠ 같은 모델 내 INDEX / CONSTRAINT / FK 참조도 함께 자동 갱신됩니다.<br>'
              + '표준/도메인 일치 플래그는 강등됩니다 (재변환 필요).'
              + '</div></div>',
          icon: 'warning',
          showCancelButton: true,
          confirmButtonText: '저장',
          cancelButtonText: '취소',
          confirmButtonColor: '#3949AB',
        }).then(r => { if (r.isConfirmed) this._doSaveAll(); });
        return;
      }
      this._doSaveAll();
    },
    _doSaveAll() {
      if (!this._validateNewRows()) {
        const dups = this._dupErrorList || [];
        if (dups.length > 0) {
          const list = dups.slice(0, 6).map(e => '· ' + e).join('<br>');
          const more = dups.length > 6 ? `<br>외 ${dups.length - 6}건` : '';
          this.$swal.fire({ title: '한글명 중복 — 같은 테이블 내 중복 불가', html: list + more, icon: 'error' });
        } else {
          this.$swal.fire({ title: '입력 오류', text: '빨간 행의 한글명을 채워주세요.', icon: 'warning' });
        }
        return;
      }
      const dirtyItems = (this.dmColumnAllItems || []).filter(it => this.isRowDirty(it));
      const addGroups = this._groupByObj(this.newRows);
      const delGroups = this._groupByObj(this.pendingDeletes);
      const updGroups = this._groupByObj(dirtyItems);
      // (owner, objNm) 조합 키 셋
      const allKeys = new Set([
        ...Object.keys(addGroups), ...Object.keys(delGroups), ...Object.keys(updGroups),
      ]);
      if (allKeys.size === 0) return;

      const requests = [];
      allKeys.forEach(k => {
        const meta = (addGroups[k] || delGroups[k] || updGroups[k]);
        const objNm = meta.objNm;
        const objOwner = meta.objOwner;
        const attrs = [];
        ((addGroups[k] && addGroups[k].rows) || []).forEach(r => attrs.push({
          mode: 'ADD', attrNmKr: r.attrNmKr,
          attrNm: r.attrNm, dataType: r.dataType, dataLen: r.dataLen, dataDecimalLen: r.dataDecimalLen,
          attrOrder: r.attrOrder,
          pkYn: r.pkYn, fkYn: r.fkYn,
          nullableYn: r.nullableYn, defaultVal: r.defaultVal,
        }));
        ((updGroups[k] && updGroups[k].rows) || []).forEach(r => attrs.push({
          mode: 'UPDATE',
          origAttrNm: (r._orig && r._orig.attrNm) || r.attrNm,  // PK 매칭용
          attrNm: r.attrNm,                                      // 새 영문명 (변경 시 cascade)
          attrNmKr: r.attrNmKr,
          dataType: r.dataType, dataLen: r.dataLen, dataDecimalLen: r.dataDecimalLen,
          attrOrder: r.attrOrder,
          pkYn: r.pkYn, fkYn: r.fkYn,
          nullableYn: r.nullableYn, defaultVal: r.defaultVal,
        }));
        ((delGroups[k] && delGroups[k].rows) || []).forEach(r => attrs.push({
          mode: 'DELETE', attrNm: r.attrNm,
        }));
        requests.push(axios.post(this.$APIURL.base + 'api/dm/saveAttrs', {
          dataModelId: this.selectedModelId, objNm, objOwner, attrs,
        }));
      });

      Promise.all(requests).then(responses => {
        const fail = responses.find(res => !(res.data && res.data.resultCode === 200));
        if (fail) {
          this.$swal.fire({
            title: '일부 저장 실패',
            text: (fail.data && fail.data.resultMessage) || '서버 오류',
            icon: 'error',
          });
          return;
        }
        const totalSaved = responses.reduce((sum, res) => {
          try {
            const c = res.data && res.data.contents;
            const parsed = typeof c === 'string' ? JSON.parse(c) : (c || {});
            return sum + (parsed.saved || 0);
          } catch (e) { return sum; }
        }, 0);
        this.$swal.fire({
          title: `${totalSaved}건 저장 완료`, icon: 'success', timer: 1500, showConfirmButton: false,
        });
        this.newRows = [];
        this.pendingDeletes = [];
        this.load();
      }).catch((err) => {
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '저장 실패';
        this.$swal.fire({ title: '저장 실패', text: msg, icon: 'error' });
      });
    },
    // 한글명 기준 표준화 — 사전 confirm
    resolveSelectedConfirm() {
      const saved = this.selectedRows.filter(r => r._mode === 'saved');
      if (saved.length === 0) {
        this.$swal.fire({ title: '변환할 저장된 컬럼이 없습니다.', text: '신규 행은 먼저 저장 후 변환하세요.', icon: 'info' });
        return;
      }
      this.$swal.fire({
        title: '한글명 기준 표준화',
        html: '<div style="text-align:left; font-size:.9rem; line-height:1.6;">'
            + '선택한 <b>' + saved.length + '건</b>의 컬럼을 처리합니다.<br><br>'
            + '<b>한글명</b> (예: 사용자ID) 으로 표준 용어를 검색해<br>'
            + '아래 항목을 자동으로 채웁니다:<br>'
            + '<ul style="margin:6px 0 6px 18px;">'
            + '<li><b>영문명</b> (물리, ATTR_NM)</li>'
            + '<li><b>데이터 타입 / 길이 / 소수점</b></li>'
            + '<li>표준/도메인 일치 플래그</li>'
            + '</ul>'
            + '<div style="margin-top:8px; padding:8px; background:#FFF3E0; border-left:3px solid #FB8C00; font-size:.85rem; color:#5D4037;">'
            + '⚠ 매칭되는 표준 용어가 없으면 해당 행은 실패로 표시됩니다.'
            + '</div></div>',
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '실행',
        cancelButtonText: '취소',
        confirmButtonColor: '#3949AB',
      }).then(r => { if (r.isConfirmed) this.resolveSelected(); });
    },
    resolveSelected() {
      const saved = this.selectedRows.filter(r => r._mode === 'saved');
      if (saved.length === 0) {
        this.$swal.fire({ title: '변환할 저장된 컬럼이 없습니다.', text: '신규 행은 먼저 저장 후 변환하세요.', icon: 'info' });
        return;
      }
      this.resolving = true;
      const payload = {
        dataModelId: this.selectedModelId,
        attrs: saved.map(r => ({
          objNm: r.objNm, attrNm: r.attrNm, attrNmKr: r.attrNmKr, objOwner: r.objOwner,
        })),
        dryRun: true,
      };
      axios.post(this.$APIURL.base + 'api/dm/resolveAttrs', payload).then((res) => {
        const data = res.data || {};
        const items = data.items || [];
        const reasonMap = {};
        (data.failedList || []).forEach(f => { reasonMap[f.objNm + '::' + f.attrNm] = f.reason; });
        // 응답 items 를 그리드 행에 직접 반영 (DB 업데이트 X — 저장 버튼 누를 때 일괄)
        items.forEach(it => {
          const row = this.dmColumnAllItems.find(r =>
            r._mode === 'saved' && r.objNm === it.objNm && r.attrNm === it.attrNm);
          if (!row) return;
          this.$set(row, 'attrNm',         it.newAttrNm);
          this.$set(row, 'attrNmKr',       it.newAttrNmKr);
          this.$set(row, 'dataType',       it.newDataType);
          this.$set(row, 'dataLen',        it.newDataLen);
          this.$set(row, 'dataDecimalLen', it.newDataDecimalLen);
          this.$set(row, '_resolveReason', null);
        });
        // 실패 사유 주입
        this.dmColumnAllItems.forEach(r => {
          const k = r.objNm + '::' + r.attrNm;
          if (reasonMap[k]) this.$set(r, '_resolveReason', reasonMap[k]);
        });
        this.$swal.fire({
          title: `변환 결과: ${data.succeeded || 0} / ${data.tried || 0}`,
          text: (data.failed || 0) > 0
                ? `실패 ${data.failed}건 — 그리드 "변환 불가 사유" 컬럼에 표시됩니다.`
                : '값이 그리드에 채워졌습니다. 저장 버튼을 눌러야 DB 에 반영됩니다.',
          icon: (data.failed || 0) > 0 ? 'warning' : 'success',
          timer: 2200, showConfirmButton: false,
        });
        this.resolving = false;
      }).catch((err) => {
        this.resolving = false;
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '변환 실패';
        this.$swal.fire({ title: '변환 실패', text: msg, icon: 'error' });
      });
    },
    // 영문명 기준 표준화 — 사전 confirm
    resolveByEngConfirm() {
      const saved = this.selectedRows.filter(r => r._mode === 'saved');
      if (saved.length === 0) {
        this.$swal.fire({ title: '변환할 저장된 컬럼이 없습니다.', text: '신규 행은 먼저 저장 후 변환하세요.', icon: 'info' });
        return;
      }
      this.$swal.fire({
        title: '영문명 기준 표준화',
        html: '<div style="text-align:left; font-size:.9rem; line-height:1.6;">'
            + '선택한 <b>' + saved.length + '건</b>의 컬럼을 처리합니다.<br><br>'
            + '<b>영문명</b> (예: USER_ID) 으로 표준 용어를 검색해<br>'
            + '아래 항목을 자동으로 채웁니다:<br>'
            + '<ul style="margin:6px 0 6px 18px;">'
            + '<li><b>한글명</b> (논리, ATTR_NM_KR)</li>'
            + '<li><b>데이터 타입 / 길이 / 소수점</b></li>'
            + '<li>표준/도메인 일치 플래그</li>'
            + '</ul>'
            + '<div style="margin-top:8px; padding:8px; background:#E3F2FD; border-left:3px solid #1976D2; font-size:.85rem; color:#1A237E;">'
            + 'ℹ 영문명은 그대로 유지됩니다.'
            + '</div>'
            + '<div style="margin-top:6px; padding:8px; background:#FFF3E0; border-left:3px solid #FB8C00; font-size:.85rem; color:#5D4037;">'
            + '⚠ 영문명이 표준 용어 사전에 없으면 해당 행은 실패로 표시됩니다.'
            + '</div></div>',
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '실행',
        cancelButtonText: '취소',
        confirmButtonColor: '#3949AB',
      }).then(r => { if (r.isConfirmed) this.resolveByEng(); });
    },
    resolveByEng() {
      const saved = this.selectedRows.filter(r => r._mode === 'saved');
      if (saved.length === 0) return;
      this.resolvingByEng = true;
      const payload = {
        dataModelId: this.selectedModelId,
        attrs: saved.map(r => ({
          objNm: r.objNm, attrNm: r.attrNm, attrNmKr: r.attrNmKr, objOwner: r.objOwner,
        })),
        dryRun: true,
      };
      axios.post(this.$APIURL.base + 'api/dm/resolveAttrsByEng', payload).then((res) => {
        const data = res.data || {};
        const items = data.items || [];
        const reasonMap = {};
        (data.failedList || []).forEach(f => { reasonMap[f.objNm + '::' + f.attrNm] = f.reason; });
        items.forEach(it => {
          const row = this.dmColumnAllItems.find(r =>
            r._mode === 'saved' && r.objNm === it.objNm && r.attrNm === it.attrNm);
          if (!row) return;
          this.$set(row, 'attrNm',         it.newAttrNm);
          this.$set(row, 'attrNmKr',       it.newAttrNmKr);
          this.$set(row, 'dataType',       it.newDataType);
          this.$set(row, 'dataLen',        it.newDataLen);
          this.$set(row, 'dataDecimalLen', it.newDataDecimalLen);
          this.$set(row, '_resolveReason', null);
        });
        this.dmColumnAllItems.forEach(r => {
          const k = r.objNm + '::' + r.attrNm;
          if (reasonMap[k]) this.$set(r, '_resolveReason', reasonMap[k]);
        });
        this.$swal.fire({
          title: '변환 결과: ' + (data.succeeded || 0) + ' / ' + (data.tried || 0),
          text: (data.failed || 0) > 0
                ? '실패 ' + data.failed + '건 — 그리드 "변환 불가 사유" 컬럼에 표시됩니다.'
                : '값이 그리드에 채워졌습니다. 저장 버튼을 눌러야 DB 에 반영됩니다.',
          icon: (data.failed || 0) > 0 ? 'warning' : 'success',
          timer: 2200, showConfirmButton: false,
        });
        this.resolvingByEng = false;
      }).catch((err) => {
        this.resolvingByEng = false;
        const msg = (err.response && err.response.data && err.response.data.resultMessage) || '변환 실패';
        this.$swal.fire({ title: '변환 실패', text: msg, icon: 'error' });
      });
    },
    showTermData(item) {
      this.termLoading = true;
      this.termDataModalShow = true;
      axios.get(this.$APIURL.base + "api/std/getTermsInfoByNm", {
        params: { 'termsNm': item.attrNmKr }
      }).then((res) => {
        this.termDetailItem = res.data;
        this.termLoading = false;
      }).catch(() => {
        this.$swal.fire({ title: '용어 검색 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
        this.termLoading = false;
      });
    },
    columnDataDownload() {
      axios.get(this.$APIURL.base + "api/dm/downloadDataModelAttrs", {
        params: { 'clctId': this.selectedModelId },
        responseType: 'blob',
        headers: { "Accept": "application/vnd.ms-excel" }
      }).then(response => {
        const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/csv" }));
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", `데이터모델_컬럼정보_${this.$getToday()}.xlsx`);
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(url);
        link.remove();
      }).catch(() => {
        this.$swal.fire({ title: '컬럼 정보 다운로드 실패 - API 확인 필요', confirmButtonText: '확인', icon: 'error' });
      });
    },
    // ===== 엑셀 업로드 =====
    downloadAttrsTemplate() {
      const url = this.$APIURL.base + 'api/dm/uploadTemplate?scope=attrs';
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'dataq_attrs_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    triggerUploadAttrs() {
      if (!this.selectedModelId) {
        this.$swal.fire({ title: '데이터모델을 먼저 선택하세요.', icon: 'warning' });
        return;
      }
      this.$refs.uploadAttrsInput.value = '';
      this.$refs.uploadAttrsInput.click();
    },
    onAttrFileSelected(e) {
      const f = e.target.files && e.target.files[0];
      if (!f) return;
      this.uploadFile = f;
      this._runAttrsUpload('preview');
    },
    _runAttrsUpload(mode) {
      if (!this.uploadFile) return;
      if (mode === 'commit') this.uploadCommitting = true;
      const fd = new FormData();
      fd.append('file', this.uploadFile);
      fd.append('dataModelId', this.selectedModelId);
      fd.append('mode', mode);
      axios.post(this.$APIURL.base + 'api/dm/uploadAttrs', fd, {
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
        this.uploadRows = payload.attrs || [];
        this.uploadErrors = payload.errors || [];
        this.uploadWarnings = payload.warnings || [];
        this.uploadSummary = payload.summary || null;
        if (mode === 'commit') {
          this.uploadCommitting = false;
          this.uploadDialog = false;
          const s = this.uploadSummary || {};
          const inserted = s.toInsertAttrs || s.toInsert || 0;
          const skipped = s.skippedAttrs || s.skipped || 0;
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
    commitAttrsUpload() {
      this._runAttrsUpload('commit');
    },
    rowClass(item) {
      if (!item) return '';
      if (item._action === 'ERROR') return 'row-upload-error';
      if (item._action === 'SKIP') return 'row-upload-skip';
      return '';
    },
    _applyPendingView(pending) {
      const apply = () => {
        this.selectedModelId = pending.modelId;
        // 86번 #11 — 같은 OBJ_NM 다른 OWNER 가능 → 소유자도 정확 일치로 같이 세팅
        this.searchOwner     = pending.tableOwner || '';
        this.searchOwnerMode = pending.tableOwner ? 'exact' : 'contains';
        this.searchTable     = pending.tableNm || '';
        this.searchTableMode = pending.tableNm ? 'exact' : 'contains';
        // 추가 대상 테이블도 자동 세팅 — owner.objNm key 형식
        if (pending.tableNm) {
          this.addTargetKey = (pending.tableOwner || '') + '' + pending.tableNm;
        }
        this.$nextTick(() => {
          this.loadObjOptions();
          this.load();
        });
      };
      if (this.modelList.length > 0) {
        apply();
      } else {
        axios.post(this.$APIURL.base + "api/dm/getDataModelStatsList", {
          'schNm': null
        }).then((res) => {
          this.modelList = res.data.map(item => ({
            dataModelId: item.dataModelId,
            dataModelNm: item.dataModelNm,
          }));
          apply();
        });
      }
    },
  },
  created() {
    this.getModelList();
  },
  mounted() {
    this.$resizableGrid && this.$resizableGrid();
    this._pasteHandler = this.onPaste.bind(this);
    document.addEventListener('paste', this._pasteHandler);
    // 86번 #11 — 그리드 높이 동적 계산 (toolbar/카운트/페이징 영역 차감)
    this._calcTableHeight();
    this._resizeHandler = this._calcTableHeight.bind(this);
    window.addEventListener('resize', this._resizeHandler);
  },
  beforeDestroy() {
    if (this._pasteHandler) document.removeEventListener('paste', this._pasteHandler);
    if (this._resizeHandler) window.removeEventListener('resize', this._resizeHandler);
  },
  activated() {
    if (eventBus.pendingColumnView) {
      const pending = eventBus.pendingColumnView;
      eventBus.pendingColumnView = null;
      this._applyPendingView(pending);
    }
    // 탭 활성화 시 다시 계산 (다른 탭에서 viewport 가 바뀌었을 수 있음)
    this.$nextTick(() => this._calcTableHeight());
  },
}
</script>

<style scoped>
.filterWrapper { border-bottom: 1px solid #E8EAF6; background: #ffffff; }
.filterLabel { font-size: .8rem; white-space: nowrap; color: #455A64; font-weight: 500; }
.filterInput { flex-grow: 0 !important; flex-shrink: 0 !important; }
/* 86번 #11 — .tb-btn / .tb-btn-magic / 데이터테이블 가로스크롤 은 styles.css 전역에 옮김 */
.tableSpt { display: flex; justify-content: space-between; align-items: center; padding: 6px 20px; background: #FAFBFF; }
.split_bottom_wrap { position: absolute; width: 100%; max-height: 60px; bottom: 0px; border-top: 1px solid #E8EAF6; background: #FAFBFF; }
.pagination_wrap { position: relative; width: 100%; }
pre { font-family: 'Roboto'; }
.checkboxStyle { margin-top: 0; padding-top: 0; }
/* 86번 #11 — height/overflow 는 v-data-table fixed-header + :height prop 에서 처리. CSS 강제 height 제거. */
.row-nonstandard > td { background-color: #FFEBEE !important; }
.inline-edit { min-width: 100px; }
.inline-edit >>> input { padding: 2px 6px; }
.add-position-select { width: 90px !important; min-width: 90px !important; max-width: 90px !important; }
.add-position-select >>> .v-input__slot { min-width: 0 !important; padding: 0 6px !important; }
.add-position-select >>> .v-select__selection { font-size: .82rem; }
.inline-edit-center >>> input { text-align: center; }
.inline-error >>> .v-input__slot { background-color: #FFEBEE !important; }
.inline-dirty >>> .v-input__slot { background-color: #FFF8E1 !important; }
.inline-dirty-cb >>> .v-input--selection-controls__input { background-color: #FFF8E1 !important; border-radius: 4px; }
.row-upload-error > td { background-color: #FFEBEE !important; }
.row-upload-skip > td { background-color: #FFF8E1 !important; }
.preview-grid { border: 1px solid #E0E0E0; }
/* 컬럼 화면 액션 버튼 — 글자 대비 충분한 폭 확보 */
.btn-action { min-width: 110px !important; padding: 0 16px !important; letter-spacing: 0 !important; }
.btn-action.btn-wide { min-width: 170px !important; padding: 0 18px !important; }
/* 컬럼 그리드 헤더 3색 — 테이블 식별 / 논리 사용자 편집 / 자동 채움 물리 */
#clTable_table >>> th.hdr-table    { background-color: #ECEFF1 !important; color: #455A64 !important; }
#clTable_table >>> th.hdr-logical  { background-color: #E8F5E9 !important; color: #2E7D32 !important; font-weight: 600 !important; }
#clTable_table >>> th.hdr-physical { background-color: #E3F2FD !important; color: #1565C0 !important; }
/* 헤더 한 글자씩 줄바꿈 방지 (디 폴 트, 데 이 터 타 입 등) */
#clTable_table >>> th { white-space: nowrap !important; }
</style>
