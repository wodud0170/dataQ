# 51. 비표준 컬럼 일괄 변환 — 서버 API 선행 구현 계획

**작성일**: 2026-04-21
**기반 문서**: [50_논리모델_컬럼추가_분석및개선계획.md](50_논리모델_컬럼추가_분석및개선계획.md)
**대상 세션**: 이번 세션

---

## 0. 이번 세션 결정사항

### 0-1. 50번 Phase C/D 중 "다이얼로그 내 변환", "용어 등록 딥링크" 방향은 폐기

사유: 등록 시점에 용어사전을 전부 파악하고 있다고 전제할 수 없다. 등록은 한글명만 받아 진행하고, 변환은 **별도 시점**(차후 빠른 등록 메뉴 등)에 수행하는 게 실제 흐름에 맞다.

폐기 대상:
- 다이얼로그 [표준 변환] 버튼 / 결과 패널
- [용어 등록하기] CTA + eventBus `openTermsRegister` 딥링크
- [비표준으로 추가] 분기 버튼

### 0-2. 이번 세션에서 **하지 않을 것**

- 현재 데이터 모델 화면(DSDatamodelStatusColumn.vue)에는 변환 관련 UI를 **추가하지 않는다**
- 기존 다이얼로그 변환 UI(50번 Phase B 결과물)는 **이번 세션에서는 건드리지 않고 그대로 둔다**
- 프론트 코드 변경 없음, 프론트 빌드 없음

### 0-3. 이번 세션에서 **할 것**

- **서버에 일괄 변환 API만 선행 구현**. 데이터 모델 화면에서 당장 호출하지는 않지만, 차후 "빠른 등록" 같은 별도 메뉴/기능이 들어올 때 바로 붙일 수 있도록 **연계 지점을 열어둔다**

---

## 1. 왜 서버 API만 먼저 만드는가

- 변환 로직은 단일 API로 수렴 가능 — 어느 UI에서 호출하든 동일한 비즈니스 규칙
- UI는 아직 설계가 확정되지 않은 상태(빠른 등록 메뉴의 위치·동선 미정). 서버 API는 UI 설계와 독립적으로 먼저 정리 가능
- 나중에 UI 작업 시 API가 이미 있으면 프론트 연동만으로 기능 완성 — 빠른 등록 설계 시 서버 이슈로 막히지 않음

---

## 2. 서버 API 설계

### 2-1. 엔드포인트

파일: [DataModelController.java](../q-center/src/main/java/qualitycenter/controller/DataModelController.java)

**신규**: `POST /api/dm/resolveAttrs`

요청 본문(JSON):
```json
{
  "dataModelId": "DM_xxx",
  "attrs": [
    { "objNm": "CUST", "attrNm": "TMP_COL_3" },
    { "objNm": "CUST", "attrNm": "TMP_COL_4" }
  ]
}
```

- `attrs`가 배열이면 → 지정된 컬럼들만 변환 시도 (선택 변환)
- `attrs`가 null/빈 배열이면 → 해당 `dataModelId`의 **모든 비표준 컬럼**(`TERMS_STND_YN='N'`) 변환 시도 (일괄 변환)

**키 설계 사유**: TB_DATA_MODEL_ATTR PK는 복합키 `(DM_ID, OBJ_NM, ATTR_NM)` — 단일 surrogate key(ATTR_ID) 없음.

응답:
```json
{
  "tried": 3,
  "succeeded": 2,
  "failed": 1,
  "failedList": [
    { "objNm": "CUST", "attrNm": "TMP_COL_3", "attrNmKr": "사용자명", "reason": "용어 미등록" }
  ]
}
```

### 2-2. 내부 처리

1. 입력 검증: `dataModelId` 필수. `attrIds`는 선택
2. 대상 컬럼 목록 조회
   - `attrs` 지정 → `selectAttrListByKeys`
   - 미지정 → `selectNonStandardAttrs(dataModelId)`
3. 각 컬럼에 대해:
   - `attrNmKr`로 `resolveStandard` 내부 로직 재활용하여 용어/도메인 매칭
   - 매칭 성공 → `updateAttr` 내부 로직으로 물리명·타입·길이·domainId·`TERMS_STND_YN='Y'` 갱신
   - 매칭 실패 → `failedList`에 `{objNm, attrNm, attrNmKr, reason}` 추가
4. 집계 응답

### 2-3. 리팩토링 원칙

기존 엔드포인트 `resolveStandard`(단건 한글명 조회), `updateAttr`(단건 수정)의 **본문 로직을 private 헬퍼로 추출**하여 신규 `resolveAttrs`와 공유. 기존 단건 엔드포인트는 헬퍼를 호출하는 얇은 래퍼로 축소. **코드 복제 금지**.

