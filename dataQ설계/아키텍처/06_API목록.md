# 06. API 목록

> 최종 수정: 2026-03-23
> 기준: q-center (포탈) REST API

## 공통 응답 형식

```json
{
  "resultCode": 200,
  "resultMessage": "성공",
  "contents": {}
}
```

- `resultCode`: 200 성공 / 기타 실패
- `contents`: 반환 데이터 (Vue에서 `res.data.contents`로 접근)

---

## 데이터모델 API (`/api/dm`)

| 메서드 | 경로 | 파라미터 | 설명 |
|--------|------|---------|------|
| POST | `/createDataModel` | StdDataModelVo | 데이터모델 생성 |
| POST | `/updateDataModel` | StdDataModelVo | 데이터모델 수정 |
| POST | `/deleteDataModels` | dmId[] | 데이터모델 삭제 |
| POST | `/getDataModelStatsList` | NDQualityRetrieveCond | 현황 목록 (통계 포함, 최신 수집 기준) |
| GET | `/getDataModelStatsByClctId` | clctId | 수집ID로 현황 조회 |
| POST | `/getDataModelList` | NDQualityRetrieveCond | 기본 목록 |
| POST | `/getDataModelClctList` | dmId, startDt, endDt | 수집이력 목록 |
| GET | `/getDataModelObjListByClctId` | clctId | 테이블 목록 |
| POST | `/getDataModelObjListByObjNm` | objNm, dmClctId | 테이블 검색 |
| GET | `/downloadDataModelObjs` | clctId | 테이블 Excel 다운로드 |
| GET | `/getDataModelAttrListByClctId` | clctId | 컬럼 목록 |
| POST | `/getDataModelAttrListByRetreiveCond` | NDQualityRetrieveCond | 컬럼 검색 |
| GET | `/downloadDataModelAttrs` | clctId | 컬럼 Excel 다운로드 |
| POST | `/getSchemaList` | dsId | 데이터소스 스키마 목록 |
| POST | `/getDataModelSchemas` | dmId | 스키마 필터 조회 |
| POST | `/saveDataModelSchemas` | dmId, schemaNm[] | 스키마 필터 저장 |
| POST | `/getDataModelHistoryList` | NDQualityRetrieveCond | 수집이력 조회 |
| POST | `/collectDataModel` | dmId | 수집 시작 (→ q-executor 비동기) |

---

## 데이터 표준 API (`/api/std`)

### 단어
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/createWord` | 단어 생성 |
| POST | `/updateWord` | 단어 수정 |
| POST | `/deleteWords` | 단어 삭제 |
| GET/POST | `/getWordList` | 목록 조회 |
| GET | `/getWordInfoByNm` | 이름으로 단건 조회 |
| GET | `/getWordInfoById` | ID로 단건 조회 |
| POST | `/uploadWords` | Excel 업로드 |
| GET | `/downloadWords` | Excel 다운로드 |

### 용어
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/createTerms` | 용어 생성 |
| POST | `/updateTerms` | 용어 수정 |
| POST | `/deleteTermsList` | 용어 삭제 |
| GET/POST | `/getTermsList` | 목록 조회 |
| GET | `/getTermsTokensByNm` | 토큰 조회 |
| GET | `/getTermsTokenListByNm` | 토큰 목록 조회 |
| POST | `/getTermsListByCond` | 조건 검색 |
| GET | `/getTermsInfoByNm` | 이름으로 단건 조회 |
| GET | `/getTermsWordInfoList` | 용어-단어 정보 목록 |
| POST | `/uploadTermsList` | Excel 업로드 |
| GET | `/downloadTermsList` | Excel 다운로드 |

### 코드
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET/POST | `/getCodeInfoList` | 코드 목록 |
| GET | `/getCodeInfoListByNm` | 이름으로 검색 |
| POST | `/createCode` | 코드 생성 |
| POST | `/updateCode` | 코드 수정 |
| POST | `/deleteCodeList` | 코드 삭제 |
| POST | `/uploadCodeInfoList` | Excel 업로드 |
| GET | `/downloadCodeInfoList` | Excel 다운로드 |
| GET | `/getCodeDataList` | 코드값 목록 |

### 도메인
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/createDomain` | 도메인 생성 |
| POST | `/updateDomain` | 도메인 수정 |
| POST | `/deleteDomains` | 도메인 삭제 |
| GET/POST | `/getDomainList` | 목록 조회 |
| GET | `/getDomainInfoByNm` | 이름으로 단건 조회 |
| GET/POST | `/getDomainGroupList` | 도메인 그룹 목록 |
| GET/POST | `/getDomainClsfList` | 도메인 분류 목록 |
| POST | `/uploadDomains` | Excel 업로드 |
| GET | `/downloadDomains` | Excel 다운로드 |

---

## 진단 API (`/api/diag`)

| 메서드 | 경로 | 파라미터 | 설명 |
|--------|------|---------|------|
| POST | `/getDiagJobList` | - | JOB 목록 (STOPPED 제외) |
| GET | `/getDiagJobById` | diagJobId | JOB 단건 조회 (폴링) |
| GET | `/getDiagResultList` | diagJobId | 컬럼 상세 결과 목록 |
| GET | `/getDiagResultSummary` | diagJobId | 테이블 집계 결과 |
| POST | `/startDiag` | clctId, dataModelId, dataModelNm, clctDt | 진단 시작 |
| POST | `/stopDiag` | diagJobId | 진단 중지 |

---

## 로그인 API (`/api/login`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/login` | 로그인 (id, password) |
| POST | `/logout` | 로그아웃 |
| GET | `/checkSession` | 세션 확인 (로그인 상태 체크) |

---

## 시스템 정보 API (`/api/sysinfo`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET/POST | `/getSysInfoList` | 시스템 목록 |
| GET | `/getSysInfoById` | 시스템 단건 |
| POST | `/createSysInfo` | 시스템 생성 |
| POST | `/updateSysInfo` | 시스템 수정 |
| POST | `/deleteSysInfo` | 시스템 삭제 |
| GET/POST | `/getDataSourceList` | 데이터소스 목록 |
| POST | `/createDataSource` | 데이터소스 생성 |
| POST | `/updateDataSource` | 데이터소스 수정 |
| POST | `/deleteDataSource` | 데이터소스 삭제 |
| POST | `/validateDataSource` | DB 연결 테스트 |

---

## 검색 API (`/api/search`)

| 메서드 | 경로 | 파라미터 | 설명 |
|--------|------|---------|------|
| GET/POST | `/search` | keyword | 전체 통합 검색 |

---

## q-executor API (내부 호출 전용)

q-center에서만 호출. 외부 직접 호출 불가.

| 모듈 | 경로 | 설명 |
|------|------|------|
| `/api/dm` | `/collectDataModel` | 데이터모델 수집 (비동기) |
| `/api/std` | `/upload*` | Excel 업로드 처리 (동기) |
| `/api/diag` | `/runDiag` | 진단 실행 (비동기) |
| `/api/diag` | `/stopDiag` | 진단 중지 |

---

## Vue에서 API 호출 시 주의사항

```js
// 반드시 this.$APIURL.base prefix 사용
const url = this.$APIURL.base + 'api/dm/getDataModelStatsList';
axios.post(url, params)
  .then(res => {
    // 데이터는 res.data.contents
    const data = res.data.contents;
  });
```
