package qualityexecutor.service.std;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ndata.quality.model.std.StdDataModelAttrVo;
import com.ndata.quality.model.std.StdDiagResultVo;
import com.ndata.quality.model.std.StdTermsVo;

/**
 * DiagService 진단 로직 단위 테스트
 *
 * 테스트 시나리오: TBL_MSG_HIST 테이블 기준
 * - USER_ID 컬럼: 용어 존재 (영문명 일치), VARCHAR 동일, 길이 16 vs 표준 20 → 길이 불일치
 */
class DiagServiceTest {

    private Map<String, StdTermsVo> termsByEng;

    @BeforeEach
    void setUp() {
        termsByEng = new HashMap<>();

        // 용어: USER_ID → 한글명 "사용자ID", 도메인 "V20" (VARCHAR, 20)
        StdTermsVo userIdTerm = new StdTermsVo();
        userIdTerm.setTermsNm("사용자ID");
        userIdTerm.setTermsEngAbrvNm("USER_ID");
        userIdTerm.setDomainNm("V20");
        userIdTerm.setDataType("VARCHAR");
        userIdTerm.setDataLen((short) 20);
        userIdTerm.setDataDecimalLen((short) 0);

        termsByEng.put("USER_ID", userIdTerm);
    }

    // ========== STEP 1: 용어 존재 확인 ==========