헬퍼 시그니처 예:
```java
private Map<String, Object> resolveTermsInternal(String termsNm);
private void applyResolvedToAttr(StdDataModelAttrVo attr, Map<String, Object> resolved);
```

### 2-4. Mapper 쿼리 신설

파일: [datamodel.xml](../q-common/src/main/resources/mapper/stnd/datamodel.xml)

#### 2-4-a. `selectNonStandardAttrs`

```xml
<select id="selectNonStandardAttrs" resultMap="stdDataModelAttrMap">
  SELECT (기존 resultMap 컬럼 일체)
  FROM TB_DATA_MODEL_ATTR
  WHERE DM_ID = #{dataModelId}
    AND USE_YN = 'Y'
    AND TERMS_STND_YN = 'N'
  ORDER BY OBJ_NM, ATTR_SEQ
</select>
```

#### 2-4-b. `selectAttrListByKeys`

```xml
<select id="selectAttrListByKeys" resultMap="stdDataModelAttrMap">
  SELECT (기존 resultMap 컬럼 일체)
  FROM TB_DATA_MODEL_ATTR
  WHERE DM_ID = #{dataModelId}
    AND USE_YN = 'Y'
    AND (OBJ_NM, ATTR_NM) IN
    <foreach item="k" collection="attrs" open="(" separator="," close=")">
      (#{k.objNm}, #{k.attrNm})
    </foreach>
</select>
```

---

## 3. DB 환경 체크 (착수 전)

> **전제**: 각 PC가 자기 로컬 Docker PostgreSQL(`dataq-db`)로 개발 중이라 DDL 변경은 자동 공유되지 않음. 다른 PC/세션에서 이어받을 때는 먼저 52번 마이그레이션 파일을 자기 DB에 한 번 돌려서 최신 스키마로 맞출 것.

### 3-0. 52번 마이그레이션 일괄 적용 (권장)

```bash
docker cp dataQ설계/52_DB_마이그레이션_CLCT폐기_적용.sql dataq-db:/tmp/
docker exec -i dataq-db psql -U admin -d quality -f /tmp/52_DB_마이그레이션_CLCT폐기_적용.sql
```

이 하나로 아래 3-1~3-3 모두 충족된다. 수동 개별 적용이 필요할 때만 아래 항목 참고.

### 3-1. DM_CLCT_ID 컬럼 DROP 여부

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_name = 'tb_data_model_obj' AND column_name = 'dm_clct_id';
```

→ 행이 나오면 DDL 미적용. 52번 §2~§3 실행(또는 3-0).

### 3-2. ATTR_ID 컬럼 존재 여부 (API 입력 형태에 영향)

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'tb_data_model_attr'
ORDER BY ordinal_position;
```

**확인 결과**: ATTR_ID 컬럼 없음. PK는 복합키 `(DM_ID, OBJ_NM, ATTR_NM)`. 입력 형태를 `attrs: [{objNm, attrNm}]`로 확정.

### 3-3. TERMS_STND_YN / USE_YN / OBJ_OWNER NULL 허용 확인

```sql
SELECT column_name, is_nullable FROM information_schema.columns
WHERE table_name IN ('tb_data_model_attr','tb_data_model_obj')
  AND column_name IN ('terms_stnd_yn','use_yn','obj_owner');
```

- `terms_stnd_yn` / `use_yn` 없으면 52번 §1 실행.
- `tb_data_model_attr.obj_owner` 의 `is_nullable = 'NO'`면 52번 §8 실행 (논리모델 컬럼 INSERT 실패 원인).

---

## 4. 작업 순서

| # | 작업 | 파일 | 비고 |
|---|------|------|------|
| 1 | §3 DB 환경 체크 | - | 사용자 확인 |
| 2 | 헬퍼 추출: `resolveStandard` / `updateAttr` 본문 private 분리 | 1 (Controller) | 기존 단건 엔드포인트는 헬퍼 호출로 축소 |
| 3 | 신규 API `POST /api/dm/resolveAttrs` 추가 | 1 (Controller) | 헬퍼 재사용 |
| 4 | Mapper: `selectNonStandardAttrs`, `selectAttrListByIds` 추가 | 1 (XML) | |
| 5 | 사용자가 IDE에서 서버 재기동 | - | 메모리 규칙 |
| 6 | API 단독 검증 (§5) | - | curl 또는 Postman |
| 7 | 커밋 | - | |
| 8 | 문서 갱신 — 50번에 "Phase B 변환 UI / Phase C 딥링크는 폐기, 51번으로 대체" 명시 | 1 | |

**프론트 변경 없음 → 프론트 빌드 없음 → 프론트 관련 커밋 없음**

