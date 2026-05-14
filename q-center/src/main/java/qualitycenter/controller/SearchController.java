package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.quality.model.std.StdApproveStatVo;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;

@Tag(name = "데이터검색", description = "데이터검색(대시보드 등) API")
@Slf4j
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
	private SessionService sessionService;

    @Autowired
	private SqlSessionTemplate sqlSessionTemplate;

    /** 88번 거버넌스 — FE 가 현재 사용자의 관리자 여부 확인 */
    @RequestMapping(value = "/getUserInfo", method = RequestMethod.GET)
    public java.util.Map<String, Object> getUserInfo() {
        java.util.Map<String, Object> m = new HashMap<>();
        m.put("userId", sessionService.getUserId());
        m.put("admin",  sessionService.isAdmin());
        m.put("role",   sessionService.isAdmin() ? "A" : "M");
        return m;
    }

    @RequestMapping(value = "/getDashboardInfo", method = RequestMethod.GET)
	public HashMap getDashboardInfo() {
        StdApproveStatVo.RetrieveCond retCond = new StdApproveStatVo.RetrieveCond();
        String reqUserId = null;
		if (!sessionService.isAdmin()) {// 관리자가 아닌 경우에는 자신 신청내역만 확인
			reqUserId = sessionService.getUserId();
			retCond.setReqUserId(reqUserId);
		}
		return sqlSessionTemplate.selectOne("search.selectDataboardInfo", retCond);
	}

    @RequestMapping(value = "/getDataModelStat", method = RequestMethod.GET)
	public HashMap getDataModelStat() {
		return sqlSessionTemplate.selectOne("search.selectDataModelStat");
	}

    @RequestMapping(value = "/getTopDataModelList", method = RequestMethod.GET)
	public List<HashMap> getTopDataModelList() {
		return sqlSessionTemplate.selectList("search.selectTopDataModelList");
	}
}
