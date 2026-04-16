-- ============================================================
-- 진단 테스트용 오라클 테이블 DDL
-- 행정안전부 공통표준 용어/도메인 기반
--
-- ★ 표준 준수 컬럼과 의도적 오류 컬럼이 혼합되어 있습니다.
-- ★ 오류 컬럼에는 주석으로 [오류] 표시
-- ============================================================

-- -------------------------------------------------------
-- 1. 거래처 기본 (표준 준수율 높음 - 약 90%)
-- -------------------------------------------------------
CREATE TABLE TB_CNPT_BASE (
    CNPT_NO            VARCHAR2(20)   NOT NULL,   -- 거래처번호      도메인: 번호V20
    CNPT_NM            VARCHAR2(100),             -- 거래처명        도메인: 명V100  (표준 없을 수 있음)
    CNPT_RPRSV_NM      VARCHAR2(100),             -- 거래처대표자명  도메인: 명V100
    CNPT_BRNO          CHAR(10),                  -- 거래처사업자등록번호 도메인: 사업자등록번호C10
    CNPT_DEPT_NM       VARCHAR2(200),             -- 거래처부서명    도메인: 명V200
    CNPT_ZIP           CHAR(5),                   -- 거래처우편번호  도메인: 우편번호C5
    CNPT_ADDR          VARCHAR2(200),             -- 거래처주소      도메인: 주소V200
    CNPT_DADDR         VARCHAR2(200),             -- 거래처상세주소  도메인: 상세주소V200
    CNPT_TELNO         VARCHAR2(11),              -- 거래처전화번호  도메인: 전화번호V11  (표준 없을 수 있음)
    CNPT_PIC_NM        VARCHAR2(100),             -- 거래처담당자명  도메인: 명V100
    CNPT_PIC_TELNO     VARCHAR2(11),              -- 거래처담당자전화번호 도메인: 전화번호V11
    USE_YN             CHAR(1),                   -- 사용여부        도메인: 여부C1
    REG_DT             CHAR(14),                  -- 등록일시 (표준 용어 없을 수 있음)
    CONSTRAINT PK_TB_CNPT_BASE PRIMARY KEY (CNPT_NO)
);

COMMENT ON TABLE  TB_CNPT_BASE IS '거래처 기본';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_NO        IS '거래처번호';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_NM        IS '거래처명';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_RPRSV_NM  IS '거래처대표자명';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_BRNO      IS '거래처사업자등록번호';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_DEPT_NM   IS '거래처부서명';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_ZIP       IS '거래처우편번호';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_ADDR      IS '거래처주소';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_DADDR     IS '거래처상세주소';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_TELNO     IS '거래처전화번호';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_PIC_NM    IS '거래처담당자명';
COMMENT ON COLUMN TB_CNPT_BASE.CNPT_PIC_TELNO IS '거래처담당자전화번호';
COMMENT ON COLUMN TB_CNPT_BASE.USE_YN         IS '사용여부';
COMMENT ON COLUMN TB_CNPT_BASE.REG_DT         IS '등록일시';

-- -------------------------------------------------------
-- 2. 검수 관리 (표준 준수율 약 70% - 타입/길이 오류 포함)
-- -------------------------------------------------------
CREATE TABLE TB_IGI_MNG (
    IGI_NO             VARCHAR2(25)   NOT NULL,   -- 검수번호        도메인: 번호V25
    IGI_YMD            CHAR(8),                   -- 검수일자        도메인: 연월일C8
    IGI_QTY            NUMBER(10,3),              -- 검수수량        도메인: 수N10,3
    IGI_AMT            NUMBER(15),                -- 검수금액        도메인: 금액N15
    IGI_CN             VARCHAR2(4000),            -- 검수내용        도메인: 내용V4000
    IGI_RSLT_CN        VARCHAR2(4000),            -- 검수결과내용    도메인: 내용V4000
    IGI_CMPTN_YN       CHAR(1),                   -- 검수완료여부    도메인: 여부C1
    IGI_CMPTN_YMD      CHAR(8),                   -- 검수완료일자    도메인: 연월일C8
    IGI_DMND_YMD       CHAR(8),                   -- 검수요청일자    도메인: 연월일C8
    IGI_PLC_NM         VARCHAR2(100),             -- 검수장소명      도메인: 명V100
    CHCKR_NM           VARCHAR2(50),              -- [오류-길이] 검수자명 표준: 명V100 → 50으로 줄임
    IGI_YN             VARCHAR2(1),               -- [오류-타입] 검수여부 표준: 여부C1(CHAR) → VARCHAR2
    CONSTRAINT PK_TB_IGI_MNG PRIMARY KEY (IGI_NO)
);

