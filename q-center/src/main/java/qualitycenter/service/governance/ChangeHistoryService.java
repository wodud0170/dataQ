package qualitycenter.service.governance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ndata.quality.model.std.StdDataModelChangeHistoryVo;

import qualitycenter.service.auth.SessionService;

/**
 * 88번 거버넌스 — 데이터 모델 변경 이력 INSERT 헬퍼.
 *
 * 사용 패턴:
 *   - 변경 endpoint (saveAttrs / updateObj / addObj / deleteObj / createPk / ...) 가 변경 직후 호출
 *   - 단일 트랜잭션 유지 시 session 인자로 SqlSession 전달
 *   - 트랜잭션 외부에선 sqlSessionTemplate 자동 사용
 *
 * Tier 정책 (88_거버넌스_승인워크플로우_설계 §2):
 *   - TIER1   : schema 영향 변경. 사용자=DRAFT/SUBMITTED, 관리자=APPROVED 즉시
 *   - TIER1_5 : 사용자 UI 차단, 관리자만 가능 (컬럼 순서). 관리자=APPROVED
 *   - TIER2   : 메타 변경. 항상 APPROVED 즉시
 */
@Service
public class ChangeHistoryService {

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@Autowired
	private SessionService sessionService;

	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/**
	 * 변경 이력 INSERT. tier / aprvStatus 는 호출자가 결정.
	 * session 이 null 이면 새 트랜잭션, 아니면 그 session 트랜잭션 안에 INSERT.
	 */
	public void record(SqlSession session,
	                   String dmId, String changeType, String tier,
	                   String objOwner, String objNm, String attrNm,
	                   String constraintNm, String indexNm,
	                   String beforeJson, String afterJson, String ddlSnippet,
	                   String aprvStatus) {
		StdDataModelChangeHistoryVo vo = new StdDataModelChangeHistoryVo();
		vo.setDmId(dmId);
		vo.setChangeDt(LocalDateTime.now().format(DT_FMT));
		vo.setChangeUserId(safeUserId());
		vo.setChangeType(changeType);
		vo.setChangeTier(tier);
		vo.setObjOwner(objOwner);
		vo.setObjNm(objNm);
		vo.setAttrNm(attrNm);
		vo.setConstraintNm(constraintNm);
		vo.setIndexNm(indexNm);
		vo.setBeforeJson(beforeJson);
		vo.setAfterJson(afterJson);
		vo.setDdlSnippet(ddlSnippet);
		vo.setAprvStatus(aprvStatus == null ? "APPROVED" : aprvStatus);
		if ("APPROVED".equals(vo.getAprvStatus())) {
			vo.setAprvUserId(safeUserId());
			vo.setAprvDt(vo.getChangeDt());
		}
		if (session != null) session.insert("dmChangeHistory.insert", vo);
		else sqlSessionTemplate.insert("dmChangeHistory.insert", vo);
	}

	/** 사용자 변경 시 사용할 aprvStatus 결정 (관리자=APPROVED, 일반=DRAFT) */
	public String resolveAprvStatusForUserChange() {
		return sessionService.isAdmin() ? "APPROVED" : "DRAFT";
	}

	/** 현재 사용자 ID. 시스템 호출 시 'system' fallback */
	public String safeUserId() {
		String uid = sessionService.getUserId();
		return (uid == null || uid.isEmpty()) ? "system" : uid;
	}

	public String currentDt() {
		return LocalDateTime.now().format(DT_FMT);
	}
}
