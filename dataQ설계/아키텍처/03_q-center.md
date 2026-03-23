# 03. q-center 모듈

> 최종 수정: 2026-03-23

## 개요

사용자가 접속하는 웹 포탈 모듈. Spring Boot 기반 REST API 서버와 Vue.js 프론트엔드를 함께 제공.
백그라운드 처리가 필요한 작업은 q-executor로 위임(WebClient 호출).

## 패키지 구조

```
q-center/src/main/java/qualitycenter/
├── QualityCenterApplication.java
├── config/
│   ├── HttpWebServerConfig.java
│   ├── MyBatisConfig.java
│   ├── SwaggerConfig.java
│   ├── WebSocketConfiguration.java
│   ├── WebResourceConfig.java
│   ├── SocketDestinationPrincipal.java
│   └── UsernameUserDestinationResolver.java
├── controller/
│   ├── DataModelController.java      /api/dm
│   ├── DataStandardController.java   /api/std
│   ├── LoginController.java          /api/login
│   ├── DiagController.java           /api/diag
│   ├── SysInfoController.java        /api/sysinfo
│   ├── SearchController.java         /api/search
│   └── WebSocketController.java      STOMP
├── security/
│   ├── WebSecurityConfiguration.java
│   ├── CustomAuthenticationProvider.java
│   ├── CustomAuthSuccessHandler.java
│   ├── CustomAuthFailureHandler.java
│   ├── CustomLogoutHandler.java
│   └── HttpHandshakeInterceptor.java
└── service/
    ├── auth/SessionService.java
    └── ws/WebSocketService.java
```

## Controller 목록

### DataModelController (`/api/dm`)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/createDataModel` | 데이터모델 생성 |
| POST | `/updateDataModel` | 데이터모델 수정 |
| POST | `/deleteDataModels` | 데이터모델 삭제 |
| POST | `/getDataModelStatsList` | 현황 목록 (통계 포함) |
| GET | `/getDataModelStatsByClctId` | 수집ID로 현황 조회 |
| POST | `/getDataModelList` | 데이터모델 기본 목록 |
| POST | `/getDataModelClctList` | 수집이력 목록 |
| GET | `/getDataModelObjListByClctId` | 테이블 목록 |
| POST | `/getDataModelObjListByObjNm` | 테이블 검색 |
| GET | `/downloadDataModelObjs` | 테이블 Excel 다운로드 |
| GET | `/getDataModelAttrListByClctId` | 컬럼 목록 |
| POST | `/getDataModelAttrListByRetreiveCond` | 컬럼 검색 |
| GET | `/downloadDataModelAttrs` | 컬럼 Excel 다운로드 |
| POST | `/getSchemaList` | 데이터소스 스키마 목록 |
| POST | `/getDataModelSchemas` | 스키마 필터 조회 |
| POST | `/saveDataModelSchemas` | 스키마 필터 저장 |
| POST | `/getDataModelHistoryList` | 수집이력 조회 |
| POST | `/collectDataModel` | 수집 시작 (→ q-executor 위임) |

### DataStandardController (`/api/std`)

**단어**
| 메서드 | 엔드포인트 |
|--------|-----------|
| POST | `/createWord`, `/updateWord`, `/deleteWords` |
| GET/POST | `/getWordList`, `/getWordInfoByNm`, `/getWordInfoById` |
| POST | `/uploadWords` |
| GET | `/downloadWords` |

**용어**
| 메서드 | 엔드포인트 |
|--------|-----------|
| POST | `/createTerms`, `/updateTerms`, `/deleteTermsList` |
| GET/POST | `/getTermsList`, `/getTermsTokensByNm`, `/getTermsListByCond` |
| GET | `/getTermsInfoByNm`, `/getTermsWordInfoList` |
| POST | `/uploadTermsList` |
| GET | `/downloadTermsList` |

**코드**
| 메서드 | 엔드포인트 |
|--------|-----------|
| GET/POST | `/getCodeInfoList`, `/getCodeInfoListByNm` |
| POST | `/createCode`, `/updateCode`, `/deleteCodeList` |
| POST | `/uploadCodeInfoList` |
| GET | `/downloadCodeInfoList`, `/getCodeDataList` |

