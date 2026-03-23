# 02. q-common 모듈

> 최종 수정: 2026-03-23

## 개요

모든 모듈이 공통으로 사용하는 VO 클래스, MyBatis Mapper XML, 유틸리티를 담은 공통 모듈.
`mvn install`로 로컬 Maven 저장소에 JAR 설치 후 q-center, q-executor에서 의존.

## 패키지 구조

```
q-common/src/main/java/com/ndata/quality/
├── common/           상수 및 열거형
├── model/
│   └── std/          VO 클래스 (도메인 모델)
├── service/          공통 서비스 (Excel)
└── tool/             유틸리티

q-common/src/main/resources/mapper/stnd/
└── *.xml             MyBatis Mapper XML (9개)
```

## VO 클래스 목록

### 데이터모델

| 클래스 | 테이블 | 주요 필드 |
|--------|--------|-----------|
| `StdDataModelVo` | TB_DATA_MODEL | dmId, dmNm, dmSysCd, dmDsId, ver, useYn |
| `StdDataModelCollectVo` | TB_DATA_MODEL_CLCT | dmClctId, dmId, clctStartDt, clctEndDt, clctCmptnYn |
| `StdDataModelObjVo` | TB_DATA_MODEL_OBJ | objNm, objNmKr, dmClctId |
| `StdDataModelAttrVo` | TB_DATA_MODEL_ATTR | attrNm, attrNmKr, dataType, dataLen, dataDecimalLen, nullableYn, pkYn, fkYn, defaultVal, termsStndYn, wordLst, wordStndLst, dmClctId |
| `StdDataModelStatsVo` | TB_DATA_MODEL_STATS | dmClctId, objCnt, attrCnt, totalStndRate, termsStndRate, wordStndRate, domainStndRate |
| `StdDataModelSchemaVo` | TB_DATA_MODEL_SCHEMA | dmId, schemaNm |

### 표준 사전

| 클래스 | 테이블 | 주요 필드 |
|--------|--------|-----------|
| `StdDomainVo` | TB_DOMAIN | domainId, domainNm, domainGrpNm, domainClsfNm, domainDesc, dataType, dataLen, dataDecimalLen, dataUnit, storFmt, exprFmtLst, allowValLst, commStndYn, aprvYn, aprvUserId |
| `StdDomainGroupVo` | TB_DOMAIN (group 집계) | domainGrpNm |
| `StdDomainClassificationVo` | TB_DOMAIN (clsf 집계) | domainClsfNm |
| `StdTermsVo` | TB_TERMS | termsId, termsNm, termsEngAbrvNm, domainNm, allophSynmLst, aprvYn |
| `StdWordVo` | TB_WORD | wordId, wordNm, wordMeaning, wordCategory, aprvYn |
| `StdCodeInfoVo` | TB_CODE_INFO | codeId, codeNm, codeDesc |
| `StdCodeDataVo` | TB_CODE_DATA | codeDataId, codeId, codeValue, codeDesc |

### 진단

| 클래스 | 테이블 | 주요 필드 |
|--------|--------|-----------|
| `StdDiagJobVo` | TB_DIAG_JOB | diagJobId, clctId, dataModelId, dataModelNm, clctDt, status, totalCnt, processCnt, resultCnt, cretDt, cretUserId, startDt, endDt |
| `StdDiagResultVo` | TB_DIAG_RESULT | resultId, diagJobId, objNm, attrNm, attrNmKr, diagType, diagDetail, stdValue, actualValue |

### 기타

| 클래스 | 설명 |
|--------|------|
| `StdApproveStatVo` | 승인 상태 정보 |
| `UploadResult` | Excel 업로드 처리 결과 |
| `SysInfoVo` | 시스템 정보 |
| `NDQualityRetrieveCond` | 공통 조회 조건 (검색어, 페이징 등) |
| `NDQualityConstant` | 상수 (서비스 URL 등) |
| `NDQualityApproveStat` | 승인 상태 열거형 (APRV_Y, APRV_N 등) |
| `NDQualityStdObjectType` | 표준 객체 타입 열거형 |

