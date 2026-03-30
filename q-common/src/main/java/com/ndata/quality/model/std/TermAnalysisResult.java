package com.ndata.quality.model.std;

import java.util.List;
import lombok.Data;

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

    @Data
    public static class WordAnalysis {
        private String wordNm;         // 한글명
        private String status;         // MATCHED, NEW, UNRECOGNIZED
        private List<WordCandidate> candidates;  // 매칭된 후보들
        private WordCandidate selected;          // 자동 선택된 후보
        private NewWordSuggestion newWord;       // status=NEW일 때 추천값
    }

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

    @Data
    public static class NewWordSuggestion {
        private String wordEngAbrvNm;  // 추천 영문약어
        private String wordEngNm;      // 추천 영문명
        private String domainClsfNm;   // 추천 도메인분류
    }
}
