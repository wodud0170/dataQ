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
 * 104번 WP8-31·75 — 사용자 정의 속성(테이블·컬럼 관리 항목) 정의와 값 조회 (관리 > 사용자 정의 속성).
 *
 * <p>정의 조회·값 조회는 누구나, 정의 등록·수정·사용 중지·삭제는 사용자 정의 속성 관리 권한(UDP_ADMIN).
 * 정의의 대상(OBJ 테이블·ATTR 컬럼)은 등록 때 정하고 바꾸지 않는다(값이 테이블 키·컬럼 키로 저장돼 있다).
 * 값이 있는 정의는 지우지 않고 사용 중지만 한다. 값 저장은 테이블 추가·수정과 컬럼 저장(DataModelController)이 한다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/udp")
public class UdpController {

	private static final List<String> DATA_TPS = java.util.Arrays.asList("TEXT", "NUMBER", "DATE", "SELECT");
	private static final List<String> TARGET_TPS = java.util.Arrays.asList("OBJ", "ATTR");

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private UdpService udpService;

	/** 정의 목록 — {useOnly: Y(사용 중만)|N, targetTp?: OBJ|ATTR (없으면 전부)} */
	@PostMapping("/defs")
	public List<Map<String, Object>> defs(@RequestBody(required = false) Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("useOnly", body != null && "Y".equals(body.get("useOnly")) ? "Y" : "N");
		if (body != null && body.get("targetTp") != null) {
			String tt = body.get("targetTp").toString();
			if (!TARGET_TPS.contains(tt)) throw new IllegalArgumentException("대상은 테이블·컬럼 중 하나입니다.");
			p.put("targetTp", tt);
		}
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

	/** 모델의 컬럼 값 — {dmId} → [{objOwner, objNm, attrNm, udpId, val}]. 화면에 보이는 컬럼(DataModelController.visibleTo)의 값만 */
	@PostMapping("/attrVals")
	public List<Map<String, Object>> attrVals(@RequestBody Map<String, Object> body) {
		String userId = sessionService.getUserId();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : sqlSessionTemplate.<Map<String, Object>>selectList("udp.selectModelAttrVals", body.get("dmId"))) {
			if (!DataModelController.visibleTo((String) r.remove("aprvStatus"), (String) r.remove("requesterUserId"), userId)) continue;
			out.add(r);
		}
		return out;
	}

	/**
	 * 정의 저장 — {udpId?, targetTp(추가 때 필수 OBJ|ATTR, 수정 때 무시), udpNm, dataTp, selectVals: [..](SELECT), requiredYn, sortOrder, useYn, udpDesc}.
	 * 이름은 같은 대상 안에서 겹치지 않고, 그 대상 엑셀 양식의 기본 열 이름과 같을 수 없다(양식에 같은 머리가 두 번 들어간다).
	 */
	@PostMapping("/save")
	public Mono<Response> save(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.hasPerm(PermCode.UDP_ADMIN)) throw new IllegalStateException("권한이 없습니다 (사용자 정의 속성 관리 권한 필요)");
			String id = body.get("udpId") == null ? "" : body.get("udpId").toString().trim();
			String nm = body.get("udpNm") == null ? "" : body.get("udpNm").toString().trim();
			String tp = body.get("dataTp") == null ? "TEXT" : body.get("dataTp").toString();
			String targetTp;
			if (id.isEmpty()) {
				targetTp = body.get("targetTp") == null ? "" : body.get("targetTp").toString();
				if (!TARGET_TPS.contains(targetTp)) throw new IllegalArgumentException("대상은 테이블·컬럼 중 하나입니다.");
			} else {
				Map<String, Object> cur = sqlSessionTemplate.selectOne("udp.selectDef", id);
				if (cur == null) throw new IllegalArgumentException("속성을 찾을 수 없습니다.");
				targetTp = (String) cur.get("targetTp");
			}
			if (nm.isEmpty()) throw new IllegalArgumentException("속성 이름을 넣어 주세요.");
			if (java.util.Arrays.asList("ATTR".equals(targetTp) ? DataModelController.ATTR_HEADERS : DataModelController.TABLE_HEADERS).contains(nm))
				throw new IllegalArgumentException("엑셀 양식의 기본 열 이름과 같은 이름은 쓸 수 없습니다: " + nm);
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
			p.put("targetTp", targetTp);
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
