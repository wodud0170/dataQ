package qualitycenter.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ndata.quality.model.std.*;

/**
 * q-common 모델 클래스 단위 테스트
 *
 * 대상: TermAnalysisResult, StdWordVo, StdTermsVo, StdDomainVo,
 *       StdDiagResultVo, StdDataModelAttrVo, UploadResult, StdDiagJobVo,
 *       StdDomainGroupVo, StdDomainClassificationVo
 */
class ModelVoTest {

    // ==================================================================
    // TermAnalysisResult
    // ==================================================================

    @Nested
    @DisplayName("TermAnalysisResult")
    class TermAnalysisResultTest {

        @Test
        @DisplayName("기본 필드 getter/setter")
        void basicFields() {
            TermAnalysisResult r = new TermAnalysisResult();
            r.setInputNm("사용자이름");
            r.setStatus("AUTO");
            r.setRecommendedEngAbrvNm("USR_NM");
            r.setRecommendedDomainNm("V20");
            r.setRecommendedDataType("VARCHAR");
            r.setRecommendedDataLen(20);

            assertEquals("사용자이름", r.getInputNm());
            assertEquals("AUTO", r.getStatus());
            assertEquals("USR_NM", r.getRecommendedEngAbrvNm());
            assertEquals("V20", r.getRecommendedDomainNm());
            assertEquals("VARCHAR", r.getRecommendedDataType());
            assertEquals(20, r.getRecommendedDataLen());
        }

        @Test
        @DisplayName("existingTerm 세팅 (REGISTERED)")
        void existingTerm() {
            TermAnalysisResult r = new TermAnalysisResult();
            r.setStatus("REGISTERED");
            StdTermsVo term = new StdTermsVo();
            term.setTermsNm("사용자이름");
            term.setTermsEngAbrvNm("USR_NM");
            r.setExistingTerm(term);

            assertNotNull(r.getExistingTerm());
            assertEquals("사용자이름", r.getExistingTerm().getTermsNm());
        }

        @Test
        @DisplayName("words 리스트 세팅")
        void wordsList() {
            TermAnalysisResult r = new TermAnalysisResult();
            List<TermAnalysisResult.WordAnalysis> words = new ArrayList<>();
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("사용자");
            wa.setStatus("MATCHED");
            words.add(wa);
            r.setWords(words);

            assertEquals(1, r.getWords().size());
            assertEquals("사용자", r.getWords().get(0).getWordNm());
        }

        @Test
        @DisplayName("alternativeWords 세팅 (2순위 분리)")
        void alternativeWords() {
            TermAnalysisResult r = new TermAnalysisResult();
            List<TermAnalysisResult.WordAnalysis> altWords = new ArrayList<>();
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("핸드");
            altWords.add(wa);
            r.setAlternativeWords(altWords);
            r.setAlternativeEngAbrvNm("HNDPHN_NO");

            assertNotNull(r.getAlternativeWords());
            assertEquals(1, r.getAlternativeWords().size());
            assertEquals("HNDPHN_NO", r.getAlternativeEngAbrvNm());
        }

        @Test
        @DisplayName("domainCandidates 세팅")
        void domainCandidates() {
            TermAnalysisResult r = new TermAnalysisResult();
            List<StdDomainVo> candidates = new ArrayList<>();
            StdDomainVo d = new StdDomainVo();
            d.setDomainNm("V20");
            d.setDataType("VARCHAR");
            d.setDataLen((short) 20);
            candidates.add(d);
            r.setDomainCandidates(candidates);

            assertNotNull(r.getDomainCandidates());
            assertEquals(1, r.getDomainCandidates().size());
            assertEquals("V20", r.getDomainCandidates().get(0).getDomainNm());
        }

        @Test
        @DisplayName("null 필드 기본값")
        void nullDefaults() {
            TermAnalysisResult r = new TermAnalysisResult();
            assertNull(r.getInputNm());
            assertNull(r.getStatus());
            assertNull(r.getWords());
            assertNull(r.getExistingTerm());
            assertNull(r.getDomainCandidates());
            assertNull(r.getAlternativeWords());
            assertEquals(0, r.getRecommendedDataLen());
        }
    }

    // ==================================================================
    // WordAnalysis 내부 클래스
    // ==================================================================

    @Nested
    @DisplayName("TermAnalysisResult.WordAnalysis")
    class WordAnalysisTest {

