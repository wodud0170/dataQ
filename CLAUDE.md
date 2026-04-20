# Narae DataQ - 프로젝트 가이드

## 프로젝트 개요
데이터 품질/표준화 관리 플랫폼. 단어·용어·도메인 표준 관리, 데이터 모델 수집/진단, 승인 워크플로우 제공.

## 기술 스택
- Backend: Spring Boot 2 + MyBatis 3 + PostgreSQL 13+
- Frontend: Vue.js 2 + Vuetify 2 (SPA, keep-alive 탭 기반)
- External: lib/common-0.0.1-SNAPSHOT.jar (DBHandler, DataSourceVo, drivers.xml)
- 빌드: Maven (q-common → q-center → q-executor 순서)

## 모듈 구조
- **q-common**: 공유 VO, MyBatis Mapper XML, 유틸리티
- **q-center**: 웹 서버 (Controller + Vue 프론트엔드, 포트 28091)
- **q-executor**: 백그라운드 워커 (수집, 표준 진단, 구조 변경 진단)

## 핵심 경로
```
Vue 컴포넌트   q-center/vue/front/src/components/
Vue 네비/탭    q-center/vue/front/src/views/nav/NdNav.vue
              q-center/vue/front/src/views/content/NdContent.vue
Controller    q-center/src/main/java/qualitycenter/controller/
Executor      q-executor/src/main/java/qualityexecutor/service/std/
Mapper XML    q-common/src/main/resources/mapper/stnd/
VO            q-common/src/main/java/com/ndata/quality/model/std/
설계 문서      dataQ설계/
```

## 핵심 아키텍처 패턴

### 컴포넌트 간 이동 (eventBus)
```javascript
// 소스 컴포넌트에서
import { eventBus } from '../eventBus';
eventBus.pendingXxx = { ... };  // 데이터 전달
eventBus.$emit('openXxx');       // 이벤트 발행

// NdNav.vue mounted()에서 수신
eventBus.$on('openXxx', () => {
    this.addTabItem('탭제목', 'componentName');
});

// 대상 컴포넌트에서 (keep-alive activated hook)
activated() {
    if (eventBus.pendingXxx) {
        const pending = eventBus.pendingXxx;
        eventBus.pendingXxx = null;
        this._applyPending(pending);
    }
}
```
**주의**: eventBus 사용 시 `import { eventBus } from '../eventBus'` 반드시 추가.

### 빌드 순서
```bash
cd q-common && mvn install -q -DskipTests
cd ../q-center && mvn package -q -DskipTests
cd ../q-executor && mvn package -q -DskipTests
```

### Oracle 접속 (SID/Service Name)
- drivers.xml에 Oracle(SID)와 Oracle(Service Name) 별도 드라이버 정의
- DataSourceUtils.getDBHandler()에서 connProps="Service Name"이면 driverName을 swap
- TB_DATA_SOURCE.CONN_PROPS 컬럼에 "SID" 또는 "Service Name" 저장

## 개발 규칙

### 절대 하지 말 것
- **사용자가 지정한 필드명/헤더명/라벨을 임의로 축약하거나 변경하지 말 것** (그대로 사용)
- **Python으로 파일 편집/XML 조작 하지 말 것** (Edit/Read 도구 직접 사용)
- **불필요한 fallback/방어코드 추가하지 말 것** (사용자가 필요없다고 하면 즉시 제거)
- **git commit 시 Co-Authored-By 라인 넣지 말 것**
- **import 누락, 매핑 누락 하지 말 것** (새 필드 추가 시 VO → XML resultMap → 프론트 map 함수 모두 체크)

### 반드시 할 것
- DDL 변경 시 `dataQ설계/DDL_claude_generated.sql`에 먼저 반영 후 안내
- 기능 구현 시 입력 검증/에러처리/UX 흐름을 먼저 확인하고 구현
- Vue 컴포넌트 수정 후 필요한 import가 모두 있는지 검증
- 새 필드 추가 시 체크리스트: Java VO → MyBatis resultMap → SQL SELECT → 프론트 data/map/template

### 작업 스타일
- 짧고 직접적인 응답 선호, 불필요한 설명 생략
- 작업 전에 묻지 말고 바로 실행 (모호하면 그때 질문)
- 문서 작성 요청 시 `dataQ설계/` 폴더에 번호_제목.md 형식으로 생성
- 빌드 요청 시 q-common → q-center → q-executor 순서로 한 번에 실행

## 주요 테이블
| 테이블 | 용도 |
|---|---|
| TB_WORD | 단어 표준 (APRV_YN으로 승인 관리) |
| TB_TERMS | 용어 표준 (단어 조합, TB_TERMS_WORDS로 매핑) |
| TB_DOMAIN | 도메인 표준 (데이터 타입/길이 정의) |
| TB_APRV_STATS | 승인 이력 (요청/승인/반려, 이력 누적) |
| TB_DATA_MODEL | 데이터 모델 정의 |
| TB_DATA_MODEL_CLCT | 데이터 모델 수집 이력 |
| TB_DATA_MODEL_OBJ | 수집된 테이블 |
| TB_DATA_MODEL_ATTR | 수집된 컬럼 (OBJ_OWNER 포함) |
| TB_DIAG_JOB | 표준화 진단 Job |
| TB_DIAG_RESULT | 표준화 진단 결과 (이슈 건별) |
| TB_STRUCT_DIAG_HISTORY | 구조 변경 진단 이력 |
| TB_STRUCT_DIAG_DETAIL | 구조 변경 진단 상세 (변경 건별) |
| ndata.TB_DATA_SOURCE | 데이터소스 (ndata 스키마) |

## 승인 프로세스
- 상태: 0(승인대기) → 2(승인) 또는 3(반려) → 0(재요청)
- 관리자 등록 시 APRV_YN='Y' 즉시 승인
- 일반 사용자 등록 시 APRV_YN='N' → 승인 화면에서 관리자 승인 필요
- 용어 승인 시 구성 단어 미승인 체크 (미승인 단어 있으면 승인 거부)
- 단어 반려 시 연관 미승인 용어 경고

## 표준화 준수율 계산식 (통일됨)
```
준수율 = (전체 컬럼수 - 이슈 컬럼수) / 전체 컬럼수 × 100
이슈 컬럼수 = COUNT(DISTINCT OBJ_NM || '.' || ATTR_NM) FROM TB_DIAG_RESULT
```
RESULT_CNT(이슈 건수)가 아닌 ISSUE_COL_CNT(이슈 컬럼수) 사용.
