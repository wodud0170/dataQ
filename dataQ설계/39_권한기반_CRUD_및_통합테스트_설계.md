# 권한 기반 CRUD 제한 및 통합 테스트 설계

**작성일**: 2026-04-16
**상태**: 설계 (구현 전)

---

## 1. 현재 상태

### 1.1 문제점
- 단어/용어/도메인/코드 사전에서 **등록, 수정, 삭제** 버튼이 모든 사용자에게 노출
- 일반 사용자가 승인된 표준을 수정/삭제할 수 있음 → 표준 무결성 훼손 가능
- 등록 버튼 라벨이 관리자/일반 구분 없이 동일 ("등록")
- 백엔드 수정/삭제 API에 관리자 권한 체크 없음 (프론트 우회 시 보안 취약)

### 1.2 현행 구조 (변경 대상 컴포넌트)

| 컴포넌트 | 등록 | 수정 | 삭제 | 일괄 등록 | 일괄 삭제 | isAdmin 체크 |
|---|---|---|---|---|---|---|
| DSWord.vue | L43 showModal('add') | L112 showModal('update') | L46 wordRemoveItem() | L44 excelFileUpload | L47 wordBulkRemove() | 없음 |
| DSTerm.vue | L52 showModal('add') | L130,L184 showModal('update') | L56 termRemoveItem() | L53 excelFileUpload | L57 termBulkRemove() | 없음 |
| DSDomain.vue | L43 showModal('add') | L107 showModal('update') | L46 domainRemoveItem() | L44 domainExcelFileUpload | L47 domainBulkRemove() | 없음 |
| DSCode.vue | L32 showModal('codeAdd') | L107 showModal('codeUpdate') | L35 codeRemoveItem() | L33 excelFileUpload | - | 없음 |

### 1.3 현행 백엔드 권한 상태

| API | 파일 | isAdmin 체크 |
|---|---|---|
| createWord (POST) | DataStandardController.java | O (APRV_YN 분기) |
| updateWord (POST) | DataStandardController.java:188 | **X** |
| deleteWords (POST) | DataStandardController.java:221 | **X** |
| createTerms (POST) | DataStandardController.java | O (APRV_YN 분기) |
| updateTerms (POST) | DataStandardController.java:617 | **X** |
| deleteTermsList (POST) | DataStandardController.java:664 | **X** |
| createDomain (POST) | DataStandardController.java | O (APRV_YN 분기) |
| updateDomain (POST) | DataStandardController.java:1098 | **X** |
| deleteDomains (POST) | DataStandardController.java:1127 | **X** |
| createCode (POST) | DataStandardController.java:801 | O (createTerms 위임) |
| updateCode (POST) | DataStandardController.java:818 | **X** (updateTerms 위임) |
| deleteCodeList (POST) | DataStandardController.java:832 | **X** (deleteTermsList 위임) |

---

## 2. 변경 설계

### 2.1 역할별 권한 매트릭스

| 기능 | 일반 사용자 | 관리자 |
|---|---|---|
| 등록 (신규) | **등록 신청** (APRV_YN='N') | **등록** (APRV_YN='Y' 즉시) |
| 수정 | X (버튼 비노출) | O |
| 삭제 | X (버튼 비노출) | O |
| 일괄 등록 | X (버튼 비노출) | O |
| 일괄 삭제 | X (버튼 비노출) | O |
| 다운로드 | O | O |
| 조회 | O (승인 항목만 기본) | O (전체) |

### 2.2 프론트엔드 변경 — 공통 패턴

#### 2.2.1 isAdmin 획득 (4개 컴포넌트 공통)

각 컴포넌트의 `data`에 `isAdmin: false` 추가, `created()`에서 API 호출:

```javascript
// data에 추가
isAdmin: false,

// created()에 추가
axios.get(this.$APIURL.base + 'api/login/isAdmin', { params: { user: this.$loginStatusData.id } })
  .then(res => { this.isAdmin = res.data === true; });
```

#### 2.2.2 버튼 제어 규칙

