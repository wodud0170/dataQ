# 데이터소스 수정 시 연결 테스트 NPE 이슈

**작성일**: 2026-04-19

## 증상

기존 등록된 Oracle 데이터소스(접속방식=SID, SID=ORCLCDB)를 **수정**하여
접속방식을 Service Name으로 바꾸고 dbName=XEPDB1로 변경한 뒤 **연결 테스트**를 누르면
프론트엔드에 "알 수 없는 오류가 발생했습니다" 모달이 뜨고, 서버 로그에 아래 NPE가 찍힘.

```
ERROR q.c.SysInfoController : test datasource failed
java.lang.NullPointerException: null
    at com.ndata.datasource.dbms.connection.DBConnectionManager$DBConnectionPool.getConnection(DBConnectionManager.java:228)
    at com.ndata.datasource.dbms.connection.DBConnectionManager.getConnection(DBConnectionManager.java:94)
    at com.ndata.datasource.dbms.connection.DBConnection.getConnection(DBConnection.java:158)
    at com.ndata.datasource.dbms.connection.DBConnection.getConnection(DBConnection.java:152)
    at com.ndata.datasource.dbms.connection.DBConnection.connect(DBConnection.java:118)
    at qualitycenter.controller.SysInfoController.testDataSource(SysInfoController.java:201)
```

## 재현 절차

1. Oracle 데이터소스를 SID 방식으로 등록 (예: SID=ORCLCDB, URL=`jdbc:oracle:thin:@host:1521:ORCLCDB`)
2. 한 번이라도 연결 테스트 성공 (서버의 DBConnectionManager 정적 맵에 풀이 캐시됨)
3. DB 기반 자체를 교체 (예: 컨테이너 교체, 방식이 Service Name으로 바뀜)
4. **동일 데이터소스 ID**를 수정하여 접속방식=Service Name, dbName=XEPDB1로 변경 후 저장
5. 연결 테스트 클릭 → NPE

## 회피 방법

- **새 데이터소스로 등록**하면 정상 동작 (새 dsn → 신규 풀 생성)
- 또는 q-center를 재시작하면 기존 등록건도 정상 동작 (정적 맵 초기화되기 때문)

## 원인 분석

### 핵심 원인
`com.ndata.datasource.dbms.connection.DBConnectionManager`는 **dsn(poolName) 기준의 static 싱글턴 맵**을 사용.

```java
private static Map<String, DBConnectionManager> instanceMap;
private static Map<String, Object> pools;
private static Map<String, Object> clients;
```

데이터소스 수정은 DB의 `TB_DATA_SOURCE` row만 update 할 뿐,
같은 dsn으로 캐시된 풀을 **무효화하지 않음**. 결과적으로:

- SID 시절에 만들어진 풀(`freeConnections`/`allConnections`에 옛 URL 연결 보유)이 그대로 재사용됨
- 이후 `pool.getConnection()` 호출 시 freeConnections의 첫 원소를 꺼내 `isClosed()` 확인
  → 어떤 사유로 null 또는 무효 상태가 된 커넥션이 남아있어 NPE 발생

### 소스 위치
- `qualitycenter.controller.SysInfoController#testDataSource` ([q-center/src/main/java/qualitycenter/controller/SysInfoController.java:186-219](../q-center/src/main/java/qualitycenter/controller/SysInfoController.java#L186-L219))
  - driverName 스왑(Service Name)은 정상 수행되나, 풀 캐시 정리 로직 없음
- `qualitycenter.controller.SysInfoController#updateDataSource`도 동일 (update 전/후 풀 close 없음)
- 근본 소재는 `common-0.0.1-SNAPSHOT.jar`의 `DBConnectionManager` 정적 캐시 설계

## 파급 영향

| 구분 | 영향 |
|------|------|
| 운영 UX | 사용자가 이유를 알 수 없는 "알 수 없는 오류" 모달만 봄 — 디버깅 어려움 |
| 수집/진단 | 데이터소스 수정 후 수집/진단 Job이 옛 풀로 시도하여 연결 실패 가능성 |
| 회피책 | 매번 q-center 재시작 또는 신규 등록 — 현장 운영 부적합 |

## 수정 방향 (3가지 옵션)

### A. testDataSource 진입 시 기존 풀 invalidate (최소 변경, 권장)
`SysInfoController.testDataSource`에서 `DBConnection` 생성 **직전에** 해당 dsn의 풀을 close.

```java
// 기존 풀 정리 (수정으로 연결정보가 바뀐 경우 대비)
try {
    DBConnectionManager oldMgr = DBConnectionManager.getInstance(dataVo.getDsn(), 0);
    oldMgr.close();  // 풀/커넥션 정리, 정적 맵에서 제거
} catch (Exception ignore) { /* 최초 테스트 시엔 풀이 없음 */ }
```

**주의**: `DBConnectionManager.close()`가 `instanceMap`/`pools`/`clients`에서 완전 제거하는지 jar 내부 동작 확인 필요. 아니면 별도 invalidate 메서드 요청하거나 리플렉션으로 직접 정리.

### B. updateDataSource 성공 직후 풀 invalidate
`SysInfoController.updateDataSource` 후반부에 동일 로직 추가 — 수정 저장 시점에 옛 풀을 날림.
사용자가 수정만 하고 테스트 안 해도 수집/진단이 신규 연결정보로 동작한다는 장점.

### C. common jar 수정
`DBConnectionManager`에 공식 `invalidate(String dsn)` 추가. 근본 수정이지만 lib 빌드 권한/계획 필요.

### 권장
단기: **A + B** 동시 적용 (컨트롤러 레벨만 변경, jar 수정 불필요).
장기: C로 이관하여 연결 캐시 생명주기를 명시적으로 관리.

## 확인이 필요한 항목

- [ ] `DBConnectionManager.close()`가 정적 맵에서 풀을 실제로 제거하는지 (남아있으면 getInstance 재호출 시 옛 풀 재사용)
- [ ] 수정이 아닌 **삭제 후 재등록** 시에도 같은 dsn 재활용 가능한지 (동일 id 재사용 여부)
- [ ] q-executor 수집 Job도 동일 풀 캐시를 사용하는지 (사용한다면 Job 시작 전에도 invalidate 필요)

## 관련 파일

- [SysInfoController.java:186-219](../q-center/src/main/java/qualitycenter/controller/SysInfoController.java#L186-L219) — testDataSource
- [drivers.xml](../lib/drivers.xml) — Oracle(Service Name) 드라이버 정의 (이미 등록됨)
- [오라클_테스트환경_접속정보.md](테스트/오라클_테스트환경_접속정보.md) — 재현에 사용한 Oracle XE 환경

## 검증 케이스

NPE 재현에 사용한 데이터소스:
- dsn: `ORACLE19TEST`
- 이전: connProps=SID, dbName=ORCLCDB (doctorkirk/oracle-19c 컨테이너)
- 수정 후: connProps=Service Name, dbName=XEPDB1 (gvenzl/oracle-xe 컨테이너)
- 결과: 연결 테스트 → NPE → 신규 등록(`ORACLE_XE` 등)으로 회피
