# RAMP 업무 흐름 분석 자료

> RAMP(=RMS 개선시스템) 스키마를 국가기록원 기록물관리 프로세스에 맞춰 5개 시나리오로 분해.
> CAMS 업무흐름(`cams_workflow_study.pdf`)과 짝을 이루며, **CAMS↔RAMP DB 통합 가능성 판단**의 기준 자료.
> 입력: `ramp기관스키마정보.xlsx` (365 테이블 / 5,777 컬럼)

## ⭐ 통합 핵심 4 메인테이블 (양쪽 동일 비중)

| CAMS | RAMP | 키 매핑 |
|---|---|---|
| **RG_DOCUMENT** (기록물철, 110컬럼) | **tb_rdfolder** (기록물철, 170컬럼) | BSID ↔ fls_id |
| **RG_DETAIL** (기록물건, 99컬럼) | **tb_rdrecord** (기록물건, 149컬럼) | DSID ↔ ritm_id |

→ 이 4개가 통합의 기준점. 다른 모든 테이블은 이들에 종속. 컬럼 단위 매핑은 이 4개부터.

## ⚠️ 데이터 접근성 비대칭

- **RAMP**: 향후 실제 데이터 조회 가능
- **CAMS**: 데이터 영영 접근 불가 — 메타데이터(스키마)만

→ 값 공간 충돌 검사·실데이터 검증은 RAMP 측에서만 가능. CAMS는 ID 생성규칙·도메인·코멘트 기반 추론.

## 🔑 통합 매핑 정책 (2026-05-19 확정)

1. **표준화 이전(원시 상태)** 기준으로 매핑한다.
   - CAMS 단어 표준화 결과(149 적재분)는 사전·용어 표준화 영역. 통합 컬럼명 흡수 룰과 분리.
   - 매핑은 **현 RAMP 컬럼 ↔ 현 CAMS 컬럼** 원시 상태로 진행.
2. **RAMP를 표준 기준으로 간주.**
   - RAMP가 R8(업무단어+형식단어) 미준수인 경우도 인지하나, **그래도 우선**.
3. **흡수 통합 = CAMS → RAMP 컬럼명으로 변경.**
   - 의미 매칭되는 CAMS 컬럼은 통합 후 RAMP 컬럼명으로 존재. CAMS 영문명·구조는 사라짐(필요 시 매핑테이블에 보존).

## 위치 — RMS와 CAMS의 관계

```
[기관/처리과]      [기록관(RAMP=RMS)]      [국가기록원(CAMS)]
   문서 생산  ─▶   생산·등록·관리   ─이관─▶   인수·보존·공개·서비스
                  ▲                       │
                  └──────폐기 심의◀────────┘
```

- **RAMP**: 각 기관의 기록관리시스템(RMS 개선판). **생산~관리~이관**까지의 기관 측 업무.
- **CAMS**: 국가기록원의 통합관리시스템. **인수~영구보존~대국민서비스**까지.
- 기록 1건의 라이프사이클은 **RAMP에서 시작**해 **CAMS로 이관**되며, 통합은 두 시스템을 하나의 데이터 모델로 합치는 작업.

## 시나리오 목차
1. 기록물 생산·등록 — 생산현황 보고 → 기록물 정식 등록 → 원문·NEO 파일
2. 분류·기능분류·평가 — 기능분류 체계 → 단위업무 → 보존기간 평가·재평가·폐기 심의
3. 보관·서고 — 보존상자 → 서가 배치 → 매체 수록 → RFID 추적
4. 인수·이관·인계 — 처리과별 인수계획 → 인수 검수 → 마이그레이션 → 외부 기관간 인계
5. 공개·열람·접근권한 — 공개목록 작성·변경 → 공개재분류 → 열람신청 → 접근권한

---

## 시나리오 1 — 기록물 생산·등록

**상황** 처리과에서 공문서를 생산하면 → 생산현황 보고서 작성 → 기록물철·건 정식 등록 → 원문 파일 첨부 → 영구보존포맷(NEO) 변환.

**핵심 질문** "한 건의 공문서가 어떻게 `tb_rdrecord`에 PK `ritm_id` 를 가지고 정식 등록되나?"

### 흐름
```
① 생산현황 보고     →  ② 기록물 등록     →  ③ 원문/NEO
   tb_cr*               tb_rd*               tb_storgdfile, tb_stfolderneofile
```