```html
<!-- 등록: 라벨 분기 (모든 사용자 노출) -->
<v-btn v-on:click="showModal('add')">{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>

<!-- 일괄 등록: 관리자만 -->
<v-btn v-if="isAdmin" v-on:click="excelFileUpload">일괄 등록</v-btn>

<!-- 삭제: 관리자만 -->
<v-btn v-if="isAdmin" v-on:click="removeItem()">삭제</v-btn>

<!-- 일괄 삭제: 관리자만 -->
<v-btn v-if="isAdmin" v-on:click="bulkRemove()">일괄 삭제</v-btn>

<!-- 상세 패널 수정: 관리자만 (패널 자체는 보임, 버튼만 숨김) -->
<v-btn v-if="isAdmin" v-on:click="showModal('update')">수정</v-btn>
```

### 2.3 파일별 구체적 변경 사항

---

#### DSWord.vue

**파일 경로**: `q-center/vue/front/src/components/DSWord.vue`

| 위치 | 현재 코드 | 변경 |
|---|---|---|
| L592 `props` | `['isMobile']` | 변경 없음 |
| L593 `data` | `data: () => ({` | `isAdmin: false` 추가 |
| `created()` | (기존 로직) | isAdmin API 호출 추가 |
| **L43** 등록 버튼 | `title="등록">등록</v-btn>` | `>{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>` |
| **L44** 일괄 등록 | `<v-btn class="gradient" v-on:click="excelFileUpload"` | `<v-btn v-if="isAdmin" class="gradient" v-on:click="excelFileUpload"` |
| **L46** 삭제 | `<v-btn class="gradient" v-on:click="wordRemoveItem()"` | `<v-btn v-if="isAdmin" class="gradient" v-on:click="wordRemoveItem()"` |
| **L47** 일괄 삭제 | `<v-btn class="gradient" color="red lighten-4" v-on:click="wordBulkRemove()"` | `<v-btn v-if="isAdmin" class="gradient" color="red lighten-4" v-on:click="wordBulkRemove()"` |
| **L112** 상세 수정 | `<v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>` | `<v-btn v-if="isAdmin" class="gradient" v-on:click="showModal('update')">수정</v-btn>` |

---

#### DSTerm.vue

**파일 경로**: `q-center/vue/front/src/components/DSTerm.vue`

| 위치 | 현재 코드 | 변경 |
|---|---|---|
| L781 `props` | `['isMobile']` | 변경 없음 |
| `data` | (기존) | `isAdmin: false` 추가 |
| `created()` | (기존 로직) | isAdmin API 호출 추가 |
| **L52** 등록 버튼 | `title="등록">등록</v-btn>` | `>{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>` |
| **L53** 일괄 등록 | `<v-btn class="gradient" v-on:click="excelFileUpload"` | `<v-btn v-if="isAdmin" class="gradient" v-on:click="excelFileUpload"` |
| **L54** 템플릿 다운로드 | 변경 없음 (모든 사용자 노출) | - |
| **L56** 선택 삭제 | `<v-btn class="gradient" v-on:click="termRemoveItem()"` | `<v-btn v-if="isAdmin" class="gradient" v-on:click="termRemoveItem()"` |
| **L57** 전체 삭제 | `<v-btn class="gradient" color="red lighten-4" v-on:click="termBulkRemove()"` | `<v-btn v-if="isAdmin" class="gradient" color="red lighten-4" v-on:click="termBulkRemove()"` |
| **L130** 상세 수정 (상세보기 탭) | `<v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>` | `<v-btn v-if="isAdmin" ...>수정</v-btn>` |
| **L184** 단어구성 수정 (단어구성 탭) | `<v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>` | `<v-btn v-if="isAdmin" ...>수정</v-btn>` |

---

#### DSDomain.vue

**파일 경로**: `q-center/vue/front/src/components/DSDomain.vue`

| 위치 | 현재 코드 | 변경 |
|---|---|---|
| L571 `props` | `['isMobile']` | 변경 없음 |
| L572 `data` | `data: () => ({` | `isAdmin: false` 추가 |
| `created()` | (기존 로직) | isAdmin API 호출 추가 |
| **L43** 등록 버튼 | `title="등록">등록</v-btn>` | `>{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>` |
| **L44** 일괄 등록 | `<v-btn class="gradient" v-on:click="domainExcelFileUpload()"` | `<v-btn v-if="isAdmin" class="gradient" ...` |
| **L46** 삭제 | `<v-btn class="gradient" v-on:click="domainRemoveItem()"` | `<v-btn v-if="isAdmin" class="gradient" ...` |
| **L47** 일괄 삭제 | `<v-btn class="gradient" color="red lighten-4" v-on:click="domainBulkRemove()"` | `<v-btn v-if="isAdmin" class="gradient" ...` |
| **L107** 상세 수정 | `<v-btn class="gradient" v-on:click="showModal('update')">수정</v-btn>` | `<v-btn v-if="isAdmin" ...>수정</v-btn>` |