COMMENT ON TABLE  TB_IGI_MNG IS '검수 관리';
COMMENT ON COLUMN TB_IGI_MNG.IGI_NO        IS '검수번호';
COMMENT ON COLUMN TB_IGI_MNG.IGI_YMD       IS '검수일자';
COMMENT ON COLUMN TB_IGI_MNG.IGI_QTY       IS '검수수량';
COMMENT ON COLUMN TB_IGI_MNG.IGI_AMT       IS '검수금액';
COMMENT ON COLUMN TB_IGI_MNG.IGI_CN        IS '검수내용';
COMMENT ON COLUMN TB_IGI_MNG.IGI_RSLT_CN   IS '검수결과내용';
COMMENT ON COLUMN TB_IGI_MNG.IGI_CMPTN_YN  IS '검수완료여부';
COMMENT ON COLUMN TB_IGI_MNG.IGI_CMPTN_YMD IS '검수완료일자';
COMMENT ON COLUMN TB_IGI_MNG.IGI_DMND_YMD  IS '검수요청일자';
COMMENT ON COLUMN TB_IGI_MNG.IGI_PLC_NM    IS '검수장소명';
COMMENT ON COLUMN TB_IGI_MNG.CHCKR_NM      IS '검수자명';
COMMENT ON COLUMN TB_IGI_MNG.IGI_YN        IS '검수여부';

-- -------------------------------------------------------
-- 3. 건물 정보 (표준 준수율 약 60% - 다양한 오류)
-- -------------------------------------------------------
CREATE TABLE TB_BLDG_INFO (
    BLDG_MNG_NO        VARCHAR2(25)   NOT NULL,   -- 건물관리번호    도메인: 번호V25
    BLDG_NM            VARCHAR2(100),             -- 건물명          도메인: 명V100
    BLDG_ADDR          VARCHAR2(200),             -- 건물주소        도메인: 주소V200
    BLDG_USG_NM        VARCHAR2(100),             -- 건물용도명      도메인: 명V100
    BLDG_USG_CD        CHAR(2),                   -- 건물용도코드    도메인: 코드C2
    BLDG_FLR_CNT       NUMBER(4),                 -- 건물층수        도메인: 수N4
    BLDG_AMT           NUMBER(15),                -- 건물금액        도메인: 금액N15
    BLDG_HGT           NUMBER(10,3),              -- 건물높이        도메인: 수N10,3
    BLDG_CNT           NUMBER(10),                -- 건물수          도메인: 수N10
    BLDG_FLR_NM        VARCHAR2(50),              -- [오류-길이] 건물층명 표준: 명V100 → 50으로 줄임
    BLDG_STRCT_CN      VARCHAR2(2000),            -- [오류-길이] 건물구조내용 표준: 내용V4000 → 2000으로 줄임
    BLDG_XUAR          NUMBER(15,5),              -- [오류-길이] 건물전용면적 표준: 면적N19,9 → (15,5)
    BNO                VARCHAR2(10),              -- 건물번호        도메인: 건물번호V10
    BMNO               NUMBER(5),                 -- 건물본번        도메인: 건물본번N5
    BSNO               NUMBER(5),                 -- 건물부번        도메인: 건물부번N5
    BLDG_EXPLN         VARCHAR2(4000),            -- 건물설명        도메인: 내용V4000
    BLDG_APRS_EVL_AMT  NUMBER(10),                -- [오류-길이] 건물감정평가금액 표준: 금액N15 → 10으로 줄임
    BLDG_EQVL          NUMBER(18),                -- 건물가액        도메인: 금액N18
    CONSTRAINT PK_TB_BLDG_INFO PRIMARY KEY (BLDG_MNG_NO)
);

