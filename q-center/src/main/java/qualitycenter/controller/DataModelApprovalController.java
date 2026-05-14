package qualitycenter.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ndata.common.message.Response;
import com.ndata.common.message.RestResult;

import lombok.extern.slf4j.Slf4j;
import qualitycenter.service.auth.SessionService;
import qualitycenter.service.governance.ChangeHistoryService;
import reactor.core.publisher.Mono;

/**
 * 88번 거버넌스 — 데이터 모델 변경 신청 / 승인 / 반려 / 롤백 endpoint.
 *
 * 핵심 흐름 (DRAFT → SUBMITTED → APPROVED/REJECTED):
 *  - 사용자가 모델을 변경하면 ChangeHistoryService 가 DRAFT 로 이력 INSERT (DataModelController 분기)
 *  - 사용자가 [신청] 누르면 submitDrafts 가 DRAFT → SUBMITTED + SUBMISSION_ID 부여
 *  - 관리자가 [승인] 누르면 approve / [반려] 누르면 reject
 *  - 사용자는 본인 DRAFT 롤백 가능
 */
@Slf4j
@RestController
@RequestMapping("/api/dmApproval")
public class DataModelApprovalController {

	@Autowired private SqlSessionTemplate sqlSessionTemplate;
	@Autowired private SessionService sessionService;
	@Autowired private ChangeHistoryService changeHistory;

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/** 본인 DRAFT 목록 (신청 모달용) */
	@RequestMapping(value = "/myDrafts", method = RequestMethod.POST)
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> myDrafts(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>();
		p.put("currentUserId", changeHistory.safeUserId());
		p.put("dmId", body.get("dmId"));
		return sqlSessionTemplate.selectList("dmChangeHistory.selectMyDrafts", p);
	}