        @Test
        @DisplayName("MATCHED 상태 - candidates와 selected 세팅")
        void matchedStatus() {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("사용자");
            wa.setStatus("MATCHED");

            TermAnalysisResult.WordCandidate c1 = new TermAnalysisResult.WordCandidate();
            c1.setWordId("id1");
            c1.setWordNm("사용자");
            c1.setWordEngAbrvNm("USR");
            c1.setWordEngNm("USER");
            c1.setDomainClsfNm("");
            c1.setScore(500);
            c1.setSelected(true);

            TermAnalysisResult.WordCandidate c2 = new TermAnalysisResult.WordCandidate();
            c2.setWordId("id2");
            c2.setWordNm("사용자");
            c2.setWordEngAbrvNm("USER");
            c2.setWordEngNm("USER");
            c2.setScore(100);
            c2.setSelected(false);

            wa.setCandidates(Arrays.asList(c1, c2));
            wa.setSelected(c1);

            assertEquals(2, wa.getCandidates().size());
            assertTrue(wa.getSelected().isSelected());
            assertEquals("USR", wa.getSelected().getWordEngAbrvNm());
        }

        @Test
        @DisplayName("NEW 상태 - newWord 세팅")
        void newStatus() {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("핸드폰");
            wa.setStatus("NEW");

            TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
            suggestion.setWordEngAbrvNm("HNDPHN");
            suggestion.setWordEngNm("HANDPHONE");
            suggestion.setDomainClsfNm("");
            wa.setNewWord(suggestion);

            assertEquals("NEW", wa.getStatus());
            assertEquals("HNDPHN", wa.getNewWord().getWordEngAbrvNm());
            assertEquals("HANDPHONE", wa.getNewWord().getWordEngNm());
            assertEquals("", wa.getNewWord().getDomainClsfNm());
        }

        @Test
        @DisplayName("WordCandidate 스코어 비교")
        void candidateScoreComparison() {
            TermAnalysisResult.WordCandidate c1 = new TermAnalysisResult.WordCandidate();
            c1.setScore(500);
            TermAnalysisResult.WordCandidate c2 = new TermAnalysisResult.WordCandidate();
            c2.setScore(100);

            List<TermAnalysisResult.WordCandidate> list = new ArrayList<>(Arrays.asList(c2, c1));
            list.sort((a, b) -> b.getScore() - a.getScore());

            assertEquals(500, list.get(0).getScore());
            assertEquals(100, list.get(1).getScore());
        }
    }

    // ==================================================================
    // StdWordVo
    // ==================================================================

    @Nested
    @DisplayName("StdWordVo")
    class StdWordVoTest {

        @Test
        @DisplayName("기본 필드 getter/setter")
        void basicFields() {
            StdWordVo w = new StdWordVo();
            w.setId("uuid-1");
            w.setWordNm("사용자");
            w.setWordEngAbrvNm("USR");
            w.setWordEngNm("USER");
            w.setWordDesc("사용자 설명");
            w.setWordClsfYn("N");
            w.setDomainClsfNm("");
            w.setPartOfSpeech("NN");
            w.setCommStndYn("Y");

            assertEquals("uuid-1", w.getId());
            assertEquals("사용자", w.getWordNm());
            assertEquals("USR", w.getWordEngAbrvNm());
            assertEquals("USER", w.getWordEngNm());
            assertEquals("사용자 설명", w.getWordDesc());
            assertEquals("N", w.getWordClsfYn());
            assertEquals("NN", w.getPartOfSpeech());
            assertEquals("Y", w.getCommStndYn());
        }

        @Test
        @DisplayName("기본 승인 상태 N")
        void defaultAprvYn() {
            StdWordVo w = new StdWordVo();
            assertEquals("N", w.getAprvYn());
        }

        @Test
        @DisplayName("이음동의어/금칙어 배열 세팅")
        void arrayFields() {
            StdWordVo w = new StdWordVo();
            w.setAllophSynmLst(new String[]{"유저", "사용인"});
            w.setForbdnWordLst(new String[]{"비속어1"});

            assertEquals(2, w.getAllophSynmLst().length);
            assertEquals(1, w.getForbdnWordLst().length);
            assertEquals("유저", w.getAllophSynmLst()[0]);
        }

        @Test
        @DisplayName("감사 필드 세팅 (생성자/수정자/승인자)")
        void auditFields() {
            StdWordVo w = new StdWordVo();
            w.setCretUserId("admin");
            w.setCretDt("20260330120000");
            w.setUpdtUserId("user1");
            w.setUpdtDt("20260330130000");
            w.setAprvUserId("admin");
            w.setAprvStatUpdtDt("20260330140000");

            assertEquals("admin", w.getCretUserId());
            assertEquals("20260330120000", w.getCretDt());
            assertEquals("user1", w.getUpdtUserId());
            assertEquals("admin", w.getAprvUserId());
        }