COMMENT ON TABLE  TB_BLDG_INFO IS '건물 정보';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_MNG_NO       IS '건물관리번호';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_NM            IS '건물명';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_ADDR          IS '건물주소';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_USG_NM        IS '건물용도명';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_USG_CD        IS '건물용도코드';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_FLR_CNT       IS '건물층수';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_AMT           IS '건물금액';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_HGT           IS '건물높이';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_CNT           IS '건물수';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_FLR_NM        IS '건물층명';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_STRCT_CN      IS '건물구조내용';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_XUAR          IS '건물전용면적';
COMMENT ON COLUMN TB_BLDG_INFO.BNO                 IS '건물번호';
COMMENT ON COLUMN TB_BLDG_INFO.BMNO                IS '건물본번';
COMMENT ON COLUMN TB_BLDG_INFO.BSNO                IS '건물부번';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_EXPLN         IS '건물설명';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_APRS_EVL_AMT  IS '건물감정평가금액';
COMMENT ON COLUMN TB_BLDG_INFO.BLDG_EQVL          IS '건물가액';

-- -------------------------------------------------------
-- 4. 거래 이력 (표준 준수율 약 80%)
-- -------------------------------------------------------
CREATE TABLE TB_DLNG_HIST (
    DLNG_NO            VARCHAR2(20)   NOT NULL,   -- 거래번호 (표준 미존재 가능)
    DLNG_BGNG_YMD      CHAR(8),                   -- 거래시작일자    도메인: 연월일C8
    DLNG_END_YMD       CHAR(8),                   -- 거래종료일자    도메인: 연월일C8
    DLNG_BGNG_DT       DATE,                      -- 거래시작일시    도메인: 연월일시분초D
    DLNG_END_DT        DATE,                      -- 거래종료일시    도메인: 연월일시분초D
    DLNG_QTY           NUMBER(10,3),              -- 거래수량        도메인: 수N10,3
    DLNG_UNTPRC        NUMBER(15),                -- 거래단가        도메인: 금액N15
    DLNG_SUM_AMT       NUMBER(15),                -- 거래합계금액    도메인: 금액N15
    DLNG_BGNG_TM       CHAR(6),                   -- 거래시작시각    도메인: 시분초C6
    DLNG_END_TM        CHAR(6),                   -- 거래종료시각    도메인: 시분초C6
    DLNG_RSLT_CD       VARCHAR2(10),              -- 거래결과코드 (표준 미존재)
    DLNG_MEMO          VARCHAR2(500),             -- 거래메모 (표준 미존재 - 비표준 영문명)
    CONSTRAINT PK_TB_DLNG_HIST PRIMARY KEY (DLNG_NO)
);

COMMENT ON TABLE  TB_DLNG_HIST IS '거래 이력';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_NO        IS '거래번호';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_BGNG_YMD  IS '거래시작일자';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_END_YMD   IS '거래종료일자';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_BGNG_DT   IS '거래시작일시';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_END_DT    IS '거래종료일시';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_QTY       IS '거래수량';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_UNTPRC    IS '거래단가';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_SUM_AMT   IS '거래합계금액';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_BGNG_TM   IS '거래시작시각';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_END_TM    IS '거래종료시각';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_RSLT_CD   IS '거래결과코드';
COMMENT ON COLUMN TB_DLNG_HIST.DLNG_MEMO      IS '거래메모';

-- -------------------------------------------------------
-- 5. 강의 관리 (표준 준수율 약 50% - 한글명 불일치 포함)
-- -------------------------------------------------------
CREATE TABLE TB_LCTR_MNG (
    LCTR_NO            VARCHAR2(20)   NOT NULL,   -- 강의번호 (표준 미존재)
    LCTR_NM            VARCHAR2(200),             -- 강의명 (표준 미존재 가능)
    LCTR_BGNG_YMD      CHAR(8),                   -- 강의시작일자    도메인: 연월일C8
    LCTR_END_YMD       CHAR(8),                   -- 강의종료일자    도메인: 연월일C8
    LCTR_BGNG_HM       CHAR(4),                   -- 강의시작시분    도메인: 시분C4
    LCTR_END_HM        CHAR(4),                   -- 강의종료시분    도메인: 시분C4
    LCTR_BGNG_DT       DATE,                      -- 강의시작일시    도메인: 연월일시분초D
    LCTR_END_DT        DATE,                      -- 강의종료일시    도메인: 연월일시분초D
    INSTR_NM           VARCHAR2(100),             -- 강사명 (표준 미존재 가능)
    INSTR_TELNO        VARCHAR2(11),              -- 강사전화번호    도메인: 전화번호V11
    INSTR_MBL_TELNO    VARCHAR2(11),              -- 강사휴대전화번호 도메인: 전화번호V11
    INSTR_EML_ADDR     VARCHAR2(320),             -- 강사이메일주소  도메인: 주소V320
    INSTR_ZIP          CHAR(5),                   -- 강사우편번호    도메인: 우편번호C5
    INSTR_ADDR         VARCHAR2(200),             -- 강사주소        도메인: 주소V200
    INSTR_DADDR        VARCHAR2(200),             -- 강사상세주소    도메인: 상세주소V200
    CONSTRAINT PK_TB_LCTR_MNG PRIMARY KEY (LCTR_NO)
);