### ① 생산현황 보고
처리과가 매년 생산한 기록물 목록을 보고. **법정 의무**.

| 테이블 | PK | 컬럼수 | 설명 |
|---|---|---|---|
| `tb_crfolder` | (rctr_id, fls_rcpt_sn) | 80 | 생산현황보고 기록물철 — 접수일련번호 19자리 |
| `tb_crrecord` | (rctr_id, ritm_rcpt_sn) | 61 | 생산현황보고 기록물건 |

**핵심 컬럼**: `pdst_rpt_year`(보고년도), `pdst_rpt_inst`(보고기관), `pdst_rpt_seq`(보고순번), `clsf_schm_id`(분류체계), `prdctn_year`(생산년도).

→ CAMS 시나리오 1의 `RG_SDOCUMENT`/`RG_SDETAIL` 에 해당하지만 키 형태가 다름.

### ② 기록물 정식 등록 — 핵심
**`tb_rdfolder`(170컬럼)** 와 **`tb_rdrecord`(149컬럼)** 가 RAMP의 핵심 마스터.

| 테이블 | PK | 설명 |
|---|---|---|
| **`tb_rdfolder`** | **`fls_id` STRING(14)** | 기록물철 — 단일 surrogate key |
| **`tb_rdrecord`** | **`ritm_id` STRING(14)** | 기록물건 — 단일 surrogate key |

기록물건은 `fls_id` 외래키로 철에 종속. RAMP는 **단일 인조키**로 통일.

**`tb_rdfolder` 핵심 컬럼**: `rctr_id`(기록관), `fls_id`(PK), `acptn_year`(인수년도), `acptn_year_seq`(인수년도순번), `prdctn_sys_cd`(생산시스템), `trdv_cd`(처리과), `clsf_schm_se_cd`+`clsf_schm_id`(분류체계).

**`tb_rdrecord` 핵심**: `ritm_id`(PK), `fls_id`(부모철 FK), `prdctn_year`, `prdctn_dt`, `prdctn_reg_no`(생산등록번호).

### ③ 원문 파일·영구보존포맷
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_storgdfile` | `orgnl_file_id` STRING(19) | 원문파일 (42컬럼) |
| `tb_storgdfile_cloud` | — | 클라우드 저장본 (38컬럼) |
| `tb_stfolderneofile` | `fls_neob_file_id` STRING(19) | 기록물철 NEO 파일 (19컬럼) |
| `tb_stformat_hist` | — | 포맷변환 이력 |
| `tb_stformat_queue` | — | 포맷변환 큐 |
| `tb_streqcnvr` | — | 포맷변환 요청정보 |
| `tb_rdmultiaprovcreat` | (rctr_id, multi_aprv_ritm_id, ...) | 다중 결재 (전자결재 연동) |

→ CAMS의 `SV_NEO_FILE`, `SV_PDF_FILE` 에 해당.

---

## 시나리오 2 — 분류·기능분류·평가

**상황** 기록물을 어떤 단위업무 / 기능분류 체계에 둘 것인지, 보존기간 도래 시 폐기/연장/영구 어떻게 평가할지.

### ① 기능분류·단위업무
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_zzfnctclsf` | `bm_cd` STRING(35) | 기능분류 메인 (50컬럼) — 정부기능분류체계 |
| `tb_zzunit` | (rctr_id, unit_job_cd) | 단위업무 (35컬럼) — 기존 자료관 |
| `tb_zzunitnewreq` | (rctr_id, aply_id, ...) | 단위업무 신규 요청 (33컬럼) |
| `tb_zzunitchgreq` | (rctr_id, aply_id, ...) | 단위업무 수정 신청 (45컬럼) |
| `tb_zzunit*` 외 23테이블 | — | 분류 관련 |

기능분류는 정부공통 → 기관레벨로 내려옴. `bm_cd` 35자리에 계층이 인코딩.

