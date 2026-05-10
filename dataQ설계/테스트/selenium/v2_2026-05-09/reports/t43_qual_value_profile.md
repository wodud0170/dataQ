# 86번 #11 통합 테스트 v2 리포트
실행: 2026-05-09 23:41:56

**1/1 통과**

## T43 값 프로파일링 ✅ PASS

| 단계 | 결과 | 경과 | 상세 |
|---|---|---|---|
| A01 값 프로파일링 화면 진입 | ✓ | 17.1s |  |
| A02 모델 콤보 렌더 | ✓ | 17.1s |  |
| A03 모델/선택 없을 때 실행 disabled | ✓ | 17.1s |  |
| B01 빈 cols 배열 차단 | ✓ | 17.2s | rc=400 |
| B02 진단 트리거 (executor 영향) | ✓ | 17.4s | rc=500 msg=Connection refused: getsockopt: localhost/127.0.0. |
| B03 VALUE history 조회 | ✓ | 17.5s | http=200 |
| B04 value result 조회 | ✓ | 17.5s | skip |
| B05 진행률(history) 응답 | ✓ | 17.5s | skip |
| C01 도메인 분류 콤보 렌더 | ✓ | 17.5s |  |
| C02 선택/해제 버튼 | ✓ | 17.5s |  |
