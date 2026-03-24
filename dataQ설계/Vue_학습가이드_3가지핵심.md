# Vue.js 핵심 3가지 - dataQ 프로젝트 기반 학습 가이드

> JSP + jQuery + Ajax 경험자를 위한 Vue 핵심 정리
> 이 문서의 모든 예제는 실제 dataQ 프로젝트 코드입니다.

---

## 1. 데이터 흐름: data → computed → template

### 개념

jQuery에서는 데이터가 바뀌면 **직접 DOM을 수정**했습니다.
```javascript
// jQuery 방식
$('#table-body').html('<tr><td>' + name + '</td></tr>');
```

Vue에서는 **데이터만 바꾸면 화면이 자동으로 갱신**됩니다.

### 흐름도

```
data()          →    computed       →    template (화면)
(원본 데이터)        (가공/필터링)        (HTML 렌더링)

jobList: []     →    filteredDetail →    v-data-table :items="filteredDetail"
```

### 실제 코드 (DSDataDiagResult.vue)

**① data() — 원본 데이터 저장소**

```javascript
data() {
  return {
    detailList: [],        // 서버에서 받은 원본 데이터
    searchTable: '',       // 검색 필터 값
    onlyIssue: true,       // 이슈만 보기 체크
  };
}
```
- `data()`는 이 컴포넌트가 사용하는 **모든 변수**를 선언하는 곳
- jQuery의 전역 변수 선언과 비슷하지만, Vue가 이 변수들을 **감시**합니다
- 값이 바뀌면 → 관련된 computed와 template이 **자동 재계산/재렌더링**

**② computed — 데이터 가공 (자동 계산)**

```javascript
computed: {
  filteredDetail() {
    return this.detailList.filter(item => {
      // 테이블명 검색
      if (this.searchTable && !item.objNm.includes(this.searchTable)) {
        return false;
      }
      // 이슈만 보기
      if (this.onlyIssue && item.diagTypes.length === 0) {
        return false;
      }
      return true;
    });
  },
  detailPageCount() {
    return Math.ceil(this.filteredDetail.length / this.detailItemsPerPage);
  },
}
```
- `filteredDetail`은 **함수처럼 생겼지만 변수처럼 사용**합니다
- `this.searchTable`이 바뀌면 → `filteredDetail`이 **자동 재계산**
- `filteredDetail`이 바뀌면 → `detailPageCount`도 **자동 재계산** (연쇄 반응)
- jQuery로 치면: 검색어가 바뀔 때마다 필터링 함수를 **수동 호출**해야 했는데, Vue는 **알아서** 호출

**③ template — 화면 렌더링 (자동)**

```html
<v-data-table
  :items="filteredDetail"
  :items-per-page="detailItemsPerPage"
>
```
- `filteredDetail`이 바뀌면 → 테이블이 **자동으로 다시 그려짐**
- jQuery의 `$('#table').html(...)` 같은 코드가 **필요 없음**

### 요약: jQuery vs Vue 데이터 흐름

```
[jQuery]
데이터 변경 → 직접 DOM 조작 필요 → $('#xxx').html(newHtml)

[Vue]
데이터 변경 → (자동) computed 재계산 → (자동) 화면 갱신
```

---

## 2. 이벤트 흐름: click → method → axios → data 변경

### 개념

사용자가 버튼을 클릭하면 → 메서드 실행 → 서버 호출 → 데이터 저장 → 화면 자동 갱신

### 흐름도

```
사용자 클릭          method 실행         서버 호출           data 변경         화면 자동 갱신
    │                   │                  │                  │                  │
    ▼                   ▼                  ▼                  ▼                  ▼
@click="startDiag"  startDiag()     axios.post(...)    this.jobList=res   v-data-table 갱신
```

### 실제 코드 (DSDataDiag.vue)

**① template — 이벤트 바인딩**

```html
<v-btn @click="startDiag">진단 시작</v-btn>
```
- jQuery: `$('#btn').on('click', function() { ... })`
- Vue: `@click="메서드명"` — 훨씬 간결

**② methods — 함수 정의**

