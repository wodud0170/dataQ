package com.ndata.quality.model.std;

import java.util.List;
import lombok.Data;

/**
 * 표준화 추천 분석 결과 DTO
 *
 * <p>입력된 한글 용어명을 형태소 분석하여 단어 후보, 영문약어, 도메인 추천 정보를 담는다.</p>
 * <ul>
 *   <li>REGISTERED: 기등록 용어 (existingTerm에 기존 용어 정보)</li>
 *   <li>AUTO: 모든 구성 단어가 TB_WORD에 매칭됨 (즉시 등록 가능)</li>
 *   <li>PARTIAL: 일부 단어가 미등록 (NEW/UNRECOGNIZED)</li>
 *   <li>FAILED: 형태소 분석 실패 또는 매칭 단어 없음</li>
 * </ul>
 */
@Data
public class TermAnalysisResult {
    private String inputNm;           // 입력한 한글명
    private String status;            // REGISTERED, AUTO, PARTIAL, FAILED

    // 기등록 용어 정보 (status=REGISTERED)
    private StdTermsVo existingTerm;

    // 분석된 단어 목록
    private List<WordAnalysis> words;

    // 추천 영문약어
    private String recommendedEngAbrvNm;

    // 추천 도메인
    private String recommendedDomainNm;
    private String recommendedDataType;
    private long recommendedDataLen;

    // 도메인 후보 목록
    private List<StdDomainVo> domainCandidates;

    // 2순위 분리 결과 (1순위 미등록 단어를 추가 분리)
    private List<WordAnalysis> alternativeWords;
    private String alternativeEngAbrvNm;

    /**
     * 개별 단어 분석 결과
     *
     * <ul>
     *   <li>MATCHED: TB_WORD에 매칭됨 (candidates에 후보, selected에 자동 선택)</li>
     *   <li>NEW: TB_WORD_DICT에 매칭됨 (newWord에 추천 영문명/약어)</li>
     *   <li>UNRECOGNIZED: 어디에도 매칭되지 않음 (사용자 직접 입력 필요)</li>
     * </ul>
     */
    @Data
    public static class WordAnalysis {
        private String wordNm;         // 한글명
        private String status;         // MATCHED, NEW, UNRECOGNIZED
        private List<WordCandidate> candidates;  // 매칭된 후보들
        private WordCandidate selected;          // 자동 선택된 후보
        private NewWordSuggestion newWord;       // status=NEW일 때 추천값
    }

    /**
     * 단어 후보 (TB_WORD에 매칭된 단어 정보 + 사용 빈도 기반 점수)
     */
    @Data
    public static class WordCandidate {
        private String wordId;
        private String wordNm;
        private String wordEngAbrvNm;
        private String wordEngNm;
        private String domainClsfNm;
        private int score;
        private boolean selected;
    }

    /**
     * 신규 단어 추천 정보 (TB_WORD_DICT 기반 또는 빈 값)
     */
    @Data
    public static class NewWordSuggestion {
        private String wordEngAbrvNm;  // 추천 영문약어
        private String wordEngNm;      // 추천 영문명
        private String domainClsfNm;   // 추천 도메인분류
    }
}