COMMENT ON TABLE  TB_LCTR_MNG IS '강의 관리';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_NO         IS '강의번호';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_NM         IS '강의명';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_BGNG_YMD   IS '강의시작일자';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_END_YMD    IS '강의종료일자';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_BGNG_HM    IS '강의시작시분';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_END_HM     IS '강의종료시분';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_BGNG_DT    IS '강의시작일시';
COMMENT ON COLUMN TB_LCTR_MNG.LCTR_END_DT     IS '강의종료일시';
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_NM        IS '강사이름';          -- [오류-한글명] 표준: 강사명 → 강사이름
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_TELNO     IS '강사전화번호';
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_MBL_TELNO IS '강사핸드폰번호';    -- [오류-한글명] 표준: 강사휴대전화번호 → 강사핸드폰번호
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_EML_ADDR  IS '강사이메일주소';
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_ZIP       IS '강사우편번호';
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_ADDR      IS '강사주소';
COMMENT ON COLUMN TB_LCTR_MNG.INSTR_DADDR     IS '강사상세주소';

-- -------------------------------------------------------
-- 6. 가맹점 관리 (표준 준수율 약 75% - 혼합 오류)
-- -------------------------------------------------------
CREATE TABLE TB_FRCS_MNG (
    FRCS_NO            VARCHAR2(20)   NOT NULL,   -- 가맹점번호 (표준 미존재 가능)
    FRCS_NM            VARCHAR2(100),             -- 가맹점명 (표준 미존재 가능)
    FRCS_RPRSV_NM      VARCHAR2(100),             -- 가맹점대표자명  도메인: 명V100
    FRCS_BRNO          CHAR(10),                  -- 가맹점사업자등록번호 도메인: 사업자등록번호C10
    FRCS_ZIP           CHAR(5),                   -- 가맹점우편번호  도메인: 우편번호C5
    FRCS_ADDR          VARCHAR2(200),             -- 가맹점주소      도메인: 주소V200
    FRCS_DADDR         VARCHAR2(200),             -- 가맹점상세주소  도메인: 상세주소V200
    FRCS_TELNO         VARCHAR2(11),              -- 가맹점전화번호  도메인: 전화번호V11
    FRCS_OPEN_YMD      CHAR(8),                   -- 가맹점개점일자 (표준 미존재)
    FRCS_CLOSE_YMD     CHAR(8),                   -- 가맹점폐점일자 (표준 미존재)
    FRCS_GRADE         VARCHAR2(10),              -- 가맹점등급 (비표준 영문명)
    FRCS_STATUS        VARCHAR2(2),               -- 가맹점상태 (비표준 영문명)
    CONSTRAINT PK_TB_FRCS_MNG PRIMARY KEY (FRCS_NO)
);

COMMENT ON TABLE  TB_FRCS_MNG IS '가맹점 관리';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_NO         IS '가맹점번호';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_NM         IS '가맹점명';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_RPRSV_NM   IS '가맹점대표자명';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_BRNO       IS '가맹점사업자등록번호';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_ZIP        IS '가맹점우편번호';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_ADDR       IS '가맹점주소';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_DADDR      IS '가맹점상세주소';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_TELNO      IS '가맹점전화번호';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_OPEN_YMD   IS '가맹점개점일자';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_CLOSE_YMD  IS '가맹점폐점일자';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_GRADE      IS '가맹점등급';
COMMENT ON COLUMN TB_FRCS_MNG.FRCS_STATUS     IS '가맹점상태';

-- -------------------------------------------------------
-- 7. FAQ 관리 (표준 준수율 100% - 완벽 준수)
-- -------------------------------------------------------
CREATE TABLE TB_FAQ_MNG (
    FAQ_SN             NUMBER(22)     NOT NULL,   -- FAQ일련번호      도메인: 일련번호N22
    FAQ_TTL            VARCHAR2(256),             -- FAQ제목          도메인: 명V256
    FAQ_CN             VARCHAR2(4000),            -- FAQ내용          도메인: 내용V4000
    CONSTRAINT PK_TB_FAQ_MNG PRIMARY KEY (FAQ_SN)
);

