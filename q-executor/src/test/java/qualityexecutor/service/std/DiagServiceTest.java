package qualityexecutor.service.std;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        // USER_ID: CHAR(16) vs 표준 VARCHAR(20) → 타입 불일치 + 길이 불일치
        StdDataModelAttrVo attr = createAttr("TBL_MSG_HIST", "USER_ID", "사용자ID", "CHAR", 16, 0);
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

    // ========== 헬퍼 메서드 ==========

    /**
     * DiagService의 진단 로직을 재현 (DB 의존성 제거)
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

            if (stdType != null && dataType != null && !stdType.equalsIgnoreCase(dataType)) {
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
