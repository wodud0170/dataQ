# DSTerm.vue 용어 등록/수정 화면 분석 및 개선 포인트

## 1. 현재 구조

### 화면 구성
- **상단**: 검색 영역 + 용어 목록 테이블 (Split 50%)
- **하단**: 상세보기 / 단어 구성 목록 탭 (Split 50%)
- **모달**: 등록(3-step stepper), 수정(3-step stepper), 일괄등록

### 등록 스테퍼 (addTermModal)
| Step | 내용 | 동작 |
|------|------|------|
| 1 | 용어명 입력 | 텍스트 입력 → 형태소 분석 API 호출 |
| 2 | 단어 목록 선택 | 형태소 분석 결과 → 등록된 단어 선택 or 인라인 등록 |
| 3 | 용어 정보 입력 | 단어 순서 변경, 영문약어(자동), 도메인/코드 선택, 설명 등 |

### 수정 스테퍼 (updateTermModal)
| Step | 내용 | 비고 |
|------|------|------|
| 1 | 용어명 입력 | 기존값 바인딩 |
| 2 | 단어 목록 선택 | 기존값 바인딩, **인라인 등록 미적용** |
| 3 | 용어 정보 입력 | **도메인 유형(코드) 선택 미적용** |

---

## 2. 등록/수정 간 불일치 (핵심 문제)

등록 모달에만 적용된 기능이 수정 모달에는 빠져있음.

| 기능 | 등록 (add) | 수정 (update) |
|------|------------|---------------|
| 인라인 단어 등록 (Step 2) | O | **X** - 미등록 단어 시 페이지 이동만 가능 |
| 도메인 유형 선택 (일반/코드) | O | **X** - 일반 도메인만 선택 가능 |
| 코드 선택 autocomplete | O | **X** |
| lastWordIsCode computed | O | **X** - computed 자체가 없음 |

**영향**: 코드 도메인으로 등록한 용어를 수정하면, 코드 도메인 정보를 볼 수 없고 일반 도메인으로만 변경 가능.

---

## 3. 코드 중복 (구조적 문제)

등록/수정 로직이 거의 동일한데 각각 별도 변수와 메서드로 존재.

### 중복 데이터 변수 (약 40개)
```
addTerm_termNm / updateTerm_termNm
addTerm_wordListArr / updateTerm_wordListArr
addTerm_domainNm / updateTerm_domainNm
addTerm_allophSynmLst_arr / updateTerm_allophSynmLst_arr
... (20쌍 이상)
```

### 중복 메서드
```
addNextStep() / updateNextStep()           → 로직 동일
addFormReset() / updateFormReset()         → 유사
createWordToTerm() → if (addTermModalShow) / else if (updateTermModalShow)
collectSelectedItems() → 동일 패턴
moveItemUp/Down() → state 파라미터로 분기
```

**영향**: 한쪽에 기능 추가 시 다른쪽에도 동일 작업 필요 → 누락 발생 (현재 상태)

---

## 4. UX 개선 포인트

### 4-1. Step 2 → Step 3 전환 시 정보 손실
- Step 2에서 단어를 선택하고 Step 3으로 넘어간 후 다시 Step 2로 돌아가면 선택 상태가 유지되지만, Step 1로 가면 `addTerm_selected_word_list`가 초기화됨 (watch에서 처리)
- Step 1 → Step 2 재진입 시 용어명이 변경되지 않았더라도 API를 다시 호출함

### 4-2. 미등록 단어 처리 흐름
- **현재 (수정 모달)**: 미등록 단어 발견 → swal "단어 페이지로 이동할까요?" → 모달 닫히고 페이지 이동 → 단어 등록 → 다시 돌아와서 용어 수정 처음부터 시작
- **개선**: 등록 모달처럼 인라인 등록 제공

### 4-3. 도메인 자동 선택
- Step 3에서 마지막 단어의 `domainClsfNm`에 따라 도메인 목록이 필터링됨
- 하지만 자동 선택(첫 번째 항목)은 없어서 사용자가 매번 수동 선택
- 도메인 목록이 1개일 때는 자동 선택하면 편리

### 4-4. 용어 영문 약어명 확인
- 영문 약어명이 readonly로 자동생성되는데, 단어 순서 변경 후에도 올바르게 갱신되는지 확인 필요
- `createTermEngAbrvNm()`이 watch `addTerm_wordList`에서 호출되므로, splice로 순서 변경 시 watch가 트리거되어 정상 동작

### 4-5. 검색 기능
- 검색 초기화 버튼이 검색어만 초기화하고, 목록은 초기화하지 않음 (재검색 필요)
- `clearMessage()`가 세 필드를 모두 지우는데 각 필드의 `@click:clear`에서 동일 함수 호출 → 하나만 지우고 싶어도 전부 지워짐

---

## 5. 수정 모달 개선 필요 항목 정리

### 즉시 필요 (등록과 동기화)
1. **인라인 단어 등록**: Step 2에 `v-sheet` 인라인 폼 추가
2. **도메인 유형 선택**: `updateTerm_domainType` 데이터 추가 + 라디오 UI
3. **코드 선택 autocomplete**: `updateTerm_selectedCode`, `updateTerm_codeInfoList` 추가
4. **lastWordIsCode computed**: `updateTerm_lastWordIsCode` 추가
5. **코드그룹 조건부 숨김**: `v-if="updateTerm_domainType !== 'code'"`
6. **updateModalInit()에서 기존 코드 도메인 감지**: codeGrp가 있으면 domainType을 'code'로 설정

### 선택적 개선
7. **도메인 1건 자동 선택**: 도메인 목록이 1개면 자동 바인딩
8. **검색 초기화 동작 개선**: `clearMessage` → 각 필드 독립 초기화

---

## 6. 리팩토링 고려사항

현재 구조는 add/update 분기가 data 변수명 레벨에서 이루어지고 있어서, 기능 추가 시 항상 양쪽을 수정해야 함. 중장기적으로는:

**방안 A: 공용 데이터 객체**
```javascript
// formData 하나로 통합
formData: {
  mode: 'add', // or 'update'
  termNm: null,
  wordListArr: [],
  domainType: 'domain',
  ...
}
```

**방안 B: mixin 분리**
```javascript
// termFormMixin.js에 공통 로직 분리
// DSTerm.vue에서 mixin import
```

단, 현재 동작에 문제가 없고 등록/수정 간 동기화만 해결하면 당장은 충분하므로, 리팩토링은 기능 추가가 더 필요해질 때 검토.

---

## 7. 관련 파일

| 파일 | 역할 |
|------|------|
| DSTerm.vue (2350줄) | 용어 목록/등록/수정/삭제 화면 |
| NdModal.vue | 공통 모달 컴포넌트 |
| terms.xml | 용어 관련 쿼리 (검색, CRUD) |
| DataStandardController.java | 용어 API 컨트롤러 |
| StdTermsVo.java | 용어 VO |
| StdCodeInfoVo.java | 코드 VO (dataType, dataLen 추가됨) |
