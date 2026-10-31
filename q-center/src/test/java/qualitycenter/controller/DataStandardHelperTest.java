package qualitycenter.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ndata.quality.model.std.StdWordVo;
import com.ndata.quality.model.std.TermAnalysisResult;

/**
 * DataStandardController의 순수 헬퍼 메서드 단위 테스트
 *
 * 대상: generateAlternativeSplit, subSplit,
 *       buildWordAnalyses, composeEngAbrv, judgeStatus
 *
 * DB 의존성 없이 로직만 재현하여 테스트
 */
class DataStandardHelperTest {

    // TB_WORD 시뮬레이션
    private Map<String, List<StdWordVo>> wordsByNm;
    // TB_WORD_DICT 시뮬레이션
    private Map<String, Map<String, Object>> wordDict;
    // 단어 사용 횟수 시뮬레이션
    private Map<String, Integer> usageMap;

    @BeforeEach
    void setUp() {
        wordsByNm = new HashMap<>();
        wordDict = new HashMap<>();
        usageMap = new HashMap<>();

        addWord("사용자", "USER", "USR", "");
        addWord("이름", "NAME", "NM", "");
        addWord("전화", "TELEPHONE", "TEL", "");
        addWord("번호", "NUMBER", "NO", "번호");
        addWord("주소", "ADDRESS", "ADDR", "");
        addWord("운전", "DRIVING", "DRVNG", "");
        addWord("면허", "LICENSE", "LCNS", "");
        addWord("코드", "CODE", "CD", "코드");
        addWord("일자", "DATE", "DT", "일자");
        addWord("금액", "AMOUNT", "AMT", "금액");

        addDict("핸드폰", "HANDPHONE", "HNDPHN", "");
        addDict("지갑", "WALLET", "WLLT", "");
        addDict("결제", "PAYMENT", "PYMNT", "");

        usageMap.put("사용자", 5);
        usageMap.put("번호", 10);
        usageMap.put("코드", 8);
    }

    // ==================================================================
    // 1. (삭제) greedySplit 테스트 — 110번 7.5-9c: 서버 greedySplit() 은 호출부가 없어 지웠다
    // ==================================================================

    // ==================================================================
    // 2. generateAlternativeSplit 테스트
    // ==================================================================

    @Nested
    @DisplayName("generateAlternativeSplit - 2순위 분리")
    class AlternativeSplitTest {

        @Test
        @DisplayName("등록 단어는 분리하지 않음")
        void registeredWordNotSplit() {
            List<String> primary = Arrays.asList("사용자", "번호");
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용", "자", "번", "호"));
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);
            assertEquals(primary, alt);
        }

