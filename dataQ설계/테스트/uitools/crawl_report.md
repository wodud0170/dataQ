# Selenium 메뉴 크롤링 리포트

- 사용자: `space`
- 메뉴 수: **31**
- 정상 진입: **31** / 미노출/실패: **0**

| # | 그룹 | 메뉴 | ID | 상태 | 응답(s) | 버튼 | 입력 | 행 | 에러 |
|---|---|---|---|---|---|---|---|---|---|
| 1 |  | 대시보드 | `nav_dashboard` | OK | 2.69 | 1 | 2 | 0 |  |
| 2 | 데이터 표준 사전 | 코드 | `nav_dsCode` | OK | 2.8 | 7 | 5 | 28 |  |
| 3 | 데이터 표준 사전 | 도메인 사전 | `nav_domain` | OK | 2.73 | 7 | 6 | 28 |  |
| 4 | 데이터 표준 사전 | 도메인 분류 | `nav_domainClassification` | OK | 2.7 | 7 | 3 | 20 |  |
| 5 | 데이터 표준 사전 | 도메인 그룹 | `nav_domainGroup` | OK | 2.67 | 5 | 3 | 12 |  |
| 6 | 데이터 표준 사전 | 변경 이력 | `nav_changeHistory` | OK | 2.71 | 2 | 6 | 20 |  |
| 7 | 데이터 모델 | 테이블 | `nav_datamodelStatusTable` | OK | 2.71 | 5 | 5 | 1 |  |
| 8 | 데이터 모델 | 컬럼 | `nav_datamodelStatusColumn` | OK | 2.71 | 9 | 7 | 1 |  |
| 9 | 데이터 모델 | 인덱스 | `nav_datamodelStatusIndex` | OK | 2.69 | 1 | 6 | 1 |  |
| 10 | 데이터 모델 | 제약조건 | `nav_datamodelStatusConstraint` | OK | 2.67 | 1 | 5 | 1 |  |
| 11 | 데이터 모델 | 데이터 모델 관리 | `nav_datamodelCollection` | OK | 2.73 | 4 | 3 | 17 |  |
| 12 | 데이터 모델 | 데이터 모델 수집이력 | `nav_datamodelHistory` | OK | 2.67 | 2 | 5 | 1 |  |
| 13 | 데이터 모델 | 데이터 모델 현황 | `nav_datamodelStatus` | OK | 2.76 | 12 | 3 | 10 |  |
| 14 | 데이터 모델 | 진단 제외 관리 | `nav_diagTargetMgmt` | OK | 2.7 | 6 | 3 | 1 |  |
| 15 | 데이터 모델 | 모델링 도구 임포트 | `nav_erwinImport` | OK | 2.7 | 3 | 2 | 0 |  |
| 16 | 표준 진단 | 진단 실행 | `nav_dataDiag` | OK | 2.68 | 22 | 3 | 20 |  |
| 17 | 표준 진단 | 진단 결과 | `nav_dataDiagResult` | OK | 2.71 | 1 | 14 | 0 |  |
| 18 | 데이터 품질 진단 | 도메인 룰 관리 | `nav_qualDomainRule` | OK | 2.67 | 1 | 2 | 0 |  |
| 19 | 데이터 품질 진단 | 값 프로파일링 | `nav_valueProfile` | OK | 2.68 | 3 | 6 | 1 |  |
| 20 | 데이터 품질 진단 | 업무 규칙 관리 | `nav_ruleManage` | OK | 2.7 | 3 | 3 | 1 |  |
| 21 | 데이터 품질 진단 | 업무 규칙 진단 결과 | `nav_ruleResult` | OK | 2.7 | 1 | 4 | 1 |  |
| 22 | 데이터 품질 진단 | 컬럼 규칙 매핑 | `nav_qualColRule` | OK | 2.68 | 1 | 7 | 1 |  |
| 23 | 데이터 품질 진단 | 진단 통계 | `nav_qualStats` | OK | 2.72 | 1 | 4 | 0 |  |
| 24 | 진단 스케줄 | 스케줄 관리 | `nav_scheduleManage` | OK | 2.72 | 4 | 1 | 1 |  |
| 25 | 진단 스케줄 | 스케줄 실행 이력 | `nav_scheduleLog` | OK | 2.7 | 2 | 5 | 100 |  |
| 26 | 마이페이지 | 내 정보 | `nav_myProfile` | OK | 2.76 | 2 | 7 | 0 |  |
| 27 | 마이페이지 | 요청 현황 | `nav_myRequest` | OK | 2.71 | 1 | 2 | 1 |  |
| 28 | 관리 | 데이터 소스 | `nav_datasource` | OK | 2.7 | 6 | 2 | 3 |  |
| 29 | 관리 | 승인 처리 | `nav_approval` | OK | 2.78 | 29 | 2 | 11 |  |
| 30 | 커뮤니티 | 공지사항 | `nav_boardNotice` | OK | 2.69 | 2 | 3 | 1 |  |
| 31 | 커뮤니티 | Q&A | `nav_boardQna` | OK | 2.68 | 2 | 3 | 1 |  |

