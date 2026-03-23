# 04. q-executor 모듈

> 최종 수정: 2026-03-23

## 개요

백그라운드 배치 처리를 담당하는 모듈. q-center에서 WebClient로 호출.
외부 DB 접속(수집), 데이터 표준화 진단 등 시간이 오래 걸리는 작업을 처리.

- **포트**: 8090
- **처리 방식**: `Runnable` 구현체를 스레드로 실행

## 패키지 구조

```
q-executor/src/main/java/qualityexecutor/
├── QualityExecutorApplication.java
├── controller/
│   ├── DataControllerBase.java        베이스 컨트롤러
│   ├── DataModelController.java       /api/dm
│   ├── DataStandardController.java    /api/std
│   └── DiagController.java            /api/diag
└── service/
    ├── std/
    │   ├── DataModelService.java      데이터모델 수집
    │   ├── DataStandardService.java   표준 정보 처리
    │   └── DiagService.java           진단 처리
    └── stomp/
        └── StompSessionService.java   WebSocket 발송
```

## DataControllerBase

모든 컨트롤러의 부모 클래스. 서비스 실행 방식을 제공.

```java
// 비동기: 별도 스레드로 실행 (즉시 HTTP 응답 반환)
public void startService(Runnable svc) {
    appContext.getAutowireCapableBeanFactory().autowireBean(svc);
    new Thread(svc).start();
}

// 동기: 현재 스레드에서 실행 (처리 완료 후 HTTP 응답)
public void runService(Runnable svc) {
    appContext.getAutowireCapableBeanFactory().autowireBean(svc);
    svc.run();
}
```

> **사용 기준**:
> - `startService`: 수집 등 장시간 작업 (진행 상황을 WebSocket으로 전송)
> - `runService`: 업로드 등 (HTTP 응답 자체가 완료 신호)

## Controller 목록

### DataModelController (`/api/dm`)

| 메서드 | 엔드포인트 | 실행 방식 |
|--------|-----------|----------|
| POST | `/collectDataModel` | `startService` (비동기) |

### DataStandardController (`/api/std`)

| 메서드 | 엔드포인트 | 실행 방식 |
|--------|-----------|----------|
| POST | `/uploadWords` | `runService` (동기) |
| POST | `/uploadTermsList` | `runService` (동기) |
| POST | `/uploadDomains` | `runService` (동기) |
| POST | `/uploadCodeInfoList` | `runService` (동기) |
| POST | `/uploadDataModelObjs` | `runService` (동기) |

### DiagController (`/api/diag`)

| 메서드 | 엔드포인트 | 실행 방식 |
|--------|-----------|----------|
| POST | `/runDiag` | `startService` (비동기) |
| POST | `/stopDiag` | - (DiagService.requestStop 호출) |

## Service 상세

### DataModelService

외부 DB에서 테이블/컬럼 정보를 수집하여 내부 PostgreSQL에 저장.

**처리 흐름**
```
1. 데이터소스 정보 조회 (TB_DATA_SOURCE)
2. DataSourceUtils로 외부 DB 연결 (JDBC)
3. 스키마 필터 조회 (TB_DATA_MODEL_SCHEMA)
4. DBMS별 SQL로 테이블 목록 조회
5. 각 테이블의 컬럼 정보 조회
6. TB_DATA_MODEL_CLCT 수집 레코드 생성
7. TB_DATA_MODEL_OBJ 테이블 정보 저장 (batch)
8. TB_DATA_MODEL_ATTR 컬럼 정보 저장 (batch)
9. TB_DATA_MODEL_STATS 통계 계산 및 저장
10. StompSessionService로 완료 메시지 전송
```

**지원 DBMS별 테이블 조회 SQL**
- Oracle: `ALL_TABLES`, `ALL_TAB_COLUMNS`
- PostgreSQL: `information_schema.tables`, `information_schema.columns`
- MySQL/MariaDB: `information_schema.tables`, `information_schema.columns`
- MSSQL: `sys.tables`, `sys.columns`

### DataStandardService

Excel 업로드 처리.

**처리 흐름**
```
1. Excel 파일 파싱 (ExcelUploadService)
2. VO 리스트 변환
3. 기존 데이터 삭제 또는 upsert
4. MyBatis batch insert
5. HTTP 응답으로 완료 신호 반환
```

### DiagService

데이터 표준화 진단 처리. `Runnable` 구현, `@Service`.

**진단 유형**
| 코드 | 설명 |
|------|------|
| `TERM_NOT_EXIST` | 한글명/영문명 모두 표준 용어에 없음 |
| `TERM_KR_NM_MISMATCH` | 영문명은 있지만 한글명 불일치 |
| `TERM_ENG_NM_MISMATCH` | 한글명은 있지만 영문명 불일치 |
| `DATA_TYPE_MISMATCH` | 컬럼 데이터 타입이 도메인 표준과 불일치 |
| `DATA_LEN_MISMATCH` | 컬럼 길이가 도메인 표준보다 큼 |

**진단 로직**
```
1. 전체 용어 로드 → HashMap 구성
   - termsByKr: 한글명 → StdTermsVo
   - termsByEng: 영문명 → StdTermsVo
   - 동의어/이음이의어도 포함

2. 수집 컬럼별 진단:
   attrNmKr → termsByKr 조회
   attrNm   → termsByEng 조회

   if (termByKr == null && termByEng == null) → TERM_NOT_EXIST
   if (termByKr != null && termByEng == null) → TERM_ENG_NM_MISMATCH + checkDomain
   if (termByKr == null && termByEng != null) → TERM_KR_NM_MISMATCH + checkDomain
   else → checkDomain only

3. checkDomain (도메인 검증):
   - 용어 → domainNm → TB_DOMAIN.DATA_TYPE, DATA_LEN 조회
   - DATA_TYPE 비교 (case-insensitive)
   - DATA_LEN 비교 (컬럼 길이 > 표준 길이 → DATA_LEN_MISMATCH)

4. 결과 batch insert (50건 단위)
5. 진행률 업데이트 (processCnt, resultCnt)
```

**JOB 상태 전이**
```
READY → RUNNING → DONE
                → STOPPED (중지 요청 시, 결과 삭제)
                → ERROR (예외 발생 시)
```

**중지 처리**
```java
// 정적 Map으로 중지 플래그 관리
static ConcurrentHashMap<String, AtomicBoolean> stopFlags;

// q-center가 /stopDiag 호출 → requestStop(diagJobId)
// DiagService의 진단 루프에서 주기적으로 stopFlags 확인
// 중지 시: TB_DIAG_RESULT 삭제 → JOB 상태 STOPPED
```

### StompSessionService

WebSocket STOMP를 통해 UI에 진행 상황 전송.

```java
// 전체 공지
sendMessage("/topic/onMessage", payload)

// 개인 메시지
sendMessageToUser(userId, "/exchange/amq.direct/message.window", payload)
```

## 폴링 기반 진단 진행 확인

진단 중 UI는 3초마다 q-center `/api/diag/getDiagJobById`를 폴링하여 상태를 확인.

```
RUNNING  → 폴링 계속 (진행바 업데이트)
DONE     → 폴링 중단, 결과 조회
STOPPED  → 폴링 중단
ERROR    → 폴링 중단, 오류 표시
```
