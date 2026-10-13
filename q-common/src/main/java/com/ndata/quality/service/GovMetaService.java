package com.ndata.quality.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 104번 WP5-78 — 범정부 메타 생성 전 검증 (구현 ①).
 *
 * <p>105번 §6.4 의 1~3 단계: 대상 고르기 → 검증 → 내부 레코드 건수. 파일 쓰기·전송(4·5단계)은 연계 규격을 받은 뒤
 * 붙인다(구현 ②). 결과는 생성 이력(TB_GOV_META_SEND)과 검증 결과(TB_GOV_META_SEND_ERR)에 남긴다. 오류(ERROR)가 하나라도
 * 있으면 상태 ERROR, 없으면 DONE. 경고(WARN)는 규격을 받기 전까지 경고로 두는 항목이다.</p>
 *
 * <p>q-center(생성·전송 화면)와 q-executor(예약 실행 GOV_META)가 함께 쓰므로 q-common 에 있다.</p>
 *
 * <p>대상</p>
 * <ul>
 *   <li>모델 단위(dmId) — 예약 실행 단위. 모델의 데이터소스 = DB, 모델의 시스템 = 정보시스템.</li>
 *   <li>시스템 단위(sysCd, 하위 포함) 또는 전체 — 전송대상 정보시스템(SEND_YN='Y', 시스템 노드) → 시스템 데이터소스 → DB 마다
 *       대표 모델(TB_GOV_META_DB.REP_DM_ID, 없으면 그 DB 를 쓰는 모델이 하나일 때 그 모델).</li>
 * </ul>
 */
@Slf4j
@Service
public class GovMetaService {

	public static final String SEV_ERROR = "ERROR";
	public static final String SEV_WARN = "WARN";

	/** 코드 매핑 그룹 (TB_GOV_META_CODE_MAP.CODE_GRP) */
	public static final List<String> CODE_GRPS = Arrays.asList("DB_FORM", "DBMS", "DBMS_VER", "OS", "OS_VER", "TBL_TP", "OCCR_CYCLE", "OPEN_TP");

	/** 한글 컬럼명 — 한글을 원칙으로, 특수문자·공백을 쓸 수 없다 (SMETA p.91, 별표 1) */
	private static final Pattern KR_NM_BAD = Pattern.compile("[^가-힣A-Za-z0-9]");
	/** 정보시스템코드 — 기관코드 7자리 + "_" + 일련번호 5자리 (SMETA p.88, 추정이라 경고) */
	private static final Pattern GOV_SYS_CD = Pattern.compile("^[0-9A-Za-z]{7}_[0-9]{5}$");

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	/** 검증 실행 중 모으는 것 */
	private static class Ctx {
		final List<Map<String, Object>> errs = new ArrayList<>();
		final Set<String> reportedCode = new HashSet<>();
		final Map<String, Set<String>> codeMap = new HashMap<>();
		int sysCnt, dbCnt, tblCnt, colCnt, errCnt, warnCnt;