---

## 5. 검증 시나리오 (API 단독)

서버 재기동 후 curl 또는 Postman으로 직접 호출.

### 5-1. 전제 데이터 준비

비표준 컬럼이 하나도 없다면 테스트용으로 몇 건 등록 필요. 기존 다이얼로그에서 용어 없는 한글명으로 [비표준으로 추가]를 이용해 2-3건 만든다. (50번 Phase B 결과 UI 활용)

### 5-2. 일괄 변환 호출

```bash
curl -X POST http://localhost:28091/api/dm/resolveAttrs \
  -H "Content-Type: application/json" \
  -d '{"dataModelId":"DM_xxx"}'
```

기대:
- 응답 JSON에 `tried`, `succeeded`, `failed`, `failedList` 포함
- DB에서 성공 행들은 `TERMS_STND_YN='Y'`, 물리명·타입 갱신되었는지 확인

### 5-3. 선택 변환 호출

```bash
curl -X POST http://localhost:28091/api/dm/resolveAttrs \
  -H "Content-Type: application/json" \
  -d '{"dataModelId":"DM_xxx","attrIds":[12,15]}'
```

기대:
- 지정된 ID만 처리
- 나머지 비표준 행은 그대로

### 5-4. 실패 케이스

- 존재하지 않는 attrId → `failedList`에 reason 포함
- 용어사전에 없는 한글명 → `reason: "용어 미등록"`

---

## 6. 차후 연계 가능성 (참고 — 다음 세션 이후)

서버 API가 완성되면 아래 방식들로 붙일 수 있음. **이번 세션 범위 아님**.

### 6-1. 빠른 등록 메뉴 (유력)

- 별도 메뉴 또는 모달에서 한글명 리스트를 한 번에 입력받아 비표준 컬럼을 벌크 등록 → 바로 `/api/dm/resolveAttrs` 호출하여 매칭된 것은 표준 전환
- 사용자가 자주 쓰는 "표준 용어 일괄 매핑" UX에 가장 적합

### 6-2. 데이터 모델 화면 체크박스 + 툴바 버튼

- 그리드에서 비표준 행 선택 후 [선택 변환]/[일괄 변환] 호출
- 50번 초기 설계에 있던 방향이지만 이번 세션에서 보류

### 6-3. 용어 등록 후 자동 일괄 변환 트리거

- 용어 등록 완료 시 해당 한글명을 가진 비표준 컬럼이 있는지 서버에서 조회 → 자동 `resolveAttrs` 호출 제안 다이얼로그
- UX 친화적이나 구현 복잡도 있음

---

## 7. 리스크 / 주의

### 7-1. 헬퍼 추출 범위

`resolveStandard` / `updateAttr`는 현재 별개 엔드포인트로 존재. 내부 로직을 뽑을 때 기존 시그니처(응답 JSON, 에러 처리)가 깨지지 않도록 **얇은 래퍼로 유지**하고 내부만 헬퍼 호출로 바꿀 것.

### 7-2. 트랜잭션 경계

일괄 변환 중 한 건 실패가 전체 롤백을 일으키면 곤란. 건별 독립 처리(각 건 try/catch)로 부분 성공 허용. `@Transactional`을 메서드 최상단에 걸지 말 것.

### 7-3. 성능

비표준 컬럼 수백 건 × 개별 SELECT/UPDATE는 느릴 수 있음. 초기 구현은 단순 루프. 실사용에서 문제되면 벌크 UPDATE로 개선.

### 7-4. 데이터 모델 화면 기존 UI는 건드리지 말 것

50번 Phase B 결과로 이미 들어가 있는 다이얼로그 변환 UI는 **이번 세션에서 제거하지 않는다**. 나중에 "빠른 등록" 설계 시점에 전체 UI 방향 확정하면서 같이 정리.

### 7-5. 프론트 빌드 금지

프론트 소스 변경 없음 → `npm run build` 실행하지 않음. 메모리 규칙과 별개로 이번 세션 자체가 백엔드-only 작업.

---

## 8. 문서 후속 정리 (구현 후)

- [50_논리모델_컬럼추가_분석및개선계획.md](50_논리모델_컬럼추가_분석및개선계획.md):
  - Phase B 다이얼로그 변환 UI / Phase C 용어 등록 딥링크 / Phase D 그리드 일괄 변환 버튼 → **폐기** 표기
  - "변환 로직은 서버 API `POST /api/dm/resolveAttrs`로 분리. UI 연계는 빠른 등록 등 별도 설계 시 진행" 명시
- [10_경쟁사분석_및_TODO.md](10_경쟁사분석_및_TODO.md): 44번 CLCT 폐기를 "보류"에서 "완료"로
