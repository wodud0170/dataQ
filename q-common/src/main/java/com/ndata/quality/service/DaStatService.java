package com.ndata.quality.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 104번 WP9 — 모델별 준수 지표. 시스템 단위 차이 분석·DA 목표 달성도(q-center)와 준수 현황 스냅샷(q-executor)이 같이 쓴다.
 *
 * <p>표준 준수율 = (전체 컬럼 - 이슈 컬럼) / 전체 컬럼 × 100 (최신 완료 표준 진단, CLAUDE.md 통일 계산식),
 * 구조 일치율 = (1 - 차이 / 전체) × 100 (최신 완료 구조 진단, 대시보드·모델 목록과 같은 식). 진단이 없으면 null.</p>
 */
@Service
public class DaStatService {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;

	/** 모델별 지표 (dmId 가 비면 사용 중 모델 전부) — 원값에 stndRate·structRate 를 붙인다 */
	public List<Map<String, Object>> modelMetrics(String dmId) {
		Map<String, Object> p = new HashMap<>();
		p.put("dmId", dmId);
		List<Map<String, Object>> rows = sqlSessionTemplate.selectList("daStat.selectModelMetrics", p);
		for (Map<String, Object> r : rows) {
			r.put("stndRate", stndRate((Number) r.get("totalColCnt"), (Number) r.get("issueColCnt")));
			r.put("structRate", structRate((Number) r.get("structTotal"), (Number) r.get("structDiff")));
		}
		return rows;
	}

	public static Double stndRate(Number total, Number issue) {
		if (total == null || total.longValue() <= 0) return null;
		return round1((total.doubleValue() - (issue == null ? 0 : issue.doubleValue())) / total.doubleValue() * 100);
	}

	public static Double structRate(Number total, Number diff) {
		if (total == null) return null;
		if (total.longValue() <= 0) return 100.0;
		return round1((1 - (diff == null ? 0 : diff.doubleValue()) / total.doubleValue()) * 100);
	}

	public static Double round1(double v) {
		return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}
}