```javascript
methods: {
  startDiag() {
    // 1) 파라미터 준비
    const body = {
      clctId: this.selectedClctId,
      dataModelId: this.selectedModel,
    };

    // 2) 서버 호출 (axios = $.ajax)
    axios.post(this.$APIURL.base + 'api/diag/startDiag', body)
      .then(res => {
        // 3) 응답 처리
        if (res.data.resultCode === 200) {
          this.showSnackbar('진단을 시작했습니다.', 'success');
          this.loadJobList();    // 목록 다시 불러오기
        }
      })
      .catch(() => {
        this.showSnackbar('서버 오류', 'error');
      });
  },

  loadJobList() {
    axios.post(this.$APIURL.base + 'api/diag/getDiagJobList')
      .then(res => {
        // 4) data 변경 → 화면 자동 갱신
        this.jobList = res.data;
      });
  },
}
```

### jQuery Ajax vs axios 비교

```javascript
// jQuery
$.ajax({
    url: '/api/diag/getDiagJobList',
    type: 'POST',
    contentType: 'application/json',
    success: function(res) {
        // DOM 직접 조작
        var html = '';
        for (var i = 0; i < res.length; i++) {
            html += '<tr><td>' + res[i].name + '</td></tr>';
        }
        $('#table-body').html(html);
    },
    error: function() { alert('오류'); }
});

// Vue + axios
axios.post(this.$APIURL.base + 'api/diag/getDiagJobList')
  .then(res => {
    this.jobList = res.data;  // 이 한 줄이면 끝. 화면은 알아서 갱신.
  });
```

### mounted() — 화면 로드 시 자동 실행

```javascript
mounted() {
  this.loadDataModelList();  // 데이터모델 목록 로드
  this.loadJobList();        // 진단 이력 로드
}
```
- jQuery: `$(document).ready(function() { ... })`
- Vue: `mounted() { ... }` — 같은 역할

### 전체 이벤트 흐름 정리

```
[화면 로드]
mounted() → loadJobList() → axios → this.jobList = 응답 → 테이블 자동 렌더링

[진단 시작 클릭]
@click → startDiag() → axios.post → 성공 시 → loadJobList() → this.jobList 갱신 → 테이블 갱신

[데이터모델 선택 변경]
@change → onModelChange(dmId) → axios.post → this.clctList = 응답 → 수집일시 셀렉트 자동 갱신
```

---

## 3. 컴포넌트 간 통신: props / emit / eventBus

### 개념

jQuery에서는 전역 변수나 DOM을 통해 컴포넌트 간 데이터를 주고받았습니다.
Vue에서는 3가지 방법이 있습니다.

### 3-1. props: 부모 → 자식 (데이터 내려주기)

```
NdLayout (부모)
    │
    │  :isMobile="isMobile"    ← props로 전달
    │  :tabs="tabs"
    ▼
NdNav (자식)
```

**부모 (NdLayout.vue) — 데이터 전달**
```html
<router-view name="aside"
  :isMobile="isMobile"
  :tabs="tabs"
  :activeContent="activeContent"
  :navDiagGroup="navDiagGroup"
/>
```
- `:isMobile="isMobile"` → 자식에게 `isMobile` 값을 내려보냄
- jQuery로 치면: 함수 호출할 때 파라미터를 넘기는 것과 비슷

**자식 (NdNav.vue) — 데이터 받기**
```javascript
props: ['isMobile', 'tabs', 'activeContent', 'navDiagGroup']
```
- 받은 props는 `this.isMobile`로 바로 사용 가능
- **주의**: 자식이 props를 직접 수정하면 안 됨 (읽기 전용)

### 3-2. $emit: 자식 → 부모 (이벤트 올려보내기)

```
NdNav (자식)
    │
    │  this.$emit('addTabItem', '진단 결과', 'dataDiagResult', 0)
    │                                                       ← 이벤트 발생
    ▼
NdLayout (부모)
    │
    │  @addTabItem="addTabItem"                            ← 이벤트 수신
```

**자식 (NdNav.vue) — 이벤트 발생**
```javascript
methods: {
  addTabItem(title, name) {
    let _index = this.tabs.length;
    this.$emit('addTabItem', title, name, _index);  // 부모에게 알림
  }
}
```

**부모 (NdLayout.vue) — 이벤트 수신**
```html
<router-view name="aside"
  @addTabItem="addTabItem"
/>
```
```javascript
methods: {
  addTabItem(title, name, index) {
    this.tabs.push({ title, name, index });  // 실제 데이터 변경은 부모에서
  }
}
```
- jQuery로 치면: 콜백 함수를 넘겨주는 것과 비슷
- **원칙**: 데이터는 부모가 관리하고, 자식은 "이렇게 해주세요"라고 요청만 함

