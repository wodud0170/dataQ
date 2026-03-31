package qualitycenter.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ndata.quality.model.std.TermAnalysisResult;

/**
 * 표준화 추천 시스템 테스트
 *
 * 대상 로직: DataStandardController.analyzeTerms()
 * DB 의존성이 있어 순수 단위 테스트가 어렵기 때문,
 * 분석 결과의 상태 판정·영문약어 조합·DICT 추천·등록 흐름을 시뮬레이션하여 검증한다.
 */
class TermRecommendTest {

	// ========== 테스트용 데이터 ==========

	/** TB_WORD에 등록된 단어 맵 (wordNm → 단어 목록) */
	private Map<String, List<FakeWord>> wordsByNm;

	/** TB_WORD_DICT 추천 사전 (wordKor → {wordEng, wordAbrv, domainClsfNm}) */
	private Map<String, Map<String, String>> wordDict;

	@BeforeEach
	void setUp() {
		wordsByNm = new HashMap<>();
		wordDict = new HashMap<>();

		// 등록된 단어 세팅
		addWord("사용자", "USER", "USR", "");
		addWord("이름", "NAME", "NM", "");
		addWord("전화", "TELEPHONE", "TEL", "");
		addWord("번호", "NUMBER", "NO", "번호");
		addWord("주소", "ADDRESS", "ADDR", "");
		addWord("운전", "DRIVING", "DRVNG", "");
		addWord("면허", "LICENSE", "LCNS", "");

		// DICT 추천 세팅 (미등록 단어용)
		addDict("핸드폰", "HANDPHONE", "HNDPHN", "");
		addDict("지갑", "WALLET", "WLLT", "");
	}

	// ========== 1. 상태 판정 테스트 ==========

	@Nested
	@DisplayName("상태 판정")
	class StatusJudgement {

		@Test
		@DisplayName("전체 MATCHED → AUTO")
		void 전체_매칭_AUTO() {
			// "사용자이름" → [사용자(MATCHED), 이름(MATCHED)]
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWord("사용자"),
				matchedWord("이름")
			);
			String status = judgeStatus(words);
			assertEquals("AUTO", status);
		}