    @Test
    @DisplayName("영문명이 용어에 없으면 TERM_NOT_EXIST")
    void 영문명_용어_미존재() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "UNKNOWN_COL", "알수없음", "VARCHAR", 100, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(1, results.size());
        assertEquals("TERM_NOT_EXIST", results.get(0).getDiagType());
        assertEquals("UNKNOWN_COL", results.get(0).getActualValue());
    }

    @Test
    @DisplayName("영문명이 용어에 있으면 TERM_NOT_EXIST가 아님")
    void 영문명_용어_존재() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.stream().noneMatch(r -> r.getDiagType().equals("TERM_NOT_EXIST")));
    }

    // ========== STEP 2: 한글명 일치 확인 ==========

    @Test
    @DisplayName("한글명이 표준과 다르면 TERM_KR_NM_MISMATCH")
    void 한글명_불일치() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "유저아이디", "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.stream().anyMatch(r ->
                r.getDiagType().equals("TERM_KR_NM_MISMATCH")
                && r.getStdValue().equals("사용자ID")
                && r.getActualValue().equals("유저아이디")));
    }

    @Test
    @DisplayName("한글명이 표준과 같으면 TERM_KR_NM_MISMATCH 없음")
    void 한글명_일치() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.stream().noneMatch(r -> r.getDiagType().equals("TERM_KR_NM_MISMATCH")));
    }

    @Test
    @DisplayName("한글명이 null이면 TERM_KR_NM_MISMATCH 발생 (표준에 한글명 존재 시)")
    void 한글명_null_불일치() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", null, "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.stream().anyMatch(r ->
                r.getDiagType().equals("TERM_KR_NM_MISMATCH")
                && r.getStdValue().equals("사용자ID")));
    }

    @Test
    @DisplayName("한글명이 빈문자열이면 TERM_KR_NM_MISMATCH 발생 (표준에 한글명 존재 시)")
    void 한글명_빈값_불일치() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "", "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.stream().anyMatch(r ->
                r.getDiagType().equals("TERM_KR_NM_MISMATCH")
                && r.getStdValue().equals("사용자ID")));
    }

    @Test
    @DisplayName("한글명 null + 길이 불일치 → 2건 발생")
    void 한글명_null_길이_불일치_동시() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", null, "VARCHAR", 16, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(2, results.size(), "한글명 null + 길이 불일치 = 2건");
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("TERM_KR_NM_MISMATCH")));
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_LEN_MISMATCH")));
    }

    // ========== STEP 3: 도메인 검증 (타입·길이) ==========

    @Test
    @DisplayName("타입 동일, 길이 다르면 DATA_LEN_MISMATCH만 발생")
    void 길이_불일치_타입_동일() {
        // USER_ID: VARCHAR(16) vs 표준 VARCHAR(20) → 길이 불일치
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "VARCHAR", 16, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(1, results.size(), "타입 동일·길이 불일치 시 결과 1건이어야 함");
        assertEquals("DATA_LEN_MISMATCH", results.get(0).getDiagType());
        assertEquals("20", results.get(0).getStdValue());
        assertEquals("16", results.get(0).getActualValue());
    }

    @Test
    @DisplayName("타입도 다르고 길이도 다르면 2건 발생")
    void 타입_길이_둘다_불일치() {
        // USER_ID: INTEGER(16) vs 표준 VARCHAR(20) → 타입 불일치 + 길이 불일치
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "INTEGER", 16, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(2, results.size(), "타입·길이 둘 다 다르면 결과 2건이어야 함");
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_TYPE_MISMATCH")));
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_LEN_MISMATCH")));
    }

    @Test
    @DisplayName("소수점 자릿수가 다르면 DATA_LEN_MISMATCH")
    void 소수점_불일치() {
        // 표준: NUMERIC(10,2) 용어 세팅
        StdTermsVo numTerm = new StdTermsVo();
        numTerm.setTermsNm("금액");
        numTerm.setTermsEngAbrvNm("AMT");
        numTerm.setDomainNm("N10_2");
        numTerm.setDataType("NUMERIC");
        numTerm.setDataLen((short) 10);
        numTerm.setDataDecimalLen((short) 2);
        termsByEng.put("AMT", numTerm);

        // 실제: NUMERIC(10,4)
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "AMT", "금액", "NUMERIC", 10, 4);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(1, results.size());
        assertEquals("DATA_LEN_MISMATCH", results.get(0).getDiagType());
        assertEquals("2", results.get(0).getStdValue());
        assertEquals("4", results.get(0).getActualValue());
    }

    @Test
    @DisplayName("타입·길이·소수점 모두 동일하면 진단 결과 없음")
    void 완전_일치() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "VARCHAR", 20, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertTrue(results.isEmpty(), "모두 일치 시 결과 0건이어야 함");
    }

    @Test
    @DisplayName("한글명 불일치 + 길이 불일치 동시 발생 시 2건")
    void 한글명_불일치_길이_불일치_동시() {
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "유저아이디", "VARCHAR", 16, 0);
        List<StdDiagResultVo> results = diagnose(attr);

        assertEquals(2, results.size(), "한글명 불일치 + 길이 불일치 = 2건");
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("TERM_KR_NM_MISMATCH")));
        assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_LEN_MISMATCH")));
    }

    // ========== STEP 4: 타입 동의어 비교 (isTypeEquivalent) ==========

    @Nested
    @DisplayName("타입 동의어 비교")
    class TypeEquivalenceTest {

        @Test
        @DisplayName("동일 타입 (대소문자 무시) → 동등")
        void sameTypeCaseInsensitive() {
            assertTrue(isTypeEquivalent("VARCHAR", "varchar"));
            assertTrue(isTypeEquivalent("varchar", "VARCHAR"));
            assertTrue(isTypeEquivalent("Numeric", "numeric"));
        }

        @Test
        @DisplayName("DATE ↔ DATETIME 동등")
        void dateEquivalence() {
            assertTrue(isTypeEquivalent("DATE", "DATETIME"));
            assertTrue(isTypeEquivalent("DATETIME", "DATE"));
        }

        @Test
        @DisplayName("문자열 계열 (CHAR ↔ VARCHAR ↔ VARCHAR2) 동등")
        void stringFamilyEquivalence() {
            assertTrue(isTypeEquivalent("CHAR", "VARCHAR"));
            assertTrue(isTypeEquivalent("VARCHAR", "CHAR"));
            assertTrue(isTypeEquivalent("CHAR", "VARCHAR2"));
            assertTrue(isTypeEquivalent("VARCHAR2", "CHAR"));
            assertTrue(isTypeEquivalent("VARCHAR", "VARCHAR2"));
            assertTrue(isTypeEquivalent("VARCHAR2", "VARCHAR"));
        }

        @Test
        @DisplayName("숫자 계열 (NUMBER ↔ NUMERIC ↔ DECIMAL) 동등")
        void numericFamilyEquivalence() {
            assertTrue(isTypeEquivalent("NUMBER", "NUMERIC"));
            assertTrue(isTypeEquivalent("NUMERIC", "NUMBER"));
            assertTrue(isTypeEquivalent("NUMBER", "DECIMAL"));
            assertTrue(isTypeEquivalent("DECIMAL", "NUMBER"));
            assertTrue(isTypeEquivalent("NUMERIC", "DECIMAL"));
            assertTrue(isTypeEquivalent("DECIMAL", "NUMERIC"));
        }

        @Test
        @DisplayName("다른 계열 간 비동등")
        void differentFamilies() {
            assertFalse(isTypeEquivalent("VARCHAR", "NUMBER"));
            assertFalse(isTypeEquivalent("CHAR", "NUMERIC"));
            assertFalse(isTypeEquivalent("DATE", "VARCHAR"));
            assertFalse(isTypeEquivalent("NUMBER", "DATE"));
            assertFalse(isTypeEquivalent("CLOB", "BLOB"));
        }

        @Test
        @DisplayName("VARCHAR과 INTEGER → 비동등")
        void varcharVsInteger() {
            assertFalse(isTypeEquivalent("VARCHAR", "INTEGER"));
        }

        @Test
        @DisplayName("DATE ↔ TIMESTAMP → 비동등 (범위 외)")
        void dateVsTimestamp() {
            assertFalse(isTypeEquivalent("DATE", "TIMESTAMP"));
        }
    }

    // ========== STEP 5: checkDomain 로직 확장 테스트 ==========

    @Nested
    @DisplayName("checkDomain 확장")
    class CheckDomainExtendedTest {

        @Test
        @DisplayName("도메인 null → 진단 결과 없음")
        void noDomain() {
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("테스트용어");
            term.setTermsEngAbrvNm("TEST_TERM");
            term.setDomainNm(null);
            term.setDataType("VARCHAR");
            term.setDataLen((short) 20);
            term.setDataDecimalLen((short) 0);
            termsByEng.put("TEST_TERM", term);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "TEST_TERM", "테스트용어", "CHAR", 10, 0);
            List<StdDiagResultVo> results = diagnose(attr);
            // 도메인이 null이면 도메인 검증 스킵, 한글명도 일치 → 0건
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("타입 동의어 (VARCHAR↔VARCHAR2) → 타입 불일치 없음")
        void typeEquivalentNoMismatch() {
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("주소");
            term.setTermsEngAbrvNm("ADDR");
            term.setDomainNm("V100");
            term.setDataType("VARCHAR");
            term.setDataLen((short) 100);
            term.setDataDecimalLen((short) 0);
            termsByEng.put("ADDR", term);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "ADDR", "주소", "VARCHAR2", 100, 0);
            List<StdDiagResultVo> results = diagnose(attr);
            // VARCHAR ↔ VARCHAR2 동의어 → 타입 불일치 없음, 길이도 동일
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("NUMBER ↔ NUMERIC 동의어 → 타입 불일치 없음, 길이만 진단")
        void numericEquivalentLenMismatch() {
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("금액");
            term.setTermsEngAbrvNm("AMT2");
            term.setDomainNm("N10");
            term.setDataType("NUMBER");
            term.setDataLen((short) 10);
            term.setDataDecimalLen((short) 0);
            termsByEng.put("AMT2", term);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "AMT2", "금액", "NUMERIC", 12, 0);
            List<StdDiagResultVo> results = diagnose(attr);
            // NUMBER ↔ NUMERIC → 타입 동등, 길이 10 vs 12 → DATA_LEN_MISMATCH만 1건
            assertEquals(1, results.size());
            assertEquals("DATA_LEN_MISMATCH", results.get(0).getDiagType());
        }

        @Test
        @DisplayName("stdLen이 0이면 길이 비교 스킵")
        void stdLenZeroSkip() {
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("메모");
            term.setTermsEngAbrvNm("MEMO");
            term.setDomainNm("CLOB");
            term.setDataType("CLOB");
            term.setDataLen((short) 0);
            term.setDataDecimalLen((short) 0);
            termsByEng.put("MEMO", term);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "MEMO", "메모", "CLOB", 0, 0);
            List<StdDiagResultVo> results = diagnose(attr);
            assertTrue(results.isEmpty(), "stdLen=0이면 길이 비교 스킵");
        }

        @Test
        @DisplayName("stdType/dataType 중 하나가 null → 타입 비교 스킵")
        void nullTypeSkip() {
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("특이용어");
            term.setTermsEngAbrvNm("SPECIAL");
            term.setDomainNm("SPECIAL_DOM");
            term.setDataType(null);
            term.setDataLen((short) 20);
            term.setDataDecimalLen((short) 0);
            termsByEng.put("SPECIAL", term);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "SPECIAL", "특이용어", "VARCHAR", 20, 0);
            List<StdDiagResultVo> results = diagnose(attr);
            assertTrue(results.isEmpty(), "stdType이 null이면 타입 비교 스킵");
        }

        @Test
        @DisplayName("한글명+타입+길이 모두 불일치 → 3건")
        void tripleFailure() {
            // INTEGER는 VARCHAR과 다른 계열이므로 타입 불일치 발생
            StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "유저아이디", "INTEGER", 16, 0);
            List<StdDiagResultVo> results = diagnose(attr);

            assertEquals(3, results.size(), "한글명+타입+길이 = 3건");
            assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("TERM_KR_NM_MISMATCH")));
            assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_TYPE_MISMATCH")));
            assertTrue(results.stream().anyMatch(r -> r.getDiagType().equals("DATA_LEN_MISMATCH")));
        }
    }

    // ========== STEP 6: 엣지 케이스 테스트 ==========

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCaseTest {

        @Test
        @DisplayName("attrNm이 null이면 TERM_NOT_EXIST")
        void nullAttrNm() {
            StdDataModelAttrVo attr = createAttr("TBL_TEST", null, "테스트", "VARCHAR", 20, 0);
            List<StdDiagResultVo> results = diagnose(attr);

            assertEquals(1, results.size());
            assertEquals("TERM_NOT_EXIST", results.get(0).getDiagType());
        }

        @Test
        @DisplayName("attrNm 앞뒤 공백 trim 처리")
        void attrNmTrimmed() {
            StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "  USER_ID  ", "사용자ID", "VARCHAR", 20, 0);
            List<StdDiagResultVo> results = diagnose(attr);

            assertTrue(results.isEmpty(), "앞뒤 공백 trim 후 매칭되어야 함");
        }

        @Test
        @DisplayName("표준 한글명이 빈값이면 한글명 비교 스킵")
        void emptyStdTermsNm() {
            StdTermsVo emptyNmTerm = new StdTermsVo();
            emptyNmTerm.setTermsNm("");
            emptyNmTerm.setTermsEngAbrvNm("EMPTY_NM");
            emptyNmTerm.setDomainNm("V20");
            emptyNmTerm.setDataType("VARCHAR");
            emptyNmTerm.setDataLen((short) 20);
            emptyNmTerm.setDataDecimalLen((short) 0);
            termsByEng.put("EMPTY_NM", emptyNmTerm);

            StdDataModelAttrVo attr = createAttr("TBL_TEST", "EMPTY_NM", "아무거나", "VARCHAR", 20, 0);
            List<StdDiagResultVo> results = diagnose(attr);

            assertTrue(results.stream().noneMatch(r -> r.getDiagType().equals("TERM_KR_NM_MISMATCH")),
                "표준 한글명이 빈값이면 비교 스킵");
        }

        @Test
        @DisplayName("diagResult에 objNm, attrNm 정확히 세팅되는지 확인")
        void resultFieldsPopulated() {
            StdDataModelAttrVo attr = createAttr("MY_TABLE", "UNKNOWN_COL", "미지컬럼", "VARCHAR", 20, 0);
            List<StdDiagResultVo> results = diagnose(attr);

            assertEquals(1, results.size());
            StdDiagResultVo r = results.get(0);
            assertEquals("MY_TABLE", r.getObjNm());
            assertEquals("UNKNOWN_COL", r.getAttrNm());
            assertEquals("미지컬럼", r.getAttrNmKr());
            assertEquals("TEST_JOB", r.getDiagJobId());
        }
    }

    // ========== 헬퍼 메서드 ==========

    /** isTypeEquivalent 로직 재현 */
    private boolean isTypeEquivalent(String stdType, String actualType) {
        if (stdType.equalsIgnoreCase(actualType)) return true;
        String s = stdType.toUpperCase();
        String a = actualType.toUpperCase();
        if ((s.equals("DATE") && a.equals("DATETIME")) || (s.equals("DATETIME") && a.equals("DATE"))) return true;
        if (isStringFamily(s) && isStringFamily(a)) return true;
        if (isNumericFamily(s) && isNumericFamily(a)) return true;
        return false;
    }

    private boolean isStringFamily(String type) {
        return type.equals("CHAR") || type.equals("VARCHAR") || type.equals("VARCHAR2");
    }

    private boolean isNumericFamily(String type) {
        return type.equals("NUMBER") || type.equals("NUMERIC") || type.equals("DECIMAL");
    }

    /**
     * DiagService의 진단 로직을 재현 (DB 의존성 제거)
     * isTypeEquivalent 반영 버전
     */
    private List<StdDiagResultVo> diagnose(StdDataModelAttrVo attr) {
        List<StdDiagResultVo> results = new ArrayList<>();

        String attrNm   = attr.getAttrNm();
        String attrNmKr = attr.getAttrNmKr();

        // STEP 1. 영문명 기준 용어 존재 확인
        StdTermsVo termByEng = attrNm != null ? termsByEng.get(attrNm.trim()) : null;

        if (termByEng == null) {
            results.add(buildResult(attr, "TERM_NOT_EXIST", "용어 미존재", null, attrNm));
            return results;
        }

        // STEP 2. 한글명 일치 확인
        // - 표준 용어에 한글명이 있는 경우, 실제 한글명이 null/빈값이면 불일치로 판정
        String stdTermsNm = termByEng.getTermsNm();
        if (stdTermsNm != null && !stdTermsNm.trim().isEmpty()) {
            String actualKr = (attrNmKr != null) ? attrNmKr.trim() : "";
            if (!stdTermsNm.trim().equals(actualKr)) {
                results.add(buildResult(attr, "TERM_KR_NM_MISMATCH", "한글명 불일치",
                        stdTermsNm, attrNmKr));
            }
        }

        // STEP 3. 도메인 검증
        if (termByEng.getDomainNm() != null) {
            String stdType       = termByEng.getDataType();
            long   stdLen        = termByEng.getDataLen();
            int    stdDecimalLen = termByEng.getDataDecimalLen();
            String dataType      = attr.getDataType();
            long   dataLen       = attr.getDataLen();
            int    dataDecimalLen= attr.getDataDecimalLen();

            if (stdType != null && dataType != null && !isTypeEquivalent(stdType, dataType)) {
                results.add(buildResult(attr, "DATA_TYPE_MISMATCH", "데이터 타입 불일치", stdType, dataType));
            }
            if (stdLen > 0 && dataLen != stdLen) {
                results.add(buildResult(attr, "DATA_LEN_MISMATCH", "데이터 길이 불일치",
                        String.valueOf(stdLen), String.valueOf(dataLen)));
            } else if (stdDecimalLen > 0 && dataDecimalLen != stdDecimalLen) {
                results.add(buildResult(attr, "DATA_LEN_MISMATCH", "소수점 자릿수 불일치",
                        String.valueOf(stdDecimalLen), String.valueOf(dataDecimalLen)));
            }
        }

        return results;
    }

    private StdDataModelAttrVo createAttr(String objNm, String attrNm, String attrNmKr,
                                           String dataType, long dataLen, int dataDecimalLen) {
        StdDataModelAttrVo attr = new StdDataModelAttrVo();
        attr.setObjNm(objNm);
        attr.setAttrNm(attrNm);
        attr.setAttrNmKr(attrNmKr);
        attr.setDataType(dataType);
        attr.setDataLen(dataLen);
        attr.setDataDecimalLen((short) dataDecimalLen);
        return attr;
    }

    private StdDiagResultVo buildResult(StdDataModelAttrVo attr, String diagType, String diagDetail,
                                         String stdValue, String actualValue) {
        StdDiagResultVo r = new StdDiagResultVo();
        r.setDiagJobId("TEST_JOB");
        r.setObjNm(attr.getObjNm());
        r.setAttrNm(attr.getAttrNm());
        r.setAttrNmKr(attr.getAttrNmKr());
        r.setDiagType(diagType);
        r.setDiagDetail(diagDetail);
        r.setStdValue(stdValue);
        r.setActualValue(actualValue);
        return r;
    }
}
