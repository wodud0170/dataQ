package com.ndata.quality.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ndata.module.StringUtils;
import com.twitter.penguin.korean.KoreanPosJava;
import com.twitter.penguin.korean.KoreanTokenJava;
import com.twitter.penguin.korean.TwitterKoreanProcessorJava;
import com.twitter.penguin.korean.phrase_extractor.KoreanPhraseExtractor;
import com.twitter.penguin.korean.tokenizer.KoreanTokenizer;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;
import lombok.extern.slf4j.Slf4j;
import scala.collection.Seq;

@Slf4j
public class StringWordAnalyzer {

    public static Map<String, String> getWordsFromString(String str) {
        Map<String, String> tokenMap = new LinkedHashMap<String, String>();
        Komoran komoran = new Komoran(DEFAULT_MODEL.FULL);

        KomoranResult analyzeResultList = komoran.analyze(str);

        // System.out.println(analyzeResultList.getPlainText());
        List<Token> tokenList = analyzeResultList.getTokenList();

        for (Token token : tokenList) {
            log.info(String.format("(%2d, %2d) %s/%s", token.getBeginIndex(), token.getEndIndex(), token.getMorph(),
                    token.getPos()));
            if (token.getPos().contains("NN") || token.getPos().contains("SL") || token.getPos().contains("SN")
                    || token.getPos().contains("NF")) {// 단어가 명사인 경우
                tokenMap.put(token.getMorph(), "NN");
            } else {
                tokenMap.put(token.getMorph(), token.getPos());
            }
        }

        return tokenMap;
    }

    public static Map<String, String> getWordsFromStringByOkt(String termsStr, Map<String, String> tokenMap,
            boolean recursive) {
        if (tokenMap == null) {
            tokenMap = new LinkedHashMap<String, String>();
        }

        // Normalize
        CharSequence normalized = TwitterKoreanProcessorJava.normalize(termsStr);

        // Tokenize
        Seq<KoreanTokenizer.KoreanToken> tokens = (Seq<KoreanTokenizer.KoreanToken>) TwitterKoreanProcessorJava
                .tokenize(normalized);

        // Stemming
        Seq<KoreanTokenizer.KoreanToken> stemmed = (Seq<KoreanTokenizer.KoreanToken>) TwitterKoreanProcessorJava
                .stem(tokens);

        // KoreanTokenJava 리스트 [학교(Noun: 15, 2), 가다(Verb: 18, 2), 가족(Noun: 0, 2)]
        List<KoreanTokenJava> stemmedTokenList = TwitterKoreanProcessorJava.tokensToJavaKoreanTokenList(stemmed);
        // 마지막 어간이 1글자 이상인 경우는 분리한다.
        int lastIdx = stemmedTokenList.size() - 1;
        KoreanTokenJava lastToken = stemmedTokenList.get(lastIdx);
        if (lastIdx > 0 && lastToken.getLength() > 1) {
            stemmedTokenList.remove(lastToken);
            for (String tokenText : lastToken.getText().split("")) {
                KoreanTokenJava newToken = new KoreanTokenJava(tokenText, KoreanPosJava.Noun, lastIdx, tokenText.length(), false);
                stemmedTokenList.add(newToken);
                lastIdx++;
            }
        }
        log.info("1>> {}", stemmedTokenList);

        if (!recursive) {
            // 용어가 스페이스로 구분되어 전달된 경우는 우선적으로 각 토큰을 추가한다.
            String[] strTokens = StringUtils.split(termsStr, " ");
            for (int i = 0; i < strTokens.length; i++) {
                tokenMap = putTokenToMap(termsStr, strTokens[i], "NN", tokenMap);
            }
        }

        // 어구 리스트
        List<KoreanPhraseExtractor.KoreanPhrase> phrases = TwitterKoreanProcessorJava.extractPhrases(tokens, true, true);
        for (KoreanPhraseExtractor.KoreanPhrase item : phrases) {
            log.info("3>> {}({})", item.text(), item.pos().toString());
            String[] strTokens = StringUtils.split(item.text(), " ");
            if (strTokens.length > 1) {
                for (String strToken : strTokens) {
                    log.info("4>> {}", strToken);
                    if (tokenMap.get(strToken) == null && !isStemmedToken(strToken, stemmedTokenList)) {
                        tokenMap = getWordsFromStringByOkt(strToken, tokenMap, true);
                    }
                }
            } else {
                if (isPosNoun(item)) {// 단어가 명사인 경우
                    tokenMap = putTokenToMap(termsStr, item.text(), "NN", tokenMap);
                    // 어구 목록에서 현재 어구를 포함하는 어구의 현재어구를 제거한 단어 목록을 추출한다.
                    List<String> spWords = phrases.stream()
                            .filter(v -> v.text().contains(item.text()) && v.text().length() > item.text().length() && isPosNoun(item))
                            .map(d -> {
                                // item.text()를 제거한 문자열 리턴
                                // int idx = d.text().indexOf(item.text());
                                // String s = d.text().substring(0, idx) + d.text().substring(idx +
                                // item.text().length());
                                // return s;
                                return d.text().replace(item.text(), "");
                            })
                            .collect(Collectors.toList());
                    for (String spWord : spWords) {
                        String word = spWord.trim().replaceAll("\\s", "");
                        log.info("4>> {}", word);
                        if (tokenMap.get(word) == null && !isStemmedToken(word, stemmedTokenList)) {
                            tokenMap = putTokenToMap(termsStr, word, "NN", tokenMap);
                        }
                    }
                } else {
                    tokenMap = putTokenToMap(termsStr, item.text(), item.pos().toString(), tokenMap);
                }
            }
        }

        if (!recursive) {
            // 어간 리스트를 추가한다.
            //for (KoreanTokenJava token : stemmedTokenList) {
            for (int idx=0; idx<stemmedTokenList.size(); idx++) {
                KoreanTokenJava token = stemmedTokenList.get(idx);
                log.info("5>> {}({})", token.getText(), token.getPos().toString());
                if (isPosNoun(token)) {// 단어가 명사인 경우
                    // 어간을 추가한다.
                    tokenMap = putTokenToMap(termsStr, token.getText(), "NN", tokenMap);
                    // 어간리스트의 순차조합으로 구성된 단어를 추가한다.
                    String tokenText = token.getText();
                    for (int i=idx+1; i<stemmedTokenList.size(); i++) {
                        if (isPosNoun(stemmedTokenList.get(i))) {// 단어가 명사인 경우
                            tokenText += stemmedTokenList.get(i).getText();
                            if (tokenMap.get(tokenText) == null && !isStemmedToken(tokenText, stemmedTokenList)) {
                                tokenMap = putTokenToMap(termsStr, tokenText, "NN", tokenMap);
                            }
                        }
                    }
                    // 한글 명사인 경우 음절단위로 분리하여 추가한다.
                    if (token.getPos().equals(KoreanPosJava.Noun)) {
                        String[] syllables = token.getText().split("");
                        for (String syllable : syllables) {
                            if (tokenMap.get(syllable) == null && !isStemmedToken(syllable, stemmedTokenList)) {
                                tokenMap = putTokenToMap(termsStr, syllable, "NN", tokenMap);
                            }
                        }
                    }
                } else {
                    tokenMap = putTokenToMap(termsStr, token.getText(), token.getPos().toString(), tokenMap);
                }
            }
        }

        return tokenMap;
    }

