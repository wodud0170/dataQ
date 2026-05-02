package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.quality.model.std.QualProfileHistoryVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 70번 §5.2 진단 통계 — 시계열 추이
 * 모델/테이블/컬럼 단위 위반률·NULL율·DISTINCT 비율 추이.
 */
@Slf4j
@RestController
@RequestMapping("/api/qual/stats")
public class QualStatsController {

    @Autowired
    private SqlSessionTemplate sql;

    @GetMapping("/trend")
    public List<QualProfileHistoryVo> trend(
            @RequestParam String dmId,
            @RequestParam(required = false) String objNm,
            @RequestParam(required = false) String attrNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("dmId", dmId);
        p.put("objNm", objNm);
        p.put("attrNm", attrNm);
        return sql.selectList("qualDiag.selectProfileTrend", p);
    }
}
