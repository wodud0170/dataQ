# 데이터소스 SID/Service Name 수정 이력

**작업일**: 2026-04-07
**소요**: 토큰 과다 소모. 아래 삽질 기록 참고.

---

## 요구사항

Oracle/Tibero 접속 시 SID와 Service Name을 선택할 수 있어야 함.
- SID: `jdbc:oracle:thin:@host:port:SID`
- Service Name: `jdbc:oracle:thin:@//host:port/ServiceName`

---

## 최종 해결 (이것만 하면 됐음)

### 1. drivers.xml (q-center/lib, q-executor/lib)
Oracle/Tibero에 Service Name용 드라이버 추가:
```xml
<!-- 기존 SID 방식 -->
<driver>
  <driverType>Oracle</driverType>
  <driverName>Oracle</driverName>
  <urlType>jdbc:oracle:thin:@%s:%d:%s</urlType>
</driver>
<!-- Service Name 방식 추가 -->
<driver>
  <driverType>Oracle</driverType>
  <driverName>Oracle(Service Name)</driverName>
  <urlType>jdbc:oracle:thin:@//%s:%d/%s</urlType>
</driver>
```

### 2. MMDatasource.vue (프론트)
- Oracle/Tibero 선택 시 "접속 방식" 셀렉트박스 표시 (SID / Service Name)
- 저장/수정/테스트 시 Service Name이면 내부적으로 driverName을 `Oracle(Service Name)`으로 변환
- 수정 화면 로드 시 DB의 driverName에서 `(Service Name)` 제거하여 표시, connType 자동 세팅

### 3. 코드 수정 없음
- DataSourceUtils.java 수정 불필요
- SysInfoController.java 수정 불필요
- lib jar 수정 불필요

---

## 삽질 기록 (하지 말았어야 할 것들)

### 삽질 1: DataSourceVo에 connType 필드 추가 시도
- DataSourceVo가 lib jar 안에 있어서 필드 추가 불가
- jar 디컴파일, 바이트코드 분석 등 불필요한 작업 수행
- **결론: 불필요했음**

### 삽질 2: connProps에 "Service Name" 저장 + dbName 앞에 "/" 붙이기
- DataSourceUtils.getDBHandler()에서 connProps 확인 후 dbName 앞에 "/" 추가
- lib 내부 URL 생성이 `@host:port:dbName` 형태라 "/" 붙여도 `@host:port:/XEPDB1`이 되어 포트 파싱 에러
- SysInfoController.testDataSource()에서도 동일 로직 추가했으나 동일 실패
- **결론: lib의 URL 생성 방식을 모르고 추측으로 코딩. 완전 실패.**

### 삽질 3: lib jar 바이트코드 분석
- DBConnection.class, DBHandlerForOracle.class 등 디컴파일 시도
- 의존성 문제로 TestUrl.java 실행 실패 (slf4j, logback 등)
- **결론: drivers.xml만 열어봤으면 5분 만에 끝날 일.**

### 삽질 4: drivers.xml 발견 후 Oracle(SID)/Oracle(Service Name) 분리
- 드라이버명을 바꿨더니 기존 DB의 driverName과 불일치
- 수정 화면에서 드라이버 목록 빈칸
- 드라이버 목록 자동 로드 추가 → 또 다른 문제 발생
- **결론: 수정 화면에서 DB값 그대로 표시하면 끝. 목록 API 호출 불필요.**

---

## 교훈

1. **설정 파일부터 확인하라.** drivers.xml에 URL 패턴이 명시되어 있었음.
2. **lib 내부 코드를 추측하지 마라.** 바이트코드 분석보다 설정 파일 한번 열어보는 게 빠름.
3. **프론트는 단순하게.** 사용자가 선택한 값을 DB에서 불러와서 그대로 보여주면 됨.
4. **한 가지 방법이 실패하면 접근을 바꿔라.** dbName에 "/" 붙이기 실패 → 다른 방법 대신 같은 방식 반복.