---

#### DSCode.vue

**파일 경로**: `q-center/vue/front/src/components/DSCode.vue`

코드는 내부적으로 TB_TERMS를 사용 (createCode → createTerms 위임). APRV_YN 이미 존재.

| 위치 | 현재 코드 | 변경 |
|---|---|---|
| L830 `props` | `['isMobile']` | 변경 없음 |
| `data` | (기존) | `isAdmin: false` 추가 |
| `created()` | (기존 로직) | isAdmin API 호출 추가 |
| **L32** 코드 등록 | `title="등록">등록</v-btn>` | `>{{ isAdmin ? '등록' : '등록 신청' }}</v-btn>` |
| **L33** 일괄 등록 | `<v-btn class="gradient" v-on:click="excelFileUpload"` | `<v-btn v-if="isAdmin" class="gradient" ...` |
| **L35** 코드 삭제 | `<v-btn class="gradient" v-on:click="codeRemoveItem()"` | `<v-btn v-if="isAdmin" class="gradient" ...` |
| **L107** 상세 수정 | `<v-btn class="gradient" v-on:click="showModal('codeUpdate')">수정</v-btn>` | `<v-btn v-if="isAdmin" ...>수정</v-btn>` |
| **L753** 코드값 등록 | `<v-btn class="gradient" v-on:click="showModal('codeValAdd')"` | 변경 없음 (코드값은 승인 대상 아님, TB_CODE_DATA) |
| **L754** 코드값 일괄등록 | (기존) | 변경 없음 |
| **L756** 코드값 삭제 | (기존) | 변경 없음 |

> **참고**: 코드값(TB_CODE_DATA)은 별도 테이블이며 APRV_YN 없음. 코드 자체(TB_TERMS 기반)만 승인 대상.

---

## 3. 승인/반려 프로세스 단순화

### 3.1 원칙
- 모든 신청은 **1차 판정(승인 or 반려)으로 종결**
- 반려 시 재신청 = 처음부터 새로 등록 신청 (별도 "재요청" 기능 불필요)
- 기록 측면에서도 각 신청이 독립적으로 남아 추적이 깔끔함

### 3.2 승인 순서 규칙
- **단어 → 용어** 순서 (용어는 단어 조합이므로 구성 단어가 먼저 승인되어야 함)
- 이미 구현됨: 용어 승인 시 미승인 단어 체크 (`selectUnapprovedWordsByTermsId`)

### 3.3 코드 승인 프로세스 추가

현재 `approve.xml`의 `selectStdAprvStatList`에 WORD/TERMS/DOMAIN만 포함.
**CODE 타입 추가 필요**.

#### 변경 파일: `q-common/src/main/resources/mapper/stnd/approve.xml`

**selectStdAprvStatList**에 CODE union 추가:
```sql
union all
select 
    ''            as id
   ,'CODE'       as reqTp
   ,TERMS_ID     as reqItemId
   ,TERMS_NM     as reqItemNm
   ,TERMS_ENG_ABRV_NM as reqItemEngNm
   ,0            as aprvStat
   ,CRET_USER_ID as reqUserId
   ,to_char(to_timestamp(CRET_DT,'YYYYMMDDHH24MISS'),'YYYY-MM-DD HH24:MI:SS') as reqCretDt
   ,to_char(to_timestamp(UPDT_DT,'YYYYMMDDHH24MISS'),'YYYY-MM-DD HH24:MI:SS') as reqUpdtDt
   ,APRV_USER_ID as aprvUserId
   ,to_char(to_timestamp(APRV_STAT_UPDT_DT,'YYYYMMDDHH24MISS'),'YYYY-MM-DD HH24:MI:SS') as aprvStatUpdtDt 
   ,''           as aprvStatUpdtRsn
from TB_TERMS
where APRV_YN = 'N'
  AND TERMS_SE = 'CODE'   -- 코드 구분 조건
  AND NOT EXISTS (SELECT 1 FROM TB_APRV_STATS s WHERE s.REQ_ITEM_ID = TERMS_ID AND s.REQ_TP = 'CODE')
```

