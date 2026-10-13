package qualitycenter.controller;

import java.util.HashMap;
import java.util.List;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.quality.common.PermCode;
import com.ndata.quality.model.std.StdApproveStatVo;
import com.ndata.quality.service.PermService;

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
	private PermService permService;

    @Autowired
	private SqlSessionTemplate sqlSessionTemplate;

    /**
     * 88번 거버넌스 — 현재 사용자 정보.
     * admin·role 은 사용자 속성 표시용. 화면 기능 분기는 perms(104번 WP2, 권한 코드 → 시스템 범위)로 한다.
     */
    @RequestMapping(value = "/getUserInfo", method = RequestMethod.GET)
    public java.util.Map<String, Object> getUserInfo() {
        java.util.Map<String, Object> m = new HashMap<>();
        boolean admin = sessionService.isAdmin();
        m.put("userId", sessionService.getUserId());
        m.put("admin",  admin);
        m.put("role",   admin ? "A" : "M");
        m.put("perms",  permService.perms(sessionService.getUserId()));
        return m;
    }

    @RequestMapping(value = "/getDashboardInfo", method = RequestMethod.GET)
	public HashMap getDashboardInfo() {
        StdApproveStatVo.RetrieveCond retCond = new StdApproveStatVo.RetrieveCond();
        String reqUserId = null;
		if (!sessionService.hasPerm(PermCode.STD_APPROVE)) {// 승인 권한이 없으면 자신 신청내역만 확인
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
