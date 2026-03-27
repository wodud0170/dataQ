package qualityexecutor.service.std;

import com.ndata.quality.model.std.*;
import com.ndata.quality.tool.StringWordAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TermRecommendService {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    public List<TermAnalysisResult> analyze(List<String> termNames) {
        // 1. Cache all existing terms by Korean name
        List<StdTermsVo> allTerms = sqlSessionTemplate.selectList("terms.selectAllTermsByNm");
        Map<String, StdTermsVo> termsByNm = new HashMap<>();
        for (StdTermsVo t : allTerms) {
            termsByNm.put(t.getTermsNm(), t);
        }

        // 2. Cache all words by Korean name
        List<StdWordVo> allWords = sqlSessionTemplate.selectList("word.selectAllWords");
        Map<String, List<StdWordVo>> wordsByNm = new HashMap<>();
        for (StdWordVo w : allWords) {
            if (!wordsByNm.containsKey(w.getWordNm())) {
                wordsByNm.put(w.getWordNm(), new ArrayList<>());
            }
            wordsByNm.get(w.getWordNm()).add(w);
        }

        // 3. Word usage counts for scoring
        List<Map<String, Object>> usageCounts = sqlSessionTemplate.selectList("word.selectWordUsageCounts");
        Map<String, Integer> usageMap = new HashMap<>();
        for (Map<String, Object> row : usageCounts) {
            String nm = (String) row.get("wordNm");
            Number cnt = (Number) row.get("cnt");
            if (nm != null && cnt != null) usageMap.put(nm, cnt.intValue());
        }

        // 4. Cache domains by classification
        List<StdDomainVo> allDomains = sqlSessionTemplate.selectList("domain.selectAllDomains");
        Map<String, List<StdDomainVo>> domainsByClsf = new HashMap<>();
        for (StdDomainVo d : allDomains) {
            String clsf = d.getDomainClsfNm();
            if (clsf == null) clsf = "";
            if (!domainsByClsf.containsKey(clsf)) {
                domainsByClsf.put(clsf, new ArrayList<>());
            }
            domainsByClsf.get(clsf).add(d);
        }

        List<TermAnalysisResult> results = new ArrayList<>();

        for (String name : termNames) {
            String cleanName = name.replaceAll("\\s", "");
            if (cleanName.isEmpty()) continue;

            TermAnalysisResult result = new TermAnalysisResult();
            result.setInputNm(cleanName);

            // Check if already registered
            if (termsByNm.containsKey(cleanName)) {
                result.setStatus("REGISTERED");
                result.setExistingTerm(termsByNm.get(cleanName));
                results.add(result);
                continue;
            }

            // Morphological analysis
            Map<String, String> tokens;
            try {
                tokens = StringWordAnalyzer.getWordsFromStringByOkt(cleanName, null, false);
            } catch (Exception e) {
                log.error("Tokenization failed for: {}", cleanName, e);
                result.setStatus("FAILED");
                result.setWords(new ArrayList<>());
                results.add(result);
                continue;
            }

            if (tokens == null || tokens.isEmpty()) {
                result.setStatus("FAILED");
                result.setWords(new ArrayList<>());
                results.add(result);
                continue;
            }

            // Analyze each token
            List<TermAnalysisResult.WordAnalysis> wordAnalyses = new ArrayList<>();
            boolean hasNew = false;

            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                String token = entry.getKey().replaceAll("#\\d+$", ""); // remove duplicate suffix
                String pos = entry.getValue();

                // Skip non-nouns
                if (!"NN".equals(pos)) continue;

                TermAnalysisResult.WordAnalysis wa = new TermAnalysisResult.WordAnalysis();
                wa.setWordNm(token);

                List<StdWordVo> matches = wordsByNm.get(token);
                if (matches != null && !matches.isEmpty()) {
                    wa.setStatus("MATCHED");
                    List<TermAnalysisResult.WordCandidate> candidates = new ArrayList<>();
                    TermAnalysisResult.WordCandidate best = null;
                    int bestScore = -1;

                    for (StdWordVo w : matches) {
                        TermAnalysisResult.WordCandidate c = new TermAnalysisResult.WordCandidate();
                        c.setWordId(w.getId());
                        c.setWordNm(w.getWordNm());
                        c.setWordEngAbrvNm(w.getWordEngAbrvNm());
                        c.setWordEngNm(w.getWordEngNm());
                        c.setDomainClsfNm(w.getDomainClsfNm());

                        // Scoring
                        int score = 0;
                        Integer usage = usageMap.get(w.getWordNm());
                        if (usage != null) score += usage * 100;
                        if (w.getWordEngAbrvNm() != null) {
                            score += (10 - Math.min(w.getWordEngAbrvNm().length(), 10)) * 10;
                        }
                        if ("Y".equals(w.getCommStndYn())) score += 50;
                        c.setScore(score);

                        if (score > bestScore) {
                            bestScore = score;
                            best = c;
                        }
                        candidates.add(c);
                    }

                    // Sort by score desc
                    candidates.sort((a, b) -> b.getScore() - a.getScore());
                    if (best != null) best.setSelected(true);

                    wa.setCandidates(candidates);
                    wa.setSelected(best);
                } else {
                    wa.setStatus("NEW");
                    hasNew = true;
                    wa.setCandidates(new ArrayList<>());

                    // Suggest new word
                    TermAnalysisResult.NewWordSuggestion suggestion = new TermAnalysisResult.NewWordSuggestion();
                    suggestion.setWordEngAbrvNm(token.toUpperCase()); // placeholder
                    suggestion.setWordEngNm(token); // placeholder
                    suggestion.setDomainClsfNm("");
                    wa.setNewWord(suggestion);
                }

                wordAnalyses.add(wa);
            }

            result.setWords(wordAnalyses);

            // Generate recommended English abbreviation
            StringBuilder engAbrv = new StringBuilder();
            for (TermAnalysisResult.WordAnalysis wa : wordAnalyses) {
                if (engAbrv.length() > 0) engAbrv.append("_");
                if ("MATCHED".equals(wa.getStatus()) && wa.getSelected() != null) {
                    engAbrv.append(wa.getSelected().getWordEngAbrvNm());
                } else if (wa.getNewWord() != null) {
                    engAbrv.append(wa.getNewWord().getWordEngAbrvNm());
                }
            }
            result.setRecommendedEngAbrvNm(engAbrv.toString());

            // Recommend domain based on last word
            if (!wordAnalyses.isEmpty()) {
                TermAnalysisResult.WordAnalysis lastWord = wordAnalyses.get(wordAnalyses.size() - 1);
                String clsfNm = null;
                if ("MATCHED".equals(lastWord.getStatus()) && lastWord.getSelected() != null) {
                    clsfNm = lastWord.getSelected().getDomainClsfNm();
                }
                if (clsfNm != null && domainsByClsf.containsKey(clsfNm)) {
                    List<StdDomainVo> candidates = domainsByClsf.get(clsfNm);
                    result.setDomainCandidates(candidates);
                    if (!candidates.isEmpty()) {
                        StdDomainVo first = candidates.get(0);
                        result.setRecommendedDomainNm(first.getDomainNm());
                        result.setRecommendedDataType(first.getDataType());
                        result.setRecommendedDataLen(first.getDataLen());
                    }
                }
            }

            // Determine status
            if (wordAnalyses.isEmpty()) {
                result.setStatus("FAILED");
            } else if (hasNew) {
                result.setStatus("PARTIAL");
            } else {
                result.setStatus("AUTO");
            }

            results.add(result);
        }

        return results;
    }
}
