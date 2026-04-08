# 데이터소스 SID/Service Name - 최종 상태

**일시**: 2026-04-07 17:30

---

## 수정 완료 항목 (빌드 완료, 재시작 필요)

### 1. sysinfo.xml - selectDataSourceList에 CONN_PROPS 추가
- **원인**: API 목록 조회 시 connProps가 null로 반환 → 프론트에서 접속 방식을 알 수 없음
- **수정**: `CONN_PROPS as connProps` 컬럼 추가
- **파일**: q-common/src/main/resources/mapper/stnd/sysinfo.xml

### 2. drivers.xml - Oracle(Service Name) 드라이버 추가
- **파일**: q-center/lib/drivers.xml, q-executor/lib/drivers.xml
- Oracle(SID): `jdbc:oracle:thin:@%s:%d:%s`
- Oracle(Service Name): `jdbc:oracle:thin:@//%s:%d/%s`
- Tibero도 동일 구조 추가

### 3. DataSourceUtils.java - connProps 기반 driverName 임시 변경
- Service Name이면 `Oracle(Service Name)` 드라이버로 매칭
- 접속 후 원복

### 4. SysInfoController.testDataSource - 동일 처리

### 5. DataModelController.getSchemaList - dataSourceUtils 사용
- 직접 DBHandler.getDBHandler 호출 → dataSourceUtils.getDBHandler 사용

### 6. 프론트 MMDatasource.vue
- driverName 조작 제거 (applyConnTypeToDriverName 삭제)
- connProps에만 SID/Service Name 저장
- 수정 화면에서 connProps 값으로 접속 방식 로드

### 7. datamodel.xml - Oracle 스키마 조회 SQL 변경
- `SELECT USER FROM DUAL` → `SELECT DISTINCT OWNER FROM ALL_TAB_COMMENTS`

---

## 테스트 방법

q-center 재시작 후:
```
cd ndata-quality-master
python test_datasource.py
```

성공 기준:
1. connProps가 "Service Name"으로 정상 반환
2. 연결 테스트 성공 (12505 에러 없음)
3. 스키마 목록 1개 이상 조회

---

## DB 현재 상태
```
dsn=오라클19c테스트 | driver_nm=Oracle | conn_props=Service Name | db_name=XEPDB1
dsn=회사oracle      | driver_nm=Oracle | conn_props=SID          | db_name=EE
```
