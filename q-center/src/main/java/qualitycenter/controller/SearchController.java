package qualitycenter.controller;

import java.util.HashMap;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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

    /** 대시보드 표준 현황 — 승인된 용어·단어·도메인 건수 (110번 2.4-10-b: 화면이 쓰지 않는 승인 건수 3종은 뺐다) */
    @RequestMapping(value = "/getDashboardInfo", method = RequestMethod.GET)
	public HashMap getDashboardInfo() {
		return sqlSessionTemplate.selectOne("search.selectDataboardInfo", new StdApproveStatVo.RetrieveCond());
	}
}