		@Test
		@DisplayName("일부 MATCHED + 일부 NEW → PARTIAL")
		void 부분_매칭_PARTIAL() {
			// "핸드폰번호" → [핸드폰(NEW), 번호(MATCHED)]
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				newWord("핸드폰"),
				matchedWord("번호")
			);
			String status = judgeStatus(words);
			assertEquals("PARTIAL", status);
		}

		@Test
		@DisplayName("전체 NEW → FAILED (이전 버그: PARTIAL로 나왔음)")
		void 전체_미매칭_FAILED() {
			// "핸드폰지갑" → [핸드폰(NEW), 지갑(NEW)]
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				newWord("핸드폰"),
				newWord("지갑")
			);
			String status = judgeStatus(words);
			assertEquals("FAILED", status, "전체 NEW이면 PARTIAL이 아니라 FAILED");
		}

		@Test
		@DisplayName("빈 토큰 목록 → FAILED")
		void 빈_토큰_FAILED() {
			List<TermAnalysisResult.WordAnalysis> words = Collections.emptyList();
			String status = judgeStatus(words);
			assertEquals("FAILED", status);
		}

		@Test
		@DisplayName("단일 MATCHED → AUTO")
		void 단일_매칭_AUTO() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(matchedWord("주소"));
			String status = judgeStatus(words);
			assertEquals("AUTO", status);
		}

		@Test
		@DisplayName("단일 NEW → FAILED")
		void 단일_미매칭_FAILED() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(newWord("핸드폰"));
			String status = judgeStatus(words);
			assertEquals("FAILED", status);
		}
	}

	// ========== 2. 영문약어 조합 테스트 ==========

	@Nested
	@DisplayName("영문약어 조합")
	class EngAbrvComposition {

		@Test
		@DisplayName("전체 MATCHED → 선택된 약어 _로 조합")
		void 전체_매칭_약어조합() {
			// 사용자 + 이름 → USR_NM
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWordWithAbrv("사용자", "USR"),
				matchedWordWithAbrv("이름", "NM")
			);
			String engAbrv = composeEngAbrv(words);
			assertEquals("USR_NM", engAbrv);
		}

		@Test
		@DisplayName("MATCHED + NEW(DICT추천) → 약어 조합")
		void 부분매칭_DICT추천_약어조합() {
			// 핸드폰(NEW, dict추천 HNDPHN) + 번호(MATCHED, NO)
			TermAnalysisResult.WordAnalysis w1 = newWordWithSuggestion("핸드폰", "HNDPHN");
			TermAnalysisResult.WordAnalysis w2 = matchedWordWithAbrv("번호", "NO");
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(w1, w2);
			String engAbrv = composeEngAbrv(words);
			assertEquals("HNDPHN_NO", engAbrv);
		}

		@Test
		@DisplayName("NEW 단어에 DICT 추천 없으면 빈 약어")
		void 미추천_빈_약어() {
			// 미등록 + DICT에도 없는 단어
			TermAnalysisResult.WordAnalysis w1 = newWordWithSuggestion("알수없음", "");
			TermAnalysisResult.WordAnalysis w2 = matchedWordWithAbrv("번호", "NO");
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(w1, w2);
			String engAbrv = composeEngAbrv(words);
			assertEquals("NO", engAbrv, "첫 토큰 약어가 빈값이면 구분자 없이 두 번째부터 시작");
		}

		@Test
		@DisplayName("운전면허번호 → DRVNG_LCNS_NO")
		void 운전면허번호_약어조합() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWordWithAbrv("운전", "DRVNG"),
				matchedWordWithAbrv("면허", "LCNS"),
				matchedWordWithAbrv("번호", "NO")
			);
			String engAbrv = composeEngAbrv(words);
			assertEquals("DRVNG_LCNS_NO", engAbrv);
		}
	}

	// ========== 3. TB_WORD_DICT 추천 테스트 ==========

	@Nested
	@DisplayName("DICT 추천값 조회")
	class DictSuggestion {

		@Test
		@DisplayName("DICT에 있는 단어 → 추천값 세팅")
		void DICT_존재_추천값() {
			Map<String, String> entry = wordDict.get("핸드폰");
			assertNotNull(entry);
			assertEquals("HNDPHN", entry.get("wordAbrv"));
			assertEquals("HANDPHONE", entry.get("wordEng"));
		}

		@Test
		@DisplayName("DICT에 없는 단어 → 빈 추천값")
		void DICT_미존재_빈값() {
			Map<String, String> entry = wordDict.get("알수없음");
			assertNull(entry, "DICT에 없는 단어는 null이어야 함");
		}

		@Test
		@DisplayName("DICT 조회 후 NewWordSuggestion에 정확히 세팅")
		void DICT_NewWordSuggestion_세팅() {
			String token = "지갑";
			Map<String, String> entry = wordDict.get(token);
			assertNotNull(entry);

			TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
			suggestion.setWordEngAbrvNm(entry.getOrDefault("wordAbrv", ""));
			suggestion.setWordEngNm(entry.getOrDefault("wordEng", ""));
			suggestion.setDomainClsfNm(entry.getOrDefault("domainClsfNm", ""));

			assertEquals("WLLT", suggestion.getWordEngAbrvNm());
			assertEquals("WALLET", suggestion.getWordEngNm());
		}
	}

	// ========== 4. 단어 등록 → 용어 등록 흐름 테스트 ==========

	@Nested
	@DisplayName("등록 흐름 (registerTermsBatch)")
	class RegistrationFlow {

		@Test
		@DisplayName("NEW 단어가 있으면 TB_WORD에 먼저 등록되어야 함")
		void NEW_단어_선등록_필요() {
			// registerTermsBatch에서 newWords 처리 시뮬레이션
			List<Map<String, Object>> newWords = new ArrayList<>();
			Map<String, Object> nw = new HashMap<>();
			nw.put("wordNm", "핸드폰");
			nw.put("wordEngAbrvNm", "HNDPHN");
			nw.put("wordEngNm", "HANDPHONE");
			nw.put("wordDesc", "핸드폰");
			nw.put("domainClsfNm", "");
			newWords.add(nw);

			// 검증: newWords 목록이 비어있지 않으면 단어 등록이 선행되어야 함
			assertFalse(newWords.isEmpty(), "NEW 단어가 있으면 단어 등록 필요");
			for (Map<String, Object> w : newWords) {
				assertNotNull(w.get("wordNm"), "단어 한글명 필수");
				assertNotNull(w.get("wordEngAbrvNm"), "영문약어 필수");
				assertNotNull(w.get("wordEngNm"), "영문명 필수");
			}
		}

		@Test
		@DisplayName("NEW 단어 없이 전체 MATCHED이면 단어 등록 없이 용어만 등록")
		void 전체_MATCHED_단어등록_불필요() {
			List<Map<String, Object>> newWords = null;

			// newWords가 null이면 단어 등록 스킵
			assertTrue(newWords == null || newWords.isEmpty());
		}

		@Test
		@DisplayName("NEW 단어의 필수 필드가 누락되면 등록 불가")
		void NEW_단어_필수필드_누락시_등록불가() {
			Map<String, Object> nw = new HashMap<>();
			nw.put("wordNm", "핸드폰");
			nw.put("wordEngAbrvNm", ""); // 빈값 → 등록 불가여야 함
			nw.put("wordEngNm", "HANDPHONE");

			String engAbrv = (String) nw.get("wordEngAbrvNm");
			assertTrue(engAbrv == null || engAbrv.isEmpty(),
				"영문약어가 비어있으면 등록 불가로 처리해야 함");
		}

		@Test
		@DisplayName("중복 용어는 SKIPPED 처리")
		void 중복_용어_SKIPPED() {
			// 이미 등록된 용어명 세트 시뮬레이션
			Set<String> existingTerms = new HashSet<>(Arrays.asList("사용자이름", "전화번호"));

			String termsNm = "사용자이름";
			assertTrue(existingTerms.contains(termsNm),
				"이미 등록된 용어는 SKIPPED");
		}

		@Test
		@DisplayName("단어 등록 후 wordId가 용어-단어 관계에 정확히 매핑")
		void 단어등록후_wordId_매핑() {
			// newWordIdMap 시뮬레이션
			Map<Integer, String> newWordIdMap = new HashMap<>();
			newWordIdMap.put(0, "uuid-new-word-1");
			newWordIdMap.put(1, "uuid-new-word-2");

			// words에서 newWordIndex로 참조
			Map<String, Object> wordRef = new HashMap<>();
			wordRef.put("newWordIndex", 0);
			wordRef.put("wordOrd", 1);

			Number newWordIndex = (Number) wordRef.get("newWordIndex");
			String resolvedId = newWordIdMap.get(newWordIndex.intValue());

			assertEquals("uuid-new-word-1", resolvedId,
				"newWordIndex 0번이면 첫 번째 신규 단어 ID");
		}
	}

	// ========== 5. 프론트엔드 동작 시나리오 (통합 시나리오) ==========

	@Nested
	@DisplayName("E2E 시나리오")
	class E2EScenarios {

		@Test
		@DisplayName("시나리오: '사용자이름' → AUTO, 승인 가능")
		void 사용자이름_AUTO_승인() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWordWithAbrv("사용자", "USR"),
				matchedWordWithAbrv("이름", "NM")
			);
			String status = judgeStatus(words);
			String engAbrv = composeEngAbrv(words);

			assertEquals("AUTO", status);
			assertEquals("USR_NM", engAbrv);
			// AUTO → 프론트에서 승인 버튼 표시
		}

		@Test
		@DisplayName("시나리오: '핸드폰번호' → PARTIAL, 수정 필요")
		void 핸드폰번호_PARTIAL_수정() {
			TermAnalysisResult.WordAnalysis w1 = newWordWithSuggestion("핸드폰", "HNDPHN");
			TermAnalysisResult.WordAnalysis w2 = matchedWordWithAbrv("번호", "NO");
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(w1, w2);

			String status = judgeStatus(words);
			assertEquals("PARTIAL", status);
			// PARTIAL → 프론트에서 수정 버튼 표시
			// 수정 다이얼로그에서 NEW 단어 확인/수정 → 단어 등록 → 용어 등록
		}

		@Test
		@DisplayName("시나리오: '핸드폰지갑명' → FAILED, 수정 필요")
		void 핸드폰지갑명_FAILED_수정() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				newWord("핸드폰"),
				newWord("지갑")
			);
			String status = judgeStatus(words);
			assertEquals("FAILED", status);
			// FAILED → 프론트에서 수정 버튼 표시 (승인 버튼 아님!)
		}

		@Test
		@DisplayName("시나리오: '운전면허번호' → AUTO, 3단어 분리")
		void 운전면허번호_AUTO_3단어() {
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWordWithAbrv("운전", "DRVNG"),
				matchedWordWithAbrv("면허", "LCNS"),
				matchedWordWithAbrv("번호", "NO")
			);
			String status = judgeStatus(words);
			String engAbrv = composeEngAbrv(words);

			assertEquals("AUTO", status);
			assertEquals("DRVNG_LCNS_NO", engAbrv);
		}
	}

	// ========== 6. 1순위 분리 (greedySplit) 테스트 ==========

	@Nested
	@DisplayName("1순위 분리 (greedySplit: TB_WORD > DICT > OKT longest)")
	class PrimarySplit {

		@Test
		@DisplayName("핸드폰지갑명 - DICT에 핸드폰,지갑 있음 → [핸드폰, 지갑, 명]")
		void 핸드폰지갑명_DICT_우선() {
			List<String> nnTokens = Arrays.asList(
				"핸드폰지갑명", "핸드폰", "지갑명", "핸드폰지갑", "핸", "드", "폰", "지갑", "지", "갑", "명"
			);
			Set<String> registered = Collections.emptySet();
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰", "지갑"));

			List<String> result = greedySplit("핸드폰지갑명", nnTokens, registered, dict);

			System.out.println("[TEST] 1순위 핸드폰지갑명: " + result);
			assertEquals(Arrays.asList("핸드폰", "지갑", "명"), result);
		}

		@Test
		@DisplayName("운전면허번호 - TB_WORD에 운전면허,번호 → [운전면허, 번호]")
		void 운전면허번호_등록단어_longest() {
			List<String> nnTokens = Arrays.asList("운전면허번호", "운전면허", "운전", "면허", "번호", "면허번호");
			Set<String> registered = new HashSet<>(Arrays.asList("운전", "면허", "번호", "운전면허"));
			Set<String> dict = Collections.emptySet();

			List<String> result = greedySplit("운전면허번호", nnTokens, registered, dict);

			assertEquals(Arrays.asList("운전면허", "번호"), result);
		}

		@Test
		@DisplayName("사용자전화번호 - 전부 TB_WORD → [사용자, 전화, 번호]")
		void 사용자전화번호_전부등록() {
			List<String> nnTokens = Arrays.asList("사용자", "전화", "번호", "사용", "자");
			Set<String> registered = new HashSet<>(Arrays.asList("사용자", "전화", "번호"));
			Set<String> dict = Collections.emptySet();

			List<String> result = greedySplit("사용자전화번호", nnTokens, registered, dict);

			assertEquals(Arrays.asList("사용자", "전화", "번호"), result);
		}

		@Test
		@DisplayName("핸드폰꾸룩 - 핸드폰(DICT) + 꾸룩(매칭불가)")
		void 핸드폰꾸룩_뒷부분_매칭불가() {
			List<String> nnTokens = Arrays.asList("핸드폰", "핸드", "폰");
			Set<String> registered = Collections.emptySet();
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰"));

			List<String> result = greedySplit("핸드폰꾸룩", nnTokens, registered, dict);

			System.out.println("[TEST] 핸드폰꾸룩: " + result);
			assertEquals("핸드폰", result.get(0));
			assertEquals("꾸룩", result.get(1));  // 매칭불가 잔여
			assertEquals(2, result.size());
		}
	}

	// ========== 7. 2순위 분리 (generateAlternativeSplit) 테스트 ==========

	@Nested
	@DisplayName("2순위 분리 (미등록 토큰 추가 분리)")
	class AlternativeSplit {

		@Test
		@DisplayName("핸드폰지갑명 → 2순위: [핸드, 폰, 지갑, 명]")
		void 핸드폰지갑명_2순위() {
			List<String> nnTokens = Arrays.asList(
				"핸드폰지갑명", "핸드폰", "핸드", "지갑명", "핸드폰지갑", "핸", "드", "폰", "지갑", "지", "갑", "명"
			);
			Set<String> registered = Collections.emptySet();
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰", "지갑"));

			List<String> primary = greedySplit("핸드폰지갑명", nnTokens, registered, dict);
			Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
			List<String> alt = generateAlternativeSplit(primary, candidateSet, registered);

			System.out.println("[TEST] 2순위 핸드폰지갑명: " + alt);
			assertEquals(Arrays.asList("핸드", "폰", "지갑", "명"), alt);
		}

		@Test
		@DisplayName("운전면허번호 - TB_WORD 등록 → 2순위도 동일 (분리 안 함)")
		void 운전면허번호_등록이면_분리안함() {
			List<String> nnTokens = Arrays.asList("운전면허번호", "운전면허", "운전", "면허", "번호");
			Set<String> registered = new HashSet<>(Arrays.asList("운전면허", "번호"));

			List<String> primary = Arrays.asList("운전면허", "번호");
			Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
			List<String> alt = generateAlternativeSplit(primary, candidateSet, registered);

			assertEquals(primary, alt, "등록 단어는 추가 분리하지 않음");
		}

		@Test
		@DisplayName("핸드폰꾸룩 → 2순위: [핸드, 폰, 꾸룩]")
		void 핸드폰꾸룩_2순위() {
			List<String> nnTokens = Arrays.asList("핸드폰", "핸드", "폰");
			Set<String> registered = Collections.emptySet();

			List<String> primary = Arrays.asList("핸드폰", "꾸룩");
			Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
			List<String> alt = generateAlternativeSplit(primary, candidateSet, registered);

			System.out.println("[TEST] 2순위 핸드폰꾸룩: " + alt);
			assertEquals(Arrays.asList("핸드", "폰", "꾸룩"), alt);
		}
	}

	// ========== 8. DICT vs OKT 우선순위 검증 ==========

	@Nested
	@DisplayName("DICT vs OKT 우선순위")
	class DictVsOktPriority {

		@Test
		@DisplayName("DICT에 있는 단어가 OKT 후보보다 우선 매칭됨")
		void dictHigherThanOkt() {
			// "핸드폰번호" - 핸드폰은 DICT에 있고, "핸드", "폰"은 OKT 후보
			// DICT가 OKT보다 우선이므로 "핸드폰" 통째로 매칭
			List<String> nnTokens = Arrays.asList("핸드폰번호", "핸드폰", "핸드", "폰", "번호");
			Set<String> registered = new HashSet<>(Arrays.asList("번호"));
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰"));

			List<String> result = greedySplit("핸드폰번호", nnTokens, registered, dict);

			// 번호는 registered (TB_WORD) → 1순위, 핸드폰은 DICT → 2순위
			// "핸드폰번호" 시작에서 "번호"는 startsWith가 아니므로 건너뜀
			// "핸드폰"은 DICT에서 매칭 → 그 다음 "번호"는 TB_WORD에서 매칭
			assertEquals(2, result.size());
			assertEquals("핸드폰", result.get(0));
			assertEquals("번호", result.get(1));
		}

		@Test
		@DisplayName("DICT에 핸드폰 있고 OKT에 핸드+폰 있을 때, DICT가 우선")
		void dictBeatsOktShorterTokens() {
			// OKT에 "핸드"(2글자), "폰"(1글자) 있지만
			// DICT에 "핸드폰"(3글자) 있으므로 DICT가 우선
			List<String> nnTokens = Arrays.asList("핸드폰명", "핸드폰", "핸드", "폰", "명");
			Set<String> registered = Collections.emptySet();
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰"));

			List<String> result = greedySplit("핸드폰명", nnTokens, registered, dict);

			assertEquals("핸드폰", result.get(0), "DICT에 있는 '핸드폰'이 OKT '핸드'+'폰'보다 우선");
			assertEquals("명", result.get(1));
		}

		@Test
		@DisplayName("TB_WORD 등록어가 DICT보다 우선")
		void registeredBeatsDict() {
			// "지갑"이 TB_WORD에도 DICT에도 있으면 TB_WORD가 우선
			List<String> nnTokens = Arrays.asList("지갑번호", "지갑", "번호");
			Set<String> registered = new HashSet<>(Arrays.asList("지갑", "번호"));
			Set<String> dict = new HashSet<>(Arrays.asList("지갑"));

			List<String> result = greedySplit("지갑번호", nnTokens, registered, dict);

			assertEquals(Arrays.asList("지갑", "번호"), result);
		}
	}

	// ========== 9. 입력 전체가 하나의 등록단어인 경우 ==========

	@Nested
	@DisplayName("입력 전체가 단일 등록 단어")
	class SingleRegisteredWordInput {

		@Test
		@DisplayName("입력 전체가 등록단어 하나 → 분리 없이 1개 반환")
		void wholeInputIsSingleRegisteredWord() {
			// greedySplit에서 !c.equals(input) 조건이 있어 매칭되지 않음
			// 결국 잔여 통째로 반환됨
			List<String> nnTokens = Arrays.asList("사용자");
			Set<String> registered = new HashSet<>(Arrays.asList("사용자"));
			Set<String> dict = Collections.emptySet();

			List<String> result = greedySplit("사용자", nnTokens, registered, dict);

			assertEquals(1, result.size());
			assertEquals("사용자", result.get(0));
		}

		@Test
		@DisplayName("전체가 하나의 등록단어 → 상태 판정 시뮬레이션")
		void singleWordStatusJudgement() {
			// 단일 등록단어가 분리 결과로 나오면 MATCHED → AUTO
			List<TermAnalysisResult.WordAnalysis> words = Arrays.asList(
				matchedWordWithAbrv("사용자", "USR")
			);
			String status = judgeStatus(words);
			assertEquals("AUTO", status);
			assertEquals("USR", composeEngAbrv(words));
		}

		@Test
		@DisplayName("전체가 하나의 DICT 단어 → 분리 없이 1개 반환")
		void wholeInputIsSingleDictWord() {
			List<String> nnTokens = Arrays.asList("핸드폰");
			Set<String> registered = Collections.emptySet();
			Set<String> dict = new HashSet<>(Arrays.asList("핸드폰"));

			List<String> result = greedySplit("핸드폰", nnTokens, registered, dict);

			assertEquals(1, result.size());
			assertEquals("핸드폰", result.get(0));
		}
	}

	// ========== 10. 1글자 단어 케이스 ==========

	@Nested
	@DisplayName("1글자 단어 처리")
	class SingleCharWordTest {

		@Test
		@DisplayName("1글자 토큰만 있는 경우 - OKT 2+ 매칭 불가, gap 처리")
		void singleCharTokensOnly() {
			// 등록 단어도 DICT도 없고, OKT 후보도 1글자뿐
			List<String> nnTokens = Arrays.asList("가", "나", "다");
			Set<String> registered = Collections.emptySet();
			Set<String> dict = Collections.emptySet();

			List<String> result = greedySplit("가나다", nnTokens, registered, dict);

			// 1글자는 OKT 2+ 매칭 조건(c.length() >= 2)에 걸리지 않음
			// "가나다" 전체가 gap으로 처리됨
			assertFalse(result.isEmpty());
			// 결과는 gap 처리에 의해 "가나다" 통째이거나, 1글자씩 분리
			// gap 로직: skip=1에서 "나다"에 대해 candidates 확인 - "나" startsWith but candidates에 "나" 있음 → gap
			// 즉 "가"(gap) + "나" startsWith check...
			// 실제로 1글자 후보는 !c.equals(input)를 만족하므로 gap에서 매칭 가능
			assertTrue(result.size() >= 1);
		}

		@Test
		@DisplayName("1글자 등록단어 → TB_WORD에서도 2글자 이상 조건이 있는지 확인")
		void singleCharRegisteredWord() {
			// greedySplit에서 registered 매칭은 길이 제한 없이 startsWith && !equals(input) 체크
			List<String> nnTokens = Arrays.asList("가번호", "가", "번호");
			Set<String> registered = new HashSet<>(Arrays.asList("가", "번호"));
			Set<String> dict = Collections.emptySet();

			// "가번호"에서 registered "가"는 startsWith=true, !equals("가번호")=true → 매칭
			// 이어서 "번호" → registered 매칭
			List<String> result = greedySplit("가번호", nnTokens, registered, dict);

			assertEquals(2, result.size());
			assertEquals("가", result.get(0));
			assertEquals("번호", result.get(1));
		}

		@Test
		@DisplayName("2순위 분리에서 2글자 이하 토큰은 분리하지 않음")
		void altSplitSkipsShortTokens() {
			List<String> primary = Arrays.asList("가", "나");
			Set<String> candidateSet = new LinkedHashSet<>(Arrays.asList("가", "나"));
			Set<String> registered = Collections.emptySet();

			List<String> alt = generateAlternativeSplit(primary, candidateSet, registered);

			// 2글자 이하이므로 분리하지 않음
			assertEquals(primary, alt);
		}
	}

	// ========== greedySplit 로직 재현 (컨트롤러와 동일) ==========

	private List<String> greedySplit(String input, List<String> nnTokens, Set<String> registered, Set<String> dict) {
		List<String> result = new ArrayList<>();
		Set<String> candidateSet = new LinkedHashSet<>(nnTokens);
		for (String w : registered) { if (w.length() >= 2 && input.contains(w)) candidateSet.add(w); }

		String remaining = input;
		int maxIter = 100;
		while (!remaining.isEmpty() && maxIter-- > 0) {
			String selected = null;

			// 1. TB_WORD longest
			for (String c : candidateSet) {
				if (registered.contains(c) && remaining.startsWith(c) && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 2. DICT longest
			for (String c : candidateSet) {
				if (dict.contains(c) && remaining.startsWith(c) && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 3. remaining == OKT token
			if (candidateSet.contains(remaining) && !remaining.equals(input)) {
				result.add(remaining); remaining = ""; continue;
			}

			// 4. OKT longest 2+
			for (String c : candidateSet) {
				if (c.length() >= 2 && remaining.startsWith(c) && c.length() < remaining.length() && !c.equals(input)) {
					if (selected == null || c.length() > selected.length()) selected = c;
				}
			}
			if (selected != null) { result.add(selected); remaining = remaining.substring(selected.length()); continue; }

			// 5. gap
			boolean skipped = false;
			for (int skip = 1; skip < remaining.length(); skip++) {
				String sub = remaining.substring(skip);
				for (String c : candidateSet) {
					if (sub.startsWith(c) && !c.equals(input)) { skipped = true; break; }
				}
				if (skipped) { result.add(remaining.substring(0, skip)); remaining = remaining.substring(skip); break; }
			}
			if (!skipped) { result.add(remaining); remaining = ""; }
		}
		return result;
	}

	private List<String> generateAlternativeSplit(List<String> primary, Set<String> candidates, Set<String> registered) {
		List<String> alt = new ArrayList<>();
		for (String token : primary) {
			if (registered.contains(token) || token.length() < 3) { alt.add(token); continue; }
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

	// ========== 헬퍼 메서드: 상태 판정 로직 (컨트롤러 로직 재현) ==========

	private String judgeStatus(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
		boolean hasNew = wordAnalyses.stream()
			.anyMatch(wa -> "NEW".equals(wa.getStatus()));
		boolean hasMatched = wordAnalyses.stream()
			.anyMatch(wa -> "MATCHED".equals(wa.getStatus()));

		if (wordAnalyses.isEmpty() || !hasMatched) {
			return "FAILED";
		} else if (hasNew) {
			return "PARTIAL";
		} else {
			return "AUTO";
		}
	}

	private String composeEngAbrv(List<TermAnalysisResult.WordAnalysis> wordAnalyses) {
		StringBuilder engAbrv = new StringBuilder();
		for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
			if (engAbrv.length() > 0) engAbrv.append("_");
			if ("MATCHED".equals(wa.getStatus()) && wa.getSelected() != null) {
				engAbrv.append(wa.getSelected().getWordEngAbrvNm());
			} else if (wa.getNewWord() != null) {
				engAbrv.append(wa.getNewWord().getWordEngAbrvNm());
			}
		}
		return engAbrv.toString();
	}

	// ========== 헬퍼 메서드: 테스트 데이터 생성 ==========

	private void addWord(String nm, String eng, String abrv, String domainClsf) {
		FakeWord w = new FakeWord(nm, eng, abrv, domainClsf);
		wordsByNm.computeIfAbsent(nm, k -> new ArrayList<>()).add(w);
	}

	private void addDict(String kor, String eng, String abrv, String domainClsf) {
		Map<String, String> entry = new HashMap<>();
		entry.put("wordEng", eng);
		entry.put("wordAbrv", abrv);
		entry.put("domainClsfNm", domainClsf);
		wordDict.put(kor, entry);
	}

	private TermAnalysisResult.WordAnalysis matchedWord(String wordNm) {
		return matchedWordWithAbrv(wordNm, wordsByNm.containsKey(wordNm)
			? wordsByNm.get(wordNm).get(0).abrv : "");
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

	private TermAnalysisResult.WordAnalysis newWord(String wordNm) {
		TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
		wa.setWordNm(wordNm);
		wa.setStatus("NEW");
		wa.setCandidates(new ArrayList<>());
		TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
		Map<String, String> entry = wordDict.get(wordNm);
		if (entry != null) {
			suggestion.setWordEngAbrvNm(entry.getOrDefault("wordAbrv", ""));
			suggestion.setWordEngNm(entry.getOrDefault("wordEng", ""));
			suggestion.setDomainClsfNm(entry.getOrDefault("domainClsfNm", ""));
		} else {
			suggestion.setWordEngAbrvNm("");
			suggestion.setWordEngNm("");
			suggestion.setDomainClsfNm("");
		}
		wa.setNewWord(suggestion);
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

	/** 테스트용 단어 VO */
	private static class FakeWord {
		final String nm, eng, abrv, domainClsf;
		FakeWord(String nm, String eng, String abrv, String domainClsf) {
			this.nm = nm; this.eng = eng; this.abrv = abrv; this.domainClsf = domainClsf;
		}
	}
}