### ② 보존기간 재평가
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_streqrevlopnn` | `dmnd_revl_rvw_opnn_id` STRING(6) | 요청재평가 검토의견 |
| `tb_streqrevlopnnfolder` | `dmnd_revl_rvw_opnn_fls_id` STRING(13) | 재평가 대상 철 |

### ③ 폐기 심의·이력
| 테이블 | PK | 설명 |
|---|---|---|
| **`tb_dfdscdopnn`** | **(dscd_evl_year, fls_id)** | 폐기 평가의견 — 폐기/보류/재책정 (49컬럼) |
| **`tb_dfdscdhist`** | **(rctr_id, fls_id, dscd_year)** | 폐기 이력 (29컬럼) — 처리과/처리기관 의견 누적 |
| `tb_rddscdphbtappoint` | (rctr_id, fls_id, dscd_phbt_seq) | 폐기금지 지정 |

→ CAMS 시나리오 4의 `SV_DISUSE_ARCHIVE`, `RG_SEXHAUSTARCIVE` 에 해당.

---

## 시나리오 3 — 보관·서고

**상황** 기록물철을 실제 어느 보존상자에 담아 어느 서가 셀에 배치하는가. 매체 수록·RFID 추적까지.

### ① 보존상자·서가배치
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_srprsrbox` | (rctr_id, prsr_box_id STRING(19)) | 보존상자 — 서고·서가·층·열 위치 포함 (13컬럼) |
| `tb_srbkshdpos` | (rctr_id, bksh_dpos_id STRING(10)) | 서가 배치 작업 마스터 (9컬럼) |
| `tb_srbkshdposdtl` | (rctr_id, bksh_dpos_id, **fls_id**) | 서가 배치 상세 — 어느 철이 어디로 (7컬럼) |
| `tb_zztmpbkshexcel` | — | 서가배치 엑셀 임시 (대량 처리용) |

→ `fls_id` 가 `tb_srbkshdposdtl` 통해 보존상자·서가위치와 연결. CAMS의 `SV_ARCHIVE_ARRANGE_LIST` 와 동일 패턴.

### ② 매체 수록 — 광디스크·M/F
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_sroptidisk_mst` | (rctr_id, trdv_cd, ...) | 광디스크수록 마스터 (32컬럼) |
| `tb_sroptidisk` | (rctr_id, opds_id STRING(15)) | 광디스크 수록 계획 (15컬럼) |
| `tb_sroptidisk_cntchck` | — | 광디스크 정수점검 (8) |
| `tb_sroptidisk_cntchckdtl` | — | 정수점검 상세 (13) |
| `tb_srmfphtg` | (rctr_id, phtg_seq) | M/F 촬영계획 (33컬럼) |
| `tb_stmedium` | (rctr_id, stmd_id) | 저장매체 마스터 |

→ CAMS `SV_OD_*`, `SV_MF_*` 와 동일 도메인이나, RAMP는 단순화돼 있고 CAMS는 매체별로 더 세분화.

### ③ RFID
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_rftabpub` | `tag_id` STRING(24) | 태그 발행 (17컬럼) |
| `tb_rffixreder` | (rctr_id, fix_redr_id) | 고정형 리더기 (17컬럼) |

→ CAMS `RF_TAGPUB`·`RF_FIXREDER` 와 거의 동일.

---

## 시나리오 4 — 인수·이관·인계 (CAMS와 연결지점)

**상황** 처리과별 인수계획 → 검수 → CAMS 또는 다른 기관으로 이관/인계.
**RAMP 측에서 가장 복잡한 구간** — 외부와의 데이터 경계.