COMMENT ON TABLE  TB_FAQ_MNG IS 'FAQ 관리';
COMMENT ON COLUMN TB_FAQ_MNG.FAQ_SN  IS 'FAQ일련번호';
COMMENT ON COLUMN TB_FAQ_MNG.FAQ_TTL IS 'FAQ제목';
COMMENT ON COLUMN TB_FAQ_MNG.FAQ_CN  IS 'FAQ내용';

-- -------------------------------------------------------
-- 8. 게시물 관리 (표준 준수율 약 40% - 오류 많음)
-- -------------------------------------------------------
CREATE TABLE TB_PST_MNG (
    PST_SEQ            NUMBER(10)     NOT NULL,   -- 게시물순번 (비표준 영문명 - SEQ)
    PST_TITLE          VARCHAR2(256),             -- 게시물제목 (비표준 영문명 - TITLE vs TTL)
    PST_CONTENT        VARCHAR2(4000),            -- 게시물내용 (비표준 영문명 - CONTENT vs CN)
    PST_PSWD           VARCHAR2(50),              -- [오류-길이] 게시물비밀번호 표준: 번호V100 → 50
    PST_REG_DT         DATE,                      -- 게시물등록일시  도메인: 연월일시분초D
    PST_REG_YMD        CHAR(8),                   -- 게시물등록일자  도메인: 연월일C8
    PSTG_FWK_USE_YN    CHAR(1),                   -- 게시기능사용여부 도메인: 여부C1
    HIT_CNT            NUMBER(10),                -- 조회수 (비표준 영문명)
    DEL_YN             CHAR(1),                   -- 삭제여부 (비표준 영문명)
    REGSTR_ID          VARCHAR2(20),              -- 등록자ID (비표준 영문명)
    CONSTRAINT PK_TB_PST_MNG PRIMARY KEY (PST_SEQ)
);

COMMENT ON TABLE  TB_PST_MNG IS '게시물 관리';
COMMENT ON COLUMN TB_PST_MNG.PST_SEQ         IS '게시물순번';
COMMENT ON COLUMN TB_PST_MNG.PST_TITLE       IS '게시물제목';
COMMENT ON COLUMN TB_PST_MNG.PST_CONTENT     IS '게시물내용';
COMMENT ON COLUMN TB_PST_MNG.PST_PSWD        IS '게시물비밀번호';
COMMENT ON COLUMN TB_PST_MNG.PST_REG_DT      IS '게시물등록일시';
COMMENT ON COLUMN TB_PST_MNG.PST_REG_YMD     IS '게시물등록일자';
COMMENT ON COLUMN TB_PST_MNG.PSTG_FWK_USE_YN IS '게시기능사용여부';
COMMENT ON COLUMN TB_PST_MNG.HIT_CNT         IS '조회수';
COMMENT ON COLUMN TB_PST_MNG.DEL_YN          IS '삭제여부';
COMMENT ON COLUMN TB_PST_MNG.REGSTR_ID       IS '등록자ID';

-- -------------------------------------------------------
-- 9. SMS 발송 이력 (표준 준수율 약 65% - 타입 오류 포함)
-- -------------------------------------------------------
CREATE TABLE TB_SMS_SNDNG_HIST (
    SMS_SN             NUMBER(22)     NOT NULL,   -- SMS일련번호 (표준 미존재 가능)
    SMS_CN             VARCHAR2(2000),            -- SMS내용          도메인: 내용V2000
    SMS_SNDNG_YN       CHAR(1),                   -- SMS발송여부      도메인: 여부C1
    SMS_USE_YN         CHAR(1),                   -- SMS사용여부      도메인: 여부C1
    SMS_RCPTN_TELNO    VARCHAR2(11),              -- SMS수신전화번호  도메인: 전화번호V11
    SMS_TRSM_TELNO     VARCHAR2(11),              -- SMS전송전화번호  도메인: 전화번호V11
    SMS_SNDNG_DT       VARCHAR2(14),              -- [오류-타입] SMS발송일시 표준: 연월일시분초D(DATE) → VARCHAR2
    SMS_RCPTN_DT       VARCHAR2(14),              -- [오류-타입] SMS수신일시 (표준 미존재 + VARCHAR로 처리)
    SMS_SNDNG_RSLT_CD  VARCHAR2(10),              -- SMS발송결과코드 (비표준 영문명)
    SMS_FAIL_RSN       VARCHAR2(500),             -- SMS실패사유 (비표준 영문명)
    CONSTRAINT PK_TB_SMS_SNDNG_HIST PRIMARY KEY (SMS_SN)
);

