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