**도메인**
| 메서드 | 엔드포인트 |
|--------|-----------|
| POST | `/createDomain`, `/updateDomain`, `/deleteDomains` |
| GET/POST | `/getDomainList`, `/getDomainInfoByNm` |
| GET/POST | `/getDomainGroupList`, `/getDomainClsfList` |
| POST | `/uploadDomains` |
| GET | `/downloadDomains` |

### DiagController (`/api/diag`)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/getDiagJobList` | JOB 목록 조회 (STOPPED 제외) |
| GET | `/getDiagJobById` | JOB 단건 조회 (폴링용) |
| GET | `/getDiagResultList` | 컬럼 상세 결과 |
| GET | `/getDiagResultSummary` | 테이블 집계 결과 |
| POST | `/startDiag` | 진단 시작 (JOB 생성 → q-executor 위임) |
| POST | `/stopDiag` | 진단 중지 (→ q-executor 위임) |

**startDiag 처리 흐름**
```
1. TB_DIAG_JOB INSERT (status=READY)
2. q-executor /api/diag/runDiag 호출 (WebClient)
3. Response.contents = diagJobId 반환
```

### LoginController (`/api/login`)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/login` | 로그인 |
| POST | `/logout` | 로그아웃 |
| GET | `/checkSession` | 세션 확인 |

### SysInfoController (`/api/sysinfo`)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET/POST | `/getSysInfoList` | 시스템 목록 |
| GET | `/getSysInfoById` | 시스템 단건 |
| POST | `/createSysInfo`, `/updateSysInfo`, `/deleteSysInfo` | CRUD |
| GET/POST | `/getDataSourceList` | 데이터소스 목록 |
| POST | `/createDataSource`, `/updateDataSource`, `/deleteDataSource` | CRUD |
| POST | `/validateDataSource` | DB 연결 테스트 |

## Spring Security 설정

### 인증 방식
- **Form Login** (JSESSIONID 세션)
- `CustomAuthenticationProvider`: DB에서 사용자 조회 및 비밀번호 검증

### 공개 엔드포인트 (인증 불필요)
```
/, /login, /logout
/swagger-ui/**, /v3/api-docs/**
/api/login/**
/api/search/**
/api/std/**
/api/dm/**
/api/sysinfo/**
/api/diag/**
/api/admin/**
/sockjs/**
```

### 핸들러
| 클래스 | 역할 |
|--------|------|
| `CustomAuthSuccessHandler` | 로그인 성공 → 세션 저장 |
| `CustomAuthFailureHandler` | 로그인 실패 → 오류 응답 |
| `CustomLogoutHandler` | 로그아웃 → 세션 삭제 |
| `HttpHandshakeInterceptor` | WebSocket 핸드셰이크 시 인증 확인 |

## Response 객체 구조

```java
// q-center의 공통 응답 객체
Response {
  int resultCode;       // 200: 성공, 기타: 실패
  String resultMessage; // 메시지
  Object contents;      // 반환 데이터 (res.data.contents 로 접근)
}
```

> **주의**: `setData()` 메서드 없음. 데이터 반환 시 `res.setContents(data)` 사용.

## WebSocket 구조

```
WebSocketConfiguration.java
  - STOMP 브로커: RabbitMQ (amq.direct)
  - 엔드포인트: /sockjs (SockJS fallback 지원)

구독 토픽:
  /topic/onMessage                         - 전체 공지
  /user/exchange/amq.direct/message.window - 개인 메시지 (업로드 완료 등)
```

> **중요**: 장시간 DB 처리 후 WebSocket 완료 메시지가 유실되는 문제가 있었음.
> 업로드 처리는 q-executor에서 `runService` (동기)로 실행하여 HTTP 응답을 완료 신호로 사용.

## 프론트엔드 (Vue.js)

상세 내용은 [07_프론트엔드.md](./07_프론트엔드.md) 참조.

### 주요 설정 (main.js)
- Vue 2.x + Vuetify 2.x
- `this.$APIURL.base`: API 호출 시 반드시 이 prefix 사용
  ```js
  // 올바른 예
  axios.get(this.$APIURL.base + 'api/dm/getDataModelStatsList')
  // 잘못된 예
  axios.get('/api/dm/getDataModelStatsList')
  ```
- WebSocket: 세션 확인 후 자동 연결
- EventBus (`eventBus.js`): 컴포넌트 간 이벤트 통신