## MyBatis Mapper XML

### Mapper 스캔 설정
```yaml
# q-center, q-executor application.yml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
```
`classpath*:`를 사용해 q-common JAR 내부의 XML까지 스캔.

### Mapper 목록

| XML 파일 | Namespace | 대상 테이블 |
|----------|-----------|------------|
| `datamodel.xml` | `dataModelMapper` | TB_DATA_MODEL, TB_DATA_MODEL_CLCT, TB_DATA_MODEL_STATS, TB_DATA_MODEL_OBJ, TB_DATA_MODEL_ATTR, TB_DATA_MODEL_SCHEMA |
| `domain.xml` | `domainMapper` | TB_DOMAIN |
| `terms.xml` | `termsMapper` | TB_TERMS |
| `word.xml` | `wordMapper` | TB_WORD |
| `codedata.xml` | `codeDataMapper` | TB_CODE_INFO, TB_CODE_DATA |
| `approve.xml` | `approveMapper` | TB_APPROVE_STAT |
| `diag.xml` | `diagMapper` | TB_DIAG_JOB, TB_DIAG_RESULT |
| `search.xml` | `searchMapper` | 통합 검색 |
| `sysinfo.xml` | `sysInfoMapper` | TB_SYS_INFO, TB_DATA_SOURCE |

### 주요 쿼리 패턴

**datamodel.xml 주요 쿼리**
```
selectDataModelStatsList     - 현황 목록 (통계 포함, 최신 수집 기준)
selectDataModelList          - 데이터모델 기본 목록
selectDataModelClctList      - 수집이력 목록
selectDataModelObjList       - 테이블 목록 (수집ID 기준)
selectDataModelAttrList      - 컬럼 목록 (수집ID 기준)
insertDataModelClct          - 수집 레코드 생성
updateDataModelClctComplete  - 수집 완료 처리
insertDataModelObj           - 테이블 저장 (batch)
insertDataModelAttr          - 컬럼 저장 (batch)
```

**diag.xml 주요 쿼리**
```
insertDiagJob                - 진단 JOB 생성
updateDiagJobStatus          - 상태 변경 (READY/RUNNING/DONE/STOPPED/ERROR)
updateDiagJobProgress        - 진행률 업데이트 (processCnt, resultCnt)
selectDiagJobList            - JOB 목록 (STOPPED 제외)
selectDiagJobById            - 단건 조회 (폴링용)
insertDiagResult             - 진단 결과 저장 (batch 50건)
deleteDiagResultsByJobId     - 결과 삭제 (STOP 시)
selectDiagResultList         - 컬럼 상세 목록
selectDiagResultSummary      - 테이블 집계 (GROUP BY OBJ_NM)
```

**terms.xml 주요 쿼리**
```
selectAllTermsForDiag        - 진단용 전체 용어 로드 (도메인 정보 JOIN)
  - TB_TERMS LEFT JOIN TB_DOMAIN ON DOMAIN_NM
  - NO UNNEST (용어 1건당 1행)
```

## 공통 서비스

### ExcelUploadService
- Apache POI 기반 Excel 파싱
- 시트 → VO 리스트 변환
- 지원 형식: `.xlsx`, `.xls`

### ExcelDownloadService
- VO 리스트 → Excel 파일 생성
- 다운로드 응답 처리

### DataSourceUtils
외부 DB 연결 지원 DBMS:
| DBMS | Driver |
|------|--------|
| Oracle | oracle.jdbc.OracleDriver |
| PostgreSQL | org.postgresql.Driver |
| MySQL | com.mysql.jdbc.Driver |
| MariaDB | org.mariadb.jdbc.Driver |
| MSSQL | com.microsoft.sqlserver.jdbc.SQLServerDriver |
| Cubrid | cubrid.jdbc.driver.CUBRIDDriver |

### StringWordAnalyzer
- KOMORAN 기반 한글 형태소 분석
- 용어 토큰화에 사용