		void add(String lvl, String sev, String sysCd, String dsId, String owner, String obj, String attr, String item, String msg) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("errSeq", errs.size() + 1);
			e.put("errLvl", lvl);
			e.put("sev", sev);
			e.put("sysCd", sysCd);
			e.put("dsId", dsId);
			e.put("objOwner", owner);
			e.put("objNm", obj);
			e.put("attrNm", attr);
			e.put("itemNm", item);
			e.put("errMsg", msg.length() > 1000 ? msg.substring(0, 1000) : msg);
			errs.add(e);
			if (SEV_ERROR.equals(sev)) errCnt++; else warnCnt++;
		}
	}

	/**
	 * 검증을 실행하고 이력을 남긴다.
	 *
	 * @param triggerType   MANUAL(화면) / SCHEDULE(예약 실행)
	 * @param sysCd         시스템 단위 대상 (하위 포함). dmId 와 함께 오면 dmId 가 우선
	 * @param dmId          모델 단위 대상
	 * @param scheduleLogId 예약 실행 이력 (SCHEDULE 일 때)
	 * @return sendId, status, 건수
	 */
	public Map<String, Object> validate(String triggerType, String sysCd, String dmId, String scheduleLogId, String userId) {
		return validate(newSendId(), triggerType, sysCd, dmId, scheduleLogId, userId);
	}

	public static String newSendId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	/** 예약 실행은 실행 이력에 먼저 이으려고 sendId 를 미리 정해 넘긴다 */
	public Map<String, Object> validate(String sendId, String triggerType, String sysCd, String dmId, String scheduleLogId, String userId) {
		Map<String, Object> conf = sqlSessionTemplate.selectOne("govMeta.selectConf");

		Map<String, Object> send = new HashMap<>();
		send.put("sendId", sendId);
		send.put("dmId", isEmpty(dmId) ? null : dmId);
		send.put("sysCd", isEmpty(sysCd) ? null : sysCd);
		send.put("triggerType", triggerType);
		send.put("scheduleLogId", scheduleLogId);
		send.put("status", "RUNNING");
		send.put("linkMthd", conf == null ? null : conf.get("linkMthd"));
		send.put("userId", userId);
		sqlSessionTemplate.insert("govMeta.insertSend", send);

		Ctx ctx = new Ctx();
		String status;
		String msg;
		try {
			for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("govMeta.selectCodeMapAll")) {
				ctx.codeMap.computeIfAbsent(str(c.get("codeGrp")), k -> new HashSet<>()).add(str(c.get("localVal")));
			}
			if (conf == null || isEmpty(str(conf.get("instNm")))) {
				ctx.add("SYS", SEV_ERROR, null, null, null, null, null, "기관명", "기관명이 없습니다. 범정부 메타 > 설정에서 넣어 주세요.");
			}
			if (!isEmpty(dmId)) {
				validateModel(ctx, dmId);
			} else {
				validateSystems(ctx, sysCd);
			}
			status = ctx.errCnt > 0 ? "ERROR" : "DONE";
			msg = "검증만 했습니다 — 파일 생성·전송은 연계 규격을 받은 뒤 붙습니다. 오류 " + ctx.errCnt + "건, 경고 " + ctx.warnCnt + "건";
		} catch (Exception e) {
			log.error(">> govMeta validate failed sendId={}", sendId, e);
			status = "ERROR";
			msg = "검증 중 오류: " + e.getMessage();
		}

		for (int i = 0; i < ctx.errs.size(); i += 500) {
			Map<String, Object> p = new HashMap<>();
			p.put("sendId", sendId);
			p.put("errs", ctx.errs.subList(i, Math.min(i + 500, ctx.errs.size())));
			sqlSessionTemplate.insert("govMeta.insertSendErrs", p);
		}
		Map<String, Object> fin = new HashMap<>();
		fin.put("sendId", sendId);
		fin.put("status", status);
		fin.put("sysCnt", ctx.sysCnt);
		fin.put("dbCnt", ctx.dbCnt);
		fin.put("tblCnt", ctx.tblCnt);
		fin.put("colCnt", ctx.colCnt);
		fin.put("errCnt", ctx.errCnt);
		fin.put("warnCnt", ctx.warnCnt);
		fin.put("sendMsg", msg.length() > 2000 ? msg.substring(0, 2000) : msg);
		sqlSessionTemplate.update("govMeta.finishSend", fin);
		log.info(">> govMeta validate sendId={} status={} sys={} db={} tbl={} col={} err={} warn={}",
				sendId, status, ctx.sysCnt, ctx.dbCnt, ctx.tblCnt, ctx.colCnt, ctx.errCnt, ctx.warnCnt);
		return fin;
	}

	/** 모델 단위 — 모델의 시스템·데이터소스로 정보시스템·DB 를 정한다 */
	private void validateModel(Ctx ctx, String dmId) {
		Map<String, Object> m = sqlSessionTemplate.selectOne("govMeta.selectModel", dmId);
		if (m == null) {
			ctx.add("DB", SEV_ERROR, null, null, null, null, null, "데이터 모델", "데이터 모델을 찾을 수 없습니다 (" + dmId + ").");
			return;
		}
		String sysCd = str(m.get("sysCd"));
		String dsId = str(m.get("dsId"));
		if (isEmpty(sysCd)) {
			ctx.add("SYS", SEV_ERROR, null, dsId, null, null, null, "정보시스템", "데이터 모델 '" + m.get("dmNm") + "' 에 시스템이 지정되어 있지 않습니다.");
		} else {
			Map<String, Object> q = new HashMap<>();
			q.put("sysCdExact", sysCd);
			List<Map<String, Object>> sysList = sqlSessionTemplate.selectList("govMeta.selectSendSysList", q);
			if (!sysList.isEmpty()) {
				Map<String, Object> sys = sysList.get(0);
				if (!"Y".equals(sys.get("sendYn"))) {
					ctx.add("SYS", SEV_WARN, sysCd, null, null, null, null, "전송대상", "정보시스템 '" + sys.get("sysNm") + "' 이 전송대상이 아닙니다.");
				}
				validateSys(ctx, sys);
				if (!isEmpty(dsId) && !dsList(sys).contains(dsId)) {
					ctx.add("DB", SEV_WARN, sysCd, dsId, null, null, null, "데이터소스",
							"모델의 데이터소스가 정보시스템 '" + sys.get("sysNm") + "' 의 데이터소스 목록에 없습니다.");
				}
			}
		}
		if (isEmpty(dsId)) {
			ctx.add("DB", SEV_ERROR, sysCd, null, null, null, null, "데이터소스",
					"데이터 모델 '" + m.get("dmNm") + "' 에 데이터소스가 없어 DB 정보를 만들 수 없습니다 (논리 모델은 대상이 아닙니다).");
			return;
		}
		Map<String, Object> db = sqlSessionTemplate.selectOne("govMeta.selectGovDb", dsId);
		String rep = db == null ? null : str(db.get("repDmId"));
		if (!isEmpty(rep) && !rep.equals(dmId)) {
			ctx.add("DB", SEV_WARN, sysCd, dsId, null, null, null, "대표 모델", "이 DB 의 대표 모델이 아닙니다. 시스템 단위 생성에서는 대표 모델의 테이블을 씁니다.");
		}
		validateDb(ctx, sysCd, db, dmId);
	}

	/** 시스템 단위 — 전송대상 정보시스템과 그 DB 의 대표 모델 */
	private void validateSystems(Ctx ctx, String sysCd) {
		Map<String, Object> q = new HashMap<>();
		q.put("sysCd", sysCd);
		List<Map<String, Object>> sysList = new ArrayList<>();
		for (Map<String, Object> s : sqlSessionTemplate.<Map<String, Object>>selectList("govMeta.selectSendSysList", q)) {
			// 시스템 그룹(SYS_TP 0)이 아닌 시스템 노드만
			if ("Y".equals(s.get("sendYn")) && !"0".equals(String.valueOf(s.get("sysTp")))) sysList.add(s);
		}
		if (sysList.isEmpty()) {
			ctx.add("SYS", SEV_ERROR, isEmpty(sysCd) ? null : sysCd, null, null, null, null, "전송대상",
					"전송대상 정보시스템이 없습니다. 범정부 메타 > 정보시스템·DB 에서 전송대상을 지정해 주세요.");
			return;
		}
		for (Map<String, Object> sys : sysList) {
			String sCd = str(sys.get("sysCd"));
			validateSys(ctx, sys);
			List<String> dsIds = dsList(sys);
			if (dsIds.isEmpty()) {
				ctx.add("SYS", SEV_ERROR, sCd, null, null, null, null, "데이터베이스", "정보시스템 '" + sys.get("sysNm") + "' 에 데이터소스가 없습니다.");
				continue;
			}
			Map<String, Object> mq = new HashMap<>();
			mq.put("dsIds", dsIds);
			Map<String, List<Map<String, Object>>> modelsByDs = new HashMap<>();
			for (Map<String, Object> m : sqlSessionTemplate.<Map<String, Object>>selectList("govMeta.selectModelsByDs", mq)) {
				modelsByDs.computeIfAbsent(str(m.get("dsId")), k -> new ArrayList<>()).add(m);
			}
			for (String dsId : dsIds) {
				Map<String, Object> db = sqlSessionTemplate.selectOne("govMeta.selectGovDb", dsId);
				if (db == null) {
					ctx.add("DB", SEV_ERROR, sCd, dsId, null, null, null, "데이터소스", "데이터소스를 찾을 수 없습니다 (" + dsId + ").");
					continue;
				}
				List<Map<String, Object>> models = modelsByDs.getOrDefault(dsId, Collections.emptyList());
				String rep = str(db.get("repDmId"));
				if (isEmpty(rep)) {
					if (models.size() == 1) {
						rep = str(models.get(0).get("dmId"));
					} else if (models.isEmpty()) {
						ctx.add("DB", SEV_WARN, sCd, dsId, null, null, null, "데이터 모델", "DB '" + db.get("dsn") + "' 를 쓰는 데이터 모델이 없어 테이블을 넣을 수 없습니다.");
					} else {
						ctx.add("DB", SEV_ERROR, sCd, dsId, null, null, null, "대표 모델",
								"DB '" + db.get("dsn") + "' 를 데이터 모델 " + models.size() + "개가 씁니다. 정보시스템·DB 에서 대표 모델을 정해 주세요.");
					}
				}
				validateDb(ctx, sCd, db, rep);
			}
		}
	}

	private void validateSys(Ctx ctx, Map<String, Object> sys) {
		ctx.sysCnt++;
		String sCd = str(sys.get("sysCd"));
		String nm = String.valueOf(sys.get("sysNm"));
		if (isEmpty(str(sys.get("govSysNm")))) {
			ctx.add("SYS", SEV_ERROR, sCd, null, null, null, null, "정보시스템명", "'" + nm + "' 의 정보시스템명(IRM 등록명)이 없습니다.");
		}
		String govCd = str(sys.get("govSysCd"));
		if (isEmpty(govCd)) {
			ctx.add("SYS", SEV_ERROR, sCd, null, null, null, null, "정보시스템코드", "'" + nm + "' 의 정보시스템코드가 없습니다.");
		} else if (!GOV_SYS_CD.matcher(govCd).matches()) {
			ctx.add("SYS", SEV_WARN, sCd, null, null, null, null, "정보시스템코드", "정보시스템코드 '" + govCd + "' 가 '기관코드 7자리_일련번호 5자리' 형식이 아닙니다.");
		}
		if (isEmpty(str(sys.get("operDeptNm")))) {
			ctx.add("SYS", SEV_ERROR, sCd, null, null, null, null, "운영부서명", "'" + nm + "' 의 운영부서명이 없습니다.");
		}
	}

	private void validateDb(Ctx ctx, String sysCd, Map<String, Object> db, String dmId) {
		if (db == null) return;
		ctx.dbCnt++;
		String dsId = str(db.get("dsId"));
		String dsn = String.valueOf(db.get("dsn"));
		String form = str(db.get("dbFormCd"));
		if (isEmpty(form)) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "DB 형태", "DB '" + dsn + "' 의 DB 형태가 없습니다.");
		} else {
			checkCode(ctx, "DB", SEV_ERROR, "DB_FORM", form, sysCd, dsId, null, null, null, "DB 형태");
		}
		if (isEmpty(str(db.get("dbNmKr")))) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "한글 DB명", "DB '" + dsn + "' 의 한글 DB명이 없습니다.");
		}
		if (isEmpty(str(db.get("dbNmEn"))) && isEmpty(str(db.get("dbName")))) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "영문 DB명", "DB '" + dsn + "' 의 영문 DB명이 없습니다.");
		}
		if (isEmpty(str(db.get("brmId")))) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "업무분류체계", "DB '" + dsn + "' 의 업무분류체계가 없습니다.");
		}
		String dbmsNm = str(db.get("dbmsNm"));
		if (isEmpty(dbmsNm)) {
			ctx.add("DB", SEV_WARN, sysCd, dsId, null, null, null, "DBMS 정보", "DB '" + dsn + "' 의 DBMS 이름·버전이 없습니다. 데이터 모델을 수집하면 채워집니다.");
		} else {
			checkCode(ctx, "DB", SEV_WARN, "DBMS", dbmsNm, sysCd, dsId, null, null, null, "DBMS 정보");
			checkCode(ctx, "DB", SEV_WARN, "DBMS_VER", str(db.get("dbmsVer")), sysCd, dsId, null, null, null, "DBMS 정보");
		}
		String os = str(db.get("osNm"));
		String osVer = str(db.get("osVer"));
		if (isEmpty(os) || isEmpty(osVer)) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "운영체제 정보", "DB '" + dsn + "' 의 운영체제 이름·버전이 없습니다.");
		} else {
			checkCode(ctx, "DB", SEV_ERROR, "OS", os, sysCd, dsId, null, null, null, "운영체제 정보");
			checkCode(ctx, "DB", SEV_ERROR, "OS_VER", osVer, sysCd, dsId, null, null, null, "운영체제 정보");
		}
		if (isEmpty(dmId)) return;

		List<Map<String, Object>> tbls = sqlSessionTemplate.selectList("govMeta.selectSendTblList", dmId);
		if (tbls.isEmpty()) {
			ctx.add("DB", SEV_ERROR, sysCd, dsId, null, null, null, "테이블 수", "DB '" + dsn + "' 에 전송대상 테이블이 없습니다.");
			return;
		}
		Set<String> sendTbls = new HashSet<>();
		for (Map<String, Object> t : tbls) {
			ctx.tblCnt++;
			String owner = str(t.get("objOwner"));
			String obj = str(t.get("objNm"));
			sendTbls.add(nz(owner) + "|" + obj);
			if (isEmpty(owner)) {
				ctx.add("TBL", SEV_ERROR, sysCd, dsId, owner, obj, null, "테이블소유자", "테이블소유자가 없습니다.");
			}
			if (isEmpty(str(t.get("objNmKr")))) {
				ctx.add("TBL", SEV_ERROR, sysCd, dsId, owner, obj, null, "한글테이블명", "한글테이블명이 없습니다.");
			}
			String openTp = str(t.get("openTp"));
			if (isEmpty(openTp)) {
				ctx.add("TBL", SEV_ERROR, sysCd, dsId, owner, obj, null, "공개/비공개여부", "공개/비공개여부가 없습니다.");
			} else {
				checkCode(ctx, "TBL", SEV_ERROR, "OPEN_TP", openTp, sysCd, dsId, owner, obj, null, "공개/비공개여부");
				if (!"공개".equals(openTp)) {
					if (isEmpty(str(t.get("openLimitReason")))) {
						ctx.add("TBL", SEV_ERROR, sysCd, dsId, owner, obj, null, "부분공개·비공개 사유", openTp + " 테이블에 사유가 없습니다.");
					}
					if (isEmpty(str(t.get("openLimitBasis")))) {
						ctx.add("TBL", SEV_ERROR, sysCd, dsId, owner, obj, null, "부분공개·비공개 관련근거", openTp + " 테이블에 관련근거가 없습니다.");
					}
				}
			}
			checkCode(ctx, "TBL", SEV_ERROR, "TBL_TP", str(t.get("tblTp")), sysCd, dsId, owner, obj, null, "테이블유형");
			checkCode(ctx, "TBL", SEV_ERROR, "OCCR_CYCLE", str(t.get("occrCycle")), sysCd, dsId, owner, obj, null, "발생주기");
		}

		for (Map<String, Object> c : sqlSessionTemplate.<Map<String, Object>>selectList("govMeta.selectSendColList", dmId)) {
			ctx.colCnt++;
			String owner = str(c.get("objOwner"));
			String obj = str(c.get("objNm"));
			String attr = str(c.get("attrNm"));
			String kr = str(c.get("attrNmKr"));
			if (!isEmpty(kr) && KR_NM_BAD.matcher(kr).find()) {
				ctx.add("COL", SEV_ERROR, sysCd, dsId, owner, obj, attr, "한글컬럼명", "한글컬럼명 '" + kr + "' 에 특수문자·공백을 쓸 수 없습니다.");
			}
			String openTp = str(c.get("openTp"));
			if (!isEmpty(openTp)) {
				checkCode(ctx, "COL", SEV_ERROR, "OPEN_TP", openTp, sysCd, dsId, owner, obj, attr, "공개/비공개여부");
				if (!"공개".equals(openTp) && isEmpty(str(c.get("openLimitReason")))) {
					ctx.add("COL", SEV_ERROR, sysCd, dsId, owner, obj, attr, "비공개 사유", openTp + " 컬럼에 사유가 없습니다.");
				}
			}
			if ("Y".equals(c.get("privYn")) && !"Y".equals(c.get("encrYn"))) {
				ctx.add("COL", SEV_WARN, sysCd, dsId, owner, obj, attr, "암호화여부", "개인정보 컬럼인데 암호화여부가 N 입니다.");
			}
			for (String ref : fkRefs(c, owner)) {
				if (!sendTbls.contains(ref)) {
					ctx.add("COL", SEV_WARN, sysCd, dsId, owner, obj, attr, "FK정보",
							"참조 테이블 " + ref.replace("|", ".") + " 가 전송대상이 아닙니다.");
				}
			}
		}
	}

	/** FK 참조 테이블 (소유자|테이블) — 제약조건 R 이 있으면 그것, 없으면 FK_PARENT */
	private List<String> fkRefs(Map<String, Object> c, String owner) {
		List<String> refs = new ArrayList<>();
		String agg = str(c.get("fkRefTbls"));
		if (!isEmpty(agg)) {
			for (String r : agg.split(",")) {
				int i = r.indexOf('|');
				String o = r.substring(0, i);
				refs.add((isEmpty(o) ? nz(owner) : o) + "|" + r.substring(i + 1));
			}
		} else if (!isEmpty(str(c.get("fkParentObjNm")))) {
			String o = str(c.get("fkParentObjOwner"));
			refs.add((isEmpty(o) ? nz(owner) : o) + "|" + c.get("fkParentObjNm"));
		}
		return refs;
	}

	/** 코드 매핑이 있는지 — 같은 그룹·값은 처음 한 번만 남긴다 */
	private void checkCode(Ctx ctx, String lvl, String sev, String grp, String val, String sysCd, String dsId,
			String owner, String obj, String attr, String item) {
		if (isEmpty(val)) return;
		Set<String> vals = ctx.codeMap.get(grp);
		if (vals != null && vals.contains(val)) return;
		if (!ctx.reportedCode.add(grp + "\u0000" + val)) return;
		ctx.add(lvl, sev, sysCd, dsId, owner, obj, attr, item,
				item + " '" + val + "' 의 범정부 코드가 코드 매핑(" + grp + ")에 없습니다. 같은 값의 다른 항목은 따로 적지 않습니다.");
	}

	/** 시스템 데이터소스 목록 — 매퍼가 array_to_string(SYS_DS_LST, ',') 로 준다 */
	private static List<String> dsList(Map<String, Object> sys) {
		List<String> out = new ArrayList<>();
		String v = str(sys.get("sysDsLst"));
		if (isEmpty(v)) return out;
		for (String s : v.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
		return out;
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
}