    private static Map<String, String> putTokenToMap(String termsStr, String token, String pos, Map<String, String> tokenMap) {
        long strCnt = tokenMap.keySet().stream().filter(v -> (v.equals(token) || v.startsWith(token + "#"))).count();
        if (strCnt > 0 && strCnt < StringUtils.countMatches(termsStr, token)) {
            //동일한 토큰 여러개인 경우에 '#{순서}'를 붙여서 키로 사용한다.
            tokenMap.put(token + "#" + strCnt, pos);
        } else {
            tokenMap.put(token, pos);
        }
        return tokenMap;
    }

    private static boolean isStemmedToken(String str, List<KoreanTokenJava> stemmedTokenList) {
        for (KoreanTokenJava token : stemmedTokenList) {
            if (token.getText().equals(str)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPosNoun(KoreanPhraseExtractor.KoreanPhrase phrase) {
        return phrase.pos().toString().equals(KoreanPosJava.Noun.name()) || phrase.pos().toString().equals(KoreanPosJava.Foreign.name()) ||
                phrase.pos().toString().equals(KoreanPosJava.Number.name()) || phrase.pos().toString().equals(KoreanPosJava.ProperNoun.name()) ||
                phrase.pos().toString().equals(KoreanPosJava.Suffix.name()) || phrase.pos().toString().equals(KoreanPosJava.NounPrefix.name()) ||
                phrase.pos().toString().equals(KoreanPosJava.Alpha.name()) || phrase.pos().toString().equals(KoreanPosJava.Josa.name());
    }

    private static boolean isPosNoun(KoreanTokenJava token) {
        return token.getPos().equals(KoreanPosJava.Noun)
                || token.getPos().equals(KoreanPosJava.Foreign) ||
                token.getPos().equals(KoreanPosJava.Number)
                || token.getPos().equals(KoreanPosJava.ProperNoun) ||
                token.getPos().equals(KoreanPosJava.Suffix)
                || token.getPos().equals(KoreanPosJava.NounPrefix) ||
                token.getPos().equals(KoreanPosJava.Alpha)
                || token.getPos().equals(KoreanPosJava.Josa);
    }
}