	/** 사용자 — 선택한 DRAFT 들을 SUBMITTED 로 일괄 신청 */
	@PostMapping("/submit")
	@SuppressWarnings("unchecked")
	public Mono<Response> submit(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty())
				throw new IllegalArgumentException("changeSeqList 필수");
			String submissionId = UUID.randomUUID().toString();
			Map<String, Object> p = new HashMap<>();
			p.put("currentUserId", changeHistory.safeUserId());
			p.put("changeSeqList", seqList);
			p.put("submissionId",  submissionId);
			int n = sqlSessionTemplate.update("dmChangeHistory.submitDrafts", p);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"submitted\":" + n + ",\"submissionId\":\"" + submissionId + "\"}");
		} catch (Exception e) {
			log.error(">> submit failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 사용자 — DRAFT 단건 롤백 (DB row 삭제 + 본 변경에 따른 실제 모델 row 도 정리 필요 — V2 검토) */
	@PostMapping("/rollbackDraft")
	public Mono<Response> rollbackDraft(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			Object seq = body.get("changeSeq");
			if (seq == null) throw new IllegalArgumentException("changeSeq 필수");
			Map<String, Object> p = new HashMap<>();
			p.put("changeSeq",     seq);
			p.put("currentUserId", changeHistory.safeUserId());
			int n = sqlSessionTemplate.delete("dmChangeHistory.deleteDraft", p);
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"deleted\":" + n + "}");
		} catch (Exception e) {
			log.error(">> rollbackDraft failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 관리자 — SUBMITTED 묶음 목록 (승인 화면용) */
	@PostMapping("/submissions")
	public List<Map<String, Object>> submissions(@RequestBody Map<String, Object> body) {
		if (!sessionService.isAdmin()) return new ArrayList<>();
		Map<String, Object> p = new HashMap<>();
		p.put("changeUserId", body.get("changeUserId"));
		return sqlSessionTemplate.selectList("dmChangeHistory.selectSubmissions", p);
	}

	/** 관리자 — 특정 묶음의 항목들 */
	@PostMapping("/submissionItems")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> submissionItems(@RequestBody Map<String, Object> body) {
		if (!sessionService.isAdmin()) return new ArrayList<>();
		String submissionId = (String) body.get("submissionId");
		return sqlSessionTemplate.selectList("dmChangeHistory.selectBySubmissionId", submissionId);
	}

	/** 관리자 — 선택 항목 승인 */
	@PostMapping("/approve")
	@SuppressWarnings("unchecked")
	public Mono<Response> approve(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			String now = LocalDateTime.now().format(DT_FMT);
			String adminId = changeHistory.safeUserId();
			int n = 0;
			for (Object seq : seqList) {
				n += applyApprovalStatus(seq, "APPROVED", adminId, now, body.get("aprvComment"));
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"approved\":" + n + "}");
		} catch (Exception e) {
			log.error(">> approve failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/** 관리자 — 선택 항목 반려 + 사유 */
	@PostMapping("/reject")
	@SuppressWarnings("unchecked")
	public Mono<Response> reject(@RequestBody Map<String, Object> body) {
		Response res = new Response();
		try {
			if (!sessionService.isAdmin()) throw new IllegalStateException("관리자 권한 필요");
			List<Object> seqList = (List<Object>) body.get("changeSeqList");
			if (seqList == null || seqList.isEmpty()) throw new IllegalArgumentException("changeSeqList 필수");
			String now = LocalDateTime.now().format(DT_FMT);
			String adminId = changeHistory.safeUserId();
			int n = 0;
			for (Object seq : seqList) {
				n += applyApprovalStatus(seq, "REJECTED", adminId, now, body.get("aprvComment"));
			}
			res.setResultInfo(RestResult.CODE_200);
			res.setContents("{\"rejected\":" + n + "}");
		} catch (Exception e) {
			log.error(">> reject failed: {}", e.getMessage(), e);
			res.setResultInfo(RestResult.CODE_500.getCode(), e.getMessage());
		}
		return Mono.just(res);
	}

	/**
	 * change_history 의 status 갱신 + 실제 모델 row 의 aprv_status 동기화.
	 * change_type 별 분기 — ADD_ATTR/MODIFY_ATTR/DEL_ATTR → tb_data_model_attr,
	 * ADD_OBJ/MODIFY_OBJ/DEL_OBJ → tb_data_model_obj.
	 */
	private int applyApprovalStatus(Object changeSeq, String status, String adminId, String now, Object comment) {
		Map<String, Object> p = new HashMap<>();
		p.put("changeSeq",   changeSeq);
		p.put("aprvStatus",  status);
		p.put("aprvUserId",  adminId);
		p.put("aprvDt",      now);
		p.put("aprvComment", comment);
		int n = sqlSessionTemplate.update("dmChangeHistory.updateAprvStatus", p);
		// 실제 모델 row 동기화
		Map<String, Object> hist = sqlSessionTemplate.selectOne("dmChangeHistory.selectByChangeSeq", changeSeq);
		if (hist != null) {
			String changeType = (String) hist.get("change_type");
			String dmId       = (String) hist.get("dm_id");
			String objOwner   = (String) hist.get("obj_owner");
			String objNm      = (String) hist.get("obj_nm");
			String attrNm     = (String) hist.get("attr_nm");
			Map<String, Object> sp = new HashMap<>();
			sp.put("dmId",        dmId);
			sp.put("objOwner",    objOwner);
			sp.put("objNm",       objNm);
			sp.put("attrNm",      attrNm);
			sp.put("aprvStatus",  status);
			sp.put("aprvUserId",  adminId);
			sp.put("aprvDt",      now);
			if (changeType != null && changeType.contains("_ATTR") && attrNm != null) {
				sqlSessionTemplate.update("dmChangeHistory.syncAttrAprvStatus", sp);
			} else if (changeType != null && changeType.contains("_OBJ")) {
				sqlSessionTemplate.update("dmChangeHistory.syncObjAprvStatus", sp);
			}
		}
		return n;
	}

	/** 변경 이력 조회 (관리자: 전체, 사용자: 본인 + APPROVED) */
	@PostMapping("/history")
	public List<com.ndata.quality.model.std.StdDataModelChangeHistoryVo> history(@RequestBody Map<String, Object> body) {
		Map<String, Object> p = new HashMap<>(body);
		p.put("currentUserId", changeHistory.safeUserId());
		p.put("isAdmin",       sessionService.isAdmin());
		return sqlSessionTemplate.selectList("dmChangeHistory.selectByDmId", p);
	}
}