기존 TERMS union에서 코드 제외:
```sql
-- 기존 TERMS 부분에 조건 추가
AND (TERMS_SE IS NULL OR TERMS_SE != 'CODE')
```

**putStdAprvStat** (DataStandardController.java:1585 switch문)에 CODE case 추가:
```java
case CODE:
    session.update("approve.updateTermsAprvStat", dataVo);  // CODE는 TB_TERMS 사용
    break;
```

> **확인 필요**: TB_TERMS에 TERMS_SE 컬럼으로 코드/용어 구분이 되는지 확인 후 위 조건 결정.

### 3.4 알림 기능 (향후 검토)
- 로그인 시 "미확인 처리 결과 N건" 알림
- 현재는 요청 현황 페이지에서 수동 확인 (구현 우선순위 낮음)

---

## 4. 대시보드 승인 현황 카드 개선

### 4.1 현재 상태

**파일**: `QDashboard.vue` (L117~166), `search.xml` (L14~43)

현재 4장 카드: 승인요청 / **검토** / 승인완료 / 반려
- "검토" 상태(APRV_STAT=1)는 실제 사용되지 않음 → **제거**
- 관리자/사용자 구분 없이 동일한 UI, 모두 "승인" 화면으로 이동
- 승인완료/반려 건수가 **전체 누적** (기간 제한 없음)

### 4.2 변경 설계

#### 카드 구성 (3장으로 축소)

