# Oracle 테스트 환경 접속 정보

**생성일**: 2026-04-07

## Docker 컨테이너

| 항목 | 값 |
|------|-----|
| 이미지 | gvenzl/oracle-xe:21-slim |
| 컨테이너명 | oracle-test |
| 포트 | 1521 (호스트) → 1521 (컨테이너) |
| SID | XE |
| PDB | XEPDB1 |

## 접속 정보

### SYS (관리자)
| 항목       | 값          |
| -------- | ---------- |
| Host     | localhost  |
| Port     | 1521       |
| SID      | XE         |
| Username | SYS        |
| Password | oracle1234 |
| Role     | SYSDBA     |

### TESTUSER (DBA 권한 테스트 계정)
| 항목           | 값                                       |
| ------------ | --------------------------------------- |
| Host         | localhost                               |
| Port         | 1521                                    |
| Service Name | XEPDB1                                  |
| Username     | TESTUSER                                |
| Password     | test1234                                |
| JDBC URL     | jdbc:oracle:thin:@localhost:1521/XEPDB1 |

## 테스트 스키마

- TESTUSER 스키마에 50개 테이블 생성
- 인사/급여/조직/프로젝트/자산/계약 등 업무 도메인 기반
- 각 테이블 3~10개 컬럼, PK 포함

## DataQ 데이터소스 등록

1. 관리 > 데이터 소스 > 추가
2. 접속 정보 입력 (위 TESTUSER 정보)
3. 데이터 모델 > 수집 실행