        @Test
        @DisplayName("요청 시스템 코드/명 세팅")
        void reqSysFields() {
            StdWordVo w = new StdWordVo();
            w.setReqSysCd("SYS001");
            w.setReqSysNm("기간계시스템");

            assertEquals("SYS001", w.getReqSysCd());
            assertEquals("기간계시스템", w.getReqSysNm());
        }
    }

    // ==================================================================
    // StdTermsVo
    // ==================================================================

    @Nested
    @DisplayName("StdTermsVo")
    class StdTermsVoTest {

        @Test
        @DisplayName("기본 필드 getter/setter")
        void basicFields() {
            StdTermsVo t = new StdTermsVo();
            t.setId("terms-1");
            t.setTermsNm("사용자이름");
            t.setTermsEngAbrvNm("USR_NM");
            t.setTermsDesc("사용자의 이름");
            t.setDomainNm("V20");
            t.setDataType("VARCHAR");
            t.setDataLen((short) 20);
            t.setDataDecimalLen((short) 0);

            assertEquals("terms-1", t.getId());
            assertEquals("사용자이름", t.getTermsNm());
            assertEquals("USR_NM", t.getTermsEngAbrvNm());
            assertEquals("V20", t.getDomainNm());
            assertEquals("VARCHAR", t.getDataType());
            assertEquals(20, t.getDataLen());
            assertEquals(0, t.getDataDecimalLen());
        }

        @Test
        @DisplayName("기본 승인 상태 N")
        void defaultAprvYn() {
            StdTermsVo t = new StdTermsVo();
            assertEquals("N", t.getAprvYn());
        }

        @Test
        @DisplayName("wordList 세팅")
        void wordList() {
            StdTermsVo t = new StdTermsVo();
            StdTermsVo.Word w1 = new StdTermsVo.Word();
            w1.setTermsId("terms-1");
            w1.setWordId("word-1");
            w1.setWordNm("사용자");
            w1.setWordOrd((short) 1);

            StdTermsVo.Word w2 = new StdTermsVo.Word();
            w2.setTermsId("terms-1");
            w2.setWordId("word-2");
            w2.setWordNm("이름");
            w2.setWordOrd((short) 2);

            t.setWordList(new StdTermsVo.Word[]{w1, w2});

            assertEquals(2, t.getWordList().length);
            assertEquals("사용자", t.getWordList()[0].getWordNm());
            assertEquals(1, t.getWordList()[0].getWordOrd());
            assertEquals("이름", t.getWordList()[1].getWordNm());
        }

        @Test
        @DisplayName("코드그룹/저장형식/표현형식 세팅")
        void additionalFields() {
            StdTermsVo t = new StdTermsVo();
            t.setCodeGrp("GRP01");
            t.setStorFmt("YYYYMMDD");
            t.setExprFmtLst(new String[]{"YYYY-MM-DD", "YYYY/MM/DD"});
            t.setAllowValLst(new String[]{"Y", "N"});

            assertEquals("GRP01", t.getCodeGrp());
            assertEquals("YYYYMMDD", t.getStorFmt());
            assertEquals(2, t.getExprFmtLst().length);
            assertEquals(2, t.getAllowValLst().length);
        }
    }

    // ==================================================================
    // UploadResult
    // ==================================================================

    @Nested
    @DisplayName("UploadResult")
    class UploadResultTest {

        @Test
        @DisplayName("addSuccess - totalCount와 successCount 증가")
        void addSuccess() {
            UploadResult r = new UploadResult();
            r.addSuccess();
            r.addSuccess();
            r.addSuccess();

            assertEquals(3, r.getTotalCount());
            assertEquals(3, r.getSuccessCount());
            assertEquals(0, r.getSkipCount());
            assertEquals(0, r.getFailCount());
        }

        @Test
        @DisplayName("addSkip - totalCount와 skipCount 증가")
        void addSkip() {
            UploadResult r = new UploadResult();
            r.addSkip();
            r.addSkip();

            assertEquals(2, r.getTotalCount());
            assertEquals(0, r.getSuccessCount());
            assertEquals(2, r.getSkipCount());
            assertEquals(0, r.getFailCount());
        }

        @Test
        @DisplayName("addFail - totalCount, failCount 증가, failDetails 추가")
        void addFail() {
            UploadResult r = new UploadResult();
            r.addFail("행 1: 단어명 누락");
            r.addFail("행 2: 영문약어 누락");

            assertEquals(2, r.getTotalCount());
            assertEquals(0, r.getSuccessCount());
            assertEquals(2, r.getFailCount());
            assertEquals(2, r.getFailDetails().size());
            assertEquals("행 1: 단어명 누락", r.getFailDetails().get(0));
        }