| 카드 | 색상 | 관리자 | 사용자 |
|---|---|---|---|
| 1번 | 파랑 (#1976D2) | 승인대기 N건 (전체) | 승인대기 N건 (본인) |
| 2번 | 초록 (#43A047) | 승인완료 N건 (최근 1달) | 승인완료 N건 (본인) |
| 3번 | 빨강 (#E53935) | 반려 N건 (최근 1달) | 반려 N건 (본인) |

#### 섹션 타이틀 분기
```html
<h2><v-icon>dashboard_customize</v-icon>&nbsp;&nbsp;{{ isAdmin ? '승인 현황' : '내 요청 현황' }}</h2>
```

#### 클릭 시 이동 분기

| 클릭 | 관리자 | 사용자 |
|---|---|---|
| 승인대기 | 관리 > 승인 (필터: REQUESTED) | 마이페이지 > 요청 현황 (필터: PENDING) |
| 승인완료 | 관리 > 승인 (필터: APPROVED) | 마이페이지 > 요청 현황 (필터: APPROVED) |
| 반려 | 관리 > 승인 (필터: REJECTED) | 마이페이지 > 요청 현황 (필터: REJECTED) |

### 4.3 파일별 구체적 변경 사항

---

#### QDashboard.vue

**파일 경로**: `q-center/vue/front/src/components/QDashboard.vue`

**(1) isAdmin 획득** — `data`에 `isAdmin: false` 추가, `created()`에서 API 호출

**(2) L117~166 카드 영역 변경**

현재 4카드 → 3카드로 축소. 검토 카드(L133~141) 제거.

클릭 핸들러를 인라인에서 메서드로 변경:
```html
<!-- 승인대기 -->
<v-card class="aprv-card" v-on:click.stop="onAprvCardClick('REQUESTED')">
  ...
</v-card>
<!-- 승인완료 -->
<v-card class="aprv-card" v-on:click.stop="onAprvCardClick('APPROVED')">
  ...
</v-card>
<!-- 반려 -->
<v-card class="aprv-card" v-on:click.stop="onAprvCardClick('REJECTED')">
  ...
</v-card>
```

**(3) L119 타이틀 변경**
```
현재: <h2><v-icon>dashboard_customize</v-icon>&nbsp;&nbsp;승인 현황</h2>
변경: <h2><v-icon>dashboard_customize</v-icon>&nbsp;&nbsp;{{ isAdmin ? '승인 현황' : '내 요청 현황' }}</h2>
```

**(4) methods에 onAprvCardClick 추가** (L657 sendApprovalStatus 대체):
```javascript
onAprvCardClick(status) {
  if (this.isAdmin) {
    // 관리자 → 승인 화면
    eventBus.pendingApprovalFilter = status;
    this.$emit('addTabItem', '승인', 'approval');
  } else {
    // 사용자 → 요청 현황
    eventBus.pendingMyRequestFilter = status === 'REQUESTED' ? 'PENDING' : status;
    this.$emit('addTabItem', '요청 현황', 'myRequest');
  }
}
```

> **주의**: eventBus import 필요 — `import { eventBus } from '../eventBus';`

**(5) L234 data에서 제거**
- `aprvStatCheckingCnt` 제거

**(6) L465-484 getDashboardInfo에서 제거**
- `this.aprvStatCheckingCnt = _data.aprvStatCheckingCnt;` 행 제거 (L475)

---

#### search.xml (백엔드 쿼리 변경)

**파일 경로**: `q-common/src/main/resources/mapper/stnd/search.xml`

**(1) resultMap (L5~13)**: `aprvStatCheckingCnt` 행 제거

**(2) selectDataboardInfo (L14~43)** 변경:

```sql
select 
    (select count(*) from TB_TERMS where APRV_YN = 'Y') as termsCnt,
    (select count(*) from TB_WORD where APRV_YN = 'Y') as wordCnt,
    (select count(*) from TB_DOMAIN where APRV_YN = 'Y') as domainCnt,
    -- 승인대기 (기존과 동일)
    (select (select count(*) from TB_TERMS where APRV_YN = 'N'
        <if test="reqUserId != null">AND CRET_USER_ID = #{reqUserId}</if>) + 
     (select count(*) from TB_WORD where APRV_YN = 'N'
        <if test="reqUserId != null">AND CRET_USER_ID = #{reqUserId}</if>) + 
     (select count(*) from TB_DOMAIN where APRV_YN = 'N'
        <if test="reqUserId != null">AND CRET_USER_ID = #{reqUserId}</if>)) as aprvStatRequestedCnt,
    -- 승인완료 (관리자: 최근 1달, 사용자: 본인 건 전체)
    (select count(*) from TB_APRV_STATS where APRV_STAT = 2
        <if test="reqUserId != null">AND REQ_USER_ID = #{reqUserId}</if>
        <if test="reqUserId == null">AND APRV_STAT_UPDT_DT >= to_char(now() - interval '1 month', 'YYYYMMDDHH24MISS')</if>
    ) as aprvStatApprovedCnt,
    -- 반려 (관리자: 최근 1달, 사용자: 본인 건 전체)
    (select count(*) from TB_APRV_STATS where APRV_STAT = 3
        <if test="reqUserId != null">AND REQ_USER_ID = #{reqUserId}</if>
        <if test="reqUserId == null">AND APRV_STAT_UPDT_DT >= to_char(now() - interval '1 month', 'YYYYMMDDHH24MISS')</if>
    ) as aprvStatRejectedCnt
```

**핵심**: `reqUserId == null` = 관리자 → 최근 1달 필터 적용. `reqUserId != null` = 사용자 → 본인 건 전체.

---

#### MMApproval.vue (승인 화면 필터 연동)

**파일 경로**: `q-center/vue/front/src/components/MMApproval.vue`

현재 props로 `approvalStatus`를 받아 watch로 필터 적용 (L265~267).
기존 방식(props)과 eventBus 방식 중 eventBus가 keep-alive 대응에 유리.

**변경**:
```javascript
// activated() 추가 (keep-alive 대응)
activated() {
  if (eventBus.pendingApprovalFilter) {
    this.activeStatusFilter = eventBus.pendingApprovalFilter;
    eventBus.pendingApprovalFilter = null;
  }
}
```

> import 확인: `import { eventBus } from '../eventBus';` 필요 여부 체크

---

#### DSMyRequest.vue (요청 현황 필터 연동)

**파일 경로**: `q-center/vue/front/src/components/DSMyRequest.vue`

현재 필터: `activeFilter` (ALL/PENDING/APPROVED/REJECTED), L129.

**변경**:
```javascript
// activated() 추가 (keep-alive 대응)
activated() {
  if (eventBus.pendingMyRequestFilter) {
    this.activeFilter = eventBus.pendingMyRequestFilter;
    eventBus.pendingMyRequestFilter = null;
    this.getMyRequestList();  // 필터 적용 후 데이터 재조회
  }
}
```

> import 확인: `import { eventBus } from '../eventBus';` 필요 여부 체크

---

## 5. 백엔드 권한 체크 추가

### 5.1 변경 대상

**파일**: `q-center/src/main/java/qualitycenter/controller/DataStandardController.java`

수정/삭제 API 6개에 관리자 권한 체크 추가:

| API | 라인 | 추가 코드 |
|---|---|---|
| updateWord | L188 | 아래 패턴 추가 |
| deleteWords | L221 | 아래 패턴 추가 |
| updateTerms | L617 | 아래 패턴 추가 |
| deleteTermsList | L664 | 아래 패턴 추가 |
| updateDomain | L1098 | 아래 패턴 추가 |
| deleteDomains | L1127 | 아래 패턴 추가 |

#### 공통 패턴
```java
// 메서드 최상단에 추가
if (!sessionService.isAdmin()) {
    Response result = new Response();
    result.setResultInfo(RestResult.CODE_500.getCode(), "관리자만 수정/삭제할 수 있습니다.");
    return Mono.just(result);
}
```

> updateCode, deleteCodeList은 각각 updateTerms, deleteTermsList을 위임하므로 별도 추가 불필요.

---

## 6. 통합 테스트 시나리오

### 6.1 테스트 전제
- 테스트 계정: jyjang (일반), space (관리자)
- **모든 조작은 DOM 클릭으로 수행** (execute_script는 스크롤 보조만 허용)
  - 실질적으로 사용자가 사용하듯 테스트를 하기 위해 모든 진행은 DOM 클릭으로 제한함
- 각 STEP에서 스크린샷 저장

### 6.2 시나리오: 단어 + 용어 + 도메인 등록 신청 → 승인/반려 → 결과 확인

```
STEP 1. jyjang 로그인 — 등록 신청
  1-a. 단어 등록 신청 2건
       - 단어A: "셀레니움XXX" (승인 대상)
       - 단어B: "셀레반려XXX" (반려 대상)
  1-b. 용어 등록 신청 1건
       - 단어A + 기존 승인 단어를 조합한 용어
         (예: "셀레니움XXX코드" = 셀레니움XXX + 코드)
  1-c. 도메인 등록 신청 1건
  1-d. 마이페이지 → 요청 현황에서 4건 승인대기 확인
  1-e. 등록 버튼 라벨이 "등록 신청"인지 확인
  1-f. 수정/삭제 버튼이 비노출인지 확인

STEP 2. space 로그인 — 승인/반려 처리
  2-a. 승인 화면 → 단어A 승인
  2-b. 단어B 반려 (반려 사유 입력)
  2-c. 용어 승인 (구성 단어A가 이미 승인됐으므로 가능)
  2-d. 도메인 승인
  2-e. 승인 화면 결과 확인
       - 승인완료 3건 (단어A, 용어, 도메인)
       - 반려 1건 (단어B)

STEP 3. jyjang 로그인 — 처리 결과 확인
  3-a. 마이페이지 → 요청 현황
       - 승인완료 3건, 반려 1건 확인
       - 반려 항목에 반려 사유 표시 확인
  3-b. 단어 사전 → 단어A 검색 → 조회됨 (승인 완료)
  3-c. 단어B는 승인 여부 체크 해제 시에만 보임 (미승인)

STEP 4. space 로그인 — 정리 (삭제)
  4-a. 단어 사전 → 테스트 단어/용어/도메인 삭제
  4-b. 삭제 확인
```

### 6.3 검증 포인트

| STEP | 검증 항목 | 기대 결과 |
|---|---|---|
| 1 | 일반 사용자 등록 버튼 라벨 | "등록 신청"으로 표시 |
| 1 | 수정/삭제/일괄등록/일괄삭제 버튼 | 비노출 |
| 1 | 등록 후 APRV_YN | 'N' (미승인) |
| 2 | 관리자 등록 버튼 라벨 | "등록"으로 표시 |
| 2 | 수정/삭제 버튼 | 노출 |
| 2 | 단어 승인 → 용어 승인 순서 | 정상 처리 |
| 2 | 반려 시 사유 입력 | 필수, 저장됨 |
| 3 | 요청 현황 상태 표시 | 승인완료/반려 정확히 구분 |
| 3 | 반려 사유 표시 | 관리자가 입력한 사유 노출 |
| 4 | 관리자만 삭제 가능 | 정상 동작 |

### 6.4 테스트 파일 구조
```
dataQ설계/테스트/selenium/
  test_login.py                    — 로그인/로그아웃 (기존)
  test_global_search.py            — 통합검색 연동 (기존)
  test_word_approval_flow.py       — 단어 등록→승인→삭제 (기존)
  test_full_approval_flow.py       — 통합 승인 시나리오 (신규)
```

---

## 7. 구현 순서

```
Phase 1: 권한 기반 버튼 제어 (프론트)
  ① DSWord.vue — isAdmin 추가, 등록 라벨 분기, 수정/삭제/일괄등록/일괄삭제 v-if
  ② DSTerm.vue — 동일 패턴 적용
  ③ DSDomain.vue — 동일 패턴 적용
  ④ DSCode.vue — 동일 패턴 적용 (코드값 버튼은 변경 안 함)
  ⑤ 빌드 + 수동 확인

Phase 2: 대시보드 개선 (프론트 + 백엔드)
  ⑥ search.xml — checkingCnt 제거, 관리자용 기간 필터 추가
  ⑦ QDashboard.vue — 3카드, 타이틀 분기, onAprvCardClick 메서드
  ⑧ MMApproval.vue — activated()에서 eventBus.pendingApprovalFilter 소비
  ⑨ DSMyRequest.vue — activated()에서 eventBus.pendingMyRequestFilter 소비
  ⑩ 빌드 + 수동 확인

Phase 3: 백엔드 권한 체크
  ⑪ DataStandardController.java — 수정/삭제 API 6개에 isAdmin 검증 추가
  ⑫ approve.xml — CODE 타입 추가
  ⑬ 빌드 + API 테스트

Phase 4: Selenium 통합 테스트
  ⑭ test_full_approval_flow.py 작성 + 실행
  ⑮ 테스트 통과 확인
```

---

## 8. 변경 파일 요약

| 파일 | Phase | 변경 요약 |
|---|---|---|
| `DSWord.vue` | 1 | isAdmin data/created, 등록 라벨, 수정/삭제/일괄등록/일괄삭제 v-if |
| `DSTerm.vue` | 1 | 동일 (수정 버튼 2곳) |
| `DSDomain.vue` | 1 | 동일 |
| `DSCode.vue` | 1 | 동일 (코드값 영역 제외) |
| `search.xml` | 2 | checkingCnt 제거, 승인완료/반려에 최근 1달 조건 (관리자) |
| `QDashboard.vue` | 2 | 3카드, isAdmin, 타이틀 분기, onAprvCardClick, eventBus import |
| `MMApproval.vue` | 2 | activated() + eventBus.pendingApprovalFilter |
| `DSMyRequest.vue` | 2 | activated() + eventBus.pendingMyRequestFilter |
| `DataStandardController.java` | 3 | updateWord/deleteWords/updateTerms/deleteTermsList/updateDomain/deleteDomains에 isAdmin 체크 |
| `approve.xml` | 3 | selectStdAprvStatList에 CODE union 추가, TERMS에서 CODE 제외 |
| `test_full_approval_flow.py` | 4 | 신규 Selenium 통합 테스트 |

---

## 9. 미결 사항

| 항목 | 상태 | 비고 |
|---|---|---|
| 알림 기능 | 향후 검토 | 로그인 시 미확인 처리결과 알림 |
| 코드 승인 분기 조건 | 확인 필요 | TB_TERMS.TERMS_SE = 'CODE' 여부 확인 |
| 코드값(TB_CODE_DATA) 권한 | 결정됨 | 코드값은 권한 제어 대상 아님 |
| 일괄 등록 APRV_YN | 확인 완료 | 관리자 한정이므로 기존 APRV_YN='Y' 유지 |
| 서버 측 권한 체크 | Phase 3 | 프론트 우회 방지용 |
