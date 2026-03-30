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
 * 대상: greedySplit, generateAlternativeSplit, subSplit,
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
    // 1. greedySplit 테스트
    // ==================================================================

    @Nested
    @DisplayName("greedySplit - 1순위 분리")
    class GreedySplitTest {

        @Test
        @DisplayName("전체 TB_WORD 등록 단어로만 구성된 용어")
        void allRegisteredWords() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용자", "전화", "번호"));
            List<String> result = greedySplit("사용자전화번호", candidates, wordsByNm, wordDict);
            assertEquals(Arrays.asList("사용자", "전화", "번호"), result);
        }

        @Test
        @DisplayName("TB_WORD > DICT 우선순위 적용")
        void wordOverDict() {
            // "번호"는 TB_WORD에 있고 DICT에도 있을 경우 TB_WORD 우선
            addDict("번호", "NUMBER", "NO", "");
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용자", "번호"));
            List<String> result = greedySplit("사용자번호", candidates, wordsByNm, wordDict);
            assertEquals(Arrays.asList("사용자", "번호"), result);
        }

        @Test
        @DisplayName("DICT 단어로 분리")
        void dictWordsSplit() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드폰", "번호", "핸드", "폰"));
            List<String> result = greedySplit("핸드폰번호", candidates, wordsByNm, wordDict);
            // 핸드폰은 DICT에 있고, 번호는 TB_WORD에 있음 - TB_WORD가 먼저 체크되지만 번호 먼저 매칭은 안 됨
            // 사실 "번호"가 TB_WORD에 있으므로 먼저 매칭을 시도하나, "핸드폰번호"에서 "번호"는 startsWith가 아님
            // 따라서 핸드폰(DICT longest)가 매칭
            assertEquals("핸드폰", result.get(0));
            assertEquals("번호", result.get(1));
        }

        @Test
        @DisplayName("빈 입력")
        void emptyInput() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용자"));
            List<String> result = greedySplit("", candidates, wordsByNm, wordDict);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("매칭 불가능한 문자가 포함된 경우 gap 처리")
        void gapHandling() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용자", "번호"));
            // "사용자XY번호" → "사용자", "X", "Y", 또는 "XY", "번호"
            List<String> result = greedySplit("사용자XY번호", candidates, wordsByNm, wordDict);
            assertEquals("사용자", result.get(0));
            // gap은 한 글자씩 또는 매칭점까지 스킵
            assertTrue(result.contains("번호"), "번호가 결과에 포함되어야 함");
        }

        @Test
        @DisplayName("단일 단어 (TB_WORD에 등록)")
        void singleRegisteredWord() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("주소"));
            List<String> result = greedySplit("주소", candidates, wordsByNm, wordDict);
            // input == "주소"이고 candidate "주소"도 있지만, !c.equals(input) 조건으로 건너뜀
            // candidates.contains(remaining) && !remaining.equals(input) 도 false
            // 결국 gap 처리로 "주소" 통째로 남음
            assertEquals(1, result.size());
            assertEquals("주소", result.get(0));
        }

        @Test
        @DisplayName("longest match 우선 - 운전면허 vs 운전+면허")
        void longestMatchPriority() {
            addWord("운전면허", "DRIVER_LICENSE", "DRVNG_LCNS", "");
            Set<String> candidates = new LinkedHashSet<>(
                    Arrays.asList("운전면허번호", "운전면허", "운전", "면허", "번호", "면허번호"));
            List<String> result = greedySplit("운전면허번호", candidates, wordsByNm, wordDict);
            assertEquals(Arrays.asList("운전면허", "번호"), result);
        }

        @Test
        @DisplayName("OKT 2글자 이상 토큰으로 매칭")
        void oktLongestMatch() {
            // TB_WORD/DICT에 없고 candidate에만 있는 토큰
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("미등록단어", "미등록", "단어"));
            List<String> result = greedySplit("미등록단어", candidates, wordsByNm, wordDict);
            // "미등록" (2+) startsWith, length < input → 선택
            assertEquals("미등록", result.get(0));
            assertEquals("단어", result.get(1));
        }

        @Test
        @DisplayName("전체 매칭 불가 → 잔여 통째 반환")
        void noMatchAtAll() {
            Set<String> candidates = new LinkedHashSet<>();
            List<String> result = greedySplit("없는단어", candidates, wordsByNm, wordDict);
            assertEquals(1, result.size());
            assertEquals("없는단어", result.get(0));
        }
    }

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

        @Test
        @DisplayName("greedySplit → buildWordAnalyses → judgeStatus 파이프라인 - 전체 MATCHED")
        void fullPipelineAllMatched() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("사용자", "이름"));
            List<String> tokens = greedySplit("사용자이름", candidates, wordsByNm, wordDict);
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);
            String status = judgeStatus(analyses);
            String engAbrv = composeEngAbrv(analyses);

            assertEquals("AUTO", status);
            assertEquals("USR_NM", engAbrv);
        }

        @Test
        @DisplayName("greedySplit → buildWordAnalyses → judgeStatus 파이프라인 - PARTIAL")
        void fullPipelinePartial() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드폰", "번호"));
            List<String> tokens = greedySplit("핸드폰번호", candidates, wordsByNm, wordDict);
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);
            String status = judgeStatus(analyses);

            assertEquals("PARTIAL", status);
        }

        @Test
        @DisplayName("1순위 → 2순위 대체 분리")
        void primaryToAlternative() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("핸드폰", "핸드", "폰", "번호"));
            List<String> primary = greedySplit("핸드폰번호", candidates, wordsByNm, wordDict);
            List<String> alt = generateAlternativeSplit(primary, candidates, wordsByNm);

            // 핸드폰(DICT에는 있지만 TB_WORD에 없음)은 미등록 → 추가 분리
            // "핸드폰" → subSplit → [핸드, 폰]
            // "번호"는 TB_WORD에 등록 → 유지
            assertEquals(Arrays.asList("핸드", "폰", "번호"), alt);
        }

        @Test
        @DisplayName("코드금액일자 → 3단어 분리, AUTO")
        void threeWordAutoScenario() {
            Set<String> candidates = new LinkedHashSet<>(Arrays.asList("코드", "금액", "일자"));
            List<String> tokens = greedySplit("코드금액일자", candidates, wordsByNm, wordDict);
            List<TermAnalysisResult.WordAnalysis> analyses = buildWordAnalyses(tokens, wordsByNm, wordDict, usageMap);

            assertEquals("AUTO", judgeStatus(analyses));
            assertEquals("CD_AMT_DT", composeEngAbrv(analyses));
        }
    }

    // ==================================================================
    // 로직 재현 (컨트롤러와 동일한 순수 로직)
    // ==================================================================

    private List<String> greedySplit(String input, Set<String> candidates,
            Map<String, List<StdWordVo>> wordsByNm, Map<String, Map<String, Object>> wordDict) {
        List<String> result = new ArrayList<>();
        Set<String> dictKeys = wordDict.keySet();
        String remaining = input;
        int maxIter = 100;

        while (!remaining.isEmpty() && maxIter-- > 0) {
            String selected = null;

            // 1. TB_WORD longest match
            for (String c : candidates) {
                if (wordsByNm.containsKey(c) && remaining.startsWith(c) && !c.equals(input)) {
                    if (selected == null || c.length() > selected.length()) selected = c;
                }
            }
            if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

            // 2. DICT longest match
            for (String c : candidates) {
                if (dictKeys.contains(c) && remaining.startsWith(c) && !c.equals(input)) {
                    if (selected == null || c.length() > selected.length()) selected = c;
                }
            }
            if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

            // 3. remaining == OKT token
            if (candidates.contains(remaining) && !remaining.equals(input)) {
                result.add(remaining); remaining = ""; continue;
            }

            // 4. OKT longest 2+
            for (String c : candidates) {
                if (c.length() >= 2 && remaining.startsWith(c) && c.length() < remaining.length() && !c.equals(input)) {
                    if (selected == null || c.length() > selected.length()) selected = c;
                }
            }
            if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

            // 5. gap
            boolean skipped = false;
            for (int skip = 1; skip < remaining.length(); skip++) {
                String sub = remaining.substring(skip);
                for (String c : candidates) {
                    if (sub.startsWith(c) && !c.equals(input)) { skipped = true; break; }
                }
                if (skipped) { result.add(remaining.substring(0, skip)); remaining = remaining.substring(skip); break; }
            }
            if (!skipped) { result.add(remaining); remaining = ""; }
        }
        return result;
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
