package qualitycenter.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;
import com.ndata.quality.common.PermCode;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.udp.UdpService;
import reactor.core.publisher.Mono;

/**
 * 104번 WP8-31·75 — 사용자 정의 속성(테이블 관리 항목) 정의와 값 조회 (관리 > 사용자 정의 속성).
 *
 * <p>정의 조회·값 조회는 누구나, 정의 등록·수정·사용 중지·삭제는 사용자 정의 속성 관리 권한(UDP_ADMIN).
 * 값이 있는 정의는 지우지 않고 사용 중지만 한다. 값 저장은 테이블 추가·수정(DataModelController)이 한다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/udp")
public class UdpController {

	private static final List<String> DATA_TPS = java.util.Arrays.asList("TEXT", "NUMBER", "DATE", "SELECT");

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private UdpService udpService;

	/** 정의 목록 — {useOnly: Y(사용 중만)|N} */
	@PostMapping("/defs")
	public List<Map<String, Object>> defs(@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("useOnly", body != null && "Y".equals(body.get("useOnly")) ? "Y" : "N");
		return sqlSessionTemplate.selectList("udp.selectDefs", p);
	}

	/** 테이블 하나의 값 — {dmId, objOwner, objNm} → {udpId: val} */
	@PostMapping("/vals")
	public Map<String, String> vals(@RequestBody Map<String, Object> body) {
		return udpService.objVals((String) body.get("dmId"), (String) body.get("objOwner"), (String) body.get("objNm"));
	}

	/** 모델 테이블별 값 요약 — {dmId} → [{objOwner, objNm, summary}] */
	@PostMapping("/summary")
	public List<Map<String, Object>> summary(@RequestBody Map<String, Object> body) {
		return sqlSessionTemplate.selectList("udp.selectModelSummary", body.get("dmId"));
	}

	/** 정의 저장 — {udpId?, udpNm, dataTp, selectVals: [..](SELECT), requiredYn, sortOrder, useYn, udpDesc} */
	@PostMapping("/save")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.UDP_ADMIN)) throw new IllegalStateException("권한이 없습니다 (사용자 정의 속성 관리 권한 필요)");
			String id = body.get("udpId") == null ? "" : body.get("udpId").toString().trim();
			String nm = body.get("udpNm") == null ? "" : body.get("udpNm").toString().trim();
			String tp = body.get("dataTp") == null ? "TEXT" : body.get("dataTp").toString();
			if (nm.isEmpty()) throw new IllegalArgumentException("속성 이름을 넣어 주세요.");
			if (!DATA_TPS.contains(tp)) throw new IllegalArgumentException("유형은 글·숫자·날짜·선택 중 하나입니다.");
			List<String> sel = new ArrayList<>();
			if (body.get("selectVals") instanceof List) {
				for (Object o : (List<?>) body.get("selectVals")) {
					String v = o == null ? "" : o.toString().trim();
					if (!v.isEmpty() && !sel.contains(v)) sel.add(v);
				}
			}
			if ("SELECT".equals(tp) && sel.size() < 2) throw new IllegalArgumentException("선택 유형은 선택값을 2개 이상 넣어 주세요.");
			if (!"SELECT".equals(tp)) sel.clear();
			Map<String, Object> p = new HashMap<>();
			p.put("udpId", id.isEmpty() ? null : id);
			p.put("udpNm", nm);
			if ((Integer) sqlSessionTemplate.selectOne("udp.countDefNm", p) > 0) throw new IllegalArgumentException("같은 이름의 속성이 있습니다: " + nm);
			p.put("dataTp", tp);
			p.put("selectVals", sel);
			p.put("requiredYn", "Y".equals(body.get("requiredYn")) ? "Y" : "N");
			p.put("sortOrder", body.get("sortOrder") == null || body.get("sortOrder").toString().isEmpty() ? 0 : Integer.parseInt(body.get("sortOrder").toString()));
			p.put("useYn", "N".equals(body.get("useYn")) ? "N" : "Y");
			p.put("udpDesc", body.get("udpDesc"));
			p.put("userId", sessionService.getUserId());
			if (id.isEmpty()) {
				id = "UDP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
				p.put("udpId", id);
				sqlSessionTemplate.insert("udp.insertDef", p);
			} else {
				sqlSessionTemplate.update("udp.updateDef", p);
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents(id);
		} catch (NumberFormatException e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), "순서는 숫자여야 합니다.");
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 정의 삭제 — {udpId}. 값이 있으면 지우지 않는다(사용 중지로) */
	@PostMapping("/delete")
	public Mono<Response> delete(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.UDP_ADMIN)) throw new IllegalStateException("권한이 없습니다 (사용자 정의 속성 관리 권한 필요)");
			Map<String, Object> d = sqlSessionTemplate.selectOne("udp.selectDef", body.get("udpId"));
			if (d == null) throw new IllegalArgumentException("속성을 찾을 수 없습니다.");
			if (((Number) d.get("valCnt")).intValue() > 0) {
				throw new IllegalStateException("'" + d.get("udpNm") + "' 에 값이 " + d.get("valCnt") + "건 있어 지울 수 없습니다. 사용 중지하세요.");
			}
			sqlSessionTemplate.delete("udp.deleteDef", body.get("udpId"));
			res.setResultInfo(RestResult.CODE_200);
		} catch (Exception e) {
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}
}