        @Test
        @DisplayName("혼합 결과 (성공+중복+실패)")
        void mixedResults() {
            UploadResult r = new UploadResult();
            r.addSuccess();
            r.addSuccess();
            r.addSkip();
            r.addFail("실패1");

            assertEquals(4, r.getTotalCount());
            assertEquals(2, r.getSuccessCount());
            assertEquals(1, r.getSkipCount());
            assertEquals(1, r.getFailCount());
        }

        @Test
        @DisplayName("failDetails 최대 100건 제한")
        void failDetailsMaxLimit() {
            UploadResult r = new UploadResult();
            for (int i = 0; i < 150; i++) {
                r.addFail("실패 " + i);
            }

            assertEquals(150, r.getFailCount(), "failCount는 전체 실패 수");
            assertEquals(100, r.getFailDetails().size(), "failDetails는 최대 100건");
            assertEquals(150, r.getTotalCount());
        }

        @Test
        @DisplayName("초기 상태")
        void initialState() {
            UploadResult r = new UploadResult();
            assertEquals(0, r.getTotalCount());
            assertEquals(0, r.getSuccessCount());
            assertEquals(0, r.getSkipCount());
            assertEquals(0, r.getFailCount());
            assertNotNull(r.getFailDetails());
            assertTrue(r.getFailDetails().isEmpty());
        }
    }

    // ==================================================================
    // StdDiagResultVo
    // ==================================================================

    @Nested
    @DisplayName("StdDiagResultVo")
    class StdDiagResultVoTest {

        @Test
        @DisplayName("전체 필드 세팅 및 조회")
        void allFields() {
            StdDiagResultVo r = new StdDiagResultVo();
            r.setResultId(1L);
            r.setDiagJobId("JOB-001");
            r.setObjNm("TBL_USER");
            r.setAttrNm("USER_ID");
            r.setAttrNmKr("사용자ID");
            r.setDiagType("DATA_LEN_MISMATCH");
            r.setDiagDetail("데이터 길이 불일치");
            r.setStdValue("20");
            r.setActualValue("16");

            assertEquals(1L, r.getResultId());
            assertEquals("JOB-001", r.getDiagJobId());
            assertEquals("TBL_USER", r.getObjNm());
            assertEquals("USER_ID", r.getAttrNm());
            assertEquals("사용자ID", r.getAttrNmKr());
            assertEquals("DATA_LEN_MISMATCH", r.getDiagType());
            assertEquals("20", r.getStdValue());
            assertEquals("16", r.getActualValue());
        }
    }

    // ==================================================================
    // StdDataModelAttrVo
    // ==================================================================

    @Nested
    @DisplayName("StdDataModelAttrVo")
    class StdDataModelAttrVoTest {

        @Test
        @DisplayName("전체 필드 세팅 및 조회")
        void allFields() {
            StdDataModelAttrVo a = new StdDataModelAttrVo();
            a.setClctId("clct-1");
            a.setDataModelId("dm-1");
            a.setObjNm("TBL_USER");
            a.setObjNmKr("사용자테이블");
            a.setAttrNm("USER_ID");
            a.setAttrNmKr("사용자ID");
            a.setDataType("VARCHAR");
            a.setDataLen(20);
            a.setDataDecimalLen((short) 0);
            a.setNullableYn("N");
            a.setTermsStndYn("Y");
            a.setDomainStndYn("Y");
            a.setPkYn("Y");
            a.setFkYn("N");
            a.setDefaultVal(null);
            a.setAttrOrder((short) 1);

            assertEquals("clct-1", a.getClctId());
            assertEquals("TBL_USER", a.getObjNm());
            assertEquals("VARCHAR", a.getDataType());
            assertEquals(20, a.getDataLen());
            assertEquals(0, a.getDataDecimalLen());
            assertEquals("Y", a.getPkYn());
            assertEquals(1, a.getAttrOrder());
        }

        @Test
        @DisplayName("wordLst/wordStndLst 배열 세팅")
        void arrayFields() {
            StdDataModelAttrVo a = new StdDataModelAttrVo();
            a.setWordLst(new String[]{"사용자", "ID"});
            a.setWordStndLst(new String[]{"Y", "Y"});

            assertEquals(2, a.getWordLst().length);
            assertEquals(2, a.getWordStndLst().length);
        }
    }

    // ==================================================================
    // StdDomainVo
    // ==================================================================

    @Nested
    @DisplayName("StdDomainVo")
    class StdDomainVoTest {