## 화면 스크린샷

### 1. 대시보드 (`nav_dashboard`)
![nav_dashboard](uitools/screenshots/nav_dashboard.png)

### 2. 코드 (`nav_dsCode`)
![nav_dsCode](uitools/screenshots/nav_dsCode.png)

### 3. 도메인 사전 (`nav_domain`)
![nav_domain](uitools/screenshots/nav_domain.png)

### 4. 도메인 분류 (`nav_domainClassification`)
![nav_domainClassification](uitools/screenshots/nav_domainClassification.png)

### 5. 도메인 그룹 (`nav_domainGroup`)
![nav_domainGroup](uitools/screenshots/nav_domainGroup.png)

### 6. 변경 이력 (`nav_changeHistory`)
![nav_changeHistory](uitools/screenshots/nav_changeHistory.png)

### 7. 테이블 (`nav_datamodelStatusTable`)
![nav_datamodelStatusTable](uitools/screenshots/nav_datamodelStatusTable.png)

### 8. 컬럼 (`nav_datamodelStatusColumn`)
![nav_datamodelStatusColumn](uitools/screenshots/nav_datamodelStatusColumn.png)

### 9. 인덱스 (`nav_datamodelStatusIndex`)
![nav_datamodelStatusIndex](uitools/screenshots/nav_datamodelStatusIndex.png)

### 10. 제약조건 (`nav_datamodelStatusConstraint`)
![nav_datamodelStatusConstraint](uitools/screenshots/nav_datamodelStatusConstraint.png)

### 11. 데이터 모델 관리 (`nav_datamodelCollection`)
![nav_datamodelCollection](uitools/screenshots/nav_datamodelCollection.png)

### 12. 데이터 모델 수집이력 (`nav_datamodelHistory`)
![nav_datamodelHistory](uitools/screenshots/nav_datamodelHistory.png)

### 13. 데이터 모델 현황 (`nav_datamodelStatus`)
![nav_datamodelStatus](uitools/screenshots/nav_datamodelStatus.png)

### 14. 진단 제외 관리 (`nav_diagTargetMgmt`)
![nav_diagTargetMgmt](uitools/screenshots/nav_diagTargetMgmt.png)

### 15. 모델링 도구 임포트 (`nav_erwinImport`)
![nav_erwinImport](uitools/screenshots/nav_erwinImport.png)

### 16. 진단 실행 (`nav_dataDiag`)
![nav_dataDiag](uitools/screenshots/nav_dataDiag.png)

### 17. 진단 결과 (`nav_dataDiagResult`)
![nav_dataDiagResult](uitools/screenshots/nav_dataDiagResult.png)

### 18. 도메인 룰 관리 (`nav_qualDomainRule`)
![nav_qualDomainRule](uitools/screenshots/nav_qualDomainRule.png)

### 19. 값 프로파일링 (`nav_valueProfile`)
![nav_valueProfile](uitools/screenshots/nav_valueProfile.png)

### 20. 업무 규칙 관리 (`nav_ruleManage`)
![nav_ruleManage](uitools/screenshots/nav_ruleManage.png)

### 21. 업무 규칙 진단 결과 (`nav_ruleResult`)
![nav_ruleResult](uitools/screenshots/nav_ruleResult.png)

### 22. 컬럼 규칙 매핑 (`nav_qualColRule`)
![nav_qualColRule](uitools/screenshots/nav_qualColRule.png)

### 23. 진단 통계 (`nav_qualStats`)
![nav_qualStats](uitools/screenshots/nav_qualStats.png)

### 24. 스케줄 관리 (`nav_scheduleManage`)
![nav_scheduleManage](uitools/screenshots/nav_scheduleManage.png)

### 25. 스케줄 실행 이력 (`nav_scheduleLog`)
![nav_scheduleLog](uitools/screenshots/nav_scheduleLog.png)

### 26. 내 정보 (`nav_myProfile`)
![nav_myProfile](uitools/screenshots/nav_myProfile.png)

### 27. 요청 현황 (`nav_myRequest`)
![nav_myRequest](uitools/screenshots/nav_myRequest.png)

### 28. 데이터 소스 (`nav_datasource`)
![nav_datasource](uitools/screenshots/nav_datasource.png)

### 29. 승인 처리 (`nav_approval`)
![nav_approval](uitools/screenshots/nav_approval.png)

### 30. 공지사항 (`nav_boardNotice`)
![nav_boardNotice](uitools/screenshots/nav_boardNotice.png)

### 31. Q&A (`nav_boardQna`)
![nav_boardQna](uitools/screenshots/nav_boardQna.png)