### ① 처리과별 인수계획
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_tkorgacptnplan` | (rctr_id, acptn_year, acptn_trdv_cd, prdctn_sys_cd, acptn_seq) | 인수계획 (71컬럼) |
| `tb_tkorgacptnplanpre` | 동일 | 사전 접수계획 (67컬럼) |

### ② 인수 처리
| 테이블 | PK | 설명 |
|---|---|---|
| **`tb_tkfoldermng`** | **(rctr_id, fls_acptn_sn STRING(19), fls_id)** | 기록물철 인수관리 |
| **`tb_tkrecordmng`** | **(rctr_id, ritm_acptn_seq STRING(19))** | 기록물건 인수관리 |
| `tb_tkattachfile` | (..., ritm_acptn_seq, reg_sn, atch_file_type) | 첨부파일 인수 (38컬럼) |
| `tb_tkfilecheck` | (..., file_hstry_seq) | 파일검수 |
| `tb_tkviruscheck` | (..., virs_hstry_seq) | 바이러스 검사이력 (28컬럼) |
| `tb_tkfolderacptnhist` | (..., hstry_seq) | 접수/반려 이력 (24컬럼) |
| `tb_tkorgrcpterror` | — | 연계인수 접수오류 (28컬럼) |
| `tb_tkmonitor` | — | 모니터링 (30컬럼) |

→ 인수 단계에는 **별도 접수일련번호** `fls_acptn_sn`(19자리) 와 `ritm_acptn_seq`(19자리) 가 등장. **`fls_id` 와 별도** 인수 시점 키.

### ③ 이관·기관간 인계
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_rdtkovrplan` | (rctr_id, tkov_se_cd, tkov_rprs_inst_cd, hndv_year, tkov_seq) | 인수인계 계획 (29컬럼) |
| `tb_dfrcpttransflist` | (rctr_id, trnsf_year, trnsf_list_id) | 접수이관 목록 (56컬럼) |
| `tb_dforgtkov` | (rctr_id, hndv_year, inst_hndv_seq) | 기관간 인계처리 |
| `tb_dforgtkovlist` | (rctr_id, hndv_year, inst_hndv_seq, trdv_cd, clsf_schm_*, **fls_id**) | 기관간 인계목록 상세 |

### ④ 마이그레이션 (이관받은 백업 보관)
| 테이블 | PK | 설명 |
|---|---|---|
| **`tb_mgfolder`** | **(trnsf_rctr_cd, prdctn_sys_fls_id STRING(28))** | 마이그레이션 기록물철 (116컬럼) |
| **`tb_mgrecord`** | **(trnsf_rctr_cd, ritm_id STRING(19))** | 마이그레이션 기록물건 (82컬럼) |
| `tb_mgbkshdpos`/`tb_mgbkshdposdtl` | — | 이관 서가배치 백업 |
| `tb_mgrecordchghist`·`tb_mgfolderkwrd`·`tb_mgrecordkwrd` | — | 이력·색인 백업 |

→ `tb_mg*` 는 외부에서 이관받은 데이터의 **원본 보존** 영역. `prdctn_sys_fls_id`(28자리) 는 원시스템 키 그대로 보관.

---

## 시나리오 5 — 공개·열람·접근권한

### ① 공개목록·재분류
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_strlslist` | (rctr_id, ritm_id) | 공개목록 (65컬럼) |
| `tb_oprlslistchg` | (id INTEGER) | 공개목록 변경 (21컬럼) |
| `tb_rdrlsclgnopnn` | (ritm_id, clgn_sn, fls_id) | 공개재분류 의견 (52컬럼) |
| `tb_rdrlshist` | (ritm_id, chg_hstry_no) | 공개속성 변경 이력 (30컬럼) |

→ CAMS `SV_DOCUMENT_OPEN`·`SV_DOCUMENT_OPEN_RECLASS` 에 해당.

### ② 열람신청
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_rdfolderprsldtl` | (rctr_id, prsl_aply_no, fls_id) | 기록물철 열람상세 (23컬럼) |
| `tb_rdrecordprsldtl` | (rctr_id, prsl_aply_no, ritm_id) | 기록물건 열람상세 (25컬럼) |

→ CAMS `US_INSPREQ_*` 와 짝.

### ③ 접근권한
| 테이블 | PK | 설명 |
|---|---|---|
| `tb_rdfolderaccs` | (rctr_id, **fls_id**, acs_seq) | 기록물철 접근 설정 |
| `tb_rdrecordaccs` | (rctr_id, **ritm_id**, acs_seq) | 기록물건 접근 설정 |

→ 접근권한은 CAMS에는 명시적 동등물이 약함. RAMP 강점.

---

## 시나리오 교차 — 같은 테이블의 라이프
- `tb_rdfolder` / `tb_rdrecord` 는 **시나리오 1 종착점** → 시나리오 2(평가)/3(서고)/4(이관)/5(공개) 의 모든 출발점.
- `fls_id`, `ritm_id` 는 RAMP 전 시나리오를 가로지르는 universal key.

---

# CAMS ↔ RAMP 통합 가능성 분석

## 1. 핵심 키 매핑 (사용자 확정)