        @Test
        @DisplayName("2글자 이하 토큰은 분리하지 않음")
        void shortTokenNotSplit() {
            List<String> primary = Arrays.asList("AB");
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("A", "B"));
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);
            assertEquals(primary, alt);
        }

        @Test
        @DisplayName("미등록 3글자 이상 토큰은 subSplit 수행")
        void unregisteredLongTokenSplit() {
            List<String> primary = Arrays.asList("핸드폰", "번호");
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드", "폰", "번호"));
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);
            assertEquals(Arrays.asList("핸드", "폰", "번호"), alt);
        }

        @Test
        @DisplayName("subSplit 결과가 1개면 원본 유지")
        void subSplitSingleResult() {
            List<String> primary = Arrays.asList("알수없는단어");
            Set<String> candidates = new LinkedHashSet<>(); // 아무 후보도 없음
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);
            assertEquals(primary, alt);
        }

        @Test
        @DisplayName("빈 primary → 빈 결과")
        void emptyPrimary() {
            List<String> alt = generateAlternativeSplit(Collections.emptyList(),
                    new LinkedHashSet<>(), wordsByNm);
            assertTrue(alt.isEmpty());
        }

        @Test
        @DisplayName("복수 미등록 토큰 동시 분리")
        void multipleUnregisteredTokensSplit() {
            addDict("결제", "PAYMENT", "PYMNT", "");
            List<String> primary = Arrays.asList("핸드폰", "결제코드");
            Set<String> candidates = new LinkedHashSet<>(
                    Arrays.asList("핸드", "폰", "결제", "코드", "결제코드"));
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);
            // 핸드폰: 미등록, 3글자 → subSplit → [핸드, 폰]
            // 결제코드: 미등록, 4글자 → subSplit → [결제, 코드]
            assertEquals(Arrays.asList("핸드", "폰", "결제", "코드"), alt);
        }
    }

    // ==================================================================
    // 3. subSplit 테스트
    // ==================================================================

    @Nested
    @DisplayName("subSplit - shortest 재분리")
    class SubSplitTest {

        @Test
        @DisplayName("shortest 2+ 매칭으로 분리")
        void shortestMatch() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드", "핸드폰", "폰"));
            List<String> result = subSplit("핸드폰", candidates);
            // shortest 2+: "핸드" (2) vs "핸드폰" (3, 하지만 c.length() < remaining.length()=3이므로 3 < 3 = false)
            // 따라서 "핸드"만 매칭 가능, 남은 "폰"은 candidates에 있음
            assertEquals(Arrays.asList("핸드", "폰"), result);
        }

        @Test
        @DisplayName("후보가 없으면 잔여 통째 반환")
        void noCandidatesRemaining() {
            Set<String> candidates = new LinkedHashSet<>();
            List<String> result = subSplit("알수없음", candidates);
            assertEquals(Arrays.asList("알수없음"), result);
        }

        @Test
        @DisplayName("빈 토큰")
        void emptyToken() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("AB"));
            List<String> result = subSplit("", candidates);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("remaining 자체가 candidate에 있으면 그대로 사용")
        void remainingIsCandidate() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("ABC", "AB"));
            // "ABC": AB (shortest 2+)는 startsWith=true, length(2) < length(3)=true → 선택
            // 남은 "C": candidates에 없으므로 통째 반환
            List<String> result = subSplit("ABC", candidates);
            assertEquals(Arrays.asList("AB", "C"), result);
        }
    }

    // ==================================================================
    // 4. buildWordAnalyses 테스트
    // ==================================================================

    @Nested
    @DisplayName("buildWordAnalyses - 토큰 → WordAnalysis 변환")
    class BuildWordAnalysesTest {

        @Test
        @DisplayName("등록 단어 → MATCHED 상태, 후보 목록 생성")
        void registeredWordMatched() {
            List<String> tokens = Arrays.asList("사용자");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals(1, result.size());
            TermAnalysisResult.WordAnalysis wa = result.get(0);
            assertEquals("사용자", wa.getWordNm());
            assertEquals("MATCHED", wa.getStatus());
            assertNotNull(wa.getCandidates());
            assertFalse(wa.getCandidates().isEmpty());
            assertNotNull(wa.getSelected());
            assertTrue(wa.getSelected().isSelected());
        }

        @Test
        @DisplayName("DICT 단어 → NEW 상태, 추천값 세팅")
        void dictWordNew() {
            List<String> tokens = Arrays.asList("핸드폰");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals(1, result.size());
            TermAnalysisResult.WordAnalysis wa = result.get(0);
            assertEquals("NEW", wa.getStatus());
            assertNotNull(wa.getNewWord());
            assertEquals("HNDPHN", wa.getNewWord().getWordEngAbrvNm());
            assertEquals("HANDPHONE", wa.getNewWord().getWordEngNm());
        }

        @Test
        @DisplayName("미등록 + DICT에도 없는 단어 → UNRECOGNIZED 상태")
        void unrecognizedWord() {
            List<String> tokens = Arrays.asList("꾸룩");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals(1, result.size());
            TermAnalysisResult.WordAnalysis wa = result.get(0);
            assertEquals("UNRECOGNIZED", wa.getStatus());
            assertNotNull(wa.getNewWord());
            assertEquals("", wa.getNewWord().getWordEngAbrvNm());
        }

        @Test
        @DisplayName("복수 토큰 혼합 (MATCHED + NEW + UNRECOGNIZED)")
        void mixedTokens() {
            List<String> tokens = Arrays.asList("사용자", "핸드폰", "꾸룩");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals(3, result.size());
            assertEquals("MATCHED", result.get(0).getStatus());
            assertEquals("NEW", result.get(1).getStatus());
            assertEquals("UNRECOGNIZED", result.get(2).getStatus());
        }

        @Test
        @DisplayName("빈 토큰 목록 → 빈 결과")
        void emptyTokens() {
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(
                    Collections.emptyList(), wordsByNm, wordDict, usageMap);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("후보 스코어 정렬 - 사용량 높은 것이 먼저")
        void candidateScoreSorting() {
            // 동일 단어명에 복수 후보
            addWord("번호", "NUMBER2", "NUM", "");
            List<String> tokens = Arrays.asList("번호");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            TermAnalysisResult.WordAnalysis wa = result.get(0);
            assertEquals("MATCHED", wa.getStatus());
            assertTrue(wa.getCandidates().size() >= 2);
            // 스코어 내림차순인지 확인
            for (int i = 0; i < wa.getCandidates().size() - 1; i++) {
                assertTrue(wa.getCandidates().get(i).getScore() >= wa.getCandidates().get(i + 1).getScore());
            }
        }

        @Test
        @DisplayName("selected는 최고 스코어 후보에만 true")
        void selectedOnlyBest() {
            addWord("코드", "CODE2", "CD2", "코드");
            List<String> tokens = Arrays.asList("코드");
            List<TermAnalysisResult.WordAnalysis> result = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            TermAnalysisResult.WordAnalysis wa = result.get(0);
            long selectedCount = wa.getCandidates().stream().filter(TermAnalysisResult.WordCandidate::isSelected).count();
            assertEquals(1, selectedCount, "selected는 정확히 1개여야 함");
        }
    }

    // ==================================================================
    // 5. composeEngAbrv 테스트
    // ==================================================================

    @Nested
    @DisplayName("composeEngAbrv - 영문약어 조합")
    class ComposeEngAbrvTest {

        @Test
        @DisplayName("전체 MATCHED → 약어 _ 조합")
        void allMatched() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    matchedWordWithAbrv("사용자", "USR"),
                    matchedWordWithAbrv("이름", "NM")
            );
            assertEquals("USR_NM", composeEngAbrv(words));
        }

        @Test
        @DisplayName("단일 MATCHED")
        void singleMatched() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    matchedWordWithAbrv("주소", "ADDR")
            );
            assertEquals("ADDR", composeEngAbrv(words));
        }

        @Test
        @DisplayName("빈 목록 → 빈 문자열")
        void emptyList() {
            assertEquals("", composeEngAbrv(Collections.emptyList()));
        }

        @Test
        @DisplayName("MATCHED + NEW(추천값 있음)")
        void matchedAndNewWithSuggestion() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    newWordWithSuggestion("핸드폰", "HNDPHN"),
                    matchedWordWithAbrv("번호", "NO")
            );
            assertEquals("HNDPHN_NO", composeEngAbrv(words));
        }

        @Test
        @DisplayName("NEW(추천값 빈값) → 빈 약어 부분은 무시, 구분자 없이 다음 토큰부터")
        void newWithEmptySuggestion() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    newWordWithSuggestion("꾸룩", ""),
                    matchedWordWithAbrv("번호", "NO")
            );
            // 첫 토큰이 빈 약어 → sb에 "" append, sb.length()=0 유지
            // 두번째 토큰에서 sb.length()==0이므로 "_" 없이 바로 "NO"
            assertEquals("NO", composeEngAbrv(words));
        }

        @Test
        @DisplayName("3개 MATCHED 조합")
        void threeMatched() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    matchedWordWithAbrv("운전", "DRVNG"),
                    matchedWordWithAbrv("면허", "LCNS"),
                    matchedWordWithAbrv("번호", "NO")
            );
            assertEquals("DRVNG_LCNS_NO", composeEngAbrv(words));
        }

        @Test
        @DisplayName("newWord가 null인 경우 빈값 처리")
        void newWordNull() {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("미지");
            wa.setStatus("NEW");
            wa.setNewWord(null);
            wa.setSelected(null);

            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(wa);
            // newWord가 null이면 아무것도 append 안 함
            assertEquals("", composeEngAbrv(words));
        }
    }

    // ==================================================================
    // 6. judgeStatus 테스트
    // ==================================================================

    @Nested
    @DisplayName("judgeStatus - 상태 판정")
    class JudgeStatusTest {

        @Test
        @DisplayName("전체 MATCHED → AUTO")
        void allMatched() {
            List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
                    matchedWordWithAbrv("사용자", "USR"),
                    matchedWordWithAbrv("이름", "NM")
            );
            assertEquals("AUTO", judgeStatus(words));
        }

        @Test
        @DisplayName("MATCHED + NEW → PARTIAL")
        void matchedAndNew() {
            TermAnalysisResult.WordAnalysis wa1 = matchedWordWithAbrv("번호", "NO");
            TermAnalysisResult.WordAnalysis wa2 = new TermAnalysisResult.WordAnalysis();
            wa2.setWordNm("핸드폰");
            wa2.setStatus("NEW");

            assertEquals("PARTIAL", judgeStatus(Arrays.asList(wa1, wa2)));
        }

        @Test
        @DisplayName("전체 NEW → FAILED")
        void allNew() {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("핸드폰");
            wa.setStatus("NEW");
            assertEquals("FAILED", judgeStatus(Arrays.asList(wa)));
        }

        @Test
        @DisplayName("전체 UNRECOGNIZED → FAILED")
        void allUnrecognized() {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm("꾸룩");
            wa.setStatus("UNRECOGNIZED");
            assertEquals("FAILED", judgeStatus(Arrays.asList(wa)));
        }

        @Test
        @DisplayName("MATCHED + UNRECOGNIZED → PARTIAL")
        void matchedAndUnrecognized() {
            TermAnalysisResult.WordAnalysis wa1 = matchedWordWithAbrv("번호", "NO");
            TermAnalysisResult.WordAnalysis wa2 = new TermAnalysisResult.WordAnalysis();
            wa2.setWordNm("꾸룩");
            wa2.setStatus("UNRECOGNIZED");
            assertEquals("PARTIAL", judgeStatus(Arrays.asList(wa1, wa2)));
        }

        @Test
        @DisplayName("빈 목록 → FAILED")
        void emptyList() {
            assertEquals("FAILED", judgeStatus(Collections.emptyList()));
        }

        @Test
        @DisplayName("단일 MATCHED → AUTO")
        void singleMatched() {
            assertEquals("AUTO", judgeStatus(Arrays.asList(matchedWordWithAbrv("주소", "ADDR"))));
        }

        @Test
        @DisplayName("NEW + UNRECOGNIZED (MATCHED 없음) → FAILED")
        void newAndUnrecognizedNoMatched() {
            TermAnalysisResult.WordAnalysis wa1 = new TermAnalysisResult.WordAnalysis();
            wa1.setWordNm("핸드폰");
            wa1.setStatus("NEW");
            TermAnalysisResult.WordAnalysis wa2 = new TermAnalysisResult.WordAnalysis();
            wa2.setWordNm("꾸룩");
            wa2.setStatus("UNRECOGNIZED");
            assertEquals("FAILED", judgeStatus(Arrays.asList(wa1, wa2)));
        }
    }

    // ==================================================================
    // 7. 통합 시나리오 테스트
    // ==================================================================

    @Nested
    @DisplayName("통합 시나리오")
    class IntegrationScenarios {

        // 110번 7.5-9c — greedySplit 사본을 지워 분리 결과(토큰)를 직접 준다
        @Test
        @DisplayName("buildWordAnalyses → judgeStatus 파이프라인 - 전체 MATCHED")
        void fullPipelineAllMatched() {
            List<String> tokens = Arrays.asList("사용자", "이름");
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);
            String status = judgeStatus(analyses);
            String engAbrv = composeEngAbrv(analyses);

            assertEquals("AUTO", status);
            assertEquals("USR_NM", engAbrv);
        }

        @Test
        @DisplayName("buildWordAnalyses → judgeStatus 파이프라인 - PARTIAL")
        void fullPipelinePartial() {
            List<String> tokens = Arrays.asList("핸드폰", "번호");
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);
            String status = judgeStatus(analyses);

            assertEquals("PARTIAL", status);
        }

        @Test
        @DisplayName("1순위 → 2순위 대체 분리")
        void primaryToAlternative() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드폰", "핸드", "폰", "번호"));
            List<String> primary = Arrays.asList("핸드폰", "번호");
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);

            // 핸드폰(DICT에는 있지만 TB_WORD에 없음)은 미등록 → 추가 분리
            // "핸드폰" → subSplit → [핸드, 폰]
            // "번호"는 TB_WORD에 등록 → 유지
            assertEquals(Arrays.asList("핸드", "폰", "번호"), alt);
        }

        @Test
        @DisplayName("코드금액일자 → 3단어 분리, AUTO")
        void threeWordAutoScenario() {
            List<String> tokens = Arrays.asList("코드", "금액", "일자");
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals("AUTO", judgeStatus(analyses));
            assertEquals("CD_AMT_DT", composeEngAbrv(analyses));
        }
    }

    // ==================================================================
    // 8. registerWord 입력값 검증 로직 테스트
    // ==================================================================

    @Nested
    @DisplayName("registerWord 입력값 검증")
    class RegisterWordValidationTest {

        @Test
        @DisplayName("wordNm null → 실패")
        void wordNmNull() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", null);
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", "TEST");
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
            assertNotNull(res.get("message"));
        }

        @Test
        @DisplayName("wordNm 빈값 → 실패")
        void wordNmEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "  ");
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", "TEST");
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("wordEngAbrvNm null → 실패")
        void wordEngAbrvNmNull() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", null);
            body.put("wordEngNm", "TEST");
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("wordEngAbrvNm 빈값 → 실패")
        void wordEngAbrvNmEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", "");
            body.put("wordEngNm", "TEST");
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("wordEngNm null → 실패")
        void wordEngNmNull() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", null);
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("wordEngNm 빈값 → 실패")
        void wordEngNmEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", "   ");
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("필수 3필드 모두 null → 실패")
        void allNull() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", null);
            body.put("wordEngAbrvNm", null);
            body.put("wordEngNm", null);
            Map<String, Object> res = validateRegisterWord(body);
            assertFalse((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("필수값 모두 정상 → 성공")
        void allValid() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", "TEST");
            Map<String, Object> res = validateRegisterWord(body);
            assertTrue((Boolean) res.get("success"));
        }

        @Test
        @DisplayName("wordDesc/domainClsfNm은 선택 → null이어도 검증 통과")
        void optionalFieldsNull() {
            Map<String, Object> body = new HashMap<>();
            body.put("wordNm", "테스트");
            body.put("wordEngAbrvNm", "TST");
            body.put("wordEngNm", "TEST");
            body.put("wordDesc", null);
            body.put("domainClsfNm", null);
            Map<String, Object> res = validateRegisterWord(body);
            assertTrue((Boolean) res.get("success"));
        }
    }

    // ==================================================================
    // 9. registerTermsBatch 빈 items 처리 테스트
    //    (110번 7.5-6 — 신규 단어 함께 등록(newWords) 경로를 뺐으므로 그 검증 사본 테스트는 지웠다)
    // ==================================================================

    @Nested
    @DisplayName("registerTermsBatch 빈 items 처리")
    class RegisterTermsBatchEmptyItemsTest {

        @Test
        @DisplayName("items가 null이거나 빈 리스트 → 빈 결과 반환")
        void emptyItemsResult() {
            Map<String, Object> res = simulateBatchWithEmptyItems(null);
            assertEquals(0, res.get("success"));
            assertEquals(0, res.get("fail"));

            Map<String, Object> res2 = simulateBatchWithEmptyItems(new ArrayList<>());
            assertEquals(0, res2.get("success"));
            assertEquals(0, res2.get("fail"));
        }
    }

    // ==================================================================
    // 로직 재현 (컨트롤러와 동일한 순수 로직)
    // ==================================================================

    /**
     * registerWord 입력값 검증 로직 재현 (DB 비의존 부분)
     */
    private Map<String, Object> validateRegisterWord(Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        String wordNm = (String) body.get("wordNm");
        String wordEngAbrvNm = (String) body.get("wordEngAbrvNm");
        String wordEngNm = (String) body.get("wordEngNm");

        if (wordNm == null || wordNm.trim().isEmpty()
                || wordEngAbrvNm == null || wordEngAbrvNm.trim().isEmpty()
                || wordEngNm == null || wordEngNm.trim().isEmpty()) {
            res.put("success", false);
            res.put("message", "한글명, 영문약어, 영문명은 필수입니다.");
            return res;
        }
        res.put("success", true);
        return res;
    }

    /**
     * registerTermsBatch의 빈 items 처리 로직 재현
     */
    private Map<String, Object> simulateBatchWithEmptyItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            Map<String, Object> res = new HashMap<>();
            res.put("success", 0);
            res.put("fail", 0);
            res.put("details", new ArrayList<>());
            return res;
        }
        return new HashMap<>(); // 여기선 도달하지 않음
    }

    private List<String> generateAlternativeSplit(List<String> primarySplit, Set<String> candidates, Map<String, List<StdWordVo>> wordsByNm) {
        List<String> alt = new ArrayList<>();
        for (String token : primarySplit) {
            if (wordsByNm.containsKey(token) || token.length() < 3) {
                alt.add(token);
                continue;
            }
            List<String> sub = subSplit(token, candidates);
            if (sub.size() > 1) alt.addAll(sub); else alt.add(token);
        }
        return alt;
    }

    private List<String> subSplit(String token, Set<String> candidates) {
        List<String> result = new ArrayList<>();
        String remaining = token;
        int maxIter = 50;
        while (!remaining.isEmpty() && maxIter-- > 0) {
            String best = null;
            for (String c : candidates) {
                if (c.length() >= 2 && remaining.startsWith(c) && c.length() < remaining.length()) {
                    if (best == null || c.length() < best.length()) best = c;
                }
            }
            if (best != null) { result.add(best); remaining = remaining.substring(best.length()); continue; }
            if (candidates.contains(remaining)) { result.add(remaining); remaining = ""; continue; }
            result.add(remaining); remaining = "";
        }
        return result;
    }

    private List<TermAnalysisResult.WordAnalysis> buildWordAnalyses(List<String> tokens,
            Map<String, List<StdWordVo>> wordsByNm, Map<String, Map<String, Object>> wordDict,
            Map<String, Integer> usageMap) {
        List<TermAnalysisResult.WordAnalysis> list = new ArrayList<>();
        for (String token : tokens) {
            TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
            wa.setWordNm(token);

            List<StdWordVo> matches = wordsByNm.get(token);
            if (matches != null && !matches.isEmpty()) {
                wa.setStatus("MATCHED");
                List<TermAnalysisResult.WordCandidate> cands = new ArrayList<>();
                TermAnalysisResult.WordCandidate best = null;
                int bestScore = -1;
                for (StdWordVo w : matches) {
                    TermAnalysisResult.WordCandidate c = new TermAnalysisResult.WordCandidate();
                    c.setWordId(w.getId());
                    c.setWordNm(w.getWordNm());
                    c.setWordEngAbrvNm(w.getWordEngAbrvNm());
                    c.setWordEngNm(w.getWordEngNm());
                    c.setDomainClsfNm(w.getDomainClsfNm());
                    int score = 0;
                    Integer usage = usageMap.get(w.getWordNm());
                    if (usage != null) score += usage * 100;
                    if (w.getWordEngAbrvNm() != null) score += Math.min(w.getWordEngAbrvNm().length(), 10) * 10;
                    c.setScore(score);
                    if (score > bestScore) { bestScore = score; best = c; }
                    cands.add(c);
                }
                cands.sort((a, b) -> b.getScore() - a.getScore());
                if (best != null) best.setSelected(true);
                wa.setCandidates(cands);
                wa.setSelected(best);
            } else {
                Map<String, Object> dictEntry = wordDict.get(token);
                if (dictEntry != null) {
                    wa.setStatus("NEW");
                    wa.setCandidates(new ArrayList<>());
                    TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
                    suggestion.setWordEngAbrvNm(dictEntry.get("wordAbrv") != null ? (String) dictEntry.get("wordAbrv") : "");
                    suggestion.setWordEngNm(dictEntry.get("wordEng") != null ? (String) dictEntry.get("wordEng") : "");
                    suggestion.setDomainClsfNm(dictEntry.get("domainClsfNm") != null ? (String) dictEntry.get("domainClsfNm") : "");
                    wa.setNewWord(suggestion);
                } else {
                    wa.setStatus("UNRECOGNIZED");
                    wa.setCandidates(new ArrayList<>());
                    TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
                    suggestion.setWordEngAbrvNm("");
                    suggestion.setWordEngNm("");
                    suggestion.setDomainClsfNm("");
                    wa.setNewWord(suggestion);
                }
            }
            list.add(wa);
        }
        return list;
    }

    private String composeEngAbrv(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
        StringBuilder sb = new StringBuilder();
        for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
            if (sb.length() > 0) sb.append("_");
            if ("MATCHED".equals(wa.getStatus()) && wa.getSelected() != null) {
                sb.append(wa.getSelected().getWordEngAbrvNm());
            } else if (wa.getNewWord() != null) {
                sb.append(wa.getNewWord().getWordEngAbrvNm());
            }
        }
        return sb.toString();
    }

    private String judgeStatus(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
        boolean hasMatched = false, hasNew = false;
        for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
            if ("MATCHED".equals(wa.getStatus())) hasMatched = true;
            else hasNew = true;
        }
        if (wordAnalyses.isEmpty() || !hasMatched) return "FAILED";
        if (hasNew) return "PARTIAL";
        return "AUTO";
    }

    // ==================================================================
    // 헬퍼: 데이터 생성
    // ==================================================================

    private void addWord(String nm, String eng, String abrv, String domainClsf) {
        StdWordVo w = new StdWordVo();
        w.setId("word-" + nm);
        w.setWordNm(nm);
        w.setWordEngNm(eng);
        w.setWordEngAbrvNm(abrv);
        w.setDomainClsfNm(domainClsf);
        wordsByNm.computeIfAbsent(nm, k -> new ArrayList<>()).add(w);
    }

    private void addDict(String kor, String eng, String abrv, String domainClsf) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("wordEng", eng);
        entry.put("wordAbrv", abrv);
        entry.put("domainClsfNm", domainClsf);
        wordDict.put(kor, entry);
    }

    private TermAnalysisResult.WordAnalysis matchedWordWithAbrv(String wordNm, String abrv) {
        TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
        wa.setWordNm(wordNm);
        wa.setStatus("MATCHED");
        TermAnalysisResult.WordCandidate c = new TermAnalysisResult.WordCandidate();
        c.setWordNm(wordNm);
        c.setWordEngAbrvNm(abrv);
        c.setSelected(true);
        wa.setSelected(c);
        wa.setCandidates(Arrays.asList(c));
        return wa;
    }

    private TermAnalysisResult.WordAnalysis newWordWithSuggestion(String wordNm, String abrv) {
        TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
        wa.setWordNm(wordNm);
        wa.setStatus("NEW");
        wa.setCandidates(new ArrayList<>());
        TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
        suggestion.setWordEngAbrvNm(abrv);
        wa.setNewWord(suggestion);
        return wa;
    }
}
