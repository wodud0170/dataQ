# Phase 2 설계서: 영향도 분석 + ERwin 모델 임포트

**작성일**: 2026-04-01
**상태**: 설계

---

## 1. 영향도 분석

### 1.1 목적
단어/도메인을 변경하거나 삭제할 때, **영향받는 모든 용어/테이블/컬럼을 사전에 파악**할 수 있게 한다.

### 1.2 현재 문제
- 단어를 수정/삭제해도, 이 단어를 사용하는 용어가 몇 개인지 알 수 없음
- 도메인을 변경하면, 이 도메인을 사용하는 용어 → 테이블 컬럼까지 연쇄적으로 영향이 가지만 확인 불가
- SMETA는 변경 전에 "영향도 분석" 팝업으로 연관 용어/엔터티/테이블 목록을 보여줌

### 1.3 영향도 관계 구조

```
단어 (TB_WORD)
  │
  ├─ 사용하는 용어 (TB_TERMS_WORDS → TB_TERMS)
  │     │
  │     └─ 이 용어가 적용된 테이블 컬럼 (TB_DATA_MODEL_ATTR)
  │
  └─ 금칙어로 지정한 다른 단어들

도메인 (TB_DOMAIN)
  │
  └─ 이 도메인을 사용하는 용어 (TB_TERMS.DOMAIN_NM)
        │
        └─ 이 용어가 적용된 테이블 컬럼 (TB_DATA_MODEL_ATTR)
```

### 1.4 기능 상세

#### 1.4.1 단어 영향도 분석

단어 수정/삭제 전에 "영향도 분석" 버튼 클릭 시:

```
┌─────────────────────────────────────────────┐
│  영향도 분석: 단어 "사용자" (USR)              │
├─────────────────────────────────────────────┤
│                                             │
│  연관 용어 (5건)                              │
│  ┌─────────────────────────────────────┐    │
│  │ 용어명        │ 영문약어       │ 도메인  │    │
│  │ 사용자이름     │ USR_NM       │ V100   │    │
│  │ 사용자ID      │ USR_ID       │ V20    │    │
│  │ 사용자유형     │ USR_TP       │ V10    │    │
│  │ 사용자상태     │ USR_STAT     │ V10    │    │
│  │ 사용자권한     │ USR_AUTH     │ V20    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  연관 테이블 컬럼 (12건)                       │
│  ┌─────────────────────────────────────┐    │
│  │ 테이블명      │ 컬럼명         │ 모델명  │    │
│  │ TB_USER      │ USR_NM        │ 오라클  │    │
│  │ TB_USER      │ USR_ID        │ 오라클  │    │
│  │ TB_AUTH      │ USR_ID        │ 오라클  │    │
│  │ ...          │               │        │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  [엑셀 다운로드]                    [닫기]     │
└─────────────────────────────────────────────┘
```

#### 1.4.2 도메인 영향도 분석

도메인 수정/삭제 전에:

```
┌─────────────────────────────────────────────┐
│  영향도 분석: 도메인 "V100" (VARCHAR, 100)     │
├─────────────────────────────────────────────┤
│                                             │
│  이 도메인을 사용하는 용어 (23건)                │
│  ┌─────────────────────────────────────┐    │
│  │ 용어명        │ 영문약어              │    │
│  │ 사용자이름     │ USR_NM              │    │
│  │ 시스템명       │ SYS_NM              │    │
│  │ ...                                 │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  [엑셀 다운로드]                    [닫기]     │
└─────────────────────────────────────────────┘
```

### 1.5 API 설계

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/std/impact/word?wordId=` | 단어 영향도 (연관 용어 + 연관 컬럼) |
| GET | `/api/std/impact/domain?domainNm=` | 도메인 영향도 (연관 용어) |

### 1.6 쿼리 설계

```sql
-- 단어 → 연관 용어
SELECT t.TERMS_NM, t.TERMS_ENG_ABRV_NM, t.DOMAIN_NM
FROM TB_TERMS_WORDS tw
JOIN TB_TERMS t ON tw.TERMS_ID = t.TERMS_ID
WHERE tw.WORD_ID = #{wordId}
ORDER BY t.TERMS_NM;