| 개념 | CAMS | RAMP |
|---|---|---|
| **기록물철** | **`BSID`** (RG_DOCUMENT PK) | **`fls_id` STRING(14)** (tb_rdfolder PK) |
| **기록물건** | **`DSID`** (RG_DETAIL은 (BSID, DSID) 복합) | **`ritm_id` STRING(14)** (tb_rdrecord PK, 단일) |
| 기록관 | `DHOUSECODE` | `rctr_id` STRING(7) |
| 처리과 | `KIKWANCODE` | `trdv_cd` STRING(7) |
| 분류체계 | `CLSS_ID`, `BSCNTCLSCD` | `clsf_schm_id` STRING(35), `bm_cd` STRING(35) |

## 2. 키 구조 차이 — 통합의 핵심 이슈

### CAMS — 단계별 키 분리
| 단계 | 철 키 | 건 키 |
|---|---|---|
| 생산현황 (RG_S*) | `SBSID` | (SBSID, SDSID) |
| 이관 (RG_M*) | `MBSID` | (MBSID, DSID) |
| 정식등록 (RG_DOCUMENT) | `BSID` | (BSID, DSID) |
| 폐기 백업 | `BSID` (DEL_ALL 테이블 동일) | (BSID, DSID) |

→ 단계마다 키가 바뀜. 데이터 흐름이 **명시적**. 단계 추적이 키 자체로 가능.

### RAMP — 단일 surrogate key 통일
- `fls_id` / `ritm_id` 는 **시스템 생성 인조키**로 한 번 부여되면 전 시나리오 공통.
- 단계 정보는 별도 컬럼(`acptn_year`, `acptn_year_seq`, `dscd_year` 등)에 분리.
- 인수 단계에서만 별도 일련번호 `fls_acptn_sn`(19), `ritm_acptn_seq`(19) 가 동행.

## 3. 통합 시 시나리오

### A. RAMP 단일키 방식으로 통일 (RAMP 우선 정책 충실)
- CAMS의 (BSID, DSID) 복합키 → `fls_id`(14), `ritm_id`(14) 단일키로 변환.
- CAMS의 SBSID/MBSID/BSID 단계별 키 → 모두 같은 `fls_id` 로 통합, 단계는 상태컬럼으로.
- 영향:
  - ✅ 키 구조 단순화, 조인 용이.
  - ⚠️ CAMS의 단계 추적 정보가 키에서 사라짐 → **상태/이력 컬럼이 충실해야 데이터 유실 없음**.
  - 🔴 SBSID/MBSID/BSID 가 시점별로 다른 값을 가질 수 있음 → 통합 매핑 시 어느 시점을 `fls_id` 로 채택할지 결정 필요.

### B. 양쪽 키 보존
- `fls_id` 컬럼 추가 + 기존 `BSID/MBSID/SBSID` 도 별도 보존.
- ✅ 데이터 유실 0.
- ⚠️ 컬럼이 늘고 인덱스가 무거워짐.

→ **권고: A 방식 + 이력테이블(또는 별도 매핑 테이블)에 `legacy_bsid/mbsid/sbsid` 보존**. 통합 후 운영은 단일키, 추적은 매핑.

## 4. 데이터 유실 위험 영역

| 영역 | CAMS 키 / 데이터 | 위험 | 대응 |
|---|---|---|---|
| 생산현황 단계 | SBSID, SDSID | RAMP에 정식 단계 없음(생산현황은 tb_cr*에 별도 보관) | **매핑 테이블 별도** 또는 SBSID를 `tb_crfolder.fls_rcpt_sn` 으로 매핑 |
| 이관 단계 | MBSID | RAMP는 tb_tkfoldermng.fls_acptn_sn 동등 | 매핑 가능 |
| 복합키 → 단일키 | (BSID, DSID) → ritm_id | DSID가 BSID 내에서만 unique한 경우 충돌 | 합성 또는 재발번 필요 |
| 컬럼 데이터 | RG_DETAIL 99컬럼 vs tb_rdrecord 149컬럼 | 컬럼 매핑 누락 가능 | **컬럼 단위 매핑표 필수** |

## 5. 인조키 vs 의미키 — 사용자 지적 반영

**RAMP는 인조키 위주**:
- `fls_id`(14), `ritm_id`(14), `orgnl_file_id`(19), `bksh_dpos_id`(10), `prsr_box_id`(19), `bm_cd`(35), `unit_job_cd`(8), `trnsf_list_id`(10), `tag_id`(24) …
- 시스템 생성 ID라 의미 추정 불가, 키 길이가 짧음.