### 3-3. eventBus: 아무 컴포넌트 간 통신 (형제, 먼 관계)

props/emit은 **부모-자식** 관계에서만 사용합니다.
관계가 먼 컴포넌트끼리 통신할 때는 **eventBus**를 사용합니다.

```
DSDataDiag.vue ──eventBus.$emit('openDiagResult')──▶ NdNav.vue
    (진단 실행)                                         (네비게이션)
                                                          │
                                          addTabItem('진단 결과', 'dataDiagResult')
                                                          │
                                                          ▼
                                                   DSDataDiagResult.vue
                                                      (진단 결과)
```

**eventBus 정의 (eventBus.js)**
```javascript
import Vue from 'vue'
export const eventBus = new Vue()  // 빈 Vue 인스턴스 = 이벤트 중계소
```

**보내는 쪽 (DSDataDiag.vue) — 결과보기 버튼 클릭**
```javascript
import { eventBus } from '../eventBus';

methods: {
  goToResult(item) {
    eventBus.pendingDiagJobId = item.diagJobId;   // 데이터 저장
    eventBus.$emit('openDiagResult');              // 이벤트 발송
  }
}
```

**받는 쪽 (NdNav.vue) — 이벤트 수신**
```javascript
import { eventBus } from '../../eventBus';

mounted() {
  eventBus.$on('openDiagResult', () => {
    this.addTabItem('진단 결과', 'dataDiagResult');   // 탭 열기
  });
},
beforeDestroy() {
  eventBus.$off('openDiagResult');   // 메모리 누수 방지: 반드시 해제
}
```

**데이터 참조 (DSDataDiagResult.vue)**
```javascript
import { eventBus } from '../eventBus';

activated() {
  this.checkPendingJob();   // eventBus에 저장된 데이터 가져오기
},
methods: {
  checkPendingJob() {
    if (eventBus.pendingDiagJobId) {
      this.pendingJobId = eventBus.pendingDiagJobId;
      eventBus.pendingDiagJobId = null;   // 사용 후 초기화
    }
  }
}
```

### 통신 방법 선택 기준

| 상황 | 방법 | 예시 |
|------|------|------|
| 부모 → 자식 데이터 전달 | **props** | NdLayout → NdNav에 탭 목록 전달 |
| 자식 → 부모 이벤트 알림 | **$emit** | NdNav → NdLayout에 "탭 추가해주세요" |
| 먼 관계 컴포넌트 간 통신 | **eventBus** | DSDataDiag → NdNav "진단 결과 탭 열어줘" |

---

## 부록: dataQ 전체 컴포넌트 구조

```
NdLayout.vue (최상위 부모 - 전체 레이아웃, 상태 관리)
    │
    ├── NdHeader.vue (상단 헤더)
    │       props: isMobile, drawer, tabs
    │       emit: navIconClick, addTabItem
    │
    ├── NdNav.vue (좌측 네비게이션)
    │       props: isMobile, tabs, activeContent, navDiagGroup...
    │       emit: addTabItem, addActiveContent, navAllGroupClose
    │       eventBus: $on('openDiagResult')
    │
    └── NdContent.vue (메인 콘텐츠 영역)
            props: isMobile, tabs, activeContent
            emit: addActiveContent, removeTabItem
            │
            └── 각 화면 컴포넌트 (탭에 따라 전환)
                    ├── DSDataDiag.vue (진단 실행)
                    ├── DSDataDiagResult.vue (진단 결과)
                    ├── DSTerm.vue (용어)
                    ├── DSWord.vue (단어)
                    ├── DSDomain.vue (도메인)
                    └── DSCode.vue (코드)
```

---

## 핵심 요약

1. **데이터 흐름**: `data()`에 변수 선언 → `computed`에서 가공 → `template`에서 표시. **데이터만 바꾸면 화면은 자동**.
2. **이벤트 흐름**: `@click` → `methods` → `axios` → `this.변수 = 응답` → 화면 자동 갱신. **DOM 조작 불필요**.
3. **컴포넌트 통신**: 부모→자식은 `props`, 자식→부모는 `$emit`, 먼 관계는 `eventBus`.

> 이 3가지만 이해하면, Claude에게 "이런 화면 만들어줘"라고 정확하게 지시하고 결과를 검증할 수 있습니다.