COMMENT ON TABLE  TB_SMS_SNDNG_HIST IS 'SMS 발송 이력';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_SN            IS 'SMS일련번호';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_CN            IS 'SMS내용';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_SNDNG_YN      IS 'SMS발송여부';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_USE_YN        IS 'SMS사용여부';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_RCPTN_TELNO   IS 'SMS수신전화번호';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_TRSM_TELNO    IS 'SMS전송전화번호';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_SNDNG_DT      IS 'SMS발송일시';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_RCPTN_DT      IS 'SMS수신일시';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_SNDNG_RSLT_CD IS 'SMS발송결과코드';
COMMENT ON COLUMN TB_SMS_SNDNG_HIST.SMS_FAIL_RSN      IS 'SMS실패사유';

-- -------------------------------------------------------
-- 10. ERP 연계 관리 (표준 준수율 약 55% - 복합 오류)
-- -------------------------------------------------------
CREATE TABLE TB_ERP_LINK_MNG (
    ERP_LINK_SEQ       NUMBER(10)     NOT NULL,   -- ERP연계순번 (비표준 영문명)
    ERP_LINK_YN        CHAR(1),                   -- ERP연계여부      도메인: 여부C1
    ERP_TRSM_YN        CHAR(1),                   -- ERP전송여부      도메인: 여부C1
    ERP_TRSM_DT        DATE,                      -- ERP전송일시      도메인: 연월일시분초D (표준 미존재 가능)
    EAI_TRSM_YN        CHAR(1),                   -- EAI전송여부      도메인: 여부C1
    EAI_TRSM_DT        DATE,                      -- EAI전송일시      도메인: 연월일시분초D
    EAI_TRSM_YMD       CHAR(8),                   -- EAI전송일자      도메인: 연월일C8
    EAI_PRCS_DT        DATE,                      -- EAI처리일시      도메인: 연월일시분초D
    EAI_PRCS_YMD       VARCHAR2(8),               -- [오류-타입] EAI처리일자 표준: 연월일C8(CHAR) → VARCHAR2
    ESB_LINK_DT        DATE,                      -- ESB연계일시      도메인: 연월일시분초D
    LINK_STAT_CD       VARCHAR2(10),              -- 연계상태코드 (비표준 영문명)
    ERR_MSG            VARCHAR2(2000),            -- 오류메시지 (비표준 영문명)
    RETRY_CNT          NUMBER(3),                 -- 재시도횟수 (비표준 영문명)
    CONSTRAINT PK_TB_ERP_LINK_MNG PRIMARY KEY (ERP_LINK_SEQ)
);

COMMENT ON TABLE  TB_ERP_LINK_MNG IS 'ERP 연계 관리';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ERP_LINK_SEQ IS 'ERP연계순번';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ERP_LINK_YN  IS 'ERP연계여부';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ERP_TRSM_YN  IS 'ERP전송여부';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ERP_TRSM_DT  IS 'ERP전송일시';
COMMENT ON COLUMN TB_ERP_LINK_MNG.EAI_TRSM_YN  IS 'EAI전송여부';
COMMENT ON COLUMN TB_ERP_LINK_MNG.EAI_TRSM_DT  IS 'EAI전송일시';
COMMENT ON COLUMN TB_ERP_LINK_MNG.EAI_TRSM_YMD IS 'EAI전송일자';
COMMENT ON COLUMN TB_ERP_LINK_MNG.EAI_PRCS_DT  IS 'EAI처리일시';
COMMENT ON COLUMN TB_ERP_LINK_MNG.EAI_PRCS_YMD IS 'EAI처리일자';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ESB_LINK_DT  IS 'ESB연계일시';
COMMENT ON COLUMN TB_ERP_LINK_MNG.LINK_STAT_CD IS '연계상태코드';
COMMENT ON COLUMN TB_ERP_LINK_MNG.ERR_MSG      IS '오류메시지';
COMMENT ON COLUMN TB_ERP_LINK_MNG.RETRY_CNT    IS '재시도횟수';