**CAMS는 의미키 + 복합키 다수**:
- (TRANSYEAR, DHOUSECODE, ACCEPT_ROWNO, REQ_SEQ) 같은 4단 복합키.
- 의미가 포함돼 SQL로 가독성 있는 조회 가능.

**통합 시 차이**:
- RAMP 인조키로 가면 → 어플리케이션 화면에서 의미 표시 별도 SELECT 필요.
- CAMS 의미키로 가면 → 키 길이가 길고 변경 시 cascade 영향.

→ **RAMP 우선 정책이면 인조키 방식 채택**. 의미는 별도 컬럼·인덱스로.

## 6. 통합 가능성 평가

| 시나리오 | CAMS 영역 | RAMP 영역 | 매칭 난이도 |
|---|---|---|---|
| 생산현황 | RG_S* (8개) | tb_cr* (2개 + 부속) | 🟡 RAMP 단순화 — CAMS 양식 다양성을 흡수 가능한지 확인 |
| 이관·인수 | RG_M*, CP_TRANSFER_* | tb_tk*, tb_dforgtkov* | 🟢 흐름 유사, 키만 변환 |
| 정식등록 | RG_DOCUMENT/RG_DETAIL | tb_rdfolder/tb_rdrecord | 🟢 **핵심 매핑 (사용자 확정)** |
| 보존처리 | SV_* (50+ 테이블) | tb_sr*, tb_st* (20+) | 🔴 CAMS가 훨씬 세분화 — RAMP 흡수 시 CAMS 매체 세부 정보 보존 방안 필요 |
| 공개·열람 | SV_OPEN_*, US_*, CA_* | tb_strls*, tb_op*, tb_rdrls*, tb_rd*prsl* | 🟡 RAMP에 정보공개청구(CA_*) 동등물 약함 |
| 재평가·폐기 | SV_DISUSE_*, RG_SEXHAUST* | tb_df* | 🟢 흐름 유사 |
| 검색·서비스 | STS_*, K2_*, KN_*, UCI_*, CN_* | tb_rd*kwrd (제한적) | 🔴 CAMS의 대국민서비스·검색 인프라가 압도적으로 풍부 — RAMP 측 신규 구축 또는 CAMS 영역 유지 |

## 7. 권고 통합 전략

1. **핵심 마스터(기록물철·건)**: RAMP 단일키 방식 채택. `BSID/DSID` 는 mapping 테이블에 보존.
2. **이관·인수**: RAMP 구조 채택 가능. CAMS의 단계 키(SBSID/MBSID) 는 매핑.
3. **보존처리**: CAMS 영역 유지 권고. RAMP에 흡수하려면 RAMP 측 신규 테이블 다수 필요.
4. **공개·서비스**: CAMS의 정보공개청구·UCI·포털 연계는 별도 모듈로 유지. RAMP 측은 내부 공개 관리만.
5. **데이터 유실 방지 장치**:
   - `legacy_key_mapping` 테이블 — (sbsid|mbsid|bsid|dsid) ↔ (fls_id|ritm_id) 영구 보관
   - 단계 정보 컬럼 — `lifecycle_stage_cd` 등 명시
   - 컬럼 단위 매핑표 — 사용 안 되는 CAMS 컬럼도 archive 컬럼으로 보존

---

## 다음 작업
1. **컬럼 단위 매핑표** — `RG_DOCUMENT(110컬럼) ↔ tb_rdfolder(170컬럼)` 컬럼별 매핑 (의미 일치/불일치/RAMP-only/CAMS-only)
2. **`RG_DETAIL(99) ↔ tb_rdrecord(149)`** 동일
3. **보존처리 영역 매핑** — `SV_*` 50+ ↔ `tb_sr*`·`tb_st*`. 결손 영역 정리
4. **매핑 테이블 설계** — legacy 키 보존 스키마
5. **RAMP BEFORE/AFTER 파일** 도입 (단어·도메인 표준화 변경 추적용)

---

## 8. 키 매핑 도메인 분석 결과 (2026-05-19 추가)

CAMS 스키마 측 실제 컬럼 도메인을 확인한 결과 (참고 — CAMS BSID/DSID는 VARCHAR2(12), RAMP fls_id/ritm_id는 STRING(14)):