        @Test
        @DisplayName("도메인 기본 필드")
        void basicFields() {
            StdDomainVo d = new StdDomainVo();
            d.setId("dom-1");
            d.setDomainNm("V20");
            d.setDomainGrpNm("문자");
            d.setDomainClsfNm("명");
            d.setDomainDesc("VARCHAR 20자리");
            d.setDataType("VARCHAR");
            d.setDataLen((short) 20);
            d.setDataDecimalLen((short) 0);
            d.setDataUnit("");
            d.setStorFmt("");

            assertEquals("V20", d.getDomainNm());
            assertEquals("문자", d.getDomainGrpNm());
            assertEquals("명", d.getDomainClsfNm());
            assertEquals("VARCHAR", d.getDataType());
            assertEquals(20, d.getDataLen());
        }

        @Test
        @DisplayName("기본 승인 상태 N")
        void defaultAprvYn() {
            StdDomainVo d = new StdDomainVo();
            assertEquals("N", d.getAprvYn());
        }
    }

    // ==================================================================
    // StdDiagJobVo
    // ==================================================================

    @Nested
    @DisplayName("StdDiagJobVo")
    class StdDiagJobVoTest {

        @Test
        @DisplayName("전체 필드 세팅 및 조회")
        void allFields() {
            StdDiagJobVo j = new StdDiagJobVo();
            j.setDiagJobId("JOB-001");
            j.setClctId("clct-1");
            j.setDataModelId("dm-1");
            j.setDataModelNm("테스트모델");
            j.setStatus("RUNNING");
            j.setTotalCnt(100);
            j.setProcessCnt(50);
            j.setResultCnt(5);

            assertEquals("JOB-001", j.getDiagJobId());
            assertEquals("RUNNING", j.getStatus());
            assertEquals(100, j.getTotalCnt());
            assertEquals(50, j.getProcessCnt());
            assertEquals(5, j.getResultCnt());
        }

        @Test
        @DisplayName("상태값 종류")
        void statusValues() {
            StdDiagJobVo j = new StdDiagJobVo();
            for (String status : Arrays.asList("READY", "RUNNING", "DONE", "STOPPED", "ERROR")) {
                j.setStatus(status);
                assertEquals(status, j.getStatus());
            }
        }
    }

    // ==================================================================
    // StdDomainGroupVo / StdDomainClassificationVo
    // ==================================================================

    @Nested
    @DisplayName("StdDomainGroupVo")
    class StdDomainGroupVoTest {

        @Test
        @DisplayName("필드 세팅")
        void fields() {
            StdDomainGroupVo g = new StdDomainGroupVo();
            g.setId("grp-1");
            g.setDomainGrpNm("문자");
            g.setCommStndYn("Y");
            g.setMagntdOrd("1");

            assertEquals("grp-1", g.getId());
            assertEquals("문자", g.getDomainGrpNm());
            assertEquals("Y", g.getCommStndYn());
        }
    }

    @Nested
    @DisplayName("StdDomainClassificationVo")
    class StdDomainClassificationVoTest {

        @Test
        @DisplayName("필드 세팅")
        void fields() {
            StdDomainClassificationVo c = new StdDomainClassificationVo();
            c.setId("clsf-1");
            c.setDomainClsfNm("명");
            c.setDomainGrpNm("문자");
            c.setCommStndYn("Y");

            assertEquals("clsf-1", c.getId());
            assertEquals("명", c.getDomainClsfNm());
            assertEquals("문자", c.getDomainGrpNm());
        }
    }

    // ==================================================================
    // StdDataModelVo
    // ==================================================================

    @Nested
    @DisplayName("StdDataModelVo")
    class StdDataModelVoTest {

        @Test
        @DisplayName("기본 필드 및 현황 필드")
        void allFields() {
            StdDataModelVo m = new StdDataModelVo();
            m.setDataModelId("dm-1");
            m.setDataModelNm("테스트모델");
            m.setDataModelSysCd("SYS001");
            m.setDataModelSysNm("기간계");
            m.setVer("1.0");
            m.setUseYn("Y");
            m.setStndRate(95.5);
            m.setNonStndCnt(3);
            m.setNoTermsCnt(1);
            m.setDomainMismatchCnt(2);

            assertEquals("dm-1", m.getDataModelId());
            assertEquals("테스트모델", m.getDataModelNm());
            assertEquals("Y", m.getUseYn());
            assertEquals(95.5, m.getStndRate(), 0.01);
            assertEquals(3, m.getNonStndCnt());
            assertEquals(1, m.getNoTermsCnt());
            assertEquals(2, m.getDomainMismatchCnt());
        }
    }
}