-- 단어 → 연관 컬럼 (단어의 영문약어가 컬럼명에 포함된 것)
SELECT a.OBJ_NM, a.ATTR_NM, a.ATTR_NM_KR, dm.DM_NM
FROM TB_DATA_MODEL_ATTR a
JOIN TB_DATA_MODEL dm ON a.DM_ID = dm.DM_ID
WHERE a.ATTR_NM LIKE '%' || (SELECT WORD_ENG_ABRV_NM FROM TB_WORD WHERE WORD_ID = #{wordId}) || '%'
ORDER BY a.OBJ_NM, a.ATTR_NM;

-- 도메인 → 연관 용어
SELECT TERMS_NM, TERMS_ENG_ABRV_NM
FROM TB_TERMS
WHERE DOMAIN_NM = #{domainNm}
ORDER BY TERMS_NM;
```

### 1.7 프론트 적용 위치

| 화면 | 적용 위치 | 동작 |
|------|----------|------|
| DSWord.vue | 단어 상세 / 수정 / 삭제 시 | "영향도 분석" 버튼 → 팝업 |
| DSDomain.vue | 도메인 상세 / 수정 / 삭제 시 | "영향도 분석" 버튼 → 팝업 |

### 1.8 구현 우선순위

| 순서 | 항목 |
|------|------|
| 1 | MyBatis 쿼리 (impact.xml) |
| 2 | API 2개 (Controller) |
| 3 | 영향도 팝업 컴포넌트 (공통) |
| 4 | DSWord.vue에 버튼 + 팝업 연결 |
| 5 | DSDomain.vue에 버튼 + 팝업 연결 |
| 6 | 엑셀 다운로드 |

---

## 2. ERwin 모델 임포트

### 2.1 목적
ERwin에서 설계한 데이터 모델을 우리 시스템에 업로드하여, DBMS 수집 없이도 모델 정보를 등록하고 표준화 진단/구조 진단을 적용할 수 있게 한다.

### 2.2 ERwin 파일 형식

ERwin은 두 가지 형식으로 내보내기 가능:
- **`.erwin`** — 바이너리 형식 (파싱 어려움)
- **XML 내보내기** — ERwin의 "Export > XML" 기능으로 생성. **이것을 사용**

ERwin XML 구조 (핵심):
```xml
<ERwin>
  <Model>
    <Entity_Groups>
      <Entity>
        <EntityProps>
          <Name>사용자</Name>          <!-- 논리명 -->
          <Physical_Name>TB_USER</Physical_Name>  <!-- 물리명 -->
        </EntityProps>
        <Attribute_Groups>
          <Attribute>
            <AttributeProps>
              <Name>사용자ID</Name>     <!-- 논리명 -->
              <Physical_Name>USER_ID</Physical_Name>  <!-- 물리명 -->
              <Datatype>VARCHAR2(20)</Datatype>
              <Null_Option>NOT NULL</Null_Option>
              <PK>YES</PK>
            </AttributeProps>
          </Attribute>
        </Attribute_Groups>
      </Entity>
    </Entity_Groups>
  </Model>
</ERwin>
```

### 2.3 임포트 흐름

```
사용자가 ERwin XML 파일 업로드
    │
    ├─ 1. XML 파싱 → 엔터티/속성 목록 추출
    │
    ├─ 2. 미리보기 화면에서 파싱 결과 확인
    │     - 테이블 N개, 컬럼 M개
    │     - 논리명/물리명 매핑 확인
    │
    ├─ 3. "임포트 실행" 클릭
    │
    ├─ 4. TB_DATA_MODEL에 모델 등록 (또는 기존 모델에 추가)
    │     TB_DATA_MODEL_OBJ에 테이블 등록
    │     TB_DATA_MODEL_ATTR에 컬럼 등록
    │
    └─ 5. 완료 → 기존 수집 데이터와 동일하게 진단/비교 가능
```

### 2.4 화면 설계

```
┌──────────────────────────────────────────────────────┐
│  ERwin 모델 임포트                                     │
├──────────────────────────────────────────────────────┤
│                                                      │
│  [ERwin XML 파일 선택]  [파일명.xml]                    │
│                                                      │
│  대상 데이터모델: [기존 모델 선택 ▼] 또는 [신규 생성]      │
│                                                      │
│  ┌─ 미리보기 ──────────────────────────────────┐      │
│  │ 파싱 결과: 테이블 42개, 컬럼 387개              │      │
│  │                                            │      │
│  │ 테이블명(물리)   │ 테이블명(논리)   │ 컬럼 수   │      │
│  │ TB_USER        │ 사용자         │ 15      │      │
│  │ TB_ORDER       │ 주문          │ 22      │      │
│  │ ...                                        │      │
│  └────────────────────────────────────────────┘      │
│                                                      │
│  [임포트 실행]                                         │
│                                                      │
│  ┌─ 임포트 결과 ──────────────────────────────┐       │
│  │ 성공: 42 테이블, 387 컬럼                    │       │
│  │ 스킵: 0 (중복)                              │       │
│  │ 실패: 0                                    │       │
│  └────────────────────────────────────────────┘       │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 2.5 API 설계

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/dm/parseErwinXml` | ERwin XML 파일 업로드 → 파싱 결과 반환 (미리보기) |
| POST | `/api/dm/importErwinModel` | 파싱된 데이터를 DB에 저장 (임포트 실행) |

### 2.6 백엔드 구조

```java
// ERwin XML 파서 (q-center 또는 q-executor)
public class ErwinXmlParser {
    
    public ErwinParseResult parse(InputStream xmlFile) {
        // DOM 또는 SAX 파서로 XML 파싱
        // Entity → StdDataModelObjVo 변환
        // Attribute → StdDataModelAttrVo 변환
        return result;
    }
}

public class ErwinParseResult {
    private List<StdDataModelObjVo> tables;   // 테이블 목록
    private List<StdDataModelAttrVo> columns; // 컬럼 목록
    private int tableCount;
    private int columnCount;
}
```

### 2.7 ERwin 버전 호환

| ERwin 버전 | XML 형식 | 지원 |
|-----------|---------|------|
| ERwin 7.x+ | XML Export | 지원 예정 |
| ERwin 9.x+ | XML Export | 지원 예정 |
| DA# | XML Export | 향후 검토 |

### 2.8 구현 우선순위

| 순서 | 항목 |
|------|------|
| 1 | ERwin XML 파서 (Java) |
| 2 | 파싱 미리보기 API |
| 3 | 임포트 실행 API (기존 수집 구조 재활용) |
| 4 | 임포트 화면 (Vue 컴포넌트) |
| 5 | 메뉴 등록 (데이터 모델 하위) |
| 6 | DA# XML 지원 (선택) |

---

## 3. 메뉴 구조 (Phase 2 완료 후)

```
데이터 표준 사전
  ├─ 단어            (영향도 분석 버튼 추가)
  ├─ 용어
  ├─ 코드
  ├─ 도메인          (영향도 분석 버튼 추가)
  ├─ 도메인 그룹
  ├─ 도메인 분류
  └─ 변경 이력

데이터 모델
  ├─ 데이터 모델 현황
  ├─ 데이터 모델 수집
  ├─ ERwin 모델 임포트  ← 신규
  └─ 수집 이력

표준화 진단
  ├─ 진단 실행
  └─ 진단 결과

구조 진단
  └─ 구조 진단

자동 표준화 지원
  └─ 표준화 추천

관리
  ├─ 사용자 관리
  ├─ 승인
  ├─ 데이터 소스
  └─ 시스템
```

---

## 4. 전체 구현 우선순위

| 순서 | 기능 | 난이도 | 예상 기간 |
|------|------|--------|---------|
| 1 | **영향도 분석 — 쿼리 + API** | 중 | 1일 |
| 2 | **영향도 분석 — 팝업 + 화면 연결** | 중 | 1일 |
| 3 | **ERwin XML 파서** | 고 | 2~3일 |
| 4 | **ERwin 임포트 API + 화면** | 중 | 1~2일 |
| 5 | 엑셀 다운로드 (영향도 결과) | 낮 | 0.5일 |
| 6 | DA# XML 지원 | 낮 | 1일 |

**총 예상 기간**: 5~7일