| # | CAMS | RAMP | 도메인 일치 | 결정 |
|---|---|---|---|---|
| K0+ | `BSID`/`DSID` VARCHAR2(12) | `fls_id`/`ritm_id` STRING(14) | ⚠️ 12 vs 14 | **같은 단위 — 강제 정렬. VARCHAR(14) 한 컬럼에 양쪽 값 공존(가변길이)** |
| K5 기록관 | `ORG_CODE` VARCHAR2(7) (+10자 변형) | `rctr_id` STRING(7) | ✅ 7 | 통일 — CAMS 내부 10자 변형은 7자로 표준화 선행 |
| K6 처리과 | `ORG_COD` VARCHAR2(7) (+10자 변형) | `trdv_cd` STRING(7) | ✅ 7 | K5와 동일 |
| K7 분류체계 | `BSCNTCLSCD/CLSS_ID` 분산 | `clsf_schm_id` STRING(35) + `bm_cd` STRING(35) | 🟡 RAMP 2개 분리 | RAMP 구조 채택, CAMS → 두 키로 분해 매핑 |
| K8 단위업무 | `UNTWK_COD` VARCHAR2(8) | `unit_job_cd` STRING(8) | ✅ 8 | **직접 매핑** |
| K9 보관상자 | `BOKWANBOXNO` VARCHAR2(21) | `prsr_box_id` STRING(19) | 🔴 21 vs 19 | 재발번 (RAMP 19자) |
| K10 서고·서가 | `SGMNGNO` VARCHAR2(10) + 서가/서고 NUMBER(3) | `bksh_dpos_id` STRING(10), `bksh_id/stk_id` STRING(3) | ✅ 일치 | **직접 매핑** |
| K11 원문파일 | `RG_APPENDFILE` PK=(BSID,DSID,DOC_REL,APPENDSID,FILE_NM) 복합 | `orgnl_file_id` STRING(19) 단일 | 🔴 구조 다름 | RAMP 단일키 채택, CAMS 재발번 + 복합키 매핑테이블 |
| K12 NEO파일 | `NF_FILE_ID` VARCHAR2(15) | `fls_neob_file_id` STRING(19) | 🟡 15 vs 19 | 같은 단위 — 강제 정렬, STRING(19) 공존 |
| K13 광디스크 | `OD_SEQNO` NUMBER(8), `OD_MNG_NO` VARCHAR2(12) | `opds_id` STRING(15) | 🔴 타입 다름 | RAMP 형식 채택, CAMS 재발번 |
| K14 사용자 | `USR_ID/USER_ID` VARCHAR2(20) | `user_id` STRING(35) | 🟡 20 vs 35 | 같은 단위 — 강제 정렬, STRING(35) 공존 (시스템 통합 필수) |
| K15 RFID 태그 | `TAGID` VARCHAR2(24) | `tag_id` STRING(24) | ✅ 24 | **직접 매핑** |

### 강제 정렬 원칙 (K0+, K12, K14)
- "강제 정렬" = **RAMP 컬럼 정의 채택, CAMS 값을 가변길이 VARCHAR로 그대로 적재**.
- 별도 패딩·변환 불필요. CAMS 12자 BSID, RAMP 14자 fls_id 가 한 `VARCHAR(14)` 컬럼에 자기 형식 유지하며 공존.
- **충돌 검사 필수**: 두 시스템의 값 공간이 우연히 겹치는지(같은 문자열이 양쪽에서 다른 의미로 쓰이는지) 확인. 충돌 시 prefix 부여 등 대응.

### 직접 매핑 가능 (도메인 일치)
**K6 처리과 / K8 단위업무 / K10 서고·서가 / K15 RFID** — 길이·타입 모두 일치.

### 재발번 필요 (구조·길이 본질적 차이)
**K9 보관상자 / K11 원문파일 / K13 광디스크** — 재발번 + legacy 키 매핑 테이블 보존.

### CAMS 측 선행 작업
**K5/K6** — CAMS 내부 7자/10자 변형을 7자로 표준화 (CAMS 도메인 정리 작업과 합쳐 처리).

---

산출: `09_RAMP_업무흐름_분석.md` (본 문서). 통합 작업의 기준.
