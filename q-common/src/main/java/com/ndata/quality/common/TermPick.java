package com.ndata.quality.common;

import java.util.List;
import java.util.stream.Collectors;

import com.ndata.quality.model.std.StdTermsVo;

/**
 * 108 §4.31 (104번 WP6-13) — 용어 영문약어명 중복 허용 사전에서는 한 영문약어에 용어가 여럿일 수 있다.
 * 그 컬럼의 용어는 "한글명이 같은 용어, 없으면 용어명 순 첫 용어" 로 본다(114 §1 권고 채택).
 * 표준 진단(q-executor DiagService)·수집(DataModelService)·컬럼 표준 검사·영향도(q-center DataModelController)가 같이 쓴다.
 */
public final class TermPick {

	private TermPick() {}

	/** 후보 중 한글명(trim)이 같은 용어, 없으면 첫 용어(호출 쿼리가 용어명 순). 후보가 없으면 null */
	public static StdTermsVo pick(List<StdTermsVo> cands, String attrNmKr) {
		if (cands.isEmpty()) return null;
		if (attrNmKr != null) {
			String kr = attrNmKr.trim();
			for (StdTermsVo t : cands) {
				if (kr.equals(t.getTermsNm())) return t;
			}
		}
		return cands.get(0);
	}

	/** 후보 용어명을 ", " 로 이음 — 한글명 불일치 표준값 */
	public static String names(List<StdTermsVo> cands) {
		return cands.stream().map(StdTermsVo::getTermsNm).collect(Collectors.joining(", "));
	}
}
